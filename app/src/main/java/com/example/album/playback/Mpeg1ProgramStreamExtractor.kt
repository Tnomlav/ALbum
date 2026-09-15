package com.example.album.playback

import android.util.SparseArray
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.ts.Ac3Reader
import androidx.media3.extractor.ts.ElementaryStreamReader
import androidx.media3.extractor.ts.H262Reader
import androidx.media3.extractor.ts.MpegAudioReader
import androidx.media3.extractor.ts.TsPayloadReader
import java.io.IOException

/**
 * MPEG-1 program streams for the main player.
 *
 * Media3 ships a program stream extractor, but it only understands the MPEG-2
 * layer: its sniffer requires the MPEG-2 pack header prefix, and its PES reader
 * assumes the MPEG-2 header layout. MPEG-1 packs are two bytes shorter, mark
 * the clock reference with the `0010` prefix, and their PES packets may carry
 * 0xFF stuffing plus a "buffer scale and size" field before an optional
 * MPEG-1 style PTS/DTS pair. Without this extractor those files matched no
 * connector at all: they either played as a black screen with audio only or
 * were sent to the compatible player.
 *
 * The elementary stream parsers themselves are the same for both generations,
 * so this extractor only parses the container and reuses Media3's readers
 * (MPEG-1/2 video, MPEG audio and AC-3).
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class Mpeg1ProgramStreamExtractor : Extractor {

    private val pesReaders = SparseArray<PesReader>()
    private val payload = ParsableByteArray(4096)
    private val scratch = ParsableByteArray(PACK_HEADER_SIZE)
    private val timestampAdjuster = TimestampAdjuster(0)

    private var output: ExtractorOutput? = null
    private var seekMapOutput: Boolean = false
    private var tracksEnded: Boolean = false
    private var lastTrackPosition: Long = 0L
    private var durationRead: Boolean = false
    private var durationScanning: Boolean = false
    private var durationUs: Long = C.TIME_UNSET
    private var firstScr: Long = -1L
    private var lastScr: Long = -1L
    private var fileLength: Long = C.LENGTH_UNSET.toLong()

    override fun init(output: ExtractorOutput) {
        this.output = output
        pesReaders.clear()
        seekMapOutput = false
        tracksEnded = false
        lastTrackPosition = 0L
        durationRead = false
        durationScanning = false
        durationUs = C.TIME_UNSET
        firstScr = -1L
        lastScr = -1L
        fileLength = C.LENGTH_UNSET.toLong()
    }

    /**
     * Estimates the duration from the clock reference of the first and the last
     * pack header. Without it the player reports no length at all, which is
     * what made these files show `00:00` in the controls.
     *
     * The scan jumps to the tail of the file, looks for the last pack header
     * there and then seeks back to the beginning.
     */
    @Throws(IOException::class)
    private fun readDuration(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val length = input.getLength()
        if (length == C.LENGTH_UNSET.toLong() || length <= 0L) {
            durationRead = true
            return Extractor.RESULT_CONTINUE
        }
        fileLength = length
        if (!durationScanning) {
            val data = scratch.data
            input.resetPeekPosition()
            if (input.peekFully(data, 0, PACK_HEADER_SIZE, /* allowEndOfInput= */ true) &&
                isMpeg1PackHeader(data)
            ) {
                firstScr = readScr(data)
            }
            input.resetPeekPosition()
            durationScanning = true
            seekPosition.position = (length - DURATION_SEARCH_LENGTH).coerceAtLeast(0L)
            return Extractor.RESULT_SEEK
        }
        val data = scratch.data
        // The tail is pulled in one go and searched in memory: walking it one
        // byte at a time through the extractor input costs a round trip per
        // byte and is far slower than the scan itself.
        val tailLength = minOf(DURATION_SEARCH_LENGTH, length).toInt()
        val tail = ByteArray(tailLength)
        runCatching { input.readFully(tail, 0, tailLength) }
        var offset = 0
        while (offset + PACK_HEADER_SIZE <= tail.size) {
            if (isMpeg1PackHeader(tail, offset)) {
                lastScr = readScr(tail, offset)
                offset += PACK_HEADER_SIZE
            } else {
                offset++
            }
        }
        if (firstScr >= 0L && lastScr > firstScr) {
            durationUs = (lastScr - firstScr) * 1_000_000L / SCR_TICKS_PER_SECOND
        }
        durationRead = true
        seekPosition.position = 0L
        return Extractor.RESULT_SEEK
    }

    /** Reads the 33 bit clock reference of an MPEG-1 pack header. */
    private fun readScr(data: ByteArray, offset: Int = 0): Long =
        ((data[offset + 4].toLong() and 0x0E) shl 29) or
            ((data[offset + 5].toLong() and 0xFF) shl 22) or
            ((data[offset + 6].toLong() and 0xFE) shl 14) or
            ((data[offset + 7].toLong() and 0xFF) shl 7) or
            ((data[offset + 8].toLong() and 0xFE) shr 1)

    override fun sniff(input: ExtractorInput): Boolean {
        val data = scratch.data
        if (!input.peekFully(data, 0, PACK_HEADER_SIZE, /* allowEndOfInput= */ true)) return false
        return isMpeg1PackHeader(data)
    }

    @Throws(IOException::class)
    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val extractorOutput = output ?: return Extractor.RESULT_END_OF_INPUT
        if (!durationRead) {
            val result = readDuration(input, seekPosition)
            if (result != Extractor.RESULT_CONTINUE) return result
        }
        if (!seekMapOutput) {
            // Pack headers of this generation carry no index, but the clock
            // references at the ends of the file give the length, and the mux
            // rate of a program stream is constant, so the byte position for a
            // timestamp can be interpolated over the file length.
            extractorOutput.seekMap(
                if (durationUs > 0 && fileLength > 0) {
                    LinearSeekMap(durationUs, fileLength)
                } else {
                    SeekMap.Unseekable(durationUs)
                }
            )
            seekMapOutput = true
        }

        val data = scratch.data
        input.resetPeekPosition()
        if (!input.peekFully(data, 0, 4, /* allowEndOfInput= */ true)) {
            endTracks(extractorOutput)
            return Extractor.RESULT_END_OF_INPUT
        }
        val startCode = readInt(data, 0)
        if (startCode ushr 8 != START_CODE_PREFIX) {
            // Resynchronise on the next start code, exactly like the Media3
            // program stream extractor does.
            input.resetPeekPosition()
            input.skipFully(1)
            return Extractor.RESULT_CONTINUE
        }

        input.resetPeekPosition()
        when (startCode) {
            PACK_START_CODE -> {
                input.skipFully(PACK_HEADER_SIZE)
                return Extractor.RESULT_CONTINUE
            }

            SYSTEM_HEADER_START_CODE,
            PROGRAM_STREAM_MAP,
            PADDING_STREAM,
            PRIVATE_STREAM_2 -> {
                input.skipFully(4)
                input.readFully(data, 0, 2)
                val length = readUnsignedShort(data, 0)
                input.skipFully(length)
                return Extractor.RESULT_CONTINUE
            }
        }

        val streamId = startCode and 0xFF
        input.skipFully(4)
        input.readFully(data, 0, 2)
        val packetLength = readUnsignedShort(data, 0)
        val result = readPacket(extractorOutput, input, streamId, packetLength)
        if (result == Extractor.RESULT_CONTINUE) {
            maybeEndTracks(extractorOutput, input.getPosition())
        }
        return result
    }

    override fun seek(position: Long, timeUs: Long) {
        // A seek lands on an interpolated byte position, which is not a packet
        // boundary, so parsing resynchronises on the next start code. The
        // timestamp adjuster has to follow the new timeline for the resumed
        // presentation timestamps to stay in order.
        if (timestampAdjuster.getTimestampOffsetUs() == C.TIME_UNSET) {
            timestampAdjuster.reset(timeUs)
        }
        for (index in 0 until pesReaders.size()) {
            pesReaders.valueAt(index).seek()
        }
    }

    override fun release() {
        pesReaders.clear()
        output = null
    }

    /**
     * Reads one PES packet: parses whatever header the file uses and forwards
     * the payload to the elementary stream reader for its stream id.
     */
    private fun readPacket(
        extractorOutput: ExtractorOutput,
        input: ExtractorInput,
        streamId: Int,
        packetLength: Int
    ): Int {
        if (packetLength == 0) {
            // MPEG-1 program streams always carry a length. A zero length
            // packet cannot be delimited, so the stream ends here.
            endTracks(extractorOutput)
            return Extractor.RESULT_END_OF_INPUT
        }
        val reader = pesReaderFor(extractorOutput, streamId, input.getPosition())
        if (reader == null) {
            input.skipFully(packetLength)
            return Extractor.RESULT_CONTINUE
        }

        var remaining = packetLength
        // Stuffing bytes fill the header when the encoder needed a longer
        // packet; MPEG-1 marks them with 0xFF.
        var flags = -1
        while (remaining > 0) {
            flags = readByte(input)
            remaining--
            if (flags != 0xFF) break
        }
        if (remaining <= 0) return Extractor.RESULT_CONTINUE
        if ((flags and 0xC0) == 0x40) {
            // buffer scale and buffer size
            readByte(input)
            flags = readByte(input)
            remaining -= 2
        }
        if (remaining <= 0) return Extractor.RESULT_CONTINUE

        var timeUs = C.TIME_UNSET
        if ((flags and 0xE0) == 0x20) {
            timeUs = timestampAdjuster.adjustTsTimestamp(readTimestamp(input, flags))
            remaining -= 4
            if ((flags and 0x10) != 0) {
                readTimestamp(input, -1)
                remaining -= 5
            }
        } else if ((flags and 0xC0) == 0x80) {
            // MPEG-2 style header, still common inside MPEG-1 files.
            val headerFlags = readByte(input)
            val headerLength = readByte(input)
            remaining -= 2
            if (headerLength > remaining) return Extractor.RESULT_CONTINUE
            var headerLeft = headerLength
            if ((headerFlags and 0x80) != 0) {
                timeUs = timestampAdjuster.adjustTsTimestamp(readTimestamp(input, -1))
                headerLeft -= 5
                if ((headerFlags and 0x40) != 0) {
                    readTimestamp(input, -1)
                    headerLeft -= 5
                }
            }
            if (headerLeft > 0) input.skipFully(headerLeft)
            remaining -= headerLength
        }

        if (remaining <= 0) return Extractor.RESULT_CONTINUE
        payload.ensureCapacity(remaining)
        payload.reset(remaining)
        input.readFully(payload.data, 0, remaining)
        payload.setPosition(0)
        reader.packetStarted(timeUs, TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR)
        reader.consume(payload)
        reader.packetFinished(/* isEndOfInput= */ false)
        return Extractor.RESULT_CONTINUE
    }

    private fun pesReaderFor(
        extractorOutput: ExtractorOutput,
        streamId: Int,
        position: Long
    ): PesReader? {
        pesReaders.get(streamId)?.let { return it }
        // Creating a track after endTracks() is not allowed, so late streams
        // of an exotic file are ignored rather than reported.
        if (tracksEnded) return null
        val elementaryStreamReader = when {
            streamId == PRIVATE_STREAM_1 -> Ac3Reader(MimeTypes.VIDEO_PS)
            (streamId and 0xE0) == AUDIO_STREAM -> MpegAudioReader(MimeTypes.VIDEO_PS)
            (streamId and 0xF0) == VIDEO_STREAM -> H262Reader(MimeTypes.VIDEO_PS)
            else -> return null
        }
        lastTrackPosition = position
        elementaryStreamReader.createTracks(
            extractorOutput,
            TsPayloadReader.TrackIdGenerator(streamId, MAX_STREAM_ID_PLUS_ONE)
        )
        val reader = PesReader(elementaryStreamReader)
        pesReaders.put(streamId, reader)
        return reader
    }

    private fun endTracks(extractorOutput: ExtractorOutput) {
        if (tracksEnded) return
        tracksEnded = true
        extractorOutput.endTracks()
    }

    /**
     * All stream ids of a program stream are known once the streams have been
     * seen once, so the track list is closed a little after the last new one.
     */
    private fun maybeEndTracks(extractorOutput: ExtractorOutput, position: Long) {
        if (tracksEnded) return
        // Once the streams have been seen the track list can be closed. The
        // audio/video flags are only informational: a silent clip still has to
        // publish its single video track.
        if (pesReaders.size() > 0 && position > lastTrackPosition + TRACK_SEARCH_LENGTH) {
            endTracks(extractorOutput)
        } else if (position > MAX_TRACK_SEARCH_LENGTH) {
            endTracks(extractorOutput)
        }
    }

    private fun readByte(input: ExtractorInput): Int {
        val data = scratch.data
        input.readFully(data, 0, 1)
        return data[0].toInt() and 0xFF
    }

    /** Reads a 33-bit 90 kHz PTS/DTS, optionally reusing the already read byte. */
    private fun readTimestamp(input: ExtractorInput, first: Int): Long {
        val data = scratch.data
        if (first >= 0) {
            data[0] = first.toByte()
        } else {
            input.readFully(data, 0, 1)
        }
        input.readFully(data, 1, 4)
        return ((data[0].toLong() and 0x0E) shl 29) or
            ((data[1].toLong() and 0xFF) shl 22) or
            ((data[2].toLong() and 0xFE) shl 14) or
            ((data[3].toLong() and 0xFF) shl 7) or
            ((data[4].toLong() and 0xFE) shr 1)
    }

    private fun isMpeg1PackHeader(data: ByteArray): Boolean {
        if (data[0].toInt() != 0x00 || data[1].toInt() != 0x00) return false
        if (data[2].toInt() != 0x01 || (data[3].toInt() and 0xFF) != 0xBA) return false
        // '0010' is the MPEG-1 clock reference prefix; MPEG-2 uses '01'.
        return (data[4].toInt() and 0xF0) == 0x20
    }

    private fun isMpeg1PackHeader(data: ByteArray, offset: Int): Boolean {
        if (data[offset].toInt() != 0x00 || data[offset + 1].toInt() != 0x00) return false
        if (data[offset + 2].toInt() != 0x01 || (data[offset + 3].toInt() and 0xFF) != 0xBA) return false
        return (data[offset + 4].toInt() and 0xF0) == 0x20
    }

    /** Parses one PES packet header and hands the payload to a reader. */
    private class PesReader(private val payloadReader: ElementaryStreamReader) {
        fun seek() {
            payloadReader.seek()
        }

        fun packetStarted(timeUs: Long, flags: Int) {
            payloadReader.packetStarted(timeUs, flags)
        }

        fun consume(data: ParsableByteArray) {
            payloadReader.consume(data)
        }

        fun packetFinished(isEndOfInput: Boolean) {
            payloadReader.packetFinished(isEndOfInput)
        }
    }

    /**
     * Constant bit rate seek map: a program stream interleaves its packets at
     * a fixed rate, so a timestamp maps linearly onto a byte position.
     */
    private class LinearSeekMap(
        private val durationUs: Long,
        private val length: Long
    ) : SeekMap {
        override fun isSeekable(): Boolean = true

        override fun getDurationUs(): Long = durationUs

        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            val target = timeUs.coerceIn(0L, durationUs)
            val position = if (durationUs > 0L) target * length / durationUs else 0L
            return SeekMap.SeekPoints(
                SeekPoint(target, position.coerceIn(0L, length))
            )
        }
    }

    private companion object {
        const val PACK_HEADER_SIZE = 12
        const val START_CODE_PREFIX = 0x000001
        const val DURATION_SEARCH_LENGTH = 256L * 1024L
        const val SCR_TICKS_PER_SECOND = 90_000L

        const val PACK_START_CODE = 0x000001BA.toInt()
        const val SYSTEM_HEADER_START_CODE = 0x000001BB.toInt()
        const val PROGRAM_STREAM_MAP = 0x000001BC.toInt()
        const val PRIVATE_STREAM_1 = 0x000001BD.toInt()
        const val PADDING_STREAM = 0x000001BE.toInt()
        const val PRIVATE_STREAM_2 = 0x000001BF.toInt()

        const val VIDEO_STREAM = 0xE0
        const val AUDIO_STREAM = 0xC0
        const val MAX_STREAM_ID_PLUS_ONE = 0x100

        const val TRACK_SEARCH_LENGTH = 64L * 1024L
        const val MAX_TRACK_SEARCH_LENGTH = 4L * 1024L * 1024L

        fun readInt(data: ByteArray, offset: Int): Int =
            ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)

        fun readUnsignedShort(data: ByteArray, offset: Int): Int =
            ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }
}

/**
 * The app's extractor list: MPEG-1 program streams first, then everything the
 * Media3 defaults already handle (MP4, Matroska, MPEG-2 PS, AVI, ...).
 */
@androidx.annotation.OptIn(UnstableApi::class)
fun albumExtractorsFactory(): ExtractorsFactory = ExtractorsFactory {
    arrayOf<Extractor>(Mpeg1ProgramStreamExtractor()) +
        DefaultExtractorsFactory().createExtractors()
}

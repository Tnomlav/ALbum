package com.example.album

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.album.playback.albumExtractorsFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the "MPG/AVI must use the main player" issue.
 *
 * Media3 ships its own AVI and MPEG-PS extractors, so these files no longer
 * have to be forwarded to LibVLC before the main player gets a chance. The
 * samples are not part of the repository; push them into the app's files
 * directory to run the test, otherwise it is skipped:
 *
 * ```
 * adb push sample.avi /data/local/tmp/
 * adb shell run-as com.example.album sh -c "cat /data/local/tmp/sample.avi > files/album-sample.avi"
 * adb push sample.mpg /data/local/tmp/
 * adb shell run-as com.example.album sh -c "cat /data/local/tmp/sample.mpg > files/album-sample.mpg"
 * ```
 */
@RunWith(AndroidJUnit4::class)
class MainPlayerContainerTest {

    @Test
    fun aviDecodesWithTheMainPlayer() {
        val result = openSample("album-sample.avi")
        assertTrue("AVI did not become ready", result.ready)
        assertTrue("AVI produced no video track", result.videoMimeTypes.isNotEmpty())
        assertTrue(
            "AVI video track is not decodable: ${result.videoMimeTypes}",
            result.hasSupportedVideoTrack
        )
    }

    @Test
    fun mpegProgramStreamIsDemuxedByTheMainPlayer() {
        val result = openSample("album-sample.mpg")
        // MPEG-2 decoding depends on the device, but demuxing is the part the
        // main player is responsible for: it must find the video stream even
        // when the platform has to hand playback over to LibVLC afterwards.
        assertTrue(
            "MPEG-PS produced no video track: ${result.videoMimeTypes}",
            result.videoMimeTypes.any { it == "video/mpeg2" }
        )
    }

    private data class OpenResult(
        val ready: Boolean,
        val videoMimeTypes: List<String>,
        val hasSupportedVideoTrack: Boolean
    )

    private fun openSample(name: String): OpenResult {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.filesDir, name)
        assumeTrue("sample $name is not installed", file.isFile)

        val ready = CountDownLatch(1)
        val tracksChanged = CountDownLatch(1)
        // ExoPlayer is not thread safe and insists on the thread that created
        // it, so every player interaction happens on the main looper.
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(
                context,
                DefaultRenderersFactory(context).setEnableDecoderFallback(true)
            )
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context, albumExtractorsFactory())
                )
                .build()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) ready.countDown()
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    if (tracks.groups.isNotEmpty()) tracksChanged.countDown()
                }
            })
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
        }
        try {
            tracksChanged.await(30, TimeUnit.SECONDS)
            val becameReady = ready.await(30, TimeUnit.SECONDS)
            var tracks: androidx.media3.common.Tracks? = null
            instrumentation.runOnMainSync { tracks = player.currentTracks }
            val reported = requireNotNull(tracks)
            val videoGroups = reported.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            val result = OpenResult(
                ready = becameReady,
                videoMimeTypes = videoGroups.mapNotNull { group ->
                    group.getTrackFormat(0).sampleMimeType
                },
                hasSupportedVideoTrack = videoGroups.any { group ->
                    (0 until group.length).any { index ->
                        val support = group.getTrackSupport(index)
                        support != C.FORMAT_UNSUPPORTED_TYPE && support != C.FORMAT_UNSUPPORTED_SUBTYPE
                    }
                }
            )
            println(
                "AlbumTest: $name -> $result groups=" +
                    reported.groups.joinToString { group ->
                        "type=${group.type} length=${group.length} " +
                            "mime=${group.getTrackFormat(0).sampleMimeType} " +
                            "support=${(0 until group.length).joinToString(",") { group.getTrackSupport(it).toString() }} " +
                            "fmt=${group.getTrackFormat(0)}"
                    }
            )
            return result
        } finally {
            instrumentation.runOnMainSync { player.release() }
        }
    }
}

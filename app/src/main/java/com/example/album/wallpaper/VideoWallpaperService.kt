package com.example.album.wallpaper

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import org.json.JSONArray
import java.io.File

/** Renders the selected local video queue as a looping live wallpaper. */
class VideoWallpaperService : WallpaperService() {
    private companion object { const val TAG = "AlbumVideoWallpaper" }
    override fun onCreateEngine(): Engine = VideoEngine()

    private inner class VideoEngine : Engine() {
        private var player: MediaPlayer? = null
        private var visible = false
        private var hasBeenVisible = false
        private var surfaceReady = false
        private var playerReady = false
        private var prepareRetryCount = 0
        private var currentQueueIndex: Int? = null
        private var queueIdentity: String? = null
        private val failedQueueIndexes = mutableSetOf<Int>()
        private val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        private val volumeHandler = Handler(Looper.getMainLooper())
        private val volumeUpdater = object : Runnable {
            override fun run() {
                updateAudioOutput()
                if (player != null) volumeHandler.postDelayed(this, 500L)
            }
        }
        private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (playerReady) player?.pause()
                AudioManager.AUDIOFOCUS_GAIN -> if (shouldPlay() && playerReady) player?.start()
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            applyPowerSaverPolicy()
            if (isVisible && hasBeenVisible && !visible) advanceQueue()
            visible = isVisible
            if (isVisible) hasBeenVisible = true
            if (isVisible) startPlayback()
            else if (playerReady) {
                if (keepPlayingInBackground()) player?.start() else player?.pause()
            }
            updateAudioOutput()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = holder.surface.isValid
            if (surfaceReady) startPlayback(holder)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (!holder.surface.isValid) return
            surfaceReady = true
            runCatching {
                player?.let {
                    applyVideoScaling(it)
                    it.setSurface(holder.surface)
                }
            }
                .onFailure { Log.w(TAG, "surface rebind failed", it) }
            if (player == null) startPlayback(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            playerReady = false
            failedQueueIndexes.clear()
            currentQueueIndex = null
            player?.release()
            player = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            volumeHandler.removeCallbacks(volumeUpdater)
            audioManager.abandonAudioFocus(audioFocusListener)
            playerReady = false
            failedQueueIndexes.clear()
            player?.release()
            player = null
            super.onDestroy()
        }

        private fun startPlayback(holder: SurfaceHolder? = surfaceHolder) {
            if (!surfaceReady && holder == null) return
            val targetHolder = holder ?: surfaceHolder ?: return
            if (!targetHolder.surface.isValid) return
            player?.let {
                applyVideoScaling(it)
                if (shouldPlay() && playerReady && !it.isPlaying) it.start()
                return
            }
            val source = currentSource() ?: return
            if (!source.isFile || !source.canRead() || source.length() == 0L) {
                Log.e(TAG, "video source unavailable: ${source.absolutePath} exists=${source.exists()} length=${source.length()}")
                return
            }
            playerReady = false
            player = runCatching {
                MediaPlayer().apply {
                    // Set the native renderer mode before attaching the Surface
                    // and before prepareAsync, preventing the default stretched
                    // transform from being used for the first decoded frame.
                    setDataSource(source.absolutePath)
                    applyVideoScaling(this)
                    setSurface(targetHolder.surface)
                    isLooping = true
                    val mode = soundMode()
                    val outputVolume = wallpaperOutputVolume(mode)
                    setVolume(outputVolume, outputVolume)
                    if (mode == "BackgroundWithFocus") {
                        audioManager.requestAudioFocus(
                            audioFocusListener,
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN
                        )
                    } else {
                        audioManager.abandonAudioFocus(audioFocusListener)
                    }
                    setOnPreparedListener { prepared ->
                        playerReady = true
                        prepareRetryCount = 0
                        applyVideoScaling(prepared)
                        if (shouldPlay()) prepared.start()
                    }
                    setOnErrorListener { failedPlayer, what, extra ->
                        Log.e(TAG, "video playback error: ${source.absolutePath} what=$what extra=$extra")
                        playerReady = false
                        if (player === failedPlayer) player = null
                        failedPlayer.release()
                        if (surfaceReady) {
                            val queue = readQueue(getSharedPreferences("album_preferences", MODE_PRIVATE).getString("live_wallpaper_queue", "[]"))
                            val failedIndex = currentQueueIndex
                            if (queue.size > 1 && failedIndex != null) {
                                failedQueueIndexes += failedIndex
                                val next = nextPlayableQueueIndex(queue, failedIndex)
                                if (next != null) {
                                    getSharedPreferences("album_preferences", MODE_PRIVATE).edit()
                                        .putInt("live_wallpaper_index", next)
                                        .apply()
                                    prepareRetryCount = 0
                                    startPlayback()
                                } else {
                                    Log.e(TAG, "all videos in the wallpaper queue failed")
                                }
                            } else if (prepareRetryCount++ < 1) {
                                startPlayback()
                            }
                        }
                        true
                    }
                    prepareAsync()
                }
            }.getOrElse {
                Log.e(TAG, "video player setup failed: ${source.absolutePath}", it)
                playerReady = false
                player?.release()
                null
            }
            volumeHandler.removeCallbacks(volumeUpdater)
            volumeHandler.post(volumeUpdater)
        }

        private fun applyVideoScaling(mediaPlayer: MediaPlayer) {
            mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
        }

        private fun keepPlayingInBackground(): Boolean =
            getSharedPreferences("album_preferences", MODE_PRIVATE)
                .getBoolean("wallpaper_dynamic_background", false)

        private fun shouldPlay(): Boolean = visible || (surfaceReady && keepPlayingInBackground())

        private fun soundMode(): String =
            getSharedPreferences("album_preferences", MODE_PRIVATE)
                .getString("wallpaper_sound", "Disabled") ?: "Disabled"

        private fun updateAudioOutput() {
            player?.let {
                val outputVolume = wallpaperOutputVolume(soundMode())
                it.setVolume(outputVolume, outputVolume)
            }
        }

        private fun wallpaperOutputVolume(mode: String): Float {
            if (mode == "Disabled" || mode == "ForegroundOnly" && !visible) return 0f
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val systemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
            val wallpaperVolume = getSharedPreferences("album_preferences", MODE_PRIVATE)
                .getFloat("wallpaper_volume", 1f).coerceIn(0f, 1f)
            return (systemVolume * wallpaperVolume).coerceIn(0f, 1f)
        }

        private fun applyPowerSaverPolicy() {
            val powerManager = getSystemService(PowerManager::class.java)
            if (powerManager?.isPowerSaveMode == true) {
                getSharedPreferences("album_preferences", MODE_PRIVATE).edit()
                    .putBoolean("wallpaper_low_power", true)
                    .putBoolean("wallpaper_dynamic_background", false)
                    .apply()
            }
        }

        private fun currentSource(): File? {
            val prefs = getSharedPreferences("album_preferences", MODE_PRIVATE)
            val queue = readQueue(prefs.getString("live_wallpaper_queue", "[]"))
            if (queue.isEmpty()) {
                currentQueueIndex = null
                val name = prefs.getString("live_wallpaper_single_video", "live_wallpaper_video") ?: "live_wallpaper_video"
                return directChild(name)?.takeIf { it.isFile && it.canRead() }
            }
            val identity = prefs.getString("live_wallpaper_queue_dir", "live_wallpaper_queue") ?: "live_wallpaper_queue"
            if (queueIdentity != identity) {
                queueIdentity = identity
                failedQueueIndexes.clear()
            }
            val index = prefs.getInt("live_wallpaper_index", 0).coerceIn(0, queue.lastIndex)
            val playableIndex = nextPlayableQueueIndex(queue, index)
            if (playableIndex == null) {
                currentQueueIndex = null
                return null
            }
            currentQueueIndex = playableIndex
            if (playableIndex != index) prefs.edit().putInt("live_wallpaper_index", playableIndex).apply()
            return directChild(identity)?.let { File(it, queue[playableIndex]) }?.takeIf { it.isFile && it.canRead() }
        }

        private fun advanceQueue() {
            val prefs = getSharedPreferences("album_preferences", MODE_PRIVATE)
            val queue = readQueue(prefs.getString("live_wallpaper_queue", "[]"))
            if (queue.size <= 1) return
            val current = prefs.getInt("live_wallpaper_index", 0).coerceIn(0, queue.lastIndex)
            val next = nextPlayableQueueIndex(queue, current, includeCurrent = false) ?: return
            player?.release()
            player = null
            playerReady = false
            prefs.edit().putInt("live_wallpaper_index", next).apply()
        }

        private fun nextPlayableQueueIndex(queue: List<String>, start: Int, includeCurrent: Boolean = true): Int? {
            val prefs = getSharedPreferences("album_preferences", MODE_PRIVATE)
            val identity = prefs.getString("live_wallpaper_queue_dir", "live_wallpaper_queue") ?: "live_wallpaper_queue"
            val directory = directChild(identity) ?: return null
            val candidates = orderedQueueIndexes(prefs, queue.size, start, includeCurrent)
            return candidates.firstOrNull { index ->
                index !in failedQueueIndexes && File(directory, queue[index]).let { it.isFile && it.canRead() }
            }
        }

        private fun orderedQueueIndexes(
            prefs: android.content.SharedPreferences,
            size: Int,
            start: Int,
            includeCurrent: Boolean
        ): List<Int> {
            if (size == 0) return emptyList()
            val indexes = when (prefs.getString("wallpaper_video_order", "InOrder")) {
                "TrueRandom" -> (0 until size).filter { includeCurrent || it != start }.shuffled()
                "Shuffle" -> {
                    val stored = readIndexes(prefs.getString("live_wallpaper_shuffle", "[]"), size)
                    val shuffled = (stored + (0 until size)).distinct()
                    val position = shuffled.indexOf(start)
                    val rotated = if (position >= 0) shuffled.drop(position) + shuffled.take(position) else shuffled
                    if (includeCurrent) rotated else rotated.dropWhile { it == start }
                }
                else -> {
                    val sequential = (0 until size).map { offset -> (start + offset) % size }
                    if (includeCurrent) sequential else sequential.drop(1)
                }
            }
            return indexes
        }

        private fun directChild(name: String): File? {
            if (name.isBlank() || name.contains('/') || name.contains('\\') || name == "." || name == "..") return null
            return File(filesDir, name)
        }

        private fun readQueue(value: String?): List<String> = runCatching {
            val json = JSONArray(value ?: "[]")
            (0 until json.length()).map { json.getString(it) }.filter {
                it.isNotBlank() && it != "." && it != ".." && !it.contains('/') && !it.contains('\\')
            }
        }.getOrDefault(emptyList())

        private fun readIndexes(value: String?, size: Int): List<Int> = runCatching {
            val json = JSONArray(value ?: "[]")
            (0 until json.length()).map { json.getInt(it) }.filter { it in 0 until size }
        }.getOrDefault(emptyList())
    }
}

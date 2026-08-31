package com.example.album.wallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.media.MediaPlayer
import android.media.AudioManager
import android.content.Context
import android.os.PowerManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import org.json.JSONArray

/** Renders the last imported local video as a looping, silent live wallpaper. */
class VideoWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = VideoEngine()

    private inner class VideoEngine : Engine() {
        private var player: MediaPlayer? = null
        private var visible = false
        private var hasBeenVisible = false
        private var surfaceReady = false
        private val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        private val volumeHandler = Handler(Looper.getMainLooper())
        private val volumeUpdater = object : Runnable {
            override fun run() {
                updateAudioOutput()
                if (player != null) volumeHandler.postDelayed(this, 500L)
            }
        }
        private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                player?.pause()
            } else if (change == AudioManager.AUDIOFOCUS_GAIN && visible) {
                player?.start()
            }
        }
        override fun onVisibilityChanged(visible: Boolean) {
            applyPowerSaverPolicy()
            if (visible && hasBeenVisible && !this.visible) advanceQueue()
            this.visible = visible
            if (visible) hasBeenVisible = true
            if (visible) startPlayback() else if (keepPlayingInBackground()) player?.start() else player?.pause()
            updateAudioOutput()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            // The system preview may create the surface before dispatching a
            // visibility callback. Treat a valid preview surface as playable
            // so the prepared video cannot remain stuck on a black frame.
            surfaceReady = true
            visible = true
            startPlayback(holder)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            player?.setSurface(holder.surface)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            player?.release()
            player = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            volumeHandler.removeCallbacks(volumeUpdater)
            audioManager.abandonAudioFocus(audioFocusListener)
            player?.release()
            player = null
            super.onDestroy()
        }

        private fun startPlayback(holder: SurfaceHolder? = surfaceHolder) {
            if (!visible && holder == null) return
            val surface = holder?.surface ?: surfaceHolder?.surface ?: return
            if (!surface.isValid || player != null) {
                player?.let { if (visible && !it.isPlaying) it.start() }
                return
            }
            val source = currentSource() ?: return
            if (!source.isFile) return
            player = runCatching {
                MediaPlayer().apply {
                    setSurface(surface)
                    // Keep the video's original aspect ratio. The wallpaper
                    // surface may have a different shape from the source;
                    // fitting avoids stretching the frames to fill it.
                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                    setDataSource(source.absolutePath)
                    isLooping = true
                    val soundMode = soundMode()
                    val outputVolume = wallpaperOutputVolume(soundMode)
                    setVolume(outputVolume, outputVolume)
                    if (soundMode == "BackgroundWithFocus") {
                        audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                    } else {
                        audioManager.abandonAudioFocus(audioFocusListener)
                    }
                    setOnPreparedListener { prepared ->
                        if (shouldPlay()) prepared.start()
                    }
                    setOnErrorListener { failedPlayer, _, _ ->
                        failedPlayer.release()
                        player = null
                        true
                    }
                    prepareAsync()
                }
            }.getOrElse {
                player?.release()
                null
            }
            volumeHandler.removeCallbacks(volumeUpdater)
            volumeHandler.post(volumeUpdater)
        }

        private fun keepPlayingInBackground(): Boolean =
            applicationContext.getSharedPreferences("album_preferences", Context.MODE_PRIVATE)
                .getBoolean("wallpaper_dynamic_background", false)

        private fun shouldPlay(): Boolean = visible || (surfaceReady && keepPlayingInBackground())

        private fun soundMode(): String =
            applicationContext.getSharedPreferences("album_preferences", Context.MODE_PRIVATE)
                .getString("wallpaper_sound", "Disabled") ?: "Disabled"

        private fun updateAudioOutput() {
            val currentPlayer = player ?: return
            val outputVolume = wallpaperOutputVolume(soundMode())
            currentPlayer.setVolume(outputVolume, outputVolume)
        }

        private fun wallpaperOutputVolume(mode: String): Float {
            if (mode == "Disabled" || mode == "ForegroundOnly" && !visible) return 0f
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val systemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
            val wallpaperVolume = applicationContext.getSharedPreferences("album_preferences", Context.MODE_PRIVATE)
                .getFloat("wallpaper_volume", 1f)
                .coerceIn(0f, 1f)
            return (systemVolume * wallpaperVolume).coerceIn(0f, 1f)
        }

        private fun applyPowerSaverPolicy() {
            val powerManager = applicationContext.getSystemService(PowerManager::class.java)
            if (powerManager?.isPowerSaveMode == true) {
                applicationContext.getSharedPreferences("album_preferences", Context.MODE_PRIVATE).edit()
                    .putBoolean("wallpaper_low_power", true)
                    .putBoolean("wallpaper_dynamic_background", false)
                    .apply()
            }
        }

        private fun currentSource(): File? {
            val prefs = applicationContext.getSharedPreferences("album_preferences", Context.MODE_PRIVATE)
            val queue = runCatching {
                val json = JSONArray(prefs.getString("live_wallpaper_queue", "[]"))
                (0 until json.length()).map { json.getString(it) }
            }.getOrDefault(emptyList())
            if (queue.isEmpty()) return File(applicationContext.filesDir, "live_wallpaper_video")
            val index = prefs.getInt("live_wallpaper_index", 0).coerceIn(0, queue.lastIndex)
            return File(applicationContext.filesDir, "live_wallpaper_queue/${queue[index]}")
        }

        private fun advanceQueue() {
            val prefs = applicationContext.getSharedPreferences("album_preferences", Context.MODE_PRIVATE)
            val queue = runCatching {
                val json = JSONArray(prefs.getString("live_wallpaper_queue", "[]"))
                (0 until json.length()).map { json.getString(it) }
            }.getOrDefault(emptyList())
            if (queue.size <= 1) return
            val current = prefs.getInt("live_wallpaper_index", 0).coerceIn(0, queue.lastIndex)
            val order = prefs.getString("wallpaper_video_order", "InOrder")
            val next = when (order) {
                "TrueRandom" -> (queue.indices.filter { it != current }).random()
                "Shuffle" -> {
                    val shuffled = runCatching {
                        val json = JSONArray(prefs.getString("live_wallpaper_shuffle", "[]"))
                        (0 until json.length()).map { json.getInt(it) }.filter { it in queue.indices }
                    }.getOrDefault(emptyList())
                    val position = shuffled.indexOf(current)
                    shuffled.getOrNull(position + 1) ?: run {
                        val nextOrder = queue.indices.shuffled()
                        prefs.edit().putString("live_wallpaper_shuffle", JSONArray(nextOrder).toString()).apply()
                        nextOrder.first()
                    }
                }
                else -> (current + 1) % queue.size
            }
            player?.release()
            player = null
            prefs.edit().putInt("live_wallpaper_index", next).apply()
        }
    }
}

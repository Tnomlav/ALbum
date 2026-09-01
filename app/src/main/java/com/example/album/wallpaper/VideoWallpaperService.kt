package com.example.album.wallpaper

import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONArray
import java.io.File

/** Renders the selected local video queue as a looping live wallpaper. */
class VideoWallpaperService : WallpaperService() {
    private companion object { const val TAG = "AlbumVideoWallpaper" }
    override fun onCreateEngine(): Engine = VideoEngine()

    private inner class VideoEngine : Engine() {
        private var player: ExoPlayer? = null
        private var visible = false
        private var hasBeenVisible = false
        private var surfaceReady = false
        private var playerReady = false
        private var prepareRetryCount = 0
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
                AudioManager.AUDIOFOCUS_GAIN -> if (visible && playerReady) player?.play()
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            applyPowerSaverPolicy()
            if (isVisible && hasBeenVisible && !visible) advanceQueue()
            visible = isVisible
            if (isVisible) hasBeenVisible = true
            if (isVisible) startPlayback()
            else if (playerReady) {
                if (keepPlayingInBackground()) player?.play() else player?.pause()
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
            runCatching { player?.setVideoSurface(holder.surface) }
                .onFailure { Log.w(TAG, "surface rebind failed", it) }
            if (player == null) startPlayback(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            playerReady = false
            player?.clearVideoSurface()
            player?.release()
            player = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            volumeHandler.removeCallbacks(volumeUpdater)
            audioManager.abandonAudioFocus(audioFocusListener)
            playerReady = false
            player?.clearVideoSurface()
            player?.release()
            player = null
            super.onDestroy()
        }

        private fun startPlayback(holder: SurfaceHolder? = surfaceHolder) {
            if (!surfaceReady && holder == null) return
            val targetHolder = holder ?: surfaceHolder ?: return
            if (!targetHolder.surface.isValid) return
            player?.let {
                if (visible && playerReady) it.play()
                return
            }
            val source = currentSource() ?: return
            if (!source.isFile || !source.canRead() || source.length() == 0L) {
                Log.e(TAG, "video source unavailable: ${source.absolutePath} exists=${source.exists()} length=${source.length()}")
                return
            }
            playerReady = false
            player = runCatching {
                ExoPlayer.Builder(applicationContext).build().apply {
                    setVideoSurface(targetHolder.surface)
                    // Preserve the source aspect ratio while filling the entire
                    // wallpaper surface. Excess edges are cropped instead of
                    // leaving black bars or stretching the video.
                    setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        soundMode() == "BackgroundWithFocus"
                    )
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(source)))
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = wallpaperOutputVolume(soundMode())
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state != Player.STATE_READY) return
                            playerReady = true
                            prepareRetryCount = 0
                            Log.i(TAG, "video ready: ${source.name} visible=$visible surfaceReady=$surfaceReady")
                            // Re-apply after the codec is created. Some vendor
                            // decoders ignore the pre-prepare value.
                            setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                            playWhenReady = shouldPlay()
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            Log.e(TAG, "video playback error: ${source.absolutePath}", error)
                            playerReady = false
                            player?.release()
                            player = null
                            if (surfaceReady && prepareRetryCount++ < 1) startPlayback()
                        }
                    })
                    prepare()
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

        private fun keepPlayingInBackground(): Boolean =
            getSharedPreferences("album_preferences", MODE_PRIVATE)
                .getBoolean("wallpaper_dynamic_background", false)

        private fun shouldPlay(): Boolean = visible || (surfaceReady && keepPlayingInBackground())

        private fun soundMode(): String =
            getSharedPreferences("album_preferences", MODE_PRIVATE)
                .getString("wallpaper_sound", "Disabled") ?: "Disabled"

        private fun updateAudioOutput() {
            player?.volume = wallpaperOutputVolume(soundMode())
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
            val queue = runCatching {
                val json = JSONArray(prefs.getString("live_wallpaper_queue", "[]"))
                (0 until json.length()).map { json.getString(it) }
            }.getOrDefault(emptyList())
            if (queue.isEmpty()) return File(filesDir, "live_wallpaper_video")
            val index = prefs.getInt("live_wallpaper_index", 0).coerceIn(0, queue.lastIndex)
            return File(filesDir, "live_wallpaper_queue/${queue[index]}")
        }

        private fun advanceQueue() {
            val prefs = getSharedPreferences("album_preferences", MODE_PRIVATE)
            val queue = runCatching {
                val json = JSONArray(prefs.getString("live_wallpaper_queue", "[]"))
                (0 until json.length()).map { json.getString(it) }
            }.getOrDefault(emptyList())
            if (queue.size <= 1) return
            val current = prefs.getInt("live_wallpaper_index", 0).coerceIn(0, queue.lastIndex)
            val next = when (prefs.getString("wallpaper_video_order", "InOrder")) {
                "TrueRandom" -> (queue.indices.filter { it != current }).random()
                "Shuffle" -> queue.indices.shuffled().first()
                else -> (current + 1) % queue.size
            }
            player?.clearVideoSurface()
            player?.release()
            player = null
            playerReady = false
            prefs.edit().putInt("live_wallpaper_index", next).apply()
        }
    }
}

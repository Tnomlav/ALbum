package com.example.album.wallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.media.MediaPlayer
import java.io.File

/** Renders the last imported local video as a looping, silent live wallpaper. */
class VideoWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = VideoEngine()

    private inner class VideoEngine : Engine() {
        private var player: MediaPlayer? = null
        private var visible = false

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) startPlayback() else player?.pause()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startPlayback(holder)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            player?.setSurface(holder.surface)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            player?.release()
            player = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
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
            val source = File(applicationContext.filesDir, "live_wallpaper_video")
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
                    setVolume(0f, 0f)
                    setOnPreparedListener { prepared -> if (visible) prepared.start() }
                    prepareAsync()
                }
            }.getOrElse {
                player?.release()
                null
            }
        }
    }
}

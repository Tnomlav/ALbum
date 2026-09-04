package com.example.album.wallpaper

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import org.json.JSONArray
import java.io.File
import kotlin.math.min

/** Displays the imported static wallpaper queue and rotates it without the app UI running. */
class ImageWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ImageEngine()

    private inner class ImageEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var visible = false
        private var hasBeenVisible = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0

        private val rotateTask = object : Runnable {
            override fun run() {
                if (!visible) return
                advanceQueue()
                drawWallpaper()
                scheduleRotation()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible && hasBeenVisible && !this.visible && returnSwitchEnabled()) advanceQueue()
            this.visible = visible
            if (visible) {
                hasBeenVisible = true
                drawWallpaper()
                scheduleRotation()
            } else {
                handler.removeCallbacks(rotateTask)
                if (!allowBackground()) return
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            drawWallpaper()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            surfaceWidth = width
            surfaceHeight = height
            drawWallpaper()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(rotateTask)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacksAndMessages(null)
            super.onDestroy()
        }

        private fun drawWallpaper() {
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return
            val source = currentSource() ?: return
            val autoAdjust = getSharedPreferences("album_preferences", MODE_PRIVATE)
                .getBoolean("wallpaper_static_auto_adjust", true)
            val options = BitmapFactory.Options()
            if (autoAdjust && surfaceWidth > 0 && surfaceHeight > 0) {
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(source.absolutePath, options)
                options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, surfaceWidth, surfaceHeight)
                options.inJustDecodeBounds = false
            }
            val bitmap = BitmapFactory.decodeFile(source.absolutePath, options) ?: return
            val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                canvas.drawColor(android.graphics.Color.BLACK)
                val scale = min(canvas.width.toFloat() / bitmap.width, canvas.height.toFloat() / bitmap.height)
                val left = (canvas.width - bitmap.width * scale) / 2f
                val top = (canvas.height - bitmap.height * scale) / 2f
                val matrix = Matrix().apply { postScale(scale, scale); postTranslate(left, top) }
                canvas.drawBitmap(bitmap, matrix, paint)
            } finally {
                holder.unlockCanvasAndPost(canvas)
                bitmap.recycle()
            }
        }

        private fun scheduleRotation() {
            handler.removeCallbacks(rotateTask)
            if (!visible || returnSwitchEnabled()) return
            val prefs = getSharedPreferences("album_preferences", MODE_PRIVATE)
            val frequency = prefs.getString("wallpaper_frequency", "5") ?: "5"
            val seconds = if (frequency == "custom") prefs.getString("wallpaper_custom_seconds", "10")?.toLongOrNull() ?: 10L else frequency.toLongOrNull() ?: 5L
            handler.postDelayed(rotateTask, seconds.coerceIn(1L, 86400L) * 1000L)
        }

        private fun currentSource(): File? {
            val prefs = getSharedPreferences("album_preferences", MODE_PRIVATE)
            val queue = readQueue(prefs.getString("static_wallpaper_queue", "[]"))
            if (queue.isEmpty()) return null
            val index = prefs.getInt("static_wallpaper_index", 0).coerceIn(0, queue.lastIndex)
            val directoryName = prefs.getString("static_wallpaper_queue_dir", "static_wallpaper_queue") ?: "static_wallpaper_queue"
            if (directoryName.isBlank() || directoryName == "." || directoryName == ".." || directoryName.contains('/') || directoryName.contains('\\')) return null
            return File(filesDir, directoryName).let { directory ->
                File(directory, queue[index]).takeIf { it.isFile && it.canRead() }
            }
        }

        private fun advanceQueue() {
            val prefs = getSharedPreferences("album_preferences", MODE_PRIVATE)
            val queue = readQueue(prefs.getString("static_wallpaper_queue", "[]"))
            if (queue.size <= 1) return
            val current = prefs.getInt("static_wallpaper_index", 0).coerceIn(0, queue.lastIndex)
            val next = when (prefs.getString("wallpaper_static_order", "InOrder")) {
                "TrueRandom" -> (queue.indices.filter { it != current }).random()
                "Shuffle" -> nextShuffledIndex(prefs, queue.size, current)
                else -> (current + 1) % queue.size
            }
            prefs.edit().putInt("static_wallpaper_index", next).apply()
        }

        private fun nextShuffledIndex(prefs: android.content.SharedPreferences, size: Int, current: Int): Int {
            val order = readIndexes(prefs.getString("static_wallpaper_shuffle", "[]"), size)
            val position = order.indexOf(current)
            if (position >= 0 && position + 1 < order.size) return order[position + 1]
            val next = (0 until size).shuffled()
            prefs.edit().putString("static_wallpaper_shuffle", JSONArray(next).toString()).apply()
            return next.first()
        }

        private fun readQueue(value: String?): List<String> = runCatching {
            val json = JSONArray(value ?: "[]")
            (0 until json.length()).map { json.getString(it) }
                .filter { it.isNotBlank() && it != "." && it != ".." && !it.contains('/') && !it.contains('\\') }
        }.getOrDefault(emptyList())

        private fun readIndexes(value: String?, size: Int): List<Int> = runCatching {
            val json = JSONArray(value ?: "[]")
            (0 until json.length()).map { json.getInt(it) }.filter { it in 0 until size }
        }.getOrDefault(emptyList())

        private fun returnSwitchEnabled() = getSharedPreferences("album_preferences", MODE_PRIVATE).getBoolean("wallpaper_static_switch_on_home", false)
        private fun allowBackground() = getSharedPreferences("album_preferences", MODE_PRIVATE).getBoolean("wallpaper_allow_background", false)

        private fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) sample *= 2
            return sample
        }
    }
}

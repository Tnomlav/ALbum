package com.example.album.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.widget.Toast
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.example.album.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONArray
import android.media.MediaMetadataRetriever
import androidx.media3.common.MimeTypes
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Starts the system wallpaper flow while preserving access to SAF and MediaStore URIs. */
fun setWallpaper(context: Context, item: MediaItem, english: Boolean) {
    if (item.isVideo) {
        setDynamicWallpaper(context, listOf(item), english)
        return
    }
    setStaticWallpaper(context, listOf(item), english)
}

/** Imports the complete static queue before opening the static wallpaper service. */
fun setStaticWallpaper(context: Context, items: List<MediaItem>, english: Boolean) {
    CoroutineScope(Dispatchers.Main.immediate).launch {
        val imported = withContext(Dispatchers.IO) {
            runCatching {
                val directory = File(context.filesDir, "static_wallpaper_queue_staging_${System.nanoTime()}").apply {
                    mkdirs()
                }
                val names = items.filterNot { it.isVideo }.mapIndexedNotNull { index, item ->
                    val target = File(directory, "image_${index.toString().padStart(5, '0')}.${item.name.substringAfterLast('.', "jpg")}")
                    context.contentResolver.openInputStream(item.uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    target.takeIf { it.isFile }?.name
                }
                if (names.isEmpty()) error("Unable to read image")
                val finalDirectory = File(context.filesDir, "static_wallpaper_queue")
                if (finalDirectory.exists() && !finalDirectory.deleteRecursively()) error("Unable to replace wallpaper queue")
                if (!directory.renameTo(finalDirectory)) error("Unable to prepare wallpaper queue")
                val preferences = context.getSharedPreferences("album_preferences", Context.MODE_PRIVATE)
                val editor = preferences.edit().putString("static_wallpaper_queue", JSONArray(names).toString()).putInt("static_wallpaper_index", 0)
                if (preferences.getString("wallpaper_static_order", "InOrder") == "Shuffle") {
                    val shuffle = names.indices.shuffled()
                    editor.putString("static_wallpaper_shuffle", JSONArray(shuffle).toString()).putInt("static_wallpaper_index", shuffle.first())
                }
                editor.apply()
                finalDirectory
            }.getOrNull()
        }
        if (imported == null) {
            Toast.makeText(context, if (english) "Unable to open wallpaper settings" else "无法打开壁纸设置", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val component = ComponentName(context, com.example.album.wallpaper.ImageWallpaperService::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            }
        } else Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, if (english) "Unable to open wallpaper settings" else "无法打开壁纸设置", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Uses a composed still image as the only item in the static wallpaper queue. */
fun setStaticWallpaperBitmap(context: Context, bitmap: Bitmap, english: Boolean) {
    CoroutineScope(Dispatchers.Main.immediate).launch {
        val imported = withContext(Dispatchers.IO) {
            try {
                runCatching {
                    val directory = File(context.filesDir, "static_wallpaper_queue_staging_${System.nanoTime()}").apply { mkdirs() }
                    val target = File(directory, "image_00000.jpg")
                    target.outputStream().use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) error("Unable to encode image")
                    }
                    val finalDirectory = File(context.filesDir, "static_wallpaper_queue")
                    if (finalDirectory.exists() && !finalDirectory.deleteRecursively()) error("Unable to replace wallpaper queue")
                    if (!directory.renameTo(finalDirectory)) error("Unable to prepare wallpaper queue")
                    context.getSharedPreferences("album_preferences", Context.MODE_PRIVATE).edit()
                        .putString("static_wallpaper_queue", JSONArray(listOf(target.name)).toString())
                        .putInt("static_wallpaper_index", 0)
                        .apply()
                    finalDirectory
                }.getOrNull()
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        if (imported == null) {
            Toast.makeText(context, if (english) "Unable to save wallpaper" else "无法保存壁纸", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val component = ComponentName(context, com.example.album.wallpaper.ImageWallpaperService::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            }
        } else Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, if (english) "Unable to open wallpaper settings" else "无法打开壁纸设置", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Imports the complete dynamic queue before opening the system live-wallpaper preview. */
fun setDynamicWallpaper(context: Context, items: List<MediaItem>, english: Boolean) {
    CoroutineScope(Dispatchers.Main.immediate).launch {
        val copied = runCatching {
            val directory = withContext(Dispatchers.IO) {
                File(context.filesDir, "live_wallpaper_queue_staging_${System.nanoTime()}").apply {
                    mkdirs()
                }
            }
            val lowPower = context.getSharedPreferences("album_preferences", Context.MODE_PRIVATE)
                .getBoolean("wallpaper_low_power", false) ||
                (context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true)
            val names = items.filter { it.isVideo }.mapIndexedNotNull { index, item ->
                val target = File(directory, "video_${index.toString().padStart(5, '0')}.mp4")
                val success = if (lowPower) transcodeLowPowerVideo(context, item, target) else withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(item.uri)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Unable to read video")
                    }.isSuccess
                }
                target.takeIf { success && it.isFile }?.name
            }
            if (names.isEmpty()) error("Unable to read video")
            val finalDirectory = File(context.filesDir, "live_wallpaper_queue")
            if (finalDirectory.exists() && !finalDirectory.deleteRecursively()) error("Unable to replace video queue")
            if (!directory.renameTo(finalDirectory)) error("Unable to prepare wallpaper queue")
            val preferences = context.getSharedPreferences("album_preferences", Context.MODE_PRIVATE)
            val editor = preferences.edit()
                .putBoolean("wallpaper_low_power", lowPower)
                .putString("live_wallpaper_queue", JSONArray(names).toString())
                .putInt("live_wallpaper_index", 0)
            if (preferences.getString("wallpaper_video_order", "InOrder") == "Shuffle") {
                val shuffle = names.indices.shuffled()
                editor.putString("live_wallpaper_shuffle", JSONArray(shuffle).toString()).putInt("live_wallpaper_index", shuffle.first())
            }
            editor.apply()
            finalDirectory
        }.getOrNull()
        if (copied == null) {
            Toast.makeText(context, if (english) "Unable to import video" else "无法导入视频", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val component = ComponentName(context, com.example.album.wallpaper.VideoWallpaperService::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            }
        } else {
            Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, if (english) "Unable to open live wallpaper settings" else "无法打开动态壁纸设置", Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun transcodeLowPowerVideo(context: Context, item: MediaItem, target: File): Boolean {
    val retriever = MediaMetadataRetriever()
    val (width, height, frameRate) = runCatching {
        retriever.setDataSource(context, item.uri)
        Triple(
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: item.width,
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: item.height,
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
        )
    }.getOrElse { Triple(item.width, item.height, 30f) }
    retriever.release()
    val longest = maxOf(width, height).coerceAtLeast(1)
    val scale = minOf(1f, 1080f / longest.toFloat())
    val outputFrameRate = frameRate.coerceAtMost(30f).coerceAtLeast(24f).toInt()
    return suspendCancellableCoroutine { continuation ->
        val effect = ScaleAndRotateTransformation.Builder().setScale(scale, scale).build()
        val encoderSettings = VideoEncoderSettings.Builder().setBitrate(2_500_000).build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(encoderSettings).build())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(target.isFile)
                }

                override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                    target.delete()
                    if (continuation.isActive) continuation.resume(false)
                }
            })
            .build()
        continuation.invokeOnCancellation { transformer.cancel() }
        transformer.start(
            EditedMediaItem.Builder(androidx.media3.common.MediaItem.fromUri(item.uri))
                .setFrameRate(outputFrameRate)
                .setEffects(Effects(emptyList(), listOf(effect)))
                .build(),
            target.absolutePath
        )
    }
}

package com.example.album.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.app.WallpaperManager
import android.content.ComponentName
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.album.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Starts the system wallpaper flow while preserving access to SAF and MediaStore URIs. */
fun setWallpaper(context: Context, item: MediaItem, english: Boolean) {
    if (item.isVideo) {
        setLiveVideoWallpaper(context, item, english)
        return
    }
    val label = appText("设置为壁纸", english)
    CoroutineScope(Dispatchers.Main.immediate).launch {
        val temporaryUri = withContext(Dispatchers.IO) {
            try {
                val extension = item.mimeType.substringAfter('/', "img").ifBlank { "img" }
                val file = File(context.cacheDir, "wallpaper_${System.nanoTime()}.$extension")
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

        val candidates = buildList {
            temporaryUri?.let { uri ->
                add(Intent(Intent.ACTION_ATTACH_DATA).apply {
                    setDataAndType(uri, item.mimeType)
                    putExtra("mimeType", item.mimeType)
                })
            }
            add(Intent(Intent.ACTION_ATTACH_DATA).apply {
                setDataAndType(item.uri, item.mimeType)
                putExtra("mimeType", item.mimeType)
            })
            temporaryUri?.let { uri ->
                add(Intent(Intent.ACTION_SET_WALLPAPER).apply {
                    type = item.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                })
            }
            add(Intent(Intent.ACTION_SET_WALLPAPER))
        }

        val intent = candidates.firstOrNull { candidate ->
            context.packageManager.queryIntentActivities(candidate, 0).isNotEmpty()
        }
        if (intent == null) {
            Toast.makeText(context, if (english) "Unable to open wallpaper settings" else "无法打开壁纸设置", Toast.LENGTH_SHORT).show()
            return@launch
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(Intent.createChooser(intent, label).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure {
            Toast.makeText(context, if (english) "Unable to open wallpaper settings" else "无法打开壁纸设置", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Copies the source into app-private storage before opening the system live-wallpaper preview. */
private fun setLiveVideoWallpaper(context: Context, item: MediaItem, english: Boolean) {
    CoroutineScope(Dispatchers.Main.immediate).launch {
        val copied = withContext(Dispatchers.IO) {
            runCatching {
                val target = File(context.filesDir, "live_wallpaper_video")
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to read video")
                target
            }.getOrNull()
        }
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

package com.example.album.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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

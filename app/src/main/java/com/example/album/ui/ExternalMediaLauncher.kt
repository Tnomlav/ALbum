package com.example.album.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.album.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private suspend fun shareableUri(context: Context, item: MediaItem): Uri? = withContext(Dispatchers.IO) {
    if (item.uri.scheme != "file") return@withContext item.uri
    runCatching {
        val directory = File(context.cacheDir, "external_media")
        directory.mkdirs()
        val extension = item.name.substringAfterLast('.', "bin").ifBlank { "bin" }
        val file = File(directory, "${System.nanoTime()}.$extension")
        item.uri.path?.let(::File)?.inputStream()?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}

fun shareMedia(context: Context, items: List<MediaItem>, english: Boolean) {
    if (items.isEmpty()) return
    CoroutineScope(Dispatchers.Main.immediate).launch {
        val uris = items.mapNotNull { shareableUri(context, it) }
        if (uris.size != items.size) {
            Toast.makeText(context, if (english) "Unable to prepare these files" else "无法准备这些文件", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = when {
                items.all(MediaItem::isVideo) -> "video/*"
                items.none(MediaItem::isVideo) -> "image/*"
                else -> "*/*"
            }
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (context.packageManager.queryIntentActivities(intent, 0).isEmpty()) {
            Toast.makeText(context, if (english) "No app can share these files" else "没有可分享这些文件的应用", Toast.LENGTH_SHORT).show()
            return@launch
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, appText("分享媒体", english)).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure {
            Toast.makeText(context, if (english) "No app can share these files" else "没有可分享这些文件的应用", Toast.LENGTH_SHORT).show()
        }
    }
}

fun openMediaWith(context: Context, item: MediaItem, english: Boolean) {
    CoroutineScope(Dispatchers.Main.immediate).launch {
        val uri = shareableUri(context, item)
        if (uri == null) {
            Toast.makeText(context, if (english) "Unable to prepare this file" else "无法准备此文件", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (context.packageManager.queryIntentActivities(intent, 0).isEmpty()) {
            Toast.makeText(context, if (english) "No app can open this file" else "没有可打开此文件的应用", Toast.LENGTH_SHORT).show()
            return@launch
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, appText("打开方式", english)).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure {
            Toast.makeText(context, if (english) "No app can open this file" else "没有可打开此文件的应用", Toast.LENGTH_SHORT).show()
        }
    }
}

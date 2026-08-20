package com.example.album.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalFolderRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun addTree(uri: Uri) {
        val stored = treeUris().mapTo(linkedSetOf()) { it.toString() }
        stored += uri.toString()
        preferences.edit().putStringSet(KEY_TREE_URIS, stored).apply()
    }

    fun treeUris(): List<Uri> = preferences.getStringSet(KEY_TREE_URIS, emptySet())
        .orEmpty()
        .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }

    suspend fun loadMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        treeUris().flatMap { treeUri ->
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@flatMap emptyList()
            buildList { scan(root, root.name ?: "本地文件夹", this) }
        }.distinctBy { it.uri }.sortedByDescending { it.dateTaken }
    }

    private fun scan(file: DocumentFile, folderName: String, output: MutableList<MediaItem>) {
        if (file.isDirectory) {
            val childFolder = file.name?.takeIf { it.isNotBlank() } ?: folderName
            runCatching { file.listFiles() }.getOrDefault(emptyArray()).forEach { child ->
                scan(child, childFolder, output)
            }
            return
        }

        val mime = file.type ?: context.contentResolver.getType(file.uri).orEmpty()
        val isVideo = mime.startsWith("video/")
        if (!isVideo && !mime.startsWith("image/")) return
        val stableId = file.uri.toString().hashCode().toLong() and 0xffffffffL
        output += MediaItem(
            id = stableId,
            uri = file.uri,
            name = file.name ?: if (isVideo) "未命名视频" else "未命名图片",
            folder = folderName,
            dateTaken = file.lastModified().takeIf { it > 0 } ?: 0L,
            mimeType = mime.ifBlank { if (isVideo) "video/*" else "image/*" },
            size = file.length(),
            dateModified = file.lastModified().takeIf { it > 0 }?.div(1000L) ?: 0L,
            isVideo = isVideo,
            isDocument = true
        )
    }

    companion object {
        private const val PREFERENCES = "local_folder_preferences"
        private const val KEY_TREE_URIS = "tree_uris"
    }
}

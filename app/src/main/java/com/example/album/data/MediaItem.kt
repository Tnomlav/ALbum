package com.example.album.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val folder: String,
    val dateTaken: Long,
    val mimeType: String,
    val relativePath: String? = null,
    val size: Long = 0L,
    val dateModified: Long = 0L,
    val duration: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val isVideo: Boolean = false,
    val isDocument: Boolean = false
)

fun MediaItem.displayAddress(): String {
    val relative = relativePath?.trim()?.trim('/')
    if (!relative.isNullOrBlank()) return "$relative/$name"
    if (uri.scheme.equals("file", ignoreCase = true)) {
        uri.path?.let { path -> return Uri.decode(path) }
    }
    return uri.toString()
}

val MediaItem.displayAspectRatio: Float
    get() = if (width > 0 && height > 0) (width.toFloat() / height).coerceIn(.45f, 2.4f) else 1f

data class MediaAlbum(
    val name: String,
    val items: List<MediaItem>,
    // Keep the cover independent from the folder's current display sort.
    val coverItem: MediaItem? = null
) {
    val cover: MediaItem get() = coverItem ?: items.first()
}

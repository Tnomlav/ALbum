package com.example.album.data

import android.net.Uri
import android.provider.DocumentsContract

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

/** The stable folder identity used by transfer destinations. */
fun transferFolderPath(relativePath: String?, folder: String): String =
    relativePath?.trim('/')?.takeIf { it.isNotBlank() } ?: folder.trim('/').replace('\\', '/')

fun MediaItem.transferFolderPath(): String = transferFolderPath(relativePath, folder)

fun isSystemTrashedName(name: String?): Boolean = name.orEmpty().trimStart().startsWith(".trashed", ignoreCase = true)

fun MediaItem.isSystemTrashedFile(): Boolean = isSystemTrashedName(name)

fun MediaItem.displayAddress(): String {
    val relative = relativePath?.trim()?.trim('/')
    if (!relative.isNullOrBlank()) return "${Uri.decode(relative)}/$name"
    if (uri.scheme.equals("file", ignoreCase = true)) {
        uri.path?.let { path -> return Uri.decode(path) }
    }
    // SAF uses primary: as the document ID for the device's main shared
    // storage. Show the same relative path used by MediaStore instead of
    // exposing the provider-specific URI prefix.
    runCatching { DocumentsContract.getDocumentId(uri) }
        .getOrNull()
        ?.takeIf { it.startsWith("primary:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim('/')
        ?.takeIf { it.isNotBlank() }
        ?.let { return Uri.decode(it) }
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

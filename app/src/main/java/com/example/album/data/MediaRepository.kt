package com.example.album.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class MediaRepository(private val context: Context) {
    suspend fun loadImages(): List<MediaItem> = loadMedia(isVideo = false)

    suspend fun loadVideos(): List<MediaItem> = loadMedia(isVideo = true)

    suspend fun scanPublicMedia(): Int = withContext(Dispatchers.IO) {
        val publicRoots = listOf(
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_DOWNLOADS
        ).map(::publicDirectory).distinctBy(File::getAbsolutePath)
        // Most media is already indexed by MediaStore. Walking the complete
        // shared-storage root on every refresh made a normal scan needlessly
        // expensive, so only add known app folders outside public roots.
        val roots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            val sharedRoot = Environment.getExternalStorageDirectory()
            val appRoots = sharedRoot.listFiles().orEmpty()
                .filter { it.isDirectory && it.name.lowercase() in KNOWN_SCAN_DIRECTORIES }
            publicRoots + appRoots
        } else {
            publicRoots
        }
            .distinctBy(File::getAbsolutePath)
        val paths = roots.asSequence()
            .filter(File::isDirectory)
            .flatMap { root ->
                root.walkTopDown()
                    .onEnter { directory ->
                        !directory.name.startsWith('.') &&
                            (directory == root || !directory.name.equals("Android", ignoreCase = true))
                    }
                    .filter { file -> file.isFile && file.extension.lowercase() in MEDIA_EXTENSIONS }
            }
            .map(File::getAbsolutePath)
            .distinct()
            .toList()
        val indexedPaths = indexedMediaPaths()
        val unindexedPaths = paths.filterNot { it in indexedPaths }
        if (unindexedPaths.isEmpty()) return@withContext 0

        unindexedPaths.chunked(SCAN_BATCH_SIZE).sumOf { batch ->
            // Bound each request so a large shared-storage scan keeps making
            // progress and cannot wait forever for one missing callback.
            withTimeoutOrNull(SCAN_BATCH_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val remaining = AtomicInteger(batch.size)
                    val scanned = AtomicInteger(0)
                    val completed = AtomicBoolean(false)
                    try {
                        MediaScannerConnection.scanFile(context, batch.toTypedArray(), null) { _, uri ->
                            if (uri != null) scanned.incrementAndGet()
                            if (remaining.decrementAndGet() == 0 && completed.compareAndSet(false, true) && continuation.isActive) {
                                continuation.resume(scanned.get())
                            }
                        }
                    } catch (_: RuntimeException) {
                        if (completed.compareAndSet(false, true) && continuation.isActive) continuation.resume(0)
                    }
                }
            } ?: 0
        }
    }

    private fun indexedMediaPaths(): Set<String> {
        val collections = listOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        return buildSet {
            collections.forEach { collection ->
                runCatching {
                    context.contentResolver.query(
                        collection,
                        arrayOf(MediaStore.MediaColumns.DATA),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val pathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (pathIndex >= 0) while (cursor.moveToNext()) {
                            cursor.getString(pathIndex)?.let(::add)
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadMedia(isVideo: Boolean): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isVideo ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val dateColumn = if (isVideo) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            add(dateColumn)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
            if (isVideo) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()

        val items = mutableListOf<MediaItem>()
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "$dateColumn DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val folderIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val takenIndex = cursor.getColumnIndexOrThrow(dateColumn)
            val relativePathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            } else -1
            val durationIndex = if (isVideo) cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val taken = cursor.getLong(takenIndex).takeIf { it > 0 }
                    ?: cursor.getLong(addedIndex) * 1000L
                items += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = cursor.getString(nameIndex) ?: "未命名",
                    folder = cursor.getString(folderIndex) ?: "其他",
                    dateTaken = taken,
                    mimeType = cursor.getString(mimeIndex) ?: if (isVideo) "video/*" else "image/*",
                    relativePath = if (relativePathIndex >= 0) cursor.getString(relativePathIndex) else null,
                    size = cursor.getLong(sizeIndex),
                    dateModified = cursor.getLong(modifiedIndex),
                    duration = if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L,
                    width = cursor.getInt(widthIndex),
                    height = cursor.getInt(heightIndex),
                    isVideo = isVideo
                )
            }
        }
        items
    }

    fun delete(item: MediaItem): Int = context.contentResolver.delete(item.uri, null, null)

    fun rename(item: MediaItem, newName: String): MediaItem? = runCatching {
        if (item.isDocument) {
            val renamedUri = DocumentsContract.renameDocument(context.contentResolver, item.uri, newName) ?: return null
            item.copy(uri = renamedUri, name = newName)
        } else {
            val changed = context.contentResolver.update(
                item.uri,
                ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) },
                null,
                null
            )
            if (changed > 0) item.copy(name = newName) else null
        }
    }.getOrNull()

    fun renameFolder(items: List<MediaItem>, newName: String): Int = runCatching {
        val normalized = newName.trim().trim('/')
        if (normalized.isBlank()) return 0
        items.count { item ->
            if (item.isDocument || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@count false
            val root = if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            val relativePath = "$root/$normalized/"
            context.contentResolver.update(
                item.uri,
                ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) },
                null,
                null
            ) > 0
        }
    }.getOrDefault(0)

    suspend fun transfer(
        item: MediaItem,
        destinationFolder: String,
        conflictPolicy: ConflictPolicy,
        preserveModifiedDate: Boolean
    ): TransferResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val root = if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val relativePath = "$root/${destinationFolder.trim('/')}"
        val collection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.isVideo ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            item.isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val existing = findDestination(collection, relativePath, item.name)
        if (existing != null && conflictPolicy == ConflictPolicy.Skip) {
            return@withContext TransferResult(item, success = true, skipped = true, targetName = item.name)
        }
        val targetName = if (existing != null && conflictPolicy == ConflictPolicy.KeepBoth) {
            availableName(collection, relativePath, item.name)
        } else item.name

        if (existing != null && conflictPolicy == ConflictPolicy.Overwrite) {
            runCatching { resolver.delete(existing, null, null) }
                .getOrElse { return@withContext TransferResult(item, success = false) }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            if (preserveModifiedDate) {
                put(MediaStore.MediaColumns.DATE_MODIFIED, item.dateTaken / 1000L)
                if (item.isVideo) put(MediaStore.Video.Media.DATE_TAKEN, item.dateTaken)
                else put(MediaStore.Images.Media.DATE_TAKEN, item.dateTaken)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val directory = File(Environment.getExternalStoragePublicDirectory(root), destinationFolder)
                if (!directory.exists()) directory.mkdirs()
                put(MediaStore.MediaColumns.DATA, File(directory, targetName).absolutePath)
            }
        }
        val target = resolver.insert(collection, values)
            ?: return@withContext TransferResult(item, success = false)
        runCatching {
            resolver.openInputStream(item.uri).use { input ->
                resolver.openOutputStream(target).use { output ->
                    requireNotNull(input)
                    requireNotNull(output)
                    input.copyTo(output)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(target, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    if (preserveModifiedDate) put(MediaStore.MediaColumns.DATE_MODIFIED, item.dateTaken / 1000L)
                }, null, null)
            }
            TransferResult(item, success = true, targetName = targetName)
        }.getOrElse {
            resolver.delete(target, null, null)
            TransferResult(item, success = false)
        }
    }

    private fun findDestination(collection: android.net.Uri, relativePath: String, name: String): android.net.Uri? {
        val resolver = context.contentResolver
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            args = arrayOf("${relativePath.trimEnd('/')}/", name)
        } else {
            val root = if (collection == MediaStore.Video.Media.EXTERNAL_CONTENT_URI) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            val path = File(Environment.getExternalStoragePublicDirectory(root), relativePath.substringAfter('/'))
            selection = "${MediaStore.MediaColumns.DATA} = ?"
            args = arrayOf(File(path, name).absolutePath)
        }
        return resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
        }
    }

    private fun availableName(collection: android.net.Uri, relativePath: String, original: String): String {
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val extension = if (dot > 0) original.substring(dot) else ""
        var index = 1
        var candidate: String
        do {
            candidate = "$base ($index)$extension"
            index++
        } while (findDestination(collection, relativePath, candidate) != null)
        return candidate
    }

    @Suppress("DEPRECATION")
    private fun publicDirectory(type: String): File =
        Environment.getExternalStoragePublicDirectory(type)

    private companion object {
        const val SCAN_BATCH_SIZE = 128
        const val SCAN_BATCH_TIMEOUT_MS = 30_000L
        val KNOWN_SCAN_DIRECTORIES = setOf(
            "pixiv", "pixivimages", "pixiv_images", "pixiv_download",
            "screenshots", "screenshot", "camera", "微信", "wechat", "weixin",
            "qq", "qqimages", "douyin", "抖音", "xiaohongshu", "rednote",
            "instagram", "whatsapp", "telegram", "baidu", "baidunetdisk", "百度网盘", "百度云"
        )
        val MEDIA_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif",
            "mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp", "ts", "mpeg", "mpg"
        )
    }

}

package com.example.album.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class MediaRepository(private val context: Context) {
    private val localFolders = LocalFolderRepository(context)
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
        val indexedPaths = indexedMediaPaths()
        suspend fun scanBatch(batch: List<String>): Int = withTimeoutOrNull(SCAN_BATCH_TIMEOUT_MS) {
            // Bound each request so a large shared-storage scan keeps making
            // progress and cannot wait forever for one missing callback.
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

        var totalScanned = 0
        val pending = ArrayList<String>(SCAN_BATCH_SIZE)
        for (root in roots.filter(File::isDirectory)) {
            for (file in root.walkTopDown().onEnter { directory ->
                !directory.name.startsWith('.') &&
                    (directory == root || !directory.name.equals("Android", ignoreCase = true))
            }) {
                currentCoroutineContext().ensureActive()
                if (!file.isFile || file.extension.lowercase() !in MEDIA_EXTENSIONS) continue
                if (file.absolutePath in indexedPaths) continue
                pending += file.absolutePath
                if (pending.size == SCAN_BATCH_SIZE) {
                    totalScanned += scanBatch(pending)
                    pending.clear()
                }
            }
        }
        if (pending.isNotEmpty()) totalScanned += scanBatch(pending)
        totalScanned
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

    fun delete(item: MediaItem): Int {
        if (item.isDocument) {
            // Some SAF providers reject DocumentFile.delete() even though
            // their resolver supports deleting the same document URI. Try
            // both APIs so a Move operation does not silently become Copy.
            val deleted = runCatching {
                DocumentFile.fromSingleUri(context, item.uri)?.delete() == true
            }.getOrDefault(false) || runCatching {
                context.contentResolver.delete(item.uri, null, null) > 0
            }.getOrDefault(false) || runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, item.uri)
                true
            }.getOrDefault(false)
            return if (deleted) 1 else 0
        }
        return context.contentResolver.delete(item.uri, null, null)
    }

    fun rename(item: MediaItem, newName: String): MediaItem? = runCatching {
        if (!item.isVideo && needsImageConversion(item, newName)) {
            if (item.isDocument) return@runCatching convertDocumentImageIfNeeded(item, newName)
            return@runCatching convertImageIfNeeded(item, newName)
        }
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

    private fun convertDocumentImageIfNeeded(item: MediaItem, newName: String): MediaItem? {
        val parent = localFolders.findAuthorizedParent(item.uri) ?: return null
        if (parent.findFile(newName) != null) return null
        val newExtension = newName.substringAfterLast('.', "").lowercase()
        val format = when (newExtension) {
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            else -> return null
        }
        val mime = when (newExtension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val target = parent.createFile(mime, newName) ?: return null
        return try {
            val bitmap = context.contentResolver.openInputStream(item.uri)?.use(BitmapFactory::decodeStream)
                ?: throw IOException("Unable to decode image")
            val written = context.contentResolver.openOutputStream(target.uri)?.use { output ->
                bitmap.compress(format, if (format == Bitmap.CompressFormat.PNG) 100 else 92, output)
            } == true
            bitmap.recycle()
            if (!written) throw IOException("Unable to encode image")
            if (DocumentFile.fromSingleUri(context, item.uri)?.delete() != true) {
                throw IOException("Unable to remove original image")
            }
            item.copy(
                uri = target.uri,
                name = newName,
                mimeType = mime,
                size = target.length()
            )
        } catch (_: OutOfMemoryError) {
            runCatching { target.delete() }
            null
        } catch (_: Exception) {
            runCatching { target.delete() }
            null
        }
    }

    private fun convertImageIfNeeded(item: MediaItem, newName: String): MediaItem? {
        val oldExtension = item.name.substringAfterLast('.', "").lowercase()
        val newExtension = newName.substringAfterLast('.', "").lowercase()
        val supported = setOf("jpg", "jpeg", "png", "webp")
        if (newExtension !in supported || oldExtension == newExtension ||
            (oldExtension == "jpg" && newExtension == "jpeg") ||
            (oldExtension == "jpeg" && newExtension == "jpg")
        ) return null

        val format = when (newExtension) {
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        val mime = when (newExtension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            item.relativePath?.trim('/')?.takeIf { it.isNotBlank() }?.let {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$it/")
            }
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = context.contentResolver.insert(collection, values) ?: return null
        return try {
            val bitmap = context.contentResolver.openInputStream(item.uri)?.use(BitmapFactory::decodeStream)
                ?: throw IOException("Unable to decode image")
            val written = context.contentResolver.openOutputStream(target)?.use { output ->
                bitmap.compress(format, if (format == Bitmap.CompressFormat.PNG) 100 else 92, output)
            } == true
            bitmap.recycle()
            if (!written) throw IOException("Unable to encode image")
            context.contentResolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            if (context.contentResolver.delete(item.uri, null, null) <= 0) throw IOException("Unable to remove original image")
            item.copy(
                uri = target,
                name = newName,
                mimeType = mime,
                size = context.contentResolver.openAssetFileDescriptor(target, "r")?.use { it.length } ?: 0L
            )
        } catch (_: OutOfMemoryError) {
            context.contentResolver.delete(target, null, null)
            null
        } catch (_: Exception) {
            context.contentResolver.delete(target, null, null)
            null
        }
    }

    private fun needsImageConversion(item: MediaItem, newName: String): Boolean {
        val oldExtension = item.name.substringAfterLast('.', "").lowercase()
        val newExtension = newName.substringAfterLast('.', "").lowercase()
        val supported = setOf("jpg", "jpeg", "png", "webp")
        return newExtension in supported && oldExtension != newExtension &&
            !(oldExtension == "jpg" && newExtension == "jpeg") &&
            !(oldExtension == "jpeg" && newExtension == "jpg")
    }

    fun renameFolder(items: List<MediaItem>, newName: String): Int = runCatching {
        val normalized = newName.trim().trim('/')
        if (normalized.isBlank()) return 0
        items.count { item ->
            if (item.isDocument || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@count false
            val oldPath = item.relativePath?.trim('/')
                ?: return@count false
            val parentPath = oldPath.substringBeforeLast('/', "")
            val relativePath = if (parentPath.isBlank()) "$normalized/" else "$parentPath/$normalized/"
            context.contentResolver.update(
                item.uri,
                ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) },
                null,
                null
            ) > 0
        }
    }.getOrDefault(0)

    suspend fun createFolder(parent: MediaItem, name: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = name.trim().trim('/')
        if (normalized.isBlank() || normalized.any { it in "\\/:*?\"<>|" }) return@withContext false
        val directCreated = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val dataColumn = arrayOf(MediaStore.MediaColumns.DATA)
                val parentDirectory = context.contentResolver.query(parent.uri, dataColumn, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.let(::File)?.parentFile else null
                } ?: return@runCatching false
                return@runCatching File(parentDirectory, normalized).mkdirs()
            }
            val root = if (parent.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            val relative = parent.relativePath?.trim('/')?.takeIf { it.isNotBlank() }
                ?: "$root/${parent.folder}"
            File(Environment.getExternalStorageDirectory(), "$relative/$normalized").mkdirs()
        }.getOrDefault(false)
        if (directCreated) return@withContext true
        val root = if (parent.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val relative = parent.relativePath?.trim('/')?.takeIf { it.isNotBlank() }
            ?: "$root/${parent.folder}"
        createFolderAtPath(relative, normalized, parent.isVideo)
    }

    suspend fun createFolderAtPath(path: String, name: String, isVideo: Boolean): Boolean = withContext(Dispatchers.IO) {
        val normalizedPath = path.trim('/').replace("\\", "/")
        val normalizedName = name.trim().trim('/')
        if (normalizedPath.isBlank() || normalizedName.isBlank() || normalizedName.any { it in "\\/:*?\"<>|" }) {
            return@withContext false
        }
        val directCreated = runCatching {
            val directory = File(Environment.getExternalStorageDirectory(), normalizedPath)
            val target = File(directory, normalizedName)
            target.mkdirs() || target.isDirectory
        }.getOrDefault(false)
        if (directCreated) return@withContext true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false

        // Scoped storage may reject mkdirs(). Creating a temporary file through
        // MediaStore still lets the provider materialize the requested folder.
        val relativePath = "$normalizedPath/$normalizedName/"
        val markerName = ".album_folder_marker_${System.currentTimeMillis()}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, markerName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val marker = runCatching {
            context.contentResolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        }.getOrNull() ?: return@withContext false
        return@withContext try {
            context.contentResolver.openOutputStream(marker)?.use { }
            context.contentResolver.update(
                marker,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            context.contentResolver.delete(marker, null, null)
            true
        } catch (_: Exception) {
            context.contentResolver.delete(marker, null, null)
            false
        }
    }

    /**
     * Moves a document into an existing directory without creating a second
     * copy first. This is shared by normal Move and Pixiv archive Move.
     */
    suspend fun moveUriToDirectory(
        sourceUri: Uri,
        destination: DocumentFile,
        targetName: String
    ): DirectMoveResult? = withContext(Dispatchers.IO) {
        if (!destination.isDirectory || !destination.canWrite()) return@withContext null

        // Keep provider-backed documents inside their provider. Renaming the
        // physical path directly can leave a stale MediaStore/SAF row and an
        // unusable source URI.
        if (!sourceUri.scheme.equals("file", ignoreCase = true) &&
            sourceUri.authority == destination.uri.authority
        ) {
            val parent = findPersistedDocumentParent(sourceUri) ?: return@withContext null
            val moved = runCatching {
                DocumentsContract.moveDocument(context.contentResolver, sourceUri, parent.uri, destination.uri)
            }.getOrNull() ?: return@withContext null
            val movedFile = DocumentFile.fromSingleUri(context, moved)
            val movedName = movedFile?.name
            if (!movedName.isNullOrBlank() && movedName != targetName) {
                val renamed = runCatching {
                    DocumentsContract.renameDocument(context.contentResolver, moved, targetName)
                }.getOrNull()
                if (renamed != null) return@withContext DirectMoveResult(renamed, targetName)
            }
            return@withContext DirectMoveResult(moved, movedName?.takeIf { it.isNotBlank() } ?: targetName)
        }

        // A file:// source has no provider row to invalidate, so a physical
        // rename is safe for that legacy/local-folder representation.
        if (!sourceUri.scheme.equals("file", ignoreCase = true)) return@withContext null
        val physicalSource = physicalFileForUri(sourceUri) ?: return@withContext null
        val physicalDestination = physicalFileForUri(destination.uri) ?: return@withContext null
        if (!physicalDestination.isDirectory) return@withContext null
        val target = File(physicalDestination, targetName)
        if (physicalSource.canonicalFile == target.canonicalFile) {
            return@withContext DirectMoveResult(Uri.fromFile(target), target.name)
        }
        if (target.exists()) return@withContext null
        if (!physicalSource.renameTo(target)) return@withContext null
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        DirectMoveResult(Uri.fromFile(target), target.name)
    }

    /** Deletes a source without using DocumentFile.delete(), which may trash it. */
    fun deleteUriPermanently(uri: Uri): Boolean {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val file = uri.path?.let(::File) ?: return false
            return !file.exists() || file.delete()
        }
        // Delete through the provider first so MediaStore and SAF metadata do
        // not retain a row whose physical file was removed behind its back.
        if (runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)) {
            return true
        }
        return runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
            true
        }.getOrDefault(false)
    }

    private fun physicalFileForUri(uri: Uri): File? {
        if (uri.scheme.equals("file", ignoreCase = true)) return uri.path?.let(::File)
        runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?.takeIf { it.startsWith("primary:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.let { relative -> File(Environment.getExternalStorageDirectory(), Uri.decode(relative.trim('/'))) }
            ?.takeIf { it.exists() }
            ?.let { return it }
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index)?.let(::File) else null
            }
        }.getOrNull()?.takeIf { it.exists() }
    }

    suspend fun transfer(
        item: MediaItem,
        destinationFolder: String,
        conflictPolicy: ConflictPolicy,
        preserveModifiedDate: Boolean,
        mode: TransferMode = TransferMode.Copy
    ): TransferResult = withContext(Dispatchers.IO) {
        try {
        // Prefer a provider-side MediaStore move before consulting SAF. A
        // destination can have a persisted tree permission even when the
        // source is MediaStore; checking SAF first silently downgraded Move
        // to copy-then-delete.
            if (mode == TransferMode.Move) {
                tryDirectMediaStoreMove(item, destinationFolder, conflictPolicy)?.let {
                    return@withContext it
                }
            }
        // A destination found through an authorized tree URI is a real folder,
        // including folders that contain no media and folders outside Pictures/Movies.
            localFolders.findAuthorizedDirectory(destinationFolder)?.let { directory ->
            if (mode == TransferMode.Move) {
                moveToDocumentDirectory(item, directory, conflictPolicy)?.let { return@withContext it }
                return@withContext moveByCopyAndPermanentDelete(item, directory, conflictPolicy)
            }
            return@withContext transferToDocumentDirectory(item, directory, conflictPolicy)
        }
        findWritablePhysicalDirectory(destinationFolder)?.let { directory ->
            if (mode == TransferMode.Move && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !item.isDocument) {
                mediaStoreRelativePathForDirectory(directory)?.let { relativePath ->
                    val collection = if (item.isVideo) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    }
                    val existing = findDestination(collection, relativePath, item.name)
                    if (existing == item.uri) {
                        return@withContext TransferResult(item, success = true, skipped = true, targetName = item.name, movedDirectly = true)
                    }
                    val targetName = if (existing != null && conflictPolicy == ConflictPolicy.KeepBoth) {
                        availableName(collection, relativePath, item.name)
                    } else item.name
                    if (existing != null && conflictPolicy == ConflictPolicy.Skip) {
                        return@withContext TransferResult(item, success = true, skipped = true, targetName = item.name)
                    }
                    if (existing != null && conflictPolicy == ConflictPolicy.Overwrite) {
                        runCatching { context.contentResolver.delete(existing, null, null) }
                            .getOrElse { return@withContext TransferResult(item, success = false) }
                    }
                    moveMediaStoreItem(item, collection, relativePath, existing, targetName, conflictPolicy)?.let {
                        return@withContext it
                    }
                }
            }
            if (mode == TransferMode.Move) {
                val destination = DocumentFile.fromFile(directory)
                moveUriToDirectory(item.uri, destination, item.name)?.let { moved ->
                    return@withContext TransferResult(
                        item,
                        success = true,
                        targetName = moved.name,
                        movedDirectly = true
                    )
                }
                return@withContext moveByCopyAndPermanentDelete(item, destination, conflictPolicy)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val root = if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                val collection = if (item.isVideo) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                mediaStoreRelativePathForDirectory(directory)?.let { relativePath ->
                    // A provider-side move may require user-granted media
                    // management access. Keep Move semantics by copying into
                    // MediaStore here, then let the caller remove the source.
                    return@withContext transferToMediaStoreDirectory(
                        item, collection, relativePath, conflictPolicy, preserveModifiedDate
                    )
                }
            }
            return@withContext transferToPhysicalDirectory(item, directory, conflictPolicy, preserveModifiedDate)
        }
        val resolver = context.contentResolver
        val root = if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val relativePath = resolveMediaStoreRelativePath(destinationFolder)
        val collection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.isVideo ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            item.isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val existing = findDestination(collection, relativePath, item.name)
        if (mode == TransferMode.Move && existing == item.uri) {
            return@withContext TransferResult(item, success = true, skipped = true, targetName = item.name, movedDirectly = true)
        }
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
        if (mode == TransferMode.Move) {
            moveMediaStoreItem(item, collection, relativePath, existing, targetName, conflictPolicy)?.let {
                return@withContext it
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
            put(MediaStore.MediaColumns.MIME_TYPE, transferMimeType(item))
            if (preserveModifiedDate) {
                put(MediaStore.MediaColumns.DATE_MODIFIED, item.dateTaken / 1000L)
                if (item.isVideo) put(MediaStore.Video.Media.DATE_TAKEN, item.dateTaken)
                else put(MediaStore.Images.Media.DATE_TAKEN, item.dateTaken)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val destinationPath = relativePath.removeSuffix("/")
                val directory = File(Environment.getExternalStorageDirectory(), destinationPath)
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
        } catch (_: Exception) {
            TransferResult(item, success = false)
        }
    }

    private fun transferToMediaStoreDirectory(
        item: MediaItem,
        collection: android.net.Uri,
        relativePath: String,
        conflictPolicy: ConflictPolicy,
        preserveModifiedDate: Boolean
    ): TransferResult {
        val resolver = context.contentResolver
        val existing = findDestination(collection, relativePath, item.name)
        if (existing == item.uri) {
            return TransferResult(item, success = true, skipped = true, targetName = item.name)
        }
        if (existing != null && conflictPolicy == ConflictPolicy.Skip) {
            return TransferResult(item, success = true, skipped = true, targetName = item.name)
        }
        val targetName = if (existing != null && conflictPolicy == ConflictPolicy.KeepBoth) {
            availableName(collection, relativePath, item.name)
        } else item.name
        if (existing != null && conflictPolicy == ConflictPolicy.Overwrite &&
            runCatching { resolver.delete(existing, null, null) }.getOrDefault(0) == 0
        ) return TransferResult(item, success = false)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
            put(MediaStore.MediaColumns.MIME_TYPE, transferMimeType(item))
            if (preserveModifiedDate) {
                put(MediaStore.MediaColumns.DATE_MODIFIED, item.dateTaken / 1000L)
                if (item.isVideo) put(MediaStore.Video.Media.DATE_TAKEN, item.dateTaken)
                else put(MediaStore.Images.Media.DATE_TAKEN, item.dateTaken)
            }
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(collection, values)
            ?: return TransferResult(item, success = false)
        return runCatching {
            resolver.openInputStream(item.uri).use { input ->
                resolver.openOutputStream(target).use { output ->
                    requireNotNull(input)
                    requireNotNull(output)
                    input.copyTo(output)
                }
            }
            resolver.update(target, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            TransferResult(item, success = true, targetName = targetName)
        }.getOrElse {
            resolver.delete(target, null, null)
            TransferResult(item, success = false)
        }
    }

    private fun tryDirectMediaStoreMove(
        item: MediaItem,
        destinationFolder: String,
        conflictPolicy: ConflictPolicy
    ): TransferResult? {
        if (item.isDocument || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val directory = findWritablePhysicalDirectory(destinationFolder) ?: return null
        val relativePath = mediaStoreRelativePathForDirectory(directory) ?: return null
        val collection = if (item.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val existing = findDestination(collection, relativePath, item.name)
        if (existing == item.uri) {
            return TransferResult(item, success = true, skipped = true, targetName = item.name, movedDirectly = true)
        }
        if (existing != null && conflictPolicy == ConflictPolicy.Skip) {
            return TransferResult(item, success = true, skipped = true, targetName = item.name)
        }
        val targetName = if (existing != null && conflictPolicy == ConflictPolicy.KeepBoth) {
            availableName(collection, relativePath, item.name)
        } else item.name
        if (existing != null && conflictPolicy == ConflictPolicy.Overwrite &&
            runCatching { context.contentResolver.delete(existing, null, null) }.getOrDefault(0) == 0
        ) return null
        return moveMediaStoreItem(item, collection, relativePath, existing, targetName, conflictPolicy)
    }

    private fun moveMediaStoreItem(
        item: MediaItem,
        collection: android.net.Uri,
        relativePath: String,
        existing: android.net.Uri?,
        targetName: String,
        conflictPolicy: ConflictPolicy
    ): TransferResult? {
        if (existing == item.uri) return TransferResult(item, success = true, skipped = true, targetName = item.name, movedDirectly = true)
        if (existing != null && conflictPolicy == ConflictPolicy.KeepBoth && targetName == item.name) return null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
        }
        val changed = runCatching { context.contentResolver.update(item.uri, values, null, null) }
            .getOrDefault(0)
        return if (changed > 0) {
            TransferResult(item, success = true, targetName = targetName, movedDirectly = true)
        } else null
    }

    private suspend fun moveToDocumentDirectory(
        item: MediaItem,
        directory: DocumentFile,
        conflictPolicy: ConflictPolicy
    ): TransferResult? {
        val existingNames = runCatching { directory.listFiles().mapNotNull { it.name }.toSet() }
            .getOrDefault(emptySet())
        val targetChoice = resolveTransferTargetName(item.name, existingNames, conflictPolicy)
        if (targetChoice.skipped) {
            return TransferResult(item, success = true, skipped = true, targetName = item.name, movedDirectly = true)
        }
        val existing = runCatching { directory.findFile(item.name) }.getOrNull()
        if (existing?.uri == item.uri) {
            return TransferResult(item, success = true, skipped = true, targetName = item.name, movedDirectly = true)
        }
        if (existing != null && conflictPolicy == ConflictPolicy.Overwrite && !deleteUriPermanently(existing.uri)) return null
        val moved = moveUriToDirectory(item.uri, directory, targetChoice.name) ?: return null
        return TransferResult(item, success = true, targetName = moved.name, movedDirectly = true)
    }

    private fun moveByCopyAndPermanentDelete(
        item: MediaItem,
        directory: DocumentFile,
        conflictPolicy: ConflictPolicy
    ): TransferResult {
        if (!directory.isDirectory || !directory.canWrite()) return TransferResult(item, success = false)
        val targetChoice = resolveTransferTargetName(
            item.name,
            runCatching { directory.listFiles().mapNotNull { it.name }.toSet() }.getOrDefault(emptySet()),
            conflictPolicy
        )
        if (targetChoice.skipped) return TransferResult(item, success = true, skipped = true, targetName = item.name)
        val existing = runCatching { directory.findFile(item.name) }.getOrNull()
        if (existing != null && conflictPolicy == ConflictPolicy.Overwrite && !deleteUriPermanently(existing.uri)) {
            return TransferResult(item, success = false)
        }
        val target = runCatching { directory.createFile(transferMimeType(item), targetChoice.name) }.getOrNull()
            ?: return TransferResult(item, success = false)
        return runCatching {
            context.contentResolver.openInputStream(item.uri).use { input ->
                context.contentResolver.openOutputStream(target.uri).use { output ->
                    requireNotNull(input)
                    requireNotNull(output)
                    input.copyTo(output)
                }
            }
            if (!target.exists()) throw IOException("Target file was not created")
            if (!deleteUriPermanently(item.uri)) throw IOException("Unable to permanently remove original")
            // The source has already been removed here. Tell the caller not to
            // issue a second MediaStore/SAF delete request.
            TransferResult(item, success = true, targetName = targetChoice.name, movedDirectly = true)
        }.getOrElse {
            deleteUriPermanently(target.uri)
            TransferResult(item, success = false)
        }
    }

    private fun findPersistedDocumentParent(targetUri: Uri): DocumentFile? {
        val treeUris = buildSet {
            addAll(localFolders.treeUris())
            addAll(
                context.contentResolver.persistedUriPermissions
                    .map { it.uri }
                    .filter { DocumentsContract.isTreeUri(it) }
            )
        }
        return treeUris.asSequence()
            .mapNotNull { DocumentFile.fromTreeUri(context, it) }
            .mapNotNull { findDocumentParent(it, targetUri) }
            .firstOrNull()
    }

    private fun findDocumentParent(directory: DocumentFile, targetUri: Uri): DocumentFile? {
        if (!directory.isDirectory) return null
        for (child in runCatching { directory.listFiles() }.getOrDefault(emptyArray())) {
            if (child.uri == targetUri) return directory
            if (child.isDirectory) findDocumentParent(child, targetUri)?.let { return it }
        }
        return null
    }

    private fun transferToDocumentDirectory(
        item: MediaItem,
        directory: DocumentFile,
        conflictPolicy: ConflictPolicy
    ): TransferResult {
        if (!directory.isDirectory || !directory.canWrite()) return TransferResult(item, success = false)
        val existing = runCatching { directory.findFile(item.name) }.getOrNull()
        val targetChoice = resolveTransferTargetName(
            item.name,
            runCatching { directory.listFiles().mapNotNull { it.name }.toSet() }.getOrDefault(emptySet()),
            conflictPolicy
        )
        if (targetChoice.skipped) {
            return TransferResult(item, success = true, skipped = true, targetName = item.name)
        }
        val targetName = targetChoice.name
        if (existing != null && conflictPolicy == ConflictPolicy.Overwrite &&
            !runCatching { existing.delete() }.getOrDefault(false)
        ) return TransferResult(item, success = false)

        val target = runCatching {
            directory.createFile(transferMimeType(item), targetName)
        }.getOrNull() ?: return TransferResult(item, success = false)
        return try {
            context.contentResolver.openInputStream(item.uri).use { input ->
                context.contentResolver.openOutputStream(target.uri).use { output ->
                    requireNotNull(input)
                    requireNotNull(output)
                    input.copyTo(output)
                }
            }
            if (!target.exists()) throw IOException("Target file was not created")
            TransferResult(item, success = true, targetName = targetName)
        } catch (_: Exception) {
            runCatching { target.delete() }
            TransferResult(item, success = false)
        }
    }

    private fun findWritablePhysicalDirectory(path: String): File? {
        val normalized = path.trim('/').replace("\\", "/")
        if (normalized.isBlank()) return null
        val external = Environment.getExternalStorageDirectory()
        // Destination paths are relative to shared storage, not implicitly
        // relative to Pictures. Do not silently select Pictures/<leaf> when
        // the exact target path cannot be resolved.
        return File(external, normalized).takeIf { it.isDirectory && it.canWrite() }
    }

    /** Returns the directory path relative to shared storage. */
    private fun mediaStoreRelativePathForDirectory(directory: File): String? {
        val actual = runCatching { directory.canonicalFile }.getOrNull() ?: return null
        val rootPath = Environment.getExternalStorageDirectory().canonicalPath
        val actualPath = actual.path
        if (!actualPath.startsWith("$rootPath${File.separator}")) return null
        val child = actualPath.removePrefix(rootPath).trim(File.separatorChar, '/')
        return child.takeIf { it.isNotBlank() }?.let { "$it/" }
    }

    private fun transferToPhysicalDirectory(
        item: MediaItem,
        directory: File,
        conflictPolicy: ConflictPolicy,
        preserveModifiedDate: Boolean
    ): TransferResult {
        val original = File(directory, item.name)
        val targetChoice = resolveTransferTargetName(
            item.name,
            directory.listFiles().orEmpty().map { it.name }.toSet(),
            conflictPolicy
        )
        if (targetChoice.skipped) {
            return TransferResult(item, success = true, skipped = true, targetName = item.name)
        }
        val targetName = targetChoice.name
        val target = File(directory, targetName)
        if (target.exists() && conflictPolicy == ConflictPolicy.Overwrite && !target.delete()) {
            return TransferResult(item, success = false)
        }
        return try {
            context.contentResolver.openInputStream(item.uri).use { input ->
                target.outputStream().use { output ->
                    requireNotNull(input)
                    input.copyTo(output)
                }
            }
            if (!target.isFile) throw IOException("Target file was not created")
            if (preserveModifiedDate) {
                val timestamp = if (item.dateModified > 0L) item.dateModified * 1000L else item.dateTaken
                if (timestamp > 0L) target.setLastModified(timestamp)
            }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(transferMimeType(item)), null)
            TransferResult(item, success = true, targetName = targetName)
        } catch (_: Exception) {
            runCatching { target.delete() }
            TransferResult(item, success = false)
        }
    }

    private fun transferMimeType(item: MediaItem): String {
        val declared = item.mimeType.takeIf { it.contains('/') && !it.endsWith("/*") }
        if (declared != null) return declared
        val extension = item.name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: if (item.isVideo) "video/mp4" else "image/jpeg"
    }

    private fun findDestination(collection: android.net.Uri, relativePath: String, name: String): android.net.Uri? {
        val resolver = context.contentResolver
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            args = arrayOf("${relativePath.trimEnd('/')}/", name)
        } else {
            val path = File(Environment.getExternalStorageDirectory(), relativePath.trim('/'))
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

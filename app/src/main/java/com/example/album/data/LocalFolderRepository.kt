package com.example.album.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque

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

    suspend fun createFolderForFile(fileUri: Uri, name: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = name.trim()
        if (normalized.isBlank()) return@withContext false
        val authorizedParent = treeUris()
            .asSequence()
            .mapNotNull { DocumentFile.fromTreeUri(context, it) }
            .mapNotNull { findParentDirectory(it, fileUri) }
            .firstOrNull()
        if (authorizedParent != null) {
            return@withContext runCatching { authorizedParent.createDirectory(normalized)?.exists() == true }
                .getOrDefault(false)
        }
        runCatching {
            val documentId = DocumentsContract.getDocumentId(fileUri)
            val authority = fileUri.authority ?: return@runCatching false
            val rootId = documentId.substringBefore(':', documentId) + ":"
            val rootUri = DocumentsContract.buildTreeDocumentUri(authority, rootId)
            val root = DocumentFile.fromTreeUri(context, rootUri)
            val relativePath = documentId.substringAfter(':', "")
                .substringBeforeLast('/', "")
                .split('/')
                .filter(String::isNotBlank)
            val parent = relativePath.fold(root) { current, segment ->
                current?.findFile(Uri.decode(segment))?.takeIf { it.isDirectory }
            }
            parent?.createDirectory(normalized)?.exists() == true
        }.getOrDefault(false)
    }

    suspend fun createFolderAtPath(path: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedName = name.trim()
        val segments = path.trim('/').split('/').filter(String::isNotBlank)
        if (normalizedName.isBlank() || segments.isEmpty()) return@withContext false

        treeUris().asSequence().mapNotNull { DocumentFile.fromTreeUri(context, it) }
            .mapNotNull { root -> findDirectoryByPath(root, segments) }
            .firstOrNull()
            ?.let { parent ->
                runCatching { parent.createDirectory(normalizedName)?.exists() == true }
                    .getOrDefault(false)
            }
            ?: false
    }

    /** Resolves a displayed folder path to the actual authorized SAF directory. */
    fun findAuthorizedDirectory(path: String): DocumentFile? {
        val segments = path.trim('/').split('/').filter(String::isNotBlank)
        if (segments.isEmpty()) return null
        return treeUris().asSequence()
            .mapNotNull { DocumentFile.fromTreeUri(context, it) }
            .mapNotNull { root -> findDirectoryByPath(root, segments) }
            .firstOrNull()
    }

    fun findAuthorizedParent(fileUri: Uri): DocumentFile? = treeUris().asSequence()
        .mapNotNull { DocumentFile.fromTreeUri(context, it) }
        .mapNotNull { findParentDirectory(it, fileUri) }
        .firstOrNull()

    private fun findParentDirectory(directory: DocumentFile, targetUri: Uri): DocumentFile? {
        if (!directory.isDirectory) return null
        for (child in runCatching { directory.listFiles() }.getOrDefault(emptyArray())) {
            if (child.uri == targetUri) return directory
            if (child.isDirectory) findParentDirectory(child, targetUri)?.let { return it }
        }
        return null
    }

    private fun findDirectoryByPath(root: DocumentFile, segments: List<String>): DocumentFile? {
        if (!root.isDirectory) return null
        if (root.name.equals(segments.first(), ignoreCase = true)) {
            var current = root
            for (segment in segments.drop(1)) {
                current = runCatching {
                    current.listFiles().firstOrNull { child ->
                        child.isDirectory && child.name.equals(segment, ignoreCase = true)
                    }
                }
                    .getOrNull()
                    ?.takeIf { it.isDirectory }
                    ?: return null
            }
            return current
        }
        return runCatching { root.listFiles() }.getOrDefault(emptyArray())
            .asSequence()
            .filter { it.isDirectory }
            .mapNotNull { findDirectoryByPath(it, segments) }
            .firstOrNull()
    }

    suspend fun loadMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val listFilesGate = Semaphore(8)
        coroutineScope {
            treeUris().map { treeUri ->
                async {
                    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@async emptyList()
                    val rootName = root.name ?: "本地文件夹"
                    scan(root, rootName, rootName, listFilesGate)
                }
            }.awaitAll().flatten().distinctBy { it.uri }.sortedByDescending { it.dateTaken }
        }
    }

    /** Walks the authorized storage roots and reports the complete index once finished. */
    suspend fun streamFolderNames(
        onBatch: suspend (Set<String>) -> Unit,
        onChildBatch: suspend (Map<String, Set<String>>) -> Unit = { },
        onFileBatch: suspend (Map<String, Set<String>>) -> Unit = { }
    ) = withContext(Dispatchers.IO) {
        val batch = linkedSetOf<String>()
        val childBatch = linkedMapOf<String, MutableSet<String>>()
        val fileBatch = linkedMapOf<String, MutableSet<String>>()
        var visited = 0

        suspend fun addChild(parent: String, child: String) {
            childBatch.getOrPut(parent) { linkedSetOf() }.add(child)
        }

        suspend fun flush() {
            if (batch.isNotEmpty()) {
                onBatch(batch.toSet())
                batch.clear()
            }
            if (childBatch.isNotEmpty()) {
                onChildBatch(childBatch.mapValues { (_, children) -> children.toSet() })
                childBatch.clear()
            }
            if (fileBatch.isNotEmpty()) {
                onFileBatch(fileBatch.mapValues { (_, files) -> files.toSet() })
                fileBatch.clear()
            }
        }

        suspend fun addFile(folder: String?, name: String?) {
            if (!folder.isNullOrBlank() && !name.isNullOrBlank() &&
                !name.trimStart().startsWith(".trashed", ignoreCase = true)
            ) {
                fileBatch.getOrPut(folder) { linkedSetOf() }.add(name)
            }
            visited++
            if (visited % 1024 == 0) {
                yield()
            }
        }

        suspend fun add(name: String?) {
            if (!name.isNullOrBlank()) batch += name
            visited++
            if (visited % 1024 == 0) {
                yield()
            }
        }

        val canScanSharedStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        // A full-storage scan already includes user-selected tree roots. Avoid
        // traversing the same directories through the slower SAF API twice.
        if (!canScanSharedStorage) {
            treeUris().forEach { treeUri ->
                val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@forEach
                streamDirectories(root, null, ::add, ::addChild, ::addFile)
            }
        }
        if (canScanSharedStorage) {
            // A direct filesystem walk sees media, non-media, and empty
            // directories. Avoid querying the entire MediaStore.Files table
            // first; on large devices that query dominates search startup.
            val storageRoot = Environment.getExternalStorageDirectory()
            streamFileDirectories(storageRoot, ::add, ::addChild, ::addFile)
        } else {
            // Without all-files access, MediaStore is a useful fallback for
            // folders that contain indexed files, while SAF covers granted
            // tree roots and empty directories.
            streamIndexedFileFolders(::add, ::addChild, ::addFile)
        }
        flush()
    }

    private suspend fun streamIndexedFileFolders(
        add: suspend (String?) -> Unit,
        onChild: suspend (parent: String, child: String) -> Unit,
        onFile: suspend (folder: String?, name: String?) -> Unit
    ) {
        try {
            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME)
            } else {
                arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME)
            }
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"), projection, null, null, null
            )?.use { cursor ->
                val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relativeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val displayIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val storagePrefix = Environment.getExternalStorageDirectory().path.trimEnd('/') + "/"
                while (cursor.moveToNext()) {
                    val relativePath = if (relativeIndex >= 0) cursor.getString(relativeIndex).orEmpty() else ""
                    val dataPath = if (dataIndex >= 0) cursor.getString(dataIndex).orEmpty() else ""
                    val folderPath = relativePath.ifBlank {
                        dataPath.removePrefix(storagePrefix).substringBeforeLast('/', "")
                    }
                    val parts = folderPath.split('/').filter(String::isNotBlank)
                    parts.forEach { add(it) }
                    parts.zipWithNext().forEach { (parent, child) -> onChild(parent, child) }
                    onFile(parts.lastOrNull(), displayIndex.takeIf { it >= 0 }?.let(cursor::getString))
                }
            }
        } catch (_: Exception) {
            // MediaStore can reject a column on vendor Android builds.
        }
    }

    private suspend fun streamDirectories(
        file: DocumentFile,
        parentName: String?,
        add: suspend (String?) -> Unit,
        onChild: suspend (parent: String, child: String) -> Unit,
        onFile: suspend (folder: String?, name: String?) -> Unit
    ) {
        if (!file.isDirectory) return
        add(file.name)
        if (parentName != null && !file.name.isNullOrBlank()) onChild(parentName, file.name!!)
        val children = try {
            file.listFiles()
        } catch (_: Exception) {
            emptyArray()
        }
        children.forEach { child ->
            if (child.isDirectory) streamDirectories(child, file.name, add, onChild, onFile)
            else onFile(file.name, child.name)
        }
    }

    private suspend fun streamFileDirectories(
        root: File,
        add: suspend (String?) -> Unit,
        onChild: suspend (parent: String, child: String) -> Unit,
        onFile: suspend (folder: String?, name: String?) -> Unit
    ) {
        val pending = ArrayDeque<Pair<File, String?>>()
        pending.add(root to null)
        while (pending.isNotEmpty()) {
            val (directory, parentName) = pending.removeFirst()
            if (directory != root) {
                add(directory.name)
                if (!parentName.isNullOrBlank()) onChild(parentName, directory.name)
            }
            val children = try { directory.listFiles().orEmpty() } catch (_: Exception) { emptyArray() }
            children.forEach { child ->
                if (child.isDirectory) {
                    if (shouldIndexDirectory(child)) pending.addLast(child to directory.name)
                } else {
                    onFile(directory.name, child.name)
                }
            }
        }
    }

    private fun shouldIndexDirectory(directory: File): Boolean {
        if (directory.name.startsWith('.')) return false
        val path = directory.absolutePath.replace('\\', '/').lowercase()
        return "/android/data/" !in "$path/" && "/android/obb/" !in "$path/"
    }

    private suspend fun scan(
        file: DocumentFile,
        folderName: String,
        folderPath: String,
        listFilesGate: Semaphore
    ): List<MediaItem> {
        if (file.isDirectory) {
            val childFolder = file.name?.takeIf { it.isNotBlank() } ?: folderName
            val childPath = if (folderPath.isBlank()) childFolder else "$folderPath/$childFolder"
            val children = listFilesGate.withPermit {
                runCatching { file.listFiles() }.getOrDefault(emptyArray())
            }
            val directories = children.filter { it.isDirectory }
            val files = children.filterNot { it.isDirectory }
            return coroutineScope {
                val nested = directories.map { child ->
                    async { scan(child, childFolder, childPath, listFilesGate) }
                }
                val direct = files.mapNotNull { child -> mediaItem(child, childFolder, childPath) }
                nested.awaitAll().flatten() + direct
            }
        }

        return mediaItem(file, folderName, folderPath)?.let(::listOf).orEmpty()
    }

    private fun mediaItem(file: DocumentFile, folderName: String, folderPath: String): MediaItem? {
        val mime = file.type ?: context.contentResolver.getType(file.uri).orEmpty()
        val isVideo = mime.startsWith("video/")
        if (!isVideo && !mime.startsWith("image/")) return null
        val duration = if (isVideo) readVideoDuration(file.uri) else 0L
        val stableId = file.uri.toString().hashCode().toLong() and 0xffffffffL
        return MediaItem(
            id = stableId,
            uri = file.uri,
            name = file.name ?: if (isVideo) "未命名视频" else "未命名图片",
            folder = folderName,
            relativePath = folderPath.trim('/').takeIf { it.isNotBlank() },
            dateTaken = file.lastModified().takeIf { it > 0 } ?: 0L,
            mimeType = mime.ifBlank { if (isVideo) "video/*" else "image/*" },
            size = file.length(),
            duration = duration,
            dateModified = file.lastModified().takeIf { it > 0 }?.div(1000L) ?: 0L,
            isVideo = isVideo,
            isDocument = true
        )
    }

    private fun readVideoDuration(uri: Uri): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)

    companion object {
        private const val PREFERENCES = "local_folder_preferences"
        private const val KEY_TREE_URIS = "tree_uris"
    }
}

package com.example.album.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.album.data.MediaItem
import com.example.album.data.isSystemTrashedFile
import com.example.album.data.MediaRepository
import com.example.album.data.LocalFolderRepository
import com.example.album.data.CleanupRepository
import com.example.album.data.DuplicateGroup
import com.example.album.data.RecycleEntry
import com.example.album.data.ConflictPolicy
import com.example.album.data.TransferResult
import com.example.album.data.ThumbnailRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.ContextCompat

private data class RefreshResult(
    val images: List<MediaItem>,
    val videos: List<MediaItem>,
    val local: List<MediaItem>,
    val recycle: List<RecycleEntry>,
    val errors: List<String>
)

private data class PreparedMedia(
    val images: List<MediaItem>,
    val videos: List<MediaItem>,
    val localImages: List<MediaItem>,
    val localVideos: List<MediaItem>,
    val visibleImages: List<MediaItem>,
    val visibleVideos: List<MediaItem>,
    val excluded: List<MediaItem>
)

private data class IndexedFolderSnapshot(
    val names: Set<String>,
    val children: Map<String, Set<String>>,
    val files: Map<String, Set<String>>,
    val updatedAt: Long
)

class MediaLibraryState(context: Context) {
    private val appContext = context.applicationContext
    private val repository = MediaRepository(appContext)
    private val localFolders = LocalFolderRepository(appContext)
    private val cleanup = CleanupRepository(appContext)
    private val cleanupPreferences = context.getSharedPreferences("cleanup_preferences", Context.MODE_PRIVATE)
    private val settingsPreferences = context.getSharedPreferences("album_settings", Context.MODE_PRIVATE)
    private val folderIndexFile = File(appContext.filesDir, "searchable_folder_index.json")
    private var allImages: List<MediaItem> = emptyList()
    private var allVideos: List<MediaItem> = emptyList()
    private val refreshMutex = Mutex()
    private val folderNamesMutex = Mutex()
    private val folderIndexScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var folderIndexJob: Job? = null
    private var folderIndexJobAccessKey: String? = null
    private var folderNamesAccessKey: String? = null
    private var folderIndexUpdatedAt = 0L
    private companion object {
        // A cached directory index is cheap to reuse. Refresh it at most once
        // per two minutes unless a caller explicitly invalidates it.
        const val FOLDER_INDEX_REFRESH_INTERVAL_MS = 2 * 60 * 1000L
    }
    /*
    private val retentionDays = settingsPreferences.getString("retention", "60天")?.filter(Char::isDigit)?.toIntOrNull() ?: 60
    */
    private val retentionDays = settingsPreferences.getString("retention", "60")?.filter(Char::isDigit)?.toIntOrNull() ?: 60
    private var maintenanceReady = false

    fun close() {
        folderIndexJob?.cancel()
        folderIndexScope.cancel()
    }

    var images by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var videos by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var localImages by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var localVideos by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshError by mutableStateOf<String?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set
    var permissionGranted by mutableStateOf(false)
        private set
    var hasLocalFolders by mutableStateOf(false)
        private set
    var localFolderCount by mutableIntStateOf(0)
        private set
    var searchableFolderNames by mutableStateOf<Set<String>>(emptySet())
        private set
    var searchableFoldersLoading by mutableStateOf(false)
        private set
    var searchableFoldersReady by mutableStateOf(false)
        private set
    var searchableFolderChildren by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set
    var searchableFolderFiles by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set
    var excludedFolders by mutableStateOf(cleanupPreferences.getStringSet("excluded_folders", emptySet()).orEmpty().toSet())
        private set
    var excludedMedia by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    // Load recycle metadata during refresh instead of blocking construction.
    var recycleEntries by mutableStateOf<List<RecycleEntry>>(emptyList())
        private set

    /* init {
        val days = settingsPreferences.getString("retention", "60天")?.filter(Char::isDigit)?.toIntOrNull() ?: 60
    } */

    suspend fun refresh(
        granted: Boolean,
        scheduleThumbnailOptimization: Boolean = true
    ) = refreshMutex.withLock {
        permissionGranted = granted
        loading = true
        refreshError = null
        try {
            val canReadImages = granted && hasImageReadAccess(appContext)
            val canReadVideos = granted && hasVideoReadAccess(appContext)
            val refreshResult = coroutineScope {
                val errors = ConcurrentLinkedQueue<String>()
                val imagesTask = async {
                    if (!canReadImages) emptyList()
                    else try {
                        repository.loadImages()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        errors.add("图片读取失败")
                        emptyList()
                    }
                }
                val videosTask = async {
                    if (!canReadVideos) emptyList()
                    else try {
                        repository.loadVideos()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        errors.add("视频读取失败")
                        emptyList()
                    }
                }
                val localTask = async {
                    try {
                        localFolders.loadMedia()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        errors.add("本地文件夹读取失败")
                        emptyList()
                    }
                }
                val maintenanceTask = async(Dispatchers.IO) {
                    if (!maintenanceReady) {
                        try {
                            cleanup.purgeExpired(retentionDays)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Maintenance failure must not prevent the library from loading.
                        }
                        maintenanceReady = true
                    }
                    try {
                        cleanup.loadRecycleEntries()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                RefreshResult(imagesTask.await(), videosTask.await(), localTask.await(), maintenanceTask.await(), errors.toList())
            }
            refreshError = refreshResult.errors.takeIf { it.isNotEmpty() }?.joinToString("、")
            val prepared = withContext(Dispatchers.Default) {
                val showHiddenMedia = settingsPreferences.getBoolean("show_hidden_media", false)
                fun visibleName(item: MediaItem): Boolean =
                    !item.isSystemTrashedFile() && (showHiddenMedia || !item.name.trimStart().startsWith('.'))
                val normalizedImages = refreshResult.images.filter(::visibleName).distinctBy { it.uri }.sortedByDescending { it.dateTaken }
                val normalizedVideos = refreshResult.videos.filter(::visibleName).distinctBy { it.uri }.sortedByDescending { it.dateTaken }
                val normalizedLocalImages = refreshResult.local.filterNot { it.isVideo }.filter(::visibleName).distinctBy { it.uri }.sortedByDescending { it.dateTaken }
                val normalizedLocalVideos = refreshResult.local.filter { it.isVideo }.filter(::visibleName).distinctBy { it.uri }.sortedByDescending { it.dateTaken }
                val excluded = excludedFolders
                PreparedMedia(
                    images = normalizedImages,
                    videos = normalizedVideos,
                    localImages = normalizedLocalImages,
                    localVideos = normalizedLocalVideos,
                    visibleImages = normalizedImages.filterNot { it.folder in excluded },
                    visibleVideos = normalizedVideos.filterNot { it.folder in excluded },
                    excluded = (normalizedImages + normalizedVideos).filter { it.folder in excluded }
                )
            }
            recycleEntries = refreshResult.recycle
            val localFolderCountSnapshot = withContext(Dispatchers.IO) { localFolders.treeUris().size }
            hasLocalFolders = localFolderCountSnapshot > 0
            localFolderCount = localFolderCountSnapshot
            allImages = prepared.images
            allVideos = prepared.videos
            localImages = prepared.localImages
            localVideos = prepared.localVideos
            images = prepared.visibleImages
            videos = prepared.visibleVideos
            excludedMedia = prepared.excluded
            if (scheduleThumbnailOptimization) {
                ThumbnailRepository.scheduleBackgroundOptimization(
                    appContext,
                    (prepared.images + prepared.videos).distinctBy { it.uri },
                    settingsPreferences
                )
            } else {
                ThumbnailRepository.cancelBackgroundOptimization()
            }
        } finally {
            loading = false
        }
    }

    private suspend fun refreshSearchableFolderIndex(accessKey: String) {
        val names = linkedSetOf<String>()
        val children = linkedMapOf<String, MutableSet<String>>()
        val files = linkedMapOf<String, MutableSet<String>>()
        try {
            localFolders.streamFolderNames(
                onBatch = { batch -> names += batch },
                onChildBatch = { childBatch ->
                    childBatch.forEach { (parent, childNames) ->
                        children.getOrPut(parent) { linkedSetOf() } += childNames
                    }
                },
                onFileBatch = { fileBatch ->
                    fileBatch.forEach { (folder, fileNames) ->
                        files.getOrPut(folder) { linkedSetOf() } += fileNames
                    }
                }
            )
            writeFolderIndex(accessKey, names, children, files)
            withContext(Dispatchers.Main.immediate) {
                folderNamesAccessKey = accessKey
                folderIndexUpdatedAt = System.currentTimeMillis()
                searchableFolderNames = names.toSet()
                searchableFolderChildren = children.mapValues { (_, childNames) -> childNames.toSet() }
                searchableFolderFiles = files.mapValues { (_, fileNames) -> fileNames.toSet() }
                searchableFoldersLoading = false
                searchableFoldersReady = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            withContext(Dispatchers.Main.immediate) {
                searchableFoldersLoading = false
                searchableFoldersReady = true
            }
        }
    }

    suspend fun loadSearchableFolderNames() = folderNamesMutex.withLock {
        val accessKey = buildString {
            append(localFolders.treeUris().map(Uri::toString).sorted().joinToString("|"))
            append(";allFiles=")
            append(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager())
        }
        if (folderNamesAccessKey == accessKey &&
            folderIndexUpdatedAt > 0L &&
            System.currentTimeMillis() - folderIndexUpdatedAt < FOLDER_INDEX_REFRESH_INTERVAL_MS
        ) return@withLock
        if (folderIndexJob?.isActive == true && folderIndexJobAccessKey != accessKey) {
            folderIndexJob?.cancel()
            folderIndexJob = null
            folderIndexJobAccessKey = null
        }
        searchableFolderNames = emptySet()
        searchableFolderChildren = emptyMap()
        searchableFolderFiles = emptyMap()
        searchableFoldersLoading = true
        searchableFoldersReady = false
        // Reuse the last complete directory index immediately. Empty and
        // non-media folders are not represented by MediaStore, so this cache
        // keeps them searchable without making every app start scan storage.
        val loadedCache = readFolderIndex(accessKey)
        if (loadedCache) {
            folderNamesAccessKey = accessKey
            searchableFoldersLoading = false
            searchableFoldersReady = true
            val cacheIsFresh =
                folderIndexUpdatedAt > 0L &&
                System.currentTimeMillis() - folderIndexUpdatedAt < FOLDER_INDEX_REFRESH_INTERVAL_MS
            if (!cacheIsFresh && folderIndexJob?.isActive != true) {
                folderIndexJobAccessKey = accessKey
                folderIndexJob = folderIndexScope.launch {
                    refreshSearchableFolderIndex(accessKey)
                }
            }
            return@withLock
        }
        if (folderIndexJob?.isActive != true) {
            folderIndexJobAccessKey = accessKey
            folderIndexJob = folderIndexScope.launch {
                refreshSearchableFolderIndex(accessKey)
            }
        }
        /*
        // Keep the filesystem walk off the UI path. Publish one immutable
        // snapshot at the end so the search view and its loading animation do
        // not recompose for every filesystem batch.
        try {
            val names = linkedSetOf<String>()
            val children = linkedMapOf<String, MutableSet<String>>()
            val files = linkedMapOf<String, MutableSet<String>>()
            localFolders.streamFolderNames(
                onBatch = { batch ->
                    names += batch
                },
                onChildBatch = { childBatch ->
                    childBatch.forEach { (parent, childNames) ->
                        children.getOrPut(parent) { linkedSetOf() } += childNames
                    }
                },
                onFileBatch = { fileBatch ->
                    fileBatch.forEach { (folder, fileNames) ->
                        files.getOrPut(folder) { linkedSetOf() } += fileNames
                    }
                }
            )
            folderNamesAccessKey = accessKey
            withContext(Dispatchers.Main.immediate) {
                searchableFolderNames = names.toSet()
                searchableFolderChildren = children.mapValues { (_, childNames) -> childNames.toSet() }
                searchableFolderFiles = files.mapValues { (_, fileNames) -> fileNames.toSet() }
                searchableFoldersReady = true
            }
            writeFolderIndex(accessKey, names, children, files)
        } finally {
            searchableFoldersLoading = false
            searchableFoldersReady = true
        }
        */
    }

    private suspend fun readFolderIndex(accessKey: String): Boolean {
        val snapshot = withContext(Dispatchers.IO) {
            runCatching {
                val root = JSONObject(folderIndexFile.readText())
                if (root.optString("accessKey") != accessKey) return@runCatching null
                val names = root.optJSONArray("names").toStringSet()
                val children = root.optJSONObject("children").toSetMap()
                val files = root.optJSONObject("files").toSetMap()
                val updatedAt = root.optLong("updatedAt", 0L)
                IndexedFolderSnapshot(names, children, files, updatedAt)
            }.getOrNull()
        } ?: return false
        withContext(Dispatchers.Main.immediate) {
            searchableFolderNames = snapshot.names
            searchableFolderChildren = snapshot.children
            searchableFolderFiles = snapshot.files
        }
        folderIndexUpdatedAt = snapshot.updatedAt
        return true
    }

    private suspend fun writeFolderIndex(
        accessKey: String,
        names: Set<String>,
        children: Map<String, Set<String>>,
        files: Map<String, Set<String>>
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject().put("accessKey", accessKey)
                .put("updatedAt", System.currentTimeMillis())
                .put("names", JSONArray(names.toList()))
                .put("children", children.toJsonObject())
                .put("files", files.toJsonObject())
            val temporary = File(folderIndexFile.parentFile, "${folderIndexFile.name}.tmp")
            temporary.writeText(root.toString())
            if (!temporary.renameTo(folderIndexFile)) {
                temporary.delete()
            }
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        if (this@toStringSet == null) return@buildSet
        repeat(this@toStringSet!!.length()) { index ->
            this@toStringSet!!.optString(index).takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }

    private fun JSONObject?.toSetMap(): Map<String, Set<String>> = buildMap {
        val source = this@toSetMap ?: return@buildMap
        source.keys().forEach { key ->
            put(key, source.optJSONArray(key).toStringSet())
        }
    }

    private fun Map<String, Set<String>>.toJsonObject(): JSONObject = JSONObject().also { result ->
        forEach { (key, values) -> result.put(key, JSONArray(values.toList())) }
    }

    suspend fun scanAndRefresh(granted: Boolean, userInitiated: Boolean = false): MediaScanResult {
        if (!userInitiated) return MediaScanResult.NotRequested
        if (!granted) return MediaScanResult.PermissionRequired
        scanning = true
        return try {
            val scanned = try {
                repository.scanPublicMedia()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return MediaScanResult.Failed(error.message)
            }
            refresh(granted)
            MediaScanResult.Completed(scanned)
        } finally {
            scanning = false
        }
    }

    fun setBackgroundOptimization(enabled: Boolean) {
        if (enabled) {
            ThumbnailRepository.scheduleBackgroundOptimization(
                appContext,
                (allImages + allVideos).distinctBy { it.uri },
                settingsPreferences
            )
        } else {
            ThumbnailRepository.cancelBackgroundOptimization()
        }
    }

    suspend fun setShowHiddenMedia(enabled: Boolean) {
        settingsPreferences.edit().putBoolean("show_hidden_media", enabled).apply()
        refresh(permissionGranted)
    }

    fun remove(item: MediaItem) {
        if (item.isDocument) {
            if (item.isVideo) localVideos = localVideos.filterNot { it.uri == item.uri }
            else localImages = localImages.filterNot { it.uri == item.uri }
        } else if (item.isVideo) allVideos = allVideos.filterNot { it.uri == item.uri }
        else allImages = allImages.filterNot { it.uri == item.uri }
        applyExclusions()
    }

    fun remove(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val uris = items.mapTo(hashSetOf()) { it.uri }
        allImages = allImages.filterNot { it.uri in uris }
        allVideos = allVideos.filterNot { it.uri in uris }
        localImages = localImages.filterNot { it.uri in uris }
        localVideos = localVideos.filterNot { it.uri in uris }
        applyExclusions()
    }

    fun deleteLegacy(item: MediaItem): Boolean = runCatching { repository.delete(item) > 0 }.getOrDefault(false)

    fun rename(item: MediaItem, newName: String): MediaItem? {
        val renamed = repository.rename(item, newName) ?: return null
        if (item.isDocument) {
            if (item.isVideo) localVideos = localVideos.map { if (it.uri == item.uri) renamed else it }
            else localImages = localImages.map { if (it.uri == item.uri) renamed else it }
        } else {
            if (item.isVideo) videos = videos.map { if (it.uri == item.uri) renamed else it }
            else images = images.map { if (it.uri == item.uri) renamed else it }
            if (item.isVideo) allVideos = allVideos.map { if (it.uri == item.uri) renamed else it }
            else allImages = allImages.map { if (it.uri == item.uri) renamed else it }
        }
        return renamed
    }

    fun renameFolder(folder: String, newName: String): Int {
        val matching = (allImages + allVideos).filter { it.folder == folder }
        val renamed = repository.renameFolder(matching, newName)
        if (renamed > 0) applyExclusions()
        return renamed
    }

    suspend fun createFolder(parentItems: List<MediaItem>, name: String): Boolean {
        val parent = parentItems.firstOrNull() ?: return false
        val created = if (parent.isDocument) {
            localFolders.createFolderForFile(parent.uri, name)
        } else {
            repository.createFolder(parent, name)
        }
        if (created) refresh(permissionGranted)
        return created
    }

    suspend fun createTransferFolder(path: String, items: List<MediaItem>, name: String): Boolean {
        val localCreated = localFolders.createFolderAtPath(path, name)
        if (localCreated) return true

        val mediaKinds = items.map { it.isVideo }.distinct()
        return mediaKinds.any { isVideo -> repository.createFolderAtPath(path, name, isVideo) }
    }

    suspend fun transfer(
        items: List<MediaItem>,
        destinationFolder: String,
        conflictPolicy: ConflictPolicy,
        preserveModifiedDate: Boolean,
        mode: com.example.album.data.TransferMode = com.example.album.data.TransferMode.Copy
    ): List<TransferResult> = coroutineScope {
        // SAF and MediaStore both benefit from a small amount of parallelism,
        // while unbounded jobs make real devices slower due to I/O contention.
        val gate = Semaphore(3)
        items.map { item ->
            async {
                gate.withPermit {
                    repository.transfer(item, destinationFolder, conflictPolicy, preserveModifiedDate, mode)
                }
            }
        }.awaitAll()
    }

    suspend fun addLocalFolder(uri: android.net.Uri) {
        val folderCount = withContext(Dispatchers.IO) {
            localFolders.addTree(uri)
            localFolders.treeUris().size
        }
        hasLocalFolders = true
        localFolderCount = folderCount
        refresh(permissionGranted)
    }

    fun excludeFolder(folder: String) {
        excludedFolders = excludedFolders + folder
        cleanupPreferences.edit().putStringSet("excluded_folders", excludedFolders).apply()
        applyExclusions()
    }

    fun restoreExcludedFolder(folder: String) {
        excludedFolders = excludedFolders - folder
        cleanupPreferences.edit().putStringSet("excluded_folders", excludedFolders).apply()
        applyExclusions()
    }

    suspend fun findDuplicates(): List<DuplicateGroup> = cleanup.findExactDuplicates(allImages)

    suspend fun stageForRecycle(items: List<MediaItem>): List<RecycleEntry> {
        val staged = cleanup.stageForRecycle(items)
        recycleEntries = cleanup.loadRecycleEntries()
        return staged
    }

    suspend fun stageForSystemRecycle(items: List<MediaItem>): List<RecycleEntry> {
        val staged = cleanup.stageForSystemRecycle(items)
        recycleEntries = cleanup.loadRecycleEntries()
        return staged
    }

    fun discardRecycle(ids: Set<String>) {
        cleanup.discardRecycleEntries(ids)
        recycleEntries = cleanup.loadRecycleEntries()
    }

    suspend fun restoreRecycle(entry: RecycleEntry): Boolean {
        val restored = cleanup.restore(entry)
        recycleEntries = cleanup.loadRecycleEntries()
        if (restored) refresh(permissionGranted, scheduleThumbnailOptimization = false)
        return restored
    }

    suspend fun restoreRecycle(entries: List<RecycleEntry>): List<RecycleEntry> {
        val restored = cleanup.restore(entries)
        recycleEntries = cleanup.loadRecycleEntries()
        if (restored.isNotEmpty()) refresh(permissionGranted, scheduleThumbnailOptimization = false)
        return restored
    }

    fun permanentlyDeleteRecycle(entry: RecycleEntry) {
        cleanup.removeRecycleEntry(entry)
        recycleEntries = cleanup.loadRecycleEntries()
    }

    suspend fun permanentlyDeleteRecycle(entries: List<RecycleEntry>) {
        withContext(Dispatchers.IO) { cleanup.removeRecycleEntries(entries) }
        recycleEntries = cleanup.loadRecycleEntries()
    }

    fun removeRecycleRecords(entries: List<RecycleEntry>) {
        cleanup.discardRecycleEntries(entries.mapTo(mutableSetOf()) { it.id })
        recycleEntries = cleanup.loadRecycleEntries()
    }

    fun purgeExpiredRecycle(retentionDays: Int) {
        cleanup.purgeExpired(retentionDays)
        recycleEntries = cleanup.loadRecycleEntries()
    }

    val indexedMediaCount: Int
        get() = allImages.size + allVideos.size + localImages.size + localVideos.size

    private fun applyExclusions() {
        images = allImages.filterNot { it.folder in excludedFolders }
        videos = allVideos.filterNot { it.folder in excludedFolders }
        excludedMedia = (allImages + allVideos).filter { it.folder in excludedFolders }
    }
}

sealed interface MediaScanResult {
    data class Completed(val scannedFiles: Int) : MediaScanResult
    data class Failed(val reason: String?) : MediaScanResult
    data object PermissionRequired : MediaScanResult
    data object NotRequested : MediaScanResult
}

private fun hasImageReadAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    else -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

private fun hasVideoReadAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    else -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

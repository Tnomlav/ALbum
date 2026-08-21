package com.example.album.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.album.data.MediaItem
import com.example.album.data.MediaRepository
import com.example.album.data.LocalFolderRepository
import com.example.album.data.CleanupRepository
import com.example.album.data.DuplicateGroup
import com.example.album.data.RecycleEntry
import com.example.album.data.ConflictPolicy
import com.example.album.data.TransferResult
import com.example.album.data.ThumbnailRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class RefreshResult(
    val images: List<MediaItem>,
    val videos: List<MediaItem>,
    val local: List<MediaItem>,
    val recycle: List<RecycleEntry>,
    val folderNames: Set<String>
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

class MediaLibraryState(context: Context) {
    private val appContext = context.applicationContext
    private val repository = MediaRepository(appContext)
    private val localFolders = LocalFolderRepository(appContext)
    private val cleanup = CleanupRepository(appContext)
    private val cleanupPreferences = context.getSharedPreferences("cleanup_preferences", Context.MODE_PRIVATE)
    private val settingsPreferences = context.getSharedPreferences("album_settings", Context.MODE_PRIVATE)
    private var allImages: List<MediaItem> = emptyList()
    private var allVideos: List<MediaItem> = emptyList()
    private val refreshMutex = Mutex()
    /*
    private val retentionDays = settingsPreferences.getString("retention", "60天")?.filter(Char::isDigit)?.toIntOrNull() ?: 60
    */
    private val retentionDays = settingsPreferences.getString("retention", "60")?.filter(Char::isDigit)?.toIntOrNull() ?: 60
    private var maintenanceReady = false

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
        try {
            val refreshResult = coroutineScope {
                val imagesTask = async { if (granted) runCatching { repository.loadImages() }.getOrDefault(emptyList()) else emptyList() }
                val videosTask = async { if (granted) runCatching { repository.loadVideos() }.getOrDefault(emptyList()) else emptyList() }
                val localTask = async { runCatching { localFolders.loadMedia() }.getOrDefault(emptyList()) }
                val maintenanceTask = async(Dispatchers.IO) {
                    if (!maintenanceReady) {
                        runCatching { cleanup.purgeExpired(retentionDays) }
                        maintenanceReady = true
                    }
                    runCatching { cleanup.loadRecycleEntries() }.getOrDefault(emptyList())
                }
                val folderNamesTask = async { runCatching { localFolders.loadFolderNames() }.getOrDefault(emptySet()) }
                RefreshResult(imagesTask.await(), videosTask.await(), localTask.await(), maintenanceTask.await(), folderNamesTask.await())
            }
            val prepared = withContext(Dispatchers.Default) {
                val showHiddenMedia = settingsPreferences.getBoolean("show_hidden_media", false)
                fun visibleName(item: MediaItem): Boolean = showHiddenMedia || !item.name.trimStart().startsWith('.')
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
            searchableFolderNames = refreshResult.folderNames
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

    suspend fun scanAndRefresh(granted: Boolean, userInitiated: Boolean = false): MediaScanResult {
        if (!userInitiated) return MediaScanResult.NotRequested
        if (!granted) return MediaScanResult.PermissionRequired
        scanning = true
        return try {
            val scanned = runCatching { repository.scanPublicMedia() }
                .getOrElse { return MediaScanResult.Failed(it.message) }
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

    suspend fun transfer(
        items: List<MediaItem>,
        destinationFolder: String,
        conflictPolicy: ConflictPolicy,
        preserveModifiedDate: Boolean
    ): List<TransferResult> = items.map { item ->
        repository.transfer(item, destinationFolder, conflictPolicy, preserveModifiedDate)
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
        if (restored) refresh(permissionGranted)
        return restored
    }

    fun permanentlyDeleteRecycle(entry: RecycleEntry) {
        cleanup.removeRecycleEntry(entry)
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

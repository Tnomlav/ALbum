package com.example.album.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

object ThumbnailRepository {
    // Visible cells are user-facing and must win over maintenance work while
    // the user is scrolling through a large library.
    private val visibleDecodeSlots = Semaphore(5)
    private val prefetchDecodeSlots = Semaphore(1)
    private val originalDecodeSlots = Semaphore(1)
    private val keyLocks = ConcurrentHashMap<String, Mutex>()
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trimScheduled = AtomicBoolean(false)
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8L / 1024L).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    @Volatile private var lastTrimAt = 0L
    @Volatile private var backgroundOptimizationJob: Job? = null
    @Volatile private var backgroundOptimizationSignature: Int? = null

    fun quantizeSize(requestedSize: Int): Int = when {
        requestedSize <= 360 -> 360
        requestedSize <= 720 -> 720
        requestedSize <= 1440 -> 1440
        requestedSize <= 1800 -> 1800
        requestedSize <= 2400 -> 2400
        requestedSize <= 3600 -> 3600
        requestedSize <= 5400 -> 5400
        else -> 6000
    }

    fun peek(item: MediaItem, requestedSize: Int, preferences: SharedPreferences): Bitmap? {
        val generation = preferences.getLong("thumbnail_cache_generation", 0L)
        val key = cacheKey(item, quantizeSize(requestedSize), generation)
        return memory.get(key)?.takeUnless(Bitmap::isRecycled)
    }

    fun cacheBytes(context: Context): Long = thumbnailDirectory(context).walkTopDown()
        .filter(File::isFile)
        .sumOf(File::length)

    fun clear(context: Context, preferences: SharedPreferences): Long {
        val directory = thumbnailDirectory(context)
        val cleared = cacheBytes(context)
        backgroundOptimizationJob?.cancel()
        backgroundOptimizationJob = null
        backgroundOptimizationSignature = null
        memory.evictAll()
        directory.deleteRecursively()
        preferences.edit()
            .putLong("thumbnail_cache_generation", preferences.getLong("thumbnail_cache_generation", 0L) + 1L)
            .apply()
        return cleared
    }

    fun applyCacheLimit(context: Context, preferences: SharedPreferences) {
        val directory = thumbnailDirectory(context)
        if (directory.isDirectory) trim(directory, cacheLimit(preferences.getString("cache_limit", "自动")))
    }

    suspend fun load(
        context: Context,
        item: MediaItem,
        requestedSize: Int,
        preferences: SharedPreferences
    ): Bitmap? {
        // A real visible request is a stronger signal than background
        // optimization. Stop maintenance so fast scrolling is not competing
        // with a long-running thumbnail batch.
        if (backgroundOptimizationJob?.isActive == true) cancelBackgroundOptimization()
        return loadInternal(context, item, requestedSize, preferences, visibleDecodeSlots)
    }

    suspend fun loadOriginal(context: Context, item: MediaItem): Bitmap? = withContext(Dispatchers.IO) {
        if (item.isVideo) return@withContext null
        originalDecodeSlots.withPermit {
            val loaded = decodeOriginalBitmap(context, item) ?: return@withPermit null
            loaded.prepareToDraw()
            loaded
        }
    }

    suspend fun prefetch(
        context: Context,
        items: List<MediaItem>,
        requestedSize: Int,
        preferences: SharedPreferences
    ) = withContext(Dispatchers.IO) {
        items.asSequence()
            .distinctBy { it.uri }
            .filterNot {
                it.mimeType.equals("image/gif", ignoreCase = true) &&
                    preferences.getBoolean("gif_thumbnails", true)
            }
            .forEach { item ->
                ensureActive()
                yield()
                loadInternal(context, item, requestedSize, preferences, prefetchDecodeSlots)
            }
    }

    fun scheduleBackgroundOptimization(
        context: Context,
        items: List<MediaItem>,
        preferences: SharedPreferences
    ) {
        if (!preferences.getBoolean("background_optimization", true)) {
            cancelBackgroundOptimization()
            return
        }
        val signature = items.asSequence()
            .map { it.uri.toString() }
            .sorted()
            .fold(1) { hash, uri -> 31 * hash + uri.hashCode() }
        if (backgroundOptimizationJob?.isActive == true && backgroundOptimizationSignature == signature) return
        backgroundOptimizationJob?.cancel()
        backgroundOptimizationSignature = signature
        backgroundOptimizationJob = maintenanceScope.launch {
            // Background generation must remain bounded. Generating two sizes
            // for a large library can otherwise keep the device busy for a
            // long time after the user has left the page.
            val recentItems = items.take(BACKGROUND_FIRST_PASS_ITEMS)
            prefetch(context.applicationContext, recentItems, 360, preferences)
            delay(BACKGROUND_PASS_DELAY_MS)
            prefetch(
                context.applicationContext,
                recentItems.take(BACKGROUND_SECOND_PASS_ITEMS),
                720,
                preferences
            )
            // Do not immediately continue into the rest of the library. A
            // later user-visible request will generate those thumbnails on
            // demand without competing with scrolling and decoding.
        }
    }

    private const val BACKGROUND_FIRST_PASS_ITEMS = 160
    private const val BACKGROUND_SECOND_PASS_ITEMS = 80
    private const val BACKGROUND_PASS_DELAY_MS = 1_000L

    fun cancelBackgroundOptimization() {
        backgroundOptimizationJob?.cancel()
        backgroundOptimizationJob = null
        backgroundOptimizationSignature = null
    }

    private suspend fun loadInternal(
        context: Context,
        item: MediaItem,
        requestedSize: Int,
        preferences: SharedPreferences,
        slots: Semaphore
    ): Bitmap? = withContext(Dispatchers.IO) {
        val size = quantizeSize(requestedSize)
        val generation = preferences.getLong("thumbnail_cache_generation", 0L)
        val key = cacheKey(item, size, generation)
        memory.get(key)?.takeUnless(Bitmap::isRecycled)?.let { return@withContext it }
        val lock = keyLocks.getOrPut(key) { Mutex() }
        try {
            lock.withLock {
                memory.get(key)?.takeUnless(Bitmap::isRecycled)?.let { return@withLock it }
                slots.withPermit {
                    val diskCacheEnabled = preferences.getBoolean("background_optimization", true)
                    val cacheFile = cacheFile(context.cacheDir, key)
                    if (diskCacheEnabled && cacheFile.isFile) {
                        BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { cached ->
                            cached.prepareToDraw()
                            cacheFile.setLastModified(System.currentTimeMillis())
                            memory.put(key, cached)
                            return@withPermit cached
                        }
                    }
                    val loaded = loadPlatformThumbnail(context, item, size) ?: return@withPermit null
                    loaded.prepareToDraw()
                    memory.put(key, loaded)
                    if (diskCacheEnabled) {
                        persist(cacheFile, loaded)
                        scheduleTrim(cacheFile.parentFile!!, preferences)
                    }
                    loaded
                }
            }
        } finally {
            keyLocks.remove(key, lock)
        }
    }

    private fun loadPlatformThumbnail(context: Context, item: MediaItem, size: Int): Bitmap? = runCatching {
        if (item.uri.scheme == "file") {
            val path = item.uri.path ?: return@runCatching null
            if (item.isVideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createVideoThumbnail(File(path), Size(size, size), null)
                } else {
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND)
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val sample = calculateSample(bounds.outWidth, bounds.outHeight, size)
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(item.uri, Size(size, size), null)
        } else if (item.isVideo) {
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null
            )
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Thumbnails.getThumbnail(
                context.contentResolver, item.id, MediaStore.Images.Thumbnails.MINI_KIND, null
            )
        }
    }.getOrNull()

    private fun decodeOriginalBitmap(context: Context, item: MediaItem): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = if (item.uri.scheme == "file") {
                val path = item.uri.path ?: return@runCatching null
                ImageDecoder.createSource(File(path))
            } else {
                ImageDecoder.createSource(context.contentResolver, item.uri)
            }
            ImageDecoder.decodeBitmap(source)
        } else {
            openMediaInputStream(context, item.uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
        }
    }.getOrNull()

    private fun calculateSample(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }

    private fun persist(target: File, bitmap: Bitmap) {
        runCatching {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        }
    }

    private fun scheduleTrim(directory: File, preferences: SharedPreferences) {
        val now = System.currentTimeMillis()
        if (now - lastTrimAt < 30_000L || !trimScheduled.compareAndSet(false, true)) return
        maintenanceScope.launch {
            try {
                trim(directory, cacheLimit(preferences.getString("cache_limit", null)))
                lastTrimAt = System.currentTimeMillis()
            } finally {
                trimScheduled.set(false)
            }
        }
    }

    private fun cacheKey(item: MediaItem, size: Int, generation: Long): String {
        val identity = "${item.uri}|${item.size}|${item.dateModified}|$generation|$size"
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) } + "_$size"
    }

    private fun thumbnailDirectory(context: Context) = File(context.cacheDir, "media_thumbnails")

    private fun cacheFile(cacheDir: File, key: String) = File(File(cacheDir, "media_thumbnails"), "$key.jpg")

    private fun cacheLimit(setting: String?): Long = when (setting) {
        "250 MB" -> 250L * 1024 * 1024
        "500 MB" -> 500L * 1024 * 1024
        "1 GB" -> 1024L * 1024 * 1024
        else -> 128L * 1024 * 1024
    }

    private fun trim(directory: File, maxBytes: Long) {
        val files = directory.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.map { file -> file to file.lastModified() }
            ?.sortedBy { (_, lastModified) -> lastModified }
            ?.map { (file) -> file }
            ?: return
        var total = files.sumOf(File::length)
        files.forEach { file ->
            if (total <= maxBytes) return
            val length = file.length()
            if (file.delete()) total -= length
        }
    }
}

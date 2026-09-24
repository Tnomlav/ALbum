package com.example.album.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import org.json.JSONObject

data class DuplicateGroup(val hash: String, val items: List<MediaItem>)

/**
 * Positions of the pictures that share an aspect ratio (within [tolerance])
 * with at least one other picture.
 *
 * The duplicate matcher requires the aspect ratios to agree, so nothing outside
 * this set can be a duplicate. Decoding every picture in a large library took
 * minutes; this is what makes the scan skip most of it. Entries whose aspect is
 * unknown stay in the set: they are rare and must not be silently skipped.
 */
internal fun aspectCandidatePositions(aspects: List<Float>, tolerance: Float = .03f): Set<Int> {
    val candidates = mutableSetOf<Int>()
    val ordered = mutableListOf<Int>()
    aspects.forEachIndexed { position, aspect ->
        if (aspect.isFinite() && aspect > 0f) ordered += position else candidates += position
    }
    ordered.sortBy { aspects[it] }
    var left = 0
    ordered.forEachIndexed { right, _ ->
        while (aspects[ordered[right]] - aspects[ordered[left]] > tolerance * aspects[ordered[right]]) left++
        if (right > left) {
            for (position in left..right) candidates += ordered[position]
        }
    }
    return candidates
}

/**
 * Whether two fingerprints are the same picture rather than merely similar.
 *
 * Deliberately strict, because these groups are offered for deletion: at most
 * six of the sixty-four dHash bits may differ, the aspect ratio has to match
 * within three percent, and the average colour has to be within thirty of each
 * other. A re-encode, a resize or a quality change stays well inside that;
 * two different photos of the same scene usually do not.
 */
internal fun looksLikeSamePicture(
    hashA: Long,
    hashB: Long,
    aspectA: Float,
    aspectB: Float,
    redA: Float,
    greenA: Float,
    blueA: Float,
    redB: Float,
    greenB: Float,
    blueB: Float
): Boolean {
    if (java.lang.Long.bitCount(hashA xor hashB) > 6) return false
    if (!aspectA.isFinite() || !aspectB.isFinite()) return false
    val largerAspect = max(aspectA, aspectB)
    if (largerAspect <= 0f) return false
    if (kotlin.math.abs(aspectA - aspectB) / largerAspect > .03f) return false
    val red = (redA - redB).toDouble()
    val green = (greenA - greenB).toDouble()
    val blue = (blueA - blueB).toDouble()
    return kotlin.math.sqrt(red * red + green * green + blue * blue) <= 30.0
}

data class RecycleEntry(
    val id: String,
    val sourceUri: String,
    val storedPath: String,
    val originalName: String,
    val originalFolder: String,
    val originalRelativePath: String?,
    val mimeType: String,
    val dateTaken: Long,
    val duration: Long,
    val isVideo: Boolean,
    val deletedAt: Long,
    val systemTrashed: Boolean = false,
    /** MediaStore DATE_MODIFIED, stored in epoch seconds. */
    val dateModified: Long = 0L
)

class CleanupRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val analysisPreferences = context.getSharedPreferences(ANALYSIS_PREFERENCES, Context.MODE_PRIVATE)
    private val recycleDirectory = File(context.filesDir, "recycle_bin").apply { mkdirs() }

    suspend fun findExactDuplicates(items: List<MediaItem>): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        pruneAnalysisCache(items)
        val cacheEditor = analysisPreferences.edit()
        val sized = items.mapNotNull { item -> mediaSize(item)?.takeIf { it > 0 }?.let { it to item } }
        val result = sized.groupBy { it.first }
            .values
            .filter { it.size > 1 }
            .flatMap { candidates ->
                candidates.mapNotNull { (_, item) -> cachedSha256(item, cacheEditor)?.let { it to item } }
                    .groupBy({ it.first }, { it.second })
                    .filterValues { it.size > 1 }
                    .map { (hash, duplicates) -> DuplicateGroup(hash, duplicates.sortedByDescending { it.dateTaken }) }
            }
            .sortedByDescending { it.items.size }
        cacheEditor.apply()
        result
    }

    /**
     * Duplicate groups: files that are byte-for-byte identical **and** the ones
     * that are plainly the same picture after being re-encoded, resized or
     * saved at a different quality.
     *
     * The page used to compare SHA-256 only, so two copies that look identical
     * by eye never showed up unless their bytes matched exactly. The perceptual
     * fingerprint that could have caught them was computed by a function
     * nothing ever called.
     */
    suspend fun findDuplicateGroups(
        items: List<MediaItem>,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        pruneAnalysisCache(items)
        val cacheEditor = analysisPreferences.edit()
        // Two passes: hashing files that share a size, then decoding the
        // pictures that could be the same image at another size or quality.
        // Progress is reported over both, and every loop checks for cancellation
        // so leaving the page stops the work instead of pinning the CPU.
        val sized = items.mapIndexedNotNull { index, item ->
            mediaSize(item)?.takeIf { it > 0 }?.let { it to index }
        }
        val hashingCandidates = sized.groupBy({ it.first }, { it.second }).values
            .filter { it.size > 1 }
            .flatten()
        val pictures = items.withIndex().filterNot { it.value.isVideo }
        val candidatePositions = aspectCandidatePositions(
            pictures.map { it.value.width.toFloat() / it.value.height.coerceAtLeast(1) }
        )
        val decoding = pictures.filterIndexed { position, _ -> position in candidatePositions }
        val totalWork = hashingCandidates.size + decoding.size
        var completedWork = 0
        onProgress(completedWork, totalWork)

        val parents = IntArray(items.size) { it }
        fun root(value: Int): Int {
            var current = value
            while (parents[current] != current) {
                parents[current] = parents[parents[current]]
                current = parents[current]
            }
            return current
        }
        fun union(first: Int, second: Int) {
            val a = root(first)
            val b = root(second)
            if (a != b) parents[b] = a
        }

        // Identical bytes: only files of the same size can be, so the hashing
        // stays limited to the sizes that repeat.
        sized.groupBy({ it.first }, { it.second }).values.filter { it.size > 1 }.forEach { candidates ->
            coroutineContext.ensureActive()
            val hashed = candidates.mapNotNull { index ->
                coroutineContext.ensureActive()
                val value = cachedSha256(items[index], cacheEditor)?.let { it to index }
                completedWork++
                onProgress(completedWork, totalWork)
                value
            }
            hashed.groupBy({ it.first }, { it.second }).values.filter { it.size > 1 }.forEach { same ->
                same.drop(1).forEach { union(same.first(), it) }
            }
        }

        // The same picture at another size or quality. Eight 8-bit bands keep
        // the candidate search cheap while still guaranteeing a hit: a pair that
        // differs in at most six of sixty-four bits must share a whole band.
        // Decoding is the expensive part, so only pictures that could possibly
        // match are decoded (the matcher requires the aspect ratio to agree
        // within three percent) and the decodes run a few at a time.
        val pendingWrites = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())
        val fingerprints = coroutineScope {
            val gate = Semaphore(FINGERPRINT_CONCURRENCY)
            decoding
                .map { (index, item) ->
                    async {
                        gate.withPermit {
                            val value = cachedFingerprint(item) { key, stored -> pendingWrites += key to stored }
                                ?.let { index to it }
                            completedWork++
                            onProgress(completedWork, totalWork)
                            value
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
        pendingWrites.forEach { (key, value) -> cacheEditor.putString(key, value) }
        val buckets = mutableMapOf<Long, MutableList<Int>>()
        fingerprints.forEachIndexed { position, (index, candidate) ->
            val possible = mutableSetOf<Int>()
            repeat(8) { band ->
                val segment = (candidate.hash ushr (band * 8)) and 0xffL
                buckets[(band.toLong() shl 8) or segment]?.let(possible::addAll)
            }
            possible.forEach { otherPosition ->
                val other = fingerprints[otherPosition].second
                if (looksLikeSamePicture(candidate, other)) {
                    union(index, fingerprints[otherPosition].first)
                }
            }
            repeat(8) { band ->
                val segment = (candidate.hash ushr (band * 8)) and 0xffL
                buckets.getOrPut((band.toLong() shl 8) or segment) { mutableListOf() } += position
            }
        }

        val result = items.indices.groupBy(::root).values
            .filter { it.size > 1 }
            .map { members ->
                val duplicates = members.map { items[it] }.sortedByDescending { it.dateTaken }
                DuplicateGroup(
                    hash = duplicates.joinToString("-") { it.uri.toString().hashCode().toString(16) },
                    items = duplicates
                )
            }
            .sortedByDescending { it.items.size }
        cacheEditor.apply()
        result
    }

    suspend fun stageForRecycle(items: List<MediaItem>): List<RecycleEntry> = withContext(Dispatchers.IO) {
        val existing = loadRecycleEntries().toMutableList()
        val pendingItems = pendingRecycleItems(items, existing)
        if (pendingItems.isEmpty()) return@withContext emptyList()
        val gate = Semaphore(3)
        val staged = coroutineScope {
            pendingItems.map { item ->
                async {
                    gate.withPermit {
                        if (!hasEnoughBackupSpace(item.size, recycleDirectory.usableSpace)) return@withPermit null
                        var target: File? = null
                        runCatching {
                            val id = UUID.randomUUID().toString()
                            val extension = item.name.substringAfterLast('.', if (item.isVideo) "mp4" else "jpg")
                            target = File(recycleDirectory, "$id.$extension")
                            openMediaInputStream(context, item.uri).use { input ->
                                requireNotNull(input)
                                requireNotNull(target).outputStream().use(input::copyTo)
                            }
                            RecycleEntry(
                                id = id,
                                sourceUri = item.uri.toString(),
                                storedPath = requireNotNull(target).absolutePath,
                                originalName = item.name,
                                originalFolder = item.folder,
                                originalRelativePath = item.relativePath,
                                mimeType = item.mimeType,
                                dateTaken = item.dateTaken,
                                dateModified = item.dateModified,
                                duration = item.duration,
                                isVideo = item.isVideo,
                                deletedAt = System.currentTimeMillis()
                            )
                        }.getOrElse {
                            // A failed copy must not leave an untracked partial backup consuming storage.
                            runCatching { target?.delete() }
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (staged.isNotEmpty()) saveRecycleEntries(existing + staged)
        staged
    }

    suspend fun stageForSystemRecycle(items: List<MediaItem>): List<RecycleEntry> = withContext(Dispatchers.IO) {
        val existing = loadRecycleEntries()
        val pendingItems = pendingRecycleItems(items, existing)
        if (pendingItems.isEmpty()) return@withContext emptyList()
        val staged = pendingItems.map { item ->
            RecycleEntry(
                id = UUID.randomUUID().toString(),
                sourceUri = item.uri.toString(),
                storedPath = "",
                originalName = item.name,
                originalFolder = item.folder,
                originalRelativePath = item.relativePath,
                mimeType = item.mimeType,
                dateTaken = item.dateTaken,
                dateModified = item.dateModified,
                duration = item.duration,
                isVideo = item.isVideo,
                deletedAt = System.currentTimeMillis(),
                systemTrashed = true
            )
        }
        if (staged.isNotEmpty()) saveRecycleEntries(existing + staged)
        staged
    }

    fun loadRecycleEntries(): List<RecycleEntry> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RECYCLE, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                val entry = RecycleEntry(
                    id = json.getString("id"),
                    sourceUri = json.optString("sourceUri"),
                    storedPath = json.getString("storedPath"),
                    originalName = json.getString("originalName"),
                    originalFolder = json.optString("originalFolder", "其他"),
                    originalRelativePath = json.optString("originalRelativePath").takeIf { it.isNotBlank() },
                    mimeType = json.optString("mimeType", "image/*"),
                    dateTaken = json.optLong("dateTaken"),
                    dateModified = json.optLong("dateModified"),
                    duration = json.optLong("duration"),
                    isVideo = json.optBoolean("isVideo"),
                    deletedAt = json.optLong("deletedAt"),
                    systemTrashed = json.optBoolean("systemTrashed")
                )
                if (entry.systemTrashed || File(entry.storedPath).exists()) add(entry)
            }
        }
    }.getOrDefault(emptyList())

    suspend fun restore(entry: RecycleEntry): Boolean = restore(listOf(entry)).isNotEmpty()

    suspend fun restore(entries: List<RecycleEntry>): List<RecycleEntry> = withContext(Dispatchers.IO) {
        val candidates = entries.filterNot { it.systemTrashed }
        if (candidates.isEmpty()) return@withContext emptyList()
        val gate = Semaphore(3)
        val restored = coroutineScope {
            candidates.map { entry ->
                async { gate.withPermit { restoreToMediaStore(entry) } }
            }.awaitAll().mapNotNull { it }
        }
        if (restored.isNotEmpty()) {
            restored.forEach(::deletePrivateBackup)
            val restoredIds = restored.mapTo(hashSetOf()) { it.id }
            saveRecycleEntries(loadRecycleEntries().filterNot { it.id in restoredIds })
        }
        restored
    }

    private suspend fun restoreToMediaStore(entry: RecycleEntry): RecycleEntry? = withContext(Dispatchers.IO) {
        if (entry.systemTrashed) return@withContext null
        val file = File(entry.storedPath)
        if (!file.exists()) return@withContext null
        val collection = if (entry.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, entry.originalName)
            put(MediaStore.MediaColumns.MIME_TYPE, entry.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val folder = entry.originalFolder.replace(Regex("[\\/:*?\"<>|]"), "_")
                val mediaRoot = if (entry.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                val fallbackPath = "$mediaRoot/相册/已还原/$folder/"
                val restorePath = entry.originalRelativePath
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { "${it.trimEnd('/')}/" }
                    ?: fallbackPath
                put(MediaStore.MediaColumns.RELATIVE_PATH, restorePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            if (entry.dateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, entry.dateTaken)
            if (entry.dateModified > 0) {
                put(MediaStore.MediaColumns.DATE_MODIFIED, entry.dateModified)
            }
        }
        val target = context.contentResolver.insert(collection, values) ?: return@withContext null
        runCatching {
            openMediaOutputStream(context, target).use { output ->
                requireNotNull(output)
                file.inputStream().use { it.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(target, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    if (entry.dateModified > 0) {
                        put(MediaStore.MediaColumns.DATE_MODIFIED, entry.dateModified)
                    }
                }, null, null)
            }
            entry
        }.getOrElse {
            context.contentResolver.delete(target, null, null)
            null
        }
    }

    fun removeRecycleEntry(entry: RecycleEntry): Boolean {
        deletePrivateBackup(entry)
        val remaining = loadRecycleEntries().filterNot { it.id == entry.id }
        saveRecycleEntries(remaining)
        return true
    }

    fun removeRecycleEntries(entries: List<RecycleEntry>): Boolean {
        if (entries.isEmpty()) return true
        val ids = entries.mapTo(hashSetOf()) { it.id }
        entries.forEach(::deletePrivateBackup)
        saveRecycleEntries(loadRecycleEntries().filterNot { it.id in ids })
        return true
    }

    fun discardRecycleEntries(ids: Set<String>) {
        val entries = loadRecycleEntries()
        entries.filter { it.id in ids }.forEach(::deletePrivateBackup)
        saveRecycleEntries(entries.filterNot { it.id in ids })
    }

    fun purgeExpired(retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        val entries = loadRecycleEntries()
        val expiredPrivateEntries = entries.filter { !it.systemTrashed && it.deletedAt < cutoff }
        expiredPrivateEntries.forEach(::deletePrivateBackup)
        saveRecycleEntries(entries - expiredPrivateEntries.toSet())
    }

    private fun deletePrivateBackup(entry: RecycleEntry) {
        if (!entry.systemTrashed && entry.storedPath.isNotBlank()) File(entry.storedPath).delete()
    }

    private fun mediaSize(item: MediaItem): Long? {
        if (item.size > 0L) return item.size
        context.contentResolver.query(item.uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
        }
        return runCatching { context.contentResolver.openAssetFileDescriptor(item.uri, "r")?.use { it.length } }.getOrNull()
    }

    private fun sha256(item: MediaItem): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        openMediaInputStream(context, item.uri).use { input ->
            requireNotNull(input)
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun cachedSha256(item: MediaItem, editor: android.content.SharedPreferences.Editor): String? {
        val key = "sha:${item.uri}"
        val signature = analysisSignature(item)
        analysisPreferences.getString(key, null)?.let { stored ->
            if (stored.startsWith("$signature|")) return stored.substringAfter('|')
        }
        val hash = sha256(item) ?: return null
        editor.putString(key, "$signature|$hash")
        return hash
    }

    private fun cachedFingerprint(item: MediaItem, onStore: (String, String) -> Unit): Fingerprint? {
        val key = "fingerprint:${item.uri}"
        val signature = analysisSignature(item)
        analysisPreferences.getString(key, null)?.let { stored ->
            runCatching {
                val json = JSONObject(stored)
                if (json.optString("signature") == signature) {
                    return Fingerprint(
                        item = item,
                        hash = json.getString("hash").toLong(),
                        width = json.getInt("width"),
                        height = json.getInt("height"),
                        size = json.getLong("size"),
                        sharpness = json.getDouble("sharpness").toFloat(),
                        red = json.getDouble("red").toFloat(),
                        green = json.getDouble("green").toFloat(),
                        blue = json.getDouble("blue").toFloat()
                    )
                }
            }
        }
        val fingerprint = fingerprint(item) ?: return null
        onStore(key, JSONObject().apply {
            put("signature", signature)
            put("hash", fingerprint.hash.toString())
            put("width", fingerprint.width)
            put("height", fingerprint.height)
            put("size", fingerprint.size)
            put("sharpness", fingerprint.sharpness.toDouble())
            put("red", fingerprint.red.toDouble())
            put("green", fingerprint.green.toDouble())
            put("blue", fingerprint.blue.toDouble())
        }.toString())
        return fingerprint
    }

    private fun pruneAnalysisCache(items: List<MediaItem>) {
        val activeUris = items.mapTo(hashSetOf()) { it.uri.toString() }
        val editor = analysisPreferences.edit()
        analysisPreferences.all.keys.forEach { key ->
            val uri = when {
                key.startsWith("sha:") -> key.removePrefix("sha:")
                key.startsWith("fingerprint:") -> key.removePrefix("fingerprint:")
                else -> null
            }
            if (uri != null && uri !in activeUris) editor.remove(key)
        }
        editor.apply()
    }

    // The version suffix invalidates fingerprints computed by an older decoder
    // (the sample size changed, so the same picture would hash differently).
    private fun analysisSignature(item: MediaItem): String = "${item.size}:${item.dateModified}:fp2"

    private fun fingerprint(item: MediaItem): Fingerprint? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openMediaInputStream(context, item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        // The fingerprint only needs 64x64, so 128px is plenty: decoding at half
        // the previous size is what keeps a full-library scan to a sane time.
        while (max(bounds.outWidth, bounds.outHeight) / sample > 128) sample *= 2
        val decoded = openMediaInputStream(context, item.uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val thumb = Bitmap.createScaledBitmap(decoded, 64, 64, true)
        val hashBitmap = Bitmap.createScaledBitmap(thumb, 9, 8, true)
        var hash = 0L
        var bit = 0
        repeat(8) { y ->
            repeat(8) { x ->
                if (luminance(hashBitmap.getPixel(x, y)) > luminance(hashBitmap.getPixel(x + 1, y))) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        var red = 0L
        var green = 0L
        var blue = 0L
        var sharpness = 0L
        repeat(64) { y ->
            repeat(64) { x ->
                val color = thumb.getPixel(x, y)
                red += android.graphics.Color.red(color)
                green += android.graphics.Color.green(color)
                blue += android.graphics.Color.blue(color)
                if (x > 0) sharpness += kotlin.math.abs(luminance(color) - luminance(thumb.getPixel(x - 1, y)))
                if (y > 0) sharpness += kotlin.math.abs(luminance(color) - luminance(thumb.getPixel(x, y - 1)))
            }
        }
        if (hashBitmap !== thumb) hashBitmap.recycle()
        if (thumb !== decoded) thumb.recycle()
        decoded.recycle()
        val count = 64f * 64f
        Fingerprint(
            item = item,
            hash = hash,
            width = bounds.outWidth,
            height = bounds.outHeight,
            size = mediaSize(item) ?: 0L,
            sharpness = sharpness / (count * 2f),
            red = red / count,
            green = green / count,
            blue = blue / count
        )
    }.getOrNull()

    private fun luminance(color: Int): Int = (
        android.graphics.Color.red(color) * 299 +
            android.graphics.Color.green(color) * 587 +
            android.graphics.Color.blue(color) * 114
        ) / 1000

    private data class Fingerprint(
        val item: MediaItem,
        val hash: Long,
        val width: Int,
        val height: Int,
        val size: Long,
        val sharpness: Float,
        val red: Float,
        val green: Float,
        val blue: Float
    ) {
        val aspect: Float get() = width.toFloat() / height
    }

    private fun looksLikeSamePicture(first: Fingerprint, second: Fingerprint): Boolean =
        looksLikeSamePicture(
            hashA = first.hash,
            hashB = second.hash,
            aspectA = first.aspect,
            aspectB = second.aspect,
            redA = first.red,
            greenA = first.green,
            blueA = first.blue,
            redB = second.red,
            greenB = second.green,
            blueB = second.blue
        )

    private fun saveRecycleEntries(entries: List<RecycleEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("sourceUri", entry.sourceUri)
                put("storedPath", entry.storedPath)
                put("originalName", entry.originalName)
                put("originalFolder", entry.originalFolder)
                put("originalRelativePath", entry.originalRelativePath ?: "")
                put("mimeType", entry.mimeType)
                put("dateTaken", entry.dateTaken)
                put("dateModified", entry.dateModified)
                put("duration", entry.duration)
                put("isVideo", entry.isVideo)
                put("deletedAt", entry.deletedAt)
                put("systemTrashed", entry.systemTrashed)
            })
        }
        preferences.edit().putString(KEY_RECYCLE, array.toString()).apply()
    }

    companion object {
        private const val PREFERENCES = "cleanup_preferences"
        private const val ANALYSIS_PREFERENCES = "cleanup_analysis_cache"
        private const val KEY_RECYCLE = "recycle_entries"
    }
}

internal fun pendingRecycleItems(items: List<MediaItem>, existing: List<RecycleEntry>): List<MediaItem> {
    val pendingSources = pendingRecycleSourceUris(
        requestedSources = items.map { it.uri.toString() },
        existingSources = existing.map { it.sourceUri }.toSet()
    ).toHashSet()
    return items.filter { pendingSources.remove(it.uri.toString()) }
}

internal fun pendingRecycleSourceUris(
    requestedSources: List<String>,
    existingSources: Set<String>
): List<String> = requestedSources.distinct().filterNot { it in existingSources }

internal fun hasEnoughBackupSpace(fileSize: Long, usableSpace: Long): Boolean =
    usableSpace >= fileSize.coerceAtLeast(1L) + MIN_BACKUP_FREE_SPACE_BYTES

private const val MIN_BACKUP_FREE_SPACE_BYTES = 8L * 1024L * 1024L

/**
 * How many pictures are decoded at once while fingerprinting. Decoding is CPU
 * and IO bound; four keeps a large scan to seconds instead of minutes without
 * competing with whatever the user is looking at.
 */
private const val FINGERPRINT_CONCURRENCY = 4

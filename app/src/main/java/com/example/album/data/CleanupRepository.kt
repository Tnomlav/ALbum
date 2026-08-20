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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DuplicateGroup(val hash: String, val items: List<MediaItem>)

data class SimilarItem(
    val item: MediaItem,
    val width: Int,
    val height: Int,
    val size: Long,
    val sharpness: Float
) {
    val pixels: Long get() = width.toLong() * height
}

data class SimilarGroup(val id: String, val items: List<SimilarItem>, val bestUri: String)

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
    val systemTrashed: Boolean = false
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

    suspend fun findSimilarImages(items: List<MediaItem>): List<SimilarGroup> = withContext(Dispatchers.IO) {
        pruneAnalysisCache(items)
        val cacheEditor = analysisPreferences.edit()
        val fingerprints = items.asSequence().filterNot { it.isVideo }.mapNotNull { cachedFingerprint(it, cacheEditor) }.toList()
        if (fingerprints.size < 2) {
            cacheEditor.apply()
            return@withContext emptyList()
        }
        val parents = IntArray(fingerprints.size) { it }
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

        val buckets = mutableMapOf<Long, MutableList<Int>>()
        fingerprints.forEachIndexed { index, candidate ->
            val possible = mutableSetOf<Int>()
            repeat(4) { band ->
                val segment = (candidate.hash ushr (band * 16)) and 0xffffL
                buckets[(band.toLong() shl 16) or segment]?.let(possible::addAll)
            }
            possible.forEach { otherIndex ->
                val other = fingerprints[otherIndex]
                val aspectDifference = kotlin.math.abs(candidate.aspect - other.aspect) / max(candidate.aspect, other.aspect)
                val colorDistance = kotlin.math.sqrt(
                    ((candidate.red - other.red) * (candidate.red - other.red) +
                        (candidate.green - other.green) * (candidate.green - other.green) +
                        (candidate.blue - other.blue) * (candidate.blue - other.blue)).toDouble()
                )
                if (aspectDifference <= .08f && colorDistance <= 58.0 && java.lang.Long.bitCount(candidate.hash xor other.hash) <= 8) {
                    union(index, otherIndex)
                }
            }
            repeat(4) { band ->
                val segment = (candidate.hash ushr (band * 16)) and 0xffffL
                buckets.getOrPut((band.toLong() shl 16) or segment) { mutableListOf() } += index
            }
        }

        val result = fingerprints.indices.groupBy(::root).values.filter { it.size > 1 }.map { indexes ->
            val ranked = indexes.map { fingerprints[it] }.sortedWith(
                compareByDescending<Fingerprint> { it.width.toLong() * it.height }
                    .thenByDescending { it.sharpness }
                    .thenByDescending { it.size }
            )
            SimilarGroup(
                id = ranked.joinToString("-") { it.item.uri.toString().hashCode().toString(16) },
                items = ranked.map { SimilarItem(it.item, it.width, it.height, it.size, it.sharpness) },
                bestUri = ranked.first().item.uri.toString()
            )
        }.sortedByDescending { it.items.size }
        cacheEditor.apply()
        result
    }

    suspend fun stageForRecycle(items: List<MediaItem>): List<RecycleEntry> = withContext(Dispatchers.IO) {
        val existing = loadRecycleEntries().toMutableList()
        val staged = items.mapNotNull { item ->
            runCatching {
                val id = UUID.randomUUID().toString()
                val extension = item.name.substringAfterLast('.', if (item.isVideo) "mp4" else "jpg")
                val target = File(recycleDirectory, "$id.$extension")
                openMediaInputStream(context, item.uri).use { input ->
                    requireNotNull(input)
                    target.outputStream().use(input::copyTo)
                }
                RecycleEntry(
                    id = id,
                    sourceUri = item.uri.toString(),
                    storedPath = target.absolutePath,
                    originalName = item.name,
                    originalFolder = item.folder,
                    originalRelativePath = item.relativePath,
                    mimeType = item.mimeType,
                    dateTaken = item.dateTaken,
                    duration = item.duration,
                    isVideo = item.isVideo,
                    deletedAt = System.currentTimeMillis()
                )
            }.getOrNull()
        }
        if (staged.isNotEmpty()) saveRecycleEntries(existing + staged)
        staged
    }

    suspend fun stageForSystemRecycle(items: List<MediaItem>): List<RecycleEntry> = withContext(Dispatchers.IO) {
        val staged = items.map { item ->
            RecycleEntry(
                id = UUID.randomUUID().toString(),
                sourceUri = item.uri.toString(),
                storedPath = "",
                originalName = item.name,
                originalFolder = item.folder,
                originalRelativePath = item.relativePath,
                mimeType = item.mimeType,
                dateTaken = item.dateTaken,
                duration = item.duration,
                isVideo = item.isVideo,
                deletedAt = System.currentTimeMillis(),
                systemTrashed = true
            )
        }
        if (staged.isNotEmpty()) saveRecycleEntries(loadRecycleEntries() + staged)
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
                    duration = json.optLong("duration"),
                    isVideo = json.optBoolean("isVideo"),
                    deletedAt = json.optLong("deletedAt"),
                    systemTrashed = json.optBoolean("systemTrashed")
                )
                if (entry.systemTrashed || File(entry.storedPath).exists()) add(entry)
            }
        }
    }.getOrDefault(emptyList())

    suspend fun restore(entry: RecycleEntry): Boolean = withContext(Dispatchers.IO) {
        if (entry.systemTrashed) return@withContext false
        val file = File(entry.storedPath)
        if (!file.exists()) return@withContext false
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
        }
        val target = context.contentResolver.insert(collection, values) ?: return@withContext false
        runCatching {
            openMediaOutputStream(context, target).use { output ->
                requireNotNull(output)
                file.inputStream().use { it.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            removeRecycleEntry(entry)
            true
        }.getOrElse {
            context.contentResolver.delete(target, null, null)
            false
        }
    }

    fun removeRecycleEntry(entry: RecycleEntry): Boolean {
        deletePrivateBackup(entry)
        val remaining = loadRecycleEntries().filterNot { it.id == entry.id }
        saveRecycleEntries(remaining)
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

    private fun cachedFingerprint(item: MediaItem, editor: android.content.SharedPreferences.Editor): Fingerprint? {
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
        editor.putString(key, JSONObject().apply {
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

    private fun analysisSignature(item: MediaItem): String = "${item.size}:${item.dateModified}"

    private fun fingerprint(item: MediaItem): Fingerprint? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openMediaInputStream(context, item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
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

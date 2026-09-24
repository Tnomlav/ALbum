package com.example.album.data

import android.content.ContentValues
import android.content.Context
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
 * The candidate set for "identical bytes": only files whose size appears more
 * than once can be duplicates, and the size is already in the media metadata,
 * so this costs no file access at all.
 */
internal fun duplicateSizeCandidates(sizes: List<Long>): List<Int> {
    val bySize = mutableMapOf<Long, MutableList<Int>>()
    sizes.forEachIndexed { position, size ->
        if (size > 0L) bySize.getOrPut(size) { mutableListOf() } += position
    }
    return bySize.values.filter { it.size > 1 }.flatten()
}

/**
 * Streams two files side by side and gives up at the first difference. Byte
 * equality is what "duplicate" means here, and stopping early is what makes a
 * library-wide check take seconds instead of minutes.
 */
internal fun sameContent(left: java.io.InputStream, right: java.io.InputStream): Boolean {
    val leftBuffer = ByteArray(COMPARE_BUFFER_BYTES)
    val rightBuffer = ByteArray(COMPARE_BUFFER_BYTES)
    while (true) {
        val leftRead = left.read(leftBuffer)
        val rightRead = right.read(rightBuffer)
        if (leftRead != rightRead) return false
        if (leftRead <= 0) return true
        for (index in 0 until leftRead) {
            if (leftBuffer[index] != rightBuffer[index]) return false
        }
    }
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
    private val recycleDirectory = File(context.filesDir, "recycle_bin").apply { mkdirs() }

    /**
     * Analysis results (SHA-256 and perceptual fingerprints) live in one compact
     * text file instead of SharedPreferences: a library-sized preference set is
     * slow to read, slow to prune and rewrites the whole XML on commit, which was
     * a large part of why a repeat scan still felt heavy.
     */
    private val analysisCacheFile = File(context.filesDir, "cleanup_analysis_cache.txt")

    /**
     * Byte-for-byte duplicates, found the way a phone's storage cleaner does it:
     * group by the size MediaStore already knows, read a 16 KB head of each
     * candidate, then stream the survivors side by side and stop at the first
     * difference. Nothing is hashed end to end and no picture is decoded, which
     * is what makes it take seconds instead of minutes.
     */
    suspend fun findDuplicateGroups(
        items: List<MediaItem>,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        withAnalysisCache(items) { cache -> duplicateGroups(items, cache, onProgress) }
    }

    private suspend fun <T> withAnalysisCache(
        items: List<MediaItem>,
        block: suspend (MutableMap<String, String>) -> T
    ): T {
        val cache = loadAnalysisCache()
        val activeUris = items.mapTo(hashSetOf()) { it.uri.toString() }
        return try {
            block(cache)
        } finally {
            // Persist whatever was computed, even when the user cancelled: the
            // next scan picks up where this one stopped instead of redoing it.
            saveAnalysisCache(cache, activeUris)
        }
    }

    private suspend fun duplicateGroups(
        items: List<MediaItem>,
        cache: MutableMap<String, String>,
        onProgress: (completed: Int, total: Int) -> Unit
    ): List<DuplicateGroup> {
        // Only files whose size repeats can be identical, and the size comes
        // straight from the media metadata. Every loop checks for cancellation so
        // leaving the page stops the work instead of pinning the CPU.
        val hashingCandidates = duplicateSizeCandidates(items.map { mediaSize(it) ?: 0L })
        val totalWork = hashingCandidates.size
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

        // Identical bytes, the way a phone's storage cleaner does it. Only files
        // of the same size can match, and only files that also agree on their
        // first 16 KB are worth comparing: the comparison itself streams both
        // files and stops at the first difference, so nothing is hashed.
        val quickSignatures = coroutineScope {
            val gate = Semaphore(ANALYSIS_CONCURRENCY)
            hashingCandidates.map { index ->
                async {
                    gate.withPermit {
                        val value = cachedHeadSignature(items[index], cache)?.let { it to index }
                        completedWork++
                        onProgress(completedWork, totalWork)
                        value
                    }
                }
            }.awaitAll().filterNotNull()
        }
        quickSignatures.groupBy({ it.first }, { it.second }).values.filter { it.size > 1 }.forEach { same ->
            coroutineContext.ensureActive()
            // Compare against the first member: byte equality is transitive, so
            // one representative per group is enough, and every comparison stops
            // as soon as the two files differ.
            val representative = same.first()
            same.drop(1).forEach { index ->
                coroutineContext.ensureActive()
                if (sameBytes(items[representative], items[index])) union(representative, index)
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
        return result
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

    /**
     * Size plus the first 16 KB. This is the whole pre-filter for "identical
     * bytes": files that disagree here cannot be identical, and files that agree
     * are compared directly instead of being hashed end to end.
     */
    private fun cachedHeadSignature(item: MediaItem, cache: MutableMap<String, String>): String? {
        val key = "head:${item.uri}"
        val signature = analysisSignature(item)
        cache[key]?.let { stored ->
            if (stored.startsWith("$signature|")) return stored.substringAfter('|')
        }
        val value = headSignature(item) ?: return null
        cache[key] = "$signature|$value"
        return value
    }

    private fun headSignature(item: MediaItem): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(item.size.toString().toByteArray())
        openMediaInputStream(context, item.uri).use { input ->
            requireNotNull(input)
            val buffer = ByteArray(HEAD_SIGNATURE_BYTES)
            val head = input.read(buffer)
            if (head > 0) digest.update(buffer, 0, head)
        }
        digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }.getOrNull()

    /** Opens both files and compares them with [sameContent]. */
    private fun sameBytes(first: MediaItem, second: MediaItem): Boolean = runCatching {
        openMediaInputStream(context, first.uri).use { left ->
            openMediaInputStream(context, second.uri).use { right ->
                left != null && right != null && sameContent(left, right)
            }
        }
    }.getOrDefault(false)

    /**
     * One line per cached value, `<key>\t<value>`. Loading is a single buffered
     * read; saving drops entries whose files are no longer in the library.
     */
    private fun loadAnalysisCache(): MutableMap<String, String> = runCatching {
        val entries = java.util.concurrent.ConcurrentHashMap<String, String>()
        if (!analysisCacheFile.isFile) return@runCatching entries
        analysisCacheFile.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val separator = line.indexOf('\t')
                if (separator > 0) entries[line.substring(0, separator)] = line.substring(separator + 1)
            }
        }
        entries
    }.getOrDefault(java.util.concurrent.ConcurrentHashMap())

    private fun saveAnalysisCache(cache: Map<String, String>, activeUris: Set<String>) {
        runCatching {
            val temporary = File(context.filesDir, "${analysisCacheFile.name}.tmp")
            temporary.bufferedWriter().use { writer ->
                cache.forEach { (key, value) ->
                    val uri = key.substringAfter(':', "")
                    // Only the head signatures are used now; drop anything left
                    // over from earlier versions of the analysis.
                    if (!key.startsWith("head:")) return@forEach
                    if (uri.isNotEmpty() && uri !in activeUris) return@forEach
                    writer.append(key).append('\t').append(value).append('\n')
                }
            }
            if (!temporary.renameTo(analysisCacheFile)) {
                analysisCacheFile.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    // The version suffix invalidates values computed by an older decoder or hash
    // implementation: the same picture would hash differently.
    private fun analysisSignature(item: MediaItem): String = "${item.size}:${item.dateModified}:fp3"

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
 * How many candidate files are read at once while checking for duplicates. The
 * work is IO bound; six keeps a large library to seconds without competing with
 * whatever the user is looking at.
 */
private const val ANALYSIS_CONCURRENCY = 6

/** Head bytes hashed by the cheap signature before a direct comparison. */
private const val HEAD_SIGNATURE_BYTES = 16 * 1024

/** Chunk size used while comparing two candidate files. */
private const val COMPARE_BUFFER_BYTES = 64 * 1024

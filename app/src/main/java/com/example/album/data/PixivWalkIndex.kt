package com.example.album.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * One entry of a Pixiv folder listing.
 *
 * Every field here comes out of a *single* provider query per folder (see
 * [listPixivDirectory]). Reading the same fields through `DocumentFile` costs
 * one binder round trip per field per entry, and that is where the Pixiv walks
 * spend almost all of their time.
 */
internal data class PixivDirEntry(
    val uri: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

/**
 * A folder's listing from an earlier walk. [fingerprint] is derived from the
 * listing itself, so an unchanged folder can be replayed straight from the
 * index without asking the provider about anything below it.
 */
internal data class PixivWalkIndexEntry(
    val fingerprint: String,
    val entries: List<PixivDirEntry>
)

/** What a walk cost. Shown on the page so the caching is visible. */
data class PixivWalkStats(
    val durationMs: Long = 0L,
    val directoriesListed: Int = 0,
    val directoriesReused: Int = 0,
    val itemsFound: Int = 0
) {
    operator fun plus(other: PixivWalkStats) = PixivWalkStats(
        durationMs = durationMs + other.durationMs,
        directoriesListed = directoriesListed + other.directoriesListed,
        directoriesReused = directoriesReused + other.directoriesReused,
        itemsFound = itemsFound + other.itemsFound
    )
}

/** Stable value that changes whenever a folder's own listing changes. */
internal fun pixivDirectoryFingerprint(entries: List<PixivDirEntry>): String {
    val builder = StringBuilder(entries.size * 48)
    entries.sortedBy { it.uri }.forEach { entry ->
        builder.append(entry.uri).append('\u001f')
            .append(entry.name).append('\u001f')
            .append(if (entry.isDirectory) 'd' else 'f').append('\u001f')
            .append(entry.size).append('\u001f')
            .append(entry.lastModified).append('\u001f')
            .append(entry.mimeType).append('\u001e')
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(builder.toString().toByteArray())
    return digest.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
}

/**
 * The listings collected by one walk, keyed by folder URI. It is written next
 * to the library cache so the next walk can skip everything that did not
 * change.
 */
internal class PixivWalkIndex(
    private val directories: MutableMap<String, PixivWalkIndexEntry> = mutableMapOf()
) {
    fun size(): Int = directories.size

    fun entries(directoryUri: String): PixivWalkIndexEntry? = directories[directoryUri]

    /** The previous listing for [directoryUri], but only while it still matches. */
    fun reusable(directoryUri: String, entries: List<PixivDirEntry>): List<PixivDirEntry>? =
        directories[directoryUri]
            ?.takeIf { it.fingerprint == pixivDirectoryFingerprint(entries) }
            ?.entries

    fun put(directoryUri: String, entries: List<PixivDirEntry>) {
        directories[directoryUri] = PixivWalkIndexEntry(pixivDirectoryFingerprint(entries), entries)
    }

    fun putEntry(directoryUri: String, entry: PixivWalkIndexEntry) {
        directories[directoryUri] = entry
    }

    fun merge(other: PixivWalkIndex) {
        directories.putAll(other.directories)
    }

    fun toJson(key: String): JSONObject {
        val dirs = JSONObject()
        directories.forEach { (uri, entry) ->
            dirs.put(
                uri,
                JSONObject()
                    .put("fp", entry.fingerprint)
                    .put(
                        "entries",
                        JSONArray().apply {
                            entry.entries.forEach { child ->
                                put(
                                    JSONObject()
                                        .put("uri", child.uri)
                                        .put("name", child.name)
                                        .put("dir", child.isDirectory)
                                        .put("size", child.size)
                                        .put("modified", child.lastModified)
                                        .put("mime", child.mimeType)
                                )
                            }
                        }
                    )
            )
        }
        return JSONObject().put("key", key).put("dirs", dirs)
    }

    companion object {
        fun fromJson(key: String, root: JSONObject?): PixivWalkIndex {
            val index = PixivWalkIndex()
            if (root == null || root.optString("key") != key) return index
            val dirs = root.optJSONObject("dirs") ?: return index
            dirs.keys().forEach { uri ->
                val directory = dirs.optJSONObject(uri) ?: return@forEach
                val fingerprint = directory.optString("fp")
                val array = directory.optJSONArray("entries") ?: return@forEach
                val entries = (0 until array.length()).mapNotNull { position ->
                    val entry = array.optJSONObject(position) ?: return@mapNotNull null
                    PixivDirEntry(
                        uri = entry.optString("uri"),
                        name = entry.optString("name"),
                        isDirectory = entry.optBoolean("dir"),
                        size = entry.optLong("size"),
                        lastModified = entry.optLong("modified"),
                        mimeType = entry.optString("mime")
                    )
                }
                if (fingerprint.isBlank() || entries.isEmpty()) return@forEach
                index.putEntry(uri, PixivWalkIndexEntry(fingerprint, entries))
            }
            return index
        }
    }
}

private const val WALK_INDEX_FILE = "pixiv_walk_index.json"

internal fun loadPixivWalkIndex(context: Context, key: String): PixivWalkIndex = runCatching {
    val file = File(context.filesDir, WALK_INDEX_FILE)
    if (!file.isFile) return@runCatching PixivWalkIndex()
    PixivWalkIndex.fromJson(key, JSONObject(file.readText()))
}.getOrDefault(PixivWalkIndex())

internal fun savePixivWalkIndex(context: Context, key: String, index: PixivWalkIndex) {
    if (index.size() == 0) return
    runCatching {
        val file = File(context.filesDir, WALK_INDEX_FILE)
        val temporary = File(context.filesDir, "$WALK_INDEX_FILE.tmp")
        temporary.writeText(index.toJson(key).toString())
        if (!temporary.renameTo(file)) {
            file.writeText(index.toJson(key).toString())
            temporary.delete()
        }
    }
}

/**
 * Lists one folder in a single query.
 *
 * Returns null when the folder cannot be read (the caller then keeps whatever
 * it already has); an empty list means "this folder really is empty".
 */
internal fun listPixivDirectory(
    context: Context,
    treeUri: Uri,
    directoryUri: Uri
): List<PixivDirEntry>? {
    if (directoryUri.scheme.equals("file", ignoreCase = true)) {
        val directory = directoryUri.path?.let(::File) ?: return null
        val children = directory.listFiles() ?: return emptyList()
        return children.map { child ->
            val mime = if (child.isDirectory) {
                DocumentsContract.Document.MIME_TYPE_DIR
            } else {
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(child.extension.lowercase())
                    .orEmpty()
            }
            PixivDirEntry(
                uri = Uri.fromFile(child).toString(),
                name = child.name,
                isDirectory = child.isDirectory,
                size = if (child.isDirectory) 0L else child.length(),
                lastModified = child.lastModified(),
                mimeType = mime
            )
        }
    }
    return runCatching {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(directoryUri)
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val entries = ArrayList<PixivDirEntry>(cursor.count)
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(0) ?: continue
                val mime = cursor.getString(2).orEmpty()
                entries += PixivDirEntry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString(),
                    name = cursor.getString(1).orEmpty(),
                    isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                    lastModified = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                    mimeType = mime
                )
            }
            entries
        }
    }.getOrNull()
}

/** Guards against a pathological tree while staying far below the binder limit. */
internal const val MAX_PIXIV_WALK_DEPTH = 24

/**
 * Walks [rootUri], handing every non-hidden image entry to [onItem] together
 * with the artist folder it belongs to.
 *
 * Folders whose listing matches [previous] are replayed from the index instead
 * of being listed again, so a second walk only pays for the folders that
 * actually changed.
 */
internal fun walkPixivFolders(
    rootUri: String,
    folderName: String,
    previous: PixivWalkIndex,
    list: (String) -> List<PixivDirEntry>?,
    includeHidden: Boolean,
    onItem: (PixivDirEntry, String, Map<String, PixivDirEntry>) -> Unit,
    onFolderDone: () -> Unit = {}
): Pair<PixivWalkIndex, PixivWalkStats> {
    val next = PixivWalkIndex()
    // nanoTime rather than SystemClock: this stays a plain JVM call, so the walk
    // logic can be unit tested.
    val startedAt = System.nanoTime()
    var listed = 0
    var reused = 0
    var items = 0

    fun hidden(entry: PixivDirEntry): Boolean =
        (!includeHidden && entry.name.trimStart().startsWith('.')) || isSystemTrashedName(entry.name)

    fun replay(directoryUri: String, folder: String, depth: Int) {
        val cached = previous.entries(directoryUri) ?: return
        val siblings = cached.entries.associateBy { it.name }
        cached.entries.forEach { entry ->
            if (hidden(entry)) return@forEach
            if (entry.isDirectory) {
                previous.entries(entry.uri)?.let { child ->
                    next.putEntry(entry.uri, child)
                    reused++
                    replay(entry.uri, folder, depth + 1)
                }
            } else if (entry.isImage) {
                items++
                onItem(entry, folder, siblings)
            }
        }
    }

    fun visit(directoryUri: String, folder: String, depth: Int) {
        if (depth > MAX_PIXIV_WALK_DEPTH) return
        val entries = list(directoryUri) ?: return
        listed++
        val cached = previous.reusable(directoryUri, entries)
        next.put(directoryUri, entries)
        if (cached != null) {
            // This folder was listed (it is the one we just compared); everything
            // below it is replayed from the index and counted there.
            replay(directoryUri, folder, depth)
            return
        }
        val siblings = entries.associateBy { it.name }
        entries.forEach { entry ->
            if (hidden(entry)) return@forEach
            if (entry.isDirectory) {
                visit(entry.uri, folder, depth + 1)
            } else if (entry.isImage) {
                items++
                onItem(entry, folder, siblings)
            }
        }
        onFolderDone()
    }

    visit(rootUri, folderName, 0)
    return next to PixivWalkStats(
        durationMs = (System.nanoTime() - startedAt) / 1_000_000L,
        directoriesListed = listed,
        directoriesReused = reused,
        itemsFound = items
    )
}

package com.example.album.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Last known media lists. MediaStore queries can take a while on large
 * libraries, so the app shows this snapshot immediately and replaces it with
 * the fresh scan results.
 */
object MediaSnapshotStore {
    private const val FILE_NAME = "media_snapshot.json"

    data class Snapshot(
        val images: List<MediaItem>,
        val videos: List<MediaItem>,
        val localImages: List<MediaItem>,
        val localVideos: List<MediaItem>
    )

    // Last snapshot parsed or written in this process. Warm starts (returning to
    // the grid, configuration changes, tab switches) read this instead of
    // parsing a multi-megabyte JSON file on the main thread, which is what used
    // to stall the first frame of a cold start.
    @Volatile
    private var inMemory: Snapshot? = null

    /** The snapshot already parsed in this process, without touching the disk. */
    fun cached(): Snapshot? = inMemory

    fun save(context: Context, snapshot: Snapshot) {
        inMemory = snapshot
        runCatching {
            val root = JSONObject()
                .put("images", encode(snapshot.images))
                .put("videos", encode(snapshot.videos))
                .put("localImages", encode(snapshot.localImages))
                .put("localVideos", encode(snapshot.localVideos))
            val file = File(context.filesDir, FILE_NAME)
            val temporary = File(context.filesDir, "$FILE_NAME.tmp")
            temporary.writeText(root.toString())
            if (!temporary.renameTo(file)) {
                file.writeText(root.toString())
                temporary.delete()
            }
        }
    }

    fun load(context: Context): Snapshot? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) {
            inMemory = null
            return@runCatching null
        }
        val root = JSONObject(file.readText())
        Snapshot(
            images = decode(root.optJSONArray("images")),
            videos = decode(root.optJSONArray("videos")),
            localImages = decode(root.optJSONArray("localImages")),
            localVideos = decode(root.optJSONArray("localVideos"))
        ).takeIf { it.images.isNotEmpty() || it.videos.isNotEmpty() || it.localImages.isNotEmpty() || it.localVideos.isNotEmpty() }
    }.getOrNull().also { inMemory = it }

    private fun encode(items: List<MediaItem>): JSONArray {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("uri", item.uri.toString())
                    .put("name", item.name)
                    .put("folder", item.folder)
                    .put("dateTaken", item.dateTaken)
                    .put("mimeType", item.mimeType)
                    .put("relativePath", item.relativePath.orEmpty())
                    .put("size", item.size)
                    .put("dateModified", item.dateModified)
                    .put("duration", item.duration)
                    .put("width", item.width)
                    .put("height", item.height)
                    .put("isVideo", item.isVideo)
                    .put("isDocument", item.isDocument)
            )
        }
        return array
    }

    private fun decode(array: JSONArray?): List<MediaItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val uri = entry.optString("uri").takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return@mapNotNull null
            MediaItem(
                id = entry.optLong("id"),
                uri = uri,
                name = entry.optString("name"),
                folder = entry.optString("folder"),
                dateTaken = entry.optLong("dateTaken"),
                mimeType = entry.optString("mimeType"),
                relativePath = entry.optString("relativePath").takeIf { it.isNotBlank() },
                size = entry.optLong("size"),
                dateModified = entry.optLong("dateModified"),
                duration = entry.optLong("duration"),
                width = entry.optInt("width"),
                height = entry.optInt("height"),
                isVideo = entry.optBoolean("isVideo"),
                isDocument = entry.optBoolean("isDocument")
            )
        }
    }
}

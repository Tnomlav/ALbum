package com.example.album.wallpaper

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps a copy of the applied wallpaper queue outside the app's private
 * storage. App updates keep private files, but reinstalling removes them, and
 * some launchers clear the live wallpaper when the package is replaced. The
 * copy lets the app restore the queue with a single confirmation.
 */
object WallpaperBackup {
    private const val FILE_NAME = "wallpaper_backup.json"

    data class Snapshot(val kind: String, val uris: List<String>)

    fun save(context: Context, kind: String, uris: List<String>) {
        if (uris.isEmpty()) return
        val payload = JSONObject()
            .put("kind", kind)
            .put("uris", JSONArray(uris))
            .put("savedAt", System.currentTimeMillis())
            .toString()
        writableFiles(context).forEach { file ->
            runCatching {
                file.parentFile?.mkdirs()
                val temporary = File(file.parentFile, "$FILE_NAME.tmp")
                temporary.writeText(payload)
                if (!temporary.renameTo(file)) {
                    temporary.delete()
                    file.writeText(payload)
                }
            }
        }
    }

    fun load(context: Context): Snapshot? {
        readableFiles(context).forEach { file ->
            val snapshot = runCatching {
                if (!file.isFile) return@runCatching null
                val json = JSONObject(file.readText())
                val uris = json.optJSONArray("uris")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optString(index).takeIf { it.isNotBlank() }
                    }
                }.orEmpty()
                if (uris.isEmpty()) null else Snapshot(
                    kind = json.optString("kind", WallpaperAppliedStoreKind.STATIC),
                    uris = uris
                )
            }.getOrNull()
            if (snapshot != null) return snapshot
        }
        return null
    }

    private fun writableFiles(context: Context): List<File> = buildList {
        publicDirectory()?.let { add(File(it, FILE_NAME)) }
        context.getExternalFilesDir(null)?.let { add(File(it, FILE_NAME)) }
        add(File(context.filesDir, FILE_NAME))
    }

    private fun readableFiles(context: Context): List<File> = buildList {
        add(File(context.filesDir, FILE_NAME))
        context.getExternalFilesDir(null)?.let { add(File(it, FILE_NAME)) }
        publicDirectory()?.let { add(File(it, FILE_NAME)) }
    }

    private fun publicDirectory(): File? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null
        if (!Environment.isExternalStorageManager()) return null
        val root = Environment.getExternalStorageDirectory() ?: return null
        return File(root, "Album")
    }
}

internal object WallpaperAppliedStoreKind {
    const val STATIC = "static"
    const val DYNAMIC = "dynamic"
}

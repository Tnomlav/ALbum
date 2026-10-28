package com.example.album.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

internal data class PixivTagFingerprint(
    val size: Long,
    val modified: Long
)

internal fun mediaItemTagFingerprint(item: MediaItem): PixivTagFingerprint =
    PixivTagFingerprint(size = item.size, modified = item.dateModified)

/**
 * Persistent local tag index. Tag search should be a database read, not a
 * repeated scan of every image and sidecar file.
 */
internal class PixivTagIndexStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                uri TEXT PRIMARY KEY NOT NULL,
                file_size INTEGER NOT NULL,
                modified INTEGER NOT NULL,
                tags TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // An index is disposable: rebuild it instead of migrating derived data.
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun loadValid(items: List<MediaItem>): Map<String, List<String>> {
        if (items.isEmpty()) return emptyMap()
        val expected = items.associate { item ->
            item.uri.toString() to mediaItemTagFingerprint(item)
        }
        val result = mutableMapOf<String, List<String>>()
        readableDatabase.query(
            TABLE,
            arrayOf(COL_URI, COL_SIZE, COL_MODIFIED, COL_TAGS),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val uri = cursor.getString(0)
                val fingerprint = expected[uri] ?: continue
                if (cursor.getLong(1) != fingerprint.size || cursor.getLong(2) != fingerprint.modified) {
                    continue
                }
                result[uri] = decodeTags(cursor.getString(3))
            }
        }
        return result
    }

    fun upsert(item: MediaItem, tags: List<String>) {
        val values = ContentValues().apply {
            put(COL_URI, item.uri.toString())
            put(COL_SIZE, item.size)
            put(COL_MODIFIED, item.dateModified)
            put(COL_TAGS, encodeTags(tags))
        }
        writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun encodeTags(tags: List<String>): String =
        JSONArray(tags.distinct()).toString()

    private fun decodeTags(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        buildList {
            repeat(array.length()) { index ->
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val DATABASE_NAME = "pixiv_tag_index.db"
        const val DATABASE_VERSION = 1
        const val TABLE = "pixiv_tags"
        const val COL_URI = "uri"
        const val COL_SIZE = "file_size"
        const val COL_MODIFIED = "modified"
        const val COL_TAGS = "tags"
    }
}

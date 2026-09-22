package com.example.album.data

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

/**
 * Images the Pixiv archiver tagged as adult content.
 *
 * The archiver writes tags into the file itself (EXIF user comment, PNG text or
 * a `.pixiv.json` sidecar), but reading them back costs a file read per image.
 * Because the archiver already holds the tags while writing them, it records
 * the identity of the adult ones here; the library then filters against that
 * list, which is just two hash lookups.
 */
object AdultTagStore {
    private const val PREFERENCES = "content_filter"
    private const val KEY_URIS = "adult_tagged_uris"
    private const val KEY_NAMES = "adult_tagged_names"
    private const val SETTING_KEY = "hide_adult_tagged"

    /** Tags that mark a work as adult. R-18G is the harder variant of the same. */
    private val adultTags = setOf("r-18", "r-18g")

    fun isAdultTag(tag: String): Boolean = normalize(tag) in adultTags

    fun hasAdultTag(tags: Iterable<String>): Boolean = tags.any(::isAdultTag)

    /** True when the user asked for these images to be hidden. */
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("album_settings", Context.MODE_PRIVATE)
            .getBoolean(SETTING_KEY, false)

    /** Remembers the archived file once it turned out to carry an adult tag. */
    fun record(context: Context, uri: String, name: String?, tags: Iterable<String>) {
        if (uri.isBlank() || !hasAdultTag(tags)) return
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val uris = preferences.getStringSet(KEY_URIS, emptySet()).orEmpty() + uri
        val names = preferences.getStringSet(KEY_NAMES, emptySet()).orEmpty() +
            name?.takeIf { it.isNotBlank() } ?: emptySet()
        preferences.edit {
            putStringSet(KEY_URIS, uris)
            putStringSet(KEY_NAMES, names)
        }
    }

    fun load(context: Context): AdultTagFilter {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return AdultTagFilter(
            uris = preferences.getStringSet(KEY_URIS, emptySet()).orEmpty().toSet(),
            names = preferences.getStringSet(KEY_NAMES, emptySet()).orEmpty().toSet()
        )
    }

    private fun normalize(tag: String): String = tag.trim()
        .trim('【', '】', '[', ']')
        .lowercase(Locale.ROOT)
}

/** The identities of adult-tagged images, as recorded while archiving. */
data class AdultTagFilter(
    val uris: Set<String> = emptySet(),
    val names: Set<String> = emptySet()
) {
    val isEmpty: Boolean get() = uris.isEmpty() && names.isEmpty()

    fun contains(uri: String, name: String): Boolean =
        uri in uris || (name.isNotBlank() && name in names)

    fun contains(item: MediaItem): Boolean =
        contains(item.uri.toString(), item.name)
}

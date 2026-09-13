package com.example.album.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Persists the slideshow queue (URIs plus their order). */
object SlideshowQueueStore {
    private const val PREFERENCES = "album_preferences"
    private const val URIS_KEY = "slideshow_queue_uris"
    private const val ORDER_KEY = "slideshow_queue_order"

    data class State(val uris: Set<String>, val order: List<String>) {
        fun normalized(): State {
            val uniqueOrder = order.filter { it.isNotBlank() && it in uris }.distinct()
            val missing = uris.filter { it.isNotBlank() && it !in uniqueOrder }
            return State(uris = uris.filter { it.isNotBlank() }.toSet(), order = uniqueOrder + missing)
        }
    }

    @Synchronized
    fun load(context: Context): State {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return State(
            uris = preferences.getStringSet(URIS_KEY, emptySet()).orEmpty().toSet(),
            order = parseArray(preferences.getString(ORDER_KEY, "[]"))
        ).normalized()
    }

    @Synchronized
    fun save(context: Context, state: State) {
        val normalized = state.normalized()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putStringSet(URIS_KEY, normalized.uris)
            .putString(ORDER_KEY, JSONArray(normalized.order).toString())
            .apply()
    }

    private fun parseArray(value: String?): List<String> = runCatching {
        val json = JSONArray(value ?: "[]")
        (0 until json.length()).mapNotNull { index ->
            json.optString(index).takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())
}

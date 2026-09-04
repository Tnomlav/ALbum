package com.example.album.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists the wallpaper manager queue across app upgrades and preference recovery. */
data class WallpaperQueueState(
    val uris: Set<String>,
    val order: List<String>
) {
    fun normalized(): WallpaperQueueState {
        val uniqueOrder = order.filter { it.isNotBlank() && it in uris }.distinct()
        val missing = uris.filter { it.isNotBlank() && it !in uniqueOrder }
        return WallpaperQueueState(
            uris = uris.filter { it.isNotBlank() }.toSet(),
            order = uniqueOrder + missing
        )
    }
}

object WallpaperQueueStore {
    private const val PREFERENCES = "album_preferences"
    private const val URI_KEY = "wallpaper_queue_uris"
    private const val ORDER_KEY = "wallpaper_queue_order"
    private const val STATE_FILE = "wallpaper_queue_state.json"

    @Synchronized
    fun load(context: Context): WallpaperQueueState {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val preferenceState = runCatching {
            if (!preferences.contains(URI_KEY)) return@runCatching null
            WallpaperQueueState(
                uris = preferences.getStringSet(URI_KEY, emptySet()).orEmpty().toSet(),
                order = parseArray(preferences.getString(ORDER_KEY, "[]"))
            ).normalized()
        }.getOrNull()
        if (preferenceState != null) {
            // Upgrade existing installs immediately so the queue has a durable
            // recovery copy before any later preference migration can affect it.
            writeStateFile(context, preferenceState)
            return preferenceState
        }

        val recovered = readStateFile(context).normalized()
        // Migrate a queue recovered from the durable file back to the legacy
        // preference keys so older code paths and future launches see it too.
        writePreferences(preferences, recovered)
        return recovered
    }

    @Synchronized
    fun save(context: Context, state: WallpaperQueueState) {
        val normalized = state.normalized()
        // Keep the file as a recovery copy. SharedPreferences remains updated
        // for compatibility with existing wallpaper services and old installs.
        writeStateFile(context, normalized)
        writePreferences(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE), normalized)
    }

    private fun writePreferences(
        preferences: android.content.SharedPreferences,
        state: WallpaperQueueState
    ) {
        preferences.edit()
            .putStringSet(URI_KEY, state.uris)
            .putString(ORDER_KEY, JSONArray(state.order).toString())
            .apply()
    }

    private fun writeStateFile(context: Context, state: WallpaperQueueState) {
        runCatching {
            val destination = File(context.filesDir, STATE_FILE)
            val temporary = File(context.filesDir, "$STATE_FILE.tmp")
            temporary.writeText(
                JSONObject()
                    .put("uris", JSONArray(state.uris.toList()))
                    .put("order", JSONArray(state.order))
                    .toString()
            )
            if (!temporary.renameTo(destination)) {
                temporary.delete()
                error("Unable to replace wallpaper queue state")
            }
        }
    }

    private fun readStateFile(context: Context): WallpaperQueueState {
        return runCatching {
            val json = JSONObject(File(context.filesDir, STATE_FILE).readText())
            WallpaperQueueState(
                uris = parseArray(json.optJSONArray("uris")).toSet(),
                order = parseArray(json.optJSONArray("order"))
            )
        }.getOrDefault(WallpaperQueueState(emptySet(), emptyList()))
    }

    private fun parseArray(value: String?): List<String> = runCatching {
        parseArray(JSONArray(value ?: "[]"))
    }.getOrDefault(emptyList())

    private fun parseArray(value: JSONArray?): List<String> = runCatching {
        if (value == null) return@runCatching emptyList()
        (0 until value.length()).mapNotNull { index ->
            value.optString(index).takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())
}

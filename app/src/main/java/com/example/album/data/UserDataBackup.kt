package com.example.album.data

import android.content.Context
import com.example.album.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

data class ImportSummary(val appliedValues: Int)

/**
 * Exports and imports the user data that Album keeps locally.
 *
 * The app has no cloud backup on purpose (Pixiv cookies and the private Trash
 * folder must not leave the device), so this file is the only way to carry
 * favorites, queues, excluded folders and preferences to another device. It
 * deliberately contains no media, no thumbnails and no credentials.
 */
object UserDataBackup {
    const val FORMAT_VERSION = 1
    const val FILE_NAME_PREFIX = "album-backup"
    private const val MAX_IMPORT_BYTES = 4 * 1024 * 1024

    private const val SETTINGS = "album_settings"
    private const val PREFERENCES = "album_preferences"
    private const val CLEANUP = "cleanup_preferences"

    /** Device-specific or security-relevant values that must not travel. */
    private val settingsDenylist = setOf(
        "wallpaper_restore_version",
        "thumbnail_cache_generation",
        "media_management",
        "all_files_access"
    )

    private val preferencesAllowlist = setOf(
        "favorites",
        "favorite_keys",
        "wallpaper_queue_uris",
        "wallpaper_queue_order",
        "slideshow_queue_uris",
        "slideshow_queue_order"
    )

    private val cleanupAllowlist = setOf("excluded_folders")

    fun defaultFileName(): String = "$FILE_NAME_PREFIX-${BuildConfig.VERSION_NAME}.json"

    fun export(context: Context): String {
        val settings = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val cleanup = context.getSharedPreferences(CLEANUP, Context.MODE_PRIVATE)

        return JSONObject()
            .put("format", FORMAT_VERSION)
            .put("applicationId", BuildConfig.APPLICATION_ID)
            .put("versionName", BuildConfig.VERSION_NAME)
            .put("exportedAt", System.currentTimeMillis())
            .put("settings", encodeSection(settings.all.filterKeys { it !in settingsDenylist }))
            .put("preferences", encodeSection(preferences.all.filterKeys { it in preferencesAllowlist }))
            .put("cleanup", encodeSection(cleanup.all.filterKeys { it in cleanupAllowlist }))
            .toString(2)
    }

    fun import(context: Context, raw: String): ImportSummary {
        require(raw.length <= MAX_IMPORT_BYTES) { "Backup file is too large" }
        val json = JSONObject(raw)
        val format = json.optInt("format", -1)
        require(format == FORMAT_VERSION) {
            "Unsupported backup format: $format"
        }
        var applied = 0
        applied += applySection(
            context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE),
            json.optJSONObject("settings"),
            settingsDenylist
        )
        applied += applySection(
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE),
            json.optJSONObject("preferences"),
            allowOnly = preferencesAllowlist
        )
        applied += applySection(
            context.getSharedPreferences(CLEANUP, Context.MODE_PRIVATE),
            json.optJSONObject("cleanup"),
            allowOnly = cleanupAllowlist
        )
        return ImportSummary(applied)
    }

    private fun encodeSection(values: Map<String, *>): JSONObject {
        val json = JSONObject()
        values.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is Set<*> -> json.put(key, JSONArray(value.mapNotNull { it?.toString() }))
                is Boolean, is Number, is String -> json.put(key, value)
            }
        }
        return json
    }

    private fun applySection(
        preferences: android.content.SharedPreferences,
        section: JSONObject?,
        denylist: Set<String> = emptySet(),
        allowOnly: Set<String>? = null
    ): Int {
        if (section == null) return 0
        val editor = preferences.edit()
        var applied = 0
        section.keys().forEach { key ->
            if (key in denylist) return@forEach
            if (allowOnly != null && key !in allowOnly) return@forEach
            when (val value = section.opt(key)) {
                is JSONArray -> {
                    editor.putStringSet(
                        key,
                        (0 until value.length()).mapNotNull { value.optString(it).takeIf(String::isNotBlank) }.toSet()
                    )
                }

                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
                else -> return@forEach
            }
            applied++
        }
        editor.apply()
        return applied
    }
}

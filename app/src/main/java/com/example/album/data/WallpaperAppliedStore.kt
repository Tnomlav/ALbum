package com.example.album.data

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import com.example.album.wallpaper.ImageWallpaperService
import com.example.album.wallpaper.VideoWallpaperService
import java.security.MessageDigest

/**
 * Remembers which queue was last handed to the system wallpaper flow so the
 * manager can show "Apply" versus "Re-apply" for the current queue.
 */
object WallpaperAppliedStore {
    const val KIND_STATIC = "static"
    const val KIND_DYNAMIC = "dynamic"

    private const val PREFERENCES = "album_preferences"
    private const val STATIC_SIGNATURE_KEY = "wallpaper_applied_signature_static"
    private const val DYNAMIC_SIGNATURE_KEY = "wallpaper_applied_signature_dynamic"

    fun signature(uris: List<String>): String {
        if (uris.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-1")
        uris.forEach { uri ->
            digest.update(uri.toByteArray())
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun markApplied(context: Context, kind: String, uris: List<String>) {
        val key = signatureKey(kind) ?: return
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(key, signature(uris))
            .apply()
    }

    fun appliedSignature(context: Context, kind: String): String? {
        val key = signatureKey(kind) ?: return null
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(key, null)
    }

    /** True while the matching Album live wallpaper component is active. */
    fun isWallpaperActive(context: Context, kind: String): Boolean {
        val service = when (kind) {
            KIND_STATIC -> ImageWallpaperService::class.java
            KIND_DYNAMIC -> VideoWallpaperService::class.java
            else -> return false
        }
        val info = runCatching { WallpaperManager.getInstance(context).wallpaperInfo }.getOrNull() ?: return false
        return info.component == ComponentName(context, service)
    }

    private fun signatureKey(kind: String): String? = when (kind) {
        KIND_STATIC -> STATIC_SIGNATURE_KEY
        KIND_DYNAMIC -> DYNAMIC_SIGNATURE_KEY
        else -> null
    }
}

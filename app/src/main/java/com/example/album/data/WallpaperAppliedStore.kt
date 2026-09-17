package com.example.album.data

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
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
    /**
     * The system wallpaper id of the still image we leave behind as a fallback.
     * Comparing it later tells us whether the user has picked a wallpaper of
     * their own since we applied ours.
     */
    private const val FALLBACK_STATIC_ID_KEY = "wallpaper_applied_static_id"

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

    /** Call right after writing the fallback still image. */
    fun recordFallbackStaticId(context: Context) {
        val id = currentStaticId(context)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { putInt(FALLBACK_STATIC_ID_KEY, id) }
    }

    /** The id recorded when we last applied a wallpaper, or -1. */
    fun fallbackStaticId(context: Context): Int =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getInt(FALLBACK_STATIC_ID_KEY, -1)

    fun currentStaticId(context: Context): Int = runCatching {
        WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
    }.getOrDefault(-1)

    /**
     * Re-binds one of our live wallpapers. The system unbinds it when the
     * package is replaced, which is why an app update used to leave the user
     * with whatever still wallpaper was underneath.
     */
    fun rebind(context: Context, kind: String): Boolean {
        val service: Class<out android.service.wallpaper.WallpaperService> = when (kind) {
            KIND_STATIC -> ImageWallpaperService::class.java
            KIND_DYNAMIC -> VideoWallpaperService::class.java
            else -> return false
        }
        val manager = WallpaperManager.getInstance(context)
        val component = ComponentName(context, service)
        // Called through reflection: the compile-time SDK this project builds
        // against no longer exposes setWallpaperComponent, but the framework
        // still implements it, and a missing method simply means "no restore".
        return runCatching {
            WallpaperManager::class.java
                .getMethod("setWallpaperComponent", ComponentName::class.java)
                .invoke(manager, component)
        }.isSuccess
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

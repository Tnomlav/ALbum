package com.example.album.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Lets the running wallpaper services react as soon as the user applies new
 * settings. The services live in the app process, so a package-scoped
 * broadcast is enough and does not require any extra permission.
 */
object WallpaperRefresh {
    const val ACTION_SETTINGS_CHANGED = "com.example.album.action.WALLPAPER_SETTINGS_CHANGED"

    fun notifySettingsChanged(context: Context) {
        val intent = Intent(ACTION_SETTINGS_CHANGED).setPackage(context.packageName)
        runCatching { context.sendBroadcast(intent) }
    }

    fun register(context: Context, receiver: BroadcastReceiver) {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_SETTINGS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
}

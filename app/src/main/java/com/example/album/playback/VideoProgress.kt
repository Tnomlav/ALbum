package com.example.album.playback

import android.net.Uri

/**
 * The preference key a video's playback position is stored under.
 *
 * Every writer and reader has to agree on this: the in-app player used to save
 * with `Uri.hashCode()` while the viewer looked the value up with
 * `uri.toString().hashCode()`, so nothing was ever resumed.
 */
internal fun videoProgressKey(uri: Uri): String = videoProgressKey(uri.toString())

internal fun videoProgressKey(uri: String): String = "video_position_${uri.hashCode()}"

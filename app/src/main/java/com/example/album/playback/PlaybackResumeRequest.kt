package com.example.album.playback

data class PlaybackResumeRequest(
    val uri: String,
    val positionMs: Long,
    val playWhenReady: Boolean,
    val requestId: Long
)

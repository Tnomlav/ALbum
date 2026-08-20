package com.example.album.playback

private const val FINISHED_TOLERANCE_MS = 1_000L

internal fun resumePosition(savedPositionMs: Long, durationMs: Long): Long {
    if (savedPositionMs <= 0L) return 0L
    if (durationMs > 0L && savedPositionMs >= durationMs - FINISHED_TOLERANCE_MS) return 0L
    return if (durationMs > 0L) savedPositionMs.coerceAtMost(durationMs) else savedPositionMs
}

internal fun positionForPersistence(positionMs: Long, durationMs: Long, playbackEnded: Boolean): Long {
    if (playbackEnded) return 0L
    return resumePosition(positionMs, durationMs)
}

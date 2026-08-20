package com.example.album.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlaybackLogicTest {
    @Test
    fun `resume keeps an unfinished position`() {
        assertEquals(42_000L, resumePosition(42_000L, 120_000L))
    }

    @Test
    fun `resume restarts a video saved at its end`() {
        assertEquals(0L, resumePosition(119_500L, 120_000L))
        assertEquals(0L, resumePosition(120_000L, 120_000L))
    }

    @Test
    fun `ended playback never persists the terminal position`() {
        assertEquals(0L, positionForPersistence(120_000L, 120_000L, playbackEnded = true))
    }

    @Test
    fun `unknown duration preserves a valid saved position`() {
        assertEquals(12_000L, resumePosition(12_000L, 0L))
    }
}

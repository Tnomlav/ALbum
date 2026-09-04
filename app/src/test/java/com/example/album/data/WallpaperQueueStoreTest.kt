package com.example.album.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperQueueStoreTest {
    @Test
    fun normalizedQueueKeepsOrderAndAppendsMissingUris() {
        val state = WallpaperQueueState(
            uris = linkedSetOf("b", "a", "c"),
            order = listOf("a", "a", "missing", "b")
        ).normalized()

        assertEquals(setOf("a", "b", "c"), state.uris)
        assertEquals(listOf("a", "b", "c"), state.order)
    }

    @Test
    fun normalizedQueueRemovesBlankEntries() {
        val state = WallpaperQueueState(
            uris = setOf("", "a"),
            order = listOf("", "a")
        ).normalized()

        assertEquals(setOf("a"), state.uris)
        assertEquals(listOf("a"), state.order)
    }
}

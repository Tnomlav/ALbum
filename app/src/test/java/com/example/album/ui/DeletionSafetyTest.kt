package com.example.album.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DeletionSafetyTest {
    @Test
    fun recycleModeOnlyDeletesSuccessfullyBackedUpSources() {
        val result = resolveDeletionSources(
            requestedSources = listOf("content://one", "content://two", "content://three"),
            backedUpSources = setOf("content://one", "content://three"),
            recycleEnabled = true
        )

        assertEquals(setOf("content://one", "content://three"), result)
    }

    @Test
    fun permanentDeleteDoesNotRequireBackup() {
        val result = resolveDeletionSources(
            requestedSources = listOf("content://one", "content://two"),
            backedUpSources = emptySet(),
            recycleEnabled = false
        )

        assertEquals(setOf("content://one", "content://two"), result)
    }
}

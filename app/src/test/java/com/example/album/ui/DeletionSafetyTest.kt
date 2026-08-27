package com.example.album.ui

import com.example.album.data.pendingRecycleSourceUris
import com.example.album.data.hasEnoughBackupSpace
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

    @Test
    fun repeatedRecycleRequestsAreIdempotentBySourceUri() {
        assertEquals(
            listOf("content://one", "content://two"),
            pendingRecycleSourceUris(
                listOf("content://one", "content://one", "content://two"),
                emptySet()
            )
        )
    }

    @Test
    fun alreadyRecycledSourcesAreExcludedFromASecondRequest() {
        assertEquals(
            listOf("content://two"),
            pendingRecycleSourceUris(
                listOf("content://one", "content://two"),
                setOf("content://one")
            )
        )
    }

    @Test
    fun backupRejectsFilesThatWouldLeaveNoSafetyMargin() {
        assertEquals(false, hasEnoughBackupSpace(10L * 1024L * 1024L, 17L * 1024L * 1024L))
        assertEquals(true, hasEnoughBackupSpace(10L * 1024L * 1024L, 18L * 1024L * 1024L))
    }
}

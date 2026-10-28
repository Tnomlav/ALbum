package com.example.album.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateCleanupActionsTest {
    @Test
    fun idleStateShowsBothScanModes() {
        assertEquals(
            DuplicateScanButtonVisibility(quick = true, deep = true, stop = false),
            duplicateScanButtonVisibility(scanning = false, completedMode = null)
        )
    }

    @Test
    fun completedQuickScanHidesDeepScan() {
        assertEquals(
            DuplicateScanButtonVisibility(quick = true, deep = false, stop = false),
            duplicateScanButtonVisibility(scanning = false, completedMode = DuplicateScanMode.Quick)
        )
    }

    @Test
    fun completedDeepScanHidesQuickScan() {
        assertEquals(
            DuplicateScanButtonVisibility(quick = false, deep = true, stop = false),
            duplicateScanButtonVisibility(scanning = false, completedMode = DuplicateScanMode.Deep)
        )
    }

    @Test
    fun runningScanOnlyShowsStop() {
        DuplicateScanMode.entries.forEach { mode ->
            assertEquals(
                DuplicateScanButtonVisibility(quick = false, deep = false, stop = true),
                duplicateScanButtonVisibility(scanning = true, completedMode = mode)
            )
        }
    }
}

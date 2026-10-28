package com.example.album.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ToolboxOrderTest {
    @Test
    fun upgradeAddsSearchModulesWithoutLosingCustomOrder() {
        val migrated = normalizeToolboxOrder(
            listOf("slideshow", "archive", "wallpaper", "cleanup", "pixiv")
        )

        assertEquals(
            listOf("slideshow", "wallpaper", "cleanup", "pixiv", "saucenao", "ascii2d"),
            migrated
        )
        assertFalse(migrated.contains("archive"))
    }

    @Test
    fun emptyAndDuplicateOrdersNormalizeToEveryToolOnce() {
        val normalized = normalizeToolboxOrder(
            listOf("ascii2d", "wallpaper", "ascii2d", "unknown")
        )

        assertEquals(
            listOf("cleanup", "slideshow", "pixiv", "saucenao", "ascii2d", "wallpaper"),
            normalized
        )
        assertEquals(normalized.size, normalized.distinct().size)
    }
}

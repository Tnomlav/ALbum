package com.example.album.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyFolderToneTest {
    @Test
    fun folderToneIsStableAndCaseInsensitive() {
        assertEquals(
            emptyFolderTone("Pictures", darkTheme = false),
            emptyFolderTone("pictures", darkTheme = false)
        )
    }

    @Test
    fun differentFoldersKeepDifferentPastelHues() {
        val backgrounds = listOf(
            "Pictures",
            "Screenshots",
            "Pixiv",
            "Downloads",
            "Camera",
            "Wallpapers",
            "Saved",
            "Favorites"
        ).map { emptyFolderTone(it, darkTheme = false).background }

        assertTrue("Empty folders should not collapse to one shared fill", backgrounds.distinct().size > 1)
    }

    @Test
    fun lightAndDarkThemesUseSeparateTones() {
        val light = emptyFolderTone("Pictures", darkTheme = false)
        val dark = emptyFolderTone("Pictures", darkTheme = true)

        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.icon, dark.icon)
    }
}

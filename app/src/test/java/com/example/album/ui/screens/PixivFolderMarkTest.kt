package com.example.album.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivFolderMarkTest {
    @Test
    fun pixivFolderNameMatchesCaseInsensitively() {
        assertTrue(isPixivFolderName("Pixiv"))
        assertTrue(isPixivFolderName("pixiv"))
        assertTrue(isPixivFolderName("PIXIV"))
    }

    @Test
    fun similarNamesDoNotReceivePixivMark() {
        assertFalse(isPixivFolderName("Pixiv Archive"))
        assertFalse(isPixivFolderName("My Pixiv"))
        assertFalse(isPixivFolderName("Pictures"))
    }
}

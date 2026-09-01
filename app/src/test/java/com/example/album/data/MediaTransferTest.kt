package com.example.album.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTransferTest {
    private val existing = setOf("photo.jpg", "photo (1).jpg", "photo (2).jpg")

    @Test
    fun noConflictKeepsOriginalName() {
        assertEquals(
            TransferTargetName("photo.jpg"),
            resolveTransferTargetName("photo.jpg", emptySet(), ConflictPolicy.KeepBoth)
        )
    }

    @Test
    fun skipReportsSuccessfulNoOp() {
        val result = resolveTransferTargetName("photo.jpg", existing, ConflictPolicy.Skip)
        assertEquals("photo.jpg", result.name)
        assertTrue(result.skipped)
    }

    @Test
    fun overwriteKeepsOriginalNameForReplacement() {
        val result = resolveTransferTargetName("photo.jpg", existing, ConflictPolicy.Overwrite)
        assertEquals("photo.jpg", result.name)
        assertFalse(result.skipped)
    }

    @Test
    fun keepBothAdvancesPastAllExistingSuffixes() {
        assertEquals(
            TransferTargetName("photo (3).jpg"),
            resolveTransferTargetName("photo.jpg", existing, ConflictPolicy.KeepBoth)
        )
    }

    @Test
    fun namesWithoutExtensionAlsoGetStableSuffixes() {
        assertEquals(
            TransferTargetName("photo (2)"),
            resolveTransferTargetName("photo", setOf("photo", "photo (1)"), ConflictPolicy.KeepBoth)
        )
    }

    @Test
    fun transferUsesFullRelativePathForNestedMediaStoreFolder() {
        assertEquals(
            "AI生成/AI生成1",
            transferFolderPath("AI生成/AI生成1/", "AI生成1")
        )
    }

    @Test
    fun sameLeafFoldersKeepDifferentTransferIdentities() {
        assertEquals(
            "AI生成/AI生成1",
            transferFolderPath("AI生成/AI生成1/", "AI生成1")
        )
        assertEquals(
            "Pictures/其他/AI生成1",
            transferFolderPath("Pictures/其他/AI生成1/", "AI生成1")
        )
    }

    @Test
    fun mediaStorePathDoesNotDuplicateAnExplicitPublicRoot() {
        assertEquals(
            "AI生成/AI生成1/",
            resolveMediaStoreRelativePath("AI生成/AI生成1")
        )
        assertEquals(
            "Pictures/AI生成/AI生成1/",
            resolveMediaStoreRelativePath("Pictures/AI生成/AI生成1")
        )
    }
}

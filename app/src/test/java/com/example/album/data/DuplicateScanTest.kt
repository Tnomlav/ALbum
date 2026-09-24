package com.example.album.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Duplicate checking is exact-only: candidates are files whose size repeats, and
 * "duplicate" means the bytes are equal. These are the two pieces worth pinning,
 * because they are what makes the scan fast and correct.
 */
class DuplicateScanTest {

    private fun stream(text: String) = ByteArrayInputStream(text.toByteArray())

    @Test
    fun identicalFilesMatch() {
        assertTrue(sameContent(stream("hello world"), stream("hello world")))
    }

    @Test
    fun emptyFilesMatch() {
        assertTrue(sameContent(stream(""), stream("")))
    }

    @Test
    fun aDifferenceInTheMiddleDoesNotMatch() {
        val left = "a".repeat(200_000) + "X" + "b".repeat(200_000)
        val right = "a".repeat(200_000) + "Y" + "b".repeat(200_000)
        assertFalse(sameContent(stream(left), stream(right)))
    }

    @Test
    fun differentLengthsDoNotMatch() {
        assertFalse(sameContent(stream("same prefix, different tail"), stream("same prefix, different")))
        assertFalse(sameContent(stream("short"), stream("")))
    }

    @Test
    fun onlySizesThatRepeatBecomeCandidates() {
        // 10 appears twice (positions 0, 2) and 20 twice (positions 1, 4).
        assertEquals(listOf(0, 2, 1, 4), duplicateSizeCandidates(listOf(10L, 20L, 10L, 30L, 20L, 0L)))
        assertTrue(duplicateSizeCandidates(listOf(1L, 2L, 3L)).isEmpty())
        // An unknown size is not a match for another unknown size.
        assertTrue(duplicateSizeCandidates(listOf(0L, 0L)).isEmpty())
    }

    @Test
    fun theSameArtworkArchivedTwiceIsANearDuplicateCandidate() {
        // What an archived folder actually looks like: one artwork, written
        // twice on different days, so the bytes differ slightly but the pixel
        // size is the same and the file sizes are 0.05% apart.
        val widths = listOf(1200, 1200, 900)
        val heights = listOf(1600, 1600, 1600)
        val sizes = listOf(1_516_874L, 1_516_168L, 1_516_000L)
        assertEquals(setOf(0, 1), nearDuplicateCandidates(widths, heights, sizes))
    }

    @Test
    fun differentDimensionsOrVeryDifferentSizesAreNotCandidates() {
        // Same dimensions but a completely different amount of data: a different
        // picture that happens to be the same shape.
        assertTrue(
            nearDuplicateCandidates(
                widths = listOf(1200, 1200),
                heights = listOf(1600, 1600),
                sizes = listOf(1_500_000L, 600_000L)
            ).isEmpty()
        )
        // Same size, different shape: a resize, which this check deliberately
        // does not cover.
        assertTrue(
            nearDuplicateCandidates(
                widths = listOf(1200, 840),
                heights = listOf(1600, 1120),
                sizes = listOf(1_500_000L, 1_500_000L)
            ).isEmpty()
        )
        // Unknown dimensions cannot be compared.
        assertTrue(
            nearDuplicateCandidates(
                widths = listOf(0, 0),
                heights = listOf(0, 0),
                sizes = listOf(1_500_000L, 1_500_000L)
            ).isEmpty()
        )
    }

    @Test
    fun theSamePictureMatchesOnlyWhileTheHashStaysClose() {
        assertTrue(looksLikeSamePicture(0L, 0b111111L, 100f, 100f, 100f, 104f, 98f, 101f))
        assertFalse(looksLikeSamePicture(0L, 0b1111111L, 100f, 100f, 100f, 100f, 100f, 100f))
        assertFalse(looksLikeSamePicture(0L, 0L, 100f, 100f, 100f, 200f, 60f, 40f))
    }

    @Test
    fun theHashGridSetsABitWhenTheLeftCellIsBrighter() {
        val brighteningRight = IntArray(9 * 8) { index -> (index % 9) * 10 }
        assertEquals(0L, dHashFromLuminanceGrid(brighteningRight))
        val darkeningRight = IntArray(9 * 8) { index -> 80 - (index % 9) * 10 }
        assertEquals(-1L, dHashFromLuminanceGrid(darkeningRight))
        val single = brighteningRight.copyOf()
        single[3 * 9 + 4] = 999
        // Row 3, column 4 -> bit 3*8 + 4.
        assertEquals(1L shl 28, dHashFromLuminanceGrid(single))
    }
}

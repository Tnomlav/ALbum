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
    fun onlyFilesWithTheSameSizeAndShapeBecomeCandidates() {
        val sizes = listOf(10L, 20L, 10L, 30L, 20L, 0L)
        val widths = listOf(100, 100, 100, 100, 100, 100)
        val heights = listOf(200, 200, 200, 200, 200, 200)
        // 10 appears twice (0, 2) and 20 twice (1, 4); the zero size is skipped.
        assertEquals(listOf(0, 2, 1, 4), duplicateCandidates(sizes, widths, heights))
        assertTrue(duplicateCandidates(listOf(1L, 2L, 3L), widths, heights).isEmpty())
        // Same size, different shape: cannot be byte-identical.
        assertTrue(
            duplicateCandidates(
                sizes = listOf(10L, 10L),
                widths = listOf(100, 120),
                heights = listOf(200, 200)
            ).isEmpty()
        )
        // Unknown shape falls back to the size-only group rather than being skipped.
        assertEquals(
            listOf(0, 1),
            duplicateCandidates(
                sizes = listOf(10L, 10L),
                widths = listOf(0, 0),
                heights = listOf(0, 0)
            )
        )
    }

    @Test
    fun theCheckNeverLooksAtNamesOrPictures() {
        // The same file name or the same artwork id must not matter: only the
        // bytes do, so the candidate step works for any picture on the device.
        assertEquals(
            listOf(0, 1),
            duplicateCandidates(
                sizes = listOf(64L, 64L),
                widths = listOf(1200, 1200),
                heights = listOf(1600, 1600)
            )
        )
    }

}

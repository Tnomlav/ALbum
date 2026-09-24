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
}

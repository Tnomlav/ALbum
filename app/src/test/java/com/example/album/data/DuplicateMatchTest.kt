package com.example.album.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Duplicates" only compared SHA-256, so a picture saved twice at different
 * quality never showed up even though it looks identical. These thresholds
 * decide what counts as the same picture now.
 */
class DuplicateMatchTest {

    private fun matches(
        hashA: Long = 0L,
        hashB: Long = 0L,
        aspectA: Float = 1.5f,
        aspectB: Float = 1.5f,
        redA: Float = 100f,
        greenA: Float = 100f,
        blueA: Float = 100f,
        redB: Float = 100f,
        greenB: Float = 100f,
        blueB: Float = 100f
    ) = looksLikeSamePicture(hashA, hashB, aspectA, aspectB, redA, greenA, blueA, redB, greenB, blueB)

    @Test
    fun aReEncodeAtAnotherSizeStillMatches() {
        // Same picture, a few bits of the hash differ and the average colour
        // shifted slightly by re-compression.
        assertTrue(matches(hashA = 0L, hashB = 0b111111L, aspectB = 1.48f, redB = 112f, greenB = 106f))
    }

    @Test
    fun aCroppedPictureIsNotADuplicate() {
        // Same scene, different aspect ratio.
        assertFalse(matches(aspectA = 1.5f, aspectB = 1.15f))
    }

    @Test
    fun aDifferentPictureIsNotADuplicate() {
        // A different photo: far more hash bits differ.
        assertFalse(matches(hashB = 0b1111111111L))
        // ... or a clearly different colour balance.
        assertFalse(matches(redB = 200f, greenB = 60f, blueB = 40f))
    }

    @Test
    fun degenerateValuesDoNotMatch() {
        assertFalse(matches(aspectA = 0f, aspectB = 0f))
        assertFalse(matches(aspectA = Float.NaN, aspectB = Float.NaN))
    }

    @Test
    fun onlyPicturesSharingAnAspectRatioAreDecoded() {
        val aspects = listOf(1.5f, 1.51f, 1.33f, 0.75f, 0f)
        val candidates = aspectCandidatePositions(aspects)
        // The two landscape photos can match each other; the portrait one has no
        // partner, and the unknown aspect is kept (never silently skipped).
        assertTrue(0 in candidates)
        assertTrue(1 in candidates)
        assertTrue(2 !in candidates)
        assertTrue(4 in candidates)
    }
}

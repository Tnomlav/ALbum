package com.example.album.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Authorized folders have no MediaStore row, so the player leans on these
 * dimensions to follow the video's own orientation. A sideways recording must
 * report the size the viewer actually sees, otherwise "match video ratio" picks
 * the wrong way round.
 */
class LocalFolderVideoMetadataTest {
    @Test
    fun uprightVideoKeepsItsCodedSize() {
        assertEquals(1920 to 1080, rotatedVideoSize(1920, 1080, 0))
        assertEquals(1920 to 1080, rotatedVideoSize(1920, 1080, 180))
    }

    @Test
    fun quarterTurnsAreSwapped() {
        assertEquals(1080 to 1920, rotatedVideoSize(1920, 1080, 90))
        assertEquals(1080 to 1920, rotatedVideoSize(1920, 1080, 270))
    }

    @Test
    fun outOfRangeRotationIsNormalized() {
        assertEquals(1080 to 1920, rotatedVideoSize(1920, 1080, -90))
        assertEquals(1080 to 1920, rotatedVideoSize(1920, 1080, 450))
    }

    @Test
    fun unknownSizeStaysUnknownInsteadOfBecomingPortrait() {
        assertEquals(0 to 0, rotatedVideoSize(0, 0, 90))
        assertEquals(0 to 0, rotatedVideoSize(1920, 0, 0))
        assertEquals(0, VideoMetadata().width)
        assertEquals(0, VideoMetadata().height)
        assertEquals(0L, VideoMetadata().durationMs)
    }
}

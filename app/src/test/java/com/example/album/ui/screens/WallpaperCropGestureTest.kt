package com.example.album.ui.screens

import com.example.album.ui.editor.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crop frame's touch bands, shared by the frame itself and by the gesture
 * layer that covers the black bars above and below the picture. The two used to
 * carry their own copy of this math, which is how they drifted apart.
 */
class WallpaperCropGestureTest {

    private val outward = 96f
    private val inward = 48f

    private fun handle(x: Float, y: Float) = cropHandleAt(
        frameLeft = 100f,
        frameTop = 200f,
        frameRight = 500f,
        frameBottom = 900f,
        pointX = x,
        pointY = y,
        outwardX = outward,
        outwardY = outward,
        inwardX = inward,
        inwardY = inward
    )

    @Test
    fun theFrameBodyIsAMove() {
        assertEquals(CROP_HANDLE_MOVE, handle(300f, 500f))
    }

    @Test
    fun justOutsideTheRightEdgeStillGrabsThatEdge() {
        // 60px outside: past the inward band, inside the outward one.
        assertEquals(7, handle(560f, 500f))
    }

    @Test
    fun justInsideTheRightEdgeGrabsThatEdge() {
        assertEquals(7, handle(480f, 500f))
    }

    @Test
    fun theBlackBarJustOutsideTheTopEdgeGrabsThatEdge() {
        assertEquals(4, handle(300f, 140f))
    }

    @Test
    fun aTouchFarFromTheFrameGrabsNothing() {
        assertEquals(CROP_HANDLE_NONE, handle(1200f, 1000f))
    }

    @Test
    fun theNearestCornerWins() {
        assertEquals(0, handle(90f, 190f))
        assertEquals(1, handle(510f, 190f))
        assertEquals(2, handle(90f, 910f))
        assertEquals(3, handle(510f, 910f))
    }

    @Test
    fun theMiddleOfAnEdgeZerosInOnThatEdgeOnly() {
        // 300px from the left corner: no corner is close, so the top edge wins.
        assertEquals(4, handle(300f, 180f))
    }

    @Test
    fun tiltingOnlyShrinksWhatIsDrawn() {
        val chosen = NormalizedRect(.05f, .05f, .95f, .95f)
        val imageRatio = 16f / 9f
        assertEquals(1f, rotatedWallpaperPreviewScale(chosen, 0f, imageRatio), 1e-4f)
        val tiltedScale = rotatedWallpaperPreviewScale(chosen, 30f, imageRatio)
        assertTrue(tiltedScale < 1f)
        // The frame itself is untouched by the tilt ...
        assertFrameEquals(chosen, wallpaperPreviewFrame(chosen, 30f, imageRatio))
        // ... while what is drawn is the inset version.
        assertTrue(chosen.width * tiltedScale < chosen.width - 1e-3f)
    }

    @Test
    fun aDragMadeWhileTiltedDoesNotShrinkTheCrop() {
        val imageRatio = 16f / 9f
        val dragged = NormalizedRect(0f, 0f, 1f, 1f) // the user dragged to fill the picture
        // The old rule clipped the dragged frame against the rotated bounds at
        // the scale of the *previous*, smaller frame; the stored crop silently
        // shrank and never came back when the angle returned to zero.
        val oldStored = constrainWallpaperFrameToRotatedBounds(dragged, 30f, 1f)
        assertTrue(oldStored.width < dragged.width - 1e-3f)
        // The new rule only keeps the frame inside the picture ...
        val stored = constrainWallpaperFrameToImage(dragged)
        assertFrameEquals(dragged, stored)
        // ... so zero degrees shows the size the user dragged.
        assertFrameEquals(dragged, wallpaperPreviewFrame(stored, 0f, imageRatio))
    }

    @Test
    fun theChosenSizeComesBackWhenTheAngleReturnsToZero() {
        val imageRatio = 16f / 9f
        val chosen = NormalizedRect(.05f, .05f, .95f, .95f)
        val tilted = wallpaperPreviewFrame(chosen, 35f, imageRatio)
        // Tilting and returning to zero gives back exactly the chosen frame.
        assertFrameEquals(chosen, wallpaperPreviewFrame(tilted, 0f, imageRatio))
    }

    private fun assertFrameEquals(expected: NormalizedRect, actual: NormalizedRect) {
        assertEquals(expected.left, actual.left, 1e-4f)
        assertEquals(expected.top, actual.top, 1e-4f)
        assertEquals(expected.right, actual.right, 1e-4f)
        assertEquals(expected.bottom, actual.bottom, 1e-4f)
    }
}

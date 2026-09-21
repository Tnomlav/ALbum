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
    fun zeroDegreesKeepsExactlyTheFrameTheUserChose() {
        val chosen = NormalizedRect(.12f, .2f, .88f, .8f)
        assertFrameEquals(chosen, inscribeWallpaperFrameInRotatedPicture(chosen, 0f, 16f / 9f))
    }

    @Test
    fun aTiltShrinksTheFrameOnlyAsFarAsTheBoundaryNeeds() {
        val imageRatio = 16f / 9f
        val chosen = NormalizedRect(.05f, .05f, .95f, .95f)
        val tilted = inscribeWallpaperFrameInRotatedPicture(chosen, 30f, imageRatio)
        assertTrue(tilted.width < chosen.width - 1e-3f)
        assertTrue(wallpaperFrameInsideRotatedPicture(tilted, 30f, imageRatio))
        // "Exactly inscribed": no room is wasted, so growing it by one percent
        // would already leave the turned picture.
        val grown = NormalizedRect(
            tilted.centerX() - tilted.width / 2f * 1.01f,
            tilted.centerY() - tilted.height / 2f * 1.01f,
            tilted.centerX() + tilted.width / 2f * 1.01f,
            tilted.centerY() + tilted.height / 2f * 1.01f
        )
        assertTrue(!wallpaperFrameInsideRotatedPicture(grown, 30f, imageRatio))
    }

    @Test
    fun aDragMadeWhileTiltedKeepsTheSizeTheUserAskedFor() {
        val dragged = NormalizedRect(0f, 0f, 1f, 1f) // the user dragged to fill the picture
        // Dragging only has to stay inside the picture itself; the angle preview
        // shrinks what is drawn instead of rewriting the frame, so the size is
        // still there when the angle returns to zero.
        val stored = constrainWallpaperFrameToImage(dragged)
        assertFrameEquals(dragged, stored)
        assertFrameEquals(dragged, inscribeWallpaperFrameInRotatedPicture(stored, 0f, 16f / 9f))
    }

    @Test
    fun aFramePushedIntoACornerWhileTiltedComesBackInside() {
        val imageRatio = 9f / 16f
        val chosen = NormalizedRect(.05f, .05f, .95f, .95f)
        // Shoved far past the right edge and above the top edge: the drawn frame
        // used to be clipped against the *bounding box* of the turned picture,
        // which is why it could sit over the black corners.
        val shoved = NormalizedRect(1.6f, -.6f, 2.5f, -.05f)
        val tilted = inscribeWallpaperFrameInRotatedPicture(shoved, 40f, imageRatio)
        assertTrue(wallpaperFrameInsideRotatedPicture(tilted, 40f, imageRatio))
        val betterPlaced = inscribeWallpaperFrameInRotatedPicture(chosen, 40f, imageRatio)
        assertTrue(wallpaperFrameInsideRotatedPicture(betterPlaced, 40f, imageRatio))
        // Shrinking keeps the shape of the frame it was given.
        assertEquals(shoved.width / shoved.height, tilted.width / tilted.height, 1e-3f)
    }

    @Test
    fun theChosenSizeComesBackWhenTheAngleReturnsToZero() {
        val imageRatio = 16f / 9f
        val chosen = NormalizedRect(.05f, .05f, .95f, .95f)
        val tilted = inscribeWallpaperFrameInRotatedPicture(chosen, 35f, imageRatio)
        assertTrue(tilted.width < chosen.width - 1e-3f)
        // The angle never rewrites the frame the user chose -- the screen keeps
        // the requested frame and draws the inscribed one -- so zero degrees
        // shows the chosen size again instead of the shrunk one.
        assertFrameEquals(chosen, inscribeWallpaperFrameInRotatedPicture(chosen, 0f, imageRatio))
    }

    private fun NormalizedRect.centerX() = (left + right) / 2f

    private fun NormalizedRect.centerY() = (top + bottom) / 2f

    private fun assertFrameEquals(expected: NormalizedRect, actual: NormalizedRect) {
        assertEquals(expected.left, actual.left, 1e-4f)
        assertEquals(expected.top, actual.top, 1e-4f)
        assertEquals(expected.right, actual.right, 1e-4f)
        assertEquals(expected.bottom, actual.bottom, 1e-4f)
    }
}

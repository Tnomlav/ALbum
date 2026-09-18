package com.example.album.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The floating window's corner drag used to weight the finger's vertical motion
 * almost twice as heavily as the horizontal one, which made a sideways drag
 * crawl and every wobble jump the window around.
 */
class OverlayMiniWindowResizeTest {

    private val verticalPerWidth = MINI_WINDOW_HEIGHT_PER_WIDTH

    @Test
    fun draggingAlongTheWindowDiagonalTracksTheFingerExactly() {
        // Along the window's own diagonal the corner should follow the finger
        // one to one: moving the corner (t, t*9/16) must grow the width by t.
        val t = 120f
        val growth = cornerResizeGrowth(horizontalGrowth = t, verticalGrowth = t * verticalPerWidth)
        assertEquals(t, growth, 0.01f)
    }

    @Test
    fun sidewaysMotionIsProjectedNotHalved() {
        // A purely horizontal drag should still move the corner most of the way
        // (the old formula only gave half of it).
        val growth = cornerResizeGrowth(horizontalGrowth = 100f, verticalGrowth = 0f)
        assertEquals(100f / (1f + verticalPerWidth * verticalPerWidth), growth, 0.01f)
        assert(growth > 70f)
    }

    @Test
    fun verticalWobbleCannotDominateTheDrag() {
        // A vertical-only wobble must not be amplified: the old formula turned
        // 100px of vertical motion into ~89px of width growth.
        val growth = cornerResizeGrowth(horizontalGrowth = 0f, verticalGrowth = 100f)
        assertEquals(100f * verticalPerWidth / (1f + verticalPerWidth * verticalPerWidth), growth, 0.01f)
        assert(growth < 50f)
    }

    @Test
    fun oppositeCornersUseSignedGrowthTheSameWay() {
        // Sign handling happens before this function (the caller passes growth
        // that is positive when the window should grow), so the projection has
        // to be linear in both arguments.
        val positive = cornerResizeGrowth(80f, 45f)
        val negative = cornerResizeGrowth(-80f, -45f)
        assertEquals(-positive, negative, 0.01f)
    }
}

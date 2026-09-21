package com.example.album.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The floating window's corner semantics, spelled out because they changed
 * several times:
 *
 * | dragged corner | pinned edges        |
 * | -------------- | ------------------- |
 * | bottom-right   | top and left        |
 * | bottom-left    | top and right       |
 * | top-left       | bottom and right    |
 * | top-right      | bottom and left     |
 */
class MiniWindowAnchorTest {

    @Test
    fun bottomRightKeepsTopAndLeft() {
        // Dragging the right side leaves x alone, dragging the bottom leaves y
        // alone: neither edge is pinned by the "opposite" rule.
        val (pinRight, pinBottom) = miniWindowPinnedEdges(
            OverlayMiniWindow.EDGE_RIGHT or OverlayMiniWindow.EDGE_BOTTOM
        )
        assertEquals(false, pinRight)
        assertEquals(false, pinBottom)
    }

    @Test
    fun bottomLeftKeepsTopAndRight() {
        val (pinRight, pinBottom) = miniWindowPinnedEdges(
            OverlayMiniWindow.EDGE_LEFT or OverlayMiniWindow.EDGE_BOTTOM
        )
        assertEquals(true, pinRight)
        assertEquals(false, pinBottom)
    }

    @Test
    fun topLeftKeepsBottomAndRight() {
        val (pinRight, pinBottom) = miniWindowPinnedEdges(
            OverlayMiniWindow.EDGE_LEFT or OverlayMiniWindow.EDGE_TOP
        )
        assertEquals(true, pinRight)
        assertEquals(true, pinBottom)
    }

    @Test
    fun topRightKeepsBottomAndLeft() {
        val (pinRight, pinBottom) = miniWindowPinnedEdges(
            OverlayMiniWindow.EDGE_RIGHT or OverlayMiniWindow.EDGE_TOP
        )
        assertEquals(false, pinRight)
        assertEquals(true, pinBottom)
    }
}

package com.example.album.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoGestureZonesTest {
    @Test
    fun `default brightness and volume split the screen in half`() {
        assertEquals(2, verticalGestureZone(.1f, "1:1"))
        assertEquals(2, verticalGestureZone(.49f, "1:1"))
        assertEquals(3, verticalGestureZone(.51f, "1:1"))
        assertEquals(3, verticalGestureZone(.9f, "1:1"))
    }

    @Test
    fun `equal three way split keeps a neutral middle`() {
        assertEquals(2, verticalGestureZone(.2f, "1:1:1"))
        assertEquals(0, verticalGestureZone(.5f, "1:1:1"))
        assertEquals(3, verticalGestureZone(.8f, "1:1:1"))
    }

    @Test
    fun `one two one split widens the neutral middle`() {
        assertEquals(2, verticalGestureZone(.2f, "1:2:1"))
        assertEquals(0, verticalGestureZone(.5f, "1:2:1"))
        assertEquals(3, verticalGestureZone(.8f, "1:2:1"))
    }

    @Test
    fun `default double tap uses thirds`() {
        assertEquals(-1, doubleTapZone(.1f, "1:1:1"))
        assertEquals(0, doubleTapZone(.5f, "1:1:1"))
        assertEquals(1, doubleTapZone(.9f, "1:1:1"))
    }

    @Test
    fun `one two one double tap keeps the pause zone wider`() {
        assertEquals(-1, doubleTapZone(.2f, "1:2:1"))
        assertEquals(0, doubleTapZone(.5f, "1:2:1"))
        assertEquals(1, doubleTapZone(.8f, "1:2:1"))
    }

    @Test
    fun `one zero one double tap has no pause zone`() {
        assertEquals(-1, doubleTapZone(.1f, "1:0:1"))
        assertEquals(-1, doubleTapZone(.49f, "1:0:1"))
        assertEquals(1, doubleTapZone(.51f, "1:0:1"))
        assertEquals(1, doubleTapZone(.9f, "1:0:1"))
    }
}

package com.example.album.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ListScrollHandleTest {
    @Test
    fun `scrollbar settings accept the html option values`() {
        assertEquals(16, scrollTouchWidthDp("16px"))
        assertEquals(24, scrollTouchWidthDp("24px"))
        assertEquals(32, scrollTouchWidthDp("32px"))
        assertEquals(24, scrollTouchWidthDp("48px"))

        assertEquals(500L, scrollVisibleDurationMillis("0.5秒"))
        assertEquals(1_000L, scrollVisibleDurationMillis("1秒"))
        assertEquals(2_000L, scrollVisibleDurationMillis("2秒"))
        assertEquals(3_000L, scrollVisibleDurationMillis("3秒"))
    }

    @Test
    fun `thumb width follows the html touch width mapping`() {
        assertEquals(4, scrollThumbWidthDp(16))
        assertEquals(5, scrollThumbWidthDp(24))
        assertEquals(7, scrollThumbWidthDp(32))
    }

    @Test
    fun `thumb drag is relative to its starting position and clamps`() {
        assertEquals(0.5f, scrollFractionForThumbDrag(0.25f, 150f, 800f, 200f), 0.0001f)
        assertEquals(0f, scrollFractionForThumbDrag(0.1f, -500f, 800f, 200f), 0f)
        assertEquals(1f, scrollFractionForThumbDrag(0.9f, 500f, 800f, 200f), 0f)
    }
}

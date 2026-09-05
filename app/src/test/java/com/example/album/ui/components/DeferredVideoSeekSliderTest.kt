package com.example.album.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DeferredVideoSeekSliderTest {
    @Test
    fun tap_maps_to_absolute_position_only_when_released() {
        assertEquals(25_000L, deferredSeekAbsoluteValue(250f, 1_000, 100_000L))
        assertEquals(0L, deferredSeekAbsoluteValue(-20f, 1_000, 100_000L))
        assertEquals(100_000L, deferredSeekAbsoluteValue(1_020f, 1_000, 100_000L))
    }

    @Test
    fun drag_applies_distance_to_the_existing_thumb_value() {
        assertEquals(60_000L, deferredSeekRelativeValue(50_000f, 100f, 1_000, 100_000L))
        assertEquals(40_000L, deferredSeekRelativeValue(50_000f, -100f, 1_000, 100_000L))
        assertEquals(0L, deferredSeekRelativeValue(10_000f, -500f, 1_000, 100_000L))
        assertEquals(100_000L, deferredSeekRelativeValue(90_000f, 500f, 1_000, 100_000L))
    }
}

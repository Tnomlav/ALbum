package com.example.album.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PrototypeViewportTest {
    @Test
    fun exactDesignViewportUsesUnitScale() {
        assertEquals(1f, prototypeViewportScale(412f, 900f), 0f)
    }

    @Test
    fun viewportUsesSmallerAxisScale() {
        assertEquals(.5f, prototypeViewportScale(206f, 600f), 0f)
        assertEquals(.5f, prototypeViewportScale(300f, 450f), 0f)
    }

    @Test
    fun emulatorViewportIsHeightLimited() {
        assertEquals(891.4286f / 900f, prototypeViewportScale(411.4286f, 891.4286f), .0001f)
    }

    @Test
    fun commonAspectRatiosNeverStretchPastTheAvailableAxis() {
        listOf(
            320f to 640f,
            360f to 800f,
            412f to 915f,
            600f to 960f,
            800f to 1280f,
            1280f to 800f
        ).forEach { (width, height) ->
            val scale = prototypeViewportScale(width, height)
            assert(scale > 0f)
            assert(scale * PrototypeDesignWidth <= width + .001f)
            assert(scale * PrototypeDesignHeight <= height + .001f)
        }
    }
}

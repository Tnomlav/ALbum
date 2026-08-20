package com.example.album.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageAdjustmentTest {
    @Test
    fun defaultAdjustmentsUseNeutralParameters() {
        assertEquals(
            EditorAdjustmentParameters(1f, 1f, 0f, 0f, 0f, 0f),
            editorAdjustmentParameters(ImageEditState())
        )
    }

    @Test
    fun everyAddedAdjustmentChangesTheOutputMatrix() {
        val baseline = editorAdjustmentParameters(ImageEditState())
        val adjustedStates = listOf(
            ImageEditState(exposure = .5f),
            ImageEditState(brightness = .5f),
            ImageEditState(contrast = 1.5f),
            ImageEditState(tint = .5f),
            ImageEditState(temperature = .5f),
            ImageEditState(highlights = .5f),
            ImageEditState(shadows = .5f),
            ImageEditState(saturation = 1.5f),
            ImageEditState(vibrance = .5f),
            ImageEditState(fade = .5f),
            ImageEditState(sharpness = .5f),
            ImageEditState(enhance = .5f)
        )
        adjustedStates.forEach { state ->
            assertFalse(editorAdjustmentParameters(state) == baseline)
        }
    }
}

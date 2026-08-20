package com.example.album.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageEditorSafetyTest {
    @Test
    fun brushCarouselOrderMatchesPrototype() {
        assertEquals(
            listOf("圆珠笔", "虚线", "钢笔", "毛笔", "马克笔", "荧光笔", "铅笔", "蜡笔", "喷枪", "霓虹笔", "马赛克", "橡皮"),
            EditorBrush.entries.map(EditorBrush::label)
        )
    }

    @Test
    fun onlyFormatsWithMatchingBitmapEncodersCanBeReplaced() {
        assertTrue(supportsInPlaceEdit("image/jpeg"))
        assertTrue(supportsInPlaceEdit("image/png"))
        assertTrue(supportsInPlaceEdit("image/webp"))
        assertFalse(supportsInPlaceEdit("image/gif"))
        assertFalse(supportsInPlaceEdit("image/heic"))
        assertFalse(supportsInPlaceEdit("image/avif"))
    }

    @Test
    fun freeCropDimensionsPreserveSourcePixelAspect() {
        assertEquals(864 to 1872, editorOutputDimensions(1440, 3120, ImageEditState(), .6f))
        assertEquals(
            720 to 1560,
            editorOutputDimensions(
                1440,
                3120,
                ImageEditState(cropRect = NormalizedRect(.25f, .25f, .75f, .75f)),
                1f
            )
        )
    }

    @Test
    fun presetCropDimensionsRespectRotationAndZoom() {
        val square = ImageEditState(crop = CropPreset.Square, composeScale = 2f)
        assertEquals(720 to 720, editorOutputDimensions(1440, 3120, square, 1f))
        assertEquals(720 to 720, editorOutputDimensions(3120, 1440, square.copy(rotation = 90), 1f))
    }

    @Test
    fun exportSamplingMatchesMemoryLimit() {
        assertEquals(1, editorExportSampleSize(1440, 3120))
        assertEquals(2, editorExportSampleSize(8000, 6000))
        assertEquals(4, editorExportSampleSize(12000, 9000))
    }
}

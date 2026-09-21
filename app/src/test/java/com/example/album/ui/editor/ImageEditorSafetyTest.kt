package com.example.album.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.album.ui.screens.inscribeWallpaperFrameInRotatedPicture
import com.example.album.ui.screens.wallpaperFrameInsideRotatedPicture

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

    @Test
    fun wallpaperCropMoveStaysInsideRotatedImageBounds() {
        // A frame shoved into the bottom-right corner is pulled back so that every
        // corner of it lands inside the *turned picture*, not merely inside the
        // bounding box of that picture (which is what let it sit on the black
        // corners before).
        val imageRatio = 16f / 9f
        val frame = inscribeWallpaperFrameInRotatedPicture(
            NormalizedRect(.72f, .72f, .92f, .92f),
            straighten = 45f,
            imageAspectRatio = imageRatio
        )
        assertTrue(wallpaperFrameInsideRotatedPicture(frame, 45f, imageRatio))
        assertTrue(frame.left >= -0.0001f && frame.right <= 1.0001f)
        assertTrue(frame.top >= -0.0001f && frame.bottom <= 1.0001f)
    }
}

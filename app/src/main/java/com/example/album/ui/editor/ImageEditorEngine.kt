package com.example.album.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.res.ResourcesCompat
import com.example.album.R
import com.example.album.data.MediaItem
import com.example.album.data.openMediaInputStream
import com.example.album.data.openMediaOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NormalizedPoint(val x: Float, val y: Float)
data class NormalizedRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}
enum class EditorBrush(val label: String) {
    Pen("圆珠笔"), Dashed("虚线"), Fountain("钢笔"), Brush("毛笔"), Marker("马克笔"),
    Highlighter("荧光笔"), Pencil("铅笔"), Crayon("蜡笔"), Spray("喷枪"),
    Neon("霓虹笔"), Mosaic("马赛克"), Eraser("橡皮")
}
data class EditorStroke(val points: List<NormalizedPoint>, val color: Int, val width: Float, val brush: EditorBrush = EditorBrush.Pen)
enum class EditorTextAlign(val label: String) { Left("左"), Center("中"), Right("右") }
enum class EditorFont(val label: String) {
    System("思源黑体"), Serif("思源宋体"), Monospace("毛笔楷书"), Kai("行书"), Song("小薇体"),
    Hei("黄油体"), Fang("点阵体"), Cursive("龙藏体"), Wide("毛草"), Rounded("快乐体")
}

/** Resolves the same bundled font files for both the Compose preview and export. */
internal object EditorTypefaceRegistry {
    @Volatile private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    fun resolve(font: EditorFont): android.graphics.Typeface? {
        val context = context ?: return null
        val id = when (font) {
            EditorFont.System -> R.font.noto_sans_sc
            EditorFont.Serif -> R.font.noto_serif_sc
            EditorFont.Monospace -> R.font.ma_shan_zheng
            EditorFont.Kai -> R.font.zhi_mang_xing
            EditorFont.Song -> R.font.zcool_xiaowei
            EditorFont.Hei -> R.font.zcool_qingke_huangyou
            EditorFont.Fang -> R.font.dotgothic16
            EditorFont.Cursive -> R.font.long_cang
            EditorFont.Wide -> R.font.liu_jian_mao_cao
            EditorFont.Rounded -> R.font.zcool_kuaile
            else -> return null
        }
        return runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
    }
}
data class EditorText(
    val text: String,
    val x: Float = .5f,
    val y: Float = .5f,
    val color: Int,
    val size: Float = .089f,
    val opacity: Float = 1f,
    val font: EditorFont = EditorFont.System,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val outline: Boolean = false,
    val strokeColor: Int = android.graphics.Color.BLACK,
    val strokeWidth: Float = .05f,
    val strokeOpacity: Float = 1f,
    val background: Boolean = false,
    val backgroundColor: Int = android.graphics.Color.BLACK,
    val backgroundOpacity: Float = .55f,
    val backgroundPadding: Float = .022f,
    val backgroundRadius: Float = .01f,
    val shadowEnabled: Boolean = false,
    val shadowColor: Int = android.graphics.Color.BLACK,
    val shadowBlur: Float = .022f,
    val shadowDistance: Float = .014f,
    val shadowOpacity: Float = .55f,
    val align: EditorTextAlign = EditorTextAlign.Center,
    val vertical: Boolean = false,
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 1f,
    val boxScale: Float = 1f,
    val boxWidth: Float = .5f,
    val boxHeight: Float = .2f
)

enum class CropPreset(val label: String, val ratio: Float?) {
    Original("原图", null), Free("自由", null), Device("本机", 9f / 16f), Custom("自定义", null),
    Square("1:1", 1f), ThreeTwo("3:2", 3f / 2f), TwoThree("2:3", 2f / 3f),
    FourThree("4:3", 4f / 3f), ThreeFour("3:4", 3f / 4f), FiveFour("5:4", 5f / 4f),
    FourFive("4:5", 4f / 5f), SixteenNine("16:9", 16f / 9f), NineSixteen("9:16", 9f / 16f)
}

enum class EditorFilter(val label: String) {
    Original("原图"), Vivid("鲜艳"), Warm("暖色"), Cool("冷色"), Mono("黑白"), Film("胶片")
}

data class ImageEditState(
    val rotation: Int = 0,
    val straighten: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: CropPreset = CropPreset.Free,
    val customCropRatio: Float? = null,
    val cropRect: NormalizedRect = NormalizedRect(),
    val composeScale: Float = 1f,
    val composeX: Float = 0f,
    val composeY: Float = 0f,
    val filter: EditorFilter = EditorFilter.Original,
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val tint: Float = 0f,
    val temperature: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val saturation: Float = 1f,
    val vibrance: Float = 0f,
    val fade: Float = 0f,
    val sharpness: Float = 0f,
    val enhance: Float = 0f,
    val strokes: List<EditorStroke> = emptyList(),
    val texts: List<EditorText> = listOf(EditorText("", color = android.graphics.Color.WHITE))
)

private fun centeredFrameForRatio(imageRatio: Float, frameRatio: Float): NormalizedRect {
    if (imageRatio <= 0f || frameRatio <= 0f) return NormalizedRect()
    val width = if (imageRatio > frameRatio) frameRatio / imageRatio else 1f
    val height = if (imageRatio > frameRatio) 1f else imageRatio / frameRatio
    return NormalizedRect(
        left = (1f - width) / 2f,
        top = (1f - height) / 2f,
        right = (1f + width) / 2f,
        bottom = (1f + height) / 2f
    )
}

private fun cropFrameFor(width: Int, height: Int, state: ImageEditState): NormalizedRect {
    if (state.crop == CropPreset.Free) return state.cropRect
    val ratio = when (state.crop) {
        CropPreset.Custom -> state.customCropRatio
        else -> state.crop.ratio
    }?.takeIf { it > 0f } ?: (width.toFloat() / height.coerceAtLeast(1))
    return if (state.cropRect != NormalizedRect()) state.cropRect
    else centeredFrameForRatio(width.toFloat() / height.coerceAtLeast(1), ratio)
}

internal fun editorOutputDimensions(
    sourceWidth: Int,
    sourceHeight: Int,
    state: ImageEditState,
    outputScale: Float
): Pair<Int, Int> {
    val rotated = state.rotation % 180 != 0
    val referenceWidth = if (rotated) sourceHeight else sourceWidth
    val referenceHeight = if (rotated) sourceWidth else sourceHeight
    val straighten = Math.toRadians(state.straighten.toDouble())
    val rotatedWidth = abs(referenceWidth * cos(straighten) + referenceHeight * sin(straighten)).toFloat()
    val rotatedHeight = abs(referenceWidth * sin(straighten) + referenceHeight * cos(straighten)).toFloat()
    var cropWidth = rotatedWidth
    var cropHeight = rotatedHeight
    if (state.crop != CropPreset.Original) {
        val frame = cropFrameFor(referenceWidth, referenceHeight, state)
        val zoom = state.composeScale.coerceIn(1f, 4f)
        val cosine = abs(cos(straighten)).toFloat()
        val sine = abs(sin(straighten)).toFloat()
        val frameWidth = referenceWidth * frame.width
        val frameHeight = referenceHeight * frame.height
        val safeFrameScale = minOf(
            1f,
            referenceWidth * zoom / (frameWidth * cosine + frameHeight * sine).coerceAtLeast(.0001f),
            referenceHeight * zoom / (frameWidth * sine + frameHeight * cosine).coerceAtLeast(.0001f)
        ).coerceIn(.01f, 1f)
        cropWidth = referenceWidth * frame.width * safeFrameScale / zoom
        cropHeight = referenceHeight * frame.height * safeFrameScale / zoom
    }
    val scale = outputScale.coerceIn(.01f, 1f)
    // Use nearest-pixel sizing here. Truncating floating point geometry makes
    // square presets occasionally export as (n, n - 1) after rotation.
    return (cropWidth * scale).roundToInt().coerceAtLeast(1) to
        (cropHeight * scale).roundToInt().coerceAtLeast(1)
}

internal fun editorExportSampleSize(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (
        max(width, height) / sample > 6000 ||
        width.toLong() * height / (sample.toLong() * sample) > 12_000_000L
    ) sample *= 2
    return sample
}

suspend fun loadEditorBitmap(context: Context, item: MediaItem): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openMediaInputStream(context, item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        openMediaInputStream(context, item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}

fun geometryBitmap(source: Bitmap, state: ImageEditState): Bitmap {
    val matrix = Matrix().apply {
        postScale(if (state.flipHorizontal) -1f else 1f, if (state.flipVertical) -1f else 1f)
        postRotate(state.rotation + state.straighten)
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

fun croppedGeometryBitmap(
    bitmap: Bitmap,
    state: ImageEditState,
    referenceWidth: Int = bitmap.width,
    referenceHeight: Int = bitmap.height
): Bitmap = cropBitmap(bitmap, state, referenceWidth, referenceHeight)

suspend fun saveEditedBitmap(
    context: Context,
    source: Bitmap,
    sourceItem: MediaItem,
    state: ImageEditState,
    outputScale: Float,
    quality: Int,
    replaceOriginal: Boolean = false
): android.net.Uri? = withContext(Dispatchers.IO) {
    val exportSource = loadExportBitmap(context, sourceItem) ?: source
    val referenceGeometry = geometryBitmap(exportSource, state.copy(straighten = 0f))
    var working = geometryBitmap(exportSource, state)
    if (working !== exportSource && exportSource !== source) exportSource.recycle()
    applyAdjustments(working, state).also { adjusted ->
        if (adjusted !== working && working !== source) working.recycle()
        working = adjusted
    }
    cropBitmap(working, state, referenceGeometry.width, referenceGeometry.height).also { cropped ->
        if (cropped !== working && working !== source) working.recycle()
        working = cropped
    }
    if (referenceGeometry !== source && !referenceGeometry.isRecycled) referenceGeometry.recycle()
    if (outputScale < .999f) {
        val width = max(1, (working.width * outputScale).toInt())
        val height = max(1, (working.height * outputScale).toInt())
        Bitmap.createScaledBitmap(working, width, height, true).also { scaled ->
            if (scaled !== working && working !== source) working.recycle()
            working = scaled
        }
    }
    drawOverlays(working, state)

    val stem = sourceItem.name.substringBeforeLast('.', sourceItem.name).ifBlank { "图片" }
    val displayName = "${stem}_编辑_${System.currentTimeMillis()}.jpg"
    val resolver = context.contentResolver
    val preserveDate = context.getSharedPreferences("album_settings", Context.MODE_PRIVATE).getBoolean("preserve_date", true)
    if (replaceOriginal) {
        val format = when {
            sourceItem.mimeType.contains("png", true) -> Bitmap.CompressFormat.PNG
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && sourceItem.mimeType.contains("webp", true) -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.JPEG
        }
        val encoded = File.createTempFile("album-edited-", ".tmp", context.cacheDir)
        val encodedReady = runCatching {
            encoded.outputStream().use { output -> check(working.compress(format, quality, output)) { "图片编码失败" } }
            encoded.length() > 0L
        }.getOrDefault(false)
        if (!encodedReady) {
            encoded.delete()
            if (working !== source && !working.isRecycled) working.recycle()
            return@withContext null
        }
        val backup = File.createTempFile("album-original-", ".bak", context.cacheDir)
        val backedUp = runCatching {
            openMediaInputStream(context, sourceItem.uri).use { input ->
                requireNotNull(input)
                backup.outputStream().use(input::copyTo)
            }
            true
        }.getOrDefault(false)
        val result = if (!backedUp) null else runCatching {
            openMediaOutputStream(context, sourceItem.uri, "wt")?.use { output ->
                encoded.inputStream().use { input -> input.copyTo(output) }
            } ?: error("无法写入原文件")
            if (preserveDate && sourceItem.dateTaken > 0L) {
                if (sourceItem.uri.scheme == "file") {
                    sourceItem.uri.path?.let { File(it).setLastModified(sourceItem.dateTaken) }
                } else {
                    resolver.update(sourceItem.uri, ContentValues().apply {
                        put(MediaStore.Images.Media.DATE_TAKEN, sourceItem.dateTaken)
                        put(MediaStore.Images.Media.DATE_MODIFIED, sourceItem.dateTaken / 1000L)
                    }, null, null)
                }
            }
            sourceItem.uri
        }.getOrElse {
            runCatching {
                openMediaOutputStream(context, sourceItem.uri, "wt")?.use { output -> backup.inputStream().use { it.copyTo(output) } }
            }
            null
        }
        encoded.delete()
        backup.delete()
        if (working !== source && !working.isRecycled) working.recycle()
        return@withContext result
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (preserveDate && sourceItem.dateTaken > 0L) {
            put(MediaStore.Images.Media.DATE_TAKEN, sourceItem.dateTaken)
            put(MediaStore.Images.Media.DATE_MODIFIED, sourceItem.dateTaken / 1000L)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/相册/已编辑")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "相册/已编辑")
            directory.mkdirs()
            put(MediaStore.Images.Media.DATA, File(directory, displayName).absolutePath)
        }
    }
    val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
    runCatching {
        resolver.openOutputStream(target)?.use { output ->
            check(working.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "图片编码失败" }
        } ?: error("无法打开输出文件")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(target, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
        target
    }.getOrElse {
        resolver.delete(target, null, null)
        null
    }
        .also { if (working !== source && !working.isRecycled) working.recycle() }
}

private fun loadExportBitmap(context: Context, item: MediaItem): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openMediaInputStream(context, item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val sample = editorExportSampleSize(bounds.outWidth, bounds.outHeight)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    openMediaInputStream(context, item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}.getOrNull()

private fun applyAdjustments(bitmap: Bitmap, state: ImageEditState): Bitmap {
    val parameters = editorAdjustmentParameters(state)
    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    Canvas(output).drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(editorColorMatrix(state))
    })
    sharpenInPlace(output, parameters.sharpenStrength)
    return output
}

internal data class EditorAdjustmentParameters(
    val saturation: Float,
    val gain: Float,
    val redOffset: Float,
    val greenOffset: Float,
    val blueOffset: Float,
    val sharpenStrength: Float
)

internal fun editorAdjustmentParameters(state: ImageEditState): EditorAdjustmentParameters {
    val saturation = (state.saturation * (1f + state.vibrance * .75f + state.enhance * .30f)).coerceIn(0f, 3.2f)
    val exposureGain = 2.0.pow((state.exposure + state.enhance * .25f).toDouble()).toFloat()
    val contrast = (state.contrast * (1f + state.enhance * .28f) * (1f - state.fade * .45f)).coerceIn(.2f, 2.8f)
    val highlightGain = (1f + state.highlights * .45f).coerceIn(.6f, 1.5f)
    val gain = exposureGain * contrast * highlightGain
    val neutralOffset = (1f - contrast) * 128f + state.brightness * 140f + state.shadows * 72f + state.fade * 60f
    return EditorAdjustmentParameters(
        saturation = saturation,
        gain = gain,
        redOffset = neutralOffset + state.temperature * 72f + state.tint * 45f,
        greenOffset = neutralOffset - state.tint * 55f,
        blueOffset = neutralOffset - state.temperature * 72f + state.tint * 45f,
        sharpenStrength = (state.sharpness * .72f + state.enhance * .28f).coerceIn(0f, .9f)
    )
}

internal fun editorColorMatrix(state: ImageEditState): ColorMatrix {
    val parameters = editorAdjustmentParameters(state)
    val matrix = filterMatrix(state.filter)
    matrix.postConcat(ColorMatrix().apply { setSaturation(parameters.saturation) })
    matrix.postConcat(ColorMatrix(floatArrayOf(
        parameters.gain, 0f, 0f, 0f, parameters.redOffset,
        0f, parameters.gain, 0f, 0f, parameters.greenOffset,
        0f, 0f, parameters.gain, 0f, parameters.blueOffset,
        0f, 0f, 0f, 1f, 0f
    )))
    return matrix
}

private fun sharpenInPlace(bitmap: Bitmap, strength: Float) {
    if (strength <= .001f || bitmap.width < 3 || bitmap.height < 3) return
    val width = bitmap.width
    var previous = IntArray(width).also { bitmap.getPixels(it, 0, width, 0, 0, width, 1) }
    var current = IntArray(width).also { bitmap.getPixels(it, 0, width, 0, 1, width, 1) }
    var next = IntArray(width).also { bitmap.getPixels(it, 0, width, 0, 2, width, 1) }
    val sharpened = IntArray(width)
    for (y in 1 until bitmap.height - 1) {
        sharpened[0] = current[0]
        sharpened[width - 1] = current[width - 1]
        for (x in 1 until width - 1) {
            val center = current[x]
            val left = current[x - 1]
            val right = current[x + 1]
            val up = previous[x]
            val down = next[x]
            fun channel(shift: Int): Int {
                val value = (center shr shift) and 0xff
                val neighbors = ((left shr shift) and 0xff) + ((right shr shift) and 0xff) +
                    ((up shr shift) and 0xff) + ((down shr shift) and 0xff)
                return (value + strength * (value * 4 - neighbors)).toInt().coerceIn(0, 255)
            }
            sharpened[x] = (center and -0x1000000) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
        bitmap.setPixels(sharpened, 0, width, 0, y, width, 1)
        val recycled = previous
        previous = current
        current = next
        next = recycled
        if (y + 2 < bitmap.height) bitmap.getPixels(next, 0, width, 0, y + 2, width, 1)
    }
}

private fun cropBitmap(
    bitmap: Bitmap,
    state: ImageEditState,
    referenceWidth: Int = bitmap.width,
    referenceHeight: Int = bitmap.height
): Bitmap {
    if (state.crop == CropPreset.Original) return bitmap
    val refWidth = referenceWidth.coerceAtLeast(1)
    val refHeight = referenceHeight.coerceAtLeast(1)
    val frame = cropFrameFor(refWidth, refHeight, state)
    val zoom = state.composeScale.coerceIn(1f, 4f)
    val angle = Math.toRadians(state.straighten.toDouble())
    val cosine = abs(cos(angle)).toFloat()
    val sine = abs(sin(angle)).toFloat()
    val frameWidth = refWidth * frame.width
    val frameHeight = refHeight * frame.height
    val imageWidth = refWidth * zoom
    val imageHeight = refHeight * zoom
    val safeFrameScale = minOf(
        1f,
        imageWidth / (frameWidth * cosine + frameHeight * sine).coerceAtLeast(.0001f),
        imageHeight / (frameWidth * sine + frameHeight * cosine).coerceAtLeast(.0001f)
    ).coerceIn(.01f, 1f)
    val visibleLeft = .5f + (frame.left - .5f) * safeFrameScale
    val visibleTop = .5f + (frame.top - .5f) * safeFrameScale
    val visibleRight = .5f + (frame.right - .5f) * safeFrameScale
    val visibleBottom = .5f + (frame.bottom - .5f) * safeFrameScale
    val panX = state.composeX.coerceIn(-1f, 1f) * (zoom - 1f) / (2f * zoom)
    val panY = state.composeY.coerceIn(-1f, 1f) * (zoom - 1f) / (2f * zoom)
    val leftNorm = (visibleLeft - .5f) / zoom + panX
    val topNorm = (visibleTop - .5f) / zoom + panY
    val rightNorm = (visibleRight - .5f) / zoom + panX
    val bottomNorm = (visibleBottom - .5f) / zoom + panY
    val left = (bitmap.width / 2f + leftNorm * refWidth).toInt().coerceIn(0, bitmap.width - 1)
    val top = (bitmap.height / 2f + topNorm * refHeight).toInt().coerceIn(0, bitmap.height - 1)
    val right = (bitmap.width / 2f + rightNorm * refWidth).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = (bitmap.height / 2f + bottomNorm * refHeight).toInt().coerceIn(top + 1, bitmap.height)
    val width = (right - left).coerceAtLeast(1)
    val height = (bottom - top).coerceAtLeast(1)
    if (left == 0 && top == 0 && width == bitmap.width && height == bitmap.height) return bitmap
    return Bitmap.createBitmap(bitmap, left, top, width, height)
}

private fun filterMatrix(filter: EditorFilter): ColorMatrix = when (filter) {
    EditorFilter.Original -> ColorMatrix()
    EditorFilter.Mono -> ColorMatrix().apply { setSaturation(0f) }
    EditorFilter.Vivid -> ColorMatrix().apply { setSaturation(1.35f) }
    EditorFilter.Warm -> ColorMatrix(floatArrayOf(
        1.08f, 0f, 0f, 0f, 7f, 0f, 1.01f, 0f, 0f, 2f, 0f, 0f, .88f, 0f, 0f, 0f, 0f, 0f, 1f, 0f
    ))
    EditorFilter.Cool -> ColorMatrix(floatArrayOf(
        .9f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 2f, 0f, 0f, 1.12f, 0f, 7f, 0f, 0f, 0f, 1f, 0f
    ))
    EditorFilter.Film -> ColorMatrix(floatArrayOf(
        1.06f, .03f, 0f, 0f, 4f, .02f, .98f, 0f, 0f, 1f, 0f, .02f, .84f, 0f, 3f, 0f, 0f, 0f, 1f, 0f
    ))
}

private fun drawOverlays(bitmap: Bitmap, state: ImageEditState) {
    drawDoodleOverlays(bitmap, state.strokes)
    val overlay = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(overlay)
    state.texts.forEach { text -> drawEditorTextOverlay(canvas, text, bitmap.width.toFloat(), bitmap.height.toFloat()) }
    Canvas(bitmap).drawBitmap(overlay, 0f, 0f, null)
    overlay.recycle()
}

internal fun renderDoodleComposite(base: Bitmap, strokes: List<EditorStroke>): Bitmap {
    val result = base.copy(Bitmap.Config.ARGB_8888, true)
    drawDoodleOverlays(result, strokes)
    return result
}

private fun drawDoodleOverlays(bitmap: Bitmap, strokes: List<EditorStroke>) {
    val overlay = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(overlay)
    val shortSide = min(bitmap.width, bitmap.height).toFloat()
    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        if (stroke.brush == EditorBrush.Mosaic) {
            val sample = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(sample).apply {
                drawBitmap(bitmap, 0f, 0f, null)
                drawBitmap(overlay, 0f, 0f, null)
            }
            drawMosaicStroke(sample, canvas, stroke, shortSide)
            sample.recycle()
            return@forEach
        }
        val path = Path().apply {
            moveTo(stroke.points.first().x * bitmap.width, stroke.points.first().y * bitmap.height)
            stroke.points.drop(1).forEach { lineTo(it.x * bitmap.width, it.y * bitmap.height) }
        }
        if (stroke.brush == EditorBrush.Eraser) {
            val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = stroke.width * shortSide * 1.8f
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            canvas.drawPath(path, eraserPaint)
            return@forEach
        }
        val baseWidth = stroke.width * shortSide
        if (stroke.brush == EditorBrush.Spray) {
            drawSprayStroke(canvas, stroke, bitmap.width.toFloat(), bitmap.height.toFloat(), baseWidth)
            return@forEach
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            style = Paint.Style.STROKE
            strokeCap = when (stroke.brush) {
                EditorBrush.Marker, EditorBrush.Highlighter, EditorBrush.Fountain -> Paint.Cap.SQUARE
                else -> Paint.Cap.ROUND
            }
            strokeJoin = Paint.Join.ROUND
            strokeWidth = baseWidth * editorBrushWidth(stroke.brush)
            alpha = (editorBrushAlpha(stroke.brush) * 255).toInt()
            if (stroke.brush == EditorBrush.Dashed) {
                pathEffect = DashPathEffect(floatArrayOf(strokeWidth * 2.4f, strokeWidth * 1.7f), 0f)
            }
            if (stroke.brush == EditorBrush.Crayon) {
                pathEffect = DashPathEffect(floatArrayOf(baseWidth * .32f, baseWidth * .12f), 0f)
            }
            if (stroke.brush == EditorBrush.Neon) setShadowLayer(maxOf(6f, baseWidth * 1.2f), 0f, 0f, stroke.color)
        }
        canvas.drawPath(path, paint)
        if (stroke.brush == EditorBrush.Neon) {
            paint.clearShadowLayer()
            paint.color = android.graphics.Color.WHITE
            paint.alpha = (255 * .72f).toInt()
            paint.strokeWidth = maxOf(1f, baseWidth * .18f)
            paint.pathEffect = null
            canvas.drawPath(path, paint)
        }
    }
    Canvas(bitmap).drawBitmap(overlay, 0f, 0f, null)
    overlay.recycle()
}

private fun drawSprayStroke(
    canvas: Canvas,
    stroke: EditorStroke,
    bitmapWidth: Float,
    bitmapHeight: Float,
    baseWidth: Float
) {
    val radius = maxOf(4f, baseWidth * 1.15f)
    val dotCount = maxOf(8, (baseWidth * 1.4f).toInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = stroke.color
        alpha = (editorBrushAlpha(EditorBrush.Spray) * 255).toInt()
        style = Paint.Style.FILL
    }
    stroke.points.forEachIndexed { pointIndex, point ->
        repeat(dotCount) { dotIndex ->
            val seed = pointIndex * 131 + dotIndex * 37
            val angle = Math.toRadians((seed * 47 % 360).toDouble())
            val distance = radius * kotlin.math.sqrt(((seed * 17 % 1000) / 1000f).coerceIn(0f, 1f))
            canvas.drawCircle(
                point.x * bitmapWidth + kotlin.math.cos(angle).toFloat() * distance,
                point.y * bitmapHeight + kotlin.math.sin(angle).toFloat() * distance,
                1.4f,
                paint
            )
        }
    }
}

internal fun editorBrushWidth(brush: EditorBrush): Float = when (brush) {
    EditorBrush.Brush -> 1.35f
    EditorBrush.Marker -> 1.45f
    EditorBrush.Highlighter -> 2.25f
    EditorBrush.Pencil -> .42f
    EditorBrush.Fountain -> .62f
    EditorBrush.Crayon -> .9f
    EditorBrush.Neon -> .72f
    EditorBrush.Mosaic, EditorBrush.Eraser -> 1f
    else -> 1f
}

internal fun editorBrushAlpha(brush: EditorBrush): Float = when (brush) {
    EditorBrush.Brush -> .88f
    EditorBrush.Marker -> .82f
    EditorBrush.Highlighter -> .3f
    EditorBrush.Pencil -> .72f
    EditorBrush.Fountain -> 1f
    EditorBrush.Crayon -> .64f
    EditorBrush.Spray -> .55f
    else -> 1f
}

private fun drawMosaicStroke(bitmap: Bitmap, canvas: Canvas, stroke: EditorStroke, shortSide: Float) {
    val radius = (stroke.width * shortSide * 1.5f).toInt().coerceAtLeast(6)
    val block = (radius / 3).coerceIn(4, 28)
    val points = stroke.points
    if (points.isEmpty()) return
    fun stamp(point: NormalizedPoint) {
        val centerX = (point.x * bitmap.width).toInt()
        val centerY = (point.y * bitmap.height).toInt()
        var y = (centerY - radius).coerceAtLeast(0)
        while (y < (centerY + radius).coerceAtMost(bitmap.height)) {
            var x = (centerX - radius).coerceAtLeast(0)
            while (x < (centerX + radius).coerceAtMost(bitmap.width)) {
                val sampleX = (x + block / 2).coerceIn(0, bitmap.width - 1)
                val sampleY = (y + block / 2).coerceIn(0, bitmap.height - 1)
                canvas.drawRect(
                    x.toFloat(), y.toFloat(),
                    (x + block).coerceAtMost(bitmap.width).toFloat(),
                    (y + block).coerceAtMost(bitmap.height).toFloat(),
                    Paint().apply { color = bitmap.getPixel(sampleX, sampleY) }
                )
                x += block
            }
            y += block
        }
    }
    stamp(points.first())
    points.zipWithNext().forEach { (from, to) ->
        val distance = maxOf(abs(to.x - from.x) * bitmap.width, abs(to.y - from.y) * bitmap.height)
        val steps = (distance / (block * .55f)).toInt().coerceAtLeast(1)
        for (step in 1..steps) {
            val ratio = step / steps.toFloat()
            stamp(NormalizedPoint(from.x + (to.x - from.x) * ratio, from.y + (to.y - from.y) * ratio))
        }
    }
}

internal fun drawEditorTextOverlay(
    canvas: Canvas,
    overlay: EditorText,
    canvasWidth: Float,
    canvasHeight: Float,
    overrideX: Float? = null,
    overrideY: Float? = null
) {
    val shortSide = min(canvasWidth, canvasHeight)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = overlay.color
        alpha = (overlay.opacity.coerceIn(0f, 1f) * 255).toInt()
        textSize = overlay.size * overlay.boxScale.coerceIn(.35f, 3f) * shortSide
        textAlign = Paint.Align.LEFT
        typeface = editorTypeface(overlay.font, overlay.bold, overlay.italic)
        if (overlay.shadowEnabled) {
            setShadowLayer(
                overlay.shadowBlur.coerceIn(0f, .2f) * shortSide,
                overlay.shadowDistance.coerceIn(-.2f, .2f) * shortSide,
                overlay.shadowDistance.coerceIn(-.2f, .2f) * shortSide,
                android.graphics.Color.argb(
                    (overlay.shadowOpacity.coerceIn(0f, 1f) * 255).toInt(),
                    android.graphics.Color.red(overlay.shadowColor),
                    android.graphics.Color.green(overlay.shadowColor),
                    android.graphics.Color.blue(overlay.shadowColor)
                )
            )
        }
    }
    // HTML stores letter spacing in preview CSS pixels; convert it to the
    // exported image using the same 360px editor reference side.
    val spacing = overlay.letterSpacing * shortSide / 360f
    fun lineWidth(line: String, paint: Paint = textPaint): Float =
        line.sumOf { paint.measureText(it.toString()).toDouble() }.toFloat() + spacing * (line.length - 1).coerceAtLeast(0)
    val lines = if (overlay.vertical) {
        overlay.text.toList().map(Char::toString)
    } else {
        val maximumWidth = (overlay.boxWidth.coerceIn(.12f, .96f) * canvasWidth).coerceAtLeast(textPaint.textSize * 2f)
        overlay.text.split('\n').flatMap { rawLine ->
            if (rawLine.isEmpty()) return@flatMap listOf("")
            val wrapped = mutableListOf<String>()
            val current = StringBuilder()
            rawLine.forEach { character ->
                val candidate = current.toString() + character
                if (current.isNotEmpty() && lineWidth(candidate) > maximumWidth) {
                    wrapped += current.toString()
                    current.clear()
                }
                current.append(character)
            }
            wrapped + current.toString()
        }
    }
    val widths = lines.map(::lineWidth)
    val blockWidth = widths.maxOrNull() ?: 0f
    val lineHeight = textPaint.textSize * 1.2f * overlay.lineSpacing.coerceIn(.7f, 2.5f)
    val x = (overrideX ?: overlay.x) * canvasWidth
    val centerY = (overrideY ?: overlay.y) * canvasHeight
    val blockHeight = textPaint.descent() - textPaint.ascent() + (lines.size - 1).coerceAtLeast(0) * lineHeight
    val firstBaseline = centerY - blockHeight / 2f - textPaint.ascent()
    val boxWidth = (if (overlay.vertical) overlay.boxHeight else overlay.boxWidth).coerceIn(.12f, .96f) * canvasWidth
    val boxLeft = x - boxWidth / 2f
    val boxRight = x + boxWidth / 2f
    val blockLeft = when (overlay.align) {
        EditorTextAlign.Left -> boxLeft
        EditorTextAlign.Center -> x - blockWidth / 2f
        EditorTextAlign.Right -> boxRight - blockWidth
    }
    if (overlay.background) {
        val padding = overlay.backgroundPadding.coerceIn(0f, .2f) * shortSide
        val backgroundColor = android.graphics.Color.argb(
            (overlay.backgroundOpacity.coerceIn(0f, 1f) * 255).toInt(),
            android.graphics.Color.red(overlay.backgroundColor),
            android.graphics.Color.green(overlay.backgroundColor),
            android.graphics.Color.blue(overlay.backgroundColor)
        )
        canvas.drawRoundRect(
            blockLeft - padding,
            firstBaseline + textPaint.ascent() - padding,
            blockLeft + blockWidth + padding,
            firstBaseline + (lines.size - 1).coerceAtLeast(0) * lineHeight + textPaint.descent() + padding,
            overlay.backgroundRadius.coerceIn(0f, .2f) * shortSide,
            overlay.backgroundRadius.coerceIn(0f, .2f) * shortSide,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
        )
    }
    fun drawRun(line: String, baseline: Float, paint: Paint) {
        val width = lineWidth(line, paint)
        var cursor = when (overlay.align) {
            EditorTextAlign.Left -> boxLeft
            EditorTextAlign.Center -> x - width / 2f
            EditorTextAlign.Right -> boxRight - width
        }
        line.forEach { character ->
            val glyph = character.toString()
            canvas.drawText(glyph, cursor, baseline, paint)
            cursor += paint.measureText(glyph) + spacing
        }
    }
    lines.forEachIndexed { index, line ->
        val baseline = firstBaseline + index * lineHeight
        if (overlay.outline) {
            drawRun(line, baseline, Paint(textPaint).apply {
                color = overlay.strokeColor
                alpha = (overlay.strokeOpacity.coerceIn(0f, 1f) * 255).toInt()
                style = Paint.Style.STROKE
                strokeWidth = textPaint.textSize * overlay.strokeWidth.coerceIn(.01f, .3f)
                clearShadowLayer()
            })
        }
        drawRun(line, baseline, textPaint)
    }
}

private fun editorTypeface(font: EditorFont, bold: Boolean, italic: Boolean): android.graphics.Typeface {
    val family = EditorTypefaceRegistry.resolve(font) ?: when (font) {
        EditorFont.System -> android.graphics.Typeface.SANS_SERIF
        EditorFont.Serif -> android.graphics.Typeface.SERIF
        EditorFont.Monospace -> android.graphics.Typeface.MONOSPACE
        EditorFont.Kai -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
        EditorFont.Song -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
        EditorFont.Hei -> android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
        EditorFont.Fang -> android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)
        EditorFont.Cursive -> android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
        EditorFont.Wide -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
        EditorFont.Rounded -> android.graphics.Typeface.create("sans-serif-rounded", android.graphics.Typeface.NORMAL)
    }
    val style = when {
        bold && italic -> android.graphics.Typeface.BOLD_ITALIC
        bold -> android.graphics.Typeface.BOLD
        italic -> android.graphics.Typeface.ITALIC
        else -> android.graphics.Typeface.NORMAL
    }
    return android.graphics.Typeface.create(family, style)
}

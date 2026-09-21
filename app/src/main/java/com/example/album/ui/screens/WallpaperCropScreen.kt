package com.example.album.ui.screens

import androidx.activity.compose.BackHandler
import android.app.WallpaperManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import com.example.album.data.MediaItem
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.editor.ImageEditState
import com.example.album.ui.editor.NormalizedRect
import com.example.album.ui.editor.cropWallpaperBitmap
import com.example.album.ui.editor.geometryBitmap
import com.example.album.ui.editor.loadWallpaperBitmap
import com.example.album.ui.editor.EditorRuler
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Height reserved for the angle ruler, so it never covers the preview. */
private val AngleBarHeight = 104.dp

@Composable
fun WallpaperCropScreen(
    item: MediaItem,
    onDismiss: () -> Unit,
    onConfirm: (android.graphics.Bitmap) -> Unit
) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val configuration = LocalConfiguration.current
    val singleScreenRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.coerceAtLeast(1)
    val multiScreenRatio = remember(context, configuration) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val desiredWidth = wallpaperManager.desiredMinimumWidth
        val desiredHeight = wallpaperManager.desiredMinimumHeight
        val desiredRatio = if (desiredWidth > 0 && desiredHeight > 0) {
            desiredWidth.toFloat() / desiredHeight.toFloat()
        } else 0f
        desiredRatio.takeIf { it > singleScreenRatio * 1.05f } ?: (singleScreenRatio * 2f)
    }
    var multiScreen by remember { mutableStateOf(false) }
    // Use the editor's geometry state so angle preview and export follow one
    // implementation.
    var editState by remember(item.uri) { mutableStateOf(ImageEditState()) }
    var bitmap by remember(item.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loading by remember(item.uri) { mutableStateOf(true) }
    // The crop frame the user asked for, in image coordinates. The tilt is a
    // preview transform: it only shrinks what is drawn, so the chosen size has
    // to stay here untouched and come back when the angle returns to zero.
    // null means "the centred default for the current screen ratio".
    var requestedFrame by remember(item.uri) { mutableStateOf<NormalizedRect?>(null) }
    val rotation = editState.rotation
    val straighten = editState.straighten

    LaunchedEffect(item.uri) {
        loading = true
        bitmap = loadWallpaperBitmap(context, item)
        loading = false
    }
    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(58.dp)
                .background(Color.Black.copy(alpha = .92f)).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, appText("取消", english), tint = Color.White)
            }
            IconButton(onClick = {
                multiScreen = false
                editState = ImageEditState()
                requestedFrame = null
            }) {
                Icon(Icons.Outlined.Restore, appText("重置修改", english), tint = Color.White)
            }
            IconButton(onClick = {
                editState = editState.copy(rotation = (rotation + 270) % 360)
                requestedFrame = null
            }) {
                Icon(Icons.Outlined.RotateLeft, appText("左转", english), tint = Color.White)
            }
            IconButton(onClick = {
                multiScreen = !multiScreen
                requestedFrame = null
            }) {
                Icon(Icons.Outlined.ViewWeek, if (multiScreen) appText("切换为单屏宽度", english) else appText("切换为多屏宽度", english), tint = Color.White)
            }
            IconButton(onClick = {
                editState = editState.copy(rotation = (rotation + 90) % 360)
                requestedFrame = null
            }) {
                Icon(Icons.Outlined.RotateRight, appText("右转", english), tint = Color.White)
            }
            IconButton(onClick = {
                val source = bitmap ?: return@IconButton
                val ratio = if (multiScreen) multiScreenRatio else singleScreenRatio.coerceAtLeast(.01f)
                val imageRatio = if (rotation % 180 == 0) {
                    source.width.toFloat() / source.height.coerceAtLeast(1)
                } else {
                    source.height.toFloat() / source.width.coerceAtLeast(1)
                }
                val selected = requestedFrame ?: centeredFrame(imageRatio, ratio)
                onConfirm(cropWallpaperBitmap(source, selected, ratio, rotation, straighten.safeWallpaperAngle()))
            }) {
                Icon(Icons.Outlined.Check, appText("使用裁剪区域", english), tint = Color.White)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        } else {
            val source = bitmap
            if (source == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, appText("无法读取图片", english), tint = Color.White, modifier = Modifier.size(42.dp))
                }
            } else {
                val editorGeometry = remember(source, rotation) {
                    geometryBitmap(source, editState.copy(straighten = 0f))
                }
                androidx.compose.runtime.DisposableEffect(editorGeometry) {
                    onDispose {
                        if (editorGeometry !== source && !editorGeometry.isRecycled) editorGeometry.recycle()
                    }
                }
                // The angle bar sits at the bottom of the screen, so the
                // preview has to reserve its height; otherwise the bar covers
                // the bottom of the picture.
                BoxWithConstraints(
                    Modifier.fillMaxSize().statusBarsPadding()
                        .padding(top = 58.dp, bottom = AngleBarHeight + 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val imageRatio = (editorGeometry.width.toFloat() / editorGeometry.height.coerceAtLeast(1))
                        .takeIf { it.isFinite() && it > 0f } ?: 1f
                    val width = min(maxWidth.value, maxHeight.value * imageRatio).coerceAtLeast(1f).dp
                    val height = (width.value / imageRatio).coerceAtLeast(1f).dp
                    val ratio = if (multiScreen) multiScreenRatio else singleScreenRatio.coerceAtLeast(.01f)
                    val initial = centeredFrame(imageRatio, ratio)
                    val safeStraighten = straighten.safeWallpaperAngle()
                    // The frame the user asked for is never overwritten by the
                    // angle preview: tilting only shrinks what is drawn, so
                    // rotating back restores the chosen size instead of leaving
                    // the squeezed one behind.
                    val requested = requestedFrame ?: initial
                    val displayFrame = wallpaperPreviewFrame(requested, safeStraighten, imageRatio)
                    val safeFrameScale = rotatedWallpaperPreviewScale(requested, safeStraighten, imageRatio)
                    val currentFrame by rememberUpdatedState(displayFrame)
                    val currentScale by rememberUpdatedState(safeFrameScale)
                    // The gesture layer covers the whole preview, not just the
                    // picture: touching the black bars above or below the image
                    // used to do nothing at all. It only moves the frame (the
                    // resize handles live on the frame itself), which is what a
                    // drag outside the picture means.
                    val imageSizePx = with(LocalDensity.current) {
                        androidx.compose.ui.geometry.Size(width.value.dp.toPx(), height.value.dp.toPx())
                    }
                    val imageLeftPx = with(LocalDensity.current) { ((maxWidth - width) / 2).toPx() }
                    val imageTopPx = with(LocalDensity.current) { ((maxHeight - height) / 2).toPx() }
                    val bandOuterPx = with(LocalDensity.current) { 48.dp.toPx() }
                    val bandInnerPx = with(LocalDensity.current) { 24.dp.toPx() }
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            var handle = -1
                            var working = displayFrame
                            fun shown(value: NormalizedRect) = NormalizedRect(
                                left = .5f + (value.left - .5f) * currentScale,
                                top = .5f + (value.top - .5f) * currentScale,
                                right = .5f + (value.right - .5f) * currentScale,
                                bottom = .5f + (value.bottom - .5f) * currentScale
                            )
                            detectDragGestures(
                                onDragStart = { point ->
                                    working = currentFrame
                                    // The same corner/edge bands the frame draws,
                                    // measured in preview pixels, so a drag that
                                    // starts in the black bar just outside the
                                    // frame still grabs the handle. One shared
                                    // hit test keeps the black bars and the frame
                                    // from disagreeing about what was grabbed.
                                    val w = imageSizePx.width.coerceAtLeast(1f)
                                    val h = imageSizePx.height.coerceAtLeast(1f)
                                    val visible = shown(working)
                                    val left = imageLeftPx + visible.left * w
                                    val top = imageTopPx + visible.top * h
                                    val right = imageLeftPx + visible.right * w
                                    val bottom = imageTopPx + visible.bottom * h
                                    // A touch inside the frame is handled by the
                                    // frame itself; this layer only ever resizes
                                    // (it is what makes the black bars work).
                                    val grabbed = cropHandleAt(
                                        frameLeft = left,
                                        frameTop = top,
                                        frameRight = right,
                                        frameBottom = bottom,
                                        pointX = point.x,
                                        pointY = point.y,
                                        outwardX = bandOuterPx,
                                        outwardY = bandOuterPx,
                                        inwardX = bandInnerPx,
                                        inwardY = bandInnerPx
                                    )
                                    handle = if (grabbed == CROP_HANDLE_MOVE) CROP_HANDLE_NONE else grabbed
                                },
                                onDragEnd = { handle = -1 },
                                onDragCancel = { handle = -1 },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val w = imageSizePx.width.coerceAtLeast(1f)
                                    val h = imageSizePx.height.coerceAtLeast(1f)
                                    val x = ((change.position.x - imageLeftPx) / w)
                                        .let { .5f + (it - .5f) / currentScale }.coerceIn(0f, 1f)
                                    val y = ((change.position.y - imageTopPx) / h)
                                        .let { .5f + (it - .5f) / currentScale }.coerceIn(0f, 1f)
                                    val candidate = if (handle >= 0) {
                                        resizeFrameFromPointer(working, handle, x, y, ratio / imageRatio, .06f)
                                    } else {
                                        val dx = amount.x / w / currentScale
                                        val dy = amount.y / h / currentScale
                                        NormalizedRect(
                                            working.left + dx,
                                            working.top + dy,
                                            working.right + dx,
                                            working.bottom + dy
                                        )
                                    }
                                    working = constrainWallpaperFrameToImage(candidate)
                                    requestedFrame = working
                                }
                            )
                        }
                    )
                    Box(Modifier.width(width).height(height).clipToBounds()) {
                        Image(
                            editorGeometry.asImageBitmap(),
                            null,
                            Modifier
                                .fillMaxSize()
                                // Match the editor: the fine angle is a live
                                // layer transform, not a second bitmap edit.
                                .graphicsLayer { rotationZ = straighten },
                            contentScale = ContentScale.Crop
                        )
                        CropFrame(
                            frame = displayFrame,
                            normalizedRatio = ratio / imageRatio,
                            visibleScale = safeFrameScale,
                            straighten = safeStraighten,
                            onFrameChange = { requestedFrame = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        if (!loading && bitmap != null) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = .86f))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (english) "Angle: ${straighten.safeWallpaperAngle().toInt()}°" else "角度：${straighten.safeWallpaperAngle().toInt()}°",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                EditorRuler(
                    value = straighten.safeWallpaperAngle(),
                    valueRange = -45f..45f,
                    onValueChange = { editState = editState.copy(straighten = it.safeWallpaperAngle()) },
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    tickSpacing = 5.dp,
                    edgeInset = 14.dp
                )
            }
        }
    }
}

private fun centeredFrame(imageRatio: Float, frameRatio: Float): NormalizedRect {
    val safeImageRatio = imageRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeFrameRatio = frameRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val width = if (safeImageRatio > safeFrameRatio) safeFrameRatio / safeImageRatio else 1f
    val height = if (safeImageRatio > safeFrameRatio) 1f else safeImageRatio / safeFrameRatio
    return NormalizedRect((1f - width) / 2f, (1f - height) / 2f, (1f + width) / 2f, (1f + height) / 2f)
}

private fun Float.safeWallpaperAngle(): Float =
    takeIf { it.isFinite() }?.coerceIn(-45f, 45f) ?: 0f

/** Keeps the displayed, rotated crop frame inside the source image. */
internal fun constrainWallpaperFrameToRotatedBounds(
    frame: NormalizedRect,
    straighten: Float,
    visibleScale: Float = 1f
): NormalizedRect {
    val scale = visibleScale.takeIf { it.isFinite() }?.coerceIn(.01f, 1f) ?: 1f
    val safeStraighten = straighten.safeWallpaperAngle()
    val cosine = abs(cos(Math.toRadians(safeStraighten.toDouble()))).toFloat()
    val sine = abs(sin(Math.toRadians(safeStraighten.toDouble()))).toFloat()
    val left = frame.left.takeIf { it.isFinite() } ?: 0f
    val top = frame.top.takeIf { it.isFinite() } ?: 0f
    val right = frame.right.takeIf { it.isFinite() } ?: 1f
    val bottom = frame.bottom.takeIf { it.isFinite() } ?: 1f
    var width = (right - left).takeIf { it.isFinite() }?.coerceIn(.001f, 1f) ?: 1f
    var height = (bottom - top).takeIf { it.isFinite() }?.coerceIn(.001f, 1f) ?: 1f

    // A caller may provide a frame from before the angle changed. Shrink it
    // only when even its rotated bounding box cannot fit; normal angle
    // changes preserve the user's crop size because visibleScale already
    // handles the preview inset.
    val rotatedWidth = scale * (width * cosine + height * sine)
    val rotatedHeight = scale * (width * sine + height * cosine)
    val dimensionScale = minOf(
        1f,
        1f / rotatedWidth.coerceAtLeast(.0001f),
        1f / rotatedHeight.coerceAtLeast(.0001f)
    )
    width *= dimensionScale
    height *= dimensionScale

    val halfRotatedWidth = scale * (width * cosine + height * sine) / 2f
    val halfRotatedHeight = scale * (width * sine + height * cosine) / 2f
    val halfWidth = width / 2f
    val halfHeight = height / 2f
    val displayCenterMinX = .5f + (halfRotatedWidth - .5f) / scale
    val displayCenterMaxX = .5f + (.5f - halfRotatedWidth) / scale
    val displayCenterMinY = .5f + (halfRotatedHeight - .5f) / scale
    val displayCenterMaxY = .5f + (.5f - halfRotatedHeight) / scale
    val requestedCenterX = (left + right) / 2f
    val requestedCenterY = (top + bottom) / 2f
    val minCenterX = maxOf(halfWidth, displayCenterMinX)
    val maxCenterX = minOf(1f - halfWidth, displayCenterMaxX)
    val minCenterY = maxOf(halfHeight, displayCenterMinY)
    val maxCenterY = minOf(1f - halfHeight, displayCenterMaxY)
    // A very wide/tall frame can make the two constraints meet in reverse due
    // to rounding. coerceIn throws for that case, so use the image center.
    val centerX = if (minCenterX.isFinite() && maxCenterX.isFinite() && minCenterX <= maxCenterX) {
        requestedCenterX.coerceIn(minCenterX, maxCenterX)
    } else {
        .5f
    }
    val centerY = if (minCenterY.isFinite() && maxCenterY.isFinite() && minCenterY <= maxCenterY) {
        requestedCenterY.coerceIn(minCenterY, maxCenterY)
    } else {
        .5f
    }
    return NormalizedRect(
        centerX - halfWidth,
        centerY - halfHeight,
        centerX + halfWidth,
        centerY + halfHeight
    )
}

/**
 * Preview inset that keeps [frame] inside the picture while it is shown at
 * [straighten] degrees: what is drawn is scaled by this factor about the image
 * centre, the same way the editor's crop step insets the frame it exports.
 */
internal fun rotatedWallpaperPreviewScale(
    frame: NormalizedRect,
    straighten: Float,
    imageAspectRatio: Float
): Float {
    val ratio = imageAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val width = frame.width.takeIf { it.isFinite() }?.coerceIn(.001f, 1f) ?: 1f
    val height = frame.height.takeIf { it.isFinite() }?.coerceIn(.001f, 1f) ?: 1f
    val angle = Math.toRadians(straighten.safeWallpaperAngle().toDouble())
    val cosine = abs(cos(angle)).toFloat()
    val sine = abs(sin(angle)).toFloat()
    return minOf(
        1f,
        1f / (width * cosine + height * sine / ratio).coerceAtLeast(.0001f),
        1f / (width * sine * ratio + height * cosine).coerceAtLeast(.0001f)
    ).coerceIn(.01f, 1f)
}

/**
 * What the crop preview draws for the frame the user asked for: the requested
 * frame is only inset, never rewritten. That is what makes a chosen size come
 * back when the angle returns to zero, instead of staying squeezed forever.
 */
internal fun wallpaperPreviewFrame(
    requested: NormalizedRect,
    straighten: Float,
    imageAspectRatio: Float
): NormalizedRect = constrainWallpaperFrameToRotatedBounds(
    requested,
    straighten,
    rotatedWallpaperPreviewScale(requested, straighten, imageAspectRatio)
)

/**
 * Keeps a hand-dragged frame inside the picture. The tilt never limits the
 * stored frame: it is handled by [rotatedWallpaperPreviewScale] when the frame
 * is drawn (and again when it is exported), so a drag made while the picture is
 * tilted cannot quietly shrink the crop the user chose.
 */
internal fun constrainWallpaperFrameToImage(frame: NormalizedRect): NormalizedRect =
    constrainWallpaperFrameToRotatedBounds(frame, 0f, 1f)

/** Nothing close enough to grab. */
internal const val CROP_HANDLE_NONE = -1

/** The frame body: a drag moves the whole crop frame. */
internal const val CROP_HANDLE_MOVE = 8

/**
 * Decides what a drag on the crop frame grabs. The frame and the point share
 * one coordinate space (pixels in the gesture layer that covers the black bars,
 * normalized image units inside `CropFrame`); [outwardX]/[outwardY] are how far
 * *outside* the frame the finger may land and still grab a handle,
 * [inwardX]/[inwardY] how far inside.
 *
 * The inward band is deliberately the smaller one: the finger usually lands
 * just outside the frame, and a symmetric band turned a small frame into almost
 * nothing but resize zones, which made moving it hard.
 *
 * Returns the corners 0..3 (left-top, right-top, left-bottom, right-bottom),
 * the edges 4..7 (top, bottom, left, right), [CROP_HANDLE_MOVE] for the frame
 * body, or [CROP_HANDLE_NONE] when the touch is too far away.
 */
internal fun cropHandleAt(
    frameLeft: Float,
    frameTop: Float,
    frameRight: Float,
    frameBottom: Float,
    pointX: Float,
    pointY: Float,
    outwardX: Float,
    outwardY: Float,
    inwardX: Float,
    inwardY: Float
): Int {
    // Positive means "outside the frame"; the sign has to be consistent with
    // the band test below, otherwise the inward limit is unreachable and the
    // band stays just as wide inside the frame.
    fun inBand(outward: Float, outwardLimit: Float, inwardLimit: Float): Boolean =
        outward <= outwardLimit && -outward <= inwardLimit
    var handle = CROP_HANDLE_NONE
    var nearest = Float.MAX_VALUE
    for (index in 0..3) {
        val isLeft = index % 2 == 0
        val isTop = index < 2
        val dx = if (isLeft) frameLeft - pointX else pointX - frameRight
        val dy = if (isTop) frameTop - pointY else pointY - frameBottom
        if (!inBand(dx, outwardX, inwardX) || !inBand(dy, outwardY, inwardY)) continue
        val distance = abs(dx) + abs(dy)
        // Strict "<" keeps the lowest index on a tie, like minByOrNull did.
        if (distance < nearest) {
            nearest = distance
            handle = index
        }
    }
    if (handle != CROP_HANDLE_NONE) return handle
    // Edges get a wider band than the corners: the finger usually lands
    // slightly outside the visible border.
    val edge = maxOf(outwardX, outwardY) * 1.8f
    val candidates = floatArrayOf(
        if (pointX in frameLeft - edge..frameRight + edge && inBand(frameTop - pointY, edge, inwardY)) abs(pointY - frameTop) else Float.POSITIVE_INFINITY,
        if (pointX in frameLeft - edge..frameRight + edge && inBand(pointY - frameBottom, edge, inwardY)) abs(pointY - frameBottom) else Float.POSITIVE_INFINITY,
        if (pointY in frameTop - edge..frameBottom + edge && inBand(frameLeft - pointX, edge, inwardX)) abs(pointX - frameLeft) else Float.POSITIVE_INFINITY,
        if (pointY in frameTop - edge..frameBottom + edge && inBand(pointX - frameRight, edge, inwardX)) abs(pointX - frameRight) else Float.POSITIVE_INFINITY
    )
    val nearestEdge = candidates.indices.minByOrNull { candidates[it] }
    if (nearestEdge != null && candidates[nearestEdge] <= edge) return nearestEdge + 4
    return if (pointX in frameLeft..frameRight && pointY in frameTop..frameBottom) {
        CROP_HANDLE_MOVE
    } else {
        CROP_HANDLE_NONE
    }
}

@Composable
private fun CropFrame(
    frame: NormalizedRect,
    normalizedRatio: Float,
    visibleScale: Float = 1f,
    straighten: Float,
    onFrameChange: (NormalizedRect) -> Unit,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val minSize = .06f
    val currentFrame by rememberUpdatedState(frame)
    val currentOnFrameChange by rememberUpdatedState(onFrameChange)
    Canvas(
        modifier.pointerInput(normalizedRatio, visibleScale, straighten) {
            var handle = -1 // 0..3 corners, 4..7 edges, 8 whole-frame move
            var working = currentFrame
            // The tilt is a preview inset, not a limit on the crop: a drag only
            // has to keep the frame inside the picture.
            fun constrained(value: NormalizedRect) = constrainWallpaperFrameToImage(value)
            fun shownFrame(value: NormalizedRect) = NormalizedRect(
                left = .5f + (value.left - .5f) * visibleScale,
                top = .5f + (value.top - .5f) * visibleScale,
                right = .5f + (value.right - .5f) * visibleScale,
                bottom = .5f + (value.bottom - .5f) * visibleScale
            )
            detectDragGestures(
                onDragStart = { point ->
                    // The drawn frame is already a valid crop, so nothing is
                    // written back just because a drag started.
                    working = currentFrame
                    val pointX = point.x / size.width
                    val pointY = point.y / size.height
                    val visible = shownFrame(working)
                    // The finger usually lands just *outside* the frame, so the
                    // outward part of a handle stays generous; the part that
                    // reaches inside is halved, otherwise the small frame was
                    // mostly made of resize zones and moving it was hard.
                    val radiusX = (48f * density.density / size.width).coerceAtLeast(.024f)
                    val radiusY = (48f * density.density / size.height).coerceAtLeast(.024f)
                    // One shared hit test, so the frame and the black bars
                    // around it can never disagree about what a touch grabbed.
                    handle = cropHandleAt(
                        frameLeft = visible.left,
                        frameTop = visible.top,
                        frameRight = visible.right,
                        frameBottom = visible.bottom,
                        pointX = pointX,
                        pointY = pointY,
                        outwardX = radiusX,
                        outwardY = radiusY,
                        inwardX = radiusX * 0.5f,
                        inwardY = radiusY * 0.5f
                    )
                },
                onDrag = { change, amount ->
                    if (handle < 0) return@detectDragGestures
                    change.consume()
                    val dx = amount.x / size.width / visibleScale
                    val dy = amount.y / size.height / visibleScale
                    val x = (.5f + (change.position.x / size.width - .5f) / visibleScale).coerceIn(0f, 1f)
                    val y = (.5f + (change.position.y / size.height - .5f) / visibleScale).coerceIn(0f, 1f)
                    val candidate = when (handle) {
                        0, 1, 2, 3, 4, 5, 6, 7 -> resizeFrameFromPointer(working, handle, x, y, normalizedRatio, minSize)
                        else -> {
                            val left = working.left + dx
                            val top = working.top + dy
                            NormalizedRect(left, top, left + working.width, top + working.height)
                        }
                    }
                    working = constrained(candidate)
                    currentOnFrameChange(working)
                },
                onDragEnd = { handle = -1 },
                onDragCancel = { handle = -1 }
            )
        }
    ) {
        val visible = NormalizedRect(
            left = .5f + (frame.left - .5f) * visibleScale,
            top = .5f + (frame.top - .5f) * visibleScale,
            right = .5f + (frame.right - .5f) * visibleScale,
            bottom = .5f + (frame.bottom - .5f) * visibleScale
        )
        val left = visible.left * size.width
        val top = visible.top * size.height
        val right = visible.right * size.width
        val bottom = visible.bottom * size.height
        drawRect(Color.Black.copy(alpha = .52f), topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(size.width, top))
        drawRect(Color.Black.copy(alpha = .52f), topLeft = Offset(0f, bottom), size = androidx.compose.ui.geometry.Size(size.width, size.height - bottom))
        drawRect(Color.Black.copy(alpha = .52f), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, bottom - top))
        drawRect(Color.Black.copy(alpha = .52f), topLeft = Offset(right, top), size = androidx.compose.ui.geometry.Size(size.width - right, bottom - top))
        drawRect(Color.White, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(right - left, bottom - top), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        val corner = 20.dp.toPx()
        listOf(
            Offset(left, top) to listOf(Offset(left + corner, top), Offset(left, top + corner)),
            Offset(right, top) to listOf(Offset(right - corner, top), Offset(right, top + corner)),
            Offset(left, bottom) to listOf(Offset(left + corner, bottom), Offset(left, bottom - corner)),
            Offset(right, bottom) to listOf(Offset(right - corner, bottom), Offset(right, bottom - corner))
        ).forEach { (start, ends) -> ends.forEach { end -> drawLine(Color.White, start, end, 5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round) } }
    }
}

private fun resizeFrameFromPointer(
    base: NormalizedRect,
    handle: Int,
    x: Float,
    y: Float,
    ratio: Float,
    minSize: Float
): NormalizedRect {
    val safeRatio = ratio.coerceAtLeast(.0001f)

    fun fit(rawWidth: Float, rawHeight: Float, maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        val minimumWidth = maxOf(minSize, minSize * safeRatio)
        val minimumHeight = maxOf(minSize, minSize / safeRatio)
        var width = rawWidth.coerceAtLeast(minimumWidth)
        var height = width / safeRatio
        if (height < minimumHeight) {
            height = minimumHeight
            width = height * safeRatio
        }
        val scale = minOf(1f, maxWidth / width, maxHeight / height)
        return width * scale to height * scale
    }

    return when (handle) {
        0, 1, 2, 3 -> {
            val anchorX = if (handle == 0 || handle == 2) base.right else base.left
            val anchorY = if (handle == 0 || handle == 1) base.bottom else base.top
            val leftSide = handle == 0 || handle == 2
            val topSide = handle == 0 || handle == 1
            val rawWidth = if (leftSide) anchorX - x else x - anchorX
            val (width, height) = fit(
                rawWidth,
                rawWidth / safeRatio,
                if (leftSide) anchorX else 1f - anchorX,
                if (topSide) anchorY else 1f - anchorY
            )
            val left = if (leftSide) anchorX - width else anchorX
            val top = if (topSide) anchorY - height else anchorY
            NormalizedRect(left, top, left + width, top + height)
        }
        4, 5 -> {
            val anchorY = if (handle == 4) base.bottom else base.top
            val centerX = (base.left + base.right) / 2f
            val topSide = handle == 4
            val rawHeight = if (topSide) anchorY - y else y - anchorY
            val (width, height) = fit(
                rawHeight * safeRatio,
                rawHeight,
                2f * min(centerX, 1f - centerX),
                if (topSide) anchorY else 1f - anchorY
            )
            val top = if (topSide) anchorY - height else anchorY
            NormalizedRect(centerX - width / 2f, top, centerX + width / 2f, top + height)
        }
        6, 7 -> {
            val anchorX = if (handle == 6) base.right else base.left
            val centerY = (base.top + base.bottom) / 2f
            val leftSide = handle == 6
            val rawWidth = if (leftSide) anchorX - x else x - anchorX
            val (width, height) = fit(
                rawWidth,
                rawWidth / safeRatio,
                if (leftSide) anchorX else 1f - anchorX,
                2f * min(centerY, 1f - centerY)
            )
            val left = if (leftSide) anchorX - width else anchorX
            NormalizedRect(left, centerY - height / 2f, left + width, centerY + height / 2f)
        }
        else -> base
    }
}

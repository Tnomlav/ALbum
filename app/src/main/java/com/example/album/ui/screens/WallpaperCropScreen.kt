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
    var frame by remember(item.uri) { mutableStateOf(NormalizedRect()) }
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
                frame = NormalizedRect()
            }) {
                Icon(Icons.Outlined.Restore, appText("重置修改", english), tint = Color.White)
            }
            IconButton(onClick = {
                editState = editState.copy(rotation = (rotation + 270) % 360)
                frame = NormalizedRect()
            }) {
                Icon(Icons.Outlined.RotateLeft, appText("左转", english), tint = Color.White)
            }
            IconButton(onClick = {
                multiScreen = !multiScreen
                frame = NormalizedRect()
            }) {
                Icon(Icons.Outlined.ViewWeek, if (multiScreen) appText("切换为单屏宽度", english) else appText("切换为多屏宽度", english), tint = Color.White)
            }
            IconButton(onClick = {
                editState = editState.copy(rotation = (rotation + 90) % 360)
                frame = NormalizedRect()
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
                val selected = if (frame == NormalizedRect()) centeredFrame(imageRatio, ratio) else frame
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
                BoxWithConstraints(
                    Modifier.fillMaxSize().statusBarsPadding().padding(top = 58.dp, bottom = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val imageRatio = (editorGeometry.width.toFloat() / editorGeometry.height.coerceAtLeast(1))
                        .takeIf { it.isFinite() && it > 0f } ?: 1f
                    val width = min(maxWidth.value, maxHeight.value * imageRatio).coerceAtLeast(1f).dp
                    val height = (width.value / imageRatio).coerceAtLeast(1f).dp
                    val ratio = if (multiScreen) multiScreenRatio else singleScreenRatio.coerceAtLeast(.01f)
                    val initial = centeredFrame(imageRatio, ratio)
                    val displayFrame = if (frame == NormalizedRect()) initial else frame
                    val safeStraighten = straighten.safeWallpaperAngle()
                    val angle = Math.toRadians(safeStraighten.toDouble())
                    val cosine = abs(cos(angle)).toFloat()
                    val sine = abs(sin(angle)).toFloat()
                    val frameWidth = width.value * displayFrame.width.coerceIn(.001f, 1f)
                    val frameHeight = height.value * displayFrame.height.coerceIn(.001f, 1f)
                    val safeFrameScale = minOf(
                        1f,
                        width.value / (frameWidth * cosine + frameHeight * sine).coerceAtLeast(.0001f),
                        height.value / (frameWidth * sine + frameHeight * cosine).coerceAtLeast(.0001f)
                    ).coerceIn(.01f, 1f)
                    LaunchedEffect(displayFrame, safeStraighten, safeFrameScale) {
                        val constrained = constrainWallpaperFrameToRotatedBounds(displayFrame, safeStraighten, safeFrameScale)
                        if (constrained != frame) frame = constrained
                    }
                    Box(Modifier.width(width).height(height)) {
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
                            onFrameChange = { frame = it },
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
            fun constrained(value: NormalizedRect) = constrainWallpaperFrameToRotatedBounds(
                value,
                straighten,
                visibleScale
            )
            fun shownFrame(value: NormalizedRect) = NormalizedRect(
                left = .5f + (value.left - .5f) * visibleScale,
                top = .5f + (value.top - .5f) * visibleScale,
                right = .5f + (value.right - .5f) * visibleScale,
                bottom = .5f + (value.bottom - .5f) * visibleScale
            )
            detectDragGestures(
                onDragStart = { point ->
                    working = constrained(currentFrame)
                    if (working != currentFrame) currentOnFrameChange(working)
                    val pointX = point.x / size.width
                    val pointY = point.y / size.height
                    val visible = shownFrame(working)
                    val radiusX = (48f * density.density / size.width).coerceAtLeast(.024f)
                    val radiusY = (48f * density.density / size.height).coerceAtLeast(.024f)
                    val radius = maxOf(radiusX, radiusY)
                    val corners = listOf(
                        Offset(visible.left, visible.top), Offset(visible.right, visible.top),
                        Offset(visible.left, visible.bottom), Offset(visible.right, visible.bottom)
                    )
                    handle = corners.indices.minByOrNull {
                        val dx = corners[it].x - pointX
                        val dy = corners[it].y - pointY
                        dx * dx + dy * dy
                    }?.takeIf {
                        val dx = corners[it].x - pointX
                        val dy = corners[it].y - pointY
                        dx * dx + dy * dy <= radius * radius
                    } ?: -1
                    if (handle < 0) {
                        val edgeTolerance = radius
                        val candidates = listOf(
                            if (pointX in visible.left - edgeTolerance..visible.right + edgeTolerance) abs(pointY - visible.top) else Float.POSITIVE_INFINITY,
                            if (pointX in visible.left - edgeTolerance..visible.right + edgeTolerance) abs(pointY - visible.bottom) else Float.POSITIVE_INFINITY,
                            if (pointY in visible.top - edgeTolerance..visible.bottom + edgeTolerance) abs(pointX - visible.left) else Float.POSITIVE_INFINITY,
                            if (pointY in visible.top - edgeTolerance..visible.bottom + edgeTolerance) abs(pointX - visible.right) else Float.POSITIVE_INFINITY
                        )
                        handle = candidates.indices.minByOrNull { candidates[it] }
                            ?.takeIf { candidates[it] <= edgeTolerance }?.plus(4) ?: -1
                    }
                    if (handle < 0 && pointX in visible.left..visible.right && pointY in visible.top..visible.bottom) handle = 8
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

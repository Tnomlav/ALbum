package com.example.album.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
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
import com.example.album.ui.editor.NormalizedRect
import com.example.album.ui.editor.cropWallpaperBitmap
import com.example.album.ui.editor.loadWallpaperBitmap
import com.example.album.ui.editor.EditorRuler
import kotlin.math.abs
import kotlin.math.min

@Composable
fun WallpaperCropScreen(
    item: MediaItem,
    onDismiss: () -> Unit,
    onConfirm: (android.graphics.Bitmap) -> Unit
) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val configuration = LocalConfiguration.current
    val portraitRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.coerceAtLeast(1)
    var landscape by remember { mutableStateOf(false) }
    var rotation by remember { mutableStateOf(0) }
    var straighten by remember { mutableStateOf(0f) }
    var bitmap by remember(item.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loading by remember(item.uri) { mutableStateOf(true) }
    var frame by remember(item.uri) { mutableStateOf(NormalizedRect()) }

    LaunchedEffect(item.uri) {
        loading = true
        bitmap = loadWallpaperBitmap(context, item)
        loading = false
    }
    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).background(Color.Black.copy(alpha = .92f)).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, appText("取消", english), tint = Color.White)
            }
            IconButton(onClick = {
                rotation = (rotation + 270) % 360
                frame = NormalizedRect()
            }) {
                Icon(Icons.Outlined.RotateLeft, appText("左转", english), tint = Color.White)
            }
            IconButton(onClick = {
                rotation = (rotation + 90) % 360
                frame = NormalizedRect()
            }) {
                Icon(Icons.Outlined.RotateRight, appText("右转", english), tint = Color.White)
            }
            IconButton(onClick = {
                landscape = !landscape
                frame = NormalizedRect()
            }) {
                Icon(Icons.Outlined.ScreenRotation, appText("切换方向", english), tint = Color.White)
            }
            IconButton(onClick = {
                val source = bitmap ?: return@IconButton
                val ratio = if (landscape) 1f / portraitRatio.coerceAtLeast(.01f) else portraitRatio.coerceAtLeast(.01f)
                val imageRatio = if (rotation % 180 == 0) {
                    source.width.toFloat() / source.height.coerceAtLeast(1)
                } else {
                    source.height.toFloat() / source.width.coerceAtLeast(1)
                }
                val selected = if (frame == NormalizedRect()) centeredFrame(imageRatio, ratio) else frame
                onConfirm(cropWallpaperBitmap(source, selected, ratio, rotation, straighten))
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
                BoxWithConstraints(
                    Modifier.fillMaxSize().padding(top = 58.dp, bottom = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val imageRatio = if (rotation % 180 == 0) {
                        source.width.toFloat() / source.height.coerceAtLeast(1)
                    } else {
                        source.height.toFloat() / source.width.coerceAtLeast(1)
                    }
                    val width = min(maxWidth.value, maxHeight.value * imageRatio).dp
                    val height = (width.value / imageRatio).dp
                    val ratio = if (landscape) 1f / portraitRatio.coerceAtLeast(.01f) else portraitRatio.coerceAtLeast(.01f)
                    val initial = centeredFrame(imageRatio, ratio)
                    if (frame == NormalizedRect()) frame = initial
                    Box(Modifier.width(width).height(height)) {
                        Image(
                            source.asImageBitmap(),
                            null,
                            Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation + straighten },
                            contentScale = ContentScale.FillBounds
                        )
                        CropFrame(
                            frame = frame,
                            normalizedRatio = ratio / imageRatio,
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
                    text = if (english) "Angle: ${straighten.toInt()}°" else "角度：${straighten.toInt()}°",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                EditorRuler(
                    value = straighten,
                    valueRange = -45f..45f,
                    onValueChange = { straighten = it },
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    tickSpacing = 5.dp,
                    edgeInset = 14.dp
                )
            }
        }
    }
}

private fun centeredFrame(imageRatio: Float, frameRatio: Float): NormalizedRect {
    val width = if (imageRatio > frameRatio) frameRatio / imageRatio else 1f
    val height = if (imageRatio > frameRatio) 1f else imageRatio / frameRatio
    return NormalizedRect((1f - width) / 2f, (1f - height) / 2f, (1f + width) / 2f, (1f + height) / 2f)
}

@Composable
private fun CropFrame(
    frame: NormalizedRect,
    normalizedRatio: Float,
    onFrameChange: (NormalizedRect) -> Unit,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val minSize = .06f
    Canvas(
        modifier.pointerInput(frame, normalizedRatio) {
            var handle = -1 // 0..3 corners, 4..7 edges, 8 whole-frame move
            var working = frame
            detectDragGestures(
                onDragStart = { point ->
                    val pointX = point.x / size.width
                    val pointY = point.y / size.height
                    val radiusX = (48f * density.density / size.width).coerceAtLeast(.024f)
                    val radiusY = (48f * density.density / size.height).coerceAtLeast(.024f)
                    val radius = maxOf(radiusX, radiusY)
                    val corners = listOf(
                        Offset(frame.left, frame.top), Offset(frame.right, frame.top),
                        Offset(frame.left, frame.bottom), Offset(frame.right, frame.bottom)
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
                            if (pointX in frame.left - edgeTolerance..frame.right + edgeTolerance) abs(pointY - frame.top) else Float.POSITIVE_INFINITY,
                            if (pointX in frame.left - edgeTolerance..frame.right + edgeTolerance) abs(pointY - frame.bottom) else Float.POSITIVE_INFINITY,
                            if (pointY in frame.top - edgeTolerance..frame.bottom + edgeTolerance) abs(pointX - frame.left) else Float.POSITIVE_INFINITY,
                            if (pointY in frame.top - edgeTolerance..frame.bottom + edgeTolerance) abs(pointX - frame.right) else Float.POSITIVE_INFINITY
                        )
                        handle = candidates.indices.minByOrNull { candidates[it] }
                            ?.takeIf { candidates[it] <= edgeTolerance }?.plus(4) ?: -1
                    }
                    if (handle < 0 && pointX in frame.left..frame.right && pointY in frame.top..frame.bottom) handle = 8
                },
                onDrag = { change, amount ->
                    if (handle < 0) return@detectDragGestures
                    change.consume()
                    val dx = amount.x / size.width
                    val dy = amount.y / size.height
                    val x = (change.position.x / size.width).coerceIn(0f, 1f)
                    val y = (change.position.y / size.height).coerceIn(0f, 1f)
                    working = when (handle) {
                        0, 1, 2, 3 -> resizeCornerFromPointer(working, handle, x, y, normalizedRatio, minSize)
                        4, 5, 6, 7 -> resizeEdgeFromPointer(working, handle, x, y, normalizedRatio, minSize)
                        else -> {
                            val left = (working.left + dx).coerceIn(0f, 1f - working.width)
                            val top = (working.top + dy).coerceIn(0f, 1f - working.height)
                            NormalizedRect(left, top, left + working.width, top + working.height)
                        }
                    }
                    onFrameChange(working)
                },
                onDragEnd = { handle = -1 },
                onDragCancel = { handle = -1 }
            )
        }
    ) {
        val left = frame.left * size.width
        val top = frame.top * size.height
        val right = frame.right * size.width
        val bottom = frame.bottom * size.height
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

private fun resizeCornerFromPointer(
    base: NormalizedRect,
    handle: Int,
    x: Float,
    y: Float,
    ratio: Float,
    minSize: Float
): NormalizedRect {
    val anchorX = if (handle == 0 || handle == 2) base.right else base.left
    val anchorY = if (handle == 0 || handle == 1) base.bottom else base.top
    val directionX = if (handle == 0 || handle == 2) -1f else 1f
    val directionY = if (handle == 0 || handle == 1) -1f else 1f
    val rawWidth = if (directionX < 0f) anchorX - x else x - anchorX
    val rawHeight = if (directionY < 0f) anchorY - y else y - anchorY
    val width = maxOf(minSize, rawWidth, rawHeight * ratio).coerceAtMost(
        minOf(
            if (directionX < 0f) anchorX else 1f - anchorX,
            (if (directionY < 0f) anchorY else 1f - anchorY) * ratio
        ).coerceAtLeast(minSize)
    )
    val height = (width / ratio).coerceAtLeast(minSize)
    val left = if (directionX < 0f) anchorX - width else anchorX
    val top = if (directionY < 0f) anchorY - height else anchorY
    return NormalizedRect(left, top, left + width, top + height)
}

private fun resizeEdgeFromPointer(
    base: NormalizedRect,
    handle: Int,
    x: Float,
    y: Float,
    ratio: Float,
    minSize: Float
): NormalizedRect {
    return if (handle == 4 || handle == 5) {
        val anchorY = if (handle == 4) base.bottom else base.top
        val centerX = (base.left + base.right) / 2f
        val maxHeight = if (handle == 4) anchorY else 1f - anchorY
        val height = (if (handle == 4) anchorY - y else y - anchorY)
            .coerceAtLeast(minSize / ratio.coerceAtLeast(.0001f))
            .coerceAtMost(maxHeight)
        val width = (height * ratio).coerceAtMost(2f * min(centerX, 1f - centerX))
        val top = if (handle == 4) anchorY - height else anchorY
        NormalizedRect(centerX - width / 2f, top, centerX + width / 2f, top + height)
    } else {
        val anchorX = if (handle == 6) base.right else base.left
        val centerY = (base.top + base.bottom) / 2f
        val maxWidth = if (handle == 6) anchorX else 1f - anchorX
        val width = (if (handle == 6) anchorX - x else x - anchorX)
            .coerceAtLeast(minSize)
            .coerceAtMost(maxWidth)
        val height = (width / ratio.coerceAtLeast(.0001f)).coerceAtMost(2f * min(centerY, 1f - centerY))
        val left = if (handle == 6) anchorX - width else anchorX
        NormalizedRect(left, centerY - height / 2f, left + width, centerY + height / 2f)
    }
}

private fun resizeCorner(
    base: NormalizedRect,
    handle: Int,
    dx: Float,
    dy: Float,
    ratio: Float,
    minSize: Float
): NormalizedRect {
    val anchorX = if (handle == 0 || handle == 2) base.right else base.left
    val anchorY = if (handle == 0 || handle == 1) base.bottom else base.top
    val directionX = if (handle == 0 || handle == 2) -1f else 1f
    val directionY = if (handle == 0 || handle == 1) -1f else 1f
    val requestedWidth = maxOf(minSize, abs(anchorX - (anchorX + dx)) , abs(anchorY - (anchorY + dy)) * ratio)
    val maxWidth = minOf(
        if (directionX < 0f) anchorX else 1f - anchorX,
        (if (directionY < 0f) anchorY else 1f - anchorY) * ratio
    ).coerceAtLeast(minSize)
    val width = requestedWidth.coerceAtMost(maxWidth)
    val height = (width / ratio).coerceAtLeast(minSize)
    val left = if (directionX < 0f) anchorX - width else anchorX
    val top = if (directionY < 0f) anchorY - height else anchorY
    return NormalizedRect(left, top, left + width, top + height)
}

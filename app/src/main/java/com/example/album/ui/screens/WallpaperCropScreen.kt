package com.example.album.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.album.data.MediaItem
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.editor.NormalizedRect
import com.example.album.ui.editor.cropWallpaperBitmap
import com.example.album.ui.editor.loadWallpaperBitmap
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
                landscape = !landscape
                frame = NormalizedRect()
            }) {
                Icon(Icons.Outlined.ScreenRotation, appText("切换方向", english), tint = Color.White)
            }
            IconButton(onClick = {
                val source = bitmap ?: return@IconButton
                val ratio = if (landscape) 1f / portraitRatio.coerceAtLeast(.01f) else portraitRatio.coerceAtLeast(.01f)
                val selected = if (frame == NormalizedRect()) centeredFrame(source.width.toFloat() / source.height, ratio) else frame
                onConfirm(cropWallpaperBitmap(source, selected, ratio))
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
                    val imageRatio = source.width.toFloat() / source.height.coerceAtLeast(1)
                    val width = min(maxWidth.value, maxHeight.value * imageRatio).dp
                    val height = (width.value / imageRatio).dp
                    val ratio = if (landscape) 1f / portraitRatio.coerceAtLeast(.01f) else portraitRatio.coerceAtLeast(.01f)
                    val initial = centeredFrame(imageRatio, ratio)
                    if (frame == NormalizedRect()) frame = initial
                    Box(Modifier.width(width).height(height)) {
                        Image(source.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
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
    val minSize = .08f
    Canvas(
        modifier.pointerInput(frame) {
            var handle = -1
            var working = frame
            detectDragGestures(
                onDragStart = { point ->
                    val points = listOf(
                        Offset(frame.left * size.width, frame.top * size.height),
                        Offset(frame.right * size.width, frame.top * size.height),
                        Offset(frame.left * size.width, frame.bottom * size.height),
                        Offset(frame.right * size.width, frame.bottom * size.height)
                    )
                    val hit = points.withIndex().minByOrNull { (it.value - point).getDistance() }
                    handle = if (hit != null && (hit.value - point).getDistance() <= 64f) hit.index else if (
                        point.x in frame.left * size.width..frame.right * size.width &&
                        point.y in frame.top * size.height..frame.bottom * size.height
                    ) 4 else -1
                },
                onDrag = { change, amount ->
                    if (handle < 0) return@detectDragGestures
                    change.consume()
                    val dx = amount.x / size.width
                    val dy = amount.y / size.height
                    working = when (handle) {
                        0, 1, 2, 3 -> resizeCorner(working, handle, dx, dy, normalizedRatio, minSize)
                        else -> {
                            val left = (working.left + dx).coerceIn(0f, 1f - working.width)
                            val top = (working.top + dy).coerceIn(0f, 1f - working.height)
                            NormalizedRect(left, top, left + working.width, top + working.height)
                        }
                    }
                    onFrameChange(working)
                },
                onDragEnd = { handle = -1 }
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
        listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom)).forEach {
            drawCircle(Color.White, radius = 7.dp.toPx(), center = it)
        }
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

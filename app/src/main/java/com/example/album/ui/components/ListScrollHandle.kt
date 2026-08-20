package com.example.album.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween

@Composable
fun ListScrollHandle(
    progress: Float,
    visibleFraction: Float,
    scrolling: Boolean,
    onFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("album_settings", Context.MODE_PRIVATE) }
    var persistent by remember { mutableStateOf(preferences.getBoolean("persistent_scrollbar", false)) }
    var touchWidthDp by remember { mutableIntStateOf(scrollTouchWidthDp(preferences.getString("scroll_width", "24px"))) }
    var duration by remember { mutableStateOf(scrollVisibleDurationMillis(preferences.getString("scroll_duration", "1秒"))) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
            when (key) {
                "persistent_scrollbar" -> persistent = shared.getBoolean(key, false)
                "scroll_width" -> touchWidthDp = scrollTouchWidthDp(shared.getString(key, "24px"))
                "scroll_duration" -> duration = scrollVisibleDurationMillis(shared.getString(key, "1秒"))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val touchWidth = touchWidthDp.dp
    var visible by remember { mutableStateOf(persistent) }
    var dragging by remember { mutableStateOf(false) }
    var interactionEpoch by remember { mutableIntStateOf(0) }
    val accent = MaterialTheme.colorScheme.primary
    val currentOnFraction by rememberUpdatedState(onFraction)
    val currentProgress by rememberUpdatedState(progress.coerceIn(0f, 1f))
    val currentVisibleFraction by rememberUpdatedState(visibleFraction.coerceIn(0f, 1f))
    LaunchedEffect(scrolling, dragging, persistent, duration, interactionEpoch) {
        if (persistent || scrolling || dragging || interactionEpoch > 0) visible = true
        if (!persistent && !scrolling && !dragging) {
            delay(duration)
            visible = false
        }
    }
    if (visibleFraction >= .999f) return
    val baseThumbWidth = scrollThumbWidthDp(touchWidthDp).dp
    val thumbWidth by animateDpAsState(
        if (dragging) baseThumbWidth + 2.dp else baseThumbWidth,
        tween(100),
        label = "scroll-thumb-width"
    )

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.zIndex(20f),
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(160))
    ) {
    Box(
        Modifier.fillMaxHeight().width(touchWidth).zIndex(20f)
            .pointerInput(touchWidthDp) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val trackHeight = size.height.toFloat()
                    if (trackHeight <= 0f) return@awaitEachGesture
                    val thumbHeight = (trackHeight * currentVisibleFraction)
                        .coerceAtLeast(32.dp.toPx())
                        .coerceAtMost(trackHeight)
                    val travel = (trackHeight - thumbHeight).coerceAtLeast(0f)
                    val thumbTop = currentProgress * travel
                    val startedOnThumb = down.position.y in thumbTop..(thumbTop + thumbHeight)
                    interactionEpoch++
                    down.consume()

                    if (!startedOnThumb) {
                        currentOnFraction((down.position.y / trackHeight).coerceIn(0f, 1f))
                        while (down.pressed) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                        }
                        return@awaitEachGesture
                    }

                    dragging = true
                    val startY = down.position.y
                    val startProgress = currentProgress
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            currentOnFraction(
                                scrollFractionForThumbDrag(
                                    startProgress = startProgress,
                                    pointerDeltaPx = change.position.y - startY,
                                    trackHeightPx = trackHeight,
                                    thumbHeightPx = thumbHeight
                                )
                            )
                            change.consume()
                        }
                    } finally {
                        dragging = false
                        interactionEpoch++
                    }
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val thumbHeight = (size.height * visibleFraction).coerceAtLeast(32.dp.toPx()).coerceAtMost(size.height)
            val top = progress.coerceIn(0f, 1f) * (size.height - thumbHeight)
            drawRoundRect(
                color = accent,
                topLeft = Offset(size.width - thumbWidth.toPx() - 2.dp.toPx(), top),
                size = Size(thumbWidth.toPx(), thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(999.dp.toPx())
            )
        }
    }
    }
}

internal fun scrollTouchWidthDp(stored: String?): Int =
    stored?.filter(Char::isDigit)?.toIntOrNull()?.takeIf { it in setOf(16, 24, 32) } ?: 24

internal fun scrollVisibleDurationMillis(stored: String?): Long = when (stored) {
    "0.5秒", "0.5s" -> 500L
    "2秒", "2s" -> 2_000L
    "3秒", "3s" -> 3_000L
    else -> 1_000L
}

internal fun scrollThumbWidthDp(touchWidthDp: Int): Int = when (touchWidthDp) {
    16 -> 4
    32 -> 7
    else -> 5
}

internal fun scrollFractionForThumbDrag(
    startProgress: Float,
    pointerDeltaPx: Float,
    trackHeightPx: Float,
    thumbHeightPx: Float
): Float {
    val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
    return (startProgress + pointerDeltaPx / travel).coerceIn(0f, 1f)
}

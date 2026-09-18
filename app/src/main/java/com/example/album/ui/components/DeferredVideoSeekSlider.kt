package com.example.album.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A video seek bar where a tap commits on release, while a drag is relative
 * to the thumb's value at pointer-down instead of jumping to the touch point.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DeferredVideoSeekSlider(
    valueMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    colors: SliderColors,
    thumb: (@Composable (() -> Unit))? = null,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = .35f),
    thumbColor: Color = Color.White,
    thumbBorderColor: Color = Color.White
) {
    val duration = durationMs.coerceAtLeast(1L)
    val trackStrokeWidthPx = with(LocalDensity.current) { 12.dp.toPx() }
    // Back to the archive page's geometry (only the bar width stays at 12dp):
    // 3dp between the track ends and the thumb centre.
    val thumbInsetPx = with(LocalDensity.current) { 6.dp.toPx() }
    val thumbRadiusPx = with(LocalDensity.current) { 6.dp.toPx() }
    val thumbRingWidthPx = with(LocalDensity.current) { 3.dp.toPx() }
    var displayedValue by remember { mutableFloatStateOf(valueMs.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val latestOnSeek = rememberUpdatedState(onSeek)
    val latestDisplayedValue = rememberUpdatedState(displayedValue)

    LaunchedEffect(valueMs) {
        if (!dragging) displayedValue = valueMs.toFloat().coerceIn(0f, duration.toFloat())
    }

    val gestureModifier = Modifier.pointerInput(duration) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            down.consume()
            val startValue = latestDisplayedValue.value.coerceIn(0f, duration.toFloat())
            val startX = down.position.x
            dragging = true
            try {
                var moved = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dx = change.position.x - startX
                    if (change.pressed) {
                        if (!moved && abs(dx) > viewConfiguration.touchSlop) moved = true
                        if (moved) {
                            val next = deferredSeekRelativeValue(startValue, dx, size.width, duration)
                            displayedValue = next.toFloat()
                            latestOnSeek.value(next)
                        }
                        change.consume()
                    } else {
                        val finalValue = if (moved) {
                            deferredSeekRelativeValue(startValue, dx, size.width, duration)
                        } else {
                            deferredSeekAbsoluteValue(change.position.x, size.width, duration)
                        }
                        displayedValue = finalValue.toFloat()
                        latestOnSeek.value(finalValue)
                        change.consume()
                        break
                    }
                }
            } finally {
                // Duration changes can cancel pointerInput while the finger is
                // down; always release the local drag lock on cancellation.
                dragging = false
            }
        }
    }

    // 1:1 with the Pixiv archive page's scan-limit slider: a 12dp rounded track
    // whose played and unplayed segments stop 3dp short of the thumb centre,
    // and a 16dp thumb with a 4dp ring. Only the colours differ (white on the
    // player's black background instead of the theme accent).
    Slider(
        value = displayedValue.coerceIn(0f, duration.toFloat()),
        // During touch gestures the custom handler owns the interaction. Keep
        // the callback for semantic/keyboard actions, which have no pointer
        // gesture and should still be able to set progress accessibly.
        onValueChange = { nextValue ->
            if (!dragging) {
                val next = nextValue.roundToLong().coerceIn(0L, duration)
                displayedValue = next.toFloat()
                latestOnSeek.value(next)
            }
        },
        valueRange = 0f..duration.toFloat(),
        modifier = modifier.then(gestureModifier),
        track = { sliderState ->
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val centerY = size.height / 2f
                val fraction = ((sliderState.value - sliderState.valueRange.start) /
                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start))
                    .coerceIn(0f, 1f)
                // Material3 keeps half a thumb of padding at both ends, so the
                // thumb travels between those insets. Using the full width put
                // the split off-centre from the thumb.
                val inset = thumbInsetPx
                val thumbCenter = inset + (size.width - inset * 2f).coerceAtLeast(0f) * fraction
                val separatorOuter = (thumbCenter - thumbRadiusPx).coerceAtLeast(0f)
                // The whole track is painted in the unplayed colour first: the
                // separator ring around the thumb is that same colour and
                // transparency, so it merges with the unplayed track instead of
                // showing a black notch next to the thumb.
                drawLine(
                    color = inactiveColor,
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = trackStrokeWidthPx,
                    cap = StrokeCap.Round
                )
                // The played part keeps the bar's own rounded shape and stops at
                // the ring's outer edge: only the thumb carries the separator,
                // the bar itself is never notched or cut square. Ring plus dot
                // are exactly the thumb's old 12dp.
                if (separatorOuter > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, centerY - trackStrokeWidthPx / 2f),
                        size = Size(separatorOuter, trackStrokeWidthPx),
                        cornerRadius = CornerRadius(thumbRadiusPx, thumbRadiusPx)
                    )
                }
                // The thumb is drawn here, in the same coordinate space as the
                // track, so its centre always sits on the track's centre line.
                // A separate thumb slot is measured and placed by Material3,
                // which left it a few pixels off the track's middle.
                drawCircle(
                    color = thumbColor,
                    radius = (thumbRadiusPx - thumbRingWidthPx).coerceAtLeast(0f),
                    center = Offset(thumbCenter, centerY)
                )
            }
        },
        colors = SliderDefaults.colors(
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor,
            thumbColor = thumbColor,
            disabledActiveTrackColor = activeColor,
            disabledInactiveTrackColor = inactiveColor,
            disabledThumbColor = thumbColor,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
            disabledActiveTickColor = Color.Transparent,
            disabledInactiveTickColor = Color.Transparent
        ),
        thumb = {
            // Invisible: it keeps Material3's thumb width (which is what the
            // track's travel and the touch target are based on) while the
            // visible thumb is painted with the track above.
            Box(Modifier.size(12.dp))
        }
    )
}

internal fun deferredSeekAbsoluteValue(x: Float, width: Int, duration: Long): Long {
    if (width <= 0) return 0L
    return (x / width.toFloat()).coerceIn(0f, 1f).times(duration.toDouble()).roundToLong().coerceIn(0L, duration)
}

internal fun deferredSeekRelativeValue(start: Float, dx: Float, width: Int, duration: Long): Long {
    if (width <= 0) return start.roundToLong().coerceIn(0L, duration)
    return (start + dx / width.toFloat() * duration.toFloat()).roundToLong().coerceIn(0L, duration)
}

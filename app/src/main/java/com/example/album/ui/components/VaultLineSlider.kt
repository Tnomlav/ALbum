package com.example.album.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * The rounded line slider used by the Pixiv archive page: a thick rounded
 * track that stops just short of the thumb, plus a white-ringed round thumb.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VaultLineSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color,
    thumbBorderColor: Color = Color.White,
    enabled: Boolean = true,
    steps: Int = 0,
    thumbGap: androidx.compose.ui.unit.Dp = 3.dp
) {
    val trackStrokeWidth = with(LocalDensity.current) { 12.dp.toPx() }
    val thumbTrackGap = with(LocalDensity.current) { thumbGap.toPx() }
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier,
        track = { sliderState ->
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val centerY = size.height / 2f
                val span = (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                    .takeIf { it > 0f } ?: 1f
                val fraction = ((sliderState.value - sliderState.valueRange.start) / span).coerceIn(0f, 1f)
                val thumbCenter = size.width * fraction
                // Paint the whole track with the unplayed colour first, so the
                // band around the thumb shows the track instead of whatever is
                // behind the slider. On the video player that background is
                // black, which used to leave black notches on both sides of the
                // thumb.
                drawLine(
                    color = inactiveColor,
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = trackStrokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = activeColor,
                    start = Offset(0f, centerY),
                    end = Offset((thumbCenter - thumbTrackGap).coerceAtLeast(0f), centerY),
                    strokeWidth = trackStrokeWidth,
                    cap = StrokeCap.Round
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
            Box(
                Modifier.size(16.dp)
                    .clip(CircleShape)
                    .background(thumbColor, CircleShape)
                    .border(4.dp, thumbBorderColor, CircleShape)
            )
        }
    )
}

package com.example.album.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat

fun resistedPullDistance(current: Float, delta: Float, maxDistance: Float): Float {
    if (delta < 0f) return (current + delta).coerceAtLeast(0f)
    if (delta == 0f) return current
    val resistance = 1f + current / maxDistance.coerceAtLeast(1f)
    return (current + delta / resistance).coerceAtMost(maxDistance)
}

@Composable
fun rememberPullRefreshConnection(
    enabled: Boolean,
    refreshing: Boolean,
    atTop: () -> Boolean,
    pullDistance: () -> Float,
    triggerDistance: Float,
    maxDistance: Float,
    onPullDistanceChange: (Float) -> Unit,
    onRelease: () -> Unit,
    onRefreshStarted: () -> Unit = {}
): NestedScrollConnection {
    val latestEnabled by rememberUpdatedState(enabled)
    val latestRefreshing by rememberUpdatedState(refreshing)
    val latestAtTop by rememberUpdatedState(atTop)
    val latestPullProvider by rememberUpdatedState(pullDistance)
    val latestTrigger by rememberUpdatedState(triggerDistance)
    val latestMaxDistance by rememberUpdatedState(maxDistance)
    val latestPullHandler by rememberUpdatedState(onPullDistanceChange)
    val latestRelease by rememberUpdatedState(onRelease)
    val latestStarted by rememberUpdatedState(onRefreshStarted)
    return androidx.compose.runtime.remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!latestEnabled || source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = available.y
                val pull = latestPullProvider()
                if (!latestRefreshing && delta > 0f && (pull > 0f || latestAtTop())) {
                    latestPullHandler(resistedPullDistance(pull, delta, latestMaxDistance))
                    return Offset(0f, delta)
                }
                if (!latestRefreshing && delta < 0f && pull > 0f) {
                    val newPull = (pull + delta).coerceAtLeast(0f)
                    val consumed = newPull - pull
                    latestPullHandler(newPull)
                    return Offset(0f, consumed)
                }
                if (latestRefreshing && pull > 0f && delta > 0f) {
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val pull = latestPullProvider()
                if (latestEnabled && pull > 0f) {
                    if (!latestRefreshing && pull >= latestTrigger) latestStarted()
                    else if (!latestRefreshing) latestRelease()
                    return available
                }
                return Velocity.Zero
            }
        }
    }
}

@Composable
fun PullRefreshIndicator(
    pullDistance: Float,
    refreshing: Boolean,
    triggerDistance: Float,
    modifier: Modifier = Modifier
) {
    val progress = (pullDistance / triggerDistance.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val rotation by rememberInfiniteTransition(label = "pull-refresh-rotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(720, easing = LinearEasing), RepeatMode.Restart),
        label = "pull-refresh-rotation"
    )
    AnimatedVisibility(
        visible = refreshing || progress > .02f,
        modifier = modifier.fillMaxWidth().zIndex(21f),
        enter = fadeIn(tween(160)) + scaleIn(tween(180), initialScale = .72f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = .72f)
    ) {
        Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.Center) {
            val accent = MaterialTheme.colorScheme.primary
            Canvas(Modifier.size(20.dp)) {
                drawArc(Color.Gray.copy(alpha = .38f), 0f, 360f, false, style = Stroke(2.dp.toPx()))
                drawArc(accent, if (refreshing) rotation - 90f else -90f + progress * 210f, if (refreshing) 105f else progress * 300f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Butt))
            }
        }
    }
}

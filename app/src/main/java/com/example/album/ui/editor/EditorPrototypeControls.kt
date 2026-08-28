package com.example.album.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

internal val EditorAccent: Color
    @Composable get() = MaterialTheme.colorScheme.primary
internal val EditorInk = Color(0xFF232725)
internal val EditorMuted = Color(0xFF777B79)
internal val EditorTile = Color(0xFFF0F0EE)
internal val EditorStageColor = Color(0xFFF6F6F4)

@Composable
internal fun EditorRuler(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    majorEvery: Int = 5,
    tickStep: Float = 1f,
    tickSpacing: Dp? = null,
    edgeInset: Dp = 14.dp,
    onValueChangeStarted: () -> Unit = {},
    onValueChangeFinished: () -> Unit = {}
) {
    val span = valueRange.endInclusive - valueRange.start
    val safeTickStep = tickStep.coerceAtLeast(.0001f)
    val sensitivity = 1.5f
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnStarted by rememberUpdatedState(onValueChangeStarted)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier.pointerInput(valueRange, tickStep, tickSpacing, edgeInset) {
            val inset = edgeInset.toPx().coerceAtMost(size.width / 4f)
            val trackWidth = (size.width - inset * 2f).coerceAtLeast(1f)
            val pixelsPerUnit = tickSpacing?.toPx()?.div(safeTickStep)
                ?: (trackWidth / span.coerceAtLeast(1f) * 4f / sensitivity)
            var liveValue = latestValue
            detectDragGestures(
                onDragStart = {
                    liveValue = latestValue
                    latestOnStarted()
                },
                onDrag = { change, drag ->
                    change.consume()
                    liveValue = (liveValue - drag.x / pixelsPerUnit).coerceIn(valueRange)
                    latestOnValueChange(liveValue)
                },
                onDragEnd = { latestOnFinished() },
                onDragCancel = { latestOnFinished() }
            )
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = size.width / 2f
            val inset = edgeInset.toPx().coerceAtMost(size.width / 4f)
            val trackWidth = (size.width - inset * 2f).coerceAtLeast(1f)
            val pixelsPerUnit = tickSpacing?.toPx()?.div(safeTickStep)
                ?: (trackWidth / span.coerceAtLeast(1f) * 4f / sensitivity)
            val first = kotlin.math.floor((value - center / pixelsPerUnit) / safeTickStep).toInt() - 1
            val last = kotlin.math.ceil((value + center / pixelsPerUnit) / safeTickStep).toInt() + 1
            for (tickIndex in first..last) {
                val tick = tickIndex * safeTickStep
                if (tick.toFloat() !in valueRange) continue
                val x = center + (tick - value) * pixelsPerUnit
                if (x < inset || x > size.width - inset) continue
                val major = tickIndex % majorEvery.coerceAtLeast(1) == 0
                val distance = (kotlin.math.abs(x - center) / (size.width / 2f)).coerceIn(0f, 1f)
                val centerWeight = 1f - distance
                val minHeight = if (major) .28f else .20f
                val maxHeight = if (major) .70f else .54f
                val tickHeight = size.height * (minHeight + (maxHeight - minHeight) * centerWeight)
                val tickCenter = size.height / 2f
                drawLine(
                    color = if (major) EditorInk else Color(0xFFAAAAA8),
                    start = Offset(x, tickCenter - tickHeight / 2f),
                    end = Offset(x, tickCenter + tickHeight / 2f),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawLine(
                accent,
                Offset(center, 0f),
                Offset(center, size.height),
                3.dp.toPx(),
                StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun EditorCenterCarousel(
    itemCount: Int,
    selectedIndex: Int,
    onCentered: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 68.dp,
    itemHeight: Dp = 74.dp,
    itemSpacing: Dp = 14.dp,
    centerLastItem: Boolean = true,
    showCenterOutline: Boolean = true,
    outlineWidth: Dp = 3.dp,
    content: @Composable (Int, onClick: () -> Unit) -> Unit
) {
    if (itemCount == 0) return
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = state,
        snapPosition = SnapPosition.Center
    )
    val outlineColor = EditorAccent
    val indices = remember(itemCount) { (0 until itemCount).toList() }
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val latestOnCentered by rememberUpdatedState(onCentered)
    BoxWithConstraints(
        modifier = modifier.drawWithContent {
            drawContent()
            if (showCenterOutline) {
                val frameWidth = itemWidth.toPx()
                val frameHeight = itemHeight.toPx()
                val strokeWidth = outlineWidth.toPx()
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(
                        (size.width - frameWidth) / 2f + strokeWidth / 2f,
                        strokeWidth / 2f
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        (frameWidth - strokeWidth).coerceAtLeast(0f),
                        (frameHeight - strokeWidth).coerceAtLeast(0f)
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
                )
            }
        },
        contentAlignment = Alignment.TopCenter
    ) {
        val side = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)
        val sidePx = with(LocalDensity.current) { side.roundToPx() }
        val maxSelectableIndex = if (centerLastItem) itemCount - 1 else (itemCount - 2).coerceAtLeast(0)
        var initialized by remember(itemCount, sidePx) { mutableStateOf(false) }
        LaunchedEffect(itemCount, sidePx, maxSelectableIndex) {
            snapshotFlow { state.layoutInfo.totalItemsCount }.first { it == itemCount }
            // contentPadding already places the item's start at the centered
            // inset. A second negative offset would move it past the outline.
            state.scrollToItem(latestSelectedIndex.coerceIn(0, maxSelectableIndex), 0)
            initialized = true
        }
        LaunchedEffect(selectedIndex, sidePx, maxSelectableIndex, initialized) {
            if (!initialized || state.isScrollInProgress) return@LaunchedEffect
            val target = selectedIndex.coerceIn(0, maxSelectableIndex)
            val center = (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2
            val current = state.layoutInfo.visibleItemsInfo.minByOrNull {
                kotlin.math.abs(it.offset + it.size / 2 - center)
            }?.index
            if (current != target) state.scrollToItem(target, 0)
        }
        LaunchedEffect(itemCount, sidePx, maxSelectableIndex, initialized) {
            if (!initialized) return@LaunchedEffect
            snapshotFlow { state.isScrollInProgress }
                .distinctUntilChanged()
                .collect { scrolling ->
                    if (scrolling) return@collect
                    val info = state.layoutInfo
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    val centeredItem = info.visibleItemsInfo.minByOrNull {
                        kotlin.math.abs(it.offset + it.size / 2 - center)
                    } ?: return@collect
                    val centered = centeredItem.index.coerceIn(0, maxSelectableIndex)
                    // The fling behavior chooses a nearby snap point, but a
                    // partially visible item can still stop between cards.
                    // Re-align its start to the side inset so the card center
                    // and the fixed green outline use exactly the same axis.
                    if (centeredItem.index != centered ||
                        kotlin.math.abs(centeredItem.offset + centeredItem.size / 2 - center) > 1
                    ) {
                        // The fling behavior has already animated the row. Use
                        // an immediate correction here so release never waits
                        // for a second default spring animation.
                        state.scrollToItem(centered, 0)
                    }
                    if (centered != latestSelectedIndex.coerceIn(0, maxSelectableIndex)) {
                        latestOnCentered(centered)
                    }
                }
        }
        LazyRow(
            state = state,
            flingBehavior = flingBehavior,
            userScrollEnabled = true,
            contentPadding = if (centerLastItem) PaddingValues(horizontal = side)
            // The trailing settings card is not selectable. Reduce the
            // trailing inset by one card slot so the last selectable option
            // is the physical end of the row and cannot scroll past the
            // green frame before snapping back.
            else PaddingValues(
                start = side,
                end = (side - itemWidth - itemSpacing).coerceAtLeast(0.dp)
            ),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            items(indices, key = { it }) { index ->
                Box(Modifier.width(itemWidth).height(itemHeight), contentAlignment = Alignment.Center) {
                    // Cards remain responsible for their own selection logic;
                    // this callback additionally brings a clicked card to the
                    // same centered position used by drag snapping.
                    content(index) {
                        scope.launch { state.scrollToItem(index, 0) }
                    }
                }
            }
        }
    }
}

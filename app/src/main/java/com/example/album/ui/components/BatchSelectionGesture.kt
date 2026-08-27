package com.example.album.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Adds a long-press range selector to a lazy grid. Items are addressed by
 * their visual lazy-grid order, which gives the requested left-to-right,
 * then top-to-bottom selection behavior.
 */
fun <T> Modifier.batchSelectionGesture(
    state: LazyGridState,
    items: List<T>,
    keyOf: (T) -> Any = { it as Any },
    onStart: (T) -> Unit,
    onSelectRange: (List<T>) -> Unit,
    onEnd: () -> Unit = {},
    enabled: Boolean = true
): Modifier = if (!enabled) this else composed {
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnSelectRange by rememberUpdatedState(onSelectRange)
    pointerInput(state, items, enabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startPosition = down.position
        val pointerId = down.id
        var finished = false
        var lastIndex = -1
        val visitedIndices = linkedSetOf<Int>()

        val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: return@withTimeoutOrNull false
                if (!change.pressed) return@withTimeoutOrNull false
                val moved = abs(change.position.x - startPosition.x) > viewConfiguration.touchSlop ||
                    abs(change.position.y - startPosition.y) > viewConfiguration.touchSlop
                if (moved) return@withTimeoutOrNull false
            }
            false
        } ?: true

        if (!longPressed) {
            return@awaitEachGesture
        }

        fun itemAt(x: Float, y: Float): Int? {
            val visible = state.layoutInfo.visibleItemsInfo
            return visible.filter { info -> items.any { keyOf(it) == info.key } }.minByOrNull { info ->
                val left = info.offset.x.toFloat()
                val top = info.offset.y.toFloat()
                val right = left + info.size.width
                val bottom = top + info.size.height
                when {
                    x in left..right && y in top..bottom -> 0f
                    else -> {
                        val dx = when {
                            x < left -> left - x
                            x > right -> x - right
                            else -> 0f
                        }
                        val dy = when {
                            y < top -> top - y
                            y > bottom -> y - bottom
                            else -> 0f
                        }
                        dx * dx + dy * dy
                    }
                }
            }?.let { info -> items.indexOfFirst { keyOf(it) == info.key }.takeIf { it >= 0 } }
        }

        val startIndex = itemAt(startPosition.x, startPosition.y)
        if (startIndex == null || startIndex !in items.indices) return@awaitEachGesture
        lastIndex = startIndex
        visitedIndices += startIndex
        currentOnStart(items[startIndex])

        while (!finished) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null) break
            change.consume()
            val currentIndex = itemAt(change.position.x, change.position.y)
                ?.coerceIn(items.indices)
            if (currentIndex != null && currentIndex != lastIndex) {
                lastIndex = currentIndex
                val from = minOf(startIndex, currentIndex)
                val to = maxOf(startIndex, currentIndex)
                val fresh = (from..to).filter { visitedIndices.add(it) }.map(items::get)
                if (fresh.isNotEmpty()) currentOnSelectRange(fresh)
            }
            val edge = 72f
            val viewportHeight = state.layoutInfo.viewportEndOffset.toFloat()
            val scrollDelta = when {
                change.position.y < edge -> -(edge - change.position.y) * .22f
                change.position.y > viewportHeight - edge -> (change.position.y - (viewportHeight - edge)) * .22f
                else -> 0f
            }
            if (scrollDelta != 0f) state.dispatchRawDelta(scrollDelta)
            if (!change.pressed) finished = true
        }
        onEnd()
    }
}
}

private suspend fun <T> PointerInputScope.awaitBatchSelection(
    state: LazyListState,
    items: List<T>,
    keyOf: (T) -> Any,
    onStart: (T) -> Unit,
    onSelectRange: (List<T>) -> Unit,
    onEnd: () -> Unit,
    canStartAt: (Float, Float) -> Boolean,
    viewConfiguration: androidx.compose.ui.platform.ViewConfiguration
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val pointerId = down.id
        val startPosition = down.position
        val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == pointerId }
                    ?: return@withTimeoutOrNull false
                if (!change.pressed) return@withTimeoutOrNull false
                if (abs(change.position.x - startPosition.x) > viewConfiguration.touchSlop ||
                    abs(change.position.y - startPosition.y) > viewConfiguration.touchSlop
                ) return@withTimeoutOrNull false
            }
            false
        } ?: true
        if (!longPressed) return@awaitEachGesture
        if (!canStartAt(startPosition.x, startPosition.y)) return@awaitEachGesture

        fun itemAt(x: Float, y: Float): Int? = state.layoutInfo.visibleItemsInfo
            .filter { info -> y in info.offset.toFloat()..(info.offset + info.size).toFloat() }
            .firstNotNullOfOrNull { info -> items.indexOfFirst { keyOf(it) == info.key }.takeIf { it >= 0 } }
        val startIndex = itemAt(startPosition.x, startPosition.y) ?: return@awaitEachGesture
        val visited = linkedSetOf(startIndex)
        var lastIndex = startIndex
        onStart(items[startIndex])
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == pointerId } ?: break
            change.consume()
            val current = itemAt(change.position.x, change.position.y)
            if (current != null && current != lastIndex) {
                lastIndex = current
                val fresh = (minOf(startIndex, current)..maxOf(startIndex, current))
                    .filter { visited.add(it) }.map(items::get)
                if (fresh.isNotEmpty()) onSelectRange(fresh)
            }
            if (!change.pressed) break
        }
        onEnd()
    }
}

private suspend fun <T> PointerInputScope.awaitBatchSelection(
    state: LazyStaggeredGridState,
    items: List<T>,
    keyOf: (T) -> Any,
    onStart: (T) -> Unit,
    onSelectRange: (List<T>) -> Unit,
    onEnd: () -> Unit,
    viewConfiguration: androidx.compose.ui.platform.ViewConfiguration
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val pointerId = down.id
        val startPosition = down.position
        val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == pointerId }
                    ?: return@withTimeoutOrNull false
                if (!change.pressed) return@withTimeoutOrNull false
                if (abs(change.position.x - startPosition.x) > viewConfiguration.touchSlop ||
                    abs(change.position.y - startPosition.y) > viewConfiguration.touchSlop
                ) return@withTimeoutOrNull false
            }
            false
        } ?: true
        if (!longPressed) return@awaitEachGesture
        fun itemAt(x: Float, y: Float): Int? = state.layoutInfo.visibleItemsInfo
            .filter { info -> x in info.offset.x.toFloat()..(info.offset.x + info.size.width).toFloat() &&
                y in info.offset.y.toFloat()..(info.offset.y + info.size.height).toFloat() }
            .firstNotNullOfOrNull { info -> items.indexOfFirst { keyOf(it) == info.key }.takeIf { it >= 0 } }
        val startIndex = itemAt(startPosition.x, startPosition.y) ?: return@awaitEachGesture
        val visited = linkedSetOf(startIndex)
        var lastIndex = startIndex
        onStart(items[startIndex])
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == pointerId } ?: break
            change.consume()
            val current = itemAt(change.position.x, change.position.y)
            if (current != null && current != lastIndex) {
                lastIndex = current
                val fresh = (minOf(startIndex, current)..maxOf(startIndex, current))
                    .filter { visited.add(it) }.map(items::get)
                if (fresh.isNotEmpty()) onSelectRange(fresh)
            }
            if (!change.pressed) break
        }
        onEnd()
    }
}

fun <T> Modifier.batchSelectionListGesture(
    state: LazyListState,
    items: List<T>,
    keyOf: (T) -> Any,
    onStart: (T) -> Unit,
    onSelectRange: (List<T>) -> Unit,
    onEnd: () -> Unit = {},
    canStartAt: (Float, Float) -> Boolean = { _, _ -> true },
    enabled: Boolean = true
): Modifier = if (!enabled) this else composed {
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnSelectRange by rememberUpdatedState(onSelectRange)
    pointerInput(state, items, enabled) {
        awaitBatchSelection(state, items, keyOf, currentOnStart, currentOnSelectRange, onEnd, canStartAt, viewConfiguration)
    }
}

fun <T> Modifier.batchSelectionStaggeredGesture(
    state: LazyStaggeredGridState,
    items: List<T>,
    keyOf: (T) -> Any,
    onStart: (T) -> Unit,
    onSelectRange: (List<T>) -> Unit,
    onEnd: () -> Unit = {},
    enabled: Boolean = true
): Modifier = if (!enabled) this else composed {
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnSelectRange by rememberUpdatedState(onSelectRange)
    pointerInput(state, items, enabled) {
        awaitBatchSelection(state, items, keyOf, currentOnStart, currentOnSelectRange, onEnd, viewConfiguration)
    }
}

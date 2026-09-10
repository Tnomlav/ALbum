package com.example.album.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Adds a long-press range selector to a lazy grid. Items are addressed by
 * their visual lazy-grid order, which gives the requested left-to-right,
 * then top-to-bottom selection behavior.
 *
 * The pointer input intentionally only keys on the lazy state: entering
 * selection mode mutates the item and selection lists while the finger is
 * still down, and restarting the gesture at that point would silently break
 * the range drag. The latest values are read through `rememberUpdatedState`
 * instead.
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
    val latestItems by rememberUpdatedState(items)
    val latestKeyOf by rememberUpdatedState(keyOf)
    val latestOnStart by rememberUpdatedState(onStart)
    val latestOnSelectRange by rememberUpdatedState(onSelectRange)
    val latestOnEnd by rememberUpdatedState(onEnd)
    pointerInput(state, enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!awaitLongPress(down)) return@awaitEachGesture

            val startPosition = down.position
            fun itemAt(x: Float, y: Float): Int? {
                val currentItems = latestItems
                val currentKeyOf = latestKeyOf
                val visible = state.layoutInfo.visibleItemsInfo
                return visible.filter { info -> currentItems.any { currentKeyOf(it) == info.key } }.minByOrNull { info ->
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
                }?.let { info -> latestItems.indexOfFirst { latestKeyOf(it) == info.key }.takeIf { it >= 0 } }
            }

            val startIndex = itemAt(startPosition.x, startPosition.y)
            if (startIndex == null || startIndex !in latestItems.indices) return@awaitEachGesture
            var lastIndex = startIndex
            val visitedIndices = linkedSetOf(startIndex)
            latestOnStart(latestItems[startIndex])

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                val currentIndex = itemAt(change.position.x, change.position.y)
                    ?.coerceIn(latestItems.indices)
                if (currentIndex != null && currentIndex != lastIndex) {
                    lastIndex = currentIndex
                    val from = minOf(startIndex, currentIndex)
                    val to = maxOf(startIndex, currentIndex)
                    val fresh = (from..to).filter { visitedIndices.add(it) }.map(latestItems::get)
                    if (fresh.isNotEmpty()) latestOnSelectRange(fresh)
                }
                val edge = 72f
                val viewportHeight = state.layoutInfo.viewportEndOffset.toFloat()
                val scrollDelta = when {
                    change.position.y < edge -> -(edge - change.position.y) * .22f
                    change.position.y > viewportHeight - edge -> (change.position.y - (viewportHeight - edge)) * .22f
                    else -> 0f
                }
                if (scrollDelta != 0f) state.dispatchRawDelta(scrollDelta)
                if (!change.pressed) break
            }
            latestOnEnd()
        }
    }
}

/**
 * Shared long-press wait. Returns true only when the finger stayed inside the
 * touch slop for the whole long-press timeout.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitLongPress(
    down: androidx.compose.ui.input.pointer.PointerInputChange
): Boolean {
    val startPosition = down.position
    val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                ?: return@withTimeoutOrNull false
            if (!change.pressed) return@withTimeoutOrNull false
            val moved = abs(change.position.x - startPosition.x) > viewConfiguration.touchSlop ||
                abs(change.position.y - startPosition.y) > viewConfiguration.touchSlop
            if (moved) return@withTimeoutOrNull false
        }
        false
    } ?: true
    return longPressed
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
    val latestItems by rememberUpdatedState(items)
    val latestKeyOf by rememberUpdatedState(keyOf)
    val latestOnStart by rememberUpdatedState(onStart)
    val latestOnSelectRange by rememberUpdatedState(onSelectRange)
    val latestOnEnd by rememberUpdatedState(onEnd)
    pointerInput(state, enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!awaitLongPress(down)) return@awaitEachGesture
            if (!canStartAt(down.position.x, down.position.y)) return@awaitEachGesture
            val startIndex = latestItems.indexOfFirst { item ->
                state.layoutInfo.visibleItemsInfo.any { info ->
                    latestKeyOf(item) == info.key &&
                        down.position.y in info.offset.toFloat()..(info.offset + info.size).toFloat()
                }
            }
            if (startIndex < 0) return@awaitEachGesture
            val visited = linkedSetOf(startIndex)
            var lastIndex = startIndex
            latestOnStart(latestItems[startIndex])
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                val current = latestItems.indexOfFirst { item ->
                    state.layoutInfo.visibleItemsInfo.any { info ->
                        latestKeyOf(item) == info.key &&
                            change.position.y in info.offset.toFloat()..(info.offset + info.size).toFloat()
                    }
                }
                if (current >= 0 && current != lastIndex) {
                    lastIndex = current
                    val fresh = (minOf(startIndex, current)..maxOf(startIndex, current))
                        .filter { visited.add(it) }.map(latestItems::get)
                    if (fresh.isNotEmpty()) latestOnSelectRange(fresh)
                }
                if (!change.pressed) break
            }
            latestOnEnd()
        }
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
    val latestItems by rememberUpdatedState(items)
    val latestKeyOf by rememberUpdatedState(keyOf)
    val latestOnStart by rememberUpdatedState(onStart)
    val latestOnSelectRange by rememberUpdatedState(onSelectRange)
    val latestOnEnd by rememberUpdatedState(onEnd)
    pointerInput(state, enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!awaitLongPress(down)) return@awaitEachGesture
            fun itemAt(x: Float, y: Float): Int? = state.layoutInfo.visibleItemsInfo
                .filter { info ->
                    x in info.offset.x.toFloat()..(info.offset.x + info.size.width).toFloat() &&
                        y in info.offset.y.toFloat()..(info.offset.y + info.size.height).toFloat()
                }
                .firstNotNullOfOrNull { info -> latestItems.indexOfFirst { latestKeyOf(it) == info.key }.takeIf { it >= 0 } }
            val startIndex = itemAt(down.position.x, down.position.y) ?: return@awaitEachGesture
            val visited = linkedSetOf(startIndex)
            var lastIndex = startIndex
            latestOnStart(latestItems[startIndex])
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                val current = itemAt(change.position.x, change.position.y)
                if (current != null && current != lastIndex) {
                    lastIndex = current
                    val fresh = (minOf(startIndex, current)..maxOf(startIndex, current))
                        .filter { visited.add(it) }.map(latestItems::get)
                    if (fresh.isNotEmpty()) latestOnSelectRange(fresh)
                }
                if (!change.pressed) break
            }
            latestOnEnd()
        }
    }
}

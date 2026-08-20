package com.example.album.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.example.album.data.MediaItem
import com.example.album.data.ThumbnailRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

private data class VisibleMediaWindow(val first: Int, val last: Int)

@Composable
fun LazyGridMediaPrefetch(
    state: LazyGridState,
    items: List<MediaItem>,
    requestedSize: Int = 360,
    keySelector: (MediaItem) -> Any = { it.uri.toString() }
) {
    MediaPrefetchEffect(
        items = items,
        requestedSize = requestedSize,
        keySelector = keySelector,
        visibleKeys = { state.layoutInfo.visibleItemsInfo.map { it.key } }
    )
}

@Composable
fun LazyStaggeredGridMediaPrefetch(
    state: LazyStaggeredGridState,
    items: List<MediaItem>,
    requestedSize: Int = 360,
    keySelector: (MediaItem) -> Any = { it.uri.toString() }
) {
    MediaPrefetchEffect(
        items = items,
        requestedSize = requestedSize,
        keySelector = keySelector,
        visibleKeys = { state.layoutInfo.visibleItemsInfo.map { it.key } }
    )
}

@Composable
private fun MediaPrefetchEffect(
    items: List<MediaItem>,
    requestedSize: Int,
    keySelector: (MediaItem) -> Any,
    visibleKeys: () -> List<Any>
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("album_settings", android.content.Context.MODE_PRIVATE)
    val latestVisibleKeys by rememberUpdatedState(visibleKeys)
    val latestKeySelector by rememberUpdatedState(keySelector)

    LaunchedEffect(items, requestedSize, context) {
        val indexByKey = items.mapIndexed { index, item -> latestKeySelector(item) to index }.toMap()
        var previousFirst = -1
        snapshotFlow { latestVisibleKeys() }
            .map { keys ->
                val indexes = keys.mapNotNull(indexByKey::get)
                if (indexes.isEmpty()) null else VisibleMediaWindow(indexes.min(), indexes.max())
            }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { window ->
                val forward = previousFirst < 0 || window.first >= previousFirst
                previousFirst = window.first
                val visibleCount = (window.last - window.first + 1).coerceAtLeast(1)
                val prefetchCount = (visibleCount * 2).coerceIn(16, 36)
                val candidates = if (forward) {
                    items.subList(
                        (window.last + 1).coerceAtMost(items.size),
                        (window.last + 1 + prefetchCount).coerceAtMost(items.size)
                    )
                } else {
                    items.subList((window.first - prefetchCount).coerceAtLeast(0), window.first)
                        .asReversed()
                }
                ThumbnailRepository.prefetch(
                    context,
                    candidates.distinctBy { it.uri },
                    requestedSize,
                    preferences
                )
            }
    }
}

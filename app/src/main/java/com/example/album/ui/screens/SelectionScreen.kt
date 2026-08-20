package com.example.album.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.album.data.MediaItem
import com.example.album.data.MediaAlbum
import com.example.album.ui.components.MediaThumbnail
import com.example.album.ui.components.LazyGridMediaPrefetch
import com.example.album.ui.theme.VaultDimens
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.MediaSort
import com.example.album.ui.SortDirection

@Composable
fun SelectionScreen(
    media: List<MediaItem>,
    selectedUris: Set<String>,
    columns: Int,
    onToggle: (MediaItem) -> Unit,
    sort: MediaSort = MediaSort.Time,
    sortDirection: SortDirection = SortDirection.Descending
) {
    val orderedMedia = remember(media, sort, sortDirection) {
        val sorted = when (sort) {
            MediaSort.Time, MediaSort.Count -> media.sortedBy { it.dateTaken }
            MediaSort.Name -> media.sortedBy { it.name.lowercase() }
            MediaSort.Size -> media.sortedBy { it.size }
            MediaSort.Duration -> media.sortedBy { it.duration }
        }
        if (sortDirection == SortDirection.Descending) sorted.reversed() else sorted
    }
    val gridState = rememberLazyGridState()
    LazyGridMediaPrefetch(gridState, orderedMedia)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceIn(1, 6)),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)
    ) {
        items(orderedMedia, key = { it.uri.toString() }) { item ->
            val selected = item.uri.toString() in selectedUris
            val markScale by animateFloatAsState(if (selected) 1f else .82f, tween(120), label = "selection-scale")
            val markColor by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .75f),
                tween(120),
                label = "selection-color"
            )
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable { onToggle(item) }) {
                MediaThumbnail(item, Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).graphicsLayer { scaleX = markScale; scaleY = markScale },
                    shape = CircleShape,
                    color = markColor,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                ) {
                    Box(Modifier.size(VaultDimens.SelectionMarkSize), contentAlignment = Alignment.Center) {
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = .7f),
                            exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = .7f)
                        ) {
                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumSelectionScreen(
    media: List<MediaItem>,
    selectedFolders: Set<String>,
    columns: Int,
    onToggle: (String) -> Unit,
    sort: MediaSort = MediaSort.Time,
    sortDirection: SortDirection = SortDirection.Descending,
    initialFirstVisibleItem: Int = 0,
    initialFirstVisibleOffset: Int = 0,
    onScrollPositionChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    val english = LocalAppEnglish.current
    val albums = remember(media, sort, sortDirection) {
        val grouped = media.groupBy { it.folder }.map { (name, items) ->
            val sortedItems = when (sort) {
                MediaSort.Time, MediaSort.Count -> items.sortedBy { it.dateTaken }
                MediaSort.Name -> items.sortedBy { it.name.lowercase() }
                MediaSort.Size -> items.sortedBy { it.size }
                MediaSort.Duration -> items.sortedBy { it.duration }
            }.let { if (sortDirection == SortDirection.Descending) it.reversed() else it }
            MediaAlbum(name, sortedItems, sortedItems.firstOrNull())
        }
        val sorted = when (sort) {
            MediaSort.Time -> grouped.sortedBy { it.cover.dateTaken }
            MediaSort.Name -> grouped.sortedBy { it.name.lowercase() }
            MediaSort.Size -> grouped.sortedBy { album -> album.items.sumOf { it.size } }
            MediaSort.Count -> grouped.sortedBy { it.items.size }
            MediaSort.Duration -> grouped.sortedBy { album -> album.items.sumOf { it.duration } }
        }
        if (sortDirection == SortDirection.Descending) sorted.reversed() else sorted
    }
    val covers = remember(albums) { albums.map(MediaAlbum::cover) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) -> onScrollPositionChanged(index, offset) }
    }
    LaunchedEffect(albums, initialFirstVisibleItem, initialFirstVisibleOffset) {
        if (albums.isNotEmpty()) {
            gridState.scrollToItem(
                initialFirstVisibleItem.coerceIn(0, albums.lastIndex),
                initialFirstVisibleOffset.coerceAtLeast(0)
            )
        }
    }
    LazyGridMediaPrefetch(gridState, covers, keySelector = MediaItem::folder)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceIn(1, 6)),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(VaultDimens.AlbumGap),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(VaultDimens.AlbumGap)
    ) {
        items(albums, key = { it.name }) { album ->
            val selected = album.name in selectedFolders
            Column(Modifier.clickable { onToggle(album.name) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    MediaThumbnail(album.cover, Modifier.fillMaxSize())
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .75f),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                    ) {
                        Box(Modifier.size(VaultDimens.SelectionMarkSize), contentAlignment = Alignment.Center) {
                            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Text(album.name, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
                Text(if (english) "${album.items.size} items" else "${album.items.size} 项", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

package com.example.album.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.album.data.MediaItem
import com.example.album.data.MediaAlbum
import com.example.album.ui.components.MediaThumbnail
import com.example.album.ui.components.LazyGridMediaPrefetch
import com.example.album.ui.components.LazyStaggeredGridMediaPrefetch
import com.example.album.ui.components.batchSelectionGesture
import com.example.album.ui.components.batchSelectionStaggeredGesture
import com.example.album.ui.theme.VaultDimens
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.MediaSort
import com.example.album.ui.SortDirection
import com.example.album.ui.searchAlbums
import com.example.album.ui.filterAlbums
import com.example.album.ui.appText
import com.example.album.ui.MediaLayout
import com.example.album.data.displayAspectRatio
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SelectionScreen(
    media: List<MediaItem>,
    orderUris: List<String> = emptyList(),
    selectedUris: Set<String>,
    columns: Int,
    onToggle: (MediaItem) -> Unit,
    topTrailingCount: Int? = null,
    query: String = "",
    searching: Boolean = false,
    sort: MediaSort = MediaSort.Time,
    sortDirection: SortDirection = SortDirection.Descending,
    showDateHeaders: Boolean = false,
    layout: MediaLayout = MediaLayout.Grid,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 3.dp,
    anchorUri: String? = null,
    initialFirstVisibleItem: Int = 0,
    initialFirstVisibleOffset: Int = 0,
    onScrollPositionChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    val sortedMedia = remember(media, sort, sortDirection) {
        val sorted = when (sort) {
            MediaSort.Time, MediaSort.Count -> media.sortedBy { it.dateTaken }
            MediaSort.Name -> media.sortedBy { it.name.lowercase() }
            MediaSort.Size -> media.sortedBy { it.size }
            MediaSort.Duration -> media.sortedBy { it.duration }
        }
        if (sortDirection == SortDirection.Descending) sorted.reversed() else sorted
    }
    val orderedMedia = remember(media, sortedMedia, orderUris) {
        if (orderUris.isEmpty()) sortedMedia else {
            val positions = orderUris.withIndex().associate { it.value to it.index }
            val sourcePositions = media.withIndex().associate { it.value.uri.toString() to it.index }
            // Preserve the frozen pre-selection order exactly. Items that
            // appeared after selection started are appended in source order.
            media.sortedWith(
                compareBy<MediaItem> { positions[it.uri.toString()] ?: Int.MAX_VALUE }
                    .thenBy { sourcePositions[it.uri.toString()] ?: Int.MAX_VALUE }
            )
        }
    }
    val english = LocalAppEnglish.current
    if (searching && query.isNotBlank() && orderedMedia.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (query.isNotBlank() && orderedMedia.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(appText("没有匹配的内容", english), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val dateFormatter = remember { SimpleDateFormat("yyyy年M月d日", Locale.CHINA) }
    val dateSections = remember(orderedMedia, dateFormatter) {
        orderedMedia.groupBy { dateFormatter.format(Date(it.dateTaken)) }.entries.toList()
    }
    // Locate the item that was on screen before selection started. Lists can
    // differ (Pixiv adds a navigation row, staggered layouts add headers), so
    // the anchor is the media URI, not a raw index.
    val anchorIndex = remember(orderedMedia, dateSections, showDateHeaders, topTrailingCount, anchorUri) {
        val uri = anchorUri ?: return@remember initialFirstVisibleItem.coerceAtLeast(0)
        var index = if (topTrailingCount != null) 1 else 0
        if (showDateHeaders) {
            for ((_, items) in dateSections) {
                index += 1
                val position = items.indexOfFirst { it.uri.toString() == uri }
                if (position >= 0) return@remember index + position
                index += items.size
            }
            index
        } else {
            val position = orderedMedia.indexOfFirst { it.uri.toString() == uri }
            if (position >= 0) index + position else index
        }
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = anchorIndex,
        initialFirstVisibleItemScrollOffset = initialFirstVisibleOffset.coerceAtLeast(0)
    )
    val staggeredState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState(
        initialFirstVisibleItemIndex = anchorIndex,
        initialFirstVisibleItemScrollOffset = initialFirstVisibleOffset.coerceAtLeast(0)
    )
    if (layout == MediaLayout.Adaptive) {
        LaunchedEffect(staggeredState) {
            snapshotFlow { staggeredState.firstVisibleItemIndex to staggeredState.firstVisibleItemScrollOffset }
                .sample(80L)
                .collectLatest { (index, offset) -> onScrollPositionChanged(index, offset) }
        }
        LazyStaggeredGridMediaPrefetch(staggeredState, orderedMedia)
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns.coerceIn(1, 6)),
            state = staggeredState,
            modifier = Modifier.fillMaxSize().batchSelectionStaggeredGesture(
                state = staggeredState,
                items = orderedMedia,
                keyOf = { it.uri.toString() },
                onStart = { item -> if (item.uri.toString() !in selectedUris) onToggle(item) },
                onSelectRange = { range -> range.forEach { if (it.uri.toString() !in selectedUris) onToggle(it) } }
            ),
            contentPadding = contentPadding,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(itemSpacing),
            verticalItemSpacing = itemSpacing
        ) {
            selectionStaggeredItems(
                orderedMedia = orderedMedia,
                dateSections = dateSections,
                showDateHeaders = showDateHeaders,
                topTrailingCount = topTrailingCount,
                selectedUris = selectedUris,
                onToggle = onToggle
            )
        }
        return
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .sample(80L)
            .collectLatest { (index, offset) -> onScrollPositionChanged(index, offset) }
    }
    LazyGridMediaPrefetch(gridState, orderedMedia)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceIn(1, 6)),
        state = gridState,
        modifier = Modifier.fillMaxSize().batchSelectionGesture(
            state = gridState,
            items = orderedMedia,
            keyOf = { it.uri.toString() },
            onStart = { item -> if (item.uri.toString() !in selectedUris) onToggle(item) },
            onSelectRange = { range -> range.forEach { if (it.uri.toString() !in selectedUris) onToggle(it) } }
        ),
        contentPadding = contentPadding,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(itemSpacing),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(itemSpacing)
    ) {
        topTrailingCount?.let { count ->
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(
                    count.toString(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
        if (showDateHeaders) dateSections.forEach { (date, sectionItems) ->
            item(key = "selection-date:$date", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 38.dp)
                        .padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 5.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        timelineDateLabel(sectionItems.first().dateTaken, english),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        sectionItems.size.toString(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            items(sectionItems, key = { it.uri.toString() }) { item ->
                SelectionMediaCell(item, selectedUris, onToggle, item.displayAspectRatio)
            }
        } else items(orderedMedia, key = { it.uri.toString() }) { item ->
            SelectionMediaCell(item, selectedUris, onToggle, 1f)
        }
    }
}

private fun androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope.selectionStaggeredItems(
    orderedMedia: List<MediaItem>,
    dateSections: List<Map.Entry<String, List<MediaItem>>>,
    showDateHeaders: Boolean,
    topTrailingCount: Int?,
    selectedUris: Set<String>,
    onToggle: (MediaItem) -> Unit
) {
    topTrailingCount?.let { count ->
        item(span = StaggeredGridItemSpan.FullLine) {
            Text(
                count.toString(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
    if (showDateHeaders) {
        dateSections.forEach { (date, sectionItems) ->
            item(key = "selection-date:$date", span = StaggeredGridItemSpan.FullLine) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 38.dp)
                        .padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(timelineDateLabel(sectionItems.first().dateTaken, LocalAppEnglish.current), style = MaterialTheme.typography.bodyMedium)
                    Text(sectionItems.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            items(sectionItems, key = { it.uri.toString() }) { item ->
                SelectionMediaCell(item, selectedUris, onToggle, item.displayAspectRatio)
            }
        }
    } else {
        items(orderedMedia, key = { it.uri.toString() }) { item ->
            SelectionMediaCell(item, selectedUris, onToggle, item.displayAspectRatio)
        }
    }
}

@Composable
private fun SelectionMediaCell(
    item: MediaItem,
    selectedUris: Set<String>,
    onToggle: (MediaItem) -> Unit,
    aspectRatio: Float = 1f
) {
    val selected = item.uri.toString() in selectedUris
    val markScale by animateFloatAsState(if (selected) 1f else .82f, tween(120), label = "selection-scale")
    val markColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .75f),
        tween(120),
        label = "selection-color"
    )
    Box(Modifier.fillMaxWidth().aspectRatio(aspectRatio.coerceIn(.45f, 2.4f)).clickable { onToggle(item) }) {
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

@Composable
fun AlbumSelectionScreen(
    media: List<MediaItem>,
    selectedFolders: Set<String>,
    columns: Int,
    onToggle: (String) -> Unit,
    sort: MediaSort = MediaSort.Time,
    sortDirection: SortDirection = SortDirection.Descending,
    additionalFolderNames: Set<String> = emptySet(),
    additionalFileNames: Map<String, Set<String>> = emptyMap(),
    pinnedFolderName: String? = null,
    query: String = "",
    searchingFolders: Boolean = false,
    anchorFolder: String? = null,
    initialFirstVisibleItem: Int = 0,
    initialFirstVisibleOffset: Int = 0,
    onScrollPositionChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    val english = LocalAppEnglish.current
    val allAlbums = remember(media, sort, sortDirection, additionalFolderNames, additionalFileNames, pinnedFolderName) {
        searchAlbums(
            media = media,
            query = "",
            sort = sort,
            sortDirection = sortDirection,
            additionalFolderNames = additionalFolderNames,
            additionalFileNames = additionalFileNames,
            pinnedFolderName = pinnedFolderName
        )
    }
    val albums = remember(allAlbums, query, additionalFileNames, pinnedFolderName) {
        filterAlbums(allAlbums, query, additionalFileNames, pinnedFolderName)
    }
    val covers = remember(albums) { albums.mapNotNull(MediaAlbum::coverItem) }
    // Match the album page exactly (outer 7 dp padding, 8/20 vertical content
    // padding) and start at the same scroll position, so entering selection
    // does not resize the tiles or jump the list.
    val anchorIndex = remember(albums, anchorFolder) {
        val index = anchorFolder?.let { name -> albums.indexOfFirst { it.name == name } } ?: -1
        if (index >= 0) index else initialFirstVisibleItem.coerceAtLeast(0)
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = anchorIndex,
        initialFirstVisibleItemScrollOffset = initialFirstVisibleOffset.coerceAtLeast(0)
    )
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .sample(80L)
            .collectLatest { (index, offset) -> onScrollPositionChanged(index, offset) }
    }
    if (searchingFolders && query.isNotBlank() && albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (query.isNotBlank() && albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(appText("没有匹配的内容", english), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyGridMediaPrefetch(gridState, covers, keySelector = MediaItem::folder)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceIn(1, 6)),
        state = gridState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp).batchSelectionGesture(
            state = gridState,
            items = albums,
            keyOf = { it.name },
            onStart = { album -> if (album.name !in selectedFolders) onToggle(album.name) },
            onSelectRange = { range -> range.forEach { if (it.name !in selectedFolders) onToggle(it.name) } }
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 20.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(VaultDimens.AlbumGap),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(VaultDimens.AlbumGap)
    ) {
        items(albums, key = { it.name }) { album ->
            val selected = album.name in selectedFolders
            Column(Modifier.fillMaxWidth().clickable { onToggle(album.name) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                        album.coverItem?.let { cover ->
                        MediaThumbnail(
                            cover,
                            Modifier.fillMaxSize().clip(RoundedCornerShape(VaultDimens.AlbumRadius)),
                            showVideoDuration = false
                        )
                    } ?: Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(VaultDimens.AlbumRadius)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize(.34f)
                        )
                    }
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 7.dp, end = 2.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        album.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = VaultDimens.AlbumName
                    )
                    Text(
                        album.items.size.toString(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = VaultDimens.AlbumCount
                    )
                }
            }
        }
    }
}

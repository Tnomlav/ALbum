package com.example.album.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import com.example.album.data.MediaAlbum
import com.example.album.data.MediaItem
import com.example.album.data.displayAspectRatio
import com.example.album.ui.components.AlbumTile
import com.example.album.ui.components.MediaThumbnail
import com.example.album.ui.components.PressableMediaThumbnail
import com.example.album.ui.components.PullRefreshIndicator
import com.example.album.ui.components.rememberPullRefreshConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.album.ui.components.ListScrollHandle
import com.example.album.ui.components.LazyGridMediaPrefetch
import com.example.album.ui.components.LazyStaggeredGridMediaPrefetch
import com.example.album.ui.components.LocalMediaAnimatedVisibilityScope
import com.example.album.ui.components.LocalActiveSharedMediaKey
import com.example.album.ui.MediaSort
import com.example.album.ui.SortDirection
import com.example.album.ui.MediaLayout
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.PixivArchiveNavigation
import com.example.album.ui.theme.VaultDimens
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@Composable
fun AlbumsScreen(
    media: List<MediaItem>,
    isVideo: Boolean,
    query: String,
    loading: Boolean,
    scanning: Boolean = false,
    permissionGranted: Boolean,
    sort: MediaSort,
    sortDirection: SortDirection,
    albumColumns: Int,
    folderColumns: Int,
    layout: MediaLayout,
    onRequestPermission: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onLongPressMedia: (MediaItem) -> Unit,
    onLongPressAlbum: (MediaAlbum, Int, Int) -> Unit,
    onRefresh: () -> Unit,
    openedFolder: String?,
    onOpenedFolderChange: (String?) -> Unit,
    onVisibleScopeChanged: (List<MediaItem>?) -> Unit = {},
    sharedElementEnabled: Boolean = true,
    onOpenPixivArchive: (() -> Unit)? = null,
    pinnedAlbumName: String? = null,
    albumQueryMatchesItems: Boolean = true,
    flatMode: Boolean = false,
    emptyMessage: String? = null,
    favoriteUris: Set<String> = emptySet()
    ,showFavoriteBadge: Boolean = true,
    additionalAlbumNames: Set<String> = emptySet()
    ,initialAlbumFirstVisibleItem: Int = 0
    ,initialAlbumFirstVisibleOffset: Int = 0,
    onClearQuery: () -> Unit = {}
) {
    val refreshing = loading || scanning
    val english = LocalAppEnglish.current
    fun itemsInDisplayOrder(items: List<MediaItem>): List<MediaItem> {
        val base = when (sort) {
            MediaSort.Time -> items.sortedBy { it.dateTaken }
            MediaSort.Name -> items.sortedBy { it.name.lowercase() }
            MediaSort.Size -> items.sortedBy { it.size }
            MediaSort.Count -> items.sortedBy { it.dateTaken }
            MediaSort.Duration -> items.sortedBy { it.duration }
        }
        return if (sortDirection == SortDirection.Descending) base.reversed() else base
    }
    val albums = remember(media, query, sort, sortDirection, albumQueryMatchesItems, additionalAlbumNames, pinnedAlbumName) {
        val groupedItems = media.groupBy { it.folder }
        val grouped = (groupedItems.keys + additionalAlbumNames).distinct()
            .map { name ->
                val orderedItems = itemsInDisplayOrder(groupedItems[name].orEmpty())
                MediaAlbum(name, orderedItems, orderedItems.firstOrNull())
            }
            .filter { album -> query.isBlank() || album.name.contains(query, true) || (albumQueryMatchesItems && album.items.any { it.name.contains(query, true) }) }
        val sorted = when (sort) {
            MediaSort.Time -> grouped.sortedBy { it.coverItem?.dateTaken ?: Long.MIN_VALUE }
            MediaSort.Name -> grouped.sortedBy { it.name.lowercase() }
            MediaSort.Size -> grouped.sortedBy { album -> album.items.sumOf { it.size } }
            MediaSort.Count -> grouped.sortedBy { it.items.size }
            MediaSort.Duration -> grouped.sortedBy { album -> album.items.sumOf { it.duration } }
        }
        val ordered = if (sortDirection == SortDirection.Descending) sorted.reversed() else sorted
        if (pinnedAlbumName == null) ordered else {
            val pinned = ordered.firstOrNull { it.name.equals(pinnedAlbumName, ignoreCase = true) }
                ?: MediaAlbum(pinnedAlbumName, emptyList(), null)
            listOf(pinned) + ordered.filterNot { it.name.equals(pinnedAlbumName, ignoreCase = true) }
        }
    }
    val currentAlbum = remember(media, openedFolder, query, sort, sortDirection, flatMode) {
        if (flatMode) {
            val sorted = itemsInDisplayOrder(media)
            MediaAlbum("__flat__", sorted, sorted.firstOrNull())
        } else openedFolder?.let { folder ->
            val folderItems = media.filter { it.folder == folder }
            val items = folderItems.filter { query.isBlank() || it.name.contains(query, true) }
            val sorted = itemsInDisplayOrder(items)
            val cover = itemsInDisplayOrder(folderItems).firstOrNull()
            MediaAlbum(folder, sorted, cover)
        }
    }
    LaunchedEffect(openedFolder, currentAlbum?.items) {
        onVisibleScopeChanged(if (openedFolder == null) null else currentAlbum?.items.orEmpty())
    }

    when {
        !permissionGranted && media.isEmpty() -> PermissionEmpty(onRequestPermission)
        refreshing && media.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        media.isEmpty() && openedFolder.equals("Pixiv", ignoreCase = true) && onOpenPixivArchive != null ->
            FolderGrid(
                MediaAlbum("Pixiv", emptyList(), null),
                folderColumns,
                MediaLayout.Grid,
                loading,
                onOpenMedia,
                onLongPressMedia,
                onRefresh,
                sharedElementEnabled,
                onOpenPixivArchive,
                favoriteUris
            )
        media.isEmpty() && pinnedAlbumName == null -> EmptyMessage(
            emptyMessage ?: if (query.isBlank()) appText(if (isVideo) "这里还没有视频" else "这里还没有图片", english) else appText("没有找到相关内容", english),
            actionLabel = query.takeIf { it.isNotBlank() }?.let { appText("清除", english) },
            onAction = onClearQuery
        )
        albums.isEmpty() && currentAlbum == null -> EmptyMessage(appText("没有找到相关内容", english), appText("清除", english), onClearQuery)
        else -> AnimatedContent(
            targetState = currentAlbum,
            contentKey = { it?.name },
            transitionSpec = {
                // Keep the cover as the shared element while the old grid
                // disappears and the destination grid arrives underneath it.
                fadeIn(
                    animationSpec = tween(360, easing = CubicBezierEasing(.22f, .78f, .24f, 1f))
                ) togetherWith fadeOut(
                    animationSpec = tween(360, easing = CubicBezierEasing(.22f, .78f, .24f, 1f))
                )
            },
            label = "album-folder"
        ) { shownAlbum ->
            // During a viewer transition use the outer page visibility scope;
            // AnimatedContent's scope only describes folder changes and would
            // otherwise keep the thumbnail permanently visible.
            val parentVisibilityScope = LocalMediaAnimatedVisibilityScope.current
            val mediaVisibilityScope = if (LocalActiveSharedMediaKey.current == null) this else parentVisibilityScope
            CompositionLocalProvider(LocalMediaAnimatedVisibilityScope provides mediaVisibilityScope) {
                if (shownAlbum != null) {
                FolderGrid(shownAlbum, folderColumns, layout, refreshing = refreshing, onOpenMedia = onOpenMedia, onLongPressMedia = onLongPressMedia, onRefresh = onRefresh, sharedElementEnabled = sharedElementEnabled, onOpenPixivArchive = onOpenPixivArchive, favoriteUris = favoriteUris, showFavoriteBadge = showFavoriteBadge)
                } else {
                    AlbumGrid(albums, albumColumns, refreshing = refreshing, onOpenAlbum = onOpenedFolderChange, onLongPressAlbum = onLongPressAlbum, onRefresh = onRefresh, sharedElementEnabled = sharedElementEnabled, favoriteUris = favoriteUris, initialFirstVisibleItem = initialAlbumFirstVisibleItem, initialFirstVisibleOffset = initialAlbumFirstVisibleOffset)
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<MediaAlbum>, columns: Int, refreshing: Boolean, onOpenAlbum: (String) -> Unit, onLongPressAlbum: (MediaAlbum, Int, Int) -> Unit, onRefresh: () -> Unit, sharedElementEnabled: Boolean = true, favoriteUris: Set<String> = emptySet(), initialFirstVisibleItem: Int = 0, initialFirstVisibleOffset: Int = 0) {
    val context = LocalContext.current
    val pullEnabled = remember { context.getSharedPreferences("album_settings", Context.MODE_PRIVATE).getBoolean("pull_refresh", true) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(albums, initialFirstVisibleItem, initialFirstVisibleOffset) {
        if (albums.isNotEmpty()) {
            gridState.scrollToItem(initialFirstVisibleItem.coerceIn(0, albums.lastIndex), initialFirstVisibleOffset.coerceAtLeast(0))
        }
    }
    val albumCovers = remember(albums) { albums.mapNotNull { it.coverItem ?: it.items.firstOrNull() } }
    LazyGridMediaPrefetch(gridState, albumCovers, keySelector = MediaItem::folder)
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val scrollJob = remember { arrayOfNulls<Job>(1) }
    val metrics by remember(gridState) { derivedStateOf { gridScrollMetrics(gridState) } }
    val density = LocalDensity.current
    val triggerPull = with(density) { 68.dp.toPx() }
    val maxPull = with(density) { 116.dp.toPx() }
    val pullRefreshing = refreshing
    LaunchedEffect(refreshing) {
        if (!refreshing) pullDistance = 0f
    }
    val pullOffset by animateFloatAsState(
        targetValue = if (pullRefreshing) triggerPull else pullDistance,
        animationSpec = if (pullDistance > 0f && !refreshing) snap() else tween(240, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
        label = "album-pull"
    )
    val pullConnection = rememberPullRefreshConnection(
        enabled = pullEnabled,
        refreshing = refreshing,
        atTop = { !gridState.canScrollBackward },
        pullDistance = { pullDistance },
        triggerDistance = triggerPull,
        maxDistance = maxPull,
        onPullDistanceChange = { pullDistance = it },
        onRelease = { pullDistance = 0f },
        onRefreshStarted = {
            pullDistance = triggerPull
            onRefresh()
        }
    )
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp).graphicsLayer { translationY = pullOffset }.nestedScroll(pullConnection),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultDimens.AlbumGap),
            verticalArrangement = Arrangement.spacedBy(VaultDimens.AlbumGap)
        ) {
            items(albums, key = { it.name }) { album ->
                if (album.items.isEmpty()) {
                    EmptyAlbumTile(album.name) { onOpenAlbum(album.name) }
                } else {
                    AlbumTile(
                        album = album,
                        onLongClick = {
                            onLongPressAlbum(album, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
                        },
                        sharedElementEnabled = sharedElementEnabled
                    ) { onOpenAlbum(album.name) }
                }
            }
        }
        ListScrollHandle(
            progress = metrics.progress,
            visibleFraction = metrics.visibleFraction,
            scrolling = gridState.isScrollInProgress,
            onFraction = { fraction ->
                scrollJob[0]?.cancel()
                scrollJob[0] = scope.launch { gridState.scrollToItem((fraction * (metrics.total - 1).coerceAtLeast(0)).toInt()) }
            },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        PullRefreshIndicator(pullDistance, pullRefreshing, triggerPull, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    }
}

@Composable
private fun EmptyAlbumTile(name: String, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(VaultDimens.AlbumRadius)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(.34f)
            )
        }
        Text(name, modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 7.dp, end = 2.dp), maxLines = 1, fontSize = VaultDimens.AlbumName)
    }
}

@Composable
private fun FolderGrid(
    album: MediaAlbum,
    columns: Int,
    layout: MediaLayout,
    refreshing: Boolean,
    onOpenMedia: (MediaItem) -> Unit,
    onLongPressMedia: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    sharedElementEnabled: Boolean = true,
    onOpenPixivArchive: (() -> Unit)? = null,
    favoriteUris: Set<String> = emptySet(),
    showFavoriteBadge: Boolean = true
) {
    val context = LocalContext.current
    val pullEnabled = remember { context.getSharedPreferences("album_settings", Context.MODE_PRIVATE).getBoolean("pull_refresh", true) }
    val gridState = rememberLazyGridState()
    LazyGridMediaPrefetch(gridState, album.items)
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val scrollJob = remember { arrayOfNulls<Job>(1) }
    val metrics by remember(gridState) { derivedStateOf { gridScrollMetrics(gridState) } }
    val density = LocalDensity.current
    val triggerPull = with(density) { 68.dp.toPx() }
    val maxPull = with(density) { 116.dp.toPx() }
    val pullRefreshing = refreshing
    LaunchedEffect(refreshing) {
        if (!refreshing) pullDistance = 0f
    }
    val pullOffset by animateFloatAsState(
        targetValue = if (pullRefreshing) triggerPull else pullDistance,
        animationSpec = if (pullDistance > 0f && !refreshing) snap() else tween(240, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
        label = "folder-pull"
    )
    val pullConnection = rememberPullRefreshConnection(
        enabled = pullEnabled,
        refreshing = refreshing,
        atTop = { !gridState.canScrollBackward },
        pullDistance = { pullDistance },
        triggerDistance = triggerPull,
        maxDistance = maxPull,
        onPullDistanceChange = { pullDistance = it },
        onRelease = { pullDistance = 0f },
        onRefreshStarted = {
            pullDistance = triggerPull
            onRefresh()
        }
    )
    if (layout == MediaLayout.Adaptive) {
        AdaptiveFolderGrid(album, columns, refreshing, onOpenMedia, onLongPressMedia, onRefresh, sharedElementEnabled, onOpenPixivArchive, favoriteUris, showFavoriteBadge)
        return
    }
    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier.fillMaxSize().graphicsLayer { translationY = pullOffset }.nestedScroll(pullConnection),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (album.name.equals("Pixiv", ignoreCase = true) && onOpenPixivArchive != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PixivArchiveNavigation(onOpenPixivArchive)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "${album.items.size}",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        if (album.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyFolderMessage()
            }
        }
        items(album.items, key = { it.uri.toString() }) { item ->
            PressableMediaThumbnail(item, Modifier.fillMaxWidth().aspectRatio(1f), favorite = item.uri.toString() in favoriteUris, showFavoriteBadge = showFavoriteBadge, onLongClick = { onLongPressMedia(item) }, sharedElementEnabled = sharedElementEnabled) { onOpenMedia(item) }
        }
    }
    ListScrollHandle(
        progress = metrics.progress,
        visibleFraction = metrics.visibleFraction,
        scrolling = gridState.isScrollInProgress,
        onFraction = { fraction ->
            scrollJob[0]?.cancel()
            scrollJob[0] = scope.launch { gridState.scrollToItem((fraction * (metrics.total - 1).coerceAtLeast(0)).toInt()) }
        },
        modifier = Modifier.align(Alignment.CenterEnd)
    )
    PullRefreshIndicator(pullDistance, pullRefreshing, triggerPull, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    }
}

@Composable
private fun AdaptiveFolderGrid(
    album: MediaAlbum,
    columns: Int,
    refreshing: Boolean,
    onOpenMedia: (MediaItem) -> Unit,
    onLongPressMedia: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    sharedElementEnabled: Boolean = true,
    onOpenPixivArchive: (() -> Unit)? = null,
    favoriteUris: Set<String> = emptySet(),
    showFavoriteBadge: Boolean = true
) {
    val context = LocalContext.current
    val pullEnabled = remember { context.getSharedPreferences("album_settings", Context.MODE_PRIVATE).getBoolean("pull_refresh", true) }
    val state = rememberLazyStaggeredGridState()
    LazyStaggeredGridMediaPrefetch(state, album.items)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val scrollJob = remember { arrayOfNulls<Job>(1) }
    val metrics by remember(state) { derivedStateOf { staggeredGridScrollMetrics(state) } }
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val triggerPull = with(density) { 68.dp.toPx() }
    val maxPull = with(density) { 116.dp.toPx() }
    val pullRefreshing = refreshing
    LaunchedEffect(refreshing) {
        if (!refreshing) pullDistance = 0f
    }
    val pullOffset by animateFloatAsState(
        targetValue = if (pullRefreshing) triggerPull else pullDistance,
        animationSpec = if (pullDistance > 0f && !refreshing) snap() else tween(240, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
        label = "adaptive-folder-pull"
    )
    val pullConnection = rememberPullRefreshConnection(
        enabled = pullEnabled,
        refreshing = refreshing,
        atTop = { !state.canScrollBackward },
        pullDistance = { pullDistance },
        triggerDistance = triggerPull,
        maxDistance = maxPull,
        onPullDistanceChange = { pullDistance = it },
        onRelease = { pullDistance = 0f },
        onRefreshStarted = {
            pullDistance = triggerPull
            onRefresh()
        }
    )
    Box(Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = state,
                modifier = Modifier.fillMaxSize().graphicsLayer { translationY = pullOffset }.nestedScroll(pullConnection),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalItemSpacing = 3.dp
        ) {
            if (album.name.equals("Pixiv", ignoreCase = true) && onOpenPixivArchive != null) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    PixivArchiveNavigation(onOpenPixivArchive)
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                Text(
                    "${album.items.size}",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
            if (album.items.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    EmptyFolderMessage()
                }
            }
            items(album.items, key = { it.uri.toString() }) { item ->
                PressableMediaThumbnail(
                    item,
                    Modifier.fillMaxWidth().aspectRatio(item.displayAspectRatio),
                    favorite = item.uri.toString() in favoriteUris,
                    showFavoriteBadge = showFavoriteBadge,
                    onLongClick = { onLongPressMedia(item) },
                    sharedElementEnabled = sharedElementEnabled
                ) { onOpenMedia(item) }
            }
        }
        ListScrollHandle(
            progress = metrics.progress,
            visibleFraction = metrics.visibleFraction,
            scrolling = state.isScrollInProgress,
            onFraction = { fraction ->
                scrollJob[0]?.cancel()
                scrollJob[0] = scope.launch {
                    state.scrollToItem((fraction * (metrics.total - 1).coerceAtLeast(0)).toInt())
                }
            },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        PullRefreshIndicator(pullDistance, pullRefreshing, triggerPull, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    }
}

private data class ScrollMetrics(val total: Int, val progress: Float, val visibleFraction: Float)

private fun gridScrollMetrics(state: androidx.compose.foundation.lazy.grid.LazyGridState): ScrollMetrics {
    val total = state.layoutInfo.totalItemsCount
    val visible = state.layoutInfo.visibleItemsInfo.size
    return ScrollMetrics(
        total = total,
        progress = if (total <= visible) 0f else state.firstVisibleItemIndex.toFloat() / (total - visible),
        visibleFraction = if (total == 0) 1f else visible.toFloat() / total
    )
}

private fun staggeredGridScrollMetrics(state: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState): ScrollMetrics {
    val total = state.layoutInfo.totalItemsCount
    val visible = state.layoutInfo.visibleItemsInfo.size
    return ScrollMetrics(
        total = total,
        progress = if (total <= visible) 0f else state.firstVisibleItemIndex.toFloat() / (total - visible),
        visibleFraction = if (total == 0) 1f else visible.toFloat() / total
    )
}

@Composable
private fun PermissionEmpty(onRequestPermission: () -> Unit) {
    val english = LocalAppEnglish.current
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(appText("需要照片和视频访问权限", english), style = MaterialTheme.typography.titleMedium)
        Text(appText("授权后才能读取设备上的相册内容", english), modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRequestPermission, modifier = Modifier.padding(top = 8.dp)) { Text(appText("授权访问", english)) }
    }
}

@Composable
private fun EmptyMessage(message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        actionLabel?.let { TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(it) } }
    }
}

@Composable
private fun EmptyFolderMessage() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(appText("此文件夹没有内容", LocalAppEnglish.current), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


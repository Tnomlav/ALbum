package com.example.album.ui.screens

import android.content.Context
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import com.example.album.data.MediaItem
import com.example.album.data.displayAspectRatio
import com.example.album.ui.MediaLayout
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.components.PressableMediaThumbnail
import com.example.album.ui.components.PullRefreshIndicator
import com.example.album.ui.components.rememberPullRefreshConnection
import com.example.album.ui.components.ListScrollHandle
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.album.ui.components.LazyGridMediaPrefetch
import com.example.album.ui.components.LazyStaggeredGridMediaPrefetch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@Composable
fun TimelineScreen(
    media: List<MediaItem>,
    query: String,
    loading: Boolean,
    scanning: Boolean = false,
    permissionGranted: Boolean,
    isVideo: Boolean,
    columns: Int,
    layout: MediaLayout,
    jumpToDate: String?,
    onJumpConsumed: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onLongPressMedia: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    sharedElementEnabled: Boolean = true,
    favoriteUris: Set<String> = emptySet(),
    showFavoriteBadge: Boolean = true,
    onClearQuery: () -> Unit = {}
) {
    val refreshing = loading || scanning
    val english = LocalAppEnglish.current
    if (!permissionGranted && media.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(appText("需要照片访问权限", english))
            TextButton(onClick = onRequestPermission) { Text(appText("授权访问", english)) }
        }
        return
    }
    if (refreshing && media.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (media.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (query.isBlank()) appText(if (isVideo) "这里还没有视频" else "这里还没有图片", english) else appText("没有找到相关内容", english), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (query.isNotBlank()) TextButton(onClick = onClearQuery, modifier = Modifier.padding(top = 8.dp)) { Text(appText("清除", english)) }
        }
        return
    }

    val formatter = remember { SimpleDateFormat("yyyy年M月d日", Locale.CHINA) }
    val filtered = remember(media, query) {
        media.filter {
            query.isBlank() || it.name.contains(query, true) || it.folder.contains(query, true) ||
                formatter.format(Date(it.dateTaken)).contains(query, true)
        }
    }
    val groups = remember(filtered) { filtered.groupBy { formatter.format(Date(it.dateTaken)) } }
    val groupedDates = remember(groups) { groups.entries.toList() }

    if (layout == MediaLayout.Adaptive) {
        AdaptiveTimeline(
            groupedDates = groupedDates,
            itemCount = filtered.size,
            isVideo = isVideo,
            columns = columns,
            loading = refreshing,
            jumpToDate = jumpToDate,
            onJumpConsumed = onJumpConsumed,
            onOpenMedia = onOpenMedia,
            onLongPressMedia = onLongPressMedia,
            onRefresh = onRefresh,
            sharedElementEnabled = sharedElementEnabled,
            favoriteUris = favoriteUris,
            showFavoriteBadge = showFavoriteBadge
        )
        return
    }

    OptimizedTimelineGrid(
        groupedDates = groupedDates,
        itemCount = filtered.size,
        isVideo = isVideo,
        columns = columns,
        loading = refreshing,
        jumpToDate = jumpToDate,
        onJumpConsumed = onJumpConsumed,
        onOpenMedia = onOpenMedia,
        onLongPressMedia = onLongPressMedia,
        onRefresh = onRefresh,
        sharedElementEnabled = sharedElementEnabled,
        favoriteUris = favoriteUris,
        showFavoriteBadge = showFavoriteBadge
    )
    return
}

@Composable
private fun OptimizedTimelineGrid(
    groupedDates: List<Map.Entry<String, List<MediaItem>>>,
    itemCount: Int,
    isVideo: Boolean,
    columns: Int,
    loading: Boolean,
    jumpToDate: String?,
    onJumpConsumed: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onLongPressMedia: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    sharedElementEnabled: Boolean = true,
    favoriteUris: Set<String> = emptySet(),
    showFavoriteBadge: Boolean = true
) {
    val english = LocalAppEnglish.current
    val context = LocalContext.current
    val pullEnabled = remember {
        context.getSharedPreferences("album_settings", Context.MODE_PRIVATE).getBoolean("pull_refresh", true)
    }
    val state = rememberLazyGridState()
    val flatItems = remember(groupedDates) { groupedDates.flatMap { it.value } }
    LazyGridMediaPrefetch(state, flatItems)
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val scrollJob = remember { arrayOfNulls<Job>(1) }
    val metrics by remember(state) { derivedStateOf { timelineGridScrollMetrics(state) } }
    val density = LocalDensity.current
    val triggerPull = with(density) { 68.dp.toPx() }
    val maxPull = with(density) { 116.dp.toPx() }
    val pullRefreshing = loading
    LaunchedEffect(loading) {
        if (!loading) pullDistance = 0f
    }
    val pullOffset by animateFloatAsState(
        targetValue = if (pullRefreshing) triggerPull else pullDistance,
        animationSpec = if (pullDistance > 0f && !loading) snap()
        else tween(240, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
        label = "timeline-grid-pull"
    )
    val pullConnection = rememberPullRefreshConnection(
        enabled = pullEnabled,
        refreshing = loading,
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
    LaunchedEffect(jumpToDate, groupedDates) {
        val target = jumpToDate ?: return@LaunchedEffect
        val parser = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
        val selectedTime = runCatching { parser.parse(target)?.time }.getOrNull()
        val groupIndex = groupedDates.indices.minByOrNull { index ->
            if (groupedDates[index].key == target) Long.MIN_VALUE
            else kotlin.math.abs(
                (runCatching { parser.parse(groupedDates[index].key)?.time }.getOrNull() ?: Long.MAX_VALUE) -
                    (selectedTime ?: 0L)
            )
        } ?: -1
        if (groupIndex >= 0) {
            val targetIndex = 1 + groupedDates.take(groupIndex).sumOf { 1 + it.value.size }
            state.animateScrollToItem(targetIndex)
        }
        onJumpConsumed()
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceIn(1, 6)),
            state = state,
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = pullOffset }.nestedScroll(pullConnection),
            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    itemCount.toString(),
                    modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 11.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
            groupedDates.forEach { group ->
                item(key = "header-${group.key}", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 38.dp)
                            .padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            timelineDateLabel(group.value.first().dateTaken, english),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            group.value.size.toString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                group.value.forEach { item ->
                    item(key = item.uri.toString()) {
                        PressableMediaThumbnail(
                            item,
                            Modifier.fillMaxWidth().aspectRatio(1f),
                            favorite = item.uri.toString() in favoriteUris,
                            showFavoriteBadge = showFavoriteBadge,
                            onLongClick = { onLongPressMedia(item) },
                            sharedElementEnabled = sharedElementEnabled
                        ) { onOpenMedia(item) }
                    }
                }
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

@Composable
private fun AdaptiveTimeline(
    groupedDates: List<Map.Entry<String, List<MediaItem>>>,
    itemCount: Int,
    isVideo: Boolean,
    columns: Int,
    loading: Boolean,
    jumpToDate: String?,
    onJumpConsumed: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onLongPressMedia: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    sharedElementEnabled: Boolean = true,
    favoriteUris: Set<String> = emptySet(),
    showFavoriteBadge: Boolean = true
) {
    val english = LocalAppEnglish.current
    val context = LocalContext.current
    val pullEnabled = remember { context.getSharedPreferences("album_settings", Context.MODE_PRIVATE).getBoolean("pull_refresh", true) }
    val state = rememberLazyStaggeredGridState()
    val flatItems = remember(groupedDates) { groupedDates.flatMap { it.value } }
    LazyStaggeredGridMediaPrefetch(state, flatItems)
    val scope = rememberCoroutineScope()
    val scrollJob = remember { arrayOfNulls<Job>(1) }
    val metrics by remember(state) { derivedStateOf { timelineStaggeredGridScrollMetrics(state) } }
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val triggerPull = with(density) { 68.dp.toPx() }
    val maxPull = with(density) { 116.dp.toPx() }
    val pullRefreshing = loading
    LaunchedEffect(loading) {
        if (!loading) pullDistance = 0f
    }
    val pullOffset by animateFloatAsState(
        targetValue = if (pullRefreshing) triggerPull else pullDistance,
        animationSpec = if (pullDistance > 0f && !loading) snap() else tween(240, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
        label = "adaptive-timeline-pull"
    )
    val pullConnection = rememberPullRefreshConnection(
        enabled = pullEnabled,
        refreshing = loading,
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
    LaunchedEffect(jumpToDate, groupedDates) {
        val target = jumpToDate ?: return@LaunchedEffect
        val parser = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
        val selectedTime = runCatching { parser.parse(target)?.time }.getOrNull()
        val groupIndex = groupedDates.indices.minByOrNull { index ->
            if (groupedDates[index].key == target) Long.MIN_VALUE
            else kotlin.math.abs((runCatching { parser.parse(groupedDates[index].key)?.time }.getOrNull() ?: Long.MAX_VALUE) - (selectedTime ?: 0L))
        } ?: -1
        if (groupIndex >= 0) {
            val targetIndex = 1 + groupedDates.take(groupIndex).sumOf { 1 + it.value.size }
            state.animateScrollToItem(targetIndex)
        }
        onJumpConsumed()
    }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = pullOffset }.nestedScroll(pullConnection),
            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalItemSpacing = 3.dp
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Text(
                    itemCount.toString(),
                    modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
            groupedDates.forEach { group ->
                item(key = "header-${group.key}", span = StaggeredGridItemSpan.FullLine) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(timelineDateLabel(group.value.first().dateTaken, english), style = MaterialTheme.typography.bodyMedium)
                        Text(group.value.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
                group.value.forEach { mediaItem ->
                    item(key = mediaItem.uri.toString()) {
                        PressableMediaThumbnail(
                            mediaItem,
                            Modifier.fillMaxWidth().aspectRatio(mediaItem.displayAspectRatio),
                            favorite = mediaItem.uri.toString() in favoriteUris,
                            showFavoriteBadge = showFavoriteBadge,
                            onLongClick = { onLongPressMedia(mediaItem) },
                            sharedElementEnabled = sharedElementEnabled
                        ) { onOpenMedia(mediaItem) }
                    }
                }
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

private data class TimelineScrollMetrics(val total: Int, val progress: Float, val visibleFraction: Float)

private fun timelineGridScrollMetrics(state: androidx.compose.foundation.lazy.grid.LazyGridState): TimelineScrollMetrics {
    val total = state.layoutInfo.totalItemsCount
    val visible = state.layoutInfo.visibleItemsInfo.size
    return TimelineScrollMetrics(
        total = total,
        progress = if (total <= visible) 0f else state.firstVisibleItemIndex.toFloat() / (total - visible),
        visibleFraction = if (total == 0) 1f else visible.toFloat() / total
    )
}

private fun timelineStaggeredGridScrollMetrics(
    state: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
): TimelineScrollMetrics {
    val total = state.layoutInfo.totalItemsCount
    val visible = state.layoutInfo.visibleItemsInfo.size
    return TimelineScrollMetrics(
        total = total,
        progress = if (total <= visible) 0f else state.firstVisibleItemIndex.toFloat() / (total - visible),
        visibleFraction = if (total == 0) 1f else visible.toFloat() / total
    )
}

private fun timelineDateLabel(timestamp: Long, english: Boolean): String {
    val date = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val monthDay = SimpleDateFormat(if (english) "MMM d" else "M月d日", if (english) Locale.ENGLISH else Locale.CHINA).format(Date(timestamp))
    return when {
        sameDay(date, today) -> "${if (english) "Today" else "今天"} · $monthDay"
        sameDay(date, yesterday) -> "${if (english) "Yesterday" else "昨天"} · $monthDay"
        date.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> monthDay
        else -> SimpleDateFormat(if (english) "MMM d, yyyy" else "yyyy年M月d日", if (english) Locale.ENGLISH else Locale.CHINA).format(Date(timestamp))
    }
}

private fun sameDay(first: Calendar, second: Calendar): Boolean =
    first.get(Calendar.ERA) == second.get(Calendar.ERA) &&
        first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)


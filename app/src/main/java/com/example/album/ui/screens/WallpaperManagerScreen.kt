package com.example.album.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.album.data.MediaItem
import com.example.album.data.displayAspectRatio
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.MediaLayout
import com.example.album.ui.MediaSort
import com.example.album.ui.SortDirection
import com.example.album.ui.components.MediaThumbnail
import com.example.album.ui.searchTextMatches
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WallpaperSort(val label: String) {
    Time("时间"),
    Name("名称"),
    Size("大小"),
    QueueOrder("加入顺序")
}

@Composable
fun WallpaperManagerScreen(
    queuedMedia: List<MediaItem>,
    searchMedia: List<MediaItem>,
    query: String,
    selectionMode: Boolean,
    selectedUris: Set<String>,
    columns: Int,
    layout: MediaLayout,
    sort: WallpaperSort,
    sortDirection: SortDirection,
    queueOrder: List<String>,
    onOpenMedia: (MediaItem) -> Unit,
    onToggleSelection: (MediaItem) -> Unit,
    onEnterSelectionMode: (MediaItem) -> Unit,
    onRemove: (MediaItem) -> Unit
) {
    val english = LocalAppEnglish.current
    val formatter = remember { SimpleDateFormat("yyyy年M月d日", Locale.CHINA) }
    val baseMatchingMedia = remember(searchMedia, query) {
        if (query.isBlank()) emptyList()
        else searchMedia.filter { searchTextMatches(query, it.name, it.folder) }
    }
    val folders = remember(searchMedia, query) {
        if (query.isBlank()) emptyList()
        else searchMedia.map { it.folder }.filter { searchTextMatches(query, it) }.distinct().sorted()
    }
    val baseVisibleMedia = if (query.isBlank()) queuedMedia else baseMatchingMedia
    val visibleMedia = remember(baseVisibleMedia, sort, sortDirection, queueOrder) {
        val ordered = when (sort) {
            WallpaperSort.Time -> baseVisibleMedia.sortedBy { it.dateTaken }
            WallpaperSort.Name -> baseVisibleMedia.sortedBy { it.name.lowercase() }
            WallpaperSort.Size -> baseVisibleMedia.sortedBy { it.size }
            WallpaperSort.QueueOrder -> baseVisibleMedia.sortedBy { item ->
                val index = queueOrder.indexOf(item.uri.toString())
                if (index < 0) Int.MAX_VALUE else index
            }
        }
        if (sortDirection == SortDirection.Descending) ordered.reversed() else ordered
    }
    val sections = remember(visibleMedia) { visibleMedia.groupBy { formatter.format(Date(it.dateTaken)) }.entries.toList() }

    if (visibleMedia.isEmpty() && folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (query.isBlank()) appText("壁纸队列为空", english) else appText("没有找到相关内容", english),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = if (layout == MediaLayout.Adaptive) GridCells.Adaptive(96.dp) else GridCells.Fixed(columns.coerceIn(1, 6)),
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                if (query.isBlank()) visibleMedia.size.toString() else appText("搜索结果 ${visibleMedia.size} 项", english),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End
            )
        }
        if (query.isNotBlank()) {
            folders.forEach { folder ->
                item(key = "folder:$folder", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(folder.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        sections.forEach { (date, itemsForDate) ->
            item(key = "date:$date", span = { GridItemSpan(maxLineSpan) }) {
                Text(date, modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 1.dp), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
            items(itemsForDate, key = { it.uri.toString() }) { item ->
                val selected = item.uri.toString() in selectedUris
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(item.displayAspectRatio).clip(RoundedCornerShape(4.dp))
                            .combinedClickable(
                                onClick = { if (selectionMode) onToggleSelection(item) else onOpenMedia(item) },
                                onLongClick = { onEnterSelectionMode(item) }
                            )
                    ) {
                        MediaThumbnail(item, Modifier.fillMaxSize(), showVideoDuration = item.isVideo, contentScale = ContentScale.Crop)
                        if (selectionMode) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(5.dp).size(23.dp)
                                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = .48f), RoundedCornerShape(50))
                            ) {
                                if (selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.fillMaxSize().padding(3.dp))
                            }
                        } else {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp)
                                    .clickable { onRemove(item) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Close, appText("移出壁纸队列", english), tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Text(item.name, modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

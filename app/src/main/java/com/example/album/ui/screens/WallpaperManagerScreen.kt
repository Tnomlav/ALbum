package com.example.album.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.album.ui.theme.VaultDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WallpaperSort(val label: String) {
    Time("时间"),
    Name("名称"),
    Size("大小"),
    QueueOrder("加入顺序")
}

/** Applies the same order used by the wallpaper manager to a wallpaper queue. */
fun sortWallpaperMedia(
    media: List<MediaItem>,
    sort: WallpaperSort,
    sortDirection: SortDirection,
    queueOrder: List<String>
): List<MediaItem> {
    val ordered = when (sort) {
        WallpaperSort.Time -> media.sortedBy { it.dateTaken }
        WallpaperSort.Name -> media.sortedBy { it.name.lowercase() }
        WallpaperSort.Size -> media.sortedBy { it.size }
        WallpaperSort.QueueOrder -> media.sortedBy { item ->
            val index = queueOrder.indexOf(item.uri.toString())
            if (index < 0) Int.MAX_VALUE else index
        }
    }
    return if (sortDirection == SortDirection.Descending) ordered.reversed() else ordered
}

@Composable
fun WallpaperManagerScreen(
    queuedMedia: List<MediaItem>,
    searchMedia: List<MediaItem>,
    query: String,
    selectionMode: Boolean,
    selectedUris: Set<String>,
    selectionOrder: List<String>,
    columns: Int,
    mediaLayout: MediaLayout,
    folderLayout: MediaLayout,
    sort: WallpaperSort,
    sortDirection: SortDirection,
    queueOrder: List<String>,
    onOpenMedia: (MediaItem) -> Unit,
    onToggleSelection: (MediaItem) -> Unit,
    onEnterSelectionMode: (MediaItem, List<MediaItem>) -> Unit,
    onRemove: (MediaItem) -> Unit,
    folderMode: Boolean = false,
    openedFolder: String? = null,
    onOpenFolder: (String) -> Unit = {},
    onCloseFolder: () -> Unit = {}
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
    val folderQueue = remember(queuedMedia, query) {
        queuedMedia
            .filter { query.isBlank() || searchTextMatches(query, it.folder, it.name) }
            .groupBy { it.folder }
            .toSortedMap()
    }
    val baseVisibleMedia = when {
        folderMode && openedFolder != null -> queuedMedia.filter { it.folder == openedFolder }
        folderMode -> emptyList()
        query.isBlank() -> queuedMedia
        else -> baseMatchingMedia
    }
    val visibleMedia = remember(baseVisibleMedia, sort, sortDirection, queueOrder, selectionMode, selectionOrder) {
        val sorted = sortWallpaperMedia(baseVisibleMedia, sort, sortDirection, queueOrder)
        if (selectionMode && query.isBlank() && selectionOrder.isNotEmpty()) {
            val positions = selectionOrder.withIndex().associate { it.value to it.index }
            sorted.sortedBy { positions[it.uri.toString()] ?: Int.MAX_VALUE }
        } else sorted
    }
    val sections = remember(visibleMedia) { visibleMedia.groupBy { formatter.format(Date(it.dateTaken)) }.entries.toList() }

    val showFolderTiles = folderMode && openedFolder == null
    if (showFolderTiles && folderQueue.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(appText("壁纸队列为空", english), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (!showFolderTiles && visibleMedia.isEmpty() && folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (query.isBlank()) appText("壁纸队列为空", english) else appText("没有找到相关内容", english),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val adaptiveFolderColumns = (maxWidth / 96.dp).toInt().coerceIn(2, 6)
        val folderColumns = if (folderLayout == MediaLayout.Adaptive) adaptiveFolderColumns else columns.coerceIn(1, 6)
        if (showFolderTiles) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 7.dp, vertical = 8.dp)) {
                WallpaperFolderSection(
                    folders = folderQueue.keys.toList(),
                    covers = folderQueue.mapValues { it.value.firstOrNull() },
                    counts = folderQueue.mapValues { it.value.size },
                    columns = folderColumns,
                    english = english,
                    onOpenFolder = onOpenFolder
                )
            }
            return@BoxWithConstraints
        }
        if (mediaLayout == MediaLayout.Adaptive) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(columns.coerceIn(1, 6)),
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalItemSpacing = 6.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    if (openedFolder != null) {
                        WallpaperFolderBackRow(openedFolder, english, onCloseFolder)
                    }
                    WallpaperResultCount(visibleMedia.size, query, english)
                }
                if (folders.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "folders") {
                        WallpaperFolderSection(
                            folders = folders,
                            covers = remember(folders, searchMedia) {
                                folders.associateWith { folder -> searchMedia.firstOrNull { it.folder == folder } }
                            },
                            counts = remember(folders, searchMedia) {
                                folders.associateWith { folder -> searchMedia.count { it.folder == folder } }
                            },
                            columns = folderColumns,
                            english = english
                        )
                    }
                }
                sections.forEach { (date, itemsForDate) ->
                    item(span = StaggeredGridItemSpan.FullLine, key = "date:$date") {
                        Text(date, modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 1.dp), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                    items(itemsForDate, key = { it.uri.toString() }) { item ->
                        WallpaperMediaCell(
                            item = item,
                            selectionMode = selectionMode,
                            selected = item.uri.toString() in selectedUris,
                            visibleMedia = visibleMedia,
                            english = english,
                            aspectRatio = item.displayAspectRatio,
                            onOpenMedia = onOpenMedia,
                            onToggleSelection = onToggleSelection,
                            onEnterSelectionMode = onEnterSelectionMode,
                            onRemove = onRemove
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns.coerceIn(1, 6)),
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (openedFolder != null) {
                        WallpaperFolderBackRow(openedFolder, english, onCloseFolder)
                    }
                    WallpaperResultCount(visibleMedia.size, query, english)
                }
                if (folders.isNotEmpty()) {
                    item(key = "folders", span = { GridItemSpan(maxLineSpan) }) {
                        WallpaperFolderSection(
                            folders = folders,
                            covers = remember(folders, searchMedia) {
                                folders.associateWith { folder -> searchMedia.firstOrNull { it.folder == folder } }
                            },
                            counts = remember(folders, searchMedia) {
                                folders.associateWith { folder -> searchMedia.count { it.folder == folder } }
                            },
                            columns = folderColumns,
                            english = english
                        )
                    }
                }
                sections.forEach { (date, itemsForDate) ->
                    item(key = "date:$date", span = { GridItemSpan(maxLineSpan) }) {
                        Text(date, modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 1.dp), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                    items(itemsForDate, key = { it.uri.toString() }) { item ->
                        WallpaperMediaCell(
                            item = item,
                            selectionMode = selectionMode,
                            selected = item.uri.toString() in selectedUris,
                            visibleMedia = visibleMedia,
                            english = english,
                            aspectRatio = 1f,
                            onOpenMedia = onOpenMedia,
                            onToggleSelection = onToggleSelection,
                            onEnterSelectionMode = onEnterSelectionMode,
                            onRemove = onRemove
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperResultCount(count: Int, query: String, english: Boolean) {
    Text(
        if (query.isBlank()) count.toString()
        else if (english) "Search results: $count" else "搜索结果 $count 项",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.End
    )
}

@Composable
private fun WallpaperFolderBackRow(folder: String, english: Boolean, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClose).padding(horizontal = 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Outlined.Folder, contentDescription = appText("文件夹", english), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(folder.substringAfterLast('/'), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        Text(if (english) "Back to folders" else "返回文件夹列表", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
    }
}

@Composable
private fun WallpaperFolderSection(
    folders: List<String>,
    covers: Map<String, MediaItem?>,
    columns: Int,
    english: Boolean,
    counts: Map<String, Int> = emptyMap(),
    onOpenFolder: (String) -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        folders.chunked(columns.coerceAtLeast(1)).forEach { rowFolders ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowFolders.forEach { folder ->
                    Column(Modifier.weight(1f).clickable { onOpenFolder(folder) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                            val cover = covers[folder]
                            if (cover != null) {
                                MediaThumbnail(
                                    cover,
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(VaultDimens.AlbumRadius)),
                                    showVideoDuration = false
                                )
                            } else {
                                Box(
                                    Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(VaultDimens.AlbumRadius)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Folder,
                                        contentDescription = appText("文件夹", english),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxSize(.34f)
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 2.dp, top = 7.dp, end = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                folder.substringAfterLast('/'),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = VaultDimens.AlbumName
                            )
                            counts[folder]?.let { count ->
                                Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = VaultDimens.AlbumCount)
                            }
                        }
                    }
                }
                repeat(columns - rowFolders.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WallpaperMediaCell(
    item: MediaItem,
    selectionMode: Boolean,
    selected: Boolean,
    visibleMedia: List<MediaItem>,
    english: Boolean,
    aspectRatio: Float,
    onOpenMedia: (MediaItem) -> Unit,
    onToggleSelection: (MediaItem) -> Unit,
    onEnterSelectionMode: (MediaItem, List<MediaItem>) -> Unit,
    onRemove: (MediaItem) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(aspectRatio.coerceIn(.45f, 2.4f)).clip(RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelection(item) else onOpenMedia(item) },
                    onLongClick = { onEnterSelectionMode(item, visibleMedia) }
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
        Text(
            item.name,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

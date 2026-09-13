package com.example.album.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.album.data.MediaItem
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.components.MediaThumbnail

/** Slideshow queue: the images that will be played, managed like the wallpaper queue. */
@Composable
fun SlideshowQueueScreen(
    items: List<MediaItem>,
    columns: Int = 4,
    onOpenMedia: (MediaItem) -> Unit,
    onRemove: (MediaItem) -> Unit
) {
    val english = LocalAppEnglish.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                appText("幻灯片队列为空，先在其他页面把图片加入队列", english),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceIn(1, 6)),
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                if (english) "${items.size} item(s)" else "${items.size} 项",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        items(items, key = { it.uri.toString() }) { item ->
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(4.dp))
                        .clickable { onOpenMedia(item) }
                ) {
                    MediaThumbnail(item, Modifier.fillMaxSize(), showVideoDuration = false, contentScale = ContentScale.Crop)
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp)
                            .clickable { onRemove(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Close, appText("移出幻灯片队列", english), tint = Color.White, modifier = Modifier.size(20.dp))
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
    }
}

package com.example.album.ui.screens

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.album.data.MediaItem
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.components.MediaThumbnail
import com.example.album.ui.theme.VaultDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WallpaperManagerScreen(
    media: List<MediaItem>,
    onOpenMedia: (MediaItem) -> Unit,
    onRemove: (MediaItem) -> Unit
) {
    val english = LocalAppEnglish.current
    val formatter = remember { SimpleDateFormat("yyyy年M月d日", Locale.CHINA) }
    val sections = remember(media) {
        media.sortedByDescending { it.dateTaken }
            .groupBy { formatter.format(Date(it.dateTaken)) }
            .entries.toList()
    }

    if (media.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(appText("壁纸队列为空", english), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "${media.size}",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        sections.forEach { (date, itemsForDate) ->
            item(key = "date:$date", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    date,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 1.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = VaultDimens.AlbumName
                )
            }
            items(itemsForDate, key = { it.uri.toString() }) { item ->
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clickable { onOpenMedia(item) }
                    ) {
                        MediaThumbnail(
                            item,
                            Modifier.fillMaxSize(),
                            showVideoDuration = true,
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onRemove(item) },
                            modifier = Modifier.align(Alignment.TopEnd).size(34.dp)
                        ) {
                            Icon(Icons.Outlined.Close, appText("移出壁纸队列", english), tint = Color.White)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp)) {
                        Text(
                            item.name,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

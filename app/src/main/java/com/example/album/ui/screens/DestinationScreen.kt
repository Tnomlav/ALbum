package com.example.album.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.album.data.ConflictPolicy
import com.example.album.data.MediaItem
import com.example.album.data.TransferMode
import com.example.album.ui.theme.VaultDimens
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.components.MediaThumbnail
import com.example.album.ui.components.VaultTextInputSheet

@Composable
fun DestinationScreen(
    mode: TransferMode,
    itemCount: Int,
    items: List<MediaItem>,
    folders: List<String>,
    folderCovers: Map<String, MediaItem> = emptyMap(),
    recentFolders: List<String>,
    defaultConflictPolicy: ConflictPolicy,
    defaultPreserveDate: Boolean,
    onBack: () -> Unit,
    onConfirm: (String, ConflictPolicy, Boolean) -> Unit
) {
    val english = LocalAppEnglish.current
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var newFolderOpen by rememberSaveable { mutableStateOf(false) }
    val allFolders = remember(folders, recentFolders, selectedFolder) {
        (recentFolders + folders + listOfNotNull(selectedFolder)).filter { it.isNotBlank() }.distinct()
    }
    val visibleFolders = remember(allFolders, query) {
        if (query.isBlank()) allFolders else allFolders.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                        .height(VaultDimens.HeaderContentHeight).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(48.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                        Text(appText("取消", english), color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(
                        modifier = Modifier.weight(1f).height(48.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it; selectedFolder = null },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) Text(appText("搜索文件夹、图片名称", english), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = VaultDimens.SearchText, maxLines = 1)
                                    inner()
                                }
                            }
                        )
                    }
                    Box(
                        Modifier.size(48.dp).then(
                            if (selectedFolder != null) Modifier.clickable { onConfirm(selectedFolder!!, defaultConflictPolicy, defaultPreserveDate) }
                            else Modifier
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(appText("确认", english), color = if (selectedFolder != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f))
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 7.dp).windowInsetsPadding(WindowInsets.navigationBars)) {
            Text(
                if (english) "Selected $itemCount items" else "已选择 $itemCount 项",
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
            ) {
                items(items.size, key = { index -> "${items[index].uri}:$index" }) { index ->
                    val item = items[index]
                    if (item.isDocument) {
                        Box(
                            Modifier.size(62.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("图片", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                    } else {
                        MediaThumbnail(
                            item = item,
                            modifier = Modifier.size(62.dp).clip(RoundedCornerShape(7.dp)),
                            requestedSize = 180
                        )
                    }
                }
            }
            Text(
                appText("近期使用", english),
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(visibleFolders, key = { _, folder -> folder }) { index, folder ->
                    DestinationFolderCell(
                        name = folder,
                        selected = folder == selectedFolder,
                        index = index,
                        cover = folderCovers[folder]
                    ) { selectedFolder = folder }
                }
                item(key = "new-folder") {
                    DestinationFolderCell(
                        name = appText("新建文件夹", english),
                        selected = false,
                        index = visibleFolders.size,
                        isNew = true
                    ) { newFolderOpen = true }
                }
            }
        }
    }

    if (newFolderOpen) {
        NewFolderDialog(
            onDismiss = { newFolderOpen = false },
            onCreate = {
                selectedFolder = it
                newFolderOpen = false
            }
        )
    }
}

@Composable
private fun DestinationFolderCell(
    name: String,
    selected: Boolean,
    index: Int,
    isNew: Boolean = false,
    cover: MediaItem? = null,
    onClick: () -> Unit
) {
    val palettes = listOf(
        listOf(Color(0xFFD8E9EF), Color(0xFF6594B1), Color(0xFF334B59)),
        listOf(Color(0xFFE9EDC8), Color(0xFF9EBE71), Color(0xFF4E735A)),
        listOf(Color(0xFFF4DEC8), Color(0xFFD49162), Color(0xFF754938)),
        listOf(Color(0xFFE7DDF0), Color(0xFF9E80B7), Color(0xFF504663)),
        listOf(Color(0xFFDCEFED), Color(0xFF75AEB5), Color(0xFF466476))
    )
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(palettes[index % palettes.size])),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null && !isNew) {
                MediaThumbnail(
                    item = cover,
                    modifier = Modifier.fillMaxSize(),
                    requestedSize = 220,
                    contentScale = ContentScale.Crop
                )
            } else if (isNew) {
                Text("+", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(19.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .72f), CircleShape)
                    .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Text("✓", color = Color.White, fontSize = 11.sp)
            }
        }
            Text(
                name,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, start = 2.dp, end = 2.dp),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )
    }
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val english = LocalAppEnglish.current
    var name by rememberSaveable { mutableStateOf("") }
    val normalized = name.trim()
    val valid = normalized.isNotBlank() && normalized.none { it in "\\/:*?\"<>|" }
    VaultTextInputSheet(
        title = appText("新建文件夹", english),
        value = name,
        onValueChange = { name = it },
        label = appText("文件夹名称", english),
        confirmLabel = appText("创建", english),
        confirmEnabled = valid,
        onDismiss = onDismiss,
        onConfirm = { onCreate(normalized) }
    )
}

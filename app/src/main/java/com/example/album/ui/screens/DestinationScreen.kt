package com.example.album.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.album.ui.searchTextMatches
import com.example.album.data.ConflictPolicy
import com.example.album.data.MediaItem
import com.example.album.data.TransferMode
import com.example.album.ui.theme.VaultDimens
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.components.MediaThumbnail
import com.example.album.data.openMediaInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun DestinationScreen(
    mode: TransferMode,
    itemCount: Int,
    items: List<MediaItem>,
    folders: List<String>,
    folderCovers: Map<String, MediaItem> = emptyMap(),
    folderItems: Map<String, List<MediaItem>> = emptyMap(),
    folderChildren: Map<String, Set<String>> = emptyMap(),
    searchingFolders: Boolean = false,
    recentFolders: List<String>,
    defaultConflictPolicy: ConflictPolicy,
    defaultPreserveDate: Boolean,
    onBack: () -> Unit,
    onConfirm: (String, ConflictPolicy, Boolean) -> Unit,
    validateFolder: suspend (String) -> Boolean = { true },
    onCreateFolder: suspend (String, String) -> Boolean = { _, _ -> false }
) {
    val english = LocalAppEnglish.current
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var currentFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var createFolderName by rememberSaveable { mutableStateOf("") }
    var createFolderError by remember { mutableStateOf(false) }
    var createdFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    fun coverFor(path: String): MediaItem? = folderCovers[path]
    val indexedFolderPaths = remember(folders, folderChildren) {
        expandIndexedFolderPaths(folders.toSet(), folderChildren)
    }
    val allFolders = remember(folders, createdFolders, selectedFolder, indexedFolderPaths) {
        (folders + indexedFolderPaths + createdFolders + listOfNotNull(selectedFolder))
            .filter { it.isNotBlank() }.distinct()
    }
    val folderEntries = remember(allFolders, folderChildren, currentFolder, query) {
        currentFolder?.let { current ->
            val childPrefix = "$current/"
            val pathChildren = allFolders.asSequence()
                .filter { it.startsWith(childPrefix) }
                .map { it.removePrefix(childPrefix).substringBefore('/') }
            val names = (folderChildren[current.substringAfterLast('/')].orEmpty() + pathChildren +
                createdFolders.filter { it.startsWith(childPrefix) }
                    .map { it.removePrefix(childPrefix).substringBefore('/') })
                .toList()
            names.distinct().map { name -> name to "$current/$name" }
        } ?: if (query.isNotBlank()) {
            // Searching may address a nested folder directly. If the folder
            // index also contains its leaf-name alias, prefer the complete
            // path so "AI生成1" resolves to AI生成/AI生成1 rather than a
            // new root-level directory.
            val completePathLeaves = allFolders
                .filter { '/' in it }
                .mapTo(hashSetOf()) { it.substringAfterLast('/') }
            allFolders.asSequence()
                .filter { path -> '/' in path || path !in completePathLeaves }
                .map { path -> path.substringAfterLast('/') to path }
                .distinctBy { it.second }
                .toList()
        } else run {
            // A destination picker must expose only root folders here. A
            // nested path such as AI生成/AI生成1 is opened one level
            // at a time, so its leaf name cannot accidentally be selected as
            // AI生成1.
            val pathRoots = allFolders.map { it.substringBefore('/') }.toSet()
            val knownPathSegments = allFolders
                .filter { it.contains('/') }
                .flatMap { it.split('/') }
                .toSet()
            allFolders.asSequence()
                .filter { path ->
                    if (path.contains('/')) true
                    else path !in knownPathSegments || path in pathRoots
                }
                .map { path ->
                    val root = path.substringBefore('/')
                    root to root
                }
                .distinctBy { it.second }
                .toList()
        }
    }
    val visibleFolders = remember(folderEntries, query) {
        folderEntries.filter { (name, _) -> searchTextMatches(query, name) }
    }
    val indexedChildNames = remember(folderChildren) {
        folderChildren.values.flatten().toSet()
    }
    val canonicalRecentFolders = remember(recentFolders, allFolders, indexedChildNames) {
        recentFolders
            .mapNotNull { recent ->
                if ('/' in recent) {
                    recent.takeIf { it in allFolders }
                } else {
                    allFolders
                        .filter { '/' in it && it.substringAfterLast('/') == recent }
                        .singleOrNull()
                        ?: recent.takeIf { it in allFolders && it !in indexedChildNames }
                }
            }
            .distinct()
    }
    val recentFolderExists by produceState(
        initialValue = emptyMap<String, Boolean>(),
        canonicalRecentFolders
    ) {
        value = canonicalRecentFolders.associateWith { path -> validateFolder(path) }
    }
    val recentEntries = remember(canonicalRecentFolders, recentFolderExists, query) {
        canonicalRecentFolders
            .filter { recentFolderExists[it] == true }
            .filter { searchTextMatches(query, it.substringAfterLast('/')) }
            .map { it.substringAfterLast('/') to it }
    }
    val currentFolderItems = currentFolder?.let { folder ->
        folderItems[folder].orEmpty()
    }.orEmpty()

    BackHandler {
        if (currentFolder != null) {
            val parent = currentFolder!!.substringBeforeLast('/', "").ifBlank { null }
            currentFolder = parent
            selectedFolder = parent
            query = ""
        } else {
            onBack()
        }
    }

    Scaffold(
        floatingActionButton = {
            val canCreate = currentFolder != null
            FloatingActionButton(
                onClick = {
                    if (canCreate) {
                        createFolderName = ""
                        createFolderError = false
                        showCreateFolderDialog = true
                    }
                },
                containerColor = if (canCreate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (canCreate) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(Icons.Outlined.CreateNewFolder, appText("新建文件夹", english))
            }
        },
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
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; selectedFolder = null }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Outlined.Close, contentDescription = appText("清除搜索", english), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Box(
                        Modifier.size(48.dp).then(
                            if (selectedFolder != null) Modifier.clickable { onConfirm(selectedFolder!!, defaultConflictPolicy, defaultPreserveDate) }
                            else Modifier
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            appText("确认", english),
                            color = if (selectedFolder != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)
                            }
                        )
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
            currentFolder?.let { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val parent = folder.substringBeforeLast('/', "").ifBlank { null }
                        currentFolder = parent
                        selectedFolder = parent
                        query = ""
                    }.padding(horizontal = 2.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, modifier = Modifier.size(18.dp))
                    Text(folder.substringAfterLast('/'), modifier = Modifier.padding(start = 6.dp), fontSize = 13.sp)
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
            ) {
                items(items.size, key = { index -> "${items[index].uri}:$index" }) { index ->
                    val item = items[index]
                    if (item.isDocument) {
                        DocumentPreviewThumbnail(item)
                    } else {
                        MediaThumbnail(
                            item = item,
                            modifier = Modifier.size(62.dp).clip(RoundedCornerShape(7.dp)),
                            requestedSize = 180
                        )
                    }
                }
            }
            if (currentFolder == null) {
                Text(
                    appText("近期使用", english),
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
                ) {
                    items(recentEntries, key = { it.second }) { (name, folder) ->
                        RecentFolderCell(name = name, selected = folder == selectedFolder) {
                            currentFolder = folder
                            selectedFolder = folder
                            query = ""
                        }
                    }
                }
                Text(
                    appText("选择文件夹", english),
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            if (searchingFolders && query.isNotBlank() && visibleFolders.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (query.isNotBlank() && visibleFolders.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(appText("没有找到相关文件夹", english), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed(visibleFolders, key = { _, entry -> "folder:${entry.second}" }) { index, (name, folder) ->
                        DestinationFolderCell(
                            name = name,
                            selected = folder == selectedFolder,
                            index = index,
                            cover = coverFor(folder)
                        , onToggle = { selectedFolder = if (selectedFolder == folder) null else folder },
                        onOpen = {
                            currentFolder = folder
                            selectedFolder = folder
                            query = ""
                        }
                        )
                    }
                    if (currentFolder != null && query.isBlank()) {
                        items(currentFolderItems, key = { "item:${it.uri}" }) { item ->
                            if (item.isDocument) {
                                DocumentPreviewThumbnail(
                                    item,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                )
                            } else {
                                MediaThumbnail(
                                    item = item,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(7.dp)),
                                    requestedSize = 220
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(appText("新建文件夹", english)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = createFolderName,
                        onValueChange = { createFolderName = it; createFolderError = false },
                        label = { Text(appText("文件夹名称", english)) },
                        singleLine = true,
                        isError = createFolderError
                    )
                    if (createFolderError) {
                        Text(
                            appText("文件夹创建失败", english),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = createFolderName.trim().isNotBlank(),
                    onClick = {
                        val parent = currentFolder
                        val name = createFolderName.trim()
                        if (parent != null) {
                            scope.launch {
                                val created = onCreateFolder(parent, name)
                                if (created) {
                                    createdFolders = createdFolders + "$parent/$name"
                                    showCreateFolderDialog = false
                                    query = ""
                                } else {
                                    createFolderError = true
                                }
                            }
                        }
                    }
                ) { Text(appText("创建", english)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text(appText("取消", english)) }
            }
        )
    }
}

/**
 * Folder indexes produced from storage walks historically kept child names
 * separately from their parents. Reconstruct the full path before a transfer
 * destination is selected, otherwise an empty nested folder such as
 * AI生成/AI生成1 is mistaken for a root-level AI生成1 folder.
 */
private fun expandIndexedFolderPaths(
    folderNames: Set<String>,
    folderChildren: Map<String, Set<String>>
): Set<String> {
    val paths = linkedSetOf<String>()
    val childNames = folderChildren.values.flatten().toSet()
    val roots = folderNames.filter { '/' !in it && it !in childNames }

    fun visit(path: String, ancestry: Set<String>) {
        if (!paths.add(path)) return
        val leaf = path.substringAfterLast('/')
        if (leaf in ancestry) return
        folderChildren[leaf].orEmpty().forEach { child ->
            if (child.isNotBlank()) visit("$path/$child", ancestry + leaf)
        }
    }

    roots.forEach { visit(it, emptySet()) }
    // Preserve names that cannot be connected because the provider returned an
    // incomplete index; they remain selectable instead of disappearing.
    folderNames.filter { '/' !in it }.forEach { name ->
        if (name !in childNames) paths += name
    }
    return paths
}

@Composable
private fun DocumentPreviewThumbnail(item: MediaItem, modifier: Modifier = Modifier.size(62.dp)) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                openMediaInputStream(context, item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / sample > 180 || bounds.outHeight / sample > 180) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                openMediaInputStream(context, item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Text("图片", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun RecentFolderCell(name: String, selected: Boolean, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .14f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            name,
            modifier = Modifier.padding(top = 5.dp),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    val palettes = listOf(
        listOf(Color(0xFFD8E9EF), Color(0xFF6594B1), Color(0xFF334B59)),
        listOf(Color(0xFFE9EDC8), Color(0xFF9EBE71), Color(0xFF4E735A)),
        listOf(Color(0xFFF4DEC8), Color(0xFFD49162), Color(0xFF754938)),
        listOf(Color(0xFFE7DDF0), Color(0xFF9E80B7), Color(0xFF504663)),
        listOf(Color(0xFFDCEFED), Color(0xFF75AEB5), Color(0xFF466476))
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(palettes[index % palettes.size]))
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null && !isNew) {
                MediaThumbnail(
                    item = cover,
                    modifier = Modifier.fillMaxSize(),
                    requestedSize = 220,
                    contentScale = ContentScale.Crop,
                    showVideoDuration = false
                )
            } else if (isNew) {
                Text("+", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(19.dp)
                    .clickable(onClick = onToggle)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .72f), CircleShape)
                    .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Text("✓", color = Color.White, fontSize = 11.sp)
            }
        }
            Text(
                name,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, start = 2.dp, end = 2.dp)
                    .clickable(onClick = onOpen),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )
    }
}

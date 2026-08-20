package com.example.album.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.album.data.DuplicateGroup
import com.example.album.data.MediaItem
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.data.PixivArchiveRecord
import com.example.album.data.PixivArchivePhase
import com.example.album.data.PixivArchiveProgress
import com.example.album.data.PixivArchiveRepository
import com.example.album.data.openMediaInputStream
import com.example.album.data.PixivArchiveStatus
import com.example.album.data.RecycleEntry
import com.example.album.data.SimilarGroup
import com.example.album.data.ThumbnailRepository
import com.example.album.PixivWebActivity
import com.example.album.ui.components.MediaThumbnail
import com.example.album.ui.components.VaultConfirmationSheet
import com.example.album.ui.components.VaultChoiceConfirmationSheet
import com.example.album.ui.components.VaultTextInputDialog
import com.example.album.ui.components.SelectionTopBar
import com.example.album.ui.components.SelectionSubBar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

private enum class CleanupTab(val label: String) {
    Recycle("回收站"),
    Duplicates("重复图片"),
    Excluded("已排除")
}

@Composable
fun CleanupScreen(
    media: List<MediaItem>,
    recycleEntries: List<RecycleEntry>,
    excludedMedia: List<MediaItem>,
    onBack: () -> Unit,
    findDuplicates: suspend () -> List<DuplicateGroup>,
    confirmMediaDeletion: Boolean = true,
    recycleMediaDeletion: Boolean = true,
    onDeleteMedia: (List<MediaItem>) -> Unit,
    onRestoreRecycle: (List<RecycleEntry>) -> Unit,
    onDeleteRecycle: (List<RecycleEntry>) -> Unit,
    onRestoreExcluded: (String) -> Unit
) {
    val english = LocalAppEnglish.current
    var selectedTab by remember { mutableStateOf(CleanupTab.Recycle) }
    var groups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var scanning by remember { mutableStateOf(false) }
    var confirmDuplicateDelete by remember { mutableStateOf<List<MediaItem>?>(null) }
    var confirmRestore by remember { mutableStateOf<List<RecycleEntry>?>(null) }
    var confirmDelete by remember { mutableStateOf<RecycleEntry?>(null) }
    var confirmEmptyRecycle by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submitDuplicateDelete(deleting: List<MediaItem>) {
        if (deleting.isEmpty()) return
        onDeleteMedia(deleting)
        selectedUris = selectedUris - deleting.mapTo(hashSetOf()) { it.uri.toString() }
    }

    fun requestDuplicateDelete(deleting: List<MediaItem>) {
        if (deleting.isEmpty()) return
        if (confirmMediaDeletion) confirmDuplicateDelete = deleting else submitDuplicateDelete(deleting)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CleanupToolbar(selectedTab = selectedTab, onTabSelected = { selectedTab = it }, onBack = onBack)

        when (selectedTab) {
            CleanupTab.Duplicates -> DuplicateContent(media, groups, selectedUris, scanning, onScan = {
                scanning = true
                scope.launch {
                    groups = findDuplicates()
                    selectedUris = groups.flatMap { it.items.drop(1) }.mapTo(mutableSetOf()) { it.uri.toString() }
                    scanning = false
                }
            }, onToggle = { uri -> selectedUris = if (uri in selectedUris) selectedUris - uri else selectedUris + uri }, onDelete = { requestedUris ->
                val liveUris = media.mapTo(hashSetOf()) { it.uri.toString() }
                val deleting = groups.flatMap { it.items }.distinctBy { it.uri }.filter { it.uri.toString() in requestedUris && it.uri.toString() in liveUris }
                if (deleting.isNotEmpty()) {
                    requestDuplicateDelete(deleting)
                }
            }, onDeleteAll = {
                val liveUris = media.mapTo(hashSetOf()) { it.uri.toString() }
                val deleting = groups.flatMap { it.items.drop(1) }.distinctBy { it.uri }
                    .filter { it.uri.toString() in liveUris }
                if (deleting.isNotEmpty()) {
                    requestDuplicateDelete(deleting)
                }
            })
            CleanupTab.Recycle -> RecycleContent(
                entries = recycleEntries,
                onRestore = { entry -> confirmRestore = listOf(entry) },
                onDelete = { confirmDelete = it },
                onRestoreAll = { if (recycleEntries.isNotEmpty()) confirmRestore = recycleEntries },
                onDeleteAll = { confirmEmptyRecycle = true }
            )
            CleanupTab.Excluded -> ExcludedContent(excludedMedia, onRestoreExcluded)
        }
    }

    confirmDuplicateDelete?.let { deleting ->
        VaultConfirmationSheet(
            title = if (recycleMediaDeletion) {
                if (english) "Move to Trash" else "移到回收站"
            } else {
                if (english) "Delete permanently" else "永久删除"
            },
            body = if (english) {
                if (recycleMediaDeletion) "${deleting.size} duplicate item(s) will be moved to Trash." else "${deleting.size} duplicate item(s) will be permanently deleted and cannot be recovered."
            } else {
                if (recycleMediaDeletion) "${deleting.size} 个重复项将移到回收站。" else "${deleting.size} 个重复项将被永久删除且无法恢复。"
            },
            confirmLabel = if (recycleMediaDeletion) {
                if (english) "Move to Trash" else "移到回收站"
            } else {
                if (english) "Delete" else "删除"
            },
            danger = true,
            onDismiss = { confirmDuplicateDelete = null },
            onConfirm = {
                confirmDuplicateDelete = null
                submitDuplicateDelete(deleting)
            }
        )
    }

    confirmRestore?.let { entries ->
        val restoreAll = entries.size > 1
        VaultConfirmationSheet(
            title = if (english) if (restoreAll) "Restore all" else "Restore" else if (restoreAll) "全部还原" else "还原",
            body = if (english) {
                if (restoreAll) "Restore ${entries.size} items to their original locations?" else "Restore \"${entries.first().originalName}\" to its original location?"
            } else {
                if (restoreAll) "将回收站中的 ${entries.size} 项还原到原位置？" else "将“${entries.first().originalName}”还原到原位置？"
            },
            confirmLabel = if (english) "Restore" else "还原",
            onDismiss = { confirmRestore = null },
            onConfirm = {
                confirmRestore = null
                onRestoreRecycle(entries)
            }
        )
    }
    confirmDelete?.let { entry ->
        VaultConfirmationSheet(
            title = appText("彻底删除", english),
            body = if (english) "\"${entry.originalName}\" cannot be recovered." else "“${entry.originalName}”将无法恢复。",
            confirmLabel = appText("彻底删除", english),
            danger = true,
            onDismiss = { confirmDelete = null },
            onConfirm = { onDeleteRecycle(listOf(entry)); confirmDelete = null }
        )
    }
    if (confirmEmptyRecycle) {
        VaultConfirmationSheet(
            title = appText("清空回收站", english),
            body = if (english) "${recycleEntries.size} items in Trash will be permanently deleted and cannot be recovered." else "回收站中的 ${recycleEntries.size} 项将被彻底删除且无法恢复。",
            confirmLabel = appText("清空回收站", english),
            danger = true,
            onDismiss = { confirmEmptyRecycle = false },
            onConfirm = { onDeleteRecycle(recycleEntries); confirmEmptyRecycle = false }
        )
    }
}

@Composable
fun PixivArchiveScreen(
    session: PixivArchiveSession,
    onStartScan: (Uri, Int) -> Unit,
    onBack: () -> Unit,
    onArchiveComplete: suspend () -> Unit,
    favoriteSelected: (List<MediaItem>) -> Boolean = { false },
    onFavorite: (List<MediaItem>) -> Unit = {},
    onCopy: (List<MediaItem>) -> Unit = {},
    onMove: (List<MediaItem>) -> Unit = {},
    onRename: (MediaItem, String) -> Uri? = { _, _ -> null },
    onShare: (List<MediaItem>) -> Unit = {},
    onDelete: (List<MediaItem>) -> Unit = {}
) {
    val english = LocalAppEnglish.current
    val context = LocalContext.current
    val selectedUris by session.selectedUris
    val records by session.records
    val selectableUris by session.selectableUris
    var renameItem by remember { mutableStateOf<MediaItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteItems by remember { mutableStateOf<List<MediaItem>?>(null) }

    fun selectedUrls(): List<String> = records
        .filter { it.uri.toString() in selectedUris }
        .mapNotNull { record -> record.pid?.let { pid -> "https://www.pixiv.net/artworks/$pid" } }

    fun mediaItem(record: PixivArchiveRecord): MediaItem = MediaItem(
        id = record.uri.toString().hashCode().toLong(),
        uri = record.uri,
        name = record.filename,
        folder = "Pixiv",
        mimeType = record.mimeType,
        isVideo = record.mimeType.startsWith("video/"),
        dateTaken = record.dateModified,
        dateModified = record.dateModified / 1000L,
        isDocument = true
    )

    fun selectedMedia(): List<MediaItem> = records
        .filter { it.uri.toString() in selectedUris }
        .map(::mediaItem)

    fun copySelectedUrls() {
        val urls = selectedUrls()
        if (urls.isEmpty()) return
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText("Pixiv", urls.joinToString("\n")))
        Toast.makeText(context, if (english) "Pixiv URLs copied" else "已复制 Pixiv 网址", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (selectedUris.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(58.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, appText("返回", english))
                }
                Text(
                    appText("Pixiv 文件归档", english),
                    modifier = Modifier.padding(start = 4.dp),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            val selectedItems = selectedMedia()
            SelectionTopBar(
                selected = selectedUris.size,
                onClose = { session.selectedUris.value = emptySet() },
                favoriteSelected = selectedItems.isNotEmpty() && favoriteSelected(selectedItems),
                onFavorite = { onFavorite(selectedItems); session.selectedUris.value = emptySet() },
                onCopy = { onCopy(selectedItems); session.selectedUris.value = emptySet() },
                onMove = { onMove(selectedItems); session.selectedUris.value = emptySet() },
                onRename = {
                    selectedItems.singleOrNull()?.let {
                        renameItem = it
                        renameText = it.name
                    }
                },
                renameEnabled = selectedItems.size == 1,
                onShare = { onShare(selectedItems) },
                onDelete = { deleteItems = selectedItems }
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            ArchiveContent(session, onStartScan, onArchiveComplete)
        }
        if (selectedUris.isNotEmpty()) {
            SelectionSubBar(
                selected = selectedUris.size,
                total = selectableUris.size,
                onSelectAll = {
                    session.selectedUris.value = if (
                        selectableUris.isNotEmpty() && selectedUris.containsAll(selectableUris)
                    ) emptySet() else selectableUris
                }
            )
        }
    }
    renameItem?.let { item ->
        VaultTextInputDialog(
            title = appText("重命名", english),
            value = renameText,
            onValueChange = { renameText = it },
            label = appText("文件名", english),
            confirmLabel = appText("保存", english),
            onDismiss = { renameItem = null },
            onConfirm = {
                val newName = renameText.trim()
                if (newName.isNotEmpty() && newName != item.name) {
                    val renamedUri = onRename(item, newName)
                    if (renamedUri != null) {
                        session.records.value = session.records.value.map { record ->
                            if (record.uri == item.uri) record.copy(uri = renamedUri, filename = newName) else record
                        }
                    }
                }
                renameItem = null
                session.selectedUris.value = emptySet()
            }
        )
    }
    deleteItems?.let { items ->
        VaultConfirmationSheet(
            title = appText("删除所选", english),
            body = if (english) "Delete ${items.size} selected files?" else "确定删除选中的 ${items.size} 个文件吗？",
            confirmLabel = appText("删除", english),
            danger = true,
            onDismiss = { deleteItems = null },
            onConfirm = {
                onDelete(items)
                deleteItems = null
                session.selectedUris.value = emptySet()
            }
        )
    }
}

@Composable
private fun CleanupToolbar(
    selectedTab: CleanupTab,
    onTabSelected: (CleanupTab) -> Unit,
    onBack: () -> Unit
) {
    val english = LocalAppEnglish.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, appText("返回设置", english), modifier = Modifier.size(23.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CleanupTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(
                            if (selected) Modifier
                                .shadow(2.dp, RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                            else Modifier
                        )
                        .clickable { onTabSelected(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        appText(tab.label, english),
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DuplicateContent(
    media: List<MediaItem>,
    groups: List<DuplicateGroup>,
    selectedUris: Set<String>,
    scanning: Boolean,
    onScan: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (Set<String>) -> Unit,
    onDeleteAll: () -> Unit
) {
    val english = LocalAppEnglish.current
    val liveUris = media.mapTo(hashSetOf()) { it.uri.toString() }
    val liveGroups = groups.map { it.copy(items = it.items.filter { item -> item.uri.toString() in liveUris }) }.filter { it.items.size > 1 }
    var collapsedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
    val allCollapsed = liveGroups.isNotEmpty() && liveGroups.all { it.hash in collapsedGroups }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 7.dp, end = 7.dp, bottom = 28.dp)
    ) {
        stickyHeader {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                CleanupSummary {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (liveGroups.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    collapsedGroups = if (allCollapsed) emptySet() else liveGroups.mapTo(mutableSetOf()) { it.hash }
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    appText(if (allCollapsed) "展开全部" else "折叠全部", english),
                                    modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = if (allCollapsed) 0f else 90f }
                                )
                            }
                        }
                        Text(if (english) "${liveGroups.size} duplicate groups" else "${liveGroups.size} 组重复图片", fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CleanupCommand("全盘查重", color = Color(0xFF22A447), enabled = !scanning, onClick = onScan)
                        if (liveGroups.isNotEmpty()) CleanupCommand("清理全部", color = Color(0xFFFF453A), onClick = onDeleteAll)
                    }
                }
            }
        }
        if (scanning) {
            item { CleanupBusy("正在计算文件哈希…") }
        } else if (liveGroups.isEmpty()) {
            item { CleanupEmpty("没有发现重复图片") }
        }
        items(liveGroups, key = { it.hash }) { group ->
            val collapsed = group.hash in collapsedGroups
            Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            collapsedGroups = if (collapsed) collapsedGroups - group.hash else collapsedGroups + group.hash
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            appText(if (collapsed) "展开" else "折叠", english),
                            modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = if (collapsed) 0f else 90f }
                        )
                    }
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (english) "Duplicate group ${liveGroups.indexOf(group) + 1}" else "重复组 ${liveGroups.indexOf(group) + 1}", fontSize = 13.sp)
                        Text(if (english) "  ${group.items.size} photos" else "  ${group.items.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    val selectedInGroup = group.items.mapTo(mutableSetOf()) { it.uri.toString() }.intersect(selectedUris)
                    CleanupCommand("清理所选", color = Color(0xFFFF453A), enabled = selectedInGroup.isNotEmpty()) { onDelete(selectedInGroup) }
                }
                if (collapsed) {
                    group.items.chunked(4).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            rowItems.forEach { item ->
                                val key = item.uri.toString()
                                Box(Modifier.weight(1f).aspectRatio(1f).clickable { onToggle(key) }) {
                                    MediaThumbnail(item, Modifier.fillMaxSize())
                                    CleanupSelectionMark(key in selectedUris, Modifier.align(Alignment.TopStart).padding(5.dp))
                                }
                            }
                            repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
                        }
                    }
                } else {
                    group.items.forEach { item -> DuplicateMediaRow(item, item.uri.toString() in selectedUris) { onToggle(item.uri.toString()) } }
                }
            }
        }
    }
}

@Composable
private fun DuplicateMediaRow(item: MediaItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(72.dp)) {
            MediaThumbnail(item, Modifier.fillMaxSize())
            CleanupSelectionMark(selected, Modifier.align(Alignment.TopStart).padding(5.dp))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CleanupProperty(item.folder, Modifier.weight(1f), primary = true)
                CleanupProperty(item.name, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CleanupProperty(formatMediaSize(item.size), Modifier.weight(1f))
                CleanupProperty(formatCleanupDate(item.dateTaken), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CleanupSelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.White)
            .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
    }
}

@Composable
private fun CleanupProperty(text: String, modifier: Modifier = Modifier, primary: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        color = if (primary) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = if (primary) 13.sp else 10.sp,
        lineHeight = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun formatCleanupDate(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(time.coerceAtLeast(0L)))

@Composable
private fun CleanupSummary(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 2.dp, end = 2.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun CleanupCommand(
    label: String,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val english = LocalAppEnglish.current
    Box(
        Modifier
            .height(40.dp)
            .shadow(if (enabled) 2.dp else 0.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else .42f)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(appText(label, english), color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CleanupEmpty(label: String) {
    val english = LocalAppEnglish.current
    Text(
        appText(label, english),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 72.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CleanupBusy(label: String) {
    val english = LocalAppEnglish.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(appText(label, english), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun SimilarContent(
    media: List<MediaItem>,
    groups: List<SimilarGroup>,
    selectedUris: Set<String>,
    scanning: Boolean,
    onToggle: (String) -> Unit,
    onKeepBest: () -> Unit,
    onSelectGroup: (SimilarGroup) -> Unit,
    onDelete: () -> Unit
) {
    val english = LocalAppEnglish.current
    val liveUris = media.mapTo(hashSetOf()) { it.uri.toString() }
    val liveGroups = groups.mapNotNull { group ->
        val items = group.items.filter { it.item.uri.toString() in liveUris }
        if (items.size < 2) null else group.copy(items = items, bestUri = group.bestUri.takeIf { it in liveUris } ?: items.first().item.uri.toString())
    }
    if (scanning) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(appText("正在分析缩略图…", english), modifier = Modifier.padding(top = 12.dp))
                Text(appText("仅在本机计算感知哈希", english), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (groups.isEmpty()) appText("点击右上角扫描", english) else if (english) "${liveGroups.size} similar groups" else "${liveGroups.size} 组相似图片", fontWeight = FontWeight.SemiBold)
                        Text(appText("综合画面、平均色与宽高比", english), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onKeepBest, enabled = liveGroups.isNotEmpty()) { Text(appText("保留最佳", english)) }
                }
                TextButton(onClick = onDelete, enabled = selectedUris.isNotEmpty(), modifier = Modifier.align(Alignment.End)) {
                    Text(if (english) "Clean selected (${selectedUris.size})" else "清理所选 (${selectedUris.size})")
                }
            }
        }
        items(liveGroups, key = { it.id }) { group ->
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (english) "Similar group · ${group.items.size} photos" else "相似组 · ${group.items.size}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSelectGroup(group) }) { Text(appText("选择其余", english)) }
                }
                group.items.forEach { similar ->
                    val item = similar.item
                    val key = item.uri.toString()
                    val best = key == group.bestUri
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !best) { onToggle(key) }.padding(horizontal = 15.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MediaThumbnail(item, Modifier.size(68.dp).clip(RoundedCornerShape(5.dp)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (best) {
                                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .13f), shape = RoundedCornerShape(4.dp)) {
                                        Text(appText("建议保留", english), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Text(
                                "${similar.width} × ${similar.height} · ${formatMediaSize(similar.size)} · ${if (english) "sharpness" else "清晰度"} ${similar.sharpness.toInt()}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(item.folder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(
                            Icons.Outlined.CheckCircle,
                            appText(if (best) "建议保留" else if (key in selectedUris) "已选择" else "未选择", english),
                            tint = when {
                                best -> MaterialTheme.colorScheme.primary.copy(alpha = .45f)
                                key in selectedUris -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatMediaSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.CHINA, "%.1f MB", bytes / 1024f / 1024f)
    bytes >= 1024L -> String.format(Locale.CHINA, "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}

@Composable
private fun RecycleContent(
    entries: List<RecycleEntry>,
    onRestore: (RecycleEntry) -> Unit,
    onDelete: (RecycleEntry) -> Unit,
    onRestoreAll: () -> Unit,
    onDeleteAll: () -> Unit
) {
    val english = LocalAppEnglish.current
    var collapsed by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 7.dp, end = 7.dp, bottom = 28.dp)
    ) {
        item {
            CleanupSummary {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { collapsed = !collapsed }, modifier = Modifier.size(38.dp)) {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                appText(if (collapsed) "展开" else "折叠", english),
                                modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = if (collapsed) 0f else 90f }
                            )
                        }
                    }
                    Text(if (english) "${entries.size} items" else "${entries.size} 项", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
                if (entries.isEmpty()) {
                    Text(if (english) "Items are kept for 60 days" else "项目保留60天", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CleanupCommand("全部还原", onClick = onRestoreAll)
                        CleanupCommand("清空回收站", color = Color(0xFFFF453A), onClick = onDeleteAll)
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            item { CleanupEmpty("回收站为空") }
        } else if (collapsed) {
            items(entries.chunked(4), key = { row -> row.joinToString { it.id } }) { rowEntries ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    rowEntries.forEach { entry ->
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            RecycleThumbnail(entry, Modifier.fillMaxSize())
                            Row(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(34.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f)),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { onRestore(entry) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Outlined.Restore, appText("还原", english), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDelete(entry) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Outlined.DeleteForever, appText("彻底删除", english), tint = Color(0xFFCC3B33), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    repeat(4 - rowEntries.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
                }
            }
        } else {
            items(entries, key = { it.id }) { entry -> RecycleMediaRow(entry, { onRestore(entry) }, { onDelete(entry) }) }
        }
    }
}

@Composable
private fun RecycleMediaRow(entry: RecycleEntry, onRestore: () -> Unit, onDelete: () -> Unit) {
    val english = LocalAppEnglish.current
    val bytes = remember(entry.id, entry.storedPath) { runCatching { File(entry.storedPath).length() }.getOrDefault(0L) }
    Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
        RecycleThumbnail(entry, Modifier.size(72.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CleanupProperty(entry.originalFolder, Modifier.weight(1f), primary = true)
                CleanupProperty(entry.originalName, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CleanupProperty(formatMediaSize(bytes), Modifier.weight(1f))
                CleanupProperty(formatCleanupDate(entry.dateTaken.takeIf { it > 0 } ?: entry.deletedAt), Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onRestore, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Restore, appText("还原", english), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.DeleteForever, appText("彻底删除", english), tint = Color(0xFFCC3B33), modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ExcludedContent(media: List<MediaItem>, onRestore: (String) -> Unit) {
    val english = LocalAppEnglish.current
    val folders = remember(media) { media.groupBy { it.folder } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 7.dp, end = 7.dp, bottom = 28.dp)
    ) {
        item {
            CleanupSummary {
                Text(if (english) "${folders.size} folders" else "${folders.size} 个文件夹", modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                if (folders.isNotEmpty()) CleanupCommand("全部还原") { folders.keys.forEach(onRestore) }
            }
        }
        if (folders.isEmpty()) item { CleanupEmpty("没有已排除文件夹") }
        items(folders.entries.toList(), key = { it.key }) { (folder, items) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
                MediaThumbnail(items.first(), Modifier.size(72.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(folder, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (english) "${items.size} items" else "${items.size} 项", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                IconButton(onClick = { onRestore(folder) }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Outlined.Restore, appText("恢复扫描", english), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

enum class ArchiveUiState { Idle, Scanning, Ready, Archiving, Complete, Error }

private enum class ArchiveResultFilter { All, Complete, Failed }

data class ArchiveActivity(
    val phase: PixivArchivePhase = PixivArchivePhase.Discover,
    val completed: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
    val currentFile: String = "",
    val currentArtist: String = "",
    val message: String = "等待开始",
    val logs: List<String> = emptyList()
)

/** Keeps archive work and its result alive while the archive page is not visible. */
class PixivArchiveSession {
    val records = mutableStateOf<List<PixivArchiveRecord>>(emptyList())
    val state = mutableStateOf(ArchiveUiState.Idle)
    val completed = mutableStateOf(0)
    val failed = mutableStateOf(0)
    val activity = mutableStateOf(ArchiveActivity())
    val selectedUris = mutableStateOf<Set<String>>(emptySet())
    val selectableUris = mutableStateOf<Set<String>>(emptySet())
    var scanJob: Job? = null

    fun reset() {
        if (scanJob?.isActive == true) return
        records.value = emptyList()
        state.value = ArchiveUiState.Idle
        completed.value = 0
        failed.value = 0
        activity.value = ArchiveActivity()
        selectedUris.value = emptySet()
        selectableUris.value = emptySet()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveContent(
    session: PixivArchiveSession,
    onStartScan: (Uri, Int) -> Unit,
    onArchiveComplete: suspend () -> Unit
) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val scope = rememberCoroutineScope()
    val repository = remember { PixivArchiveRepository(context) }
    val preferences = remember { context.getSharedPreferences("pixiv_archive", Context.MODE_PRIVATE) }
    val defaultPixivSourceUri = remember {
        DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Pictures/pixiv"
        )
    }
    var sourceUri by remember {
        mutableStateOf(preferences.getString("source_uri", null)?.let(Uri::parse) ?: defaultPixivSourceUri)
    }
    var targetUri by remember { mutableStateOf(preferences.getString("target_uri", null)?.let(Uri::parse)) }
    var keepName by remember { mutableStateOf(preferences.getBoolean("keep_name", true)) }
    var writeTags by remember { mutableStateOf(preferences.getBoolean("write_tags", true)) }
    var copyInsteadOfMove by remember { mutableStateOf(preferences.getBoolean("copy_instead_of_move", false)) }
    var maxBatchSize by remember { mutableIntStateOf(preferences.getInt("max_batch_size", 200).coerceIn(50, 1000)) }
    var records by session.records
    var state by session.state
    var completed by session.completed
    var failed by session.failed
    var activity by session.activity
    var confirmArchive by remember { mutableStateOf(false) }
    var showPixivLoginPrompt by remember { mutableStateOf(false) }
    var pixivSessionConnected by remember { mutableStateOf(false) }
    var checkingPixivLogin by remember { mutableStateOf(false) }
    var pixivLoginCheckFailed by remember { mutableStateOf(false) }
    var showPixivAccountActions by remember { mutableStateOf(false) }
    var resultFilter by remember { mutableStateOf(ArchiveResultFilter.All) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    var resultGrid by remember { mutableStateOf(false) }
    var selectedUris by session.selectedUris

    fun toggleRecordSelection(record: PixivArchiveRecord) {
        val key = record.uri.toString()
        selectedUris = if (key in selectedUris) selectedUris - key else selectedUris + key
    }

    LaunchedEffect(Unit) {
        pixivSessionConnected = repository.verifyAuthenticatedSession()
    }

    val pixivLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        scope.launch {
            if (result.resultCode != Activity.RESULT_OK) return@launch
            // WebView may flush its cookies a little after the login page closes.
            // Confirm the session from the repository after returning to the app.
            checkingPixivLogin = true
            pixivLoginCheckFailed = false
            pixivSessionConnected = false
            repeat(24) {
                if (!checkingPixivLogin) return@launch
                // On some real devices Pixiv rejects the follow-up HTTP request
                // even though WebView already has the authenticated PHPSESSID.
                // Treat that same WebView session as the fallback proof after
                // giving the API check a chance to succeed.
                if (repository.verifyAuthenticatedSession() || repository.hasAuthenticatedSession()) {
                    pixivSessionConnected = true
                    checkingPixivLogin = false
                    return@launch
                }
                delay(500)
            }
            checkingPixivLogin = false
            pixivLoginCheckFailed = true
        }
    }

    fun launchPixiv(url: String) {
        pixivLauncher.launch(
            Intent(context, PixivWebActivity::class.java).putExtra(PixivWebActivity.EXTRA_URL, url)
        )
    }

    fun showTreeConflict() {
        Toast.makeText(
            context,
            if (english) "Source and archive folders must not overlap" else "来源目录和归档目录不能互相包含",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun applyProgress(progress: PixivArchiveProgress) {
        activity = ArchiveActivity(
            phase = progress.phase,
            completed = progress.completed,
            total = progress.total,
            failed = progress.failed,
            currentFile = progress.currentFile,
            currentArtist = progress.currentArtist,
            message = progress.message,
            logs = if (progress.log.isBlank()) activity.logs else (listOf(progress.log) + activity.logs).take(4)
        )
    }

    fun storeTree(key: String, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            .recoverCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        preferences.edit().putString(key, uri.toString()).apply()
    }

    val sourceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val currentTarget = targetUri
        if (currentTarget != null && documentTreesOverlap(uri, currentTarget)) {
            showTreeConflict()
            return@rememberLauncherForActivityResult
        }
        storeTree("source_uri", uri)
        sourceUri = uri
        session.reset()
    }
    val targetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val currentSource = sourceUri
        if (currentSource != null && documentTreesOverlap(currentSource, uri)) {
            showTreeConflict()
            return@rememberLauncherForActivityResult
        }
        storeTree("target_uri", uri)
        targetUri = uri
    }

    fun archiveSingle(record: PixivArchiveRecord) {
        if (!pixivSessionConnected) {
            showPixivLoginPrompt = true
            return
        }
        val target = targetUri
        if (target == null) {
            targetLauncher.launch(null)
            return
        }
        if (!hasPersistedTreePermission(context, target, write = true)) {
            targetLauncher.launch(target)
            return
        }
        if (!record.canArchive || state == ArchiveUiState.Archiving) return
        scope.launch {
            state = ArchiveUiState.Archiving
            completed = 0
            failed = 0
            activity = ArchiveActivity(
                phase = PixivArchivePhase.Folders,
                total = 1,
                message = if (english) "Archiving selected image" else "正在归档所选图片"
            )
            val result = repository.archive(listOf(record), target, keepName, writeTags, copyInsteadOfMove) { update ->
                withContext(Dispatchers.Main) {
                    completed = update.completed
                    failed = update.failed
                    applyProgress(update)
                }
            }
            records = records.map { current ->
                result.records.firstOrNull { it.uri == current.uri } ?: current
            }
            completed = result.completed
            failed = result.failed
            state = if (result.failed == 0) ArchiveUiState.Ready else ArchiveUiState.Error
            onArchiveComplete()
        }
    }
    fun rescanFailed() {
        if (!pixivSessionConnected || state == ArchiveUiState.Archiving) return
        retryJob?.cancel()
        val pending = records.filter { it.status == PixivArchiveStatus.Warning }.take(maxBatchSize)
        if (pending.isEmpty()) return
        completed = 0
        failed = 0
        activity = ArchiveActivity(message = if (english) "正在重新查询未成功图片" else "正在重新查询未成功图片")
        retryJob = scope.launch {
            state = ArchiveUiState.Scanning
            activity = ArchiveActivity(
                phase = PixivArchivePhase.Metadata,
                total = pending.size,
                message = if (english) "Retrying artwork lookup" else "正在重新查询未成功图片"
            )
            try {
                val updated = repository.rescan(
                    pending,
                    maxItems = maxBatchSize,
                    onProgress = { update ->
                    withContext(Dispatchers.Main) {
                        completed = update.completed
                        failed = update.failed
                        applyProgress(update)
                    }
                    },
                    onRecord = { result ->
                    withContext(Dispatchers.Main) {
                        records = records.map { current ->
                            if (current.uri == result.uri) result else current
                        }
                    }
                    }
                )
                val byUri = updated.associateBy { it.uri }
                records = records.map { byUri[it.uri] ?: it }
                state = ArchiveUiState.Ready
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = error.message ?: if (english) "Retry failed" else "重新扫描失败"
                activity = activity.copy(phase = PixivArchivePhase.Error, message = message)
                state = ArchiveUiState.Error
            }
        }
    }
    // The slider limits discovery only; every record returned by that scan can be archived.
    val batchRecords = records
    val readyCount = batchRecords.count { it.canArchive }
    val warningCount = batchRecords.count { it.status == PixivArchiveStatus.Warning }
    val retryCount = records.count { it.status == PixivArchiveStatus.Warning }
    val failedRecords = batchRecords.filter {
        it.status == PixivArchiveStatus.Warning || it.status == PixivArchiveStatus.Failed
    }
    val completeRecords = batchRecords.filter { it !in failedRecords }
    val visibleBatchRecords = when (resultFilter) {
        ArchiveResultFilter.All -> batchRecords
        ArchiveResultFilter.Complete -> completeRecords
        ArchiveResultFilter.Failed -> failedRecords
    }
    LaunchedEffect(visibleBatchRecords) {
        val visibleUris = visibleBatchRecords.mapTo(hashSetOf()) { it.uri.toString() }
        session.selectableUris.value = visibleUris
        session.selectedUris.value = session.selectedUris.value.intersect(visibleUris)
    }

    fun requestArchive() {
        if (!pixivSessionConnected) {
            showPixivLoginPrompt = true
        } else if (readyCount == 0) {
            Toast.makeText(
                context,
                if (english) "No recognized Pixiv files can be archived" else "没有可归档的 Pixiv 文件，请检查扫描结果",
                Toast.LENGTH_SHORT
            ).show()
        } else if (targetUri == null || !hasPersistedTreePermission(context, targetUri!!, write = true)) {
            targetLauncher.launch(null)
        } else {
            confirmArchive = true
        }
    }

    fun copyPixivUrl(record: PixivArchiveRecord) {
        val pid = record.pid ?: return
        val url = "https://www.pixiv.net/artworks/$pid"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Pixiv", url))
        Toast.makeText(context, if (english) "Pixiv URL copied" else "已复制 Pixiv 网址", Toast.LENGTH_SHORT).show()
    }
    val progress = when {
        activity.phase == PixivArchivePhase.Ready || activity.phase == PixivArchivePhase.Complete -> 1f
        activity.total > 0 -> (activity.completed + activity.failed).toFloat() / activity.total
        else -> 0f
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 9.dp, top = 2.dp, end = 9.dp, bottom = 28.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF0096FA).copy(alpha = .10f))
                    .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 15.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF0096FA)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("P", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(appText("Pixiv 文件归档", english), modifier = Modifier.weight(1f).padding(start = 9.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    CleanupCommand(
                        label = if (pixivSessionConnected) appText("已登录 Pixiv", english) else appText("登录 Pixiv", english)
                    ) {
                        if (pixivSessionConnected) showPixivAccountActions = true
                        else launchPixiv(PixivWebActivity.LOGIN_URL)
                    }
                }
                Text(
                    if (pixivSessionConnected) {
                        if (english) "Pixiv session connected · metadata lookup uses this account" else "Pixiv 会话已连接 · 作品检索将使用此账号"
                    } else {
                        if (english) "Pixiv session not connected · sign in before scanning" else "Pixiv 会话未连接 · 登录后才能开始检索"
                    },
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                ArchiveFolderRow("Pixiv 来源目录", treeName(context, sourceUri, appText("未选择", english))) { sourceLauncher.launch(sourceUri) }
                ArchiveFolderRow("归档目标目录", treeName(context, targetUri, appText("未选择", english))) { targetLauncher.launch(targetUri) }
                ArchiveToggleRow("将 Pixiv tags 写入图片信息", writeTags) {
                    writeTags = it
                    preferences.edit().putBoolean("write_tags", it).apply()
                }
                ArchiveToggleRow("保留原始文件名", keepName) {
                    keepName = it
                    preferences.edit().putBoolean("keep_name", it).apply()
                }
                ArchiveToggleRow("归档时复制（保留原图）", copyInsteadOfMove) {
                    copyInsteadOfMove = it
                    preferences.edit().putBoolean("copy_instead_of_move", it).apply()
                }
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (english) "Scan limit" else "单次扫描上限", modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Text("$maxBatchSize", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    val sliderEnabled = state != ArchiveUiState.Scanning && state != ArchiveUiState.Archiving
                    val activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = if (sliderEnabled) 1f else .55f)
                    val inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (sliderEnabled) 1f else .55f)
                    val trackStrokeWidth = with(LocalDensity.current) { 12.dp.toPx() }
                    val thumbTrackGap = with(LocalDensity.current) { 3.dp.toPx() }
                    Slider(
                        value = maxBatchSize.toFloat(),
                        onValueChange = { value ->
                            val next = (value / 50f).roundToInt() * 50
                            maxBatchSize = next.coerceIn(50, 1000)
                            preferences.edit().putInt("max_batch_size", maxBatchSize).apply()
                        },
                        valueRange = 50f..1000f,
                        steps = 0,
                        enabled = sliderEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        track = { sliderState ->
                            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                                val centerY = size.height / 2f
                                val fraction = ((sliderState.value - sliderState.valueRange.start) /
                                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start))
                                    .coerceIn(0f, 1f)
                                val thumbCenter = size.width * fraction
                                drawLine(
                                    color = inactiveTrackColor,
                                    start = androidx.compose.ui.geometry.Offset(thumbCenter + thumbTrackGap, centerY),
                                    end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                                    strokeWidth = trackStrokeWidth,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                drawLine(
                                    color = activeTrackColor,
                                    start = androidx.compose.ui.geometry.Offset(0f, centerY),
                                    end = androidx.compose.ui.geometry.Offset(thumbCenter - thumbTrackGap, centerY),
                                    strokeWidth = trackStrokeWidth,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                        },
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            thumbColor = MaterialTheme.colorScheme.primary,
                            disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                            disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledThumbColor = MaterialTheme.colorScheme.primary,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent,
                            disabledActiveTickColor = Color.Transparent,
                            disabledInactiveTickColor = Color.Transparent
                        ),
                        thumb = {
                            Box(
                                Modifier.size(16.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .border(4.dp, Color.White, CircleShape)
                            )
                        }
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 12.dp, end = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArchivePrimaryButton(
                    label = if (state == ArchiveUiState.Scanning) "重新开始" else "开始扫描",
                    onClick = {
                        if (!pixivSessionConnected) {
                            showPixivLoginPrompt = true
                            return@ArchivePrimaryButton
                        }
                        val source = sourceUri ?: return@ArchivePrimaryButton sourceLauncher.launch(null)
                        if (!hasPersistedTreePermission(context, source, write = false)) {
                            sourceLauncher.launch(source)
                            return@ArchivePrimaryButton
                        }
                        preferences.edit().putInt("max_batch_size", maxBatchSize).apply()
                        onStartScan(source, maxBatchSize)
                    },
                    enabled = state != ArchiveUiState.Archiving,
                    modifier = Modifier.weight(1f),
                    filled = false
                )
                ArchivePrimaryButton(
                    label = if (state == ArchiveUiState.Archiving) "正在归档..." else "开始归档",
                    onClick = { requestArchive() },
                    enabled = state != ArchiveUiState.Scanning && state != ArchiveUiState.Archiving,
                    modifier = Modifier.weight(1f),
                    filled = true
                )
            }
            if (state == ArchiveUiState.Scanning || state == ArchiveUiState.Archiving) {
                ArchiveActivityPanel(activity, progress, state == ArchiveUiState.Scanning && activity.total == 0)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(appText("扫描结果", english), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                IconButton(
                    onClick = { resultGrid = !resultGrid },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        if (resultGrid) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                        if (resultGrid) appText("列表显示", english) else appText("网格显示", english),
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(start = 6.dp)) {
                    if (resultFilter != ArchiveResultFilter.Complete) {
                        ArchiveResultActionButton(
                            label = if (state == ArchiveUiState.Scanning) (if (english) "Restart" else "重新开始") else if (english) "Retry" else "重新扫描",
                            enabled = retryCount > 0 && state != ArchiveUiState.Archiving,
                            filled = true,
                            onClick = {
                                if (!pixivSessionConnected) {
                                    showPixivLoginPrompt = true
                                } else if (state == ArchiveUiState.Scanning) {
                                    val source = sourceUri
                                    if (source != null && hasPersistedTreePermission(context, source, write = false)) {
                                        retryJob?.cancel()
                                        onStartScan(source, maxBatchSize)
                                    } else {
                                        sourceLauncher.launch(source)
                                    }
                                } else {
                                    rescanFailed()
                                }
                            }
                        )
                    }
                    IconButton(
                        onClick = {
                            retryJob?.cancel()
                            retryJob = null
                            session.reset()
                            resultFilter = ArchiveResultFilter.All
                        },
                        enabled = records.isNotEmpty() &&
                            state != ArchiveUiState.Scanning &&
                            state != ArchiveUiState.Archiving,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.DeleteForever,
                            contentDescription = appText("清空扫描结果", english),
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ArchiveResultTab("全部", "All", batchRecords.size, resultFilter == ArchiveResultFilter.All, Modifier.weight(1f)) { resultFilter = ArchiveResultFilter.All; session.selectedUris.value = emptySet() }
                ArchiveResultTab("完成", "Complete", completeRecords.size, resultFilter == ArchiveResultFilter.Complete, Modifier.weight(1f)) { resultFilter = ArchiveResultFilter.Complete; session.selectedUris.value = emptySet() }
                ArchiveResultTab("失败", "Failed", failedRecords.size, resultFilter == ArchiveResultFilter.Failed, Modifier.weight(1f)) { resultFilter = ArchiveResultFilter.Failed; session.selectedUris.value = emptySet() }
            }
        }
        if (records.isEmpty() && state != ArchiveUiState.Scanning) {
            item {
                Text(
                    if (english) "Choose a source folder to scan. The app extracts PIDs from filenames and queries Pixiv." else "选择来源目录后扫描，应用将从文件名提取 PID 并查询 Pixiv。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 52.dp),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (!resultGrid) items(visibleBatchRecords, key = { it.uri.toString() }) { record ->
            ArchiveRecordRow(
                record = record,
                writeTags = writeTags,
                archiving = state == ArchiveUiState.Archiving,
                selected = record.uri.toString() in selectedUris,
                selectionMode = selectedUris.isNotEmpty(),
                onSelect = { toggleRecordSelection(record) },
                onArchive = { archiveSingle(record) },
                onOpen = {
                    record.pid?.let { pid -> launchPixiv("https://www.pixiv.net/artworks/$pid") }
                },
                onCopyUrl = { copyPixivUrl(record) }
            )
        }
        else items(visibleBatchRecords.chunked(3), key = { row -> row.firstOrNull()?.uri.toString() }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { record ->
                ArchiveGridItem(
                    record,
                    Modifier.weight(1f),
                    selected = record.uri.toString() in selectedUris,
                    onSelect = { toggleRecordSelection(record) }
                )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (!pixivSessionConnected) {
        Box(
            Modifier.fillMaxSize()
                .padding(top = 112.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .78f))
                .clickable { }
        )
    }
    }

    if (confirmArchive) {
        VaultConfirmationSheet(
            title = appText("确认文件归档", english),
            body = if (english) "$readyCount photos will be grouped by artist and moved to ${treeName(context, targetUri, "the selected folder")}. ${if (writeTags) "Pixiv tags will be written to image information." else "Image tags will not be changed."} Source files are deleted only after a successful copy." else "$readyCount 张图片将按画师名称和 UID 建立文件夹并移动到 ${treeName(context, targetUri, "所选目标目录")}。${if (writeTags) "Pixiv tags 将写入图片信息。" else "不会修改图片 tags。"}只有复制成功的来源文件才会删除。",
            confirmLabel = appText("开始归档", english),
            onDismiss = { confirmArchive = false },
            onConfirm = confirm@{
                val target = targetUri ?: return@confirm
                confirmArchive = false
                scope.launch {
                    state = ArchiveUiState.Archiving
                    completed = 0
                    failed = 0
                    activity = ArchiveActivity(phase = PixivArchivePhase.Folders, total = readyCount, message = if (english) "Preparing artist folders" else "正在准备画师目录")
                            val result = repository.archive(records, target, keepName, writeTags, copyInsteadOfMove) { update ->
                        withContext(Dispatchers.Main) {
                            completed = update.completed
                            failed = update.failed
                            applyProgress(update)
                        }
                    }
                    records = result.records
                    completed = result.completed
                    failed = result.failed
                    state = if (result.failed == 0) ArchiveUiState.Complete else ArchiveUiState.Error
                    onArchiveComplete()
                }
            }
        )
    }

    if (showPixivLoginPrompt) {
        VaultConfirmationSheet(
            title = appText("登录 Pixiv", english),
            body = if (english) {
                "Sign in to Pixiv before scanning artwork information or archiving files."
            } else {
                "请先登录 Pixiv，才能扫描作品信息或归档文件。"
            },
            confirmLabel = appText("登录 Pixiv", english),
            onDismiss = { showPixivLoginPrompt = false },
            onConfirm = {
                showPixivLoginPrompt = false
                launchPixiv(PixivWebActivity.LOGIN_URL)
            }
        )
    }

    if (checkingPixivLogin) {
        VaultConfirmationSheet(
            title = appText("正在确认 Pixiv 登录", english),
            body = if (english) {
                "Waiting for the login status to sync. Please wait."
            } else {
                "正在等待登录状态同步，请稍候。"
            },
            confirmLabel = appText("取消等待", english),
            onDismiss = { checkingPixivLogin = false },
            onConfirm = { checkingPixivLogin = false }
        )
    }

    if (pixivLoginCheckFailed) {
        VaultConfirmationSheet(
            title = appText("未检测到登录", english),
            body = if (english) {
                "Pixiv login was not detected. Please complete login in the web page and try again."
            } else {
                "暂时没有检测到 Pixiv 登录状态，请在网页中完成登录后重试。"
            },
            confirmLabel = appText("重新登录", english),
            onDismiss = { pixivLoginCheckFailed = false },
            onConfirm = {
                pixivLoginCheckFailed = false
                launchPixiv(PixivWebActivity.LOGIN_URL)
            }
        )
    }

    if (showPixivAccountActions) {
        VaultChoiceConfirmationSheet(
            title = appText("Pixiv 账号", english),
            choices = listOf(appText("退出 Pixiv 登录", english), appText("更换 Pixiv 账号", english)),
            onDismiss = { showPixivAccountActions = false },
            onChoice = { choice ->
                showPixivAccountActions = false
                when (choice) {
                    appText("退出 Pixiv 登录", english) -> {
                        repository.clearAuthenticatedSession()
                        pixivSessionConnected = false
                    }
                    appText("更换 Pixiv 账号", english) -> {
                        repository.clearAuthenticatedSession()
                        pixivSessionConnected = false
                        launchPixiv(PixivWebActivity.LOGIN_URL)
                    }
                }
            }
        )
    }
}

private fun documentTreesOverlap(first: Uri, second: Uri): Boolean = runCatching {
    if (first.authority != second.authority) return@runCatching false
    fun normalizedTreeId(uri: Uri): String = DocumentsContract.getTreeDocumentId(uri)
        .replace(':', '/')
        .trimEnd('/')
    val firstId = normalizedTreeId(first)
    val secondId = normalizedTreeId(second)
    firstId == secondId || firstId.startsWith("$secondId/") || secondId.startsWith("$firstId/")
}.getOrDefault(first == second)

private fun hasPersistedTreePermission(context: Context, uri: Uri, write: Boolean): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) return true
    return context.contentResolver.persistedUriPermissions.any { permission ->
        permission.uri == uri && permission.isReadPermission && (!write || permission.isWritePermission)
    }
}

@Composable
private fun ArchiveStageGrid(phase: PixivArchivePhase) {
    val english = LocalAppEnglish.current
    val stages = listOf(
        PixivArchivePhase.Discover to if (english) "Read files" else "读取文件",
        PixivArchivePhase.Metadata to if (english) "Artwork info" else "查询作品",
        PixivArchivePhase.Folders to if (english) "Folders" else "创建目录",
        PixivArchivePhase.Tags to if (english) "Tags" else "写入标签",
        PixivArchivePhase.Move to if (english) "Move files" else "移动文件",
        PixivArchivePhase.Complete to if (english) "Complete" else "完成"
    )
    val activeIndex = stages.indexOfFirst { it.first == phase }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        stages.chunked(3).forEachIndexed { rowIndex, rowStages ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                rowStages.forEachIndexed { columnIndex, (_, label) ->
                    val index = rowIndex * 3 + columnIndex
                    val done = phase == PixivArchivePhase.Complete ||
                        (phase == PixivArchivePhase.Ready && index <= 1) ||
                        activeIndex > index
                    val color = when {
                        done -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        Modifier.weight(1f).heightIn(min = 28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color.copy(alpha = if (done) .12f else .07f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = color, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveActivityPanel(activity: ArchiveActivity, progress: Float, indeterminate: Boolean) {
    val english = LocalAppEnglish.current
    val pending = (activity.total - activity.completed - activity.failed).coerceAtLeast(0)
    val phase = when (activity.phase) {
        PixivArchivePhase.Discover -> if (english) "Reading source files" else "正在读取来源文件"
        PixivArchivePhase.Metadata -> if (english) "Querying Pixiv information" else "正在查询 Pixiv 信息"
        PixivArchivePhase.Ready -> if (english) "Scan complete" else "扫描完成，等待归档"
        PixivArchivePhase.Folders -> if (english) "Creating artist folders" else "正在创建画师目录"
        PixivArchivePhase.Tags -> if (english) "Writing image tags" else "正在写入图片标签"
        PixivArchivePhase.Move -> if (english) "Moving images" else "正在移动图片"
        PixivArchivePhase.Complete -> if (english) "Archive complete" else "归档完成"
        PixivArchivePhase.Error -> if (english) "Archive paused" else "归档已暂停"
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(phase, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (indeterminate) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
        else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp))
        ArchiveStageGrid(activity.phase)
        Text(activity.message + activity.currentArtist.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        activity.currentFile.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (english) "Completed ${activity.completed}" else "完成 ${activity.completed}", fontSize = 11.sp)
            Text(if (english) "Pending $pending" else "等待 $pending", fontSize = 11.sp)
            Text(if (english) "Failed ${activity.failed}" else "失败 ${activity.failed}", color = if (activity.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        activity.logs.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp) }
    }
}

@Composable
private fun ArchiveFolderRow(label: String, value: String, onClick: () -> Unit) {
    val english = LocalAppEnglish.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(appText(label, english), fontSize = 13.sp)
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
        Text(appText("更改", english), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
    }
}

@Composable
private fun ArchiveToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val english = LocalAppEnglish.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onCheckedChange(!checked) }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(appText(label, english), modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ArchiveResultTab(
    label: String,
    englishLabel: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (LocalAppEnglish.current) englishLabel else label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(count.toString(), fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun ArchiveResultActionButton(label: String, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) MaterialTheme.colorScheme.primary else Color(0xFFE53935),
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(label, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun ArchivePrimaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    filled: Boolean
) {
    val accent = MaterialTheme.colorScheme.primary
    val english = LocalAppEnglish.current
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (filled) accent else accent.copy(alpha = .12f))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else .42f),
        contentAlignment = Alignment.Center
    ) {
        Text(appText(label, english), color = if (filled) Color.White else accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ArchiveRecordRow(
    record: PixivArchiveRecord,
    writeTags: Boolean,
    archiving: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onSelect: () -> Unit,
    onArchive: () -> Unit,
    onOpen: () -> Unit,
    onCopyUrl: () -> Unit
) {
    val english = LocalAppEnglish.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .combinedClickable(onClick = {}, onLongClick = onCopyUrl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(68.dp)) {
            ArchiveThumbnail(
                record,
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(5.dp))
                    .combinedClickable(onClick = { if (selectionMode) onSelect() }, onLongClick = onSelect)
            )
            if (selectionMode) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(5.dp).size(20.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape
                        )
                        .border(
                            1.5.dp,
                            if (selected) MaterialTheme.colorScheme.primary else Color.White,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
        Column(
            Modifier.weight(1f).padding(horizontal = 12.dp)
                .combinedClickable(
                    onClick = { if (selectionMode) onSelect() },
                    onLongClick = { if (selectionMode) onSelect() else onCopyUrl() }
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(record.filename, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val metadata = record.metadata
            Text(
                metadata?.let { "${it.title} · PID ${record.pid}" } ?: record.pid?.let { "PID $it" } ?: if (english) "PID not recognized" else "无法识别 PID",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            metadata?.let {
                val artist = if (english && it.artist == "未识别画师") "Unknown artist" else it.artist
                Text("$artist [${it.artistId}]", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                if (it.tags.isNotEmpty()) {
                    Text(
                        it.tags.joinToString(" · "),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                archiveStatusMessage(record.message, english) + if (writeTags && record.status != PixivArchiveStatus.Failed && record.metadata != null) (if (english) " · write tags" else " · 写入 tags") else "",
                color = when (record.status) {
                    PixivArchiveStatus.Failed, PixivArchiveStatus.Warning -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (record.pid != null) IconButton(onClick = onOpen, enabled = !archiving) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, appText("打开 Pixiv 作品", english)) }
        when {
            record.status == PixivArchiveStatus.Archived ->
                Text(appText("已归档", english), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp))
            record.canArchive ->
                ArchivePrimaryButton(
                    label = if (archiving) "归档中..." else "归档",
                    onClick = onArchive,
                    enabled = !archiving,
                    modifier = Modifier.width(62.dp),
                    filled = true
                )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ArchiveGridItem(
    record: PixivArchiveRecord,
    modifier: Modifier,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Column(
        modifier.padding(vertical = 5.dp)
            .combinedClickable(onClick = onSelect, onLongClick = onSelect)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(5.dp))) {
            ArchiveThumbnail(record, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(20.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        CircleShape
                    )
                    .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
            }
            if (record.status == PixivArchiveStatus.Warning) {
                Text(
                    "!",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            record.filename,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp)
        )
    }
}

private fun archiveStatusMessage(message: String, english: Boolean): String {
    val archivedPrefix = "已归档至 "
    return if (english && message.startsWith(archivedPrefix)) {
        "Archived to ${message.removePrefix(archivedPrefix)}"
    } else {
        appText(message, english)
    }
}

@Composable
private fun ArchiveThumbnail(record: PixivArchiveRecord, modifier: Modifier) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("album_settings", Context.MODE_PRIVATE) }
    val item = remember(record.uri, record.filename, record.mimeType) {
        MediaItem(
            id = record.uri.toString().hashCode().toLong() and 0xffffffffL,
            uri = record.uri,
            name = record.filename,
            folder = "Pixiv",
            dateTaken = 0L,
            mimeType = record.mimeType,
            isDocument = true
        )
    }
    val requestedSize = 360
    val bitmap by produceState<Bitmap?>(
        initialValue = ThumbnailRepository.peek(item, requestedSize, preferences),
        item.uri,
        item.name,
        item.mimeType
    ) {
        value = ThumbnailRepository.load(context, item, requestedSize, preferences)
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), record.filename, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

private fun treeName(context: Context, uri: Uri?, fallback: String): String {
    if (uri == null) return fallback
    return DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: fallback
}

@Composable
private fun RecycleThumbnail(entry: RecycleEntry, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, entry.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (entry.systemTrashed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(Uri.parse(entry.sourceUri), android.util.Size(256, 256), null)
                } else if (entry.isVideo) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(entry.storedPath)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 256)
                        } else {
                            @Suppress("DEPRECATION")
                            retriever.getFrameAtTime(0)
                        }
                    } finally {
                        retriever.release()
                    }
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(entry.storedPath, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
                    BitmapFactory.decodeFile(entry.storedPath, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), entry.originalName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

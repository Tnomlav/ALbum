package com.example.album.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.album.ui.theme.VaultDimens
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText

@Composable
fun SelectionTopBar(
    selected: Int,
    favoriteSelected: Boolean = false,
    onFavorite: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    renameEnabled: Boolean = true,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSlideshow: (() -> Unit)? = null,
    onOpenWith: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onEditTags: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onWallpaper: (() -> Unit)? = null,
    onExclude: (() -> Unit)? = null,
    selectingFolders: Boolean = false,
    onClose: () -> Unit,
    chromeAlpha: Float = 1f
) {
    val english = LocalAppEnglish.current
    var menuOpen by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.alpha(chromeAlpha), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                // Keep the right action group on the same screen-edge baseline as the normal top bar.
                .height(VaultDimens.HeaderContentHeight).padding(start = 6.dp, end = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, appText("返回", english))
            }
            Text(
                if (selectingFolders) appText("已选择文件夹", english) else appText("已选择", english),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onDelete, enabled = selected > 0) { Icon(Icons.Outlined.Delete, appText("删除所选", english)) }
            IconButton(onClick = onMove, enabled = selected > 0) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, appText("移动", english)) }
            IconButton(onClick = onRename, enabled = selected == 1 && renameEnabled) { Icon(Icons.Outlined.Edit, appText("重命名", english)) }
            IconButton(onClick = onFavorite, enabled = selected > 0) {
                Icon(
                    if (favoriteSelected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    if (favoriteSelected) appText("取消收藏所选", english) else appText("收藏所选", english),
                    tint = if (favoriteSelected) androidx.compose.ui.graphics.Color(0xFFFFD60A) else androidx.compose.ui.graphics.Color(0xFF1A1A1A)
                )
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuOpen = true }, enabled = selected > 0) { Icon(Icons.Outlined.MoreVert, appText("更多操作", english)) }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.width(190.dp).clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                ) {
                    fun run(action: () -> Unit) { menuOpen = false; action() }
                    DropdownMenuItem(text = { Text(appText("分享", english)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { run(onShare) })
                    DropdownMenuItem(text = { Text(appText("复制", english)) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { run(onCopy) })
                    onSlideshow?.let { action -> DropdownMenuItem(text = { Text(appText("幻灯片", english)) }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) }, onClick = { run(action) }) }
                    onOpenWith?.let { action -> DropdownMenuItem(text = { Text(appText("打开方式", english)) }, leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) }, onClick = { run(action) }) }
                    onInfo?.let { action -> DropdownMenuItem(text = { Text(appText("信息", english)) }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = { run(action) }) }
                    onEditTags?.let { action -> DropdownMenuItem(text = { Text(if (english) "View/Edit Tags" else "查看/编辑 Tags") }, leadingIcon = { Icon(Icons.Outlined.Label, null) }, onClick = { run(action) }) }
                    onEdit?.let { action -> DropdownMenuItem(text = { Text(appText("编辑", english)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { run(action) }) }
                    onWallpaper?.let { action -> DropdownMenuItem(text = { Text(appText("设置为壁纸", english)) }, leadingIcon = { Icon(Icons.Outlined.Wallpaper, null) }, onClick = { run(action) }) }
                    onExclude?.let { action -> DropdownMenuItem(text = { Text(appText("排除", english)) }, leadingIcon = { Icon(Icons.Outlined.FolderOff, null) }, onClick = { run(action) }) }
                }
            }
        }
    }
}

@Composable
fun SelectionSubBar(
    selected: Int,
    total: Int,
    onSelectAll: () -> Unit,
    chromeAlpha: Float = 1f
) {
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(chromeAlpha),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("$selected/$total", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onSelectAll) {
                Box(
                    Modifier.size(18.dp)
                        .background(
                            if (selected == total && total > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface,
                            CircleShape
                        )
                        .border(
                            1.5.dp,
                            if (selected == total && total > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected == total && total > 0) {
                        Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(11.dp))
                    }
                }
            }
        }
    }
}

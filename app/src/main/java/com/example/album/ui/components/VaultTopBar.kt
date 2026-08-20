package com.example.album.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.example.album.ui.theme.VaultDimens
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText

@Composable
fun VaultTopBar(
    title: String,
    query: String,
    searchEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    favoriteActive: Boolean,
    onFavoriteClick: () -> Unit,
    menuItems: List<String>,
    onMenuItemClick: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    searchPlaceholder: String? = null,
    searchModeLabels: List<String> = emptyList(),
    selectedSearchMode: Int = 0,
    onSearchModeChange: (Int) -> Unit = {},
    onTitleClick: (() -> Unit)? = null,
    chromeAlpha: Float = 1f
) {
    val english = LocalAppEnglish.current
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.alpha(chromeAlpha), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(VaultDimens.HeaderContentHeight).padding(start = VaultDimens.HeaderHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                if (searchEnabled) 2.dp else VaultDimens.HeaderGap
            )
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = appText("返回相册", english))
                }
            } else {
                Row(
                    modifier = Modifier.widthIn(min = 48.dp).then(
                        if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier
                    ).height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(title, fontSize = 16.sp, maxLines = 1)
                    if (onTitleClick != null) {
                        Icon(
                            Icons.Outlined.SwapHoriz,
                            contentDescription = appText("切换图片和视频", english),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(17.dp)
                        )
                    }
                }
            }
            if (searchEnabled) {
                Row(
                    modifier = Modifier.weight(1f).height(48.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .padding(
                            start = if (searchModeLabels.isNotEmpty()) 7.dp else 13.dp,
                            end = 13.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.height(19.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = VaultDimens.SearchText),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text(
                                    searchPlaceholder ?: appText(if (title == "视频" || title == "Videos") "搜索文件夹、视频名称" else "搜索文件夹、图片名称", english),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = VaultDimens.SearchText,
                                    maxLines = 1
                                )
                                inner()
                            }
                        }
                    )
                }
                if (searchModeLabels.isNotEmpty()) {
                    val modeIndex = selectedSearchMode.coerceIn(searchModeLabels.indices)
                    Row(
                        modifier = Modifier.height(48.dp).widthIn(min = 44.dp)
                            .clickable {
                                onSearchModeChange((modeIndex + 1) % searchModeLabels.size)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            searchModeLabels[modeIndex],
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        Icon(
                            Icons.Outlined.SwapHoriz,
                            contentDescription = appText("切换搜索方式", english),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(25.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.height(48.dp).width(if (searchModeLabels.isNotEmpty()) 44.dp else 48.dp)
                ) {
                    Icon(
                        if (favoriteActive) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = appText(if (favoriteActive) "显示全部" else "仅显示收藏", english),
                        tint = if (favoriteActive) androidx.compose.ui.graphics.Color(0xFFFFD60A) else MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.height(48.dp).width(if (searchModeLabels.isNotEmpty()) 44.dp else 48.dp)
                ) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = appText("更多操作", english))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.width(190.dp).clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                ) {
                    menuItems.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item, fontSize = 14.sp) },
                            modifier = Modifier.height(52.dp),
                            onClick = {
                                menuExpanded = false
                                onMenuItemClick(item)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(1.dp))
        }
    }
}

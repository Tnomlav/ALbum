package com.example.album.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.album.ui.theme.VaultDimens
import com.example.album.ui.appText
import com.example.album.ui.theme.ThemeAccent
import com.example.album.ui.components.VaultInfoSheet
import com.example.album.ui.components.VaultWheelChoiceSheet
import com.example.album.ui.components.VaultColorSheet
import com.example.album.ui.components.VaultConfirmationSheet
import com.example.album.ui.components.VaultLicensesSheet
import com.example.album.ui.components.ListScrollHandle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.zIndex
import com.example.album.data.ThumbnailRepository
import com.example.album.BuildConfig
import com.example.album.data.AppRelease
import com.example.album.data.AppUpdateChecker
import com.example.album.data.UpdateStatus
import com.example.album.data.UserDataBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    language: String,
    settingsSection: String?,
    onSettingsSectionChange: (String?) -> Unit,
    onOpenCleanup: () -> Unit,
    onThemeModeChange: (String) -> Unit,
    onThemeColorChange: (String) -> Unit,
    onNavReorderChange: (Boolean) -> Unit,
    onToolsReorderChange: (Boolean) -> Unit = {},
    onPixivTabEnabledChange: (Boolean) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onBackgroundOptimizationChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    showFavoriteBadge: Boolean,
    onShowFavoriteBadgeChange: (Boolean) -> Unit,
    onShowHiddenMediaChange: (Boolean) -> Unit,
    onRenameExtensionChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("album_settings", Context.MODE_PRIVATE) }
    var choice by remember { mutableStateOf<ChoiceSetting?>(null) }
    var dialog by remember { mutableStateOf<InfoDialog?>(null) }
    var showThemeColors by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val packageInfo = remember {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val appVersion = packageInfo.versionName ?: "1.0"
    @Suppress("DEPRECATION")
    val appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateChecked by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppRelease?>(null) }

    var themeMode by remember { mutableStateOf(preferences.getString("theme_mode", "自动") ?: "自动") }
    var themeAccent by remember {
        mutableStateOf(ThemeAccent.fromStored(preferences.getString("theme_color", null)))
    }
    var navReorder by remember { mutableStateOf(preferences.getBoolean("nav_reorder", false)) }
    var toolsReorder by remember { mutableStateOf(preferences.getBoolean("tools_reorder", false)) }
    var pixivTabEnabled by remember { mutableStateOf(preferences.getBoolean("pixiv_tab_enabled", false)) }
    var recycleBin by remember { mutableStateOf(preferences.getBoolean("recycle_bin", true)) }
    var mediaManagement by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context))
    }
    var allFilesAccess by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
    }
    var deleteConfirmation by remember { mutableStateOf(preferences.getBoolean("delete_confirmation", true)) }
    var preserveDate by remember { mutableStateOf(preferences.getBoolean("preserve_date", true)) }
    var showRenameExtension by remember { mutableStateOf(preferences.getBoolean("rename_show_extension", false)) }
    var autoplay by remember { mutableStateOf(preferences.getBoolean("video_autoplay", true)) }
    var pauseVideoOnBackground by remember { mutableStateOf(preferences.getBoolean("video_pause_on_background", true)) }
    var rememberProgress by remember { mutableStateOf(preferences.getBoolean("video_progress", true)) }
    var autoHidePlayer by remember { mutableStateOf(preferences.getBoolean("video_auto_hide", true)) }
    var longSkip by remember { mutableStateOf(preferences.getBoolean("long_skip", false)) }
    var centerPopup by remember { mutableStateOf(preferences.getBoolean("video_center_popup", true)) }
    var tapPause by remember { mutableStateOf(preferences.getBoolean("video_tap_pause", false)) }
    var portraitTapPause by remember { mutableStateOf(preferences.getBoolean("video_portrait_tap_pause", false)) }
    var autoMini by remember { mutableStateOf(preferences.getBoolean("video_auto_mini", false)) }
    var edgeProtection by remember { mutableStateOf(preferences.getBoolean("edge_protection", true)) }
    var gifThumbnails by remember { mutableStateOf(preferences.getBoolean("gif_thumbnails", true)) }
    var previewOriginal by remember { mutableStateOf(preferences.getBoolean("preview_original", true)) }
    var showHiddenMedia by remember { mutableStateOf(preferences.getBoolean("show_hidden_media", false)) }
    var pullRefresh by remember { mutableStateOf(preferences.getBoolean("pull_refresh", true)) }
    var persistentScrollbar by remember { mutableStateOf(preferences.getBoolean("persistent_scrollbar", false)) }
    var randomSlideshow by remember { mutableStateOf(preferences.getBoolean("random_slideshow", false)) }
    var backgroundOptimization by remember { mutableStateOf(preferences.getBoolean("background_optimization", true)) }
    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { ThumbnailRepository.cacheBytes(context) }
    }
    // Settings are grouped into sub-pages: the main page keeps the theme and
    // one row per group, and tapping a row opens that group on its own page.
    // The selection is owned by the caller so the app-level back handler can
    // close the sub-page as well; this local handler stays as a fallback for
    // hosts that compose this screen on its own.
    BackHandler(enabled = settingsSection != null) { onSettingsSectionChange(null) }

    val choiceValues = remember { mutableStateMapOf<String, String>() }
    val isEnglish = language == "English"
    val mediaManagementLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mediaManagement = MediaStore.canManageMedia(context)
            preferences.edit().putBoolean("media_management", mediaManagement).apply()
        }
    }

    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            allFilesAccess = Environment.isExternalStorageManager()
            preferences.edit().putBoolean("all_files_access", allFilesAccess).apply()
        }
    }

    var pendingImport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val payload = UserDataBackup.export(context)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open the destination file")
                }
            }
            Toast.makeText(
                context,
                result.fold(
                    onSuccess = { if (isEnglish) "Backup saved" else "备份已保存" },
                    onFailure = { error ->
                        if (isEnglish) "Export failed: ${error.message ?: "unknown error"}"
                        else "导出失败：${error.message ?: "未知错误"}"
                    }
                ),
                if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    }
                }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                Toast.makeText(
                    context,
                    if (isEnglish) "Unable to read the backup file" else "无法读取备份文件",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                pendingImport = text
            }
        }
    }

    fun openMediaManagementSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(context, if (isEnglish) "Media management permission requires Android 12 or later" else "媒体管理权限需要 Android 12 或更高版本", Toast.LENGTH_SHORT).show()
            return
        }
        val packageUri = Uri.parse("package:${context.packageName}")
        val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, packageUri)
        runCatching { mediaManagementLauncher.launch(intent) }.onFailure {
            mediaManagementLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        }
    }

    fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(context, if (isEnglish) "All files access requires Android 11 or later" else "完全文件访问需要 Android 11 或更高版本", Toast.LENGTH_SHORT).show()
            return
        }
        val packageUri = Uri.parse("package:${context.packageName}")
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
        runCatching { allFilesLauncher.launch(intent) }.onFailure {
            allFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    fun setBoolean(key: String, value: Boolean, apply: (Boolean) -> Unit) {
        val editor = preferences.edit().putBoolean(key, value)
        if (key == "gif_thumbnails" || key == "background_optimization") {
            editor.putLong(
                "thumbnail_cache_generation",
                preferences.getLong("thumbnail_cache_generation", 0L) + 1L
            )
        }
        editor.apply()
        apply(value)
    }
    fun value(key: String, default: String) = choiceValues[key]
        ?: preferences.getString(key, default)
        ?: default
    fun choose(title: String, key: String, options: List<String>, current: String, onSelected: (String) -> Unit = {}) {
        choice = ChoiceSetting(title, options, current) { selected ->
            choiceValues[key] = selected
            preferences.edit().putString(key, selected).apply()
            onSelected(selected)
        }
    }

    CompositionLocalProvider(LocalSettingsEnglish provides isEnglish) {
    val listState = rememberLazyListState()
    val listMetrics by remember(listState) { derivedStateOf {
        val total = listState.layoutInfo.totalItemsCount
        val visible = listState.layoutInfo.visibleItemsInfo.size
        Triple(
            if (total <= visible) 0f else listState.firstVisibleItemIndex.toFloat() / (total - visible),
            if (total == 0) 1f else visible.toFloat() / total,
            total
        )
    } }
    Box(Modifier.fillMaxWidth().statusBarsPadding()) {
    LazyColumn(modifier = Modifier.fillMaxWidth(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        if (settingsSection != null) {
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onSettingsSectionChange(null) }
                        .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = settingsText("返回", isEnglish),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        settingsText(settingsSection!!, isEnglish),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }
        if (settingsSection == null) {
        // One row per category. Tapping a row opens that category's own page;
        // the arrow always points into the category.
        item { SettingsHeader("主题", expanded = false) { onSettingsSectionChange("主题") } }
        item { SettingsHeader("文件操作", expanded = false) { onSettingsSectionChange("文件操作") } }
        item { SettingsHeader("显示", expanded = false) { onSettingsSectionChange("显示") } }
        item { SettingsHeader("视频", expanded = false) { onSettingsSectionChange("视频") } }
        item { SettingsHeader("滚动条", expanded = false) { onSettingsSectionChange("滚动条") } }
        item { SettingsHeader("幻灯片", expanded = false) { onSettingsSectionChange("幻灯片") } }
        item { SettingsHeader("缓存", expanded = false) { onSettingsSectionChange("缓存") } }
        item { SettingsHeader("关于", expanded = false) { onSettingsSectionChange("关于") } }
        }
        if (settingsSection == "主题") {
        item { ValueRow("主题模式", if (isEnglish && themeMode == "自动") "System" else themeMode) { choose("主题模式", "theme_mode", listOf("自动", "浅色", "深色"), themeMode) { themeMode = it; onThemeModeChange(it) } } }
        item { ThemeColorRow(themeAccent.label) { showThemeColors = true } }
        item { ValueRow("语言", language) {
            choose("语言", "language", listOf("简体中文", "English"), language, onLanguageChange)
        } }
        item { ValueRow("主页", value("default_home", "相册")) {
            choose(
                "主页",
                "default_home",
                // Videos are part of Albums now, so the list only offers pages
                // that exist as tabs.
                buildList {
                    add("相册")
                    add("时间轴")
                    if (pixivTabEnabled) add("Pixiv")
                    add("工具箱")
                    add("设置")
                },
                value("default_home", "相册")
            )
        } }
        item { ToggleRow("下拉刷新", "在支持扫描的页面顶部下拉触发扫描", pullRefresh) { setBoolean("pull_refresh", it) { pullRefresh = it } } }
        item { ToggleRow("长按移动底栏图标", "长按拖动底栏图标排序", navReorder) { setBoolean("nav_reorder", it) { navReorder = it; onNavReorderChange(it) } } }
        item { ToggleRow("长按移动工具箱组件", "开启后可长按并拖动工具箱内的条目调整顺序", toolsReorder) { setBoolean("tools_reorder", it) { toolsReorder = it; onToolsReorderChange(it) } } }
        item {
            ToggleRow("显示 Pixiv 底栏页面", "在底栏显示 Pixiv 页面", pixivTabEnabled) { enabled ->
                setBoolean("pixiv_tab_enabled", enabled) {
                    pixivTabEnabled = it
                    if (!it && value("default_home", "相册") == "Pixiv") {
                        choiceValues["default_home"] = "相册"
                        preferences.edit().putString("default_home", "相册").apply()
                    }
                    onPixivTabEnabledChange(it)
                }
            }
        }
        }
        if (settingsSection == "文件操作") {
        // Permissions are what the category is about, so they lead the page.
        item {
            ToggleRow(
                "所有文件访问权限",
                if (allFilesAccess) "已开启，归档可直接写入目录" else "未开启，点击前往系统设置授权",
                allFilesAccess
            ) { openAllFilesSettings() }
        }
        item {
            ToggleRow(
                "媒体管理权限",
                if (mediaManagement) "已开启，删除时使用应用内确认弹窗" else "未开启，点击前往系统设置授权",
                mediaManagement
            ) { openMediaManagementSettings() }
        }
        item { ToggleRow("回收站", "开启后，删除的文件将进入回收站", recycleBin) { setBoolean("recycle_bin", it) { recycleBin = it } } }
        item { ValueRow("回收站文件保留期限", value("retention", "60天"), "保存在应用私有目录，卸载时会一并删除") {
            choose("回收站文件保留期限", "retention", listOf("10天", "30天", "60天", "90天"), value("retention", "60天")) { selected ->
                onRetentionChange(selected.filter(Char::isDigit).toIntOrNull() ?: 60)
            }
        } }
        item {
            ToggleRow(
                "删除前确认",
                "普通删除是否二次确认",
                deleteConfirmation
            ) { setBoolean("delete_confirmation", it) { deleteConfirmation = it } }
        }
        item { ToggleRow("复制/移动/编辑时保留原日期", null, preserveDate) { setBoolean("preserve_date", it) { preserveDate = it } } }
        item { ToggleRow("重命名时显示后缀", "关闭后只编辑文件名，原后缀会自动保留", showRenameExtension) { setBoolean("rename_show_extension", it) { showRenameExtension = it; onRenameExtensionChange(it) } } }
        item { ValueRow("编辑后保存方式", value("edit_save", "每次询问"), "保留编辑副本或替换当前版本，保存前均需确认") { choose("编辑后保存方式", "edit_save", listOf("每次询问", "保留二者", "替换原图"), value("edit_save", "每次询问")) } }
        item { ValueRow("复制/移动文件已存在", value("conflict", "保留两者")) { choose("同名文件处理", "conflict", listOf("保留两者", "覆盖", "跳过"), value("conflict", "保留两者")) } }
        // Backups close the page, so they sit at its end.
        item {
            ClickableRow("导出应用数据", "收藏、队列与偏好设置，不含媒体和账号") {
                exportLauncher.launch(UserDataBackup.defaultFileName())
            }
        }
        item {
            ClickableRow("导入应用数据", "会覆盖当前数据") {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
        }
        if (settingsSection == "显示") {
        item { ToggleRow("播放 GIF 缩略图", "仅控制缩略图，预览和全屏始终播放", gifThumbnails) { setBoolean("gif_thumbnails", it) { gifThumbnails = it } } }
        item { ToggleRow("预览页显示原图", "先显示缩略图再加载原图", previewOriginal) { setBoolean("preview_original", it) { previewOriginal = it } } }
        item { ToggleRow("显示收藏星标", "在缩略图右上角显示收藏星标", showFavoriteBadge) { setBoolean("show_favorite_badge", it) { onShowFavoriteBadgeChange(it) } } }
        item { ToggleRow("显示点号开头的图片", "显示文件名以 . 开头的图片文件", showHiddenMedia) {
            setBoolean("show_hidden_media", it) {
                showHiddenMedia = it
                onShowHiddenMediaChange(it)
            }
        } }
        }
        if (settingsSection == "视频") {
        item { ToggleRow("打开视频时自动播放", null, autoplay) { setBoolean("video_autoplay", it) { autoplay = it } } }
        item { ToggleRow("进入后台时自动暂停", null, pauseVideoOnBackground) { setBoolean("video_pause_on_background", it) { pauseVideoOnBackground = it } } }
        item { ToggleRow("记住最后一次播放进度", null, rememberProgress) { setBoolean("video_progress", it) { rememberProgress = it } } }
        item { ToggleRow("自动隐藏播放器界面", "播放中无操作 3 秒后隐藏控件", autoHidePlayer) { setBoolean("video_auto_hide", it) { autoHidePlayer = it } } }
        item { ToggleRow("视频弹窗", "暂停、快进快退、调节倍速时显示中间的黑色提示", centerPopup) { setBoolean("video_center_popup", it) { centerPopup = it } } }
        item { ToggleRow("长快进", "在播放器中显示长快退和长快进按钮", longSkip) { setBoolean("long_skip", it) { longSkip = it } } }
        if (longSkip) {
            item { ValueRow("长快进长度", value("long_skip_length", "30秒")) {
                choose("长快进长度", "long_skip_length", listOf("15秒", "30秒", "45秒", "60秒", "90秒", "120秒"), value("long_skip_length", "30秒"))
            } }
        }
        item { ValueRow("快进长度", value("normal_skip", "10秒")) { choose("快进长度", "normal_skip", listOf("3秒", "5秒", "10秒", "15秒", "30秒"), value("normal_skip", "10秒")) } }
        item { ValueRow("满屏滑动跳过时间", value("gesture_seek", "90秒"), "横向滑满整个屏幕对应的进度") { choose("满屏滑动跳过时间", "gesture_seek", listOf("30秒", "60秒", "90秒", "120秒", "150秒"), value("gesture_seek", "90秒")) } }
        item { ToggleRow("边缘误触保护", "在屏幕边缘松手时取消当次跳转", edgeProtection) { setBoolean("edge_protection", it) { edgeProtection = it } } }
        item { ToggleRow("单击暂停", null, tapPause) {
            setBoolean("video_tap_pause", it) {
                tapPause = it
                if (!it) {
                    portraitTapPause = false
                    preferences.edit().putBoolean("video_portrait_tap_pause", false).apply()
                }
            }
        } }
        item { ToggleRow("只在竖屏下单击暂停", null, portraitTapPause) {
            setBoolean("video_portrait_tap_pause", it) {
                portraitTapPause = it
                if (it) {
                    tapPause = true
                    preferences.edit().putBoolean("video_tap_pause", true).apply()
                }
            }
        } }
        item { ToggleRow("自动小窗", "播放中切到后台时自动进入小窗并保持播放", autoMini) {
            setBoolean("video_auto_mini", it) { autoMini = it }
        } }
        item { ValueRow("亮度：空白：音量 触控占比", value("video_brightness_volume_ratio", "1:1")) {
            choose("亮度：空白：音量 触控占比", "video_brightness_volume_ratio", listOf("1:1", "1:1:1", "1:2:1"), value("video_brightness_volume_ratio", "1:1"))
        } }
        item { ValueRow("快退：暂停：快进 触控占比", value("video_seek_pause_ratio", "1:1:1")) {
            choose("快退：暂停：快进 触控占比", "video_seek_pause_ratio", listOf("1:1:1", "1:2:1", "1:0:1"), value("video_seek_pause_ratio", "1:1:1"))
        } }
        }
        if (settingsSection == "滚动条") {
        item { ValueRow("拖动宽度", value("scroll_width", "24px"), "调整右侧滚动条的触控区域") { choose("拖动宽度", "scroll_width", listOf("16px", "24px", "32px"), value("scroll_width", "24px")) } }
        item { ValueRow("浮现时间", value("scroll_duration", "1秒"), "停止滚动后继续显示的时间") { choose("浮现时间", "scroll_duration", listOf("0.5秒", "1秒", "2秒", "3秒"), value("scroll_duration", "1秒")) } }
        item { ToggleRow("始终显示", "页面可滚动时保持滚动条常驻", persistentScrollbar) { setBoolean("persistent_scrollbar", it) { persistentScrollbar = it } } }
        }
        if (settingsSection == "幻灯片") {
        item { ValueRow("幻灯片播放间隔", value("slideshow_interval", "3秒")) { choose("幻灯片播放间隔", "slideshow_interval", (1..10).map { "${it}秒" }, value("slideshow_interval", "3秒")) } }
        item { ValueRow("幻灯片播放动画", value("slideshow_animation", "自然")) { choose("幻灯片播放动画", "slideshow_animation", listOf("自然", "淡入淡出", "滑动"), value("slideshow_animation", "自然")) } }
        item { ToggleRow("幻灯片随机播放", null, randomSlideshow) { setBoolean("random_slideshow", it) { randomSlideshow = it } } }
        }
        if (settingsSection == "缓存") {
        item { ToggleRow("后台优化", "在后台增量生成分级缩略图", backgroundOptimization) {
            setBoolean("background_optimization", it) {
                backgroundOptimization = it
                onBackgroundOptimizationChange(it)
            }
        } }
        item { ValueRow("缓存上限", value("cache_limit", "自动")) {
            choose("缓存上限", "cache_limit", listOf("自动", "250 MB", "500 MB", "1 GB"), value("cache_limit", "自动")) {
                scope.launch {
                    withContext(Dispatchers.IO) { ThumbnailRepository.applyCacheLimit(context, preferences) }
                    cacheBytes = withContext(Dispatchers.IO) { ThumbnailRepository.cacheBytes(context) }
                }
            }
        } }
        item { ValueRow("清理缓存", formatCacheSize(cacheBytes)) {
            scope.launch {
                val cleared = withContext(Dispatchers.IO) { ThumbnailRepository.clear(context, preferences) }
                cacheBytes = 0L
                Toast.makeText(context, if (isEnglish) "Cleared ${formatCacheSize(cleared)} of cache" else "已清理 ${formatCacheSize(cleared)} 缓存", Toast.LENGTH_SHORT).show()
            }
        } }
        }
        if (settingsSection == "关于") {
        item { ValueRow("隐私政策", "›") {
            dialog = InfoDialog(
                "隐私政策",
                if (isEnglish) {
                    "Album is designed to keep your personal media on your device. Photos and videos are scanned, sorted, edited, played, favorited, and cached locally. The app does not upload your local media, use it for advertising, or send it to an analytics service.\n\n" +
                        "The app may request Android media, file-management, notification, and media-playback permissions. These permissions are used only for the related features you enable. You can revoke them in Android settings; some features may then be unavailable.\n\n" +
                        "Pixiv features open Pixiv services in an in-app WebView and may request Pixiv pages, artwork metadata, and media URLs. Pixiv login cookies are stored by Android WebView on this device until you sign out or clear the app data. Pixiv handles information submitted to its services under its own privacy policy.\n\n" +
                        "When you check for updates, Album downloads the release manifest published with this project's GitHub Releases and compares it with the installed version. When you choose an update, Android opens the APK download link from that release. The app does not silently install updates and does not verify the APK for you.\n\n" +
                        "Album has no cloud backup: Android auto backup and device-to-device transfer are switched off for this app, so favorites, wallpaper and slideshow queues, preferences, and excluded folders stay on this device only and do not survive a reinstall.\n\n" +
                        "Files you delete are copied into the app's private Trash folder before the original is removed, which means they occupy app storage until the retention period ends. Clearing the app's data or uninstalling Album deletes everything still in Trash.\n\n" +
                        "Media is sent to another app only when you choose a system action such as sharing or opening a file. Settings, thumbnails, playback progress, and cached data remain on the device and can be cleared from the app or Android settings.\n\n" +
                        "Version: v$appVersion\nLast revised: September 17, 2026"
                } else {
                    "Album 以本地处理为设计原则。图片和视频的扫描、排序、编辑、播放、收藏及缩略图缓存均在本机完成。应用不会上传你的本地媒体，不会将其用于广告，也不会接入分析统计服务。\n\n" +
                        "应用可能申请媒体访问、文件管理、通知和媒体播放等 Android 权限，仅用于你启用的对应功能。你可以在 Android 系统设置中撤销权限；撤销后，相关功能可能无法使用。\n\n" +
                        "Pixiv 功能会通过应用内 WebView 访问 Pixiv 页面，并可能请求作品信息、媒体库信息和媒体地址。Pixiv 登录 Cookie 由 Android WebView 保存在本机，直到你退出登录或清除应用数据。提交给 Pixiv 服务的信息受 Pixiv 自身隐私政策约束。\n\n" +
                        "检查更新时，Album 会下载与本项目 GitHub Release 一起发布的版本清单，并与已安装版本比较。你选择更新后，应用会让 Android 打开该 Release 中的 APK 下载地址；应用不会静默安装更新，也不会替你校验 APK。\n\n" +
                        "Album 不使用云备份：本应用的 Android 自动备份和设备间迁移均已关闭，因此收藏、壁纸与幻灯片队列、偏好设置和排除文件夹只保存在本机，重装后不会恢复。\n\n" +
                        "删除的文件会先复制到应用私有目录的回收站，再移除原文件，因此它们在保留期内会占用应用存储空间。清除应用数据或卸载 Album 会同时删除回收站中仍在的文件。\n\n" +
                        "只有在你主动使用系统分享或打开文件等操作时，媒体才会交给其他应用。设置、缩略图、播放进度和缓存均保存在本机，可在应用内或 Android 系统设置中清除。\n\n" +
                        "版本：v$appVersion\n最后修订：2026年9月17日"
                }
            )
        } }
        item { ValueRow("开源许可", "›") { showLicenses = true } }
        item {
            ValueRow(
                "应用版本",
                when {
                    checkingUpdate -> "检查中..."
                    availableUpdate != null -> if (isEnglish) "v${availableUpdate?.versionName} available" else "发现 v${availableUpdate?.versionName}"
                    updateChecked -> "已是最新版本"
                    else -> "›"
                },
                "v$appVersion",
                if (checkingUpdate) null else {
                    {
                        if (BuildConfig.UPDATE_URL.isBlank()) {
                            dialog = InfoDialog(
                                "检查更新",
                                if (isEnglish) {
                                    "No update source is configured. Set ALBUM_UPDATE_URL in gradle.properties to an HTTPS JSON endpoint, then rebuild the app."
                                } else {
                                    "尚未配置更新源。请在 gradle.properties 中设置 ALBUM_UPDATE_URL 为提供版本 JSON 的 HTTPS 地址，然后重新构建应用。"
                                }
                            )
                            return@ValueRow
                        }
                        checkingUpdate = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { AppUpdateChecker.fetch(BuildConfig.UPDATE_URL) }
                            }.onSuccess { release ->
                                when (AppUpdateChecker.status(release, appVersion, appVersionCode)) {
                                    UpdateStatus.Available -> {
                                        availableUpdate = release
                                        updateChecked = false
                                    }

                                    UpdateStatus.UpToDate -> {
                                        availableUpdate = null
                                        updateChecked = true
                                        Toast.makeText(context, if (isEnglish) "v$appVersion is up to date" else "当前已是最新版本 v$appVersion", Toast.LENGTH_SHORT).show()
                                    }

                                    UpdateStatus.SourceBehind -> {
                                        availableUpdate = null
                                        updateChecked = false
                                        dialog = InfoDialog(
                                            "检查更新",
                                            if (isEnglish) {
                                                "The update source still lists v${release.versionName}, which is older than the installed v$appVersion. This version has not been published to the update source yet, so no update can be offered."
                                            } else {
                                                "更新源仍停留在 v${release.versionName}，比已安装的 v$appVersion 更旧。这一版还没有发布到更新源，因此暂时无法提供更新。"
                                            }
                                        )
                                    }
                                }
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Update check failed: ${error.message ?: "unknown error"}" else "检查更新失败：${error.message ?: "未知错误"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            checkingUpdate = false
                        }
                    }
                }
            )
        }
        }
    }
    ListScrollHandle(
        progress = listMetrics.first,
        visibleFraction = listMetrics.second,
        scrolling = listState.isScrollInProgress,
        onFraction = { fraction -> scope.launch { listState.scrollToItem((fraction * (listMetrics.third - 1).coerceAtLeast(0)).toInt()) } },
        modifier = Modifier.align(Alignment.CenterEnd).zIndex(20f)
    )
    }

    choice?.let { setting ->
        val displayedOptions = setting.options.map { settingsDisplay(setting.title, it, isEnglish) }
        val displayedSelected = settingsDisplay(setting.title, setting.selected, isEnglish)
        VaultWheelChoiceSheet(
            title = settingsText(setting.title, isEnglish),
            options = displayedOptions,
            selected = displayedSelected,
            onDismiss = { choice = null },
            onApply = { option ->
                displayedOptions.indexOf(option).takeIf { it >= 0 }?.let { index ->
                    setting.onSelect(setting.options[index])
                }
                choice = null
            }
        )
    }
    if (showThemeColors) {
        VaultColorSheet(
            title = settingsText("主题颜色", isEnglish),
            options = ThemeAccent.entries,
            selected = themeAccent,
            onDismiss = { showThemeColors = false },
            onSelect = { selected ->
                themeAccent = selected
                preferences.edit().putString("theme_color", selected.storedValue).apply()
                onThemeColorChange(selected.storedValue)
                showThemeColors = false
            },
            optionLabel = { settingsText(it.label, isEnglish) }
        )
    }
    dialog?.let { info ->
        VaultInfoSheet(settingsText(info.title, isEnglish), info.body, if (isEnglish) "Done" else "知道了") { dialog = null }
    }
    if (showLicenses) {
        VaultLicensesSheet(isEnglish) { showLicenses = false }
    }
    pendingImport?.let { raw ->
        VaultConfirmationSheet(
            title = "导入应用数据",
            body = if (isEnglish) {
                "Importing replaces the current favorites, wallpaper and slideshow queues, excluded folders, and app preferences. Nothing is imported until you confirm, and no media files are touched."
            } else {
                "导入会覆盖当前的收藏、壁纸与幻灯片队列、排除文件夹和应用偏好设置。确认前不会写入任何内容，也不会改动任何媒体文件。"
            },
            confirmLabel = if (isEnglish) "Import" else "导入",
            onDismiss = { pendingImport = null },
            onConfirm = {
                val result = runCatching { UserDataBackup.import(context, raw) }
                pendingImport = null
                Toast.makeText(
                    context,
                    result.fold(
                        onSuccess = { summary ->
                            if (isEnglish) "Imported ${summary.appliedValues} values; reloading"
                            else "已导入 ${summary.appliedValues} 项设置，正在重新加载"
                        },
                        onFailure = { error ->
                            if (isEnglish) "Import failed: ${error.message ?: "not an Album backup"}"
                            else "导入失败：${error.message ?: "不是 Album 备份文件"}"
                        }
                    ),
                    if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                ).show()
                if (result.isSuccess) context.findActivity()?.recreate()
            }
        )
    }
    availableUpdate?.let { release ->
        val updateBody = buildString {
            append(if (isEnglish) "Version ${release.versionName}" else "版本 ${release.versionName}")
            if (release.notes.isNotBlank()) { append("\n\n"); append(release.notes) }
        }
        if (release.downloadUrl.isNotBlank()) {
            VaultConfirmationSheet(
                title = if (isEnglish) "Update available" else "发现新版本",
                body = updateBody,
                confirmLabel = if (isEnglish) "Download" else "下载",
                onDismiss = { availableUpdate = null },
                onConfirm = {
                    if (!AppUpdateChecker.isSecureHttpsUrl(release.downloadUrl)) {
                        Toast.makeText(context, if (isEnglish) "Download link is not secure" else "下载地址不安全", Toast.LENGTH_SHORT).show()
                        availableUpdate = null
                    } else {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))) }
                            .onFailure {
                                Toast.makeText(context, if (isEnglish) "Unable to open download link" else "无法打开下载地址", Toast.LENGTH_SHORT).show()
                            }
                        availableUpdate = null
                    }
                }
            )
        } else {
            VaultInfoSheet(if (isEnglish) "Update available" else "发现新版本", updateBody, if (isEnglish) "Done" else "知道了") {
                availableUpdate = null
            }
        }
    }
    }
}

private data class ChoiceSetting(val title: String, val options: List<String>, val selected: String, val onSelect: (String) -> Unit)
private data class InfoDialog(val title: String, val body: String)

/** Finds the Activity behind a ContextWrapper so an import can reload the UI. */
private fun Context.findActivity(): android.app.Activity? {
    var current: Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}

private val LocalSettingsEnglish = compositionLocalOf { false }

private fun settingsText(value: String, english: Boolean): String {
    if (!english) return value
    Regex("^(\\d+(?:\\.\\d+)?)秒$").matchEntire(value)?.let { return "${it.groupValues[1]} seconds" }
    Regex("^(\\d+)天$").matchEntire(value)?.let { return "${it.groupValues[1]} days" }
    Regex("^已添加 (\\d+) 个$").matchEntire(value)?.let { return "${it.groupValues[1]} added" }
    Regex("^(\\d+) 项$").matchEntire(value)?.let { return "${it.groupValues[1]} items" }
    // One dictionary for the whole app: see AppLanguage.kt.
    return appText(value, english = true)
}

internal fun settingsDisplay(title: String, value: String, english: Boolean): String = when {
    title == "语言" -> value
    english && title == "主题模式" && value == "自动" -> "System"
    else -> settingsText(value, english)
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> "${bytes / 1024L / 1024L} MB"
    else -> String.format(java.util.Locale.CHINA, "%.1f GB", bytes / 1024f / 1024f / 1024f)
}

@Composable
private fun SettingsHeader(title: String, expanded: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 15.dp, end = 15.dp, top = 17.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The category rows on the settings home are the page's navigation, so
        // they read a size larger than the rows inside a category.
        Text(
            settingsText(title, LocalSettingsEnglish.current),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall
        )
        if (onClick != null) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** A plain row that only opens something else. */
@Composable
private fun ClickableRow(label: String, note: String?, onClick: () -> Unit) {
    val english = LocalSettingsEnglish.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = VaultDimens.SettingsRowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(settingsText(label, english), style = MaterialTheme.typography.bodyMedium)
            note?.let {
                Text(
                    settingsText(it, english),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String, note: String? = null, onClick: (() -> Unit)?) {
    val english = LocalSettingsEnglish.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = VaultDimens.SettingsRowMinHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = "${settingsText(label, english)}: ${settingsText(value, english)}" }
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(settingsText(label, english), style = MaterialTheme.typography.bodyMedium)
            note?.let { Text(settingsText(it, english), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // The chosen option is the row's value, so it carries the theme colour.
        Text(settingsText(value, english), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ThemeColorRow(value: String, onClick: () -> Unit) {
    val english = LocalSettingsEnglish.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = VaultDimens.SettingsRowMinHeight).clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(settingsText("主题颜色", english), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(settingsText(value, english), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 10.dp))
        androidx.compose.foundation.layout.Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}

@Composable
private fun ToggleRow(label: String, note: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val english = LocalSettingsEnglish.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = VaultDimens.SettingsRowMinHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { stateDescription = if (checked) settingsText("已开启", english) else settingsText("已关闭", english) }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(settingsText(label, english), style = MaterialTheme.typography.bodyMedium)
            note?.let { Text(settingsText(it, english), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // The row owns the switch semantics and click target. Keeping the
        // visual switch non-interactive avoids exposing two switches to
        // accessibility services and prevents duplicate toggle events.
        VaultSwitch(checked)
    }
}

@Composable
private fun VaultSwitch(checked: Boolean) {
    val fraction by animateFloatAsState(if (checked) 1f else 0f, label = "switch")
    val primary = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outline
    Canvas(
        Modifier.size(48.dp)
    ) {
        val trackTop = 10.dp.toPx()
        val trackHeight = 28.dp.toPx()
        drawRoundRect(if (checked) primary else off, Offset(1.dp.toPx(), trackTop), Size(46.dp.toPx(), trackHeight), androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f))
        drawCircle(Color.White, 11.dp.toPx(), Offset(13.dp.toPx() + 22.dp.toPx() * fraction, 24.dp.toPx()))
    }
}

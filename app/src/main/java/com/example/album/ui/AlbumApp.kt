package com.example.album.ui

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import org.json.JSONArray
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.album.data.MediaItem
import com.example.album.data.transferFolderPath
import com.example.album.data.displayAddress
import com.example.album.data.PixivArchiveRepository
import com.example.album.data.WallpaperQueueState
import com.example.album.data.WallpaperQueueStore
import com.example.album.data.SlideshowQueueStore
import com.example.album.data.decodeFavoriteKeys
import com.example.album.data.encodeFavoriteKeys
import com.example.album.data.repairFavoriteUris
import com.example.album.data.toFavoriteCandidate
import com.example.album.data.WallpaperAppliedStore
import com.example.album.wallpaper.WallpaperBackup
import com.example.album.data.TransferMode
import com.example.album.data.TransferRequest
import com.example.album.playback.PlaybackResumeRequest
import com.example.album.ui.components.MediaViewer
import com.example.album.ui.components.LocalMediaAnimatedVisibilityScope
import com.example.album.ui.components.LocalMediaSharedTransitionScope
import com.example.album.ui.components.LocalActiveSharedMediaKey
import com.example.album.ui.components.VaultTopBar
import com.example.album.ui.components.SelectionTopBar
import com.example.album.ui.components.SelectionSubBar
import com.example.album.ui.components.VaultOptionSheet
import com.example.album.ui.components.VaultApplyChoiceSheet
import com.example.album.ui.components.VaultSortChoiceSheet
import com.example.album.ui.components.VaultSortWheelSheet
import com.example.album.ui.components.VaultLayoutWheelSheet
import com.example.album.ui.screens.SlideshowSettingsSheet
import com.example.album.ui.screens.GestureHintsSheet
import com.example.album.ui.components.VaultWheelChoiceSheet
import com.example.album.ui.components.VaultDateSheet
import com.example.album.ui.components.VaultTextInputDialog
import com.example.album.ui.components.VaultConfirmationSheet
import com.example.album.ui.components.VaultInfoSheet
import com.example.album.ui.editor.ImageEditorDialog
import com.example.album.ui.screens.AlbumsScreen
import com.example.album.ui.screens.SettingsScreen
import com.example.album.ui.screens.TimelineScreen
import com.example.album.ui.screens.CleanupScreen
import com.example.album.ui.screens.WallpaperManagerScreen
import com.example.album.ui.screens.SlideshowQueueScreen
import com.example.album.ui.screens.SlideshowQueueScreen
import com.example.album.ui.screens.WallpaperSort
import com.example.album.ui.screens.sortWallpaperMedia
import com.example.album.ui.screens.WallpaperSettingsSheet
import com.example.album.ui.screens.WallpaperCropScreen
import com.example.album.ui.screens.PixivArchiveScreen
import com.example.album.ui.screens.PixivArchiveSession
import com.example.album.ui.screens.ArchiveActivity
import com.example.album.ui.screens.ArchiveUiState
import com.example.album.ui.screens.PixivArchiveScanService
import com.example.album.data.PixivArchivePhase
import com.example.album.ui.screens.DestinationScreen
import com.example.album.ui.theme.VaultDimens
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.yield
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

private enum class MainTab(val label: String, val icon: ImageVector) {
    Albums("相册", Icons.Outlined.Collections),
    Videos("视频", Icons.Outlined.VideoLibrary),
    Timeline("时间轴", Icons.Outlined.WatchLater),
    Pixiv("Pixiv", PixivPMark),
    Tools("工具箱", Icons.Outlined.Widgets),
    Settings("设置", Icons.Outlined.Settings)
}

private val PixivPMark = ImageVector.Builder("pixiv-p", 24.dp, 24.dp, 120f, 120f).apply {
    addPath(
        pathData = PathParser().parsePathString(
            "M32 28C36 28 39 27 41 29C43 31 44 34 45 37C50 30 57 27 66 27C82 27 93 40 93 58C93 76 82 89 66 89C58 89 51 85 46 79V92C46 95 44 97 41 97H32ZM62 42C52 42 46 49 46 59C46 69 52 76 62 76C72 76 79 69 79 59C79 49 72 42 62 42Z"
        ).toNodes(),
        pathFillType = PathFillType.EvenOdd,
        fill = SolidColor(Color.Black)
    )
}.build()

private enum class PixivSearchMode { Artist, Tag }

@Composable
internal fun PixivArchiveNavigation(onClick: () -> Unit) {
    val english = LocalAppEnglish.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 6.dp).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primary.copy(alpha = .1f),
        shape = RoundedCornerShape(7.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.width(30.dp).height(30.dp).background(Color.White, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    PixivPMark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.35f; scaleY = 1.35f }
                )
            }
            Text(
                if (english) "Pixiv archive" else "Pixiv 文件归档",
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun enabledMainTabs(pixivEnabled: Boolean): List<MainTab> =
    MainTab.entries.filter { tab ->
        (tab != MainTab.Pixiv || pixivEnabled) && tab != MainTab.Videos
    }

private fun normalizedTabOrder(stored: List<MainTab>, pixivEnabled: Boolean): List<MainTab> {
    val enabled = enabledMainTabs(pixivEnabled)
    val result = stored.filterTo(mutableListOf()) { it in enabled }
    enabled.forEachIndexed { defaultIndex, tab ->
        if (tab !in result) {
            val nextExisting = enabled.drop(defaultIndex + 1).firstOrNull { it in result }
            val insertion = nextExisting?.let(result::indexOf)?.takeIf { it >= 0 } ?: result.size
            result.add(insertion, tab)
        }
    }
    return result
}

internal data class PixivTagSearchEntry(val item: MediaItem, val tags: List<String>)

/** Toolbox tab: shortcuts to the standalone tools that used to be buried in menus. */
@Composable
private fun ToolsScreen(
    english: Boolean,
    onOpenArchive: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenCleanup: () -> Unit,
    onStartSlideshow: () -> Unit,
    onOpenPixiv: () -> Unit,
    reorderEnabled: Boolean = false,
    order: List<String> = listOf("wallpaper", "cleanup", "slideshow", "pixiv"),
    onOrderChange: (List<String>) -> Unit = {}
) {
    // Entries are looked up per id: a stored order can still contain the removed
    // "archive" id, and indexing the filtered list would have shown the wrong
    // label for every entry after it.
    val entryById = remember(order, english) {
        order.mapNotNull { id ->
            val entry = when (id) {
                "wallpaper" -> Triple(Icons.Outlined.Wallpaper, appText("壁纸队列", english), appText("管理静态与动态壁纸队列并应用", english))
                "cleanup" -> Triple(Icons.Outlined.CleaningServices, appText("文件清理", english), appText("重复图片、回收站与排除文件夹", english))
                "slideshow" -> Triple(Icons.Outlined.Slideshow, appText("幻灯片播放", english), appText("管理幻灯片队列并开始播放", english))
                // The Pixiv page keeps the archive entry's icon and wording:
                // the archive itself is opened from inside that page.
                "pixiv" -> Triple(Icons.Outlined.Archive, appText("Pixiv 文件归档", english), appText("扫描并整理 Pixiv 图片到画师目录", english))
                else -> null
            }
            entry?.let { id to it }
        }.toMap()
    }
    val actions = mapOf(
        "wallpaper" to onOpenWallpaper,
        "cleanup" to onOpenCleanup,
        "slideshow" to onStartSlideshow,
        "pixiv" to onOpenPixiv
    )
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            // This page has no top bar any more, so it owns the status bar inset.
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        order.forEachIndexed { index, id ->
            val entry = entryById[id] ?: return@forEachIndexed
            // The drag state has to follow the entry, not the slot: without a
            // key a swap moved the rows but left the offset behind, which is
            // why the drag looked like it was not following the finger.
            key(id) {
                ReorderableToolEntry(
                    icon = entry.first,
                    title = entry.second,
                    subtitle = entry.third,
                    reorderEnabled = reorderEnabled,
                    onMoveUp = {
                        if (index > 0 && index - 1 < order.size) {
                            val updated = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                            onOrderChange(updated)
                        }
                    },
                    onMoveDown = {
                        if (index < order.lastIndex) {
                            val updated = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                            onOrderChange(updated)
                        }
                    },
                    onClick = actions[id] ?: {}
                )
            }
        }
    }
}

@Composable
private fun ReorderableToolEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    reorderEnabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit
) {
    var dragOffset by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var rowStep by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val entrySpacing = with(density) { 8.dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                // One row plus the Column's spacing: the distance the entry
                // really travels when it swaps with its neighbour.
                if (size.height > 0) rowStep = size.height + entrySpacing
            }
            .graphicsLayer { translationY = dragOffset; if (dragging) scaleX = 1.02f; scaleY = 1.02f }
            .pointerInput(reorderEnabled, rowStep) {
                if (!reorderEnabled) return@pointerInput
                val step = rowStep.takeIf { it > 0f } ?: with(density) { 72.dp.toPx() }
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; dragOffset = 0f },
                    onDragCancel = { dragging = false; dragOffset = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                        // Keep the row under the finger: when it swaps with a
                        // neighbour the offset is reduced by exactly the height
                        // it moved, so the drag stays continuous and never
                        // drifts away from the finger.
                        while (dragOffset > step) { dragOffset -= step; onMoveDown() }
                        while (dragOffset < -step) { dragOffset += step; onMoveUp() }
                    }
                )
            }
    ) {
        ToolEntry(icon = icon, title = title, subtitle = subtitle, onClick = onClick)
    }
}

@Composable
private fun ToolEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp)) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AlbumApp(
    onThemeModeChange: (String) -> Unit,
    onThemeColorChange: (String) -> Unit,
    appLanguage: String,
    onAppLanguageChange: (String) -> Unit,
    externalMediaUri: Uri? = null,
    externalWallpaperUri: Uri? = null,
    externalSharedUris: List<Uri> = emptyList(),
    onExternalShareConsumed: () -> Unit = {},
    playbackResumeRequest: PlaybackResumeRequest? = null,
    onPlaybackResumeConsumed: (Long) -> Unit = {},
    pictureInPictureMode: Boolean = false,
    onEnterPictureInPicture: () -> Boolean = { false },
    onAutoEnterPictureInPictureChange: (Boolean, Int, Int) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val scope = rememberCoroutineScope()
    val library = remember { MediaLibraryState(context) }
    DisposableEffect(library) {
        onDispose { library.close() }
    }
    val lifecycleOwner = context as? LifecycleOwner
    val pixivRepository = remember { PixivArchiveRepository(context) }
    pixivRepository.english = english
    val pixivArchiveSession = remember { PixivArchiveSession(context) }
    // The archive session builds user-visible status messages without a
    // Composable context, so it needs to know the current language.
    pixivArchiveSession.english = english
    val preferences = remember { context.getSharedPreferences("album_preferences", android.content.Context.MODE_PRIVATE) }
    val albumSettings = remember { context.getSharedPreferences("album_settings", android.content.Context.MODE_PRIVATE) }
    var showRenameExtension by remember { mutableStateOf(albumSettings.getBoolean("rename_show_extension", false)) }
    val transferPreferences = remember { context.getSharedPreferences("transfer_preferences", android.content.Context.MODE_PRIVATE) }
    val pixivEnabledAtStart = albumSettings.getBoolean("pixiv_tab_enabled", false)
    val initialTab = when (albumSettings.getString("default_home", "相册")) {
        "视频" -> MainTab.Albums
        "时间轴" -> MainTab.Timeline
        "Pixiv" -> if (pixivEnabledAtStart) MainTab.Pixiv else MainTab.Albums
        else -> MainTab.Albums
    }
    val initialSort = when (albumSettings.getString("default_sort", "时间")) {
        "名称" -> MediaSort.Name
        "大小" -> MediaSort.Size
        else -> MediaSort.Time
    }
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var query by rememberSaveable { mutableStateOf("") }
    var appliedQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(500L)
        appliedQuery = query
    }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var viewerMedia by remember { mutableStateOf<MediaItem?>(null) }
    var viewerScope by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var viewerPlaybackResume by remember { mutableStateOf<PlaybackResumeRequest?>(null) }
    // Keep the shared key alive until the outgoing bounds animation finishes.
    // Clearing it together with selectedMedia would remove the source element
    // from the transition and make the image jump to its thumbnail.
    var activeSharedMediaKey by remember { mutableStateOf<String?>(null) }
    var editingMedia by remember { mutableStateOf<MediaItem?>(null) }
    // Keep the Scaffold slots measured while a shared image is travelling.
    // Their alpha follows the same 360 ms window as the page transition so
    // the chrome cannot visually lag behind the thumbnail.
    val pageChromeAlpha by animateFloatAsState(
        targetValue = if (selectedMedia == null && editingMedia == null) 1f else 0f,
        animationSpec = tween(360, easing = CubicBezierEasing(.22f, .78f, .24f, 1f)),
        label = "page-chrome-alpha"
    )
    // The editor temporarily replaces the viewer; retain the item for return.
    var editorReturnMedia by remember { mutableStateOf<MediaItem?>(null) }
    var transferRequest by remember { mutableStateOf<TransferRequest?>(null) }
    var pixivArchiveMoveUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeletes by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var pixivArchivePendingDeleteUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingAppDelete by remember { mutableStateOf<List<MediaItem>?>(null) }
    var pendingRename by remember { mutableStateOf<Pair<MediaItem, String>?>(null) }
    var pendingRecycleIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var externalDeleteRequestInFlight by remember { mutableStateOf(false) }
    var pendingTrashRestore by remember { mutableStateOf<List<com.example.album.data.RecycleEntry>>(emptyList()) }
    var pendingTrashDelete by remember { mutableStateOf<List<com.example.album.data.RecycleEntry>>(emptyList()) }
    var cleanupOpen by rememberSaveable { mutableStateOf(false) }
    var wallpaperManagerOpen by rememberSaveable { mutableStateOf(false) }
    var wallpaperShowVideos by rememberSaveable { mutableStateOf(false) }
    var wallpaperQuery by rememberSaveable { mutableStateOf("") }
    var wallpaperSelectionMode by rememberSaveable { mutableStateOf(false) }
    var wallpaperSelectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var wallpaperSelectionOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var pixivReloadGeneration by remember { mutableIntStateOf(0) }
    var pixivReloadJob by remember { mutableStateOf<Job?>(null) }
    var wallpaperColumns by rememberSaveable { mutableIntStateOf(4) }
    var wallpaperMediaLayout by rememberSaveable { mutableStateOf(MediaLayout.Grid) }
    var wallpaperFolderLayout by rememberSaveable { mutableStateOf(MediaLayout.Grid) }
    var wallpaperFolderMode by rememberSaveable { mutableStateOf(false) }
    var wallpaperOpenedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var wallpaperSort by rememberSaveable { mutableStateOf(WallpaperSort.Time) }
    var wallpaperSortDirection by rememberSaveable { mutableStateOf(SortDirection.Descending) }
    var pixivArchiveOpen by rememberSaveable { mutableStateOf(false) }
    var favoriteFilter by rememberSaveable { mutableStateOf(false) }
    var mediaSort by rememberSaveable { mutableStateOf(initialSort) }
    var sortDirection by rememberSaveable { mutableStateOf(SortDirection.Descending) }
    var albumColumns by rememberSaveable { mutableIntStateOf(3) }
    var folderColumns by rememberSaveable { mutableIntStateOf(4) }
    var timelineColumns by rememberSaveable { mutableIntStateOf(4) }
    var folderLayout by rememberSaveable { mutableStateOf(MediaLayout.Grid) }
    var timelineLayout by rememberSaveable { mutableStateOf(MediaLayout.Grid) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showColumnDialog by remember { mutableStateOf(false) }
    var showLayoutDialog by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }
    var timelineJumpDate by rememberSaveable { mutableStateOf<String?>(null) }
    var timelineShowsVideos by rememberSaveable { mutableStateOf(false) }
    var albumShowsVideos by rememberSaveable { mutableStateOf(false) }
    var showExcludeDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showPixivHomeIntro by remember { mutableStateOf(false) }
    var showPixivArchiveInfo by remember { mutableStateOf(false) }
    var createFolderName by rememberSaveable { mutableStateOf("") }
    var favoriteUris by remember { mutableStateOf(preferences.getStringSet("favorites", emptySet()).orEmpty().toSet()) }
    var favoriteKeys by remember { mutableStateOf(decodeFavoriteKeys(preferences.getString("favorite_keys", null))) }
    var wallpaperQueueLoaded by remember { mutableStateOf(false) }
    var wallpaperQueueUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var wallpaperQueueSaveJob by remember { mutableStateOf<Job?>(null) }
    var slideshowQueueOpen by rememberSaveable { mutableStateOf(false) }
    var slideshowQueueUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var slideshowQueueOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var slideshowQueueSaveJob by remember { mutableStateOf<Job?>(null) }
    var slideshowQueueColumns by rememberSaveable { mutableIntStateOf(4) }
    var slideshowQueueLayout by rememberSaveable { mutableStateOf(MediaLayout.Grid) }
    var slideshowQueueSort by rememberSaveable { mutableStateOf(MediaSort.Time) }
    var slideshowQueueSortDirection by rememberSaveable { mutableStateOf(SortDirection.Ascending) }
    var showSlideshowColumnDialog by remember { mutableStateOf(false) }
    var showSlideshowLayoutDialog by remember { mutableStateOf(false) }
    var showSlideshowSortDialog by remember { mutableStateOf(false) }
    val wallpaperImportState by WallpaperImportCoordinator.state.collectAsState()
    val wallpaperImportRunning = wallpaperImportState is WallpaperImportState.Running
    var showFavoriteBadge by remember { mutableStateOf(albumSettings.getBoolean("show_favorite_badge", true)) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectionGestureActive by remember { mutableStateOf(false) }
    var selectingFolders by rememberSaveable { mutableStateOf(false) }
    var selectionMediaSort by rememberSaveable { mutableStateOf(initialSort) }
    var selectionMediaSortDirection by rememberSaveable { mutableStateOf(SortDirection.Descending) }
    var selectionMediaOrderUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectionAnchorUri by remember { mutableStateOf<String?>(null) }
    var selectionAnchorFolder by remember { mutableStateOf<String?>(null) }
    var showWallpaperSortDialog by remember { mutableStateOf(false) }
    var showWallpaperColumnDialog by remember { mutableStateOf(false) }
    var showWallpaperLayoutDialog by remember { mutableStateOf(false) }
    var showWallpaperSettings by remember { mutableStateOf(false) }
    var wallpaperCropItem by remember { mutableStateOf<MediaItem?>(null) }
    var wallpaperAppliedSignature by remember { mutableStateOf<String?>(null) }
    var wallpaperComponentActive by remember { mutableStateOf(false) }
    var wallpaperAppliedRefresh by remember { mutableIntStateOf(0) }

    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var wallpaperQueueOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(context) {
        val loaded = withContext(Dispatchers.IO) { WallpaperQueueStore.load(context) }
        if (!wallpaperQueueLoaded) {
            wallpaperQueueUris = loaded.uris
            wallpaperQueueOrder = loaded.order
            wallpaperQueueLoaded = true
        }
    }
    fun persistWallpaperQueue(uris: Set<String>, order: List<String>) {
        wallpaperQueueSaveJob?.cancel()
        wallpaperQueueSaveJob = scope.launch(Dispatchers.IO) {
            WallpaperQueueStore.save(context, WallpaperQueueState(uris, order))
        }
    }
    // Re-applying a wallpaper used to be lost when the app was updated or
    // reinstalled: the launcher drops the live wallpaper and the private queue
    // can be gone. Restore it from the backup copy so one confirmation brings
    // the wallpaper back.
    var wallpaperRestoreAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(wallpaperQueueLoaded, wallpaperQueueUris, library.loading, library.images.size, library.videos.size) {
        if (!wallpaperQueueLoaded || wallpaperRestoreAttempted || library.loading) return@LaunchedEffect
        val appliedKind = when {
            WallpaperAppliedStore.appliedSignature(context, WallpaperAppliedStore.KIND_DYNAMIC) != null -> WallpaperAppliedStore.KIND_DYNAMIC
            WallpaperAppliedStore.appliedSignature(context, WallpaperAppliedStore.KIND_STATIC) != null -> WallpaperAppliedStore.KIND_STATIC
            else -> null
        }
        val stillActive = appliedKind?.let { WallpaperAppliedStore.isWallpaperActive(context, it) } == true
        val queuePresent = wallpaperQueueUris.isNotEmpty()
        // Keep a backup of the applied queue even when it was applied before
        // the backup existed: without one a dropped live wallpaper (a package
        // replacement does that on some launchers) cannot be restored.
        if (appliedKind != null && queuePresent) {
            val ordered = wallpaperQueueOrder.filter { it in wallpaperQueueUris }
                .ifEmpty { wallpaperQueueUris.toList() }
            withContext(Dispatchers.IO) {
                if (WallpaperBackup.load(context) == null) {
                    WallpaperBackup.save(context, appliedKind, ordered)
                }
            }
        }
        wallpaperRestoreAttempted = true
        preferences.edit().putInt("wallpaper_restore_version", com.example.album.BuildConfig.VERSION_CODE).apply()
        // Installing an update replaces the package, and the system unbinds our
        // live wallpaper with it. Re-bind it here, but only while the still
        // wallpaper is still the one we left behind: if the user has picked a
        // wallpaper of their own since, it must not be touched.
        val fallbackId = WallpaperAppliedStore.fallbackStaticId(context)
        val currentId = WallpaperAppliedStore.currentStaticId(context)
        val userPickedOwnWallpaper = fallbackId != -1 && currentId != -1 && currentId != fallbackId
        if (appliedKind != null && !stillActive && !userPickedOwnWallpaper) {
            withContext(Dispatchers.IO) { WallpaperAppliedStore.rebind(context, appliedKind) }
        }
    }
    LaunchedEffect(context) {
        val loaded = withContext(Dispatchers.IO) { SlideshowQueueStore.load(context) }
        slideshowQueueUris = loaded.uris
        slideshowQueueOrder = loaded.order
    }
    // Favorites are stored by URI, and a URI changes when a file is renamed,
    // moved, or re-indexed by MediaStore. Every library scan records the
    // identity of the favorites it can still see and re-points the ones whose
    // URI changed, so the star does not silently disappear.
    LaunchedEffect(library.images, library.videos, library.localImages, library.localVideos) {
        val items = (library.images + library.videos + library.localImages + library.localVideos)
            .distinctBy { it.uri.toString() }
            .map { it.toFavoriteCandidate() }
        if (items.isEmpty()) return@LaunchedEffect
        val repaired = repairFavoriteUris(favoriteUris, favoriteKeys, items)
        if (repaired.favorites != favoriteUris) {
            favoriteUris = repaired.favorites
            preferences.edit().putStringSet("favorites", repaired.favorites).apply()
        }
        if (repaired.keys != favoriteKeys) {
            favoriteKeys = repaired.keys
            preferences.edit().putString("favorite_keys", encodeFavoriteKeys(repaired.keys)).apply()
        }
    }
    fun persistSlideshowQueue(uris: Set<String>, order: List<String>) {
        slideshowQueueSaveJob?.cancel()
        slideshowQueueSaveJob = scope.launch(Dispatchers.IO) {
            SlideshowQueueStore.save(context, SlideshowQueueStore.State(uris, order))
        }
    }
    fun addToSlideshowQueue(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val keys = items.map { it.uri.toString() }
        val updated = slideshowQueueUris + keys
        val order = slideshowQueueOrder + keys.filterNot { it in slideshowQueueOrder }
        slideshowQueueUris = updated
        slideshowQueueOrder = order
        persistSlideshowQueue(updated, order)
        Toast.makeText(context, if (english) "Added to slideshow queue" else "已加入幻灯片队列", Toast.LENGTH_SHORT).show()
    }
    val slideshowQueueMedia by remember { derivedStateOf {
        val all = (library.images + library.localImages + library.videos + library.localVideos)
            .distinctBy { it.uri.toString() }
        val positions = slideshowQueueOrder.withIndex().associate { it.value to it.index }
        all.filter { it.uri.toString() in slideshowQueueUris }
            .sortedBy { positions[it.uri.toString()] ?: Int.MAX_VALUE }
    } }
    // The queue page and the slideshow itself both use this list; the play
    // button used to start playback from the unsorted queue, which made every
    // sort option look broken.
    val slideshowQueueSorted by remember {
        derivedStateOf {
            val sorted = when (slideshowQueueSort) {
                MediaSort.Name -> slideshowQueueMedia.sortedBy { it.name.lowercase() }
                MediaSort.Size -> slideshowQueueMedia.sortedBy { it.size }
                MediaSort.Duration -> slideshowQueueMedia.sortedBy { it.duration }
                else -> slideshowQueueMedia.sortedBy { it.dateTaken }
            }
            if (slideshowQueueSortDirection == SortDirection.Descending) sorted.reversed() else sorted
        }
    }
    var slideshowQueueFolderMode by rememberSaveable { mutableStateOf(false) }
    var slideshowQueueFolderLayout by rememberSaveable { mutableStateOf(MediaLayout.Grid) }
    var slideshowQueueOpenedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    // True while the slideshow was started from the play button: the viewer
    // then opens in full screen and begins playing immediately.
    var slideshowStartImmersive by remember { mutableStateOf(false) }
    var showSlideshowSettings by remember { mutableStateOf(false) }
    var showGestureHints by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Most of the app's gestures are invisible; spell them out once.
        if (!albumSettings.getBoolean("gesture_hints_shown", false)) showGestureHints = true
    }
    var suspendedSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(selectionMode) {
        if (!selectionMode) {
            selectionGestureActive = false
            selectedUris = emptySet()
            selectedFolders = emptySet()
            selectingFolders = false
            selectionMediaOrderUris = emptyList()
        }
    }
    var selectionFolderFirstVisibleItem by remember { mutableIntStateOf(0) }
    var selectionFolderFirstVisibleOffset by remember { mutableIntStateOf(0) }
    var albumFirstVisibleItem by remember { mutableIntStateOf(0) }
    var albumFirstVisibleOffset by remember { mutableIntStateOf(0) }
    // Where the page was before the search page opened, so leaving the search
    // page returns there instead of somewhere the filtered list clamped to.
    var pageScrollRequest by remember { mutableStateOf<com.example.album.ui.PageScrollRequest?>(null) }
    var searchPageOpenForTab by remember { mutableStateOf<MainTab?>(null) }
    var searchReturnItem by remember { mutableIntStateOf(0) }
    var searchReturnOffset by remember { mutableIntStateOf(0) }
    // Name of the folder the search list was showing when a folder was opened,
    // so closing the folder comes back to that folder instead of an index that
    // a deletion may have invalidated.
    var searchReturnAnchorFolder by remember { mutableStateOf<String?>(null) }
    var selectionMediaFirstVisibleItem by remember { mutableIntStateOf(0) }
    var selectionMediaFirstVisibleOffset by remember { mutableIntStateOf(0) }
    var timelineFirstVisibleItem by remember { mutableIntStateOf(0) }
    var timelineFirstVisibleOffset by remember { mutableIntStateOf(0) }
    // Whether the page that is on screen is scrolled to its very top. Tracked
    // per page (and per grid inside a folder), because a stored index from the
    // folder list says nothing about the media grid that is actually visible.
    var albumPageAtTop by remember { mutableStateOf(true) }
    var timelinePageAtTop by remember { mutableStateOf(true) }

    var folderScope by remember { mutableStateOf<List<MediaItem>?>(null) }
    // The list the current page displays, so the viewer pages through the same
    // order (and the same filters) the user sees.
    var pageScope by remember { mutableStateOf<List<MediaItem>?>(null) }
    var viewerScrollUri by remember { mutableStateOf<String?>(null) }
    var viewerScrollToken by remember { mutableLongStateOf(0L) }
    var wallpaperReturnTab by remember { mutableStateOf<MainTab?>(null) }
    var wallpaperReturnFolder by remember { mutableStateOf<String?>(null) }
    var openedFolder by rememberSaveable { mutableStateOf<String?>(null) }

    // Entering the search page puts the (much shorter) result list at the top,
    // and leaving it returns to the position the page had before searching
    // instead of wherever the filtered list clamped to.
    LaunchedEffect(appliedQuery, selectedTab, openedFolder) {
        val searching = appliedQuery.isNotBlank() &&
            openedFolder == null &&
            (selectedTab == MainTab.Albums || selectedTab == MainTab.Videos || selectedTab == MainTab.Timeline)
        if (searching && searchPageOpenForTab == null) {
            searchPageOpenForTab = selectedTab
            val onTimeline = selectedTab == MainTab.Timeline
            searchReturnItem = if (onTimeline) timelineFirstVisibleItem else albumFirstVisibleItem
            searchReturnOffset = if (onTimeline) timelineFirstVisibleOffset else albumFirstVisibleOffset
            pageScrollRequest = com.example.album.ui.PageScrollRequest(0, 0, System.nanoTime())
        } else if (!searching && searchPageOpenForTab != null) {
            searchPageOpenForTab = null
            pageScrollRequest = com.example.album.ui.PageScrollRequest(
                searchReturnItem,
                searchReturnOffset,
                System.nanoTime()
            )
        }
    }
    // The request is a one shot: drop it again so switching to another page
    // later cannot replay a scroll that belonged to the previous one.
    LaunchedEffect(pageScrollRequest) {
        if (pageScrollRequest != null) {
            delay(600L)
            pageScrollRequest = null
        }
    }

    // Deleting shortens the list under the grid, which would otherwise clamp to
    // the new end and leave the user at the bottom of the page.
    fun requestPageRestoreAfterDelete() {
        pageScrollRequest = com.example.album.ui.PageScrollRequest(
            if (selectingFolders) albumFirstVisibleItem else selectionMediaFirstVisibleItem,
            if (selectingFolders) albumFirstVisibleOffset else selectionMediaFirstVisibleOffset,
            System.nanoTime(),
            key = if (selectingFolders) selectionAnchorFolder else selectionAnchorUri
        )
    }
    var selectionRenameItem by remember { mutableStateOf<MediaItem?>(null) }
    var selectionRenameFolder by remember { mutableStateOf<String?>(null) }
    var selectionRenameText by remember { mutableStateOf("") }
    var selectionSlideshow by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectionInfoItem by remember { mutableStateOf<MediaItem?>(null) }
    var tagEditorItem by remember { mutableStateOf<MediaItem?>(null) }
    var tagEditorText by remember { mutableStateOf("") }
    var folderBackStack by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var folderReturnQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var navReorderEnabled by remember { mutableStateOf(albumSettings.getBoolean("nav_reorder", false)) }
    var toolsReorderEnabled by remember { mutableStateOf(albumSettings.getBoolean("tools_reorder", false)) }
    var scrollToTopToken by remember { mutableLongStateOf(0L) }
    // Tapping the icon of the page you are already on asks for a refresh; the
    // grids play the pull gesture first instead of reloading invisibly.
    var pullRefreshToken by remember { mutableLongStateOf(0L) }
    var searchReturnAlbumFirstVisibleItem by remember { mutableIntStateOf(0) }
    var searchReturnAlbumFirstVisibleOffset by remember { mutableIntStateOf(0) }
    var searchReturnMediaFirstVisibleItem by remember { mutableIntStateOf(0) }
    var toolsOrder by remember {
        mutableStateOf(
            albumSettings.getString("tools_order", null)
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf("archive", "wallpaper", "cleanup", "slideshow", "pixiv")
        )
    }
    var pixivTabEnabled by remember { mutableStateOf(pixivEnabledAtStart) }
    var pixivLibraryImages by remember { mutableStateOf<List<MediaItem>?>(null) }
    var pixivFolderNames by remember { mutableStateOf(setOf("Pixiv")) }
    var pixivSourceFolderName by remember { mutableStateOf("Pixiv") }
    var pixivTagsByUri by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    // Bumped only when a full walk finishes. Partial snapshots during the walk
    // update the grid but must not restart the (expensive) tag index build.
    var pixivLibraryVersion by remember { mutableIntStateOf(0) }
    var pixivTagsLoading by remember { mutableStateOf(false) }
    var pixivPageRefreshing by remember { mutableStateOf(false) }
    var pixivRefreshKey by remember { mutableIntStateOf(0) }
    var archiveMediaRefreshPending by remember { mutableStateOf(false) }
    var archiveMediaRefreshing by remember { mutableStateOf(false) }
    var pixivSearchMode by rememberSaveable { mutableStateOf(PixivSearchMode.Artist) }
    LaunchedEffect(selectedTab, pixivArchiveOpen, pixivTabEnabled) {
        if (pixivTabEnabled && selectedTab == MainTab.Pixiv && !pixivArchiveOpen &&
            !albumSettings.getBoolean("pixiv_home_intro_seen", false)
        ) {
            showPixivHomeIntro = true
            albumSettings.edit().putBoolean("pixiv_home_intro_seen", true).apply()
        }
    }

    val wallpaperLibraryMedia by remember { derivedStateOf {
        (library.images + library.videos + library.localImages + library.localVideos)
            .distinctBy { it.uri.toString() }
    } }
    val wallpaperQueueMedia by remember { derivedStateOf {
        wallpaperLibraryMedia.filter { it.uri.toString() in wallpaperQueueUris }
    } }
    val wallpaperSearchMedia by remember(wallpaperLibraryMedia, wallpaperShowVideos) {
        derivedStateOf { wallpaperLibraryMedia.filter { it.isVideo == wallpaperShowVideos } }
    }
    val wallpaperApplyItems = remember(
        wallpaperQueueMedia,
        wallpaperShowVideos,
        wallpaperSort,
        wallpaperSortDirection,
        wallpaperQueueOrder
    ) {
        sortWallpaperMedia(
            media = wallpaperQueueMedia.filter { it.isVideo == wallpaperShowVideos },
            sort = wallpaperSort,
            sortDirection = wallpaperSortDirection,
            queueOrder = wallpaperQueueOrder
        )
    }
    val wallpaperApplySignature = remember(wallpaperApplyItems) {
        WallpaperAppliedStore.signature(wallpaperApplyItems.map { it.uri.toString() })
    }
    val wallpaperQueueIsApplied = wallpaperComponentActive &&
        wallpaperApplyItems.isNotEmpty() &&
        wallpaperAppliedSignature == wallpaperApplySignature
    LaunchedEffect(wallpaperShowVideos, wallpaperAppliedRefresh, wallpaperImportState) {
        val kind = if (wallpaperShowVideos) WallpaperAppliedStore.KIND_DYNAMIC else WallpaperAppliedStore.KIND_STATIC
        wallpaperAppliedSignature = withContext(Dispatchers.IO) { WallpaperAppliedStore.appliedSignature(context, kind) }
        wallpaperComponentActive = withContext(Dispatchers.IO) { WallpaperAppliedStore.isWallpaperActive(context, kind) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) wallpaperAppliedRefresh++
            if (event == Lifecycle.Event.ON_RESUME) {
                // Returning from the wallpaper chooser: restore the page the
                // user was on before the system UI took over.
                wallpaperReturnTab?.let { tab ->
                    if (selectedTab != tab) selectedTab = tab
                    wallpaperReturnTab = null
                }
                if (openedFolder != wallpaperReturnFolder && wallpaperReturnFolder != null) {
                    openedFolder = wallpaperReturnFolder
                }
                wallpaperReturnFolder = null
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    fun addToWallpaperQueue(items: List<com.example.album.data.MediaItem>) {
        wallpaperQueueLoaded = true
        val updated = wallpaperQueueUris + items.map { it.uri.toString() }
        val updatedOrder = wallpaperQueueOrder + items.map { it.uri.toString() }.filterNot { it in wallpaperQueueOrder }
        wallpaperQueueUris = updated
        wallpaperQueueOrder = updatedOrder
        persistWallpaperQueue(updated, updatedOrder)
        Toast.makeText(
            context,
            if (english) "Added ${items.size} item(s) to wallpaper queue" else "已加入壁纸队列 ${items.size} 项",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun removeFromWallpaperQueue(item: com.example.album.data.MediaItem) {
        wallpaperQueueLoaded = true
        val updated = wallpaperQueueUris - item.uri.toString()
        val updatedOrder = wallpaperQueueOrder - item.uri.toString()
        wallpaperQueueUris = updated
        wallpaperQueueOrder = updatedOrder
        persistWallpaperQueue(updated, updatedOrder)
    }
    fun requestWallpaper(item: MediaItem) {
        // The wallpaper flows leave the app (system chooser / live wallpaper
        // preview); remember where the user was so returning does not drop
        // them back on the home page.
        wallpaperReturnTab = selectedTab
        wallpaperReturnFolder = openedFolder
        launchWallpaperAppChooser(context, item, english)
    }
    var backgroundOptimizationEnabled by remember {
        mutableStateOf(albumSettings.getBoolean("background_optimization", true))
    }
    val initialTabOrder = remember {
        val stored = albumSettings.getString("nav_order", null)
            ?.split(',')
            ?.mapNotNull { name -> MainTab.entries.firstOrNull { it.name == name } }
            .orEmpty()
        normalizedTabOrder(stored, pixivEnabledAtStart)
    }
    var tabOrder by remember { mutableStateOf(initialTabOrder) }
    val primaryTab = tabOrder.firstOrNull { it in enabledMainTabs(pixivTabEnabled) } ?: MainTab.Albums
    var lastRootBackAt by rememberSaveable { mutableLongStateOf(0L) }
    var bottomBarWidth by remember { mutableIntStateOf(0) }
    var draggedNavTab by remember { mutableStateOf<MainTab?>(null) }
    var navDragX by remember { mutableFloatStateOf(0f) }
    var navDragY by remember { mutableFloatStateOf(0f) }
    var suppressNavClickUntil by remember { mutableLongStateOf(0L) }
    val currentTabOrder by rememberUpdatedState(tabOrder)
    val hapticFeedback = LocalHapticFeedback.current
    val visibleImages by remember { derivedStateOf {
        // The remembered query is not an active search. Returning from search
        // must restore the exact same source as the initial page.
        val source = if (appliedQuery.isBlank()) {
            library.images
        } else {
            (library.images + library.localImages).distinctBy { it.uri }
        }
        if (favoriteFilter) source.filter { it.uri.toString() in favoriteUris } else source
    } }
    val visibleVideos by remember { derivedStateOf {
        val source = if (appliedQuery.isBlank()) {
            (library.videos + library.localVideos).distinctBy { it.uri }
        } else {
            (library.videos + library.localVideos).distinctBy { it.uri }
        }
        if (favoriteFilter) source.filter { it.uri.toString() in favoriteUris } else source
    } }
    val defaultPixivImages by remember { derivedStateOf {
        (library.images + library.localImages)
            .filter { it.folder.equals("Pixiv", ignoreCase = true) }
            .distinctBy { it.uri.toString() }
    } }
    suspend fun reloadPixivPage(forceWalk: Boolean = true) {
        if (selectedTab != MainTab.Pixiv || cleanupOpen) return
        val generation = ++pixivReloadGeneration
        // Loading the cached snapshot is not a "refresh": showing the progress
        // indicator for it is what made opening the app look like it was
        // loading. Only a real SAF walk sets the flag.
        var walking = false
        try {
            // Show the previous snapshot immediately (SAF tree walks are slow)
            // and replace it once the fresh walk finishes.
            if (pixivLibraryImages == null) {
                withContext(Dispatchers.IO) { pixivRepository.loadCachedLibrary() }?.let { cached ->
                    if (generation != pixivReloadGeneration) return
                    pixivLibraryImages = cached.items
                    pixivFolderNames = cached.folderNames
                    pixivSourceFolderName = cached.sourceFolderName
                    pixivLibraryVersion++
                }
            }
            if (!forceWalk && pixivLibraryImages != null) {
                // Nothing to do: the cached snapshot (which survives app
                // restarts) is what the page shows. Walking the SAF tree again
                // on every entry is exactly what made opening the page feel
                // like a load.
                return
            }
            pixivPageRefreshing = true
            walking = true
            // A full SAF walk can take several seconds. Partial snapshots are
            // streamed through a conflated channel so folders appear as soon as
            // they are read instead of all at the end.
            val progress = Channel<com.example.album.data.PixivLibrarySnapshot>(Channel.CONFLATED)
            val progressJob = scope.launch {
                for (partial in progress) {
                    if (generation != pixivReloadGeneration) break
                    pixivLibraryImages = partial.items
                    pixivFolderNames = partial.folderNames
                    pixivSourceFolderName = partial.sourceFolderName
                }
            }
            val snapshot = try {
                pixivRepository.loadLibrary(defaultPixivImages) { partial ->
                    progress.trySend(partial)
                }
            } finally {
                progress.close()
                progressJob.cancel()
            }
            if (generation != pixivReloadGeneration) return
            pixivLibraryImages = snapshot.items
            pixivTagsByUri = snapshot.tagsByUri
            pixivFolderNames = snapshot.folderNames
            pixivSourceFolderName = snapshot.sourceFolderName
            pixivLibraryVersion++
        } finally {
            if (walking && generation == pixivReloadGeneration) pixivPageRefreshing = false
        }
    }
    fun requestPixivReload(forceWalk: Boolean = true) {
        pixivReloadJob?.cancel()
        pixivReloadJob = scope.launch {
            // Triggers often arrive together (a library refresh plus an explicit
            // request); the short wait collapses them into one SAF walk instead
            // of cancelling and restarting a walk that just started.
            delay(350L)
            reloadPixivPage(forceWalk)
        }
    }
    LaunchedEffect(pixivTabEnabled, cleanupOpen) {
        // Show the cached snapshot only. The SAF walk is driven by the explicit
        // refresh below or by the first visit when nothing is cached, never by
        // opening the app or another page.
        requestPixivReload(forceWalk = false)
    }
    LaunchedEffect(pixivRefreshKey) {
        if (pixivRefreshKey > 0) requestPixivReload(forceWalk = true)
    }
    LaunchedEffect(selectedTab) {
        // Entering the page again must not start another walk: the snapshot
        // that is already loaded stays on screen until the user refreshes.
        if (selectedTab == MainTab.Pixiv && pixivLibraryImages == null) requestPixivReload(forceWalk = false)
    }
    val needsFolderSearchIndex by remember {
        derivedStateOf {
            appliedQuery.isNotBlank() &&
                selectedTab in setOf(MainTab.Albums, MainTab.Videos) &&
                openedFolder == null &&
                (!selectionMode || selectingFolders)
        }
    }
    LaunchedEffect(needsFolderSearchIndex) {
        if (needsFolderSearchIndex) {
            library.loadSearchableFolderNames()
        }
    }
    val pixivImages by remember { derivedStateOf {
        val source = pixivLibraryImages ?: defaultPixivImages
        if (favoriteFilter) source.filter { it.uri.toString() in favoriteUris } else source
    } }
    val pixivSearchImages by remember { derivedStateOf {
        val allowedFolders = pixivFolderNames + pixivSourceFolderName
        pixivImages.filter { it.folder in allowedFolders }
    } }
    LaunchedEffect(pixivSearchMode, appliedQuery, pixivLibraryVersion, favoriteFilter) {
        // Load the tag index once for the current Pixiv library. Searching is
        // local filtering; tying this job to every keystroke cancels the
        // full read repeatedly on large archives and can leave no results.
        // The key is the completed-walk version, not the image list, so the
        // partial snapshots streamed while walking do not rebuild the index.
        // Switching the mode alone must not load anything: the index is only
        // read once the user actually searches by tag.
        if (pixivSearchMode == PixivSearchMode.Tag && appliedQuery.isNotBlank()) {
            pixivTagsLoading = true
            try {
                pixivTagsByUri = pixivRepository.loadTags(pixivImages)
            } finally {
                pixivTagsLoading = false
            }
        } else {
            pixivTagsByUri = emptyMap()
            pixivTagsLoading = false
        }
    }
    val albumImages by remember { derivedStateOf {
        (visibleImages + library.localImages).distinctBy { it.uri }
    } }
    val pixivTagSearchIndex = remember(pixivSearchImages, pixivTagsByUri) {
        if (pixivTagsByUri.isEmpty()) emptyList()
        else pixivSearchImages.map { item ->
            PixivTagSearchEntry(item, pixivTagsByUri[item.uri.toString()].orEmpty())
        }
    }
    val pixivTagResults by remember { derivedStateOf {
        val tagQuery = appliedQuery.trim().removePrefix("#").trim()
        if (tagQuery.isBlank()) emptyList()
        else {
            // The tag index and each item's uri string are precomputed, so a
            // keystroke only scans cached strings instead of rebuilding them
            // for the whole archive on the main thread.
            val index = pixivTagSearchIndex
            if (index.isEmpty()) {
                pixivSearchImages.filter { item ->
                    pixivRepository.matchesTagQuery(pixivTagsByUri[item.uri.toString()].orEmpty(), tagQuery)
                }
            } else {
                index.filter { entry -> pixivRepository.matchesTagQuery(entry.tags, tagQuery) }.map { it.item }
            }
        }
    } }
    val currentSelectionMedia by remember { derivedStateOf {
        val openedMediaScope = if (openedFolder != null) {
            folderScope?.takeIf { items -> items.all { it.folder == openedFolder } }
        } else null
        when (selectedTab) {
            MainTab.Videos -> openedMediaScope
                ?: openedFolder?.let { folder -> visibleVideos.filter { it.folder == folder } }
                ?: visibleVideos
            MainTab.Albums -> openedMediaScope
                ?: openedFolder?.let { folder -> albumImages.filter { it.folder == folder } }
                ?: albumImages
            MainTab.Timeline -> if (timelineShowsVideos) visibleVideos else visibleImages
            MainTab.Pixiv -> if (pixivSearchMode == PixivSearchMode.Tag && appliedQuery.isNotBlank()) {
                pixivTagResults
            } else {
                openedMediaScope
                    ?: openedFolder?.let { folder -> pixivSearchImages.filter { it.folder == folder } }
                    ?: pixivSearchImages
            }
            MainTab.Tools -> emptyList()
            MainTab.Settings -> emptyList()
        }
    } }
    val selectionMedia by remember { derivedStateOf {
        val text = appliedQuery.trim()
        if (text.isBlank() || (selectedTab == MainTab.Pixiv && pixivSearchMode == PixivSearchMode.Tag)) {
            currentSelectionMedia
        } else {
            // A search is global. Do not keep using folderScope here: it is a
            // transient view state and can otherwise hide matches after the
            // user enters multi-select from a folder or returns from search.
            val searchSource = when (selectedTab) {
                MainTab.Videos -> visibleVideos
                MainTab.Albums -> albumImages
                MainTab.Timeline -> if (timelineShowsVideos) visibleVideos else visibleImages
                MainTab.Pixiv -> pixivSearchImages
                MainTab.Tools -> emptyList()
                MainTab.Settings -> emptyList()
            }
            searchMedia(text, searchSource)
        }
    } }

    fun freezeSelectionSort() {
        if (selectedTab == MainTab.Timeline) {
            selectionMediaSort = MediaSort.Time
            selectionMediaSortDirection = SortDirection.Descending
        } else {
            selectionMediaSort = mediaSort
            selectionMediaSortDirection = sortDirection
        }
        // selectionMedia is already the exact list shown by the current
        // folder, search, timeline, or Pixiv view. Re-sorting it here makes
        // entering selection visibly reorder the grid when that view has a
        // more specific display order than the global sort setting.
        val ordered = if (selectedTab == MainTab.Timeline) {
            sortMediaItems(selectionMedia, MediaSort.Time, SortDirection.Descending)
        } else {
            selectionMedia
        }
        selectionMediaOrderUris = ordered.map { it.uri.toString() }
    }
    // Search is a view over the source; it must never become the source of
    // truth for a selection. Actions resolve selected keys from the complete
    // media set so items hidden by the current query remain actionable.
    val completeSelectionMedia by remember { derivedStateOf {
        val source = when (selectedTab) {
            MainTab.Videos -> library.videos + library.localVideos
            MainTab.Albums -> library.images + library.videos + library.localImages + library.localVideos
            MainTab.Timeline -> if (timelineShowsVideos) {
                library.videos + library.localVideos
            } else {
                library.images + library.localImages
            }
            MainTab.Pixiv -> pixivSearchImages
            MainTab.Tools -> emptyList()
            MainTab.Settings -> emptyList()
        }
        // favoriteFilter is a display filter, just like query. Do not apply
        // it here or changing the view can silently shrink the action set.
        source.distinctBy { it.uri.toString() }
    } }
    val selectedItemsForAction by remember { derivedStateOf {
        if (selectingFolders) completeSelectionMedia.filter { it.folder in selectedFolders }
        else completeSelectionMedia.filter { it.uri.toString() in selectedUris }
    } }
    val observerRefreshJob = remember { arrayOfNulls<Job>(1) }
    var suppressObserverRefreshUntil by remember { mutableLongStateOf(0L) }
    val latestSuppressObserverRefreshUntil by rememberUpdatedState(suppressObserverRefreshUntil)

    DisposableEffect(lifecycleOwner, backgroundOptimizationEnabled) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // Re-check permissions after returning from Settings (users
                    // can revoke image/video access while this activity is
                    // stopped), but do not rescan the library: coming back from
                    // the background used to trigger a full reload every time.
                    val granted = hasMediaPermission(context)
                    if (granted != library.permissionGranted) {
                        scope.launch { library.refresh(granted) }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    // Do not keep decoding thumbnails while the app is in the
                    // background. Visible screens still load on demand later.
                    com.example.album.data.ThumbnailRepository.cancelBackgroundOptimization()
                }
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver) }
    }

    fun openTagEditor(item: MediaItem) {
        tagEditorText = pixivTagsByUri[item.uri.toString()].orEmpty().joinToString("\n")
        tagEditorItem = item
        scope.launch {
            val tags = runCatching { withContext(Dispatchers.IO) { pixivRepository.readTags(item) } }
                .getOrDefault(pixivTagsByUri[item.uri.toString()].orEmpty())
            if (tagEditorItem?.uri == item.uri) tagEditorText = tags.joinToString("\n")
        }
    }

    fun tabIcon(tab: MainTab): ImageVector = if (tab == MainTab.Albums && albumShowsVideos) {
        Icons.Outlined.VideoLibrary
    } else tab.icon

    fun tabLabel(tab: MainTab): String = if (appLanguage == "English") when (tab) {
        MainTab.Albums -> if (albumShowsVideos) "Videos" else "Albums"
        MainTab.Videos -> "Videos"
        MainTab.Timeline -> "Timeline"
        MainTab.Pixiv -> "Pixiv"
        MainTab.Tools -> "Tools"
        MainTab.Settings -> "Settings"
    } else when (tab) {
        // The label follows the content in both languages, not just English:
        // the icon already switches, and "视频 icon + 相册 label" reads wrong.
        MainTab.Albums -> if (albumShowsVideos) "视频" else "相册"
        else -> tab.label
    }

    fun mediaInDisplayOrder(items: List<MediaItem>): List<MediaItem> {
        return if (selectedTab == MainTab.Timeline) {
            sortMediaItems(items, MediaSort.Time, SortDirection.Descending)
        } else sortMediaItems(items, mediaSort, sortDirection)
    }

    fun openMedia(item: MediaItem) {
        // Video playback has no shared-image destination; keep its thumbnail
        // visible while the native player performs its regular entrance.
        activeSharedMediaKey = item.takeUnless { it.isVideo }?.let { "media:${it.uri}" }
        // A folder owns the exact ordered list emitted by AlbumsScreen. Keep
        // using that scope while opening from a folder; the global search list
        // is only appropriate on the root/search views.
        val visibleSource = if (openedFolder != null) currentSelectionMedia else selectionMedia
        val visibleItems = visibleSource.filter { it.isVideo == item.isVideo }
        // AlbumsScreen already provides the folder's exact display order. Do
        // not sort that list a second time: reversing a stable sort twice can
        // change the order of files that share the same timestamp or name.
        val hasExactFolderOrder = openedFolder != null && folderScope?.let { scope ->
            scope.isNotEmpty() && scope.all { it.folder == openedFolder } &&
                scope.any { it.uri == item.uri }
        } == true
        val candidateScope = when {
            // The timeline shows the library list order grouped by date; the
            // player must use that same order for "next video".
            // Whatever page is showing reports its exact list; paging must
            // follow it (order, filters and search included).
            pageScope?.any { it.uri == item.uri } == true &&
                pageScope?.all { it.isVideo == item.isVideo } == true -> pageScope!!
            selectedTab == MainTab.Timeline -> {
                val timelineItems = (if (timelineShowsVideos) visibleVideos else visibleImages)
                    .filter { it.isVideo == item.isVideo }
                timelineItems.takeIf { scope -> scope.any { it.uri == item.uri } } ?: visibleItems
            }
            hasExactFolderOrder -> visibleItems
            else -> mediaInDisplayOrder(visibleItems)
        }
            ?: if (item.isVideo) {
                mediaInDisplayOrder((library.videos + library.localVideos).distinctBy { it.uri.toString() })
            } else if (selectedTab == MainTab.Pixiv) {
                mediaInDisplayOrder(pixivImages)
            } else {
                mediaInDisplayOrder((library.images + library.localImages).distinctBy { it.uri.toString() })
            }
        // ACTION_VIEW can provide a URI that is not in Album's library yet.
        // Keep that exact item in the playlist instead of handing the player
        // an empty list or a list for another video.
        viewerScope = candidateScope.takeIf { scope -> scope.any { it.uri == item.uri } } ?: listOf(item)
        viewerMedia = item
        selectedMedia = item
    }

    LaunchedEffect(externalMediaUri) {
        val uri = externalMediaUri ?: return@LaunchedEffect
        val filename = Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast('/')
        val mime = runCatching { context.contentResolver.getType(uri).orEmpty() }.getOrDefault("").ifBlank {
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(filename.substringAfterLast('.', "").lowercase())
                .orEmpty()
        }
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) return@LaunchedEffect
        val readable = withContext(Dispatchers.IO) {
            runCatching {
                com.example.album.data.openMediaInputStream(context, uri)?.use { input -> input.read() >= 0 } == true
            }.getOrDefault(false)
        }
        if (!readable) {
            Toast.makeText(context, if (english) "Unable to read this media" else "无法读取此媒体文件", Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }
        val name = filename.ifBlank { "外部媒体" }
        val externalItem = MediaItem(
            id = uri.toString().hashCode().toLong() and 0xffffffffL,
            uri = uri,
            name = name,
            folder = "外部打开",
            dateTaken = 0L,
            mimeType = mime,
            isVideo = mime.startsWith("video/"),
            isDocument = true
        )
        albumShowsVideos = externalItem.isVideo
        selectedTab = MainTab.Albums
        openMedia(externalItem)
    }

    LaunchedEffect(externalWallpaperUri) {
        val uri = externalWallpaperUri ?: return@LaunchedEffect
        val filename = Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast('/')
        val mime = runCatching { context.contentResolver.getType(uri).orEmpty() }.getOrDefault("").ifBlank {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(filename.substringAfterLast('.', "").lowercase()).orEmpty()
        }
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) return@LaunchedEffect
        val readable = withContext(Dispatchers.IO) {
            runCatching {
                com.example.album.data.openMediaInputStream(context, uri)?.use { input -> input.read() >= 0 } == true
            }.getOrDefault(false)
        }
        if (!readable) {
            Toast.makeText(context, if (english) "Unable to read this media" else "无法读取此媒体文件", Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }
        val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "外部壁纸"
        val externalItem = MediaItem(
            id = uri.toString().hashCode().toLong() and 0xffffffffL,
            uri = uri,
            name = name,
            folder = "外部壁纸",
            dateTaken = 0L,
            mimeType = mime,
            isVideo = mime.startsWith("video/"),
            isDocument = true
        )
        if (externalItem.isVideo) setWallpaper(context, externalItem, english)
        else wallpaperCropItem = externalItem
    }

    // "Share to Album" from another app: offer to copy the shared photos and
    // videos into a library folder through the normal destination screen.
    LaunchedEffect(externalSharedUris) {
        if (externalSharedUris.isEmpty()) return@LaunchedEffect
        val items = withContext(Dispatchers.IO) {
            externalSharedUris.mapNotNull { uri -> uri.toSharedMediaItem(context) }
        }
        onExternalShareConsumed()
        if (items.isEmpty()) {
            Toast.makeText(
                context,
                if (english) "Unable to read the shared media" else "无法读取分享的媒体文件",
                Toast.LENGTH_LONG
            ).show()
            return@LaunchedEffect
        }
        transferRequest = TransferRequest(items, TransferMode.Copy)
        Toast.makeText(
            context,
            if (english) "Choose where to import ${items.size} item(s)" else "请选择导入 ${items.size} 个文件的位置",
            Toast.LENGTH_SHORT
        ).show()
    }

    LaunchedEffect(playbackResumeRequest, library.videos, library.localVideos) {
        val request = playbackResumeRequest ?: return@LaunchedEffect
        val video = (library.videos + library.localVideos)
            .firstOrNull { it.uri.toString() == request.uri }
            ?: return@LaunchedEffect
        viewerPlaybackResume = request
        albumShowsVideos = true
        selectedTab = MainTab.Albums
        openMedia(video)
        onPlaybackResumeConsumed(request.requestId)
    }

    fun beginEditing(item: MediaItem) {
        val returnToViewer = selectedMedia != null && viewerMedia != null
        editorReturnMedia = item.takeIf { returnToViewer }
        editingMedia = item
        if (!returnToViewer) {
            activeSharedMediaKey = null
            selectedMedia = null
            viewerMedia = null
        }
    }

    fun finishEditing() {
        val returnItem = editorReturnMedia
        editingMedia = null
        editorReturnMedia = null
        if (returnItem != null) openMedia(returnItem)
    }

    fun closeFolder() {
        // Restore the scroll position the search results had before the folder
        // was opened; otherwise the folder's position leaks into the search
        // list and it lands near the end.
        albumFirstVisibleItem = searchReturnAlbumFirstVisibleItem
        selectionMediaFirstVisibleItem = searchReturnMediaFirstVisibleItem
        // Anchor on the folder that was on screen: an index saved before the
        // folder was opened can be past the end of the list once files were
        // deleted inside it, which dropped the search page at the bottom.
        pageScrollRequest = com.example.album.ui.PageScrollRequest(
            searchReturnAlbumFirstVisibleItem,
            searchReturnAlbumFirstVisibleOffset,
            System.nanoTime(),
            key = searchReturnAnchorFolder
        )
        // Restore the search text and its applied form in the same snapshot:
        // the 500 ms debounce used for typing made the page flash the
        // unfiltered list (and its old scroll position) before searching again.
        val restoredQuery = folderReturnQuery.orEmpty()
        openedFolder = null
        folderScope = null
        folderBackStack = emptyList()
        query = restoredQuery
        appliedQuery = restoredQuery
        folderReturnQuery = null
        searchOpen = true
    }

    fun openFolder(folder: String?) {
        if (folder == null) {
            folderBackStack = emptyList()
            folderReturnQuery = null
        } else {
            if (openedFolder == null) {
                // Only a search that is still open is restored when the folder
                // closes. A search the user already left must not re-open.
                folderReturnQuery = if (searchOpen) query.takeIf { it.isNotBlank() } else null
                searchReturnAlbumFirstVisibleItem = albumFirstVisibleItem
                searchReturnAlbumFirstVisibleOffset = albumFirstVisibleOffset
                searchReturnAnchorFolder = selectionAnchorFolder
                searchReturnMediaFirstVisibleItem = selectionMediaFirstVisibleItem
                folderBackStack = listOf(folder)
            } else {
                val currentPath = folderBackStack.ifEmpty { listOfNotNull(openedFolder) }
                folderBackStack = if (currentPath.lastOrNull() == folder) currentPath else currentPath + folder
            }
        }
        openedFolder = folder
        folderScope = null
        if (folder != null) {
            // A folder opened from a search must show all of its contents;
            // the parent search text must not become a filename filter.
            query = ""
            appliedQuery = ""
            suspendedSearchQuery = null
            searchOpen = false
        }
    }

    fun navigateFolderBack() {
        if (openedFolder == null) return
        val currentPath = folderBackStack.ifEmpty { listOfNotNull(openedFolder) }
        if (currentPath.size > 1) {
            folderBackStack = currentPath.dropLast(1)
            openedFolder = folderBackStack.last()
            folderScope = null
            query = ""
        } else {
            closeFolder()
        }
    }

    fun clearSelection() {
        selectionMode = false
        selectingFolders = false
        selectedUris = emptySet()
        selectedFolders = emptySet()
    }

    fun selectMainTab(tab: MainTab) {
        if (selectedTab == tab) return
        selectedTab = tab
        query = ""
        suspendedSearchQuery = null
        searchOpen = true
        openedFolder = null
        folderScope = null
        folderBackStack = emptyList()
        folderReturnQuery = null
        clearSelection()
        timelineJumpDate = null
    }

    fun returnToPrimaryTab() {
        clearSelection()
        folderReturnQuery = null
        closeFolder()
        timelineJumpDate = null
        favoriteFilter = false
        suspendedSearchQuery = null
        searchOpen = true
        lastRootBackAt = 0L
        if (selectedTab != primaryTab) selectedTab = primaryTab
    }

    fun suspendSearch() {
        if (query.isBlank()) {
            suspendedSearchQuery = null
            searchOpen = false
            return
        }
        // Leaving the search page forgets the search: the field is cleared and
        // the next tap starts a new one. Remembering the text made the page
        // restore an old query the user had already left.
        suspendedSearchQuery = null
        query = ""
        appliedQuery = ""
        searchOpen = false
        // Return to where the page was before the search started, in the same
        // snapshot the results disappear in.
        pageScrollRequest = com.example.album.ui.PageScrollRequest(
            searchReturnAlbumFirstVisibleItem,
            searchReturnAlbumFirstVisibleOffset,
            System.nanoTime(),
            key = searchReturnAnchorFolder
        )
    }

    fun resumeSearch() {
        query = suspendedSearchQuery ?: query
        suspendedSearchQuery = null
        searchOpen = true
    }

    val appBackEnabled by remember {
        derivedStateOf {
            val standalonePageOpen = transferRequest != null || pixivArchiveOpen || cleanupOpen || wallpaperManagerOpen
            (standalonePageOpen || (selectedMedia == null && editingMedia == null && selectionSlideshow.isEmpty())) &&
                pendingAppDelete == null &&
                selectionRenameItem == null &&
                selectionInfoItem == null &&
                tagEditorItem == null &&
                !showSortDialog &&
                !showColumnDialog &&
                !showLayoutDialog &&
                !showWallpaperSortDialog &&
                !showWallpaperColumnDialog &&
                !showWallpaperLayoutDialog &&
                !showWallpaperSettings &&
                !showDateDialog &&
                !showExcludeDialog
        }
    }

    BackHandler(enabled = appBackEnabled) {
        when {
            transferRequest != null -> transferRequest = null
            slideshowQueueOpen -> {
                if (slideshowQueueFolderMode && slideshowQueueOpenedFolder != null) {
                    slideshowQueueOpenedFolder = null
                } else {
                    slideshowQueueOpen = false
                }
            }
            wallpaperManagerOpen && wallpaperSelectionMode -> {
                wallpaperSelectionMode = false
                wallpaperSelectedUris = emptySet()
                wallpaperSelectionOrder = emptyList()
            }
            pixivArchiveOpen && pixivArchiveSession.selectionMode.value -> {
                pixivArchiveSession.selectionMode.value = false
                pixivArchiveSession.selectedUris.value = emptySet()
            }
            // Selection is a nested mode of every page, including the Pixiv
            // archive page. Back must leave that mode before closing its page.
            selectionMode -> clearSelection()
            pixivArchiveOpen -> {
                pixivArchiveOpen = false
                pixivRefreshKey++
            }
            cleanupOpen -> {
                cleanupOpen = false
                pixivRefreshKey++
                if (archiveMediaRefreshPending) {
                    archiveMediaRefreshPending = false
                    scope.launch {
                        library.refresh(library.permissionGranted, scheduleThumbnailOptimization = false)
                    }
                }
            }
            wallpaperManagerOpen -> wallpaperManagerOpen = false
            openedFolder != null -> navigateFolderBack()
            query.isNotBlank() -> suspendSearch()
            favoriteFilter -> favoriteFilter = false
            timelineJumpDate != null -> timelineJumpDate = null
            selectedTab != primaryTab -> returnToPrimaryTab()
            else -> {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastRootBackAt <= 2_000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastRootBackAt = now
                    Toast.makeText(
                        context,
                        if (english) "Press back again to exit" else "再按一次返回退出应用",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        externalDeleteRequestInFlight = false
        if (result.resultCode == Activity.RESULT_OK) {
            library.remove(pendingDeletes)
            val deletedUris = pendingDeletes.mapTo(hashSetOf()) { it.uri.toString() }
            pixivArchiveSession.removeRecords(deletedUris intersect pixivArchivePendingDeleteUris)
            pixivArchivePendingDeleteUris -= deletedUris
            if (pendingDeletes.any { it.uri == selectedMedia?.uri }) selectedMedia = null
            requestPageRestoreAfterDelete()
            selectedUris = emptySet()
            selectionMode = false
        } else if (pendingRecycleIds.isNotEmpty()) {
            library.discardRecycle(pendingRecycleIds)
        }
        pixivArchivePendingDeleteUris = emptySet()
        pendingDeletes = emptyList()
        pendingRecycleIds = emptySet()
    }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        externalDeleteRequestInFlight = false
        if (result.resultCode == Activity.RESULT_OK) {
            library.remove(pendingDeletes)
            val deletedUris = pendingDeletes.mapTo(hashSetOf()) { it.uri.toString() }
            pixivArchiveSession.records.value = pixivArchiveSession.records.value.filterNot {
                it.uri.toString() in deletedUris && it.uri.toString() in pixivArchivePendingDeleteUris
            }
            pixivArchivePendingDeleteUris -= deletedUris
            if (pendingDeletes.any { it.uri == selectedMedia?.uri }) selectedMedia = null
            requestPageRestoreAfterDelete()
            selectedUris = emptySet()
            selectionMode = false
        } else if (pendingRecycleIds.isNotEmpty()) {
            library.discardRecycle(pendingRecycleIds)
        }
        pixivArchivePendingDeleteUris = emptySet()
        pendingDeletes = emptyList()
        pendingRecycleIds = emptySet()
    }
    val trashRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val restoring = pendingTrashRestore
        if (result.resultCode == Activity.RESULT_OK) {
            library.removeRecycleRecords(restoring)
            scope.launch { library.refresh(library.permissionGranted) }
            Toast.makeText(context, if (english) "Restored ${restoring.size} items" else "已还原 ${restoring.size} 项", Toast.LENGTH_SHORT).show()
        }
        pendingTrashRestore = emptyList()
    }
    val trashDeleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val deleting = pendingTrashDelete
        if (result.resultCode == Activity.RESULT_OK) {
            library.removeRecycleRecords(deleting)
            Toast.makeText(context, if (english) "Permanently deleted ${deleting.size} items" else "已彻底删除 ${deleting.size} 项", Toast.LENGTH_SHORT).show()
        }
        pendingTrashDelete = emptyList()
    }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val rename = pendingRename
        if (result.resultCode == Activity.RESULT_OK && rename != null) {
            scope.launch {
                val renamed = library.rename(rename.first, rename.second)
                if (renamed != null) {
                    openMedia(renamed)
                    val oldKey = rename.first.uri.toString()
                    if (oldKey in favoriteUris) {
                        favoriteUris = favoriteUris - oldKey + renamed.uri.toString()
                        preferences.edit().putStringSet("favorites", favoriteUris).apply()
                    }
                } else {
                    Toast.makeText(context, appText("重命名失败", english), Toast.LENGTH_SHORT).show()
                }
            }
        }
        pendingRename = null
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = hasMediaPermission(context)
        scope.launch { library.refresh(granted) }
        if (
            granted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !MediaStore.canManageMedia(context) &&
            !albumSettings.getBoolean("media_management_prompted", false)
        ) {
            albumSettings.edit().putBoolean("media_management_prompted", true).apply()
            val packageUri = Uri.parse("package:${context.packageName}")
            runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, packageUri))
            }.recoverCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
            }
        }
    }
    val requestPermission: () -> Unit = {
        val missing = missingPermissions(context, requiredMediaPermissions())
        if (missing.isEmpty()) {
            scope.launch { library.refresh(true) }
        } else {
            permissionLauncher.launch(missing)
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val flags = FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
            true
        }.getOrElse {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
                true
            }.getOrDefault(false)
        }
        if (!persisted) {
            Toast.makeText(
                context,
                if (english) "Folder access could not be saved; please choose it again" else "无法保存文件夹访问权限，请重新选择",
                Toast.LENGTH_LONG
            ).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            library.addLocalFolder(uri)
            Toast.makeText(context, if (english) "Local folder added" else "已添加本地文件夹", Toast.LENGTH_SHORT).show()
        }
    }
    suspend fun performDelete(deleting: List<MediaItem>) {
        if (deleting.isEmpty() || externalDeleteRequestInFlight) {
            return
        }
            val recycleEnabled = albumSettings.getBoolean("recycle_bin", true)
            // Keep the app's recycle bin authoritative. Using MediaStore's
            // system Trash here makes the persisted recycle records depend on
            // OEM-specific Trash URI and permission behavior, which can make
            // items disappear from this screen or fail to restore.
            val staged = when {
                recycleEnabled -> library.stageForRecycle(deleting)
                else -> emptyList()
            }
            val stagedSources = staged.mapTo(mutableSetOf()) { it.sourceUri }
            val deletableSources = resolveDeletionSources(deleting.map { it.uri.toString() }, stagedSources, recycleEnabled)
            val deletable = deleting.filter { it.uri.toString() in deletableSources }
            val unstagedCount = deleting.size - deletable.size
            if (unstagedCount > 0) {
                Toast.makeText(context, if (english) "$unstagedCount items could not be backed up; originals were kept" else "$unstagedCount 项无法备份，已保留原文件", Toast.LENGTH_LONG).show()
            }
            if (deletable.isEmpty()) {
                pixivArchivePendingDeleteUris = emptySet()
                return
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                deletable.none { it.isDocument } &&
                !canModifyMediaDirectly(context)
            ) {
                pendingDeletes = deletable
                pendingRecycleIds = staged.mapTo(mutableSetOf()) { it.id }
                runCatching {
                    val request = MediaStore.createDeleteRequest(context.contentResolver, deletable.map { it.uri })
                    externalDeleteRequestInFlight = true
                    deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }.onFailure {
                    externalDeleteRequestInFlight = false
                    library.discardRecycle(pendingRecycleIds)
                    pendingDeletes = emptyList()
                    pendingRecycleIds = emptySet()
                    pixivArchivePendingDeleteUris = emptySet()
                    Toast.makeText(
                        context,
                        if (english) "Unable to request deletion; originals were kept" else "无法请求系统删除，已保留原文件",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                val failedUris = mutableSetOf<String>()
                val deletedMedia = deletable.mapNotNull { media ->
                    if (library.deleteLegacy(media)) media else {
                        failedUris += media.uri.toString()
                        null
                    }
                }
                library.remove(deletedMedia)
                val deletedUris = deletable.mapTo(hashSetOf()) { it.uri.toString() } - failedUris
                if (deletedUris.isNotEmpty()) {
                    Toast.makeText(
                        context,
                        if (recycleEnabled) {
                            if (english) "Moved ${deletedUris.size} items to Trash" else "已将 ${deletedUris.size} 项移到回收站"
                        } else {
                            if (english) "Permanently deleted ${deletedUris.size} items" else "已彻底删除 ${deletedUris.size} 项"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
                pixivArchiveSession.records.value = pixivArchiveSession.records.value.filterNot {
                    it.uri.toString() in deletedUris && it.uri.toString() in pixivArchivePendingDeleteUris
                }
                pixivArchivePendingDeleteUris -= deletedUris
                val failedEntries = staged.filter { it.sourceUri in failedUris }.mapTo(mutableSetOf()) { it.id }
                if (failedEntries.isNotEmpty()) library.discardRecycle(failedEntries)
                if (deletable.any { it.uri == selectedMedia?.uri && it.uri.toString() !in failedUris }) selectedMedia = null
                requestPageRestoreAfterDelete()
                selectedUris = emptySet()
                selectionMode = false
                pixivArchivePendingDeleteUris = emptySet()
            }
    }
    val requestDelete: (List<MediaItem>) -> Unit = { deleting ->
        scope.launch { performDelete(deleting) }
    }
    val requestDeleteWithConfirmation: (List<MediaItem>) -> Unit = { deleting ->
        if (deleting.isNotEmpty()) {
            if (albumSettings.getBoolean("delete_confirmation", true)) pendingAppDelete = deleting
            else requestDelete(deleting)
        }
    }

    val requestMediaScan: (Boolean) -> Unit = { showResult ->
        if (!library.scanning) scope.launch {
            when (val result = library.scanAndRefresh(library.permissionGranted, userInitiated = true)) {
                is MediaScanResult.Completed -> if (showResult) Toast.makeText(
                    context,
                    if (english) "Media scan complete: ${result.scannedFiles} files processed"
                    else "媒体扫描完成，已处理 ${result.scannedFiles} 个文件",
                    Toast.LENGTH_SHORT
                ).show()
                is MediaScanResult.Failed -> Toast.makeText(
                    context,
                    if (english) "Media scan failed: ${result.reason ?: "unknown error"}"
                    else "媒体扫描失败：${result.reason ?: "未知错误"}",
                    Toast.LENGTH_LONG
                ).show()
                MediaScanResult.PermissionRequired -> requestPermission()
                MediaScanResult.NotRequested -> Unit
            }
        }
    }

    // Pull to refresh only re-reads MediaStore. The full storage walk behind
    // "重新扫描" touches every file in DCIM/Pictures/Movies/Downloads and made
    // every pull feel slow even when nothing had changed; the system media
    // provider already indexes new files, so the walk stays on the menu entry.
    val refreshLibrary: () -> Unit = {
        scope.launch {
            library.refresh(library.permissionGranted)
            if (selectedTab == MainTab.Pixiv) pixivRefreshKey++
        }
    }

    LaunchedEffect(library.permissionGranted) {
        val granted = hasMediaPermission(context)
        library.refresh(granted)
        val initialPermissionsPrompted = albumSettings.getBoolean("runtime_permissions_prompted", false)
        val missingInitialPermissions = missingPermissions(context, requiredAppPermissions())
        if (!initialPermissionsPrompted && missingInitialPermissions.isNotEmpty()) {
            albumSettings.edit().putBoolean("runtime_permissions_prompted", true).apply()
            permissionLauncher.launch(missingInitialPermissions)
        } else if (
            granted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager() &&
            !albumSettings.getBoolean("all_files_access_prompted", false)
        ) {
            albumSettings.edit().putBoolean("all_files_access_prompted", true).apply()
            val packageUri = Uri.parse("package:${context.packageName}")
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri))
            }.recoverCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else if (
            granted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !MediaStore.canManageMedia(context) &&
            !albumSettings.getBoolean("media_management_prompted", false)
        ) {
            albumSettings.edit().putBoolean("media_management_prompted", true).apply()
            val packageUri = Uri.parse("package:${context.packageName}")
            runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, packageUri))
            }.recoverCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
            }
        }
    }
    LaunchedEffect(library.refreshError) {
        library.refreshError?.let { message ->
            Toast.makeText(
                context,
                if (english) "Some media could not be read: $message" else message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    DisposableEffect(library.permissionGranted) {
        if (!library.permissionGranted) {
            return@DisposableEffect onDispose { }
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // Pixiv archiving copies/deletes each file and can emit a
                // MediaStore callback per operation. Refresh once after the
                // batch instead of starting a full reload for every file.
                if (pixivArchiveSession.state.value == ArchiveUiState.Archiving ||
                    android.os.SystemClock.uptimeMillis() < latestSuppressObserverRefreshUntil
                ) {
                    observerRefreshJob[0]?.cancel()
                    return
                }
                observerRefreshJob[0]?.cancel()
                observerRefreshJob[0] = scope.launch {
                    delay(400L)
                    library.refresh(true)
                }
            }
        }
        context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        context.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        onDispose {
            observerRefreshJob[0]?.cancel()
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    transferRequest?.let { request ->
        LaunchedEffect(Unit) {
            library.loadSearchableFolderNames()
        }
        val transferMedia = (library.images + library.videos + library.localImages + library.localVideos)
            .distinctBy { it.uri.toString() }
        // BUCKET_DISPLAY_NAME is only a leaf name. Use the full relative path
        // here so moving into a nested folder cannot fall back to Pictures/<leaf>.
        val mediaFolders = transferMedia.map { it.transferFolderPath() }
        val searchableTransferFolders = library.searchableFolderNames +
            library.searchableFolderChildren.values.flatten()
        val folderCovers = transferMedia
            .groupBy { it.transferFolderPath() }
            .mapValues { (_, media) -> media.firstOrNull() }
            .filterValues { it != null }
            .mapValues { (_, item) -> item!! }
        val folderItems = transferMedia.groupBy { it.transferFolderPath() }
        val recentFolders = transferPreferences.getString("recent_folders", "").orEmpty()
            .split('\u001f').filter { it.isNotBlank() }.take(8)
        DestinationScreen(
            mode = request.mode,
            itemCount = request.items.size,
            items = request.items,
            folders = (mediaFolders + searchableTransferFolders).distinct(),
            folderCovers = folderCovers,
            folderItems = folderItems,
            folderChildren = library.searchableFolderChildren,
            searchingFolders = library.searchableFoldersLoading || !library.searchableFoldersReady,
            recentFolders = recentFolders,
            validateFolder = library::isTransferFolderAvailable,
            defaultConflictPolicy = when (albumSettings.getString("conflict", "保留两者")) {
                "覆盖" -> com.example.album.data.ConflictPolicy.Overwrite
                "跳过" -> com.example.album.data.ConflictPolicy.Skip
                else -> com.example.album.data.ConflictPolicy.KeepBoth
            },
            defaultPreserveDate = albumSettings.getBoolean("preserve_date", true),
            onBack = { transferRequest = null },
            onCreateFolder = { parent, name ->
                library.createTransferFolder(parent, request.items, name)
            },
            onConfirm = { destination, policy, preserveDate ->
                // Leave the destination screen immediately. The transfer and
                // any follow-up refresh/delete work continue in this scope.
                transferRequest = null
                val transferErrorHandler = CoroutineExceptionHandler { _, error ->
                    transferRequest = null
                    pixivArchiveMoveUris = emptySet()
                    Toast.makeText(
                        context,
                        error.message ?: if (english) "Transfer failed" else "移动或复制失败，源文件已保留",
                        Toast.LENGTH_LONG
                    ).show()
                }
                scope.launch(transferErrorHandler) {
                    // Let Compose commit the destination-page exit before
                    // starting transfer work and its media refresh.
                    yield()
                    val archiveMoveUris = pixivArchiveMoveUris
                    val results = library.transfer(request.items, destination, policy, preserveDate, request.mode)
                    val completed = results.filter { it.success && !it.skipped }
                    val completedItems = completed.map { it.item }
                    val skipped = results.count { it.skipped }
                    val failed = results.count { !it.success }
                    val updatedRecent = (listOf(destination) + recentFolders).distinct().take(8)
                    transferPreferences.edit().putString("recent_folders", updatedRecent.joinToString("\u001f")).apply()
                    library.refresh(library.permissionGranted, scheduleThumbnailOptimization = false)

                    if (completed.isNotEmpty()) {
                        // Folder entries are displayed by leaf name in the
                        // album grid, while the destination picker may carry
                        // a parent path such as "Pictures/Wallpapers".
                        openedFolder = destination.substringAfterLast('/')
                            .trim()
                            .takeIf { it.isNotBlank() }
                        selectedTab = when {
                            request.items.isNotEmpty() && request.items.all { it.isVideo } -> MainTab.Videos
                            request.items.isNotEmpty() && request.items.all { !it.isVideo } -> MainTab.Albums
                            else -> selectedTab
                        }
                        query = ""
                        suspendedSearchQuery = null
                        searchOpen = true
                        favoriteFilter = false
                    }

                    if (request.mode == TransferMode.Move) {
                        val directlyMovedArchiveUris = completed
                            .filter { it.movedDirectly && it.item.uri.toString() in archiveMoveUris }
                            .mapTo(hashSetOf()) { it.item.uri.toString() }
                        if (directlyMovedArchiveUris.isNotEmpty()) {
                            pixivArchiveSession.removeRecords(directlyMovedArchiveUris)
                            pixivArchiveMoveUris -= directlyMovedArchiveUris
                        }
                    }

                    var sourceDeleteRequestFailed = false
                    var sourceDeletePending = false
                    if (request.mode == TransferMode.Move && completedItems.isNotEmpty()) {
                        val documents = completed.filter { !it.movedDirectly && it.item.isDocument }.map { it.item }
                        val deletedDocuments = withContext(Dispatchers.IO) {
                            documents.mapNotNull { media -> media.takeIf { library.deleteLegacy(it) } }
                        }
                        library.remove(deletedDocuments)
                        if (archiveMoveUris.isNotEmpty()) {
                            val deletedUris = deletedDocuments.mapTo(hashSetOf()) { it.uri.toString() }
                            pixivArchiveSession.removeRecords(deletedUris intersect archiveMoveUris)
                            pixivArchiveMoveUris -= deletedUris
                        }
                        val systemMedia = completed.filter { !it.movedDirectly && !it.item.isDocument }.map { it.item }
                        val deletedSystemMedia = withContext(Dispatchers.IO) {
                            systemMedia.mapNotNull { media -> media.takeIf { library.deleteLegacy(it) } }
                        }
                        library.remove(deletedSystemMedia)
                        if (archiveMoveUris.isNotEmpty()) {
                            val deletedUris = deletedSystemMedia.mapTo(hashSetOf()) { it.uri.toString() }
                            pixivArchiveSession.removeRecords(deletedUris intersect archiveMoveUris)
                            pixivArchiveMoveUris -= deletedUris
                        }
                        val requiresSystemConfirmation = systemMedia.filterNot { media ->
                            deletedSystemMedia.any { it.uri == media.uri }
                        }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        requiresSystemConfirmation.isNotEmpty() &&
                        !canModifyMediaDirectly(context)
                    ) {
                            pendingDeletes = requiresSystemConfirmation
                            pixivArchivePendingDeleteUris = requiresSystemConfirmation
                                .mapNotNull { media -> media.uri.toString().takeIf { it in archiveMoveUris } }
                                .toSet()
                            runCatching {
                                val deleteRequest = MediaStore.createDeleteRequest(context.contentResolver, requiresSystemConfirmation.map { it.uri })
                                externalDeleteRequestInFlight = true
                                deleteLauncher.launch(IntentSenderRequest.Builder(deleteRequest.intentSender).build())
                                sourceDeletePending = true
                            }.onFailure {
                                externalDeleteRequestInFlight = false
                                sourceDeleteRequestFailed = true
                                pendingDeletes = emptyList()
                                Toast.makeText(
                                    context,
                                    if (english) "Copied, but the original files were kept" else "已完成复制，但源文件已保留",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            pixivArchiveMoveUris = emptySet()
                        } else {
                            pixivArchiveMoveUris = emptySet()
                            selectedUris = emptySet()
                            selectionMode = false
                        }
                    } else {
                        selectedUris = emptySet()
                        selectionMode = false
                    }

                    val action = if (english) {
                        if (request.mode == TransferMode.Copy || sourceDeleteRequestFailed) "Copied"
                        else if (sourceDeletePending) "Awaiting source deletion confirmation"
                        else "Moved"
                    } else if (request.mode == TransferMode.Copy || sourceDeleteRequestFailed) "复制"
                    else if (sourceDeletePending) "等待确认删除源文件"
                    else "移动"
                    val done = completed.size
                    val details = buildList {
                        add(if (english) "$action $done items" else "$action $done 项")
                        if (skipped > 0) add(if (english) "Skipped $skipped items" else "跳过 $skipped 项")
                        if (failed > 0) add(if (english) "Failed $failed items" else "失败 $failed 项")
                    }.joinToString(if (english) ", " else "，")
                    Toast.makeText(context, details, Toast.LENGTH_SHORT).show()
                }
            }
        )
        return
    }

    if (pixivArchiveOpen) {
        PixivArchiveScreen(
            session = pixivArchiveSession,
            onStartScan = { source, maxBatchSize ->
                if (pixivArchiveSession.state.value != ArchiveUiState.Scanning) {
                    pixivArchiveSession.beginScan()
                    val intent = Intent(context, PixivArchiveScanService::class.java).apply {
                        action = PixivArchiveScanService.ACTION_SCAN
                        putExtra(PixivArchiveScanService.EXTRA_SOURCE_URI, source.toString())
                        putExtra(PixivArchiveScanService.EXTRA_MAX_BATCH, maxBatchSize)
                    }
                    runCatching { ContextCompat.startForegroundService(context, intent) }
                        .onFailure { error ->
                            pixivArchiveSession.setScanState(ArchiveUiState.Error)
                            pixivArchiveSession.activity.value = pixivArchiveSession.activity.value.copy(
                                phase = PixivArchivePhase.Error,
                                message = error.message ?: "无法启动后台扫描"
                            )
                        }
                }
            },
            onCancelScan = {
                pixivArchiveSession.cancelScan()
                context.startService(
                    Intent(context, PixivArchiveScanService::class.java).apply {
                        action = PixivArchiveScanService.ACTION_CANCEL
                    }
                )
            },
            onClearScan = { pixivArchiveSession.clearScanResults() },
            onBack = {
                pixivArchiveSession.selectionMode.value = false
                pixivArchiveSession.selectedUris.value = emptySet()
                pixivArchiveOpen = false
                pixivRefreshKey++
            },
            onArchiveComplete = { completed, failed ->
                observerRefreshJob[0]?.cancel()
                archiveMediaRefreshPending = false
                // Re-read the library in the background: the archive page is
                // released straight away, and the P page walk is then started
                // once (the reload requests are debounced) with the refreshed
                // media index already in place.
                scope.launch {
                    try {
                        library.refresh(library.permissionGranted, scheduleThumbnailOptimization = false)
                    } finally {
                        archiveMediaRefreshing = false
                        pixivRefreshKey++
                    }
                }
                Toast.makeText(
                    context,
                    if (failed == 0) {
                        if (english) "Archive complete: $completed items" else "归档完成，共 $completed 项"
                    } else {
                        if (english) "Archive finished: $completed succeeded, $failed failed" else "归档完成：成功 $completed 项，失败 $failed 项"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            },
            archiveMediaRefreshing = archiveMediaRefreshing,
            favoriteSelected = { items -> items.isNotEmpty() && items.all { it.uri.toString() in favoriteUris } },
            onFavorite = { items ->
                val keys = items.mapTo(mutableSetOf()) { it.uri.toString() }
                favoriteUris = if (keys.all { it in favoriteUris }) favoriteUris - keys else favoriteUris + keys
                preferences.edit().putStringSet("favorites", favoriteUris).apply()
            },
            onCopy = { items ->
                if (items.isNotEmpty()) {
                    // The archive page is rendered as a standalone page (the rest
                    // of the UI, including the destination screen, is not
                    // composed while it is open), so leave it first.
                    pixivArchiveOpen = false
                    transferRequest = TransferRequest(items, TransferMode.Copy)
                }
            },
            onRename = { item, newName ->
                val renamed = library.rename(item, newName)
                if (renamed == null) {
                    Toast.makeText(context, appText("重命名失败", english), Toast.LENGTH_SHORT).show()
                }
                if (renamed != null) scope.launch { library.refresh(library.permissionGranted, scheduleThumbnailOptimization = false) }
                renamed?.uri
            },
            onShare = { items ->
                shareMedia(context, items, english)
            },
            onMove = { items ->
                if (items.isNotEmpty()) {
                    pixivArchiveMoveUris = items.mapTo(hashSetOf()) { it.uri.toString() }
                    pixivArchiveOpen = false
                    transferRequest = TransferRequest(items, TransferMode.Move)
                }
            },
            onDelete = { items ->
                pixivArchivePendingDeleteUris = items.mapTo(hashSetOf()) { it.uri.toString() }
                // The delete confirmation lives in the main UI, which is not
                // composed while the archive page is open.
                pixivArchiveOpen = false
                requestDelete(items)
            }
        )
        return
    }

    if (cleanupOpen) {
        CleanupScreen(
            // Duplicate scanning must include authorized local-folder media;
            // moved or archived images can be represented there instead of in
            // the MediaStore image collection.
            media = (library.images + library.localImages).distinctBy { it.uri },
            recycleEntries = library.recycleEntries,
            excludedMedia = library.excludedMedia,
            onBack = {
                cleanupOpen = false
                pixivRefreshKey++
                if (archiveMediaRefreshPending) {
                    archiveMediaRefreshPending = false
                    scope.launch {
                        library.refresh(library.permissionGranted, scheduleThumbnailOptimization = false)
                    }
                }
            },
            findDuplicates = library::findDuplicates,
            confirmMediaDeletion = albumSettings.getBoolean("delete_confirmation", true),
            recycleMediaDeletion = albumSettings.getBoolean("recycle_bin", true),
            onDeleteMedia = { entries -> performDelete(entries) },
            onRestoreRecycle = { entries ->
                val systemEntries = entries.filter { it.systemTrashed }
                val privateEntries = entries.filterNot { it.systemTrashed }
                if (privateEntries.isNotEmpty()) scope.launch {
                    val restored = library.restoreRecycle(privateEntries).size
                    Toast.makeText(context, if (english) "Restored $restored items" else "已还原 $restored 项", Toast.LENGTH_SHORT).show()
                }
                if (systemEntries.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val requestableEntries = systemEntries.filter { entry ->
                        runCatching {
                            MediaStore.createTrashRequest(context.contentResolver, listOf(Uri.parse(entry.sourceUri)), false)
                        }.isSuccess
                    }
                    val unavailableEntries = systemEntries.filterNot { entry -> requestableEntries.any { it.id == entry.id } }
                    if (unavailableEntries.isNotEmpty()) {
                        library.removeRecycleRecords(unavailableEntries)
                        Toast.makeText(
                            context,
                            if (english) "Removed ${unavailableEntries.size} unavailable Trash records" else "已清理 ${unavailableEntries.size} 条失效回收站记录",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    if (requestableEntries.isEmpty()) return@CleanupScreen
                    pendingTrashRestore = requestableEntries
                    runCatching {
                        val request = MediaStore.createTrashRequest(context.contentResolver, requestableEntries.map { Uri.parse(it.sourceUri) }, false)
                        trashRestoreLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }.onFailure {
                        pendingTrashRestore = emptyList()
                        Toast.makeText(context, if (english) "Unable to request system restore" else "无法请求系统还原", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDeleteRecycle = { entries ->
                val systemEntries = entries.filter { it.systemTrashed }
                val privateEntries = entries.filterNot { it.systemTrashed }
                if (privateEntries.isNotEmpty()) {
                    scope.launch { library.permanentlyDeleteRecycle(privateEntries) }
                }
                val directlyDeleted = systemEntries.filter { entry ->
                    runCatching {
                        context.contentResolver.delete(Uri.parse(entry.sourceUri), null, null) > 0
                    }.getOrDefault(false)
                }
                if (directlyDeleted.isNotEmpty()) library.removeRecycleRecords(directlyDeleted)
                val remainingEntries = systemEntries.filterNot { entry -> directlyDeleted.any { it.id == entry.id } }
                if (remainingEntries.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val requestableEntries = remainingEntries.filter { entry ->
                        runCatching {
                            MediaStore.createDeleteRequest(context.contentResolver, listOf(Uri.parse(entry.sourceUri)))
                        }.isSuccess
                    }
                    val unavailableEntries = remainingEntries.filterNot { entry -> requestableEntries.any { it.id == entry.id } }
                    if (unavailableEntries.isNotEmpty()) {
                        library.removeRecycleRecords(unavailableEntries)
                        Toast.makeText(
                            context,
                            if (english) "Removed ${unavailableEntries.size} unavailable Trash records" else "已清理 ${unavailableEntries.size} 条失效回收站记录",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    if (requestableEntries.isEmpty()) return@CleanupScreen
                    pendingTrashDelete = requestableEntries
                    runCatching {
                        val request = MediaStore.createDeleteRequest(context.contentResolver, requestableEntries.map { Uri.parse(it.sourceUri) })
                        trashDeleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }.onFailure {
                        pendingTrashDelete = emptyList()
                        Toast.makeText(context, if (english) "Unable to request permanent deletion" else "无法请求彻底删除", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onRestoreExcluded = library::restoreExcludedFolder,
            onOpenMedia = ::openMedia,
            onOpenExcludedFolder = { folder, isVideo ->
                cleanupOpen = false
                albumShowsVideos = isVideo
                selectedTab = MainTab.Albums
                openedFolder = folder
                folderScope = null
                query = ""
                searchOpen = false
            }
        )
        return
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalMediaSharedTransitionScope provides this@SharedTransitionLayout,
                LocalActiveSharedMediaKey provides activeSharedMediaKey
            ) {
                Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
            if (slideshowQueueOpen) VaultTopBar(
                title = appText("幻灯片队列", english),
                query = "",
                searchEnabled = false,
                onQueryChange = {},
                favoriteActive = false,
                favoriteVisible = false,
                onFavoriteClick = {},
                menuItems = listOf(
                    appText("设置", english),
                    appText("列数", english),
                    appText("排布方式", english),
                    appText("排序方式", english),
                    appText("清空幻灯片队列", english)
                ),
                onMenuItemClick = { action ->
                    when (action) {
                        appText("设置", english) -> showSlideshowSettings = true
                        appText("列数", english) -> showSlideshowColumnDialog = true
                        appText("排布方式", english) -> showSlideshowLayoutDialog = true
                        appText("排序方式", english) -> showSlideshowSortDialog = true
                        appText("清空幻灯片队列", english) -> {
                            slideshowQueueUris = emptySet()
                            slideshowQueueOrder = emptyList()
                            persistSlideshowQueue(emptySet(), emptyList())
                        }
                    }
                },
                onBack = {
                    if (slideshowQueueFolderMode && slideshowQueueOpenedFolder != null) {
                        slideshowQueueOpenedFolder = null
                    } else {
                        slideshowQueueOpen = false
                    }
                },
                backLabel = if (slideshowQueueFolderMode && slideshowQueueOpenedFolder != null) {
                    appText("返回队列", english)
                } else {
                    appText("返回工具箱", english)
                },
                actionLabel = appText("播放", english),
                actionEnabled = slideshowQueueMedia.isNotEmpty(),
                actionCapsule = true,
                actionStartPadding = 10.dp,
                onActionClick = {
                    val items = slideshowQueueSorted.filterNot { it.isVideo }
                    if (items.isNotEmpty()) {
                        selectionSlideshow = items
                        // Play through the normal image viewer so the
                        // full-screen page, preview controls and editing all
                        // behave exactly like a normal photo.
                        viewerScope = items
                        viewerMedia = items.first()
                        selectedMedia = items.first()
                        slideshowStartImmersive = true
                        // The queue page stays open behind the slideshow: the
                        // back gesture has to return to the queue, not to the
                        // Tools page it was started from.
                    }
                },
                chromeAlpha = pageChromeAlpha
            ) else if (wallpaperManagerOpen) VaultTopBar(
                title = if (english) "Wallpaper manager" else "壁纸管理",
                query = "",
                // The manager only manages the queue now: media is added from
                // the multi-select menu, so the search field is gone.
                searchEnabled = false,
                onQueryChange = {},
                favoriteActive = false,
                onFavoriteClick = {},
                menuItems = listOf(
                    appText("设置", english),
                    appText("列数", english),
                    appText("排布方式", english),
                    appText("排序方式", english),
                    appText("清空壁纸队列", english)
                ),
                onMenuItemClick = { action ->
                    when (action) {
                        appText("设置", english) -> showWallpaperSettings = true
                        appText("列数", english) -> showWallpaperColumnDialog = true
                        appText("排布方式", english) -> showWallpaperLayoutDialog = true
                        appText("排序方式", english) -> showWallpaperSortDialog = true
                        appText("清空壁纸队列", english) -> {
                            val currentTypeUris = wallpaperQueueMedia
                                .filter { it.isVideo == wallpaperShowVideos }
                                .mapTo(mutableSetOf()) { it.uri.toString() }
                            val updatedUris = wallpaperQueueUris - currentTypeUris
                            val updatedOrder = wallpaperQueueOrder.filterNot { it in currentTypeUris }
                            wallpaperQueueUris = updatedUris
                            wallpaperQueueOrder = updatedOrder
                            persistWallpaperQueue(updatedUris, updatedOrder)
                        }
                    }
                },
                onBack = {
                    wallpaperManagerOpen = false
                    wallpaperQuery = ""
                    wallpaperSelectionMode = false
                    wallpaperSelectedUris = emptySet()
                    wallpaperSelectionOrder = emptyList()
                },
                backLabel = appText("返回工具箱", english),
                // Static/live moved to the title switch, matching the other
                // pages now that the search field is gone.
                searchModeLabels = emptyList(),
                selectedSearchMode = if (wallpaperShowVideos) 1 else 0,
                // Inside a queue folder the bar shows the folder name instead.
                titleSwitch = if (wallpaperOpenedFolder == null) {
                    listOf(
                        if (english) "Static" else "静态",
                        if (english) "Live" else "动态"
                    )
                } else null,
                onTitleSwitchChange = {
                    wallpaperShowVideos = it == 1
                    wallpaperSelectedUris = emptySet()
                    wallpaperSelectionOrder = emptyList()
                },
                actionLabel = if (wallpaperImportRunning) {
                    val progress = wallpaperImportState as? WallpaperImportState.Running
                    if (progress == null) appText("中止", english)
                    else if (english) "Cancel ${progress.completed}/${progress.total}" else "中止 ${progress.completed}/${progress.total}"
                } else when {
                    // "清除" collided with "清除搜索" elsewhere; this action
                    // only takes the selected items out of the queue.
                    wallpaperSelectionMode -> appText("移出队列", english)
                    wallpaperQueueIsApplied -> appText("重新应用", english)
                    else -> appText("应用", english)
                },
                actionDestructive = wallpaperSelectionMode,
                actionEnabled = if (wallpaperImportRunning) true else if (wallpaperSelectionMode) {
                    wallpaperSelectedUris.isNotEmpty()
                } else true,
                actionCapsule = true,
                actionStartPadding = 10.dp,
                onActionClick = {
                    if (wallpaperImportRunning) {
                        WallpaperImportCoordinator.cancel()
                    } else if (wallpaperSelectionMode) {
                        wallpaperSelectedUris.forEach { uri ->
                            wallpaperQueueMedia.firstOrNull { it.uri.toString() == uri }?.let(::removeFromWallpaperQueue)
                        }
                        wallpaperSelectionMode = false
                        wallpaperSelectedUris = emptySet()
                        wallpaperSelectionOrder = emptyList()
                    } else {
                        val currentItems = wallpaperApplyItems
                        if (wallpaperShowVideos) {
                            setDynamicWallpaper(context, currentItems, english)
                        } else {
                            setStaticWallpaper(context, currentItems, english)
                        }
                    }
                },
                chromeAlpha = pageChromeAlpha
            ) else if (selectionMode || selectionGestureActive) SelectionTopBar(
                selected = if (selectingFolders) selectedFolders.size else selectedItemsForAction.size,
                selectingFolders = selectingFolders,
                onClose = { clearSelection() },
                query = query,
                onQueryChange = { query = it },
                favoriteSelected = selectedItemsForAction.isNotEmpty() && selectedItemsForAction.all { it.uri.toString() in favoriteUris },
                onFavorite = {
                    val selectedKeys = selectedItemsForAction.mapTo(mutableSetOf()) { it.uri.toString() }
                    favoriteUris = if (selectedKeys.all { it in favoriteUris }) favoriteUris - selectedKeys else favoriteUris + selectedKeys
                    preferences.edit().putStringSet("favorites", favoriteUris).apply()
                },
                onCopy = {
                    if (selectedItemsForAction.isNotEmpty()) transferRequest = TransferRequest(selectedItemsForAction, TransferMode.Copy)
                },
                onMove = {
                    if (selectedItemsForAction.isNotEmpty()) transferRequest = TransferRequest(selectedItemsForAction, TransferMode.Move)
                },
                onRename = {
                    if (selectingFolders) {
                        selectionRenameFolder = selectedFolders.singleOrNull()
                        selectionRenameText = selectionRenameFolder.orEmpty()
                    } else selectedItemsForAction.singleOrNull()?.let {
                        selectionRenameItem = it
                        selectionRenameText = it.name
                    }
                },
                renameEnabled = !selectingFolders || selectedFolders.size == 1,
                onShare = {
                    shareMedia(context, selectedItemsForAction, english)
                },
                onDelete = {
                    requestDeleteWithConfirmation(selectedItemsForAction)
                },
                onAddToSlideshowQueue = selectedItemsForAction.takeIf { items -> items.any { !it.isVideo } }?.let {{
                    addToSlideshowQueue(selectedItemsForAction.filterNot { it.isVideo })
                    selectionMode = false
                    selectingFolders = false
                }},
                onOpenWith = selectedItemsForAction.singleOrNull()?.takeIf { !selectingFolders }?.let { selected -> {
                    openMediaWith(context, selected, english)
                }},
                onInfo = selectedItemsForAction.singleOrNull()?.takeIf { !selectingFolders }?.let { selected -> { selectionInfoItem = selected }},
                onEditTags = selectedItemsForAction.singleOrNull()?.takeIf { !selectingFolders && !it.isVideo }?.let { selected -> { openTagEditor(selected) }},
                onEdit = selectedItemsForAction.singleOrNull()?.takeIf { !selectingFolders && !it.isVideo }?.let { selected -> {
                    selectionMode = false
                    beginEditing(selected)
                }},
                onWallpaper = selectedItemsForAction.singleOrNull()?.takeIf { !selectingFolders }?.let { selected -> {
                    requestWallpaper(selected)
                }},
                onAddToWallpaperQueue = if (selectedItemsForAction.isNotEmpty()) {
                    {
                        addToWallpaperQueue(selectedItemsForAction)
                        clearSelection()
                    }
                } else null,
                onExclude = if (selectingFolders && selectedFolders.isNotEmpty()) {{
                    selectedFolders.forEach(library::excludeFolder)
                    Toast.makeText(context, if (english) "Excluded ${selectedFolders.size} folders" else "已排除 ${selectedFolders.size} 个文件夹", Toast.LENGTH_SHORT).show()
                    selectionMode = false
                    selectingFolders = false
                    selectedFolders = emptySet()
                }} else null,
                chromeAlpha = pageChromeAlpha
            ) else AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    val direction = if (tabOrder.indexOf(targetState) >= tabOrder.indexOf(initialState)) 1 else -1
                    (
                        fadeIn(tween(190, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))) +
                            slideInHorizontally(tween(240, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))) { width -> direction * width * 24 / 100 }
                        ) togetherWith (
                        fadeOut(tween(170, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))) +
                            slideOutHorizontally(tween(240, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))) { width -> -direction * width * 24 / 100 }
                        ) using SizeTransform(clip = false)
                },
                contentKey = { it },
                label = "main-tab-topbar-transition"
            ) { tab ->
                // The Tools and Settings pages have no search field, no page
                // switch and no menu, so their top bar carried nothing: the
                // Tools page drops it entirely.
                if (tab != MainTab.Settings && tab != MainTab.Tools) VaultTopBar(
                title = if (openedFolder != null) {
                    openedFolder.orEmpty()
                } else if (tab == MainTab.Timeline) {
                    if (appLanguage == "English") "Timeline" else "时间轴"
                } else tabLabel(tab),
                query = query,
                searchEnabled = searchOpen || openedFolder == null,
                onSearchClick = { resumeSearch() },
                onSearchFocus = { resumeSearch() },
                onQueryChange = {
                    suspendedSearchQuery = null
                    searchOpen = true
                    if (query.isBlank() && it.isNotBlank()) {
                        // Remember where the page was before the search so
                        // leaving the search returns there.
                        searchReturnAlbumFirstVisibleItem = albumFirstVisibleItem
                        searchReturnAlbumFirstVisibleOffset = albumFirstVisibleOffset
                        searchReturnAnchorFolder = selectionAnchorFolder
                    }
                    query = it
                    if (tab == MainTab.Pixiv && it.isNotBlank()) {
                        openedFolder = null
                        folderScope = null
                    }
                },
                favoriteActive = favoriteFilter,
                favoriteVisible = selectedTab != MainTab.Tools,
                onFavoriteClick = { favoriteFilter = !favoriteFilter },
                menuItems = when (tab) {
                    MainTab.Albums -> if (appLanguage == "English") {
                        if (openedFolder == null) listOf("Rescan", "Add local folder", "Columns", "Sort", "Select")
                        else listOf("Rescan", "New folder", "Columns", "Layout", "Sort", "Select")
                    } else {
                        if (openedFolder == null) listOf("重新扫描", "添加本地文件夹", "列数", "排序方式", "进入多选")
                        else listOf("重新扫描", "新建文件夹", "列数", "排布方式", "排序方式", "进入多选")
                    }
                    MainTab.Timeline -> if (appLanguage == "English") {
                        listOf("Rescan", "Add local folder", "Jump to date", "Columns", "Layout", "Select")
                    } else listOf("重新扫描", "添加本地文件夹", "跳转日期", "列数", "排布方式", "进入多选")
                    MainTab.Videos -> if (appLanguage == "English") {
                        if (openedFolder == null) listOf("Rescan", "Add local folder", "Columns", "Sort", "Select")
                        else listOf("Rescan", "New folder", "Columns", "Layout", "Sort", "Select")
                    } else {
                        if (openedFolder == null) listOf("重新扫描", "添加本地文件夹", "列数", "排序方式", "进入多选")
                        else listOf("重新扫描", "新建文件夹", "列数", "排布方式", "排序方式", "进入多选")
                    }
                    MainTab.Pixiv -> if (appLanguage == "English") {
                        if (pixivSearchMode == PixivSearchMode.Tag) listOf("Search by artist", "Rescan", "Columns", "Layout", "Sort", "Select", "Notes")
                        else if (openedFolder == null) listOf("Search by tag", "Rescan", "Columns", "Sort", "Select", "Notes")
                        else listOf("Rescan", "New folder", "Columns", "Layout", "Sort", "Select", "Notes")
                    } else if (pixivSearchMode == PixivSearchMode.Tag) {
                        listOf("按画师搜索", "重新扫描", "列数", "排布方式", "排序方式", "进入多选", "注意事项")
                    } else if (openedFolder == null) {
                        listOf("按 Tag 搜索", "重新扫描", "列数", "排序方式", "进入多选", "注意事项")
                    } else listOf("重新扫描", "新建文件夹", "列数", "排布方式", "排序方式", "进入多选", "注意事项")
                    MainTab.Tools -> emptyList()
                    MainTab.Settings -> emptyList()
                },
                onMenuItemClick = { action ->
                    // Explicit alternative to the title switch for the P page's
                    // search mode: the switch is the only other way to change
                    // it, and this path cannot be missed.
                    if (tab == MainTab.Pixiv && (action == "按画师搜索" || action == "按 Tag 搜索" ||
                            action == "Search by artist" || action == "Search by tag")
                    ) {
                        pixivSearchMode = if (pixivSearchMode == PixivSearchMode.Tag) {
                            PixivSearchMode.Artist
                        } else {
                            PixivSearchMode.Tag
                        }
                        openedFolder = null
                        folderScope = null
                        return@VaultTopBar
                    }
                    when (MainMenuAction.fromLabel(action)) {
                        MainMenuAction.Scan -> scope.launch {
                            requestMediaScan(true)
                            if (selectedTab == MainTab.Pixiv) pixivRefreshKey++
                        }
                        MainMenuAction.AddLocalFolder -> folderLauncher.launch(null)
                        MainMenuAction.CreateFolder -> {
                            if (openedFolder != null) {
                                createFolderName = ""
                                showCreateFolderDialog = true
                            }
                        }
                        MainMenuAction.Columns -> showColumnDialog = true
                        MainMenuAction.Layout -> showLayoutDialog = true
                        MainMenuAction.Sort -> showSortDialog = true
                        MainMenuAction.JumpToDate -> showDateDialog = true
                        MainMenuAction.Select -> {
                            // Freeze the order at the moment selection starts.
                            // This is especially important inside a folder,
                            // where the selection screen replaces the folder
                            // content immediately.
                            freezeSelectionSort()
                            selectionMode = true
                            selectingFolders = openedFolder == null && (tab == MainTab.Albums || tab == MainTab.Videos || (tab == MainTab.Pixiv && pixivSearchMode == PixivSearchMode.Artist))
                            val first = currentSelectionMedia.firstOrNull()
                            if (selectingFolders) {
                                selectedFolders = first?.folder?.let(::setOf).orEmpty()
                                selectedUris = emptySet()
                            } else {
                                selectedUris = first?.uri?.toString()?.let(::setOf).orEmpty()
                                selectedFolders = emptySet()
                            }
                        }
                        MainMenuAction.WallpaperManager -> {
                            query = ""
                            suspendedSearchQuery = null
                            searchOpen = false
                            openedFolder = null
                            folderScope = null
                            wallpaperManagerOpen = true
                        }
                        MainMenuAction.ExcludeFolder -> showExcludeDialog = true
                        MainMenuAction.PixivArchiveInfo -> showPixivArchiveInfo = true
                        null -> Toast.makeText(
                            context,
                            if (english) "This action is unavailable" else "该功能暂不可用",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onBack = when {
                    openedFolder != null && tab != MainTab.Timeline -> ::navigateFolderBack
                    searchOpen && query.isNotBlank() -> ::suspendSearch
                    else -> null
                },
                backLabel = when {
                    openedFolder != null && tab != MainTab.Timeline ->
                        appText("返回上级文件夹", english)
                    searchOpen && query.isNotBlank() -> appText("退出搜索", english)
                    else -> null
                },
                searchPlaceholder = if (tab == MainTab.Pixiv) {
                    if (pixivSearchMode == PixivSearchMode.Tag) {
                        if (appLanguage == "English") "Search tags" else "搜索 Tag"
                    } else if (openedFolder == null) {
                        if (appLanguage == "English") "Search Pixiv folders" else "搜索 Pixiv 文件夹"
                    } else if (appLanguage == "English") "Search this Pixiv folder" else "搜索当前 Pixiv 文件夹"
                } else if (openedFolder != null) {
                    if (appLanguage == "English") "Search this folder" else "搜索当前文件夹中的${if (tab == MainTab.Videos) "视频" else "图片"}"
                } else if (tab == MainTab.Timeline) {
                    if (appLanguage == "English") "Search names and dates" else "搜索${if (timelineShowsVideos) "视频" else "图片"}名称、日期"
                } else null,
                // Every page switch lives in the title area (the same mark plus
                // current state as the photo/video pages); none of them sit
                // inside the search field any more.
                searchModeLabels = emptyList(),
                selectedSearchMode = when {
                    tab == MainTab.Timeline -> if (timelineShowsVideos) 1 else 0
                    tab == MainTab.Albums -> if (albumShowsVideos) 1 else 0
                    tab == MainTab.Pixiv -> if (pixivSearchMode == PixivSearchMode.Tag) 1 else 0
                    else -> 0
                },
                titleSwitch = when {
                    // The photo/video switch belongs to the library home page;
                    // inside a folder the bar shows the folder name instead.
                    openedFolder != null -> null
                    tab == MainTab.Albums || tab == MainTab.Timeline -> listOf(
                        if (english) "Pictures" else "图片",
                        if (english) "Videos" else "视频"
                    )
                    // The P page's switch takes the place of its title, the
                    // same way the photo/video switch does on the library.
                    tab == MainTab.Pixiv -> listOf(
                        if (english) "Artist" else "画师",
                        "Tag"
                    )
                    else -> null
                },
                // Only the P page keeps its switch while searching; on the
                // library pages the switch belongs to the home view and used to
                // reappear next to the search's back arrow.
                titleSwitchWhileSearching = tab == MainTab.Pixiv,
                onTitleSwitchChange = { index ->
                    if (tab == MainTab.Albums) {
                        val showVideos = index == 1
                        if (albumShowsVideos != showVideos) {
                            albumShowsVideos = showVideos
                            query = ""
                            openedFolder = null
                            appliedQuery = ""
                        }
                    } else if (tab == MainTab.Timeline) {
                        val showVideos = index == 1
                        if (timelineShowsVideos != showVideos) {
                            timelineShowsVideos = showVideos
                            query = ""
                            timelineJumpDate = null
                        }
                    } else if (tab == MainTab.Pixiv) {
                        val mode = if (index == 0) PixivSearchMode.Artist else PixivSearchMode.Tag
                        if (mode != pixivSearchMode) {
                            pixivSearchMode = mode
                            // Leave the folder the previous mode was showing:
                            // inside a folder the bar shows the folder name and
                            // the switch disappears with it, so the mode could
                            // not be changed back.
                            openedFolder = null
                            folderScope = null
                        }
                    }
                },
                onSearchModeChange = { index ->
                    if (tab == MainTab.Timeline) {
                        val showVideos = index == 1
                        if (timelineShowsVideos != showVideos) {
                            timelineShowsVideos = showVideos
                            query = ""
                            timelineJumpDate = null
                        }
                    } else {
                        val mode = if (index == 0) PixivSearchMode.Artist else PixivSearchMode.Tag
                        if (mode != pixivSearchMode) pixivSearchMode = mode
                    }
                },
                onTitleClick = null,
                chromeAlpha = pageChromeAlpha
                )
            }
        },
        bottomBar = {
            androidx.compose.foundation.layout.Column {
                if (selectionMode) SelectionSubBar(
                    selected = if (selectingFolders) selectedFolders.size else selectedItemsForAction.size,
                    total = if (selectingFolders) selectionMedia.map { it.folder }.distinct().size else selectionMedia.size,
                    onSelectAll = {
                        if (selectingFolders) {
                            val all = selectionMedia.mapTo(mutableSetOf()) { it.folder }
                            selectedFolders = if (all.isNotEmpty() && all.all { it in selectedFolders }) {
                                selectedFolders - all
                            } else {
                                selectedFolders + all
                            }
                        } else {
                            val visibleKeys = selectionMedia.mapTo(mutableSetOf()) { it.uri.toString() }
                            selectedUris = if (visibleKeys.isNotEmpty() && visibleKeys.all { it in selectedUris }) {
                                selectedUris - visibleKeys
                            } else {
                                selectedUris + visibleKeys
                            }
                        }
                    },
                    chromeAlpha = pageChromeAlpha
                )
            AnimatedVisibility(
                visible = openedFolder == null && !wallpaperManagerOpen,
                enter = slideInVertically(tween(170, easing = CubicBezierEasing(.22f, .8f, .28f, 1f))) { it } + fadeIn(tween(120)),
                // Leaving the page hides the bar at once: the slide-out used to
                // still be running while the folder page was already visible.
                exit = ExitTransition.None
            ) {
            Surface(
                modifier = Modifier.alpha(pageChromeAlpha),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(VaultDimens.BottomBarHeight)
                        .onSizeChanged { bottomBarWidth = it.width }
                ) {
                val bottomTabs = enabledMainTabs(pixivTabEnabled)
                val bottomTabWidth = bottomBarWidth.toFloat() / bottomTabs.size.coerceAtLeast(1)
                val navDragThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
                bottomTabs.forEach { tab -> key(tab) {
                    val selected = selectedTab == tab
                    val slot = tabOrder.indexOf(tab).coerceAtLeast(0)
                    val slotX by animateFloatAsState(
                        targetValue = slot * bottomTabWidth,
                        animationSpec = tween(180, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
                        label = "nav-slot-${tab.name}"
                    )
                    val sourceAlpha by animateFloatAsState(
                        targetValue = if (draggedNavTab == tab) .25f else 1f,
                        animationSpec = tween(120),
                        label = "nav-drag-alpha-${tab.name}"
                    )
                    val navTint by animateColorAsState(
                        if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(180),
                        label = "nav-tint-${tab.name}"
                    )
                    val iconOffset by animateDpAsState(if (selected) 0.dp else 6.dp, tween(240, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)), label = "nav-offset-${tab.name}")
                    val labelAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(140), label = "nav-label-${tab.name}")
                    Column(
                        modifier = Modifier.fillMaxWidth(1f / bottomTabs.size).fillMaxHeight()
                            .graphicsLayer {
                                translationX = slotX
                                alpha = sourceAlpha
                            }
                            .pointerInput(tab, navReorderEnabled, bottomTabWidth) {
                                if (!navReorderEnabled || bottomTabWidth <= 0) return@pointerInput
                                var horizontalDragDistance = 0f
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { position ->
                                        val index = currentTabOrder.indexOf(tab).coerceAtLeast(0)
                                        draggedNavTab = tab
                                        navDragX = index * bottomTabWidth + position.x
                                        navDragY = position.y
                                        horizontalDragDistance = 0f
                                        suppressNavClickUntil = android.os.SystemClock.elapsedRealtime() + 400L
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        horizontalDragDistance += kotlin.math.abs(amount.x)
                                        navDragX = (navDragX + amount.x).coerceIn(0f, bottomBarWidth.toFloat())
                                        navDragY += amount.y
                                        // Do not reorder on the small involuntary movement that often
                                        // follows a long press. Reordering starts only after a real drag.
                                        if (horizontalDragDistance < navDragThresholdPx) return@detectDragGesturesAfterLongPress
                                        val order = currentTabOrder
                                        val from = order.indexOf(tab)
                                        val to = (navDragX / bottomTabWidth).toInt().coerceIn(order.indices)
                                        if (from >= 0 && from != to) {
                                            val reordered = order.toMutableList().apply {
                                                val moved = removeAt(from)
                                                add(to, moved)
                                            }
                                             tabOrder = reordered
                                             albumSettings.edit().putString("nav_order", reordered.joinToString(",") { it.name }).apply()
                                         }
                                     },
                                    onDragEnd = { draggedNavTab = null },
                                    onDragCancel = { draggedNavTab = null }
                                )
                            }
                            .clickable(
                                interactionSource = remember(tab) { MutableInteractionSource() },
                                indication = null
                            ) {
                            if (android.os.SystemClock.elapsedRealtime() < suppressNavClickUntil) return@clickable
                            if (tab != selectedTab) {
                                selectMainTab(tab)
                                return@clickable
                            }
                            val atTop = when (tab) {
                                MainTab.Timeline -> timelinePageAtTop
                                MainTab.Albums, MainTab.Videos, MainTab.Pixiv -> albumPageAtTop
                                else -> true
                            }
                            if (!atTop) {
                                // Reset the stored position as well: any grid
                                // that is recreated later starts at the top,
                                // so the jump happens in one step.
                                if (tab == MainTab.Timeline) {
                                    timelineFirstVisibleItem = 0
                                    timelineFirstVisibleOffset = 0
                                } else {
                                    albumFirstVisibleItem = 0
                                    albumFirstVisibleOffset = 0
                                }
                                scrollToTopToken = System.nanoTime()
                            } else {
                                // Already at the top: a second tap refreshes.
                                // The library pages replay the pull-to-refresh
                                // gesture so the reload is visible and matches
                                // what a finger pull does.
                                when (tab) {
                                    MainTab.Pixiv -> requestPixivReload()
                                    MainTab.Albums, MainTab.Videos, MainTab.Timeline ->
                                        pullRefreshToken = System.nanoTime()
                                    else -> Unit
                                }
                            }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        if (tab == MainTab.Pixiv) {
                            Box(
                                modifier = Modifier.height(24.dp).width(24.dp).offset { IntOffset(0, iconOffset.roundToPx()) }.background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    tabIcon(tab),
                                    contentDescription = tabLabel(tab),
                                    tint = navTint,
                                    modifier = Modifier.fillMaxSize().graphicsLayer {
                                        scaleX = 1.45f
                                        scaleY = 1.45f
                                    }
                                )
                            }
                        } else {
                            Icon(
                                tabIcon(tab),
                                contentDescription = tabLabel(tab),
                                tint = navTint,
                                modifier = Modifier.height(24.dp).offset { IntOffset(0, iconOffset.roundToPx()) }
                            )
                        }
                        Text(
                            tabLabel(tab),
                            color = navTint,
                            fontSize = VaultDimens.BottomLabel,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(top = 2.dp).alpha(labelAlpha)
                        )
                    }
                } }
                draggedNavTab?.let { dragged ->
                    val ghostWidth = 72.dp
                    val ghostHeight = 56.dp
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val ghostWidthPx = with(density) { ghostWidth.toPx() }
                    val ghostHeightPx = with(density) { ghostHeight.toPx() }
                    val ghostSelected = selectedTab == dragged
                    val ghostTint = if (ghostSelected) {
                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (navDragX - ghostWidthPx / 2f).roundToInt(),
                                    (navDragY - ghostHeightPx / 2f).roundToInt()
                                )
                            }
                            .width(ghostWidth)
                            .height(ghostHeight)
                            .graphicsLayer {
                                scaleX = 1.08f
                                scaleY = 1.08f
                            }
                            .zIndex(10f),
                        shape = RoundedCornerShape(8.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                        shadowElevation = 6.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) {
                            Icon(dragged.icon, contentDescription = null, tint = ghostTint, modifier = Modifier.height(25.dp))
                            Text(tabLabel(dragged), color = ghostTint, fontSize = VaultDimens.BottomLabel, maxLines = 1)
                        }
                    }
                }
                }
            }
            }
            }
        },
        ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(selectedTab, tabOrder, pixivTabEnabled, openedFolder, selectionMode, selectedMedia, editingMedia, selectionSlideshow) {
                    val swipeTabs = tabOrder.filter { it in enabledMainTabs(pixivTabEnabled) }
                    val swipeIndex = swipeTabs.indexOf(selectedTab)
                    val gestureEnabled = selectedMedia == null &&
                        editingMedia == null &&
                        selectionSlideshow.isEmpty() &&
                        !selectionMode &&
                        openedFolder == null &&
                        swipeIndex >= 0
                    if (!gestureEnabled) return@pointerInput

                    val minimumSwipe = 64.dp.toPx()
                    var horizontalDistance = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDistance = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            horizontalDistance += dragAmount
                        },
                        onDragEnd = {
                            val targetIndex = when {
                                horizontalDistance <= -minimumSwipe -> swipeIndex + 1
                                horizontalDistance >= minimumSwipe -> swipeIndex - 1
                                else -> swipeIndex
                            }
                            swipeTabs.getOrNull(targetIndex)?.let(::selectMainTab)
                        },
                        onDragCancel = { horizontalDistance = 0f }
                    )
                }
        ) {
            // Give the source page a real visibility transition. Shared
            // elements need the source scope to move out at the same moment
            // the viewer scope moves in; keeping the grid permanently visible
            // makes Compose place the image at its final bounds immediately.
            AnimatedVisibility(
                visible = selectedMedia == null,
                enter = fadeIn(tween(360, easing = CubicBezierEasing(.22f, .78f, .24f, 1f))),
                exit = fadeOut(tween(360, easing = CubicBezierEasing(.22f, .78f, .24f, 1f)))
            ) {
                CompositionLocalProvider(LocalMediaAnimatedVisibilityScope provides this) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (tabOrder.indexOf(targetState) >= tabOrder.indexOf(initialState)) 1 else -1
                    // Keep the page viewport fixed while the two pages crossfade.
                    // The old page leaves in the same frame the new page enters;
                    // this avoids the intermediate re-layout that made tab changes
                    // appear to jump, while preserving the HTML prototype's
                    // directional 24% travel.
                    (
                        fadeIn(
                            tween(
                                durationMillis = 380,
                                delayMillis = 18,
                                easing = CubicBezierEasing(.22f, 1f, .36f, 1f)
                            )
                        ) +
                            slideInHorizontally(
                                tween(240, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
                            ) { width -> direction * width * 24 / 100 } +
                            scaleIn(
                                initialScale = .992f,
                                animationSpec = tween(240, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
                            )
                        ) togetherWith (
                        fadeOut(
                            tween(300, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
                        ) +
                            slideOutHorizontally(
                                tween(240, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
                            ) { width -> -direction * width * 24 / 100 } +
                            scaleOut(
                                targetScale = .992f,
                                animationSpec = tween(300, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
                            )
                        ) using SizeTransform(clip = false)
                },
                contentKey = { it },
                label = "main-tab-transition"
            ) { tab ->
                Box(Modifier.fillMaxSize()) {
            when (tab) {
                MainTab.Albums -> AlbumsScreen(
                    // One page for both libraries; the top-bar switch decides
                    // whether it shows photos or videos.
                    media = if (albumShowsVideos) visibleVideos else albumImages,
                    isVideo = albumShowsVideos,
                    query = appliedQuery,
                    searchingFolders = library.searchableFoldersLoading || !library.searchableFoldersReady,
                    loading = library.loading,
                    scanning = library.scanning,
                    initialLoadComplete = library.initialLoadComplete,
                    permissionGranted = if (albumShowsVideos) library.videoPermissionGranted else library.imagePermissionGranted,
                    sort = mediaSort,
                    sortDirection = sortDirection,
                    albumColumns = albumColumns,
                    folderColumns = folderColumns,
                    layout = folderLayout,
                    initialAlbumFirstVisibleItem = albumFirstVisibleItem,
                    initialAlbumFirstVisibleOffset = albumFirstVisibleOffset,
                    onAlbumScrollPositionChanged = { index, offset ->
                        albumFirstVisibleItem = index
                        albumFirstVisibleOffset = offset
                        selectionFolderFirstVisibleItem = index
                        selectionFolderFirstVisibleOffset = offset
                        albumPageAtTop = index <= 0 && offset <= 0
                    },
                    // A folder always opens at the top. This used to receive
                    // the scroll position of whichever media grid was last on
                    // screen, so entering a folder could start halfway down.
                    initialMediaFirstVisibleItem = 0,
                    initialMediaFirstVisibleOffset = 0,
                    onFirstVisibleMediaChanged = { selectionAnchorUri = it },
                    scrollToTopToken = scrollToTopToken,
                    pullRequestToken = pullRefreshToken,
                    scrollToUri = viewerScrollUri,
                    scrollToToken = viewerScrollToken,
                    scrollRequest = pageScrollRequest,
                    onVisibleScopeChanged = { scope -> pageScope = scope; folderScope = scope },
                    onFirstVisibleFolderChanged = { selectionAnchorFolder = it },
                    onMediaScrollPositionChanged = { index, offset ->
                        selectionMediaFirstVisibleItem = index
                        selectionMediaFirstVisibleOffset = offset
                        albumPageAtTop = index <= 0 && offset <= 0
                    },
                    onRequestPermission = requestPermission,
                    onOpenMedia = { item -> if (selectionMode) { val key = item.uri.toString(); selectedUris = if (key in selectedUris) selectedUris - key else selectedUris + key } else openMedia(item) },
                    onLongPressMedia = { pressed -> freezeSelectionSort(); selectionMode = true; selectingFolders = false; selectedUris = selectedUris + pressed.uri.toString() },
                    onBatchSelectMedia = { items ->
                        freezeSelectionSort()
                        selectingFolders = false
                        selectedUris = selectedUris + items.map { it.uri.toString() }
                    },
                    onSelectionGestureStartMedia = { pressed -> selectionGestureActive = true; freezeSelectionSort(); selectingFolders = false; selectedUris = selectedUris + pressed.uri.toString() },
                    onSelectionGestureEnd = { selectionGestureActive = false; selectionMode = true },
                    onLongPressAlbum = { album, index, offset ->
                        freezeSelectionSort()
                        albumFirstVisibleItem = index
                        albumFirstVisibleOffset = offset
                        selectionFolderFirstVisibleItem = index
                        selectionFolderFirstVisibleOffset = offset
                        selectionMode = true
                        selectingFolders = true
                        selectedFolders = selectedFolders + album.name
                    },
                    onSelectionGestureStartAlbum = { album -> selectionGestureActive = true; freezeSelectionSort(); selectingFolders = true; selectedFolders = selectedFolders + album.name },
                    onBatchSelectAlbums = { albums ->
                        freezeSelectionSort()
                        selectingFolders = true
                        selectedFolders = selectedFolders + albums.map { it.name }
                    },
                    onAlbumSelectionGestureEnd = { selectionGestureActive = false; selectionMode = true },
                    onRefresh = { refreshLibrary() },
                    openedFolder = openedFolder,
                    onOpenedFolderChange = { folder -> if (folder != null && selectionMode && selectingFolders) { selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder } else openFolder(folder) },
                    sharedElementEnabled = tab == selectedTab,
                    favoriteUris = favoriteUris,
                    showFavoriteBadge = showFavoriteBadge,
                    selectionPreview = selectionMode || selectionGestureActive,
                    selectedUris = selectedUris,
                    selectedFolders = selectedFolders,
                    additionalAlbumNames = if (appliedQuery.isBlank()) emptySet() else library.searchableFolderNames + library.searchableFolderChildren.values.flatten(),
                    additionalFileNames = if (appliedQuery.isBlank()) emptyMap() else library.searchableFolderFiles,
                    additionalChildFolderNames = if (openedFolder == null) emptySet() else library.searchableFolderChildren[openedFolder].orEmpty(),
                    onClearQuery = { query = "" }
                    ,onOpenPixivArchive = {
                        pixivArchiveOpen = true
                    }
                )
                MainTab.Videos -> AlbumsScreen(
                    media = visibleVideos,
                    isVideo = true,
                    query = appliedQuery,
                    searchingFolders = library.searchableFoldersLoading || !library.searchableFoldersReady,
                    loading = library.loading,
                    scanning = library.scanning,
                    initialLoadComplete = library.initialLoadComplete,
                    permissionGranted = library.videoPermissionGranted,
                    sort = mediaSort,
                    sortDirection = sortDirection,
                    albumColumns = albumColumns,
                    folderColumns = folderColumns,
                    layout = folderLayout,
                    initialAlbumFirstVisibleItem = albumFirstVisibleItem,
                    initialAlbumFirstVisibleOffset = albumFirstVisibleOffset,
                    onAlbumScrollPositionChanged = { index, offset ->
                        albumFirstVisibleItem = index
                        albumFirstVisibleOffset = offset
                        selectionFolderFirstVisibleItem = index
                        selectionFolderFirstVisibleOffset = offset
                    },
                    initialMediaFirstVisibleItem = selectionMediaFirstVisibleItem,
                    initialMediaFirstVisibleOffset = selectionMediaFirstVisibleOffset,
                    onFirstVisibleMediaChanged = { selectionAnchorUri = it },
                    scrollToTopToken = scrollToTopToken,
                    pullRequestToken = pullRefreshToken,
                    scrollToUri = viewerScrollUri,
                    scrollToToken = viewerScrollToken,
                    scrollRequest = pageScrollRequest,
                    onVisibleScopeChanged = { scope -> pageScope = scope; folderScope = scope },
                    onFirstVisibleFolderChanged = { selectionAnchorFolder = it },
                    onMediaScrollPositionChanged = { index, offset ->
                        selectionMediaFirstVisibleItem = index
                        selectionMediaFirstVisibleOffset = offset
                    },
                    onRequestPermission = requestPermission,
                    onOpenMedia = { item -> if (selectionMode) { val key = item.uri.toString(); selectedUris = if (key in selectedUris) selectedUris - key else selectedUris + key } else openMedia(item) },
                    onLongPressMedia = { pressed -> freezeSelectionSort(); selectionMode = true; selectingFolders = false; selectedUris = selectedUris + pressed.uri.toString() },
                    onBatchSelectMedia = { items ->
                        freezeSelectionSort()
                        selectingFolders = false
                        selectedUris = selectedUris + items.map { it.uri.toString() }
                    },
                    onSelectionGestureStartMedia = { pressed -> selectionGestureActive = true; freezeSelectionSort(); selectingFolders = false; selectedUris = selectedUris + pressed.uri.toString() },
                    onSelectionGestureEnd = { selectionGestureActive = false; selectionMode = true },
                    onLongPressAlbum = { album, index, offset ->
                        freezeSelectionSort()
                        albumFirstVisibleItem = index
                        albumFirstVisibleOffset = offset
                        selectionFolderFirstVisibleItem = index
                        selectionFolderFirstVisibleOffset = offset
                        selectionMode = true
                        selectingFolders = true
                        selectedFolders = selectedFolders + album.name
                    },
                    onSelectionGestureStartAlbum = { album -> selectionGestureActive = true; freezeSelectionSort(); selectingFolders = true; selectedFolders = selectedFolders + album.name },
                    onBatchSelectAlbums = { albums ->
                        freezeSelectionSort()
                        selectingFolders = true
                        selectedFolders = selectedFolders + albums.map { it.name }
                    },
                    onAlbumSelectionGestureEnd = { selectionGestureActive = false; selectionMode = true },
                    onRefresh = { refreshLibrary() },
                    openedFolder = openedFolder,
                    onOpenedFolderChange = { folder -> if (folder != null && selectionMode && selectingFolders) { selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder } else openFolder(folder) },
                    sharedElementEnabled = tab == selectedTab,
                    favoriteUris = favoriteUris,
                    showFavoriteBadge = showFavoriteBadge,
                    selectionPreview = selectionMode || selectionGestureActive,
                    selectedUris = selectedUris,
                    selectedFolders = selectedFolders,
                    additionalAlbumNames = if (appliedQuery.isBlank()) emptySet() else library.searchableFolderNames + library.searchableFolderChildren.values.flatten(),
                    additionalFileNames = if (appliedQuery.isBlank()) emptyMap() else library.searchableFolderFiles,
                    additionalChildFolderNames = if (openedFolder == null) emptySet() else library.searchableFolderChildren[openedFolder].orEmpty(),
                    onClearQuery = { query = "" }
                )
                MainTab.Timeline -> TimelineScreen(
                    media = if (timelineShowsVideos) visibleVideos else visibleImages,
                    query = appliedQuery,
                    loading = library.loading,
                    scanning = library.scanning,
                    initialLoadComplete = library.initialLoadComplete,
                    permissionGranted = if (timelineShowsVideos) library.videoPermissionGranted else library.imagePermissionGranted,
                    isVideo = timelineShowsVideos,
                    columns = timelineColumns,
                    layout = timelineLayout,
                    // Selection mode updates the shared position; use it so
                    // leaving selection returns to the same spot.
                    initialFirstVisibleItem = selectionMediaFirstVisibleItem,
                    initialFirstVisibleOffset = selectionMediaFirstVisibleOffset,
                    onFirstVisibleMediaChanged = { selectionAnchorUri = it },
                    onVisibleScopeChanged = { pageScope = it },
                    scrollToTopToken = scrollToTopToken,
                    pullRequestToken = pullRefreshToken,
                    scrollToUri = viewerScrollUri,
                    scrollToToken = viewerScrollToken,
                    scrollRequest = pageScrollRequest,
                    onScrollPositionChanged = { index, offset ->
                        timelineFirstVisibleItem = index
                        timelineFirstVisibleOffset = offset
                        selectionMediaFirstVisibleItem = index
                        selectionMediaFirstVisibleOffset = offset
                        timelinePageAtTop = index <= 0 && offset <= 0
                    },
                    jumpToDate = timelineJumpDate,
                    onJumpConsumed = { timelineJumpDate = null },
                    onRequestPermission = requestPermission,
                    onOpenMedia = { item -> if (selectionMode) { val key = item.uri.toString(); selectedUris = if (key in selectedUris) selectedUris - key else selectedUris + key } else openMedia(item) },
                    onLongPressMedia = { pressed ->
                        freezeSelectionSort()
                        selectionMediaFirstVisibleItem = timelineFirstVisibleItem
                        selectionMediaFirstVisibleOffset = timelineFirstVisibleOffset
                        selectionMode = true
                        selectingFolders = false
                        selectedUris = selectedUris + pressed.uri.toString()
                    },
                    onBatchSelectMedia = { items ->
                        freezeSelectionSort()
                        selectingFolders = false
                        selectedUris = selectedUris + items.map { it.uri.toString() }
                    },
                    onSelectionGestureStartMedia = { pressed -> selectionGestureActive = true; freezeSelectionSort(); selectingFolders = false; selectedUris = selectedUris + pressed.uri.toString() },
                    onSelectionGestureEnd = { selectionGestureActive = false; selectionMode = true },
                    onRefresh = { refreshLibrary() },
                    sharedElementEnabled = tab == selectedTab,
                    favoriteUris = favoriteUris,
                    showFavoriteBadge = showFavoriteBadge,
                    selectionPreview = selectionMode || selectionGestureActive,
                    selectedUris = selectedUris,
                    onClearQuery = { query = "" }
                )
                MainTab.Pixiv -> Column(Modifier.fillMaxSize()) {
                    if (openedFolder == null) {
                        PixivArchiveNavigation {
                            pixivArchiveOpen = true
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                AlbumsScreen(
                    media = if (pixivSearchMode == PixivSearchMode.Tag && appliedQuery.isNotBlank()) pixivTagResults else pixivSearchImages,
                    isVideo = false,
                    query = if (pixivSearchMode == PixivSearchMode.Tag) "" else appliedQuery,
                    searchingFolders = false,
                    loading = library.loading || pixivTagsLoading || pixivPageRefreshing,
                    scanning = library.scanning,
                    permissionGranted = true,
                    sort = mediaSort,
                    sortDirection = sortDirection,
                    albumColumns = albumColumns,
                    folderColumns = folderColumns,
                    layout = folderLayout,
                    onRequestPermission = requestPermission,
                    onOpenMedia = { item -> if (selectionMode) { val key = item.uri.toString(); selectedUris = if (key in selectedUris) selectedUris - key else selectedUris + key } else openMedia(item) },
                    onLongPressMedia = { pressed ->
                        freezeSelectionSort()
                        selectionMode = true
                        selectingFolders = false
                        selectedUris = setOf(pressed.uri.toString())
                    },
                    onBatchSelectMedia = { items ->
                        freezeSelectionSort()
                        selectingFolders = false
                        selectedUris = selectedUris + items.map { it.uri.toString() }
                    },
                    onSelectionGestureStartMedia = { pressed -> selectionGestureActive = true; freezeSelectionSort(); selectingFolders = false; selectedUris = selectedUris + pressed.uri.toString() },
                    onSelectionGestureEnd = { selectionGestureActive = false; selectionMode = true },
                    onLongPressAlbum = { album, index, offset ->
                        freezeSelectionSort()
                        selectionFolderFirstVisibleItem = index
                        selectionFolderFirstVisibleOffset = offset
                        selectionMode = true
                        selectingFolders = true
                        selectedFolders = selectedFolders + album.name
                    },
                    onSelectionGestureStartAlbum = { album -> selectionGestureActive = true; freezeSelectionSort(); selectingFolders = true; selectedFolders = selectedFolders + album.name },
                    onBatchSelectAlbums = { albums ->
                        freezeSelectionSort()
                        selectingFolders = true
                        selectedFolders = selectedFolders + albums.map { it.name }
                    },
                    onAlbumSelectionGestureEnd = { selectionGestureActive = false; selectionMode = true },
                    onRefresh = {
                        requestPixivReload()
                    },
                    openedFolder = openedFolder,
                    onOpenedFolderChange = { folder -> if (folder != null && selectionMode && selectingFolders) { selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder } else openFolder(folder) },
                    sharedElementEnabled = tab == selectedTab,
                    onOpenPixivArchive = {
                        pixivArchiveOpen = true
                    },
                    selectedFolders = selectedFolders,
                    pinnedAlbumName = pixivSourceFolderName,
                    albumQueryMatchesItems = pixivSearchMode != PixivSearchMode.Artist,
                    flatMode = pixivSearchMode == PixivSearchMode.Tag && appliedQuery.isNotBlank(),
                    scrollToTopToken = scrollToTopToken,
                    pullRequestToken = pullRefreshToken,
                    scrollToUri = viewerScrollUri,
                    scrollToToken = viewerScrollToken,
                    scrollRequest = pageScrollRequest,
                    onVisibleScopeChanged = { scope -> pageScope = scope; folderScope = scope },
                    favoriteUris = favoriteUris,
                    showFavoriteBadge = showFavoriteBadge,
                    selectionPreview = selectionMode || selectionGestureActive,
                    selectedUris = selectedUris,
                    emptyMessage = if (appliedQuery.isBlank()) {
                        if (english) "Enter a tag to search images" else "输入 Tag 搜索图片"
                    } else if (english) "No images match this tag" else "没有匹配该 Tag 的图片"
                )
                    }
                }
                MainTab.Tools -> ToolsScreen(
                    english = english,
                    onOpenArchive = { pixivArchiveOpen = true },
                    onOpenWallpaper = {
                        wallpaperShowVideos = false
                        wallpaperManagerOpen = true
                    },
                    onOpenCleanup = { cleanupOpen = true },
                    onStartSlideshow = {
                        slideshowQueueOpen = true
                    },
                    reorderEnabled = toolsReorderEnabled,
                    order = toolsOrder,
                    onOrderChange = { updated ->
                        toolsOrder = updated
                        albumSettings.edit().putString("tools_order", updated.joinToString(",")).apply()
                    },
                    onOpenPixiv = {
                        // Open the Pixiv page itself without adding a bottom-bar
                        // tab for it.
                        selectedTab = MainTab.Pixiv
                    }
                )
                MainTab.Settings -> SettingsScreen(
                    language = appLanguage,
                    onOpenCleanup = { cleanupOpen = true },
                    onThemeModeChange = onThemeModeChange,
                    onThemeColorChange = onThemeColorChange,
                    onNavReorderChange = { navReorderEnabled = it },
                    onToolsReorderChange = { toolsReorderEnabled = it },
                    onShowHints = { showGestureHints = true },
                    onPixivTabEnabledChange = { enabled ->
                        pixivTabEnabled = enabled
                        tabOrder = normalizedTabOrder(tabOrder, enabled)
                        if (!enabled && selectedTab == MainTab.Pixiv) {
                            selectedTab = MainTab.Albums
                            query = ""
                            folderScope = null
                        }
                    },
                    onRetentionChange = library::purgeExpiredRecycle,
                    onDefaultSortChange = { selected ->
                        mediaSort = when (selected) {
                            "名称" -> MediaSort.Name
                            "大小" -> MediaSort.Size
                            else -> MediaSort.Time
                        }
                    },
                    onBackgroundOptimizationChange = { enabled ->
                        backgroundOptimizationEnabled = enabled
                        library.setBackgroundOptimization(enabled)
                    },
                    showFavoriteBadge = showFavoriteBadge,
                    onShowFavoriteBadgeChange = { showFavoriteBadge = it },
                    onShowHiddenMediaChange = { enabled ->
                        scope.launch {
                            library.setShowHiddenMedia(enabled)
                            pixivRefreshKey++
                        }
                    },
                    onRenameExtensionChange = { showRenameExtension = it },
                    onLanguageChange = onAppLanguageChange
                )
            }
            if (selectionMode && selectingFolders && appliedQuery.isNotBlank() && library.searchableFoldersLoading) {
                Box(
                        modifier = Modifier
                            .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
                }
            }
                }
            }
        }
            }
        }

            if (slideshowQueueOpen) {
                Box(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = VaultDimens.HeaderContentHeight)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    SlideshowQueueScreen(
                        items = slideshowQueueSorted,
                        columns = slideshowQueueColumns,
                        layout = if (slideshowQueueFolderMode) slideshowQueueFolderLayout else slideshowQueueLayout,
                        folderMode = slideshowQueueFolderMode,
                        openedFolder = slideshowQueueOpenedFolder,
                        onOpenFolder = { folder -> slideshowQueueOpenedFolder = folder },
                        onCloseFolder = { slideshowQueueOpenedFolder = null },
                        // Opening a picture from the queue is the same thing as
                        // playing the slideshow: the viewer then runs in
                        // slideshow mode, so tapping the screen enters the
                        // preview page with the playback still attached.
                        onOpenMedia = { item ->
                            val slides = slideshowQueueSorted
                            selectionSlideshow = slides
                            viewerScope = slides
                            slideshowStartImmersive = false
                            openMedia(item)
                        },
                        onRemove = { item ->
                            val key = item.uri.toString()
                            val uris = slideshowQueueUris - key
                            val order = slideshowQueueOrder - key
                            slideshowQueueUris = uris
                            slideshowQueueOrder = order
                            persistSlideshowQueue(uris, order)
                        }
                    )
                }
            }
            if (wallpaperManagerOpen) {
                Box(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = VaultDimens.HeaderContentHeight)
                        .background(MaterialTheme.colorScheme.surface)
                        // The empty manager still needs a full-size hit target;
                        // drawing a background alone does not stop clicks from
                        // reaching the page underneath it.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { }
                ) {
                    WallpaperManagerScreen(
                        queuedMedia = wallpaperQueueMedia.filter { it.isVideo == wallpaperShowVideos },
                        searchMedia = wallpaperSearchMedia,
                        query = "",
                        selectionMode = wallpaperSelectionMode,
                        selectedUris = wallpaperSelectedUris,
                        selectionOrder = wallpaperSelectionOrder,
                        columns = wallpaperColumns,
                        mediaLayout = wallpaperMediaLayout,
                        folderLayout = wallpaperFolderLayout,
                        sort = wallpaperSort,
                        sortDirection = wallpaperSortDirection,
                        queueOrder = wallpaperQueueOrder,
                        onOpenMedia = ::openMedia,
                        onToggleSelection = { item ->
                            val key = item.uri.toString()
                            wallpaperSelectedUris = if (key in wallpaperSelectedUris) wallpaperSelectedUris - key else wallpaperSelectedUris + key
                        },
                        onEnterSelectionMode = { item, orderedItems ->
                            wallpaperSelectionMode = true
                            wallpaperSelectedUris = wallpaperSelectedUris + item.uri.toString()
                            wallpaperSelectionOrder = orderedItems.map { it.uri.toString() }
                        },
                        onRemove = ::removeFromWallpaperQueue,
                        folderMode = wallpaperFolderMode,
                        openedFolder = wallpaperOpenedFolder,
                        onOpenFolder = { folder -> wallpaperOpenedFolder = folder },
                        onCloseFolder = { wallpaperOpenedFolder = null }
                    )
                }
            }

            AnimatedVisibility(
                visible = selectedMedia != null && editingMedia == null,
                modifier = Modifier.zIndex(2000f),
                enter = androidx.compose.animation.EnterTransition.None,
                // Keep the outgoing viewer composed while the shared image
                // travels back to its thumbnail bounds.
                exit = fadeOut(tween(360, easing = CubicBezierEasing(.22f, .78f, .24f, 1f)))
            ) {
                viewerMedia?.let { viewerItem ->
                    CompositionLocalProvider(
                        LocalMediaSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalMediaAnimatedVisibilityScope provides this@AnimatedVisibility
                    ) {
                        val fallbackViewerItems = if (viewerItem.isVideo) {
                            mediaInDisplayOrder((library.videos + library.localVideos).distinctBy { it.uri.toString() })
                        } else if (selectedTab == MainTab.Pixiv) {
                            mediaInDisplayOrder(pixivImages)
                        } else {
                            mediaInDisplayOrder((library.images + library.localImages).distinctBy { it.uri.toString() })
                        }
                        val viewerItems = viewerScope.takeIf { scope ->
                            scope.any { it.uri == viewerItem.uri } &&
                                scope.all { it.isVideo == viewerItem.isVideo }
                        } ?: fallbackViewerItems
                        MediaViewer(
                            item = viewerItem,
                            items = viewerItems,
                            slideshowActive = selectionSlideshow.isNotEmpty(),
                            slideshowIntervalMs = (albumSettings.getString("slideshow_interval", "3秒")?.filter(Char::isDigit)?.toLongOrNull() ?: 3L) * 1000L,
                            // The play button starts in full screen, so the
                            // slideshow runs right away; opening a picture from
                            // the queue keeps the preview page first.
                            startImmersive = slideshowStartImmersive && selectionSlideshow.isNotEmpty(),
                            useSharedElementTransition = true,
                            playbackResumeRequest = viewerPlaybackResume?.takeIf { it.uri == viewerItem.uri.toString() },
                            onPlaybackResumeConsumed = { requestId ->
                                if (viewerPlaybackResume?.requestId == requestId) viewerPlaybackResume = null
                            },
                            onItemChanged = { changed ->
                        // Keep the page under the viewer on the image the user is
                        // actually looking at, so closing returns there.
                        viewerScrollUri = changed.uri.toString()
                        viewerScrollToken = System.nanoTime()
                        if (selectionSlideshow.isNotEmpty()) {
                            // Stay inside the slideshow playlist. openMedia()
                            // would rebuild the scope from the page underneath,
                            // which ended the slideshow as soon as the user
                            // swiped to another picture.
                            viewerMedia = changed
                            selectedMedia = changed
                            // No shared-element hand-over between slideshow
                            // pictures: it made the page flash while swiping.
                            activeSharedMediaKey = null
                        } else {
                            openMedia(changed)
                        }
                    },
                            onClose = {
                                selectedMedia = null
                                selectionSlideshow = emptyList()
                                slideshowStartImmersive = false
                                // Keep the viewer and source key composed for
                                // the full shared-element return animation.
                                scope.launch {
                                    delay(400L)
                            if (selectedMedia == null) {
                                viewerMedia = null
                                viewerScope = emptyList()
                                activeSharedMediaKey = null
                            }
                        }
                            },
                            onDelete = { deleting -> requestDeleteWithConfirmation(listOf(deleting)) },
                            onEdit = { editing -> beginEditing(editing) },
                            onCopy = { copying -> transferRequest = TransferRequest(listOf(copying), TransferMode.Copy) },
                            onMove = { moving -> transferRequest = TransferRequest(listOf(moving), TransferMode.Move) },
                            onRename = { renaming, newName ->
                                scope.launch {
                                    val renamed = library.rename(renaming, newName)
                                    if (renamed != null) {
                                        openMedia(renamed)
                                    } else if (
                                        !renaming.isDocument &&
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                        !canModifyMediaDirectly(context)
                                    ) {
                                        pendingRename = renaming to newName
                                        val request = MediaStore.createWriteRequest(context.contentResolver, listOf(renaming.uri))
                                        writeLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                                    } else {
                                        Toast.makeText(context, appText("重命名失败", english), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            favorite = { favoriteItem -> favoriteItem.uri.toString() in favoriteUris },
                            onFavorite = { favoriteItem ->
                                val key = favoriteItem.uri.toString()
                                favoriteUris = if (key in favoriteUris) favoriteUris - key else favoriteUris + key
                                preferences.edit().putStringSet("favorites", favoriteUris).apply()
                            },
                            onEditTags = { editing -> openTagEditor(editing) },
                            onWallpaper = ::requestWallpaper,
                            pictureInPictureMode = pictureInPictureMode,
                            onEnterPictureInPicture = onEnterPictureInPicture,
                            onAutoEnterPictureInPictureChange = onAutoEnterPictureInPictureChange
                        )
                    }
                }
            }
        }
    }

    editingMedia?.let { editing ->
        ImageEditorDialog(
            item = editing,
            enterFromViewer = editorReturnMedia != null,
            onDismiss = ::finishEditing,
            onSaved = {
                scope.launch { library.refresh(library.permissionGranted) }
                finishEditing()
            }
        )
    }

    if (showPixivHomeIntro) {
        VaultInfoSheet(
            title = "Pixiv 文件归档说明",
            body = "Pixiv 文件归档，用于把本地保存的 Pixiv 图片整理到指定目录。\n\n使用时先选择“来源目录”和“归档目标目录”，然后点击“开始扫描”。应用会从图片文件名中读取 Pixiv 作品 ID，登录 Pixiv 网站查询作品、画师和标签，并将结果保存到本地。\n\n扫描完成后，选择要处理的图片并点击“归档”。应用会将图片移动至目标目录中与其画师名对应的文件夹内，如果尚无此文件夹，则按“画师名称_UID”建立文件夹。没有扫描到作品信息的图片不会被归档，可在失败结果中重新处理。\n\nPixiv 文件归档主页支持按画师或标签，在来源文件夹和归档文件夹内，搜索图片和文件夹。",
            dismissLabel = "知道了",
            onDismiss = { showPixivHomeIntro = false }
        )
    }

    wallpaperCropItem?.let { item ->
        WallpaperCropScreen(
            item = item,
            onDismiss = { wallpaperCropItem = null },
            onConfirm = { composed ->
                wallpaperCropItem = null
                com.example.album.ui.setStaticWallpaperBitmap(context, composed, english)
            }
        )
    }
    if (showPixivArchiveInfo) {
        VaultInfoSheet(
            title = "Pixiv 文件归档说明与注意事项",
            body = "Pixiv 文件归档，用于把本地保存的 Pixiv 图片整理到指定目录。\n\n使用时先选择“来源目录”和“归档目标目录”，然后点击“开始扫描”。应用会从图片文件名中读取 Pixiv 作品 ID，登录 Pixiv 网站查询作品、画师和标签，并将结果保存到本地。\n\n扫描完成后，选择要处理的图片并点击“归档”。应用会将图片移动至目标目录中与其画师名对应的文件夹内，如果尚无此文件夹，则按“画师名称_UID”建立文件夹。没有扫描到作品信息的图片不会被归档，可在失败结果中重新处理。\n\nPixiv 文件归档主页支持按画师或标签，在来源文件夹和归档文件夹内，搜索图片和文件夹。\n\n请先登录 Pixiv，并确保网络可用。来源目录和归档目标目录不能互相包含。归档前请确认移动/复制选项和目标目录正确。写入 tags 可能修改图片信息。只有成功处理的文件会标记为已归档，失败文件会保留在原位置。图片较多时，建议降低单次扫描上限并分批处理。",
            dismissLabel = "知道了",
            onDismiss = { showPixivArchiveInfo = false }
        )
    }
    if (showWallpaperSettings) {
        WallpaperSettingsSheet(
            preferences = preferences,
            initialIsVideo = wallpaperShowVideos,
            onDismiss = { showWallpaperSettings = false }
        )
    }
    if (showSlideshowSettings) {
        SlideshowSettingsSheet(
            preferences = albumSettings,
            onDismiss = { showSlideshowSettings = false }
        )
    }
    if (showGestureHints) {
        GestureHintsSheet(onDismiss = {
            showGestureHints = false
            albumSettings.edit().putBoolean("gesture_hints_shown", true).apply()
        })
    }
    if (showSlideshowColumnDialog) {
        val options = (1..6).map { if (english) "$it columns" else "$it 列" }
        VaultWheelChoiceSheet(
            title = appText("列数", english),
            options = options,
            selected = options.getOrElse(slideshowQueueColumns - 1) { options.first() },
            onDismiss = { showSlideshowColumnDialog = false },
            onApply = { label ->
                slideshowQueueColumns = label.substringBefore(' ').toIntOrNull() ?: slideshowQueueColumns
                showSlideshowColumnDialog = false
            }
        )
    }
    if (showSlideshowLayoutDialog) {
        // Same two wheels as the wallpaper manager: the first picks the
        // surface (media ≈ timeline, folder ≈ album page), the second its
        // layout.
        val scopes = listOf(appText("媒体", english), appText("文件夹", english))
        val layouts = MediaLayout.entries.map { appText(it.label, english) }
        VaultLayoutWheelSheet(
            title = appText("排布方式", english),
            scopes = scopes,
            layouts = layouts,
            selectedScope = if (slideshowQueueFolderMode) scopes.last() else scopes.first(),
            layoutForScope = { scope ->
                val layout = if (scope == scopes.first()) slideshowQueueLayout else slideshowQueueFolderLayout
                appText(layout.label, english)
            },
            onDismiss = { showSlideshowLayoutDialog = false },
            onApply = { scope, layout ->
                val selected = MediaLayout.entries[layouts.indexOf(layout).coerceAtLeast(0)]
                if (scope == scopes.first()) {
                    slideshowQueueFolderMode = false
                    slideshowQueueLayout = selected
                } else {
                    slideshowQueueFolderMode = true
                    slideshowQueueFolderLayout = selected
                    slideshowQueueOpenedFolder = null
                }
                showSlideshowLayoutDialog = false
            }
        )
    }
    if (showSlideshowSortDialog) {
        val methods = listOf(MediaSort.Time, MediaSort.Name, MediaSort.Size)
        val methodOptions = methods.map { appText(it.label, english) }
        val directionOptions = SortDirection.entries.map { appText(it.label, english) }
        VaultSortWheelSheet(
            title = appText("排序方式", english),
            methods = methodOptions,
            selectedMethod = appText(slideshowQueueSort.label, english),
            selectedDirection = appText(slideshowQueueSortDirection.label, english),
            directions = directionOptions,
            onDismiss = { showSlideshowSortDialog = false }
        ) { method, direction ->
            slideshowQueueSort = methods[methodOptions.indexOf(method).coerceAtLeast(0)]
            slideshowQueueSortDirection = SortDirection.entries[directionOptions.indexOf(direction).coerceAtLeast(0)]
            showSlideshowSortDialog = false
        }
    }
    if (showWallpaperSortDialog) {
        val methods = WallpaperSort.entries
        val methodOptions = methods.map { if (english && it == WallpaperSort.QueueOrder) "Queue order" else appText(it.label, english) }
        val directionOptions = SortDirection.entries.map { appText(it.label, english) }
        VaultSortWheelSheet(
            title = appText("排序方式", english),
            methods = methodOptions,
            selectedMethod = methodOptions[methods.indexOf(wallpaperSort)],
            selectedDirection = appText(wallpaperSortDirection.label, english),
            directions = directionOptions,
            onDismiss = { showWallpaperSortDialog = false }
        ) { method, direction ->
            wallpaperSort = methods[methodOptions.indexOf(method)]
            wallpaperSortDirection = SortDirection.entries[directionOptions.indexOf(direction)]
            showWallpaperSortDialog = false
        }
    }
    if (showWallpaperColumnDialog) {
        val options = (1..6).map { if (english) "$it columns" else "$it 列" }
        VaultWheelChoiceSheet(
            title = appText("列数", english),
            options = options,
            selected = if (english) "$wallpaperColumns columns" else "$wallpaperColumns 列",
            onDismiss = { showWallpaperColumnDialog = false },
            onApply = { label ->
                wallpaperColumns = label.substringBefore(' ').toIntOrNull() ?: wallpaperColumns
                showWallpaperColumnDialog = false
            }
        )
    }
    if (showWallpaperLayoutDialog) {
        // Two wheels like the sort sheet: the first picks which surface is
        // being configured (media ≈ timeline, folder ≈ album page), the
        // second picks its layout.
        val scopes = listOf(appText("媒体", english), appText("文件夹", english))
        val layouts = MediaLayout.entries.map { appText(it.label, english) }
        VaultLayoutWheelSheet(
            title = appText("排布方式", english),
            scopes = scopes,
            layouts = layouts,
            selectedScope = if (wallpaperFolderMode) scopes.last() else scopes.first(),
            layoutForScope = { scope ->
                val layout = if (scope == scopes.first()) wallpaperMediaLayout else wallpaperFolderLayout
                appText(layout.label, english)
            },
            onDismiss = { showWallpaperLayoutDialog = false },
            onApply = { scope, layout ->
                val selected = MediaLayout.entries[layouts.indexOf(layout)]
                if (scope == scopes.first()) {
                    wallpaperFolderMode = false
                    wallpaperMediaLayout = selected
                } else {
                    wallpaperFolderMode = true
                    wallpaperFolderLayout = selected
                }
                showWallpaperLayoutDialog = false
            }
        )
    }
    if (showSortDialog) {
        val methods = when {
            selectedTab == MainTab.Pixiv && pixivSearchMode == PixivSearchMode.Tag -> listOf(MediaSort.Time, MediaSort.Name, MediaSort.Size)
            openedFolder == null -> listOf(MediaSort.Time, MediaSort.Name, MediaSort.Count)
            selectedTab == MainTab.Videos -> listOf(MediaSort.Time, MediaSort.Name, MediaSort.Size, MediaSort.Duration)
            else -> listOf(MediaSort.Time, MediaSort.Name, MediaSort.Size)
        }
        val methodOptions = methods.map { appText(it.label, english) }
        val directionOptions = SortDirection.entries.map { appText(it.label, english) }
        VaultSortWheelSheet(
            title = appText("排序方式", english), methods = methodOptions,
            selectedMethod = appText(mediaSort.label, english),
            selectedDirection = appText(sortDirection.label, english), directions = directionOptions,
            onDismiss = { showSortDialog = false }
        ) { method, direction ->
            mediaSort = methods[methodOptions.indexOf(method)]
            sortDirection = SortDirection.entries[directionOptions.indexOf(direction)]
            showSortDialog = false
        }
        /*
        val options = values.map { (method, direction) -> "${appText(method.label, english)} · ${appText(direction.label, english)}" }
        val selectedOption = "${appText(mediaSort.label, english)} · ${appText(sortDirection.label, english)}"
        VaultApplyChoiceSheet(appText("排序方式", english), options, selectedOption, onDismiss = { showSortDialog = false }) { label ->
            val selected = values[options.indexOf(label)]
            mediaSort = selected.first
            sortDirection = selected.second
            showSortDialog = false
        } */
    }
    if (showColumnDialog) {
        val currentColumns = when {
            selectedTab == MainTab.Timeline -> timelineColumns
            selectedTab == MainTab.Pixiv && (pixivSearchMode == PixivSearchMode.Tag || openedFolder != null) -> folderColumns
            openedFolder != null -> folderColumns
            else -> albumColumns
        }
        val options = (1..6).map { if (english) "$it columns" else "$it 列" }
        VaultWheelChoiceSheet(
            title = appText("列数", english),
            options = options,
            selected = if (english) "$currentColumns columns" else "$currentColumns 列",
            onDismiss = { showColumnDialog = false },
            onApply = { label ->
                val columns = label.substringBefore(' ').toIntOrNull() ?: return@VaultWheelChoiceSheet
                when {
                    selectedTab == MainTab.Timeline -> timelineColumns = columns
                    selectedTab == MainTab.Pixiv && (pixivSearchMode == PixivSearchMode.Tag || openedFolder != null) -> folderColumns = columns
                    openedFolder != null -> folderColumns = columns
                    else -> albumColumns = columns
                }
                showColumnDialog = false
            }
        )
    }
    if (showLayoutDialog) {
        val currentLayout = if (selectedTab == MainTab.Timeline) timelineLayout else folderLayout
        val options = MediaLayout.entries.map { appText(it.label, english) }
        VaultWheelChoiceSheet(
            title = appText("排布方式", english),
            options = options,
            selected = appText(currentLayout.label, english),
            onDismiss = { showLayoutDialog = false },
            onApply = { label ->
                val selected = MediaLayout.entries[options.indexOf(label)]
                // One layout setting for every media page, so choosing "grid"
                // on one page also means grid on the timeline and vice versa.
                timelineLayout = selected
                folderLayout = selected
                showLayoutDialog = false
            }
        )
    }
    if (showDateDialog) {
        val latest = (if (timelineShowsVideos) library.videos else library.images).maxOfOrNull { it.dateTaken }
            ?: System.currentTimeMillis()
        VaultDateSheet(
            initialMillis = latest,
            onDismiss = { showDateDialog = false }
        ) { selectedMillis ->
            query = ""
            timelineJumpDate = java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.CHINA).format(java.util.Date(selectedMillis))
            showDateDialog = false
        }
    }
    if (showExcludeDialog) {
        val folders = when (selectedTab) {
            MainTab.Videos -> library.videos.map { it.folder }
            else -> library.images.map { it.folder }
        }.distinct().sorted()
        ChoiceDialog(appText("排除文件夹", english), folders, "", onDismiss = { showExcludeDialog = false }) { folder ->
            library.excludeFolder(folder)
            showExcludeDialog = false
            Toast.makeText(context, if (english) "Excluded \"$folder\"" else "已排除“$folder”", Toast.LENGTH_SHORT).show()
        }
    }
    if (showCreateFolderDialog) {
        val normalized = createFolderName.trim()
        val valid = normalized.isNotBlank() && normalized.none { it in "\\/:*?\"<>|" }
        VaultTextInputDialog(
            title = appText("新建文件夹", english),
            value = createFolderName,
            onValueChange = { createFolderName = it },
            label = appText("文件夹名称", english),
            confirmLabel = appText("创建", english),
            confirmEnabled = valid,
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = {
                val parentItems = currentSelectionMedia.toList()
                showCreateFolderDialog = false
                scope.launch {
                    val created = library.createFolder(parentItems, normalized)
                    Toast.makeText(
                        context,
                        if (created) {
                            if (english) "Folder created" else "文件夹已创建"
                        } else {
                            if (english) "Unable to create folder" else "文件夹创建失败"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
    pendingAppDelete?.let { deleting ->
        val recycleEnabled = albumSettings.getBoolean("recycle_bin", true)
        VaultConfirmationSheet(
            title = if (recycleEnabled) {
                if (english) "Move to Trash" else "移到回收站"
            } else {
                if (english) "Delete permanently" else "永久删除"
            },
            body = if (english) {
                if (recycleEnabled) "${deleting.size} item(s) will be moved to Trash." else "${deleting.size} item(s) will be permanently deleted and cannot be recovered."
            } else {
                if (recycleEnabled) "选中的 ${deleting.size} 项将移到回收站。" else "选中的 ${deleting.size} 项将被永久删除且无法恢复。"
            },
            confirmLabel = if (recycleEnabled) {
                if (english) "Move to Trash" else "移到回收站"
            } else {
                if (english) "Delete" else "删除"
            },
            danger = true,
            onDismiss = { pendingAppDelete = null },
            onConfirm = {
                pendingAppDelete = null
                requestDelete(deleting)
            }
        )
    }
    selectionRenameItem?.let { item ->
        val extension = item.name.substringAfterLast('.', "").takeIf { it.isNotBlank() }
        val editableName = if (showRenameExtension || extension == null) {
            item.name
        } else {
            item.name.removeSuffix(".$extension")
        }
        VaultTextInputDialog(
            title = appText("重命名", english),
            value = if (selectionRenameText == item.name) editableName else selectionRenameText,
            onValueChange = { selectionRenameText = it },
            label = appText("文件名", english),
            confirmLabel = appText("保存", english),
            autoFocus = true,
            initialSelection = TextRange(0, (if (showRenameExtension && extension != null) editableName.length - extension.length - 1 else editableName.length).coerceAtLeast(0)),
            onDismiss = { selectionRenameItem = null },
            onConfirm = {
                    val enteredName = selectionRenameText.trim()
                    val newName = if (!showRenameExtension && extension != null && !enteredName.endsWith(".$extension", ignoreCase = true)) {
                        "$enteredName.$extension"
                    } else enteredName
                    if (newName.isNotEmpty() && newName != item.name) {
                                scope.launch {
                                    val renamed = library.rename(item, newName)
                                    if (
                                        renamed == null &&
                                        !item.isDocument &&
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                        !canModifyMediaDirectly(context)
                                    ) {
                                        pendingRename = item to newName
                                        val request = MediaStore.createWriteRequest(context.contentResolver, listOf(item.uri))
                                        writeLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                                    }
                                }
                    }
                    selectionRenameItem = null
                    selectionMode = false
                    selectedUris = emptySet()
            }
        )
    }
    selectionRenameFolder?.let { folder ->
        VaultTextInputDialog(
            title = appText("重命名文件夹", english),
            value = selectionRenameText,
            onValueChange = { selectionRenameText = it },
            label = appText("文件夹名称", english),
            confirmLabel = appText("保存", english),
            autoFocus = true,
            initialSelection = TextRange(0, selectionRenameText.length),
            onDismiss = { selectionRenameFolder = null },
            onConfirm = {
                val newName = selectionRenameText.trim()
                if (newName.isNotEmpty() && newName != folder) {
                    scope.launch {
                        val renamed = withContext(Dispatchers.IO) { library.renameFolder(folder, newName) }
                        if (renamed == 0) {
                            Toast.makeText(context, appText("重命名失败", english), Toast.LENGTH_SHORT).show()
                        } else {
                            library.refresh(library.permissionGranted)
                        }
                    }
                }
                selectionRenameFolder = null
                clearSelection()
            }
        )
    }
    // The slideshow now runs inside the normal image viewer (MediaViewer), so
    // the standalone overlay is no longer used.
    selectionInfoItem?.let { item ->
        VaultInfoSheet(
            title = appText("信息", english),
            body = buildString {
                appendLine(item.name)
                appendLine(item.folder)
                appendLine(item.mimeType)
                append(appText("地址", english))
                append("：")
                appendLine(item.displayAddress())
                append("${item.size / 1024L} KB")
            },
            dismissLabel = appText("知道了", english),
            onDismiss = { selectionInfoItem = null }
        )
    }
    tagEditorItem?.let { item ->
        VaultTextInputDialog(
            title = if (english) "View/Edit Tags" else "查看/编辑 Tags",
            value = tagEditorText,
            onValueChange = { tagEditorText = it },
            label = if (english) "One tag per line" else "每行一个 Tag",
            confirmLabel = appText("保存", english),
            confirmEnabled = true,
            singleLine = false,
            onDismiss = { tagEditorItem = null },
            onConfirm = {
                val tags = tagEditorText.lineSequence().map(String::trim).filter(String::isNotBlank).distinct().toList()
                tagEditorItem = null
                scope.launch {
                    if (pixivRepository.updateTags(item, tags)) {
                        pixivTagsByUri = pixivTagsByUri + (item.uri.toString() to tags)
                        pixivRefreshKey++
                        Toast.makeText(context, if (english) "Tags saved" else "Tags 已保存", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, if (english) "Unable to save tags" else "无法保存 Tags", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

internal fun resolveDeletionSources(
    requestedSources: List<String>,
    backedUpSources: Set<String>,
    recycleEnabled: Boolean
): Set<String> = if (recycleEnabled) {
    requestedSources.filterTo(mutableSetOf()) { it in backedUpSources }
} else {
    requestedSources.toSet()
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    VaultOptionSheet(title, options, selected, onDismiss, onSelect)
}

private fun requiredMediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO
    )
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaPermission(context: android.content.Context): Boolean = requiredMediaPermissions().any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun canModifyMediaDirectly(context: android.content.Context): Boolean =
    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)) ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())

private fun requiredAppPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    requiredMediaPermissions() + Manifest.permission.POST_NOTIFICATIONS
} else requiredMediaPermissions()

private fun missingPermissions(context: android.content.Context, permissions: Array<String>): Array<String> =
    permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()

/**
 * Turns a URI that arrived through the system share sheet into a library item.
 * Returns null when the media is not something Album can read, so the caller
 * reports it instead of offering a transfer that is guaranteed to fail.
 */
private fun Uri.toSharedMediaItem(context: android.content.Context): MediaItem? {
    val filename = Uri.decode(lastPathSegment.orEmpty()).substringAfterLast('/')
    val mime = runCatching { context.contentResolver.getType(this).orEmpty() }.getOrDefault("").ifBlank {
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(filename.substringAfterLast('.', "").lowercase())
            .orEmpty()
    }
    if (!mime.startsWith("image/") && !mime.startsWith("video/")) return null
    val readable = runCatching {
        com.example.album.data.openMediaInputStream(context, this)?.use { input -> input.read() >= 0 } == true
    }.getOrDefault(false)
    if (!readable) return null
    return MediaItem(
        id = toString().hashCode().toLong() and 0xffffffffL,
        uri = this,
        name = filename.ifBlank { "shared" },
        folder = "分享导入",
        dateTaken = 0L,
        mimeType = mime,
        isVideo = mime.startsWith("video/"),
        isDocument = true
    )
}

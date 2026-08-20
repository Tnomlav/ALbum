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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.album.data.MediaItem
import com.example.album.data.RecycleEntry
import com.example.album.data.displayAddress
import com.example.album.data.PixivArchiveRepository
import com.example.album.data.TransferMode
import com.example.album.data.TransferRequest
import com.example.album.playback.PlaybackResumeRequest
import com.example.album.ui.components.MediaViewer
import com.example.album.ui.components.SlideshowOverlay
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
import com.example.album.ui.components.VaultWheelChoiceSheet
import com.example.album.ui.components.VaultDateSheet
import com.example.album.ui.components.VaultTextInputDialog
import com.example.album.ui.components.VaultConfirmationSheet
import com.example.album.ui.components.VaultInfoSheet
import com.example.album.ui.editor.ImageEditorDialog
import com.example.album.ui.screens.AlbumsScreen
import com.example.album.ui.screens.SettingsScreen
import com.example.album.ui.screens.TimelineScreen
import com.example.album.ui.screens.SelectionScreen
import com.example.album.ui.screens.AlbumSelectionScreen
import com.example.album.ui.screens.CleanupScreen
import com.example.album.ui.screens.PixivArchiveScreen
import com.example.album.ui.screens.PixivArchiveSession
import com.example.album.ui.screens.ArchiveActivity
import com.example.album.ui.screens.ArchiveUiState
import com.example.album.data.PixivArchivePhase
import com.example.album.ui.screens.DestinationScreen
import com.example.album.ui.theme.VaultDimens
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

private enum class MainTab(val label: String, val icon: ImageVector) {
    Albums("相册", Icons.Outlined.Collections),
    Videos("视频", Icons.Outlined.VideoLibrary),
    Timeline("时间轴", Icons.Outlined.WatchLater),
    Pixiv("Pixiv", PixivPMark),
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
    MainTab.entries.filter { it != MainTab.Pixiv || pixivEnabled }

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

@Composable
fun AlbumApp(
    onThemeModeChange: (String) -> Unit,
    onThemeColorChange: (String) -> Unit,
    appLanguage: String,
    onAppLanguageChange: (String) -> Unit,
    externalMediaUri: Uri? = null,
    playbackResumeRequest: PlaybackResumeRequest? = null,
    onPlaybackResumeConsumed: (Long) -> Unit = {},
    pictureInPictureMode: Boolean = false,
    onEnterPictureInPicture: () -> Boolean = { false }
) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val scope = rememberCoroutineScope()
    val library = remember { MediaLibraryState(context) }
    val lifecycleOwner = context as? LifecycleOwner
    val pixivRepository = remember { PixivArchiveRepository(context) }
    val pixivArchiveSession = remember { PixivArchiveSession() }
    val preferences = remember { context.getSharedPreferences("album_preferences", android.content.Context.MODE_PRIVATE) }
    val albumSettings = remember { context.getSharedPreferences("album_settings", android.content.Context.MODE_PRIVATE) }
    val transferPreferences = remember { context.getSharedPreferences("transfer_preferences", android.content.Context.MODE_PRIVATE) }
    val pixivEnabledAtStart = albumSettings.getBoolean("pixiv_tab_enabled", false)
    val initialTab = when (albumSettings.getString("default_home", "相册")) {
        "视频" -> MainTab.Videos
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
    val snackbarHostState = remember { SnackbarHostState() }
    var undoRecycleEntries by remember { mutableStateOf<List<RecycleEntry>>(emptyList()) }
    var pendingRename by remember { mutableStateOf<Pair<MediaItem, String>?>(null) }
    var pendingRecycleIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingTrashRestore by remember { mutableStateOf<List<com.example.album.data.RecycleEntry>>(emptyList()) }
    var pendingTrashDelete by remember { mutableStateOf<List<com.example.album.data.RecycleEntry>>(emptyList()) }
    var cleanupOpen by rememberSaveable { mutableStateOf(false) }
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
    var showExcludeDialog by remember { mutableStateOf(false) }
    var favoriteUris by remember { mutableStateOf(preferences.getStringSet("favorites", emptySet()).orEmpty().toSet()) }
    var showFavoriteBadge by remember { mutableStateOf(albumSettings.getBoolean("show_favorite_badge", true)) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectingFolders by rememberSaveable { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionFolderFirstVisibleItem by remember { mutableIntStateOf(0) }
    var selectionFolderFirstVisibleOffset by remember { mutableIntStateOf(0) }
    var folderScope by remember { mutableStateOf<List<MediaItem>?>(null) }
    var openedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionRenameItem by remember { mutableStateOf<MediaItem?>(null) }
    var selectionRenameFolder by remember { mutableStateOf<String?>(null) }
    var selectionRenameText by remember { mutableStateOf("") }
    var selectionSlideshow by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectionInfoItem by remember { mutableStateOf<MediaItem?>(null) }
    var tagEditorItem by remember { mutableStateOf<MediaItem?>(null) }
    var tagEditorText by remember { mutableStateOf("") }
    var navReorderEnabled by remember { mutableStateOf(albumSettings.getBoolean("nav_reorder", false)) }
    var pixivTabEnabled by remember { mutableStateOf(pixivEnabledAtStart) }
    var pixivLibraryImages by remember { mutableStateOf<List<MediaItem>?>(null) }
    var pixivFolderNames by remember { mutableStateOf(setOf("Pixiv")) }
    var pixivSourceFolderName by remember { mutableStateOf("Pixiv") }
    var pixivTagsByUri by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var pixivRefreshKey by remember { mutableIntStateOf(0) }
    var pixivSearchMode by rememberSaveable { mutableStateOf(PixivSearchMode.Artist) }
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
    var bottomBarWidth by remember { mutableIntStateOf(0) }
    var draggedNavTab by remember { mutableStateOf<MainTab?>(null) }
    var navDragX by remember { mutableFloatStateOf(0f) }
    var navDragY by remember { mutableFloatStateOf(0f) }
    var suppressNavClickUntil by remember { mutableLongStateOf(0L) }
    val currentTabOrder by rememberUpdatedState(tabOrder)
    val hapticFeedback = LocalHapticFeedback.current
    val visibleImages by remember { derivedStateOf {
        val source = if (query.isBlank()) library.images else (library.images + library.localImages).distinctBy { it.uri }
        if (favoriteFilter) source.filter { it.uri.toString() in favoriteUris } else source
    } }
    val visibleVideos by remember { derivedStateOf {
        val source = if (query.isBlank()) library.videos else (library.videos + library.localVideos).distinctBy { it.uri }
        if (favoriteFilter) source.filter { it.uri.toString() in favoriteUris } else source
    } }
    val defaultPixivImages by remember { derivedStateOf {
        (library.images + library.localImages)
            .filter { it.folder.equals("Pixiv", ignoreCase = true) }
            .distinctBy { it.uri.toString() }
    } }
    LaunchedEffect(pixivTabEnabled, pixivRefreshKey, cleanupOpen, defaultPixivImages) {
        if (pixivTabEnabled && !cleanupOpen) {
            val snapshot = pixivRepository.loadLibrary(defaultPixivImages)
            // Include archived artist folders in the P page; Pixiv itself is
            // pinned separately below so it remains the first folder.
            pixivLibraryImages = snapshot.items
            pixivTagsByUri = snapshot.tagsByUri
            pixivFolderNames = snapshot.folderNames
            pixivSourceFolderName = snapshot.sourceFolderName
        }
    }
    val pixivImages by remember { derivedStateOf {
        val source = pixivLibraryImages ?: defaultPixivImages
        if (favoriteFilter) source.filter { it.uri.toString() in favoriteUris } else source
    } }
    val albumImages by remember { derivedStateOf {
        visibleImages
    } }
    val pixivTagResults by remember { derivedStateOf {
        if (query.isBlank()) emptyList() else pixivImages.filter { item ->
            pixivTagsByUri[item.uri.toString()].orEmpty().any { tag -> tag.contains(query, ignoreCase = true) }
        }
    } }
    val currentSelectionMedia by remember { derivedStateOf {
        when (selectedTab) {
            MainTab.Videos -> folderScope ?: visibleVideos
            MainTab.Albums -> folderScope ?: albumImages
            MainTab.Timeline -> if (timelineShowsVideos) visibleVideos else visibleImages
            MainTab.Pixiv -> if (pixivSearchMode == PixivSearchMode.Tag && query.isNotBlank()) pixivTagResults else folderScope ?: pixivImages
            MainTab.Settings -> emptyList()
        }
    } }
    val selectedItems by remember { derivedStateOf {
        if (selectingFolders) currentSelectionMedia.filter { it.folder in selectedFolders }
        else currentSelectionMedia.filter { it.uri.toString() in selectedUris }
    } }
    val observerRefreshJob = remember { arrayOfNulls<Job>(1) }
    var suppressObserverRefreshUntil by remember { mutableLongStateOf(0L) }
    val latestSuppressObserverRefreshUntil by rememberUpdatedState(suppressObserverRefreshUntil)

    DisposableEffect(lifecycleOwner, backgroundOptimizationEnabled) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Do not keep decoding thumbnails while the app is in the
                // background. Visible screens still load on demand later.
                com.example.album.data.ThumbnailRepository.cancelBackgroundOptimization()
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

    fun tabLabel(tab: MainTab): String = if (appLanguage == "English") when (tab) {
        MainTab.Albums -> "Albums"
        MainTab.Videos -> "Videos"
        MainTab.Timeline -> "Timeline"
        MainTab.Pixiv -> "Pixiv"
        MainTab.Settings -> "Settings"
    } else tab.label

    fun openMedia(item: MediaItem) {
        // Video playback has no shared-image destination; keep its thumbnail
        // visible while the native player performs its regular entrance.
        activeSharedMediaKey = item.takeUnless { it.isVideo }?.let { "media:${it.uri}" }
        viewerScope = currentSelectionMedia
            .filter { it.isVideo == item.isVideo }
            .takeIf { scope -> scope.any { it.uri == item.uri } }
            ?: if (item.isVideo) {
                (library.videos + library.localVideos).distinctBy { it.uri.toString() }
            } else if (selectedTab == MainTab.Pixiv) {
                pixivImages
            } else {
                (library.images + library.localImages).distinctBy { it.uri.toString() }
            }
        viewerMedia = item
        selectedMedia = item
    }

    LaunchedEffect(externalMediaUri) {
        val uri = externalMediaUri ?: return@LaunchedEffect
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) return@LaunchedEffect
        val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "外部媒体"
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
        selectedTab = if (externalItem.isVideo) MainTab.Videos else MainTab.Albums
        openMedia(externalItem)
    }

    LaunchedEffect(playbackResumeRequest, library.videos, library.localVideos) {
        val request = playbackResumeRequest ?: return@LaunchedEffect
        val video = (library.videos + library.localVideos)
            .firstOrNull { it.uri.toString() == request.uri }
            ?: return@LaunchedEffect
        viewerPlaybackResume = request
        selectedTab = MainTab.Videos
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
        openedFolder = null
        folderScope = null
        query = ""
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
        openedFolder = null
        folderScope = null
        clearSelection()
        timelineJumpDate = null
    }

    fun returnToPrimaryTab() {
        clearSelection()
        closeFolder()
        timelineJumpDate = null
        favoriteFilter = false
        if (selectedTab != MainTab.Albums) selectedTab = MainTab.Albums
    }

    val appBackEnabled by remember {
        derivedStateOf {
            val standalonePageOpen = transferRequest != null || pixivArchiveOpen || cleanupOpen
            (standalonePageOpen || (selectedMedia == null && editingMedia == null && selectionSlideshow.isEmpty())) &&
                pendingAppDelete == null &&
                selectionRenameItem == null &&
                selectionInfoItem == null &&
                tagEditorItem == null &&
                !showSortDialog &&
                !showColumnDialog &&
                !showLayoutDialog &&
                !showDateDialog &&
                !showExcludeDialog &&
                (
                    transferRequest != null ||
                        pixivArchiveOpen ||
                        cleanupOpen ||
                        selectionMode ||
                        openedFolder != null ||
                        query.isNotBlank() ||
                        favoriteFilter ||
                        timelineJumpDate != null ||
                        selectedTab != MainTab.Albums
                    )
        }
    }

    BackHandler(enabled = appBackEnabled) {
        when {
            transferRequest != null -> transferRequest = null
            pixivArchiveOpen -> {
                pixivArchiveOpen = false
                pixivRefreshKey++
            }
            cleanupOpen -> {
                cleanupOpen = false
                pixivRefreshKey++
            }
            selectionMode -> clearSelection()
            openedFolder != null -> closeFolder()
            query.isNotBlank() -> query = ""
            favoriteFilter -> favoriteFilter = false
            timelineJumpDate != null -> timelineJumpDate = null
            selectedTab != MainTab.Albums -> returnToPrimaryTab()
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeletes.forEach(library::remove)
            val deletedUris = pendingDeletes.mapTo(hashSetOf()) { it.uri.toString() }
            pixivArchiveSession.records.value = pixivArchiveSession.records.value.filterNot {
                it.uri.toString() in deletedUris && it.uri.toString() in pixivArchivePendingDeleteUris
            }
            pixivArchivePendingDeleteUris -= deletedUris
            if (pendingDeletes.any { it.uri == selectedMedia?.uri }) selectedMedia = null
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
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeletes.forEach(library::remove)
            val deletedUris = pendingDeletes.mapTo(hashSetOf()) { it.uri.toString() }
            pixivArchiveSession.records.value = pixivArchiveSession.records.value.filterNot {
                it.uri.toString() in deletedUris && it.uri.toString() in pixivArchivePendingDeleteUris
            }
            pixivArchivePendingDeleteUris -= deletedUris
            if (pendingDeletes.any { it.uri == selectedMedia?.uri }) selectedMedia = null
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
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            .recoverCatching { context.contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION) }
        scope.launch {
            library.addLocalFolder(uri)
            Toast.makeText(context, if (english) "Local folder added" else "已添加本地文件夹", Toast.LENGTH_SHORT).show()
        }
    }
    val requestDelete: (List<MediaItem>) -> Unit = requestDelete@ { deleting ->
        if (deleting.isEmpty()) return@requestDelete
        scope.launch {
            val recycleEnabled = albumSettings.getBoolean("recycle_bin", true)
            val useSystemTrash = recycleEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && deleting.none { it.isDocument }
            val staged = when {
                useSystemTrash -> library.stageForSystemRecycle(deleting)
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
                return@launch
            }
            if (useSystemTrash) {
                val directlyTrashed = withContext(Dispatchers.IO) {
                    deletable.filter { media ->
                        runCatching {
                            context.contentResolver.update(
                                media.uri,
                                ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) },
                                null,
                                null
                            ) > 0
                        }.getOrDefault(false)
                    }
                }
                directlyTrashed.forEach(library::remove)
                val directlyDeletedUris = directlyTrashed.mapTo(hashSetOf()) { it.uri.toString() }
                pixivArchiveSession.records.value = pixivArchiveSession.records.value.filterNot {
                    it.uri.toString() in directlyDeletedUris && it.uri.toString() in pixivArchivePendingDeleteUris
                }
                pixivArchivePendingDeleteUris -= directlyDeletedUris
                val remaining = deletable.filterNot { media -> directlyTrashed.any { it.uri == media.uri } }
                if (remaining.isNotEmpty()) {
                    pendingDeletes = remaining
                    val remainingUris = remaining.mapTo(mutableSetOf()) { it.uri.toString() }
                    pendingRecycleIds = staged.filter { it.sourceUri in remainingUris }.mapTo(mutableSetOf()) { it.id }
                    runCatching {
                    val request = MediaStore.createTrashRequest(context.contentResolver, remaining.map { it.uri }, true)
                    trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }.onFailure {
                    library.discardRecycle(pendingRecycleIds)
                    pendingDeletes = emptyList()
                    pendingRecycleIds = emptySet()
                    pixivArchivePendingDeleteUris = emptySet()
                    Toast.makeText(context, if (english) "Unable to open system Trash" else "无法打开系统回收站", Toast.LENGTH_SHORT).show()
                }
                } else {
                    pendingDeletes = emptyList()
                    pendingRecycleIds = emptySet()
                    pixivArchivePendingDeleteUris = emptySet()
                    selectedUris = emptySet()
                    selectionMode = false
                }
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                deletable.none { it.isDocument } &&
                !canModifyMediaDirectly(context)
            ) {
                pendingDeletes = deletable
                pendingRecycleIds = staged.mapTo(mutableSetOf()) { it.id }
                val request = MediaStore.createDeleteRequest(context.contentResolver, deletable.map { it.uri })
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                val failedUris = mutableSetOf<String>()
                deletable.forEach { media ->
                    if (library.deleteLegacy(media)) library.remove(media) else failedUris += media.uri.toString()
                }
                val deletedUris = deletable.mapTo(hashSetOf()) { it.uri.toString() } - failedUris
                val undoEntries = staged.filter { it.sourceUri in deletedUris && !it.systemTrashed }
                if (undoEntries.isNotEmpty()) {
                    undoRecycleEntries = undoEntries
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = if (english) "Moved ${undoEntries.size} items to Trash" else "已将 ${undoEntries.size} 项移到回收站",
                            actionLabel = if (english) "Undo" else "撤销",
                            withDismissAction = true
                        )
                        if (result == SnackbarResult.ActionPerformed && undoRecycleEntries == undoEntries) {
                            undoEntries.forEach { library.restoreRecycle(it) }
                            undoRecycleEntries = emptyList()
                            Toast.makeText(context, if (english) "Restored ${undoEntries.size} items" else "已还原 ${undoEntries.size} 项", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                pixivArchiveSession.records.value = pixivArchiveSession.records.value.filterNot {
                    it.uri.toString() in deletedUris && it.uri.toString() in pixivArchivePendingDeleteUris
                }
                pixivArchivePendingDeleteUris -= deletedUris
                val failedEntries = staged.filter { it.sourceUri in failedUris }.mapTo(mutableSetOf()) { it.id }
                if (failedEntries.isNotEmpty()) library.discardRecycle(failedEntries)
                if (deletable.any { it.uri == selectedMedia?.uri && it.uri.toString() !in failedUris }) selectedMedia = null
                selectedUris = emptySet()
                selectionMode = false
                pixivArchivePendingDeleteUris = emptySet()
            }
        }
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
        val transferMedia = library.images + library.videos
        val mediaFolders = transferMedia.map { it.folder }
        val folderCovers = transferMedia
            .groupBy { it.folder }
            .mapValues { (_, media) -> media.firstOrNull() }
            .filterValues { it != null }
            .mapValues { (_, item) -> item!! }
        val recentFolders = transferPreferences.getString("recent_folders", "").orEmpty()
            .split('\u001f').filter { it.isNotBlank() }.take(8)
        DestinationScreen(
            mode = request.mode,
            itemCount = request.items.size,
            items = request.items,
            folders = mediaFolders.distinct(),
            folderCovers = folderCovers,
            recentFolders = recentFolders,
            defaultConflictPolicy = when (albumSettings.getString("conflict", "保留两者")) {
                "覆盖" -> com.example.album.data.ConflictPolicy.Overwrite
                "跳过" -> com.example.album.data.ConflictPolicy.Skip
                else -> com.example.album.data.ConflictPolicy.KeepBoth
            },
            defaultPreserveDate = albumSettings.getBoolean("preserve_date", true),
            onBack = { transferRequest = null },
            onConfirm = { destination, policy, preserveDate ->
                scope.launch {
                    val results = library.transfer(request.items, destination, policy, preserveDate)
                    val completed = results.filter { it.success && !it.skipped }.map { it.item }
                    val skipped = results.count { it.skipped }
                    val failed = results.count { !it.success }
                    val updatedRecent = (listOf(destination) + recentFolders).distinct().take(8)
                    transferPreferences.edit().putString("recent_folders", updatedRecent.joinToString("\u001f")).apply()
                    transferRequest = null
                    library.refresh(library.permissionGranted)

                    if (request.mode == TransferMode.Move && completed.isNotEmpty()) {
                        val documents = completed.filter { it.isDocument }
                        val deletedDocuments = withContext(Dispatchers.IO) {
                            documents.filter { media -> library.deleteLegacy(media) }
                        }
                        deletedDocuments.forEach(library::remove)
                        if (pixivArchiveMoveUris.isNotEmpty()) {
                            val deletedUris = deletedDocuments.mapTo(hashSetOf()) { it.uri.toString() }
                            pixivArchiveSession.records.value = pixivArchiveSession.records.value.filterNot {
                                it.uri.toString() in deletedUris
                            }
                            pixivArchiveMoveUris = emptySet()
                        }
                        val systemMedia = completed.filterNot { it.isDocument }
                        val deletedSystemMedia = withContext(Dispatchers.IO) {
                            systemMedia.filter { media -> library.deleteLegacy(media) }
                        }
                        deletedSystemMedia.forEach(library::remove)
                        val requiresSystemConfirmation = systemMedia.filterNot { media ->
                            deletedSystemMedia.any { it.uri == media.uri }
                        }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        requiresSystemConfirmation.isNotEmpty() &&
                        !canModifyMediaDirectly(context)
                    ) {
                            pendingDeletes = requiresSystemConfirmation
                            val deleteRequest = MediaStore.createDeleteRequest(context.contentResolver, requiresSystemConfirmation.map { it.uri })
                            deleteLauncher.launch(IntentSenderRequest.Builder(deleteRequest.intentSender).build())
                        } else {
                            selectedUris = emptySet()
                            selectionMode = false
                        }
                    } else {
                        selectedUris = emptySet()
                        selectionMode = false
                    }

                    val action = if (english) {
                        if (request.mode == TransferMode.Copy) "Copied" else "Moved"
                    } else if (request.mode == TransferMode.Copy) "复制" else "移动"
                    val done = results.count { it.success && !it.skipped }
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
                pixivArchiveSession.scanJob?.cancel()
                pixivArchiveSession.scanJob = null
                    pixivArchiveSession.state.value = ArchiveUiState.Scanning
                    pixivArchiveSession.records.value = emptyList()
                    pixivArchiveSession.completed.value = 0
                    pixivArchiveSession.failed.value = 0
                    pixivArchiveSession.activity.value = ArchiveActivity(
                        message = if (english) "Reading source folder" else "正在读取来源目录"
                    )
                    pixivArchiveSession.scanJob = scope.launch {
                    runCatching {
                        pixivRepository.scan(
                            source,
                            maxItems = maxBatchSize,
                            onProgress = { update ->
                            withContext(Dispatchers.Main) {
                                val current = pixivArchiveSession.activity.value
                                pixivArchiveSession.completed.value = update.completed
                                pixivArchiveSession.failed.value = update.failed
                                pixivArchiveSession.activity.value = ArchiveActivity(
                                    phase = update.phase,
                                    completed = update.completed,
                                    total = update.total,
                                    failed = update.failed,
                                    currentFile = update.currentFile,
                                    currentArtist = update.currentArtist,
                                    message = update.message,
                                    logs = if (update.log.isBlank()) current.logs
                                    else (listOf(update.log) + current.logs).take(4)
                                )
                            }
                            },
                            onRecord = { record ->
                            withContext(Dispatchers.Main) {
                                pixivArchiveSession.records.value =
                                    pixivArchiveSession.records.value + record
                            }
                            }
                        )
                    }.onSuccess { scanned ->
                        pixivArchiveSession.records.value = scanned
                        pixivArchiveSession.state.value = ArchiveUiState.Ready
                        Toast.makeText(
                            context,
                            if (english) "Scan complete: ${scanned.size} results" else "全部扫描完成，共 ${scanned.size} 项结果",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        val message = error.message ?: if (english) "Unable to read source folder" else "无法读取来源目录"
                        val current = pixivArchiveSession.activity.value
                        pixivArchiveSession.activity.value = current.copy(
                            phase = PixivArchivePhase.Error,
                            message = message,
                            logs = (listOf(message) + current.logs).take(4)
                        )
                        pixivArchiveSession.state.value = ArchiveUiState.Error
                    }
                    if (pixivArchiveSession.scanJob === coroutineContext[Job]) pixivArchiveSession.scanJob = null
                }
            },
            onBack = { pixivArchiveOpen = false; pixivRefreshKey++ },
            onArchiveComplete = {
                // Refresh the index after archiving, but do not start a large
                // thumbnail maintenance pass on the same frame as completion.
                observerRefreshJob[0]?.cancel()
                suppressObserverRefreshUntil = android.os.SystemClock.uptimeMillis() + 1_500L
                library.refresh(library.permissionGranted, scheduleThumbnailOptimization = false)
                pixivRefreshKey++
            },
            favoriteSelected = { items -> items.isNotEmpty() && items.all { it.uri.toString() in favoriteUris } },
            onFavorite = { items ->
                val keys = items.mapTo(mutableSetOf()) { it.uri.toString() }
                favoriteUris = if (keys.all { it in favoriteUris }) favoriteUris - keys else favoriteUris + keys
                preferences.edit().putStringSet("favorites", favoriteUris).apply()
            },
            onCopy = { items -> if (items.isNotEmpty()) transferRequest = TransferRequest(items, TransferMode.Copy) },
            onRename = { item, newName ->
                val renamed = library.rename(item, newName)
                if (renamed == null) {
                    Toast.makeText(context, appText("重命名失败", english), Toast.LENGTH_SHORT).show()
                }
                if (renamed != null) scope.launch { library.refresh(library.permissionGranted) }
                renamed?.uri
            },
            onShare = { items ->
                val uris = ArrayList(items.map { it.uri })
                if (uris.isNotEmpty()) runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = when {
                            items.all(MediaItem::isVideo) -> "video/*"
                            items.none(MediaItem::isVideo) -> "image/*"
                            else -> "*/*"
                        }
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, appText("分享媒体", english)))
                }
            },
            onMove = { items ->
                if (items.isNotEmpty()) {
                    pixivArchiveMoveUris = items.mapTo(hashSetOf()) { it.uri.toString() }
                    transferRequest = TransferRequest(items, TransferMode.Move)
                }
            },
            onDelete = { items ->
                pixivArchivePendingDeleteUris = items.mapTo(hashSetOf()) { it.uri.toString() }
                requestDelete(items)
            }
        )
        return
    }

    if (cleanupOpen) {
        CleanupScreen(
            media = (library.images + library.videos).distinctBy { it.uri },
            recycleEntries = library.recycleEntries,
            excludedMedia = library.excludedMedia,
            onBack = { cleanupOpen = false; pixivRefreshKey++ },
            findDuplicates = library::findDuplicates,
            confirmMediaDeletion = albumSettings.getBoolean("delete_confirmation", true),
            recycleMediaDeletion = albumSettings.getBoolean("recycle_bin", true),
            onDeleteMedia = requestDelete,
            onRestoreRecycle = { entries ->
                val systemEntries = entries.filter { it.systemTrashed }
                val privateEntries = entries.filterNot { it.systemTrashed }
                if (privateEntries.isNotEmpty()) scope.launch {
                    val restored = privateEntries.count { library.restoreRecycle(it) }
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
                entries.filterNot { it.systemTrashed }.forEach(library::permanentlyDeleteRecycle)
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
            onRestoreExcluded = library::restoreExcludedFolder
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) SelectionTopBar(
                selected = if (selectingFolders) selectedFolders.size else selectedItems.size,
                selectingFolders = selectingFolders,
                onClose = { clearSelection() },
                favoriteSelected = selectedItems.isNotEmpty() && selectedItems.all { it.uri.toString() in favoriteUris },
                onFavorite = {
                    val selectedKeys = selectedItems.mapTo(mutableSetOf()) { it.uri.toString() }
                    favoriteUris = if (selectedKeys.all { it in favoriteUris }) favoriteUris - selectedKeys else favoriteUris + selectedKeys
                    preferences.edit().putStringSet("favorites", favoriteUris).apply()
                },
                onCopy = {
                    if (selectedItems.isNotEmpty()) transferRequest = TransferRequest(selectedItems, TransferMode.Copy)
                },
                onMove = {
                    if (selectedItems.isNotEmpty()) transferRequest = TransferRequest(selectedItems, TransferMode.Move)
                },
                onRename = {
                    if (selectingFolders) {
                        selectionRenameFolder = selectedFolders.singleOrNull()
                        selectionRenameText = selectionRenameFolder.orEmpty()
                    } else selectedItems.singleOrNull()?.let {
                        selectionRenameItem = it
                        selectionRenameText = it.name
                    }
                },
                renameEnabled = !selectingFolders || selectedFolders.size == 1,
                onShare = {
                    val uris = ArrayList(selectedItems.map { it.uri })
                    if (uris.isNotEmpty()) runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = when {
                                selectedItems.all(MediaItem::isVideo) -> "video/*"
                                selectedItems.none(MediaItem::isVideo) -> "image/*"
                                else -> "*/*"
                            }
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, appText("分享媒体", english)))
                    }.onFailure {
                        Toast.makeText(context, if (english) "No app can share these files" else "没有可分享这些文件的应用", Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = {
                    requestDeleteWithConfirmation(selectedItems)
                },
                onSlideshow = selectedItems.takeIf { items -> items.any { !it.isVideo } }?.let {{
                    selectionSlideshow = selectedItems.filterNot { it.isVideo }
                    selectionMode = false
                    selectingFolders = false
                }},
                onOpenWith = selectedItems.singleOrNull()?.takeIf { !selectingFolders }?.let { selected -> {
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(selected.uri, selected.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, appText("打开方式", english)))
                    }.onFailure {
                        Toast.makeText(context, if (english) "No app can open this file" else "没有可打开此文件的应用", Toast.LENGTH_SHORT).show()
                    }
                }},
                onInfo = selectedItems.singleOrNull()?.takeIf { !selectingFolders }?.let { selected -> { selectionInfoItem = selected }},
                onEditTags = selectedItems.singleOrNull()?.takeIf { !selectingFolders && !it.isVideo }?.let { selected -> { openTagEditor(selected) }},
                onEdit = selectedItems.singleOrNull()?.takeIf { !selectingFolders && !it.isVideo }?.let { selected -> {
                    selectionMode = false
                    beginEditing(selected)
                }},
                onWallpaper = selectedItems.singleOrNull()?.takeIf { !selectingFolders && !it.isVideo }?.let { selected -> {
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_ATTACH_DATA).apply {
                            setDataAndType(selected.uri, selected.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            putExtra("mimeType", selected.mimeType)
                        }, appText("设置为壁纸", english)))
                    }.onFailure {
                        Toast.makeText(context, if (english) "Unable to open wallpaper settings" else "无法打开壁纸设置", Toast.LENGTH_SHORT).show()
                    }
                }},
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
                        fadeIn(tween(260, delayMillis = 18, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))) +
                            slideInHorizontally(tween(380, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))) { width -> direction * width * 24 / 100 }
                        ) togetherWith (
                        fadeOut(tween(220, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))) +
                            slideOutHorizontally(tween(380, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))) { width -> -direction * width * 24 / 100 }
                        ) using SizeTransform(clip = false)
                },
                contentKey = { it },
                label = "main-tab-topbar-transition"
            ) { tab ->
                if (tab != MainTab.Settings) VaultTopBar(
                title = if (tab == MainTab.Timeline) {
                    if (appLanguage == "English") {
                        if (timelineShowsVideos) "Videos" else "Pictures"
                    } else if (timelineShowsVideos) "视频" else "图片"
                } else tabLabel(tab),
                query = query,
                searchEnabled = true,
                onQueryChange = {
                    query = it
                    if (tab == MainTab.Pixiv && it.isNotBlank()) {
                        openedFolder = null
                        folderScope = null
                    }
                },
                favoriteActive = favoriteFilter,
                onFavoriteClick = { favoriteFilter = !favoriteFilter },
                menuItems = when (tab) {
                    MainTab.Albums, MainTab.Videos -> if (appLanguage == "English") {
                        if (openedFolder == null) listOf("Scan", "Add local folder", "Columns", "Sort", "Select")
                        else listOf("Scan", "Columns", "Layout", "Sort", "Select")
                    } else {
                        if (openedFolder == null) listOf("扫描刷新", "添加本地文件夹", "列数", "排序方式", "进入多选")
                        else listOf("扫描刷新", "列数", "排布方式", "排序方式", "进入多选")
                    }
                    MainTab.Timeline -> if (appLanguage == "English") {
                        listOf("Scan", "Add local folder", "Jump to date", "Columns", "Layout", "Select")
                    } else listOf("扫描刷新", "添加本地文件夹", "跳转日期", "列数", "排布方式", "进入多选")
                    MainTab.Pixiv -> if (appLanguage == "English") {
                        if (pixivSearchMode == PixivSearchMode.Tag) listOf("Scan", "Columns", "Layout", "Sort", "Select")
                        else if (openedFolder == null) listOf("Scan", "Columns", "Sort", "Select")
                        else listOf("Scan", "Columns", "Layout", "Sort", "Select")
                    } else if (pixivSearchMode == PixivSearchMode.Tag) {
                        listOf("扫描刷新", "列数", "排布方式", "排序方式", "进入多选")
                    } else if (openedFolder == null) {
                        listOf("扫描刷新", "列数", "排序方式", "进入多选")
                    } else listOf("扫描刷新", "列数", "排布方式", "排序方式", "进入多选")
                    MainTab.Settings -> emptyList()
                },
                onMenuItemClick = { action ->
                    when (MainMenuAction.fromLabel(action)) {
                        MainMenuAction.Scan -> scope.launch {
                            requestMediaScan(true)
                            if (selectedTab == MainTab.Pixiv) pixivRefreshKey++
                        }
                        MainMenuAction.AddLocalFolder -> folderLauncher.launch(null)
                        MainMenuAction.Columns -> showColumnDialog = true
                        MainMenuAction.Layout -> showLayoutDialog = true
                        MainMenuAction.Sort -> showSortDialog = true
                        MainMenuAction.JumpToDate -> showDateDialog = true
                        MainMenuAction.Select -> {
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
                        MainMenuAction.ExcludeFolder -> showExcludeDialog = true
                        null -> Toast.makeText(
                            context,
                            if (english) "This action is unavailable" else "该功能暂不可用",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onBack = if (openedFolder != null && tab != MainTab.Timeline) {
                    ::closeFolder
                } else null,
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
                searchModeLabels = if (tab == MainTab.Pixiv) {
                    listOf(if (appLanguage == "English") "Artist" else "画师", "Tag")
                } else emptyList(),
                selectedSearchMode = if (pixivSearchMode == PixivSearchMode.Artist) 0 else 1,
                onSearchModeChange = { index ->
                    val mode = if (index == 0) PixivSearchMode.Artist else PixivSearchMode.Tag
                    if (mode != pixivSearchMode) pixivSearchMode = mode
                },
                onTitleClick = if (tab == MainTab.Timeline) ({
                    timelineShowsVideos = !timelineShowsVideos
                    query = ""
                    timelineJumpDate = null
                    Toast.makeText(
                        context,
                        if (appLanguage == "English") "Timeline switched to ${if (timelineShowsVideos) "videos" else "pictures"}"
                        else "时间轴已切换为${if (timelineShowsVideos) "视频" else "图片"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }) else null,
                chromeAlpha = pageChromeAlpha
                )
            }
        },
        bottomBar = {
            androidx.compose.foundation.layout.Column {
                if (selectionMode) SelectionSubBar(
                    selected = if (selectingFolders) selectedFolders.size else selectedItems.size,
                    total = if (selectingFolders) currentSelectionMedia.map { it.folder }.distinct().size else currentSelectionMedia.size,
                    onSelectAll = {
                        if (selectingFolders) {
                            val all = currentSelectionMedia.mapTo(mutableSetOf()) { it.folder }
                            selectedFolders = if (selectedFolders.size == all.size) emptySet() else all
                        } else {
                            selectedUris = if (selectedItems.size == currentSelectionMedia.size) emptySet()
                            else currentSelectionMedia.mapTo(mutableSetOf()) { it.uri.toString() }
                        }
                    },
                    chromeAlpha = pageChromeAlpha
                )
            AnimatedVisibility(
                visible = openedFolder == null,
                enter = slideInVertically(tween(240, easing = CubicBezierEasing(.22f, .8f, .28f, 1f))) { it } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(200, easing = CubicBezierEasing(.22f, .8f, .28f, 1f))) { it } + fadeOut(tween(140))
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
                            selectMainTab(tab)
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        if (tab == MainTab.Pixiv) {
                            Box(
                                modifier = Modifier.height(24.dp).width(24.dp).offset(y = iconOffset).background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    tab.icon,
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
                                tab.icon,
                                contentDescription = tabLabel(tab),
                                tint = navTint,
                                modifier = Modifier.height(24.dp).offset(y = iconOffset)
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
                                tween(380, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
                            ) { width -> direction * width * 24 / 100 } +
                            scaleIn(
                                initialScale = .992f,
                                animationSpec = tween(380, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
                            )
                        ) togetherWith (
                        fadeOut(
                            tween(300, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
                        ) +
                            slideOutHorizontally(
                                tween(380, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
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
            if (selectionMode && selectingFolders) AlbumSelectionScreen(
                media = currentSelectionMedia,
                selectedFolders = selectedFolders,
                columns = albumColumns,
                sort = mediaSort,
                sortDirection = sortDirection,
                initialFirstVisibleItem = selectionFolderFirstVisibleItem,
                initialFirstVisibleOffset = selectionFolderFirstVisibleOffset,
                onScrollPositionChanged = { index, offset ->
                    selectionFolderFirstVisibleItem = index
                    selectionFolderFirstVisibleOffset = offset
                },
                onToggle = { folder -> selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder }
            ) else if (selectionMode) SelectionScreen(
                    media = currentSelectionMedia,
                    selectedUris = selectedUris,
                    columns = when {
                        tab == MainTab.Timeline -> timelineColumns
                        tab == MainTab.Pixiv -> folderColumns
                        openedFolder != null -> folderColumns
                        else -> albumColumns
                    },
                    sort = mediaSort,
                    sortDirection = sortDirection,
                    onToggle = { item ->
                        val key = item.uri.toString()
                        selectedUris = if (key in selectedUris) selectedUris - key else selectedUris + key
                    }
                ) else when (tab) {
                MainTab.Albums -> AlbumsScreen(
                    media = albumImages,
                    isVideo = false,
                    query = query,
                    loading = library.loading,
                    scanning = library.scanning,
                    permissionGranted = library.permissionGranted,
                    sort = mediaSort,
                    sortDirection = sortDirection,
                    albumColumns = albumColumns,
                    folderColumns = folderColumns,
                    layout = folderLayout,
                    initialAlbumFirstVisibleItem = selectionFolderFirstVisibleItem,
                    initialAlbumFirstVisibleOffset = selectionFolderFirstVisibleOffset,
                    onRequestPermission = requestPermission,
                    onOpenMedia = ::openMedia,
                    onLongPressMedia = { pressed -> selectionMode = true; selectingFolders = false; selectedUris = setOf(pressed.uri.toString()) },
                    onLongPressAlbum = { album, index, offset ->
                        selectionFolderFirstVisibleItem = index
                        selectionFolderFirstVisibleOffset = offset
                        selectionMode = true
                        selectingFolders = true
                        selectedFolders = setOf(album.name)
                    },
                    onRefresh = { requestMediaScan(false) },
                    openedFolder = openedFolder,
                    onOpenedFolderChange = { openedFolder = it },
                    onVisibleScopeChanged = { folderScope = it },
                    sharedElementEnabled = tab == selectedTab,
                    favoriteUris = favoriteUris,
                    showFavoriteBadge = showFavoriteBadge,
                    onClearQuery = { query = "" }
                    ,onOpenPixivArchive = {
                        pixivArchiveOpen = true
                    }
                )
                MainTab.Videos -> AlbumsScreen(
                    media = visibleVideos,
                    isVideo = true,
                    query = query,
                    loading = library.loading,
                    scanning = library.scanning,
                    permissionGranted = library.permissionGranted,
                    sort = mediaSort,
                    sortDirection = sortDirection,
                    albumColumns = albumColumns,
                    folderColumns = folderColumns,
                    layout = folderLayout,
                    initialAlbumFirstVisibleItem = selectionFolderFirstVisibleItem,
                    initialAlbumFirstVisibleOffset = selectionFolderFirstVisibleOffset,
                    onRequestPermission = requestPermission,
                    onOpenMedia = ::openMedia,
                    onLongPressMedia = { pressed -> selectionMode = true; selectingFolders = false; selectedUris = setOf(pressed.uri.toString()) },
                    onLongPressAlbum = { album, index, offset ->
                        selectionFolderFirstVisibleItem = index
                        selectionFolderFirstVisibleOffset = offset
                        selectionMode = true
                        selectingFolders = true
                        selectedFolders = setOf(album.name)
                    },
                    onRefresh = { requestMediaScan(false) },
                    openedFolder = openedFolder,
                    onOpenedFolderChange = { openedFolder = it },
                    onVisibleScopeChanged = { folderScope = it },
                    sharedElementEnabled = tab == selectedTab,
                    favoriteUris = favoriteUris,
                    showFavoriteBadge = showFavoriteBadge,
                    onClearQuery = { query = "" }
                )
                MainTab.Timeline -> TimelineScreen(
                    media = if (timelineShowsVideos) visibleVideos else visibleImages,
                    query = query,
                    loading = library.loading,
                    scanning = library.scanning,
                    permissionGranted = library.permissionGranted,
                    isVideo = timelineShowsVideos,
                    columns = timelineColumns,
                    layout = timelineLayout,
                    jumpToDate = timelineJumpDate,
                    onJumpConsumed = { timelineJumpDate = null },
                    onRequestPermission = requestPermission,
                    onOpenMedia = ::openMedia,
                    onLongPressMedia = { pressed -> selectionMode = true; selectingFolders = false; selectedUris = setOf(pressed.uri.toString()) },
                    onRefresh = { requestMediaScan(false) },
                    sharedElementEnabled = tab == selectedTab,
                    favoriteUris = favoriteUris,
                    showFavoriteBadge = showFavoriteBadge,
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
                    media = if (pixivSearchMode == PixivSearchMode.Tag && query.isNotBlank()) pixivTagResults else pixivImages,
                    isVideo = false,
                    query = if (pixivSearchMode == PixivSearchMode.Tag) "" else query,
                    loading = library.loading,
                    scanning = library.scanning,
                    permissionGranted = true,
                    sort = mediaSort,
                    sortDirection = sortDirection,
                    albumColumns = albumColumns,
                    folderColumns = folderColumns,
                    layout = folderLayout,
                    onRequestPermission = requestPermission,
                    onOpenMedia = ::openMedia,
                    onLongPressMedia = { pressed ->
                        selectionMode = true
                        selectingFolders = false
                        selectedUris = setOf(pressed.uri.toString())
                    },
                    onLongPressAlbum = { album, index, offset ->
                        selectionFolderFirstVisibleItem = index
                        selectionFolderFirstVisibleOffset = offset
                        selectionMode = true
                        selectingFolders = true
                        selectedFolders = setOf(album.name)
                    },
                    onRefresh = {
                        requestMediaScan(false)
                        pixivRefreshKey++
                    },
                    openedFolder = openedFolder,
                    onOpenedFolderChange = { openedFolder = it },
                    onVisibleScopeChanged = { folderScope = it },
                    sharedElementEnabled = tab == selectedTab,
                    onOpenPixivArchive = {
                        pixivArchiveOpen = true
                    },
                    pinnedAlbumName = pixivSourceFolderName,
                    albumQueryMatchesItems = pixivSearchMode != PixivSearchMode.Artist,
                    flatMode = pixivSearchMode == PixivSearchMode.Tag && query.isNotBlank(),
                    favoriteUris = favoriteUris,
                    showFavoriteBadge = showFavoriteBadge,
                    additionalAlbumNames = pixivFolderNames,
                    emptyMessage = if (query.isBlank()) {
                        if (english) "Enter a tag to search images" else "输入 Tag 搜索图片"
                    } else if (english) "No images match this tag" else "没有匹配该 Tag 的图片"
                )
                    }
                }
                MainTab.Settings -> SettingsScreen(
                    language = appLanguage,
                    onOpenCleanup = { cleanupOpen = true },
                    onThemeModeChange = onThemeModeChange,
                    onThemeColorChange = onThemeColorChange,
                    onNavReorderChange = { navReorderEnabled = it },
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
                        scope.launch { library.setShowHiddenMedia(enabled) }
                    },
                    onLanguageChange = onAppLanguageChange
                )
            }
                }
            }
                }
            }
        }
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
                            (library.videos + library.localVideos).distinctBy { it.uri.toString() }
                        } else if (selectedTab == MainTab.Pixiv) {
                            pixivImages
                        } else {
                            (library.images + library.localImages).distinctBy { it.uri.toString() }
                        }
                        val viewerItems = viewerScope.takeIf { scope ->
                            scope.any { it.uri == viewerItem.uri } &&
                                scope.all { it.isVideo == viewerItem.isVideo }
                        } ?: fallbackViewerItems
                        MediaViewer(
                            item = viewerItem,
                            items = viewerItems,
                            useSharedElementTransition = true,
                            playbackResumeRequest = viewerPlaybackResume?.takeIf { it.uri == viewerItem.uri.toString() },
                            onPlaybackResumeConsumed = { requestId ->
                                if (viewerPlaybackResume?.requestId == requestId) viewerPlaybackResume = null
                            },
                            onItemChanged = ::openMedia,
                            onClose = {
                                selectedMedia = null
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
                                val renamed = library.rename(renaming, newName)
                                if (renamed != null) {
                                    openMedia(renamed)
                } else if (
                    !renaming.isDocument &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    !canModifyMediaDirectly(context)
                ) {
                                    pendingRename = renaming to newName
                            if (!canModifyMediaDirectly(context)) {
                                val request = MediaStore.createWriteRequest(context.contentResolver, listOf(renaming.uri))
                                writeLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                            }
                                } else {
                                    Toast.makeText(context, appText("重命名失败", english), Toast.LENGTH_SHORT).show()
                                }
                            },
                            favorite = { favoriteItem -> favoriteItem.uri.toString() in favoriteUris },
                            onFavorite = { favoriteItem ->
                                val key = favoriteItem.uri.toString()
                                favoriteUris = if (key in favoriteUris) favoriteUris - key else favoriteUris + key
                                preferences.edit().putStringSet("favorites", favoriteUris).apply()
                            },
                            onEditTags = { editing -> openTagEditor(editing) },
                            pictureInPictureMode = pictureInPictureMode,
                            onEnterPictureInPicture = onEnterPictureInPicture
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
                if (selectedTab == MainTab.Timeline) timelineLayout = selected else folderLayout = selected
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
        VaultTextInputDialog(
            title = appText("重命名", english),
            value = selectionRenameText,
            onValueChange = { selectionRenameText = it },
            label = appText("文件名", english),
            confirmLabel = appText("保存", english),
            onDismiss = { selectionRenameItem = null },
            onConfirm = {
                    val newName = selectionRenameText.trim()
                    if (newName.isNotEmpty() && newName != item.name) {
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
    selectionSlideshow.firstOrNull()?.let { initial ->
        SlideshowOverlay(selectionSlideshow, initial, onClose = { selectionSlideshow = emptyList() })
    }
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

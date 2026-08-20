@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.album.ui.components

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Minimize
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Settings as SettingsIcon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem.Builder
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView
import com.example.album.data.MediaItem
import com.example.album.data.displayAddress
import com.example.album.data.ThumbnailRepository
import com.example.album.data.openMediaInputStream
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.playback.MediaPlaybackService
import com.example.album.playback.PlaybackResumeRequest
import com.example.album.playback.positionForPersistence
import com.example.album.playback.resumePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap

private fun frameAlignedPosition(player: ExoPlayer, requestedPositionMs: Long): Long {
    val duration = player.duration.takeIf { it > 0L }
    val requested = requestedPositionMs.coerceAtLeast(0L).let { value ->
        duration?.let { value.coerceAtMost(it) } ?: value
    }
    val frameRate = player.videoFormat?.frameRate?.takeIf { it > 0f } ?: return requested
    val frameIndex = (requested * frameRate / 1000f).roundToLong()
    val aligned = (frameIndex * 1000f / frameRate).roundToLong()
    return duration?.let { aligned.coerceIn(0L, it) } ?: aligned.coerceAtLeast(0L)
}

private fun seekToVideoFrame(player: ExoPlayer, requestedPositionMs: Long) {
    player.seekTo(frameAlignedPosition(player, requestedPositionMs))
}

@Composable
fun MediaViewer(
    item: MediaItem,
    items: List<MediaItem>,
    useSharedElementTransition: Boolean = false,
    playbackResumeRequest: PlaybackResumeRequest? = null,
    onPlaybackResumeConsumed: (Long) -> Unit = {},
    onItemChanged: (MediaItem) -> Unit,
    onClose: () -> Unit,
    onDelete: (MediaItem) -> Unit,
    onEdit: (MediaItem) -> Unit,
    onCopy: (MediaItem) -> Unit,
    onMove: (MediaItem) -> Unit,
    onRename: (MediaItem, String) -> Unit,
    favorite: (MediaItem) -> Boolean,
    onFavorite: (MediaItem) -> Unit,
    onEditTags: ((MediaItem) -> Unit)? = null,
    pictureInPictureMode: Boolean = false,
    onEnterPictureInPicture: () -> Boolean = { false }
) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val thumbnailPreferences = remember {
        context.getSharedPreferences("album_settings", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    val sharedPhotoTransition = useSharedElementTransition && !item.isVideo
    val viewerAlpha = remember { Animatable(if (sharedPhotoTransition) 1f else 0f) }
    val viewerEntranceScale = remember { Animatable(if (sharedPhotoTransition) 1f else .96f) }
    // Keep the viewer surface transparent while the shared image travels from
    // its thumbnail bounds. The shared element is rendered in the transition
    // overlay, so this only fades the page behind it instead of fading the image.
    val viewerBackgroundAlpha = remember { Animatable(if (sharedPhotoTransition) 0f else 1f) }
    var closing by remember { mutableStateOf(false) }
    val viewerItems = remember(items, item.isVideo) { items.filter { it.isVideo == item.isVideo } }
    var currentIndex by remember(item.uri, viewerItems) {
        mutableIntStateOf(viewerItems.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0))
    }
    val current = viewerItems.getOrNull(currentIndex) ?: item
    var showInfo by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var renameText by remember(current.uri) { mutableStateOf(current.name) }
    var showRename by remember { mutableStateOf(false) }
    var showVideoSettings by remember { mutableStateOf(false) }
    var videoSettingsVersion by remember { mutableIntStateOf(0) }
    var imageScale by remember { mutableFloatStateOf(1f) }
    var imageOffset by remember { mutableStateOf(Offset.Zero) }
    var imageViewport by remember { mutableStateOf(IntSize.Zero) }
    var imageControlsVisible by remember { mutableStateOf(true) }
    var videoMiniMode by remember { mutableStateOf(false) }
    var viewerDirection by remember { mutableIntStateOf(1) }
    var highResolutionLoaded by remember(current.uri) { mutableStateOf(false) }
    var originalResolutionLoaded by remember(current.uri) { mutableStateOf(false) }
    val previewOriginalImages = thumbnailPreferences.getBoolean("preview_original", true)

    LaunchedEffect(currentIndex, viewerItems) {
        if (current.isVideo) return@LaunchedEffect
        val adjacent = listOf(currentIndex + 1, currentIndex - 1, currentIndex + 2)
            .mapNotNull(viewerItems::getOrNull)
            .filterNot(MediaItem::isVideo)
        ThumbnailRepository.prefetch(context, adjacent, 1800, thumbnailPreferences)
    }

    LaunchedEffect(Unit) {
        if (sharedPhotoTransition) {
            // This starts on the same frame as the shared-element bounds
            // animation. The old grid remains visible through the transparent
            // surface and is progressively replaced as the image moves.
            viewerBackgroundAlpha.animateTo(
                1f,
                tween(360, easing = CubicBezierEasing(.22f, .78f, .24f, 1f))
            )
        } else {
            coroutineScope {
                launch { viewerAlpha.animateTo(1f, tween(220)) }
                launch { viewerEntranceScale.animateTo(1f, tween(360, easing = CubicBezierEasing(.22f, .78f, .24f, 1f))) }
            }
        }
    }

    fun leaveViewer(after: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            coroutineScope {
                launch { viewerAlpha.animateTo(0f, tween(190)) }
                launch { viewerEntranceScale.animateTo(.965f, tween(260, easing = CubicBezierEasing(.22f, .78f, .24f, 1f))) }
            }
            after()
        }
    }

    fun closeViewer() {
        if (useSharedElementTransition && !current.isVideo) {
            if (closing) return
            closing = true
            scope.launch {
                highResolutionLoaded = false
                originalResolutionLoaded = false
                // The source page enters at the same time as this surface
                // becomes transparent, so the returning shared image is not
                // composited over an opaque viewer background.
                launch {
                    viewerBackgroundAlpha.animateTo(
                        0f,
                        tween(360, easing = CubicBezierEasing(.22f, .78f, .24f, 1f))
                    )
                }
                onClose()
            }
        } else {
            leaveViewer(onClose)
        }
    }

    BackHandler {
        when {
            imageScale > 1.01f -> { imageScale = 1f; imageOffset = Offset.Zero }
            else -> closeViewer()
        }
    }

    LaunchedEffect(current.uri) {
        imageScale = 1f
        imageOffset = Offset.Zero
        imageControlsVisible = true
    }

    // Keep the window metrics stable while the viewer controls fade in/out.
    // Hiding system bars here changes the root height and makes Fit images
    // recenter between the preview and full-screen states.
    DisposableEffect(current.isVideo) {
        val activity = context as? Activity
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    fun moveViewer(direction: Int) {
        if (viewerItems.isEmpty()) return
        viewerDirection = direction
        currentIndex = (currentIndex + direction + viewerItems.size) % viewerItems.size
        onItemChanged(viewerItems[currentIndex])
    }

    Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = viewerAlpha.value
        scaleX = viewerEntranceScale.value
        scaleY = viewerEntranceScale.value
    }) {
        Surface(
            Modifier.fillMaxSize(),
            color = when {
                !current.isVideo -> Color.White.copy(alpha = viewerBackgroundAlpha.value)
                videoMiniMode -> Color.Transparent
                else -> Color.Black
            }
        ) {
            if (current.isVideo) {
                Media3VideoPlayer(
                    current = current,
                    videos = viewerItems,
                    onCurrentChanged = { changed ->
                        currentIndex = viewerItems.indexOfFirst { it.uri == changed.uri }.coerceAtLeast(0)
                        onItemChanged(changed)
                    },
                    onBack = ::closeViewer,
                    miniMode = videoMiniMode,
                    onMiniModeChange = { videoMiniMode = it },
                    pictureInPictureMode = pictureInPictureMode,
                    onEnterPictureInPicture = onEnterPictureInPicture,
                    favorite = favorite(current),
                    onFavorite = { onFavorite(current) },
                    onShare = { share(context, current, english) },
                    onSettings = { showVideoSettings = true },
                    settingsVersion = videoSettingsVersion
                )
            } else {
                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    val nextScale = (imageScale * zoomChange).coerceIn(1f, 5f)
                    val maxX = imageViewport.width * (nextScale - 1f) / 2f
                    val maxY = imageViewport.height * (nextScale - 1f) / 2f
                    imageScale = nextScale
                    imageOffset = if (nextScale <= 1.01f) Offset.Zero else Offset(
                        (imageOffset.x + panChange.x).coerceIn(-maxX, maxX),
                        (imageOffset.y + panChange.y).coerceIn(-maxY, maxY)
                    )
                }
                Box(
                    Modifier.fillMaxSize().pointerInput(current.uri, showInfo, showMenu) {
                        val topBarEnd = 68.dp.toPx()
                        val bottomBarStart = size.height - 76.dp.toPx()
                        detectTapGestures(
                            onTap = { offset ->
                                if (showInfo || showMenu) {
                                    showInfo = false
                                    showMenu = false
                                } else {
                                    imageControlsVisible = !imageControlsVisible
                                }
                            },
                            onDoubleTap = { offset ->
                                if (offset.y < topBarEnd || offset.y > bottomBarStart) return@detectTapGestures
                                imageScale = if (imageScale > 1.01f) 1f else 2.5f
                                imageOffset = Offset.Zero
                            }
                        )
                    }
                ) {
                    // Use one stable image viewport in both states. The bars
                    // are overlays, so hiding them never changes image fit or
                    // its center position.
                    Box(
                        Modifier.fillMaxSize()
                            .padding(top = 68.dp, bottom = 76.dp)
                            .clipToBounds()
                        .onSizeChanged { imageViewport = it }
                        .transformable(state = transformState, canPan = { imageScale > 1.01f })
                    ) {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            val easing = CubicBezierEasing(.22f, .72f, .24f, 1f)
                            slideInHorizontally(tween(240, easing = easing)) { width -> viewerDirection * width } togetherWith
                                slideOutHorizontally(tween(240, easing = easing)) { width -> -viewerDirection * width }
                        },
                        label = "viewer-media",
                        modifier = Modifier.fillMaxSize().pointerInput(currentIndex, viewerItems.size, imageScale) {
                            if (imageScale > 1.01f) return@pointerInput
                            var distance = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { distance = 0f },
                                onHorizontalDrag = { change, amount -> change.consume(); distance += amount },
                                onDragEnd = {
                                    if (abs(distance) > 90f) moveViewer(if (distance < 0) 1 else -1)
                                }
                            )
                        }
                    ) { shown ->
                        Box(
                            Modifier.fillMaxSize().mediaSharedElement(shown).graphicsLayer {
                                scaleX = imageScale
                                scaleY = imageScale
                                translationX = imageOffset.x
                                translationY = imageOffset.y
                            }.background(Color.White)
                        ) {
                            MediaThumbnail(
                                shown,
                                Modifier.fillMaxSize(),
                                requestedSize = 360,
                                showVideoMark = false,
                                contentScale = ContentScale.Fit,
                                backgroundColor = Color.White,
                                animateGif = true
                            )
                            run {
                                val highResolutionAlpha = if (highResolutionLoaded) 1f else 0f
                                MediaThumbnail(
                                    shown,
                                    Modifier.fillMaxSize().graphicsLayer { alpha = highResolutionAlpha },
                                    requestedSize = 1800,
                                    showVideoMark = false,
                                    contentScale = ContentScale.Fit,
                                    backgroundColor = Color.Transparent,
                                    animateGif = true,
                                    onLoaded = { highResolutionLoaded = true }
                                )
                                if (previewOriginalImages && !shown.mimeType.equals("image/gif", ignoreCase = true)) {
                                    val originalResolutionAlpha = if (originalResolutionLoaded) 1f else 0f
                                    OriginalMediaImage(
                                        shown,
                                        Modifier.fillMaxSize().graphicsLayer { alpha = originalResolutionAlpha },
                                        contentScale = ContentScale.Fit,
                                        onLoaded = { originalResolutionLoaded = true }
                                    )
                                }
                            }
                        }
                    }

                    }

                    AnimatedVisibility(
                        imageControlsVisible,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(220)),
                        modifier = Modifier.zIndex(20f)
                    ) {
                        ViewerTopBar(
                            item = current,
                            position = if (viewerItems.size > 1) "${currentIndex + 1}/${viewerItems.size}" else "",
                            favorite = favorite(current),
                            onClose = ::closeViewer,
                            onFavorite = { onFavorite(current) },
                            onInfo = { showMenu = false; showInfo = !showInfo }
                        )
                    }
                    AnimatedVisibility(
                        imageControlsVisible,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(220)),
                        modifier = Modifier.align(Alignment.BottomCenter).zIndex(20f)
                    ) {
                        ViewerBottomBar(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            // Switch to the editor atomically. Waiting for the
                            // viewer exit animation exposes the thumbnail page
                            // for a frame before the editor dialog is created.
                            onEdit = { onEdit(current) },
                            onDelete = { onDelete(current) },
                            onMove = { onMove(current) },
                            onRename = { renameText = current.name; showRename = true },
                            menuExpanded = showMenu,
                            onMenuExpanded = { showInfo = false; showMenu = it },
                            onShare = { share(context, current, english) },
                            onCopy = { onCopy(current) },
                            onSettings = current.takeIf { it.isVideo }?.let { { showVideoSettings = true } },
                            onEditTags = onEditTags?.let { action -> { action(current) } },
                            onWallpaper = current.takeUnless(MediaItem::isVideo)?.let {
                                { setWallpaper(context, current, english) }
                            }
                        )
                    }

                    if (showInfo && imageControlsVisible) {
                        MediaInfoPanel(current, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 66.dp, end = 10.dp))
                    }

                }
            }
        }

        if (showRename) {
            VaultTextInputDialog(
                title = appText("重命名", english),
                value = renameText,
                onValueChange = { renameText = it },
                label = appText("文件名", english),
                confirmLabel = appText("保存", english),
                onDismiss = { showRename = false },
                onConfirm = {
                        val newName = renameText.trim()
                        if (newName.isNotEmpty() && newName != current.name) onRename(current, newName)
                        showRename = false
                }
            )
        }

        if (showVideoSettings && current.isVideo) {
            VideoSettingsDialog(onDismiss = { showVideoSettings = false; videoSettingsVersion++ })
        }

    }
}

@Composable
private fun OriginalMediaImage(
    item: MediaItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onLoaded: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        item.uri,
        item.size,
        item.dateModified
    ) {
        value = ThumbnailRepository.loadOriginal(context, item)
    }
    val loaded = bitmap != null
    LaunchedEffect(loaded) {
        if (loaded) onLoaded?.invoke()
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = item.name,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

@Composable
private fun ViewerTopBar(item: MediaItem, position: String, favorite: Boolean, onClose: () -> Unit, onFavorite: () -> Unit, onInfo: () -> Unit) {
    val english = LocalAppEnglish.current
    Row(
        Modifier.fillMaxWidth()
            .height(68.dp)
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, appText("返回", english), tint = Color(0xFF1A1A1A)) }
        Text(
            if (position.isEmpty()) item.name else "${item.name} · $position",
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFF1A1A1A),
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, appText("收藏", english), tint = if (favorite) Color(0xFFFFD60A) else Color(0xFF1A1A1A)) }
        IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, appText("信息", english), tint = Color(0xFF1A1A1A)) }
    }
}

@Composable
private fun ViewerBottomBar(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpanded: (Boolean) -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onSettings: (() -> Unit)?,
    onEditTags: (() -> Unit)?,
    onWallpaper: (() -> Unit)?
) {
    val english = LocalAppEnglish.current
    Row(
        modifier.fillMaxWidth()
            .height(76.dp)
            .background(Color.White)
            .navigationBarsPadding()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ViewerEditAction(appText("编辑", english), onEdit)
        ViewerAction(Icons.Outlined.Delete, appText("移到回收站", english), onDelete)
        ViewerAction(Icons.AutoMirrored.Outlined.DriveFileMove, appText("移动", english), onMove)
        ViewerAction(Icons.Outlined.Edit, appText("重命名", english), onRename)
        Box {
            ViewerAction(Icons.Outlined.MoreVert, appText("菜单", english)) { onMenuExpanded(true) }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpanded(false) },
                modifier = Modifier.width(228.dp).background(Color.White, RoundedCornerShape(14.dp))
            ) {
                DropdownMenuItem(text = { Text(appText("分享", english)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { onMenuExpanded(false); onShare() })
                DropdownMenuItem(text = { Text(appText("复制", english)) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { onMenuExpanded(false); onCopy() })
                onSettings?.let { action ->
                    DropdownMenuItem(text = { Text(appText("设置", english)) }, leadingIcon = { Icon(Icons.Outlined.SettingsIcon, null) }, onClick = { onMenuExpanded(false); action() })
                }
                onEditTags?.let { action ->
                    DropdownMenuItem(text = { Text(if (english) "View/Edit Tags" else "查看/编辑 Tags") }, leadingIcon = { Icon(Icons.Outlined.Label, null) }, onClick = { onMenuExpanded(false); action() })
                }
                onWallpaper?.let { action ->
                    DropdownMenuItem(text = { Text(appText("设置为壁纸", english)) }, leadingIcon = { Icon(Icons.Outlined.Wallpaper, null) }, onClick = { onMenuExpanded(false); action() })
                }
            }
        }
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.height(56.dp).widthIn(min = 56.dp)) {
        Icon(icon, label, tint = Color(0xFF777B79), modifier = Modifier.fillMaxSize(.48f))
    }
}

@Composable
private fun VideoSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val preferences = remember { context.getSharedPreferences("album_settings", Context.MODE_PRIVATE) }
    var autoplay by remember { mutableStateOf(preferences.getBoolean("video_autoplay", true)) }
    var rememberProgress by remember { mutableStateOf(preferences.getBoolean("video_progress", true)) }
    var autoHide by remember { mutableStateOf(preferences.getBoolean("video_auto_hide", true)) }
    var longSkip by remember { mutableStateOf(preferences.getBoolean("long_skip", false)) }
    var edgeProtection by remember { mutableStateOf(preferences.getBoolean("edge_protection", true)) }
    var tapPause by remember { mutableStateOf(preferences.getBoolean("video_tap_pause", false)) }
    var portraitTapPause by remember { mutableStateOf(preferences.getBoolean("video_portrait_tap_pause", false)) }
    var normalSkip by remember { mutableStateOf(preferences.getString("normal_skip", "10秒") ?: "10秒") }
    var longSkipLength by remember { mutableStateOf(preferences.getString("long_skip_length", "30秒") ?: "30秒") }
    var gestureSeek by remember { mutableStateOf(preferences.getString("gesture_seek", "90秒") ?: "90秒") }
    var openChoice by remember { mutableStateOf<String?>(null) }

    fun putBoolean(key: String, value: Boolean) = preferences.edit().putBoolean(key, value).apply()
    fun putString(key: String, value: String) = preferences.edit().putString(key, value).apply()
    val options = when (openChoice) {
        "normal" -> listOf("5秒", "10秒", "15秒", "30秒")
        "long" -> listOf("30秒", "60秒", "90秒", "120秒")
        "gesture" -> listOf("30秒", "60秒", "90秒", "120秒", "150秒")
        else -> emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black.copy(alpha = .88f),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(appText("视频设置", english)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VideoSettingSwitch(appText("打开视频时自动播放", english), autoplay) {
                    autoplay = it; putBoolean("video_autoplay", it)
                }
                VideoSettingSwitch(appText("记住最后一次播放进度", english), rememberProgress) {
                    rememberProgress = it; putBoolean("video_progress", it)
                }
                VideoSettingSwitch(appText("自动隐藏播放器界面", english), autoHide) {
                    autoHide = it; putBoolean("video_auto_hide", it)
                }
                VideoSettingSwitch(appText("长快进", english), longSkip) {
                    longSkip = it; putBoolean("long_skip", it)
                }
                VideoSettingChoice(appText("快进长度", english), normalSkip) { openChoice = "normal" }
                if (longSkip) VideoSettingChoice(appText("长快进长度", english), longSkipLength) { openChoice = "long" }
                VideoSettingChoice(appText("满屏滑动跳过时间", english), gestureSeek) { openChoice = "gesture" }
                VideoSettingSwitch(appText("边缘误触保护", english), edgeProtection) {
                    edgeProtection = it; putBoolean("edge_protection", it)
                }
                VideoSettingSwitch(appText("单击暂停", english), tapPause) {
                    tapPause = it
                    if (!it) portraitTapPause = false
                    putBoolean("video_tap_pause", it)
                    putBoolean("video_portrait_tap_pause", if (!it) false else portraitTapPause)
                }
                VideoSettingSwitch(appText("只在竖屏下单击暂停", english), portraitTapPause) {
                    portraitTapPause = it
                    if (it) tapPause = true
                    putBoolean("video_portrait_tap_pause", it)
                    putBoolean("video_tap_pause", if (it) true else tapPause)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(appText("完成", english)) } }
    )

    if (openChoice != null) {
        AlertDialog(
            onDismissRequest = { openChoice = null },
            containerColor = Color.Black.copy(alpha = .88f),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Text(
                    when (openChoice) {
                        "normal" -> appText("快进长度", english)
                        "long" -> appText("长快进长度", english)
                        else -> appText("满屏滑动跳过时间", english)
                    }
                )
            },
            text = {
                Column {
                    options.forEach { option ->
                        TextButton(
                            onClick = {
                                when (openChoice) {
                                    "normal" -> { normalSkip = option; putString("normal_skip", option) }
                                    "long" -> { longSkipLength = option; putString("long_skip_length", option) }
                                    "gesture" -> { gestureSeek = option; putString("gesture_seek", option) }
                                }
                                openChoice = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(option) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { openChoice = null }) { Text(appText("取消", english)) } }
        )
    }
}

@Composable
private fun VideoSettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun VideoSettingChoice(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ViewerEditAction(label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.height(56.dp).widthIn(min = 56.dp)) {
        ViewerEditIcon(label, Modifier.fillMaxSize(.48f))
    }
}

/** The viewer edit mark from the prototype: an open rounded frame with a diagonal stroke. */
@Composable
private fun ViewerEditIcon(contentDescription: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = size.minDimension
        // Match the lighter stroke weight in the prototype icon.
        val strokeWidth = side * 0.085f
        val left = size.width * 0.18f
        val top = size.height * 0.16f
        val right = size.width * 0.84f
        val bottom = size.height * 0.84f
        val corner = side * 0.18f
        val frame = Path().apply {
            moveTo(size.width * 0.66f, top)
            lineTo(left + corner, top)
            cubicTo(left + corner * 0.45f, top, left, top + corner * 0.45f, left, top + corner)
            lineTo(left, bottom - corner)
            cubicTo(left, bottom - corner * 0.45f, left + corner * 0.45f, bottom, left + corner, bottom)
            lineTo(right - corner, bottom)
            cubicTo(right - corner * 0.45f, bottom, right, bottom - corner * 0.45f, right, bottom - corner)
            lineTo(right, size.height * 0.49f)
        }
        drawPath(
            path = frame,
            color = Color(0xFF777B79),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawLine(
            color = Color(0xFF777B79),
            start = Offset(size.width * 0.48f, size.height * 0.51f),
            end = Offset(size.width * 0.82f, size.height * 0.17f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun NativeVideoPlayer(
    current: MediaItem,
    videos: List<MediaItem>,
    playbackResumeRequest: PlaybackResumeRequest?,
    onPlaybackResumeConsumed: (Long) -> Unit,
    onCurrentChanged: (MediaItem) -> Unit,
    onBack: () -> Unit,
    miniMode: Boolean,
    onMiniModeChange: (Boolean) -> Unit,
    pictureInPictureMode: Boolean,
    onEnterPictureInPicture: () -> Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onShare: () -> Unit
) {
    // Native video stays underneath the Compose interaction surfaces.
    val videoSurfaceZ = -100f
    val gestureSurfaceZ = 0f
    val bufferingSurfaceZ = 800f
    val controlSurfaceZ = 1000f
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val preferences = remember { context.getSharedPreferences("album_settings", Context.MODE_PRIVATE) }
    val rememberProgress = preferences.getBoolean("video_progress", true)
    val seekIncrement = (preferences.getString("normal_skip", "10秒")?.filter(Char::isDigit)?.toLongOrNull() ?: 10L) * 1000L
    val longSeekIncrement = (preferences.getString("long_skip_length", "30秒")?.filter(Char::isDigit)?.toLongOrNull() ?: 30L) * 1000L
    val showLongSkip = preferences.getBoolean("long_skip", false)
    val gestureSeekDuration = (preferences.getString("gesture_seek", "90秒")?.filter(Char::isDigit)?.toLongOrNull() ?: 90L) * 1000L
    val edgeProtection = preferences.getBoolean("edge_protection", true)
    var controlsLocked by remember { mutableStateOf(false) }
    var playbackMode by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var speedDialogWasPlaying by remember { mutableStateOf(false) }
    var orientationMode by remember { mutableIntStateOf(0) }
    var gestureTarget by remember { mutableStateOf<Long?>(null) }
    var gestureHud by remember { mutableStateOf<String?>(null) }
    var gestureViewportWidth by remember { mutableIntStateOf(0) }
    var gestureViewportHeight by remember { mutableIntStateOf(0) }
    var gestureBrightness by remember {
        mutableFloatStateOf(
            ((context as? Activity)?.window?.attributes?.screenBrightness ?: .5f)
                .takeIf { it >= 0f } ?: .5f
        )
    }
    var gestureVolume by remember {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val maxVolume = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
        val currentVolume = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: maxVolume
        mutableFloatStateOf((currentVolume.toFloat() / maxVolume).coerceIn(0f, 1f))
    }
    var backgroundServiceStarted by remember { mutableStateOf(false) }
    var videoControlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var playerPosition by remember { mutableLongStateOf(0L) }
    var playerDuration by remember { mutableLongStateOf(0L) }
    var playerPlaying by remember { mutableStateOf(false) }
    var playerBuffering by remember { mutableStateOf(false) }
    var renderRecoveryAttempted by remember { mutableStateOf(false) }
    var miniRootSize by remember { mutableStateOf(IntSize.Zero) }
    var miniWidthPx by remember { mutableFloatStateOf(0f) }
    var miniOffset by remember { mutableStateOf(Offset.Zero) }
    var miniPositionInitialized by remember { mutableStateOf(false) }
    var composeRootWindowTop by remember { mutableFloatStateOf(0f) }
    var composeRootWindowLeft by remember { mutableFloatStateOf(0f) }
    var playerWindowTop by remember { mutableFloatStateOf(0f) }
    var playerWindowLeft by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val gestureScope = rememberCoroutineScope()
    val latestGestureBrightness by rememberUpdatedState(gestureBrightness)
    val latestGestureVolume by rememberUpdatedState(gestureVolume)
    val latestPlaybackMode by rememberUpdatedState(playbackMode)
    val activity = context as? Activity
    val initialOrientation = remember(activity) { activity?.requestedOrientation }
    val initialScreenBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness }
    val initialMediaVolume = remember(context) {
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.getStreamVolume(AudioManager.STREAM_MUSIC)
    }
    var restoreScreenBrightness by remember(activity) { mutableStateOf(initialScreenBrightness) }
    var restoreMediaVolume by remember(context) { mutableStateOf(initialMediaVolume) }
    var restoreSystemBrightnessOnExit by remember { mutableStateOf(false) }
    var hasLeftPlayer by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Keep the app portrait; the HTML-style player rotates its own canvas
        // for manual landscape mode instead of rotating the device window.
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    val player = remember(videos) {
        ExoPlayer.Builder(
            context,
            DefaultRenderersFactory(context)
                // Prefer Android's software codec on emulator/device variants
                // whose hardware codec can output corrupted frames. Media3
                // still falls back to hardware when no software decoder exists.
                .setMediaCodecSelector(MediaCodecSelector.PREFER_SOFTWARE)
                .setEnableDecoderFallback(true)
                .forceDisableMediaCodecAsynchronousQueueing()
        )
            .setSeekBackIncrementMs(seekIncrement)
            .setSeekForwardIncrementMs(seekIncrement)
            .build().apply {
            setSeekParameters(SeekParameters.EXACT)
            setMediaItems(videos.map { Builder().setUri(it.uri).setMediaId(it.uri.toString()).build() })
            val initialIndex = videos.indexOfFirst { it.uri == current.uri }.coerceAtLeast(0)
            val resume = playbackResumeRequest?.takeIf { it.uri == current.uri.toString() }
            val savedPosition = resume?.positionMs
                ?: if (rememberProgress) preferences.getLong(progressKey(current), 0L) else 0L
            seekTo(initialIndex, savedPosition)
            prepare()
            playWhenReady = resume?.playWhenReady ?: preferences.getBoolean("video_autoplay", true)
        }
    }
    val renderPlayer = player
    var nativePlayerView by remember { mutableStateOf<VideoPlayerView?>(null) }
    val overlayBounds = remember { mutableStateMapOf<Int, RectF>() }
    val overlayRootBounds = remember { mutableStateMapOf<Int, RectF>() }
    fun syncOverlayBounds(view: VideoPlayerView) {
        overlayBounds.forEach { (action, bounds) -> view.setOverlayRegion(action, bounds) }
    }
    fun registerHit(action: Int): Modifier = Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val rect = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
        overlayBounds[action] = rect
        val root = coordinates.boundsInRoot()
        overlayRootBounds[action] = RectF(root.left, root.top, root.right, root.bottom)
        nativePlayerView?.setOverlayRegion(action, rect)
    }
    val dynamicHitRegions = overlayRootBounds.toMap()
    LaunchedEffect(playbackResumeRequest?.requestId) {
        playbackResumeRequest?.takeIf { it.uri == current.uri.toString() }?.let { request ->
            context.stopService(Intent(context, MediaPlaybackService::class.java))
            if (player.currentMediaItemIndex != videos.indexOfFirst { it.uri.toString() == request.uri }) {
                val index = videos.indexOfFirst { it.uri.toString() == request.uri }
                if (index >= 0) player.seekTo(index, request.positionMs)
            } else {
                player.seekTo(request.positionMs)
            }
            player.playWhenReady = request.playWhenReady
            onPlaybackResumeConsumed(request.requestId)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (renderRecoveryAttempted) return
                renderRecoveryAttempted = true
                val index = player.currentMediaItemIndex.coerceAtLeast(0)
                val position = player.currentPosition.coerceAtLeast(0L)
                player.stop()
                player.seekTo(index, position)
                player.prepare()
                player.playWhenReady = true
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                videos.firstOrNull { it.uri.toString() == mediaItem?.mediaId }?.let { changed ->
                    renderRecoveryAttempted = false
                    onCurrentChanged(changed)
                    if (rememberProgress) {
                        val saved = preferences.getLong(progressKey(changed), 0L)
                        val restored = resumePosition(saved, player.duration)
                        if (restored > 0L) player.seekTo(restored)
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (rememberProgress) {
                    videos.getOrNull(oldPosition.mediaItemIndex)?.let { previous ->
                        val persisted = if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                            0L
                        } else {
                            positionForPersistence(
                                positionMs = oldPosition.positionMs,
                                durationMs = 0L,
                                playbackEnded = player.playbackState == Player.STATE_ENDED
                            )
                        }
                        preferences.edit().putLong(progressKey(previous), persisted).apply()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && rememberProgress) {
                    val restored = resumePosition(player.currentPosition, player.duration)
                    if (player.currentPosition > 0L && restored == 0L) player.seekTo(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    val endedIndex = player.currentMediaItemIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
                    videos.getOrNull(endedIndex)?.let { ended ->
                        preferences.edit().putLong(progressKey(ended), 0L).apply()
                    }
                    when {
                        videos.isEmpty() || latestPlaybackMode == 3 -> player.pause()
                        latestPlaybackMode == 1 -> { player.seekTo(endedIndex, 0L); player.play() }
                        latestPlaybackMode == 2 -> { player.seekTo((endedIndex + 1) % videos.size, 0L); player.play() }
                        else -> { player.seekTo((endedIndex + 1) % videos.size, 0L); player.play() }
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            if (rememberProgress) {
                videos.getOrNull(player.currentMediaItemIndex)?.let { playing ->
                    val persisted = positionForPersistence(
                        positionMs = player.currentPosition,
                        durationMs = player.duration,
                        playbackEnded = player.playbackState == Player.STATE_ENDED
                    )
                    preferences.edit().putLong(progressKey(playing), persisted).apply()
                }
            }
            player.removeListener(listener)
            player.release()
            activity?.let { host ->
                host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                if (restoreSystemBrightnessOnExit) {
                    val attributes = host.window.attributes
                    attributes.screenBrightness = -1f
                    host.window.attributes = attributes
                } else restoreScreenBrightness?.let { brightness ->
                    val attributes = host.window.attributes
                    attributes.screenBrightness = brightness
                    host.window.attributes = attributes
                }
                restoreMediaVolume?.let { volume ->
                    (host.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                        ?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                }
            }
        }
    }

    DisposableEffect(player, backgroundServiceStarted) {
        val activity = context as? androidx.activity.ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasLeftPlayer = true
                Lifecycle.Event.ON_RESUME -> {
                    if (hasLeftPlayer) {
                        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        restoreMediaVolume = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: restoreMediaVolume
                        try {
                            val systemBrightness = Settings.System.getInt(
                                context.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS
                            )
                            restoreScreenBrightness = (systemBrightness / 255f).coerceIn(0f, 1f)
                            restoreSystemBrightnessOnExit = true
                        } catch (_: SecurityException) {
                            // Keep the original window value if the system setting is unavailable.
                        }
                        hasLeftPlayer = false
                    }
                    if (backgroundServiceStarted) {
                        context.stopService(Intent(context, MediaPlaybackService::class.java))
                        val restored = preferences.getLong(progressKey(current), player.currentPosition)
                        player.seekTo(restored)
                        player.play()
                        backgroundServiceStarted = false
                    }
                }
                else -> Unit
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(playbackMode) {
        when (playbackMode) {
            0 -> { player.repeatMode = Player.REPEAT_MODE_OFF; player.shuffleModeEnabled = false }
            1 -> { player.repeatMode = Player.REPEAT_MODE_OFF; player.shuffleModeEnabled = false }
            2 -> { player.repeatMode = Player.REPEAT_MODE_OFF; player.shuffleModeEnabled = true }
            else -> { player.repeatMode = Player.REPEAT_MODE_OFF; player.shuffleModeEnabled = false }
        }
    }

    LaunchedEffect(player) {
        while (true) {
            playerPosition = player.currentPosition.coerceAtLeast(0L)
            playerDuration = player.duration.takeIf { it > 0L } ?: 0L
            playerPlaying = player.isPlaying
            playerBuffering = player.playbackState == Player.STATE_BUFFERING
            delay(200L)
        }
    }

    fun refreshControls() {
        videoControlsVisible = true
        controlsInteraction++
    }

    LaunchedEffect(videoControlsVisible, controlsLocked, controlsInteraction) {
        if (videoControlsVisible && !controlsLocked && preferences.getBoolean("video_auto_hide", true)) {
            delay(3000L)
            videoControlsVisible = false
        }
    }

    LaunchedEffect(pictureInPictureMode) {
        if (pictureInPictureMode) {
            videoControlsVisible = false
            gestureHud = null
            gestureTarget = null
        }
    }

    LaunchedEffect(gestureHud) {
        if (gestureHud != null) {
            delay(700L)
            gestureHud = null
        }
    }

    fun cycleOrientation() {
        orientationMode = (orientationMode + 1) % 3
        Toast.makeText(context, appText(listOf("自适应", "横屏", "竖屏")[orientationMode], english), Toast.LENGTH_SHORT).show()
    }

    fun restartOrTogglePlayback() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekToDefaultPosition(player.currentMediaItemIndex)
            }
            player.play()
        }
    }

    fun seekAdjacent(next: Boolean) {
        if (videos.isEmpty()) return
        val currentIndex = player.currentMediaItemIndex.coerceIn(0, videos.lastIndex.coerceAtLeast(0))
        val targetIndex = if (next) (currentIndex + 1) % videos.size else (currentIndex - 1 + videos.size) % videos.size
        if (targetIndex != C.INDEX_UNSET) {
            player.seekTo(targetIndex, 0L)
            player.playWhenReady = true
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
        } else {
            Toast.makeText(
                context,
                appText(if (next) "已经是最后一个视频" else "已经是第一个视频", english),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun invokeComposeHit(action: Int) {
        when (action) {
            VideoPlayerView.HIT_BACK -> onBack()
            VideoPlayerView.HIT_FAVORITE -> onFavorite()
            VideoPlayerView.HIT_SPEED -> {
                speedDialogWasPlaying = player.isPlaying
                refreshControls()
                showSpeedDialog = true
            }
            VideoPlayerView.HIT_SHARE -> onShare()
            VideoPlayerView.HIT_PREVIOUS -> { refreshControls(); seekAdjacent(false) }
            VideoPlayerView.HIT_PLAY_PAUSE -> { refreshControls(); restartOrTogglePlayback() }
            VideoPlayerView.HIT_NEXT -> { refreshControls(); seekAdjacent(true) }
            VideoPlayerView.HIT_ORIENTATION -> { refreshControls(); cycleOrientation() }
        }
    }

    fun updateGestureBrightness(value: Float) {
        gestureBrightness = value.coerceIn(0f, 1f)
        val activity = context as? Activity ?: return
        val attributes = activity.window.attributes
        attributes.screenBrightness = gestureBrightness.coerceAtLeast(.01f)
        activity.window.attributes = attributes
    }

    fun updateGestureVolume(value: Float) {
        gestureVolume = value.coerceIn(0f, 1f)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        audio.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (gestureVolume * maxVolume).roundToInt().coerceIn(0, maxVolume),
            0
        )
    }

    fun startBackgroundPlayback() {
        preferences.edit().putLong(progressKey(current), player.currentPosition).apply()
        val service = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_START
            putExtra(MediaPlaybackService.EXTRA_URI, current.uri.toString())
            putExtra(MediaPlaybackService.EXTRA_NAME, current.name)
            putExtra(MediaPlaybackService.EXTRA_POSITION, player.currentPosition)
        }
        runCatching { ContextCompat.startForegroundService(context, service) }.onFailure {
            Toast.makeText(context, "无法启动后台播放", Toast.LENGTH_SHORT).show()
            return
        }
        backgroundServiceStarted = true
        player.pause()
        Toast.makeText(context, appText("视频正在后台播放", english), Toast.LENGTH_SHORT).show()
        onBack()
    }

    LaunchedEffect(miniMode, miniRootSize) {
        if (!miniMode || miniRootSize == IntSize.Zero) return@LaunchedEffect
        val targetWidth = with(density) { 250.dp.toPx() }
            .coerceAtMost((miniRootSize.width - with(density) { 24.dp.toPx() }).coerceAtLeast(1f))
        if (!miniPositionInitialized) {
            miniWidthPx = targetWidth
            val margin = with(density) { 12.dp.toPx() }
            val bottom = with(density) { 84.dp.toPx() }
            miniOffset = Offset(
                x = (miniRootSize.width - targetWidth - margin).coerceAtLeast(margin),
                y = (miniRootSize.height - targetWidth * 9f / 16f - bottom).coerceAtLeast(margin)
            )
            miniPositionInitialized = true
        } else {
            val height = miniWidthPx * 9f / 16f
            miniOffset = Offset(
                miniOffset.x.coerceIn(8f, (miniRootSize.width - miniWidthPx - 8f).coerceAtLeast(8f)),
                miniOffset.y.coerceIn(8f, (miniRootSize.height - height - 8f).coerceAtLeast(8f))
            )
        }
    }

    Box(
        Modifier.fillMaxSize().background(if (miniMode) Color.Transparent else Color.Black)
            .onSizeChanged { miniRootSize = it }
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                composeRootWindowLeft = bounds.left
                composeRootWindowTop = bounds.top
            }
    ) {
        if (miniMode && miniPositionInitialized) {
            MiniVideoPlayer(
                player = player,
                playing = playerPlaying,
                widthPx = miniWidthPx,
                offset = miniOffset,
                seekIncrement = seekIncrement,
                onMove = { delta ->
                    val height = miniWidthPx * 9f / 16f
                    miniOffset = Offset(
                        (miniOffset.x + delta.x).coerceIn(8f, (miniRootSize.width - miniWidthPx - 8f).coerceAtLeast(8f)),
                        (miniOffset.y + delta.y).coerceIn(8f, (miniRootSize.height - height - 8f).coerceAtLeast(8f))
                    )
                },
                onResize = { delta, fromLeft, fromTop ->
                    val ratio = 16f / 9f
                    val horizontalDelta = if (fromLeft) -delta.x else delta.x
                    val verticalDelta = (if (fromTop) -delta.y else delta.y) * ratio
                    val sizeDelta = if (abs(horizontalDelta) >= abs(verticalDelta)) horizontalDelta else verticalDelta
                    val oldWidth = miniWidthPx
                    val oldHeight = oldWidth / ratio
                    val oldRight = miniOffset.x + oldWidth
                    val oldBottom = miniOffset.y + oldHeight
                    val minimum = min(with(density) { 180.dp.toPx() }, (miniRootSize.width - 16f).coerceAtLeast(1f))
                    val maximum = min(
                        (miniRootSize.width - 16f).coerceAtLeast(minimum),
                        ((miniRootSize.height - 16f) * ratio).coerceAtLeast(minimum)
                    )
                    val newWidth = (oldWidth + sizeDelta).coerceIn(minimum, maximum)
                    val newHeight = newWidth / ratio
                    miniWidthPx = newWidth
                    miniOffset = Offset(
                        (if (fromLeft) oldRight - newWidth else miniOffset.x)
                            .coerceIn(8f, (miniRootSize.width - newWidth - 8f).coerceAtLeast(8f)),
                        (if (fromTop) oldBottom - newHeight else miniOffset.y)
                            .coerceIn(8f, (miniRootSize.height - newHeight - 8f).coerceAtLeast(8f))
                    )
                },
                onBackground = ::startBackgroundPlayback,
                onRestore = { onMiniModeChange(false) },
                onClose = onBack
            )
            return@Box
        }

        Box(
            Modifier.fillMaxSize()
                .zIndex(gestureSurfaceZ)
            .onSizeChanged {
                gestureViewportWidth = it.width
                gestureViewportHeight = it.height
            }
            .pointerInput(player, controlsLocked, gestureSeekDuration, edgeProtection) {
                var lastTapAt = 0L
                var lastTapPosition = Offset.Zero
                var pendingTapJob: kotlinx.coroutines.Job? = null
                awaitEachGesture {
                    // Let top-bar buttons receive the gesture first. The full-screen
                    // gesture layer only handles unconsumed touches from the video.
                    val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Final)
                    val pointerId = down.id
                    if (controlsLocked) {
                        do {
                            val lockedEvent = awaitPointerEvent()
                            val lockedChange = lockedEvent.changes.firstOrNull { it.id == pointerId }
                        } while (lockedChange?.pressed == true)
                        return@awaitEachGesture
                    }
                    val start = down.position
                    val wasPlaying = player.isPlaying
                    val startTime = player.currentPosition
                    val startBrightness = latestGestureBrightness
                    val startVolume = latestGestureVolume
                    var mode = "pending"
                    var targetTime = startTime
                    var lastPosition = start
                    var longPressActive = false
                    var finished = false

                    while (!finished) {
                        val event = if (mode == "pending") {
                            withTimeoutOrNull(460L) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            mode = "long-press"
                            longPressActive = true
                            player.setPlaybackSpeed(2f)
                            if (!player.isPlaying) player.play()
                            gestureHud = "2x"
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null) {
                            finished = true
                            continue
                        }
                        val position = change.position
                        val deltaX = position.x - start.x
                        val deltaY = position.y - start.y
                        lastPosition = position
                        if (mode == "pending" && hypot(deltaX.toDouble(), deltaY.toDouble()) > 10.0) {
                            mode = if (abs(deltaX) >= abs(deltaY)) {
                                if (wasPlaying) player.pause()
                                "seek"
                            } else if (start.x < gestureViewportWidth / 2f) {
                                "brightness"
                            } else {
                                "volume"
                            }
                        }
                        when (mode) {
                            "seek" -> {
                                change.consume()
                                val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                targetTime = frameAlignedPosition(
                                    player,
                                    (startTime + (deltaX / gestureViewportWidth.coerceAtLeast(1) * gestureSeekDuration).toLong())
                                        .coerceIn(0L, duration)
                                )
                                val offset = ((targetTime - startTime) / 1000L)
                                gestureTarget = targetTime
                                gestureHud = "${if (offset >= 0) "+" else ""}${offset}秒  ${formatPlayerTime(targetTime)}"
                            }
                            "brightness" -> {
                                change.consume()
                                val next = (startBrightness - deltaY / gestureViewportHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
                                updateGestureBrightness(next)
                                gestureHud = "亮度 ${roundToInt(next * 100f)}%"
                            }
                            "volume" -> {
                                change.consume()
                                val next = (startVolume - deltaY / gestureViewportHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
                                updateGestureVolume(next)
                                gestureHud = "音量 ${roundToInt(next * 100f)}%"
                            }
                        }
                        if (!change.pressed) finished = true
                    }

                    when (mode) {
                        "long-press" -> {
                            player.setPlaybackSpeed(speed)
                            if (!wasPlaying) player.pause()
                            gestureHud = if (speed == 1f) "1x" else "${speed}x"
                        }
                        "seek" -> {
                            val edge = 24.dp.toPx()
                            val touchesEdge = start.x <= edge || start.x >= gestureViewportWidth - edge ||
                                lastPosition.x <= edge || lastPosition.x >= gestureViewportWidth - edge
                            if (edgeProtection && touchesEdge) {
                                if (wasPlaying) player.play()
                                gestureHud = "已取消跳转"
                            } else {
                                seekToVideoFrame(player, targetTime)
                                if (wasPlaying) player.play() else player.pause()
                                gestureHud = formatPlayerTime(targetTime)
                            }
                            gestureTarget = null
                        }
                        "brightness", "volume" -> Unit
                        "pending" -> {
                            val now = android.os.SystemClock.uptimeMillis()
                            val isDoubleTap = now - lastTapAt < 300L &&
                                hypot((start.x - lastTapPosition.x).toDouble(), (start.y - lastTapPosition.y).toDouble()) < 42.0
                            if (isDoubleTap) {
                                pendingTapJob?.cancel()
                                lastTapAt = 0L
                                val zone = start.x / gestureViewportWidth.coerceAtLeast(1)
                                if (zone in (1f / 3f)..(2f / 3f) && player.playbackState == Player.STATE_ENDED) {
                                    player.seekTo(0L)
                                    player.play()
                                    gestureHud = "从头播放"
                                } else if (player.isPlaying && zone in (1f / 3f)..(2f / 3f)) {
                                    restartOrTogglePlayback()
                                    gestureHud = "暂停"
                                } else if (!player.isPlaying && zone in (1f / 3f)..(2f / 3f)) {
                                    restartOrTogglePlayback()
                                    gestureHud = "播放"
                                } else if (zone < 1f / 3f) {
                                    seekToVideoFrame(player, player.currentPosition - seekIncrement)
                                    gestureHud = "快退 ${seekIncrement / 1000L}秒"
                                } else {
                                    val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                    seekToVideoFrame(player, player.currentPosition + seekIncrement)
                                    gestureHud = "快进 ${seekIncrement / 1000L}秒"
                                }
                            } else {
                                lastTapAt = now
                                lastTapPosition = start
                                pendingTapJob = gestureScope.launch {
                                    delay(300L)
                                    if (lastTapAt == now) {
                                        lastTapAt = 0L
                                        if (!controlsLocked) videoControlsVisible = !videoControlsVisible
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) {
        AndroidView(
            factory = { viewContext ->
                (PlayerView.inflate(viewContext, com.example.album.R.layout.view_video_player, null) as VideoPlayerView).apply {
                    nativePlayerView = this
                    syncOverlayBounds(this)
                    this.player = renderPlayer
                    this.controlsHitEnabled = false
                    this.onOverlayBack = onBack
                    this.onOverlayFavorite = onFavorite
                    this.onOverlaySpeed = {
                        speedDialogWasPlaying = player.isPlaying
                        refreshControls()
                        showSpeedDialog = true
                    }
                    this.onOverlayShare = onShare
                    this.onOverlayPrevious = { refreshControls(); seekAdjacent(false) }
                    this.onOverlayNext = { refreshControls(); seekAdjacent(true) }
                    this.onOverlayPlayPause = { refreshControls(); restartOrTogglePlayback() }
                    this.onOverlayOrientation = { refreshControls(); cycleOrientation() }
                }
            },
            update = { view ->
                (view as? VideoPlayerView)?.apply {
                    nativePlayerView = this
                    syncOverlayBounds(this)
                    this.player = renderPlayer
                    this.controlsHitEnabled = false
                } ?: run { view.player = renderPlayer }
            },
            modifier = Modifier.fillMaxSize().zIndex(videoSurfaceZ).onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                playerWindowLeft = bounds.left
                playerWindowTop = bounds.top
            }
        )
        if (playerBuffering && !miniMode) {
            CircularProgressIndicator(
                modifier = Modifier.size(42.dp).align(Alignment.Center).zIndex(bufferingSurfaceZ),
                color = Color.White,
                trackColor = Color.Transparent,
                strokeWidth = 4.dp
            )
        }
        if (!controlsLocked) {
            AnimatedVisibility(videoControlsVisible && !pictureInPictureMode, modifier = Modifier.zIndex(controlSurfaceZ), enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                Row(
                    Modifier.fillMaxWidth().height(70.dp)
                        .offset {
                            IntOffset(
                                (playerWindowLeft - composeRootWindowLeft).roundToInt(),
                                (playerWindowTop - composeRootWindowTop).roundToInt()
                            )
                        }
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .70f), Color.Transparent)))
                        .padding(horizontal = 10.dp)
                        .zIndex(controlSurfaceZ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).then(registerHit(VideoPlayerView.HIT_BACK))) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, appText("返回", english), tint = Color.White) }
                    Text(current.name, Modifier.weight(1f), color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${videos.indexOfFirst { it.uri == current.uri } + 1}/${videos.size}", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    Box(Modifier.size(46.dp).then(registerHit(VideoPlayerView.HIT_SPEED)).clickable {
                        speedDialogWasPlaying = player.isPlaying
                        refreshControls()
                        showSpeedDialog = true
                    }, contentAlignment = Alignment.Center) {
                        Text(if (speed == 1f) "1x" else "${speed}x", color = Color.White, fontSize = 13.sp)
                    }
                    IconButton(onClick = onFavorite, modifier = Modifier.size(46.dp).then(registerHit(VideoPlayerView.HIT_FAVORITE))) { Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, appText("收藏", english), tint = if (favorite) Color(0xFFFFD60A) else Color.White, modifier = Modifier.size(25.dp)) }
                    IconButton(onClick = onShare, modifier = Modifier.size(46.dp).then(registerHit(VideoPlayerView.HIT_SHARE))) { Icon(Icons.Outlined.Share, appText("分享", english), tint = Color.White, modifier = Modifier.size(24.dp)) }
                }
            }
            AnimatedVisibility(videoControlsVisible && !pictureInPictureMode, modifier = Modifier.align(Alignment.CenterStart).zIndex(controlSurfaceZ), enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VideoTool(Icons.Outlined.PictureInPictureAlt, appText("画中画", english)) {
                        if (!onEnterPictureInPicture()) onMiniModeChange(true)
                    }
                    VideoTool(Icons.Outlined.Headphones, appText("后台播放", english), onClick = ::startBackgroundPlayback)
                }
            }
            AnimatedVisibility(videoControlsVisible && !pictureInPictureMode, modifier = Modifier.align(Alignment.CenterEnd).zIndex(controlSurfaceZ), enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                VideoTool(Icons.Outlined.LockOpen, appText("锁定控制", english), Modifier.padding(end = 12.dp)) { refreshControls(); controlsLocked = true }
            }
            AnimatedVisibility(videoControlsVisible && !pictureInPictureMode, modifier = Modifier.align(Alignment.BottomCenter).zIndex(controlSurfaceZ), enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f))))
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatPlayerTime(playerPosition), color = Color.White, fontSize = 12.sp, modifier = Modifier.width(48.dp))
                        Slider(
                            value = playerPosition.toFloat().coerceIn(0f, playerDuration.coerceAtLeast(1L).toFloat()),
                            onValueChange = {
                                playerPosition = frameAlignedPosition(player, it.toLong())
                                seekToVideoFrame(player, playerPosition)
                            },
                            valueRange = 0f..playerDuration.coerceAtLeast(1L).toFloat(),
                            modifier = Modifier.weight(1f).height(18.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = .28f),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            ),
                            thumb = {
                                Box(
                                    Modifier.size(12.dp)
                                        .background(Color.White, CircleShape)
                                )
                            }
                        )
                        Text(formatPlayerTime(playerDuration), color = Color.White, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(48.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        VideoTool(playbackModeIcon(playbackMode), playbackModeLabel(playbackMode, english), Modifier.weight(1f)) {
                            refreshControls()
                            playbackMode = (playbackMode + 1) % 4
                            Toast.makeText(context, playbackModeLabel(playbackMode, english), Toast.LENGTH_SHORT).show()
                        }
                        VideoTool(Icons.Outlined.SkipPrevious, appText("上一个视频", english), Modifier.weight(1f).then(registerHit(VideoPlayerView.HIT_PREVIOUS))) { refreshControls(); seekAdjacent(next = false) }
                        if (showLongSkip) VideoTool(Icons.Outlined.FastRewind, appText("长快退", english), Modifier.weight(1f)) { seekToVideoFrame(player, player.currentPosition - longSeekIncrement) }
                        VideoTool(Icons.Outlined.FastRewind, appText("快退", english), Modifier.weight(1f)) { refreshControls(); seekToVideoFrame(player, player.currentPosition - seekIncrement) }
                        VideoTool(if (playerPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, appText(if (playerPlaying) "暂停" else "播放", english), Modifier.weight(1f).then(registerHit(VideoPlayerView.HIT_PLAY_PAUSE)), 38.dp) {
                            refreshControls(); restartOrTogglePlayback()
                            playerPlaying = player.isPlaying
                        }
                        VideoTool(Icons.Outlined.FastForward, appText("快进", english), Modifier.weight(1f)) { refreshControls(); seekToVideoFrame(player, player.currentPosition + seekIncrement) }
                        if (showLongSkip) VideoTool(Icons.Outlined.FastForward, appText("长快进", english), Modifier.weight(1f)) {
                            val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                            seekToVideoFrame(player, player.currentPosition + longSeekIncrement)
                        }
                        VideoTool(Icons.Outlined.SkipNext, appText("下一个视频", english), Modifier.weight(1f).then(registerHit(VideoPlayerView.HIT_NEXT))) { refreshControls(); seekAdjacent(next = true) }
                        VideoTool(Icons.Outlined.ScreenRotation, appText("旋转方向", english), Modifier.weight(1f).then(registerHit(VideoPlayerView.HIT_ORIENTATION))) { refreshControls(); cycleOrientation() }
                    }
                }
            }
        } else if (!pictureInPictureMode) {
            VideoTool(Icons.Outlined.Lock, appText("解锁控制", english), Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
                controlsLocked = false
                videoControlsVisible = true
            }
        }
        // Route taps from the same Compose layout nodes that draw the icons.
        // This avoids any fixed dp/column arithmetic and follows the actual
        // icon bounds after insets, rotation, or window resizing.
        if (videoControlsVisible && !pictureInPictureMode && !controlsLocked && dynamicHitRegions.isNotEmpty()) {
            Box(
                Modifier.fillMaxSize().zIndex(controlSurfaceZ + 1f).pointerInput(dynamicHitRegions) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val hit = dynamicHitRegions.entries.firstOrNull { it.value.contains(down.position.x, down.position.y) }?.key
                        if (hit != null) {
                            down.consume()
                            var released = false
                            while (!released) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    released = true
                                    invokeComposeHit(hit)
                                }
                            }
                        }
                    }
                }
            )
        }
        val hudText = if (pictureInPictureMode) null else gestureHud ?: gestureTarget?.let(::formatPlayerTime)
        hudText?.let { text ->
            Surface(
                modifier = Modifier.align(Alignment.Center).zIndex(bufferingSurfaceZ),
                color = Color.Black.copy(alpha = .72f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
        }
    }

    if (showSpeedDialog) {
        LaunchedEffect(Unit) {
            if (speedDialogWasPlaying) player.pause()
        }
        val speedOptions = remember { listOf("0.25x", "0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x", "4x") }
        VaultWheelChoiceSheet(
            title = appText("播放速度", english),
            options = speedOptions,
            selected = if (speed == 1f) "1x" else "${speed}x",
            onDismiss = {
                showSpeedDialog = false
                if (speedDialogWasPlaying) player.play()
            },
            onApply = { selected ->
                speed = selected.removeSuffix("x").toFloatOrNull() ?: 1f
                player.setPlaybackSpeed(speed)
                showSpeedDialog = false
                if (speedDialogWasPlaying) player.play()
            }
        )
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun MiniVideoPlayer(
    player: ExoPlayer,
    playing: Boolean,
    widthPx: Float,
    offset: Offset,
    seekIncrement: Long,
    onMove: (Offset) -> Unit,
    onResize: (Offset, Boolean, Boolean) -> Unit,
    onBackground: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit
) {
    val english = LocalAppEnglish.current
    val density = LocalDensity.current
    val widthDp = with(density) { widthPx.toDp() }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnResize by rememberUpdatedState(onResize)
    val renderPlayer = player
    Box(
        Modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .width(widthDp)
            .aspectRatio(16f / 9f)
            .shadow(14.dp, RoundedCornerShape(7.dp), ambientColor = Color.Black.copy(alpha = .34f), spotColor = Color.Black.copy(alpha = .34f))
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { viewContext ->
                (PlayerView.inflate(viewContext, com.example.album.R.layout.view_video_player, null) as PlayerView).apply { this.player = renderPlayer }
            },
            update = { view -> view.player = renderPlayer },
            modifier = Modifier.fillMaxSize().zIndex(-100f)
        )
        Box(
            Modifier.fillMaxSize().zIndex(0f).pointerInput(Unit) {
                var resizeFromLeft: Boolean? = null
                var resizeFromTop: Boolean? = null
                detectDragGestures(
                    onDragStart = { start ->
                        val edge = 28.dp.toPx()
                        val fromLeft = start.x <= edge
                        val fromRight = start.x >= size.width - edge
                        val fromTop = start.y <= edge
                        val fromBottom = start.y >= size.height - edge
                        if ((fromLeft || fromRight) && (fromTop || fromBottom)) {
                            resizeFromLeft = fromLeft
                            resizeFromTop = fromTop
                        }
                    },
                    onDragEnd = { resizeFromLeft = null; resizeFromTop = null },
                    onDragCancel = { resizeFromLeft = null; resizeFromTop = null },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val fromLeft = resizeFromLeft
                        val fromTop = resizeFromTop
                        if (fromLeft != null && fromTop != null) {
                            currentOnResize(Offset(dragAmount.x, dragAmount.y), fromLeft, fromTop)
                        } else {
                            currentOnMove(Offset(dragAmount.x, dragAmount.y))
                        }
                    }
                )
            }
        )
        Row(
            Modifier.align(Alignment.TopEnd)
                .zIndex(1000f)
                .background(Color.Black.copy(alpha = .52f), RoundedCornerShape(bottomStart = 10.dp))
                .padding(start = 5.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MiniVideoButton(Icons.Outlined.Minimize, appText("后台播放", english), onBackground)
            MiniVideoButton(Icons.Outlined.Fullscreen, appText("恢复全屏播放", english), onRestore)
            MiniVideoButton(Icons.Outlined.Close, appText("关闭", english), onClose)
        }
        Row(
            Modifier.align(Alignment.BottomCenter)
                .zIndex(1000f)
                .background(Color.Black.copy(alpha = .58f), RoundedCornerShape(18.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniVideoButton(Icons.Outlined.FastRewind, appText("快退", english)) {
                seekToVideoFrame(player, player.currentPosition - seekIncrement)
            }
            MiniVideoButton(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, appText(if (playing) "暂停" else "播放", english)) {
                if (player.isPlaying) player.pause() else player.play()
            }
            MiniVideoButton(Icons.Outlined.FastForward, appText("快进", english)) {
                seekToVideoFrame(player, player.currentPosition + seekIncrement)
            }
        }
    }
}

@Composable
private fun MiniVideoButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = .38f), CircleShape)
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun VideoTool(icon: ImageVector, label: String, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 25.dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.height(46.dp)) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

private fun playbackModeLabel(mode: Int, english: Boolean): String = appText(when (mode) {
    1 -> "循环播放"
    2 -> "随机播放"
    3 -> "播完暂停"
    else -> "顺序播放"
}, english)

private fun playbackModeIcon(mode: Int): ImageVector = when (mode) {
    2 -> Icons.Outlined.Shuffle
    3 -> Icons.Outlined.StopCircle
    else -> Icons.Outlined.Repeat
}

private fun formatPlayerTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}

private fun roundToInt(value: Float): Int = value.toInt()

@Composable
private fun MediaInfoPanel(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val details by produceState<MediaDetails?>(null, item.uri) {
        value = withContext(Dispatchers.IO) { readDetails(context, item) }
    }
    Surface(
        modifier = modifier.width(228.dp),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 10.dp,
        color = Color.White
    ) {
        Text(
            text = buildString {
                append(if (english) "Name: " else "名称：")
                append(item.name)
                append('\n')
                append(if (english) "Dimensions: " else "尺寸：")
                append(details?.dimensions ?: "…")
                append('\n')
                append(if (english) "Type: " else "类型：")
                append(item.mimeType)
                append('\n')
                append(if (english) "Location: " else "地址：")
                append(item.displayAddress())
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            color = Color(0xFF1C1C1E),
            fontSize = 13.sp,
            lineHeight = 24.sp
        )
    }
}

@Composable
internal fun SlideshowOverlay(items: List<MediaItem>, initial: MediaItem, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val preferences = remember { context.getSharedPreferences("album_settings", Context.MODE_PRIVATE) }
    var random by remember { mutableStateOf(preferences.getBoolean("random_slideshow", false)) }
    var animation by remember { mutableStateOf(preferences.getString("slideshow_animation", "自然") ?: "自然") }
    var intervalLabel by remember { mutableStateOf(preferences.getString("slideshow_interval", "3秒") ?: "3秒") }
    var menuOpen by remember { mutableStateOf(false) }
    val slides = remember(items, random) { if (random) items.shuffled() else items }
    val interval = (intervalLabel.filter(Char::isDigit).toLongOrNull() ?: 3L) * 1000L
    var index by remember(slides) { mutableIntStateOf(slides.indexOfFirst { it.uri == initial.uri }.coerceAtLeast(0)) }
    var playing by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    val current = slides.getOrNull(index) ?: return

    fun move(direction: Int) {
        if (slides.isEmpty()) return
        progress = 0f
        index = (index + direction + slides.size) % slides.size
    }

    LaunchedEffect(playing, index, slides.size, interval) {
        while (playing && slides.size > 1) {
            delay(50L)
            progress = (progress + 50f / interval).coerceAtMost(1f)
            if (progress >= 1f) move(1)
        }
    }
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(
            Modifier.fillMaxSize()
                .pointerInput(index, slides.size) {
                    var distance = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { distance = 0f },
                        onHorizontalDrag = { change, amount -> change.consume(); distance += amount },
                        onDragEnd = { if (abs(distance) > 90f) move(if (distance < 0) 1 else -1) }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { playing = !playing }
                    )
                }
        ) {
            AnimatedContent(
                current,
                transitionSpec = {
                    when (animation) {
                        "滑动" -> slideInHorizontally(tween(320)) { it } togetherWith slideOutHorizontally(tween(320)) { -it }
                        "淡入淡出" -> fadeIn(tween(420)) togetherWith fadeOut(tween(420))
                        else -> (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = .985f)) togetherWith
                            (fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 1.015f))
                    }
                },
                label = "slideshow",
                modifier = Modifier.fillMaxSize()
            ) { slide ->
                MediaThumbnail(slide, Modifier.fillMaxSize(), requestedSize = 1800, showVideoMark = false, contentScale = ContentScale.Fit, backgroundColor = Color.Black, animateGif = true)
            }
            AnimatedVisibility(controlsVisible) {
                Box(
                    Modifier.fillMaxWidth().statusBarsPadding().height(76.dp).background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = .72f), Color.Transparent))
                    )
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(start = 16.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose, modifier = Modifier.size(46.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, appText("关闭幻灯片", english), tint = Color.White, modifier = Modifier.size(25.dp)) }
                        Text(current.name, Modifier.weight(1f), color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${index + 1} / ${slides.size}", color = Color.White, fontSize = 12.sp, modifier = Modifier.widthIn(min = 66.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(46.dp)) {
                                Icon(Icons.Outlined.MoreVert, appText("幻灯片设置", english), tint = Color.White, modifier = Modifier.size(25.dp))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("${appText("幻灯片播放间隔", english)}：$intervalLabel") },
                                    onClick = {
                                        val values = (1..10).map { "${it}秒" }
                                        val next = values[(values.indexOf(intervalLabel).coerceAtLeast(0) + 1) % values.size]
                                        intervalLabel = next
                                        preferences.edit().putString("slideshow_interval", next).apply()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("${appText("幻灯片播放动画", english)}：$animation") },
                                    onClick = {
                                        val values = listOf("自然", "淡入淡出", "滑动")
                                        val next = values[(values.indexOf(animation).coerceAtLeast(0) + 1) % values.size]
                                        animation = next
                                        preferences.edit().putString("slideshow_animation", next).apply()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("${appText("幻灯片随机播放", english)}：${if (random) appText("开启", english) else appText("关闭", english)}") },
                                    onClick = {
                                        random = !random
                                        preferences.edit().putBoolean("random_slideshow", random).apply()
                                    }
                                )
                            }
                        }
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(2.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = .28f)
                    )
                }
            }
        }
    }
}

private data class MediaDetails(val size: String?, val dimensions: String?)

private fun readDetails(context: Context, item: MediaItem): MediaDetails {
    var bytes: Long? = if (item.uri.scheme == "file") item.uri.path?.let(::File)?.length() else null
    if (bytes == null) context.contentResolver.query(item.uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) bytes = cursor.getLong(0)
    }
    val dimensions = if (item.isVideo) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                if (item.uri.scheme == "file") retriever.setDataSource(item.uri.path ?: return@runCatching null)
                else retriever.setDataSource(context, item.uri)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (width != null && height != null) "$width × $height" else null
            } finally {
                retriever.release()
            }
        }.getOrNull()
    } else {
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openMediaInputStream(context, item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth > 0) "${options.outWidth} × ${options.outHeight}" else null
        }.getOrNull()
    }
    val size = bytes?.let { value ->
        when {
            value >= 1024 * 1024 -> String.format(Locale.CHINA, "%.1f MB", value / 1024f / 1024f)
            value >= 1024 -> String.format(Locale.CHINA, "%.1f KB", value / 1024f)
            else -> "$value B"
        }
    }
    return MediaDetails(size, dimensions)
}

private fun share(context: Context, item: MediaItem, english: Boolean) {
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, appText("分享媒体", english)))
    }.onFailure {
        Toast.makeText(context, if (english) "No app can share this file" else "没有可分享此文件的应用", Toast.LENGTH_SHORT).show()
    }
}

private fun setWallpaper(context: Context, item: MediaItem, english: Boolean) {
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(item.uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("mimeType", item.mimeType)
        }, appText("设置为壁纸", english)))
    }.onFailure {
        Toast.makeText(context, if (english) "Unable to open wallpaper settings" else "无法打开壁纸设置", Toast.LENGTH_SHORT).show()
    }
}

private fun progressKey(item: MediaItem): String = "video_position_${item.uri.toString().hashCode()}"

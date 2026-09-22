@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@file:Suppress("UnsafeOptInUsageError")

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
import com.example.album.ui.shareMedia
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.ui.text.TextRange
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
import androidx.media3.common.AudioAttributes
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
import com.example.album.ui.appSeekDeltaText
import com.example.album.ui.appSeekText
import com.example.album.ui.appText
import com.example.album.playback.MediaPlaybackService
import com.example.album.playback.PlaybackResumeRequest
import com.example.album.playback.positionForPersistence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
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
    onWallpaper: ((MediaItem) -> Unit)? = null,
    pictureInPictureMode: Boolean = false,
    onEnterPictureInPicture: () -> Boolean = { false },
    onAutoEnterPictureInPictureChange: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    slideshowActive: Boolean = false,
    slideshowIntervalMs: Long = 3_000L,
    /**
     * Open straight into the immersive full-screen page. The slideshow's play
     * button uses this so playback starts immediately; opening a picture from
     * the queue keeps the normal preview first.
     */
    startImmersive: Boolean = false
) {
    val context = LocalContext.current
    val showRenameExtension = remember { context.getSharedPreferences("album_settings", android.content.Context.MODE_PRIVATE).getBoolean("rename_show_extension", false) }
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
    val viewerItems = remember(items, item.uri, item.isVideo) {
        val sameType = items.filter { it.isVideo == item.isVideo }
        // ACTION_VIEW can provide a URI that is not in Album's library yet.
        // Keep it in the playlist so ExoPlayer always has a valid current item.
        if (sameType.any { it.uri == item.uri }) sameType else listOf(item) + sameType
    }
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
    // A page drag in progress: the slideshow timer must not fire in the middle
    // of a swipe (two page changes at once made the page flash).
    var pagerDragging by remember { mutableStateOf(false) }
    var imageViewport by remember { mutableStateOf(IntSize.Zero) }
    var imageControlsVisible by remember { mutableStateOf(!startImmersive) }
    // Fade the background with the same timing as the controls so entering and
    // leaving full screen never looks out of step.
    val imageFullScreenBackground by androidx.compose.animation.animateColorAsState(
        if (imageControlsVisible) Color.White else Color.Black,
        androidx.compose.animation.core.tween(220),
        label = "viewer-image-background"
    )
    var videoMiniMode by remember { mutableStateOf(false) }
    // The main player always gets the first chance. Probing the platform
    // extractor first used to send AVI/MPEG files straight to LibVLC on phones
    // whose MediaExtractor reports a codec the codec list does not offer back,
    // even though Media3's own extractors and the device decoders handle the
    // file perfectly well.
    var useVlcPlayer by remember(current.uri) {
        mutableStateOf(current.isVideo && requiresVlcPlayback(current))
    }
    // Formats/codecs the platform player cannot handle are replayed with the
    // bundled LibVLC player.
    var vlcFallbackForCurrent by remember { mutableStateOf(false) }
    // The custom mini window belongs to the Media3 player; clear it when the
    // current video is routed to the LibVLC fallback (AVI and friends).
    LaunchedEffect(current.uri) {
        vlcFallbackForCurrent = false
        if (requiresVlcPlayback(current)) videoMiniMode = false
    }
    var viewerDirection by remember { mutableIntStateOf(1) }
    var highResolutionLoaded by remember(current.uri) { mutableStateOf(false) }
    var originalResolutionLoaded by remember(current.uri) { mutableStateOf(false) }
    val previewOriginalImages = thumbnailPreferences.getBoolean("preview_original", true)

    LaunchedEffect(currentIndex, viewerItems) {
        if (current.isVideo) return@LaunchedEffect
        // Two pictures on each side: swiping reveals the neighbour out of the
        // thumbnail cache instead of an empty frame.
        val adjacent = listOf(currentIndex - 2, currentIndex - 1, currentIndex + 1, currentIndex + 2)
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
        // Request portrait before the exit animation starts. The player menu,
        // system back gesture, and top bar all converge here, so restoring the
        // window policy during disposal would leave a visible rotation lag.
        if (current.isVideo) {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
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
        // Swiping to the next image must not leave full screen: only a single
        // tap toggles the preview controls.
    }

    // Slideshow playback uses this same viewer: it advances only while the
    // full-screen (controls hidden) state is active, so opening the preview
    // pauses it and closing the preview resumes.
    LaunchedEffect(slideshowActive, imageControlsVisible, currentIndex, viewerItems.size, current.uri, pagerDragging) {
        if (!slideshowActive || pagerDragging || current.isVideo || imageControlsVisible || viewerItems.size <= 1) return@LaunchedEffect
        delay(slideshowIntervalMs.coerceAtLeast(500L))
        viewerDirection = 1
        val next = if (currentIndex >= viewerItems.lastIndex) 0 else currentIndex + 1
        currentIndex = next
        onItemChanged(viewerItems[next])
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
        val nextIndex = currentIndex + direction
        if (nextIndex !in viewerItems.indices) return
        viewerDirection = direction
        currentIndex = nextIndex
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
                !current.isVideo && !imageControlsVisible -> imageFullScreenBackground
                !current.isVideo -> Color.White.copy(alpha = viewerBackgroundAlpha.value)
                videoMiniMode -> Color.Transparent
                else -> Color.Black
            }
        ) {
            if (current.isVideo) {
                if (useVlcPlayer || vlcFallbackForCurrent) {
                    android.util.Log.i("AlbumVlcFallback", "rendering LibVLC player for ${current.name}")
                    VlcVideoPlayer(
                        current = current,
                        onBack = ::closeViewer,
                        pictureInPictureMode = pictureInPictureMode,
                        onEnterPictureInPicture = onEnterPictureInPicture,
                        onAutoEnterPictureInPictureChange = onAutoEnterPictureInPictureChange,
                        miniMode = videoMiniMode,
                        onMiniModeChange = { videoMiniMode = it },
                        favorite = favorite(current),
                        onFavorite = { onFavorite(current) },
                        onShare = { share(context, current, english) },
                        onWallpaper = onWallpaper?.let { action -> { action(current) } } ?: {},
                        onInfo = { showInfo = true }
                    )
                } else {
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
                     onWallpaper = onWallpaper?.let { action -> { action(current) } } ?: {},
                     onInfo = { showInfo = true },
                     onSettings = { showVideoSettings = true },
                     settingsVersion = videoSettingsVersion,
                    onAutoEnterPictureInPictureChange = onAutoEnterPictureInPictureChange,
                     onPlaybackError = {
                        if (!vlcFallbackForCurrent) {
                            vlcFallbackForCurrent = true
                            Toast.makeText(
                                context,
                                if (english) "Switched to the compatible player" else "已切换为兼容播放器",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                }
            } else {
                // How far the picture is dragged horizontally, in pixels. The
                // page follows the finger and the neighbour is pulled in from
                // the edge; releasing past the middle switches pages.
                var pagerOffset by remember { mutableFloatStateOf(0f) }
                // True while a page drag is in progress: the slideshow timer
                // must not fire in the middle of a swipe, which made the page
                // flash as two page changes landed at once.
                // Suppresses the slide transition for the frame in which a drag
                // turns into a page change: the animation already happened
                // under the finger.
                var pagerCommit by remember { mutableStateOf(false) }
                // Cleared one frame after a drag handed the page over, so the
                // regular slide transition is available again.
                LaunchedEffect(currentIndex) {
                    if (pagerCommit) pagerCommit = false
                }
                /** Half of the pan room the *fitted* picture has at [scale]. */
                fun panLimit(scale: Float): Offset {
                    val viewportAspect = imageViewport.width.toFloat()
                        .takeIf { it > 0f && imageViewport.height > 0 }?.div(imageViewport.height.toFloat())
                        ?: (16f / 9f)
                    val mediaAspect = if (current.width > 0 && current.height > 0) {
                        (current.width.toFloat() / current.height).coerceIn(.05f, 20f)
                    } else {
                        viewportAspect
                    }
                    val fittedWidth = if (mediaAspect > viewportAspect) imageViewport.width.toFloat()
                    else imageViewport.height * mediaAspect
                    val fittedHeight = if (mediaAspect > viewportAspect) imageViewport.width / mediaAspect
                    else imageViewport.height.toFloat()
                    return Offset(
                        ((fittedWidth * scale - imageViewport.width) / 2f).coerceAtLeast(0f),
                        ((fittedHeight * scale - imageViewport.height) / 2f).coerceAtLeast(0f)
                    )
                }
                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    val nextScale = (imageScale * zoomChange).coerceIn(1f, 5f)
                    // Bounds come from the *fitted* picture, not the viewport:
                    // a picture that is shorter than the viewport must not be
                    // draggable up and down at all, otherwise its edges could be
                    // pulled inside the visible area.
                    val limits = panLimit(nextScale)
                    val maxX = limits.x
                    val maxY = limits.y
                    imageScale = nextScale
                    imageOffset = if (nextScale <= 1.01f) Offset.Zero else Offset(
                        // The picture stops at its edge; continuing the drag
                        // there is what pulls the neighbouring page in (see the
                        // pager gesture below).
                        //
                        // Panning is scaled by the zoom level: at 2.5x the
                        // content travels 2.5x as far for the finger to cover
                        // the same part of the picture.
                        (imageOffset.x + panChange.x * nextScale).coerceIn(-maxX, maxX),
                        (imageOffset.y + panChange.y * nextScale).coerceIn(-maxY, maxY)
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
                    // The page the finger is pulling in, drawn under the
                    // current one so both edges of the drag are visible.
                    val pageWidth = imageViewport.width.toFloat().coerceAtLeast(1f)
                    val neighbourIndex = when {
                        pagerOffset < -0.5f -> currentIndex + 1
                        pagerOffset > 0.5f -> currentIndex - 1
                        else -> -1
                    }
                    if (neighbourIndex in viewerItems.indices) {
                        MediaThumbnail(
                            viewerItems[neighbourIndex],
                            Modifier.fillMaxSize().graphicsLayer {
                                translationX = pagerOffset + if (pagerOffset < 0f) pageWidth else -pageWidth
                            },
                            // The small cached thumbnail decodes immediately, so
                            // the neighbour appears under the finger instead of
                            // a blank frame while a larger size loads.
                            requestedSize = 360,
                            showVideoMark = false,
                            contentScale = ContentScale.Fit,
                            backgroundColor = imageFullScreenBackground,
                            animateGif = false
                        )
                    }
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            if (pagerCommit) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val easing = CubicBezierEasing(.22f, .72f, .24f, 1f)
                                slideInHorizontally(tween(240, easing = easing)) { width -> viewerDirection * width } togetherWith
                                    slideOutHorizontally(tween(240, easing = easing)) { width -> -viewerDirection * width }
                            }
                        },
                        label = "viewer-media",
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { translationX = pagerOffset }
                            .pointerInput(currentIndex, viewerItems.size) {
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                fun hasNeighbour(direction: Int): Boolean =
                                    currentIndex + direction in viewerItems.indices
                                /**
                                 * True when the finger is pushing past an edge
                                 * of the zoomed picture, which means the page
                                 * (not the picture) should move.
                                 */
                                fun pushingPastEdge(amount: Float): Boolean {
                                    if (imageScale <= 1.01f) return true
                                    val maxX = panLimit(imageScale).x
                                    return when {
                                        amount > 0f -> imageOffset.x >= maxX - 1f
                                        amount < 0f -> imageOffset.x <= -maxX + 1f
                                        else -> false
                                    }
                                }
                                fun slideTo(target: Float, durationMs: Int, onFinished: () -> Unit = {}) {
                                    scope.launch {
                                        val animation = Animatable(pagerOffset)
                                        animation.animateTo(target, tween(durationMs)) { pagerOffset = value }
                                        onFinished()
                                    }
                                }
                                fun settle() = slideTo(0f, 150)
                                // Written by hand instead of detectHorizontalDragGestures:
                                // that detector consumes the gesture the moment it
                                // starts, so a zoomed picture could no longer pan --
                                // the transformable saw consumed changes and gave up.
                                // Nothing is consumed here until the gesture has been
                                // classified as a page drag.
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val slop = viewConfiguration.touchSlop
                                    var paging = false
                                    var handedToPicture = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        val totalDx = change.position.x - down.position.x
                                        if (!paging && !handedToPicture && abs(totalDx) > slop) {
                                            // Past the slop either the picture pans
                                            // (zoomed, not at its edge) or the page
                                            // takes the drag.
                                            if (pushingPastEdge(totalDx)) {
                                                paging = true
                                                pagerDragging = true
                                            } else {
                                                handedToPicture = true
                                            }
                                        }
                                        if (paging && change.pressed) {
                                            change.consume()
                                            val next = pagerOffset + (change.position.x - change.previousPosition.x)
                                            pagerOffset = when {
                                                // Rubber band at either end of the
                                                // playlist instead of detaching.
                                                next < 0f && !hasNeighbour(1) -> next * .35f
                                                next > 0f && !hasNeighbour(-1) -> next * .35f
                                                else -> next.coerceIn(-width, width)
                                            }
                                        }
                                        if (!change.pressed) break
                                    }
                                    if (paging) {
                                        pagerDragging = false
                                        val direction = if (pagerOffset < 0f) 1 else -1
                                        // 15% of the page commits the swipe.
                                        if (abs(pagerOffset) >= width * .1f && hasNeighbour(direction)) {
                                            slideTo(if (direction > 0) -width else width, 170) {
                                                // The neighbour is centred now: hand
                                                // the page over without a second slide.
                                                pagerCommit = true
                                                // Leave the zoomed state behind: the
                                                // next picture opens as it was.
                                                imageScale = 1f
                                                imageOffset = Offset.Zero
                                                moveViewer(direction)
                                                pagerOffset = 0f
                                            }
                                        } else {
                                            settle()
                                        }
                                    }
                                }
                            }
                    ) { shown ->
                        Box(
                            Modifier.fillMaxSize().mediaSharedElement(shown).graphicsLayer {
                                scaleX = imageScale
                                scaleY = imageScale
                                translationX = imageOffset.x
                                translationY = imageOffset.y
                            }.background(imageFullScreenBackground)
                        ) {
                            MediaThumbnail(
                                shown,
                                Modifier.fillMaxSize(),
                                requestedSize = 360,
                                showVideoMark = false,
                                contentScale = ContentScale.Fit,
                                backgroundColor = imageFullScreenBackground,
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
                            onInfo = { showMenu = false; showInfo = !showInfo },
                            menuExpanded = showMenu,
                            onMenuExpanded = { showInfo = false; showMenu = it },
                            onShare = { share(context, current, english) },
                            onCopy = { onCopy(current) },
                            onSettings = current.takeIf { it.isVideo }?.let { { showVideoSettings = true } },
                            onEditTags = onEditTags?.let { action -> { action(current) } },
                            onWallpaper = onWallpaper?.let { action -> { action(current) } }
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
                        )
                    }

                    if (showInfo && imageControlsVisible) {
                        MediaInfoPanel(current, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 66.dp, end = 10.dp), playerStyle = current.isVideo)
                    }

                }
            }
        }

        if (showInfo && current.isVideo) {
            Box(
                Modifier.fillMaxSize()
                    .zIndex(30f)
                    .pointerInput(Unit) { detectTapGestures { showInfo = false } }
            ) {
                MediaInfoPanel(
                    current,
                    Modifier.align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 66.dp, end = 10.dp),
                    playerStyle = true
                )
            }
        }

        if (showRename) {
            val extension = current.name.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            val editableName = if (showRenameExtension || extension == null) current.name else current.name.removeSuffix(".$extension")
            VaultTextInputDialog(
                title = appText("重命名", english),
                value = if (renameText == current.name) editableName else renameText,
                onValueChange = { renameText = it },
                label = appText("文件名", english),
                confirmLabel = appText("保存", english),
                autoFocus = true,
                initialSelection = TextRange(0, (if (showRenameExtension && extension != null) editableName.length - extension.length - 1 else editableName.length).coerceAtLeast(0)),
                onDismiss = { showRename = false },
                onConfirm = {
                        val enteredName = renameText.trim()
                        val newName = if (!showRenameExtension && extension != null && !enteredName.endsWith(".$extension", ignoreCase = true)) "$enteredName.$extension" else enteredName
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
private fun ViewerTopBar(
    item: MediaItem,
    position: String,
    favorite: Boolean,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onInfo: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpanded: (Boolean) -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onSettings: (() -> Unit)?,
    onEditTags: (() -> Unit)?,
    onWallpaper: (() -> Unit)?
) {
    val english = LocalAppEnglish.current
    val menuForeground = if (item.isVideo) Color.White else Color(0xFF1A1A1A)
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
        Box {
            IconButton(onClick = { onMenuExpanded(true) }) {
                Icon(Icons.Outlined.MoreVert, appText("菜单", english), tint = Color(0xFF1A1A1A))
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpanded(false) },
                modifier = Modifier.width(190.dp).clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)),
                containerColor = if (item.isVideo) Color.Black.copy(alpha = .20f) else Color.White
            ) {
                DropdownMenuItem(text = { Text(appText("分享", english), color = menuForeground, fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Outlined.Share, null, tint = menuForeground) }, modifier = Modifier.height(52.dp), onClick = { onMenuExpanded(false); onShare() })
                DropdownMenuItem(text = { Text(appText("复制", english), color = menuForeground, fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, tint = menuForeground) }, modifier = Modifier.height(52.dp), onClick = { onMenuExpanded(false); onCopy() })
                DropdownMenuItem(text = { Text(appText("信息", english), color = menuForeground, fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Outlined.Info, null, tint = menuForeground) }, modifier = Modifier.height(52.dp), onClick = { onMenuExpanded(false); onInfo() })
                onSettings?.let { action ->
                    DropdownMenuItem(text = { Text(appText("设置", english), color = menuForeground, fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Outlined.SettingsIcon, null, tint = menuForeground) }, modifier = Modifier.height(52.dp), onClick = { onMenuExpanded(false); action() })
                }
                onEditTags?.let { action ->
                    DropdownMenuItem(text = { Text(if (english) "View/Edit Tags" else "查看/编辑 Tags", color = menuForeground, fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Outlined.Label, null, tint = menuForeground) }, modifier = Modifier.height(52.dp), onClick = { onMenuExpanded(false); action() })
                }
                onWallpaper?.let { action ->
                    DropdownMenuItem(text = { Text(appText("设置为壁纸", english), color = menuForeground, fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Outlined.Wallpaper, null, tint = menuForeground) }, modifier = Modifier.height(52.dp), onClick = { onMenuExpanded(false); action() })
                }
            }
        }
    }
}

@Composable
private fun ViewerBottomBar(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
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
    var pauseOnBackground by remember { mutableStateOf(preferences.getBoolean("video_pause_on_background", true)) }
    var rememberProgress by remember { mutableStateOf(preferences.getBoolean("video_progress", true)) }
    var autoHide by remember { mutableStateOf(preferences.getBoolean("video_auto_hide", true)) }
    var centerPopup by remember { mutableStateOf(preferences.getBoolean("video_center_popup", true)) }
    var longSkip by remember { mutableStateOf(preferences.getBoolean("long_skip", false)) }
    var edgeProtection by remember { mutableStateOf(preferences.getBoolean("edge_protection", true)) }
    var tapPause by remember { mutableStateOf(preferences.getBoolean("video_tap_pause", false)) }
    var portraitTapPause by remember { mutableStateOf(preferences.getBoolean("video_portrait_tap_pause", false)) }
    var normalSkip by remember { mutableStateOf(preferences.getString("normal_skip", "10秒") ?: "10秒") }
    var longSkipLength by remember { mutableStateOf(preferences.getString("long_skip_length", "30秒") ?: "30秒") }
    var gestureSeek by remember { mutableStateOf(preferences.getString("gesture_seek", "90秒") ?: "90秒") }
    var autoMini by remember { mutableStateOf(preferences.getBoolean("video_auto_mini", false)) }
    var brightnessVolumeRatio by remember { mutableStateOf(preferences.getString("video_brightness_volume_ratio", "1:1") ?: "1:1") }
    var seekPauseRatio by remember { mutableStateOf(preferences.getString("video_seek_pause_ratio", "1:1:1") ?: "1:1:1") }
    var openChoice by remember { mutableStateOf<String?>(null) }

    fun putBoolean(key: String, value: Boolean) = preferences.edit().putBoolean(key, value).apply()
    fun putString(key: String, value: String) = preferences.edit().putString(key, value).apply()
    val options = when (openChoice) {
        "normal" -> listOf("3秒", "5秒", "10秒", "15秒", "30秒")
        "long" -> listOf("30秒", "60秒", "90秒", "120秒")
        "gesture" -> listOf("30秒", "60秒", "90秒", "120秒", "150秒")
        "brightnessVolume" -> listOf("1:1", "1:1:1", "1:2:1")
        "seekPause" -> listOf("1:1:1", "1:2:1", "1:0:1")
        else -> emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black.copy(alpha = .20f),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(appText("视频设置", english)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VideoSettingSwitch(appText("打开视频时自动播放", english), autoplay) {
                    autoplay = it; putBoolean("video_autoplay", it)
                }
                VideoSettingSwitch(appText("进入后台时自动暂停", english), pauseOnBackground) {
                    pauseOnBackground = it; putBoolean("video_pause_on_background", it)
                }
                VideoSettingSwitch(appText("记住最后一次播放进度", english), rememberProgress) {
                    rememberProgress = it; putBoolean("video_progress", it)
                }
                VideoSettingSwitch(appText("自动隐藏播放器界面", english), autoHide) {
                    autoHide = it; putBoolean("video_auto_hide", it)
                }
                VideoSettingSwitch(appText("手势提示浮层", english), centerPopup) {
                    centerPopup = it; putBoolean("video_center_popup", it)
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
                VideoSettingSwitch(appText("自动小窗", english), autoMini) {
                    autoMini = it; putBoolean("video_auto_mini", it)
                }
                VideoSettingChoice(appText("亮度：空白：音量 触控占比", english), brightnessVolumeRatio) { openChoice = "brightnessVolume" }
                VideoSettingChoice(appText("快退：暂停：快进 触控占比", english), seekPauseRatio) { openChoice = "seekPause" }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(appText("完成", english)) } }
    )

    if (openChoice != null) {
        AlertDialog(
            onDismissRequest = { openChoice = null },
            containerColor = Color.Black.copy(alpha = .20f),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Text(
                    when (openChoice) {
                        "normal" -> appText("快进长度", english)
                        "long" -> appText("长快进长度", english)
                        "gesture" -> appText("满屏滑动跳过时间", english)
                        "brightnessVolume" -> appText("亮度：空白：音量 触控占比", english)
                        else -> appText("快退：暂停：快进 触控占比", english)
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
                                    "brightnessVolume" -> { brightnessVolumeRatio = option; putString("video_brightness_volume_ratio", option) }
                                    "seekPause" -> { seekPauseRatio = option; putString("video_seek_pause_ratio", option) }
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
private fun MiniVideoPlayer(
    player: ExoPlayer,
    playing: Boolean,
    widthPx: Float,
    offset: Offset,
    aspectRatio: Float,
    seekIncrement: Long,
    onMove: (Offset) -> Unit,
    onResize: (Offset, Boolean, Boolean) -> Unit,
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
            .aspectRatio(aspectRatio)
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
        LegacyMiniVideoButton(
            Icons.Outlined.Fullscreen,
            appText("恢复全屏播放", english),
            Modifier.align(Alignment.TopStart).zIndex(1000f),
            onRestore
        )
        LegacyMiniVideoButton(
            Icons.Outlined.Close,
            appText("关闭", english),
            Modifier.align(Alignment.TopEnd).zIndex(1000f),
            onClose
        )
        LegacyMiniVideoButton(
            Icons.Outlined.FastRewind,
            appText("快退", english),
            Modifier.align(Alignment.CenterStart).zIndex(1000f)
        ) {
            seekToVideoFrame(player, player.currentPosition - seekIncrement)
        }
        LegacyMiniVideoButton(
            if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            appText(if (playing) "暂停" else "播放", english),
            Modifier.align(Alignment.Center).zIndex(1000f)
        ) {
            if (player.isPlaying) player.pause() else player.play()
        }
        LegacyMiniVideoButton(
            Icons.Outlined.FastForward,
            appText("快进", english),
            Modifier.align(Alignment.CenterEnd).zIndex(1000f)
        ) {
            seekToVideoFrame(player, player.currentPosition + seekIncrement)
        }
    }
}

@Composable
private fun LegacyMiniVideoButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        // Flat white icon like the other mini windows: the black disc made this
        // one look like a different design.
        modifier = modifier.size(44.dp)
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
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
private fun MediaInfoPanel(item: MediaItem, modifier: Modifier = Modifier, playerStyle: Boolean = false) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val details by produceState<MediaDetails?>(null, item.uri) {
        value = withContext(Dispatchers.IO) { readDetails(context, item) }
    }
    Surface(
        modifier = modifier.width(228.dp),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 10.dp,
        color = if (playerStyle) Color.Black.copy(alpha = .20f) else Color.White
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
            color = if (playerStyle) Color.White else Color(0xFF1C1C1E),
            fontSize = 13.sp,
            lineHeight = 24.sp
        )
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
    shareMedia(context, listOf(item), english)
}

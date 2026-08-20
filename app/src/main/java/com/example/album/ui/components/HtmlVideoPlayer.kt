@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.album.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.provider.Settings
import android.view.View
import android.view.OrientationEventListener
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem.Builder
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView
import com.example.album.data.MediaItem
import com.example.album.playback.MediaPlaybackService
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import kotlinx.coroutines.delay

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun HtmlVideoPlayer(
    current: MediaItem,
    videos: List<MediaItem>,
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
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val preferences = remember { context.getSharedPreferences("album_settings", Context.MODE_PRIVATE) }
    val normalSkip = (preferences.getString("normal_skip", "10秒")?.filter(Char::isDigit)?.toLongOrNull() ?: 10L) * 1000L
    val longSkip = (preferences.getString("long_skip_length", "30秒")?.filter(Char::isDigit)?.toLongOrNull() ?: 30L) * 1000L
    val longSkipEnabled = preferences.getBoolean("long_skip", false)
    val rememberProgress = preferences.getBoolean("video_progress", true)
    val edgeProtection = preferences.getBoolean("edge_protection", true)
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var mode by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showSpeed by remember { mutableStateOf(false) }
    var wasPlayingForSpeed by remember { mutableStateOf(false) }
    var orientation by remember { mutableIntStateOf(0) }
    var gestureHud by remember { mutableStateOf<String?>(null) }
    var lockVisible by remember { mutableStateOf(true) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var bufferingSince by remember { mutableLongStateOf(0L) }
    var bufferingSuppressed by remember { mutableStateOf(false) }
    var sensorLandscape by remember { mutableStateOf(false) }
    var selectedIndex by remember(videos, current.uri) {
        mutableIntStateOf(videos.indexOfFirst { it.uri == current.uri }.coerceAtLeast(0))
    }
    val density = LocalDensity.current
    // Mode 0 is the HTML player's adaptive mode: resolve from the actual
    // viewer bounds. Modes 1/2 force landscape/portrait inside the fixed
    // portrait app window without requesting a system rotation.
    val resolvedLandscape = orientation == 1 ||
        (orientation == 0 && (sensorLandscape || viewportSize.width > viewportSize.height))
    val activity = context as? Activity
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val initialScreenBrightness = remember(activity) {
        activity?.window?.attributes?.screenBrightness
    }
    val initialMediaVolume = remember(audioManager) {
        audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
    }
    var restoreScreenBrightness by remember(activity) { mutableStateOf(initialScreenBrightness) }
    var restoreMediaVolume by remember(audioManager) { mutableStateOf(initialMediaVolume) }
    var restoreSystemBrightnessOnExit by remember { mutableStateOf(false) }
    var hasLeftPlayer by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(angle: Int) {
                if (angle == ORIENTATION_UNKNOWN) return
                sensorLandscape = angle in 60..120 || angle in 240..300
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    DisposableEffect(activity) {
        val lifecycle = (activity as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasLeftPlayer = true
                Lifecycle.Event.ON_RESUME -> if (hasLeftPlayer) {
                    restoreMediaVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: restoreMediaVolume
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
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val player = remember(videos) {
        ExoPlayer.Builder(
            context,
            DefaultRenderersFactory(context)
                // Let Android choose the best hardware codec first and use
                // software/fallback codecs when the device exposes them.
                .setMediaCodecSelector(MediaCodecSelector.DEFAULT)
                .setEnableDecoderFallback(true)
                .forceDisableMediaCodecAsynchronousQueueing()
        ).setSeekBackIncrementMs(normalSkip).setSeekForwardIncrementMs(normalSkip).build().apply {
            setMediaItems(videos.map { item ->
                Builder().setUri(item.uri).setMediaId(item.uri.toString())
                    .setMimeType(item.mimeType.takeIf { it.startsWith("video/") && it != "video/*" })
                    .build()
            })
            val initialIndex = videos.indexOfFirst { it.uri == current.uri }.coerceAtLeast(0)
            val saved = if (rememberProgress) preferences.getLong("video_position_${current.uri.toString().hashCode()}", 0L) else 0L
            seekTo(initialIndex, saved)
            prepare()
            playWhenReady = preferences.getBoolean("video_autoplay", true)
        }
    }
    val latestMode by rememberUpdatedState(mode)
    DisposableEffect(player) {
        var recoveryAttempted = false
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (recoveryAttempted) {
                    player.playWhenReady = false
                    bufferingSuppressed = true
                    bufferingSince = 0L
                    // A failed decoder can leave ExoPlayer reporting
                    // BUFFERING forever. Stop it so the next-video action and
                    // the Play button have a clean IDLE state to recover from.
                    player.stop()
                    return
                }
                recoveryAttempted = true
                val index = player.currentMediaItemIndex.coerceAtLeast(0)
                val resumePosition = player.currentPosition.coerceAtLeast(0L)
                player.stop()
                player.seekTo(index, resumePosition)
                player.prepare()
                player.playWhenReady = true
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                recoveryAttempted = false
                bufferingSuppressed = false
                bufferingSince = 0L
                videos.firstOrNull { it.uri.toString() == mediaItem?.mediaId }?.let { changed ->
                    selectedIndex = videos.indexOf(changed).coerceAtLeast(0)
                    onCurrentChanged(changed)
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    bufferingSuppressed = false
                    bufferingSince = 0L
                }
                if (state == Player.STATE_ENDED && videos.isNotEmpty()) {
                    when (latestMode) {
                        3 -> player.pause()
                        1 -> { player.seekTo(player.currentMediaItemIndex, 0L); player.play() }
                        2 -> {
                            val next = if (videos.size <= 1) 0 else (0 until videos.size).filter { it != player.currentMediaItemIndex }.random()
                            player.seekTo(next, 0L); player.play()
                        }
                        else -> { player.seekTo((player.currentMediaItemIndex + 1) % videos.size, 0L); player.play() }
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            if (rememberProgress) preferences.edit().putLong("video_position_${current.uri.toString().hashCode()}", player.currentPosition).apply()
            player.removeListener(listener)
            player.release()
            activity?.let { host ->
                if (restoreSystemBrightnessOnExit) {
                    host.window.attributes = host.window.attributes.apply { screenBrightness = -1f }
                } else restoreScreenBrightness?.let { brightness ->
                    host.window.attributes = host.window.attributes.apply {
                        screenBrightness = brightness
                    }
                }
                restoreMediaVolume?.let { volume ->
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                }
            }
        }
    }
    LaunchedEffect(player, interactionTick, controlsVisible, controlsLocked) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            playing = player.isPlaying
            val isBuffering = player.playbackState == Player.STATE_BUFFERING
            buffering = isBuffering && !bufferingSuppressed
            if (isBuffering && !bufferingSuppressed) {
                if (bufferingSince == 0L) bufferingSince = android.os.SystemClock.elapsedRealtime()
                if (android.os.SystemClock.elapsedRealtime() - bufferingSince > 8000L) {
                    player.playWhenReady = false
                    bufferingSuppressed = true
                    buffering = false
                    bufferingSince = 0L
                    player.stop()
                }
            } else if (!isBuffering) {
                bufferingSince = 0L
                if (player.playbackState == Player.STATE_READY) bufferingSuppressed = false
            }
            delay(200L)
        }
    }
    LaunchedEffect(controlsVisible, controlsLocked, interactionTick) {
        if (controlsVisible && !controlsLocked && preferences.getBoolean("video_auto_hide", true)) {
            delay(3000L)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsLocked, lockVisible, interactionTick) {
        if (controlsLocked && lockVisible) {
            delay(3000L)
            lockVisible = false
        }
    }
    LaunchedEffect(gestureHud) {
        if (gestureHud != null) {
            delay(700L)
            gestureHud = null
        }
    }
    fun refresh() { controlsVisible = true; interactionTick++ }
    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            // stop() is used after a decoder/buffer timeout. An idle player
            // needs an explicit prepare before play() can start it again.
            bufferingSuppressed = false
            bufferingSince = 0L
            if (player.playbackState == Player.STATE_IDLE) {
                player.seekTo(selectedIndex, player.currentPosition.coerceAtLeast(0L))
                player.prepare()
            }
            player.play()
        }
        refresh()
    }
    fun seekAdjacent(next: Boolean) {
        if (videos.isEmpty()) return
        val index = selectedIndex.coerceIn(0, videos.lastIndex)
        val target = if (next) (index + 1) % videos.size else (index - 1 + videos.size) % videos.size
        selectedIndex = target
        bufferingSuppressed = false
        bufferingSince = 0L
        // Update the Compose viewer immediately. Decoder preparation can fail
        // on an emulator, but navigation must still leave the current item
        // and show the requested video's metadata instead of waiting for a
        // successful render transition.
        onCurrentChanged(videos[target])
        // Keep the prepared queue stable. Replacing all media items here can
        // invalidate the current surface and leave a recovered player idle.
        player.stop()
        player.seekTo(target, 0L)
        player.playWhenReady = true
        player.prepare()
        refresh()
    }
    fun cycleOrientation() {
        orientation = (orientation + 1) % 3
        refresh()
    }
    fun startBackground() {
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_START
            putExtra(MediaPlaybackService.EXTRA_URI, current.uri.toString())
            putExtra(MediaPlaybackService.EXTRA_NAME, current.name)
            putExtra(MediaPlaybackService.EXTRA_POSITION, player.currentPosition)
        }
        runCatching { androidx.core.content.ContextCompat.startForegroundService(context, intent) }
        player.pause()
        onBack()
    }
    if (miniMode) {
        HtmlMiniVideoPlayer(
            player = player,
            playing = playing,
            seek = normalSkip,
            onBackground = ::startBackground,
            onRestore = { onMiniModeChange(false) },
            onClose = onBack
        )
    } else Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { viewportSize = it }, contentAlignment = Alignment.Center) {
        val canvasWidth = viewportSize.width
        val canvasHeight = viewportSize.height
        // If the window itself is landscape, its coordinates are already in
        // the desired orientation. Only rotate when the app remains in a
        // portrait-sized window and the user requests/adapts to landscape.
        // Match the HTML viewer: in a portrait-sized root, landscape mode is
        // a swapped-size stage rotated around its center. If the system has
        // already supplied a landscape window, leave it unrotated.
        val rotateCanvas = resolvedLandscape && canvasWidth > 0 && canvasHeight > 0 && canvasWidth <= canvasHeight
        Box(
            Modifier
                .then(if (rotateCanvas) Modifier.size(
                    with(density) { canvasHeight.toDp() },
                    with(density) { canvasWidth.toDp() }
                ) else Modifier.fillMaxSize())
                .graphicsLayer { rotationZ = if (rotateCanvas) 90f else 0f }
                .background(Color.Black)
        ) {
        Box(
            Modifier.fillMaxSize().zIndex(-10f)
        ) {
            AndroidView(
                factory = { viewContext -> PlayerView.inflate(viewContext, com.example.album.R.layout.view_html_video_player, null) as PlayerView },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier.fillMaxSize().zIndex(0f).pointerInput(controlsLocked, pictureInPictureMode, edgeProtection) {
                detectTapGestures(
                    onTap = {
                        if (controlsLocked) {
                            lockVisible = true
                            interactionTick++
                        } else if (!pictureInPictureMode) {
                            controlsVisible = !controlsVisible
                            interactionTick++
                        }
                    },
                    onDoubleTap = { offset ->
                        if (controlsLocked || pictureInPictureMode) return@detectTapGestures
                        val fraction = offset.x / size.width.coerceAtLeast(1)
                        if (player.playbackState == Player.STATE_ENDED) {
                            player.seekTo(0L); player.play(); gestureHud = "从头播放"
                        } else if (fraction < 1f / 3f) {
                            player.seekTo((player.currentPosition - normalSkip).coerceAtLeast(0L)); gestureHud = "快退 ${normalSkip / 1000L}秒"
                        } else if (fraction > 2f / 3f) {
                            player.seekTo((player.currentPosition + normalSkip).coerceAtMost(player.duration.coerceAtLeast(0L))); gestureHud = "快进 ${normalSkip / 1000L}秒"
                        } else { togglePlay(); gestureHud = if (player.isPlaying) "播放" else "暂停" }
                        refresh()
                    }
                )
            }
        )
        if (buffering && !miniMode) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(42.dp).zIndex(8f),
                color = Color.White,
                trackColor = Color.Transparent,
                strokeWidth = 4.dp
            )
        }
        if (!controlsLocked && controlsVisible && !pictureInPictureMode) {
            Box(Modifier.fillMaxSize().zIndex(10f)) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(78.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(.70f), Color.Transparent)))
                        .padding(start = 10.dp, end = 10.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HtmlVideoButton(Icons.Outlined.ArrowBack, appText("返回", english), onBack)
                    Text(current.name, Modifier.weight(1f), color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${selectedIndex + 1}/${videos.size}", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    Box(Modifier.size(46.dp).clickable {
                        wasPlayingForSpeed = player.isPlaying
                        if (wasPlayingForSpeed) player.pause()
                        showSpeed = true
                        refresh()
                    }, contentAlignment = Alignment.Center) { Text(if (speed == 1f) "1x" else "${speed}x", color = Color.White, fontSize = 13.sp) }
                    HtmlVideoButton(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, appText("收藏", english), onFavorite, tint = if (favorite) Color(0xFFFFD60A) else Color.White)
                    HtmlVideoButton(Icons.Outlined.Share, appText("分享", english), onShare, iconSize = 24.dp)
                }
                val sideOffset = with(density) { (-canvasHeight * 0.03f).toDp() }
                Column(Modifier.align(Alignment.CenterStart).offset(y = sideOffset).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HtmlVideoButton(Icons.Outlined.PictureInPictureAlt, appText("画中画", english), { if (!onEnterPictureInPicture()) onMiniModeChange(true) })
                    HtmlVideoButton(Icons.Outlined.Headphones, appText("后台播放", english), ::startBackground)
                }
                HtmlVideoButton(Icons.Outlined.LockOpen, appText("锁定控件", english), { controlsLocked = true; refresh() }, Modifier.align(Alignment.CenterEnd).offset(y = sideOffset).padding(end = 12.dp))
                SpacerBottomControls(
                    position = position,
                    duration = duration,
                    playing = playing,
                    mode = mode,
                    longSkipEnabled = longSkipEnabled,
                    onSeek = { player.seekTo(it); refresh() },
                    onMode = { mode = (mode + 1) % 4; refresh() },
                    onPrevious = { seekAdjacent(false) },
                    onNext = { seekAdjacent(true) },
                    onPlay = ::togglePlay,
                    onRewind = { player.seekTo((player.currentPosition - normalSkip).coerceAtLeast(0L)); refresh() },
                    onForward = { player.seekTo((player.currentPosition + normalSkip).coerceAtMost(player.duration.coerceAtLeast(0L))); refresh() },
                    onLongRewind = { player.seekTo((player.currentPosition - longSkip).coerceAtLeast(0L)); refresh() },
                    onLongForward = { player.seekTo((player.currentPosition + longSkip).coerceAtMost(player.duration.coerceAtLeast(0L))); refresh() },
                    onOrientation = ::cycleOrientation,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        } else if (controlsLocked && !pictureInPictureMode && lockVisible) {
            HtmlVideoButton(Icons.Outlined.Lock, appText("解锁控件", english), { controlsLocked = false; refresh() }, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).zIndex(11f))
        }
        gestureHud?.let { message ->
            Box(Modifier.align(Alignment.Center).zIndex(12f).background(Color.Black.copy(.72f), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(message, color = Color.White, fontSize = 14.sp)
            }
        }
        }
    }
    if (showSpeed) {
        val speedOptions = remember { listOf("0.25x", "0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x", "4x") }
        VaultWheelChoiceSheet(
            title = appText("播放速度", english),
            options = speedOptions,
            selected = if (speed == 1f) "1x" else "${speed}x",
            onDismiss = { showSpeed = false; if (wasPlayingForSpeed) player.play() },
            onApply = { selected ->
                speed = selected.removeSuffix("x").toFloatOrNull() ?: 1f
                player.setPlaybackSpeed(speed)
                showSpeed = false
                if (wasPlayingForSpeed) player.play()
            }
        )
    }
}

@Composable
private fun HtmlVideoButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.White, iconSize: androidx.compose.ui.unit.Dp = 25.dp) {
    IconButton(onClick = onClick, modifier = modifier.size(46.dp)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SpacerBottomControls(
    position: Long, duration: Long, playing: Boolean, mode: Int, longSkipEnabled: Boolean,
    onSeek: (Long) -> Unit, onMode: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onPlay: () -> Unit, onRewind: () -> Unit, onForward: () -> Unit, onLongRewind: () -> Unit,
    onLongForward: () -> Unit, onOrientation: () -> Unit, modifier: Modifier = Modifier
) {
    val english = LocalAppEnglish.current
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.82f)))).padding(start = 14.dp, end = 14.dp, top = 26.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(htmlTime(position), color = Color.White, fontSize = 12.sp, modifier = Modifier.width(48.dp))
            Slider(
                value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.weight(1f).height(24.dp).graphicsLayer { scaleY = .65f },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(.28f))
            )
            Text(htmlTime(duration), color = Color.White, fontSize = 12.sp, modifier = Modifier.width(48.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            HtmlVideoGridButton(when (mode) { 0 -> Icons.Outlined.FormatListNumbered; 2 -> Icons.Outlined.Shuffle; 3 -> Icons.Outlined.StopCircle; else -> Icons.Outlined.Repeat }, appText("播放顺序", english), onMode)
            HtmlVideoGridButton(Icons.Outlined.SkipPrevious, appText("上一个视频", english), onPrevious)
            if (longSkipEnabled) HtmlVideoGridButton(Icons.Outlined.FastRewind, appText("长快退", english), onLongRewind)
            HtmlVideoGridButton(Icons.Outlined.FastRewind, appText("快退", english), onRewind)
            HtmlVideoGridButton(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, appText(if (playing) "暂停" else "播放", english), onPlay, iconSize = if (longSkipEnabled) 34.dp else 42.dp)
            HtmlVideoGridButton(Icons.Outlined.FastForward, appText("快进", english), onForward)
            if (longSkipEnabled) HtmlVideoGridButton(Icons.Outlined.FastForward, appText("长快进", english), onLongForward)
            HtmlVideoGridButton(Icons.Outlined.SkipNext, appText("下一个视频", english), onNext)
            HtmlVideoGridButton(Icons.Outlined.Fullscreen, appText("自适应", english), onOrientation)
        }
    }
}

@Composable
private fun RowScope.HtmlVideoGridButton(icon: ImageVector, label: String, onClick: () -> Unit, iconSize: androidx.compose.ui.unit.Dp = 25.dp) {
    Box(Modifier.weight(1f).height(46.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun HtmlMiniVideoPlayer(
    player: ExoPlayer,
    playing: Boolean,
    seek: Long,
    onBackground: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color.Transparent),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            Modifier.padding(end = 12.dp, bottom = 84.dp).width(250.dp)
                .clip(RoundedCornerShape(7.dp)).background(Color.Black)
        ) {
            AndroidView(
                factory = { context -> PlayerView.inflate(context, com.example.album.R.layout.view_html_video_player, null) as PlayerView },
                update = { it.player = player },
                modifier = Modifier.fillMaxWidth().height(141.dp)
            )
            Row(
                Modifier.align(Alignment.TopEnd).background(Color.Black.copy(.55f), RoundedCornerShape(bottomStart = 8.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                HtmlVideoButton(Icons.Outlined.Headphones, "后台播放", onBackground, Modifier.size(38.dp), iconSize = 21.dp)
                HtmlVideoButton(Icons.Outlined.Fullscreen, "恢复全屏", onRestore, Modifier.size(38.dp), iconSize = 21.dp)
                HtmlVideoButton(Icons.Outlined.StopCircle, "关闭", onClose, Modifier.size(38.dp), iconSize = 21.dp)
            }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).background(Color.Black.copy(.55f), CircleShape),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                HtmlVideoButton(Icons.Outlined.FastRewind, "快退", { player.seekTo((player.currentPosition - seek).coerceAtLeast(0L)) }, Modifier.size(36.dp), iconSize = 20.dp)
                HtmlVideoButton(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "播放", { if (player.isPlaying) player.pause() else player.play() }, Modifier.size(36.dp), iconSize = 20.dp)
                HtmlVideoButton(Icons.Outlined.FastForward, "快进", { player.seekTo((player.currentPosition + seek).coerceAtMost(player.duration.coerceAtLeast(0L))) }, Modifier.size(36.dp), iconSize = 20.dp)
            }
        }
    }
}

private fun htmlTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}

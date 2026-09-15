@file:Suppress("UnsafeOptInUsageError")

package com.example.album.ui.components

import android.app.Activity
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.album.data.MediaItem
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.appSeekText
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Containers that neither Media3 nor the Android platform extractors can
 * demux. They are routed to the bundled LibVLC player.
 *
 * AVI (RIFF) and MPEG-PS (.mpg/.mpeg/.vob/...) used to be listed here, but
 * Media3's `AviExtractor` and `PsExtractor` read those containers, so they now
 * play in the main player and only fall back to LibVLC if the codec inside is
 * unsupported.
 */
internal fun requiresVlcPlayback(item: MediaItem): Boolean {
    val mime = item.mimeType.lowercase()
    val name = item.name.lowercase()
    if (mime.startsWith("audio/")) return false
    val extensions = listOf(
        ".wmv", ".asf", ".rmvb", ".rm", ".ogm"
    )
    return mime.contains("x-ms-wmv") ||
        mime.contains("x-ms-asf") ||
        mime.contains("vnd.rn-realmedia") ||
        extensions.any { name.endsWith(it) }
}

/**
 * Containers that Media3 demuxes with its own extractors (AVI, MPEG-PS)
 * instead of the platform ones. ExoPlayer happily opens those files, but when
 * the codec inside is unsupported it reports no error at all and stalls on a
 * black screen, so the main player watches for a missing video track and hands
 * these files to LibVLC.
 */
internal fun usesMedia3LegacyContainer(item: MediaItem): Boolean {
    val mime = item.mimeType.lowercase()
    if (mime.startsWith("audio/")) return false
    val name = item.name.lowercase()
    return mime.contains("avi") ||
        mime.contains("x-msvideo") ||
        mime.contains("msvideo") ||
        mime.contains("divx") ||
        mime == "video/mp2p" ||
        name.endsWith(".avi") ||
        name.endsWith(".divx") ||
        name.endsWith(".xvid") ||
        name.endsWith(".mpg") ||
        name.endsWith(".mpeg") ||
        name.endsWith(".mpe") ||
        name.endsWith(".m1v") ||
        name.endsWith(".m2v") ||
        name.endsWith(".mpv") ||
        name.endsWith(".vob")
}

/**
 * LibVLC based player used for AVI and other containers ExoPlayer cannot
 * read. It keeps the same look as the main player: tap to toggle the
 * controls, double tap on the sides to seek, and the system
 * picture-in-picture window when the app goes to the background.
 */
@Composable
internal fun VlcVideoPlayer(
    current: MediaItem,
    onBack: () -> Unit,
    pictureInPictureMode: Boolean,
    onEnterPictureInPicture: () -> Boolean,
    onAutoEnterPictureInPictureChange: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    miniMode: Boolean = false,
    onMiniModeChange: (Boolean) -> Unit = {},
    favorite: Boolean = false,
    onFavorite: () -> Unit = {},
    onShare: () -> Unit = {},
    onWallpaper: () -> Unit = {},
    onInfo: () -> Unit = {}
) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val preferences = remember {
        context.getSharedPreferences("album_settings", Context.MODE_PRIVATE)
    }
    var playing by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var miniWidthPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var miniOffset by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var miniInitialized by remember { androidx.compose.runtime.mutableStateOf(false) }
    var viewportSize by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var orientationMode by remember {
        androidx.compose.runtime.mutableIntStateOf(preferences.getInt("video_orientation_mode", 0).coerceIn(0, 3))
    }
    val activity = context as? Activity
    var speed by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var playerMenuOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    var gestureHud by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var temporaryFastPlayback by remember { androidx.compose.runtime.mutableStateOf(false) }
    var gestureWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var gestureHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var brightness by remember {
        androidx.compose.runtime.mutableFloatStateOf(
            ((context as? Activity)?.window?.attributes?.screenBrightness ?: .5f).takeIf { it >= 0f } ?: .5f
        )
    }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager }
    var volume by remember {
        val max = audioManager?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
        androidx.compose.runtime.mutableFloatStateOf(
            ((audioManager?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) ?: max).toFloat() / max).coerceIn(0f, 1f)
        )
    }
    val seekPauseRatio = preferences.getString("video_seek_pause_ratio", "1:1:1") ?: "1:1:1"
    val brightnessVolumeRatio = preferences.getString("video_brightness_volume_ratio", "1:1") ?: "1:1"
    fun setBrightnessValue(value: Float) {
        brightness = value.coerceIn(0f, 1f)
        (context as? Activity)?.let { host ->
            host.window.attributes = host.window.attributes.apply { screenBrightness = brightness.coerceAtLeast(.01f) }
        }
    }
    fun setVolumeValue(value: Float) {
        volume = value.coerceIn(0f, 1f)
        val max = audioManager?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: return
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (volume * max).toInt().coerceIn(0, max), 0)
    }
    val skip = (preferences.getString("normal_skip", "10秒")?.filter(Char::isDigit)?.toLongOrNull() ?: 10L) * 1000L
    val autoHide = preferences.getBoolean("video_auto_hide", true)
    LaunchedEffect(playing, current.width, current.height) {
        onAutoEnterPictureInPictureChange(
            preferences.getBoolean("video_auto_mini", false) && playing,
            current.width,
            current.height
        )
    }
    DisposableEffect(Unit) {
        onDispose { onAutoEnterPictureInPictureChange(false, 0, 0) }
    }

    val libVlc = remember {
        LibVLC(context, arrayListOf("--no-drop-late-frames", "--no-skip-frames", "--audio-time-stretch"))
    }
    val mediaPlayer = remember { MediaPlayer(libVlc) }

    DisposableEffect(mediaPlayer, libVlc) {
        onDispose {
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.detachViews() }
            runCatching { mediaPlayer.release() }
            runCatching { libVlc.release() }
        }
    }
    DisposableEffect(current.uri) {
        // LibVLC cannot open MediaStore/SAF content:// URIs as an MRL, so hand
        // it the underlying file descriptor instead.
        var descriptor: ParcelFileDescriptor? = null
        val media = runCatching {
            val fromDescriptor = if (current.uri.scheme?.lowercase() == "content") {
                descriptor = context.contentResolver.openFileDescriptor(current.uri, "r")
                descriptor?.fileDescriptor?.let { Media(libVlc, it) }
            } else {
                null
            }
            (fromDescriptor ?: Media(libVlc, current.uri)).apply {
                setHWDecoderEnabled(true, false)
                addOption(":network-caching=300")
            }
        }.getOrNull()
        if (media != null) {
            runCatching {
                mediaPlayer.media = media
                mediaPlayer.play()
            }
        }
        onDispose {
            runCatching { mediaPlayer.stop() }
            media?.let { runCatching { it.release() } }
            descriptor?.let { runCatching { it.close() } }
        }
    }
    LaunchedEffect(mediaPlayer) {
        while (true) {
            playing = runCatching { mediaPlayer.isPlaying }.getOrDefault(false)
            position = runCatching { mediaPlayer.time }.getOrDefault(0L).coerceAtLeast(0L)
            duration = runCatching { mediaPlayer.length }.getOrDefault(0L).coerceAtLeast(0L)
            delay(250L)
        }
    }
    LaunchedEffect(controlsVisible, controlsInteraction, autoHide) {
        if (controlsVisible && autoHide) {
            delay(3_000L)
            controlsVisible = false
        }
    }
    DisposableEffect(context) {
        val activity = context as? Activity
        val lifecycle = (activity as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val pauseOnBackground = preferences.getBoolean("video_pause_on_background", true)
                val autoMini = preferences.getBoolean("video_auto_mini", false)
                if (autoMini && !pictureInPictureMode) {
                    if (!onEnterPictureInPicture() && pauseOnBackground) {
                        runCatching { mediaPlayer.pause() }
                    }
                } else if (pauseOnBackground) {
                    runCatching { mediaPlayer.pause() }
                }
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    fun togglePlayback() {
        runCatching { if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play() }
        controlsVisible = true
        controlsInteraction++
    }

    fun seekBy(deltaMs: Long) {
        val target = (mediaPlayer.time + deltaMs).coerceAtLeast(0L)
        runCatching { mediaPlayer.time = target }
    }

    fun timeText(value: Long): String {
        val seconds = value.coerceAtLeast(0L) / 1000L
        return "%02d:%02d".format(seconds / 60L, seconds % 60L)
    }

    val videoAspect = if (current.width > 0 && current.height > 0) {
        (current.width.toFloat() / current.height.toFloat()).coerceIn(.45f, 2.4f)
    } else {
        16f / 9f
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(miniMode, viewportSize, videoAspect) {
        if (!miniMode || viewportSize == androidx.compose.ui.unit.IntSize.Zero) return@LaunchedEffect
        val horizontalLimit = (viewportSize.width - with(density) { 24.dp.toPx() }).coerceAtLeast(1f)
        val verticalLimit = (viewportSize.height - with(density) { 100.dp.toPx() }).coerceAtLeast(1f)
        val targetWidth = with(density) { 250.dp.toPx() }
            .coerceAtMost(horizontalLimit)
            .coerceAtMost(verticalLimit * videoAspect)
        if (!miniInitialized) {
            miniWidthPx = targetWidth
            val margin = with(density) { 12.dp.toPx() }
            val bottom = with(density) { 84.dp.toPx() }
            miniOffset = androidx.compose.ui.geometry.Offset(
                x = (viewportSize.width - targetWidth - margin).coerceAtLeast(margin),
                y = (viewportSize.height - targetWidth / videoAspect - bottom).coerceAtLeast(margin)
            )
            miniInitialized = true
        }
    }
    Box(
        Modifier.fillMaxSize()
            .background(if (miniMode) Color.Transparent else Color.Black)
            .onSizeChanged { viewportSize = it }
    ) {
        Box(
            Modifier.then(
                if (miniMode) {
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(miniOffset.x.roundToInt(), miniOffset.y.roundToInt()) }
                        .width(with(density) { miniWidthPx.toDp() })
                        .aspectRatio(videoAspect)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                } else {
                    Modifier.fillMaxSize()
                }
            ).background(Color.Black)
        ) {
        AndroidView(
            factory = { viewContext ->
                VLCVideoLayout(viewContext).also { layout ->
                    runCatching { mediaPlayer.attachViews(layout, null, false, false) }
                }
            },
            update = { },
            modifier = Modifier.fillMaxSize()
        )
        if (miniMode) {
            MiniWindowOverlay(
                playing = playing,
                seekBack = { seekBy(-skip); controlsInteraction++ },
                seekForward = { seekBy(skip); controlsInteraction++ },
                onTogglePlay = { togglePlayback() },
                onMove = { delta ->
                    val height = miniWidthPx / videoAspect
                    miniOffset = androidx.compose.ui.geometry.Offset(
                        (miniOffset.x + delta.x).coerceIn(8f, (viewportSize.width - miniWidthPx - 8f).coerceAtLeast(8f)),
                        (miniOffset.y + delta.y).coerceIn(8f, (viewportSize.height - height - 8f).coerceAtLeast(8f))
                    )
                },
                onResize = { delta, fromLeft, fromTop ->
                    val ratio = videoAspect
                    val horizontalDelta = if (fromLeft) -delta.x else delta.x
                    val verticalDelta = (if (fromTop) -delta.y else delta.y) * ratio
                    val sizeDelta = if (abs(horizontalDelta) >= abs(verticalDelta)) horizontalDelta else verticalDelta
                    val oldWidth = miniWidthPx
                    val oldHeight = oldWidth / ratio
                    val oldRight = miniOffset.x + oldWidth
                    val oldBottom = miniOffset.y + oldHeight
                    val minimum = min(with(density) { 180.dp.toPx() }, (viewportSize.width - 16f).coerceAtLeast(1f))
                    val maximum = min(
                        (viewportSize.width - 16f).coerceAtLeast(minimum),
                        ((viewportSize.height - 16f) * ratio).coerceAtLeast(minimum)
                    )
                    val newWidth = (oldWidth + sizeDelta).coerceIn(minimum, maximum)
                    val newHeight = newWidth / ratio
                    miniWidthPx = newWidth
                    miniOffset = androidx.compose.ui.geometry.Offset(
                        (if (fromLeft) oldRight - newWidth else miniOffset.x)
                            .coerceIn(8f, (viewportSize.width - newWidth - 8f).coerceAtLeast(8f)),
                        (if (fromTop) oldBottom - newHeight else miniOffset.y)
                            .coerceIn(8f, (viewportSize.height - newHeight - 8f).coerceAtLeast(8f))
                    )
                },
                onRestore = { onMiniModeChange(false) },
                onClose = onBack
            )
        }
        if (!miniMode) {
            Box(
                Modifier.fillMaxSize()
                    .onSizeChanged { gestureWidth = it.width; gestureHeight = it.height }
                    .pointerInput(controlsVisible, seekPauseRatio, brightnessVolumeRatio, skip) {
                        detectTapGestures(
                            onPress = {
                                val releasedBeforeLongPress = kotlinx.coroutines.withTimeoutOrNull(460L) { tryAwaitRelease() }
                                if (releasedBeforeLongPress == null) {
                                    val wasPlaying = mediaPlayer.isPlaying
                                    temporaryFastPlayback = true
                                    runCatching { mediaPlayer.rate = 2f }
                                    runCatching { mediaPlayer.play() }
                                    gestureHud = "2x ${appText("播放", english)}"
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        runCatching { mediaPlayer.rate = speed }
                                        if (!wasPlaying) runCatching { mediaPlayer.pause() }
                                        temporaryFastPlayback = false
                                        gestureHud = if (speed == 1f) "1x" else "${speed}x"
                                    }
                                }
                            },
                            onTap = {
                                if (controlsVisible) controlsVisible = false else {
                                    controlsVisible = true
                                    controlsInteraction++
                                }
                            },
                            onDoubleTap = { offset ->
                                val width = size.width.coerceAtLeast(1)
                                when (doubleTapZone(offset.x / width, seekPauseRatio)) {
                                    -1 -> {
                                        seekBy(-skip)
                                        gestureHud = appSeekText("快退", skip, english)
                                    }
                                    1 -> {
                                        seekBy(skip)
                                        gestureHud = appSeekText("快进", skip, english)
                                    }
                                    else -> {
                                        togglePlayback()
                                        gestureHud = appText(if (playing) "暂停" else "播放", english)
                                    }
                                }
                                controlsVisible = true
                                controlsInteraction++
                            }
                        )
                    }
                    .pointerInput(controlsVisible, mediaPlayer, brightnessVolumeRatio) {
                        var totalDragX = 0f
                        var totalDragY = 0f
                        var gestureStartX = 0f
                        var gestureMode = 0
                        detectDragGestures(
                            onDragStart = { start ->
                                totalDragX = 0f
                                totalDragY = 0f
                                gestureStartX = start.x
                                gestureMode = 0
                            },
                            onDrag = { change, dragAmount ->
                                if (temporaryFastPlayback) return@detectDragGestures
                                change.consume()
                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y
                                if (gestureMode == 0 && kotlin.math.hypot(totalDragX.toDouble(), totalDragY.toDouble()) > 10.0) {
                                    val angle = Math.toDegrees(kotlin.math.atan2(kotlin.math.abs(totalDragY).toDouble(), kotlin.math.abs(totalDragX).toDouble()))
                                    gestureMode = if (angle <= 60.0) 1 else verticalGestureZone(gestureStartX / gestureWidth.coerceAtLeast(1), brightnessVolumeRatio)
                                }
                                when (gestureMode) {
                                    1 -> {
                                        val delta = (dragAmount.x / gestureWidth.coerceAtLeast(1) * skip).toLong()
                                        seekBy(delta)
                                        gestureHud = timeText((position + delta).coerceAtLeast(0L))
                                    }
                                    2 -> {
                                        setBrightnessValue(brightness - dragAmount.y / gestureHeight.coerceAtLeast(1) * 0.5f)
                                        gestureHud = "${appText("亮度", english)} ${(brightness * 100).toInt()}%"
                                    }
                                    3 -> {
                                        setVolumeValue(volume - dragAmount.y / gestureHeight.coerceAtLeast(1) * 0.5f)
                                        gestureHud = "${appText("音量", english)} ${(volume * 100).toInt()}%"
                                    }
                                }
                                controlsVisible = true
                                controlsInteraction++
                            },
                            onDragEnd = { gestureMode = 0 },
                            onDragCancel = { gestureMode = 0 }
                        )
                    }
            )
            gestureHud?.let { hud ->
                Box(
                    Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .72f)).padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(hud, color = Color.White, fontSize = 14.sp)
                }
            }
            if (controlsVisible) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(.72f), Color.Transparent)))
                    .statusBarsPadding().height(72.dp).padding(start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, appText("返回", english), tint = Color.White)
                }
                Text(
                    current.name,
                    Modifier.weight(1f).padding(start = 6.dp, end = 6.dp),
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val speedSteps = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)
                TextButton(
                    onClick = {
                        val next = speedSteps[(speedSteps.indexOfFirst { it == speed }.coerceAtLeast(0) + 1) % speedSteps.size]
                        speed = next
                        runCatching { mediaPlayer.rate = next }
                        controlsInteraction++
                    },
                    modifier = Modifier.size(46.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (speed == 1f) "1.0x" else "${speed}x", color = Color.White, fontSize = 13.sp, maxLines = 1)
                }
                IconButton(onClick = { controlsInteraction++; onFavorite() }, modifier = Modifier.size(46.dp)) {
                    Icon(
                        if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        appText("收藏", english),
                        tint = if (favorite) Color(0xFFFFD60A) else Color.White
                    )
                }
                IconButton(onClick = { controlsInteraction++; playerMenuOpen = true }, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Outlined.MoreVert, appText("菜单", english), tint = Color.White)
                }
            }
            Row(
                Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMiniModeChange(true) }) {
                    Icon(Icons.Outlined.PictureInPictureAlt, appText("小窗", english), tint = Color.White, modifier = Modifier.size(25.dp))
                }
            }
            Row(
                Modifier.align(Alignment.Center).padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { seekBy(-skip); controlsInteraction++ }) {
                    Icon(Icons.Outlined.FastRewind, appText("快退", english), tint = Color.White, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { togglePlayback() }) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        appText(if (playing) "暂停" else "播放", english),
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                IconButton(onClick = { seekBy(skip); controlsInteraction++ }) {
                    Icon(Icons.Outlined.FastForward, appText("快进", english), tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.84f))))
                    .padding(start = 14.dp, end = 14.dp, top = 30.dp, bottom = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(timeText(position), color = Color.White, fontSize = 12.sp, modifier = Modifier.size(width = 48.dp, height = 24.dp))
                    VaultLineSlider(
                        value = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { fraction ->
                            runCatching {
                                mediaPlayer.time = (fraction * duration).toLong().coerceAtLeast(0L)
                            }
                            controlsInteraction++
                        },
                        modifier = Modifier.weight(1f).height(24.dp),
                        activeColor = Color.White,
                        inactiveColor = Color.White.copy(alpha = .35f),
                        thumbColor = Color.White,
                        thumbBorderColor = Color.White
                    )
                    Text(timeText(duration), color = Color.White, fontSize = 12.sp, modifier = Modifier.size(width = 48.dp, height = 24.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val next = (orientationMode + 1) % 4
                            orientationMode = next
                            preferences.edit().putInt("video_orientation_mode", next).apply()
                            activity?.requestedOrientation = when (next) {
                                1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                3 -> if (current.width > current.height && current.height > 0) {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                } else {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                                else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                            }
                            controlsInteraction++
                        },
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(
                            when (orientationMode) {
                                1 -> Icons.Outlined.ScreenRotation
                                2 -> Icons.Outlined.ScreenRotation
                                else -> Icons.Outlined.ScreenRotation
                            },
                            appText(orientationModeLabel(orientationMode), english),
                            tint = Color.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    Text(
                        appText(orientationModeLabel(orientationMode), english),
                        Modifier.weight(2f),
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        }
        }
    }
    if (playerMenuOpen) {
        AlertDialog(
            onDismissRequest = { playerMenuOpen = false },
            containerColor = Color.Black.copy(alpha = .85f),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text(appText("菜单", english)) },
            text = {
                Column {
                    DropdownMenuItem(
                        text = { Text(appText("分享", english), color = Color.White) },
                        leadingIcon = { Icon(Icons.Outlined.Share, null, tint = Color.White) },
                        onClick = { playerMenuOpen = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text(appText("设置为壁纸", english), color = Color.White) },
                        leadingIcon = { Icon(Icons.Outlined.Wallpaper, null, tint = Color.White) },
                        onClick = { playerMenuOpen = false; onWallpaper() }
                    )
                    DropdownMenuItem(
                        text = { Text(appText("信息", english), color = Color.White) },
                        leadingIcon = { Icon(Icons.Outlined.Info, null, tint = Color.White) },
                        onClick = { playerMenuOpen = false; onInfo() }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { playerMenuOpen = false }) { Text(appText("关闭", english)) } }
        )
    }
}

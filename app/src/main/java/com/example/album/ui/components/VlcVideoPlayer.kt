@file:Suppress("UnsafeOptInUsageError")

package com.example.album.ui.components

import android.app.Activity
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * True when the platform has no decoder for the video track (or cannot even
 * parse the container). Such files are played with LibVLC instead.
 */
internal suspend fun platformLacksVideoDecoder(context: Context, item: MediaItem): Boolean =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val extractor = android.media.MediaExtractor()
            try {
                val descriptor = context.contentResolver.openFileDescriptor(item.uri, "r")
                if (descriptor != null) {
                    descriptor.use { extractor.setDataSource(it.fileDescriptor) }
                } else {
                    extractor.setDataSource(context, item.uri, null)
                }
                var hasVideoTrack = false
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                    if (!mime.startsWith("video/")) continue
                    hasVideoTrack = true
                    val decoder = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
                        .findDecoderForFormat(format)
                    if (decoder == null) return@runCatching true
                }
                !hasVideoTrack
            } finally {
                runCatching { extractor.release() }
            }
        }.getOrDefault(true)
    }

/**
 * Containers that the Android platform extractors (and therefore Media3)
 * cannot demux. They are routed to the bundled LibVLC player instead.
 */
internal fun requiresVlcPlayback(item: MediaItem): Boolean {
    val mime = item.mimeType.lowercase()
    val name = item.name.lowercase()
    if (mime.startsWith("audio/")) return false
    val extensions = listOf(
        ".avi", ".divx", ".xvid", ".wmv", ".asf", ".rmvb", ".rm",
        ".mpg", ".mpeg", ".mpe", ".m1v", ".m2v", ".mpv", ".vob", ".ogm"
    )
    return mime.contains("avi") ||
        mime.contains("x-msvideo") ||
        mime.contains("msvideo") ||
        mime.contains("divx") ||
        mime.contains("x-ms-wmv") ||
        mime.contains("x-ms-asf") ||
        mime.contains("mpeg") ||
        mime.contains("mp2p") ||
        mime.contains("mp2t") ||
        mime.contains("vnd.rn-realmedia") ||
        extensions.any { name.endsWith(it) }
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
    onMiniModeChange: (Boolean) -> Unit = {}
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
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (controlsVisible) controlsVisible = false else {
                            controlsVisible = true
                            controlsInteraction++
                        }
                    },
                    onDoubleTap = { offset ->
                        val width = size.width.coerceAtLeast(1)
                        when {
                            offset.x < width / 3f -> seekBy(-skip)
                            offset.x > width * 2f / 3f -> seekBy(skip)
                            else -> togglePlayback()
                        }
                        controlsVisible = true
                        controlsInteraction++
                    }
                )
            }
        )
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
                IconButton(onClick = { onMiniModeChange(true) }, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Outlined.PictureInPictureAlt, appText("小窗", english), tint = Color.White)
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
}

package com.example.album.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.OrientationEventListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings as SettingsIcon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem.Builder
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.example.album.data.MediaItem
import com.example.album.playback.MediaPlaybackService
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.theme.VaultDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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

private val HtmlFullscreenIcon: ImageVector by lazy {
    ImageVector.Builder("html-fullscreen", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.7f) {
            moveTo(8f, 3f); lineTo(3f, 3f); lineTo(3f, 8f)
            moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f)
            moveTo(8f, 21f); lineTo(3f, 21f); lineTo(3f, 16f)
            moveTo(16f, 21f); lineTo(21f, 21f); lineTo(21f, 16f)
        }
    }.build()
}

private val HtmlLandscapeIcon: ImageVector by lazy {
    ImageVector.Builder("html-landscape", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.7f) {
            moveTo(5f, 6f); lineTo(19f, 6f); lineTo(21f, 8f); lineTo(21f, 16f)
            lineTo(19f, 18f); lineTo(5f, 18f); lineTo(3f, 16f); lineTo(3f, 8f)
            lineTo(5f, 6f); close()
        }
    }.build()
}

private val HtmlPortraitIcon: ImageVector by lazy {
    ImageVector.Builder("html-portrait", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.7f) {
            moveTo(9f, 3f); lineTo(15f, 3f); lineTo(17f, 5f); lineTo(17f, 19f)
            lineTo(15f, 21f); lineTo(9f, 21f); lineTo(7f, 19f); lineTo(7f, 5f)
            lineTo(9f, 3f); close()
        }
    }.build()
}

private fun htmlOrientationIcon(mode: Int): ImageVector = when (mode) {
    1 -> HtmlLandscapeIcon
    2 -> HtmlPortraitIcon
    else -> HtmlFullscreenIcon
}

private fun htmlLineIcon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.7f, pathBuilder = draw)
    }.build()

private val HtmlShareIcon = htmlLineIcon("html-share") { moveTo(12f, 15f); lineTo(12f, 3f); moveTo(12f, 3f); lineTo(8f, 7f); moveTo(12f, 3f); lineTo(16f, 7f); moveTo(5f, 11f); lineTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 11f) }
private val HtmlSequenceIcon = htmlLineIcon("html-sequence") { moveTo(3f, 5f); lineTo(20f, 5f); moveTo(16f, 2f); lineTo(20f, 5f); moveTo(3f, 12f); lineTo(20f, 12f); moveTo(16f, 9f); lineTo(20f, 12f); moveTo(3f, 19f); lineTo(20f, 19f); moveTo(16f, 16f); lineTo(20f, 19f) }
private val HtmlRepeatIcon = htmlLineIcon("html-repeat") { moveTo(17f, 2f); lineTo(21f, 6f); lineTo(17f, 10f); moveTo(3f, 6f); lineTo(21f, 6f); moveTo(7f, 22f); lineTo(3f, 18f); lineTo(7f, 14f); moveTo(21f, 18f); lineTo(3f, 18f) }
private val HtmlShuffleIcon = htmlLineIcon("html-shuffle") { moveTo(3f, 7f); lineTo(6f, 7f); lineTo(18f, 17f); lineTo(21f, 17f); moveTo(18f, 14f); lineTo(21f, 17f); lineTo(18f, 20f); moveTo(3f, 17f); lineTo(6f, 17f); lineTo(15f, 7f); lineTo(21f, 7f); moveTo(18f, 4f); lineTo(21f, 7f); lineTo(18f, 10f) }
private val HtmlPreviousIcon = htmlLineIcon("html-previous") { moveTo(6f, 5f); lineTo(6f, 19f); moveTo(18f, 6f); lineTo(9f, 12f); lineTo(18f, 18f); close() }
private val HtmlNextIcon = htmlLineIcon("html-next") { moveTo(18f, 5f); lineTo(18f, 19f); moveTo(6f, 6f); lineTo(15f, 12f); lineTo(6f, 18f); close() }
private val HtmlRewindIcon = ImageVector.Builder("html-rewind", 24.dp, 24.dp, 24f, 24f).apply { path(fill = androidx.compose.ui.graphics.SolidColor(Color.White), pathBuilder = { moveTo(11f, 6f); lineTo(4f, 12f); lineTo(11f, 18f); close(); moveTo(20f, 6f); lineTo(13f, 12f); lineTo(20f, 18f); close() }) }.build()
private val HtmlForwardIcon = ImageVector.Builder("html-forward", 24.dp, 24.dp, 24f, 24f).apply { path(fill = androidx.compose.ui.graphics.SolidColor(Color.White), pathBuilder = { moveTo(4f, 6f); lineTo(11f, 12f); lineTo(4f, 18f); close(); moveTo(13f, 6f); lineTo(20f, 12f); lineTo(13f, 18f); close() }) }.build()
private val HtmlLongRewindIcon = ImageVector.Builder("html-long-rewind", 24.dp, 24.dp, 24f, 24f).apply { path(fill = androidx.compose.ui.graphics.SolidColor(Color.White), pathBuilder = { moveTo(8f, 7f); lineTo(2f, 12f); lineTo(8f, 17f); close(); moveTo(15f, 7f); lineTo(9f, 12f); lineTo(15f, 17f); close(); moveTo(22f, 7f); lineTo(16f, 12f); lineTo(22f, 17f); close() }) }.build()
private val HtmlLongForwardIcon = ImageVector.Builder("html-long-forward", 24.dp, 24.dp, 24f, 24f).apply { path(fill = androidx.compose.ui.graphics.SolidColor(Color.White), pathBuilder = { moveTo(2f, 7f); lineTo(8f, 12f); lineTo(2f, 17f); close(); moveTo(9f, 7f); lineTo(15f, 12f); lineTo(9f, 17f); close(); moveTo(16f, 7f); lineTo(22f, 12f); lineTo(16f, 17f); close() }) }.build()
private val HtmlPlayIcon = ImageVector.Builder("html-play", 24.dp, 24.dp, 24f, 24f).apply { path(fill = androidx.compose.ui.graphics.SolidColor(Color.White), pathBuilder = { moveTo(7f, 4f); lineTo(20f, 12f); lineTo(7f, 20f); close() }) }.build()
private val HtmlPauseIcon = ImageVector.Builder("html-pause", 24.dp, 24.dp, 24f, 24f).apply { path(fill = androidx.compose.ui.graphics.SolidColor(Color.White), pathBuilder = { moveTo(7f, 4f); lineTo(11f, 4f); lineTo(11f, 20f); lineTo(7f, 20f); close(); moveTo(14f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(14f, 20f); close() }) }.build()
private val HtmlPipIcon = htmlLineIcon("html-pip") { moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 20f); lineTo(3f, 20f); close(); moveTo(12f, 11f); lineTo(19f, 11f); lineTo(19f, 17f); lineTo(12f, 17f); close() }
private val HtmlBackgroundIcon = htmlLineIcon("html-background") {
    moveTo(4f, 13f); lineTo(4f, 10f); lineTo(5f, 7f); lineTo(7f, 5f); lineTo(10f, 4f); lineTo(14f, 4f); lineTo(17f, 5f); lineTo(19f, 7f); lineTo(20f, 10f); lineTo(20f, 13f)
    moveTo(4f, 13f); lineTo(7f, 13f); lineTo(7f, 19f); lineTo(5f, 19f); lineTo(3f, 17f); lineTo(3f, 14f); close()
    moveTo(20f, 13f); lineTo(17f, 13f); lineTo(17f, 19f); lineTo(19f, 19f); lineTo(21f, 17f); lineTo(21f, 14f); close()
}
private val HtmlLockIcon = htmlLineIcon("html-lock") {
    moveTo(5f, 10f); lineTo(19f, 10f); lineTo(19f, 21f); lineTo(5f, 21f); close()
    moveTo(8f, 10f); lineTo(8f, 7f); lineTo(9f, 4.5f); lineTo(11f, 3f); lineTo(13f, 3f); lineTo(15f, 4.5f); lineTo(16f, 7f); lineTo(16f, 10f)
}
private val HtmlStarIcon = htmlLineIcon("html-star") { moveTo(12f, 2.7f); lineTo(14.85f, 8.47f); lineTo(21.22f, 9.4f); lineTo(16.61f, 13.89f); lineTo(17.7f, 20.23f); lineTo(12f, 17.24f); lineTo(6.3f, 20.23f); lineTo(7.39f, 13.89f); lineTo(2.78f, 9.4f); lineTo(9.15f, 8.47f); close() }

/**
 * Official Media3 PlayerView implementation. The controller, timeline and
 * playlist navigation are provided by the maintained AndroidX UI instead of
 * custom Compose hit regions.
 */
@Composable
internal fun Media3VideoPlayer(
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
    onShare: () -> Unit,
    onSettings: () -> Unit = {},
    settingsVersion: Int = 0
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val english = LocalAppEnglish.current
    val preferences = remember { context.getSharedPreferences("album_settings", Context.MODE_PRIVATE) }
    var currentIndex by remember(videos, current.uri) {
        mutableIntStateOf(videos.indexOfFirst { it.uri == current.uri }.coerceAtLeast(0))
    }
    var playing by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var playerMenuOpen by remember { mutableStateOf(false) }
    val autoHideControls = preferences.getBoolean("video_auto_hide", true)
    val tapPause = preferences.getBoolean("video_tap_pause", false)
    val portraitTapPause = preferences.getBoolean("video_portrait_tap_pause", false)
    var mode by remember { mutableIntStateOf(0) }
    val latestMode by rememberUpdatedState(mode)
    var speed by remember { mutableFloatStateOf(1f) }
    var showSpeed by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var gestureHud by remember { mutableStateOf<String?>(null) }
    var temporaryFastPlayback by remember { mutableStateOf(false) }
    var gestureWidth by remember { mutableIntStateOf(0) }
    var gestureHeight by remember { mutableIntStateOf(0) }
    val normalSkip = (preferences.getString("normal_skip", "10秒")?.filter(Char::isDigit)?.toLongOrNull() ?: 10L) * 1000L
    val longSkip = (preferences.getString("long_skip_length", "30秒")?.filter(Char::isDigit)?.toLongOrNull() ?: 30L) * 1000L
    val longSkipEnabled = preferences.getBoolean("long_skip", false)
    val gestureSkip = (preferences.getString("gesture_seek", "90秒")?.filter(Char::isDigit)?.toLongOrNull() ?: 90L) * 1000L
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var brightness by remember {
        mutableFloatStateOf(((context as? Activity)?.window?.attributes?.screenBrightness ?: .5f).takeIf { it >= 0f } ?: .5f)
    }
    var volume by remember {
        val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
        mutableFloatStateOf(((audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: max).toFloat() / max).coerceIn(0f, 1f))
    }
    var orientationMode by remember { mutableIntStateOf(0) }
    var displayedOrientationMode by remember { mutableIntStateOf(0) }
    var sensorLandscape by remember { mutableStateOf(false) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val hostActivity = context as? Activity
    val initialScreenBrightness = remember(hostActivity) {
        hostActivity?.window?.attributes?.screenBrightness
    }
    val initialMediaVolume = remember(audioManager) {
        audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
    }
    var restoreScreenBrightness by remember(hostActivity) { mutableStateOf(initialScreenBrightness) }
    var restoreMediaVolume by remember(audioManager) { mutableStateOf(initialMediaVolume) }
    var restoreSystemBrightnessOnExit by remember { mutableStateOf(false) }
    var hasLeftPlayer by remember { mutableStateOf(false) }
    fun exitPlayer() {
        // Request portrait before the viewer's exit animation starts so the
        // window rotation happens in parallel with the surface dismissal.
        hostActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onBack()
    }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(angle: Int) {
                if (angle != ORIENTATION_UNKNOWN) sensorLandscape = angle in 60..120 || angle in 240..300
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    DisposableEffect(hostActivity) {
        val lifecycle = (hostActivity as? ComponentActivity)?.lifecycle
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
    // Let the player own the window orientation while it is open. This gives
    // the landscape layout the real physical window width instead of rotating
    // a portrait-sized canvas inside a portrait Activity.
    LaunchedEffect(orientationMode) {
        hostActivity?.requestedOrientation = when (orientationMode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }
    LaunchedEffect(orientationMode, viewportSize, sensorLandscape) {
        if (viewportSize == IntSize.Zero) return@LaunchedEffect
        val viewportLandscape = viewportSize.width > viewportSize.height
        val targetReached = when (orientationMode) {
            1 -> viewportLandscape
            2 -> !viewportLandscape
            else -> viewportLandscape == sensorLandscape
        }
        if (targetReached) displayedOrientationMode = orientationMode
    }
    DisposableEffect(hostActivity) {
        onDispose {
            hostActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            hostActivity?.let { activity ->
                if (restoreSystemBrightnessOnExit) {
                    activity.window.attributes = activity.window.attributes.apply { screenBrightness = -1f }
                } else restoreScreenBrightness?.let { brightness ->
                    activity.window.attributes = activity.window.attributes.apply {
                        screenBrightness = brightness
                    }
                }
                restoreMediaVolume?.let { volume ->
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                }
            }
        }
    }
    val player = remember(videos) {
        ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .build().apply {
                setSeekParameters(SeekParameters.EXACT)
                val items = videos.map { item ->
                    Builder()
                        .setUri(item.uri)
                        .setMediaId(item.uri.toString())
                        .setMimeType(item.mimeType.takeIf { it.startsWith("video/") && it != "video/*" })
                        .build()
                }
                setMediaItems(items, currentIndex, 0L)
                prepare()
                playWhenReady = preferences.getBoolean("video_autoplay", true)
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                videos.firstOrNull { it.uri.toString() == mediaItem?.mediaId }?.let { changed ->
                    currentIndex = videos.indexOf(changed).coerceAtLeast(0)
                    onCurrentChanged(changed)
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_ENDED || videos.isEmpty()) return
                when (latestMode) {
                    1 -> { player.seekTo(currentIndex, 0L); player.play() }
                    2 -> {
                        val next = (0 until videos.size).filter { it != currentIndex }.randomOrNull() ?: currentIndex
                        currentIndex = next
                        player.seekTo(next, 0L)
                        player.play()
                    }
                    3 -> player.pause()
                    else -> {
                        val next = (currentIndex + 1) % videos.size
                        currentIndex = next
                        player.seekTo(next, 0L)
                        player.play()
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            if (preferences.getBoolean("video_progress", true)) {
                preferences.edit().putLong("video_position_${current.uri.hashCode()}", player.currentPosition).apply()
            }
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            playing = player.isPlaying
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            delay(250L)
        }
    }

    LaunchedEffect(controlsVisible, controlsLocked, controlsInteraction, autoHideControls) {
        if (controlsVisible && !controlsLocked && autoHideControls) {
            delay(3_000L)
            controlsVisible = false
        }
    }
    LaunchedEffect(gestureHud, temporaryFastPlayback) {
        if (gestureHud != null && !temporaryFastPlayback) {
            delay(700L)
            gestureHud = null
        }
    }

    fun startBackgroundNow() {
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_START
            putExtra(MediaPlaybackService.EXTRA_URI, current.uri.toString())
            putExtra(MediaPlaybackService.EXTRA_NAME, current.name)
            putExtra(MediaPlaybackService.EXTRA_POSITION, player.currentPosition)
        }
        runCatching { ContextCompat.startForegroundService(context, intent) }
        player.pause()
        exitPlayer()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startBackgroundNow()
        } else {
            Toast.makeText(
                context,
                if (english) "Notification permission is required for background playback"
                else "后台播放需要通知权限",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun startBackground() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startBackgroundNow()
        }
    }

    fun refreshControls() {
        controlsVisible = true
        controlsInteraction++
    }

    fun adjacent(next: Boolean) {
        if (videos.isEmpty()) return
        val target = if (next) (currentIndex + 1) % videos.size else (currentIndex - 1 + videos.size) % videos.size
        currentIndex = target
        player.seekTo(target, 0L)
        player.prepare()
        player.playWhenReady = true
        onCurrentChanged(videos[target])
        refreshControls()
    }

    fun togglePlayback() {
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
        refreshControls()
    }

    fun cycleMode() {
        mode = (mode + 1) % 4
        player.repeatMode = if (mode == 1) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = mode == 2
        refreshControls()
        gestureHud = when (mode) {
            0 -> "顺序播放"
            1 -> "循环播放"
            2 -> "随机播放"
            else -> "播放完停止"
        }
    }

    fun timeText(value: Long): String {
        val seconds = value.coerceAtLeast(0L) / 1000L
        return "%02d:%02d".format(seconds / 60L, seconds % 60L)
    }

    fun setBrightness(value: Float) {
        brightness = value.coerceIn(0f, 1f)
        (context as? Activity)?.let { activity ->
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = brightness.coerceAtLeast(.01f) }
        }
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: return
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).roundToInt().coerceIn(0, max), 0)
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { viewportSize = it }, contentAlignment = Alignment.Center) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val windowIsLandscape = viewportSize.width > viewportSize.height
        val rotateCanvas = false
        Box(
            Modifier
                .then(if (rotateCanvas) Modifier.size(with(density) { viewportSize.height.toDp() }, with(density) { viewportSize.width.toDp() }) else Modifier.fillMaxSize())
                .graphicsLayer { rotationZ = if (rotateCanvas) 90f else 0f }
                .background(Color.Black)
        ) {
        AndroidView(
            factory = { viewContext ->
                (PlayerView.inflate(viewContext, com.example.album.R.layout.view_media3_video_player, null) as PlayerView).apply {
                    this.player = player
                    controllerShowTimeoutMs = 3_000
                    controllerHideOnTouch = true
                    controllerAutoShow = true
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        if (!pictureInPictureMode) {
            Box(
                Modifier.fillMaxSize()
                    .zIndex(1f)
                    .onSizeChanged { gestureWidth = it.width; gestureHeight = it.height }
                    .pointerInput(controlsLocked, normalSkip, speed, duration, orientationMode, tapPause, portraitTapPause, settingsVersion) {
                        detectTapGestures(
                            onPress = {
                                if (!controlsLocked) {
                                    val releasedBeforeLongPress = withTimeoutOrNull(460L) { tryAwaitRelease() }
                                    if (releasedBeforeLongPress == null) {
                                        val wasPlaying = player.playWhenReady
                                        temporaryFastPlayback = true
                                        player.setPlaybackSpeed(2f)
                                        player.play()
                                        gestureHud = "2x 播放"
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            player.setPlaybackSpeed(speed)
                                            if (wasPlaying) player.play() else player.pause()
                                            temporaryFastPlayback = false
                                            gestureHud = if (speed == 1f) "1x" else "${speed}x"
                                        }
                                    }
                                }
                            },
                            onTap = {
                                if (!controlsLocked) {
                                    val isLandscape = gestureWidth > gestureHeight || sensorLandscape
                                    val pauseOnTap = tapPause && (!portraitTapPause || !isLandscape)
                                    if (pauseOnTap) {
                                        togglePlayback()
                                        refreshControls()
                                    } else if (controlsVisible) controlsVisible = false else refreshControls()
                                }
                            },
                            onLongPress = { },
                            onDoubleTap = { offset ->
                                if (controlsLocked) return@detectTapGestures
                                val isLandscape = gestureWidth > gestureHeight || sensorLandscape
                                val pauseOnTap = tapPause && (!portraitTapPause || !isLandscape)
                                if (pauseOnTap) {
                                    if (controlsVisible) controlsVisible = false else refreshControls()
                                    return@detectTapGestures
                                }
                                val fraction = offset.x / size.width.coerceAtLeast(1)
                                when {
                                    fraction < 1f / 3f -> {
                                        seekToVideoFrame(player, player.currentPosition - normalSkip)
                                        gestureHud = "快退 ${normalSkip / 1000L}秒"
                                    }
                                    fraction > 2f / 3f -> {
                                        seekToVideoFrame(player, player.currentPosition + normalSkip)
                                        gestureHud = "快进 ${normalSkip / 1000L}秒"
                                    }
                                    else -> {
                                        togglePlayback()
                                        gestureHud = if (player.isPlaying) "播放" else "暂停"
                                    }
                                    
                                }
                                refreshControls()
                            }
                        )
                    }
                    .pointerInput(controlsLocked, duration, gestureSkip, temporaryFastPlayback) {
                        detectDragGestures(
                            onDragStart = { gestureHud = null },
                            onDrag = { change, dragAmount ->
                                if (controlsLocked || temporaryFastPlayback) return@detectDragGestures
                                change.consume()
                                val x = change.position.x
                                val horizontal = kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y)
                                if (horizontal) {
                                    val next = (player.currentPosition + (dragAmount.x / gestureWidth.coerceAtLeast(1) * gestureSkip).toLong())
                                        .coerceIn(0L, player.duration.coerceAtLeast(0L))
                                    seekToVideoFrame(player, next)
                                    val alignedNext = frameAlignedPosition(player, next)
                                    gestureHud = timeText(alignedNext)
                                } else if (x < gestureWidth / 2f) {
                                    setBrightness(brightness - dragAmount.y / gestureHeight.coerceAtLeast(1))
                                    gestureHud = "亮度 ${(brightness * 100).roundToInt()}%"
                                } else {
                                    setVolume(volume - dragAmount.y / gestureHeight.coerceAtLeast(1))
                                    gestureHud = "音量 ${(volume * 100).roundToInt()}%"
                                }
                                refreshControls()
                            },
                            onDragEnd = { refreshControls() }
                        )
                    }
            )
            if (!controlsVisible && !controlsLocked) {
                Box(
                    Modifier.fillMaxWidth().height(92.dp).align(Alignment.TopCenter)
                        .zIndex(2f)
                        .pointerInput(Unit) { detectTapGestures(onTap = { refreshControls() }) }
                )
                Box(
                    Modifier.fillMaxWidth().height(112.dp).align(Alignment.BottomCenter)
                        .zIndex(2f)
                        .pointerInput(Unit) { detectTapGestures(onTap = { refreshControls() }) }
                )
            }
            gestureHud?.let { hud ->
                Box(Modifier.align(Alignment.Center).zIndex(12f).background(Color.Black.copy(.72f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(hud, color = Color.White, fontSize = 14.sp)
                }
            }
        }
        if (!pictureInPictureMode && controlsVisible && !controlsLocked) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(3f)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(.72f), Color.Transparent)))
                    .then(
                        if (windowIsLandscape) {
                            Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp)
                        } else {
                            Modifier.statusBarsPadding().height(72.dp).padding(start = 8.dp, end = 8.dp)
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = ::exitPlayer, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, appText("返回", english), tint = Color.White)
                }
                Text(
                    current.name,
                    Modifier.weight(1f).padding(
                        start = if (windowIsLandscape) 4.dp else VaultDimens.HeaderGap,
                        end = 4.dp
                    ),
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = { refreshControls(); showSpeed = true }, modifier = Modifier.size(46.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("${speed}x", color = Color.White, fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
                IconButton(onClick = { refreshControls(); onFavorite() }, modifier = Modifier.size(46.dp)) {
                    Icon(HtmlStarIcon, appText("收藏", english), tint = if (favorite) Color(0xFFFFD60A) else Color.White)
                }
                Box {
                    IconButton(onClick = { refreshControls(); playerMenuOpen = true }, modifier = Modifier.size(46.dp)) {
                        Icon(Icons.Outlined.MoreVert, appText("菜单", english), tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = playerMenuOpen,
                        onDismissRequest = { playerMenuOpen = false },
                        containerColor = Color.Black.copy(alpha = .88f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text(appText("分享", english), color = Color.White) },
                            leadingIcon = { Icon(Icons.Outlined.Share, null, tint = Color.White) },
                            colors = MenuDefaults.itemColors(
                                textColor = Color.White,
                                leadingIconColor = Color.White
                            ),
                            onClick = { playerMenuOpen = false; onShare() }
                        )
                        DropdownMenuItem(
                            text = { Text(appText("设置", english), color = Color.White) },
                            leadingIcon = { Icon(Icons.Outlined.SettingsIcon, null, tint = Color.White) },
                            colors = MenuDefaults.itemColors(
                                textColor = Color.White,
                                leadingIconColor = Color.White
                            ),
                            onClick = { playerMenuOpen = false; onSettings() }
                        )
                    }
                }
            }
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = 8.dp).zIndex(3f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = { refreshControls(); if (!onEnterPictureInPicture()) onMiniModeChange(true) }) {
                    Icon(HtmlPipIcon, appText("画中画", english), tint = Color.White)
                }
                IconButton(onClick = ::startBackground) {
                    Icon(HtmlBackgroundIcon, appText("后台播放", english), tint = Color.White, modifier = Modifier.size(25.dp))
                }
            }
            IconButton(
                onClick = { controlsLocked = true; controlsVisible = false },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).zIndex(3f)
            ) { Icon(HtmlLockIcon, appText("锁定控件", english), tint = Color.White, modifier = Modifier.size(25.dp)) }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(3f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.84f))))
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = if (windowIsLandscape) 26.dp else 32.dp,
                        bottom = if (windowIsLandscape) 12.dp else 18.dp
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(timeText(position), color = Color.White, fontSize = 12.sp, modifier = Modifier.size(width = 48.dp, height = 24.dp))
                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                        onValueChange = { seekToVideoFrame(player, it.toLong()); refreshControls() },
                        valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                        modifier = Modifier.weight(1f).height(24.dp).graphicsLayer { scaleY = .65f },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(.3f))
                    )
                    Text(timeText(duration), color = Color.White, fontSize = 12.sp, modifier = Modifier.size(width = 48.dp, height = 24.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val modeIcon = when (mode) {
                        1 -> HtmlRepeatIcon
                        2 -> HtmlShuffleIcon
                        3 -> HtmlPlayIcon
                        else -> HtmlSequenceIcon
                    }
                    val modeLabel = when (mode) {
                        1 -> "循环播放"
                        2 -> "随机播放"
                        3 -> "播放完停止"
                        else -> "顺序播放"
                    }
                    buildList<Triple<ImageVector, String, () -> Unit>> {
                        add(Triple(modeIcon, modeLabel, ::cycleMode))
                        add(Triple(HtmlPreviousIcon, "上一个视频", { adjacent(false) }))
                        if (longSkipEnabled) add(Triple(HtmlLongRewindIcon, "长快退", {
                            seekToVideoFrame(player, player.currentPosition - longSkip)
                            refreshControls()
                        }))
                        add(Triple(HtmlRewindIcon, "快退", {
                            seekToVideoFrame(player, player.currentPosition - normalSkip)
                            refreshControls()
                        }))
                        add(Triple(if (playing) HtmlPauseIcon else HtmlPlayIcon, "播放", ::togglePlayback))
                        add(Triple(HtmlForwardIcon, "快进", {
                            seekToVideoFrame(player, player.currentPosition + normalSkip)
                            refreshControls()
                        }))
                        if (longSkipEnabled) add(Triple(HtmlLongForwardIcon, "长快进", {
                            seekToVideoFrame(player, player.currentPosition + longSkip)
                            refreshControls()
                        }))
                        add(Triple(HtmlNextIcon, "下一个视频", { adjacent(true) }))
                        add(Triple(htmlOrientationIcon(displayedOrientationMode), "横竖屏", {
                            val nextMode = (orientationMode + 1) % 3
                            hostActivity?.requestedOrientation = when (nextMode) {
                                1 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                            }
                            orientationMode = nextMode
                            refreshControls()
                        }))
                    }.forEach { (icon, label, action) ->
                        IconButton(onClick = action, modifier = Modifier.weight(1f).height(46.dp)) {
                            Icon(
                                icon,
                                appText(label, english),
                                tint = Color.White,
                                modifier = Modifier.size(if (label == "播放") if (longSkipEnabled) 34.dp else 42.dp else 25.dp)
                            )
                        }
                    }
                }
            }
        } else if (!pictureInPictureMode && controlsLocked) {
            IconButton(
                onClick = { controlsLocked = false; refreshControls() },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).zIndex(10f)
            ) { Icon(HtmlLockIcon, appText("解锁控件", english), tint = Color.White, modifier = Modifier.size(25.dp)) }
        }
        if (showSpeed) {
            val speedOptions = remember { listOf("0.25x", "0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x", "4x") }
            VaultWheelChoiceSheet(
                title = appText("播放速度", english),
                options = speedOptions,
                selected = if (speed == 1f) "1x" else "${speed}x",
                onDismiss = { showSpeed = false },
                onApply = { selected ->
                    speed = selected.removeSuffix("x").toFloatOrNull() ?: 1f
                    player.setPlaybackSpeed(speed)
                    showSpeed = false
                    refreshControls()
                }
            )
        }
        }
    }
}

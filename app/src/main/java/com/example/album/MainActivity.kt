package com.example.album

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import com.example.album.ui.AlbumApp
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.PrototypeViewport
import com.example.album.ui.theme.AlbumTheme
import com.example.album.ui.theme.ThemeAccent
import com.example.album.playback.MediaPlaybackService
import com.example.album.playback.PlaybackResumeRequest

class MainActivity : ComponentActivity() {
    private var playbackResumeRequest by mutableStateOf<PlaybackResumeRequest?>(null)
    private var externalMediaUri by mutableStateOf<Uri?>(null)
    private var externalWallpaperUri by mutableStateOf<Uri?>(null)
    private var pictureInPictureMode by mutableStateOf(false)
    @Suppress("DEPRECATION")
    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) = com.example.album.data.ThumbnailRepository.trimMemory(level)
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        override fun onLowMemory() = com.example.album.data.ThumbnailRepository.trimMemory(TRIM_MEMORY_RUNNING_CRITICAL)
    }

    private fun enterVideoPictureInPicture(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isInPictureInPictureMode) return false
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
        return enterPictureInPictureMode(params)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pictureInPictureMode = isInPictureInPictureMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep every non-video surface portrait. The video player temporarily
        // overrides this policy with the sensor when adaptive orientation is
        // selected, then restores portrait when it is closed.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        application.registerComponentCallbacks(memoryCallbacks)
        playbackResumeRequest = intent.toPlaybackResumeRequest()
        externalMediaUri = intent.toExternalMediaUri()
        externalWallpaperUri = intent.toExternalWallpaperUri()
        enableEdgeToEdge()
        setContent {
            val preferences = remember { getSharedPreferences("album_settings", MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(preferences.getString("theme_mode", "自动") ?: "自动") }
            var themeAccent by remember {
                mutableStateOf(ThemeAccent.fromStored(preferences.getString("theme_color", null)))
            }
            var appLanguage by remember { mutableStateOf(preferences.getString("language", "简体中文") ?: "简体中文") }
            val systemDark = isSystemInDarkTheme()
            CompositionLocalProvider(LocalAppEnglish provides (appLanguage == "English")) {
                AlbumTheme(
                    darkTheme = when (themeMode) {
                        "深色" -> true
                        "浅色" -> false
                        else -> systemDark
                    },
                    accent = themeAccent.color
                ) {
                    // Keep the prototype viewport stable even if the emulator
                    // reports landscape. The HTML player rotates its own
                    // internal canvas; the whole app must not stretch to the
                    // system's landscape bounds.
                    // Use the actual window bounds. On a normal phone the
                    // bounds remain the same 412dp-ish design size; when the
                    // video is shown in a landscape window this prevents the
                    // portrait prototype canvas from collapsing into a strip.
                    PrototypeViewport(useDeviceViewport = true) {
                        AlbumApp(
                            onThemeModeChange = { selected ->
                                themeMode = selected
                                preferences.edit().putString("theme_mode", selected).apply()
                            },
                            onThemeColorChange = { storedValue ->
                                themeAccent = ThemeAccent.fromStored(storedValue)
                                preferences.edit().putString("theme_color", themeAccent.storedValue).apply()
                            },
                            appLanguage = appLanguage,
                            onAppLanguageChange = { selected ->
                                appLanguage = selected
                                preferences.edit().putString("language", selected).apply()
                            },
                            playbackResumeRequest = playbackResumeRequest,
                            externalMediaUri = externalMediaUri,
                            externalWallpaperUri = externalWallpaperUri,
                            onPlaybackResumeConsumed = { requestId ->
                                if (playbackResumeRequest?.requestId == requestId) {
                                    playbackResumeRequest = null
                                }
                            },
                            pictureInPictureMode = pictureInPictureMode,
                            onEnterPictureInPicture = ::enterVideoPictureInPicture
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        application.unregisterComponentCallbacks(memoryCallbacks)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        playbackResumeRequest = intent.toPlaybackResumeRequest()
        externalMediaUri = intent.toExternalMediaUri()
        externalWallpaperUri = intent.toExternalWallpaperUri()
    }

    private fun Intent.toExternalMediaUri(): Uri? {
        if (action != Intent.ACTION_VIEW) return null
        val uri = data ?: return null
        val type = type.orEmpty()
        // Some file managers omit the MIME type or send */*. AlbumApp can
        // resolve those from the provider or filename.
        return uri.takeIf { type.isBlank() || type == "*/*" || type.startsWith("image/") || type.startsWith("video/") }
    }

    private fun Intent.toExternalWallpaperUri(): Uri? {
        if (action != Intent.ACTION_ATTACH_DATA) return null
        val uri = data ?: return null
        val type = type.orEmpty()
        return uri.takeIf {
            type.isBlank() || type == "*/*" || type.startsWith("image/") || type.startsWith("video/")
        }
    }

    private fun Intent.toPlaybackResumeRequest(): PlaybackResumeRequest? {
        if (action != MediaPlaybackService.ACTION_RESUME_PLAYER) return null
        val uri = getStringExtra(MediaPlaybackService.EXTRA_URI) ?: return null
        return PlaybackResumeRequest(
            uri = uri,
            positionMs = getLongExtra(MediaPlaybackService.EXTRA_POSITION, 0L),
            playWhenReady = getBooleanExtra(MediaPlaybackService.EXTRA_PLAY_WHEN_READY, true),
            requestId = getLongExtra(MediaPlaybackService.EXTRA_REQUEST_ID, System.nanoTime())
        )
    }

}

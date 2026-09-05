package com.example.album

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.WebSettings
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.album.ui.theme.AlbumTheme
import com.example.album.ui.theme.ThemeAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PixivWebActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var checkingWebSession = false
    private var pageLoadFailed by mutableStateOf(false)
    private var rendererRecoveryAttempted = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !webViewDataDirectoryConfigured) {
            // Keep login rendering and cookies away from scan fallback WebViews.
            // The versioned suffix also avoids reusing a previously corrupted profile.
            WebView.setDataDirectorySuffix("pixiv_login_v2")
            webViewDataDirectoryConfigured = true
        }
        super.onCreate(savedInstanceState)
        rendererRecoveryAttempted = intent.getBooleanExtra(EXTRA_RENDERER_RECOVERED, false)
        webView = WebView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)
            settings.allowContentAccess = true
            settings.allowFileAccess = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = browserCompatibleUserAgent(settings.userAgentString)
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) view.requestFocusFromTouch()
                if (event.action == MotionEvent.ACTION_UP) view.performClick()
                false
            }
            val currentWebView = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(currentWebView, true)
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (BuildConfig.DEBUG) {
                        android.util.Log.w("PixivWeb", "${message.message()} @ ${message.sourceId()}:${message.lineNumber()}")
                    }
                    return true
                }
                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message
                ): Boolean {
                    // Pixiv occasionally opens OAuth or verification in a new
                    // target. Reuse this WebView so cookies stay in-session.
                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                    transport.webView = view
                    resultMsg.sendToTarget()
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    pageLoadFailed = false
                }

                override fun onPageCommitVisible(view: WebView, url: String) {
                    pageLoadFailed = false
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme == "https" && isAllowedPixivWebHost(uri.host)) return false
                    // Never follow a cleartext downgrade, even outside the app.
                    if (uri.scheme == "http") return true
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    return true
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    if (BuildConfig.DEBUG) android.util.Log.e(
                            "PixivWeb",
                            "load error main=${request.isForMainFrame} code=${error.errorCode} description=${error.description} url=${request.url}"
                        )
                    if (request.isForMainFrame) pageLoadFailed = true
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: android.webkit.WebResourceResponse
                ) {
                    if (BuildConfig.DEBUG) android.util.Log.e(
                            "PixivWeb",
                            "http error main=${request.isForMainFrame} status=${errorResponse.statusCode} reason=${errorResponse.reasonPhrase} url=${request.url}"
                        )
                    if (request.isForMainFrame) pageLoadFailed = true
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: android.webkit.RenderProcessGoneDetail
                ): Boolean {
                    val crashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detail.didCrash() else false
                    if (BuildConfig.DEBUG) android.util.Log.e("PixivWeb", "renderer gone crashed=$crashed")
                    if (!rendererRecoveryAttempted) {
                        rendererRecoveryAttempted = true
                        intent.putExtra(EXTRA_RENDERER_RECOVERED, true)
                        view.destroy()
                        recreate()
                    } else {
                        pageLoadFailed = true
                    }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    CookieManager.getInstance().flush()
                }
            }
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val settingsPreferences = getSharedPreferences("album_settings", MODE_PRIVATE)
        val language = settingsPreferences.getString("language", "简体中文") ?: "简体中文"
        val themeMode = settingsPreferences.getString("theme_mode", "自动") ?: "自动"
        val themeAccent = ThemeAccent.fromStored(settingsPreferences.getString("theme_color", null))

        setContent {
            val systemDark = isSystemInDarkTheme()
            AlbumTheme(
                darkTheme = when (themeMode) {
                    "深色" -> true
                    "浅色" -> false
                    else -> systemDark
                },
                accent = themeAccent.color
            ) {
                PixivBrowser(
                    webView = webView,
                    english = language == "English",
                    pageLoadFailed = pageLoadFailed,
                    onRetry = ::retryLoginPage,
                    onClose = ::finish,
                    onDone = ::complete
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
        val requested = intent.getStringExtra(EXTRA_URL)?.let(Uri::parse)
        val url = requested?.takeIf {
            it.scheme == "https" && isAllowedPixivWebHost(it.host)
        }?.toString() ?: "https://www.pixiv.net/"
        webView.loadUrl(url)
    }

    private fun retryLoginPage() {
        pageLoadFailed = false
        webView.stopLoading()
        webView.clearCache(true)
        webView.loadUrl(LOGIN_URL)
    }

    private fun complete() {
        if (checkingWebSession) return
        // Verify from the login process itself. Waiting for onPageFinished is
        // unreliable here: Pixiv may stay on the account host, redirect as a
        // SPA, or leave a blank WebView while the authenticated cookies are
        // already available.
        checkingWebSession = true
        CookieManager.getInstance().flush()
        lifecycleScope.launch {
            val authenticated = repeatAuthenticatedCheck()
            if (isFinishing || isDestroyed || !checkingWebSession) return@launch
            checkingWebSession = false
            finishWithAuthentication(authenticated)
        }
    }

    private suspend fun repeatAuthenticatedCheck(): Boolean {
        repeat(5) { attempt ->
            val cookies = CookieManager.getInstance().getCookie("https://www.pixiv.net/")
                .orEmpty()
                .plus(";")
                .plus(CookieManager.getInstance().getCookie("https://accounts.pixiv.net/").orEmpty())
                .trim(';')
            if (cookies.isNotBlank() && requestAuthenticatedSession(cookies)) return true
            if (attempt < 4) delay(500L)
        }
        return false
    }

    private suspend fun requestAuthenticatedSession(cookies: String): Boolean {
        val userAgent = webView.settings.userAgentString
        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL("https://www.pixiv.net/ajax/user/self?lang=zh").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Referer", "https://www.pixiv.net/")
                    setRequestProperty("Origin", "https://www.pixiv.net")
                    setRequestProperty("X-Requested-With", "XMLHttpRequest")
                    setRequestProperty("Cookie", cookies)
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching false
                    pixivSelfResponseAuthenticated(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            }
                .getOrDefault(false)
        }
    }

    private fun finishWithAuthentication(authenticated: Boolean) {
        lifecycleScope.launch {
            CookieManager.getInstance().flush()
            setResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra(EXTRA_AUTHENTICATED, authenticated)
                    .putExtra(EXTRA_PIXIV_COOKIES, CookieManager.getInstance().getCookie("https://www.pixiv.net/"))
                    .putExtra(EXTRA_ACCOUNT_COOKIES, CookieManager.getInstance().getCookie("https://accounts.pixiv.net/"))
            )
            finish()
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "pixiv_url"
        const val EXTRA_AUTHENTICATED = "pixiv_authenticated"
        const val EXTRA_PIXIV_COOKIES = "pixiv_cookies"
        const val EXTRA_ACCOUNT_COOKIES = "pixiv_account_cookies"
        private const val EXTRA_RENDERER_RECOVERED = "pixiv_renderer_recovered"
        const val LOGIN_URL = "https://accounts.pixiv.net/login?lang=zh&source=pc&view_type=page"
        @Volatile private var webViewDataDirectoryConfigured = false
    }
}

internal fun browserCompatibleUserAgent(defaultUserAgent: String): String = defaultUserAgent
    .replace("; wv", "")
    .replace(Regex("\\s*Version/4\\.0\\s*"), " ")
    .replace(Regex("\\s{2,}"), " ")
    .trim()

internal fun isAllowedPixivWebHost(host: String?): Boolean {
    val normalized = host?.trim()?.lowercase() ?: return false
    return normalized == "pixiv.net" ||
        normalized == "www.pixiv.net" ||
        normalized == "accounts.pixiv.net" ||
        normalized == "oauth.secure.pixiv.net"
}

internal fun pixivSelfResponseAuthenticated(json: String): Boolean = runCatching {
    val root = JSONObject(json)
    val error = root.opt("error")
    if (error == true || error?.toString()?.equals("true", ignoreCase = true) == true) {
        return@runCatching false
    }
    fun value(objectName: String, key: String): String =
        root.optJSONObject(objectName)?.opt(key)?.toString().orEmpty()
    listOfNotNull(
        value("userData", "id"),
        value("body", "userId"),
        value("body", "id"),
        value("data", "userId"),
        value("data", "id")
    ).any { it.isNotBlank() }
}.getOrDefault(false)

@SuppressLint("SetJavaScriptEnabled")
@androidx.compose.runtime.Composable
private fun PixivBrowser(
    webView: WebView,
    english: Boolean,
    pageLoadFailed: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit
) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = if (english) "Close" else "关闭"
                    )
                }
                Text(
                    text = if (english) "Pixiv account" else "Pixiv 账号",
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp
                )
                TextButton(onClick = onDone, modifier = Modifier.padding(end = 4.dp).height(48.dp)) {
                    Text(if (english) "Done" else "完成")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            if (pageLoadFailed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (english) "Pixiv page failed to load" else "Pixiv 页面加载失败",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRetry) {
                        Text(if (english) "Retry" else "重试")
                    }
                }
            }
        }
    }
}

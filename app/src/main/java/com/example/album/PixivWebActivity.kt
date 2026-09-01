package com.example.album

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.launch

class PixivWebActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var checkingWebSession = false
    private var pageLoadFailed by mutableStateOf(false)
    private val recoveryHandler = Handler(Looper.getMainLooper())
    private var committedUrl: String? = null
    private var fallbackAttempted = false
    private var rendererRecoveryAttempted = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !webViewDataDirectoryConfigured) {
            WebView.setDataDirectorySuffix("pixiv_login")
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
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) view.requestFocusFromTouch()
                false
            }
            val currentWebView = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(currentWebView, true)
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    android.util.Log.w("PixivWeb", "${message.message()} @ ${message.sourceId()}:${message.lineNumber()}")
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
                    committedUrl = null
                    scheduleBlankPageRecovery(url)
                }

                override fun onPageCommitVisible(view: WebView, url: String) {
                    committedUrl = url
                    pageLoadFailed = false
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme == "http" || uri.scheme == "https") return false
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    return true
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    android.util.Log.e(
                        "PixivWeb",
                        "load error main=${request.isForMainFrame} code=${error.errorCode} description=${error.description} url=${request.url}"
                    )
                    if (request.isForMainFrame) recoverFailedMainFrame(request.url.toString())
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: android.webkit.WebResourceResponse
                ) {
                    android.util.Log.e(
                        "PixivWeb",
                        "http error main=${request.isForMainFrame} status=${errorResponse.statusCode} reason=${errorResponse.reasonPhrase} url=${request.url}"
                    )
                    if (request.isForMainFrame) recoverFailedMainFrame(request.url.toString())
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: android.webkit.RenderProcessGoneDetail
                ): Boolean {
                    android.util.Log.e("PixivWeb", "renderer gone crashed=${detail.didCrash()}")
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
                    val host = Uri.parse(url).host.orEmpty().lowercase()
                    if (checkingWebSession && (host == "pixiv.net" || host.endsWith(".pixiv.net")) && host != "accounts.pixiv.net") {
                        checkWebSession(view, 3)
                    }
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
            it.scheme == "https" && it.host.orEmpty().let { host -> host == "pixiv.net" || host.endsWith(".pixiv.net") }
        }?.toString() ?: "https://www.pixiv.net/"
        webView.loadUrl(url)
    }

    private fun scheduleBlankPageRecovery(url: String) {
        recoveryHandler.postDelayed({
            if (isFinishing || isDestroyed || committedUrl == url) return@postDelayed
            android.util.Log.w("PixivWeb", "no visible page commit for $url")
            recoverFailedMainFrame(url)
        }, PAGE_VISIBLE_TIMEOUT_MS)
    }

    private fun recoverFailedMainFrame(url: String) {
        if (isFinishing || isDestroyed) return
        if (!fallbackAttempted && isLoginUrl(url)) {
            fallbackAttempted = true
            pageLoadFailed = false
            webView.stopLoading()
            webView.loadUrl(ALTERNATE_LOGIN_URL)
        } else {
            pageLoadFailed = true
        }
    }

    private fun isLoginUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.host.equals("accounts.pixiv.net", ignoreCase = true) ||
            uri.path.orEmpty().contains("login", ignoreCase = true)
    }

    private fun retryLoginPage() {
        fallbackAttempted = false
        committedUrl = null
        pageLoadFailed = false
        webView.stopLoading()
        webView.clearCache(true)
        webView.loadUrl(LOGIN_URL)
    }

    private fun complete() {
        if (checkingWebSession) return
        // Confirm inside the same WebView that performed the login. The
        // WebView cookie store and a normal Android HTTP client are not always
        // observable at the same moment on real devices.
        checkingWebSession = true
        CookieManager.getInstance().flush()
        val cookies = listOf(
            CookieManager.getInstance().getCookie("https://www.pixiv.net/"),
            CookieManager.getInstance().getCookie("https://accounts.pixiv.net/")
        ).filterNot { it.isNullOrBlank() }.joinToString(";")
        if (cookies.split(';').any { cookie ->
                cookie.substringBefore('=').trim().equals("PHPSESSID", ignoreCase = true) &&
                    cookie.substringAfter('=', "").isNotBlank()
            }) {
            checkingWebSession = false
            finishWithAuthentication(true)
            return
        }
        webView.loadUrl("https://www.pixiv.net/")
        webView.postDelayed({
            if (checkingWebSession) {
                checkingWebSession = false
                finishWithAuthentication(false)
            }
        }, 10_000L)
    }

    private fun checkWebSession(view: WebView, retries: Int) {
        view.postDelayed({
            if (!checkingWebSession || isFinishing) return@postDelayed
            view.evaluateJavascript(
                """(async()=>{try{const r=await fetch('/ajax/user/self?lang=zh',{credentials:'include',cache:'no-store'});const j=await r.json();return !!(j&&!j.error&&String((j.userData&&j.userData.id)||(j.body&&j.body.userId)||'').length>0)}catch(e){return false}})()"""
            ) { raw ->
                val authenticated = raw.trim().trim('"') == "true"
                if (authenticated) {
                    checkingWebSession = false
                    finishWithAuthentication(true)
                } else if (retries > 0) {
                    checkWebSession(view, retries - 1)
                } else {
                    checkingWebSession = false
                    finishWithAuthentication(false)
                }
            }
        }, if (retries == 3) 500L else 900L)
    }

    private fun finishWithAuthentication(authenticated: Boolean) {
        lifecycleScope.launch {
            CookieManager.getInstance().flush()
            val cookies = listOf(
                CookieManager.getInstance().getCookie("https://www.pixiv.net/"),
                CookieManager.getInstance().getCookie("https://accounts.pixiv.net/")
            ).filterNot { it.isNullOrBlank() }.joinToString(";")
            val cookieAuthenticated = cookies.split(';').any { cookie ->
                cookie.substringBefore('=').trim().equals("PHPSESSID", ignoreCase = true) &&
                    cookie.substringAfter('=', "").isNotBlank()
            }
            val finalAuthenticated = authenticated || cookieAuthenticated
            setResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra(EXTRA_AUTHENTICATED, finalAuthenticated)
                    .putExtra(EXTRA_PIXIV_COOKIES, CookieManager.getInstance().getCookie("https://www.pixiv.net/"))
                    .putExtra(EXTRA_ACCOUNT_COOKIES, CookieManager.getInstance().getCookie("https://accounts.pixiv.net/"))
            )
            finish()
        }
    }

    override fun onDestroy() {
        recoveryHandler.removeCallbacksAndMessages(null)
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
        const val ALTERNATE_LOGIN_URL = "https://www.pixiv.net/login.php?return_to=%2F"
        private const val PAGE_VISIBLE_TIMEOUT_MS = 8_000L
        @Volatile private var webViewDataDirectoryConfigured = false
    }
}

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

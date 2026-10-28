package com.example.album

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.album.data.openMediaInputStream
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.theme.AlbumTheme
import com.example.album.ui.theme.ThemeAccent
import java.io.BufferedInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Hosts third-party search pages inside a dedicated process. When Album opens
 * this activity from a selected image, the page's file input is filled with a
 * private cache copy of that image and the site's own submit control is used.
 */
class ImageSearchActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var source: ImageSearchSource
    private var english = false
    private var originalImageUri: Uri? = null
    private var imageName: String? = null
    private var preparedUploadUri: Uri? = null
    private var preparedUploadFile: File? = null
    private var preparedUploadMime = "application/octet-stream"
    private var pendingFileChooser: ValueCallback<Array<Uri>>? = null
    private var imageInjectionJob: Job? = null
    private var autoSelectionConsumed = false
    private var autoFillInFlight = false
    private var autoFillAttempts = 0
    private var autoSubmitInFlight = false
    private var autoSubmitAttempts = 0
    private var searchSubmitted = false
    private var pageLoadFailed by mutableStateOf(false)
    private var statusText by mutableStateOf<String?>(null)
    private var statusBusy by mutableStateOf(false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !webViewDataDirectoryConfigured) {
            WebView.setDataDirectorySuffix("image_search_v1")
            webViewDataDirectoryConfigured = true
        }
        super.onCreate(savedInstanceState)

        source = ImageSearchSource.fromKey(intent.getStringExtra(EXTRA_SOURCE))
        originalImageUri = intent.getParcelableExtraCompat(EXTRA_IMAGE_URI)
        imageName = intent.getStringExtra(EXTRA_IMAGE_NAME)
        val preferences = getSharedPreferences("album_settings", MODE_PRIVATE)
        english = preferences.getString("language", "简体中文") == "English"
        val themeMode = preferences.getString("theme_mode", "自动") ?: "自动"
        val themeAccent = ThemeAccent.fromStored(preferences.getString("theme_color", null))

        webView = WebView(this).apply {
            val currentWebView = this
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.allowContentAccess = true
            settings.allowFileAccess = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = browserCompatibleUserAgent(settings.userAgentString)
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(currentWebView, true)
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    view: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    val callback = filePathCallback ?: return false
                    pendingFileChooser?.onReceiveValue(null)
                    pendingFileChooser = null

                    val automatic = preparedUploadUri
                    if (automatic != null && !autoSelectionConsumed) {
                        autoSelectionConsumed = true
                        callback.onReceiveValue(arrayOf(automatic))
                        statusText = appText("正在将所选图片填入网页…", english)
                        statusBusy = true
                        view?.postDelayed({ scheduleAutoSubmit() }, 450L)
                        return true
                    }

                    pendingFileChooser = callback
                    val chooser = fileChooserParams?.createIntent()
                    if (chooser == null) {
                        pendingFileChooser = null
                        callback.onReceiveValue(null)
                        return false
                    }
                    return runCatching {
                        @Suppress("DEPRECATION")
                        startActivityForResult(chooser, REQUEST_MANUAL_FILE_CHOOSER)
                        true
                    }.getOrElse {
                        pendingFileChooser = null
                        callback.onReceiveValue(null)
                        false
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    pageLoadFailed = false
                    if (searchSubmitted) {
                        statusText = appText("正在载入搜索结果…", english)
                        statusBusy = true
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    CookieManager.getInstance().flush()
                    if (searchSubmitted && !autoSubmitInFlight) {
                        statusText = null
                        statusBusy = false
                    } else {
                        scheduleAutoFill()
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme == "https" && source.allowsHost(uri.host)) return false
                    if (uri.scheme == "http") return true
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    return true
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        pageLoadFailed = true
                        statusBusy = false
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: android.webkit.WebResourceResponse
                ) {
                    if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                        pageLoadFailed = true
                        statusBusy = false
                    }
                }
            }
        }
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
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalAppEnglish provides english
                ) {
                    ImageSearchBrowser(
                        webView = webView,
                        source = source,
                        imageName = imageName,
                        statusText = statusText,
                        statusBusy = statusBusy,
                        pageLoadFailed = pageLoadFailed,
                        onRetry = {
                            pageLoadFailed = false
                            webView.reload()
                        },
                        onClose = ::finish
                    )
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else finish()
            }
        })

        if (originalImageUri == null) {
            statusText = appText("请选择一张图片开始搜索", english)
            statusBusy = false
        } else {
            prepareSelectedImage(originalImageUri!!)
        }
        webView.loadUrl(source.startUrl)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MANUAL_FILE_CHOOSER) return
        val callback = pendingFileChooser
        pendingFileChooser = null
        if (callback == null) return
        val selected = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        if (selected.isNullOrEmpty()) {
            callback.onReceiveValue(null)
            return
        }
        callback.onReceiveValue(selected)
        scheduleAutoSubmit()
    }

    private fun prepareSelectedImage(uri: Uri) {
        statusText = appText("正在准备所选图片…", english)
        statusBusy = true
        lifecycleScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                runCatching { copyForUpload(uri) }
            }
            if (isFinishing || isDestroyed) return@launch
            prepared.onSuccess { upload ->
                preparedUploadUri = upload.uri
                preparedUploadMime = upload.mime
                statusText = appText("图片将上传至所选网站，仅在本次搜索中使用", english)
                statusBusy = false
                scheduleAutoFill(150L)
            }.onFailure {
                statusText = appText("无法准备所选图片，请在网页中手动选择", english)
                statusBusy = false
                Toast.makeText(
                    this@ImageSearchActivity,
                    appText("无法准备所选图片，请在网页中手动选择", english),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private data class PreparedUpload(val uri: Uri, val mime: String)

    private fun copyForUpload(uri: Uri): PreparedUpload {
        val mime = contentResolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mime)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?: imageName
                ?.substringAfterLast('.', "")
                ?.lowercase()
                ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
            ?: "jpg"
        val directory = File(cacheDir, "image_search").apply { mkdirs() }
        directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith("album-search-") }
            ?.forEach(File::delete)
        val target = File(directory, "album-search-${System.nanoTime()}.$extension")
        val input = openMediaInputStream(this, uri) ?: error("Image stream unavailable")
        input.use { sourceStream ->
            target.outputStream().use(sourceStream::copyTo)
        }
        preparedUploadFile = target
        return PreparedUpload(
            FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                target
            ),
            mime
        )
    }

    private fun scheduleAutoFill(delayMs: Long = 500L) {
        if (preparedUploadUri == null || autoSelectionConsumed || autoFillInFlight ||
            searchSubmitted || !::webView.isInitialized
        ) {
            return
        }
        webView.postDelayed({
            if (preparedUploadUri == null || autoSelectionConsumed || isFinishing || isDestroyed) {
                return@postDelayed
            }
            autoFillInFlight = true
            webView.evaluateJavascript(INSPECT_FILE_INPUT_SCRIPT) { result ->
                autoFillInFlight = false
                when (jsStringResult(result)) {
                    "supported" -> beginInjectedUpload()
                    "picker" -> openFilePickerWithScript()
                    else -> {
                        autoFillAttempts += 1
                        if (autoFillAttempts < 60) {
                            scheduleAutoFill(500L)
                        } else {
                            statusText = appText("未找到上传控件，请手动选择图片", english)
                            statusBusy = false
                        }
                    }
                }
            }
        }, delayMs)
    }

    private fun openFilePickerWithScript() {
        if (autoFillInFlight || autoSelectionConsumed) return
        autoFillInFlight = true
        webView.evaluateJavascript(AUTO_OPEN_FILE_PICKER_SCRIPT) { result ->
            autoFillInFlight = false
            if (result?.trim() == "true") {
                statusText = appText("正在将所选图片填入网页…", english)
                statusBusy = true
            } else {
                statusText = appText("未找到上传控件，请手动选择图片", english)
                statusBusy = false
            }
        }
    }

    private fun beginInjectedUpload() {
        val file = preparedUploadFile ?: return
        if (autoSelectionConsumed || imageInjectionJob?.isActive == true) return
        statusText = appText("正在将所选图片填入网页…", english)
        statusBusy = true
        imageInjectionJob = lifecycleScope.launch {
            val initialized = evaluateBoolean(
                INJECT_FILE_START_SCRIPT
                    .replace("__ALBUM_FILE_NAME__", JSONObject.quote(file.name))
                    .replace("__ALBUM_FILE_MIME__", JSONObject.quote(preparedUploadMime))
            )
            if (!initialized) {
                scheduleAutoFill(500L)
                return@launch
            }

            val buffer = ByteArray(96 * 1024)
            BufferedInputStream(file.inputStream()).use { input ->
                while (true) {
                    val count = withContext(Dispatchers.IO) { input.read(buffer) }
                    if (count <= 0) break
                    val chunk = buffer.copyOf(count)
                    val base64 = withContext(Dispatchers.Default) {
                        Base64.encodeToString(chunk, Base64.NO_WRAP)
                    }
                    val appended = evaluateBoolean(
                        "window.__albumImageSearchChunks.push(${JSONObject.quote(base64)}); true;"
                    )
                    if (!appended) {
                        statusText = appText("无法准备所选图片，请在网页中手动选择", english)
                        statusBusy = false
                        return@launch
                    }
                    yield()
                }
            }

            val filled = evaluateBoolean(INJECT_FILE_AND_FILL_SCRIPT)
            if (!filled) {
                statusText = appText("无法准备所选图片，请在网页中手动选择", english)
                statusBusy = false
                return@launch
            }
            autoSelectionConsumed = true
            scheduleAutoSubmit(250L)
        }
    }

    private suspend fun evaluateBoolean(script: String): Boolean {
        if (isFinishing || isDestroyed) return false
        return suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { result ->
                if (continuation.isActive) continuation.resume(result?.trim() == "true")
            }
        }
    }

    private fun jsStringResult(value: String?): String =
        runCatching { JSONTokener(value.orEmpty()).nextValue() as? String }
            .getOrNull()
            .orEmpty()

    private fun scheduleAutoSubmit(delayMs: Long = 500L) {
        if (searchSubmitted || autoSubmitInFlight || !::webView.isInitialized) return
        webView.postDelayed({
            if (searchSubmitted || isFinishing || isDestroyed) return@postDelayed
            autoSubmitInFlight = true
            webView.evaluateJavascript(SUBMIT_SEARCH_SCRIPT) { result ->
                autoSubmitInFlight = false
                if (result?.trim() == "true") {
                    searchSubmitted = true
                    statusText = appText("正在提交搜索…", english)
                    statusBusy = true
                } else {
                    autoSubmitAttempts += 1
                    if (autoSubmitAttempts < 15) {
                        scheduleAutoSubmit(600L)
                    } else {
                        statusText = appText("图片已填入，请在网页中点击搜索按钮", english)
                        statusBusy = false
                    }
                }
            }
        }, delayMs)
    }

    override fun onDestroy() {
        pendingFileChooser?.onReceiveValue(null)
        pendingFileChooser = null
        imageInjectionJob?.cancel()
        imageInjectionJob = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        preparedUploadFile?.delete()
        preparedUploadFile = null
        super.onDestroy()
    }

    private fun Intent.getParcelableExtraCompat(name: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }

    companion object {
        const val EXTRA_SOURCE = "image_search_source"
        const val EXTRA_IMAGE_URI = "image_search_uri"
        const val EXTRA_IMAGE_NAME = "image_search_name"
        private const val REQUEST_MANUAL_FILE_CHOOSER = 4101
        @Volatile private var webViewDataDirectoryConfigured = false

        private const val AUTO_OPEN_FILE_PICKER_SCRIPT = """
            (function () {
              try {
                if (window.__albumImageSearchPickerOpened) return true;
                var input = document.querySelector('input[type=file]');
                if (!input) return false;
                window.__albumImageSearchPickerOpened = true;
                input.click();
                return true;
              } catch (error) {
                return false;
              }
            })();
        """

        private const val INSPECT_FILE_INPUT_SCRIPT = """
            (function () {
              try {
                var input = document.querySelector('input[type=file]');
                if (!input) return 'missing';
                if (typeof File === 'function' && typeof DataTransfer === 'function') {
                  return 'supported';
                }
                return 'picker';
              } catch (error) {
                return 'missing';
              }
            })();
        """

        private const val INJECT_FILE_START_SCRIPT = """
            (function () {
              try {
                var input = document.querySelector('input[type=file]');
                if (!input) return false;
                window.__albumImageSearchChunks = [];
                window.__albumImageSearchFileName = __ALBUM_FILE_NAME__;
                window.__albumImageSearchFileMime = __ALBUM_FILE_MIME__;
                return true;
              } catch (error) {
                return false;
              }
            })();
        """

        private const val INJECT_FILE_AND_FILL_SCRIPT = """
            (function () {
              try {
                var input = document.querySelector('input[type=file]');
                var chunks = window.__albumImageSearchChunks || [];
                if (!input || chunks.length === 0) return false;
                var parts = [];
                for (var i = 0; i < chunks.length; i++) {
                  var binary = atob(chunks[i]);
                  var bytes = new Uint8Array(binary.length);
                  for (var j = 0; j < binary.length; j++) {
                    bytes[j] = binary.charCodeAt(j);
                  }
                  parts.push(bytes);
                }
                var file = new File(
                  parts,
                  window.__albumImageSearchFileName || 'image.jpg',
                  { type: window.__albumImageSearchFileMime || 'image/jpeg' }
                );
                var transfer = new DataTransfer();
                transfer.items.add(file);
                input.files = transfer.files;
                input.dispatchEvent(new Event('change', { bubbles: true }));
                window.__albumImageSearchChunks = [];
                return true;
              } catch (error) {
                window.__albumImageSearchChunks = [];
                return false;
              }
            })();
        """

        private const val SUBMIT_SEARCH_SCRIPT = """
            (function () {
              try {
                if (window.__albumImageSearchSubmitted) return true;
                var input = document.querySelector('input[type=file]');
                if (!input || !input.files || input.files.length === 0) return false;
                input.dispatchEvent(new Event('change', { bubbles: true }));
                var form = input.form || input.closest('form');
                if (!form) return false;
                var submit = form.querySelector(
                  'button[type=submit], input[type=submit], button[name=search], button[name=commit]'
                );
                window.__albumImageSearchSubmitted = true;
                if (typeof form.requestSubmit === 'function') {
                  form.requestSubmit(submit || undefined);
                } else if (submit) {
                  submit.click();
                } else {
                  form.submit();
                }
                return true;
              } catch (error) {
                window.__albumImageSearchSubmitted = false;
                return false;
              }
            })();
        """

        fun intent(
            context: Context,
            source: ImageSearchSource,
            imageUri: Uri? = null,
            imageName: String? = null
        ): Intent = Intent(context, ImageSearchActivity::class.java).apply {
            putExtra(EXTRA_SOURCE, source.key)
            imageUri?.let {
                data = it
                clipData = android.content.ClipData.newRawUri(imageName ?: "image", it)
                putExtra(EXTRA_IMAGE_URI, it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            putExtra(EXTRA_IMAGE_NAME, imageName)
        }
    }
}

@Composable
private fun ImageSearchBrowser(
    webView: WebView,
    source: ImageSearchSource,
    imageName: String?,
    statusText: String?,
    statusBusy: Boolean,
    pageLoadFailed: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val english = LocalAppEnglish.current
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
                        contentDescription = appText("关闭", english)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(source.displayName, fontSize = 17.sp)
                    if (!imageName.isNullOrBlank()) {
                        Text(
                            imageName,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            statusText?.let { text ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (statusBusy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        text,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        appText("网页加载失败", english),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRetry) {
                        Text(appText("重试", english))
                    }
                }
            }
        }
    }
}

package com.brainer.otnor.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.brainer.otnor.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

class WebViewManager(
    private val context: Context,
    val uiVersionManager: UIVersionManager,
    private val onFileChooserRequest: ((ValueCallback<Array<Uri>>) -> Unit)? = null
) {

    companion object {
        private var activeManagerRef: WeakReference<WebViewManager>? = null

        fun reloadFromExternal() {
            activeManagerRef?.get()?.reloadBackend()
        }

        fun loadUrlFromExternal(url: String) {
            activeManagerRef?.get()?.loadUrl(url)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webViewRef: WeakReference<WebView>? = null

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isUsingCachedFallback = MutableStateFlow(false)
    val isUsingCachedFallback: StateFlow<Boolean> = _isUsingCachedFallback.asStateFlow()

    private val _currentUrl = MutableStateFlow(Constants.BACKEND_URL)
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private var timeoutRunnable: Runnable? = null

    init {
        activeManagerRef = WeakReference(this)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createAndConfigureWebView(): WebView {
        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            javaScriptCanOpenWindowsAutomatically = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            userAgentString = Constants.USER_AGENT
        }

        val bridge = AndropiBridge(context) {
            reloadBackend()
        }
        webView.addJavascriptInterface(bridge, Constants.JS_BRIDGE_NAME)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                _isLoading.value = true
                if (!url.isNullOrBlank()) {
                    _currentUrl.value = url
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                cancelTimeout()
                _isLoading.value = false

                if (view != null && url != null && url.startsWith("http://127.0.0.1:8080") && !_isUsingCachedFallback.value) {
                    view.evaluateJavascript(
                        "(function() { return document.documentElement.outerHTML; })();"
                    ) { html ->
                        uiVersionManager.saveSnapshot(url, html)
                    }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val host = uri.host?.lowercase() ?: ""
                if (host == "127.0.0.1" || host == "localhost") {
                    return false
                }
                // External URLs open in default browser per spec
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                } catch (e: Exception) {
                    false
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    cancelTimeout()
                    loadCachedOrFallback(view, request.url?.toString() ?: Constants.BACKEND_URL)
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 500) {
                    cancelTimeout()
                    loadCachedOrFallback(view, request.url?.toString() ?: Constants.BACKEND_URL)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                // Auto-grant geolocation for personal app
                callback?.invoke(origin, true, false)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                // Auto-grant camera/mic WebRTC permissions for personal app
                request?.grant(request.resources)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    Log.d(
                        Constants.TAG_OTNOR,
                        "[WebView Console] ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                    )
                }
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback != null && onFileChooserRequest != null) {
                    onFileChooserRequest.invoke(filePathCallback)
                    return true
                }
                return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
            }
        }

        webViewRef = WeakReference(webView)
        loadBackendWithTimeout(webView, Constants.BACKEND_URL)
        return webView
    }

    fun loadBackendWithTimeout(webView: WebView? = webViewRef?.get(), url: String = Constants.BACKEND_URL) {
        val wv = webView ?: return
        cancelTimeout()
        _isLoading.value = true
        _isUsingCachedFallback.value = false

        val runnable = Runnable {
            if (_isLoading.value && !_isUsingCachedFallback.value) {
                Log.w(Constants.TAG_OTNOR, "Backend $url unreachable after 3 seconds — loading cached snapshot")
                wv.stopLoading()
                loadCachedOrFallback(wv, url)
            }
        }
        timeoutRunnable = runnable
        mainHandler.postDelayed(runnable, 3_000L)
        wv.loadUrl(url)
    }

    private fun loadCachedOrFallback(webView: WebView?, url: String) {
        val wv = webView ?: return
        _isUsingCachedFallback.value = true
        _isLoading.value = false
        val cached = uiVersionManager.getLatestSnapshotHtml(url)
        val html = uiVersionManager.buildOfflineFallbackHtml(cached)
        wv.loadDataWithBaseURL(
            Constants.BACKEND_URL,
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    fun reloadBackend() {
        loadBackendWithTimeout(webViewRef?.get(), Constants.BACKEND_URL)
    }

    fun loadUrl(url: String) {
        loadBackendWithTimeout(webViewRef?.get(), url)
    }

    fun canGoBack(): Boolean = webViewRef?.get()?.canGoBack() == true

    fun goBack() {
        webViewRef?.get()?.goBack()
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }
}

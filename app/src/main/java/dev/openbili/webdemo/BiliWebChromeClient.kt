package dev.openbili.webdemo

import android.graphics.Color
import android.os.Message
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class BiliWebChromeClient(
  private val onProgress: (Int) -> Unit,
  private val onTitle: (String?) -> Unit,
  private val fullscreenDelegate: FullscreenDelegate,
  private val routeNewWindow: (String) -> Unit,
) : WebChromeClient() {
  private val popupTimeouts = mutableMapOf<WebView, Runnable>()

  interface FullscreenDelegate {
    fun show(view: View, callback: CustomViewCallback)

    fun show(view: View, requestedOrientation: Int, callback: CustomViewCallback) =
      show(view, callback)

    fun hide()
  }

  override fun onProgressChanged(view: WebView, newProgress: Int) =
    onProgress(newProgress.coerceIn(0, 100))

  override fun onReceivedTitle(view: WebView, title: String?) = onTitle(title)

  override fun onShowCustomView(view: View, callback: CustomViewCallback) =
    fullscreenDelegate.show(view, callback)

  @Deprecated("Compatibility callback for WebView providers that supply an orientation hint")
  override fun onShowCustomView(
    view: View,
    requestedOrientation: Int,
    callback: CustomViewCallback,
  ) = fullscreenDelegate.show(view, requestedOrientation, callback)

  override fun onHideCustomView() = fullscreenDelegate.hide()

  override fun onCreateWindow(
    view: WebView,
    isDialog: Boolean,
    isUserGesture: Boolean,
    resultMsg: Message,
  ): Boolean {
    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
    // Script-only popups have no trustworthy user intent and are not needed for video playback.
    if (!isUserGesture) return false

    val temporary = WebView(view.context)
    WebViewConfigurator.configure(temporary, BuildConfig.DEBUG)
    with(temporary.settings) {
      javaScriptEnabled = false
      domStorageEnabled = false
      setSupportMultipleWindows(false)
      javaScriptCanOpenWindowsAutomatically = false
    }
    temporary.setBackgroundColor(Color.TRANSPARENT)
    temporary.webViewClient =
      object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
          child: WebView,
          request: WebResourceRequest,
        ): Boolean {
          routePopup(child, request.url.toString())
          return true
        }

        @Deprecated("Compatibility callback for older WebView providers")
        override fun shouldOverrideUrlLoading(child: WebView, url: String): Boolean {
          routePopup(child, url)
          return true
        }

        override fun onPageStarted(child: WebView, url: String, favicon: android.graphics.Bitmap?) {
          if (url != BLANK_URL) routePopup(child, url)
        }

        override fun onPageFinished(child: WebView, url: String) {
          if (url != BLANK_URL) routePopup(child, url)
        }
      }
    temporary.webChromeClient =
      object : WebChromeClient() {
        override fun onCloseWindow(window: WebView) = closePopup(window, postDestroy = true)
      }

    val timeout = Runnable { closePopup(temporary, postDestroy = false) }
    popupTimeouts[temporary] = timeout
    temporary.postDelayed(timeout, POPUP_TIMEOUT_MILLIS)
    transport.webView = temporary
    resultMsg.sendToTarget()
    return true
  }

  fun dispose() {
    fullscreenDelegate.hide()
    popupTimeouts.keys.toList().forEach { closePopup(it, postDestroy = false) }
  }

  private fun routePopup(webView: WebView, target: String) {
    if (!popupTimeouts.containsKey(webView)) return
    try {
      when (UrlPolicy.decide(target)) {
        UrlDecision.ALLOW,
        UrlDecision.EXTERNAL -> routeNewWindow(target)
        UrlDecision.BLOCK -> Unit
      }
    } finally {
      closePopup(webView, postDestroy = true)
    }
  }

  private fun closePopup(webView: WebView, postDestroy: Boolean) {
    val timeout = popupTimeouts.remove(webView) ?: return
    webView.removeCallbacks(timeout)
    val destroy = {
      webView.stopLoading()
      webView.webChromeClient = null
      webView.webViewClient = WebViewClient()
      webView.removeAllViews()
      webView.destroy()
    }
    if (postDestroy) webView.post(destroy) else destroy()
  }

  private companion object {
    const val BLANK_URL = "about:blank"
    const val POPUP_TIMEOUT_MILLIS = 5_000L
  }
}

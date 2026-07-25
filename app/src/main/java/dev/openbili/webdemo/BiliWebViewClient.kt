package dev.openbili.webdemo

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class BiliWebViewClient(
  private val injectionManager: InjectionManager,
  private val onState: (url: String, title: String?, canGoBack: Boolean) -> Unit,
  private val onProgress: (Int) -> Unit,
  private val onError: (PageError) -> Unit,
  private val onSubresourceError: () -> Unit,
  private val openExternal: (String) -> Unit,
  private val onRendererGone: () -> Unit,
  private val onPageCommitVisible: (String) -> Unit = {},
) : WebViewClient() {
  private var currentMainFrameUrl: String? = null
  private var failedMainFrameUrl: String? = null
  private var lastVisibleUrl: String? = null

  override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
    route(request.url.toString())

  @Deprecated("Compatibility callback for older WebView providers")
  override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = route(url)

  private fun route(url: String): Boolean =
    when (UrlPolicy.decide(url)) {
      UrlDecision.ALLOW -> false
      UrlDecision.EXTERNAL -> true.also { openExternal(url) }
      UrlDecision.BLOCK -> true
    }

  override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
    currentMainFrameUrl = url
    failedMainFrameUrl = null
    lastVisibleUrl = null
    onProgress(0)
    onState(url, view.title, view.canGoBack())
  }

  override fun onPageCommitVisible(view: WebView, url: String) {
    notifyPageVisible(view, url)
  }

  override fun onPageFinished(view: WebView, url: String) {
    // Some vendor WebView builds can omit the commit callback. This is a final, idempotent
    // fallback.
    notifyPageVisible(view, url)
  }

  override fun onReceivedError(
    view: WebView,
    request: WebResourceRequest,
    error: WebResourceError,
  ) {
    if (!request.isForMainFrame) {
      onSubresourceError()
      return
    }
    val detail = error.description?.toString().orEmpty()
    failedMainFrameUrl = request.url.toString()
    val mapped =
      when (error.errorCode) {
        ERROR_TIMEOUT -> PageError.Timeout(detail)
        ERROR_HOST_LOOKUP,
        ERROR_CONNECT,
        ERROR_IO -> PageError.Network(detail)
        else -> PageError.Network(detail)
      }
    onError(mapped)
  }

  override fun onReceivedHttpError(
    view: WebView,
    request: WebResourceRequest,
    errorResponse: WebResourceResponse,
  ) {
    if (request.isForMainFrame && errorResponse.statusCode in 400..599) {
      failedMainFrameUrl = request.url.toString()
      onError(PageError.Http(errorResponse.statusCode, "服务器返回 HTTP ${errorResponse.statusCode}"))
    } else if (errorResponse.statusCode in 400..599) {
      onSubresourceError()
    }
  }

  override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
    handler.cancel() // Never bypass certificate validation.
    if (
      currentMainFrameUrl.isNullOrBlank() ||
        error.url == currentMainFrameUrl ||
        error.url == view.url
    ) {
      failedMainFrameUrl = error.url
      onError(PageError.Ssl("证书校验失败（${error.primaryError}）"))
    } else {
      onSubresourceError()
    }
  }

  @androidx.annotation.RequiresApi(26)
  override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
    injectionManager.dispose()
    onError(PageError.Renderer(if (detail.didCrash()) "网页渲染进程崩溃" else "系统回收了网页渲染进程"))
    onRendererGone()
    return true
  }

  private fun notifyPageVisible(view: WebView, url: String) {
    if (failedMainFrameUrl == url || lastVisibleUrl == url) return
    injectionManager.injectFallback(view)
    onState(url, view.title, view.canGoBack())
    lastVisibleUrl = url
    onPageCommitVisible(url)
  }
}

package dev.openbili.webdemo.feed

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import dev.openbili.webdemo.BuildConfig
import dev.openbili.webdemo.FEED_URL
import dev.openbili.webdemo.UrlDecision
import dev.openbili.webdemo.UrlPolicy
import dev.openbili.webdemo.WebViewConfigurator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Renders the public source page behind an opaque Compose feed only while metadata is needed. The
 * view is full-sized so responsive/lazy DOM content can render, but it never accepts touch or
 * accessibility focus and is destroyed as soon as the ViewModel marks the request inactive.
 */
@Composable
fun FeedSourceWebView(
  requestId: Long,
  modifier: Modifier = Modifier,
  onResult: (requestId: Long, FeedExtractionResult) -> Unit,
  onNetworkError: (requestId: Long, detail: String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val currentOnResult by rememberUpdatedState(onResult)
  val currentOnNetworkError by rememberUpdatedState(onNetworkError)

  key(requestId) {
    AndroidView(
      modifier = modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {},
      factory = { context ->
        ExtractionWebView(context).apply {
          layoutParams =
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
          isClickable = false
          isFocusable = false
          WebViewConfigurator.configure(this, BuildConfig.DEBUG)

          val extractor = FeedPageExtractor(context)
          val completeResult: (FeedExtractionResult) -> Unit = { result ->
            if (markCompleted()) currentOnResult(requestId, result)
          }
          val completeNetworkError: (String) -> Unit = { detail ->
            if (markCompleted()) currentOnNetworkError(requestId, detail)
          }

          webViewClient =
            FeedSourceClient(
              startExtraction = {
                startExtraction(scope, extractor, completeResult, completeNetworkError)
              },
              onNetworkError = completeNetworkError,
            )
          schedulePageTimeout { completeNetworkError("推荐网页加载超时，请稍后重试。") }
          loadUrl(FEED_URL)
        }
      },
      onRelease = ExtractionWebView::release,
    )
  }
}

private class ExtractionWebView(context: Context) : WebView(context) {
  private var extractionJob: Job? = null
  private var completed = false
  private var pageTimeout: Runnable? = null
  private var released = false

  fun schedulePageTimeout(onTimeout: () -> Unit) {
    pageTimeout = Runnable { onTimeout() }.also { postDelayed(it, PAGE_TIMEOUT_MILLIS) }
  }

  fun markCompleted(): Boolean {
    if (completed || released) return false
    completed = true
    pageTimeout?.let(::removeCallbacks)
    pageTimeout = null
    return true
  }

  fun startExtraction(
    scope: CoroutineScope,
    extractor: FeedPageExtractor,
    onResult: (FeedExtractionResult) -> Unit,
    onFailure: (String) -> Unit,
  ) {
    if (completed || released || extractionJob?.isActive == true) return
    extractionJob = scope.launch {
      runCatching { extractor.extract(this@ExtractionWebView) }
        .onSuccess(onResult)
        .onFailure { error ->
          if (error !is kotlinx.coroutines.CancellationException) {
            onFailure("推荐内容提取失败，请稍后重试。")
          }
        }
    }
  }

  fun release() {
    if (released) return
    released = true
    completed = true
    extractionJob?.cancel()
    extractionJob = null
    pageTimeout?.let(::removeCallbacks)
    pageTimeout = null
    stopLoading()
    webChromeClient = null
    webViewClient = WebViewClient()
    removeAllViews()
    destroy()
  }

  private companion object {
    const val PAGE_TIMEOUT_MILLIS = 20_000L
  }
}

private class FeedSourceClient(
  private val startExtraction: () -> Unit,
  private val onNetworkError: (String) -> Unit,
) : WebViewClient() {
  override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
    UrlPolicy.decide(request.url.toString()) != UrlDecision.ALLOW

  @Deprecated("Compatibility callback for older WebView providers")
  override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
    UrlPolicy.decide(url) != UrlDecision.ALLOW

  override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
    if (UrlPolicy.decide(url) != UrlDecision.ALLOW) view.stopLoading()
  }

  override fun onPageCommitVisible(view: WebView, url: String) = startExtraction()

  override fun onPageFinished(view: WebView, url: String) = startExtraction()

  override fun onReceivedError(
    view: WebView,
    request: WebResourceRequest,
    error: WebResourceError,
  ) {
    if (request.isForMainFrame) {
      val message =
        if (error.errorCode == ERROR_TIMEOUT) "推荐网页连接超时，请稍后重试。" else "无法连接推荐网页，请检查网络后重试。"
      onNetworkError(message)
    }
  }

  override fun onReceivedHttpError(
    view: WebView,
    request: WebResourceRequest,
    errorResponse: WebResourceResponse,
  ) {
    if (request.isForMainFrame && errorResponse.statusCode >= 400) {
      onNetworkError("推荐网页暂时不可用（HTTP ${errorResponse.statusCode}）。")
    }
  }

  override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
    handler.cancel()
    if (error.url == view.url || view.url.isNullOrBlank()) {
      onNetworkError("推荐网页证书校验失败，已停止加载。")
    }
  }

  override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
    onNetworkError("推荐网页渲染进程已停止，请重试。")
    return true
  }
}

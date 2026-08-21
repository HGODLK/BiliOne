package dev.openbili.webdemo.feed

/**
 * 推荐信息流的「数据源 WebView」宿主。
 *
 * 本文件负责用不可见的 WebView 打开 bilibili 公开页面并注入提取脚本，把脚本结果通过
 * 回调交给 ViewModel。WebView 生命周期、页面超时、网络/证书错误与渲染进程崩溃等边界
 * 都在这里统一处理，避免把 Android WebView 的细节泄漏到 Compose UI 层。
 */

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
 * 渲染承载数据源的 WebView 组合体。
 *
 * 仅在需要元数据时，把公开源页面渲染在不透明的 Compose 信息流背后：视图保持全尺寸以让
 * 响应式/懒加载 DOM 内容正常渲染，但从不接收触摸或无障碍焦点；一旦 ViewModel 将请求标记
 * 为失效，即被销毁。
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

/**
 * 专用于提取的 WebView 子类。
 *
 * 维护「是否已完成」「是否已释放」两个状态位，保证超时、提取完成、释放三者在并发回调
 * 下最多触发一次结果回传，并负责在释放时彻底销毁 WebView 资源。
 */
private class ExtractionWebView(context: Context) : WebView(context) {
  private var extractionJob: Job? = null
  private var completed = false
  private var pageTimeout: Runnable? = null
  private var released = false

  /** 安排页面整体加载超时，超时后调用 [onTimeout]。 */
  fun schedulePageTimeout(onTimeout: () -> Unit) {
    pageTimeout = Runnable { onTimeout() }.also { postDelayed(it, PAGE_TIMEOUT_MILLIS) }
  }

  /** 标记为已完成并移除超时任务；若已释放则返回 false 阻止重复回传。 */
  fun markCompleted(): Boolean {
    if (completed || released) return false
    completed = true
    pageTimeout?.let(::removeCallbacks)
    pageTimeout = null
    return true
  }

  /** 启动提取协程；已完成/已释放或已有提取任务时直接忽略。 */
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

  /** 释放 WebView：取消任务、移除回调、停止加载并销毁原生视图。 */
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

/**
 * 数据源页面的 WebViewClient。
 *
 * 拦截页面导航（只放行 [UrlPolicy] 允许的地址）、在页面内容可见/加载完成时触发提取，
 * 并把主框架的网络错误、HTTP 错误、SSL 错误与渲染进程崩溃统一上报为网络错误。
 */
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

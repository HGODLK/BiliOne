package dev.openbili.webdemo

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** 一个具备生命周期感知的 WebView，仅用于可见的视频页。 */
class BiliWebView(context: Context) : WebView(context) {
  private var released = false
  private var urlPollingRunnable: Runnable? = null
  private var onReleased: ((BiliWebView) -> Unit)? = null
  internal var biliWebChromeClient: BiliWebChromeClient? = null

  var injectionManager: InjectionManager? = null

  fun setOnReleasedListener(listener: (BiliWebView) -> Unit) {
    onReleased = listener
  }

  fun startUrlPolling(
    initialUrl: String,
    intervalMillis: Long = URL_POLL_INTERVAL_MILLIS,
    onUrlChanged: (String) -> Unit,
  ) {
    stopUrlPolling()
    val runnable =
      object : Runnable {
        private var lastUrl = initialUrl

        override fun run() {
          if (released) return
          val observedUrl = url
          if (!observedUrl.isNullOrBlank() && observedUrl != lastUrl) {
            lastUrl = observedUrl
            onUrlChanged(observedUrl)
          }
          if (!released) postDelayed(this, intervalMillis)
        }
      }
    urlPollingRunnable = runnable
    postDelayed(runnable, intervalMillis)
  }

  fun stopUrlPolling() {
    urlPollingRunnable?.let(::removeCallbacks)
    urlPollingRunnable = null
  }

  fun release() {
    if (released) return
    released = true
    stopUrlPolling()

    val releaseListener = onReleased
    onReleased = null
    releaseListener?.invoke(this)

    stopLoading()
    onPause()
    biliWebChromeClient?.dispose()
    biliWebChromeClient = null
    webChromeClient = null
    webViewClient = WebViewClient()
    injectionManager?.dispose()
    injectionManager = null
    clearHistory()
    removeAllViews()
    destroy()
  }

  private companion object {
    const val URL_POLL_INTERVAL_MILLIS = 750L
  }
}

/** 第一版界面状态的兼容性包装。 */
@Composable
fun BiliWebViewContainer(
  state: WebViewState,
  modifier: Modifier,
  fullscreenDelegate: BiliWebChromeClient.FullscreenDelegate,
  onCreated: (BiliWebView) -> Unit,
  onNavigation: (String, String?, Boolean) -> Unit,
  onProgress: (Int) -> Unit,
  onError: (PageError) -> Unit,
  onSubresourceError: () -> Unit,
  openExternal: (String) -> Unit,
  onRendererGone: () -> Unit,
  onPageCommitVisible: (String) -> Unit = {},
  onReleased: (BiliWebView) -> Unit = {},
  @ColorInt backgroundColor: Int = Color.TRANSPARENT,
) {
  VideoWebViewContainer(
    initialUrl = state.currentUrl,
    webViewGeneration = state.webViewGeneration,
    modifier = modifier,
    fullscreenDelegate = fullscreenDelegate,
    onCreated = onCreated,
    onNavigation = onNavigation,
    onProgress = onProgress,
    onError = onError,
    onSubresourceError = onSubresourceError,
    openExternal = openExternal,
    onRendererGone = onRendererGone,
    onPageCommitVisible = onPageCommitVisible,
    onReleased = onReleased,
    backgroundColor = backgroundColor,
  )
}

/**
 * 承载视频屏使用的唯一长生命周期 WebView。推荐源的 WebView 有意保持独立，
 * 不得使用此容器。
 */
@Composable
fun VideoWebViewContainer(
  initialUrl: String,
  webViewGeneration: Int,
  modifier: Modifier,
  fullscreenDelegate: BiliWebChromeClient.FullscreenDelegate,
  onCreated: (BiliWebView) -> Unit,
  onNavigation: (String, String?, Boolean) -> Unit,
  onProgress: (Int) -> Unit,
  onError: (PageError) -> Unit,
  onSubresourceError: () -> Unit,
  openExternal: (String) -> Unit,
  onRendererGone: () -> Unit,
  onPageCommitVisible: (String) -> Unit = {},
  onReleased: (BiliWebView) -> Unit = {},
  onDebugInfo: (VideoDebugInfo) -> Unit = {},
  @ColorInt backgroundColor: Int = Color.TRANSPARENT,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  var current by remember { mutableStateOf<BiliWebView?>(null) }

  DisposableEffect(lifecycleOwner, current) {
    val webView = current
    val observer = LifecycleEventObserver { _, event ->
      webView?.let { web ->
        when (event) {
          Lifecycle.Event.ON_PAUSE -> web.onPause()
          Lifecycle.Event.ON_RESUME -> web.onResume()
          Lifecycle.Event.ON_DESTROY -> web.release()
          else -> Unit
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  key(webViewGeneration) {
    AndroidView(
      modifier = modifier,
      factory = { context ->
        BiliWebView(context).apply {
          layoutParams =
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
          val injection = InjectionManager(context)
          injectionManager = injection
          WebViewConfigurator.configure(this, BuildConfig.DEBUG)

          // ── 用于视频播放的桌面 UA ──────────────────────────
          // 在第一次 loadUrl 之前设置，让服务器从一开始就见到桌面 UA。
          val desktopUa = UserAgentProvider.desktopUserAgent(context)
          if (desktopUa != null) {
            settings.userAgentString = desktopUa
            if (BuildConfig.DEBUG) {
              Log.d(TAG, "videoUrl = $initialUrl")
              Log.d(TAG, "userAgentString set = $desktopUa")
            }
          }

          // 上报当前信息（实际 UA 仍未知）。
          onDebugInfo(
            VideoDebugInfo(
              setUserAgent = desktopUa,
              actualUserAgent = null,
              videoUrl = initialUrl,
              uaApplied = desktopUa != null,
            )
          )

          settings.setSupportMultipleWindows(true)
          settings.javaScriptCanOpenWindowsAutomatically = false
          setBackgroundColor(backgroundColor)
          webViewClient =
            BiliWebViewClient(
              injectionManager = injection,
              onState = onNavigation,
              onProgress = onProgress,
              onError = onError,
              onSubresourceError = onSubresourceError,
              openExternal = openExternal,
              onRendererGone = onRendererGone,
              onPageCommitVisible = onPageCommitVisible,
            )
          biliWebChromeClient =
            BiliWebChromeClient(
                onProgress = onProgress,
                onTitle = { title -> onNavigation(url ?: initialUrl, title, canGoBack()) },
                fullscreenDelegate = fullscreenDelegate,
                routeNewWindow = { target ->
                  when (UrlPolicy.decide(target)) {
                    UrlDecision.ALLOW -> loadUrl(target)
                    UrlDecision.EXTERNAL -> openExternal(target)
                    UrlDecision.BLOCK -> Unit
                  }
                },
              )
              .also { webChromeClient = it }
          injection.installDocumentStart(this)
          setOnReleasedListener { releasedWebView ->
            if (current === releasedWebView) current = null
            onReleased(releasedWebView)
          }
          current = this
          onCreated(this)

          when (UrlPolicy.decide(initialUrl)) {
            UrlDecision.ALLOW -> {
              startUrlPolling(initialUrl) { observedUrl ->
                injection.injectFallback(this)
                onNavigation(observedUrl, title, canGoBack())
              }
              loadUrl(initialUrl)
              if (BuildConfig.DEBUG) {
                // 页面开始加载后校验生效的 navigator.userAgent。
                postDelayed(
                  {
                    evaluateJavascript("navigator.userAgent") { actual ->
                      val cleaned = actual?.trim('"')
                      Log.d(TAG, "navigator.userAgent = $cleaned")
                      onDebugInfo(
                        VideoDebugInfo(
                          setUserAgent = desktopUa,
                          actualUserAgent = cleaned,
                          videoUrl = initialUrl,
                          uaApplied =
                            desktopUa != null &&
                              cleaned != null &&
                              !cleaned.contains("Android") &&
                              !cleaned.contains("Mobile"),
                        )
                      )
                    }
                  },
                  UA_CHECK_DELAY_MILLIS,
                )
              }
            }
            UrlDecision.EXTERNAL -> {
              onError(PageError.ExternalOpen("初始视频地址不允许在应用内加载。"))
              openExternal(initialUrl)
            }
            UrlDecision.BLOCK -> onError(PageError.ExternalOpen("已阻止不安全的初始视频地址。"))
          }
        }
      },
      onRelease = BiliWebView::release,
    )
  }
}

private const val TAG = "VideoWebView"
private const val UA_CHECK_DELAY_MILLIS = 2_000L

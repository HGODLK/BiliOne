package dev.openbili.webdemo

import android.content.Context
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

class InjectionManager(context: Context) {
  private val commonCss = context.assets.readText("injection/common.css")
  private val videoCss = context.assets.readText("injection/video.css")
  private val bootstrap = context.assets.readText("injection/bootstrap.js")
  private val videoCleanup = context.assets.readText("injection/video-cleanup.js")
  private var documentStartHandler: ScriptHandler? = null

  val supportsDocumentStart: Boolean
    get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

  private val script: String by lazy {
    "window.__BILI_DEMO_VIDEO_STYLES__={common:${JSONObject.quote(commonCss)},video:${JSONObject.quote(videoCss)}};\n$bootstrap\n$videoCleanup"
  }

  fun installDocumentStart(webView: WebView) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    documentStartHandler?.remove()
    documentStartHandler =
      WebViewCompat.addDocumentStartJavaScript(
        webView,
        script,
        setOf("https://bilibili.com", "https://*.bilibili.com", "https://b23.tv"),
      )
  }

  fun injectFallback(webView: WebView) {
    if (UrlPolicy.decide(webView.url) == UrlDecision.ALLOW) webView.evaluateJavascript(script, null)
  }

  fun dispose() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
      documentStartHandler?.remove()
    documentStartHandler = null
  }

  private fun android.content.res.AssetManager.readText(path: String): String =
    open(path).bufferedReader().use { it.readText() }
}

package dev.openbili.webdemo

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewFeature
import androidx.webkit.WebSettingsCompat

object WebViewConfigurator {
  @Suppress("DEPRECATION")
  @SuppressLint("SetJavaScriptEnabled")
  fun configure(webView: WebView, debug: Boolean) {
    WebView.setWebContentsDebuggingEnabled(debug)
    with(webView.settings) {
      javaScriptEnabled = true
      domStorageEnabled = true
      allowFileAccess = false
      allowContentAccess = false
      allowFileAccessFromFileURLs = false
      allowUniversalAccessFromFileURLs = false
      mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      mediaPlaybackRequiresUserGesture = true
      builtInZoomControls = false
      displayZoomControls = false
      loadWithOverviewMode = true
      useWideViewPort = true
      if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
        WebSettingsCompat.setSafeBrowsingEnabled(this, true)
      }
    }
    CookieManager.getInstance().apply {
      setAcceptCookie(true)
      // Third-party cookies stay disabled: the demo does not require a cross-site login flow.
      setAcceptThirdPartyCookies(webView, false)
    }
    webView.isHorizontalScrollBarEnabled = false
    webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
  }
}

package dev.openbili.webdemo

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

object Diagnostics {
  fun create(context: Context, state: WebViewState, javaScriptEnabled: Boolean): String {
    val provider = WebViewCompat.getCurrentWebViewPackage(context)
    val config = context.resources.configuration
    val version =
      runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull() ?: "unknown"
    return listOf(
        "设备制造商: ${Build.MANUFACTURER}",
        "设备型号: ${Build.MODEL}",
        "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "应用版本: $version",
        "WebView provider: ${provider?.packageName ?: "unknown"}",
        "WebView 版本: ${provider?.versionName ?: "unknown"}",
        "当前 URL: ${UrlPolicy.redactSensitiveQuery(state.currentUrl)}",
        "窗口: ${config.screenWidthDp}dp × ${config.screenHeightDp}dp",
        "方向: ${if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) "横屏" else "竖屏"}",
        "视频全屏: ${state.isFullscreen}",
        "JavaScript: $javaScriptEnabled",
        "Document-start: ${WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)}",
        "Safe Browsing: ${WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)}",
        "子资源错误计数: ${state.subresourceErrors}",
      )
      .joinToString("\n")
  }
}

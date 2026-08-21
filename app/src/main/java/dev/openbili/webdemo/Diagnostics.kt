package dev.openbili.webdemo

/**
 * 应用诊断信息收集器。
 *
 * 提供 [Diagnostics.create]，把设备、WebView、屏幕方向与播放状态等运行时信息
 * 汇总成一段纯文本，方便在日志或"关于/诊断"界面里排查问题。字符串字面量大多
 * 已经是面向用户的中文标签，这里只负责把它们拼接起来。
 */

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

object Diagnostics {
  /**
   * 生成一段多行的运行时诊断快照。
   *
   * @param context 应用上下文，用于读取包版本与资源配置。
   * @param state 当前 WebView 状态，提供 URL、全屏位与子资源错误计数。
   * @param javaScriptEnabled JavaScript 是否启用。
   * @return 以换行符分隔的诊断文本，可直接展示或写入日志。
   */
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

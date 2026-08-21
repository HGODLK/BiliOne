package dev.openbili.webdemo

import android.content.Context
import android.util.Log
import android.webkit.WebSettings

/**
 * 从系统 WebView 默认 UA 中提取当前 Chrome 版本，构建桌面版 Chrome user-agent 字符串。
 * 结果使用 Linux x86_64 桌面格式，排除 Android、Mobile、wv 和 Version/4.0 标记。
 */
object UserAgentProvider {

  private const val TAG = "UserAgentProvider"
  private val chromeVersionRegex = Regex("""Chrome/(\d+\.\d+\.\d+\.\d+)""")

  /**
   * 返回适合视频 WebView 的桌面 Chrome UA；当无法从默认 UA 提取出 Chrome 版本时
   * 返回 null。
   */
  fun desktopUserAgent(context: Context): String? {
    val defaultUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("")
    val version = chromeVersionRegex.find(defaultUa)?.groupValues?.get(1)
    if (version.isNullOrBlank()) {
      Log.w(
        TAG,
        "Cannot extract Chrome version from default UA; keeping default. defaultUa=$defaultUa",
      )
      return null
    }

    val desktopUa =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)" +
        " Chrome/$version Safari/537.36"

    if (BuildConfig.DEBUG) {
      Log.d(TAG, "default UA = $defaultUa")
      Log.d(TAG, "desktop UA = $desktopUa")
      Log.d(TAG, "Chrome version extracted = $version")
    }

    return desktopUa
  }
}

package dev.openbili.webdemo

import android.content.Context
import android.util.Log
import android.webkit.WebSettings

/**
 * Builds a desktop Chrome user-agent string by extracting the current Chrome version from the
 * system WebView default UA. The result uses a Linux x86_64 desktop format that excludes Android,
 * Mobile, wv, and Version/4.0 markers.
 */
object UserAgentProvider {

  private const val TAG = "UserAgentProvider"
  private val chromeVersionRegex = Regex("""Chrome/(\d+\.\d+\.\d+\.\d+)""")

  /**
   * Returns a desktop Chrome UA suitable for the video WebView, or null when the Chrome version
   * cannot be extracted from the default UA.
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

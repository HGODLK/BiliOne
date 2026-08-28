package dev.openbili.webdemo.video

import java.net.URI
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request

private const val MAX_COMMENT_SHORT_LINK_REDIRECTS = 5
private const val MAX_COMMENT_SHORT_LINK_CACHE_SIZE = 160

/**
 * b23 分享短码本身不含 BV/av 号，只能通过受限重定向恢复视频标识。
 * 每一跳都限制在 B 站域名内，不携带账号 Cookie，也不会缓存失败结果。
 */
internal object CommentShortVideoLinkResolver {
  private val client =
    OkHttpClient.Builder()
      .cookieJar(CookieJar.NO_COOKIES)
      .followRedirects(false)
      .followSslRedirects(false)
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .callTimeout(15, TimeUnit.SECONDS)
      .build()
  private val successCache =
    object : LinkedHashMap<String, String>(MAX_COMMENT_SHORT_LINK_CACHE_SIZE, .75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
        size > MAX_COMMENT_SHORT_LINK_CACHE_SIZE
    }

  fun resolve(rawUrl: String): String? {
    return runCatching { resolveNetwork(rawUrl) }.getOrNull()
  }

  private fun resolveNetwork(rawUrl: String): String? {
    val initialUrl = normalizedB23Url(rawUrl) ?: return null
    synchronized(successCache) { successCache[initialUrl]?.let { return it } }
    var currentUrl = initialUrl
    repeat(MAX_COMMENT_SHORT_LINK_REDIRECTS + 1) { redirectIndex ->
      val request =
        Request.Builder()
          .url(currentUrl)
          .header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36",
          )
          .header("Referer", "https://www.bilibili.com/")
          .get()
          .build()
      client.newCall(request).execute().use { response ->
        if (response.isRedirect) {
          if (redirectIndex >= MAX_COMMENT_SHORT_LINK_REDIRECTS) return null
          val location = response.header("Location") ?: return null
          val nextUrl = response.request.url.resolve(location) ?: return null
          if (nextUrl.scheme != "https" || !isAllowedBilibiliHost(nextUrl.host)) return null
          currentUrl = nextUrl.toString()
          return@repeat
        }
        if (!response.isSuccessful) return null
        val reference = extractCommentVideoReferenceFromUrl(response.request.url.toString()) ?: return null
        synchronized(successCache) { successCache[initialUrl] = reference }
        return reference
      }
    }
    return null
  }

  private fun normalizedB23Url(rawUrl: String): String? {
    val normalized =
      when {
        rawUrl.startsWith("//") -> "https:$rawUrl"
        rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl
        else -> return null
      }
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (
      !uri.host.equals("b23.tv", ignoreCase = true) &&
        !uri.host.equals("www.b23.tv", ignoreCase = true)
    ) {
      return null
    }
    return uri.toString()
  }

  private fun isAllowedBilibiliHost(host: String): Boolean {
    val normalized = host.lowercase()
    return normalized == "b23.tv" ||
      normalized == "www.b23.tv" ||
      normalized == "bilibili.com" ||
      normalized.endsWith(".bilibili.com")
  }
}

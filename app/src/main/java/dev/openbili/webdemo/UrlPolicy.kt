package dev.openbili.webdemo

import androidx.core.net.toUri
import java.net.URI
import java.util.Locale

enum class UrlDecision {
  ALLOW,
  EXTERNAL,
  BLOCK,
}

object UrlPolicy {
  private val blockedSchemes = setOf("javascript", "file", "content", "data")
  private val externalSchemes = setOf("mailto", "tel", "intent", "market")
  private val sensitiveKeys =
    setOf("token", "access_token", "auth", "code", "password", "ticket", "session", "sign")

  fun decide(rawUrl: String?): UrlDecision {
    if (rawUrl.isNullOrBlank()) return UrlDecision.BLOCK
    val uri = runCatching { rawUrl.toUri() }.getOrNull() ?: return UrlDecision.BLOCK
    val scheme = uri.scheme?.lowercase() ?: return UrlDecision.BLOCK
    if (scheme in blockedSchemes) return UrlDecision.BLOCK
    if (scheme in externalSchemes || scheme != "https") return UrlDecision.EXTERNAL
    val host = uri.host?.trimEnd('.')?.lowercase() ?: return UrlDecision.BLOCK
    if (!isAllowedHost(host)) return UrlDecision.EXTERNAL
    val path = uri.path.orEmpty().lowercase()
    if (path.startsWith("/pay") || path.startsWith("/payment")) return UrlDecision.EXTERNAL
    return UrlDecision.ALLOW
  }

  fun isAllowedHost(host: String): Boolean {
    val normalized = host.trimEnd('.').lowercase()
    return normalized == "bilibili.com" ||
      normalized.endsWith(".bilibili.com") ||
      normalized == "b23.tv"
  }

  /**
   * 解析信息流链接并返回规范化的视频页 URL。信息流提取有意比通用导航更窄：短链接和
   * 非视频 B 站页面不是信息流条目。
   */
  fun normalizeVideoUrl(
    rawUrl: String?,
    baseUrl: String = "https://www.bilibili.com/",
  ): String? {
    val uri = resolve(rawUrl, baseUrl) ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.rawUserInfo != null || (uri.port != -1 && uri.port != 443)) return null

    val rawHost = uri.host?.trimEnd('.')?.lowercase(Locale.ROOT) ?: return null
    if (!isBilibiliHost(rawHost)) return null
    // 重写移动子域，让桌面 UA 生效时由桌面站点提供服务。
    val host = if (rawHost == "m.bilibili.com") "www.bilibili.com" else rawHost

    val path = uri.path.orEmpty()
    if (!path.lowercase(Locale.ROOT).startsWith("/video/") || path.length <= "/video/".length) {
      return null
    }

    return runCatching { URI("https", null, host, -1, path, null, null).toASCIIString() }
      .getOrNull()
  }

  /**
   * 把协议相对或相对封面 URL 转换为 HTTPS，并拒绝主动式/非网页协议。
   */
  fun normalizeImageUrl(
    rawUrl: String?,
    baseUrl: String = "https://www.bilibili.com/",
  ): String? {
    val uri = resolve(rawUrl, baseUrl) ?: return null
    // 离线媒体子系统拥有这个确切的应用私有路径，从不把它暴露到应用之外。只允许其
    // 持久化的封面文件；继续拒绝其他所有本地/主动式协议。
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme == "file") {
      val path = uri.path.orEmpty().replace('\\', '/')
      return uri.toASCIIString().takeIf {
        uri.rawQuery == null &&
          uri.rawFragment == null &&
          uri.rawUserInfo == null &&
          path.contains("/files/offline_media/metadata/") &&
          path.endsWith("/cover.jpg")
      }
    }
    // 接受 http（强制升级到 https）和 https；拒绝其他一切。
    if (scheme != "https" && scheme != "http") return null
    if (uri.rawUserInfo != null) return null
    val host = uri.host?.trimEnd('.')?.lowercase(Locale.ROOT) ?: return null
    val port = uri.port
    if (port < -1 || port > 65535) return null
    val path = uri.path.orEmpty().ifBlank { "/" }
    return runCatching {
        URI("https", null, host, port, path, uri.query, null).toASCIIString()
      }
      .getOrNull()
  }

  fun isVideoPage(rawUrl: String): Boolean {
    val uri = rawUrl.toUri()
    val path = uri.path.orEmpty().lowercase()
    return isAllowedHost(uri.host.orEmpty()) &&
      (path.startsWith("/video/") || path.startsWith("/bangumi/play/"))
  }

  fun isFeedPage(rawUrl: String): Boolean {
    val uri = rawUrl.toUri()
    return isAllowedHost(uri.host.orEmpty()) &&
      (uri.path.isNullOrBlank() || uri.path == "/" || uri.path?.startsWith("/v/popular") == true)
  }

  fun redactSensitiveQuery(rawUrl: String): String {
    val uri = runCatching { rawUrl.toUri() }.getOrNull() ?: return "<invalid-url>"
    if (uri.queryParameterNames.isEmpty()) return rawUrl
    val builder = uri.buildUpon().clearQuery()
    uri.queryParameterNames.sorted().forEach { key ->
      val values = uri.getQueryParameters(key)
      values.forEach { value ->
        builder.appendQueryParameter(
          key,
          if (key.lowercase() in sensitiveKeys) "<redacted>" else value,
        )
      }
    }
    return builder.build().toString()
  }

  private fun isBilibiliHost(host: String): Boolean =
    host == "bilibili.com" || host.endsWith(".bilibili.com")

  private fun resolve(rawUrl: String?, baseUrl: String): URI? {
    val candidate = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val base = URI(baseUrl.trim())
        base.resolve(URI(candidate)).normalize()
      }
      .getOrNull()
  }
}

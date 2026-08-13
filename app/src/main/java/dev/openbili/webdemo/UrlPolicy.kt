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
   * Resolves a feed link and returns a canonical video page URL. Feed extraction is deliberately
   * narrower than general navigation: short links and non-video Bilibili pages are not feed items.
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
    // Rewrite the mobile subdomain so the desktop site is served when combined with a desktop UA.
    val host = if (rawHost == "m.bilibili.com") "www.bilibili.com" else rawHost

    val path = uri.path.orEmpty()
    if (!path.lowercase(Locale.ROOT).startsWith("/video/") || path.length <= "/video/".length) {
      return null
    }

    return runCatching { URI("https", null, host, -1, path, null, null).toASCIIString() }
      .getOrNull()
  }

  /**
   * Converts protocol-relative or relative cover URLs to HTTPS and rejects active/non-web schemes.
   */
  fun normalizeImageUrl(
    rawUrl: String?,
    baseUrl: String = "https://www.bilibili.com/",
  ): String? {
    val uri = resolve(rawUrl, baseUrl) ?: return null
    // The offline-media subsystem owns this exact app-private path and never exposes it outside the
    // app. Allow only its persisted cover file; continue rejecting every other local/active scheme.
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
    // Accept http (force-upgrade to https) and https; reject everything else.
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

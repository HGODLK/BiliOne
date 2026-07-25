package dev.openbili.webdemo.feed

import dev.openbili.webdemo.UrlPolicy
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object FeedItemJsonParser {
  private const val MAX_ITEMS = 40
  private const val MAX_TEXT_LENGTH = 300

  fun parse(rawJavascriptResult: String?): FeedExtractionResult {
    val envelope = decodeEnvelope(rawJavascriptResult) ?: return invalidResponse()
    val stats = parseStats(envelope.optJSONObject("stats"))
    return when (envelope.optString("status")) {
      "success" -> parseSuccess(envelope.optJSONArray("items"), stats)
      "empty" -> {
        val error = envelope.optJSONObject("error")
        FeedExtractionResult.Empty(
          stats,
          safeText(error?.optString("message"), MAX_TEXT_LENGTH) ?: "页面尚未出现视频卡片",
        )
      }
      "error" -> parseError(envelope.optJSONObject("error"), stats)
      else -> invalidResponse(stats)
    }
  }

  private fun parseSuccess(
    array: JSONArray?,
    sourceStats: FeedExtractionStats,
  ): FeedExtractionResult {
    if (array == null) return invalidResponse(sourceStats)

    val items = ArrayList<FeedItem>(minOf(array.length(), MAX_ITEMS))
    val seenUrls = HashSet<String>()
    var invalidUrls = 0
    var missingTitles = 0
    var missingCovers = 0
    var duplicates = 0

    for (index in 0 until array.length()) {
      val value = array.optJSONObject(index) ?: continue
      val videoUrl = UrlPolicy.normalizeVideoUrl(value.stringOrNull("videoUrl"))
      if (videoUrl == null) {
        invalidUrls++
        continue
      }
      val title = safeText(value.stringOrNull("title"), MAX_TEXT_LENGTH)
      if (title == null) {
        missingTitles++
        continue
      }
      val coverUrl = UrlPolicy.normalizeImageUrl(value.stringOrNull("coverUrl"))
      if (coverUrl == null) {
        missingCovers++
        continue
      }
      if (!seenUrls.add(videoUrl)) {
        duplicates++
        continue
      }
      if (items.size == MAX_ITEMS) continue

      items +=
        FeedItem(
          id = safeText(value.stringOrNull("id"), MAX_TEXT_LENGTH) ?: deriveId(videoUrl),
          title = title,
          videoUrl = videoUrl,
          coverUrl = coverUrl,
          uploader = safeText(value.stringOrNull("uploader"), MAX_TEXT_LENGTH),
          playCount = safeText(value.stringOrNull("playCount"), MAX_TEXT_LENGTH),
          duration = safeText(value.stringOrNull("duration"), MAX_TEXT_LENGTH),
        )
    }

    val stats =
      sourceStats.copy(
        uniqueItems = items.size,
        duplicateItems = sourceStats.duplicateItems + duplicates,
        filteredInvalidVideoUrl = sourceStats.filteredInvalidVideoUrl + invalidUrls,
        filteredMissingTitle = sourceStats.filteredMissingTitle + missingTitles,
        filteredMissingCover = sourceStats.filteredMissingCover + missingCovers,
      )
    return if (items.isEmpty()) {
      FeedExtractionResult.Failure(
        code = FeedExtractionErrorCode.NO_VALID_ITEMS,
        message = "提取结果中没有可安全显示的视频卡片",
        stats = stats,
        retryable = true,
      )
    } else {
      FeedExtractionResult.Success(items, stats)
    }
  }

  private fun parseError(
    error: JSONObject?,
    stats: FeedExtractionStats,
  ): FeedExtractionResult.Failure {
    val sourceCode = error?.stringOrNull("code")
    val code =
      if (sourceCode == "NO_VALID_ITEMS") FeedExtractionErrorCode.NO_VALID_ITEMS
      else FeedExtractionErrorCode.SCRIPT_ERROR
    return FeedExtractionResult.Failure(
      code = code,
      message = safeText(error?.stringOrNull("message"), MAX_TEXT_LENGTH) ?: "推荐内容提取脚本执行失败",
      stats = stats,
      retryable = error?.optBoolean("retryable", false) == true,
    )
  }

  private fun parseStats(value: JSONObject?): FeedExtractionStats =
    FeedExtractionStats(
      videoLinksFound = value.nonNegativeInt("videoLinksFound"),
      parsedItems = value.nonNegativeInt("parsedItems"),
      uniqueItems = value.nonNegativeInt("uniqueItems"),
      duplicateItems = value.nonNegativeInt("duplicateItems"),
      filteredInvalidVideoUrl = value.nonNegativeInt("filteredInvalidVideoUrl"),
      filteredMissingTitle = value.nonNegativeInt("filteredMissingTitle"),
      filteredMissingCover = value.nonNegativeInt("filteredMissingCover"),
    )

  private fun decodeEnvelope(raw: String?): JSONObject? {
    val value =
      raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" && it != "undefined" } ?: return null
    return runCatching {
        when (val decoded = JSONTokener(value).nextValue()) {
          is JSONObject -> decoded
          is String -> JSONTokener(decoded).nextValue() as? JSONObject
          else -> null
        }
      }
      .getOrNull()
  }

  private fun deriveId(videoUrl: String): String =
    runCatching { URI(videoUrl).path.substringAfterLast('/') }.getOrDefault(videoUrl)

  private fun safeText(value: String?, maxLength: Int): String? =
    value?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }?.take(maxLength)

  private fun JSONObject?.nonNegativeInt(key: String): Int =
    this?.optInt(key, 0)?.coerceAtLeast(0) ?: 0

  private fun JSONObject.stringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it != "null" }

  private fun invalidResponse(
    stats: FeedExtractionStats = FeedExtractionStats()
  ): FeedExtractionResult.Failure =
    FeedExtractionResult.Failure(
      code = FeedExtractionErrorCode.INVALID_RESPONSE,
      message = "推荐内容提取器返回了无效结果",
      stats = stats,
      retryable = true,
    )
}

package dev.openbili.webdemo.feed

import dev.openbili.webdemo.UrlPolicy
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 网页提取脚本结果的 JSON 解析器。
 *
 * 脚本在 WebView 中返回一个「信封」结构（status/items/error/stats），本对象负责把它
 * 解析为强类型的 [FeedExtractionResult]，并在解析过程中做二次校验与统计累加：
 * 过滤非法视频链接、缺失标题/封面的条目、去重，并限制最大条目数与文本长度，
 * 确保进入 UI 的数据是安全、可控的。
 */
object FeedItemJsonParser {
  /** 单次提取最多保留的卡片数量。 */
  private const val MAX_ITEMS = 40
  /** 单个文本字段的最大长度，避免超长内容拖慢排版。 */
  private const val MAX_TEXT_LENGTH = 300

  /** 解析脚本原始返回，按 status 分派到成功/空/错误三种结果。 */
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

  /** 解析成功信封中的 items 数组，逐条校验并统计过滤数量。 */
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

    // 逐条校验：视频链接、标题、封面必须同时有效，否则计入对应的过滤统计。
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
      // 同一视频链接只保留一条，重复项计入 duplicateItems。
      if (!seenUrls.add(videoUrl)) {
        duplicates++
        continue
      }
      // 达到上限后丢弃多余条目（仍会继续统计后续重复/过滤项）。
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

  /** 解析 error 信封，把脚本错误码映射为归一化 [FeedExtractionErrorCode]。 */
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

  /** 解析信封中的 stats 字段，缺失或负数一律归一为 0。 */
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

  /** 把脚本返回的原始字符串解码为 JSON 对象，兼容「JSON 字符串再包一层字符串」的情况。 */
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

  /** 当脚本未提供 id 时，从视频 URL 路径末尾推导一个稳定 id。 */
  private fun deriveId(videoUrl: String): String =
    runCatching { URI(videoUrl).path.substringAfterLast('/') }.getOrDefault(videoUrl)

  /** 压缩空白并裁剪到指定长度；空串视为无效返回 null。 */
  private fun safeText(value: String?, maxLength: Int): String? =
    value?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }?.take(maxLength)

  /** 读取非负整数，缺失或负数一律归 0。 */
  private fun JSONObject?.nonNegativeInt(key: String): Int =
    this?.optInt(key, 0)?.coerceAtLeast(0) ?: 0

  /** 读取字符串，把 JSON null 与字符串 "null" 一并视为缺失。 */
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

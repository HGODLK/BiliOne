package dev.openbili.webdemo.feed

/**
 * 推荐信息流「网页提取」结果模型。
 *
 * 推荐首页的数据来自对 bilibili 公开页面注入脚本后在 WebView 中执行提取，本文件集中
 * 定义提取过程的统计指标、错误码以及最终结果的封闭类型：
 *  - [FeedExtractionStats] 记录每一步过滤的数量，便于诊断；
 *  - [FeedExtractionErrorCode] 枚举脚本执行/解析各环节的失败原因；
 *  - [FeedExtractionResult] 用 Success/Empty/Failure 三类密封子类型承载结果，
 *    供 [FeedPageExtractor] 与 [FeedViewModel] 之间以不可变对象传递。
 */

/**
 * 一次提取过程的过滤统计。
 *
 * 各计数累加自脚本上报的统计与 Kotlin 侧二次校验，用于观测过滤漏斗，不参与业务判断。
 */
data class FeedExtractionStats(
  val videoLinksFound: Int = 0,
  val parsedItems: Int = 0,
  val uniqueItems: Int = 0,
  val duplicateItems: Int = 0,
  val filteredInvalidVideoUrl: Int = 0,
  val filteredMissingTitle: Int = 0,
  val filteredMissingCover: Int = 0,
)

/**
 * 提取失败的归一化错误码。
 *
 * 与 [FeedExtractionResult.Failure.code] 配对，供上层决定是否可重试以及展示何种兜底文案。
 */
enum class FeedExtractionErrorCode {
  INVALID_RESPONSE,
  NO_VALID_ITEMS,
  SCRIPT_ERROR,
  EVALUATION_FAILED,
  EVALUATION_TIMEOUT,
}

/**
 * 提取结果的封闭类型。
 *
 * 统一携带 [stats]，子类型分别表达成功、空页面与失败三种结局。
 */
sealed interface FeedExtractionResult {
  val stats: FeedExtractionStats

  data class Success(
    val items: List<FeedItem>,
    override val stats: FeedExtractionStats,
  ) : FeedExtractionResult

  data class Empty(
    override val stats: FeedExtractionStats,
    val message: String = "页面尚未出现视频卡片",
  ) : FeedExtractionResult

  data class Failure(
    val code: FeedExtractionErrorCode,
    val message: String,
    override val stats: FeedExtractionStats = FeedExtractionStats(),
    val retryable: Boolean = false,
  ) : FeedExtractionResult
}

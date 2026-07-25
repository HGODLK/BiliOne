package dev.openbili.webdemo.feed

data class FeedExtractionStats(
  val videoLinksFound: Int = 0,
  val parsedItems: Int = 0,
  val uniqueItems: Int = 0,
  val duplicateItems: Int = 0,
  val filteredInvalidVideoUrl: Int = 0,
  val filteredMissingTitle: Int = 0,
  val filteredMissingCover: Int = 0,
)

enum class FeedExtractionErrorCode {
  INVALID_RESPONSE,
  NO_VALID_ITEMS,
  SCRIPT_ERROR,
  EVALUATION_FAILED,
  EVALUATION_TIMEOUT,
}

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

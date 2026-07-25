package dev.openbili.webdemo.feed

sealed interface FeedUiState {
  val isRefreshing: Boolean
    get() = false

  val stats: FeedExtractionStats?
    get() = null

  data object Loading : FeedUiState

  data class Content(
    val items: List<FeedItem>,
    override val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val refreshMessage: String? = null,
    override val stats: FeedExtractionStats? = null,
  ) : FeedUiState

  data class Empty(
    val message: String = "暂时没有可显示的推荐内容",
    override val stats: FeedExtractionStats? = null,
  ) : FeedUiState

  data class ExtractionError(
    val detail: String,
    override val stats: FeedExtractionStats? = null,
  ) : FeedUiState

  data class NetworkError(val detail: String) : FeedUiState
}

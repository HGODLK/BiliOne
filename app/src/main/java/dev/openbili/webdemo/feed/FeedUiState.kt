package dev.openbili.webdemo.feed

/**
 * 信息流页面的 UI 状态模型。
 *
 * 推荐与热门两条信息流共用这一状态树：Loading（首屏加载）、Content（有内容）、
 * Empty（空）、ExtractionError（提取/脚本失败）与 NetworkError（网络失败）。
 * [stats] 用于在错误/空态透出提取统计，[isRefreshing] 表示下拉刷新进行中。
 */
sealed interface FeedUiState {
  /** 是否正在下拉刷新；默认无内容时视为 false。 */
  val isRefreshing: Boolean
    get() = false

  /** 提取统计；仅部分子类型携带，默认 null。 */
  val stats: FeedExtractionStats?
    get() = null

  /** 首屏加载中。 */
  data object Loading : FeedUiState

  /** 已加载出内容，可能同时处于刷新或加载更多状态。 */
  data class Content(
    val items: List<FeedItem>,
    override val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val refreshMessage: String? = null,
    override val stats: FeedExtractionStats? = null,
  ) : FeedUiState

  /** 空态：接口返回无卡片时的提示页。 */
  data class Empty(
    val message: String = "暂时没有可显示的推荐内容",
    override val stats: FeedExtractionStats? = null,
  ) : FeedUiState

  /** 提取失败：脚本执行或解析环节出错，携带统计便于诊断。 */
  data class ExtractionError(
    val detail: String,
    override val stats: FeedExtractionStats? = null,
  ) : FeedUiState

  /** 网络失败：无法连接或页面超时等错误。 */
  data class NetworkError(val detail: String) : FeedUiState
}

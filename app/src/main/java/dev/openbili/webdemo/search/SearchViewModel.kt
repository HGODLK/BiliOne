package dev.openbili.webdemo.search

/**
 * 搜索模块：状态定义、搜索分类/排序枚举与 [SearchViewModel]。
 *
 * 本文件承载 B 站搜索入口的完整状态与业务逻辑：
 *  - [SearchCategory]、[SearchOrder]、[ArticleSearchOrder] 描述搜索页的分类与排序维度；
 *  - [SearchUiState] 集中保存查询词、热搜、联想词、各类结果、分页与加载状态；
 *  - [SearchViewModel] 负责热搜/联想/各分类搜索的拉取、分页加载、去重，以及搜索历史的
 *    本地持久化。
 *
 * 并发约束：通过 searchGeneration / suggestionGeneration 两个代数计数来“作废”过期请求；
 * 结果回填前校验“代数 + 关键词 + 分类”三重一致性，避免旧请求覆盖新状态。
 */

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliSearchApi
import dev.openbili.webdemo.api.HotSearchItem
import dev.openbili.webdemo.api.SearchUser
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.live.BiliLiveApi
import dev.openbili.webdemo.live.LiveSearchRoom
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 搜索结果的分类页签。
 *
 * @param title 页签展示文案；
 * @param enabled 是否允许切换到该分类（用于在特定环境下禁用某类搜索）。
 */
enum class SearchCategory(val title: String, val enabled: Boolean) {
  VIDEO("视频", true),
  BANGUMI("番剧", true),
  CINEMA("影视", true),
  LIVE("直播", true),
  ARTICLE("专栏", true),
  USER("用户", true),
}

/**
 * 视频分类下的结果排序方式。
 *
 * @param apiValue 对应 B 站搜索接口的 order 参数取值。
 */
enum class SearchOrder(val title: String, val apiValue: String) {
  COMPREHENSIVE("综合排序", "totalrank"),
  MOST_PLAYED("最多播放", "click"),
  NEWEST("最新发布", "pubdate"),
  MOST_DANMAKU("最多弹幕", "dm"),
  MOST_FAVORITED("最多收藏", "stow"),
}

/**
 * 专栏分类下的结果排序方式。
 *
 * @param apiValue 对应 B 站专栏搜索接口的 order 参数取值。
 */
enum class ArticleSearchOrder(val title: String, val apiValue: String) {
  COMPREHENSIVE("综合排序", "totalrank"),
  NEWEST("最新发布", "pubdate"),
  MOST_CLICKED("最多点击", "click"),
  MOST_LIKED("最多喜欢", "attention"),
  MOST_COMMENTED("最多评论", "scores"),
}

/**
 * 搜索模块的完整 UI 状态（不可变快照，配合 StateFlow 使用）。
 *
 * 字段按用途分组：查询与联想、热搜与历史、各分类结果、当前分类与排序、分页与加载标志。
 */
data class SearchUiState(
  val query: String = "",
  val history: List<String> = emptyList(),
  val hot: List<HotSearchItem> = emptyList(),
  val suggestions: List<String> = emptyList(),
  val suggestionsLoading: Boolean = false,
  val results: List<FeedItem> = emptyList(),
  val bangumiResults: List<SpaceContentCard> = emptyList(),
  val liveRooms: List<LiveSearchRoom> = emptyList(),
  val articles: List<ArticleItem> = emptyList(),
  val users: List<SearchUser> = emptyList(),
  val category: SearchCategory = SearchCategory.VIDEO,
  val order: SearchOrder = SearchOrder.COMPREHENSIVE,
  val articleOrder: ArticleSearchOrder = ArticleSearchOrder.COMPREHENSIVE,
  val submittedQuery: String = "",
  val page: Int = 0,
  val hasMore: Boolean = false,
  val loading: Boolean = false,
  val loadingMore: Boolean = false,
  val searched: Boolean = false,
  val error: String? = null,
)

/**
 * 搜索入口的 ViewModel：驱动热搜、联想词与各分类搜索，并管理搜索历史持久化。
 *
 * 关键设计：
 *  - 状态集中在 [_state]，通过 [state] 以 StateFlow 对外只读暴露；
 *  - [searchGeneration]/[suggestionGeneration] 用于取消过期请求（见各方法的代数校验）；
 *  - 搜索历史以控制字符 U+001F 拼接后存入 SharedPreferences。
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {
  private val prefs = application.getSharedPreferences("search_history", 0)
  private val _state = MutableStateFlow(SearchUiState(history = readHistory()))
  val state: StateFlow<SearchUiState> = _state.asStateFlow()
  private var searchJob: Job? = null
  private var suggestionJob: Job? = null
  private var searchGeneration = 0L
  private var suggestionGeneration = 0L

  /** 打开搜索入口时复位状态，并在需要时懒加载热搜、恢复已输入关键词的联想词。 */
  fun open() {
    _state.value =
      _state.value.copy(
        history = readHistory(),
        searched = false,
        results = emptyList(),
        bangumiResults = emptyList(),
        liveRooms = emptyList(),
        articles = emptyList(),
        users = emptyList(),
        submittedQuery = "",
        page = 0,
        hasMore = false,
        loading = false,
        loadingMore = false,
        error = null,
      )
    // ── 热搜仅首次懒加载一次，之后复用缓存，避免每次打开都重复请求 ─────────────
    if (_state.value.hot.isEmpty())
      viewModelScope.launch {
        val hot =
          withContext(Dispatchers.IO) {
            runCatching { BiliSearchApi.getHotSearch() }.getOrDefault(emptyList())
          }
        _state.value = _state.value.copy(hot = hot)
      }
    // 若已有关键词（例如从上一页返回），立即重新拉取其联想词
    if (_state.value.query.isNotBlank()) setQuery(_state.value.query)
  }

  /**
   * 更新查询词并触发联想词请求（带 180ms 防抖）。
   *
   * 通过 [suggestionGeneration] 作废旧请求，只有仍是最新一代且关键词未变时才回填结果。
   */
  fun setQuery(value: String) {
    val normalized = value.take(80)
    val generation = ++suggestionGeneration
    suggestionJob?.cancel()
    // 空白关键词直接清空联想，不发起网络请求
    if (normalized.isBlank()) {
      _state.value =
        _state.value.copy(query = normalized, suggestions = emptyList(), suggestionsLoading = false)
      return
    }
    _state.value =
      _state.value.copy(query = normalized, suggestions = emptyList(), suggestionsLoading = true)
    suggestionJob = viewModelScope.launch {
      // 轻微防抖，避免输入过程中频繁请求
      delay(180)
      val values =
        withContext(Dispatchers.IO) {
          runCatching { BiliSearchApi.getSearchSuggestions(normalized) }.getOrDefault(emptyList())
        }
      // 校验代数与关键词，丢弃已被后续输入取代的过期结果
      if (generation == suggestionGeneration && _state.value.query == normalized) {
        _state.value = _state.value.copy(suggestions = values, suggestionsLoading = false)
      }
    }
  }

  /** 清空搜索入口：取消进行中的联想请求并复位查询词与联想结果。 */
  fun clearEntry() {
    suggestionJob?.cancel()
    suggestionGeneration++
    _state.value =
      _state.value.copy(
        query = "",
        suggestions = emptyList(),
        suggestionsLoading = false,
      )
  }

  /** 切换搜索分类：若分类可用且确有变化，则以当前关键词重新发起搜索。 */
  fun selectCategory(category: SearchCategory) {
    if (!category.enabled || category == _state.value.category) return
    _state.value = _state.value.copy(category = category)
    val keyword = _state.value.submittedQuery.ifBlank { _state.value.query }
    if (keyword.isNotBlank()) search(keyword)
  }

  /** 切换视频分类下的排序方式，切换后立即用当前关键词重新搜索。 */
  fun selectOrder(order: SearchOrder) {
    val current = _state.value
    if (
      order == current.order ||
        current.category != SearchCategory.VIDEO
    )
      return
    _state.value = current.copy(order = order)
    val keyword = current.submittedQuery.ifBlank { current.query }
    if (keyword.isNotBlank()) search(keyword)
  }

  /** 切换专栏分类下的排序方式，切换后立即用当前关键词重新搜索。 */
  fun selectArticleOrder(order: ArticleSearchOrder) {
    val current = _state.value
    if (current.category != SearchCategory.ARTICLE || order == current.articleOrder) return
    _state.value = current.copy(articleOrder = order)
    val keyword = current.submittedQuery.ifBlank { current.query }
    if (keyword.isNotBlank()) search(keyword)
  }

  /**
   * 发起一次新搜索：保存历史、复位结果并加载第 1 页。
   *
   * 通过递增 [searchGeneration] 作废所有进行中的搜索与联想请求，确保结果只属于本次搜索。
   */
  fun search(value: String = _state.value.query) {
    val keyword = value.trim()
    if (keyword.isEmpty()) return
    saveHistory(keyword)
    // 新一代代数：用于在结果回填时淘汰旧请求
    val generation = ++searchGeneration
    searchJob?.cancel()
    suggestionJob?.cancel()
    suggestionGeneration++
    _state.value =
      _state.value.copy(
        query = keyword,
        submittedQuery = keyword,
        results = emptyList(),
        bangumiResults = emptyList(),
        liveRooms = emptyList(),
        articles = emptyList(),
        users = emptyList(),
        page = 0,
        hasMore = false,
        loading = true,
        loadingMore = false,
        searched = true,
        error = null,
        suggestions = emptyList(),
        suggestionsLoading = false,
      )
    loadPage(
      keyword,
      page = 1,
      generation = generation,
      append = false,
      category = _state.value.category,
      order = _state.value.order,
      articleOrder = _state.value.articleOrder,
    )
  }

  /** 加载下一页结果；仅在已有提交关键词且不在加载中、仍有更多数据时生效。 */
  fun loadNextPage() {
    val current = _state.value
    if (
      current.submittedQuery.isBlank() || current.loading || current.loadingMore || !current.hasMore
    )
      return
    loadPage(
      keyword = current.submittedQuery,
      page = current.page + 1,
      generation = searchGeneration,
      append = true,
      category = current.category,
      order = current.order,
      articleOrder = current.articleOrder,
    )
  }

  /** 出错后重试：用最近提交的关键词重新发起搜索。 */
  fun retry() {
    val keyword = _state.value.submittedQuery.ifBlank { _state.value.query }.trim()
    if (keyword.isNotEmpty()) search(keyword)
  }

  /**
   * 请求指定分类的第 [page] 页结果并合并进状态。
   *
   * @param append 是否以“加载更多”方式追加（true 时不清空既有结果，并按 id 去重）；
   * @param generation 发起搜索时的代数，回填前用于校验请求是否已过期。
   */
  private fun loadPage(
    keyword: String,
    page: Int,
    generation: Long,
    append: Boolean,
    category: SearchCategory,
    order: SearchOrder,
    articleOrder: ArticleSearchOrder,
  ) {
    if (append) _state.value = _state.value.copy(loadingMore = true, error = null)
    searchJob = viewModelScope.launch {
      try {
        // ── 按分类只拉取当前所需的数据源，其余保持空 ───────────────────────────
        val users =
          if (category == SearchCategory.USER)
            withContext(Dispatchers.IO) { BiliSearchApi.searchUsers(keyword, page) }
          else emptyList()
        val cards =
          if (category == SearchCategory.VIDEO)
            withContext(Dispatchers.IO) {
              BiliSearchApi.searchVideos(keyword, page, order.apiValue).cards
            }
          else emptyList()
        val bangumiResponse =
          when (category) {
            SearchCategory.BANGUMI ->
              withContext(Dispatchers.IO) {
                BiliSearchApi.searchBangumi(keyword, page, SpaceContentKind.BANGUMI)
              }
            SearchCategory.CINEMA ->
              withContext(Dispatchers.IO) {
                BiliSearchApi.searchBangumi(keyword, page, SpaceContentKind.DRAMA)
              }
            else -> null
          }
        val liveResponse =
          if (category == SearchCategory.LIVE)
            withContext(Dispatchers.IO) { BiliLiveApi.searchRooms(keyword, page) }
          else null
        val articleResponse =
          if (category == SearchCategory.ARTICLE)
            withContext(Dispatchers.IO) {
              BiliSearchApi.searchArticles(keyword, page, articleOrder.apiValue)
            }
          else null
        // 把视频卡片映射为 FeedItem，统一交给结果网格渲染
        val items = cards.map { card ->
          FeedItem(
            card.bvid.ifBlank { card.aid.toString() },
            card.title,
            "https://www.bilibili.com/video/${card.bvid.ifBlank { "av${card.aid}" }}",
            card.coverUrl,
            card.uploaderName,
            FeedViewModel.formatCount(card.playCount),
            FeedViewModel.formatDuration(card.durationSeconds),
            card.uploaderFace,
            card.uploaderMid,
            card.danmakuCount,
            card.pubdate,
            card.description,
          )
        }
        // 三重校验：代数、关键词、分类都一致才允许回填，否则丢弃过期结果
        if (
          generation != searchGeneration ||
            _state.value.submittedQuery != keyword ||
            _state.value.category != category
        )
          return@launch
        // 按 append 语义合并（追加时去重），并更新分页与加载标志
        _state.value =
          _state.value.copy(
            results = if (append) (_state.value.results + items).distinctBy { it.id } else items,
            bangumiResults =
              if (append)
                (_state.value.bangumiResults + bangumiResponse?.cards.orEmpty()).distinctBy {
                  it.id
                }
              else bangumiResponse?.cards.orEmpty(),
            liveRooms =
              if (append)
                (_state.value.liveRooms + liveResponse?.rooms.orEmpty()).distinctBy { it.roomId }
              else liveResponse?.rooms.orEmpty(),
            articles =
              if (append)
                (_state.value.articles + articleResponse.orEmptyItems()).distinctBy { it.id }
              else articleResponse.orEmptyItems(),
            users = if (append) (_state.value.users + users).distinctBy { it.mid } else users,
            page = page,
            hasMore =
              when (category) {
                SearchCategory.USER -> users.size >= 20
                SearchCategory.ARTICLE -> articleResponse?.hasMore == true
                SearchCategory.BANGUMI,
                SearchCategory.CINEMA -> bangumiResponse?.hasMore == true
                SearchCategory.LIVE -> liveResponse?.hasMore == true
                else -> cards.size >= 20
              },
            loading = false,
            loadingMore = false,
            history = readHistory(),
          )
      } catch (error: Exception) {
        // 协程取消必须继续向上抛出，不能当作普通失败吞掉
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (generation != searchGeneration || _state.value.submittedQuery != keyword) return@launch
        _state.value =
          _state.value.copy(
            loading = false,
            loadingMore = false,
            error = error.message ?: "搜索失败",
          )
      }
    }
  }

  /** 清空本地保存的搜索历史。 */
  fun clearHistory() {
    prefs.edit().remove("items").apply()
    _state.value = _state.value.copy(history = emptyList())
  }

  /** 从 SharedPreferences 读取历史：以控制字符 U+001F 分隔，过滤空项。 */
  private fun readHistory(): List<String> =
    prefs.getString("items", "").orEmpty().split('\u001f').filter(String::isNotBlank)

  /** 保存搜索词到历史头部，去除重复并最多保留 12 条。 */
  private fun saveHistory(value: String) {
    val next = (listOf(value) + readHistory().filterNot { it == value }).take(12)
    prefs.edit().putString("items", next.joinToString("\u001f")).apply()
  }

  /** 扩展函数：把可空的专栏搜索响应安全地转换为（可能为空的）结果列表。 */
  private fun dev.openbili.webdemo.api.ArticleSearchResponse?.orEmptyItems(): List<ArticleItem> =
    this?.items.orEmpty()
}

package dev.openbili.webdemo.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.HotSearchItem
import dev.openbili.webdemo.api.SearchUser
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.live.BiliLiveApi
import dev.openbili.webdemo.live.LiveSearchRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchCategory(val title: String, val enabled: Boolean) {
  COMPREHENSIVE("综合", true),
  VIDEO("视频", true),
  BANGUMI("番剧", true),
  CINEMA("影视", true),
  LIVE("直播", true),
  ARTICLE("专栏", true),
  USER("用户", true),
}

enum class SearchOrder(val title: String, val apiValue: String) {
  COMPREHENSIVE("综合排序", "totalrank"),
  MOST_PLAYED("最多播放", "click"),
  NEWEST("最新发布", "pubdate"),
  MOST_DANMAKU("最多弹幕", "dm"),
  MOST_FAVORITED("最多收藏", "stow"),
}

enum class ArticleSearchOrder(val title: String, val apiValue: String) {
  COMPREHENSIVE("综合排序", "totalrank"),
  NEWEST("最新发布", "pubdate"),
  MOST_CLICKED("最多点击", "click"),
  MOST_LIKED("最多喜欢", "attention"),
  MOST_COMMENTED("最多评论", "scores"),
}

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
  val category: SearchCategory = SearchCategory.COMPREHENSIVE,
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

class SearchViewModel(application: Application) : AndroidViewModel(application) {
  private val prefs = application.getSharedPreferences("search_history", 0)
  private val _state = MutableStateFlow(SearchUiState(history = readHistory()))
  val state: StateFlow<SearchUiState> = _state.asStateFlow()
  private var searchJob: Job? = null
  private var suggestionJob: Job? = null
  private var searchGeneration = 0L
  private var suggestionGeneration = 0L

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
    if (_state.value.hot.isEmpty())
      viewModelScope.launch {
        val hot =
          withContext(Dispatchers.IO) {
            runCatching { BiliApi.getHotSearch() }.getOrDefault(emptyList())
          }
        _state.value = _state.value.copy(hot = hot)
      }
    if (_state.value.query.isNotBlank()) setQuery(_state.value.query)
  }

  fun setQuery(value: String) {
    val normalized = value.take(80)
    val generation = ++suggestionGeneration
    suggestionJob?.cancel()
    if (normalized.isBlank()) {
      _state.value =
        _state.value.copy(query = normalized, suggestions = emptyList(), suggestionsLoading = false)
      return
    }
    _state.value =
      _state.value.copy(query = normalized, suggestions = emptyList(), suggestionsLoading = true)
    suggestionJob = viewModelScope.launch {
      delay(180)
      val values =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.getSearchSuggestions(normalized) }.getOrDefault(emptyList())
        }
      if (generation == suggestionGeneration && _state.value.query == normalized) {
        _state.value = _state.value.copy(suggestions = values, suggestionsLoading = false)
      }
    }
  }

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

  fun selectCategory(category: SearchCategory) {
    if (!category.enabled || category == _state.value.category) return
    _state.value = _state.value.copy(category = category)
    val keyword = _state.value.submittedQuery.ifBlank { _state.value.query }
    if (keyword.isNotBlank()) search(keyword)
  }

  fun selectOrder(order: SearchOrder) {
    val current = _state.value
    if (
      order == current.order ||
        current.category !in setOf(SearchCategory.COMPREHENSIVE, SearchCategory.VIDEO)
    )
      return
    _state.value = current.copy(order = order)
    val keyword = current.submittedQuery.ifBlank { current.query }
    if (keyword.isNotBlank()) search(keyword)
  }

  fun selectArticleOrder(order: ArticleSearchOrder) {
    val current = _state.value
    if (current.category != SearchCategory.ARTICLE || order == current.articleOrder) return
    _state.value = current.copy(articleOrder = order)
    val keyword = current.submittedQuery.ifBlank { current.query }
    if (keyword.isNotBlank()) search(keyword)
  }

  fun search(value: String = _state.value.query) {
    val keyword = value.trim()
    if (keyword.isEmpty()) return
    saveHistory(keyword)
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

  fun retry() {
    val keyword = _state.value.submittedQuery.ifBlank { _state.value.query }.trim()
    if (keyword.isNotEmpty()) search(keyword)
  }

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
        val users =
          if (category == SearchCategory.USER)
            withContext(Dispatchers.IO) { BiliApi.searchUsers(keyword, page) }
          else emptyList()
        val cards =
          if (category == SearchCategory.COMPREHENSIVE || category == SearchCategory.VIDEO)
            withContext(Dispatchers.IO) {
              BiliApi.searchVideos(keyword, page, order.apiValue).cards
            }
          else emptyList()
        val bangumiResponse =
          when (category) {
            SearchCategory.BANGUMI ->
              withContext(Dispatchers.IO) {
                BiliApi.searchBangumi(keyword, page, SpaceContentKind.BANGUMI)
              }
            SearchCategory.CINEMA ->
              withContext(Dispatchers.IO) {
                BiliApi.searchBangumi(keyword, page, SpaceContentKind.DRAMA)
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
              BiliApi.searchArticles(keyword, page, articleOrder.apiValue)
            }
          else null
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
        if (
          generation != searchGeneration ||
            _state.value.submittedQuery != keyword ||
            _state.value.category != category
        )
          return@launch
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

  fun clearHistory() {
    prefs.edit().remove("items").apply()
    _state.value = _state.value.copy(history = emptyList())
  }

  private fun readHistory(): List<String> =
    prefs.getString("items", "").orEmpty().split('\u001f').filter(String::isNotBlank)

  private fun saveHistory(value: String) {
    val next = (listOf(value) + readHistory().filterNot { it == value }).take(12)
    prefs.edit().putString("items", next.joinToString("\u001f")).apply()
  }

  private fun dev.openbili.webdemo.api.ArticleSearchResponse?.orEmptyItems(): List<ArticleItem> =
    this?.items.orEmpty()
}

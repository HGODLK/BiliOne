package dev.openbili.webdemo.bangumi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.api.BangumiExploreCategory
import dev.openbili.webdemo.api.BangumiIndexItem
import dev.openbili.webdemo.api.BangumiIndexOrder
import dev.openbili.webdemo.api.BangumiIndexQuery
import dev.openbili.webdemo.api.BiliApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BangumiIndexUiState(
  val category: BangumiExploreCategory = BangumiExploreCategory.ANIME,
  val query: BangumiIndexQuery = BangumiIndexQuery(),
  /** The current server-search text. Blank restores the retained index result immediately. */
  val keyword: String = "",
  val items: List<BangumiIndexItem> = emptyList(),
  val total: Int = 0,
  val nextPage: Int = 1,
  val hasNext: Boolean = true,
  val initialLoading: Boolean = false,
  val loadingMore: Boolean = false,
  val error: String? = null,
  val searchItems: List<BangumiIndexItem> = emptyList(),
  val searchNextPage: Int = 1,
  val searchHasNext: Boolean = true,
  val searchInitialLoading: Boolean = false,
  val searchLoadingMore: Boolean = false,
  val searchError: String? = null,
) {
  val searching: Boolean get() = keyword.trim().isNotEmpty()
  val visibleItems: List<BangumiIndexItem> get() = if (searching) searchItems else items
  val visibleTotal: Int get() = if (searching) searchItems.size else total
  val visibleHasNext: Boolean get() = if (searching) searchHasNext else hasNext
  val visibleInitialLoading: Boolean get() = if (searching) searchInitialLoading else initialLoading
  val visibleLoadingMore: Boolean get() = if (searching) searchLoadingMore else loadingMore
  val visibleError: String? get() = if (searching) searchError else error
}

/**
 * Browsing and keyword search intentionally retain independent state. Clearing a keyword returns
 * to the exact loaded index list instead of filtering its first page locally.
 */
class BangumiIndexViewModel : ViewModel() {
  private val _state = MutableStateFlow(BangumiIndexUiState())
  val state: StateFlow<BangumiIndexUiState> = _state.asStateFlow()
  private var browseRequest: Job? = null
  private var searchRequest: Job? = null
  private var keywordDebounce: Job? = null
  private var browseGeneration = 0L
  private var searchGeneration = 0L

  fun ensureLoaded() {
    val current = _state.value
    if (!current.searching && current.items.isEmpty() && !current.initialLoading) refresh()
  }

  fun retry() {
    if (_state.value.searching) restartSearch(_state.value.keyword) else refresh()
  }

  fun openCategory(category: BangumiExploreCategory) {
    if (_state.value.category == category) return
    browseGeneration += 1L
    searchGeneration += 1L
    browseRequest?.cancel()
    searchRequest?.cancel()
    keywordDebounce?.cancel()
    _state.value =
      BangumiIndexUiState(
        category = category,
        query = BangumiIndexQuery(order = BiliApi.bangumiIndexDefaultOrder(category)),
        initialLoading = true,
      )
    loadBrowsePage(page = 1, generation = browseGeneration, replace = true)
  }

  fun reset() =
    updateQuery { BangumiIndexQuery(order = BiliApi.bangumiIndexDefaultOrder(_state.value.category)) }

  fun setKeyword(keyword: String) {
    if (keyword == _state.value.keyword) return
    val normalized = keyword.trim()
    keywordDebounce?.cancel()
    searchRequest?.cancel()
    searchGeneration += 1L
    _state.update {
      it.copy(
        keyword = keyword,
        searchItems = if (normalized.isEmpty()) it.searchItems else emptyList(),
        searchNextPage = 1,
        searchHasNext = true,
        searchInitialLoading = normalized.isNotEmpty(),
        searchLoadingMore = false,
        searchError = null,
      )
    }
    if (normalized.isEmpty()) return
    val generation = searchGeneration
    val category = _state.value.category
    keywordDebounce =
      viewModelScope.launch {
        delay(320)
        if (generation == searchGeneration && _state.value.category == category) {
          loadSearchPage(normalized, page = 1, generation = generation, replace = true)
        }
      }
  }

  fun selectOrder(order: BangumiIndexOrder) {
    updateQuery {
      if (it.order == order) it.copy(sortDescending = !it.sortDescending)
      else it.copy(order = order, sortDescending = true)
    }
  }

  fun toggleSortDirection() = updateQuery { it.copy(sortDescending = !it.sortDescending) }

  fun updateQuery(transform: (BangumiIndexQuery) -> BangumiIndexQuery) {
    val current = _state.value
    val next = transform(current.query)
    if (next == current.query) return
    browseGeneration += 1L
    browseRequest?.cancel()
    _state.update {
      it.copy(
        query = next,
        items = emptyList(),
        total = 0,
        nextPage = 1,
        hasNext = true,
        initialLoading = true,
        loadingMore = false,
        error = null,
      )
    }
    loadBrowsePage(page = 1, generation = browseGeneration, replace = true)
  }

  fun refresh() {
    val current = _state.value
    browseGeneration += 1L
    browseRequest?.cancel()
    _state.update {
      it.copy(
        items = emptyList(),
        total = 0,
        nextPage = 1,
        hasNext = true,
        initialLoading = true,
        loadingMore = false,
        error = null,
      )
    }
    loadBrowsePage(page = 1, generation = browseGeneration, replace = true)
  }

  fun loadNextPage() {
    val current = _state.value
    if (current.searching) {
      val keyword = current.keyword.trim()
      if (keyword.isBlank() || !current.searchHasNext || current.searchInitialLoading || current.searchLoadingMore) return
      loadSearchPage(keyword, current.searchNextPage, searchGeneration, replace = false)
    } else {
      if (!current.hasNext || current.initialLoading || current.loadingMore) return
      loadBrowsePage(current.nextPage, browseGeneration, replace = false)
    }
  }

  private fun restartSearch(value: String) {
    val keyword = value.trim()
    if (keyword.isBlank()) return
    searchGeneration += 1L
    searchRequest?.cancel()
    _state.update {
      it.copy(
        searchItems = emptyList(),
        searchNextPage = 1,
        searchHasNext = true,
        searchInitialLoading = true,
        searchLoadingMore = false,
        searchError = null,
      )
    }
    loadSearchPage(keyword, page = 1, generation = searchGeneration, replace = true)
  }

  private fun loadBrowsePage(page: Int, generation: Long, replace: Boolean) {
    val snapshot = _state.value
    val query = snapshot.query
    val category = snapshot.category
    if (!replace) _state.update { it.copy(loadingMore = true, error = null) }
    browseRequest =
      viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { BiliApi.getBangumiIndex(query, category, page) } }
        if (generation != browseGeneration || query != _state.value.query || category != _state.value.category) return@launch
        result.onSuccess { response ->
          _state.update { current ->
            val merged = if (replace) response.items else (current.items + response.items).distinctBy(::identity)
            current.copy(
              items = merged,
              total = response.total,
              nextPage = response.page + 1,
              hasNext = response.hasNext,
              initialLoading = false,
              loadingMore = false,
              error = null,
            )
          }
        }.onFailure { error ->
          _state.update { it.copy(initialLoading = false, loadingMore = false, error = error.message ?: "${category.label}索引加载失败，请重试") }
        }
      }
  }

  private fun loadSearchPage(keyword: String, page: Int, generation: Long, replace: Boolean) {
    val category = _state.value.category
    if (!replace) _state.update { it.copy(searchLoadingMore = true, searchError = null) }
    searchRequest =
      viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { BiliApi.searchBangumiIndex(keyword, category, page) } }
        if (generation != searchGeneration || _state.value.keyword.trim() != keyword || _state.value.category != category) return@launch
        result.onSuccess { response ->
          _state.update { current ->
            val merged = if (replace) response.items else (current.searchItems + response.items).distinctBy(::identity)
            current.copy(
              searchItems = merged,
              searchNextPage = response.page + 1,
              searchHasNext = response.hasNext,
              searchInitialLoading = false,
              searchLoadingMore = false,
              searchError = null,
            )
          }
        }.onFailure { error ->
          _state.update { it.copy(searchInitialLoading = false, searchLoadingMore = false, searchError = error.message ?: "搜索${category.label}失败，请重试") }
        }
      }
  }

  private fun identity(item: BangumiIndexItem): Long = item.seasonId.takeIf { it > 0L } ?: item.episodeId

  override fun onCleared() {
    browseRequest?.cancel()
    searchRequest?.cancel()
    keywordDebounce?.cancel()
  }
}

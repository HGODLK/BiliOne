package dev.openbili.webdemo.bangumi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.api.BangumiRecommendation
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.BiliBangumiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BangumiRecommendationUiState(
  val items: List<BangumiRecommendation> = emptyList(),
  val seasons: Map<String, BangumiSeason> = emptyMap(),
  val detailErrors: Map<String, String> = emptyMap(),
  val selectedId: String? = null,
  val initialLoading: Boolean = false,
  val refreshing: Boolean = false,
  val initialError: String? = null,
  val refreshMessage: String? = null,
  val generation: Long = 0L,
)

class BangumiRecommendationViewModel : ViewModel() {

  private val _state = MutableStateFlow(BangumiRecommendationUiState())
  val state: StateFlow<BangumiRecommendationUiState> = _state.asStateFlow()

  private var generation = 0L
  private val detailJobs = mutableMapOf<String, Job>()
  private var pendingItems: List<BangumiRecommendation>? = null
  private var interactionActive = false

  fun ensureLoaded() {
    val current = _state.value
    if (current.items.isNotEmpty() || current.initialLoading) return
    _state.update { it.copy(initialLoading = true, initialError = null) }
    loadCarousel(refresh = false)
  }

  fun refresh() {
    _state.update { it.copy(refreshing = true, refreshMessage = null) }
    loadCarousel(refresh = true)
  }

  fun select(stableId: String) {
    _state.update { it.copy(selectedId = stableId) }
    ensureAdjacentDetails()
  }

  fun ensureDetails(ids: List<String>) {
    ids.forEach { id -> loadDetailIfNeeded(id) }
  }

  fun retryDetail(stableId: String) {
    _state.update { it.copy(detailErrors = it.detailErrors - stableId) }
    loadDetailIfNeeded(stableId)
  }

  fun consumeRefreshMessage() {
    _state.update { it.copy(refreshMessage = null) }
  }

  fun setInteractionActive(active: Boolean) {
    interactionActive = active
    if (!active) {
      val pending = pendingItems
      if (pending != null) {
        pendingItems = null
        replaceItems(pending)
      }
    }
  }

  private fun loadCarousel(refresh: Boolean) {
    val requestGen = ++generation
    _state.update { it.copy(generation = requestGen) }
    viewModelScope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching { BiliBangumiApi.getBangumiRecommendations() }
        }
      if (requestGen != generation) return@launch
      result
        .onSuccess { items ->
          if (items.isEmpty()) {
            if (refresh) {
              _state.update {
                it.copy(
                  refreshing = false,
                  refreshMessage = "暂无新推荐，已保留当前内容",
                )
              }
            } else {
              _state.update {
                it.copy(initialLoading = false, initialError = "本期推荐加载失败")
              }
            }
            return@launch
          }
          if (interactionActive && refresh) {
            pendingItems = items
            _state.update { it.copy(refreshing = false) }
          } else {
            replaceItems(items)
          }
        }
        .onFailure { error ->
          if (!refresh) {
            _state.update {
              it.copy(
                initialLoading = false,
                initialError = error.message ?: "本期推荐加载失败",
              )
            }
          } else {
            _state.update {
              it.copy(
                refreshing = false,
                refreshMessage = error.message ?: "刷新失败，已保留当前内容",
              )
            }
          }
        }
    }
  }

  private fun replaceItems(items: List<BangumiRecommendation>) {
    val current = _state.value
    val oldSelectedId = current.selectedId
    val oldItems = current.items
    val oldSeasons = current.seasons
    val oldErrors = current.detailErrors

    val selectedId =
      if (items.any { it.stableId == oldSelectedId }) {
        oldSelectedId
      } else {
        val oldIndex = oldItems.indexOfFirst { it.stableId == oldSelectedId }
        val newIndex = oldIndex.coerceIn(0, items.size - 1)
        items.getOrNull(newIndex)?.stableId ?: items.firstOrNull()?.stableId
      }

    val newSeasons = oldSeasons.filterKeys { id -> items.any { it.stableId == id } }
    val newErrors = oldErrors.filterKeys { id -> items.any { it.stableId == id } }

    _state.update {
      it.copy(
        items = items,
        seasons = newSeasons,
        detailErrors = newErrors,
        selectedId = selectedId,
        initialLoading = false,
        refreshing = false,
        initialError = null,
      )
    }

    ensureAllDetails()
  }

  private fun ensureAllDetails() {
    _state.value.items.filterNot(BangumiRecommendation::isLive).forEach { item ->
      loadDetailIfNeeded(item.stableId)
    }
  }

  private fun ensureAdjacentDetails() {
    val state = _state.value
    val items = state.items
    if (items.isEmpty()) return
    val selectedId = state.selectedId ?: items.first().stableId
    val selectedIndex = items.indexOfFirst { it.stableId == selectedId }
    if (selectedIndex < 0) return

    val idsToLoad = mutableListOf<String>()
    idsToLoad.add(selectedId)
    if (selectedIndex - 1 >= 0) idsToLoad.add(items[selectedIndex - 1].stableId)
    if (selectedIndex + 1 < items.size) idsToLoad.add(items[selectedIndex + 1].stableId)

    idsToLoad.forEach { id -> loadDetailIfNeeded(id) }
  }

  private fun loadDetailIfNeeded(stableId: String) {
    val current = _state.value
    if (stableId in current.seasons || stableId in current.detailErrors) return
    if (detailJobs[stableId]?.isActive == true) return

    val item = current.items.firstOrNull { it.stableId == stableId } ?: return
    if (item.isLive) return

    detailJobs[stableId] = viewModelScope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching {
            when {
              item.seasonId > 0L -> BiliBangumiApi.getBangumiSeason(seasonId = item.seasonId)
              item.episodeId > 0L -> BiliBangumiApi.getBangumiSeason(episodeId = item.episodeId)
              else -> throw IllegalStateException("缺少番剧标识")
            }
          }
        }
      result
        .onSuccess { season ->
          _state.update { it.copy(seasons = it.seasons + (stableId to season)) }
        }
        .onFailure { error ->
          _state.update {
            it.copy(detailErrors = it.detailErrors + (stableId to (error.message ?: "番剧资料加载失败")))
          }
        }
      detailJobs.remove(stableId)
    }
  }
}

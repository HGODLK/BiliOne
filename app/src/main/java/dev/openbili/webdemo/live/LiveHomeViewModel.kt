package dev.openbili.webdemo.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LiveFollowingUiState(
  val isLoggedIn: Boolean = false,
  val isLoading: Boolean = false,
  val rooms: List<LiveSearchRoom> = emptyList(),
  val error: String? = null,
)

sealed interface LiveHomeUiState {
  val isRefreshing: Boolean
    get() = false

  data object Loading : LiveHomeUiState

  data class Content(
    val areas: List<LiveAreaFilter>,
    val areaGroups: List<LiveAreaGroup> = emptyList(),
    val selectedArea: LiveAreaFilter,
    val rooms: List<LiveSearchRoom>,
    val heroRooms: List<LiveSearchRoom> = emptyList(),
    val selectedHeroRoomId: Long? = null,
    val heroLoading: Boolean = false,
    val heroError: String? = null,
    val following: LiveFollowingUiState = LiveFollowingUiState(),
    val isLoadingMore: Boolean = false,
    val isChangingArea: Boolean = false,
    override val isRefreshing: Boolean = false,
    val emptyMessage: String? = null,
    val loadError: String? = null,
    val refreshMessage: String? = null,
  ) : LiveHomeUiState
}

/** Live discovery feed shown as the third page inside HomeHubScreen. */
class LiveHomeViewModel : ViewModel() {
  private val recommendationArea = LiveAreaFilter(parentAreaId = 0, name = "推荐")
  private val _state = MutableStateFlow<LiveHomeUiState>(LiveHomeUiState.Loading)
  val state: StateFlow<LiveHomeUiState> = _state.asStateFlow()

  private var areas = listOf(recommendationArea)
  private var areaGroups = emptyList<LiveAreaGroup>()
  private var selectedArea = recommendationArea
  private var page = 1
  private var noMore = false
  private var generation = 0L
  private var started = false
  private var loadJob: Job? = null
  private var landingJob: Job? = null
  private var allRooms = mutableListOf<LiveSearchRoom>()
  private var heroRooms = emptyList<LiveSearchRoom>()
  private var selectedHeroRoomId: Long? = null
  private var heroLoading = false
  private var heroError: String? = null
  private var following = LiveFollowingUiState()

  /** Defers every live-home request until the page is actually visible. */
  fun ensureLoaded() {
    if (started) return
    started = true
    loadPage(refresh = false, expectedGeneration = generation)
    loadLandingSections()
  }

  fun selectArea(area: LiveAreaFilter) {
    if (area.stableId == selectedArea.stableId || !isSelectableArea(area)) return
    started = true
    generation++
    loadJob?.cancel()
    selectedArea = area
    page = 1
    noMore = false
    allRooms = mutableListOf()
    publish(
      content(
        rooms = emptyList(),
        isChangingArea = true,
      )
    )
    loadPage(refresh = false, expectedGeneration = generation)
  }

  fun selectHeroRoom(room: LiveSearchRoom) {
    if (heroRooms.none { it.stableId == room.stableId }) return
    selectedHeroRoomId = room.roomId
    updateContent { it.copy(selectedHeroRoomId = selectedHeroRoomId) }
  }

  fun refresh() {
    if (!started) {
      ensureLoaded()
      return
    }
    generation++
    loadJob?.cancel()
    page = 1
    noMore = false
    publish(
      content(
        rooms = allRooms.toList(),
        isRefreshing = allRooms.isNotEmpty(),
        isChangingArea = allRooms.isEmpty(),
      )
    )
    loadPage(refresh = true, expectedGeneration = generation)
    loadLandingSections()
  }

  fun loadNextPage() {
    if (noMore || loadJob?.isActive == true) return
    val current = _state.value as? LiveHomeUiState.Content ?: return
    if (current.isLoadingMore || current.isRefreshing || current.isChangingArea) return
    _state.value = current.copy(isLoadingMore = true, loadError = null)
    loadPage(refresh = false, expectedGeneration = generation)
  }

  fun consumeRefreshMessage() {
    val current = _state.value as? LiveHomeUiState.Content ?: return
    if (current.refreshMessage != null) _state.value = current.copy(refreshMessage = null)
  }

  private fun loadLandingSections() {
    landingJob?.cancel()
    heroLoading = true
    following = following.copy(isLoading = true, error = null)
    updateContent { it.copy(heroLoading = true, following = following) }
    landingJob = viewModelScope.launch {
      val heroResult = runCatching {
        withContext(Dispatchers.IO) { BiliLiveApi.getHomeRecommendations(limit = 5) }
      }
      if (heroResult.isSuccess) {
        heroRooms = heroResult.getOrDefault(emptyList())
        if (heroRooms.none { it.roomId == selectedHeroRoomId }) {
          selectedHeroRoomId = heroRooms.firstOrNull()?.roomId
        }
        heroError = null
      } else {
        heroError = heroResult.exceptionOrNull()?.message ?: "推荐直播暂时不可用"
      }
      val followingResult = runCatching {
        withContext(Dispatchers.IO) { BiliLiveApi.getFollowedLiveRooms() }
      }
      if (followingResult.isSuccess) {
        val result = followingResult.getOrThrow()
        following =
          LiveFollowingUiState(
            isLoggedIn = result.isLoggedIn,
            rooms = result.rooms,
          )
      } else {
        following =
          LiveFollowingUiState(
            isLoggedIn = true,
            error = followingResult.exceptionOrNull()?.message ?: "关注直播暂时不可用",
          )
      }
      heroLoading = false
      updateContent { it.copy(heroLoading = false, following = following) }
    }
  }

  private fun loadPage(refresh: Boolean, expectedGeneration: Long) {
    loadJob = viewModelScope.launch {
      try {
        val requestedPage = page
        if (requestedPage == 1 && areaGroups.isEmpty()) {
          val fetchedGroups =
            withContext(Dispatchers.IO) {
              runCatching(BiliLiveApi::getLiveAreaGroups).getOrDefault(emptyList())
            }
          if (expectedGeneration != generation) return@launch
          if (fetchedGroups.isNotEmpty()) {
            areaGroups = fetchedGroups
            areas = listOf(recommendationArea) + fetchedGroups.map { it.parent }
          }
        }
        val response =
          withContext(Dispatchers.IO) {
            BiliLiveApi.getLiveRooms(
              parentAreaId = selectedArea.parentAreaId,
              areaId = selectedArea.areaId,
              page = requestedPage,
            )
          }
        if (expectedGeneration != generation) return@launch
        if (response.rooms.isEmpty()) {
          noMore = true
          publish(
            if (refresh && allRooms.isNotEmpty()) {
              content(
                rooms = allRooms.toList(),
                refreshMessage = "刷新结果为空，已保留原内容",
              )
            } else {
              content(
                rooms = emptyList(),
                emptyMessage = "这个分区暂时没有正在直播的内容",
              )
            }
          )
          return@launch
        }
        if (refresh || requestedPage == 1) {
          allRooms = response.rooms.distinctBy { it.stableId }.toMutableList()
        } else {
          val existing = allRooms.mapTo(HashSet()) { it.stableId }
          allRooms.addAll(response.rooms.filter { existing.add(it.stableId) })
        }
        page = requestedPage + 1
        noMore = !response.hasMore
        publish(content(rooms = allRooms.toList()))
      } catch (error: Throwable) {
        if (error is CancellationException) throw error
        if (expectedGeneration != generation) return@launch
        publish(
          if (allRooms.isEmpty()) {
            content(
              rooms = emptyList(),
              loadError = error.message ?: "加载直播失败",
            )
          } else {
            content(
              rooms = allRooms.toList(),
              refreshMessage = "加载失败：${error.message ?: "请稍后重试"}",
            )
          }
        )
      }
    }
  }

  private fun isSelectableArea(area: LiveAreaFilter): Boolean =
    area.stableId == recommendationArea.stableId ||
      areas.any { it.stableId == area.stableId } ||
      areaGroups.any { group -> group.children.any { it.stableId == area.stableId } }

  private fun content(
    rooms: List<LiveSearchRoom> = allRooms.toList(),
    isLoadingMore: Boolean = false,
    isChangingArea: Boolean = false,
    isRefreshing: Boolean = false,
    emptyMessage: String? = null,
    loadError: String? = null,
    refreshMessage: String? = null,
  ) =
    LiveHomeUiState.Content(
      areas = areas,
      areaGroups = areaGroups,
      selectedArea = selectedArea,
      rooms = rooms,
      heroRooms = heroRooms,
      selectedHeroRoomId = selectedHeroRoomId,
      heroLoading = heroLoading,
      heroError = heroError,
      following = following,
      isLoadingMore = isLoadingMore,
      isChangingArea = isChangingArea,
      isRefreshing = isRefreshing,
      emptyMessage = emptyMessage,
      loadError = loadError,
      refreshMessage = refreshMessage,
    )

  private fun publish(content: LiveHomeUiState.Content) {
    _state.value =
      content.copy(
        areas = areas,
        areaGroups = areaGroups,
        selectedArea = selectedArea,
        heroRooms = heroRooms,
        selectedHeroRoomId = selectedHeroRoomId,
        heroLoading = heroLoading,
        heroError = heroError,
        following = following,
      )
  }

  private fun updateContent(transform: (LiveHomeUiState.Content) -> LiveHomeUiState.Content) {
    val current = _state.value as? LiveHomeUiState.Content ?: return
    publish(transform(current))
  }

  override fun onCleared() {
    loadJob?.cancel()
    landingJob?.cancel()
  }
}

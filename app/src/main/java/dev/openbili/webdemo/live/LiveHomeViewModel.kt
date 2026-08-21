package dev.openbili.webdemo.live

/**
 * 直播首页的状态持有器。
 *
 * 本文件定义直播发现页的全部 UI 状态（[LiveFollowingUiState]、[LiveHomeUiState]）与
 * 对应的 [LiveHomeViewModel]：负责主网格的翻页/刷新、分区切换、顶部推荐位与关注横栏
 * 的独立加载，并把"真相源"缓存在私有字段里，通过 [LiveHomeViewModel.state] 只读地发布
 * 给组合层。分页与切分区都靠 generation 令牌丢弃过期响应，避免竞态。
 */

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「我的关注」横向栏的 UI 状态。
 *
 * 与主网格分开加载（见 [LiveHomeViewModel.loadLandingSections]）；[isLoggedIn] 用来区分
 * 「未登录」「已登录但无直播」「加载失败」三种空态展示。
 */
data class LiveFollowingUiState(
  val isLoggedIn: Boolean = false,
  val isLoading: Boolean = false,
  val rooms: List<LiveSearchRoom> = emptyList(),
  val error: String? = null,
)

/**
 * 直播首页的组合状态：首屏加载中的 [Loading]，或携带完整数据的 [Content]。
 *
 * [isRefreshing] 在接口层给出默认值 false，让 [Loading] 这类空状态无需覆写；
 * 只有 [Content] 才真正持有刷新标志。
 */
sealed interface LiveHomeUiState {
  val isRefreshing: Boolean
    get() = false

  /** 首屏尚未加载完成时的占位状态。 */
  data object Loading : LiveHomeUiState

  /**
   * 直播首页的完整数据快照。
   *
   * [areas]/[areaGroups] 供分区索引页使用，[heroRooms] 是顶部推荐位，[following] 是
   * 关注横栏，[rooms] 是主网格。[isLoadingMore]/[isChangingArea]/[isRefreshing] 分别
   * 描述翻页、切分区、下拉刷新三种进行中状态，驱动互斥的 UI 分支。
   */
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

/** 直播发现流，作为 HomeHubScreen 中的第三个页面展示。 */
class LiveHomeViewModel : ViewModel() {
  // ── 私有缓存：状态"真相源"，StateFlow 只发布由它们拼装的快照 ─────────────────
  private val recommendationArea = LiveAreaFilter(parentAreaId = 0, name = "推荐")
  private val _state = MutableStateFlow<LiveHomeUiState>(LiveHomeUiState.Loading)
  // 组合层只读入口；写回一律走 publish/content，避免状态散落多处
  val state: StateFlow<LiveHomeUiState> = _state.asStateFlow()

  private var areas = listOf(recommendationArea)
  private var areaGroups = emptyList<LiveAreaGroup>()
  private var selectedArea = recommendationArea
  // 当前已请求到哪一页；refresh/切分区时重置为 1
  private var page = 1
  // 是否已拉取到最后一页，避免无意义的翻页请求
  private var noMore = false
  // 请求代次令牌：切分区/刷新时自增，用来丢弃仍在途的过期响应
  private var generation = 0L
  // 首次 ensureLoaded 后置位，保证"延迟到可见再加载"只触发一次
  private var started = false
  private var loadJob: Job? = null
  private var landingJob: Job? = null
  // 主网格累积房间；翻页时按 stableId 去重追加
  private var allRooms = mutableListOf<LiveSearchRoom>()
  private var heroRooms = emptyList<LiveSearchRoom>()
  private var selectedHeroRoomId: Long? = null
  private var heroLoading = false
  private var heroError: String? = null
  private var following = LiveFollowingUiState()

  /** 把直播首页的所有请求推迟到页面真正可见时才发出。 */
  fun ensureLoaded() {
    if (started) return
    started = true
    loadPage(refresh = false, expectedGeneration = generation)
    loadLandingSections()
  }

  fun selectArea(area: LiveAreaFilter) {
    if (area.stableId == selectedArea.stableId || !isSelectableArea(area)) return
    started = true
    // 自增代次并取消在途请求，使旧分区尚未返回的响应在 loadPage 里被识别为过期
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
    // 刷新同样使在途请求过期，并重置翻页游标
    generation++
    loadJob?.cancel()
    page = 1
    noMore = false
    // 有旧数据时显示下拉刷新指示器，无旧数据时按"切分区"的整页占位处理
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

  // 顶部推荐位与关注横栏和主网格独立：各自拉取、各自成败，互不阻塞
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
          // 切分区可能发生在 IO 返回前：代次不符即丢弃本次结果
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
        // 再次校验代次：网络返回与用户切分区都可能交错
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
          // 首屏/刷新：整批替换，并就地按 stableId 去重
          allRooms = response.rooms.distinctBy { it.stableId }.toMutableList()
        } else {
          // 翻页：只追加未见过的房间，existing.add 同时完成查重与登记
          val existing = allRooms.mapTo(HashSet()) { it.stableId }
          allRooms.addAll(response.rooms.filter { existing.add(it.stableId) })
        }
        page = requestedPage + 1
        noMore = !response.hasMore
        publish(content(rooms = allRooms.toList()))
      } catch (error: Throwable) {
        // 协程取消是正常的控制流，必须重新抛出，不得吞掉
        if (error is CancellationException) throw error
        // 过期请求的失败不再上报，避免覆盖新分区的状态
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

  // content() 按缓存字段构造快照；publish() 再强制用最新缓存覆盖一次，保证经
  // updateContent(transform) 改写后也不会把陈旧的 areas/heroRooms/following 写回状态
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

package dev.openbili.webdemo.dynamic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.HomeDynamicUploader
import dev.openbili.webdemo.api.HomeDynamicUploaderResponse
import dev.openbili.webdemo.api.SpaceDynamicItem
import dev.openbili.webdemo.api.SpaceDynamicResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeDynamicUiState(
  val uploaders: List<HomeDynamicUploader> = emptyList(),
  val uploaderFirstPageIds: Set<Long> = emptySet(),
  val uploaderOffset: String = "",
  val uploadersHaveMore: Boolean = false,
  val uploadersLoadingMore: Boolean = false,
  val selectedMid: Long? = null,
  val selectedDynamicId: String? = null,
  val videoOnly: Boolean = false,
  val items: List<SpaceDynamicItem> = emptyList(),
  val firstPageIds: Set<String> = emptySet(),
  val offset: String = "",
  val hasMore: Boolean = false,
  val loading: Boolean = false,
  val loadingMore: Boolean = false,
  val initialized: Boolean = false,
  val error: String? = null,
)

private data class HomeDynamicKey(val mid: Long?, val videoOnly: Boolean)

private data class HomeDynamicSnapshot(
  val items: List<SpaceDynamicItem>,
  val firstPageIds: Set<String>,
  val offset: String,
  val hasMore: Boolean,
)

private data class HomeDynamicPendingUpdate(
  val key: HomeDynamicKey,
  val response: SpaceDynamicResponse,
  val uploaderPage: HomeDynamicUploaderResponse?,
)

private data class HomeDynamicUploaderMerge(
  val items: List<HomeDynamicUploader>,
  val firstPageIds: Set<Long>,
  val offset: String,
  val hasMore: Boolean,
)

class HomeDynamicViewModel : ViewModel() {
  private val _state = MutableStateFlow(HomeDynamicUiState())
  val state: StateFlow<HomeDynamicUiState> = _state.asStateFlow()

  private val snapshots = mutableMapOf<HomeDynamicKey, HomeDynamicSnapshot>()
  private var loadJob: Job? = null
  private var uploaderLoadJob: Job? = null
  private var pollLoadJob: Job? = null
  private var pendingCommitJob: Job? = null
  private var pendingUpdate: HomeDynamicPendingUpdate? = null
  private val optimisticReadUploaderMids = mutableSetOf<Long>()
  private val sessionReadUploaderMids = mutableSetOf<Long>()
  private var latestPortalUploaderPage: HomeDynamicUploaderResponse? = null
  private var pageVisible = false
  private var hostCommitAllowed = false
  private var detailOverlayActive = false
  private var generation = 0L

  fun ensureLoaded(loggedIn: Boolean) {
    if (!loggedIn) {
      loadJob?.cancel()
      uploaderLoadJob?.cancel()
      pollLoadJob?.cancel()
      pendingCommitJob?.cancel()
      pendingUpdate = null
      optimisticReadUploaderMids.clear()
      sessionReadUploaderMids.clear()
      latestPortalUploaderPage = null
      snapshots.clear()
      generation++
      _state.value =
        HomeDynamicUiState(
          initialized = true,
          error = "登录后查看关注动态",
        )
      return
    }
    val current = _state.value
    if (
      !current.loading &&
        (!current.initialized || current.error == "登录后查看关注动态")
    ) load(reset = true, refreshPortal = true)
  }

  fun refresh() {
    load(reset = true, refreshPortal = true)
  }

  fun selectDynamic(dynamicId: String?) {
    _state.value = _state.value.copy(selectedDynamicId = dynamicId)
  }

  fun setHostCommitAllowed(allowed: Boolean) {
    hostCommitAllowed = allowed
    schedulePendingCommit()
  }

  fun setDetailOverlayActive(active: Boolean) {
    detailOverlayActive = active
    schedulePendingCommit()
  }

  fun setPageVisible(visible: Boolean) {
    if (pageVisible == visible) return
    pageVisible = visible
    if (!visible || sessionReadUploaderMids.isEmpty()) return

    val current = _state.value
    val selectedUploaderWasRead = current.selectedMid in sessionReadUploaderMids
    if (selectedUploaderWasRead) saveSnapshot(current)
    val restoredSnapshot =
      if (selectedUploaderWasRead) snapshots[HomeDynamicKey(null, current.videoOnly)] else null

    loadJob?.cancel()
    pollLoadJob?.cancel()
    pendingCommitJob?.cancel()
    pendingUpdate = null
    generation++
    sessionReadUploaderMids.clear()
    optimisticReadUploaderMids.clear()

    val baseState =
      if (selectedUploaderWasRead) {
        current.copy(
          selectedMid = null,
          selectedDynamicId = null,
          items = restoredSnapshot?.items.orEmpty(),
          firstPageIds = restoredSnapshot?.firstPageIds.orEmpty(),
          offset = restoredSnapshot?.offset.orEmpty(),
          hasMore = restoredSnapshot?.hasMore ?: false,
          loading = restoredSnapshot == null,
          loadingMore = false,
          error = null,
        )
      } else current
    val uploaderMerge =
      latestPortalUploaderPage?.let { page ->
        mergeUploaderFirstPage(baseState, page)
      }
    latestPortalUploaderPage = null
    _state.value =
      baseState.copy(
        uploaders = uploaderMerge?.items ?: baseState.uploaders,
        uploaderFirstPageIds = uploaderMerge?.firstPageIds ?: baseState.uploaderFirstPageIds,
        uploaderOffset = uploaderMerge?.offset ?: baseState.uploaderOffset,
        uploadersHaveMore = uploaderMerge?.hasMore ?: baseState.uploadersHaveMore,
      )

    if (selectedUploaderWasRead && restoredSnapshot == null) {
      load(reset = true, refreshPortal = true)
    } else {
      pollUpdates()
    }
  }

  fun pollUpdates() {
    val expected = _state.value
    if (!expected.initialized || expected.loading || pollLoadJob?.isActive == true) return
    val expectedKey = HomeDynamicKey(expected.selectedMid, expected.videoOnly)
    val expectedGeneration = generation
    pollLoadJob =
      viewModelScope.launch {
        try {
          val (uploaderPage, response) =
            coroutineScope {
              val uploaderRequest =
                async(Dispatchers.IO) {
                  runCatching { BiliApi.getHomeDynamicUploaders() }.getOrNull()
                }
              val pageRequest = async(Dispatchers.IO) { fetchPage(expected, reset = true) }
              uploaderRequest.await() to pageRequest.await()
            }
          val current = _state.value
          if (
            generation != expectedGeneration ||
              HomeDynamicKey(current.selectedMid, current.videoOnly) != expectedKey
          ) return@launch
          pendingUpdate = HomeDynamicPendingUpdate(expectedKey, response, uploaderPage)
          schedulePendingCommit()
        } catch (error: Exception) {
          if (error is kotlinx.coroutines.CancellationException) throw error
          // Polling is deliberately silent: keep the last valid UI and retry on the next tick.
        }
      }
  }

  fun selectUploader(mid: Long?) {
    val current = _state.value
    if (current.selectedMid == mid) return
    val selectedHasUpdate = current.uploaders.firstOrNull { it.mid == mid }?.hasUpdate == true
    if (selectedHasUpdate && mid != null) {
      optimisticReadUploaderMids += mid
      sessionReadUploaderMids += mid
      // Only a portal response fetched after this click may decide the next-entry order.
      latestPortalUploaderPage = null
    }
    saveSnapshot(current)
    val key = HomeDynamicKey(mid, current.videoOnly)
    val cached = snapshots[key]
    loadJob?.cancel()
    pendingUpdate = null
    pendingCommitJob?.cancel()
    generation++
    _state.value =
      current.copy(
        uploaders =
          current.uploaders.map { uploader ->
            if (uploader.mid == mid && selectedHasUpdate) uploader.copy(hasUpdate = false)
            else uploader
          },
        selectedMid = mid,
        selectedDynamicId = null,
        items = cached?.items.orEmpty(),
        firstPageIds = cached?.firstPageIds.orEmpty(),
        offset = cached?.offset.orEmpty(),
        hasMore = cached?.hasMore ?: false,
        loading = cached == null,
        loadingMore = false,
        error = null,
      )
    if (cached == null || selectedHasUpdate) {
      load(reset = true, refreshPortal = false, silent = cached != null)
    }
  }

  fun setVideoOnly(enabled: Boolean) {
    val current = _state.value
    if (current.videoOnly == enabled) return
    saveSnapshot(current)
    val key = HomeDynamicKey(current.selectedMid, enabled)
    val cached = snapshots[key]
    loadJob?.cancel()
    pendingUpdate = null
    pendingCommitJob?.cancel()
    generation++
    _state.value =
      current.copy(
        videoOnly = enabled,
        selectedDynamicId = null,
        items = cached?.items.orEmpty(),
        firstPageIds = cached?.firstPageIds.orEmpty(),
        offset = cached?.offset.orEmpty(),
        hasMore = cached?.hasMore ?: false,
        loading = cached == null,
        loadingMore = false,
        error = null,
      )
    if (cached == null) load(reset = true, refreshPortal = false)
  }

  fun loadMore() {
    val current = _state.value
    if (current.loading || current.loadingMore || !current.hasMore) return
    load(reset = false, refreshPortal = false)
  }

  fun loadMoreUploaders() {
    val current = _state.value
    if (
      current.uploadersLoadingMore ||
        !current.uploadersHaveMore ||
        current.uploaderOffset.isBlank()
    ) return
    _state.value = current.copy(uploadersLoadingMore = true)
    uploaderLoadJob?.cancel()
    uploaderLoadJob =
      viewModelScope.launch {
        try {
          val page =
            withContext(Dispatchers.IO) {
              BiliApi.getHomeDynamicUploaders(current.uploaderOffset)
            }
          _state.value =
            _state.value.copy(
              uploaders =
                (_state.value.uploaders + maskSessionReadUploaderUpdates(page.items))
                  .distinctBy(HomeDynamicUploader::mid),
              uploaderOffset = page.offset,
              uploadersHaveMore = page.hasMore,
              uploadersLoadingMore = false,
            )
        } catch (error: Exception) {
          if (error is kotlinx.coroutines.CancellationException) throw error
          _state.value =
            _state.value.copy(
              uploadersLoadingMore = false,
              error = error.message ?: "UP 主列表加载失败",
            )
        }
      }
  }

  fun toggleLike(item: SpaceDynamicItem, accountMid: Long) {
    if (accountMid <= 0L) {
      _state.value = _state.value.copy(error = "登录后才能点赞动态")
      return
    }
    val targetLiked = !item.liked
    val update: (SpaceDynamicItem) -> SpaceDynamicItem = { current ->
      if (current.id != item.id) current
      else
        current.copy(
          liked = targetLiked,
          likeCount = (current.likeCount + if (targetLiked) 1 else -1).coerceAtLeast(0L),
        )
    }
    val before = _state.value
    _state.value = before.copy(items = before.items.map(update), error = null)
    saveSnapshot(_state.value)
    viewModelScope.launch(Dispatchers.IO) {
      val failure = runCatching { BiliApi.setDynamicLike(item.id, targetLiked, accountMid) }.exceptionOrNull()
      if (failure != null) {
        withContext(Dispatchers.Main) {
          val current = _state.value
          _state.value =
            current.copy(
              items = current.items.map { row -> if (row.id == item.id) item else row },
              error = failure.message ?: "动态点赞失败",
            )
          saveSnapshot(_state.value)
        }
      }
    }
  }

  fun consumeError() {
    _state.value = _state.value.copy(error = null)
  }

  private fun load(reset: Boolean, refreshPortal: Boolean, silent: Boolean = false) {
    val expected = _state.value
    val expectedKey = HomeDynamicKey(expected.selectedMid, expected.videoOnly)
    val uploaderReadMid =
      expected.selectedMid?.takeIf { reset && it in optimisticReadUploaderMids }
    val requestGeneration = ++generation
    loadJob?.cancel()
    pollLoadJob?.cancel()
    pendingCommitJob?.cancel()
    pendingUpdate = null
    _state.value =
      expected.copy(
        loading = reset && !silent,
        loadingMore = !reset && !silent,
        error = null,
      )
    loadJob =
      viewModelScope.launch {
        try {
          val (baseUploaderPage, response) =
            coroutineScope {
              val uploaderRequest =
                if (refreshPortal) async(Dispatchers.IO) { BiliApi.getHomeDynamicUploaders() }
                else null
              val pageRequest = async(Dispatchers.IO) { fetchPage(expected, reset) }
              (uploaderRequest?.await()
                ?: HomeDynamicUploaderResponse(
                  items = expected.uploaders,
                  offset = expected.uploaderOffset,
                  hasMore = expected.uploadersHaveMore,
                )) to pageRequest.await()
            }
          val confirmedUploaderPage =
            uploaderReadMid?.let {
              withContext(Dispatchers.IO) {
                runCatching { BiliApi.getHomeDynamicUploaders() }.getOrNull()
              }
            }
          val portalRefreshed = refreshPortal || confirmedUploaderPage != null
          val uploaderPage = confirmedUploaderPage ?: baseUploaderPage
          if (requestGeneration != generation) return@launch
          val current = _state.value
          if (HomeDynamicKey(current.selectedMid, current.videoOnly) != expectedKey) return@launch
          if (portalRefreshed) latestPortalUploaderPage = uploaderPage
          val normalizedUploaderItems =
            if (portalRefreshed) reconcileFirstUploaderPage(uploaderPage.items)
            else maskSessionReadUploaderUpdates(uploaderPage.items)
          val uploaderMerge =
            if (portalRefreshed) {
              mergeUploaderFirstPage(
                current = current,
                page = uploaderPage.copy(items = normalizedUploaderItems),
              )
            } else null
          val merged =
            if (reset) response.items
            else (current.items + response.items).distinctBy(SpaceDynamicItem::id)
          _state.value =
            current.copy(
              uploaders = uploaderMerge?.items ?: normalizedUploaderItems,
              uploaderFirstPageIds =
                uploaderMerge?.firstPageIds ?: current.uploaderFirstPageIds,
              uploaderOffset = uploaderMerge?.offset ?: uploaderPage.offset,
              uploadersHaveMore = uploaderMerge?.hasMore ?: uploaderPage.hasMore,
              uploadersLoadingMore = false,
              items = merged,
              firstPageIds =
                if (reset) response.items.mapTo(linkedSetOf(), SpaceDynamicItem::id)
                else current.firstPageIds,
              offset = response.offset,
              hasMore = response.hasMore,
              loading = false,
              loadingMore = false,
              initialized = true,
            )
          saveSnapshot(_state.value)
        } catch (error: Exception) {
          if (error is kotlinx.coroutines.CancellationException) throw error
          if (requestGeneration != generation) return@launch
          uploaderReadMid?.let { mid ->
            val optimisticRemoved = optimisticReadUploaderMids.remove(mid)
            val sessionRemoved = sessionReadUploaderMids.remove(mid)
            if (optimisticRemoved || sessionRemoved) {
              _state.value =
                _state.value.copy(
                  uploaders =
                    _state.value.uploaders.map { uploader ->
                      if (uploader.mid == mid) uploader.copy(hasUpdate = true) else uploader
                    }
                )
            }
          }
          _state.value =
            _state.value.copy(
              loading = false,
              loadingMore = false,
              initialized = true,
              error = error.message ?: "动态加载失败",
            )
        }
      }
  }

  private suspend fun fetchPage(
    expected: HomeDynamicUiState,
    reset: Boolean,
  ): SpaceDynamicResponse {
    var response =
      if (expected.selectedMid == null) {
        BiliApi.getHomeDynamics(
          offset = if (reset) "" else expected.offset,
          videoOnly = expected.videoOnly,
        )
      } else {
        BiliApi.getHomeUploaderDynamics(
          mid = expected.selectedMid,
          offset = if (reset) "" else expected.offset,
        )
      }
    if (expected.selectedMid == null || !expected.videoOnly) return response

    var filtered = response.items.filter { it.video != null }
    var offset = response.offset
    var hasMore = response.hasMore
    var rounds = 0
    while (filtered.size < 8 && hasMore && offset.isNotBlank() && rounds < 2) {
      val next = BiliApi.getHomeUploaderDynamics(expected.selectedMid, offset)
      filtered = (filtered + next.items.filter { it.video != null }).distinctBy(SpaceDynamicItem::id)
      offset = next.offset
      hasMore = next.hasMore
      rounds++
    }
    return SpaceDynamicResponse(filtered, offset, hasMore)
  }

  private fun saveSnapshot(state: HomeDynamicUiState) {
    if (!state.initialized && state.items.isEmpty()) return
    snapshots[HomeDynamicKey(state.selectedMid, state.videoOnly)] =
      HomeDynamicSnapshot(state.items, state.firstPageIds, state.offset, state.hasMore)
  }

  private fun maskSessionReadUploaderUpdates(
    uploaders: List<HomeDynamicUploader>
  ): List<HomeDynamicUploader> =
    uploaders.map { uploader ->
      if (uploader.mid in sessionReadUploaderMids && uploader.hasUpdate) {
        uploader.copy(hasUpdate = false)
      } else uploader
    }

  private fun reconcileFirstUploaderPage(
    uploaders: List<HomeDynamicUploader>
  ): List<HomeDynamicUploader> {
    val rowsByMid = uploaders.associateBy(HomeDynamicUploader::mid)
    optimisticReadUploaderMids.removeAll { mid ->
      rowsByMid[mid]?.hasUpdate == false || mid !in rowsByMid
    }
    return maskSessionReadUploaderUpdates(uploaders)
  }

  private fun mergeUploaderFirstPage(
    current: HomeDynamicUiState,
    page: HomeDynamicUploaderResponse,
  ): HomeDynamicUploaderMerge {
    val refreshedIds = page.items.mapTo(linkedSetOf(), HomeDynamicUploader::mid)
    val retainedTail =
      current.uploaders.filter { uploader ->
        uploader.mid !in current.uploaderFirstPageIds && uploader.mid !in refreshedIds
      }
    val selectedUploader =
      current.selectedMid
        ?.let { mid -> current.uploaders.firstOrNull { it.mid == mid } }
        ?.takeIf {
          it.mid !in refreshedIds && retainedTail.none { tail -> tail.mid == it.mid }
        }
    val refreshedItems =
      (page.items + listOfNotNull(selectedUploader) + retainedTail)
        .distinctBy(HomeDynamicUploader::mid)
    val items = preserveSessionReadUploaderPositions(current.uploaders, refreshedItems)
    val hadLoadedTail = retainedTail.isNotEmpty()
    return HomeDynamicUploaderMerge(
      items = items,
      firstPageIds = refreshedIds,
      offset = if (hadLoadedTail) current.uploaderOffset else page.offset,
      hasMore = if (hadLoadedTail) current.uploadersHaveMore else page.hasMore,
    )
  }

  private fun preserveSessionReadUploaderPositions(
    current: List<HomeDynamicUploader>,
    refreshed: List<HomeDynamicUploader>,
  ): List<HomeDynamicUploader> {
    if (sessionReadUploaderMids.isEmpty()) return refreshed
    val merged =
      refreshed.filterNot { it.mid in sessionReadUploaderMids }.toMutableList()
    current.withIndex()
      .filter { (_, uploader) -> uploader.mid in sessionReadUploaderMids }
      .forEach { (index, uploader) ->
        merged.add(index.coerceAtMost(merged.size), uploader.copy(hasUpdate = false))
      }
    return merged.distinctBy(HomeDynamicUploader::mid)
  }

  private fun schedulePendingCommit() {
    pendingCommitJob?.cancel()
    if (
      !hostCommitAllowed ||
        detailOverlayActive ||
        _state.value.selectedDynamicId != null ||
        pendingUpdate == null
    ) return
    pendingCommitJob =
      viewModelScope.launch {
        // Let the shared-cover/card morph commit its final frame before changing list order.
        delay(34)
        applyPendingUpdateIfAllowed()
      }
  }

  private fun applyPendingUpdateIfAllowed() {
    if (
      !hostCommitAllowed || detailOverlayActive || _state.value.selectedDynamicId != null
    ) return
    val pending = pendingUpdate ?: return
    val current = _state.value
    if (HomeDynamicKey(current.selectedMid, current.videoOnly) != pending.key) {
      pendingUpdate = null
      return
    }
    val refreshedIds = pending.response.items.mapTo(linkedSetOf(), SpaceDynamicItem::id)
    val retainedTail =
      current.items.filter { item ->
        item.id !in current.firstPageIds && item.id !in refreshedIds
      }
    val selectedItem =
      current.selectedDynamicId
        ?.let { id -> current.items.firstOrNull { it.id == id } }
        ?.takeIf { it.id !in refreshedIds && retainedTail.none { tail -> tail.id == it.id } }
    val mergedItems =
      (pending.response.items + listOfNotNull(selectedItem) + retainedTail)
        .distinctBy(SpaceDynamicItem::id)
    val hadLoadedTail = retainedTail.isNotEmpty()

    val uploaderPage =
      pending.uploaderPage?.let { page ->
        latestPortalUploaderPage = page
        page.copy(items = reconcileFirstUploaderPage(page.items))
      }
    val uploaderMerge = uploaderPage?.let { mergeUploaderFirstPage(current, it) }

    _state.value =
      current.copy(
        uploaders = uploaderMerge?.items ?: current.uploaders,
        uploaderFirstPageIds =
          uploaderMerge?.firstPageIds ?: current.uploaderFirstPageIds,
        uploaderOffset = uploaderMerge?.offset ?: current.uploaderOffset,
        uploadersHaveMore = uploaderMerge?.hasMore ?: current.uploadersHaveMore,
        items = mergedItems,
        firstPageIds = refreshedIds,
        offset = if (hadLoadedTail) current.offset else pending.response.offset,
        hasMore = if (hadLoadedTail) current.hasMore else pending.response.hasMore,
        error = null,
      )
    pendingUpdate = null
    saveSnapshot(_state.value)
  }
}

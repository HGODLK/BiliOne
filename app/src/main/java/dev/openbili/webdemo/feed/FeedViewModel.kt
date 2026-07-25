package dev.openbili.webdemo.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Paginated recommendation feed backed by the bilibili web API (WBI-signed). */
class FeedViewModel : ViewModel() {
  private val _state = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
  val state: StateFlow<FeedUiState> = _state.asStateFlow()

  // Auth
  private val _userInfo = MutableStateFlow(UserInfo(mid = 0, name = "", face = "", isLogin = false))
  val userInfo: StateFlow<UserInfo> = _userInfo.asStateFlow()

  private var freshIndex = 1L
  private var allItems = mutableListOf<FeedItem>()
  private var noMore = false
  private var loadJob: Job? = null
  private var generation = 0L
  private var initialTargetCount = 30

  init {
    loadPage()
  }

  fun refresh() {
    generation++
    loadJob?.cancel()
    freshIndex++
    noMore = false
    _state.value =
      if (allItems.isEmpty()) FeedUiState.Loading
      else FeedUiState.Content(items = allItems.toList(), isRefreshing = true)
    loadPage(refresh = true, expectedGeneration = generation)
  }

  fun loadNextPage() {
    if (noMore || loadJob?.isActive == true) return
    val current = _state.value
    if (current !is FeedUiState.Content || current.isLoadingMore || current.isRefreshing) return
    _state.value = current.copy(isLoadingMore = true)
    loadPage(refresh = false, expectedGeneration = generation)
  }

  /** Stops work that can be resumed once a root-page swipe has settled. */
  fun cancelSupplementaryLoadingForPageSwitch() {
    val current = _state.value as? FeedUiState.Content ?: return
    if (loadJob?.isActive != true) return
    generation++
    loadJob?.cancel()
    loadJob = null
    _state.value = current.copy(isLoadingMore = false, isRefreshing = false)
  }

  fun resumeSupplementaryLoadingAfterPageSwitch() {
    if (
      allItems.isNotEmpty() &&
        allItems.size < initialTargetCount &&
        !noMore &&
        loadJob?.isActive != true
    ) {
      loadPage(expectedGeneration = generation)
    }
  }

  fun updateUserInfo(info: UserInfo) {
    _userInfo.value = info
  }

  fun setInitialTargetCount(count: Int) {
    initialTargetCount = count.coerceIn(20, 40)
    if (allItems.isNotEmpty() && allItems.size < initialTargetCount && loadJob?.isActive != true) {
      loadPage(expectedGeneration = generation)
    }
  }

  private fun loadPage(
    refresh: Boolean = false,
    expectedGeneration: Long = generation,
  ) {
    loadJob = viewModelScope.launch {
      try {
        val requestIndex = freshIndex++
        val response = withContext(Dispatchers.IO) { BiliApi.getPersonalizedFeed(requestIndex) }
        if (expectedGeneration != generation) return@launch
        if (response.cards.isEmpty() && refresh) {
          _state.value =
            if (allItems.isEmpty()) FeedUiState.Empty()
            else FeedUiState.Content(allItems.toList(), refreshMessage = "刷新结果为空，已保留原内容")
          return@launch
        }
        if (response.cards.isEmpty()) {
          noMore = true
          val current = _state.value
          if (current is FeedUiState.Content) {
            _state.value = current.copy(isLoadingMore = false)
          }
          return@launch
        }

        val newItems =
          response.cards.map { card ->
            FeedItem(
              id = card.bvid.ifBlank { card.aid.toString() },
              title = card.title,
              videoUrl = "https://www.bilibili.com/video/${card.bvid.ifBlank { "av${card.aid}" }}",
              coverUrl = card.coverUrl,
              uploader = card.uploaderName,
              playCount = formatCount(card.playCount),
              duration = formatDuration(card.durationSeconds),
              // extra metadata for the video screen
              uploaderFace = card.uploaderFace,
              uploaderMid = card.uploaderMid,
              danmakuCount = card.danmakuCount,
              publishedAt = card.pubdate,
              description = card.description,
            )
          }

        if (refresh) allItems = newItems.toMutableList()
        else {
          val existing = allItems.mapTo(HashSet()) { it.videoUrl }
          allItems.addAll(newItems.filter { existing.add(it.videoUrl) })
        }
        _state.value =
          FeedUiState.Content(
            items = allItems.toList(),
            isLoadingMore = false,
          )
        if (!noMore && allItems.size < initialTargetCount) {
          loadPage(refresh = false, expectedGeneration = expectedGeneration)
        }
      } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        if (expectedGeneration != generation) return@launch
        android.util.Log.e("FeedVM", "loadPage failed", e)
        if (allItems.isEmpty()) {
          _state.value = FeedUiState.NetworkError(e.message ?: "加载失败")
        } else {
          val current = _state.value
          if (current is FeedUiState.Content) {
            _state.value =
              current.copy(
                isLoadingMore = false,
                refreshMessage = "加载失败：${e.message}",
              )
          }
        }
      }
    }
  }

  override fun onCleared() {
    loadJob?.cancel()
  }

  fun consumeRefreshMessage() {
    _state.update { current ->
      if (current is FeedUiState.Content) current.copy(refreshMessage = null) else current
    }
  }

  companion object {
    fun formatCount(n: Long): String =
      when {
        n >= 10_000_0000L -> "${n / 100_000_000}.${(n % 100_000_000) / 10_000_000}亿"
        n >= 10_000L -> "${n / 10_000}.${(n % 10_000) / 1000}万"
        else -> n.toString()
      }

    fun formatDuration(seconds: Long): String {
      val h = seconds / 3600
      val m = (seconds % 3600) / 60
      val s = seconds % 60
      return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
  }
}

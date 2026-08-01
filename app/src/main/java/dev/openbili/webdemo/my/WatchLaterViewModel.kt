package dev.openbili.webdemo.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.FeedCard
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WatchLaterFeedback(val token: Long, val message: String)

data class WatchLaterUiState(
  val items: List<FeedItem> = emptyList(),
  val addedVideoKeys: Set<String> = emptySet(),
  val busyVideoIds: Set<String> = emptySet(),
  val loading: Boolean = false,
  val loaded: Boolean = false,
  val error: String? = null,
  val feedback: WatchLaterFeedback? = null,
)

class WatchLaterViewModel : ViewModel() {
  private val _state = MutableStateFlow(WatchLaterUiState())
  val state: StateFlow<WatchLaterUiState> = _state.asStateFlow()

  private var accountMid = 0L
  private var generation = 0L
  private var feedbackToken = 0L
  private var loadJob: Job? = null

  fun setAccount(mid: Long) {
    if (accountMid == mid) return
    accountMid = mid
    generation++
    loadJob?.cancel()
    loadJob = null
    _state.value = WatchLaterUiState()
  }

  fun ensureLoaded() {
    if (accountMid > 0L && !_state.value.loaded && !_state.value.loading) refresh()
  }

  fun refresh() {
    val expectedMid = accountMid
    if (expectedMid <= 0L) return
    val expectedGeneration = ++generation
    loadJob?.cancel()
    _state.value = _state.value.copy(loading = true, error = null)
    loadJob = viewModelScope.launch {
      try {
        val cards = withContext(Dispatchers.IO) { BiliApi.getWatchLater() }
        if (!isCurrent(expectedGeneration, expectedMid)) return@launch
        _state.value =
          _state.value.copy(
            items = cards.map(::toFeedItem),
            addedVideoKeys = cards.flatMapTo(linkedSetOf(), ::watchLaterCardKeys),
            loading = false,
            loaded = true,
            error = null,
          )
      } catch (error: Throwable) {
        if (error is CancellationException) throw error
        if (!isCurrent(expectedGeneration, expectedMid)) return@launch
        _state.value =
          _state.value.copy(
            loading = false,
            loaded = true,
            error = error.message ?: "稍后再看加载失败",
          )
      }
    }
  }

  fun add(item: FeedItem, aidHint: Long? = null) {
    val expectedMid = accountMid
    if (expectedMid <= 0L) {
      postFeedback("请先登录")
      return
    }
    if (item.id in _state.value.busyVideoIds) return
    _state.value =
      _state.value.copy(
        busyVideoIds = _state.value.busyVideoIds + item.id,
        error = null,
      )
    viewModelScope.launch {
      try {
        val aid =
          withContext(Dispatchers.IO) {
            aidHint?.takeIf { it > 0L }
              ?: resolveWatchLaterAid(item)
              ?: throw IllegalStateException("无法识别这个视频")
          }
        withContext(Dispatchers.IO) { BiliApi.addToWatchLater(aid) }
        if (accountMid != expectedMid) return@launch
        val refreshedCards =
          if (_state.value.loaded) {
            withContext(Dispatchers.IO) { runCatching { BiliApi.getWatchLater() }.getOrNull() }
          } else {
            null
          }
        if (accountMid != expectedMid) return@launch
        val current = _state.value
        val keys = watchLaterVideoKeys(item, aid)
        _state.value =
          if (refreshedCards != null) {
            val refreshedItems = refreshedCards.map(::toFeedItem)
            current.copy(
              items =
                if (refreshedCards.any { it.aid == aid }) refreshedItems
                else
                  listOf(item) +
                    refreshedItems.filterNot { existing ->
                      watchLaterVideoKeys(existing).any(keys::contains)
                    },
              addedVideoKeys = refreshedCards.flatMapTo(linkedSetOf(), ::watchLaterCardKeys) + keys,
              busyVideoIds = current.busyVideoIds - item.id,
              loaded = true,
              error = null,
            )
          } else {
            current.copy(
              items =
                if (
                  current.loaded &&
                    current.items.none { existing ->
                      watchLaterVideoKeys(existing).any(keys::contains)
                    }
                ) {
                  listOf(item) + current.items
                } else {
                  current.items
                },
              addedVideoKeys = current.addedVideoKeys + keys,
              busyVideoIds = current.busyVideoIds - item.id,
              error = null,
            )
          }
        postFeedback("已添加到稍后再看")
      } catch (error: Throwable) {
        if (error is CancellationException) throw error
        if (accountMid != expectedMid) return@launch
        _state.value = _state.value.copy(busyVideoIds = _state.value.busyVideoIds - item.id)
        postFeedback(error.message ?: "添加到稍后再看失败")
      }
    }
  }

  fun consumeFeedback() {
    if (_state.value.feedback != null) _state.value = _state.value.copy(feedback = null)
  }

  fun consumeError() {
    if (_state.value.error != null) _state.value = _state.value.copy(error = null)
  }

  private fun postFeedback(message: String) {
    _state.value = _state.value.copy(feedback = WatchLaterFeedback(++feedbackToken, message))
  }

  private fun isCurrent(expectedGeneration: Long, expectedMid: Long): Boolean =
    expectedGeneration == generation && expectedMid == accountMid
}

internal fun resolveWatchLaterAid(item: FeedItem): Long? {
  val direct =
    item.id.removePrefix("av").removePrefix("AV").toLongOrNull()
      ?: Regex("/av(\\d+)", RegexOption.IGNORE_CASE)
        .find(item.videoUrl)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
  if (direct != null && direct > 0L) return direct
  val bvid =
    sequenceOf(item.id, item.videoUrl)
      .mapNotNull { WATCH_LATER_BVID_REGEX.find(it)?.value }
      .firstOrNull() ?: return null
  return BiliApi.getVideoInfo(bvid)?.aid?.takeIf { it > 0L }
}

internal fun watchLaterVideoKeys(item: FeedItem, aid: Long? = null): Set<String> = buildSet {
  add(item.id)
  aid
    ?.takeIf { it > 0L }
    ?.let {
      add(it.toString())
      add("av$it")
    }
  sequenceOf(item.id, item.videoUrl)
    .mapNotNull { WATCH_LATER_BVID_REGEX.find(it)?.value }
    .forEach(::add)
}

internal fun WatchLaterUiState.contains(item: FeedItem, aid: Long? = null): Boolean =
  watchLaterVideoKeys(item, aid).any(addedVideoKeys::contains)

private fun watchLaterCardKeys(card: FeedCard): Set<String> = buildSet {
  add(card.aid.toString())
  add("av${card.aid}")
  card.bvid.takeIf(String::isNotBlank)?.let(::add)
}

private fun toFeedItem(card: FeedCard): FeedItem =
  FeedItem(
    id = card.bvid.ifBlank { "av${card.aid}" },
    title = card.title,
    videoUrl =
      if (card.bvid.isNotBlank()) "https://www.bilibili.com/video/${card.bvid}"
      else "https://www.bilibili.com/video/av${card.aid}",
    coverUrl = card.coverUrl,
    uploader = card.uploaderName,
    playCount = FeedViewModel.formatCount(card.playCount),
    duration = FeedViewModel.formatDuration(card.durationSeconds),
    uploaderFace = card.uploaderFace,
    uploaderMid = card.uploaderMid,
    danmakuCount = card.danmakuCount,
    publishedAt = card.pubdate,
    description = card.description,
  )

private val WATCH_LATER_BVID_REGEX = Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE)

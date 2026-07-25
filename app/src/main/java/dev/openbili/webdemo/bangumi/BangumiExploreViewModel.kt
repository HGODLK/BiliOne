package dev.openbili.webdemo.bangumi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.api.BangumiExploreCategory
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.BangumiExplorePage
import dev.openbili.webdemo.api.BangumiWatchProgress
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.HistoryCursor
import dev.openbili.webdemo.api.SpaceContentCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cache lifetime for the "正在追" card list. */
private const val FOLLOWING_CACHE_MS = 60_000L
private const val EXPLORE_PAGE_CACHE_MS = 5 * 60_000L

private data class FollowingPlaybackOverride(
  val seasonId: Long,
  val episode: BangumiEpisode,
  val positionMs: Long,
)

data class BangumiExploreUiState(
  val selectedCategory: BangumiExploreCategory = BangumiExploreCategory.ANIME,
  val pages: Map<BangumiExploreCategory, BangumiExplorePage> = emptyMap(),
  val loading: Set<BangumiExploreCategory> = emptySet(),
  val errors: Map<BangumiExploreCategory, String> = emptyMap(),
  val following: List<SpaceContentCard> = emptyList(),
  val followingLoading: Boolean = false,
  val followingRefreshing: Boolean = false,
  val followingLoadingMore: Boolean = false,
  val followingHasMore: Boolean = false,
  val followingError: String? = null,
  val accountMid: Long = 0L,
  /** Season currently animating to the front of the rail; the matching card is drawn on top. */
  val reorderingSeasonId: Long? = null,
) {
  val selectedPage: BangumiExplorePage?
    get() = pages[selectedCategory]
}

/** A small, per-category cache: entering the second page never disturbs the root PV state. */
class BangumiExploreViewModel : ViewModel() {
  private val _state = MutableStateFlow(BangumiExploreUiState())
  val state: StateFlow<BangumiExploreUiState> = _state.asStateFlow()
  private val requests = mutableMapOf<BangumiExploreCategory, Job>()
  private val pageFetchedAtMillis = mutableMapOf<BangumiExploreCategory, Long>()
  private var accountMid: Long = 0L
  private var followingJob: Job? = null
  private var followingFetchedAtMillis: Long = 0L
  private var followingHistory: List<SpaceContentCard> = emptyList()
  private var followingHistoryCursor = HistoryCursor()
  private var followingHistoryHasMore = false
  private var followingFollowed: List<SpaceContentCard> = emptyList()
  private var followingPage = 0
  private var followingFollowedHasMore = false
  private val followingPlaybackOverrides = mutableMapOf<Long, FollowingPlaybackOverride>()

  fun setAccount(mid: Long) {
    if (accountMid == mid) return
    accountMid = mid
    followingJob?.cancel()
    followingFetchedAtMillis = 0L
    resetFollowingPagination()
    followingPlaybackOverrides.clear()
    pageFetchedAtMillis.clear()
    _state.update {
      it.copy(
        following = emptyList(),
        followingLoading = false,
        followingRefreshing = false,
        followingLoadingMore = false,
        followingHasMore = false,
        followingError = null,
        accountMid = mid,
      )
    }
    if (mid > 0L && BiliApi.bangumiSeasonType(_state.value.selectedCategory) != null) {
      loadFollowing(category = _state.value.selectedCategory)
      ensureLoaded(_state.value.selectedCategory)
    }
  }

  fun select(category: BangumiExploreCategory) {
    followingJob?.cancel()
    followingFetchedAtMillis = 0L
    resetFollowingPagination()
    followingPlaybackOverrides.clear()
    _state.update {
      it.copy(
        selectedCategory = category,
        // A category switch must never briefly render the previous category's rows.
        following = emptyList(),
        followingLoading = false,
        followingRefreshing = false,
        followingLoadingMore = false,
        followingHasMore = false,
        followingError = null,
      )
    }
    ensureLoaded(category)
    if (accountMid > 0L && BiliApi.bangumiSeasonType(category) != null) loadFollowing(category = category)
  }

  fun ensureLoaded(category: BangumiExploreCategory = _state.value.selectedCategory) {
    val current = _state.value
    val fresh =
      pageFetchedAtMillis[category]?.let { fetchedAt ->
        System.currentTimeMillis() - fetchedAt < EXPLORE_PAGE_CACHE_MS
      } == true
    if ((category in current.pages && fresh) || category in current.loading) return
    load(category)
  }

  fun refresh(category: BangumiExploreCategory = _state.value.selectedCategory) = load(category)

  /** Refresh the "正在追" list from the network. Use [force] to bypass cache. */
  fun refreshFollowing(force: Boolean = false) {
    if (accountMid <= 0L) return
    val current = _state.value
    if (!force && System.currentTimeMillis() - followingFetchedAtMillis < FOLLOWING_CACHE_MS) return
    if (force) {
      followingFetchedAtMillis = 0L
    }
    val category = current.selectedCategory
    if (BiliApi.bangumiSeasonType(category) == null) return
    loadFollowing(category = category, reset = true, forceRefresh = force)
  }

  /** Continue the horizontal "正在追" rail from its current server cursors. */
  fun loadMoreFollowing() {
    val current = _state.value
    if (
      accountMid <= 0L ||
        !current.followingHasMore ||
        current.followingLoading ||
        current.followingRefreshing ||
        current.followingLoadingMore ||
        BiliApi.bangumiSeasonType(current.selectedCategory) == null
    ) {
      return
    }
    loadFollowing(category = current.selectedCategory, reset = false)
  }

  /** Clear the cached following season status for a specific season so the next fetch picks up fresh data. */
  fun clearFollowingStatusCache(seasonId: Long) {
    if (seasonId <= 0L) return
    _state.update { current ->
      val updated = current.following.map { card ->
        if (card.seasonId == seasonId && card.watchProgress != null) {
          card.copy(watchProgress = null)
        } else card
      }
      if (updated === current.following) current
      else current.copy(following = updated)
    }
  }

  /** Apply the currently selected episode to the in-memory rail while the server catches up. */
  fun applyFollowingPlayback(seasonId: Long, episode: BangumiEpisode, positionMs: Long) {
    if (seasonId <= 0L || episode.id <= 0L) return
    val override =
      FollowingPlaybackOverride(
        seasonId = seasonId,
        episode = episode,
        positionMs = positionMs.coerceAtLeast(0L),
      )
    followingPlaybackOverrides[seasonId] = override
    _state.update { current ->
      val updated = current.following.map(::applyFollowingPlaybackOverride)
      if (updated == current.following) current else current.copy(following = updated)
    }
  }

  /**
   * Move the just-watched season's card to the front of the rail. Only the ordering changes; the
   * card content is left untouched. With the season-stable LazyRow keys and `Modifier.animateItem()`
   * this animates the old→new order transition in place instead of rebuilding the rail. The private
   * history cache is reordered too so a later pagination merge cannot silently revert the move.
   */
  fun moveFollowingToFront(seasonId: Long) {
    if (seasonId <= 0L) return
    fun List<SpaceContentCard>.toFront(): List<SpaceContentCard> {
      val index = indexOfFirst { it.seasonId == seasonId }
      return if (index <= 0) this
      else listOf(this[index]) + this.filterIndexed { i, _ -> i != index }
    }
    followingHistory = followingHistory.toFront()
    _state.update { current ->
      val updated = current.following.toFront()
      if (updated === current.following) current
      else current.copy(following = updated, reorderingSeasonId = seasonId)
    }
    viewModelScope.launch {
      delay(500)
      _state.update { if (it.reorderingSeasonId == seasonId) it.copy(reorderingSeasonId = null) else it }
    }
  }

  private fun loadFollowing(
    category: BangumiExploreCategory,
    reset: Boolean = true,
    forceRefresh: Boolean = false,
  ) {
    if (accountMid <= 0L) return
    val current = _state.value
    if (reset && current.followingLoading && !forceRefresh) return
    if (!reset && (!current.followingHasMore || current.followingLoadingMore)) return
    val requestedMid = accountMid
    val requestedCategory = category
    _state.update {
      it.copy(
        followingLoading = reset && !forceRefresh,
        followingRefreshing = reset && forceRefresh,
        followingLoadingMore = !reset,
        followingError = null,
      )
    }
    if (reset) {
      followingJob?.cancel()
      resetFollowingPagination()
    }
    followingJob =
      viewModelScope.launch {
        val result =
          withContext(Dispatchers.IO) {
            runCatching {
              if (reset) {
                val history = BiliApi.getBangumiWatchingHistoryPage(requestedCategory)
                val followed = BiliApi.getBangumiWatchingFollowedPage(requestedMid, requestedCategory, page = 1)
                FollowingPageResult.Initial(history, followed)
              } else if (followingHistoryHasMore) {
                FollowingPageResult.History(
                  BiliApi.getBangumiWatchingHistoryPage(requestedCategory, followingHistoryCursor)
                )
              } else {
                FollowingPageResult.Followed(
                  BiliApi.getBangumiWatchingFollowedPage(
                    requestedMid,
                    requestedCategory,
                    page = followingPage + 1,
                  )
                )
              }
            }
          }
        if (requestedMid != accountMid) return@launch
        if (_state.value.selectedCategory != requestedCategory) return@launch
        result
          .onSuccess { response ->
            when (response) {
              is FollowingPageResult.Initial -> {
                followingHistory = response.history.cards
                followingHistoryCursor = response.history.cursor
                followingHistoryHasMore = response.history.hasMore && response.history.cursor != HistoryCursor()
                followingFollowed = response.followed.cards
                followingPage = 1
                followingFollowedHasMore = response.followed.hasMore
                followingFetchedAtMillis = System.currentTimeMillis()
                // A successful full refresh is authoritative. Any local override has now had
                // an opportunity to be replaced by the server's latest history.
                followingPlaybackOverrides.clear()
              }
              is FollowingPageResult.History -> {
                followingHistory = (followingHistory + response.history.cards).distinctBy(::followingKey)
                followingHistoryHasMore =
                  response.history.hasMore && response.history.cursor != followingHistoryCursor
                followingHistoryCursor = response.history.cursor
              }
              is FollowingPageResult.Followed -> {
                followingFollowed = (followingFollowed + response.followed.cards).distinctBy(::followingKey)
                followingPage += 1
                followingFollowedHasMore = response.followed.hasMore
              }
            }
            val seasonType = BiliApi.bangumiSeasonType(requestedCategory) ?: return@onSuccess
            val cards =
              BiliApi
                .mergeBangumiWatchingCards(followingFollowed, followingHistory, seasonType)
                .map(::applyFollowingPlaybackOverride)
            _state.update {
              it.copy(
                following = cards,
                followingLoading = false,
                followingRefreshing = false,
                followingLoadingMore = false,
                followingHasMore = followingHistoryHasMore || followingFollowedHasMore,
              )
            }
          }
          .onFailure { error ->
            _state.update {
              it.copy(
                followingLoading = false,
                followingRefreshing = false,
                followingLoadingMore = false,
                followingError = error.message ?: "正在追加载失败",
              )
            }
          }
      }
  }

  private fun resetFollowingPagination() {
    followingHistory = emptyList()
    followingHistoryCursor = HistoryCursor()
    followingHistoryHasMore = false
    followingFollowed = emptyList()
    followingPage = 0
    followingFollowedHasMore = false
  }

  private fun followingKey(card: SpaceContentCard): String =
    card.seasonId.takeIf { it > 0L }?.toString() ?: card.id

  private fun applyFollowingPlaybackOverride(card: SpaceContentCard): SpaceContentCard {
    val override = followingPlaybackOverrides[card.seasonId] ?: return card
    val episode = override.episode
    val durationMs = episode.durationSeconds.coerceAtLeast(0L) * 1_000L
    val percent =
      if (durationMs > 0L) {
        ((override.positionMs * 100L) / durationMs).toInt().coerceIn(0, 100)
      } else null
    val episodeIndex = episode.title.ifBlank { episode.longTitle }
    val coverUrl = episode.coverUrl.ifBlank { card.coverUrl }
    return card.copy(
      coverUrl = coverUrl,
      historyCoverUrl = coverUrl,
      aid = episode.aid.takeIf { it > 0L } ?: card.aid,
      bvid = episode.bvid.ifBlank { card.bvid },
      videoUrl = "https://www.bilibili.com/bangumi/play/ep${episode.id}",
      episodeId = episode.id,
      watchProgress =
        BangumiWatchProgress(
          episodeId = episode.id,
          episodeIndex = episodeIndex,
          positionMs = override.positionMs,
          percent = percent,
        ),
      hasHistory = true,
    )
  }

  private sealed interface FollowingPageResult {
    data class Initial(
      val history: dev.openbili.webdemo.api.BangumiWatchingHistoryPage,
      val followed: dev.openbili.webdemo.api.SpaceBangumiResponse,
    ) : FollowingPageResult

    data class History(val history: dev.openbili.webdemo.api.BangumiWatchingHistoryPage) : FollowingPageResult

    data class Followed(val followed: dev.openbili.webdemo.api.SpaceBangumiResponse) : FollowingPageResult
  }

  private fun load(category: BangumiExploreCategory) {
    requests[category]?.cancel()
    _state.update {
      it.copy(
        loading = it.loading + category,
        errors = it.errors - category,
      )
    }
    requests[category] =
      viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { BiliApi.getBangumiExplorePage(category) } }
        result
          .onSuccess { page ->
            pageFetchedAtMillis[category] = System.currentTimeMillis()
            _state.update {
              it.copy(
                pages = it.pages + (category to page),
                loading = it.loading - category,
              )
            }
          }
          .onFailure { error ->
            _state.update {
              it.copy(
                loading = it.loading - category,
                errors = it.errors + (category to (error.message ?: "加载失败，请重试")),
              )
            }
          }
      }
  }
}

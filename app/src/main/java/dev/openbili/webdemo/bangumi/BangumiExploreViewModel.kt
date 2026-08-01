package dev.openbili.webdemo.bangumi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.api.BangumiExploreCategory
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.BangumiExplorePage
import dev.openbili.webdemo.api.BangumiWatchingHistoryPage
import dev.openbili.webdemo.api.BangumiWatchProgress
import dev.openbili.webdemo.api.BangumiWatchProgressState
import dev.openbili.webdemo.api.BangumiUserStatus
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.HistoryCursor
import dev.openbili.webdemo.api.SpaceContentCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Cache lifetime for the "正在追" card list. */
private const val FOLLOWING_CACHE_MS = 60_000L
private const val EXPLORE_PAGE_CACHE_MS = 5 * 60_000L
private const val INITIAL_HISTORY_TARGET = 12
private const val INITIAL_HISTORY_MAX_PAGES = 4
private const val FOLLOWING_COVER_RESOLVE_LIMIT = 18
private const val FOLLOWING_COVER_RESOLVE_CONCURRENCY = 3
private const val FOLLOWING_STATUS_RESOLVE_CONCURRENCY = 4

private data class FollowingPlaybackOverride(
  val seasonId: Long,
  val episode: BangumiEpisode,
  val positionMs: Long,
  val viewedAt: Long,
)

data class BangumiFollowingUiState(
  val cards: List<SpaceContentCard> = emptyList(),
  val loading: Boolean = false,
  val refreshing: Boolean = false,
  val loadingMore: Boolean = false,
  val hasMore: Boolean = false,
  val error: String? = null,
  /** Changes whenever this category is entered again so its Compose tree starts cleanly. */
  val sessionId: Long = 0L,
  /** Season currently animating to the front of this category's rail. */
  val reorderingSeasonId: Long? = null,
)

data class BangumiExploreUiState(
  val selectedCategory: BangumiExploreCategory = BangumiExploreCategory.ANIME,
  val pages: Map<BangumiExploreCategory, BangumiExplorePage> = emptyMap(),
  val loading: Set<BangumiExploreCategory> = emptySet(),
  val errors: Map<BangumiExploreCategory, String> = emptyMap(),
  val followingByCategory: Map<BangumiExploreCategory, BangumiFollowingUiState> = emptyMap(),
  val accountMid: Long = 0L,
) {
  val selectedPage: BangumiExplorePage?
    get() = pages[selectedCategory]

  fun following(category: BangumiExploreCategory): BangumiFollowingUiState =
    followingByCategory[category] ?: BangumiFollowingUiState()
}

private class FollowingComponentState {
  var job: Job? = null
  var fetchedAtMillis: Long = 0L
  var history: List<SpaceContentCard> = emptyList()
  var historyCursor: HistoryCursor = HistoryCursor()
  var historyHasMore: Boolean = false
  var followed: List<SpaceContentCard> = emptyList()
  var followedPage: Int = 0
  var followedHasMore: Boolean = false
  val playbackOverrides = mutableMapOf<Long, FollowingPlaybackOverride>()
  val statusCache = ConcurrentHashMap<Long, BangumiUserStatus>()

  fun reset() {
    job?.cancel()
    job = null
    fetchedAtMillis = 0L
    history = emptyList()
    historyCursor = HistoryCursor()
    historyHasMore = false
    followed = emptyList()
    followedPage = 0
    followedHasMore = false
    playbackOverrides.clear()
    statusCache.clear()
  }
}

/** A small, per-category cache: entering the second page never disturbs the root PV state. */
class BangumiExploreViewModel : ViewModel() {
  private val _state = MutableStateFlow(BangumiExploreUiState())
  val state: StateFlow<BangumiExploreUiState> = _state.asStateFlow()
  private val requests = mutableMapOf<BangumiExploreCategory, Job>()
  private val pageFetchedAtMillis = mutableMapOf<BangumiExploreCategory, Long>()
  private var accountMid: Long = 0L
  private val followingComponents = mutableMapOf<BangumiExploreCategory, FollowingComponentState>()
  private val episodeCoverCache = ConcurrentHashMap<Long, String>()

  private fun followingComponent(category: BangumiExploreCategory): FollowingComponentState =
    followingComponents.getOrPut(category, ::FollowingComponentState)

  private fun updateFollowing(
    category: BangumiExploreCategory,
    transform: (BangumiFollowingUiState) -> BangumiFollowingUiState,
  ) {
    _state.update { current ->
      val updated = transform(current.following(category))
      current.copy(followingByCategory = current.followingByCategory + (category to updated))
    }
  }

  fun setAccount(mid: Long) {
    if (accountMid == mid) return
    accountMid = mid
    followingComponents.values.forEach(FollowingComponentState::reset)
    followingComponents.clear()
    pageFetchedAtMillis.clear()
    _state.update {
      it.copy(
        followingByCategory = emptyMap(),
        accountMid = mid,
      )
    }
    if (mid > 0L && BiliApi.bangumiSeasonType(_state.value.selectedCategory) != null) {
      loadFollowing(category = _state.value.selectedCategory)
      ensureLoaded(_state.value.selectedCategory)
    }
  }

  fun select(category: BangumiExploreCategory) {
    _state.update { it.copy(selectedCategory = category) }
    ensureLoaded(category)
    if (accountMid > 0L && BiliApi.bangumiSeasonType(category) != null) {
      loadFollowing(
        category = category,
        silent = _state.value.following(category).cards.isNotEmpty(),
      )
    }
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
  fun refreshFollowing(force: Boolean = false, silent: Boolean = false) {
    if (accountMid <= 0L) return
    val current = _state.value
    val category = current.selectedCategory
    val component = followingComponent(category)
    if (!force && System.currentTimeMillis() - component.fetchedAtMillis < FOLLOWING_CACHE_MS) return
    if (force) {
      component.fetchedAtMillis = 0L
      component.statusCache.clear()
    }
    if (BiliApi.bangumiSeasonType(category) == null) return
    loadFollowing(
      category = category,
      reset = true,
      forceRefresh = force,
      silent = silent,
    )
  }

  /** Continue the horizontal "正在追" rail from its current server cursors. */
  fun loadMoreFollowing(category: BangumiExploreCategory = _state.value.selectedCategory) {
    val current = _state.value
    val following = current.following(category)
    if (
      accountMid <= 0L ||
        !following.hasMore ||
        following.loading ||
        following.refreshing ||
        following.loadingMore ||
        BiliApi.bangumiSeasonType(category) == null
    ) {
      return
    }
    loadFollowing(category = category, reset = false)
  }

  /** Clear the cached following season status for a specific season so the next fetch picks up fresh data. */
  fun clearFollowingStatusCache(seasonId: Long) {
    if (seasonId <= 0L) return
    followingComponents.values.forEach { it.statusCache.remove(seasonId) }
    _state.update { current ->
      val updatedByCategory =
        current.followingByCategory.mapValues { (_, following) ->
          following.copy(
            cards =
              following.cards.map { card ->
                if (card.seasonId == seasonId) {
                  card.copy(
                    watchProgress = null,
                    watchProgressState = BangumiWatchProgressState.UNAVAILABLE,
                  )
                } else card
              }
          )
        }
      if (updatedByCategory == current.followingByCategory) current
      else current.copy(followingByCategory = updatedByCategory)
    }
  }

  private fun categoriesContainingSeason(seasonId: Long): Set<BangumiExploreCategory> {
    val matches =
      _state.value.followingByCategory
        .filterValues { following -> following.cards.any { it.seasonId == seasonId } }
        .keys
    return matches.ifEmpty { setOf(_state.value.selectedCategory) }
  }

  /** Apply the currently selected episode to the matching category component while the server catches up. */
  fun applyFollowingPlayback(seasonId: Long, episode: BangumiEpisode, positionMs: Long) {
    if (seasonId <= 0L || episode.id <= 0L) return
    val override =
      FollowingPlaybackOverride(
        seasonId = seasonId,
        episode = episode,
        positionMs = positionMs.coerceAtLeast(0L),
        viewedAt = System.currentTimeMillis() / 1_000L,
      )
    categoriesContainingSeason(seasonId).forEach { category ->
      val component = followingComponent(category)
      component.playbackOverrides[seasonId] = override
      component.statusCache[seasonId] =
        BangumiUserStatus(
          followed = true,
          watchProgress = override.toWatchProgress(),
        )
      updateFollowing(category) { following ->
        following.copy(cards = following.cards.map { applyFollowingPlaybackOverride(it, component) })
      }
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
    categoriesContainingSeason(seasonId).forEach { category ->
      val component = followingComponent(category)
      component.history = component.history.toFront()
      updateFollowing(category) { following ->
        val updated = following.cards.toFront()
        if (updated === following.cards) following
        else following.copy(cards = updated, reorderingSeasonId = seasonId)
      }
      viewModelScope.launch {
        delay(500)
        updateFollowing(category) {
          if (it.reorderingSeasonId == seasonId) it.copy(reorderingSeasonId = null) else it
        }
      }
    }
  }

  private fun loadFollowing(
    category: BangumiExploreCategory,
    reset: Boolean = true,
    forceRefresh: Boolean = false,
    silent: Boolean = false,
  ) {
    if (accountMid <= 0L) return
    val component = followingComponent(category)
    val current = _state.value.following(category)
    if (component.job?.isActive == true && !forceRefresh) return
    if (!reset && (!current.hasMore || current.loadingMore)) return
    val requestedMid = accountMid
    val requestedCategory = category
    val keepCurrentContentVisible = silent && current.cards.isNotEmpty()
    if (!keepCurrentContentVisible) {
      updateFollowing(category) {
        it.copy(
          loading = reset && !forceRefresh,
          refreshing = reset && forceRefresh,
          loadingMore = !reset,
          error = null,
        )
      }
    }
    if (reset) {
      component.job?.cancel()
      component.history = emptyList()
      component.historyCursor = HistoryCursor()
      component.historyHasMore = false
      component.followed = emptyList()
      component.followedPage = 0
      component.followedHasMore = false
    }
    component.job =
      viewModelScope.launch {
        val result =
          withContext(Dispatchers.IO) {
            runCatching {
              if (reset) {
                coroutineScope {
                  val history = async { loadInitialWatchingHistory(requestedCategory) }
                  val followed = async {
                    BiliApi.getBangumiWatchingFollowedPage(
                      requestedMid,
                      requestedCategory,
                      page = 1,
                    )
                  }
                  FollowingPageResult.Initial(history.await(), followed.await())
                }
              } else if (component.historyHasMore) {
                FollowingPageResult.History(
                  BiliApi.getBangumiWatchingHistoryPage(requestedCategory, component.historyCursor)
                )
              } else {
                FollowingPageResult.Followed(
                  BiliApi.getBangumiWatchingFollowedPage(
                    requestedMid,
                    requestedCategory,
                    page = component.followedPage + 1,
                  )
                )
              }
            }
          }
        if (requestedMid != accountMid) return@launch
        result
          .onSuccess { response ->
            when (response) {
              is FollowingPageResult.Initial -> {
                component.history = response.history.cards
                component.historyCursor = response.history.cursor
                component.historyHasMore =
                  response.history.hasMore && response.history.cursor != HistoryCursor()
                component.followed = response.followed.cards
                component.followedPage = 1
                component.followedHasMore = response.followed.hasMore
                component.fetchedAtMillis = System.currentTimeMillis()
                // A successful full refresh is authoritative. Any local override has now had
                // an opportunity to be replaced by the server's latest history.
                component.playbackOverrides.clear()
              }
              is FollowingPageResult.History -> {
                component.history =
                  (component.history + response.history.cards).distinctBy(::followingKey)
                component.historyHasMore =
                  response.history.hasMore && response.history.cursor != component.historyCursor
                component.historyCursor = response.history.cursor
              }
              is FollowingPageResult.Followed -> {
                component.followed =
                  (component.followed + response.followed.cards).distinctBy(::followingKey)
                component.followedPage += 1
                component.followedHasMore = response.followed.hasMore
              }
            }
            val seasonType = BiliApi.bangumiSeasonType(requestedCategory) ?: return@onSuccess
            val cards =
              BiliApi
                .mergeBangumiWatchingCards(component.followed, component.history, seasonType)
                .map { applyFollowingPlaybackOverride(it, component) }
                .let { merged ->
                  withContext(Dispatchers.IO) {
                    resolveFollowingEpisodeCovers(resolveFollowingStatuses(merged, component))
                  }
                }
                .let(::sortFollowingCards)
            if (requestedMid != accountMid) return@onSuccess
            updateFollowing(requestedCategory) {
              val hasMore = component.historyHasMore || component.followedHasMore
              if (
                keepCurrentContentVisible &&
                  followingSnapshotsEqual(it.cards, cards) &&
                  it.hasMore == hasMore
              ) {
                // The network and all progress/cover resolution still completed, but no visible
                // snapshot changed. Preserve the exact StateFlow value so Compose does no work.
                it
              } else {
                it.copy(
                  cards = cards,
                  loading = false,
                  refreshing = false,
                  loadingMore = false,
                  hasMore = hasMore,
                  error = null,
                )
              }
            }
          }
          .onFailure { error ->
            if (error is CancellationException || requestedMid != accountMid) {
              return@onFailure
            }
            if (!keepCurrentContentVisible) {
              updateFollowing(requestedCategory) {
                it.copy(
                  loading = false,
                  refreshing = false,
                  loadingMore = false,
                  error = error.message ?: "正在追加载失败",
                )
              }
            }
          }
      }
  }

  private fun followingKey(card: SpaceContentCard): String =
    card.seasonId.takeIf { it > 0L }?.toString() ?: card.id

  private fun sortFollowingCards(cards: List<SpaceContentCard>): List<SpaceContentCard> {
    val watched =
      cards.filter { it.lastViewedAt > 0L }.sortedByDescending(SpaceContentCard::lastViewedAt)
    val withoutTimestamp = cards.filter { it.lastViewedAt <= 0L }
    return watched + withoutTimestamp
  }

  private fun loadInitialWatchingHistory(
    category: BangumiExploreCategory
  ): BangumiWatchingHistoryPage {
    var cursor = HistoryCursor()
    var hasMore = true
    val cards = mutableListOf<SpaceContentCard>()
    var loadedPages = 0
    while (
      loadedPages < INITIAL_HISTORY_MAX_PAGES &&
        hasMore &&
        cards.distinctBy(::followingKey).size < INITIAL_HISTORY_TARGET
    ) {
      val page = BiliApi.getBangumiWatchingHistoryPage(category, cursor)
      cards += page.cards
      val nextCursor = page.cursor
      hasMore = page.hasMore && nextCursor != cursor && nextCursor != HistoryCursor()
      cursor = nextCursor
      loadedPages += 1
    }
    return BangumiWatchingHistoryPage(
      cards =
        cards
          .sortedByDescending(SpaceContentCard::lastViewedAt)
          .distinctBy(::followingKey),
      cursor = cursor,
      hasMore = hasMore,
    )
  }

  private suspend fun resolveFollowingStatuses(
    cards: List<SpaceContentCard>,
    component: FollowingComponentState,
  ): List<SpaceContentCard> = coroutineScope {
    val semaphore = Semaphore(FOLLOWING_STATUS_RESOLVE_CONCURRENCY)
    cards.map { card ->
      async {
        if (
          card.watchProgress != null ||
            card.hasHistory ||
            card.historicalOnly ||
            card.seasonId <= 0L
        ) {
          return@async card.withResolvedProgressState()
        }
        val cached = component.statusCache[card.seasonId]
        val status =
          cached
            ?: semaphore.withPermit {
              component.statusCache[card.seasonId]
                ?: BiliApi.getBangumiUserStatus(card.seasonId)?.also { resolved ->
                  component.statusCache[card.seasonId] = resolved
                }
            }
        applyFollowingUserStatus(card, status)
      }
    }.awaitAll()
  }

  private suspend fun resolveFollowingEpisodeCovers(
    cards: List<SpaceContentCard>
  ): List<SpaceContentCard> = coroutineScope {
    val semaphore = Semaphore(FOLLOWING_COVER_RESOLVE_CONCURRENCY)
    cards.mapIndexed { index, card ->
      async {
        val episodeId =
          card.watchProgress?.episodeId?.takeIf { it > 0L }
            ?: card.episodeId.takeIf { it > 0L }
        if (
          index >= FOLLOWING_COVER_RESOLVE_LIMIT ||
            episodeId == null ||
            card.historyCoverUrl.isNotBlank()
        ) {
          return@async card
        }
        val cover =
          episodeCoverCache[episodeId]
            ?: semaphore.withPermit {
              episodeCoverCache[episodeId]
                ?: runCatching {
                    val season = BiliApi.getBangumiSeason(episodeId = episodeId)
                    (season.episodes + season.sections.flatMap { it.episodes })
                      .firstOrNull { it.id == episodeId }
                      ?.coverUrl
                      .orEmpty()
                  }
                  .getOrDefault("")
                  .also { resolved ->
                    if (resolved.isNotBlank()) episodeCoverCache[episodeId] = resolved
                  }
            }
        if (cover.isBlank()) card
        else {
          card.copy(
            coverUrl = cover,
            historyCoverUrl = cover,
            episodeId = episodeId,
            videoUrl = "https://www.bilibili.com/bangumi/play/ep$episodeId",
          )
        }
      }
    }.awaitAll()
  }

  private fun applyFollowingPlaybackOverride(
    card: SpaceContentCard,
    component: FollowingComponentState,
  ): SpaceContentCard {
    val override = component.playbackOverrides[card.seasonId] ?: return card
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
      watchProgressState = BangumiWatchProgressState.RESOLVED,
      hasHistory = true,
      lastViewedAt = override.viewedAt,
    )
  }

  private fun FollowingPlaybackOverride.toWatchProgress(): BangumiWatchProgress {
    val durationMs = episode.durationSeconds.coerceAtLeast(0L) * 1_000L
    val percent =
      if (durationMs > 0L) {
        ((positionMs * 100L) / durationMs).toInt().coerceIn(0, 100)
      } else null
    return BangumiWatchProgress(
      episodeId = episode.id,
      episodeIndex = episode.title.ifBlank { episode.longTitle },
      positionMs = positionMs,
      percent = percent,
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

internal fun applyFollowingUserStatus(
  card: SpaceContentCard,
  status: BangumiUserStatus?,
): SpaceContentCard {
  if (status == null) {
    return card.copy(watchProgressState = BangumiWatchProgressState.UNAVAILABLE)
  }
  val progress =
    status.watchProgress
      ?: return card.copy(watchProgressState = BangumiWatchProgressState.NO_RECORD)
  return card.copy(
    episodeId = progress.episodeId,
    videoUrl = "https://www.bilibili.com/bangumi/play/ep${progress.episodeId}",
    watchProgress = progress,
    watchProgressState = BangumiWatchProgressState.RESOLVED,
    hasHistory = true,
  )
}

internal fun followingSnapshotsEqual(
  current: List<SpaceContentCard>,
  updated: List<SpaceContentCard>,
): Boolean = current == updated

private fun SpaceContentCard.withResolvedProgressState(): SpaceContentCard =
  if (
    watchProgress != null &&
      watchProgressState != BangumiWatchProgressState.RESOLVED
  ) {
    copy(watchProgressState = BangumiWatchProgressState.RESOLVED)
  } else {
    this
  }

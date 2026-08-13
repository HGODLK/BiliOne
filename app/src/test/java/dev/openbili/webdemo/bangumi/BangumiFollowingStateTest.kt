package dev.openbili.webdemo.bangumi

import dev.openbili.webdemo.api.BangumiExploreCategory
import dev.openbili.webdemo.api.BangumiWatchProgress
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.ui.canCommitFollowingCardsDuringScroll
import dev.openbili.webdemo.ui.shouldAutoLoadMoreFollowing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiFollowingStateTest {
  @Test
  fun `anime and guochuang following components expose only their own cards and flags`() {
    val animeCard = SpaceContentCard(id = "anime", title = "番剧")
    val guochuangCard = SpaceContentCard(id = "guochuang", title = "国创")
    val state =
      BangumiExploreUiState(
        selectedCategory = BangumiExploreCategory.GUOCHUANG,
        followingByCategory =
          mapOf(
            BangumiExploreCategory.ANIME to
              BangumiFollowingUiState(cards = listOf(animeCard), loading = true, sessionId = 3L),
            BangumiExploreCategory.GUOCHUANG to
              BangumiFollowingUiState(
                cards = listOf(guochuangCard),
                hasMore = true,
                sessionId = 7L,
              ),
          ),
      )

    assertEquals(listOf(animeCard), state.following(BangumiExploreCategory.ANIME).cards)
    assertTrue(state.following(BangumiExploreCategory.ANIME).loading)
    assertEquals(3L, state.following(BangumiExploreCategory.ANIME).sessionId)

    assertEquals(listOf(guochuangCard), state.following(BangumiExploreCategory.GUOCHUANG).cards)
    assertTrue(state.following(BangumiExploreCategory.GUOCHUANG).hasMore)
    assertEquals(7L, state.following(BangumiExploreCategory.GUOCHUANG).sessionId)
  }

  @Test
  fun `silent refresh only commits a semantically changed final snapshot`() {
    val first = SpaceContentCard(id = "first", title = "第一部", seasonId = 1L)
    val second = SpaceContentCard(id = "second", title = "第二部", seasonId = 2L)
    val current = listOf(first, second)

    assertTrue(followingSnapshotsEqual(current, current.map { it.copy() }))
    assertFalse(followingSnapshotsEqual(current, listOf(second, first)))
    assertFalse(
      followingSnapshotsEqual(
        current,
        listOf(first.copy(subtitle = "进度已更新"), second),
      )
    )
  }

  @Test
  fun `following cards sort all timestamped history before stable followed entries`() {
    val followedA = SpaceContentCard(id = "followed-a", title = "未观看甲", seasonId = 1L)
    val older = SpaceContentCard(id = "older", title = "较早观看", seasonId = 2L, lastViewedAt = 100L)
    val newest = SpaceContentCard(id = "newest", title = "最近观看", seasonId = 3L, lastViewedAt = 300L)
    val followedB = SpaceContentCard(id = "followed-b", title = "未观看乙", seasonId = 4L)

    assertEquals(
      listOf("newest", "older", "followed-a", "followed-b"),
      sortFollowingCards(listOf(followedA, older, newest, followedB)).map(SpaceContentCard::id),
    )
  }

  @Test
  fun `later history updates in place and appends without displacing the rail`() {
    val followedA = SpaceContentCard(id = "followed-a", title = "未观看甲", seasonId = 1L)
    val followedB = SpaceContentCard(id = "followed-b", title = "未观看乙", seasonId = 2L)
    val resolvedA = followedA.copy(lastViewedAt = 300L, hasHistory = true)
    val olderHistory =
      SpaceContentCard(id = "older", title = "更早历史", seasonId = 3L, lastViewedAt = 100L)

    assertEquals(
      listOf("followed-a", "followed-b", "older"),
      mergeFollowingPageStable(
          listOf(followedA, followedB),
          listOf(olderHistory, resolvedA, followedB),
        )
        .map(SpaceContentCard::id),
    )
    assertEquals(
      300L,
      mergeFollowingPageStable(
          listOf(followedA, followedB),
          listOf(olderHistory, resolvedA, followedB),
        )
        .first()
        .lastViewedAt,
    )
  }

  @Test
  fun `local playback override remains until matching server history catches up`() {
    val stale =
      SpaceContentCard(
        id = "season",
        title = "国创",
        seasonId = 7L,
        episodeId = 70L,
        lastViewedAt = 99L,
      )
    val caughtUp =
      stale.copy(
        episodeId = 71L,
        watchProgress =
          BangumiWatchProgress(
            episodeId = 71L,
            episodeIndex = "第 2 话",
            positionMs = 12_000L,
          ),
        lastViewedAt = 100L,
      )

    assertFalse(
      serverHistoryAcknowledgesPlaybackOverride(
        serverHistoryCard = stale,
        overrideEpisodeId = 71L,
        overrideViewedAt = 100L,
      )
    )
    assertTrue(
      serverHistoryAcknowledgesPlaybackOverride(
        serverHistoryCard = caughtUp,
        overrideEpisodeId = 71L,
        overrideViewedAt = 100L,
      )
    )
  }

  @Test
  fun `short guochuang rail does not auto paginate before user scrolls`() {
    assertFalse(
      shouldAutoLoadMoreFollowing(
        userScrollStarted = false,
        hasMore = true,
        loadingMore = false,
        lastVisibleIndex = 2,
        lastCardIndex = 2,
      )
    )
    assertTrue(
      shouldAutoLoadMoreFollowing(
        userScrollStarted = true,
        hasMore = true,
        loadingMore = false,
        lastVisibleIndex = 2,
        lastCardIndex = 2,
      )
    )
  }

  @Test
  fun `following rail prefetches three cards before the boundary`() {
    assertFalse(
      shouldAutoLoadMoreFollowing(
        userScrollStarted = true,
        hasMore = true,
        loadingMore = false,
        lastVisibleIndex = 6,
        lastCardIndex = 10,
        prefetchDistanceCards = 3,
      )
    )
    assertTrue(
      shouldAutoLoadMoreFollowing(
        userScrollStarted = true,
        hasMore = true,
        loadingMore = false,
        lastVisibleIndex = 7,
        lastCardIndex = 10,
        prefetchDistanceCards = 3,
      )
    )
  }

  @Test
  fun `empty continuation pages keep scanning within the bounded request`() {
    assertTrue(
      shouldContinueFollowingScan(
        visibleNewCards = 0,
        hasMore = true,
        completedRounds = 1,
        maxRounds = 3,
      )
    )
    assertFalse(
      shouldContinueFollowingScan(
        visibleNewCards = 1,
        hasMore = true,
        completedRounds = 1,
        maxRounds = 3,
      )
    )
    assertFalse(
      shouldContinueFollowingScan(
        visibleNewCards = 0,
        hasMore = true,
        completedRounds = 3,
        maxRounds = 3,
      )
    )
  }

  @Test
  fun `stable tail append can commit while the following rail is scrolling`() {
    val first = SpaceContentCard(id = "first", title = "第一部", seasonId = 1L)
    val second = SpaceContentCard(id = "second", title = "第二部", seasonId = 2L)
    val appended = SpaceContentCard(id = "third", title = "第三部", seasonId = 3L)

    assertTrue(
      canCommitFollowingCardsDuringScroll(
        current = listOf(first, second),
        updated = listOf(first.copy(subtitle = "进度更新"), second, appended),
      )
    )
    assertFalse(
      canCommitFollowingCardsDuringScroll(
        current = listOf(first, second),
        updated = listOf(second, first, appended),
      )
    )
  }
}

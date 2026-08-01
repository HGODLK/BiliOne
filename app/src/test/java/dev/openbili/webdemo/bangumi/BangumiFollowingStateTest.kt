package dev.openbili.webdemo.bangumi

import dev.openbili.webdemo.api.BangumiExploreCategory
import dev.openbili.webdemo.api.SpaceContentCard
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
}

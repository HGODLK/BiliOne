package dev.openbili.webdemo.ui

import dev.openbili.webdemo.api.SpaceContentCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiFollowingPlaceholderTest {
  @Test
  fun refreshKeepsLoadedCardsVisibleForReorderAnimation() {
    assertFalse(
      shouldShowFollowingPlaceholders(
        following = listOf(SpaceContentCard(id = "season-1", title = "测试番剧")),
        loading = false,
        refreshing = true,
      )
    )
  }

  @Test
  fun initialLoadShowsPlaceholders() {
    assertTrue(
      shouldShowFollowingPlaceholders(
        following = emptyList(),
        loading = true,
        refreshing = false,
      )
    )
  }
}

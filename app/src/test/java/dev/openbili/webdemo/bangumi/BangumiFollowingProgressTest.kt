package dev.openbili.webdemo.bangumi

import dev.openbili.webdemo.api.BangumiUserStatus
import dev.openbili.webdemo.api.BangumiWatchProgress
import dev.openbili.webdemo.api.BangumiWatchProgressState
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import dev.openbili.webdemo.ui.buildFollowingSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BangumiFollowingProgressTest {
  private val followedCard =
    SpaceContentCard(
      id = "bangumi:123",
      title = "测试番剧",
      subtitle = "更新至第12话",
      seasonId = 123L,
      episodeId = 999L,
      kind = SpaceContentKind.BANGUMI,
      seasonType = 1,
    )

  @Test
  fun `unavailable follow progress is not labelled unwatched`() {
    val card = applyFollowingUserStatus(followedCard, status = null)

    assertEquals(BangumiWatchProgressState.UNAVAILABLE, card.watchProgressState)
    assertNull(card.watchProgress)
    assertFalse(
      buildFollowingSubtitle(
        progress = card.watchProgress,
        progressState = card.watchProgressState,
        hasHistory = card.hasHistory,
        historicalOnly = card.historicalOnly,
        fallback = card.subtitle,
      ) == "尚未观看"
    )
    assertEquals(
      "更新至第12话",
      buildFollowingSubtitle(
        progress = card.watchProgress,
        progressState = card.watchProgressState,
        hasHistory = card.hasHistory,
        historicalOnly = card.historicalOnly,
        fallback = card.subtitle,
      ),
    )
  }

  @Test
  fun `confirmed empty user status is labelled unwatched`() {
    val card =
      applyFollowingUserStatus(
        followedCard,
        BangumiUserStatus(followed = true, watchProgress = null),
      )

    assertEquals(BangumiWatchProgressState.NO_RECORD, card.watchProgressState)
    assertEquals(
      "尚未观看",
      buildFollowingSubtitle(
        progress = card.watchProgress,
        progressState = card.watchProgressState,
        hasHistory = card.hasHistory,
        historicalOnly = card.historicalOnly,
      ),
    )
  }

  @Test
  fun `season status replaces latest episode with watched episode`() {
    val progress =
      BangumiWatchProgress(
        episodeId = 456L,
        episodeIndex = "8",
        positionMs = 30_000L,
        percent = 25,
      )
    val card =
      applyFollowingUserStatus(
        followedCard,
        BangumiUserStatus(followed = true, watchProgress = progress),
      )

    assertEquals(BangumiWatchProgressState.RESOLVED, card.watchProgressState)
    assertEquals(456L, card.episodeId)
    assertEquals("https://www.bilibili.com/bangumi/play/ep456", card.videoUrl)
    assertEquals("看到第8话 · 25%", buildFollowingSubtitle(
      progress = card.watchProgress,
      progressState = card.watchProgressState,
      hasHistory = card.hasHistory,
      historicalOnly = card.historicalOnly,
    ))
  }
}

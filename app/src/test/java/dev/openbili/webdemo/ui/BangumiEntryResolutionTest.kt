package dev.openbili.webdemo.ui

import dev.openbili.webdemo.BangumiPlaybackStore
import dev.openbili.webdemo.api.BangumiWatchProgress
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BangumiEntryResolutionTest {
  @Test
  fun `heartbeat keeps the actual pgc season subtype`() {
    assertEquals(1, pgcPlaybackSubType(0))
    assertEquals(2, pgcPlaybackSubType(2))
    assertEquals(4, pgcPlaybackSubType(4))
    assertEquals(7, pgcPlaybackSubType(7))
  }

  private val defaultCard =
    SpaceContentCard(
      id = "bangumi:12345",
      title = "Test Anime",
      subtitle = "更新至第161话",
      coverUrl = "https://i0.hdslb.com/cover.jpg",
      videoUrl = "https://www.bilibili.com/bangumi/play/ep999",
      seasonId = 12345L,
      episodeId = 999L,
      kind = SpaceContentKind.BANGUMI,
    )

  // ── 服务器观看进度具有权威性 ───────────────────────────────────────

  @Test
  fun `server watch progress overrides local selection`() {
    val cardWithProgress =
      defaultCard.copy(
        watchProgress =
          BangumiWatchProgress(
            episodeId = 152L,
            episodeIndex = "152",
            positionMs = 103_000L,
            percent = 7,
          )
      )
    val localSelection =
      BangumiPlaybackStore.Selection(
        seasonId = 12345L,
        episodeId = 888L,
        bvid = "bv888",
      )

    val target =
      resolveBangumiEntryTarget(cardWithProgress, localSelection, allowLocalSelection = true)

    assertEquals(152L, target.card.episodeId)
    assertEquals(103_000L, target.startPositionMs)
    assertTrue(target.serverResumeAuthoritative)
    assertTrue(target.card.videoUrl.contains("ep152"))
  }

  @Test
  fun `server position zero is still authoritative`() {
    val cardWithZeroProgress =
      defaultCard.copy(
        watchProgress =
          BangumiWatchProgress(
            episodeId = 152L,
            episodeIndex = "152",
            positionMs = 0L,
            percent = 0,
          )
      )

    val target = resolveBangumiEntryTarget(cardWithZeroProgress, null, allowLocalSelection = false)

    assertEquals(152L, target.card.episodeId)
    assertEquals(0L, target.startPositionMs)
    assertTrue(target.serverResumeAuthoritative)
  }

  // ── 无服务器记录 → 本地选择 ─────────────────────────────────────────

  @Test
  fun `falls back to local selection when no server progress`() {
    val localSelection =
      BangumiPlaybackStore.Selection(
        seasonId = 12345L,
        episodeId = 777L,
        bvid = "bv777",
      )

    val target = resolveBangumiEntryTarget(defaultCard, localSelection, allowLocalSelection = true)

    assertEquals(777L, target.card.episodeId)
    assertEquals(0L, target.startPositionMs)
    assertFalse(target.serverResumeAuthoritative)
    assertTrue(target.card.videoUrl.contains("ep777"))
  }

  // ── 无服务器、无本地 → 默认卡片 ─────────────────────────────────────

  @Test
  fun `uses default card when neither server nor local selection available`() {
    val target = resolveBangumiEntryTarget(defaultCard, null, allowLocalSelection = false)

    assertEquals(999L, target.card.episodeId)
    assertEquals(0L, target.startPositionMs)
    assertFalse(target.serverResumeAuthoritative)
    assertEquals(defaultCard.videoUrl, target.card.videoUrl)
  }

  @Test
  fun `uses default card when local selection not allowed`() {
    val localSelection =
      BangumiPlaybackStore.Selection(
        seasonId = 12345L,
        episodeId = 777L,
        bvid = "bv777",
      )

    val target = resolveBangumiEntryTarget(defaultCard, localSelection, allowLocalSelection = false)

    assertEquals(999L, target.card.episodeId)
    assertFalse(target.serverResumeAuthoritative)
  }

  @Test
  fun `uses default card when local selection is null`() {
    val target = resolveBangumiEntryTarget(defaultCard, null, allowLocalSelection = true)

    assertEquals(999L, target.card.episodeId)
    assertEquals(0L, target.startPositionMs)
    assertFalse(target.serverResumeAuthoritative)
  }

  // ── 无效服务器分集 → 穿透回退 ──────────────────────────────────────

  @Test
  fun `server progress with episodeId zero falls through to default`() {
    val cardWithInvalidProgress =
      defaultCard.copy(
        watchProgress =
          BangumiWatchProgress(
            episodeId = 0L,
            episodeIndex = "",
            positionMs = 0L,
          )
      )

    val target =
      resolveBangumiEntryTarget(cardWithInvalidProgress, null, allowLocalSelection = false)

    // episodeId = 0L 无效，因此穿透回退到默认
    assertEquals(999L, target.card.episodeId)
    assertEquals(0L, target.startPositionMs)
    assertFalse(target.serverResumeAuthoritative)
  }

  // ── 卡片稳定 ID 不改变 ─────────────────────────────────────────────

  @Test
  fun `card stable id remains unchanged regardless of resolution`() {
    val cardWithProgress =
      defaultCard.copy(
        watchProgress =
          BangumiWatchProgress(
            episodeId = 152L,
            episodeIndex = "152",
            positionMs = 103_000L,
          )
      )

    val target = resolveBangumiEntryTarget(cardWithProgress, null, allowLocalSelection = false)

    assertEquals(defaultCard.id, target.card.id)
    assertEquals(defaultCard.coverUrl, target.card.coverUrl)
    assertEquals(defaultCard.title, target.card.title)
  }
}

package dev.openbili.webdemo

import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.SpaceContentCard
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BangumiPlaybackStoreTest {
  private val context
    get() = RuntimeEnvironment.getApplication()

  @Before
  fun clearStore() {
    context.getSharedPreferences("bangumi_playback_selection", 0).edit().clear().commit()
    context.getSharedPreferences("bangumi_local_history", 0).edit().clear().commit()
  }

  @Test
  fun selectionIsSharedBySeasonAcrossSearchAndProfileCards() {
    val searchCard = SpaceContentCard(id = "search:bangumi:42", title = "作品", seasonId = 42)
    val profileCard = SpaceContentCard(id = "bangumi:42", title = "作品", seasonId = 42)
    val episode =
      BangumiEpisode(
        id = 108,
        aid = 208,
        bvid = "BV1EPISODE",
        cid = 308,
        title = "8",
        longTitle = "继续冒险",
        coverUrl = "",
        durationSeconds = 1_400,
      )

    BangumiPlaybackStore.save(context, searchCard, seasonId = 42, episode = episode)

    assertEquals(
      BangumiPlaybackStore.Selection(42, 108, "BV1EPISODE"),
      BangumiPlaybackStore.read(context, profileCard),
    )
  }

  @Test
  fun localHistoryReplacesAnOlderEpisodeFromTheSameSeason() {
    val indexCard =
      SpaceContentCard(
        id = "bangumi-index:42",
        title = "索引作品",
        coverUrl = "https://example.com/season.jpg",
        seasonId = 42,
        seasonType = 4,
      )
    val first = episode(id = 101, title = "第一话")
    val second = episode(id = 102, title = "第二话")

    BangumiLocalHistoryStore.record(
      context,
      accountMid = 100,
      sourceCard = indexCard,
      seasonId = 42,
      episode = first,
      positionMs = 30_000,
      durationMs = 1_000_000,
      viewedAt = 100,
    )
    BangumiLocalHistoryStore.record(
      context,
      accountMid = 100,
      sourceCard = indexCard,
      seasonId = 42,
      episode = second,
      positionMs = 40_000,
      durationMs = 1_000_000,
      viewedAt = 200,
    )

    val stored = BangumiLocalHistoryStore.read(context, accountMid = 100).single()
    assertEquals(102L, stored.episodeId)
    assertEquals(42L, stored.seasonId)
    assertEquals(4, stored.seasonType)
    assertEquals(40_000L, stored.positionMs)
    assertEquals(200L, stored.viewedAt)
  }

  @Test
  fun localHistoryIsSeparatedByAccount() {
    val card = SpaceContentCard(id = "bangumi:42", title = "作品", seasonId = 42)
    BangumiLocalHistoryStore.record(
      context,
      accountMid = 100,
      sourceCard = card,
      seasonId = 42,
      episode = episode(id = 101, title = "账号甲记录"),
      positionMs = 10_000,
      durationMs = 1_000_000,
    )
    BangumiLocalHistoryStore.record(
      context,
      accountMid = 200,
      sourceCard = card,
      seasonId = 42,
      episode = episode(id = 102, title = "账号乙记录"),
      positionMs = 20_000,
      durationMs = 1_000_000,
    )

    assertEquals(101L, BangumiLocalHistoryStore.read(context, 100).single().episodeId)
    assertEquals(102L, BangumiLocalHistoryStore.read(context, 200).single().episodeId)
  }

  private fun episode(id: Long, title: String) =
    BangumiEpisode(
      id = id,
      aid = id + 100,
      bvid = "BV$id",
      cid = id + 200,
      title = title,
      longTitle = title,
      coverUrl = "https://example.com/$id.jpg",
      durationSeconds = 1_000,
    )
}

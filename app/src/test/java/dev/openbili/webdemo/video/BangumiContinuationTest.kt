package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.BangumiSection
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BangumiContinuationTest {
  @Test
  fun mainEpisodeContinuationUsesTheNextMainEpisode() {
    val first = episode(1, "1", "第一话")
    val second = episode(2, "2", "第二话")
    val third = episode(3, "3", "第三话")
    val page = page(currentEpisodeId = second.id, episodes = listOf(first, second, third))

    assertEquals(third, page.nextPlayableEpisode())
    assertEquals("第2话 · 第二话", page.currentEpisodeTitle())
  }

  @Test
  fun sectionContinuationDoesNotJumpIntoMainEpisodes() {
    val main = episode(1, "1", "正片")
    val pv = episode(20, "PV1", "先导")
    val trailer = episode(21, "PV2", "终版")
    val page =
      page(
        currentEpisodeId = pv.id,
        episodes = listOf(main),
        sections = listOf(BangumiSection(9, "PV", listOf(pv, trailer))),
      )

    assertEquals(trailer, page.nextPlayableEpisode())
  }

  @Test
  fun finalEpisodeHasNoContinuation() {
    val only = episode(1, "1", "第一话")

    assertNull(page(currentEpisodeId = only.id, episodes = listOf(only)).nextPlayableEpisode())
  }

  private fun page(
    currentEpisodeId: Long,
    episodes: List<BangumiEpisode>,
    sections: List<BangumiSection> = emptyList(),
  ) =
    BangumiPageUi(
      sourceCard =
        SpaceContentCard(
          id = "season",
          title = "测试番剧",
          subtitle = "",
          kind = SpaceContentKind.BANGUMI,
        ),
      season =
        BangumiSeason(
          seasonId = 1,
          mediaId = 1,
          title = "测试番剧",
          coverUrl = "season-cover",
          evaluate = "",
          typeName = "番剧",
          areas = emptyList(),
          styles = emptyList(),
          publishText = "",
          rating = null,
          ratingCount = 0,
          followCount = 0,
          viewCount = 0,
          danmakuCount = 0,
          followed = false,
          episodes = episodes,
          seasons = emptyList(),
          sections = sections,
        ),
      loading = false,
      error = null,
      currentEpisodeId = currentEpisodeId,
      posterVisible = true,
    )

  private fun episode(id: Long, title: String, longTitle: String) =
    BangumiEpisode(
      id = id,
      aid = id,
      bvid = "BV$id",
      cid = id,
      title = title,
      longTitle = longTitle,
      coverUrl = "cover-$id",
      durationSeconds = 60,
    )
}

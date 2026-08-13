package dev.openbili.webdemo.ui

import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.BangumiExploreCardStyle
import dev.openbili.webdemo.api.BangumiExploreCategory
import dev.openbili.webdemo.api.BangumiExploreItem
import dev.openbili.webdemo.api.BangumiExplorePage
import dev.openbili.webdemo.api.BangumiExploreSection
import dev.openbili.webdemo.api.BangumiExploreSectionKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimeExploreContentTest {
  @Test
  fun contentStartsBelowSafeInsetAndTopBar() {
    assertEquals(102.dp, bangumiExploreContentTopPadding(24.dp))
  }

  @Test
  fun groupsTheAnimePageByModuleRoleAndRemovesTimeline() {
    val hot = item("hot")
    val ranking = item("ranking")
    val timeline = item("timeline")
    val recommended = item("recommended")
    val feed = item("feed")
    val other = item("other")
    val page =
      BangumiExplorePage(
        category = BangumiExploreCategory.ANIME,
        sections =
          listOf(
            section("hot", BangumiExploreSectionKind.HOT, hot),
            section("timeline", BangumiExploreSectionKind.TIMELINE, timeline),
            section("ranking", BangumiExploreSectionKind.RANKING, ranking),
            section("recommended", BangumiExploreSectionKind.RECOMMENDATION, recommended),
            section("feed", BangumiExploreSectionKind.FEED, feed),
            section("other", BangumiExploreSectionKind.OTHER, other),
          ),
      )

    val groups = animeExploreContentGroups(page)

    assertEquals(listOf(hot), groups.hot)
    assertEquals(listOf(ranking), groups.ranking)
    assertEquals(listOf(feed), groups.recommendations)
  }

  private fun section(
    id: String,
    kind: BangumiExploreSectionKind,
    item: BangumiExploreItem,
  ) = BangumiExploreSection(stableId = id, title = id, items = listOf(item), kind = kind)

  private fun item(id: String) =
    BangumiExploreItem(
      stableId = id,
      title = id,
      subtitle = "",
      coverUrl = "https://example.com/$id.webp",
      targetUrl = "https://www.bilibili.com/bangumi/play/ss1",
      seasonId = 1L,
      episodeId = 0L,
      style = BangumiExploreCardStyle.LANDSCAPE,
    )
}

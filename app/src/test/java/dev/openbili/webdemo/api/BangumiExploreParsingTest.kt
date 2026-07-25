package dev.openbili.webdemo.api

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BangumiExploreParsingTest {
  @Test
  fun normalizesLandscapeAndPosterModulesWithoutIncludingFilters() {
    val response =
      JSONObject(
        """
        {
          "code": 0,
          "data": {"modules": [
            {"style":"web_index_v3","items":[{"title":"类型"}]},
            {"style":"web_hot_v3","title":"热门推荐","items":[{
              "title":"横封面","cover":"//i0.hdslb.com/hot.png",
              "big_cover":"//i0.hdslb.com/hot-hero.png",
              "hover":{"img":"//i0.hdslb.com/hot-hover.png"},
              "link":"https://www.bilibili.com/bangumi/play/ep101"
            }]},
            {"style":"web_rank_v3","title":"排行榜","items":[{
              "title":"竖封面","cover":"//i0.hdslb.com/rank.png",
              "sub_title":"全站热度","link":"https://www.bilibili.com/bangumi/play/ss202"
            }]}
          ]}
        }
        """.trimIndent()
      )

    val page = BiliApi.parseBangumiExplorePage(response, BangumiExploreCategory.ANIME)

    assertEquals(listOf("热门推荐", "排行榜"), page.sections.map { it.title })
    assertEquals(BangumiExploreSectionKind.HOT, page.sections[0].kind)
    assertEquals(BangumiExploreSectionKind.RANKING, page.sections[1].kind)
    assertEquals(BangumiExploreCardStyle.LANDSCAPE, page.sections[0].items.single().style)
    assertEquals("https://i0.hdslb.com/hot-hover.png", page.sections[0].items.single().heroCoverUrl)
    assertEquals(BangumiExploreCardStyle.POSTER, page.sections[1].items.single().style)
    assertEquals(202L, page.sections[1].items.single().seasonId)
  }

  @Test
  fun expandsTimelineEpisodesAndKeepsTheirParentLabel() {
    val response =
      JSONObject(
        """
        {"code":0,"data":{"modules":[{
          "style":"web_timeline_v3","title":"时间表","items":[{
            "text":"今天更新","episodes":[{
              "title":"第 3 集","cover":"//i0.hdslb.com/ep.png",
              "link":"https://www.bilibili.com/bangumi/play/ep303"
            }]
          }]
        }]}}}
        """.trimIndent()
      )

    val item =
      BiliApi.parseBangumiExplorePage(response, BangumiExploreCategory.ANIME)
        .sections
        .single()
        .items
        .single()

    assertEquals("第 3 集", item.title)
    assertEquals("今天更新", item.subtitle)
    assertEquals(BangumiExploreCardStyle.POSTER, item.style)
    assertTrue(item.coverUrl.startsWith("https://"))
    assertEquals(
      BangumiExploreSectionKind.TIMELINE,
      BiliApi.parseBangumiExplorePage(response, BangumiExploreCategory.ANIME).sections.single().kind,
    )
  }

  @Test
  fun parsesFeedSubItemsAsPosterRecommendationsWithRatings() {
    val response =
      JSONObject(
        """
        {"code":0,"data":{"modules":[{"style":"web_feed_v3","title":"猜你喜欢","items":[{
          "sub_items":[{"title":"推荐番剧","cover":"//i0.hdslb.com/recommend.png",
            "card_style":"v_card","rating":"9.9","rating_count":123,
            "link":"https://www.bilibili.com/bangumi/play/ss404"}]
        }]}]}}
        """.trimIndent()
      )

    val item = BiliApi.parseBangumiExplorePage(response, BangumiExploreCategory.ANIME).sections.single().items.single()

    assertEquals(BangumiExploreSectionKind.FEED, BiliApi.parseBangumiExplorePage(response, BangumiExploreCategory.ANIME).sections.single().kind)
    assertEquals(BangumiExploreCardStyle.POSTER, item.style)
    assertEquals("https://i0.hdslb.com/recommend.png", item.coverUrl)
    assertEquals(9.9, item.rating ?: 0.0, 0.001)
    assertEquals(123L, item.ratingCount)
  }

  @Test
  fun keepsAllFeedItemsForIncrementalPresentation() {
    val feedItems = JSONArray()
    repeat(28) { index ->
      feedItems.put(
        JSONObject()
          .put("title", "推荐$index")
          .put("cover", "//i0.hdslb.com/feed-$index.png")
          .put("link", "https://www.bilibili.com/bangumi/play/ss${index + 1}"),
      )
    }
    val response =
      JSONObject()
        .put("code", 0)
        .put(
          "data",
          JSONObject().put(
            "modules",
            JSONArray().put(JSONObject().put("style", "web_feed_v3").put("items", feedItems)),
          ),
        )

    assertEquals(
      28,
      BiliApi.parseBangumiExplorePage(response, BangumiExploreCategory.ANIME).sections.single().items.size,
    )
  }
}

package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BangumiRecommendationParsingTest {
  @Test
  fun parsesAnimeBannerModuleInServerOrder() {
    val response =
      JSONObject(
        """
        {
          "code": 0,
          "data": {
            "modules": [
              {
                "style": "web_index_v3",
                "items": [{"title": "不是轮播"}]
              },
              {
                "style": "web_banner_v3",
                "items": [
                  {
                    "title": "假面骑士ZZZ",
                    "big_cover": "//i0.hdslb.com/banner.png",
                    "cover": "//i0.hdslb.com/card.png",
                    "link": "https://www.bilibili.com/bangumi/play/ep3781144",
                    "season_id": 109700,
                    "episode_id": 3781144
                  },
                  {
                    "title": "刀剑神域",
                    "big_cover": "https://i0.hdslb.com/sao-banner.png",
                    "cover": "https://i0.hdslb.com/sao-card.png",
                    "link": "https://www.bilibili.com/bangumi/play/ss4452"
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()
      )

    val items = BiliApi.parseBangumiRecommendations(response)

    assertEquals(2, items.size)
    assertEquals("season:109700", items[0].stableId)
    assertEquals(109700L, items[0].seasonId)
    assertEquals(3781144L, items[0].episodeId)
    assertEquals("https://i0.hdslb.com/banner.png", items[0].bannerUrl)
    assertEquals("https://i0.hdslb.com/card.png", items[0].cardUrl)
    assertEquals("season:4452", items[1].stableId)
    assertEquals(1, items[1].position)
  }

  @Test
  fun missingBannerModuleReturnsNoRecommendations() {
    val response =
      JSONObject(
        """{"code":0,"data":{"modules":[{"style":"web_index_v3","items":[]}]}}"""
      )

    assertEquals(emptyList<BangumiRecommendation>(), BiliApi.parseBangumiRecommendations(response))
  }

  @Test
  fun parsesV2MovieBannerAndResolvesEpisodeFromLink() {
    val response =
      JSONObject(
        """
        {
          "code": 0,
          "data": {
            "modules": [{
              "style": "web_banner_v2",
              "items": [{
                "title": "电影推荐",
                "cover": "//i0.hdslb.com/movie.png",
                "link": "https://www.bilibili.com/bangumi/play/ep4448452"
              }]
            }]
          }
        }
        """.trimIndent()
      )

    val item =
      BiliApi.parseBangumiRecommendations(
        json = response,
        bannerStyle = "web_banner_v2",
        sourceName = "movie",
      ).single()

    assertEquals("movie:episode:4448452", item.stableId)
    assertEquals(4448452L, item.episodeId)
    assertEquals("https://i0.hdslb.com/movie.png", item.bannerUrl)
  }

  @Test
  fun keepsLiveBannerWithoutBangumiIdentity() {
    val response =
      JSONObject(
        """
        {
          "code": 0,
          "data": {
            "modules": [{
              "style": "web_banner_v2",
              "items": [{
                "title": "直播推荐",
                "cover": "//i0.hdslb.com/live.png",
                "link": "https://live.bilibili.com/23192840"
              }]
            }]
          }
        }
        """.trimIndent()
      )

    val item =
      BiliApi.parseBangumiRecommendations(
        json = response,
        bannerStyle = "web_banner_v2",
        sourceName = "variety",
      ).single()

    assertTrue(item.isLive)
    assertEquals(0L, item.episodeId)
  }
}

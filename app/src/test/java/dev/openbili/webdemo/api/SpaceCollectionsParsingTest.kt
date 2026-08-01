package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpaceCollectionsParsingTest {
  @Test
  fun requestUsesAcceptedPageSize() {
    val url = BiliApi.spaceCollectionsUrl(17679937L)

    assertTrue(url.contains("page_size=20"))
    assertFalse(url.contains("page_size=30"))
  }

  @Test
  fun parsesCurrentNestedItemsListsShape() {
    val response =
      JSONObject(
        """
        {
          "code": 0,
          "data": {
            "items_lists": {
              "seasons_list": [{
                "meta": {
                  "season_id": 8454680,
                  "name": "合集·回家吃饭系列",
                  "description": "合集简介",
                  "cover": "http://i0.hdslb.com/collection.jpg",
                  "total": 35
                }
              }],
              "series_list": [{
                "meta": {
                  "series_id": 9527,
                  "name": "系列·测试系列",
                  "description": "系列简介",
                  "cover": "//i1.hdslb.com/series.jpg",
                  "total": 13
                }
              }]
            }
          }
        }
        """
          .trimIndent()
      )

    val cards = BiliApi.parseSpaceCollections(response)

    assertEquals(listOf("seasons_list:8454680", "series_list:9527"), cards.map { it.id })
    assertEquals(listOf("合集·回家吃饭系列", "系列·测试系列"), cards.map { it.title })
    assertEquals("https://i0.hdslb.com/collection.jpg", cards[0].coverUrl)
    assertEquals("https://i1.hdslb.com/series.jpg", cards[1].coverUrl)
    assertEquals(listOf(8454680L, 9527L), cards.map { it.collectionId })
    assertEquals(
      listOf(SpaceCollectionType.SEASON, SpaceCollectionType.SERIES),
      cards.map { it.collectionType },
    )
    assertEquals(listOf(35, 13), cards.map { it.collectionTotal })
  }

  @Test
  fun retainsLegacyTopLevelListCompatibility() {
    val response =
      JSONObject(
        """
        {
          "code": 0,
          "data": {
            "seasons_list": [],
            "series_list": [{
              "series_id": 42,
              "title": "旧结构系列",
              "description": "旧结构简介"
            }]
          }
        }
        """
          .trimIndent()
      )

    val card = BiliApi.parseSpaceCollections(response).single()

    assertEquals("series_list:42", card.id)
    assertEquals("旧结构系列", card.title)
    assertEquals("旧结构简介", card.subtitle)
  }

  @Test
  fun buildsTypeSpecificDetailUrls() {
    val season =
      SpaceContentCard(
        id = "seasons_list:8454680",
        title = "合集",
        collectionId = 8454680,
        collectionType = SpaceCollectionType.SEASON,
      )
    val series =
      SpaceContentCard(
        id = "series_list:4684427",
        title = "系列",
        collectionId = 4684427,
        collectionType = SpaceCollectionType.SERIES,
      )

    val seasonUrl = BiliApi.spaceCollectionVideosUrl(17679937L, season, page = 2)
    val seriesUrl = BiliApi.spaceCollectionVideosUrl(546195L, series, page = 3)

    assertTrue(seasonUrl.contains("/seasons_archives_list"))
    assertTrue(seasonUrl.contains("season_id=8454680"))
    assertTrue(seasonUrl.contains("page_num=2&page_size=30"))
    assertTrue(seriesUrl.contains("/x/series/archives"))
    assertTrue(seriesUrl.contains("series_id=4684427"))
    assertTrue(seriesUrl.contains("pn=3&ps=30"))
  }

  @Test
  fun parsesCollectionDetailArchivesAndPagination() {
    val response =
      JSONObject(
        """
        {
          "code": 0,
          "data": {
            "archives": [{
              "aid": 123,
              "bvid": "BV1DETAIL123",
              "title": "合集视频",
              "pic": "http://i0.hdslb.com/detail.jpg",
              "duration": 258,
              "pubdate": 1700000000,
              "desc": "视频简介",
              "stat": {"view": 88, "danmaku": 7}
            }],
            "page": {"page_num": 1, "page_size": 30, "total": 35}
          }
        }
        """
          .trimIndent()
      )

    val page =
      BiliApi.parseSpaceCollectionVideos(response, requestedPage = 1, requestedPageSize = 30)

    assertEquals(1, page.cards.size)
    assertEquals("BV1DETAIL123", page.cards.single().bvid)
    assertEquals("合集视频", page.cards.single().title)
    assertEquals(258L, page.cards.single().durationSeconds)
    assertEquals(35, page.total)
    assertTrue(page.hasMore)
  }

  @Test
  fun parsesSeriesPaginationFieldNames() {
    val response =
      JSONObject(
        """
        {
          "code": 0,
          "data": {
            "archives": [{"aid": 456, "bvid": "BV1SERIES456", "title": "系列视频"}],
            "page": {"num": 2, "size": 30, "total": 31}
          }
        }
        """
          .trimIndent()
      )

    val page =
      BiliApi.parseSpaceCollectionVideos(response, requestedPage = 2, requestedPageSize = 30)

    assertEquals(31, page.total)
    assertFalse(page.hasMore)
  }
}

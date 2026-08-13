package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArticleAndHistoryParserTest {
  @Test
  fun bangumiEpisodeIdOnlyMatchesEpisodePlaybackUrls() {
    assertEquals(
      12345L,
      BiliApi.bangumiEpisodeId("https://www.bilibili.com/bangumi/play/ep12345?from=history"),
    )
    assertEquals(null, BiliApi.bangumiEpisodeId("https://www.bilibili.com/bangumi/play/ss12345"))
    assertEquals(null, BiliApi.bangumiEpisodeId("https://www.bilibili.com/video/BV1TEST"))
  }

  @Test
  fun historyRequestKeepsReturnedCursorBusinessSeparateFromFilterType() {
    val url =
      BiliApi.historyCursorUrl(
        HistoryCursor(max = 88, viewAt = 77, business = "pgc"),
        type = "archive",
      )

    assertTrue(url.contains("max=88"))
    assertTrue(url.contains("view_at=77"))
    assertTrue(url.contains("business=pgc"))
    assertTrue(url.contains("type=archive"))
    assertTrue(url.contains("ps=30"))
  }

  @Test
  fun historyParserKeepsVideoAndArticleAndReturnsCursor() {
    val response =
      BiliApi.parseHistoryResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "cursor": {"max": 88, "view_at": 77, "business": "article", "ps": 2},
              "list": [
                {
                  "title": "视频历史",
                  "cover": "//i0.hdslb.com/video.jpg",
                  "author_name": "UP",
                  "author_mid": 11,
                  "duration": 120,
                  "view_at": 66,
                  "history": {"business": "archive", "oid": 1, "bvid": "BV1TEST", "cid": 2}
                },
                {
                  "title": "专栏历史",
                  "cover": "//i0.hdslb.com/article.jpg",
                  "author_name": "作者",
                  "author_mid": 22,
                  "view_at": 55,
                  "history": {"business": "article-list", "oid": 99}
                }
              ]
            }
          }
          """
        )
      )

    assertEquals(2, response.items.size)
    assertTrue(response.items[0] is AccountHistoryItem.Video)
    assertTrue(response.items[1] is AccountHistoryItem.Article)
    assertEquals(88L, response.cursor.max)
    assertEquals(77L, response.cursor.viewAt)
    assertEquals("article", response.cursor.business)
    assertTrue(response.hasMore)
  }

  @Test
  fun historyParserKeepsLiveRoomPresentationFields() {
    val response =
      BiliApi.parseHistoryResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "list": [{
                "title": "测试直播",
                "cover": "//i0.hdslb.com/live-cover.jpg",
                "keyframe": "//i0.hdslb.com/live-keyframe.jpg",
                "author_name": "主播甲",
                "author_mid": 123,
                "author_face": "//i0.hdslb.com/live-face.jpg",
                "tag_name": "单机游戏",
                "parent_area_name": "游戏",
                "live_status": 1,
                "view_at": 1700000000,
                "history": {"business": "live", "oid": 456}
              }]
            }
          }
          """
        )
      )

    val item = response.items.single() as AccountHistoryItem.Live
    assertEquals(456L, item.roomId)
    assertEquals("测试直播", item.title)
    assertEquals(123L, item.anchorUid)
    assertEquals("主播甲", item.anchorName)
    assertEquals("https://i0.hdslb.com/live-face.jpg", item.anchorFace)
    assertEquals("https://i0.hdslb.com/live-cover.jpg", item.coverUrl)
    assertEquals("https://i0.hdslb.com/live-keyframe.jpg", item.keyframeUrl)
    assertEquals("单机游戏", item.areaName)
    assertEquals("游戏", item.parentAreaName)
    assertEquals(1, item.liveStatus)
    assertEquals(1700000000L, item.viewAt)
  }

  @Test
  fun historyParserKeepsPgcIdentityAndMovieLabel() {
    val response =
      BiliApi.parseHistoryResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "list": [{
                "title": "测试电影",
                "badge": "电影",
                "cover": "//i0.hdslb.com/movie.jpg",
                "uri": "https://www.bilibili.com/bangumi/play/ep7788",
                "view_at": 66,
                "history": {
                  "business": "pgc",
                  "oid": 7788,
                  "epid": 7788,
                  "season_id": 9900,
                  "bvid": "BV1MOVIE",
                  "cid": 2
                }
              }]
            }
          }
          """
        )
      )

    val item = response.items.single() as AccountHistoryItem.Bangumi
    assertEquals(7788L, item.bangumi.episodeId)
    assertEquals(9900L, item.bangumi.seasonId)
    assertEquals("电影", item.mediaLabel)
    assertEquals("https://www.bilibili.com/bangumi/play/ep7788", item.bangumi.videoUrl)
    assertEquals("https://i0.hdslb.com/movie.jpg", item.card.coverUrl)
    assertEquals("", item.bangumi.coverUrl)
  }

  @Test
  fun historyParserDoesNotPromoteArchiveAnimationTagsToPgc() {
    val response =
      BiliApi.parseHistoryResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "list": [{
                "title": "【独家】《非人哉》第24集",
                "tag_name": "国产动画",
                "bvid": "BV1ARCHIVE",
                "history": {
                  "business": "archive",
                  "oid": 116715904108484
                }
              }]
            }
          }
          """
        )
      )

    assertEquals(1, response.items.size)
    assertTrue(response.items.single() is AccountHistoryItem.Video)
    assertFalse(response.items.single() is AccountHistoryItem.Bangumi)
  }

  @Test
  fun historyParserNeverTreatsPgcAidAsEpisodeId() {
    val response =
      BiliApi.parseHistoryResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "list": [{
                "title": "[10月/完结] 葬送的芙莉莲 28",
                "uri": "https://www.bilibili.com/bangumi/play/ss46089",
                "history": {
                  "business": "pgc",
                  "oid": 1802760232,
                  "bvid": "BV1FRIEREN",
                  "cid": 1509379376
                }
              }]
            }
          }
          """
        )
      )

    val item = response.items.single() as AccountHistoryItem.Bangumi
    assertEquals(0L, item.bangumi.episodeId)
    assertEquals(46089L, item.bangumi.seasonId)
    assertEquals(1802760232L, item.bangumi.aid)
    assertEquals("BV1FRIEREN", item.bangumi.bvid)
    assertEquals("https://www.bilibili.com/video/BV1FRIEREN", item.bangumi.videoUrl)
    assertEquals("history:pgc:BV1FRIEREN", item.bangumi.id)
  }

  @Test
  fun historyParserRetainsPgcRowsWithOnlyArchiveIdentity() {
    val response =
      BiliApi.parseHistoryResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "list": [{
                "title": "名侦探柯南 1267",
                "uri": "https://www.bilibili.com/video/BV1CONAN",
                "history": {
                  "business": "pgc",
                  "oid": 116940316084672,
                  "bvid": "BV1CONAN"
                }
              }]
            }
          }
          """
        )
      )

    val item = response.items.single() as AccountHistoryItem.Bangumi
    assertEquals(0L, item.bangumi.episodeId)
    assertEquals(0L, item.bangumi.seasonId)
    assertEquals("https://www.bilibili.com/video/BV1CONAN", item.bangumi.videoUrl)
  }

  @Test
  fun historyParserRoutesTypedArchiveRowsToDramaPage() {
    val response =
      BiliApi.parseHistoryResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "list": [{
                "title": "[纪录片] 荒野独居 澳洲版 EP10",
                "uri": "https://www.bilibili.com/video/BV1DOC",
                "history": {
                  "business": "archive",
                  "oid": 42,
                  "bvid": "BV1DOC"
                }
              }]
            }
          }
          """
        )
      )

    val item = response.items.single() as AccountHistoryItem.Bangumi
    assertEquals("纪录片", item.mediaLabel)
    assertEquals(SpaceContentKind.DRAMA, item.bangumi.kind)
  }

  @Test
  fun bangumiIdentityParsesSeasonAndEpisodeRedirects() {
    assertEquals(46089L, BiliApi.bangumiIdentityFromUrl("https://www.bilibili.com/bangumi/play/ss46089").seasonId)
    assertEquals(12345L, BiliApi.bangumiIdentityFromUrl("https://www.bilibili.com/bangumi/play/ep12345").episodeId)
  }

  @Test
  fun bangumiSearchParserBuildsPortraitDestinationCards() {
    val response =
      BiliApi.parseBangumiSearchResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "page": 1,
              "pagesize": 20,
              "numResults": 21,
              "result": [{
                "season_id": 46089,
                "title": "<em class=\"keyword\">葬送的芙莉莲</em>",
                "cover": "//i0.hdslb.com/frieren.jpg",
                "index_show": "全28话",
                "eps": [{"id": 12345, "url": "https://www.bilibili.com/bangumi/play/ep12345"}]
              }]
            }
          }
          """
        ),
        requestedPage = 1,
        kind = SpaceContentKind.BANGUMI,
      )

    val card = response.cards.single()
    assertEquals("葬送的芙莉莲", card.title)
    assertEquals(46089L, card.seasonId)
    assertEquals(12345L, card.episodeId)
    assertEquals("https://i0.hdslb.com/frieren.jpg", card.coverUrl)
    assertTrue(response.hasMore)
  }

  @Test
  fun historyPgcLabelsKeepTheNativeBangumiCategories() {
    assertEquals("番剧", BiliApi.historyPgcMediaLabel("番剧", ""))
    assertEquals("国创", BiliApi.historyPgcMediaLabel("", "国创"))
    assertEquals("国创", BiliApi.historyPgcMediaLabel("动画", "国创"))
    assertEquals("港澳台番剧", BiliApi.historyPgcMediaLabel("番剧", "仅限港澳台地区"))
    assertEquals("电影", BiliApi.historyPgcMediaLabel("剧场版", ""))
    assertEquals("电视剧", BiliApi.historyPgcMediaLabel("电视剧", ""))
    assertEquals("纪录片", BiliApi.historyPgcMediaLabel("纪录片", ""))
    assertEquals("综艺", BiliApi.historyPgcMediaLabel("综艺", ""))
  }

  @Test
  fun nativeGuochuangHintOverridesGenericAnimationAndStaleSeasonType() {
    val response =
      BiliApi.parseHistoryResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "has_more": false,
              "list": [{
                "title": "记忆管理局",
                "type_name": "动画",
                "tag_name": "国创",
                "view_at": 1700000001,
                "history": {
                  "business": "pgc",
                  "season_id": 73957,
                  "season_type": 1,
                  "epid": 4791311
                }
              }]
            }
          }
          """
        )
      )

    val item = response.items.single() as AccountHistoryItem.Bangumi
    assertEquals("国创", item.mediaLabel)
    assertEquals(4, item.bangumi.seasonType)
  }

  @Test
  fun historyParserContinuesWhenFilteredPageIsNotFull() {
    val response =
      BiliApi.parseHistoryResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "cursor": {"max": 0, "view_at": 1700000000, "business": "archive", "ps": 30},
              "list": [{
                "title": "不足三十条的筛选页",
                "view_at": 1700000001,
                "history": {"business": "archive", "oid": 1, "bvid": "BV1TEST", "cid": 2}
              }]
            }
          }
          """
        )
      )

    assertEquals(1, response.items.size)
    assertTrue(response.hasMore)
  }

  @Test
  fun historyParserStopsOnEmptyTerminalPage() {
    val response =
      BiliApi.parseHistoryResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "cursor": {"max": 0, "view_at": 0, "business": "", "ps": 30},
              "list": []
            }
          }
          """
        )
      )

    assertFalse(response.hasMore)
  }

  @Test
  fun articleSearchParserDecodesTextAndPagination() {
    val response =
      BiliApi.parseArticleSearchResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "page": 1,
              "numPages": 2,
              "result": [{
                "id": 42,
                "title": "&lt;测试&gt;专栏",
                "desc": "简介&amp;说明",
                "image_urls": ["//i0.hdslb.com/article.png"],
                "author": "作者",
                "mid": 7,
                "category_name": "数码",
                "pub_time": 100,
                "view": 200,
                "like": 30,
                "reply": 4
              }]
            }
          }
          """
        ),
        requestedPage = 1,
      )

    assertEquals("<测试>专栏", response.items.single().title)
    assertEquals("简介&说明", response.items.single().summary)
    assertTrue(response.items.single().coverUrl.startsWith("https://"))
    assertTrue(response.hasMore)
  }

  @Test
  fun articlePageParserBuildsNativeTextImageAndCodeBlocks() {
    val state =
      """
      {
        "detail": {
          "modules": [
            {"module_type":"MODULE_TYPE_TITLE","module_title":{"text":"正文标题"}},
            {"module_type":"MODULE_TYPE_AUTHOR","module_author":{"name":"正文作者","mid":9,"pub_ts":"123"}},
            {"module_type":"MODULE_TYPE_CONTENT","module_content":{"paragraphs":[
              {"para_type":1,"text":{"nodes":[{"word":{"words":"第一段","font_size":17,"style":{"bold":false}}}]}},
              {"para_type":2,"pic":{"pics":[{"url":"//i0.hdslb.com/body.png","width":800,"height":600}]}},
              {"para_type":7,"code":{"lang":"language-kotlin","content":"val answer = 42"}}
            ]}}
          ]
        }
      }
      """
        .trimIndent()
    val html = "<script>window.__INITIAL_STATE__=$state;(function(){})();</script>"
    val detail = BiliApi.parseArticlePage(html, ArticleItem(id = 12, title = "占位"))

    assertEquals("正文标题", detail.article.title)
    assertEquals("正文作者", detail.article.authorName)
    assertEquals(3, detail.blocks.size)
    assertTrue(detail.blocks[0] is ArticleBlock.Text)
    assertTrue(detail.blocks[1] is ArticleBlock.Image)
    assertTrue(detail.blocks[2] is ArticleBlock.Code)
    assertFalse((detail.blocks[0] as ArticleBlock.Text).heading)
  }

  @Test
  fun modernOpusInitialStateParsesCommentIdentityEmoteAndVideoCard() {
    val state =
      """
      {
        "detail": {
          "basic": {"comment_type":12,"comment_id_str":"33179525"},
          "modules": [{
            "module_type":"MODULE_TYPE_CONTENT",
            "module_content":{"paragraphs":[
              {"para_type":1,"text":{"nodes":[
                {"rich":{"text":"[doge]","type":"RICH_TEXT_NODE_TYPE_EMOJI","icon_url":"//i0.hdslb.com/doge.png"}},
                {"rich":{"text":"视频链接","type":"RICH_TEXT_NODE_TYPE_WEB","jump_url":"https://www.bilibili.com/video/BV1ERTR6zECb"}}
              ]}}
            ]}
          }]
        }
      }
      """
        .trimIndent()
    val html = "<script>window.__INITIAL_STATE__ = $state;</script><div>后续页面内容</div>"

    val detail = BiliApi.parseArticlePage(html, ArticleItem(id = 12, title = "占位"))

    assertEquals(33179525L, detail.commentOid)
    assertEquals(12, detail.commentType)
    assertEquals("https://i0.hdslb.com/doge.png", (detail.blocks[0] as ArticleBlock.Text).emotes["[doge]"])
    assertEquals("BV1ERTR6zECb", (detail.blocks[1] as ArticleBlock.Video).bvid)
  }

  @Test
  fun opusApiFallbackUsesDataItemShape() {
    val response =
      JSONObject(
        """
        {
          "code": 0,
          "data": {"item": {
            "basic": {"comment_type": 12, "comment_id_str": "7654"},
            "modules": [
              {"module_type":"MODULE_TYPE_TITLE","module_title":{"text":"接口正文"}},
              {"module_type":"MODULE_TYPE_CONTENT","module_content":{"paragraphs":[
                {"para_type":1,"text":{"nodes":[{"word":{"words":"兜底内容"}}]}}
              ]}}
            ]
          }}
        }
        """.trimIndent()
      )

    val detail = BiliApi.parseArticleJson(response, ArticleItem(id = 7, title = "占位"))

    assertEquals("接口正文", detail.article.title)
    assertEquals(7654L, detail.commentOid)
    assertEquals("兜底内容", (detail.blocks.single() as ArticleBlock.Text).content)
  }
}

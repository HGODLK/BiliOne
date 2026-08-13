package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpaceDynamicParsingTest {
  @Test
  fun `home dynamic portal parses nested uploader page and cursor`() {
    val response =
      BiliApi.parseHomeDynamicUploaders(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "live_users": [{"mid": 42}],
              "up_list": {
                "has_more": true,
                "offset": "opaque-next-page",
                "items": [{
                  "mid": 42,
                  "uname": "更新的UP主",
                  "face": "//i.test/face.jpg",
                  "has_update": true
                }]
              }
            }
          }
          """
        )
      )

    assertEquals(1, response.items.size)
    assertEquals("更新的UP主", response.items.single().name)
    assertTrue(response.items.single().hasUpdate)
    assertTrue(response.items.single().live)
    assertEquals("opaque-next-page", response.offset)
    assertTrue(response.hasMore)
  }

  @Test
  fun `video dynamic uses archive aid as video comment target`() {
    val response =
      BiliApi.parseSpaceDynamics(
        JSONObject(
          """
          {
            "data": {
              "has_more": true,
              "offset": "next-page",
              "items": [{
                "id_str": "12345",
                "basic": {"comment_id_str": "12345", "comment_type": 17},
                "modules": {
                  "module_author": {"mid": 42, "name": "测试用户", "face": "//i.test/a.jpg", "pub_ts": 1700000000},
                  "module_dynamic": {
                    "desc": {"text": "视频动态正文"},
                    "major": {"archive": {
                      "aid": 9988,
                      "bvid": "BV1xx411c7mD",
                      "title": "视频标题",
                      "desc": "视频简介",
                      "cover": "//i.test/cover.jpg",
                      "duration_text": "03:14",
                      "stat": {"play": "1.2万", "danmaku": "88"}
                    }}
                  },
                  "module_stat": {
                    "comment": {"count": 7},
                    "like": {"count": 9, "status": true},
                    "forward": {"count": 2}
                  },
                  "module_tag": {"text": "置顶", "tag_type": 1}
                }
              }]
            }
          }
          """
            .trimIndent()
        )
      )

    assertTrue(response.hasMore)
    assertEquals("next-page", response.offset)
    val item = response.items.single()
    assertEquals(9988L, item.commentOid)
    assertEquals(1, item.commentType)
    assertEquals("BV1xx411c7mD", item.video?.bvid)
    assertEquals("https://i.test/cover.jpg", item.video?.coverUrl)
    assertEquals(7L, item.commentCount)
    assertTrue(item.liked)
    assertTrue(item.pinned)
  }

  @Test
  fun `image dynamic keeps text images and dynamic comment target`() {
    val response =
      BiliApi.parseSpaceDynamics(
        JSONObject(
          """
          {
            "data": {
              "items": [{
                "id_str": "24680",
                "basic": {"comment_id_str": "13579", "comment_type": 17},
                "modules": {
                  "module_author": {"mid": 3, "name": "画师", "pub_ts": 1700000001},
                  "module_dynamic": {
                    "desc": {"text": "两张图片"},
                    "major": {"draw": {"items": [
                      {"src": "http://i.test/1.jpg", "width": 1000, "height": 800},
                      {"src": "//i.test/2.jpg", "width": 900, "height": 900}
                    ]}}
                  }
                }
              }]
            }
          }
          """
            .trimIndent()
        )
      )

    val item = response.items.single()
    assertEquals("两张图片", item.text)
    assertEquals(13579L, item.commentOid)
    assertEquals(17, item.commentType)
    assertEquals(2, item.images.size)
    assertEquals("https://i.test/1.jpg", item.images.first().url)
  }

  @Test
  fun `opus and forwarded bilibili images are normalized and deduplicated`() {
    val response =
      BiliApi.parseSpaceDynamics(
        JSONObject(
          """
          {
            "data": {"items": [{
              "id_str": "97531",
              "modules": {
                "module_author": {"mid": 7, "name": "图片用户"},
                "module_dynamic": {"major": {"opus": {"pics": [
                  {"url": "/bfs/new_dyn/a.jpg", "width": 1200, "height": 1800},
                  {"img_src": "//i0.hdslb.com/bfs/new_dyn/b.jpg", "img_width": 800, "img_height": 600}
                ]}}}
              },
              "orig": {"modules": {"module_dynamic": {"major": {"draw": {"items": [
                {"src": "//i0.hdslb.com/bfs/new_dyn/b.jpg", "width": 800, "height": 600},
                {"image_url": "https://i0.hdslb.com/bfs/new_dyn/c.jpg", "width": 900, "height": 900}
              ]}}}}}
            }]}
          }
          """
            .trimIndent()
        )
      )

    val images = response.items.single().images
    assertEquals(3, images.size)
    assertEquals("https://i0.hdslb.com/bfs/new_dyn/a.jpg", images[0].url)
    assertEquals(1800, images[0].height)
    assertEquals("https://i0.hdslb.com/bfs/new_dyn/b.jpg", images[1].url)
  }

  @Test
  fun `article dynamic exposes unified article item`() {
    val response =
      BiliApi.parseSpaceDynamics(
        JSONObject(
          """
          {
            "data": {"items": [{
              "id_str": "998877",
              "basic": {"comment_id_str": "33179525", "comment_type": 12},
              "modules": {
                "module_author": {"mid": 7, "name": "专栏作者", "face": "//i.test/face.jpg", "pub_ts": 1700000000},
                "module_dynamic": {"desc": {"text": "动态附言"}, "major": {"article": {
                  "title": "动态里的专栏",
                  "desc": "专栏简介",
                  "covers": [{"url": "//i.test/article.jpg", "width": 1600, "height": 900}]
                }}},
                "module_stat": {"comment": {"count": 4}, "like": {"count": 8}}
              }
            }]}
          }
          """.trimIndent()
        )
      )

    val article = response.items.single().article!!
    assertEquals(33179525L, article.id)
    assertEquals("动态里的专栏", article.title)
    assertEquals("专栏简介", article.summary)
    assertEquals("https://i.test/article.jpg", article.coverUrl)
    assertEquals(7L, article.authorMid)
  }
}

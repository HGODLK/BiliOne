package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchLaterApiTest {
  @Test
  fun watchLaterParsesTheServerListIntoFeedCards() {
    val cards =
      BiliApi.parseWatchLaterResponse(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "list": [
                {
                  "aid": 123,
                  "bvid": "BV1xx411c7mD",
                  "cid": 456,
                  "title": "稍后再看的视频",
                  "pic": "//i0.hdslb.com/test.jpg",
                  "duration": 125,
                  "pubdate": 1700000000,
                  "owner": {"mid": 9, "name": "测试UP", "face": "//i0.hdslb.com/face.jpg"},
                  "stat": {"view": 1000, "danmaku": 20}
                }
              ]
            }
          }
          """
        )
      )

    assertEquals(1, cards.size)
    assertEquals(123L, cards.single().aid)
    assertEquals("BV1xx411c7mD", cards.single().bvid)
    assertEquals("测试UP", cards.single().uploaderName)
    assertEquals(125L, cards.single().durationSeconds)
  }

  @Test
  fun watchLaterSkipsMalformedEntries() {
    val cards =
      BiliApi.parseWatchLaterResponse(
        JSONObject("""{"data":{"list":[{"title":"missing aid"},{"aid":321,"title":"valid"}]}}""")
      )

    assertEquals(listOf(321L), cards.map { it.aid })
  }
}

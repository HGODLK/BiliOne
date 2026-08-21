package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedCardParserTest {
  @Test
  fun normalizesProtocolRelativeRecommendationImages() {
    val card =
      FeedCard.fromJson(
        JSONObject(
          """
          {
            "id": 1,
            "pic": "//i0.hdslb.com/bfs/archive/cover.jpg",
            "owner": {
              "mid": 9604927,
              "name": "忍者乱泰郎",
              "face": "//i2.hdslb.com/bfs/face/avatar.jpg@128w_128h_1c_1s.webp"
            }
          }
          """
            .trimIndent()
        )
      )

    assertEquals("https://i0.hdslb.com/bfs/archive/cover.jpg", card.coverUrl)
    assertEquals(
      "https://i2.hdslb.com/bfs/face/avatar.jpg@128w_128h_1c_1s.webp",
      card.uploaderFace,
    )
  }
}

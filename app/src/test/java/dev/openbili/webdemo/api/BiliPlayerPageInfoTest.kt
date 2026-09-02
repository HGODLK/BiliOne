package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BiliPlayerPageInfoTest {
  @Test
  fun parsesProgressAndViewPointsInMilliseconds() {
    val data =
      JSONObject(
        """
        {
          "last_play_cid": 123,
          "last_play_time": 4567,
          "view_points": [
            {"from": 60.5, "to": 120.25, "content": "第二段", "imgUrl": "//i.example/a.jpg"},
            {"from": 0, "to": 60.5, "content": "第一段"}
          ]
        }
        """.trimIndent()
      )

    val parsed =
      parsePlayerPageInfo(data, expectedCid = 123L, durationMs = 180_000L)

    assertEquals(4567L, parsed.lastPlayTimeMs)
    assertEquals(2, parsed.chapters.size)
    assertEquals(0L, parsed.chapters[0].startMs)
    assertEquals(60_500L, parsed.chapters[0].endMs)
    assertEquals(60_500L, parsed.chapters[1].startMs)
    assertEquals(120_250L, parsed.chapters[1].endMs)
    assertEquals("//i.example/a.jpg", parsed.chapters[1].imageUrl)
  }

  @Test
  fun ignoresProgressForAnotherCidAndClampsChapterToDuration() {
    val data =
      JSONObject(
        """
        {
          "last_play_cid": 999,
          "last_play_time": 4567,
          "view_points": [{"from": 10, "to": 90, "content": "超出"}]
        }
        """.trimIndent()
      )

    val parsed = parsePlayerPageInfo(data, expectedCid = 123L, durationMs = 60_000L)

    assertEquals(0L, parsed.lastPlayTimeMs)
    assertEquals(1, parsed.chapters.size)
    assertEquals(60_000L, parsed.chapters.single().endMs)
  }

  @Test
  fun blankContentDoesNotFallbackToTitle() {
    val data =
      JSONObject(
        """
        {"view_points": [
          {"from": 0, "to": 10, "content": "", "title": "不应显示"},
          {"from": 10, "to": 20, "content": "有效"}
        ]}
        """.trimIndent()
      )

    val parsed = parsePlayerPageInfo(data, expectedCid = 1L, durationMs = 30_000L)

    assertEquals(1, parsed.chapters.size)
    assertEquals("有效", parsed.chapters.single().title)
  }
}

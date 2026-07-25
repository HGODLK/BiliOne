package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommentPictureParsingTest {

  @Test
  fun parsesNormalizesDeduplicatesAndLimitsCommentPictures() {
    val pictures =
      (0 until 11).joinToString(",") { index ->
        val url =
          if (index == 10) "//i0.hdslb.com/bfs/note/0.jpg"
          else "http://i0.hdslb.com/bfs/note/$index.jpg"
        """{"img_src":"$url","img_width":${640 + index},"img_height":480}"""
      }
    val content = JSONObject("""{"pictures":[$pictures]}""")

    val result = BiliApi.parseCommentPictures(content)

    assertEquals(9, result.size)
    assertEquals("https://i0.hdslb.com/bfs/note/0.jpg", result.first().url)
    assertEquals(640, result.first().width)
    assertEquals(480, result.first().height)
  }

  @Test
  fun ignoresUnsafeOrMalformedPictureUrls() {
    val content =
      JSONObject(
        """{"pictures":[{"img_src":"javascript:alert(1)"},{"img_src":""},{"img_url":"//i1.hdslb.com/a.png"}]}"""
      )

    val result = BiliApi.parseCommentPictures(content)

    assertEquals(listOf("https://i1.hdslb.com/a.png"), result.map(CommentImage::url))
  }
}

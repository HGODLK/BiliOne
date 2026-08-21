package dev.openbili.webdemo.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivateMessageVideoTargetParsingTest {
  @Test
  fun `outgoing video share reads bvid and cover from nested video`() {
    val parsed =
      BiliPrivateMessageApi.parsePrivateContent(
        11,
        """{"video":{"bvid":"BV1ab411c7mD","title":"嵌套视频","cover":"//i0.hdslb.com/test.jpg"}}""",
      )

    assertEquals("BV1ab411c7mD", parsed.linkUrl)
    assertEquals(0L, parsed.oid)
    assertEquals("嵌套视频", parsed.title)
    assertTrue(parsed.coverUrl.endsWith("/test.jpg"))
  }

  @Test
  fun `recommended sub card keeps numeric video identity`() {
    val parsed =
      BiliPrivateMessageApi.parsePrivateContent(
        16,
        """{"main_title":"推荐","sub_cards":[{"title":"第一条推荐","aid":170001,"jump_url":"bilibili://video/170001"}]}""",
      )

    assertEquals("bilibili://video/170001", parsed.linkUrl)
    assertEquals(170001L, parsed.oid)
  }

  @Test
  fun `video share falls back to nested aid when link is absent`() {
    val parsed =
      BiliPrivateMessageApi.parsePrivateContent(
        11,
        """{"item":{"aid":455017605,"title":"AV 视频"}}""",
      )

    assertEquals("https://www.bilibili.com/video/av455017605", parsed.linkUrl)
    assertEquals(455017605L, parsed.oid)
  }
}

package dev.openbili.webdemo.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentVideoLinksTest {
  @Test
  fun parsesDirectB23VideoLink() {
    val parsed = parseCommentVideoLinks("推荐这个 https://b23.tv/BV1ERTR6zECb")

    assertEquals(listOf("BV1ERTR6zECb"), parsed.links.map { it.bvid })
    assertEquals(
      "推荐这个",
      parsed.textWithMappedLinksRemoved(setOf("BV1ERTR6zECb")),
    )
  }

  @Test
  fun parsesBilibiliVideoLinkWithQueryAndKeepsPunctuation() {
    val parsed =
      parseCommentVideoLinks(
        "看看：https://www.bilibili.com/video/BV1ERTR6zECb?spm_id_from=333.1007，挺好"
      )

    assertEquals("BV1ERTR6zECb", parsed.links.single().bvid)
    assertEquals(
      "看看：，挺好",
      parsed.textWithMappedLinksRemoved(setOf("BV1ERTR6zECb")),
    )
  }

  @Test
  fun keepsUnsupportedOrUnresolvedLinksAsText() {
    val content = "短链 https://b23.tv/AbCd12 和网页 https://example.com/BV1ERTR6zECb"
    val parsed = parseCommentVideoLinks(content)

    assertTrue(parsed.links.isEmpty())
    assertEquals(content, parsed.textWithMappedLinksRemoved(emptySet()))
  }

  @Test
  fun onlyRemovesLinksThatMappedSuccessfully() {
    val content = "甲 https://b23.tv/BV1ERTR6zECb 乙 https://b23.tv/BV1Q5411W7Bf"
    val parsed = parseCommentVideoLinks(content)

    assertEquals(
      "甲  乙 https://b23.tv/BV1Q5411W7Bf",
      parsed.textWithMappedLinksRemoved(setOf("BV1ERTR6zECb")),
    )
  }

  @Test
  fun `parses article and opus links`() {
    val parsed =
      parseCommentVideoLinks(
        "专栏 https://www.bilibili.com/read/cv51562647/ 和动态专栏 https://www.bilibili.com/opus/123456"
      )

    assertEquals(listOf(51562647L, 123456L), parsed.articleLinks.map { it.articleId })
    assertEquals(
      "专栏  和动态专栏",
      parsed.textWithMappedLinksRemoved(emptySet(), setOf(51562647L, 123456L)),
    )
  }
}

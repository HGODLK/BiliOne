package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommentMentionParsingTest {

  @Test
  fun parsesOnlyServerResolvedMentions() {
    val content =
      JSONObject("""{"message":"@一只小艾拉 测试 @不存在","members":[{"mid":"474833573","uname":"一只小艾拉"}]}""")

    assertEquals(
      listOf(CommentMention(mid = 474833573L, name = "一只小艾拉")),
      BiliApi.parseCommentMentions(content),
    )
  }

  @Test
  fun ignoresInvalidAndDeduplicatesMembers() {
    val content =
      JSONObject(
        """{"members":[{"mid":"0","uname":"无效"},{"mid":"12","uname":""},{"mid":"7","uname":"用户"},{"mid":"7","uname":"用户"}]}"""
      )

    assertEquals(listOf(CommentMention(7L, "用户")), BiliApi.parseCommentMentions(content))
  }
}

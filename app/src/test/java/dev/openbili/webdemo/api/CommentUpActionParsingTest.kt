package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommentUpActionParsingTest {
  @Test
  fun parsesUploaderLikeAndReplyFlags() {
    val comment =
      BiliApi.parseComment(
        JSONObject(
          """
          {
            "rpid": 1,
            "mid": 2,
            "like": 3,
            "rcount": 4,
            "ctime": 5,
            "action": 0,
            "member": {"uname": "测试用户", "avatar": ""},
            "content": {"message": "测试评论"},
            "up_action": {"like": true, "reply": true}
          }
          """
            .trimIndent()
        )
      )

    assertTrue(comment.upLiked)
    assertTrue(comment.upReplied)
  }

  @Test
  fun missingUploaderActionDefaultsToFalse() {
    val comment =
      BiliApi.parseComment(
        JSONObject(
          """
          {
            "rpid": 1,
            "mid": 2,
            "like": 0,
            "rcount": 0,
            "ctime": 5,
            "member": {},
            "content": {"message": ""}
          }
          """
            .trimIndent()
        )
      )

    assertFalse(comment.upLiked)
    assertFalse(comment.upReplied)
  }
}

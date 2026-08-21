package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommentAddResponseParsingTest {
  @Test
  fun parsesFullReplyFromAddResponse() {
    val json =
      JSONObject(
        """
        {
          "data": {
            "reply": {
              "rpid": 101,
              "mid": 202,
              "like": 0,
              "rcount": 0,
              "ctime": 123,
              "member": {"uname": "测试用户"},
              "content": {"message": "你好"}
            }
          }
        }
        """.trimIndent()
      )

    val result = BiliCommentApi.parseAddedCommentResponse(json, "你好", "响应无效")

    assertEquals(101L, result.rpid)
    assertEquals("测试用户", result.name)
    assertEquals("你好", result.content)
  }

  @Test
  fun fallsBackToMinimalCommentWhenOnlyRpidIsReturned() {
    val json = JSONObject("""{"data":{"rpid":303,"mid":404}}""")

    val result = BiliCommentApi.parseAddedCommentResponse(json, "刚发送", "响应无效")

    assertEquals(303L, result.rpid)
    assertEquals(404L, result.mid)
    assertEquals("我", result.name)
    assertEquals("刚发送", result.content)
  }
}

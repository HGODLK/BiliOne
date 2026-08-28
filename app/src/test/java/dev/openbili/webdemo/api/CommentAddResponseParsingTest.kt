package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    val result = BiliCommentApi.parseAddedCommentResponse(json, "响应无效")

    assertEquals(101L, result.rpid)
    assertEquals("测试用户", result.name)
    assertEquals("你好", result.content)
  }

  @Test
  fun rejectsUnconfirmedResponseWhenOnlyRpidIsReturned() {
    val json = JSONObject("""{"data":{"rpid":303,"mid":404}}""")

    val error =
      assertThrows(IllegalStateException::class.java) {
        BiliCommentApi.parseAddedCommentResponse(json, "响应无效")
      }

    assertEquals("响应无效", error.message)
  }
}

package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountUnreadSummaryTest {
  @Test
  fun `parses reply and mention unread counts without relying on chat`() {
    val summary =
      BiliApi.parseInteractionUnreadSummary(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "at": 2,
              "chat": 4,
              "like": 8,
              "reply": 3,
              "sys_msg": 1
            }
          }
          """
        )
      )

    assertEquals(3, summary.replyCount)
    assertEquals(2, summary.mentionCount)
    assertEquals(5, summary.interactionCount)
  }

  @Test
  fun `missing or negative interaction counts never create unread indicators`() {
    val summary =
      BiliApi.parseInteractionUnreadSummary(
        JSONObject("""{"code":0,"data":{"chat":-1,"reply":-2}}""")
      )

    assertEquals(0, summary.interactionCount)
  }

  @Test
  fun `parses all visible private-message unread categories`() {
    val count =
      BiliApi.parsePrivateMessageUnreadCount(
        JSONObject(
          """
          {
            "code": 0,
            "data": {
              "unfollow_unread": 1,
              "follow_unread": 6,
              "unfollow_push_msg": 2,
              "dustbin_push_msg": 50,
              "dustbin_unread": 40,
              "biz_msg_unfollow_unread": 3,
              "biz_msg_follow_unread": 4,
              "custom_unread": 5
            }
          }
          """
        )
      )

    assertEquals(21, count)
  }

  @Test
  fun `negative private-message counts are ignored`() {
    val count =
      BiliApi.parsePrivateMessageUnreadCount(
        JSONObject("""{"code":0,"data":{"follow_unread":-3,"unfollow_unread":2}}""")
      )

    assertEquals(2, count)
  }
}

package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    assertEquals(8, summary.likeCount)
    assertEquals(5, summary.interactionCount)
  }

  @Test
  fun `missing or negative interaction counts never create unread indicators`() {
    val summary =
      BiliApi.parseInteractionUnreadSummary(
        JSONObject("""{"code":0,"data":{"chat":-1,"reply":-2,"like":-3}}""")
      )

    assertEquals(0, summary.interactionCount)
    assertEquals(0, summary.likeCount)
  }

  @Test
  fun `like messages preserve comment target and pagination cursor`() {
    val page =
      BiliApi.parseLikeMessagePage(
        JSONObject(
          """
          {
            "total": {
              "items": [{
              "id": 901,
              "like_time": 1700000000,
              "counts": 2,
              "users": [{
                "mid": 42,
                "nickname": "测试用户",
                "avatar": "//i0.hdslb.com/bfs/face/test.jpg",
                "level": 6,
                "vip": {"status": 1, "label": {"text": "年度大会员"}}
              }],
              "item": {
                "business": "评论",
                "business_id": 0,
                "item_id": 67890,
                "type": "reply",
                "title": "被点赞的评论",
                "uri": "https://www.bilibili.com/video/BV1xx411c7mD",
                "native_uri": "bilibili://video/12345?comment_root_id=67000&comment_secondary_id=67890"
              }
              }],
              "cursor": {"id": 901, "time": 1700000000, "is_end": false}
            }
          }
          """
        )
      )

    assertEquals(1, page.items.size)
    with(page.items.single()) {
      assertEquals(42L, userMid)
      assertEquals("等共 2 人赞了我的评论", title)
      assertEquals("被点赞的评论", sourceContent)
      assertEquals(12345L, oid)
      assertEquals(67000L, rootId)
      assertEquals(67890L, targetCommentId)
      assertEquals(MessageTargetKind.VIDEO, targetKind)
      assertTrue(userVipActive)
    }
    assertEquals(MessageCursor(id = 901, time = 1700000000), page.cursor)
    assertTrue(page.hasMore)
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

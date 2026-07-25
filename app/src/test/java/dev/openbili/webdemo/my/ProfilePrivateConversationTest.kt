package dev.openbili.webdemo.my

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePrivateConversationTest {
  @Test
  fun createsSelectableSessionForProfileWithoutExistingConversation() {
    val session =
      privateConversationSession(
        userMid = 2233L,
        userName = "测试用户",
        userFace = "https://example.com/avatar.jpg",
      )

    assertEquals(2233L, session.id)
    assertEquals(2233L, session.userMid)
    assertEquals("测试用户", session.userName)
    assertTrue(session.isPrivate)
  }

  @Test
  fun keepsDirectProfileTargetWhenSessionListDoesNotContainIt() {
    val existing = privateConversationSession(100L, "已有会话", "")
    val target = privateConversationSession(200L, "主页用户", "")

    val sessions = includeDirectPrivateTarget(listOf(existing), target)

    assertEquals(listOf(200L, 100L), sessions.map { it.userMid })
  }

  @Test
  fun doesNotDuplicateDirectProfileTargetAlreadyReturnedByServer() {
    val serverTarget = privateConversationSession(200L, "服务端名称", "")
    val requestedTarget = privateConversationSession(200L, "主页名称", "")

    val sessions = includeDirectPrivateTarget(listOf(serverTarget), requestedTarget)

    assertEquals(1, sessions.size)
    assertEquals("服务端名称", sessions.single().userName)
  }
}

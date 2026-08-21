package dev.openbili.webdemo.my

import dev.openbili.webdemo.api.AccountMessageUserStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyUnreadIndicatorTest {
  @Test
  fun `acknowledged unread stays hidden while server summary is stale`() {
    val pending = PendingUnreadAcknowledgement(acknowledgedCount = 3, createdAtMs = 1_000L)

    assertEquals(
      UnreadAcknowledgementResolution(visibleCount = 0, keepPending = true),
      resolveUnreadAfterAcknowledgement(serverCount = 3, pending = pending, nowMs = 2_000L),
    )
    assertEquals(
      UnreadAcknowledgementResolution(visibleCount = 2, keepPending = true),
      resolveUnreadAfterAcknowledgement(serverCount = 5, pending = pending, nowMs = 2_000L),
    )
    assertEquals(
      UnreadAcknowledgementResolution(visibleCount = 0, keepPending = false),
      resolveUnreadAfterAcknowledgement(serverCount = 0, pending = pending, nowMs = 2_000L),
    )
  }

  @Test
  fun `controller mode hides all message sections`() {
    assertEquals(
      listOf(
        MySection.FAVORITES,
        MySection.HISTORY,
        MySection.WATCH_LATER,
        MySection.FOLLOWING,
        MySection.CACHED_VIDEOS,
        MySection.SETTINGS,
      ),
      mySectionsForControlMode(controlMode = true),
    )
    assertEquals(MySection.entries, mySectionsForControlMode(controlMode = false))
  }

  @Test
  fun `only message menu entries expose unread indicators`() {
    val state = MyUiState(privateMessageUnreadCount = 2, interactionUnreadCount = 3)

    assertTrue(state.hasUnread(MySection.MESSAGES))
    assertTrue(state.hasUnread(MySection.INTERACTIONS))
    assertFalse(state.hasUnread(MySection.FOLLOWING))
    assertFalse(state.hasUnread(MySection.HISTORY))
  }

  @Test
  fun `zero counts do not expose indicators`() {
    val state = MyUiState()

    assertFalse(state.hasUnread(MySection.MESSAGES))
    assertFalse(state.hasUnread(MySection.INTERACTIONS))
  }

  @Test
  fun `monitor retry delay backs off after repeated failures`() {
    assertEquals(5_000L, accountUnreadRetryDelayMs(1))
    assertEquals(10_000L, accountUnreadRetryDelayMs(2))
    assertEquals(30_000L, accountUnreadRetryDelayMs(3))
    assertEquals(30_000L, accountUnreadRetryDelayMs(9))
  }

  @Test
  fun `account message user card fills missing level and vip style`() {
    val message = privateConversationSession(42L, "测试 UP", "")
    val styled =
      applyAccountMessageUserStyles(
          messages = listOf(message),
          styles =
            mapOf(
              42L to
                AccountMessageUserStyle(
                  level = 6,
                  vipActive = true,
                  vipLabel = "年度大会员",
                )
            ),
        )
        .single()

    assertEquals(6, styled.userLevel)
    assertTrue(styled.userVipActive)
    assertEquals("年度大会员", styled.userVipLabel)
  }

  @Test
  fun `cached private sessions only override server count while messages are open`() {
    assertEquals(
      1,
      resolvedPrivateMessageUnreadCount(
        section = MySection.MESSAGES,
        privateMessagesLoaded = true,
        cachedUnreadCount = 1,
        serverUnreadCount = 4,
      ),
    )
    assertEquals(
      4,
      resolvedPrivateMessageUnreadCount(
        section = MySection.HISTORY,
        privateMessagesLoaded = true,
        cachedUnreadCount = 1,
        serverUnreadCount = 4,
      ),
    )
  }

  @Test
  fun `controller back returns through content sections and root`() {
    assertEquals(MyControlLevel.SECTIONS, myControlBackTarget(MyControlLevel.CONTENT))
    assertEquals(MyControlLevel.ROOT, myControlBackTarget(MyControlLevel.SECTIONS))
    assertEquals(MyControlLevel.ROOT, myControlBackTarget(MyControlLevel.ROOT))
  }
}

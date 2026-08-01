package dev.openbili.webdemo.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LiveCoverTest {
  @Test
  fun `display and shared transition prefer the current keyframe`() {
    val room =
      LiveSearchRoom(
        roomId = 1L,
        uid = 2L,
        title = "直播",
        uname = "主播",
        coverUrl = "https://example.com/cover.jpg",
        keyframeUrl = "https://example.com/keyframe.jpg",
      )

    assertEquals("https://example.com/keyframe.jpg", room.currentDisplayCoverUrl())
  }

  @Test
  fun `blank keyframe falls back to room cover`() {
    val room =
      LiveSearchRoom(
        roomId = 1L,
        uid = 2L,
        title = "直播",
        uname = "主播",
        coverUrl = "https://example.com/cover.jpg",
        keyframeUrl = "",
      )

    assertEquals("https://example.com/cover.jpg", room.currentDisplayCoverUrl())
  }

  @Test
  fun `the same room keeps distinct home source anchors`() {
    val hero = LiveHomeSourceAnchor.hero(1L)
    val following = LiveHomeSourceAnchor.following(1L)
    val feed = LiveHomeSourceAnchor.feed(1L, "0:0")

    assertEquals(1L, hero.roomId)
    assertNotEquals(hero.stableId, following.stableId)
    assertNotEquals(hero.stableId, feed.stableId)
    assertNotEquals(following.stableId, feed.stableId)
  }
}

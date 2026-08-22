package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.DanmakuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuLanePolicyTest {
  @Test
  fun largeEmojiReservesOnlyItsAdjacentLanes() {
    val span = danmakuLaneSpan(contentHeight = 58f, laneStep = 27f, laneCount = 8)
    val occupied = booleanArrayOf(true, true, true, false, false, false, false, false)

    assertEquals(3, span)
    assertEquals(3, firstDanmakuLaneWindow(occupied.size, span) { !occupied[it] })
    assertEquals(3, firstDanmakuLaneWindow(occupied.size, 1) { !occupied[it] })
  }

  @Test
  fun noWindowIsReturnedWhenAdjacentSpaceIsInsufficient() {
    val occupied = booleanArrayOf(false, true, false, true)

    assertNull(firstDanmakuLaneWindow(occupied.size, 2) { !occupied[it] })
  }

  @Test
  fun liveWindowTrimKeepsActiveEmojiAsSchedulingPrefix() {
    val emoji = item("emoji", timeMs = 0L, imageUrl = "https://example.com/emoji.png")
    val firstText = item("text-a", timeMs = 100L)
    val secondText = item("text-b", timeMs = 200L)
    val incoming = listOf(firstText, secondText, item("text-c", timeMs = 300L))

    assertTrue(
      isDanmakuSlidingWindowReplacement(
        previous = listOf(emoji, firstText, secondText),
        current = incoming,
      )
    )
    assertEquals(
      listOf("emoji", "text-a", "text-b", "text-c"),
      mergeActiveDanmakuSlidingWindow(
          incoming = incoming,
          activeScheduledItems = listOf(emoji, firstText, secondText),
        )
        .map(DanmakuItem::sourceId),
    )
  }

  @Test
  fun blockWordStyleMiddleRemovalDoesNotReuseSlidingWindowState() {
    val first = item("first", timeMs = 0L)
    val blocked = item("blocked", timeMs = 100L)
    val last = item("last", timeMs = 200L)

    assertFalse(
      isDanmakuSlidingWindowReplacement(
        previous = listOf(first, blocked, last),
        current = listOf(first, last),
      )
    )
  }

  private fun item(
    id: String,
    timeMs: Long,
    imageUrl: String? = null,
  ) =
    DanmakuItem(
      timeMs = timeMs,
      type = 1,
      fontSize = 25,
      color = 0xFFFFFF,
      content = id,
      sourceId = id,
      imageUrl = imageUrl,
      imageLarge = imageUrl != null,
    )
}

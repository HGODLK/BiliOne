package dev.openbili.webdemo.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}

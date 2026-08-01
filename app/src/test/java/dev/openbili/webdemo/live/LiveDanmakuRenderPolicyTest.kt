package dev.openbili.webdemo.live

import dev.openbili.webdemo.api.DanmakuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDanmakuRenderPolicyTest {
  @Test
  fun hidesEverythingUntilPlaybackAndFirstFrameAreReady() {
    assertTrue(liveDanmakuAfterPlaybackStart(listOf(itemAt(1_000L)), null).isEmpty())
  }

  @Test
  fun dropsRoomEntryBacklogButKeepsSmallLookbackAndFutureItems() {
    val items = listOf(itemAt(100L), itemAt(1_399L), itemAt(1_400L), itemAt(2_100L))

    assertEquals(
      listOf(1_400L, 2_100L),
      liveDanmakuAfterPlaybackStart(items, renderStartPositionMs = 2_000L).map(DanmakuItem::timeMs),
    )
  }

  private fun itemAt(timeMs: Long) =
    DanmakuItem(
      timeMs = timeMs,
      type = 1,
      fontSize = 25,
      color = 0xFFFFFF,
      content = "弹幕-$timeMs",
      sourceId = "id-$timeMs",
    )
}

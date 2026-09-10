package dev.openbili.webdemo.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentAnimeDanmakuEffectTest {
  @Test
  fun marksOnlyTheCurrentEpisodeActivityWindow() {
    val inWindow = item(timeMs = 2_000_000L)
    val atEnd = item(timeMs = 2_025_000L)
    val before = item(timeMs = 1_999_999L)
    val after = item(timeMs = 2_025_001L)
    val top = item(timeMs = 2_010_000L, type = 5)
    val bottom = item(timeMs = 2_010_000L, type = 4)

    val adapted =
      BiliDanmakuApi.applyCurrentAnimeDanmakuEffect(
        cid = 41_355_775_902L,
        items = listOf(inWindow, atEnd, before, after, top, bottom),
      )

    assertTrue(adapted[0].isBidirectional)
    assertTrue(adapted[1].isBidirectional)
    assertFalse(adapted[2].isBidirectional)
    assertFalse(adapted[3].isBidirectional)
    assertFalse(adapted[4].isBidirectional)
    assertFalse(adapted[5].isBidirectional)
  }

  @Test
  fun leavesOtherVideosUntouched() {
    val item = item(timeMs = 2_010_000L)

    val adapted =
      BiliDanmakuApi.applyCurrentAnimeDanmakuEffect(
        cid = 1L,
        items = listOf(item),
      )

    assertFalse(adapted.single().isBidirectional)
  }

  private fun item(timeMs: Long, type: Int = 1) =
    DanmakuItem(
      timeMs = timeMs,
      type = type,
      fontSize = 25,
      color = 0xFFFFFF,
      content = "弹幕",
    )
}

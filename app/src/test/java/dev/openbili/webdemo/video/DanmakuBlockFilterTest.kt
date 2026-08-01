package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.DanmakuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuBlockFilterTest {
  @Test
  fun levelOneReturnsOriginalList() {
    val items = sampleSegment()
    assertSame(items, filterDanmakuByBlockLevel(items, 1))
  }

  @Test
  fun retainsRequestedCountFromEveryFiveItemsPerSegment() {
    val first = sampleSegment(startMs = 0L)
    val second = sampleSegment(startMs = DANMAKU_SEGMENT_DURATION_MS)
    val filtered = filterDanmakuByBlockLevel(first + second, 4)

    assertEquals(8, filtered.size)
    assertEquals(4, filtered.count { it.timeMs < DANMAKU_SEGMENT_DURATION_MS })
    assertEquals(4, filtered.count { it.timeMs >= DANMAKU_SEGMENT_DURATION_MS })
    assertEquals(
      listOf("0-0", "0-1", "0-5", "0-6"),
      filtered.filter { it.timeMs < DANMAKU_SEGMENT_DURATION_MS }.map(DanmakuItem::sourceId),
    )
  }

  @Test
  fun strongerLevelsOnlyRemoveItemsFromWeakerLevels() {
    val items = sampleSegment()
    val retainedByLevel = (1..5).associateWith { filterDanmakuByBlockLevel(items, it).toSet() }

    assertEquals(listOf(10, 8, 6, 4, 2), retainedByLevel.values.map(Set<DanmakuItem>::size))
    for (level in 2..5) {
      assertTrue(retainedByLevel.getValue(level).all(retainedByLevel.getValue(level - 1)::contains))
    }
  }

  @Test
  fun localDanmakuIsNeverBlocked() {
    val local =
      DanmakuItem(
        timeMs = 500L,
        type = 1,
        fontSize = 25,
        color = 0xFFFFFF,
        content = "自己发送",
        isLocal = true,
      )
    val filtered = filterDanmakuByBlockLevel(sampleSegment() + local, 5)

    assertTrue(local in filtered)
  }

  private fun sampleSegment(startMs: Long = 0L): List<DanmakuItem> =
    (0 until 10).map { index ->
      DanmakuItem(
        timeMs = startMs + index * 1_000L,
        type = 1,
        fontSize = 25,
        color = 0xFFFFFF,
        content = "弹幕$index",
        sourceId = "${startMs / DANMAKU_SEGMENT_DURATION_MS}-$index",
      )
    }
}

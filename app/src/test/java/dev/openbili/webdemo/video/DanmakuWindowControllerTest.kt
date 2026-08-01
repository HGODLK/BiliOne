package dev.openbili.webdemo.video

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuWindowControllerTest {
  @Test
  fun mapsPlaybackPositionToSixMinuteSegment() {
    assertEquals(1, danmakuSegmentIndexAt(0L, durationSeconds = 1_200L))
    assertEquals(1, danmakuSegmentIndexAt(359_999L, durationSeconds = 1_200L))
    assertEquals(2, danmakuSegmentIndexAt(360_000L, durationSeconds = 1_200L))
    assertEquals(4, danmakuSegmentIndexAt(99_999_999L, durationSeconds = 1_200L))
  }

  @Test
  fun keepsPreviousCurrentAndNextSegmentsWithinVideoBounds() {
    assertEquals(listOf(1, 2), danmakuWindowSegmentIndices(1, durationSeconds = 1_200L))
    assertEquals(listOf(1, 2, 3), danmakuWindowSegmentIndices(2, durationSeconds = 1_200L))
    assertEquals(listOf(3, 4), danmakuWindowSegmentIndices(4, durationSeconds = 1_200L))
  }
}

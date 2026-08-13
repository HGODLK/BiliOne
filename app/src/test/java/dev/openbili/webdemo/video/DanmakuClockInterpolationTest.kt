package dev.openbili.webdemo.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuClockInterpolationTest {
  @Test
  fun frameBudgetTracksDisplayRefreshRate() {
    assertEquals(8_333_333L, danmakuFrameBudgetNanos(120f))
    assertEquals(16_666_667L, danmakuFrameBudgetNanos(60f))
  }

  @Test
  fun renderThreadClockAdvancesFromMainThreadSample() {
    val snapshot =
      DanmakuPlaybackClockSnapshot(
        positionMs = 10_000L,
        sampledAtRealtimeNs = 2_000_000_000L,
        playbackRate = 1.5f,
        advancing = true,
        epoch = 7L,
      )

    assertEquals(10_150L, snapshot.positionAt(2_100_000_000L))
  }

  @Test
  fun pausedRenderThreadClockStaysAtSampledPosition() {
    val snapshot =
      DanmakuPlaybackClockSnapshot(
        positionMs = 10_000L,
        sampledAtRealtimeNs = 2_000_000_000L,
        playbackRate = 2f,
        advancing = false,
        epoch = 7L,
      )

    assertEquals(10_000L, snapshot.positionAt(3_000_000_000L))
  }

  @Test
  fun renderThreadClockDoesNotRunBackwardsBeforeItsSample() {
    val snapshot =
      DanmakuPlaybackClockSnapshot(
        positionMs = 10_000L,
        sampledAtRealtimeNs = 2_000_000_000L,
        playbackRate = 1f,
        advancing = true,
        epoch = 7L,
      )

    assertEquals(10_000L, snapshot.positionAt(1_900_000_000L))
  }

  @Test
  fun repeatedRawMillisecondPositionStillAdvancesAtDisplayCadence() {
    val position =
      interpolateDanmakuPosition(
        currentPositionMs = 1_000.0,
        frameElapsedNs = 8_333_333L,
        rawPositionMs = 1_000L,
        playbackRate = 1f,
      )

    assertTrue(position != null && position > 1_008.3)
  }

  @Test
  fun quantizedRawClockKeepsTheContinuousPrediction() {
    val position =
      interpolateDanmakuPosition(
        currentPositionMs = 1_008.333333,
        frameElapsedNs = 8_333_333L,
        rawPositionMs = 1_010L,
        playbackRate = 1f,
      )

    assertEquals(1_016.666666, position ?: 0.0, 0.0001)
  }

  @Test
  fun playbackRateScalesTheFrameAdvance() {
    val position =
      interpolateDanmakuPosition(
        currentPositionMs = 2_000.0,
        frameElapsedNs = 8_000_000L,
        rawPositionMs = 2_016L,
        playbackRate = 2f,
      )

    assertEquals(2_016.0, position ?: 0.0, 0.0001)
  }

  @Test
  fun discontinuityFallsBackToTheExistingClockPath() {
    val position =
      interpolateDanmakuPosition(
        currentPositionMs = 1_000.0,
        frameElapsedNs = 8_000_000L,
        rawPositionMs = 1_100L,
        playbackRate = 1f,
      )

    assertNull(position)
  }
}

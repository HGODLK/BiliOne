package dev.openbili.webdemo

import org.junit.Assert.assertEquals
import org.junit.Test

class CdnShortBufferingDetectorTest {
  @Test
  fun thirdShortBufferingWithinTenSecondsSwitchesCdn() {
    val detector = CdnShortBufferingDetector()

    assertEquals(ShortBufferingDecision.COUNTED, detector.onBuffering(0L, true))
    assertEquals(ShortBufferingDecision.COUNTED, detector.onBuffering(5_000L, true))
    assertEquals(ShortBufferingDecision.SWITCH_CDN, detector.onBuffering(10_000L, true))
  }

  @Test
  fun bufferingOutsideRollingWindowDoesNotReachLimit() {
    val detector = CdnShortBufferingDetector()

    assertEquals(ShortBufferingDecision.COUNTED, detector.onBuffering(0L, true))
    assertEquals(ShortBufferingDecision.COUNTED, detector.onBuffering(5_000L, true))
    assertEquals(ShortBufferingDecision.COUNTED, detector.onBuffering(10_001L, true))
    assertEquals(ShortBufferingDecision.COUNTED, detector.onBuffering(15_001L, true))
    assertEquals(ShortBufferingDecision.SWITCH_CDN, detector.onBuffering(19_000L, true))
  }

  @Test
  fun userSeekExcludesOnlyFirstLoadAndThenCountsBuffering() {
    val detector = CdnShortBufferingDetector()
    detector.onUserSeek()

    assertEquals(ShortBufferingDecision.USER_SEEK_LOAD, detector.onBuffering(1_000L, false))
    detector.onReady()
    assertEquals(ShortBufferingDecision.COUNTED, detector.onBuffering(2_000L, true))
    assertEquals(ShortBufferingDecision.COUNTED, detector.onBuffering(3_000L, true))
    assertEquals(ShortBufferingDecision.SWITCH_CDN, detector.onBuffering(4_000L, true))
  }

  @Test
  fun readyWithoutBufferingEndsSeekExclusion() {
    val detector = CdnShortBufferingDetector()
    detector.onUserSeek()

    detector.onReady()

    assertEquals(ShortBufferingDecision.COUNTED, detector.onBuffering(1_000L, true))
  }

  @Test
  fun pausedBufferingDoesNotEnterShortBufferingWindow() {
    val detector = CdnShortBufferingDetector()

    assertEquals(ShortBufferingDecision.NOT_COUNTED, detector.onBuffering(0L, false))
    assertEquals(ShortBufferingDecision.COUNTED, detector.onBuffering(1_000L, true))
    assertEquals(ShortBufferingDecision.COUNTED, detector.onBuffering(2_000L, true))
    assertEquals(ShortBufferingDecision.SWITCH_CDN, detector.onBuffering(3_000L, true))
  }
}

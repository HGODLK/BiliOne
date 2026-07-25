package dev.openbili.webdemo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPageForegroundTest {
  @Test
  fun profileOverlayMakesRetainedVideoNonForeground() {
    assertFalse(
      isVideoPageForeground(
        videoScreenVisible = true,
        profileVisible = true,
        profileSuppressed = false,
      )
    )
  }

  @Test
  fun returningToVideoReactivatesItsForegroundState() {
    assertTrue(
      isVideoPageForeground(
        videoScreenVisible = true,
        profileVisible = true,
        profileSuppressed = true,
      )
    )
  }
}

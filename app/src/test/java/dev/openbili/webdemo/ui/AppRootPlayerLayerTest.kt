package dev.openbili.webdemo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRootPlayerLayerTest {
  @Test
  fun embeddedCoverStopsSuppressingDanmakuAfterCurrentMediaFirstFrame() {
    assertTrue(
      isEmbeddedPlaybackCoverVisible(
        showEmbeddedCover = true,
        renderedVideoId = null,
        selectedVideoId = "bangumi:ep2",
      )
    )
    assertFalse(
      isEmbeddedPlaybackCoverVisible(
        showEmbeddedCover = true,
        renderedVideoId = "bangumi:ep2",
        selectedVideoId = "bangumi:ep2",
      )
    )
  }
}

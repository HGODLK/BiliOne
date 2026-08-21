package dev.openbili.webdemo

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressGuardTest {
  @Test
  fun loadingOrIdlePlayerCannotOverwriteProgress() {
    assertFalse(
      isPlaybackSnapshotValid("video", "video", Player.STATE_BUFFERING, requireReady = true)
    )
    assertFalse(
      isPlaybackSnapshotValid("video", "video", Player.STATE_IDLE, requireReady = false)
    )
    assertFalse(
      isPlaybackSnapshotValid("video", "video", Player.STATE_BUFFERING, requireReady = false)
    )
  }

  @Test
  fun readyPlayerWithDifferentMediaCannotPersist() {
    assertFalse(
      isPlaybackSnapshotValid("video-a", "video-b", Player.STATE_READY, requireReady = true)
    )
  }

  @Test
  fun matchingReadyPlayerCanPersist() {
    assertTrue(
      isPlaybackSnapshotValid("video", "video", Player.STATE_READY, requireReady = true)
    )
  }
}

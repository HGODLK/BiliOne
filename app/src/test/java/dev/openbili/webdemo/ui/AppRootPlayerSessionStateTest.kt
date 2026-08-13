package dev.openbili.webdemo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRootPlayerSessionStateTest {
  @Test
  fun restoredPlaybackEndSurvivesPlayerPreparation() {
    val state = AppRootPlayerSessionState()

    state.restorePlaybackEnded(true)
    state.updatePlaybackEndedFromPlayer(playerEnded = false)

    assertTrue(state.playbackEnded)
  }

  @Test
  fun clearingPlaybackEndReleasesRestoredRetention() {
    val state = AppRootPlayerSessionState()
    state.restorePlaybackEnded(true)

    state.clearPlaybackEnded()
    state.updatePlaybackEndedFromPlayer(playerEnded = false)

    assertFalse(state.playbackEnded)
  }

  @Test
  fun scrubFrameSeekIsImmediateThenThrottledToEightyMilliseconds() {
    assertEquals(0L, scrubFrameSeekDelayMs(Long.MIN_VALUE, 1_000_000_000L))
    assertEquals(60L, scrubFrameSeekDelayMs(1_000_000_000L, 1_020_000_000L))
    assertEquals(0L, scrubFrameSeekDelayMs(1_000_000_000L, 1_080_000_000L))
  }
}

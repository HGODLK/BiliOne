package dev.openbili.webdemo.ui

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
}

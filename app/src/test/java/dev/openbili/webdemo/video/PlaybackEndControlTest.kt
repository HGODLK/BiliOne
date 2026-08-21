package dev.openbili.webdemo.video

import dev.openbili.webdemo.ui.ControlVideoMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEndControlTest {
  @Test
  fun playerDirectAndPlayerControlsTransferFocusToTheEndOverlay() {
    assertTrue(
      shouldTakePlaybackEndControl(
        controlMode = true,
        playbackPageForeground = true,
        currentMode = ControlVideoMode.PLAYER_DIRECT,
        playerSurfaceFocused = false,
        alreadyOwned = false,
      )
    )
    assertTrue(
      shouldTakePlaybackEndControl(
        controlMode = true,
        playbackPageForeground = true,
        currentMode = ControlVideoMode.PLAYER_CONTROLS,
        playerSurfaceFocused = false,
        alreadyOwned = false,
      )
    )
  }

  @Test
  fun pageNavigationOnlyTransfersWhenThePlayerItselfOwnsFocus() {
    assertTrue(
      shouldTakePlaybackEndControl(
        controlMode = true,
        playbackPageForeground = true,
        currentMode = ControlVideoMode.PAGE_NAVIGATION,
        playerSurfaceFocused = true,
        alreadyOwned = false,
      )
    )
    assertFalse(
      shouldTakePlaybackEndControl(
        controlMode = true,
        playbackPageForeground = true,
        currentMode = ControlVideoMode.PAGE_NAVIGATION,
        playerSurfaceFocused = false,
        alreadyOwned = false,
      )
    )
  }

  @Test
  fun backgroundOrTouchOnlyPlaybackDoesNotStealControlFocus() {
    assertFalse(
      shouldTakePlaybackEndControl(
        controlMode = false,
        playbackPageForeground = true,
        currentMode = ControlVideoMode.PLAYER_DIRECT,
        playerSurfaceFocused = true,
        alreadyOwned = false,
      )
    )
    assertFalse(
      shouldTakePlaybackEndControl(
        controlMode = true,
        playbackPageForeground = false,
        currentMode = ControlVideoMode.PLAYER_DIRECT,
        playerSurfaceFocused = true,
        alreadyOwned = false,
      )
    )
  }
}

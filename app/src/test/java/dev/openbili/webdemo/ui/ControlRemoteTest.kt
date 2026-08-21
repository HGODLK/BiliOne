package dev.openbili.webdemo.ui

import android.view.InputDevice
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlRemoteTest {
  @Test
  fun `connected controller only owns interaction while its focus chrome is active`() {
    assertTrue(controllerInteractionActive(controlMode = true, controlFocusVisible = true))
    assertFalse(controllerInteractionActive(controlMode = true, controlFocusVisible = false))
    assertFalse(controllerInteractionActive(controlMode = false, controlFocusVisible = true))
  }

  @Test
  fun `playback focus is restored only when controller retakes the foreground page`() {
    assertTrue(
      shouldRestorePlaybackControlFocus(
        controlMode = true,
        controlFocusVisible = true,
        isPlaybackPageForeground = true,
      )
    )
    assertFalse(
      shouldRestorePlaybackControlFocus(
        controlMode = true,
        controlFocusVisible = false,
        isPlaybackPageForeground = true,
      )
    )
    assertFalse(
      shouldRestorePlaybackControlFocus(
        controlMode = true,
        controlFocusVisible = true,
        isPlaybackPageForeground = false,
      )
    )
    assertFalse(
      shouldRestorePlaybackControlFocus(
        controlMode = true,
        controlFocusVisible = true,
        isPlaybackPageForeground = true,
        focusTargetReady = false,
      )
    )
    assertFalse(
      shouldRestorePlaybackControlFocus(
        controlMode = true,
        controlFocusVisible = true,
        isPlaybackPageForeground = true,
        focusRestoreBlocked = true,
      )
    )
  }

  @Test
  fun `controller playback page requires an explicit confirm before direct control`() {
    assertEquals(
      ControlVideoSurfaceAction.ENTER_DIRECT,
      resolveControlVideoSurfaceAction(
        keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
        keyUp = true,
        repeatCount = 0,
        mode = ControlVideoMode.PAGE_NAVIGATION,
        fullscreen = false,
      ),
    )
  }

  @Test
  fun `controller seek step is five seconds`() {
    assertEquals(5_000L, CONTROL_SEEK_STEP_MS)
  }

  @Test
  fun `video page surface routes arrows to explicit regions`() {
    assertEquals(
      ControlVideoSurfaceAction.FOCUS_HEADER,
      resolveControlVideoSurfaceAction(
        KeyEvent.KEYCODE_DPAD_UP,
        keyUp = false,
        repeatCount = 0,
        mode = ControlVideoMode.PAGE_NAVIGATION,
        fullscreen = false,
      ),
    )
    assertEquals(
      ControlVideoSurfaceAction.FOCUS_RECOMMENDATIONS,
      resolveControlVideoSurfaceAction(
        KeyEvent.KEYCODE_DPAD_DOWN,
        keyUp = false,
        repeatCount = 0,
        mode = ControlVideoMode.PAGE_NAVIGATION,
        fullscreen = false,
      ),
    )
    assertEquals(
      ControlVideoSurfaceAction.FOCUS_COMMENTS,
      resolveControlVideoSurfaceAction(
        KeyEvent.KEYCODE_DPAD_RIGHT,
        keyUp = false,
        repeatCount = 0,
        mode = ControlVideoMode.PAGE_NAVIGATION,
        fullscreen = false,
      ),
    )
  }

  @Test
  fun `embedded player direct mode seeks and distinguishes confirm`() {
    assertEquals(
      ControlVideoSurfaceAction.SEEK_BACKWARD,
      resolveControlVideoSurfaceAction(
        KeyEvent.KEYCODE_DPAD_LEFT,
        keyUp = false,
        repeatCount = 0,
        mode = ControlVideoMode.PLAYER_DIRECT,
        fullscreen = false,
      ),
    )
    assertEquals(
      ControlVideoSurfaceAction.ENTER_CONTROLS,
      resolveControlVideoSurfaceAction(
        KeyEvent.KEYCODE_DPAD_DOWN,
        keyUp = false,
        repeatCount = 0,
        mode = ControlVideoMode.PLAYER_DIRECT,
        fullscreen = false,
      ),
    )
    assertEquals(
      ControlVideoSurfaceAction.REGISTER_DIRECT_CONFIRM,
      resolveControlVideoSurfaceAction(
        KeyEvent.KEYCODE_DPAD_CENTER,
        keyUp = true,
        repeatCount = 0,
        mode = ControlVideoMode.PLAYER_DIRECT,
        fullscreen = false,
      ),
    )
  }

  @Test
  fun `fullscreen confirm pauses into control selection`() {
    assertEquals(
      ControlVideoSurfaceAction.PAUSE_AND_ENTER_CONTROLS,
      resolveControlVideoSurfaceAction(
        KeyEvent.KEYCODE_BUTTON_A,
        keyUp = true,
        repeatCount = 0,
        mode = ControlVideoMode.PLAYER_DIRECT,
        fullscreen = true,
      ),
    )
  }

  @Test
  fun `physical controller sources enable control navigation`() {
    assertTrue(isControlNavigationInput(InputDevice.SOURCE_GAMEPAD, isVirtual = false))
    assertTrue(isControlNavigationInput(InputDevice.SOURCE_JOYSTICK, isVirtual = false))
    assertTrue(isControlNavigationInput(InputDevice.SOURCE_DPAD, isVirtual = false))
    assertFalse(isControlNavigationInput(InputDevice.SOURCE_TOUCHSCREEN, isVirtual = false))
    assertFalse(isControlNavigationInput(InputDevice.SOURCE_DPAD, isVirtual = true))
  }

  @Test
  fun `only navigation and gamepad buttons restore controller focus chrome`() {
    assertTrue(isControlInteractionKey(KeyEvent.KEYCODE_DPAD_LEFT))
    assertTrue(isControlInteractionKey(KeyEvent.KEYCODE_DPAD_CENTER))
    assertTrue(isControlInteractionKey(KeyEvent.KEYCODE_BUTTON_A))
    assertFalse(isControlInteractionKey(KeyEvent.KEYCODE_VOLUME_UP))
    assertFalse(isControlInteractionKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
  }

  @Test
  fun `controller hat axes map to regular remote D-pad keys`() {
    assertEquals(
      KeyEvent.KEYCODE_DPAD_LEFT,
      controlDpadKeyCodeForAxis(-1f, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT),
    )
    assertEquals(
      KeyEvent.KEYCODE_DPAD_RIGHT,
      controlDpadKeyCodeForAxis(1f, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT),
    )
    assertEquals(
      KeyEvent.KEYCODE_DPAD_UP,
      controlDpadKeyCodeForAxis(-1f, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN),
    )
    assertEquals(
      KeyEvent.KEYCODE_DPAD_DOWN,
      controlDpadKeyCodeForAxis(1f, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN),
    )
    assertEquals(
      null,
      controlDpadKeyCodeForAxis(0f, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT),
    )
  }

  @Test
  fun `surface arrows seek and vertical directions reveal controls`() {
    assertEquals(
      ControlPlayerAction.SEEK_BACKWARD,
      resolveControlPlayerAction(KeyEvent.KEYCODE_DPAD_LEFT, true, false, false),
    )
    assertEquals(
      ControlPlayerAction.SEEK_FORWARD,
      resolveControlPlayerAction(KeyEvent.KEYCODE_DPAD_RIGHT, true, false, false),
    )
    assertEquals(
      ControlPlayerAction.SHOW_CONTROLS,
      resolveControlPlayerAction(KeyEvent.KEYCODE_DPAD_UP, true, false, false),
    )
    assertEquals(
      ControlPlayerAction.NONE,
      resolveControlPlayerAction(KeyEvent.KEYCODE_DPAD_DOWN, true, true, false),
    )
  }

  @Test
  fun `focused controls retain their own D-pad events`() {
    assertEquals(
      ControlPlayerAction.NONE,
      resolveControlPlayerAction(KeyEvent.KEYCODE_DPAD_LEFT, false, true, false),
    )
    assertEquals(
      ControlPlayerAction.NONE,
      resolveControlPlayerAction(KeyEvent.KEYCODE_DPAD_CENTER, false, true, false),
    )
  }

  @Test
  fun `dialogs block surface navigation but not physical media keys`() {
    assertEquals(
      ControlPlayerAction.NONE,
      resolveControlPlayerAction(KeyEvent.KEYCODE_DPAD_RIGHT, true, true, true),
    )
    assertEquals(
      ControlPlayerAction.TOGGLE_PLAY_PAUSE,
      resolveControlPlayerAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false, true, true),
    )
  }

  @Test
  fun `center reveals hidden controls before toggling playback`() {
    assertEquals(
      ControlPlayerAction.SHOW_CONTROLS,
      resolveControlPlayerAction(KeyEvent.KEYCODE_DPAD_CENTER, true, false, false),
    )
    assertEquals(
      ControlPlayerAction.TOGGLE_PLAY_PAUSE,
      resolveControlPlayerAction(KeyEvent.KEYCODE_DPAD_CENTER, true, true, false),
    )
  }

  @Test
  fun `confirm press starts once and release clicks`() {
    assertEquals(
      ControlConfirmAction.START_TRACKING,
      resolveControlConfirmAction(KeyEvent.KEYCODE_BUTTON_A, true, 0, false, false),
    )
    assertEquals(
      ControlConfirmAction.NONE,
      resolveControlConfirmAction(KeyEvent.KEYCODE_BUTTON_A, true, 2, true, false),
    )
    assertEquals(
      ControlConfirmAction.CLICK,
      resolveControlConfirmAction(KeyEvent.KEYCODE_BUTTON_A, false, 0, true, false),
    )
  }

  @Test
  fun `release after long press does not click`() {
    assertEquals(
      ControlConfirmAction.CANCEL,
      resolveControlConfirmAction(KeyEvent.KEYCODE_ENTER, false, 0, true, true),
    )
  }
}

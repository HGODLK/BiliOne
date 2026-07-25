package dev.openbili.webdemo

import org.junit.Assert.assertEquals
import org.junit.Test

class StateLogicTest {
  @Test
  fun backPriority() {
    assertEquals(BackAction.EXIT_FULLSCREEN, resolveBackAction(true, true))
    assertEquals(BackAction.WEB_HISTORY, resolveBackAction(false, true))
    assertEquals(BackAction.FINISH_ACTIVITY, resolveBackAction(false, false))
  }

  @Test
  fun appBackPriority() {
    assertEquals(BackAction.EXIT_FULLSCREEN, resolveAppBackAction(true, true))
    assertEquals(BackAction.RETURN_TO_FEED, resolveAppBackAction(false, true))
    assertEquals(BackAction.FINISH_ACTIVITY, resolveAppBackAction(false, false))
  }

  @Test
  fun rootBackExitsOnlyInsideConfirmationWindow() {
    assertEquals(false, shouldExitOnRootBack(null, 10_000L))
    assertEquals(true, shouldExitOnRootBack(10_000L, 11_999L))
    assertEquals(true, shouldExitOnRootBack(10_000L, 12_000L))
    assertEquals(false, shouldExitOnRootBack(10_000L, 12_001L))
    assertEquals(false, shouldExitOnRootBack(10_000L, 9_999L))
  }

  @Test
  fun fullscreenStateTransitions() {
    var state = WebViewState()
    state = state.copy(isFullscreen = true)
    assertEquals(true, state.isFullscreen)
    state = state.copy(isFullscreen = false)
    assertEquals(false, state.isFullscreen)
  }
}

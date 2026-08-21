package dev.openbili.webdemo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInputModeTest {
  @Test
  fun `first touch locks touch mode for the current launch`() {
    val selected = resolveAppInputMode(AppInputMode.UNDECIDED, AppInputKind.TOUCH)

    assertEquals(AppInputMode.TOUCH, selected)
    assertEquals(AppInputMode.TOUCH, resolveAppInputMode(selected, AppInputKind.CONTROLLER))
  }

  @Test
  fun `first controller input locks controller mode for the current launch`() {
    val selected = resolveAppInputMode(AppInputMode.UNDECIDED, AppInputKind.CONTROLLER)

    assertEquals(AppInputMode.CONTROLLER, selected)
    assertEquals(AppInputMode.CONTROLLER, resolveAppInputMode(selected, AppInputKind.TOUCH))
  }

  @Test
  fun `controller drift does not select an input mode`() {
    assertFalse(isMeaningfulControllerMotion(0f, 0.12f, -0.2f, 0.49f))
    assertTrue(isMeaningfulControllerMotion(0f, -0.5f))
    assertTrue(isMeaningfulControllerMotion(0.7f, 0f))
  }
}

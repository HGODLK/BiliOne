package dev.openbili.webdemo.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuOcclusionControllerTest {
  @Test
  fun startupMaskHasPriorityOverOtherStateChanges() {
    val controller = DanmakuOcclusionController()

    controller.setDeclarativelySuppressed(true)
    controller.setStartupMaskVisible(true)
    controller.setDeclarativelySuppressed(false)

    assertTrue(controller.currentSnapshot().blocked)

    controller.setStartupMaskVisible(false)

    assertFalse(controller.currentSnapshot().blocked)
  }

  @Test
  fun immediateSuppressionIsReleasedWithoutRemovingStartupMask() {
    val controller = DanmakuOcclusionController()

    controller.setStartupMaskVisible(true)
    controller.suppressImmediately()
    val hiddenGeneration = controller.currentSnapshot().generation
    controller.releaseImmediateSuppression()

    assertTrue(controller.currentSnapshot().blocked)
    assertNotEquals(hiddenGeneration, controller.currentSnapshot().generation)

    controller.setStartupMaskVisible(false)

    assertFalse(controller.currentSnapshot().blocked)
  }
}

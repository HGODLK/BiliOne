package dev.openbili.webdemo.video

import dev.openbili.webdemo.ui.COMMENT_PROFILE_PREPARE_TIMEOUT_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGestureLogicTest {
  @Test
  fun twoFingerContactWithoutSpanChangeDoesNotToggleFullscreen() {
    assertNull(
      fullscreenTargetForSpan(
        isFullscreen = false,
        initialSpan = 400f,
        currentSpan = 400f,
        minimumMovementPx = 12f,
      )
    )
  }

  @Test
  fun outwardMotionEntersAndInwardMotionExitsFullscreen() {
    assertEquals(
      true,
      fullscreenTargetForSpan(false, initialSpan = 400f, currentSpan = 420f, minimumMovementPx = 12f),
    )
    assertEquals(
      false,
      fullscreenTargetForSpan(true, initialSpan = 400f, currentSpan = 380f, minimumMovementPx = 12f),
    )
  }

  @Test
  fun wrongDirectionNeverChangesCurrentFullscreenMode() {
    assertNull(fullscreenTargetForSpan(false, 400f, 380f, 12f))
    assertNull(fullscreenTargetForSpan(true, 400f, 420f, 12f))
  }

  @Test
  fun commentProfilePreparationStaysUnderThreeTenthsOfASecond() {
    assertTrue(COMMENT_PROFILE_PREPARE_TIMEOUT_MS <= 300L)
  }
}

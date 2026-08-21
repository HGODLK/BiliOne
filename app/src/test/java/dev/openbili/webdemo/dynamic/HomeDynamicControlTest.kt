package dev.openbili.webdemo.dynamic

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDynamicControlTest {
  @Test
  fun uploaderMenuOnlyEntersFourthLevelOnConfirmRelease() {
    assertEquals(
      DynamicUploaderControlAction.SELECT_AND_ENTER_CONTENT,
      resolveDynamicUploaderControlAction(
        keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
        keyUp = true,
        controlEnabled = true,
      ),
    )
    assertEquals(
      DynamicUploaderControlAction.CONSUME,
      resolveDynamicUploaderControlAction(
        keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
        keyUp = false,
        controlEnabled = true,
      ),
    )
  }

  @Test
  fun uploaderMenuConsumesHorizontalDirectionsWithoutCrossingLevels() {
    assertEquals(
      DynamicUploaderControlAction.CONSUME,
      resolveDynamicUploaderControlAction(
        keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
        keyUp = false,
        controlEnabled = true,
      ),
    )
    assertEquals(
      DynamicUploaderControlAction.CONSUME,
      resolveDynamicUploaderControlAction(
        keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
        keyUp = false,
        controlEnabled = true,
      ),
    )
    assertEquals(
      DynamicUploaderControlAction.NONE,
      resolveDynamicUploaderControlAction(
        keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
        keyUp = false,
        controlEnabled = true,
      ),
    )
  }

  @Test
  fun touchModeDoesNotUseControllerCrossLevelAction() {
    assertEquals(
      DynamicUploaderControlAction.NONE,
      resolveDynamicUploaderControlAction(
        keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
        keyUp = true,
        controlEnabled = false,
      ),
    )
  }
}

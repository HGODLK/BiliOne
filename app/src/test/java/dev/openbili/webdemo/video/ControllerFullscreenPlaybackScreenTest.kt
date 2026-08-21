package dev.openbili.webdemo.video

import org.junit.Assert.assertEquals
import android.view.KeyEvent
import org.junit.Test

class ControllerFullscreenPlaybackScreenTest {
  @Test
  fun `controller uses fullscreen page while touch playback page is disabled`() {
    assertEquals(
      VideoPlaybackPageKind.CONTROLLER_FULLSCREEN,
      resolveVideoPlaybackPageKind(
        controlMode = true,
        controllerTouchPlaybackPage = false,
      ),
    )
  }

  @Test
  fun `controller uses existing touch page after the setting is enabled`() {
    assertEquals(
      VideoPlaybackPageKind.TOUCH,
      resolveVideoPlaybackPageKind(
        controlMode = true,
        controllerTouchPlaybackPage = true,
      ),
    )
  }

  @Test
  fun `touch mode always uses the existing playback page`() {
    assertEquals(
      VideoPlaybackPageKind.TOUCH,
      resolveVideoPlaybackPageKind(
        controlMode = false,
        controllerTouchPlaybackPage = false,
      ),
    )
  }

  @Test
  fun `background confirm toggles playback and down enters actions`() {
    assertEquals(
      ControllerPlaybackKeyAction.TOGGLE_PLAY,
      resolveControllerPlaybackKeyAction(
        keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
        keyUp = true,
        repeatCount = 0,
        overlay = ControllerPlaybackOverlay.HIDDEN,
      ),
    )
    assertEquals(
      ControllerPlaybackKeyAction.SHOW_ACTIONS,
      resolveControllerPlaybackKeyAction(
        keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
        keyUp = false,
        repeatCount = 0,
        overlay = ControllerPlaybackOverlay.INFO,
      ),
    )
  }

  @Test
  fun `live background consumes horizontal seeking`() {
    assertEquals(
      ControllerPlaybackKeyAction.CONSUME,
      resolveControllerPlaybackKeyAction(
        keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
        keyUp = false,
        repeatCount = 0,
        overlay = ControllerPlaybackOverlay.HIDDEN,
        isLive = true,
      ),
    )
  }

  @Test
  fun `holding horizontal seek keys accelerates by repeat count`() {
    assertEquals(-5_000L, controllerPlaybackSeekDeltaMs(forward = false, repeatCount = 0))
    assertEquals(10_000L, controllerPlaybackSeekDeltaMs(forward = true, repeatCount = 4))
    assertEquals(60_000L, controllerPlaybackSeekDeltaMs(forward = true, repeatCount = 12))
  }

  @Test
  fun `selection panel exposes root groups before entering a group`() {
    val group =
      ControllerPlaybackSelectionGroup(
        key = "episodes:1",
        label = "第 1-50 项",
        items = listOf(ControllerPlaybackActionItem("episode:1", "第1话")),
      )

    val root =
      resolveControllerPlaybackSelectionContent(
        currentGroup = null,
        rootItems = emptyList(),
        rootGroups = listOf(group),
      )
    val entered =
      resolveControllerPlaybackSelectionContent(
        currentGroup = group,
        rootItems = emptyList(),
        rootGroups = listOf(group),
      )

    assertEquals(listOf(group), root.groups)
    assertEquals(emptyList<ControllerPlaybackActionItem>(), root.items)
    assertEquals(emptyList<ControllerPlaybackSelectionGroup>(), entered.groups)
    assertEquals(group.items, entered.items)
  }

  @Test
  fun `controller playback time uses hour format when needed`() {
    assertEquals("01:02", formatControllerPlaybackTime(62_000L))
    assertEquals("1:01:02", formatControllerPlaybackTime(3_662_000L))
  }
}

package dev.openbili.webdemo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerReplayTargetTest {
  @Test
  fun retainedParentDoesNotReplayLoadedChild() {
    assertFalse(
      isReplayTargetCurrent(
        loadedVideoId = "child-video",
        expectedVideoId = "parent-video",
      )
    )
  }

  @Test
  fun currentLoadedVideoCanReplayInPlace() {
    assertTrue(
      isReplayTargetCurrent(
        loadedVideoId = "parent-video",
        expectedVideoId = "parent-video",
      )
    )
  }
}

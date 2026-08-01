package dev.openbili.webdemo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTransitionInputLockTest {
  @Test
  fun retainedReturnSessionDoesNotKeepProfileLocked() {
    assertFalse(
      profileTransitionInputLocked(
        activeCommentTransitionBlocksInput = false,
        activeAvatarTransition = false,
      )
    )
  }

  @Test
  fun activeProfileTransitionsStillLockInput() {
    assertTrue(
      profileTransitionInputLocked(
        activeCommentTransitionBlocksInput = true,
        activeAvatarTransition = false,
      )
    )
    assertTrue(
      profileTransitionInputLocked(
        activeCommentTransitionBlocksInput = false,
        activeAvatarTransition = true,
      )
    )
  }
}

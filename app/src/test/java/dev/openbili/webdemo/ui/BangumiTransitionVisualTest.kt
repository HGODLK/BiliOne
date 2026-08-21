package dev.openbili.webdemo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiTransitionVisualTest {
  @Test
  fun `poster visual uses cover fitting while player visual preserves landscape framing`() {
    assertTrue(BangumiTransitionVisual.POSTER_PORTRAIT.fitCover)
    assertFalse(BangumiTransitionVisual.PLAYER_LANDSCAPE.fitCover)
  }

  @Test
  fun `poster transition keeps full backdrop instead of punching player portal`() {
    assertFalse(
      shouldPunchRootVideoEntryPortal(
        transitionVisual = BangumiTransitionVisual.POSTER_PORTRAIT,
        playerBoundsReady = true,
      )
    )
  }

  @Test
  fun `landscape transition punches portal only after player bounds are ready`() {
    assertFalse(
      shouldPunchRootVideoEntryPortal(
        transitionVisual = BangumiTransitionVisual.PLAYER_LANDSCAPE,
        playerBoundsReady = false,
      )
    )
    assertTrue(
      shouldPunchRootVideoEntryPortal(
        transitionVisual = BangumiTransitionVisual.PLAYER_LANDSCAPE,
        playerBoundsReady = true,
      )
    )
  }

  @Test
  fun `season switch always uses direct exit regardless of transition shape`() {
    assertTrue(shouldFadeBangumiExitDirectly(seasonChangedFromSource = true))
    assertFalse(shouldFadeBangumiExitDirectly(seasonChangedFromSource = false))
  }
}

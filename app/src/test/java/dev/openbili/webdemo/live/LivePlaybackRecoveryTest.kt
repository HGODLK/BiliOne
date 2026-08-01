package dev.openbili.webdemo.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackRecoveryTest {
  @Test
  fun switchesEveryKnownSourceBeforeRefreshingUrls() {
    val recovery = LivePlaybackRecovery()

    assertEquals(
      LivePlaybackRecoveryAction.SwitchSource(1),
      recovery.onFailure(nowMs = 1_000L, currentSourceIndex = 0, sourceCount = 3),
    )
    assertEquals(
      LivePlaybackRecoveryAction.SwitchSource(2),
      recovery.onFailure(nowMs = 2_000L, currentSourceIndex = 1, sourceCount = 3),
    )
    assertEquals(
      LivePlaybackRecoveryAction.RefreshUrls(delayMs = 800L, round = 1),
      recovery.onFailure(nowMs = 3_000L, currentSourceIndex = 2, sourceCount = 3),
    )
  }

  @Test
  fun automaticRefreshStopsAfterTheConfiguredRounds() {
    val recovery =
      LivePlaybackRecovery(
        maxRefreshRounds = 2,
        duplicateWindowMs = 0L,
        refreshDelaysMs = longArrayOf(10L, 20L),
      )

    assertEquals(
      LivePlaybackRecoveryAction.RefreshUrls(10L, 1),
      recovery.onFailure(nowMs = 1L, currentSourceIndex = 0, sourceCount = 1),
    )
    assertEquals(
      LivePlaybackRecoveryAction.RefreshUrls(20L, 2),
      recovery.onFailure(nowMs = 2L, currentSourceIndex = 0, sourceCount = 1),
    )
    assertEquals(
      LivePlaybackRecoveryAction.Stop,
      recovery.onFailure(nowMs = 3L, currentSourceIndex = 0, sourceCount = 1),
    )
  }

  @Test
  fun duplicatePlayerCallbacksDoNotConsumeAnotherFallback() {
    val recovery = LivePlaybackRecovery(duplicateWindowMs = 750L)

    recovery.onFailure(nowMs = 1_000L, currentSourceIndex = 0, sourceCount = 3)
    assertEquals(
      LivePlaybackRecoveryAction.Ignore,
      recovery.onFailure(nowMs = 1_200L, currentSourceIndex = 0, sourceCount = 3),
    )
  }

  @Test
  fun readyStateResetsTheRefreshBudget() {
    val recovery = LivePlaybackRecovery(maxRefreshRounds = 1, duplicateWindowMs = 0L)
    recovery.onFailure(nowMs = 1L, currentSourceIndex = 0, sourceCount = 1)
    assertTrue(
      recovery.onFailure(nowMs = 2L, currentSourceIndex = 0, sourceCount = 1)
        is LivePlaybackRecoveryAction.Stop
    )

    recovery.onReady()

    assertTrue(
      recovery.onFailure(nowMs = 3L, currentSourceIndex = 0, sourceCount = 1)
        is LivePlaybackRecoveryAction.RefreshUrls
    )
  }
}

package dev.openbili.webdemo.live

internal sealed interface LivePlaybackRecoveryAction {
  data class SwitchSource(val index: Int) : LivePlaybackRecoveryAction

  data class RefreshUrls(
    val delayMs: Long,
    val round: Int,
  ) : LivePlaybackRecoveryAction

  data object Ignore : LivePlaybackRecoveryAction

  data object Stop : LivePlaybackRecoveryAction
}

/**
 * Keeps automatic live recovery bounded. A newly fetched URL set preserves the refresh count; a
 * confirmed READY state or an explicit user request starts a fresh recovery sequence.
 */
internal class LivePlaybackRecovery(
  private val maxRefreshRounds: Int = 2,
  private val duplicateWindowMs: Long = 750L,
  private val refreshDelaysMs: LongArray = longArrayOf(800L, 2_000L),
) {
  private var refreshRounds = 0
  private var lastFailedSourceIndex = -1
  private var lastFailureAtMs = Long.MIN_VALUE

  fun reset() {
    refreshRounds = 0
    lastFailedSourceIndex = -1
    lastFailureAtMs = Long.MIN_VALUE
  }

  fun onReady() = reset()

  fun onFailure(
    nowMs: Long,
    currentSourceIndex: Int,
    sourceCount: Int,
  ): LivePlaybackRecoveryAction {
    val duplicate =
      currentSourceIndex == lastFailedSourceIndex &&
        lastFailureAtMs != Long.MIN_VALUE &&
        nowMs - lastFailureAtMs in 0..duplicateWindowMs
    if (duplicate) return LivePlaybackRecoveryAction.Ignore

    lastFailedSourceIndex = currentSourceIndex
    lastFailureAtMs = nowMs
    val nextSourceIndex = currentSourceIndex + 1
    if (nextSourceIndex in 0 until sourceCount) {
      return LivePlaybackRecoveryAction.SwitchSource(nextSourceIndex)
    }
    if (refreshRounds >= maxRefreshRounds) return LivePlaybackRecoveryAction.Stop

    refreshRounds += 1
    lastFailedSourceIndex = -1
    val delayMs =
      refreshDelaysMs
        .getOrElse(refreshRounds - 1) { refreshDelaysMs.lastOrNull() ?: 0L }
        .coerceAtLeast(0L)
    return LivePlaybackRecoveryAction.RefreshUrls(delayMs, refreshRounds)
  }
}

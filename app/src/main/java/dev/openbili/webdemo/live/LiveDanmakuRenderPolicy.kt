package dev.openbili.webdemo.live

import dev.openbili.webdemo.api.DanmakuItem

/**
 * Keeps room-entry backlog away from the first playable video frame.
 *
 * Live chat starts before playback is necessarily running. Rendering that complete backlog when the
 * user first presses play makes text measurement and bitmap preparation contend with the decoder's
 * first frame. Once playback and the first frame are both ready, only a small look-back is
 * admitted; all later items continue through normally.
 */
internal fun liveDanmakuAfterPlaybackStart(
  items: List<DanmakuItem>,
  renderStartPositionMs: Long?,
  lookbackMs: Long = LIVE_DANMAKU_STARTUP_LOOKBACK_MS,
): List<DanmakuItem> {
  val start = renderStartPositionMs ?: return emptyList()
  val cutoff = (start - lookbackMs.coerceAtLeast(0L)).coerceAtLeast(0L)
  return items.filter { it.timeMs >= cutoff }
}

internal const val LIVE_DANMAKU_STARTUP_LOOKBACK_MS = 600L

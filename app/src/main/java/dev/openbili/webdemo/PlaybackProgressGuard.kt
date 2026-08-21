package dev.openbili.webdemo

import androidx.media3.common.Player

/** 判断播放器快照是否仍属于当前媒体，避免切换或缓冲期间把 0 秒写入历史。 */
internal fun isPlaybackSnapshotValid(
  expectedMediaId: String?,
  actualMediaId: String?,
  playbackState: Int,
  requireReady: Boolean,
): Boolean {
  if (expectedMediaId.isNullOrBlank() || actualMediaId != expectedMediaId) return false
  return if (requireReady) {
    playbackState == Player.STATE_READY
  } else {
    playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED
  }
}

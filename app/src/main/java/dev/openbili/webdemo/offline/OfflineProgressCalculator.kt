package dev.openbili.webdemo.offline

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download

/** Media3 单条音频或视频轨道的进度快照，供调度层和界面共用。 */
internal data class OfflineTrackProgress(
  val state: Int,
  val contentLength: Long,
  val bytesDownloaded: Long,
  val percentDownloaded: Float,
)

internal data class OfflineCombinedProgress(
  val percent: Float?,
  val bytesDownloaded: Long,
  val totalBytes: Long,
)

/**
 * 合并同一个视频的音频、视频轨道进度。
 *
 * 只有所有未完成轨道都能提供总大小时才进行字节加权；否则只在每条轨道都有可靠百分比时
 * 使用百分比平均值，剩余情况返回 null，避免未知轨道让进度提前变成 100% 或产生 NaN。
 */
@OptIn(UnstableApi::class)
internal fun combineOfflineTrackProgress(tracks: List<OfflineTrackProgress>): OfflineCombinedProgress {
  if (tracks.isEmpty()) return OfflineCombinedProgress(null, 0L, 0L)

  val bytesDownloaded = tracks.sumOf { it.bytesDownloaded.coerceAtLeast(0L) }
  val effectiveTotals =
    tracks.map { track ->
      when {
        track.contentLength > 0L -> track.contentLength
        track.state == Download.STATE_COMPLETED && track.bytesDownloaded > 0L ->
          track.bytesDownloaded
        else -> null
      }
    }
  val allTotalsKnown = effectiveTotals.all { it != null && it > 0L }
  if (allTotalsKnown) {
    val totalBytes = effectiveTotals.filterNotNull().sum()
    val percent =
      if (totalBytes > 0L) {
        (bytesDownloaded.toDouble() / totalBytes.toDouble() * 100.0)
          .toFloat()
          .takeIf(Float::isFinite)
          ?.coerceIn(0f, 100f)
      } else {
        null
      }
    return OfflineCombinedProgress(percent, bytesDownloaded, totalBytes)
  }

  val knownPercents =
    tracks.mapNotNull { track ->
      track.percentDownloaded
        .takeIf { it.isFinite() && it >= 0f }
        ?.coerceIn(0f, 100f)
    }
  val percent =
    if (knownPercents.size == tracks.size && knownPercents.isNotEmpty()) {
      knownPercents.average().toFloat().takeIf(Float::isFinite)?.coerceIn(0f, 100f)
    } else {
      null
    }
  return OfflineCombinedProgress(percent, bytesDownloaded, 0L)
}

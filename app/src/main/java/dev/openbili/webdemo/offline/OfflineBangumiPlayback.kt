package dev.openbili.webdemo.offline

/** 缓存番剧换集时的阻断原因。 */
enum class OfflineBangumiPlaybackBlockReason(val message: String) {
  NOT_CACHED("该集尚未缓存"),
  NOT_COMPLETED("该集缓存尚未完成"),
  UNAVAILABLE("该集缓存当前不可用"),
}

data class OfflineBangumiPlaybackResolution(
  val entry: OfflineMediaEntry? = null,
  val blockReason: OfflineBangumiPlaybackBlockReason? = null,
) {
  val playable: Boolean
    get() = entry != null
}

/** 从缓存快照中解析番剧分集，不负责联网回退。 */
object OfflineBangumiPlaybackResolver {
  fun resolve(
    snapshots: List<OfflineMediaSnapshot>,
    episodeId: Long,
    cid: Long,
    seasonId: Long,
    canPlay: (OfflineMediaEntry) -> Boolean,
  ): OfflineBangumiPlaybackResolution {
    val candidates =
      snapshots.filter { snapshot ->
        val entry = snapshot.entry
        entry.kind == OfflineMediaKind.BANGUMI &&
          entry.episodeId == episodeId &&
          (cid <= 0L || entry.cid <= 0L || entry.cid == cid) &&
          (seasonId <= 0L || entry.seasonId <= 0L || entry.seasonId == seasonId)
      }
    if (candidates.isEmpty()) {
      return OfflineBangumiPlaybackResolution(
        blockReason = OfflineBangumiPlaybackBlockReason.NOT_CACHED
      )
    }
    val completed = candidates.firstOrNull { it.state == OfflineTransferState.COMPLETED }
    if (completed == null) {
      return OfflineBangumiPlaybackResolution(
        blockReason = OfflineBangumiPlaybackBlockReason.NOT_COMPLETED
      )
    }
    if (!canPlay(completed.entry)) {
      return OfflineBangumiPlaybackResolution(
        blockReason = OfflineBangumiPlaybackBlockReason.UNAVAILABLE
      )
    }
    return OfflineBangumiPlaybackResolution(entry = completed.entry)
  }
}

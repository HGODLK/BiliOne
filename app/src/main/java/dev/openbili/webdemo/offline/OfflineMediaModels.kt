package dev.openbili.webdemo.offline

import android.net.Uri
import dev.openbili.webdemo.feed.FeedItem
import java.io.File

enum class OfflineMediaKind {
  VIDEO,
  BANGUMI,
}

enum class OfflineEntitlementState {
  FREE,
  ACTIVE,
  LOCKED,
  REVOKED,
}

enum class OfflineTransferState {
  PREPARING,
  QUEUED,
  DOWNLOADING,
  PAUSED,
  COMPLETED,
  FAILED,
  UNAVAILABLE,
}

data class OfflineStorageLocation(
  val id: String,
  val label: String,
  val rootPath: String,
  val removable: Boolean,
  val availableBytes: Long,
  val selected: Boolean,
)

data class OfflineStorageMigrationProgress(
  val copiedBytes: Long,
  val totalBytes: Long,
) {
  val fraction: Float
    get() =
      if (totalBytes <= 0L) 1f
      else (copiedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

data class OfflineSubtitle(
  val id: String,
  val label: String,
  val language: String,
  val relativePath: String,
)

data class OfflineMediaEntry(
  val id: String,
  val kind: OfflineMediaKind,
  val accountMid: Long,
  val title: String,
  val partTitle: String,
  val coverUrl: String,
  val coverRelativePath: String = "",
  val bvid: String,
  val aid: Long,
  val cid: Long,
  val pageNumber: Int,
  val collectionId: Long = 0L,
  val seasonId: Long = 0L,
  val episodeId: Long = 0L,
  val durationMs: Long,
  val qualityId: Int,
  val qualityLabel: String,
  val videoUrl: String = "",
  val audioUrl: String = "",
  val videoCacheKey: String = "",
  val audioCacheKey: String = "",
  val videoMimeType: String = "video/mp4",
  val audioMimeType: String = "audio/mp4",
  val includeDanmaku: Boolean = true,
  val includeSubtitles: Boolean = true,
  val danmakuRelativePath: String = "",
  val subtitles: List<OfflineSubtitle> = emptyList(),
  val requiresVip: Boolean = false,
  val entitlementState: OfflineEntitlementState = OfflineEntitlementState.FREE,
  val entitlementValidUntilMs: Long = Long.MAX_VALUE,
  val createdAtMs: Long = System.currentTimeMillis(),
  val preparationPaused: Boolean = false,
  val preparationError: String = "",
) {
  val playbackUri: String
    get() = "bilione-offline://media/${Uri.encode(id)}"

  fun coverFile(root: File): File? =
    coverRelativePath.takeIf(String::isNotBlank)?.let { File(root, it) }?.takeIf(File::isFile)

  fun toFeedItem(root: File): FeedItem =
    FeedItem(
      id = "offline:$id",
      title = title,
      videoUrl = playbackUri,
      coverUrl = coverFile(root)?.let(Uri::fromFile)?.toString() ?: coverUrl,
      uploader = partTitle.takeIf(String::isNotBlank),
      playCount = qualityLabel,
      duration = formatDuration(durationMs),
      description = if (entitlementState == OfflineEntitlementState.REVOKED) "会员状态失效，缓存内容不可用" else "",
    )
}

data class OfflineMediaSnapshot(
  val entry: OfflineMediaEntry,
  val state: OfflineTransferState,
  val progressPercent: Float,
  val bytesDownloaded: Long,
  val totalBytes: Long,
  val failureReason: String = "",
)

data class OfflineMediaRequest(
  val kind: OfflineMediaKind,
  val accountMid: Long,
  val title: String,
  val partTitle: String,
  val coverUrl: String,
  val bvid: String,
  val aid: Long,
  val cid: Long,
  val pageNumber: Int,
  val durationMs: Long,
  val collectionId: Long = 0L,
  val seasonId: Long = 0L,
  val episodeId: Long = 0L,
  val qualityId: Int,
  val qualityLabel: String = "",
  val includeDanmaku: Boolean = true,
  val includeSubtitles: Boolean = true,
  val requiresVip: Boolean = false,
)

internal fun offlineMediaId(kind: OfflineMediaKind, bvid: String, cid: Long, episodeId: Long): String =
  when (kind) {
    OfflineMediaKind.VIDEO -> "video_${bvid.filter(Char::isLetterOrDigit)}_$cid"
    OfflineMediaKind.BANGUMI -> "bangumi_${episodeId}_$cid"
  }

private fun formatDuration(durationMs: Long): String {
  val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L)
  val hours = totalSeconds / 3_600L
  val minutes = (totalSeconds % 3_600L) / 60L
  val seconds = totalSeconds % 60L
  return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
  else "%02d:%02d".format(minutes, seconds)
}

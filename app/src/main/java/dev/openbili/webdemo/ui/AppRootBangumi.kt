package dev.openbili.webdemo.ui

import androidx.compose.ui.geometry.Rect
import dev.openbili.webdemo.BangumiPlaybackStore
import dev.openbili.webdemo.api.BangumiIndexItem
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import dev.openbili.webdemo.feed.FeedItem

internal data class ActiveBangumiPage(
  val sourceCard: SpaceContentCard,
  val sourceProfileEntryId: Long,
  val sourceMid: Long,
  val sourceBounds: Rect?,
  val sourceVideoCoverUrl: String = "",
  val returnToSourceCover: Boolean = false,
  /** Keeps the portrait transition contract while routing source-cover visibility to Explore. */
  val sourceIsBangumiExplorePoster: Boolean = false,
  val sourceUsesLivePlayer: Boolean = false,
  val sourceOrigin: PageOrigin = PageOrigin.Profile(sourceProfileEntryId, sourceMid),
  val sourceSeasonId: Long = sourceCard.seasonId,
  val sourceFollowedByViewer: Boolean = false,
  val seasonChangedFromSource: Boolean = false,
  val season: BangumiSeason? = null,
  val loading: Boolean = true,
  val error: String? = null,
  val currentEpisodeId: Long = sourceCard.episodeId,
  val followBusy: Boolean = false,
  val playbackFallbackEmitted: Boolean = false,
)

/** Bilibili's PGC heartbeat expects the actual season media type, not only anime vs guochuang. */
internal fun pgcPlaybackSubType(seasonType: Int): Int = seasonType.takeIf { it > 0 } ?: 1

/** Result of resolving which episode and start position to use when entering a bangumi page. */
data class BangumiEntryTarget(
  val card: SpaceContentCard,
  val startPositionMs: Long,
  val serverResumeAuthoritative: Boolean,
)

/**
 * Pure function that decides the effective entry target for a bangumi card.
 *
 * Priority:
 * 1. [sourceCard.watchProgress] with valid episodeId → server-recorded episode and position.
 * 2. [localSelection] (when [allowLocalSelection]) → last-watched from BangumiPlaybackStore.
 * 3. Fallback → source card's default [SpaceContentCard.episodeId] / new_ep.
 */
internal fun resolveBangumiEntryTarget(
  sourceCard: SpaceContentCard,
  localSelection: BangumiPlaybackStore.Selection?,
  allowLocalSelection: Boolean,
): BangumiEntryTarget {
  val progress = sourceCard.watchProgress
  if (progress != null && progress.episodeId > 0L) {
    val videoUrl = "https://www.bilibili.com/bangumi/play/ep${progress.episodeId}"
    return BangumiEntryTarget(
      card =
        sourceCard.copy(
          videoUrl = videoUrl,
          episodeId = progress.episodeId,
        ),
      startPositionMs = progress.positionMs,
      serverResumeAuthoritative = true,
    )
  }
  if (allowLocalSelection && localSelection != null) {
    val restoredSeasonId = localSelection.seasonId.takeIf { it > 0L } ?: sourceCard.seasonId
    val videoUrl = "https://www.bilibili.com/bangumi/play/ep${localSelection.episodeId}"
    return BangumiEntryTarget(
      card =
        sourceCard.copy(
          aid = 0L,
          bvid = localSelection.bvid,
          videoUrl = videoUrl,
          seasonId = restoredSeasonId,
          episodeId = localSelection.episodeId,
        ),
      startPositionMs = 0L,
      serverResumeAuthoritative = false,
    )
  }
  return BangumiEntryTarget(
    card = sourceCard,
    startPositionMs = 0L,
    serverResumeAuthoritative = false,
  )
}

internal fun SpaceContentCard.toBangumiVideoItem(
  uploader: String? = null,
  uploaderFace: String? = null,
  uploaderMid: Long = 0L,
): FeedItem =
  FeedItem(
    id = id,
    title = title,
    videoUrl =
      episodeId.takeIf { it > 0L }?.let { "https://www.bilibili.com/bangumi/play/ep$it" }
        ?: videoUrl,
    coverUrl = coverUrl,
    uploader = uploader,
    playCount = null,
    duration = null,
    uploaderFace = uploaderFace,
    uploaderMid = uploaderMid,
    description = subtitle,
  )

internal fun BangumiIndexItem.toIndexBangumiCard(): SpaceContentCard =
  SpaceContentCard(
    id = stableId,
    title = title,
    subtitle = indexShow.ifBlank { subtitle },
    coverUrl = coverUrl,
    videoUrl = targetUrl,
    seasonId = seasonId,
    episodeId = episodeId,
    kind = SpaceContentKind.BANGUMI,
    seasonType = seasonType,
  )

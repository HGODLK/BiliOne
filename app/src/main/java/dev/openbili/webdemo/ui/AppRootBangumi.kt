package dev.openbili.webdemo.ui

import androidx.compose.ui.geometry.Rect
import dev.openbili.webdemo.BangumiPlaybackStore
import dev.openbili.webdemo.api.BangumiIndexItem
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import dev.openbili.webdemo.feed.FeedItem

/** 番剧共享转场的几何目标；它不决定来源页面与进入生命周期。 */
internal enum class BangumiTransitionVisual {
  PLAYER_LANDSCAPE,
  POSTER_PORTRAIT,
  ;

  val fitCover: Boolean
    get() = this == POSTER_PORTRAIT
}

/** 竖版共享海报落点不是播放器，转场期间应保留完整页面遮罩。 */
internal fun shouldPunchRootVideoEntryPortal(
  transitionVisual: BangumiTransitionVisual?,
  playerBoundsReady: Boolean,
): Boolean =
  transitionVisual != BangumiTransitionVisual.POSTER_PORTRAIT &&
    (transitionVisual == null || playerBoundsReady)

/** 换季后的详情已不再对应来源卡，退出时必须整体淡出，不能伪造封面飞回。 */
internal fun shouldFadeBangumiExitDirectly(seasonChangedFromSource: Boolean): Boolean =
  seasonChangedFromSource

internal data class ActiveBangumiPage(
  val sourceCard: SpaceContentCard,
  val sourceProfileEntryId: Long,
  val sourceMid: Long,
  val sourceBounds: Rect?,
  val sourceVideoCoverUrl: String = "",
  val returnToSourceCover: Boolean = false,
  /** 在把源封面可见性路由给 Explore 的同时，保持竖屏转场契约。 */
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

/** B 站 PGC 心跳期望真实的季度媒体类型，而不只是番剧与国创之分。 */
internal fun pgcPlaybackSubType(seasonType: Int): Int = seasonType.takeIf { it > 0 } ?: 1

/** 进入番剧页时解析用哪一集和哪个起始位置的结果。 */
data class BangumiEntryTarget(
  val card: SpaceContentCard,
  val startPositionMs: Long,
  val serverResumeAuthoritative: Boolean,
)

/**
 * 决定番剧卡片有效进入目标的纯函数。
 *
 * 优先级：
 * 1. 带有效 episodeId 的 [sourceCard.watchProgress] → 服务器记录的分集与位置。
 * 2. [localSelection]（当 [allowLocalSelection] 时）→ BangumiPlaybackStore 记录的
 *    上次观看位置。
 * 3. 回退 → 源卡片默认的 [SpaceContentCard.episodeId] / new_ep。
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

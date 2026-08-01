package dev.openbili.webdemo.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.DanmakuMaskTimeline
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.SpaceDynamicItem
import dev.openbili.webdemo.api.SpaceProfile
import dev.openbili.webdemo.api.VideoEngagement
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedScrollAnchor
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.live.LiveSearchRoom

internal enum class SearchTransitionDirection {
  ENTER,
  EXIT,
}

internal fun Rect.hasUsableSize(): Boolean = this != Rect.Zero && width > 0f && height > 0f

internal fun Rect.approximatelyEquals(other: Rect, tolerancePx: Float = 1f): Boolean =
  hasUsableSize() &&
    other.hasUsableSize() &&
    kotlin.math.abs(left - other.left) <= tolerancePx &&
    kotlin.math.abs(top - other.top) <= tolerancePx &&
    kotlin.math.abs(right - other.right) <= tolerancePx &&
    kotlin.math.abs(bottom - other.bottom) <= tolerancePx

/**
 * A returned source-card measurement must not collapse onto the active player. When that happens,
 * retain the boundary captured at entry so the cover can still fly back instead of disappearing in
 * place after the exit prelude.
 */
internal fun resolveExitTransitionTargetBounds(
  latest: Rect?,
  fallback: Rect?,
  playerBounds: Rect,
): Rect? =
  latest?.takeIf { it.hasUsableSize() && !it.approximatelyEquals(playerBounds) } ?: fallback

internal sealed interface PageOrigin {
  data object Home : PageOrigin

  data object My : PageOrigin

  data object Search : PageOrigin

  data class LiveRoom(val entryId: Long, val roomId: Long) : PageOrigin

  /** Independent PGC index page retained below a bangumi detail page. */
  data object BangumiIndex : PageOrigin

  data object BangumiHome : PageOrigin

  data object Article : PageOrigin

  data object Other : PageOrigin

  data class Profile(val entryId: Long, val mid: Long) : PageOrigin

  data class Video(val entryId: String) : PageOrigin
}

internal const val MAX_VIDEO_ENTRY_CACHE = 8
internal const val MAX_VIDEO_STACK_DEPTH = 8
internal const val MAX_PROFILE_STACK_DEPTH = 8
internal const val MAX_ARTICLE_STACK_DEPTH = 8
internal const val MAX_LIVE_ROOM_STACK_DEPTH = 8
internal const val FIRST_FRAME_REVEAL_TIMEOUT_MS = 4_500L

internal data class LiveRoomParentFrame(
  val entryId: Long,
  val room: LiveSearchRoom,
  val childRoomId: Long,
  val childCoverBounds: Rect,
)

internal fun liveRecommendationBoundsKey(parentRoomId: Long, childRoomId: Long): String =
  "$parentRoomId:$childRoomId"

internal data class StackFrame(
  val entryId: String,
  val item: FeedItem,
  val parentPage: PageOrigin,
  val sourceCardBounds: Rect?,
  val sourceProfile: ProfilePageEntry? = null,
  val inPlaceSelectionChanged: Boolean = false,
  val rootFeedScrollAnchor: FeedScrollAnchor? = null,
  val sourceWasPlaybackEndRecommendation: Boolean = false,
)

internal data class ProfileVideoKey(val profileEntryId: Long, val itemId: String)

/** One independently retained profile page in the bounded profile navigation stack. */
internal data class ProfileStackEntry(
  val entryId: Long,
  val state: AppRootProfileState,
  val commentTransition: CommentProfileTransition? = null,
  val avatarTransition: AvatarProfileTransition? = null,
  /** This entry was opened above a video and returns to that video, not to the profile below it. */
  val returnsToVideo: Boolean = false,
  /** This entry was opened from an article and must reveal the retained article on dismissal. */
  val returnsToArticle: Boolean = false,
)

/**
 * Makes a profile's route back to its underlying page durable before another overlay replaces the
 * global transition slots. Existing entry-owned transitions always win.
 */
internal fun List<ProfileStackEntry>.retainReturnTransitionsFor(
  entryId: Long,
  commentTransition: CommentProfileTransition?,
  avatarTransition: AvatarProfileTransition?,
): List<ProfileStackEntry> =
  map { entry ->
    if (entry.entryId != entryId) entry
    else
      entry.copy(
        commentTransition = entry.commentTransition ?: commentTransition,
        avatarTransition = entry.avatarTransition ?: avatarTransition,
      )
  }

internal data class SuspendedArticleVideo(
  val item: FeedItem,
  val stack: List<StackFrame>,
)

internal data class ProfilePageEntry(
  val mid: Long,
  val profile: SpaceProfile?,
  val videos: List<FeedItem>,
  val page: Int,
  val hasMore: Boolean,
  val error: String?,
  val dynamics: List<SpaceDynamicItem>,
  val dynamicOffset: String,
  val dynamicHasMore: Boolean,
  val dynamicError: String?,
  val selectedDynamicId: String?,
  val commentReturnTransition: CommentProfileTransition?,
  val avatarReturnTransition: AvatarProfileTransition?,
  val collections: List<dev.openbili.webdemo.api.SpaceContentCard> = emptyList(),
  val collectionsError: String? = null,
  val selectedCollectionId: String? = null,
  val collectionVideos: List<FeedItem> = emptyList(),
  val collectionPage: Int = 0,
  val collectionHasMore: Boolean = false,
  val collectionError: String? = null,
  val collectionTotal: Int = 0,
)

internal data class CommentProfileTransition(
  val token: Long,
  val targetMid: Long,
  val sourceComment: CommentItem,
  val sourceBounds: Rect,
  val sourceAvatarBounds: Rect?,
  val currentSourceBounds: () -> Rect,
  val currentSourceAvatarBounds: () -> Rect,
  /** Profile that owns the clicked comment, when navigation starts inside another profile. */
  val sourceProfile: ProfilePageEntry? = null,
  val progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> =
    Animatable(0f),
  val backgroundAlpha: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> =
    Animatable(0f),
) {
  val preparation =
    TransitionPreparationBarrier(
      buildSet {
        add(TransitionReadySignal.SOURCE_BOUNDS)
        add(TransitionReadySignal.TARGET_MOUNTED)
        add(TransitionReadySignal.TARGET_BOUNDS_STABLE)
        // The first comment-profile visit must not decode the shared avatar during the flight.
        if (sourceAvatarBounds != null && sourceComment.face.isNotBlank()) {
          add(TransitionReadySignal.IMAGE_READY)
        }
      }
    )
  var phase by mutableStateOf(SessionPhase.PREPARING)
  var preparationTimedOut by mutableStateOf(false)
  var closing: Boolean = false
  var blocksInput by mutableStateOf(true)
}

internal fun resolvedCommentProfileBounds(fallback: Rect, current: Rect): Rect =
  current.takeIf { it.width > 0f && it.height > 0f } ?: fallback

/**
 * Locks global pointer input only while a profile transition is actively running.
 *
 * Return-transition sessions are intentionally not inputs here: they remain retained for the
 * entire lifetime of the destination profile so the eventual back animation can reuse them.
 */
internal fun profileTransitionInputLocked(
  activeCommentTransitionBlocksInput: Boolean,
  activeAvatarTransition: Boolean,
): Boolean = activeCommentTransitionBlocksInput || activeAvatarTransition

internal data class AvatarProfileTransition(
  val token: Long,
  val targetMid: Long,
  val face: String,
  val name: String,
  val sourceBounds: Rect,
  /** Profile that owns the clicked avatar, when navigation starts inside another profile. */
  val sourceProfile: ProfilePageEntry? = null,
  val progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> =
    Animatable(0f),
  val backgroundAlpha: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> =
    Animatable(0f),
) {
  val preparation =
    TransitionPreparationBarrier(
      setOf(
        TransitionReadySignal.SOURCE_BOUNDS,
        TransitionReadySignal.IMAGE_READY,
        TransitionReadySignal.TARGET_MOUNTED,
        TransitionReadySignal.TARGET_BOUNDS_STABLE,
      )
    )
  var phase by mutableStateOf(SessionPhase.PREPARING)
  var preparationTimedOut by mutableStateOf(false)
  var closing: Boolean = false
}

internal data class VideoPageEntry(
  val item: FeedItem,
  val recommendations: List<FeedItem>,
  val info: VideoInfo?,
  val engagement: VideoEngagement,
  val favoriteFolders: List<FavoriteFolder>,
  val description: String,
  val onlineViewerText: String?,
  val comments: List<CommentItem>,
  val commentTotalCount: Long,
  val commentPage: Int,
  val commentHasMore: Boolean,
  val commentOid: Long,
  val commentSort: CommentSort = CommentSort.DEFAULT,
  val danmaku: List<DanmakuItem>,
  val danmakuMask: DanmakuMaskTimeline?,
  val emotes: List<BiliEmote>,
  val cid: Long,
  val durationSeconds: Long,
  val savedPositionMs: Long,
  val qualityIndex: Int,
  val dataReady: Boolean,
  val playbackEnded: Boolean = false,
)

internal data class VideoExitPrelude(
  val item: FeedItem,
  val playerBounds: Rect,
  val fitCover: Boolean = false,
  val reusePlayerSurface: Boolean = false,
  val pageAlpha: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> =
    Animatable(1f),
  val coverAlpha: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> =
    Animatable(0f),
) {
  var transitionBitmap by mutableStateOf<Bitmap?>(null)
}

internal enum class TransitionKind {
  ENTER_ROOT,
  ENTER_RECOMMENDATION,
  ENTER_PROFILE,
  EXIT_ROOT,
  EXIT_RECOMMENDATION,
  EXIT_PROFILE,
}

internal enum class SessionPhase {
  PREPARING,
  READY,
  FLYING,
  REVEALING_BACKGROUND,
  WAITING_FIRST_FRAME,
  REVEALING,
  COMPLETED,
  CANCELLED,
}

internal fun shouldDisplayCardTransitionOverlay(
  kind: TransitionKind,
  phase: SessionPhase,
): Boolean {
  if (phase == SessionPhase.PREPARING) return false
  val exit =
    kind == TransitionKind.EXIT_ROOT ||
      kind == TransitionKind.EXIT_RECOMMENDATION ||
      kind == TransitionKind.EXIT_PROFILE
  return phase != SessionPhase.READY || !exit
}

internal fun shouldHideVideoPageBehindExitCover(
  kind: TransitionKind?,
  phase: SessionPhase?,
): Boolean =
  kind == TransitionKind.EXIT_ROOT &&
    phase in setOf(SessionPhase.FLYING, SessionPhase.REVEALING_BACKGROUND)

internal class CardTransitionSession(
  val token: Long,
  val kind: TransitionKind,
  val item: FeedItem,
  startBounds: Rect,
  endBounds: Rect,
  initialProgress: Float,
  initialPanelAlpha: Float = if (initialProgress == 0f) 0f else 1f,
  val fitCover: Boolean = false,
  val reusePlayerSurface: Boolean = false,
  requiredSignals: Set<TransitionReadySignal> = emptySet(),
) {
  var startBounds by mutableStateOf(startBounds)
  var endBounds by mutableStateOf(endBounds)
  val progress = Animatable(initialProgress)
  val coverAlpha = Animatable(1f)
  val panelAlpha = Animatable(initialPanelAlpha)
  val backgroundAlpha = Animatable(if (initialProgress == 0f) 0f else 1f)
  val themeScrimAlpha = Animatable(0f)
  val bridgeAlpha = Animatable(0f)
  var transitionBitmap by mutableStateOf<Bitmap?>(null)
  val preparation = TransitionPreparationBarrier(requiredSignals)
  var phase by mutableStateOf(SessionPhase.PREPARING)
  var reverseRequested by mutableStateOf(false)
  var backgroundStarted by mutableStateOf(false)
  var timedOut by mutableStateOf(false)
}

internal fun shouldReloadSelectedVideo(item: FeedItem?, playerState: PlayerState): Boolean =
  item != null && playerState is PlayerState.Idle

internal fun feedItemFromCard(card: dev.openbili.webdemo.api.FeedCard) =
  FeedItem(
    id = card.bvid.ifBlank { card.aid.toString() },
    title = card.title,
    videoUrl = "https://www.bilibili.com/video/${card.bvid.ifBlank { "av${card.aid}" }}",
    coverUrl = card.coverUrl,
    uploader = card.uploaderName,
    playCount = FeedViewModel.formatCount(card.playCount),
    duration = FeedViewModel.formatDuration(card.durationSeconds),
    uploaderFace = card.uploaderFace,
    uploaderMid = card.uploaderMid,
    danmakuCount = card.danmakuCount,
    publishedAt = card.pubdate,
    description = card.description,
  )

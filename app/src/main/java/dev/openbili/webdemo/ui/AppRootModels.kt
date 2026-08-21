/**
 * AppRoot 拆分后的共享数据模型与纯函数集合。
 *
 * 本文件承载各上下文类共同使用、且与 UI 无关的模型定义与判定逻辑：页面来源枚举
 * [PageOrigin]、卡片转场会话 [CardTransitionSession]、视频/文章/个人资料的关系栈帧、
 * 退出前奏 [VideoExitPrelude]，以及大量“转场期间是否隐藏封面 / 是否抑制弹幕 / 是否
 * 延迟挂载辅助内容”的纯函数。将这些无副作用的模型与规则从巨型 AppRoot 中剥离，
 * 既能降低单文件复杂度，也便于拆分出的各上下文类以参数形式复用同一套转场状态机。
 */

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

/** 搜索/番剧索引等覆盖层的转场方向：进入或退出。 */
internal enum class SearchTransitionDirection {
  ENTER,
  EXIT,
}

/** 判断一个 Rect 是否“可用”：非零且宽高都为正，可作为有效的转场锚点边界。 */
internal fun Rect.hasUsableSize(): Boolean = this != Rect.Zero && width > 0f && height > 0f

/** 判断两个 Rect 在给定像素容差内是否近似相等，用于识别“边界是否已经稳定”。 */
internal fun Rect.approximatelyEquals(other: Rect, tolerancePx: Float = 1f): Boolean =
  hasUsableSize() &&
    other.hasUsableSize() &&
    kotlin.math.abs(left - other.left) <= tolerancePx &&
    kotlin.math.abs(top - other.top) <= tolerancePx &&
    kotlin.math.abs(right - other.right) <= tolerancePx &&
    kotlin.math.abs(bottom - other.bottom) <= tolerancePx

/**
 * 解析退出转场的目标边界。
 *
 * 返回的源卡片实测边界不得塌缩到“当前正在播放的播放器”上；一旦出现这种情况，就保留
 * 进入时捕获的那份边界，这样封面仍能飞回原卡片，而不是在退出前奏结束后原地消失。
 */
internal fun resolveExitTransitionTargetBounds(
  latest: Rect?,
  fallback: Rect?,
  playerBounds: Rect,
): Rect? =
  latest?.takeIf { it.hasUsableSize() && !it.approximatelyEquals(playerBounds) } ?: fallback

/**
 * 页面来源（origin）的封闭接口，标记一个视频/文章/直播等详情页是从哪个上层页面打开的。
 *
 * 退出转场依赖它决定封面应飞回哪个卡片的边界映射；关系栈帧 [StackFrame] 也用它记录
 * 每一层的父页面，从而支持“返回上一级视频 / 返回个人资料 / 返回首页”等不同回退路径。
 */
internal sealed interface PageOrigin {
  data object Home : PageOrigin

  data object My : PageOrigin

  data object Search : PageOrigin

  data class LiveRoom(val entryId: Long, val roomId: Long) : PageOrigin

  /** 独立的 PGC 索引页，被保留在番剧详情页下方（详情页关闭后回到该索引）。 */
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

/**
 * 直播房间的父级栈帧。
 *
 * 当用户从某个直播间点进“推荐直播间”时，记录父直播间与子直播间的关系及其封面边界，
 * 供退出时飞回父直播间的对应卡片。
 */
internal data class LiveRoomParentFrame(
  val entryId: Long,
  val room: LiveSearchRoom,
  val childRoomId: Long,
  val childCoverBounds: Rect,
)

internal fun liveRecommendationBoundsKey(parentRoomId: Long, childRoomId: Long): String =
  "$parentRoomId:$childRoomId"

/**
 * 视频页关系栈的一帧，记录某个视频的入口信息与返回目标。
 *
 * parentPage 标记它从哪个上层页面打开，sourceCardBounds / rootFeedScrollAnchor /
 * sourceProfile 等字段在退出转场时决定封面应飞回何处、滚动位置应如何还原。
 */
internal data class StackFrame(
  val entryId: String,
  val item: FeedItem,
  val parentPage: PageOrigin,
  val sourceCardBounds: Rect?,
  val sourceProfile: ProfilePageEntry? = null,
  val inPlaceSelectionChanged: Boolean = false,
  val rootFeedScrollAnchor: FeedScrollAnchor? = null,
  val sourceWasPlaybackEndRecommendation: Boolean = false,
  val rootVideoOrigin: VideoOrigin = VideoOrigin.OTHER,
  val sourceAnchorKey: String? = null,
)

/** 个人资料页中某条视频的唯一键：由资料入口 ID 与视频条目 ID 共同组成。 */
internal data class ProfileVideoKey(val profileEntryId: Long, val itemId: String)

/**
 * 有界个人资料导航栈中，一页被独立保留的个人资料。
 *
 * 每个栈帧持有独立的 [AppRootProfileState] 状态对象，使父资料页在子资料页打开期间始终
 * 保持组合，从而保留其滚动位置、打开的动态详情、评论状态与分区选择。commentTransition /
 * avatarTransition 保存该页“返回上一级”时复用的转场会话。
 */
internal data class ProfileStackEntry(
  val entryId: Long,
  val state: AppRootProfileState,
  val commentTransition: CommentProfileTransition? = null,
  val avatarTransition: AvatarProfileTransition? = null,
  /** 此栈帧是从某个视频上方打开的，关闭时应返回该视频，而不是返回其下方的资料页。 */
  val returnsToVideo: Boolean = false,
  /** 此栈帧是从文章页打开的，关闭时必须重新揭示被保留在下面的文章页。 */
  val returnsToArticle: Boolean = false,
)

/**
 * 在另一个覆盖层抢占全局转场槽位之前，把某个资料页“返回其下层页面”的路线持久化到栈帧里。
 *
 * 已由栈帧自己持有的转场始终优先：只有当该帧尚未记录对应转场时，才用传入的
 * commentTransition / avatarTransition 补上，避免覆盖已有会话。
 */
internal fun List<ProfileStackEntry>.retainReturnTransitionsFor(
  entryId: Long,
  commentTransition: CommentProfileTransition?,
  avatarTransition: AvatarProfileTransition?,
): List<ProfileStackEntry> = map { entry ->
  if (entry.entryId != entryId) entry
  else
    entry.copy(
      commentTransition = entry.commentTransition ?: commentTransition,
      avatarTransition = entry.avatarTransition ?: avatarTransition,
    )
}

/**
 * 因打开文章页而被“挂起”的视频播放现场。
 *
 * 当用户从视频的评论链接进入专栏时，当前视频被暂停并连同其关系栈、播放状态一并快照到
 * 这里；文章关闭后可用 [AppRootArticleContext.restoreVideoSuspendedByArticle] 原样恢复。
 */
internal data class SuspendedArticleVideo(
  val item: FeedItem,
  val stack: List<StackFrame>,
  /** 评论链接打开新文章之前，已经在视频下方被保留的文章帧数量。 */
  val retainedArticleDepth: Int,
  val wasPlaying: Boolean,
  val playerWasReady: Boolean,
)

/**
 * 判断某一文章帧在当前时刻是否应可见。
 *
 * 若存在“被挂起的视频”的保留深度 retainedArticleDepth，则只有位于该深度之上的新文章段
 * 可见（索引 >= 该深度）；否则在“视频未显示”或“正从视频返回文章”时显示文章层。
 */
internal fun shouldShowArticleFrame(
  showVideo: Boolean,
  returningVideoToArticle: Boolean,
  retainedArticleDepth: Int?,
  frameIndex: Int,
): Boolean =
  retainedArticleDepth?.let { frameIndex >= it } ?: (!showVideo || returningVideoToArticle)

/** 判断当前是否正在“返回被挂起的视频”：保留深度与剩余文章深度恰好相等。 */
internal fun isReturningToSuspendedVideo(
  retainedArticleDepth: Int?,
  remainingArticleDepth: Int,
): Boolean = retainedArticleDepth != null && retainedArticleDepth == remainingArticleDepth

/**
 * 个人资料页的完整数据快照。
 *
 * 涵盖用户资料、作品列表（分页）、动态流、收藏夹/合集及其各自的加载态与错误态，以及该页
 * 保存的评论/头像返回转场会话。它被用于个人资料关系栈的保留与恢复（snapshot/restore）。
 */
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

/**
 * 从评论点击进入个人资料时的共享元素转场会话。
 *
 * 记录源评论卡片/头像的实测边界、目标头像边界与进度 Animatable，并内建转场就绪屏障
 * [preparation]，在源边界、目标挂载、目标边界稳定（以及可选的头像图片就绪）齐备后才开飞。
 */
internal data class CommentProfileTransition(
  val token: Long,
  val targetMid: Long,
  val sourceComment: CommentItem,
  val sourceBounds: Rect,
  val sourceAvatarBounds: Rect?,
  val currentSourceBounds: () -> Rect,
  val currentSourceAvatarBounds: () -> Rect,
  /** 当导航从一个资料页内部发起时，记录“点击评论所属的那个资料页”快照。 */
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
        // 首次进入评论头像资料页时，不能在飞行过程中解码共享头像，因此仅在确实需要
        // 头像位图时才要求 IMAGE_READY 信号。
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
 * 仅在资料页转场“正在飞行”时锁定全局指针输入。
 *
 * 返回转场会话刻意不作为这里的输入条件：它们会在目标资料页整个生命周期内一直被保留，
 * 以便最终返回时复用同一段退场动画。
 */
internal fun profileTransitionInputLocked(
  activeCommentTransitionBlocksInput: Boolean,
  activeAvatarTransition: Boolean,
): Boolean = activeCommentTransitionBlocksInput || activeAvatarTransition

/**
 * 从用户头像点击进入个人资料时的共享元素转场会话。
 *
 * 结构与 [CommentProfileTransition] 类似，记录源头像边界与目标头像边界、进度 Animatable
 * 及就绪屏障，驱动“头像从原卡片飞向目标资料页头部”的动画。
 */
internal data class AvatarProfileTransition(
  val token: Long,
  val targetMid: Long,
  val face: String,
  val name: String,
  val sourceBounds: Rect,
  /** 当导航从一个资料页内部发起时，记录“被点击头像所属的那个资料页”快照。 */
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

/**
 * 视频页的完整数据快照，也是 [AppRootVideoState.videoEntryCache] 的缓存条目。
 *
 * 把推荐列表、视频信息、互动数据、收藏夹、评论（含分页/排序）、弹幕、表情、当前 cid 与
 * 时长、保存的播放进度、清晰度索引及 dataReady 等一次性打包，供视频关系栈切换时无损恢复。
 */
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
  val replyRoot: CommentItem? = null,
  val replyItems: List<CommentItem> = emptyList(),
  val replyPage: Int = 1,
  val replyHasMore: Boolean = false,
  val danmaku: List<DanmakuItem>,
  val danmakuMask: DanmakuMaskTimeline?,
  val emotes: List<BiliEmote>,
  val cid: Long,
  val durationSeconds: Long,
  val savedPositionMs: Long,
  val qualityIndex: Int,
  val dataReady: Boolean,
  val playbackEnded: Boolean = false,
  val engagementAccountMid: Long = 0L,
)

/**
 * 视频退出前奏：退出转场真正开始前，先用一张不透明静止封面替换实时播放画面。
 *
 * 它承载封面位图、页面/封面透明度 Animatable 与播放器边界，保证“封面先完全盖住视频，
 * 再开始移动”的三段式退场，避免实时帧与飞行封面同帧闪烁。
 */
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

/**
 * 个人资料页中“番剧卡片返回”的请求。
 *
 * 退出番剧详情回到资料页时，用 token 标识本次请求，并携带目标资料入口与卡片 ID，
 * 让保留的资料网格把对应卡片滚动到可见位置后再执行封面飞回。
 */
internal data class ProfileBangumiReturnRequest(
  val token: Long,
  val profileEntryId: Long,
  val cardId: String,
)

/** 卡片转场的种类：区分根/推荐/资料三种进入与退出方向。 */
internal enum class TransitionKind {
  ENTER_ROOT,
  ENTER_RECOMMENDATION,
  ENTER_PROFILE,
  EXIT_ROOT,
  EXIT_RECOMMENDATION,
  EXIT_PROFILE,
}

/**
 * 转场会话的生命周期阶段。
 *
 * 从 PREPARING（收集信号）→ READY（就绪）→ FLYING（封面飞行）→ REVEALING_BACKGROUND
 * （揭示目标背景）→ WAITING_FIRST_FRAME（等待首帧）→ REVEALING（揭示内容）→ COMPLETED
 * （完成）或 CANCELLED（取消），各阶段驱动不同的内容挂载时机与面板透明度。
 */
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

/**
 * 判断当前转场是否应显示卡片转场覆盖层。
 *
 * 准备阶段（PREPARING）不显示；退出类转场即使到达 READY 阶段也继续显示（此时覆盖层作为
 * 退场遮罩），进入类转场则在 READY 之后才进入可见的飞行阶段。
 */
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

/**
 * 判断当前转场是否应抑制弹幕显示。
 *
 * 所有退出类转场都抑制弹幕；推荐页之间的进入转场仅在覆盖层可见时抑制，其余情况不干预。
 */
internal fun shouldSuppressDanmakuForCardTransition(
  kind: TransitionKind?,
  phase: SessionPhase?,
): Boolean =
  when (kind) {
    TransitionKind.EXIT_ROOT,
    TransitionKind.EXIT_RECOMMENDATION,
    TransitionKind.EXIT_PROFILE -> true
    TransitionKind.ENTER_RECOMMENDATION ->
      phase != null && shouldDisplayCardTransitionOverlay(kind, phase)
    else -> false
  }

/** 判断根退出的封面飞行阶段是否应把视频页整体隐藏在退场封面之后。 */
internal fun shouldHideVideoPageBehindExitCover(
  kind: TransitionKind?,
  phase: SessionPhase?,
): Boolean =
  kind == TransitionKind.EXIT_ROOT &&
    phase in setOf(SessionPhase.FLYING, SessionPhase.REVEALING_BACKGROUND)

/** 判断当前进入转场是否应使用“根视频入口背景”桥接层（仅根入口启用）。 */
internal fun shouldUseRootVideoEntryBackdrop(kind: TransitionKind?): Boolean =
  kind == TransitionKind.ENTER_ROOT

/** 判断从个人资料进入番剧时是否应使用资料侧的海报过渡目标（仅在进入早期阶段生效）。 */
internal fun shouldUseProfileBangumiTransitionTarget(
  sourceProfileEntryId: Long,
  kind: TransitionKind?,
  phase: SessionPhase?,
): Boolean =
  sourceProfileEntryId > 0L &&
    kind == TransitionKind.ENTER_PROFILE &&
    phase in setOf(SessionPhase.PREPARING, SessionPhase.READY, SessionPhase.FLYING)

/**
 * 计算根视频入口内容在揭示背景阶段的透明度。
 *
 * REVEALING_BACKGROUND 阶段用 smoothstep 曲线（progress²·(3−2·progress)）平滑淡入；
 * 等待首帧 / 揭示 / 完成阶段为 1，其余阶段为 0。
 */
internal fun rootVideoEntryContentAlpha(
  phase: SessionPhase?,
  backgroundRevealProgress: Float = 1f,
): Float =
  when (phase) {
    SessionPhase.REVEALING_BACKGROUND -> {
      val progress = backgroundRevealProgress.coerceIn(0f, 1f)
      progress * progress * (3f - 2f * progress)
    }
    SessionPhase.WAITING_FIRST_FRAME,
    SessionPhase.REVEALING,
    SessionPhase.COMPLETED -> 1f
    else -> 0f
  }

/** 判断是否应延迟挂载视频页的辅助内容（推荐/评论之外的次要面板）。 */
internal fun shouldDeferVideoAuxiliaryContent(
  preparingRootEnter: Boolean,
  kind: TransitionKind?,
  phase: SessionPhase?,
): Boolean {
  if (preparingRootEnter) return true
  val entering =
    when (kind) {
      TransitionKind.ENTER_ROOT,
      TransitionKind.ENTER_PROFILE -> true
      else -> false
    }
  val coverFlying =
    when (phase) {
      SessionPhase.PREPARING,
      SessionPhase.READY,
      SessionPhase.FLYING -> true
      else -> false
    }
  return entering && coverFlying
}

/** 判断是否应延迟挂载视频页的评论内容（辅助内容延迟，或根入口显式要求延迟评论）。 */
internal fun shouldDeferVideoCommentContent(
  deferAllAuxiliaryContent: Boolean,
  kind: TransitionKind?,
  deferRootEnterComments: Boolean,
): Boolean =
  deferAllAuxiliaryContent || (kind == TransitionKind.ENTER_ROOT && deferRootEnterComments)

/**
 * 视频卡片共享元素转场会话。
 *
 * 承载一次进入/退出转场的全部动态状态：起止边界、进度/封面/面板/背景/主题压暗/桥接等多路
 * Animatable、转场位图、就绪屏障与阶段 [phase]。它是 [AppRootPlaybackContext.prepareCardTransition]
 * 与 [AppRootPlaybackContext.revealTransitionSession] 之间的唯一契约对象。
 */
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
  // 根入口先挂载下层的推荐面板，下一帧再挂载评论面板。推荐页之间的导航永远不会置位该
  // 标记，因此其保留的父/子面板编排不会被改动。
  var deferRootEnterComments by mutableStateOf(kind == TransitionKind.ENTER_ROOT)
  var reverseRequested by mutableStateOf(false)
  var backgroundStarted by mutableStateOf(false)
  var timedOut by mutableStateOf(false)
}

/** 判断是否应重新加载所选视频：存在条目且播放器处于空闲状态。 */
internal fun shouldReloadSelectedVideo(item: FeedItem?, playerState: PlayerState): Boolean =
  item != null && playerState is PlayerState.Idle

/** 把接口层 FeedCard 转换为应用统一的 FeedItem（用于相关推荐等场景）。 */
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

package dev.openbili.webdemo.ui

/**
 * AppRoot 组合体的状态持有器与工厂。
 *
 * AppRoot 曾是 8000 余行的巨型 @Composable，其散落的 remember 状态既难读又撑爆 JVM
 * 方法字节码上限（64 KB）。本文件把全部跨层共享状态集中为一个数据类，并由工厂
 * [rememberAppRootStates] 统一创建：
 *  - AppRoot 侧写 `var x by s.xState`（可读写）或 `val x = s.x`（只读别名）；
 *  - 其余拆分文件（AppRootTail 等）通过参数共享同一个 `AppRootStates` 实例。
 *
 * 约束：
 *  - 字段与工厂中 remember 表达式的顺序、初始值、键必须与拆分前的源码逐字一致；
 *  - 新增状态优先放这里，而不是在 AppRoot 里再堆 remember。
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import dev.openbili.webdemo.api.ArticleDetail
import dev.openbili.webdemo.api.CommentNavigationTarget
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.article.ArticleStackFrame
import dev.openbili.webdemo.article.ArticleTransitionSession
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.live.LiveHomeSourceAnchor
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.my.MyControllerState
import dev.openbili.webdemo.video.DanmakuWindowController
import kotlinx.coroutines.Job

/**
 * AppRoot 组合体的全部共享状态。
 *
 * 字段按业务分组：控制器焦点、卡片边界映射、启动遮罩、根页签与搜索、直播、番剧
 * 索引、各类转场进度、隐藏封面项、文章、视频页、个人资料与悬浮预览等。持有纯状态
 * 对象（MutableState/Animatable/普通对象），不持有组合逻辑；逻辑留在各上下文类中。
 */
internal class AppRootStates(
  val controlInitialFocusRequester: FocusRequester,
  val homeControlSecondLevelRequestState: MutableState<Int>,
  val homeControlSearchFocusRequestState: MutableState<Int>,
  val homeControlFocusRestoreRequestState: MutableState<Int>,
  val homeControlLevelState: MutableState<HomeControlLevel>,
  val bangumiControlSecondLevelRequestState: MutableState<Int>,
  val bangumiControlFocusRestoreRequestState: MutableState<Int>,
  val bangumiControlLevelState: MutableState<BangumiControlLevel>,
  val myControllerState: MyControllerState,
  val showControlExitDialogState: MutableState<Boolean>,
  val feedCardBounds: MutableMap<String, Rect>,
  val popularCardBounds: MutableMap<String, Rect>,
  val dynamicCardBounds: MutableMap<String, Rect>,
  val homeDynamicArticleBounds: SnapshotStateMap<String, Rect>,
  val homeLiveCardBounds: MutableMap<String, Rect>,
  val myCardBounds: SnapshotStateMap<String, Rect>,
  val myInteractionVideoMessageIds: SnapshotStateMap<String, Long>,
  val searchCardBounds: SnapshotStateMap<String, Rect>,
  val bangumiIndexCardBounds: SnapshotStateMap<String, Rect>,
  val myArticleBounds: SnapshotStateMap<String, Rect>,
  val myInteractionArticleMessageIds: SnapshotStateMap<String, Long>,
  val searchArticleBounds: SnapshotStateMap<String, Rect>,
  val profileArticleBounds: SnapshotStateMap<String, Rect>,
  val articleVideoBounds: SnapshotStateMap<String, Rect>,
  val profileCardBounds: SnapshotStateMap<ProfileVideoKey, Rect>,
  val liveRecommendationCardBounds: SnapshotStateMap<String, Rect>,
  val startupWarmupFadeInProgressState: MutableState<Boolean>,
  val bangumiStartupPreloadReadyState: MutableState<Boolean>,
  val rootTabState: MutableState<RootTab>,
  val rootPageSwitchRequestedState: MutableState<Boolean>,
  val rootPageSwitchRequestTokenState: MutableState<Long>,
  val showSearchState: MutableState<Boolean>,
  val showSearchResultsState: MutableState<Boolean>,
  val searchOpenedFromControllerState: MutableState<Boolean>,
  val activeLiveRoomState: MutableState<LiveSearchRoom?>,
  val activeLiveEntryIdState: MutableState<Long>,
  val nextLiveEntryIdState: MutableState<Long>,
  val liveRoomParentStackState: MutableState<List<LiveRoomParentFrame>>,
  val hiddenLiveRecommendationCoverItemIdState: MutableState<String?>,
  val activeLiveOriginState: MutableState<PageOrigin>,
  val activeLiveSourceAnchorState: MutableState<LiveHomeSourceAnchor?>,
  val livePlayerBoundsState: MutableState<Rect>,
  val liveTransitionSessionState: MutableState<CardTransitionSession?>,
  val liveExitPreludeState: MutableState<VideoExitPrelude?>,
  val liveVideoSurfaceVisibleState: MutableState<Boolean>,
  val liveTransitionJobState: MutableState<Job?>,
  val liveFirstFrameEntryIdState: MutableState<Long>,
  val homeLivePreludeActiveState: MutableState<Boolean>,
  val homeDynamicDetailActiveState: MutableState<Boolean>,
  val homeRecommendationModeState: MutableState<HomeRecommendationMode>,
  val liveFullscreenTransitionActiveState: MutableState<Boolean>,
  val videoFullscreenTransitionActiveState: MutableState<Boolean>,
  val musicEntryInputLockedState: MutableState<Boolean>,
  val livePageAlpha: Animatable<Float, AnimationVector1D>,
  val showBangumiIndexState: MutableState<Boolean>,
  val bangumiIndexTransitionDirectionState: MutableState<SearchTransitionDirection?>,
  val bangumiIndexTransitionSourceBoundsState: MutableState<Rect>,
  val bangumiIndexTransitionProgress: Animatable<Float, AnimationVector1D>,
  val bangumiIndexTransitionMaskAlpha: Animatable<Float, AnimationVector1D>,
  val bangumiIndexTransitionScrimAlpha: Animatable<Float, AnimationVector1D>,
  val bangumiIndexTransitionJobState: MutableState<Job?>,
  val liveAreaIndex: LiveAreaIndexTransitionState,
  val liveAreaIndexFocusRestoreRequestState: MutableIntState,
  val searchTransitionDirectionState: MutableState<SearchTransitionDirection?>,
  val searchTransitionSourceBoundsState: MutableState<Rect>,
  val searchTransitionQueryState: MutableState<String>,
  val searchTransitionProgress: Animatable<Float, AnimationVector1D>,
  val searchTransitionMaskAlpha: Animatable<Float, AnimationVector1D>,
  val searchTransitionScrimAlpha: Animatable<Float, AnimationVector1D>,
  val searchTransitionJobState: MutableState<Job?>,
  val searchTransitionPreparationState: MutableState<TransitionPreparationBarrier?>,
  val transitionPhaseState: MutableState<TransitionPhase>,
  val searchBoundsState: MutableState<Rect>,
  val transitionSessionState: MutableState<CardTransitionSession?>,
  val transitionTokenState: MutableState<Long>,
  val hiddenFeedCoverItemIdState: MutableState<String?>,
  val hiddenPopularCoverItemIdState: MutableState<String?>,
  val hiddenHomeDynamicCoverItemIdState: MutableState<String?>,
  val hiddenHomeDynamicArticleItemIdState: MutableState<String?>,
  val hiddenHomeLiveCoverItemIdState: MutableState<String?>,
  val hiddenMyCoverItemIdState: MutableState<String?>,
  val hiddenSearchCoverItemIdState: MutableState<String?>,
  val hiddenBangumiIndexItemIdState: MutableState<String?>,
  val hiddenBangumiRecommendationItemIdState: MutableState<String?>,
  val hiddenArticleVideoCoverItemIdState: MutableState<String?>,
  val hiddenRecommendationCoverItemIdState: MutableState<String?>,
  val hiddenPlaybackEndRecommendationCoverItemIdState: MutableState<String?>,
  val hiddenProfileCoverItemIdState: MutableState<String?>,
  val profileVideoTransitionActiveState: MutableState<Boolean>,
  val activeBangumiPageState: MutableState<ActiveBangumiPage?>,
  val bangumiPreviewTargetState: MutableState<BangumiPreviewTarget?>,
  val bangumiCardEnterPendingState: MutableState<Boolean>,
  val bangumiPreviewMutedState: MutableState<Boolean>,
  val bangumiPosterBoundsState: MutableState<Rect>,
  val deferSearchBangumiPageCompositionState: MutableState<Boolean>,
  val deferBangumiIndexPageCompositionState: MutableState<Boolean>,
  val deferBangumiHomePageCompositionState: MutableState<Boolean>,
  val videoExitPreludeState: MutableState<VideoExitPrelude?>,
  val videoStackState: MutableState<List<StackFrame>>,
  val articleStackState: MutableState<List<ArticleStackFrame>>,
  val articleEntryTokenState: MutableState<Long>,
  val articleDetailState: MutableState<ArticleDetail?>,
  val articleDetailCache: SnapshotStateMap<Long, ArticleDetail>,
  val articleLoadingState: MutableState<Boolean>,
  val articleErrorState: MutableState<String?>,
  val articleHeroBoundsState: MutableState<Rect>,
  val articleTransitionSessionState: MutableState<ArticleTransitionSession?>,
  val articleTransitionJobState: MutableState<Job?>,
  val articleLoadTokenState: MutableState<Long>,
  val articleContentReadyState: MutableState<Boolean>,
  val articleRestoringParentEntryIdState: MutableState<Long?>,
  val articleSuspendedVideoState: MutableState<SuspendedArticleVideo?>,
  val hiddenMyArticleItemIdState: MutableState<String?>,
  val hiddenSearchArticleItemIdState: MutableState<String?>,
  val hiddenProfileArticleItemIdState: MutableState<String?>,
  val hiddenVideoCommentArticleItemIdState: MutableState<String?>,
  val hiddenArticleCommentArticleItemIdState: MutableState<String?>,
  val pendingVideoCommentTargetState: MutableState<CommentNavigationTarget?>,
  val pendingArticleCommentTargetState: MutableState<CommentNavigationTarget?>,
  val commentNavigationRequestTokenState: MutableState<Long>,
  val interactionTargetLoadingIdState: MutableState<Long?>,
  val articlePageAlpha: Animatable<Float, AnimationVector1D>,
  val videoState: AppRootVideoState,
  val danmakuWindowController: DanmakuWindowController,
  val dataCommitAllowedIdState: MutableState<String?>,
  val playerActivationIdState: MutableState<String?>,
  val showEmbeddedCoverState: MutableState<Boolean>,
  val playerBoundsState: MutableState<Rect>,
  val videoPageDataReadyIdState: MutableState<String?>,
  val activeTransitionJobState: MutableState<Job?>,
  val activeRevealJobState: MutableState<Job?>,
  val directHomeAlpha: Animatable<Float, AnimationVector1D>,
  val bangumiSeasonExitFadeAlpha: Animatable<Float, AnimationVector1D>,
  val directHomeInProgressState: MutableState<Boolean>,
  val previewItemState: MutableState<FeedItem?>,
  val previewInfoState: MutableState<VideoInfo?>,
  val previewFromHomeFeedState: MutableState<Boolean>,
  val dismissedFeedItemIdsState: MutableState<Set<String>>,
  val profileState: AppRootProfileState,
  val profileEntryTokenState: MutableState<Long>,
  val profileStackState: MutableState<List<ProfileStackEntry>>,
  val profileLayerSuppressedState: MutableState<Boolean>,
  val profileBangumiReturnRequestState: MutableState<ProfileBangumiReturnRequest?>,
  val playerViewHolder: Array<HeldPlayerView?>,
  val commentImagePreviewActiveState: MutableState<Boolean>,
)

/**
 * 在组合中创建并记住 [AppRootStates] 全部状态。
 *
 * 只有依赖外部值的初始表达式（如 transitionPhaseState 的初始页、dataCommitAllowedId
 * 的初始选中视频）引用参数 appState/settings；其余 remember 均无键，按槽位恒定，
 * 与拆分前 AppRoot 内的原始声明语义一致。
 */
@Composable
internal fun rememberAppRootStates(
  appState: dev.openbili.webdemo.AppUiState,
  settings: dev.openbili.webdemo.settings.AppSettings,
): AppRootStates {
  // ── 控制器模式：二级请求令牌与焦点还原请求、当前层级、退出确认弹窗 ──────────
  val controlInitialFocusRequester = remember { FocusRequester() }
  val homeControlSecondLevelRequestState = remember { mutableStateOf(0) }
  val homeControlSearchFocusRequestState = remember { mutableStateOf(0) }
  val homeControlFocusRestoreRequestState = remember { mutableStateOf(0) }
  val homeControlLevelState = remember { mutableStateOf(HomeControlLevel.ROOT) }
  val bangumiControlSecondLevelRequestState = remember { mutableStateOf(0) }
  val bangumiControlFocusRestoreRequestState = remember { mutableStateOf(0) }
  val bangumiControlLevelState = remember { mutableStateOf(BangumiControlLevel.ROOT) }
  val myControllerState = remember { MyControllerState() }
  val showControlExitDialogState = rememberSaveable { mutableStateOf(false) }
  // ── 各页面卡片实测边界映射：共享转场的来源/目标锚点 ─────────────────────────
  val feedCardBounds = remember { mutableMapOf<String, Rect>() }
  val popularCardBounds = remember { mutableMapOf<String, Rect>() }
  val dynamicCardBounds = remember { mutableMapOf<String, Rect>() }
  val homeDynamicArticleBounds = remember { mutableStateMapOf<String, Rect>() }
  val homeLiveCardBounds = remember { mutableMapOf<String, Rect>() }
  val myCardBounds = remember { mutableStateMapOf<String, Rect>() }
  val myInteractionVideoMessageIds = remember { mutableStateMapOf<String, Long>() }
  val searchCardBounds = remember { mutableStateMapOf<String, Rect>() }
  val bangumiIndexCardBounds = remember { mutableStateMapOf<String, Rect>() }
  val myArticleBounds = remember { mutableStateMapOf<String, Rect>() }
  val myInteractionArticleMessageIds = remember { mutableStateMapOf<String, Long>() }
  val searchArticleBounds = remember { mutableStateMapOf<String, Rect>() }
  val profileArticleBounds = remember { mutableStateMapOf<String, Rect>() }
  val articleVideoBounds = remember { mutableStateMapOf<String, Rect>() }
  val profileCardBounds = remember { mutableStateMapOf<ProfileVideoKey, Rect>() }
  val liveRecommendationCardBounds = remember { mutableStateMapOf<String, Rect>() }
  // ── 启动遮罩：预热淡入标记、番剧启动预载就绪位 ─────────────────────────────
  val startupWarmupFadeInProgressState = remember { mutableStateOf(false) }
  // 预载是否完成与启动遮罩是否启用是两件事。关闭遮罩时也必须先在后台解码“本期推荐”，
  // 否则控制器切到番剧页后才会开始加载首屏图片。
  val bangumiStartupPreloadReadyState = remember { mutableStateOf(false) }
  // ── 根页签与根页面切换请求 ───────────────────────────────────────────────
  val rootTabState = rememberSaveable { mutableStateOf(RootTab.HOME) }
  val rootPageSwitchRequestedState = remember { mutableStateOf(false) }
  val rootPageSwitchRequestTokenState = remember { mutableStateOf(0L) }
  val showSearchState = rememberSaveable { mutableStateOf(false) }
  val showSearchResultsState = rememberSaveable { mutableStateOf(false) }
  val searchOpenedFromControllerState = remember { mutableStateOf(false) }
  // ── 直播：当前房间、关系栈、来源锚点、播放器边界与转场 ─────────────────────
  val activeLiveRoomState = remember { mutableStateOf<LiveSearchRoom?>(null) }
  val activeLiveEntryIdState = remember { mutableStateOf(0L) }
  val nextLiveEntryIdState = remember { mutableStateOf(0L) }
  val liveRoomParentStackState = remember { mutableStateOf<List<LiveRoomParentFrame>>(emptyList()) }
  val hiddenLiveRecommendationCoverItemIdState = remember { mutableStateOf<String?>(null) }
  val activeLiveOriginState = remember { mutableStateOf<PageOrigin>(PageOrigin.Search) }
  val activeLiveSourceAnchorState = remember { mutableStateOf<LiveHomeSourceAnchor?>(null) }
  val livePlayerBoundsState = remember { mutableStateOf(Rect.Zero) }
  val liveTransitionSessionState = remember { mutableStateOf<CardTransitionSession?>(null) }
  val liveExitPreludeState = remember { mutableStateOf<VideoExitPrelude?>(null) }
  val liveVideoSurfaceVisibleState = remember { mutableStateOf(true) }
  val liveTransitionJobState = remember { mutableStateOf<Job?>(null) }
  val liveFirstFrameEntryIdState = remember { mutableStateOf(0L) }
  val homeLivePreludeActiveState = remember { mutableStateOf(false) }
  val homeDynamicDetailActiveState = remember { mutableStateOf(false) }
  val homeRecommendationModeState = rememberSaveable {     mutableStateOf(HomeRecommendationMode.NORMAL)   }
  val liveFullscreenTransitionActiveState = remember { mutableStateOf(false) }
  val videoFullscreenTransitionActiveState = remember { mutableStateOf(false) }
  val musicEntryInputLockedState = remember { mutableStateOf(false) }
  val livePageAlpha = remember { Animatable(1f) }
  // ── 番剧索引与搜索的进出转场：方向/来源边界/进度/遮罩/压暗 ─────────────────
  val showBangumiIndexState = rememberSaveable { mutableStateOf(false) }
  val bangumiIndexTransitionDirectionState = remember {     mutableStateOf<SearchTransitionDirection?>(null)   }
  val bangumiIndexTransitionSourceBoundsState = remember { mutableStateOf(Rect.Zero) }
  val bangumiIndexTransitionProgress = remember { Animatable(0f) }
  val bangumiIndexTransitionMaskAlpha = remember { Animatable(1f) }
  val bangumiIndexTransitionScrimAlpha = remember { Animatable(0f) }
  val bangumiIndexTransitionJobState = remember { mutableStateOf<Job?>(null) }
  val liveAreaIndex = remember { LiveAreaIndexTransitionState() }
  val liveAreaIndexFocusRestoreRequestState = remember { mutableIntStateOf(0) }
  val searchTransitionDirectionState = remember { mutableStateOf<SearchTransitionDirection?>(null) }
  val searchTransitionSourceBoundsState = remember { mutableStateOf(Rect.Zero) }
  val searchTransitionQueryState = remember { mutableStateOf("") }
  val searchTransitionProgress = remember { Animatable(0f) }
  val searchTransitionMaskAlpha = remember { Animatable(1f) }
  val searchTransitionScrimAlpha = remember { Animatable(0f) }
  val searchTransitionJobState = remember { mutableStateOf<Job?>(null) }
  val searchTransitionPreparationState = remember {     mutableStateOf<TransitionPreparationBarrier?>(null)   }
  val transitionPhaseState = remember {     mutableStateOf<TransitionPhase>(       appState.selectedVideo?.let { TransitionPhase.Video(it, null) } ?: TransitionPhase.Feed     )   }
  val searchBoundsState = remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
  val transitionSessionState = remember { mutableStateOf<CardTransitionSession?>(null) }
  val transitionTokenState = remember { mutableStateOf(0L) }
  // ── 各页面"转场期间隐藏封面"的条目 ID ────────────────────────────────────
  val hiddenFeedCoverItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenPopularCoverItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenHomeDynamicCoverItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenHomeDynamicArticleItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenHomeLiveCoverItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenMyCoverItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenSearchCoverItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenBangumiIndexItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenBangumiRecommendationItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenArticleVideoCoverItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenRecommendationCoverItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenPlaybackEndRecommendationCoverItemIdState = remember {     mutableStateOf<String?>(null)   }
  val hiddenProfileCoverItemIdState = remember { mutableStateOf<String?>(null) }
  val profileVideoTransitionActiveState = remember { mutableStateOf(false) }
  val activeBangumiPageState = remember { mutableStateOf<ActiveBangumiPage?>(null) }
  val bangumiPreviewTargetState = remember { mutableStateOf<BangumiPreviewTarget?>(null) }
  val bangumiCardEnterPendingState = remember { mutableStateOf(false) }
  val bangumiPreviewMutedState = rememberSaveable { mutableStateOf(true) }
  val bangumiPosterBoundsState = remember { mutableStateOf(Rect.Zero) }
  val deferSearchBangumiPageCompositionState = remember { mutableStateOf(false) }
  val deferBangumiIndexPageCompositionState = remember { mutableStateOf(false) }
  val deferBangumiHomePageCompositionState = remember { mutableStateOf(false) }
  // ── 视频页与文章页：退出前奏、栈、转场会话与内容就绪标记 ──────────────────
  val videoExitPreludeState = remember { mutableStateOf<VideoExitPrelude?>(null) }
  val videoStackState = remember { mutableStateOf<List<StackFrame>>(emptyList()) }
  val articleStackState = remember { mutableStateOf<List<ArticleStackFrame>>(emptyList()) }
  val articleEntryTokenState = remember { mutableStateOf(0L) }
  val articleDetailState = remember { mutableStateOf<ArticleDetail?>(null) }
  val articleDetailCache = remember { mutableStateMapOf<Long, ArticleDetail>() }
  val articleLoadingState = remember { mutableStateOf(false) }
  val articleErrorState = remember { mutableStateOf<String?>(null) }
  val articleHeroBoundsState = remember { mutableStateOf(Rect.Zero) }
  val articleTransitionSessionState = remember { mutableStateOf<ArticleTransitionSession?>(null) }
  val articleTransitionJobState = remember { mutableStateOf<Job?>(null) }
  val articleLoadTokenState = remember { mutableStateOf(0L) }
  val articleContentReadyState = remember { mutableStateOf(false) }
  val articleRestoringParentEntryIdState = remember { mutableStateOf<Long?>(null) }
  val articleSuspendedVideoState = remember { mutableStateOf<SuspendedArticleVideo?>(null) }
  val hiddenMyArticleItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenSearchArticleItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenProfileArticleItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenVideoCommentArticleItemIdState = remember { mutableStateOf<String?>(null) }
  val hiddenArticleCommentArticleItemIdState = remember { mutableStateOf<String?>(null) }
  val pendingVideoCommentTargetState = remember { mutableStateOf<CommentNavigationTarget?>(null) }
  val pendingArticleCommentTargetState = remember { mutableStateOf<CommentNavigationTarget?>(null) }
  val commentNavigationRequestTokenState = remember { mutableStateOf(0L) }
  val interactionTargetLoadingIdState = remember { mutableStateOf<Long?>(null) }
  val articlePageAlpha = remember { Animatable(0f) }
  // ── 视频页核心对象与数据提交闸门 ─────────────────────────────────────────
  val videoState = remember { AppRootVideoState() }
  val danmakuWindowController = remember { DanmakuWindowController() }
  val dataCommitAllowedIdState = remember { mutableStateOf<String?>(appState.selectedVideo?.id) }
  val playerActivationIdState = remember { mutableStateOf<String?>(appState.selectedVideo?.id) }
  val showEmbeddedCoverState = remember { mutableStateOf(appState.selectedVideo != null) }
  val playerBoundsState = remember { mutableStateOf(Rect.Zero) }
  val videoPageDataReadyIdState = remember { mutableStateOf<String?>(null) }
  val activeTransitionJobState = remember { mutableStateOf<Job?>(null) }
  val activeRevealJobState = remember { mutableStateOf<Job?>(null) }
  val directHomeAlpha = remember { Animatable(1f) }
  val bangumiSeasonExitFadeAlpha = remember { Animatable(0f) }
  val directHomeInProgressState = remember { mutableStateOf(false) }
  val previewItemState = remember { mutableStateOf<FeedItem?>(null) }
  val previewInfoState = remember { mutableStateOf<VideoInfo?>(null) }
  val previewFromHomeFeedState = remember { mutableStateOf(false) }
  val dismissedFeedItemIdsState = remember { mutableStateOf<Set<String>>(emptySet()) }
  // ── 个人资料页：状态对象、关系栈与图层抑制 ───────────────────────────────
  val profileState = remember { AppRootProfileState() }
  val profileEntryTokenState = remember { mutableStateOf(0L) }
  val profileStackState = remember { mutableStateOf<List<ProfileStackEntry>>(emptyList()) }
  val profileLayerSuppressedState = remember { mutableStateOf(false) }
  val profileBangumiReturnRequestState = remember {     mutableStateOf<ProfileBangumiReturnRequest?>(null)   }
  val playerViewHolder = remember { arrayOfNulls<HeldPlayerView>(1) }
  val commentImagePreviewActiveState = remember { mutableStateOf(false) }
  return AppRootStates(
    controlInitialFocusRequester = controlInitialFocusRequester,
    homeControlSecondLevelRequestState = homeControlSecondLevelRequestState,
    homeControlSearchFocusRequestState = homeControlSearchFocusRequestState,
    homeControlFocusRestoreRequestState = homeControlFocusRestoreRequestState,
    homeControlLevelState = homeControlLevelState,
    bangumiControlSecondLevelRequestState = bangumiControlSecondLevelRequestState,
    bangumiControlFocusRestoreRequestState = bangumiControlFocusRestoreRequestState,
    bangumiControlLevelState = bangumiControlLevelState,
    myControllerState = myControllerState,
    showControlExitDialogState = showControlExitDialogState,
    feedCardBounds = feedCardBounds,
    popularCardBounds = popularCardBounds,
    dynamicCardBounds = dynamicCardBounds,
    homeDynamicArticleBounds = homeDynamicArticleBounds,
    homeLiveCardBounds = homeLiveCardBounds,
    myCardBounds = myCardBounds,
    myInteractionVideoMessageIds = myInteractionVideoMessageIds,
    searchCardBounds = searchCardBounds,
    bangumiIndexCardBounds = bangumiIndexCardBounds,
    myArticleBounds = myArticleBounds,
    myInteractionArticleMessageIds = myInteractionArticleMessageIds,
    searchArticleBounds = searchArticleBounds,
    profileArticleBounds = profileArticleBounds,
    articleVideoBounds = articleVideoBounds,
    profileCardBounds = profileCardBounds,
    liveRecommendationCardBounds = liveRecommendationCardBounds,
    startupWarmupFadeInProgressState = startupWarmupFadeInProgressState,
    bangumiStartupPreloadReadyState = bangumiStartupPreloadReadyState,
    rootTabState = rootTabState,
    rootPageSwitchRequestedState = rootPageSwitchRequestedState,
    rootPageSwitchRequestTokenState = rootPageSwitchRequestTokenState,
    showSearchState = showSearchState,
    showSearchResultsState = showSearchResultsState,
    searchOpenedFromControllerState = searchOpenedFromControllerState,
    activeLiveRoomState = activeLiveRoomState,
    activeLiveEntryIdState = activeLiveEntryIdState,
    nextLiveEntryIdState = nextLiveEntryIdState,
    liveRoomParentStackState = liveRoomParentStackState,
    hiddenLiveRecommendationCoverItemIdState = hiddenLiveRecommendationCoverItemIdState,
    activeLiveOriginState = activeLiveOriginState,
    activeLiveSourceAnchorState = activeLiveSourceAnchorState,
    livePlayerBoundsState = livePlayerBoundsState,
    liveTransitionSessionState = liveTransitionSessionState,
    liveExitPreludeState = liveExitPreludeState,
    liveVideoSurfaceVisibleState = liveVideoSurfaceVisibleState,
    liveTransitionJobState = liveTransitionJobState,
    liveFirstFrameEntryIdState = liveFirstFrameEntryIdState,
    homeLivePreludeActiveState = homeLivePreludeActiveState,
    homeDynamicDetailActiveState = homeDynamicDetailActiveState,
    homeRecommendationModeState = homeRecommendationModeState,
    liveFullscreenTransitionActiveState = liveFullscreenTransitionActiveState,
    videoFullscreenTransitionActiveState = videoFullscreenTransitionActiveState,
    musicEntryInputLockedState = musicEntryInputLockedState,
    livePageAlpha = livePageAlpha,
    showBangumiIndexState = showBangumiIndexState,
    bangumiIndexTransitionDirectionState = bangumiIndexTransitionDirectionState,
    bangumiIndexTransitionSourceBoundsState = bangumiIndexTransitionSourceBoundsState,
    bangumiIndexTransitionProgress = bangumiIndexTransitionProgress,
    bangumiIndexTransitionMaskAlpha = bangumiIndexTransitionMaskAlpha,
    bangumiIndexTransitionScrimAlpha = bangumiIndexTransitionScrimAlpha,
    bangumiIndexTransitionJobState = bangumiIndexTransitionJobState,
    liveAreaIndex = liveAreaIndex,
    liveAreaIndexFocusRestoreRequestState = liveAreaIndexFocusRestoreRequestState,
    searchTransitionDirectionState = searchTransitionDirectionState,
    searchTransitionSourceBoundsState = searchTransitionSourceBoundsState,
    searchTransitionQueryState = searchTransitionQueryState,
    searchTransitionProgress = searchTransitionProgress,
    searchTransitionMaskAlpha = searchTransitionMaskAlpha,
    searchTransitionScrimAlpha = searchTransitionScrimAlpha,
    searchTransitionJobState = searchTransitionJobState,
    searchTransitionPreparationState = searchTransitionPreparationState,
    transitionPhaseState = transitionPhaseState,
    searchBoundsState = searchBoundsState,
    transitionSessionState = transitionSessionState,
    transitionTokenState = transitionTokenState,
    hiddenFeedCoverItemIdState = hiddenFeedCoverItemIdState,
    hiddenPopularCoverItemIdState = hiddenPopularCoverItemIdState,
    hiddenHomeDynamicCoverItemIdState = hiddenHomeDynamicCoverItemIdState,
    hiddenHomeDynamicArticleItemIdState = hiddenHomeDynamicArticleItemIdState,
    hiddenHomeLiveCoverItemIdState = hiddenHomeLiveCoverItemIdState,
    hiddenMyCoverItemIdState = hiddenMyCoverItemIdState,
    hiddenSearchCoverItemIdState = hiddenSearchCoverItemIdState,
    hiddenBangumiIndexItemIdState = hiddenBangumiIndexItemIdState,
    hiddenBangumiRecommendationItemIdState = hiddenBangumiRecommendationItemIdState,
    hiddenArticleVideoCoverItemIdState = hiddenArticleVideoCoverItemIdState,
    hiddenRecommendationCoverItemIdState = hiddenRecommendationCoverItemIdState,
    hiddenPlaybackEndRecommendationCoverItemIdState = hiddenPlaybackEndRecommendationCoverItemIdState,
    hiddenProfileCoverItemIdState = hiddenProfileCoverItemIdState,
    profileVideoTransitionActiveState = profileVideoTransitionActiveState,
    activeBangumiPageState = activeBangumiPageState,
    bangumiPreviewTargetState = bangumiPreviewTargetState,
    bangumiCardEnterPendingState = bangumiCardEnterPendingState,
    bangumiPreviewMutedState = bangumiPreviewMutedState,
    bangumiPosterBoundsState = bangumiPosterBoundsState,
    deferSearchBangumiPageCompositionState = deferSearchBangumiPageCompositionState,
    deferBangumiIndexPageCompositionState = deferBangumiIndexPageCompositionState,
    deferBangumiHomePageCompositionState = deferBangumiHomePageCompositionState,
    videoExitPreludeState = videoExitPreludeState,
    videoStackState = videoStackState,
    articleStackState = articleStackState,
    articleEntryTokenState = articleEntryTokenState,
    articleDetailState = articleDetailState,
    articleDetailCache = articleDetailCache,
    articleLoadingState = articleLoadingState,
    articleErrorState = articleErrorState,
    articleHeroBoundsState = articleHeroBoundsState,
    articleTransitionSessionState = articleTransitionSessionState,
    articleTransitionJobState = articleTransitionJobState,
    articleLoadTokenState = articleLoadTokenState,
    articleContentReadyState = articleContentReadyState,
    articleRestoringParentEntryIdState = articleRestoringParentEntryIdState,
    articleSuspendedVideoState = articleSuspendedVideoState,
    hiddenMyArticleItemIdState = hiddenMyArticleItemIdState,
    hiddenSearchArticleItemIdState = hiddenSearchArticleItemIdState,
    hiddenProfileArticleItemIdState = hiddenProfileArticleItemIdState,
    hiddenVideoCommentArticleItemIdState = hiddenVideoCommentArticleItemIdState,
    hiddenArticleCommentArticleItemIdState = hiddenArticleCommentArticleItemIdState,
    pendingVideoCommentTargetState = pendingVideoCommentTargetState,
    pendingArticleCommentTargetState = pendingArticleCommentTargetState,
    commentNavigationRequestTokenState = commentNavigationRequestTokenState,
    interactionTargetLoadingIdState = interactionTargetLoadingIdState,
    articlePageAlpha = articlePageAlpha,
    videoState = videoState,
    danmakuWindowController = danmakuWindowController,
    dataCommitAllowedIdState = dataCommitAllowedIdState,
    playerActivationIdState = playerActivationIdState,
    showEmbeddedCoverState = showEmbeddedCoverState,
    playerBoundsState = playerBoundsState,
    videoPageDataReadyIdState = videoPageDataReadyIdState,
    activeTransitionJobState = activeTransitionJobState,
    activeRevealJobState = activeRevealJobState,
    directHomeAlpha = directHomeAlpha,
    bangumiSeasonExitFadeAlpha = bangumiSeasonExitFadeAlpha,
    directHomeInProgressState = directHomeInProgressState,
    previewItemState = previewItemState,
    previewInfoState = previewInfoState,
    previewFromHomeFeedState = previewFromHomeFeedState,
    dismissedFeedItemIdsState = dismissedFeedItemIdsState,
    profileState = profileState,
    profileEntryTokenState = profileEntryTokenState,
    profileStackState = profileStackState,
    profileLayerSuppressedState = profileLayerSuppressedState,
    profileBangumiReturnRequestState = profileBangumiReturnRequestState,
    playerViewHolder = playerViewHolder,
    commentImagePreviewActiveState = commentImagePreviewActiveState,
  )
}

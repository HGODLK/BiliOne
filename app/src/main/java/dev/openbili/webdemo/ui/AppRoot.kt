package dev.openbili.webdemo.ui

/**
 * AppRoot 根组合体（拆分后的前半部分）。
 *
 * 原 AppRoot 是一个 8000 余行的巨型 @Composable，因单个方法字节码超过 JVM 的 64KB
 * 上限而被拆成三份：状态集中在 [AppRootStates]（见 AppRootStates.kt），本文件承载根
 * 组合体的“前半段”，[appRootTailContent]（见 AppRootTail.kt）承载“后半段”。
 *
 * 本文件职责：
 *  - 收集全部 ViewModel 状态，并解构为局部可读写变量；
 *  - 注册生命周期/网络监听、启动遮罩、播放器预热、根页签滚动等副作用
 *    （LaunchedEffect / DisposableEffect）；
 *  - 构造播放器上下文 [AppRootPlaybackContext]、资料页上下文 [AppRootProfileContext] 与
 *    视频页上下文 [AppRootVideoPageContext]，并把其入口包装成局部转发函数；
 *  - 最后调用 [appRootTailContent]，交由尾部继续构造 feed/video 等上下文并完成最终 UI。
 */

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.ArticleDetail
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.BangumiExploreSectionKind
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.BangumiSection
import dev.openbili.webdemo.api.BiliArticleApi
import dev.openbili.webdemo.api.BiliBangumiApi
import dev.openbili.webdemo.api.BiliCommentApi
import dev.openbili.webdemo.api.BiliDanmakuApi
import dev.openbili.webdemo.api.BiliFollowApi
import dev.openbili.webdemo.api.BiliReportApi
import dev.openbili.webdemo.api.BiliVideoApi
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.CommentNavigationTarget
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.DanmakuMaskParser
import dev.openbili.webdemo.api.MessageTargetKind
import dev.openbili.webdemo.api.RiskControlManager
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.VideoEngagement
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.article.ArticleOrigin
import dev.openbili.webdemo.article.ArticleScreen
import dev.openbili.webdemo.article.ArticleStackFrame
import dev.openbili.webdemo.article.ArticleTransitionOverlay
import dev.openbili.webdemo.article.ArticleTransitionSession
import dev.openbili.webdemo.AuthViewModel
import dev.openbili.webdemo.bangumi.BangumiExploreViewModel
import dev.openbili.webdemo.bangumi.BangumiIndexViewModel
import dev.openbili.webdemo.bangumi.BangumiRecommendationViewModel
import dev.openbili.webdemo.BangumiLocalHistoryStore
import dev.openbili.webdemo.BangumiPlaybackStore
import dev.openbili.webdemo.feed.CoverImageRequestFactory
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedPerformanceConfig
import dev.openbili.webdemo.feed.FeedScrollAnchor
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import dev.openbili.webdemo.feed.LocalCoverImageLoadingEnabled
import dev.openbili.webdemo.live.currentDisplayCoverUrl
import dev.openbili.webdemo.live.LiveHomeSourceAnchor
import dev.openbili.webdemo.live.LiveHomeViewModel
import dev.openbili.webdemo.live.LiveRoomScreen
import dev.openbili.webdemo.live.LiveRoomViewModel
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.LoginState
import dev.openbili.webdemo.MainViewModel
import dev.openbili.webdemo.my.contains
import dev.openbili.webdemo.my.MyScreen
import dev.openbili.webdemo.my.MyViewModel
import dev.openbili.webdemo.my.ProfilePrivateConversationPane
import dev.openbili.webdemo.my.WatchLaterViewModel
import dev.openbili.webdemo.offline.OfflineMediaManager
import dev.openbili.webdemo.PlaybackProgressStore
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.resolvePlaybackPage
import dev.openbili.webdemo.search.SearchResultsScreen
import dev.openbili.webdemo.search.SearchScreen
import dev.openbili.webdemo.search.SearchViewModel
import dev.openbili.webdemo.settings.AppSettingsViewModel
import dev.openbili.webdemo.settings.preferredResolutionModeFor
import dev.openbili.webdemo.video.BangumiPageUi
import dev.openbili.webdemo.video.CommentProfileAnchor
import dev.openbili.webdemo.video.DanmakuOcclusionRegistry
import dev.openbili.webdemo.video.DanmakuOverlayView
import dev.openbili.webdemo.video.DanmakuWindowController
import dev.openbili.webdemo.video.PlaybackPageGlassBackdrop
import dev.openbili.webdemo.video.VideoInfoTile
import dev.openbili.webdemo.video.VideoScreen
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 播放器（进入）转场所需的就绪信号集合：来源边界、封面图就绪、目标已挂载、目标边界稳定
// 四项齐备后才允许启动卡片飞行转场，避免在半挂载状态下抢先开始动画。
internal val playerTransitionRequiredSignals =
  setOf(
    TransitionReadySignal.SOURCE_BOUNDS,
    TransitionReadySignal.IMAGE_READY,
    TransitionReadySignal.TARGET_MOUNTED,
    TransitionReadySignal.TARGET_BOUNDS_STABLE,
  )

// 退出转场所需的就绪信号集合：比进入转场多一项 SOURCE_SNAPSHOT（来源快照），
// 因为退出前需要先截取当前画面作为飞离卡片的贴图。
internal val exitTransitionRequiredSignals =
  setOf(
    TransitionReadySignal.SOURCE_BOUNDS,
    TransitionReadySignal.SOURCE_SNAPSHOT,
    TransitionReadySignal.IMAGE_READY,
    TransitionReadySignal.TARGET_MOUNTED,
    TransitionReadySignal.TARGET_BOUNDS_STABLE,
  )

// 嵌套资料页头部淡出动画的时长（毫秒）。
internal const val NESTED_PROFILE_HEADER_FADE_OUT_MS = 140L

/**
 * 应用根组合体：承载单页应用的顶层状态与转场编排。
 *
 * 它不直接渲染具体页面，而是把各 ViewModel 的状态收集并解构为局部变量后：
 *  - 通过 [rememberAppRootStates] 拿到跨文件共享的 [AppRootStates]；
 *  - 编排根页签滚动、启动遮罩淡出、播放器预热、番剧预载、播放器归属等关键效果；
 *  - 构造播放器/资料页/视频页三个上下文，并暴露给后续 UI 的转发函数；
 *  - 最终把控制权交给 [appRootTailContent]，由尾部完成剩余效果链与最终 UI 调用。
 */
@OptIn(UnstableApi::class)
@Composable
fun AppRoot(
  mainViewModel: MainViewModel,
  feedViewModel: FeedViewModel,
  authViewModel: AuthViewModel,
  playerViewModel: PlayerViewModel,
  myViewModel: MyViewModel,
  profileMessageViewModel: MyViewModel,
  searchViewModel: SearchViewModel,
  settingsViewModel: AppSettingsViewModel,
  bangumiRecommendationViewModel: BangumiRecommendationViewModel,
  onSearch: () -> Unit,
  onFeedRefresh: () -> Unit,
  onFeedPullRefresh: (Int) -> Unit,
  onExitRequested: () -> Unit,
) {
  val appStateState = mainViewModel.state.collectAsState()
  val appState by appStateState
  val feedState by feedViewModel.state.collectAsState()
  val userInfo by feedViewModel.userInfo.collectAsState()
  val loginStateState = authViewModel.loginState.collectAsState()
  val loginState by loginStateState
  val authUserInfoState = authViewModel.userInfo.collectAsState()
  val authUserInfo by authUserInfoState
  val profileIpAuthorizedState = authViewModel.appAccessAuthorized.collectAsState()
  val profileIpAuthorized by profileIpAuthorizedState
  val playerStateState = playerViewModel.playerState.collectAsState()
  val playerState by playerStateState
  val subtitleState by playerViewModel.subtitleState.collectAsState()
  val renderedVideoId by playerViewModel.renderedVideoId.collectAsState()
  val renderedVideoFrameCount by playerViewModel.renderedVideoFrameCount.collectAsState()
  val myState by myViewModel.state.collectAsState()
  val watchLaterViewModel: WatchLaterViewModel = viewModel()
  val watchLaterState by watchLaterViewModel.state.collectAsState()
  val profileMessageState by profileMessageViewModel.state.collectAsState()
  val searchState by searchViewModel.state.collectAsState()
  val settingsState = settingsViewModel.state.collectAsState()
  val settings by settingsState
  val bangumiRecommendationState by bangumiRecommendationViewModel.state.collectAsState()
  val bangumiExploreViewModel: BangumiExploreViewModel = viewModel()
  val bangumiIndexViewModel: BangumiIndexViewModel = viewModel()
  val bangumiIndexState by bangumiIndexViewModel.state.collectAsState()
  val riskChallenge by RiskControlManager.challenge.collectAsState()
  val context = LocalContext.current
  fun currentPreferredResolutionMode() = settings.preferredResolutionModeFor(context)

  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val rootDensity = LocalDensity.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val controlMode = LocalControlMode.current
  val s = rememberAppRootStates(appState = appState, settings = settings)
  var homeControlSecondLevelRequest by s.homeControlSecondLevelRequestState
  var homeControlSearchFocusRequest by s.homeControlSearchFocusRequestState
  var homeControlFocusRestoreRequest by s.homeControlFocusRestoreRequestState
  var homeControlLevel by s.homeControlLevelState
  var showControlExitDialog by s.showControlExitDialogState
  var startupWarmupFadeInProgress by s.startupWarmupFadeInProgressState
  var bangumiStartupPreloadReady by s.bangumiStartupPreloadReadyState
  var rootTab by s.rootTabState
  var rootPageSwitchRequested by s.rootPageSwitchRequestedState
  var rootPageSwitchRequestToken by s.rootPageSwitchRequestTokenState
  var showSearch by s.showSearchState
  var showSearchResults by s.showSearchResultsState
  var searchOpenedFromController by s.searchOpenedFromControllerState
  var activeLiveRoom by s.activeLiveRoomState
  var activeLiveEntryId by s.activeLiveEntryIdState
  var nextLiveEntryId by s.nextLiveEntryIdState
  var liveRoomParentStack by s.liveRoomParentStackState
  var hiddenLiveRecommendationCoverItemId by s.hiddenLiveRecommendationCoverItemIdState
  var activeLiveOrigin by s.activeLiveOriginState
  var activeLiveSourceAnchor by s.activeLiveSourceAnchorState
  var livePlayerBounds by s.livePlayerBoundsState
  var liveTransitionSession by s.liveTransitionSessionState
  var liveExitPrelude by s.liveExitPreludeState
  var liveVideoSurfaceVisible by s.liveVideoSurfaceVisibleState
  var liveTransitionJob by s.liveTransitionJobState
  var liveFirstFrameEntryId by s.liveFirstFrameEntryIdState
  var homeLivePreludeActive by s.homeLivePreludeActiveState
  var homeDynamicDetailActive by s.homeDynamicDetailActiveState
  var homeRecommendationMode by s.homeRecommendationModeState
  var liveFullscreenTransitionActive by s.liveFullscreenTransitionActiveState
  var videoFullscreenTransitionActive by s.videoFullscreenTransitionActiveState
  var musicEntryInputLocked by s.musicEntryInputLockedState
  var showBangumiIndex by s.showBangumiIndexState
  var bangumiIndexTransitionDirection by s.bangumiIndexTransitionDirectionState
  var bangumiIndexTransitionSourceBounds by s.bangumiIndexTransitionSourceBoundsState
  var bangumiIndexTransitionJob by s.bangumiIndexTransitionJobState
  var liveAreaIndexFocusRestoreRequest by s.liveAreaIndexFocusRestoreRequestState
  var searchTransitionDirection by s.searchTransitionDirectionState
  var searchTransitionSourceBounds by s.searchTransitionSourceBoundsState
  var searchTransitionQuery by s.searchTransitionQueryState
  var searchTransitionJob by s.searchTransitionJobState
  var searchTransitionPreparation by s.searchTransitionPreparationState
  var transitionPhase by s.transitionPhaseState
  var searchBounds by s.searchBoundsState
  var transitionSession by s.transitionSessionState
  var transitionToken by s.transitionTokenState
  var hiddenFeedCoverItemId by s.hiddenFeedCoverItemIdState
  var hiddenPopularCoverItemId by s.hiddenPopularCoverItemIdState
  var hiddenHomeDynamicCoverItemId by s.hiddenHomeDynamicCoverItemIdState
  var hiddenHomeDynamicArticleItemId by s.hiddenHomeDynamicArticleItemIdState
  var hiddenHomeLiveCoverItemId by s.hiddenHomeLiveCoverItemIdState
  var hiddenMyCoverItemId by s.hiddenMyCoverItemIdState
  var hiddenSearchCoverItemId by s.hiddenSearchCoverItemIdState
  var hiddenBangumiIndexItemId by s.hiddenBangumiIndexItemIdState
  var hiddenBangumiRecommendationItemId by s.hiddenBangumiRecommendationItemIdState
  var hiddenArticleVideoCoverItemId by s.hiddenArticleVideoCoverItemIdState
  var hiddenRecommendationCoverItemId by s.hiddenRecommendationCoverItemIdState
  var hiddenPlaybackEndRecommendationCoverItemId by s.hiddenPlaybackEndRecommendationCoverItemIdState
  var hiddenProfileCoverItemId by s.hiddenProfileCoverItemIdState
  var profileVideoTransitionActive by s.profileVideoTransitionActiveState
  var activeBangumiPage by s.activeBangumiPageState
  var bangumiPreviewTarget by s.bangumiPreviewTargetState
  var bangumiCardEnterPending by s.bangumiCardEnterPendingState
  var bangumiPreviewMuted by s.bangumiPreviewMutedState
  var bangumiPosterBounds by s.bangumiPosterBoundsState
  var deferSearchBangumiPageComposition by s.deferSearchBangumiPageCompositionState
  var deferBangumiIndexPageComposition by s.deferBangumiIndexPageCompositionState
  var deferBangumiHomePageComposition by s.deferBangumiHomePageCompositionState
  var videoExitPrelude by s.videoExitPreludeState
  var videoStack by s.videoStackState
  var articleStack by s.articleStackState
  var articleEntryToken by s.articleEntryTokenState
  var articleDetail by s.articleDetailState
  var articleLoading by s.articleLoadingState
  var articleError by s.articleErrorState
  var articleHeroBounds by s.articleHeroBoundsState
  var articleTransitionSession by s.articleTransitionSessionState
  var articleTransitionJob by s.articleTransitionJobState
  var articleLoadToken by s.articleLoadTokenState
  var articleContentReady by s.articleContentReadyState
  var articleRestoringParentEntryId by s.articleRestoringParentEntryIdState
  var articleSuspendedVideo by s.articleSuspendedVideoState
  var hiddenMyArticleItemId by s.hiddenMyArticleItemIdState
  var hiddenSearchArticleItemId by s.hiddenSearchArticleItemIdState
  var hiddenProfileArticleItemId by s.hiddenProfileArticleItemIdState
  var hiddenVideoCommentArticleItemId by s.hiddenVideoCommentArticleItemIdState
  var hiddenArticleCommentArticleItemId by s.hiddenArticleCommentArticleItemIdState
  var pendingVideoCommentTarget by s.pendingVideoCommentTargetState
  var pendingArticleCommentTarget by s.pendingArticleCommentTargetState
  var commentNavigationRequestToken by s.commentNavigationRequestTokenState
  var interactionTargetLoadingId by s.interactionTargetLoadingIdState
  var dataCommitAllowedId by s.dataCommitAllowedIdState
  var playerActivationId by s.playerActivationIdState
  var showEmbeddedCover by s.showEmbeddedCoverState
  var playerBounds by s.playerBoundsState
  var videoPageDataReadyId by s.videoPageDataReadyIdState
  var activeTransitionJob by s.activeTransitionJobState
  var activeRevealJob by s.activeRevealJobState
  var directHomeInProgress by s.directHomeInProgressState
  var previewItem by s.previewItemState
  var previewInfo by s.previewInfoState
  var previewFromHomeFeed by s.previewFromHomeFeedState
  var dismissedFeedItemIds by s.dismissedFeedItemIdsState
  var profileEntryToken by s.profileEntryTokenState
  var profileStack by s.profileStackState
  var profileLayerSuppressed by s.profileLayerSuppressedState
  var profileBangumiReturnRequest by s.profileBangumiReturnRequestState
  var commentImagePreviewActive by s.commentImagePreviewActiveState
  val controlInitialFocusRequester = s.controlInitialFocusRequester
  val feedCardBounds = s.feedCardBounds
  val popularCardBounds = s.popularCardBounds
  val dynamicCardBounds = s.dynamicCardBounds
  val homeDynamicArticleBounds = s.homeDynamicArticleBounds
  val homeLiveCardBounds = s.homeLiveCardBounds
  val myCardBounds = s.myCardBounds
  val myInteractionVideoMessageIds = s.myInteractionVideoMessageIds
  val searchCardBounds = s.searchCardBounds
  val bangumiIndexCardBounds = s.bangumiIndexCardBounds
  val myArticleBounds = s.myArticleBounds
  val myInteractionArticleMessageIds = s.myInteractionArticleMessageIds
  val searchArticleBounds = s.searchArticleBounds
  val profileArticleBounds = s.profileArticleBounds
  val articleVideoBounds = s.articleVideoBounds
  val profileCardBounds = s.profileCardBounds
  val liveRecommendationCardBounds = s.liveRecommendationCardBounds
  val livePageAlpha = s.livePageAlpha
  val bangumiIndexTransitionProgress = s.bangumiIndexTransitionProgress
  val bangumiIndexTransitionMaskAlpha = s.bangumiIndexTransitionMaskAlpha
  val bangumiIndexTransitionScrimAlpha = s.bangumiIndexTransitionScrimAlpha
  val liveAreaIndex = s.liveAreaIndex
  val searchTransitionProgress = s.searchTransitionProgress
  val searchTransitionMaskAlpha = s.searchTransitionMaskAlpha
  val searchTransitionScrimAlpha = s.searchTransitionScrimAlpha
  val articleDetailCache = s.articleDetailCache
  val articlePageAlpha = s.articlePageAlpha
  val videoState = s.videoState
  val danmakuWindowController = s.danmakuWindowController
  val directHomeAlpha = s.directHomeAlpha
  val bangumiSeasonExitFadeAlpha = s.bangumiSeasonExitFadeAlpha
  val profileState = s.profileState
  val playerViewHolder = s.playerViewHolder
  val connectivityManager =
    remember(context.applicationContext) {
      context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
        as? ConnectivityManager
    }
  val networkAvailableState =
    remember(connectivityManager) { mutableStateOf(connectivityManager?.activeNetwork != null) }
  var networkAvailable by networkAvailableState
  // ── 生命周期 + 网络状态监听：前台时开启未读监控、网络恢复时刷新登录态与未读数 ──
  DisposableEffect(lifecycleOwner, myViewModel, authUserInfo.mid, connectivityManager) {
    fun syncUnreadMonitoring() {
      myViewModel.setUnreadMonitoringActive(
        authUserInfo.mid > 0L &&
          lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
      )
    }
    val lifecycleObserver = LifecycleEventObserver { _, event ->
      syncUnreadMonitoring()
      if (event == Lifecycle.Event.ON_START) authViewModel.checkLoginStatus()
    }
    val networkCallback =
      object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
          networkAvailable = true
          myViewModel.onUnreadNetworkAvailable()
          authViewModel.checkLoginStatus()
        }

        override fun onCapabilitiesChanged(
          network: Network,
          networkCapabilities: NetworkCapabilities,
        ) {
          networkAvailable =
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        override fun onLost(network: Network) {
          networkAvailable = connectivityManager?.activeNetwork != null
        }
      }
    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    val networkCallbackRegistered =
      connectivityManager?.let { manager ->
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
              manager.registerDefaultNetworkCallback(networkCallback)
            } else {
              manager.registerNetworkCallback(
                NetworkRequest.Builder()
                  .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                  .build(),
                networkCallback,
              )
            }
            true
          }
          .getOrDefault(false)
      } ?: false
    syncUnreadMonitoring()
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
      if (networkCallbackRegistered) {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
      }
      myViewModel.setUnreadMonitoringActive(false)
    }
  }
  val feedGridState = rememberLazyGridState()
  val searchGridState = rememberLazyGridState()
  val bangumiIndexGridState = rememberLazyGridState()
  // Feed 几何信息是事件时点的参考值，而非 UI 状态。把它放在快照状态之外，
  // 可避免快速滑动期间每一次布局刻度都触发 AppRoot 重组。
  val profileStateHolder = rememberSaveableStateHolder()
  val liveRoomStateHolder = rememberSaveableStateHolder()
  // ── 启动遮罩：冷启动时用遮罩盖住首帧，等预热/番剧预载就绪后再淡出 ──
  val startupWarmupVisibleState = remember {
    mutableStateOf(FeedPerformanceConfig.startupWarmupMaskEnabled)
  }
  var startupWarmupVisible by startupWarmupVisibleState
  val startupWarmupAlpha = remember {
    Animatable(if (FeedPerformanceConfig.startupWarmupMaskEnabled) 1f else 0f)
  }
  val startupWarmupStartedAt = remember { android.os.SystemClock.elapsedRealtime() }

  // 启动遮罩是应用级最高层，直接广播给所有已经挂载的弹幕 Surface，避免等待 AndroidView
  // 的下一次局部更新才清空置顶表面。
  SideEffect { DanmakuOcclusionRegistry.setStartupMaskVisible(startupWarmupVisible) }


  val rootPagerState =
    rememberPagerState(initialPage = rootTab.ordinal, pageCount = { RootTab.entries.size })
  val latestMySection by rememberUpdatedState(myState.section)
  val liveHomeViewModel: LiveHomeViewModel = viewModel()
  val liveHomeState by liveHomeViewModel.state.collectAsState()
  val rootPlayerOwnershipState =
    remember { mutableStateOf(RootPlayerOwnership(RootPlayerSurfaceRole.IDLE)) }
  var rootPlayerOwnership by rootPlayerOwnershipState
  val videoEntryCache = videoState.videoEntryCache
  // ── 视频页局部状态 ───────────────────────────────────────────
  var videoRecommendations by videoState::videoRecommendations
  var videoDescription by videoState::videoDescription
  var videoInfo by videoState::videoInfo
  var videoEngagement by videoState::videoEngagement
  var favoriteFolders by videoState::favoriteFolders
  var favoriteFoldersLoading by videoState::favoriteFoldersLoading
  var videoActionBusy by videoState::videoActionBusy
  var onlineViewerText by videoState::onlineViewerText


  val followingStates = profileState.followingStates
  val followingBusy = profileState.followingBusy
  var followingGroups by profileState::followingGroups
  var followingGroupsLoading by profileState::followingGroupsLoading
  var followingGroupsLoaded by profileState::followingGroupsLoaded
  var commentItems by videoState::commentItems
  var commentTotalCount by videoState::commentTotalCount
  var commentPage by videoState::commentPage
  var commentHasMore by videoState::commentHasMore
  var commentSort by videoState::commentSort
  var commentsRefreshing by videoState::commentsRefreshing
  var commentOid by videoState::commentOid
  var commentsLoading by videoState::commentsLoading
  var replyRoot by videoState::replyRoot
  var replyItems by videoState::replyItems
  var replyPage by videoState::replyPage
  var replyHasMore by videoState::replyHasMore
  var repliesLoading by videoState::repliesLoading
  var danmaku by videoState::danmaku
  var danmakuMask by videoState::danmakuMask
  var emotes by videoState::emotes
  var emotePackages by videoState::emotePackages
  var mentionSuggestions by videoState::mentionSuggestions
  var mentionSuggestionsLoading by videoState::mentionSuggestionsLoading
  var historyAid by videoState::historyAid
  var historyCid by videoState::historyCid
  var historyDuration by videoState::historyDuration
  var historyStartTimestamp by videoState::historyStartTimestamp
  var profileMid by profileState::profileMid
  var profileAvatarBounds by profileState::profileAvatarBounds
  var commentProfileTransition by profileState::commentProfileTransition
  var commentProfileReturnTransition by profileState::commentProfileReturnTransition
  var avatarProfileTransition by profileState::avatarProfileTransition
  var avatarProfileReturnTransition by profileState::avatarProfileReturnTransition
  var profileTransitionJob by profileState::profileTransitionJob
  var spaceProfile by profileState::spaceProfile
  var spaceVideos by profileState::spaceVideos
  var spacePage by profileState::spacePage
  var spaceHasMore by profileState::spaceHasMore
  var spaceLoading by profileState::spaceLoading
  var spaceError by profileState::spaceError
  var spaceDynamics by profileState::spaceDynamics
  var spaceDynamicHasMore by profileState::spaceDynamicHasMore
  var spaceDynamicLoading by profileState::spaceDynamicLoading
  var spaceDynamicError by profileState::spaceDynamicError
  var selectedDynamicId by profileState::selectedDynamicId
  var spaceCollections by profileState::spaceCollections
  var spaceCollectionsLoading by profileState::spaceCollectionsLoading
  var spaceCollectionsError by profileState::spaceCollectionsError
  var selectedCollectionId by profileState::selectedCollectionId
  var spaceCollectionVideos by profileState::spaceCollectionVideos
  var spaceCollectionPage by profileState::spaceCollectionPage
  var spaceCollectionHasMore by profileState::spaceCollectionHasMore
  var spaceCollectionLoading by profileState::spaceCollectionLoading
  var spaceCollectionError by profileState::spaceCollectionError
  var spaceCollectionTotal by profileState::spaceCollectionTotal

  // ── 根页签落定后的收尾：提交待取消的关注、同步当前页签、复位首页推荐模式 ──
  LaunchedEffect(rootPagerState) {
    snapshotFlow { rootPagerState.settledPage }
      .distinctUntilChanged()
      .collect { page ->
        val settledTab = RootTab.entries[page]
        if (
          rootTab == RootTab.MY &&
            settledTab != RootTab.MY &&
            latestMySection == dev.openbili.webdemo.my.MySection.FOLLOWING
        ) {
          myViewModel.commitPendingUnfollows()
        }
        rootTab = settledTab
        if (settledTab != RootTab.HOME) {
          homeRecommendationMode = HomeRecommendationMode.NORMAL
        }
      }
  }

  val rootPageSwitchInProgress = rootPageSwitchRequested || rootPagerState.isScrollInProgress
  // ── 页签滑动期间暂停/恢复各页的补充加载，避免滑动动画与网络渲染互相抢帧 ──
  LaunchedEffect(rootPagerState) {
    snapshotFlow { rootPagerState.isScrollInProgress }
      .distinctUntilChanged()
      .collect { switching ->
        if (switching) {
          feedViewModel.cancelSupplementaryLoadingForPageSwitch()
          myViewModel.cancelSupplementaryLoadingForPageSwitch()
        } else {
          feedViewModel.resumeSupplementaryLoadingAfterPageSwitch()
          if (RootTab.entries[rootPagerState.settledPage] == RootTab.MY) {
            myViewModel.resumeSupplementaryLoadingAfterPageSwitch()
          }
        }
      }
  }


  // ── 播放器会话状态：进度/拖拽预览/播放状态/弹幕开关等，跨页面共享 ──
  val playerSession = remember {
    AppRootPlayerSessionState(initialShowDanmaku = settings.defaultShowDanmaku)
  }
  var currentPositionMs by playerSession::currentPositionMs
  var scrubPreviewMs by playerSession::scrubPreviewMs
  var pendingSeekTargetMs by playerSession::pendingSeekTargetMs
  var seekWasPlaying by playerSession::seekWasPlaying
  var isPlaying by playerSession::isPlaying
  var isBuffering by playerSession::isBuffering
  var showDanmaku by playerSession::showDanmaku
  var playerReady by playerSession::playerReady
  var playerControlsVisible by playerSession::playerControlsVisible
  var playbackEnded by playerSession::playbackEnded
  var playbackSpeed by playerSession::playbackSpeed
  LaunchedEffect(appState.selectedVideo?.id, settings.defaultShowDanmaku) {
    if (appState.selectedVideo != null) showDanmaku = settings.defaultShowDanmaku
  }
  val danmakuPositionEpoch = playerSession.danmakuPositionEpoch
  val latestScrubPreview by rememberUpdatedState(scrubPreviewMs)
  val latestPendingSeekTarget by rememberUpdatedState(pendingSeekTargetMs)
  val playerPositionProvider =
    remember(playerViewModel) {
      {
        latestScrubPreview
          ?: latestPendingSeekTarget
          ?: playerViewModel.exoPlayer?.currentPosition
          ?: 0L
      }
    }
  val playerPlaybackRateProvider =
    remember(playerViewModel) {
      { playerViewModel.exoPlayer?.playbackParameters?.speed ?: 1f }
    }
  val playerUiPositionProvider =
    remember(playerSession) {
      {
        playerSession.scrubPreviewMs
          ?: playerSession.pendingSeekTargetMs
          ?: playerSession.currentPositionMs
      }
    }



  // 弹幕是否因播放暂停或转场而暂停：非播放中、退出前奏/转场会话存在或相位不在 Feed/Video 时暂停。
  val danmakuPausedForPlayer =
    !isPlaying ||
      videoExitPrelude != null ||
      transitionSession != null ||
      (transitionPhase !is TransitionPhase.Feed && transitionPhase !is TransitionPhase.Video)
  // 把会频繁变化的弹幕参数包成 rememberUpdatedState，供 AndroidView 的 update 闭包始终读到最新值。
  val latestDanmaku by rememberUpdatedState(danmaku)
  val latestDanmakuMask by rememberUpdatedState(danmakuMask)
  val latestShowDanmaku by rememberUpdatedState(showDanmaku)
  val latestDanmakuPaused by rememberUpdatedState(danmakuPausedForPlayer)
  val latestDanmakuOpacity by rememberUpdatedState(settings.danmakuOpacity)
  val latestDanmakuDisplayArea by rememberUpdatedState(settings.danmakuDisplayArea)
  val latestDanmakuDensity by rememberUpdatedState(settings.danmakuDensity)
  val latestDanmakuBlockLevel by rememberUpdatedState(settings.danmakuBlockLevel)
  val latestDanmakuFontScale by rememberUpdatedState(settings.danmakuFontScale)
  val latestDanmakuSpeed by rememberUpdatedState(settings.danmakuSpeed)
  val latestDanmakuSmartBlocking by rememberUpdatedState(settings.danmakuSmartBlocking)
  val latestDanmakuPositionEpoch by rememberUpdatedState(danmakuPositionEpoch)
  val latestPlayerPositionProvider by rememberUpdatedState(playerPositionProvider)
  val playerSurfacePlayData =
    when (val state = playerState) {
      is dev.openbili.webdemo.PlayerState.Ready -> state.playData
      is dev.openbili.webdemo.PlayerState.Error -> state.playData
      else -> null
    }
  val isHdrPlayback =
    playerSurfacePlayData?.let { data ->
      data.streams.getOrNull(data.currentStreamIndex)?.id in setOf(125, 126)
    } == true
  val latestIsHdrPlayback by rememberUpdatedState(isHdrPlayback)
  val playbackCtx =
    AppRootPlaybackContext(
      context = context,
      scope = scope,
      playerViewHolder = s.playerViewHolder,
      rootPagerState = rootPagerState,
      playerViewModel = playerViewModel,
      videoState = s.videoState,
      playerSession = playerSession,
      settingsState = settingsState,
      authUserInfoState = authUserInfoState,
      appStateState = appStateState,
      transitionPhaseState = s.transitionPhaseState,
      transitionSessionState = s.transitionSessionState,
      showEmbeddedCoverState = s.showEmbeddedCoverState,
      playerBoundsState = s.playerBoundsState,
      activeBangumiPageState = s.activeBangumiPageState,
      profileVideoTransitionActiveState = s.profileVideoTransitionActiveState,
      hiddenProfileCoverItemIdState = s.hiddenProfileCoverItemIdState,
      hiddenSearchCoverItemIdState = s.hiddenSearchCoverItemIdState,
      hiddenArticleVideoCoverItemIdState = s.hiddenArticleVideoCoverItemIdState,
      hiddenMyCoverItemIdState = s.hiddenMyCoverItemIdState,
      commentImagePreviewActiveState = s.commentImagePreviewActiveState,
      rootTabState = s.rootTabState,
      rootPageSwitchRequestTokenState = s.rootPageSwitchRequestTokenState,
      rootPageSwitchRequestedState = s.rootPageSwitchRequestedState,
      homeRecommendationModeState = s.homeRecommendationModeState,
      hiddenRecommendationCoverItemIdState = s.hiddenRecommendationCoverItemIdState,
      hiddenHomeDynamicCoverItemIdState = s.hiddenHomeDynamicCoverItemIdState,
      hiddenBangumiIndexItemIdState = s.hiddenBangumiIndexItemIdState,
      hiddenBangumiRecommendationItemIdState = s.hiddenBangumiRecommendationItemIdState,
      hiddenFeedCoverItemIdState = s.hiddenFeedCoverItemIdState,
      hiddenPopularCoverItemIdState = s.hiddenPopularCoverItemIdState,
      hiddenPlaybackEndRecommendationCoverItemIdState = s.hiddenPlaybackEndRecommendationCoverItemIdState,
      activeTransitionJobState = s.activeTransitionJobState,
      activeRevealJobState = s.activeRevealJobState,
    )

  // ── 播放器上下文转发函数：把 AppRootPlaybackContext 的能力包装成局部函数供内部调用 ──
  fun launchTransition(block: suspend CoroutineScope.() -> Unit) = playbackCtx.launchTransition(block)

  fun animateToRootTab(tab: RootTab) = playbackCtx.animateToRootTab(tab)

  fun restoredBangumiCard(sourceCard: SpaceContentCard): SpaceContentCard =
    playbackCtx.restoredBangumiCard(sourceCard)

  fun commitPlaybackProgress() = playbackCtx.commitPlaybackProgress()

  fun obtainPlayerView(ctx: android.content.Context, role: SharedPlayerViewRole): PlayerView =
    playbackCtx.obtainPlayerView(ctx, role)

  fun obtainPlayerViewForHost(ctx: android.content.Context, role: SharedPlayerViewRole): PlayerView =
    playbackCtx.obtainPlayerViewForHost(ctx, role)

  fun unbindPlayerView() = playbackCtx.unbindPlayerView()

  fun prewarmPlayerInfrastructure() = playbackCtx.prewarmPlayerInfrastructure()

  fun cachedCardTransitionBitmap(session: CardTransitionSession) = playbackCtx.cachedCardTransitionBitmap(session)

  suspend fun prepareCardTransition(session: CardTransitionSession, targetBounds: () -> Rect = { playerBounds }): Rect =
    playbackCtx.prepareCardTransition(session, targetBounds)

  suspend fun prepareExitTransition(session: CardTransitionSession, targetBounds: () -> Rect?): Rect =
    playbackCtx.prepareExitTransition(session, targetBounds)

  fun previewSeek(targetMs: Long) = playbackCtx.previewSeek(targetMs)

  fun setTemporarySpeedBoost(active: Boolean) = playbackCtx.setTemporarySpeedBoost(active)

  fun setPlaybackSpeed(speed: Float) = playbackCtx.setPlaybackSpeed(speed)

  fun cancelSeekPreview() = playbackCtx.cancelSeekPreview()

  fun commitSeek(targetMs: Long) = playbackCtx.commitSeek(targetMs)

  fun revealTransitionSession(session: CardTransitionSession, timedOut: Boolean = false) =
    playbackCtx.revealTransitionSession(session, timedOut)

  // ── 系统返回键：非首页且无视频/资料页时回到首页；遥控器模式下弹退出确认 ──
  BackHandler(enabled = rootTab != RootTab.HOME && !appState.isVideoScreen && profileMid == null) {
    animateToRootTab(RootTab.HOME)
  }
  BackHandler(
    enabled =
      controlMode &&
        rootTab == RootTab.HOME &&
        !appState.isVideoScreen &&
        profileMid == null &&
        activeLiveRoom == null &&
        articleStack.isEmpty() &&
        !showSearch &&
        !showSearchResults &&
        !showBangumiIndex &&
        canShowControlHomeExit(homeControlLevel) &&
        homeRecommendationMode == HomeRecommendationMode.NORMAL
  ) {
    showControlExitDialog = true
  }





  LaunchedEffect(playerViewModel, context, startupWarmupVisible) {
    if (startupWarmupVisible) return@LaunchedEffect
    // 把可复用的播放器创建移出 feed 的首次组合与封面解码高峰期。
    // 唯一的 PlayerView 宿主与 ExoPlayer 分两个主队列空闲阶段创建；
    // 这里不会发起任何播放地址、封面或视频请求。
    val localWarmup = launch { runCatching { playerViewModel.prewarmLocalInfrastructure() } }
    try {
      awaitMainMessageQueueIdle()
      if (playerViewHolder[0] == null) {
        obtainPlayerView(context, SharedPlayerViewRole.PREVIEW)
      }
      awaitMainMessageQueueIdle()
      prewarmPlayerInfrastructure()
    } finally {
      localWarmup.join()
    }
  }

  LaunchedEffect(startupWarmupVisible) {
    if (!startupWarmupVisible) return@LaunchedEffect
    val fadeDuration = if (settings.reduceMotion) 90 else 170
    while (true) {
      val elapsed = android.os.SystemClock.elapsedRealtime() - startupWarmupStartedAt
      val minimumReached = elapsed >= FeedPerformanceConfig.startupWarmupDurationMs - fadeDuration
      val timedOut = elapsed >= FeedPerformanceConfig.startupWarmupTimeoutMs
      if (minimumReached && (bangumiStartupPreloadReady || timedOut)) break
      delay(16)
    }
    // 超时机制保证在弱网下冷启动仍可恢复。遮罩自身播放淡出动画期间先暂停未完成的预取，
    // 避免图片解码抢占动画帧，待空闲后再恢复。
    startupWarmupFadeInProgress = true
    startupWarmupAlpha.animateTo(
      0f,
      animationSpec = tween(fadeDuration),
    )
    startupWarmupVisible = false
    startupWarmupFadeInProgress = false
  }

  // ── 当播放器空闲且无视频页/直播占据时，解绑共享 PlayerView 并归还到空闲池 ──
  LaunchedEffect(
    playerReady,
    appState.isVideoScreen,
    rootPlayerOwnership.role,
    activeLiveRoom,
  ) {
    if (
      !playerReady &&
        !appState.isVideoScreen &&
        activeLiveRoom == null &&
        rootPlayerOwnership.role == RootPlayerSurfaceRole.IDLE
    ) {
      unbindPlayerView()
    }
  }

  // ── 根播放器内容：AndroidView 承载共享 PlayerView 与弹幕浮层，在组合期读取浮层输入 ──
  val rootPlayerContent:
    @Composable
    (
      androidx.compose.ui.Modifier,
      Float,
      Boolean,
      Boolean,
      SharedPlayerViewRole,
      Boolean?,
      Boolean,
    ) -> Unit =
    { modifier, fullscreenProgress, fullscreen, danmakuAllowed, role, surfaceVisible, bindPlayer ->
      // 在组合阶段读取浮层输入。若只在 AndroidView 的 update lambda 内读取，
      // rememberUpdatedState 的变化将无法触发该 update 块失效。
      val danmakuItems = if (danmakuAllowed) latestDanmaku else emptyList()
      val maskTimeline = if (danmakuAllowed) latestDanmakuMask else null
      val danmakuEnabled = danmakuAllowed && latestShowDanmaku
      val smartBlocking = latestDanmakuSmartBlocking
      val danmakuPaused = latestDanmakuPaused
      val danmakuOpacity = latestDanmakuOpacity
      val danmakuDisplayArea = latestDanmakuDisplayArea
      val danmakuDensity = latestDanmakuDensity
      val danmakuBlockLevel = latestDanmakuBlockLevel
      val danmakuFontScale = latestDanmakuFontScale
      val danmakuSpeed = latestDanmakuSpeed
      val danmakuPositionEpoch = latestDanmakuPositionEpoch
      val highDynamicRange = latestIsHdrPlayback
      val profilePageCoversPlayer = profileStack.isNotEmpty() && !profileLayerSuppressed
      val cardTransitionRequiresDanmakuSuppression =
        shouldSuppressDanmakuForCardTransition(
          kind = transitionSession?.kind,
          phase = transitionSession?.phase,
        )
      // 前奏在静态封面把接力棒交给飞行卡片的瞬间就被刻意移除。整个第二阶段需要持续抑制
      // 独立的 SurfaceView，否则 AndroidView 的更新会让弹幕表面重新显示在飞行动画之上。
      val danmakuTransitionSuppressed =
        !danmakuAllowed ||
          playbackEnded ||
          videoFullscreenTransitionActive ||
          videoExitPrelude != null ||
          profilePageCoversPlayer ||
          cardTransitionRequiresDanmakuSuppression ||
          commentImagePreviewActive ||
          showEmbeddedCover ||
          surfaceVisible == false
      val danmakuState = remember { DanmakuUpdateState() }
      val hostedOverlay = remember { arrayOfNulls<DanmakuOverlayView>(1) }
      val updatedOverlay = remember { arrayOfNulls<DanmakuOverlayView>(1) }
      AndroidView(
        factory = { ctx ->
          FrameLayout(ctx).apply {
            val playerView = obtainPlayerViewForHost(ctx, role)
            addView(
              playerView,
              FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
              ),
            )
            if (danmakuAllowed) {
              val overlay = DanmakuOverlayView(ctx)
              addView(
                overlay,
                FrameLayout.LayoutParams(
                  FrameLayout.LayoutParams.MATCH_PARENT,
                  FrameLayout.LayoutParams.MATCH_PARENT,
                ),
              )
              overlay.setEmbeddedHost(this)
              hostedOverlay[0] = overlay
              playerView.bindDanmakuVideoViewport(overlay)
            }
          }
        },
        update = { host ->
          val playerView = host.getChildAt(0) as PlayerView
          val overlay = hostedOverlay[0]
          val targetPlayer = if (bindPlayer) playerViewModel.exoPlayer else null
          if (playerView.player !== targetPlayer) {
            playerView.player = targetPlayer
          }
          surfaceVisible?.let { visible ->
            playerView.updateVideoSurfaceAlpha(if (visible) 1f else 0f)
          }
          playerView.updatePlayerCornerRadius(
            PLAYER_CORNER_RADIUS_DP * (1f - fullscreenProgress.coerceIn(0f, 1f))
          )
          playerView.updateSubtitlePresentation(
            visible =
              role == SharedPlayerViewRole.DETAIL &&
                subtitleState.selectedTrackId != null &&
                subtitleState.mediaId == appState.selectedVideo?.id &&
                subtitleState.mediaId == targetPlayer?.currentMediaItem?.mediaId &&
                subtitleState.bvid.equals(videoInfo?.bvid, ignoreCase = true) &&
                subtitleState.aid == videoInfo?.aid &&
                (historyCid <= 0L || subtitleState.cid == historyCid),
            style = settings.subtitleStyle,
          )
          if (overlay != null) {
            overlay.setStartupMaskVisible(startupWarmupVisible)
            val overlayChanged = updatedOverlay[0] !== overlay
            val parentChanged =
              if (danmakuTransitionSuppressed) false else overlay.moveToFullscreenHost(fullscreen)
            if (overlayChanged || parentChanged) playerView.bindDanmakuVideoViewport(overlay)
            overlay.setTransitionSuppressed(danmakuTransitionSuppressed)
            val changed =
              danmakuState.changed(
                danmakuItems,
                maskTimeline,
                danmakuEnabled,
                smartBlocking,
                danmakuPaused,
                fullscreen,
                highDynamicRange,
                danmakuOpacity,
                danmakuDisplayArea,
                danmakuDensity,
                danmakuBlockLevel,
                danmakuFontScale,
                danmakuSpeed,
                danmakuPositionEpoch,
              )
            if (changed || overlayChanged) {
              updatedOverlay[0] = overlay
              overlay.update(
                danmakuItems,
                maskTimeline,
                danmakuEnabled,
                smartBlocking,
                danmakuPaused,
                fullscreen,
                highDynamicRange,
                danmakuOpacity,
                danmakuDisplayArea,
                danmakuDensity,
                danmakuBlockLevel,
                danmakuFontScale,
                danmakuSpeed,
                danmakuPositionEpoch,
                latestPlayerPositionProvider,
                playerPlaybackRateProvider,
              )
            }
          }
        },
        onRelease = {
          hostedOverlay[0]?.releaseHost()
          hostedOverlay[0] = null
          updatedOverlay[0] = null
        },
        modifier = modifier,
      )
    }



  // ── 将登录用户信息同步到 feed / 我的 / 稍后再看 / 私信 ─────────────────────────────────────
  LaunchedEffect(authUserInfo) {
    val changedAccount = feedViewModel.userInfo.value.mid != authUserInfo.mid
    if (changedAccount) {
      followingStates.clear()
      followingBusy.clear()
      followingGroups = emptyList()
      followingGroupsLoaded = false
      followingGroupsLoading = false
    }
    feedViewModel.updateUserInfo(authUserInfo)
    myViewModel.setUser(authUserInfo.mid)
    watchLaterViewModel.setAccount(authUserInfo.mid)
    if (authUserInfo.isLogin) watchLaterViewModel.ensureLoaded()
    profileMessageViewModel.setUser(authUserInfo.mid, loadInitialSection = false)
    if (changedAccount) feedViewModel.refresh()
  }

  // ── “稍后再看”操作反馈：弹 Toast 后消费掉反馈令牌，避免重复弹出 ──
  LaunchedEffect(watchLaterState.feedback?.token) {
    val feedback = watchLaterState.feedback ?: return@LaunchedEffect
    Toast.makeText(context, feedback.message, Toast.LENGTH_SHORT).show()
    watchLaterViewModel.consumeFeedback()
  }

  LaunchedEffect(Unit) {
    feedViewModel.setInitialTargetCount(30)
  }

  // ── 把画质/音质解锁与字幕、音频偏好同步到播放器 ViewModel ──
  LaunchedEffect(
    settings.unlockDolbyVision,
    settings.unlockDolbyAtmos,
    settings.unlockHiRes,
    settings.defaultShowSubtitles,
    settings.advancedAudioEnabled,
    settings.advancedAudioPriority,
  ) {
    playerViewModel.setCompatibilityUnlocks(
      dolbyVision = settings.unlockDolbyVision,
      dolbyAtmos = settings.unlockDolbyAtmos,
      hiRes = settings.unlockHiRes,
    )
    playerViewModel.setDefaultSubtitlesEnabled(settings.defaultShowSubtitles)
    playerViewModel.setAdvancedAudioPreferences(
      enabled = settings.advancedAudioEnabled,
      priority = settings.advancedAudioPriority,
    )
  }

  // 在启动遮罩仍覆盖着保留的根 pager 时提前发起番剧请求。这样相邻的 BANGUMI 页面
  // 就能在遮罩询问该页面是否就绪之前，先解析详情并解码首批轮播图。
  LaunchedEffect(bangumiRecommendationViewModel) {
    bangumiRecommendationViewModel.ensureLoaded()
  }

  val bangumiRootPageActive =
    shouldActivateBangumiRootPage(
      selectedTab = rootTab,
      settledPage = rootPagerState.settledPage,
      pageSwitchInProgress = rootPageSwitchInProgress,
      videoScreenVisible = appState.isVideoScreen,
    )
  LaunchedEffect(bangumiRootPageActive) {
    if (bangumiRootPageActive) bangumiRecommendationViewModel.ensureLoaded()
  }
  // ── 根播放器归属状态机：按选中视频与播放就绪程度在 IDLE/DETAIL_PENDING/DETAIL 间迁移 ──
  LaunchedEffect(appState.selectedVideo?.id, transitionPhase) {
    val selectedId = appState.selectedVideo?.id ?: return@LaunchedEffect
    if (
      transitionPhase !is TransitionPhase.Feed &&
        rootPlayerOwnership.role != RootPlayerSurfaceRole.EXIT_COVERED &&
        rootPlayerOwnership.mediaId != selectedId
    ) {
      rootPlayerOwnership = RootPlayerOwnership(RootPlayerSurfaceRole.DETAIL_PENDING, selectedId)
    }
  }
  LaunchedEffect(appState.selectedVideo?.id, appState.isVideoScreen, renderedVideoId, playerState) {
    val selectedId = appState.selectedVideo?.id ?: return@LaunchedEffect
    if (
      appState.isVideoScreen &&
        renderedVideoId == selectedId &&
        playerState is dev.openbili.webdemo.PlayerState.Ready &&
        rootPlayerOwnership.role == RootPlayerSurfaceRole.DETAIL_PENDING &&
        rootPlayerOwnership.mediaId == selectedId
    ) {
      rootPlayerOwnership = RootPlayerOwnership(RootPlayerSurfaceRole.DETAIL, selectedId)
    }
  }
  LaunchedEffect(transitionPhase, appState.isVideoScreen, rootPlayerOwnership.role) {
    if (
      transitionPhase is TransitionPhase.Feed &&
        !appState.isVideoScreen &&
        rootPlayerOwnership.role in
          setOf(
            RootPlayerSurfaceRole.DETAIL_PENDING,
            RootPlayerSurfaceRole.DETAIL,
            RootPlayerSurfaceRole.EXIT_COVERED,
          )
    ) {
      rootPlayerOwnership = RootPlayerOwnership(RootPlayerSurfaceRole.IDLE)
    }
  }
  // 当前番剧页及其正在播放的一集，供下方播放器效果做心跳/进度上报定位。
  val heartbeatBangumiPage = activeBangumiPage
  val heartbeatBangumiEpisode =
    heartbeatBangumiPage
      ?.season
      ?.let { it.episodes + it.sections.flatMap { section -> section.episodes } }
      ?.firstOrNull {
        it.id == heartbeatBangumiPage.currentEpisodeId &&
          it.aid == historyAid &&
          it.cid == historyCid
      }
  // ── 播放器效果集合：负责媒体加载、前后台切换、进度提交与转场揭示等副作用 ──
  AppRootPlayerEffects(
    context = context,
    lifecycleOwner = lifecycleOwner,
    isVideoScreen = appState.isVideoScreen,
    selectedVideoId = appState.selectedVideo?.id,
    playerActivationId = playerActivationId,
    playerState = playerState,
    renderedVideoId = renderedVideoId,
    // 番剧 hero 拥有独立的预览播放器。详情播放器回到 feed 时必须始终进入 warm idle，
    // 即使旧版转场记录仍然提到 PREVIEW。
    keepMediaWhileInFeed = false,
    playerViewModel = playerViewModel,
    sessionState = playerSession,
    transitionSession = transitionSession,
    historyAid = historyAid,
    historyCid = historyCid,
    historyDuration = historyDuration,
    historyStartTimestamp = historyStartTimestamp,
    bangumiSubType =
      activeBangumiPage?.let { page -> pgcPlaybackSubType(page.sourceCard.seasonType) } ?: 0,
    bangumiEpisodeId = heartbeatBangumiEpisode?.id ?: 0L,
    bangumiSeasonId =
      if (heartbeatBangumiEpisode != null) heartbeatBangumiPage.season.seasonId else 0L,
    loggedIn = authUserInfo.isLogin,
    pauseWhenLeavingApp = settings.pauseWhenLeavingApp,
    onCommitPlaybackProgress = {
      if (appState.isVideoScreen) commitPlaybackProgress()
    },
    onRevealTransitionSession = ::revealTransitionSession,
  )

  // ── 定位到指定评论：进入视频页后拉取目标评论线程并插到评论列表顶部 ──
  LaunchedEffect(
    pendingVideoCommentTarget,
    transitionPhase,
    videoInfo?.aid,
    commentsLoading,
    videoPageDataReadyId,
  ) {
    val target = pendingVideoCommentTarget ?: return@LaunchedEffect
    if (transitionPhase !is TransitionPhase.Video) return@LaunchedEffect
    val activeAid = videoInfo?.aid ?: return@LaunchedEffect
    if (commentsLoading || videoPageDataReadyId != appState.selectedVideo?.id) return@LaunchedEffect
    if (target.oid > 0L && activeAid != target.oid) return@LaunchedEffect
    if (commentItems.any { it.rpid == target.rootRpid }) return@LaunchedEffect
    val thread =
      runCatching {
          withContext(Dispatchers.IO) {
            BiliCommentApi.getCommentThread(activeAid, target.rootRpid, target.type)
          }
        }
        .getOrNull()
    if (pendingVideoCommentTarget != target || videoInfo?.aid != activeAid) return@LaunchedEffect
    if (thread == null) {
      Toast.makeText(context, "这条评论可能已被删除", Toast.LENGTH_SHORT).show()
      pendingVideoCommentTarget = null
      return@LaunchedEffect
    }
    commentItems = (listOf(thread.root) + commentItems).distinctBy { it.rpid }
    commentTotalCount = maxOf(commentTotalCount, commentItems.size.toLong())
  }

  // ── 资料页上下文：加载/打开/关闭资料页，并在 IP 授权通过后补加载 ──
  val profileCtx =
    AppRootProfileContext(
      context = context,
      scope = scope,
      profileState = s.profileState,
      playerViewModel = playerViewModel,
      videoEntryCache = videoEntryCache,
      appStateState = appStateState,
      settingsState = settingsState,
      authUserInfoState = authUserInfoState,
      profileIpAuthorizedState = profileIpAuthorizedState,
      articleStackState = s.articleStackState,
      profileLayerSuppressedState = s.profileLayerSuppressedState,
      transitionPhaseState = s.transitionPhaseState,
      playerActivationIdState = s.playerActivationIdState,
      dataCommitAllowedIdState = s.dataCommitAllowedIdState,
      profileEntryTokenState = s.profileEntryTokenState,
      profileStackState = s.profileStackState,
      myCardBounds = s.myCardBounds,
      currentPreferredResolutionModeRef = { currentPreferredResolutionMode() },
    )

  fun loadPreparedProfile(mid: Long) = profileCtx.loadPreparedProfile(mid)

  // IP 授权通过后，补加载此前因授权未通过而搁置的资料页。
  LaunchedEffect(profileIpAuthorized) {
    if (profileIpAuthorized) profileMid?.let(::loadPreparedProfile)
  }

  fun loadSpacePage(mid: Long, page: Int) = profileCtx.loadSpacePage(mid, page)

  fun loadSpaceDynamics(refresh: Boolean) = profileCtx.loadSpaceDynamics(refresh)

  fun loadProfile(mid: Long) = profileCtx.loadProfile(mid)

  fun restoreProfile(entry: ProfilePageEntry) = profileCtx.restoreProfile(entry)

  fun activeProfileEntry(entryId: Long? = null): ProfileStackEntry? = profileCtx.activeProfileEntry(entryId)

  fun openProfile(mid: Long) = profileCtx.openProfile(mid)

  fun openAvatarProfile(
    mid: Long,
    bounds: Rect,
    face: String? = authUserInfo.face,
    name: String? = authUserInfo.name,
  ) = profileCtx.openAvatarProfile(mid, bounds, face, name)

  fun openAvatarProfileFrom(
    sourceEntryId: Long?,
    mid: Long,
    bounds: Rect,
    face: String?,
    name: String?,
  ) = profileCtx.openAvatarProfileFrom(sourceEntryId, mid, bounds, face, name)

  fun openCommentProfile(mid: Long, comment: CommentItem, anchor: CommentProfileAnchor) =
    profileCtx.openCommentProfile(mid, comment, anchor)

  fun openCommentProfileFrom(
    sourceEntryId: Long?,
    mid: Long,
    comment: CommentItem,
    anchor: CommentProfileAnchor,
    returnsToArticleSource: Boolean = false,
  ) = profileCtx.openCommentProfileFrom(sourceEntryId, mid, comment, anchor, returnsToArticleSource)

  fun openArticleCommentProfile(mid: Long, comment: CommentItem, anchor: CommentProfileAnchor) =
    profileCtx.openArticleCommentProfile(mid, comment, anchor)

  fun retainedPlaybackPage(itemId: String): VideoPage? = profileCtx.retainedPlaybackPage(itemId)

  fun closeProfile() = profileCtx.closeProfile()
  fun showVideoPreview(item: FeedItem, fromHomeFeed: Boolean = false) {
    previewItem = item
    previewInfo = null
    previewFromHomeFeed = fromHomeFeed
    scope.launch {
      val bvid = item.videoUrl.substringAfterLast("/").substringBefore("?")
      val info =
        withContext(Dispatchers.IO) { runCatching { BiliVideoApi.getVideoInfo(bvid) }.getOrNull() }
      if (previewItem?.id == item.id) previewInfo = info
    }
  }

  fun loadFollowingGroups() =
    profileState.loadFollowingGroups(
      loggedIn = authUserInfo.isLogin,
      onLogin = authViewModel::startLogin,
      context = context,
      scope = scope,
    )

  fun selectFollowingGroup(mid: Long, groupId: Long) =
    profileState.selectFollowingGroup(
      mid = mid,
      groupId = groupId,
      loggedIn = authUserInfo.isLogin,
      onLogin = authViewModel::startLogin,
      context = context,
      scope = scope,
    )

  fun unfollow(mid: Long) =
    profileState.unfollow(
      mid = mid,
      loggedIn = authUserInfo.isLogin,
      onLogin = authViewModel::startLogin,
      context = context,
      scope = scope,
    )

  // ── 批量预取相关 UP 主的关注状态，供关注按钮在无网络往返时立即显示 ──
  LaunchedEffect(
    authUserInfo.isLogin,
    authUserInfo.mid,
    profileMid,
    videoInfo?.uploaderMid,
    appState.selectedVideo?.uploaderMid,
  ) {
    if (!authUserInfo.isLogin) return@LaunchedEffect
    val targets =
      listOfNotNull(
          profileMid,
          videoInfo?.uploaderMid,
          appState.selectedVideo?.uploaderMid,
        )
        .filter { it > 0 && it != authUserInfo.mid && !followingStates.containsKey(it) }
        .distinct()
    if (targets.isEmpty()) return@LaunchedEffect
    val loaded =
      withContext(Dispatchers.IO) {
        targets.mapNotNull { mid ->
          runCatching { mid to BiliFollowApi.isFollowing(mid) }.getOrNull()
        }
      }
    loaded.forEach { (mid, followed) -> followingStates[mid] = followed }
  }

  // 等待播放器边界在连续两帧内稳定（有可用尺寸且与上一帧近似相等），
  // 用于转场前获取可靠的来源/目标锚点，最多采样 8 帧。
  suspend fun awaitStablePlayerBounds(): Rect {
    var previous = Rect.Zero
    var stableFrames = 0
    repeat(8) {
      withFrameNanos {}
      val current = playerBounds
      if (current.hasUsableSize() && current.approximatelyEquals(previous)) {
        stableFrames += 1
        if (stableFrames >= 2) return current
      } else {
        stableFrames = 0
      }
      previous = current
    }
    return playerBounds
  }

  // ── 视频页上下文：承载视频数据快照/缓存/恢复、评论排序、选集等视频页专属逻辑 ──
  val videoPageCtx =
    AppRootVideoPageContext(
      context = context,
      scope = scope,
      lifecycleOwner = lifecycleOwner,
      videoEntryCache = videoEntryCache,
      danmakuWindowController = s.danmakuWindowController,
      playerPositionProvider = playerPositionProvider,
      playerViewModel = playerViewModel,
      mainViewModel = mainViewModel,
      playerUiPositionProvider = playerUiPositionProvider,
      videoState = s.videoState,
      playerSession = playerSession,
      appStateState = appStateState,
      settingsState = settingsState,
      authUserInfoState = authUserInfoState,
      loginStateState = loginStateState,
      playerActivationIdState = s.playerActivationIdState,
      dataCommitAllowedIdState = s.dataCommitAllowedIdState,
      transitionSessionState = s.transitionSessionState,
      videoPageDataReadyIdState = s.videoPageDataReadyIdState,
      showEmbeddedCoverState = s.showEmbeddedCoverState,
      transitionPhaseState = s.transitionPhaseState,
      videoStackState = s.videoStackState,
      playerBoundsState = s.playerBoundsState,
      awaitStablePlayerBoundsRef = ::awaitStablePlayerBounds,
      commitPlaybackProgressRef = ::commitPlaybackProgress,
      currentPreferredResolutionModeRef = ::currentPreferredResolutionMode,
      retainedPlaybackPageRef = ::retainedPlaybackPage,
    )

  fun loadMentionSuggestions(query: String) = videoPageCtx.loadMentionSuggestions(query)

  fun snapshotEntry(item: FeedItem): VideoPageEntry = videoPageCtx.snapshotEntry(item)

  fun cacheEntry(entry: VideoPageEntry) = videoPageCtx.cacheEntry(entry)

  fun selectCommentSort(sort: CommentSort) = videoPageCtx.selectCommentSort(sort)

  fun selectVideoPage(page: VideoPage) = videoPageCtx.selectVideoPage(page)

  fun restoreEntry(entry: VideoPageEntry) = videoPageCtx.restoreEntry(entry)

  fun restoreEntryForFreshPlayback(entry: VideoPageEntry) = videoPageCtx.restoreEntryForFreshPlayback(entry)

  fun clearVisibleVideoData() = videoPageCtx.clearVisibleVideoData()

  fun ensureVideoPageData(item: FeedItem) = videoPageCtx.ensureVideoPageData(item)

  fun selectCollectionEpisode(episode: FeedItem) = videoPageCtx.selectCollectionEpisode(episode)
  // ── 进入视频页时加载推荐、评论与弹幕（交由尾部 appRootTailContent 完成） ────
  appRootTailContent(
    s = s,
    mainViewModel = mainViewModel,
    feedViewModel = feedViewModel,
    authViewModel = authViewModel,
    playerViewModel = playerViewModel,
    myViewModel = myViewModel,
    profileMessageViewModel = profileMessageViewModel,
    searchViewModel = searchViewModel,
    settingsViewModel = settingsViewModel,
    bangumiRecommendationViewModel = bangumiRecommendationViewModel,
    scope = scope,
    playbackCtx = playbackCtx,
    profileCtx = profileCtx,
    videoPageCtx = videoPageCtx,
    networkAvailableState = networkAvailableState,
    startupWarmupVisibleState = startupWarmupVisibleState,
    startupWarmupAlpha = startupWarmupAlpha,
    rootPlayerOwnershipState = rootPlayerOwnershipState,
    rootPagerState = rootPagerState,
    feedGridState = feedGridState,
    searchGridState = searchGridState,
    bangumiIndexGridState = bangumiIndexGridState,
    profileStateHolder = profileStateHolder,
    liveRoomStateHolder = liveRoomStateHolder,
    playerSession = playerSession,
    onSearch = onSearch,
    onFeedRefresh = onFeedRefresh,
    onFeedPullRefresh = onFeedPullRefresh,
    onExitRequested = onExitRequested,
    rootPlayerContent = rootPlayerContent,
  )
}

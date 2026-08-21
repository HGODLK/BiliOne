package dev.openbili.webdemo.ui

/**
 * AppRoot 的尾部组合体：视频数据提交效果链、番剧进入/退出上下文、feed/video 上下文
 * 构造与最终 UI 调用。从 AppRoot.kt 整体迁出以控制方法体积（见 docs/code-standards.md），
 * 内部再用 composable lambda 包裹，规避 JVM 64KB 方法上限。
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
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateHolder
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
import dev.openbili.webdemo.isPlaybackSnapshotValid
import dev.openbili.webdemo.resolvePlaybackPage
import dev.openbili.webdemo.search.SearchResultsScreen
import dev.openbili.webdemo.search.SearchScreen
import dev.openbili.webdemo.search.SearchViewModel
import dev.openbili.webdemo.settings.AppSettingsViewModel
import dev.openbili.webdemo.settings.preferredResolutionModeFor
import dev.openbili.webdemo.video.BangumiPageUi
import dev.openbili.webdemo.video.CommentProfileAnchor
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

@OptIn(UnstableApi::class)
@Composable
internal fun appRootTailContent(
  s: AppRootStates,
  mainViewModel: MainViewModel,
  feedViewModel: FeedViewModel,
  authViewModel: AuthViewModel,
  playerViewModel: PlayerViewModel,
  myViewModel: MyViewModel,
  profileMessageViewModel: MyViewModel,
  searchViewModel: SearchViewModel,
  settingsViewModel: AppSettingsViewModel,
  bangumiRecommendationViewModel: BangumiRecommendationViewModel,
  scope: CoroutineScope,
  playbackCtx: AppRootPlaybackContext,
  profileCtx: AppRootProfileContext,
  videoPageCtx: AppRootVideoPageContext,
  networkAvailableState: MutableState<Boolean>,
  startupWarmupVisibleState: MutableState<Boolean>,
  startupWarmupAlpha: Animatable<Float, AnimationVector1D>,
  rootPlayerOwnershipState: MutableState<RootPlayerOwnership>,
  rootPagerState: PagerState,
  feedGridState: LazyGridState,
  searchGridState: LazyGridState,
  bangumiIndexGridState: LazyGridState,
  profileStateHolder: SaveableStateHolder,
  liveRoomStateHolder: SaveableStateHolder,
  playerSession: AppRootPlayerSessionState,
  onSearch: () -> Unit,
  onFeedRefresh: () -> Unit,
  onFeedPullRefresh: (Int) -> Unit,
  onExitRequested: () -> Unit,
  rootPlayerContent: @Composable (androidx.compose.ui.Modifier, Float, Boolean, Boolean, SharedPlayerViewRole, Boolean?, Boolean) -> Unit,
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
  val lifecycleOwner = LocalLifecycleOwner.current
  val rootDensity = LocalDensity.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val controlMode = LocalControlMode.current
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
  var networkAvailable by networkAvailableState
  // 信息流几何是事件时刻的引用，不是 UI 状态：把它留在快照状态之外，避免滑动
  // 期间的每次布局 tick 都使 AppRoot 失效。
  var startupWarmupVisible by startupWarmupVisibleState


  val latestMySection by rememberUpdatedState(myState.section)
  val liveHomeViewModel: LiveHomeViewModel = viewModel()
  val liveHomeState by liveHomeViewModel.state.collectAsState()
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


  val rootPageSwitchInProgress = rootPageSwitchRequested || rootPagerState.isScrollInProgress
  val bangumiRootPageActive =
    shouldActivateBangumiRootPage(
      selectedTab = rootTab,
      settledPage = rootPagerState.settledPage,
      pageSwitchInProgress = rootPageSwitchInProgress,
      videoScreenVisible = appState.isVideoScreen,
    )


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



  val danmakuPausedForPlayer =
    !isPlaying ||
      videoExitPrelude != null ||
      transitionSession != null ||
      (transitionPhase !is TransitionPhase.Feed && transitionPhase !is TransitionPhase.Video)
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

  fun currentPreferredResolutionMode() = settings.preferredResolutionModeFor(context)


  fun launchTransition(block: suspend CoroutineScope.() -> Unit) = playbackCtx.launchTransition(block)


  fun animateToRootTab(tab: RootTab) = playbackCtx.animateToRootTab(tab)


  fun restoredBangumiCard(sourceCard: SpaceContentCard): SpaceContentCard =
    playbackCtx.restoredBangumiCard(sourceCard)


  fun commitPlaybackProgress() = playbackCtx.commitPlaybackProgress()


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

  fun openCommentProfile(mid: Long, comment: CommentItem, anchor: CommentProfileAnchor) =
    profileCtx.openCommentProfile(mid, comment, anchor)


  fun openArticleCommentProfile(mid: Long, comment: CommentItem, anchor: CommentProfileAnchor) =
    profileCtx.openArticleCommentProfile(mid, comment, anchor)


  fun retainedPlaybackPage(itemId: String): VideoPage? = profileCtx.retainedPlaybackPage(itemId)


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


  val tailBody: @Composable () -> Unit = {
  LaunchedEffect(appState.selectedVideo?.id, dataCommitAllowedId) {
    val item = appState.selectedVideo ?: return@LaunchedEffect
    if (dataCommitAllowedId != item.id) return@LaunchedEffect
    historyStartTimestamp = System.currentTimeMillis() / 1000
    val cached = videoEntryCache[item.id]
    if (dataCommitAllowedId == item.id) {
      if (cached != null) restoreEntry(cached) else clearVisibleVideoData()
    }
    ensureVideoPageData(item)
  }

  LaunchedEffect(dataCommitAllowedId, appState.selectedVideo) {
    val item = appState.selectedVideo ?: return@LaunchedEffect
    if (dataCommitAllowedId != item.id) return@LaunchedEffect
    val cached = videoEntryCache[item.id]
    if (cached == null) {
      clearVisibleVideoData()
    } else if (playerActivationId == item.id) {
      // 真正新鲜的卡片打开可以重启已完成的缓存。
      restoreEntryForFreshPlayback(cached)
    } else {
      // 栈返回刻意保留缓存的终态。旧实现里这种无条件的新鲜恢复会在父页挂载的
      // 瞬间清掉 playbackEnded，使其结束覆盖层在整个卡片飞行期间缺失、只在最终
      // 清理时才重新出现。
      restoreEntry(cached)
    }
  }

  LaunchedEffect(
    appState.selectedVideo?.id,
    historyCid,
    historyDuration,
    dataCommitAllowedId,
  ) {
    val item = appState.selectedVideo ?: return@LaunchedEffect
    if (dataCommitAllowedId != item.id || historyCid <= 0L) return@LaunchedEffect
    val expectedCid = historyCid
    val expectedDuration = historyDuration
    val cachedEntry = videoEntryCache[item.id]
    danmakuWindowController.seedLocalDanmaku(expectedCid, cachedEntry?.danmaku.orEmpty())
    val initialPositionMs =
      cachedEntry?.takeIf { it.cid == expectedCid }?.savedPositionMs ?: playerUiPositionProvider()
    danmakuWindowController.monitor(
      cid = expectedCid,
      durationSeconds = expectedDuration,
      initialPositionMs = initialPositionMs,
      positionProvider = playerPositionProvider,
    ) { window ->
      if (appState.selectedVideo?.id != item.id || historyCid != expectedCid) return@monitor
      danmaku = window
      videoEntryCache[item.id]
        ?.takeIf { it.cid == expectedCid }
        ?.let { cacheEntry(it.copy(danmaku = window)) }
    }
  }

  LaunchedEffect(
    appState.selectedVideo?.id,
    historyCid,
    historyDuration,
    dataCommitAllowedId,
    settings.danmakuDensity == 5,
    videoInfo?.publishedAt,
    videoInfo?.danmakuCount,
  ) {
    val item = appState.selectedVideo ?: return@LaunchedEffect
    if (dataCommitAllowedId != item.id || historyCid <= 0L) return@LaunchedEffect
    val expectedCid = historyCid
    val requestAllHistory = settings.danmakuDensity == 5
    if (!requestAllHistory) {
      danmakuWindowController.clearHistoricalDanmaku(expectedCid)?.let { window ->
        if (appState.selectedVideo?.id == item.id && historyCid == expectedCid) danmaku = window
      }
      return@LaunchedEffect
    }
    val expectedDuration = historyDuration
    val expectedPublishedAt = videoInfo?.publishedAt ?: 0L
    val expectedDanmakuCount = videoInfo?.danmakuCount ?: 0L
    // 在回溯历史月份前等待视频发布时间戳：此效果以上方的 videoInfo 为键，
    // 元数据一到就会重启。
    if (expectedPublishedAt <= 0L) return@LaunchedEffect

    val loaded =
      withContext(Dispatchers.IO) {
        runCatching {
            BiliDanmakuApi.getDanmaku(
              cid = expectedCid,
              durationSeconds = expectedDuration,
              publishedAt = expectedPublishedAt,
              expectedCount = expectedDanmakuCount,
              includeHistory = true,
            )
          }
          .getOrDefault(emptyList())
      }
    if (
      appState.selectedVideo?.id != item.id ||
        historyCid != expectedCid ||
        settings.danmakuDensity != 5
    ) {
      return@LaunchedEffect
    }
    danmakuWindowController.setHistoricalDanmaku(expectedCid, loaded)?.let { window ->
      danmaku = window
      videoEntryCache[item.id]
        ?.takeIf { it.cid == expectedCid }
        ?.let { cacheEntry(it.copy(danmaku = window)) }
    }
  }

  LaunchedEffect(
    appState.selectedVideo?.id,
    historyAid,
    historyCid,
    dataCommitAllowedId,
    settings.danmakuSmartBlocking,
  ) {
    val item = appState.selectedVideo ?: return@LaunchedEffect
    if (
      !settings.danmakuSmartBlocking ||
        dataCommitAllowedId != item.id ||
        historyAid <= 0L ||
        historyCid <= 0L
    ) {
      return@LaunchedEffect
    }
    val expectedAid = historyAid
    val expectedCid = historyCid
    videoEntryCache[item.id]?.danmakuMask?.let {
      danmakuMask = it
      return@LaunchedEffect
    }
    val bvid =
      item.id.takeIf { it.startsWith("BV") }
        ?: item.videoUrl
          .substringAfterLast("/")
          .substringBefore("?")
          .takeIf { it.startsWith("BV") }
          .orEmpty()
    val resource =
      withContext(Dispatchers.IO) {
        runCatching {
            BiliDanmakuApi.getDanmakuMaskResource(
              aid = expectedAid,
              cid = expectedCid,
              bvid = bvid,
            )
          }
          .getOrNull()
      }
    val loadedMask = resource?.let {
      withContext(Dispatchers.Default) { DanmakuMaskParser.parse(it) }
    }
    if (
      appState.selectedVideo?.id != item.id ||
        historyAid != expectedAid ||
        historyCid != expectedCid ||
        !settings.danmakuSmartBlocking
    ) {
      return@LaunchedEffect
    }
    danmakuMask = loadedMask
    if (loadedMask != null) {
      videoEntryCache[item.id]
        ?.takeIf { it.cid == expectedCid }
        ?.let { cacheEntry(it.copy(danmakuMask = loadedMask)) }
    }
  }

  LaunchedEffect(appState.selectedVideo?.id, dataCommitAllowedId) {
    val item = appState.selectedVideo ?: return@LaunchedEffect
    if (dataCommitAllowedId != item.id || emotePackages.isNotEmpty()) return@LaunchedEffect
    val packages =
      withContext(Dispatchers.IO) {
        runCatching { BiliCommentApi.getReplyEmotes() }.getOrDefault(emptyList())
      }
    if (appState.selectedVideo?.id == item.id && packages.isNotEmpty()) {
      emotePackages = packages
      emotes = packages.flatMap { it.emotes }.distinctBy { it.text }
    }
  }

  LaunchedEffect(
    videoInfo?.aid,
    authUserInfo.mid,
    authUserInfo.isLogin,
    dataCommitAllowedId,
    transitionSession,
  ) {
    val aid = videoInfo?.aid ?: 0L
    val currentAccountMid = authUserInfo.mid.takeIf { authUserInfo.isLogin } ?: 0L
    if (
      transitionSession != null ||
        dataCommitAllowedId != appState.selectedVideo?.id ||
        appState.selectedVideo == null
    )
      return@LaunchedEffect
    val retained = appState.selectedVideo?.id?.let(videoEntryCache::get)
    val retainedAccountState = retained?.takeIf {
      currentAccountMid > 0L && it.engagementAccountMid == currentAccountMid && it.engagement.loaded
    }
    videoEngagement = retainedAccountState?.engagement ?: VideoEngagement()
    videoState.videoEngagementAccountMid = retainedAccountState?.engagementAccountMid ?: 0L
    favoriteFolders = retainedAccountState?.favoriteFolders.orEmpty()
    favoriteFoldersLoading = false
    videoActionBusy = false
    if (aid <= 0 || currentAccountMid <= 0L) return@LaunchedEffect
    val result = withContext(Dispatchers.IO) { runCatching { BiliVideoApi.getVideoEngagement(aid) } }
    if (videoInfo?.aid != aid || !authUserInfo.isLogin || authUserInfo.mid != currentAccountMid) {
      return@LaunchedEffect
    }
    result
      .onSuccess { engagement ->
        videoEngagement = engagement
        videoState.videoEngagementAccountMid = currentAccountMid
        appState.selectedVideo?.id?.let { itemId ->
          videoEntryCache[itemId]?.let { entry ->
            cacheEntry(
              entry.copy(
                engagement = engagement,
                engagementAccountMid = currentAccountMid,
              )
            )
          }
        }
      }
      .onFailure { error ->
        Toast.makeText(
            context,
            error.message ?: "点赞、投币和收藏状态获取失败",
            Toast.LENGTH_SHORT,
          )
          .show()
      }
  }

  LaunchedEffect(historyAid, historyCid, transitionSession) {
    if (transitionSession != null) return@LaunchedEffect
    if (historyAid <= 0 || historyCid <= 0) {
      onlineViewerText = null
      return@LaunchedEffect
    }
    while (true) {
      if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
        return@LaunchedEffect
      }
      onlineViewerText =
        withContext(Dispatchers.IO) {
          runCatching { BiliVideoApi.getOnlineViewerText(historyAid, historyCid) }.getOrNull()
        }
      delay(30_000)
    }
  }

  // ── 登录面板 ─────────────────────────────────────────────────────
  if (loginState !is LoginState.Idle) {
    LoginSheet(
      loginState = loginState,
      onDismiss = { authViewModel.cancelLogin() },
      onRetry = { authViewModel.retryLogin() },
    )
  }


  // ── 开始进入转场 ───────────────────────────────────────────
  val enterBangumiCtx =
    AppRootEnterBangumiContext(
      context = context,
      scope = scope,
      keyboardController = keyboardController,
      videoEntryCache = videoEntryCache,
      playerViewModel = playerViewModel,
      mainViewModel = mainViewModel,
      authViewModel = authViewModel,
      bangumiExploreViewModel = bangumiExploreViewModel,
      videoState = s.videoState,
      playerSession = playerSession,
      profileState = s.profileState,
      searchCardBounds = s.searchCardBounds,
      myCardBounds = s.myCardBounds,
      bangumiIndexCardBounds = s.bangumiIndexCardBounds,
      appStateState = appStateState,
      settingsState = settingsState,
      authUserInfoState = authUserInfoState,
      playerStateState = playerStateState,
      renderedVideoId = renderedVideoId,
      transitionTokenState = s.transitionTokenState,
      dataCommitAllowedIdState = s.dataCommitAllowedIdState,
      hiddenProfileCoverItemIdState = s.hiddenProfileCoverItemIdState,
      pendingVideoCommentTargetState = s.pendingVideoCommentTargetState,
      rootPlayerOwnershipState = rootPlayerOwnershipState,
      hiddenMyCoverItemIdState = s.hiddenMyCoverItemIdState,
      playerActivationIdState = s.playerActivationIdState,
      showEmbeddedCoverState = s.showEmbeddedCoverState,
      profileStackState = s.profileStackState,
      hiddenArticleVideoCoverItemIdState = s.hiddenArticleVideoCoverItemIdState,
      profileVideoTransitionActiveState = s.profileVideoTransitionActiveState,
      transitionSessionState = s.transitionSessionState,
      transitionPhaseState = s.transitionPhaseState,
      hiddenSearchCoverItemIdState = s.hiddenSearchCoverItemIdState,
      videoStackState = s.videoStackState,
      profileLayerSuppressedState = s.profileLayerSuppressedState,
      deferBangumiHomePageCompositionState = s.deferBangumiHomePageCompositionState,
      deferBangumiIndexPageCompositionState = s.deferBangumiIndexPageCompositionState,
      hiddenBangumiIndexItemIdState = s.hiddenBangumiIndexItemIdState,
      deferSearchBangumiPageCompositionState = s.deferSearchBangumiPageCompositionState,
      hiddenHomeDynamicCoverItemIdState = s.hiddenHomeDynamicCoverItemIdState,
      hiddenFeedCoverItemIdState = s.hiddenFeedCoverItemIdState,
      hiddenBangumiRecommendationItemIdState = s.hiddenBangumiRecommendationItemIdState,
      bangumiPosterBoundsState = s.bangumiPosterBoundsState,
      activeBangumiPageState = s.activeBangumiPageState,
      hiddenPopularCoverItemIdState = s.hiddenPopularCoverItemIdState,
      playerBoundsState = s.playerBoundsState,
      videoPageDataReadyIdState = s.videoPageDataReadyIdState,
      selectCollectionEpisodeRef = ::selectCollectionEpisode,
      activeProfileEntryRef = ::activeProfileEntry,
      restoreEntryForFreshPlaybackRef = ::restoreEntryForFreshPlayback,
      awaitStablePlayerBoundsRef = ::awaitStablePlayerBounds,
      cacheEntryRef = ::cacheEntry,
      restoredBangumiCardRef = ::restoredBangumiCard,
      clearVisibleVideoDataRef = ::clearVisibleVideoData,
      currentPreferredResolutionModeRef = ::currentPreferredResolutionMode,
      snapshotEntryRef = ::snapshotEntry,
      revealTransitionSessionRef = ::revealTransitionSession,
      prepareCardTransitionRef = ::prepareCardTransition,
      launchTransitionRef = ::launchTransition,
      retainedPlaybackPageRef = ::retainedPlaybackPage,
    )

  fun startEnterVideo(
    item: FeedItem,
    cardBounds: Rect?,
    origin: VideoOrigin = VideoOrigin.OTHER,
    commentTarget: CommentNavigationTarget? = null,
    rootFeedScrollAnchor: FeedScrollAnchor? = null,
    onLanded: (() -> Unit)? = null,
    fitCover: Boolean = false,
    preserveCurrentPlayback: Boolean = false,
    startPositionMs: Long = 0L,
    restoreSavedProgress: Boolean = true,
    transitionTargetBounds: (() -> Rect)? = null,
    sourceAnchorKey: String? = null,
  ) = enterBangumiCtx.startEnterVideo(item, cardBounds, origin, commentTarget, rootFeedScrollAnchor, onLanded, fitCover, preserveCurrentPlayback, startPositionMs, restoreSavedProgress, transitionTargetBounds, sourceAnchorKey)

  fun startProfileVideo(profileEntryId: Long, item: FeedItem, cardBounds: Rect) =
    enterBangumiCtx.startProfileVideo(profileEntryId, item, cardBounds)

  fun selectBangumiEpisode(episode: BangumiEpisode) = enterBangumiCtx.selectBangumiEpisode(episode)

  fun selectBangumiSeason(seasonId: Long) = enterBangumiCtx.selectBangumiSeason(seasonId)

  fun toggleBangumiFollow() = enterBangumiCtx.toggleBangumiFollow()

  fun postBangumiShortReview(score: Int, content: String) =
    enterBangumiCtx.postBangumiShortReview(score, content)

  fun loadActiveBangumiMetadata(card: SpaceContentCard) =
    enterBangumiCtx.loadActiveBangumiMetadata(card)

  fun startRootBangumi(
    card: SpaceContentCard,
    item: FeedItem,
    cardBounds: Rect,
    pageOrigin: PageOrigin,
    videoOrigin: VideoOrigin,
    restoreEpisodeSelection: Boolean = true,
    preserveCurrentPlayback: Boolean = false,
    initialSeason: BangumiSeason? = null,
    returnToSourceCover: Boolean = false,
  ) = enterBangumiCtx.startRootBangumi(card, item, cardBounds, pageOrigin, videoOrigin, restoreEpisodeSelection, preserveCurrentPlayback, initialSeason, returnToSourceCover)

  fun startHistoryBangumi(card: SpaceContentCard, item: FeedItem, cardBounds: Rect) =
    enterBangumiCtx.startHistoryBangumi(card, item, cardBounds)

  fun startSearchBangumi(
    card: SpaceContentCard,
    cardBounds: Rect,
    sourceIsBangumiExplorePoster: Boolean = false,
    sourceOrigin: PageOrigin = PageOrigin.Search,
  ) = enterBangumiCtx.startSearchBangumi(card, cardBounds, sourceIsBangumiExplorePoster, sourceOrigin)

  fun startProfileBangumi(profileEntryId: Long, card: SpaceContentCard, cardBounds: Rect) =
    enterBangumiCtx.startProfileBangumi(profileEntryId, card, cardBounds)

  LaunchedEffect(
    activeBangumiPage?.currentEpisodeId,
    activeBangumiPage?.season?.seasonId,
    videoPageDataReadyId,
    dataCommitAllowedId,
  ) {
    val page = activeBangumiPage ?: return@LaunchedEffect
    val item = appState.selectedVideo ?: return@LaunchedEffect
    if (dataCommitAllowedId != item.id) return@LaunchedEffect
    val season = page.season ?: return@LaunchedEffect
    val episode =
      (season.episodes + season.sections.flatMap(BangumiSection::episodes)).firstOrNull {
        it.id == page.currentEpisodeId
      } ?: return@LaunchedEffect
    if (episode.cid <= 0L || episode.durationSeconds <= 0L) return@LaunchedEffect
    val identityChanged = historyAid != episode.aid || historyCid != episode.cid
    historyAid = episode.aid
    historyCid = episode.cid
    historyDuration = episode.durationSeconds
    if (identityChanged) {
      historyStartTimestamp = System.currentTimeMillis() / 1_000L
      danmaku = emptyList()
      danmakuMask = null
    }
    // Media3 强制播放器访问必须在主线程：切到 IO 做两次持久化写入之前先抓取
    // 播放快照。
    val player = playerViewModel.exoPlayer
    val playbackPositionMs =
      player
        ?.takeIf {
          playerViewModel.isPlaybackIdentityActive(item.id, episode.aid, episode.cid) &&
            isPlaybackSnapshotValid(
              expectedMediaId = item.id,
              actualMediaId = it.currentMediaItem?.mediaId,
              playbackState = it.playbackState,
              requireReady = false,
            )
        }
        ?.currentPosition
    withContext(Dispatchers.IO) {
      BangumiPlaybackStore.save(
        context.applicationContext,
        page.sourceCard,
        season.seasonId,
        episode,
      )
      playbackPositionMs?.let { positionMs ->
        BangumiLocalHistoryStore.record(
          context.applicationContext,
          page.sourceCard,
          season.seasonId,
          episode,
          positionMs,
          episode.durationSeconds * 1_000L,
        )
      }
    }
    videoEntryCache[item.id]?.let { entry ->
      cacheEntry(
        entry.copy(
          cid = episode.cid,
          durationSeconds = episode.durationSeconds,
          danmaku = if (identityChanged) emptyList() else entry.danmaku,
          danmakuMask = if (identityChanged) null else entry.danmakuMask,
        )
      )
    }
  }

  val exitCtx =
    AppRootExitContext(
      scope = scope,
      keyboardController = keyboardController,
      videoEntryCache = videoEntryCache,
      playerViewHolder = s.playerViewHolder,
      playerViewModel = playerViewModel,
      mainViewModel = mainViewModel,
      bangumiExploreViewModel = bangumiExploreViewModel,
      videoState = s.videoState,
      playerSession = playerSession,
      profileState = s.profileState,
      feedState = feedState,
      renderedVideoId = renderedVideoId,
      appStateState = appStateState,
      settingsState = settingsState,
      playerStateState = playerStateState,
      feedGridState = feedGridState,
      popularCardBounds = s.popularCardBounds,
      feedCardBounds = s.feedCardBounds,
      myCardBounds = s.myCardBounds,
      searchCardBounds = s.searchCardBounds,
      bangumiIndexCardBounds = s.bangumiIndexCardBounds,
      articleVideoBounds = s.articleVideoBounds,
      profileCardBounds = s.profileCardBounds,
      bangumiSeasonExitFadeAlpha = s.bangumiSeasonExitFadeAlpha,
      videoStackState = s.videoStackState,
      showEmbeddedCoverState = s.showEmbeddedCoverState,
      transitionPhaseState = s.transitionPhaseState,
      transitionSessionState = s.transitionSessionState,
      transitionTokenState = s.transitionTokenState,
      playerActivationIdState = s.playerActivationIdState,
      dataCommitAllowedIdState = s.dataCommitAllowedIdState,
      rootPlayerOwnershipState = rootPlayerOwnershipState,
      hiddenArticleVideoCoverItemIdState = s.hiddenArticleVideoCoverItemIdState,
      hiddenMyCoverItemIdState = s.hiddenMyCoverItemIdState,
      hiddenSearchCoverItemIdState = s.hiddenSearchCoverItemIdState,
      hiddenProfileCoverItemIdState = s.hiddenProfileCoverItemIdState,
      profileLayerSuppressedState = s.profileLayerSuppressedState,
      hiddenPopularCoverItemIdState = s.hiddenPopularCoverItemIdState,
      hiddenHomeDynamicCoverItemIdState = s.hiddenHomeDynamicCoverItemIdState,
      hiddenFeedCoverItemIdState = s.hiddenFeedCoverItemIdState,
      hiddenBangumiRecommendationItemIdState = s.hiddenBangumiRecommendationItemIdState,
      hiddenBangumiIndexItemIdState = s.hiddenBangumiIndexItemIdState,
      bangumiPosterBoundsState = s.bangumiPosterBoundsState,
      activeBangumiPageState = s.activeBangumiPageState,
      deferSearchBangumiPageCompositionState = s.deferSearchBangumiPageCompositionState,
      deferBangumiIndexPageCompositionState = s.deferBangumiIndexPageCompositionState,
      deferBangumiHomePageCompositionState = s.deferBangumiHomePageCompositionState,
      playerBoundsState = s.playerBoundsState,
      videoPageDataReadyIdState = s.videoPageDataReadyIdState,
      profileVideoTransitionActiveState = s.profileVideoTransitionActiveState,
      bangumiPreviewTargetState = s.bangumiPreviewTargetState,
      hiddenRecommendationCoverItemIdState = s.hiddenRecommendationCoverItemIdState,
      hiddenPlaybackEndRecommendationCoverItemIdState = s.hiddenPlaybackEndRecommendationCoverItemIdState,
      videoExitPreludeState = s.videoExitPreludeState,
      profileBangumiReturnRequestState = s.profileBangumiReturnRequestState,
      activeRevealJobState = s.activeRevealJobState,
      activeTransitionJobState = s.activeTransitionJobState,
      launchTransitionRef = ::launchTransition,
      prepareCardTransitionRef = ::prepareCardTransition,
      prepareExitTransitionRef = ::prepareExitTransition,
      currentPreferredResolutionModeRef = ::currentPreferredResolutionMode,
      restoreEntryRef = ::restoreEntry,
      restoreEntryForFreshPlaybackRef = ::restoreEntryForFreshPlayback,
      snapshotEntryRef = ::snapshotEntry,
      ensureVideoPageDataRef = ::ensureVideoPageData,
      clearVisibleVideoDataRef = ::clearVisibleVideoData,
      cacheEntryRef = ::cacheEntry,
      retainedPlaybackPageRef = ::retainedPlaybackPage,
      commitPlaybackProgressRef = ::commitPlaybackProgress,
      loadProfileRef = ::loadProfile,
      restoreProfileRef = ::restoreProfile,
      activeProfileEntryRef = ::activeProfileEntry,
    )

  fun beginVideoExitPrelude(
    item: FeedItem,
    bounds: Rect,
    fitCover: Boolean = false,
    reusePlayerSurface: Boolean = false,
  ): VideoExitPrelude = exitCtx.beginVideoExitPrelude(item, bounds, fitCover, reusePlayerSurface)

  fun startExitBangumi() = exitCtx.startExitBangumi()

  fun startExitVideoToProfile() = exitCtx.startExitVideoToProfile()

  fun cancelPreparingProfileVideo() = exitCtx.cancelPreparingProfileVideo()

  fun startExitVideo() = exitCtx.startExitVideo()

  fun startBackToPreviousVideo() = exitCtx.startBackToPreviousVideo()

  fun reverseActiveEnter() = exitCtx.reverseActiveEnter()

  fun cancelPreparingRootEnter() = exitCtx.cancelPreparingRootEnter()

  fun startRecommendedVideo(
    current: FeedItem,
    recommendation: FeedItem,
    bounds: Rect,
    returnBounds: Rect?,
    fromPlaybackEnd: Boolean,
  ) = exitCtx.startRecommendedVideo(current, recommendation, bounds, returnBounds, fromPlaybackEnd)
  fun returnDirectlyHome() {
    if (transitionSession != null || directHomeInProgress) return
    // 在启动第一个动画帧之前锁定导航：否则快速按返回可能在直接回首页淡出仍
    // 在排程时启动普通卡片返回转场。
    directHomeInProgress = true
    keyboardController?.hide()
    commitPlaybackProgress()
    playerViewModel.exoPlayer?.pause()
    launchTransition {
      rootPagerState.scrollToPage(RootTab.HOME.ordinal)
      rootTab = RootTab.HOME
      showSearch = false
      showSearchResults = false
      profileTransitionJob?.cancel()
      commentProfileTransition = null
      commentProfileReturnTransition = null
      avatarProfileTransition = null
      avatarProfileReturnTransition = null
      profileMid = null
      profileStack = emptyList()
      profileLayerSuppressed = false
      directHomeAlpha.snapTo(1f)
      withFrameNanos {}
      directHomeAlpha.animateTo(
        0f,
        tween(if (settings.reduceMotion) 70 else 160, easing = FastOutSlowInEasing),
      )
      if (myState.section == dev.openbili.webdemo.my.MySection.FOLLOWING) {
        myViewModel.commitPendingUnfollows()
      }
      mainViewModel.returnToFeed()
      videoStack = emptyList()
      dataCommitAllowedId = null
      playerActivationId = null
      transitionSession = null
      transitionPhase = TransitionPhase.Feed
      hiddenFeedCoverItemId = null
      hiddenMyCoverItemId = null
      hiddenSearchCoverItemId = null
      hiddenArticleVideoCoverItemId = null
      hiddenRecommendationCoverItemId = null
      hiddenPlaybackEndRecommendationCoverItemId = null
      hiddenProfileCoverItemId = null
      profileVideoTransitionActive = false
      articleTransitionJob?.cancel()
      articleStack = emptyList()
      articleLoadToken += 1
      articleContentReady = false
      articleRestoringParentEntryId = null
      articleSuspendedVideo = null
      articleDetail = null
      articleLoading = false
      articleError = null
      articleTransitionSession = null
      articleHeroBounds = Rect.Zero
      hiddenMyArticleItemId = null
      hiddenSearchArticleItemId = null
      hiddenProfileArticleItemId = null
      hiddenHomeDynamicArticleItemId = null
      hiddenVideoCommentArticleItemId = null
      hiddenArticleCommentArticleItemId = null
      articlePageAlpha.snapTo(0f)
      directHomeAlpha.snapTo(1f)
      directHomeInProgress = false
    }
  }

  val articleCtx =
    AppRootArticleContext(
      context = context,
      scope = scope,
      playerViewModel = playerViewModel,
      mainViewModel = mainViewModel,
      videoEntryCache = videoEntryCache,
      articlePageAlpha = s.articlePageAlpha,
      homeDynamicArticleBounds = s.homeDynamicArticleBounds,
      profileArticleBounds = s.profileArticleBounds,
      myCardBounds = s.myCardBounds,
      searchArticleBounds = s.searchArticleBounds,
      myArticleBounds = s.myArticleBounds,
      myInteractionVideoMessageIds = s.myInteractionVideoMessageIds,
      myInteractionArticleMessageIds = s.myInteractionArticleMessageIds,
      articleDetailCache = s.articleDetailCache,
      appStateState = appStateState,
      settingsState = settingsState,
      playerStateState = playerStateState,
      transitionPhaseState = s.transitionPhaseState,
      transitionSessionState = s.transitionSessionState,
      articleTransitionSessionState = s.articleTransitionSessionState,
      playerActivationIdState = s.playerActivationIdState,
      hiddenProfileArticleItemIdState = s.hiddenProfileArticleItemIdState,
      showEmbeddedCoverState = s.showEmbeddedCoverState,
      articleContentReadyState = s.articleContentReadyState,
      articleDetailState = s.articleDetailState,
      videoStackState = s.videoStackState,
      hiddenHomeDynamicArticleItemIdState = s.hiddenHomeDynamicArticleItemIdState,
      profileLayerSuppressedState = s.profileLayerSuppressedState,
      pendingVideoCommentTargetState = s.pendingVideoCommentTargetState,
      articleStackState = s.articleStackState,
      interactionTargetLoadingIdState = s.interactionTargetLoadingIdState,
      hiddenMyArticleItemIdState = s.hiddenMyArticleItemIdState,
      dataCommitAllowedIdState = s.dataCommitAllowedIdState,
      hiddenSearchArticleItemIdState = s.hiddenSearchArticleItemIdState,
      pendingArticleCommentTargetState = s.pendingArticleCommentTargetState,
      articleSuspendedVideoState = s.articleSuspendedVideoState,
      hiddenVideoCommentArticleItemIdState = s.hiddenVideoCommentArticleItemIdState,
      commentNavigationRequestTokenState = s.commentNavigationRequestTokenState,
      articleTransitionJobState = s.articleTransitionJobState,
      hiddenArticleCommentArticleItemIdState = s.hiddenArticleCommentArticleItemIdState,
      articleErrorState = s.articleErrorState,
      articleRestoringParentEntryIdState = s.articleRestoringParentEntryIdState,
      articleHeroBoundsState = s.articleHeroBoundsState,
      articleLoadTokenState = s.articleLoadTokenState,
      articleLoadingState = s.articleLoadingState,
      articleEntryTokenState = s.articleEntryTokenState,
      startEnterVideoRef = { item: FeedItem, cardBounds: Rect?, origin: VideoOrigin, commentTarget: CommentNavigationTarget? ->
        startEnterVideo(item, cardBounds, origin, commentTarget)
      },
      startProfileVideoRef = { profileEntryId: Long, item: FeedItem, cardBounds: Rect ->
        startProfileVideo(profileEntryId, item, cardBounds)
      },
      retainedPlaybackPageRef = { itemId: String -> retainedPlaybackPage(itemId) },
      commitPlaybackProgressRef = { commitPlaybackProgress() },
      cacheEntryRef = { entry: VideoPageEntry -> cacheEntry(entry) },
      clearVisibleVideoDataRef = { clearVisibleVideoData() },
      snapshotEntryRef = { item: FeedItem -> snapshotEntry(item) },
      currentPreferredResolutionModeRef = { currentPreferredResolutionMode() },
      restoreEntryRef = { entry: VideoPageEntry -> restoreEntry(entry) },
    )

  LaunchedEffect(authUserInfo.isLogin, authUserInfo.mid, authUserInfo.vipActive) {
    withContext(Dispatchers.IO) {
      OfflineMediaManager.get(context).reconcileEntitlements(authUserInfo)
    }
  }

  fun startExitArticle() = articleCtx.startExitArticle()

  fun startEnterArticle(
    article: ArticleItem,
    sourceBounds: Rect?,
    origin: ArticleOrigin,
    commentTarget: CommentNavigationTarget? = null,
  ) = articleCtx.startEnterArticle(article, sourceBounds, origin, commentTarget)

  fun openInteractionTarget(
    message: AccountMessage,
    sourceBounds: Rect,
    profileEntryId: Long? = null,
  ) = articleCtx.openInteractionTarget(message, sourceBounds, profileEntryId)

  fun loadArticleDetail(article: ArticleItem) = articleCtx.loadArticleDetail(article)
  val overlayTransitionContext =
    remember {
      AppRootOverlayContext(
        scope = scope,
        settings = settings,
        searchViewModel = searchViewModel,
        bangumiIndexViewModel = bangumiIndexViewModel,
        bangumiExploreViewModel = bangumiExploreViewModel,
        articleStack = articleStack,
        controlMode = controlMode,
        rootTab = rootTab,
        transitionPhase = s.transitionPhaseState,
        focusManager = focusManager,
        keyboardController = keyboardController,
        controlInitialFocusRequester = s.controlInitialFocusRequester,
        activeLiveRoom = s.activeLiveRoomState,
        homeControlLevel = s.homeControlLevelState,
        bangumiControlLevel = s.bangumiControlLevelState,
        homeControlSearchFocusRequest = s.homeControlSearchFocusRequestState,
        homeControlFocusRestoreRequest = s.homeControlFocusRestoreRequestState,
        showSearch = s.showSearchState,
        showSearchResults = s.showSearchResultsState,
        searchOpenedFromController = s.searchOpenedFromControllerState,
        showBangumiIndex = s.showBangumiIndexState,
        bangumiIndexTransitionDirection = s.bangumiIndexTransitionDirectionState,
        bangumiIndexTransitionSourceBounds = s.bangumiIndexTransitionSourceBoundsState,
        bangumiIndexTransitionJob = s.bangumiIndexTransitionJobState,
        bangumiIndexTransitionProgress = s.bangumiIndexTransitionProgress,
        bangumiIndexTransitionMaskAlpha = s.bangumiIndexTransitionMaskAlpha,
        bangumiIndexTransitionScrimAlpha = s.bangumiIndexTransitionScrimAlpha,
        searchTransitionDirection = s.searchTransitionDirectionState,
        searchTransitionQuery = s.searchTransitionQueryState,
        searchTransitionJob = s.searchTransitionJobState,
        searchTransitionPreparation = s.searchTransitionPreparationState,
        searchTransitionProgress = s.searchTransitionProgress,
        searchTransitionMaskAlpha = s.searchTransitionMaskAlpha,
        searchTransitionScrimAlpha = s.searchTransitionScrimAlpha,
        searchTransitionSourceBounds = s.searchTransitionSourceBoundsState,
        searchBounds = s.searchBoundsState,
        liveAreaIndex = s.liveAreaIndex,
        liveAreaIndexFocusRestoreRequest = s.liveAreaIndexFocusRestoreRequestState,
        appState = appState,
      )
    }
  AppRootOverlayBackHandlers(overlayTransitionContext)

  BackHandler(
    enabled =
      articleStack.isNotEmpty() &&
        appState.selectedVideo == null &&
        (profileStack.isEmpty() || profileLayerSuppressed),
    onBack = ::startExitArticle,
  )

  // 系统返回键：走与左上角箭头相同的退出转场，而不是 MainActivity 直接的
  // returnToFeed()——那会把 transitionPhase 留在 Video 导致白屏。
  BackHandler(
    enabled =
      appState.selectedVideo != null && !appState.video.isFullscreen && !directHomeInProgress
  ) {
    val session = transitionSession
    when {
      session?.kind == TransitionKind.ENTER_ROOT ||
        session?.kind == TransitionKind.ENTER_RECOMMENDATION ||
        session?.kind == TransitionKind.ENTER_PROFILE -> reverseActiveEnter()
      session == null && transitionPhase is TransitionPhase.ToVideo ->
        if (videoStack.lastOrNull()?.parentPage is PageOrigin.Profile) cancelPreparingProfileVideo()
        else cancelPreparingRootEnter()
      transitionPhase is TransitionPhase.Video ->
        if (activeBangumiPage != null) startExitBangumi()
        else if (videoStack.lastOrNull()?.parentPage is PageOrigin.Profile)
          startExitVideoToProfile()
        else if (videoStack.size > 1) startBackToPreviousVideo() else startExitVideo()
      else -> Unit
    }
  }

  val liveTransitionContext =
    remember {
      AppRootLiveTransitionContext(
        scope = scope,
        settings = settings,
        playerViewModel = playerViewModel,
        myViewModel = myViewModel,
        activeLiveOrigin = s.activeLiveOriginState,
        activeLiveSourceAnchor = s.activeLiveSourceAnchorState,
        hiddenSearchCoverItemId = s.hiddenSearchCoverItemIdState,
        hiddenMyCoverItemId = s.hiddenMyCoverItemIdState,
        hiddenHomeLiveCoverItemId = s.hiddenHomeLiveCoverItemIdState,
        myCardBounds = s.myCardBounds,
        homeLiveCardBounds = s.homeLiveCardBounds,
        searchCardBounds = s.searchCardBounds,
        activeLiveRoom = s.activeLiveRoomState,
        liveExitPrelude = s.liveExitPreludeState,
        liveVideoSurfaceVisible = s.liveVideoSurfaceVisibleState,
        livePlayerBounds = s.livePlayerBoundsState,
        liveRoomParentStack = s.liveRoomParentStackState,
        hiddenLiveRecommendationCoverItemId = s.hiddenLiveRecommendationCoverItemIdState,
        activeLiveEntryId = s.activeLiveEntryIdState,
        nextLiveEntryId = s.nextLiveEntryIdState,
        liveFirstFrameEntryId = s.liveFirstFrameEntryIdState,
        transitionToken = s.transitionTokenState,
        liveTransitionSession = s.liveTransitionSessionState,
        liveTransitionJob = s.liveTransitionJobState,
        livePageAlpha = s.livePageAlpha,
        liveRecommendationCardBounds = s.liveRecommendationCardBounds,
        rootPlayerOwnership = rootPlayerOwnershipState,
        transitionSession = s.transitionSessionState,
        articleTransitionSession = s.articleTransitionSessionState,
        prepareCardTransition = ::prepareCardTransition,
        closeSearchResultsAnimated = { closeSearchResultsAnimated(overlayTransitionContext) },
        animateToRootTab = ::animateToRootTab,
      )
    }

  // ── 屏幕共存计算 ───────────────────────────────────────────────
  val uiValues =
    computeAppRootUiValues(
      appState = appState,
      transitionPhase = transitionPhase,
      transitionSession = transitionSession,
      articleStack = articleStack,
      videoExitPrelude = videoExitPrelude,
      bangumiCardEnterPending = bangumiCardEnterPending,
      articleTransitionSession = articleTransitionSession,
      liveTransitionSession = liveTransitionSession,
      musicEntryInputLocked = musicEntryInputLocked,
      rootPageSwitchRequested = rootPageSwitchRequested,
      directHomeInProgress = directHomeInProgress,
      searchTransitionDirection = searchTransitionDirection,
      bangumiIndexTransitionDirection = bangumiIndexTransitionDirection,
      liveAreaIndex = s.liveAreaIndex,
      liveTransitionJob = liveTransitionJob,
      liveExitPrelude = liveExitPrelude,
      homeLivePreludeActive = homeLivePreludeActive,
      liveFullscreenTransitionActive = liveFullscreenTransitionActive,
      videoFullscreenTransitionActive = videoFullscreenTransitionActive,
      profileVideoTransitionActive = profileVideoTransitionActive,
      commentProfileTransition = commentProfileTransition,
      avatarProfileTransition = avatarProfileTransition,
      activeBangumiPage = activeBangumiPage,
      startupWarmupVisible = startupWarmupVisible,
      bangumiRootPageActive = bangumiRootPageActive,
    )
  val showFeed = uiValues.showFeed
  val showVideo = uiValues.showVideo
  val activeSession = uiValues.activeSession
  val activeArticleFrame = uiValues.activeArticleFrame
  val transitionVisualsActive = uiValues.transitionVisualsActive
  val waitingForFirstFrame = uiValues.waitingForFirstFrame
  val navigationLocked = uiValues.navigationLocked
  val liveWaitingForFirstFrame = uiValues.liveWaitingForFirstFrame
  val interactionTransitionActive = uiValues.interactionTransitionActive
  val preparingRootEnter = uiValues.preparingRootEnter
  val deferVideoAuxiliaryContent = uiValues.deferVideoAuxiliaryContent
  val deferVideoCommentContent = uiValues.deferVideoCommentContent
  val rootEnterSession = uiValues.rootEnterSession
  val profileEnterSession = uiValues.profileEnterSession
  val bangumiHomeTransitionSession = uiValues.bangumiHomeTransitionSession
  val bangumiDetailPlayerSuppressed = uiValues.bangumiDetailPlayerSuppressed
  val rootPlayerHostEnabled = uiValues.rootPlayerHostEnabled
  val searchBangumiSession = uiValues.searchBangumiSession
  val searchBangumiExitPrelude = uiValues.searchBangumiExitPrelude
  val searchBangumiSourceAboveVideo = uiValues.searchBangumiSourceAboveVideo
  val feedLayerAlpha = uiValues.feedLayerAlpha
  LaunchedEffect(
    controlMode,
    startupWarmupVisible,
    uiValues.showVideo,
    rootTab,
    showSearch,
    showSearchResults,
    showBangumiIndex,
    profileMid,
    activeLiveRoom,
    articleStack.size,
    showControlExitDialog,
    homeControlLevel,
    homeRecommendationMode,
  ) {
    val rootHomeReady =
      controlMode &&
        !startupWarmupVisible &&
        !showVideo &&
        rootTab == RootTab.HOME &&
        profileMid == null &&
        activeLiveRoom == null &&
        articleStack.isEmpty() &&
        !showSearch &&
        !showSearchResults &&
        !showBangumiIndex &&
        !showControlExitDialog &&
        homeControlLevel == HomeControlLevel.ROOT &&
        homeRecommendationMode == HomeRecommendationMode.NORMAL
    if (rootHomeReady) {
      withFrameNanos {}
      withFrameNanos {}
      runCatching { controlInitialFocusRequester.requestFocus() }
    }
  }
  LaunchedEffect(uiValues.rootPlayerHostEnabled) {
    if (!uiValues.rootPlayerHostEnabled) {
      playerViewHolder[0]?.view?.apply {
        animate().cancel()
        alpha = 1f
        updateVideoSurfaceAlpha(1f)
      }
      return@LaunchedEffect
    }
  }
    val feedCtx =
      AppRootFeedContext(
        context = context,
        scope = scope,
        controlMode = controlMode,
        controlInitialFocusRequester = s.controlInitialFocusRequester,
        feedGridState = feedGridState,
        searchGridState = searchGridState,
        rootPagerState = rootPagerState,
        liveAreaIndex = s.liveAreaIndex,
        overlayTransitionContext = overlayTransitionContext,
        liveTransitionContext = liveTransitionContext,
        feedViewModel = feedViewModel,
        authViewModel = authViewModel,
        playerViewModel = playerViewModel,
        myViewModel = myViewModel,
        searchViewModel = searchViewModel,
        settingsViewModel = settingsViewModel,
        bangumiRecommendationViewModel = bangumiRecommendationViewModel,
        bangumiExploreViewModel = bangumiExploreViewModel,
        watchLaterViewModel = watchLaterViewModel,
        onSearch = onSearch,
        onFeedRefresh = onFeedRefresh,
        onFeedPullRefresh = onFeedPullRefresh,
        userInfo = userInfo,
        appState = appState,
        myState = myState,
        searchState = searchState,
        watchLaterState = watchLaterState,
        settings = settings,
        authUserInfo = authUserInfo,
        profileIpAuthorized = profileIpAuthorized,
        bangumiRecommendationState = bangumiRecommendationState,
        feedState = feedState,
        rootPageSwitchInProgress = rootPageSwitchInProgress,
        searchBangumiSourceAboveVideo = searchBangumiSourceAboveVideo,
        rootEnterSession = rootEnterSession,
        bangumiRootPageActive = bangumiRootPageActive,
        showFeed = showFeed,
        feedLayerAlpha = feedLayerAlpha,
        activeArticleFrame = activeArticleFrame,
        showVideo = showVideo,
        navigationLocked = navigationLocked,
        feedCardBounds = s.feedCardBounds,
        popularCardBounds = s.popularCardBounds,
        dynamicCardBounds = s.dynamicCardBounds,
        homeLiveCardBounds = s.homeLiveCardBounds,
        homeDynamicArticleBounds = s.homeDynamicArticleBounds,
        myArticleBounds = s.myArticleBounds,
        searchArticleBounds = s.searchArticleBounds,
        searchCardBounds = s.searchCardBounds,
        myCardBounds = s.myCardBounds,
        myInteractionArticleMessageIds = s.myInteractionArticleMessageIds,
        myInteractionVideoMessageIds = s.myInteractionVideoMessageIds,
        profileState = s.profileState,
        homeControlSearchFocusRequestState = s.homeControlSearchFocusRequestState,
        homeControlFocusRestoreRequestState = s.homeControlFocusRestoreRequestState,
        homeControlLevelState = s.homeControlLevelState,
        bangumiControlSecondLevelRequestState = s.bangumiControlSecondLevelRequestState,
        bangumiControlFocusRestoreRequestState = s.bangumiControlFocusRestoreRequestState,
        bangumiControlLevelState = s.bangumiControlLevelState,
        myControllerState = s.myControllerState,
        hiddenMyCoverItemIdState = s.hiddenMyCoverItemIdState,
        searchBoundsState = s.searchBoundsState,
        showSearchResultsState = s.showSearchResultsState,
        searchTransitionDirectionState = s.searchTransitionDirectionState,
        showSearchState = s.showSearchState,
        searchOpenedFromControllerState = s.searchOpenedFromControllerState,
        hiddenSearchCoverItemIdState = s.hiddenSearchCoverItemIdState,
        bangumiIndexTransitionDirectionState = s.bangumiIndexTransitionDirectionState,
        hiddenHomeLiveCoverItemIdState = s.hiddenHomeLiveCoverItemIdState,
        showBangumiIndexState = s.showBangumiIndexState,
        hiddenMyArticleItemIdState = s.hiddenMyArticleItemIdState,
        hiddenRecommendationCoverItemIdState = s.hiddenRecommendationCoverItemIdState,
        hiddenSearchArticleItemIdState = s.hiddenSearchArticleItemIdState,
        hiddenHomeDynamicArticleItemIdState = s.hiddenHomeDynamicArticleItemIdState,
        activeLiveRoomState = s.activeLiveRoomState,
        activeLiveOriginState = s.activeLiveOriginState,
        liveAreaIndexFocusRestoreRequestState = s.liveAreaIndexFocusRestoreRequestState,
        transitionPhaseState = s.transitionPhaseState,
        activeBangumiPageState = s.activeBangumiPageState,
        directHomeInProgressState = s.directHomeInProgressState,
        dismissedFeedItemIdsState = s.dismissedFeedItemIdsState,
        homeDynamicDetailActiveState = s.homeDynamicDetailActiveState,
        musicEntryInputLockedState = s.musicEntryInputLockedState,
        rootTabState = s.rootTabState,
        hiddenHomeDynamicCoverItemIdState = s.hiddenHomeDynamicCoverItemIdState,
        bangumiPreviewTargetState = s.bangumiPreviewTargetState,
        hiddenFeedCoverItemIdState = s.hiddenFeedCoverItemIdState,
        bangumiStartupPreloadReadyState = s.bangumiStartupPreloadReadyState,
        homeLivePreludeActiveState = s.homeLivePreludeActiveState,
        bangumiPreviewMutedState = s.bangumiPreviewMutedState,
        bangumiCardEnterPendingState = s.bangumiCardEnterPendingState,
        hiddenBangumiRecommendationItemIdState = s.hiddenBangumiRecommendationItemIdState,
        rootPageSwitchRequestedState = s.rootPageSwitchRequestedState,
        homeRecommendationModeState = s.homeRecommendationModeState,
        startupWarmupFadeInProgressState = s.startupWarmupFadeInProgressState,
        hiddenPopularCoverItemIdState = s.hiddenPopularCoverItemIdState,
        homeControlSecondLevelRequestState = s.homeControlSecondLevelRequestState,
        startEnterVideoRef = { item: FeedItem, cardBounds: Rect?, origin: VideoOrigin, rootFeedScrollAnchor: FeedScrollAnchor?, sourceAnchorKey: String? ->
          startEnterVideo(item, cardBounds, origin, rootFeedScrollAnchor = rootFeedScrollAnchor, sourceAnchorKey = sourceAnchorKey)
        },
        startSearchBangumiRef = { card: SpaceContentCard, cardBounds: Rect, sourceIsBangumiExplorePoster: Boolean ->
          startSearchBangumi(card, cardBounds, sourceIsBangumiExplorePoster = sourceIsBangumiExplorePoster)
        },
        startEnterArticleRef = { article: ArticleItem, sourceBounds: Rect?, origin: ArticleOrigin ->
          startEnterArticle(article, sourceBounds, origin)
        },
        openInteractionTargetRef = { message: AccountMessage, sourceBounds: Rect ->
          openInteractionTarget(message, sourceBounds)
        },
        openCommentProfileRef = { mid: Long, comment: CommentItem, anchor: CommentProfileAnchor ->
          openCommentProfile(mid, comment, anchor)
        },
        startRootBangumiRef = { card: SpaceContentCard, item: FeedItem, cardBounds: Rect, pageOrigin: PageOrigin, videoOrigin: VideoOrigin, restoreEpisodeSelection: Boolean, initialSeason: BangumiSeason?, returnToSourceCover: Boolean ->
          startRootBangumi(card, item, cardBounds, pageOrigin, videoOrigin, restoreEpisodeSelection = restoreEpisodeSelection, initialSeason = initialSeason, returnToSourceCover = returnToSourceCover)
        },
        showVideoPreviewRef = { item: FeedItem, fromHomeFeed: Boolean -> showVideoPreview(item, fromHomeFeed = fromHomeFeed) },
        openAvatarProfileRef = { mid: Long, bounds: Rect, face: String?, name: String? ->
          openAvatarProfile(mid, bounds, face, name)
        },
        animateToRootTabRef = { tab: RootTab -> animateToRootTab(tab) },
        startHistoryBangumiRef = { card: SpaceContentCard, item: FeedItem, cardBounds: Rect ->
          startHistoryBangumi(card, item, cardBounds)
        },
      )
    val videoCtx =
      AppRootVideoContext(
        context = context,
        scope = scope,
        settings = settings,
        settingsViewModel = settingsViewModel,
        authViewModel = authViewModel,
        playerViewModel = playerViewModel,
        mainViewModel = mainViewModel,
        videoState = s.videoState,
        playerSession = playerSession,
        danmakuWindowController = s.danmakuWindowController,
        profileState = s.profileState,
        videoEntryCache = videoEntryCache,
        playerViewHolder = s.playerViewHolder,
        rootPlayerContent = rootPlayerContent,
        playerPositionProvider = playerPositionProvider,
        playerUiPositionProvider = playerUiPositionProvider,
        appState = appState,
        userInfo = userInfo,
        authUserInfo = authUserInfo,
        playerState = playerState,
        subtitleState = subtitleState,
        renderedVideoId = renderedVideoId,
        renderedVideoFrameCount = renderedVideoFrameCount,
        networkAvailable = networkAvailable,
        showVideo = showVideo,
        activeSession = activeSession,
        transitionVisualsActive = transitionVisualsActive,
        rootPlayerHostEnabled = rootPlayerHostEnabled,
        bangumiDetailPlayerSuppressed = bangumiDetailPlayerSuppressed,
        rootEnterSession = rootEnterSession,
        profileEnterSession = profileEnterSession,
        preparingRootEnter = preparingRootEnter,
        bangumiHomeTransitionSession = bangumiHomeTransitionSession,
        deferVideoCommentContent = deferVideoCommentContent,
        deferVideoAuxiliaryContent = deferVideoAuxiliaryContent,
        activeBangumiPageState = s.activeBangumiPageState,
        videoExitPreludeState = s.videoExitPreludeState,
        bangumiPosterBoundsState = s.bangumiPosterBoundsState,
        deferSearchBangumiPageCompositionState = s.deferSearchBangumiPageCompositionState,
        deferBangumiIndexPageCompositionState = s.deferBangumiIndexPageCompositionState,
        deferBangumiHomePageCompositionState = s.deferBangumiHomePageCompositionState,
        hiddenRecommendationCoverItemIdState = s.hiddenRecommendationCoverItemIdState,
        hiddenPlaybackEndRecommendationCoverItemIdState = s.hiddenPlaybackEndRecommendationCoverItemIdState,
        commentImagePreviewActiveState = s.commentImagePreviewActiveState,
        videoFullscreenTransitionActiveState = s.videoFullscreenTransitionActiveState,
        videoPageDataReadyIdState = s.videoPageDataReadyIdState,
        directHomeInProgressState = s.directHomeInProgressState,
        playerBoundsState = s.playerBoundsState,
        showEmbeddedCoverState = s.showEmbeddedCoverState,
        rootPlayerOwnershipState = rootPlayerOwnershipState,
        videoStackState = s.videoStackState,
        articleSuspendedVideoState = s.articleSuspendedVideoState,
        pendingVideoCommentTargetState = s.pendingVideoCommentTargetState,
        hiddenVideoCommentArticleItemIdState = s.hiddenVideoCommentArticleItemIdState,
        transitionPhaseState = s.transitionPhaseState,
        transitionSessionState = s.transitionSessionState,
        playerActivationIdState = s.playerActivationIdState,
        profileLayerSuppressedState = s.profileLayerSuppressedState,
        directHomeAlpha = s.directHomeAlpha,
        reverseActiveEnterRef = { reverseActiveEnter() },
        cancelPreparingProfileVideoRef = { cancelPreparingProfileVideo() },
        cancelPreparingRootEnterRef = { cancelPreparingRootEnter() },
        startExitBangumiRef = { startExitBangumi() },
        startExitVideoToProfileRef = { startExitVideoToProfile() },
        startBackToPreviousVideoRef = { startBackToPreviousVideo() },
        startExitVideoRef = { startExitVideo() },
        returnDirectlyHomeRef = { returnDirectlyHome() },
        startRecommendedVideoRef = { current: FeedItem, recommendation: FeedItem, bounds: Rect, returnBounds: Rect?, fromPlaybackEnd: Boolean ->
          startRecommendedVideo(current, recommendation, bounds, returnBounds, fromPlaybackEnd)
        },
        selectCollectionEpisodeRef = { episode: FeedItem -> selectCollectionEpisode(episode) },
        showVideoPreviewRef = { item: FeedItem -> showVideoPreview(item) },
        startEnterArticleRef = { article: ArticleItem, sourceBounds: Rect?, origin: ArticleOrigin ->
          startEnterArticle(article, sourceBounds, origin)
        },
        cacheEntryRef = { entry: VideoPageEntry -> cacheEntry(entry) },
        snapshotEntryRef = { item: FeedItem -> snapshotEntry(item) },
        currentPreferredResolutionModeRef = { currentPreferredResolutionMode() },
        openAvatarProfileRef = { mid: Long, bounds: Rect, face: String?, name: String? ->
          openAvatarProfile(mid, bounds, face, name)
        },
        selectFollowingGroupRef = { mid: Long, groupId: Long -> selectFollowingGroup(mid, groupId) },
        unfollowRef = { mid: Long -> unfollow(mid) },
        openCommentProfileRef = { mid: Long, comment: CommentItem, anchor: CommentProfileAnchor ->
          openCommentProfile(mid, comment, anchor)
        },
        setTemporarySpeedBoostRef = { active: Boolean -> setTemporarySpeedBoost(active) },
        setPlaybackSpeedRef = { speed: Float -> setPlaybackSpeed(speed) },
        commitSeekRef = { targetMs: Long -> commitSeek(targetMs) },
        previewSeekRef = { targetMs: Long -> previewSeek(targetMs) },
        cancelSeekPreviewRef = { cancelSeekPreview() },
        selectVideoPageRef = { page: VideoPage -> selectVideoPage(page) },
        selectCommentSortRef = { sort: CommentSort -> selectCommentSort(sort) },
        loadFollowingGroupsRef = { loadFollowingGroups() },
        loadMentionSuggestionsRef = { query: String -> loadMentionSuggestions(query) },
        selectBangumiEpisodeRef = { episode: BangumiEpisode -> selectBangumiEpisode(episode) },
        selectBangumiSeasonRef = { seasonId: Long -> selectBangumiSeason(seasonId) },
        toggleBangumiFollowRef = { toggleBangumiFollow() },
        postBangumiShortReviewRef = { score: Int, content: String -> postBangumiShortReview(score, content) },
      )
  AppRootBoxContent(
    feedCtx = feedCtx,
    videoCtx = videoCtx,
    articleCtx = articleCtx,
    showFeed = showFeed,
    showBangumiIndex = showBangumiIndex,
    searchBangumiSourceAboveVideo = searchBangumiSourceAboveVideo,
    feedLayerAlpha = feedLayerAlpha,
    bangumiIndexState = bangumiIndexState,
    bangumiIndexGridState = bangumiIndexGridState,
    bangumiIndexViewModel = bangumiIndexViewModel,
    bangumiControlLevelState = s.bangumiControlLevelState,
    bangumiControlFocusRestoreRequestState = s.bangumiControlFocusRestoreRequestState,
    overlayTransitionContext = overlayTransitionContext,
    bangumiIndexCardBounds = s.bangumiIndexCardBounds,
    hiddenBangumiIndexItemId = hiddenBangumiIndexItemId,
    appState = appState,
    transitionPhase = transitionPhase,
    liveAreaIndex = s.liveAreaIndex,
    liveHomeState = liveHomeState,
    liveHomeViewModel = liveHomeViewModel,
    startRootBangumiRef = { card: SpaceContentCard, item: FeedItem, cardBounds: Rect, pageOrigin: PageOrigin, videoOrigin: VideoOrigin, restoreEpisodeSelection: Boolean ->
      startRootBangumi(card, item, cardBounds, pageOrigin, videoOrigin, restoreEpisodeSelection = restoreEpisodeSelection)
    },
    settings = settings,
    authUserInfo = authUserInfo,
    profileStack = profileStack,
    profileLayerSuppressed = profileLayerSuppressed,
    activeLiveRoom = activeLiveRoom,
    activeLiveEntryId = activeLiveEntryId,
    liveTransitionSession = liveTransitionSession,
    liveTransitionJob = liveTransitionJob,
    liveExitPrelude = liveExitPrelude,
    liveVideoSurfaceVisibleState = s.liveVideoSurfaceVisibleState,
    liveFullscreenTransitionActiveState = s.liveFullscreenTransitionActiveState,
    liveFirstFrameEntryIdState = s.liveFirstFrameEntryIdState,
    livePlayerBoundsState = s.livePlayerBoundsState,
    liveRoomParentStack = liveRoomParentStack,
    hiddenLiveRecommendationCoverItemIdState = s.hiddenLiveRecommendationCoverItemIdState,
    liveRoomStateHolder = liveRoomStateHolder,
    liveRecommendationCardBounds = s.liveRecommendationCardBounds,
    livePageAlpha = s.livePageAlpha,
    liveTransitionContext = liveTransitionContext,
    renderedVideoId = renderedVideoId,
    renderedVideoFrameCount = renderedVideoFrameCount,
    playerViewModel = playerViewModel,
    authViewModel = authViewModel,
    settingsViewModel = settingsViewModel,
    rootPlayerContent = rootPlayerContent,
    openAvatarProfileRef = { mid: Long, bounds: Rect, face: String?, name: String? ->
      openAvatarProfile(mid, bounds, face, name)
    },
    rootPlayerHostEnabled = rootPlayerHostEnabled,
    rootPlayerOwnershipState = rootPlayerOwnershipState,
    bangumiPreviewTargetState = s.bangumiPreviewTargetState,
    showVideo = showVideo,
    profileStackState = s.profileStackState,
    hiddenArticleVideoCoverItemIdState = s.hiddenArticleVideoCoverItemIdState,
    articleVideoBounds = s.articleVideoBounds,
    videoState = s.videoState,
    openProfileRef = { mid: Long -> openProfile(mid) },
    openArticleCommentProfileRef = { mid: Long, comment: CommentItem, anchor: CommentProfileAnchor ->
      openArticleCommentProfile(mid, comment, anchor)
    },
    returnDirectlyHomeRef = { returnDirectlyHome() },
    showVideoPreviewRef = { item: FeedItem -> showVideoPreview(item) },
    loadMentionSuggestionsRef = { query: String -> loadMentionSuggestions(query) },
  )
  AppRootUiOverlays(
    profileCtx = profileCtx,
    profileState = s.profileState,
    profileStateHolder = profileStateHolder,
    authViewModel = authViewModel,
    profileMessageViewModel = profileMessageViewModel,
    profileMessageState = profileMessageState,
    hiddenProfileCoverItemIdState = s.hiddenProfileCoverItemIdState,
    hiddenMyCoverItemIdState = s.hiddenMyCoverItemIdState,
    profileBangumiReturnRequestState = s.profileBangumiReturnRequestState,
    hiddenProfileArticleItemIdState = s.hiddenProfileArticleItemIdState,
    profileVideoTransitionActiveState = s.profileVideoTransitionActiveState,
    transitionSessionState = s.transitionSessionState,
    activeSession = activeSession,
    profileArticleBounds = s.profileArticleBounds,
    profileCardBounds = s.profileCardBounds,
    showVideoPreviewRef = { item: FeedItem -> showVideoPreview(item) },
    cancelPreparingProfileVideoRef = { cancelPreparingProfileVideo() },
    reverseActiveEnterRef = { reverseActiveEnter() },
    startProfileBangumiRef = { profileEntryId: Long, card: SpaceContentCard, cardBounds: Rect ->
      startProfileBangumi(profileEntryId, card, cardBounds)
    },
    startEnterArticleRef = { article: ArticleItem, sourceBounds: Rect?, origin: ArticleOrigin ->
      startEnterArticle(article, sourceBounds, origin)
    },
    startEnterLiveRef = { room: LiveSearchRoom, bounds: Rect ->
      startEnterLive(liveTransitionContext, room, bounds, PageOrigin.My)
    },
    loadFollowingGroupsRef = { loadFollowingGroups() },
    startProfileVideoRef = { profileEntryId: Long, item: FeedItem, cardBounds: Rect ->
      startProfileVideo(profileEntryId, item, cardBounds)
    },
    openInteractionTargetRef = { message: AccountMessage, sourceBounds: Rect, profileEntryId: Long? ->
      openInteractionTarget(message, sourceBounds, profileEntryId)
    },
    bangumiHomeTransitionSession = bangumiHomeTransitionSession,
    liveTransitionSession = liveTransitionSession,
    liveExitPrelude = liveExitPrelude,
    isHdrPlayback = isHdrPlayback,
    activeBangumiPage = activeBangumiPage,
    articleTransitionSession = articleTransitionSession,
    videoExitPrelude = videoExitPrelude,
    bangumiSeasonExitFadeAlpha = s.bangumiSeasonExitFadeAlpha,
    searchTransitionDirectionState = s.searchTransitionDirectionState,
    searchTransitionSourceBoundsState = s.searchTransitionSourceBoundsState,
    searchTransitionProgress = s.searchTransitionProgress,
    searchTransitionMaskAlpha = s.searchTransitionMaskAlpha,
    searchTransitionScrimAlpha = s.searchTransitionScrimAlpha,
    bangumiIndexTransitionDirectionState = s.bangumiIndexTransitionDirectionState,
    bangumiIndexTransitionSourceBoundsState = s.bangumiIndexTransitionSourceBoundsState,
    bangumiIndexTransitionProgress = s.bangumiIndexTransitionProgress,
    bangumiIndexTransitionMaskAlpha = s.bangumiIndexTransitionMaskAlpha,
    bangumiIndexTransitionScrimAlpha = s.bangumiIndexTransitionScrimAlpha,
    liveAreaIndex = s.liveAreaIndex,
    appState = appState,
    showSearchState = s.showSearchState,
    searchState = searchState,
    searchBoundsState = s.searchBoundsState,
    searchViewModel = searchViewModel,
    overlayTransitionContext = overlayTransitionContext,
    focusManager = focusManager,
    settings = settings,
    previewItemState = s.previewItemState,
    previewInfoState = s.previewInfoState,
    previewFromHomeFeedState = s.previewFromHomeFeedState,
    dismissedFeedItemIdsState = s.dismissedFeedItemIdsState,
    authUserInfo = authUserInfo,
    watchLaterViewModel = watchLaterViewModel,
    watchLaterState = watchLaterState,
    riskChallenge = riskChallenge,
    showControlExitDialogState = s.showControlExitDialogState,
    onExitRequested = onExitRequested,
    startupWarmupVisible = startupWarmupVisible,
    startupWarmupAlpha = startupWarmupAlpha,
    interactionTransitionActive = interactionTransitionActive,
    transitionToken = transitionToken,
    transitionPhase = transitionPhase,
    liveFullscreenTransitionActive = liveFullscreenTransitionActive,
    videoFullscreenTransitionActive = videoFullscreenTransitionActive,
  )
  }
  tailBody()
}

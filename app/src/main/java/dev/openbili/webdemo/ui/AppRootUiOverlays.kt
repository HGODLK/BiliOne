package dev.openbili.webdemo.ui

/**
 * 根覆盖层：资料页、转场效果、搜索/番剧索引/直播区转场遮罩、搜索屏、启动遮罩与
 * 控制器退出弹窗，全部绘制在根 Box 之上（顶层）。
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.openbili.webdemo.AppUiState
import dev.openbili.webdemo.AuthViewModel
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.RiskChallenge
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.article.ArticleOrigin
import dev.openbili.webdemo.article.ArticleTransitionOverlay
import dev.openbili.webdemo.article.ArticleTransitionSession
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.my.contains
import dev.openbili.webdemo.my.MyUiState
import dev.openbili.webdemo.my.MyViewModel
import dev.openbili.webdemo.my.WatchLaterUiState
import dev.openbili.webdemo.my.WatchLaterViewModel
import dev.openbili.webdemo.search.SearchUiState
import dev.openbili.webdemo.search.SearchScreen
import dev.openbili.webdemo.search.SearchViewModel
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.video.VideoInfoTile

@Composable
internal fun AppRootUiOverlays(
  profileCtx: AppRootProfileContext,
  profileState: AppRootProfileState,
  profileStateHolder: SaveableStateHolder,
  authViewModel: AuthViewModel,
  profileMessageViewModel: MyViewModel,
  profileMessageState: MyUiState,
  hiddenProfileCoverItemIdState: MutableState<String?>,
  hiddenMyCoverItemIdState: MutableState<String?>,
  profileBangumiReturnRequestState: MutableState<ProfileBangumiReturnRequest?>,
  hiddenProfileArticleItemIdState: MutableState<String?>,
  profileVideoTransitionActiveState: MutableState<Boolean>,
  transitionSessionState: MutableState<CardTransitionSession?>,
  activeSession: CardTransitionSession?,
  profileArticleBounds: SnapshotStateMap<String, Rect>,
  profileCardBounds: SnapshotStateMap<ProfileVideoKey, Rect>,
  showVideoPreviewRef: (FeedItem) -> Unit,
  cancelPreparingProfileVideoRef: () -> Unit,
  reverseActiveEnterRef: () -> Unit,
  startProfileBangumiRef: (Long, SpaceContentCard, Rect) -> Unit,
  startEnterArticleRef: (ArticleItem, Rect?, ArticleOrigin) -> Unit,
  startEnterLiveRef: (LiveSearchRoom, Rect) -> Unit,
  loadFollowingGroupsRef: () -> Unit,
  startProfileVideoRef: (Long, FeedItem, Rect) -> Unit,
  openInteractionTargetRef: (AccountMessage, Rect, Long?) -> Unit,
  bangumiHomeTransitionSession: CardTransitionSession?,
  liveTransitionSession: CardTransitionSession?,
  liveExitPrelude: VideoExitPrelude?,
  isHdrPlayback: Boolean,
  activeBangumiPage: ActiveBangumiPage?,
  articleTransitionSession: ArticleTransitionSession?,
  videoExitPrelude: VideoExitPrelude?,
  bangumiSeasonExitFadeAlpha: Animatable<Float, AnimationVector1D>,
  searchTransitionDirectionState: MutableState<SearchTransitionDirection?>,
  searchTransitionSourceBoundsState: MutableState<Rect>,
  searchTransitionProgress: Animatable<Float, AnimationVector1D>,
  searchTransitionMaskAlpha: Animatable<Float, AnimationVector1D>,
  searchTransitionScrimAlpha: Animatable<Float, AnimationVector1D>,
  bangumiIndexTransitionDirectionState: MutableState<SearchTransitionDirection?>,
  bangumiIndexTransitionSourceBoundsState: MutableState<Rect>,
  bangumiIndexTransitionProgress: Animatable<Float, AnimationVector1D>,
  bangumiIndexTransitionMaskAlpha: Animatable<Float, AnimationVector1D>,
  bangumiIndexTransitionScrimAlpha: Animatable<Float, AnimationVector1D>,
  liveAreaIndex: LiveAreaIndexTransitionState,
  appState: AppUiState,
  showSearchState: MutableState<Boolean>,
  searchState: SearchUiState,
  searchBoundsState: MutableState<Rect>,
  searchViewModel: SearchViewModel,
  overlayTransitionContext: AppRootOverlayContext,
  focusManager: FocusManager,
  settings: AppSettings,
  previewItemState: MutableState<FeedItem?>,
  previewInfoState: MutableState<VideoInfo?>,
  previewFromHomeFeedState: MutableState<Boolean>,
  dismissedFeedItemIdsState: MutableState<Set<String>>,
  authUserInfo: UserInfo,
  watchLaterViewModel: WatchLaterViewModel,
  watchLaterState: WatchLaterUiState,
  riskChallenge: RiskChallenge?,
  showControlExitDialogState: MutableState<Boolean>,
  onExitRequested: () -> Unit,
  startupWarmupVisible: Boolean,
  startupWarmupAlpha: Animatable<Float, AnimationVector1D>,
  interactionTransitionActive: Boolean,
  transitionToken: Long,
  transitionPhase: TransitionPhase,
  liveFullscreenTransitionActive: Boolean,
  videoFullscreenTransitionActive: Boolean,
) {
  val controllerFullPlayback =
    settings.alwaysControllerPlaybackPage ||
      (LocalControlMode.current && !settings.controllerTouchPlaybackPage)
  fun showVideoPreview(item: FeedItem) = showVideoPreviewRef(item)
  fun cancelPreparingProfileVideo() = cancelPreparingProfileVideoRef()
  fun reverseActiveEnter() = reverseActiveEnterRef()
  fun startProfileBangumi(profileEntryId: Long, card: SpaceContentCard, cardBounds: Rect) =
    startProfileBangumiRef(profileEntryId, card, cardBounds)
  fun startEnterArticle(article: ArticleItem, sourceBounds: Rect?, origin: ArticleOrigin) =
    startEnterArticleRef(article, sourceBounds, origin)
  fun loadFollowingGroups() = loadFollowingGroupsRef()
  fun startProfileVideo(profileEntryId: Long, item: FeedItem, cardBounds: Rect) =
    startProfileVideoRef(profileEntryId, item, cardBounds)
  fun openInteractionTarget(message: AccountMessage, sourceBounds: Rect, profileEntryId: Long?) =
    openInteractionTargetRef(message, sourceBounds, profileEntryId)
  var searchTransitionDirection by searchTransitionDirectionState
  var searchTransitionSourceBounds by searchTransitionSourceBoundsState
  var bangumiIndexTransitionDirection by bangumiIndexTransitionDirectionState
  var bangumiIndexTransitionSourceBounds by bangumiIndexTransitionSourceBoundsState
  var showSearch by showSearchState
  var searchBounds by searchBoundsState
  var previewItem by previewItemState
  var previewInfo by previewInfoState
  var previewFromHomeFeed by previewFromHomeFeedState
  var dismissedFeedItemIds by dismissedFeedItemIdsState
  var showControlExitDialog by showControlExitDialogState

AppRootProfileOverlay(
  ctx = profileCtx,
  profileState = profileState,
  profileStateHolder = profileStateHolder,
  authViewModel = authViewModel,
  profileMessageViewModel = profileMessageViewModel,
  profileMessageState = profileMessageState,
  hiddenProfileCoverItemIdState = hiddenProfileCoverItemIdState,
  hiddenMyCoverItemIdState = hiddenMyCoverItemIdState,
  profileBangumiReturnRequestState = profileBangumiReturnRequestState,
  hiddenProfileArticleItemIdState = hiddenProfileArticleItemIdState,
  profileVideoTransitionActiveState = profileVideoTransitionActiveState,
  transitionSessionState = transitionSessionState,
  activeSession = activeSession,
  profileArticleBounds = profileArticleBounds,
  profileCardBounds = profileCardBounds,
  showVideoPreviewRef = { item: FeedItem -> showVideoPreview(item) },
  cancelPreparingProfileVideoRef = { cancelPreparingProfileVideo() },
  reverseActiveEnterRef = { reverseActiveEnter() },
  startProfileBangumiRef = { profileEntryId: Long, card: SpaceContentCard, cardBounds: Rect ->
    startProfileBangumi(profileEntryId, card, cardBounds)
  },
  startEnterArticleRef = { article: ArticleItem, sourceBounds: Rect?, origin: ArticleOrigin ->
    startEnterArticle(article, sourceBounds, origin)
  },
  startEnterLiveRef = startEnterLiveRef,
  loadFollowingGroupsRef = { loadFollowingGroups() },
  startProfileVideoRef = { profileEntryId: Long, item: FeedItem, cardBounds: Rect ->
    startProfileVideo(profileEntryId, item, cardBounds)
  },
  openInteractionTargetRef = { message: AccountMessage, sourceBounds: Rect, profileEntryId: Long? ->
    openInteractionTarget(message, sourceBounds, profileEntryId)
  },
)
// 根、播放器与保留资料页层之间只保留一份共享卡片前景。若在资料页层再画一份，
// 该层移到播放器之后时会出现一帧交接闪屏。HDR 播放对这个前景套用相同的焦点压暗，
// 让海报无法绕过非播放器内容蒙版；退出转场期间跳过压暗，因为 leaveHdrPlaybackPage()
// 那时已释放覆盖层。
bangumiHomeTransitionSession?.let { session ->
  Box(
    Modifier.fillMaxSize()
      .zIndex(1.5f)
      .graphicsLayer { alpha = session.themeScrimAlpha.value.coerceIn(0f, 1f) }
      .background(MaterialTheme.colorScheme.background)
  )
}
liveTransitionSession
  ?.takeIf {
    it.phase != SessionPhase.PREPARING &&
      it.phase != SessionPhase.CANCELLED &&
      it.phase != SessionPhase.COMPLETED
  }
  ?.let { session ->
    CardTransitionOverlay(
      item = session.item,
      startBounds = session.startBounds,
      endBounds = session.endBounds,
      progress = { session.progress.value },
      overlayAlpha = { session.coverAlpha.value },
      fillViewportCrop = controllerFullPlayback,
      preserveAspectRatio = controllerFullPlayback,
      modifier = Modifier.zIndex(4f),
      bitmap = session.transitionBitmap,
    )
  }
liveExitPrelude
  ?.takeIf {
    it.playerBounds != Rect.Zero && it.playerBounds.width > 0f && it.playerBounds.height > 0f
  }
  ?.let { prelude ->
    CardTransitionOverlay(
      item = prelude.item,
      startBounds = prelude.playerBounds,
      endBounds = prelude.playerBounds,
      progress = { if (controllerFullPlayback) 1f else 0f },
      overlayAlpha = { prelude.coverAlpha.value },
      fillViewportCrop = controllerFullPlayback,
      preserveAspectRatio = controllerFullPlayback,
      bitmap = prelude.transitionBitmap,
      modifier = Modifier.zIndex(5f),
    )
  }
activeSession
  ?.takeIf {
    (!it.reusePlayerSurface || activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome) &&
      shouldDisplayCardTransitionOverlay(it.kind, it.phase)
  }
  ?.let { session ->
    val isExit =
      session.kind == TransitionKind.EXIT_ROOT ||
        session.kind == TransitionKind.EXIT_RECOMMENDATION ||
        session.kind == TransitionKind.EXIT_PROFILE
    CardTransitionOverlay(
      item = session.item,
      startBounds = session.startBounds,
      endBounds = session.endBounds,
      progress = { session.progress.value },
      overlayAlpha = { session.coverAlpha.value },
      fitCover = session.fitCover,
      // 控制器页的退出也要从整屏封面开始，和退出前奏保持同一几何目标；否则
      // 竖版番剧会在前奏交接时落回触屏页的播放器/海报动画。
      fillViewportCrop = controllerFullPlayback,
      preserveAspectRatio = controllerFullPlayback || session.fitCover,
      coverDimAlpha = { if (session.fitCover && isHdrPlayback && !isExit) .48f else 0f },
      // 恢复的资料页在返回期间刻意保持在播放器之上：共享封面必须在保留层之上，
      // 否则它 p=1 -> p=0 的飞行不可见。
      modifier = Modifier.zIndex(2f),
      bitmap = session.transitionBitmap,
      allowAsyncImageFallback = activeBangumiPage?.sourceOrigin != PageOrigin.BangumiHome,
    )
  }
articleTransitionSession?.let { session -> ArticleTransitionOverlay(session) }
// 退出前奏必须位于视频页与独立保留的资料页之上：这让资料页返回复用与根返回相同
// 的可见交接——静止封面在前、目的地背景其次、飞行封面最后。
videoExitPrelude
  ?.takeIf {
    it.playerBounds != Rect.Zero && it.playerBounds.width > 0f && it.playerBounds.height > 0f
  }
  ?.let { prelude ->
    CardTransitionOverlay(
      item = prelude.item,
      startBounds = prelude.playerBounds,
      endBounds = prelude.playerBounds,
      // 竖版番剧的触屏目标位于页面左下方；控制器页的静止退出封面必须直接
      // 使用整屏终点，避免它在正式飞行接管前闪现一帧触屏海报。
      progress = { if (controllerFullPlayback) 1f else 0f },
      overlayAlpha = { prelude.coverAlpha.value },
      fitCover = prelude.fitCover,
      fillViewportCrop = controllerFullPlayback,
      preserveAspectRatio = controllerFullPlayback || prelude.fitCover,
      bitmap = prelude.transitionBitmap,
      modifier = Modifier.zIndex(3f),
      allowAsyncImageFallback = activeBangumiPage?.sourceOrigin != PageOrigin.BangumiHome,
    )
  }
if (bangumiSeasonExitFadeAlpha.value > .001f) {
  Box(
    Modifier.fillMaxSize()
      .zIndex(240f)
      .background(Color.Black.copy(alpha = bangumiSeasonExitFadeAlpha.value))
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) awaitPointerEvent().changes.forEach { it.consume() }
        }
      }
  )
}
if (searchTransitionDirection != null && !appState.isVideoScreen) {
  ExpandingPageTransitionOverlay(
    sourceBounds = searchTransitionSourceBounds,
    progress = { searchTransitionProgress.value },
    overlayAlpha = { searchTransitionMaskAlpha.value },
    scrimAlpha = { searchTransitionScrimAlpha.value },
  )
}
if (bangumiIndexTransitionDirection != null && !appState.isVideoScreen) {
  ExpandingPageTransitionOverlay(
    sourceBounds = bangumiIndexTransitionSourceBounds,
    progress = { bangumiIndexTransitionProgress.value },
    overlayAlpha = { bangumiIndexTransitionMaskAlpha.value },
    scrimAlpha = { bangumiIndexTransitionScrimAlpha.value },
    sourceCornerRadius = 28.dp,
  )
}
if (liveAreaIndex.direction != null && !appState.isVideoScreen) {
  ExpandingPageTransitionOverlay(
    sourceBounds = liveAreaIndex.sourceBounds,
    progress = { liveAreaIndex.progress.value },
    overlayAlpha = { liveAreaIndex.maskAlpha.value },
    scrimAlpha = { liveAreaIndex.scrimAlpha.value },
    sourceCornerRadius = 28.dp,
  )
}
if (showSearch && !appState.isVideoScreen) {
  SearchScreen(
    state = searchState,
    searchBounds = searchBounds,
    onQuery = searchViewModel::setQuery,
    onSearch = { keyword -> openSearchResultsAnimated(overlayTransitionContext, keyword) },
    onClearHistory = searchViewModel::clearHistory,
    onBack = {
      focusManager.clearFocus(force = true)
      showSearch = false
      if (!settings.retainLastSearchQuery) searchViewModel.clearEntry()
      requestHomeSearchFocus(overlayTransitionContext)
    },
    onDismiss = {
      focusManager.clearFocus(force = true)
      showSearch = false
      if (!settings.retainLastSearchQuery) searchViewModel.clearEntry()
      requestHomeSearchFocus(overlayTransitionContext)
    },
    reduceMotion = settings.reduceMotion,
  )
}
previewItem?.let { item ->
  VideoInfoTile(
    item = item,
    info = previewInfo,
    onlineViewerText = null,
    description = previewInfo?.desc.orEmpty(),
    onDismiss = {
      previewItem = null
      previewInfo = null
      previewFromHomeFeed = false
    },
    onWatchLaterClick = {
      val aid = previewInfo?.aid
      previewItem = null
      previewInfo = null
      previewFromHomeFeed = false
      if (authUserInfo.isLogin) watchLaterViewModel.toggle(item, aid)
      else authViewModel.startLogin()
    },
    watchLaterAdded = watchLaterState.contains(item, previewInfo?.aid),
    watchLaterBusy = watchLaterState.loading || item.id in watchLaterState.busyVideoIds,
    onNotInterested =
      if (previewFromHomeFeed) {
        {
          dismissedFeedItemIds = dismissedFeedItemIds + item.id
          previewItem = null
          previewInfo = null
          previewFromHomeFeed = false
        }
      } else null,
    onNotInterestedUploader =
      if (previewFromHomeFeed) {
        {
          dismissedFeedItemIds = dismissedFeedItemIds + item.id
          previewItem = null
          previewInfo = null
          previewFromHomeFeed = false
        }
      } else null,
  )
}
riskChallenge?.let { challenge ->
  RiskControlDialog(
    challenge = challenge,
    onVerified = {},
  )
}
if (showControlExitDialog) {
  ControlExitDialog(
    onDismiss = { showControlExitDialog = false },
    onExit = {
      showControlExitDialog = false
      onExitRequested()
    },
  )
}
if (startupWarmupVisible) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .zIndex(1_000f)
        .graphicsLayer { alpha = startupWarmupAlpha.value }
        .background(Color(0xFFFFF9F8))
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              awaitPointerEvent().changes.forEach { it.consume() }
            }
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    BiliOneStartupAnimation(
      reduceMotion = settings.reduceMotion,
      customImageUri = settings.startupMaskUri,
    )
  }
}
if (interactionTransitionActive) {
  Box(
    Modifier.fillMaxSize().zIndex(320f).pointerInput(
      transitionToken,
      transitionPhase,
      searchTransitionDirection,
      bangumiIndexTransitionDirection,
      liveFullscreenTransitionActive,
      videoFullscreenTransitionActive,
    ) {
      awaitPointerEventScope {
        while (true) {
          awaitPointerEvent().changes.forEach { it.consume() }
        }
      }
    }
  )
}
}

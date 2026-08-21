package dev.openbili.webdemo.video

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.openbili.webdemo.PlayerSubtitleState
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.BiliEmotePackage
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.CommentNavigationTarget
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.MentionSuggestion
import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.api.PremiumAudioMode
import dev.openbili.webdemo.api.VideoEngagement
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedImageLoadMode
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import dev.openbili.webdemo.feed.rememberListFeedImageLoadPolicy
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.LocalControlFocusVisible
import dev.openbili.webdemo.ui.StableBoundsTracker
import dev.openbili.webdemo.ui.TransitionPreparationBarrier
import dev.openbili.webdemo.ui.TransitionReadySignal
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.ui.controllerInteractionActive
import dev.openbili.webdemo.ui.controlFocusOutline
import dev.openbili.webdemo.ui.hasUsableSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal fun commentCanBeDeletedBy(
  currentAccountMid: Long,
  uploaderMid: Long,
  commentAuthorMid: Long,
): Boolean =
  currentAccountMid > 0L &&
    (commentAuthorMid == currentAccountMid || uploaderMid == currentAccountMid)

@Composable
internal fun VideoContent(
  item: FeedItem,
  videoInfo: VideoInfo?,
  videoEngagement: VideoEngagement,
  favoriteFolders: List<FavoriteFolder>,
  favoriteFoldersLoading: Boolean,
  playData: PlayUrlData,
  danmaku: List<DanmakuItem>,
  danmakuPaused: Boolean,
  commentItems: List<CommentItem>,
  commentTotalCount: Long,
  commentHasMore: Boolean,
  commentsLoading: Boolean,
  commentSort: CommentSort,
  commentsRefreshing: Boolean,
  pageContentLoading: Boolean,
  pageForegroundColor: Color = MaterialTheme.colorScheme.onBackground,
  glassBackdrop: PlaybackPageGlassBackdrop = PlaybackPageGlassBackdrop(),
  deferAuxiliaryContent: Boolean = false,
  deferCommentContent: Boolean = deferAuxiliaryContent,
  currentAccountMid: Long,
  hiddenCommentAvatarRpid: Long?,
  commentNavigationTarget: CommentNavigationTarget?,
  replyRoot: CommentItem?,
  replyItems: List<CommentItem>,
  replyHasMore: Boolean,
  repliesLoading: Boolean,
  emotes: List<BiliEmote>,
  emotePackages: List<BiliEmotePackage>,
  mentionSuggestions: List<MentionSuggestion>,
  mentionSuggestionsLoading: Boolean,
  recommendations: List<FeedItem>,
  hiddenRecommendationCoverItemId: String?,
  hiddenPlaybackEndRecommendationCoverItemId: String?,
  recommendationListState: LazyListState,
  recommendationReturnBounds: MutableMap<String, Rect>,
  commentMediaBounds: MutableMap<String, Rect> = mutableMapOf(),
  commentListState: LazyListState,
  commentNavigationSessionId: Long,
  commentChromeState: CommentChromeState,
  currentPositionMs: () -> Long,
  durationMs: Long,
  playerPositionProvider: () -> Long,
  isPlaying: Boolean,
  isBuffering: Boolean,
  showLoadingCover: Boolean,
  dimLoadingCover: Boolean,
  playbackEnded: Boolean,
  playbackSpeed: Float,
  playbackEndRevealAlpha: () -> Float,
  embeddedPlayerHandoffAlpha: () -> Float,
  autoNextSeconds: Int,
  autoNextTriggered: Boolean,
  autoNextHandoffProgress: () -> Float,
  onAutoNext: () -> Unit,
  nextPlaybackTarget: PlaybackContinuationTarget?,
  showDanmaku: Boolean,
  danmakuComposerEnabled: Boolean,
  onRecommendationClick: (FeedItem, Rect, Rect?, Boolean) -> Unit,
  onRecommendationLongClick: (FeedItem) -> Unit,
  onArticleClick: (ArticleItem, Rect) -> Unit,
  hiddenLinkedArticleItemId: String?,
  onLoadMoreComments: () -> Unit,
  onRefreshComments: () -> Unit,
  onCommentSort: (CommentSort) -> Unit,
  onPostComment: (String, Uri?) -> Unit,
  onPostReply: (CommentItem, CommentItem, String, Uri?) -> Unit,
  onLikeComment: (CommentItem) -> Unit,
  onDeleteComment: (CommentItem) -> Unit,
  onToggleCommentPin: (CommentItem) -> Unit = {},
  onLikeVideo: (Boolean) -> Unit,
  onCoinVideo: (Int, Boolean) -> Unit,
  onFavoriteVideo: (List<Long>, List<Long>) -> Unit,
  onLoadFavoriteFolders: () -> Unit,
  onLogin: () -> Unit,
  onOpenReplies: (CommentItem) -> Unit,
  onLoadMoreReplies: () -> Unit,
  onRefreshReplies: () -> Unit,
  onDismissReplies: () -> Unit,
  onCommentNavigationConsumed: () -> Unit,
  onProfileClick: (Long, String?, String?, Rect) -> Unit,
  onCommentProfileClick: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onMentionQuery: (String) -> Unit,
  onPrepareEnterFullscreen: () -> Unit,
  onEnterFullscreen: () -> Unit,
  embeddedGestureResetKey: Int,
  playerControlsVisible: Boolean,
  controlsVisible: Boolean,
  onControlsVisible: (Boolean) -> Unit,
  onControlsMenuVisibilityChanged: (Boolean) -> Unit,
  onDanmakuComposerVisibilityChanged: (Boolean) -> Unit,
  onProgressScrubChanged: (Boolean) -> Unit,
  onPlayPause: () -> Unit,
  onTemporarySpeedChanged: (Boolean) -> Unit,
  onPlaybackSpeedChanged: (Float) -> Unit,
  onReplay: () -> Unit,
  onVideoPageSelected: (VideoPage) -> Unit,
  onCollectionEpisodeSelected: (FeedItem, Rect?) -> Unit,
  onSeek: (Long) -> Unit,
  onSeekPreview: (Long) -> Unit,
  onSeekCancel: () -> Unit,
  onToggleDanmaku: () -> Unit,
  onSendDanmaku: (String, Int, Int, Int, Int) -> Unit,
  onSwitchQuality: (Int) -> Unit,
  premiumAudioVisible: Boolean,
  commentImageEnabled: Boolean,
  onCommentImagePreview: (CommentImage, Rect) -> Unit,
  onSwitchPremiumAudio: (PremiumAudioMode) -> Unit,
  subtitleState: PlayerSubtitleState,
  onSelectSubtitle: (String?) -> Unit,
  playerView: @Composable (Modifier) -> Unit,
  settings: AppSettings,
  brightnessGestureEnabled: Boolean,
  onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
  panelSlideProgress: () -> Float = { 1f },
  bangumiPage: BangumiPageUi? = null,
  onBangumiPosterBoundsChanged: (Rect) -> Unit = {},
  onBangumiOpenDetails: () -> Unit = {},
  onBangumiOpenEpisodeSelection: () -> Unit = {},
  onBangumiEpisodeSelected: (dev.openbili.webdemo.api.BangumiEpisode) -> Unit = {},
  onBangumiSeasonSelected: (Long) -> Unit = {},
  controlPlayerModifier: Modifier = Modifier,
  controlPlayerControlsEnabled: Boolean = false,
  controlPlayerControlsFocusRequester: FocusRequester? = null,
  controlPlaybackEndedEnabled: Boolean = false,
  controlPlaybackEndFocusRequester: FocusRequester? = null,
  controlModeEnabled: Boolean = false,
  controlNavigationEnabled: Boolean = false,
  controlPlayerFocusRequester: FocusRequester? = null,
  controlRecommendationFocusRequester: FocusRequester? = null,
  controlBangumiLowerPanelFocus: BangumiLowerPanelControlFocus? = null,
  controlCommentFocusRequester: FocusRequester? = null,
  modifier: Modifier = Modifier,
  onPlayerBoundsChanged: (Rect) -> Unit,
) {
  val controlCommentSortFocusRequester = remember { FocusRequester() }
  val controlFirstCommentFocusRequester = remember { FocusRequester() }
  val controlReplyEntryFocusRequester = remember { FocusRequester() }
  val recommendationImageLoadPolicy = rememberListFeedImageLoadPolicy(recommendationListState)
  val commentImageLoadPolicy = rememberListFeedImageLoadPolicy(commentListState)
  var localGestureFeedback by remember { mutableStateOf<GestureIndicator?>(null) }
  var localGestureFeedbackVisible by remember { mutableStateOf(false) }
  var localGestureFeedbackVersion by remember { androidx.compose.runtime.mutableIntStateOf(0) }
  var localSeekPreviewMs by remember { mutableStateOf<Long?>(null) }
  LaunchedEffect(brightnessGestureEnabled) {
    if (
      !brightnessGestureEnabled && localGestureFeedback?.kind == GestureIndicatorKind.BRIGHTNESS
    ) {
      localGestureFeedbackVisible = false
      localGestureFeedback = null
    }
  }
  var replyTarget by remember(item.id) { mutableStateOf<CommentItem?>(null) }
  var replyTargetRoot by remember(item.id) { mutableStateOf<CommentItem?>(null) }
  var displayedReplyRoot by remember(item.id) { mutableStateOf<CommentItem?>(replyRoot) }
  var controlReplyReturnFocusRequester by
    remember(item.id) { mutableStateOf<FocusRequester?>(null) }
  var replySourceBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  var navigationRootBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  var embeddedPlayerBoundsInRoot by remember(item.id) { mutableStateOf(Rect.Zero) }
  var openedNavigationRequestId by
    remember(item.id, commentNavigationSessionId) { mutableStateOf<Long?>(null) }
  var primaryPaneSize by remember { mutableStateOf(IntSize.Zero) }
  val density = LocalDensity.current
  val pageLayout =
    with(density) {
      if (bangumiPage != null) {
        bangumiPageLayoutForPane(
          primaryWidthDp = primaryPaneSize.width.toDp().value,
          primaryHeightDp = primaryPaneSize.height.toDp().value,
          fontScale = density.fontScale,
        )
      } else {
        videoPageLayoutForPane(
          primaryWidthDp = primaryPaneSize.width.toDp().value,
          primaryHeightDp = primaryPaneSize.height.toDp().value,
          fontScale = density.fontScale,
        )
      }
    }
  var commentPaneBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  var commentExpandedBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  var commentComposerBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  val replyTransitionProgress = remember(item.id) { Animatable(if (replyRoot == null) 0f else 1f) }
  var replyContentReady by remember(item.id) { mutableStateOf(replyRoot != null) }
  var replyClosing by remember(item.id) { mutableStateOf(false) }
  var replyDismissRequestId by
    remember(item.id) { androidx.compose.runtime.mutableLongStateOf(0L) }
  var handledReplyDismissRequestId by
    remember(item.id) { androidx.compose.runtime.mutableLongStateOf(0L) }
  var replyPreparation by
    remember(item.id) {
      mutableStateOf<TransitionPreparationBarrier?>(null)
    }
  var contentBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  var longCommentOverlay by remember(item.id) { mutableStateOf(false) }
  var resumePlaybackAfterLongComment by remember(item.id) { mutableStateOf(false) }
  val controlFocusVisible = LocalControlFocusVisible.current
  val controllerOwnsInteraction =
    controllerInteractionActive(
      controlMode = controlModeEnabled,
      controlFocusVisible = controlFocusVisible,
    )
  LaunchedEffect(controllerOwnsInteraction) {
    if (controllerOwnsInteraction) {
      commentComposerBounds = Rect.Zero
      replyTarget = null
      replyTargetRoot = null
      longCommentOverlay = false
      if (resumePlaybackAfterLongComment && !isPlaying) onPlayPause()
      resumePlaybackAfterLongComment = false
    }
  }
  LaunchedEffect(
    commentNavigationSessionId,
    commentNavigationTarget?.requestId,
    commentNavigationTarget?.targetRpid,
    commentNavigationTarget?.rootRpid,
    commentItems,
  ) {
    val target = commentNavigationTarget ?: return@LaunchedEffect
    if (openedNavigationRequestId == target.requestId) return@LaunchedEffect
    val rootIndex = commentItems.indexOfFirst { it.rpid == target.rootRpid }
    if (rootIndex < 0) return@LaunchedEffect
    commentListState.animateScrollToItem(rootIndex)
    repeat(8) {
      withFrameNanos {}
      if (navigationRootBounds.hasUsableSize()) return@repeat
    }
    openedNavigationRequestId = target.requestId
    if (target.targetRpid == target.rootRpid) {
      onCommentNavigationConsumed()
    } else {
      replySourceBounds = navigationRootBounds.takeIf { it.hasUsableSize() } ?: commentPaneBounds
      onOpenReplies(commentItems[rootIndex])
    }
  }
  var showDanmakuComposer by remember(item.id) { mutableStateOf(false) }
  LaunchedEffect(danmakuComposerEnabled) {
    if (!danmakuComposerEnabled) showDanmakuComposer = false
  }
  LaunchedEffect(showDanmakuComposer) {
    onDanmakuComposerVisibilityChanged(showDanmakuComposer)
  }
  DisposableEffect(Unit) {
    onDispose {
      onDanmakuComposerVisibilityChanged(false)
      onProgressScrubChanged(false)
    }
  }
  var commentChromeVisibility by commentChromeState.visibility
  var keepCommentChromeHiddenAtTop by commentChromeState.keepHiddenAtTop
  var commentFastScrolling by remember(item.id) { mutableStateOf(false) }
  var floatingActionsExpanded by commentChromeState.floatingActionsExpanded
  var deleteCandidate by remember(item.id) { mutableStateOf<CommentItem?>(null) }
  var deleteCandidateBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  val currentUploaderMid = videoInfo?.uploaderMid ?: item.uploaderMid
  val emoteMap = remember(emotes) { emotes.associateBy { it.text } }
  fun canDeleteComment(comment: CommentItem): Boolean =
    commentCanBeDeletedBy(currentAccountMid, currentUploaderMid, comment.mid)

  LaunchedEffect(commentListState, item.id) {
    var previous =
      CommentScrollPosition(
        index = commentListState.firstVisibleItemIndex,
        offset = commentListState.firstVisibleItemScrollOffset,
      )
    snapshotFlow {
        CommentScrollSnapshot(
          position =
            CommentScrollPosition(
              index = commentListState.firstVisibleItemIndex,
              offset = commentListState.firstVisibleItemScrollOffset,
            ),
          scrolling = commentListState.isScrollInProgress,
        )
      }
      .map { snapshot ->
        val current = snapshot.position
        val direction =
          when {
            current.index > previous.index ||
              (current.index == previous.index && current.offset > previous.offset) -> 1
            current.index < previous.index ||
              (current.index == previous.index && current.offset < previous.offset) -> -1
            else -> 0
          }
        previous = current
        Triple(snapshot.scrolling, direction, current.isAtTop)
      }
      .distinctUntilChanged()
      .collect { (scrolling, direction, isAtTop) ->
        if (commentFastScrolling != scrolling) commentFastScrolling = scrolling
        // 只有列表真正朝顶部回移时才释放短列表回弹闩锁。静止的短列表仍可能被
        // 上滑视口手势有意收起；可滚动列表到达偏移零必须恢复操作行。
        if (isAtTop && direction < 0 && keepCommentChromeHiddenAtTop) {
          keepCommentChromeHiddenAtTop = false
        }
        val nextVisibility =
          commentChromeAfterObservedScroll(
            currentVisibility = commentChromeVisibility,
            scrolling = scrolling,
            direction = direction,
            isAtTop = isAtTop,
            keepHiddenAtTop = keepCommentChromeHiddenAtTop,
          )
        if (commentChromeVisibility != nextVisibility) {
          commentChromeVisibility = nextVisibility
        }
        if (scrolling && direction != 0) {
          if (floatingActionsExpanded) floatingActionsExpanded = false
        }
      }
  }
  var keepLoadingCover by remember(item.id) { mutableStateOf(true) }
  val loadingCoverAlpha = remember(item.id) { Animatable(1f) }
  val loadingCoverBlur = remember(item.id) { Animatable(0f) }
  LaunchedEffect(item.id, showLoadingCover) {
    if (showLoadingCover) {
      keepLoadingCover = true
      loadingCoverAlpha.snapTo(1f)
      loadingCoverBlur.snapTo(0f)
    } else if (keepLoadingCover) {
      delay(40)
      coroutineScope {
        launch {
          loadingCoverBlur.animateTo(
            14f,
            tween(if (settings.reduceMotion) 70 else 220, easing = FastOutSlowInEasing),
          )
        }
        launch {
          loadingCoverAlpha.animateTo(
            0f,
            tween(if (settings.reduceMotion) 90 else 240, easing = FastOutSlowInEasing),
          )
        }
      }
      keepLoadingCover = false
    }
  }
  LaunchedEffect(localGestureFeedbackVersion, localGestureFeedback) {
    val version = localGestureFeedbackVersion
    val displayed = localGestureFeedback ?: return@LaunchedEffect
    delay(800)
    if (localGestureFeedbackVersion != version || localGestureFeedback != displayed) {
      return@LaunchedEffect
    }
    localGestureFeedbackVisible = false
    delay(GESTURE_INDICATOR_FADE_OUT_MS.toLong())
    if (localGestureFeedbackVersion == version && localGestureFeedback == displayed) {
      localGestureFeedback = null
    }
  }
  LaunchedEffect(replyRoot?.rpid, replyDismissRequestId) {
    val root = replyRoot
    if (
      replyDismissRequestId != handledReplyDismissRequestId &&
        displayedReplyRoot != null
    ) {
      // 关闭请求使用单调代次而不是布尔值：父层在动画结束后清空 replyRoot 时不会把
      // 当前收起协程的 effect key 改回初始值，从而避免第二次打开后只能播放按压动画。
      handledReplyDismissRequestId = replyDismissRequestId
      replyPreparation?.cancel()
      replyPreparation = null
      replyClosing = true
      // 收起动画开始前停止绘制昂贵的回复 LazyColumn：下方轻量表面保持可见，
      // 关闭仍读作一次连续转场。
      replyContentReady = false
      // 收起期间只挂载轻量面板：录制或变换完整回复列表会造成长时间主线程绘制，
      // 让动画跳到结尾。
      replyTransitionProgress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 80 else 260, easing = FastOutSlowInEasing),
      )
      delay(16)
      displayedReplyRoot = null
      replyClosing = false
      onDismissReplies()
      if (controllerOwnsInteraction) {
        withFrameNanos {}
        controlReplyReturnFocusRequester?.let { requester ->
          runCatching { requester.requestFocus() }
        }
      }
      replyPreparation = null
    } else if (root != null) {
      replyClosing = false
      val preparation =
        TransitionPreparationBarrier(
          setOf(
            TransitionReadySignal.SOURCE_BOUNDS,
            TransitionReadySignal.TARGET_MOUNTED,
            TransitionReadySignal.TARGET_BOUNDS_STABLE,
          )
        )
      replyPreparation?.cancel()
      replyPreparation = preparation
      preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
      displayedReplyRoot = root
      replyContentReady = false
      replyTransitionProgress.snapTo(0f)
      val boundsTracker = StableBoundsTracker(requiredMatches = 1)
      for (frame in 0 until 2) {
        withFrameNanos {}
        if (commentExpandedBounds.width > 0f && commentExpandedBounds.height > 0f) {
          preparation.markReady(TransitionReadySignal.TARGET_MOUNTED)
          if (boundsTracker.observe(commentExpandedBounds)) {
            preparation.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
            break
          }
        }
      }
      // 回复目标是已布局好的评论窗格：不要让过期/无效的边界回调把用户点击
      // 卡在全局转场超时之后。
      preparation.await(timeoutMillis = 80L)
      if (replyPreparation !== preparation || displayedReplyRoot?.rpid != root.rpid) {
        return@LaunchedEffect
      }
      replyTransitionProgress.animateTo(
        1f,
        tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
      )
      withFrameNanos {}
      replyContentReady = true
      if (controllerOwnsInteraction) {
        withFrameNanos {}
        runCatching { controlReplyEntryFocusRequester.requestFocus() }
      }
      replyPreparation = null
    } else if (displayedReplyRoot != null) {
      replyPreparation?.cancel()
      replyPreparation = null
      replyClosing = true
      replyContentReady = false
      replyTransitionProgress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 80 else 260, easing = FastOutSlowInEasing),
      )
      delay(16)
      displayedReplyRoot = null
      replyClosing = false
      if (controllerOwnsInteraction) {
        withFrameNanos {}
        controlReplyReturnFocusRequester?.let { requester ->
          runCatching { requester.requestFocus() }
        }
      }
      replyPreparation = null
    }
  }
  BackHandler(enabled = deleteCandidate != null) { deleteCandidate = null }
  BackHandler(enabled = displayedReplyRoot != null && deleteCandidate == null) {
    if (!replyClosing) replyDismissRequestId += 1L
  }
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .padding(start = 16.dp, end = 12.dp, bottom = 12.dp)
        .onGloballyPositioned { contentBounds = it.boundsInRoot() }
        .pointerInput(deleteCandidate?.rpid, deleteCandidateBounds, contentBounds) {
          if (
            deleteCandidate == null ||
              deleteCandidateBounds.width <= 0f ||
              deleteCandidateBounds.height <= 0f
          )
            return@pointerInput
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              val down = event.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
              val rootPosition = down.position + Offset(contentBounds.left, contentBounds.top)
              if (!deleteCandidateBounds.contains(rootPosition)) {
                down.consume()
                deleteCandidate = null
              }
            }
          }
        }
  ) {
    AdaptiveVideoPanes(
      modifier = Modifier.fillMaxSize(),
      primary = {
        // ── 左：播放器 + 信息 + 推荐 ──────────────────────────
        Column(modifier = Modifier.fillMaxSize().onSizeChanged { primaryPaneSize = it }) {
          // 实测窗格高度在播放器与推荐轨之间共享：避免全宽 16:9 播放器把推荐轨
          // 挤出矮窗口。
          val measuredPane = primaryPaneSize.width > 0 && primaryPaneSize.height > 0
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .then(
                  if (measuredPane) Modifier.height(pageLayout.playerHeight)
                  else Modifier.aspectRatio(16f / 9f)
                ),
            contentAlignment = Alignment.TopStart,
          ) {
            Surface(
              modifier =
                (if (measuredPane) Modifier.width(pageLayout.playerWidth).fillMaxHeight()
                  else Modifier.fillMaxSize())
                  .then(controlPlayerModifier)
                  .onGloballyPositioned {
                    val bounds = it.boundsInRoot()
                    embeddedPlayerBoundsInRoot = bounds
                    onPlayerBoundsChanged(bounds)
                  }
                  .then(
                    if (bangumiPage != null) {
                      Modifier.graphicsLayer {
                        alpha = panelSlideProgress().coerceIn(0f, 1f)
                      }
                    } else Modifier
                  ),
              shape = VideoShapeTokens.Player,
              color = Color.Transparent,
              shadowElevation = 0.dp,
              tonalElevation = 0.dp,
              border = null,
            ) {
              Box(
                Modifier.fillMaxSize().graphicsLayer {
                  alpha = embeddedPlayerHandoffAlpha().coerceIn(0f, 1f)
                }
              ) {
                playerView(Modifier.fillMaxSize().zIndex(0f))
                if (keepLoadingCover) {
                  Box(
                    modifier =
                      Modifier.fillMaxSize()
                        .blur(loadingCoverBlur.value.dp)
                        .graphicsLayer { alpha = loadingCoverAlpha.value }
                        .zIndex(.5f)
                  ) {
                    CoverImage(
                      coverUrl = item.coverUrl,
                      modifier = Modifier.fillMaxSize(),
                      shape = VideoShapeTokens.Player,
                      contentScale =
                        if (bangumiPage != null) androidx.compose.ui.layout.ContentScale.Fit
                        else androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    if (dimLoadingCover) {
                      // 该遮罩属于临时 SDR 封面层：随封面淡出，绝不触碰底下已解码
                      // 的 HDR/杜比 SurfaceView。
                      Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .62f)))
                    }
                  }
                }

                // 起播缓冲时的小加载圈。
                if (isBuffering || (keepLoadingCover && loadingCoverAlpha.value > .15f)) {
                  Box(
                    modifier = Modifier.fillMaxSize().zIndex(.75f),
                    contentAlignment = Alignment.Center,
                  ) {
                    androidx.compose.material3.CircularProgressIndicator(
                      modifier = Modifier.size(32.dp),
                      strokeWidth = 3.dp,
                    )
                  }
                }

                key(embeddedGestureResetKey) {
                  PlayerGestureLayer(
                    enabledBrightness = brightnessGestureEnabled,
                    enabledVolume = settings.volumeGesture,
                    enabledSeek = settings.horizontalSeekGesture,
                    enabledFullscreenToggle = settings.twoFingerFullscreenGesture,
                    enabledTwoFingerSeek = settings.twoFingerSeekGesture,
                    positionProvider = playerPositionProvider,
                    durationMs = durationMs,
                    onSeek = onSeek,
                    onIndicator = {
                      localGestureFeedback = it
                      localGestureFeedbackVisible = true
                      localGestureFeedbackVersion += 1
                    },
                    onSeekPreview = {
                      localSeekPreviewMs = it
                      if (it != null) {
                        onSeekPreview(it)
                        localGestureFeedbackVisible = false
                        localGestureFeedback = null
                        onControlsVisible(true)
                      }
                    },
                    onSeekCancel = onSeekCancel,
                    onToggleControls = { onControlsVisible(!controlsVisible) },
                    onDoubleTap = onPlayPause,
                    onTemporarySpeedChanged = onTemporarySpeedChanged,
                    isFullscreen = false,
                    onFullscreenChanged = { if (it) onEnterFullscreen() },
                    seekEdgeInset = 0.dp,
                    modifier = Modifier.fillMaxSize().zIndex(1.5f),
                  )
                }
                FadeVisibility(
                  visible = localGestureFeedbackVisible,
                  enterMillis = GESTURE_INDICATOR_FADE_IN_MS,
                  exitMillis = GESTURE_INDICATOR_FADE_OUT_MS,
                  modifier =
                    Modifier.align(
                        when (localGestureFeedback?.kind) {
                          GestureIndicatorKind.BRIGHTNESS -> Alignment.CenterStart
                          GestureIndicatorKind.VOLUME -> Alignment.CenterEnd
                          else -> Alignment.Center
                        }
                      )
                      .padding(horizontal = 18.dp)
                      .zIndex(2.5f),
                ) {
                  localGestureFeedback?.let { indicator ->
                    GestureIndicatorOverlay(indicator = indicator)
                  }
                }
                if (!isPlaying && !isBuffering && !playbackEnded) {
                  PlayerCenterPlayPauseButton(
                    isPlaying = false,
                    onPlayPause = onPlayPause,
                    modifier = Modifier.align(Alignment.Center).zIndex(3.5f),
                  )
                }
                FadingVisibility(
                  visible = controlsVisible,
                  modifier = Modifier.fillMaxSize().zIndex(3f),
                ) {
                  ModernPlayerControls(
                    playData = playData,
                    premiumAudioVisible = premiumAudioVisible,
                    showDanmaku = showDanmaku,
                    danmakuSmartBlocking = settings.danmakuSmartBlocking,
                    isFullscreen = false,
                    isPlaying = isPlaying,
                    showCenterAction = isPlaying,
                    currentPositionMs = { localSeekPreviewMs ?: currentPositionMs() },
                    durationMs = durationMs,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onSeekPreview = onSeekPreview,
                    onSeekCancel = onSeekCancel,
                    onFullscreen = onEnterFullscreen,
                    onFullscreenPress = onPrepareEnterFullscreen,
                    onToggleDanmaku = onToggleDanmaku,
                    onDanmakuSmartBlockingChange = { value ->
                      onSettingsChange { it.copy(danmakuSmartBlocking = value) }
                    },
                    danmakuComposerEnabled = danmakuComposerEnabled,
                    onComposeDanmaku = {
                      showDanmakuComposer = !showDanmakuComposer
                      if (showDanmakuComposer) onControlsVisible(true)
                    },
                    danmakuDisplayArea = settings.danmakuDisplayArea,
                    danmakuDensity = settings.danmakuDensity,
                    danmakuBlockLevel = settings.danmakuBlockLevel,
                    danmakuOpacity = settings.danmakuOpacity,
                    danmakuFontScale = settings.danmakuFontScale,
                    danmakuSpeed = settings.danmakuSpeed,
                    playbackSpeed = playbackSpeed,
                    onDanmakuDisplayAreaChange = { value ->
                      onSettingsChange { it.copy(danmakuDisplayArea = value) }
                    },
                    onDanmakuDensityChange = { value ->
                      onSettingsChange { it.copy(danmakuDensity = value) }
                    },
                    onDanmakuBlockLevelChange = { value ->
                      onSettingsChange { it.copy(danmakuBlockLevel = value) }
                    },
                    onDanmakuOpacityChange = { value ->
                      onSettingsChange { it.copy(danmakuOpacity = value) }
                    },
                    onDanmakuFontScaleChange = { value ->
                      onSettingsChange { it.copy(danmakuFontScale = value) }
                    },
                    onDanmakuSpeedChange = { value ->
                      onSettingsChange { it.copy(danmakuSpeed = value) }
                    },
                    onPlaybackSpeedChange = onPlaybackSpeedChanged,
                    onMenuVisibilityChanged = onControlsMenuVisibilityChanged,
                    onProgressScrubChanged = onProgressScrubChanged,
                    onSwitchQuality = onSwitchQuality,
                    onSwitchPremiumAudio = onSwitchPremiumAudio,
                    subtitleState = subtitleState,
                    onSelectSubtitle = onSelectSubtitle,
                    subtitleStyle = settings.subtitleStyle,
                    onSubtitleStyleChange = { style ->
                      onSettingsChange { it.copy(subtitleStyle = style) }
                    },
                    modifier = Modifier.fillMaxSize(),
                    controlEnabled = controlPlayerControlsEnabled,
                    controlInitialFocusRequester = controlPlayerControlsFocusRequester,
                  )
                }
                if (showDanmakuComposer) {
                  DanmakuComposer(
                    onSend = { message, color, mode, fontSize, colorful ->
                      onSendDanmaku(message, color, mode, fontSize, colorful)
                      showDanmakuComposer = false
                    },
                    onDismiss = { showDanmakuComposer = false },
                    vipActive = premiumAudioVisible,
                    initialColor = settings.danmakuColor,
                    initialColorful = settings.danmakuColorful,
                    onColorChanged = { color, colorful ->
                      onSettingsChange {
                        it.copy(danmakuColor = color, danmakuColorful = colorful)
                      }
                    },
                    imeHostBottomInRoot =
                      embeddedPlayerBoundsInRoot.bottom.takeIf {
                        embeddedPlayerBoundsInRoot.height > 0f
                      },
                    imeBaselineBottomPadding = 82.dp,
                    modifier =
                      Modifier.align(Alignment.BottomCenter).padding(bottom = 82.dp).zIndex(5f),
                  )
                }
                if (playbackEnded) {
                  if (nextPlaybackTarget != null) {
                    AutoNextOverlay(
                      coverUrl = item.coverUrl,
                      nextCoverUrl = nextPlaybackTarget.coverUrl,
                      nextTitle = nextPlaybackTarget.title,
                      seconds = autoNextSeconds,
                      triggered = autoNextTriggered,
                      autoPlayEnabled = settings.autoPlayNext,
                      handoffProgress = autoNextHandoffProgress,
                      revealAlpha = playbackEndRevealAlpha,
                      isFullscreen = false,
                      onFullscreen = onEnterFullscreen,
                      onNext = onAutoNext,
                      onReplay = onReplay,
                      controlEnabled = controlPlaybackEndedEnabled,
                      initialFocusRequester = controlPlaybackEndFocusRequester,
                      modifier = Modifier.fillMaxSize().zIndex(7f),
                    )
                  } else {
                    PlaybackEndedRecommendations(
                      coverUrl = item.coverUrl,
                      recommendations = recommendations,
                      hiddenCoverItemId = hiddenPlaybackEndRecommendationCoverItemId,
                      revealAlpha = playbackEndRevealAlpha,
                      isFullscreen = false,
                      onFullscreen = onEnterFullscreen,
                      onReplay = onReplay,
                      onRecommendationClick = { recommendation, enterBounds ->
                        onRecommendationClick(recommendation, enterBounds, enterBounds, true)
                      },
                      onRecommendationLongClick = onRecommendationLongClick,
                      controlEnabled = controlPlaybackEndedEnabled,
                      initialFocusRequester = controlPlaybackEndFocusRequester,
                      modifier = Modifier.fillMaxSize().zIndex(7f),
                    )
                  }
                }
              }
            }
          }

          if (deferAuxiliaryContent) {
            Spacer(Modifier.weight(1f))
          } else if (bangumiPage != null) {
            BangumiLowerPanel(
              page = bangumiPage,
              onPosterBoundsChanged = onBangumiPosterBoundsChanged,
              onOpenDetails = onBangumiOpenDetails,
              onOpenEpisodeSelection = onBangumiOpenEpisodeSelection,
              onEpisodeSelected = onBangumiEpisodeSelected,
              onSeasonSelected = onBangumiSeasonSelected,
              panelSlideProgress = panelSlideProgress,
              glassBackdrop = glassBackdrop,
              foregroundColor = pageForegroundColor,
              controlFocus = controlBangumiLowerPanelFocus,
              modifier = Modifier.weight(1f),
            )
          } else {
            // 推荐（横向滚动）
            AnimatedVisibility(
              visible = recommendations.isNotEmpty(),
              enter = fadeIn(tween(if (settings.reduceMotion) 90 else 240)),
              exit = fadeOut(tween(if (settings.reduceMotion) 70 else 160)),
            ) {
              Column(
                Modifier.graphicsLayer {
                  val progress = panelSlideProgress().coerceIn(0f, 1f)
                  alpha = progress
                }
              ) {
                Spacer(Modifier.height(8.dp))
                Text(
                  "推荐视频",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = pageForegroundColor,
                  modifier = Modifier.padding(horizontal = 12.dp),
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) {
                  val displayedRecommendations = recommendations.take(20)
                  CompositionLocalProvider(
                    LocalFeedImageLoadPolicy provides recommendationImageLoadPolicy
                  ) {
                    LazyRow(
                      state = recommendationListState,
                      modifier =
                        Modifier.then(
                          if (controlNavigationEnabled) {
                            Modifier.focusProperties {
                                onExit = {
                                  if (
                                    requestedFocusDirection == FocusDirection.Left ||
                                      requestedFocusDirection == FocusDirection.Right
                                  ) {
                                    cancelFocusChange()
                                  }
                                }
                              }
                              .focusGroup()
                          } else Modifier
                        ),
                      contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      itemsIndexed(
                        items = displayedRecommendations,
                        key = { _, rec -> rec.id },
                      ) { index, rec ->
                        RecommendationCard(
                          item = rec,
                          onClick = { bounds ->
                            onRecommendationClick(rec, bounds, bounds, false)
                          },
                          onLongClick = { onRecommendationLongClick(rec) },
                          coverVisible = rec.id != hiddenRecommendationCoverItemId,
                          onCoverBoundsChanged = { recommendationReturnBounds[rec.id] = it },
                          cardWidth = pageLayout.recommendationCardWidth,
                          compactHorizontal = pageLayout.compactHorizontalRecommendations,
                          compactHeight = pageLayout.compactRecommendationCardHeight,
                          showDuration = true,
                          controlEnabled = controlNavigationEnabled,
                          controlFocusRequester =
                            controlRecommendationFocusRequester.takeIf { index == 0 },
                          controlUpFocusRequester = controlPlayerFocusRequester,
                          controlAtHorizontalStart = index == 0,
                          controlAtHorizontalEnd = index == displayedRecommendations.lastIndex,
                        )
                      }
                    }
                  }
                }
              }
            }
            FadeVisibility(
              visible = pageContentLoading && recommendations.isEmpty(),
              enterMillis = if (settings.reduceMotion) 80 else 180,
              exitMillis = if (settings.reduceMotion) 70 else 150,
            ) {
              RecommendationLoadingSkeleton(
                Modifier.graphicsLayer {
                  alpha = panelSlideProgress().coerceIn(0f, 1f)
                }
              )
            }
          }
        }
      },
      secondary = {
        if (deferCommentContent) {
          // 为播放器目标保留窗格真实的约束派生几何，在共享封面占用帧预算期间
          // 不挂载也不绘制评论。
          Box(
            Modifier.fillMaxSize().onGloballyPositioned {
              commentExpandedBounds = it.boundsInRoot()
            }
          )
        } else {
          // ── 右：评论 ───────────────────────────────────────────────
          Box(
            modifier =
              Modifier.fillMaxSize()
                .onGloballyPositioned { commentExpandedBounds = it.boundsInRoot() }
                .zIndex(2f)
                .graphicsLayer {
                  val progress = panelSlideProgress().coerceIn(0f, 1f)
                  alpha = progress
                  compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .pointerInput(item.id, displayedReplyRoot?.rpid) {
                  if (displayedReplyRoot != null) return@pointerInput
                  val collapseThresholdPx = 24.dp.toPx()
                  val restoreThresholdPx = 32.dp.toPx()
                  awaitPointerEventScope {
                    var tracking = false
                    var handled = false
                    var travelY = 0f
                    while (true) {
                      val event = awaitPointerEvent(PointerEventPass.Final)
                      val change = event.changes.firstOrNull() ?: continue
                      when {
                        change.pressed && !change.previousPressed -> {
                          tracking = true
                          handled = false
                          travelY = 0f
                        }
                        tracking && change.pressed -> {
                          travelY += change.position.y - change.previousPosition.y
                          val crossedThreshold =
                            travelY <= -collapseThresholdPx || travelY >= restoreThresholdPx
                          if (!handled && crossedThreshold) {
                            keepCommentChromeHiddenAtTop = travelY < 0f
                            commentChromeVisibility =
                              commentChromeAfterViewportSwipe(
                                currentVisibility = commentChromeVisibility,
                                fingerDeltaY = travelY,
                                listIsAtTop =
                                  commentListState.firstVisibleItemIndex == 0 &&
                                    commentListState.firstVisibleItemScrollOffset == 0,
                              )
                            if (floatingActionsExpanded) floatingActionsExpanded = false
                            handled = true
                          }
                        }
                        !change.pressed -> {
                          tracking = false
                          handled = false
                          travelY = 0f
                        }
                      }
                    }
                  }
                }
          ) {
            Box(
              Modifier.fillMaxSize().graphicsLayer {
                // 回复表面接近不透明后，停止在其后合成评论列表：保持仅渲染，
                // 避免运动期间重组任一 LazyColumn。
                alpha = (1f - replyTransitionProgress.value * 2.5f).coerceIn(0f, 1f)
              }
            ) {
              Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                  visible = commentChromeVisibility.showDockedActions,
                  modifier = Modifier.fillMaxWidth(),
                  enter =
                    expandVertically(
                      animationSpec = tween(if (settings.reduceMotion) 80 else 240),
                      expandFrom = Alignment.Top,
                    ) +
                      scaleIn(
                        initialScale = .24f,
                        transformOrigin = TransformOrigin(.5f, 0f),
                        animationSpec = tween(if (settings.reduceMotion) 80 else 220),
                      ) +
                      fadeIn(tween(if (settings.reduceMotion) 60 else 160)),
                  exit =
                    shrinkVertically(
                      animationSpec = tween(if (settings.reduceMotion) 70 else 220),
                      shrinkTowards = Alignment.Top,
                    ) +
                      scaleOut(
                        targetScale = .24f,
                        transformOrigin = TransformOrigin(.5f, 0f),
                        animationSpec = tween(if (settings.reduceMotion) 70 else 200),
                      ) +
                      fadeOut(tween(if (settings.reduceMotion) 60 else 140)),
                ) {
                  Column {
                    VideoActionPanel(
                      info = videoInfo,
                      engagement = videoEngagement,
                      loggedIn = currentAccountMid > 0L,
                      favoriteFolders = favoriteFolders,
                      favoriteFoldersLoading = favoriteFoldersLoading,
                      onLike = onLikeVideo,
                      onCoin = onCoinVideo,
                      onFavorite = onFavoriteVideo,
                      onLoadFavoriteFolders = onLoadFavoriteFolders,
                      onLogin = onLogin,
                      foregroundColor = pageForegroundColor,
                      controlEnabled = controlNavigationEnabled,
                      controlLikeFocusRequester = controlCommentFocusRequester,
                      controlPlayerFocusRequester = controlPlayerFocusRequester,
                      controlSortFocusRequester = controlCommentSortFocusRequester,
                    )
                    Spacer(Modifier.height(8.dp))
                  }
                }
                AnimatedVisibility(
                  visible = commentChromeVisibility.showSortControls,
                  modifier = Modifier.fillMaxWidth(),
                  enter =
                    slideInVertically(
                      initialOffsetY = { -it },
                      animationSpec = tween(if (settings.reduceMotion) 70 else 190),
                    ) +
                      expandVertically(
                        animationSpec = tween(if (settings.reduceMotion) 70 else 190),
                        expandFrom = Alignment.Top,
                      ) +
                      fadeIn(tween(if (settings.reduceMotion) 60 else 150)),
                  exit =
                    slideOutVertically(
                      targetOffsetY = { -it },
                      animationSpec = tween(if (settings.reduceMotion) 70 else 180),
                    ) +
                      shrinkVertically(
                        animationSpec = tween(if (settings.reduceMotion) 70 else 180),
                        shrinkTowards = Alignment.Top,
                      ) +
                      fadeOut(tween(if (settings.reduceMotion) 60 else 130)),
                ) {
                  CommentSectionHeader(
                    commentCount = commentTotalCount,
                    commentSort = commentSort,
                    onCommentSort = onCommentSort,
                    foregroundColor = pageForegroundColor,
                    controlEnabled = controlNavigationEnabled,
                    controlFirstFocusRequester = controlCommentSortFocusRequester,
                    controlUpFocusRequester = controlCommentFocusRequester,
                    controlDownFocusRequester = controlFirstCommentFocusRequester,
                  )
                }
                Box(
                  Modifier.weight(1f).fillMaxWidth().onGloballyPositioned {
                    commentPaneBounds = it.boundsInRoot()
                  }
                ) {
                  val nearCommentEnd by
                    remember(commentListState) {
                      derivedStateOf {
                        val last =
                          commentListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= commentListState.layoutInfo.totalItemsCount - 4
                      }
                    }
                  LaunchedEffect(
                    nearCommentEnd,
                    commentHasMore,
                    commentsLoading,
                    commentImageLoadPolicy.mode,
                  ) {
                    if (
                      nearCommentEnd &&
                        commentHasMore &&
                        !commentsLoading &&
                        commentImageLoadPolicy.mode != FeedImageLoadMode.PAUSED
                    )
                      onLoadMoreComments()
                  }
                  LaunchedEffect(commentSort) {
                    commentListState.scrollToItem(0)
                  }
                  val commentBottomPadding =
                    with(LocalDensity.current) {
                      maxOf(100.dp.toPx(), commentComposerBounds.height + 16.dp.toPx()).toDp()
                    }
                  Box(Modifier.matchParentSize()) {
                    PullRefreshContainer(
                      refreshing = commentsRefreshing,
                      onRefresh = onRefreshComments,
                      modifier = Modifier.fillMaxSize(),
                    ) {
                      FadeVisibility(
                        visible = commentItems.isNotEmpty(),
                        modifier = Modifier.fillMaxSize(),
                        enterMillis = if (settings.reduceMotion) 90 else 240,
                        exitMillis = if (settings.reduceMotion) 70 else 150,
                      ) {
                        CompositionLocalProvider(
                          LocalFeedImageLoadPolicy provides commentImageLoadPolicy
                        ) {
                          LazyColumn(
                            state = commentListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                              PaddingValues(
                                start = 10.dp,
                                end = 10.dp,
                                top = 8.dp,
                                bottom = commentBottomPadding,
                              ),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                          ) {
                            itemsIndexed(
                              items = commentItems,
                              key = { _, comment -> comment.rpid },
                              contentType = { _, _ -> "comment" },
                            ) { index, comment ->
                              val controlReturnFocusRequester =
                                remember(comment.rpid) { FocusRequester() }
                              Box(
                                Modifier.fillMaxWidth().onGloballyPositioned {
                                  if (commentNavigationTarget?.rootRpid == comment.rpid) {
                                    navigationRootBounds = it.boundsInRoot()
                                  }
                                }
                              ) {
                                CommentRow(
                                  comment,
                                  settings.showCommentEmotes,
                                  emoteMap,
                                  settings.showCommentLocation,
                                  onLikeComment,
                                  videoInfo?.uploaderMid ?: item.uploaderMid,
                                  onCommentProfileClick,
                                  onCommentImagePreview,
                                  { comment, bounds ->
                                    replySourceBounds = bounds
                                    onOpenReplies(comment)
                                  },
                                  {
                                    replyTargetRoot = comment
                                    replyTarget = comment
                                  },
                                  bottomClearancePx = commentComposerBounds.height,
                                  viewportHeightPx = commentPaneBounds.height,
                                  avatarVisible = hiddenCommentAvatarRpid != comment.rpid,
                                  trackBounds = !commentFastScrolling,
                                  hiddenLinkedVideoCoverItemId = hiddenRecommendationCoverItemId,
                                  onLinkedVideoClick = { linkedVideo, bounds ->
                                    commentMediaBounds[linkedVideo.id] = bounds
                                    onRecommendationClick(linkedVideo, bounds, bounds, false)
                                  },
                                  onLinkedVideoBoundsChanged = { linkedVideo, bounds ->
                                    commentMediaBounds[linkedVideo.id] = bounds
                                  },
                                  onLinkedVideoLongClick = onRecommendationLongClick,
                                  onTimestampClick = { seconds, _ ->
                                    onSeek((seconds * 1000L).coerceIn(0L, durationMs))
                                  },
                                  onLinkedArticleClick = onArticleClick,
                                  hiddenLinkedArticleItemId = hiddenLinkedArticleItemId,
                                  deletionSelected = deleteCandidate?.rpid == comment.rpid,
                                  pinActionAvailable =
                                    currentAccountMid > 0L && currentAccountMid == currentUploaderMid,
                                  pinActionLabel = if (comment.isPinned) "取消置顶" else "置顶",
                                  onPinRequest = {
                                    deleteCandidate = null
                                    onToggleCommentPin(comment)
                                  },
                                  onDeleteRequest =
                                    if (canDeleteComment(comment)) {
                                      { bounds ->
                                        deleteCandidateBounds = bounds
                                        deleteCandidate = comment
                                      }
                                    } else null,
                                  onDeleteConfirm = {
                                    deleteCandidate = null
                                    onDeleteComment(comment)
                                  },
                                  onDeleteCancel = { deleteCandidate = null },
                                  onDeletionBoundsChanged = { deleteCandidateBounds = it },
                                  controlEnabled = controlNavigationEnabled,
                                  controlFocusRequester =
                                    controlFirstCommentFocusRequester.takeIf {
                                      index ==
                                        commentListState.firstVisibleItemIndex.coerceIn(
                                          0,
                                          commentItems.lastIndex.coerceAtLeast(0),
                                        )
                                    },
                                  controlReturnFocusRequester = controlReturnFocusRequester,
                                  controlPlayerFocusRequester = controlPlayerFocusRequester,
                                  controlUpFocusRequester =
                                    controlCommentSortFocusRequester.takeIf { index == 0 },
                                  controlAtListEnd = index == commentItems.lastIndex,
                                  onControlOpenReplies = { selectedComment, bounds ->
                                    controlReplyReturnFocusRequester = controlReturnFocusRequester
                                    replySourceBounds = bounds
                                    onOpenReplies(selectedComment)
                                  },
                                )
                              }
                            }
                            if (commentsLoading) {
                              item {
                                Box(
                                  Modifier.fillMaxWidth().padding(8.dp),
                                  contentAlignment = Alignment.Center,
                                ) {
                                  CircularProgressIndicator(
                                    Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                  )
                                }
                              }
                            }
                          }
                        }
                      }
                      FadeVisibility(
                        visible = pageContentLoading && commentItems.isEmpty(),
                        modifier = Modifier.fillMaxSize(),
                        enterMillis = if (settings.reduceMotion) 80 else 180,
                        exitMillis = if (settings.reduceMotion) 70 else 150,
                      ) {
                        CommentLoadingSkeleton(Modifier.fillMaxSize())
                      }
                      if (commentsLoading && commentItems.isEmpty() && !pageContentLoading) {
                        CircularProgressIndicator(
                          Modifier.align(Alignment.Center).size(24.dp),
                          strokeWidth = 2.dp,
                        )
                      }
                    }
                  }
                }
              }
              if (!commentChromeVisibility.showDockedActions && displayedReplyRoot == null) {
                FloatingVideoActions(
                  expanded = floatingActionsExpanded,
                  info = videoInfo,
                  engagement = videoEngagement,
                  loggedIn = currentAccountMid > 0L,
                  favoriteFolders = favoriteFolders,
                  favoriteFoldersLoading = favoriteFoldersLoading,
                  reduceMotion = settings.reduceMotion,
                  onExpandedChange = { floatingActionsExpanded = it },
                  onLike = onLikeVideo,
                  onCoin = onCoinVideo,
                  onFavorite = onFavoriteVideo,
                  onLoadFavoriteFolders = onLoadFavoriteFolders,
                  onLogin = onLogin,
                  foregroundColor = pageForegroundColor,
                  modifier = Modifier.fillMaxSize().zIndex(25f),
                )
              }
            }
            displayedReplyRoot?.let { root ->
              ReplyThreadTransitionContainer(
                sourceBounds = replySourceBounds,
                targetBounds = commentExpandedBounds,
                progress = { replyTransitionProgress.value },
                contentReady = replyContentReady,
                modifier = Modifier.fillMaxSize().zIndex(30f),
              ) {
                ReplyThreadPanel(
                  root = root,
                  replies = replyItems,
                  hasMore = replyHasMore,
                  loading = repliesLoading,
                  showEmotes = settings.showCommentEmotes,
                  emoteCatalog = emoteMap,
                  showLocation = settings.showCommentLocation,
                  onLike = onLikeComment,
                  uploaderMid = currentUploaderMid,
                  onProfileClick = onCommentProfileClick,
                  onImagePreview = onCommentImagePreview,
                  onReply = { root, target ->
                    replyTargetRoot = root
                    replyTarget = target
                  },
                  onLoadMore = onLoadMoreReplies,
                  navigationTargetRpid =
                    commentNavigationTarget?.targetRpid?.takeIf {
                      commentNavigationTarget.rootRpid == root.rpid
                    },
                  navigationRequestId =
                    commentNavigationTarget?.requestId?.takeIf {
                      commentNavigationTarget.rootRpid == root.rpid
                    },
                  onNavigationTargetReached = onCommentNavigationConsumed,
                  onRefresh = onRefreshReplies,
                  onDismiss = {
                    if (!replyClosing) replyDismissRequestId += 1L
                  },
                  bottomClearancePx = commentComposerBounds.height,
                  hiddenCommentAvatarRpid = hiddenCommentAvatarRpid,
                  hiddenLinkedVideoCoverItemId = hiddenRecommendationCoverItemId,
                  onLinkedVideoClick = { linkedVideo, bounds ->
                    commentMediaBounds[linkedVideo.id] = bounds
                    onRecommendationClick(linkedVideo, bounds, bounds, false)
                  },
                  onLinkedVideoBoundsChanged = { linkedVideo, bounds ->
                    commentMediaBounds[linkedVideo.id] = bounds
                  },
                  onLinkedVideoLongClick = onRecommendationLongClick,
                  onTimestampClick = { seconds, _ ->
                    onSeek((seconds * 1000L).coerceIn(0L, durationMs))
                  },
                  onLinkedArticleClick = onArticleClick,
                  hiddenLinkedArticleItemId = hiddenLinkedArticleItemId,
                  deletionSelectedRpid = deleteCandidate?.rpid,
                  canDeleteComment = ::canDeleteComment,
                  onDeleteRequest = { comment, bounds ->
                    deleteCandidateBounds = bounds
                    deleteCandidate = comment
                  },
                  onDeleteConfirm = { comment ->
                    deleteCandidate = null
                    onDeleteComment(comment)
                  },
                  onDeleteCancel = { deleteCandidate = null },
                  onDeletionBoundsChanged = { deleteCandidateBounds = it },
                  pinActionAvailable =
                    currentAccountMid > 0L && currentAccountMid == currentUploaderMid,
                  pinActionLabel = { comment -> if (comment.isPinned) "取消置顶" else "置顶" },
                  onPinRequest = {
                    deleteCandidate = null
                    onToggleCommentPin(it)
                  },
                  controlEnabled = controlModeEnabled,
                  controlInitialFocusRequester = controlReplyEntryFocusRequester,
                  modifier = Modifier.fillMaxSize(),
                )
              }
            }
          }
        }
      },
    )
    val commentComposerVisible =
      commentPaneBounds.width > 0f && !controllerOwnsInteraction
    AnimatedVisibility(
      visible = commentComposerVisible,
      modifier =
        if (longCommentOverlay) {
          Modifier.align(Alignment.BottomEnd).fillMaxSize().zIndex(40f)
        } else {
          Modifier.align(Alignment.BottomEnd)
            .width(with(density) { commentPaneBounds.width.toDp() })
            .zIndex(20f)
        },
      enter =
        fadeIn(tween(if (settings.reduceMotion) 70 else 170)) +
          slideInVertically(tween(if (settings.reduceMotion) 70 else 190)) { it / 5 },
      exit =
        fadeOut(tween(if (settings.reduceMotion) 60 else 130)) +
          slideOutVertically(tween(if (settings.reduceMotion) 60 else 150)) { it / 5 },
    ) {
      CommentComposer(
        emotes = emotes,
        emotePackages = emotePackages,
        mentionSuggestions = mentionSuggestions,
        mentionSuggestionsLoading = mentionSuggestionsLoading,
        onMentionQuery = onMentionQuery,
        targetName = replyTarget?.name,
        onClearTarget = {
          replyTargetRoot = null
          replyTarget = null
        },
        imageEnabled = commentImageEnabled,
        onSend = { message, imageUri ->
          val target = replyTarget
          if (target == null) onPostComment(message, imageUri)
          else {
            onPostReply(replyTargetRoot ?: target, target, message, imageUri)
            replyTargetRoot = null
            replyTarget = null
          }
        },
        onDetachedModeChanged = { detached ->
          if (detached != longCommentOverlay) {
            longCommentOverlay = detached
            if (detached) {
              resumePlaybackAfterLongComment = isPlaying
              if (isPlaying) onPlayPause()
            } else {
              if (resumePlaybackAfterLongComment && !isPlaying) onPlayPause()
              resumePlaybackAfterLongComment = false
            }
          }
        },
        modifier =
          if (longCommentOverlay) {
            Modifier.fillMaxSize()
              .navigationBarsPadding()
              .imePadding()
              .onGloballyPositioned { commentComposerBounds = it.boundsInRoot() }
          } else {
            Modifier.navigationBarsPadding()
              .imePadding()
              .padding(12.dp)
              .onGloballyPositioned { commentComposerBounds = it.boundsInRoot() }
          },
      )
    }
  }
  LaunchedEffect(replyRoot) {
    val latest = replyRoot
    if (latest != null && displayedReplyRoot?.rpid == latest.rpid && !replyClosing) {
      displayedReplyRoot = latest
    }
  }
}

internal data class CommentChromeVisibility(
  val showDockedActions: Boolean = true,
  val showSortControls: Boolean = true,
)

/** 保留每个视频的 UI 状态，让恢复父页时绝不改变飞行中的评论几何。 */
internal class CommentChromeState {
  val visibility = mutableStateOf(CommentChromeVisibility())
  val keepHiddenAtTop = mutableStateOf(false)
  val floatingActionsExpanded = mutableStateOf(false)
}

internal data class CommentScrollPosition(val index: Int, val offset: Int) {
  val isAtTop: Boolean
    get() = index == 0 && offset == 0
}

private data class CommentScrollSnapshot(
  val position: CommentScrollPosition,
  val scrolling: Boolean,
)

internal fun commentChromeAfterScroll(
  currentVisibility: CommentChromeVisibility,
  previous: CommentScrollPosition,
  current: CommentScrollPosition,
): CommentChromeVisibility {
  if (current.isAtTop) return CommentChromeVisibility()

  val movingDown =
    current.index > previous.index ||
      (current.index == previous.index && current.offset > previous.offset)
  if (movingDown) {
    return CommentChromeVisibility(showDockedActions = false, showSortControls = false)
  }

  val movingUp =
    current.index < previous.index ||
      (current.index == previous.index && current.offset < previous.offset)
  return if (movingUp) {
    CommentChromeVisibility(showDockedActions = false, showSortControls = true)
  } else {
    currentVisibility
  }
}

/** 即使空/短评论列表没有滚动范围，也处理竖向手势意图。 */
internal fun commentChromeAfterViewportSwipe(
  currentVisibility: CommentChromeVisibility,
  fingerDeltaY: Float,
  listIsAtTop: Boolean,
): CommentChromeVisibility =
  when {
    fingerDeltaY < 0f ->
      CommentChromeVisibility(showDockedActions = false, showSortControls = false)
    fingerDeltaY > 0f && listIsAtTop -> CommentChromeVisibility()
    fingerDeltaY > 0f -> CommentChromeVisibility(showDockedActions = false, showSortControls = true)
    else -> currentVisibility
  }

/** 在真实顶部恢复操作行，同时保留有意的短列表收起。 */
internal fun commentChromeAfterObservedScroll(
  currentVisibility: CommentChromeVisibility,
  scrolling: Boolean,
  direction: Int,
  isAtTop: Boolean,
  keepHiddenAtTop: Boolean,
): CommentChromeVisibility =
  when {
    isAtTop && keepHiddenAtTop -> currentVisibility
    isAtTop -> CommentChromeVisibility()
    scrolling && direction > 0 ->
      CommentChromeVisibility(showDockedActions = false, showSortControls = false)
    // 向下的列表抖动、惯性停稳与过滚动回弹都可能报出一像素的反向移动：恢复因此
    // 由视口手势检测器掌控，需要刻意的 32dp 下拉才改变控制条。
    scrolling && direction < 0 -> currentVisibility
    else -> currentVisibility
  }

@Composable
private fun FloatingVideoActions(
  expanded: Boolean,
  info: VideoInfo?,
  engagement: VideoEngagement,
  loggedIn: Boolean,
  favoriteFolders: List<FavoriteFolder>,
  favoriteFoldersLoading: Boolean,
  reduceMotion: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  onLike: (Boolean) -> Unit,
  onCoin: (Int, Boolean) -> Unit,
  onFavorite: (List<Long>, List<Long>) -> Unit,
  onLoadFavoriteFolders: () -> Unit,
  onLogin: () -> Unit,
  foregroundColor: Color,
  modifier: Modifier = Modifier,
) {
  val quietInteraction = remember { MutableInteractionSource() }
  val shortDuration = if (reduceMotion) 70 else 150
  val longDuration = if (reduceMotion) 90 else 240
  Box(modifier) {
    if (expanded) {
      Box(
        Modifier.fillMaxSize()
          .clickable(
            interactionSource = quietInteraction,
            indication = null,
            onClick = { onExpandedChange(false) },
          )
      )
    }

    AnimatedVisibility(
      visible = !expanded,
      modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
      enter =
        scaleIn(
          initialScale = .35f,
          transformOrigin = TransformOrigin(.5f, 0f),
          animationSpec = tween(longDuration),
        ) + fadeIn(tween(shortDuration)),
      exit =
        scaleOut(
          targetScale = .35f,
          transformOrigin = TransformOrigin(.5f, 0f),
          animationSpec = tween(shortDuration),
        ) + fadeOut(tween(shortDuration)),
    ) {
      Box(
        modifier =
          Modifier.size(48.dp)
            .clickable(
              onClickLabel = "展开点赞、投币和收藏",
              role = Role.Button,
              onClick = { onExpandedChange(true) },
            ),
        contentAlignment = Alignment.Center,
      ) {
        Surface(
          modifier = Modifier.width(44.dp).height(32.dp),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = .90f),
          contentColor = foregroundColor,
          border =
            androidx.compose.foundation.BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(width = 20.dp, height = 12.dp)) {
              val triangle =
                Path().apply {
                  // 让每条边距都离开像素边界，避免抗锯齿裁掉一侧让小指示器显得
                  // 倾斜。
                  moveTo(size.width * .12f, size.height * .18f)
                  lineTo(size.width * .88f, size.height * .18f)
                  lineTo(size.width * .5f, size.height * .84f)
                  close()
                }
              val outlineColor =
                if (foregroundColor.luminance() > .5f) Color.Black.copy(alpha = .58f)
                else Color.White.copy(alpha = .74f)
              drawPath(
                path = triangle,
                color = outlineColor,
                style =
                  androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.2.dp.toPx(),
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                  ),
              )
              drawPath(triangle, foregroundColor)
            }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = expanded,
      modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
      enter =
        scaleIn(
          initialScale = .16f,
          transformOrigin = TransformOrigin(.5f, 0f),
          animationSpec = tween(longDuration, easing = FastOutSlowInEasing),
        ) + fadeIn(tween(shortDuration)),
      exit =
        scaleOut(
          targetScale = .16f,
          transformOrigin = TransformOrigin(.5f, 0f),
          animationSpec = tween(shortDuration),
        ) + fadeOut(tween(shortDuration)),
    ) {
      Box(
        Modifier.fillMaxWidth()
          .clickable(
            interactionSource = quietInteraction,
            indication = null,
            onClick = {},
          )
      ) {
        VideoActionPanel(
          info = info,
          engagement = engagement,
          loggedIn = loggedIn,
          favoriteFolders = favoriteFolders,
          favoriteFoldersLoading = favoriteFoldersLoading,
          onLike = onLike,
          onCoin = onCoin,
          onFavorite = onFavorite,
          onLoadFavoriteFolders = onLoadFavoriteFolders,
          onLogin = onLogin,
          foregroundColor = foregroundColor,
        )
      }
    }
  }
}

@Composable
private fun CommentSectionHeader(
  commentCount: Long,
  commentSort: CommentSort,
  onCommentSort: (CommentSort) -> Unit,
  foregroundColor: Color,
  controlEnabled: Boolean = false,
  controlFirstFocusRequester: FocusRequester? = null,
  controlUpFocusRequester: FocusRequester? = null,
  controlDownFocusRequester: FocusRequester? = null,
) {
  Surface(
    color = Color.Transparent,
    contentColor = foregroundColor,
    tonalElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        "评论  ${formatCompactCount(commentCount)}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
        color = foregroundColor,
      )
      CommentSortControls(
        commentSort = commentSort,
        onCommentSort = onCommentSort,
        controlEnabled = controlEnabled,
        controlFirstFocusRequester = controlFirstFocusRequester,
        controlUpFocusRequester = controlUpFocusRequester,
        controlDownFocusRequester = controlDownFocusRequester,
      )
    }
  }
}

@Composable
private fun CommentSortControls(
  commentSort: CommentSort,
  onCommentSort: (CommentSort) -> Unit,
  controlEnabled: Boolean = false,
  controlFirstFocusRequester: FocusRequester? = null,
  controlUpFocusRequester: FocusRequester? = null,
  controlDownFocusRequester: FocusRequester? = null,
) {
  val focusRequesters =
    remember(controlFirstFocusRequester) {
      CommentSort.entries.mapIndexed { index, _ ->
        if (index == 0 && controlFirstFocusRequester != null) controlFirstFocusRequester
        else FocusRequester()
      }
    }
  CommentSort.entries.forEachIndexed { index, sort ->
    FilterChip(
      selected = commentSort == sort,
      onClick = { onCommentSort(sort) },
      label = { Text(sort.label, maxLines = 1) },
      colors =
        FilterChipDefaults.filterChipColors(
          containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .30f),
          labelColor = MaterialTheme.colorScheme.onBackground,
          selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .76f),
          selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
      modifier =
        Modifier.then(
          if (controlEnabled) {
            Modifier.focusRequester(focusRequesters[index])
              .focusProperties {
                left =
                  if (index == 0) FocusRequester.Cancel else focusRequesters[index - 1]
                right =
                  if (index == focusRequesters.lastIndex) FocusRequester.Cancel
                  else focusRequesters[index + 1]
                up = controlUpFocusRequester ?: FocusRequester.Cancel
                down = controlDownFocusRequester ?: FocusRequester.Cancel
              }
              .controlFocusOutline(
                RoundedCornerShape(16.dp),
                MaterialTheme.colorScheme.primary,
                enabled = true,
              )
          } else Modifier
        ),
    )
  }
}

internal data class VideoPageLayout(
  val playerWidth: Dp,
  val playerHeight: Dp,
  val recommendationCardWidth: Dp,
  val compactHorizontalRecommendations: Boolean,
  val compactRecommendationCardHeight: Dp,
)

internal fun videoPageLayoutForPane(
  primaryWidthDp: Float,
  primaryHeightDp: Float,
  fontScale: Float = 1f,
): VideoPageLayout {
  if (primaryWidthDp <= 0f || primaryHeightDp <= 0f) {
    return VideoPageLayout(0.dp, 0.dp, 232.dp, false, 68.dp)
  }
  val scale = fontScale.coerceIn(.85f, 2f)
  val idealPlayerHeight = primaryWidthDp * 9f / 16f
  val minimumVerticalRailHeight = 207f
  val compactHorizontalRecommendations =
    primaryHeightDp < 360f || primaryHeightDp < idealPlayerHeight + minimumVerticalRailHeight
  if (compactHorizontalRecommendations) {
    val headerHeight = 36f + (scale - 1f).coerceAtLeast(0f) * 45f
    val bottomClearance = 4f
    // 矮窗格用横向卡，但保留足够高度容纳真正的 16:9 缩略图与两行可读文本：宽度
    // 保持连续，让 4:3、16:10、分屏与自由窗口都落在平衡的可见卡数量上，无需按
    // 设备分支。
    val minimumCardHeight = 84f + (scale - 1f).coerceAtLeast(0f) * 30f
    val maximumCardHeight = 156f + (scale - 1f).coerceAtLeast(0f) * 30f
    val heightAvailableBelowIdealPlayer =
      primaryHeightDp - idealPlayerHeight - headerHeight - bottomClearance
    val cardHeight = heightAvailableBelowIdealPlayer.coerceIn(minimumCardHeight, maximumCardHeight)
    val playerHeight =
      minOf(
          idealPlayerHeight,
          primaryHeightDp - headerHeight - cardHeight - bottomClearance,
        )
        .coerceAtLeast(96f)
    val playerWidth = minOf(primaryWidthDp, playerHeight * 16f / 9f)
    val thumbnailWidth = (cardHeight - 12f).coerceAtLeast(0f) * 16f / 9f
    val minimumTextWidth = 132f + (scale - 1f).coerceAtLeast(0f) * 60f
    val cardWidth =
      maxOf(primaryWidthDp * .46f, thumbnailWidth + minimumTextWidth + 20f).coerceIn(268f, 440f)
    return VideoPageLayout(
      playerWidth.dp,
      playerHeight.dp,
      cardWidth.dp,
      true,
      cardHeight.dp,
    )
  }
  val recommendationHeaderHeight = 36f
  val bottomClearance = 4f
  val desiredCardWidth = (primaryWidthDp * .34f).coerceIn(160f, 312f)
  fun cardChrome(width: Float) = if (width < 210f) 81f else 89f

  val initialChrome = cardChrome(desiredCardWidth)
  val availableAtIdeal =
    primaryHeightDp - idealPlayerHeight - recommendationHeaderHeight - bottomClearance
  val cardWidthAtIdeal =
    minOf(desiredCardWidth, (availableAtIdeal - initialChrome) * 16f / 9f)
      .coerceIn(160f, desiredCardWidth)
  val chrome = cardChrome(cardWidthAtIdeal)
  val playerHeight =
    minOf(
        idealPlayerHeight,
        primaryHeightDp -
          recommendationHeaderHeight -
          bottomClearance -
          chrome -
          cardWidthAtIdeal * 9f / 16f,
      )
      .coerceAtLeast(0f)
  val playerWidth = minOf(primaryWidthDp, playerHeight * 16f / 9f)
  val remainingForCards =
    primaryHeightDp - playerHeight - recommendationHeaderHeight - bottomClearance
  val finalCardWidth =
    minOf(desiredCardWidth, (remainingForCards - chrome) * 16f / 9f)
      .coerceIn(160f, desiredCardWidth)
  return VideoPageLayout(playerWidth.dp, playerHeight.dp, finalCardWidth.dp, false, 68.dp)
}

internal fun bangumiPageLayoutForPane(
  primaryWidthDp: Float,
  primaryHeightDp: Float,
  fontScale: Float = 1f,
): VideoPageLayout {
  if (primaryWidthDp <= 0f || primaryHeightDp <= 0f) {
    return VideoPageLayout(0.dp, 0.dp, 232.dp, true, 68.dp)
  }
  val scale = fontScale.coerceIn(.85f, 1.6f)
  val minimumLowerHeight = 158f + (scale - 1f).coerceAtLeast(0f) * 44f
  val desiredLowerHeight = (primaryHeightDp * .37f).coerceIn(minimumLowerHeight, 258f)
  val idealPlayerHeight = primaryWidthDp * 9f / 16f
  val playerHeight =
    minOf(idealPlayerHeight, primaryHeightDp - desiredLowerHeight - 8f).coerceAtLeast(112f)
  val playerWidth = minOf(primaryWidthDp, playerHeight * 16f / 9f)
  return VideoPageLayout(playerWidth.dp, playerHeight.dp, 232.dp, true, 68.dp)
}

internal fun recommendationCardWidthForPane(primaryWidthDp: Float, primaryHeightDp: Float) =
  videoPageLayoutForPane(primaryWidthDp, primaryHeightDp).recommendationCardWidth

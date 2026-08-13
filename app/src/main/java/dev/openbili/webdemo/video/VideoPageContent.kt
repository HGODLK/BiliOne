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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import dev.openbili.webdemo.ui.SessionPhase
import dev.openbili.webdemo.ui.StableBoundsTracker
import dev.openbili.webdemo.ui.TransitionPreparationBarrier
import dev.openbili.webdemo.ui.TransitionReadySignal
import dev.openbili.webdemo.ui.VideoShapeTokens
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
  onBangumiEpisodeSelected: (dev.openbili.webdemo.api.BangumiEpisode) -> Unit = {},
  onBangumiSeasonSelected: (Long) -> Unit = {},
  modifier: Modifier = Modifier,
  onPlayerBoundsChanged: (Rect) -> Unit,
) {
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
  var replyDismissRequested by remember(item.id) { mutableStateOf(false) }
  var replyPreparation by
    remember(item.id) {
      mutableStateOf<TransitionPreparationBarrier?>(null)
    }
  var contentBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  var longCommentOverlay by remember(item.id) { mutableStateOf(false) }
  var resumePlaybackAfterLongComment by remember(item.id) { mutableStateOf(false) }
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
  LaunchedEffect(replyRoot?.rpid, replyDismissRequested) {
    val root = replyRoot
    if (replyDismissRequested && displayedReplyRoot != null) {
      replyPreparation?.cancel()
      replyPreparation = null
      replyClosing = true
      // Stop drawing the expensive reply LazyColumn before the contraction. The lightweight
      // surface below remains visible, so the close still reads as one continuous transition.
      replyContentReady = false
      // Keep only the lightweight panel mounted through the contraction. Recording or transforming
      // the full reply list caused a long main-thread draw and made the animation jump to its end.
      replyTransitionProgress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 80 else 260, easing = FastOutSlowInEasing),
      )
      delay(16)
      displayedReplyRoot = null
      replyClosing = false
      onDismissReplies()
      replyDismissRequested = false
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
      // The reply target is the already-laid-out comment pane. Do not let a stale/invalid bounds
      // callback hold a user tap behind the global transition timeout.
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
      replyPreparation = null
    }
  }
  BackHandler(enabled = deleteCandidate != null) { deleteCandidate = null }
  BackHandler(
    enabled = displayedReplyRoot != null && deleteCandidate == null
  ) {
    if (!replyClosing) replyDismissRequested = true
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
        // ── LEFT: Player + info + recommendations ──────────────────────────
        Column(modifier = Modifier.fillMaxSize().onSizeChanged { primaryPaneSize = it }) {
          // The measured pane height is shared between the player and the recommendation rail.
          // This prevents a full-width 16:9 player from pushing the rail outside short windows.
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
                        .zIndex(.5f),
                  ) {
                    CoverImage(
                      coverUrl = item.coverUrl,
                      modifier = Modifier.fillMaxSize(),
                      shape = VideoShapeTokens.Player,
                      contentScale =
                        if (bangumiPage != null)
                          androidx.compose.ui.layout.ContentScale.Fit
                        else androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    if (dimLoadingCover) {
                      // This shade belongs to the temporary SDR cover layer. It fades out with the
                      // cover and never touches the decoded HDR/Dolby SurfaceView underneath.
                      Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .62f)))
                    }
                  }
                }

                // Small loading circle when buffering at start.
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
              onEpisodeSelected = onBangumiEpisodeSelected,
              onSeasonSelected = onBangumiSeasonSelected,
              panelSlideProgress = panelSlideProgress,
              glassBackdrop = glassBackdrop,
              foregroundColor = pageForegroundColor,
              modifier = Modifier.weight(1f),
            )
          } else {
            // Recommendations (horizontal scroll)
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
                  CompositionLocalProvider(
                    LocalFeedImageLoadPolicy provides recommendationImageLoadPolicy
                  ) {
                    LazyRow(
                      state = recommendationListState,
                      contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      items(recommendations.take(20), key = { it.id }) { rec ->
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
          // Preserve the pane's real constraint-derived geometry for the player target without
          // mounting or drawing comments while the shared cover owns the frame budget.
          Box(
            Modifier.fillMaxSize().onGloballyPositioned {
              commentExpandedBounds = it.boundsInRoot()
            }
          )
        } else {
          // ── RIGHT: Comments ───────────────────────────────────────────────
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
              // Once the reply surface is nearly opaque, stop compositing the comment list behind
              // it. Keeping this render-only avoids recomposing either LazyColumn during motion.
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
                  if (commentListState.layoutInfo.totalItemsCount > 0)
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
                          items(
                            items = commentItems,
                            key = { it.rpid },
                            contentType = { "comment" },
                          ) { comment ->
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
                                  onRecommendationClick(linkedVideo, bounds, bounds, false)
                                },
                                onLinkedVideoLongClick = onRecommendationLongClick,
                                onLinkedArticleClick = onArticleClick,
                                hiddenLinkedArticleItemId = hiddenLinkedArticleItemId,
                                deletionSelected = deleteCandidate?.rpid == comment.rpid,
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
                              )
                            }
                          }
                          if (commentsLoading) {
                            item {
                              Box(
                                Modifier.fillMaxWidth().padding(8.dp),
                                contentAlignment = Alignment.Center,
                              ) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
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
                  if (!replyClosing) replyDismissRequested = true
                },
                bottomClearancePx = commentComposerBounds.height,
                hiddenCommentAvatarRpid = hiddenCommentAvatarRpid,
                hiddenLinkedVideoCoverItemId = hiddenRecommendationCoverItemId,
                onLinkedVideoClick = { linkedVideo, bounds ->
                  onRecommendationClick(linkedVideo, bounds, bounds, false)
                },
                onLinkedVideoLongClick = onRecommendationLongClick,
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
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
        }
        }
      },
    )
    if (commentPaneBounds.width > 0f) {
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
            Modifier.align(Alignment.BottomEnd)
              .fillMaxSize()
              .navigationBarsPadding()
              .imePadding()
              .zIndex(40f)
              .onGloballyPositioned { commentComposerBounds = it.boundsInRoot() }
          } else {
            Modifier.align(Alignment.BottomEnd)
              .width(with(density) { commentPaneBounds.width.toDp() })
              .navigationBarsPadding()
              .imePadding()
              .padding(12.dp)
              .zIndex(20f)
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

/** Retained per-video UI state so restoring a parent never changes comment geometry mid-flight. */
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

/** Handles vertical intent even when an empty or short comment list has no scroll range. */
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

/**
 * Prevents a short list's top-edge rebound from immediately undoing an intentional upward swipe.
 */
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
    // Downward list jitter, fling settling and overscroll rebound can all report a one-pixel
    // reverse movement. Restoring is therefore owned by the viewport gesture detector, which
    // requires a deliberate 32dp downward drag before changing the chrome.
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
                  // Keep every edge inset from the pixel boundary so antialiasing cannot clip
                  // one side and make the small indicator appear tilted.
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
      )
    }
  }
}

@Composable
private fun CommentSortControls(
  commentSort: CommentSort,
  onCommentSort: (CommentSort) -> Unit,
) {
  CommentSort.entries.forEach { sort ->
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
    // Short panes use a horizontal card, but keep enough height for a true 16:9 thumbnail and
    // two readable text lines. Width remains continuous so 4:3, 16:10, split, and freeform
    // windows all land on a balanced number of visible cards without device-specific branches.
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

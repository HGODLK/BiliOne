package dev.openbili.webdemo.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import dev.openbili.webdemo.AuthViewModel
import dev.openbili.webdemo.BangumiPlaybackStore
import dev.openbili.webdemo.LoginState
import dev.openbili.webdemo.MainViewModel
import dev.openbili.webdemo.PlaybackProgressStore
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.R
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.ArticleDetail
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.BangumiSection
import dev.openbili.webdemo.api.BangumiExploreSectionKind
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.CommentNavigationTarget
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.DanmakuMaskParser
import dev.openbili.webdemo.api.DanmakuMaskTimeline
import dev.openbili.webdemo.api.MessageTargetKind
import dev.openbili.webdemo.api.RiskControlManager
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.BangumiIndexItem
import dev.openbili.webdemo.api.VideoEngagement
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.api.commentTimeHasMore
import dev.openbili.webdemo.api.commentTimeNextPage
import dev.openbili.webdemo.api.commentTimeStartPage
import dev.openbili.webdemo.api.orderCommentsByTime
import dev.openbili.webdemo.article.ArticleOrigin
import dev.openbili.webdemo.article.ArticleScreen
import dev.openbili.webdemo.article.ArticleStackFrame
import dev.openbili.webdemo.article.ArticleTransitionOverlay
import dev.openbili.webdemo.article.ArticleTransitionSession
import dev.openbili.webdemo.bangumi.BangumiRecommendationViewModel
import dev.openbili.webdemo.bangumi.BangumiExploreViewModel
import dev.openbili.webdemo.bangumi.BangumiIndexViewModel
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.CoverImageRequestFactory
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedScrollAnchor
import dev.openbili.webdemo.feed.FeedPerformanceConfig
import dev.openbili.webdemo.feed.FeedScreen
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import dev.openbili.webdemo.feed.LocalCoverImageLoadingEnabled
import dev.openbili.webdemo.my.MyScreen
import dev.openbili.webdemo.my.MyViewModel
import dev.openbili.webdemo.my.ProfilePrivateConversationPane
import dev.openbili.webdemo.search.SearchResultsScreen
import dev.openbili.webdemo.search.SearchScreen
import dev.openbili.webdemo.search.SearchViewModel
import dev.openbili.webdemo.settings.AppSettingsViewModel
import dev.openbili.webdemo.settings.PreferredResolutionMode
import dev.openbili.webdemo.video.CommentProfileAnchor
import dev.openbili.webdemo.video.BangumiPageUi
import dev.openbili.webdemo.video.VideoInfoTile
import dev.openbili.webdemo.video.VideoScreen
import dev.openbili.webdemo.video.bangumiPageLayoutForPane
import dev.openbili.webdemo.video.videoPaneSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val playerTransitionRequiredSignals =
  setOf(
    TransitionReadySignal.SOURCE_BOUNDS,
    TransitionReadySignal.IMAGE_READY,
    TransitionReadySignal.TARGET_MOUNTED,
    TransitionReadySignal.TARGET_BOUNDS_STABLE,
  )

private val exitTransitionRequiredSignals =
  setOf(
    TransitionReadySignal.SOURCE_BOUNDS,
    TransitionReadySignal.SOURCE_SNAPSHOT,
    TransitionReadySignal.IMAGE_READY,
    TransitionReadySignal.TARGET_MOUNTED,
    TransitionReadySignal.TARGET_BOUNDS_STABLE,
  )

private const val NESTED_PROFILE_HEADER_FADE_OUT_MS = 140L

private enum class RootPlayerSurfaceRole {
  IDLE,
  PREVIEW_PENDING,
  PREVIEW,
  DETAIL_PENDING,
  DETAIL,
  EXIT_COVERED,
}

private data class RootPlayerOwnership(
  val role: RootPlayerSurfaceRole,
  val mediaId: String? = null,
)

private data class SharedPlayerHostConfig(
  val modifier: Modifier,
  val fullscreenProgress: Float,
  val fullscreen: Boolean,
  val danmakuAllowed: Boolean,
)

private enum class SharedPlayerViewRole {
  PREVIEW,
  DETAIL,
}

private data class HeldPlayerView(
  val role: SharedPlayerViewRole,
  val view: PlayerView,
)

internal fun shouldPositionBangumiPreviewPortal(
  previewOwned: Boolean,
  boundsUsable: Boolean,
  previewPortalVisible: Boolean,
): Boolean = previewOwned && boundsUsable && previewPortalVisible

internal fun shouldUseRootPlayerHost(
  startupWarmupVisible: Boolean,
  bangumiRootPageActive: Boolean,
  hasBangumiHomeTransition: Boolean,
): Boolean =
  !startupWarmupVisible && (bangumiRootPageActive || hasBangumiHomeTransition)

internal fun shouldActivateBangumiRootPage(
  selectedTab: RootTab,
  settledPage: Int,
  pageSwitchInProgress: Boolean,
  videoScreenVisible: Boolean,
): Boolean =
  selectedTab == RootTab.BANGUMI &&
    settledPage == RootTab.BANGUMI.ordinal &&
    !pageSwitchInProgress &&
    !videoScreenVisible

internal fun shouldSuppressDetailPlayerForBangumiCardTransition(
  kind: TransitionKind,
  phase: SessionPhase,
): Boolean =
  when (kind) {
    TransitionKind.ENTER_ROOT ->
      phase == SessionPhase.PREPARING ||
        phase == SessionPhase.READY ||
        phase == SessionPhase.FLYING
    TransitionKind.EXIT_ROOT ->
      phase == SessionPhase.FLYING || phase == SessionPhase.REVEALING_BACKGROUND
    else -> false
  }

@Composable
private fun CachedBangumiTransitionCover(
  coverUrl: String,
  modifier: Modifier = Modifier,
) {
  val bitmap =
    LoadedFeedImageRegistry.bitmap(bangumiPreviewCoverCacheKey(coverUrl))
      ?: LoadedFeedImageRegistry.bitmap(coverUrl)
  if (bitmap != null) {
    Image(
      bitmap = bitmap.asImageBitmap(),
      contentDescription = null,
      modifier = modifier,
      contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    )
  } else {
    Box(modifier.background(Color.Black))
  }
}

@Composable
private fun RootPlayerLayer(
  hostEnabled: Boolean,
  ownership: RootPlayerOwnership,
  previewBounds: Rect,
  previewCoverAlpha: () -> Float,
  previewCoverBlend: BangumiPreviewCoverBlend?,
  previewGestureVisualActive: Boolean,
  previewPortalVisible: Boolean,
  previewImageLoadingEnabled: Boolean,
  previewTarget: BangumiPreviewTarget?,
  layerItem: FeedItem?,
  playerContent: @Composable (SharedPlayerHostConfig) -> Unit,
) {
  if (!hostEnabled) return
  val density = LocalDensity.current
  // The preview portal owns the physical host while its media id is being switched. Requiring the
  // old ownership id to already match the new target briefly parks the SurfaceView at 1 x 1 before
  // the ownership effect can catch up, which is visible as a positional flash.
  val previewOwned =
    ownership.role in setOf(RootPlayerSurfaceRole.PREVIEW_PENDING, RootPlayerSurfaceRole.PREVIEW)
  // PREVIEW_PENDING must already receive the real preview bounds. Waiting for the first frame
  // before sizing the SurfaceView creates a deadlock on devices that do not render a 1 px parked
  // surface. A cover remains above it until that first frame is reported.
  val previewPositioned =
    shouldPositionBangumiPreviewPortal(
      previewOwned = previewOwned,
      boundsUsable = previewBounds.hasUsableSize(),
      previewPortalVisible = previewPortalVisible,
    )
  val bounds = if (previewPositioned) previewBounds else Rect.Zero
  val contentVisible = bounds.hasUsableSize()
  // SurfaceView cannot be parked outside the window: on Samsung's SurfaceControl implementation
  // an off-screen parent may stay in SurfaceFlinger's Offscreen Hierarchy after the Compose view
  // returns. A one-pixel on-screen host keeps the one surface attached without exposing content.
  val hostBounds =
    if (contentVisible) bounds
    else Rect(0f, 0f, 1f, 1f)

  Box(
    Modifier.offset { IntOffset(hostBounds.left.roundToInt(), hostBounds.top.roundToInt()) }
      .size(
        width = with(density) { hostBounds.width.toDp() },
        height = with(density) { hostBounds.height.toDp() },
      )
      .clip(VideoShapeTokens.Player)
  ) {
    if (contentVisible) {
      VideoCardGradient(
        coverUrl = layerItem?.coverUrl.orEmpty(),
        modifier = Modifier.fillMaxSize(),
        loadKey = "root-player-background:${layerItem?.id.orEmpty()}",
      ) {
        Box(
          Modifier.matchParentSize().background(Color.Black.copy(alpha = .36f))
        )
      }
    }
    // Keep the sole AndroidView mounted after warmup. Parking it off-screen avoids the stale
    // SurfaceView buffer size seen when a PV-sized host was detached and later reused by a larger
    // homepage player.
    playerContent(
      SharedPlayerHostConfig(
        modifier = Modifier.fillMaxSize(),
        fullscreenProgress = 0f,
        fullscreen = false,
        danmakuAllowed = false,
      )
    )
    if (contentVisible && previewPositioned) {
      Box(
        Modifier.fillMaxSize().graphicsLayer {
          alpha = previewCoverAlpha().coerceIn(0f, 1f)
        }
      ) {
        if (previewGestureVisualActive && previewCoverBlend != null) {
          CachedBangumiTransitionCover(
            coverUrl = previewCoverBlend.fromCoverUrl,
            modifier = Modifier.fillMaxSize(),
          )
          CachedBangumiTransitionCover(
            coverUrl = previewCoverBlend.toCoverUrl,
            modifier =
              Modifier.fillMaxSize().graphicsLayer { alpha = previewCoverBlend.progress },
          )
        } else {
          CoverImage(
            coverUrl =
              previewTarget?.item?.coverUrl?.ifBlank { layerItem?.coverUrl.orEmpty() }
                ?: layerItem?.coverUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            shape = VideoShapeTokens.Player,
            enforceAspectRatio = false,
            requestWidth = 1600,
            requestHeight = 900,
            loadKey = "root-player-preview-pending:${previewTarget?.item?.id.orEmpty()}",
            bitmapCacheKey =
              bangumiPreviewCoverCacheKey(
                previewTarget?.item?.coverUrl?.ifBlank { layerItem?.coverUrl.orEmpty() }
                  ?: layerItem?.coverUrl.orEmpty()
              ),
            alwaysLoad = true,
            loadingEnabled = previewImageLoadingEnabled,
            retainBitmap = true,
            fadeIn = false,
          )
        }
      }
    }
  }
}

private data class ActiveBangumiPage(
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
 * 1. [card.watchProgress] with valid episodeId → server-recorded episode and position.
 * 2. [localSelection] (when [allowLocalSelection]) → last-watched from BangumiPlaybackStore.
 * 3. Fallback → source card's default [card.episodeId] / new_ep.
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
      card = sourceCard.copy(
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
      card = sourceCard.copy(
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

private fun SpaceContentCard.toBangumiVideoItem(
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

private fun BangumiIndexItem.toIndexBangumiCard(): SpaceContentCard =
  SpaceContentCard(
    id = stableId,
    title = title,
    subtitle = indexShow.ifBlank { subtitle },
    coverUrl = coverUrl,
    videoUrl = targetUrl,
    seasonId = seasonId,
    episodeId = episodeId,
    kind = dev.openbili.webdemo.api.SpaceContentKind.BANGUMI,
    seasonType = seasonType,
  )

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
) {
  val appState by mainViewModel.state.collectAsState()
  val feedState by feedViewModel.state.collectAsState()
  val userInfo by feedViewModel.userInfo.collectAsState()
  val loginState by authViewModel.loginState.collectAsState()
  val authUserInfo by authViewModel.userInfo.collectAsState()
  val profileIpAuthorized by authViewModel.appAccessAuthorized.collectAsState()
  val playerState by playerViewModel.playerState.collectAsState()
  val renderedVideoId by playerViewModel.renderedVideoId.collectAsState()
  val myState by myViewModel.state.collectAsState()
  val profileMessageState by profileMessageViewModel.state.collectAsState()
  val searchState by searchViewModel.state.collectAsState()
  val settings by settingsViewModel.state.collectAsState()
  val bangumiRecommendationState by bangumiRecommendationViewModel.state.collectAsState()
  val bangumiExploreViewModel: BangumiExploreViewModel = viewModel()
  val bangumiIndexViewModel: BangumiIndexViewModel = viewModel()
  val bangumiIndexState by bangumiIndexViewModel.state.collectAsState()
  val riskChallenge by RiskControlManager.challenge.collectAsState()
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val rootDensity = LocalDensity.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val feedGridState = rememberLazyGridState()
  val searchGridState = rememberLazyGridState()
  val bangumiIndexGridState = rememberLazyGridState()
  // Feed geometry is an event-time reference, not UI state. Keeping it outside snapshot state
  // prevents every layout tick during a fling from invalidating AppRoot.
  val feedCardBounds = remember { mutableMapOf<String, Rect>() }
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
  val profileStateHolder = rememberSaveableStateHolder()
  var startupWarmupVisible by remember {
    mutableStateOf(FeedPerformanceConfig.startupWarmupMaskEnabled)
  }
  val startupWarmupAlpha = remember {
    Animatable(if (FeedPerformanceConfig.startupWarmupMaskEnabled) 1f else 0f)
  }
  val startupWarmupStartedAt = remember { android.os.SystemClock.elapsedRealtime() }
  var startupWarmupFadeInProgress by remember { mutableStateOf(false) }
  var bangumiStartupPreloadReady by remember {
    mutableStateOf(!FeedPerformanceConfig.startupWarmupMaskEnabled)
  }
  var rootTab by rememberSaveable { mutableStateOf(RootTab.HOME) }
  val rootPagerState =
    rememberPagerState(initialPage = rootTab.ordinal, pageCount = { RootTab.entries.size })
  var rootPageSwitchRequested by remember { mutableStateOf(false) }
  var rootPageSwitchRequestToken by remember { mutableStateOf(0L) }
  val latestMySection by rememberUpdatedState(myState.section)
  var showSearch by rememberSaveable { mutableStateOf(false) }
  var showSearchResults by rememberSaveable { mutableStateOf(false) }
  var showBangumiIndex by rememberSaveable { mutableStateOf(false) }
  var bangumiIndexTransitionDirection by remember { mutableStateOf<SearchTransitionDirection?>(null) }
  var bangumiIndexTransitionSourceBounds by remember { mutableStateOf(Rect.Zero) }
  val bangumiIndexTransitionProgress = remember { Animatable(0f) }
  val bangumiIndexTransitionMaskAlpha = remember { Animatable(1f) }
  val bangumiIndexTransitionScrimAlpha = remember { Animatable(0f) }
  var bangumiIndexTransitionJob by remember { mutableStateOf<Job?>(null) }
  var searchTransitionDirection by remember { mutableStateOf<SearchTransitionDirection?>(null) }
  var searchTransitionSourceBounds by remember { mutableStateOf(Rect.Zero) }
  var searchTransitionQuery by remember { mutableStateOf("") }
  val searchTransitionProgress = remember { Animatable(0f) }
  val searchTransitionMaskAlpha = remember { Animatable(1f) }
  val searchTransitionScrimAlpha = remember { Animatable(0f) }
  var searchTransitionJob by remember { mutableStateOf<Job?>(null) }
  var searchTransitionPreparation by remember {
    mutableStateOf<TransitionPreparationBarrier?>(null)
  }
  var transitionPhase by remember {
    mutableStateOf<TransitionPhase>(
      appState.selectedVideo?.let { TransitionPhase.Video(it, null) } ?: TransitionPhase.Feed
    )
  }
  var searchBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
  var transitionSession by remember { mutableStateOf<CardTransitionSession?>(null) }
  var transitionToken by remember { mutableStateOf(0L) }
  var hiddenFeedCoverItemId by remember { mutableStateOf<String?>(null) }
  var hiddenMyCoverItemId by remember { mutableStateOf<String?>(null) }
  var hiddenSearchCoverItemId by remember { mutableStateOf<String?>(null) }
  var hiddenBangumiIndexItemId by remember { mutableStateOf<String?>(null) }
  var hiddenBangumiRecommendationItemId by remember { mutableStateOf<String?>(null) }
  var hiddenArticleVideoCoverItemId by remember { mutableStateOf<String?>(null) }
  var hiddenRecommendationCoverItemId by remember { mutableStateOf<String?>(null) }
  var hiddenPlaybackEndRecommendationCoverItemId by remember {
    mutableStateOf<String?>(null)
  }
  var hiddenProfileCoverItemId by remember { mutableStateOf<String?>(null) }
  var profileVideoTransitionActive by remember { mutableStateOf(false) }
  var activeBangumiPage by remember { mutableStateOf<ActiveBangumiPage?>(null) }
  var bangumiPreviewTarget by remember { mutableStateOf<BangumiPreviewTarget?>(null) }
  var bangumiCardEnterPending by remember { mutableStateOf(false) }
  var bangumiPreviewMuted by rememberSaveable { mutableStateOf(true) }
  var rootPlayerOwnership by remember {
    mutableStateOf(RootPlayerOwnership(RootPlayerSurfaceRole.IDLE))
  }
  var bangumiPosterBounds by remember { mutableStateOf(Rect.Zero) }
  var deferSearchBangumiPageComposition by remember { mutableStateOf(false) }
  var deferBangumiIndexPageComposition by remember { mutableStateOf(false) }
  var deferBangumiHomePageComposition by remember { mutableStateOf(false) }
  var videoExitPrelude by remember { mutableStateOf<VideoExitPrelude?>(null) }
  var videoStack by remember { mutableStateOf<List<StackFrame>>(emptyList()) }
  var articleStack by remember { mutableStateOf<List<ArticleStackFrame>>(emptyList()) }
  var articleEntryToken by remember { mutableStateOf(0L) }
  var articleDetail by remember { mutableStateOf<ArticleDetail?>(null) }
  val articleDetailCache = remember { mutableStateMapOf<Long, ArticleDetail>() }
  var articleLoading by remember { mutableStateOf(false) }
  var articleError by remember { mutableStateOf<String?>(null) }
  var articleHeroBounds by remember { mutableStateOf(Rect.Zero) }
  var articleTransitionSession by remember { mutableStateOf<ArticleTransitionSession?>(null) }
  var articleTransitionJob by remember { mutableStateOf<Job?>(null) }
  var articleLoadToken by remember { mutableStateOf(0L) }
  var articleContentReady by remember { mutableStateOf(false) }
  var articleRestoringParentEntryId by remember { mutableStateOf<Long?>(null) }
  var articleSuspendedVideo by remember { mutableStateOf<SuspendedArticleVideo?>(null) }
  var hiddenMyArticleItemId by remember { mutableStateOf<String?>(null) }
  var hiddenSearchArticleItemId by remember { mutableStateOf<String?>(null) }
  var hiddenProfileArticleItemId by remember { mutableStateOf<String?>(null) }
  var hiddenVideoCommentArticleItemId by remember { mutableStateOf<String?>(null) }
  var hiddenArticleCommentArticleItemId by remember { mutableStateOf<String?>(null) }
  var pendingVideoCommentTarget by remember { mutableStateOf<CommentNavigationTarget?>(null) }
  var pendingArticleCommentTarget by remember { mutableStateOf<CommentNavigationTarget?>(null) }
  var commentNavigationRequestToken by remember { mutableStateOf(0L) }
  var interactionTargetLoadingId by remember { mutableStateOf<Long?>(null) }
  val articlePageAlpha = remember { Animatable(0f) }
  val videoState = remember { AppRootVideoState() }
  val videoEntryCache = videoState.videoEntryCache
  var dataCommitAllowedId by remember { mutableStateOf(appState.selectedVideo?.id) }
  var playerActivationId by remember { mutableStateOf(appState.selectedVideo?.id) }
  var showEmbeddedCover by remember { mutableStateOf(appState.selectedVideo != null) }
  var playerBounds by remember { mutableStateOf(Rect.Zero) }
  var videoPageDataReadyId by remember { mutableStateOf<String?>(null) }
  var activeTransitionJob by remember { mutableStateOf<Job?>(null) }
  var activeRevealJob by remember { mutableStateOf<Job?>(null) }
  val directHomeAlpha = remember { Animatable(1f) }
  val bangumiSeasonExitFadeAlpha = remember { Animatable(0f) }
  var directHomeInProgress by remember { mutableStateOf(false) }
  // ── Video-page local state ───────────────────────────────────────────
  var videoRecommendations by videoState::videoRecommendations
  var videoDescription by videoState::videoDescription
  var videoInfo by videoState::videoInfo
  var videoEngagement by videoState::videoEngagement
  var favoriteFolders by videoState::favoriteFolders
  var favoriteFoldersLoading by videoState::favoriteFoldersLoading
  var videoActionBusy by videoState::videoActionBusy
  var onlineViewerText by videoState::onlineViewerText
  var previewItem by remember { mutableStateOf<FeedItem?>(null) }
  var previewInfo by remember { mutableStateOf<VideoInfo?>(null) }
  var previewFromHomeFeed by remember { mutableStateOf(false) }
  var dismissedFeedItemIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  val profileState = remember { AppRootProfileState() }
  var profileEntryToken by remember { mutableStateOf(0L) }
  var profileStack by remember { mutableStateOf<List<ProfileStackEntry>>(emptyList()) }
  var profileLayerSuppressed by remember { mutableStateOf(false) }
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
  fun launchTransition(block: suspend CoroutineScope.() -> Unit) {
    val previous = activeTransitionJob
    activeTransitionJob = scope.launch {
      previous?.cancelAndJoin()
      block()
    }
  }
  fun animateToRootTab(tab: RootTab) {
    val requestToken = rootPageSwitchRequestToken + 1L
    rootPageSwitchRequestToken = requestToken
    rootPageSwitchRequested = true
    scope.launch {
      try {
        rootPagerState.animateScrollToPage(
          page = tab.ordinal,
          animationSpec =
            tween(if (settings.reduceMotion) 140 else 360, easing = FastOutSlowInEasing),
        )
      } finally {
        // animateScrollToPage returns at the settled position. Hold the playback gate through one
        // more display commit so neither the entering nor leaving page can play during the flight.
        withFrameNanos {}
        if (rootPageSwitchRequestToken == requestToken) rootPageSwitchRequested = false
      }
    }
  }

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
      }
  }

  val rootPageSwitchInProgress =
    rootPageSwitchRequested || rootPagerState.isScrollInProgress
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

  BackHandler(enabled = rootTab != RootTab.HOME && !appState.isVideoScreen && profileMid == null) {
    animateToRootTab(RootTab.HOME)
  }

  val playerSession = remember { AppRootPlayerSessionState() }
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
  val playerUiPositionProvider =
    remember(playerSession) {
      {
        playerSession.scrubPreviewMs
          ?: playerSession.pendingSeekTargetMs
          ?: playerSession.currentPositionMs
      }
    }

  fun restoredBangumiCard(sourceCard: SpaceContentCard): SpaceContentCard {
    val selection =
      BangumiPlaybackStore.read(context.applicationContext, sourceCard) ?: return sourceCard
    val restoredSeasonId = selection.seasonId.takeIf { it > 0L } ?: sourceCard.seasonId
    return sourceCard.copy(
      aid = 0L,
      bvid = selection.bvid,
      videoUrl = "https://www.bilibili.com/bangumi/play/ep${selection.episodeId}",
      seasonId = restoredSeasonId,
      episodeId = selection.episodeId,
    )
  }

  fun commitPlaybackProgress() {
    val bangumiPage = activeBangumiPage
    val bangumiEpisode =
      bangumiPage?.season
        ?.let { it.episodes + it.sections.flatMap { section -> section.episodes } }
        ?.firstOrNull { it.id == bangumiPage.currentEpisodeId }
    bangumiPage?.let { page ->
      val season = page.season
      if (season != null && bangumiEpisode != null) {
        BangumiPlaybackStore.save(
          context.applicationContext,
          page.sourceCard,
          season.seasonId,
          bangumiEpisode,
        )
      }
    }
    val aid = historyAid
    val cid = historyCid
    if (aid <= 0L || cid <= 0L) return
    val durationSeconds = historyDuration
    val positionMs = playerViewModel.exoPlayer?.currentPosition ?: currentPositionMs
    PlaybackProgressStore.save(
      context.applicationContext,
      aid,
      cid,
      positionMs,
      durationSeconds * 1000L,
    )
    if (authUserInfo.isLogin) {
      // Snapshot the PGC identity before launching IO. Episode switches update activeBangumiPage
      // immediately after this function returns.
      val bangumiSubType =
        bangumiPage?.let { page -> if (page.sourceCard.seasonType == 4) 4 else 1 } ?: 0
      val bangumiEpisodeId =
        bangumiEpisode
          ?.takeIf { it.aid == aid && it.cid == cid }
          ?.id
          ?: 0L
      val bangumiSeasonId =
        if (bangumiEpisodeId > 0L) bangumiPage?.season?.seasonId ?: 0L else 0L
      scope.launch(Dispatchers.IO) {
        runCatching {
          if (bangumiSubType > 0) {
            BiliApi.reportBangumiPlayback(
              aid = aid,
              cid = cid,
              episodeId = bangumiEpisodeId,
              seasonId = bangumiSeasonId,
              playedSeconds = positionMs / 1000L,
              durationSeconds = durationSeconds,
              startTimestamp = historyStartTimestamp,
              subType = bangumiSubType,
              playType = 2,
            )
          } else {
            BiliApi.reportPlayback(aid, cid, positionMs / 1000L)
          }
        }
      }
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
  val playerViewHolder = remember { arrayOfNulls<HeldPlayerView>(1) }

  fun obtainPlayerView(
    ctx: android.content.Context,
    role: SharedPlayerViewRole,
  ): PlayerView {
    playerViewHolder[0]?.takeIf { it.role == role }?.let { return it.view }
    playerViewHolder[0]?.view?.let { previous ->
      previous.animate().cancel()
      previous.player = null
      (previous.parent as? ViewGroup)?.removeView(previous)
    }
    return createPlayerView(ctx).also { view ->
      // Keep the Android hierarchy mounted and control the separate SurfaceControl layer itself.
      // Parent alpha is insufficient on Samsung SurfaceView implementations and can expose the
      // previous decoder buffer while a new PV is being prepared.
      view.alpha = 1f
      view.updateVideoSurfaceAlpha(if (role == SharedPlayerViewRole.PREVIEW) 0f else 1f)
      playerViewHolder[0] = HeldPlayerView(role, view)
    }
  }

  fun obtainPlayerViewForHost(
    ctx: android.content.Context,
    role: SharedPlayerViewRole,
  ): PlayerView =
    obtainPlayerView(ctx, role).also { view ->
      // Compose may insert the destination AndroidViewHolder before disposing the source holder.
      // A host of the same role may be recreated; detach it synchronously before reattaching.
      (view.parent as? ViewGroup)?.removeView(view)
    }

  fun unbindPlayerView() { playerViewHolder[0]?.view?.player = null }

  fun prewarmPlayerInfrastructure() {
    val player = playerViewModel.preparePlayer()
    obtainPlayerView(context, SharedPlayerViewRole.PREVIEW).player = player
  }

  suspend fun prepareCardTransition(
    session: CardTransitionSession,
    targetBounds: () -> Rect = { playerBounds },
  ): Rect = coroutineScope {
    val boundsTracker = StableBoundsTracker()
    val coverJob = launch {
      if (session.reusePlayerSurface) {
        session.transitionBitmap =
          LoadedFeedImageRegistry.bitmap(session.item.coverUrl)
            ?: LoadedFeedImageRegistry.bitmap(
              bangumiPreviewCoverCacheKey(session.item.coverUrl)
            )
        session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
        return@launch
      }
      if (session.item.coverUrl.isBlank()) {
        session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
      } else if (
        (
          LoadedFeedImageRegistry.bitmap(
            session.item.coverUrl,
            requireUncropped = session.fitCover,
          ) ?: activeBangumiPage
            ?.takeIf { it.sourceOrigin == PageOrigin.BangumiHome && !session.fitCover }
            ?.let {
              LoadedFeedImageRegistry.bitmap(
                bangumiPreviewCoverCacheKey(session.item.coverUrl)
              )
            }
        )?.also {
          session.transitionBitmap = it
          LoadedFeedImageRegistry.markLoaded(session.item.coverUrl, it)
        } != null
      ) {
        session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
      } else if (activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome) {
        // The bangumi animation is cache-only. A miss is represented by its black bridge; never
        // start Coil/network/decode work while the user is already watching the transition.
        session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
      } else {
        runCatching {
            val request =
              CoverImageRequestFactory.request(
                session.item.coverUrl,
                ImageRequest.Builder(context.applicationContext),
                width = if (session.fitCover) 840 else 672,
                height = if (session.fitCover) 1120 else 378,
                crop = !session.fitCover,
              )
            context.applicationContext.imageLoader.execute(request).image
          }
          .getOrNull()
          ?.let { image ->
            val bitmap = (image as? BitmapImage)?.bitmap
            session.transitionBitmap = bitmap
            LoadedFeedImageRegistry.markLoaded(
              session.item.coverUrl,
              bitmap,
              cropped = !session.fitCover,
            )
            session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
          }
      }
    }
    val readinessJob = launch {
      while (isActive && !session.preparation.isReady()) {
        withFrameNanos {}
        val bounds = targetBounds()
        if (bounds.hasUsableSize()) {
          session.preparation.markReady(TransitionReadySignal.TARGET_MOUNTED)
          if (boundsTracker.observe(bounds)) {
            session.endBounds = bounds
            session.preparation.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
          }
        }
      }
    }
    val result =
      session.preparation.await(
        timeoutMillis = if (session.fitCover) 1_600L else TRANSITION_PREPARE_TIMEOUT_MS
      )
    readinessJob.cancelAndJoin()
    coverJob.cancelAndJoin()
    if (result == TransitionPreparationResult.CANCELLED) return@coroutineScope Rect.Zero
    session.timedOut = result == TransitionPreparationResult.TIMED_OUT
    val resolvedBounds = targetBounds().takeIf { it.hasUsableSize() }
    resolvedBounds?.let { session.endBounds = it }
    session.phase = SessionPhase.READY
    withFrameNanos {}
    resolvedBounds ?: Rect.Zero
  }

  suspend fun prepareExitTransition(
    session: CardTransitionSession,
    targetBounds: () -> Rect?,
  ): Rect = coroutineScope {
    val boundsTracker = StableBoundsTracker()
    val coverJob = launch {
      if (session.reusePlayerSurface) {
        session.transitionBitmap =
          LoadedFeedImageRegistry.bitmap(session.item.coverUrl)
            ?: LoadedFeedImageRegistry.bitmap(
              bangumiPreviewCoverCacheKey(session.item.coverUrl)
            )
        session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
        return@launch
      }
      if (session.item.coverUrl.isBlank()) {
        session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
      } else if (
        (
          LoadedFeedImageRegistry.bitmap(
            session.item.coverUrl,
            requireUncropped = session.fitCover,
          ) ?: activeBangumiPage
            ?.takeIf { it.sourceOrigin == PageOrigin.BangumiHome && !session.fitCover }
            ?.let {
              LoadedFeedImageRegistry.bitmap(
                bangumiPreviewCoverCacheKey(session.item.coverUrl)
              )
            }
        )?.also {
          session.transitionBitmap = it
          LoadedFeedImageRegistry.markLoaded(session.item.coverUrl, it)
        } != null
      ) {
        session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
      } else if (activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome) {
        session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
      } else {
        runCatching {
            val request =
              CoverImageRequestFactory.request(
                session.item.coverUrl,
                ImageRequest.Builder(context.applicationContext),
                width = if (session.fitCover) 840 else 672,
                height = if (session.fitCover) 1120 else 378,
                crop = !session.fitCover,
              )
            context.applicationContext.imageLoader.execute(request).image
          }
          .getOrNull()
          ?.let { image ->
            val bitmap = (image as? BitmapImage)?.bitmap
            session.transitionBitmap = bitmap
            LoadedFeedImageRegistry.markLoaded(
              session.item.coverUrl,
              bitmap,
              cropped = !session.fitCover,
            )
            session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
          }
      }
    }
    val readinessJob = launch {
      while (isActive && !session.preparation.isReady()) {
        withFrameNanos {}
        val bounds = targetBounds()
        if (bounds != null && bounds.hasUsableSize()) {
          session.preparation.markReady(TransitionReadySignal.TARGET_MOUNTED)
          if (boundsTracker.observe(bounds)) {
            session.startBounds = bounds
            session.preparation.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
          }
        }
      }
    }
    val result =
      session.preparation.await(
        timeoutMillis = if (session.fitCover) 1_600L else TRANSITION_PREPARE_TIMEOUT_MS
      )
    readinessJob.cancelAndJoin()
    coverJob.cancelAndJoin()
    if (result == TransitionPreparationResult.CANCELLED) return@coroutineScope Rect.Zero
    session.timedOut = result == TransitionPreparationResult.TIMED_OUT
    val resolvedBounds = targetBounds()?.takeIf { it.hasUsableSize() }
    resolvedBounds?.let { session.startBounds = it }
    session.phase = SessionPhase.READY
    withFrameNanos {}
    resolvedBounds ?: Rect.Zero
  }

  LaunchedEffect(playerViewModel, context, startupWarmupVisible) {
    if (startupWarmupVisible) return@LaunchedEffect
    // Keep reusable player creation out of the feed's first composition and cover decode burst.
    // The single PlayerView host and ExoPlayer are created in separate main-queue idle stages;
    // no play URL, cover, or video request is made here.
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
    // A timeout keeps cold start recoverable on a bad network. Pause any unfinished prefetch while
    // the mask itself animates so image decoding cannot steal its frames, then resume when idle.
    startupWarmupFadeInProgress = true
    startupWarmupAlpha.animateTo(
      0f,
      animationSpec = tween(fadeDuration),
    )
    startupWarmupVisible = false
    startupWarmupFadeInProgress = false
  }

  LaunchedEffect(
    playerReady,
    appState.isVideoScreen,
    rootPlayerOwnership.role,
  ) {
    if (
      !playerReady &&
        !appState.isVideoScreen &&
        rootPlayerOwnership.role == RootPlayerSurfaceRole.IDLE
    ) {
      unbindPlayerView()
    }
  }

  val rootPlayerContent:
    @Composable (
      androidx.compose.ui.Modifier,
      Float,
      Boolean,
      Boolean,
      SharedPlayerViewRole,
    ) -> Unit =
    { modifier, fullscreenProgress, fullscreen, danmakuAllowed, role ->
        // Read overlay inputs during composition. If they are read only inside AndroidView's
        // update lambda, rememberUpdatedState can change without invalidating that update block.
        val danmakuItems = if (danmakuAllowed) latestDanmaku else emptyList()
        val maskTimeline = if (danmakuAllowed) latestDanmakuMask else null
        val danmakuEnabled = danmakuAllowed && latestShowDanmaku
        val smartBlocking = latestDanmakuSmartBlocking
        val danmakuPaused = latestDanmakuPaused
        val danmakuOpacity = latestDanmakuOpacity
        val danmakuDisplayArea = latestDanmakuDisplayArea
        val danmakuDensity = latestDanmakuDensity
        val danmakuFontScale = latestDanmakuFontScale
        val danmakuSpeed = latestDanmakuSpeed
        val danmakuPositionEpoch = latestDanmakuPositionEpoch
        val highDynamicRange = latestIsHdrPlayback
        val danmakuState = remember { DanmakuUpdateState() }
        AndroidView(
          factory = { ctx -> obtainPlayerViewForHost(ctx, role) },
          update = { playerView ->
            if (playerView.player !== playerViewModel.exoPlayer) {
              playerView.player = playerViewModel.exoPlayer
            }
            val fullscreenRadius =
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) 14f else 0f
            playerView.updatePlayerCornerRadius(
              20f + (fullscreenRadius - 20f) * fullscreenProgress
            )
            if (
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
                danmakuFontScale,
                danmakuSpeed,
                danmakuPositionEpoch,
              )
            ) {
              playerView.updateDanmakuOverlay(
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
                danmakuFontScale,
                danmakuSpeed,
                danmakuPositionEpoch,
                latestPlayerPositionProvider,
              )
            }
          },
          modifier = modifier,
        )
    }

  fun previewSeek(targetMs: Long) = playerSession.previewSeek(playerViewModel, targetMs)

  fun setTemporarySpeedBoost(active: Boolean) =
    playerSession.setTemporarySpeedBoost(playerViewModel, active)

  fun setPlaybackSpeed(speed: Float) = playerSession.setPlaybackSpeed(playerViewModel, speed)

  fun cancelSeekPreview() = playerSession.cancelSeekPreview(playerViewModel)

  fun commitSeek(targetMs: Long) =
    playerSession.commitSeek(playerViewModel, targetMs, historyDuration, scope)

  fun revealTransitionSession(session: CardTransitionSession, timedOut: Boolean = false) {
    if (
      transitionSession?.token != session.token ||
        session.reverseRequested ||
        session.phase != SessionPhase.WAITING_FIRST_FRAME
    )
      return
    if (timedOut) {
      session.timedOut = true
      showEmbeddedCover = !session.reusePlayerSurface
    }
    val animateSharedCover =
      !session.reusePlayerSurface || activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome
    activeRevealJob?.cancel()
    activeRevealJob = scope.launch {
      if (timedOut) withFrameNanos {}
      session.phase = SessionPhase.REVEALING
      kotlinx.coroutines.coroutineScope {
        if (animateSharedCover) {
          launch {
            session.coverAlpha.animateTo(
              0f,
              tween(if (settings.reduceMotion) 90 else 170, easing = FastOutSlowInEasing),
            )
          }
        }
        launch {
          delay(if (settings.reduceMotion) 20 else 70)
          session.panelAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 90 else 210, easing = FastOutSlowInEasing),
          )
        }
      }
      session.phase = SessionPhase.COMPLETED
      withFrameNanos {}
      if (transitionSession?.token == session.token && !session.reverseRequested) {
        transitionPhase = TransitionPhase.Video(session.item, null)
        transitionSession = null
        hiddenFeedCoverItemId = null
        hiddenMyCoverItemId = null
        hiddenSearchCoverItemId = null
        hiddenBangumiIndexItemId = null
        hiddenBangumiRecommendationItemId = null
        hiddenArticleVideoCoverItemId = null
        hiddenRecommendationCoverItemId = null
        hiddenPlaybackEndRecommendationCoverItemId = null
        hiddenProfileCoverItemId = null
        profileVideoTransitionActive = false
      }
      activeRevealJob = null
    }
  }

  // ── Sync auth user info to feed ─────────────────────────────────────
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
    profileMessageViewModel.setUser(authUserInfo.mid, loadInitialSection = false)
    if (changedAccount) feedViewModel.refresh()
  }

  LaunchedEffect(settings.initialFeedCount) {
    feedViewModel.setInitialTargetCount(settings.initialFeedCount)
  }

  LaunchedEffect(
    settings.unlockDolbyVision,
    settings.unlockDolbyAtmos,
    settings.advancedAudioEnabled,
    settings.advancedAudioPriority,
  ) {
    playerViewModel.setCompatibilityUnlocks(
      dolbyVision = settings.unlockDolbyVision,
      dolbyAtmos = settings.unlockDolbyAtmos,
    )
    playerViewModel.setAdvancedAudioPreferences(
      enabled = settings.advancedAudioEnabled,
      priority = settings.advancedAudioPriority,
    )
  }

  // Start the bangumi request while the startup mask still covers the retained root pager. Its
  // adjacent BANGUMI page can then resolve details and decode the first carousel images before the
  // mask asks that page whether it is ready.
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
  LaunchedEffect(appState.selectedVideo?.id, transitionPhase) {
    val selectedId = appState.selectedVideo?.id ?: return@LaunchedEffect
    if (
      transitionPhase !is TransitionPhase.Feed &&
        rootPlayerOwnership.role != RootPlayerSurfaceRole.EXIT_COVERED &&
        rootPlayerOwnership.mediaId != selectedId
    ) {
      rootPlayerOwnership =
        RootPlayerOwnership(RootPlayerSurfaceRole.DETAIL_PENDING, selectedId)
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
  val heartbeatBangumiPage = activeBangumiPage
  val heartbeatBangumiEpisode =
    heartbeatBangumiPage?.season
      ?.let { it.episodes + it.sections.flatMap { section -> section.episodes } }
      ?.firstOrNull {
        it.id == heartbeatBangumiPage.currentEpisodeId &&
          it.aid == historyAid &&
          it.cid == historyCid
      }
  AppRootPlayerEffects(
    context = context,
    lifecycleOwner = lifecycleOwner,
    isVideoScreen = appState.isVideoScreen,
    selectedVideoId = appState.selectedVideo?.id,
    playerActivationId = playerActivationId,
    playerState = playerState,
    renderedVideoId = renderedVideoId,
    // The Bangumi hero owns a separate preview player. The detail player must always become warm
    // idle when it returns to a feed, even if legacy transition bookkeeping still mentions PREVIEW.
    keepMediaWhileInFeed = false,
    playerViewModel = playerViewModel,
    sessionState = playerSession,
    transitionSession = transitionSession,
    historyAid = historyAid,
    historyCid = historyCid,
    historyDuration = historyDuration,
    historyStartTimestamp = historyStartTimestamp,
    bangumiSubType =
      activeBangumiPage?.let { page ->
        if (page.sourceCard.seasonType == 4) 4 else 1
      } ?: 0,
    bangumiEpisodeId = heartbeatBangumiEpisode?.id ?: 0L,
    bangumiSeasonId =
      if (heartbeatBangumiEpisode != null) heartbeatBangumiPage.season.seasonId else 0L,
    loggedIn = authUserInfo.isLogin,
    onCommitPlaybackProgress = {
      if (appState.isVideoScreen) commitPlaybackProgress()
    },
    onRevealTransitionSession = ::revealTransitionSession,
  )

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
            BiliApi.getCommentThread(activeAid, target.rootRpid, target.type)
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

  fun loadSpacePage(mid: Long, page: Int) = profileState.loadSpacePage(mid, page, scope)

  fun loadSpaceDynamics(refresh: Boolean) {
    profileMid?.let { profileState.loadSpaceDynamics(it, refresh, scope) }
  }

  fun newProfileEntry(
    state: AppRootProfileState,
    commentTransition: CommentProfileTransition? = null,
    avatarTransition: AvatarProfileTransition? = null,
    returnsToVideo: Boolean = false,
    returnsToArticle: Boolean = false,
  ): ProfileStackEntry =
    ProfileStackEntry(
      entryId = ++profileEntryToken,
      state = state,
      commentTransition = commentTransition,
      avatarTransition = avatarTransition,
      returnsToVideo = returnsToVideo,
      returnsToArticle = returnsToArticle,
    )

  fun prepareProfile(mid: Long, initialProfile: dev.openbili.webdemo.api.SpaceProfile? = null) {
    profileState.prepareProfile(mid, initialProfile) { playerViewModel.exoPlayer?.pause() }
    profileStack = listOf(newProfileEntry(profileState))
    profileLayerSuppressed = false
  }

  fun loadPreparedProfile(mid: Long) = profileState.loadPreparedProfile(mid, scope)

  fun loadProfile(mid: Long) {
    prepareProfile(mid)
    loadPreparedProfile(mid)
  }

  LaunchedEffect(profileIpAuthorized) {
    if (profileIpAuthorized) profileMid?.let(::loadPreparedProfile)
  }

  fun snapshotProfile(mid: Long) = profileState.snapshotProfile(mid)

  fun restoreProfile(entry: ProfilePageEntry) {
    profileState.restoreProfile(entry)
    if (profileStack.none { it.state === profileState }) {
      profileStack = listOf(newProfileEntry(profileState))
    }
    profileLayerSuppressed = false
  }

  fun activeProfileEntry(entryId: Long? = null): ProfileStackEntry? =
    if (entryId == null) profileStack.lastOrNull()
    else profileStack.firstOrNull { it.entryId == entryId }

  suspend fun prepareProfileTransition(
    barrier: TransitionPreparationBarrier,
    imageUrl: String? = null,
    timeoutMillis: Long = TRANSITION_PREPARE_TIMEOUT_MS,
    targetMounted: () -> Boolean = { profileMid != null },
    targetBounds: () -> Rect = { profileAvatarBounds },
  ): TransitionPreparationResult = coroutineScope {
    val boundsTracker = StableBoundsTracker()
    val imageJob =
      imageUrl?.takeIf(String::isNotBlank)?.let { url ->
        launch {
          runCatching {
              context.applicationContext.imageLoader
                .execute(
                  ImageRequest.Builder(context.applicationContext).data(url).size(96, 96).build()
                )
                .image
            }
            .getOrNull()
            ?.let { barrier.markReady(TransitionReadySignal.IMAGE_READY) }
        }
      }
    val readinessJob = launch {
      while (isActive && !barrier.isReady()) {
        withFrameNanos {}
        if (targetMounted()) barrier.markReady(TransitionReadySignal.TARGET_MOUNTED)
        val bounds = targetBounds()
        if (boundsTracker.observe(bounds)) {
          barrier.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
        }
      }
    }
    val result = barrier.await(timeoutMillis)
    readinessJob.cancelAndJoin()
    imageJob?.cancelAndJoin()
    result
  }

  suspend fun prepareBoundsTransition(
    barrier: TransitionPreparationBarrier,
    bounds: () -> Rect,
  ): TransitionPreparationResult = coroutineScope {
    val tracker = StableBoundsTracker()
    val readinessJob = launch {
      while (isActive && !barrier.isReady()) {
        withFrameNanos {}
        val current = bounds()
        if (current.hasUsableSize()) {
          barrier.markReady(TransitionReadySignal.TARGET_MOUNTED)
          if (tracker.observe(current)) {
            barrier.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
          }
        }
      }
    }
    val result = barrier.await()
    readinessJob.cancelAndJoin()
    result
  }

  fun openProfile(mid: Long) {
    profileTransitionJob?.cancel()
    commentProfileTransition = null
    commentProfileReturnTransition = null
    avatarProfileTransition = null
    avatarProfileReturnTransition = null
    loadProfile(mid)
  }

  fun openAvatarProfileFrom(
    sourceEntryId: Long?,
    mid: Long,
    bounds: Rect,
    face: String? = authUserInfo.face,
    name: String? = authUserInfo.name,
  ) {
    if (mid <= 0) return
    val sourceEntry = sourceEntryId?.let(::activeProfileEntry)
    val sourceState = sourceEntry?.state
    val returnsToVideo =
      sourceEntryId == null &&
        profileLayerSuppressed &&
        profileStack.isNotEmpty() &&
        transitionPhase is TransitionPhase.Video
    val returnsToArticle = sourceEntryId == null && articleStack.isNotEmpty()
    val retainedProfileEntry = profileStack.lastOrNull().takeIf { returnsToVideo }
    (sourceEntry ?: retainedProfileEntry)?.let { owner ->
      profileStack =
        profileStack.retainReturnTransitionsFor(
          owner.entryId,
          commentProfileReturnTransition,
          avatarProfileReturnTransition,
        )
    }
    // The banner already represents this account. Replaying the shared avatar transition here
    // looks like the avatar takes an unexplained round trip and does not perform navigation.
    if (
      sourceState?.profileMid == mid ||
        (sourceEntry == null && !returnsToVideo && !returnsToArticle && profileMid == mid)
    )
      return
    if (
      (sourceEntry != null || returnsToVideo || returnsToArticle) &&
        profileStack.size >= MAX_PROFILE_STACK_DEPTH
    ) {
      Toast.makeText(context, "个人主页最多可以连续打开八层", Toast.LENGTH_SHORT).show()
      return
    }
    val sourceProfileState = sourceState ?: retainedProfileEntry?.state
    val sourceProfile = sourceProfileState?.profileMid?.let(sourceProfileState::snapshotProfile)
    if (bounds == Rect.Zero || bounds.width <= 0f || bounds.height <= 0f) {
      if (sourceEntry != null || returnsToVideo || returnsToArticle) {
        val child = AppRootProfileState()
        child.prepareProfile(mid) { playerViewModel.exoPlayer?.pause() }
        profileStack =
          profileStack +
            newProfileEntry(
              child,
              returnsToVideo = returnsToVideo,
              returnsToArticle = returnsToArticle,
            )
        profileLayerSuppressed = false
        child.loadPreparedProfile(mid, scope)
      } else openProfile(mid)
      return
    }
    val session =
      AvatarProfileTransition(
        token = System.nanoTime(),
        targetMid = mid,
        face = face.orEmpty(),
        name = name.orEmpty(),
        sourceBounds = bounds,
        sourceProfile = sourceProfile,
      )
    commentProfileTransition = null
    commentProfileReturnTransition = null
    avatarProfileTransition = session
    avatarProfileReturnTransition = session
    session.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
    if (session.face.isBlank()) session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
    val avatarChildEntry: ProfileStackEntry? =
      if (
        (sourceEntry != null || returnsToVideo || returnsToArticle) &&
          (sourceProfile != null || returnsToArticle)
      ) {
        val child = AppRootProfileState()
        child.prepareProfile(mid) { playerViewModel.exoPlayer?.pause() }
        newProfileEntry(
          child,
          avatarTransition = session,
          returnsToVideo = returnsToVideo,
          returnsToArticle = returnsToArticle,
        )
      } else null
    if (avatarChildEntry == null) prepareProfile(mid)
    else {
      profileStack = profileStack + avatarChildEntry
      profileLayerSuppressed = false
    }
    profileTransitionJob?.cancel()
    profileTransitionJob = scope.launch {
      session.progress.snapTo(0f)
      val preparationResult = coroutineScope {
        val preparation = async {
          prepareProfileTransition(
            barrier = session.preparation,
            imageUrl = session.face,
            targetMounted = {
              avatarChildEntry?.let {
                profileStack.lastOrNull()?.entryId == it.entryId && it.state.profileMid == mid
              } ?: (profileMid != null)
            },
            targetBounds = { avatarChildEntry?.state?.profileAvatarBounds ?: profileAvatarBounds },
          )
        }
        if (avatarChildEntry != null) {
          delay(NESTED_PROFILE_HEADER_FADE_OUT_MS)
        }
        preparation.await()
      }
      if (
        preparationResult == TransitionPreparationResult.CANCELLED ||
          avatarProfileTransition?.token != session.token ||
          session.closing
      )
        return@launch
      session.preparationTimedOut = preparationResult == TransitionPreparationResult.TIMED_OUT
      session.phase = SessionPhase.READY
      withFrameNanos {}
      session.phase = SessionPhase.FLYING
      coroutineScope {
        launch {
          session.progress.animateTo(
            1f,
            tween(if (settings.reduceMotion) 140 else 380, easing = FastOutSlowInEasing),
          )
        }
        if (sourceProfile != null || returnsToArticle) {
          launch {
            session.backgroundAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 80 else 170, easing = FastOutSlowInEasing),
            )
          }
        }
      }
      if (avatarProfileTransition?.token == session.token && !session.closing) {
        session.phase = SessionPhase.COMPLETED
        session.progress.snapTo(1f)
        if (avatarChildEntry != null) {
          avatarChildEntry.state.loadPreparedProfile(mid, scope)
          avatarProfileTransition = null
          session.backgroundAlpha.snapTo(0f)
          return@launch
        }
        withFrameNanos {}
        if (avatarProfileTransition?.token == session.token && !session.closing) {
          avatarProfileTransition = null
          loadPreparedProfile(mid)
        }
      }
    }
  }

  fun openAvatarProfile(
    mid: Long,
    bounds: Rect,
    face: String? = authUserInfo.face,
    name: String? = authUserInfo.name,
  ) = openAvatarProfileFrom(null, mid, bounds, face, name)

  fun openCommentProfileFrom(
    sourceEntryId: Long?,
    mid: Long,
    comment: CommentItem,
    anchor: CommentProfileAnchor,
    returnsToArticleSource: Boolean = false,
  ) {
    val sourceEntry = sourceEntryId?.let(::activeProfileEntry)
    val sourceState = sourceEntry?.state
    val returnsToVideo =
      sourceEntryId == null &&
        profileLayerSuppressed &&
        profileStack.isNotEmpty() &&
        transitionPhase is TransitionPhase.Video
    val returnsToArticle =
      sourceEntryId == null && returnsToArticleSource && articleStack.isNotEmpty()
    val retainedProfileEntry = profileStack.lastOrNull().takeIf { returnsToVideo }
    (sourceEntry ?: retainedProfileEntry)?.let { owner ->
      profileStack =
        profileStack.retainReturnTransitionsFor(
          owner.entryId,
          commentProfileReturnTransition,
          avatarProfileReturnTransition,
        )
    }
    if (
      mid <= 0 ||
        sourceState?.profileMid == mid ||
        (sourceEntry == null && !returnsToVideo && !returnsToArticle && profileMid == mid)
    )
      return
    if (
      (sourceEntry != null || returnsToVideo || returnsToArticle) &&
        profileStack.size >= MAX_PROFILE_STACK_DEPTH
    ) {
      Toast.makeText(context, "个人主页最多可以连续打开八层", Toast.LENGTH_SHORT).show()
      return
    }
    val sourceProfileState = sourceState ?: retainedProfileEntry?.state
    val sourceProfile = sourceProfileState?.profileMid?.let(sourceProfileState::snapshotProfile)
    val bounds =
      anchor.currentCardBounds().takeIf { it.width > 0f && it.height > 0f }
        ?: anchor.initialCardBounds
    if (bounds.width <= 0f || bounds.height <= 0f) {
      if (sourceEntry != null || returnsToVideo || returnsToArticle) {
        val child = AppRootProfileState()
        child.prepareProfile(mid) { playerViewModel.exoPlayer?.pause() }
        profileStack =
          profileStack +
            newProfileEntry(
              child,
              returnsToVideo = returnsToVideo,
              returnsToArticle = returnsToArticle,
            )
        profileLayerSuppressed = false
        child.loadPreparedProfile(mid, scope)
      } else openProfile(mid)
      return
    }
    val session =
      CommentProfileTransition(
        token = System.nanoTime(),
        targetMid = mid,
        sourceComment = comment,
        sourceBounds = bounds,
        sourceAvatarBounds =
          anchor.currentAvatarBounds().takeIf {
            mid == comment.mid && it.width > 0f && it.height > 0f
          },
        currentSourceBounds = anchor.currentCardBounds,
        currentSourceAvatarBounds = anchor.currentAvatarBounds,
        sourceProfile = sourceProfile,
      )
    avatarProfileTransition = null
    avatarProfileReturnTransition = null
    commentProfileTransition = session
    commentProfileReturnTransition = session
    session.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
    // Nested navigation (profile → profile): keep the parent profile fully composed by creating a
    // separate state holder for the child. The parent never leaves composition, so all its scroll
    // positions, opened dynamic detail, comment state, and section selection are preserved.
    val childEntry: ProfileStackEntry? =
      if (
        (sourceEntry != null || returnsToVideo || returnsToArticle) &&
          (sourceProfile != null || returnsToArticle)
      ) {
        val child = AppRootProfileState()
        child.prepareProfile(mid) { playerViewModel.exoPlayer?.pause() }
        newProfileEntry(
          child,
          commentTransition = session,
          returnsToVideo = returnsToVideo,
          returnsToArticle = returnsToArticle,
        )
      } else null
    if (childEntry == null) prepareProfile(mid)
    else {
      profileStack = profileStack + childEntry
      profileLayerSuppressed = false
    }
    profileTransitionJob?.cancel()
    profileTransitionJob = scope.launch {
      session.progress.snapTo(0f)
      val preparationResult = coroutineScope {
        val preparation = async {
          prepareProfileTransition(
            barrier = session.preparation,
            imageUrl =
              session.sourceComment.face.takeIf {
                session.sourceAvatarBounds != null && it.isNotBlank()
              },
            timeoutMillis = COMMENT_PROFILE_PREPARE_TIMEOUT_MS,
            targetMounted = {
              childEntry?.let {
                profileStack.lastOrNull()?.entryId == it.entryId && it.state.profileMid == mid
              } ?: (profileMid != null)
            },
            targetBounds = { childEntry?.state?.profileAvatarBounds ?: profileAvatarBounds },
          )
        }
        if (childEntry != null) {
          delay(NESTED_PROFILE_HEADER_FADE_OUT_MS)
        }
        preparation.await()
      }
      if (
        preparationResult == TransitionPreparationResult.CANCELLED ||
          commentProfileTransition?.token != session.token ||
          session.closing
      )
        return@launch
      session.preparationTimedOut = preparationResult == TransitionPreparationResult.TIMED_OUT
      session.phase = SessionPhase.READY
      withFrameNanos {}
      session.phase = SessionPhase.FLYING
      coroutineScope {
        launch {
          session.progress.animateTo(
            1f,
            tween(if (settings.reduceMotion) 140 else 420, easing = FastOutSlowInEasing),
          )
        }
        if (sourceProfile != null || returnsToArticle) {
          launch {
            session.backgroundAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 80 else 180, easing = FastOutSlowInEasing),
            )
          }
        }
      }
      if (commentProfileTransition?.token == session.token && !session.closing) {
        session.phase = SessionPhase.COMPLETED
        session.progress.snapTo(1f)
        session.blocksInput = false
        if (childEntry != null) {
          childEntry.state.loadPreparedProfile(mid, scope)
          commentProfileTransition = null
          session.backgroundAlpha.snapTo(0f)
          return@launch
        }
        withFrameNanos {}
        if (commentProfileTransition?.token == session.token && !session.closing) {
          commentProfileTransition = null
          loadPreparedProfile(mid)
        }
      }
    }
  }

  fun openCommentProfile(
    mid: Long,
    comment: CommentItem,
    anchor: CommentProfileAnchor,
  ) = openCommentProfileFrom(null, mid, comment, anchor)

  fun openArticleCommentProfile(
    mid: Long,
    comment: CommentItem,
    anchor: CommentProfileAnchor,
  ) = openCommentProfileFrom(null, mid, comment, anchor, returnsToArticleSource = true)

  fun resumeVideoUnderProfile() {
    val item = appState.selectedVideo ?: return
    if (transitionPhase !is TransitionPhase.Video) return
    dataCommitAllowedId = item.id
    playerActivationId = item.id
    if (!playerViewModel.resumeIfLoaded(item.id)) {
      val restore = videoEntryCache[item.id]
      playerViewModel.loadVideo(
        item,
        startPositionMs = restore?.savedPositionMs ?: 0L,
        preferredStreamIndex = restore?.qualityIndex,
        preferredResolutionMode = settings.preferredResolutionMode,
      )
    }
  }

  fun completeProfileReturnToArticle() {
    profileStack = profileStack.dropLast(1)
    val parentEntry = profileStack.lastOrNull()
    commentProfileReturnTransition = parentEntry?.commentTransition
    avatarProfileReturnTransition = parentEntry?.avatarTransition
    profileLayerSuppressed = articleStack.lastOrNull()?.origin == ArticleOrigin.PROFILE
  }

  fun closeProfile() {
    val topProfileEntry = profileStack.lastOrNull()
    val commentSession =
      commentProfileTransition
        ?: topProfileEntry?.commentTransition
        ?: commentProfileReturnTransition
    if (commentSession != null) {
      commentSession.closing = true
      commentSession.blocksInput = true
      commentSession.preparation.cancel()
      val returnPreparation =
        TransitionPreparationBarrier(
          setOf(
            TransitionReadySignal.TARGET_MOUNTED,
            TransitionReadySignal.TARGET_BOUNDS_STABLE,
          )
        )
      profileTransitionJob?.cancel()
      profileTransitionJob = scope.launch {
        commentProfileTransition = commentSession
        prepareBoundsTransition(returnPreparation) {
          resolvedCommentProfileBounds(
            commentSession.sourceBounds,
            commentSession.currentSourceBounds(),
          )
        }
        if (commentProfileTransition?.token != commentSession.token) return@launch
        commentSession.phase = SessionPhase.READY
        withFrameNanos {}
        commentSession.phase = SessionPhase.FLYING
        if (commentSession.sourceProfile != null) {
          commentSession.backgroundAlpha.snapTo(0f)
          commentSession.backgroundAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 70 else 150, easing = FastOutSlowInEasing),
          )
        }
        val duration =
          if (settings.reduceMotion) 110
          else (380 * commentSession.progress.value.coerceIn(.25f, 1f)).toInt()
        commentSession.progress.animateTo(
          0f,
          tween(duration, easing = FastOutSlowInEasing),
        )
        commentSession.phase = SessionPhase.COMPLETED
        withFrameNanos {}
        if (commentProfileTransition?.token == commentSession.token) {
          val sourceProfile = commentSession.sourceProfile
          if (topProfileEntry?.returnsToArticle == true) {
            completeProfileReturnToArticle()
            commentProfileTransition = null
          } else if (topProfileEntry?.returnsToVideo == true) {
            profileStack = profileStack.dropLast(1)
            profileLayerSuppressed = true
            commentProfileTransition = null
            val parentEntry = profileStack.lastOrNull()
            commentProfileReturnTransition = parentEntry?.commentTransition
            avatarProfileReturnTransition = parentEntry?.avatarTransition
            resumeVideoUnderProfile()
          } else if (sourceProfile != null) {
            if (profileStack.size > 1) {
              // Remove only the top overlay. Every parent page remains composed and unchanged.
              profileStack = profileStack.dropLast(1)
              commentProfileTransition = null
              val parentEntry = profileStack.lastOrNull()
              commentProfileReturnTransition = parentEntry?.commentTransition
              avatarProfileReturnTransition = parentEntry?.avatarTransition
            } else {
              restoreProfile(sourceProfile)
            }
          } else {
            profileMid = null
            profileStack = emptyList()
            profileLayerSuppressed = false
            commentProfileTransition = null
            if (commentProfileReturnTransition?.token == commentSession.token) {
              commentProfileReturnTransition = null
            }
            resumeVideoUnderProfile()
          }
        }
      }
      return
    }
    val avatarSession =
      avatarProfileTransition ?: topProfileEntry?.avatarTransition ?: avatarProfileReturnTransition
    if (avatarSession != null) {
      avatarSession.closing = true
      avatarSession.preparation.cancel()
      val returnPreparation =
        TransitionPreparationBarrier(
          setOf(
            TransitionReadySignal.TARGET_MOUNTED,
            TransitionReadySignal.TARGET_BOUNDS_STABLE,
          )
        )
      profileTransitionJob?.cancel()
      profileTransitionJob = scope.launch {
        avatarProfileTransition = avatarSession
        prepareBoundsTransition(returnPreparation) { avatarSession.sourceBounds }
        if (avatarProfileTransition?.token != avatarSession.token) return@launch
        avatarSession.phase = SessionPhase.READY
        withFrameNanos {}
        avatarSession.phase = SessionPhase.FLYING
        if (avatarSession.sourceProfile != null) {
          avatarSession.backgroundAlpha.snapTo(0f)
          avatarSession.backgroundAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 70 else 150, easing = FastOutSlowInEasing),
          )
        }
        val duration =
          if (settings.reduceMotion) 110
          else (340 * avatarSession.progress.value.coerceIn(.25f, 1f)).toInt()
        avatarSession.progress.animateTo(
          0f,
          tween(duration, easing = FastOutSlowInEasing),
        )
        avatarSession.phase = SessionPhase.COMPLETED
        withFrameNanos {}
        if (avatarProfileTransition?.token == avatarSession.token) {
          val sourceProfile = avatarSession.sourceProfile
          if (topProfileEntry?.returnsToArticle == true) {
            completeProfileReturnToArticle()
            avatarProfileTransition = null
          } else if (topProfileEntry?.returnsToVideo == true) {
            profileStack = profileStack.dropLast(1)
            profileLayerSuppressed = true
            avatarProfileTransition = null
            val parentEntry = profileStack.lastOrNull()
            commentProfileReturnTransition = parentEntry?.commentTransition
            avatarProfileReturnTransition = parentEntry?.avatarTransition
            resumeVideoUnderProfile()
          } else if (sourceProfile != null) {
            if (profileStack.size > 1) {
              profileStack = profileStack.dropLast(1)
              avatarProfileTransition = null
              val parentEntry = profileStack.lastOrNull()
              commentProfileReturnTransition = parentEntry?.commentTransition
              avatarProfileReturnTransition = parentEntry?.avatarTransition
            } else {
              restoreProfile(sourceProfile)
            }
          } else {
            profileMid = null
            profileStack = emptyList()
            profileLayerSuppressed = false
            avatarProfileTransition = null
            if (avatarProfileReturnTransition?.token == avatarSession.token) {
              avatarProfileReturnTransition = null
            }
            resumeVideoUnderProfile()
          }
        }
      }
      return
    }
    profileTransitionJob?.cancel()
    if (topProfileEntry?.returnsToArticle == true) {
      completeProfileReturnToArticle()
      return
    }
    if (topProfileEntry?.returnsToVideo == true) {
      profileStack = profileStack.dropLast(1)
      profileLayerSuppressed = true
      val parentEntry = profileStack.lastOrNull()
      commentProfileReturnTransition = parentEntry?.commentTransition
      avatarProfileReturnTransition = parentEntry?.avatarTransition
      resumeVideoUnderProfile()
      return
    }
    if (profileStack.size > 1) {
      profileStack = profileStack.dropLast(1)
      val parentEntry = profileStack.lastOrNull()
      commentProfileReturnTransition = parentEntry?.commentTransition
      avatarProfileReturnTransition = parentEntry?.avatarTransition
      return
    }
    profileMid = null
    profileStack = emptyList()
    profileLayerSuppressed = false
    resumeVideoUnderProfile()
  }

  fun showVideoPreview(item: FeedItem, fromHomeFeed: Boolean = false) {
    previewItem = item
    previewInfo = null
    previewFromHomeFeed = fromHomeFeed
    scope.launch {
      val bvid = item.videoUrl.substringAfterLast("/").substringBefore("?")
      val info =
        withContext(Dispatchers.IO) { runCatching { BiliApi.getVideoInfo(bvid) }.getOrNull() }
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
          runCatching { mid to BiliApi.isFollowing(mid) }.getOrNull()
        }
      }
    loaded.forEach { (mid, followed) -> followingStates[mid] = followed }
  }

  fun loadMentionSuggestions(query: String) {
    videoState.loadMentionSuggestions(query, authUserInfo.mid, scope)
  }

  fun snapshotEntry(item: FeedItem): VideoPageEntry =
    videoState.snapshotEntry(
      item,
      playerViewModel,
      currentPositionMs,
      videoPageDataReadyId,
      playbackEnded,
    )

  fun cacheEntry(entry: VideoPageEntry) {
    videoState.cacheEntry(entry, appState.selectedVideo?.id)
  }

  fun selectCommentSort(sort: CommentSort) {
    videoState.selectCommentSort(sort, { appState.selectedVideo?.id }, context, scope)
  }

  fun selectVideoPage(page: VideoPage) {
    val item = appState.selectedVideo ?: return
    val info = videoInfo ?: return
    if (page.cid <= 0L || page.cid == historyCid || info.pages.none { it.cid == page.cid }) return
    commitPlaybackProgress()
    videoStack.lastOrNull()?.let { currentFrame ->
      videoStack =
        videoStack.dropLast(1) +
          currentFrame.copy(sourceCardBounds = null, inPlaceSelectionChanged = true)
    }
    playerSession.clearPlaybackEnded()
    showEmbeddedCover = true
    currentPositionMs = 0L
    historyCid = page.cid
    historyDuration = page.durationSeconds
    historyStartTimestamp = System.currentTimeMillis() / 1000L
    danmaku = emptyList()
    videoEntryCache[item.id]?.let { entry ->
      cacheEntry(
        entry.copy(
          cid = page.cid,
          durationSeconds = page.durationSeconds,
          savedPositionMs = 0L,
          danmaku = emptyList(),
          playbackEnded = false,
        )
      )
    }
    playerViewModel.loadVideo(
      item = item,
      preferredResolutionMode = settings.preferredResolutionMode,
      page = page,
    )
  }

  fun restoreEntry(entry: VideoPageEntry) {
    videoState.restoreEntry(entry, playerSession)
    videoPageDataReadyId = entry.item.id.takeIf { entry.dataReady }
  }

  /** A card tap restarts a completed cache; video-stack returns retain its terminal overlay. */
  fun restoreEntryForFreshPlayback(entry: VideoPageEntry) {
    restoreEntry(entry)
    if (entry.playbackEnded) {
      playerSession.clearPlaybackEnded()
      playerSession.currentPositionMs = 0L
    }
  }

  fun clearVisibleVideoData() {
    videoState.clearVisibleVideoData(playerSession)
    videoPageDataReadyId = null
  }

  // Page data is owned by its cache entry rather than by the currently visible composition. A
  // parent page may therefore finish loading while a recommended child video is open.
  fun ensureVideoPageData(item: FeedItem) {
    videoState.ensureVideoPageData(
      item = item,
      scope = scope,
      selectedVideoId = { mainViewModel.state.value.selectedVideo?.id },
      dataCommitAllowedId = { dataCommitAllowedId },
      onRestore = ::restoreEntry,
      onDataReady = { videoPageDataReadyId = it },
    )
  }

  fun selectCollectionEpisode(episode: FeedItem) {
    val current = appState.selectedVideo ?: return
    if (
      episode.id == current.id ||
        transitionSession != null ||
        transitionPhase !is TransitionPhase.Video
    )
      return

    cacheEntry(snapshotEntry(current))
    commitPlaybackProgress()

    playerViewModel.cancelPendingLoad()
    playerViewModel.exoPlayer?.pause()
    val currentFrame = videoStack.lastOrNull()
    if (currentFrame != null) {
      videoStack =
        videoStack.dropLast(1) +
          currentFrame.copy(
            entryId = episode.id,
            item = episode,
            sourceCardBounds = null,
            inPlaceSelectionChanged = true,
          )
    }
    val retained = videoEntryCache[episode.id]
    if (retained != null) restoreEntry(retained) else clearVisibleVideoData()
    playerSession.clearPlaybackEnded()
    showEmbeddedCover = true
    dataCommitAllowedId = episode.id
    playerActivationId = episode.id
    transitionPhase = TransitionPhase.Video(episode, null)
    mainViewModel.replaceVideoInPlace(episode)
    ensureVideoPageData(episode)
    playerViewModel.loadVideo(
      item = episode,
      startPositionMs = retained?.savedPositionMs ?: 0L,
      preferredStreamIndex = retained?.qualityIndex,
      preferredResolutionMode = settings.preferredResolutionMode,
    )
  }

  // ── Load recommendations, comments & danmaku when entering video ────
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
      // A genuinely fresh card open may restart a completed cache.
      restoreEntryForFreshPlayback(cached)
    } else {
      // Stack returns deliberately keep the cached terminal state. Previously this unconditional
      // fresh restore cleared playbackEnded as soon as the parent was mounted, so its end overlay
      // stayed absent for the entire card flight and only reappeared in final cleanup.
      restoreEntry(cached)
    }
  }

  LaunchedEffect(
    appState.selectedVideo?.id,
    historyCid,
    dataCommitAllowedId,
    settings.danmakuDensity == 5,
    videoInfo?.publishedAt,
    videoInfo?.danmakuCount,
  ) {
    val item = appState.selectedVideo ?: return@LaunchedEffect
    if (dataCommitAllowedId != item.id || historyCid <= 0L) return@LaunchedEffect
    val expectedCid = historyCid
    val requestAllHistory = settings.danmakuDensity == 5
    val expectedDuration = historyDuration
    val expectedPublishedAt = videoInfo?.publishedAt ?: 0L
    val expectedDanmakuCount = videoInfo?.danmakuCount ?: 0L
    val cachedDanmaku = videoEntryCache[item.id]?.danmaku.orEmpty()
    if (cachedDanmaku.isNotEmpty()) {
      danmaku = cachedDanmaku
      if (!requestAllHistory) return@LaunchedEffect
    }

    val current =
      if (cachedDanmaku.isNotEmpty()) {
        cachedDanmaku
      } else {
        withContext(Dispatchers.IO) {
          runCatching {
              BiliApi.getDanmaku(
                cid = expectedCid,
                durationSeconds = expectedDuration,
              )
            }
            .getOrDefault(emptyList())
        }
      }
    if (appState.selectedVideo?.id != item.id || historyCid != expectedCid) return@LaunchedEffect
    if (current.isNotEmpty()) {
      danmaku = current
      videoEntryCache[item.id]
        ?.takeIf { it.cid == expectedCid }
        ?.let { cacheEntry(it.copy(danmaku = current)) }
    }
    // Wait for the video publication timestamp before walking history months. The effect is keyed
    // by videoInfo above and will restart as soon as metadata arrives.
    if (!requestAllHistory || expectedPublishedAt <= 0L) return@LaunchedEffect

    val loaded =
      withContext(Dispatchers.IO) {
        runCatching {
            BiliApi.getDanmaku(
              cid = expectedCid,
              durationSeconds = expectedDuration,
              publishedAt = expectedPublishedAt,
              expectedCount = expectedDanmakuCount,
              includeHistory = true,
            )
          }
          .getOrDefault(current)
      }
    if (
      appState.selectedVideo?.id != item.id ||
        historyCid != expectedCid ||
        settings.danmakuDensity != 5
    ) {
      return@LaunchedEffect
    }
    danmaku = loaded
    videoEntryCache[item.id]
      ?.takeIf { it.cid == expectedCid }
      ?.let { cacheEntry(it.copy(danmaku = loaded)) }
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
            BiliApi.getDanmakuMaskResource(
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
        runCatching { BiliApi.getReplyEmotes() }.getOrDefault(emptyList())
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
    if (
      transitionSession != null ||
        dataCommitAllowedId != appState.selectedVideo?.id ||
        appState.selectedVideo == null
    )
      return@LaunchedEffect
    val retained = appState.selectedVideo?.id?.let(videoEntryCache::get)
    videoEngagement = retained?.engagement ?: VideoEngagement()
    favoriteFolders = retained?.favoriteFolders.orEmpty()
    favoriteFoldersLoading = false
    videoActionBusy = false
    if (aid <= 0 || !authUserInfo.isLogin) return@LaunchedEffect
    val engagement =
      withContext(Dispatchers.IO) { runCatching { BiliApi.getVideoEngagement(aid) }.getOrNull() }
    if (videoInfo?.aid == aid && engagement != null) videoEngagement = engagement
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
          runCatching { BiliApi.getOnlineViewerText(historyAid, historyCid) }.getOrNull()
        }
      delay(30_000)
    }
  }

  // ── Login sheet ─────────────────────────────────────────────────────
  if (loginState !is LoginState.Idle) {
    LoginSheet(
      loginState = loginState,
      onDismiss = { authViewModel.cancelLogin() },
      onRetry = { authViewModel.retryLogin() },
    )
  }

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

  // ── Start enter transition ───────────────────────────────────────────
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
  ) {
    if (transitionPhase !is TransitionPhase.Feed) return
    val reuseCurrentPlayback =
      preserveCurrentPlayback &&
        renderedVideoId == item.id &&
        playerState is dev.openbili.webdemo.PlayerState.Ready
    val bangumiHomeEnter = origin == VideoOrigin.BANGUMI
    val bangumiIndexEnter = activeBangumiPage?.sourceOrigin == PageOrigin.BangumiIndex
    if (bangumiHomeEnter && item.coverUrl.isNotBlank()) {
      LoadedFeedImageRegistry.bitmap(bangumiPreviewCoverCacheKey(item.coverUrl))?.let { bitmap ->
        LoadedFeedImageRegistry.markLoaded(item.coverUrl, bitmap)
      }
    }
    rootPlayerOwnership =
      RootPlayerOwnership(
        if (reuseCurrentPlayback) RootPlayerSurfaceRole.DETAIL
        else RootPlayerSurfaceRole.DETAIL_PENDING,
        item.id,
      )
    pendingVideoCommentTarget = commentTarget
    keyboardController?.hide()
    val parent =
      when (origin) {
        VideoOrigin.HOME -> PageOrigin.Home
        VideoOrigin.MY -> PageOrigin.My
        VideoOrigin.SEARCH -> PageOrigin.Search
        VideoOrigin.BANGUMI -> PageOrigin.BangumiHome
        VideoOrigin.ARTICLE -> PageOrigin.Article
        VideoOrigin.OTHER -> PageOrigin.Other
      }
    videoStack =
      listOf(
        StackFrame(
          entryId = item.id,
          item = item,
          parentPage = parent,
          sourceCardBounds = cardBounds,
          rootFeedScrollAnchor = rootFeedScrollAnchor.takeIf { origin == VideoOrigin.HOME },
        )
      )
    dataCommitAllowedId = null
    playerActivationId = null
    showEmbeddedCover = cardBounds == null && !reuseCurrentPlayback
    // The destination shell shares one state holder across root videos. Reset or restore that
    // holder while it is still covered by the source page, so the reveal cannot expose the
    // previous video's comments, recommendations, or metadata.
    if (!bangumiHomeEnter && !bangumiIndexEnter) {
      videoEntryCache[item.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
    }
    val session = cardBounds?.let { source ->
      CardTransitionSession(
          token = ++transitionToken,
          kind = TransitionKind.ENTER_ROOT,
          item = item,
          startBounds = source,
          endBounds = source,
          initialProgress = 0f,
          fitCover = fitCover,
          reusePlayerSurface = reuseCurrentPlayback,
          requiredSignals = playerTransitionRequiredSignals,
        )
        .also {
          it.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
          transitionSession = it
        }
    }
    transitionPhase = TransitionPhase.ToVideo(item, cardBounds)
    playerBounds = Rect.Zero
    mainViewModel.openVideo(item)
    launchTransition {
      if (bangumiHomeEnter) {
        playerViewModel.cancelPendingLoad()
        playerViewModel.exoPlayer?.pause()
      }
      withFrameNanos {}
      val target =
        session?.let {
          transitionTargetBounds?.let { bounds -> prepareCardTransition(it, bounds) }
            ?: prepareCardTransition(it)
        } ?: awaitStablePlayerBounds()
      if (session == null || target == Rect.Zero || target.width <= 0f) {
        session?.apply {
          phase = SessionPhase.CANCELLED
          preparation.cancel()
        }
        if (transitionSession === session) transitionSession = null
        transitionPhase = TransitionPhase.Video(item, cardBounds)
        if (bangumiHomeEnter || bangumiIndexEnter) {
          videoEntryCache[item.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
          if (bangumiHomeEnter) deferBangumiHomePageComposition = false
          withFrameNanos {}
          awaitMainMessageQueueIdle()
        }
        onLanded?.invoke()
        dataCommitAllowedId = item.id
        playerActivationId = item.id
        showEmbeddedCover = !reuseCurrentPlayback
        if (!reuseCurrentPlayback) {
          playerViewModel.loadVideo(
            item,
            startPositionMs = startPositionMs,
            preferredResolutionMode = settings.preferredResolutionMode,
            restoreSavedProgress = restoreSavedProgress,
          )
        }
        return@launchTransition
      }
      session.endBounds = target
      hiddenFeedCoverItemId = item.id.takeIf { origin == VideoOrigin.HOME }
      hiddenMyCoverItemId = item.id.takeIf { origin == VideoOrigin.MY }
      hiddenSearchCoverItemId = item.id.takeIf { origin == VideoOrigin.SEARCH }
      hiddenBangumiIndexItemId = item.id.takeIf { bangumiIndexEnter }
      hiddenBangumiRecommendationItemId =
        item.id.takeIf { origin == VideoOrigin.BANGUMI && !reuseCurrentPlayback }
      hiddenArticleVideoCoverItemId = item.id.takeIf { origin == VideoOrigin.ARTICLE }
      session.backgroundStarted = true
      session.phase = SessionPhase.FLYING
      kotlinx.coroutines.coroutineScope {
        launch {
          session.progress.animateTo(
            1f,
            tween(if (settings.reduceMotion) 140 else 400, easing = FastOutSlowInEasing),
          )
        }
        launch {
          delay(if (settings.reduceMotion) 10 else 45)
          session.backgroundAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
          )
        }
        if (bangumiHomeEnter) {
          launch {
            session.themeScrimAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 100 else 260, easing = FastOutSlowInEasing),
            )
          }
        }
      }
      if (session.reverseRequested) return@launchTransition
      session.phase = SessionPhase.REVEALING_BACKGROUND
      if (bangumiHomeEnter || bangumiIndexEnter) {
        // The flight owns the frame budget. Only mount the complete player page once its geometry
        // has landed, then yield to the main queue before fading in the information panels.
        videoEntryCache[item.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
        if (bangumiHomeEnter) deferBangumiHomePageComposition = false
        if (bangumiIndexEnter) onLanded?.invoke()
        withFrameNanos {}
        awaitMainMessageQueueIdle()
        withFrameNanos {}
      }
      withFrameNanos {}
      kotlinx.coroutines.coroutineScope {
        launch {
          session.panelAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 90 else 180, easing = FastOutSlowInEasing),
          )
        }
        if (bangumiHomeEnter) {
          launch {
            session.themeScrimAlpha.animateTo(
              0f,
              tween(if (settings.reduceMotion) 80 else 180, easing = FastOutSlowInEasing),
            )
          }
        }
      }
      if (session.reverseRequested) return@launchTransition
      if (reuseCurrentPlayback) {
        session.phase = SessionPhase.WAITING_FIRST_FRAME
        playerActivationId = item.id
        revealTransitionSession(session)
        while (transitionSession?.token == session.token) withFrameNanos {}
        onLanded?.invoke()
        dataCommitAllowedId = item.id
      } else if (bangumiHomeEnter || bangumiIndexEnter) {
        session.phase = SessionPhase.WAITING_FIRST_FRAME
        playerActivationId = item.id
        playerViewModel.loadVideo(
          item,
          startPositionMs = startPositionMs,
          preferredResolutionMode = settings.preferredResolutionMode,
          restoreSavedProgress = restoreSavedProgress,
        )
        // The stationary card owns the media wait and first-frame reveal. Defer comments,
        // metadata, danmaku and their state commits until that final fade has fully completed.
        while (transitionSession?.token == session.token) withFrameNanos {}
        if (bangumiHomeEnter) onLanded?.invoke()
        dataCommitAllowedId = item.id
      } else {
        onLanded?.invoke()
        dataCommitAllowedId = item.id
        session.phase = SessionPhase.WAITING_FIRST_FRAME
        playerActivationId = item.id
        playerViewModel.loadVideo(
          item,
          startPositionMs = startPositionMs,
          preferredResolutionMode = settings.preferredResolutionMode,
          restoreSavedProgress = restoreSavedProgress,
        )
      }
    }
  }

  fun startProfileVideo(profileEntryId: Long, item: FeedItem, cardBounds: Rect) {
    val sourceEntry = activeProfileEntry(profileEntryId) ?: return
    val sourceState = sourceEntry.state
    val sourceMid = sourceState.profileMid ?: return
    if (
      transitionSession != null ||
        transitionPhase !is TransitionPhase.Feed && transitionPhase !is TransitionPhase.Video
    )
      return
    // Once the profile is hidden behind its child video, a profile opened from that video will use
    // the global transition slots. Persist this profile's own return route before that can happen.
    profileStack =
      profileStack.retainReturnTransitionsFor(
        sourceEntry.entryId,
        commentProfileReturnTransition,
        avatarProfileReturnTransition,
      )
    val bounds = cardBounds.takeIf { it != Rect.Zero && it.width > 0f && it.height > 0f }
    val fromVideo = transitionPhase is TransitionPhase.Video
    val currentVideo = appState.selectedVideo
    keyboardController?.hide()
    if (fromVideo && currentVideo != null) cacheEntry(snapshotEntry(currentVideo))
    val expandedStack =
      (if (fromVideo) videoStack else emptyList()) +
        StackFrame(
          entryId = item.id,
          item = item,
          parentPage = PageOrigin.Profile(sourceEntry.entryId, sourceMid),
          sourceCardBounds = bounds,
          sourceProfile = sourceState.snapshotProfile(sourceMid),
        )
    videoStack =
      if (expandedStack.size <= MAX_VIDEO_STACK_DEPTH) expandedStack
      else expandedStack.takeLast(MAX_VIDEO_STACK_DEPTH)
    dataCommitAllowedId = null
    playerActivationId = null
    showEmbeddedCover = false
    // The source profile already covers the shared video shell. Preserve any parent video in its
    // cache, then stage only the clicked video's state before that shell becomes visible.
    videoEntryCache[item.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
    val session = bounds?.let { source ->
      CardTransitionSession(
          token = ++transitionToken,
          kind = TransitionKind.ENTER_PROFILE,
          item = item,
          startBounds = source,
          endBounds = source,
          initialProgress = 0f,
          initialPanelAlpha = 0f,
          requiredSignals = playerTransitionRequiredSignals,
        )
        .also {
          it.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
          transitionSession = it
        }
    }
    transitionPhase = TransitionPhase.ToVideo(item, bounds, fromVideo = fromVideo)
    if (!fromVideo) playerBounds = Rect.Zero
    // Match the root-feed transition: compose only the destination shell first. Video data and
    // playback remain blocked by dataCommitAllowedId/playerActivationId until the flight ends.
    mainViewModel.openVideo(item)
    launchTransition {
      withFrameNanos {}
      val target = session?.let { prepareCardTransition(it) } ?: awaitStablePlayerBounds()
      if (session == null || target == Rect.Zero || target.width <= 0f || target.height <= 0f) {
        session?.apply {
          phase = SessionPhase.CANCELLED
          preparation.cancel()
        }
        if (transitionSession === session) transitionSession = null
        profileLayerSuppressed = true
        hiddenProfileCoverItemId = null
        profileVideoTransitionActive = false
        transitionPhase = TransitionPhase.Video(item, null)
        dataCommitAllowedId = item.id
        playerActivationId = item.id
        showEmbeddedCover = true
        playerViewModel.loadVideo(
          item,
          preferredResolutionMode = settings.preferredResolutionMode,
        )
        return@launchTransition
      }
      session.endBounds = target
      hiddenProfileCoverItemId = item.id
      profileVideoTransitionActive = true
      session.backgroundStarted = true
      session.phase = SessionPhase.FLYING
      kotlinx.coroutines.coroutineScope {
        launch {
          session.progress.animateTo(
            1f,
            tween(if (settings.reduceMotion) 140 else 400, easing = FastOutSlowInEasing),
          )
        }
        launch {
          delay(if (settings.reduceMotion) 10 else 45)
          session.backgroundAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
          )
        }
      }
      if (session.reverseRequested) return@launchTransition
      profileLayerSuppressed = true
      hiddenProfileCoverItemId = null
      profileVideoTransitionActive = false
      session.phase = SessionPhase.REVEALING_BACKGROUND
      withFrameNanos {}
      session.panelAlpha.animateTo(
        1f,
        tween(if (settings.reduceMotion) 90 else 180, easing = FastOutSlowInEasing),
      )
      if (session.reverseRequested) return@launchTransition
      dataCommitAllowedId = item.id
      session.phase = SessionPhase.WAITING_FIRST_FRAME
      playerActivationId = item.id
      playerViewModel.loadVideo(item, preferredResolutionMode = settings.preferredResolutionMode)
    }
  }

  fun selectBangumiEpisode(episode: BangumiEpisode) {
    val page = activeBangumiPage ?: return
    if (episode.id <= 0L || episode.id == page.currentEpisodeId) return
    val item =
      FeedItem(
        id = "${page.sourceCard.id}:ep${episode.id}",
        title =
          listOf(episode.title, episode.longTitle)
            .filter(String::isNotBlank)
            .joinToString(" · ")
            .ifBlank { page.sourceCard.title },
        videoUrl = "https://www.bilibili.com/bangumi/play/ep${episode.id}",
        coverUrl = episode.coverUrl.ifBlank { page.sourceCard.coverUrl },
        uploader = appState.selectedVideo?.uploader,
        playCount = null,
        duration = null,
        uploaderFace = appState.selectedVideo?.uploaderFace,
        uploaderMid = appState.selectedVideo?.uploaderMid ?: 0L,
        description = page.season?.evaluate.orEmpty(),
      )
    BangumiPlaybackStore.save(
      context.applicationContext,
      page.sourceCard,
      page.season?.seasonId ?: 0L,
      episode,
    )
    selectCollectionEpisode(item)
    // selectCollectionEpisode() commits the old aid/cid. Only then publish the new episode so
    // the background heartbeat cannot attribute the old episode's final position to the new one.
    activeBangumiPage = page.copy(currentEpisodeId = episode.id)
    if (
      page.sourceOrigin == PageOrigin.BangumiHome &&
        page.sourceCard.seasonId > 0L &&
        page.season?.seasonId == page.sourceCard.seasonId
    ) {
      // Update the retained source card while it is hidden behind the player. The final position
      // is patched again on exit, but updating the artwork here gives the card time to decode
      // the selected episode cover before the shared-element flight returns.
      bangumiExploreViewModel.applyFollowingPlayback(
        seasonId = page.sourceCard.seasonId,
        episode = episode,
        positionMs = 0L,
      )
    }
  }

  fun selectBangumiSeason(seasonId: Long) {
    val page = activeBangumiPage ?: return
    if (
      seasonId <= 0L ||
        seasonId == page.season?.seasonId ||
        page.loading ||
        transitionSession != null
    ) {
      return
    }
    val pageId = page.sourceCard.id
    activeBangumiPage = page.copy(loading = true, error = null)
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.getBangumiSeason(seasonId = seasonId) }
        }
      val current = activeBangumiPage
      if (current?.sourceCard?.id != pageId) return@launch
      result
        .onSuccess { season ->
          activeBangumiPage =
            current.copy(
              season =
                season.copy(
                  followed =
                    season.followed ||
                      (
                        current.sourceFollowedByViewer &&
                          season.seasonId == current.sourceSeasonId
                      ),
                ),
              loading = false,
              error = null,
              currentEpisodeId = 0L,
              seasonChangedFromSource =
                current.sourceSeasonId > 0L && season.seasonId != current.sourceSeasonId,
            )
          season.episodes
            .ifEmpty {
              season.sections.firstOrNull { it.episodes.isNotEmpty() }?.episodes.orEmpty()
            }
            .firstOrNull()
            ?.let(::selectBangumiEpisode)
        }
        .onFailure { error ->
          activeBangumiPage =
            current.copy(loading = false, error = error.message ?: "季度加载失败")
        }
    }
  }

  fun toggleBangumiFollow() {
    if (!authUserInfo.isLogin) {
      authViewModel.startLogin()
      return
    }
    val page = activeBangumiPage ?: return
    val season = page.season ?: return
    if (page.followBusy || season.seasonId <= 0L) return
    val target = !season.followed
    val pageId = page.sourceCard.id
    activeBangumiPage = page.copy(followBusy = true)
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching {
            BiliApi.setBangumiFollow(season.seasonId, target)
            BiliApi.getBangumiSeason(seasonId = season.seasonId)
          }
        }
      val current = activeBangumiPage
      if (current?.sourceCard?.id != pageId) return@launch
      result
        .onSuccess { confirmedSeason ->
          activeBangumiPage =
            current.copy(
              followBusy = false,
              // A successful mutation is the authoritative result. The read-after-write endpoint
              // can briefly return the old relation, so do not let it flip the button back.
              season = confirmedSeason.copy(followed = target),
            )
          Toast.makeText(
              context,
              if (target) {
                if (page.sourceCard.kind == dev.openbili.webdemo.api.SpaceContentKind.DRAMA)
                  "已追剧"
                else "已追番"
              } else "已取消追番追剧",
              Toast.LENGTH_SHORT,
            )
            .show()
        }
        .onFailure { error ->
          activeBangumiPage = current.copy(followBusy = false)
          Toast.makeText(context, error.message ?: "追番操作失败", Toast.LENGTH_SHORT).show()
        }
    }
  }

  fun postBangumiShortReview(score: Int, content: String) {
    if (!authUserInfo.isLogin) {
      authViewModel.startLogin()
      return
    }
    val mediaId = activeBangumiPage?.season?.mediaId ?: 0L
    if (mediaId <= 0L) return
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.postBangumiShortReview(mediaId, score, content) }
        }
      result
        .onSuccess {
          activeBangumiPage?.takeIf { it.season?.mediaId == mediaId }?.let { current ->
            activeBangumiPage =
              current.copy(
                season = current.season?.copy(userRatingScore = score),
              )
          }
          Toast.makeText(context, "短评发布成功", Toast.LENGTH_SHORT).show()
        }
        .onFailure { error ->
          Toast.makeText(context, error.message ?: "短评发布失败", Toast.LENGTH_SHORT).show()
        }
    }
  }

  fun loadActiveBangumiMetadata(card: SpaceContentCard) {
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching {
            BiliApi.getBangumiSeason(
              seasonId = card.seasonId,
              episodeId = card.episodeId,
              bvid = card.bvid,
              aid = card.aid,
            )
          }
        }
      val current = activeBangumiPage
      if (current?.sourceCard?.id != card.id) return@launch
      activeBangumiPage =
        result.fold(
          onSuccess = { season ->
            val playableEpisodes =
              (season.episodes + season.sections.flatMap { it.episodes }).distinctBy { it.id }
            val selectedId =
              card.episodeId
                .takeIf { requested -> playableEpisodes.any { it.id == requested } }
                ?: card.bvid
                  .takeIf(String::isNotBlank)
                  ?.let { requested ->
                    playableEpisodes.firstOrNull { it.bvid.equals(requested, ignoreCase = true) }?.id
                  }
                ?: card.aid
                  .takeIf { it > 0L }
                  ?.let { requested -> playableEpisodes.firstOrNull { it.aid == requested }?.id }
                ?: playableEpisodes.firstOrNull()?.id
                ?: 0L
            val fellBack = selectedId > 0L && selectedId != card.episodeId && card.episodeId > 0L
            if (fellBack && !current.playbackFallbackEmitted) {
              val fallbackEp = playableEpisodes.firstOrNull { it.id == selectedId }
              if (fallbackEp != null) {
                val fallbackItem =
                  FeedItem(
                    id = "${current.sourceCard.id}:ep$selectedId",
                    title = current.sourceCard.title,
                    videoUrl = "https://www.bilibili.com/bangumi/play/ep$selectedId",
                    coverUrl = current.sourceCard.coverUrl,
                    uploader = null,
                    playCount = null,
                    duration = null,
                    description = current.sourceCard.subtitle,
                  )
                playerViewModel.loadVideo(
                  item = fallbackItem,
                  startPositionMs = 0L,
                  preferredResolutionMode = settings.preferredResolutionMode,
                  restoreSavedProgress = true,
                )
              }
            }
            val sourceSeasonId =
              current.sourceSeasonId.takeIf { it > 0L } ?: season.seasonId
            current.copy(
              sourceSeasonId = sourceSeasonId,
              season =
                season.copy(
                  followed =
                    season.followed ||
                      (
                        current.sourceFollowedByViewer &&
                          season.seasonId == sourceSeasonId
                      ),
                ),
              loading = false,
              error = null,
              currentEpisodeId = selectedId,
              seasonChangedFromSource =
                sourceSeasonId > 0L && season.seasonId != sourceSeasonId,
              playbackFallbackEmitted = fellBack || current.playbackFallbackEmitted,
            )
          },
          onFailure = { error ->
            current.copy(loading = false, error = error.message ?: "番剧资料加载失败")
          },
        )
    }
  }

  LaunchedEffect(
    activeBangumiPage?.currentEpisodeId,
    activeBangumiPage?.season?.seasonId,
    videoPageDataReadyId,
    dataCommitAllowedId,
  ) {
    val page = activeBangumiPage ?: return@LaunchedEffect
    val item = appState.selectedVideo ?: return@LaunchedEffect
    if (dataCommitAllowedId != item.id) return@LaunchedEffect
    val episode =
      page.season
        ?.let { it.episodes + it.sections.flatMap(BangumiSection::episodes) }
        ?.firstOrNull { it.id == page.currentEpisodeId }
        ?: return@LaunchedEffect
    if (episode.cid <= 0L || episode.durationSeconds <= 0L) return@LaunchedEffect
    val cidChanged = historyCid != episode.cid
    historyCid = episode.cid
    historyDuration = episode.durationSeconds
    if (cidChanged) {
      danmaku = emptyList()
      danmakuMask = null
    }
    videoEntryCache[item.id]?.let { entry ->
      cacheEntry(
        entry.copy(
          cid = episode.cid,
          durationSeconds = episode.durationSeconds,
          danmaku = if (cidChanged) emptyList() else entry.danmaku,
          danmakuMask = if (cidChanged) null else entry.danmakuMask,
        )
      )
    }
  }

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
  ) {
    if (activeBangumiPage != null || transitionPhase !is TransitionPhase.Feed) return
    val localSelection =
      if (restoreEpisodeSelection) BangumiPlaybackStore.read(context.applicationContext, card) else null
    val entryTarget = resolveBangumiEntryTarget(card, localSelection, restoreEpisodeSelection)
    val resolvedCard = entryTarget.card
    val playbackItem =
      item.copy(
        videoUrl = resolvedCard.videoUrl,
      )
    if (
      resolvedCard.episodeId != card.episodeId || resolvedCard.seasonId != card.seasonId
    ) {
      videoEntryCache.remove(playbackItem.id)
    }
    val bounds = cardBounds.takeIf { it.hasUsableSize() }
    bounds?.let {
      when (pageOrigin) {
        PageOrigin.My -> myCardBounds[playbackItem.id] = it
        PageOrigin.Search -> searchCardBounds[playbackItem.id] = it
        PageOrigin.BangumiIndex -> bangumiIndexCardBounds[playbackItem.id] = it
        else -> Unit
      }
    }
    activeBangumiPage =
      ActiveBangumiPage(
        sourceCard = card,
        sourceProfileEntryId = 0L,
        sourceMid = authUserInfo.mid,
        sourceBounds = bounds,
        sourceVideoCoverUrl =
          item.coverUrl
            .takeIf { pageOrigin == PageOrigin.My || pageOrigin == PageOrigin.BangumiHome }
            .orEmpty(),
        returnToSourceCover = returnToSourceCover,
        sourceUsesLivePlayer = preserveCurrentPlayback,
        sourceOrigin = pageOrigin,
        sourceFollowedByViewer = initialSeason?.followed == true,
        season = initialSeason,
        loading = initialSeason == null,
        currentEpisodeId = resolvedCard.episodeId,
      )
    deferBangumiHomePageComposition = pageOrigin == PageOrigin.BangumiHome
    deferBangumiIndexPageComposition = pageOrigin == PageOrigin.BangumiIndex
    bangumiPosterBounds = Rect.Zero
    startEnterVideo(
      item = playbackItem,
      cardBounds = bounds,
      origin = videoOrigin,
      onLanded = {
        deferBangumiIndexPageComposition = false
        if (activeBangumiPage?.season == null) loadActiveBangumiMetadata(resolvedCard)
      },
      fitCover = pageOrigin == PageOrigin.Search || pageOrigin == PageOrigin.BangumiIndex,
      preserveCurrentPlayback = preserveCurrentPlayback,
      startPositionMs = entryTarget.startPositionMs,
      restoreSavedProgress = !entryTarget.serverResumeAuthoritative,
      transitionTargetBounds =
        if (pageOrigin == PageOrigin.BangumiIndex) {
          { bangumiPosterBounds }
        } else null,
    )
  }

  fun startHistoryBangumi(card: SpaceContentCard, item: FeedItem, cardBounds: Rect) {
    startRootBangumi(card, item, cardBounds, PageOrigin.My, VideoOrigin.MY)
  }

  fun startSearchBangumi(
    card: SpaceContentCard,
    cardBounds: Rect,
    sourceIsBangumiExplorePoster: Boolean = false,
    sourceOrigin: PageOrigin = PageOrigin.Search,
  ) {
    if (
      card.videoUrl.isBlank() ||
        transitionSession != null ||
        activeBangumiPage != null ||
        transitionPhase !is TransitionPhase.Feed
    ) {
      return
    }
    val restoredCard = restoredBangumiCard(card)
    val item = restoredCard.toBangumiVideoItem()
    if (
      restoredCard.episodeId != card.episodeId || restoredCard.seasonId != card.seasonId
    ) {
      videoEntryCache.remove(item.id)
    }
    val bounds = cardBounds.takeIf { it.hasUsableSize() }
    fun setPortraitSourceHidden(hidden: Boolean) {
      when (sourceOrigin) {
        PageOrigin.BangumiIndex -> hiddenBangumiIndexItemId = card.id.takeIf { hidden }
        PageOrigin.Search -> {
          if (sourceIsBangumiExplorePoster) {
            hiddenBangumiRecommendationItemId = card.id.takeIf { hidden }
          } else {
            hiddenSearchCoverItemId = card.id.takeIf { hidden }
          }
        }
        else -> Unit
      }
    }
    bounds?.let {
      when (sourceOrigin) {
        PageOrigin.BangumiIndex -> bangumiIndexCardBounds[card.id] = it
        PageOrigin.Search -> searchCardBounds[card.id] = it
        else -> Unit
      }
    }
    keyboardController?.hide()
    videoStack =
      listOf(
        StackFrame(
          entryId = item.id,
          item = item,
          parentPage = sourceOrigin,
          sourceCardBounds = bounds,
        )
      )
    activeBangumiPage =
      ActiveBangumiPage(
        sourceCard = card,
        sourceProfileEntryId = 0L,
        sourceMid = authUserInfo.mid,
        sourceBounds = bounds,
        sourceOrigin = sourceOrigin,
        sourceIsBangumiExplorePoster = sourceIsBangumiExplorePoster,
      )
    deferSearchBangumiPageComposition = sourceOrigin == PageOrigin.Search
    deferBangumiIndexPageComposition = sourceOrigin == PageOrigin.BangumiIndex
    bangumiPosterBounds = Rect.Zero
    dataCommitAllowedId = null
    playerActivationId = null
    showEmbeddedCover = false
    videoEntryCache[item.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
    val session =
      bounds?.let { source ->
        CardTransitionSession(
            token = ++transitionToken,
            kind = TransitionKind.ENTER_ROOT,
            item = item,
            startBounds = source,
            endBounds = source,
            initialProgress = 0f,
            initialPanelAlpha = 0f,
            fitCover = true,
            requiredSignals = playerTransitionRequiredSignals,
          )
          .also {
            it.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
            transitionSession = it
          }
      }
    transitionPhase = TransitionPhase.ToVideo(item, bounds)
    playerBounds = Rect.Zero
    mainViewModel.openVideo(item)
    launchTransition {
      withFrameNanos {}
      val target =
        session?.let { prepareCardTransition(it) { bangumiPosterBounds } }
          ?: bangumiPosterBounds.takeIf { it.hasUsableSize() }
          ?: Rect.Zero
      if (!target.hasUsableSize()) {
        session?.apply {
          phase = SessionPhase.CANCELLED
          preparation.cancel()
        }
        if (transitionSession === session) transitionSession = null
        setPortraitSourceHidden(false)
        transitionPhase = TransitionPhase.Video(item, null)
        deferSearchBangumiPageComposition = false
        deferBangumiIndexPageComposition = false
        withFrameNanos {}
        loadActiveBangumiMetadata(restoredCard)
        dataCommitAllowedId = item.id
        playerActivationId = item.id
        showEmbeddedCover = true
        playerViewModel.loadVideo(item, preferredResolutionMode = settings.preferredResolutionMode)
        return@launchTransition
      }
      session?.endBounds = target
      setPortraitSourceHidden(true)
      session?.apply {
        backgroundStarted = true
        phase = SessionPhase.FLYING
      }
      if (session != null) {
        coroutineScope {
          launch {
            session.progress.animateTo(
              1f,
              tween(if (settings.reduceMotion) 140 else 400, easing = FastOutSlowInEasing),
            )
          }
          launch {
            delay(if (settings.reduceMotion) 10 else 45)
            session.backgroundAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
            )
          }
        }
        if (session.reverseRequested) return@launchTransition
      }
      setPortraitSourceHidden(false)
      session?.phase = SessionPhase.REVEALING_BACKGROUND
      deferSearchBangumiPageComposition = false
      deferBangumiIndexPageComposition = false
      // The shared-poster flight owns the frame budget. Mount the complete page only after it has
      // landed, then let the new composition settle before beginning the component reveal.
      withFrameNanos {}
      awaitMainMessageQueueIdle()
      withFrameNanos {}
      session?.panelAlpha?.animateTo(
        1f,
        tween(if (settings.reduceMotion) 90 else 190, easing = FastOutSlowInEasing),
      )
      if (session?.reverseRequested == true) return@launchTransition
      loadActiveBangumiMetadata(restoredCard)
      dataCommitAllowedId = item.id
      if (session != null) session.phase = SessionPhase.WAITING_FIRST_FRAME
      else transitionPhase = TransitionPhase.Video(item, null)
      playerActivationId = item.id
      playerViewModel.loadVideo(item, preferredResolutionMode = settings.preferredResolutionMode)
    }
  }

  fun startProfileBangumi(
    profileEntryId: Long,
    card: SpaceContentCard,
    cardBounds: Rect,
  ) {
    val sourceEntry = activeProfileEntry(profileEntryId) ?: return
    val sourceState = sourceEntry.state
    val sourceMid = sourceState.profileMid ?: return
    if (
      card.videoUrl.isBlank() ||
        transitionSession != null ||
        activeBangumiPage != null ||
        transitionPhase !is TransitionPhase.Feed && transitionPhase !is TransitionPhase.Video
    ) {
      return
    }
    val bounds = cardBounds.takeIf { it.hasUsableSize() }
    val fromVideo = transitionPhase is TransitionPhase.Video
    val currentVideo = appState.selectedVideo
    val restoredCard = restoredBangumiCard(card)
    val item =
      restoredCard.toBangumiVideoItem(
        uploader = sourceState.spaceProfile?.name,
        uploaderFace = sourceState.spaceProfile?.face,
        uploaderMid = sourceMid,
      )
    if (
      restoredCard.episodeId != card.episodeId || restoredCard.seasonId != card.seasonId
    ) {
      videoEntryCache.remove(item.id)
    }
    keyboardController?.hide()
    if (fromVideo && currentVideo != null) cacheEntry(snapshotEntry(currentVideo))
    val expandedStack =
      (if (fromVideo) videoStack else emptyList()) +
        StackFrame(
          entryId = item.id,
          item = item,
          parentPage = PageOrigin.Profile(sourceEntry.entryId, sourceMid),
          sourceCardBounds = bounds,
          sourceProfile = sourceState.snapshotProfile(sourceMid),
        )
    videoStack =
      if (expandedStack.size <= MAX_VIDEO_STACK_DEPTH) expandedStack
      else expandedStack.takeLast(MAX_VIDEO_STACK_DEPTH)
    activeBangumiPage =
      ActiveBangumiPage(
        sourceCard = card,
        sourceProfileEntryId = profileEntryId,
        sourceMid = sourceMid,
        sourceBounds = bounds,
        sourceFollowedByViewer = sourceMid == authUserInfo.mid,
      )
    bangumiPosterBounds = Rect.Zero
    dataCommitAllowedId = null
    playerActivationId = null
    showEmbeddedCover = false
    videoEntryCache[item.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
    val session =
      bounds?.let { source ->
        CardTransitionSession(
            token = ++transitionToken,
            kind = TransitionKind.ENTER_PROFILE,
            item = item,
            startBounds = source,
            endBounds = source,
            initialProgress = 0f,
            initialPanelAlpha = 0f,
            fitCover = true,
            requiredSignals = playerTransitionRequiredSignals,
          )
          .also {
            it.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
            transitionSession = it
          }
      }
    transitionPhase = TransitionPhase.ToVideo(item, bounds, fromVideo = fromVideo)
    if (!fromVideo) playerBounds = Rect.Zero
    mainViewModel.openVideo(item)
    launchTransition {
      withFrameNanos {}
      val target =
        session?.let { prepareCardTransition(it) { bangumiPosterBounds } }
          ?: bangumiPosterBounds.takeIf { it.hasUsableSize() }
          ?: Rect.Zero
      if (!target.hasUsableSize()) {
        session?.apply {
          phase = SessionPhase.CANCELLED
          preparation.cancel()
        }
        if (transitionSession === session) transitionSession = null
        profileLayerSuppressed = false
        hiddenProfileCoverItemId = null
        profileVideoTransitionActive = false
        transitionPhase = TransitionPhase.Video(item, null)
        loadActiveBangumiMetadata(restoredCard)
        dataCommitAllowedId = item.id
        playerActivationId = item.id
        showEmbeddedCover = true
        playerViewModel.loadVideo(item, preferredResolutionMode = settings.preferredResolutionMode)
        return@launchTransition
      }
      session?.endBounds = target
      hiddenProfileCoverItemId = card.id
      profileVideoTransitionActive = true
      session?.apply {
        backgroundStarted = true
        phase = SessionPhase.FLYING
      }
      if (session != null) {
        coroutineScope {
          launch {
            session.progress.animateTo(
              1f,
              tween(if (settings.reduceMotion) 140 else 400, easing = FastOutSlowInEasing),
            )
          }
          launch {
            delay(if (settings.reduceMotion) 10 else 45)
            session.backgroundAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
            )
          }
        }
        if (session.reverseRequested) return@launchTransition
      }
      loadActiveBangumiMetadata(restoredCard)
      profileLayerSuppressed = false
      hiddenProfileCoverItemId = null
      profileVideoTransitionActive = false
      session?.phase = SessionPhase.REVEALING_BACKGROUND
      withFrameNanos {}
      session?.panelAlpha?.animateTo(
        1f,
        tween(if (settings.reduceMotion) 90 else 190, easing = FastOutSlowInEasing),
      )
      if (session?.reverseRequested == true) return@launchTransition
      dataCommitAllowedId = item.id
      if (session != null) session.phase = SessionPhase.WAITING_FIRST_FRAME
      else transitionPhase = TransitionPhase.Video(item, null)
      playerActivationId = item.id
      playerViewModel.loadVideo(item, preferredResolutionMode = settings.preferredResolutionMode)
    }
  }

  fun beginVideoExitPrelude(
    item: FeedItem,
    bounds: Rect,
    fitCover: Boolean = false,
    reusePlayerSurface: Boolean = false,
  ): VideoExitPrelude {
    val transitionBitmap =
      LoadedFeedImageRegistry.bitmap(item.coverUrl, requireUncropped = fitCover)
        ?: activeBangumiPage
          ?.takeIf { it.sourceOrigin == PageOrigin.BangumiHome && !fitCover }
          ?.let { LoadedFeedImageRegistry.bitmap(bangumiPreviewCoverCacheKey(item.coverUrl)) }
    val prelude =
      VideoExitPrelude(
          item = item,
          playerBounds = bounds,
          fitCover = fitCover,
          reusePlayerSurface = reusePlayerSurface,
          coverAlpha = Animatable(if (fitCover && transitionBitmap != null) 1f else 0f),
        )
        .also { it.transitionBitmap = transitionBitmap }
    videoExitPrelude = prelude
    playerViewModel.exoPlayer?.pause()
    return prelude
  }

  suspend fun animateVideoExitPrelude(
    prelude: VideoExitPrelude,
    onPageFade: (suspend () -> Unit)? = null,
  ) {
    // Exit preparation may have populated the registry after the prelude was created.
    if (prelude.transitionBitmap == null) {
      prelude.transitionBitmap =
        LoadedFeedImageRegistry.bitmap(
          prelude.item.coverUrl,
          requireUncropped = prelude.fitCover,
        )
          ?: activeBangumiPage
            ?.takeIf { it.sourceOrigin == PageOrigin.BangumiHome && !prelude.fitCover }
            ?.let {
              LoadedFeedImageRegistry.bitmap(
                bangumiPreviewCoverCacheKey(prelude.item.coverUrl)
              )
            }
    }
    // The cover must completely replace the live video before the destination starts changing.
    if (prelude.fitCover && prelude.transitionBitmap != null) {
      prelude.coverAlpha.snapTo(1f)
    } else {
      prelude.coverAlpha.animateTo(
        1f,
        tween(if (settings.reduceMotion) 70 else 140, easing = FastOutSlowInEasing),
      )
    }
    withFrameNanos {}
    rootPlayerOwnership =
      RootPlayerOwnership(
        RootPlayerSurfaceRole.EXIT_COVERED,
        rootPlayerOwnership.mediaId ?: prelude.item.id,
      )
    // Commit the ownership change underneath the fully opaque stationary cover before it moves.
    withFrameNanos {}
    withFrameNanos {}
    kotlinx.coroutines.coroutineScope {
      launch {
        prelude.pageAlpha.animateTo(
          0f,
          tween(if (settings.reduceMotion) 90 else 200, easing = FastOutSlowInEasing),
        )
      }
      onPageFade?.let { fade -> launch { fade() } }
    }
  }

  suspend fun fadeOutVideoExitPrelude(prelude: VideoExitPrelude) {
    prelude.coverAlpha.animateTo(
      0f,
      tween(if (settings.reduceMotion) 80 else 180, easing = FastOutSlowInEasing),
    )
    if (videoExitPrelude === prelude) videoExitPrelude = null
  }

  fun startExitRootBangumi(
    page: ActiveBangumiPage,
    departing: FeedItem,
  ) {
    val returnPreviewTarget =
      bangumiPreviewTarget.takeIf { page.sourceOrigin == PageOrigin.BangumiHome }
    val portraitSource =
      page.sourceOrigin == PageOrigin.Search || page.sourceOrigin == PageOrigin.BangumiIndex
    val reusePlayerSurface =
      page.sourceUsesLivePlayer &&
        page.currentEpisodeId == page.sourceCard.episodeId &&
        renderedVideoId == departing.id
    val savedPosterBounds = if (portraitSource) bangumiPosterBounds else playerBounds
    // Portrait source cards represent the work that was clicked, not the currently selected
    // season inside detail. They always receive the original cover back when a stable source
    // boundary still exists.
    val skipPosterFlight =
      page.seasonChangedFromSource &&
        page.sourceOrigin !in setOf(PageOrigin.Search, PageOrigin.BangumiIndex)
    val destination =
      if (skipPosterFlight) null
      else
        when (page.sourceOrigin) {
          PageOrigin.My -> myCardBounds[page.sourceCard.id]
          PageOrigin.Search -> searchCardBounds[page.sourceCard.id]
          PageOrigin.BangumiIndex -> bangumiIndexCardBounds[page.sourceCard.id]
          else -> null
        } ?: page.sourceBounds
    val currentEpisode =
      page.season
        ?.let { it.episodes + it.sections.flatMap(BangumiSection::episodes) }
        ?.firstOrNull { it.id == page.currentEpisodeId }
    val currentEpisodeCover =
      currentEpisode?.coverUrl.orEmpty().ifBlank { departing.coverUrl }
    if (
      page.sourceOrigin == PageOrigin.BangumiHome &&
        page.sourceCard.seasonId > 0L &&
        page.season?.seasonId == page.sourceCard.seasonId &&
        currentEpisode != null
    ) {
      // Capture the last position before cancelling the player load. This updates the hidden
      // destination card while the exit cover is still opaque.
      bangumiExploreViewModel.applyFollowingPlayback(
        seasonId = page.sourceCard.seasonId,
        episode = currentEpisode,
        positionMs = playerViewModel.exoPlayer?.currentPosition ?: currentPositionMs,
      )
    }
    val posterItem =
      departing.copy(
        id = page.sourceCard.id,
        title = page.season?.title ?: page.sourceCard.title,
        // Search cards retain the portrait season artwork. The retained Bangumi rail uses the
        // currently selected episode's horizontal cover so the destination and flight agree.
        coverUrl =
          if (portraitSource) {
            // Search return must reuse the exact source request key/bitmap. Season metadata may
            // spell the same CDN artwork differently and would otherwise trigger a second decode
            // plus a visible handoff at the destination card.
            page.sourceCard.coverUrl
          } else if (page.sourceOrigin == PageOrigin.BangumiHome && page.returnToSourceCover) {
            page.sourceVideoCoverUrl.ifBlank { page.sourceCard.coverUrl }
          } else if (page.sourceOrigin == PageOrigin.BangumiHome) {
            currentEpisodeCover.ifBlank { page.sourceVideoCoverUrl }
          } else {
            page.sourceVideoCoverUrl.ifBlank { departing.coverUrl }
          },
      )
    val prelude =
      beginVideoExitPrelude(
        posterItem,
        bounds = if (skipPosterFlight) Rect.Zero else savedPosterBounds,
        fitCover = portraitSource && !skipPosterFlight,
        reusePlayerSurface = reusePlayerSurface && !skipPosterFlight,
      )
    playerViewModel.cancelPendingLoad()
    launchTransition {
      var bangumiHandoffPrepared = false
      if (skipPosterFlight) {
        bangumiSeasonExitFadeAlpha.snapTo(0f)
        coroutineScope {
          launch {
            prelude.pageAlpha.animateTo(
              0f,
              tween(if (settings.reduceMotion) 90 else 220, easing = FastOutSlowInEasing),
            )
          }
          launch {
            bangumiSeasonExitFadeAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 100 else 260, easing = FastOutSlowInEasing),
            )
          }
        }
      } else {
        val exitSession =
          if (destination?.hasUsableSize() == true && savedPosterBounds.hasUsableSize()) {
            CardTransitionSession(
                token = ++transitionToken,
                kind = TransitionKind.EXIT_ROOT,
                item = posterItem,
                startBounds = destination,
                endBounds = savedPosterBounds,
                initialProgress = 1f,
                initialPanelAlpha = 0f,
                fitCover = portraitSource,
                reusePlayerSurface = reusePlayerSurface,
                requiredSignals = exitTransitionRequiredSignals,
              )
              .also {
                it.preparation.markReady(
                  TransitionReadySignal.SOURCE_BOUNDS,
                  TransitionReadySignal.SOURCE_SNAPSHOT,
                )
                transitionSession = it
              }
          } else null
        if (exitSession != null) {
          val preparedBounds =
            prepareExitTransition(exitSession) {
              when (page.sourceOrigin) {
                PageOrigin.My -> myCardBounds[page.sourceCard.id]
                PageOrigin.Search -> searchCardBounds[page.sourceCard.id]
                PageOrigin.BangumiIndex -> bangumiIndexCardBounds[page.sourceCard.id]
                else -> null
              } ?: destination
            }
          if (preparedBounds.hasUsableSize()) exitSession.startBounds = preparedBounds
          if (page.sourceOrigin == PageOrigin.My) hiddenMyCoverItemId = page.sourceCard.id
          if (page.sourceOrigin == PageOrigin.Search) {
            if (page.sourceIsBangumiExplorePoster) {
              hiddenBangumiRecommendationItemId = page.sourceCard.id
            } else {
              hiddenSearchCoverItemId = page.sourceCard.id
            }
          }
          if (page.sourceOrigin == PageOrigin.BangumiIndex) {
            hiddenBangumiIndexItemId = page.sourceCard.id
          }
          if (page.sourceOrigin == PageOrigin.BangumiHome && !reusePlayerSurface) {
            hiddenBangumiRecommendationItemId = page.sourceCard.id
          }
          withFrameNanos {}
          // The Bangumi Hero remains a stable retained layer. First replace the detail player with
          // the stationary right-card cover and fade out only the detail page; then move that one
          // cover back to its card. Do not put a full-screen theme bridge between these stages,
          // because it momentarily hides/reveals the Hero and makes it look like a second shared
          // element.
          animateVideoExitPrelude(prelude) {
            if (portraitSource) {
              exitSession.panelAlpha.animateTo(
                1f,
                tween(if (settings.reduceMotion) 90 else 200, easing = FastOutSlowInEasing),
              )
            }
          }
          exitSession.phase = SessionPhase.FLYING
          withFrameNanos {}
          if (videoExitPrelude === prelude) videoExitPrelude = null
          exitSession.progress.animateTo(
            0f,
            tween(if (settings.reduceMotion) 140 else 340, easing = FastOutSlowInEasing),
          )
          if (page.sourceOrigin == PageOrigin.BangumiHome) {
            // The homepage preview owns its own TextureView player. The detail player is now warm
            // idle while the retained homepage becomes visible under the returning card cover.
            rootPlayerOwnership = RootPlayerOwnership(RootPlayerSurfaceRole.IDLE)
            exitSession.phase = SessionPhase.REVEALING_BACKGROUND
            withFrameNanos {}
            withFrameNanos {}
            bangumiHandoffPrepared = true
          }
        } else {
          animateVideoExitPrelude(prelude)
          fadeOutVideoExitPrelude(prelude)
        }
      }
      if (page.sourceOrigin == PageOrigin.BangumiHome && !bangumiHandoffPrepared) {
        rootPlayerOwnership = RootPlayerOwnership(RootPlayerSurfaceRole.IDLE)
        withFrameNanos {}
        withFrameNanos {}
      }
      if (page.sourceOrigin != PageOrigin.BangumiHome && (reusePlayerSurface || returnPreviewTarget != null)) {
        rootPlayerOwnership = RootPlayerOwnership(RootPlayerSurfaceRole.IDLE)
        withFrameNanos {}
      }
      // Snapshotting large page state and writing playback progress are intentionally outside the
      // moving interval. They may allocate or enqueue IO, but can no longer steal an animation frame.
      cacheEntry(snapshotEntry(departing))
      commitPlaybackProgress()
      dataCommitAllowedId = null
      playerActivationId = null
      mainViewModel.returnToFeed()
      videoStack = emptyList()
      bangumiPosterBounds = Rect.Zero
      transitionPhase = TransitionPhase.Feed
      if (page.sourceOrigin == PageOrigin.BangumiHome) {
        // Commit the now-visible retained root page before releasing the transition session. Media
        // preview activation is still gated, so no PV prepare can compete with the card flight.
        withFrameNanos {}
        withFrameNanos {}
      }
      activeBangumiPage = null
      transitionSession = null
      hiddenMyCoverItemId = null
      hiddenSearchCoverItemId = null
      hiddenBangumiIndexItemId = null
      hiddenBangumiRecommendationItemId = null
      withFrameNanos {}
      if (page.sourceOrigin == PageOrigin.BangumiHome && page.sourceCard.seasonId > 0L) {
        // The returned cover has landed on the source card. After a short settle, move the
        // just-watched season to the front of the retained "正在追" rail. moveFollowingToFront()
        // no-ops when the season is not in the rail, and animateItem() drives the reorder.
        val frontSeasonId = page.sourceCard.seasonId
        scope.launch {
          delay(220)
          bangumiExploreViewModel.moveFollowingToFront(frontSeasonId)
        }
      }
      if (videoExitPrelude === prelude) videoExitPrelude = null
      if (skipPosterFlight) {
        bangumiSeasonExitFadeAlpha.animateTo(
          0f,
          tween(if (settings.reduceMotion) 90 else 190, easing = FastOutSlowInEasing),
        )
      }
    }
  }

  fun startExitBangumi() {
    if (transitionSession != null || videoExitPrelude != null) return
    val page = activeBangumiPage ?: return
    val departing = appState.selectedVideo ?: return
    val departingFrame = videoStack.lastOrNull() ?: return
    if (
      page.sourceOrigin == PageOrigin.My ||
        page.sourceOrigin == PageOrigin.Search ||
        page.sourceOrigin == PageOrigin.BangumiIndex ||
        page.sourceOrigin == PageOrigin.BangumiHome
    ) {
      startExitRootBangumi(page, departing)
      return
    }
    val origin = departingFrame.parentPage as? PageOrigin.Profile ?: return
    val savedPosterBounds = bangumiPosterBounds
    val skipPosterFlight = page.seasonChangedFromSource
    val destination =
      if (skipPosterFlight) null
      else
        page.sourceBounds
          ?: profileCardBounds[ProfileVideoKey(page.sourceProfileEntryId, page.sourceCard.id)]
    val remainingStack = videoStack.dropLast(1)
    val parentFrame = remainingStack.lastOrNull()
    val posterItem =
      page.sourceCard
        .copy(
          title = page.season?.title ?: page.sourceCard.title,
          coverUrl = page.season?.coverUrl?.takeIf(String::isNotBlank) ?: page.sourceCard.coverUrl,
        )
        .toBangumiVideoItem(
        uploader = departing.uploader,
        uploaderFace = departing.uploaderFace,
        uploaderMid = departing.uploaderMid,
      )
    cacheEntry(snapshotEntry(departing))
    commitPlaybackProgress()
    val prelude =
      beginVideoExitPrelude(
        posterItem,
        bounds = if (skipPosterFlight) Rect.Zero else savedPosterBounds,
        fitCover = !skipPosterFlight,
      )
    playerViewModel.cancelPendingLoad()
    fun restoreUnderlyingParent() {
      if (parentFrame != null) {
        videoEntryCache[parentFrame.entryId]?.let(::restoreEntry) ?: clearVisibleVideoData()
        dataCommitAllowedId = parentFrame.item.id
        showEmbeddedCover = true
        mainViewModel.openVideo(parentFrame.item)
        transitionPhase = TransitionPhase.Video(parentFrame.item, parentFrame.sourceCardBounds)
        ensureVideoPageData(parentFrame.item)
      } else {
        mainViewModel.returnToFeed()
        dataCommitAllowedId = null
        playerActivationId = null
        transitionPhase = TransitionPhase.Feed
      }
    }
    launchTransition {
      val exitSession =
        if (destination?.hasUsableSize() == true && savedPosterBounds.hasUsableSize()) {
          CardTransitionSession(
              token = ++transitionToken,
              kind = TransitionKind.EXIT_PROFILE,
              item = posterItem,
              startBounds = destination,
              endBounds = savedPosterBounds,
              initialProgress = 1f,
              initialPanelAlpha = 0f,
              fitCover = true,
              requiredSignals = exitTransitionRequiredSignals,
            )
            .also {
              it.preparation.markReady(
                TransitionReadySignal.SOURCE_BOUNDS,
                TransitionReadySignal.SOURCE_SNAPSHOT,
              )
              transitionSession = it
            }
        } else null
      profileLayerSuppressed = false
      val retainedProfile = activeProfileEntry(origin.entryId)
      if (retainedProfile == null) {
        departingFrame.sourceProfile?.let(::restoreProfile) ?: loadProfile(origin.mid)
      }
      commentProfileTransition = null
      if (skipPosterFlight) {
        // A different season no longer has a meaningful source poster on the retained profile.
        // SurfaceView content cannot be reliably faded by a parent graphicsLayer. Fade the entire
        // window through an opaque handoff so the player, poster, and panels leave as one image.
        bangumiSeasonExitFadeAlpha.snapTo(0f)
        coroutineScope {
          launch {
            prelude.pageAlpha.animateTo(
              0f,
              tween(if (settings.reduceMotion) 90 else 220, easing = FastOutSlowInEasing),
            )
          }
          launch {
            bangumiSeasonExitFadeAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 100 else 260, easing = FastOutSlowInEasing),
            )
          }
        }
        restoreUnderlyingParent()
        activeBangumiPage = null
        withFrameNanos {}
        if (videoExitPrelude === prelude) videoExitPrelude = null
        bangumiSeasonExitFadeAlpha.animateTo(
          0f,
          tween(if (settings.reduceMotion) 90 else 190, easing = FastOutSlowInEasing),
        )
      } else if (exitSession != null) {
        profileVideoTransitionActive = true
        val preparedBounds =
          prepareExitTransition(exitSession) {
            profileCardBounds[ProfileVideoKey(page.sourceProfileEntryId, page.sourceCard.id)]
              ?: destination
          }
        if (preparedBounds.hasUsableSize()) exitSession.startBounds = preparedBounds
        hiddenProfileCoverItemId = page.sourceCard.id
        withFrameNanos {}
        prelude.transitionBitmap = exitSession.transitionBitmap ?: prelude.transitionBitmap
        animateVideoExitPrelude(prelude) {
          exitSession.panelAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 90 else 200, easing = FastOutSlowInEasing),
          )
        }
        restoreUnderlyingParent()
        activeBangumiPage = null
        exitSession.phase = SessionPhase.FLYING
        withFrameNanos {}
        if (videoExitPrelude === prelude) videoExitPrelude = null
        exitSession.progress.animateTo(
          0f,
          tween(if (settings.reduceMotion) 140 else 360, easing = FastOutSlowInEasing),
        )
      } else {
        animateVideoExitPrelude(prelude)
        restoreUnderlyingParent()
        activeBangumiPage = null
        fadeOutVideoExitPrelude(prelude)
      }
      withFrameNanos {}
      videoStack = remainingStack
      hiddenProfileCoverItemId = null
      withFrameNanos {}
      transitionSession = null
      profileVideoTransitionActive = false
      bangumiPosterBounds = Rect.Zero
      if (parentFrame != null) {
        dataCommitAllowedId = parentFrame.item.id
        playerActivationId = null
      }
    }
  }

  fun startExitVideoToProfile() {
    if (transitionSession != null || videoExitPrelude != null) return
    val departing = appState.selectedVideo ?: return
    val departingFrame = videoStack.lastOrNull() ?: return
    val origin = departingFrame.parentPage as? PageOrigin.Profile ?: return
    val savedPlayerBounds = playerBounds
    val destinationKey = ProfileVideoKey(origin.entryId, departing.id)
    val destination =
      if (departingFrame.inPlaceSelectionChanged) null
      else
        departingFrame.sourceCardBounds
          ?: profileCardBounds[destinationKey]
    val remainingStack = videoStack.dropLast(1)
    val parentFrame = remainingStack.lastOrNull()
    cacheEntry(snapshotEntry(departing))
    commitPlaybackProgress()
    val prelude = beginVideoExitPrelude(departing, savedPlayerBounds)
    playerViewModel.cancelPendingLoad()
    fun restoreUnderlyingParent() {
      if (parentFrame != null) {
        videoEntryCache[parentFrame.entryId]?.let(::restoreEntry) ?: clearVisibleVideoData()
        dataCommitAllowedId = parentFrame.item.id
        showEmbeddedCover = true
        mainViewModel.openVideo(parentFrame.item)
        transitionPhase = TransitionPhase.Video(parentFrame.item, parentFrame.sourceCardBounds)
        ensureVideoPageData(parentFrame.item)
      } else {
        mainViewModel.returnToFeed()
        dataCommitAllowedId = null
        playerActivationId = null
        transitionPhase = TransitionPhase.Feed
      }
    }

    launchTransition {
      val exitSession =
        if (
          destination != null &&
            destination != Rect.Zero &&
            savedPlayerBounds != Rect.Zero &&
            savedPlayerBounds.width > 0f
        ) {
          CardTransitionSession(
              token = ++transitionToken,
              kind = TransitionKind.EXIT_PROFILE,
              item = departing,
              startBounds = destination,
              endBounds = savedPlayerBounds,
              initialProgress = 1f,
              initialPanelAlpha = 0f,
              requiredSignals = exitTransitionRequiredSignals,
            )
            .also {
              it.preparation.markReady(
                TransitionReadySignal.SOURCE_BOUNDS,
                TransitionReadySignal.SOURCE_SNAPSHOT,
              )
              transitionSession = it
            }
        } else null
      // A profile opened above this video removes the underlying profile from composition. Its old
      // card coordinates may still be cached when that nested profile closes, making the return
      // flight collapse or disappear. Drop them before remounting so the source profile must report
      // a fresh destination for this exit.
      profileCardBounds.remove(destinationKey)
      profileLayerSuppressed = false
      val retainedProfile = activeProfileEntry(origin.entryId)
      if (retainedProfile == null) {
        departingFrame.sourceProfile?.let(::restoreProfile) ?: loadProfile(origin.mid)
      }
      commentProfileTransition = null
      if (exitSession != null) {
        profileVideoTransitionActive = true
        // Give the restored profile a short, frame-based opportunity to remeasure. Falling back to
        // the captured entry bounds keeps the animation available when the card is currently off
        // screen, without waiting on I/O or loading during the animation itself.
        repeat(8) {
          if (profileCardBounds[destinationKey]?.hasUsableSize() == true) return@repeat
          withFrameNanos {}
        }
        val remountedDestination =
          profileCardBounds[destinationKey]?.takeIf { it.hasUsableSize() } ?: destination
        val preparedBounds =
          prepareExitTransition(exitSession) {
            profileCardBounds[destinationKey] ?: remountedDestination
          }
        if (preparedBounds.hasUsableSize()) exitSession.startBounds = preparedBounds
        hiddenProfileCoverItemId = departing.id
        // The restored profile is already mounted by prepareExitTransition. Keep it fully visible
        // below the video page, hide its destination cover, then begin the ordered cover/page fade.
        withFrameNanos {}
        animateVideoExitPrelude(prelude) {
          exitSession.panelAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 90 else 200, easing = FastOutSlowInEasing),
          )
        }
        restoreUnderlyingParent()
        exitSession.phase = SessionPhase.FLYING
        withFrameNanos {}
        if (videoExitPrelude === prelude) videoExitPrelude = null
        exitSession.progress.animateTo(
          0f,
          tween(if (settings.reduceMotion) 140 else 360, easing = FastOutSlowInEasing),
        )
      } else {
        animateVideoExitPrelude(prelude)
        restoreUnderlyingParent()
        fadeOutVideoExitPrelude(prelude)
      }
      withFrameNanos {}
      videoStack = remainingStack
      hiddenProfileCoverItemId = null
      // Reveal the real card cover underneath the overlay before discarding the p=0 flying copy.
      withFrameNanos {}
      transitionSession = null
      profileVideoTransitionActive = false
      if (parentFrame != null) {
        dataCommitAllowedId = parentFrame.item.id
        // Keep the underlying parent selected but paused while its profile child is visible.
        playerActivationId = null
      }
    }
  }

  fun cancelPreparingProfileVideo() {
    val departingFrame = videoStack.lastOrNull() ?: return
    if (departingFrame.parentPage !is PageOrigin.Profile) return
    activeTransitionJob?.cancel()
    activeTransitionJob = null
    playerViewModel.cancelPendingLoad()
    val remainingStack = videoStack.dropLast(1)
    val parentFrame = remainingStack.lastOrNull()
    if (parentFrame != null) {
      videoEntryCache[parentFrame.entryId]?.let(::restoreEntry) ?: clearVisibleVideoData()
      dataCommitAllowedId = parentFrame.item.id
      playerActivationId = null
      showEmbeddedCover = true
      mainViewModel.openVideo(parentFrame.item)
      transitionPhase = TransitionPhase.Video(parentFrame.item, parentFrame.sourceCardBounds)
    } else {
      mainViewModel.returnToFeed()
      dataCommitAllowedId = null
      playerActivationId = null
      transitionPhase = TransitionPhase.Feed
    }
    videoStack = remainingStack
    activeBangumiPage = null
    bangumiPosterBounds = Rect.Zero
    hiddenProfileCoverItemId = null
    profileVideoTransitionActive = false
    transitionSession = null
  }

  // ── Start exit transition ────────────────────────────────────────────
  fun startExitVideo() {
    if (videoExitPrelude != null) return
    keyboardController?.hide()
    val item = appState.selectedVideo ?: return
    val frame = videoStack.firstOrNull() ?: return
    val prelude = beginVideoExitPrelude(item, playerBounds)
    cacheEntry(snapshotEntry(item))
    commitPlaybackProgress()
    val returnBounds = frame.sourceCardBounds.takeUnless { frame.inPlaceSelectionChanged }
    fun latestReturnBounds(): Rect? =
      when (frame.parentPage) {
        PageOrigin.Home -> feedCardBounds[item.id]
        PageOrigin.My -> myCardBounds[item.id]
        PageOrigin.Search -> searchCardBounds[item.id]
        PageOrigin.Article -> articleVideoBounds[item.id]
        else -> null
      }

    fun resolveReturnBounds(fallback: Rect?): Rect? {
      val latest = latestReturnBounds()
      // Search stays mounted underneath the video and can report the player rectangle after an
      // intervening profile navigation. Other mature root sources keep their existing policy.
      return if (frame.parentPage == PageOrigin.Search)
        resolveExitTransitionTargetBounds(
          latest = latest,
          fallback = fallback,
          playerBounds = playerBounds,
        )
      else latest ?: fallback
    }

    transitionPhase = TransitionPhase.ToFeed(item, returnBounds)
    launchTransition {
      var bounds = returnBounds
      if (bounds != null) {
        if (frame.parentPage == PageOrigin.Home && frame.rootFeedScrollAnchor != null) {
          val itemCount =
            (feedState as? dev.openbili.webdemo.feed.FeedUiState.Content)?.items?.size ?: 0
          if (itemCount > 0) {
            val anchor = frame.rootFeedScrollAnchor
            feedGridState.scrollToItem(
              anchor.firstVisibleItemIndex.coerceIn(0, itemCount - 1),
              anchor.firstVisibleItemScrollOffset,
            )
            withFrameNanos {}
            withFrameNanos {}
          }
        }
        val latestBounds = resolveReturnBounds(bounds) ?: bounds
        val safeTop = with(rootDensity) { 86.dp.toPx() }
        if (
          frame.parentPage == PageOrigin.Home &&
            frame.rootFeedScrollAnchor == null &&
            latestBounds.top < safeTop
        ) {
          val items = (feedState as? dev.openbili.webdemo.feed.FeedUiState.Content)?.items
          val index = items?.indexOfFirst { it.id == item.id } ?: -1
          if (index >= 0) {
            val rowStart = index - index % 3
            val anchorRowStart = (rowStart - 3).coerceAtLeast(0)
            feedGridState.scrollToItem(anchorRowStart)
            delay(32)
          }
        }
        if (
          frame.parentPage == PageOrigin.Search &&
            latestBounds.top < with(rootDensity) { 176.dp.toPx() }
        ) {
          val portraitResults =
            searchState.category == dev.openbili.webdemo.search.SearchCategory.BANGUMI ||
              searchState.category == dev.openbili.webdemo.search.SearchCategory.CINEMA
          val index =
            if (portraitResults) searchState.bangumiResults.indexOfFirst { it.id == item.id }
            else searchState.results.indexOfFirst { it.id == item.id }
          if (index >= 0) {
            val columns = if (portraitResults) 5 else 3
            val rowStart = index - index % columns
            searchGridState.scrollToItem((rowStart - columns).coerceAtLeast(0))
            withFrameNanos {}
            withFrameNanos {}
          }
        }
        bounds = resolveReturnBounds(bounds)
      }
      transitionPhase = TransitionPhase.ToFeed(item, bounds)
      if (bounds != null && playerBounds != Rect.Zero) {
        val initialBounds = bounds
        val session =
          CardTransitionSession(
              token = ++transitionToken,
              kind = TransitionKind.EXIT_ROOT,
              item = item,
              startBounds = initialBounds,
              endBounds = playerBounds,
              initialProgress = 1f,
              initialPanelAlpha = 0f,
              requiredSignals = exitTransitionRequiredSignals,
            )
            .also {
              it.preparation.markReady(
                TransitionReadySignal.SOURCE_BOUNDS,
                TransitionReadySignal.SOURCE_SNAPSHOT,
              )
              transitionSession = it
            }
        val preparedBounds =
          prepareExitTransition(session) {
            resolveReturnBounds(initialBounds)
          }
        if (preparedBounds.hasUsableSize()) bounds = preparedBounds
        hiddenFeedCoverItemId = item.id.takeIf { frame.parentPage == PageOrigin.Home }
        hiddenMyCoverItemId = item.id.takeIf { frame.parentPage == PageOrigin.My }
        hiddenSearchCoverItemId = item.id.takeIf { frame.parentPage == PageOrigin.Search }
        hiddenArticleVideoCoverItemId = item.id.takeIf { frame.parentPage == PageOrigin.Article }
        // The retained source page becomes visible during the prelude. Hide its real cover before
        // that fade starts so only the player-position cover can hand off into the flight layer.
        withFrameNanos {}
        animateVideoExitPrelude(prelude)
        session.startBounds = bounds
        session.phase = SessionPhase.FLYING
        withFrameNanos {}
        if (videoExitPrelude === prelude) videoExitPrelude = null
        session.progress.animateTo(
          0f,
          tween(if (settings.reduceMotion) 140 else 340, easing = FastOutSlowInEasing),
        )
      } else {
        animateVideoExitPrelude(prelude)
        mainViewModel.returnToFeed()
        videoStack = emptyList()
        transitionPhase = TransitionPhase.Feed
        hiddenFeedCoverItemId = null
        hiddenMyCoverItemId = null
        hiddenSearchCoverItemId = null
        hiddenArticleVideoCoverItemId = null
        withFrameNanos {}
        withFrameNanos {}
        fadeOutVideoExitPrelude(prelude)
        return@launchTransition
      }
      withFrameNanos {}
      mainViewModel.returnToFeed()
      videoStack = emptyList()
      transitionSession = null
      transitionPhase = TransitionPhase.Feed
      hiddenFeedCoverItemId = null
      hiddenMyCoverItemId = null
      hiddenSearchCoverItemId = null
      hiddenArticleVideoCoverItemId = null
    }
  }

  fun startBackToPreviousVideo() {
    if (videoExitPrelude != null) return
    keyboardController?.hide()
    if (videoStack.size < 2) return startExitVideo()
    val departingFrame = videoStack.last()
    val parentFrame = videoStack[videoStack.lastIndex - 1]
    val departing = appState.selectedVideo ?: return
    cacheEntry(snapshotEntry(departing))
    commitPlaybackProgress()
    // Pause synchronously on the click path, before the cover starts replacing the live frame.
    playerViewModel.exoPlayer?.pause()
    playerSession.isPlaying = false
    val parentEntry = videoEntryCache[parentFrame.entryId]
    val savedPlayerBounds = playerBounds
    val destination =
      departingFrame.sourceCardBounds.takeUnless {
        departingFrame.inPlaceSelectionChanged
      }
    // Preserve the established three-stage return: cover replaces the live child player, the
    // child page fades, then that covered player flies back into the parent recommendation card.
    val prelude = beginVideoExitPrelude(departing, savedPlayerBounds)
    mainViewModel.onFullscreenChanged(false)
    playerViewModel.cancelPendingLoad()
    showEmbeddedCover = true
    transitionPhase =
      TransitionPhase.ToPreviousVideo(
        departingItem = departing,
        previousItem = parentFrame.item,
        cardBounds = destination ?: Rect.Zero,
        previousSourceBounds = parentFrame.sourceCardBounds,
      )
    launchTransition {
      val session =
        if (destination != null && destination != Rect.Zero && savedPlayerBounds != Rect.Zero) {
          CardTransitionSession(
              token = ++transitionToken,
              kind = TransitionKind.EXIT_RECOMMENDATION,
              item = departing,
              startBounds = destination,
              endBounds = savedPlayerBounds,
              initialProgress = 1f,
              // EXIT_RECOMMENDATION maps this value as 1 - panelAlpha. Keep the child panels
              // visible while the exit prelude fades them, then reset to 1 after restoring the
              // parent and animate back to 0 while the cover flies home.
              initialPanelAlpha = 0f,
              requiredSignals = exitTransitionRequiredSignals,
            )
            .also {
              it.preparation.markReady(
                TransitionReadySignal.SOURCE_BOUNDS,
                TransitionReadySignal.SOURCE_SNAPSHOT,
              )
              transitionSession = it
            }
        } else null
      // Start the visible prelude immediately. The return destination was frozen when the child
      // opened, so this path must not wait on a new image decode or layout-stability barrier.
      animateVideoExitPrelude(prelude)
      hiddenRecommendationCoverItemId =
        departing.id.takeUnless { departingFrame.sourceWasPlaybackEndRecommendation }
      hiddenPlaybackEndRecommendationCoverItemId =
        departing.id.takeIf { departingFrame.sourceWasPlaybackEndRecommendation }
      // Restore and compose the parent before the departing cover starts flying back. Previously
      // this happened after the flight, leaving an empty page underneath the overlay.
      if (parentEntry != null) restoreEntry(parentEntry) else clearVisibleVideoData()
      dataCommitAllowedId = parentFrame.item.id
      // A completed parent already owns a retained end-card. Do not restart the ordinary
      // loading-cover pipeline behind it while returning from a recommended child video.
      showEmbeddedCover = parentEntry?.playbackEnded != true
      mainViewModel.openVideo(parentFrame.item)
      transitionPhase =
        TransitionPhase.ToPreviousVideo(
          departingItem = departing,
          previousItem = parentFrame.item,
          cardBounds = destination ?: Rect.Zero,
          previousSourceBounds = parentFrame.sourceCardBounds,
        )
      ensureVideoPageData(parentFrame.item)
      withFrameNanos {}
      withFrameNanos {}
      if (session != null) {
        session.panelAlpha.snapTo(1f)
        if (videoExitPrelude === prelude) videoExitPrelude = null
        session.phase = SessionPhase.FLYING
        coroutineScope {
          launch {
            session.progress.animateTo(
              0f,
              tween(if (settings.reduceMotion) 140 else 340, easing = FastOutSlowInEasing),
            )
          }
          launch {
            session.panelAlpha.animateTo(
              0f,
              tween(if (settings.reduceMotion) 100 else 240, easing = FastOutSlowInEasing),
            )
          }
        }
      } else {
        fadeOutVideoExitPrelude(prelude)
      }
      withFrameNanos {}
      videoStack = videoStack.dropLast(1)
      transitionSession = null
      transitionPhase = TransitionPhase.Video(parentFrame.item, parentFrame.sourceCardBounds)
      hiddenRecommendationCoverItemId = null
      hiddenPlaybackEndRecommendationCoverItemId = null
      dataCommitAllowedId = parentFrame.item.id
      val restore = videoEntryCache[parentFrame.entryId]
      if (restore?.playbackEnded == true) {
        // A stack return should reveal the parent's terminal cover/end-card, rather than prepare
        // media behind the departing cover flight and accidentally restart playback.
        playerSession.restorePlaybackEnded(true)
        playerActivationId = null
      } else {
        rootPlayerOwnership =
          RootPlayerOwnership(RootPlayerSurfaceRole.DETAIL_PENDING, parentFrame.item.id)
        playerActivationId = parentFrame.item.id
        playerViewModel.loadVideo(
          parentFrame.item,
          startPositionMs = restore?.savedPositionMs ?: 0L,
          preferredStreamIndex = restore?.qualityIndex,
          preferredResolutionMode = settings.preferredResolutionMode,
        )
      }
    }
  }

  fun reverseActiveEnter() {
    val session = transitionSession ?: return
    if (
      session.reverseRequested ||
        session.kind !in
          setOf(
            TransitionKind.ENTER_ROOT,
            TransitionKind.ENTER_RECOMMENDATION,
            TransitionKind.ENTER_PROFILE,
          )
    )
      return
    session.reverseRequested = true
    session.preparation.cancel()
    // Until the recommendation commits, playerActivationId still belongs to the parent. Keep that
    // exact ExoPlayer load alive during a mid-flight reversal instead of rebuilding it from cache.
    val retainedRecommendationParent =
      if (session.kind == TransitionKind.ENTER_RECOMMENDATION)
        videoStack.dropLast(1).lastOrNull()?.takeIf { playerActivationId == it.item.id }
      else null
    if (retainedRecommendationParent == null) playerViewModel.cancelPendingLoad()
    activeRevealJob?.cancel()
    activeRevealJob = null
    launchTransition {
      session.phase = SessionPhase.CANCELLED
      val returningParent =
        if (
          session.kind == TransitionKind.ENTER_RECOMMENDATION ||
            session.kind == TransitionKind.ENTER_PROFILE
        )
          videoStack.dropLast(1).lastOrNull()
        else null
      val returningProfile =
        if (session.kind == TransitionKind.ENTER_PROFILE)
          (videoStack.lastOrNull()?.parentPage as? PageOrigin.Profile)
        else null
      if (returningProfile != null) {
        profileLayerSuppressed = false
        if (activeProfileEntry(returningProfile.entryId) == null) {
          videoStack.lastOrNull()?.sourceProfile?.let(::restoreProfile)
            ?: loadProfile(returningProfile.mid)
        }
        commentProfileTransition = null
        avatarProfileTransition = null
        hiddenProfileCoverItemId = session.item.id
        profileVideoTransitionActive = true
      }
      if (
        returningParent != null &&
          mainViewModel.state.value.selectedVideo?.id != returningParent.item.id
      ) {
        videoEntryCache[returningParent.entryId]?.let(::restoreEntry) ?: clearVisibleVideoData()
        dataCommitAllowedId = returningParent.item.id
        showEmbeddedCover = true
        mainViewModel.openVideo(returningParent.item)
        ensureVideoPageData(returningParent.item)
        withFrameNanos {}
        withFrameNanos {}
      }
      if (session.kind == TransitionKind.ENTER_PROFILE && returningParent == null) {
        mainViewModel.returnToFeed()
        dataCommitAllowedId = null
        playerActivationId = null
      }
      val duration =
        ((if (settings.reduceMotion) 140 else 360) * session.progress.value)
          .toInt()
          .coerceAtLeast(1)
      kotlinx.coroutines.coroutineScope {
        launch {
          session.progress.animateTo(0f, tween(duration, easing = FastOutSlowInEasing))
        }
        launch {
          session.coverAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 50 else 100, easing = FastOutSlowInEasing),
          )
        }
        launch {
          session.panelAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 50 else 100, easing = FastOutSlowInEasing),
          )
        }
        launch {
          session.backgroundAlpha.animateTo(
            0f,
            tween(if (settings.reduceMotion) 50 else 100, easing = FastOutSlowInEasing),
          )
        }
        launch {
          session.bridgeAlpha.animateTo(
            0f,
            tween(if (settings.reduceMotion) 50 else 100, easing = FastOutSlowInEasing),
          )
        }
        launch {
          session.themeScrimAlpha.animateTo(
            0f,
            tween(if (settings.reduceMotion) 80 else 180, easing = FastOutSlowInEasing),
          )
        }
      }
      withFrameNanos {}
      if (
        session.reusePlayerSurface &&
          activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome
      ) {
        rootPlayerOwnership = RootPlayerOwnership(RootPlayerSurfaceRole.IDLE)
      } else if (!session.reusePlayerSurface) {
        rootPlayerOwnership =
          RootPlayerOwnership(RootPlayerSurfaceRole.EXIT_COVERED, session.item.id)
      }
      withFrameNanos {}
      when (session.kind) {
        TransitionKind.ENTER_ROOT -> {
          mainViewModel.returnToFeed()
          videoStack = emptyList()
          if (
            activeBangumiPage?.sourceOrigin in
              setOf(
                PageOrigin.My,
                PageOrigin.Search,
                PageOrigin.BangumiIndex,
                PageOrigin.BangumiHome,
              )
          ) {
            activeBangumiPage = null
            deferSearchBangumiPageComposition = false
            deferBangumiIndexPageComposition = false
            deferBangumiHomePageComposition = false
            bangumiPosterBounds = Rect.Zero
          }
          transitionPhase = TransitionPhase.Feed
          hiddenFeedCoverItemId = null
          hiddenMyCoverItemId = null
          hiddenSearchCoverItemId = null
          hiddenBangumiIndexItemId = null
          hiddenBangumiRecommendationItemId = null
          hiddenArticleVideoCoverItemId = null
          hiddenProfileCoverItemId = null
          profileVideoTransitionActive = false
        }
        TransitionKind.ENTER_RECOMMENDATION -> {
          val parentFrame = returningParent
          if (parentFrame != null && appState.selectedVideo?.id != parentFrame.item.id) {
            videoEntryCache[parentFrame.entryId]?.let(::restoreEntry)
            dataCommitAllowedId = parentFrame.item.id
            showEmbeddedCover = true
            mainViewModel.openVideo(parentFrame.item)
          }
          videoStack = videoStack.dropLast(1)
          transitionPhase =
            parentFrame?.let { TransitionPhase.Video(it.item, it.sourceCardBounds) }
              ?: TransitionPhase.Feed
          hiddenRecommendationCoverItemId = null
          hiddenPlaybackEndRecommendationCoverItemId = null
          if (parentFrame != null) {
            dataCommitAllowedId = parentFrame.item.id
            playerActivationId = parentFrame.item.id
            showEmbeddedCover = true
            val parentAlreadyRendered =
              renderedVideoId == parentFrame.item.id &&
                playerState is dev.openbili.webdemo.PlayerState.Ready
            rootPlayerOwnership =
              RootPlayerOwnership(
                if (parentAlreadyRendered) RootPlayerSurfaceRole.DETAIL
                else RootPlayerSurfaceRole.DETAIL_PENDING,
                parentFrame.item.id,
              )
            if (parentAlreadyRendered) playerViewModel.exoPlayer?.play()
            if (retainedRecommendationParent?.entryId != parentFrame.entryId) {
              val restore = videoEntryCache[parentFrame.entryId]
              playerViewModel.loadVideo(
                parentFrame.item,
                startPositionMs = restore?.savedPositionMs ?: 0L,
                preferredStreamIndex = restore?.qualityIndex,
                preferredResolutionMode = settings.preferredResolutionMode,
              )
            }
          }
        }
        TransitionKind.ENTER_PROFILE -> {
          videoStack = videoStack.dropLast(1)
          activeBangumiPage = null
          bangumiPosterBounds = Rect.Zero
          transitionPhase =
            returningParent?.let { TransitionPhase.Video(it.item, it.sourceCardBounds) }
              ?: TransitionPhase.Feed
          hiddenProfileCoverItemId = null
          withFrameNanos {}
          profileVideoTransitionActive = false
          if (returningParent != null) {
            dataCommitAllowedId = returningParent.item.id
            playerActivationId = null
          }
        }
        else -> Unit
      }
      transitionSession = null
    }
  }

  fun cancelPreparingRootEnter() {
    if (transitionSession != null || transitionPhase !is TransitionPhase.ToVideo) return
    activeTransitionJob?.cancel()
    activeTransitionJob = null
    playerViewModel.cancelPendingLoad()
    mainViewModel.returnToFeed()
    videoStack = emptyList()
    if (
      activeBangumiPage?.sourceOrigin in
        setOf(
          PageOrigin.My,
          PageOrigin.Search,
          PageOrigin.BangumiIndex,
          PageOrigin.BangumiHome,
        )
    ) {
      activeBangumiPage = null
      deferSearchBangumiPageComposition = false
      deferBangumiIndexPageComposition = false
      deferBangumiHomePageComposition = false
      bangumiPosterBounds = Rect.Zero
    }
    transitionPhase = TransitionPhase.Feed
    hiddenFeedCoverItemId = null
    hiddenMyCoverItemId = null
    hiddenSearchCoverItemId = null
    hiddenBangumiIndexItemId = null
    hiddenBangumiRecommendationItemId = null
    hiddenArticleVideoCoverItemId = null
    hiddenProfileCoverItemId = null
    profileVideoTransitionActive = false
  }

  fun startRecommendedVideo(
    current: FeedItem,
    recommendation: FeedItem,
    bounds: Rect,
    returnBounds: Rect?,
    fromPlaybackEnd: Boolean,
  ) {
    if (
      transitionSession != null || transitionPhase !is TransitionPhase.Video || bounds == Rect.Zero
    )
      return
    keyboardController?.hide()
    mainViewModel.onFullscreenChanged(false)
    cacheEntry(snapshotEntry(current))
    commitPlaybackProgress()
    val frame =
      StackFrame(
        entryId = recommendation.id,
        item = recommendation,
        parentPage = PageOrigin.Video(current.id),
        sourceCardBounds = returnBounds ?: bounds,
        sourceWasPlaybackEndRecommendation = fromPlaybackEnd,
      )
    val expandedStack = videoStack + frame
    videoStack =
      if (expandedStack.size <= MAX_VIDEO_STACK_DEPTH) expandedStack
      else {
        val inheritedRootFrame = expandedStack.first()
        expandedStack.drop(1).mapIndexed { index, retained ->
          if (index == 0)
            retained.copy(
              parentPage = inheritedRootFrame.parentPage,
              sourceCardBounds = null,
              sourceProfile = inheritedRootFrame.sourceProfile,
            )
          else retained
        }
      }
    dataCommitAllowedId = null
    showEmbeddedCover = false
    val session =
      CardTransitionSession(
          token = ++transitionToken,
          kind = TransitionKind.ENTER_RECOMMENDATION,
          item = recommendation,
          startBounds = bounds,
          endBounds = bounds,
          initialProgress = 0f,
          initialPanelAlpha = 1f,
          requiredSignals = playerTransitionRequiredSignals,
        )
        .also {
          it.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
          transitionSession = it
        }
    transitionPhase = TransitionPhase.ToVideo(recommendation, bounds, fromVideo = true)

    launchTransition {
      withFrameNanos {}
      val target = prepareCardTransition(session)
      if (!target.hasUsableSize()) {
        session.phase = SessionPhase.CANCELLED
        session.preparation.cancel()
        if (transitionSession === session) transitionSession = null
        hiddenRecommendationCoverItemId = null
        hiddenPlaybackEndRecommendationCoverItemId = null
        videoEntryCache[recommendation.id]?.let(::restoreEntryForFreshPlayback)
          ?: clearVisibleVideoData()
        dataCommitAllowedId = recommendation.id
        mainViewModel.openVideo(recommendation)
        transitionPhase = TransitionPhase.Video(recommendation, bounds)
        playerActivationId = recommendation.id
        showEmbeddedCover = true
        playerViewModel.loadVideo(
          recommendation,
          preferredResolutionMode = settings.preferredResolutionMode,
        )
        return@launchTransition
      }
      session.endBounds = target
      hiddenRecommendationCoverItemId = recommendation.id.takeUnless { fromPlaybackEnd }
      hiddenPlaybackEndRecommendationCoverItemId = recommendation.id.takeIf { fromPlaybackEnd }
      session.phase = SessionPhase.FLYING
      kotlinx.coroutines.coroutineScope {
        launch {
          session.progress.animateTo(
            1f,
            tween(if (settings.reduceMotion) 140 else 400, easing = FastOutSlowInEasing),
          )
        }
        launch {
          session.panelAlpha.animateTo(
            0f,
            tween(if (settings.reduceMotion) 70 else 150, easing = FastOutSlowInEasing),
          )
        }
      }
      if (session.reverseRequested) return@launchTransition
      // The old panel is fully transparent here. Swap its shared data before selecting the child,
      // so the child can reveal only its retained cache or loading skeletons, never parent data.
      videoEntryCache[recommendation.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
      dataCommitAllowedId = recommendation.id
      mainViewModel.openVideo(recommendation)
      transitionPhase = TransitionPhase.Video(recommendation, bounds)
      withFrameNanos {}
      session.panelAlpha.animateTo(
        1f,
        tween(if (settings.reduceMotion) 90 else 220, easing = FastOutSlowInEasing),
      )
      if (session.reverseRequested) return@launchTransition
      session.phase = SessionPhase.WAITING_FIRST_FRAME
      playerActivationId = recommendation.id
      playerViewModel.loadVideo(
        recommendation,
        preferredResolutionMode = settings.preferredResolutionMode,
      )
    }
  }

  fun returnDirectlyHome() {
    if (transitionSession != null || directHomeInProgress) return
    // Lock navigation before launching the first animation frame. Otherwise a rapid Back press can
    // start the normal card-return transition while the direct-home fade is still being scheduled.
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
      hiddenVideoCommentArticleItemId = null
      hiddenArticleCommentArticleItemId = null
      articlePageAlpha.snapTo(0f)
      directHomeAlpha.snapTo(1f)
      directHomeInProgress = false
    }
  }

  fun loadArticleDetail(article: ArticleItem) {
    val token = ++articleLoadToken
    articleLoading = true
    articleError = null
    articleDetail = null
    scope.launch {
      runCatching { withContext(Dispatchers.IO) { BiliApi.getArticleDetail(article) } }
        .onSuccess { detail ->
          if (token == articleLoadToken && articleStack.lastOrNull()?.article?.id == article.id) {
            articleDetail = detail
            articleDetailCache[article.id] = detail
            articleLoading = false
            scope.launch(Dispatchers.IO) { BiliApi.reportArticleRead(detail.commentOid) }
          }
        }
        .onFailure { error ->
          if (token == articleLoadToken && articleStack.lastOrNull()?.article?.id == article.id) {
            articleError = error.message ?: "专栏正文加载失败"
            articleLoading = false
          }
        }
    }
  }

  suspend fun awaitStableArticleHeroBounds(): Rect {
    var previous = Rect.Zero
    var stableFrames = 0
    repeat(30) {
      withFrameNanos {}
      val current = articleHeroBounds
      if (current.hasUsableSize() && current.approximatelyEquals(previous, tolerancePx = 1.5f)) {
        stableFrames++
        if (stableFrames >= 2) return current
      } else {
        stableFrames = 0
      }
      previous = current
    }
    return articleHeroBounds
  }

  fun articleSourceBounds(frame: ArticleStackFrame): Rect? =
    when (frame.origin) {
      ArticleOrigin.MY -> myArticleBounds[frame.article.stableId]
      ArticleOrigin.SEARCH -> searchArticleBounds[frame.article.stableId]
      ArticleOrigin.PROFILE -> profileArticleBounds[frame.article.stableId]
      ArticleOrigin.VIDEO -> null
      ArticleOrigin.ARTICLE -> null
    } ?: frame.sourceBounds

  fun hideArticleSource(frame: ArticleStackFrame, hidden: Boolean) {
    when (frame.origin) {
      ArticleOrigin.MY -> hiddenMyArticleItemId = frame.article.stableId.takeIf { hidden }
      ArticleOrigin.SEARCH -> hiddenSearchArticleItemId = frame.article.stableId.takeIf { hidden }
      ArticleOrigin.PROFILE -> hiddenProfileArticleItemId = frame.article.stableId.takeIf { hidden }
      ArticleOrigin.VIDEO ->
        hiddenVideoCommentArticleItemId = frame.article.stableId.takeIf { hidden }
      ArticleOrigin.ARTICLE ->
        hiddenArticleCommentArticleItemId = frame.article.stableId.takeIf { hidden }
    }
  }

  fun suspendVideoForArticle(): Boolean {
    val current = appState.selectedVideo ?: return true
    if (transitionSession != null || transitionPhase !is TransitionPhase.Video) return false
    cacheEntry(snapshotEntry(current))
    commitPlaybackProgress()
    playerViewModel.exoPlayer?.pause()
    playerViewModel.cancelPendingLoad()
    articleSuspendedVideo =
      SuspendedArticleVideo(
        item = current,
        stack =
          videoStack.ifEmpty { listOf(StackFrame(current.id, current, PageOrigin.Other, null)) },
      )
    mainViewModel.returnToFeed()
    videoStack = emptyList()
    dataCommitAllowedId = null
    playerActivationId = null
    transitionPhase = TransitionPhase.Feed
    return true
  }

  fun restoreVideoSuspendedByArticle() {
    val suspended = articleSuspendedVideo ?: return
    val retained = videoEntryCache[suspended.item.id]
    if (retained != null) restoreEntry(retained) else clearVisibleVideoData()
    videoStack = suspended.stack
    showEmbeddedCover = true
    dataCommitAllowedId = suspended.item.id
    playerActivationId = null
    mainViewModel.openVideo(suspended.item)
    transitionPhase =
      TransitionPhase.Video(suspended.item, suspended.stack.lastOrNull()?.sourceCardBounds)
    articleSuspendedVideo = null
  }

  fun startEnterArticle(
    article: ArticleItem,
    sourceBounds: Rect?,
    origin: ArticleOrigin,
    commentTarget: CommentNavigationTarget? = null,
  ) {
    if (transitionSession != null || articleTransitionSession != null) return
    pendingArticleCommentTarget = commentTarget
    val nested = articleStack.isNotEmpty()
    if (!nested && !suspendVideoForArticle()) return
    articleDetail?.let { current ->
      articleStack.lastOrNull()?.article?.id?.let { articleDetailCache[it] = current }
    }
    val frame = ArticleStackFrame(++articleEntryToken, article, origin, sourceBounds)
    val expandedStack = if (nested) articleStack + frame else listOf(frame)
    articleStack =
      if (expandedStack.size <= MAX_ARTICLE_STACK_DEPTH) expandedStack
      else {
        val inheritedRoot = expandedStack.first()
        articleDetailCache.remove(inheritedRoot.article.id)
        expandedStack.drop(1).mapIndexed { index, retained ->
          if (index == 0) retained.copy(origin = inheritedRoot.origin, sourceBounds = null)
          else retained
        }
      }
    articleHeroBounds = Rect.Zero
    articleContentReady = false
    articleRestoringParentEntryId = null
    articleLoading = false
    articleError = null
    articleDetail = null
    articleTransitionJob?.cancel()
    articleTransitionJob = scope.launch {
      articlePageAlpha.snapTo(0f)
      val target = awaitStableArticleHeroBounds()
      val source = articleSourceBounds(frame)
      if (source?.hasUsableSize() == true && target.hasUsableSize()) {
        val session = ArticleTransitionSession(article, source, target, initialProgress = 0f)
        articleTransitionSession = session
        hideArticleSource(frame, hidden = true)
        withFrameNanos {}
        if (origin == ArticleOrigin.PROFILE) {
          profileLayerSuppressed = true
          withFrameNanos {}
        }
        coroutineScope {
          launch {
            session.progress.animateTo(
              1f,
              tween(if (settings.reduceMotion) 130 else 380, easing = FastOutSlowInEasing),
            )
          }
          launch {
            delay(if (settings.reduceMotion) 0 else 45)
            articlePageAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 100 else 280, easing = FastOutSlowInEasing),
            )
          }
        }
        withFrameNanos {}
        articleTransitionSession = null
        hideArticleSource(frame, hidden = false)
      } else {
        if (origin == ArticleOrigin.PROFILE) {
          profileLayerSuppressed = true
          withFrameNanos {}
        }
        articlePageAlpha.animateTo(
          1f,
          tween(if (settings.reduceMotion) 100 else 220, easing = FastOutSlowInEasing),
        )
      }
      if (articleStack.lastOrNull() == frame) {
        articleContentReady = true
        loadArticleDetail(article)
      }
    }
  }

  fun openInteractionTarget(message: AccountMessage, sourceBounds: Rect) {
    if (interactionTargetLoadingId != null || transitionPhase !is TransitionPhase.Feed) return
    interactionTargetLoadingId = message.id
    scope.launch {
      try {
        val rootRpid = message.rootId.takeIf { it > 0L } ?: message.targetCommentId
        // A private share points only at the media. It has no reply-thread semantics; passing its
        // zero IDs into comment navigation made the video page report “此条评论被删除”.
        val target =
          if (message.isPrivate) null
          else
            CommentNavigationTarget(
              oid = message.oid,
              type = message.commentType,
              rootRpid = rootRpid,
              targetRpid = message.targetCommentId.takeIf { it > 0L } ?: rootRpid,
              requestId = ++commentNavigationRequestToken,
            )
        if (message.targetKind == MessageTargetKind.ARTICLE) {
          val articleId =
            Regex("(?:read/cv|opus/|article/)(\\d+)", RegexOption.IGNORE_CASE)
              .find(message.linkUrl)
              ?.groupValues
              ?.getOrNull(1)
              ?.toLongOrNull() ?: message.oid
          if (articleId <= 0L) throw IllegalStateException("无法解析这条专栏消息")
          val sourceUrl =
            message.linkUrl
              .takeIf {
                it.startsWith("https://", ignoreCase = true) ||
                  it.startsWith("http://", ignoreCase = true)
              }
              .orEmpty()
          val articleStableId = "article:$articleId"
          myInteractionArticleMessageIds[articleStableId] = message.id
          if (sourceBounds.hasUsableSize()) myArticleBounds[articleStableId] = sourceBounds
          startEnterArticle(
            ArticleItem(
              id = articleId,
              title = message.subjectTitle.ifBlank { "专栏" },
              coverUrl = message.coverUrl,
              sourceUrl = sourceUrl,
            ),
            sourceBounds.takeIf(Rect::hasUsableSize),
            ArticleOrigin.MY,
            target,
          )
        } else {
          val bvid =
            Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE).find(message.linkUrl)?.value
          val linkedAid =
            Regex("(?:/av|video/av)(\\d+)", RegexOption.IGNORE_CASE)
              .find(message.linkUrl)
              ?.groupValues
              ?.getOrNull(1)
              ?.toLongOrNull()
          val info =
            withContext(Dispatchers.IO) {
              if (!bvid.isNullOrBlank()) BiliApi.getVideoInfo(bvid)
              else BiliApi.getVideoInfoByAid(message.oid.takeIf { it > 0L } ?: linkedAid ?: 0L)
            }
          val resolvedBvid = info?.bvid ?: bvid
          if (resolvedBvid.isNullOrBlank()) throw IllegalStateException("无法解析这条视频消息")
          val item =
            FeedItem(
              id = resolvedBvid,
              title = info?.title ?: message.subjectTitle.ifBlank { "视频" },
              videoUrl = "https://www.bilibili.com/video/$resolvedBvid",
              coverUrl = info?.coverUrl ?: message.coverUrl,
              uploader = info?.uploaderName,
              playCount = info?.let { FeedViewModel.formatCount(it.playCount) },
              duration = info?.let { FeedViewModel.formatDuration(it.durationSeconds) },
              uploaderFace = info?.uploaderFace,
              uploaderMid = info?.uploaderMid ?: 0L,
              danmakuCount = info?.danmakuCount ?: 0L,
              publishedAt = info?.publishedAt ?: 0L,
              description = info?.desc.orEmpty(),
            )
          myInteractionVideoMessageIds[item.id] = message.id
          if (sourceBounds.hasUsableSize()) myCardBounds[item.id] = sourceBounds
          startEnterVideo(
            item,
            sourceBounds.takeIf(Rect::hasUsableSize),
            VideoOrigin.MY,
            target?.copy(oid = info?.aid ?: target.oid),
          )
        }
      } catch (error: Exception) {
        Toast.makeText(context, error.message ?: "无法打开这条互动消息", Toast.LENGTH_SHORT).show()
        pendingVideoCommentTarget = null
        pendingArticleCommentTarget = null
      } finally {
        interactionTargetLoadingId = null
      }
    }
  }

  fun startExitArticle() {
    val frame = articleStack.lastOrNull() ?: return
    articleContentReady = false
    articleLoadToken++
    val activeSession = articleTransitionSession
    articleTransitionJob?.cancel()
    articleTransitionJob = scope.launch {
      val session =
        activeSession
          ?: run {
            val source =
              articleSourceBounds(frame).takeUnless { frame.origin == ArticleOrigin.VIDEO }
            val target = articleHeroBounds
            if (source?.hasUsableSize() == true && target.hasUsableSize()) {
              ArticleTransitionSession(
                  articleDetail?.article ?: frame.article,
                  source,
                  target,
                  initialProgress = 1f,
                )
                .also {
                  articleTransitionSession = it
                }
            } else null
          }
      if (session != null) {
        articleTransitionSession = session
        session.endBounds = articleHeroBounds.takeIf(Rect::hasUsableSize) ?: session.endBounds
        hideArticleSource(frame, hidden = true)
        withFrameNanos {}
        articlePageAlpha.animateTo(
          0f,
          tween(if (settings.reduceMotion) 80 else 170, easing = FastOutSlowInEasing),
        )
        if (frame.origin == ArticleOrigin.PROFILE) {
          // Keep a stationary 16:9 hero above the handoff while the retained profile remounts.
          // The destination card is already hidden, so only the flying cover is visible.
          profileLayerSuppressed = false
          repeat(2) { withFrameNanos {} }
        }
        session.startBounds = articleSourceBounds(frame) ?: session.startBounds
        session.progress.animateTo(
          0f,
          tween(if (settings.reduceMotion) 120 else 330, easing = FastOutSlowInEasing),
        )
      } else {
        articlePageAlpha.animateTo(
          0f,
          tween(if (settings.reduceMotion) 90 else 190, easing = FastOutSlowInEasing),
        )
        if (frame.origin == ArticleOrigin.PROFILE) profileLayerSuppressed = false
      }
      val remainingStack = articleStack.dropLast(1)
      val parent = remainingStack.lastOrNull()
      val restoredParentDetail = parent?.let { articleDetailCache[it.article.id] }
      if (parent == null) {
        articleDetail = null
        articleLoading = false
        articleError = null
        articleContentReady = false
        articleRestoringParentEntryId = null
      } else {
        // Commit the retained parent's data before it becomes the top composition. Pairing the
        // parent item with the departing child's detail for even one frame resets comments and
        // makes the retained LazyColumn appear to jump.
        articleDetail = restoredParentDetail
        articleLoading = restoredParentDetail == null
        articleError = null
        articleContentReady = restoredParentDetail != null
        articleRestoringParentEntryId = parent.entryId
      }
      articleStack = remainingStack
      articleLoadToken++
      hideArticleSource(frame, hidden = false)
      articleTransitionSession = null
      articleHeroBounds = Rect.Zero
      if (parent == null) {
        articlePageAlpha.snapTo(0f)
        restoreVideoSuspendedByArticle()
      } else {
        articlePageAlpha.snapTo(1f)
        articleRestoringParentEntryId = null
        if (restoredParentDetail == null) {
          articleContentReady = true
          loadArticleDetail(parent.article)
        }
      }
    }
  }

  fun openSearchResultsAnimated(keyword: String) {
    val normalized = keyword.trim()
    if (normalized.isEmpty() || appState.isVideoScreen) return
    searchTransitionJob?.cancel()
    keyboardController?.hide()
    focusManager.clearFocus(force = true)
    searchTransitionQuery = normalized
    searchTransitionSourceBounds = searchBounds
    searchTransitionPreparation?.cancel()
    val preparation =
      TransitionPreparationBarrier(
        setOf(
          TransitionReadySignal.SOURCE_BOUNDS,
          TransitionReadySignal.TARGET_MOUNTED,
          TransitionReadySignal.TARGET_BOUNDS_STABLE,
        )
      )
    preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
    searchTransitionPreparation = preparation
    showSearch = false
    showSearchResults = false
    searchTransitionDirection = SearchTransitionDirection.ENTER
    searchTransitionJob = scope.launch {
      searchTransitionMaskAlpha.snapTo(1f)
      searchTransitionScrimAlpha.snapTo(0f)
      searchTransitionProgress.snapTo(0f)
      withFrameNanos {}
      preparation.markReady(TransitionReadySignal.TARGET_MOUNTED)
      withFrameNanos {}
      preparation.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
      preparation.await()
      if (searchTransitionPreparation !== preparation) return@launch
      coroutineScope {
        launch {
          searchTransitionProgress.animateTo(
            1f,
            tween(if (settings.reduceMotion) 120 else 380, easing = FastOutSlowInEasing),
          )
        }
        launch {
          searchTransitionScrimAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 80 else 220, easing = FastOutSlowInEasing),
          )
        }
      }
      if (searchTransitionDirection != SearchTransitionDirection.ENTER) return@launch
      searchViewModel.search(normalized)
      showSearchResults = true
      withFrameNanos {}
      searchTransitionDirection = null
      searchTransitionPreparation = null
      searchTransitionMaskAlpha.snapTo(0f)
    }
  }

  fun closeSearchResultsAnimated() {
    if (
      appState.isVideoScreen ||
        articleStack.isNotEmpty() ||
        searchTransitionDirection == SearchTransitionDirection.EXIT
    )
      return
    val reversingEnter = searchTransitionDirection == SearchTransitionDirection.ENTER
    searchTransitionJob?.cancel()
    searchTransitionPreparation?.cancel()
    keyboardController?.hide()
    focusManager.clearFocus(force = true)
    if (searchBounds.width > 0f && searchBounds.height > 0f) {
      searchTransitionSourceBounds = searchBounds
    }
    showSearch = false
    val preparation =
      TransitionPreparationBarrier(
        setOf(
          TransitionReadySignal.SOURCE_BOUNDS,
          TransitionReadySignal.TARGET_MOUNTED,
          TransitionReadySignal.TARGET_BOUNDS_STABLE,
        )
      )
    preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
    searchTransitionPreparation = preparation
    searchTransitionDirection = SearchTransitionDirection.EXIT
    searchTransitionJob = scope.launch {
      if (!reversingEnter) {
        searchTransitionMaskAlpha.snapTo(0f)
        searchTransitionScrimAlpha.snapTo(0f)
        searchTransitionProgress.snapTo(1f)
        withFrameNanos {}
        withFrameNanos {}
        coroutineScope {
          launch {
            searchTransitionMaskAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 70 else 120),
            )
          }
          launch {
            searchTransitionScrimAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 70 else 120),
            )
          }
        }
        if (searchTransitionDirection != SearchTransitionDirection.EXIT) return@launch
        // The opaque surface now completely covers the result page. Drop the expensive grid
        // before changing geometry so only a flat Material surface participates in the exit.
        showSearchResults = false
        withFrameNanos {}
      } else {
        searchTransitionMaskAlpha.snapTo(1f)
        showSearchResults = false
      }
      preparation.markReady(
        TransitionReadySignal.TARGET_MOUNTED,
        TransitionReadySignal.TARGET_BOUNDS_STABLE,
      )
      preparation.await()
      if (searchTransitionPreparation !== preparation) return@launch
      coroutineScope {
        launch {
          searchTransitionProgress.animateTo(
            0f,
            tween(if (settings.reduceMotion) 100 else 320, easing = FastOutSlowInEasing),
          )
        }
        launch {
          searchTransitionScrimAlpha.animateTo(
            0f,
            tween(if (settings.reduceMotion) 70 else 180, easing = FastOutSlowInEasing),
          )
        }
      }
      searchTransitionMaskAlpha.animateTo(
        0f,
        tween(if (settings.reduceMotion) 60 else 100),
      )
      if (searchTransitionDirection == SearchTransitionDirection.EXIT) {
        searchTransitionDirection = null
        searchTransitionPreparation = null
      }
    }
  }

  fun openBangumiIndexAnimated(sourceBounds: Rect) {
    if (
      appState.isVideoScreen ||
        showBangumiIndex ||
        bangumiIndexTransitionDirection != null ||
        transitionPhase !is TransitionPhase.Feed
    ) return
    // The index follows the explore page's current category (番剧/国创/电影…), not always 番剧.
    bangumiIndexViewModel.openCategory(bangumiExploreViewModel.state.value.selectedCategory)
    bangumiIndexTransitionJob?.cancel()
    bangumiIndexTransitionSourceBounds = sourceBounds.takeIf { it.hasUsableSize() } ?: Rect.Zero
    bangumiIndexTransitionDirection = SearchTransitionDirection.ENTER
    bangumiIndexTransitionJob = scope.launch {
      bangumiIndexTransitionMaskAlpha.snapTo(1f)
      bangumiIndexTransitionScrimAlpha.snapTo(0f)
      bangumiIndexTransitionProgress.snapTo(0f)
      // The source rect is frozen above; wait for two committed target frames before moving it.
      withFrameNanos {}
      withFrameNanos {}
      coroutineScope {
        launch {
          bangumiIndexTransitionProgress.animateTo(
            1f,
            tween(if (settings.reduceMotion) 120 else 380, easing = FastOutSlowInEasing),
          )
        }
        launch {
          bangumiIndexTransitionScrimAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 80 else 220, easing = FastOutSlowInEasing),
          )
        }
      }
      if (bangumiIndexTransitionDirection != SearchTransitionDirection.ENTER) return@launch
      showBangumiIndex = true
      withFrameNanos {}
      bangumiIndexTransitionDirection = null
      bangumiIndexTransitionMaskAlpha.snapTo(0f)
      // Network work starts only after the lightweight Surface has landed and the page is mounted.
      bangumiIndexViewModel.ensureLoaded()
    }
  }

  fun closeBangumiIndexAnimated() {
    if (appState.isVideoScreen || bangumiIndexTransitionDirection == SearchTransitionDirection.EXIT) return
    val reversingEnter = bangumiIndexTransitionDirection == SearchTransitionDirection.ENTER
    bangumiIndexTransitionJob?.cancel()
    bangumiIndexTransitionDirection = SearchTransitionDirection.EXIT
    bangumiIndexTransitionJob = scope.launch {
      if (!reversingEnter) {
        bangumiIndexTransitionMaskAlpha.snapTo(0f)
        bangumiIndexTransitionScrimAlpha.snapTo(0f)
        bangumiIndexTransitionProgress.snapTo(1f)
        coroutineScope {
          launch { bangumiIndexTransitionMaskAlpha.animateTo(1f, tween(if (settings.reduceMotion) 70 else 120)) }
          launch { bangumiIndexTransitionScrimAlpha.animateTo(1f, tween(if (settings.reduceMotion) 70 else 120)) }
        }
        showBangumiIndex = false
        withFrameNanos {}
      } else {
        bangumiIndexTransitionMaskAlpha.snapTo(1f)
        showBangumiIndex = false
      }
      coroutineScope {
        launch {
          bangumiIndexTransitionProgress.animateTo(
            0f,
            tween(if (settings.reduceMotion) 100 else 320, easing = FastOutSlowInEasing),
          )
        }
        launch {
          bangumiIndexTransitionScrimAlpha.animateTo(
            0f,
            tween(if (settings.reduceMotion) 70 else 180, easing = FastOutSlowInEasing),
          )
        }
      }
      bangumiIndexTransitionMaskAlpha.animateTo(0f, tween(if (settings.reduceMotion) 60 else 100))
      if (bangumiIndexTransitionDirection == SearchTransitionDirection.EXIT) {
        bangumiIndexTransitionDirection = null
      }
    }
  }

  BackHandler(
    enabled =
      searchTransitionDirection != null && !appState.isVideoScreen && articleStack.isEmpty(),
    onBack = ::closeSearchResultsAnimated,
  )

  BackHandler(
    enabled =
      (showBangumiIndex || bangumiIndexTransitionDirection != null) &&
        !appState.isVideoScreen &&
        articleStack.isEmpty(),
    onBack = ::closeBangumiIndexAnimated,
  )

  BackHandler(
    enabled =
      articleStack.isNotEmpty() &&
        appState.selectedVideo == null &&
        (profileStack.isEmpty() || profileLayerSuppressed),
    onBack = ::startExitArticle,
  )

  // System back: route through the same exit transition as the top-left
  // arrow, instead of MainActivity's direct returnToFeed() which would
  // leave transitionPhase == Video and cause a white screen.
  BackHandler(
    enabled = appState.selectedVideo != null && !appState.video.isFullscreen && !directHomeInProgress
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
        else if (videoStack.lastOrNull()?.parentPage is PageOrigin.Profile) startExitVideoToProfile()
        else if (videoStack.size > 1) startBackToPreviousVideo() else startExitVideo()
      else -> Unit
    }
  }

  // ── Screen coexistence ───────────────────────────────────────────────
  val showFeed = true
  val showVideo = appState.selectedVideo != null && transitionPhase !is TransitionPhase.Feed
  val activeSession = transitionSession
  val activeArticleFrame = articleStack.lastOrNull()
  // Once the visual card flight has landed, keep only the player-position cover while Media3
  // waits for its first frame. The cover is local to the player; it must not keep the comments and
  // recommendation panes behind a full-screen pointer interceptor.
  val transitionVisualsActive =
    videoExitPrelude != null ||
      activeSession != null ||
      (transitionPhase !is TransitionPhase.Feed && transitionPhase !is TransitionPhase.Video)
  val waitingForFirstFrame = activeSession?.phase == SessionPhase.WAITING_FIRST_FRAME
  val navigationLocked =
    !waitingForFirstFrame &&
      (bangumiCardEnterPending ||
        videoExitPrelude != null ||
        activeSession != null ||
        articleTransitionSession != null ||
        (transitionPhase !is TransitionPhase.Feed && transitionPhase !is TransitionPhase.Video))
  val preparingRootEnter =
    (transitionPhase as? TransitionPhase.ToVideo)?.let { !it.fromVideo && activeSession == null } ==
      true
  val rootEnterSession = activeSession?.takeIf { it.kind == TransitionKind.ENTER_ROOT }
  val profileEnterSession = activeSession?.takeIf { it.kind == TransitionKind.ENTER_PROFILE }
  val bangumiHomeTransitionSession =
    activeSession?.takeIf { activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome }
  val bangumiDetailPlayerSuppressed =
    bangumiHomeTransitionSession?.let { session ->
      shouldSuppressDetailPlayerForBangumiCardTransition(session.kind, session.phase)
    } == true
  val rootPlayerHostEnabled =
    shouldUseRootPlayerHost(
      startupWarmupVisible = startupWarmupVisible,
      bangumiRootPageActive = bangumiRootPageActive,
      // Bangumi card flights are cover-only. The real PlayerView is deliberately detached until
      // either the root preview or the landed detail page becomes stable.
      hasBangumiHomeTransition = false,
    )
  LaunchedEffect(rootPlayerHostEnabled) {
    if (!rootPlayerHostEnabled) {
      playerViewHolder[0]?.view?.apply {
        animate().cancel()
        alpha = 1f
        updateVideoSurfaceAlpha(1f)
      }
      return@LaunchedEffect
    }
  }
  val searchBangumiSession =
    activeSession?.takeIf {
      activeBangumiPage?.sourceOrigin in setOf(PageOrigin.Search, PageOrigin.BangumiIndex) &&
        (it.kind == TransitionKind.ENTER_ROOT || it.kind == TransitionKind.EXIT_ROOT)
    }
  val searchBangumiExitPrelude =
    activeBangumiPage?.sourceOrigin in setOf(PageOrigin.Search, PageOrigin.BangumiIndex) &&
      videoExitPrelude != null
  val searchBangumiSourceAboveVideo =
    when (searchBangumiSession?.kind) {
      TransitionKind.ENTER_ROOT ->
        searchBangumiSession.phase !in
          setOf(
            SessionPhase.REVEALING_BACKGROUND,
            SessionPhase.WAITING_FIRST_FRAME,
            SessionPhase.REVEALING,
            SessionPhase.COMPLETED,
          )
      TransitionKind.EXIT_ROOT -> true
      else -> false
    }
  val feedLayerAlpha =
    when {
      !showVideo || directHomeInProgress || preparingRootEnter -> 1f
      searchBangumiSession?.kind == TransitionKind.ENTER_ROOT ->
        1f - searchBangumiSession.backgroundAlpha.value.coerceIn(0f, 1f)
      searchBangumiSession?.kind == TransitionKind.EXIT_ROOT ->
        searchBangumiSession.panelAlpha.value.coerceIn(0f, 1f)
      searchBangumiExitPrelude -> 1f
      rootEnterSession != null || activeSession?.kind == TransitionKind.EXIT_ROOT -> 1f
      else -> 0f
    }
  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Layer 0: Feed
    if (showFeed) {
      Box(
        Modifier.fillMaxSize()
          .zIndex(if (searchBangumiSourceAboveVideo) 1f else 0f)
          .graphicsLayer { alpha = feedLayerAlpha }
      ) {
        if (showSearchResults) {
          Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = VideoShapeTokens.Card,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 2.dp,
            border =
              androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
              ),
          ) {
            SearchResultsScreen(
              state = searchState,
              gridState = searchGridState,
              onCategory = searchViewModel::selectCategory,
              onOrder = searchViewModel::selectOrder,
              onArticleOrder = searchViewModel::selectArticleOrder,
              onVideo = { video, bounds ->
                val sourceBounds = bounds.takeUnless { it == Rect.Zero }
                if (sourceBounds != null) searchCardBounds[video.id] = sourceBounds
                startEnterVideo(video, sourceBounds, VideoOrigin.SEARCH)
              },
              onVideoLongClick = { showVideoPreview(it) },
              onVideoBounds = { video, bounds ->
                if (bounds.width > 0f && bounds.height > 0f) searchCardBounds[video.id] = bounds
              },
              onVideoProfile = { mid, face, name, bounds ->
                openAvatarProfile(mid, bounds, face, name)
              },
              onBangumi = { card, bounds ->
                val sourceBounds = bounds.takeUnless { it == Rect.Zero }
                if (sourceBounds != null) searchCardBounds[card.id] = sourceBounds
                startSearchBangumi(card, bounds)
              },
              onArticle = { article, bounds ->
                val sourceBounds = bounds.takeUnless { it == Rect.Zero }
                if (sourceBounds != null) searchArticleBounds[article.stableId] = sourceBounds
                startEnterArticle(article, sourceBounds, ArticleOrigin.SEARCH)
              },
              onArticleBounds = { article, bounds ->
                if (bounds.hasUsableSize()) searchArticleBounds[article.stableId] = bounds
              },
              onUser = { mid, face, name, bounds ->
                openAvatarProfile(mid, bounds, face, name)
              },
              onLoadMore = searchViewModel::loadNextPage,
              onRefresh = searchViewModel::retry,
              onRetry = searchViewModel::retry,
              onBack = ::closeSearchResultsAnimated,
              hiddenCoverItemId = hiddenSearchCoverItemId,
              hiddenArticleItemId = hiddenSearchArticleItemId,
              backEnabled = !appState.isVideoScreen && activeArticleFrame == null,
            )
          }
        } else {
          val rootBackdropLayer = rememberGraphicsLayer()
          val captureRootBackdrop =
            !navigationLocked &&
              !directHomeInProgress &&
              searchTransitionDirection == null &&
              !rootPagerState.isScrollInProgress &&
              (rootTab != RootTab.HOME || !feedGridState.isScrollInProgress) &&
              transitionPhase is TransitionPhase.Feed
          Box(
            Modifier.fillMaxSize().drawWithContent {
              if (captureRootBackdrop) {
                rootBackdropLayer.record { this@drawWithContent.drawContent() }
                drawLayer(rootBackdropLayer)
              } else {
                // Keep the previous capture available to the bottom glass capsule, but draw the
                // live page directly while a full-screen transition is running. This avoids
                // re-recording the whole retained root page on every animation frame.
                drawContent()
              }
            }
          ) {
            HorizontalPager(
              state = rootPagerState,
              modifier = Modifier.fillMaxSize(),
              beyondViewportPageCount = 1,
              userScrollEnabled = false,
              key = { RootTab.entries[it] },
            ) { page ->
              val tab = RootTab.entries[page]
              if (tab == RootTab.HOME) {
                FeedScreen(
                  state = feedState,
                  userInfo = userInfo,
                  onRefresh = onFeedRefresh,
                  onLoadNextPage = { feedViewModel.loadNextPage() },
                  onItemClick = { item, bounds, scrollAnchor ->
                    if (transitionPhase is TransitionPhase.Feed)
                      startEnterVideo(
                        item,
                        bounds,
                        VideoOrigin.HOME,
                        rootFeedScrollAnchor = scrollAnchor,
                      )
                  },
                  onItemLongClick = { showVideoPreview(it, fromHomeFeed = true) },
                  onProfileClick = { item, bounds ->
                    openAvatarProfile(
                      item.uploaderMid,
                      bounds,
                      item.uploaderFace,
                      item.uploader,
                    )
                  },
                  onLoginClick = { bounds ->
                    if (userInfo.isLogin) openAvatarProfile(userInfo.mid, bounds)
                    else authViewModel.startLogin()
                  },
                  onSearch = {
                    if (!showSearch) {
                      searchViewModel.open()
                      showSearch = true
                    }
                  },
                  searchQuery = searchState.query,
                  onSearchQueryChange = searchViewModel::setQuery,
                  onSearchSubmit = ::openSearchResultsAnimated,
                  onConsumeRefreshMessage = { feedViewModel.consumeRefreshMessage() },
                  onSearchBoundsChanged = { searchBounds = it },
                  coverPrefetchCount = settings.coverPrefetchScreens * 9,
                  backgroundWorkAllowed =
                    !rootPageSwitchInProgress && transitionPhase is TransitionPhase.Feed,
                  gridState = feedGridState,
                  hiddenCoverItemId = hiddenFeedCoverItemId,
                  dismissedItemIds = dismissedFeedItemIds,
                  onRestoreDismissedItem = { item ->
                    dismissedFeedItemIds = dismissedFeedItemIds - item.id
                  },
                  onItemBoundsChanged = { feedItem, bounds ->
                    feedCardBounds[feedItem.id] = bounds
                  },
                )
              } else if (tab == RootTab.MY) {
                MyScreen(
                  user = userInfo,
                  state = myState,
                  onSection = myViewModel::select,
                  onFolder = myViewModel::selectFolder,
                  onVideo = { item, bounds ->
                    val sourceBounds = bounds.takeUnless { it == Rect.Zero }
                    if (sourceBounds != null) myCardBounds[item.id] = sourceBounds
                    startEnterVideo(item, sourceBounds, VideoOrigin.MY)
                  },
                  onBangumi = { card, item, bounds ->
                    startHistoryBangumi(card, item, bounds)
                  },
                  onVideoLongClick = { showVideoPreview(it) },
                  onArticle = { article, bounds ->
                    val sourceBounds = bounds.takeUnless { it == Rect.Zero }
                    if (sourceBounds != null) myArticleBounds[article.stableId] = sourceBounds
                    startEnterArticle(article, sourceBounds, ArticleOrigin.MY)
                  },
                  onArticleBounds = { article, bounds ->
                    if (bounds.hasUsableSize()) myArticleBounds[article.stableId] = bounds
                  },
                  onHistoryFilter = myViewModel::selectHistoryFilter,
                  onLoadMoreHistory = myViewModel::loadMoreHistory,
                  onFavoriteQuery = myViewModel::setFavoriteQuery,
                  onLoadMoreFavorites = myViewModel::loadMoreFavorites,
                  onRemoveFavorite = myViewModel::removeFavorite,
                  onCopyFavorite = myViewModel::copyFavorite,
                  onMoveFavorite = myViewModel::moveFavorite,
                  onCreateFavoriteFolder = myViewModel::createFavoriteFolder,
                  onEditFavoriteFolder = myViewModel::editFavoriteFolder,
                  onDeleteFavoriteFolder = myViewModel::deleteFavoriteFolder,
                  hiddenCoverItemId = hiddenMyCoverItemId,
                  hiddenArticleItemId = hiddenMyArticleItemId,
                  hiddenInteractionTargetMessageId =
                    hiddenMyCoverItemId?.let(myInteractionVideoMessageIds::get)
                      ?: hiddenMyArticleItemId?.let(myInteractionArticleMessageIds::get),
                  onProfile = { person, bounds ->
                    if (myState.section == dev.openbili.webdemo.my.MySection.FOLLOWING) {
                      myViewModel.commitPendingUnfollows()
                    }
                    openAvatarProfile(person.mid, bounds, person.face, person.name)
                  },
                  onUnfollow = myViewModel::unfollow,
                  onFollowingQuery = myViewModel::setFollowingQuery,
                  onFollowingGroup = myViewModel::selectFollowingGroup,
                  onFollowingOrder = myViewModel::selectFollowingOrder,
                  onLoadMoreFollowings = myViewModel::loadMoreFollowings,
                  onRefresh = myViewModel::refresh,
                  onLogin = { authViewModel.startLogin() },
                  onAccountClick = { bounds -> openAvatarProfile(userInfo.mid, bounds) },
                  onMessage = myViewModel::selectMessage,
                  onLoadMorePrivateSessions = myViewModel::loadMorePrivateSessions,
                  onLoadMorePrivateMessageHistory = myViewModel::loadMorePrivateMessageHistory,
                  onReplyMessage = myViewModel::replyToSelected,
                  onReplyPrivateMessage = { text, imageUri ->
                    myViewModel.replyToSelectedPrivate(context.applicationContext, text, imageUri)
                  },
                  onWithdrawPrivateMessage = myViewModel::withdrawPrivateMessage,
                  onDeletePrivateMessage = myViewModel::deletePrivateMessage,
                  onPrivateMessageProfile = { mid, face, name, bounds ->
                    openAvatarProfile(mid, bounds, face, name)
                  },
                  onPrivateMessageTarget = ::openInteractionTarget,
                  onInteractionTarget = ::openInteractionTarget,
                  onInteractionProfile = ::openCommentProfile,
                  onLoadMoreInteractions = myViewModel::loadMoreInteractions,
                  onErrorConsumed = myViewModel::consumeError,
                  hiddenInteractionCommentAvatarRpid =
                    commentProfileTransition
                      ?.takeIf { it.sourceAvatarBounds != null }
                      ?.sourceComment
                      ?.rpid,
                  settings = settings,
                  onSettingsChange = settingsViewModel::update,
                  onLogout = {
                    if (myState.section == dev.openbili.webdemo.my.MySection.FOLLOWING) {
                      myViewModel.commitPendingUnfollows()
                    }
                    authViewModel.logout()
                    Toast.makeText(context, "已经安全退出啦 (｡•̀ᴗ-)✧", Toast.LENGTH_SHORT).show()
                  },
                )
              } else {
                BangumiRecommendationScreen(
                  exploreViewModel = bangumiExploreViewModel,
                  // The index is a retained overlay. Its own BackHandler must be the only one
                  // active, otherwise the underlying explore page consumes Back first and
                  // collapses to "本期推荐" before the index closes.
                  active = bangumiRootPageActive && !showVideo && !showBangumiIndex,
                  preloadEnabled =
                    !bangumiStartupPreloadReady &&
                      !startupWarmupFadeInProgress &&
                      !rootPageSwitchInProgress &&
                      transitionPhase is TransitionPhase.Feed,
                  retainedForDetailReturn =
                    activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome,
                  previewMuted = bangumiPreviewMuted,
                  hiddenCardId = hiddenBangumiRecommendationItemId,
                  currentAccountMid = authUserInfo.mid,
                  state = bangumiRecommendationState,
                  onRefresh = { bangumiRecommendationViewModel.refresh() },
                  onSelect = { bangumiRecommendationViewModel.select(it) },
                  onRequireDetails = { bangumiRecommendationViewModel.ensureDetails(it) },
                  onRetryDetail = { bangumiRecommendationViewModel.retryDetail(it) },
                  onPreviewChanged = { target ->
                    bangumiPreviewTarget = target
                  },
                  onPreloadReady = { bangumiStartupPreloadReady = true },
                  onTogglePreviewMute = { bangumiPreviewMuted = !bangumiPreviewMuted },
                  onOpenMainEpisode = { card, item, bounds ->
                    if (!bangumiCardEnterPending && transitionPhase is TransitionPhase.Feed) {
                      bangumiCardEnterPending = true
                      playerViewModel.exoPlayer?.pause()
                      scope.launch {
                        try {
                          withFrameNanos {}
                          withFrameNanos {}
                          if (transitionPhase !is TransitionPhase.Feed) return@launch
                          playerViewModel.exoPlayer?.apply {
                            volume = 1f
                            repeatMode = Player.REPEAT_MODE_OFF
                          }
                          startRootBangumi(
                            card = card,
                            item = item,
                            cardBounds = bounds,
                            pageOrigin = PageOrigin.BangumiHome,
                            videoOrigin = VideoOrigin.BANGUMI,
                            restoreEpisodeSelection = false,
                            initialSeason = bangumiPreviewTarget?.season,
                          )
                        } finally {
                          bangumiCardEnterPending = false
                        }
                      }
                    }
                  },
                  onOpenExploreLandscape = { exploreItem, bounds ->
                    if (!bangumiCardEnterPending && transitionPhase is TransitionPhase.Feed) {
                      bangumiCardEnterPending = true
                      playerViewModel.exoPlayer?.pause()
                      scope.launch {
                        try {
                          val card =
                            SpaceContentCard(
                              id = "bangumi-explore-${exploreItem.stableId}",
                              title = exploreItem.title,
                              subtitle = exploreItem.subtitle,
                              coverUrl = exploreItem.coverUrl,
                              videoUrl = exploreItem.targetUrl,
                              seasonId = exploreItem.seasonId,
                              episodeId = exploreItem.episodeId,
                              kind = dev.openbili.webdemo.api.SpaceContentKind.BANGUMI,
                              watchProgress = exploreItem.watchProgress,
                              seasonType = exploreItem.seasonType,
                              hasHistory = exploreItem.hasHistory,
                              historicalOnly = exploreItem.historicalOnly,
                            )
                          val item =
                            FeedItem(
                              id = card.id,
                              title = card.title,
                              videoUrl = card.videoUrl,
                              coverUrl = card.coverUrl,
                              uploader = null,
                              playCount = null,
                              duration = null,
                              description = card.subtitle,
                            )
                          startRootBangumi(
                            card = card,
                            item = item,
                            cardBounds = bounds,
                            pageOrigin = PageOrigin.BangumiHome,
                            videoOrigin = VideoOrigin.BANGUMI,
                            restoreEpisodeSelection = false,
                            returnToSourceCover =
                              exploreItem.sectionKind == BangumiExploreSectionKind.HOT,
                          )
                        } finally {
                          bangumiCardEnterPending = false
                        }
                      }
                    }
                  },
                  onOpenExplorePoster = { exploreItem, bounds ->
                    if (transitionPhase is TransitionPhase.Feed) {
                      // This intentionally takes the established portrait contract: a fitted
                      // poster flies into the Bangumi page instead of reusing the PV/player-cover
                      // overlay used by horizontal artwork above.
                      startSearchBangumi(
                        SpaceContentCard(
                          id = "bangumi-explore-${exploreItem.stableId}",
                          title = exploreItem.title,
                          subtitle = exploreItem.subtitle,
                          coverUrl = exploreItem.coverUrl,
                          videoUrl = exploreItem.targetUrl,
                          seasonId = exploreItem.seasonId,
                          episodeId = exploreItem.episodeId,
                          kind = dev.openbili.webdemo.api.SpaceContentKind.BANGUMI,
                          seasonType = exploreItem.seasonType,
                          hasHistory = exploreItem.hasHistory,
                          historicalOnly = exploreItem.historicalOnly,
                        ),
                        bounds,
                        sourceIsBangumiExplorePoster = true,
                      )
                    }
                  },
                  onOpenIndex = ::openBangumiIndexAnimated,
                )
              }
            }
          }
          if (!showBangumiIndex) BottomCapsule(
            selected = rootTab,
            backdropLayer = rootBackdropLayer,
            onSelected = ::animateToRootTab,
            selectionPosition = {
              (rootPagerState.currentPage + rootPagerState.currentPageOffsetFraction).coerceIn(
                0f,
                RootTab.entries.lastIndex.toFloat(),
              )
            },
            onSelectionDrag = { position ->
              rootPageSwitchRequested = true
              val anchor = rootPagerAnchorForCapsulePosition(position)
              rootPagerState.requestScrollToPage(
                page = anchor.page,
                pageOffsetFraction = anchor.offsetFraction,
              )
            },
            onInteractionStart = {},
            onInteractionEnd = {},
            dragEnabled =
              !navigationLocked &&
                searchTransitionDirection == null &&
                bangumiIndexTransitionDirection == null &&
                !showBangumiIndex,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
          )
        }
      }
    }

    // Keep the root pager composed below the index. The recommendation screen owns the current
    // explore/recommendation state and its scroll positions; replacing it here would reset that
    // state to the "本期推荐" layer whenever the index closes.
    if (showFeed && showBangumiIndex) {
      Box(
        Modifier.fillMaxSize()
          .zIndex(if (searchBangumiSourceAboveVideo) 1f else 0f)
          .graphicsLayer { alpha = feedLayerAlpha }
          .background(MaterialTheme.colorScheme.background)
      ) {
        Surface(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          shape = VideoShapeTokens.Card,
          color = MaterialTheme.colorScheme.background,
          tonalElevation = 2.dp,
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          BangumiIndexScreen(
            state = bangumiIndexState,
            gridState = bangumiIndexGridState,
            onBack = ::closeBangumiIndexAnimated,
            onReset = bangumiIndexViewModel::reset,
            onOrderSelected = bangumiIndexViewModel::selectOrder,
            onSortDirectionToggle = bangumiIndexViewModel::toggleSortDirection,
            onQueryChanged = bangumiIndexViewModel::updateQuery,
            onKeywordChange = bangumiIndexViewModel::setKeyword,
            onRefresh = bangumiIndexViewModel::refresh,
            onLoadMore = bangumiIndexViewModel::loadNextPage,
            onRetry = bangumiIndexViewModel::retry,
            onOpen = { indexItem, bounds ->
              val card = indexItem.toIndexBangumiCard()
              startRootBangumi(
                card = card,
                item = card.toBangumiVideoItem(),
                cardBounds = bounds,
                pageOrigin = PageOrigin.BangumiIndex,
                videoOrigin = VideoOrigin.OTHER,
                restoreEpisodeSelection = !bangumiIndexState.searching,
              )
            },
            onItemBounds = { item, bounds ->
              if (bounds.hasUsableSize()) bangumiIndexCardBounds[item.stableId] = bounds
            },
            hiddenItemId = hiddenBangumiIndexItemId,
            foregroundActive = !appState.isVideoScreen && transitionPhase is TransitionPhase.Feed,
          )
        }
      }
    }

    // Layer 1: Video
    if (showVideo) {
      val item = appState.selectedVideo!!
      val bangumiPageUi =
        activeBangumiPage?.let { page ->
          BangumiPageUi(
            sourceCard = page.sourceCard,
            season = page.season,
            loading = page.loading,
            error = page.error,
            currentEpisodeId = page.currentEpisodeId,
            posterVisible =
              transitionSession?.let { session ->
                val enteringPosterFlight =
                  session.fitCover &&
                    session.kind in
                      setOf(
                        TransitionKind.ENTER_ROOT,
                        TransitionKind.ENTER_RECOMMENDATION,
                        TransitionKind.ENTER_PROFILE,
                      )
                !enteringPosterFlight ||
                  session.phase in
                    setOf(
                      SessionPhase.WAITING_FIRST_FRAME,
                      SessionPhase.REVEALING,
                      SessionPhase.COMPLETED,
                    )
              } ?: true,
            followBusy = page.followBusy,
          )
        }
      val showSearchBangumiTransitionTarget =
        (deferSearchBangumiPageComposition || deferBangumiIndexPageComposition) &&
          activeBangumiPage?.sourceOrigin in setOf(PageOrigin.Search, PageOrigin.BangumiIndex) &&
          activeSession?.kind == TransitionKind.ENTER_ROOT
      val showBangumiHomeTransitionTarget =
        deferBangumiHomePageComposition &&
          activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome &&
          activeSession?.kind == TransitionKind.ENTER_ROOT
      Box(
        Modifier.fillMaxSize().graphicsLayer {
          val pageAlpha =
            when {
              preparingRootEnter ||
                shouldHideVideoPageBehindExitCover(activeSession?.kind, activeSession?.phase) -> 0f
              rootEnterSession != null -> rootEnterSession.backgroundAlpha.value.coerceIn(0f, 1f)
              profileEnterSession != null ->
                profileEnterSession.backgroundAlpha.value.coerceIn(0f, 1f)
              else -> 1f
            }
          alpha = pageAlpha * directHomeAlpha.value
        }
      ) {
        if (showSearchBangumiTransitionTarget) {
          SearchBangumiTransitionTarget { bounds ->
            if (bounds.hasUsableSize()) bangumiPosterBounds = bounds
          }
        } else if (showBangumiHomeTransitionTarget) {
          BangumiPlayerTransitionTarget { bounds ->
            if (bounds.hasUsableSize()) playerBounds = bounds
          }
        } else {
          CompositionLocalProvider(
            LocalCoverImageLoadingEnabled provides (bangumiHomeTransitionSession == null)
          ) {
            VideoScreen(
          item = item,
          description = videoDescription,
          videoInfo = videoInfo,
          currentCid = historyCid,
          videoEngagement = videoEngagement,
          favoriteFolders = favoriteFolders,
          favoriteFoldersLoading = favoriteFoldersLoading,
          showCoverUntilFirstFrame = showEmbeddedCover && renderedVideoId != item.id,
          onlineViewerText = onlineViewerText,
          playerState = playerState,
          danmaku = danmaku,
          danmakuPaused = transitionVisualsActive,
          commentItems = commentItems,
          commentTotalCount = commentTotalCount,
          commentHasMore = commentHasMore,
          commentsLoading = commentsLoading,
          commentSort = commentSort,
          commentsRefreshing = commentsRefreshing,
          pageContentLoading = videoPageDataReadyId != item.id,
          currentAccountMid = authUserInfo.mid,
          hiddenCommentAvatarRpid =
            commentProfileTransition?.takeIf { it.sourceAvatarBounds != null }?.sourceComment?.rpid,
          commentNavigationTarget = pendingVideoCommentTarget,
          replyRoot = replyRoot,
          replyItems = replyItems,
          replyHasMore = replyHasMore,
          repliesLoading = repliesLoading,
          emotes = emotes,
          emotePackages = emotePackages,
          mentionSuggestions = mentionSuggestions,
          mentionSuggestionsLoading = mentionSuggestionsLoading,
          recommendations = videoRecommendations,
          hiddenRecommendationCoverItemId = hiddenRecommendationCoverItemId,
          hiddenPlaybackEndRecommendationCoverItemId =
            hiddenPlaybackEndRecommendationCoverItemId,
          hiddenLinkedArticleItemId = hiddenVideoCommentArticleItemId,
          currentPositionMs = playerUiPositionProvider,
          durationMs = historyDuration * 1000,
          playerPositionProvider = playerPositionProvider,
          isPlaying =
            if (scrubPreviewMs != null || pendingSeekTargetMs != null) seekWasPlaying
            else isPlaying,
          isBuffering = isBuffering,
          playbackEnded = playbackEnded,
          playbackSpeed = playbackSpeed,
          showDanmaku = showDanmaku,
          isFullscreen = appState.video.isFullscreen,
          isPlaybackPageForeground =
            isVideoPageForeground(
              videoScreenVisible = appState.isVideoScreen,
              profileVisible = profileMid != null,
              // A Bangumi detail page is still a video page, but it must not claim foreground
              // while a profile opened from its comments is visible above it.
              profileSuppressed = profileLayerSuppressed,
            ),
          pageExitAlpha = { videoExitPrelude?.pageAlpha?.value ?: 1f },
          playerControlsVisible = playerControlsVisible,
          panelSlideProgress = {
            transitionSession?.let { session ->
              when (session.kind) {
                TransitionKind.EXIT_ROOT,
                TransitionKind.EXIT_RECOMMENDATION,
                TransitionKind.EXIT_PROFILE -> 1f - session.panelAlpha.value
                else -> session.panelAlpha.value
              }
            } ?: 1f
          },
          settings = settings,
          onSettingsChange = settingsViewModel::update,
          onFullscreenChanged = mainViewModel::onFullscreenChanged,
          onFullscreenTransitionChanged = { active ->
            playerViewHolder[0]?.view?.setDanmakuTransitionSuppressed(active)
          },
          onBack = {
            if (directHomeInProgress) Unit
            else if (appState.video.isFullscreen) mainViewModel.onFullscreenChanged(false)
            else if (
              transitionSession?.kind == TransitionKind.ENTER_ROOT ||
                transitionSession?.kind == TransitionKind.ENTER_RECOMMENDATION ||
                transitionSession?.kind == TransitionKind.ENTER_PROFILE
            )
              reverseActiveEnter()
            else if (transitionSession == null && transitionPhase is TransitionPhase.ToVideo)
              if (videoStack.lastOrNull()?.parentPage is PageOrigin.Profile)
                cancelPreparingProfileVideo()
              else cancelPreparingRootEnter()
            else if (transitionPhase is TransitionPhase.Video)
              if (activeBangumiPage != null)
                startExitBangumi()
              else if (videoStack.lastOrNull()?.parentPage is PageOrigin.Profile)
                startExitVideoToProfile()
              else if (videoStack.size > 1) startBackToPreviousVideo() else startExitVideo()
          },
          onHome = {
            // returnDirectlyHome commits synchronously; keep the PGC page identity alive for it.
            returnDirectlyHome()
            activeBangumiPage = null
            deferSearchBangumiPageComposition = false
            deferBangumiIndexPageComposition = false
            deferBangumiHomePageComposition = false
            bangumiPosterBounds = Rect.Zero
          },
          onTogglePlayPause = {
            val p = playerViewModel.exoPlayer
            if (p != null) {
              if (p.isPlaying) p.pause() else p.play()
            }
          },
          onTemporarySpeedChanged = ::setTemporarySpeedBoost,
          onPlaybackSpeedChanged = ::setPlaybackSpeed,
          onRetryPlayback = {
            playerSession.clearPlaybackEnded()
            playerViewModel.retry()
          },
          onRetryNextQuality = {
            playerSession.clearPlaybackEnded()
            playerViewModel.retryWithNextQuality()
          },
          onReplay = {
            val replayItem = appState.selectedVideo
            playerSession.clearPlaybackEnded()
            if (replayItem != null) {
              val retained = videoEntryCache[replayItem.id]
              retained?.let {
                cacheEntry(it.copy(savedPositionMs = 0L, playbackEnded = false))
              }
              playerSession.currentPositionMs = 0L
              if (!playerViewModel.replayIfLoaded(replayItem.id)) {
                // A retained completed parent deliberately does not replace the child's media
                // while its end overlay is restored. Reload that parent only when the user asks
                // to replay, preserving its selected part and quality while forcing position 0.
                playerActivationId = replayItem.id
                rootPlayerOwnership =
                  RootPlayerOwnership(RootPlayerSurfaceRole.DETAIL_PENDING, replayItem.id)
                showEmbeddedCover = true
                val replayPage = videoInfo?.pages?.firstOrNull { it.cid == historyCid }
                playerViewModel.loadVideo(
                  item = replayItem,
                  startPositionMs = 0L,
                  preferredStreamIndex = retained?.qualityIndex,
                  preferredResolutionMode = settings.preferredResolutionMode,
                  page = replayPage,
                  restoreSavedProgress = false,
                )
              }
            }
          },
          onSeek = ::commitSeek,
          onSeekPreview = ::previewSeek,
          onSeekCancel = ::cancelSeekPreview,
          onToggleDanmaku = { showDanmaku = !showDanmaku },
          onSendDanmaku = { message, color, mode, fontSize, colorful ->
            if (historyCid > 0) {
              val position = playerViewModel.exoPlayer?.currentPosition ?: 0L
              scope.launch {
                val result =
                  withContext(Dispatchers.IO) {
                    runCatching {
                      BiliApi.sendDanmakuAuthenticated(
                        cid = historyCid,
                        aid = historyAid,
                        bvid =
                          item.id.takeIf { it.startsWith("BV") }
                            ?: item.videoUrl
                              .substringAfterLast("/")
                              .substringBefore("?")
                              .takeIf { it.startsWith("BV") }
                              .orEmpty(),
                        message = message,
                        progressMs = position,
                        color = color,
                        mode = mode,
                        fontSize = fontSize,
                        colorful = colorful,
                      )
                    }
                  }
                if (result.isSuccess) {
                  danmaku =
                    danmaku +
                      DanmakuItem(
                        timeMs = position,
                        type = mode,
                        fontSize = fontSize,
                        color = color,
                        content = message,
                        isLocal = true,
                        colorful = colorful,
                      )
                } else {
                  Toast.makeText(
                      context,
                      result.exceptionOrNull()?.message ?: "弹幕发送失败",
                      Toast.LENGTH_SHORT,
                    )
                    .show()
                }
              }
            }
          },
          onRecommendationClick = { rec, bounds, returnBounds, fromPlaybackEnd ->
            startRecommendedVideo(item, rec, bounds, returnBounds, fromPlaybackEnd)
          },
          onVideoPageSelected = ::selectVideoPage,
          onCollectionEpisodeSelected = { episode, _ -> selectCollectionEpisode(episode) },
          onLoadMoreComments = {
            if (!commentsLoading && commentOid != 0L && commentHasMore) {
              val expectedUrl = item.videoUrl
              val expectedOid = commentOid
              val expectedSort = commentSort
              val next =
                if (expectedSort == CommentSort.TIME) {
                  commentTimeNextPage(commentPage)
                } else {
                  commentPage + 1
                }
              commentsLoading = true
              scope.launch {
                try {
                  val resp =
                    withContext(Dispatchers.IO) {
                      BiliApi.getComments(expectedOid, next, expectedSort.apiValue)
                    }
                  if (
                    mainViewModel.state.value.selectedVideo?.videoUrl != expectedUrl ||
                      commentOid != expectedOid ||
                      commentSort != expectedSort
                  )
                    return@launch
                  val combined = (commentItems + resp.items).distinctBy { it.rpid }
                  commentItems =
                    if (expectedSort == CommentSort.TIME) {
                      orderCommentsByTime(combined)
                    } else combined
                  commentHasMore =
                    if (expectedSort == CommentSort.TIME) {
                      commentTimeHasMore(next, resp.totalCount)
                    } else {
                      resp.hasMore
                    }
                  commentPage = next
                } finally {
                  if (mainViewModel.state.value.selectedVideo?.videoUrl == expectedUrl)
                    commentsLoading = false
                }
              }
            }
          },
          onRefreshComments = {
            if (!commentsRefreshing && !commentsLoading && commentOid > 0L) {
              val expectedItemId = item.id
              val expectedOid = commentOid
              val expectedSort = commentSort
              val targetPage =
                if (expectedSort == CommentSort.TIME) {
                  commentTimeStartPage()
                } else {
                  1
                }
              commentsRefreshing = true
              scope.launch {
                try {
                  val response =
                    withContext(Dispatchers.IO) {
                      BiliApi.getComments(expectedOid, targetPage, expectedSort.apiValue)
                    }
                  if (
                    appState.selectedVideo?.id == expectedItemId &&
                      commentOid == expectedOid &&
                      commentSort == expectedSort
                  ) {
                    commentItems =
                      if (expectedSort == CommentSort.TIME) {
                        orderCommentsByTime(response.items)
                      } else response.items
                    commentTotalCount = response.totalCount
                    commentHasMore =
                      if (expectedSort == CommentSort.TIME) {
                        commentTimeHasMore(targetPage, response.totalCount)
                      } else {
                        response.hasMore
                      }
                    commentPage = targetPage
                  }
                } finally {
                  if (appState.selectedVideo?.id == expectedItemId) commentsRefreshing = false
                }
              }
            }
          },
          onPostComment = { message, imageUri ->
            if (!commentsLoading && commentOid != 0L) {
              val expectedOid = commentOid
              commentsLoading = true
              scope.launch {
                try {
                  val added =
                    withContext(Dispatchers.IO) {
                      val uploadedImage = imageUri?.let { uploadCommentImage(context, it) }
                      BiliApi.addComment(expectedOid, message, image = uploadedImage)
                    }
                  if (commentOid == expectedOid) {
                    val updated = (commentItems + added).distinctBy { it.rpid }
                    commentItems =
                      if (commentSort == CommentSort.TIME) {
                        orderCommentsByTime(updated)
                      } else listOf(added) + commentItems
                  }
                  if (commentOid == expectedOid) commentTotalCount += 1
                } catch (error: Exception) {
                  if (error is kotlinx.coroutines.CancellationException) throw error
                  Toast.makeText(context, error.message ?: "评论发送失败", Toast.LENGTH_SHORT).show()
                } finally {
                  if (commentOid == expectedOid) commentsLoading = false
                }
              }
            }
          },
          onRecommendationLongClick = { showVideoPreview(it) },
          onArticleClick = { article, bounds ->
            startEnterArticle(article, bounds.takeIf(Rect::hasUsableSize), ArticleOrigin.VIDEO)
          },
          onPostReply = { root, parent, message, imageUri ->
            if (!repliesLoading && commentOid != 0L) {
              val expectedOid = commentOid
              repliesLoading = true
              scope.launch {
                try {
                  val added =
                    withContext(Dispatchers.IO) {
                      val uploadedImage = imageUri?.let { uploadCommentImage(context, it) }
                      BiliApi.addReply(
                        expectedOid,
                        root.rpid,
                        parent.rpid,
                        message,
                        image = uploadedImage,
                      )
                    }
                  if (commentOid == expectedOid) {
                    if (replyRoot?.rpid == root.rpid)
                      replyItems = (replyItems + added).distinctBy { it.rpid }
                    commentItems = commentItems.map {
                      if (it.rpid == root.rpid) it.copy(replyCount = it.replyCount + 1) else it
                    }
                  }
                } catch (error: Exception) {
                  if (error is kotlinx.coroutines.CancellationException) throw error
                  Toast.makeText(context, error.message ?: "回复发送失败", Toast.LENGTH_SHORT).show()
                } finally {
                  if (commentOid == expectedOid) repliesLoading = false
                }
              }
            }
          },
          onLikeComment = { comment ->
            val expectedOid = commentOid
            scope.launch {
              val target = !comment.liked
              val success =
                withContext(Dispatchers.IO) {
                  runCatching { BiliApi.setCommentLike(expectedOid, comment.rpid, target) }
                    .isSuccess
                }
              if (success && commentOid == expectedOid)
                commentItems = commentItems.map {
                  if (it.rpid == comment.rpid)
                    it.copy(
                      liked = target,
                      likeCount = (it.likeCount + if (target) 1 else -1).coerceAtLeast(0),
                    )
                  else it
                }
              if (success && commentOid == expectedOid)
                replyItems = replyItems.map {
                  if (it.rpid == comment.rpid)
                    it.copy(
                      liked = target,
                      likeCount = (it.likeCount + if (target) 1 else -1).coerceAtLeast(0),
                    )
                  else it
                }
            }
          },
          onDeleteComment = { comment ->
            val expectedOid = commentOid
            if (expectedOid > 0L) {
              scope.launch {
                val result =
                  withContext(Dispatchers.IO) {
                    runCatching { BiliApi.deleteComment(expectedOid, comment.rpid) }
                  }
                result
                  .onSuccess {
                    if (commentOid == expectedOid) {
                      val deletedRoot = commentItems.any { it.rpid == comment.rpid }
                      commentItems = commentItems.filterNot { it.rpid == comment.rpid }
                      if (replyRoot?.rpid == comment.rpid) {
                        replyRoot = null
                        replyItems = emptyList()
                        replyHasMore = false
                      } else if (replyItems.any { it.rpid == comment.rpid }) {
                        replyItems = replyItems.filterNot { it.rpid == comment.rpid }
                        replyRoot = replyRoot?.let { root ->
                          root.copy(replyCount = (root.replyCount - 1).coerceAtLeast(0))
                        }
                        val rootId = replyRoot?.rpid
                        commentItems = commentItems.map { root ->
                          if (root.rpid == rootId)
                            root.copy(replyCount = (root.replyCount - 1).coerceAtLeast(0))
                          else root
                        }
                      }
                      if (deletedRoot) commentTotalCount = (commentTotalCount - 1).coerceAtLeast(0)
                      appState.selectedVideo?.let { cacheEntry(snapshotEntry(it)) }
                    }
                    Toast.makeText(context, "评论已删除", Toast.LENGTH_SHORT).show()
                  }
                  .onFailure {
                    Toast.makeText(context, it.message ?: "删除失败", Toast.LENGTH_SHORT).show()
                  }
              }
            }
          },
          onLikeVideo = { targetLiked ->
            val expected = videoInfo
            if (expected != null && !videoActionBusy) {
              val previousEngagement = videoEngagement
              val previousInfo = expected
              videoActionBusy = true
              videoEngagement = previousEngagement.copy(liked = targetLiked)
              videoInfo =
                previousInfo.copy(
                  likeCount = (previousInfo.likeCount + if (targetLiked) 1 else -1).coerceAtLeast(0)
                )
              scope.launch {
                val result =
                  withContext(Dispatchers.IO) {
                    runCatching { BiliApi.setVideoLike(expected.aid, targetLiked) }
                  }
                result
                  .onSuccess {
                    Toast.makeText(
                        context,
                        if (targetLiked) "已点赞" else "已取消点赞",
                        Toast.LENGTH_SHORT,
                      )
                      .show()
                  }
                  .onFailure {
                    if (videoInfo?.aid == expected.aid) {
                      videoEngagement = previousEngagement
                      videoInfo = previousInfo
                    }
                    Toast.makeText(context, it.message ?: "点赞失败", Toast.LENGTH_SHORT).show()
                  }
                if (videoInfo?.aid == expected.aid) videoActionBusy = false
              }
            }
          },
          onCoinVideo = { count, alsoLike ->
            val expected = videoInfo
            val remaining = (2 - videoEngagement.coins).coerceAtLeast(0)
            if (expected != null && !videoActionBusy && count in 1..remaining) {
              videoActionBusy = true
              scope.launch {
                val result =
                  withContext(Dispatchers.IO) {
                    runCatching { BiliApi.coinVideo(expected.aid, count, alsoLike) }
                  }
                result
                  .onSuccess {
                    if (videoInfo?.aid == expected.aid) {
                      val wasLiked = videoEngagement.liked
                      videoEngagement =
                        videoEngagement.copy(
                          coins = (videoEngagement.coins + count).coerceAtMost(2),
                          liked = videoEngagement.liked || alsoLike,
                        )
                      videoInfo =
                        videoInfo?.copy(
                          coinCount = expected.coinCount + count,
                          likeCount = expected.likeCount + if (alsoLike && !wasLiked) 1 else 0,
                        )
                    }
                    Toast.makeText(context, "已投 $count 枚硬币", Toast.LENGTH_SHORT).show()
                  }
                  .onFailure {
                    Toast.makeText(context, it.message ?: "投币失败", Toast.LENGTH_SHORT).show()
                  }
                if (videoInfo?.aid == expected.aid) videoActionBusy = false
              }
            }
          },
          onFavoriteVideo = { addIds, removeIds ->
            val expected = videoInfo
            if (expected != null && !videoActionBusy) {
              val previousFolders = favoriteFolders
              val wasFavorited = previousFolders.any { it.favorited }
              videoActionBusy = true
              scope.launch {
                val result =
                  withContext(Dispatchers.IO) {
                    runCatching { BiliApi.setFavoriteFolders(expected.aid, addIds, removeIds) }
                  }
                result
                  .onSuccess {
                    if (videoInfo?.aid == expected.aid) {
                      favoriteFolders = previousFolders.map { folder ->
                        when (folder.id) {
                          in addIds -> folder.copy(favorited = true)
                          in removeIds -> folder.copy(favorited = false)
                          else -> folder
                        }
                      }
                      val isFavorited = favoriteFolders.any { it.favorited }
                      videoEngagement = videoEngagement.copy(favorited = isFavorited)
                      if (wasFavorited != isFavorited) {
                        videoInfo =
                          videoInfo?.copy(
                            favoriteCount =
                              (expected.favoriteCount + if (isFavorited) 1 else -1).coerceAtLeast(0)
                          )
                      }
                      Toast.makeText(
                          context,
                          if (isFavorited) "收藏成功" else "已取消收藏",
                          Toast.LENGTH_SHORT,
                        )
                        .show()
                    }
                  }
                  .onFailure {
                    Toast.makeText(context, it.message ?: "收藏失败", Toast.LENGTH_SHORT).show()
                  }
                if (videoInfo?.aid == expected.aid) videoActionBusy = false
              }
            }
          },
          onLoadFavoriteFolders = {
            val aid = videoInfo?.aid ?: 0L
            if (!favoriteFoldersLoading && aid > 0) {
              favoriteFoldersLoading = true
              scope.launch {
                val result =
                  withContext(Dispatchers.IO) {
                    runCatching { BiliApi.getFavoriteFolders(authUserInfo.mid, aid) }
                  }
                result
                  .onSuccess { folders ->
                    if (videoInfo?.aid == aid) {
                      favoriteFolders = folders
                      videoEngagement =
                        videoEngagement.copy(favorited = folders.any { it.favorited })
                    }
                  }
                  .onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "收藏夹加载失败",
                        Toast.LENGTH_SHORT,
                      )
                      .show()
                  }
                if (videoInfo?.aid == aid) favoriteFoldersLoading = false
              }
            }
          },
          onPlayerBoundsChanged = { bounds ->
            if (bounds.width > 0f && bounds.height > 0f) playerBounds = bounds
          },
          onOpenReplies = { comment ->
            if (!repliesLoading && commentOid > 0) {
              replyRoot = comment
              replyItems = emptyList()
              replyPage = 1
              replyHasMore = false
              repliesLoading = true
              val expectedOid = commentOid
              scope.launch {
                try {
                  val response =
                    withContext(Dispatchers.IO) {
                      BiliApi.getCommentReplies(expectedOid, comment.rpid, 1)
                    }
                  if (commentOid == expectedOid && replyRoot?.rpid == comment.rpid) {
                    replyItems = response.items
                    replyHasMore = response.hasMore
                  }
                } finally {
                  if (replyRoot?.rpid == comment.rpid) repliesLoading = false
                }
              }
            }
          },
          onLoadMoreReplies = {
            val root = replyRoot
            if (root != null && replyHasMore && !repliesLoading && commentOid > 0) {
              repliesLoading = true
              val expectedOid = commentOid
              val next = replyPage + 1
              scope.launch {
                try {
                  val response =
                    withContext(Dispatchers.IO) {
                      BiliApi.getCommentReplies(expectedOid, root.rpid, next)
                    }
                  if (commentOid == expectedOid && replyRoot?.rpid == root.rpid) {
                    replyItems = (replyItems + response.items).distinctBy { it.rpid }
                    replyHasMore = response.hasMore
                    replyPage = next
                  }
                } finally {
                  if (replyRoot?.rpid == root.rpid) repliesLoading = false
                }
              }
            }
          },
          onRefreshReplies = {
            val root = replyRoot
            if (root != null && !repliesLoading && commentOid > 0L) {
              repliesLoading = true
              val expectedOid = commentOid
              scope.launch {
                try {
                  val response =
                    withContext(Dispatchers.IO) {
                      BiliApi.getCommentReplies(expectedOid, root.rpid, 1)
                    }
                  if (commentOid == expectedOid && replyRoot?.rpid == root.rpid) {
                    replyItems = response.items
                    replyHasMore = response.hasMore
                    replyPage = 1
                  }
                } finally {
                  if (replyRoot?.rpid == root.rpid) repliesLoading = false
                }
              }
            }
          },
          onDismissReplies = { replyRoot = null },
          onCommentNavigationConsumed = { pendingVideoCommentTarget = null },
          onProfileClick = { mid, face, name, bounds ->
            openAvatarProfile(mid, bounds, face, name)
          },
          onUploaderProfileClick = { mid, face, name, bounds ->
            openAvatarProfile(mid, bounds, face, name)
          },
          onCommentSort = ::selectCommentSort,
          showUploaderFollowButton =
            (videoInfo?.uploaderMid ?: item.uploaderMid).let {
              it > 0L && it != authUserInfo.mid
            },
          uploaderFollowed = followingStates[videoInfo?.uploaderMid ?: item.uploaderMid] == true,
          uploaderFollowBusy = followingBusy[videoInfo?.uploaderMid ?: item.uploaderMid] == true,
          followingGroups = followingGroups,
          followingGroupsLoading = followingGroupsLoading,
          loggedIn = authUserInfo.isLogin,
          premiumAudioVisible = authUserInfo.vipActive,
          commentImageEnabled = authUserInfo.vipActive || userInfo.vipActive,
          onLoadFollowingGroups = ::loadFollowingGroups,
          onSelectUploaderFollowingGroup = { groupId ->
            selectFollowingGroup(videoInfo?.uploaderMid ?: item.uploaderMid, groupId)
          },
          onUnfollowUploader = { unfollow(videoInfo?.uploaderMid ?: item.uploaderMid) },
          onLogin = authViewModel::startLogin,
          onCommentProfileClick = ::openCommentProfile,
          onMentionQuery = ::loadMentionSuggestions,
          playerView = { modifier, fullscreenProgress, fullscreen ->
            if (playerReady && !rootPlayerHostEnabled && !bangumiDetailPlayerSuppressed) {
              rootPlayerContent(
                modifier,
                fullscreenProgress,
                fullscreen,
                true,
                SharedPlayerViewRole.DETAIL,
              )
            }
          },
          onSwitchQuality = { idx -> playerViewModel.switchQuality(idx) },
          onSwitchPremiumAudio = playerViewModel::switchPremiumAudio,
          bangumiPage = bangumiPageUi,
          onBangumiPosterBoundsChanged = { bounds ->
            if (bounds.hasUsableSize()) bangumiPosterBounds = bounds
          },
          onBangumiEpisodeSelected = ::selectBangumiEpisode,
          onBangumiSeasonSelected = ::selectBangumiSeason,
          onBangumiFollow = ::toggleBangumiFollow,
          onBangumiRate = ::postBangumiShortReview,
          )
        }
        }
        if (navigationLocked) {
          Box(
            Modifier.fillMaxSize().pointerInput(transitionToken, transitionPhase) {
              awaitPointerEventScope {
                while (true) {
                  awaitPointerEvent().changes.forEach { it.consume() }
                }
              }
            }
          )
        }
      }
    }

    // Only the bangumi homepage owns this app-root PlayerView. Card flights detach the real player;
    // the landed detail page mounts it later underneath the stationary cached cover.
    RootPlayerLayer(
      hostEnabled =
        rootPlayerHostEnabled &&
          rootPlayerOwnership.role !in
            setOf(RootPlayerSurfaceRole.PREVIEW, RootPlayerSurfaceRole.PREVIEW_PENDING),
      ownership = rootPlayerOwnership,
      previewBounds = Rect.Zero,
      previewCoverAlpha = { 1f },
      previewCoverBlend = null,
      previewGestureVisualActive = false,
      previewPortalVisible = false,
      previewImageLoadingEnabled = false,
      previewTarget = bangumiPreviewTarget,
      layerItem = appState.selectedVideo ?: bangumiPreviewTarget?.item,
      playerContent = { host ->
        rootPlayerContent(
          host.modifier,
          host.fullscreenProgress,
          host.fullscreen,
          host.danmakuAllowed,
          SharedPlayerViewRole.PREVIEW,
        )
      },
    )

    // Layer 1: Article. It is a peer detail page to Video and keeps its root source mounted.
    articleStack.forEachIndexed { frameIndex, frame ->
      key(frame.entryId) {
        val isTopFrame = frameIndex == articleStack.lastIndex
        val profileCoversArticle = profileStack.isNotEmpty() && !profileLayerSuppressed
        val returningVideoToArticle =
          videoStack.firstOrNull()?.parentPage == PageOrigin.Article &&
            transitionPhase is TransitionPhase.ToFeed
        // A video opened from a profile that itself sits above an article is still the top page.
        // Keeping the article opaque here made the player audible but visually hidden underneath.
        val articleLayerVisible = !showVideo || returningVideoToArticle
        Box(
          Modifier.fillMaxSize().graphicsLayer {
            alpha =
              if (!articleLayerVisible) 0f
              else if (isTopFrame && articleRestoringParentEntryId == frame.entryId) 1f
              else if (isTopFrame) articlePageAlpha.value.coerceIn(0f, 1f) else 1f
          }
        ) {
          val retainedDetail =
            if (isTopFrame) articleDetail else articleDetailCache[frame.article.id]
          ArticleScreen(
            article = frame.article,
            detail = retainedDetail,
            loading = if (isTopFrame) articleLoading else false,
            error = if (isTopFrame) articleError else null,
            contentLoadEnabled = if (isTopFrame) articleContentReady else retainedDetail != null,
            currentAccountMid = authUserInfo.mid,
            showCommentEmotes = settings.showCommentEmotes,
            showCommentLocation = settings.showCommentLocation,
            sharedEmotes = emotes,
            sharedEmotePackages = emotePackages,
            mentionSuggestions = mentionSuggestions,
            mentionSuggestionsLoading = mentionSuggestionsLoading,
            hiddenVideoCoverItemId = hiddenArticleVideoCoverItemId,
            hiddenLinkedArticleItemId = hiddenArticleCommentArticleItemId,
            commentNavigationTarget = pendingArticleCommentTarget.takeIf { isTopFrame },
            onBack = ::startExitArticle,
            onHome = ::returnDirectlyHome,
            onRetry = { loadArticleDetail(frame.article) },
            onMentionQuery = ::loadMentionSuggestions,
            onAuthorProfile = { mid, face, name, bounds ->
              openAvatarProfile(mid, bounds, face, name)
            },
            onProfile = ::openProfile,
            onCommentProfile = ::openArticleCommentProfile,
            onCommentNavigationConsumed = { pendingArticleCommentTarget = null },
            onVideo = { video, bounds ->
              if (bounds.hasUsableSize()) articleVideoBounds[video.id] = bounds
              startEnterVideo(video, bounds.takeIf(Rect::hasUsableSize), VideoOrigin.ARTICLE)
            },
            onVideoLongClick = { showVideoPreview(it) },
            onVideoBoundsChanged = { video, bounds ->
              if (bounds.hasUsableSize()) articleVideoBounds[video.id] = bounds
            },
            onArticle = { nestedArticle, bounds ->
              startEnterArticle(
                nestedArticle,
                bounds.takeIf(Rect::hasUsableSize),
                ArticleOrigin.ARTICLE,
              )
            },
            onHeroBoundsChanged = { bounds ->
              if (isTopFrame && bounds.hasUsableSize()) articleHeroBounds = bounds
            },
            heroVisible =
              !isTopFrame ||
                articleTransitionSession == null ||
                articleTransitionSession?.article?.stableId != frame.article.stableId,
            interactionEnabled =
              isTopFrame &&
                articleLayerVisible &&
                !profileCoversArticle &&
                articleTransitionSession == null,
            backEnabled =
              isTopFrame &&
                articleLayerVisible &&
                !profileCoversArticle &&
                articleTransitionSession == null,
          )
          if (isTopFrame && articleTransitionSession != null) {
            Box(
              Modifier.fillMaxSize().pointerInput(frame.entryId) {
                awaitPointerEventScope {
                  while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
              }
            )
          }
        }
      }
    }
  }
  if (!profileLayerSuppressed)
    AppRootProfileLayer(
      // A retained profile is hidden exclusively through profileLayerSuppressed. When it is
      // composed, it is the current top page — including for a profile opened from a Bangumi
      // comment. Keeping this at -1 for Bangumi placed both the page and its shared-avatar
      // transition below VideoScreen.
      modifier = Modifier.zIndex(1f),
      profileMid = profileMid,
      profileStateHolder = profileStateHolder,
      profile = spaceProfile,
      videos = spaceVideos,
      dynamics = spaceDynamics,
      dynamicsLoading = spaceDynamicLoading,
      dynamicsHasMore = spaceDynamicHasMore,
      dynamicsError = spaceDynamicError,
      selectedDynamicId = selectedDynamicId,
      loading = spaceLoading,
      hasMore = spaceHasMore,
      error = spaceError,
      currentPage = spacePage,
      currentAccountMid = authUserInfo.mid,
      followed = profileMid?.let { followingStates[it] ?: spaceProfile?.followed } ?: false,
      followBusy = profileMid?.let { followingBusy[it] } == true,
      followingGroups = followingGroups,
      followingGroupsLoading = followingGroupsLoading,
      loggedIn = authUserInfo.isLogin,
      profileIpAuthorized = profileIpAuthorized,
      settings = settings,
      hiddenCoverItemId = hiddenProfileCoverItemId,
      hiddenArticleItemId = hiddenProfileArticleItemId,
      profileAvatarBounds = profileAvatarBounds,
      commentTransition = commentProfileTransition,
      commentReturnTransition = commentProfileReturnTransition,
      avatarTransition = avatarProfileTransition,
      avatarReturnTransition = avatarProfileReturnTransition,
      activeSession = activeSession,
      profileVideoTransitionActive = profileVideoTransitionActive,
      onBack = {
        when {
          transitionSession?.kind == TransitionKind.ENTER_PROFILE -> reverseActiveEnter()
          transitionSession == null && transitionPhase is TransitionPhase.ToVideo ->
            cancelPreparingProfileVideo()
          else -> closeProfile()
        }
      },
      onVideoClick = ::startProfileVideo,
      onBangumiClick = ::startProfileBangumi,
      onVideoLongClick = { showVideoPreview(it) },
      onArticleClick = { article, bounds ->
        val sourceBounds = bounds.takeUnless { it == Rect.Zero }
        if (sourceBounds != null) profileArticleBounds[article.stableId] = sourceBounds
        startEnterArticle(article, sourceBounds, ArticleOrigin.PROFILE)
      },
      onArticleBoundsChanged = { article, bounds ->
        if (bounds.hasUsableSize()) profileArticleBounds[article.stableId] = bounds
      },
      onLoadPage = { page -> profileMid?.let { loadSpacePage(it, page) } },
      onRefresh = { profileMid?.let(::loadPreparedProfile) },
      onLoadFollowingGroups = ::loadFollowingGroups,
      onSelectFollowingGroup = { entryId, groupId ->
        activeProfileEntry(entryId)?.state?.let { state ->
          state.profileMid?.let { mid ->
            state.selectFollowingGroup(
              mid = mid,
              groupId = groupId,
              loggedIn = authUserInfo.isLogin,
              onLogin = authViewModel::startLogin,
              context = context,
              scope = scope,
            )
          }
        }
      },
      onUnfollow = { entryId ->
        activeProfileEntry(entryId)?.state?.let { state ->
          state.profileMid?.let { mid ->
            state.unfollow(
              mid = mid,
              loggedIn = authUserInfo.isLogin,
              onLogin = authViewModel::startLogin,
              context = context,
              scope = scope,
            )
          }
        }
      },
      onPrivateMessagesSelected = { mid, name, face ->
        if (authUserInfo.isLogin) {
          profileMessageViewModel.openPrivateConversation(mid, name, face)
        } else {
          authViewModel.startLogin()
        }
      },
      privateMessageContent = {
        ProfilePrivateConversationPane(
          state = profileMessageState,
          onLoadMoreHistory = profileMessageViewModel::loadMorePrivateMessageHistory,
          onReplyPrivate = { text, imageUri ->
            profileMessageViewModel.replyToSelectedPrivate(
              context.applicationContext,
              text,
              imageUri,
            )
          },
          onWithdraw = profileMessageViewModel::withdrawPrivateMessage,
          onDelete = profileMessageViewModel::deletePrivateMessage,
          onProfile = { _, _, _, _ -> },
          onTarget = ::openInteractionTarget,
        )
      },
      onLogin = authViewModel::startLogin,
      onAuthorizeProfileIp = authViewModel::startAppAuthorization,
      onCommentProfileClick = { entryId, mid, comment, anchor ->
        openCommentProfileFrom(entryId, mid, comment, anchor)
      },
      onAvatarProfileClick = { entryId, mid, face, name, bounds ->
        openAvatarProfileFrom(entryId, mid, bounds, face, name)
      },
      onEnsureDynamics = {
        if (spaceDynamics.isEmpty()) loadSpaceDynamics(refresh = false)
      },
      onLoadMoreDynamics = { loadSpaceDynamics(refresh = false) },
      onRefreshDynamics = { loadSpaceDynamics(refresh = true) },
      onSelectedDynamicIdChange = { selectedDynamicId = it },
      onDynamicLike = { entryId, item ->
        activeProfileEntry(entryId)
          ?.state
          ?.toggleDynamicLike(
            item = item,
            accountMid = authUserInfo.mid,
            onLogin = authViewModel::startLogin,
            context = context,
            scope = scope,
          )
      },
      onDynamicDelete = { entryId, item ->
        activeProfileEntry(entryId)
          ?.state
          ?.deleteDynamic(
            item = item,
            accountMid = authUserInfo.mid,
            onLogin = authViewModel::startLogin,
            context = context,
            scope = scope,
          )
      },
      onDynamicPin = { entryId, item ->
        activeProfileEntry(entryId)
          ?.state
          ?.setDynamicPinned(
            item = item,
            accountMid = authUserInfo.mid,
            onLogin = authViewModel::startLogin,
            context = context,
            scope = scope,
          )
      },
      onVideoBoundsChanged = { entryId, video, bounds ->
        profileCardBounds[ProfileVideoKey(entryId, video.id)] = bounds
      },
      onAvatarBoundsChanged = { bounds ->
        if (bounds.width > 0f && bounds.height > 0f) profileAvatarBounds = bounds
      },
      profileStack = profileStack,
      // The visible profile is the top page and therefore owns system Back. Its close transition
      // restores the retained Bangumi playback page underneath.
      backHandlingEnabled = true,
    )
  // Keep exactly one shared-card foreground across the root, player, and retained profile layers.
  // Drawing a second copy inside the profile layer caused a one-frame handoff flash when that
  // layer moved behind the player. HDR playback applies the same focus dim to this foreground so
  // the poster never bypasses the non-player-content mask. Skip the dim during exit transitions
  // because leaveHdrPlaybackPage() has already released the overlay by that point.
  bangumiHomeTransitionSession?.let { session ->
      Box(
        Modifier.fillMaxSize()
          .zIndex(1.5f)
          .graphicsLayer { alpha = session.themeScrimAlpha.value.coerceIn(0f, 1f) }
          .background(MaterialTheme.colorScheme.background)
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
        coverDimAlpha = { if (session.fitCover && isHdrPlayback && !isExit) .48f else 0f },
        // A restored profile deliberately remains above the player while returning. The shared
        // cover must be above that retained layer or its p=1 -> p=0 flight is invisible.
        modifier = Modifier.zIndex(2f),
        bitmap = session.transitionBitmap,
        allowAsyncImageFallback = activeBangumiPage?.sourceOrigin != PageOrigin.BangumiHome,
      )
    }
  articleTransitionSession?.let { session -> ArticleTransitionOverlay(session) }
  // Exit preludes must sit above both the video page and the independently retained profile page.
  // This lets profile return use the same visible handoff as root return: stationary cover first,
  // destination background second, flying cover last.
  videoExitPrelude
    ?.takeIf {
      it.playerBounds != Rect.Zero &&
        it.playerBounds.width > 0f &&
        it.playerBounds.height > 0f
    }
    ?.let { prelude ->
      CardTransitionOverlay(
        item = prelude.item,
        startBounds = prelude.playerBounds,
        endBounds = prelude.playerBounds,
        progress = { 0f },
        overlayAlpha = { prelude.coverAlpha.value },
        fitCover = prelude.fitCover,
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
  if (showSearch && !appState.isVideoScreen) {
    SearchScreen(
      state = searchState,
      searchBounds = searchBounds,
      onQuery = searchViewModel::setQuery,
      onSearch = ::openSearchResultsAnimated,
      onClearHistory = searchViewModel::clearHistory,
      onBack = {
        focusManager.clearFocus(force = true)
        showSearch = false
      },
      onDismiss = {
        focusManager.clearFocus(force = true)
        showSearch = false
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
  if (startupWarmupVisible) {
    Box(
      modifier =
        Modifier.fillMaxSize()
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
      BiliOneStartupAnimation(reduceMotion = settings.reduceMotion)
    }
  }
}

@Composable
private fun BiliOneStartupAnimation(
  reduceMotion: Boolean,
  modifier: Modifier = Modifier,
) {
  // Reuse the exact foreground used by the Android adaptive icon so the first visual impression
  // and the launcher identity cannot drift apart.
  val logoAlpha = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
  val logoScale = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else .9f) }
  LaunchedEffect(reduceMotion) {
    if (reduceMotion) return@LaunchedEffect
    logoAlpha.snapTo(0f)
    logoScale.snapTo(.9f)
    launch { logoAlpha.animateTo(1f, animationSpec = tween(150, easing = FastOutSlowInEasing)) }
    logoScale.animateTo(1f, animationSpec = tween(280, easing = FastOutSlowInEasing))
  }
  Image(
    painter = painterResource(R.drawable.bilione_icon_foreground_compact),
    contentDescription = null,
    modifier =
      modifier.size(172.dp).graphicsLayer {
        alpha = logoAlpha.value
        scaleX = logoScale.value
        scaleY = logoScale.value
      },
  )
}

/**
 * Measures only the poster destination used by the search-to-bangumi shared transition. Keeping
 * this layout independent from [VideoScreen] prevents the player, comments, and page effects from
 * being composed while the poster is in flight.
 */
@Composable
private fun SearchBangumiTransitionTarget(onPosterBoundsChanged: (Rect) -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val contentWidth = maxOf(1.dp, maxWidth - 28.dp)
    val contentHeight = maxOf(1.dp, maxHeight - 88.dp)
    val paneSpec =
      videoPaneSpec(
        widthPx = with(density) { contentWidth.roundToPx() },
        heightPx = with(density) { contentHeight.roundToPx() },
        density = density.density,
        fontScale = density.fontScale,
      )
    val primaryWidth =
      if (paneSpec.split) {
        maxOf(1.dp, contentWidth - with(density) { paneSpec.secondarySizePx.toDp() } - 12.dp)
      } else contentWidth
    val primaryHeight =
      if (paneSpec.split) contentHeight
      else maxOf(1.dp, contentHeight * .68f)
    val pageLayout =
      bangumiPageLayoutForPane(
        primaryWidthDp = primaryWidth.value,
        primaryHeightDp = primaryHeight.value,
        fontScale = density.fontScale,
      )
    val posterHeight = maxOf(1.dp, primaryHeight - pageLayout.playerHeight - 8.dp)
    Box(
      Modifier.offset(x = 16.dp, y = 84.dp + pageLayout.playerHeight)
        .size(width = posterHeight * .75f, height = posterHeight)
        .onGloballyPositioned { onPosterBoundsChanged(it.boundsInRoot()) }
    )
  }
}

/** Lightweight 16:9 destination used while a bangumi-home player/card flight owns the frame. */
@Composable
private fun BangumiPlayerTransitionTarget(onPlayerBoundsChanged: (Rect) -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val contentWidth = maxOf(1.dp, maxWidth - 28.dp)
    val contentHeight = maxOf(1.dp, maxHeight - 88.dp)
    val paneSpec =
      videoPaneSpec(
        widthPx = with(density) { contentWidth.roundToPx() },
        heightPx = with(density) { contentHeight.roundToPx() },
        density = density.density,
        fontScale = density.fontScale,
      )
    val primaryWidth =
      if (paneSpec.split) {
        maxOf(1.dp, contentWidth - with(density) { paneSpec.secondarySizePx.toDp() } - 12.dp)
      } else contentWidth
    val primaryHeight =
      if (paneSpec.split) contentHeight
      else maxOf(1.dp, contentHeight * .68f)
    val pageLayout =
      bangumiPageLayoutForPane(
        primaryWidthDp = primaryWidth.value,
        primaryHeightDp = primaryHeight.value,
        fontScale = density.fontScale,
      )
    Box(
      // Mirror VideoContent exactly: 76 dp BangumiHeader, then the content's 16 dp start padding.
      // The real Surface uses playerWidth, not the complete primary pane width.
      Modifier.offset(x = 16.dp, y = 76.dp)
        .size(width = pageLayout.playerWidth, height = pageLayout.playerHeight)
        .onGloballyPositioned { onPlayerBoundsChanged(it.boundsInRoot()) }
    )
  }
}

private fun uploadCommentImage(context: Context, uri: Uri): BiliApi.PrivateImageUpload {
  val bytes =
    context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
      ?: throw IllegalStateException("无法读取评论图片")
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
    throw IllegalStateException("评论图片格式不受支持")
  }
  val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
  return BiliApi.uploadCommentImage(
    bytes = bytes,
    fileName = "comment-image.${mimeType.substringAfterLast('/', "jpg")}",
    mimeType = mimeType,
    width = bounds.outWidth,
    height = bounds.outHeight,
  )
}

/**
 * Tracks the last danmaku parameters so we can skip [PlayerView.updateDanmakuOverlay] when nothing
 * has changed across recompositions.
 */
private class DanmakuUpdateState {
  private var itemsRef: List<dev.openbili.webdemo.api.DanmakuItem>? = null
  private var maskRef: DanmakuMaskTimeline? = null
  private var enabled = false
  private var smartBlocking = false
  private var paused = false
  private var fullscreen = false
  private var highDynamicRange = false
  private var opacity = .72f
  private var displayArea = .75f
  private var densityLevel = 3
  private var fontScale = 1f
  private var speed = 1f
  private var positionEpoch = Long.MIN_VALUE

  fun changed(
    items: List<dev.openbili.webdemo.api.DanmakuItem>,
    mask: DanmakuMaskTimeline?,
    enabled: Boolean,
    smartBlocking: Boolean,
    paused: Boolean,
    fullscreen: Boolean,
    highDynamicRange: Boolean,
    opacity: Float,
    displayArea: Float,
    densityLevel: Int,
    fontScale: Float,
    speed: Float,
    positionEpoch: Long,
  ): Boolean {
    if (
      items !== itemsRef ||
        mask !== maskRef ||
        enabled != this.enabled ||
        smartBlocking != this.smartBlocking ||
        paused != this.paused ||
        fullscreen != this.fullscreen ||
        highDynamicRange != this.highDynamicRange ||
        opacity != this.opacity ||
        displayArea != this.displayArea ||
        densityLevel != this.densityLevel ||
        fontScale != this.fontScale ||
        speed != this.speed ||
        positionEpoch != this.positionEpoch
    ) {
      itemsRef = items
      maskRef = mask
      this.enabled = enabled
      this.smartBlocking = smartBlocking
      this.paused = paused
      this.fullscreen = fullscreen
      this.highDynamicRange = highDynamicRange
      this.opacity = opacity
      this.displayArea = displayArea
      this.densityLevel = densityLevel
      this.fontScale = fontScale
      this.speed = speed
      this.positionEpoch = positionEpoch
      return true
    }
    return false
  }
}

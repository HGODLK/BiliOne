package dev.openbili.webdemo.video

import android.net.Uri
import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import dev.openbili.webdemo.BuildConfig
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.PlayerSubtitleState
import dev.openbili.webdemo.WebViewConfigurator
import dev.openbili.webdemo.subtitleStateForMedia
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.BiliEmotePackage
import dev.openbili.webdemo.api.BiliHttpClient
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.CommentNavigationTarget
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.FollowingGroup
import dev.openbili.webdemo.api.MentionSuggestion
import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.api.PremiumAudioMode
import dev.openbili.webdemo.api.VideoEngagement
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.api.VideoStream
import dev.openbili.webdemo.api.remainingVideoCoins
import dev.openbili.webdemo.api.videoCoinLimit
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.PlaybackCoverRegistry
import dev.openbili.webdemo.offline.OfflineCacheChooserDialog
import dev.openbili.webdemo.offline.OfflineMediaKind
import dev.openbili.webdemo.offline.OfflineMediaManager
import dev.openbili.webdemo.offline.OfflineMediaRequest
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.ui.AvatarImage
import dev.openbili.webdemo.ui.SessionPhase
import dev.openbili.webdemo.ui.StableBoundsTracker
import dev.openbili.webdemo.ui.TransitionPreparationBarrier
import dev.openbili.webdemo.ui.TransitionPreparationResult
import dev.openbili.webdemo.ui.TransitionReadySignal
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.LocalVideoCardContentColors
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.ui.CrossfadeBackgroundImage
import dev.openbili.webdemo.ui.rememberBackgroundLuminanceProfile
import dev.openbili.webdemo.ui.navigationBringIntoViewTarget
import dev.openbili.webdemo.ui.rememberStaticBackgroundModel
import dev.openbili.webdemo.ui.videoBackgroundForeground
import dev.openbili.webdemo.ui.videoBackgroundScrim
import dev.openbili.webdemo.ui.rememberNavigationBringIntoViewRequester
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PlaybackContinuationTarget(
  val key: String,
  val title: String,
  val coverUrl: String,
  val countdownSeconds: Int,
  val onSelect: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(
  item: FeedItem,
  description: String,
  videoInfo: VideoInfo?,
  currentCid: Long,
  videoEngagement: VideoEngagement,
  favoriteFolders: List<FavoriteFolder>,
  favoriteFoldersLoading: Boolean,
  showCoverUntilFirstFrame: Boolean,
  renderedVideoId: String?,
  renderedVideoFrameCount: Int,
  playbackCoverFrameGateReady: Boolean,
  onlineViewerText: String?,
  playerState: PlayerState,
  subtitleState: PlayerSubtitleState,
  danmaku: List<DanmakuItem>,
  danmakuPaused: Boolean,
  commentItems: List<CommentItem>,
  commentTotalCount: Long,
  commentHasMore: Boolean,
  commentsLoading: Boolean,
  commentSort: CommentSort,
  commentsRefreshing: Boolean,
  pageContentLoading: Boolean,
  deferAuxiliaryContent: Boolean = false,
  deferCommentContent: Boolean = deferAuxiliaryContent,
  currentAccountMid: Long,
  currentAccountVipActive: Boolean = false,
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
  currentPositionMs: () -> Long,
  durationMs: Long,
  playerPositionProvider: () -> Long,
  isPlaying: Boolean,
  isBuffering: Boolean,
  playbackEnded: Boolean,
  playbackSpeed: Float,
  showDanmaku: Boolean,
  danmakuComposerEnabled: Boolean,
  isFullscreen: Boolean,
  isPlaybackPageForeground: Boolean = true,
  pageExitAlpha: () -> Float = { 1f },
  retainBackgroundDuringPageExit: Boolean = false,
  playerControlsVisible: Boolean,
  onPlayerControlsVisibilityChanged: (Boolean) -> Unit,
  settings: AppSettings,
  onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
  onFullscreenChanged: (Boolean) -> Unit,
  onFullscreenTransitionChanged: (Boolean) -> Unit,
  onBack: () -> Unit,
  onHome: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onTemporarySpeedChanged: (Boolean) -> Unit,
  onPlaybackSpeedChanged: (Float) -> Unit,
  onRetryPlayback: () -> Unit,
  onRetryNextQuality: () -> Unit,
  onReplay: () -> Unit,
  onSeek: (Long) -> Unit,
  onSeekPreview: (Long) -> Unit,
  onSeekCancel: () -> Unit,
  onToggleDanmaku: () -> Unit,
  onSendDanmaku: (String, Int, Int, Int, Int) -> Unit,
  onRecommendationClick: (FeedItem, Rect, Rect?, Boolean) -> Unit,
  onVideoPageSelected: (VideoPage) -> Unit,
  onCollectionEpisodeSelected: (FeedItem, Rect?) -> Unit,
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
  onPlayerBoundsChanged: (Rect) -> Unit,
  onOpenReplies: (CommentItem) -> Unit,
  onLoadMoreReplies: () -> Unit,
  onRefreshReplies: () -> Unit,
  onDismissReplies: () -> Unit,
  onCommentNavigationConsumed: () -> Unit,
  onProfileClick: (Long, String?, String?, Rect) -> Unit,
  onUploaderProfileClick: (Long, String?, String?, Rect) -> Unit,
  showUploaderFollowButton: Boolean,
  uploaderFollowed: Boolean,
  uploaderFollowBusy: Boolean,
  followingGroups: List<FollowingGroup>,
  followingGroupsLoading: Boolean,
  loggedIn: Boolean,
  premiumAudioVisible: Boolean,
  commentImageEnabled: Boolean,
  onLoadFollowingGroups: () -> Unit,
  onSelectUploaderFollowingGroup: (Long) -> Unit,
  onUnfollowUploader: () -> Unit,
  onLogin: () -> Unit,
  onCommentProfileClick: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onMentionQuery: (String) -> Unit,
  onCommentImagePreviewActiveChanged: (Boolean) -> Unit = {},
  onSwitchQuality: (Int) -> Unit,
  onSwitchPremiumAudio: (PremiumAudioMode) -> Unit,
  onSelectSubtitle: (String?) -> Unit,
  playerView: @Composable (Modifier, Float, Boolean) -> Unit,
  panelSlideProgress: () -> Float = { 1f },
  bangumiPage: BangumiPageUi? = null,
  onBangumiPosterBoundsChanged: (Rect) -> Unit = {},
  onBangumiEpisodeSelected: (dev.openbili.webdemo.api.BangumiEpisode) -> Unit = {},
  onBangumiSeasonSelected: (Long) -> Unit = {},
  onBangumiFollow: () -> Unit = {},
  onBangumiRate: (Int, String) -> Unit = { _, _ -> },
) {
  val view = LocalView.current
  val window = (view.context as? Activity)?.window
  val activity = view.context as? Activity
  val preferredDisplayModeBeforeVideo =
    remember(window) { window?.attributes?.preferredDisplayModeId ?: 0 }
  var brightnessBeforeHdr by remember(window, item.id) { mutableStateOf<Float?>(null) }

  DisposableEffect(window, item.id) {
    onDispose {
      // The video page owns the temporary HDR override only. Once this composable leaves, hand
      // brightness back to the system instead of preserving an app-level value in the next page.
      window?.let(::releaseWindowBrightnessOverride)
      // MainActivity normally asks for the panel's highest refresh mode. HDR SurfaceView shares
      // the display compositor with the UI layer, so returning to that mode after the video page
      // is essential once this page has temporarily requested a video-friendly cadence.
      window?.let { restorePreferredDisplayMode(it, preferredDisplayModeBeforeVideo) }
    }
  }

  DisposableEffect(view, isPlaybackPageForeground, settings.keepScreenOn) {
    val previous = view.keepScreenOn
    view.keepScreenOn = settings.keepScreenOn && isPlaybackPageForeground
    onDispose { view.keepScreenOn = previous }
  }

  DisposableEffect(window, isFullscreen) {
    if (!isFullscreen || window == null || activity == null) return@DisposableEffect onDispose {}
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
    onDispose {
      WindowCompat.setDecorFitsSystemWindows(window, false)
      WindowInsetsControllerCompat(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
      }
    }
  }

  var showVideoInfo by remember(item.id) { mutableStateOf(false) }
  var showVideoSelection by remember(item.id) { mutableStateOf(false) }
  var showBangumiEpisodeSelection by remember { mutableStateOf(false) }
  var showBangumiInfo by remember(bangumiPage?.sourceCard?.id) { mutableStateOf(false) }
  var showOfflineCacheChooser by remember(item.id) { mutableStateOf(false) }
  var resumeAfterBangumiInfo by
    remember(bangumiPage?.sourceCard?.id) { mutableStateOf(false) }
  val collectionEpisodes = videoInfo?.collection?.episodes.orEmpty()
  val currentCollectionIndex = collectionEpisodes.indexOfFirst { it.bvid == videoInfo?.bvid }
  val nextCollectionEpisode =
    collectionEpisodes.getOrNull(currentCollectionIndex + 1).takeIf { currentCollectionIndex >= 0 }
  val currentPageIndex = videoInfo?.pages?.indexOfFirst { it.cid == currentCid } ?: -1
  val nextVideoPage =
    if (videoInfo?.collection == null && currentPageIndex >= 0)
      videoInfo?.pages?.getOrNull(currentPageIndex + 1)
    else null
  val nextBangumiEpisode = bangumiPage?.nextPlayableEpisode()
  val cachePlayData = (playerState as? PlayerState.Ready)?.playData
  val cacheStreams =
    cachePlayData?.let { data ->
      (listOfNotNull(data.streams.getOrNull(data.currentStreamIndex)) + data.streams)
        .distinctBy(VideoStream::id)
    }.orEmpty()
  val offlineMediaManager = remember(view.context) { OfflineMediaManager.get(view.context) }
  val cacheTargets =
    remember(videoInfo, bangumiPage, currentAccountMid, currentCid) {
      if (bangumiPage != null) {
        val season = bangumiPage.season
        bangumiPage.playableEpisodes().mapIndexed { index, episode ->
          OfflineMediaRequest(
            kind = OfflineMediaKind.BANGUMI,
            accountMid = currentAccountMid,
            title = season?.title ?: bangumiPage.sourceCard.title,
            partTitle = episode.displayTitle(bangumiPage.sourceCard.kind),
            coverUrl =
              episode.coverUrl.ifBlank {
                season?.coverUrl?.takeIf(String::isNotBlank) ?: bangumiPage.sourceCard.coverUrl
              },
            bvid = episode.bvid,
            aid = episode.aid,
            cid = episode.cid,
            pageNumber = index + 1,
            durationMs = episode.durationSeconds * 1_000L,
            seasonId = season?.seasonId ?: 0L,
            episodeId = episode.id,
            qualityId = 0,
            requiresVip = true,
          )
        }
      } else {
        videoInfo?.let { info ->
          val collection = info.collection
          if (collection != null && collection.episodes.isNotEmpty()) {
            collection.episodes.mapIndexed { index, episode ->
              OfflineMediaRequest(
                kind = OfflineMediaKind.VIDEO,
                accountMid = currentAccountMid,
                title = collection.title.ifBlank { info.title },
                partTitle = episode.title,
                coverUrl = episode.coverUrl.ifBlank { info.coverUrl.ifBlank { item.coverUrl } },
                bvid = episode.bvid,
                aid = episode.aid,
                cid = episode.cid,
                pageNumber = index + 1,
                durationMs = episode.durationSeconds * 1_000L,
                collectionId = collection.id,
                qualityId = 0,
              )
            }
          } else {
            info.pages.ifEmpty {
              listOf(
                VideoPage(
                  page = 1,
                  cid = info.cid,
                  part = info.title,
                  durationSeconds = info.durationSeconds,
                )
              )
            }.map { page ->
              OfflineMediaRequest(
                kind = OfflineMediaKind.VIDEO,
                accountMid = currentAccountMid,
                title = info.title,
                partTitle = page.part,
                coverUrl = info.coverUrl.ifBlank { item.coverUrl },
                bvid = info.bvid,
                aid = info.aid,
                cid = page.cid,
                pageNumber = page.page,
                durationMs = page.durationSeconds * 1_000L,
                qualityId = 0,
              )
            }
          }
        }.orEmpty()
      }
    }
  val existingOfflineTargetIds =
    remember(showOfflineCacheChooser, cacheTargets) {
      if (showOfflineCacheChooser) offlineMediaManager.entries().mapTo(mutableSetOf()) { it.id }
      else emptySet()
    }
  val nextPlaybackTarget =
    when {
      nextBangumiEpisode != null ->
        PlaybackContinuationTarget(
          key = "bangumi:${nextBangumiEpisode.id}",
          title = nextBangumiEpisode.displayTitle(bangumiPage.sourceCard.kind),
          coverUrl =
            nextBangumiEpisode.coverUrl.takeIf(String::isNotBlank)
              ?: bangumiPage.season?.coverUrl?.takeIf(String::isNotBlank)
              ?: bangumiPage.sourceCard.coverUrl,
          countdownSeconds = 10,
          onSelect = { onBangumiEpisodeSelected(nextBangumiEpisode) },
        )
      nextCollectionEpisode != null ->
        PlaybackContinuationTarget(
          key = "collection:${nextCollectionEpisode.bvid}",
          title = nextCollectionEpisode.title,
          coverUrl = nextCollectionEpisode.coverUrl.ifBlank { item.coverUrl },
          countdownSeconds = 5,
          onSelect = {
            onCollectionEpisodeSelected(feedItemFromCollectionEpisode(nextCollectionEpisode), null)
          },
        )
      nextVideoPage != null ->
        PlaybackContinuationTarget(
          key = "page:${item.id}:${nextVideoPage.cid}",
          title = nextVideoPage.part,
          coverUrl = item.coverUrl,
          countdownSeconds = 5,
          onSelect = { onVideoPageSelected(nextVideoPage) },
        )
      else -> null
    }
  val playbackEndReveal = remember(item.id) { Animatable(if (playbackEnded) 1f else 0f) }
  LaunchedEffect(playbackEnded) {
    if (playbackEnded) {
      PlaybackCoverRegistry.requestRetention(item.coverUrl)
      if (playbackEndReveal.value < .999f) {
        withFrameNanos {}
        playbackEndReveal.animateTo(
          1f,
          tween(
            if (nextPlaybackTarget != null) 280 else 300,
            easing = FastOutSlowInEasing,
          ),
        )
      }
    } else {
      playbackEndReveal.snapTo(0f)
    }
  }
  var controlsVisible by remember { mutableStateOf(true) }
  var controlsMenuOpen by remember { mutableStateOf(false) }
  var fullscreenProgressScrubbing by remember { mutableStateOf(false) }
  var fullscreenControlsLocked by remember { mutableStateOf(false) }
  var embeddedDanmakuComposerVisible by remember { mutableStateOf(false) }
  var embeddedProgressScrubbing by remember { mutableStateOf(false) }
  var gestureFeedback by remember { mutableStateOf<GestureIndicator?>(null) }
  var gestureFeedbackVisible by remember { mutableStateOf(false) }
  var gestureFeedbackVersion by remember { mutableIntStateOf(0) }
  var gestureSeekPreviewMs by remember { mutableStateOf<Long?>(null) }
  var showDanmakuComposer by remember(item.id) { mutableStateOf(false) }
  LaunchedEffect(danmakuComposerEnabled) {
    if (!danmakuComposerEnabled) showDanmakuComposer = false
  }
  val currentSubtitleState =
    subtitleStateForMedia(
      state = subtitleState,
      mediaId = item.id,
      cid = currentCid,
      bvid = videoInfo?.bvid,
      aid = videoInfo?.aid ?: 0L,
    )
  val recommendationScrollStates = remember { mutableMapOf<String, LazyListState>() }
  val commentScrollStates = remember { mutableMapOf<String, LazyListState>() }
  LaunchedEffect(controlsVisible) {
    onPlayerControlsVisibilityChanged(controlsVisible)
  }
  val commentChromeStates = remember { mutableMapOf<String, CommentChromeState>() }
  var commentNavigationSessionId by remember(item.id) { mutableStateOf(0L) }
  LaunchedEffect(commentNavigationTarget?.requestId) {
    commentNavigationTarget?.requestId?.let { commentNavigationSessionId = it }
  }
  val recommendationListState =
    remember(item.id) { recommendationScrollStates.getOrPut(item.id) { LazyListState() } }
  val commentScrollStateKey = "${item.id}:commentNavigation:$commentNavigationSessionId"
  val commentListState =
    remember(commentScrollStateKey) {
      commentScrollStates.getOrPut(commentScrollStateKey) { LazyListState() }
    }
  val commentChromeState =
    remember(item.id) { commentChromeStates.getOrPut(item.id) { CommentChromeState() } }
  val avatarPrefetchContext = LocalContext.current.applicationContext
  LaunchedEffect(item.id, commentItems, replyItems) {
    snapshotFlow {
        val visibleCommentIndices =
          commentListState.layoutInfo.visibleItemsInfo
            .map { it.index }
            .filter { it in commentItems.indices }
        visibleCommentIndices.minOrNull() to visibleCommentIndices.maxOrNull()
      }
      .distinctUntilChanged()
      .collectLatest { (firstVisibleIndex, lastVisibleIndex) ->
        delay(PALETTE_PREFETCH_DEBOUNCE_MS)
        val window =
          commentViewportWindow(
            totalCount = commentItems.size,
            firstVisibleIndex = firstVisibleIndex ?: 0,
            lastVisibleIndex = lastVisibleIndex ?: -1,
          )
        val nearbyComments = window?.let { commentItems.slice(it) }.orEmpty()
        CommentAvatarPaletteCache.retainOnly(nearbyComments.mapTo(mutableSetOf()) { it.face })
        prefetchCommentAvatarPalettes(avatarPrefetchContext, nearbyComments)
      }
  }
  val recommendationReturnBounds = remember(item.id) { mutableMapOf<String, Rect>() }
  var retainedPlayData by remember { mutableStateOf<PlayUrlData?>(null) }
  var embeddedPlayerBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  LaunchedEffect(playerState) {
    if (playerState is PlayerState.Ready) retainedPlayData = playerState.playData
  }
  val activePlayData = (playerState as? PlayerState.Ready)?.playData ?: retainedPlayData
  val isHdrPlayback =
    activePlayData?.streams?.getOrNull(activePlayData.currentStreamIndex)?.id in setOf(125, 126)
  var hdrBrightnessSuppressed by remember(item.id) { mutableStateOf(false) }
  // Navigating to a profile deliberately releases this page's temporary HDR window override, but
  // the video screen stays composed beneath that profile. Regain ownership when the same video
  // page becomes visible again; otherwise the retained suppression flag keeps both brightness and
  // the HDR focus mask disabled forever after a profile round trip.
  LaunchedEffect(isHdrPlayback, isPlaybackPageForeground) {
    if (isHdrPlayback && isPlaybackPageForeground) hdrBrightnessSuppressed = false
  }
  LaunchedEffect(
    window,
    activity,
    isHdrPlayback,
    isPlaybackPageForeground,
    preferredDisplayModeBeforeVideo,
  ) {
    val playerWindow = window ?: return@LaunchedEffect
    val preferredModeId =
      if (isHdrPlayback && isPlaybackPageForeground)
        hdrPreferredDisplayModeId(activity) ?: preferredDisplayModeBeforeVideo
      else preferredDisplayModeBeforeVideo
    restorePreferredDisplayMode(playerWindow, preferredModeId)
  }
  // Releasing the HDR lock as soon as playback reaches END keeps the end-card from leaving the
  // display at maximum brightness. Switching to a new HDR item will take the lock again.
  val forceHdrBrightness =
    isHdrPlayback &&
      isPlaybackPageForeground &&
      !playbackEnded &&
      !hdrBrightnessSuppressed &&
      !showCoverUntilFirstFrame
  val dimNonPlayerContent =
    isHdrPlayback && isPlaybackPageForeground && !hdrBrightnessSuppressed
  LaunchedEffect(window, forceHdrBrightness) {
    val playerWindow = window ?: return@LaunchedEffect
    if (forceHdrBrightness) {
      val currentBrightness = playerWindow.attributes.screenBrightness
      if (brightnessBeforeHdr == null) brightnessBeforeHdr = currentBrightness
      if (gestureFeedback?.kind == GestureIndicatorKind.BRIGHTNESS) {
        gestureFeedbackVisible = false
        gestureFeedback = null
      }
      animateWindowBrightness(
        window = playerWindow,
        from = resolveWindowBrightness(view.context, currentBrightness),
        to = 1f,
        durationMs = HDR_BRIGHTNESS_RAMP_MS,
      )
      setWindowBrightness(playerWindow, 1f)
    } else {
      val originalBrightness = brightnessBeforeHdr ?: return@LaunchedEffect
      animateWindowBrightness(
        window = playerWindow,
        from =
          resolveWindowBrightness(
            view.context,
            playerWindow.attributes.screenBrightness,
          ),
        to = resolveWindowBrightness(view.context, originalBrightness),
        durationMs = HDR_BRIGHTNESS_RESTORE_MS,
      )
      val attributes = playerWindow.attributes
      attributes.screenBrightness = originalBrightness
      playerWindow.attributes = attributes
      brightnessBeforeHdr = null
    }
  }
  fun leaveHdrPlaybackPage() {
    if (!isHdrPlayback) return
    hdrBrightnessSuppressed = true
    window?.let(::releaseWindowBrightnessOverride)
    brightnessBeforeHdr = null
    window?.let { restorePreferredDisplayMode(it, preferredDisplayModeBeforeVideo) }
  }
  fun openRecommendation(
    recommendation: FeedItem,
    bounds: Rect,
    returnBounds: Rect?,
    fromPlaybackEnd: Boolean,
  ) {
    // The previous detail page remains composed underneath the incoming recommendation during the
    // card transition. Relinquish its HDR ownership before navigation so it cannot pin brightness.
    leaveHdrPlaybackPage()
    onRecommendationClick(recommendation, bounds, returnBounds, fromPlaybackEnd)
  }
  val hdrFocusOverlayAlpha by
    animateFloatAsState(
      targetValue = if (dimNonPlayerContent && !playbackEnded) .48f else 0f,
      animationSpec = tween(380, easing = FastOutSlowInEasing),
      label = "hdrFocusOverlayAlpha",
    )
  // In-place part/episode selection replaces item but not the video page. Keeping this state
  // outside that item key prevents a completed fullscreen layout from briefly rebuilding through
  // the embedded layout before the replacement media is ready.
  val fullscreenProgress = remember { Animatable(0f) }
  var fullscreenTransitionBusy by remember { mutableStateOf(false) }
  var fullscreenLayerVisible by remember { mutableStateOf(false) }
  var frozenEmbeddedPlayerBounds by remember { mutableStateOf(Rect.Zero) }
  var lastValidEmbeddedPlayerBounds by remember { mutableStateOf(Rect.Zero) }
  var trackEmbeddedPlayerBounds by remember { mutableStateOf(true) }
  var embeddedGestureResetKey by remember { mutableIntStateOf(0) }
  var fullscreenForegroundBounds by remember { mutableStateOf(Rect.Zero) }
  var videoScreenBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  val pageBackgroundLayer = rememberGraphicsLayer()
  var pageBackgroundBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  var commentImagePreview by remember(item.id) {
    mutableStateOf<CommentImagePreviewSession?>(null)
  }
  var commentImagePreviewJob by remember(item.id) {
    mutableStateOf<kotlinx.coroutines.Job?>(null)
  }
  val embeddedPlayerHandoffAlpha =
    remember {
      { (1f - fullscreenProgress.value).coerceIn(0f, 1f) }
    }
  val transitionScope = rememberCoroutineScope()
  val transitionDuration = if (settings.reduceMotion) 100 else 360
  fun openCommentImagePreview(image: CommentImage, bounds: Rect) {
    if (bounds.width <= 0f || bounds.height <= 0f || commentImagePreview != null) return
    val session = CommentImagePreviewSession(image, bounds)
    commentImagePreview = session
    commentImagePreviewJob?.cancel()
    commentImagePreviewJob = transitionScope.launch {
      session.progress.snapTo(0f)
      val preparationResult = session.preparation.await()
      if (
        preparationResult == TransitionPreparationResult.CANCELLED ||
          commentImagePreview !== session
      ) {
        return@launch
      }
      session.preparationTimedOut = preparationResult == TransitionPreparationResult.TIMED_OUT
      session.phase = SessionPhase.READY
      withFrameNanos {}
      session.phase = SessionPhase.FLYING
      session.progress.animateTo(
        1f,
        tween(if (settings.reduceMotion) 110 else 380, easing = FastOutSlowInEasing),
      )
      session.phase = SessionPhase.COMPLETED
    }
  }
  fun closeCommentImagePreview() {
    val session = commentImagePreview ?: return
    commentImagePreviewJob?.cancel()
    session.preparation.cancel()
    session.phase = SessionPhase.CANCELLED
    commentImagePreviewJob = transitionScope.launch {
      session.progress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 90 else 320, easing = FastOutSlowInEasing),
      )
      kotlinx.coroutines.delay(16)
      if (commentImagePreview === session) commentImagePreview = null
    }
  }
  DisposableEffect(item.id) {
    onDispose {
      commentImagePreviewJob?.cancel()
      commentImagePreview?.preparation?.cancel()
      // If the page is torn down while the preview is still open, clear the suppression flag so
      // a later video does not start with danmaku hidden.
      onCommentImagePreviewActiveChanged(false)
    }
  }
  LaunchedEffect(commentImagePreview != null) {
    onCommentImagePreviewActiveChanged(commentImagePreview != null)
  }
  val autoNextKey = nextPlaybackTarget?.key
  var autoNextSeconds by
    remember(item.id, currentCid, autoNextKey, settings.autoNextCountdownSeconds) {
      mutableIntStateOf(settings.autoNextCountdownSeconds)
    }
  var autoNextTriggered by remember(item.id, currentCid, autoNextKey) { mutableStateOf(false) }
  val autoNextHandoff = remember(item.id, currentCid, autoNextKey) { Animatable(0f) }
  val playbackEndRevealAlpha = remember(item.id) { { playbackEndReveal.value } }
  val autoNextHandoffProgress =
    remember(item.id, currentCid, autoNextKey) { { autoNextHandoff.value } }
  fun embeddedRecommendationReturnBounds(fullscreenCardBounds: Rect): Rect? {
    val fullscreenBounds = fullscreenForegroundBounds
    val embeddedBounds = embeddedPlayerBounds
    if (
      fullscreenCardBounds == Rect.Zero ||
        fullscreenBounds.width <= 0f ||
        fullscreenBounds.height <= 0f ||
        embeddedBounds.width <= 0f ||
        embeddedBounds.height <= 0f
    ) {
      return null
    }
    val offsetX = embeddedBounds.center.x - fullscreenBounds.center.x
    val offsetY = embeddedBounds.center.y - fullscreenBounds.center.y
    return Rect(
      left = fullscreenCardBounds.left + offsetX,
      top = fullscreenCardBounds.top + offsetY,
      right = fullscreenCardBounds.right + offsetX,
      bottom = fullscreenCardBounds.bottom + offsetY,
    )
  }
  fun triggerAutoNext() {
    if (autoNextTriggered || autoNextKey == null || !playbackEnded) return
    autoNextTriggered = true
    transitionScope.launch {
      autoNextHandoff.animateTo(
        1f,
        tween(260, easing = FastOutSlowInEasing),
      )
      nextPlaybackTarget.onSelect()
    }
  }
  LaunchedEffect(
    playbackEnded,
    autoNextKey,
    settings.autoPlayNext,
    settings.autoNextCountdownSeconds,
  ) {
    autoNextSeconds = settings.autoNextCountdownSeconds
    autoNextTriggered = false
    autoNextHandoff.snapTo(0f)
    if (!playbackEnded || autoNextKey == null || !settings.autoPlayNext) return@LaunchedEffect
    while (autoNextSeconds > 0 && !autoNextTriggered) {
      delay(1_000)
      if (!autoNextTriggered) autoNextSeconds -= 1
    }
    if (!autoNextTriggered) triggerAutoNext()
  }
  fun freezeEmbeddedPlayerBounds() {
    if (fullscreenLayerVisible) return
    val candidate = embeddedPlayerBounds.takeIf { it.width > 0f && it.height > 0f }
      ?: lastValidEmbeddedPlayerBounds.takeIf { it.width > 0f && it.height > 0f }
      ?: return
    frozenEmbeddedPlayerBounds = candidate
    trackEmbeddedPlayerBounds = false
  }
  fun enterFullscreenAnimated() {
    if (fullscreenTransitionBusy || fullscreenLayerVisible) return
    // The end backdrop is rendered inside the same floating player boundary. Keep its decoded
    // cover strongly retained before the second layout instance is composed, so the moving blur
    // never starts a new image request during the fullscreen handoff.
    if (playbackEnded) PlaybackCoverRegistry.requestRetention(item.coverUrl)
    freezeEmbeddedPlayerBounds()
    if (frozenEmbeddedPlayerBounds.width <= 0f || frozenEmbeddedPlayerBounds.height <= 0f) return
    fullscreenLayerVisible = true
    fullscreenTransitionBusy = true
    onFullscreenTransitionChanged(true)
    onFullscreenChanged(true)
    transitionScope.launch {
      // Let the host hide the independent danmaku Surface before the boundary starts moving.
      withFrameNanos {}
      fullscreenProgress.animateTo(
        1f,
        tween(transitionDuration, easing = FastOutSlowInEasing),
      )
      fullscreenTransitionBusy = false
      onFullscreenTransitionChanged(false)
    }
  }
  fun exitFullscreenAnimated() {
    if (fullscreenTransitionBusy || !fullscreenLayerVisible) return
    showDanmakuComposer = false
    fullscreenControlsLocked = false
    fullscreenTransitionBusy = true
    onFullscreenTransitionChanged(true)
    onFullscreenChanged(false)
    transitionScope.launch {
      showVideoInfo = false
      // SurfaceView visibility is applied during traversal; animate only on the following frame.
      withFrameNanos {}
      fullscreenProgress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
      )
      fullscreenLayerVisible = false
      trackEmbeddedPlayerBounds = true
      // The fullscreen layer owns the pointer stream until it is removed. Recreate the window
      // gesture gate after that handoff so a completed fullscreen gesture cannot keep consuming
      // the next two-finger gesture on a child page.
      embeddedGestureResetKey += 1
      fullscreenTransitionBusy = false
      onFullscreenTransitionChanged(false)
    }
  }
  BackHandler(enabled = isFullscreen || fullscreenLayerVisible) {
    if (!fullscreenTransitionBusy) exitFullscreenAnimated()
  }
  LaunchedEffect(isFullscreen) {
    if (isFullscreen && !fullscreenLayerVisible && !fullscreenTransitionBusy) {
      enterFullscreenAnimated()
    } else if (!isFullscreen && fullscreenLayerVisible && !fullscreenTransitionBusy) {
      exitFullscreenAnimated()
    }
  }
  DisposableEffect(item.id) {
    onDispose {
      onFullscreenTransitionChanged(false)
    }
  }
  LaunchedEffect(gestureFeedbackVersion, gestureFeedback) {
    val version = gestureFeedbackVersion
    val displayed = gestureFeedback ?: return@LaunchedEffect
    delay(800)
    if (gestureFeedbackVersion != version || gestureFeedback != displayed) return@LaunchedEffect
    gestureFeedbackVisible = false
    delay(GESTURE_INDICATOR_FADE_OUT_MS.toLong())
    if (gestureFeedbackVersion == version && gestureFeedback == displayed) gestureFeedback = null
  }
  val controlsHeldVisible =
    controlsMenuOpen ||
      showDanmakuComposer ||
      embeddedDanmakuComposerVisible ||
      fullscreenProgressScrubbing ||
      embeddedProgressScrubbing ||
      gestureSeekPreviewMs != null
  LaunchedEffect(
    controlsVisible,
    controlsHeldVisible,
    isPlaying,
    fullscreenControlsLocked,
    settings.controlsTimeoutSeconds,
  ) {
    if (controlsVisible && isPlaying && !controlsHeldVisible) {
      delay(settings.controlsTimeoutSeconds * 1000L)
      controlsVisible = false
    }
  }

  // The fullscreen player reaches the actual window edges. Keep the rounded-corner interpolation
  // below unchanged for this first true-edge fullscreen trial.
  val fullscreenInsetPx = 0
  val floatingSourceBounds =
    if (fullscreenLayerVisible) frozenEmbeddedPlayerBounds else embeddedPlayerBounds
  if (fullscreenLayerVisible) {
    Box(
      Modifier.fillMaxSize()
        .zIndex(80f)
        .graphicsLayer { alpha = fullscreenProgress.value.coerceIn(0f, 1f) }
        .background(Color.Black)
    )
  }
  if (
    playerState is PlayerState.Ready &&
      floatingSourceBounds.width > 0f &&
      floatingSourceBounds.height > 0f
  ) {
    val progress = fullscreenProgress.value.coerceIn(0f, 1f)
    val animatedShape = RoundedCornerShape(VideoShapeTokens.CornerRadius * (1f - progress))
    Box(
      Modifier.fillMaxSize()
        .floatingPlayerLayout(
          progress = progress,
          sourceBounds = floatingSourceBounds,
          targetInsetPx = fullscreenInsetPx,
        )
        .zIndex(if (fullscreenLayerVisible) 90f else 0f)
        .graphicsLayer {
          shape = animatedShape
          clip = true
        }
    ) {
      val embeddedLetterboxShadeAlpha = if (bangumiPage != null) .74f else .36f
      val letterboxShadeAlpha =
        fullscreenVideoBackgroundShadeAlpha(
          embeddedShadeAlpha = embeddedLetterboxShadeAlpha,
          fullscreenBrightness = settings.fullscreenBackgroundBrightness,
          fullscreenProgress = progress,
        )
      VideoCardGradient(
        coverUrl = item.coverUrl,
        modifier = Modifier.fillMaxSize(),
        loadKey = "player-background:${item.id}",
      ) {
        Box(Modifier.fillMaxSize()) {
          // Only shade the cover-derived letterbox. The SurfaceView is placed above this layer,
          // so the decoded video keeps its original luminance and HDR output.
          Box(
            Modifier.matchParentSize().background(Color.Black.copy(alpha = letterboxShadeAlpha))
          )
          playerView(Modifier.fillMaxSize(), progress, fullscreenLayerVisible)
        }
      }
    }
  }

  if (fullscreenLayerVisible) {
    Box(Modifier.fillMaxSize().clipToBounds().zIndex(100f)) {
      Box(
        Modifier.fillMaxSize()
          .floatingPlayerLayout(
            progress = fullscreenProgress.value,
            sourceBounds = frozenEmbeddedPlayerBounds,
            targetInsetPx = fullscreenInsetPx,
          )
          .graphicsLayer {
            val progress = fullscreenProgress.value.coerceIn(0f, 1f)
            shape = RoundedCornerShape(VideoShapeTokens.CornerRadius * (1f - progress))
            clip = true
            alpha = progress.coerceIn(0f, 1f)
          }
          .background(Color.Transparent)
      ) {
        if (playerState is PlayerState.Ready) {
          PlayerGestureLayer(
              enabledBrightness =
                !fullscreenControlsLocked && settings.brightnessGesture && !isHdrPlayback,
              enabledVolume = !fullscreenControlsLocked && settings.volumeGesture,
              enabledSeek = !fullscreenControlsLocked && settings.horizontalSeekGesture,
              enabledFullscreenToggle =
                !fullscreenControlsLocked && settings.twoFingerFullscreenGesture,
              enabledTwoFingerSeek =
                !fullscreenControlsLocked && settings.twoFingerSeekGesture,
              enabledDoubleTap = !fullscreenControlsLocked,
              enabledTemporarySpeed = !fullscreenControlsLocked,
              positionProvider = playerPositionProvider,
              durationMs = durationMs,
              onSeek = onSeek,
              onIndicator = {
                gestureFeedback = it
                gestureFeedbackVisible = true
                gestureFeedbackVersion += 1
              },
              onSeekPreview = {
                gestureSeekPreviewMs = it
                if (it != null) {
                  onSeekPreview(it)
                  gestureFeedbackVisible = false
                  gestureFeedback = null
                  controlsVisible = true
                }
              },
              onSeekCancel = onSeekCancel,
              onToggleControls = { controlsVisible = !controlsVisible },
              onDoubleTap = { if (!fullscreenControlsLocked) onTogglePlayPause() },
              onTemporarySpeedChanged = { active ->
                if (!active || !fullscreenControlsLocked) onTemporarySpeedChanged(active)
              },
              isFullscreen = true,
              onFullscreenChanged = { enter -> if (!enter) exitFullscreenAnimated() },
              seekEdgeInset = 40.dp,
              modifier = Modifier.fillMaxSize().zIndex(1.5f),
            )
        }
        if (isBuffering) {
          CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center).size(38.dp).zIndex(4f),
            strokeWidth = 3.dp,
            color = Color.White,
          )
        }
        if (playerState is PlayerState.Error) {
          PlayerErrorActions(
            error = playerState,
            onRetry = onRetryPlayback,
            onRetryNextQuality = onRetryNextQuality,
            fullscreen = true,
            modifier = Modifier.align(Alignment.Center).zIndex(6f),
          )
        }
        if (
          !isPlaying &&
            !isBuffering &&
            !playbackEnded &&
            !fullscreenControlsLocked &&
            playerState is PlayerState.Ready &&
            fullscreenLayerVisible
        ) {
          PlayerCenterPlayPauseButton(
            isPlaying = false,
            onPlayPause = onTogglePlayPause,
            modifier = Modifier.align(Alignment.Center).zIndex(3.5f),
          )
        }
        FadingVisibility(
          visible =
            controlsVisible && playerState is PlayerState.Ready,
          modifier = Modifier.fillMaxSize().zIndex(3f),
        ) {
          val readyState = playerState as? PlayerState.Ready
          if (readyState != null && !fullscreenControlsLocked)
            ModernPlayerControls(
              playData = readyState.playData,
              premiumAudioVisible = premiumAudioVisible,
              showDanmaku = showDanmaku,
              danmakuSmartBlocking = settings.danmakuSmartBlocking,
              isFullscreen = true,
              isPlaying = isPlaying,
              showCenterAction = isPlaying,
              currentPositionMs = { gestureSeekPreviewMs ?: currentPositionMs() },
              durationMs = durationMs,
              onPlayPause = onTogglePlayPause,
              onSeek = onSeek,
              onSeekPreview = onSeekPreview,
              onSeekCancel = onSeekCancel,
              onFullscreen = ::exitFullscreenAnimated,
              onToggleDanmaku = onToggleDanmaku,
              onDanmakuSmartBlockingChange = { value ->
                onSettingsChange { it.copy(danmakuSmartBlocking = value) }
              },
              danmakuComposerEnabled = danmakuComposerEnabled,
              onComposeDanmaku = {
                showDanmakuComposer = !showDanmakuComposer
                if (showDanmakuComposer) controlsVisible = true
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
              onMenuVisibilityChanged = { controlsMenuOpen = it },
              onProgressScrubChanged = { active ->
                fullscreenProgressScrubbing = active
                if (active) controlsVisible = true
              },
              onSwitchQuality = onSwitchQuality,
              onSwitchPremiumAudio = onSwitchPremiumAudio,
              subtitleState = currentSubtitleState,
              onSelectSubtitle = onSelectSubtitle,
              subtitleStyle = settings.subtitleStyle,
              onSubtitleStyleChange = { style ->
                onSettingsChange { it.copy(subtitleStyle = style) }
              },
              modifier = Modifier.fillMaxSize(),
              fullscreenTitle =
                bangumiPage?.currentEpisodeTitle()?.takeIf(String::isNotBlank) ?: item.title,
              onlineViewerText = onlineViewerText,
              onOpenSelection =
                when {
                  (bangumiPage?.playableEpisodes()?.size ?: 0) > 1 -> {
                    { showBangumiEpisodeSelection = true }
                  }
                  (videoInfo?.collection?.episodes?.size ?: 0) > 1 ||
                    (videoInfo?.pages?.size ?: 0) > 1 -> {
                    { showVideoSelection = true }
                  }
                  else -> null
                },
            )
        }
        FadingVisibility(
          visible =
            controlsVisible && playerState is PlayerState.Ready && !playbackEnded,
          modifier = Modifier.fillMaxSize().zIndex(4.5f),
        ) {
          Box(Modifier.fillMaxSize()) {
            FullscreenLockButton(
              locked = fullscreenControlsLocked,
              onClick = {
                fullscreenControlsLocked = !fullscreenControlsLocked
                controlsMenuOpen = false
                showDanmakuComposer = false
                gestureSeekPreviewMs = null
                gestureFeedbackVisible = false
                onTemporarySpeedChanged(false)
                controlsVisible = true
              },
              modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp),
            )
            FullscreenLockButton(
              locked = fullscreenControlsLocked,
              onClick = {
                fullscreenControlsLocked = !fullscreenControlsLocked
                controlsMenuOpen = false
                showDanmakuComposer = false
                gestureSeekPreviewMs = null
                gestureFeedbackVisible = false
                onTemporarySpeedChanged(false)
                controlsVisible = true
              },
              modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp),
            )
          }
        }
        AnimatedVisibility(
          visible = gestureFeedbackVisible,
          modifier =
            Modifier.align(
                when (gestureFeedback?.kind) {
                  GestureIndicatorKind.BRIGHTNESS -> Alignment.CenterStart
                  GestureIndicatorKind.VOLUME -> Alignment.CenterEnd
                  else -> Alignment.Center
                }
              )
              .padding(horizontal = 28.dp)
              .zIndex(4f),
          enter = fadeIn(tween(GESTURE_INDICATOR_FADE_IN_MS)),
          exit = fadeOut(tween(GESTURE_INDICATOR_FADE_OUT_MS)),
        ) {
          gestureFeedback?.let { indicator ->
            GestureIndicatorOverlay(indicator = indicator)
          }
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
              onSettingsChange { it.copy(danmakuColor = color, danmakuColorful = colorful) }
            },
            imeBaselineBottomPadding = 92.dp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp).zIndex(5f),
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
              isFullscreen = true,
              onFullscreen = ::exitFullscreenAnimated,
              onNext = ::triggerAutoNext,
              onReplay = onReplay,
              modifier = Modifier.fillMaxSize().zIndex(7f),
            )
          } else {
            PlaybackEndedRecommendations(
              coverUrl = item.coverUrl,
              recommendations = recommendations,
              hiddenCoverItemId = hiddenPlaybackEndRecommendationCoverItemId,
              revealAlpha = playbackEndRevealAlpha,
              showForeground = false,
              isFullscreen = true,
              onFullscreen = ::exitFullscreenAnimated,
              onReplay = onReplay,
              onRecommendationClick = { recommendation, bounds ->
                openRecommendation(
                  recommendation,
                  bounds,
                  embeddedRecommendationReturnBounds(bounds),
                  true,
                )
              },
              onRecommendationLongClick = onRecommendationLongClick,
              modifier = Modifier.fillMaxSize().zIndex(7f),
            )
          }
        }
      }
      if (playbackEnded && nextPlaybackTarget == null) {
        PlaybackEndedForeground(
          recommendations = recommendations,
          hiddenCoverItemId = hiddenPlaybackEndRecommendationCoverItemId,
          onReplay = onReplay,
          onRecommendationClick = { recommendation, bounds ->
            openRecommendation(
              recommendation,
              bounds,
              embeddedRecommendationReturnBounds(bounds),
              true,
            )
          },
          onRecommendationLongClick = onRecommendationLongClick,
          modifier =
            Modifier.fillMaxSize()
              .onGloballyPositioned { fullscreenForegroundBounds = it.boundsInRoot() }
              .graphicsLayer {
                val progress = fullscreenProgress.value.coerceIn(0f, 1f)
                val source = embeddedPlayerBounds
                if (source.width > 0f && source.height > 0f) {
                  translationX = (source.center.x - size.width / 2f) * (1f - progress)
                  translationY = (source.center.y - size.height / 2f) * (1f - progress)
                }
                alpha = playbackEndReveal.value
              }
              .zIndex(8f),
        )
      }
    }
  }

  val videoPageBackground = MaterialTheme.colorScheme.background
  val hasCustomPageBackground = settings.videoBackgroundUri.isNotBlank()
  val currentPlaybackCover =
    resolvePlaybackCoverUrl(
      currentEpisodeCoverUrl = bangumiPage?.currentEpisodeCoverUrl().orEmpty(),
      videoInfo = videoInfo,
      currentCid = currentCid,
      fallbackItemCoverUrl = item.coverUrl,
    )
  // Keep the last presented artwork for the lifetime of the playback screen. Recommendation,
  // collection, part and back-stack switches replace [item] in the same slot; keying this state
  // by media id cleared the old background before the new renderer had produced a frame.
  var committedPlaybackCoverBackground by remember { mutableStateOf("") }
  LaunchedEffect(
    hasCustomPageBackground,
    settings.useVideoCoverBackground,
    playbackCoverFrameGateReady,
    renderedVideoId,
    renderedVideoFrameCount,
    currentPlaybackCover,
  ) {
    when {
      hasCustomPageBackground || !settings.useVideoCoverBackground ->
        committedPlaybackCoverBackground = ""
      playbackCoverFrameGateReady &&
        renderedVideoId == item.id &&
        renderedVideoFrameCount >= 3 &&
        currentPlaybackCover.isNotBlank() ->
        committedPlaybackCoverBackground = currentPlaybackCover
    }
  }
  val usePlaybackCoverBackground =
    !hasCustomPageBackground &&
      settings.useVideoCoverBackground &&
      committedPlaybackCoverBackground.isNotBlank()
  val effectivePageBackgroundSource =
    if (hasCustomPageBackground) settings.videoBackgroundUri
    else if (usePlaybackCoverBackground) committedPlaybackCoverBackground
    else ""
  val effectivePageBackgroundBlurred =
    usePlaybackCoverBackground || (hasCustomPageBackground && settings.videoBackgroundBlur)
  val hasImagePageBackground = effectivePageBackgroundSource.isNotBlank()
  val darkVideoPage = videoPageBackground.luminance() < .5f
  val backgroundModel =
    rememberStaticBackgroundModel(
      source = effectivePageBackgroundSource,
      blurred = effectivePageBackgroundBlurred,
    )
  val backgroundRevealAlpha by
    animateFloatAsState(
      targetValue = if (backgroundModel != null) 1f else 0f,
      animationSpec = tween(if (settings.reduceMotion) 90 else 560),
      label = "videoBackgroundReveal",
    )
  val backgroundLuminance = rememberBackgroundLuminanceProfile(backgroundModel)
  val targetHeaderForeground =
    videoBackgroundForeground(
      luminance = backgroundLuminance?.top,
      darkMode = darkVideoPage,
      fallback = MaterialTheme.colorScheme.onBackground,
    )
  val pageHeaderForeground by
    animateColorAsState(
      targetValue = targetHeaderForeground,
      animationSpec = tween(if (settings.reduceMotion) 90 else 220),
      label = "videoHeaderForeground",
    )
  val pageContentForeground by
    animateColorAsState(
      targetValue =
        videoBackgroundForeground(
          luminance = backgroundLuminance?.middle,
          darkMode = darkVideoPage,
          fallback = MaterialTheme.colorScheme.onBackground,
        ),
      animationSpec = tween(if (settings.reduceMotion) 90 else 220),
      label = "videoContentForeground",
    )
  val backgroundScrim = videoBackgroundScrim(backgroundLuminance, darkVideoPage)
  val embeddedPortalRadiusPx = with(LocalDensity.current) { VideoShapeTokens.CornerRadius.toPx() }
  Box(
    Modifier.fillMaxSize()
      .onGloballyPositioned { videoScreenBounds = it.boundsInRoot() }
      .graphicsLayer {
        alpha = if (retainBackgroundDuringPageExit) 1f else pageExitAlpha().coerceIn(0f, 1f)
      }
  ) {
  Box(
    Modifier.matchParentSize()
      .onGloballyPositioned { pageBackgroundBounds = it.boundsInRoot() }
      .drawWithContent {
        pageBackgroundLayer.record { this@drawWithContent.drawContent() }
        drawLayer(pageBackgroundLayer)
      }
  ) {
    Box(Modifier.matchParentSize().drawBehind {
        val bounds = embeddedPlayerBounds
        if (!fullscreenLayerVisible && bounds.width > 0f && bounds.height > 0f) {
          val backgroundOutsidePlayer =
            Path().apply {
              fillType = PathFillType.EvenOdd
              addRect(Rect(0f, 0f, size.width, size.height))
              addRoundRect(
                RoundRect(
                  bounds.left,
                  bounds.top,
                  bounds.right,
                  bounds.bottom,
                  embeddedPortalRadiusPx,
                  embeddedPortalRadiusPx,
                )
              )
            }
          drawPath(backgroundOutsidePlayer, videoPageBackground)
        } else {
          drawRect(videoPageBackground)
        }
      })
    if (hasImagePageBackground && backgroundModel != null) {
      CrossfadeBackgroundImage(
        model = backgroundModel,
        modifier =
          Modifier.matchParentSize()
            .drawWithContent {
              val bounds = embeddedPlayerBounds
              if (!fullscreenLayerVisible && bounds.width > 0f && bounds.height > 0f) {
                val imageOutsidePlayer =
                  Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(
                      RoundRect(
                        bounds.left,
                        bounds.top,
                        bounds.right,
                        bounds.bottom,
                        embeddedPortalRadiusPx,
                        embeddedPortalRadiusPx,
                      )
                    )
                  }
                clipPath(imageOutsidePlayer) { this@drawWithContent.drawContent() }
              } else {
                drawContent()
              }
            }
            .graphicsLayer {
              alpha =
                backgroundRevealAlpha *
                  if (effectivePageBackgroundBlurred) 1f
                  else 1f - settings.videoBackgroundTransparency.coerceIn(0f, 1f)
            },
        contentScale = ContentScale.Crop,
        transitionMillis = if (settings.reduceMotion) 90 else 520,
      )
      Box(
        Modifier.matchParentSize().graphicsLayer { alpha = backgroundRevealAlpha }.drawBehind {
          val bounds = embeddedPlayerBounds
          if (!fullscreenLayerVisible && bounds.width > 0f && bounds.height > 0f) {
            val scrimOutsidePlayer =
              Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(
                  RoundRect(
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom,
                    embeddedPortalRadiusPx,
                    embeddedPortalRadiusPx,
                  )
                )
              }
            clipPath(scrimOutsidePlayer) { drawRect(backgroundScrim) }
          } else {
            drawRect(backgroundScrim)
          }
        }
      )
    }
  }
  val playbackPageGlass = PlaybackPageGlassBackdrop(pageBackgroundLayer, pageBackgroundBounds)
  Scaffold(
    modifier =
      Modifier.fillMaxSize().graphicsLayer {
        // A child-to-parent return reuses this VideoScreen slot. Fade only the child chrome and
        // keep its already-decoded backdrop visible until the parent is restored; otherwise the
        // home layer underneath is exposed as a one-frame white flash.
        alpha = if (retainBackgroundDuringPageExit) pageExitAlpha().coerceIn(0f, 1f) else 1f
      },
    containerColor = Color.Transparent,
    topBar = {
      if (bangumiPage != null) {
        BangumiHeader(
          page = bangumiPage,
          onBack = { leaveHdrPlaybackPage(); onBack() },
          onHome = { leaveHdrPlaybackPage(); onHome() },
          onFollow = onBangumiFollow,
          onRate = onBangumiRate,
          panelSlideProgress = panelSlideProgress,
          showDeviceStatus = settings.showPlaybackDeviceStatus,
          foregroundColor = pageHeaderForeground,
          glassBackdrop = playbackPageGlass,
        )
      } else {
        VideoHeader(
          item = item,
          info = videoInfo,
          onlineViewerText = onlineViewerText,
          description = description,
          currentCid = currentCid,
          onOpenSelection = { showVideoSelection = true },
          onBack = { leaveHdrPlaybackPage(); onBack() },
          onHome = { leaveHdrPlaybackPage(); onHome() },
          onProfileClick = { mid, face, name, bounds ->
            leaveHdrPlaybackPage()
            onProfileClick(mid, face, name, bounds)
          },
          onUploaderProfileClick = { mid, face, name, bounds ->
            leaveHdrPlaybackPage()
            onUploaderProfileClick(mid, face, name, bounds)
          },
          showFollowButton = showUploaderFollowButton,
          followed = uploaderFollowed,
          followBusy = uploaderFollowBusy,
          followingGroups = followingGroups,
          followingGroupsLoading = followingGroupsLoading,
          loggedIn = loggedIn,
          onLoadFollowingGroups = onLoadFollowingGroups,
          onSelectFollowingGroup = onSelectUploaderFollowingGroup,
          onUnfollow = onUnfollowUploader,
          onLogin = { leaveHdrPlaybackPage(); onLogin() },
          onShowInfo = { showVideoInfo = true },
          panelSlideProgress = panelSlideProgress,
          showDeviceStatus = settings.showPlaybackDeviceStatus,
          foregroundColor = pageHeaderForeground,
          glassBackdrop = playbackPageGlass,
        )
      }
    },
  ) { padding ->
    val visiblePlayData =
      activePlayData
        ?: remember {
          PlayUrlData(dashAudioUrl = null, streams = emptyList(), currentStreamIndex = 0)
        }
    run {
      Box(Modifier.fillMaxSize()) {
        VideoContent(
          item = item,
          videoInfo = videoInfo,
          videoEngagement = videoEngagement,
          favoriteFolders = favoriteFolders,
          favoriteFoldersLoading = favoriteFoldersLoading,
          playData = visiblePlayData,
          danmaku = danmaku,
          danmakuPaused = danmakuPaused,
          commentItems = commentItems,
          commentTotalCount = commentTotalCount,
          commentHasMore = commentHasMore,
          commentsLoading = commentsLoading,
          commentSort = commentSort,
          commentsRefreshing = commentsRefreshing,
          pageContentLoading = pageContentLoading,
          pageForegroundColor = pageContentForeground,
          glassBackdrop = playbackPageGlass,
          deferAuxiliaryContent = deferAuxiliaryContent,
          deferCommentContent = deferCommentContent,
          currentAccountMid = currentAccountMid,
          hiddenCommentAvatarRpid = hiddenCommentAvatarRpid,
          commentNavigationTarget = commentNavigationTarget,
          replyRoot = replyRoot,
          replyItems = replyItems,
          replyHasMore = replyHasMore,
          repliesLoading = repliesLoading,
          emotes = emotes,
          emotePackages = emotePackages,
          mentionSuggestions = mentionSuggestions,
          mentionSuggestionsLoading = mentionSuggestionsLoading,
          recommendations = recommendations,
          hiddenRecommendationCoverItemId = hiddenRecommendationCoverItemId,
          hiddenPlaybackEndRecommendationCoverItemId =
            hiddenPlaybackEndRecommendationCoverItemId,
          recommendationListState = recommendationListState,
          recommendationReturnBounds = recommendationReturnBounds,
          commentListState = commentListState,
          commentNavigationSessionId = commentNavigationSessionId,
          commentChromeState = commentChromeState,
          durationMs = durationMs,
          isPlaying = isPlaying,
          isBuffering = isBuffering,
          showLoadingCover = showCoverUntilFirstFrame,
          dimLoadingCover = isHdrPlayback,
          playbackEnded = playbackEnded,
          playbackSpeed = playbackSpeed,
          playbackEndRevealAlpha = playbackEndRevealAlpha,
          embeddedPlayerHandoffAlpha = embeddedPlayerHandoffAlpha,
          autoNextSeconds = autoNextSeconds,
          autoNextTriggered = autoNextTriggered,
          autoNextHandoffProgress = autoNextHandoffProgress,
          onAutoNext = ::triggerAutoNext,
          nextPlaybackTarget = nextPlaybackTarget,
          onLoadMoreComments = onLoadMoreComments,
          onRefreshComments = onRefreshComments,
          onCommentSort = onCommentSort,
          onPostComment = onPostComment,
          onPostReply = onPostReply,
          onLikeComment = onLikeComment,
          onDeleteComment = onDeleteComment,
          onLikeVideo = onLikeVideo,
          onCoinVideo = onCoinVideo,
          onFavoriteVideo = onFavoriteVideo,
          onLoadFavoriteFolders = onLoadFavoriteFolders,
          onLogin = {
            leaveHdrPlaybackPage()
            onLogin()
          },
          onOpenReplies = onOpenReplies,
          onLoadMoreReplies = onLoadMoreReplies,
          onRefreshReplies = onRefreshReplies,
          onDismissReplies = onDismissReplies,
          onCommentNavigationConsumed = onCommentNavigationConsumed,
          onProfileClick = { mid, face, name, bounds ->
            leaveHdrPlaybackPage()
            onProfileClick(mid, face, name, bounds)
          },
          onCommentProfileClick = { mid, comment, anchor ->
            leaveHdrPlaybackPage()
            onCommentProfileClick(mid, comment, anchor)
          },
          onMentionQuery = onMentionQuery,
          onPrepareEnterFullscreen = ::freezeEmbeddedPlayerBounds,
          onEnterFullscreen = ::enterFullscreenAnimated,
          embeddedGestureResetKey = embeddedGestureResetKey,
          playerControlsVisible = playerControlsVisible,
          controlsVisible = controlsVisible,
          onControlsVisible = { controlsVisible = it },
          onControlsMenuVisibilityChanged = { controlsMenuOpen = it },
          onDanmakuComposerVisibilityChanged = { active ->
            embeddedDanmakuComposerVisible = active
            if (active) controlsVisible = true
          },
          onProgressScrubChanged = { active ->
            embeddedProgressScrubbing = active
            if (active) controlsVisible = true
          },
          onPlayPause = onTogglePlayPause,
          onTemporarySpeedChanged = onTemporarySpeedChanged,
          onPlaybackSpeedChanged = onPlaybackSpeedChanged,
          onReplay = onReplay,
          onVideoPageSelected = onVideoPageSelected,
          onCollectionEpisodeSelected = onCollectionEpisodeSelected,
          onSeek = onSeek,
          onSeekPreview = onSeekPreview,
          onSeekCancel = onSeekCancel,
          onToggleDanmaku = onToggleDanmaku,
          onSendDanmaku = onSendDanmaku,
          onSwitchQuality = onSwitchQuality,
          premiumAudioVisible = premiumAudioVisible,
          commentImageEnabled = commentImageEnabled,
          onCommentImagePreview = ::openCommentImagePreview,
          onSwitchPremiumAudio = onSwitchPremiumAudio,
          subtitleState = currentSubtitleState,
          onSelectSubtitle = onSelectSubtitle,
          currentPositionMs = currentPositionMs,
          playerPositionProvider = playerPositionProvider,
          showDanmaku = showDanmaku,
          danmakuComposerEnabled = danmakuComposerEnabled,
          onRecommendationClick = ::openRecommendation,
          onRecommendationLongClick = onRecommendationLongClick,
          onArticleClick = { article, bounds ->
            leaveHdrPlaybackPage()
            onArticleClick(article, bounds)
          },
          hiddenLinkedArticleItemId = hiddenLinkedArticleItemId,
          playerView = {},
          settings = settings,
          brightnessGestureEnabled = settings.brightnessGesture && !isHdrPlayback,
          onSettingsChange = onSettingsChange,
          panelSlideProgress = panelSlideProgress,
          bangumiPage = bangumiPage,
          onBangumiPosterBoundsChanged = onBangumiPosterBoundsChanged,
          onBangumiOpenDetails = {
            resumeAfterBangumiInfo = isPlaying
            if (isPlaying) onTogglePlayPause()
            showBangumiInfo = true
          },
          onBangumiEpisodeSelected = onBangumiEpisodeSelected,
          onBangumiSeasonSelected = onBangumiSeasonSelected,
          modifier = Modifier.padding(padding),
          onPlayerBoundsChanged = {
            embeddedPlayerBounds = it
            if (trackEmbeddedPlayerBounds && it.width > 0f && it.height > 0f) {
              lastValidEmbeddedPlayerBounds = it
            }
            onPlayerBoundsChanged(it)
          },
        )
        if (playerState is PlayerState.Error) {
          Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            shadowElevation = 0.dp,
          ) {
            PlayerErrorActions(
              error = playerState,
              onRetry = onRetryPlayback,
              onRetryNextQuality = onRetryNextQuality,
            )
          }
        }
      }
    }
  }
  if (
    hdrFocusOverlayAlpha > .001f &&
      embeddedPlayerBounds.width > 0f &&
      embeddedPlayerBounds.height > 0f
  ) {
    HdrVideoFocusOverlay(
      playerBounds = embeddedPlayerBounds,
      alpha = hdrFocusOverlayAlpha,
      modifier = Modifier.fillMaxSize().zIndex(50f),
    )
  }
  if (showVideoInfo && bangumiPage == null) {
    VideoInfoTile(
      item = item,
      info = videoInfo,
      onlineViewerText = onlineViewerText,
      description = description,
      onDismiss = { showVideoInfo = false },
      onCacheClick =
        {
          showVideoInfo = false
          showOfflineCacheChooser = true
        }.takeUnless { OfflineMediaManager.isOfflineUri(item.videoUrl) },
    )
  }
  if (showVideoSelection && videoInfo != null && bangumiPage == null) {
    VideoSelectionTile(
      info = videoInfo,
      currentCid = currentCid,
      onPageSelected = {
        showVideoSelection = false
        onVideoPageSelected(it)
      },
      onEpisodeSelected = { episode, bounds ->
        showVideoSelection = false
        onCollectionEpisodeSelected(feedItemFromCollectionEpisode(episode), bounds)
      },
      onDismiss = { showVideoSelection = false },
    )
  }
  if (showBangumiEpisodeSelection && bangumiPage != null) {
    BangumiEpisodeSelectionDialog(
      page = bangumiPage,
      onDismiss = { showBangumiEpisodeSelection = false },
      onEpisodeSelected = { episode ->
        showBangumiEpisodeSelection = false
        onBangumiEpisodeSelected(episode)
      },
    )
  }
  if (showBangumiInfo && bangumiPage != null) {
    BangumiInfoDialog(
      page = bangumiPage,
      onDismiss = {
        showBangumiInfo = false
        if (resumeAfterBangumiInfo) onTogglePlayPause()
        resumeAfterBangumiInfo = false
      },
      onCacheClick = {
        showBangumiInfo = false
        showOfflineCacheChooser = true
      },
    )
  }
  if (showOfflineCacheChooser) {
    OfflineCacheChooserDialog(
      title = bangumiPage?.season?.title ?: videoInfo?.title ?: item.title,
      targets = cacheTargets,
      streams = cacheStreams,
      existingTargetIds = existingOfflineTargetIds,
      premiumAvailable = currentAccountVipActive,
      onDismiss = {
        showOfflineCacheChooser = false
        if (resumeAfterBangumiInfo) onTogglePlayPause()
        resumeAfterBangumiInfo = false
      },
      onConfirm = { requests ->
        val added = requests.count(offlineMediaManager::enqueue)
        val message =
          if (added > 0) "已加入 $added 个缓存任务"
          else "所选内容已经在缓存列表中"
        Toast.makeText(view.context, message, Toast.LENGTH_SHORT).show()
        showOfflineCacheChooser = false
        if (resumeAfterBangumiInfo) onTogglePlayPause()
        resumeAfterBangumiInfo = false
      },
    )
  }
  commentImagePreview?.let { session ->
    CommentImagePreviewOverlay(
      session = session,
      rootBounds = videoScreenBounds,
      onDismiss = ::closeCommentImagePreview,
      modifier = Modifier.fillMaxSize().zIndex(100f),
    )
  }
  }
}

@Composable
private fun FullscreenLockButton(
  locked: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  androidx.compose.material3.IconButton(
    onClick = onClick,
    modifier = modifier.size(48.dp),
  ) {
    Icon(
      imageVector = if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
      contentDescription = if (locked) "解锁播放器控制" else "锁定播放器控制",
      modifier = Modifier.size(24.dp),
      tint = Color.White,
    )
  }
}

internal class CommentImagePreviewSession(
  val image: CommentImage,
  val sourceBounds: Rect,
) {
  val progress = Animatable(0f)
  val preparation =
    TransitionPreparationBarrier(
      setOf(
        TransitionReadySignal.SOURCE_BOUNDS,
        TransitionReadySignal.IMAGE_READY,
        TransitionReadySignal.TARGET_MOUNTED,
        TransitionReadySignal.TARGET_BOUNDS_STABLE,
      )
    )
  val targetBoundsTracker = StableBoundsTracker()
  var phase by mutableStateOf(SessionPhase.PREPARING)
  var preparationTimedOut by mutableStateOf(false)

  init {
    preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
  }
}

internal fun Modifier.floatingPlayerLayout(
  progress: Float,
  sourceBounds: Rect,
  targetInsetPx: Int,
): Modifier = layout { measurable, constraints ->
  val parentWidth = constraints.maxWidth.coerceAtLeast(1)
  val parentHeight = constraints.maxHeight.coerceAtLeast(1)
  val targetLeft = targetInsetPx.coerceAtMost(parentWidth / 2)
  val targetTop = targetInsetPx.coerceAtMost(parentHeight / 2)
  val targetWidth = (parentWidth - targetLeft * 2).coerceAtLeast(1)
  val targetHeight = (parentHeight - targetTop * 2).coerceAtLeast(1)
  val hasSource = sourceBounds.width > 0f && sourceBounds.height > 0f
  val animationProgress = progress.coerceIn(0f, 1f)
  val startLeft = if (hasSource) sourceBounds.left else targetLeft.toFloat()
  val startTop = if (hasSource) sourceBounds.top else targetTop.toFloat()
  val startWidth = if (hasSource) sourceBounds.width else targetWidth.toFloat()
  val startHeight = if (hasSource) sourceBounds.height else targetHeight.toFloat()
  val left = (startLeft + (targetLeft - startLeft) * animationProgress).roundToInt()
  val top = (startTop + (targetTop - startTop) * animationProgress).roundToInt()
  val width =
    (startWidth + (targetWidth - startWidth) * animationProgress)
      .roundToInt()
      .coerceIn(1, parentWidth)
  val height =
    (startHeight + (targetHeight - startHeight) * animationProgress)
      .roundToInt()
      .coerceIn(1, parentHeight)
  val placeable = measurable.measure(Constraints.fixed(width, height))
  layout(parentWidth, parentHeight) { placeable.place(left, top) }
}

internal fun commentImageStartScale(
  sourceBounds: Rect,
  targetWidth: Float,
  targetHeight: Float,
): Float {
  if (
    sourceBounds.width <= 1f || sourceBounds.height <= 1f || targetWidth <= 1f || targetHeight <= 1f
  )
    return .92f
  return minOf(sourceBounds.width / targetWidth, sourceBounds.height / targetHeight)
    .coerceIn(.05f, 1f)
}

internal fun commentImagePanLimit(
  imageSize: Float,
  viewportSize: Float,
  scale: Float,
): Float = maxOf(0f, (imageSize * scale - viewportSize) / 2f)

internal fun isLongCommentImage(width: Int, height: Int): Boolean =
  width > 0 && height.toLong() * 2L >= width.toLong() * 5L

internal data class CommentImageThumbnailSpec(
  val url: String,
  val widthPx: Int,
  val heightPx: Int,
)

private const val COMMENT_IMAGE_THUMBNAIL_MAX_EDGE_PX = 480

internal fun commentImageThumbnailSpec(
  rawUrl: String,
  imageWidth: Int,
  imageHeight: Int,
  targetWidthPx: Int,
  targetHeightPx: Int,
  crop: Boolean,
): CommentImageThumbnailSpec {
  val boundedTargetWidth = targetWidthPx.coerceIn(1, COMMENT_IMAGE_THUMBNAIL_MAX_EDGE_PX)
  val boundedTargetHeight = targetHeightPx.coerceIn(1, COMMENT_IMAGE_THUMBNAIL_MAX_EDGE_PX)
  val validImageSize = imageWidth > 0 && imageHeight > 0
  val width =
    if (!crop && validImageSize) {
      minOf(
          boundedTargetWidth.toFloat(),
          boundedTargetHeight.toFloat() * imageWidth / imageHeight,
        )
        .toInt()
        .coerceAtLeast(1)
    } else {
      boundedTargetWidth
    }
  val height =
    if (!crop && validImageSize) {
      (width.toFloat() * imageHeight / imageWidth).toInt().coerceIn(1, boundedTargetHeight)
    } else {
      boundedTargetHeight
    }
  val originalUrl = fullResolutionCommentImageUrl(rawUrl)
  val host = runCatching { java.net.URI(originalUrl).host.orEmpty() }.getOrDefault("")
  if (host != "hdslb.com" && !host.endsWith(".hdslb.com")) {
    return CommentImageThumbnailSpec(originalUrl, width, height)
  }
  val base = originalUrl.substringBefore('?')
  val query = originalUrl.substringAfter('?', "")
  val suffix = if (crop) "@${width}w_${height}h_1c.webp" else "@${width}w.webp"
  val url = base + suffix + if (query.isBlank()) "" else "?$query"
  return CommentImageThumbnailSpec(url, width, height)
}

internal fun fullResolutionCommentImageUrl(url: String): String {
  val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
  if (host != "hdslb.com" && !host.endsWith(".hdslb.com")) return url
  val base = url.substringBefore('?').substringBefore('@')
  val query = url.substringAfter('?', "")
  return base + if (query.isBlank()) "" else "?$query"
}

internal fun longCommentImageHtml(imageUrl: String): String {
  val escapedUrl =
    buildString(imageUrl.length) {
      imageUrl.forEach { character ->
        append(
          when (character) {
            '&' -> "&amp;"
            '"' -> "&quot;"
            '\'' -> "&#39;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            else -> character
          }
        )
      }
    }
  return """
    <!doctype html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
          html, body {
            width: 100%;
            margin: 0;
            padding: 0;
            overflow-x: hidden;
            background: transparent;
          }
          img {
            display: block;
            width: 100%;
            height: auto;
            margin: 0;
            padding: 0;
          }
        </style>
      </head>
      <body><img src="$escapedUrl"></body>
    </html>
  """
    .trimIndent()
}

internal data class CommentImagePreviewLayout(
  val widthPx: Float,
  val heightPx: Float,
  val verticallyScrollable: Boolean,
)

internal fun commentImagePreviewLayout(
  viewportWidth: Float,
  viewportHeight: Float,
  imageWidth: Int,
  imageHeight: Int,
  wideViewport: Boolean,
): CommentImagePreviewLayout {
  val safeViewportWidth = viewportWidth.coerceAtLeast(1f)
  val safeViewportHeight = viewportHeight.coerceAtLeast(1f)
  val ratio = if (imageWidth > 0 && imageHeight > 0) imageWidth.toFloat() / imageHeight else 1.5f
  if (isLongCommentImage(imageWidth, imageHeight)) {
    return CommentImagePreviewLayout(
      widthPx = safeViewportWidth * if (wideViewport) (2f / 5f) else .88f,
      heightPx = safeViewportHeight * .86f,
      verticallyScrollable = true,
    )
  }
  val maxTargetWidth = safeViewportWidth * .9f
  val maxTargetHeight = safeViewportHeight * .86f
  val targetWidth = minOf(maxTargetWidth, maxTargetHeight * ratio).coerceAtLeast(1f)
  return CommentImagePreviewLayout(
    widthPx = targetWidth,
    heightPx = (targetWidth / ratio).coerceAtMost(maxTargetHeight).coerceAtLeast(1f),
    verticallyScrollable = false,
  )
}

@Composable
internal fun CommentImagePreviewOverlay(
  session: CommentImagePreviewSession,
  rootBounds: Rect,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val previewImageUrl = remember(session.image) { fullResolutionCommentImageUrl(session.image.url) }
  var zoomScale by remember(session) { mutableStateOf(1f) }
  var panOffset by remember(session) { mutableStateOf(Offset.Zero) }
  var confirmSave by remember(session) { mutableStateOf(false) }
  var saving by remember(session) { mutableStateOf(false) }
  fun saveImage() {
    saving = true
    scope.launch {
      val result = savePreviewImageToGallery(context, previewImageUrl)
      saving = false
      confirmSave = false
      val message =
        when (result) {
          ImageSaveResult.SAVED -> "已经保存到相册啦 (´▽`ʃ♡ƪ)"
          ImageSaveResult.PERMISSION_REQUIRED -> "想保存这张图，需要先给我相册权限哦 (´；ω；`)"
          ImageSaveResult.FAILED -> "这张图暂时没能保存下来，请稍后再试 (｡•́︿•̀｡)"
        }
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }
  val legacyStoragePermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        saveImage()
      } else {
        confirmSave = false
        Toast.makeText(
            context,
            "想保存这张图，需要先给我相册权限哦 (´；ω；`)",
            Toast.LENGTH_SHORT,
          )
          .show()
      }
    }
  BackHandler(onBack = onDismiss)
  BoxWithConstraints(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    val viewportWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val viewportHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
    val imageRatio =
      if (session.image.width > 0 && session.image.height > 0)
        session.image.width.toFloat() / session.image.height
      else 1.5f
    val previewLayout =
      commentImagePreviewLayout(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        imageWidth = session.image.width,
        imageHeight = session.image.height,
        wideViewport = maxWidth >= 600.dp,
      )
    val targetWidth = previewLayout.widthPx
    val targetHeight = previewLayout.heightPx
    val rootLeft = if (rootBounds.left.isFinite()) rootBounds.left else 0f
    val rootTop = if (rootBounds.top.isFinite()) rootBounds.top else 0f
    val source = session.sourceBounds.translate(Offset(-rootLeft, -rootTop))
    val validSource = source.width > 1f && source.height > 1f
    val startScale = commentImageStartScale(source, targetWidth, targetHeight)
    val startTranslationX = if (validSource) source.center.x - viewportWidth / 2f else 0f
    val startTranslationY = if (validSource) source.center.y - viewportHeight / 2f else 0f
    val widthDp = with(density) { targetWidth.toDp() }
    val heightDp = with(density) { targetHeight.toDp() }
    LaunchedEffect(session, targetWidth, targetHeight) {
      session.preparation.markReady(TransitionReadySignal.TARGET_MOUNTED)
      repeat(4) {
        withFrameNanos {}
        if (session.targetBoundsTracker.observe(Rect(0f, 0f, targetWidth, targetHeight))) {
          session.preparation.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
          return@LaunchedEffect
        }
      }
    }
    Box(
      Modifier.matchParentSize()
        .graphicsLayer { alpha = session.progress.value.coerceIn(0f, 1f) * .9f }
        .background(Color.Black)
        .clickable(onClick = onDismiss)
    )

    if (validSource) {
      Box(Modifier.matchParentSize(), contentAlignment = Alignment.TopStart) {
        AsyncImage(
          model = session.image.url,
          contentDescription = null,
          modifier =
            Modifier.requiredSize(
                with(density) { source.width.toDp() },
                with(density) { source.height.toDp() },
              )
              .clip(RoundedCornerShape(12.dp))
              .graphicsLayer {
                val progress = session.progress.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = source.left
                translationY = source.top
                alpha = (1f - progress / .35f).coerceIn(0f, 1f)
              },
          contentScale = ContentScale.Crop,
        )
      }
    }

    val transformGestureModifier =
      if (previewLayout.verticallyScrollable) {
        Modifier
      } else {
        Modifier.pointerInput(session, targetWidth, targetHeight) {
          detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
            if (session.progress.value < .995f) return@detectTransformGestures
            val previousScale = zoomScale
            val nextScale = (previousScale * zoom).coerceIn(1f, 5f)
            val scaleChange = nextScale / previousScale.coerceAtLeast(.001f)
            val center = Offset(size.width / 2f, size.height / 2f)
            val centroidCorrection = (centroid - center) * (1f - scaleChange)
            val candidate = panOffset + pan + centroidCorrection
            val maxPanX = commentImagePanLimit(targetWidth, viewportWidth, nextScale)
            val maxPanY = commentImagePanLimit(targetHeight, viewportHeight, nextScale)
            zoomScale = nextScale
            panOffset =
              if (nextScale <= 1.001f) Offset.Zero
              else
                Offset(
                  candidate.x.coerceIn(-maxPanX, maxPanX),
                  candidate.y.coerceIn(-maxPanY, maxPanY),
                )
          }
        }
      }
    val saveGestureModifier =
      if (previewLayout.verticallyScrollable) {
        Modifier
      } else {
        Modifier.pointerInput(session) {
          detectTapGestures(
            onLongPress = {
              if (session.progress.value >= .995f && !saving) confirmSave = true
            }
          )
        }
      }
    Box(
      modifier =
        Modifier.size(widthDp, heightDp)
          .graphicsLayer {
            val progress = session.progress.value.coerceIn(0f, 1f)
            val effectiveZoom = 1f + (zoomScale - 1f) * progress
            val effectivePan = panOffset * progress
            val sharedScale = startScale + (1f - startScale) * progress
            transformOrigin = TransformOrigin.Center
            val imageScale = sharedScale * effectiveZoom
            scaleX = imageScale
            scaleY = imageScale
            translationX = startTranslationX * (1f - progress) + effectivePan.x
            translationY = startTranslationY * (1f - progress) + effectivePan.y
            alpha = if (validSource) ((progress - .04f) / .28f).coerceIn(0f, 1f) else progress
          }
          .then(saveGestureModifier)
          .then(transformGestureModifier)
    ) {
      if (previewLayout.verticallyScrollable) {
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { webContext ->
            WebView(webContext).apply {
              WebViewConfigurator.configure(this, BuildConfig.DEBUG)
              settings.javaScriptEnabled = false
              settings.domStorageEnabled = false
              settings.loadWithOverviewMode = false
              settings.useWideViewPort = false
              settings.builtInZoomControls = false
              settings.displayZoomControls = false
              isHorizontalScrollBarEnabled = false
              isVerticalScrollBarEnabled = true
              overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
              setBackgroundColor(android.graphics.Color.TRANSPARENT)
              isLongClickable = true
              setOnLongClickListener {
                if (session.progress.value >= .995f && !saving) confirmSave = true
                true
              }
              webViewClient =
                object : WebViewClient() {
                  override fun onPageFinished(view: WebView, url: String) {
                    session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
                  }
                }
              loadDataWithBaseURL(
                "https://www.bilibili.com/",
                longCommentImageHtml(previewImageUrl),
                "text/html",
                "UTF-8",
                null,
              )
            }
          },
          onRelease = { webView ->
            webView.setOnLongClickListener(null)
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            webView.destroy()
          },
        )
      } else {
        AsyncImage(
          model = previewImageUrl,
          contentDescription = "图片预览",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Fit,
          onSuccess = { session.preparation.markReady(TransitionReadySignal.IMAGE_READY) },
        )
      }
    }
    if (confirmSave) {
      AlertDialog(
        onDismissRequest = { if (!saving) confirmSave = false },
        title = { Text("保存图片？") },
        text = { Text("图片会保存到手机相册中的“哔哩ss”文件夹。") },
        dismissButton = {
          TextButton(onClick = { confirmSave = false }, enabled = !saving) { Text("取消") }
        },
        confirmButton = {
          TextButton(
            enabled = !saving,
            onClick = {
              if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                  ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                  ) != PackageManager.PERMISSION_GRANTED
              ) {
                legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
              } else {
                saveImage()
              }
            },
          ) {
            Text(if (saving) "保存中…" else "保存")
          }
        },
      )
    }
  }
}

@Composable
private fun HdrVideoFocusOverlay(
  playerBounds: Rect,
  alpha: Float,
  modifier: Modifier = Modifier,
) {
  var overlayBounds by remember { mutableStateOf(Rect.Zero) }
  val cornerRadiusPx = with(LocalDensity.current) { VideoShapeTokens.CornerRadius.toPx() }
  Box(
    modifier
      .onGloballyPositioned { overlayBounds = it.boundsInRoot() }
      .drawBehind {
        if (overlayBounds.width <= 0f || overlayBounds.height <= 0f) return@drawBehind
        val left = (playerBounds.left - overlayBounds.left).coerceIn(0f, size.width)
        val top = (playerBounds.top - overlayBounds.top).coerceIn(0f, size.height)
        val right = (playerBounds.right - overlayBounds.left).coerceIn(0f, size.width)
        val bottom = (playerBounds.bottom - overlayBounds.top).coerceIn(0f, size.height)
        if (right <= left || bottom <= top) return@drawBehind
        val mask =
          Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(left, top, right, bottom, cornerRadiusPx, cornerRadiusPx))
          }
        drawPath(mask, Color.Black.copy(alpha = alpha.coerceIn(0f, 1f)))
      }
  )
}

private suspend fun animateWindowBrightness(
  window: Window,
  from: Float,
  to: Float,
  durationMs: Long,
) {
  val frameCount = (durationMs / HDR_BRIGHTNESS_STEP_MS).toInt().coerceAtLeast(1)
  val frameDelayMs = (durationMs / frameCount).coerceAtLeast(1L)
  for (frame in 1..frameCount) {
    val progress = FastOutSlowInEasing.transform(frame.toFloat() / frameCount)
    setWindowBrightness(window, from + (to - from) * progress)
    if (frame < frameCount) delay(frameDelayMs)
  }
}

internal fun fullscreenVideoBackgroundShadeAlpha(
  embeddedShadeAlpha: Float,
  fullscreenBrightness: Float,
  fullscreenProgress: Float,
): Float {
  val baseShade = embeddedShadeAlpha.coerceIn(0f, 1f)
  val brightness = fullscreenBrightness.coerceIn(0f, 1f)
  val progress = fullscreenProgress.coerceIn(0f, 1f)
  val fullscreenShade = 1f - brightness * (1f - baseShade)
  return baseShade + (fullscreenShade - baseShade) * progress
}

private fun resolveWindowBrightness(context: Context, rawBrightness: Float): Float =
  rawBrightness.takeIf { it in 0f..1f }
    ?: (android.provider.Settings.System.getInt(
      context.contentResolver,
      android.provider.Settings.System.SCREEN_BRIGHTNESS,
      128,
    ) / 255f)

private fun setWindowBrightness(window: Window, brightness: Float) {
  val attributes = window.attributes
  attributes.screenBrightness = brightness.coerceIn(.05f, 1f)
  window.attributes = attributes
}

private fun releaseWindowBrightnessOverride(window: Window) {
  val attributes = window.attributes
  attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
  window.attributes = attributes
}

/**
 * Keep HDR video at a 60 Hz display cadence. On the Tab S8 Ultra, presenting the HDR SurfaceView
 * underneath a visible Canvas danmaku layer at the app's forced 120 Hz causes SurfaceFlinger to
 * buffer-stuff the full Activity surface; 60 Hz matches 24/30/60 fps video without that queue.
 */
@Suppress("DEPRECATION")
private fun hdrPreferredDisplayModeId(activity: Activity?): Int? {
  val modes = activity?.windowManager?.defaultDisplay?.supportedModes.orEmpty()
  return (
      modes.filter { it.refreshRate <= HDR_MAX_DISPLAY_REFRESH_RATE + .5f }.maxByOrNull {
        it.refreshRate
      } ?: modes.minByOrNull { abs(it.refreshRate - HDR_MAX_DISPLAY_REFRESH_RATE) }
    )
    ?.modeId
}

private fun restorePreferredDisplayMode(window: Window, preferredModeId: Int) {
  val attributes = window.attributes
  if (attributes.preferredDisplayModeId == preferredModeId) return
  attributes.preferredDisplayModeId = preferredModeId
  window.attributes = attributes
}

private enum class ImageSaveResult {
  SAVED,
  PERMISSION_REQUIRED,
  FAILED,
}

private const val HDR_BRIGHTNESS_RAMP_MS = 900L
private const val HDR_BRIGHTNESS_RESTORE_MS = 480L
private const val HDR_BRIGHTNESS_STEP_MS = 50L
private const val HDR_MAX_DISPLAY_REFRESH_RATE = 60f

@Suppress("DEPRECATION")
private suspend fun savePreviewImageToGallery(
  context: Context,
  imageUrl: String,
): ImageSaveResult =
  withContext(Dispatchers.IO) {
    var insertedUri: android.net.Uri? = null
    try {
      BiliHttpClient.getPublic(
          imageUrl,
          mapOf("Referer" to "https://www.bilibili.com/", "User-Agent" to "Mozilla/5.0"),
        )
        .use { response ->
          val body = response.body
          if (!response.isSuccessful || body == null) return@withContext ImageSaveResult.FAILED
          val mime =
            response.header("Content-Type")?.substringBefore(';')?.trim()?.takeIf {
              it.startsWith("image/")
            } ?: "image/jpeg"
          val extension =
            when (mime.lowercase()) {
              "image/png" -> "png"
              "image/gif" -> "gif"
              "image/webp" -> "webp"
              "image/avif" -> "avif"
              else -> "jpg"
            }
          val values =
            ContentValues().apply {
              put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "哔哩ss_${System.currentTimeMillis()}.$extension",
              )
              put(MediaStore.Images.Media.MIME_TYPE, mime)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                  MediaStore.Images.Media.RELATIVE_PATH,
                  Environment.DIRECTORY_PICTURES + "/哔哩ss",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
              } else {
                val directory =
                  java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "哔哩ss",
                  )
                if (!directory.exists() && !directory.mkdirs()) {
                  return@withContext ImageSaveResult.FAILED
                }
                put(
                  MediaStore.Images.Media.DATA,
                  java.io
                    .File(
                      directory,
                      "哔哩ss_${System.currentTimeMillis()}.$extension",
                    )
                    .absolutePath,
                )
              }
            }
          val resolver = context.contentResolver
          val uri =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
              ?: return@withContext ImageSaveResult.FAILED
          insertedUri = uri
          val outputStream = resolver.openOutputStream(uri)
          if (outputStream == null) {
            resolver.delete(uri, null, null)
            insertedUri = null
            return@withContext ImageSaveResult.FAILED
          }
          outputStream.use { output ->
            body.byteStream().use { input -> input.copyTo(output) }
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) <= 0) {
              resolver.delete(uri, null, null)
              insertedUri = null
              return@withContext ImageSaveResult.FAILED
            }
          }
          ImageSaveResult.SAVED
        }
    } catch (_: SecurityException) {
      insertedUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
      ImageSaveResult.PERMISSION_REQUIRED
    } catch (_: Throwable) {
      insertedUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
      ImageSaveResult.FAILED
    }
  }

@Composable
internal fun ReplyThreadTransitionContainer(
  sourceBounds: Rect,
  targetBounds: Rect,
  progress: () -> Float,
  contentReady: Boolean = true,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val validBounds =
    sourceBounds.width > 1f &&
      sourceBounds.height > 1f &&
      targetBounds.width > 1f &&
      targetBounds.height > 1f
  val startScaleX = if (validBounds) sourceBounds.width / targetBounds.width else .96f
  val startScaleY = if (validBounds) sourceBounds.height / targetBounds.height else .96f
  val startX = if (validBounds) sourceBounds.left - targetBounds.left else 0f
  val startY = if (validBounds) sourceBounds.top - targetBounds.top else 0f
  Box(modifier = modifier.clipToBounds()) {
    // Progress is read only by the RenderNode-backed layer. Opening and closing now update four
    // transform properties without recomposing the reply list or explicitly recording a large
    // GraphicsLayer on the main thread.
    Box(
      Modifier.fillMaxSize().graphicsLayer {
        val value = progress().coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = startScaleX + (1f - startScaleX) * value
        scaleY = startScaleY + (1f - startScaleY) * value
        translationX = startX * (1f - value)
        translationY = startY * (1f - value)
        alpha = if (validBounds) (value * 2.5f).coerceIn(0f, 1f) else value
      }
    ) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border =
          androidx.compose.foundation.BorderStroke(
            .75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f),
          ),
      ) {}
      AnimatedVisibility(
        visible = contentReady,
        enter = fadeIn(tween(160, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(80, easing = FastOutSlowInEasing)),
      ) {
        Box(Modifier.fillMaxSize()) { content() }
      }
    }
  }
}

@Composable
internal fun FadeVisibility(
  visible: Boolean,
  enterMillis: Int,
  exitMillis: Int,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter = fadeIn(tween(enterMillis)),
    exit = fadeOut(tween(exitMillis)),
  ) {
    content()
  }
}

@Composable
internal fun RecommendationLoadingSkeleton(modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth()) {
    Spacer(Modifier.height(8.dp))
    Surface(
      modifier = Modifier.padding(horizontal = 12.dp).width(88.dp).height(18.dp),
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surfaceVariant,
    ) {}
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 52.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      repeat(3) {
        Surface(
          modifier = Modifier.width(220.dp).height(126.dp),
          shape = VideoShapeTokens.Card,
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f),
        ) {}
      }
    }
  }
}

@Composable
internal fun CommentLoadingSkeleton(modifier: Modifier = Modifier) {
  Column(
    modifier.padding(start = 10.dp, end = 10.dp, top = 56.dp, bottom = 110.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    repeat(5) {
      Surface(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .68f),
      ) {}
    }
  }
}

internal data class VideoActionPanelGlassColors(
  val container: Color,
  val fallback: Color,
  val border: Color,
)

internal fun videoActionPanelGlassColors(foregroundColor: Color): VideoActionPanelGlassColors {
  val usesLightForeground = foregroundColor.luminance() >= .5f
  return if (usesLightForeground) {
    VideoActionPanelGlassColors(
      container = Color.Black.copy(alpha = .58f),
      fallback = Color.Black.copy(alpha = .76f),
      border = Color.White.copy(alpha = .30f),
    )
  } else {
    VideoActionPanelGlassColors(
      container = Color.White.copy(alpha = .72f),
      fallback = Color.White.copy(alpha = .92f),
      border = Color.Black.copy(alpha = .22f),
    )
  }
}

@Composable
internal fun VideoActionPanel(
  info: VideoInfo?,
  engagement: VideoEngagement,
  loggedIn: Boolean,
  favoriteFolders: List<FavoriteFolder>,
  favoriteFoldersLoading: Boolean,
  onLike: (Boolean) -> Unit,
  onCoin: (Int, Boolean) -> Unit,
  onFavorite: (List<Long>, List<Long>) -> Unit,
  onLoadFavoriteFolders: () -> Unit,
  onLogin: () -> Unit,
  foregroundColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  modifier: Modifier = Modifier,
) {
  var showCoinDialog by remember(info?.aid) { mutableStateOf(false) }
  var showFavoriteDialog by remember(info?.aid) { mutableStateOf(false) }
  var favoriteDefaultPending by remember(info?.aid) { mutableStateOf(false) }
  var showCoinBurst by remember(info?.aid) { mutableStateOf(false) }
  val orderedFavoriteFolders =
    remember(favoriteFolders) { prioritizeVideoFavoriteFolders(favoriteFolders) }
  val glassColors = remember(foregroundColor) { videoActionPanelGlassColors(foregroundColor) }
  LaunchedEffect(favoriteDefaultPending, favoriteFoldersLoading, orderedFavoriteFolders) {
    if (!favoriteDefaultPending || favoriteFoldersLoading) return@LaunchedEffect
    val defaultFolder = orderedFavoriteFolders.firstOrNull() ?: return@LaunchedEffect
    favoriteDefaultPending = false
    if (!defaultFolder.favorited) onFavorite(listOf(defaultFolder.id), emptyList())
  }
  LaunchedEffect(showCoinBurst) {
    if (showCoinBurst) {
      delay(720)
      showCoinBurst = false
    }
  }
  BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
    val alignedMenuWidth = maxWidth
    val density = LocalDensity.current
    val compactActions = maxWidth < 380.dp || density.fontScale > 1.15f
    val menuPositionProvider =
      remember(density) {
        AlignedCardPopupPositionProvider(with(density) { 8.dp.roundToPx() })
      }
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = VideoShapeTokens.Card,
      color = glassColors.fallback,
      contentColor = foregroundColor,
      border =
        androidx.compose.foundation.BorderStroke(
          1.dp,
          glassColors.border,
        ),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        VideoActionButton(
          icon = {
            Icon(
              Icons.Default.ThumbUp,
              contentDescription = null,
              modifier = Modifier.size(25.dp),
            )
          },
          label = "点赞",
          count = info?.likeCount,
          active = engagement.loaded && engagement.liked,
          onClick = {
            if (loggedIn) onLike(!engagement.liked) else onLogin()
          },
          enabled =
            info?.aid?.let { it > 0 } == true && (!loggedIn || engagement.loaded),
          compact = compactActions,
          foregroundColor = foregroundColor,
          modifier = Modifier.weight(1f),
        )
        Box(Modifier.weight(1f)) {
          VideoActionButton(
            icon = {
              BiliCoinIcon(
                coinCount = engagement.coins,
                modifier = Modifier.size(28.dp),
              )
            },
            label = "投币",
            count = info?.coinCount,
            active = engagement.loaded && engagement.coins > 0,
            onClick = {
              if (loggedIn) showCoinDialog = true else onLogin()
            },
            enabled =
              info?.aid?.let { it > 0 } == true && (!loggedIn || engagement.loaded),
            compact = compactActions,
            foregroundColor = foregroundColor,
            modifier = Modifier.fillMaxWidth(),
          )
          if (showCoinBurst) CoinBurst(Modifier.align(Alignment.Center))
        }
        VideoActionButton(
          icon = {
            Icon(
              Icons.Default.Star,
              contentDescription = null,
              modifier = Modifier.size(27.dp),
            )
          },
          label = "收藏",
          count = info?.favoriteCount,
          active = engagement.loaded && engagement.favorited,
          onClick = {
            if (!loggedIn) {
              onLogin()
            } else {
              showFavoriteDialog = true
              val defaultFolder = orderedFavoriteFolders.firstOrNull()
              if (defaultFolder == null) {
                favoriteDefaultPending = true
                onLoadFavoriteFolders()
              } else {
                favoriteDefaultPending = false
                if (!defaultFolder.favorited) {
                  onFavorite(listOf(defaultFolder.id), emptyList())
                }
              }
            }
          },
          enabled =
            info?.aid?.let { it > 0 } == true && (!loggedIn || engagement.loaded),
          compact = compactActions,
          foregroundColor = foregroundColor,
          modifier = Modifier.weight(1f),
        )
      }
    }
    if (showCoinDialog) {
      CoinDialog(
        alreadyCoined = engagement.coins,
        alreadyLiked = engagement.liked,
        copyright = info?.copyright ?: 0,
        width = alignedMenuWidth,
        positionProvider = menuPositionProvider,
        onDismiss = { showCoinDialog = false },
        onConfirm = { count, alsoLike ->
          showCoinBurst = true
          onCoin(count, alsoLike)
        },
      )
    }
    if (showFavoriteDialog) {
      FavoriteFolderDialog(
        folders = orderedFavoriteFolders,
        loading = favoriteFoldersLoading,
        width = alignedMenuWidth,
        positionProvider = menuPositionProvider,
        onDismiss = { showFavoriteDialog = false },
        onConfirm = { addIds, removeIds ->
          onFavorite(addIds, removeIds)
        },
      )
    }
  }
}

internal fun prioritizeVideoFavoriteFolders(
  folders: List<FavoriteFolder>
): List<FavoriteFolder> {
  if (folders.size <= 1) return folders
  val defaultFolder =
    folders.firstOrNull { it.title.trim() == "默认收藏夹" }
      ?: folders.first()
  val musicFolder =
    folders.firstOrNull { it.id != defaultFolder.id && it.title.trim() == "音乐" }
  return buildList(folders.size) {
    add(defaultFolder)
    musicFolder?.let(::add)
    folders.forEach { folder ->
      if (folder.id != defaultFolder.id && folder.id != musicFolder?.id) add(folder)
    }
  }
}

private class AlignedCardPopupPositionProvider(private val gapPx: Int) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset {
    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
    val x = anchorBounds.left.coerceIn(0, maxX)
    val below = anchorBounds.bottom + gapPx
    val above = anchorBounds.top - popupContentSize.height - gapPx
    val y =
      when {
        below + popupContentSize.height <= windowSize.height -> below
        above >= 0 -> above
        else -> (windowSize.height - popupContentSize.height).coerceAtLeast(0)
      }
    return IntOffset(x, y)
  }
}

@Composable
private fun VideoActionButton(
  icon: @Composable () -> Unit,
  label: String,
  count: Long?,
  active: Boolean,
  onClick: () -> Unit,
  enabled: Boolean,
  compact: Boolean,
  foregroundColor: Color,
  modifier: Modifier = Modifier,
) {
  val actionPink = Color(0xFFFF5C8A)
  val iconColor by
    animateColorAsState(
      targetValue = if (active) actionPink else foregroundColor.copy(alpha = .86f),
      label = "videoActionIconColor",
    )
  Surface(
    modifier =
      modifier
        .heightIn(min = if (compact) 72.dp else 68.dp)
        .clickable(enabled = enabled, onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    color =
      if (active) actionPink.copy(alpha = .13f)
      else foregroundColor.copy(alpha = .08f),
    contentColor = foregroundColor,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides iconColor) {
        icon()
      }
      Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      count?.let {
        Text(
          formatCompactCount(it),
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun BiliCoinIcon(coinCount: Int, modifier: Modifier = Modifier) {
  val color = androidx.compose.material3.LocalContentColor.current
  Canvas(modifier) {
    fun drawCoin(center: Offset, radius: Float) {
      val strokeWidth = 2.dp.toPx()
      drawCircle(color = color, radius = radius, center = center, style = Stroke(strokeWidth))
      drawLine(
        color,
        Offset(center.x - radius * .30f, center.y - radius * .18f),
        Offset(center.x + radius * .30f, center.y - radius * .18f),
        strokeWidth = strokeWidth,
      )
      drawLine(
        color,
        Offset(center.x - radius * .20f, center.y + radius * .20f),
        Offset(center.x + radius * .20f, center.y + radius * .20f),
        strokeWidth = strokeWidth,
      )
    }

    if (coinIconCount(coinCount) == 2) {
      val radius = size.minDimension * .28f
      drawCoin(Offset(size.width * .42f, size.height * .55f), radius)
      drawCoin(Offset(size.width * .61f, size.height * .43f), radius)
    } else {
      drawCoin(Offset(size.width * .5f, size.height * .5f), size.minDimension * .36f)
    }
  }
}

internal fun coinIconCount(coins: Int): Int = if (coins >= 2) 2 else 1

@Composable
private fun CoinBurst(modifier: Modifier = Modifier) {
  val scale = remember { Animatable(.35f) }
  val alpha = remember { Animatable(1f) }
  LaunchedEffect(Unit) {
    coroutineScope {
      launch {
        scale.animateTo(1.15f, tween(260, easing = FastOutSlowInEasing))
        scale.animateTo(.9f, tween(180))
      }
      launch {
        delay(340)
        alpha.animateTo(0f, tween(300))
      }
    }
  }
  Box(
    modifier.size(64.dp).graphicsLayer {
      scaleX = scale.value
      scaleY = scale.value
      this.alpha = alpha.value
    }
  ) {
    repeat(3) { index ->
      Surface(
        modifier =
          Modifier.size((13 - index).dp)
            .align(
              when (index) {
                0 -> Alignment.TopCenter
                1 -> Alignment.CenterStart
                else -> Alignment.CenterEnd
              }
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
      ) {}
    }
  }
}

@Composable
private fun CoinDialog(
  alreadyCoined: Int,
  alreadyLiked: Boolean,
  copyright: Int,
  width: Dp,
  positionProvider: PopupPositionProvider,
  onDismiss: () -> Unit,
  onConfirm: (Int, Boolean) -> Unit,
) {
  val coinLimit = videoCoinLimit(copyright)
  val remaining = remainingVideoCoins(copyright, alreadyCoined)
  var count by
    remember(alreadyCoined, coinLimit) {
      mutableIntStateOf(remaining.coerceIn(1, coinLimit))
    }
  var alsoLike by remember(alreadyLiked) { mutableStateOf(!alreadyLiked) }
  var exiting by remember { mutableStateOf(false) }
  var pendingConfirm by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
  val scaleProgress = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    scaleProgress.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
  }
  LaunchedEffect(exiting) {
    if (exiting) {
      scaleProgress.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
      val confirm = pendingConfirm
      if (confirm != null) onConfirm(confirm.first, confirm.second)
      onDismiss()
    }
  }
  Popup(
    popupPositionProvider = positionProvider,
    onDismissRequest = { exiting = true },
    properties =
      PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
  ) {
    Surface(
      modifier =
        Modifier.graphicsLayer {
          val p = scaleProgress.value.coerceIn(0f, 1f)
          scaleX = 0.92f + 0.08f * p
          scaleY = 0.92f + 0.08f * p
          alpha = p
        },
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface,
      border =
        androidx.compose.foundation.BorderStroke(
          .75.dp,
          MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
      Column(
        Modifier.width(width).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text("给视频投币", style = MaterialTheme.typography.titleLarge)
        Text(
          if (remaining == 0) {
            "这个视频已经投满 $coinLimit 枚硬币"
          } else {
            "本视频已投 $alreadyCoined 枚，还可以投 $remaining 枚"
          },
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (remaining > 0) {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (1..remaining).forEach { value ->
              FilterChip(
                selected = count == value,
                onClick = { count = value },
                label = { Text("$value 枚") },
              )
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = alsoLike,
              onCheckedChange = { alsoLike = it },
              enabled = !alreadyLiked,
            )
            Text(if (alreadyLiked) "已点赞" else "同时点赞")
          }
        }
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { exiting = true }) { Text("取消") }
          Button(
            onClick = {
              pendingConfirm = count to alsoLike
              exiting = true
            },
            enabled = remaining > 0,
          ) {
            Text("确认投币")
          }
        }
      }
    }
  }
}

@Composable
private fun FavoriteFolderDialog(
  folders: List<FavoriteFolder>,
  loading: Boolean,
  width: Dp,
  positionProvider: PopupPositionProvider,
  onDismiss: () -> Unit,
  onConfirm: (List<Long>, List<Long>) -> Unit,
) {
  val original = remember(folders) { folders.filter { it.favorited }.map { it.id }.toSet() }
  var selected by remember(folders) { mutableStateOf(original) }
  var exiting by remember { mutableStateOf(false) }
  var contentReady by remember { mutableStateOf(false) }
  var pendingConfirm by remember { mutableStateOf<Pair<List<Long>, List<Long>>?>(null) }
  val scaleProgress = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    scaleProgress.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
    contentReady = true
  }
  LaunchedEffect(exiting) {
    if (exiting) {
      contentReady = false
      scaleProgress.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
      val confirm = pendingConfirm
      if (confirm != null) onConfirm(confirm.first, confirm.second)
      onDismiss()
    }
  }
  Popup(
    popupPositionProvider = positionProvider,
    onDismissRequest = { exiting = true },
    properties =
      PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
  ) {
    Surface(
      modifier =
        Modifier.graphicsLayer {
          val p = scaleProgress.value.coerceIn(0f, 1f)
          scaleX = 0.92f + 0.08f * p
          scaleY = 0.92f + 0.08f * p
          alpha = p
        },
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface,
      border =
        androidx.compose.foundation.BorderStroke(
          .75.dp,
          MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
      Column(
        Modifier.width(width).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("收藏到", style = MaterialTheme.typography.titleLarge)
        if (!contentReady) {
          Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
          }
        } else
          when {
            loading ->
              Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
              }
            folders.isEmpty() -> Text("暂无可用收藏夹", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else ->
              LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(folders, key = { it.id }) { folder ->
                  Row(
                    Modifier.fillMaxWidth()
                      .clickable {
                        selected =
                          if (folder.id in selected) selected - folder.id else selected + folder.id
                      }
                      .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Checkbox(
                      checked = folder.id in selected,
                      onCheckedChange = { checked ->
                        selected = if (checked) selected + folder.id else selected - folder.id
                      },
                    )
                    Text(folder.title, modifier = Modifier.weight(1f))
                    Text(
                      folder.mediaCount.toString(),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
          }
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { exiting = true }) { Text("取消") }
          Button(
            onClick = {
              pendingConfirm = (selected - original).toList() to (original - selected).toList()
              exiting = true
            },
            enabled = !loading && folders.isNotEmpty() && selected != original,
          ) {
            Text("确定")
          }
        }
      }
    }
  }
}

@Composable
internal fun AdaptiveVideoPanes(
  primary: @Composable () -> Unit,
  secondary: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  Layout(
    modifier = modifier,
    content = {
      Box(Modifier.fillMaxSize()) { primary() }
      Box(Modifier.fillMaxSize()) { secondary() }
    },
  ) { measurables, constraints ->
    if (measurables.size < 2) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val paneSpec = videoPaneSpec(width, height, density, fontScale)
    if (paneSpec.split) {
      val gap = 12.dp.roundToPx()
      val secondaryWidth = paneSpec.secondarySizePx
      val primaryWidth = width - secondaryWidth - gap
      val primaryPlaceable = measurables[0].measure(Constraints.fixed(primaryWidth, height))
      val secondaryPlaceable = measurables[1].measure(Constraints.fixed(secondaryWidth, height))
      layout(width, height) {
        primaryPlaceable.placeRelative(0, 0)
        secondaryPlaceable.placeRelative(primaryWidth + gap, 0)
      }
    } else {
      val gap = 12.dp.roundToPx()
      val primaryFraction = if (width >= height) .68f else .62f
      val primaryHeight = (height * primaryFraction).roundToInt().coerceAtMost(height - gap)
      val secondaryHeight = height - primaryHeight - gap
      val primaryPlaceable = measurables[0].measure(Constraints.fixed(width, primaryHeight))
      val secondaryPlaceable = measurables[1].measure(Constraints.fixed(width, secondaryHeight))
      layout(width, height) {
        primaryPlaceable.placeRelative(0, 0)
        secondaryPlaceable.placeRelative(0, primaryHeight + gap)
      }
    }
  }
}

internal data class VideoPaneSpec(val split: Boolean, val secondarySizePx: Int)

internal fun videoPaneSpec(
  widthPx: Int,
  heightPx: Int,
  density: Float,
  fontScale: Float,
): VideoPaneSpec {
  if (widthPx <= 0 || heightPx <= 0) return VideoPaneSpec(false, 0)
  val safeDensity = density.coerceAtLeast(.5f)
  val widthDp = widthPx / safeDensity
  val heightDp = heightPx / safeDensity
  val safeFontScale = fontScale.coerceIn(.85f, 2f)
  val gapDp = 12f
  val minimumPrimaryDp = 520f
  val minimumSecondaryDp = (300f + (safeFontScale - 1f) * 90f).coerceIn(288f, 360f)
  val split =
    widthPx >= heightPx &&
      heightDp >= 460f &&
      widthDp >= minimumPrimaryDp + minimumSecondaryDp + gapDp
  if (!split) return VideoPaneSpec(false, 0)

  val targetSecondaryDp = (widthDp * .30f).coerceAtLeast(minimumSecondaryDp)
  val maximumSecondaryDp = widthDp - minimumPrimaryDp - gapDp
  val secondaryPx = (targetSecondaryDp.coerceAtMost(maximumSecondaryDp) * safeDensity).roundToInt()
  return VideoPaneSpec(true, secondaryPx)
}

internal fun shouldUseSplitVideoPanes(
  widthPx: Int,
  heightPx: Int,
  density: Float = 1f,
  fontScale: Float = 1f,
): Boolean = videoPaneSpec(widthPx, heightPx, density, fontScale).split

// ── Recommendation card ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecommendationCard(
  item: FeedItem,
  onClick: (Rect) -> Unit,
  onLongClick: () -> Unit,
  coverVisible: Boolean,
  onCoverBoundsChanged: (Rect) -> Unit = {},
  overlayStyle: Boolean = false,
  cardWidth: Dp = 232.dp,
  compactHorizontal: Boolean = false,
  compactHeight: Dp = 68.dp,
  showDuration: Boolean = false,
) {
  var coverBounds by remember { mutableStateOf(Rect.Zero) }
  val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
  val scope = rememberCoroutineScope()
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) .98f else 1f,
      animationSpec = spring(dampingRatio = .82f, stiffness = 700f),
      label = "recommendationPress",
    )
  val compact = cardWidth < 210.dp
  val compactCoverWidth = (compactHeight - 12.dp) * (16f / 9f)
  val duration = item.duration?.takeIf(String::isNotBlank)
  Surface(
    modifier =
      Modifier.width(cardWidth)
        .then(if (compactHorizontal) Modifier.height(compactHeight) else Modifier)
        .navigationBringIntoViewTarget(bringIntoViewRequester)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .combinedClickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = {
            scope.launch {
              bringIntoViewRequester.bringIntoView()
              withFrameNanos {}
              onClick(coverBounds)
            }
          },
          onLongClick = onLongClick,
        ),
    shape = VideoShapeTokens.Card,
    color = if (overlayStyle) Color(0xFF171A1F) else MaterialTheme.colorScheme.surface,
    contentColor = if (overlayStyle) Color.White else MaterialTheme.colorScheme.onSurface,
    tonalElevation = if (overlayStyle) 0.dp else 2.dp,
    shadowElevation = 0.dp,
    border =
      if (overlayStyle) null
      else
        androidx.compose.foundation.BorderStroke(
          .75.dp,
          MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f),
        ),
  ) {
    VideoCardGradient(
      coverUrl = item.coverUrl,
      loadKey = item.id,
      modifier = Modifier.fillMaxWidth(),
      overlayStyle = overlayStyle,
    ) {
      val cardContentColors = LocalVideoCardContentColors.current
      if (compactHorizontal) {
        Row(
          Modifier.fillMaxSize().padding(6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            Modifier.width(compactCoverWidth)
              .fillMaxHeight()
              .clip(VideoShapeTokens.Player)
              .graphicsLayer {
                alpha = if (coverVisible) 1f else 0f
              }
              .onGloballyPositioned {
                coverBounds = it.boundsInRoot()
                onCoverBoundsChanged(coverBounds)
              }
          ) {
            CoverImage(
              coverUrl = item.coverUrl,
              modifier = Modifier.fillMaxSize(),
              shape = VideoShapeTokens.Player,
              enforceAspectRatio = false,
              loadKey = item.id,
            )
          }
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
              text = item.title,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Medium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (!item.uploaderFace.isNullOrBlank()) {
                if (showDuration && duration != null) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RecommendationAvatar(item.uploaderFace, 18.dp, item.id)
                    Text(
                      text = duration,
                      style = MaterialTheme.typography.labelSmall,
                      color = cardContentColors.secondary,
                      maxLines = 1,
                    )
                  }
                } else {
                  RecommendationAvatar(item.uploaderFace, 18.dp, item.id)
                }
                Spacer(Modifier.width(5.dp))
              }
              Text(
                text = item.uploader.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = cardContentColors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (showDuration && duration != null && item.uploaderFace.isNullOrBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                  text = duration,
                  style = MaterialTheme.typography.labelSmall,
                  color = cardContentColors.secondary,
                  maxLines = 1,
                )
              }
            }
          }
        }
      } else {
        Column {
          Box(
            Modifier.onGloballyPositioned {
                coverBounds = it.boundsInRoot()
                onCoverBoundsChanged(coverBounds)
              }
              .graphicsLayer { alpha = if (coverVisible) 1f else 0f }
          ) {
            CoverImage(
              coverUrl = item.coverUrl,
              modifier = Modifier.fillMaxWidth(),
              shape = VideoShapeTokens.Player,
              loadKey = item.id,
            )
          }
          Text(
            text = item.title,
            modifier =
              Modifier.padding(
                  start = if (compact) 9.dp else 11.dp,
                  end = if (compact) 9.dp else 11.dp,
                  top = if (compact) 7.dp else 9.dp,
                )
                .height(if (compact) 38.dp else 42.dp),
            style =
              if (compact) MaterialTheme.typography.bodySmall
              else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .padding(
                  horizontal = if (compact) 9.dp else 11.dp,
                  vertical = if (compact) 7.dp else 9.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (!item.uploaderFace.isNullOrBlank()) {
              val avatarSize = if (compact) 22.dp else 26.dp
              RecommendationAvatar(item.uploaderFace, avatarSize, item.id)
              Spacer(Modifier.width(if (compact) 7.dp else 8.dp))
            }
            Text(
              text = item.uploader.orEmpty(),
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.labelSmall,
              color = cardContentColors.secondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (showDuration && duration != null) {
              Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
              Text(
                text = duration,
                style = MaterialTheme.typography.labelSmall,
                color = cardContentColors.secondary,
                maxLines = 1,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RecommendationAvatar(
  imageUrl: String?,
  size: Dp,
  loadKey: String,
) {
  val cardContentColors = LocalVideoCardContentColors.current
  Box(
    modifier =
      Modifier.requiredSize(size)
        .clip(CircleShape)
        .background(
          cardContentColors.primary.copy(alpha = .12f)
        )
  ) {
    AvatarImage(
      face = imageUrl.orEmpty(),
      contentDescription = null,
      loadKey = loadKey,
      requestSize = 64,
      modifier = Modifier.matchParentSize(),
    )
  }
}

// ── Comment row ──────────────────────────────────────────────────────────────

/**
 * Milliseconds to wait for comment scrolling to settle before triggering avatar palette pre-fetch,
 * so rapid flings don't queue and cancel extraction jobs on every frame.
 */
private const val PALETTE_PREFETCH_DEBOUNCE_MS = 160L

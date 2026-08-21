package dev.openbili.webdemo.video

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.view.KeyEvent as AndroidKeyEvent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import dev.openbili.webdemo.subtitleStateForMedia
import dev.openbili.webdemo.ui.AvatarImage
import dev.openbili.webdemo.ui.CONTROL_DOUBLE_CONFIRM_TIMEOUT_MS
import dev.openbili.webdemo.ui.CONTROL_SEEK_STEP_MS
import dev.openbili.webdemo.ui.ControlVideoMode
import dev.openbili.webdemo.ui.ControlVideoSurfaceAction
import dev.openbili.webdemo.ui.CrossfadeBackgroundImage
import dev.openbili.webdemo.ui.LocalControlFocusVisible
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.LocalColorfulCardsEnabled
import dev.openbili.webdemo.ui.LocalVideoCardContentColors
import dev.openbili.webdemo.ui.SessionPhase
import dev.openbili.webdemo.ui.StableBoundsTracker
import dev.openbili.webdemo.ui.TransitionPreparationBarrier
import dev.openbili.webdemo.ui.TransitionPreparationResult
import dev.openbili.webdemo.ui.TransitionReadySignal
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.VideoPageSurfaceTokens
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.ui.controlFocusOutline
import dev.openbili.webdemo.ui.navigationBringIntoViewTarget
import dev.openbili.webdemo.ui.rememberBackgroundLuminanceProfile
import dev.openbili.webdemo.ui.rememberNavigationBringIntoViewRequester
import dev.openbili.webdemo.ui.rememberStaticBackgroundModel
import dev.openbili.webdemo.ui.isControlConfirmKey
import dev.openbili.webdemo.ui.resolveControlVideoSurfaceAction
import dev.openbili.webdemo.ui.videoBackgroundForeground
import dev.openbili.webdemo.ui.videoBackgroundScrim
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
  commentMediaBounds: MutableMap<String, Rect> = mutableMapOf(),
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
  onToggleCommentPin: (CommentItem) -> Unit = {},
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
  val controlFocusVisible = LocalControlFocusVisible.current
  // 控制器模式打开视频后先停留在播放页；全屏只能由明确的控制器确认动作进入。
  val controlMode = LocalControlMode.current
  val controlEmbeddedPlayerFocusRequester = remember { FocusRequester() }
  val controlFullscreenPlayerFocusRequester = remember { FocusRequester() }
  val controlEmbeddedControlsFocusRequester = remember { FocusRequester() }
  val controlFullscreenControlsFocusRequester = remember { FocusRequester() }
  val controlHeaderFocusRequester = remember { FocusRequester() }
  val controlHeaderHomeFocusRequester = remember { FocusRequester() }
  val controlHeaderOwnerFocusRequester = remember { FocusRequester() }
  val controlHeaderFollowFocusRequester = remember { FocusRequester() }
  val controlHeaderSelectionFocusRequester = remember { FocusRequester() }
  val controlHeaderDetailsFocusRequester = remember { FocusRequester() }
  val controlRecommendationFocusRequester = remember { FocusRequester() }
  val controlBangumiDetailFocusRequester = remember { FocusRequester() }
  val controlCommentFocusRequester = remember { FocusRequester() }
  val controlPlaybackEndFocusRequester = remember(item.id) { FocusRequester() }
  val controlScope = rememberCoroutineScope()
  val controlPlayerSurfaceFocusedState = remember { mutableStateOf(false) }
  var controlPlayerSurfaceFocused by controlPlayerSurfaceFocusedState
  val controlVideoModeState =
    remember(item.id) { mutableStateOf(ControlVideoMode.PAGE_NAVIGATION) }
  var controlVideoMode by controlVideoModeState
  val controlPendingSingleConfirmJobState = remember { mutableStateOf<Job?>(null) }
  var controlPendingSingleConfirmJob by controlPendingSingleConfirmJobState
  val controlHeaderFocus =
    remember {
      PlaybackHeaderControlFocus(
        back = controlHeaderFocusRequester,
        home = controlHeaderHomeFocusRequester,
        owner = controlHeaderOwnerFocusRequester,
        follow = controlHeaderFollowFocusRequester,
        selection = controlHeaderSelectionFocusRequester,
        details = controlHeaderDetailsFocusRequester,
        player = controlEmbeddedPlayerFocusRequester,
      )
    }
  val window = (view.context as? Activity)?.window
  val activity = view.context as? Activity
  val preferredDisplayModeBeforeVideo =
    remember(window) { window?.attributes?.preferredDisplayModeId ?: 0 }
  var brightnessBeforeHdr by remember(window, item.id) { mutableStateOf<Float?>(null) }

  DisposableEffect(window, item.id) {
    onDispose {
      // 只有视频页持有临时 HDR 覆盖：本组合体离开后把亮度交还系统，而不是在
      // 下一页保留应用级数值。
      window?.let(::releaseWindowBrightnessOverride)
      // MainActivity 通常会申请面板的最高刷新模式：HDR SurfaceView 与 UI 层共享显示
      // 合成器，因此本页临时请求了视频友好节奏后，离开视频页时恢复到那个模式是
      // 必需的。
      window?.let { restorePreferredDisplayMode(it, preferredDisplayModeBeforeVideo) }
    }
  }
  DisposableEffect(item.id) {
    onDispose {
      controlPendingSingleConfirmJob?.cancel()
      controlPendingSingleConfirmJob = null
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

  val showVideoInfoState = remember(item.id) { mutableStateOf(false) }
  var showVideoInfo by showVideoInfoState
  val showVideoSelectionState = remember(item.id) { mutableStateOf(false) }
  var showVideoSelection by showVideoSelectionState
  val showBangumiEpisodeSelectionState = remember { mutableStateOf(false) }
  var showBangumiEpisodeSelection by showBangumiEpisodeSelectionState
  val showBangumiInfoState =
    remember(bangumiPage?.sourceCard?.id) { mutableStateOf(false) }
  var showBangumiInfo by showBangumiInfoState
  val showOfflineCacheChooserState = remember(item.id) { mutableStateOf(false) }
  var showOfflineCacheChooser by showOfflineCacheChooserState
  val resumeAfterBangumiInfoState =
    remember(bangumiPage?.sourceCard?.id) { mutableStateOf(false) }
  var resumeAfterBangumiInfo by resumeAfterBangumiInfoState
  val collectionEpisodes = videoInfo?.collection?.episodes.orEmpty()
  val currentCollectionIndex =
    collectionEpisodes.indexOfFirst { episode ->
      collectionEpisodeSelected(
        episode = episode,
        episodes = collectionEpisodes,
        currentBvid = videoInfo?.bvid.orEmpty(),
        currentCid = currentCid,
      )
    }
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
    cachePlayData
      ?.let { data ->
        (listOfNotNull(data.streams.getOrNull(data.currentStreamIndex)) + data.streams).distinctBy(
          VideoStream::id
        )
      }
      .orEmpty()
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
        videoInfo
          ?.let { info ->
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
              info.pages
                .ifEmpty {
                  listOf(
                    VideoPage(
                      page = 1,
                      cid = info.cid,
                      part = info.title,
                      durationSeconds = info.durationSeconds,
                    )
                  )
                }
                .map { page ->
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
          }
          .orEmpty()
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
  val controlsVisibleState = remember(item.id) { mutableStateOf(!controlMode) }
  var controlsVisible by controlsVisibleState
  val controlState =
    remember(item.id) {
      VideoControlState(
        videoMode = controlVideoModeState,
        controlsVisible = controlsVisibleState,
        playerSurfaceFocused = controlPlayerSurfaceFocusedState,
        pendingSingleConfirmJob = controlPendingSingleConfirmJobState,
      )
    }
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
  val colorfulCardsEnabled = LocalColorfulCardsEnabled.current
  LaunchedEffect(item.id, commentItems, replyItems, colorfulCardsEnabled) {
    if (!colorfulCardsEnabled) return@LaunchedEffect
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
  // 导航到资料页会刻意释放本页临时的 HDR 窗口覆盖，但视频屏保持组合于资料页之下：
  // 同一视频页重新可见时重新取得所有权；否则保留的抑制标记会让亮度与 HDR 焦点
  // 蒙版在资料页往返后永久禁用。
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
  // 播放一到达 END 就释放 HDR 锁，避免结束卡以最大亮度留在屏上：切换到新的 HDR
  // 条目会重新上锁。
  val forceHdrBrightness =
    isHdrPlayback &&
      isPlaybackPageForeground &&
      !playbackEnded &&
      !hdrBrightnessSuppressed &&
      !showCoverUntilFirstFrame
  val dimNonPlayerContent = isHdrPlayback && isPlaybackPageForeground && !hdrBrightnessSuppressed
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
  val hdrFocusOverlayAlpha by
    animateFloatAsState(
      targetValue = if (dimNonPlayerContent && !playbackEnded) .48f else 0f,
      animationSpec = tween(380, easing = FastOutSlowInEasing),
      label = "hdrFocusOverlayAlpha",
    )
  // 原位分 P/选集替换 item 而不替换视频页：把该状态放在 item 键之外，避免已完成
  // 的全屏布局在替换媒体就绪前短暂经内嵌布局重建。
  val fullscreenProgress = remember { Animatable(0f) }
  var fullscreenTransitionBusy by remember { mutableStateOf(false) }
  var fullscreenLayerVisible by remember { mutableStateOf(false) }
  var bangumiControlFocusClaimed by
    remember(bangumiPage?.sourceCard?.id) { mutableStateOf(false) }
  val playbackEndOwnsControlState = remember(item.id) { mutableStateOf(false) }
  var playbackEndOwnsControl by playbackEndOwnsControlState
  PlaybackEndControlEffect(
    playbackEnded = playbackEnded,
    controlMode = controlMode,
    isPlaybackPageForeground = isPlaybackPageForeground,
    fullscreenLayerVisible = fullscreenLayerVisible,
    playbackEndOwnsControlState = playbackEndOwnsControlState,
    state = controlState,
    playbackEndFocusRequester = controlPlaybackEndFocusRequester,
    fullscreenPlayerFocusRequester = controlFullscreenPlayerFocusRequester,
    embeddedPlayerFocusRequester = controlEmbeddedPlayerFocusRequester,
    scope = controlScope,
  )
  val frozenEmbeddedPlayerBoundsState = remember { mutableStateOf(Rect.Zero) }
  var frozenEmbeddedPlayerBounds by frozenEmbeddedPlayerBoundsState
  var lastValidEmbeddedPlayerBounds by remember { mutableStateOf(Rect.Zero) }
  val trackEmbeddedPlayerBoundsState = remember { mutableStateOf(true) }
  var trackEmbeddedPlayerBounds by trackEmbeddedPlayerBoundsState
  var embeddedGestureResetKey by remember { mutableIntStateOf(0) }
  var fullscreenForegroundBounds by remember { mutableStateOf(Rect.Zero) }
  var videoScreenBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  val pageBackgroundLayer = rememberGraphicsLayer()
  var pageBackgroundBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  val commentImagePreviewState =
    remember(item.id) {
      mutableStateOf<CommentImagePreviewSession?>(null)
    }
  var commentImagePreview by commentImagePreviewState
  val commentImagePreviewJobState =
    remember(item.id) {
      mutableStateOf<kotlinx.coroutines.Job?>(null)
    }
  var commentImagePreviewJob by commentImagePreviewJobState
  val embeddedPlayerHandoffAlpha = remember {
    { (1f - fullscreenProgress.value).coerceIn(0f, 1f) }
  }
  val transitionScope = rememberCoroutineScope()
  val transitionDuration = if (settings.reduceMotion) 100 else 360
  DisposableEffect(item.id) {
    onDispose {
      commentImagePreviewJob?.cancel()
      commentImagePreview?.preparation?.cancel()
      // 预览仍打开时页面被拆除：清除抑制标记，让后续视频不会以隐藏弹幕开局。
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
  val autoNextTriggeredState =
    remember(item.id, currentCid, autoNextKey) { mutableStateOf(false) }
  var autoNextTriggered by autoNextTriggeredState
  val autoNextHandoff = remember(item.id, currentCid, autoNextKey) { Animatable(0f) }
  val playbackEndRevealAlpha = remember(item.id) { { playbackEndReveal.value } }
  val autoNextHandoffProgress =
    remember(item.id, currentCid, autoNextKey) { { autoNextHandoff.value } }
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
    if (!autoNextTriggered)
      triggerAutoNextForPage(
        autoNextTriggeredState = autoNextTriggeredState,
        autoNextKey = autoNextKey,
        playbackEnded = playbackEnded,
        scope = transitionScope,
        autoNextHandoff = autoNextHandoff,
        nextPlaybackTarget = nextPlaybackTarget,
      )
  }
  fun enterFullscreenAnimated() {
    if (fullscreenTransitionBusy || fullscreenLayerVisible) return
    // 结束背景渲染在同一个浮动播放器边界内：在第二个布局实例组合前保持其已解码
    // 封面强保留，让移动模糊在全屏交接期间绝不发起新图片请求。
    if (playbackEnded) PlaybackCoverRegistry.requestRetention(item.coverUrl)
    freezeEmbeddedPlayerBoundsForPage(
      fullscreenLayerVisible = fullscreenLayerVisible,
      embeddedPlayerBounds = embeddedPlayerBounds,
      lastValidEmbeddedPlayerBounds = lastValidEmbeddedPlayerBounds,
      frozenEmbeddedPlayerBoundsState = frozenEmbeddedPlayerBoundsState,
      trackEmbeddedPlayerBoundsState = trackEmbeddedPlayerBoundsState,
    )
    if (frozenEmbeddedPlayerBounds.width <= 0f || frozenEmbeddedPlayerBounds.height <= 0f) return
    fullscreenLayerVisible = true
    fullscreenTransitionBusy = true
    onFullscreenTransitionChanged(true)
    onFullscreenChanged(true)
    transitionScope.launch {
      // 让宿主在边界开始移动前隐藏独立的弹幕 Surface。
      withFrameNanos {}
      fullscreenProgress.animateTo(
        1f,
        tween(transitionDuration, easing = FastOutSlowInEasing),
      )
      fullscreenTransitionBusy = false
      onFullscreenTransitionChanged(false)
      if (controlMode) {
        controlVideoMode = ControlVideoMode.PLAYER_DIRECT
        controlsVisible = false
        withFrameNanos {}
        runCatching { controlFullscreenPlayerFocusRequester.requestFocus() }
      }
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
      // SurfaceView 可见性在遍历期间应用：只在下一帧动画。
      withFrameNanos {}
      fullscreenProgress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
      )
      fullscreenLayerVisible = false
      trackEmbeddedPlayerBounds = true
      // 全屏层在被移除前拥有指针流：交接后重建窗口手势闸门，让已完成的全屏手势
      // 不能继续吞掉子页的下一次双指手势。
      embeddedGestureResetKey += 1
      fullscreenTransitionBusy = false
      onFullscreenTransitionChanged(false)
      if (controlMode) {
        controlVideoMode = ControlVideoMode.PLAYER_DIRECT
        controlsVisible = false
        withFrameNanos {}
        runCatching { controlEmbeddedPlayerFocusRequester.requestFocus() }
      }
    }
  }
  BackHandler(enabled = isFullscreen || fullscreenLayerVisible) {
    if (fullscreenTransitionBusy) return@BackHandler
    if (controlMode && controlVideoMode == ControlVideoMode.PLAYER_CONTROLS) {
      controlsVisible = false
      controlVideoMode = ControlVideoMode.PLAYER_DIRECT
      controlScope.launch {
        withFrameNanos {}
        runCatching { controlFullscreenPlayerFocusRequester.requestFocus() }
      }
    } else {
      exitFullscreenAnimated()
    }
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
  val controlControlsBlocked =
    controlsHeldVisible ||
      fullscreenControlsLocked ||
      playbackEnded ||
      playerState !is PlayerState.Ready ||
      showVideoInfo ||
      showVideoSelection ||
      showBangumiEpisodeSelection ||
      showBangumiInfo ||
      showOfflineCacheChooser
  // 番剧根页会为共享返回继续组合；页面首次真正落地时明确声明播放器焦点一次，
  // 让隐藏根层没有机会在转场提交后重新夺走控制权。后续用户导航不再重复抢焦点。
  LaunchedEffect(
    controlMode,
    bangumiPage?.sourceCard?.id,
    isPlaybackPageForeground,
    fullscreenLayerVisible,
    playerState is PlayerState.Ready,
  ) {
    if (
      bangumiControlFocusClaimed ||
        !controlMode ||
        bangumiPage == null ||
        !isPlaybackPageForeground ||
        fullscreenLayerVisible ||
        controlVideoMode != ControlVideoMode.PAGE_NAVIGATION ||
        playerState !is PlayerState.Ready ||
        controlControlsBlocked
    ) {
      return@LaunchedEffect
    }
    bangumiControlFocusClaimed = true
    requestVideoControlFocus(controlScope, controlEmbeddedPlayerFocusRequester)
  }
  // 番剧下方面板会在共享元素转场结束后才组合。面板未挂载时保留播放器的 ↓ 消费，
  // 但不向尚不存在的选集 requester 发焦点请求。
  val controlRecommendationFocusReady = bangumiPage == null || !deferAuxiliaryContent

  VideoControlEffects(
    controlMode = controlMode,
    controlFocusVisible = controlFocusVisible,
    controlFocusTargetReady = playerState is PlayerState.Ready,
    // 播放数据未就绪只代表焦点目标尚未挂载，不能与弹层阻塞混为一谈；前者变为
    // Ready 时必须重试，后者则等待弹层自己的关闭回调恢复入口焦点。
    controlFocusRestoreBlocked =
      controlsHeldVisible ||
        fullscreenControlsLocked ||
        playbackEnded ||
        showVideoInfo ||
        showVideoSelection ||
        showBangumiEpisodeSelection ||
        showBangumiInfo ||
        showOfflineCacheChooser,
    itemId = item.id,
    isPlaybackPageForeground = isPlaybackPageForeground,
    state = controlState,
    fullscreenLayerVisible = fullscreenLayerVisible,
    embeddedPlayerFocusRequester = controlEmbeddedPlayerFocusRequester,
    fullscreenPlayerFocusRequester = controlFullscreenPlayerFocusRequester,
    controlsHeldVisible = controlsHeldVisible,
    isPlaying = isPlaying,
    fullscreenControlsLocked = fullscreenControlsLocked,
    controlsTimeoutSeconds = settings.controlsTimeoutSeconds,
  )

  VideoControlBackHandler(
    enabled =
      controlMode &&
        !fullscreenLayerVisible &&
        controlVideoMode != ControlVideoMode.PAGE_NAVIGATION,
    state = controlState,
    scope = controlScope,
    embeddedPlayerFocusRequester = controlEmbeddedPlayerFocusRequester,
  )

  val embeddedControlPlayerModifier =
    controlPlayerSurfaceModifier(
      state = controlState,
      scope = controlScope,
      controlMode = controlMode,
      controlControlsBlocked = controlControlsBlocked,
      isPlaying = isPlaying,
      onTogglePlayPause = onTogglePlayPause,
      fullscreenLayerVisible = fullscreenLayerVisible,
      enterFullscreenAnimated = ::enterFullscreenAnimated,
      durationMs = durationMs,
      currentPositionMs = currentPositionMs,
      onSeek = onSeek,
      commentChromeState = commentChromeState,
      headerFocusRequester = controlHeaderFocusRequester,
      recommendationFocusRequester = controlRecommendationFocusRequester,
      recommendationFocusReady = controlRecommendationFocusReady,
      commentFocusRequester = controlCommentFocusRequester,
      fullscreenControlsFocusRequester = controlFullscreenControlsFocusRequester,
      embeddedControlsFocusRequester = controlEmbeddedControlsFocusRequester,
      focusRequester = controlEmbeddedPlayerFocusRequester,
      fullscreen = false,
    )
  val fullscreenControlPlayerModifier =
    controlPlayerSurfaceModifier(
      state = controlState,
      scope = controlScope,
      controlMode = controlMode,
      controlControlsBlocked = controlControlsBlocked,
      isPlaying = isPlaying,
      onTogglePlayPause = onTogglePlayPause,
      fullscreenLayerVisible = fullscreenLayerVisible,
      enterFullscreenAnimated = ::enterFullscreenAnimated,
      durationMs = durationMs,
      currentPositionMs = currentPositionMs,
      onSeek = onSeek,
      commentChromeState = commentChromeState,
      headerFocusRequester = controlHeaderFocusRequester,
      recommendationFocusRequester = controlRecommendationFocusRequester,
      recommendationFocusReady = controlRecommendationFocusReady,
      commentFocusRequester = controlCommentFocusRequester,
      fullscreenControlsFocusRequester = controlFullscreenControlsFocusRequester,
      embeddedControlsFocusRequester = controlEmbeddedControlsFocusRequester,
      focusRequester = controlFullscreenPlayerFocusRequester,
      fullscreen = true,
    )

  // 全屏播放器到达真实窗口边缘：首次真全屏试验期间保持下方圆角插值不变。
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
        useColorfulCardsPreference = false,
      ) {
        Box(Modifier.fillMaxSize()) {
          // 只压暗封面派生的黑边：SurfaceView 位于此层之上，解码视频保持原始
          // 亮度与 HDR 输出。
          Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = letterboxShadeAlpha)))
          playerView(Modifier.fillMaxSize(), progress, fullscreenLayerVisible)
        }
      }
    }
  }

  if (fullscreenLayerVisible) {
    Box(Modifier.fillMaxSize().clipToBounds().zIndex(100f)) {
      Box(
        Modifier.fillMaxSize()
          .then(fullscreenControlPlayerModifier)
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
            enabledTwoFingerSeek = !fullscreenControlsLocked && settings.twoFingerSeekGesture,
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
            onToggleControls = {
              if (!(controlMode && controlVideoMode == ControlVideoMode.PLAYER_CONTROLS)) {
                controlsVisible = !controlsVisible
              }
            },
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
          visible = controlsVisible && playerState is PlayerState.Ready,
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
              controlEnabled =
                controlMode && controlVideoMode == ControlVideoMode.PLAYER_CONTROLS,
              controlInitialFocusRequester = controlFullscreenControlsFocusRequester,
            )
        }
        FadingVisibility(
          visible = controlsVisible && playerState is PlayerState.Ready && !playbackEnded,
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
              onNext = {
                triggerAutoNextForPage(
                  autoNextTriggeredState = autoNextTriggeredState,
                  autoNextKey = autoNextKey,
                  playbackEnded = playbackEnded,
                  scope = transitionScope,
                  autoNextHandoff = autoNextHandoff,
                  nextPlaybackTarget = nextPlaybackTarget,
                )
              },
              onReplay = onReplay,
              controlEnabled =
                controlMode &&
                  playbackEndOwnsControl &&
                  fullscreenLayerVisible,
              initialFocusRequester = controlPlaybackEndFocusRequester,
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
                openRecommendationFromDetail(
                  leaveHdrPlaybackPage = ::leaveHdrPlaybackPage,
                  onRecommendationClick = onRecommendationClick,
                  recommendation = recommendation,
                  bounds = bounds,
                  returnBounds =
                    embeddedRecommendationReturnBoundsForPage(
                      fullscreenForegroundBounds = fullscreenForegroundBounds,
                      embeddedPlayerBounds = embeddedPlayerBounds,
                      fullscreenCardBounds = bounds,
                    ),
                  fromPlaybackEnd = true,
                )
              },
              onRecommendationLongClick = onRecommendationLongClick,
              controlEnabled =
                controlMode &&
                  playbackEndOwnsControl &&
                  fullscreenLayerVisible,
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
            openRecommendationFromDetail(
              leaveHdrPlaybackPage = ::leaveHdrPlaybackPage,
              onRecommendationClick = onRecommendationClick,
              recommendation = recommendation,
              bounds = bounds,
              returnBounds =
                embeddedRecommendationReturnBoundsForPage(
                  fullscreenForegroundBounds = fullscreenForegroundBounds,
                  embeddedPlayerBounds = embeddedPlayerBounds,
                  fullscreenCardBounds = bounds,
                ),
              fromPlaybackEnd = true,
            )
          },
          onRecommendationLongClick = onRecommendationLongClick,
          controlEnabled =
            controlMode &&
              playbackEndOwnsControl &&
              fullscreenLayerVisible,
          initialFocusRequester = controlPlaybackEndFocusRequester,
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
  // 在整个播放屏生命周期内保留最后呈现的封面。推荐/合集/分 P/返回栈切换在同一
  // 槽位替换 [item]：按媒体 ID 键控该状态会在新渲染器出帧前就清掉旧背景。
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
        currentPlaybackCover.isNotBlank() -> committedPlaybackCoverBackground = currentPlaybackCover
    }
  }
  val usePlaybackCoverBackground =
    !hasCustomPageBackground &&
      settings.useVideoCoverBackground &&
      committedPlaybackCoverBackground.isNotBlank()
  val effectivePageBackgroundSource =
    if (hasCustomPageBackground) settings.videoBackgroundUri
    else if (usePlaybackCoverBackground) committedPlaybackCoverBackground else ""
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
      Box(
        Modifier.matchParentSize().drawBehind {
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
        }
      )
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
          Modifier.matchParentSize()
            .graphicsLayer { alpha = backgroundRevealAlpha }
            .drawBehind {
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
                clipPath(scrimOutsidePlayer) {
                  drawRect(backgroundScrim)
                  if (usePlaybackCoverBackground) {
                    drawRect(
                      Color.Black,
                      alpha = VideoPageSurfaceTokens.BlurredCoverBackgroundDimAlpha,
                    )
                  }
                }
              } else {
                drawRect(backgroundScrim)
                if (usePlaybackCoverBackground) {
                  drawRect(
                    Color.Black,
                    alpha = VideoPageSurfaceTokens.BlurredCoverBackgroundDimAlpha,
                  )
                }
              }
            }
        )
      }
    }
    val playbackPageGlass = PlaybackPageGlassBackdrop(pageBackgroundLayer, pageBackgroundBounds)
    Scaffold(
      modifier =
        Modifier.fillMaxSize().graphicsLayer {
          // 子页返回父页复用这个 VideoScreen 槽位：只淡出子页控制条，保持其已解码
          // 背景可见直到父页恢复；否则底下首页层会以一帧白闪暴露出来。
          alpha = if (retainBackgroundDuringPageExit) pageExitAlpha().coerceIn(0f, 1f) else 1f
        },
      containerColor = Color.Transparent,
      topBar = {
        if (bangumiPage != null) {
          BangumiHeader(
            page = bangumiPage,
            onBack = {
              leaveHdrPlaybackPage()
              onBack()
            },
            onHome = {
              leaveHdrPlaybackPage()
              onHome()
            },
            onFollow = onBangumiFollow,
            onRate = onBangumiRate,
            panelSlideProgress = panelSlideProgress,
            showDeviceStatus = settings.showPlaybackDeviceStatus,
            foregroundColor = pageHeaderForeground,
            glassBackdrop = playbackPageGlass,
            controlFocus = controlHeaderFocus.takeIf { controlMode },
          )
        } else {
          VideoHeader(
            item = item,
            info = videoInfo,
            onlineViewerText = onlineViewerText,
            description = description,
            currentCid = currentCid,
            onOpenSelection = { showVideoSelection = true },
            onBack = {
              leaveHdrPlaybackPage()
              onBack()
            },
            onHome = {
              leaveHdrPlaybackPage()
              onHome()
            },
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
            onLogin = {
              leaveHdrPlaybackPage()
              onLogin()
            },
            onShowInfo = { showVideoInfo = true },
            panelSlideProgress = panelSlideProgress,
            showDeviceStatus = settings.showPlaybackDeviceStatus,
            foregroundColor = pageHeaderForeground,
            glassBackdrop = playbackPageGlass,
            controlFocus = controlHeaderFocus.takeIf { controlMode },
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
            hiddenPlaybackEndRecommendationCoverItemId = hiddenPlaybackEndRecommendationCoverItemId,
            recommendationListState = recommendationListState,
            recommendationReturnBounds = recommendationReturnBounds,
            commentMediaBounds = commentMediaBounds,
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
            onAutoNext = {
              triggerAutoNextForPage(
                autoNextTriggeredState = autoNextTriggeredState,
                autoNextKey = autoNextKey,
                playbackEnded = playbackEnded,
                scope = transitionScope,
                autoNextHandoff = autoNextHandoff,
                nextPlaybackTarget = nextPlaybackTarget,
              )
            },
            nextPlaybackTarget = nextPlaybackTarget,
            onLoadMoreComments = onLoadMoreComments,
            onRefreshComments = onRefreshComments,
            onCommentSort = onCommentSort,
            onPostComment = onPostComment,
            onPostReply = onPostReply,
            onLikeComment = onLikeComment,
            onDeleteComment = onDeleteComment,
            onToggleCommentPin = onToggleCommentPin,
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
            onPrepareEnterFullscreen = {
              freezeEmbeddedPlayerBoundsForPage(
                fullscreenLayerVisible = fullscreenLayerVisible,
                embeddedPlayerBounds = embeddedPlayerBounds,
                lastValidEmbeddedPlayerBounds = lastValidEmbeddedPlayerBounds,
                frozenEmbeddedPlayerBoundsState = frozenEmbeddedPlayerBoundsState,
                trackEmbeddedPlayerBoundsState = trackEmbeddedPlayerBoundsState,
              )
            },
            onEnterFullscreen = ::enterFullscreenAnimated,
            embeddedGestureResetKey = embeddedGestureResetKey,
            playerControlsVisible = playerControlsVisible,
            controlsVisible = controlsVisible,
            onControlsVisible = {
              controlsVisible =
                if (controlMode && controlVideoMode == ControlVideoMode.PLAYER_CONTROLS) true
                else it
            },
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
            onCommentImagePreview = { image, bounds ->
              openVideoCommentImagePreview(
                previewState = commentImagePreviewState,
                previewJobState = commentImagePreviewJobState,
                scope = transitionScope,
                reduceMotion = settings.reduceMotion,
                image = image,
                bounds = bounds,
              )
            },
            onSwitchPremiumAudio = onSwitchPremiumAudio,
            subtitleState = currentSubtitleState,
            onSelectSubtitle = onSelectSubtitle,
            currentPositionMs = currentPositionMs,
            playerPositionProvider = playerPositionProvider,
            showDanmaku = showDanmaku,
            danmakuComposerEnabled = danmakuComposerEnabled,
            onRecommendationClick = { recommendation, bounds, returnBounds, fromEnd ->
              openRecommendationFromDetail(
                leaveHdrPlaybackPage = ::leaveHdrPlaybackPage,
                onRecommendationClick = onRecommendationClick,
                recommendation = recommendation,
                bounds = bounds,
                returnBounds = returnBounds,
                fromPlaybackEnd = fromEnd,
              )
            },
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
            onBangumiOpenEpisodeSelection = { showBangumiEpisodeSelection = true },
            onBangumiEpisodeSelected = onBangumiEpisodeSelected,
            onBangumiSeasonSelected = onBangumiSeasonSelected,
            controlPlayerModifier = embeddedControlPlayerModifier,
            controlPlayerControlsEnabled =
              controlMode && controlVideoMode == ControlVideoMode.PLAYER_CONTROLS,
            controlPlayerControlsFocusRequester = controlEmbeddedControlsFocusRequester,
            controlPlaybackEndedEnabled =
              controlMode &&
                playbackEndOwnsControl &&
                !fullscreenLayerVisible,
            controlPlaybackEndFocusRequester = controlPlaybackEndFocusRequester,
            controlModeEnabled = controlMode,
            controlNavigationEnabled =
              controlMode && controlVideoMode == ControlVideoMode.PAGE_NAVIGATION,
            controlPlayerFocusRequester = controlEmbeddedPlayerFocusRequester,
            controlRecommendationFocusRequester = controlRecommendationFocusRequester,
            controlBangumiLowerPanelFocus =
              if (
                controlMode &&
                  controlVideoMode == ControlVideoMode.PAGE_NAVIGATION &&
                  bangumiPage != null
              ) {
                BangumiLowerPanelControlFocus(
                  detail = controlBangumiDetailFocusRequester,
                  episodes = controlRecommendationFocusRequester,
                  player = controlEmbeddedPlayerFocusRequester,
                )
              } else {
                null
              },
            controlCommentFocusRequester = controlCommentFocusRequester,
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
    VideoScreenDialogs(
      controlMode = controlMode,
      controlScope = controlScope,
      controlHeaderDetailsFocusRequester = controlHeaderDetailsFocusRequester,
      controlHeaderSelectionFocusRequester = controlHeaderSelectionFocusRequester,
      controlBangumiDetailFocusRequester = controlBangumiDetailFocusRequester,
      controlBangumiEpisodeFocusRequester = controlRecommendationFocusRequester,
      item = item,
      videoInfo = videoInfo,
      onlineViewerText = onlineViewerText,
      description = description,
      bangumiPage = bangumiPage,
      currentCid = currentCid,
      onVideoPageSelected = onVideoPageSelected,
      onCollectionEpisodeSelected = onCollectionEpisodeSelected,
      onBangumiEpisodeSelected = onBangumiEpisodeSelected,
      onBangumiSeasonSelected = onBangumiSeasonSelected,
      onControlBangumiEpisodeSelected = {
        controlPendingSingleConfirmJob?.cancel()
        controlPendingSingleConfirmJob = null
        controlVideoMode = ControlVideoMode.PAGE_NAVIGATION
        controlsVisible = false
        requestVideoControlFocus(controlScope, controlEmbeddedPlayerFocusRequester)
      },
      onTogglePlayPause = onTogglePlayPause,
      showVideoInfoState = showVideoInfoState,
      showVideoSelectionState = showVideoSelectionState,
      showBangumiEpisodeSelectionState = showBangumiEpisodeSelectionState,
      showBangumiInfoState = showBangumiInfoState,
      showOfflineCacheChooserState = showOfflineCacheChooserState,
      resumeAfterBangumiInfoState = resumeAfterBangumiInfoState,
      cacheTargets = cacheTargets,
      cacheStreams = cacheStreams,
      existingOfflineTargetIds = existingOfflineTargetIds,
      currentAccountVipActive = currentAccountVipActive,
      offlineMediaManager = offlineMediaManager,
      context = view.context,
      commentImagePreview = commentImagePreview,
      videoScreenBounds = videoScreenBounds,
      commentImagePreviewState = commentImagePreviewState,
      commentImagePreviewJobState = commentImagePreviewJobState,
      previewScope = transitionScope,
      reduceMotion = settings.reduceMotion,
    )
  }
}

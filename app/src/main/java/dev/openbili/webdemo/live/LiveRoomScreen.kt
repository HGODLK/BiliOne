package dev.openbili.webdemo.live

import android.app.Activity
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.DanmakuInlineEmote
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.controlFocusOutline
import dev.openbili.webdemo.ui.isControlConfirmKey
import dev.openbili.webdemo.video.AdaptiveVideoPanes
import dev.openbili.webdemo.video.BiliRichText
import dev.openbili.webdemo.video.CommentEmoteMarkerRegistry
import dev.openbili.webdemo.video.CommentTextEditor
import dev.openbili.webdemo.video.ControllerPlaybackActionItem
import dev.openbili.webdemo.video.ControllerPlaybackControls
import dev.openbili.webdemo.video.ControllerPlaybackOverlay
import dev.openbili.webdemo.video.DanmakuControlIcon
import dev.openbili.webdemo.video.DanmakuOverlayView
import dev.openbili.webdemo.video.FullscreenControlIcon
import dev.openbili.webdemo.video.GestureIndicator
import dev.openbili.webdemo.video.GestureIndicatorKind
import dev.openbili.webdemo.video.GestureIndicatorOverlay
import dev.openbili.webdemo.video.GESTURE_INDICATOR_FADE_IN_MS
import dev.openbili.webdemo.video.GESTURE_INDICATOR_FADE_OUT_MS
import dev.openbili.webdemo.video.PlaybackHeader
import dev.openbili.webdemo.video.PlaybackHeaderControlFocus
import dev.openbili.webdemo.video.PlaybackHeaderUiModel
import dev.openbili.webdemo.video.PlaybackPageGlassBackdrop
import dev.openbili.webdemo.video.PlaybackPageGlassSurface
import dev.openbili.webdemo.video.PlayerGestureLayer
import dev.openbili.webdemo.video.RecommendationCard
import dev.openbili.webdemo.video.floatingPlayerLayout
import dev.openbili.webdemo.video.formatCompactCount
import dev.openbili.webdemo.video.videoPageLayoutForPane
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
internal fun LiveRoomScreen(
  entry: LiveSearchRoom,
  navigationEntryId: Long = 0L,
  account: UserInfo,
  player: ExoPlayer?,
  playerView: @Composable (Modifier, Float, Boolean) -> Unit,
  onPlaySource: (Long, LiveStreamSource) -> Unit,
  onStopPlayback: (Long) -> Unit,
  onSeekLiveEdge: () -> Unit,
  onFullscreenTransitionChanged: (Boolean) -> Unit = {},
  pageTransitionSuppressed: Boolean = false,
  onBack: () -> Unit,
  onHome: () -> Unit,
  onLogin: () -> Unit,
  onAnchorProfile: (Long, String?, String?, Rect) -> Unit,
  onRecommendedRoom: (LiveSearchRoom, Rect) -> Unit,
  onRecommendedRoomBoundsChanged: (LiveSearchRoom, Rect) -> Unit,
  hiddenRecommendationCoverItemId: String? = null,
  settings: AppSettings,
  onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
  onPlayerBoundsChanged: (Rect) -> Unit = {},
  onFirstVideoFrameRendered: (Long) -> Unit = {},
  headerForegroundColor: Color = MaterialTheme.colorScheme.onBackground,
  contentForegroundColor: Color = MaterialTheme.colorScheme.onBackground,
  glassBackdrop: PlaybackPageGlassBackdrop = PlaybackPageGlassBackdrop(),
  active: Boolean = true,
  viewModel: LiveRoomViewModel = viewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val controlMode = LocalControlMode.current
  val controlHeaderFocusRequester = remember(entry.roomId) { FocusRequester() }
  val controlHeaderHomeFocusRequester = remember(entry.roomId) { FocusRequester() }
  val controlHeaderOwnerFocusRequester = remember(entry.roomId) { FocusRequester() }
  val controlHeaderFollowFocusRequester = remember(entry.roomId) { FocusRequester() }
  val controlHeaderSelectionFocusRequester = remember(entry.roomId) { FocusRequester() }
  val controlHeaderDetailsFocusRequester = remember(entry.roomId) { FocusRequester() }
  val controlPlayerFocusRequester = remember(entry.roomId) { FocusRequester() }
  val controlHeaderFocus =
    remember {
      PlaybackHeaderControlFocus(
        back = controlHeaderFocusRequester,
        home = controlHeaderHomeFocusRequester,
        owner = controlHeaderOwnerFocusRequester,
        follow = controlHeaderFollowFocusRequester,
        selection = controlHeaderSelectionFocusRequester,
        details = controlHeaderDetailsFocusRequester,
        player = controlPlayerFocusRequester,
      )
    }
  var controllerLevel by
    remember(entry.roomId, navigationEntryId) {
      mutableStateOf(LiveRoomControllerLevel.PAGE_NAVIGATION)
    }
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val view = LocalView.current
  val activity = view.context as? Activity
  val window = activity?.window
  val fullscreenProgress = remember(entry.roomId) { Animatable(0f) }
  val fullscreenScope = rememberCoroutineScope()
  var fullscreenTransitionBusy by remember(entry.roomId) { mutableStateOf(false) }
  var fullscreenLayerVisible by remember(entry.roomId) { mutableStateOf(false) }
  var recommendationTransitionRequested by
    remember(entry.roomId, navigationEntryId) { mutableStateOf(false) }
  var embeddedPlayerBounds by remember(entry.roomId) { mutableStateOf(Rect.Zero) }

  LaunchedEffect(recommendationTransitionRequested, pageTransitionSuppressed) {
    if (recommendationTransitionRequested && !pageTransitionSuppressed) {
      delay(300L)
      if (!pageTransitionSuppressed) recommendationTransitionRequested = false
    }
  }
  var frozenEmbeddedPlayerBounds by remember(entry.roomId) { mutableStateOf(Rect.Zero) }
  var showInfo by remember(entry.roomId) { mutableStateOf(false) }
  val showDanmaku = settings.liveShowDanmaku
  var secondaryTab by rememberSaveable(entry.roomId) { mutableStateOf(LiveSecondaryTab.CHAT) }
  var showDanmakuBlockWords by remember(entry.roomId) { mutableStateOf(false) }
  var danmakuBlockWords by remember(entry.roomId) { mutableStateOf<List<String>>(emptyList()) }
  val danmakuBlockRoomId = state.roomInfo?.roomId?.takeIf { it > 0L } ?: entry.roomId
  val danmakuStartedAtElapsedMs =
    remember(entry.roomId) { android.os.SystemClock.elapsedRealtime() }
  val liveDanmaku =
    remember(state.liveDanmaku, danmakuStartedAtElapsedMs, danmakuBlockWords) {
      state.liveDanmaku
        .filterNot { event -> isLiveDanmakuBlocked(event.content, danmakuBlockWords) }
        .map { event ->
          val content = event.content
          DanmakuItem(
            timeMs = (event.enterAtElapsedMs - danmakuStartedAtElapsedMs).coerceAtLeast(0L),
            type = 1,
            fontSize = 25,
            color = 0xFFFFFF,
            content =
              when (content) {
                is LiveChatContent.Text -> content.text
                is LiveChatContent.Emoji -> content.displayName
                is LiveChatContent.System -> content.text
              },
            isLocal = false,
            sourceId = event.stableId,
            imageUrl = (content as? LiveChatContent.Emoji)?.imageUrl,
            imageLarge = (content as? LiveChatContent.Emoji)?.isBulge == true,
            inlineEmotes =
              (content as? LiveChatContent.Text)
                ?.emotes
                ?.map { (token, url) -> DanmakuInlineEmote(token, url) }
                .orEmpty(),
          )
        }
    }
  var firstVideoFrameRendered by remember(entry.roomId, state.generation) { mutableStateOf(false) }
  var playbackRunning by
    remember(entry.roomId, state.generation) { mutableStateOf(player?.isPlaying == true) }
  var danmakuRenderStartPositionMs by
    remember(entry.roomId, state.generation) { mutableStateOf<Long?>(null) }
  val renderedLiveDanmaku =
    remember(liveDanmaku, danmakuRenderStartPositionMs) {
      liveDanmakuAfterPlaybackStart(liveDanmaku, danmakuRenderStartPositionMs)
    }

  DisposableEffect(player, entry.roomId, state.generation, danmakuStartedAtElapsedMs) {
    firstVideoFrameRendered = false
    playbackRunning = player?.isPlaying == true
    danmakuRenderStartPositionMs = null

    fun startDanmakuWhenReady() {
      if (firstVideoFrameRendered && playbackRunning && danmakuRenderStartPositionMs == null) {
        danmakuRenderStartPositionMs =
          (android.os.SystemClock.elapsedRealtime() - danmakuStartedAtElapsedMs).coerceAtLeast(0L)
      }
    }

    val listener =
      object : Player.Listener {
        override fun onRenderedFirstFrame() {
          if (!firstVideoFrameRendered) {
            firstVideoFrameRendered = true
            onFirstVideoFrameRendered(navigationEntryId)
          }
          startDanmakuWhenReady()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
          playbackRunning = isPlaying
          startDanmakuWhenReady()
        }
      }
    player?.addListener(listener)
    onDispose { player?.removeListener(listener) }
  }

  LaunchedEffect(danmakuBlockRoomId) {
    danmakuBlockWords = LiveDanmakuBlockWordsStore.read(context, danmakuBlockRoomId)
  }

  LaunchedEffect(entry.roomId, navigationEntryId, active) {
    if (
      active &&
        (state.entryRoomId != entry.roomId ||
          state.navigationEntryId != navigationEntryId ||
          state.roomInfo == null)
    ) {
      viewModel.open(entry, navigationEntryId)
    }
    viewModel.setForeground(active)
  }
  LaunchedEffect(secondaryTab, active) {
    if (active && secondaryTab == LiveSecondaryTab.RANK) viewModel.ensureRankLoaded()
  }
  LaunchedEffect(active) {
    viewModel.setForeground(active)
    if (!active) player?.pause()
  }
  LaunchedEffect(controlMode, active, entry.roomId, controllerLevel) {
    if (
      controlMode &&
        active &&
        controllerLevel == LiveRoomControllerLevel.PAGE_NAVIGATION &&
        settings.controllerTouchPlaybackPage &&
        !settings.alwaysControllerPlaybackPage
    ) {
      androidx.compose.runtime.withFrameNanos {}
      runCatching { controlHeaderDetailsFocusRequester.requestFocus() }
    }
  }
  DisposableEffect(view, active, settings.keepScreenOn, controlMode) {
    val previousKeepScreenOn = view.keepScreenOn
    // 控制器接管页面时保持常亮；未接管时沿用原有的播放页设置。
    view.keepScreenOn = active && (controlMode || settings.keepScreenOn)
    onDispose { view.keepScreenOn = previousKeepScreenOn }
  }
  LaunchedEffect(account) { viewModel.updateAccount(account) }
  val source = state.playback?.sources?.getOrNull(state.activeSourceIndex)
  val realRoomId = state.roomInfo?.roomId ?: entry.roomId
  val latestRealRoomId by rememberUpdatedState(realRoomId)
  LaunchedEffect(realRoomId, state.playback?.currentQn, state.activeSourceIndex, source, active) {
    if (active) source?.let { onPlaySource(realRoomId, it) }
  }
  DisposableEffect(entry.roomId) {
    onDispose {
      viewModel.close()
      onStopPlayback(latestRealRoomId)
    }
  }
  DisposableEffect(lifecycleOwner, entry.roomId) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> viewModel.setForeground(active)
        Lifecycle.Event.ON_STOP -> {
          viewModel.setForeground(false)
          player?.pause()
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  fun enterFullscreenAnimated() {
    if (fullscreenTransitionBusy || fullscreenLayerVisible) return
    val source = embeddedPlayerBounds.takeIf { it.width > 0f && it.height > 0f } ?: return
    frozenEmbeddedPlayerBounds = source
    fullscreenLayerVisible = true
    fullscreenTransitionBusy = true
    onFullscreenTransitionChanged(true)
    fullscreenScope.launch {
      fullscreenProgress.animateTo(
        1f,
        tween(if (settings.reduceMotion) 100 else 360, easing = FastOutSlowInEasing),
      )
      fullscreenTransitionBusy = false
      onFullscreenTransitionChanged(false)
    }
  }
  fun exitFullscreenAnimated() {
    if (fullscreenTransitionBusy || !fullscreenLayerVisible) return
    fullscreenTransitionBusy = true
    onFullscreenTransitionChanged(true)
    fullscreenScope.launch {
      fullscreenProgress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
      )
      fullscreenLayerVisible = false
      fullscreenTransitionBusy = false
      onFullscreenTransitionChanged(false)
    }
  }
  DisposableEffect(navigationEntryId) {
    onDispose { onFullscreenTransitionChanged(false) }
  }
  BackHandler {
    if (fullscreenLayerVisible) exitFullscreenAnimated() else onBack()
  }
  DisposableEffect(window, fullscreenLayerVisible) {
    if (!fullscreenLayerVisible || window == null) return@DisposableEffect onDispose {}
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

  val room = state.roomInfo
  val anchor = state.anchorInfo
  val metadata =
    buildList {
        listOfNotNull(room?.parentAreaName, room?.areaName)
          .distinct()
          .takeIf { it.isNotEmpty() }
          ?.joinToString(" · ")
          ?.let(::add)
        state.watchedText?.takeIf(String::isNotBlank)?.let(::add)
          ?: state.online.takeIf { it > 0L }?.let { add("${formatCompactCount(it)} 人气") }
        room?.roomId?.takeIf { it > 0L }?.let { add("房间号 $it") }
      }
      .joinToString("  ·  ")

  if (settings.alwaysControllerPlaybackPage || (controlMode && !settings.controllerTouchPlaybackPage)) {
    LiveRoomControllerPlaybackScreen(
      entry = entry,
      state = state,
      account = account,
      player = player,
      playerView = playerView,
      firstFrameRendered = firstVideoFrameRendered,
      showDanmaku = showDanmaku,
      danmakuBlockWordCount = danmakuBlockWords.size,
      danmaku = renderedLiveDanmaku,
      danmakuStartedAtElapsedMs = danmakuStartedAtElapsedMs,
      danmakuRenderingEnabled = danmakuRenderStartPositionMs != null,
      danmakuTransitionSuppressed = pageTransitionSuppressed,
      settings = settings,
      onSettingsChange = onSettingsChange,
      onShowDanmaku = { enabled -> onSettingsChange { it.copy(liveShowDanmaku = enabled) } },
      onOpenDanmakuBlockWords = { showDanmakuBlockWords = true },
      onQuality = viewModel::loadPlayback,
      onRetryPlayback = { viewModel.loadPlayback(state.playback?.currentQn ?: LIVE_ORIGINAL_QN) },
      onPlaybackError = viewModel::onPlaybackError,
      onPlaybackReady = viewModel::onPlaybackReady,
      onPlaybackStall = viewModel::onPlaybackStall,
      onSeekLiveEdge = onSeekLiveEdge,
      onPlayerBoundsChanged = onPlayerBoundsChanged,
      onBack = onBack,
      onText = viewModel::setComposerText,
      onSend = viewModel::sendText,
      onToggleEmoji = viewModel::toggleEmojiPanel,
      onSelectEmojiPack = viewModel::selectEmojiPack,
      onEmoji = viewModel::sendEmoji,
      onJoinLottery = viewModel::joinInteractiveLottery,
      onLogin = onLogin,
      onRankTab = viewModel::selectRankTab,
      onAudienceRank = viewModel::selectAudienceRank,
      onGuardType = viewModel::selectGuardType,
      onLoadMoreGuards = viewModel::loadMoreGuards,
      glassBackdrop = glassBackdrop,
    )
    return
  }

  Box(Modifier.fillMaxSize()) {
    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
          PlaybackHeader(
            model =
              PlaybackHeaderUiModel(
                stableId = "live:${room?.roomId ?: entry.roomId}",
                title = room?.title ?: entry.title,
                ownerMid = anchor?.uid ?: room?.anchorUid ?: entry.uid,
                ownerName = anchor?.name ?: entry.uname,
                ownerFace = anchor?.faceUrl ?: entry.faceUrl,
                description = room?.description.orEmpty(),
                metadata = metadata,
              ),
            onBack = onBack,
            onHome = onHome,
            onOwnerProfileClick = onAnchorProfile,
            showFollowButton =
              (anchor?.uid ?: room?.anchorUid ?: entry.uid) > 0L &&
                (anchor?.uid ?: room?.anchorUid ?: entry.uid) != account.mid,
            followed = state.followed,
            followBusy = state.followBusy,
            followingGroups = state.followingGroups,
            followingGroupsLoading = state.followingGroupsLoading,
            loggedIn = account.isLogin,
            onLoadFollowingGroups = viewModel::loadFollowingGroups,
            onSelectFollowingGroup = viewModel::selectFollowingGroup,
            onUnfollow = viewModel::unfollow,
            onLogin = onLogin,
            onShowInfo = { showInfo = true },
            showDeviceStatus = settings.showPlaybackDeviceStatus,
            foregroundColor = headerForegroundColor,
            glassBackdrop = glassBackdrop,
            controlFocus = controlHeaderFocus.takeIf { controlMode },
          )
          AdaptiveVideoPanes(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            primary = {
              LivePrimaryPane(
                state = state,
                player = player,
                playerView = playerView,
                showPlayer = !fullscreenLayerVisible,
                showDanmaku = showDanmaku,
                onShowDanmaku = { enabled ->
                  onSettingsChange { it.copy(liveShowDanmaku = enabled) }
                },
                danmakuBlockWordCount = danmakuBlockWords.size,
                onOpenDanmakuBlockWords = { showDanmakuBlockWords = true },
                danmaku = renderedLiveDanmaku,
                danmakuStartedAtElapsedMs = danmakuStartedAtElapsedMs,
                danmakuRenderingEnabled = danmakuRenderStartPositionMs != null,
                danmakuTransitionSuppressed =
                  fullscreenTransitionBusy ||
                    pageTransitionSuppressed ||
                    recommendationTransitionRequested,
                settings = settings,
                onSettingsChange = onSettingsChange,
                onPlayerBoundsChanged = { bounds ->
                  embeddedPlayerBounds = bounds
                  onPlayerBoundsChanged(bounds)
                },
                controllerLevel = controllerLevel,
                onControllerLevelChanged = { controllerLevel = it },
                controlPlayerFocusRequester = controlPlayerFocusRequester,
                onControlReturnToHeader = {
                  controllerLevel = LiveRoomControllerLevel.PAGE_NAVIGATION
                  runCatching { controlHeaderDetailsFocusRequester.requestFocus() }
                },
                onToggleFullscreen = ::enterFullscreenAnimated,
                onQuality = viewModel::loadPlayback,
                onRetryPlayback = {
                  viewModel.loadPlayback(state.playback?.currentQn ?: 10_000)
                },
                onPlaybackError = viewModel::onPlaybackError,
                onPlaybackReady = viewModel::onPlaybackReady,
                onPlaybackStall = viewModel::onPlaybackStall,
                onSeekLiveEdge = onSeekLiveEdge,
                onRecommendedRoom = { room, bounds ->
                  recommendationTransitionRequested = true
                  onRecommendedRoom(room, bounds)
                },
                onRecommendedRoomBoundsChanged = onRecommendedRoomBoundsChanged,
                hiddenRecommendationCoverItemId = hiddenRecommendationCoverItemId,
                onRetryRecommendations = viewModel::retryRecommendations,
                foregroundColor = contentForegroundColor,
              )
            },
            secondary = {
              LiveSecondaryPane(
                state = state,
                account = account,
                selectedTab = secondaryTab,
                onSelectedTabChange = { secondaryTab = it },
                onText = viewModel::setComposerText,
                onSend = viewModel::sendText,
                onToggleEmoji = viewModel::toggleEmojiPanel,
                onSelectEmojiPack = viewModel::selectEmojiPack,
                onEmoji = viewModel::sendEmoji,
                onJoinLottery = viewModel::joinInteractiveLottery,
                onLogin = onLogin,
                onRankTab = viewModel::selectRankTab,
                onAudienceRank = viewModel::selectAudienceRank,
                onGuardType = viewModel::selectGuardType,
                onLoadMoreGuards = viewModel::loadMoreGuards,
                glassBackdrop = glassBackdrop,
              )
            },
          )
        }

        if (fullscreenLayerVisible) {
          Box(
            Modifier.fillMaxSize()
              .zIndex(80f)
              .graphicsLayer { alpha = fullscreenProgress.value.coerceIn(0f, 1f) }
              .background(Color.Black)
          )
          val progress = fullscreenProgress.value.coerceIn(0f, 1f)
          LivePlayerCard(
            state = state,
            player = player,
            playerView = playerView,
            showDanmaku = showDanmaku,
            onShowDanmaku = { enabled ->
              onSettingsChange { it.copy(liveShowDanmaku = enabled) }
            },
            danmakuBlockWordCount = danmakuBlockWords.size,
            onOpenDanmakuBlockWords = { showDanmakuBlockWords = true },
            danmaku = renderedLiveDanmaku,
            danmakuStartedAtElapsedMs = danmakuStartedAtElapsedMs,
            danmakuRenderingEnabled = danmakuRenderStartPositionMs != null,
            danmakuTransitionSuppressed =
              fullscreenTransitionBusy ||
                pageTransitionSuppressed ||
                recommendationTransitionRequested,
            settings = settings,
            onSettingsChange = onSettingsChange,
            fullscreen = true,
            fullscreenProgress = progress,
            onToggleFullscreen = ::exitFullscreenAnimated,
            onQuality = viewModel::loadPlayback,
            onRetryPlayback = {
              viewModel.loadPlayback(state.playback?.currentQn ?: 10_000)
            },
            onPlaybackError = viewModel::onPlaybackError,
            onPlaybackReady = viewModel::onPlaybackReady,
            onPlaybackStall = viewModel::onPlaybackStall,
            onSeekLiveEdge = onSeekLiveEdge,
            controllerLevel = controllerLevel,
            onControllerLevelChanged = { controllerLevel = it },
            controlPlayerFocusRequester = controlPlayerFocusRequester,
            onControlReturnToHeader = {
              controllerLevel = LiveRoomControllerLevel.PAGE_NAVIGATION
              runCatching { controlHeaderDetailsFocusRequester.requestFocus() }
            },
            modifier =
              Modifier.fillMaxSize()
                .floatingPlayerLayout(
                  progress = progress,
                  sourceBounds = frozenEmbeddedPlayerBounds,
                  targetInsetPx = 0,
                )
                .zIndex(90f)
                .graphicsLayer {
                  shape = RoundedCornerShape(VideoShapeTokens.CornerRadius * (1f - progress))
                  clip = true
                },
          )
        }
      }
    }
  }

  if (showInfo) {
    LiveRoomInfoDialog(
      state = state,
      onDismiss = { showInfo = false },
    )
  }
  if (showDanmakuBlockWords) {
    LiveDanmakuBlockWordsDialog(
      roomId = danmakuBlockRoomId,
      currentWords = danmakuBlockWords,
      onDismiss = { showDanmakuBlockWords = false },
      onSave = { words ->
        danmakuBlockWords = words
        LiveDanmakuBlockWordsStore.write(context, danmakuBlockRoomId, words)
        showDanmakuBlockWords = false
        Toast.makeText(
            context,
            if (words.isEmpty()) "已清空当前直播间的弹幕屏蔽词" else "已为当前直播间保存 ${words.size} 个弹幕屏蔽词",
            Toast.LENGTH_SHORT,
          )
          .show()
      },
    )
  }
}

@Composable
private fun LivePrimaryPane(
  state: LiveRoomUiState,
  player: ExoPlayer?,
  playerView: @Composable (Modifier, Float, Boolean) -> Unit,
  showPlayer: Boolean,
  showDanmaku: Boolean,
  onShowDanmaku: (Boolean) -> Unit,
  danmakuBlockWordCount: Int,
  onOpenDanmakuBlockWords: () -> Unit,
  danmaku: List<DanmakuItem>,
  danmakuStartedAtElapsedMs: Long,
  danmakuRenderingEnabled: Boolean,
  danmakuTransitionSuppressed: Boolean,
  settings: AppSettings,
  onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
  onPlayerBoundsChanged: (Rect) -> Unit,
  onToggleFullscreen: () -> Unit,
  onQuality: (Int) -> Unit,
  onRetryPlayback: () -> Unit,
  onPlaybackError: (Int, PlaybackException) -> Unit,
  onPlaybackReady: (Int) -> Unit,
  onPlaybackStall: (Int) -> Unit,
  onSeekLiveEdge: () -> Unit,
  controllerLevel: LiveRoomControllerLevel,
  onControllerLevelChanged: (LiveRoomControllerLevel) -> Unit,
  controlPlayerFocusRequester: FocusRequester,
  onControlReturnToHeader: () -> Unit,
  onRecommendedRoom: (LiveSearchRoom, Rect) -> Unit,
  onRecommendedRoomBoundsChanged: (LiveSearchRoom, Rect) -> Unit,
  hiddenRecommendationCoverItemId: String?,
  onRetryRecommendations: () -> Unit,
  foregroundColor: Color,
) {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = Color.Transparent,
    contentColor = foregroundColor,
  ) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val pageLayout = videoPageLayoutForPane(maxWidth.value, maxHeight.value)
      val playerHeight = pageLayout.playerHeight.coerceAtLeast(96.dp)
      Column(Modifier.fillMaxSize()) {
        Box(
          modifier = Modifier.fillMaxWidth().height(playerHeight),
          contentAlignment = Alignment.Center,
        ) {
          if (showPlayer) {
            LivePlayerCard(
              state = state,
              player = player,
              playerView = playerView,
              showDanmaku = showDanmaku,
              onShowDanmaku = onShowDanmaku,
              danmakuBlockWordCount = danmakuBlockWordCount,
              onOpenDanmakuBlockWords = onOpenDanmakuBlockWords,
              danmaku = danmaku,
              danmakuStartedAtElapsedMs = danmakuStartedAtElapsedMs,
              danmakuRenderingEnabled = danmakuRenderingEnabled,
              danmakuTransitionSuppressed = danmakuTransitionSuppressed,
              settings = settings,
              onSettingsChange = onSettingsChange,
              onPlayerBoundsChanged = onPlayerBoundsChanged,
              fullscreen = false,
              fullscreenProgress = 0f,
              onToggleFullscreen = onToggleFullscreen,
              onQuality = onQuality,
              onRetryPlayback = onRetryPlayback,
              onPlaybackError = onPlaybackError,
              onPlaybackReady = onPlaybackReady,
              onPlaybackStall = onPlaybackStall,
              onSeekLiveEdge = onSeekLiveEdge,
              controllerLevel = controllerLevel,
              onControllerLevelChanged = onControllerLevelChanged,
              controlPlayerFocusRequester = controlPlayerFocusRequester,
              onControlReturnToHeader = onControlReturnToHeader,
              modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f),
            )
          } else {
            Box(
              Modifier.fillMaxHeight()
                .aspectRatio(16f / 9f)
                .clip(VideoShapeTokens.Player)
                .background(Color.Black)
            )
          }
        }
        Spacer(Modifier.height(8.dp))
        LiveRecommendationSection(
          state = state,
          onRoom = onRecommendedRoom,
          onRoomBoundsChanged = onRecommendedRoomBoundsChanged,
          hiddenCoverItemId = hiddenRecommendationCoverItemId,
          onRetry = onRetryRecommendations,
          cardWidth = pageLayout.recommendationCardWidth,
          compactHorizontal = pageLayout.compactHorizontalRecommendations,
          compactHeight = pageLayout.compactRecommendationCardHeight,
          foregroundColor = foregroundColor,
          modifier = Modifier.fillMaxWidth().weight(1f),
        )
      }
    }
  }
}

@Composable
private fun LivePlayerCard(
  state: LiveRoomUiState,
  player: ExoPlayer?,
  playerView: @Composable (Modifier, Float, Boolean) -> Unit,
  showDanmaku: Boolean,
  onShowDanmaku: (Boolean) -> Unit,
  danmakuBlockWordCount: Int,
  onOpenDanmakuBlockWords: () -> Unit,
  danmaku: List<DanmakuItem>,
  danmakuStartedAtElapsedMs: Long,
  danmakuRenderingEnabled: Boolean,
  danmakuTransitionSuppressed: Boolean,
  settings: AppSettings,
  onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
  fullscreen: Boolean,
  fullscreenProgress: Float,
  onToggleFullscreen: () -> Unit,
  onQuality: (Int) -> Unit,
  onRetryPlayback: () -> Unit,
  onPlaybackError: (Int, PlaybackException) -> Unit,
  onPlaybackReady: (Int) -> Unit,
  onPlaybackStall: (Int) -> Unit,
  onSeekLiveEdge: () -> Unit,
  controllerLevel: LiveRoomControllerLevel = LiveRoomControllerLevel.PAGE_NAVIGATION,
  onControllerLevelChanged: (LiveRoomControllerLevel) -> Unit = {},
  controlPlayerFocusRequester: FocusRequester? = null,
  onControlReturnToHeader: () -> Unit = {},
  onPlayerBoundsChanged: (Rect) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var isPlaying by remember(player) { mutableStateOf(player?.isPlaying == true) }
  var buffering by remember(player) { mutableStateOf(player.isLiveBuffering()) }
  var qualityMenu by remember { mutableStateOf(false) }
  var danmakuMenu by remember { mutableStateOf(false) }
  var liveOffsetMs by remember { mutableLongStateOf(C.TIME_UNSET) }
  var controlsVisible by remember(state.entryRoomId) { mutableStateOf(true) }
  val controlMode = LocalControlMode.current
  val playerFocusRequester = controlPlayerFocusRequester ?: remember { FocusRequester() }
  val controlActionFocusRequester = remember { FocusRequester() }
  var controlPlayerFocused by remember(state.entryRoomId, fullscreen) { mutableStateOf(false) }
  var gestureFeedback by remember { mutableStateOf<GestureIndicator?>(null) }
  var gestureFeedbackVisible by remember { mutableStateOf(false) }
  var gestureFeedbackVersion by remember { mutableLongStateOf(0L) }

  val sourceIndex = state.activeSourceIndex
  var bufferingForRecovery by
    remember(player, state.generation, sourceIndex, state.playback?.currentQn) {
      mutableStateOf(player?.playbackState == Player.STATE_BUFFERING)
    }
  DisposableEffect(player, state.generation, sourceIndex) {
    isPlaying = player?.isPlaying == true
    buffering = player.isLiveBuffering()
    bufferingForRecovery = player?.playbackState == Player.STATE_BUFFERING
    val listener =
      object : Player.Listener {
        override fun onIsPlayingChanged(value: Boolean) {
          isPlaying = value
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
          buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
          bufferingForRecovery = playbackState == Player.STATE_BUFFERING
          if (playbackState == Player.STATE_READY) onPlaybackReady(sourceIndex)
        }

        override fun onPlayerError(error: PlaybackException) {
          onPlaybackError(sourceIndex, error)
        }
      }
    player?.addListener(listener)
    onDispose { player?.removeListener(listener) }
  }
  LaunchedEffect(player, sourceIndex, state.playback?.currentQn, bufferingForRecovery) {
    val activePlayer = player ?: return@LaunchedEffect
    if (!bufferingForRecovery || !activePlayer.playWhenReady) return@LaunchedEffect
    delay(3_000L)
    if (activePlayer.playbackState == Player.STATE_BUFFERING && activePlayer.playWhenReady) {
      onPlaybackStall(sourceIndex)
    }
  }
  LaunchedEffect(player, state.playback?.currentQn) {
    while (true) {
      liveOffsetMs = player?.currentLiveOffset?.takeUnless { it == C.TIME_UNSET } ?: C.TIME_UNSET
      delay(1_000L)
    }
  }
  LaunchedEffect(controlMode, state.entryRoomId, fullscreen, controllerLevel) {
    if (controlMode && controllerLevel != LiveRoomControllerLevel.PAGE_NAVIGATION) {
      androidx.compose.runtime.withFrameNanos {}
      runCatching { playerFocusRequester.requestFocus() }
    }
  }
  LaunchedEffect(controllerLevel) {
    if (controllerLevel == LiveRoomControllerLevel.PLAYER_CONTROLS) {
      androidx.compose.runtime.withFrameNanos {}
      runCatching { controlActionFocusRequester.requestFocus() }
    }
  }
  LaunchedEffect(
    controlsVisible,
    qualityMenu,
    danmakuMenu,
    isPlaying,
    settings.controlsTimeoutSeconds,
  ) {
    if (
      controlsVisible &&
        isPlaying &&
        !qualityMenu &&
        !danmakuMenu &&
        (!controlMode || controlPlayerFocused)
    ) {
      delay(settings.controlsTimeoutSeconds * 1_000L)
      controlsVisible = false
    }
  }
  LaunchedEffect(gestureFeedbackVersion, gestureFeedback) {
    val version = gestureFeedbackVersion
    val displayed = gestureFeedback ?: return@LaunchedEffect
    delay(800L)
    if (gestureFeedbackVersion != version || gestureFeedback != displayed) return@LaunchedEffect
    gestureFeedbackVisible = false
    delay(140L)
    if (gestureFeedbackVersion == version && gestureFeedback == displayed) {
      gestureFeedback = null
    }
  }
  BackHandler(enabled = controlMode && controllerLevel != LiveRoomControllerLevel.PAGE_NAVIGATION) {
    if (controllerLevel == LiveRoomControllerLevel.PLAYER_CONTROLS) {
      onControllerLevelChanged(LiveRoomControllerLevel.PLAYER_DIRECT)
      runCatching { playerFocusRequester.requestFocus() }
    } else if (fullscreen) {
      onToggleFullscreen()
      onControlReturnToHeader()
    } else {
      onControlReturnToHeader()
    }
  }

  Surface(
    modifier =
      modifier
        .then(
          if (fullscreen) Modifier
          else
            Modifier.onGloballyPositioned { coordinates ->
              val bounds = coordinates.boundsInRoot()
              if (bounds.width > 0f && bounds.height > 0f) onPlayerBoundsChanged(bounds)
            }
        )
        .then(
          if (controlMode) {
            Modifier.focusRequester(playerFocusRequester)
              .onFocusChanged { controlPlayerFocused = it.isFocused }
              .controlFocusOutline(
                shape = if (fullscreen) RoundedCornerShape(0.dp) else VideoShapeTokens.Player,
                color = MaterialTheme.colorScheme.primary,
                width = 3.dp,
              )
              .onPreviewKeyEvent { event ->
                if (
                  !controlPlayerFocused ||
                    controllerLevel == LiveRoomControllerLevel.PAGE_NAVIGATION ||
                    controllerLevel == LiveRoomControllerLevel.PLAYER_CONTROLS ||
                    event.type != KeyEventType.KeyDown ||
                    event.nativeKeyEvent.repeatCount > 0
                ) {
                  return@onPreviewKeyEvent false
                }
                when {
                  isControlConfirmKey(event.nativeKeyEvent.keyCode) -> {
                    if (!fullscreen) {
                      onToggleFullscreen()
                    } else if (controlsVisible) {
                      if (player?.isPlaying == true) player.pause() else player?.play()
                    } else {
                      controlsVisible = true
                    }
                    true
                  }
                  event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                    onControlReturnToHeader()
                    true
                  }
                  event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU -> {
                    onControllerLevelChanged(LiveRoomControllerLevel.PLAYER_CONTROLS)
                    controlsVisible = true
                    true
                  }
                  event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                    onControllerLevelChanged(LiveRoomControllerLevel.PLAYER_CONTROLS)
                    controlsVisible = true
                    true
                  }
                  else -> false
                }
              }
              .focusable()
          } else Modifier
        ),
    shape = if (fullscreen) RoundedCornerShape(0.dp) else VideoShapeTokens.Player,
    color = Color.Black,
    shadowElevation = 0.dp,
  ) {
    Box(Modifier.fillMaxSize()) {
      playerView(Modifier.fillMaxSize(), fullscreenProgress, fullscreen)
      PlayerGestureLayer(
        enabledBrightness = settings.brightnessGesture,
        enabledVolume = settings.volumeGesture,
        enabledSeek = false,
        enabledFullscreenToggle = settings.twoFingerFullscreenGesture,
        positionProvider = { 0L },
        durationMs = 0L,
        onSeek = {},
        onIndicator = { indicator ->
          gestureFeedback = indicator
          gestureFeedbackVisible = true
          gestureFeedbackVersion += 1L
        },
        onSeekPreview = {},
        onSeekCancel = {},
        onToggleControls = { controlsVisible = !controlsVisible },
        onDoubleTap = {},
        onTemporarySpeedChanged = {},
        isFullscreen = fullscreen,
        onFullscreenChanged = { target ->
          if (target != fullscreen) onToggleFullscreen()
        },
        seekEdgeInset = 40.dp,
        enabledDoubleTap = false,
        enabledTemporarySpeed = false,
        enabledTwoFingerSeek = false,
        modifier = Modifier.fillMaxSize().zIndex(1.5f),
      )
      if (controlMode && controllerLevel == LiveRoomControllerLevel.PLAYER_CONTROLS) {
        ControllerPlaybackControls(
          title = state.roomInfo?.title ?: "直播间",
          positionMs = 0L,
          durationMs = 0L,
          isPlaying = isPlaying,
          actions = buildList {
            add(ControllerPlaybackActionItem("play", if (isPlaying) "暂停" else "播放"))
            add(ControllerPlaybackActionItem("refresh", "刷新"))
            if (liveOffsetMs != C.TIME_UNSET && liveOffsetMs > 8_000L) {
              add(ControllerPlaybackActionItem("live_edge", "回到直播"))
            }
            add(ControllerPlaybackActionItem("quality", "清晰度"))
            add(ControllerPlaybackActionItem("danmaku", "弹幕"))
            add(ControllerPlaybackActionItem("fullscreen", "全屏"))
          },
          overlay = dev.openbili.webdemo.video.ControllerPlaybackOverlay.ACTIONS,
          initialActionFocusRequester = controlActionFocusRequester,
          showProgress = false,
          statusText = "LIVE",
          onAction = { action ->
            when (action.key) {
              "play" -> if (player?.isPlaying == true) player.pause() else player?.play()
              "refresh" -> onRetryPlayback()
              "live_edge" -> onSeekLiveEdge()
              "quality" -> {
                qualityMenu = true
                controlsVisible = true
              }
              "danmaku" -> {
                danmakuMenu = true
                controlsVisible = true
              }
              "fullscreen" -> onToggleFullscreen()
            }
          },
          modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(6f),
        )
      }
      if ((state.playbackLoading || buffering) && state.playbackError == null) {
        CircularProgressIndicator(
          modifier = Modifier.align(Alignment.Center).size(34.dp),
          strokeWidth = 3.dp,
          color = Color.White,
        )
      }
      val visibleError = state.playbackError
      if (visibleError != null || state.roomInfo?.liveStatus == 0) {
        Column(
          modifier =
            Modifier.align(Alignment.Center)
              .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(18.dp))
              .padding(horizontal = 22.dp, vertical = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            if (state.roomInfo?.liveStatus == 0) "直播已结束" else visibleError.orEmpty(),
            color = Color.White,
          )
          if (state.roomInfo?.liveStatus != 0) {
            TextButton(onClick = onRetryPlayback) {
              Icon(Icons.Default.Refresh, contentDescription = null)
              Spacer(Modifier.width(4.dp))
              Text("重试")
            }
          }
        }
      }
      if (showDanmaku) {
        LiveDanmakuLayer(
          items = danmaku,
          startedAtElapsedMs = danmakuStartedAtElapsedMs,
          enabled = danmakuRenderingEnabled,
          paused = !isPlaying,
          transitionSuppressed = danmakuTransitionSuppressed,
          fullscreen = fullscreen,
          opacity = settings.liveDanmakuOpacity,
          displayArea = settings.liveDanmakuDisplayArea,
          densityLevel = settings.danmakuDensity,
          fontScale = settings.liveDanmakuFontScale,
          speed = settings.liveDanmakuSpeed,
          positionEpoch = state.generation,
          modifier = Modifier.fillMaxSize(),
        )
      }

      AnimatedVisibility(
        visible = controlsVisible,
        modifier = Modifier.align(Alignment.TopStart).zIndex(3f),
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(140)),
      ) {
        Row(
          modifier =
            Modifier.padding(12.dp)
              .background(Color.Black.copy(alpha = .56f), RoundedCornerShape(16.dp))
              .padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
          Box(Modifier.size(7.dp).background(Color(0xFFFF4D6A), CircleShape))
          Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelMedium)
          state.watchedText?.takeIf(String::isNotBlank)?.let {
            Text(
              it,
              color = Color.White.copy(alpha = .82f),
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      }

      AnimatedVisibility(
        visible = controlsVisible,
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(3f),
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(140)),
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))
              )
              .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          IconButton(
            onClick = {
              if (player?.isPlaying == true) player.pause() else player?.play()
              controlsVisible = true
            }
          ) {
            if (isPlaying) {
              Text("Ⅱ", color = Color.White, style = MaterialTheme.typography.titleLarge)
            } else {
              Icon(Icons.Default.PlayArrow, contentDescription = "播放", tint = Color.White)
            }
          }
          IconButton(
            onClick = {
              onRetryPlayback()
              controlsVisible = true
            }
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新直播", tint = Color.White)
          }
          if (liveOffsetMs != C.TIME_UNSET && liveOffsetMs > 8_000L) {
            TextButton(onClick = onSeekLiveEdge) { Text("回到直播", color = Color.White) }
          }
          Spacer(Modifier.weight(1f))
          Box {
            TextButton(
              onClick = {
                qualityMenu = true
                controlsVisible = true
              }
            ) {
              Text(
                state.playback
                  ?.qualities
                  ?.firstOrNull { it.qn == state.playback.currentQn }
                  ?.description ?: "清晰度",
                color = Color.White,
              )
            }
            DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
              state.playback?.qualities.orEmpty().forEach { quality ->
                DropdownMenuItem(
                  text = { Text(quality.description) },
                  onClick = {
                    qualityMenu = false
                    onQuality(quality.qn)
                  },
                )
              }
            }
          }
          Box {
            IconButton(
              onClick = {
                danmakuMenu = true
                controlsVisible = true
              }
            ) {
              DanmakuControlIcon(
                modifier = Modifier.size(25.dp),
                color =
                  if (showDanmaku) MaterialTheme.colorScheme.primary
                  else Color.White.copy(alpha = .72f),
              )
            }
            DropdownMenu(
              expanded = danmakuMenu,
              onDismissRequest = { danmakuMenu = false },
              modifier = Modifier.width(310.dp),
            ) {
              DropdownMenuItem(
                text = { Text(if (showDanmaku) "弹幕已开启" else "弹幕已关闭") },
                trailingIcon = { Checkbox(checked = showDanmaku, onCheckedChange = null) },
                onClick = { onShowDanmaku(!showDanmaku) },
              )
              DropdownMenuItem(
                text = {
                  Column {
                    Text("弹幕屏蔽词")
                    Text(
                      if (danmakuBlockWordCount == 0) "仅当前直播间 · 尚未设置"
                      else "仅当前直播间 · 已设置 $danmakuBlockWordCount 个",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                },
                onClick = {
                  danmakuMenu = false
                  onOpenDanmakuBlockWords()
                },
              )
              Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  "显示区域  ${liveDanmakuPercentLabel(settings.liveDanmakuDisplayArea)}",
                  style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                  value = settings.liveDanmakuDisplayArea,
                  onValueChange = { value ->
                    onSettingsChange { it.copy(liveDanmakuDisplayArea = value) }
                  },
                  valueRange = .1f..1f,
                  steps = 8,
                )
                Text(
                  "弹幕密度  ${settings.danmakuDensity} 级",
                  style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                  value = settings.danmakuDensity.toFloat(),
                  onValueChange = { value ->
                    onSettingsChange { it.copy(danmakuDensity = value.roundToInt()) }
                  },
                  valueRange = 1f..5f,
                  steps = 3,
                )
                Text(
                  "不透明度  ${liveDanmakuPercentLabel(settings.liveDanmakuOpacity)}",
                  style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                  value = settings.liveDanmakuOpacity,
                  onValueChange = { value ->
                    onSettingsChange { it.copy(liveDanmakuOpacity = value) }
                  },
                  valueRange = .2f..1f,
                  steps = 7,
                )
                Text(
                  "弹幕字号  ${liveDanmakuPercentLabel(settings.liveDanmakuFontScale)}",
                  style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                  value = settings.liveDanmakuFontScale,
                  onValueChange = { value ->
                    onSettingsChange { it.copy(liveDanmakuFontScale = value) }
                  },
                  valueRange = .7f..1.5f,
                  steps = 7,
                )
                Text(
                  "滚动速度  ${"%.1f".format(settings.liveDanmakuSpeed)}×",
                  style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                  value = settings.liveDanmakuSpeed,
                  onValueChange = { value ->
                    onSettingsChange { it.copy(liveDanmakuSpeed = value) }
                  },
                  valueRange = .6f..1.8f,
                  steps = 5,
                )
              }
            }
          }
          IconButton(onClick = onToggleFullscreen) {
            FullscreenControlIcon(
              exiting = fullscreen,
              modifier = Modifier.size(25.dp),
              color = Color.White,
            )
          }
        }
      }
      AnimatedVisibility(
        visible = gestureFeedbackVisible && gestureFeedback != null,
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
        gestureFeedback?.let { GestureIndicatorOverlay(it) }
      }
    }
  }
}

private fun liveDanmakuPercentLabel(value: Float): String = "${(value * 100f).roundToInt()}%"

@Composable
internal fun LiveDanmakuLayer(
  items: List<DanmakuItem>,
  startedAtElapsedMs: Long,
  enabled: Boolean,
  paused: Boolean,
  transitionSuppressed: Boolean,
  fullscreen: Boolean,
  opacity: Float,
  displayArea: Float,
  densityLevel: Int,
  fontScale: Float,
  speed: Float,
  positionEpoch: Long,
  modifier: Modifier = Modifier,
) {
  AndroidView(
    factory = { context -> DanmakuOverlayView(context) },
    modifier = modifier,
    update = { overlay ->
      overlay.update(
        items = items,
        mask = null,
        enabled = enabled,
        smartBlocking = false,
        paused = paused,
        fullscreen = fullscreen,
        highDynamicRange = false,
        opacity = opacity,
        displayArea = displayArea,
        densityLevel = densityLevel,
        blockLevel = 1,
        fontScale = fontScale,
        speed = speed,
        positionEpoch = positionEpoch,
        currentPositionProvider = {
          (android.os.SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0L)
        },
      )
      overlay.setTransitionSuppressed(transitionSuppressed)
    },
  )
}

@Composable
private fun LiveRecommendationSection(
  state: LiveRoomUiState,
  onRoom: (LiveSearchRoom, Rect) -> Unit,
  onRoomBoundsChanged: (LiveSearchRoom, Rect) -> Unit,
  hiddenCoverItemId: String?,
  onRetry: () -> Unit,
  cardWidth: androidx.compose.ui.unit.Dp,
  compactHorizontal: Boolean,
  compactHeight: androidx.compose.ui.unit.Dp,
  foregroundColor: Color,
  modifier: Modifier = Modifier,
) {
  CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides foregroundColor) {
    Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        "推荐直播",
        modifier = Modifier.padding(horizontal = 12.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        when {
          state.recommendationsLoading && state.recommendations.isEmpty() ->
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
          state.recommendationsError != null && state.recommendations.isEmpty() ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                state.recommendationsError,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              TextButton(onClick = onRetry) { Text("重试") }
            }
          state.recommendations.isEmpty() ->
            Text("暂时没有更多推荐", color = foregroundColor.copy(alpha = .72f))
          else ->
            LazyRow(
              modifier = Modifier.fillMaxSize(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              items(state.recommendations, key = LiveSearchRoom::stableId) { room ->
                val coverUrl = room.keyframeUrl ?: room.coverUrl.orEmpty()
                RecommendationCard(
                  item =
                    FeedItem(
                      id = room.stableId,
                      title = room.title,
                      videoUrl = "",
                      coverUrl = coverUrl,
                      uploader = room.uname,
                      playCount = null,
                      duration = null,
                    ),
                  coverVisible = room.stableId != hiddenCoverItemId,
                  onCoverBoundsChanged = { bounds -> onRoomBoundsChanged(room, bounds) },
                  onClick = { bounds -> onRoom(room, bounds) },
                  onLongClick = {},
                  cardWidth = cardWidth,
                  compactHorizontal = compactHorizontal,
                  compactHeight = compactHeight,
                )
              }
            }
        }
      }
    }
  }
}

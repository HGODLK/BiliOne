package dev.openbili.webdemo.live

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.DanmakuInlineEmote
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.video.AdaptiveVideoPanes
import dev.openbili.webdemo.video.BiliRichText
import dev.openbili.webdemo.video.CommentEmoteMarkerRegistry
import dev.openbili.webdemo.video.CommentTextEditor
import dev.openbili.webdemo.video.DanmakuControlIcon
import dev.openbili.webdemo.video.DanmakuOverlayView
import dev.openbili.webdemo.video.GestureIndicator
import dev.openbili.webdemo.video.GestureIndicatorOverlay
import dev.openbili.webdemo.video.FullscreenControlIcon
import dev.openbili.webdemo.video.PlayerGestureLayer
import dev.openbili.webdemo.video.PlaybackHeader
import dev.openbili.webdemo.video.PlaybackHeaderUiModel
import dev.openbili.webdemo.video.floatingPlayerLayout
import dev.openbili.webdemo.video.formatCompactCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun LiveRoomScreen(
  entry: LiveSearchRoom,
  account: UserInfo,
  player: ExoPlayer?,
  playerView: @Composable (Modifier, Float, Boolean) -> Unit,
  onPlaySource: (Long, LiveStreamSource) -> Unit,
  onStopPlayback: (Long) -> Unit,
  onSeekLiveEdge: () -> Unit,
  onBack: () -> Unit,
  onHome: () -> Unit,
  onLogin: () -> Unit,
  onAnchorProfile: (Long, String?, String?, Rect) -> Unit,
  settings: AppSettings,
  onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
  onPlayerBoundsChanged: (Rect) -> Unit = {},
  active: Boolean = true,
  viewModel: LiveRoomViewModel = viewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current
  val view = LocalView.current
  val activity = view.context as? Activity
  val window = activity?.window
  val fullscreenProgress = remember(entry.roomId) { Animatable(0f) }
  val fullscreenScope = rememberCoroutineScope()
  var fullscreenTransitionBusy by remember(entry.roomId) { mutableStateOf(false) }
  var fullscreenLayerVisible by remember(entry.roomId) { mutableStateOf(false) }
  var embeddedPlayerBounds by remember(entry.roomId) { mutableStateOf(Rect.Zero) }
  var frozenEmbeddedPlayerBounds by remember(entry.roomId) { mutableStateOf(Rect.Zero) }
  var showInfo by remember(entry.roomId) { mutableStateOf(false) }
  var showDanmaku by remember(entry.roomId) { mutableStateOf(true) }
  val danmakuStartedAtElapsedMs =
    remember(entry.roomId) { android.os.SystemClock.elapsedRealtime() }
  val liveDanmaku =
    remember(state.liveDanmaku, danmakuStartedAtElapsedMs) {
      state.liveDanmaku.map { event ->
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

  LaunchedEffect(entry.roomId) {
    viewModel.open(entry)
    viewModel.setForeground(active)
  }
  LaunchedEffect(active) {
    viewModel.setForeground(active)
    if (!active) player?.pause()
  }
  LaunchedEffect(account) { viewModel.updateAccount(account) }
  val source = state.playback?.sources?.getOrNull(state.activeSourceIndex)
  val realRoomId = state.roomInfo?.roomId ?: entry.roomId
  val latestRealRoomId by rememberUpdatedState(realRoomId)
  LaunchedEffect(realRoomId, state.playback?.currentQn, state.activeSourceIndex, source) {
    source?.let { onPlaySource(realRoomId, it) }
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
    fullscreenScope.launch {
      fullscreenProgress.animateTo(
        1f,
        tween(if (settings.reduceMotion) 100 else 360, easing = FastOutSlowInEasing),
      )
      fullscreenTransitionBusy = false
    }
  }
  fun exitFullscreenAnimated() {
    if (fullscreenTransitionBusy || !fullscreenLayerVisible) return
    fullscreenTransitionBusy = true
    fullscreenScope.launch {
      fullscreenProgress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
      )
      fullscreenLayerVisible = false
      fullscreenTransitionBusy = false
    }
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
        systemBarsBehavior =
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
              onShowDanmaku = { showDanmaku = it },
              danmaku = liveDanmaku,
              danmakuStartedAtElapsedMs = danmakuStartedAtElapsedMs,
              settings = settings,
              onSettingsChange = onSettingsChange,
              onPlayerBoundsChanged = { bounds ->
                embeddedPlayerBounds = bounds
                onPlayerBoundsChanged(bounds)
              },
              onToggleFullscreen = ::enterFullscreenAnimated,
              onQuality = viewModel::loadPlayback,
              onRetryPlayback = {
                viewModel.loadPlayback(state.playback?.currentQn ?: 10_000)
              },
              onAdvanceSource = viewModel::advancePlaybackSource,
              onSeekLiveEdge = onSeekLiveEdge,
              onRankTab = viewModel::selectRankTab,
              onAudienceRank = viewModel::selectAudienceRank,
              onGuardType = viewModel::selectGuardType,
              onLoadMoreGuards = viewModel::loadMoreGuards,
            )
          },
          secondary = {
            LiveMessagePane(
              state = state,
              account = account,
              onText = viewModel::setComposerText,
              onSend = viewModel::sendText,
              onToggleEmoji = viewModel::toggleEmojiPanel,
              onSelectEmojiPack = viewModel::selectEmojiPack,
              onEmoji = viewModel::sendEmoji,
              onJoinLottery = viewModel::joinInteractiveLottery,
              onLogin = onLogin,
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
        val fullscreenInsetPx = with(LocalDensity.current) { 8.dp.roundToPx() }
        val fullscreenCorner = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 14.dp else 0.dp
        Box(
          Modifier.fillMaxSize()
            .clipToBounds()
            .zIndex(100f)
        ) {
          LivePlayerCard(
            state = state,
            player = player,
            playerView = playerView,
            showDanmaku = showDanmaku,
            onShowDanmaku = { showDanmaku = it },
            danmaku = liveDanmaku,
            danmakuStartedAtElapsedMs = danmakuStartedAtElapsedMs,
            settings = settings,
            onSettingsChange = onSettingsChange,
            fullscreen = true,
            fullscreenProgress = progress,
            onToggleFullscreen = ::exitFullscreenAnimated,
            onQuality = viewModel::loadPlayback,
            onRetryPlayback = {
              viewModel.loadPlayback(state.playback?.currentQn ?: 10_000)
            },
            onAdvanceSource = viewModel::advancePlaybackSource,
            onSeekLiveEdge = onSeekLiveEdge,
            modifier =
              Modifier.fillMaxSize()
                .floatingPlayerLayout(
                  progress = progress,
                  sourceBounds = frozenEmbeddedPlayerBounds,
                  targetInsetPx = fullscreenInsetPx,
                )
                .graphicsLayer {
                  shape =
                    RoundedCornerShape(
                      VideoShapeTokens.CornerRadius * (1f - progress) +
                        fullscreenCorner * progress
                    )
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
}

@Composable
private fun LivePrimaryPane(
  state: LiveRoomUiState,
  player: ExoPlayer?,
  playerView: @Composable (Modifier, Float, Boolean) -> Unit,
  showPlayer: Boolean,
  showDanmaku: Boolean,
  onShowDanmaku: (Boolean) -> Unit,
  danmaku: List<DanmakuItem>,
  danmakuStartedAtElapsedMs: Long,
  settings: AppSettings,
  onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
  onPlayerBoundsChanged: (Rect) -> Unit,
  onToggleFullscreen: () -> Unit,
  onQuality: (Int) -> Unit,
  onRetryPlayback: () -> Unit,
  onAdvanceSource: () -> Unit,
  onSeekLiveEdge: () -> Unit,
  onRankTab: (LiveRankTab) -> Unit,
  onAudienceRank: (String, String) -> Unit,
  onGuardType: (Int) -> Unit,
  onLoadMoreGuards: () -> Unit,
) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val lowerHeight = (maxHeight * .32f).coerceIn(154.dp, 230.dp)
    val playerHeight =
      (maxWidth * 9f / 16f).coerceAtMost((maxHeight - lowerHeight - 8.dp).coerceAtLeast(112.dp))
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
            danmaku = danmaku,
            danmakuStartedAtElapsedMs = danmakuStartedAtElapsedMs,
            settings = settings,
            onSettingsChange = onSettingsChange,
            onPlayerBoundsChanged = onPlayerBoundsChanged,
            fullscreen = false,
            fullscreenProgress = 0f,
            onToggleFullscreen = onToggleFullscreen,
            onQuality = onQuality,
            onRetryPlayback = onRetryPlayback,
            onAdvanceSource = onAdvanceSource,
            onSeekLiveEdge = onSeekLiveEdge,
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
      LiveRankSection(
        state = state,
        onRankTab = onRankTab,
        onAudienceRank = onAudienceRank,
        onGuardType = onGuardType,
        onLoadMoreGuards = onLoadMoreGuards,
        modifier = Modifier.fillMaxWidth().weight(1f),
      )
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
  danmaku: List<DanmakuItem>,
  danmakuStartedAtElapsedMs: Long,
  settings: AppSettings,
  onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
  fullscreen: Boolean,
  fullscreenProgress: Float,
  onToggleFullscreen: () -> Unit,
  onQuality: (Int) -> Unit,
  onRetryPlayback: () -> Unit,
  onAdvanceSource: () -> Unit,
  onSeekLiveEdge: () -> Unit,
  onPlayerBoundsChanged: (Rect) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var isPlaying by remember(player) { mutableStateOf(player?.isPlaying == true) }
  var buffering by remember(player) { mutableStateOf(player.isLiveBuffering()) }
  var playerError by remember(state.entryRoomId) { mutableStateOf<String?>(null) }
  var qualityMenu by remember { mutableStateOf(false) }
  var danmakuMenu by remember { mutableStateOf(false) }
  var liveOffsetMs by remember { mutableLongStateOf(C.TIME_UNSET) }
  var controlsVisible by remember(state.entryRoomId) { mutableStateOf(true) }
  var gestureFeedback by remember { mutableStateOf<GestureIndicator?>(null) }
  var gestureFeedbackVisible by remember { mutableStateOf(false) }
  var gestureFeedbackVersion by remember { mutableLongStateOf(0L) }

  DisposableEffect(player, state.generation) {
    isPlaying = player?.isPlaying == true
    buffering = player.isLiveBuffering()
    val listener =
      object : Player.Listener {
        override fun onIsPlayingChanged(value: Boolean) {
          isPlaying = value
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
          buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
          if (playbackState == Player.STATE_READY) playerError = null
        }

        override fun onPlayerError(error: PlaybackException) {
          playerError = "当前线路播放失败，正在切换"
          onAdvanceSource()
        }
      }
    player?.addListener(listener)
    onDispose { player?.removeListener(listener) }
  }
  LaunchedEffect(player, state.playback?.currentQn) {
    while (true) {
      liveOffsetMs = player?.currentLiveOffset?.takeUnless { it == C.TIME_UNSET } ?: C.TIME_UNSET
      delay(1_000L)
    }
  }
  LaunchedEffect(
    controlsVisible,
    qualityMenu,
    danmakuMenu,
    isPlaying,
    settings.controlsTimeoutSeconds,
  ) {
    if (controlsVisible && isPlaying && !qualityMenu && !danmakuMenu) {
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

  Surface(
    modifier =
      modifier.then(
        if (fullscreen) Modifier
        else
          Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInRoot()
            if (bounds.width > 0f && bounds.height > 0f) onPlayerBoundsChanged(bounds)
          }
      ),
    shape = if (fullscreen) RoundedCornerShape(0.dp) else VideoShapeTokens.Player,
    color = Color.Black,
    shadowElevation = if (fullscreen) 0.dp else 5.dp,
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
      if (
        (state.playbackLoading || buffering) && state.playbackError == null && playerError == null
      ) {
        CircularProgressIndicator(
          modifier = Modifier.align(Alignment.Center).size(34.dp),
          strokeWidth = 3.dp,
          color = Color.White,
        )
      }
      val visibleError = state.playbackError ?: playerError
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
          paused = !isPlaying,
          fullscreen = fullscreen,
          opacity = settings.danmakuOpacity,
          displayArea = settings.danmakuDisplayArea,
          fontScale = settings.danmakuFontScale,
          speed = settings.danmakuSpeed,
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
              Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  "显示区域  ${(settings.danmakuDisplayArea * 100).toInt()}%",
                  style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                  value = settings.danmakuDisplayArea,
                  onValueChange = { value ->
                    onSettingsChange { it.copy(danmakuDisplayArea = value) }
                  },
                  valueRange = .25f..1f,
                  steps = 2,
                )
                Text(
                  "不透明度  ${(settings.danmakuOpacity * 100).toInt()}%",
                  style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                  value = settings.danmakuOpacity,
                  onValueChange = { value ->
                    onSettingsChange { it.copy(danmakuOpacity = value) }
                  },
                  valueRange = .2f..1f,
                  steps = 7,
                )
                Text(
                  "弹幕字号  ${(settings.danmakuFontScale * 100).toInt()}%",
                  style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                  value = settings.danmakuFontScale,
                  onValueChange = { value ->
                    onSettingsChange { it.copy(danmakuFontScale = value) }
                  },
                  valueRange = .7f..1.5f,
                  steps = 7,
                )
                Text(
                  "滚动速度  ${"%.1f".format(settings.danmakuSpeed)}×",
                  style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                  value = settings.danmakuSpeed,
                  onValueChange = { value ->
                    onSettingsChange { it.copy(danmakuSpeed = value) }
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
        modifier = Modifier.align(Alignment.Center).zIndex(4f),
        enter = fadeIn(tween(90)),
        exit = fadeOut(tween(140)),
      ) {
        gestureFeedback?.let { GestureIndicatorOverlay(it) }
      }
    }
  }
}

@Composable
private fun LiveDanmakuLayer(
  items: List<DanmakuItem>,
  startedAtElapsedMs: Long,
  paused: Boolean,
  fullscreen: Boolean,
  opacity: Float,
  displayArea: Float,
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
        enabled = true,
        smartBlocking = false,
        paused = paused,
        fullscreen = fullscreen,
        highDynamicRange = false,
        opacity = opacity,
        displayArea = displayArea,
        densityLevel = LIVE_DANMAKU_DENSITY,
        fontScale = fontScale,
        speed = speed,
        positionEpoch = positionEpoch,
        currentPositionProvider = {
          (android.os.SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0L)
        },
      )
    },
  )
}

private data class AudienceRankOption(
  val title: String,
  val type: String,
  val switch: String,
)

private val audienceRankOptions =
  listOf(
    AudienceRankOption("在线·贡献", "online_rank", "contribution_rank"),
    AudienceRankOption("在线·进房", "online_rank", "entry_time_rank"),
    AudienceRankOption("今日", "daily_rank", "today_rank"),
    AudienceRankOption("昨日", "daily_rank", "yesterday_rank"),
    AudienceRankOption("本周", "weekly_rank", "current_week_rank"),
    AudienceRankOption("上周", "weekly_rank", "last_week_rank"),
    AudienceRankOption("本月", "monthly_rank", "current_month_rank"),
    AudienceRankOption("上月", "monthly_rank", "last_month_rank"),
  )

@Composable
private fun LiveRankSection(
  state: LiveRoomUiState,
  onRankTab: (LiveRankTab) -> Unit,
  onAudienceRank: (String, String) -> Unit,
  onGuardType: (Int) -> Unit,
  onLoadMoreGuards: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = VideoShapeTokens.Card,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp)) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        items(LiveRankTab.entries) { tab ->
          FilterChip(
            selected = state.rankTab == tab,
            onClick = { onRankTab(tab) },
            label = {
              val suffix =
                when (tab) {
                  LiveRankTab.AUDIENCE -> state.audienceRank.countText
                  LiveRankTab.GUARD ->
                    state.guardRank.totalCount.takeIf { it > 0 }?.toString().orEmpty()
                }
              Text(if (suffix.isBlank()) tab.title else "${tab.title} $suffix")
            },
          )
        }
        if (state.rankTab == LiveRankTab.AUDIENCE) {
          items(audienceRankOptions) { option ->
            FilterChip(
              selected =
                state.audienceRank.type == option.type &&
                  state.audienceRank.switch == option.switch,
              onClick = { onAudienceRank(option.type, option.switch) },
              label = { Text(option.title) },
            )
          }
        } else {
          items(listOf(3 to "周榜", 4 to "月榜", 5 to "陪伴榜")) { (type, label) ->
            FilterChip(
              selected = state.guardRank.typ == type,
              onClick = { onGuardType(type) },
              label = { Text(label) },
            )
          }
        }
      }
      val loading =
        if (state.rankTab == LiveRankTab.AUDIENCE) state.audienceRank.isLoading
        else state.guardRank.isLoading && state.guardRank.items.isEmpty()
      val error =
        if (state.rankTab == LiveRankTab.AUDIENCE) state.audienceRank.error
        else state.guardRank.error
      val users =
        if (state.rankTab == LiveRankTab.AUDIENCE) state.audienceRank.items
        else state.guardRank.items
      Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        when {
          loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
          error != null && users.isEmpty() -> Text(error, color = MaterialTheme.colorScheme.error)
          users.isEmpty() -> Text("暂时没有榜单数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
          else ->
            LazyRow(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(top = 5.dp),
              horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
              itemsIndexed(users, key = { _, user -> user.uid }) { index, user ->
                LiveRankUserCard(user)
                if (state.rankTab == LiveRankTab.GUARD && index >= users.lastIndex - 3) {
                  LaunchedEffect(user.uid, state.guardRank.nextPage) { onLoadMoreGuards() }
                }
              }
              if (state.rankTab == LiveRankTab.GUARD && state.guardRank.isLoading) {
                item {
                  Box(Modifier.width(50.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                  }
                }
              }
            }
        }
      }
    }
  }
}

@Composable
private fun LiveRankUserCard(user: LiveRankUser) {
  Surface(
    modifier = Modifier.width(188.dp).fillMaxHeight(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Box(contentAlignment = Alignment.BottomEnd) {
        AsyncImage(
          model = user.faceUrl,
          contentDescription = user.name,
          modifier =
            Modifier.size(44.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant),
          contentScale = ContentScale.Crop,
        )
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
          Text(
            "#${user.rank}",
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          user.name,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        user.fanMedal?.let { FanMedalChip(it) }
        val detail =
          when {
            user.accompanyDays != null -> "陪伴 ${user.accompanyDays} 天"
            user.score != null -> "${formatCompactCount(user.score)} 贡献"
            user.guardLevel != null -> guardLevelName(user.guardLevel)
            else -> null
          }
        detail?.let {
          Text(
            it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
private fun LiveMessagePane(
  state: LiveRoomUiState,
  account: UserInfo,
  onText: (String, Int) -> Unit,
  onSend: () -> Unit,
  onToggleEmoji: () -> Unit,
  onSelectEmojiPack: (String) -> Unit,
  onEmoji: (LiveEmoji) -> Unit,
  onJoinLottery: () -> Unit,
  onLogin: () -> Unit,
) {
  val listState = rememberLazyListState()
  val lastMessageId = state.messages.lastOrNull()?.stableId
  LaunchedEffect(lastMessageId) {
    if (state.messages.isNotEmpty()) {
      listState.animateScrollToItem(state.messages.lastIndex)
    }
  }
  Surface(
    Modifier.fillMaxSize(),
    shape = VideoShapeTokens.Card,
    color = MaterialTheme.colorScheme.background,
    tonalElevation = 2.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(Modifier.fillMaxSize()) {
      LiveMessageHeader(state.connectionError)
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        items(state.messages, key = LiveChatMessage::stableId) { message ->
          LiveMessageCard(message)
        }
      }
      state.interactiveLottery?.let { lottery ->
        LiveInteractiveLotteryCard(
          lottery = lottery,
          onJoin = onJoinLottery,
        )
      }
      if (state.composer.emojiPanelVisible) {
        LiveEmojiPanel(
          state = state,
          onSelectPack = onSelectEmojiPack,
          onEmoji = onEmoji,
        )
      }
      Box(Modifier.imePadding().navigationBarsPadding()) {
        LiveComposer(
          state = state,
          account = account,
          onText = onText,
          onSend = onSend,
          onToggleEmoji = onToggleEmoji,
          onLogin = onLogin,
        )
      }
    }
  }
}

@Composable
private fun LiveMessageHeader(error: String?) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
    Text(
      "直播消息",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    error?.takeIf(String::isNotBlank)?.let {
      Text(
        it,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        maxLines = 2,
      )
    }
  }
}

@Composable
private fun LiveInteractiveLotteryCard(
  lottery: LiveInteractiveLottery,
  onJoin: () -> Unit,
) {
  var remainingMs by remember(lottery.id, lottery.endAtEpochMs) {
    mutableLongStateOf((lottery.endAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L))
  }
  LaunchedEffect(lottery.id, lottery.endAtEpochMs, lottery.status) {
    while (
      remainingMs > 0L &&
        lottery.status in setOf(
          LiveLotteryStatus.ACTIVE,
          LiveLotteryStatus.JOINING,
          LiveLotteryStatus.JOINED,
        )
    ) {
      delay(1_000L)
      remainingMs = (lottery.endAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
    }
  }
  val statusText =
    when (lottery.status) {
      LiveLotteryStatus.ACTIVE ->
        if (remainingMs > 0L) "剩余 ${((remainingMs + 999L) / 1_000L)} 秒" else "等待开奖"
      LiveLotteryStatus.JOINING -> "正在参与"
      LiveLotteryStatus.JOINED -> "已参与，等待开奖"
      LiveLotteryStatus.ENDED -> "已结束"
      LiveLotteryStatus.AWARDED -> "已开奖"
      LiveLotteryStatus.INVALID -> "已失效"
    }
  Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .72f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      if (!lottery.awardImageUrl.isNullOrBlank()) {
        AsyncImage(
          model = lottery.awardImageUrl,
          contentDescription = lottery.awardName,
          modifier = Modifier.size(42.dp),
          contentScale = ContentScale.Fit,
        )
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          "天选时刻 · ${lottery.awardName} ×${lottery.awardNum}",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          lottery.requireText.ifBlank {
            lottery.command.takeIf(String::isNotBlank) ?: "发送指定弹幕参与"
          },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
          maxLines = 2,
        )
        Text(
          lottery.error ?: statusText,
          style = MaterialTheme.typography.labelSmall,
          color =
            if (lottery.error == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error,
          maxLines = 2,
        )
      }
      if (lottery.status == LiveLotteryStatus.ACTIVE) {
        TextButton(
          onClick = onJoin,
          enabled = remainingMs > 0L && !lottery.requiresPayment,
        ) {
          Text(if (lottery.requiresPayment) "不支持付费参与" else "参与")
        }
      }
    }
  }
}

@Composable
private fun LiveMessageCard(message: LiveChatMessage) {
  val pending = message.delivery is LiveMessageDelivery.Pending
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color =
      if (message.content is LiveChatContent.System)
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .58f)
      else MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    if (message.content is LiveChatContent.System) {
      Text(
        message.content.text,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    } else {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AsyncImage(
          model = message.faceUrl,
          contentDescription = message.uname,
          modifier =
            Modifier.size(34.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant),
          contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              message.uname ?: "用户",
              modifier = Modifier.weight(1f, fill = false),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            message.fanMedal?.let { FanMedalChip(it) }
            if (pending) {
              CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            }
          }
          when (val content = message.content) {
            is LiveChatContent.Text ->
              BiliRichText(
                text = content.text,
                emotes =
                  content.emotes.mapValues { (token, url) ->
                    BiliEmote(text = token, url = url)
                  },
                style = MaterialTheme.typography.bodyMedium,
              )
            is LiveChatContent.Emoji ->
              Column {
                AsyncImage(
                  model = content.imageUrl,
                  contentDescription = content.displayName,
                  modifier = Modifier.size(if (content.isBulge) 76.dp else 44.dp),
                  contentScale = ContentScale.Fit,
                )
                if (content.imageUrl.isNullOrBlank()) {
                  Text(content.displayName, style = MaterialTheme.typography.bodyMedium)
                }
              }
            is LiveChatContent.System -> Unit
          }
        }
      }
    }
  }
}

@Composable
private fun FanMedalChip(medal: FanMedalBadge) {
  val start = liveColor(medal.startColor ?: medal.color, MaterialTheme.colorScheme.primary)
  val end = liveColor(medal.endColor ?: medal.borderColor ?: medal.color, start)
  Text(
    "${medal.name} ${medal.level}",
    modifier =
      Modifier.clip(RoundedCornerShape(7.dp))
        .background(Brush.horizontalGradient(listOf(start, end)))
        .padding(horizontal = 6.dp, vertical = 2.dp),
    color = Color.White,
    style = MaterialTheme.typography.labelSmall,
    maxLines = 1,
  )
}

@Composable
private fun LiveEmojiPanel(
  state: LiveRoomUiState,
  onSelectPack: (String) -> Unit,
  onEmoji: (LiveEmoji) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 236.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 4.dp,
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(state.emojiPacks, key = LiveEmojiPack::id) { pack ->
          FilterChip(
            selected = state.composer.selectedEmojiPackId == pack.id,
            onClick = { onSelectPack(pack.id) },
            label = {
              Text(
                when (pack.kind) {
                  LiveEmojiKind.ROOM_EXCLUSIVE -> "${pack.title ?: "专属"} · 本房"
                  else -> pack.title ?: "表情"
                }
              )
            },
          )
        }
      }
      val selected =
        state.emojiPacks.firstOrNull { it.id == state.composer.selectedEmojiPackId }
          ?: state.emojiPacks.firstOrNull()
      when {
        state.emojiLoading ->
          Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
          }
        state.emojiError != null ->
          Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(state.emojiError, color = MaterialTheme.colorScheme.error)
          }
        selected == null ->
          Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text("暂无可用表情")
          }
        else ->
          LazyVerticalGrid(
            columns = GridCells.Adaptive(48.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            gridItems(selected.emojis, key = { it.fileId ?: it.sendToken }) { emoji ->
              Column(
                modifier =
                  Modifier.clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = emoji.available) { onEmoji(emoji) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                AsyncImage(
                  model = emoji.imageUrl,
                  contentDescription = emoji.displayName,
                  modifier = Modifier.size(38.dp),
                  contentScale = ContentScale.Fit,
                  alpha = if (emoji.available) 1f else .35f,
                )
                Text(
                  emoji.displayName,
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
      }
    }
  }
}

@Composable
private fun LiveComposer(
  state: LiveRoomUiState,
  account: UserInfo,
  onText: (String, Int) -> Unit,
  onSend: () -> Unit,
  onToggleEmoji: () -> Unit,
  onLogin: () -> Unit,
) {
  val editorState = rememberTextFieldState()
  val focusRequester = remember { FocusRequester() }
  val emoteRegistry = remember(state.entryRoomId) { CommentEmoteMarkerRegistry() }
  val inputEmotes =
    remember(state.emojiPacks) {
      state.emojiPacks
        .flatMap(LiveEmojiPack::emojis)
        .filter { !it.directSend && it.sendToken.isNotBlank() && it.imageUrl.isNotBlank() }
        .distinctBy(LiveEmoji::sendToken)
        .map { BiliEmote(text = it.sendToken, url = it.imageUrl) }
    }
  val markerSnapshot = remember(inputEmotes) { emoteRegistry.snapshot(inputEmotes) }
  val latestComposer by rememberUpdatedState(state.composer)
  val latestOnText by rememberUpdatedState(onText)
  LaunchedEffect(state.composer.text, state.composer.selectionStart, markerSnapshot) {
    val encoded = markerSnapshot.encode(state.composer.text)
    val selection =
      markerSnapshot.encodedOffset(state.composer.text, state.composer.selectionStart)
        .coerceIn(0, encoded.length)
    if (
      editorState.text.toString() != encoded ||
        editorState.selection.start != selection ||
        editorState.selection.end != selection
    ) {
      editorState.edit {
        replace(0, length, encoded)
        this.selection = TextRange(selection)
      }
    }
  }
  LaunchedEffect(editorState, markerSnapshot) {
    snapshotFlow { editorState.text.toString() to editorState.selection.start }
      .collectLatest { (encoded, selection) ->
        val decoded = markerSnapshot.decode(encoded)
        val decodedSelection = markerSnapshot.decodedOffset(encoded, selection)
        if (
          decoded != latestComposer.text ||
            decodedSelection != latestComposer.selectionStart
        ) {
          latestOnText(decoded, decodedSelection)
        }
      }
  }
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 5.dp,
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp)) {
      state.composer.error?.let {
        Text(
          it,
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
          maxLines = 2,
        )
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        state.activeMedal?.let { FanMedalChip(it) }
        IconButton(onClick = onToggleEmoji) {
          Text("☺", style = MaterialTheme.typography.titleLarge)
        }
        Surface(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          CommentTextEditor(
            state = editorState,
            placeholder = if (account.isLogin) "发个弹幕呗~" else "登录后发送弹幕",
            emoteMarkers = markerSnapshot.markerToEmote,
            focusRequester = focusRequester,
            enabled = account.isLogin && !state.composer.sending,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp, max = 54.dp),
          )
        }
        FilledIconButton(
          onClick = if (account.isLogin) onSend else onLogin,
          enabled =
            !state.composer.sending && (!account.isLogin || state.composer.text.isNotBlank()),
        ) {
          if (state.composer.sending) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
          }
        }
      }
    }
  }
}

@Composable
private fun LiveRoomInfoDialog(
  state: LiveRoomUiState,
  onDismiss: () -> Unit,
) {
  val room = state.roomInfo
  val anchor = state.anchorInfo
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(.72f).heightIn(max = 580.dp),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 8.dp,
      shadowElevation = 18.dp,
    ) {
      Column(
        Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(
          room?.title ?: "直播间",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          AsyncImage(
            model = anchor?.faceUrl,
            contentDescription = anchor?.name,
            modifier =
              Modifier.size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
          )
          Text(
            anchor?.name ?: "主播",
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.titleMedium,
          )
        }
        Text(
          listOfNotNull(room?.parentAreaName, room?.areaName, room?.roomId?.let { "房间号 $it" })
            .joinToString("  ·  "),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          room?.description?.ifBlank { "这个直播间暂时没有填写简介。" } ?: "这个直播间暂时没有填写简介。",
          modifier = Modifier.weight(1f, fill = false),
          style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
          Text("关闭")
        }
      }
    }
  }
}

private fun liveColor(value: Long?, fallback: Color): Color =
  value?.takeIf { it > 0L }?.let { Color((0xff000000L or (it and 0x00ffffffL)).toInt()) }
    ?: fallback

private fun guardLevelName(level: Int): String =
  when (level) {
    1 -> "总督"
    2 -> "提督"
    3 -> "舰长"
    else -> "大航海"
  }

private const val LIVE_DANMAKU_DENSITY = 3

private fun Player?.isLiveBuffering(): Boolean =
  this == null || playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING

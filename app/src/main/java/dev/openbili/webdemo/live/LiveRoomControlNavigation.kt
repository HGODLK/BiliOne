package dev.openbili.webdemo.live

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.video.ControllerPlaybackActionItem
import dev.openbili.webdemo.video.ControllerDanmakuSettingsPanel
import dev.openbili.webdemo.video.ControllerPlaybackControls
import dev.openbili.webdemo.video.ControllerPlaybackKeyAction
import dev.openbili.webdemo.video.ControllerPlaybackOverlay
import dev.openbili.webdemo.video.ControllerPlaybackPanel
import dev.openbili.webdemo.video.ControllerPlaybackSidePanel
import dev.openbili.webdemo.video.GestureIndicator
import dev.openbili.webdemo.video.PlayerGestureLayer
import dev.openbili.webdemo.video.resolveControllerPlaybackKeyAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 设置开启后直播间原页面的三态焦点层级。 */
internal enum class LiveRoomControllerLevel {
  PAGE_NAVIGATION,
  PLAYER_DIRECT,
  PLAYER_CONTROLS,
}

/** 设置开启时，直播间仍保留原页面，但把播放器直控与操作行补成完整三态。 */
@Composable
internal fun LiveRoomControllerPlaybackScreen(
  entry: LiveSearchRoom,
  state: LiveRoomUiState,
  account: UserInfo,
  player: ExoPlayer?,
  playerView: @Composable (Modifier, Float, Boolean) -> Unit,
  firstFrameRendered: Boolean,
  showDanmaku: Boolean,
  danmakuBlockWordCount: Int,
  danmaku: List<dev.openbili.webdemo.api.DanmakuItem>,
  danmakuStartedAtElapsedMs: Long,
  danmakuRenderingEnabled: Boolean,
  danmakuTransitionSuppressed: Boolean,
  settings: AppSettings,
  onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
  onShowDanmaku: (Boolean) -> Unit,
  onOpenDanmakuBlockWords: () -> Unit,
  onQuality: (Int) -> Unit,
  onRetryPlayback: () -> Unit,
  onPlaybackError: (Int, PlaybackException) -> Unit,
  onPlaybackReady: (Int) -> Unit,
  onPlaybackStall: (Int) -> Unit,
  onSeekLiveEdge: () -> Unit,
  onPlayerBoundsChanged: (Rect) -> Unit,
  onBack: () -> Unit,
  onText: (String, Int) -> Unit,
  onSend: () -> Unit,
  onToggleEmoji: () -> Unit,
  onSelectEmojiPack: (String) -> Unit,
  onEmoji: (LiveEmoji) -> Unit,
  onJoinLottery: () -> Unit,
  onLogin: () -> Unit,
  onRankTab: (LiveRankTab) -> Unit,
  onAudienceRank: (String, String) -> Unit,
  onGuardType: (Int) -> Unit,
  onLoadMoreGuards: () -> Unit,
  glassBackdrop: dev.openbili.webdemo.video.PlaybackPageGlassBackdrop,
) {
  val focusRequester = remember(entry.roomId) { FocusRequester() }
  val actionFocusRequester = remember(entry.roomId) { FocusRequester() }
  val coverAlpha = remember(entry.roomId, state.generation) { Animatable(1f) }
  val scope = rememberCoroutineScope()
  val firstFrameVisible = firstFrameRendered
  var overlay by remember(entry.roomId, state.generation) {
    mutableStateOf(ControllerPlaybackOverlay.HIDDEN)
  }
  var panel by remember(entry.roomId, state.generation) {
    mutableStateOf(ControllerPlaybackPanel.NONE)
  }
  var displayedLiveOffsetMs by remember(entry.roomId, state.generation) {
    mutableLongStateOf(C.TIME_UNSET)
  }
  var isPlaying by remember(player) { mutableStateOf(player?.isPlaying == true) }
  var buffering by remember(player) { mutableStateOf(player.isLiveBuffering()) }
  var bufferingForRecovery by
    remember(player, state.generation, state.activeSourceIndex, state.playback?.currentQn) {
      mutableStateOf(player?.playbackState == Player.STATE_BUFFERING)
    }
  var feedback by remember(entry.roomId, state.generation) { mutableStateOf<String?>(null) }

  DisposableEffect(player, state.generation, state.activeSourceIndex, state.playback?.currentQn) {
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
          if (playbackState == Player.STATE_READY) onPlaybackReady(state.activeSourceIndex)
        }

        override fun onPlayerError(error: PlaybackException) {
          onPlaybackError(state.activeSourceIndex, error)
        }
      }
    player?.addListener(listener)
    onDispose { player?.removeListener(listener) }
  }
  LaunchedEffect(
    player,
    state.activeSourceIndex,
    state.playback?.currentQn,
    bufferingForRecovery,
  ) {
    val activePlayer = player ?: return@LaunchedEffect
    if (!bufferingForRecovery || !activePlayer.playWhenReady) return@LaunchedEffect
    delay(3_000L)
    if (activePlayer.playbackState == Player.STATE_BUFFERING && activePlayer.playWhenReady) {
      onPlaybackStall(state.activeSourceIndex)
    }
  }

  LaunchedEffect(player, state.playback?.currentQn) {
    while (true) {
      displayedLiveOffsetMs = player?.currentLiveOffset ?: C.TIME_UNSET
      delay(1_000L)
    }
  }
  LaunchedEffect(firstFrameVisible, state.playbackError, entry.roomId, state.generation) {
    if (!firstFrameVisible || state.playbackError != null) coverAlpha.snapTo(1f)
    else coverAlpha.animateTo(0f, tween(240))
  }
  LaunchedEffect(entry.roomId, state.generation) {
    androidx.compose.runtime.withFrameNanos {}
    runCatching { focusRequester.requestFocus() }
  }
  LaunchedEffect(overlay, panel, isPlaying) {
    if (overlay == ControllerPlaybackOverlay.INFO && panel == ControllerPlaybackPanel.NONE && isPlaying) {
      delay(4_000L)
      if (overlay == ControllerPlaybackOverlay.INFO && panel == ControllerPlaybackPanel.NONE) {
        overlay = ControllerPlaybackOverlay.HIDDEN
      }
    }
  }
  LaunchedEffect(overlay) {
    when (overlay) {
      ControllerPlaybackOverlay.ACTIONS -> {
        androidx.compose.runtime.withFrameNanos {}
        runCatching { actionFocusRequester.requestFocus() }
      }
      ControllerPlaybackOverlay.INFO -> runCatching { focusRequester.requestFocus() }
      else -> Unit
    }
  }

  val title = state.roomInfo?.title ?: entry.title
  val ownerName = state.anchorInfo?.name ?: entry.uname
  val qualityLabel =
    state.playback?.qualities?.firstOrNull { it.qn == state.playback.currentQn }?.description
      ?: "清晰度未知"
  val liveStatus =
    buildList {
        add("LIVE")
        ownerName.takeIf(String::isNotBlank)?.let(::add)
        state.watchedText?.takeIf(String::isNotBlank)?.let(::add)
        add(qualityLabel)
      }
      .joinToString("  ·  ")
  val actionItems = buildList {
    add(ControllerPlaybackActionItem("danmaku", "弹幕"))
    add(ControllerPlaybackActionItem("quality", "清晰度"))
  }
  val qualityItems =
    state.playback?.qualities.orEmpty().map { quality ->
      ControllerPlaybackActionItem("quality:${quality.qn}", quality.description)
    }
  val moreItems = buildList {
    add(ControllerPlaybackActionItem("refresh", "刷新播放"))
    if (displayedLiveOffsetMs > 8_000L) add(ControllerPlaybackActionItem("live_edge", "回到直播"))
  }

  fun showPanel(target: ControllerPlaybackPanel) {
    panel = target
    overlay = ControllerPlaybackOverlay.PANEL
  }

  fun showFeedback(text: String) {
    feedback = text
    overlay = ControllerPlaybackOverlay.INFO
    scope.launch {
      delay(900L)
      feedback = null
    }
  }

  fun handleBack() {
    when {
      panel != ControllerPlaybackPanel.NONE -> {
        panel = ControllerPlaybackPanel.NONE
        overlay = ControllerPlaybackOverlay.INFO
        runCatching { focusRequester.requestFocus() }
      }
      overlay != ControllerPlaybackOverlay.HIDDEN -> {
        overlay = ControllerPlaybackOverlay.HIDDEN
        runCatching { focusRequester.requestFocus() }
      }
      else -> onBack()
    }
  }

  androidx.activity.compose.BackHandler(onBack = ::handleBack)

  Box(
    Modifier.fillMaxSize()
      .background(Color.Black)
      .onGloballyPositioned { onPlayerBoundsChanged(it.boundsInRoot()) }
      .focusRequester(focusRequester)
      .onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (
          overlay == ControllerPlaybackOverlay.ACTIONS &&
            (native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
              native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
        ) {
          if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
            overlay = ControllerPlaybackOverlay.INFO
          }
          return@onPreviewKeyEvent true
        }
        // 菜单子项已经获得焦点；除上下键的层级切换外，其余按键交给子项处理。
        if (overlay == ControllerPlaybackOverlay.ACTIONS) {
          return@onPreviewKeyEvent false
        }
        if (overlay == ControllerPlaybackOverlay.PANEL) {
          return@onPreviewKeyEvent false
        }
        val action =
          resolveControllerPlaybackKeyAction(
            keyCode = native.keyCode,
            keyUp = event.type == KeyEventType.KeyUp,
            repeatCount = native.repeatCount,
            overlay = overlay,
            isLive = true,
          )
        when (action) {
          ControllerPlaybackKeyAction.TOGGLE_PLAY -> {
            if (player?.isPlaying == true) player.pause() else player?.play()
            overlay = ControllerPlaybackOverlay.INFO
          }
          ControllerPlaybackKeyAction.SHOW_INFO -> {
            overlay = ControllerPlaybackOverlay.INFO
            panel = ControllerPlaybackPanel.NONE
          }
          ControllerPlaybackKeyAction.SHOW_ACTIONS -> {
            overlay = ControllerPlaybackOverlay.ACTIONS
            panel = ControllerPlaybackPanel.NONE
          }
          ControllerPlaybackKeyAction.OPEN_MORE -> showPanel(ControllerPlaybackPanel.MORE)
          ControllerPlaybackKeyAction.HIDE_OVERLAY -> handleBack()
          else -> Unit
        }
        action != ControllerPlaybackKeyAction.NONE
      }
      .focusable()
  ) {
    playerView(Modifier.fillMaxSize(), 1f, true)
    val coverUrl =
      state.roomInfo?.keyframeUrl?.takeIf(String::isNotBlank)
        ?: state.roomInfo?.coverUrl?.takeIf(String::isNotBlank)
        ?: entry.keyframeUrl
        ?: entry.coverUrl
    if (!firstFrameVisible || state.playbackError != null || coverAlpha.value > .001f) {
      CoverImage(
        coverUrl = coverUrl.orEmpty(),
        contentDescription = null,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha.value }.zIndex(1f),
        shape = RectangleShape,
        enforceAspectRatio = false,
        requestWidth = 1600,
        requestHeight = 900,
        loadKey = "controller-live:${entry.roomId}:${state.generation}",
        alwaysLoad = true,
        retainBitmap = true,
        useOriginalSource = true,
        fadeIn = false,
        contentScale = ContentScale.Crop,
      )
      Box(
        Modifier.fillMaxSize()
          .graphicsLayer { alpha = coverAlpha.value }
          .background(Color.Black.copy(alpha = .28f))
          .zIndex(1.1f)
      )
    }
    if ((state.playbackLoading || buffering) && state.playbackError == null) {
      androidx.compose.material3.CircularProgressIndicator(
        Modifier.align(Alignment.Center).zIndex(2f),
        strokeWidth = 3.dp,
        color = Color.White,
      )
    }
    if (feedback != null) {
      Text(
        feedback!!,
        modifier = Modifier.align(Alignment.Center).zIndex(4f).background(Color.Black.copy(alpha = .52f)).padding(14.dp),
        color = Color.White,
      )
    }
    PlayerGestureLayer(
      enabledBrightness = settings.brightnessGesture,
      enabledVolume = settings.volumeGesture,
      enabledSeek = false,
      enabledFullscreenToggle = false,
      positionProvider = { 0L },
      durationMs = 0L,
      onSeek = {},
      onIndicator = { indicator -> showFeedback(indicator.kind.name) },
      onSeekPreview = {},
      onSeekCancel = {},
      onToggleControls = {
        overlay =
          if (overlay == ControllerPlaybackOverlay.HIDDEN) ControllerPlaybackOverlay.INFO
          else ControllerPlaybackOverlay.HIDDEN
      },
      onDoubleTap = { if (player?.isPlaying == true) player.pause() else player?.play() },
      onTemporarySpeedChanged = {},
      isFullscreen = true,
      onFullscreenChanged = {},
      seekEdgeInset = 40.dp,
      enabledDoubleTap = true,
      enabledTemporarySpeed = false,
      enabledTwoFingerSeek = false,
      modifier = Modifier.fillMaxSize().zIndex(1.8f),
    )
    if (showDanmaku) {
      LiveDanmakuLayer(
        items = danmaku,
        startedAtElapsedMs = danmakuStartedAtElapsedMs,
        enabled = danmakuRenderingEnabled,
        paused = !isPlaying,
        transitionSuppressed = danmakuTransitionSuppressed,
        fullscreen = true,
        opacity = settings.liveDanmakuOpacity,
        displayArea = settings.liveDanmakuDisplayArea,
        densityLevel = settings.danmakuDensity,
        fontScale = settings.liveDanmakuFontScale,
        speed = settings.liveDanmakuSpeed,
        positionEpoch = state.generation,
        modifier = Modifier.fillMaxSize().zIndex(2.5f),
      )
    }
    ControllerPlaybackControls(
      title = title,
      positionMs = 0L,
      durationMs = 0L,
      isPlaying = isPlaying,
      actions = actionItems,
      overlay = overlay,
      initialActionFocusRequester = actionFocusRequester,
      showProgress = false,
      statusText = liveStatus,
      onAction = { action ->
        when (action.key) {
          "danmaku" -> showPanel(ControllerPlaybackPanel.DANMAKU)
          "quality" -> showPanel(ControllerPlaybackPanel.QUALITY)
        }
      },
      modifier = Modifier.align(Alignment.BottomCenter).zIndex(3f),
    )
    if (panel == ControllerPlaybackPanel.MORE) {
      Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .26f)).zIndex(5f),
        contentAlignment = Alignment.CenterEnd,
      ) {
        ControllerPlaybackSidePanel(
          title = "更多",
          items = moreItems,
          onItemClick = { selected ->
            panel = ControllerPlaybackPanel.NONE
            overlay = ControllerPlaybackOverlay.INFO
            when {
              selected.key == "refresh" -> onRetryPlayback()
              selected.key == "live_edge" -> onSeekLiveEdge()
            }
            runCatching { focusRequester.requestFocus() }
          },
          onBack = ::handleBack,
          modifier = Modifier.fillMaxHeight().widthIn(min = 300.dp, max = 440.dp),
        )
      }
    }
    if (panel == ControllerPlaybackPanel.QUALITY) {
      Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .26f)).zIndex(5f),
        contentAlignment = Alignment.CenterEnd,
      ) {
        ControllerPlaybackSidePanel(
          title = "清晰度",
          items = qualityItems,
          onItemClick = { selected ->
            panel = ControllerPlaybackPanel.NONE
            overlay = ControllerPlaybackOverlay.INFO
            selected.key.substringAfter(':').toIntOrNull()?.let(onQuality)
            runCatching { focusRequester.requestFocus() }
          },
          onBack = ::handleBack,
          modifier = Modifier.fillMaxHeight().widthIn(min = 300.dp, max = 440.dp),
        )
      }
    }
    if (panel == ControllerPlaybackPanel.DANMAKU) {
      Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .26f)).zIndex(5f),
        contentAlignment = Alignment.CenterEnd,
      ) {
        ControllerDanmakuSettingsPanel(
          showDanmaku = showDanmaku,
          displayArea = settings.liveDanmakuDisplayArea,
          density = settings.danmakuDensity,
          opacity = settings.liveDanmakuOpacity,
          fontScale = settings.liveDanmakuFontScale,
          speed = settings.liveDanmakuSpeed,
          speedRange = .6f..1.8f,
          speedSteps = 5,
          blockWordsLabel =
            "仅当前直播间 · ${if (danmakuBlockWordCount == 0) "尚未设置" else "已设置 $danmakuBlockWordCount 个"}",
          onToggleDanmaku = { onShowDanmaku(!showDanmaku) },
          onDisplayAreaChange = { value ->
            onSettingsChange { it.copy(liveDanmakuDisplayArea = value) }
          },
          onDensityChange = { value -> onSettingsChange { it.copy(danmakuDensity = value) } },
          onOpacityChange = { value ->
            onSettingsChange { it.copy(liveDanmakuOpacity = value) }
          },
          onFontScaleChange = { value ->
            onSettingsChange { it.copy(liveDanmakuFontScale = value) }
          },
          onSpeedChange = { value -> onSettingsChange { it.copy(liveDanmakuSpeed = value) } },
          onBlockWords = {
            handleBack()
            onOpenDanmakuBlockWords()
          },
          onBack = ::handleBack,
        )
      }
    }
    if (panel == ControllerPlaybackPanel.CHAT) {
      Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .38f)).zIndex(5f),
        contentAlignment = Alignment.CenterEnd,
      ) {
        Column(
          Modifier.fillMaxHeight().widthIn(min = 360.dp, max = 520.dp).background(Color.Black.copy(alpha = .88f)),
        ) {
          Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("聊天", color = Color.White)
            TextButton(onClick = ::handleBack) { Text("返回", color = Color.White) }
          }
          LiveSecondaryPane(
            state = state,
            account = account,
            selectedTab = LiveSecondaryTab.CHAT,
            onSelectedTabChange = {},
            onText = onText,
            onSend = onSend,
            onToggleEmoji = onToggleEmoji,
            onSelectEmojiPack = onSelectEmojiPack,
            onEmoji = onEmoji,
            onJoinLottery = onJoinLottery,
            onLogin = onLogin,
            onRankTab = onRankTab,
            onAudienceRank = onAudienceRank,
            onGuardType = onGuardType,
            onLoadMoreGuards = onLoadMoreGuards,
            glassBackdrop = glassBackdrop,
          )
        }
      }
    }
  }
}

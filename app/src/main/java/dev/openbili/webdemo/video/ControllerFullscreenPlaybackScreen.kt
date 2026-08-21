package dev.openbili.webdemo.video

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.settings.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class VideoPlaybackPageKind {
  TOUCH,
  CONTROLLER_FULLSCREEN,
}

internal fun resolveVideoPlaybackPageKind(
  controlMode: Boolean,
  controllerTouchPlaybackPage: Boolean,
  alwaysControllerPlaybackPage: Boolean = false,
): VideoPlaybackPageKind =
  if (alwaysControllerPlaybackPage || (controlMode && !controllerTouchPlaybackPage)) {
    VideoPlaybackPageKind.CONTROLLER_FULLSCREEN
  } else {
    VideoPlaybackPageKind.TOUCH
  }

/** 控制器专用播放页：播放器表面、统一控制层和统一侧栏的组合壳。 */
@Composable
internal fun ControllerFullscreenPlaybackScreen(
  item: FeedItem,
  playerState: PlayerState,
  playerReady: Boolean,
  renderedVideoId: String?,
  renderedVideoFrameCount: Int,
  isBuffering: Boolean,
  keepScreenOn: Boolean,
  currentPositionMs: () -> Long,
  durationMs: Long,
  isPlaying: Boolean = false,
  title: String = item.title,
  selectionItems: List<ControllerPlaybackActionItem> = emptyList(),
  selectionGroups: List<ControllerPlaybackSelectionGroup> = emptyList(),
  moreItems: List<ControllerPlaybackActionItem> = emptyList(),
  qualityItems: List<ControllerPlaybackActionItem> = emptyList(),
  subtitleItems: List<ControllerPlaybackActionItem> = emptyList(),
  showDanmaku: Boolean,
  settings: AppSettings,
  onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
  onToggleDanmaku: () -> Unit,
  playbackEnded: Boolean = false,
  recommendations: List<FeedItem> = emptyList(),
  hiddenPlaybackEndRecommendationCoverItemId: String? = null,
  onReplay: () -> Unit = {},
  onRecommendationClick: (FeedItem, Rect) -> Unit = { _, _ -> },
  onRecommendationLongClick: (FeedItem) -> Unit = {},
  onControllerAction: (ControllerPlaybackActionItem) -> Unit = {},
  onBack: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onSeek: (Long) -> Unit,
  onRetryPlayback: () -> Unit,
  onRetryNextQuality: () -> Unit,
  onPlayerBoundsChanged: (Rect) -> Unit,
  playerView: @Composable (Modifier) -> Unit,
) {
  val hostView = LocalView.current
  val focusRequester = remember(item.id) { FocusRequester() }
  val actionFocusRequester = remember(item.id) { FocusRequester() }
  val playbackEndFocusRequester = remember(item.id) { FocusRequester() }
  val firstFrameVisible = renderedVideoId == item.id && renderedVideoFrameCount > 0
  val playbackError = playerState is PlayerState.Error
  var overlay by remember(item.id) { mutableStateOf(ControllerPlaybackOverlay.HIDDEN) }
  var panel by remember(item.id) { mutableStateOf(ControllerPlaybackPanel.NONE) }
  var displayedPositionMs by remember(item.id) { mutableLongStateOf(currentPositionMs()) }
  var seekFeedback by remember(item.id) { mutableStateOf<String?>(null) }
  var gestureFeedback by remember(item.id) { mutableStateOf<GestureIndicator?>(null) }
  val coverAlpha = remember(item.id) { Animatable(1f) }
  val playbackEndReveal = remember(item.id) { Animatable(if (playbackEnded) 1f else 0f) }
  val scope = rememberCoroutineScope()

  DisposableEffect(hostView, keepScreenOn) {
    val previous = hostView.keepScreenOn
    hostView.keepScreenOn = keepScreenOn
    onDispose { hostView.keepScreenOn = previous }
  }

  LaunchedEffect(item.id) {
    androidx.compose.runtime.withFrameNanos {}
    runCatching { focusRequester.requestFocus() }
  }

  LaunchedEffect(item.id, firstFrameVisible, playbackError) {
    if (playbackError || !firstFrameVisible) {
      coverAlpha.snapTo(1f)
    } else {
      coverAlpha.animateTo(0f, tween(240))
    }
  }

  LaunchedEffect(playbackEnded) {
    if (playbackEnded) playbackEndReveal.animateTo(1f, tween(300))
    else playbackEndReveal.snapTo(0f)
  }

  LaunchedEffect(item.id) {
    while (true) {
      displayedPositionMs = currentPositionMs()
      delay(250)
    }
  }

  LaunchedEffect(overlay, panel, isPlaying) {
    if (overlay == ControllerPlaybackOverlay.INFO && panel == ControllerPlaybackPanel.NONE && isPlaying) {
      delay(4_000)
      if (overlay == ControllerPlaybackOverlay.INFO && panel == ControllerPlaybackPanel.NONE) {
        overlay = ControllerPlaybackOverlay.HIDDEN
      }
    }
  }

  LaunchedEffect(overlay) {
    if (overlay == ControllerPlaybackOverlay.ACTIONS) {
      androidx.compose.runtime.withFrameNanos {}
      runCatching { actionFocusRequester.requestFocus() }
    } else if (overlay == ControllerPlaybackOverlay.INFO) {
      runCatching { focusRequester.requestFocus() }
    }
  }

  fun showPanel(target: ControllerPlaybackPanel) {
    panel = target
    overlay = ControllerPlaybackOverlay.PANEL
  }

  fun showSeekFeedback(deltaMs: Long) {
    overlay = ControllerPlaybackOverlay.INFO
    seekFeedback = if (deltaMs < 0) "− 5 秒" else "+ 5 秒"
    scope.launch {
      delay(700)
      seekFeedback = null
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

  BackHandler(onBack = ::handleBack)

  Box(
    Modifier.fillMaxSize()
      .background(Color.Black)
      .onGloballyPositioned { onPlayerBoundsChanged(it.boundsInRoot()) }
      .focusRequester(focusRequester)
      .onPreviewKeyEvent { event ->
        if (playbackEnded) return@onPreviewKeyEvent false
        val nativeEvent = event.nativeKeyEvent
        if (
          overlay == ControllerPlaybackOverlay.ACTIONS &&
            (nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP ||
              nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        ) {
          if (event.type == KeyEventType.KeyDown && nativeEvent.repeatCount == 0) {
            overlay = ControllerPlaybackOverlay.INFO
          }
          return@onPreviewKeyEvent true
        }
        // 菜单子项已经获得焦点；除上下键的层级切换外，其余按键交给子项处理。
        if (overlay == ControllerPlaybackOverlay.ACTIONS) {
          return@onPreviewKeyEvent false
        }
        // 操作行和侧栏由其子项接管焦点；不能让播放表面在预览阶段吞掉方向键或确认键。
        if (overlay == ControllerPlaybackOverlay.PANEL) {
          return@onPreviewKeyEvent false
        }
        if (nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
          if (event.type == KeyEventType.KeyDown && nativeEvent.repeatCount == 0) {
            onTogglePlayPause()
            overlay = ControllerPlaybackOverlay.INFO
          }
          true
        } else {
          val action =
            resolveControllerPlaybackKeyAction(
              keyCode = nativeEvent.keyCode,
              keyUp = event.type == KeyEventType.KeyUp,
              repeatCount = nativeEvent.repeatCount,
              overlay = overlay,
            )
          when (action) {
            ControllerPlaybackKeyAction.TOGGLE_PLAY -> {
              onTogglePlayPause()
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
            ControllerPlaybackKeyAction.SEEK_BACKWARD,
            ControllerPlaybackKeyAction.SEEK_FORWARD -> {
              val delta =
                controllerPlaybackSeekDeltaMs(
                  forward = action == ControllerPlaybackKeyAction.SEEK_FORWARD,
                  repeatCount = nativeEvent.repeatCount,
                )
              if (event.type == KeyEventType.KeyDown && durationMs > 0L) {
                onSeek((currentPositionMs() + delta).coerceIn(0L, durationMs))
                showSeekFeedback(delta)
              }
            }
            ControllerPlaybackKeyAction.HIDE_OVERLAY -> handleBack()
            else -> Unit
          }
          action != ControllerPlaybackKeyAction.NONE
        }
      }
      .focusable()
  ) {
    if (playerReady) playerView(Modifier.fillMaxSize())

    val showCover = !firstFrameVisible || playbackError || coverAlpha.value > .001f
    if (showCover) {
      CoverImage(
        coverUrl = item.coverUrl,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha.value }.zIndex(1f),
        shape = RectangleShape,
        enforceAspectRatio = false,
        requestWidth = 1600,
        requestHeight = 900,
        loadKey = "controller-fullscreen:${item.id}",
        alwaysLoad = true,
        retainBitmap = true,
        useOriginalSource = true,
        fadeIn = false,
        // 专用页的最终态必须覆盖窗口；横竖封面都保持比例，允许裁边但不拉伸。
        contentScale = ContentScale.Crop,
      )
      Box(
        Modifier.fillMaxSize()
          .graphicsLayer { alpha = coverAlpha.value }
          .background(Color.Black.copy(alpha = .28f))
          .zIndex(1.1f)
      )
    }

    if ((playerState is PlayerState.Loading || isBuffering) && !playbackError) {
      CircularProgressIndicator(
        modifier = Modifier.align(Alignment.Center).size(38.dp).zIndex(2f),
        strokeWidth = 3.dp,
        color = Color.White,
      )
    }

    if (seekFeedback != null) {
      Text(
        text = seekFeedback!!,
        modifier =
          Modifier.align(Alignment.Center)
            .zIndex(4f)
            .background(Color.Black.copy(alpha = .5f))
            .padding(14.dp),
        color = Color.White,
      )
    }
    gestureFeedback?.let { indicator ->
      GestureIndicatorOverlay(
        indicator = indicator,
        modifier = Modifier.align(Alignment.Center).zIndex(4.1f),
      )
    }

    if (playerReady && !playbackError && !playbackEnded) {
      PlayerGestureLayer(
        enabledBrightness = true,
        enabledVolume = true,
        enabledSeek = durationMs > 0L,
        enabledFullscreenToggle = false,
        positionProvider = currentPositionMs,
        durationMs = durationMs,
        onSeek = onSeek,
        onIndicator = { indicator -> gestureFeedback = indicator },
        onSeekPreview = { preview -> preview?.let { displayedPositionMs = it } },
        onSeekCancel = {},
        onToggleControls = {
          overlay =
            if (overlay == ControllerPlaybackOverlay.HIDDEN) ControllerPlaybackOverlay.INFO
            else ControllerPlaybackOverlay.HIDDEN
        },
        onDoubleTap = onTogglePlayPause,
        onTemporarySpeedChanged = {},
        isFullscreen = true,
        onFullscreenChanged = {},
        seekEdgeInset = 40.dp,
        enabledTemporarySpeed = false,
        enabledTwoFingerSeek = false,
        modifier = Modifier.fillMaxSize().zIndex(1.8f),
      )
    }

    AnimatedVisibility(
      visible = !isPlaying && !playbackEnded,
      modifier = Modifier.align(Alignment.Center).zIndex(4.2f),
      enter = fadeIn(tween(150)),
      exit = fadeOut(tween(150)),
    ) {
      PlayerCenterPlayPauseButton(isPlaying = false, onPlayPause = onTogglePlayPause)
    }

    ControllerPlaybackControls(
      title = title,
      positionMs = displayedPositionMs,
      durationMs = durationMs,
      isPlaying = isPlaying,
      actions = buildList {
        add(ControllerPlaybackActionItem("danmaku", "弹幕"))
        add(ControllerPlaybackActionItem("quality", "清晰度", qualityItems.isNotEmpty()))
        add(ControllerPlaybackActionItem("subtitle", "字幕", subtitleItems.isNotEmpty()))
        if (selectionItems.isNotEmpty() || selectionGroups.isNotEmpty()) {
          add(ControllerPlaybackActionItem("selection", "选集"))
        }
      },
      overlay = overlay,
      initialActionFocusRequester = actionFocusRequester,
      onAction = { action ->
        when (action.key) {
          "danmaku" -> showPanel(ControllerPlaybackPanel.DANMAKU)
          "quality" -> showPanel(ControllerPlaybackPanel.QUALITY)
          "subtitle" -> showPanel(ControllerPlaybackPanel.SUBTITLE)
          "selection" -> showPanel(ControllerPlaybackPanel.SELECTION)
          "more" -> showPanel(ControllerPlaybackPanel.MORE)
          else -> onControllerAction(action)
        }
      },
      modifier = Modifier.align(Alignment.BottomCenter).zIndex(3f),
    )

    if (
      panel != ControllerPlaybackPanel.NONE &&
        panel != ControllerPlaybackPanel.DANMAKU &&
        panel != ControllerPlaybackPanel.SELECTION
    ) {
      Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .26f)).zIndex(5f),
        contentAlignment = Alignment.CenterEnd,
      ) {
        ControllerPlaybackSidePanel(
          title =
            when (panel) {
              ControllerPlaybackPanel.QUALITY -> "清晰度"
              ControllerPlaybackPanel.SUBTITLE -> "字幕"
              else -> "更多"
            },
          items =
            when (panel) {
              ControllerPlaybackPanel.QUALITY -> qualityItems
              ControllerPlaybackPanel.SUBTITLE -> subtitleItems
              else -> moreItems
            },
          onItemClick = { selected ->
            panel = ControllerPlaybackPanel.NONE
            overlay = ControllerPlaybackOverlay.INFO
            onControllerAction(selected)
            runCatching { focusRequester.requestFocus() }
          },
          onBack = ::handleBack,
        )
      }
    }
    if (panel == ControllerPlaybackPanel.SELECTION) {
      Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .26f)).zIndex(5f),
        contentAlignment = Alignment.CenterEnd,
      ) {
        ControllerPlaybackSelectionPanel(
          selectionItems = selectionItems,
          selectionGroups = selectionGroups,
          onItemClick = { selected ->
            panel = ControllerPlaybackPanel.NONE
            overlay = ControllerPlaybackOverlay.INFO
            onControllerAction(selected)
            runCatching { focusRequester.requestFocus() }
          },
          onBack = ::handleBack,
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
          danmakuSmartBlocking = settings.danmakuSmartBlocking,
          displayArea = settings.danmakuDisplayArea,
          density = settings.danmakuDensity,
          blockLevel = settings.danmakuBlockLevel,
          opacity = settings.danmakuOpacity,
          fontScale = settings.danmakuFontScale,
          speed = settings.danmakuSpeed,
          onToggleDanmaku = onToggleDanmaku,
          onDanmakuSmartBlockingChange = { value ->
            onSettingsChange { it.copy(danmakuSmartBlocking = value) }
          },
          onDisplayAreaChange = { value ->
            onSettingsChange { it.copy(danmakuDisplayArea = value) }
          },
          onDensityChange = { value -> onSettingsChange { it.copy(danmakuDensity = value) } },
          onBlockLevelChange = { value -> onSettingsChange { it.copy(danmakuBlockLevel = value) } },
          onOpacityChange = { value -> onSettingsChange { it.copy(danmakuOpacity = value) } },
          onFontScaleChange = { value -> onSettingsChange { it.copy(danmakuFontScale = value) } },
          onSpeedChange = { value -> onSettingsChange { it.copy(danmakuSpeed = value) } },
          onBack = ::handleBack,
        )
      }
    }

    if (playbackError) {
      PlayerErrorActions(
        error = playerState,
        onRetry = onRetryPlayback,
        onRetryNextQuality = onRetryNextQuality,
        modifier = Modifier.align(Alignment.Center).zIndex(6f),
        fullscreen = true,
      )
    }

    if (playbackEnded) {
      PlaybackEndedRecommendations(
        coverUrl = item.coverUrl,
        recommendations = recommendations,
        hiddenCoverItemId = hiddenPlaybackEndRecommendationCoverItemId,
        revealAlpha = { playbackEndReveal.value },
        showForeground = true,
        isFullscreen = true,
        onFullscreen = onBack,
        onReplay = onReplay,
        onRecommendationClick = onRecommendationClick,
        onRecommendationLongClick = onRecommendationLongClick,
        controlEnabled = true,
        initialFocusRequester = playbackEndFocusRequester,
        modifier = Modifier.fillMaxSize().zIndex(7f),
      )
    }
  }
}

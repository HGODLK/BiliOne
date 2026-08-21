package dev.openbili.webdemo.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import dev.openbili.webdemo.R
import dev.openbili.webdemo.feed.CoverImageRequestFactory
import dev.openbili.webdemo.feed.LocalCoverImageLoadingEnabled
import dev.openbili.webdemo.music.HomeMusicPlayerViewModel
import dev.openbili.webdemo.music.HomeMusicUiState
import dev.openbili.webdemo.music.MUSIC_SPECTRUM_PEAK_HOLD_MS
import dev.openbili.webdemo.music.MusicLibraryStatus
import dev.openbili.webdemo.music.advanceMusicPeak
import dev.openbili.webdemo.music.displayTitle
import dev.openbili.webdemo.settings.AppSettings
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal val MusicPaneShape = RoundedCornerShape(24.dp)
private const val MUSIC_PAGE_PLAYBACK_START_DELAY_MS = 1_000L
private const val MUSIC_PAGE_ENTRY_PREPARE_TIMEOUT_MS = 1_500L

@Composable
internal fun HomeMusicPlayerScreen(
  accountMid: Long,
  vipActive: Boolean,
  settings: AppSettings,
  viewModel: HomeMusicPlayerViewModel,
  entryBackdropLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
  entryBackdropBounds: Rect,
  entryUnderlayLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
  entryUnderlayBounds: Rect,
  onDismissed: () -> Unit,
  onLoginClick: (Rect) -> Unit,
  onFavoriteFolderSelected: (Long) -> Unit,
  onExitStarted: () -> Unit = {},
  onEntrySettled: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val state by viewModel.screenState.collectAsState()
  val lifecycleOwner = LocalLifecycleOwner.current
  val hostView = LocalView.current
  DisposableEffect(hostView) {
    val previous = hostView.keepScreenOn
    hostView.keepScreenOn = true
    onDispose { hostView.keepScreenOn = previous }
  }
  LaunchedEffect(settings.musicPreferredResolutionMode, vipActive) {
    viewModel.configureVideoQuality(settings.musicPreferredResolutionMode, vipActive)
  }
  LaunchedEffect(
    settings.unlockDolbyAtmos,
    settings.unlockHiRes,
    settings.advancedAudioEnabled,
    settings.advancedAudioPriority,
  ) {
    viewModel.configureAudio(
      unlockDolbyAtmos = settings.unlockDolbyAtmos,
      unlockHiRes = settings.unlockHiRes,
      advancedAudioEnabled = settings.advancedAudioEnabled,
      advancedAudioPriority = settings.advancedAudioPriority,
    )
  }
  DisposableEffect(lifecycleOwner, viewModel) {
    fun updateForegroundState() {
      viewModel.setAppInForeground(
        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
      )
    }
    val observer = LifecycleEventObserver { _, _ -> updateForegroundState() }
    lifecycleOwner.lifecycle.addObserver(observer)
    updateForegroundState()
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.setAppInForeground(false)
    }
  }

  BoxWithConstraints(modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val screenHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
    val wideLayout = maxWidth >= 840.dp
    val scope = rememberCoroutineScope()
    val controlMode = LocalControlMode.current
    val pageOffset = remember { Animatable(0f) }
    var pageReady by remember { mutableStateOf(false) }
    var pageSettled by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var entryAnimationInProgress by remember { mutableStateOf(true) }
    val backgroundLayer = rememberGraphicsLayer()
    var backgroundBounds by remember { mutableStateOf(Rect.Zero) }
    var ambientBounds by remember { mutableStateOf(Rect.Zero) }
    var musicPlayerBounds by remember { mutableStateOf(Rect.Zero) }
    var musicProgressBounds by remember { mutableStateOf(Rect.Zero) }
    val menuProgress = remember { Animatable(0f) }
    val spectrumLineReveal = remember { Animatable(0f) }
    val spectrumReveal = remember { Animatable(0f) }
    var spectrumExitInProgress by remember { mutableStateOf(false) }
    var hiddenMenuPointerPressed by remember { mutableStateOf(false) }
    val spectrumTouchAnimationJob = remember { arrayOfNulls<Job>(1) }
    var menuWidthPx by remember { mutableFloatStateOf(1f) }
    var menuDragging by remember { mutableStateOf(false) }
    var menuDragProgress by remember { mutableFloatStateOf(0f) }
    var menuDragStartProgress by remember { mutableFloatStateOf(0f) }
    val menuAnimationJob = remember { arrayOfNulls<Job>(1) }
    var controlMenuAnimating by remember { mutableStateOf(false) }
    var controlFocusInLibrary by remember { mutableStateOf(false) }
    var controlPlayerTransientOpen by remember { mutableStateOf(false) }
    var controlLibraryTransientOpen by remember { mutableStateOf(false) }
    var controlPlayerFocusRequest by remember { mutableIntStateOf(0) }
    var controlPlayPauseFocusRequest by remember { mutableIntStateOf(0) }
    var controlProgressFocusRequest by remember { mutableIntStateOf(0) }
    var controlLibraryFocusRequest by remember { mutableIntStateOf(0) }
    var controlDismissPlayerTransientRequest by remember { mutableIntStateOf(0) }
    var controlDismissTransientRequest by remember { mutableIntStateOf(0) }
    var initialControlFocusAssigned by remember { mutableStateOf(false) }
    val effectiveMenuProgress =
      if (wideLayout && menuDragging) menuDragProgress
      else if (wideLayout) menuProgress.value else 0f
    val controlTransientOpen = controlPlayerTransientOpen || controlLibraryTransientOpen
    val controlPlayerFocusAvailable =
      state.currentItem != null && !state.playbackLoading ||
        state.items.isNotEmpty() ||
        state.dolbyAvailable ||
        state.hiResAvailable
    val menuSettledHidden by
      remember(wideLayout) {
        derivedStateOf { wideLayout && !menuDragging && menuProgress.value >= .999f }
      }
    val spectrumColors =
      rememberVideoCoverThemeColors(
        if (entryAnimationInProgress) "" else state.currentItem?.coverUrl.orEmpty()
      )
    val musicBackgroundSource =
      if (settings.useHomeBackgroundForMusic && settings.homeBackgroundUri.isNotBlank()) {
        settings.homeBackgroundUri
      } else {
        state.currentItem?.coverUrl.orEmpty()
      }
    val musicBackgroundModel =
      rememberStaticBackgroundModel(
        source = musicBackgroundSource,
        blurred =
          !settings.useHomeBackgroundForMusic ||
            settings.homeBackgroundUri.isBlank() ||
            settings.homeBackgroundMusicBlur,
      )
    var displayedMusicBackgroundModel by remember { mutableStateOf<Any?>(null) }
    val libraryResolvedWithoutTrack =
      accountMid <= 0L ||
        state.libraryStatus in
          setOf(
            MusicLibraryStatus.MISSING,
            MusicLibraryStatus.ERROR,
          ) ||
        (state.libraryStatus == MusicLibraryStatus.READY && state.currentItem == null)
    val entryBridgeReleaseReady =
      displayedMusicBackgroundModel != null || libraryResolvedWithoutTrack
    val latestEntryBridgeReleaseReady by rememberUpdatedState(entryBridgeReleaseReady)
    val entryBridgeAlpha = remember { Animatable(1f) }
    LaunchedEffect(entryBridgeReleaseReady) {
      if (entryBridgeReleaseReady) {
        // CrossfadeBackgroundImage 只在已解码图片完成其自身淡入后才上报成功。
        // 在释放桥接之前再保留一帧完整的捕获首页，让 RenderThread 永不暴露
        // Activity 窗口背景。
        withFrameNanos {}
        entryBridgeAlpha.animateTo(
          0f,
          tween(if (settings.reduceMotion) 70 else 180),
        )
      } else {
        entryBridgeAlpha.snapTo(1f)
      }
    }
    val backgroundLuminance = rememberBackgroundLuminanceProfile(musicBackgroundModel)
    val headerForeground by
      animateColorAsState(
        targetValue =
          videoBackgroundForeground(
            luminance = backgroundLuminance?.top,
            darkMode = false,
            fallback = Color.White,
          ),
        animationSpec = tween(if (settings.reduceMotion) 80 else 220),
        label = "musicHeaderForeground",
      )
    val playerForeground by
      animateColorAsState(
        targetValue =
          videoBackgroundForeground(
            luminance = backgroundLuminance?.middle,
            darkMode = false,
            fallback = Color.White,
          ),
        animationSpec = tween(if (settings.reduceMotion) 80 else 220),
        label = "musicPlayerForeground",
      )

    suspend fun settlePage(dismiss: Boolean) {
      if (settling) return
      settling = true
      if (dismiss) {
        onExitStarted()
        viewModel.holdPreparedExitForTransition()
      }
      pageOffset.animateTo(
        targetValue = if (dismiss) -screenHeightPx else 0f,
        animationSpec = tween(if (settings.reduceMotion) 90 else 240),
      )
      settling = false
      if (dismiss) {
        viewModel.stopAndClose()
        onDismissed()
      }
    }

    LaunchedEffect(screenHeightPx) {
      viewModel.holdPreparedEntryForTransition()
      pageOffset.snapTo(-screenHeightPx)
      pageReady = true
      // 页面还在屏幕外时就加载模糊背景、表面、调色板和封面，
      // 等背景真正显示后才滑入。这去掉了此前落在滑动最后一帧上的解码 + View
      // 创建突发。
      entryAnimationInProgress = false
      withTimeoutOrNull(MUSIC_PAGE_ENTRY_PREPARE_TIMEOUT_MS) {
        while (!latestEntryBridgeReleaseReady) withFrameNanos {}
      }
      withFrameNanos {}
      pageOffset.animateTo(0f, tween(if (settings.reduceMotion) 90 else 260))
      pageSettled = true
      // 页面现在完全在屏幕上了。释放进入输入锁让触摸恢复工作，
      // 这与仍在下面延迟期间运行的播放准备无关。
      onEntrySettled()
      delay(MUSIC_PAGE_PLAYBACK_START_DELAY_MS)
      viewModel.open(
        accountMid = accountMid,
        folderSelectionId = settings.musicFavoriteFolderId,
        folderSelectionConfigured = settings.musicFavoriteFolderConfigured,
      )
    }
    LaunchedEffect(
      pageSettled,
      controlMode,
      state.currentItem?.id,
      state.playbackLoading,
      state.libraryStatus,
    ) {
      if (!pageSettled || !controlMode || initialControlFocusAssigned) return@LaunchedEffect
      when {
        state.currentItem != null && !state.playbackLoading -> {
          initialControlFocusAssigned = true
          controlFocusInLibrary = false
          controlPlayerFocusRequest++
        }
        state.libraryStatus != MusicLibraryStatus.LOADING -> {
          initialControlFocusAssigned = true
          controlFocusInLibrary = true
          controlLibraryFocusRequest++
        }
      }
    }
    BackHandler(enabled = pageReady && !settling) {
      scope.launch { settlePage(dismiss = true) }
    }

    LaunchedEffect(
      menuSettledHidden,
      state.isPlaying,
      spectrumExitInProgress,
      hiddenMenuPointerPressed,
    ) {
      if (menuSettledHidden && !spectrumExitInProgress && !hiddenMenuPointerPressed) {
        spectrumLineReveal.animateTo(1f, tween(if (settings.reduceMotion) 70 else 180))
        spectrumReveal.animateTo(
          if (state.isPlaying) 1f else 0f,
          tween(if (settings.reduceMotion) 80 else if (state.isPlaying) 260 else 210),
        )
      } else if (menuProgress.value <= .001f) {
        // 仅初始/打开状态清理。反向动画期间，显式的重新打开序列拥有这些 Animatable；
        // 在这里把它们快照归位会取消菜单动画的兄弟动画。
        spectrumReveal.snapTo(0f)
        spectrumLineReveal.snapTo(0f)
      }
    }

    fun collapseSpectrumForMenuTouch() {
      if (!menuSettledHidden || spectrumExitInProgress) return
      spectrumTouchAnimationJob[0]?.cancel()
      spectrumExitInProgress = true
      spectrumTouchAnimationJob[0] = scope.launch {
        try {
          // 先把响应式柱条收拢到基线，再移除基线。保持这两步顺序进行，
          // 让按下事件感觉有意图而不拖慢拖动。
          spectrumReveal.animateTo(0f, tween(if (settings.reduceMotion) 45 else 90))
          spectrumLineReveal.animateTo(0f, tween(if (settings.reduceMotion) 35 else 65))
        } finally {
          spectrumExitInProgress = false
        }
      }
    }

    fun requestControlPlayerFocus() {
      if (!controlPlayerFocusAvailable) return
      controlFocusInLibrary = false
      controlPlayerFocusRequest++
    }

    fun requestControlPlayPauseFocus() {
      controlFocusInLibrary = false
      controlPlayPauseFocusRequest++
    }

    fun requestControlLibraryFocus() {
      controlFocusInLibrary = true
      controlLibraryFocusRequest++
    }

    fun hideControlLibrary() {
      when (
        resolveMusicLibraryRightAction(
          wideLayout = wideLayout,
          libraryCollapsed = menuSettledHidden,
          transientOpen = controlTransientOpen,
        )
      ) {
        MusicLibraryRightAction.KEEP_TRANSIENT -> Unit
        MusicLibraryRightAction.FOCUS_PLAY_PAUSE -> requestControlPlayPauseFocus()
        MusicLibraryRightAction.COLLAPSE_AND_FOCUS_PLAY_PAUSE -> {
          if (controlMenuAnimating) return
          menuAnimationJob[0]?.cancel()
          menuAnimationJob[0] = scope.launch {
            controlMenuAnimating = true
            requestControlPlayPauseFocus()
            withFrameNanos {}
            menuProgress.animateTo(1f, tween(if (settings.reduceMotion) 80 else 220))
            controlMenuAnimating = false
          }
        }
      }
    }

    fun showControlLibrary() {
      when (resolveMusicAdvancedAudioRightAction(wideLayout, menuSettledHidden)) {
        MusicAdvancedAudioRightAction.FOCUS_LIBRARY -> requestControlLibraryFocus()
        MusicAdvancedAudioRightAction.EXPAND_AND_FOCUS_LIBRARY -> {
          if (controlMenuAnimating) return
          menuAnimationJob[0]?.cancel()
          menuAnimationJob[0] = scope.launch {
            controlMenuAnimating = true
            collapseSpectrumForMenuTouch()
            menuProgress.animateTo(0f, tween(if (settings.reduceMotion) 80 else 220))
            controlMenuAnimating = false
            withFrameNanos {}
            requestControlLibraryFocus()
          }
        }
      }
    }

    val hiddenMenuTouchGesture =
      Modifier.pointerInput(wideLayout) {
        if (!wideLayout) return@pointerInput
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false)
          if (!menuSettledHidden) return@awaitEachGesture
          collapseSpectrumForMenuTouch()
          hiddenMenuPointerPressed = true
          try {
            var pressed = true
            while (pressed) {
              pressed = awaitPointerEvent().changes.any { it.pressed }
            }
          } finally {
            hiddenMenuPointerPressed = false
          }
        }
      }

    val menuDragGesture =
      Modifier.pointerInput(wideLayout, menuWidthPx) {
        if (!wideLayout) return@pointerInput
        detectHorizontalDragGestures(
          onDragStart = {
            menuAnimationJob[0]?.cancel()
            menuDragging = true
            menuDragProgress = menuProgress.value
            menuDragStartProgress = menuProgress.value
          },
          onHorizontalDrag = { change, amount ->
            change.consume()
            menuDragProgress =
              (menuDragProgress + amount / (menuWidthPx + 30.dp.toPx()).coerceAtLeast(1f)).coerceIn(
                0f,
                1f,
              )
          },
          onDragEnd = {
            val target =
              if (menuDragStartProgress < .5f) {
                if (menuDragProgress >= .16f) 1f else 0f
              } else {
                if (menuDragProgress <= .84f) 0f else 1f
              }
            menuAnimationJob[0] = scope.launch {
              menuProgress.snapTo(menuDragProgress)
              menuDragging = false
              menuProgress.animateTo(target, tween(if (settings.reduceMotion) 80 else 220))
            }
          },
          onDragCancel = {
            menuAnimationJob[0] = scope.launch {
              menuProgress.snapTo(menuDragProgress)
              menuDragging = false
              menuProgress.animateTo(
                if (menuDragStartProgress >= .5f) 1f else 0f,
                tween(if (settings.reduceMotion) 80 else 180),
              )
            }
          },
        )
      }

    Box(
      Modifier.fillMaxSize()
        .graphicsLayer {
          translationY = pageOffset.value
          alpha = if (pageReady) 1f else 0f
        }
        // 让这个覆盖层留在命中测试链中，使触摸永远不会穿透到首页信息流。
        // 不要消费事件：子滚动容器和菜单的水平拖动检测器必须仍能自由赢得手势仲裁。
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              awaitPointerEvent()
            }
          }
        }
        // 绝不要让进入中的页面透明。OriginOS/MIUI/One UI 提升或重建播放器
        // TextureView 时，透明的 RenderNode 会短暂暴露 Activity 窗口背景，
        // 被感知为一次白闪。
        .background(MaterialTheme.colorScheme.background)
    ) {
      if (entryBridgeAlpha.value > .001f) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = entryBridgeAlpha.value }) {
          // 这些正是 HomeHubScreen 在上一帧已经绘制过的图层。把两者都画进音乐页，
          // 使桥接成为本页 RenderNode 的一部分，而不是依赖一个透明洞露出下面的
          // 活动页面。
          if (entryUnderlayBounds.width > 0f && entryUnderlayBounds.height > 0f) {
            drawLayer(entryUnderlayLayer)
          }
          if (entryBackdropBounds.width > 0f && entryBackdropBounds.height > 0f) {
            drawLayer(entryBackdropLayer)
          }
        }
      }
      CompositionLocalProvider(LocalCoverImageLoadingEnabled provides !entryAnimationInProgress) {
        Box(
          Modifier.fillMaxSize()
            .onGloballyPositioned { backgroundBounds = it.boundsInRoot() }
            .drawWithContent {
              backgroundLayer.record { this@drawWithContent.drawContent() }
              drawLayer(backgroundLayer)
            }
        ) {
          if (musicBackgroundModel != null && !entryAnimationInProgress) {
            CrossfadeBackgroundImage(
              model = musicBackgroundModel,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
              transitionMillis = if (settings.reduceMotion) 90 else 320,
              onDisplayed = { displayedMusicBackgroundModel = it },
            )
          }
          Box(
            Modifier.fillMaxSize()
              .background(Color.Black.copy(alpha = MusicPageVisualTokens.BackgroundScrimAlpha))
          )
        }

        Box(Modifier.fillMaxSize().onGloballyPositioned { ambientBounds = it.boundsInRoot() }) {
          val spectrumShouldRender =
            !settling &&
              (spectrumExitInProgress || (menuSettledHidden && !hiddenMenuPointerPressed))
          MusicAmbientLayer(
            state = state,
            spectrumTargets = viewModel.spectrumTargets,
            menuHideProgress = effectiveMenuProgress,
            spectrumEnabled = spectrumShouldRender,
            lineReveal = if (spectrumShouldRender) spectrumLineReveal.value else 0f,
            spectrumReveal = if (spectrumShouldRender) spectrumReveal.value else 0f,
            paletteColors = spectrumColors,
            clockColor = playerForeground,
            playerBounds = musicPlayerBounds,
            ambientBounds = ambientBounds,
            wideLayout = wideLayout,
            modifier = Modifier.fillMaxSize(),
          )
        }

        Column(Modifier.fillMaxSize().padding(top = 10.dp)) {
          MusicHeader(
            state = state,
            wideLayout = wideLayout,
            menuHideProgress = effectiveMenuProgress,
            foregroundColor = headerForeground,
            onHome = { scope.launch { settlePage(dismiss = true) } },
            onVolumeChange = viewModel::setSystemVolume,
            onToggleMute = viewModel::toggleMute,
          )
          Spacer(Modifier.height(1.dp))
          if (wideLayout) {
            Row(
              Modifier.fillMaxWidth().weight(1f).padding(horizontal = 30.dp),
              horizontalArrangement = Arrangement.spacedBy(24.dp),
              verticalAlignment = Alignment.Top,
            ) {
              MusicPlayerPane(
                state = state,
                viewModel = viewModel,
                playbackSurfaceEnabled = !entryAnimationInProgress,
                menuHideProgress = effectiveMenuProgress,
                infoForegroundColor = playerForeground,
                progressBarBounds = musicProgressBounds,
                onPlayerBoundsChanged = { musicPlayerBounds = it },
                controlFocusRequest = controlPlayerFocusRequest,
                playPauseFocusRequest = controlPlayPauseFocusRequest,
                controlDismissTransientRequest = controlDismissPlayerTransientRequest,
                onControlProgressRequested = {
                  controlFocusInLibrary = false
                  controlProgressFocusRequest++
                },
                onControlLibraryRequested = ::showControlLibrary,
                onControlTransientOpenChanged = { controlPlayerTransientOpen = it },
                modifier = Modifier.weight(1.55f).fillMaxHeight(),
              )
              Box(Modifier.weight(1f).fillMaxHeight()) {
                MusicLibraryPane(
                  state = state,
                  viewModel = viewModel,
                  onLoginClick = onLoginClick,
                  onFavoriteFolderSelected = onFavoriteFolderSelected,
                  backdropLayer = backgroundLayer,
                  backdropBounds = backgroundBounds,
                  underlayLayer = null,
                  underlayBounds = Rect.Zero,
                  controlFocusRequest = controlLibraryFocusRequest,
                  controlDismissTransientRequest = controlDismissTransientRequest,
                  controlFocusable = !controlMenuAnimating && effectiveMenuProgress <= .001f,
                  controlFocusActive = controlFocusInLibrary,
                  onControlReturnToPlayer = ::requestControlPlayerFocus,
                  onControlHideLibrary = ::hideControlLibrary,
                  onControlFocused = { controlFocusInLibrary = true },
                  onControlTransientOpenChanged = { controlLibraryTransientOpen = it },
                  modifier =
                    Modifier.fillMaxSize()
                      .onSizeChanged { menuWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                      .graphicsLayer {
                        // 在视口中保留一个物理像素，让大的模糊 RenderNode 不会在菜单
                        // 返回时被冷剔除并重建。
                        translationX = (menuWidthPx - 1f).coerceAtLeast(0f) * effectiveMenuProgress
                      }
                      .then(menuDragGesture),
                )
              }
            }
          } else {
            Column(
              Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              MusicPlayerPane(
                state = state,
                viewModel = viewModel,
                playbackSurfaceEnabled = !entryAnimationInProgress,
                menuHideProgress = 0f,
                infoForegroundColor = playerForeground,
                progressBarBounds = musicProgressBounds,
                onPlayerBoundsChanged = { musicPlayerBounds = it },
                controlFocusRequest = controlPlayerFocusRequest,
                playPauseFocusRequest = controlPlayPauseFocusRequest,
                controlDismissTransientRequest = controlDismissPlayerTransientRequest,
                onControlProgressRequested = {
                  controlFocusInLibrary = false
                  controlProgressFocusRequest++
                },
                onControlLibraryRequested = ::requestControlLibraryFocus,
                onControlTransientOpenChanged = { controlPlayerTransientOpen = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
              )
              MusicLibraryPane(
                state = state,
                viewModel = viewModel,
                onLoginClick = onLoginClick,
                onFavoriteFolderSelected = onFavoriteFolderSelected,
                backdropLayer = backgroundLayer,
                backdropBounds = backgroundBounds,
                underlayLayer = null,
                underlayBounds = Rect.Zero,
                controlFocusRequest = controlLibraryFocusRequest,
                controlDismissTransientRequest = controlDismissTransientRequest,
                controlFocusable = true,
                controlFocusActive = controlFocusInLibrary,
                onControlReturnToPlayer = ::requestControlPlayerFocus,
                onControlHideLibrary = ::hideControlLibrary,
                onControlFocused = { controlFocusInLibrary = true },
                onControlTransientOpenChanged = { controlLibraryTransientOpen = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
              )
            }
          }
          Spacer(Modifier.height(58.dp))
        }

        MusicProgressBar(
          progressState = viewModel.progressState,
          onSeek = viewModel::seekTo,
          controlFocusRequest = controlProgressFocusRequest,
          onControlPlayerRequested = ::requestControlPlayerFocus,
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .fillMaxWidth(.8f)
              .navigationBarsPadding()
              .padding(bottom = 20.dp)
              .onGloballyPositioned { musicProgressBounds = it.boundsInRoot() },
        )

        // 一旦平移走，菜单本身几乎没有命中区域。保留一个稳定、不可见的屏幕内部
        // 目标用于手动滑入，包括部分反转的拖动期间。它有意延伸越过系统边缘返回
        // 条带，同时避开头部和底部进度控件。
        if (wideLayout && (menuDragging || effectiveMenuProgress > .001f)) {
          Box(
            Modifier.align(Alignment.CenterEnd)
              .fillMaxHeight()
              .width(128.dp)
              .padding(top = 76.dp, bottom = 72.dp)
              .zIndex(20f)
              .then(hiddenMenuTouchGesture)
              .then(menuDragGesture)
          )
        }

        if (settling) {
          Box(
            Modifier.fillMaxSize().zIndex(40f).pointerInput(Unit) {
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

@Composable
private fun MusicAmbientLayer(
  state: HomeMusicUiState,
  spectrumTargets: FloatArray,
  menuHideProgress: Float,
  spectrumEnabled: Boolean,
  lineReveal: Float,
  spectrumReveal: Float,
  paletteColors: List<Color>,
  clockColor: Color,
  playerBounds: Rect,
  ambientBounds: Rect,
  wideLayout: Boolean,
  modifier: Modifier,
) {
  var currentTime by remember { mutableStateOf(formatMusicClockWithSeconds()) }
  LaunchedEffect(Unit) {
    while (true) {
      currentTime = formatMusicClockWithSeconds()
      kotlinx.coroutines.delay(1_000)
    }
  }
  val bandCount = dev.openbili.webdemo.music.MUSIC_SPECTRUM_BAND_COUNT
  val renderedSpectrumBands = remember { FloatArray(bandCount) }
  val peakBands = remember { FloatArray(bandCount) }
  var spectrumFrameVersion by remember { mutableIntStateOf(0) }
  val latestIsPlaying by rememberUpdatedState(state.isPlaying)
  LaunchedEffect(spectrumEnabled) {
    if (!spectrumEnabled) {
      renderedSpectrumBands.fill(0f)
      peakBands.fill(0f)
      spectrumFrameVersion++
      return@LaunchedEffect
    }
    var previousFrameNanos = 0L
    // 时间常数平滑立即响应每个最新的 FFT 目标，从不重启一段长补间。快速起音让
    // 柱条与音频对齐；较短的释放仍能避免 120 Hz 面板上可见的抖动柱条。
    while (true) {
      withFrameNanos { frameNanos ->
        val elapsedMillis =
          if (previousFrameNanos == 0L) 8.33
          else ((frameNanos - previousFrameNanos) / 1_000_000.0).coerceIn(1.0, 34.0)
        previousFrameNanos = frameNanos
        repeat(bandCount) { index ->
          val target =
            if (latestIsPlaying) spectrumTargets.getOrElse(index) { 0f }.coerceIn(0f, 1f) else 0f
          val current = renderedSpectrumBands[index]
          val timeConstantMillis = if (target > current) 26.0 else 105.0
          val blend = (1.0 - exp(-elapsedMillis / timeConstantMillis)).toFloat()
          val next = current + (target - current) * blend
          renderedSpectrumBands[index] = if (abs(target - next) < .001f) target else next
          peakBands[index] =
            advanceMusicPeak(
              current = renderedSpectrumBands[index],
              peak = peakBands[index],
              elapsedMillis = elapsedMillis,
              holdMillis = MUSIC_SPECTRUM_PEAK_HOLD_MS,
            )
        }
        spectrumFrameVersion++
      }
    }
  }
  val progress = menuHideProgress.coerceIn(0f, 1f)
  val primary = paletteColors.getOrNull(0) ?: MaterialTheme.colorScheme.primary
  val secondary = paletteColors.getOrNull(1) ?: adjacentVideoColor(primary)
  val density = LocalDensity.current
  BoxWithConstraints(modifier) {
    val spectrumGlowPadding = 18.dp
    val spectrumContentHeight = maxHeight * .40f
    val spectrumTopOffset =
      maxHeight * (.335f - .045f * lineReveal.coerceIn(0f, 1f)) - spectrumGlowPadding
    val ambientWidthPx =
      ambientBounds.width.takeIf { it > 0f } ?: with(density) { maxWidth.toPx() }
    val spectrumGeometry =
      musicSpectrumRegion(
        windowWidthPx = with(density) { maxWidth.toPx() },
        ambientLeftPx = ambientBounds.left,
        ambientWidthPx = ambientWidthPx,
        playerRightPx = playerBounds.right,
        edgeClearancePx = with(density) { 24.dp.toPx() },
        desiredWidthPx = with(density) { (maxWidth * .34f).toPx() } +
          with(density) { spectrumGlowPadding.toPx() * 2f },
      )
    val spectrumLeft = with(density) { spectrumGeometry.leftPx.toDp() }
    val spectrumWidth = with(density) { spectrumGeometry.widthPx.toDp() }
    Canvas(
      Modifier.align(Alignment.TopStart)
        .offset(x = spectrumLeft)
        .width(spectrumWidth)
        .height(spectrumContentHeight + spectrumGlowPadding * 2f)
        .graphicsLayer { translationY = spectrumTopOffset.toPx() }
    ) {
      if (!wideLayout || lineReveal <= .001f) return@Canvas
      val glowPaddingPx =
        spectrumGlowPadding.toPx().coerceAtMost(size.width * .2f).coerceAtLeast(1f)
      val startX = glowPaddingPx
      val endX = size.width - glowPaddingPx
      val baselineY = size.height - glowPaddingPx
      val maximumBarHeight = (size.height - glowPaddingPx * 2f).coerceAtLeast(1f)
      val lineAlpha = lineReveal.coerceIn(0f, 1f)
      val gradient =
        androidx.compose.ui.graphics.Brush.horizontalGradient(
          listOf(primary.copy(alpha = lineAlpha), secondary.copy(alpha = lineAlpha)),
          startX = startX,
          endX = endX,
        )
      val glowGradient =
        androidx.compose.ui.graphics.Brush.horizontalGradient(
          listOf(
            primary.copy(alpha = lineAlpha * .28f),
            secondary.copy(alpha = lineAlpha * .28f),
          ),
          startX = startX,
          endX = endX,
        )
      drawLine(
        brush = glowGradient,
        start = Offset(startX, baselineY),
        end = Offset(endX, baselineY),
        strokeWidth = 7.dp.toPx(),
        cap = StrokeCap.Round,
      )
      drawLine(
        brush = gradient,
        start = Offset(startX, baselineY),
        end = Offset(endX, baselineY),
        strokeWidth = 2.2.dp.toPx(),
        cap = StrokeCap.Round,
      )
      val bands = renderedSpectrumBands
      val peaks = peakBands
      val spacing = (endX - startX) / bands.size.coerceAtLeast(1)
      val reveal = spectrumReveal.coerceIn(0f, 1f)
      // 从绘制阶段观察就地频段缓冲。版本号只失效这一个 Canvas，
      // 并避免在每个显示同步帧上分配装箱的 List<Float>。
      spectrumFrameVersion
      bands.forEachIndexed { index, amplitude ->
        val fraction = if (bands.size <= 1) 0f else index.toFloat() / (bands.size - 1)
        val baseColor = lerp(primary, secondary, fraction)
        val color = lerp(baseColor, Color.White, .32f).copy(alpha = reveal)
        val barHeight =
          maximumBarHeight * amplitude.coerceIn(0f, 1f) * spectrumReveal.coerceIn(0f, 1f)
        val x = startX + spacing * (index + .5f)
        val strokeWidth = (spacing * .48f).coerceAtLeast(2.dp.toPx())
        val glowStrokeWidth = strokeWidth * 2.15f
        val auraStrokeWidth = strokeWidth * 3.65f
        val visibleHeight = barHeight.coerceAtLeast(auraStrokeWidth)
        // 分层描边无需 BlurMaskFilter 就保留明亮的羽化边缘。对 28 条持续变化的路径
        // 使用模糊蒙版会让 Skia 缓存成千上万个临时蒙版，正是长时间播放后渐进卡顿的
        // 来源。
        drawLine(
          color = baseColor.copy(alpha = reveal * .12f),
          start = Offset(x, baselineY - auraStrokeWidth / 2f),
          end = Offset(x, baselineY - visibleHeight + auraStrokeWidth / 2f),
          strokeWidth = auraStrokeWidth,
          cap = StrokeCap.Round,
        )
        drawLine(
          color = baseColor.copy(alpha = reveal * .30f),
          start = Offset(x, baselineY - glowStrokeWidth / 2f),
          end = Offset(x, baselineY - visibleHeight + glowStrokeWidth / 2f),
          strokeWidth = glowStrokeWidth,
          cap = StrokeCap.Round,
        )
        drawLine(
          color = color,
          // 圆形的下端点精确落在基线上，绝不泄漏到其下方。
          start = Offset(x, baselineY - strokeWidth / 2f),
          end = Offset(x, baselineY - visibleHeight + strokeWidth / 2f),
          strokeWidth = strokeWidth,
          cap = StrokeCap.Round,
        )
        val peak = peaks[index]
        if (peak > amplitude + .02f) {
          val peakY = baselineY - maximumBarHeight * peak.coerceIn(0f, 1f) * reveal
          drawLine(
            color = color.copy(alpha = reveal * .8f),
            start = Offset(x - strokeWidth * .6f, peakY),
            end = Offset(x + strokeWidth * .6f, peakY),
            strokeWidth = strokeWidth * .55f,
            cap = StrokeCap.Round,
          )
        }
      }
    }

    if (wideLayout) {
      val clockWidth = 116.dp
      val spectrumCenterX = with(density) { spectrumGeometry.centerPx.toDp() }
      val initialClockCenterX = maxWidth - 30.dp - clockWidth / 2f
      val clockTravelX = spectrumCenterX - initialClockCenterX
      val initialClockCenterPx = with(density) { 30.dp.toPx() }
      val playerClockTargetPx =
        if (playerBounds.width > 0f && playerBounds.height > 0f && ambientBounds.height > 0f) {
          // 请求的三分之二位置是从播放器底部向上测量的。
          playerBounds.top - ambientBounds.top + playerBounds.height * (1f / 3f)
        } else {
          with(density) { (maxHeight * .42f).toPx() }
        }
      val clockTravelY =
        with(density) { (playerClockTargetPx - initialClockCenterPx).coerceAtLeast(0f).toDp() }
      // 放大时按实际窗口边界计算，避免完整时间被频谱区或屏幕边缘裁切。
      val clockHalfWidth = clockWidth / 2f
      val edgeClearance = 18.dp
      val spectrumRegionLeft = with(density) { spectrumGeometry.leftPx.toDp() }
      val spectrumRegionRight = with(density) { spectrumGeometry.rightPx.toDp() }
      val maxClockScale =
        minOf(
            (spectrumCenterX - spectrumRegionLeft - edgeClearance) / clockHalfWidth,
            (spectrumRegionRight - edgeClearance - spectrumCenterX) / clockHalfWidth,
          )
          .coerceAtLeast(1f)
      val clockScale = 1f + (maxClockScale - 1f) * progress
      Box(
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(top = 14.dp, end = 30.dp)
            .width(clockWidth)
            .graphicsLayer {
              translationX = clockTravelX.toPx() * progress
              translationY = clockTravelY.toPx() * progress
              scaleX = clockScale
              scaleY = clockScale
            },
        contentAlignment = Alignment.Center,
      ) {
        MusicClockText(
          time = currentTime,
          secondsReveal = progress,
          color = clockColor.copy(alpha = .92f),
        )
      }
    }
  }
}

@Composable
private fun MusicClockText(
  time: String,
  secondsReveal: Float,
  color: Color,
) {
  val style =
    MaterialTheme.typography.headlineSmall.copy(
      fontWeight = FontWeight.Medium,
      fontFeatureSettings = "tnum",
    )
  Row(verticalAlignment = Alignment.CenterVertically) {
    MusicClockCharacters(time.take(5), color, style)
    Box(
      Modifier.width(42.dp * secondsReveal.coerceIn(0f, 1f)).graphicsLayer {
        alpha = secondsReveal.coerceIn(0f, 1f)
        clip = true
      },
      contentAlignment = Alignment.CenterStart,
    ) {
      MusicClockCharacters(time.drop(5), color, style)
    }
  }
}

@Composable
private fun MusicClockCharacters(
  text: String,
  color: Color,
  style: androidx.compose.ui.text.TextStyle,
) {
  val slotHeight = 32.dp
  val digitWidth = 14.dp
  val colonWidth = 7.dp
  val totalWidth =
    remember(text) {
      text.fold(0.dp) { width, character ->
        width + if (character == ':') colonWidth else digitWidth
      }
    }
  var visibleText by remember { mutableStateOf(text) }
  var outgoingText by remember { mutableStateOf(text) }
  val transition = remember { Animatable(1f) }
  LaunchedEffect(text) {
    if (text == visibleText) return@LaunchedEffect
    outgoingText = visibleText
    visibleText = text
    transition.snapTo(0f)
    transition.animateTo(1f, tween(90))
    outgoingText = visibleText
  }
  val density = LocalDensity.current
  val textSizePx = with(density) { style.fontSize.toPx() }
  val paint = remember {
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
      typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
      textAlign = android.graphics.Paint.Align.CENTER
    }
  }
  Canvas(Modifier.width(totalWidth).height(slotHeight)) {
    paint.textSize = textSizePx
    val metrics = paint.fontMetrics
    val baseline = ((size.height - metrics.bottom - metrics.top) / 2f).roundToInt().toFloat()
    var left = 0f
    visibleText.forEachIndexed { index, newCharacter ->
      val slotWidth = if (newCharacter == ':') colonWidth.toPx() else digitWidth.toPx()
      val centerX = (left + slotWidth / 2f).roundToInt().toFloat()
      val oldCharacter = outgoingText.getOrNull(index) ?: newCharacter
      fun drawCharacter(character: Char, alpha: Float) {
        paint.color = color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f)).toArgb()
        drawIntoCanvas { canvas ->
          canvas.nativeCanvas.drawText(character.toString(), centerX, baseline, paint)
        }
      }
      if (newCharacter == ':' || oldCharacter == newCharacter) {
        drawCharacter(newCharacter, 1f)
      } else {
        drawCharacter(oldCharacter, 1f - transition.value)
        drawCharacter(newCharacter, transition.value)
      }
      left += slotWidth
    }
  }
}

@Composable
private fun MusicHeader(
  state: HomeMusicUiState,
  wideLayout: Boolean,
  menuHideProgress: Float,
  foregroundColor: Color,
  onHome: () -> Unit,
  onVolumeChange: (Float) -> Unit,
  onToggleMute: () -> Unit,
) {
  val progress = menuHideProgress.coerceIn(0f, 1f)
  Box(
    Modifier.fillMaxWidth()
      .height(if (wideLayout) 58.dp else 48.dp)
      .padding(horizontal = if (wideLayout) 30.dp else 18.dp)
  ) {
    Row(
      modifier = Modifier.align(Alignment.CenterStart),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "音乐播放",
        color = foregroundColor,
        fontSize = if (wideLayout) 48.sp else 30.sp,
        fontWeight = FontWeight.Black,
      )
      GlassIconButton(onClick = onHome, contentDescription = "回到首页") {
        Icon(Icons.Default.Home, contentDescription = null)
      }
    }
    Row(
      modifier =
        Modifier.align(Alignment.CenterEnd)
          .padding(end = if (wideLayout) 126.dp * (1f - progress) else 0.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AnimatedVisibility(
        visible = !state.muted,
        enter =
          fadeIn(tween(durationMillis = 150, delayMillis = 45)) +
            expandHorizontally(
              animationSpec = tween(240, easing = FastOutSlowInEasing),
              expandFrom = Alignment.End,
            ),
        exit =
          fadeOut(tween(durationMillis = 105, delayMillis = 105)) +
            shrinkHorizontally(
              animationSpec = tween(220, easing = FastOutSlowInEasing),
              shrinkTowards = Alignment.End,
            ),
      ) {
        MusicSystemVolumeBar(
          value = if (state.muted) 0f else state.volume.coerceIn(0f, 1f),
          onValueChange = onVolumeChange,
          foregroundColor = foregroundColor,
          modifier = Modifier.width(if (wideLayout) 224.dp else 136.dp),
        )
      }
      GlassIconButton(
        onClick = onToggleMute,
        contentDescription = if (state.muted) "取消系统静音" else "系统静音",
      ) {
        MusicVolumeIcon(muted = state.muted)
      }
    }
  }
}

@Composable
private fun MusicSystemVolumeBar(
  value: Float,
  onValueChange: (Float) -> Unit,
  foregroundColor: Color,
  modifier: Modifier = Modifier,
) {
  val controlMode = LocalControlMode.current
  val targetValue = value.coerceIn(0f, 1f)
  val animatedValue by
    animateFloatAsState(
      targetValue = targetValue,
      animationSpec = tween(180, easing = FastOutSlowInEasing),
      label = "musicSystemVolume",
    )
  val activeColor = lerp(MaterialTheme.colorScheme.primary, foregroundColor, .24f)
  Surface(
    modifier = modifier.height(32.dp).focusProperties { canFocus = !controlMode },
    shape = RoundedCornerShape(16.dp),
    color = Color.Black.copy(alpha = .20f),
    contentColor = foregroundColor,
    border = BorderStroke(.75.dp, Color.White.copy(alpha = .14f)),
  ) {
    Canvas(
      Modifier.fillMaxSize()
        .padding(horizontal = 13.dp)
        .semantics {
          contentDescription = "系统媒体音量"
          progressBarRangeInfo = ProgressBarRangeInfo(targetValue, 0f..1f)
          setProgress { requested ->
            onValueChange(requested.coerceIn(0f, 1f))
            true
          }
        }
        .pointerInput(onValueChange) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun updateVolume(x: Float) {
              if (size.width > 0) onValueChange((x / size.width).coerceIn(0f, 1f))
            }
            updateVolume(down.position.x)
            down.consume()
            var pressed = true
            while (pressed) {
              val event = awaitPointerEvent()
              pressed = false
              event.changes.forEach { change ->
                if (change.pressed) {
                  updateVolume(change.position.x)
                  change.consume()
                  pressed = true
                }
              }
            }
          }
        }
    ) {
      val centerY = size.height / 2f
      val trackStart = 5.dp.toPx()
      val trackEnd = (size.width - 5.dp.toPx()).coerceAtLeast(trackStart)
      val thumbX = trackStart + (trackEnd - trackStart) * animatedValue.coerceIn(0f, 1f)
      drawLine(
        color = foregroundColor.copy(alpha = .22f),
        start = Offset(trackStart, centerY),
        end = Offset(trackEnd, centerY),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
      )
      drawLine(
        color = activeColor.copy(alpha = .28f),
        start = Offset(trackStart, centerY),
        end = Offset(thumbX, centerY),
        strokeWidth = 7.dp.toPx(),
        cap = StrokeCap.Round,
      )
      drawLine(
        color = activeColor,
        start = Offset(trackStart, centerY),
        end = Offset(thumbX, centerY),
        strokeWidth = 3.4.dp.toPx(),
        cap = StrokeCap.Round,
      )
      drawCircle(
        color = foregroundColor.copy(alpha = .22f),
        radius = 6.5.dp.toPx(),
        center = Offset(thumbX, centerY),
      )
      drawCircle(
        color = foregroundColor,
        radius = 3.5.dp.toPx(),
        center = Offset(thumbX, centerY),
      )
    }
  }
}

@Composable
private fun GlassIconButton(
  onClick: () -> Unit,
  contentDescription: String,
  content: @Composable () -> Unit,
) {
  val controlMode = LocalControlMode.current
  Surface(
    modifier =
      Modifier.size(46.dp)
        .clip(CircleShape)
        .focusProperties { canFocus = !controlMode }
        .clickable(onClickLabel = contentDescription, onClick = onClick),
    shape = CircleShape,
    color = Color.Black.copy(alpha = .3f),
    contentColor = Color.White,
    border = BorderStroke(.75.dp, Color.White.copy(alpha = .18f)),
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
  }
}

@Composable
@OptIn(UnstableApi::class)
private fun MusicPlayerPane(
  state: HomeMusicUiState,
  viewModel: HomeMusicPlayerViewModel,
  playbackSurfaceEnabled: Boolean,
  menuHideProgress: Float,
  infoForegroundColor: Color,
  progressBarBounds: Rect,
  onPlayerBoundsChanged: (Rect) -> Unit,
  controlFocusRequest: Int,
  playPauseFocusRequest: Int,
  controlDismissTransientRequest: Int,
  onControlProgressRequested: () -> Unit,
  onControlLibraryRequested: () -> Unit,
  onControlTransientOpenChanged: (Boolean) -> Unit,
  modifier: Modifier,
) {
  val density = LocalDensity.current
  val progress = menuHideProgress.coerceIn(0f, 1f)
  var controlsBaseBounds by remember { mutableStateOf(Rect.Zero) }
  BoxWithConstraints(modifier) {
    // 给标题、封面和控制区预留固定的安全带。高度不足时缩小封面，避免居中布局
    // 向上溢出并压住顶部“音乐播放”标题。
    val playerInfoBudget = 92.dp
    val controlsBudget = 60.dp
    val blockSpacing = 36.dp
    val verticalSafety = 24.dp
    val maxSurfaceHeight =
      (maxHeight - playerInfoBudget - controlsBudget - blockSpacing - verticalSafety)
        .coerceAtLeast(120.dp)
    val surfaceWidth =
      minOf(maxWidth, maxSurfaceHeight * (16f / 9f)).coerceAtLeast(1.dp)
    val finalControlScale = 1.10f
    val controlGapPx = with(density) { 24.dp.toPx() }
    val controlsTravelX =
      if (controlsBaseBounds.width > 0f && progressBarBounds.width > 0f) {
        progressBarBounds.center.x - controlsBaseBounds.center.x
      } else {
        0f
      }
    val controlsTravelY =
      if (controlsBaseBounds.height > 0f && progressBarBounds.height > 0f) {
        val targetCenterY =
          progressBarBounds.top - controlGapPx - controlsBaseBounds.height * finalControlScale / 2f
        targetCenterY - controlsBaseBounds.center.y
      } else {
        0f
      }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
      Column(Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            state.currentItem?.let { displayTitle(it, state.displayNameOverrides) } ?: "从右侧选择一首音乐",
            color = infoForegroundColor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          state.currentItem?.uploader?.takeIf(String::isNotBlank)?.let { uploader ->
            Text(
              uploader,
              color = infoForegroundColor.copy(alpha = .68f),
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Spacer(Modifier.height(18.dp))
        Surface(
          modifier =
            Modifier.width(surfaceWidth)
              .aspectRatio(16f / 9f)
              .align(Alignment.CenterHorizontally)
              .onGloballyPositioned { onPlayerBoundsChanged(it.boundsInRoot()) },
          shape = VideoShapeTokens.Player,
          color = Color.Black.copy(alpha = .44f),
          border = BorderStroke(.75.dp, Color.White.copy(alpha = .18f)),
          shadowElevation = 0.dp,
        ) {
          Box(Modifier.fillMaxSize()) {
            state.currentItem
              ?.takeIf { playbackSurfaceEnabled }
              ?.let { item ->
                val player = viewModel.prepareVideoPlayer()
                val context = LocalContext.current
                val transitionCoverModel =
                  remember(context, item.coverUrl) {
                    CoverImageRequestFactory.request(
                      item.coverUrl,
                      coil3.request.ImageRequest.Builder(context),
                      width = 1600,
                      height = 900,
                    )
                  }
                AndroidView(
                  factory = { context ->
                    createTexturePlayerView(context, player).apply {
                      useController = false
                      isClickable = false
                      isFocusable = false
                      resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                      setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                  },
                  update = { playerView ->
                    if (playerView.player !== player) playerView.player = player
                    playerView.useController = false
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                  },
                  modifier = Modifier.fillMaxSize().graphicsLayer { alpha = .82f },
                )
                val transitionCoverAlpha = remember { Animatable(1f) }
                var transitionCoverItemId by remember { mutableStateOf<String?>(null) }
                val coverMustMaskPlayer = transitionCoverItemId != item.id || !state.firstFrameReady
                LaunchedEffect(item.id, state.firstFrameReady) {
                  if (transitionCoverItemId != item.id) {
                    transitionCoverItemId = item.id
                    transitionCoverAlpha.snapTo(1f)
                  }
                  if (state.firstFrameReady) {
                    transitionCoverAlpha.animateTo(0f, tween(170))
                  } else {
                    transitionCoverAlpha.snapTo(1f)
                  }
                }
                // 首帧之后保持此组合体挂载。切换曲目时，其 CrossfadeBackgroundImage
                // 保留已解码的上一个封面直到新的成功，让 TextureView 永远不会成为
                // 唯一可见的交接层。
                CrossfadeBackgroundImage(
                  model = transitionCoverModel,
                  modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                      // 条目身份变化在组合中生效，先于 LaunchedEffect 得到快照归位
                      // Animatable 的机会。对那第一帧交接也强制完全不透明，
                      // 否则已释放的 TextureView 可能泄漏穿过一次。
                      alpha = if (coverMustMaskPlayer) 1f else transitionCoverAlpha.value
                    },
                  contentScale = ContentScale.Crop,
                  transitionMillis = 90,
                )
              }
          }
        }
      }
      Spacer(Modifier.height(18.dp))
      MusicTransportControls(
        state = state,
        viewModel = viewModel,
        menuHideProgress = progress,
        controlFocusRequest = controlFocusRequest,
        playPauseFocusRequest = playPauseFocusRequest,
        controlDismissTransientRequest = controlDismissTransientRequest,
        onControlProgressRequested = onControlProgressRequested,
        onControlLibraryRequested = onControlLibraryRequested,
        onControlTransientOpenChanged = onControlTransientOpenChanged,
        modifier =
          Modifier.onGloballyPositioned {
              if (progress <= .001f) controlsBaseBounds = it.boundsInRoot()
            }
            .graphicsLayer {
              translationX = controlsTravelX * progress
              translationY = controlsTravelY * progress
              scaleX = 1f + .10f * progress
              scaleY = 1f + .10f * progress
            },
      )
    }
  }
}

@Composable
private fun MusicVolumeIcon(muted: Boolean) {
  val iconColor = if (muted) Color(0xFFFF4D5E) else Color.White
  Box(Modifier.size(23.dp)) {
    Icon(
      painter = painterResource(R.drawable.ic_gesture_volume),
      contentDescription = null,
      modifier = Modifier.fillMaxSize(),
      tint = iconColor,
    )
    if (muted) {
      Canvas(Modifier.fillMaxSize()) {
        drawLine(
          color = iconColor,
          start = Offset(size.width * .16f, size.height * .14f),
          end = Offset(size.width * .84f, size.height * .86f),
          strokeWidth = 2.2.dp.toPx(),
        )
      }
    }
  }
}

private fun formatMusicClockWithSeconds(): String =
  LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

internal fun formatMusicDuration(durationMs: Long): String {
  val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
  val hours = totalSeconds / 3_600L
  val minutes = (totalSeconds % 3_600L) / 60L
  val seconds = totalSeconds % 60L
  return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
  else "%d:%02d".format(minutes, seconds)
}

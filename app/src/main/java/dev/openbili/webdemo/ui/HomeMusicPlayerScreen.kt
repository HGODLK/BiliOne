package dev.openbili.webdemo.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import dev.openbili.webdemo.api.PremiumAudioMode
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.CoverImageRequestFactory
import dev.openbili.webdemo.feed.LocalCoverImageLoadingEnabled
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.music.HomeMusicPlayerViewModel
import dev.openbili.webdemo.music.HomeMusicUiState
import dev.openbili.webdemo.music.MusicFavoriteFolderTitle
import dev.openbili.webdemo.music.MusicLibraryStatus
import dev.openbili.webdemo.music.MusicPlaybackOrder
import dev.openbili.webdemo.music.MUSIC_SPECTRUM_PEAK_HOLD_MS
import dev.openbili.webdemo.music.MusicPlaybackProgressState
import dev.openbili.webdemo.music.advanceMusicPeak
import dev.openbili.webdemo.settings.AppSettings
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val MusicPaneShape = RoundedCornerShape(24.dp)
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
    val pageOffset = remember { Animatable(0f) }
    var pageReady by remember { mutableStateOf(false) }
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
    val effectiveMenuProgress =
      if (wideLayout && menuDragging) menuDragProgress
      else if (wideLayout) menuProgress.value else 0f
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
        blurred = true,
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
        // CrossfadeBackgroundImage reports success only after the decoded image has finished its
        // own fade. Keep one more complete frame of the captured home page underneath it before
        // releasing the bridge so RenderThread never exposes the Activity window background.
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
      // Load the blurred background, surface, palette and covers while the page is still off-screen,
      // then slide in only once the background is actually displayed. This removes the decode + View
      // creation burst that previously landed on the slide's final frame.
      entryAnimationInProgress = false
      withTimeoutOrNull(MUSIC_PAGE_ENTRY_PREPARE_TIMEOUT_MS) {
        while (!latestEntryBridgeReleaseReady) withFrameNanos {}
      }
      withFrameNanos {}
      // The page is fully on screen now. Release the entry input lock so touches work again,
      // independent of playback preparation which still runs during the delay below.
      onEntrySettled()
      pageOffset.animateTo(0f, tween(if (settings.reduceMotion) 90 else 260))
      delay(MUSIC_PAGE_PLAYBACK_START_DELAY_MS)
      viewModel.open(
        accountMid = accountMid,
        folderSelectionId = settings.musicFavoriteFolderId,
        folderSelectionConfigured = settings.musicFavoriteFolderConfigured,
      )
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
        // Initial/open state cleanup only. During reverse animation the explicit reopen sequence
        // owns these Animatables; snapping them here would cancel the menu animation sibling.
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
          // First collapse the responsive bars into their baseline, then remove the baseline.
          // Keeping these sequential makes a down event feel intentional without delaying drag.
          spectrumReveal.animateTo(0f, tween(if (settings.reduceMotion) 45 else 90))
          spectrumLineReveal.animateTo(0f, tween(if (settings.reduceMotion) 35 else 65))
        } finally {
          spectrumExitInProgress = false
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
        // Keep this overlay in the hit-test chain so touches never fall through to the home feed.
        // Do not consume events: child scrollables and the menu's horizontal drag detector must
        // remain free to win gesture arbitration.
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              awaitPointerEvent()
            }
          }
        }
        // Never make the entering page transparent. Transparent RenderNodes can briefly expose
        // the Activity window background when OriginOS/MIUI/One UI promote or rebuild the player
        // TextureView, which is perceived as a white flash.
        .background(MaterialTheme.colorScheme.background)
    ) {
      if (entryBridgeAlpha.value > .001f) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = entryBridgeAlpha.value }) {
          // These are the exact layers already drawn by HomeHubScreen in the preceding frame.
          // Drawing both into the music page makes the bridge part of this page's RenderNode,
          // rather than relying on a transparent hole revealing the live page underneath.
          if (entryUnderlayBounds.width > 0f && entryUnderlayBounds.height > 0f) {
            drawLayer(entryUnderlayLayer)
          }
          if (entryBackdropBounds.width > 0f && entryBackdropBounds.height > 0f) {
            drawLayer(entryBackdropLayer)
          }
        }
      }
      CompositionLocalProvider(
        LocalCoverImageLoadingEnabled provides !entryAnimationInProgress,
      ) {
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
        }

        Box(Modifier.fillMaxSize().onGloballyPositioned { ambientBounds = it.boundsInRoot() }) {
        val spectrumShouldRender =
          !settling && (spectrumExitInProgress || (menuSettledHidden && !hiddenMenuPointerPressed))
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
                modifier =
                  Modifier.fillMaxSize()
                    .onSizeChanged { menuWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .graphicsLayer {
                      // Keep one physical pixel in the viewport so the large blur RenderNode is
                      // not cold-culled and rebuilt when the menu returns.
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
              modifier = Modifier.fillMaxWidth().weight(1f),
            )
          }
        }
        Spacer(Modifier.height(58.dp))
      }

        MusicProgressBar(
        progressState = viewModel.progressState,
        onSeek = viewModel::seekTo,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth(.8f)
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .onGloballyPositioned { musicProgressBounds = it.boundsInRoot() },
      )

      // Once translated away the menu itself has almost no hit area. Keep a stable, invisible
      // screen-interior target for manual swipe-in, including during a partially reversed drag.
      // It deliberately extends well past the system edge-back strip while avoiding the header
      // and bottom progress controls.
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
    // Time-constant smoothing responds immediately to each newest FFT target and never restarts a
    // long tween. A fast attack keeps beats aligned with audio; the shorter release still avoids
    // visibly jittery bars on 120 Hz panels.
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
    val spectrumContentWidth = maxWidth * .34f
    val spectrumContentHeight = maxHeight * .40f
    val spectrumEndPadding = (maxWidth * .035f - spectrumGlowPadding).coerceAtLeast(0.dp)
    val spectrumTopOffset =
      maxHeight * (.335f - .045f * lineReveal.coerceIn(0f, 1f)) - spectrumGlowPadding
    Canvas(
      Modifier.align(Alignment.TopEnd)
        .padding(end = spectrumEndPadding)
        .width(spectrumContentWidth + spectrumGlowPadding * 2f)
        .height(spectrumContentHeight + spectrumGlowPadding * 2f)
        .graphicsLayer { translationY = spectrumTopOffset.toPx() }
    ) {
      if (!wideLayout || lineReveal <= .001f) return@Canvas
      val glowPaddingPx = spectrumGlowPadding.toPx()
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
      // Observe the in-place band buffer from the draw phase. The version invalidates only this
      // Canvas and avoids allocating a boxed List<Float> on every display-synchronised frame.
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
        // Layered strokes retain a bright feathered edge without BlurMaskFilter. A blur mask on
        // 28 continuously changing paths makes Skia cache thousands of transient masks and is the
        // source of the progressive long-play jank.
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
          // The round lower cap lands exactly on the baseline and never leaks below it.
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
      val spectrumCenterX = maxWidth * (1f - .035f - .34f / 2f)
      val initialClockCenterX = maxWidth - 30.dp - clockWidth / 2f
      val clockTravelX = spectrumCenterX - initialClockCenterX
      val initialClockCenterPx = with(density) { 30.dp.toPx() }
      val playerClockTargetPx =
        if (playerBounds.width > 0f && playerBounds.height > 0f && ambientBounds.height > 0f) {
          // The requested two-thirds position is measured upward from the player's bottom.
          playerBounds.top - ambientBounds.top + playerBounds.height * (1f / 3f)
        } else {
          with(density) { (maxHeight * .42f).toPx() }
        }
      val clockTravelY =
        with(density) { (playerClockTargetPx - initialClockCenterPx).coerceAtLeast(0f).toDp() }
      Box(
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(top = 14.dp, end = 30.dp)
            .width(clockWidth)
            .graphicsLayer {
              translationX = clockTravelX.toPx() * progress
              translationY = clockTravelY.toPx() * progress
              scaleX = 1f + 3.1f * progress
              scaleY = 1f + 3.1f * progress
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
  val paint =
    remember {
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
  val targetValue = value.coerceIn(0f, 1f)
  val animatedValue by
    animateFloatAsState(
      targetValue = targetValue,
      animationSpec = tween(180, easing = FastOutSlowInEasing),
      label = "musicSystemVolume",
    )
  val activeColor = lerp(MaterialTheme.colorScheme.primary, foregroundColor, .24f)
  Surface(
    modifier = modifier.height(32.dp),
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
  Surface(
    modifier =
      Modifier.size(46.dp)
        .clip(CircleShape)
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
  modifier: Modifier,
) {
  val density = LocalDensity.current
  val playerLiftPx = with(density) { 28.dp.toPx() }
  val progress = menuHideProgress.coerceIn(0f, 1f)
  var controlsBaseBounds by remember { mutableStateOf(Rect.Zero) }
  Box(modifier) {
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
      Column(Modifier.fillMaxWidth().graphicsLayer { translationY = -playerLiftPx }) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            state.currentItem?.title ?: "从右侧选择一首音乐",
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
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).onGloballyPositioned {
              onPlayerBoundsChanged(it.boundsInRoot())
            },
          shape = VideoShapeTokens.Player,
          color = Color.Black.copy(alpha = .44f),
          border = BorderStroke(.75.dp, Color.White.copy(alpha = .18f)),
          shadowElevation = 0.dp,
        ) {
          Box(Modifier.fillMaxSize()) {
            state.currentItem?.takeIf { playbackSurfaceEnabled }?.let { item ->
              val player = viewModel.preparePlayer()
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
              // Keep this composable mounted after the first frame. On a track change its
              // CrossfadeBackgroundImage retains the already-decoded previous cover until the new
              // one succeeds, so the TextureView can never become the only visible handoff layer.
              CrossfadeBackgroundImage(
                model = transitionCoverModel,
                modifier =
                  Modifier.fillMaxSize().graphicsLayer {
                    // Item identity changes are applied in composition, before LaunchedEffect gets
                    // a chance to snap the Animatable. Force full opacity for that first handoff
                    // frame as well, otherwise a released TextureView can leak through once.
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
private fun MusicTransportControls(
  state: HomeMusicUiState,
  viewModel: HomeMusicPlayerViewModel,
  menuHideProgress: Float,
  modifier: Modifier,
) {
  Row(
    modifier.fillMaxWidth(),
    horizontalArrangement =
      Arrangement.spacedBy(
        16.dp + 10.dp * menuHideProgress.coerceIn(0f, 1f),
        Alignment.CenterHorizontally,
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    MusicControlButton(
      onClick = viewModel::togglePlaybackOrder,
      enabled = state.items.isNotEmpty(),
      description = if (state.playbackOrder == MusicPlaybackOrder.RANDOM) "随机播放" else "顺序播放",
    ) {
      Icon(
        if (state.playbackOrder == MusicPlaybackOrder.RANDOM) Icons.Default.Shuffle
        else Icons.Default.Repeat,
        contentDescription = null,
      )
    }
    MusicControlButton(
      onClick = viewModel::playPrevious,
      enabled = state.items.isNotEmpty(),
      description = "上一首",
    ) {
      Icon(Icons.Default.SkipPrevious, contentDescription = null)
    }
    MusicControlButton(
      onClick = viewModel::togglePlayback,
      enabled = state.currentItem != null && !state.playbackLoading,
      description = if (state.isPlaying) "暂停" else "播放",
      emphasized = true,
    ) {
      Icon(
        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = null,
      )
    }
    MusicControlButton(
      onClick = viewModel::playNext,
      enabled = state.items.isNotEmpty(),
      description = "下一首",
    ) {
      Icon(Icons.Default.SkipNext, contentDescription = null)
    }
    AdvancedAudioButton(
      state = state,
      onSelect = viewModel::selectPremiumAudio,
    )
  }
}

@Composable
private fun MusicControlButton(
  onClick: () -> Unit,
  enabled: Boolean,
  description: String,
  emphasized: Boolean = false,
  content: @Composable () -> Unit,
) {
  val contentColor = if (enabled) Color.White else Color.White.copy(alpha = .28f)
  Surface(
    modifier =
      Modifier.size(if (emphasized) 60.dp else 52.dp)
        .clip(CircleShape)
        .clickable(
          enabled = enabled,
          onClickLabel = description,
          onClick = onClick,
        ),
    shape = CircleShape,
    color =
      if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = .36f)
      else Color.Black.copy(alpha = .30f),
    contentColor = contentColor,
    border = BorderStroke(.75.dp, Color.White.copy(alpha = if (enabled) .18f else .08f)),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
  }
}

@Composable
private fun AdvancedAudioButton(
  state: HomeMusicUiState,
  onSelect: (PremiumAudioMode?) -> Unit,
) {
  val available = state.dolbyAvailable || state.hiResAvailable
  var expanded by remember { mutableStateOf(false) }
  Box {
    Surface(
      modifier =
        Modifier.size(52.dp).clip(CircleShape).clickable(
          enabled = available,
          onClickLabel = "高级音质",
        ) {
          expanded = true
        },
      shape = CircleShape,
      color =
        if (state.selectedPremiumAudio != null) MaterialTheme.colorScheme.primary.copy(alpha = .34f)
        else Color.Black.copy(alpha = .30f),
      contentColor = if (available) Color.White else Color.White.copy(alpha = .28f),
      border = BorderStroke(.75.dp, Color.White.copy(alpha = if (available) .18f else .08f)),
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
    ) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
          Icons.Default.MusicNote,
          contentDescription = null,
        )
      }
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      containerColor = Color(0xEE202124),
    ) {
      PremiumAudioMenuItem(
        label = "标准音质",
        selected = state.selectedPremiumAudio == null,
        enabled = true,
      ) {
        onSelect(null)
        expanded = false
      }
      PremiumAudioMenuItem(
        label = "Dolby Atmos",
        selected = state.selectedPremiumAudio == PremiumAudioMode.DOLBY,
        enabled = state.dolbyAvailable,
      ) {
        onSelect(PremiumAudioMode.DOLBY)
        expanded = false
      }
      PremiumAudioMenuItem(
        label = "Hi-Res",
        selected = state.selectedPremiumAudio == PremiumAudioMode.HI_RES,
        enabled = state.hiResAvailable,
      ) {
        onSelect(PremiumAudioMode.HI_RES)
        expanded = false
      }
    }
  }
}

@Composable
private fun PremiumAudioMenuItem(
  label: String,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  DropdownMenuItem(
    text = { Text(label, color = if (enabled) Color.White else Color.White.copy(alpha = .32f)) },
    onClick = onClick,
    enabled = enabled,
    trailingIcon = {
      if (selected) {
        Icon(
          Icons.Default.Check,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    },
  )
}

@Composable
private fun MusicLibraryPane(
  state: HomeMusicUiState,
  viewModel: HomeMusicPlayerViewModel,
  onLoginClick: (Rect) -> Unit,
  onFavoriteFolderSelected: (Long) -> Unit,
  backdropLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
  backdropBounds: Rect,
  underlayLayer: androidx.compose.ui.graphics.layer.GraphicsLayer?,
  underlayBounds: Rect,
  modifier: Modifier,
) {
  var loginBounds by remember { mutableStateOf(Rect.Zero) }
  val darkTheme = MaterialTheme.colorScheme.background.luminance() < .5f
  val containerAlpha =
    if (darkTheme) HomeGlassTokens.DarkContainerAlpha else HomeGlassTokens.LightContainerAlpha
  val borderAlpha =
    if (darkTheme) HomeGlassTokens.DarkBorderAlpha else HomeGlassTokens.LightBorderAlpha
  BackdropGlassSurface(
    backdropLayer = backdropLayer,
    backdropBounds = backdropBounds,
    underlayLayer = underlayLayer,
    underlayBounds = underlayBounds,
    modifier = modifier,
    shape = MusicPaneShape,
    blurRadius = HomeGlassTokens.BlurRadius,
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha),
    fallbackColor = MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        .75.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha),
      ),
  ) {
    Column(Modifier.fillMaxSize().padding(14.dp)) {
      if (state.libraryStatus != MusicLibraryStatus.SIGNED_OUT && state.folder != null) {
        TextField(
          value = state.query,
          onValueChange = viewModel::updateQuery,
          modifier = Modifier.fillMaxWidth().height(52.dp),
          singleLine = true,
          shape = RoundedCornerShape(17.dp),
          placeholder = {
            Text(
              "搜索收藏夹中的视频",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索音乐") },
          trailingIcon = {
            if (state.query.isNotBlank()) {
              androidx.compose.material3.IconButton(onClick = { viewModel.updateQuery("") }) {
                Icon(Icons.Default.Close, contentDescription = "清空音乐搜索")
              }
            }
          },
          colors =
            TextFieldDefaults.colors(
              focusedTextColor = MaterialTheme.colorScheme.onSurface,
              unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
              focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
              unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
              focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
              unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
              unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              focusedContainerColor =
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .58f),
              unfocusedContainerColor =
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .42f),
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              disabledIndicatorColor = Color.Transparent,
              cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.height(10.dp))
      }
      when (state.libraryStatus) {
        MusicLibraryStatus.SIGNED_OUT ->
          MusicLibraryMessage(
            title = "登录后才能读取个人收藏夹",
            action = "去登录",
            onAction = { onLoginClick(loginBounds) },
            actionModifier = Modifier.onGloballyPositioned { loginBounds = it.boundsInRoot() },
          )
        MusicLibraryStatus.LOADING ->
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        MusicLibraryStatus.MISSING ->
          MusicFolderSetupMessage(
            state = state,
            onFolderSelected = onFavoriteFolderSelected,
            onCreateMusicFolder = {
              viewModel.createMusicFolder(onCreated = onFavoriteFolderSelected)
            },
          )
        MusicLibraryStatus.ERROR ->
          MusicLibraryMessage(
            title = state.libraryError ?: "音乐收藏夹加载失败",
            action = "重新加载",
            onAction = viewModel::retryLibrary,
          )
        MusicLibraryStatus.READY -> {
          if (state.items.isEmpty() && state.query.isBlank()) {
            MusicLibraryMessage(
              title = "“${state.folder?.title ?: MusicFavoriteFolderTitle}”收藏夹还是空的",
              subtitle = "先在收藏夹中加入一些视频吧。",
            )
          } else {
            MusicTrackList(state = state, viewModel = viewModel)
          }
        }
      }
      state.libraryError
        ?.takeIf {
          state.libraryStatus == MusicLibraryStatus.READY ||
            state.libraryStatus == MusicLibraryStatus.MISSING
        }
        ?.let { error ->
          Text(
            error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
    }
  }
}

@Composable
private fun MusicFolderSetupMessage(
  state: HomeMusicUiState,
  onFolderSelected: (Long) -> Unit,
  onCreateMusicFolder: () -> Unit,
) {
  var folderMenuExpanded by remember { mutableStateOf(false) }
  val exactMusicFolder =
    remember(state.availableFolders) {
      state.availableFolders.firstOrNull { it.title.trim() == MusicFavoriteFolderTitle }
    }
  Column(
    Modifier.fillMaxSize().padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      if (state.folderSelectionConfigured) "原先选择的收藏夹已不可用" else "选择音乐收藏夹",
      color = MaterialTheme.colorScheme.onSurface,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      "可以选择一个已有的个人收藏夹，或新建私密“音乐”收藏夹。",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    Box {
      TextButton(
        onClick = { folderMenuExpanded = true },
        enabled = state.availableFolders.isNotEmpty() && !state.creatingFolder,
      ) {
        Text(if (state.availableFolders.isEmpty()) "暂无已有收藏夹" else "选择已有收藏夹")
      }
      DropdownMenu(
        expanded = folderMenuExpanded,
        onDismissRequest = { folderMenuExpanded = false },
      ) {
        state.availableFolders.forEach { folder ->
          DropdownMenuItem(
            text = {
              Column {
                Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  "${folder.mediaCount} 个内容 · ${if (folder.isPublic) "公开" else "私密"}",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            },
            onClick = {
              folderMenuExpanded = false
              onFolderSelected(folder.id)
            },
          )
        }
      }
    }
    if (exactMusicFolder == null) {
      Button(
        onClick = onCreateMusicFolder,
        enabled = !state.creatingFolder,
      ) {
        Text(if (state.creatingFolder) "正在创建" else "创建“音乐”收藏夹")
      }
    }
    Spacer(Modifier.height(14.dp))
    Text(
      "之后可随时在“我的 > 设置 > 音乐播放器收藏夹”中修改。",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
private fun MusicTrackList(
  state: HomeMusicUiState,
  viewModel: HomeMusicPlayerViewModel,
) {
  val listState = rememberLazyListState()
  val context = LocalContext.current
  val hostView = LocalView.current
  var deleteCandidateId by remember { mutableStateOf<String?>(null) }
  var locateCurrentPending by remember { mutableStateOf(false) }
  LaunchedEffect(state.items, deleteCandidateId) {
    if (deleteCandidateId != null && state.items.none { it.id == deleteCandidateId }) {
      deleteCandidateId = null
    }
  }
  val loadMore by
    remember(state.items.size, state.hasMore) {
      derivedStateOf {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        state.hasMore && last >= state.items.lastIndex - 3
      }
    }
  LaunchedEffect(loadMore) {
    if (loadMore) viewModel.loadMore()
  }
  LaunchedEffect(
    locateCurrentPending,
    state.libraryStatus,
    state.query,
    state.currentItem?.id,
    state.items,
    state.loadingMore,
    state.hasMore,
  ) {
    if (
      !locateCurrentPending ||
        state.query.isNotBlank() ||
        state.libraryStatus != MusicLibraryStatus.READY
    ) {
      return@LaunchedEffect
    }
    val currentIndex = state.items.indexOfFirst { it.id == state.currentItem?.id }
    when {
      currentIndex >= 0 -> {
        listState.scrollToItem(currentIndex)
        locateCurrentPending = false
      }
      state.hasMore && !state.loadingMore -> viewModel.loadMore()
      !state.loadingMore -> locateCurrentPending = false
    }
  }
  Box(Modifier.fillMaxSize()) {
    if (state.items.isEmpty()) {
      MusicLibraryMessage(title = "没有找到相关音乐")
    }
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 58.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(state.items, key = FeedItem::id) { item ->
        val deleteSelected = deleteCandidateId == item.id
        val deleting = item.id in state.deletingItemIds
        val playing = state.currentItem?.id == item.id
        val cardBlur by
          animateDpAsState(
            targetValue = if (deleteSelected) 14.dp else 0.dp,
            animationSpec = tween(150),
            label = "musicDeleteCardBlur",
          )
        Box(Modifier.fillMaxWidth()) {
          Surface(
            modifier =
              Modifier.fillMaxWidth()
                .blur(cardBlur)
                .clip(RoundedCornerShape(17.dp))
                .combinedClickable(
                  enabled = !deleteSelected && !deleting,
                  onClick = { viewModel.selectItem(item) },
                  onLongClick = {
                    performMusicDeleteVibration(context, hostView)
                    deleteCandidateId = item.id
                  },
                ),
            shape = RoundedCornerShape(17.dp),
            color = Color.Transparent,
            border =
              BorderStroke(
                if (playing) 2.75.dp else .5.dp,
                if (playing) MaterialTheme.colorScheme.primary.copy(alpha = .96f)
                else Color.White.copy(alpha = .12f),
              ),
          ) {
            VideoCardGradient(
              coverUrl = item.coverUrl,
              overlayStyle = true,
              backgroundAlpha = .68f,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                CoverImage(
                  coverUrl = item.coverUrl,
                  contentDescription = item.title,
                  modifier = Modifier.width(112.dp).aspectRatio(16f / 9f),
                  shape = RoundedCornerShape(12.dp),
                  requestWidth = 448,
                  requestHeight = 252,
                  loadKey = "home-music-list-${item.id}",
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    listOfNotNull(item.uploader, item.duration).joinToString(" · ").ifBlank { " " },
                    color = Color.White.copy(alpha = .64f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
          if (playing && !deleteSelected) {
            val highlightShape = RoundedCornerShape(17.dp)
            Box(
              Modifier.matchParentSize()
                .clip(highlightShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .08f))
                .border(2.75.dp, MaterialTheme.colorScheme.primary, highlightShape)
                .padding(3.5.dp)
                .border(
                  .9.dp,
                  Color.White.copy(alpha = .78f),
                  RoundedCornerShape(13.5.dp),
                )
            )
          }
          if (deleteSelected) {
            Row(
              Modifier.matchParentSize()
                .clip(RoundedCornerShape(17.dp))
                .background(Color.Black.copy(alpha = .18f))
                .padding(horizontal = 18.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              TextButton(
                onClick = { deleteCandidateId = null },
                enabled = !deleting,
              ) {
                Text("取消", color = Color.White)
              }
              Button(
                onClick = { viewModel.removeFromMusicFolder(item) },
                enabled = !deleting,
              ) {
                if (deleting) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                  )
                } else {
                  Text("删除")
                }
              }
            }
          }
        }
      }
      if (state.loadingMore) {
        item(key = "music_loading_more") {
          Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = Color.White,
              strokeWidth = 2.dp,
            )
          }
        }
      }
    }
    state.currentItem?.let {
      Surface(
        modifier =
          Modifier.align(Alignment.BottomEnd).padding(8.dp).size(42.dp).clip(CircleShape).clickable(
            enabled = !locateCurrentPending,
            onClickLabel = "定位到当前播放的视频",
          ) {
            locateCurrentPending = true
            if (state.query.isNotBlank()) viewModel.clearQueryForCurrentTrack()
          },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = .88f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(.75.dp, Color.White.copy(alpha = .24f)),
        shadowElevation = 4.dp,
      ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          if (locateCurrentPending) {
            CircularProgressIndicator(
              modifier = Modifier.size(19.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          } else {
            Icon(
              Icons.Default.MyLocation,
              contentDescription = null,
              modifier = Modifier.size(21.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MusicLibraryMessage(
  title: String,
  subtitle: String? = null,
  action: String? = null,
  actionEnabled: Boolean = true,
  onAction: () -> Unit = {},
  actionModifier: Modifier = Modifier,
) {
  Column(
    Modifier.fillMaxSize().padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      title,
      color = MaterialTheme.colorScheme.onSurface,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    subtitle?.let {
      Spacer(Modifier.height(8.dp))
      Text(
        it,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    action?.let {
      Spacer(Modifier.height(18.dp))
      Button(
        onClick = onAction,
        enabled = actionEnabled,
        modifier = actionModifier,
      ) {
        Text(it)
      }
    }
  }
}

@Composable
private fun MusicProgressBar(
  progressState: StateFlow<MusicPlaybackProgressState>,
  onSeek: (Long) -> Unit,
  modifier: Modifier,
) {
  val progress by progressState.collectAsState()
  val duration = progress.durationMs.coerceAtLeast(1L)
  var dragFraction by remember { mutableStateOf<Float?>(null) }
  val playedFraction =
    dragFraction ?: (progress.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
  val enabled = progress.enabled
  val activeColor = MaterialTheme.colorScheme.primary
  val inactiveColor = Color.Gray.copy(alpha = .66f)
  val trackHeight = 5.dp
  val thumbRadius = 5.5.dp
  Canvas(
    modifier
      .height(24.dp)
      .semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(playedFraction, 0f..1f)
      }
      .pointerInput(enabled, duration) {
        if (!enabled) return@pointerInput
        detectTapGestures { offset ->
          onSeek(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
        }
      }
      .pointerInput(enabled, duration) {
        if (!enabled) return@pointerInput
        detectHorizontalDragGestures(
          onDragStart = { offset ->
            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
          },
          onHorizontalDrag = { change, _ ->
            change.consume()
            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
          },
          onDragEnd = {
            dragFraction?.let { onSeek((it * duration).toLong()) }
            dragFraction = null
          },
          onDragCancel = { dragFraction = null },
        )
      }
  ) {
    val centerY = size.height / 2f
    val endX = size.width * playedFraction
    drawLine(
      color = inactiveColor,
      start = Offset(0f, centerY),
      end = Offset(size.width, centerY),
      strokeWidth = trackHeight.toPx(),
    )
    drawLine(
      color = if (enabled) activeColor else activeColor.copy(alpha = .32f),
      start = Offset(0f, centerY),
      end = Offset(endX, centerY),
      strokeWidth = trackHeight.toPx(),
    )
    if (enabled) {
      drawCircle(
        color = activeColor,
        radius = thumbRadius.toPx(),
        center = Offset(endX, centerY),
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

private fun performMusicDeleteVibration(context: Context, fallbackView: android.view.View) {
  val vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  if (vibrator?.hasVibrator() == true) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      vibrator.vibrate(VibrationEffect.createOneShot(42L, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
      @Suppress("DEPRECATION") vibrator.vibrate(42L)
    }
  } else {
    fallbackView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
  }
}

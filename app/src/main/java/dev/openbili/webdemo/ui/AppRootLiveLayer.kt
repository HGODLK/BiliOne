package dev.openbili.webdemo.ui

/**
 * 直播层 UI：从根 Box 内渲染直播房间页与直播转场。
 */

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.openbili.webdemo.AuthViewModel
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.live.LiveRoomScreen
import dev.openbili.webdemo.live.LiveRoomViewModel
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.live.currentDisplayCoverUrl
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.AppSettingsViewModel
import dev.openbili.webdemo.video.PlaybackPageGlassBackdrop
import kotlinx.coroutines.Job

/** 直播层组合体。 */
@Composable
internal fun AppRootLiveLayer(
  settings: AppSettings,
  authUserInfo: UserInfo,
  profileStack: List<ProfileStackEntry>,
  profileLayerSuppressed: Boolean,
  activeLiveRoom: LiveSearchRoom?,
  activeLiveEntryId: Long,
  liveTransitionSession: CardTransitionSession?,
  liveTransitionJob: Job?,
  liveExitPrelude: VideoExitPrelude?,
  liveVideoSurfaceVisibleState: MutableState<Boolean>,
  liveFullscreenTransitionActiveState: MutableState<Boolean>,
  liveFirstFrameEntryIdState: MutableState<Long>,
  livePlayerBoundsState: MutableState<Rect>,
  liveRoomParentStack: List<LiveRoomParentFrame>,
  hiddenLiveRecommendationCoverItemIdState: MutableState<String?>,
  liveRoomStateHolder: SaveableStateHolder,
  liveRecommendationCardBounds: SnapshotStateMap<String, Rect>,
  livePageAlpha: Animatable<Float, AnimationVector1D>,
  liveTransitionContext: AppRootLiveTransitionContext,
  renderedVideoId: String?,
  renderedVideoFrameCount: Int,
  playerViewModel: PlayerViewModel,
  authViewModel: AuthViewModel,
  settingsViewModel: AppSettingsViewModel,
  rootPlayerContent: @Composable (Modifier, Float, Boolean, Boolean, SharedPlayerViewRole, Boolean?, Boolean) -> Unit,
  openAvatarProfileRef: (Long, Rect, String?, String?) -> Unit,
) {
  var liveVideoSurfaceVisible by liveVideoSurfaceVisibleState
  var liveFullscreenTransitionActive by liveFullscreenTransitionActiveState
  var liveFirstFrameEntryId by liveFirstFrameEntryIdState
  var livePlayerBounds by livePlayerBoundsState
  var hiddenLiveRecommendationCoverItemId by hiddenLiveRecommendationCoverItemIdState
  fun openAvatarProfile(mid: Long, bounds: Rect, face: String?, name: String?) =
    openAvatarProfileRef(mid, bounds, face, name)

  activeLiveRoom?.let { liveRoom ->
    val liveRootEntrySession = liveTransitionSession?.takeIf {
      it.kind == TransitionKind.ENTER_ROOT
    }
    val liveRootEntryFlight =
      liveRootEntrySession?.phase in
        setOf(SessionPhase.PREPARING, SessionPhase.READY, SessionPhase.FLYING)
    if (liveRootEntrySession != null) {
      // 直播在整页隐藏期间使用与点播相同的小型背景桥接。
      RootVideoEntryBackdrop(
        settings = settings,
        playerBounds = Rect.Zero,
        revealAlpha = { livePageAlpha.value },
        punchPlayerPortal = false,
        showCustomImageScrim = false,
      )
    }
    val hasCustomLiveBackground = settings.videoBackgroundUri.isNotBlank()
    val currentLiveCover = liveRoom.currentDisplayCoverUrl()
    // 该状态刻意放在房间 key 之外：保留旧房间封面，直到替换流呈现三帧后，再让
    // 共享背景组件在解码出的模糊位图间交叉淡变。
    var committedLiveCoverBackground by remember { mutableStateOf("") }
    LaunchedEffect(
      hasCustomLiveBackground,
      settings.useVideoCoverBackground,
      renderedVideoId,
      renderedVideoFrameCount,
      liveRoom.roomId,
      currentLiveCover,
    ) {
      when {
        hasCustomLiveBackground || !settings.useVideoCoverBackground ->
          committedLiveCoverBackground = ""
        renderedVideoId == "live:${liveRoom.roomId}" &&
          renderedVideoFrameCount >= 3 &&
          currentLiveCover.isNotBlank() -> committedLiveCoverBackground = currentLiveCover
      }
    }
    val useLiveCoverBackground =
      !hasCustomLiveBackground &&
        settings.useVideoCoverBackground &&
        committedLiveCoverBackground.isNotBlank()
    val effectiveLiveBackgroundSource =
      if (hasCustomLiveBackground) settings.videoBackgroundUri
      else if (useLiveCoverBackground) committedLiveCoverBackground else ""
    val effectiveLiveBackgroundBlurred =
      useLiveCoverBackground || (hasCustomLiveBackground && settings.videoBackgroundBlur)
    val liveBackgroundModel =
      rememberStaticBackgroundModel(
        source = effectiveLiveBackgroundSource,
        blurred = effectiveLiveBackgroundBlurred,
      )
    val liveBackgroundRevealAlpha by
      animateFloatAsState(
        targetValue = if (liveBackgroundModel != null) 1f else 0f,
        animationSpec = tween(if (settings.reduceMotion) 90 else 560),
        label = "liveBackgroundReveal",
      )
    val liveBackgroundLuminance = rememberBackgroundLuminanceProfile(liveBackgroundModel)
    val darkLivePage = MaterialTheme.colorScheme.background.luminance() < .5f
    val liveHeaderForeground by
      androidx.compose.animation.animateColorAsState(
        targetValue =
          videoBackgroundForeground(
            luminance = liveBackgroundLuminance?.top,
            darkMode = darkLivePage,
            fallback = MaterialTheme.colorScheme.onBackground,
          ),
        animationSpec = tween(if (settings.reduceMotion) 90 else 220),
        label = "liveHeaderForeground",
      )
    val liveContentForeground by
      androidx.compose.animation.animateColorAsState(
        targetValue =
          videoBackgroundForeground(
            luminance = liveBackgroundLuminance?.middle,
            darkMode = darkLivePage,
            fallback = MaterialTheme.colorScheme.onBackground,
          ),
        animationSpec = tween(if (settings.reduceMotion) 90 else 220),
        label = "liveContentForeground",
      )
    val liveBackgroundScrim =
      videoBackgroundScrim(
        profile = liveBackgroundLuminance,
        darkMode = darkLivePage,
      )
    val liveBackdropLayer = rememberGraphicsLayer()
    var liveBackdropBounds by remember { mutableStateOf(Rect.Zero) }
    Box(
      Modifier.fillMaxSize().graphicsLayer {
        alpha = if (liveRootEntryFlight) 0f else livePageAlpha.value
      }
    ) {
      Box(
        Modifier.fillMaxSize()
          .onGloballyPositioned { liveBackdropBounds = it.boundsInRoot() }
          .drawWithContent {
            liveBackdropLayer.record { this@drawWithContent.drawContent() }
            drawLayer(liveBackdropLayer)
          }
      ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        if (effectiveLiveBackgroundSource.isNotBlank() && liveBackgroundModel != null) {
          CrossfadeBackgroundImage(
            model = liveBackgroundModel,
            modifier =
              Modifier.fillMaxSize().graphicsLayer {
                alpha =
                  liveBackgroundRevealAlpha *
                    if (effectiveLiveBackgroundBlurred) 1f
                    else 1f - settings.videoBackgroundTransparency.coerceIn(0f, 1f)
              },
            contentScale = ContentScale.Crop,
            transitionMillis = if (settings.reduceMotion) 90 else 520,
          )
          Box(
            Modifier.fillMaxSize()
              .graphicsLayer { alpha = liveBackgroundRevealAlpha }
              .drawBehind { drawRect(liveBackgroundScrim) }
          )
        }
      }
      key(activeLiveEntryId) {
        liveRoomStateHolder.SaveableStateProvider("live:$activeLiveEntryId") {
          val roomViewModel: LiveRoomViewModel = viewModel(key = "live_room_${liveRoom.roomId}")
          LiveRoomScreen(
            entry = liveRoom,
            navigationEntryId = activeLiveEntryId,
            account = authUserInfo,
            player = playerViewModel.preparePlayer(publishSystemControls = false),
            playerView = { modifier, fullscreenProgress, fullscreen ->
              if (profileStack.isEmpty()) {
                rootPlayerContent(
                  modifier,
                  fullscreenProgress,
                  fullscreen,
                  false,
                  SharedPlayerViewRole.DETAIL,
                  liveVideoSurfaceVisible,
                  true,
                )
              }
            },
            onPlaySource = playerViewModel::playLive,
            onStopPlayback = playerViewModel::stopLive,
            onSeekLiveEdge = playerViewModel::seekToLiveEdge,
            onFullscreenTransitionChanged = { liveFullscreenTransitionActive = it },
            pageTransitionSuppressed =
              liveTransitionJob != null ||
                liveTransitionSession != null ||
                liveExitPrelude != null ||
                (profileStack.isNotEmpty() && !profileLayerSuppressed),
            onBack = {
              if (liveRoomParentStack.isNotEmpty()) startBackToPreviousLive(liveTransitionContext) else startExitLive(liveTransitionContext)
            },
            onHome = { startExitLive(liveTransitionContext, closeSearchAfter = true) },
            onLogin = authViewModel::startLogin,
            onAnchorProfile = { mid, face, name, bounds ->
              playerViewModel.exoPlayer?.pause()
              openAvatarProfile(mid, bounds, face, name)
            },
            onRecommendedRoom = { room, bounds -> startEnterRecommendedLive(liveTransitionContext, room, bounds) },
            onRecommendedRoomBoundsChanged = { room, bounds ->
              if (bounds.hasUsableSize()) {
                liveRecommendationCardBounds[
                  liveRecommendationBoundsKey(liveRoom.roomId, room.roomId)] = bounds
              }
            },
            hiddenRecommendationCoverItemId = hiddenLiveRecommendationCoverItemId,
            settings = settings,
            onSettingsChange = settingsViewModel::update,
            onPlayerBoundsChanged = { bounds -> livePlayerBounds = bounds },
            onFirstVideoFrameRendered = { entryId ->
              if (entryId == activeLiveEntryId) liveFirstFrameEntryId = entryId
            },
            headerForegroundColor = liveHeaderForeground,
            contentForegroundColor = liveContentForeground,
            glassBackdrop = PlaybackPageGlassBackdrop(liveBackdropLayer, liveBackdropBounds),
            active = profileStack.isEmpty() && !liveRootEntryFlight,
            viewModel = roomViewModel,
          )
        }
      }
    }
  }
}

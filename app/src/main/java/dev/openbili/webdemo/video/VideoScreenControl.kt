package dev.openbili.webdemo.video

/**
 * 视频页控制层：遥控器（控制器）导航模式下，视频页主体与顶层控制器转场函数
 * 共享的可变状态与交互逻辑。
 *
 * 本文件把共享状态收敛为 [VideoControlState]，并提供一组纯函数与 @Composable 效果：
 *  - 焦点申请（[requestVideoControlFocus]）与按键分发（[handleControlPlayerKey]）；
 *  - 详情页推荐跳转、评论图片预览的进出转场；
 *  - 播放结束接管、自动连播、内嵌播放器边界冻结等页面级效果；
 *  - 控制器模式下的返回键处理、播放器表面焦点修饰符与控件超时隐藏。
 *
 * 约束：本文件只修改状态并调用回调，不持有 UI 组合；真正的界面在 VideoScreen 的
 * 各拆分文件中，通过参数共享同一个 [VideoControlState] 实例。
 */

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import android.view.KeyEvent as AndroidKeyEvent
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.ui.CONTROL_DOUBLE_CONFIRM_TIMEOUT_MS
import dev.openbili.webdemo.ui.CONTROL_SEEK_STEP_MS
import dev.openbili.webdemo.ui.ControlVideoMode
import dev.openbili.webdemo.ui.ControlVideoSurfaceAction
import dev.openbili.webdemo.ui.SessionPhase
import dev.openbili.webdemo.ui.TransitionPreparationResult
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.ui.controlFocusOutline
import dev.openbili.webdemo.ui.resolveControlVideoSurfaceAction
import dev.openbili.webdemo.ui.shouldRestorePlaybackControlFocus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 视频页主体与下方顶层控制器转场函数之间共享的可变控制器状态。
 *
 * 视频页保留 `by` 委托访问器，使主体其余部分无需改动即可读写同一批值。
 */
internal class VideoControlState(
  val videoMode: MutableState<ControlVideoMode>,
  val controlsVisible: MutableState<Boolean>,
  val playerSurfaceFocused: MutableState<Boolean>,
  val pendingSingleConfirmJob: MutableState<Job?>,
)

/**
 * 在协程中申请焦点。
 *
 * 连续两次 [withFrameNanos] 等待两帧，确保目标组件完成布局后再请求焦点，避免布局
 * 尚未稳定时焦点申请被忽略；[runCatching] 吞掉焦点申请可能抛出的异常。
 */
internal fun requestVideoControlFocus(scope: CoroutineScope, requester: FocusRequester) {
  scope.launch {
    withFrameNanos {}
    withFrameNanos {}
    // 评论、推荐等区域可能在播放器按键后的这一帧才完成 AnimatedVisibility / Lazy
    // 布局。requestFocus() 失败时再给它几个布局帧，避免把一次短暂未挂载误判成
    // "页面区域不可进入"。
    repeat(3) { attempt ->
      if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@launch
      if (attempt < 2) withFrameNanos {}
    }
  }
}

/**
 * 从详情页打开推荐项。
 *
 * 先让出当前详情页的 HDR 播放控制权，再触发推荐点击回调，从而跳转到目标推荐卡片。
 */
internal fun openRecommendationFromDetail(
  leaveHdrPlaybackPage: () -> Unit,
  onRecommendationClick: (FeedItem, Rect, Rect?, Boolean) -> Unit,
  recommendation: FeedItem,
  bounds: Rect,
  returnBounds: Rect?,
  fromPlaybackEnd: Boolean,
) {
  // 卡片转场期间，上一个详情页仍组合在即将进入的推荐项之下。跳转前先让出其 HDR 控制权，
  // 避免它继续钉住屏幕亮度。
  leaveHdrPlaybackPage()
  onRecommendationClick(recommendation, bounds, returnBounds, fromPlaybackEnd)
}

/**
 * 打开评论图片预览：创建预览会话并播放从 0 到 1 的展开动画。
 *
 * 先取消旧的预览任务，再启动新协程：等待布局准备完成，若期间被取消或被替换则直接
 * 退出；准备超时仅标记 [CommentImagePreviewSession.preparationTimedOut]，仍继续展开。
 */
internal fun openVideoCommentImagePreview(
  previewState: MutableState<CommentImagePreviewSession?>,
  previewJobState: MutableState<Job?>,
  scope: CoroutineScope,
  reduceMotion: Boolean,
  image: CommentImage,
  bounds: Rect,
) {
  // 边界非法或已有预览进行中时直接返回，避免重复展开或使用无效矩形。
  if (bounds.width <= 0f || bounds.height <= 0f || previewState.value != null) return
  val session = CommentImagePreviewSession(image, bounds)
  previewState.value = session
  previewJobState.value?.cancel()
  previewJobState.value =
    scope.launch {
      session.progress.snapTo(0f)
      val preparationResult = session.preparation.await()
      if (
        preparationResult == TransitionPreparationResult.CANCELLED ||
          previewState.value !== session
      ) {
        return@launch
      }
      session.preparationTimedOut = preparationResult == TransitionPreparationResult.TIMED_OUT
      // 先置 READY 再等一帧，让系统记录起始帧后再进入 FLYING，保证动画从稳定状态起步。
      session.phase = SessionPhase.READY
      withFrameNanos {}
      session.phase = SessionPhase.FLYING
      session.progress.animateTo(
        1f,
        tween(if (reduceMotion) 110 else 380, easing = FastOutSlowInEasing),
      )
      session.phase = SessionPhase.COMPLETED
    }
}

/**
 * 关闭评论图片预览：取消准备并播放从当前进度回到 0 的收起动画，动画结束后清空会话。
 *
 * 立即把会话阶段置为 [SessionPhase.CANCELLED] 并取消准备，随后在协程中把进度动画回
 * 0；延迟一帧再判断会话仍是同一个时，才清空 [previewState]，防止期间新预览被误删。
 */
internal fun closeVideoCommentImagePreview(
  previewState: MutableState<CommentImagePreviewSession?>,
  previewJobState: MutableState<Job?>,
  scope: CoroutineScope,
  reduceMotion: Boolean,
) {
  // 没有进行中的预览时直接返回。
  val session = previewState.value ?: return
  previewJobState.value?.cancel()
  session.preparation.cancel()
  session.phase = SessionPhase.CANCELLED
  previewJobState.value =
    scope.launch {
      session.progress.animateTo(
        0f,
        tween(if (reduceMotion) 90 else 320, easing = FastOutSlowInEasing),
      )
      delay(16)
      if (previewState.value === session) previewState.value = null
    }
}

@Composable
internal fun PlaybackEndControlEffect(
  playbackEnded: Boolean,
  controlMode: Boolean,
  isPlaybackPageForeground: Boolean,
  fullscreenLayerVisible: Boolean,
  playbackEndOwnsControlState: MutableState<Boolean>,
  state: VideoControlState,
  playbackEndFocusRequester: FocusRequester,
  fullscreenPlayerFocusRequester: FocusRequester,
  embeddedPlayerFocusRequester: FocusRequester,
  scope: CoroutineScope,
) {
  LaunchedEffect(
    playbackEnded,
    controlMode,
    isPlaybackPageForeground,
    fullscreenLayerVisible,
  ) {
    if (
      playbackEnded &&
        shouldTakePlaybackEndControl(
          controlMode = controlMode,
          playbackPageForeground = isPlaybackPageForeground,
          currentMode = state.videoMode.value,
          playerSurfaceFocused = state.playerSurfaceFocused.value,
          alreadyOwned = playbackEndOwnsControlState.value,
        )
    ) {
      playbackEndOwnsControlState.value = true
      state.pendingSingleConfirmJob.value?.cancel()
      state.pendingSingleConfirmJob.value = null
      state.videoMode.value = ControlVideoMode.PLAYER_DIRECT
      state.controlsVisible.value = false
      withFrameNanos {}
      withFrameNanos {}
      runCatching { playbackEndFocusRequester.requestFocus() }
    } else if (!playbackEnded && playbackEndOwnsControlState.value) {
      playbackEndOwnsControlState.value = false
      state.videoMode.value = ControlVideoMode.PLAYER_DIRECT
      state.controlsVisible.value = false
      withFrameNanos {}
      runCatching {
        if (fullscreenLayerVisible) fullscreenPlayerFocusRequester.requestFocus()
        else embeddedPlayerFocusRequester.requestFocus()
      }
    }
  }
}

internal fun embeddedRecommendationReturnBoundsForPage(
  fullscreenForegroundBounds: Rect,
  embeddedPlayerBounds: Rect,
  fullscreenCardBounds: Rect,
): Rect? {
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

internal fun triggerAutoNextForPage(
  autoNextTriggeredState: MutableState<Boolean>,
  autoNextKey: String?,
  playbackEnded: Boolean,
  scope: CoroutineScope,
  autoNextHandoff: Animatable<Float, AnimationVector1D>,
  nextPlaybackTarget: PlaybackContinuationTarget?,
) {
  if (autoNextTriggeredState.value || autoNextKey == null || !playbackEnded) return
  autoNextTriggeredState.value = true
  scope.launch {
    autoNextHandoff.animateTo(
      1f,
      tween(260, easing = FastOutSlowInEasing),
    )
    nextPlaybackTarget?.onSelect()
  }
}

internal fun freezeEmbeddedPlayerBoundsForPage(
  fullscreenLayerVisible: Boolean,
  embeddedPlayerBounds: Rect,
  lastValidEmbeddedPlayerBounds: Rect,
  frozenEmbeddedPlayerBoundsState: MutableState<Rect>,
  trackEmbeddedPlayerBoundsState: MutableState<Boolean>,
) {
  if (fullscreenLayerVisible) return
  val candidate =
    embeddedPlayerBounds.takeIf { it.width > 0f && it.height > 0f }
      ?: lastValidEmbeddedPlayerBounds.takeIf { it.width > 0f && it.height > 0f }
      ?: return
  frozenEmbeddedPlayerBoundsState.value = candidate
  trackEmbeddedPlayerBoundsState.value = false
}

internal fun enterControlSelection(
  state: VideoControlState,
  scope: CoroutineScope,
  isPlaying: Boolean,
  onTogglePlayPause: () -> Unit,
  fullscreen: Boolean,
  pauseWhenPlaying: Boolean,
  fullscreenControlsFocusRequester: FocusRequester,
  embeddedControlsFocusRequester: FocusRequester,
) {
  state.pendingSingleConfirmJob.value?.cancel()
  state.pendingSingleConfirmJob.value = null
  if (pauseWhenPlaying && isPlaying) onTogglePlayPause()
  state.controlsVisible.value = true
  state.videoMode.value = ControlVideoMode.PLAYER_CONTROLS
  scope.launch {
    withFrameNanos {}
    withFrameNanos {}
    runCatching {
      (if (fullscreen) fullscreenControlsFocusRequester else embeddedControlsFocusRequester)
        .requestFocus()
    }
  }
}

internal fun registerEmbeddedControlConfirm(
  state: VideoControlState,
  scope: CoroutineScope,
  controlMode: Boolean,
  onTogglePlayPause: () -> Unit,
  fullscreenLayerVisible: Boolean,
  controlControlsBlocked: Boolean,
  enterFullscreenAnimated: () -> Unit,
) {
  val pending = state.pendingSingleConfirmJob.value
  if (pending?.isActive == true) {
    pending.cancel()
    state.pendingSingleConfirmJob.value = null
    onTogglePlayPause()
    return
  }
  state.pendingSingleConfirmJob.value =
    scope.launch {
      delay(CONTROL_DOUBLE_CONFIRM_TIMEOUT_MS)
      state.pendingSingleConfirmJob.value = null
      if (
        controlMode &&
          state.videoMode.value == ControlVideoMode.PLAYER_DIRECT &&
          state.playerSurfaceFocused.value &&
          !fullscreenLayerVisible &&
          !controlControlsBlocked
      ) {
        enterFullscreenAnimated()
      }
    }
}

internal fun handleControlPlayerKey(
  state: VideoControlState,
  scope: CoroutineScope,
  controlMode: Boolean,
  controlControlsBlocked: Boolean,
  isPlaying: Boolean,
  onTogglePlayPause: () -> Unit,
  fullscreenLayerVisible: Boolean,
  enterFullscreenAnimated: () -> Unit,
  durationMs: Long,
  currentPositionMs: () -> Long,
  onSeek: (Long) -> Unit,
  commentChromeState: CommentChromeState,
  headerFocusRequester: FocusRequester,
  recommendationFocusRequester: FocusRequester,
  recommendationFocusReady: Boolean,
  commentFocusRequester: FocusRequester,
  fullscreenControlsFocusRequester: FocusRequester,
  embeddedControlsFocusRequester: FocusRequester,
  fullscreen: Boolean,
  event: KeyEvent,
): Boolean {
  // 此处理器只挂在播放器自身的 focusable 节点上；onFocusChanged 在 AndroidView
  // 夺回输入时会晚一帧更新，不能再用它否决已经送到该节点的方向键。
  if (!controlMode || controlControlsBlocked) return false
  val nativeEvent = event.nativeKeyEvent
  if (event.type == KeyEventType.KeyDown) {
    when (nativeEvent.keyCode) {
      AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
      AndroidKeyEvent.KEYCODE_HEADSETHOOK -> {
        if (nativeEvent.repeatCount == 0) onTogglePlayPause()
        return true
      }
      AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> {
        if (nativeEvent.repeatCount == 0 && !isPlaying) onTogglePlayPause()
        return true
      }
      AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
      AndroidKeyEvent.KEYCODE_MEDIA_STOP -> {
        if (nativeEvent.repeatCount == 0 && isPlaying) onTogglePlayPause()
        return true
      }
    }
  }
  val action =
    resolveControlVideoSurfaceAction(
      keyCode = nativeEvent.keyCode,
      keyUp = event.type == KeyEventType.KeyUp,
      repeatCount = nativeEvent.repeatCount,
      mode = state.videoMode.value,
      fullscreen = fullscreen,
    )
  when (action) {
    ControlVideoSurfaceAction.NONE -> return false
    ControlVideoSurfaceAction.CONSUME -> Unit
    ControlVideoSurfaceAction.ENTER_DIRECT -> {
      state.videoMode.value = ControlVideoMode.PLAYER_DIRECT
      state.controlsVisible.value = false
    }
    ControlVideoSurfaceAction.ENTER_CONTROLS ->
      enterControlSelection(
        state = state,
        scope = scope,
        isPlaying = isPlaying,
        onTogglePlayPause = onTogglePlayPause,
        fullscreen = fullscreen,
        pauseWhenPlaying = false,
        fullscreenControlsFocusRequester = fullscreenControlsFocusRequester,
        embeddedControlsFocusRequester = embeddedControlsFocusRequester,
      )
    ControlVideoSurfaceAction.REGISTER_DIRECT_CONFIRM ->
      registerEmbeddedControlConfirm(
        state = state,
        scope = scope,
        controlMode = controlMode,
        onTogglePlayPause = onTogglePlayPause,
        fullscreenLayerVisible = fullscreenLayerVisible,
        controlControlsBlocked = controlControlsBlocked,
        enterFullscreenAnimated = enterFullscreenAnimated,
      )
    ControlVideoSurfaceAction.PAUSE_AND_ENTER_CONTROLS ->
      enterControlSelection(
        state = state,
        scope = scope,
        isPlaying = isPlaying,
        onTogglePlayPause = onTogglePlayPause,
        fullscreen = true,
        pauseWhenPlaying = true,
        fullscreenControlsFocusRequester = fullscreenControlsFocusRequester,
        embeddedControlsFocusRequester = embeddedControlsFocusRequester,
      )
    ControlVideoSurfaceAction.SEEK_BACKWARD,
    ControlVideoSurfaceAction.SEEK_FORWARD -> {
      if (durationMs > 0L) {
        val delta =
          if (action == ControlVideoSurfaceAction.SEEK_FORWARD) CONTROL_SEEK_STEP_MS
          else -CONTROL_SEEK_STEP_MS
        onSeek((currentPositionMs() + delta).coerceIn(0L, durationMs))
      }
    }
    ControlVideoSurfaceAction.FOCUS_HEADER ->
      requestVideoControlFocus(scope, headerFocusRequester)
    ControlVideoSurfaceAction.FOCUS_RECOMMENDATIONS ->
      if (recommendationFocusReady) {
        requestVideoControlFocus(scope, recommendationFocusRequester)
      }
    ControlVideoSurfaceAction.FOCUS_COMMENTS -> {
      commentChromeState.visibility.value = CommentChromeVisibility()
      commentChromeState.keepHiddenAtTop.value = false
      commentChromeState.floatingActionsExpanded.value = false
      requestVideoControlFocus(scope, commentFocusRequester)
    }
  }
  return true
}

@Composable
internal fun VideoControlEffects(
  controlMode: Boolean,
  controlFocusVisible: Boolean,
  controlFocusTargetReady: Boolean,
  controlFocusRestoreBlocked: Boolean,
  itemId: String,
  isPlaybackPageForeground: Boolean,
  state: VideoControlState,
  fullscreenLayerVisible: Boolean,
  embeddedPlayerFocusRequester: FocusRequester,
  fullscreenPlayerFocusRequester: FocusRequester,
  controlsHeldVisible: Boolean,
  isPlaying: Boolean,
  fullscreenControlsLocked: Boolean,
  controlsTimeoutSeconds: Int,
) {
  LaunchedEffect(
    controlMode,
    controlFocusVisible,
    controlFocusTargetReady,
    controlFocusRestoreBlocked,
    fullscreenLayerVisible,
    itemId,
    isPlaybackPageForeground,
  ) {
    if (
      shouldRestorePlaybackControlFocus(
        controlMode = controlMode,
        controlFocusVisible = controlFocusVisible,
        isPlaybackPageForeground = isPlaybackPageForeground,
        focusTargetReady = controlFocusTargetReady,
        focusRestoreBlocked = controlFocusRestoreBlocked,
      )
    ) {
      state.pendingSingleConfirmJob.value?.cancel()
      state.pendingSingleConfirmJob.value = null
      state.videoMode.value =
        if (fullscreenLayerVisible) ControlVideoMode.PLAYER_DIRECT
        else ControlVideoMode.PAGE_NAVIGATION
      state.controlsVisible.value = false
      withFrameNanos {}
      withFrameNanos {}
      val targetRequester =
        if (fullscreenLayerVisible) fullscreenPlayerFocusRequester
        else embeddedPlayerFocusRequester
      // 触屏可能让 AndroidView 在同一轮布局里清掉 Compose 焦点；控制器重新接管时允许
      // 多等几个布局帧，确保普通视频页和番剧页都能热切换回来。
      repeat(3) { attempt ->
        if (runCatching { targetRequester.requestFocus() }.getOrDefault(false)) {
          return@LaunchedEffect
        }
        if (attempt < 2) withFrameNanos {}
      }
    }
  }
  LaunchedEffect(
    state.controlsVisible.value,
    controlsHeldVisible,
    isPlaying,
    fullscreenControlsLocked,
    controlsTimeoutSeconds,
  ) {
    if (
      state.controlsVisible.value &&
        isPlaying &&
        !controlsHeldVisible &&
        (!controlMode || state.videoMode.value != ControlVideoMode.PLAYER_CONTROLS)
    ) {
      delay(controlsTimeoutSeconds * 1000L)
      state.controlsVisible.value = false
    }
  }
  LaunchedEffect(controlMode, state.videoMode.value) {
    if (controlMode && state.videoMode.value == ControlVideoMode.PLAYER_CONTROLS) {
      state.controlsVisible.value = true
    }
  }
}

@Composable
internal fun VideoControlBackHandler(
  enabled: Boolean,
  state: VideoControlState,
  scope: CoroutineScope,
  embeddedPlayerFocusRequester: FocusRequester,
) {
  BackHandler(enabled = enabled) {
    state.pendingSingleConfirmJob.value?.cancel()
    state.pendingSingleConfirmJob.value = null
    state.controlsVisible.value = false
    state.videoMode.value =
      if (state.videoMode.value == ControlVideoMode.PLAYER_CONTROLS) {
        ControlVideoMode.PLAYER_DIRECT
      } else {
        ControlVideoMode.PAGE_NAVIGATION
      }
    scope.launch {
      withFrameNanos {}
      withFrameNanos {}
      runCatching { embeddedPlayerFocusRequester.requestFocus() }
    }
  }
}

@Composable
internal fun controlPlayerSurfaceModifier(
  state: VideoControlState,
  scope: CoroutineScope,
  controlMode: Boolean,
  controlControlsBlocked: Boolean,
  isPlaying: Boolean,
  onTogglePlayPause: () -> Unit,
  fullscreenLayerVisible: Boolean,
  enterFullscreenAnimated: () -> Unit,
  durationMs: Long,
  currentPositionMs: () -> Long,
  onSeek: (Long) -> Unit,
  commentChromeState: CommentChromeState,
  headerFocusRequester: FocusRequester,
  recommendationFocusRequester: FocusRequester,
  recommendationFocusReady: Boolean,
  commentFocusRequester: FocusRequester,
  fullscreenControlsFocusRequester: FocusRequester,
  embeddedControlsFocusRequester: FocusRequester,
  focusRequester: FocusRequester,
  fullscreen: Boolean,
): Modifier {
  if (!controlMode) return Modifier
  val focusModifier =
    Modifier.focusRequester(focusRequester)
      .onFocusChanged {
        state.playerSurfaceFocused.value = it.isFocused
        if (!it.isFocused) {
          state.pendingSingleConfirmJob.value?.cancel()
          state.pendingSingleConfirmJob.value = null
        }
      }
      .onPreviewKeyEvent {
        handleControlPlayerKey(
          state = state,
          scope = scope,
          controlMode = controlMode,
          controlControlsBlocked = controlControlsBlocked,
          isPlaying = isPlaying,
          onTogglePlayPause = onTogglePlayPause,
          fullscreenLayerVisible = fullscreenLayerVisible,
          enterFullscreenAnimated = enterFullscreenAnimated,
          durationMs = durationMs,
          currentPositionMs = currentPositionMs,
          onSeek = onSeek,
          commentChromeState = commentChromeState,
          headerFocusRequester = headerFocusRequester,
          recommendationFocusRequester = recommendationFocusRequester,
          recommendationFocusReady = recommendationFocusReady,
          commentFocusRequester = commentFocusRequester,
          fullscreenControlsFocusRequester = fullscreenControlsFocusRequester,
          embeddedControlsFocusRequester = embeddedControlsFocusRequester,
          fullscreen = fullscreen,
          event = it,
        )
      }
  return if (fullscreen) {
    focusModifier.focusable()
  } else {
    focusModifier
      .controlFocusOutline(
        shape = VideoShapeTokens.Player,
        color = MaterialTheme.colorScheme.primary,
        width = 3.dp,
        enabled = state.videoMode.value == ControlVideoMode.PAGE_NAVIGATION,
      )
      .focusable()
  }
}

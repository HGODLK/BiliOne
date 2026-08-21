package dev.openbili.webdemo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.live.LiveAreaFilter
import dev.openbili.webdemo.live.LiveAreaIndexScreen
import dev.openbili.webdemo.live.LiveHomeUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 直播首页“所有分区”入口的搜索式展开状态。 */
internal class LiveAreaIndexTransitionState {
  var show by mutableStateOf(false)
  var direction by mutableStateOf<SearchTransitionDirection?>(null)
  var sourceBounds by mutableStateOf(Rect.Zero)
  val progress = Animatable(0f)
  val maskAlpha = Animatable(1f)
  val scrimAlpha = Animatable(0f)
  var job: Job? = null
}

internal suspend fun animateLiveAreaIndexEnter(
  state: LiveAreaIndexTransitionState,
  reduceMotion: Boolean,
) {
  state.maskAlpha.snapTo(1f)
  state.scrimAlpha.snapTo(0f)
  state.progress.snapTo(0f)
  // 源矩形已在上面冻结；等待两个已提交的目标帧再移动它。
  withFrameNanos {}
  withFrameNanos {}
  coroutineScope {
    launch {
      state.progress.animateTo(
        1f,
        tween(if (reduceMotion) 120 else 380, easing = FastOutSlowInEasing),
      )
    }
    launch {
      state.scrimAlpha.animateTo(
        1f,
        tween(if (reduceMotion) 80 else 220, easing = FastOutSlowInEasing),
      )
    }
  }
  if (state.direction != SearchTransitionDirection.ENTER) return
  state.show = true
  withFrameNanos {}
  state.direction = null
  state.maskAlpha.snapTo(0f)
}

internal suspend fun animateLiveAreaIndexExit(
  state: LiveAreaIndexTransitionState,
  reduceMotion: Boolean,
) {
  val reversingEnter = state.direction == SearchTransitionDirection.ENTER
  if (!reversingEnter) {
    state.maskAlpha.snapTo(0f)
    state.scrimAlpha.snapTo(0f)
    state.progress.snapTo(1f)
    coroutineScope {
      launch {
        state.maskAlpha.animateTo(
          1f,
          tween(if (reduceMotion) 70 else 120),
        )
      }
      launch {
        state.scrimAlpha.animateTo(
          1f,
          tween(if (reduceMotion) 70 else 120),
        )
      }
    }
    state.show = false
    withFrameNanos {}
  } else {
    state.maskAlpha.snapTo(1f)
    state.show = false
  }
  coroutineScope {
    launch {
      state.progress.animateTo(
        0f,
        tween(if (reduceMotion) 100 else 320, easing = FastOutSlowInEasing),
      )
    }
    launch {
      state.scrimAlpha.animateTo(
        0f,
        tween(if (reduceMotion) 70 else 180, easing = FastOutSlowInEasing),
      )
    }
  }
  state.maskAlpha.animateTo(0f, tween(if (reduceMotion) 60 else 100))
  if (state.direction == SearchTransitionDirection.EXIT) {
    state.direction = null
  }
}

internal fun openLiveAreaIndex(
  scope: CoroutineScope,
  state: LiveAreaIndexTransitionState,
  sourceBounds: Rect,
  reduceMotion: Boolean,
  blocked: Boolean,
) {
  if (blocked || state.show || state.direction != null) return
  state.job?.cancel()
  state.sourceBounds = sourceBounds.takeIf { it.hasUsableSize() } ?: Rect.Zero
  state.direction = SearchTransitionDirection.ENTER
  state.job = scope.launch { animateLiveAreaIndexEnter(state, reduceMotion) }
}

internal fun closeLiveAreaIndex(
  scope: CoroutineScope,
  state: LiveAreaIndexTransitionState,
  reduceMotion: Boolean,
  blocked: Boolean,
  onFinished: () -> Unit = {},
) {
  if (blocked || state.direction == SearchTransitionDirection.EXIT) return
  state.job?.cancel()
  state.direction = SearchTransitionDirection.EXIT
  state.job =
    scope.launch {
      animateLiveAreaIndexExit(state, reduceMotion)
      onFinished()
    }
}

@Composable
internal fun LiveAreaIndexOverlay(
  state: LiveHomeUiState,
  onBack: () -> Unit,
  onAreaSelected: (LiveAreaFilter) -> Unit,
) {
  val controlMode = LocalControlMode.current
  val initialFocusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    if (controlMode) {
      withFrameNanos {}
      withFrameNanos {}
      runCatching { initialFocusRequester.requestFocus() }
    }
  }
  val liveContent = state as? LiveHomeUiState.Content
  Box(
    Modifier.fillMaxSize()
      .then(
        if (controlMode) {
          Modifier.focusProperties {
              onExit = {
                if (
                  requestedFocusDirection == FocusDirection.Left ||
                    requestedFocusDirection == FocusDirection.Right
                ) {
                  cancelFocusChange()
                }
              }
            }
            .focusGroup()
        } else Modifier
      )
      .background(MaterialTheme.colorScheme.background)
  ) {
    Surface(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      shape = VideoShapeTokens.Card,
      color = MaterialTheme.colorScheme.background,
      tonalElevation = 2.dp,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      LiveAreaIndexScreen(
        groups = liveContent?.areaGroups.orEmpty(),
        selectedArea = liveContent?.selectedArea ?: LiveAreaFilter(0, name = "推荐"),
        onBack = onBack,
        onAreaSelected = onAreaSelected,
        onHorizontalRailInteractionChanged = {},
        initialFocusRequester = initialFocusRequester.takeIf { controlMode },
      )
    }
  }
}

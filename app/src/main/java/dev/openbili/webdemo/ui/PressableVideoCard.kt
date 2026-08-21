package dev.openbili.webdemo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PressableVideoCard(
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = VideoShapeTokens.Card,
  focusRequester: FocusRequester? = null,
  onControlKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
  content: @Composable () -> Unit,
) {
  val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
  val scope = rememberCoroutineScope()
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val focused by interactionSource.collectIsFocusedAsState()
  val controlMode = LocalControlMode.current
  val controlFocusVisible = LocalControlFocusVisible.current
  var controlLongPressJob by remember { mutableStateOf<Job?>(null) }
  var controlLongPressTriggered by remember { mutableStateOf(false) }
  fun activate() {
    scope.launch {
      bringIntoViewRequester.bringIntoView()
      withFrameNanos {}
      onClick()
    }
  }
  LaunchedEffect(focused) {
    if (focused) {
      bringIntoViewRequester.bringIntoView()
    } else {
      controlLongPressJob?.cancel()
      controlLongPressJob = null
      controlLongPressTriggered = false
    }
  }
  DisposableEffect(Unit) {
    onDispose { controlLongPressJob?.cancel() }
  }
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) .98f else if (focused && controlFocusVisible) 1.025f else 1f,
      animationSpec = spring(dampingRatio = .82f, stiffness = 700f),
      label = "videoCardPress",
    )
  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .then(
          if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
        )
        .navigationBringIntoViewTarget(bringIntoViewRequester)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .clip(shape)
        .then(
          if (controlMode) {
            Modifier.onPreviewKeyEvent { event ->
              if (onControlKeyEvent?.invoke(event) == true) {
                return@onPreviewKeyEvent true
              }
              val nativeEvent = event.nativeKeyEvent
              if (!enabled || !isControlConfirmKey(nativeEvent.keyCode)) {
                return@onPreviewKeyEvent false
              }
              when (event.type) {
                KeyEventType.KeyDown -> {
                  if (nativeEvent.repeatCount == 0 && controlLongPressJob == null) {
                    controlLongPressTriggered = false
                    controlLongPressJob =
                      scope.launch {
                        delay(CONTROL_LONG_PRESS_TIMEOUT_MS)
                        controlLongPressTriggered = true
                        onLongClick()
                      }
                  }
                  true
                }
                KeyEventType.KeyUp -> {
                  controlLongPressJob?.cancel()
                  controlLongPressJob = null
                  if (!controlLongPressTriggered) activate()
                  controlLongPressTriggered = false
                  true
                }
                else -> false
              }
            }
          } else Modifier
        )
        .combinedClickable(
          enabled = enabled,
          interactionSource = interactionSource,
          indication = LocalIndication.current,
          onClick = ::activate,
          onLongClick = onLongClick,
        ),
    shape = shape,
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    shadowElevation = 0.dp,
    border =
      BorderStroke(
        if (focused && controlFocusVisible) 3.dp else 1.dp,
        if (focused && controlFocusVisible) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
      ),
    content = content,
  )
}

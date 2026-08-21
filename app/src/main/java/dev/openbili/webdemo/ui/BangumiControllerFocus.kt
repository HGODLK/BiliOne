package dev.openbili.webdemo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/**
 * 番剧控制器节点的共同焦点契约。
 *
 * 具体页面只负责提供自己的 FocusRequester 和确认动作；层级闸门、真实焦点描边以及
 * “确认键只由当前节点消费”集中在这里，避免把这些状态重新堆回根页面。
 */
@Composable
internal fun Modifier.bangumiControllerFocus(
  focusRequester: FocusRequester? = null,
  enabled: Boolean,
  shape: Shape,
  onFocused: () -> Unit = {},
  onKeyEvent: (KeyEvent) -> Boolean = { false },
  onConfirm: (() -> Unit)? = null,
): Modifier {
  val controlMode = LocalControlMode.current
  if (!controlMode) return this
  val focusEnabled = enabled
  return this
    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    .focusProperties { canFocus = focusEnabled }
    .onFocusChanged { if (it.isFocused) onFocused() }
    .controlFocusOutline(
      shape = shape,
      color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
      width = 3.dp,
      enabled = focusEnabled,
    )
    .onPreviewKeyEvent { event ->
      if (focusEnabled && onKeyEvent(event)) return@onPreviewKeyEvent true
      if (!focusEnabled || onConfirm == null || !isControlConfirmKey(event.nativeKeyEvent.keyCode)) {
        return@onPreviewKeyEvent false
      }
      if (event.type == KeyEventType.KeyUp) onConfirm()
      true
    }
}

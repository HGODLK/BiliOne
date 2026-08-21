package dev.openbili.webdemo.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/** 等待懒加载目标完成挂载，并在每个布局帧重新申请焦点。 */
internal suspend fun FocusRequester.requestFocusWithinFrames(maxFrames: Int): Boolean {
  repeat(maxFrames.coerceAtLeast(1)) {
    if (runCatching { requestFocus() }.getOrDefault(false)) return true
    withFrameNanos {}
  }
  return false
}

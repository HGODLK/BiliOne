package dev.openbili.webdemo.video

import android.view.KeyEvent
import dev.openbili.webdemo.ui.CONTROL_SEEK_STEP_MS
import dev.openbili.webdemo.ui.isControlConfirmKey

/** 控制器专用播放页的显示层级。 */
internal enum class ControllerPlaybackOverlay {
  HIDDEN,
  INFO,
  ACTIONS,
  PANEL,
}

/** 侧栏当前展示的低频能力。 */
internal enum class ControllerPlaybackPanel {
  NONE,
  MORE,
  SELECTION,
  QUALITY,
  SUBTITLE,
  DANMAKU,
  CHAT,
}

/** 播放页操作行的纯数据项，页面壳不需要知道各个业务按钮的实现。 */
internal data class ControllerPlaybackActionItem(
  val key: String,
  val label: String,
  val enabled: Boolean = true,
)

/** 专用播放页按键状态机动作。 */
internal enum class ControllerPlaybackKeyAction {
  NONE,
  CONSUME,
  TOGGLE_PLAY,
  SHOW_INFO,
  SHOW_ACTIONS,
  OPEN_MORE,
  SEEK_BACKWARD,
  SEEK_FORWARD,
  HIDE_OVERLAY,
}

/**
 * 解析专用播放页的背景按键。
 *
 * 操作行与侧栏中的按钮由 Compose 默认焦点系统接管，这里只处理播放表面本身的按键，
 * 避免背景层在按钮获得焦点后再次抢走方向键。
 */
internal fun resolveControllerPlaybackKeyAction(
  keyCode: Int,
  keyUp: Boolean,
  repeatCount: Int,
  overlay: ControllerPlaybackOverlay,
  isLive: Boolean = false,
): ControllerPlaybackKeyAction {
  if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
    return if (!keyUp && repeatCount == 0) ControllerPlaybackKeyAction.TOGGLE_PLAY
    else ControllerPlaybackKeyAction.CONSUME
  }
  if (isControlConfirmKey(keyCode)) {
    if (!keyUp) return ControllerPlaybackKeyAction.CONSUME
    return when (overlay) {
      ControllerPlaybackOverlay.HIDDEN,
      ControllerPlaybackOverlay.INFO -> ControllerPlaybackKeyAction.TOGGLE_PLAY
      ControllerPlaybackOverlay.ACTIONS,
      ControllerPlaybackOverlay.PANEL -> ControllerPlaybackKeyAction.CONSUME
    }
  }
  if (keyCode == KeyEvent.KEYCODE_MENU) {
    return if (keyUp) ControllerPlaybackKeyAction.OPEN_MORE
    else ControllerPlaybackKeyAction.CONSUME
  }
  if (keyUp) return ControllerPlaybackKeyAction.NONE
  return when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT ->
      if (overlay == ControllerPlaybackOverlay.HIDDEN || overlay == ControllerPlaybackOverlay.INFO) {
        if (isLive) ControllerPlaybackKeyAction.CONSUME
        else ControllerPlaybackKeyAction.SEEK_BACKWARD
      } else ControllerPlaybackKeyAction.CONSUME
    KeyEvent.KEYCODE_DPAD_RIGHT ->
      if (overlay == ControllerPlaybackOverlay.HIDDEN || overlay == ControllerPlaybackOverlay.INFO) {
        if (isLive) ControllerPlaybackKeyAction.CONSUME
        else ControllerPlaybackKeyAction.SEEK_FORWARD
      } else ControllerPlaybackKeyAction.CONSUME
    KeyEvent.KEYCODE_DPAD_UP ->
      if (repeatCount > 0) {
        ControllerPlaybackKeyAction.CONSUME
      } else when (overlay) {
        ControllerPlaybackOverlay.HIDDEN -> ControllerPlaybackKeyAction.SHOW_INFO
        ControllerPlaybackOverlay.INFO -> ControllerPlaybackKeyAction.SHOW_ACTIONS
        ControllerPlaybackOverlay.ACTIONS -> ControllerPlaybackKeyAction.SHOW_INFO
        ControllerPlaybackOverlay.PANEL -> ControllerPlaybackKeyAction.CONSUME
      }
    KeyEvent.KEYCODE_DPAD_DOWN ->
      if (repeatCount > 0) {
        ControllerPlaybackKeyAction.CONSUME
      } else if (overlay == ControllerPlaybackOverlay.HIDDEN || overlay == ControllerPlaybackOverlay.INFO) {
        ControllerPlaybackKeyAction.SHOW_ACTIONS
      } else if (overlay == ControllerPlaybackOverlay.ACTIONS) {
        ControllerPlaybackKeyAction.SHOW_INFO
      } else ControllerPlaybackKeyAction.CONSUME
    else -> ControllerPlaybackKeyAction.NONE
  }
}

/** 长按左右键时逐步加大 seek 步长，保留短按 5 秒的行为。 */
internal fun controllerPlaybackSeekDeltaMs(forward: Boolean, repeatCount: Int): Long {
  val stepMs =
    when {
      repeatCount >= 12 -> CONTROL_SEEK_STEP_MS * 12
      repeatCount >= 8 -> CONTROL_SEEK_STEP_MS * 6
      repeatCount >= 4 -> CONTROL_SEEK_STEP_MS * 2
      else -> CONTROL_SEEK_STEP_MS
    }
  return if (forward) stepMs else -stepMs
}

/** 返回键在专用播放页上的层级回退规则。 */
internal fun resolveControllerPlaybackBackAction(
  overlay: ControllerPlaybackOverlay,
  panel: ControllerPlaybackPanel,
): ControllerPlaybackKeyAction =
  when {
    overlay == ControllerPlaybackOverlay.PANEL && panel != ControllerPlaybackPanel.NONE ->
      ControllerPlaybackKeyAction.HIDE_OVERLAY
    overlay != ControllerPlaybackOverlay.HIDDEN -> ControllerPlaybackKeyAction.HIDE_OVERLAY
    else -> ControllerPlaybackKeyAction.NONE
  }

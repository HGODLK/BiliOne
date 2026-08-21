package dev.openbili.webdemo.ui

/**
 * 控制器（遥控器 / 手柄 / TV 遥控器）输入策略与按键映射。
 *
 * 本文件定义判断设备是否处于"控制器导航"交互方式的 CompositionLocal（[LocalControlMode]、
 * [LocalControlFocusVisible]），以及一组纯函数与枚举，把方向键、确认键、媒体键解析为
 * 各 UI 层（首页卡片、视频播放器、确认跟踪）可消费的动作，供 Compose 按键处理器与
 * 单元测试共用。这些函数不持有任何状态，只做确定性的键码到动作的映射。
 */

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.staticCompositionLocalOf

/** 方向键 / 确认键导航是否作为当前主要交互策略。 */
val LocalControlMode = staticCompositionLocalOf { false }

/** 控制器模式锁定后，控制器焦点高亮（外框）是否应当绘制。 */
val LocalControlFocusVisible = staticCompositionLocalOf { true }

/**
 * 当前一笔交互是否由控制器导航接管。
 *
 * 输入方式由本次启动的首次有效输入锁定。保留焦点高亮参数供各页面共用同一套纯判断，
 * 正常控制器模式下两项会同时为 true。
 */
internal fun controllerInteractionActive(
  controlMode: Boolean,
  controlFocusVisible: Boolean,
): Boolean = controlMode && controlFocusVisible

/** 控制器在触摸后重新接管前台播放页时，是否应当重领播放器焦点。 */
internal fun shouldRestorePlaybackControlFocus(
  controlMode: Boolean,
  controlFocusVisible: Boolean,
  isPlaybackPageForeground: Boolean,
  focusTargetReady: Boolean = true,
  focusRestoreBlocked: Boolean = false,
): Boolean =
  controlMode &&
    controlFocusVisible &&
    isPlaybackPageForeground &&
    focusTargetReady &&
    !focusRestoreBlocked

/**
 * 判断给定的输入源位掩码是否属于"控制器导航"类设备。
 *
 * 虚拟设备（如软键盘、输入法注入）不视为控制器导航；只认方向键（D-pad）、手柄、
 * 摇杆三类物理输入源。
 */
internal fun isControlNavigationInput(sources: Int, isVirtual: Boolean): Boolean {
  if (isVirtual) return false
  return listOf(
      InputDevice.SOURCE_DPAD,
      InputDevice.SOURCE_GAMEPAD,
      InputDevice.SOURCE_JOYSTICK,
    )
    // 按位与后仍等于该 source，说明 sources 中完整包含了这一来源位。
    .any { source -> sources and source == source }
}

/** [InputDevice] 便捷扩展：判断该输入设备是否为控制器导航类设备。 */
internal fun InputDevice.isControlNavigationInput(): Boolean =
  isControlNavigationInput(sources = sources, isVirtual = isVirtual)

/** 判断键码是否为方向键 / 确认键 / 手柄按键等"控制器交互"按键。 */
internal fun isControlInteractionKey(keyCode: Int): Boolean =
  keyCode == KeyEvent.KEYCODE_DPAD_UP ||
    keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
    keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
    keyCode == KeyEvent.KEYCODE_ENTER ||
    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
    keyCode == KeyEvent.KEYCODE_BUTTON_A ||
    keyCode == KeyEvent.KEYCODE_BUTTON_B ||
    keyCode == KeyEvent.KEYCODE_BUTTON_X ||
    keyCode == KeyEvent.KEYCODE_BUTTON_Y

// 摇杆轴向触发阈值：绝对值超过 0.5 才视为一次方向按压，避免轻微抖动误触发。
internal const val CONTROL_DPAD_AXIS_THRESHOLD = 0.5f

/**
 * 将手柄摇杆的某一轴数值映射为 TV 遥控器方向键对应的键码。
 *
 * 轴值越过负向 / 正向阈值时分别返回负向 / 正向键码，否则返回 null 表示未越过死区。
 */
internal fun controlDpadKeyCodeForAxis(
  axisValue: Float,
  negativeKeyCode: Int,
  positiveKeyCode: Int,
): Int? =
  when {
    axisValue <= -CONTROL_DPAD_AXIS_THRESHOLD -> negativeKeyCode
    axisValue >= CONTROL_DPAD_AXIS_THRESHOLD -> positiveKeyCode
    else -> null
  }

internal const val CONTROL_SEEK_STEP_MS = 5_000L
internal const val CONTROL_LONG_PRESS_TIMEOUT_MS = 500L
internal const val CONTROL_DOUBLE_CONFIRM_TIMEOUT_MS = 260L

/**
 * 视频表面（内嵌播放器 / 全屏播放器）所处的控制模式。
 *
 * PAGE_NAVIGATION：焦点在页面导航层；PLAYER_DIRECT：焦点直达播放器表面；
 * PLAYER_CONTROLS：焦点在播放器控制条上。
 */
internal enum class ControlVideoMode {
  PAGE_NAVIGATION,
  PLAYER_DIRECT,
  PLAYER_CONTROLS,
}

/** 视频表面在某个按键事件下应执行的动作。 */
internal enum class ControlVideoSurfaceAction {
  NONE,
  CONSUME,
  ENTER_DIRECT,
  ENTER_CONTROLS,
  REGISTER_DIRECT_CONFIRM,
  PAUSE_AND_ENTER_CONTROLS,
  SEEK_BACKWARD,
  SEEK_FORWARD,
  FOCUS_HEADER,
  FOCUS_RECOMMENDATIONS,
  FOCUS_COMMENTS,
}

/** 针对获得焦点的内嵌 / 全屏播放器表面的纯按键策略。 */
internal fun resolveControlVideoSurfaceAction(
  keyCode: Int,
  keyUp: Boolean,
  repeatCount: Int,
  mode: ControlVideoMode,
  fullscreen: Boolean,
): ControlVideoSurfaceAction {
  // 确认键：按下时吞掉事件，待抬起时再派发对应动作，避免长按重复触发。
  if (isControlConfirmKey(keyCode)) {
    if (!keyUp) return ControlVideoSurfaceAction.CONSUME
    return when (mode) {
      ControlVideoMode.PAGE_NAVIGATION -> ControlVideoSurfaceAction.ENTER_DIRECT
      ControlVideoMode.PLAYER_DIRECT ->
        if (fullscreen) ControlVideoSurfaceAction.PAUSE_AND_ENTER_CONTROLS
        else ControlVideoSurfaceAction.REGISTER_DIRECT_CONFIRM
      ControlVideoMode.PLAYER_CONTROLS -> ControlVideoSurfaceAction.CONSUME
    }
  }
  // 方向键在抬起事件以及页面导航模式下的重复事件中不生效。
  if (keyUp || repeatCount > 0 && mode == ControlVideoMode.PAGE_NAVIGATION) {
    return ControlVideoSurfaceAction.NONE
  }
  return when (mode) {
    ControlVideoMode.PAGE_NAVIGATION ->
      when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> ControlVideoSurfaceAction.FOCUS_HEADER
        KeyEvent.KEYCODE_DPAD_DOWN -> ControlVideoSurfaceAction.FOCUS_RECOMMENDATIONS
        KeyEvent.KEYCODE_DPAD_RIGHT -> ControlVideoSurfaceAction.FOCUS_COMMENTS
        KeyEvent.KEYCODE_DPAD_LEFT -> ControlVideoSurfaceAction.CONSUME
        else -> ControlVideoSurfaceAction.NONE
      }
    ControlVideoMode.PLAYER_DIRECT ->
      when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> ControlVideoSurfaceAction.SEEK_BACKWARD
        KeyEvent.KEYCODE_DPAD_RIGHT -> ControlVideoSurfaceAction.SEEK_FORWARD
        KeyEvent.KEYCODE_DPAD_DOWN -> ControlVideoSurfaceAction.ENTER_CONTROLS
        KeyEvent.KEYCODE_DPAD_UP -> ControlVideoSurfaceAction.CONSUME
        else -> ControlVideoSurfaceAction.NONE
      }
    ControlVideoMode.PLAYER_CONTROLS -> ControlVideoSurfaceAction.NONE
  }
}

/** 全屏播放器背景在获得焦点时可执行的动作。 */
internal enum class ControlPlayerAction {
  NONE,
  SHOW_CONTROLS,
  TOGGLE_PLAY_PAUSE,
  PLAY,
  PAUSE,
  SEEK_BACKWARD,
  SEEK_FORWARD,
}

/**
 * 仅在全屏播放器背景拥有焦点时解析按键。
 *
 * 已获焦点的按钮与菜单保留 Compose 默认方向键行为，因此该策略绝不会从控件上抢走
 * 方向键。
 */
internal fun resolveControlPlayerAction(
  keyCode: Int,
  playerSurfaceFocused: Boolean,
  controlsVisible: Boolean,
  controlsBlocked: Boolean,
): ControlPlayerAction {
  when (keyCode) {
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    KeyEvent.KEYCODE_HEADSETHOOK -> return ControlPlayerAction.TOGGLE_PLAY_PAUSE
    KeyEvent.KEYCODE_MEDIA_PLAY -> return ControlPlayerAction.PLAY
    KeyEvent.KEYCODE_MEDIA_PAUSE,
    KeyEvent.KEYCODE_MEDIA_STOP -> return ControlPlayerAction.PAUSE
  }
  if (!playerSurfaceFocused || controlsBlocked) return ControlPlayerAction.NONE
  return when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT -> ControlPlayerAction.SEEK_BACKWARD
    KeyEvent.KEYCODE_DPAD_RIGHT -> ControlPlayerAction.SEEK_FORWARD
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN ->
      if (controlsVisible) ControlPlayerAction.NONE else ControlPlayerAction.SHOW_CONTROLS
    KeyEvent.KEYCODE_MENU -> ControlPlayerAction.SHOW_CONTROLS
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_SPACE ->
      if (controlsVisible) ControlPlayerAction.TOGGLE_PLAY_PAUSE
      else ControlPlayerAction.SHOW_CONTROLS
    else -> ControlPlayerAction.NONE
  }
}

internal fun isControlConfirmKey(keyCode: Int): Boolean =
  keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
    keyCode == KeyEvent.KEYCODE_ENTER ||
    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
    keyCode == KeyEvent.KEYCODE_BUTTON_A ||
    keyCode == KeyEvent.KEYCODE_SPACE

internal enum class ControlConfirmAction {
  NONE,
  START_TRACKING,
  CLICK,
  LONG_CLICK,
  CANCEL,
}

/** Compose 按键处理器使用并有单元测试覆盖的纯状态转移。 */
internal fun resolveControlConfirmAction(
  keyCode: Int,
  isKeyDown: Boolean,
  repeatCount: Int,
  tracking: Boolean,
  longPressTriggered: Boolean,
): ControlConfirmAction {
  if (!isControlConfirmKey(keyCode)) return ControlConfirmAction.NONE
  if (isKeyDown) {
    return if (!tracking && repeatCount == 0) ControlConfirmAction.START_TRACKING
    else ControlConfirmAction.NONE
  }
  if (!tracking) return ControlConfirmAction.CANCEL
  return if (longPressTriggered) ControlConfirmAction.CANCEL else ControlConfirmAction.CLICK
}

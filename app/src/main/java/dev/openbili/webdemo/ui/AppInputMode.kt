package dev.openbili.webdemo.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlin.math.abs

/** 一次应用启动期间唯一生效的输入方式。 */
internal enum class AppInputMode {
  UNDECIDED,
  TOUCH,
  CONTROLLER,
}

/** 可用于锁定输入方式的首次有效输入。 */
internal enum class AppInputKind {
  TOUCH,
  CONTROLLER,
}

/** 只有尚未选择输入方式时，首次有效输入才能锁定本次启动。 */
internal fun resolveAppInputMode(
  current: AppInputMode,
  input: AppInputKind,
): AppInputMode =
  when (current) {
    AppInputMode.UNDECIDED ->
      when (input) {
        AppInputKind.TOUCH -> AppInputMode.TOUCH
        AppInputKind.CONTROLLER -> AppInputMode.CONTROLLER
      }
    AppInputMode.TOUCH,
    AppInputMode.CONTROLLER -> current
  }

/** 忽略摇杆中心附近的抖动，只把越过方向阈值的位移视为首次控制器输入。 */
internal fun isMeaningfulControllerMotion(vararg axisValues: Float): Boolean =
  axisValues.any { value -> abs(value) >= CONTROL_DPAD_AXIS_THRESHOLD }

/** 控制器模式下拦截首次触屏后显示的全局提示。 */
@Composable
internal fun ControllerModeTouchDialog(
  onContinueWithController: () -> Unit,
  onRestartWithTouch: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = {},
    title = { Text("当前处于控制器模式") },
    text = { Text("本次启动已由首次控制器输入锁定。若要使用触屏，请重启软件。") },
    confirmButton = {
      TextButton(onClick = onRestartWithTouch) { Text("重启并使用触屏") }
    },
    dismissButton = {
      TextButton(onClick = onContinueWithController) { Text("继续使用控制器") }
    },
  )
}

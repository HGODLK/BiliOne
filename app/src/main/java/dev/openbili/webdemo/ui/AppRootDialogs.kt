package dev.openbili.webdemo.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

@Composable
internal fun ControlExitDialog(onDismiss: () -> Unit, onExit: () -> Unit) {
  val cancelFocusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    withFrameNanos {}
    runCatching { cancelFocusRequester.requestFocus() }
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("退出应用？") },
    text = { Text("当前播放和页面状态会停止。") },
    confirmButton = {
      TextButton(
        onClick = onExit,
        modifier =
          Modifier.controlFocusOutline(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary,
          ),
      ) {
        Text("退出")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier =
          Modifier.focusRequester(cancelFocusRequester)
            .controlFocusOutline(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.primary,
            ),
      ) {
        Text("取消")
      }
    },
  )
}

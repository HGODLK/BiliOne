package dev.openbili.webdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.LoginState
import dev.openbili.webdemo.api.QrCodeInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSheet(
  loginState: LoginState,
  onDismiss: () -> Unit,
  onRetry: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val controlMode = LocalControlMode.current
  val closeFocusRequester = remember { FocusRequester() }

  LaunchedEffect(controlMode, loginState::class) {
    if (controlMode) {
      androidx.compose.runtime.withFrameNanos {}
      runCatching { closeFocusRequester.requestFocus() }
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        if (
          loginState is LoginState.AppQrReady ||
            loginState is LoginState.AppWaiting ||
            loginState is LoginState.AppAuthorized ||
            loginState is LoginState.AppFailed
        ) {
          "授权主页 IP 属地"
        } else {
          "登录哔哩哔哩"
        },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      when (val state = loginState) {
        is LoginState.Idle -> {
          Text("正在生成二维码...", style = MaterialTheme.typography.bodyLarge)
        }
        is LoginState.QrReady -> {
          QrImage(qrInfo = state.qrInfo)
          Text(
            "请使用哔哩哔哩 App 扫码",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        is LoginState.Waiting -> {
          QrImage(qrInfo = state.qrInfo)
          Text(
            state.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
          )
          HorizontalDivider()
          ControlSheetButton(onClick = onRetry) { Text("重新获取二维码") }
        }
        is LoginState.AppQrReady -> {
          QrImage(qrInfo = state.qrInfo)
          Text(
            "请使用哔哩哔哩 App 扫码并确认授权",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Text(
            "仅用于读取主页公开的 IP 属地，不会替换当前登录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
        is LoginState.AppWaiting -> {
          QrImage(qrInfo = state.qrInfo)
          Text(
            state.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
          )
          HorizontalDivider()
          ControlSheetButton(onClick = onRetry) { Text("重新获取二维码") }
        }
        is LoginState.Success -> {
          Text(
            "✅ 登录成功，欢迎 ${state.user.name}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        LoginState.AppAuthorized -> {
          Text(
            "✅ 授权成功，正在刷新个人空间",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        is LoginState.Failed -> {
          Text(
            "登录失败",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
          )
          Text(
            state.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
          )
          ControlSheetButton(onClick = onRetry) { Text("重试") }
        }
        is LoginState.AppFailed -> {
          Text(
            "授权失败",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
          )
          Text(
            state.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
          )
          ControlSheetButton(onClick = onRetry) { Text("重试") }
        }
      }
      TextButton(
        onClick = onDismiss,
        modifier =
          Modifier.focusRequester(closeFocusRequester)
            .controlFocusOutline(
              shape = RoundedCornerShape(20.dp),
              color = MaterialTheme.colorScheme.primary,
            ),
      ) {
        Text("关闭")
      }
    }
  }
}

@Composable
private fun ControlSheetButton(
  onClick: () -> Unit,
  content: @Composable RowScope.() -> Unit,
) {
  TextButton(
    onClick = onClick,
    modifier =
      Modifier.controlFocusOutline(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
      ),
    content = content,
  )
}

@Composable
private fun QrImage(qrInfo: QrCodeInfo) {
  if (qrInfo.url.isNotBlank()) {
    val bitmap =
      remember(qrInfo.url) {
        try {
          val writer = com.google.zxing.qrcode.QRCodeWriter()
          val matrix = writer.encode(qrInfo.url, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400)
          val bm =
            android.graphics.Bitmap.createBitmap(400, 400, android.graphics.Bitmap.Config.RGB_565)
          for (x in 0 until 400) {
            for (y in 0 until 400) {
              bm.setPixel(
                x,
                y,
                if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
              )
            }
          }
          bm
        } catch (_: Exception) {
          null
        }
      }
    if (bitmap != null) {
      androidx.compose.foundation.Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "登录二维码",
        modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)),
      )
    } else {
      PlaceholderQr()
    }
  } else {
    PlaceholderQr()
  }
}

@Composable
private fun PlaceholderQr() {
  Box(
    modifier =
      Modifier.size(200.dp)
        .background(
          MaterialTheme.colorScheme.surfaceVariant,
          RoundedCornerShape(12.dp),
        ),
    contentAlignment = Alignment.Center,
  ) {
    Text("二维码", color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

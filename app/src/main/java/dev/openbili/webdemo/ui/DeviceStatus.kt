package dev.openbili.webdemo.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private enum class NetworkKind {
  WIFI,
  CELLULAR,
  OFFLINE,
}

@Composable
fun DeviceStatusCluster(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var time by remember { mutableStateOf(currentTime()) }
  var battery by remember { mutableIntStateOf(readBattery(context)) }
  var charging by remember { mutableStateOf(false) }
  var network by remember { mutableStateOf(readNetwork(context)) }

  LaunchedEffect(Unit) {
    while (true) {
      time = currentTime()
      delay(60_000L - System.currentTimeMillis() % 60_000L)
    }
  }

  DisposableEffect(context) {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          if (intent == null) return
          val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
          val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
          battery = (level * 100f / scale).toInt().coerceIn(0, 100)
          val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
          charging =
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
              status == BatteryManager.BATTERY_STATUS_FULL
        }
      }
    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    onDispose { runCatching { context.unregisterReceiver(receiver) } }
  }

  DisposableEffect(context) {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val callback =
      object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(networkValue: Network) {
          network = readNetwork(context)
        }

        override fun onCapabilitiesChanged(
          networkValue: Network,
          capabilities: NetworkCapabilities,
        ) {
          network = networkKind(capabilities)
        }

        override fun onLost(networkValue: Network) {
          network = readNetwork(context)
        }
      }
    manager.registerDefaultNetworkCallback(callback)
    onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
  }

  Surface(
    modifier = modifier,
    shape = androidx.compose.foundation.shape.CircleShape,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .68f),
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(time, style = MaterialTheme.typography.labelMedium)
      NetworkGlyph(network)
      BatteryGlyph(battery, charging)
      Text("$battery%", style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun NetworkGlyph(kind: NetworkKind) {
  val color =
    if (kind == NetworkKind.OFFLINE) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface
  val description =
    when (kind) {
      NetworkKind.WIFI -> "Wi-Fi 已连接"
      NetworkKind.CELLULAR -> "移动网络已连接"
      NetworkKind.OFFLINE -> "网络未连接"
    }
  if (kind == NetworkKind.WIFI) {
    Icon(
      painter = painterResource(R.drawable.ic_status_wifi_rounded),
      contentDescription = description,
      tint = color,
      modifier = Modifier.size(19.dp),
    )
    return
  }
  Canvas(
    Modifier.size(width = 20.dp, height = 16.dp).semantics { contentDescription = description }
  ) {
    val stroke = 1.7.dp.toPx()
    when (kind) {
      NetworkKind.WIFI -> Unit
      NetworkKind.CELLULAR -> {
        repeat(4) { index ->
          val barHeight = (4 + index * 3).dp.toPx()
          drawRoundRect(
            color = color,
            topLeft = Offset((2 + index * 5).dp.toPx(), size.height - barHeight),
            size = Size(3.dp.toPx(), barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
          )
        }
      }
      NetworkKind.OFFLINE -> {
        drawCircle(color, radius = 6.dp.toPx(), center = center, style = Stroke(stroke))
        drawLine(
          color,
          Offset(center.x - 5.dp.toPx(), center.y - 5.dp.toPx()),
          Offset(center.x + 5.dp.toPx(), center.y + 5.dp.toPx()),
          strokeWidth = stroke,
        )
      }
    }
  }
}

@Composable
private fun BatteryGlyph(level: Int, charging: Boolean) {
  val color = MaterialTheme.colorScheme.onSurface
  val fillColor = if (level <= 15) MaterialTheme.colorScheme.error else color
  Canvas(
    Modifier.size(width = 24.dp, height = 13.dp).semantics {
      contentDescription = "电量 $level%${if (charging) "，正在充电" else ""}"
    }
  ) {
    drawRoundRect(
      color = color,
      topLeft = Offset(1f, 1f),
      size = Size(size.width - 5f, size.height - 2f),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
      style = Stroke(1.4.dp.toPx()),
    )
    drawRoundRect(
      color = color,
      topLeft = Offset(size.width - 2.dp.toPx(), size.height * .32f),
      size = Size(2.dp.toPx(), size.height * .36f),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f),
    )
    val fillWidth = (size.width - 7.dp.toPx()) * level.coerceIn(0, 100) / 100f
    drawRoundRect(
      color = fillColor,
      topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
      size = Size(fillWidth, size.height - 6.dp.toPx()),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
    )
  }
}

private fun currentTime() = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

private fun readBattery(context: Context): Int {
  val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
  return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
}

private fun readNetwork(context: Context): NetworkKind {
  val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  return manager.getNetworkCapabilities(manager.activeNetwork)?.let(::networkKind)
    ?: NetworkKind.OFFLINE
}

private fun networkKind(capabilities: NetworkCapabilities): NetworkKind =
  when {
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
    else -> NetworkKind.OFFLINE
  }

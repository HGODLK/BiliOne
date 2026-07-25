package dev.openbili.webdemo.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
internal fun YoutubeSeekBar(
  value: Float,
  durationMs: Long,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: (Float) -> Unit,
  onScrubStateChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  var dragging by remember { mutableStateOf(false) }
  val maximum = durationMs.coerceAtLeast(1L).toFloat()
  val progress = (value / maximum).coerceIn(0f, 1f)
  val progressPink = Color(0xFFFF5C8A)
  Canvas(
    modifier =
      modifier.height(26.dp).pointerInput(maximum) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          dragging = true
          onScrubStateChanged(true)
          var target = (down.position.x / size.width).coerceIn(0f, 1f) * maximum
          try {
            onValueChange(target)
            down.consume()
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id } ?: break
              if (!change.pressed) break
              target = (change.position.x / size.width).coerceIn(0f, 1f) * maximum
              onValueChange(target)
              change.consume()
            }
            onValueChangeFinished(target)
          } finally {
            dragging = false
            onScrubStateChanged(false)
          }
        }
      }
  ) {
    val trackHeight = if (dragging) 4.dp.toPx() else 3.dp.toPx()
    val top = (size.height - trackHeight) / 2f
    drawRoundRect(
      color = Color.White.copy(alpha = .34f),
      topLeft = Offset(0f, top),
      size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
    )
    drawRoundRect(
      color = progressPink,
      topLeft = Offset(0f, top),
      size = androidx.compose.ui.geometry.Size(size.width * progress, trackHeight),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
    )
    drawCircle(
      color = progressPink,
      radius = (if (dragging) 6.dp else 5.dp).toPx(),
      center = Offset(size.width * progress, size.height / 2f),
    )
  }
}

@Composable
internal fun DanmakuControlIcon(modifier: Modifier = Modifier, color: Color) {
  Canvas(modifier) {
    val stroke = Stroke(width = 1.8.dp.toPx())
    drawRoundRect(
      color = color,
      topLeft = Offset(size.width * .08f, size.height * .2f),
      size = androidx.compose.ui.geometry.Size(size.width * .84f, size.height * .6f),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
      style = stroke,
    )
    drawCircle(color, radius = 1.5.dp.toPx(), center = Offset(size.width * .35f, size.height * .5f))
    drawCircle(color, radius = 1.5.dp.toPx(), center = Offset(size.width * .65f, size.height * .5f))
  }
}

@Composable
internal fun FullscreenControlIcon(
  exiting: Boolean,
  modifier: Modifier = Modifier,
  color: Color,
) {
  Canvas(modifier) {
    val strokeWidth = 2.dp.toPx()
    val inset = if (exiting) size.width * .28f else size.width * .12f
    val arm = size.width * .24f
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    listOf(
        Offset(left, top) to Offset(left + arm, top),
        Offset(left, top) to Offset(left, top + arm),
        Offset(right, top) to Offset(right - arm, top),
        Offset(right, top) to Offset(right, top + arm),
        Offset(left, bottom) to Offset(left + arm, bottom),
        Offset(left, bottom) to Offset(left, bottom - arm),
        Offset(right, bottom) to Offset(right - arm, bottom),
        Offset(right, bottom) to Offset(right, bottom - arm),
      )
      .forEach { (start, end) ->
        drawLine(color, start, end, strokeWidth = strokeWidth)
      }
  }
}

internal fun formatPlayerTime(milliseconds: Long): String {
  val total = (milliseconds / 1000).coerceAtLeast(0)
  val hours = total / 3600
  val minutes = (total % 3600) / 60
  val seconds = total % 60
  return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
  else "%d:%02d".format(minutes, seconds)
}

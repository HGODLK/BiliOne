package dev.openbili.webdemo.video

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.VideoChapter
import dev.openbili.webdemo.ui.controlFocusOutline
import dev.openbili.webdemo.ui.timelinePointHighlightColor
import kotlin.math.abs

private const val CONTROL_SEEK_STEP_MS = 5_000L

@Composable
internal fun YoutubeSeekBar(
  value: Float,
  durationMs: Long,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: (Float) -> Unit,
  onScrubStateChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  controlEnabled: Boolean = false,
  controlFocusRequester: FocusRequester? = null,
  controlDownFocusRequester: FocusRequester? = null,
  chapters: List<VideoChapter> = emptyList(),
) {
  var dragging by remember { mutableStateOf(false) }
  val maximum = durationMs.coerceAtLeast(1L).toFloat()
  val progress = (value / maximum).coerceIn(0f, 1f)
  val progressColor = MaterialTheme.colorScheme.primary
  val pointHighlightColor =
    timelinePointHighlightColor(progressColor, MaterialTheme.colorScheme.primaryContainer)
  val chapterBoundaries =
    remember(durationMs, chapters) { chapterBoundaryFractions(durationMs, chapters) }
  val activeChapterBoundaries =
    remember(value, durationMs, chapters) {
      activeChapterBoundaryFractions(durationMs, value.toLong(), chapters)
    }
  Canvas(
    modifier =
      modifier
        .height(26.dp)
        .then(
          if (controlEnabled && controlFocusRequester != null) {
            Modifier.focusRequester(controlFocusRequester)
          } else {
            Modifier
          }
        )
        .then(
          if (controlEnabled) {
            Modifier.focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = FocusRequester.Cancel
                down = controlDownFocusRequester ?: FocusRequester.Cancel
              }
              .onPreviewKeyEvent { event ->
                when (event.nativeKeyEvent.keyCode) {
                  AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                  AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.type == KeyEventType.KeyDown) {
                      val delta =
                        if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT) {
                          -CONTROL_SEEK_STEP_MS
                        } else {
                          CONTROL_SEEK_STEP_MS
                        }
                      val target = (value + delta).coerceIn(0f, maximum)
                      onValueChange(target)
                      onValueChangeFinished(target)
                    }
                    true
                  }
                  else -> false
                }
              }
              .controlFocusOutline(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
                width = 3.dp,
              )
              .focusable()
          } else {
            Modifier
          }
        )
        .pointerInput(maximum, chapters) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          dragging = true
          onScrubStateChanged(true)
          var target = (down.position.x / size.width).coerceIn(0f, 1f) * maximum
          var moved = false
          try {
            onValueChange(target)
            down.consume()
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id } ?: break
              if (!change.pressed) break
              moved =
                moved ||
                  abs(change.position.x - down.position.x) > 8f ||
                    abs(change.position.y - down.position.y) > 8f
              target = (change.position.x / size.width).coerceIn(0f, 1f) * maximum
              onValueChange(target)
              change.consume()
            }
            if (!moved) {
              nearestChapterBoundaryForTap(
                  xPx = down.position.x,
                  widthPx = size.width.toFloat(),
                  durationMs = durationMs,
                  chapters = chapters,
                  hitSlopPx = 14f,
                )
                ?.let {
                  target = it.toFloat()
                  // 先同步预览值，再通知完成；ModernPlayerControls 会优先读取预览值。
                  onValueChange(target)
                }
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
      color = progressColor,
      topLeft = Offset(0f, top),
      size = androidx.compose.ui.geometry.Size(size.width * progress, trackHeight),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
    )
    // B 站 view_points 的章节边界叠在轨道上；拖动仍连续，点击节点时由手势层精确 seek。
    chapterBoundaries.forEach { fraction ->
      val x = size.width * fraction
      drawLine(
        color = Color.Black.copy(alpha = 0.55f),
        start = Offset(x, top - 1.dp.toPx()),
        end = Offset(x, top + trackHeight + 1.dp.toPx()),
        strokeWidth = 1.dp.toPx(),
      )
    }
    // 节点覆盖在轨道和分隔线上；当前章节前后的节点使用同主题的区分色。
    chapterBoundaries.forEach { fraction ->
      val x = size.width * fraction
      drawCircle(
        color = if (fraction in activeChapterBoundaries) pointHighlightColor else Color.White,
        radius = 3.dp.toPx(),
        center = Offset(x, size.height / 2f),
      )
    }
    // 播放进度大圆点始终最后绘制，避免与章节节点重叠时被遮挡。
    drawCircle(
      color = progressColor,
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

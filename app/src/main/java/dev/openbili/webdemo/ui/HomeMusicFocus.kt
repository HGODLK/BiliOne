package dev.openbili.webdemo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 音乐页专用焦点高亮，保证电视远距离观看时仍能看清当前项。 */
@Composable
internal fun Modifier.musicFocusChrome(
  shape: Shape,
  color: Color,
  width: Dp = 3.dp,
  fill: Color = color.copy(alpha = .16f),
  enabled: Boolean = LocalControlMode.current,
): Modifier {
  if (!enabled) return this
  val focusChromeVisible = LocalControlFocusVisible.current
  var focused by remember { mutableStateOf(false) }
  return onFocusChanged { focused = it.hasFocus }
    .drawWithContent {
      if (focused && focusChromeVisible) {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, fill, Fill)
      }
      drawContent()
      if (focused && focusChromeVisible) {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, color, Stroke(width.toPx()))
        val innerWidth = (width.toPx() * .34f).coerceAtLeast(1.dp.toPx())
        drawOutline(outline, Color.White.copy(alpha = .96f), Stroke(innerWidth))
      }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOutline(
  outline: Outline,
  color: Color,
  style: DrawStyle,
) {
  when (outline) {
    is Outline.Rectangle ->
      drawRect(
        color = color,
        topLeft = outline.rect.topLeft,
        size = Size(outline.rect.width, outline.rect.height),
        style = style,
      )
    is Outline.Rounded ->
      drawPath(
        path = Path().apply { addRoundRect(outline.roundRect) },
        color = color,
        style = style,
      )
    is Outline.Generic -> drawPath(path = outline.path, color = color, style = style)
  }
}

package dev.openbili.webdemo.ui

/**
 * 控制器焦点描边工具。
 *
 * 提供 [Modifier.controlFocusOutline] 修饰符：当组合中的某个后代焦点目标持有控制器焦点
 * 时，在其外围绘制贴合形状（矩形/圆角/自定义路径）的主题描边，用于在遥控器/手柄
 * 导航模式下高亮当前选中项。
 */

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 当后代焦点目标持有控制器焦点时，绘制感知形状的主题描边。 */
@Composable
fun Modifier.controlFocusOutline(
  shape: Shape,
  color: Color,
  width: Dp = 2.dp,
  enabled: Boolean = LocalControlMode.current,
  includeDescendants: Boolean = true,
): Modifier {
  val focusChromeVisible = LocalControlFocusVisible.current
  // 未启用控制器模式时直接返回原修饰符，不做任何绘制
  if (!enabled) return this
  // 本修饰符范围内是否存在持有焦点的目标
  var focused by remember { mutableStateOf(false) }
  // 焦点变化时更新 focused；includeDescendants 决定是否把后代焦点也算作选中
  return onFocusChanged { focused = if (includeDescendants) it.hasFocus else it.isFocused }
    .drawWithContent {
      drawContent()
      // 仅当存在焦点且焦点描边可见时才绘制
      if (focused && focusChromeVisible) {
        // 描边宽度换算成像素
        val stroke = Stroke(width.toPx())
        // 根据形状生成轮廓，分别以矩形/圆角矩形/通用路径方式描边
        when (val outline = shape.createOutline(size, layoutDirection, this)) {
          is Outline.Rectangle ->
            drawRect(
              color = color,
              topLeft = outline.rect.topLeft,
              size = Size(outline.rect.width, outline.rect.height),
              style = stroke,
            )
          is Outline.Rounded ->
            drawPath(
              path = Path().apply { addRoundRect(outline.roundRect) },
              color = color,
              style = stroke,
            )
          is Outline.Generic -> drawPath(path = outline.path, color = color, style = stroke)
        }
      }
    }
}

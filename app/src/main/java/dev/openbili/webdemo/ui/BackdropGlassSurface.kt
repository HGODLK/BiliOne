package dev.openbili.webdemo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 一块裁剪过、本地采样的玻璃表面。[backdropBounds] 描述捕获图层在应用根中的起始位置。
 * 弹窗可以提供 [sampleOriginInRoot]，因为它们的本地坐标不与主组合根共享。
 */
@Composable
fun BackdropGlassSurface(
  backdropLayer: GraphicsLayer?,
  backdropBounds: Rect,
  underlayLayer: GraphicsLayer? = null,
  underlayBounds: Rect = Rect.Zero,
  modifier: Modifier = Modifier,
  shape: Shape = AppShapes.medium,
  blurRadius: Dp = 12.dp,
  containerColor: Color,
  fallbackColor: Color? = null,
  border: BorderStroke? = null,
  tonalElevation: Dp = 0.dp,
  shadowElevation: Dp = 0.dp,
  sampleOriginInRoot: Offset? = null,
  content: @Composable () -> Unit,
) {
  val glassEffectsEnabled = LocalGlassEffectsEnabled.current
  val resolvedContainerColor =
    if (glassEffectsEnabled) containerColor else fallbackColor ?: MaterialTheme.colorScheme.surface
  var surfaceBounds by remember { mutableStateOf(Rect.Zero) }
  Surface(
    modifier = modifier.onGloballyPositioned { surfaceBounds = it.boundsInRoot() },
    shape = shape,
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
    border = border,
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
  ) {
    Box {
      val layer = backdropLayer
      val baseLayer = underlayLayer
      val sampleOrigin = sampleOriginInRoot ?: surfaceBounds.topLeft
      if (
        glassEffectsEnabled &&
          ((layer != null && backdropBounds.width > 0f && backdropBounds.height > 0f) ||
            (baseLayer != null && underlayBounds.width > 0f && underlayBounds.height > 0f)) &&
          (sampleOriginInRoot != null || surfaceBounds.width > 0f)
      ) {
        Canvas(Modifier.matchParentSize().clip(shape).blur(blurRadius)) {
          if (baseLayer != null && underlayBounds.width > 0f && underlayBounds.height > 0f) {
            translate(
              left = underlayBounds.left - sampleOrigin.x,
              top = underlayBounds.top - sampleOrigin.y,
            ) {
              drawLayer(baseLayer)
            }
          }
          if (layer != null && backdropBounds.width > 0f && backdropBounds.height > 0f) {
            translate(
              left = backdropBounds.left - sampleOrigin.x,
              top = backdropBounds.top - sampleOrigin.y,
            ) {
              drawLayer(layer)
            }
          }
        }
      }
      Box(Modifier.matchParentSize().clip(shape).background(resolvedContainerColor))
      content()
    }
  }
}

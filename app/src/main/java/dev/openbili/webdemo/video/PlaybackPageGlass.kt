package dev.openbili.webdemo.video

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.ui.BackdropGlassSurface

internal data class PlaybackPageGlassBackdrop(
  val layer: GraphicsLayer? = null,
  val bounds: Rect = Rect.Zero,
)

@Composable
internal fun PlaybackPageGlassSurface(
  backdrop: PlaybackPageGlassBackdrop,
  modifier: Modifier = Modifier,
  shape: Shape,
  containerColor: Color = Color.Black.copy(alpha = .18f),
  fallbackColor: Color = Color.Black.copy(alpha = .52f),
  border: BorderStroke? = BorderStroke(.75.dp, Color.White.copy(alpha = .18f)),
  blurRadius: Dp = 15.dp,
  content: @Composable () -> Unit,
) {
  BackdropGlassSurface(
    backdropLayer = backdrop.layer,
    backdropBounds = backdrop.bounds,
    modifier = modifier,
    shape = shape,
    blurRadius = blurRadius,
    containerColor = containerColor,
    fallbackColor = fallbackColor,
    border = border,
    content = content,
  )
}

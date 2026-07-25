package dev.openbili.webdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A translucent, readable surface. It deliberately does not blur its own content or continuously
 * sample content behind it; unsupported blur configurations therefore require no special fallback.
 */
@Composable
fun GlassSurface(
  modifier: Modifier = Modifier,
  shape: Shape = AppShapes.medium,
  containerColor: Color = GlassDefaults.containerColor(),
  borderColor: Color = GlassDefaults.borderColor(),
  tonalElevation: Dp = 0.dp,
  content: @Composable () -> Unit,
) {
  val overlay =
    remember(containerColor) {
      Brush.verticalGradient(
        listOf(
          Color.White.copy(alpha = GlassTokens.GradientOverlayAlpha),
          containerColor.copy(alpha = 0f),
        )
      )
    }
  Surface(
    modifier = modifier.border(GlassTokens.BorderWidth, borderColor, shape),
    shape = shape,
    color = containerColor,
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = tonalElevation,
  ) {
    Box(Modifier.background(overlay)) { content() }
  }
}

object GlassDefaults {
  @Composable
  fun containerColor(blurAvailable: Boolean = false): Color {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val alpha =
      when {
        isDark && blurAvailable -> GlassTokens.DarkSurfaceAlpha
        isDark -> GlassTokens.DarkFallbackSurfaceAlpha
        blurAvailable -> GlassTokens.LightSurfaceAlpha
        else -> GlassTokens.LightFallbackSurfaceAlpha
      }
    return colors.surface.copy(alpha = alpha)
  }

  @Composable
  fun borderColor(): Color {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    return colors.outline.copy(
      alpha = if (isDark) GlassTokens.DarkBorderAlpha else GlassTokens.LightBorderAlpha
    )
  }
}

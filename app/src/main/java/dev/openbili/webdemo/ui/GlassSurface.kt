package dev.openbili.webdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalGlassEffectsEnabled = staticCompositionLocalOf { true }

/**
 * 一块半透明、可读的表面。它有意不模糊自身内容，也不持续采样其后的内容；
 * 因此不受支持的模糊配置无需特殊回退。
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
  val glassEffectsEnabled = LocalGlassEffectsEnabled.current
  val resolvedContainerColor =
    if (glassEffectsEnabled) containerColor else MaterialTheme.colorScheme.surface
  val resolvedBorderColor =
    if (glassEffectsEnabled) borderColor else MaterialTheme.colorScheme.outlineVariant
  val overlay =
    remember(resolvedContainerColor, glassEffectsEnabled) {
      Brush.verticalGradient(
        listOf(
          Color.White.copy(
            alpha = if (glassEffectsEnabled) GlassTokens.GradientOverlayAlpha else 0f
          ),
          resolvedContainerColor.copy(alpha = 0f),
        )
      )
    }
  Surface(
    modifier = modifier.border(GlassTokens.BorderWidth, resolvedBorderColor, shape),
    shape = shape,
    color = resolvedContainerColor,
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

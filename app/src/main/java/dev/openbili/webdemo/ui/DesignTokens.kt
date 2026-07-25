package dev.openbili.webdemo.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared visual constants for the Compose shell. */
object AppColors {
  val LightPrimary = Color(0xFF1A7B8B)
  val LightOnPrimary = Color(0xFFFFFFFF)
  val LightBackground = Color(0xFFF7F8F8)
  val LightSurface = Color(0xFFFFFFFF)
  val LightSurfaceVariant = Color(0xFFECEFF0)
  val LightOnBackground = Color(0xFF17191A)
  val LightOnSurface = Color(0xFF17191A)
  val LightOnSurfaceVariant = Color(0xFF62686A)
  val LightOutline = Color(0xFFC8CFD1)
  val LightError = Color(0xFFC62828)
  val LightOnError = Color(0xFFFFFFFF)

  val DarkPrimary = Color(0xFF66D3E5)
  val DarkOnPrimary = Color(0xFF00363E)
  val DarkBackground = Color(0xFF101314)
  val DarkSurface = Color(0xFF1A1E20)
  val DarkSurfaceVariant = Color(0xFF252B2D)
  val DarkOnBackground = Color(0xFFE2E6E7)
  val DarkOnSurface = Color(0xFFE2E6E7)
  val DarkOnSurfaceVariant = Color(0xFFAAB2B4)
  val DarkOutline = Color(0xFF465053)
  val DarkError = Color(0xFFFF8A85)
  val DarkOnError = Color(0xFF690005)
}

val AppShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
  )

object SpacingTokens {
  val XSmall = 4.dp
  val Small = 8.dp
  val Medium = 12.dp
  val Large = 16.dp
  val XLarge = 20.dp
  val XXLarge = 24.dp
  val Huge = 32.dp
}

object LayoutTokens {
  val MinimumTouchTarget = 48.dp
  val TopBarHeight = 64.dp
  val IconSize = 24.dp
  val CompactHorizontalPadding = 12.dp
  val MediumHorizontalPadding = 20.dp
  val ExpandedHorizontalPadding = 24.dp
  val ExtraExpandedHorizontalPadding = 32.dp
  val CompactGridSpacing = 10.dp
  val MediumGridSpacing = 12.dp
  val ExpandedGridSpacing = 16.dp
}

/** Shapes shared by video cards, cover transitions, and the embedded player. */
object VideoShapeTokens {
  val CornerRadius = 20.dp
  val Card = RoundedCornerShape(CornerRadius)
  val Player = Card
}

object GlassTokens {
  val BorderWidth = 0.75.dp
  val TopBarTonalElevation = 2.dp
  val CardTonalElevation = 1.dp
  val PressedCardTonalElevation = 0.dp
  val DialogBackgroundBlur = 36.dp
  val DialogBlurBehind = 20.dp

  const val LightSurfaceAlpha = 0.82f
  const val DarkSurfaceAlpha = 0.88f
  const val LightFallbackSurfaceAlpha = 0.96f
  const val DarkFallbackSurfaceAlpha = 0.97f
  const val LightBorderAlpha = 0.62f
  const val DarkBorderAlpha = 0.70f
  const val GradientOverlayAlpha = 0.12f
  const val MediaLabelAlpha = 0.72f

  val MediaLabelBackground = Color(0xFF111314)
  val MediaLabelContent = Color(0xFFFFFFFF)
}

object MotionTokens {
  const val Quick = 120
  const val Standard = 220
  const val Emphasized = 320
  const val SkeletonPulse = 1_600
  const val PressedScale = 0.98f
}

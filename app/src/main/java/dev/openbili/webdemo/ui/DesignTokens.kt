package dev.openbili.webdemo.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Compose 外壳的共享视觉常量。 */
object AppColors {
  val LightPrimary = Color(0xFF1A7B8B)
  val LightOnPrimary = Color(0xFFFFFFFF)
  val LightPrimaryContainer = Color(0xFFB9EDF5)
  val LightOnPrimaryContainer = Color(0xFF002F36)
  val LightSecondaryContainer = Color(0xFFD1E6E9)
  val LightOnSecondaryContainer = Color(0xFF102023)
  val LightTertiary = Color(0xFF5C5E7F)
  val LightTertiaryContainer = Color(0xFFE2E0FF)
  val LightOnTertiaryContainer = Color(0xFF191936)
  val LightBackground = Color(0xFFF7F8F8)
  val LightSurface = Color(0xFFFFFFFF)
  val LightSurfaceVariant = Color(0xFFECEFF0)
  val LightSurfaceContainerLow = Color(0xFFF2F4F4)
  val LightSurfaceContainer = Color(0xFFEDF0F0)
  val LightOnBackground = Color(0xFF17191A)
  val LightOnSurface = Color(0xFF17191A)
  val LightOnSurfaceVariant = Color(0xFF62686A)
  val LightOutline = Color(0xFFC8CFD1)
  val LightOutlineVariant = Color(0xFFDCE2E3)
  val LightError = Color(0xFFC62828)
  val LightOnError = Color(0xFFFFFFFF)
  val LightErrorContainer = Color(0xFFFFDAD6)
  val LightOnErrorContainer = Color(0xFF410002)

  val DarkPrimary = Color(0xFF66D3E5)
  val DarkOnPrimary = Color(0xFF00363E)
  val DarkPrimaryContainer = Color(0xFF0C505B)
  val DarkOnPrimaryContainer = Color(0xFFB9EDF5)
  val DarkSecondaryContainer = Color(0xFF304A4E)
  val DarkOnSecondaryContainer = Color(0xFFD1E6E9)
  val DarkTertiary = Color(0xFFC5C3EB)
  val DarkTertiaryContainer = Color(0xFF444665)
  val DarkOnTertiaryContainer = Color(0xFFE2E0FF)
  val DarkBackground = Color(0xFF101314)
  val DarkSurface = Color(0xFF1A1E20)
  val DarkSurfaceVariant = Color(0xFF252B2D)
  val DarkSurfaceContainerLow = Color(0xFF171B1D)
  val DarkSurfaceContainer = Color(0xFF1E2325)
  val DarkOnBackground = Color(0xFFE2E6E7)
  val DarkOnSurface = Color(0xFFE2E6E7)
  val DarkOnSurfaceVariant = Color(0xFFAAB2B4)
  val DarkOutline = Color(0xFF465053)
  val DarkOutlineVariant = Color(0xFF30383A)
  val DarkError = Color(0xFFFF8A85)
  val DarkOnError = Color(0xFF690005)
  val DarkErrorContainer = Color(0xFF93000A)
  val DarkOnErrorContainer = Color(0xFFFFDAD6)
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

internal const val PLAYER_CORNER_RADIUS_DP = 20f

/** 视频卡片、封面转场和内嵌播放器共享的形状。 */
object VideoShapeTokens {
  val CornerRadius = PLAYER_CORNER_RADIUS_DP.dp
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

/** 绘制在用户所选页面背景之上的视频页表面的透明度级别。 */
object VideoPageSurfaceTokens {
  /** 当前视频封面作为静态模糊背景时额外降低的亮度。 */
  const val BlurredCoverBackgroundDimAlpha = 0.10f

  const val LightTopScrimAlpha = 0.62f
  const val DarkTopScrimAlpha = 0.58f
  const val LightAmbientScrimAlpha = 0.10f
  const val DarkAmbientScrimAlpha = 0.14f

  const val LightCommentCardAlpha = 0.74f
  const val DarkCommentCardAlpha = 0.68f
  const val LightCommentGradientAlpha = 0.78f
  const val DarkCommentGradientAlpha = 0.72f
  const val LightActionPanelAlpha = 0.78f
  const val DarkActionPanelAlpha = 0.74f
  const val LightInputDockAlpha = 0.88f
  const val DarkInputDockAlpha = 0.84f
  const val LightInputFieldAlpha = 0.78f
  const val DarkInputFieldAlpha = 0.72f
  const val LightDialogAlpha = 0.96f
  const val DarkDialogAlpha = 0.97f
}

object HomeGlassTokens {
  val BlurRadius = 30.dp

  const val LightContainerAlpha = 0.52f
  const val DarkContainerAlpha = 0.58f
  const val LightControlAlpha = 0.42f
  const val DarkControlAlpha = 0.48f
  const val LightBorderAlpha = 0.58f
  const val DarkBorderAlpha = 0.68f
}

/** 音乐页背景保持可辨认，同时为标题、频谱与控制区提供稳定对比度。 */
object MusicPageVisualTokens {
  const val BackgroundScrimAlpha = 0.05f
}

object MotionTokens {
  const val Quick = 120
  const val Standard = 220
  const val Emphasized = 320
  const val SkeletonPulse = 1_600
  const val PressedScale = 0.98f
}

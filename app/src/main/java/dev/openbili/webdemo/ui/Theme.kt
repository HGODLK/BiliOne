package dev.openbili.webdemo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.openbili.webdemo.R

private val LightColors =
  lightColorScheme(
    primary = AppColors.LightPrimary,
    onPrimary = AppColors.LightOnPrimary,
    background = AppColors.LightBackground,
    surface = AppColors.LightSurface,
    surfaceVariant = AppColors.LightSurfaceVariant,
    onBackground = AppColors.LightOnBackground,
    onSurface = AppColors.LightOnSurface,
    onSurfaceVariant = AppColors.LightOnSurfaceVariant,
    outline = AppColors.LightOutline,
    error = AppColors.LightError,
    onError = AppColors.LightOnError,
  )
private val DarkColors =
  darkColorScheme(
    primary = AppColors.DarkPrimary,
    onPrimary = AppColors.DarkOnPrimary,
    background = AppColors.DarkBackground,
    surface = AppColors.DarkSurface,
    surfaceVariant = AppColors.DarkSurfaceVariant,
    onBackground = AppColors.DarkOnBackground,
    onSurface = AppColors.DarkOnSurface,
    onSurfaceVariant = AppColors.DarkOnSurfaceVariant,
    outline = AppColors.DarkOutline,
    error = AppColors.DarkError,
    onError = AppColors.DarkOnError,
  )
val AppFontFamily =
  FontFamily(
    Font(R.font.sarasa_ui_sc_regular, weight = FontWeight.Normal),
    Font(R.font.sarasa_ui_sc_semibold, weight = FontWeight.Medium),
    Font(R.font.sarasa_ui_sc_semibold, weight = FontWeight.SemiBold),
    Font(R.font.sarasa_ui_sc_bold, weight = FontWeight.Bold),
  )

private val BaseTypography =
  Typography().run {
    Typography(
      displayLarge = displayLarge.copy(fontFamily = AppFontFamily),
      displayMedium = displayMedium.copy(fontFamily = AppFontFamily),
      displaySmall = displaySmall.copy(fontFamily = AppFontFamily),
      headlineLarge = headlineLarge.copy(fontFamily = AppFontFamily),
      headlineMedium = headlineMedium.copy(fontFamily = AppFontFamily),
      headlineSmall = headlineSmall.copy(fontFamily = AppFontFamily),
      titleLarge = titleLarge.copy(fontFamily = AppFontFamily),
      titleMedium = titleMedium.copy(fontFamily = AppFontFamily),
      titleSmall = titleSmall.copy(fontFamily = AppFontFamily),
      bodyLarge = bodyLarge.copy(fontFamily = AppFontFamily),
      bodyMedium = bodyMedium.copy(fontFamily = AppFontFamily),
      bodySmall = bodySmall.copy(fontFamily = AppFontFamily),
      labelLarge = labelLarge.copy(fontFamily = AppFontFamily),
      labelMedium = labelMedium.copy(fontFamily = AppFontFamily),
      labelSmall = labelSmall.copy(fontFamily = AppFontFamily),
    )
  }

private val AppTypography =
  Typography(
    headlineMedium =
      BaseTypography.headlineMedium.copy(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Medium,
      ),
    titleLarge =
      BaseTypography.titleLarge.copy(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
      ),
    titleMedium =
      BaseTypography.titleMedium.copy(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
      ),
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge =
      BaseTypography.labelLarge.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
      ),
    labelMedium =
      BaseTypography.labelMedium.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
      ),
    labelSmall = BaseTypography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
    // Keep every Material 3 text role on the bundled family, including roles not customized above.
    displayLarge = BaseTypography.displayLarge,
    displayMedium = BaseTypography.displayMedium,
    displaySmall = BaseTypography.displaySmall,
    headlineLarge = BaseTypography.headlineLarge,
    headlineSmall = BaseTypography.headlineSmall,
    titleSmall = BaseTypography.titleSmall,
    bodySmall = BaseTypography.bodySmall,
  )

@Composable
fun BiliDemoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = AppTypography,
    shapes = AppShapes,
    content = content,
  )
}

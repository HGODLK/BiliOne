package dev.openbili.webdemo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.openbili.webdemo.R
import dev.openbili.webdemo.settings.ThemeAccent

private data class AccentRoles(
  val lightPrimary: Color,
  val lightOnPrimary: Color,
  val lightPrimaryContainer: Color,
  val lightOnPrimaryContainer: Color,
  val darkPrimary: Color,
  val darkOnPrimary: Color,
  val darkPrimaryContainer: Color,
  val darkOnPrimaryContainer: Color,
)

private fun ThemeAccent.roles(): AccentRoles =
  when (this) {
    ThemeAccent.CYAN ->
      AccentRoles(
        AppColors.LightPrimary,
        AppColors.LightOnPrimary,
        AppColors.LightPrimaryContainer,
        AppColors.LightOnPrimaryContainer,
        AppColors.DarkPrimary,
        AppColors.DarkOnPrimary,
        AppColors.DarkPrimaryContainer,
        AppColors.DarkOnPrimaryContainer,
      )
    ThemeAccent.BILI_PINK ->
      AccentRoles(
        Color(0xFFA7355B),
        Color.White,
        Color(0xFFFFD9E2),
        Color(0xFF3E001D),
        Color(0xFFFFB1C6),
        Color(0xFF5F1134),
        Color(0xFF84254C),
        Color(0xFFFFD9E2),
      )
    ThemeAccent.BLUE ->
      AccentRoles(
        Color(0xFF3566A8),
        Color.White,
        Color(0xFFD7E3FF),
        Color(0xFF001B3F),
        Color(0xFFAAC7FF),
        Color(0xFF003064),
        Color(0xFF174A7E),
        Color(0xFFD7E3FF),
      )
    ThemeAccent.PURPLE ->
      AccentRoles(
        Color(0xFF73558F),
        Color.White,
        Color(0xFFF1DAFF),
        Color(0xFF2B0C46),
        Color(0xFFDDB9F8),
        Color(0xFF42245C),
        Color(0xFF5A3B73),
        Color(0xFFF1DAFF),
      )
    ThemeAccent.GREEN ->
      AccentRoles(
        Color(0xFF3F6F4A),
        Color.White,
        Color(0xFFC2F0CB),
        Color(0xFF00210B),
        Color(0xFFA6D7AD),
        Color(0xFF12391E),
        Color(0xFF285230),
        Color(0xFFC2F0CB),
      )
  }

private fun createLightColors(accent: ThemeAccent) =
  lightColorScheme(
    primary = accent.roles().lightPrimary,
    onPrimary = accent.roles().lightOnPrimary,
    primaryContainer = accent.roles().lightPrimaryContainer,
    onPrimaryContainer = accent.roles().lightOnPrimaryContainer,
    secondaryContainer = AppColors.LightSecondaryContainer,
    onSecondaryContainer = AppColors.LightOnSecondaryContainer,
    tertiary = AppColors.LightTertiary,
    tertiaryContainer = AppColors.LightTertiaryContainer,
    onTertiaryContainer = AppColors.LightOnTertiaryContainer,
    background = AppColors.LightBackground,
    surface = AppColors.LightSurface,
    surfaceVariant = AppColors.LightSurfaceVariant,
    surfaceContainerLow = AppColors.LightSurfaceContainerLow,
    surfaceContainer = AppColors.LightSurfaceContainer,
    onBackground = AppColors.LightOnBackground,
    onSurface = AppColors.LightOnSurface,
    onSurfaceVariant = AppColors.LightOnSurfaceVariant,
    outline = AppColors.LightOutline,
    outlineVariant = AppColors.LightOutlineVariant,
    error = AppColors.LightError,
    onError = AppColors.LightOnError,
    errorContainer = AppColors.LightErrorContainer,
    onErrorContainer = AppColors.LightOnErrorContainer,
  )

private fun createDarkColors(accent: ThemeAccent) =
  darkColorScheme(
    primary = accent.roles().darkPrimary,
    onPrimary = accent.roles().darkOnPrimary,
    primaryContainer = accent.roles().darkPrimaryContainer,
    onPrimaryContainer = accent.roles().darkOnPrimaryContainer,
    secondaryContainer = AppColors.DarkSecondaryContainer,
    onSecondaryContainer = AppColors.DarkOnSecondaryContainer,
    tertiary = AppColors.DarkTertiary,
    tertiaryContainer = AppColors.DarkTertiaryContainer,
    onTertiaryContainer = AppColors.DarkOnTertiaryContainer,
    background = AppColors.DarkBackground,
    surface = AppColors.DarkSurface,
    surfaceVariant = AppColors.DarkSurfaceVariant,
    surfaceContainerLow = AppColors.DarkSurfaceContainerLow,
    surfaceContainer = AppColors.DarkSurfaceContainer,
    onBackground = AppColors.DarkOnBackground,
    onSurface = AppColors.DarkOnSurface,
    onSurfaceVariant = AppColors.DarkOnSurfaceVariant,
    outline = AppColors.DarkOutline,
    outlineVariant = AppColors.DarkOutlineVariant,
    error = AppColors.DarkError,
    onError = AppColors.DarkOnError,
    errorContainer = AppColors.DarkErrorContainer,
    onErrorContainer = AppColors.DarkOnErrorContainer,
  )

private val LightColors = ThemeAccent.entries.associateWith(::createLightColors)
private val DarkColors = ThemeAccent.entries.associateWith(::createDarkColors)
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
fun BiliDemoTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  accent: ThemeAccent = ThemeAccent.CYAN,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors.getValue(accent) else LightColors.getValue(accent),
    typography = AppTypography,
    shapes = AppShapes,
    content = content,
  )
}

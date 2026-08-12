package dev.openbili.webdemo.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoCardPaletteTest {
  @Test
  fun extractsTwoDominantCoverColors() {
    val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.rgb(210, 56, 48))
    for (x in 0 until 4) {
      for (y in 0 until 8) bitmap.setPixel(x, y, android.graphics.Color.rgb(30, 70, 210))
    }

    val colors = extractVideoCoverDominantColors(bitmap)

    assertEquals(2, colors.size)
    assertTrue(videoColorDistance(colors[0], colors[1]) > 64f)
  }

  @Test
  fun gradientStaysSubtleAndUsesAdjacentHue() {
    val surface = Color(0xFFF7F7FA)
    val dominantColors = listOf(Color(0xFF2C78C7), Color(0xFFC76D2C))

    val (start, end) = videoCardGradientColors(dominantColors, surface)

    assertNotEquals(start, end)
    assertTrue(start.red > dominantColors.first().red)
    assertTrue(start.blue < surface.blue)
  }

  @Test
  fun adaptiveContentKeepsSmallTextReadableOnLightGradient() {
    val start = Color(0xFFE5D6C5)
    val end = Color(0xFFBFD5DF)

    val colors = videoCardContentColors(start, end)

    assertTrue(videoCardContrastRatio(colors.primary, start) >= 4.5f)
    assertTrue(videoCardContrastRatio(colors.primary, end) >= 4.5f)
    assertTrue(videoCardContrastRatio(colors.secondary, start) >= 4.5f)
    assertTrue(videoCardContrastRatio(colors.secondary, end) >= 4.5f)
  }

  @Test
  fun adaptiveContentSwitchesToLightTextOnDarkGradient() {
    val colors = videoCardContentColors(Color(0xFF17202A), Color(0xFF2C2038))

    assertEquals(Color.White, colors.primary)
    assertTrue(videoCardContrastRatio(colors.secondary, Color(0xFF17202A)) >= 4.5f)
  }
}

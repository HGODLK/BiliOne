package dev.openbili.webdemo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMusicLayoutTest {
  @Test
  fun `spectrum region stays to the right of the measured player`() {
    val region =
      musicSpectrumRegion(
        windowWidthPx = 1_280f,
        ambientLeftPx = 0f,
        ambientWidthPx = 1_280f,
        playerRightPx = 772f,
        edgeClearancePx = 24f,
        desiredWidthPx = 470f,
      )

    assertEquals(1_256f, region.rightPx, .001f)
    assertTrue(region.leftPx >= 796f)
    assertTrue(region.centerPx in region.leftPx..region.rightPx)
  }

  @Test
  fun `spectrum region shrinks instead of crossing player on narrow ratios`() {
    val region =
      musicSpectrumRegion(
        windowWidthPx = 840f,
        ambientLeftPx = 0f,
        ambientWidthPx = 840f,
        playerRightPx = 760f,
        edgeClearancePx = 24f,
        desiredWidthPx = 310f,
      )

    assertEquals(816f, region.rightPx, .001f)
    assertTrue(region.widthPx <= 56f)
    assertTrue(region.leftPx >= 784f)
  }
}

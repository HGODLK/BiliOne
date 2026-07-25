package dev.openbili.webdemo.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveVideoPanesTest {
  @Test
  fun tabletLandscapeWindowUsesSplitPanes() {
    assertTrue(shouldUseSplitVideoPanes(2560, 1564, density = 2.25f, fontScale = 1.4f))
  }

  @Test
  fun shortLandscapeWindowDoesNotUseSplitPanes() {
    assertFalse(shouldUseSplitVideoPanes(1280, 580, density = 2f, fontScale = 1f))
  }

  @Test
  fun portraitWindowDoesNotUseSideBySideComments() {
    assertFalse(shouldUseSplitVideoPanes(1280, 1848, density = 2f, fontScale = 1f))
  }

  @Test
  fun largeFontScaleReservesAReadableCommentPane() {
    val spec = videoPaneSpec(2560, 1564, density = 2.25f, fontScale = 1.4f)
    assertTrue(spec.split)
    assertTrue(spec.secondarySizePx >= (336f * 2.25f).toInt())
  }
}

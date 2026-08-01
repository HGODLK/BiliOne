package dev.openbili.webdemo.video

import org.junit.Assert.assertEquals
import org.junit.Test

class FullscreenVideoBackgroundTest {
  @Test
  fun `zero brightness makes fullscreen letterbox fully black`() {
    assertEquals(
      1f,
      fullscreenVideoBackgroundShadeAlpha(
        embeddedShadeAlpha = .36f,
        fullscreenBrightness = 0f,
        fullscreenProgress = 1f,
      ),
      .0001f,
    )
  }

  @Test
  fun `maximum brightness preserves the existing cover shade`() {
    assertEquals(
      .36f,
      fullscreenVideoBackgroundShadeAlpha(
        embeddedShadeAlpha = .36f,
        fullscreenBrightness = 1f,
        fullscreenProgress = 1f,
      ),
      .0001f,
    )
  }

  @Test
  fun `fullscreen shade interpolates without changing embedded player`() {
    assertEquals(
      .74f,
      fullscreenVideoBackgroundShadeAlpha(
        embeddedShadeAlpha = .74f,
        fullscreenBrightness = 0f,
        fullscreenProgress = 0f,
      ),
      .0001f,
    )
    assertEquals(
      .87f,
      fullscreenVideoBackgroundShadeAlpha(
        embeddedShadeAlpha = .74f,
        fullscreenBrightness = 0f,
        fullscreenProgress = .5f,
      ),
      .0001f,
    )
  }
}

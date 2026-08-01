package dev.openbili.webdemo.live

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveHeroVisibilityTest {
  private val viewport = Rect(left = 0f, top = 100f, right = 1200f, bottom = 900f)

  @Test
  fun acceptsAPlayerFullyInsideTheLiveGridViewport() {
    assertTrue(
      isLiveHeroPreviewFullyVisible(
        previewBounds = Rect(left = 16f, top = 110f, right = 760f, bottom = 540f),
        viewportBounds = viewport,
      )
    )
  }

  @Test
  fun rejectsAPlayerClippedAboveOrBelowTheViewport() {
    assertFalse(
      isLiveHeroPreviewFullyVisible(
        previewBounds = Rect(left = 16f, top = 80f, right = 760f, bottom = 510f),
        viewportBounds = viewport,
      )
    )
    assertFalse(
      isLiveHeroPreviewFullyVisible(
        previewBounds = Rect(left = 16f, top = 500f, right = 760f, bottom = 920f),
        viewportBounds = viewport,
      )
    )
  }

  @Test
  fun rejectsMissingLayoutBounds() {
    assertFalse(isLiveHeroPreviewFullyVisible(Rect.Zero, viewport))
    assertFalse(isLiveHeroPreviewFullyVisible(Rect(0f, 0f, 100f, 100f), Rect.Zero))
  }
}

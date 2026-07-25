package dev.openbili.webdemo.video

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentImagePreviewGeometryTest {
  @Test
  fun openingUsesOneUniformScaleThatFitsInsideSourceBounds() {
    val source = Rect(0f, 0f, 180f, 120f)

    assertEquals(.2f, commentImageStartScale(source, 600f, 600f), .0001f)
  }

  @Test
  fun panIsLockedUntilScaledImageExceedsViewport() {
    assertEquals(0f, commentImagePanLimit(600f, 1000f, 1.5f), .0001f)
    assertEquals(100f, commentImagePanLimit(600f, 1000f, 2f), .0001f)
  }
}

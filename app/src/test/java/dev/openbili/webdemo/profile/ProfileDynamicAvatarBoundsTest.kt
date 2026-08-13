package dev.openbili.webdemo.profile

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileDynamicAvatarBoundsTest {
  @Test
  fun `transition uses visible image bounds instead of outer touch target`() {
    val bounds =
      dynamicAuthorAvatarTransitionBounds(
        outerBounds = Rect(left = 100f, top = 200f, right = 148f, bottom = 248f),
        contentInsetPx = 4f,
      )

    assertEquals(Rect(left = 104f, top = 204f, right = 144f, bottom = 244f), bounds)
    assertEquals(40f, bounds.width, 0f)
    assertEquals(40f, bounds.height, 0f)
  }

  @Test
  fun `invalid outer bounds do not create a transition anchor`() {
    assertEquals(Rect.Zero, dynamicAuthorAvatarTransitionBounds(Rect.Zero, contentInsetPx = 4f))
  }
}

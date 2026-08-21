package dev.openbili.webdemo.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentViewportWindowTest {
  @Test
  fun `keeps six comments on either side of the visible range`() {
    assertEquals(
      6..22,
      commentViewportWindow(totalCount = 30, firstVisibleIndex = 12, lastVisibleIndex = 16),
    )
  }

  @Test
  fun `clips the window at the list boundaries`() {
    assertEquals(
      0..9,
      commentViewportWindow(totalCount = 10, firstVisibleIndex = 0, lastVisibleIndex = 3),
    )
  }

  @Test
  fun `has no window without a visible loaded comment`() {
    assertNull(commentViewportWindow(totalCount = 10, firstVisibleIndex = 0, lastVisibleIndex = -1))
  }
}

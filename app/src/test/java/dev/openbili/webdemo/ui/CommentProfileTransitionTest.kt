package dev.openbili.webdemo.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentProfileTransitionTest {
  @Test
  fun returnTargetUsesLatestMeasuredCardBounds() {
    val enteredAt = Rect(900f, 500f, 1250f, 720f)
    val finalAfterChromeCollapse = Rect(900f, 380f, 1250f, 600f)

    assertEquals(
      finalAfterChromeCollapse,
      resolvedCommentProfileBounds(enteredAt, finalAfterChromeCollapse),
    )
  }

  @Test
  fun invalidLatestBoundsFallsBackToEntryBounds() {
    val enteredAt = Rect(900f, 500f, 1250f, 720f)

    assertEquals(enteredAt, resolvedCommentProfileBounds(enteredAt, Rect.Zero))
  }
}

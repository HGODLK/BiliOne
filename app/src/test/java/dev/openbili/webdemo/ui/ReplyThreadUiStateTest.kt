package dev.openbili.webdemo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyThreadUiStateTest {
  @Test
  fun retainedOpenThreadRestoresExpandedAtSameScrollPosition() {
    val current =
      ReplyThreadUiState(
        openedRootRpid = 42L,
        firstVisibleItemIndex = 8,
        firstVisibleItemScrollOffset = 96,
      )

    val retained = retainedReplyThreadUiState(current, openedRootRpid = 42L)

    assertEquals(42L, retained.openedRootRpid)
    assertEquals(8, retained.firstVisibleItemIndex)
    assertEquals(96, retained.firstVisibleItemScrollOffset)
    assertEquals(true, retained.restoreExpanded)
  }

  @Test
  fun staleReplyListCannotOverwriteCurrentThreadPosition() {
    val current = ReplyThreadUiState(openedRootRpid = 42L, firstVisibleItemIndex = 3)

    assertEquals(
      current,
      current.withScrollPosition(
        rootRpid = 99L,
        firstVisibleItemIndex = 12,
        firstVisibleItemScrollOffset = 120,
      ),
    )
  }
}

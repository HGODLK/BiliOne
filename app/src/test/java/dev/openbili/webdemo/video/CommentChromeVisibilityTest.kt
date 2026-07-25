package dev.openbili.webdemo.video

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentChromeVisibilityTest {
  @Test
  fun scrollingDownHidesActionsAndSortControls() {
    val result =
      commentChromeAfterScroll(
        currentVisibility = CommentChromeVisibility(),
        previous = CommentScrollPosition(index = 0, offset = 20),
        current = CommentScrollPosition(index = 0, offset = 80),
      )

    assertEquals(
      CommentChromeVisibility(showDockedActions = false, showSortControls = false),
      result,
    )
  }

  @Test
  fun scrollingUpRestoresOnlySortControls() {
    val result =
      commentChromeAfterScroll(
        currentVisibility =
          CommentChromeVisibility(showDockedActions = false, showSortControls = false),
        previous = CommentScrollPosition(index = 5, offset = 90),
        current = CommentScrollPosition(index = 4, offset = 40),
      )

    assertEquals(
      CommentChromeVisibility(showDockedActions = false, showSortControls = true),
      result,
    )
  }

  @Test
  fun reachingTopRestoresActionsAndSortControls() {
    val result =
      commentChromeAfterScroll(
        currentVisibility =
          CommentChromeVisibility(showDockedActions = false, showSortControls = true),
        previous = CommentScrollPosition(index = 0, offset = 12),
        current = CommentScrollPosition(index = 0, offset = 0),
      )

    assertEquals(CommentChromeVisibility(), result)
  }

  @Test
  fun stationaryPositionKeepsCurrentVisibility() {
    val hidden = CommentChromeVisibility(showDockedActions = false, showSortControls = false)
    val position = CommentScrollPosition(index = 2, offset = 30)

    assertEquals(hidden, commentChromeAfterScroll(hidden, position, position))
  }

  @Test
  fun upwardSwipeHidesChromeWhenCommentListCannotScroll() {
    assertEquals(
      CommentChromeVisibility(showDockedActions = false, showSortControls = false),
      commentChromeAfterViewportSwipe(
        currentVisibility = CommentChromeVisibility(),
        fingerDeltaY = -48f,
        listIsAtTop = true,
      ),
    )
  }

  @Test
  fun downwardSwipeRestoresChromeForShortCommentListAtTop() {
    assertEquals(
      CommentChromeVisibility(),
      commentChromeAfterViewportSwipe(
        currentVisibility =
          CommentChromeVisibility(showDockedActions = false, showSortControls = false),
        fingerDeltaY = 48f,
        listIsAtTop = true,
      ),
    )
  }

  @Test
  fun shortListTopReboundDoesNotRestoreChromeAfterUpwardSwipe() {
    val hidden = CommentChromeVisibility(showDockedActions = false, showSortControls = false)

    assertEquals(
      hidden,
      commentChromeAfterObservedScroll(
        currentVisibility = hidden,
        scrolling = false,
        direction = 0,
        isAtTop = true,
        keepHiddenAtTop = true,
      ),
    )
  }

  @Test
  fun topRestoresChromeWhenNoUpwardSwipeIsHeld() {
    assertEquals(
      CommentChromeVisibility(),
      commentChromeAfterObservedScroll(
        currentVisibility =
          CommentChromeVisibility(showDockedActions = false, showSortControls = false),
        scrolling = false,
        direction = 0,
        isAtTop = true,
        keepHiddenAtTop = false,
      ),
    )
  }

  @Test
  fun retainedStateKeepsCollapsedActionsAcrossParentRestore() {
    val state = CommentChromeState()
    val hidden = CommentChromeVisibility(showDockedActions = false, showSortControls = false)

    state.visibility.value = hidden
    state.keepHiddenAtTop.value = true
    state.floatingActionsExpanded.value = false

    assertEquals(hidden, state.visibility.value)
    assertEquals(true, state.keepHiddenAtTop.value)
    assertEquals(false, state.floatingActionsExpanded.value)
  }
}

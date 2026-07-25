package dev.openbili.webdemo.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentTimeOrderTest {
  @Test
  fun latestStartsAtFirstPageAndOnlyMovesForward() {
    assertEquals(1, commentTimeStartPage())
    assertEquals(2, commentTimeNextPage(currentPage = 1))
    assertTrue(commentTimeHasMore(currentPage = 2, totalCount = 41))
    assertFalse(commentTimeHasMore(currentPage = 3, totalCount = 41))
  }

  @Test
  fun latestOrderPutsNewestCommentFirst() {
    val comments = listOf(comment(rpid = 1, ctime = 100), comment(rpid = 2, ctime = 300))

    assertEquals(listOf(2L, 1L), orderCommentsByTime(comments).map { it.rpid })
  }

  @Test
  fun timeSortUsesLatestLabel() {
    assertEquals("最新", CommentSort.TIME.label)
  }

  private fun comment(rpid: Long, ctime: Long) =
    CommentItem(
      rpid = rpid,
      mid = 1,
      name = "tester",
      face = "",
      content = "",
      likeCount = 0,
      replyCount = 0,
      ctime = ctime,
    )
}

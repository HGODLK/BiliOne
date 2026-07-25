package dev.openbili.webdemo.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentDeletionEligibilityTest {
  @Test
  fun authorCanDeleteOwnComment() {
    assertTrue(
      commentCanBeDeletedBy(currentAccountMid = 12L, uploaderMid = 99L, commentAuthorMid = 12L)
    )
  }

  @Test
  fun uploaderCanDeleteAnyCommentOnOwnVideo() {
    assertTrue(
      commentCanBeDeletedBy(currentAccountMid = 99L, uploaderMid = 99L, commentAuthorMid = 12L)
    )
  }

  @Test
  fun unrelatedOrLoggedOutUserCannotDeleteComment() {
    assertFalse(
      commentCanBeDeletedBy(currentAccountMid = 7L, uploaderMid = 99L, commentAuthorMid = 12L)
    )
    assertFalse(
      commentCanBeDeletedBy(currentAccountMid = 0L, uploaderMid = 0L, commentAuthorMid = 0L)
    )
  }
}

package dev.openbili.webdemo.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentUpActionLabelTest {
  @Test
  fun createsAllUploaderInteractionLabels() {
    assertEquals("UP主回复了此条评论", commentUpActionLabel(upLiked = false, upReplied = true))
    assertEquals("UP主觉得很赞", commentUpActionLabel(upLiked = true, upReplied = false))
    assertEquals(
      "UP主觉得很赞并回复了此条评论",
      commentUpActionLabel(upLiked = true, upReplied = true),
    )
    assertNull(commentUpActionLabel(upLiked = false, upReplied = false))
  }
}

package dev.openbili.webdemo.my

import dev.openbili.webdemo.feed.FeedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchLaterViewModelTest {
  @Test
  fun directAidDoesNotNeedAnExtraVideoInfoRequest() {
    assertEquals(123L, resolveWatchLaterAid(item(id = "av123")))
    assertEquals(456L, resolveWatchLaterAid(item(id = "other", url = "https://bilibili.com/av456")))
  }

  @Test
  fun videoKeysCoverAidAndBvidForms() {
    val item = item(id = "BV1xx411c7mD")
    val keys = watchLaterVideoKeys(item, aid = 123L)

    assertTrue("BV1xx411c7mD" in keys)
    assertTrue("123" in keys)
    assertTrue("av123" in keys)
  }

  private fun item(id: String, url: String = "https://www.bilibili.com/video/$id") =
    FeedItem(
      id = id,
      title = "title",
      videoUrl = url,
      coverUrl = "",
      uploader = null,
      playCount = null,
      duration = null,
    )
}

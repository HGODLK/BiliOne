package dev.openbili.webdemo.video

import dev.openbili.webdemo.feed.FeedItem
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoShareLinkTest {
  @Test
  fun bvidGetsCanonicalPublicVideoUrl() {
    assertEquals(
      "https://www.bilibili.com/video/BV1xx411c7mD",
      buildVideoShareUrl(
        info = null,
        item = item(id = "BV1xx411c7mD", videoUrl = "https://example.com/video"),
      ),
    )
  }

  @Test
  fun offlinePlaybackUriIsNotCopiedAsPublicLink() {
    assertEquals(
      "",
      buildVideoShareUrl(
        info = null,
        item = item(id = "offline", videoUrl = "bilione-offline://media/abc"),
      ),
    )
  }

  private fun item(id: String, videoUrl: String) =
    FeedItem(
      id = id,
      title = "测试视频",
      videoUrl = videoUrl,
      coverUrl = "",
      uploader = null,
      playCount = null,
      duration = null,
    )
}

package dev.openbili.webdemo.video

import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.VideoCollection
import dev.openbili.webdemo.api.VideoCollectionEpisode
import dev.openbili.webdemo.api.VideoCollectionSection
import dev.openbili.webdemo.api.VideoPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSelectionTest {
  @Test
  fun `multi page collection exposes page and collection choices`() {
    val episode = episode("BV1", 101L)
    val collection =
      VideoCollection(
        id = 9L,
        title = "测试合集",
        episodes = listOf(episode),
        sections = listOf(VideoCollectionSection(90L, "第一章", listOf(episode))),
      )

    val groups =
      videoSelectionGroups(
        currentBvid = "BV1",
        pages = listOf(page(1, 101L), page(2, 102L)),
        collection = collection,
      )

    assertEquals(listOf("分P", "第一章"), groups.map(VideoSelectionGroup::title))
  }

  @Test
  fun `collection selection keeps cid and distinguishes pages sharing a bvid`() {
    val first = episode("BV1", 101L)
    val second = episode("BV1", 102L)
    val episodes = listOf(first, second)

    assertFalse(collectionEpisodeSelected(first, episodes, "BV1", 102L))
    assertTrue(collectionEpisodeSelected(second, episodes, "BV1", 102L))
    assertEquals(102L, feedItemFromCollectionEpisode(second).playbackPage?.cid)
  }

  @Test
  fun `playback recommendation info never exceeds cover height`() {
    assertEquals(67.5f, recommendationInfoHeight(120.dp).value, .01f)
    assertEquals(89f, recommendationInfoHeight(300.dp).value, .01f)
  }

  private fun page(number: Int, cid: Long) =
    VideoPage(number, cid, "P$number", 60L)

  private fun episode(bvid: String, cid: Long) =
    VideoCollectionEpisode(
      bvid = bvid,
      cid = cid,
      title = bvid,
      coverUrl = "",
      durationSeconds = 60L,
      uploaderName = "UP",
      uploaderFace = "",
      uploaderMid = 1L,
      playCount = 1L,
      danmakuCount = 1L,
      publishedAt = 1L,
    )
}

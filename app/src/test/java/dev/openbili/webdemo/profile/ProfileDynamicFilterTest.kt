package dev.openbili.webdemo.profile

import dev.openbili.webdemo.api.SpaceDynamicItem
import dev.openbili.webdemo.api.SpaceDynamicVideo
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileDynamicFilterTest {
  private val imageTextDynamic = dynamic(id = "text")
  private val videoDynamic =
    dynamic(
      id = "video",
      video = SpaceDynamicVideo(aid = 1L, bvid = "BV1", title = "视频"),
    )

  @Test
  fun `all filter preserves the server feed`() {
    assertEquals(
      listOf(imageTextDynamic, videoDynamic),
      filterProfileDynamics(listOf(imageTextDynamic, videoDynamic), ProfileDynamicFilter.ALL),
    )
  }

  @Test
  fun `video and image text filters are mutually exclusive`() {
    val feed = listOf(imageTextDynamic, videoDynamic)

    assertEquals(listOf(videoDynamic), filterProfileDynamics(feed, ProfileDynamicFilter.VIDEO))
    assertEquals(
      listOf(imageTextDynamic),
      filterProfileDynamics(feed, ProfileDynamicFilter.IMAGE_TEXT),
    )
  }

  private fun dynamic(
    id: String,
    video: SpaceDynamicVideo? = null,
  ) =
    SpaceDynamicItem(
      id = id,
      text = id,
      publishTimestamp = 1L,
      authorMid = 1L,
      authorName = "作者",
      authorFace = "",
      video = video,
      commentOid = 1L,
      commentType = 17,
    )
}

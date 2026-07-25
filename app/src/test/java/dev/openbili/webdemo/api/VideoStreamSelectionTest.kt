package dev.openbili.webdemo.api

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoStreamSelectionTest {
  @Test
  fun highestQualityIsFirstAndAvcWinsWithinSameQuality() {
    val selected =
      BiliApi.selectPreferredStreams(
        listOf(
          VideoStream(80, "1080P", "hevc", 12, "hev1"),
          VideoStream(120, "4K", "av1", 13, "av01"),
          VideoStream(120, "4K", "avc", 7, "avc1"),
          VideoStream(80, "1080P", "avc1080", 7, "avc1"),
        )
      )

    assertEquals(listOf(120, 80), selected.map { it.id })
    assertEquals("avc", selected.first().url)
    assertEquals("avc1080", selected.last().url)
  }

  @Test
  fun defaultSkipsUnsupportedSpecialTiersButKeepsHighestResolution() {
    val streams =
      listOf(
        VideoStream(126, "杜比视界", "dolby", 12, "hvc1"),
        VideoStream(125, "HDR", "hdr", 12, "hev1"),
        VideoStream(120, "4K", "4k", 7, "avc1"),
        VideoStream(80, "1080P", "1080", 7, "avc1"),
      )
    assertEquals(2, BiliApi.defaultStreamIndex(streams))
  }
}

package dev.openbili.webdemo

import dev.openbili.webdemo.api.VideoStream
import dev.openbili.webdemo.settings.PreferredResolutionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferredResolutionSelectionTest {
  private val streams =
    listOf(
      stream(127, "8K", 4320),
      stream(120, "4K", 2160),
      stream(116, "1080P60", 1080, 60f),
      stream(112, "1080P+", 1080),
      stream(80, "1080P", 1080),
      stream(64, "720P", 720),
      stream(32, "480P", 480),
    )

  @Test
  fun extremeUsesHighestDeviceSupportedResolution() {
    val selected =
      selectPreferredStreamIndex(streams, PreferredResolutionMode.EXTREME) {
        effectiveStreamHeight(it) <= 2160
      }

    assertEquals(120, streams[selected].id)
  }

  @Test
  fun namedModesChooseTheirRequestedTier() {
    assertEquals(
      116,
      streams[selectPreferredStreamIndex(streams, PreferredResolutionMode.ULTRA_HIGH)].id,
    )
    assertEquals(80, streams[selectPreferredStreamIndex(streams, PreferredResolutionMode.HIGH)].id)
    assertEquals(
      64,
      streams[selectPreferredStreamIndex(streams, PreferredResolutionMode.MEDIUM)].id,
    )
    assertEquals(32, streams[selectPreferredStreamIndex(streams, PreferredResolutionMode.LOW)].id)
  }

  private fun stream(id: Int, quality: String, height: Int, frameRate: Float = 30f) =
    VideoStream(
      id = id,
      quality = quality,
      url = quality,
      codecId = 7,
      codecs = "avc1",
      width = height * 16 / 9,
      height = height,
      frameRate = frameRate,
    )
}

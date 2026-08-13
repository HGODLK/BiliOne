package dev.openbili.webdemo

import dev.openbili.webdemo.api.VideoSubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSubtitleStateTest {
  @Test
  fun `subtitle tracks never leak into another media page`() {
    val track = track()
    val previous =
      PlayerSubtitleState(
        mediaId = "video-a",
        cid = 11L,
        tracks = listOf(track),
        selectedTrackId = track.id,
      )

    val visible = subtitleStateForMedia(previous, mediaId = "video-b", cid = 22L)

    assertEquals("video-b", visible.mediaId)
    assertTrue(visible.tracks.isEmpty())
    assertEquals(null, visible.selectedTrackId)
  }

  @Test
  fun `subtitle tracks never leak between parts of the same video`() {
    val previous =
      PlayerSubtitleState(
        mediaId = "video-a",
        cid = 11L,
        tracks = listOf(track()),
        selectedTrackId = "track-1",
      )

    val visible = subtitleStateForMedia(previous, mediaId = "video-a", cid = 22L)

    assertEquals(22L, visible.cid)
    assertTrue(visible.tracks.isEmpty())
  }

  @Test
  fun `subtitle tracks require resolved bvid aid and cid to all match`() {
    val previous =
      PlayerSubtitleState(
        mediaId = "reused-card-id",
        bvid = "BV-old",
        aid = 101L,
        cid = 11L,
        tracks = listOf(track()),
        selectedTrackId = "track-1",
      )

    val visible =
      subtitleStateForMedia(
        state = previous,
        mediaId = "reused-card-id",
        bvid = "BV-current",
        aid = 202L,
        cid = 11L,
      )

    assertEquals("BV-current", visible.bvid)
    assertEquals(202L, visible.aid)
    assertTrue(visible.tracks.isEmpty())
    assertEquals(null, visible.selectedTrackId)
  }

  private fun track() =
    VideoSubtitleTrack(
      id = "track-1",
      language = "zh-CN",
      languageLabel = "中文",
      sourceUrl = "https://example.com/subtitle.json",
      type = 0,
      aiType = 0,
      aiStatus = 0,
      aid = 101L,
      cid = 11L,
      bvid = "BV-old",
    )
}

package dev.openbili.webdemo

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import dev.openbili.webdemo.feed.FeedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaybackSessionLogicTest {
  @Test
  fun currentVideoMetadataUsesItsTitleUploaderAndCover() {
    val metadata =
      playbackMediaMetadata(
        FeedItem(
          id = "BV1TEST12345",
          title = "当前视频",
          videoUrl = "https://www.bilibili.com/video/BV1TEST12345",
          coverUrl = "//i0.hdslb.com/current-cover.jpg",
          uploader = "当前 UP 主",
          playCount = null,
          duration = null,
        )
      )

    assertEquals("当前视频", metadata.title)
    assertEquals("当前 UP 主", metadata.artist)
    assertEquals("https://i0.hdslb.com/current-cover.jpg", metadata.artworkUri.toString())
  }

  @Test
  fun backgroundAudioModeDisablesOnlyTheVideoTrackType() {
    val context = RuntimeEnvironment.getApplication() as Context
    val original = TrackSelectionParameters.Builder(context).build()

    val audioOnly = audioOnlyTrackSelectionParameters(original)

    assertTrue(C.TRACK_TYPE_VIDEO in audioOnly.disabledTrackTypes)
    assertFalse(C.TRACK_TYPE_AUDIO in audioOnly.disabledTrackTypes)
  }
}

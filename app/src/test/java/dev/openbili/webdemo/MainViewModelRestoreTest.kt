package dev.openbili.webdemo

import androidx.lifecycle.SavedStateHandle
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.ui.shouldReloadSelectedVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelRestoreTest {
  @Test
  fun savedVideoIsRestoredAndIdlePlayerRequiresReload() {
    val handle =
      SavedStateHandle(
        mapOf(
          "current_url" to "https://www.bilibili.com/video/BV1TEST",
          "video_item_id" to "BV1TEST",
          "video_item_title" to "测试视频",
          "video_item_url" to "https://www.bilibili.com/video/BV1TEST",
          "video_item_cover" to "https://i0.hdslb.com/test.jpg",
          "video_item_uploader" to "测试用户",
          "video_item_play_count" to "1.2万",
          "video_item_duration" to "3:20",
        )
      )

    val viewModel = MainViewModel(handle)
    val restored = viewModel.state.value.selectedVideo

    assertNotNull(restored)
    assertEquals("BV1TEST", restored?.id)
    assertTrue(shouldReloadSelectedVideo(restored, PlayerState.Idle))
    assertFalse(shouldReloadSelectedVideo(restored, PlayerState.Loading))
  }

  @Test
  fun absentSelectionNeverRequestsReload() {
    assertFalse(shouldReloadSelectedVideo(null, PlayerState.Idle))
    assertFalse(
      shouldReloadSelectedVideo(
        FeedItem(
          "id",
          "title",
          "https://www.bilibili.com/video/BV1",
          "https://i0.hdslb.com/x",
          null,
          null,
          null,
        ),
        PlayerState.Error("failed"),
      )
    )
  }
}

package dev.openbili.webdemo.api

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommentPicturePayloadTest {
  @Test
  fun `comment picture payload matches reply api fields`() {
    val upload =
      BiliPrivateMessageApi.PrivateImageUpload(
        url = "https://i0.hdslb.com/bfs/new_dyn/test.jpg",
        width = 1280,
        height = 720,
        mimeType = "image/jpeg",
        sizeKb = 96,
      )

    val picture = JSONArray(BiliPrivateMessageApi.commentPicturesPayload(upload)).getJSONObject(0)

    assertEquals(upload.url, picture.getString("img_src"))
    assertEquals(1280, picture.getInt("img_width"))
    assertEquals(720, picture.getInt("img_height"))
    assertEquals(96, picture.getInt("img_size"))
  }
}

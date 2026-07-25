package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivateImageResponseParsingTest {
  @Test
  fun `private limit notice array is converted to plain text`() {
    val raw = """[{"text":"对方主动回复或关注你前，最多发送1条消息","color_day":"#9499A0"}]"""

    assertEquals("对方主动回复或关注你前，最多发送1条消息", parsePrivateNoticeText(raw))
  }

  @Test
  fun `private limit notice nested in text message content is converted to plain text`() {
    val raw =
      """{"content":"[{\"text\":\"对方主动回复或关注你前，最多发送1条消息\",\"color_day\":\"#9499A0\",\"color_nig\":\"#9499A0\"}]"}"""

    assertEquals("对方主动回复或关注你前，最多发送1条消息", parsePrivateNoticeText(raw))
  }

  @Test
  fun acceptsDirectStringUploadUrl() {
    val value = parsePossiblyEncodedJsonValue("\"https://i0.hdslb.com/bfs/album/test.png\"")

    assertEquals("https://i0.hdslb.com/bfs/album/test.png", value)
  }

  @Test
  fun unwrapsDoubleEncodedObjectResponse() {
    val encoded = JSONObject.quote("""{"code":0,"data":{"image_url":"//i0.hdslb.com/test.png"}}""")
    val value = parsePossiblyEncodedJsonValue(encoded)

    assertTrue(value is JSONObject)
    assertEquals(0, (value as JSONObject).optInt("code"))
  }
}

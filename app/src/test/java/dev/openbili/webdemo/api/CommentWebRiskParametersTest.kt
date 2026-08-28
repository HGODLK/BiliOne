package dev.openbili.webdemo.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentWebRiskParametersTest {
  @Test
  fun `完整浏览器指纹参与 WBI 签名`() {
    val parameters =
      BiliCommentWebRiskParameters(
        environmentToken = "environment-token",
        imageList = "[]",
        imageFingerprint = "image",
        coverImageFingerprint = "cover",
        interactionFingerprint = "interaction",
      )

    assertEquals(
      mapOf(
        "dm_img_list" to "[]",
        "dm_img_str" to "image",
        "dm_cover_img_str" to "cover",
        "dm_img_inter" to "interaction",
      ),
      parameters.wbiParameters(),
    )
  }

  @Test
  fun `指纹不完整时明确关闭行为日志`() {
    val parameters =
      BiliCommentWebRiskParameters(
        environmentToken = "environment-token",
        imageList = "[]",
      )

    assertEquals(mapOf("dm_img_switch" to "0"), parameters.wbiParameters())
  }

  @Test
  fun `环境令牌在 WBI 查询参数之后独立追加`() {
    val url =
      BiliCommentApi.buildCommentAddUrl(
          signedParameters =
            mapOf("dm_img_switch" to "0", "wts" to "123", "w_rid" to "signature"),
          environmentToken = "token+/=",
        )
        .toHttpUrl()

    assertEquals("0", url.queryParameter("dm_img_switch"))
    assertEquals("123", url.queryParameter("wts"))
    assertEquals("signature", url.queryParameter("w_rid"))
    assertEquals("token+/=", url.queryParameter("b_wet"))
    assertTrue(url.queryParameterNames.contains("b_wet"))
    assertFalse(url.encodedQuery.orEmpty().contains("token+/="))
  }
}

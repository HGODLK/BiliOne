package dev.openbili.webdemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedUrlNormalizationTest {
  @Test
  fun acceptsMainDomainSubdomainAndRelativeVideoUrls() {
    assertEquals(
      "https://www.bilibili.com/video/BV_MAIN",
      UrlPolicy.normalizeVideoUrl("https://www.bilibili.com/video/BV_MAIN?spm=tracking#reply"),
    )
    assertEquals(
      "https://www.bilibili.com/video/BV_SUB",
      UrlPolicy.normalizeVideoUrl("https://m.bilibili.com/video/BV_SUB"),
    )
    assertEquals(
      "https://www.bilibili.com/video/BV_RELATIVE",
      UrlPolicy.normalizeVideoUrl("/video/BV_RELATIVE?from=feed"),
    )
  }

  @Test
  fun rewritesMobileSubdomainToWww() {
    assertEquals(
      "https://www.bilibili.com/video/BV_MOBILE",
      UrlPolicy.normalizeVideoUrl("https://m.bilibili.com/video/BV_MOBILE?p=1"),
    )
    // 已是 www 的 URL 保持不变。
    assertEquals(
      "https://www.bilibili.com/video/BV_WWW",
      UrlPolicy.normalizeVideoUrl("https://www.bilibili.com/video/BV_WWW"),
    )
    // 其他子域保持原样（它们并非移动端专属）。
    assertEquals(
      "https://api.bilibili.com/video/BV_API",
      UrlPolicy.normalizeVideoUrl("https://api.bilibili.com/video/BV_API"),
    )
  }

  @Test
  fun rejectsInsecureForgedNonVideoAndActiveSchemeUrls() {
    listOf(
        "http://www.bilibili.com/video/BV_HTTP",
        "https://bilibili.com.attacker.example/video/BV_FORGED",
        "https://www.bilibili.com/read/cv1",
        "https://b23.tv/BV_SHORT",
        "javascript:alert(1)",
        "file:///video/BV_FILE",
        "content://provider/video/BV_CONTENT",
        "data:text/html,video/BV_DATA",
      )
      .forEach { value -> assertNull(value, UrlPolicy.normalizeVideoUrl(value)) }
  }

  @Test
  fun imageNormalizationRequiresHttps() {
    assertEquals(
      "https://i0.hdslb.com/bfs/archive/cover.jpg",
      UrlPolicy.normalizeImageUrl("//i0.hdslb.com/bfs/archive/cover.jpg#preview"),
    )
    // HTTP 封面 URL 被接受（CDN 常以这种方式提供），但会升级为 HTTPS。
    assertEquals(
      "https://i0.hdslb.com/cover.jpg",
      UrlPolicy.normalizeImageUrl("http://i0.hdslb.com/cover.jpg"),
    )
    assertNull(UrlPolicy.normalizeImageUrl("data:image/png;base64,AA=="))
  }

  @Test
  fun imageNormalizationAllowsOnlyPrivateOfflineCoverFiles() {
    val cover =
      "file:///data/user/0/io.github.shuyunr.bilione/files/offline_media/metadata/video_1/cover.jpg"
    assertEquals(cover, UrlPolicy.normalizeImageUrl(cover))
    assertNull(
      UrlPolicy.normalizeImageUrl("file:///data/user/0/io.github.shuyunr.bilione/files/secret.jpg")
    )
    assertNull(
      UrlPolicy.normalizeImageUrl(
        "file:///data/user/0/io.github.shuyunr.bilione/files/offline_media/metadata/video_1/subtitle.vtt"
      )
    )
    assertNull(UrlPolicy.normalizeImageUrl("$cover?unexpected=query"))
  }
}

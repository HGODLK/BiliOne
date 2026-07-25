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
    // Already-www URLs stay unchanged.
    assertEquals(
      "https://www.bilibili.com/video/BV_WWW",
      UrlPolicy.normalizeVideoUrl("https://www.bilibili.com/video/BV_WWW"),
    )
    // Other subdomains are left alone (they are not mobile-specific).
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
    // HTTP cover URLs are accepted (CDN commonly serves them) but upgraded to HTTPS.
    assertEquals(
      "https://i0.hdslb.com/cover.jpg",
      UrlPolicy.normalizeImageUrl("http://i0.hdslb.com/cover.jpg"),
    )
    assertNull(UrlPolicy.normalizeImageUrl("data:image/png;base64,AA=="))
  }
}

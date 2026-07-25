package dev.openbili.webdemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrlPolicyTest {
  @Test
  fun rootDomainAllowed() =
    assertEquals(UrlDecision.ALLOW, UrlPolicy.decide("https://bilibili.com/"))

  @Test
  fun legitimateSubdomainAllowed() =
    assertEquals(UrlDecision.ALLOW, UrlPolicy.decide("https://www.bilibili.com/video/BV1xx"))

  @Test
  fun forgedDomainRejected() =
    assertEquals(UrlDecision.EXTERNAL, UrlPolicy.decide("https://bilibili.com.attacker.example/"))

  @Test
  fun httpRejectedFromWebView() =
    assertEquals(UrlDecision.EXTERNAL, UrlPolicy.decide("http://www.bilibili.com/"))

  @Test
  fun dangerousSchemesBlocked() {
    listOf("javascript:alert(1)", "file:///tmp/a", "content://x/y", "data:text/html,x").forEach {
      assertEquals(it, UrlDecision.BLOCK, UrlPolicy.decide(it))
    }
  }

  @Test
  fun externalLinkDetected() =
    assertEquals(UrlDecision.EXTERNAL, UrlPolicy.decide("https://example.com/"))

  @Test
  fun paymentLinkIsExternal() =
    assertEquals(
      UrlDecision.EXTERNAL,
      UrlPolicy.decide("https://pay.bilibili.com/payment/order"),
    )

  @Test
  fun routesDetected() {
    assertTrue(UrlPolicy.isVideoPage("https://www.bilibili.com/video/BV1xx"))
    assertTrue(UrlPolicy.isFeedPage("https://www.bilibili.com/"))
    assertFalse(UrlPolicy.isVideoPage("https://example.com/video/BV1xx"))
  }

  @Test
  fun sensitiveQueryIsRedacted() {
    val value = UrlPolicy.redactSensitiveQuery("https://www.bilibili.com/?token=secret&p=1")
    assertFalse(value.contains("secret"))
    assertTrue(value.contains("p=1"))
  }
}

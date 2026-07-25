package dev.openbili.webdemo.api

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliHttpClientTest {
  @Test
  fun cookieRoundTripPreservesAttributes() {
    val original =
      Cookie.Builder()
        .name("SESSDATA")
        .value("test-value")
        .domain("bilibili.com")
        .path("/x")
        .expiresAt(4_000_000_000_000L)
        .secure()
        .httpOnly()
        .build()
    val decoded = BiliHttpClient.decodeCookie(BiliHttpClient.encodeCookie(original))!!
    assertEquals(original.name, decoded.name)
    assertEquals(original.value, decoded.value)
    assertEquals(original.domain, decoded.domain)
    assertEquals(original.path, decoded.path)
    assertEquals(original.expiresAt, decoded.expiresAt)
    assertTrue(decoded.secure)
    assertTrue(decoded.httpOnly)
  }

  @Test
  fun cookiesAreFilteredByRequestAndExpiry() {
    val future = 4_000_000_000_000L
    val cookies =
      listOf(
        Cookie.Builder()
          .name("valid")
          .value("1")
          .domain("bilibili.com")
          .path("/x")
          .expiresAt(future)
          .secure()
          .build(),
        Cookie.Builder()
          .name("wrongPath")
          .value("1")
          .domain("bilibili.com")
          .path("/other")
          .expiresAt(future)
          .build(),
        Cookie.Builder()
          .name("wrongHost")
          .value("1")
          .domain("example.com")
          .path("/")
          .expiresAt(future)
          .build(),
        Cookie.Builder()
          .name("expired")
          .value("1")
          .domain("bilibili.com")
          .path("/")
          .expiresAt(1L)
          .build(),
      )
    val https =
      BiliHttpClient.matchingCookies(cookies, "https://api.bilibili.com/x/test".toHttpUrl(), 2L)
    assertEquals(listOf("valid"), https.map { it.name })
    val http =
      BiliHttpClient.matchingCookies(cookies, "http://api.bilibili.com/x/test".toHttpUrl(), 2L)
    assertFalse(http.any { it.name == "valid" })
  }

  @Test
  fun logoutClearsCredentialsButRetainsDeviceCookies() {
    val future = 4_000_000_000_000L
    fun cookie(name: String) =
      Cookie.Builder()
        .name(name)
        .value("value")
        .domain("bilibili.com")
        .path("/")
        .expiresAt(future)
        .build()

    BiliHttpClient.replaceCookies(listOf(cookie("SESSDATA"), cookie("bili_jct"), cookie("buvid3")))
    BiliHttpClient.clearLoginSession()

    assertEquals(null, BiliHttpClient.cookieValue("SESSDATA"))
    assertEquals(null, BiliHttpClient.cookieValue("bili_jct"))
    assertEquals("value", BiliHttpClient.cookieValue("buvid3"))
    BiliHttpClient.replaceCookies(emptyList())
  }
}

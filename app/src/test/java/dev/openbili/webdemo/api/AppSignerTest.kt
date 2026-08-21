package dev.openbili.webdemo.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSignerTest {
  @Test
  fun tvQrParametersMatchKnownLocalSignature() {
    val signed = AppSigner.locallySignedParams(mapOf("local_id" to "0", "ts" to "0"))

    assertEquals("4409e2ce8ffd12b8", signed["appkey"])
    assertEquals("e134154ed6add881d28fbdf68653cd9c", signed["sign"])
  }

  @Test
  fun backendResponseAddsAppKeyAndSignature() {
    val signed =
      AppSigner.parseSignedParams(
        params = mapOf("local_id" to "0", "ts" to "0"),
        responseBody = """{"code":0,"data":{"appkey":"test-app-key","sign":"test-signature"}}""",
      )

    assertEquals("test-app-key", signed["appkey"])
    assertEquals("test-signature", signed["sign"])
    assertEquals("sign", signed.keys.last())
  }

  @Test
  fun signatureIsAlwaysLastInQuery() {
    val query =
      AppSigner.encodeQuery(
        linkedMapOf(
          "appkey" to "test-app-key",
          "ts" to "0",
          "vmid" to "123",
          "sign" to "test-signature",
        )
      )

    assertEquals("sign", query.substringAfterLast("&").substringBefore("="))
  }

  @Test
  fun backendResponseWithoutSignatureIsRejected() {
    assertThrows(IllegalStateException::class.java) {
      AppSigner.parseSignedParams(
        params = mapOf("ts" to "0"),
        responseBody = """{"code":0,"data":{"appkey":"test-app-key"}}""",
      )
    }
  }

  @Test
  fun onlyValidHttpsEndpointEnablesBackendSigning() {
    assertTrue(AppSigner.isConfiguredEndpoint("https://signer.example.com/sign"))
    assertFalse(AppSigner.isConfiguredEndpoint(""))
    assertFalse(AppSigner.isConfiguredEndpoint("http://signer.example.com/sign"))
    assertFalse(AppSigner.isConfiguredEndpoint("https://"))
  }
}

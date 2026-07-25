package dev.openbili.webdemo.api

import dev.openbili.webdemo.BuildConfig
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Signs Bilibili APP-authenticated endpoint parameters.
 *
 * The fixed TV client credentials are protocol-level identifiers rather than account credentials;
 * the signed-in account is determined by the separately issued access token. When
 * `BILI_APP_SIGNER_ENDPOINT` is configured, signing is delegated to that HTTPS backend. Otherwise,
 * the TV-compatible signature is generated locally so QR authorization remains available.
 *
 * Request: `{"operation":"tv_qr_generate","params":{"local_id":"0","ts":"..."}}`
 *
 * Response: `{"code":0,"data":{"appkey":"...","sign":"..."}}`
 */
internal object AppSigner {
  private const val TV_APP_KEY = "4409e2ce8ffd12b8"
  private const val TV_APP_SECRET = "59b43e04ad6965f34319062b478f83dd"

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
  private val client by lazy {
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .callTimeout(20, TimeUnit.SECONDS)
      .build()
  }

  fun signedParams(
    operation: AppSigningOperation,
    params: Map<String, String>,
  ): LinkedHashMap<String, String> {
    val endpoint = BuildConfig.BILI_APP_SIGNER_ENDPOINT.trim()
    if (!isConfiguredEndpoint(endpoint)) return locallySignedParams(params)
    val payload =
      JSONObject()
        .put("operation", operation.wireName)
        .put("params", JSONObject().apply { params.forEach { (key, value) -> put(key, value) } })
    val request =
      Request.Builder()
        .url(endpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .header("Accept", "application/json")
        .build()
    val body =
      client.newCall(request).execute().use { response ->
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IllegalStateException("移动端授权服务请求失败：HTTP ${response.code}")
        }
        responseBody
      }
    return parseSignedParams(params, body)
  }

  fun query(operation: AppSigningOperation, params: Map<String, String>): String =
    encodeQuery(signedParams(operation, params))

  private fun encode(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

  internal fun parseSignedParams(
    params: Map<String, String>,
    responseBody: String,
  ): LinkedHashMap<String, String> {
    val root =
      runCatching { JSONObject(responseBody) }
        .getOrElse {
          throw IllegalStateException("移动端授权服务返回了无效数据")
        }
    if (root.optInt("code", -1) != 0) {
      throw IllegalStateException(root.optString("message").ifBlank { "移动端授权服务拒绝了签名请求" })
    }
    val data = root.optJSONObject("data") ?: throw IllegalStateException("移动端授权服务响应缺少 data")
    val appKey = data.optString("appkey").trim()
    val signature = data.optString("sign").trim()
    if (appKey.isBlank() || signature.isBlank()) {
      throw IllegalStateException("移动端授权服务响应缺少签名")
    }
    return linkedMapOf<String, String>().apply {
      (params + ("appkey" to appKey)).toSortedMap().forEach { (key, value) -> put(key, value) }
      put("sign", signature)
    }
  }

  internal fun encodeQuery(params: Map<String, String>): String =
    params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

  internal fun locallySignedParams(params: Map<String, String>): LinkedHashMap<String, String> {
    val complete = params + ("appkey" to TV_APP_KEY)
    val query =
      complete.toSortedMap().entries.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
      }
    val signature = md5(query + TV_APP_SECRET)
    return linkedMapOf<String, String>().apply {
      complete.toSortedMap().forEach { (key, value) -> put(key, value) }
      put("sign", signature)
    }
  }

  internal fun isConfiguredEndpoint(endpoint: String): Boolean =
    endpoint.trim().toHttpUrlOrNull()?.isHttps == true

  private fun md5(value: String): String =
    MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") {
      byte ->
      "%02x".format(byte.toInt() and 0xff)
    }
}

internal enum class AppSigningOperation(val wireName: String) {
  TV_QR_GENERATE("tv_qr_generate"),
  TV_QR_POLL("tv_qr_poll"),
  SPACE_PROFILE("space_profile"),
}

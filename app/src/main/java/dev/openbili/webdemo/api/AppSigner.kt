package dev.openbili.webdemo.api

/**
 * APP 端鉴权签名：承载 [AppSigner] 签名器与 [AppSigningOperation] 操作枚举。
 *
 * 负责为 B 站需要 APP 客户端签名（appkey + sign）的接口生成签名参数；扫码（二维码）授权系列
 * 接口依赖该签名，签名既可本地生成，也可委托给配置的 HTTPS 后端完成。
 */

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
 * B 站「APP 端鉴权参数」签名器。
 *
 * 固定的扫码（二维码）客户端凭证是公开文档化的协议标识（参见 bilibili-API-collect 的 APPKey
 * 页面），既不是秘密，也不是账号凭证；具体登录的账号由另行签发的 access token 决定。当
 * `BILI_APP_SIGNER_ENDPOINT` 已配置时，签名会委托给该 HTTPS 后端；否则在本地生成兼容签名，
 * 以保证扫码授权依然可用。
 *
 * 响应格式：`{"code":0,"data":{"appkey":"...","sign":"..."}}`
 */
internal object AppSigner {
  // 扫码（二维码）授权使用的公开客户端 key/secret（协议公开标识，非机密）。
  private const val QR_APP_KEY = "4409e2ce8ffd12b8"
  private const val QR_APP_SECRET = "59b43e04ad6965f34319062b478f83dd"

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
  private val client by lazy {
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .callTimeout(20, TimeUnit.SECONDS)
      .build()
  }

  /**
   * 为指定的 APP 端操作生成带签名的请求参数。
   *
   * 若配置了签名后端 [BuildConfig.BILI_APP_SIGNER_ENDPOINT]，则把参数以 JSON 形式 POST 给后端
   * 并解析其返回的签名；否则回退到本地签名 [locallySignedParams]。
   *
   * @param operation 目标操作，决定后端签名时使用的 wireName。
   * @param params 待签名的原始参数（键值对）。
   * @return 附加了 `appkey` 与 `sign` 的完整参数表。
   */
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

  /**
   * 生成签名后的 URL 查询串。
   *
   * 先调用 [signedParams] 得到完整参数，再做 URL 编码并按 `k=v&...` 拼接，便于直接拼接到请求
   * URL 之后。
   *
   * @param operation 目标操作。
   * @param params 待签名的原始参数。
   * @return 形如 `a=1&b=2&sign=...` 的查询字符串。
   */
  fun query(operation: AppSigningOperation, params: Map<String, String>): String =
    encodeQuery(signedParams(operation, params))

  private fun encode(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

  /**
   * 解析签名后端返回的 JSON，取出 `appkey` 与 `sign`。
   *
   * @param params 原始请求参数，用于与返回的 appkey 合并。
   * @param responseBody 后端的原始响应体。
   * @return 合并了 appkey、并按参数名排序后追加 sign 的参数表。
   * @throws IllegalStateException 响应非法、code 非 0 或缺少签名字段时抛出。
   */
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

  /**
   * 把参数表编码为 URL 查询串（对键值分别做百分号编码并以 `&` 连接）。
   *
   * @param params 待编码的参数表。
   * @return 形如 `k1=v1&k2=v2` 的查询串。
   */
  internal fun encodeQuery(params: Map<String, String>): String =
    params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

  /**
   * 在本地用固定的扫码 key/secret 做 MD5 签名。
   *
   * 先并入 appkey，按参数名排序拼接查询串，再对「查询串 + secret」求 MD5 作为 sign。
   *
   * @param params 待签名的原始参数。
   * @return 附加了 `appkey` 与 `sign` 的参数表。
   */
  internal fun locallySignedParams(params: Map<String, String>): LinkedHashMap<String, String> {
    val complete = params + ("appkey" to QR_APP_KEY)
    val query =
      complete.toSortedMap().entries.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
      }
    val signature = md5(query + QR_APP_SECRET)
    return linkedMapOf<String, String>().apply {
      complete.toSortedMap().forEach { (key, value) -> put(key, value) }
      put("sign", signature)
    }
  }

  /**
   * 判断给定的签名端点是否为有效的 HTTPS 地址。
   *
   * @param endpoint 待校验的端点字符串。
   * @return 仅当是合法且为 HTTPS 的 URL 时返回 true。
   */
  internal fun isConfiguredEndpoint(endpoint: String): Boolean =
    endpoint.trim().toHttpUrlOrNull()?.isHttps == true

  private fun md5(value: String): String =
    MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") {
      byte ->
      "%02x".format(byte.toInt() and 0xff)
    }
}

/**
 * APP 端签名支持的操作类型。
 *
 * @property wireName 后端签名服务识别该操作所使用的协议名。
 */
internal enum class AppSigningOperation(val wireName: String) {
  /** 生成扫码（二维码）授权码。 */
  CONTROL_QR_GENERATE("tv_qr_generate"),
  /** 轮询扫码授权结果。 */
  CONTROL_QR_POLL("tv_qr_poll"),
  /** 获取个人空间资料。 */
  SPACE_PROFILE("space_profile"),
}

package dev.openbili.webdemo.api

/**
 * B 站 Web API 公共工具：承载 [BiliApiCommon]（WBI 签名与 CSRF 令牌）、[BlockingRequest] 阻塞
 * 请求封装，以及 JSON/私信文本解析等公共函数。
 */

import android.os.SystemClock
import android.util.Log
import androidx.core.text.HtmlCompat
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener


/**
 * B 站接口公共签名与鉴权工具。
 *
 * 缓存 WBI 密钥并据此为请求参数签名；同时提供 CSRF 令牌（bili_jct）读取与签名查询串拼接。需要
 * WBI 签名的接口统一通过这里生成参数，保证密钥只请求一次并在有效期内复用。
 */
object BiliApiCommon {
  /** 日志标签。 */
  internal const val TAG = "BiliApi"
  // WBI 密钥缓存：首次请求后缓存，避免每次签名都请求 nav 接口。
  @Volatile private var wbiKeys: WbiKeys? = null

  /**
   * 为参数表做 WBI 签名（自动并入风控 gaia token）。
   *
   * WBI 密钥（img_key/sub_key）首次使用时向 nav 接口请求并缓存，之后在有效期内复用；若已取得
   * 风控通行令牌 gaia_vtoken，会一并并入参数再签名。
   *
   * @param params 待签名的原始参数。
   * @return 追加了 `wts` 与 `w_rid` 的参数表。
   */
  internal fun signedParams(params: Map<String, String>): Map<String, String> {
    val now = System.currentTimeMillis()
    var keys = wbiKeys?.takeIf { it.isValid }
    if (keys == null) {
      val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/nav")
      val json = JSONObject(resp.body?.string().orEmpty())
      resp.close()
      val wbi =
        json.optJSONObject("data")?.optJSONObject("wbi_img")
          ?: throw IllegalStateException("无法取得 WBI 密钥")
      fun fileKey(url: String) = url.substringAfterLast('/').substringBefore('.')
      keys = WbiKeys(fileKey(wbi.getString("img_url")), fileKey(wbi.getString("sub_url")), now)
      wbiKeys = keys
    }
    val riskAwareParams =
      BiliHttpClient.gaiaToken?.takeIf(String::isNotBlank)?.let { params + ("gaia_vtoken" to it) }
        ?: params
    return WbiSigner.sign(riskAwareParams, WbiSigner.getMixinKey(keys.imgKey, keys.subKey))
  }

  /**
   * 读取 CSRF 令牌（bili_jct）。
   *
   * 该令牌是 B 站写操作接口（点赞、评论、关注等）必需的防跨站请求令牌。
   *
   * @return CSRF 令牌字符串。
   * @throws IllegalStateException 未登录、取不到令牌时抛出。
   */
  internal fun requireCsrf(): String =
    BiliHttpClient.cookieValue("bili_jct") ?: throw IllegalStateException("请先登录")

  /**
   * 生成 WBI 签名后的查询串。
   *
   * @param params 待签名的原始参数。
   * @return 签名后按参数名排序并编码的查询串。
   */
  internal fun signedQuery(params: Map<String, String>): String =
    signedParams(params).toSortedMap().entries.joinToString("&") { (k, v) ->
      "${URLEncoder.encode(k, "UTF-8")}" + "=${URLEncoder.encode(v, "UTF-8")}"
    }
}
/**
 * 阻塞式请求结果封装。
 *
 * 用 [CountDownLatch] 让发起方可以同步等待异步回调完成：complete 存值、completeExceptionally
 * 存异常，await 阻塞直到二者之一被调用。
 */
internal class BlockingRequest<T> {
  private val completion = CountDownLatch(1)
  private var value: T? = null
  private var failure: Throwable? = null

  /**
   * 阻塞等待结果并返回。
   *
   * @return 成功结果。
   * @throws Throwable 请求失败时抛出 [completeExceptionally] 存入的异常。
   */
  fun await(): T {
    completion.await()
    failure?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return value as T
  }

  /** 存入成功结果并唤醒等待方。 */
  fun complete(value: T) {
    this.value = value
    completion.countDown()
  }

  /** 存入失败异常并唤醒等待方。 */
  fun completeExceptionally(error: Throwable) {
    failure = error
    completion.countDown()
  }
}

/**
 * 解析可能被额外 JSON 编码过一次的值。
 *
 * 部分网关路径会把正常的响应整体再 JSON 编码一次，这里尝试最多再解两层嵌套字符串，返回最终的
 * JSON 值（对象/数组/字符串或原始类型）。
 *
 * @param raw 原始响应文本。
 * @return 解析出的值，可能为 JSONObject、JSONArray、字符串或原始类型。
 */
internal fun parsePossiblyEncodedJsonValue(raw: String): Any? {
  var value: Any? = JSONTokener(raw.trim()).nextValue()
  // 部分网关路径会把正常的响应整体再 JSON 编码一次。
  repeat(2) {
    val nested = value as? String ?: return@repeat
    val trimmed = nested.trim()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return@repeat
    value = runCatching { JSONTokener(trimmed).nextValue() }.getOrDefault(value)
  }
  return value
}

/**
 * 从私信通知原文中提取纯文本内容。
 *
 * 兼容多种嵌套结构（JSON 数组/对象/字符串），逐层解包直到取到 text 字段并拼接；无法解析时
 * 返回 null。
 *
 * @param raw 通知原文。
 * @return 提取出的文本，没有文本时返回 null。
 */
internal fun parsePrivateNoticeText(raw: String): String? {
  return runCatching {
      var value: Any? = parsePossiblyEncodedJsonValue(raw)
      repeat(3) {
        when (val current = value) {
          is JSONArray ->
            return@runCatching buildList {
                for (index in 0 until current.length()) {
                  current
                    .optJSONObject(index)
                    ?.optString("text")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
                }
              }
              .joinToString("")
              .ifBlank { null }
          is JSONObject -> {
            val nested = current.opt("content")
            if (nested == null || nested == JSONObject.NULL) return@runCatching null
            value =
              if (nested is String) {
                runCatching { parsePossiblyEncodedJsonValue(nested) }.getOrDefault(nested)
              } else {
                nested
              }
          }
          is String -> {
            val trimmed = current.trim()
            if (!trimmed.startsWith("[")) return@runCatching null
            value = JSONTokener(trimmed).nextValue()
          }
          else -> return@runCatching null
        }
      }
      null
    }
    .getOrNull()
}
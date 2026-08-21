package dev.openbili.webdemo.api

/**
 * WBI 签名：承载 [WbiSigner] 助手对象。
 *
 * 提供 B 站 WBI 签名所需的 mixin key 计算与参数签名能力；WBI 密钥（img_key/sub_key）的获取与
 * 缓存由调用方负责，本对象只承担置换与签名计算。
 */

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * B 站 WBI 签名助手。
 *
 * WBI 密钥由调用方从 nav 接口获取并缓存 12 小时；本对象负责用固定的置换表计算 mixin key，
 * 并对参数表生成 `w_rid` 签名。mixin key 的置换表自 2023 年以来保持稳定。
 */
object WbiSigner {

  // WBI mixin key 的固定置换表（自 2023 年以来未变）。
  private val MIXIN_KEY_ENC_TAB =
    intArrayOf(
      46,
      47,
      18,
      2,
      53,
      8,
      23,
      32,
      15,
      50,
      10,
      31,
      58,
      3,
      45,
      35,
      27,
      43,
      5,
      49,
      33,
      9,
      42,
      19,
      29,
      28,
      14,
      39,
      12,
      38,
      41,
      13,
      37,
      48,
      7,
      16,
      24,
      55,
      40,
      61,
      26,
      17,
      0,
      1,
      60,
      51,
      30,
      4,
      22,
      25,
      54,
      21,
      56,
      59,
      6,
      63,
      57,
      62,
      11,
      36,
      20,
      34,
      44,
      52,
    )

  /**
   * 根据 img_key 与 sub_key 计算 WBI 的 mixin key。
   *
   * 把两个 key 拼接后按固定置换表 [MIXIN_KEY_ENC_TAB] 重排字符，并截取前 32 位作为结果。
   *
   * @param imgKey nav 接口返回的 img_key。
   * @param subKey nav 接口返回的 sub_key。
   * @return 用于后续签名的 32 位 mixin key。
   */
  fun getMixinKey(imgKey: String, subKey: String): String {
    val raw = imgKey + subKey
    return MIXIN_KEY_ENC_TAB.map { raw[it] }.joinToString("").take(32)
  }

  /**
   * 对参数表做 WBI 签名。
   *
   * 追加当前秒级时间戳 `wts`，按参数名排序后拼接成查询串，再对「查询串 + mixinKey」求 MD5，
   * 摘要作为 `w_rid` 一并写入返回结果。
   *
   * @param params 待签名的原始参数。
   * @param mixinKey 由 [getMixinKey] 计算得到的 mixin key。
   * @return 追加了 `wts` 与 `w_rid` 的参数表。
   */
  fun sign(params: Map<String, String>, mixinKey: String): Map<String, String> {
    val withTs = params.toMutableMap()
    withTs["wts"] = (System.currentTimeMillis() / 1000).toString()
    val sorted = withTs.toSortedMap()
    val query =
      sorted.entries.joinToString("&") { (k, v) ->
        "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
      }
    val signStr = query + mixinKey
    val wrid = md5(signStr)
    withTs["w_rid"] = wrid
    return withTs
  }

  /** 计算字符串的 MD5 十六进制小写摘要。 */
  private fun md5(input: String): String {
    val digest = MessageDigest.getInstance("MD5")
    val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }
}

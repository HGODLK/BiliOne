package dev.openbili.webdemo.api

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Bilibili WBI signature helper.
 *
 * Keys are fetched from the nav endpoint and cached for 12 hours. The mixin permutation table is
 * stable (unchanged since 2023).
 */
object WbiSigner {

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

  fun getMixinKey(imgKey: String, subKey: String): String {
    val raw = imgKey + subKey
    return MIXIN_KEY_ENC_TAB.map { raw[it] }.joinToString("").take(32)
  }

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

  private fun md5(input: String): String {
    val digest = MessageDigest.getInstance("MD5")
    val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }
}

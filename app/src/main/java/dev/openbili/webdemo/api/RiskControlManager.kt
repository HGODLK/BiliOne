package dev.openbili.webdemo.api

/**
 * 风控验证：承载 [RiskChallenge]、[GeetestChallenge] 两个数据模型与 [RiskControlManager]。
 *
 * 负责把 B 站接口返回的 -352 风控信号桥接为可交互的极验（Geetest）验证码，并在验证通过后
 * 保存风控通行令牌（gaia token），供后续请求附带。
 */

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Response
import org.json.JSONObject

/**
 * 风控挑战信息。
 *
 * @property voucher 风控凭证 v_voucher，用于向风控网关换取极验挑战。
 */
data class RiskChallenge(val voucher: String)

/**
 * 极验验证挑战参数。
 *
 * @property voucher 风控凭证 v_voucher。
 * @property gt 极验验证码的 gt 标识。
 * @property challenge 极验验证码的 challenge 标识。
 * @property token 极验验证码的 token。
 */
data class GeetestChallenge(
  val voucher: String,
  val gt: String,
  val challenge: String,
  val token: String,
)

/**
 * 把 B 站的 -352 风控响应桥接为用户可完成的极验（Geetest）验证挑战。
 */
object RiskControlManager {
  private const val TAG = "BiliRiskControl"
  private val lock = Any()
  private val _challenge = MutableStateFlow<RiskChallenge?>(null)
  /** 当前待处理的风控挑战状态流，供 UI 观察并弹出验证码。 */
  val challenge: StateFlow<RiskChallenge?> = _challenge.asStateFlow()

  /**
   * 检查接口响应是否触发了风控（code 为 -352 或响应头携带 v_voucher）。
   *
   * 命中后记录风控挑战供 UI 弹出极验验证码；已有未处理挑战时不重复覆盖。
   *
   * @param response OkHttp 响应对象。
   */
  fun inspectResponse(response: Response) {
    val headerVoucher = response.header("x-bili-gaia-vvoucher").orEmpty()
    val contentType = response.body?.contentType()?.subtype.orEmpty()
    if (headerVoucher.isBlank() && !contentType.contains("json", ignoreCase = true)) return
    val preview = runCatching { response.peekBody(16 * 1024L).string() }.getOrDefault("")
    val json = runCatching { JSONObject(preview) }.getOrNull()
    val code = json?.optInt("code")
    if (code != -352 && headerVoucher.isBlank()) return
    val voucher =
      json?.optJSONObject("data")?.optString("v_voucher").orEmpty().ifBlank { headerVoucher }
    if (voucher.isBlank()) return
    synchronized(lock) {
      if (_challenge.value == null) {
        Log.w(TAG, "risk challenge requested")
        _challenge.value = RiskChallenge(voucher)
      }
    }
  }

  /**
   * 向风控网关注册极验验证挑战。
   *
   * 用风控凭证换取极验所需的 gt/challenge/token。
   *
   * @param challenge 待注册的风控挑战。
   * @return 极验验证挑战参数。
   * @throws IllegalStateException 注册失败或无可用验证码时抛出。
   */
  fun register(challenge: RiskChallenge): GeetestChallenge {
    val response =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/gaia-vgate/v1/register",
        mapOf(
          "v_voucher" to challenge.voucher,
          "csrf" to BiliHttpClient.cookieValue("bili_jct").orEmpty(),
        ),
      )
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) {
      throw IllegalStateException(json.optString("message").ifBlank { "验证码准备失败" })
    }
    val data = json.optJSONObject("data") ?: throw IllegalStateException("验证码数据为空")
    val geetest = data.optJSONObject("geetest") ?: data
    val gt = geetest.optString("gt").ifBlank { data.optString("gt") }
    val challengeValue = geetest.optString("challenge").ifBlank { data.optString("challenge") }
    val token = data.optString("token").ifBlank { geetest.optString("token") }
    if (gt.isBlank() || challengeValue.isBlank() || token.isBlank()) {
      throw IllegalStateException("当前风控没有可交互验证码，请稍后再试")
    }
    return GeetestChallenge(challenge.voucher, gt, challengeValue, token)
  }

  /**
   * 提交极验验证结果并换取风控通行令牌。
   *
   * 验证通过后把 grisk_id 保存为 gaia token，并清除当前风控挑战。
   *
   * @param config 极验挑战参数。
   * @param validate 极验返回的 validate 字段。
   * @param seccode 极验返回的 seccode 字段。
   * @throws IllegalStateException 验证未通过时抛出。
   */
  fun validate(config: GeetestChallenge, validate: String, seccode: String) {
    val response =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/gaia-vgate/v1/validate",
        mapOf(
          "challenge" to config.challenge,
          "token" to config.token,
          "validate" to validate,
          "seccode" to seccode,
          "csrf" to BiliHttpClient.cookieValue("bili_jct").orEmpty(),
        ),
      )
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    val data = json.optJSONObject("data")
    val gaiaToken = data?.optString("grisk_id").orEmpty()
    if (json.optInt("code") != 0 || data?.optInt("is_valid") != 1 || gaiaToken.isBlank()) {
      throw IllegalStateException(json.optString("message").ifBlank { "验证没有通过，请再试一次" })
    }
    BiliHttpClient.setGaiaToken(gaiaToken)
    synchronized(lock) { _challenge.value = null }
  }

  /**
   * 清除指定的风控挑战（用户主动关闭验证时调用）。
   *
   * @param challenge 要清除的风控挑战。
   */
  fun dismiss(challenge: RiskChallenge) {
    synchronized(lock) {
      if (_challenge.value?.voucher == challenge.voucher) _challenge.value = null
    }
  }
}

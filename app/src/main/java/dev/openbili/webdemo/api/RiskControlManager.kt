package dev.openbili.webdemo.api

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Response
import org.json.JSONObject

data class RiskChallenge(val voucher: String)

data class GeetestChallenge(
  val voucher: String,
  val gt: String,
  val challenge: String,
  val token: String,
)

/** Bridges Bilibili's -352 risk response to a user-completable Geetest challenge. */
object RiskControlManager {
  private const val TAG = "BiliRiskControl"
  private val lock = Any()
  private val _challenge = MutableStateFlow<RiskChallenge?>(null)
  val challenge: StateFlow<RiskChallenge?> = _challenge.asStateFlow()

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

  fun dismiss(challenge: RiskChallenge) {
    synchronized(lock) {
      if (_challenge.value?.voucher == challenge.voucher) _challenge.value = null
    }
  }
}

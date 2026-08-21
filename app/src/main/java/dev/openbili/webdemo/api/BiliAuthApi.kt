package dev.openbili.webdemo.api

/**
 * 登录与账号信息接口。
 *
 * 覆盖两种登录链路：网页端扫码登录（[generateQrCode]/[pollQrCode]）与 TV/控制器端
 * APP 签名扫码登录（[generateAppQrCode]/[pollAppQrCode]），以及登录后的账号信息
 * 读取 [getUserInfo]（会员状态由 nav 接口的 vip 字段推导）。
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
 * 登录与账号信息 API 集合。
 */
object BiliAuthApi {

  // ── 登录 ─────────────────────────────────────────────────────────────────

  /** 生成网页端扫码登录二维码，返回二维码链接与轮询 key。 */
  fun generateQrCode(): QrCodeInfo? {
    val resp =
      BiliHttpClient.get("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
    val body = resp.body?.string().orEmpty()
    resp.close()
    val json = JSONObject(body)
    Log.d(BiliApiCommon.TAG, "qrcode generate: code=${json.optInt("code")} msg=${json.optString("message")}")
    if (json.optInt("code") != 0) return null
    val data = json.getJSONObject("data")
    return QrCodeInfo(data.getString("url"), data.getString("qrcode_key"))
  }

  /** 轮询网页端扫码结果，未扫码/已扫码/已确认分别对应不同 code。 */
  fun pollQrCode(qrcodeKey: String): QrStatus {
    val resp =
      BiliHttpClient.get(
        "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$qrcodeKey"
      )
    val body = resp.body?.string().orEmpty()
    resp.close()
    Log.d(BiliApiCommon.TAG, "qrcode poll: bodyLen=${body.length}")
    val json = JSONObject(body)
    val data = json.optJSONObject("data")
    val code = data?.optInt("code", 86038) ?: 86038
    val msg = data?.optString("message", "") ?: ""
    Log.d(BiliApiCommon.TAG, "qrcode poll parsed: code=$code msg=$msg")
    return QrStatus(code, msg)
  }

  /** 生成 APP（TV）端签名扫码登录二维码。 */
  fun generateAppQrCode(): QrCodeInfo? {
    val params =
      AppSigner.signedParams(
        AppSigningOperation.CONTROL_QR_GENERATE,
        mapOf(
          "local_id" to "0",
          "ts" to (System.currentTimeMillis() / 1_000L).toString(),
        ),
      )
    val response =
      BiliHttpClient.postForm(
        "https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code",
        params,
      )
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) return null
    val data = json.optJSONObject("data") ?: return null
    val url = data.optString("url")
    val authCode = data.optString("auth_code")
    return if (url.isBlank() || authCode.isBlank()) null else QrCodeInfo(url, authCode)
  }

  /** 轮询 APP 端扫码结果；确认后返回 mid 与 access/refresh token。 */
  fun pollAppQrCode(authCode: String): AppQrStatus {
    val params =
      AppSigner.signedParams(
        AppSigningOperation.CONTROL_QR_POLL,
        mapOf(
          "auth_code" to authCode,
          "local_id" to "0",
          "ts" to (System.currentTimeMillis() / 1_000L).toString(),
        ),
      )
    val response =
      BiliHttpClient.postForm(
        "https://passport.bilibili.com/x/passport-tv-login/qrcode/poll",
        params,
      )
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    val data = json.optJSONObject("data")
    return AppQrStatus(
      code = json.optInt("code", 86038),
      message = json.optString("message"),
      mid = data?.optLong("mid", 0L) ?: 0L,
      accessToken = data?.optString("access_token").orEmpty(),
      refreshToken = data?.optString("refresh_token").orEmpty(),
      expiresInSeconds = data?.optLong("expires_in", 0L) ?: 0L,
    )
  }

  /** 读取当前登录账号信息（含大会员状态）。 */
  fun getUserInfo(): UserInfo {
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/nav")
    val body = resp.body?.string().orEmpty()
    resp.close()
    val json = JSONObject(body)
    val data = json.optJSONObject("data")
    val isLogin = data?.optBoolean("isLogin", false) == true
    return if (isLogin) {
      val vip = data.optJSONObject("vip")
      // 视 nav 网关不同，嵌套的会员状态字段名可能是 `status` 或 `vipStatus`；
      // 优先顶层状态，同时兼容两种嵌套写法。
      val vipStatus =
        data.optInt(
          "vipStatus",
          vip?.optInt("status", vip.optInt("vipStatus", 0)) ?: 0,
        )
      // `2` 同样是有效的开通状态（设备上当前登录账号返回 vipStatus=2）。
      // 只有会员类型字段同时存在时，非零状态才算有效开通，避免把仅剩类型字段的
      // 过期记录误判为有效订阅。
      val vipType = data.optInt("vipType", vip?.optInt("type", 0) ?: 0)
      val vipActive = vipStatus != 0 && vipType > 0
      UserInfo(
        mid = data.getLong("mid"),
        name = data.optString("uname", ""),
        face = data.optString("face", ""),
        isLogin = true,
        vipActive = vipActive,
      )
    } else {
      UserInfo(0, "", "", false)
    }
  }
}
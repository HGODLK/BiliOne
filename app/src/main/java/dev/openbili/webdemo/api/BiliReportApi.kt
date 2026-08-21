package dev.openbili.webdemo.api

/**
 * 播放上报接口。
 *
 * 覆盖普通视频的播放开始/进度上报、番剧（PGC）心跳上报与云端观看进度读取。
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
 * 播放上报 API 集合。
 */
object BiliReportApi {

  /** 上报普通视频开始播放（点击上报，用于历史记录）。 */
  fun reportPlaybackStart(aid: Long, cid: Long) {
    val csrf = BiliApiCommon.requireCsrf()
    val now = System.currentTimeMillis() / 1000
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/report/click/h5",
        mapOf(
          "aid" to aid.toString(),
          "cid" to cid.toString(),
          "part" to "1",
          "mid" to (BiliHttpClient.cookieValue("DedeUserID") ?: "0"),
          "did" to (BiliHttpClient.cookieValue("sid") ?: ""),
          "ftime" to now.toString(),
          "stime" to now.toString(),
          "jsonp" to "jsonp",
          "csrf" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0)
      Log.w(BiliApiCommon.TAG, "playback start report failed: ${json.optString("message")}")
    else Log.d(BiliApiCommon.TAG, "playback start reported: aid=$aid")
  }

  /** 上报普通视频观看进度（秒）；失败抛异常由调用方决定是否重试。 */
  fun reportPlayback(
    aid: Long,
    cid: Long,
    playedSeconds: Long,
  ) {
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v2/history/report",
        mapOf(
          "aid" to aid.toString(),
          "cid" to cid.toString(),
          "csrf" to csrf,
          "progress" to playedSeconds.coerceAtLeast(0L).toString(),
          "platform" to "android",
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) {
      val message = json.optString("message", "观看进度上报失败")
      Log.w(BiliApiCommon.TAG, "playback progress report failed: $message")
      throw IllegalStateException(message)
    }
    Log.d(BiliApiCommon.TAG, "playback progress reported: aid=$aid cid=$cid progress=$playedSeconds")
  }

  /**
   * 上报番剧（PGC）播放心跳。通用的历史接口虽然接受同一对 aid/cid，但不会推进季度
   * 的"正在追"进度；网页播放器用 season/episode ID 加 type=4 与媒体 sub_type 区分
   * 剧集播放。
   */
  fun reportBangumiPlayback(
    aid: Long,
    cid: Long,
    episodeId: Long,
    seasonId: Long,
    playedSeconds: Long,
    durationSeconds: Long,
    startTimestamp: Long,
    subType: Int,
    playType: Int = 0,
  ) {
    if (aid <= 0L || cid <= 0L || episodeId <= 0L || seasonId <= 0L || subType <= 0) return
    val csrf = BiliApiCommon.requireCsrf()
    val nowSeconds = System.currentTimeMillis() / 1000L
    val sessionSeconds =
      if (startTimestamp > 0L) (nowSeconds - startTimestamp).coerceAtLeast(0L) else 0L
    val safePlayedSeconds = playedSeconds.coerceAtLeast(0L)
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/click-interface/web/heartbeat",
        mapOf(
          "aid" to aid.toString(),
          "cid" to cid.toString(),
          "epid" to episodeId.toString(),
          "sid" to seasonId.toString(),
          "mid" to (BiliHttpClient.cookieValue("DedeUserID") ?: "0"),
          "played_time" to safePlayedSeconds.toString(),
          "realtime" to sessionSeconds.toString(),
          "real_played_time" to sessionSeconds.toString(),
          "start_ts" to startTimestamp.coerceAtLeast(0L).toString(),
          "type" to "4",
          "sub_type" to subType.toString(),
          "dt" to "2",
          "play_type" to playType.coerceIn(0, 4).toString(),
          "video_duration" to durationSeconds.coerceAtLeast(0L).toString(),
          "csrf" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) {
      val message = json.optString("message", "番剧播放心跳上报失败")
      Log.w(
        BiliApiCommon.TAG,
        "bangumi heartbeat failed: aid=$aid cid=$cid epid=$episodeId sid=$seasonId subtype=$subType code=${json.optInt("code")} message=$message",
      )
      throw IllegalStateException(message)
    }
    Log.d(
      BiliApiCommon.TAG,
      "bangumi heartbeat reported: aid=$aid cid=$cid epid=$episodeId sid=$seasonId subtype=$subType progress=$safePlayedSeconds",
    )
  }

  /** 读取云端观看进度（毫秒）；last_play_cid 与当前 cid 不一致时视为无进度。 */
  fun getPlaybackProgressMs(aid: Long, cid: Long): Long {
    if (aid <= 0L || cid <= 0L) return 0L
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/player/v2?aid=$aid&cid=$cid")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) return 0L
    val data = json.optJSONObject("data") ?: return 0L
    val lastCid = data.optLong("last_play_cid", 0L)
    if (lastCid > 0L && lastCid != cid) return 0L
    return data.optLong("last_play_time", 0L).coerceAtLeast(0L)
  }
}
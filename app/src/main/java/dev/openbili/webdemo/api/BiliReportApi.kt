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
import kotlin.math.roundToLong
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

  /**
   * 读取播放器页信息：云端观看进度与 B 站“分段章节”都来自同一个响应。
   *
   * 接口的 last_play_time 单位是毫秒，view_points 的 from/to 单位是秒。无章节或接口字段
   * 缺失时返回空列表，不能让章节解析失败影响视频播放。
   */
  fun getPlayerPageInfo(
    aid: Long,
    cid: Long,
    durationMs: Long = 0L,
    episodeId: Long = 0L,
  ): PlayerPageInfo {
    if (aid <= 0L || cid <= 0L) return PlayerPageInfo()
    val episodeQuery = episodeId.takeIf { it > 0L }?.let { "&ep_id=$it" }.orEmpty()
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/player/v2?aid=$aid&cid=$cid$episodeQuery"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) return PlayerPageInfo()
    return parsePlayerPageInfo(json.optJSONObject("data"), cid, durationMs)
  }

  /** 读取云端观看进度（毫秒）；last_play_cid 与当前 cid 不一致时视为无进度。 */
  fun getPlaybackProgressMs(aid: Long, cid: Long): Long =
    getPlayerPageInfo(aid, cid).lastPlayTimeMs
}

data class PlayerPageInfo(
  val lastPlayTimeMs: Long = 0L,
  val chapters: List<VideoChapter> = emptyList(),
)

/** 供单元测试复用的播放器页 JSON 解析；网络层只负责请求与错误兜底。 */
internal fun parsePlayerPageInfo(
  data: JSONObject?,
  expectedCid: Long,
  durationMs: Long = 0L,
): PlayerPageInfo {
  if (data == null) return PlayerPageInfo()
  val lastCid = data.optLong("last_play_cid", 0L)
  val lastPlayTimeMs =
    if (lastCid > 0L && lastCid != expectedCid) 0L
    else data.optLong("last_play_time", 0L).coerceAtLeast(0L)
  val safeDurationMs = durationMs.coerceAtLeast(0L)
  val chapters =
    data.optJSONArray("view_points")
      ?.let { points ->
        buildList {
          for (index in 0 until points.length()) {
            val point = points.optJSONObject(index) ?: continue
            // B 站播放器页的显示名称以 content 为准；content 为空的 point 不进入
            // 章节标题/选择菜单，也不回退到非接口约定的 title 字段。
            val title = point.optString("content").trim()
            val fromMs = point.optDouble("from", Double.NaN).secondsToMsOrNull()
            val rawToMs = point.optDouble("to", Double.NaN).secondsToMsOrNull()
            if (title.isBlank() || fromMs == null || rawToMs == null || rawToMs <= fromMs) continue
            val startMs = fromMs.coerceAtLeast(0L)
            if (safeDurationMs > 0L && startMs >= safeDurationMs) continue
            val endMs =
              if (safeDurationMs > 0L) rawToMs.coerceIn(startMs + 1L, safeDurationMs)
              else rawToMs
            if (endMs <= startMs) continue
            add(
              VideoChapter(
                startMs = startMs,
                endMs = endMs,
                title = title,
                imageUrl =
                  point.optString("imgUrl").trim().ifBlank {
                    point.optString("img_url").trim().ifBlank { null }
                  },
              )
            )
          }
        }
      }
      .orEmpty()
      .sortedWith(compareBy<VideoChapter> { it.startMs }.thenBy { it.endMs })
      .fold(mutableListOf<VideoChapter>()) { unique, chapter ->
        if (unique.lastOrNull()?.startMs != chapter.startMs) unique += chapter
        unique
      }
  return PlayerPageInfo(lastPlayTimeMs = lastPlayTimeMs, chapters = chapters)
}

private fun Double.secondsToMsOrNull(): Long? {
  if (!isFinite() || this < 0.0) return null
  return (this * 1000.0).roundToLong().takeIf { it >= 0L }
}

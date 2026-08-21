package dev.openbili.webdemo.api

/**
 * 首页信息流接口。
 *
 * 覆盖热门（无需登录）的各类榜单：综合热门、每周必看、入站必刷、排行榜与全站音乐
 * 榜，以及需要账号的个性化推荐流 [getPersonalizedFeed]。
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
 * 首页信息流 API 集合。
 */
object BiliFeedApi {

  // ── 信息流（热门 — 无需登录） ─────────────────────────────────────────────

  /** 分页拉取综合热门。 */
  fun getPopularFeed(page: Int): FeedResponse {
    Log.d(BiliApiCommon.TAG, "popular feed request: page=$page")
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/popular?pn=$page&ps=20")
    val body = resp.body?.string().orEmpty()
    resp.close()
    Log.d(BiliApiCommon.TAG, "popular feed response: page=$page code=... bodyLen=${body.length}")
    val json = JSONObject(body)
    if (json.optInt("code") != 0) {
      Log.w(BiliApiCommon.TAG, "popular error: ${json.optString("message")}")
      return FeedResponse(emptyList())
    }
    val list = json.getJSONObject("data").optJSONArray("list")
    val cards = mutableListOf<FeedCard>()
    if (list != null) {
      for (i in 0 until list.length()) {
        try {
          cards.add(FeedCard.fromJson(list.getJSONObject(i)))
        } catch (e: Exception) {
          Log.w(BiliApiCommon.TAG, "skip card: ${e.message}")
        }
      }
    }
    return FeedResponse(cards)
  }

  /** 每周必看全部期数，按期号倒序。 */
  fun getPopularWeeklyPeriods(): List<PopularPeriod> {
    val json =
      getPublicJson(
        "https://api.bilibili.com/x/web-interface/popular/series/list",
        "每周必看期数",
      )
    val list = json.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
    return buildList {
        for (index in 0 until list.length()) {
          val item = list.optJSONObject(index) ?: continue
          val number = item.optInt("number")
          if (number <= 0) continue
          add(
            PopularPeriod(
              id = number,
              label = "第${number}期",
              subject = item.optString("subject"),
            )
          )
        }
      }
      .sortedByDescending(PopularPeriod::id)
  }

  /** 指定期号的每周必看。 */
  fun getPopularWeekly(number: Int): FeedResponse =
    getStandardPopularList(
      "https://api.bilibili.com/x/web-interface/popular/series/one?number=$number",
      "每周必看",
    )

  /** 入站必刷。 */
  fun getPopularPrecious(): FeedResponse =
    getStandardPopularList(
      "https://api.bilibili.com/x/web-interface/popular/precious",
      "入站必刷",
    )

  /** 指定分区的排行榜。 */
  fun getPopularRanking(rid: Int): FeedResponse =
    getStandardPopularList(
      "https://api.bilibili.com/x/web-interface/ranking/v2?rid=$rid&type=all",
      "排行榜",
    )

  /** 全站音乐榜全部期数，按发布时间与 ID 倒序。 */
  fun getPopularMusicPeriods(): List<PopularPeriod> {
    val json =
      getPublicJson(
        "https://api.bilibili.com/x/copyright-music-publicity/toplist/all_period" +
          "?list_type=1&position_id=8",
        "全站音乐榜期数",
      )
    val years = json.optJSONObject("data")?.optJSONObject("list") ?: return emptyList()
    return buildList {
        val keys = years.keys()
        while (keys.hasNext()) {
          val entries = years.optJSONArray(keys.next()) ?: continue
          for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: continue
            val id = item.optInt("ID")
            val issue = item.optInt("priod")
            if (id <= 0 || issue <= 0) continue
            add(
              PopularPeriod(
                id = id,
                label = "第${issue}期",
                publishedAt = item.optLong("publish_time"),
              )
            )
          }
        }
      }
      .sortedWith(compareByDescending<PopularPeriod> { it.publishedAt }.thenByDescending { it.id })
  }

  /** 指定期数的全站音乐榜。 */
  fun getPopularMusic(listId: Int): FeedResponse {
    val json =
      getPublicJson(
        "https://api.bilibili.com/x/copyright-music-publicity/toplist/music_list" +
          "?list_type=1&position_id=8&list_id=$listId",
        "全站音乐榜",
      )
    val list = json.optJSONObject("data")?.optJSONArray("list") ?: return FeedResponse(emptyList())
    val cards = buildList {
      for (index in 0 until list.length()) {
        val item = list.optJSONObject(index) ?: continue
        val aid = item.optLong("creation_aid")
        val bvid = item.optString("creation_bvid")
        if (aid <= 0L || bvid.isBlank()) continue
        add(
          FeedCard(
            aid = aid,
            bvid = bvid,
            cid = item.optLong("creation_first_cid"),
            title = item.optString("creation_title").ifBlank { item.optString("music_title") },
            coverUrl = item.optString("creation_cover").ifBlank { item.optString("mv_cover") },
            uploaderName = item.optString("creation_nickname"),
            uploaderFace = "",
            uploaderMid = item.optLong("creation_up"),
            playCount = item.optLong("creation_play"),
            danmakuCount = 0,
            durationSeconds = item.optLong("creation_duration"),
            pubdate = 0,
            description =
              item.optString("recommendation").ifBlank {
                buildList {
                    item.optString("music_title").takeIf(String::isNotBlank)?.let(::add)
                    item.optString("singer").takeIf(String::isNotBlank)?.let(::add)
                  }
                  .joinToString(" · ")
              },
          )
        )
      }
    }
    return FeedResponse(cards)
  }

  private fun getStandardPopularList(url: String, label: String): FeedResponse {
    val json = getPublicJson(url, label)
    val list = json.optJSONObject("data")?.optJSONArray("list")
    val cards = mutableListOf<FeedCard>()
    if (list != null) {
      for (index in 0 until list.length()) {
        runCatching { FeedCard.fromJson(list.getJSONObject(index)) }
          .onSuccess { cards.add(it) }
          .onFailure { Log.w(BiliApiCommon.TAG, "$label skip card: ${it.message}") }
      }
    }
    return FeedResponse(cards)
  }

  private fun getPublicJson(url: String, label: String): JSONObject {
    val response = BiliHttpClient.get(url)
    val body = response.body?.string().orEmpty()
    response.close()
    val json = JSONObject(body)
    if (json.optInt("code") != 0) {
      throw IllegalStateException("$label：${json.optString("message", "加载失败")}")
    }
    return json
  }

  /**
   * 带账号的首页个性化推荐。fresh_index 变化时 B 站才认为是新的一次刷新，而不是
   * 重放首批缓存。
   */
  fun getPersonalizedFeed(freshIndex: Long, pageSize: Int = 20): FeedResponse {
    val params =
      linkedMapOf(
        "fresh_idx" to freshIndex.toString(),
        "fresh_idx_1h" to freshIndex.toString(),
        "fresh_type" to "3",
        "homepage_ver" to "1",
        "ps" to pageSize.coerceIn(1, 50).toString(),
        "last_y_num" to "5",
        "feed_version" to "V8",
        "brush" to "1",
        "web_location" to "1430650",
      )
    val signed = BiliApiCommon.signedParams(params)
    val url =
      "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd?" +
        signed.toSortedMap().entries.joinToString("&") { (key, value) ->
          "${URLEncoder.encode(key, "UTF-8")}" + "=${URLEncoder.encode(value, "UTF-8")}"
        }
    val resp = BiliHttpClient.get(url)
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) {
      throw IllegalStateException("推荐接口：${json.optString("message")}")
    }
    val array = json.optJSONObject("data")?.optJSONArray("item")
    val cards = mutableListOf<FeedCard>()
    if (array != null) {
      for (i in 0 until array.length()) {
        runCatching { FeedCard.fromJson(array.getJSONObject(i)) }
          .onSuccess { if (it.bvid.isNotBlank()) cards += it }
      }
    }
    Log.d(BiliApiCommon.TAG, "personalized feed: fresh=$freshIndex cards=${cards.size}")
    return FeedResponse(cards)
  }
}

package dev.openbili.webdemo.api

/**
 * 观看历史与稍后再看接口。
 *
 * 覆盖按月回溯的多类型历史分页（全部/视频/直播/专栏）、稍后再看的增删查，以及历史
 * 记录里"账号消息用户样式"的取色缓存。
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
 * 观看历史与稍后再看 API 集合。
 */
object BiliHistoryApi {
  internal const val DEFAULT_HISTORY_LOOKBACK_MONTHS = 6L
  internal const val MAX_HISTORY_MONTH_REQUESTS = 120
  internal const val MAX_HISTORY_DAY_REQUESTS = 512
  internal const val MAX_CONSECUTIVE_EMPTY_HISTORY_RESPONSES = 5
  internal const val HISTORY_REQUEST_INTERVAL_MS = 50L
  private val accountMessageUserStyleCache = ConcurrentHashMap<Long, AccountMessageUserStyle>()

  /** 按游标拉取历史记录；type 为空拉全部类型。 */
  fun getHistory(
    cursor: HistoryCursor = HistoryCursor(),
    type: String = "",
    /** “正在追”需要兼容少数没有 pgc 业务字段的旧历史行。普通历史必须保持严格分类。 */
    includeArchivePgcHints: Boolean = false,
  ): AccountHistoryResponse {
    val resp = BiliHttpClient.get(historyCursorUrl(cursor, type))
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseHistoryResponse(json, includeArchivePgcHints)
  }

  /**
   * 按网页端语义搜索账号历史。搜索接口独立于游标接口，会从服务端的完整历史中检索，
   * 因此不能用当前已经加载的列表做本地过滤来代替。
   */
  fun searchHistory(
    keyword: String,
    page: Int = 1,
    business: String = "all",
  ): AccountHistorySearchResponse {
    val normalizedKeyword = keyword.trim()
    require(normalizedKeyword.isNotEmpty()) { "历史搜索关键词不能为空" }
    require(page > 0) { "历史搜索页码无效" }
    val response = BiliHttpClient.get(historySearchUrl(normalizedKeyword, page, business))
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseHistorySearchResponse(json)
  }

  /** 网页端历史搜索请求 URL。 */
  internal fun historySearchUrl(keyword: String, page: Int, business: String): String {
    val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
    val encodedBusiness = URLEncoder.encode(business.ifBlank { "all" }, "UTF-8")
    return "https://api.bilibili.com/x/web-interface/history/search" +
      "?pn=$page&keyword=$encodedKeyword&business=$encodedBusiness" +
      "&add_time_start=0&add_time_end=0&arc_max_duration=0&arc_min_duration=0" +
      "&device_type=0&web_location=333.1391"
  }

  /** 拉取稍后再看列表。 */
  fun getWatchLater(): List<FeedCard> {
    val response = BiliHttpClient.get("https://api.bilibili.com/x/v2/history/toview/web")
    val body = response.body?.string().orEmpty()
    response.close()
    val json = JSONObject(body)
    val code = json.optInt("code")
    if (code != 0) {
      throw IllegalStateException(json.optString("message", "稍后再看加载失败"))
    }
    return parseWatchLaterResponse(json)
  }

  /** 添加稍后再看（对已存在的记录做幂等处理）。 */
  fun addToWatchLater(aid: Long) {
    require(aid > 0L) { "视频参数无效" }
    postWatchLaterAction(
      url = "https://api.bilibili.com/x/v2/history/toview/add",
      aid = aid,
      idempotentCode = 90001,
      fallback = "添加到稍后再看失败",
    )
  }

  /** 从稍后再看移除。 */
  fun removeFromWatchLater(aid: Long) {
    require(aid > 0L) { "视频参数无效" }
    postWatchLaterAction(
      url = "https://api.bilibili.com/x/v2/history/toview/del",
      aid = aid,
      idempotentCode = 90002,
      fallback = "移出稍后再看失败",
    )
  }

  internal fun parseWatchLaterResponse(json: JSONObject): List<FeedCard> {
    val list = json.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
    return buildList {
      for (index in 0 until list.length()) {
        val item = list.optJSONObject(index) ?: continue
        runCatching { FeedCard.fromJson(item) }.getOrNull()?.takeIf { it.aid > 0L }?.let(::add)
      }
    }
  }

  internal fun parseHistorySearchResponse(json: JSONObject): AccountHistorySearchResponse {
    val data = json.optJSONObject("data") ?: JSONObject()
    val parsed = parseHistoryResponse(json)
    val page = data.optJSONObject("page") ?: JSONObject()
    return AccountHistorySearchResponse(
      items = parsed.items,
      page = page.optInt("pn", 1).coerceAtLeast(1),
      total = page.optInt("total").coerceAtLeast(0),
      hasMore = data.optBoolean("has_more", parsed.hasMore),
    )
  }

  private fun postWatchLaterAction(
    url: String,
    aid: Long,
    idempotentCode: Int,
    fallback: String,
  ) {
    val csrf = BiliApiCommon.requireCsrf()
    val response =
      BiliHttpClient.postForm(
        url = url,
        fields =
          mapOf(
            "aid" to aid.toString(),
            "csrf" to csrf,
            "csrf_token" to csrf,
          ),
      )
    val body = response.body?.string().orEmpty()
    response.close()
    val json =
      runCatching { JSONObject(body) }
        .getOrElse { throw IllegalStateException("稍后再看服务暂时不可用，请稍后重试") }
    val code = json.optInt("code")
    if (code != 0 && code != idempotentCode) {
      throw IllegalStateException(json.optString("message", fallback))
    }
  }

  /**
   * 回复/@ 信息流只返回紧凑的作者对象，省略等级和 VIP 状态。改从轻量用户卡端点解析
   * 这些展示数据，而不是渲染成 Lv0。此函数读取账号消息页用户的样式（头像取色等），
   * 并带进程内缓存。
   */
  fun getAccountMessageUserStyle(mid: Long): AccountMessageUserStyle {
    if (mid <= 0L) return AccountMessageUserStyle()
    accountMessageUserStyleCache[mid]?.let {
      return it
    }
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/card?mid=$mid")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data = json.optJSONObject("data") ?: JSONObject()
    val card = data.optJSONObject("card") ?: data
    val vip = card.optJSONObject("vip") ?: JSONObject()
    val vipStatus = vip.optInt("status", card.optInt("vip_status", card.optInt("vipStatus", 0)))
    val vipType = vip.optInt("type", card.optInt("vip_type", card.optInt("vipType", 0)))
    val style =
      AccountMessageUserStyle(
        level =
          card.optJSONObject("level_info")?.optInt("current_level", card.optInt("level"))
            ?: card.optInt("level"),
        vipActive = vipStatus == 1 || (vipStatus != 0 && vipType > 0),
        vipLabel =
          vip
            .optJSONObject("label")
            ?.optString("text")
            .orEmpty()
            .ifBlank { card.optJSONObject("vip_label")?.optString("text").orEmpty() }
            .ifBlank { card.optString("vip_label") }
            .ifBlank { if (vipStatus == 1) "大会员" else "" },
      )
    accountMessageUserStyleCache.putIfAbsent(mid, style)
    return accountMessageUserStyleCache[mid] ?: style
  }

  /** 按历史类型拼接按月回溯的历史请求 URL。 */
  internal fun historyCursorUrl(cursor: HistoryCursor, type: String): String {
    val encodedBusiness = URLEncoder.encode(cursor.business, "UTF-8")
    val encodedType = URLEncoder.encode(type.ifBlank { "all" }, "UTF-8")
    return "https://api.bilibili.com/x/web-interface/history/cursor" +
      "?max=${cursor.max}&view_at=${cursor.viewAt}&business=$encodedBusiness" +
      "&ps=20&type=$encodedType&web_location=333.1391"
  }

  internal fun parseHistoryResponse(
    json: JSONObject,
    includeArchivePgcHints: Boolean = false,
  ): AccountHistoryResponse {
    val data = json.optJSONObject("data") ?: JSONObject()
    val array = data.optJSONArray("list") ?: JSONArray()
    val items = buildList {
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val history = item.optJSONObject("history") ?: JSONObject()
        // 游标接口与搜索接口的字段层级并不完全一致：搜索结果有时把稿件身份放在
        // 外层，不能只读取 history，否则整页结果会被静默丢弃。
        val uri = item.optString("uri")
        val business =
          history
            .optString("business")
            .ifBlank { item.optString("business") }
            .ifBlank {
              when {
                uri.contains("/read/cv", ignoreCase = true) ||
                  uri.contains("/opus/", ignoreCase = true) -> "article-list"
                uri.contains("live.bilibili.com", ignoreCase = true) -> "live"
                item.optString("bvid").isNotBlank() || item.optLong("aid") > 0L -> "archive"
                else -> ""
              }
            }
        val oid =
          history.optLong("oid").takeIf { it > 0L }
            ?: item.optLong("oid").takeIf { it > 0L }
            ?: item.optLong("aid")
        val bvid = history.optString("bvid").ifBlank { item.optString("bvid") }
        val viewAt =
          item.optLong("view_at").takeIf { it > 0L }
            ?: history.optLong("view_at").takeIf { it > 0L }
            ?: item.optLong("last_view_at")
        val cid = history.optLong("cid").takeIf { it > 0L } ?: item.optLong("cid")
        val duration =
          item.optLong("duration").takeIf { it > 0L } ?: history.optLong("duration")
        val pgcTypeHints =
          listOf(
            item.optString("badge"),
            item.optString("tag_name"),
            item.optString("type_name"),
            item.optString("season_type_name"),
            history.optString("badge"),
            history.optString("tag_name"),
            item.optString("title"),
          )
        val pgcHistory =
          business == "pgc" ||
            business == "bangumi" ||
            history.optLong("epid") > 0L ||
            history.optLong("ep_id") > 0L ||
            uri.contains("/bangumi/play/", ignoreCase = true) ||
            (includeArchivePgcHints &&
              business == "archive" &&
              pgcTypeHints.any(BiliBangumiApi::isTypedDramaHistoryHint))
        when {
          business == "article" ||
            business == "article-list" ||
            uri.contains("/read/cv", ignoreCase = true) ||
            uri.contains("/opus/", ignoreCase = true) -> {
            if (oid <= 0L) continue
            val cover =
              item.optString("cover").ifBlank {
                item.optJSONArray("covers")?.optString(0).orEmpty()
              }
            add(
              AccountHistoryItem.Article(
                ArticleItem(
                  id = oid,
                  title = item.optString("title"),
                  summary =
                    item.optString("show_title").ifBlank {
                      item.optString("new_desc", item.optString("badge"))
                    },
                  coverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(cover).orEmpty(),
                  authorName = item.optString("author_name"),
                  authorFace =
                    dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("author_face"))
                      .orEmpty(),
                  authorMid = item.optLong("author_mid"),
                  publishedAt = viewAt,
                  sourceUrl =
                    uri.ifBlank {
                      // 游标接口的专栏行通常只有 oid 和封面，补齐网页端可打开的地址。
                      "https://www.bilibili.com/read/cv$oid"
                    },
                )
              )
            )
          }
          business == "live" || business == "live_room" -> {
            if (oid <= 0L) continue
            val cover =
              item.optString("cover").ifBlank {
                item.optJSONArray("covers")?.optString(0).orEmpty()
              }
            val statusText =
              listOf(item.optString("badge"), item.optString("show_title")).joinToString(" ")
            add(
              AccountHistoryItem.Live(
                roomId = oid,
                title = item.optString("title").ifBlank { "直播间 $oid" },
                anchorUid = item.optLong("author_mid"),
                anchorName = item.optString("author_name").ifBlank { "主播" },
                anchorFace =
                  dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("author_face")),
                coverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(cover),
                keyframeUrl =
                  dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(
                    history.optString("keyframe").ifBlank { item.optString("keyframe") }
                  ),
                areaName =
                  item
                    .optString("tag_name")
                    .ifBlank { item.optString("show_title") }
                    .takeIf(String::isNotBlank),
                parentAreaName = item.optString("parent_area_name").takeIf(String::isNotBlank),
                liveStatus =
                  if (item.has("live_status")) item.optInt("live_status")
                  else if (statusText.contains("直播中")) 1 else 0,
                viewAt = viewAt,
              )
            )
          }
          pgcHistory -> {
            val episodeId =
              history.optLong("epid").takeIf { it > 0L }
                ?: history.optLong("ep_id").takeIf { it > 0L }
                ?: Regex("/ep(\\d+)", RegexOption.IGNORE_CASE)
                  .find(item.optString("uri"))
                  ?.groupValues
                  ?.getOrNull(1)
                  ?.toLongOrNull()
                ?: 0L
            val seasonId =
              history.optLong("season_id").takeIf { it > 0L }
                ?: item.optLong("season_id").takeIf { it > 0L }
                ?: Regex("/ss(\\d+)", RegexOption.IGNORE_CASE)
                  .find(item.optString("uri"))
                  ?.groupValues
                  ?.getOrNull(1)
                  ?.toLongOrNull()
                ?: 0L
            // 有些游标行只暴露底层的归档 aid/bvid。保留这些行并在目标页加载时解析其
            // PGC 重定向；在这里丢弃它们曾让有效的番剧历史完全消失。
            if (episodeId <= 0L && seasonId <= 0L && bvid.isBlank() && oid <= 0L) continue
            val mediaLabel =
              historyPgcMediaLabel(
                *pgcTypeHints.toTypedArray(),
                item.optString("title"),
              )
            val seasonType =
              BiliBangumiApi.resolvePgcSeasonType(
                primary = history,
                secondary = item,
                mediaLabel = mediaLabel,
              )
            val cover =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("cover")).orEmpty()
            val historyId =
              "history:pgc:" +
                when {
                  episodeId > 0L -> "ep$episodeId"
                  bvid.isNotBlank() -> bvid
                  oid > 0L -> "av$oid"
                  else -> "ss$seasonId"
                }
            val kind =
              if (mediaLabel in setOf("番剧", "港澳台番剧", "国创")) {
                SpaceContentKind.BANGUMI
              } else {
                SpaceContentKind.DRAMA
              }
            val progressSeconds =
              item.optNonNegativeLong("progress") ?: history.optNonNegativeLong("progress")
            val durationSeconds =
              item.optNonNegativeLong("duration") ?: history.optNonNegativeLong("duration")
            val progressPercent =
              if (progressSeconds != null && durationSeconds != null && durationSeconds > 0L) {
                ((progressSeconds * 100L) / durationSeconds).toInt().coerceIn(0, 100)
              } else null
            val episodeIndex =
              history.optString("ep_index").ifBlank {
                history.optString("ep_index_title").ifBlank { item.optString("show_title") }
              }
            val watchProgress =
              episodeId
                .takeIf { it > 0L }
                ?.let {
                  BangumiWatchProgress(
                    episodeId = it,
                    episodeIndex = episodeIndex,
                    positionMs =
                      (progressSeconds ?: 0L).coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L,
                    percent = progressPercent,
                  )
                }
            val bangumiCard =
              SpaceContentCard(
                id = historyId,
                title = item.optString("title"),
                subtitle = item.optString("show_title").ifBlank { item.optString("new_desc") },
                // 对只在历史里出现的季度，游标行的封面是最好的回退。追番行在可用时
                // 与下面的季度封面合并。
                coverUrl = "",
                historyCoverUrl = cover,
                aid = oid,
                bvid = bvid,
                videoUrl =
                  when {
                    episodeId > 0L -> "https://www.bilibili.com/bangumi/play/ep$episodeId"
                    bvid.isNotBlank() -> "https://www.bilibili.com/video/$bvid"
                    oid > 0L -> "https://www.bilibili.com/video/av$oid"
                    else -> "https://www.bilibili.com/bangumi/play/ss$seasonId"
                  },
                seasonId = seasonId,
                episodeId = episodeId,
                kind = kind,
                watchProgress = watchProgress,
                seasonType = seasonType,
                hasHistory = true,
                historicalOnly = true,
                lastViewedAt = viewAt,
              )
            add(
              AccountHistoryItem.Bangumi(
                card =
                  FeedCard(
                    aid = oid,
                    bvid = bvid,
                    cid = cid,
                    title = item.optString("title"),
                    // 仅在历史视频卡片上保留已看分集的封面。
                    coverUrl = cover,
                    uploaderName = item.optString("author_name"),
                    uploaderFace =
                      dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(
                          item.optString("author_face")
                        )
                        .orEmpty(),
                    uploaderMid = item.optLong("author_mid"),
                    playCount = 0,
                    danmakuCount = 0,
                    durationSeconds = duration,
                    pubdate = viewAt,
                    description = bangumiCard.subtitle,
                  ),
                bangumi = bangumiCard,
                mediaLabel = mediaLabel,
              )
            )
          }
          bvid.isNotBlank() || business == "archive" -> {
            if (bvid.isBlank() && oid <= 0L) continue
            add(
              AccountHistoryItem.Video(
                FeedCard(
                  aid = oid,
                  bvid = bvid,
                  cid = cid,
                  title = item.optString("title"),
                  coverUrl =
                    dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("cover"))
                      .orEmpty(),
                  uploaderName = item.optString("author_name"),
                  uploaderFace =
                    dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("author_face"))
                      .orEmpty(),
                  uploaderMid = item.optLong("author_mid"),
                  playCount = 0,
                  danmakuCount = 0,
                  durationSeconds = duration,
                  pubdate = viewAt,
                  description = item.optString("show_title").ifBlank { item.optString("new_desc") },
                )
              )
            )
          }
        }
      }
    }
    val cursorJson = data.optJSONObject("cursor") ?: JSONObject()
    val nextCursor =
      HistoryCursor(
        max = cursorJson.optLong("max"),
        viewAt = cursorJson.optLong("view_at"),
        business = cursorJson.optString("business"),
      )
    val hasMore =
      if (data.has("has_more")) data.optBoolean("has_more")
      else
        array.length() > 0 &&
          (nextCursor.max > 0L || nextCursor.viewAt > 0L || nextCursor.business.isNotBlank())
    return AccountHistoryResponse(items, nextCursor, hasMore)
  }

  internal fun historyPgcMediaLabel(vararg hints: String): String {
    val text = hints.joinToString(" ")
    return when {
      // B 站对部分行同时暴露宽泛的“动画”提示和原生“国创”分类。明确的原生分类
      // 必须胜出，否则国创会被错误地路由到番剧。
      text.contains("国创", ignoreCase = true) -> "国创"
      text.contains("港澳台", ignoreCase = true) &&
        (text.contains("番剧", ignoreCase = true) ||
          text.contains("动画", ignoreCase = true) ||
          text.contains("动漫", ignoreCase = true)) -> "港澳台番剧"
      text.contains("番剧", ignoreCase = true) ||
        text.contains("动画", ignoreCase = true) ||
        text.contains("动漫", ignoreCase = true) -> "番剧"
      text.contains("[剧集]", ignoreCase = true) || text.contains("【剧集】", ignoreCase = true) -> "电视剧"
      text.contains("电视剧", ignoreCase = true) -> "电视剧"
      text.contains("纪录片", ignoreCase = true) -> "纪录片"
      text.contains("综艺", ignoreCase = true) -> "综艺"
      text.contains("电影", ignoreCase = true) || text.contains("剧场版", ignoreCase = true) -> "电影"
      else -> "影视"
    }
  }
}

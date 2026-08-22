package dev.openbili.webdemo.api

/**
 * 搜索接口：热搜榜、视频/番剧/影视/专栏/用户搜索、搜索建议与时钟时长解析。
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
 * 搜索 API 集合。
 */
object BiliSearchApi {

  /** 拉取热搜榜。 */
  fun getHotSearch(): List<HotSearchItem> {
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/web-interface/search/square?limit=20&platform=web"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val array =
      json.optJSONObject("data")?.optJSONObject("trending")?.optJSONArray("list")
        ?: return emptyList()
    return buildList {
      for (i in 0 until array.length()) {
        val item = array.getJSONObject(i)
        add(
          HotSearchItem(
            item.optString("keyword"),
            item.optString("show_name", item.optString("keyword")),
          )
        )
      }
    }
  }

  /** 搜索视频，支持排序。 */
  fun searchVideos(keyword: String, page: Int = 1, order: String = "totalrank"): FeedResponse {
    val query =
      BiliApiCommon.signedQuery(
        mapOf(
          "search_type" to "video",
          "keyword" to keyword,
          "page" to page.toString(),
          "order" to order,
          "duration" to "0",
          "tids" to "0",
        )
      )
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/wbi/search/type?$query")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val array =
      json.optJSONObject("data")?.optJSONArray("result") ?: return FeedResponse(emptyList())
    val cards = buildList {
      for (i in 0 until array.length()) {
        val item = array.getJSONObject(i)
        val duration = parseClockDuration(item.optString("duration"))
        val title = BiliArticleApi.decodePlatformHtmlText(item.optString("title"))
        add(
          FeedCard(
            item.optLong("aid", item.optLong("id")),
            item.optString("bvid"),
            0,
            title,
            dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("pic")).orEmpty(),
            item.optString("author"),
            dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("upic")).orEmpty(),
            item.optLong("mid"),
            item.optLong("play"),
            item.optLong("video_review"),
            duration,
            item.optLong("senddate"),
            description =
              BiliArticleApi.decodePlatformHtmlText(item.optString("description")),
          )
        )
      }
    }
    return FeedResponse(cards)
  }

  /** 搜索番剧/影视。 */
  fun searchBangumi(
    keyword: String,
    page: Int = 1,
    kind: SpaceContentKind,
  ): SpaceBangumiResponse {
    require(kind == SpaceContentKind.BANGUMI || kind == SpaceContentKind.DRAMA) {
      "搜索类型无效"
    }
    val searchType = if (kind == SpaceContentKind.BANGUMI) "media_bangumi" else "media_ft"
    val query =
      BiliApiCommon.signedQuery(
        mapOf(
          "search_type" to searchType,
          "keyword" to keyword,
          "page" to page.toString(),
        )
      )
    val response =
      BiliHttpClient.get("https://api.bilibili.com/x/web-interface/wbi/search/type?$query")
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseBangumiSearchResponse(json, requestedPage = page, kind = kind)
  }

  /**
   * 公开搜索面只把 PGC 分成“番剧”和“影视”。保持请求由服务端支撑，然后用返回的
   * 季度/类型标记保留精确的索引分类。
   */
  fun searchBangumiIndex(
    keyword: String,
    category: BangumiExploreCategory,
    page: Int,
  ): BangumiIndexPage {
    val kind =
      if (
        category == BangumiExploreCategory.ANIME || category == BangumiExploreCategory.GUOCHUANG
      ) {
        SpaceContentKind.BANGUMI
      } else {
        SpaceContentKind.DRAMA
      }
    val searchType = if (kind == SpaceContentKind.BANGUMI) "media_bangumi" else "media_ft"
    val query =
      BiliApiCommon.signedQuery(
        mapOf(
          "search_type" to searchType,
          "keyword" to keyword,
          "page" to page.toString(),
        )
      )
    val response =
      BiliHttpClient.get("https://api.bilibili.com/x/web-interface/wbi/search/type?$query")
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseBangumiIndexSearchPage(json, category, page)
  }

  private fun parseBangumiIndexSearchPage(
    json: JSONObject,
    category: BangumiExploreCategory,
    requestedPage: Int,
  ): BangumiIndexPage {
    val data = json.optJSONObject("data") ?: JSONObject()
    val list = data.optJSONArray("result") ?: JSONArray()
    val expectedSeasonType = BiliBangumiApi.bangumiIndexSt(category).toInt()
    val items =
      buildList {
          for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val seasonType = item.optInt("season_type", item.optInt("media_type", 0))
            val typeName = BiliArticleApi.decodeHtmlText(item.optString("type_name")).trim()
            val matchesCategory =
              seasonType == expectedSeasonType || (seasonType <= 0 && typeName == category.label)
            if (!matchesCategory) continue
            val seasonId = item.optLong("season_id")
            val firstEpisode = item.optJSONArray("eps")?.optJSONObject(0)
            val episodeUrl = firstEpisode?.optString("url").orEmpty()
            val episodeId =
              firstEpisode?.optLong("id")?.takeIf { it > 0L }
                ?: BiliBangumiApi.bangumiIdentityFromUrl(episodeUrl).episodeId.takeIf { it > 0L }
                ?: 0L
            if (seasonId <= 0L && episodeId <= 0L) continue
            val title = BiliArticleApi.decodeHtmlText(item.optString("title")).trim()
            if (title.isBlank()) continue
            val badge = BiliArticleApi.decodeHtmlText(item.optString("badge")).trim()
            val score =
              item
                .optJSONObject("media_score")
                ?.optString("score")
                .orEmpty()
                .ifBlank { item.optString("score") }
                .trim()
            val targetUrl =
              firstEpisode?.optString("url")?.takeIf(String::isNotBlank)
                ?: if (seasonId > 0L) "https://www.bilibili.com/bangumi/play/ss$seasonId"
                else "https://www.bilibili.com/bangumi/play/ep$episodeId"
            add(
              BangumiIndexItem(
                seasonId = seasonId,
                mediaId = item.optLong("media_id"),
                episodeId = episodeId,
                title = title,
                subtitle =
                  BiliArticleApi.decodeHtmlText(item.optString("index_show")).trim().ifBlank {
                    BiliArticleApi.decodeHtmlText(item.optString("evaluate")).trim()
                  },
                coverUrl =
                  dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("cover"))
                    .orEmpty(),
                targetUrl = targetUrl,
                indexShow = BiliArticleApi.decodeHtmlText(item.optString("index_show")).trim(),
                badge = badge,
                badgeColor = "",
                badgeNightColor = "",
                score = score,
                orderText = typeName,
                seasonType = seasonType.takeIf { it > 0 } ?: expectedSeasonType,
              )
            )
          }
        }
        .distinctBy { it.seasonId.takeIf { id -> id > 0L } ?: it.episodeId }
    val currentPage = data.optInt("page", requestedPage).takeIf { it > 0 } ?: requestedPage
    val pageSize = data.optInt("pagesize", 20).takeIf { it > 0 } ?: 20
    val total = data.optInt("numResults", 0).coerceAtLeast(0)
    return BangumiIndexPage(
      items = items,
      page = currentPage,
      hasNext = if (total > 0) currentPage * pageSize < total else list.length() >= pageSize,
      total = total,
    )
  }

  internal fun parseBangumiSearchResponse(
    json: JSONObject,
    requestedPage: Int,
    kind: SpaceContentKind,
  ): SpaceBangumiResponse {
    val data = json.optJSONObject("data") ?: JSONObject()
    val array = data.optJSONArray("result") ?: JSONArray()
    val cards = buildList {
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val seasonId = item.optLong("season_id")
        val episodes = item.optJSONArray("eps")
        val episode = episodes?.optJSONObject(0)
        val episodeUrl = episode?.optString("url").orEmpty()
        val episodeId =
          episode?.optLong("id")?.takeIf { it > 0L }
            ?: BiliBangumiApi.bangumiIdentityFromUrl(episodeUrl).episodeId.takeIf { it > 0L }
            ?: 0L
        if (seasonId <= 0L && episodeId <= 0L) continue
        val title = BiliArticleApi.decodeHtmlText(item.optString("title")).ifBlank { "番剧影视" }
        val subtitle =
          BiliArticleApi.decodeHtmlText(item.optString("index_show"))
            .ifBlank { BiliArticleApi.decodeHtmlText(item.optString("evaluate")) }
            .ifBlank { BiliArticleApi.decodeHtmlText(item.optString("type_name")) }
        add(
          SpaceContentCard(
            id = "search:${kind.name.lowercase()}:${seasonId.takeIf { it > 0L } ?: episodeId}",
            title = title,
            subtitle = subtitle,
            coverUrl =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("cover")).orEmpty(),
            videoUrl =
              when {
                episodeId > 0L -> "https://www.bilibili.com/bangumi/play/ep$episodeId"
                seasonId > 0L -> "https://www.bilibili.com/bangumi/play/ss$seasonId"
                else -> episodeUrl
              },
            seasonId = seasonId,
            episodeId = episodeId,
            kind = kind,
          )
        )
      }
    }
    val page = data.optInt("page", requestedPage).takeIf { it > 0 } ?: requestedPage
    val pageSize = data.optInt("pagesize", 20).takeIf { it > 0 } ?: 20
    val total = data.optInt("numResults", -1)
    val hasMore = if (total >= 0) page * pageSize < total else array.length() >= pageSize
    return SpaceBangumiResponse(cards, hasMore)
  }

  /** 搜索专栏文章。 */
  fun searchArticles(
    keyword: String,
    page: Int = 1,
    order: String = "totalrank",
  ): ArticleSearchResponse {
    val query =
      BiliApiCommon.signedQuery(
        mapOf(
          "search_type" to "article",
          "keyword" to keyword,
          "page" to page.toString(),
          "order" to order,
        )
      )
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/wbi/search/type?$query")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseArticleSearchResponse(json, page)
  }

  internal fun parseArticleSearchResponse(
    json: JSONObject,
    requestedPage: Int,
  ): ArticleSearchResponse {
    val data = json.optJSONObject("data") ?: JSONObject()
    val array = data.optJSONArray("result") ?: JSONArray()
    val items = buildList {
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val id = item.optLong("id")
        if (id <= 0L) continue
        val covers = item.optJSONArray("image_urls")
        val cover = covers?.optString(0).orEmpty()
        add(
          ArticleItem(
            id = id,
            title = BiliArticleApi.decodeHtmlText(item.optString("title")),
            summary = BiliArticleApi.decodeHtmlText(item.optString("desc")),
            coverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(cover).orEmpty(),
            authorName = BiliArticleApi.decodeHtmlText(item.optString("author")),
            authorMid = item.optLong("mid"),
            categoryName = item.optString("category_name"),
            publishedAt = item.optLong("pub_time", item.optLong("pubdate")),
            viewCount = item.optLong("view"),
            likeCount = item.optLong("like"),
            replyCount = item.optLong("reply"),
          )
        )
      }
    }
    val currentPage = data.optInt("page", requestedPage)
    val totalPages = data.optInt("numPages")
    val hasMore = if (totalPages > 0) currentPage < totalPages else items.size >= 20
    return ArticleSearchResponse(items, hasMore)
  }

  /** 公开的桌面搜索联想端点。它有意不使用账号 cookie。 */
  fun getSearchSuggestions(keyword: String): List<String> {
    val term = keyword.trim()
    if (term.isEmpty()) return emptyList()
    val url =
      "https://s.search.bilibili.com/main/suggest?term=${URLEncoder.encode(term, "UTF-8")}&main_ver=v1"
    val resp = BiliHttpClient.getPublic(url)
    val body = resp.body?.string().orEmpty()
    resp.close()
    return parseSearchSuggestionsResponse(body)
  }

  internal fun parseSearchSuggestionsResponse(body: String): List<String> {
    val tags = JSONObject(body).optJSONObject("result")?.optJSONArray("tag") ?: return emptyList()
    return buildList {
        for (index in 0 until tags.length()) {
          tags.optJSONObject(index)?.optString("term")?.takeIf(String::isNotBlank)?.let(::add)
        }
      }
      .distinct()
      .take(10)
  }

  /** 搜索用户。 */
  fun searchUsers(keyword: String, page: Int = 1): List<SearchUser> {
    val query =
      BiliApiCommon.signedQuery(
        mapOf(
          "search_type" to "bili_user",
          "keyword" to keyword,
          "page" to page.toString(),
          "order" to "totalrank",
          "order_sort" to "0",
        )
      )
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/wbi/search/type?$query")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val array = json.optJSONObject("data")?.optJSONArray("result") ?: return emptyList()
    return buildList {
      for (index in 0 until array.length()) {
        val item = array.getJSONObject(index)
        add(
          SearchUser(
            mid = item.optLong("mid"),
            name = BiliArticleApi.decodePlatformHtmlText(item.optString("uname")),
            face =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("upic")).orEmpty(),
            sign = item.optString("usign"),
            fans = item.optLong("fans"),
            videoCount = item.optInt("videos"),
            level = item.optInt("level"),
            vipActive =
              item.optInt("is_vip", item.optInt("vip")) == 1 ||
                item.optJSONObject("vip")?.optInt("status", 0) == 1,
            vipLabel =
              item.optString("vip_label").ifBlank {
                item.optJSONObject("vip")?.optJSONObject("label")?.optString("text").orEmpty()
              },
            officialVerification = BiliCommentApi.parseListedUserOfficialVerification(item),
          )
        )
      }
    }
  }

  private fun parseClockDuration(value: String): Long {
    val parts = value.split(':').mapNotNull(String::toLongOrNull)
    return when (parts.size) {
      3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
      2 -> parts[0] * 60 + parts[1]
      else -> 0
    }
  }
}

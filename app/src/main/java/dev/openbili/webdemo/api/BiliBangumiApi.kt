package dev.openbili.webdemo.api

/**
 * 番剧（PGC）接口。
 *
 * 覆盖空间追番、首页"正在追"（历史+追番合并）、追番状态与进度解析、番剧探索页与
 * 二级索引（各分类 st 与筛选字段）、季度详情、追番/取追、短评与番剧地址到 BV 的解析。
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
 * 番剧（PGC）API 集合。
 */
object BiliBangumiApi {
  private val bangumiVideoBvidCache = ConcurrentHashMap<String, String>()
  private val authoritativePgcSeasonTypeCache = ConcurrentHashMap<Long, Int>()

  /** 分页拉取空间追番/追剧列表。 */
  fun getSpaceBangumi(
    mid: Long,
    type: Int,
    page: Int,
    pageSize: Int = 30,
  ): SpaceBangumiResponse {
    require(type == 1 || type == 2) { "追番追剧类型无效" }
    require(page > 0 && pageSize > 0) { "追番追剧页码无效" }
    val resp = BiliHttpClient.get(spaceBangumiFollowUrl(mid, type, page, pageSize))
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseSpaceBangumiResponse(json, type, page, pageSize)
  }

  /**
   * 构造与 PGC 首页一致的紧凑"正在追"视图：网页端先放最近的 PGC 历史，再用同媒体
   * 分类下的已追季度补足整行；仅靠追番列表无法还原这个顺序（也无法还原只有历史
   * 记录的行）。
   */
  fun getBangumiWatching(mid: Long, category: BangumiExploreCategory): SpaceBangumiResponse {
    require(mid > 0L) { "用户 UID 无效" }
    val seasonType = bangumiSeasonType(category) ?: return SpaceBangumiResponse(emptyList(), false)
    val history = getBangumiWatchingHistoryPage(category)
    val followed = getBangumiWatchingFollowedPage(mid, category, page = 1)
    return SpaceBangumiResponse(
      cards = mergeBangumiWatchingCards(followed.cards, history.cards, seasonType),
      hasMore = history.hasMore || followed.hasMore,
    )
  }

  /** 拉取请求分类的一页服务端历史游标。 */
  fun getBangumiWatchingHistoryPage(
    category: BangumiExploreCategory,
    cursor: HistoryCursor = HistoryCursor(),
  ): BangumiWatchingHistoryPage {
    if (bangumiSeasonType(category) == null) {
      return BangumiWatchingHistoryPage(emptyList(), cursor, false)
    }
    // “正在追”沿用网页端旧历史里少量 archive 行的番剧提示兼容逻辑；我的历史页使用严格分类。
    val response =
      BiliHistoryApi.getHistory(
        cursor = cursor,
        includeArchivePgcHints = true,
      )
    val cards =
      response.items
        .filterIsInstance<AccountHistoryItem.Bangumi>()
        .map(AccountHistoryItem.Bangumi::bangumi)
        // 历史记录偶尔把国创报成泛化动画类型（1）：先保留两个动画族，等
        // ViewModel 解析出季度详情的权威类型后再在请求轨道里过滤。
        .filter { it.seasonType == 1 || it.seasonType == 4 }
    return BangumiWatchingHistoryPage(cards, response.cursor, response.hasMore)
  }

  /** 读取季度详情接口返回的权威季度类型（带进程内缓存）。 */
  fun getAuthoritativePgcSeasonType(seasonId: Long = 0L, episodeId: Long = 0L): Int {
    val cacheKey = seasonId.takeIf { it > 0L }
    cacheKey
      ?.let { authoritativePgcSeasonTypeCache[it] }
      ?.let {
        return it
      }
    val query =
      when {
        seasonId > 0L -> "season_id=$seasonId"
        episodeId > 0L -> "ep_id=$episodeId"
        else -> return 0
      }
    val response = BiliHttpClient.get("https://api.bilibili.com/pgc/view/web/season?$query")
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data = json.optJSONObject("result") ?: return 0
    val resolvedType = resolveAuthoritativePgcSeasonType(data)
    val resolvedSeasonId = data.optLong("season_id", seasonId)
    if (resolvedSeasonId > 0L && resolvedType > 0) {
      authoritativePgcSeasonTypeCache[resolvedSeasonId] = resolvedType
    }
    return resolvedType
  }

  /** 拉取请求分类的一页追番列表。 */
  fun getBangumiWatchingFollowedPage(
    mid: Long,
    category: BangumiExploreCategory,
    page: Int,
  ): SpaceBangumiResponse {
    val seasonType = bangumiSeasonType(category) ?: return SpaceBangumiResponse(emptyList(), false)
    return getSpaceBangumi(mid, type = 1, page = page, pageSize = 30).let { response ->
      response.copy(cards = response.cards.filter { it.seasonType == seasonType })
    }
  }

  internal fun bangumiSeasonType(category: BangumiExploreCategory): Int? =
    when (category) {
      BangumiExploreCategory.ANIME -> 1
      BangumiExploreCategory.GUOCHUANG -> 4
      else -> null
    }

  /** 纯合并步骤独立成函数，保证分类过滤与排序可单测。 */
  internal fun mergeBangumiWatchingCards(
    followed: List<SpaceContentCard>,
    history: List<SpaceContentCard>,
    seasonType: Int,
  ): List<SpaceContentCard> {
    val followedBySeason =
      followed
        .filter { it.seasonType == seasonType && it.seasonId > 0L }
        .associateBy { it.seasonId }
    val historyBySeason = linkedMapOf<Long, SpaceContentCard>()
    val historyWithoutSeason = linkedMapOf<String, SpaceContentCard>()
    history
      .filter { it.seasonType == seasonType }
      .sortedByDescending(SpaceContentCard::lastViewedAt)
      .forEach { card ->
        if (card.seasonId > 0L) historyBySeason.putIfAbsent(card.seasonId, card)
        else historyWithoutSeason.putIfAbsent(card.id, card)
      }
    val merged = buildList {
      historyBySeason.values.forEach { watched ->
        val followedCard = followedBySeason[watched.seasonId]
        add(
          if (followedCard == null)
            watched.copy(
              coverUrl = watched.historyCoverUrl.ifBlank { watched.coverUrl },
              hasHistory = true,
              historicalOnly = true,
            )
          else {
            val progress = watched.watchProgress ?: followedCard.watchProgress
            followedCard.copy(
              subtitle = watched.subtitle.ifBlank { followedCard.subtitle },
              coverUrl =
                watched.historyCoverUrl
                  .ifBlank { watched.coverUrl }
                  .ifBlank { followedCard.coverUrl },
              videoUrl = watched.videoUrl.ifBlank { followedCard.videoUrl },
              aid = watched.aid.takeIf { it > 0L } ?: followedCard.aid,
              bvid = watched.bvid.ifBlank { followedCard.bvid },
              episodeId =
                watched.episodeId.takeIf { it > 0L }
                  ?: progress?.episodeId?.takeIf { it > 0L }
                  ?: 0L,
              watchProgress = progress,
              hasHistory = true,
              historicalOnly = false,
              lastViewedAt = watched.lastViewedAt,
            )
          }
        )
      }
      historyWithoutSeason.values.forEach { add(it.copy(hasHistory = true, historicalOnly = true)) }
      followed.forEach { card ->
        if (card.seasonType != seasonType) return@forEach
        val alreadyAdded = card.seasonId > 0L && historyBySeason.containsKey(card.seasonId)
        if (!alreadyAdded) {
          val progress = card.watchProgress
          val progressEpisodeId = progress?.episodeId?.takeIf { it > 0L } ?: 0L
          add(
            card.copy(
              episodeId = progressEpisodeId,
              videoUrl =
                when {
                  progressEpisodeId > 0L ->
                    "https://www.bilibili.com/bangumi/play/ep$progressEpisodeId"
                  card.seasonId > 0L -> "https://www.bilibili.com/bangumi/play/ss${card.seasonId}"
                  else -> ""
                },
              watchProgress = progress,
              hasHistory = progress != null,
              historicalOnly = false,
            )
          )
        }
      }
    }
    return merged.distinctBy { it.seasonId.takeIf { id -> id > 0L } ?: it.id }
  }

  internal fun parseSpaceBangumiResponse(
    json: JSONObject,
    type: Int,
    page: Int,
    pageSize: Int,
  ): SpaceBangumiResponse {
    val data = json.optJSONObject("data") ?: return SpaceBangumiResponse(emptyList(), false)
    val list = data.optJSONArray("list") ?: return SpaceBangumiResponse(emptyList(), false)
    val kind = if (type == 1) SpaceContentKind.BANGUMI else SpaceContentKind.DRAMA
    val cards = buildList {
      for (i in 0 until list.length()) {
        val row = list.optJSONObject(i) ?: continue
        val seasonId = row.optLong("season_id")
        val latestEpisode = row.optJSONObject("new_ep")
        val episodeId = latestEpisode?.optLong("id") ?: 0L
        val latestLabel = latestEpisode?.optString("index_show").orEmpty()
        val watchProgress = parseBangumiWatchProgress(row)
        val mediaLabel =
          BiliHistoryApi.historyPgcMediaLabel(
            row.optString("season_type_name"),
            row.optString("type_name"),
            row.optString("badge"),
            row.optString("tag_name"),
          )
        val resolvedSeasonType = resolvePgcSeasonType(row, mediaLabel = mediaLabel)
        if (seasonId > 0L && resolvedSeasonType == 4) {
          authoritativePgcSeasonTypeCache[seasonId] = resolvedSeasonType
        }
        add(
          SpaceContentCard(
            id = "${kind.name.lowercase()}:${seasonId.takeIf { it > 0L } ?: "${page}_$i"}",
            title = row.optString("title", "追番追剧"),
            subtitle = latestLabel.ifBlank { row.optString("evaluate") },
            coverUrl =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(row.optString("cover")).orEmpty(),
            videoUrl =
              when {
                episodeId > 0L -> "https://www.bilibili.com/bangumi/play/ep$episodeId"
                seasonId > 0L -> "https://www.bilibili.com/bangumi/play/ss$seasonId"
                else -> ""
              },
            seasonId = seasonId,
            episodeId = episodeId,
            kind = kind,
            watchProgress = watchProgress,
            watchProgressState =
              if (watchProgress != null) BangumiWatchProgressState.RESOLVED
              else BangumiWatchProgressState.UNAVAILABLE,
            seasonType = resolvedSeasonType,
          )
        )
      }
    }
    val total = data.optInt("total", -1)
    val currentPage = data.optInt("pn", page).takeIf { it > 0 } ?: page
    val effectivePageSize = data.optInt("ps", pageSize).takeIf { it > 0 } ?: pageSize
    val hasMore =
      if (total >= 0) currentPage * effectivePageSize < total
      else list.length() >= effectivePageSize
    return SpaceBangumiResponse(cards, hasMore)
  }

  /**
   * 响应确实携带进度时解析内联追番进度。
   *
   * 公开空间追番列表通常只暴露空的展示用 `progress` 字符串，不能据此断定登录用户
   * 从未看过该季度。
   */
  internal fun parseBangumiWatchProgress(row: JSONObject): BangumiWatchProgress? {
    // 部分季度形响应带有 row.user_season.last_ep_id。
    val fromUserSeason =
      row.optJSONObject("user_season")?.let { userSeason ->
        val progress = parseBangumiWatchProgressObject(userSeason)
        if (progress == null) return@let null
        val progressStr = row.opt("progress")
        val percent =
          when (progressStr) {
            is Number -> progressStr.toInt().coerceIn(0, 100)
            is String -> progressStr.removeSuffix("%").toIntOrNull()?.coerceIn(0, 100)
            else -> null
          }
        progress.copy(percent = percent)
      }
    if (fromUserSeason != null) return fromUserSeason

    // 来自 season/user/status 的进度：result.progress.last_ep_id 或 result.watch_progress
    val status = row.optJSONObject("result") ?: row.optJSONObject("data")
    if (status != null) {
      val progressObj = status.optJSONObject("progress") ?: status.optJSONObject("watch_progress")
      if (progressObj != null) {
        val progress = parseBangumiWatchProgressObject(progressObj)
        if (progress != null) {
          val percentField = progressObj.opt("progress")
          val percent =
            when (percentField) {
              is Number -> percentField.toInt().coerceIn(0, 100)
              is String -> percentField.removeSuffix("%").toIntOrNull()?.coerceIn(0, 100)
              else -> null
            }
          return progress.copy(percent = percent)
        }
      }
    }

    // 独立的进度 JSON 对象（最后手段：row 自身带 last_ep_id）
    val standalone = row.optJSONObject("progress")
    if (standalone != null) return parseBangumiWatchProgressObject(standalone)
    return null
  }

  /**
   * B 站网页播放器用 last_ep_progress（毫秒）与 last_ep_index_title；旧的关注/状态
   * 响应用 last_time（秒）与 last_ep_index——保留旧结构作为兼容兜底。
   */
  private fun parseBangumiWatchProgressObject(value: JSONObject): BangumiWatchProgress? {
    val episodeId = value.optNonNegativeLong("last_ep_id")?.takeIf { it > 0L } ?: return null
    val index =
      value.optString("last_ep_index_title").ifBlank {
        value.optString("last_ep_index")
      }
    val positionMs =
      value.optNonNegativeLong("last_ep_progress")
        ?: value
          .optNonNegativeLong("last_time")
          ?.coerceAtMost(Long.MAX_VALUE / 1_000L)
          ?.times(1_000L)
        ?: 0L
    return BangumiWatchProgress(
      episodeId = episodeId,
      episodeIndex = index,
      positionMs = positionMs,
    )
  }

  internal fun spaceBangumiFollowUrl(
    mid: Long,
    type: Int,
    page: Int,
    pageSize: Int,
  ): String =
    "https://api.bilibili.com/x/space/bangumi/follow/list" +
      "?type=$type&pn=$page&ps=$pageSize&vmid=$mid"

  private fun JSONObject.optPositiveFlag(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
      is Boolean -> value
      is Number -> value.toInt() > 0
      is String ->
        when (value.trim().lowercase()) {
          "1",
          "true" -> true
          "0",
          "false" -> false
          else -> null
        }
      else -> null
    }
  }

  /** 读取季度追番状态（season/user/status 聚合）。 */
  internal fun getBangumiUserStatus(seasonId: Long): BangumiUserStatus? {
    if (seasonId <= 0L || BiliHttpClient.cookieValue("SESSDATA").isNullOrBlank()) return null
    return runCatching {
        val response =
          BiliHttpClient.get(
            "https://api.bilibili.com/pgc/view/web/season/user/status?season_id=$seasonId"
          )
        val json = JSONObject(response.body?.string().orEmpty())
        response.close()
        if (json.optInt("code") != 0) return@runCatching null
        val status = json.optJSONObject("result") ?: json.optJSONObject("data")
        if (status == null || status.optPositiveFlag("login") == false) return@runCatching null
        BangumiUserStatus(
          followed = status.optPositiveFlag("follow"),
          watchProgress = parseBangumiWatchProgress(json),
        )
      }
      .getOrNull()
  }

  data class BangumiIdentity(val seasonId: Long = 0L, val episodeId: Long = 0L)

  fun bangumiIdentityFromUrl(url: String): BangumiIdentity =
    BangumiIdentity(
      seasonId =
        Regex("/ss(\\d+)", RegexOption.IGNORE_CASE)
          .find(url)
          ?.groupValues
          ?.getOrNull(1)
          ?.toLongOrNull() ?: 0L,
      episodeId =
        Regex("/ep(\\d+)", RegexOption.IGNORE_CASE)
          .find(url)
          ?.groupValues
          ?.getOrNull(1)
          ?.toLongOrNull() ?: 0L,
    )

  private data class BangumiRecommendationSource(
    val name: String,
    val endpointVersion: Int,
    val bannerStyle: String,
    val takeCount: Int,
  )

  private val bangumiRecommendationSources =
    listOf(
      BangumiRecommendationSource("anime", 3, "web_banner_v3", 2),
      BangumiRecommendationSource("guochuang", 3, "web_banner_v3", 2),
      BangumiRecommendationSource("movie", 2, "web_banner_v2", 2),
      BangumiRecommendationSource("tv", 2, "web_banner_v2", 1),
      BangumiRecommendationSource("documentary", 2, "web_banner_v2", 1),
      BangumiRecommendationSource("variety", 2, "web_banner_v2", 1),
    )

  /** 拉取番剧首页"本期推荐"模块。 */
  fun getBangumiRecommendations(): List<BangumiRecommendation> {
    val failures = mutableListOf<Throwable>()
    val recommendations = buildList {
      bangumiRecommendationSources.forEach { source ->
        runCatching { getBangumiRecommendationSource(source) }
          .onSuccess { addAll(it) }
          .onFailure(failures::add)
      }
    }
    if (recommendations.isEmpty() && failures.isNotEmpty()) {
      throw IllegalStateException("本期推荐加载失败", failures.first())
    }
    return recommendations
  }

  /**
   * 让二级番剧页复用与首页轮播相同的公开 PGC 载荷。v2 与 v3 的响应结构略有差异，
   * 因此刻意在这里归一化，而不是把原始模块 JSON 漏进 Compose。
   */
  fun getBangumiExplorePage(category: BangumiExploreCategory): BangumiExplorePage {
    val response =
      BiliHttpClient.get(
        "https://api.bilibili.com/pgc/page/web/v${category.endpointVersion}?name=${category.apiName}"
      )
    val body = response.body?.string().orEmpty()
    val responseCode = response.code
    response.close()
    if (responseCode !in 200..299) {
      throw IllegalStateException("${category.label}加载失败（HTTP $responseCode）")
    }
    return parseBangumiExplorePage(JSONObject(body), category)
  }

  /**
   * 各 PGC 索引分类的 st / season_type，取自《番剧二级子页接口文档.md》§1.4 已验证
   * 的索引入口矩阵（anime=1, guochuang=4, movie=2, tv=5, documentary=3, variety=7）。
   */
  internal fun bangumiIndexSt(category: BangumiExploreCategory): String =
    when (category) {
      BangumiExploreCategory.ANIME -> "1"
      BangumiExploreCategory.GUOCHUANG -> "4"
      BangumiExploreCategory.MOVIE -> "2"
      BangumiExploreCategory.TV -> "5"
      BangumiExploreCategory.DOCUMENTARY -> "3"
      BangumiExploreCategory.VARIETY -> "7"
    }

  /** 各分类的默认索引排序（番剧/国创 order=3，其余 order=2）。 */
  internal fun bangumiIndexDefaultOrder(category: BangumiExploreCategory): BangumiIndexOrder =
    when (category) {
      BangumiExploreCategory.ANIME,
      BangumiExploreCategory.GUOCHUANG -> BangumiIndexOrder.FOLLOWING
      else -> BangumiIndexOrder.VIEWS
    }

  /**
   * 把类型化查询映射为各分类索引实际接受的筛选字段：只发送该分类有文档的字段（接口
   * 文档警告不要传其他分类的字段）；UI 未暴露的字段保持 `-1` 默认值。
   */
  private fun bangumiIndexFilterParams(
    query: BangumiIndexQuery,
    category: BangumiExploreCategory,
  ): LinkedHashMap<String, String> =
    when (category) {
      BangumiExploreCategory.ANIME ->
        linkedMapOf(
          "season_version" to query.seasonVersion,
          "spoken_language_type" to query.spokenLanguageType,
          "area" to query.area,
          "is_finish" to query.isFinish,
          "copyright" to query.copyright,
          "season_status" to query.seasonStatus,
          "season_month" to query.seasonMonth,
          "year" to query.year,
          "style_id" to query.styleId,
        )
      BangumiExploreCategory.GUOCHUANG ->
        linkedMapOf(
          "season_version" to query.seasonVersion,
          "is_finish" to query.isFinish,
          "copyright" to query.copyright,
          "season_status" to query.seasonStatus,
          "year" to query.year,
          "style_id" to query.styleId,
        )
      BangumiExploreCategory.MOVIE,
      BangumiExploreCategory.TV ->
        linkedMapOf(
          "area" to query.area,
          "style_id" to query.styleId,
          "release_date" to query.releaseDate,
          "season_status" to query.seasonStatus,
        )
      BangumiExploreCategory.DOCUMENTARY ->
        linkedMapOf(
          "producer_id" to query.producerId,
          "style_id" to query.styleId,
          "release_date" to query.releaseDate,
          "season_status" to query.seasonStatus,
        )
      BangumiExploreCategory.VARIETY ->
        linkedMapOf(
          "season_status" to query.seasonStatus,
          "style_id" to query.styleId,
        )
    }

  /**
   * 构造各分类 PGC 索引有文档的公开网页查询，不泄露原始标签。
   */
  internal fun bangumiIndexUrl(
    query: BangumiIndexQuery,
    category: BangumiExploreCategory,
    page: Int,
  ): String {
    require(page > 0) { "索引页码无效" }
    val st = bangumiIndexSt(category)
    val params =
      linkedMapOf(
        "st" to st,
        "season_type" to st,
        "type" to "1",
        "order" to query.order.parameter,
        "sort" to if (query.sortDescending) "0" else "1",
        "page" to page.toString(),
        "pagesize" to "20",
      )
    params += bangumiIndexFilterParams(query, category)
    return "https://api.bilibili.com/pgc/season/index/result?" +
      params.entries.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, "UTF-8")}" + "=${URLEncoder.encode(value, "UTF-8")}"
      }
  }

  /** 分页拉取番剧二级索引。 */
  fun getBangumiIndex(
    query: BangumiIndexQuery,
    category: BangumiExploreCategory,
    page: Int,
  ): BangumiIndexPage {
    val response = BiliHttpClient.get(bangumiIndexUrl(query, category, page))
    val body = response.body?.string().orEmpty()
    val responseCode = response.code
    response.close()
    if (responseCode !in 200..299) {
      throw IllegalStateException("${category.label}索引加载失败（HTTP $responseCode）")
    }
    return parseBangumiIndexPage(JSONObject(body), page)
  }

  internal fun parseBangumiIndexPage(json: JSONObject, requestedPage: Int): BangumiIndexPage {
    if (json.optInt("code") != 0) {
      throw IllegalStateException(json.optString("message").ifBlank { "番剧索引加载失败" })
    }
    val data = json.optJSONObject("data") ?: JSONObject()
    val list = data.optJSONArray("list") ?: JSONArray()
    val items =
      buildList {
          for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val seasonId = item.optLong("season_id")
            val firstEpisode = item.optJSONObject("first_ep")
            val episodeId = firstEpisode?.optLong("ep_id") ?: 0L
            if (seasonId <= 0L && episodeId <= 0L) continue
            val rawTarget = item.optString("link")
            val targetUrl = rawTarget.ifBlank {
              if (seasonId > 0L) "https://www.bilibili.com/bangumi/play/ss$seasonId"
              else "https://www.bilibili.com/bangumi/play/ep$episodeId"
            }
            val title = item.optString("title").trim()
            if (title.isBlank()) continue
            val badgeInfo = item.optJSONObject("badge_info")
            add(
              BangumiIndexItem(
                seasonId = seasonId,
                mediaId = item.optLong("media_id"),
                episodeId = episodeId,
                title = title,
                subtitle = item.optString("subTitle").trim(),
                coverUrl =
                  dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("cover"))
                    .orEmpty(),
                targetUrl = targetUrl,
                indexShow = item.optString("index_show").trim(),
                badge = item.optString("badge").trim(),
                badgeColor = badgeInfo?.optString("bg_color").orEmpty(),
                badgeNightColor = badgeInfo?.optString("bg_color_night").orEmpty(),
                score = item.optString("score").trim(),
                orderText = item.optString("order").trim(),
                seasonType = item.optInt("season_type", 1),
              )
            )
          }
        }
        .distinctBy { item -> item.seasonId.takeIf { it > 0L } ?: item.episodeId }
    return BangumiIndexPage(
      items = items,
      page = data.optInt("num", requestedPage).takeIf { it > 0 } ?: requestedPage,
      hasNext = data.optInt("has_next") == 1,
      total = data.optInt("total").coerceAtLeast(0),
    )
  }

  internal fun parseBangumiExplorePage(
    json: JSONObject,
    category: BangumiExploreCategory,
  ): BangumiExplorePage {
    if (json.optInt("code") != 0) {
      throw IllegalStateException(json.optString("message").ifBlank { "${category.label}加载失败" })
    }
    val modules = json.optJSONObject("data")?.optJSONArray("modules") ?: JSONArray()
    val sections =
      buildList {
          for (moduleIndex in 0 until modules.length()) {
            val module = modules.optJSONObject(moduleIndex) ?: continue
            val style = module.optString("style")
            if (
              style == "web_index_v3" ||
                style == "web_index_v2" ||
                style == "web_banner_v3" ||
                style == "web_banner_v2"
            ) {
              continue
            }
            val rawItems = module.optJSONArray("items") ?: continue
            val cardStyle =
              if (
                style.contains("rank") ||
                  style.contains("operation_v") ||
                  style.contains("timeline")
              ) {
                BangumiExploreCardStyle.POSTER
              } else {
                BangumiExploreCardStyle.LANDSCAPE
              }
            val items =
              parseBangumiExploreItems(
                rawItems = rawItems,
                category = category,
                sectionStyle = style,
                defaultCardStyle = cardStyle,
              )
            if (items.isEmpty()) continue
            val title =
              module
                .optString("title")
                .trim()
                .ifBlank {
                  module.optJSONObject("header")?.optString("title").orEmpty().trim()
                }
                .ifBlank { "为你推荐" }
            val kind = bangumiExploreSectionKind(style)
            add(
              BangumiExploreSection(
                stableId = "${category.apiName}:$style:$moduleIndex",
                title = title,
                items = if (kind == BangumiExploreSectionKind.FEED) items else items.take(18),
                kind = kind,
              )
            )
          }
        }
        .take(6)
    return BangumiExplorePage(category = category, sections = sections)
  }

  internal fun bangumiExploreSectionKind(style: String): BangumiExploreSectionKind =
    when {
      style.contains("timeline", ignoreCase = true) -> BangumiExploreSectionKind.TIMELINE
      style.contains("feed", ignoreCase = true) -> BangumiExploreSectionKind.FEED
      style.contains("rank", ignoreCase = true) -> BangumiExploreSectionKind.RANKING
      style.contains("hot", ignoreCase = true) -> BangumiExploreSectionKind.HOT
      style.contains("recommend", ignoreCase = true) -> BangumiExploreSectionKind.RECOMMENDATION
      else -> BangumiExploreSectionKind.OTHER
    }

  private fun parseBangumiExploreItems(
    rawItems: JSONArray,
    category: BangumiExploreCategory,
    sectionStyle: String,
    defaultCardStyle: BangumiExploreCardStyle,
  ): List<BangumiExploreItem> =
    buildList {
        val sectionKind = bangumiExploreSectionKind(sectionStyle)

        fun addItem(item: JSONObject, parentSubtitle: String = "") {
          val title = item.optString("title").trim().ifBlank { item.optString("name").trim() }
          val targetUrl = item.optString("link").ifBlank { item.optString("url") }.trim()
          val identity = bangumiIdentityFromUrl(targetUrl)
          val seasonId = item.optLong("season_id").takeIf { it > 0L } ?: identity.seasonId
          val episodeId = item.optLong("episode_id").takeIf { it > 0L } ?: identity.episodeId
          val rawCover = item.optString("cover").ifBlank { item.optString("big_cover") }
          val coverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(rawCover).orEmpty()
          val heroRawCover =
            item
              .optJSONObject("hover")
              ?.optString("img")
              .orEmpty()
              .ifBlank { item.optString("big_cover") }
              .ifBlank { rawCover }
          val heroCoverUrl =
            dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(heroRawCover).orEmpty()
          if (
            title.isBlank() ||
              targetUrl.isBlank() ||
              coverUrl.isBlank() ||
              (seasonId <= 0L && episodeId <= 0L)
          )
            return
          val subtitle =
            item
              .optString("sub_title")
              .trim()
              .ifBlank {
                item.optString("evaluate").trim()
              }
              .ifBlank { item.optString("text").trim() }
              .ifBlank { parentSubtitle }
          val itemCardStyle =
            when (item.optString("card_style").trim().lowercase()) {
              "v_card",
              "poster",
              "vertical" -> BangumiExploreCardStyle.POSTER
              "h_card",
              "landscape",
              "horizontal" -> BangumiExploreCardStyle.LANDSCAPE
              else -> defaultCardStyle
            }
          val parsedRating =
            item.optString("rating").trim().toDoubleOrNull() ?: item.optDouble("rating", Double.NaN)
          val rating = parsedRating.takeIf { !it.isNaN() && it > 0.0 }
          val ratingCount = item.optLong("rating_count", 0L).coerceAtLeast(0L)
          add(
            BangumiExploreItem(
              stableId =
                "${category.apiName}:$sectionStyle:" +
                  if (seasonId > 0L) "ss$seasonId" else "ep$episodeId",
              title = title,
              subtitle = subtitle,
              coverUrl = coverUrl,
              targetUrl = targetUrl,
              seasonId = seasonId,
              episodeId = episodeId,
              style = itemCardStyle,
              sectionKind = sectionKind,
              rating = rating,
              ratingCount = ratingCount,
              heroCoverUrl = heroCoverUrl,
            )
          )
        }

        for (index in 0 until rawItems.length()) {
          val item = rawItems.optJSONObject(index) ?: continue
          val parentSubtitle = item.optString("text").trim()
          val nested = item.optJSONArray("episodes") ?: item.optJSONArray("sub_items")
          if (nested != null) {
            for (childIndex in 0 until nested.length()) {
              nested.optJSONObject(childIndex)?.let { addItem(it, parentSubtitle) }
            }
          } else {
            addItem(item)
          }
        }
      }
      .distinctBy(BangumiExploreItem::stableId)

  private fun getBangumiRecommendationSource(
    source: BangumiRecommendationSource
  ): List<BangumiRecommendation> {
    val response =
      BiliHttpClient.get(
        "https://api.bilibili.com/pgc/page/web/v${source.endpointVersion}?name=${source.name}"
      )
    val body = response.body?.string().orEmpty()
    val contentType = response.header("Content-Type").orEmpty()
    val responseCode = response.code
    response.close()
    if (responseCode !in 200..299) {
      throw IllegalStateException("${source.name} 推荐接口异常（HTTP $responseCode）")
    }
    if (!contentType.contains("json", ignoreCase = true) && !body.trimStart().startsWith("{")) {
      throw IllegalStateException("${source.name} 推荐接口返回了非 JSON 数据")
    }
    return parseBangumiRecommendations(
        json = JSONObject(body),
        bannerStyle = source.bannerStyle,
        sourceName = source.name,
      )
      .take(source.takeCount)
  }

  internal fun parseBangumiRecommendations(json: JSONObject): List<BangumiRecommendation> {
    return parseBangumiRecommendations(json, bannerStyle = "web_banner_v3")
  }

  internal fun parseBangumiRecommendations(
    json: JSONObject,
    bannerStyle: String,
    sourceName: String = "",
  ): List<BangumiRecommendation> {
    if (json.optInt("code") != 0) {
      throw IllegalStateException(json.optString("message").ifBlank { "本期推荐加载失败" })
    }
    val modules = json.optJSONObject("data")?.optJSONArray("modules") ?: JSONArray()
    var array: JSONArray? = null
    for (moduleIndex in 0 until modules.length()) {
      val module = modules.optJSONObject(moduleIndex) ?: continue
      if (module.optString("style") == bannerStyle) {
        array = module.optJSONArray("items")
        break
      }
    }
    val bannerItems = array ?: JSONArray()
    val items = buildList {
      for (index in 0 until bannerItems.length()) {
        val item = bannerItems.optJSONObject(index) ?: continue
        val title =
          item.optString("title").trim().ifBlank {
            item.optString("name").trim()
          }
        if (title.isBlank()) continue
        val rawBanner =
          item.optString("big_cover").ifBlank {
            item.optString("cover").ifBlank { item.optString("image_url") }
          }
        if (rawBanner.isBlank()) continue
        val bannerUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(rawBanner).orEmpty()
        if (bannerUrl.isBlank()) continue
        val cardUrl =
          dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(
              item.optString("cover").ifBlank { rawBanner }
            )
            .orEmpty()
        if (cardUrl.isBlank()) continue
        val targetUrl = item.optString("url").ifBlank { item.optString("link") }
        if (targetUrl.isBlank()) continue
        val urlIdentity = bangumiIdentityFromUrl(targetUrl)
        val seasonId = item.optLong("season_id").takeIf { it > 0L } ?: urlIdentity.seasonId
        val episodeId = item.optLong("episode_id").takeIf { it > 0L } ?: urlIdentity.episodeId
        val isLive = targetUrl.contains("live.bilibili.com", ignoreCase = true)
        if (!isLive && seasonId <= 0L && episodeId <= 0L) continue
        val baseStableId =
          when {
            seasonId > 0L -> "season:$seasonId"
            episodeId > 0L -> "episode:$episodeId"
            else -> targetUrl.trimEnd('/').lowercase()
          }
        val stableId = if (sourceName.isBlank()) baseStableId else "$sourceName:$baseStableId"
        add(
          BangumiRecommendation(
            stableId = stableId,
            title = title,
            bannerUrl = bannerUrl,
            cardUrl = cardUrl,
            targetUrl = targetUrl,
            isLive = isLive,
            seasonId = seasonId,
            episodeId = episodeId,
            position = index,
          )
        )
      }
    }
    return items.distinctBy(BangumiRecommendation::stableId)
  }

  private fun resolveBangumiIdentity(bvid: String, aid: Long): BangumiIdentity {
    require(bvid.isNotBlank() || aid > 0L) { "番剧标识无效" }
    val parameter = if (bvid.isNotBlank()) "bvid=$bvid" else "aid=$aid"
    val response = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/view?$parameter")
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val redirectUrl = json.optJSONObject("data")?.optString("redirect_url").orEmpty()
    val identity = bangumiIdentityFromUrl(redirectUrl)
    if (identity.seasonId <= 0L && identity.episodeId <= 0L) {
      throw IllegalStateException("未找到对应的番剧资料")
    }
    return identity
  }

  /** 拉取季度详情。 */
  fun getBangumiSeason(
    seasonId: Long = 0L,
    episodeId: Long = 0L,
    bvid: String = "",
    aid: Long = 0L,
  ): BangumiSeason {
    val resolvedIdentity =
      if (seasonId <= 0L && episodeId <= 0L) resolveBangumiIdentity(bvid, aid)
      else BangumiIdentity(seasonId = seasonId, episodeId = episodeId)
    require(resolvedIdentity.seasonId > 0L || resolvedIdentity.episodeId > 0L) {
      "番剧标识无效"
    }
    val query =
      if (resolvedIdentity.episodeId > 0L) "ep_id=${resolvedIdentity.episodeId}"
      else "season_id=${resolvedIdentity.seasonId}"
    val resp = BiliHttpClient.get("https://api.bilibili.com/pgc/view/web/season?$query")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data =
      json.optJSONObject("result")
        ?: json.optJSONObject("data")
        ?: throw IllegalStateException("番剧信息为空")
    fun stringList(key: String): List<String> {
      val array = data.optJSONArray(key) ?: return emptyList()
      return buildList {
        for (index in 0 until array.length()) {
          val value =
            array.optJSONObject(index)?.optString("name").orEmpty().ifBlank {
              array.optString(index)
            }
          if (value.isNotBlank()) add(value)
        }
      }
    }
    fun parseEpisodes(array: org.json.JSONArray?): List<BangumiEpisode> = buildList {
      if (array != null) {
        for (index in 0 until array.length()) {
          val episode = array.optJSONObject(index) ?: continue
          val bvid = episode.optString("bvid")
          val cid = episode.optLong("cid")
          if (bvid.isBlank() || cid <= 0L) continue
          add(
            BangumiEpisode(
              id = episode.optLong("id"),
              aid = episode.optLong("aid"),
              bvid = bvid,
              cid = cid,
              title = episode.optString("title").ifBlank { (index + 1).toString() },
              longTitle = episode.optString("long_title"),
              coverUrl =
                dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(episode.optString("cover"))
                  .orEmpty(),
              durationSeconds = (episode.optLong("duration") / 1_000L).coerceAtLeast(0L),
            )
          )
        }
      }
    }
    val episodes = parseEpisodes(data.optJSONArray("episodes"))
    val seasons = buildList {
      val array = data.optJSONArray("seasons")
      if (array != null) {
        for (index in 0 until array.length()) {
          val season = array.optJSONObject(index) ?: continue
          val id = season.optLong("season_id")
          if (id > 0L) {
            add(
              BangumiSeasonOption(
                seasonId = id,
                title =
                  season.optString("season_title").ifBlank {
                    season.optString("title", "第 ${index + 1} 季")
                  },
              )
            )
          }
        }
      }
    }
    val sections = buildList {
      val array = data.optJSONArray("section")
      if (array != null) {
        for (index in 0 until array.length()) {
          val section = array.optJSONObject(index) ?: continue
          val sectionEpisodes = parseEpisodes(section.optJSONArray("episodes"))
          if (sectionEpisodes.isNotEmpty()) {
            add(
              BangumiSection(
                id = section.optLong("id", index.toLong()),
                title = section.optString("title", "其他内容"),
                episodes = sectionEpisodes,
              )
            )
          }
        }
      }
    }
    val rating = data.optJSONObject("rating")
    val stat = data.optJSONObject("stat")
    val publish = data.optJSONObject("publish")
    val inlineUserStatus = data.optJSONObject("user_status")
    val resolvedSeasonId = data.optLong("season_id", seasonId)
    val remoteStatus = getBangumiUserStatus(resolvedSeasonId)
    val followed = remoteStatus?.followed ?: inlineUserStatus?.optPositiveFlag("follow") ?: false
    val seasonType = resolveAuthoritativePgcSeasonType(data)
    if (resolvedSeasonId > 0L && seasonType > 0) {
      authoritativePgcSeasonTypeCache[resolvedSeasonId] = seasonType
    }
    return BangumiSeason(
      seasonId = resolvedSeasonId,
      mediaId = data.optLong("media_id"),
      title = data.optString("title", "番剧"),
      coverUrl =
        dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(data.optString("cover")).orEmpty(),
      evaluate = data.optString("evaluate"),
      typeName = data.optString("type_name"),
      areas = stringList("areas"),
      styles = stringList("styles"),
      publishText =
        publish?.optString("pub_time_show").orEmpty().ifBlank {
          publish?.optString("release_date_show").orEmpty()
        },
      rating = rating?.optDouble("score")?.takeIf { !it.isNaN() && it > 0.0 },
      ratingCount = rating?.optLong("count") ?: 0L,
      followCount = stat?.let { it.optLong("favorites", it.optLong("series_follow")) } ?: 0L,
      viewCount = stat?.optLong("views") ?: 0L,
      danmakuCount = stat?.optLong("danmakus") ?: 0L,
      followed = followed,
      episodes = episodes,
      seasons = seasons,
      sections = sections,
      seasonType = seasonType,
      userRatingScore =
        inlineUserStatus?.optJSONObject("review")?.optInt("score")?.takeIf {
          it in 2..10 && it % 2 == 0
        },
    )
  }

  /** 追番/取追。 */
  fun setBangumiFollow(seasonId: Long, followed: Boolean) {
    require(seasonId > 0L) { "番剧标识无效" }
    val csrf = BiliApiCommon.requireCsrf()
    val action = if (followed) "add" else "del"
    val response =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/pgc/web/follow/$action",
        mapOf(
          "season_id" to seasonId.toString(),
          "csrf" to csrf,
          "csrf_token" to csrf,
        ),
      )
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) {
      throw IllegalStateException(json.optString("message", if (followed) "追番失败" else "取消追番失败"))
    }
  }

  /** 发表番剧短评（评分 + 内容）。 */
  fun postBangumiShortReview(mediaId: Long, score: Int, content: String) {
    require(mediaId > 0L) { "媒体标识无效" }
    require(score in 2..10 && score % 2 == 0) { "评分应为一到五颗星" }
    val csrf = BiliApiCommon.requireCsrf()
    val response =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/pgc/review/short/post",
        mapOf(
          "media_id" to mediaId.toString(),
          "score" to score.toString(),
          "content" to content.trim().take(100),
          "csrf" to csrf,
          "csrf_token" to csrf,
        ),
      )
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) {
      throw IllegalStateException(json.optString("message", "短评发布失败"))
    }
  }

  /**
   * 把普通视频 URL 或临时番剧页 URL 解析为原生视频页消费的 BV 号。季度 URL 使用其
   * 首个可播放剧集（与详情页做的元数据选择一致）；剧集 URL 仍解析到该确切剧集。
   */
  fun resolveVideoBvid(videoUrl: String): String {
    val pageId = videoUrl.substringAfterLast("/").substringBefore("?").substringBefore("#")
    if (pageId.startsWith("BV", ignoreCase = true) || pageId.startsWith("av", ignoreCase = true)) {
      return pageId
    }
    val bangumiMatch =
      Regex("^(ep|ss)(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(pageId) ?: return pageId
    bangumiVideoBvidCache[pageId]?.let {
      return it
    }
    val id = bangumiMatch.groupValues[2]
    val parameter =
      if (bangumiMatch.groupValues[1].equals("ep", ignoreCase = true)) "ep_id" else "season_id"
    val resp = BiliHttpClient.get("https://api.bilibili.com/pgc/view/web/season?$parameter=$id")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data =
      json.optJSONObject("result")
        ?: json.optJSONObject("data")
        ?: throw IllegalStateException("番剧信息为空")
    val episodes = data.optJSONArray("episodes")
    val requestedEpisodeId =
      id.toLongOrNull().takeIf {
        bangumiMatch.groupValues[1].equals("ep", ignoreCase = true)
      }
    var fallback = ""
    var resolved = ""
    fun inspectEpisodes(array: org.json.JSONArray?, allowFallback: Boolean) {
      if (array == null || resolved.isNotBlank()) return
      for (index in 0 until array.length()) {
        val episode = array.optJSONObject(index) ?: continue
        val bvid = episode.optString("bvid")
        if (bvid.isBlank()) continue
        if (allowFallback && fallback.isBlank()) fallback = bvid
        if (requestedEpisodeId != null && episode.optLong("id") == requestedEpisodeId) {
          resolved = bvid
          return
        }
      }
    }
    inspectEpisodes(episodes, allowFallback = true)
    if (resolved.isBlank() && requestedEpisodeId != null) {
      val sections = data.optJSONArray("section")
      if (sections != null) {
        for (index in 0 until sections.length()) {
          inspectEpisodes(
            sections.optJSONObject(index)?.optJSONArray("episodes"),
            allowFallback = false,
          )
          if (resolved.isNotBlank()) break
        }
      }
    }
    if (fallback.isBlank() && resolved.isBlank()) {
      val sections = data.optJSONArray("section")
      if (sections != null) {
        for (index in 0 until sections.length()) {
          val sectionEpisodes = sections.optJSONObject(index)?.optJSONArray("episodes")
          if (sectionEpisodes == null) continue
          for (episodeIndex in 0 until sectionEpisodes.length()) {
            val bvid = sectionEpisodes.optJSONObject(episodeIndex)?.optString("bvid").orEmpty()
            if (bvid.isNotBlank() && fallback.isBlank()) {
              fallback = bvid
              break
            }
          }
          if (fallback.isNotBlank()) break
        }
      }
    }
    val bvid = resolved.ifBlank { fallback }
    if (bvid.isBlank()) throw IllegalStateException("番剧暂无可播放视频")
    bangumiVideoBvidCache[pageId] = bvid
    return bvid
  }

  internal fun resolvePgcSeasonType(
    primary: JSONObject,
    secondary: JSONObject? = null,
    mediaLabel: String,
  ): Int {
    // 原生"国创"标记比历史/追番行偶尔返回的泛化动画值更精确。
    if (mediaLabel == "国创") return 4
    return sequenceOf(
        primary.optInt("season_type"),
        primary.optInt("media_type"),
        secondary?.optInt("season_type") ?: 0,
        secondary?.optInt("media_type") ?: 0,
      )
      .firstOrNull { it > 0 }
      ?: when {
        mediaLabel.contains("番剧") -> 1
        else -> 0
      }
  }

  internal fun resolveAuthoritativePgcSeasonType(data: JSONObject): Int =
    data.optInt("type", 0).takeIf { it > 0 }
      ?: data.optInt("show_season_type", 0).takeIf { it > 0 }
      ?: 0

  /** 保留旧的归档到剧集路由，但绝不把动画标签提升为 PGC 历史。 */
  internal fun isTypedDramaHistoryHint(value: String): Boolean =
    value.contains("[剧集]", ignoreCase = true) ||
      value.contains("【剧集】", ignoreCase = true) ||
      value.contains("电视剧", ignoreCase = true) ||
      value.contains("纪录片", ignoreCase = true) ||
      value.contains("综艺", ignoreCase = true) ||
      value.contains("电影", ignoreCase = true) ||
      value.contains("剧场版", ignoreCase = true)
}

internal fun JSONObject.optNonNegativeLong(key: String): Long? {
  if (!has(key) || isNull(key)) return null
  return when (val value = opt(key)) {
    is Number -> value.toLong().takeIf { it >= 0L }
    is String -> value.trim().toLongOrNull()?.takeIf { it >= 0L }
    else -> null
  }
}

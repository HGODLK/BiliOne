package dev.openbili.webdemo.live

/**
 * B 站直播区 HTTP 接口的封装层。
 *
 * 集中提供直播分区、首页推荐、关注列表、房间/主播信息、播放地址、弹幕消息配置、表情包、
 * 粉丝勋章、互动抽奖、观众/大航海排行榜以及弹幕/表情发送等接口，并把服务端返回的 JSON
 * 解析为本模块使用的数据类。协议细节（WBI 签名、字段兼容、来源优先级、人气格式化等）都在
 * 这里内聚，供上层 UI 与 [LiveDanmakuClient] 直接调用，避免业务代码与接口细节耦合。
 */

import androidx.core.text.HtmlCompat
import dev.openbili.webdemo.api.BiliApiCommon
import dev.openbili.webdemo.api.BiliHttpClient
import dev.openbili.webdemo.UrlPolicy
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * 直播区网络接口的单一入口（object 单例）。
 *
 * 所有方法都是无状态的静态调用，内部通过 [BiliHttpClient] 发出请求，并统一在 [getJson]
 * 里校验返回的 code 字段；各 parse* 函数按 B 站实际返回结构做兼容解析，屏蔽字段名差异。
 */
object BiliLiveApi {
  /** 返回全部直播分区（仅一级分区，不含子分区），用于分区筛选页。 */
  fun getLiveAreas(): List<LiveAreaFilter> {
    return getLiveAreaGroups().map { it.parent }
  }

  /** 拉取直播分区树（一级分区 + 其子分区），并解析为分组结构。 */
  fun getLiveAreaGroups(): List<LiveAreaGroup> {
    val json =
      getJson(
        "https://api.live.bilibili.com/room/v1/Area/getList",
        "直播分区",
      )
    return parseLiveAreaGroups(json)
  }

  /**
   * 获取直播首页推荐房间。
   *
   * 使用网页首屏首页接口返回的推荐位，而不是「更多推荐」接口。首页响应同时包含顶部
   * Hero 的 recommend_room_list 和「我的关注」模块；这里明确只读取前者，避免首屏推荐
   * 被当前账号的关注直播污染。结果只保留正在直播的房间，并裁剪到调用方要求的数量。
   */
  fun getHomeRecommendations(limit: Int = 5): List<LiveSearchRoom> {
    val query =
      BiliApiCommon.signedQuery(
        linkedMapOf(
          "platform" to "web",
          "web_location" to "444.7",
        )
      )
    val json =
      getJson(
        "https://api.live.bilibili.com/xlive/web-interface/v1/index/getList?$query",
        "直播首页推荐",
      )
    return parseLiveHomeRecommendations(json).take(limit.coerceAtLeast(1))
  }

  /**
   * 获取当前账号正在直播的关注房间。
   *
   * 优先使用直播首页「我的关注」模块（接口只返回有限条数）；当在线数超过首页返回数量时，
   * 再补拉完整关注列表并做去重合并。首页接口失败时退化为直接请求完整列表，保证已登录
   * 用户仍能拿到关注数据。
   */
  fun getFollowedLiveRooms(): LiveFollowingResponse {
    if (BiliHttpClient.cookieValue("SESSDATA").isNullOrBlank()) {
      return LiveFollowingResponse(isLoggedIn = false, rooms = emptyList())
    }
    // 直播首页会把个性化「关注」模块和其余模块一起返回；请求拦截器会补上网页所用的
    // WBI 签名，因此这里直接沿用首页接口的请求形态。
    val query =
      BiliApiCommon.signedQuery(
        linkedMapOf(
          "platform" to "web",
          "web_location" to "444.7",
        )
      )
    val json =
      try {
        getJson(
          "https://api.live.bilibili.com/xlive/web-interface/v1/index/getList?$query",
          "我的关注",
        )
      } catch (homepageError: Exception) {
        val fallbackRooms =
          runCatching { getAllFollowedLiveRooms(expectedCount = 0) }
            .getOrElse { throw homepageError }
        return LiveFollowingResponse(
          isLoggedIn = true,
          rooms = enrichMissingFollowedCovers(fallbackRooms),
        )
      }
    val homepageRooms = parseFollowedLiveRooms(json)
    val onlineCount = parseFollowedLiveOnlineCount(json)
    val needsFullList =
      onlineCount > homepageRooms.size || homepageRooms.size >= HOMEPAGE_FOLLOWING_LIMIT
    val fullRooms =
      if (needsFullList) {
        runCatching { getAllFollowedLiveRooms(expectedCount = onlineCount) }
          .getOrDefault(emptyList())
      } else {
        emptyList()
      }
    val mergedRooms = mergeFollowedLiveRooms(homepageRooms, fullRooms)
    return LiveFollowingResponse(
      isLoggedIn = true,
      rooms = enrichMissingFollowedCovers(mergedRooms),
    )
  }

  /**
   * 合并首页关注房间与完整关注房间。
   *
   * 以首页顺序为准，用完整列表逐字段补全首页缺失的数据（短号/UID/头像/封面/分区等）；
   * 完整列表里首页没有的房间追加到末尾，从而兼顾首页的展示顺序与列表的完整性。
   */
  internal fun mergeFollowedLiveRooms(
    homepageRooms: List<LiveSearchRoom>,
    fullRooms: List<LiveSearchRoom>,
  ): List<LiveSearchRoom> {
    val fullRoomsById = fullRooms.associateBy(LiveSearchRoom::roomId)
    val homepageRoomIds = homepageRooms.mapTo(hashSetOf(), LiveSearchRoom::roomId)
    val mergedHomepageRooms = homepageRooms.map { homepageRoom ->
      val fullRoom = fullRoomsById[homepageRoom.roomId] ?: return@map homepageRoom
      homepageRoom.copy(
        shortRoomId = homepageRoom.shortRoomId ?: fullRoom.shortRoomId,
        uid = homepageRoom.uid.takeIf { it > 0L } ?: fullRoom.uid,
        faceUrl = homepageRoom.faceUrl ?: fullRoom.faceUrl,
        coverUrl = fullRoom.coverUrl ?: homepageRoom.coverUrl,
        keyframeUrl = homepageRoom.keyframeUrl ?: fullRoom.keyframeUrl,
        areaName = homepageRoom.areaName ?: fullRoom.areaName,
        parentAreaName = homepageRoom.parentAreaName ?: fullRoom.parentAreaName,
        parentAreaId = homepageRoom.parentAreaId.takeIf { it > 0 } ?: fullRoom.parentAreaId,
        areaId = homepageRoom.areaId.takeIf { it > 0 } ?: fullRoom.areaId,
        watchedText = homepageRoom.watchedText ?: fullRoom.watchedText,
      )
    }
    return mergedHomepageRooms + fullRooms.filterNot { it.roomId in homepageRoomIds }
  }

  /**
   * 为缺失封面的关注房间逐间补拉 [getRoomInfo]，用接口数据填充封面与分区字段。
   *
   * 完整关注列表接口偶尔不返回封面，这里仅在封面确实为空时才额外请求一次，避免无谓的
   * 网络开销；补拉失败则原样保留该房间。
   */
  private fun enrichMissingFollowedCovers(rooms: List<LiveSearchRoom>): List<LiveSearchRoom> =
    rooms.map { room ->
      if (!room.coverUrl.isNullOrBlank()) return@map room
      val info = runCatching { getRoomInfo(room.roomId) }.getOrNull() ?: return@map room
      room.copy(
        shortRoomId = room.shortRoomId ?: info.shortRoomId,
        uid = room.uid.takeIf { it > 0L } ?: info.anchorUid,
        coverUrl = info.coverUrl,
        keyframeUrl = room.keyframeUrl ?: info.keyframeUrl,
        areaName = room.areaName ?: info.areaName,
        parentAreaName = room.parentAreaName ?: info.parentAreaName,
        parentAreaId = room.parentAreaId.takeIf { it > 0 } ?: info.parentAreaId,
        areaId = room.areaId.takeIf { it > 0 } ?: info.areaId,
      )
    }

  /**
   * 分页拉取完整关注直播列表。
   *
   * 以接口报告的在线数 reportedCount 作为主要停止条件：翻页直到收集满报告数量、遇到短页
   * 或重复房间，最多翻 [MAX_FOLLOWING_PAGES] 页，防止接口数量口径不一致时无限翻页。
   */
  private fun getAllFollowedLiveRooms(expectedCount: Int): List<LiveSearchRoom> {
    val rooms = mutableListOf<LiveSearchRoom>()
    val seenRoomIds = hashSetOf<Long>()
    var page = 1
    var reportedCount = expectedCount.coerceAtLeast(0)
    while (page <= MAX_FOLLOWING_PAGES) {
      val json =
        getJson(
          "https://api.live.bilibili.com/xlive/web-ucenter/v1/xfetter/GetWebList?page=$page",
          "完整关注直播",
        )
      reportedCount = maxOf(reportedCount, parseFollowedLiveOnlineCount(json))
      val pageRooms = parseFollowedLiveRooms(json)
      val added = pageRooms.filter { seenRoomIds.add(it.roomId) }
      rooms += added
      // 报告数量已知时按数量收尾；未知时以「短页」判断是否已到最后一页。
      val reachedReportedEnd = reportedCount > 0 && rooms.size >= reportedCount
      val reachedShortPage = reportedCount <= 0 && pageRooms.size < FOLLOWING_PAGE_SIZE
      if (pageRooms.isEmpty() || added.isEmpty() || reachedReportedEnd || reachedShortPage) break
      page++
    }
    return rooms
  }

  /**
   * 按分区/页码获取直播间列表。
   *
   * 对入参做钳制（页码至少 1、每页 1~30），并按人气排序返回正在直播的房间。
   */
  fun getLiveRooms(
    parentAreaId: Int = 0,
    areaId: Int = 0,
    page: Int = 1,
    pageSize: Int = 30,
  ): LiveSearchResponse {
    val safePage = page.coerceAtLeast(1)
    val safePageSize = pageSize.coerceIn(1, 30)
    val parameters =
      linkedMapOf(
        "platform" to "web",
        "parent_area_id" to parentAreaId.coerceAtLeast(0).toString(),
        "area_id" to areaId.coerceAtLeast(0).toString(),
        "page" to safePage.toString(),
        "page_size" to safePageSize.toString(),
        "sort_type" to "online",
      )
    // `second/getList` 在本应用的普通直播首页会话里会返回 -352。共享 HTTP 拦截器会对该
    // 响应正确弹出全局 Gaia 验证，但下面的兼容接口已经提供相同的房间卡片数据契约，因此
    // 不要先探测被拦截的接口：否则卡片会经由回退接口加载，同时页面上还会残留一个多余、
    // 有时甚至空白的验证弹窗。
    val query = buildQuery(parameters + ("tag_version" to "1"))
    val json =
      getJson(
        "https://api.live.bilibili.com/room/v3/area/getRoomList?$query",
        "直播列表",
      )
    return parseLiveRoomList(json, safePage, safePageSize)
  }

  fun searchRooms(keyword: String, page: Int = 1): LiveSearchResponse {
    val query =
      BiliApiCommon.signedQuery(
        linkedMapOf(
          "search_type" to "live_room",
          "keyword" to keyword,
          "page" to page.coerceAtLeast(1).toString(),
          "order" to "online",
        )
      )
    val json = getJson("https://api.bilibili.com/x/web-interface/wbi/search/type?$query", "直播搜索")
    val data = json.optJSONObject("data") ?: return LiveSearchResponse(emptyList(), false)
    val candidates = mutableListOf<JSONObject>()
    collectLiveSearchCandidates(data.opt("result"), candidates)
    val rooms =
      candidates
        .mapNotNull(::parseLiveSearchRoom)
        .filter { it.roomId > 0L }
        .distinctBy { it.roomId }
    val requestedPage = page.coerceAtLeast(1)
    val totalPages = data.optInt("numPages", data.optInt("num_pages", 0))
    return LiveSearchResponse(
      rooms = rooms,
      hasMore = if (totalPages > 0) requestedPage < totalPages else rooms.size >= 20,
    )
  }

  fun getRoomInfo(entryRoomId: Long): LiveRoomInfo {
    require(entryRoomId > 0L) { "直播间号无效" }
    val json =
      getJson(
        "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$entryRoomId",
        "直播间信息",
      )
    val data = json.optJSONObject("data") ?: throw IllegalStateException("直播间不存在")
    val realRoomId = data.longValue("room_id").takeIf { it > 0L } ?: entryRoomId
    return LiveRoomInfo(
      roomId = realRoomId,
      shortRoomId = data.longValue("short_id").takeIf { it > 0L },
      anchorUid = data.longValue("uid"),
      title = decodeHtml(data.optString("title")).ifBlank { "直播间 $realRoomId" },
      description = decodeHtml(data.optString("description", data.optString("notice"))).trim(),
      coverUrl =
        imageUrl(data.optString("user_cover"))
          .ifBlank {
            imageUrl(data.optString("background"))
          }
          .ifBlank { null },
      keyframeUrl = imageUrl(data.optString("keyframe")).ifBlank { null },
      areaName = data.optString("area_name").takeIf(String::isNotBlank),
      parentAreaName = data.optString("parent_area_name").takeIf(String::isNotBlank),
      parentAreaId = data.intValue("parent_area_id", "area_v2_parent_id"),
      areaId = data.intValue("area_id", "area_v2_id"),
      liveStatus = data.optInt("live_status"),
      online = data.longValue("online"),
    )
  }

  fun getAnchorInfo(uid: Long): LiveAnchorInfo {
    if (uid <= 0L) return LiveAnchorInfo(0L, "主播", null)
    val json =
      getJson(
        "https://api.live.bilibili.com/live_user/v1/Master/info?uid=$uid",
        "主播信息",
      )
    val info = json.optJSONObject("data")?.optJSONObject("info")
    return LiveAnchorInfo(
      uid = uid,
      name = info?.optString("uname")?.takeIf(String::isNotBlank) ?: "主播",
      faceUrl = info?.optString("face")?.let(::imageUrl)?.takeIf(String::isNotBlank),
    )
  }

  fun getPlayInfo(roomId: Long, qn: Int = 10_000): LivePlayInfo {
    val safeQn = qn.takeIf { it > 0 }?.coerceAtMost(LIVE_ORIGINAL_QN) ?: LIVE_ORIGINAL_QN
    // 官方网页直播播放器用 WBI 为房间播放请求签名。除了让请求符合受支持的网页契约，
    // 这还能让后端把认证过的播放会话与账号关联起来；此前未签名的精简请求只能取回
    // 流地址，且无法可靠地生成云端直播历史记录。
    val query = BiliApiCommon.signedQuery(playInfoRequestParameters(roomId, safeQn))
    val json =
      getJson(
        "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?$query",
        "直播播放地址",
      )
    val playurl =
      json.optJSONObject("data")?.optJSONObject("playurl_info")?.optJSONObject("playurl")
        ?: throw IllegalStateException("当前直播间没有可播放地址")
    val qualities =
      playurl
        .optJSONArray("g_qn_desc")
        .objects()
        .mapNotNull { item ->
          val value = item.optInt("qn")
          value
            .takeIf { it in 1..LIVE_ORIGINAL_QN }
            ?.let { LiveQuality(it, item.optString("desc").ifBlank { "清晰度 $it" }) }
        }
        .filterNot { quality ->
          listOf("杜比", "dolby", "hdr", "4k", "8k", "全景", "vr").any { label ->
            quality.description.contains(label, ignoreCase = true)
          }
        }
    val sources = mutableListOf<LiveStreamSource>()
    var actualQn = 0
    playurl.optJSONArray("stream").objects().forEach { stream ->
      val protocol = stream.optString("protocol_name")
      stream.optJSONArray("format").objects().forEach { format ->
        val formatName = format.optString("format_name")
        format.optJSONArray("codec").objects().forEach { codec ->
          val codecName = codec.optString("codec_name")
          val currentQn = codec.optInt("current_qn")
          if (actualQn <= 0 && currentQn > 0) actualQn = currentQn
          val baseUrl = codec.optString("base_url")
          if (baseUrl.isBlank()) return@forEach
          val streamFormat =
            when {
              protocol.contains("hls", ignoreCase = true) &&
                formatName.contains("fmp4", ignoreCase = true) -> LiveStreamFormat.HLS_FMP4
              protocol.contains("hls", ignoreCase = true) -> LiveStreamFormat.HLS_TS
              protocol.contains("http", ignoreCase = true) ||
                formatName.contains("flv", ignoreCase = true) -> LiveStreamFormat.HTTP_FLV
              else -> return@forEach
            }
          codec.optJSONArray("url_info").objects().forEachIndexed { cdnIndex, urlInfo ->
            val url = urlInfo.optString("host") + baseUrl + urlInfo.optString("extra")
            if (url.startsWith("http")) {
              sources += LiveStreamSource(url, streamFormat, codecName, cdnIndex)
            }
          }
        }
      }
    }
    val ordered = orderLiveSources(sources)
    if (ordered.isEmpty()) throw IllegalStateException("当前直播流格式暂不受支持")
    return LivePlayInfo(
      requestedQn = safeQn,
      currentQn = (actualQn.takeIf { it > 0 } ?: safeQn).coerceAtMost(LIVE_ORIGINAL_QN),
      qualities = qualities.distinctBy { it.qn }.sortedByDescending { it.qn },
      sources = ordered,
    )
  }

  internal fun orderLiveSources(sources: List<LiveStreamSource>): List<LiveStreamSource> =
    sources
      .distinctBy { it.url }
      .sortedWith(
        compareBy<LiveStreamSource>(
          { if (it.codec.contains("avc", ignoreCase = true)) 0 else 1 },
          { it.cdnIndex },
          {
            when (it.format) {
              LiveStreamFormat.HLS_FMP4 -> 0
              LiveStreamFormat.HLS_TS -> 1
              LiveStreamFormat.HTTP_FLV -> 2
            }
          },
        )
      )

  fun getDanmuConfig(roomId: Long): LiveDanmuConfig {
    val query = BiliApiCommon.signedQuery(mapOf("id" to roomId.toString(), "type" to "0"))
    val json =
      getJson(
        "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo?$query",
        "直播消息配置",
      )
    val data = json.optJSONObject("data") ?: throw IllegalStateException("直播消息配置为空")
    val endpoints =
      data.optJSONArray("host_list").objects().mapNotNull { item ->
        val host = item.optString("host")
        val port = item.optInt("wss_port", 443)
        host.takeIf(String::isNotBlank)?.let { LiveDanmuEndpoint(it, port) }
      }
    if (endpoints.isEmpty()) throw IllegalStateException("没有可用的直播消息节点")
    return LiveDanmuConfig(data.optString("token"), endpoints)
  }

  fun getEmojiPacks(roomId: Long): List<LiveEmojiPack> {
    val json =
      getJson(
        "https://api.live.bilibili.com/xlive/web-ucenter/v2/emoticon/GetEmoticons" +
          "?platform=pc&room_id=$roomId",
        "直播表情",
      )
    return parseEmojiPacks(json, roomId)
  }

  internal fun parseEmojiPacks(json: JSONObject, roomId: Long): List<LiveEmojiPack> {
    val data = json.optJSONObject("data")
    val packages = data?.optJSONArray("data") ?: data?.optJSONArray("packages")
    return packages.objects().mapNotNull { pack ->
      val packId = pack.optString("pkg_id", pack.optString("id"))
      val emojisArray = pack.optJSONArray("emoticons") ?: pack.optJSONArray("emojis")
      val rawEmojis = emojisArray.objects()
      if (rawEmojis.isEmpty()) return@mapNotNull null
      val roomExclusive = rawEmojis.any {
        it.optString("emoticon_unique").startsWith("room_${roomId}_")
      }
      val kind =
        when {
          roomExclusive -> LiveEmojiKind.ROOM_EXCLUSIVE
          packId == "1" || pack.optInt("pkg_type") == 0 -> LiveEmojiKind.BASE
          else -> LiveEmojiKind.OWNED
        }
      val emojis = rawEmojis.mapNotNull { item ->
        val uniqueToken = item.optString("emoticon_unique")
        val inputText = item.optString("emoji")
        if (uniqueToken.isBlank() && inputText.isBlank()) return@mapNotNull null
        val sendToken = uniqueToken.ifBlank { inputText }
        val url = imageUrl(item.optString("url"))
        val permitted = item.optInt("perm", 1) != 0
        val isBulge = item.optInt("bulge_display", if (roomExclusive) 1 else 0) != 0
        LiveEmoji(
          displayName =
            item.optString("descript").ifBlank {
              inputText.ifBlank { sendToken }
            },
          inputText = inputText.ifBlank { sendToken },
          sendToken = sendToken,
          fileId = uniqueToken.takeIf(String::isNotBlank),
          imageUrl = url,
          kind = kind,
          roomId = roomId.takeIf { kind == LiveEmojiKind.ROOM_EXCLUSIVE },
          // GetEmoticons 返回的每一项都是真实的图片表情。把 BASE 条目插入到发送面板
          // 会走 dm_type=0 路径，并把其标签当作纯文本发送。
          directSend = true,
          isBulge = isBulge,
          available = permitted,
          unavailableReason =
            if (permitted) null else item.optString("unlock_show_text").ifBlank { "暂不可用" },
        )
      }
      LiveEmojiPack(
        id = packId.ifBlank { "${kind.name}:${pack.optString("pkg_name")}" },
        title = pack.optString("pkg_name").takeIf(String::isNotBlank),
        iconUrl = imageUrl(pack.optString("current_cover")).takeIf(String::isNotBlank),
        kind = kind,
        roomId = roomId.takeIf { kind == LiveEmojiKind.ROOM_EXCLUSIVE },
        emojis = emojis,
      )
    }
  }

  fun getActiveMedal(roomId: Long, anchorUid: Long): FanMedalBadge? {
    if (BiliHttpClient.cookieValue("SESSDATA").isNullOrBlank()) return null
    val json =
      getJson(
        "https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByUser?room_id=$roomId",
        "直播互动信息",
      )
    val data = json.optJSONObject("data")
    val property = data?.optJSONObject("property")
    val medal =
      property?.optJSONObject("medal")
        ?: data?.optJSONObject("medal")
        ?: data?.optJSONObject("medal_info")
    return parseMedal(medal)?.takeIf {
      it.anchorUid == null || it.anchorUid == anchorUid
    }
  }

  fun getInteractiveLottery(roomId: Long): LiveInteractiveLottery? {
    if (roomId <= 0L) return null
    val json =
      getJson(
        "https://api.live.bilibili.com/xlive/lottery-interface/v1/Anchor/Check?roomid=$roomId",
        "互动抽奖",
      )
    val data = json.opt("data")
    val lottery =
      when (data) {
        is JSONObject -> {
          data.takeIf { it.optLong("id") > 0L } ?: data.optJSONArray("anchor")?.optJSONObject(0)
        }
        is JSONArray -> data.optJSONObject(0)
        else -> null
      }
    return lottery?.let(::parseInteractiveLottery)
  }

  fun joinInteractiveLottery(lotteryId: Long) {
    require(lotteryId > 0L) { "互动抽奖已失效" }
    val csrf =
      BiliHttpClient.cookieValue("bili_jct")?.takeIf(String::isNotBlank)
        ?: throw IllegalStateException("请先登录后再参与抽奖")
    val response =
      BiliHttpClient.postForm(
        "https://api.live.bilibili.com/xlive/lottery-interface/v1/Anchor/Join",
        mapOf(
          "id" to lotteryId.toString(),
          "platform" to "pc",
          "csrf" to csrf,
          "csrf_token" to csrf,
        ),
        headers =
          mapOf(
            "Referer" to "https://live.bilibili.com/",
            "Origin" to "https://live.bilibili.com",
          ),
      )
    val body = response.body?.string().orEmpty()
    response.close()
    val json = JSONObject(body)
    if (json.optInt("code") != 0) {
      throw IllegalStateException(json.optString("message").ifBlank { "参与抽奖失败" })
    }
  }

  fun getAudienceRank(
    roomId: Long,
    anchorUid: Long,
    type: String,
    switch: String,
  ): LiveAudienceRank {
    val query =
      buildQuery(
        mapOf(
          "ruid" to anchorUid.toString(),
          "room_id" to roomId.toString(),
          "page" to "1",
          "page_size" to "100",
          "type" to type,
          "switch" to switch,
          "platform" to "web",
        )
      )
    val json =
      getJson(
        "https://api.live.bilibili.com/xlive/general-interface/v1/rank/queryContributionRank?$query",
        "房间观众",
      )
    val data = json.optJSONObject("data") ?: return LiveAudienceRank("", null, emptyList())
    return LiveAudienceRank(
      countText = data.optString("count_text"),
      valueText = data.optJSONObject("config")?.optString("value_text")?.takeIf(String::isNotBlank),
      items =
        data
          .optJSONArray("item")
          .objects()
          .mapNotNull { parseRankUser(it, anchorUid) }
          .distinctBy {
            it.uid
          },
    )
  }

  fun getGuardRank(
    roomId: Long,
    anchorUid: Long,
    page: Int,
    pageSize: Int = 30,
    type: Int = 4,
  ): LiveGuardRankPage {
    val query =
      buildQuery(
        mapOf(
          "roomid" to roomId.toString(),
          "ruid" to anchorUid.toString(),
          "page" to page.coerceAtLeast(1).toString(),
          "page_size" to pageSize.coerceIn(1, 30).toString(),
          "typ" to type.toString(),
        )
      )
    val json =
      getJson(
        "https://api.live.bilibili.com/xlive/app-room/v2/guardTab/topListNew?$query",
        "大航海",
      )
    val data = json.optJSONObject("data") ?: throw IllegalStateException("大航海数据为空")
    val info = data.optJSONObject("info")
    return LiveGuardRankPage(
      totalCount = info?.optInt("num") ?: 0,
      totalPageHint = info?.optInt("page")?.takeIf { it > 0 },
      currentPage = info?.optInt("now", page) ?: page,
      actualType = data.optInt("typ", type),
      top3 =
        data
          .optJSONArray("top3")
          .objects()
          .mapNotNull { parseRankUser(it, anchorUid) }
          .distinctBy {
            it.uid
          },
      items =
        data
          .optJSONArray("list")
          .objects()
          .mapNotNull { parseRankUser(it, anchorUid) }
          .distinctBy {
            it.uid
          },
    )
  }

  fun sendTextDanmaku(roomId: Long, text: String) {
    val content = text.trim()
    require(content.isNotEmpty()) { "弹幕不能为空" }
    sendDanmaku(
      roomId = roomId,
      fields =
        mapOf(
          "msg" to content,
          "color" to "16777215",
          "mode" to "1",
          "fontsize" to "25",
          "dm_type" to "0",
        ),
    )
  }

  fun sendEmoji(roomId: Long, emoji: LiveEmoji) {
    require(emoji.available) { emoji.unavailableReason ?: "该表情暂不可用" }
    if (emoji.kind == LiveEmojiKind.ROOM_EXCLUSIVE) {
      require(emoji.roomId == roomId) { "这个专属表情不属于当前直播间" }
    }
    sendDanmaku(
      roomId = roomId,
      fields = emojiDanmakuFields(emoji),
    )
  }

  internal fun emojiDanmakuFields(emoji: LiveEmoji): Map<String, String> {
    val options =
      if (!emoji.isBulge) {
        "{}"
      } else {
        JSONObject()
          .put("bulge_display", 1)
          .put("emoticon_unique", emoji.fileId ?: emoji.sendToken)
          .put("in_player_area", 1)
          .put("is_dynamic", 1)
          .put("url", emoji.imageUrl)
          .toString()
      }
    return mapOf(
      "msg" to emoji.sendToken,
      "color" to "16777215",
      "mode" to "1",
      "fontsize" to "25",
      "dm_type" to "1",
      "emoticon_options" to options,
    )
  }

  private fun sendDanmaku(roomId: Long, fields: Map<String, String>) {
    val csrf =
      BiliHttpClient.cookieValue("bili_jct")?.takeIf(String::isNotBlank)
        ?: throw IllegalStateException("请先登录后再发送弹幕")
    val response =
      BiliHttpClient.postForm(
        "https://api.live.bilibili.com/msg/send",
        fields +
          mapOf(
            "roomid" to roomId.toString(),
            "rnd" to (System.currentTimeMillis() / 1_000L).toString(),
            "csrf" to csrf,
            "csrf_token" to csrf,
            "bubble" to "0",
          ),
        headers =
          mapOf(
            "Referer" to "https://live.bilibili.com/$roomId",
            "Origin" to "https://live.bilibili.com",
          ),
      )
    val body = response.body?.string().orEmpty()
    response.close()
    val json = JSONObject(body)
    if (json.optInt("code") != 0) {
      throw IllegalStateException(json.optString("message").ifBlank { "发送失败" })
    }
  }

  private fun collectLiveSearchCandidates(value: Any?, destination: MutableList<JSONObject>) {
    when (value) {
      is JSONArray -> {
        for (index in 0 until value.length()) {
          collectLiveSearchCandidates(value.opt(index), destination)
        }
      }
      is JSONObject -> {
        if (value.longValue("roomid", "room_id", "roomid_str") > 0L) {
          destination += value
          return
        }
        val preferredKeys = listOf("live_room", "room", "data", "result")
        preferredKeys.forEach { key ->
          value.opt(key)?.let { nested -> collectLiveSearchCandidates(nested, destination) }
        }
      }
    }
  }

  internal fun parseLiveAreas(json: JSONObject): List<LiveAreaFilter> =
    parseLiveAreaGroups(json).map { it.parent }

  internal fun parseLiveAreaGroups(json: JSONObject): List<LiveAreaGroup> =
    json
      .optJSONArray("data")
      .objects()
      .mapNotNull { item ->
        val parentAreaId = item.optInt("id")
        val name = decodeHtml(item.optString("name")).trim()
        if (parentAreaId <= 0 || name.isBlank()) {
          null
        } else {
          val parent =
            LiveAreaFilter(
              parentAreaId = parentAreaId,
              name = name,
              iconUrl =
                imageUrl(item.optString("pic", item.optString("icon"))).takeIf(String::isNotBlank),
            )
          val children =
            item
              .optJSONArray("list")
              .objects()
              .mapNotNull { child ->
                val areaId = child.optInt("id")
                val childName = decodeHtml(child.optString("name")).trim()
                if (areaId <= 0 || childName.isBlank()) {
                  null
                } else {
                  LiveAreaFilter(
                    parentAreaId =
                      child.optInt("parent_id", parentAreaId).takeIf { it > 0 } ?: parentAreaId,
                    areaId = areaId,
                    name = childName,
                    iconUrl =
                      imageUrl(child.optString("pic", child.optString("icon")))
                        .takeIf(String::isNotBlank),
                  )
                }
              }
              .distinctBy(LiveAreaFilter::stableId)
          LiveAreaGroup(parent = parent, children = children)
        }
      }
      .distinctBy { it.parent.parentAreaId }

  internal fun parseLiveHomeRecommendations(json: JSONObject): List<LiveSearchRoom> {
    val data = json.optJSONObject("data") ?: return emptyList()
    val list =
      data.optJSONArray("recommend_room_list")
        ?: data.optJSONArray("recommend_list")
        ?: data.optJSONArray("list")
        ?: return emptyList()
    return list
      .objects()
      .mapNotNull(::parseLiveSearchRoom)
      .filter { it.roomId > 0L && it.liveStatus == 1 }
      .distinctBy(LiveSearchRoom::roomId)
  }

  internal fun parseFollowedLiveRooms(json: JSONObject): List<LiveSearchRoom> {
    val data = json.optJSONObject("data") ?: return emptyList()
    val homepageModules = data.optJSONArray("room_list")
    val moduleObjects = homepageModules?.objects().orEmpty()
    val followModule = moduleObjects.firstOrNull {
      it.optJSONObject("module_info")?.optInt("id") == 13
    }
    val hasHomepageModules = moduleObjects.any { it.has("module_info") }
    val list =
      when {
        followModule != null -> followModule.optJSONArray("list")
        hasHomepageModules -> null
        else -> data.optJSONArray("rooms") ?: data.optJSONArray("list") ?: homepageModules
      } ?: return emptyList()
    return list
      .objects()
      .filter(::isLiveFollowingRoom)
      .mapNotNull(::parseLiveSearchRoom)
      .filter { it.roomId > 0L }
      .distinctBy(LiveSearchRoom::roomId)
  }

  internal fun parseFollowedLiveOnlineCount(json: JSONObject): Int {
    val data = json.optJSONObject("data") ?: return 0
    val followModule =
      data.optJSONArray("room_list").objects().firstOrNull {
        it.optJSONObject("module_info")?.optInt("id") == 13
      }
    return followModule?.optJSONObject("extra")?.optInt("follow_Online")?.coerceAtLeast(0)
      ?: data.optInt("count", 0).coerceAtLeast(0)
  }

  private fun isLiveFollowingRoom(item: JSONObject): Boolean {
    val status = item.opt("status")
    return when (status) {
      is Boolean -> status
      is Number -> status.toInt() != 0
      is String -> status == "1" || status.equals("true", ignoreCase = true)
      else -> item.optInt("live_status", 1) == 1
    }
  }

  internal fun parseLiveRoomList(
    json: JSONObject,
    page: Int,
    pageSize: Int,
  ): LiveSearchResponse {
    val data = json.optJSONObject("data") ?: return LiveSearchResponse(emptyList(), false)
    val rooms =
      data
        .optJSONArray("list")
        .objects()
        .mapNotNull(::parseLiveSearchRoom)
        .filter { it.roomId > 0L && it.liveStatus == 1 }
        .distinctBy(LiveSearchRoom::roomId)
    val total = data.longValue("count")
    val safePage = page.coerceAtLeast(1)
    val safePageSize = pageSize.coerceAtLeast(1)
    return LiveSearchResponse(
      rooms = rooms,
      hasMore =
        rooms.isNotEmpty() &&
          if (total > 0L) safePage.toLong() * safePageSize < total else rooms.size >= safePageSize,
    )
  }

  private fun parseLiveSearchRoom(item: JSONObject): LiveSearchRoom? {
    val roomId = item.longValue("roomid", "room_id", "roomid_str")
    if (roomId <= 0L) return null
    val watched =
      item
        .optJSONObject("watched_show")
        ?.optString("text_large")
        ?.ifBlank { item.optJSONObject("watched_show")?.optString("text_small") }
        ?.takeIf(String::isNotBlank) ?: formatLivePopularity(item.longValue("online"))
    return LiveSearchRoom(
      roomId = roomId,
      shortRoomId = item.longValue("short_id", "short_room_id").takeIf { it > 0L },
      uid = item.longValue("uid", "mid"),
      title = decodeHtml(item.optString("title")).ifBlank { "直播间 $roomId" },
      uname = item.optString("uname", item.optString("name")).ifBlank { "主播" },
      faceUrl = firstImageUrl(item, "uface", "face", "face_url"),
      coverUrl =
        firstImageUrl(
          item,
          "user_cover",
          "cover_from_user",
          "cover",
          "room_cover",
          "cover_url",
        ),
      keyframeUrl = firstImageUrl(item, "keyframe", "keyframe_url", "system_cover", "pic"),
      areaName =
        item.optString("area_v2_name", item.optString("area_name")).takeIf(String::isNotBlank),
      parentAreaName =
        item
          .optString("area_v2_parent_name", item.optString("parent_area_name"))
          .takeIf(String::isNotBlank),
      parentAreaId = item.intValue("area_v2_parent_id", "parent_area_id"),
      areaId = item.intValue("area_v2_id", "area_id"),
      watchedText = watched,
      liveStatus = item.optInt("live_status", 1),
    )
  }

  private fun firstImageUrl(item: JSONObject, vararg keys: String): String? =
    keys
      .asSequence()
      .map { key -> item.optString(key).trim() }
      .filter { value -> value.isNotBlank() && !value.equals("null", ignoreCase = true) }
      .map(::imageUrl)
      .firstOrNull(String::isNotBlank)

  internal fun formatLivePopularity(value: Long): String? =
    when {
      value <= 0L -> null
      value >= 100_000_000L -> "${formatCompact(value / 100_000_000.0)}亿人气"
      value >= 10_000L -> "${formatCompact(value / 10_000.0)}万人气"
      else -> "${value}人气"
    }

  private fun formatCompact(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
  }

  private fun parseRankUser(item: JSONObject, anchorUid: Long): LiveRankUser? {
    val userInfo = item.optJSONObject("uinfo")
    val base = userInfo?.optJSONObject("base")
    val uid =
      userInfo?.longValue("uid")?.takeIf { it > 0L }
        ?: item.longValue("uid").takeIf { it > 0L }
        ?: return null
    val medalObject = userInfo?.optJSONObject("medal") ?: item.optJSONObject("medal_info")
    val medal =
      parseMedal(medalObject)?.takeIf { it.anchorUid == null || it.anchorUid == anchorUid }
    val guardLevel =
      userInfo?.optJSONObject("guard")?.optInt("level")?.takeIf { it > 0 }
        ?: item.optInt("guard_level").takeIf { it > 0 }
    return LiveRankUser(
      uid = uid,
      rank = item.optInt("rank"),
      name =
        base?.optString("name")?.takeIf(String::isNotBlank)
          ?: item.optString("name").ifBlank { "神秘人" },
      faceUrl =
        imageUrl(base?.optString("face").orEmpty())
          .ifBlank {
            imageUrl(item.optString("face"))
          }
          .takeIf(String::isNotBlank),
      score = item.longValue("score").takeIf { it != 0L },
      accompanyDays = item.optInt("accompany").takeIf { it > 0 },
      fanMedal = medal,
      guardLevel = guardLevel,
    )
  }

  internal fun parseMedal(value: JSONObject?): FanMedalBadge? {
    value ?: return null
    val name = value.optString("name", value.optString("medal_name"))
    val level = value.optInt("level", value.optInt("medal_level"))
    if (name.isBlank() || level <= 0) return null
    return FanMedalBadge(
      name = name,
      level = level,
      anchorUid = value.longValue("ruid", "target_id").takeIf { it > 0L },
      color = value.longValue("color", "medal_color").takeIf { it > 0L },
      borderColor = value.longValue("border_color").takeIf { it > 0L },
      startColor = value.longValue("start_color").takeIf { it > 0L },
      endColor = value.longValue("end_color").takeIf { it > 0L },
    )
  }

  internal fun parseInteractiveLottery(value: JSONObject): LiveInteractiveLottery? {
    val id = value.longValue("id")
    if (id <= 0L) return null
    val currentSeconds =
      value.longValue("current_time").takeIf { it > 0L } ?: System.currentTimeMillis() / 1_000L
    val remainingSeconds = value.longValue("time").coerceAtLeast(0L)
    val joined = value.optInt("status") == 2
    return LiveInteractiveLottery(
      id = id,
      roomId = value.longValue("room_id", "roomid"),
      awardName = value.optString("award_name").ifBlank { "互动抽奖" },
      awardNum = value.optInt("award_num", 1).coerceAtLeast(1),
      awardImageUrl = imageUrl(value.optString("award_image")).takeIf(String::isNotBlank),
      command = value.optString("danmu"),
      requireText = value.optString("require_text").ifBlank { "发送指定弹幕参与" },
      requireType = value.optInt("require_type"),
      requireValue = value.optInt("require_value"),
      giftId = value.longValue("gift_id"),
      giftNum = value.optInt("gift_num"),
      giftPrice = value.longValue("gift_price"),
      sendGiftEnsure = value.optInt("send_gift_ensure") != 0,
      endAtEpochMs = (currentSeconds + remainingSeconds) * 1_000L,
      status = if (joined) LiveLotteryStatus.JOINED else LiveLotteryStatus.ACTIVE,
    )
  }

  internal fun playInfoRequestParameters(roomId: Long, qn: Int): Map<String, String> =
    linkedMapOf(
      "room_id" to roomId.toString(),
      "protocol" to "0,1",
      "format" to "0,1,2",
      "codec" to "0,1,2",
      "qn" to qn.coerceIn(1, LIVE_ORIGINAL_QN).toString(),
      "platform" to "web",
      "ptype" to "8",
      "dolby" to "5",
      "panorama" to "1",
      "eotf" to "0,1,2",
      "req_reason" to "0",
      "supported_drms" to "0,1,2,3",
    )

  private fun getJson(url: String, label: String): JSONObject {
    val response =
      BiliHttpClient.get(
        url,
        headers =
          if (url.startsWith("https://api.live.bilibili.com/")) {
            mapOf(
              "Referer" to "https://live.bilibili.com/",
              "Origin" to "https://live.bilibili.com",
            )
          } else {
            emptyMap()
          },
      )
    val body = response.body?.string().orEmpty()
    response.close()
    val json = JSONObject(body)
    if (json.optInt("code") != 0) {
      throw IllegalStateException("$label：${json.optString("message").ifBlank { "请求失败" }}")
    }
    return json
  }

  private fun buildQuery(params: Map<String, String>): String =
    params.entries.joinToString("&") { (key, value) ->
      "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }

  private fun decodeHtml(value: String): String =
    HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

  private fun imageUrl(value: String): String = UrlPolicy.normalizeImageUrl(value).orEmpty()

  private fun JSONObject.longValue(vararg names: String): Long {
    for (name in names) {
      val raw = opt(name) ?: continue
      val value =
        when (raw) {
          is Number -> raw.toLong()
          is String -> raw.toLongOrNull()
          else -> null
        }
      if (value != null) return value
    }
    return 0L
  }

  private fun JSONObject.intValue(vararg names: String): Int =
    longValue(*names).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

  private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
      for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }
  }

  private const val HOMEPAGE_FOLLOWING_LIMIT = 6
  private const val FOLLOWING_PAGE_SIZE = 10
  private const val MAX_FOLLOWING_PAGES = 20
}

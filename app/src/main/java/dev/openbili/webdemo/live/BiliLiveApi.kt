package dev.openbili.webdemo.live

import android.text.Html
import dev.openbili.webdemo.UrlPolicy
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.BiliHttpClient
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

object BiliLiveApi {
  fun searchRooms(keyword: String, page: Int = 1): LiveSearchResponse {
    val query =
      BiliApi.signedQuery(
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
    // The official web live player signs the room play request with WBI. Besides making the
    // request match the supported web contract, this lets the backend associate an authenticated
    // playback session with the account; the former unsigned, reduced request only fetched a
    // stream URL and did not reliably create the cloud live-history entry.
    val query = BiliApi.signedQuery(playInfoRequestParameters(roomId, safeQn))
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
          codec.optJSONArray("url_info").objects().forEach { urlInfo ->
            val url = urlInfo.optString("host") + baseUrl + urlInfo.optString("extra")
            if (url.startsWith("http")) {
              sources += LiveStreamSource(url, streamFormat, codecName)
            }
          }
        }
      }
    }
    val ordered =
      sources
        .distinctBy { it.url }
        .sortedWith(
          compareBy<LiveStreamSource>(
            {
              when (it.format) {
                LiveStreamFormat.HLS_FMP4 -> 0
                LiveStreamFormat.HLS_TS -> 1
                LiveStreamFormat.HTTP_FLV -> 3
              }
            },
            { if (it.codec.contains("avc", ignoreCase = true)) 0 else 1 },
          )
        )
    if (ordered.isEmpty()) throw IllegalStateException("当前直播流格式暂不受支持")
    return LivePlayInfo(
      requestedQn = safeQn,
      currentQn = (actualQn.takeIf { it > 0 } ?: safeQn).coerceAtMost(LIVE_ORIGINAL_QN),
      qualities = qualities.distinctBy { it.qn }.sortedByDescending { it.qn },
      sources = ordered,
    )
  }

  fun getDanmuConfig(roomId: Long): LiveDanmuConfig {
    val query = BiliApi.signedQuery(mapOf("id" to roomId.toString(), "type" to "0"))
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
        val token =
          item
            .optString("emoticon_unique")
            .ifBlank { item.optString("emoji") }
            .takeIf(String::isNotBlank) ?: return@mapNotNull null
        val url = imageUrl(item.optString("url"))
        val permitted = item.optInt("perm", 1) != 0
        val isBulge = item.optInt("bulge_display", if (roomExclusive) 1 else 0) != 0
        LiveEmoji(
          displayName =
            item.optString("descript").ifBlank {
              item.optString("emoji").ifBlank { token }
            },
          sendToken = token,
          fileId = item.optString("emoticon_unique").takeIf(String::isNotBlank),
          imageUrl = url,
          kind = kind,
          roomId = roomId.takeIf { kind == LiveEmojiKind.ROOM_EXCLUSIVE },
          directSend = roomExclusive || (kind != LiveEmojiKind.BASE && isBulge),
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
          data.takeIf { it.optLong("id") > 0L }
            ?: data.optJSONArray("anchor")?.optJSONObject(0)
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
    val options =
      JSONObject()
        .put("bulge_display", 1)
        .put("emoticon_unique", emoji.fileId ?: emoji.sendToken)
        .put("in_player_area", 1)
        .put("is_dynamic", 1)
        .put("url", emoji.imageUrl)
        .toString()
    sendDanmaku(
      roomId = roomId,
      fields =
        mapOf(
          "msg" to emoji.sendToken,
          "color" to "16777215",
          "mode" to "1",
          "fontsize" to "25",
          "dm_type" to "1",
          "emoticon_options" to options,
        ),
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

  private fun parseLiveSearchRoom(item: JSONObject): LiveSearchRoom? {
    val roomId = item.longValue("roomid", "room_id", "roomid_str")
    if (roomId <= 0L) return null
    val watched =
      item
        .optJSONObject("watched_show")
        ?.optString("text_large")
        ?.ifBlank { item.optJSONObject("watched_show")?.optString("text_small") }
        ?.takeIf(String::isNotBlank) ?: item.optString("online").takeIf(String::isNotBlank)
    return LiveSearchRoom(
      roomId = roomId,
      shortRoomId = item.longValue("short_id", "short_room_id").takeIf { it > 0L },
      uid = item.longValue("uid", "mid"),
      title = decodeHtml(item.optString("title")).ifBlank { "直播间 $roomId" },
      uname = item.optString("uname", item.optString("name")).ifBlank { "主播" },
      faceUrl =
        imageUrl(item.optString("uface", item.optString("face"))).takeIf(String::isNotBlank),
      coverUrl =
        imageUrl(item.optString("user_cover", item.optString("cover"))).takeIf(String::isNotBlank),
      keyframeUrl =
        imageUrl(item.optString("keyframe", item.optString("pic"))).takeIf(String::isNotBlank),
      areaName = item.optString("area_name").takeIf(String::isNotBlank),
      parentAreaName = item.optString("parent_area_name").takeIf(String::isNotBlank),
      watchedText = watched,
      liveStatus = item.optInt("live_status", 1),
    )
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
      value.longValue("current_time").takeIf { it > 0L }
        ?: System.currentTimeMillis() / 1_000L
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
    Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()

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

  private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
      for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }
  }
}

package dev.openbili.webdemo.api

/**
 * 私信接口。
 *
 * 覆盖未读数、会话列表、历史分页、消息用户信息、私信内容解析（文本/图片/视频/专栏/
 * JSON 文本数组）、文本与图片发送、撤回与已读回执。
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
 * 私信 API 集合与内容解析器。
 */
object BiliPrivateMessageApi {

  data class PrivateImageUpload(
    val url: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val sizeKb: Int,
  )

  /** 读取私信未读数。 */
  fun getPrivateMessageUnreadCount(): Int {
    val resp =
      BiliHttpClient.get(
        "https://api.vc.bilibili.com/session_svr/v1/session_svr/single_unread?" +
          "unread_type=0&show_unfollow_list=1&show_dustbin=0&build=0&mobi_app=web"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) {
      throw IllegalStateException(json.optString("message", json.optString("msg")))
    }
    return parsePrivateMessageUnreadCount(json)
  }

  internal fun parsePrivateMessageUnreadCount(json: JSONObject): Int {
    val data = json.optJSONObject("data") ?: json
    val visibleUnreadFields =
      listOf(
        "follow_unread",
        "unfollow_unread",
        "unfollow_push_msg",
        "biz_msg_follow_unread",
        "biz_msg_unfollow_unread",
        "custom_unread",
      )
    return visibleUnreadFields
      .sumOf { field -> data.optLong(field).coerceAtLeast(0L) }
      .coerceAtMost(Int.MAX_VALUE.toLong())
      .toInt()
  }

  /** 拉取最近会话列表。 */
  fun getPrivateMessages(): List<AccountMessage> {
    val result = mutableListOf<AccountMessage>()
    var endTimestamp = 0L
    var pageCount = 0
    do {
      val page = getPrivateMessageSessions(endTimestamp)
      result += page.items
      if (!page.hasMore || page.endTimestamp <= 0L || page.endTimestamp == endTimestamp) break
      endTimestamp = page.endTimestamp
      pageCount++
    } while (pageCount < 100)
    return result.distinctBy(AccountMessage::userMid)
  }

  /** 分页拉取会话历史消息。 */
  fun getPrivateMessageSessions(
    endTimestamp: Long = 0L,
    size: Int = 18,
  ): PrivateSessionPage {
    val pageSize = size.coerceIn(1, 100)
    val cursor = if (endTimestamp > 0L) "&end_ts=$endTimestamp" else ""
    val resp =
      BiliHttpClient.get(
        "https://api.vc.bilibili.com/session_svr/v1/session_svr/get_sessions?" +
          "session_type=1&group_fold=1&unfollow_fold=0&sort_rule=2&size=$pageSize" +
          "&build=0&mobi_app=web$cursor"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data = json.optJSONObject("data") ?: return PrivateSessionPage(emptyList())
    val array = data.optJSONArray("session_list") ?: return PrivateSessionPage(emptyList())
    val userIds = buildList {
      for (i in 0 until array.length()) {
        val row = array.optJSONObject(i) ?: continue
        val account = row.optJSONObject("account_info")
        if (row.optInt("system_msg_type") == 0 && account?.optString("name").isNullOrBlank()) {
          add(row.optLong("talker_id"))
        }
      }
    }
    val users = getMessageUsers(userIds)
    val items = buildList {
      for (i in 0 until array.length()) {
        val row = array.getJSONObject(i)
        val talker = row.optLong("talker_id")
        val last = row.optJSONObject("last_msg") ?: JSONObject()
        val parsed = parsePrivateContent(last.optInt("msg_type"), last.optString("content"))
        val account = row.optJSONObject("account_info")
        val user = users[talker]
        val notifier = parsed.notifier
        add(
          AccountMessage(
            id = talker,
            userMid = talker,
            userName =
              notifier?.first.orEmpty().ifBlank {
                account?.optString("name").orEmpty().ifBlank { user?.first ?: "UID $talker" }
              },
            userFace =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(
                  notifier?.second.orEmpty().ifBlank {
                    account?.optString("pic_url").orEmpty().ifBlank { user?.second.orEmpty() }
                  }
                )
                .orEmpty(),
            title = parsed.title.ifBlank { "私信会话" },
            content =
              if (last.optInt("msg_status") == 1 || last.optInt("msg_type") == 5) {
                if (last.optLong("sender_uid") != talker) "你撤回了一条消息" else "对方撤回了一条消息"
              } else parsed.content,
            sourceContent = "",
            oid = parsed.oid,
            rootId = 0,
            parentId = 0,
            time = row.optLong("session_ts") / 1_000_000L,
            coverUrl = parsed.coverUrl,
            linkUrl = parsed.linkUrl,
            messageType = last.optInt("msg_type"),
            isPrivate = true,
            targetKind = privateTargetKind(parsed.linkUrl, last.optInt("msg_type")),
            subjectTitle = parsed.title,
            senderMid = last.optLong("sender_uid"),
            receiverMid = last.optLong("receiver_id"),
            // 会话负载用的是 msg_seqno：读 seqno 会让每次已读回执都变成 0，
            // 本地红点消失但服务端仍把会话算作未读。
            sequence = last.optLong("msg_seqno", last.optLong("seqno")),
            messageKey = last.optLong("msg_key"),
            unreadCount = row.optInt("unread_count"),
            isOutgoing = last.optLong("sender_uid") != talker,
            withdrawn = last.optInt("msg_status") == 1 || last.optInt("msg_type") == 5,
            isPrivateNotice = parsed.noticeStyle,
            mediaWidth = parsed.mediaWidth,
            mediaHeight = parsed.mediaHeight,
          )
        )
      }
    }
    val nextTimestamp =
      (0 until array.length())
        .mapNotNull { index ->
          array.optJSONObject(index)?.optLong("session_ts")?.takeIf { it > 0L }
        }
        .minOrNull()
        ?.minus(1L) ?: 0L
    val hasMore =
      data.optBoolean("has_more") || data.optInt("has_more") == 1 || array.length() >= pageSize
    return PrivateSessionPage(items, nextTimestamp, hasMore && nextTimestamp > 0L)
  }

  fun getPrivateMessageHistory(
    talkerId: Long,
    accountMid: Long,
    endSequence: Long = 0L,
    size: Int = 15,
  ): PrivateMessagePage {
    require(talkerId > 0L) { "私信对象无效" }
    val pageSize = size.coerceIn(1, 50)
    val cursor = if (endSequence > 0L) "&end_seqno=$endSequence" else ""
    val resp =
      BiliHttpClient.get(
        "https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs?" +
          "talker_id=$talkerId&session_type=1&size=$pageSize&begin_seqno=0" +
          "&build=0&mobi_app=web$cursor"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data = json.optJSONObject("data") ?: return PrivateMessagePage(emptyList())
    val array =
      data.optJSONArray("messages")
        ?: data.optJSONArray("message_list")
        ?: return PrivateMessagePage(emptyList())
    val talker = getMessageUsers(listOf(talkerId))[talkerId]
    val items =
      buildList {
          for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val sender = row.optLong("sender_uid", row.optLong("sender_id"))
            val receiver = row.optLong("receiver_id")
            val type = row.optInt("msg_type")
            val parsed = parsePrivateContent(type, row.optString("content"))
            val seq = row.optLong("seqno", row.optLong("msg_seqno"))
            val outgoing = sender == accountMid
            val withdrawn = row.optInt("msg_status") == 1 || type == 5
            val timestamp =
              row.optLong("timestamp").takeIf { it > 0L } ?: (row.optLong("msg_key") / 1_000_000L)
            add(
              AccountMessage(
                id = seq.takeIf { it > 0L } ?: row.optLong("msg_key", timestamp),
                userMid = sender,
                userName = if (sender == accountMid) "我" else talker?.first ?: "UID $talkerId",
                userFace = if (sender == accountMid) "" else talker?.second.orEmpty(),
                title = parsed.title,
                content =
                  if (withdrawn) {
                    if (outgoing) "你撤回了一条消息" else "对方撤回了一条消息"
                  } else parsed.content,
                sourceContent = "",
                oid = parsed.oid,
                rootId = 0L,
                parentId = 0L,
                time = timestamp,
                coverUrl = parsed.coverUrl,
                linkUrl = normalizePrivateLink(parsed.linkUrl, type),
                messageType = type,
                isPrivate = true,
                targetKind = privateTargetKind(parsed.linkUrl, type),
                subjectTitle = parsed.title,
                senderMid = sender,
                receiverMid = receiver,
                sequence = seq,
                messageKey = row.optLong("msg_key"),
                isOutgoing = outgoing,
                withdrawn = withdrawn,
                withdrawTargetMessageKey =
                  when {
                    type == 5 -> row.optString("content").trim().trim('"').toLongOrNull() ?: 0L
                    withdrawn -> row.optLong("msg_key")
                    else -> 0L
                  },
                isPrivateNotice = parsed.noticeStyle,
                mediaWidth = parsed.mediaWidth,
                mediaHeight = parsed.mediaHeight,
              )
            )
          }
        }
        .sortedBy(AccountMessage::sequence)
    val next = items.map(AccountMessage::sequence).filter { it > 0L }.minOrNull()?.minus(1L) ?: 0L
    val hasMore =
      data.optBoolean("has_more") || data.optInt("has_more") == 1 || array.length() >= pageSize
    return PrivateMessagePage(items, next, hasMore && next > 0L)
  }

  private fun normalizePrivateLink(link: String, type: Int): String =
    when {
      link.startsWith("BV", ignoreCase = true) -> "https://www.bilibili.com/video/$link"
      link.isNotBlank() -> link
      type == 11 -> ""
      else -> ""
    }

  private fun privateTargetKind(link: String, type: Int): MessageTargetKind =
    when {
      type == 11 || link.contains("/video/", true) || link.startsWith("BV", true) ->
        MessageTargetKind.VIDEO
      type == 12 || link.contains("/read/", true) || link.contains("/opus/", true) ->
        MessageTargetKind.ARTICLE
      else -> MessageTargetKind.UNKNOWN
    }

  internal fun getMessageUsers(ids: List<Long>): Map<Long, Pair<String, String>> {
    if (ids.isEmpty()) return emptyMap()
    val resp =
      BiliHttpClient.get(
        "https://api.vc.bilibili.com/account/v1/user/cards?uids=${ids.distinct().take(50).joinToString(",")}"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    val data = json.optJSONArray("data") ?: return emptyMap()
    return buildMap {
      for (i in 0 until data.length()) {
        val row = data.optJSONObject(i) ?: continue
        put(
          row.optLong("mid"),
          row.optString("name") to
            dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(row.optString("face")).orEmpty(),
        )
      }
    }
  }

  internal data class ParsedPrivateContent(
    val title: String,
    val content: String,
    val coverUrl: String = "",
    val linkUrl: String = "",
    val oid: Long = 0L,
    val notifier: Pair<String, String>? = null,
    val mediaWidth: Int = 0,
    val mediaHeight: Int = 0,
    val noticeStyle: Boolean = false,
  )

  internal fun parsePrivateContent(type: Int, raw: String): ParsedPrivateContent {
    parsePrivateNoticeText(raw)?.let { noticeText ->
      return ParsedPrivateContent("提示", noticeText, noticeStyle = true)
    }
    val body = runCatching { JSONObject(raw) }.getOrNull() ?: return ParsedPrivateContent("私信", raw)
    val notifier =
      body.optJSONObject("notifier")?.let {
        it.optString("nickname") to it.optString("avatar_url")
      }
    val firstSubCard = body.optJSONArray("sub_cards")?.optJSONObject(0)
    fun firstTitle(vararg values: String): String = values.firstOrNull(String::isNotBlank).orEmpty()
    fun firstPositiveLong(vararg values: Long): Long = values.firstOrNull { it > 0L } ?: 0L
    val videoContainers =
      listOfNotNull(
        body.optJSONObject("video"),
        body.optJSONObject("item"),
        body.optJSONObject("card"),
        firstSubCard,
      )
    val videoBvid =
      firstTitle(
        body.optString("bvid"),
        *videoContainers.map { it.optString("bvid") }.toTypedArray(),
      )
    val videoOid =
      firstPositiveLong(
        body.optLong("aid"),
        body.optLong("oid"),
        *videoContainers.flatMap { listOf(it.optLong("aid"), it.optLong("oid")) }.toLongArray(),
      )
    val videoLink =
      firstTitle(
        body.optString("jump_url"),
        body.optString("url"),
        *videoContainers
          .flatMap { listOf(it.optString("jump_url"), it.optString("url")) }
          .toTypedArray(),
        videoBvid,
        videoOid.takeIf { it > 0L }?.let { "https://www.bilibili.com/video/av$it" }.orEmpty(),
      )
    val title =
      when (type) {
        1 -> "私信"
        2 -> "图片"
        7 -> firstTitle(body.optString("headline"), body.optString("title"), "分享")
        10 -> firstTitle(body.optString("title"), body.optString("main_title"), "通知")
        11 ->
          firstTitle(
            body.optString("title"),
            body.optString("headline"),
            body.optJSONObject("video")?.optString("title").orEmpty(),
            body.optJSONObject("item")?.optString("title").orEmpty(),
            body.optJSONObject("card")?.optString("title").orEmpty(),
            body.optString("desc"),
            "视频",
          )
        12 ->
          firstTitle(
            body.optString("title"),
            body.optString("headline"),
            body.optJSONObject("article")?.optString("title").orEmpty(),
            body.optJSONObject("item")?.optString("title").orEmpty(),
            "专栏",
          )
        13 -> body.optString("title", "图片卡片")
        14 -> body.optString("title", body.optString("source", "分享"))
        16 ->
          firstTitle(
            body.optString("main_title"),
            firstSubCard?.optString("title").orEmpty(),
            "推荐内容",
          )
        else -> body.optString("title", "私信")
      }
    val modules = body.optJSONArray("modules")
    val moduleText =
      buildList {
          if (modules != null)
            for (i in 0 until modules.length()) {
              val module = modules.optJSONObject(i) ?: continue
              val line =
                listOf(module.optString("title"), module.optString("detail"))
                  .filter(String::isNotBlank)
                  .joinToString("：")
              if (line.isNotBlank()) add(line)
            }
        }
        .joinToString("\n")
    val content =
      when (type) {
        1 -> body.optString("content", raw)
        2 -> "图片消息"
        7 -> body.optString("author", body.optString("source_desc"))
        10 ->
          listOf(body.optString("text"), moduleText).filter(String::isNotBlank).joinToString("\n")
        11 ->
          listOf(
              body.optString("desc"),
              body.optJSONObject("attach_msg")?.optString("content").orEmpty(),
            )
            .filter(String::isNotBlank)
            .joinToString("\n")
        12 -> body.optString("summary", body.optString("desc"))
        13 -> body.optString("title")
        14 -> body.optString("desc")
        16 -> body.optString("reply_content")
        else -> body.optString("text", body.optString("content", body.optString("desc")))
      }
    val cover =
      when (type) {
        2 -> body.optString("url")
        7 -> body.optString("thumb")
        10 ->
          body.optJSONObject("biz_content")?.optString("cover").orEmpty().ifBlank {
            body.optJSONObject("biz_content")?.optString("backup_cover").orEmpty()
          }
        11,
        14 ->
          firstTitle(
            body.optString("cover"),
            *videoContainers
              .flatMap {
                listOf(it.optString("cover"), it.optString("cover_url"), it.optString("pic"))
              }
              .toTypedArray(),
          )
        12 ->
          body.optJSONArray("image_urls")?.optString(0).orEmpty().ifBlank {
            body.optString("cover")
          }
        13 -> body.optString("pic_url")
        16 ->
          firstTitle(
            firstSubCard?.optString("cover_url").orEmpty(),
            *videoContainers
              .flatMap {
                listOf(it.optString("cover"), it.optString("cover_url"), it.optString("pic"))
              }
              .toTypedArray(),
          )
        else -> ""
      }
    val link =
      when (type) {
        7 -> body.optString("url", body.optString("bvid"))
        10 ->
          body.optJSONObject("jump_uri_config")?.optString("all_uri").orEmpty().ifBlank {
            body.optString("jump_uri")
          }
        11 -> videoLink
        12 -> body.optString("url", body.optString("jump_url"))
        13 -> body.optString("jump_url")
        14 -> body.optString("url")
        16 -> videoLink
        else -> ""
      }
    return ParsedPrivateContent(
      title = title,
      content = content.ifBlank { title },
      coverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(cover).orEmpty(),
      linkUrl = link,
      oid = if (type == 11 || type == 16) videoOid else 0L,
      notifier = notifier,
      mediaWidth = if (type == 2) body.optInt("width", body.optInt("image_width")) else 0,
      mediaHeight = if (type == 2) body.optInt("height", body.optInt("image_height")) else 0,
    )
  }

  /** 发送文本私信。 */
  fun sendPrivateMessage(senderMid: Long, receiverMid: Long, text: String) {
    require(senderMid > 0 && receiverMid > 0 && text.isNotBlank()) { "私信参数无效" }
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.vc.bilibili.com/web_im/v1/web_im/send_msg",
        mapOf(
          "msg[sender_uid]" to senderMid.toString(),
          "msg[receiver_id]" to receiverMid.toString(),
          "msg[receiver_type]" to "1",
          "msg[msg_type]" to "1",
          "msg[msg_status]" to "0",
          "msg[dev_id]" to UUID.randomUUID().toString(),
          "msg[timestamp]" to (System.currentTimeMillis() / 1000L).toString(),
          "msg[new_face_version]" to "1",
          "msg[content]" to JSONObject().put("content", text).toString(),
          "csrf" to csrf,
          "csrf_token" to csrf,
          "build" to "0",
          "mobi_app" to "web",
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "私信发送失败"))
  }

  /** 上传私信图片，返回上传结果。 */
  fun uploadPrivateImage(
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
    width: Int,
    height: Int,
  ): PrivateImageUpload = uploadBfsImage(bytes, fileName, mimeType, width, height, biz = "im")

  /** 上传评论配图（大会员图片评论）。 */
  fun uploadCommentImage(
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
    width: Int,
    height: Int,
  ): PrivateImageUpload =
    uploadBfsImage(
      bytes,
      fileName,
      mimeType,
      width,
      height,
      biz = "new_dyn",
      category = "daily",
    )

  private fun uploadBfsImage(
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
    width: Int,
    height: Int,
    biz: String,
    category: String? = null,
  ): PrivateImageUpload {
    require(bytes.isNotEmpty()) { "图片为空" }
    require(bytes.size <= 20 * 1024 * 1024) { "图片不能超过 20MB" }
    val csrf = BiliApiCommon.requireCsrf()
    val response =
      BiliHttpClient.postMultipart(
        url = "https://api.bilibili.com/x/dynamic/feed/draw/upload_bfs",
        fields =
          buildMap {
            put("csrf", csrf)
            put("csrf_token", csrf)
            put("biz", biz)
            category?.let { put("category", it) }
            put("build", "0")
            put("mobi_app", "web")
          },
        fileField = "file_up",
        fileName = fileName.ifBlank { "private-image.jpg" },
        mimeType = mimeType.ifBlank { "image/jpeg" },
        bytes = bytes,
      )
    val rawResponse = response.body?.string().orEmpty()
    response.close()
    val root = parsePossiblyEncodedJsonValue(rawResponse)
    val json = root as? JSONObject
    if (json == null && root !is String) throw IllegalStateException("图片上传响应格式异常")
    if (json == null) {
      val directUrl = (root as String).trim()
      require(
        directUrl.startsWith("http://") ||
          directUrl.startsWith("https://") ||
          directUrl.startsWith("//")
      ) {
        "图片上传未返回地址"
      }
      val normalizedUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(directUrl).orEmpty()
      require(normalizedUrl.isNotBlank()) { "图片上传地址无效" }
      return PrivateImageUpload(
        url = normalizedUrl,
        width = width.coerceAtLeast(1),
        height = height.coerceAtLeast(1),
        mimeType = mimeType.ifBlank { "image/jpeg" },
        sizeKb = ((bytes.size + 1023) / 1024).coerceAtLeast(1),
      )
    }
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "图片上传失败"))
    // 该接口的 data 视账号/风控路径不同返回 URL 字符串或对象：两种形态都算成功。
    val dataValue = json.opt("data")
    val decodedDataValue =
      if (dataValue is String && dataValue.trim().startsWith("{")) {
        runCatching { parsePossiblyEncodedJsonValue(dataValue) }.getOrDefault(dataValue)
      } else dataValue
    val url =
      when (decodedDataValue) {
        is String -> decodedDataValue
        is JSONObject ->
          decodedDataValue.optString("image_url").ifBlank { decodedDataValue.optString("url") }
        else -> ""
      }.ifBlank { json.optString("image_url") }
    require(url.isNotBlank()) { "图片上传未返回地址" }
    val normalizedUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(url).orEmpty()
    require(normalizedUrl.isNotBlank()) { "图片上传地址无效" }
    val dataObject = decodedDataValue as? JSONObject
    return PrivateImageUpload(
      url = normalizedUrl,
      width = dataObject?.optInt("image_width")?.takeIf { it > 0 } ?: width.coerceAtLeast(1),
      height = dataObject?.optInt("image_height")?.takeIf { it > 0 } ?: height.coerceAtLeast(1),
      mimeType = mimeType.ifBlank { "image/jpeg" },
      sizeKb = ((bytes.size + 1023) / 1024).coerceAtLeast(1),
    )
  }

  fun sendPrivateImage(senderMid: Long, receiverMid: Long, image: PrivateImageUpload) {
    require(senderMid > 0 && receiverMid > 0) { "私信参数无效" }
    val csrf = BiliApiCommon.requireCsrf()
    val response =
      BiliHttpClient.postForm(
        "https://api.vc.bilibili.com/web_im/v1/web_im/send_msg",
        mapOf(
          "msg[sender_uid]" to senderMid.toString(),
          "msg[receiver_id]" to receiverMid.toString(),
          "msg[receiver_type]" to "1",
          "msg[msg_type]" to "2",
          "msg[msg_status]" to "0",
          "msg[dev_id]" to UUID.randomUUID().toString(),
          "msg[timestamp]" to (System.currentTimeMillis() / 1000L).toString(),
          "msg[new_face_version]" to "1",
          "msg[content]" to
            JSONObject()
              .put("url", image.url)
              .put("width", image.width)
              .put("height", image.height)
              .put("imageType", image.mimeType.substringAfterLast('/'))
              .put("original", 1)
              .put("size", image.sizeKb)
              .toString(),
          "csrf" to csrf,
          "csrf_token" to csrf,
          "build" to "0",
          "mobi_app" to "web",
        ),
      )
    val rawResponse = response.body?.string().orEmpty()
    response.close()
    val json =
      parsePossiblyEncodedJsonValue(rawResponse) as? JSONObject
        ?: throw IllegalStateException("图片消息响应格式异常")
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "图片私信发送失败"))
  }

  internal fun commentPicturesPayload(image: PrivateImageUpload): String =
    JSONArray()
      .put(
        JSONObject()
          .put("img_src", image.url)
          .put("img_width", image.width)
          .put("img_height", image.height)
          .put("img_size", image.sizeKb)
      )
      .toString()

  /** 撤回自己的消息。 */
  fun withdrawPrivateMessage(senderMid: Long, receiverMid: Long, messageKey: Long) {
    require(senderMid > 0L && receiverMid > 0L && messageKey > 0L) { "撤回参数无效" }
    val csrf = BiliApiCommon.requireCsrf()
    val response =
      BiliHttpClient.postForm(
        "https://api.vc.bilibili.com/web_im/v1/web_im/send_msg",
        mapOf(
          "msg[sender_uid]" to senderMid.toString(),
          "msg[receiver_id]" to receiverMid.toString(),
          "msg[receiver_type]" to "1",
          "msg[msg_type]" to "5",
          "msg[msg_status]" to "0",
          "msg[dev_id]" to UUID.randomUUID().toString(),
          "msg[timestamp]" to (System.currentTimeMillis() / 1000L).toString(),
          "msg[content]" to messageKey.toString(),
          "csrf" to csrf,
          "csrf_token" to csrf,
          "build" to "0",
          "mobi_app" to "web",
        ),
      )
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) {
      throw IllegalStateException(json.optString("message", "撤回失败"))
    }
  }

  /** 上报会话已读回执。 */
  fun markPrivateMessageRead(talkerId: Long, ackSequence: Long) {
    if (talkerId <= 0L || ackSequence <= 0L) return
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.vc.bilibili.com/session_svr/v1/session_svr/update_ack",
        mapOf(
          "talker_id" to talkerId.toString(),
          "session_type" to "1",
          "ack_seqno" to ackSequence.toString(),
          "csrf" to csrf,
          "csrf_token" to csrf,
          "build" to "0",
          "mobi_app" to "web",
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
  }

  fun replyToMessage(message: AccountMessage, text: String) {
    require(message.oid > 0 && message.parentId > 0) { "这条消息缺少可回复目标" }
    val csrf = BiliApiCommon.requireCsrf()
    val root = message.rootId.takeIf { it > 0 } ?: message.parentId
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v2/reply/add",
        mapOf(
          "type" to message.commentType.toString(),
          "oid" to message.oid.toString(),
          "root" to root.toString(),
          "parent" to message.parentId.toString(),
          "message" to text,
          "csrf" to csrf,
          "csrf_token" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "回复失败"))
  }
}
package dev.openbili.webdemo.api

/**
 * 评论接口与评论解析。
 *
 * 覆盖评论分页/楼中楼/单楼详情、回复表情包、发表评论与楼中楼、点赞与删除，以及把
 * 服务端 JSON 解析为 [CommentItem]（含表情、图片、@提及、IP 属地、官方认证、UP 主
 * 点赞/回复状态）的公共解析函数。
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
 * 评论 API 集合与共享解析器。
 */
object BiliCommentApi {

  // ── 评论 ──────────────────────────────────────────────────────────────

  /** 分页拉取评论区主楼。 */
  fun getComments(
    oid: Long,
    page: Int = 1,
    sort: Int = CommentSort.DEFAULT.apiValue,
    type: Int = 1,
  ): CommentResponse {
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/v2/reply?type=$type&oid=$oid&pn=$page&ps=20&sort=$sort"
      )
    val body = resp.body?.string().orEmpty()
    resp.close()
    val json = JSONObject(body)
    if (json.optInt("code") != 0) return CommentResponse(emptyList(), false, 0)
    val data = json.getJSONObject("data")
    val pageInfo = data.optJSONObject("page")
    val total = pageInfo?.optLong("count", 0) ?: 0L
    val replies = data.optJSONArray("replies") ?: JSONArray()
    // B 站把置顶一级评论单独放在 top_replies/top 中，不会重复出现在 replies。
    // 先合并置顶项，再合并普通分页项，避免置顶评论丢失或重复显示。
    val items = buildList {
      val seen = mutableSetOf<Long>()
      fun addUnique(reply: JSONObject?, pinned: Boolean) {
        if (reply == null) return
        val rpid = reply.optLong("rpid", 0L)
        if (rpid <= 0L || !seen.add(rpid)) return
        add(parseComment(reply, pinned = pinned))
      }
      data.optJSONArray("top_replies")?.let { topReplies ->
        for (index in 0 until topReplies.length()) {
          addUnique(topReplies.optJSONObject(index), pinned = true)
        }
      }
      data.optJSONObject("top")?.takeIf { it.has("rpid") }?.let { addUnique(it, pinned = true) }
      data.optJSONObject("upper")?.optJSONObject("top")?.takeIf { it.has("rpid") }?.let {
        addUnique(it, pinned = true)
      }
      for (index in 0 until replies.length()) {
        addUnique(replies.optJSONObject(index), pinned = false)
      }
    }
    if (pageInfo == null) return CommentResponse(items, false, total)
    val current = pageInfo.optInt("num", page)
    val pageSize = pageInfo.optInt("size", 20)
    val hasMore = current * pageSize < total
    return CommentResponse(items, hasMore, total)
  }

  /** 分页拉取某主楼的楼中楼。 */
  fun getCommentReplies(oid: Long, root: Long, page: Int = 1, type: Int = 1): CommentResponse {
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/v2/reply/reply?type=$type&oid=$oid&root=$root&pn=$page&ps=20"
      )
    val body = resp.body?.string().orEmpty()
    resp.close()
    val json = JSONObject(body)
    if (json.optInt("code") != 0) return CommentResponse(emptyList(), false, 0)
    val data = json.optJSONObject("data") ?: return CommentResponse(emptyList(), false, 0)
    val pageInfo = data.optJSONObject("page")
    val total = pageInfo?.optLong("count", 0) ?: 0L
    val array = data.optJSONArray("replies") ?: return CommentResponse(emptyList(), false, total)
    val items = buildList {
      for (index in 0 until array.length()) add(parseComment(array.getJSONObject(index)))
    }
    val current = pageInfo?.optInt("num", page) ?: page
    val pageSize = pageInfo?.optInt("size", 20) ?: 20
    return CommentResponse(items, current * pageSize < total, total)
  }

  /** 拉取单条主楼详情及其楼中楼首页。 */
  fun getCommentThread(oid: Long, root: Long, type: Int = 1): CommentThread? {
    if (oid <= 0L || root <= 0L) return null
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/v2/reply/detail?type=$type&oid=$oid&root=$root&ps=20"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) return null
    val data = json.optJSONObject("data") ?: return null
    val rootObject = data.optJSONObject("root") ?: return null
    val rootItem = parseComment(rootObject)
    val replyArray =
      data.optJSONArray("replies") ?: rootObject.optJSONArray("replies") ?: JSONArray()
    val replies = buildList {
      for (index in 0 until replyArray.length()) {
        replyArray.optJSONObject(index)?.let { add(parseComment(it)) }
      }
    }
    val page = data.optJSONObject("page")
    val total = page?.optLong("count", rootItem.replyCount) ?: rootItem.replyCount
    return CommentThread(
      root = rootItem,
      replies = replies,
      hasMore = replies.size.toLong() < total,
    )
  }

  /** 拉取回复输入面板可用的小表情包。 */
  fun getReplyEmotes(): List<BiliEmotePackage> {
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/emote/user/panel/web?business=reply")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) return emptyList()
    val packages = json.optJSONObject("data")?.optJSONArray("packages") ?: return emptyList()
    return buildList {
      for (i in 0 until packages.length()) {
        val pack = packages.getJSONObject(i)
        val array = pack.optJSONArray("emote")
        val emotes = buildList {
          if (array != null)
            for (j in 0 until array.length()) {
              val emote = array.getJSONObject(j)
              val text = emote.optString("text")
              val url = emote.optString("url").replaceFirst("http://", "https://")
              if (text.isNotBlank() && url.isNotBlank()) add(BiliEmote(text, url))
            }
        }
        if (emotes.isNotEmpty())
          add(BiliEmotePackage(pack.optLong("id"), pack.optString("text"), emotes))
      }
    }
  }

  /** 发表主楼评论；可选附带一张大会员图片。 */
  fun addComment(
    oid: Long,
    message: String,
    type: Int = 1,
    image: BiliPrivateMessageApi.PrivateImageUpload? = null,
  ): CommentItem {
    val csrf = BiliApiCommon.requireCsrf()
    val fields =
      linkedMapOf(
        "type" to type.toString(),
        "oid" to oid.toString(),
        "message" to message,
        "plat" to "1",
        "csrf" to csrf,
        "csrf_token" to csrf,
      )
    image?.let { fields["pictures"] = BiliPrivateMessageApi.commentPicturesPayload(it) }
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v2/reply/add",
        fields,
      )
    val httpCode = resp.code
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    val apiCode = json.optInt("code", -1)
    Log.d(BiliApiCommon.TAG, "comment add response: http=$httpCode code=$apiCode oid=$oid")
    if (apiCode != 0) throw IllegalStateException(json.optString("message", "评论失败"))
    return parseAddedCommentResponse(json, message, "评论响应缺少评论内容")
  }

  /** 发表楼中楼回复；可选附带一张大会员图片。 */
  fun addReply(
    oid: Long,
    root: Long,
    parent: Long,
    message: String,
    type: Int = 1,
    image: BiliPrivateMessageApi.PrivateImageUpload? = null,
  ): CommentItem {
    val csrf = BiliApiCommon.requireCsrf()
    val fields =
      linkedMapOf(
        "type" to type.toString(),
        "oid" to oid.toString(),
        "root" to root.toString(),
        "parent" to parent.toString(),
        "message" to message,
        "plat" to "1",
        "csrf" to csrf,
        "csrf_token" to csrf,
      )
    image?.let { fields["pictures"] = BiliPrivateMessageApi.commentPicturesPayload(it) }
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v2/reply/add",
        fields,
      )
    val httpCode = resp.code
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    val apiCode = json.optInt("code", -1)
    Log.d(BiliApiCommon.TAG, "reply add response: http=$httpCode code=$apiCode oid=$oid")
    if (apiCode != 0) throw IllegalStateException(json.optString("message", "回复失败"))
    return parseAddedCommentResponse(json, message, "回复响应缺少评论内容")
  }

  /** 评论点赞/取消点赞（action 1/0）。 */
  fun setCommentLike(oid: Long, rpid: Long, liked: Boolean, type: Int = 1) {
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v2/reply/action",
        mapOf(
          "type" to type.toString(),
          "oid" to oid.toString(),
          "rpid" to rpid.toString(),
          "action" to if (liked) "1" else "0",
          "csrf" to csrf,
          "csrf_token" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "操作失败"))
  }

  /** 删除评论（本人评论或本人视频下的评论）。 */
  fun deleteComment(oid: Long, rpid: Long, type: Int = 1) {
    require(oid > 0L && rpid > 0L) { "评论参数无效" }
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v2/reply/del",
        mapOf(
          "type" to type.toString(),
          "oid" to oid.toString(),
          "rpid" to rpid.toString(),
          "csrf" to csrf,
          "csrf_token" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "删除失败"))
  }

  /** 置顶/取消置顶自己管理的视频下的一级评论（action 1/0）。 */
  fun setCommentTop(oid: Long, rpid: Long, pinned: Boolean, type: Int = 1) {
    require(oid > 0L && rpid > 0L) { "评论参数无效" }
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v2/reply/top",
        mapOf(
          "type" to type.toString(),
          "oid" to oid.toString(),
          "rpid" to rpid.toString(),
          "action" to if (pinned) "1" else "0",
          "csrf" to csrf,
          "csrf_token" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "置顶操作失败"))
  }

  /** 把服务端 JSON 解析为统一评论模型 [CommentItem]。 */
  internal fun parseComment(r: JSONObject, pinned: Boolean = false): CommentItem {
    val member = r.optJSONObject("member") ?: JSONObject()
    val content = r.optJSONObject("content") ?: JSONObject()
    val upAction = r.optJSONObject("up_action")
    return CommentItem(
      rpid = r.getLong("rpid"),
      mid = r.getLong("mid"),
      name = member.optString("uname"),
      face = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(member.optString("avatar")).orEmpty(),
      content = content.optString("message"),
      likeCount = r.optLong("like"),
      replyCount = r.optLong("rcount"),
      ctime = r.optLong("ctime"),
      liked = r.optInt("action") == 1,
      emotes = parseContentEmotes(content),
      location = parseCommentLocation(r),
      images = parseCommentPictures(content),
      mentions = parseCommentMentions(content),
      jumpLinks = parseCommentJumpLinks(content),
      level = member.optJSONObject("level_info")?.optInt("current_level", 0) ?: 0,
      vipActive = member.optJSONObject("vip")?.optInt("vipStatus", 0) == 1,
      vipLabel = member.optJSONObject("vip")?.optJSONObject("label")?.optString("text").orEmpty(),
      officialVerification = parseOfficialVerification(member.optJSONObject("official_verify")),
      upLiked = upAction?.optBoolean("like", false) == true,
      upReplied = upAction?.optBoolean("reply", false) == true,
      isPinned = pinned || r.optJSONObject("reply_control")?.optBoolean("is_up_top", false) == true,
    )
  }

  /** 解析发表评论响应；接口可能同时返回完整 reply，也可能只返回新评论编号。 */
  internal fun parseAddedCommentResponse(
    json: JSONObject,
    message: String,
    missingMessage: String,
  ): CommentItem {
    val data = json.optJSONObject("data") ?: throw IllegalStateException(missingMessage)
    data.optJSONObject("reply")?.let { return parseComment(it) }
    val rpid = data.optLong("rpid", 0L)
    if (rpid <= 0L) throw IllegalStateException(missingMessage)
    return CommentItem(
      rpid = rpid,
      mid = data.optLong("mid", 0L),
      name = "我",
      face = "",
      content = message,
      likeCount = 0L,
      replyCount = 0L,
      ctime = System.currentTimeMillis() / 1_000L,
    )
  }

  /** 解析官方认证信息；primary 优先，legacy 兜底。 */
  internal fun parseOfficialVerification(
    primary: JSONObject?,
    legacy: JSONObject? = null,
  ): OfficialVerification {
    val type =
      when {
        primary?.has("type") == true -> primary.optInt("type", -1)
        legacy?.has("type") == true -> legacy.optInt("type", -1)
        else -> -1
      }
    val description =
      sequenceOf(
          primary?.optString("title"),
          primary?.optString("desc"),
          legacy?.optString("title"),
          legacy?.optString("desc"),
        )
        .filterNotNull()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        .orEmpty()
    return OfficialVerification(type = type, description = description)
  }

  /** 解析列表项用户的官方认证；结构化字段缺失时回退 verify_info 文本。 */
  internal fun parseListedUserOfficialVerification(item: JSONObject): OfficialVerification {
    val structured =
      parseOfficialVerification(
        primary = item.optJSONObject("official_verify"),
        legacy = item.optJSONObject("official"),
      )
    if (structured.verified) return structured
    val fallbackDescription = item.optString("verify_info").trim()
    if (fallbackDescription.isBlank()) return structured
    val fallbackType = item.optInt("official_type", 1).takeIf { it == 0 || it == 1 } ?: 1
    return OfficialVerification(type = fallbackType, description = fallbackDescription)
  }

  private fun parseCommentLocation(reply: JSONObject): String {
    val raw = reply.optJSONObject("reply_control")?.optString("location").orEmpty().trim()
    return raw.removePrefix("IP属地：").removePrefix("IP属地:").trim()
  }

  private fun parseContentEmotes(content: JSONObject): Map<String, String> {
    val objectMap = content.optJSONObject("emote") ?: return emptyMap()
    return buildMap {
      val keys = objectMap.keys()
      while (keys.hasNext()) {
        val text = keys.next()
        val url =
          objectMap
            .optJSONObject(text)
            ?.optString("url")
            .orEmpty()
            .replaceFirst("http://", "https://")
        if (text.isNotBlank() && url.isNotBlank()) put(text, url)
      }
    }
  }

  internal fun parseCommentPictures(content: JSONObject): List<CommentImage> {
    val pictures = content.optJSONArray("pictures") ?: return emptyList()
    return buildList {
        for (index in 0 until pictures.length()) {
          val picture = pictures.optJSONObject(index) ?: continue
          val rawUrl =
            sequenceOf("img_src", "img_url", "url")
              .map(picture::optString)
              .firstOrNull(String::isNotBlank)
          val url = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(rawUrl) ?: continue
          add(
            CommentImage(
              url = url,
              width = picture.optInt("img_width").coerceAtLeast(0),
              height = picture.optInt("img_height").coerceAtLeast(0),
            )
          )
        }
      }
      .distinctBy(CommentImage::url)
      .take(9)
  }

  internal fun parseCommentMentions(content: JSONObject): List<CommentMention> {
    val members = content.optJSONArray("members") ?: return emptyList()
    return buildList {
        for (index in 0 until members.length()) {
          val member = members.optJSONObject(index) ?: continue
          val mid = member.optLong("mid", 0L)
          val name = member.optString("uname").trim()
          if (mid > 0L && name.isNotBlank()) add(CommentMention(mid = mid, name = name))
        }
      }
      .distinctBy { it.mid to it.name }
  }

  /** 解析网页端用于把正文关键字替换成行内蓝色跳转的 content.jump_url。 */
  internal fun parseCommentJumpLinks(content: JSONObject): List<CommentJumpLink> {
    val jumpUrl = content.optJSONObject("jump_url") ?: return emptyList()
    val keys = jumpUrl.keys()
    return buildList {
      while (keys.hasNext()) {
        val key = keys.next().trim()
        if (key.isBlank()) continue
        val value = jumpUrl.optJSONObject(key) ?: continue
        val title = value.optString("title").trim()
        val prefixIcon =
          sequenceOf("prefix_icon", "prefixIcon")
            .map(value::optString)
            .map(String::trim)
            .firstOrNull(String::isNotBlank)
            ?.let { dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(it) }
            .orEmpty()
        val pcUrl =
          sequenceOf("pc_url", "pcUrl", "url")
            .map(value::optString)
            .map(String::trim)
            .firstOrNull(String::isNotBlank)
            .orEmpty()
        add(CommentJumpLink(key = key, title = title, prefixIconUrl = prefixIcon, pcUrl = pcUrl))
      }
    }
  }
}

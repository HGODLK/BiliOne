package dev.openbili.webdemo.api

/**
 * 互动消息接口：未读汇总（回复/@/点赞）以及点赞、回复我、@我的三类消息分页。
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
 * 互动消息 API 集合。
 */
object BiliInteractionApi {

  /** 清除服务端的回复、@ 和点赞未读标记。 */
  fun clearInteractionUnread() {
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/msgfeed/clear",
        mapOf(
          "platform" to "web",
          "build" to "0",
          "mobi_app" to "web",
          "csrf" to csrf,
          "csrf_token" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "清除未读失败"))
  }

  /** 读取回复/@/点赞三类未读数。 */
  fun getInteractionUnreadSummary(): InteractionUnreadSummary {
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/msgfeed/unread?platform=web&build=0&mobi_app=web"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseInteractionUnreadSummary(json)
  }

  internal fun parseInteractionUnreadSummary(json: JSONObject): InteractionUnreadSummary {
    val data = json.optJSONObject("data") ?: json
    return InteractionUnreadSummary(
      replyCount = data.optInt("reply").coerceAtLeast(0),
      mentionCount = data.optInt("at").coerceAtLeast(0),
      likeCount = data.optInt("like").coerceAtLeast(0),
    )
  }

  /** 分页拉取点赞消息。 */
  fun getLikeMessages(cursor: MessageCursor = MessageCursor()): AccountMessagePage {
    val cursorQuery =
      if (cursor.id > 0L && cursor.time > 0L) "&id=${cursor.id}&like_time=${cursor.time}" else ""
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/msgfeed/like?" +
          "platform=web&build=0&mobi_app=web&web_location=333.40164$cursorQuery"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseLikeMessagePage(json.optJSONObject("data"), cursor)
  }

  internal fun parseLikeMessagePage(
    data: JSONObject?,
    previousCursor: MessageCursor = MessageCursor(),
  ): AccountMessagePage {
    if (data == null) return AccountMessagePage(emptyList())
    val total = data.optJSONObject("total") ?: data
    val array = total.optJSONArray("items") ?: JSONArray()
    val items = buildList {
      for (index in 0 until array.length()) {
        val row = array.optJSONObject(index) ?: continue
        val item = row.optJSONObject("item") ?: JSONObject()
        val users = row.optJSONArray("users")
        val user =
          users?.optJSONObject(0)
            ?: row.optJSONObject("user")
            ?: row.optJSONObject("latest")?.optJSONObject("user")
            ?: JSONObject()
        val vip = user.optJSONObject("vip") ?: JSONObject()
        val uri =
          item
            .optString("uri")
            .ifBlank { item.optString("url") }
            .ifBlank { item.optString("native_uri") }
        val nativeUri = item.optString("native_uri")
        val targetHint = "$uri $nativeUri"
        val businessId =
          item.optInt("business_id", item.optInt("subject_type", item.optInt("type", 1)))
        val business = item.optString("business")
        val targetKind =
          when {
            targetHint.contains("/video/", ignoreCase = true) ||
              targetHint.contains("bilibili://video", ignoreCase = true) -> MessageTargetKind.VIDEO
            targetHint.contains("/read/", ignoreCase = true) ||
              targetHint.contains("/opus/", ignoreCase = true) ||
              targetHint.contains("bilibili://article", ignoreCase = true) ->
              MessageTargetKind.ARTICLE
            business.contains("专栏") || business.contains("文章") || businessId == 12 ->
              MessageTargetKind.ARTICLE
            business.contains("视频") || businessId == 1 -> MessageTargetKind.VIDEO
            else -> MessageTargetKind.UNKNOWN
          }
        val sourceId = item.optLong("source_id")
        val rootId = item.optLong("root_id")
        val itemId = item.optLong("item_id")
        val itemType = item.optString("type")
        val isReplyLike = itemType.equals("reply", ignoreCase = true)
        fun queryLong(name: String): Long =
          Regex("(?:[?&])${Regex.escape(name)}=(\\d+)", RegexOption.IGNORE_CASE)
            .find(nativeUri)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull() ?: 0L
        val nativeRootId = queryLong("comment_root_id")
        val nativeSecondaryId = queryLong("comment_secondary_id")
        val targetCommentId =
          nativeSecondaryId.takeIf { it > 0L }
            ?: sourceId.takeIf { it > 0L }
            ?: nativeRootId.takeIf { it > 0L }
            ?: rootId.takeIf { it > 0L }
            ?: itemId.takeIf { isReplyLike && it > 0L }
            ?: 0L
        val targetRootId =
          nativeRootId.takeIf { it > 0L } ?: rootId.takeIf { it > 0L } ?: targetCommentId
        val count = row.optInt("counts", users?.length() ?: 1).coerceAtLeast(1)
        val objectLabel =
          when {
            itemType.equals("danmu", ignoreCase = true) || business.contains("弹幕") -> "弹幕"
            isReplyLike -> "评论"
            itemType.equals("video", ignoreCase = true) -> "视频"
            business.contains("动态") -> "动态"
            else -> "评论"
          }
        val title = if (count > 1) "等共 $count 人赞了我的$objectLabel" else "赞了我的$objectLabel"
        val subjectTitle =
          item
            .optString("subject_title")
            .ifBlank { item.optString("source_title") }
            .ifBlank { item.optString("subject") }
        add(
          AccountMessage(
            id = row.optLong("id"),
            userMid = user.optLong("mid"),
            userName = user.optString("nickname", "用户"),
            userFace =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(user.optString("avatar")).orEmpty(),
            title = title,
            content = title,
            sourceContent =
              item
                .optString("source_content")
                .ifBlank { item.optString("target_reply_content") }
                .ifBlank { item.optString("root_reply_content") }
                .ifBlank { item.optString("title") },
            oid =
              item.optLong("subject_id", item.optLong("target_id")).takeIf { it > 0L }
                ?: Regex("bilibili://video/(?:av)?(\\d+)", RegexOption.IGNORE_CASE)
                  .find(nativeUri)
                  ?.groupValues
                  ?.getOrNull(1)
                  ?.toLongOrNull()
                ?: 0L,
            rootId = targetRootId,
            parentId = targetCommentId,
            time =
              row.optLong("like_time", row.optJSONObject("latest")?.optLong("like_time") ?: 0L),
            coverUrl =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(
                  item.optString("image").ifBlank { item.optString("cover") }
                )
                .orEmpty(),
            linkUrl = uri,
            messageType = businessId,
            targetKind = targetKind,
            subjectTitle = subjectTitle,
            targetCommentId = targetCommentId,
            commentType = if (targetKind == MessageTargetKind.ARTICLE) 12 else 1,
            userLevel = user.optInt("level"),
            userVipActive =
              vip.optInt("status") == 1 ||
                user.optInt("vip_status") == 1 ||
                user.optInt("vip_type") > 0,
            userVipLabel =
              vip.optJSONObject("label")?.optString("text") ?: user.optString("vip_label"),
          )
        )
      }
    }
    val cursorObject = total.optJSONObject("cursor")
    val lastRow = array.optJSONObject(array.length() - 1)
    val nextCursor =
      cursorObject?.let {
        MessageCursor(
          id = it.optLong("id"),
          time = it.optLong("time", it.optLong("like_time")),
        )
      }
        ?: MessageCursor(
          id = lastRow?.optLong("id") ?: 0L,
          time = lastRow?.optLong("like_time") ?: 0L,
        )
    val explicitlyEnded =
      cursorObject?.let { it.optBoolean("is_end") || it.optInt("is_end") == 1 } == true ||
        (total.has("has_more") && !total.optBoolean("has_more"))
    val cursorExplicitlyContinues =
      cursorObject?.has("is_end") == true &&
        !cursorObject.optBoolean("is_end") &&
        cursorObject.optInt("is_end") != 1
    val hasMore =
      items.isNotEmpty() &&
        !explicitlyEnded &&
        nextCursor.id > 0L &&
        nextCursor.time > 0L &&
        nextCursor != previousCursor &&
        (total.optBoolean("has_more") ||
          cursorExplicitlyContinues ||
          !total.has("has_more") && cursorObject == null && array.length() >= 20)
    return AccountMessagePage(items = items, cursor = nextCursor, hasMore = hasMore)
  }

  /** 分页拉取回复我的消息。 */
  fun getReplyMessages(cursor: MessageCursor = MessageCursor()): AccountMessagePage {
    val cursorQuery =
      if (cursor.id > 0L && cursor.time > 0L) "&id=${cursor.id}&reply_time=${cursor.time}" else ""
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/msgfeed/reply?platform=web&build=0&mobi_app=web$cursorQuery"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseAccountMessagePage(
      data = json.optJSONObject("data"),
      previousCursor = cursor,
      timestampKey = "reply_time",
      atStream = false,
    )
  }

  /** 分页拉取 @ 我的消息。 */
  fun getAtMessages(cursor: MessageCursor = MessageCursor()): AccountMessagePage {
    val cursorQuery =
      if (cursor.id > 0L && cursor.time > 0L) "&id=${cursor.id}&at_time=${cursor.time}" else ""
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/msgfeed/at?platform=web&build=0&mobi_app=web$cursorQuery"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseAccountMessagePage(
      data = json.optJSONObject("data"),
      previousCursor = cursor,
      timestampKey = "at_time",
      atStream = true,
    )
  }

  fun getInteractionMessages(
    replyCursor: MessageCursor = MessageCursor(),
    atCursor: MessageCursor = MessageCursor(),
    loadReply: Boolean = true,
    loadAt: Boolean = true,
  ): InteractionMessagePage {
    val replies =
      if (loadReply) getReplyMessages(replyCursor)
      else AccountMessagePage(emptyList(), replyCursor, false)
    val mentions =
      if (loadAt) getAtMessages(atCursor) else AccountMessagePage(emptyList(), atCursor, false)
    return InteractionMessagePage(
      items = (replies.items + mentions.items).distinctBy { it.id }.sortedByDescending { it.time },
      replyCursor = replies.cursor,
      atCursor = mentions.cursor,
      replyHasMore = replies.hasMore,
      atHasMore = mentions.hasMore,
    )
  }

  private fun parseAccountMessagePage(
    data: JSONObject?,
    previousCursor: MessageCursor,
    timestampKey: String,
    atStream: Boolean,
  ): AccountMessagePage {
    if (data == null) return AccountMessagePage(emptyList())
    val array = data.optJSONArray("items") ?: JSONArray()
    val items = buildList {
      for (index in 0 until array.length()) {
        val row = array.optJSONObject(index) ?: continue
        val user = row.optJSONObject("user") ?: JSONObject()
        val item = row.optJSONObject("item") ?: JSONObject()
        val userVip = user.optJSONObject("vip") ?: JSONObject()
        val uri =
          item
            .optString("uri")
            .ifBlank { item.optString("native_uri") }
            .ifBlank { item.optString("url") }
        val businessId =
          item.optInt("business_id", item.optInt("subject_type", item.optInt("reply_type", 1)))
        val business = item.optString("business")
        val targetKind =
          when {
            uri.contains("/video/", ignoreCase = true) ||
              uri.startsWith("bilibili://video", ignoreCase = true) -> MessageTargetKind.VIDEO
            uri.contains("/read/", ignoreCase = true) ||
              uri.contains("/opus/", ignoreCase = true) ||
              uri.startsWith("bilibili://article", ignoreCase = true) -> MessageTargetKind.ARTICLE
            business.contains("专栏") || business.contains("文章") -> MessageTargetKind.ARTICLE
            business.contains("视频") -> MessageTargetKind.VIDEO
            businessId == 12 -> MessageTargetKind.ARTICLE
            businessId == 1 -> MessageTargetKind.VIDEO
            else -> MessageTargetKind.UNKNOWN
          }
        val sourceId = item.optLong("source_id")
        val rootId = item.optLong("root_id")
        val subjectTitle =
          item
            .optString("subject_title")
            .ifBlank { item.optString("source_title") }
            .ifBlank { item.optString("subject") }
        add(
          AccountMessage(
            id = if (atStream) row.optLong("id") xor Long.MIN_VALUE else row.optLong("id"),
            userMid = user.optLong("mid"),
            userName = user.optString("nickname", "用户"),
            userFace =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(user.optString("avatar")).orEmpty(),
            title = item.optString("title", if (atStream) "@了你" else "回复了你"),
            content = item.optString("source_content", item.optString("content")),
            sourceContent =
              item.optString("target_reply_content", item.optString("root_reply_content")),
            oid = item.optLong("subject_id", item.optLong("target_id")),
            rootId = rootId,
            parentId = sourceId,
            time = row.optLong(timestampKey, row.optLong("reply_time")),
            coverUrl =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(
                  item.optString("image").ifBlank { item.optString("cover") }
                )
                .orEmpty(),
            linkUrl = uri,
            messageType = businessId,
            targetKind = targetKind,
            subjectTitle = subjectTitle,
            targetCommentId = sourceId.takeIf { it > 0L } ?: rootId,
            commentType = if (targetKind == MessageTargetKind.ARTICLE) 12 else 1,
            userLevel = user.optInt("level"),
            userVipActive =
              userVip.optInt("status") == 1 ||
                user.optInt("vip_status") == 1 ||
                user.optInt("vip_type") > 0,
            userVipLabel =
              userVip.optJSONObject("label")?.optString("text") ?: user.optString("vip_label"),
          )
        )
      }
    }
    val cursorObject = data.optJSONObject("cursor")
    val nextCursor =
      cursorObject?.let {
        MessageCursor(
          id = it.optLong("id"),
          time = it.optLong("time", it.optLong(timestampKey)),
        )
      } ?: MessageCursor()
    val ended =
      cursorObject == null ||
        cursorObject.optBoolean("is_end") ||
        cursorObject.optInt("is_end") == 1 ||
        nextCursor.id <= 0L ||
        nextCursor.time <= 0L
    return AccountMessagePage(
      items = items,
      cursor = nextCursor,
      hasMore = items.isNotEmpty() && !ended && nextCursor != previousCursor,
    )
  }
}

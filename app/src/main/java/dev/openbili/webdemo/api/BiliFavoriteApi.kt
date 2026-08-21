package dev.openbili.webdemo.api

/**
 * 收藏与收藏夹接口。
 *
 * 覆盖收藏夹的增删改查、收藏/取消收藏、收藏内容在夹间复制/移动、夹内搜索分页，以及
 * 单资源是否已在夹内的确认。
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
 * 收藏与收藏夹 API 集合。
 */
object BiliFavoriteApi {

  /** 对资源批量执行收藏/取消收藏（add_media_ids 与 del_media_ids 可同时提供）。 */
  fun setFavoriteFolders(aid: Long, addMediaIds: List<Long>, removeMediaIds: List<Long>) {
    require(aid > 0) { "收藏参数无效" }
    if (addMediaIds.isEmpty() && removeMediaIds.isEmpty()) return
    require(addMediaIds.all { it > 0 } && removeMediaIds.all { it > 0 }) {
      "收藏夹参数无效"
    }
    postFavoriteAction(
      url = "https://api.bilibili.com/medialist/gateway/coll/resource/deal",
      fields =
        mapOf(
          "rid" to aid.toString(),
          "type" to "2",
          "add_media_ids" to addMediaIds.distinct().joinToString(","),
          "del_media_ids" to removeMediaIds.distinct().joinToString(","),
        ),
      action = "favorite deal",
    )
  }

  /** 复制收藏资源到另一个收藏夹。 */
  fun copyFavoriteResource(
    ownerMid: Long,
    resourceId: Long,
    resourceType: Int,
    sourceFolderId: Long,
    targetFolderId: Long,
  ) {
    transferFavoriteResource(
      action = "copy",
      ownerMid = ownerMid,
      resourceId = resourceId,
      resourceType = resourceType,
      sourceFolderId = sourceFolderId,
      targetFolderId = targetFolderId,
    )
  }

  /** 移动收藏资源到另一个收藏夹。 */
  fun moveFavoriteResource(
    ownerMid: Long,
    resourceId: Long,
    resourceType: Int,
    sourceFolderId: Long,
    targetFolderId: Long,
  ) {
    transferFavoriteResource(
      action = "move",
      ownerMid = ownerMid,
      resourceId = resourceId,
      resourceType = resourceType,
      sourceFolderId = sourceFolderId,
      targetFolderId = targetFolderId,
    )
  }

  /** 从收藏夹删除单个收藏资源。 */
  fun removeFavoriteResource(resourceId: Long, resourceType: Int, folderId: Long) {
    require(resourceId > 0 && resourceType > 0 && folderId > 0) { "收藏参数无效" }
    Log.d(BiliApiCommon.TAG, "favorite remove request: resource=$resourceId:$resourceType folder=$folderId")
    postFavoriteAction(
      url = "https://api.bilibili.com/x/v3/fav/resource/batch-del",
      fields =
        mapOf(
          "resources" to "$resourceId:$resourceType",
          "media_id" to folderId.toString(),
          "platform" to "web",
        ),
      action = "favorite remove",
    )
  }

  /** 确认某资源是否已收藏在指定收藏夹内。 */
  fun favoriteFolderContains(folderId: Long, resourceId: Long, resourceType: Int): Boolean {
    require(folderId > 0 && resourceId > 0 && resourceType > 0) { "收藏参数无效" }
    val response =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/v3/fav/resource/ids?media_id=$folderId" +
          "&platform=web&_=${System.currentTimeMillis()}"
      )
    val body = response.body?.string().orEmpty()
    response.close()
    val json = JSONObject(body)
    if (json.optInt("code") != 0) {
      throw IllegalStateException(json.optString("message", "收藏状态确认失败"))
    }
    val resources = json.optJSONArray("data") ?: return false
    for (index in 0 until resources.length()) {
      val item = resources.optJSONObject(index) ?: continue
      if (item.optLong("id") == resourceId && item.optInt("type") == resourceType) return true
    }
    return false
  }

  private fun transferFavoriteResource(
    action: String,
    ownerMid: Long,
    resourceId: Long,
    resourceType: Int,
    sourceFolderId: Long,
    targetFolderId: Long,
  ) {
    require(action == "copy" || action == "move") { "收藏操作无效" }
    require(
      ownerMid > 0 && resourceId > 0 && resourceType > 0 && sourceFolderId > 0 && targetFolderId > 0
    ) {
      "收藏参数无效"
    }
    require(sourceFolderId != targetFolderId) { "源收藏夹和目标收藏夹不能相同" }
    Log.d(
      BiliApiCommon.TAG,
      "favorite $action request: resource=$resourceId:$resourceType " +
        "source=$sourceFolderId target=$targetFolderId",
    )
    postFavoriteAction(
      url = "https://api.bilibili.com/x/v3/fav/resource/$action",
      fields =
        mapOf(
          "src_media_id" to sourceFolderId.toString(),
          "tar_media_id" to targetFolderId.toString(),
          "mid" to ownerMid.toString(),
          "resources" to "$resourceId:$resourceType",
          "platform" to "web",
        ),
      action = "favorite $action",
    )
  }

  private fun postFavoriteAction(url: String, fields: Map<String, String>, action: String) {
    val csrf = BiliApiCommon.requireCsrf()
    val response = BiliHttpClient.postForm(url, fields + ("csrf" to csrf))
    val statusCode = response.code
    val contentType = response.header("Content-Type").orEmpty()
    val body = response.body?.string().orEmpty()
    response.close()
    if (!body.trimStart().startsWith("{")) {
      Log.w(
        BiliApiCommon.TAG,
        "$action returned non-JSON: status=$statusCode type=$contentType len=${body.length}",
      )
      throw IllegalStateException("收藏服务暂时不可用，请稍后重试")
    }
    val json = JSONObject(body)
    val code = json.optInt("code")
    Log.d(BiliApiCommon.TAG, "$action: http=$statusCode code=$code")
    if (code != 0) throw IllegalStateException(json.optString("message", "收藏失败"))
  }

  /** 拉取账号收藏夹列表；resourceAid 非空时附带该资源的收藏状态（fav_state）。 */
  fun getFavoriteFolders(mid: Long, resourceAid: Long? = null): List<FavoriteFolder> {
    val resourceQuery = resourceAid?.takeIf { it > 0 }?.let { "&type=2&rid=$it" }.orEmpty()
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=$mid$resourceQuery"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val array = json.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
    return buildList {
      for (i in 0 until array.length()) {
        val item = array.getJSONObject(i)
        add(
          FavoriteFolder(
            item.getLong("id"),
            item.optString("title"),
            item.optInt("media_count"),
            item.optInt("fav_state", 0) == 1,
            item.optInt("attr", 0) == 0,
          )
        )
      }
    }
  }

  /** 分页拉取收藏夹内视频；keyword 非空时在夹内搜索。 */
  fun getFavoriteVideos(folderId: Long, page: Int, keyword: String = ""): SpaceVideoResponse {
    val keywordQuery =
      keyword
        .trim()
        .takeIf(String::isNotBlank)
        ?.let {
          "&keyword=${URLEncoder.encode(it, "UTF-8")}"
        }
        .orEmpty()
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/v3/fav/resource/list?media_id=$folderId&pn=$page" +
          "&ps=20&platform=web$keywordQuery"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data = json.getJSONObject("data")
    val array = data.optJSONArray("medias")
    val cards = buildList {
      if (array != null)
        for (i in 0 until array.length()) {
          val item = array.getJSONObject(i)
          val upper = item.optJSONObject("upper") ?: JSONObject()
          val count = item.optJSONObject("cnt_info") ?: JSONObject()
          add(
            FeedCard(
              item.optLong("id"),
              item.optString("bvid"),
              item.optLong("cid"),
              item.optString("title"),
              item.optString("cover"),
              upper.optString("name"),
              upper.optString("face"),
              upper.optLong("mid"),
              count.optLong("play"),
              count.optLong("danmaku"),
              item.optLong("duration"),
              item.optLong("fav_time"),
              description = item.optString("intro"),
              resourceType = item.optInt("type", 2),
            )
          )
        }
    }
    return SpaceVideoResponse(cards, data.optBoolean("has_more"))
  }

  /** 新建收藏夹，返回新夹 ID。 */
  fun createFavoriteFolder(title: String, isPublic: Boolean): Long {
    val normalized = title.trim()
    require(normalized.isNotBlank()) { "收藏夹名称不能为空" }
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v3/fav/folder/add",
        mapOf(
          "title" to normalized,
          "intro" to "",
          "privacy" to if (isPublic) "0" else "1",
          "csrf" to csrf,
          "csrf_token" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "创建失败"))
    return json.optJSONObject("data")?.optLong("id") ?: 0L
  }

  /** 修改收藏夹名称与可见性。 */
  fun editFavoriteFolder(folderId: Long, title: String, isPublic: Boolean) {
    val normalized = title.trim()
    require(folderId > 0L && normalized.isNotBlank()) { "收藏夹参数无效" }
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v3/fav/folder/edit",
        mapOf(
          "media_id" to folderId.toString(),
          "title" to normalized,
          "intro" to "",
          "privacy" to if (isPublic) "0" else "1",
          "csrf" to csrf,
          "csrf_token" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "修改失败"))
  }

  /** 删除收藏夹。 */
  fun deleteFavoriteFolder(folderId: Long) {
    require(folderId > 0L) { "收藏夹参数无效" }
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v3/fav/folder/del",
        mapOf(
          "media_ids" to folderId.toString(),
          "csrf" to csrf,
          "csrf_token" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "删除失败"))
  }
}
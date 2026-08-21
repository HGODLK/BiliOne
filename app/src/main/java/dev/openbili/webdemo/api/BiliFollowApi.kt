package dev.openbili.webdemo.api

/**
 * 关注关系接口。
 *
 * 覆盖关注列表（按关键词搜索/分组内成员）、关注/取关、关注分组（分组列表与移动
 * 用户到分组），以及单用户关注状态查询 [isFollowing]。
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
 * 关注关系 API 集合。
 */
object BiliFollowApi {

  /** 分页拉取关注列表；keyword 非空时走关注列表内搜索接口。 */
  fun getFollowings(
    mid: Long,
    page: Int = 1,
    keyword: String = "",
    orderType: String = "",
  ): FollowingResponse {
    val path = if (keyword.isBlank()) "followings" else "followings/search"
    val encodedKeyword = URLEncoder.encode(keyword.trim(), "UTF-8")
    val encodedOrderType = URLEncoder.encode(orderType, "UTF-8")
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/relation/$path?vmid=$mid&pn=$page&ps=50" +
          "&order=desc&order_type=$encodedOrderType" +
          if (keyword.isBlank()) "" else "&name=$encodedKeyword"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data = json.optJSONObject("data") ?: return FollowingResponse(emptyList(), 0, false)
    val total = data.optInt("total")
    val array = data.optJSONArray("list") ?: return FollowingResponse(emptyList(), total, false)
    val items = parseFollowingUsers(array)
    return FollowingResponse(items, total, page * 50 < total)
  }

  /** 分页拉取指定关注分组内的成员。 */
  fun getFollowingGroupMembers(
    groupId: Long,
    page: Int = 1,
    orderType: String = "",
  ): FollowingResponse {
    val encodedOrderType = URLEncoder.encode(orderType, "UTF-8")
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/relation/BiliApiCommon.TAG?tagid=$groupId&pn=$page&ps=50" +
          "&order_type=$encodedOrderType"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data = json.opt("data")
    val array =
      when (data) {
        is org.json.JSONArray -> data
        is JSONObject -> data.optJSONArray("list")
        else -> null
      } ?: return FollowingResponse(emptyList(), 0, false)
    val items = parseFollowingUsers(array)
    val total = (data as? JSONObject)?.optInt("total", items.size) ?: items.size
    return FollowingResponse(items, total, items.size >= 50)
  }

  private fun parseFollowingUsers(array: org.json.JSONArray): List<FollowingUser> = buildList {
    for (i in 0 until array.length()) {
      val item = array.optJSONObject(i) ?: continue
      val groups = item.optJSONArray("BiliApiCommon.TAG")
      add(
        FollowingUser(
          item.optLong("mid"),
          item.optString("uname"),
          dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("face")).orEmpty(),
          item.optString("sign"),
          groupIds =
            if (groups == null) emptyList()
            else
              buildList {
                for (index in 0 until groups.length()) add(groups.optLong(index))
              },
          officialVerification = BiliCommentApi.parseListedUserOfficialVerification(item),
        )
      )
    }
  }

  /** 关注或取关：act=1 关注、act=2 取关。 */
  fun setFollowing(mid: Long, follow: Boolean) {
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/relation/modify",
        mapOf(
          "fid" to mid.toString(),
          "act" to if (follow) "1" else "2",
          "re_src" to "11",
          "csrf" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "操作失败"))
  }

  /** 查询是否已关注某用户（attribute 第 2 位表示已关注）。 */
  fun isFollowing(mid: Long): Boolean {
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/relation?fid=$mid")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val attribute = json.optJSONObject("data")?.optInt("attribute") ?: 0
    return attribute and 2 != 0
  }

  /** 拉取账号的关注分组列表。 */
  fun getFollowingGroups(): List<FollowingGroup> {
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/relation/tags")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val groups = json.optJSONArray("data") ?: return emptyList()
    return buildList {
      for (index in 0 until groups.length()) {
        val group = groups.optJSONObject(index) ?: continue
        add(
          FollowingGroup(
            id = group.optLong("tagid"),
            name = group.optString("name").ifBlank { "默认分组" },
            count = group.optInt("count"),
          )
        )
      }
    }
  }

  /** 把用户移动到指定关注分组。 */
  fun setFollowingGroup(mid: Long, groupId: Long) {
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/relation/tags/addUsers",
        mapOf("fids" to mid.toString(), "tagids" to groupId.toString(), "csrf" to csrf),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "分组失败"))
  }
}
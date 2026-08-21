package dev.openbili.webdemo.api

/**
 * 个人空间接口。
 *
 * 覆盖空间资料、投稿分页、动态（双列卡片与点赞/删除/置顶）、合集与合集内分页、
 * 动态首页 UP 列表与单 UP 动态过滤。
 */

import android.os.SystemClock
import android.util.Log
import androidx.core.text.HtmlCompat
import dev.openbili.webdemo.UrlPolicy
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
import dev.openbili.webdemo.live.LiveSearchRoom
import org.json.JSONTokener


/**
 * 个人空间 API 集合。
 */
object BiliSpaceApi {

  private const val WEB_DYNAMIC_FEATURES =
    "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,decorationCard," +
      "onlyfansAssetsV2,forwardListHidden,ugcDelete,onlyfansQaCard,commentsNewVersion," +
      "avatarAutoTheme,sunflowerStyle,cardsEnhance,eva3CardOpus,eva3CardVideo," +
      "eva3CardComment,eva3CardVote,eva3CardUser"

  /** 读取个人空间资料。 */
  fun getSpaceProfile(mid: Long): SpaceProfile {
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/space/wbi/acc/info?" +
          BiliApiCommon.signedQuery(mapOf("mid" to mid.toString()))
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data = json.getJSONObject("data")
    val statResp = BiliHttpClient.get("https://api.bilibili.com/x/relation/stat?vmid=$mid")
    val statJson = JSONObject(statResp.body?.string().orEmpty())
    statResp.close()
    val stat = statJson.optJSONObject("data")
    val banner =
      dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(
          data.optString("top_photo"),
          baseUrl = "https://i0.hdslb.com/",
        )
        .orEmpty()
    val officialVerification =
      BiliCommentApi.parseOfficialVerification(
        primary = data.optJSONObject("official"),
        legacy = data.optJSONObject("official_verify"),
      )
    Log.d(BiliApiCommon.TAG, "space profile: mid=$mid banner=${banner.isNotBlank()}")
    return SpaceProfile(
      mid,
      data.optString("name"),
      dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(data.optString("face")).orEmpty(),
      banner,
      data.optString("sign"),
      stat?.optLong("follower") ?: 0,
      stat?.optLong("following") ?: 0,
      sex = data.optString("sex", "保密"),
      level = data.optInt("level"),
      vipActive = data.optJSONObject("vip")?.optInt("status") == 1,
      vipLabel = data.optJSONObject("vip")?.optJSONObject("label")?.optString("text").orEmpty(),
      vipIconUrl =
        dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(
            data
              .optJSONObject("vip")
              ?.optJSONObject("label")
              ?.optString("img_label_uri_hans_static")
              .orEmpty()
          )
          .orEmpty(),
      officialVerification = officialVerification,
      ipLocation = getSpaceIpLocation(mid),
      followed = data.optBoolean("is_followed", false),
    )
  }

  private fun getSpaceIpLocation(mid: Long): String {
    val accessToken = BiliHttpClient.appAccessToken() ?: return ""
    return runCatching {
        val params =
          linkedMapOf(
            "access_key" to accessToken,
            "build" to "8000000",
            "mobi_app" to "android",
            "ts" to (System.currentTimeMillis() / 1_000L).toString(),
            "vmid" to mid.toString(),
          )
        val response =
          BiliHttpClient.get(
            "https://app.bilibili.com/x/v2/space?" +
              AppSigner.query(AppSigningOperation.SPACE_PROFILE, params)
          )
        val json = JSONObject(response.body?.string().orEmpty())
        response.close()
        if (json.optInt("code") != 0) return@runCatching ""
        parseSpaceIpLocation(json.optJSONObject("data")?.optJSONObject("card"))
      }
      .getOrDefault("")
  }

  internal fun parseSpaceIpLocation(card: JSONObject?): String {
    if (card == null) return ""
    for (field in listOf("space_tag", "space_tag_bottom")) {
      val tags = card.optJSONArray(field) ?: continue
      for (index in 0 until tags.length()) {
        val raw = tags.optJSONObject(index)?.optString("title").orEmpty().trim()
        val location = raw.removePrefix("IP属地：").removePrefix("IP属地:").trim()
        if (location.isNotBlank()) return location
      }
    }
    return ""
  }

  /** 分页拉取空间投稿视频。 */
  fun getSpaceVideos(mid: Long, page: Int): SpaceVideoResponse {
    val params =
      mapOf("mid" to mid.toString(), "pn" to page.toString(), "ps" to "20", "order" to "pubdate")
    val resp =
      BiliHttpClient.get("https://api.bilibili.com/x/space/wbi/arc/search?" + BiliApiCommon.signedQuery(params))
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data = json.getJSONObject("data")
    val array = data.optJSONObject("list")?.optJSONArray("vlist")
    val cards = buildList {
      if (array != null)
        for (i in 0 until array.length()) runCatching { FeedCard.fromJson(array.getJSONObject(i)) }
          .onSuccess(::add)
    }
    val count = data.optJSONObject("page")?.optInt("count") ?: cards.size
    return SpaceVideoResponse(cards, page * 20 < count)
  }

  /** 按游标拉取空间动态。 */
  fun getSpaceDynamics(mid: Long, offset: String = ""): SpaceDynamicResponse {
    val offsetQuery =
      offset
        .takeIf(String::isNotBlank)
        ?.let {
          "&offset=${URLEncoder.encode(it, "UTF-8")}"
        }
        .orEmpty()
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space?" +
          "host_mid=$mid&platform=web&web_location=333.1365&features=" +
          "${URLEncoder.encode(WEB_DYNAMIC_FEATURES, "UTF-8")}$offsetQuery"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseSpaceDynamics(json)
  }

  internal fun parseSpaceDynamics(json: JSONObject): SpaceDynamicResponse {
    val data = json.optJSONObject("data") ?: return SpaceDynamicResponse(emptyList())
    val items = data.optJSONArray("items") ?: return SpaceDynamicResponse(emptyList())
    val parsed = buildList {
      for (i in 0 until items.length()) {
        val row = items.optJSONObject(i) ?: continue
        val modules = row.optJSONObject("modules") ?: JSONObject()
        val dynamic = modules.optJSONObject("module_dynamic") ?: JSONObject()
        val dynamicRichText = parseDynamicRichText(dynamic)
        val originalDynamic =
          row.optJSONObject("orig")?.optJSONObject("modules")?.optJSONObject("module_dynamic")
        val originalRichText = parseDynamicRichText(originalDynamic)
        val major = dynamic.optJSONObject("major")
        val originalMajor = originalDynamic?.optJSONObject("major")
        // 转发动态通常只有 orig 带有主体，顶层 major 为空；后续字段统一以原动态回退。
        val sourceMajor = major ?: originalMajor
        val sourceArchive = sourceMajor?.optJSONObject("archive")
        val article = sourceMajor?.optJSONObject("article")
        val liveRcmd = sourceMajor?.optJSONObject("live_rcmd")
        val liveContent = parseJsonObject(liveRcmd?.optString("content"))
        val upower = sourceMajor?.optJSONObject("upower_common")
        val common = sourceMajor?.optJSONObject("common")
        val live = sourceMajor?.optJSONObject("live")
        val pgc = sourceMajor?.optJSONObject("pgc")
        val ugcSeason = sourceMajor?.optJSONObject("ugc_season")
        val music = sourceMajor?.optJSONObject("music")
        val courses = sourceMajor?.optJSONObject("courses")
        val originalLiveContent =
          parseJsonObject(originalMajor?.optJSONObject("live_rcmd")?.optString("content"))
        val additional =
          dynamic.optJSONObject("additional") ?: originalDynamic?.optJSONObject("additional")
        val desc =
          firstNonBlank(
            liveContent?.let(::liveTitle),
            upower?.let(::upowerTitle),
            dynamicRichText.text,
            originalLiveContent?.let(::liveTitle),
            originalMajor?.optJSONObject("upower_common")?.let(::upowerTitle),
            originalRichText.text,
          )
        val originalModules = row.optJSONObject("orig")?.optJSONObject("modules")
        val author =
          modules.optJSONObject("module_author")
            ?: originalModules?.optJSONObject("module_author")
            ?: JSONObject()
        val liveRoom =
          parseDynamicLiveRoom(liveContent, live, author)
        val stat =
          modules.optJSONObject("module_stat")
            ?: originalModules?.optJSONObject("module_stat")
            ?: JSONObject()
        val basic = row.optJSONObject("basic") ?: JSONObject()
        val dynamicId = row.optString("id_str", "dynamic_$i")
        val dynamicEmotes = buildMap {
          putAll(dynamicRichText.emotes)
          putAll(originalRichText.emotes)
        }
        val dynamicImageArrays = buildList {
          fun collect(node: JSONObject?) {
            val nodeMajor = node?.optJSONObject("major") ?: return
            nodeMajor.optJSONObject("draw")?.optJSONArray("items")?.let(::add)
            nodeMajor.optJSONObject("opus")?.optJSONArray("pics")?.let(::add)
            nodeMajor.optJSONObject("article")?.optJSONArray("covers")?.let(::add)
            nodeMajor.optJSONObject("upower_common")?.let { node ->
              node.optJSONObject("background")?.let { background ->
                listOf("light_src", "dark_src").forEach { key ->
                  background.optString(key).takeIf(String::isNotBlank)?.let { url ->
                    add(JSONArray().put(JSONObject().put("url", url)))
                  }
                }
              }
            }
            nodeMajor.optJSONObject("common")?.optString("cover")?.takeIf(String::isNotBlank)?.let {
              add(JSONArray().put(JSONObject().put("url", it)))
            }
            nodeMajor.optJSONObject("live")?.optString("cover")?.takeIf(String::isNotBlank)?.let {
              add(JSONArray().put(JSONObject().put("url", it)))
            }
            nodeMajor.optJSONObject("pgc")?.optString("cover")?.takeIf(String::isNotBlank)?.let {
              add(JSONArray().put(JSONObject().put("url", it)))
            }
            nodeMajor.optJSONObject("ugc_season")?.optString("cover")?.takeIf(String::isNotBlank)?.let {
              add(JSONArray().put(JSONObject().put("url", it)))
            }
            nodeMajor.optJSONObject("music")?.optString("cover")?.takeIf(String::isNotBlank)?.let {
              add(JSONArray().put(JSONObject().put("url", it)))
            }
            nodeMajor.optJSONObject("courses")?.optString("cover")?.takeIf(String::isNotBlank)?.let {
              add(JSONArray().put(JSONObject().put("url", it)))
            }
          }
          collect(dynamic)
          collect(originalDynamic)
          liveContent?.let { content ->
            liveImageUrls(content).forEach { url ->
              add(JSONArray().put(JSONObject().put("url", url)))
            }
          }
          originalLiveContent?.let { content ->
            liveImageUrls(content).forEach { url ->
              add(JSONArray().put(JSONObject().put("url", url)))
            }
          }
          additional?.optJSONObject("common")?.optString("cover")?.takeIf(String::isNotBlank)?.let {
            add(JSONArray().put(JSONObject().put("url", it)))
          }
          additional?.optJSONObject("ugc")?.optString("cover")?.takeIf(String::isNotBlank)?.let {
            add(JSONArray().put(JSONObject().put("url", it)))
          }
        }
        val images =
          buildList<SpaceDynamicImage> {
            dynamicImageArrays.forEach { imageArray ->
              for (imageIndex in 0 until imageArray.length()) {
                val image = imageArray.optJSONObject(imageIndex)
                val rawUrl =
                  listOf("src", "url", "cover", "img_src", "image_url").firstNotNullOfOrNull { key
                    ->
                    image?.optString(key)?.takeIf(String::isNotBlank)
                  } ?: imageArray.optString(imageIndex)
                val imageBase =
                  if (rawUrl.startsWith("/bfs/")) "https://i0.hdslb.com/"
                  else "https://www.bilibili.com/"
                val normalized =
                  dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(rawUrl, imageBase).orEmpty()
                if (normalized.isNotBlank() && none { it.url == normalized }) {
                  add(
                    SpaceDynamicImage(
                      url = normalized,
                      width =
                        image?.optInt("width")?.takeIf { it > 0 }
                          ?: image?.optInt("img_width")
                          ?: 0,
                      height =
                        image?.optInt("height")?.takeIf { it > 0 }
                          ?: image?.optInt("img_height")
                          ?: 0,
                    )
                  )
                }
              }
            }
          }
        val additionalUgc = additional?.optJSONObject("ugc")
        val video = sourceArchive?.let {
          SpaceDynamicVideo(
            aid = it.optLong("aid"),
            bvid =
              it.optString("bvid").ifBlank {
                it.optLong("aid").takeIf { aid -> aid > 0L }?.let { aid -> "av$aid" }.orEmpty()
              },
            title = it.optString("title"),
            description = it.optString("desc"),
            coverUrl =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(it.optString("cover")).orEmpty(),
            duration = it.optString("duration_text"),
            playCount = it.optJSONObject("stat")?.optString("play").orEmpty(),
            danmakuCount = it.optJSONObject("stat")?.optString("danmaku").orEmpty(),
          )
        } ?: additionalUgc?.let(::parseAdditionalVideo)
        val fallbackText =
          firstNonBlank(
            article?.optString("title"),
            article?.optString("desc"),
            upower?.let { upowerTitle(it) },
            liveContent?.let { liveTitle(it) },
            live?.optString("title"),
            common?.optString("title"),
            common?.optString("desc"),
            pgc?.optString("title"),
            ugcSeason?.optString("title"),
            music?.optString("title"),
            courses?.optString("title"),
            live?.optString("desc_first"),
            live?.optString("desc_second"),
            additional?.optJSONObject("common")?.optString("title"),
            additional?.optJSONObject("common")?.optString("desc1"),
            additional?.optJSONObject("common")?.optString("desc2"),
          )
        val requestedCommentType = basic.optInt("comment_type", 17).takeIf { it > 0 } ?: 17
        val requestedCommentOid =
          basic.optString("comment_id_str").toLongOrNull()
            ?: basic.optLong("rid_str").takeIf { it > 0 }
            ?: dynamicId.toLongOrNull()
            ?: 0L
        val commentType = if (video?.aid ?: 0L > 0L) 1 else requestedCommentType
        val commentOid = if (video?.aid ?: 0L > 0L) video!!.aid else requestedCommentOid
        val articleId =
          article?.optLong("id")?.takeIf { it > 0L } ?: requestedCommentOid
        val articleItem =
          if (video == null && article != null && articleId > 0L &&
            (requestedCommentType == 12 || article.optLong("id") > 0L)
          ) {
            val articleTitle =
              article.optString("title").ifBlank {
                desc.lineSequence().firstOrNull().orEmpty().take(80)
              }
            ArticleItem(
              id = articleId,
              title = articleTitle.ifBlank { "专栏" },
              summary =
                article.optString("desc").ifBlank {
                  article.optString("summary").ifBlank { desc }
                },
              coverUrl = images.firstOrNull()?.url.orEmpty(),
              authorName = author.optString("name"),
              authorFace =
                dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(author.optString("face"))
                  .orEmpty(),
              authorMid = author.optLong("mid"),
              publishedAt = author.optLong("pub_ts"),
              likeCount = stat.optJSONObject("like")?.optLong("count") ?: 0L,
              replyCount = stat.optJSONObject("comment")?.optLong("count") ?: 0L,
              sourceUrl =
                dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(
                    article.optString("jump_url"),
                    baseUrl = "https://www.bilibili.com/",
                  )
                  .orEmpty()
                  .ifBlank { "https://www.bilibili.com/read/cv$articleId" },
            )
          } else null
        add(
          SpaceDynamicItem(
            id = dynamicId,
            text = desc.ifBlank { fallbackText },
            emotes = dynamicEmotes,
            publishTimestamp = author.optLong("pub_ts"),
            authorMid = author.optLong("mid"),
            authorName = author.optString("name"),
            authorFace =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(author.optString("face")).orEmpty(),
            images = images,
            video = video,
            article = articleItem,
            live = liveRoom,
            commentOid = commentOid,
            commentType = commentType,
            commentCount = stat.optJSONObject("comment")?.optLong("count") ?: 0L,
            likeCount = stat.optJSONObject("like")?.optLong("count") ?: 0L,
            liked =
              stat.optJSONObject("like")?.let { like ->
                like.optBoolean("status") || like.optInt("status") == 1
              } ?: false,
            pinned =
              modules.optJSONObject("module_tag")?.let { tag ->
                tag.optString("text").contains("置顶") || tag.optInt("tag_type") == 1
              } ?: false,
            repostCount = stat.optJSONObject("forward")?.optLong("count") ?: 0L,
          )
        )
      }
    }
    return SpaceDynamicResponse(
      items = parsed,
      offset = data.optString("offset"),
      hasMore = data.optBoolean("has_more"),
    )
  }

  private fun parseDynamicRichText(dynamic: JSONObject?): ParsedDynamicRichText {
    if (dynamic == null) return ParsedDynamicRichText()
    val major = dynamic.optJSONObject("major")
    val opus = major?.optJSONObject("opus")
    val liveContent = parseJsonObject(major?.optJSONObject("live_rcmd")?.optString("content"))
    val candidates =
      listOfNotNull(
        dynamic.optJSONObject("desc"),
        opus?.optJSONObject("summary"),
        opus?.optJSONObject("title"),
        opus,
        major?.optJSONObject("article"),
        major?.optJSONObject("upower_common"),
        major?.optJSONObject("common"),
        major?.optJSONObject("live"),
        major?.optJSONObject("pgc"),
        major?.optJSONObject("ugc_season"),
        major?.optJSONObject("music"),
        liveContent,
      )
    val opusTitle = opus?.optString("title").orEmpty().trim()
    val opusSummary = opus?.optJSONObject("summary")?.let(::dynamicRichTextValue).orEmpty().trim()
    val opusText =
      when {
        opusTitle.isNotBlank() && opusSummary.isNotBlank() && opusTitle != opusSummary ->
          "$opusTitle\n$opusSummary"
        opusTitle.isNotBlank() -> opusTitle
        else -> opusSummary
      }
    val text =
      firstNonBlank(
        dynamic.optJSONObject("desc")?.let(::dynamicRichTextValue),
        opusText,
        candidates.asSequence().map(::dynamicRichTextValue).firstOrNull(String::isNotBlank),
      )
    val emotes = buildMap {
      candidates.forEach { candidate ->
        putAll(parseDynamicEmotes(candidate.optJSONArray("rich_text_nodes")))
      }
    }
    return ParsedDynamicRichText(text, emotes)
  }

  private fun parseJsonObject(raw: String?): JSONObject? {
    val value = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
    return runCatching {
        when (val parsed = JSONTokener(value).nextValue()) {
          is JSONObject -> parsed
          is String ->
            parsed.trim().takeIf(String::isNotBlank)?.let { nested ->
              JSONTokener(nested).nextValue() as? JSONObject
            }
          else -> null
        }
      }
      .getOrNull()
  }

  private fun nestedJsonObjects(root: JSONObject, maxDepth: Int = 3): List<JSONObject> {
    val result = mutableListOf<JSONObject>()
    fun visit(node: JSONObject, depth: Int) {
      result += node
      if (depth >= maxDepth) return
      val keys = node.keys()
      while (keys.hasNext()) {
        val child = node.optJSONObject(keys.next()) ?: continue
        visit(child, depth + 1)
      }
    }
    visit(root, 0)
    return result
  }

  private fun firstJsonString(root: JSONObject, vararg keys: String): String =
    nestedJsonObjects(root)
      .asSequence()
      .flatMap { node -> keys.asSequence().map { key -> node.optString(key) } }
      .firstOrNull(String::isNotBlank)
      .orEmpty()

  private fun liveImageUrls(content: JSONObject): List<String> =
    nestedJsonObjects(content)
      .asSequence()
      .flatMap { node ->
        sequenceOf(
          node.optString("cover"),
          node.optString("keyframe"),
          node.optString("cover_from_user"),
          node.optString("user_cover"),
        )
      }
      .filter(String::isNotBlank)
      .distinct()
      .toList()

  private fun firstJsonLong(root: JSONObject, vararg keys: String): Long =
    nestedJsonObjects(root)
      .asSequence()
      .flatMap { node ->
        keys.asSequence().mapNotNull { key ->
          node.optString(key).toLongOrNull() ?: node.optLong(key).takeIf { it > 0L }
        }
      }
      .firstOrNull { it > 0L } ?: 0L

  private fun parseDynamicLiveRoom(
    content: JSONObject?,
    fallback: JSONObject?,
    author: JSONObject,
  ): LiveSearchRoom? {
    val source = content ?: fallback ?: return null
    val roomId = firstJsonLong(source, "room_id", "roomid", "roomid_str", "roomId", "id")
    if (roomId <= 0L) return null
    val uid =
      firstJsonLong(source, "uid", "mid", "anchor_uid", "anchor_mid")
        .takeIf { it > 0L }
        ?: author.optLong("mid").takeIf { it > 0L }
        ?: 0L
    val title = firstJsonString(source, "title", "roomname", "room_name").ifBlank { "直播间 $roomId" }
    val uname =
      firstJsonString(source, "uname", "anchor_name", "user_name", "name")
        .ifBlank { author.optString("name").ifBlank { "主播" } }
    val image = liveImageUrls(source).firstOrNull()
    val watched =
      firstJsonString(source, "watched_text", "online_text", "online", "watched_show")
        .takeIf(String::isNotBlank)
    val status = firstJsonLong(source, "live_status", "liveStatus", "status").toInt().let { if (it == 0) 1 else it }
    return LiveSearchRoom(
      roomId = roomId,
      shortRoomId = firstJsonLong(source, "short_id", "short_room_id").takeIf { it > 0L },
      uid = uid,
      title = title,
      uname = uname,
      faceUrl =
        firstJsonString(source, "uface", "face", "face_url")
          .takeIf(String::isNotBlank)
          ?.let { UrlPolicy.normalizeImageUrl(it) },
      coverUrl = image?.let { UrlPolicy.normalizeImageUrl(it) },
      keyframeUrl =
        firstJsonString(source, "keyframe", "keyframe_url", "system_cover")
          .takeIf(String::isNotBlank)
          ?.let { UrlPolicy.normalizeImageUrl(it) },
      areaName = firstJsonString(source, "area_name", "area_v2_name").takeIf(String::isNotBlank),
      parentAreaName =
        firstJsonString(source, "parent_area_name", "area_v2_parent_name")
          .takeIf(String::isNotBlank),
      watchedText = watched,
      liveStatus = status,
    )
  }

  private fun firstNonBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

  private fun upowerTitle(upower: JSONObject): String =
    firstNonBlank(
      upower.optString("title_prefix"),
      upower.optString("title"),
      upower.optJSONObject("button")?.optJSONObject("jump_style")?.optString("text"),
    ).let { title ->
      val suffix = upower.optString("title").takeIf(String::isNotBlank)
      if (suffix != null && title != suffix) "$title $suffix" else title
    }

  private fun liveTitle(content: JSONObject): String =
    firstJsonString(content, "uname", "anchor_name", "user_name").let { uname ->
      val title = firstJsonString(content, "title", "roomname", "room_name")
      val area = firstJsonString(content, "area_name", "parent_area_name")
      when {
        uname.isNotBlank() && title.isNotBlank() && uname != title -> "$uname：$title"
        title.isNotBlank() -> title
        uname.isNotBlank() -> uname
        else -> area
      }
    }

  private fun parseAdditionalVideo(ugc: JSONObject): SpaceDynamicVideo? {
    val jumpUrl = ugc.optString("jump_url")
    val bvid =
      Regex("(?:/video/|^)(BV[0-9A-Za-z]{10})", RegexOption.IGNORE_CASE)
        .find(jumpUrl)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { "BV" + it.drop(2) }
    val aid =
      ugc.optString("id_str").removePrefix("av").toLongOrNull()
        ?: Regex("(?:av|aid=)(\\d+)", RegexOption.IGNORE_CASE)
          .find(jumpUrl)
          ?.groupValues
          ?.getOrNull(1)
          ?.toLongOrNull()
        ?: 0L
    val reference = bvid ?: aid.takeIf { it > 0L }?.let { "av$it" } ?: return null
    return SpaceDynamicVideo(
      aid = aid,
      bvid = reference,
      title = ugc.optString("title"),
      description = ugc.optString("desc_second"),
      coverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(ugc.optString("cover")).orEmpty(),
      duration = ugc.optString("duration"),
    )
  }

  private fun dynamicRichTextValue(container: JSONObject): String =
    sequenceOf("text", "title", "desc", "summary")
      .mapNotNull { key -> (container.opt(key) as? String)?.takeIf(String::isNotBlank) }
      .firstOrNull()
      ?: container
        .optJSONArray("rich_text_nodes")
        ?.let { nodes ->
          buildString {
            for (index in 0 until nodes.length()) {
              val node = nodes.optJSONObject(index) ?: continue
              append(
                node.optString("text").ifBlank {
                  node.optString("orig_text").ifBlank {
                    node.optJSONObject("emoji")?.optString("text").orEmpty()
                  }
                }
              )
            }
          }
        }
        .orEmpty()

  private fun parseDynamicEmotes(nodes: JSONArray?): Map<String, String> = buildMap {
    if (nodes == null) return@buildMap
    for (index in 0 until nodes.length()) {
      val node = nodes.optJSONObject(index) ?: continue
      val emoji = node.optJSONObject("emoji")
      val token =
        node.optString("text").ifBlank {
          node.optString("orig_text").ifBlank { emoji?.optString("text").orEmpty() }
        }
      val rawUrl =
        emoji
          ?.optString("icon_url")
          .orEmpty()
          .ifBlank { emoji?.optString("iconUrl").orEmpty() }
          .ifBlank { emoji?.optString("url").orEmpty() }
          .ifBlank { node.optString("icon_url") }
      val url = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(rawUrl).orEmpty()
      if (token.isNotBlank() && url.isNotBlank()) put(token, url)
    }
  }

  private data class ParsedDynamicRichText(
    val text: String = "",
    val emotes: Map<String, String> = emptyMap(),
  )

  fun setDynamicLike(dynamicId: String, liked: Boolean, uid: Long) {
    require(dynamicId.isNotBlank() && uid > 0L) { "动态点赞参数无效" }
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.vc.bilibili.com/dynamic_like/v1/dynamic_like/thumb",
        mapOf(
          "dynamic_id" to dynamicId,
          "up" to if (liked) "1" else "2",
          "uid" to uid.toString(),
          "csrf" to csrf,
          "csrf_token" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "点赞失败"))
  }

  fun deleteDynamic(dynamicId: String) {
    require(dynamicId.isNotBlank()) { "动态删除参数无效" }
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/rm_dynamic",
        mapOf("dynamic_id" to dynamicId, "csrf" to csrf, "csrf_token" to csrf),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "删除失败"))
  }

  fun setDynamicPinned(dynamicId: String, pinned: Boolean) {
    require(dynamicId.isNotBlank()) { "动态置顶参数无效" }
    val csrf = BiliApiCommon.requireCsrf()
    val endpoint = if (pinned) "set_top" else "rm_top"
    val resp =
      BiliHttpClient.postJson(
        "https://api.bilibili.com/x/dynamic/feed/space/$endpoint" +
          "?csrf=${URLEncoder.encode(csrf, "UTF-8")}",
        JSONObject().put("dyn_str", dynamicId).toString(),
      )
    val statusCode = resp.code
    val contentType = resp.header("Content-Type").orEmpty()
    val body = resp.body?.string().orEmpty()
    resp.close()
    if (!body.trimStart().startsWith("{")) {
      Log.w(
        BiliApiCommon.TAG,
        "dynamic pin returned non-JSON: status=$statusCode type=$contentType len=${body.length}",
      )
      throw IllegalStateException(
        if (statusCode == 412) "置顶请求触发了风控，请稍后重试" else "置顶接口暂时返回了网页内容，请稍后重试"
      )
    }
    val json =
      runCatching { JSONObject(body) }.getOrElse { throw IllegalStateException("置顶接口响应格式异常，请稍后重试") }
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "置顶失败"))
  }

  /** 拉取空间合集列表。 */
  fun getSpaceCollections(mid: Long): List<SpaceContentCard> {
    val resp = BiliHttpClient.get(spaceCollectionsUrl(mid))
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseSpaceCollections(json)
  }

  internal fun spaceCollectionsUrl(mid: Long): String {
    require(mid > 0L) { "用户 UID 无效" }
    // 该接口的 page_size 超过 20 会返回 -400。
    return "https://api.bilibili.com/x/polymer/web-space/seasons_series_list" +
      "?mid=$mid&page_num=1&page_size=20"
  }

  internal fun parseSpaceCollections(json: JSONObject): List<SpaceContentCard> {
    val data = json.optJSONObject("data") ?: return emptyList()
    // 当前网页响应把两个列表都嵌在 items_lists 下：保留旧结构作为兜底，让缓存或
    // 代理返回的上一版协议响应仍可解析。
    val lists = data.optJSONObject("items_lists") ?: data
    return buildList {
      listOf("seasons_list", "series_list").forEach { key ->
        val array = lists.optJSONArray(key) ?: return@forEach
        for (i in 0 until array.length()) {
          val row = array.optJSONObject(i) ?: continue
          val meta = row.optJSONObject("meta") ?: row
          val collectionType =
            if (key == "seasons_list") SpaceCollectionType.SEASON else SpaceCollectionType.SERIES
          val collectionId =
            if (collectionType == SpaceCollectionType.SEASON) meta.optLong("season_id")
            else meta.optLong("series_id")
          add(
            SpaceContentCard(
              id = "$key:${collectionId.takeIf { it > 0L } ?: i.toLong()}",
              title = meta.optString("name", meta.optString("title", "合集或系列")),
              subtitle = meta.optString("description"),
              coverUrl =
                dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(meta.optString("cover")).orEmpty(),
              collectionId = collectionId,
              collectionType = collectionType,
              collectionTotal = meta.optInt("total"),
            )
          )
        }
      }
    }
  }

  fun getSpaceCollectionVideos(
    mid: Long,
    collection: SpaceContentCard,
    page: Int,
  ): SpaceCollectionVideoResponse {
    val pageSize = 30
    val resp = BiliHttpClient.get(spaceCollectionVideosUrl(mid, collection, page, pageSize))
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseSpaceCollectionVideos(json, page, pageSize)
  }

  internal fun spaceCollectionVideosUrl(
    mid: Long,
    collection: SpaceContentCard,
    page: Int,
    pageSize: Int = 30,
  ): String {
    require(mid > 0L) { "用户 UID 无效" }
    require(collection.collectionId > 0L) { "合集或系列标识无效" }
    require(page > 0 && pageSize in 1..30) { "合集或系列页码无效" }
    return when (collection.collectionType) {
      SpaceCollectionType.SEASON ->
        "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list" +
          "?mid=$mid&season_id=${collection.collectionId}&sort_reverse=false" +
          "&page_num=$page&page_size=$pageSize"
      SpaceCollectionType.SERIES ->
        "https://api.bilibili.com/x/series/archives" +
          "?mid=$mid&current_mid=0&series_id=${collection.collectionId}" +
          "&only_normal=true&sort=desc&pn=$page&ps=$pageSize"
    }
  }

  internal fun parseSpaceCollectionVideos(
    json: JSONObject,
    requestedPage: Int,
    requestedPageSize: Int,
  ): SpaceCollectionVideoResponse {
    val data =
      json.optJSONObject("data") ?: return SpaceCollectionVideoResponse(emptyList(), false, 0)
    val array = data.optJSONArray("archives")
    val cards = buildList {
      if (array != null) {
        for (i in 0 until array.length()) {
          runCatching { FeedCard.fromJson(array.getJSONObject(i)) }
            .onSuccess { if (it.bvid.isNotBlank() || it.aid > 0L) add(it) }
        }
      }
    }
    val page = data.optJSONObject("page")
    val pageNumber = page?.optInt("page_num", page.optInt("num", requestedPage)) ?: requestedPage
    val pageSize =
      page?.optInt("page_size", page.optInt("size", requestedPageSize)) ?: requestedPageSize
    val total = page?.optInt("total", cards.size) ?: cards.size
    return SpaceCollectionVideoResponse(
      cards = cards,
      hasMore = pageNumber * pageSize < total,
      total = total,
    )
  }

  /** 拉取动态首页 UP 主列表。 */
  fun getHomeDynamicUploaders(offset: String = ""): HomeDynamicUploaderResponse {
    val offsetQuery =
      offset
        .takeIf(String::isNotBlank)
        ?.let {
          "&offset=${URLEncoder.encode(it, "UTF-8")}"
        }
        .orEmpty()
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/polymer/web-dynamic/v1/portal?" +
          "up_list_more=1&web_location=333.1365$offsetQuery"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseHomeDynamicUploaders(json)
  }

  internal fun parseHomeDynamicUploaders(json: JSONObject): HomeDynamicUploaderResponse {
    val data = json.optJSONObject("data") ?: return HomeDynamicUploaderResponse()
    val upList = data.optJSONObject("up_list")
    val array = upList?.optJSONArray("items") ?: data.optJSONArray("up_list") ?: JSONArray()
    val liveNode = data.opt("live_users")
    val liveArray =
      when (liveNode) {
        is JSONArray -> liveNode
        is JSONObject -> liveNode.optJSONArray("items") ?: JSONArray()
        else -> JSONArray()
      }
    val liveMids = buildSet {
      for (index in 0 until liveArray.length()) {
        val row = liveArray.optJSONObject(index) ?: continue
        row.optLong("mid", row.optLong("uid")).takeIf { it > 0L }?.let(::add)
      }
    }
    val items =
      buildList {
          for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val mid = row.optLong("mid")
            if (mid <= 0L) continue
            add(
              HomeDynamicUploader(
                mid = mid,
                name = row.optString("uname", row.optString("name", "UP主")),
                face =
                  dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(row.optString("face")).orEmpty(),
                hasUpdate = row.optBoolean("has_update") || row.optInt("has_update") == 1,
                live = mid in liveMids,
              )
            )
          }
        }
        .distinctBy(HomeDynamicUploader::mid)
    return HomeDynamicUploaderResponse(
      items = items,
      offset = upList?.optString("offset").orEmpty(),
      hasMore = upList?.optBoolean("has_more") == true,
    )
  }

  /** 动态首页"全部/仅视频"信息流。 */
  /** 动态首页"全部/仅视频"信息流。 */
  fun getHomeDynamics(
    offset: String = "",
    videoOnly: Boolean = false,
  ): SpaceDynamicResponse {
    val offsetQuery =
      offset
        .takeIf(String::isNotBlank)
        ?.let {
          "&offset=${URLEncoder.encode(it, "UTF-8")}"
        }
        .orEmpty()
    val type = if (videoOnly) "video" else "all"
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all?" +
          "timezone_offset=-480&type=$type&platform=web&web_location=333.1365&features=" +
          "${URLEncoder.encode(WEB_DYNAMIC_FEATURES, "UTF-8")}$offsetQuery"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseSpaceDynamics(json)
  }

  /**
   * 动态首页的按 UP 过滤。官方网页在这里刻意使用带 host_mid 的 feed/all 而不是个人
   * 空间信息流；请求这第一页还会推进由 portal.up_list.items[].has_update 表示的
   * 服务端更新标记。
   */
  fun getHomeUploaderDynamics(mid: Long, offset: String = ""): SpaceDynamicResponse {
    val offsetQuery =
      offset
        .takeIf(String::isNotBlank)
        ?.let {
          "&offset=${URLEncoder.encode(it, "UTF-8")}"
        }
        .orEmpty()
    val features = URLEncoder.encode(WEB_DYNAMIC_FEATURES, "UTF-8")
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all?" +
          "host_mid=$mid&platform=web&web_location=333.1365&features=$features$offsetQuery"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseSpaceDynamics(json)
  }
}

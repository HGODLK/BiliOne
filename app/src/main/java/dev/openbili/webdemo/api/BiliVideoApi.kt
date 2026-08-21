package dev.openbili.webdemo.api

/**
 * 视频信息与播放地址接口。
 *
 * 覆盖视频/番剧播放地址（DASH 流优选、音轨选择、HDR/杜比回退）、视频资料缓存、
 * 在线人数、互动（点赞/投币）、相关推荐与帧率解析。
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
 * 视频信息与播放地址 API 集合。
 */
object BiliVideoApi {
  private const val VIDEO_INFO_CACHE_TTL_MS = 2 * 60 * 1000L
  private const val VIDEO_INFO_CACHE_LIMIT = 64
  private val videoInfoCache = ConcurrentHashMap<String, CachedVideoInfo>()
  private val videoInfoRequests = ConcurrentHashMap<String, BlockingRequest<VideoInfo?>>()

  // ── 视频信息 ────────────────────────────────────────────────────────────

  /** 按 BV 号读取视频资料（带进程内缓存与并发合并）。 */
  fun getVideoInfo(bvid: String): VideoInfo? {
    val key = bvid.trim()
    if (key.isBlank()) return null
    val now = SystemClock.elapsedRealtime()
    videoInfoCache[key]
      ?.takeIf { now - it.loadedAtMs < VIDEO_INFO_CACHE_TTL_MS }
      ?.let {
        return it.info
      }

    val request = BlockingRequest<VideoInfo?>()
    val active = videoInfoRequests.putIfAbsent(key, request)
    if (active != null) {
      return active.await()
    }

    return try {
      val loaded = requestVideoInfo(key)
      if (loaded != null) {
        val loadedAt = SystemClock.elapsedRealtime()
        videoInfoCache[key] = CachedVideoInfo(loaded, loadedAt)
        trimVideoInfoCache(loadedAt)
      }
      request.complete(loaded)
      loaded
    } catch (error: Throwable) {
      request.completeExceptionally(error)
      throw error
    } finally {
      videoInfoRequests.remove(key, request)
    }
  }

  private fun requestVideoInfo(bvid: String): VideoInfo? {
    return requestVideoInfoByParameter("bvid", bvid)
  }

  /** 按 aid 读取视频资料。 */
  fun getVideoInfoByAid(aid: Long): VideoInfo? {
    if (aid <= 0L) return null
    return requestVideoInfoByParameter("aid", aid.toString())
  }

  private fun requestVideoInfoByParameter(parameter: String, value: String): VideoInfo? {
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/view?$parameter=$value")
    val body = resp.body?.string().orEmpty()
    resp.close()
    val json = JSONObject(body)
    if (json.optInt("code") != 0) return null
    val data = json.getJSONObject("data")
    val stat = data.getJSONObject("stat")
    val owner = data.getJSONObject("owner")
    val pagesArr = data.optJSONArray("pages")
    val pages = mutableListOf<VideoPage>()
    if (pagesArr != null) {
      for (i in 0 until pagesArr.length()) {
        val p = pagesArr.getJSONObject(i)
        pages.add(
          VideoPage(
            page = p.getInt("page"),
            cid = p.getLong("cid"),
            part = p.optString("part", ""),
            durationSeconds = p.optLong("duration", 0),
          )
        )
      }
    }
    val collection =
      data.optJSONObject("ugc_season")?.let { season ->
        val sectionGroups = mutableListOf<VideoCollectionSection>()
        val episodes = buildList {
          val sections = season.optJSONArray("sections")
          if (sections != null) {
            for (sectionIndex in 0 until sections.length()) {
                val sectionEpisodes =
                  sections.optJSONObject(sectionIndex)?.optJSONArray("episodes") ?: continue
              val section = sections.optJSONObject(sectionIndex) ?: JSONObject()
              val sectionItems = buildList {
                for (episodeIndex in 0 until sectionEpisodes.length()) {
                  val episode = sectionEpisodes.optJSONObject(episodeIndex) ?: continue
                  val arc = episode.optJSONObject("arc") ?: JSONObject()
                  val author = arc.optJSONObject("author") ?: JSONObject()
                  val episodeStat = arc.optJSONObject("stat") ?: JSONObject()
                  val episodePage = episode.optJSONObject("page") ?: JSONObject()
                  val episodeBvid = episode.optString("bvid")
                  if (episodeBvid.isBlank()) continue
                  add(
                    VideoCollectionEpisode(
                      aid = arc.optLong("aid", episode.optLong("aid")),
                      bvid = episodeBvid,
                      cid = episodePage.optLong("cid", episode.optLong("cid")),
                      title = episode.optString("title", arc.optString("title")),
                      coverUrl =
                        dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(arc.optString("pic"))
                          .orEmpty(),
                      // page.duration 是当前子视频的时长，不能用合集或稿件总时长替代。
                      durationSeconds =
                        episodePage.optLong("duration")
                          .takeIf { it > 0L }
                          ?: episode.optLong("duration")
                            .takeIf { it > 0L }
                          ?: arc.optLong("duration"),
                      uploaderName = author.optString("name", owner.optString("name")),
                      uploaderFace =
                        dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(
                            author.optString("face", owner.optString("face"))
                          )
                          .orEmpty(),
                      uploaderMid = author.optLong("mid", owner.optLong("mid")),
                      playCount = episodeStat.optLong("view"),
                      danmakuCount = episodeStat.optLong("danmaku"),
                      publishedAt = arc.optLong("pubdate"),
                    )
                  )
                }
              }
              addAll(sectionItems)
              if (sectionItems.isNotEmpty()) {
                sectionGroups +=
                  VideoCollectionSection(
                    id = section.optLong("section_id", section.optLong("id", sectionIndex.toLong())),
                    title =
                      section.optString("title")
                        .ifBlank { section.optString("name") }
                        .ifBlank { "第 ${sectionIndex + 1} 组" },
                    episodes = sectionItems,
                  )
              }
            }
          }
        }
        VideoCollection(
            id = season.optLong("id"),
            title = season.optString("title").ifBlank { "视频合集" },
            episodes = episodes.distinctBy { it.bvid to it.cid },
            sections =
              sectionGroups.map {
                it.copy(episodes = it.episodes.distinctBy { episode -> episode.bvid to episode.cid })
              },
          )
          .takeIf { it.episodes.isNotEmpty() }
      }
    return VideoInfo(
      bvid = data.getString("bvid"),
      aid = data.getLong("aid"),
      cid = data.getLong("cid"),
      title = data.getString("title"),
      coverUrl = data.getString("pic"),
      uploaderName = owner.getString("name"),
      uploaderFace =
        dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(owner.getString("face")).orEmpty(),
      uploaderMid = owner.getLong("mid"),
      durationSeconds = data.optLong("duration", 0),
      playCount = stat.getLong("view"),
      danmakuCount = stat.getLong("danmaku"),
      replyCount = stat.getLong("reply"),
      likeCount = stat.getLong("like"),
      coinCount = stat.getLong("coin"),
      favoriteCount = stat.getLong("favorite"),
      shareCount = stat.getLong("share"),
      publishedAt = data.optLong("pubdate", 0),
      desc = data.optString("desc", ""),
      pages = pages,
      collection = collection,
      copyright = data.optInt("copyright", 0),
    )
  }

  private fun trimVideoInfoCache(now: Long) {
    videoInfoCache.entries.removeIf { now - it.value.loadedAtMs >= VIDEO_INFO_CACHE_TTL_MS }
    while (videoInfoCache.size > VIDEO_INFO_CACHE_LIMIT) {
      val oldest = videoInfoCache.entries.minByOrNull { it.value.loadedAtMs } ?: break
      videoInfoCache.remove(oldest.key, oldest.value)
    }
  }

  /** 读取"xx 人正在看"文案。 */
  fun getOnlineViewerText(aid: Long, cid: Long): String? {
    if (aid <= 0 || cid <= 0) return null
    val resp =
      BiliHttpClient.get("https://api.bilibili.com/x/player/online/total?aid=$aid&cid=$cid")
    val body = resp.body?.string().orEmpty()
    resp.close()
    val json = JSONObject(body)
    if (json.optInt("code") != 0) return null
    val data = json.optJSONObject("data") ?: return null
    val show = data.optJSONObject("show_switch")
    val total = data.optString("total").trim()
    val count = data.optString("count").trim()
    return when {
      show?.optBoolean("total", true) != false && total.isNotBlank() -> total
      show?.optBoolean("count", true) != false && count.isNotBlank() -> count
      else -> null
    }
  }

  // ── 播放地址 ──────────────────────────────────────────────────────────────

  /** 拉取普通视频播放地址（DASH 流）。 */
  fun getPlayUrl(bvid: String, cid: Long): PlayUrlData? {
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&qn=127&fnval=4048&fourk=1"
      )
    val body = resp.body?.string().orEmpty()
    resp.close()
    val json = JSONObject(body)
    if (json.optInt("code") != 0) {
      Log.w(
        BiliApiCommon.TAG,
        "playurl failed: code=${json.optInt("code")} bvid=$bvid cid=$cid " +
          "message=${json.optString("message")}",
      )
      return null
    }
    val data = json.optJSONObject("data") ?: return null
    return parsePlayUrlData(data)
  }

  /** 拉取番剧（PGC）播放地址。 */
  fun getBangumiPlayUrl(episodeId: Long, cid: Long): PlayUrlData? {
    if (episodeId <= 0L || cid <= 0L) return null
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/pgc/player/web/playurl?ep_id=$episodeId&cid=$cid" +
          "&qn=127&fnval=4048&fourk=1"
      )
    val body = resp.body?.string().orEmpty()
    resp.close()
    val json = JSONObject(body)
    if (json.optInt("code") != 0) {
      Log.w(
        BiliApiCommon.TAG,
        "pgc playurl failed: code=${json.optInt("code")} ep=$episodeId cid=$cid " +
          "message=${json.optString("message")}",
      )
      return null
    }
    val data = json.optJSONObject("result") ?: json.optJSONObject("data") ?: return null
    return parsePlayUrlData(data).also { parsed ->
      if (parsed == null) {
        Log.w(BiliApiCommon.TAG, "pgc playurl returned no DASH streams: ep=$episodeId cid=$cid")
      }
    }
  }

  internal fun bangumiEpisodeId(videoUrl: String): Long? =
    Regex("(?:/|^|\\b)ep(\\d+)(?:[/?#]|$)", RegexOption.IGNORE_CASE)
      .find(videoUrl)
      ?.groupValues
      ?.getOrNull(1)
      ?.toLongOrNull()

  internal fun parsePlayUrlData(data: JSONObject): PlayUrlData? {
    val dash = data.optJSONObject("dash")
    val streams = mutableListOf<VideoStream>()
    var dashAudio: AudioStream? = null
    if (dash != null) {
      val videos = dash.optJSONArray("video")
      if (videos != null) {
        val candidates = mutableListOf<VideoStream>()
        for (i in 0 until videos.length()) {
          val v = videos.getJSONObject(i)
          val qLabel =
            when (val q = v.optInt("id")) {
              16 -> "360P"
              32 -> "480P"
              64 -> "720P"
              74 -> "720P60"
              80 -> "1080P"
              112 -> "1080P+"
              116 -> "1080P60"
              120 -> "4K"
              125 -> "HDR"
              126 -> "杜比视界"
              127 -> "8K"
              else -> continue // 跳过未知编码
            }
          val url = v.optString("baseUrl").ifBlank { v.optString("base_url") }
          if (url.isNotBlank()) {
            candidates +=
              VideoStream(
                id = v.optInt("id"),
                quality = qLabel,
                url = url,
                codecId = v.optInt("codecid"),
                codecs = v.optString("codecs"),
                width = v.optInt("width"),
                height = v.optInt("height"),
                frameRate =
                  parseFrameRate(v.optString("frameRate").ifBlank { v.optString("frame_rate") }),
                bandwidth = v.optLong("bandwidth"),
                mimeType =
                  v.optString("mimeType").ifBlank {
                    v.optString("mime_type").ifBlank { "video/mp4" }
                  },
                initializationRange = dashInitializationRange(v),
                indexRange = dashIndexRange(v),
                backupUrls = dashBackupUrls(v, url),
              )
          }
        }
        // 接口对每个清晰度返回多种编码：每个清晰度只保留一条流，兼容性优先
        // AVC，其次 HEVC，再次 AV1，并按清晰度从高到低排序。
        streams += selectPreferredStreams(candidates)
      }
      dashAudio = bestDashAudio(dash.opt("audio"))
    }
    if (streams.isEmpty()) return null
    val dolbyAudio = bestDashAudio(dash?.optJSONObject("dolby")?.opt("audio"))
    val hiResAudio = bestDashAudio(dash?.optJSONObject("flac")?.opt("audio"))
    return PlayUrlData(
      dashAudioUrl = dashAudio?.url,
      dolbyAudioUrl = dolbyAudio?.url,
      hiResAudioUrl = hiResAudio?.url,
      dashAudio = dashAudio,
      dolbyAudio = dolbyAudio,
      hiResAudio = hiResAudio,
      streams = streams,
      currentStreamIndex = defaultStreamIndex(streams),
    )
  }

  internal fun bestDashAudio(value: Any?): AudioStream? {
    val candidates =
      when (value) {
        is JSONObject -> listOf(value)
        is JSONArray ->
          buildList {
            for (index in 0 until value.length()) {
              value.optJSONObject(index)?.let(::add)
            }
          }
        else -> emptyList()
      }
    return candidates
      .filter {
        it.optString("baseUrl").isNotBlank() || it.optString("base_url").isNotBlank()
      }
      .maxByOrNull { it.optLong("bandwidth") }
      ?.let { audio ->
        AudioStream(
          id = audio.optInt("id"),
          url = audio.optString("baseUrl").ifBlank { audio.optString("base_url") },
          bandwidth = audio.optLong("bandwidth"),
          mimeType =
            audio.optString("mimeType").ifBlank {
              audio.optString("mime_type").ifBlank { "audio/mp4" }
            },
          codecs = audio.optString("codecs"),
          initializationRange = dashInitializationRange(audio),
          indexRange = dashIndexRange(audio),
          backupUrls =
            dashBackupUrls(
              audio,
              audio.optString("baseUrl").ifBlank { audio.optString("base_url") },
            ),
        )
      }
  }

  internal fun bestDashAudioUrl(value: Any?): String? = bestDashAudio(value)?.url

  private fun dashBackupUrls(value: JSONObject, primaryUrl: String): List<String> {
    val candidates = value.optJSONArray("backupUrl") ?: value.optJSONArray("backup_url")
    if (candidates == null) return emptyList()
    return buildList {
        for (index in 0 until candidates.length()) {
          candidates.optString(index).takeIf { it.isNotBlank() && it != primaryUrl }?.let(::add)
        }
      }
      .distinct()
  }

  private fun dashInitializationRange(value: JSONObject): String {
    val segmentBase =
      value.optJSONObject("SegmentBase") ?: value.optJSONObject("segment_base") ?: return ""
    return segmentBase.optString("Initialization").ifBlank {
      segmentBase.optString("initialization")
    }
  }

  private fun dashIndexRange(value: JSONObject): String {
    val segmentBase =
      value.optJSONObject("SegmentBase") ?: value.optJSONObject("segment_base") ?: return ""
    return segmentBase.optString("indexRange").ifBlank { segmentBase.optString("index_range") }
  }

  internal fun codecPreference(stream: VideoStream): Int =
    when (stream.codecId) {
      7 -> 0 // AVC/H.264：硬件兼容性最广。
      12 -> 1 // HEVC/H.265。
      13 -> 2 // AV1。
      else -> 3
    }

  internal fun selectPreferredStreams(candidates: List<VideoStream>): List<VideoStream> =
    candidates
      .groupBy { it.id }
      .toSortedMap(compareByDescending<Int> { it })
      .values
      .map { variants -> variants.minBy(::codecPreference) }

  internal fun defaultStreamIndex(streams: List<VideoStream>): Int {
    if (streams.isEmpty()) return 0
    // HDR 与杜比视界需要匹配的显示/解码链路：默认回退到最高普通分辨率，
    // 特殊档位仍可在清晰度菜单中手动选择。
    return streams.indices
      .filter { streams[it].id !in setOf(125, 126) }
      .maxByOrNull { streams[it].id } ?: 0
  }

  internal fun parseFrameRate(value: String): Float {
    if (value.isBlank()) return 0f
    value.toFloatOrNull()?.let {
      return it
    }
    val parts = value.split('/')
    if (parts.size != 2) return 0f
    val numerator = parts[0].toFloatOrNull() ?: return 0f
    val denominator = parts[1].toFloatOrNull()?.takeIf { it != 0f } ?: return 0f
    return numerator / denominator
  }

  /** 读取点赞/投币/收藏等互动数据。 */
  fun getVideoEngagement(aid: Long): VideoEngagement {
    require(aid > 0) { "视频参数无效" }
    val json =
      BiliHttpClient.get("https://api.bilibili.com/x/web-interface/archive/relation?aid=$aid")
        .use { response -> JSONObject(response.body?.string().orEmpty()) }
    if (json.optInt("code") != 0) {
      throw IllegalStateException(json.optString("message", "互动状态获取失败"))
    }
    return parseVideoEngagement(json.getJSONObject("data"))
  }

  fun setVideoLike(aid: Long, liked: Boolean) {
    require(aid > 0) { "视频参数无效" }
    postVideoAction(
      "https://api.bilibili.com/x/web-interface/archive/like",
      mapOf("aid" to aid.toString(), "like" to if (liked) "1" else "2"),
      "点赞失败",
    )
  }

  fun coinVideo(aid: Long, multiply: Int = 1, selectLike: Boolean = false) {
    require(aid > 0 && multiply in 1..2) { "投币参数无效" }
    postVideoAction(
      "https://api.bilibili.com/x/web-interface/coin/add",
      mapOf(
        "aid" to aid.toString(),
        "multiply" to multiply.toString(),
        "select_like" to if (selectLike) "1" else "0",
      ),
      "投币失败",
    )
  }

  private fun postVideoAction(url: String, fields: Map<String, String>, fallback: String) {
    val csrf = BiliApiCommon.requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        url,
        fields + mapOf("csrf" to csrf, "csrf_token" to csrf),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", fallback))
  }

  // ── 相关推荐 ───────────────────────────────────────────────────────────────

  /** 拉取视频相关推荐。 */
  fun getRelated(bvid: String): List<FeedCard> {
    val resp =
      BiliHttpClient.get("https://api.bilibili.com/x/web-interface/archive/related?bvid=$bvid")
    val body = resp.body?.string().orEmpty()
    resp.close()
    val json = JSONObject(body)
    if (json.optInt("code") != 0) return emptyList()
    val list = json.optJSONArray("data") ?: return emptyList()
    val cards = mutableListOf<FeedCard>()
    for (i in 0 until list.length()) {
      try {
        cards.add(FeedCard.fromJson(list.getJSONObject(i)))
      } catch (_: Exception) {}
    }
    return cards
  }

  private data class CachedVideoInfo(val info: VideoInfo, val loadedAtMs: Long)
}

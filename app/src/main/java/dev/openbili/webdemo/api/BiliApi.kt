package dev.openbili.webdemo.api

import android.os.SystemClock
import android.text.Html
import android.util.Log
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Bilibili public API methods. All HTTP calls go through [BiliHttpClient].
 *
 * Feed uses the public "popular" endpoint which doesn't require WBI signing or login. Login and
 * video-stream endpoints require the correct Referer + buvid3 from the shared client.
 */
object BiliApi {
  private const val TAG = "BiliApi"
  private const val DANMAKU_SEGMENT_SECONDS = 6 * 60L
  private const val MAX_DANMAKU_SEGMENTS = 120
  private const val DEFAULT_HISTORY_LOOKBACK_MONTHS = 6L
  private const val MAX_HISTORY_MONTH_REQUESTS = 120
  private const val MAX_HISTORY_DAY_REQUESTS = 512
  private const val MAX_CONSECUTIVE_EMPTY_HISTORY_RESPONSES = 5
  private const val HISTORY_REQUEST_INTERVAL_MS = 50L
  private const val MAX_DANMAKU_MAP_CAPACITY = 100_000
  private const val MAX_DANMAKU_MASK_BYTES = 32 * 1024 * 1024L
  private val HTML_ENTITY_REGEX = Regex("&(?:#(\\d+)|#x([0-9a-fA-F]+)|([A-Za-z]+));")
  private val ARTICLE_BVID_REGEX = Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE)
  private val ARTICLE_OPUS_ID_REGEX = Regex("/opus/(\\d+)")
  @Volatile private var wbiKeys: WbiKeys? = null
  private val danmakuLock = Any()
  private val danmakuCache = LinkedHashMap<DanmakuCacheKey, List<DanmakuItem>>(16, .75f, true)
  private const val VIDEO_INFO_CACHE_TTL_MS = 2 * 60 * 1000L
  private const val VIDEO_INFO_CACHE_LIMIT = 64
  private val videoInfoCache = ConcurrentHashMap<String, CachedVideoInfo>()
  private val videoInfoRequests = ConcurrentHashMap<String, CompletableFuture<VideoInfo?>>()
  private val accountMessageUserStyleCache = ConcurrentHashMap<Long, AccountMessageUserStyle>()
  private val bangumiVideoBvidCache = ConcurrentHashMap<String, String>()

  data class PrivateImageUpload(
    val url: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val sizeKb: Int,
  )

  // ── Feed (popular — no auth needed) ──────────────────────────────────────

  fun getPopularFeed(page: Int): FeedResponse {
    Log.d(TAG, "popular feed request: page=$page")
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/popular?pn=$page&ps=20")
    val body = resp.body?.string().orEmpty()
    resp.close()
    Log.d(TAG, "popular feed response: page=$page code=... bodyLen=${body.length}")
    val json = JSONObject(body)
    if (json.optInt("code") != 0) {
      Log.w(TAG, "popular error: ${json.optString("message")}")
      return FeedResponse(emptyList())
    }
    val list = json.getJSONObject("data").optJSONArray("list")
    val cards = mutableListOf<FeedCard>()
    if (list != null) {
      for (i in 0 until list.length()) {
        try {
          cards.add(FeedCard.fromJson(list.getJSONObject(i)))
        } catch (e: Exception) {
          Log.w(TAG, "skip card: ${e.message}")
        }
      }
    }
    return FeedResponse(cards)
  }

  /**
   * Account-aware homepage recommendations. A changing fresh index tells Bilibili this is a new
   * brush instead of replaying the first cached batch.
   */
  fun getPersonalizedFeed(freshIndex: Long): FeedResponse {
    val params =
      linkedMapOf(
        "fresh_idx" to freshIndex.toString(),
        "fresh_idx_1h" to freshIndex.toString(),
        "fresh_type" to "3",
        "homepage_ver" to "1",
        "ps" to "20",
        "last_y_num" to "5",
        "feed_version" to "V8",
        "brush" to "1",
        "web_location" to "1430650",
      )
    val signed = signedParams(params)
    val url =
      "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd?" +
        signed.toSortedMap().entries.joinToString("&") { (key, value) ->
          "${URLEncoder.encode(key, "UTF-8")}" + "=${URLEncoder.encode(value, "UTF-8")}"
        }
    val resp = BiliHttpClient.get(url)
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) {
      throw IllegalStateException("推荐接口：${json.optString("message")}")
    }
    val array = json.optJSONObject("data")?.optJSONArray("item")
    val cards = mutableListOf<FeedCard>()
    if (array != null) {
      for (i in 0 until array.length()) {
        runCatching { FeedCard.fromJson(array.getJSONObject(i)) }
          .onSuccess { if (it.bvid.isNotBlank()) cards += it }
      }
    }
    Log.d(TAG, "personalized feed: fresh=$freshIndex cards=${cards.size}")
    return FeedResponse(cards)
  }

  private fun signedParams(params: Map<String, String>): Map<String, String> {
    val now = System.currentTimeMillis()
    var keys = wbiKeys?.takeIf { it.isValid }
    if (keys == null) {
      val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/nav")
      val json = JSONObject(resp.body?.string().orEmpty())
      resp.close()
      val wbi =
        json.optJSONObject("data")?.optJSONObject("wbi_img")
          ?: throw IllegalStateException("无法取得 WBI 密钥")
      fun fileKey(url: String) = url.substringAfterLast('/').substringBefore('.')
      keys = WbiKeys(fileKey(wbi.getString("img_url")), fileKey(wbi.getString("sub_url")), now)
      wbiKeys = keys
    }
    val riskAwareParams =
      BiliHttpClient.gaiaToken?.takeIf(String::isNotBlank)?.let { params + ("gaia_vtoken" to it) }
        ?: params
    return WbiSigner.sign(riskAwareParams, WbiSigner.getMixinKey(keys.imgKey, keys.subKey))
  }

  // ── Video info ────────────────────────────────────────────────────────────

  fun getVideoInfo(bvid: String): VideoInfo? {
    val key = bvid.trim()
    if (key.isBlank()) return null
    val now = SystemClock.elapsedRealtime()
    videoInfoCache[key]
      ?.takeIf { now - it.loadedAtMs < VIDEO_INFO_CACHE_TTL_MS }
      ?.let {
        return it.info
      }

    val request = CompletableFuture<VideoInfo?>()
    val active = videoInfoRequests.putIfAbsent(key, request)
    if (active != null) {
      return runCatching { active.get() }.getOrElse { throw (it.cause ?: it) }
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
            p.getInt("page"),
            p.getLong("cid"),
            p.optString("part", ""),
            p.optLong("duration", 0),
          )
        )
      }
    }
    val collection =
      data.optJSONObject("ugc_season")?.let { season ->
        val episodes = buildList {
          val sections = season.optJSONArray("sections")
          if (sections != null) {
            for (sectionIndex in 0 until sections.length()) {
              val sectionEpisodes =
                sections.optJSONObject(sectionIndex)?.optJSONArray("episodes") ?: continue
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
                    bvid = episodeBvid,
                    cid = episodePage.optLong("cid", episode.optLong("cid")),
                    title = episode.optString("title", arc.optString("title")),
                    coverUrl =
                      dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(arc.optString("pic"))
                        .orEmpty(),
                    durationSeconds = episodePage.optLong("duration", arc.optLong("duration")),
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
          }
        }
        VideoCollection(
            id = season.optLong("id"),
            title = season.optString("title").ifBlank { "视频合集" },
            episodes = episodes.distinctBy { it.bvid },
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
    )
  }

  private fun trimVideoInfoCache(now: Long) {
    videoInfoCache.entries.removeIf { now - it.value.loadedAtMs >= VIDEO_INFO_CACHE_TTL_MS }
    while (videoInfoCache.size > VIDEO_INFO_CACHE_LIMIT) {
      val oldest = videoInfoCache.entries.minByOrNull { it.value.loadedAtMs } ?: break
      videoInfoCache.remove(oldest.key, oldest.value)
    }
  }

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

  // ── Play URL ──────────────────────────────────────────────────────────────

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
        TAG,
        "playurl failed: code=${json.optInt("code")} bvid=$bvid cid=$cid " +
          "message=${json.optString("message")}",
      )
      return null
    }
    val data = json.optJSONObject("data") ?: return null
    return parsePlayUrlData(data)
  }

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
        TAG,
        "pgc playurl failed: code=${json.optInt("code")} ep=$episodeId cid=$cid " +
          "message=${json.optString("message")}",
      )
      return null
    }
    val data = json.optJSONObject("result") ?: json.optJSONObject("data") ?: return null
    return parsePlayUrlData(data).also { parsed ->
      if (parsed == null) {
        Log.w(TAG, "pgc playurl returned no DASH streams: ep=$episodeId cid=$cid")
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
              else -> continue // skip unknown codecs
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
        // The API returns multiple codecs for each quality. Keep one stream per quality,
        // preferring AVC for compatibility, then HEVC, then AV1. Sort highest quality first.
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
          backupUrls = dashBackupUrls(
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
        candidates.optString(index)
          .takeIf { it.isNotBlank() && it != primaryUrl }
          ?.let(::add)
      }
    }.distinct()
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
      7 -> 0 // AVC/H.264: widest hardware compatibility.
      12 -> 1 // HEVC/H.265.
      13 -> 2 // AV1.
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
    // HDR and Dolby Vision require a matching display/decoder path. Default to the highest normal
    // resolution instead; the special tiers remain selectable from the quality menu.
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

  // ── Danmaku ───────────────────────────────────────────────────────────────

  internal fun getDanmakuMaskResource(
    aid: Long,
    cid: Long,
    bvid: String,
  ): DanmakuMaskResource? {
    if (aid <= 0L || cid <= 0L) return null
    val query = buildString {
      append("aid=").append(aid).append("&cid=").append(cid)
      if (bvid.startsWith("BV")) append("&bvid=").append(bvid)
    }
    val response = BiliHttpClient.get("https://api.bilibili.com/x/player/v2?$query")
    val body = response.body?.string().orEmpty()
    val httpCode = response.code
    response.close()
    if (httpCode !in 200..299) return null
    val json = JSONObject(body)
    if (json.optInt("code") != 0) return null
    val mask = json.optJSONObject("data")?.optJSONObject("dm_mask") ?: return null
    val rawUrl = mask.optString("mask_url")
    if (rawUrl.isBlank()) return null
    val maskUrl =
      when {
        rawUrl.startsWith("//") -> "https:$rawUrl"
        rawUrl.startsWith("https://") -> rawUrl
        rawUrl.startsWith("http://") -> rawUrl.replaceFirst("http://", "https://")
        else -> return null
      }
    val maskResponse =
      BiliHttpClient.getPublic(
        maskUrl,
        headers =
          mapOf(
            "Accept-Encoding" to "identity",
            "Referer" to "https://www.bilibili.com/",
          ),
      )
    val contentLength = maskResponse.body?.contentLength() ?: -1L
    if (maskResponse.code !in 200..299 || contentLength > MAX_DANMAKU_MASK_BYTES) {
      maskResponse.close()
      return null
    }
    val bytes = maskResponse.body?.bytes() ?: ByteArray(0)
    maskResponse.close()
    if (bytes.isEmpty() || bytes.size > MAX_DANMAKU_MASK_BYTES) return null
    Log.d(TAG, "danmaku mask loaded: cid=$cid fps=${mask.optInt("fps")} bytes=${bytes.size}")
    return DanmakuMaskResource(
      fps = mask.optInt("fps").coerceAtLeast(0),
      bytes = bytes,
    )
  }

  fun getDanmaku(
    cid: Long,
    durationSeconds: Long = 0L,
    publishedAt: Long = 0L,
    expectedCount: Long = 0L,
    includeHistory: Boolean = false,
  ): List<DanmakuItem> =
    synchronized(danmakuLock) {
      val cacheKey = DanmakuCacheKey(cid, includeHistory)
      danmakuCache[cacheKey]?.let {
        Log.d(TAG, "danmaku cache hit: cid=$cid history=$includeHistory items=${it.size}")
        return@synchronized it
      }
      val currentKey = DanmakuCacheKey(cid, false)
      val current =
        danmakuCache[currentKey]
          ?: fetchCurrentDanmaku(cid, durationSeconds).also { danmakuCache[currentKey] = it }
      val loaded =
        if (includeHistory) {
          fetchHistoricalDanmaku(
            cid = cid,
            publishedAt = publishedAt,
            expectedCount = expectedCount,
            current = current,
          )
        } else {
          current
        }
      danmakuCache[cacheKey] = loaded
      while (danmakuCache.size > 16) danmakuCache.remove(danmakuCache.keys.first())
      Log.d(TAG, "danmaku loaded: cid=$cid history=$includeHistory items=${loaded.size}")
      loaded
    }

  private fun fetchCurrentDanmaku(cid: Long, durationSeconds: Long): List<DanmakuItem> {
    val segmented = fetchCurrentDanmakuSegments(cid, durationSeconds)
    val primary = fetchDanmakuXml("https://api.bilibili.com/x/v1/dm/list.so?oid=$cid", cid)
    val legacy =
      if (primary.httpCode in 200..299) {
        primary.items
      } else {
        fetchDanmakuXml("https://comment.bilibili.com/$cid.xml", cid).items
      }
    val merged = mergeDanmaku(segmented, legacy)
    Log.d(
      TAG,
      "danmaku current sources: cid=$cid segmented=${segmented.size} " +
        "legacy=${legacy.size} merged=${merged.size}",
    )
    return merged
  }

  private fun fetchCurrentDanmakuSegments(
    cid: Long,
    durationSeconds: Long,
  ): List<DanmakuItem> {
    val segmentCount =
      if (durationSeconds > 0L) {
        ((durationSeconds + DANMAKU_SEGMENT_SECONDS - 1L) / DANMAKU_SEGMENT_SECONDS)
          .toInt()
          .coerceIn(1, MAX_DANMAKU_SEGMENTS)
      } else {
        1
      }
    val items = mutableListOf<DanmakuItem>()
    for (segmentIndex in 1..segmentCount) {
      val url =
        "https://api.bilibili.com/x/v2/dm/web/seg.so" +
          "?type=1&oid=$cid&segment_index=$segmentIndex"
      val result = fetchDanmakuProtobuf(url, cid, "segment=$segmentIndex")
      if (result.httpCode !in 200..299) {
        if (segmentIndex == 1) {
          Log.w(TAG, "danmaku segmented request failed: cid=$cid http=${result.httpCode}")
        }
        if (durationSeconds <= 0L) break
        continue
      }
      items += result.items
    }
    return items
  }

  private fun fetchHistoricalDanmaku(
    cid: Long,
    publishedAt: Long,
    expectedCount: Long,
    current: List<DanmakuItem>,
  ): List<DanmakuItem> {
    if (BiliHttpClient.cookieValue("SESSDATA").isNullOrBlank()) {
      Log.d(TAG, "skip full danmaku history without SESSDATA: cid=$cid")
      return current
    }
    if (expectedCount > 0L && current.size.toLong() >= expectedCount) return current

    val zone = ZoneId.of("Asia/Shanghai")
    val today = LocalDate.now(zone)
    val publishedDate =
      publishedAt.takeIf { it > 0L }?.let { Instant.ofEpochSecond(it).atZone(zone).toLocalDate() }
        ?: today.minusMonths(DEFAULT_HISTORY_LOOKBACK_MONTHS)
    val firstMonth = YearMonth.from(publishedDate)
    val lastMonth = YearMonth.from(today)
    val months = mutableListOf<YearMonth>()
    var month = lastMonth
    while (month >= firstMonth && months.size < MAX_HISTORY_MONTH_REQUESTS) {
      months += month
      month = month.minusMonths(1)
    }
    val dates =
      months
        .flatMap { fetchDanmakuHistoryDates(cid, it) }
        .asSequence()
        .filter { it in publishedDate..today }
        .distinct()
        .sortedDescending()
        .take(MAX_HISTORY_DAY_REQUESTS)
        .toList()

    val merged = LinkedHashMap<DanmakuIdentity, DanmakuItem>(expectedCount.toSafeMapCapacity())
    current.forEach { merged.putIfAbsent(it.identity(), it) }
    var emptyResponses = 0
    var fetchedDays = 0
    for (date in dates) {
      if (expectedCount > 0L && merged.size.toLong() >= expectedCount) break
      val url =
        "https://api.bilibili.com/x/v2/dm/web/history/seg.so" + "?type=1&oid=$cid&date=$date"
      val result = fetchDanmakuProtobuf(url, cid, "history=$date")
      if (result.httpCode !in 200..299 || result.items.isEmpty()) {
        emptyResponses += 1
        if (emptyResponses >= MAX_CONSECUTIVE_EMPTY_HISTORY_RESPONSES) {
          Log.w(TAG, "stop danmaku history after repeated empty responses: cid=$cid")
          break
        }
        continue
      }
      emptyResponses = 0
      fetchedDays += 1
      result.items.forEach { merged.putIfAbsent(it.identity(), it) }
      if (expectedCount <= 0L || merged.size.toLong() < expectedCount) {
        SystemClock.sleep(HISTORY_REQUEST_INTERVAL_MS)
      }
    }
    val loaded = merged.values.sortedBy(DanmakuItem::timeMs)
    Log.d(
      TAG,
      "danmaku history merged: cid=$cid dates=${dates.size} fetched=$fetchedDays " +
        "current=${current.size} expected=$expectedCount merged=${loaded.size}",
    )
    return loaded
  }

  private fun fetchDanmakuHistoryDates(cid: Long, month: YearMonth): List<LocalDate> =
    runCatching {
        val url = "https://api.bilibili.com/x/v2/dm/history/index" + "?type=1&oid=$cid&month=$month"
        val response = BiliHttpClient.get(url)
        val responseCode = response.code
        val body = response.body?.string().orEmpty()
        response.close()
        if (responseCode !in 200..299) {
          Log.w(TAG, "danmaku history index failed: cid=$cid month=$month http=$responseCode")
          return@runCatching emptyList()
        }
        val json = JSONObject(body)
        if (json.optInt("code") != 0) {
          Log.w(
            TAG,
            "danmaku history index rejected: cid=$cid month=$month code=${json.optInt("code")}",
          )
          return@runCatching emptyList()
        }
        val data = json.optJSONArray("data") ?: return@runCatching emptyList()
        buildList {
          for (index in 0 until data.length()) {
            runCatching { LocalDate.parse(data.getString(index)) }.getOrNull()?.let(::add)
          }
        }
      }
      .getOrElse {
        Log.w(TAG, "danmaku history index error: cid=$cid month=$month", it)
        emptyList()
      }

  private fun fetchDanmakuProtobuf(
    url: String,
    cid: Long,
    source: String,
  ): DanmakuBinaryFetchResult {
    val response = BiliHttpClient.get(url, headers = mapOf("Accept-Encoding" to "identity"))
    val responseType = response.header("Content-Type").orEmpty()
    val responseCode = response.code
    val responseBytes = response.body?.bytes() ?: ByteArray(0)
    response.close()
    val items =
      if (responseCode in 200..299 && responseBytes.isNotEmpty()) {
        runCatching { DanmakuProtoParser.parseSegment(responseBytes) }
          .getOrElse {
            Log.w(
              TAG,
              "danmaku protobuf decode failed: cid=$cid source=$source " +
                "http=$responseCode type=$responseType bytes=${responseBytes.size}",
              it,
            )
            emptyList()
          }
      } else {
        emptyList()
      }
    Log.d(
      TAG,
      "danmaku protobuf response: cid=$cid source=$source http=$responseCode " +
        "type=$responseType bytes=${responseBytes.size} items=${items.size}",
    )
    return DanmakuBinaryFetchResult(responseCode, items)
  }

  private fun fetchDanmakuXml(url: String, cid: Long): DanmakuFetchResult {
    val resp = BiliHttpClient.get(url, headers = mapOf("Accept-Encoding" to "identity"))
    val responseType = resp.header("Content-Type").orEmpty()
    val responseEncoding = resp.header("Content-Encoding").orEmpty()
    val responseCode = resp.code
    val responseBytes = resp.body?.bytes() ?: ByteArray(0)
    resp.close()
    val body =
      runCatching {
          if (responseEncoding.equals("deflate", ignoreCase = true)) inflate(responseBytes)
          else responseBytes.toString(Charsets.UTF_8)
        }
        .getOrElse {
          Log.w(TAG, "danmaku decode failed: cid=$cid http=$responseCode", it)
          ""
        }
    val items = parseDanmakuXml(body)
    Log.d(
      TAG,
      "danmaku response: cid=$cid http=$responseCode type=$responseType " +
        "encoding=$responseEncoding bodyLen=${body.length} items=${items.size}",
    )
    return DanmakuFetchResult(responseCode, items)
  }

  internal fun parseDanmakuXml(body: String): List<DanmakuItem> {
    val items = mutableListOf<DanmakuItem>()
    // The legacy endpoint occasionally contains a bare ampersand in one message, making the
    // whole document invalid XML. Parse independent <d> records so one malformed entity cannot
    // discard every danmaku in the response.
    val records = Regex("""<d\s+p="([^"]+)">(.*?)</d>""", setOf(RegexOption.DOT_MATCHES_ALL))
    for (match in records.findAll(body)) {
      runCatching {
          val parts = match.groupValues[1].split(",")
          if (parts.size < 5) return@runCatching
          items +=
            DanmakuItem(
              timeMs = (parts[0].toDouble() * 1000).toLong(),
              type = parts[1].toIntOrNull() ?: 1,
              fontSize = parts[2].toIntOrNull() ?: 25,
              color = parts[3].toIntOrNull() ?: 0xFFFFFF,
              content = Html.fromHtml(match.groupValues[2], Html.FROM_HTML_MODE_LEGACY).toString(),
              sourceId = parts.getOrNull(7)?.takeIf(String::isNotBlank),
            )
        }
        .onFailure { Log.w(TAG, "skip malformed danmaku record", it) }
    }
    return items
  }

  private data class DanmakuFetchResult(val httpCode: Int, val items: List<DanmakuItem>)

  private data class DanmakuBinaryFetchResult(
    val httpCode: Int,
    val items: List<DanmakuItem>,
  )

  private data class DanmakuCacheKey(val cid: Long, val includeHistory: Boolean)

  private data class DanmakuIdentity(
    val sourceId: String?,
    val timeMs: Long,
    val type: Int,
    val fontSize: Int,
    val color: Int,
    val colorful: Int,
    val content: String,
  )

  private fun DanmakuItem.identity(): DanmakuIdentity {
    val stableId = sourceId?.takeIf(String::isNotBlank)
    return DanmakuIdentity(
      sourceId = stableId,
      timeMs = if (stableId == null) timeMs else 0L,
      type = if (stableId == null) type else 0,
      fontSize = if (stableId == null) fontSize else 0,
      color = if (stableId == null) color else 0,
      colorful = if (stableId == null) colorful else DANMAKU_COLORFUL_NONE,
      content = if (stableId == null) content else "",
    )
  }

  private fun mergeDanmaku(vararg sources: List<DanmakuItem>): List<DanmakuItem> {
    val merged = LinkedHashMap<DanmakuIdentity, DanmakuItem>()
    sources.forEach { source ->
      source.forEach { item -> merged.putIfAbsent(item.identity(), item) }
    }
    return merged.values.sortedBy(DanmakuItem::timeMs)
  }

  private fun Long.toSafeMapCapacity(): Int =
    takeIf { it in 1..MAX_DANMAKU_MAP_CAPACITY.toLong() }?.toInt() ?: 16

  private fun inflate(bytes: ByteArray): String {
    fun decode(inflater: Inflater) =
      InflaterInputStream(ByteArrayInputStream(bytes), inflater)
        .use { it.readBytes() }
        .toString(Charsets.UTF_8)
    return runCatching { decode(Inflater()) }.getOrElse { decode(Inflater(true)) }
  }

  // ── Comments ──────────────────────────────────────────────────────────────

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
    val replies = data.optJSONArray("replies") ?: return CommentResponse(emptyList(), false, total)
    val items = buildList {
      for (index in 0 until replies.length()) add(parseComment(replies.getJSONObject(index)))
    }
    if (pageInfo == null) return CommentResponse(items, false, total)
    val current = pageInfo.optInt("num", page)
    val pageSize = pageInfo.optInt("size", 20)
    val hasMore = current * pageSize < total
    return CommentResponse(items, hasMore, total)
  }

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

  fun addComment(
    oid: Long,
    message: String,
    type: Int = 1,
    image: PrivateImageUpload? = null,
  ): CommentItem {
    val csrf = requireCsrf()
    val fields =
      linkedMapOf(
        "type" to type.toString(),
        "oid" to oid.toString(),
        "message" to message,
        "csrf" to csrf,
        "csrf_token" to csrf,
      )
    image?.let { fields["pictures"] = commentPicturesPayload(it) }
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v2/reply/add",
        fields,
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "评论失败"))
    return parseComment(json.getJSONObject("data").getJSONObject("reply"))
  }

  fun sendDanmakuAuthenticated(
    cid: Long,
    aid: Long,
    bvid: String,
    message: String,
    progressMs: Long,
    color: Int,
    mode: Int,
    fontSize: Int,
    colorful: Int = DANMAKU_COLORFUL_NONE,
  ) {
    require(cid > 0 && aid > 0 && message.isNotBlank()) { "弹幕参数无效" }
    val csrf = requireCsrf()
    val fields = buildMap {
      put("type", "1")
      put("oid", cid.toString())
      put("aid", aid.toString())
      if (bvid.startsWith("BV")) put("bvid", bvid)
      put("msg", message)
      put("progress", progressMs.coerceAtLeast(0L).toString())
      put("color", (color and 0xFFFFFF).toString())
      put("fontsize", fontSize.coerceIn(18, 25).toString())
      if (colorful == DANMAKU_COLORFUL_VIP_GRADIENT) {
        put("colorful", DANMAKU_COLORFUL_VIP_GRADIENT.toString())
      }
      put("pool", "0")
      put("mode", mode.toString())
      put("plat", "1")
      put("rnd", (System.currentTimeMillis() / 1000L).toString())
      put("csrf", csrf)
      put("csrf_token", csrf)
    }
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v2/dm/post",
        fields,
      )
    val responseBody = resp.body?.string().orEmpty()
    val httpCode = resp.code
    val successful = resp.isSuccessful
    resp.close()
    val json = runCatching { JSONObject(responseBody) }.getOrElse { JSONObject() }
    val apiCode = json.optInt("code", Int.MIN_VALUE)
    if (!successful || apiCode != 0) {
      val messageText = json.optString("message", "弹幕发送失败")
      throw IllegalStateException("$messageText（HTTP $httpCode / $apiCode）")
    }
  }

  fun addReply(
    oid: Long,
    root: Long,
    parent: Long,
    message: String,
    type: Int = 1,
    image: PrivateImageUpload? = null,
  ): CommentItem {
    val csrf = requireCsrf()
    val fields =
      linkedMapOf(
        "type" to type.toString(),
        "oid" to oid.toString(),
        "root" to root.toString(),
        "parent" to parent.toString(),
        "message" to message,
        "csrf" to csrf,
        "csrf_token" to csrf,
      )
    image?.let { fields["pictures"] = commentPicturesPayload(it) }
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v2/reply/add",
        fields,
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "回复失败"))
    return parseComment(json.getJSONObject("data").getJSONObject("reply"))
  }

  fun setCommentLike(oid: Long, rpid: Long, liked: Boolean, type: Int = 1) {
    val csrf = requireCsrf()
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

  fun deleteComment(oid: Long, rpid: Long, type: Int = 1) {
    require(oid > 0L && rpid > 0L) { "评论参数无效" }
    val csrf = requireCsrf()
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

  fun getVideoEngagement(aid: Long): VideoEngagement {
    require(aid > 0) { "视频参数无效" }
    fun response(url: String): JSONObject? {
      val resp = BiliHttpClient.get(url)
      val json = JSONObject(resp.body?.string().orEmpty())
      resp.close()
      if (json.optInt("code") != 0) return null
      return json
    }
    val liked =
      response("https://api.bilibili.com/x/web-interface/archive/has/like?aid=$aid")
        ?.optInt("data", 0) == 1
    val coins =
      response("https://api.bilibili.com/x/web-interface/archive/coins?aid=$aid")
        ?.optJSONObject("data")
        ?.optInt("multiply", 0) ?: 0
    val favorited =
      response("https://api.bilibili.com/x/v2/fav/video/favoured?aid=$aid")
        ?.optJSONObject("data")
        ?.optBoolean("favoured", false) == true
    return VideoEngagement(liked = liked, coins = coins, favorited = favorited)
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

  fun removeFavoriteResource(resourceId: Long, resourceType: Int, folderId: Long) {
    require(resourceId > 0 && resourceType > 0 && folderId > 0) { "收藏参数无效" }
    Log.d(TAG, "favorite remove request: resource=$resourceId:$resourceType folder=$folderId")
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
      TAG,
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
    val csrf = requireCsrf()
    val response = BiliHttpClient.postForm(url, fields + ("csrf" to csrf))
    val statusCode = response.code
    val contentType = response.header("Content-Type").orEmpty()
    val body = response.body?.string().orEmpty()
    response.close()
    if (!body.trimStart().startsWith("{")) {
      Log.w(
        TAG,
        "$action returned non-JSON: status=$statusCode type=$contentType len=${body.length}",
      )
      throw IllegalStateException("收藏服务暂时不可用，请稍后重试")
    }
    val json = JSONObject(body)
    val code = json.optInt("code")
    Log.d(TAG, "$action: http=$statusCode code=$code")
    if (code != 0) throw IllegalStateException(json.optString("message", "收藏失败"))
  }

  private fun postVideoAction(url: String, fields: Map<String, String>, fallback: String) {
    val csrf = requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        url,
        fields + mapOf("csrf" to csrf, "csrf_token" to csrf),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", fallback))
  }

  private fun requireCsrf(): String =
    BiliHttpClient.cookieValue("bili_jct") ?: throw IllegalStateException("请先登录")

  private fun parseComment(r: JSONObject): CommentItem {
    val member = r.optJSONObject("member") ?: JSONObject()
    val content = r.optJSONObject("content") ?: JSONObject()
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
      level = member.optJSONObject("level_info")?.optInt("current_level", 0) ?: 0,
      vipActive = member.optJSONObject("vip")?.optInt("vipStatus", 0) == 1,
      vipLabel = member.optJSONObject("vip")?.optJSONObject("label")?.optString("text").orEmpty(),
    )
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

  fun getSpaceProfile(mid: Long): SpaceProfile {
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/space/wbi/acc/info?" +
          signedQuery(mapOf("mid" to mid.toString()))
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
    Log.d(TAG, "space profile: mid=$mid banner=${banner.isNotBlank()}")
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

  fun getSpaceVideos(mid: Long, page: Int): SpaceVideoResponse {
    val params =
      mapOf("mid" to mid.toString(), "pn" to page.toString(), "ps" to "20", "order" to "pubdate")
    val resp =
      BiliHttpClient.get("https://api.bilibili.com/x/space/wbi/arc/search?" + signedQuery(params))
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
        "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space?host_mid=$mid$offsetQuery"
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
        val desc = dynamicRichText.text
        val major = dynamic.optJSONObject("major")
        val archive = major?.optJSONObject("archive")
        val opus = major?.optJSONObject("opus")
        val article = major?.optJSONObject("article") ?: opus
        val author = modules.optJSONObject("module_author") ?: JSONObject()
        val stat = modules.optJSONObject("module_stat") ?: JSONObject()
        val basic = row.optJSONObject("basic") ?: JSONObject()
        val dynamicId = row.optString("id_str", "dynamic_$i")
        val originalDynamic =
          row.optJSONObject("orig")?.optJSONObject("modules")?.optJSONObject("module_dynamic")
        val originalRichText = parseDynamicRichText(originalDynamic)
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
          }
          collect(dynamic)
          collect(originalDynamic)
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
        val video = archive?.let {
          SpaceDynamicVideo(
            aid = it.optLong("aid"),
            bvid = it.optString("bvid"),
            title = it.optString("title"),
            description = it.optString("desc"),
            coverUrl =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(it.optString("cover")).orEmpty(),
            duration = it.optString("duration_text"),
            playCount = it.optJSONObject("stat")?.optString("play").orEmpty(),
            danmakuCount = it.optJSONObject("stat")?.optString("danmaku").orEmpty(),
          )
        }
        val fallbackText =
          article?.optString("title").orEmpty().ifBlank { article?.optString("desc").orEmpty() }
        val requestedCommentType = basic.optInt("comment_type", 17).takeIf { it > 0 } ?: 17
        val requestedCommentOid =
          basic.optString("comment_id_str").toLongOrNull()
            ?: basic.optLong("rid_str").takeIf { it > 0 }
            ?: dynamicId.toLongOrNull()
            ?: 0L
        val commentType = if (video?.aid ?: 0L > 0L) 1 else requestedCommentType
        val commentOid = if (video?.aid ?: 0L > 0L) video!!.aid else requestedCommentOid
        val articleItem =
          if (video == null && requestedCommentType == 12 && requestedCommentOid > 0L) {
            val articleTitle =
              article?.optString("title").orEmpty().ifBlank {
                desc.lineSequence().firstOrNull().orEmpty().take(80)
              }
            ArticleItem(
              id = requestedCommentOid,
              title = articleTitle.ifBlank { "专栏" },
              summary =
                article?.optString("desc").orEmpty().ifBlank {
                  article?.optString("summary").orEmpty().ifBlank { desc }
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
    val candidates =
      listOfNotNull(
        dynamic.optJSONObject("desc"),
        opus?.optJSONObject("summary"),
        opus?.optJSONObject("title"),
        opus,
        major?.optJSONObject("article"),
      )
    val text =
      candidates.asSequence().map(::dynamicRichTextValue).firstOrNull(String::isNotBlank).orEmpty()
    val emotes = buildMap {
      candidates.forEach { candidate ->
        putAll(parseDynamicEmotes(candidate.optJSONArray("rich_text_nodes")))
      }
    }
    return ParsedDynamicRichText(text, emotes)
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
    val csrf = requireCsrf()
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
    val csrf = requireCsrf()
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
    val csrf = requireCsrf()
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
        TAG,
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

  fun getSpaceCollections(mid: Long): List<SpaceContentCard> {
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/polymer/web-space/seasons_series_list?mid=$mid&page_num=1&page_size=30"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data = json.optJSONObject("data") ?: return emptyList()
    return buildList {
      listOf("seasons_list", "series_list").forEach { key ->
        val array = data.optJSONArray(key) ?: return@forEach
        for (i in 0 until array.length()) {
          val row = array.optJSONObject(i) ?: continue
          val meta = row.optJSONObject("meta") ?: row
          add(
            SpaceContentCard(
              id = "$key:${meta.optLong("season_id", meta.optLong("series_id", i.toLong()))}",
              title = meta.optString("name", meta.optString("title", "合集或系列")),
              subtitle = meta.optString("description"),
              coverUrl =
                dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(meta.optString("cover")).orEmpty(),
            )
          )
        }
      }
    }
  }

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
   * Builds the same compact "正在追" view used by the PGC home pages. The web client presents
   * recent PGC history first, then fills the row with followed seasons from the matching media
   * category. The profile follow-list alone cannot reproduce that ordering (or history-only rows).
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

  /** Load one server-history cursor page for the requested PGC category. */
  fun getBangumiWatchingHistoryPage(
    category: BangumiExploreCategory,
    cursor: HistoryCursor = HistoryCursor(),
  ): BangumiWatchingHistoryPage {
    val seasonType = bangumiSeasonType(category) ?: return BangumiWatchingHistoryPage(emptyList(), cursor, false)
    val response = getHistory(cursor)
    val cards =
      response.items
        .filterIsInstance<AccountHistoryItem.Bangumi>()
        .map(AccountHistoryItem.Bangumi::bangumi)
        .filter { it.seasonType == seasonType }
    return BangumiWatchingHistoryPage(cards, response.cursor, response.hasMore)
  }

  /** Load one profile follow-list page for the requested PGC category. */
  fun getBangumiWatchingFollowedPage(
    mid: Long,
    category: BangumiExploreCategory,
    page: Int,
  ): SpaceBangumiResponse {
    val seasonType = bangumiSeasonType(category) ?: return SpaceBangumiResponse(emptyList(), false)
    return getSpaceBangumi(mid, type = 1, page = page, pageSize = 30)
      .let { response -> response.copy(cards = response.cards.filter { it.seasonType == seasonType }) }
  }

  internal fun bangumiSeasonType(category: BangumiExploreCategory): Int? =
    when (category) {
      BangumiExploreCategory.ANIME -> 1
      BangumiExploreCategory.GUOCHUANG -> 4
      else -> null
    }

  /** Pure merge step kept separate so category filtering and ordering remain unit-testable. */
  internal fun mergeBangumiWatchingCards(
    followed: List<SpaceContentCard>,
    history: List<SpaceContentCard>,
    seasonType: Int,
  ): List<SpaceContentCard> {
    val followedBySeason = followed.filter { it.seasonType == seasonType && it.seasonId > 0L }
      .associateBy { it.seasonId }
    val historyBySeason = linkedMapOf<Long, SpaceContentCard>()
    val historyWithoutSeason = linkedMapOf<String, SpaceContentCard>()
    history.filter { it.seasonType == seasonType }.forEach { card ->
      if (card.seasonId > 0L) historyBySeason.putIfAbsent(card.seasonId, card)
      else historyWithoutSeason.putIfAbsent(card.id, card)
    }
    val merged = buildList {
      historyBySeason.values.forEach { watched ->
        val followedCard = followedBySeason[watched.seasonId]
        add(
          if (followedCard == null) watched.copy(
            coverUrl = watched.historyCoverUrl.ifBlank { watched.coverUrl },
            hasHistory = true,
            historicalOnly = true,
          )
          else followedCard.copy(
            subtitle = watched.subtitle.ifBlank { followedCard.subtitle },
            coverUrl =
              watched.historyCoverUrl
                .ifBlank { watched.coverUrl }
                .ifBlank { followedCard.coverUrl },
            videoUrl = watched.videoUrl.ifBlank { followedCard.videoUrl },
            aid = watched.aid.takeIf { it > 0L } ?: followedCard.aid,
            bvid = watched.bvid.ifBlank { followedCard.bvid },
            episodeId = watched.episodeId.takeIf { it > 0L } ?: 0L,
            watchProgress = watched.watchProgress,
            hasHistory = true,
            historicalOnly = false,
          )
        )
      }
      historyWithoutSeason.values.forEach { add(it.copy(hasHistory = true, historicalOnly = true)) }
      followed.forEach { card ->
        if (card.seasonType != seasonType) return@forEach
        val alreadyAdded = card.seasonId > 0L && historyBySeason.containsKey(card.seasonId)
        if (!alreadyAdded) {
          add(
            card.copy(
              episodeId = 0L,
              videoUrl = card.seasonId.takeIf { it > 0L }?.let {
                "https://www.bilibili.com/bangumi/play/ss$it"
              }.orEmpty(),
              watchProgress = null,
              hasHistory = false,
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
    val cards =
      buildList {
        for (i in 0 until list.length()) {
          val row = list.optJSONObject(i) ?: continue
          val seasonId = row.optLong("season_id")
          val latestEpisode = row.optJSONObject("new_ep")
          val episodeId = latestEpisode?.optLong("id") ?: 0L
          val latestLabel = latestEpisode?.optString("index_show").orEmpty()
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
              watchProgress = parseBangumiWatchProgress(row),
              seasonType = row.optInt("season_type"),
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

  /** Parse inline watch-progress from a follow-list row or a season/user/status response. */
  internal fun parseBangumiWatchProgress(row: JSONObject): BangumiWatchProgress? {
    // Inline progress from follow/list: row.user_season.last_ep_id
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

    // Progress from season/user/status: result.progress.last_ep_id or result.watch_progress
    val status = row.optJSONObject("result") ?: row.optJSONObject("data")
    if (status != null) {
      val progressObj = status.optJSONObject("progress")
        ?: status.optJSONObject("watch_progress")
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

    // Standalone progress JSON object (last resort: row itself has last_ep_id)
    val standalone = row.optJSONObject("progress")
    if (standalone != null) return parseBangumiWatchProgressObject(standalone)
    return null
  }

  /**
   * Bilibili's web player uses last_ep_progress (milliseconds) and
   * last_ep_index_title. Older follow/status responses use last_time (seconds)
   * and last_ep_index, so keep that shape as a compatibility fallback.
   */
  private fun parseBangumiWatchProgressObject(value: JSONObject): BangumiWatchProgress? {
    val episodeId = value.optNonNegativeLong("last_ep_id")?.takeIf { it > 0L } ?: return null
    val index = value.optString("last_ep_index_title").ifBlank {
      value.optString("last_ep_index")
    }
    val positionMs =
      value.optNonNegativeLong("last_ep_progress")
        ?: value.optNonNegativeLong("last_time")?.coerceAtMost(Long.MAX_VALUE / 1_000L)?.times(1_000L)
        ?: 0L
    return BangumiWatchProgress(
      episodeId = episodeId,
      episodeIndex = index,
      positionMs = positionMs,
    )
  }

  private fun JSONObject.optNonNegativeLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
      is Number -> value.toLong().takeIf { it >= 0L }
      is String -> value.trim().toLongOrNull()?.takeIf { it >= 0L }
      else -> null
    }
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
          "1", "true" -> true
          "0", "false" -> false
          else -> null
        }
      else -> null
    }
  }

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
   * Keeps the secondary bangumi page on the same public PGC payloads as the homepage carousel.
   * The response shape differs slightly between v2 and v3, so it is intentionally normalized here
   * instead of leaking raw module JSON into Compose.
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
   * st / season_type for each PGC index category, from the verified index-entry matrix in
   * 番剧二级子页接口文档.md §1.4 (anime=1, guochuang=4, movie=2, tv=5, documentary=3, variety=7).
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

  /** Default index sort per category (番剧/国创 order=3, the rest order=2). */
  internal fun bangumiIndexDefaultOrder(category: BangumiExploreCategory): BangumiIndexOrder =
    when (category) {
      BangumiExploreCategory.ANIME, BangumiExploreCategory.GUOCHUANG -> BangumiIndexOrder.FOLLOWING
      else -> BangumiIndexOrder.VIEWS
    }

  /**
   * Filter fields each category's index actually accepts, mapped from the typed query. Only the
   * documented fields for that category are sent (the interface doc warns against passing another
   * category's fields). Fields the UI does not expose for a category stay at the `-1` default.
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
      BangumiExploreCategory.MOVIE, BangumiExploreCategory.TV ->
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

  /** Builds the documented public web query for a category's PGC index without leaking raw labels. */
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
    val items = buildList {
      for (index in 0 until list.length()) {
        val item = list.optJSONObject(index) ?: continue
        val seasonId = item.optLong("season_id")
        val firstEpisode = item.optJSONObject("first_ep")
        val episodeId = firstEpisode?.optLong("ep_id") ?: 0L
        if (seasonId <= 0L && episodeId <= 0L) continue
        val rawTarget = item.optString("link")
        val targetUrl =
          rawTarget.ifBlank {
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
            coverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("cover")).orEmpty(),
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
    }.distinctBy { item -> item.seasonId.takeIf { it > 0L } ?: item.episodeId }
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
    val sections = buildList {
      for (moduleIndex in 0 until modules.length()) {
        val module = modules.optJSONObject(moduleIndex) ?: continue
        val style = module.optString("style")
        if (style == "web_index_v3" || style == "web_index_v2" || style == "web_banner_v3" || style == "web_banner_v2") {
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
          module.optString("title").trim().ifBlank {
            module.optJSONObject("header")?.optString("title").orEmpty().trim()
          }.ifBlank { "为你推荐" }
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
    }.take(6)
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
          item.optJSONObject("hover")?.optString("img").orEmpty()
            .ifBlank { item.optString("big_cover") }
            .ifBlank { rawCover }
        val heroCoverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(heroRawCover).orEmpty()
        if (title.isBlank() || targetUrl.isBlank() || coverUrl.isBlank() || (seasonId <= 0L && episodeId <= 0L)) return
        val subtitle =
          item.optString("sub_title").trim().ifBlank {
            item.optString("evaluate").trim()
          }.ifBlank { item.optString("text").trim() }.ifBlank { parentSubtitle }
        val itemCardStyle =
          when (item.optString("card_style").trim().lowercase()) {
            "v_card", "poster", "vertical" -> BangumiExploreCardStyle.POSTER
            "h_card", "landscape", "horizontal" -> BangumiExploreCardStyle.LANDSCAPE
            else -> defaultCardStyle
          }
        val parsedRating =
          item.optString("rating").trim().toDoubleOrNull()
            ?: item.optDouble("rating", Double.NaN)
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
    }.distinctBy(BangumiExploreItem::stableId)

  private fun getBangumiRecommendationSource(
    source: BangumiRecommendationSource,
  ): List<BangumiRecommendation> {
    val response = BiliHttpClient.get(
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
    ).take(source.takeCount)
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
        val bannerUrl =
          dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(rawBanner).orEmpty()
        if (bannerUrl.isBlank()) continue
        val cardUrl =
          dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(
            item.optString("cover").ifBlank { rawBanner }
          ).orEmpty()
        if (cardUrl.isBlank()) continue
        val targetUrl =
          item.optString("url").ifBlank { item.optString("link") }
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
    fun parseEpisodes(array: org.json.JSONArray?): List<BangumiEpisode> =
      buildList {
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
                  dev.openbili.webdemo.UrlPolicy
                    .normalizeImageUrl(episode.optString("cover"))
                    .orEmpty(),
                durationSeconds =
                  (episode.optLong("duration") / 1_000L).coerceAtLeast(0L),
              )
            )
          }
        }
      }
    val episodes = parseEpisodes(data.optJSONArray("episodes"))
    val seasons =
      buildList {
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
    val sections =
      buildList {
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
    val followed =
      remoteStatus?.followed
        ?: inlineUserStatus?.optPositiveFlag("follow")
        ?: false
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
      followCount =
        stat?.let { it.optLong("favorites", it.optLong("series_follow")) } ?: 0L,
      viewCount = stat?.optLong("views") ?: 0L,
      danmakuCount = stat?.optLong("danmakus") ?: 0L,
      followed = followed,
      episodes = episodes,
      seasons = seasons,
      sections = sections,
      userRatingScore =
        inlineUserStatus
          ?.optJSONObject("review")
          ?.optInt("score")
          ?.takeIf { it in 2..10 && it % 2 == 0 },
    )
  }

  fun setBangumiFollow(seasonId: Long, followed: Boolean) {
    require(seasonId > 0L) { "番剧标识无效" }
    val csrf = requireCsrf()
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
      throw IllegalStateException(
        json.optString("message", if (followed) "追番失败" else "取消追番失败")
      )
    }
  }

  fun postBangumiShortReview(mediaId: Long, score: Int, content: String) {
    require(mediaId > 0L) { "媒体标识无效" }
    require(score in 2..10 && score % 2 == 0) { "评分应为一到五颗星" }
    val csrf = requireCsrf()
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
   * Maps a normal video URL or a temporary bangumi page URL to the BV id consumed by the native
   * video page. Bangumi cards currently open their latest available episode in that existing page.
   */
  fun resolveVideoBvid(videoUrl: String): String {
    val pageId = videoUrl.substringAfterLast("/").substringBefore("?").substringBefore("#")
    if (pageId.startsWith("BV", ignoreCase = true) || pageId.startsWith("av", ignoreCase = true)) {
      return pageId
    }
    val bangumiMatch =
      Regex("^(ep|ss)(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(pageId) ?: return pageId
    bangumiVideoBvidCache[pageId]?.let { return it }
    val id = bangumiMatch.groupValues[2]
    val parameter =
      if (bangumiMatch.groupValues[1].equals("ep", ignoreCase = true)) "ep_id" else "season_id"
    val resp =
      BiliHttpClient.get("https://api.bilibili.com/pgc/view/web/season?$parameter=$id")
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
        if (allowFallback) fallback = bvid
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
            val bvid =
              sectionEpisodes.optJSONObject(episodeIndex)?.optString("bvid").orEmpty()
            if (bvid.isNotBlank()) {
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

  fun getHistory(
    cursor: HistoryCursor = HistoryCursor(),
    type: String = "",
  ): AccountHistoryResponse {
    val resp = BiliHttpClient.get(historyCursorUrl(cursor, type))
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseHistoryResponse(json)
  }

  /**
   * Reply/@ feeds only return a compact author object, which omits level and VIP state. Resolve
   * that presentation data from the lightweight user-card endpoint instead of rendering Lv0.
   */
  fun getAccountMessageUserStyle(mid: Long): AccountMessageUserStyle {
    if (mid <= 0L) return AccountMessageUserStyle()
    accountMessageUserStyleCache[mid]?.let {
      return it
    }
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/card?mid=$mid")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val data = json.optJSONObject("data") ?: JSONObject()
    val card = data.optJSONObject("card") ?: data
    val vip = card.optJSONObject("vip") ?: JSONObject()
    val vipStatus = vip.optInt("status", card.optInt("vip_status", card.optInt("vipStatus", 0)))
    val vipType = vip.optInt("type", card.optInt("vip_type", card.optInt("vipType", 0)))
    val style =
      AccountMessageUserStyle(
        level =
          card.optJSONObject("level_info")?.optInt("current_level", card.optInt("level"))
            ?: card.optInt("level"),
        vipActive = vipStatus == 1 || (vipStatus != 0 && vipType > 0),
        vipLabel =
          vip
            .optJSONObject("label")
            ?.optString("text")
            .orEmpty()
            .ifBlank { card.optJSONObject("vip_label")?.optString("text").orEmpty() }
            .ifBlank { card.optString("vip_label") }
            .ifBlank { if (vipStatus == 1) "大会员" else "" },
      )
    accountMessageUserStyleCache.putIfAbsent(mid, style)
    return accountMessageUserStyleCache[mid] ?: style
  }

  internal fun historyCursorUrl(cursor: HistoryCursor, type: String): String {
    val encodedBusiness = URLEncoder.encode(cursor.business, "UTF-8")
    val encodedType = URLEncoder.encode(type, "UTF-8")
    return "https://api.bilibili.com/x/web-interface/history/cursor" +
      "?max=${cursor.max}&view_at=${cursor.viewAt}&business=$encodedBusiness" +
      "&type=$encodedType&ps=30"
  }

  internal fun parseHistoryResponse(json: JSONObject): AccountHistoryResponse {
    val data = json.optJSONObject("data") ?: JSONObject()
    val array = data.optJSONArray("list") ?: JSONArray()
    val items = buildList {
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val history = item.optJSONObject("history") ?: JSONObject()
        val business = history.optString("business", item.optString("business"))
        val oid = history.optLong("oid")
        val bvid = history.optString("bvid")
        val pgcTypeHints =
          listOf(
            item.optString("badge"),
            item.optString("tag_name"),
            item.optString("type_name"),
            item.optString("season_type_name"),
            history.optString("badge"),
            history.optString("tag_name"),
            item.optString("title"),
          )
        val pgcHistory =
          business == "pgc" ||
            business == "bangumi" ||
            history.optLong("epid") > 0L ||
            history.optLong("ep_id") > 0L ||
            item.optString("uri").contains("/bangumi/play/") ||
            (business == "archive" && pgcTypeHints.any(::isTypedDramaHistoryHint))
        when {
          business == "article" ||
            business == "article-list" ||
            item.optString("uri").contains("/read/cv") ||
            item.optString("uri").contains("/opus/") -> {
            if (oid <= 0L) continue
            val cover =
              item.optString("cover").ifBlank {
                item.optJSONArray("covers")?.optString(0).orEmpty()
              }
            add(
              AccountHistoryItem.Article(
                ArticleItem(
                  id = oid,
                  title = item.optString("title"),
                  summary =
                    item.optString("show_title").ifBlank {
                      item.optString("new_desc", item.optString("badge"))
                    },
                  coverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(cover).orEmpty(),
                  authorName = item.optString("author_name"),
                  authorFace =
                    dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("author_face"))
                      .orEmpty(),
                  authorMid = item.optLong("author_mid"),
                  publishedAt = item.optLong("view_at"),
                  sourceUrl = item.optString("uri"),
                )
              )
            )
          }
          business == "live" || business == "live_room" -> {
            if (oid <= 0L) continue
            val cover =
              item.optString("cover").ifBlank {
                item.optJSONArray("covers")?.optString(0).orEmpty()
              }
            val statusText =
              listOf(item.optString("badge"), item.optString("show_title"))
                .joinToString(" ")
            add(
              AccountHistoryItem.Live(
                roomId = oid,
                title = item.optString("title").ifBlank { "直播间 $oid" },
                anchorUid = item.optLong("author_mid"),
                anchorName = item.optString("author_name").ifBlank { "主播" },
                anchorFace =
                  dev.openbili.webdemo.UrlPolicy
                    .normalizeImageUrl(item.optString("author_face")),
                coverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(cover),
                keyframeUrl =
                  dev.openbili.webdemo.UrlPolicy
                    .normalizeImageUrl(
                      history.optString("keyframe").ifBlank { item.optString("keyframe") }
                    ),
                areaName =
                  item
                    .optString("tag_name")
                    .ifBlank { item.optString("show_title") }
                    .takeIf(String::isNotBlank),
                parentAreaName = item.optString("parent_area_name").takeIf(String::isNotBlank),
                liveStatus =
                  if (item.has("live_status")) item.optInt("live_status")
                  else if (statusText.contains("直播中")) 1 else 0,
                viewAt = item.optLong("view_at"),
              )
            )
          }
          pgcHistory -> {
            val episodeId =
              history
                .optLong("epid")
                .takeIf { it > 0L }
                ?: history.optLong("ep_id").takeIf { it > 0L }
                ?: Regex("/ep(\\d+)", RegexOption.IGNORE_CASE)
                  .find(item.optString("uri"))
                  ?.groupValues
                  ?.getOrNull(1)
                  ?.toLongOrNull()
                ?: 0L
            val seasonId =
              history
                .optLong("season_id")
                .takeIf { it > 0L }
                ?: item.optLong("season_id").takeIf { it > 0L }
                ?: Regex("/ss(\\d+)", RegexOption.IGNORE_CASE)
                  .find(item.optString("uri"))
                  ?.groupValues
                  ?.getOrNull(1)
                  ?.toLongOrNull()
                ?: 0L
            // Some cursor rows only expose the underlying archive aid/bvid. Keep those rows and
            // resolve their PGC redirect when the destination page loads; dropping them here made
            // valid bangumi history disappear entirely.
            if (episodeId <= 0L && seasonId <= 0L && bvid.isBlank() && oid <= 0L) continue
            val mediaLabel =
              historyPgcMediaLabel(
                *pgcTypeHints.toTypedArray(),
                item.optString("title"),
              )
            val seasonType =
              history.optInt(
                "season_type",
                item.optInt(
                  "season_type",
                  when {
                    mediaLabel == "国创" -> 4
                    mediaLabel.contains("番剧") -> 1
                    else -> 0
                  },
                ),
              )
            val cover =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("cover")).orEmpty()
            val historyId =
              "history:pgc:" +
                when {
                  episodeId > 0L -> "ep$episodeId"
                  bvid.isNotBlank() -> bvid
                  oid > 0L -> "av$oid"
                  else -> "ss$seasonId"
                }
            val kind =
              if (mediaLabel in setOf("番剧", "港澳台番剧", "国创")) {
                SpaceContentKind.BANGUMI
              } else {
                SpaceContentKind.DRAMA
              }
            val progressSeconds =
              item.optNonNegativeLong("progress") ?: history.optNonNegativeLong("progress")
            val durationSeconds =
              item.optNonNegativeLong("duration") ?: history.optNonNegativeLong("duration")
            val progressPercent =
              if (progressSeconds != null && durationSeconds != null && durationSeconds > 0L) {
                ((progressSeconds * 100L) / durationSeconds).toInt().coerceIn(0, 100)
              } else null
            val episodeIndex =
              history.optString("ep_index").ifBlank {
                history.optString("ep_index_title").ifBlank { item.optString("show_title") }
              }
            val watchProgress =
              episodeId.takeIf { it > 0L }?.let {
                BangumiWatchProgress(
                  episodeId = it,
                  episodeIndex = episodeIndex,
                  positionMs =
                    (progressSeconds ?: 0L).coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L,
                  percent = progressPercent,
                )
              }
            val bangumiCard =
              SpaceContentCard(
                id = historyId,
                title = item.optString("title"),
                subtitle = item.optString("show_title").ifBlank { item.optString("new_desc") },
                // The cursor row's cover is the best fallback for history-only seasons. Followed
                // rows are merged with the season artwork below when available.
                coverUrl = "",
                historyCoverUrl = cover,
                aid = oid,
                bvid = bvid,
                videoUrl =
                  when {
                    episodeId > 0L -> "https://www.bilibili.com/bangumi/play/ep$episodeId"
                    bvid.isNotBlank() -> "https://www.bilibili.com/video/$bvid"
                    oid > 0L -> "https://www.bilibili.com/video/av$oid"
                    else -> "https://www.bilibili.com/bangumi/play/ss$seasonId"
                  },
                seasonId = seasonId,
                episodeId = episodeId,
                kind = kind,
                watchProgress = watchProgress,
                seasonType = seasonType,
                hasHistory = true,
                historicalOnly = true,
              )
            add(
              AccountHistoryItem.Bangumi(
                card =
                  FeedCard(
                    aid = oid,
                    bvid = bvid,
                    cid = history.optLong("cid"),
                    title = item.optString("title"),
                    // Keep the watched episode cover on the history video card only.
                    coverUrl = cover,
                    uploaderName = item.optString("author_name"),
                    uploaderFace =
                      dev.openbili.webdemo.UrlPolicy
                        .normalizeImageUrl(item.optString("author_face"))
                        .orEmpty(),
                    uploaderMid = item.optLong("author_mid"),
                    playCount = 0,
                    danmakuCount = 0,
                    durationSeconds = item.optLong("duration"),
                    pubdate = item.optLong("view_at"),
                    description = bangumiCard.subtitle,
                  ),
                bangumi = bangumiCard,
                mediaLabel = mediaLabel,
              )
            )
          }
          bvid.isNotBlank() || business == "archive" -> {
            if (bvid.isBlank() && oid <= 0L) continue
            add(
              AccountHistoryItem.Video(
                FeedCard(
                  aid = oid,
                  bvid = bvid,
                  cid = history.optLong("cid"),
                  title = item.optString("title"),
                  coverUrl =
                    dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("cover"))
                      .orEmpty(),
                  uploaderName = item.optString("author_name"),
                  uploaderFace =
                    dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("author_face"))
                      .orEmpty(),
                  uploaderMid = item.optLong("author_mid"),
                  playCount = 0,
                  danmakuCount = 0,
                  durationSeconds = item.optLong("duration"),
                  pubdate = item.optLong("view_at"),
                  description = item.optString("show_title").ifBlank { item.optString("new_desc") },
                )
              )
            )
          }
        }
      }
    }
    val cursorJson = data.optJSONObject("cursor") ?: JSONObject()
    val nextCursor =
      HistoryCursor(
        max = cursorJson.optLong("max"),
        viewAt = cursorJson.optLong("view_at"),
        business = cursorJson.optString("business"),
      )
    val hasMore =
      if (data.has("has_more")) data.optBoolean("has_more")
      else
        array.length() > 0 &&
          (nextCursor.max > 0L || nextCursor.viewAt > 0L || nextCursor.business.isNotBlank())
    return AccountHistoryResponse(items, nextCursor, hasMore)
  }

  internal fun historyPgcMediaLabel(vararg hints: String): String {
    val text = hints.joinToString(" ")
    return when {
      text.contains("港澳台", ignoreCase = true) &&
        (text.contains("番剧", ignoreCase = true) ||
          text.contains("动画", ignoreCase = true) ||
          text.contains("动漫", ignoreCase = true)) -> "港澳台番剧"
      text.contains("番剧", ignoreCase = true) ||
        text.contains("动画", ignoreCase = true) ||
        text.contains("动漫", ignoreCase = true) -> "番剧"
      text.contains("国创", ignoreCase = true) -> "国创"
      text.contains("[剧集]", ignoreCase = true) ||
        text.contains("【剧集】", ignoreCase = true) -> "电视剧"
      text.contains("电视剧", ignoreCase = true) -> "电视剧"
      text.contains("纪录片", ignoreCase = true) -> "纪录片"
      text.contains("综艺", ignoreCase = true) -> "综艺"
      text.contains("电影", ignoreCase = true) ||
        text.contains("剧场版", ignoreCase = true) -> "电影"
      else -> "影视"
    }
  }

  /** Keep legacy archive-to-drama routing, but never promote animation tags to PGC history. */
  private fun isTypedDramaHistoryHint(value: String): Boolean =
    value.contains("[剧集]", ignoreCase = true) ||
      value.contains("【剧集】", ignoreCase = true) ||
      value.contains("电视剧", ignoreCase = true) ||
      value.contains("纪录片", ignoreCase = true) ||
      value.contains("综艺", ignoreCase = true) ||
      value.contains("电影", ignoreCase = true) ||
      value.contains("剧场版", ignoreCase = true)

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

  fun getFollowingGroupMembers(
    groupId: Long,
    page: Int = 1,
    orderType: String = "",
  ): FollowingResponse {
    val encodedOrderType = URLEncoder.encode(orderType, "UTF-8")
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/relation/tag?tagid=$groupId&pn=$page&ps=50" +
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
      val groups = item.optJSONArray("tag")
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
        )
      )
    }
  }

  fun setFollowing(mid: Long, follow: Boolean) {
    val csrf = requireCsrf()
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

  fun isFollowing(mid: Long): Boolean {
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/relation?fid=$mid")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val attribute = json.optJSONObject("data")?.optInt("attribute") ?: 0
    return attribute and 2 != 0
  }

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

  fun setFollowingGroup(mid: Long, groupId: Long) {
    val csrf = requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/relation/tags/addUsers",
        mapOf("fids" to mid.toString(), "tagids" to groupId.toString(), "csrf" to csrf),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message", "分组失败"))
  }

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
        if (
          row.optInt("system_msg_type") == 0 &&
            account?.optString("name").isNullOrBlank()
        ) {
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
                if (last.optLong("sender_uid") != talker) "你撤回了一条消息"
                else "对方撤回了一条消息"
              } else parsed.content,
            sourceContent = "",
            oid = 0,
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
            // Session payloads use msg_seqno. Reading seqno made every read receipt use 0,
            // so the local dot disappeared but the server kept the conversation unread.
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
        .mapNotNull { index -> array.optJSONObject(index)?.optLong("session_ts")?.takeIf { it > 0L } }
        .minOrNull()
        ?.minus(1L)
        ?: 0L
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
    val array = data.optJSONArray("messages") ?: data.optJSONArray("message_list")
      ?: return PrivateMessagePage(emptyList())
    val talker = getMessageUsers(listOf(talkerId))[talkerId]
    val items = buildList {
      for (i in 0 until array.length()) {
        val row = array.optJSONObject(i) ?: continue
        val sender = row.optLong("sender_uid", row.optLong("sender_id"))
        val receiver = row.optLong("receiver_id")
        val type = row.optInt("msg_type")
        val parsed = parsePrivateContent(type, row.optString("content"))
        val seq = row.optLong("seqno", row.optLong("msg_seqno"))
        val outgoing = sender == accountMid
        val withdrawn = row.optInt("msg_status") == 1 || type == 5
        val timestamp = row.optLong("timestamp").takeIf { it > 0L }
          ?: (row.optLong("msg_key") / 1_000_000L)
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
            oid = 0L,
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
    }.sortedBy(AccountMessage::sequence)
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

  private data class ParsedPrivateContent(
    val title: String,
    val content: String,
    val coverUrl: String = "",
    val linkUrl: String = "",
    val notifier: Pair<String, String>? = null,
    val mediaWidth: Int = 0,
    val mediaHeight: Int = 0,
    val noticeStyle: Boolean = false,
  )

  private fun parsePrivateContent(type: Int, raw: String): ParsedPrivateContent {
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
        16 -> firstTitle(body.optString("main_title"), firstSubCard?.optString("title").orEmpty(), "推荐内容")
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
        14 -> body.optString("cover")
        12 -> body.optJSONArray("image_urls")?.optString(0).orEmpty().ifBlank {
          body.optString("cover")
        }
        13 -> body.optString("pic_url")
        16 -> firstSubCard?.optString("cover_url").orEmpty()
        else -> ""
      }
    val link =
      when (type) {
        7 -> body.optString("url", body.optString("bvid"))
        10 ->
          body.optJSONObject("jump_uri_config")?.optString("all_uri").orEmpty().ifBlank {
            body.optString("jump_uri")
          }
        11 ->
          body.optString("bvid").ifBlank {
            body.optLong("aid").takeIf { it > 0L }?.let { "https://www.bilibili.com/video/av$it" }.orEmpty()
          }
        12 -> body.optString("url", body.optString("jump_url"))
        13 -> body.optString("jump_url")
        14 -> body.optString("url")
        16 -> firstSubCard?.optString("jump_url").orEmpty()
        else -> ""
      }
    return ParsedPrivateContent(
      title = title,
      content = content.ifBlank { title },
      coverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(cover).orEmpty(),
      linkUrl = link,
      notifier = notifier,
      mediaWidth = if (type == 2) body.optInt("width", body.optInt("image_width")) else 0,
      mediaHeight = if (type == 2) body.optInt("height", body.optInt("image_height")) else 0,
    )
  }

  fun sendPrivateMessage(senderMid: Long, receiverMid: Long, text: String) {
    require(senderMid > 0 && receiverMid > 0 && text.isNotBlank()) { "私信参数无效" }
    val csrf = requireCsrf()
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

  fun uploadPrivateImage(
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
    width: Int,
    height: Int,
  ): PrivateImageUpload =
    uploadBfsImage(bytes, fileName, mimeType, width, height, biz = "im")

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
    val csrf = requireCsrf()
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
      require(directUrl.startsWith("http://") || directUrl.startsWith("https://") || directUrl.startsWith("//")) {
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
    // This endpoint returns `data` as either a URL string or an object depending on
    // the current web account/risk-control path. Treat both shapes as successful.
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
    val csrf = requireCsrf()
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
    val json = parsePossiblyEncodedJsonValue(rawResponse) as? JSONObject
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

  fun withdrawPrivateMessage(senderMid: Long, receiverMid: Long, messageKey: Long) {
    require(senderMid > 0L && receiverMid > 0L && messageKey > 0L) { "撤回参数无效" }
    val csrf = requireCsrf()
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

  fun markPrivateMessageRead(talkerId: Long, ackSequence: Long) {
    if (talkerId <= 0L || ackSequence <= 0L) return
    val csrf = requireCsrf()
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
    val csrf = requireCsrf()
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

  fun reportPlaybackStart(aid: Long, cid: Long) {
    val csrf = requireCsrf()
    val now = System.currentTimeMillis() / 1000
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/report/click/h5",
        mapOf(
          "aid" to aid.toString(),
          "cid" to cid.toString(),
          "part" to "1",
          "mid" to (BiliHttpClient.cookieValue("DedeUserID") ?: "0"),
          "did" to (BiliHttpClient.cookieValue("sid") ?: ""),
          "ftime" to now.toString(),
          "stime" to now.toString(),
          "jsonp" to "jsonp",
          "csrf" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0)
      Log.w(TAG, "playback start report failed: ${json.optString("message")}")
    else Log.d(TAG, "playback start reported: aid=$aid")
  }

  fun reportPlayback(
    aid: Long,
    cid: Long,
    playedSeconds: Long,
  ) {
    val csrf = requireCsrf()
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/v2/history/report",
        mapOf(
          "aid" to aid.toString(),
          "cid" to cid.toString(),
          "csrf" to csrf,
          "progress" to playedSeconds.coerceAtLeast(0L).toString(),
          "platform" to "android",
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) {
      val message = json.optString("message", "观看进度上报失败")
      Log.w(TAG, "playback progress report failed: $message")
      throw IllegalStateException(message)
    }
    Log.d(TAG, "playback progress reported: aid=$aid cid=$cid progress=$playedSeconds")
  }

  /**
   * Report a PGC heartbeat. The generic history endpoint accepts the same aid/cid pair but does
   * not advance the season's "正在追" progress; the web player distinguishes episode playback
   * with the season/episode IDs plus type=4 and the media sub_type.
   */
  fun reportBangumiPlayback(
    aid: Long,
    cid: Long,
    episodeId: Long,
    seasonId: Long,
    playedSeconds: Long,
    durationSeconds: Long,
    startTimestamp: Long,
    subType: Int,
    playType: Int = 0,
  ) {
    if (aid <= 0L || cid <= 0L || episodeId <= 0L || seasonId <= 0L || subType <= 0) return
    val csrf = requireCsrf()
    val nowSeconds = System.currentTimeMillis() / 1000L
    val sessionSeconds =
      if (startTimestamp > 0L) (nowSeconds - startTimestamp).coerceAtLeast(0L) else 0L
    val safePlayedSeconds = playedSeconds.coerceAtLeast(0L)
    val resp =
      BiliHttpClient.postForm(
        "https://api.bilibili.com/x/click-interface/web/heartbeat",
        mapOf(
          "aid" to aid.toString(),
          "cid" to cid.toString(),
          "epid" to episodeId.toString(),
          "sid" to seasonId.toString(),
          "mid" to (BiliHttpClient.cookieValue("DedeUserID") ?: "0"),
          "played_time" to safePlayedSeconds.toString(),
          "realtime" to sessionSeconds.toString(),
          "real_played_time" to sessionSeconds.toString(),
          "start_ts" to startTimestamp.coerceAtLeast(0L).toString(),
          "type" to "4",
          "sub_type" to subType.toString(),
          "dt" to "2",
          "play_type" to playType.coerceIn(0, 4).toString(),
          "video_duration" to durationSeconds.coerceAtLeast(0L).toString(),
          "csrf" to csrf,
        ),
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) {
      val message = json.optString("message", "番剧播放心跳上报失败")
      Log.w(
        TAG,
        "bangumi heartbeat failed: aid=$aid cid=$cid epid=$episodeId sid=$seasonId subtype=$subType code=${json.optInt("code")} message=$message",
      )
      throw IllegalStateException(message)
    }
    Log.d(
      TAG,
      "bangumi heartbeat reported: aid=$aid cid=$cid epid=$episodeId sid=$seasonId subtype=$subType progress=$safePlayedSeconds",
    )
  }

  fun getPlaybackProgressMs(aid: Long, cid: Long): Long {
    if (aid <= 0L || cid <= 0L) return 0L
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/player/v2?aid=$aid&cid=$cid")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) return 0L
    val data = json.optJSONObject("data") ?: return 0L
    val lastCid = data.optLong("last_play_cid", 0L)
    if (lastCid > 0L && lastCid != cid) return 0L
    return data.optLong("last_play_time", 0L).coerceAtLeast(0L)
  }

  fun getHotSearch(): List<HotSearchItem> {
    val resp =
      BiliHttpClient.get(
        "https://api.bilibili.com/x/web-interface/search/square?limit=20&platform=web"
      )
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val array =
      json.optJSONObject("data")?.optJSONObject("trending")?.optJSONArray("list")
        ?: return emptyList()
    return buildList {
      for (i in 0 until array.length()) {
        val item = array.getJSONObject(i)
        add(
          HotSearchItem(
            item.optString("keyword"),
            item.optString("show_name", item.optString("keyword")),
          )
        )
      }
    }
  }

  fun searchVideos(keyword: String, page: Int = 1, order: String = "totalrank"): FeedResponse {
    val query =
      signedQuery(
        mapOf(
          "search_type" to "video",
          "keyword" to keyword,
          "page" to page.toString(),
          "order" to order,
          "duration" to "0",
          "tids" to "0",
        )
      )
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/wbi/search/type?$query")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val array =
      json.optJSONObject("data")?.optJSONArray("result") ?: return FeedResponse(emptyList())
    val cards = buildList {
      for (i in 0 until array.length()) {
        val item = array.getJSONObject(i)
        val duration = parseClockDuration(item.optString("duration"))
        val title = Html.fromHtml(item.optString("title"), Html.FROM_HTML_MODE_LEGACY).toString()
        add(
          FeedCard(
            item.optLong("aid", item.optLong("id")),
            item.optString("bvid"),
            0,
            title,
            dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("pic")).orEmpty(),
            item.optString("author"),
            dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("upic")).orEmpty(),
            item.optLong("mid"),
            item.optLong("play"),
            item.optLong("video_review"),
            duration,
            item.optLong("senddate"),
            description =
              Html.fromHtml(item.optString("description"), Html.FROM_HTML_MODE_LEGACY).toString(),
          )
        )
      }
    }
    return FeedResponse(cards)
  }

  fun searchBangumi(
    keyword: String,
    page: Int = 1,
    kind: SpaceContentKind,
  ): SpaceBangumiResponse {
    require(kind == SpaceContentKind.BANGUMI || kind == SpaceContentKind.DRAMA) {
      "搜索类型无效"
    }
    val searchType = if (kind == SpaceContentKind.BANGUMI) "media_bangumi" else "media_ft"
    val query =
      signedQuery(
        mapOf(
          "search_type" to searchType,
          "keyword" to keyword,
          "page" to page.toString(),
        )
      )
    val response = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/wbi/search/type?$query")
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseBangumiSearchResponse(json, requestedPage = page, kind = kind)
  }

  /**
   * The public search surface groups PGC into only "番剧" and "影视". Keep the request server
   * backed, then use the returned season/type marker to retain the exact index category.
   */
  fun searchBangumiIndex(
    keyword: String,
    category: BangumiExploreCategory,
    page: Int,
  ): BangumiIndexPage {
    val kind =
      if (category == BangumiExploreCategory.ANIME || category == BangumiExploreCategory.GUOCHUANG) {
        SpaceContentKind.BANGUMI
      } else {
        SpaceContentKind.DRAMA
      }
    val searchType = if (kind == SpaceContentKind.BANGUMI) "media_bangumi" else "media_ft"
    val query =
      signedQuery(
        mapOf(
          "search_type" to searchType,
          "keyword" to keyword,
          "page" to page.toString(),
        )
      )
    val response = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/wbi/search/type?$query")
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseBangumiIndexSearchPage(json, category, page)
  }

  private fun parseBangumiIndexSearchPage(
    json: JSONObject,
    category: BangumiExploreCategory,
    requestedPage: Int,
  ): BangumiIndexPage {
    val data = json.optJSONObject("data") ?: JSONObject()
    val list = data.optJSONArray("result") ?: JSONArray()
    val expectedSeasonType = bangumiIndexSt(category).toInt()
    val items = buildList {
      for (index in 0 until list.length()) {
        val item = list.optJSONObject(index) ?: continue
        val seasonType = item.optInt("season_type", item.optInt("media_type", 0))
        val typeName = decodeHtmlText(item.optString("type_name")).trim()
        val matchesCategory =
          seasonType == expectedSeasonType ||
            (seasonType <= 0 && typeName == category.label)
        if (!matchesCategory) continue
        val seasonId = item.optLong("season_id")
        val firstEpisode = item.optJSONArray("eps")?.optJSONObject(0)
        val episodeUrl = firstEpisode?.optString("url").orEmpty()
        val episodeId =
          firstEpisode?.optLong("id")?.takeIf { it > 0L }
            ?: bangumiIdentityFromUrl(episodeUrl).episodeId.takeIf { it > 0L }
            ?: 0L
        if (seasonId <= 0L && episodeId <= 0L) continue
        val title = decodeHtmlText(item.optString("title")).trim()
        if (title.isBlank()) continue
        val badge = decodeHtmlText(item.optString("badge")).trim()
        val score =
          item.optJSONObject("media_score")
            ?.optString("score")
            .orEmpty()
            .ifBlank { item.optString("score") }
            .trim()
        val targetUrl =
          firstEpisode?.optString("url")?.takeIf(String::isNotBlank)
            ?: if (seasonId > 0L) "https://www.bilibili.com/bangumi/play/ss$seasonId"
            else "https://www.bilibili.com/bangumi/play/ep$episodeId"
        add(
          BangumiIndexItem(
            seasonId = seasonId,
            mediaId = item.optLong("media_id"),
            episodeId = episodeId,
            title = title,
            subtitle =
              decodeHtmlText(item.optString("index_show")).trim()
                .ifBlank { decodeHtmlText(item.optString("evaluate")).trim() },
            coverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("cover")).orEmpty(),
            targetUrl = targetUrl,
            indexShow = decodeHtmlText(item.optString("index_show")).trim(),
            badge = badge,
            badgeColor = "",
            badgeNightColor = "",
            score = score,
            orderText = typeName,
            seasonType = seasonType.takeIf { it > 0 } ?: expectedSeasonType,
          )
        )
      }
    }.distinctBy { it.seasonId.takeIf { id -> id > 0L } ?: it.episodeId }
    val currentPage = data.optInt("page", requestedPage).takeIf { it > 0 } ?: requestedPage
    val pageSize = data.optInt("pagesize", 20).takeIf { it > 0 } ?: 20
    val total = data.optInt("numResults", 0).coerceAtLeast(0)
    return BangumiIndexPage(
      items = items,
      page = currentPage,
      hasNext = if (total > 0) currentPage * pageSize < total else list.length() >= pageSize,
      total = total,
    )
  }

  internal fun parseBangumiSearchResponse(
    json: JSONObject,
    requestedPage: Int,
    kind: SpaceContentKind,
  ): SpaceBangumiResponse {
    val data = json.optJSONObject("data") ?: JSONObject()
    val array = data.optJSONArray("result") ?: JSONArray()
    val cards = buildList {
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val seasonId = item.optLong("season_id")
        val episodes = item.optJSONArray("eps")
        val episode = episodes?.optJSONObject(0)
        val episodeUrl = episode?.optString("url").orEmpty()
        val episodeId =
          episode?.optLong("id")?.takeIf { it > 0L }
            ?: bangumiIdentityFromUrl(episodeUrl).episodeId.takeIf { it > 0L }
            ?: 0L
        if (seasonId <= 0L && episodeId <= 0L) continue
        val title = decodeHtmlText(item.optString("title")).ifBlank { "番剧影视" }
        val subtitle =
          decodeHtmlText(item.optString("index_show"))
            .ifBlank { decodeHtmlText(item.optString("evaluate")) }
            .ifBlank { decodeHtmlText(item.optString("type_name")) }
        add(
          SpaceContentCard(
            id = "search:${kind.name.lowercase()}:${seasonId.takeIf { it > 0L } ?: episodeId}",
            title = title,
            subtitle = subtitle,
            coverUrl =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("cover")).orEmpty(),
            videoUrl =
              when {
                episodeId > 0L -> "https://www.bilibili.com/bangumi/play/ep$episodeId"
                seasonId > 0L -> "https://www.bilibili.com/bangumi/play/ss$seasonId"
                else -> episodeUrl
              },
            seasonId = seasonId,
            episodeId = episodeId,
            kind = kind,
          )
        )
      }
    }
    val page = data.optInt("page", requestedPage).takeIf { it > 0 } ?: requestedPage
    val pageSize = data.optInt("pagesize", 20).takeIf { it > 0 } ?: 20
    val total = data.optInt("numResults", -1)
    val hasMore = if (total >= 0) page * pageSize < total else array.length() >= pageSize
    return SpaceBangumiResponse(cards, hasMore)
  }

  fun searchArticles(
    keyword: String,
    page: Int = 1,
    order: String = "totalrank",
  ): ArticleSearchResponse {
    val query =
      signedQuery(
        mapOf(
          "search_type" to "article",
          "keyword" to keyword,
          "page" to page.toString(),
          "order" to order,
        )
      )
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/wbi/search/type?$query")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    return parseArticleSearchResponse(json, page)
  }

  internal fun parseArticleSearchResponse(
    json: JSONObject,
    requestedPage: Int,
  ): ArticleSearchResponse {
    val data = json.optJSONObject("data") ?: JSONObject()
    val array = data.optJSONArray("result") ?: JSONArray()
    val items = buildList {
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val id = item.optLong("id")
        if (id <= 0L) continue
        val covers = item.optJSONArray("image_urls")
        val cover = covers?.optString(0).orEmpty()
        add(
          ArticleItem(
            id = id,
            title = decodeHtmlText(item.optString("title")),
            summary = decodeHtmlText(item.optString("desc")),
            coverUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(cover).orEmpty(),
            authorName = decodeHtmlText(item.optString("author")),
            authorMid = item.optLong("mid"),
            categoryName = item.optString("category_name"),
            publishedAt = item.optLong("pub_time", item.optLong("pubdate")),
            viewCount = item.optLong("view"),
            likeCount = item.optLong("like"),
            replyCount = item.optLong("reply"),
          )
        )
      }
    }
    val currentPage = data.optInt("page", requestedPage)
    val totalPages = data.optInt("numPages")
    val hasMore = if (totalPages > 0) currentPage < totalPages else items.size >= 20
    return ArticleSearchResponse(items, hasMore)
  }

  fun getArticleDetail(article: ArticleItem): ArticleDetail {
    val headers =
      mapOf(
        "User-Agent" to
          "Mozilla/5.0 (Linux; Android 12; Tablet) AppleWebKit/537.36 Chrome/124 Safari/537.36",
        "Referer" to "https://www.bilibili.com/",
      )
    var requestUrl =
      article.sourceUrl
        .takeIf {
          it.contains("/read/cv", ignoreCase = true) || it.contains("/opus/", ignoreCase = true)
        }
        ?.let {
          when {
            it.startsWith("//") -> "https:$it"
            it.startsWith("http://") -> it.replaceFirst("http://", "https://")
            else -> it
          }
        } ?: "https://www.bilibili.com/read/cv${article.id}/"
    var opusId: Long? =
      ARTICLE_OPUS_ID_REGEX.find(requestUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()
    var lastFailure: Throwable? = null

    // Opus occasionally serves a transient bootstrap/risk-control document on the first request.
    // Use the account cookie jar, retain the redirect target, and retry the small HTML bootstrap
    // before deciding that the article format itself is unsupported.
    repeat(3) { attempt ->
      runCatching {
          BiliHttpClient.get(requestUrl, headers).use { response ->
            val finalUrl = response.request.url.toString()
            opusId =
              ARTICLE_OPUS_ID_REGEX.find(finalUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()
                ?: opusId
            if (finalUrl.contains("/opus/")) requestUrl = finalUrl
            val html = response.body?.string().orEmpty()
            if (!response.isSuccessful || html.isBlank()) {
              throw IllegalStateException("专栏正文加载失败")
            }
            opusId =
              ARTICLE_OPUS_ID_REGEX.find(html)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: opusId
            parseArticlePage(html, article)
          }
        }
        .onSuccess { detail ->
          return detail
        }
        .onFailure { lastFailure = it }
      if (attempt < 2) Thread.sleep(90L * (attempt + 1))
    }

    // The JSON endpoint and the page bootstrap expose the same `detail` model. It is a useful
    // fallback when the HTML shell was returned without __INITIAL_STATE__.
    opusId?.let { id ->
      runCatching {
          BiliHttpClient.get(
              "https://api.bilibili.com/x/polymer/web-dynamic/v1/opus/detail" +
                "?id=$id&timezone_offset=-480&features=onlyfansVote,onlyfansAssetsV2," +
                "onlyfansOpusCard,decorationCard,htmlNewStyle",
              headers + ("Referer" to "https://www.bilibili.com/opus/$id"),
            )
            .use { response ->
              val root = JSONObject(response.body?.string().orEmpty())
              if (!response.isSuccessful || root.optInt("code") != 0) {
                throw IllegalStateException(root.optString("message", "专栏正文加载失败"))
              }
              parseArticleJson(root, article)
            }
        }
        .onSuccess { detail ->
          return detail
        }
        .onFailure { lastFailure = it }
    }

    Log.w(TAG, "article detail unavailable: cv${article.id}", lastFailure)
    throw IllegalStateException("专栏正文暂时没加载出来，请稍后重试")
  }

  fun createFavoriteFolder(title: String, isPublic: Boolean): Long {
    val normalized = title.trim()
    require(normalized.isNotBlank()) { "收藏夹名称不能为空" }
    val csrf = requireCsrf()
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

  fun editFavoriteFolder(folderId: Long, title: String, isPublic: Boolean) {
    val normalized = title.trim()
    require(folderId > 0L && normalized.isNotBlank()) { "收藏夹参数无效" }
    val csrf = requireCsrf()
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

  fun deleteFavoriteFolder(folderId: Long) {
    require(folderId > 0L) { "收藏夹参数无效" }
    val csrf = requireCsrf()
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

  internal fun parseArticlePage(html: String, fallback: ArticleItem): ArticleDetail {
    val initialState = extractInitialStateJson(html) ?: throw IllegalStateException("专栏正文格式暂不支持")
    return parseArticleJson(JSONObject(initialState), fallback)
  }

  internal fun parseArticleJson(root: JSONObject, fallback: ArticleItem): ArticleDetail {
    val detail =
      root.optJSONObject("detail")
        ?: root.optJSONObject("item")
        ?: root.optJSONObject("data")?.optJSONObject("item")
        ?: throw IllegalStateException("专栏正文为空")
    val basic = detail.optJSONObject("basic") ?: JSONObject()
    val modules = detail.optJSONArray("modules") ?: JSONArray()
    var title = fallback.title
    var coverUrl = fallback.coverUrl
    var authorName = fallback.authorName
    var authorFace = fallback.authorFace
    var authorMid = fallback.authorMid
    var publishedAt = fallback.publishedAt
    var likeCount = fallback.likeCount
    var replyCount = fallback.replyCount
    val commentOid =
      basic.optString("comment_id_str").toLongOrNull()
        ?: basic.optString("rid_str").toLongOrNull()
        ?: fallback.id
    val commentType = basic.optInt("comment_type", 12).takeIf { it > 0 } ?: 12
    val blocks = mutableListOf<ArticleBlock>()
    for (index in 0 until modules.length()) {
      val module = modules.optJSONObject(index) ?: continue
      when (module.optString("module_type")) {
        "MODULE_TYPE_TOP" -> {
          val picture =
            module
              .optJSONObject("module_top")
              ?.optJSONObject("display")
              ?.optJSONObject("album")
              ?.optJSONArray("pics")
              ?.optJSONObject(0)
          coverUrl =
            dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(picture?.optString("url").orEmpty())
              .orEmpty()
              .ifBlank { coverUrl }
        }
        "MODULE_TYPE_TITLE" ->
          title =
            module.optJSONObject("module_title")?.optString("text").orEmpty().ifBlank { title }
        "MODULE_TYPE_AUTHOR" -> {
          val author = module.optJSONObject("module_author") ?: continue
          authorName = author.optString("name").ifBlank { authorName }
          authorFace =
            dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(author.optString("face"))
              .orEmpty()
              .ifBlank { authorFace }
          authorMid = author.optLong("mid", authorMid)
          publishedAt = author.optLong("pub_ts", publishedAt)
        }
        "MODULE_TYPE_CONTENT" -> {
          val paragraphs =
            module.optJSONObject("module_content")?.optJSONArray("paragraphs") ?: JSONArray()
          parseArticleParagraphs(paragraphs, blocks)
        }
        "MODULE_TYPE_STAT" -> {
          val stat = module.optJSONObject("module_stat")
          likeCount = stat?.optJSONObject("like")?.optLong("count", likeCount) ?: likeCount
          replyCount = stat?.optJSONObject("comment")?.optLong("count", replyCount) ?: replyCount
        }
      }
    }
    val resolved =
      fallback.copy(
        title = title,
        coverUrl = coverUrl,
        authorName = authorName,
        authorFace = authorFace,
        authorMid = authorMid,
        publishedAt = publishedAt,
        likeCount = likeCount,
        replyCount = replyCount,
      )
    if (blocks.isEmpty() && fallback.summary.isNotBlank()) {
      blocks += ArticleBlock.Text(fallback.summary)
    }
    return ArticleDetail(resolved, blocks, commentOid = commentOid, commentType = commentType)
  }

  fun reportArticleRead(articleId: Long) {
    if (articleId <= 0L) return
    val csrf = BiliHttpClient.cookieValue("bili_jct") ?: return
    runCatching {
        BiliHttpClient.postForm(
            "https://api.bilibili.com/x/v2/history/report",
            mapOf(
              "aid" to articleId.toString(),
              "type" to "5",
              "dt" to "2",
              "csrf" to csrf,
            ),
          )
          .use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || json.optInt("code") != 0) {
              throw IllegalStateException(json.optString("message", "专栏历史上报失败"))
            }
          }
      }
      .onFailure { Log.w(TAG, "article history report failed: aid=$articleId", it) }
  }

  private fun extractInitialStateJson(html: String): String? {
    val marker = Regex("window\\.__INITIAL_STATE__\\s*=\\s*").find(html) ?: return null
    val start = html.indexOf('{', marker.range.last + 1)
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until html.length) {
      val char = html[index]
      if (inString) {
        when {
          escaped -> escaped = false
          char == '\\' -> escaped = true
          char == '"' -> inString = false
        }
      } else {
        when (char) {
          '"' -> inString = true
          '{' -> depth += 1
          '}' -> {
            depth -= 1
            if (depth == 0) return html.substring(start, index + 1)
          }
        }
      }
    }
    return null
  }

  private fun parseArticleParagraphs(
    paragraphs: JSONArray,
    output: MutableList<ArticleBlock>,
    quoted: Boolean = false,
  ) {
    for (index in 0 until paragraphs.length()) {
      val paragraph = paragraphs.optJSONObject(index) ?: continue
      val linkedBvid = findArticleBvid(paragraph.optJSONObject("link_card"))
      if (linkedBvid != null) {
        output += ArticleBlock.Video(linkedBvid)
        continue
      }
      when (paragraph.optInt("para_type")) {
        1 -> {
          val nodes = paragraph.optJSONObject("text")?.optJSONArray("nodes") ?: JSONArray()
          val parsed = parseArticleTextNodes(nodes)
          if (parsed.text.isNotBlank()) {
            val firstWord = nodes.optJSONObject(0)?.optJSONObject("word")
            val heading =
              (firstWord?.optInt("font_size", 0) ?: 0) >= 22 ||
                firstWord?.optJSONObject("style")?.optBoolean("bold") == true
            output +=
              ArticleBlock.Text(
                parsed.text,
                heading = heading,
                quote = quoted,
                emotes = parsed.emotes,
                mentions = parsed.mentions,
              )
          }
          parsed.videoBvids.forEach { output += ArticleBlock.Video(it) }
        }
        2 -> {
          val pictures = paragraph.optJSONObject("pic")?.optJSONArray("pics") ?: JSONArray()
          for (pictureIndex in 0 until pictures.length()) {
            val picture = pictures.optJSONObject(pictureIndex) ?: continue
            val imageUrl =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(picture.optString("url")).orEmpty()
            if (imageUrl.isNotBlank()) {
              output +=
                ArticleBlock.Image(
                  url = imageUrl,
                  width = picture.optInt("width"),
                  height = picture.optInt("height"),
                  caption = picture.optString("comment"),
                )
            }
          }
        }
        3 -> output += ArticleBlock.Divider
        4 -> {
          val children = paragraph.optJSONObject("blockquote")?.optJSONArray("children")
          if (children != null) parseArticleParagraphs(children, output, quoted = true)
        }
        7 -> {
          val code = paragraph.optJSONObject("code") ?: continue
          val content = decodeHtmlText(code.optString("content"))
          if (content.isNotBlank()) {
            output += ArticleBlock.Code(content, code.optString("lang").removePrefix("language-"))
          }
        }
        else -> {
          findArticleBvid(paragraph)?.let { output += ArticleBlock.Video(it) }
          val children =
            paragraph.optJSONObject("list")?.optJSONArray("children")
              ?: paragraph.optJSONObject("heading")?.optJSONArray("children")
          if (children != null) parseArticleParagraphs(children, output, quoted)
        }
      }
    }
  }

  private data class ParsedArticleText(
    val text: String,
    val emotes: Map<String, String>,
    val mentions: List<CommentMention>,
    val videoBvids: List<String>,
  )

  private fun parseArticleTextNodes(nodes: JSONArray): ParsedArticleText {
    val emotes = linkedMapOf<String, String>()
    val mentions = linkedMapOf<Long, CommentMention>()
    val videoBvids = linkedSetOf<String>()
    val text =
      buildString {
          for (nodeIndex in 0 until nodes.length()) {
            val node = nodes.optJSONObject(nodeIndex) ?: continue
            val word = node.optJSONObject("word")
            val rich = node.optJSONObject("rich")
            when {
              word != null -> {
                val value = decodeHtmlText(word.optString("words"))
                append(value)
                if (value.contains("http", ignoreCase = true)) {
                  ARTICLE_BVID_REGEX.find(value)?.value?.let {
                    videoBvids += normalizeArticleBvid(it)
                  }
                }
              }
              rich != null -> {
                val value =
                  decodeHtmlText(rich.optString("text").ifBlank { rich.optString("orig_text") })
                append(value)
                val type = rich.optString("type")
                val jumpUrl = rich.optString("jump_url")
                ARTICLE_BVID_REGEX.find(jumpUrl)?.value?.let {
                  videoBvids += normalizeArticleBvid(it)
                }
                val iconUrl =
                  rich.optString("icon_url").ifBlank {
                    rich.optJSONObject("emoji")?.optString("icon_url").orEmpty()
                  }
                val normalizedIcon =
                  dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(iconUrl).orEmpty()
                if (value.isNotBlank() && normalizedIcon.isNotBlank() && type.contains("EMOJI")) {
                  emotes[value] = normalizedIcon
                }
                if (type.contains("USER")) {
                  val mid =
                    rich.optString("rid").toLongOrNull()
                      ?: rich.optString("id").toLongOrNull()
                      ?: rich.optLong("mid")
                  if (mid > 0L && value.isNotBlank()) {
                    mentions[mid] = CommentMention(mid, value.removePrefix("@"))
                  }
                }
              }
            }
          }
        }
        .trim()
    return ParsedArticleText(text, emotes, mentions.values.toList(), videoBvids.toList())
  }

  private fun findArticleBvid(value: Any?): String? =
    when (value) {
      is String -> ARTICLE_BVID_REGEX.find(value)?.value?.let(::normalizeArticleBvid)
      is JSONObject -> {
        val keys = value.keys()
        var found: String? = null
        while (keys.hasNext() && found == null) found = findArticleBvid(value.opt(keys.next()))
        found
      }
      is JSONArray -> {
        var found: String? = null
        for (index in 0 until value.length()) {
          found = findArticleBvid(value.opt(index))
          if (found != null) break
        }
        found
      }
      else -> null
    }

  private fun normalizeArticleBvid(value: String): String = "BV" + value.drop(2)

  private fun decodeHtmlText(value: String): String {
    val withoutTags = value.replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
    return HTML_ENTITY_REGEX.replace(withoutTags) { match ->
        val decimal = match.groups[1]?.value?.toIntOrNull()
        val hexadecimal = match.groups[2]?.value?.toIntOrNull(16)
        val codePoint = decimal ?: hexadecimal
        when {
          codePoint != null && Character.isValidCodePoint(codePoint) ->
            String(Character.toChars(codePoint))
          else ->
            when (match.groups[3]?.value?.lowercase()) {
              "amp" -> "&"
              "lt" -> "<"
              "gt" -> ">"
              "quot" -> "\""
              "apos" -> "'"
              "nbsp" -> " "
              else -> match.value
            }
        }
      }
      .trim()
  }

  /** Public desktop-search completion endpoint. It intentionally does not use account cookies. */
  fun getSearchSuggestions(keyword: String): List<String> {
    val term = keyword.trim()
    if (term.isEmpty()) return emptyList()
    val url =
      "https://s.search.bilibili.com/main/suggest?term=${URLEncoder.encode(term, "UTF-8")}&main_ver=v1"
    val resp = BiliHttpClient.getPublic(url)
    val body = resp.body?.string().orEmpty()
    resp.close()
    val tags = JSONObject(body).optJSONObject("result")?.optJSONArray("tag") ?: return emptyList()
    return buildList {
        for (index in 0 until tags.length()) {
          tags.optJSONObject(index)?.optString("term")?.takeIf(String::isNotBlank)?.let(::add)
        }
      }
      .distinct()
      .take(10)
  }

  fun searchUsers(keyword: String, page: Int = 1): List<SearchUser> {
    val query =
      signedQuery(
        mapOf(
          "search_type" to "bili_user",
          "keyword" to keyword,
          "page" to page.toString(),
          "order" to "totalrank",
          "order_sort" to "0",
        )
      )
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/wbi/search/type?$query")
    val json = JSONObject(resp.body?.string().orEmpty())
    resp.close()
    if (json.optInt("code") != 0) throw IllegalStateException(json.optString("message"))
    val array = json.optJSONObject("data")?.optJSONArray("result") ?: return emptyList()
    return buildList {
      for (index in 0 until array.length()) {
        val item = array.getJSONObject(index)
        add(
          SearchUser(
            mid = item.optLong("mid"),
            name = Html.fromHtml(item.optString("uname"), Html.FROM_HTML_MODE_LEGACY).toString(),
            face =
              dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("upic")).orEmpty(),
            sign = item.optString("usign"),
            fans = item.optLong("fans"),
            videoCount = item.optInt("videos"),
            level = item.optInt("level"),
            vipActive =
              item.optInt("is_vip", item.optInt("vip")) == 1 ||
                item.optJSONObject("vip")?.optInt("status", 0) == 1,
            vipLabel =
              item.optString("vip_label").ifBlank {
                item.optJSONObject("vip")?.optJSONObject("label")?.optString("text").orEmpty()
              },
          )
        )
      }
    }
  }

  private fun parseClockDuration(value: String): Long {
    val parts = value.split(':').mapNotNull(String::toLongOrNull)
    return when (parts.size) {
      3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
      2 -> parts[0] * 60 + parts[1]
      else -> 0
    }
  }

  internal fun signedQuery(params: Map<String, String>): String =
    signedParams(params).toSortedMap().entries.joinToString("&") { (k, v) ->
      "${URLEncoder.encode(k, "UTF-8")}" + "=${URLEncoder.encode(v, "UTF-8")}"
    }

  // ── Related ───────────────────────────────────────────────────────────────

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

  // ── Login ─────────────────────────────────────────────────────────────────

  fun generateQrCode(): QrCodeInfo? {
    val resp =
      BiliHttpClient.get("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
    val body = resp.body?.string().orEmpty()
    resp.close()
    val json = JSONObject(body)
    Log.d(TAG, "qrcode generate: code=${json.optInt("code")} msg=${json.optString("message")}")
    if (json.optInt("code") != 0) return null
    val data = json.getJSONObject("data")
    return QrCodeInfo(data.getString("url"), data.getString("qrcode_key"))
  }

  fun pollQrCode(qrcodeKey: String): QrStatus {
    val resp =
      BiliHttpClient.get(
        "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$qrcodeKey"
      )
    val body = resp.body?.string().orEmpty()
    resp.close()
    Log.d(TAG, "qrcode poll: bodyLen=${body.length}")
    val json = JSONObject(body)
    val data = json.optJSONObject("data")
    val code = data?.optInt("code", 86038) ?: 86038
    val msg = data?.optString("message", "") ?: ""
    Log.d(TAG, "qrcode poll parsed: code=$code msg=$msg")
    return QrStatus(code, msg)
  }

  fun generateAppQrCode(): QrCodeInfo? {
    val params =
      AppSigner.signedParams(
        AppSigningOperation.TV_QR_GENERATE,
        mapOf(
          "local_id" to "0",
          "ts" to (System.currentTimeMillis() / 1_000L).toString(),
        )
      )
    val response =
      BiliHttpClient.postForm(
        "https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code",
        params,
      )
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    if (json.optInt("code") != 0) return null
    val data = json.optJSONObject("data") ?: return null
    val url = data.optString("url")
    val authCode = data.optString("auth_code")
    return if (url.isBlank() || authCode.isBlank()) null else QrCodeInfo(url, authCode)
  }

  fun pollAppQrCode(authCode: String): AppQrStatus {
    val params =
      AppSigner.signedParams(
        AppSigningOperation.TV_QR_POLL,
        mapOf(
          "auth_code" to authCode,
          "local_id" to "0",
          "ts" to (System.currentTimeMillis() / 1_000L).toString(),
        )
      )
    val response =
      BiliHttpClient.postForm(
        "https://passport.bilibili.com/x/passport-tv-login/qrcode/poll",
        params,
      )
    val json = JSONObject(response.body?.string().orEmpty())
    response.close()
    val data = json.optJSONObject("data")
    return AppQrStatus(
      code = json.optInt("code", 86038),
      message = json.optString("message"),
      mid = data?.optLong("mid", 0L) ?: 0L,
      accessToken = data?.optString("access_token").orEmpty(),
      refreshToken = data?.optString("refresh_token").orEmpty(),
      expiresInSeconds = data?.optLong("expires_in", 0L) ?: 0L,
    )
  }

  fun getUserInfo(): UserInfo {
    val resp = BiliHttpClient.get("https://api.bilibili.com/x/web-interface/nav")
    val body = resp.body?.string().orEmpty()
    resp.close()
    val json = JSONObject(body)
    val data = json.optJSONObject("data")
    val isLogin = data?.optBoolean("isLogin", false) == true
    return if (isLogin) {
      val vip = data.optJSONObject("vip")
      // Depending on the nav gateway, the nested member state is named either `status` or
      // `vipStatus`.  Prefer the top-level state but accept both nested representations.
      val vipStatus =
        data.optInt(
          "vipStatus",
          vip?.optInt("status", vip.optInt("vipStatus", 0)) ?: 0,
        )
      // `2` is a valid active subscription status as well (the signed-in account on the
      // device currently returns vipStatus=2).  A non-zero state is active only when the
      // membership type is also present, which avoids treating an expired type-only record
      // as an active subscription.
      val vipType = data.optInt("vipType", vip?.optInt("type", 0) ?: 0)
      val vipActive = vipStatus != 0 && vipType > 0
      UserInfo(
        mid = data.getLong("mid"),
        name = data.optString("uname", ""),
        face = data.optString("face", ""),
        isLogin = true,
        vipActive = vipActive,
      )
    } else {
      UserInfo(0, "", "", false)
    }
  }

  private data class CachedVideoInfo(val info: VideoInfo, val loadedAtMs: Long)
}

internal fun parsePossiblyEncodedJsonValue(raw: String): Any? {
  var value: Any? = JSONTokener(raw.trim()).nextValue()
  // Some gateway paths JSON-encode the entire normal response one extra time.
  repeat(2) {
    val nested = value as? String ?: return@repeat
    val trimmed = nested.trim()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return@repeat
    value = runCatching { JSONTokener(trimmed).nextValue() }.getOrDefault(value)
  }
  return value
}

internal fun parsePrivateNoticeText(raw: String): String? {
  return runCatching {
      var value: Any? = parsePossiblyEncodedJsonValue(raw)
      repeat(3) {
        when (val current = value) {
          is JSONArray ->
            return@runCatching buildList {
                for (index in 0 until current.length()) {
                  current
                    .optJSONObject(index)
                    ?.optString("text")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
                }
              }
              .joinToString("")
              .ifBlank { null }
          is JSONObject -> {
            val nested = current.opt("content")
            if (nested == null || nested == JSONObject.NULL) return@runCatching null
            value =
              if (nested is String) {
                runCatching { parsePossiblyEncodedJsonValue(nested) }.getOrDefault(nested)
              } else {
                nested
              }
          }
          is String -> {
            val trimmed = current.trim()
            if (!trimmed.startsWith("[")) return@runCatching null
            value = JSONTokener(trimmed).nextValue()
          }
          else -> return@runCatching null
        }
      }
      null
    }
    .getOrNull()
}

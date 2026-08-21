package dev.openbili.webdemo.api

/**
 * 弹幕接口。
 *
 * 覆盖智能防挡 webmask 资源、按分段拉取与合并弹幕（当前段 + 前后段缓存）、历史弹幕
 * 按月回溯、protobuf 与 XML 两种格式解析、去重合并，以及登录态弹幕发送。
 */

import android.os.SystemClock
import android.util.Log
import androidx.core.text.HtmlCompat
import dev.openbili.webdemo.DevicePerformancePolicy
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
 * 弹幕 API 集合。
 */
object BiliDanmakuApi {
  private const val DANMAKU_SEGMENT_SECONDS = 6 * 60L
  private const val MAX_DANMAKU_SEGMENTS = 120
  private const val MAX_DANMAKU_MAP_CAPACITY = 100_000
  private const val MAX_DANMAKU_MASK_BYTES = 32 * 1024 * 1024L
  private const val DEFAULT_DANMAKU_CACHE_ENTRIES = 16
  private const val CONSTRAINED_DANMAKU_CACHE_ENTRIES = 4
  private const val DEFAULT_DANMAKU_SEGMENT_CACHE_ENTRIES = 48
  private const val CONSTRAINED_DANMAKU_SEGMENT_CACHE_ENTRIES = 8
  private val danmakuLock = Any()
  private val danmakuCache =
    LinkedHashMap<DanmakuCacheKey, List<DanmakuItem>>(DEFAULT_DANMAKU_CACHE_ENTRIES, .75f, true)
  private val danmakuSegmentCache =
    LinkedHashMap<DanmakuSegmentCacheKey, List<DanmakuItem>>(
      DEFAULT_DANMAKU_SEGMENT_CACHE_ENTRIES,
      .75f,
      true,
    )
  private val danmakuSegmentRequests =
    ConcurrentHashMap<DanmakuSegmentCacheKey, BlockingRequest<List<DanmakuItem>>>()

  // ── 弹幕 ───────────────────────────────────────────────────────────────

  /** 拉取智能防挡 webmask 资源（人物轮廓蒙版）。 */
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
    Log.d(BiliApiCommon.TAG, "danmaku mask loaded: cid=$cid fps=${mask.optInt("fps")} bytes=${bytes.size}")
    return DanmakuMaskResource(
      fps = mask.optInt("fps").coerceAtLeast(0),
      bytes = bytes,
    )
  }

  /** 拉取视频弹幕：按分段请求并只常驻当前段及前后各一段。 */
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
        Log.d(BiliApiCommon.TAG, "danmaku cache hit: cid=$cid history=$includeHistory items=${it.size}")
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
      while (danmakuCache.size > maxDanmakuCacheEntries()) {
        danmakuCache.remove(danmakuCache.keys.first())
      }
      Log.d(BiliApiCommon.TAG, "danmaku loaded: cid=$cid history=$includeHistory items=${loaded.size}")
      loaded
    }

  /** 拉取单个弹幕分段。 */
  internal fun getDanmakuSegment(cid: Long, segmentIndex: Int): List<DanmakuItem> {
    require(cid > 0L) { "Invalid danmaku cid" }
    require(segmentIndex in 1..MAX_DANMAKU_SEGMENTS) { "Invalid danmaku segment index" }
    val key = DanmakuSegmentCacheKey(cid, segmentIndex)
    synchronized(danmakuLock) {
      danmakuSegmentCache[key]?.let {
        return it
      }
    }

    val request = BlockingRequest<List<DanmakuItem>>()
    danmakuSegmentRequests.putIfAbsent(key, request)?.let {
      return it.await()
    }
    try {
      val url =
        "https://api.bilibili.com/x/v2/dm/web/seg.so" +
          "?type=1&oid=$cid&segment_index=$segmentIndex"
      val result = fetchDanmakuProtobuf(url, cid, "segment=$segmentIndex")
      val loaded =
        if (result.httpCode in 200..299) {
          result.items
        } else if (segmentIndex == 1) {
          val legacy = fetchDanmakuXml("https://api.bilibili.com/x/v1/dm/list.so?oid=$cid", cid)
          legacy.items.filter { it.timeMs in 0 until DANMAKU_SEGMENT_SECONDS * 1_000L }
        } else {
          emptyList()
        }
      synchronized(danmakuLock) {
        danmakuSegmentCache[key] = loaded
        while (danmakuSegmentCache.size > maxDanmakuSegmentCacheEntries()) {
          danmakuSegmentCache.remove(danmakuSegmentCache.keys.first())
        }
      }
      request.complete(loaded)
      return loaded
    } catch (error: Throwable) {
      request.completeExceptionally(error)
      throw error
    } finally {
      danmakuSegmentRequests.remove(key, request)
    }
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
      BiliApiCommon.TAG,
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
          Log.w(BiliApiCommon.TAG, "danmaku segmented request failed: cid=$cid http=${result.httpCode}")
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
      Log.d(BiliApiCommon.TAG, "skip full danmaku history without SESSDATA: cid=$cid")
      return current
    }
    if (expectedCount > 0L && current.size.toLong() >= expectedCount) return current

    val zone = ZoneId.of("Asia/Shanghai")
    val today = LocalDate.now(zone)
    val publishedDate =
      publishedAt.takeIf { it > 0L }?.let { Instant.ofEpochSecond(it).atZone(zone).toLocalDate() }
        ?: today.minusMonths(BiliHistoryApi.DEFAULT_HISTORY_LOOKBACK_MONTHS)
    val firstMonth = YearMonth.from(publishedDate)
    val lastMonth = YearMonth.from(today)
    val months = mutableListOf<YearMonth>()
    var month = lastMonth
    while (month >= firstMonth && months.size < BiliHistoryApi.MAX_HISTORY_MONTH_REQUESTS) {
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
        .take(BiliHistoryApi.MAX_HISTORY_DAY_REQUESTS)
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
        if (emptyResponses >= BiliHistoryApi.MAX_CONSECUTIVE_EMPTY_HISTORY_RESPONSES) {
          Log.w(BiliApiCommon.TAG, "stop danmaku history after repeated empty responses: cid=$cid")
          break
        }
        continue
      }
      emptyResponses = 0
      fetchedDays += 1
      result.items.forEach { merged.putIfAbsent(it.identity(), it) }
      if (expectedCount <= 0L || merged.size.toLong() < expectedCount) {
        SystemClock.sleep(BiliHistoryApi.HISTORY_REQUEST_INTERVAL_MS)
      }
    }
    val loaded = merged.values.sortedBy(DanmakuItem::timeMs)
    Log.d(
      BiliApiCommon.TAG,
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
          Log.w(BiliApiCommon.TAG, "danmaku history index failed: cid=$cid month=$month http=$responseCode")
          return@runCatching emptyList()
        }
        val json = JSONObject(body)
        if (json.optInt("code") != 0) {
          Log.w(
            BiliApiCommon.TAG,
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
        Log.w(BiliApiCommon.TAG, "danmaku history index error: cid=$cid month=$month", it)
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
              BiliApiCommon.TAG,
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
      BiliApiCommon.TAG,
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
          Log.w(BiliApiCommon.TAG, "danmaku decode failed: cid=$cid http=$responseCode", it)
          ""
        }
    val items = parseDanmakuXml(body)
    Log.d(
      BiliApiCommon.TAG,
      "danmaku response: cid=$cid http=$responseCode type=$responseType " +
        "encoding=$responseEncoding bodyLen=${body.length} items=${items.size}",
    )
    return DanmakuFetchResult(responseCode, items)
  }

  /** 解析 XML 格式弹幕段。 */
  internal fun parseDanmakuXml(body: String): List<DanmakuItem> {
    val items = mutableListOf<DanmakuItem>()
    // 老接口偶有消息内容里出现裸 & 符号，导致整份文档是非法 XML：逐条解析独立的
    // <d> 记录，避免单个坏实体把整个响应的弹幕全部丢弃。
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
              content = BiliArticleApi.decodePlatformHtmlText(match.groupValues[2]),
              sourceId = parts.getOrNull(7)?.takeIf(String::isNotBlank),
            )
        }
        .onFailure { Log.w(BiliApiCommon.TAG, "skip malformed danmaku record", it) }
    }
    return items
  }

  private data class DanmakuFetchResult(val httpCode: Int, val items: List<DanmakuItem>)

  private data class DanmakuBinaryFetchResult(
    val httpCode: Int,
    val items: List<DanmakuItem>,
  )

  private data class DanmakuCacheKey(val cid: Long, val includeHistory: Boolean)

  private data class DanmakuSegmentCacheKey(val cid: Long, val segmentIndex: Int)

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

  private fun maxDanmakuCacheEntries(): Int =
    if (DevicePerformancePolicy.isConstrainedImageMode) CONSTRAINED_DANMAKU_CACHE_ENTRIES
    else DEFAULT_DANMAKU_CACHE_ENTRIES

  private fun maxDanmakuSegmentCacheEntries(): Int =
    if (DevicePerformancePolicy.isConstrainedImageMode) CONSTRAINED_DANMAKU_SEGMENT_CACHE_ENTRIES
    else DEFAULT_DANMAKU_SEGMENT_CACHE_ENTRIES

  private fun inflate(bytes: ByteArray): String {
    fun decode(inflater: Inflater) =
      InflaterInputStream(ByteArrayInputStream(bytes), inflater)
        .use { it.readBytes() }
        .toString(Charsets.UTF_8)
    return runCatching { decode(Inflater()) }.getOrElse { decode(Inflater(true)) }
  }

  /** 登录态发送弹幕（需已登录）。 */
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
    val csrf = BiliApiCommon.requireCsrf()
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
}

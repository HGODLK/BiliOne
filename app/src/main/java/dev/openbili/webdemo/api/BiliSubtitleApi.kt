package dev.openbili.webdemo.api

import java.net.URI
import java.util.Locale
import kotlin.math.roundToLong
import org.json.JSONObject

data class VideoSubtitleTrack(
  val id: String,
  val language: String,
  val languageLabel: String,
  val sourceUrl: String,
  val type: Int,
  val aiType: Int,
  val aiStatus: Int,
  val aid: Long,
  val cid: Long,
  val bvid: String,
) {
  val isAiGenerated: Boolean
    get() = type == 1

  val isAiTranslation: Boolean
    get() = aiType == 1

  val displayLabel: String
    get() =
      when {
        isAiTranslation -> "$languageLabel · AI 翻译"
        isAiGenerated -> "$languageLabel · AI 字幕"
        else -> languageLabel
      }
}

data class VideoSubtitleCatalog(
  val tracks: List<VideoSubtitleTrack>,
  val loginRequired: Boolean,
  val aid: Long = 0L,
  val cid: Long = 0L,
  val bvid: String = "",
)

data class VideoSubtitleCue(
  val startMs: Long,
  val endMs: Long,
  val content: String,
)

/** Bilibili's current Web subtitle protocol and conversion into Media3-compatible WebVTT. */
object BiliSubtitleApi {
  private const val MAX_SUBTITLE_CATALOG_BYTES = 512 * 1024L
  private const val MAX_SUBTITLE_DOCUMENT_BYTES = 2 * 1024 * 1024L
  private const val SUBTITLE_VIEW_ENDPOINT = "https://api.bilibili.com/x/v2/subtitle/web/view"
  private const val ENCRYPTED_SUBTITLE_HOST = "subtitle.bilibili.com"
  private const val SUBTITLE_FILE_HOST = "aisubtitle.hdslb.com"
  private const val AI_SUBTITLE_PATH_PREFIX = "/bfs/ai_subtitle/prod/"

  fun getCatalog(aid: Long, cid: Long, bvid: String): VideoSubtitleCatalog {
    require(aid > 0L && cid > 0L) { "视频参数无效" }
    val normalizedBvid = bvid.takeIf { it.startsWith("BV", ignoreCase = true) }.orEmpty()
    val requestUrl =
      "$SUBTITLE_VIEW_ENDPOINT?oid=$cid&pid=$aid" +
        "&context_ext=%7B%22video_type%22%3A1%7D" +
        "&type=1&cur_production_type=0&preferred_language=ai-zh&playlist_switch=0"
    val response =
      BiliHttpClient.get(
        requestUrl,
        headers =
          mapOf(
            "Accept" to "application/octet-stream",
            "Accept-Encoding" to "identity",
            "Cache-Control" to "no-cache",
            "Origin" to "https://www.bilibili.com",
            "Referer" to
              normalizedBvid
                .takeIf(String::isNotBlank)
                ?.let { "https://www.bilibili.com/video/$it/" }
                .orEmpty()
                .ifBlank { "https://www.bilibili.com/" },
          ),
      )
    val httpCode = response.code
    val contentLength = response.body?.contentLength() ?: -1L
    if (httpCode !in 200..299 || contentLength > MAX_SUBTITLE_CATALOG_BYTES) {
      response.close()
      throw IllegalStateException("字幕接口请求失败（$httpCode）")
    }
    val bytes = response.body?.bytes() ?: ByteArray(0)
    response.close()
    if (bytes.size > MAX_SUBTITLE_CATALOG_BYTES) {
      throw IllegalStateException("字幕目录过大")
    }
    parseJsonError(bytes)?.let { throw IllegalStateException(it) }
    return parseCatalog(bytes, aid = aid, cid = cid, bvid = normalizedBvid)
  }

  fun getDocument(
    track: VideoSubtitleTrack,
    bvid: String,
    aid: Long,
    cid: Long,
  ): List<VideoSubtitleCue> {
    check(track.aid == aid && track.cid == cid && track.bvid.equals(bvid, ignoreCase = true)) {
      "字幕文件身份不匹配"
    }
    check(isTrustedSubtitleSource(track.sourceUrl)) { "字幕文件来源无效" }
    check(isSubtitleSourceBoundToMedia(track.sourceUrl, track.type, aid, cid)) {
      "字幕文件与视频身份不匹配"
    }
    val response =
      BiliHttpClient.getPublic(
        track.sourceUrl,
        headers =
          mapOf(
            "Accept" to "application/json",
            "Accept-Encoding" to "identity",
            "Cache-Control" to "no-cache",
            "Referer" to
              bvid
                .takeIf { it.startsWith("BV", ignoreCase = true) }
                ?.let { "https://www.bilibili.com/video/$it/" }
                .orEmpty()
                .ifBlank { "https://www.bilibili.com/" },
          ),
      )
    val contentLength = response.body?.contentLength() ?: -1L
    if (response.code !in 200..299 || contentLength > MAX_SUBTITLE_DOCUMENT_BYTES) {
      val httpCode = response.code
      response.close()
      throw IllegalStateException("字幕文件下载失败（$httpCode）")
    }
    val body = response.body?.string().orEmpty()
    response.close()
    if (body.toByteArray(Charsets.UTF_8).size > MAX_SUBTITLE_DOCUMENT_BYTES) {
      throw IllegalStateException("字幕文件过大")
    }
    return parseDocument(JSONObject(body))
  }

  internal fun parseCatalog(
    bytes: ByteArray,
    aid: Long,
    cid: Long,
    bvid: String,
  ): VideoSubtitleCatalog {
    require(aid > 0L && cid > 0L) { "视频参数无效" }
    val tracks =
      SubtitleViewProtoParser.parse(bytes)
        .mapNotNull { rawTrack ->
          val sourceUrl = resolveSubtitleUrl(rawTrack.sourceUrl) ?: return@mapNotNull null
          if (!isTrustedSubtitleSource(sourceUrl)) return@mapNotNull null
          if (!isSubtitleSourceBoundToMedia(sourceUrl, rawTrack.type, aid, cid)) {
            return@mapNotNull null
          }
          val id =
            rawTrack.idString.ifBlank { rawTrack.id.takeIf { it > 0L }?.toString().orEmpty() }
          if (id.isBlank()) return@mapNotNull null
          val language = rawTrack.language.ifBlank { "und" }
          VideoSubtitleTrack(
            id = id,
            language = language,
            languageLabel = rawTrack.languageLabel.ifBlank { language },
            sourceUrl = sourceUrl,
            type = rawTrack.type,
            aiType = rawTrack.aiType,
            aiStatus = rawTrack.aiStatus,
            aid = aid,
            cid = cid,
            bvid = bvid,
          )
        }
        .distinctBy(VideoSubtitleTrack::id)
    return VideoSubtitleCatalog(
      tracks = tracks,
      loginRequired = false,
      aid = aid,
      cid = cid,
      bvid = bvid,
    )
  }

  internal fun parseDocument(json: JSONObject): List<VideoSubtitleCue> {
    val rows = json.optJSONArray("body") ?: return emptyList()
    return buildList {
      for (index in 0 until rows.length()) {
        val row = rows.optJSONObject(index) ?: continue
        val startSeconds = row.optDouble("from", Double.NaN)
        val endSeconds = row.optDouble("to", Double.NaN)
        val content = row.optString("content").trim()
        if (!startSeconds.isFinite() || !endSeconds.isFinite() || content.isBlank()) continue
        val startMs = (startSeconds * 1_000.0).roundToLong().coerceAtLeast(0L)
        val endMs = (endSeconds * 1_000.0).roundToLong()
        if (endMs <= startMs) continue
        add(VideoSubtitleCue(startMs = startMs, endMs = endMs, content = content))
      }
    }
  }

  internal fun toWebVtt(cues: List<VideoSubtitleCue>): String = buildString {
    append("WEBVTT\n\n")
    cues.forEach { cue ->
      append(formatWebVttTime(cue.startMs))
        .append(" --> ")
        .append(formatWebVttTime(cue.endMs))
        .append('\n')
      append(escapeWebVttText(cue.content)).append("\n\n")
    }
  }

  private fun parseJsonError(bytes: ByteArray): String? {
    val text = bytes.toString(Charsets.UTF_8).trimStart()
    if (!text.startsWith('{')) return null
    return runCatching {
        val json = JSONObject(text)
        json.optString("message", "字幕接口返回异常").ifBlank { "字幕接口返回异常" }
      }
      .getOrDefault("字幕接口返回异常")
  }

  private fun resolveSubtitleUrl(rawUrl: String): String? {
    val normalized =
      when {
        rawUrl.startsWith("//") -> "https:$rawUrl"
        rawUrl.startsWith("https://") -> rawUrl
        rawUrl.startsWith("http://") -> rawUrl.replaceFirst("http://", "https://")
        else -> return null
      }
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    return when (uri.host?.lowercase(Locale.US)) {
      SUBTITLE_FILE_HOST -> normalized
      ENCRYPTED_SUBTITLE_HOST -> decodeEncryptedSubtitleUrl(uri)
      else -> null
    }
  }

  private fun decodeEncryptedSubtitleUrl(uri: URI): String? {
    val encryptedPath = uri.rawPath?.removePrefix("/").orEmpty()
    val query = uri.rawQuery.orEmpty()
    if (encryptedPath.isBlank() || query.isBlank()) return null
    val encrypted = percentDecodeCipherText(encryptedPath) ?: return null
    val decodedPath =
      SUBTITLE_URL_CIPHERS.firstNotNullOfOrNull { cipher ->
        val decoded = xorCipherText(encrypted, cipher.key + "bilibili")
        decoded.takeIf { it.startsWith(cipher.prefix) }?.removePrefix(cipher.prefix)
      } ?: return null
    if (!decodedPath.startsWith('/') || decodedPath.contains("..")) return null
    return "https://$SUBTITLE_FILE_HOST$decodedPath?$query"
  }

  private fun percentDecodeCipherText(value: String): String? =
    runCatching {
        buildString(value.length) {
          var index = 0
          while (index < value.length) {
            if (value[index] == '%' && index + 2 < value.length) {
              val high = value[index + 1].digitToIntOrNull(16)
              val low = value[index + 2].digitToIntOrNull(16)
              if (high != null && low != null) {
                append(((high shl 4) or low).toChar())
                index += 3
                continue
              }
            }
            append(value[index])
            index += 1
          }
        }
      }
      .getOrNull()

  private fun xorCipherText(value: String, key: String): String =
    buildString(value.length) {
      value.indices.forEach { index ->
        append((value[index].code xor key[index % key.length].code).toChar())
      }
    }

  private fun isTrustedSubtitleSource(sourceUrl: String): Boolean {
    val uri = runCatching { URI(sourceUrl) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
      uri.host.equals(SUBTITLE_FILE_HOST, ignoreCase = true) &&
      uri.rawPath.orEmpty().startsWith("/bfs/") &&
      !uri.rawPath.orEmpty().contains("..") &&
      uri.rawQuery.orEmpty().contains("auth_key=")
  }

  private fun isSubtitleSourceBoundToMedia(
    sourceUrl: String,
    type: Int,
    aid: Long,
    cid: Long,
  ): Boolean {
    if (type != 1) return true
    val path = runCatching { URI(sourceUrl).path.orEmpty() }.getOrDefault("")
    if (!path.startsWith(AI_SUBTITLE_PATH_PREFIX)) return true
    return path.removePrefix(AI_SUBTITLE_PATH_PREFIX).startsWith("$aid$cid")
  }

  private fun formatWebVttTime(timeMs: Long): String {
    val safeTime = timeMs.coerceAtLeast(0L)
    val hours = safeTime / 3_600_000L
    val minutes = (safeTime / 60_000L) % 60L
    val seconds = (safeTime / 1_000L) % 60L
    val milliseconds = safeTime % 1_000L
    return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds)
  }

  private fun escapeWebVttText(value: String): String =
    value
      .replace("\r\n", "\n")
      .replace('\r', '\n')
      .filter { it == '\n' || it == '\t' || it.code >= 0x20 }
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")

  private data class SubtitleUrlCipher(val prefix: String, val key: String)

  private val SUBTITLE_URL_CIPHERS =
    listOf(
      SubtitleUrlCipher(
        prefix = "nP](wOFRvU.+<fjS{jn-!\$D|Dz&\",zT`",
        key = "=CFxYRn{.y|uVyO\$uh&sikph?N.ilF/`",
      ),
      SubtitleUrlCipher(
        prefix = "Bn\"q~|albg@]Go~ACgyDvKnd+)_D}^&J?",
        key = "Cu~L!xs~f^&r@'vh=q]q{eeng*sEg^kp#J",
      ),
    )
}

private object SubtitleViewProtoParser {
  fun parse(bytes: ByteArray): List<RawSubtitleTrack> {
    if (bytes.isEmpty()) return emptyList()
    val reader = ProtoReader(bytes)
    return buildList {
      while (reader.hasRemaining()) {
        val tag = reader.readVarint().toInt()
        val field = tag ushr 3
        val wireType = tag and 0x07
        if (field == SUBTITLE_REPLY_FIELD && wireType == WIRE_LENGTH_DELIMITED) {
          addAll(parseVideoSubtitle(reader.readSubReader()))
        } else {
          reader.skip(wireType)
        }
      }
    }
  }

  private fun parseVideoSubtitle(reader: ProtoReader): List<RawSubtitleTrack> = buildList {
    while (reader.hasRemaining()) {
      val tag = reader.readVarint().toInt()
      val field = tag ushr 3
      val wireType = tag and 0x07
      if (field == SUBTITLE_ITEMS_FIELD && wireType == WIRE_LENGTH_DELIMITED) {
        parseTrack(reader.readSubReader())?.let(::add)
      } else {
        reader.skip(wireType)
      }
    }
  }

  private fun parseTrack(reader: ProtoReader): RawSubtitleTrack? {
    var id = 0L
    var idString = ""
    var language = ""
    var languageLabel = ""
    var sourceUrl = ""
    var type = 0
    var aiType = 0
    var aiStatus = 0
    while (reader.hasRemaining()) {
      val tag = reader.readVarint().toInt()
      val field = tag ushr 3
      val wireType = tag and 0x07
      when {
        field == TRACK_ID_FIELD && wireType == WIRE_VARINT -> id = reader.readVarint()
        field == TRACK_ID_STRING_FIELD && wireType == WIRE_LENGTH_DELIMITED ->
          idString = reader.readString()
        field == TRACK_LANGUAGE_FIELD && wireType == WIRE_LENGTH_DELIMITED ->
          language = reader.readString()
        field == TRACK_LANGUAGE_LABEL_FIELD && wireType == WIRE_LENGTH_DELIMITED ->
          languageLabel = reader.readString()
        field == TRACK_SOURCE_URL_FIELD && wireType == WIRE_LENGTH_DELIMITED ->
          sourceUrl = reader.readString()
        field == TRACK_TYPE_FIELD && wireType == WIRE_VARINT -> type = reader.readVarint().toInt()
        field == TRACK_AI_TYPE_FIELD && wireType == WIRE_VARINT ->
          aiType = reader.readVarint().toInt()
        field == TRACK_AI_STATUS_FIELD && wireType == WIRE_VARINT ->
          aiStatus = reader.readVarint().toInt()
        else -> reader.skip(wireType)
      }
    }
    if (sourceUrl.isBlank()) return null
    return RawSubtitleTrack(
      id = id,
      idString = idString,
      language = language,
      languageLabel = languageLabel,
      sourceUrl = sourceUrl,
      type = type,
      aiType = aiType,
      aiStatus = aiStatus,
    )
  }

  private class ProtoReader(
    private val bytes: ByteArray,
    private var position: Int = 0,
    private val limit: Int = bytes.size,
  ) {
    fun hasRemaining(): Boolean = position < limit

    fun readVarint(): Long {
      var result = 0L
      var shift = 0
      while (shift < Long.SIZE_BITS) {
        require(position < limit) { "Truncated subtitle protobuf varint" }
        val value = bytes[position++].toInt() and 0xFF
        result = result or ((value and 0x7F).toLong() shl shift)
        if (value and 0x80 == 0) return result
        shift += 7
      }
      throw IllegalArgumentException("Malformed subtitle protobuf varint")
    }

    fun readSubReader(): ProtoReader {
      val length = readLength()
      val end = position + length
      require(end in position..limit) { "Truncated subtitle protobuf message" }
      return ProtoReader(bytes, position, end).also { position = end }
    }

    fun readString(): String {
      val length = readLength()
      val end = position + length
      require(end in position..limit) { "Truncated subtitle protobuf string" }
      return String(bytes, position, length, Charsets.UTF_8).also { position = end }
    }

    fun skip(wireType: Int) {
      when (wireType) {
        WIRE_VARINT -> readVarint()
        WIRE_FIXED_64 -> skipBytes(Long.SIZE_BYTES)
        WIRE_LENGTH_DELIMITED -> skipBytes(readLength())
        WIRE_FIXED_32 -> skipBytes(Int.SIZE_BYTES)
        else -> throw IllegalArgumentException("Unsupported subtitle protobuf wire type: $wireType")
      }
    }

    private fun readLength(): Int {
      val value = readVarint()
      require(value in 0..Int.MAX_VALUE.toLong()) { "Invalid subtitle protobuf length" }
      return value.toInt()
    }

    private fun skipBytes(count: Int) {
      require(count >= 0 && position + count in position..limit) {
        "Truncated subtitle protobuf field"
      }
      position += count
    }
  }

  private const val SUBTITLE_REPLY_FIELD = 1
  private const val SUBTITLE_ITEMS_FIELD = 3
  private const val TRACK_ID_FIELD = 1
  private const val TRACK_ID_STRING_FIELD = 2
  private const val TRACK_LANGUAGE_FIELD = 3
  private const val TRACK_LANGUAGE_LABEL_FIELD = 4
  private const val TRACK_SOURCE_URL_FIELD = 5
  private const val TRACK_TYPE_FIELD = 7
  private const val TRACK_AI_TYPE_FIELD = 9
  private const val TRACK_AI_STATUS_FIELD = 10
  private const val WIRE_VARINT = 0
  private const val WIRE_FIXED_64 = 1
  private const val WIRE_LENGTH_DELIMITED = 2
  private const val WIRE_FIXED_32 = 5
}

private data class RawSubtitleTrack(
  val id: Long,
  val idString: String,
  val language: String,
  val languageLabel: String,
  val sourceUrl: String,
  val type: Int,
  val aiType: Int,
  val aiStatus: Int,
)

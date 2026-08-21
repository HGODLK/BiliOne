package dev.openbili.webdemo.api

/**
 * 专栏接口：抓取并解析专栏正文（HTML 与内嵌 JSON 两条路径）、文章阅读上报。
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
 * 专栏 API 集合与正文解析器。
 */
object BiliArticleApi {
  private val HTML_ENTITY_REGEX = Regex("&(?:#(\\d+)|#x([0-9a-fA-F]+)|([A-Za-z]+));")
  private val ARTICLE_BVID_REGEX = Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE)
  private val ARTICLE_OPUS_ID_REGEX = Regex("/opus/(\\d+)")

  /** 抓取并解析专栏详情正文。 */
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

    // Opus 在首次请求时偶尔会返回一个临时的引导/风控文档。使用账号 cookie jar、保留
    // 重定向目标，并在断定文章格式本身不受支持之前重试这个小 HTML 引导。
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

    // JSON 端点和页面引导暴露同一个 `detail` 模型。当 HTML 外壳返回时没有
    // __INITIAL_STATE__，它是一个有用的回退。
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

    Log.w(BiliApiCommon.TAG, "article detail unavailable: cv${article.id}", lastFailure)
    throw IllegalStateException("专栏正文暂时没加载出来，请稍后重试")
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

  /** 上报专栏阅读。 */
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
      .onFailure { Log.w(BiliApiCommon.TAG, "article history report failed: aid=$articleId", it) }
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

  internal fun decodeHtmlText(value: String): String {
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
      }.trim()
  }

  internal fun decodePlatformHtmlText(value: String): String =
    HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
}
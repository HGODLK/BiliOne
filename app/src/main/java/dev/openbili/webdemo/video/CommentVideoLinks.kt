package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedViewModel

private val commentUrlPattern =
  Regex("(?:https?|bilibili)://[^\\s<>，。！？；：）】》”]+", RegexOption.IGNORE_CASE)
private val bvidPattern = Regex("^BV[0-9A-Za-z]{10}$", RegexOption.IGNORE_CASE)
private val avidPattern = Regex("^av(\\d+)$", RegexOption.IGNORE_CASE)
private val trailingUrlPunctuation =
  setOf(
    ',',
    '.',
    '!',
    '?',
    ';',
    ':',
    ')',
    ']',
    '}',
    '，',
    '。',
    '！',
    '？',
    '；',
    '：',
    '）',
    '】',
    '》',
    '”',
    '\'',
  )

internal data class CommentVideoLink(val rawUrl: String, val bvid: String)
internal data class CommentArticleLink(
  val rawUrl: String,
  val articleId: Long,
  val sourceUrl: String,
)

internal data class ParsedCommentVideoLinks(
  val originalText: String,
  val links: List<CommentVideoLink>,
  val articleLinks: List<CommentArticleLink> = emptyList(),
) {
  fun textWithMappedLinksRemoved(
    mappedBvids: Set<String>,
    mappedArticleIds: Set<Long> = emptySet(),
  ): String {
    if (mappedBvids.isEmpty() && mappedArticleIds.isEmpty()) return originalText
    val videosRemoved =
      links.fold(originalText) { text, link ->
        if (link.bvid in mappedBvids) text.replace(link.rawUrl, "") else text
      }
    val removed =
      articleLinks.fold(videosRemoved) { text, link ->
        if (link.articleId in mappedArticleIds) text.replace(link.rawUrl, "") else text
      }
    return removed
      .replace(Regex("[ \\t]+(?=\\r?\\n)"), "")
      .replace(Regex("(?:\\r?\\n){3,}"), "\n\n")
      .trim()
  }
}

internal fun parseCommentVideoLinks(text: String): ParsedCommentVideoLinks {
  val videoLinks = mutableListOf<CommentVideoLink>()
  val articleLinks = mutableListOf<CommentArticleLink>()
  commentUrlPattern.findAll(text).forEach { match ->
        val url = match.value.trimEnd { it in trailingUrlPunctuation }
        val withoutScheme = url.substringAfter("://", missingDelimiterValue = "")
        if (withoutScheme.isBlank()) return@forEach
        val host = withoutScheme.substringBefore('/').substringBefore(':').lowercase()
        val path = withoutScheme.substringAfter('/', missingDelimiterValue = "")
        val candidate =
          when {
            host == "b23.tv" || host == "www.b23.tv" -> path.substringBeforeAny('/', '?', '#')
            host == "bilibili.com" || host == "www.bilibili.com" -> {
              val segments = path.split('/', limit = 3)
              if (segments.firstOrNull().equals("video", ignoreCase = true)) {
                segments.getOrNull(1)?.substringBeforeAny('?', '#').orEmpty()
              } else {
                ""
              }
            }
            host == "video" ->
              path.substringBeforeAny('/', '?', '#').let {
                if (it.startsWith("BV", ignoreCase = true)) it else "av$it"
              }
            else -> ""
          }
        candidate.takeIf(bvidPattern::matches)?.let {
          videoLinks += CommentVideoLink(rawUrl = url, bvid = normalizeBvid(it))
          return@forEach
        }
        avidPattern.matchEntire(candidate)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
          videoLinks += CommentVideoLink(rawUrl = url, bvid = "av$it")
          return@forEach
        }
        if (host == "bilibili.com" || host == "www.bilibili.com") {
          val articleId =
            Regex("(?:^|/)read/cv(\\d+)", RegexOption.IGNORE_CASE)
              .find(path)
              ?.groupValues
              ?.getOrNull(1)
              ?.toLongOrNull()
              ?: Regex("(?:^|/)opus/(\\d+)", RegexOption.IGNORE_CASE)
                .find(path)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
          if (articleId != null && articleId > 0L) {
            articleLinks += CommentArticleLink(url, articleId, url)
          }
        } else if (host == "article") {
          path.substringBeforeAny('/', '?', '#').toLongOrNull()?.takeIf { it > 0L }?.let {
            articleLinks +=
              CommentArticleLink(
                rawUrl = url,
                articleId = it,
                sourceUrl = "https://www.bilibili.com/read/cv$it",
              )
          }
        }
      }
  return ParsedCommentVideoLinks(text, videoLinks, articleLinks)
}

private fun String.substringBeforeAny(vararg delimiters: Char): String {
  val first = delimiters.map(::indexOf).filter { it >= 0 }.minOrNull() ?: length
  return substring(0, first)
}

private fun normalizeBvid(value: String): String = "BV" + value.drop(2)

internal fun VideoInfo.toCommentVideoFeedItem(): FeedItem =
  FeedItem(
    id = bvid,
    title = title,
    videoUrl = "https://www.bilibili.com/video/$bvid",
    coverUrl = coverUrl,
    uploader = uploaderName,
    playCount = FeedViewModel.formatCount(playCount),
    duration = FeedViewModel.formatDuration(durationSeconds),
    uploaderFace = uploaderFace,
    uploaderMid = uploaderMid,
    danmakuCount = danmakuCount,
    publishedAt = publishedAt,
    description = desc,
  )

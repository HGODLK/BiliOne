package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.CommentJumpLink
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedViewModel

private val commentUrlPattern =
  Regex("(?:(?:https?|bilibili):)?//[^\\s<>，。！？；：）】》”\\\"]+", RegexOption.IGNORE_CASE)
private val bvidPattern = Regex("^BV1[1-9A-NP-Za-km-z]{9}$", RegexOption.IGNORE_CASE)
private val rawBvidPattern =
  Regex("(?<![0-9A-Za-z])BV1[1-9A-NP-Za-km-z]{9}(?![0-9A-Za-z])", RegexOption.IGNORE_CASE)
private val rawAvidPattern =
  Regex("(?<![0-9A-Za-z])av\\d+(?![0-9A-Za-z])", RegexOption.IGNORE_CASE)
private val avidPattern = Regex("^av(\\d+)$", RegexOption.IGNORE_CASE)
private val timestampPattern =
  Regex("(?<!\\d)(?:(\\d+)#)?(\\d+(?::|：)){1,2}\\d{2}")
private val trailingUrlPunctuation =
  setOf(
    ',', '.', '!', '?', ';', ':', ')', ']', '}', '，', '。', '！', '？', '；', '：', '）', '】', '》', '”', '"', '\'',
  )

/** 根据评论中的链接类型决定长按复制内容。 */
internal fun commentCopyPayload(text: String): String {
  val parsed = parseCommentVideoLinks(text)
  parsed.articleLinks.firstOrNull()?.rawUrl?.let { return it }
  parsed.links.firstOrNull()?.let { link ->
    return "https://www.bilibili.com/video/${link.bvid}"
  }
  commentUrlPattern.find(text)?.value?.trimEnd { it in trailingUrlPunctuation }?.let { return it }
  return text
}

internal data class CommentVideoLink(
  val rawUrl: String,
  val bvid: String,
  val startIndex: Int = 0,
  val endIndex: Int = startIndex + rawUrl.length,
)

internal data class CommentArticleLink(
  val rawUrl: String,
  val articleId: Long,
  val sourceUrl: String,
  val startIndex: Int = 0,
  val endIndex: Int = startIndex + rawUrl.length,
)

internal data class PendingCommentVideoLink(
  val rawUrl: String,
  val targetUrl: String,
  val startIndex: Int,
  val endIndex: Int,
)

enum class CommentMediaKind { VIDEO, ARTICLE }

internal data class CommentMediaReference(
  val kind: CommentMediaKind,
  val sourceKey: String,
  val startIndex: Int,
  val endIndex: Int,
)

internal data class CommentTimestamp(
  val rawText: String,
  val timeSeconds: Long,
  val videoPart: Int = -1,
  val startIndex: Int,
  val endIndex: Int,
)

internal data class ParsedCommentVideoLinks(
  val originalText: String,
  val links: List<CommentVideoLink>,
  val articleLinks: List<CommentArticleLink> = emptyList(),
  val pendingVideoLinks: List<PendingCommentVideoLink> = emptyList(),
) {
  val orderedReferences: List<CommentMediaReference>
    get() =
      buildList {
        links.forEach {
          add(CommentMediaReference(CommentMediaKind.VIDEO, it.bvid, it.startIndex, it.endIndex))
        }
        articleLinks.forEach {
          add(
            CommentMediaReference(
              CommentMediaKind.ARTICLE,
              it.articleId.toString(),
              it.startIndex,
              it.endIndex,
            )
          )
        }
      }.sortedWith(compareBy<CommentMediaReference> { it.startIndex }.thenBy { it.endIndex })

  fun textWithMappedLinksRemoved(
    mappedBvids: Set<String>,
    mappedArticleIds: Set<Long> = emptySet(),
  ): String {
    if (mappedBvids.isEmpty() && mappedArticleIds.isEmpty()) return originalText
    val removals =
      buildList {
        links.filter { it.bvid in mappedBvids }.forEach { add(it.startIndex until it.endIndex) }
        articleLinks.filter { it.articleId in mappedArticleIds }.forEach {
          add(it.startIndex until it.endIndex)
        }
      }.sortedByDescending { it.first }
    var result = originalText
    removals.forEach { range -> result = result.removeRange(range) }
    return result
      .replace(Regex("[ \\t]+(?=\\r?\\n)"), "")
      .replace(Regex("(?:\\r?\\n){3,}"), "\n\n")
      .trim()
  }

  internal fun withResolvedShortLinks(
    resolvedReferences: Map<String, String>
  ): ParsedCommentVideoLinks {
    if (resolvedReferences.isEmpty() || pendingVideoLinks.isEmpty()) return this
    val resolvedLinks =
      pendingVideoLinks.mapNotNull { pending ->
        val reference = resolvedReferences[pending.targetUrl] ?: return@mapNotNull null
        CommentVideoLink(
          rawUrl = pending.rawUrl,
          bvid = reference,
          startIndex = pending.startIndex,
          endIndex = pending.endIndex,
        )
      }
    return copy(
      links =
        (links + resolvedLinks)
          .distinctBy { Triple(it.startIndex, it.endIndex, it.bvid.lowercase()) }
          .sortedBy { it.startIndex }
    )
  }

  /** 使用评论接口给出的跳转目标补足正文中无法直接识别的短链或显示文本。 */
  internal fun withCommentJumpLinks(jumpLinks: List<CommentJumpLink>): ParsedCommentVideoLinks {
    if (jumpLinks.isEmpty()) return this
    val augmentedLinks = links.toMutableList()
    val augmentedPending = pendingVideoLinks.toMutableList()
    jumpLinks.forEach { jump ->
      if (jump.key.isBlank() || jump.pcUrl.isBlank()) return@forEach
      val target = parseCommentVideoLinks(jump.pcUrl)
      val targetVideo = target.links.firstOrNull()
      val targetPending = target.pendingVideoLinks.firstOrNull()
      if (targetVideo == null && targetPending == null) return@forEach
      val existingPending = augmentedPending.firstOrNull { it.rawUrl == jump.key }
      val existingLink = augmentedLinks.firstOrNull { it.rawUrl == jump.key }
      val start =
        existingPending?.startIndex
          ?: existingLink?.startIndex
          ?: originalText.indexOf(jump.key).takeIf { it >= 0 }
          ?: return@forEach
      val end = start + jump.key.length
      if (targetVideo != null) {
        augmentedPending.removeAll { it.startIndex == start && it.endIndex == end }
        augmentedLinks.removeAll { it.startIndex == start && it.endIndex == end }
        augmentedLinks += CommentVideoLink(jump.key, targetVideo.bvid, start, end)
      } else if (augmentedLinks.none { it.startIndex == start && it.endIndex == end }) {
        augmentedPending.removeAll { it.startIndex == start && it.endIndex == end }
        augmentedPending +=
          PendingCommentVideoLink(
            rawUrl = jump.key,
            targetUrl = targetPending!!.targetUrl,
            startIndex = start,
            endIndex = end,
          )
      }
    }
    return copy(
      links = augmentedLinks.distinctBy { Triple(it.startIndex, it.endIndex, it.bvid.lowercase()) },
      pendingVideoLinks =
        augmentedPending.distinctBy { Triple(it.startIndex, it.endIndex, it.targetUrl) },
    )
  }
}

internal fun parseCommentVideoLinks(text: String): ParsedCommentVideoLinks {
  val videoLinks = mutableListOf<CommentVideoLink>()
  val articleLinks = mutableListOf<CommentArticleLink>()
  val pendingVideoLinks = mutableListOf<PendingCommentVideoLink>()
  val occupiedRanges = mutableListOf<IntRange>()
  val allUrlRanges = mutableListOf<IntRange>()
  fun isOccupied(range: IntRange): Boolean = occupiedRanges.any { it.overlaps(range) }
  fun mark(start: Int, endExclusive: Int) {
    if (endExclusive > start) occupiedRanges += start until endExclusive
  }

  commentUrlPattern.findAll(text).forEach { match ->
    val url = match.value.trimEnd { it in trailingUrlPunctuation }
    val urlStart = match.range.first
    val urlEnd = urlStart + url.length
    allUrlRanges += match.range
    if (url.isBlank() || isOccupied(urlStart until urlEnd)) return@forEach
    val withoutScheme =
      when {
        url.startsWith("//") -> url.removePrefix("//")
        url.contains("://") -> url.substringAfter("://")
        else -> ""
      }
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
          } else ""
        }
        host == "video" ->
          path.substringBeforeAny('/', '?', '#').let {
            if (it.startsWith("BV", ignoreCase = true)) it else "av$it"
          }
        else -> ""
      }
    candidate.takeIf(bvidPattern::matches)?.let {
      videoLinks += CommentVideoLink(url, normalizeBvid(it), urlStart, urlEnd)
      mark(urlStart, urlEnd)
      return@forEach
    }
    avidPattern.matchEntire(candidate)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
      videoLinks += CommentVideoLink(url, "av$it", urlStart, urlEnd)
      mark(urlStart, urlEnd)
      return@forEach
    }
    if ((host == "b23.tv" || host == "www.b23.tv") && candidate.isNotBlank()) {
      pendingVideoLinks +=
        PendingCommentVideoLink(
          rawUrl = url,
          targetUrl = "https://$withoutScheme",
          startIndex = urlStart,
          endIndex = urlEnd,
        )
      mark(urlStart, urlEnd)
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
        articleLinks += CommentArticleLink(url, articleId, url, urlStart, urlEnd)
        mark(urlStart, urlEnd)
      }
    } else if (host == "article") {
      path
        .substringBeforeAny('/', '?', '#')
        .toLongOrNull()
        ?.takeIf { it > 0L }
        ?.let {
          articleLinks +=
            CommentArticleLink(
              rawUrl = url,
              articleId = it,
              sourceUrl = "https://www.bilibili.com/read/cv$it",
              startIndex = urlStart,
              endIndex = urlEnd,
            )
          mark(urlStart, urlEnd)
        }
    }
  }
  rawBvidPattern.findAll(text).forEach { match ->
    val range = match.range
    if (isOccupied(range) || allUrlRanges.any { it.overlaps(range) }) return@forEach
    videoLinks +=
      CommentVideoLink(
        rawUrl = match.value,
        bvid = normalizeBvid(match.value),
        startIndex = range.first,
        endIndex = range.last + 1,
      )
    mark(range.first, range.last + 1)
  }
  rawAvidPattern.findAll(text).forEach { match ->
    val range = match.range
    if (isOccupied(range) || allUrlRanges.any { it.overlaps(range) }) return@forEach
    videoLinks +=
      CommentVideoLink(
        rawUrl = match.value,
        bvid = match.value.lowercase(),
        startIndex = range.first,
        endIndex = range.last + 1,
      )
    mark(range.first, range.last + 1)
  }
  return ParsedCommentVideoLinks(text, videoLinks, articleLinks, pendingVideoLinks)
}

/** 只接受 B 站视频详情页，供短链重定向结果校验使用。 */
internal fun extractCommentVideoReferenceFromUrl(url: String): String? {
  val parsed = parseCommentVideoLinks(url)
  return parsed.links.singleOrNull()?.bvid
}

/** 按 B 站网页端规则解析评论中的 M:SS/H:MM:SS 时间戳。 */
internal fun parseCommentTimestamps(text: String): List<CommentTimestamp> =
  timestampPattern
    .findAll(text)
    .map { match ->
      val raw = match.value
      val part = match.groups[1]?.value?.toIntOrNull() ?: -1
      val normalized = raw.substringAfter('#', raw).replace('：', ':')
      val seconds =
        normalized
          .split(':')
          .asReversed()
          .mapIndexed { index, value ->
            (value.toLongOrNull()?.coerceAtLeast(0L) ?: 0L) * pow60(index)
          }
          .sum()
      CommentTimestamp(raw, seconds, part, match.range.first, match.range.last + 1)
    }
    .toList()

private fun pow60(power: Int): Long {
  var result = 1L
  repeat(power) { result *= 60L }
  return result
}

private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

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

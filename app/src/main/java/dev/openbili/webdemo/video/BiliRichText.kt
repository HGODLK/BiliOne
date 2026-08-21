package dev.openbili.webdemo.video

/**
 * 评论区富文本渲染组件。
 *
 * 把 B 站评论的纯文本渲染为可交互的富文本：识别并高亮网页链接与 @ 提及，将表情符
 * 替换为内联图片，并在点击/长按时回调对应处理器。链接与提及的高亮通过
 * AnnotatedString 的字符串注解实现；点击命中通过 TextLayoutResult 反查偏移量处的
 * 注解完成，无需为每个片段单独创建可点击组件。
 */

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.CommentMention
import dev.openbili.webdemo.ui.LocalWebLinkHandler

/** 持有最近一次文本布局结果，供点击命中检测时反查偏移量对应的注解。 */
private class TextLayoutResultHolder(var value: TextLayoutResult? = null)

/** 评论正文中的行内视频/专栏节点，保留原文范围以便替换显示并命中点击。 */
data class BiliRichMediaLink(
  val id: String,
  val kind: CommentMediaKind,
  val sourceKey: String,
  val startIndex: Int,
  val endIndex: Int,
  val title: String,
  val iconUrl: String = "",
)

private class TextBoundsHolder(var value: Rect = Rect.Zero)

/** 匹配评论中的网页链接（http/https），匹配时排除其后的常见中英文标点。 */
private val richTextWebUrlPattern = Regex("https?://[^\\s<>，。！？；：）】》”]+", RegexOption.IGNORE_CASE)
/** 链接末尾需要剥除的尾随标点集合，避免把句末标点误判进链接范围。 */
private val richTextTrailingUrlPunctuation =
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

/**
 * 渲染一条可交互的富文本评论。
 *
 * 依次识别文本中的网页链接、@ 提及与表情符，并用不同样式呈现：链接加下划线着色、
 * 提及加粗着色、表情符替换为内联图片。点击时按命中目标分别回调 [onMentionClick]、
 * 链接处理器 [LocalWebLinkHandler] 或 [onTextClick]；长按回调 [onTextLongClick]，
 * 文本溢出时通过 [onOverflowChanged] 上报。
 */
@Composable
fun BiliRichText(
  text: String,
  emotes: Map<String, BiliEmote>,
  mentions: List<CommentMention> = emptyList(),
  mediaLinks: List<BiliRichMediaLink> = emptyList(),
  onMentionClick: (Long) -> Unit = {},
  onMediaClick: (BiliRichMediaLink, Rect) -> Unit = { _, _ -> },
  onMediaLongClick: (BiliRichMediaLink) -> Unit = {},
  onTimestampClick: (Long, Int) -> Unit = { _, _ -> },
  onTextClick: () -> Unit = {},
  onTextLongClick: () -> Unit = {},
  modifier: Modifier = Modifier,
  style: TextStyle? = null,
  maxLines: Int = 6,
  onOverflowChanged: (Boolean) -> Unit = {},
) {
  // ── 上下文依赖：网页链接处理器 ─────────────────────────────────────────────
  val localWebLinkHandler = LocalWebLinkHandler.current
  // ── 预处理：只保留文本中实际出现的表情与 @ 提及 ────────────────────────────
  // 按长度降序排序，保证「最长优先」匹配，避免长 token 被其子串抢先命中。
  val usedEmotes =
    remember(text, emotes) { emotes.keys.filter(text::contains).sortedByDescending(String::length) }
  val usedMentions =
    remember(text, mentions) {
      mentions
        .filter { it.mid > 0 && text.contains("@${it.name}") }
        .distinctBy { it.mid to it.name }
        .sortedByDescending { it.name.length }
    }
  // ── 链接与提及的高亮颜色，以及「起始偏移 → 链接文本」映射 ──────────────────
  val mentionColor = MaterialTheme.colorScheme.primary
  val webLinkColor = MaterialTheme.colorScheme.primary
  val mediaByStart = remember(mediaLinks) { mediaLinks.associateBy { it.startIndex } }
  val mediaById = remember(mediaLinks) { mediaLinks.associateBy(BiliRichMediaLink::id) }
  // 匹配所有网页链接并剥除末尾标点，记录其起始位置用于构建注解与后续点击命中。
  val webLinks =
    remember(text, mediaLinks) {
      richTextWebUrlPattern
        .findAll(text)
        .mapNotNull { match ->
          val value = match.value.trimEnd { it in richTextTrailingUrlPunctuation }
          value.takeIf(String::isNotBlank)?.let { match.range.first to it }
        }
        .filter { (start, value) ->
          mediaLinks.none { media ->
            start < media.endIndex && media.startIndex < start + value.length
          }
        }
        .toMap()
    }
  val timestamps =
    remember(text, webLinks) {
      parseCommentTimestamps(text)
        .filter { timestamp ->
          webLinks.keys.none { start ->
            val end = start + (webLinks[start]?.length ?: 0)
            timestamp.startIndex < end && start < timestamp.endIndex
          }
        }
        .associateBy { it.startIndex }
    }
  // ── 构建 AnnotatedString：按偏移量逐段匹配链接/提及/表情并追加样式注解 ──────
  val annotated =
    remember(
      text,
      usedEmotes,
      usedMentions,
      mediaLinks,
      mentionColor,
      webLinkColor,
      webLinks,
      timestamps,
    ) {
      buildAnnotatedString {
        var index = 0
        while (index < text.length) {
          val media = mediaByStart[index]
          val webUrl = webLinks[index]
          val timestamp = timestamps[index]
          val mention = usedMentions.firstOrNull { text.startsWith("@${it.name}", index) }
          val emote = usedEmotes.firstOrNull { text.startsWith(it, index) }
          when {
            media != null -> {
              val start = length
              val contentId = "media:${media.id}"
              appendInlineContent(contentId, media.title.ifBlank { media.sourceKey })
              append(normalizeMediaTitle(media.title, media.sourceKey))
              addStringAnnotation("media", media.id, start, length)
              addStyle(SpanStyle(color = webLinkColor), start, length)
              index = media.endIndex
            }
            webUrl != null -> {
              val start = length
              // 链接以「网页链接」占位文案显示，真实地址存入注解供点击时使用。
              append("网页链接")
              addStringAnnotation("web", webUrl, start, length)
              addStyle(
                SpanStyle(color = webLinkColor, textDecoration = TextDecoration.Underline),
                start,
                length,
              )
              index += webUrl.length
            }
            timestamp != null -> {
              val start = length
              append(timestamp.rawText)
              addStringAnnotation(
                "seek",
                "${timestamp.timeSeconds}#${timestamp.videoPart}",
                start,
                length,
              )
              addStyle(
                SpanStyle(color = webLinkColor, textDecoration = TextDecoration.Underline),
                start,
                length,
              )
              index = timestamp.endIndex
            }
            mention != null -> {
              val token = "@${mention.name}"
              val start = length
              append(token)
              // 用被提及用户的 mid 作为注解值，点击时据此回调。
              addStringAnnotation("mention", mention.mid.toString(), start, length)
              addStyle(
                SpanStyle(color = mentionColor, fontWeight = FontWeight.SemiBold),
                start,
                length,
              )
              index += token.length
            }
            emote != null -> {
              // 以表情 token 作为内联内容 id，与下方 inlineContent 映射一一对应。
              appendInlineContent(emote, emote)
              index += emote.length
            }
            else -> {
              append(text[index])
              index++
            }
          }
        }
      }
    }
  // ── 内联表情映射：为每个出现的表情预留占位并加载远程图片 ───────────────────
  val inline =
    remember(usedEmotes, emotes, mediaLinks) {
      buildMap {
        usedEmotes.forEach { token ->
        // 占位尺寸用 em 保证随字号缩放，并垂直居中对齐正文。
          put(
            token,
            InlineTextContent(Placeholder(1.35.em, 1.35.em, PlaceholderVerticalAlign.Center)) {
              AsyncImage(
                model = emotes[token]?.url,
                contentDescription = token,
                modifier = Modifier.size(22.dp),
                contentScale = ContentScale.Fit,
              )
            },
          )
        }
        mediaLinks.forEach { media ->
          put(
            "media:${media.id}",
            InlineTextContent(Placeholder(1.05.em, 1.05.em, PlaceholderVerticalAlign.Center)) {
              if (media.iconUrl.isNotBlank()) {
                AsyncImage(
                  model = media.iconUrl,
                  contentDescription = media.title,
                  modifier = Modifier.size(16.dp),
                  contentScale = ContentScale.Fit,
                )
              } else {
                Icon(
                  imageVector =
                    if (media.kind == CommentMediaKind.VIDEO) Icons.Default.PlayCircleOutline
                    else Icons.AutoMirrored.Filled.Article,
                  contentDescription = media.title,
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            },
          )
        }
      }
    }
  // ── 点击处理：缓存布局结果与各回调的最新引用 ───────────────────────────────
  val layoutResult = remember(annotated) { TextLayoutResultHolder() }
  val latestMentionClick by rememberUpdatedState(onMentionClick)
  val latestMediaClick by rememberUpdatedState(onMediaClick)
  val latestMediaLongClick by rememberUpdatedState(onMediaLongClick)
  val latestTimestampClick by rememberUpdatedState(onTimestampClick)
  val latestWebLinkClick by rememberUpdatedState(localWebLinkHandler)
  val latestTextClick by rememberUpdatedState(onTextClick)
  val latestTextLongClick by rememberUpdatedState(onTextLongClick)
  val latestOverflowChanged by rememberUpdatedState(onOverflowChanged)
  val textBounds = remember { TextBoundsHolder() }
  Text(
    annotated,
    inlineContent = inline,
    modifier =
      modifier
        .onGloballyPositioned { textBounds.value = it.boundsInRoot() }
        .pointerInput(annotated) {
        detectTapGestures(
          onTap = { position ->
            // 用点击坐标反查字符偏移，再读取该偏移处的注解判断命中目标。
            val offset = layoutResult.value?.getOffsetForPosition(position)
            val mediaAnnotation =
              offset?.let { annotated.getStringAnnotations("media", it, it).firstOrNull() }
            val webUrl =
              offset?.let { annotated.getStringAnnotations("web", it, it).firstOrNull() }?.item
            val mid =
              offset
                ?.let { annotated.getStringAnnotations("mention", it, it).firstOrNull() }
                ?.item
                ?.toLongOrNull()
            val seek =
              offset
                ?.let { annotated.getStringAnnotations("seek", it, it).firstOrNull() }
                ?.item
                ?.split('#', limit = 2)
            when {
              mediaAnnotation != null -> {
                val media = mediaById[mediaAnnotation.item]
                if (media != null) {
                  latestMediaClick(media, annotationBounds(layoutResult.value, mediaAnnotation, textBounds.value))
                }
              }
              webUrl != null -> latestWebLinkClick(webUrl)
              seek != null -> {
                val seconds = seek.getOrNull(0)?.toLongOrNull()
                val part = seek.getOrNull(1)?.toIntOrNull() ?: -1
                if (seconds != null) latestTimestampClick(seconds, part)
              }
              mid != null -> latestMentionClick(mid)
              else -> latestTextClick()
            }
          },
          onLongPress = { position ->
            val offset = layoutResult.value?.getOffsetForPosition(position)
            val media =
              offset
                ?.let { annotated.getStringAnnotations("media", it, it).firstOrNull() }
                ?.let { mediaById[it.item] }
            if (media != null) latestMediaLongClick(media) else latestTextLongClick()
          },
        )
      },
    onTextLayout = {
      // 保存本次布局结果并上报是否发生视觉溢出（供外部省略/展开逻辑使用）。
      layoutResult.value = it
      latestOverflowChanged(it.hasVisualOverflow)
    },
    style = style ?: MaterialTheme.typography.bodySmall,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
  )
}

private fun normalizeMediaTitle(title: String, fallback: String): String {
  val value = title.trim().ifBlank { fallback }
  return if (value.startsWith("【") || value.startsWith("[") || value.startsWith("〖")) value
  else "【$value】"
}

private fun annotationBounds(
  layout: TextLayoutResult?,
  annotation: androidx.compose.ui.text.AnnotatedString.Range<String>,
  rootBounds: Rect,
): Rect {
  if (layout == null || rootBounds == Rect.Zero) return Rect.Zero
  var result = Rect.Zero
  for (offset in annotation.start until annotation.end) {
    if (offset >= layout.layoutInput.text.length) break
    val box = layout.getBoundingBox(offset)
    if (box.width <= 0f || box.height <= 0f) continue
    val current = Rect(rootBounds.left + box.left, rootBounds.top + box.top, rootBounds.left + box.right, rootBounds.top + box.bottom)
    result =
      if (result == Rect.Zero) current
      else {
        Rect(
          left = minOf(result.left, current.left),
          top = minOf(result.top, current.top),
          right = maxOf(result.right, current.right),
          bottom = maxOf(result.bottom, current.bottom),
        )
      }
  }
  return result
}

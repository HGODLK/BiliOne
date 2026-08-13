package dev.openbili.webdemo.video

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.CommentMention
import dev.openbili.webdemo.ui.LocalWebLinkHandler

private class TextLayoutResultHolder(var value: TextLayoutResult? = null)

private val richTextWebUrlPattern =
  Regex("https?://[^\\s<>，。！？；：）】》”]+", RegexOption.IGNORE_CASE)
private val richTextTrailingUrlPunctuation =
  setOf(',', '.', '!', '?', ';', ':', ')', ']', '}', '，', '。', '！', '？', '；', '：', '）', '】', '》', '”', '\'')

@Composable
fun BiliRichText(
  text: String,
  emotes: Map<String, BiliEmote>,
  mentions: List<CommentMention> = emptyList(),
  onMentionClick: (Long) -> Unit = {},
  onTextClick: () -> Unit = {},
  onTextLongClick: () -> Unit = {},
  modifier: Modifier = Modifier,
  style: TextStyle? = null,
  maxLines: Int = 6,
  onOverflowChanged: (Boolean) -> Unit = {},
) {
  val localWebLinkHandler = LocalWebLinkHandler.current
  val usedEmotes =
    remember(text, emotes) { emotes.keys.filter(text::contains).sortedByDescending(String::length) }
  val usedMentions =
    remember(text, mentions) {
      mentions
        .filter { it.mid > 0 && text.contains("@${it.name}") }
        .distinctBy { it.mid to it.name }
        .sortedByDescending { it.name.length }
    }
  val mentionColor = MaterialTheme.colorScheme.primary
  val webLinkColor = MaterialTheme.colorScheme.primary
  val webLinks =
    remember(text) {
      richTextWebUrlPattern
        .findAll(text)
        .mapNotNull { match ->
          val value = match.value.trimEnd { it in richTextTrailingUrlPunctuation }
          value.takeIf(String::isNotBlank)?.let { match.range.first to it }
        }
        .toMap()
    }
  val annotated =
    remember(text, usedEmotes, usedMentions, mentionColor, webLinkColor, webLinks) {
      buildAnnotatedString {
        var index = 0
        while (index < text.length) {
          val webUrl = webLinks[index]
          val mention = usedMentions.firstOrNull { text.startsWith("@${it.name}", index) }
          val emote = usedEmotes.firstOrNull { text.startsWith(it, index) }
          when {
            webUrl != null -> {
              val start = length
              append("网页链接")
              addStringAnnotation("web", webUrl, start, length)
              addStyle(
                SpanStyle(color = webLinkColor, textDecoration = TextDecoration.Underline),
                start,
                length,
              )
              index += webUrl.length
            }
            mention != null -> {
              val token = "@${mention.name}"
              val start = length
              append(token)
              addStringAnnotation("mention", mention.mid.toString(), start, length)
              addStyle(
                SpanStyle(color = mentionColor, fontWeight = FontWeight.SemiBold),
                start,
                length,
              )
              index += token.length
            }
            emote != null -> {
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
  val inline =
    remember(usedEmotes, emotes) {
      usedEmotes.associateWith { token ->
        InlineTextContent(Placeholder(1.35.em, 1.35.em, PlaceholderVerticalAlign.Center)) {
          AsyncImage(
            model = emotes[token]?.url,
            contentDescription = token,
            modifier = Modifier.size(22.dp),
            contentScale = ContentScale.Fit,
          )
        }
      }
    }
  val layoutResult = remember(annotated) { TextLayoutResultHolder() }
  val latestMentionClick by rememberUpdatedState(onMentionClick)
  val latestWebLinkClick by rememberUpdatedState(localWebLinkHandler)
  val latestTextClick by rememberUpdatedState(onTextClick)
  val latestTextLongClick by rememberUpdatedState(onTextLongClick)
  val latestOverflowChanged by rememberUpdatedState(onOverflowChanged)
  Text(
    annotated,
    inlineContent = inline,
    modifier =
      modifier.pointerInput(annotated) {
        detectTapGestures(
          onTap = { position ->
            val offset = layoutResult.value?.getOffsetForPosition(position)
            val webUrl =
              offset?.let { annotated.getStringAnnotations("web", it, it).firstOrNull() }?.item
            val mid =
              offset
                ?.let { annotated.getStringAnnotations("mention", it, it).firstOrNull() }
                ?.item
                ?.toLongOrNull()
            when {
              webUrl != null -> latestWebLinkClick(webUrl)
              mid != null -> latestMentionClick(mid)
              else -> latestTextClick()
            }
          },
          onLongPress = { latestTextLongClick() },
        )
      },
    onTextLayout = {
      layoutResult.value = it
      latestOverflowChanged(it.hasVisualOverflow)
    },
    style = style ?: MaterialTheme.typography.bodySmall,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
  )
}

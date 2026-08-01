package dev.openbili.webdemo.live

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal object LiveDanmakuBlockWordsStore {
  private const val PREFERENCES_NAME = "live_danmaku_block_words"

  fun read(context: Context, roomId: Long): List<String> =
    parseLiveDanmakuBlockWords(
      context.getSharedPreferences(PREFERENCES_NAME, 0).getString(roomId.toString(), "").orEmpty()
    )

  fun write(context: Context, roomId: Long, words: List<String>) {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, 0)
    val normalized = normalizeLiveDanmakuBlockWords(words)
    if (normalized.isEmpty()) {
      preferences.edit().remove(roomId.toString()).apply()
    } else {
      preferences.edit().putString(roomId.toString(), normalized.joinToString("\n")).apply()
    }
  }
}

internal fun parseLiveDanmakuBlockWords(value: String): List<String> =
  normalizeLiveDanmakuBlockWords(value.split(Regex("[,，\\n\\r]+")))

internal fun normalizeLiveDanmakuBlockWords(words: Iterable<String>): List<String> {
  val seen = HashSet<String>()
  return words
    .asSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .map { it.take(MAX_BLOCK_WORD_LENGTH) }
    .filter { seen.add(it.lowercase()) }
    .take(MAX_BLOCK_WORDS)
    .toList()
}

internal fun isLiveDanmakuBlocked(
  content: LiveChatContent,
  blockWords: List<String>,
): Boolean {
  if (blockWords.isEmpty()) return false
  val text =
    when (content) {
      is LiveChatContent.Text -> content.text
      is LiveChatContent.Emoji -> content.displayName
      is LiveChatContent.System -> content.text
    }
  return blockWords.any { word -> text.contains(word, ignoreCase = true) }
}

@Composable
internal fun LiveDanmakuBlockWordsDialog(
  roomId: Long,
  currentWords: List<String>,
  onDismiss: () -> Unit,
  onSave: (List<String>) -> Unit,
) {
  var value by remember(roomId, currentWords) { mutableStateOf(currentWords.joinToString("\n")) }
  val previewWords = remember(value) { parseLiveDanmakuBlockWords(value) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("弹幕屏蔽词") },
    text = {
      Column {
        Text(
          "每行或用逗号分隔一个词。仅对当前直播间生效，下次进入这个直播间仍会保留。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = value,
          onValueChange = { value = it },
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
          minLines = 4,
          maxLines = 8,
          label = { Text("屏蔽词（最多 $MAX_BLOCK_WORDS 个）") },
          supportingText = { Text("当前 ${previewWords.size} 个") },
        )
      }
    },
    confirmButton = { TextButton(onClick = { onSave(previewWords) }) { Text("保存") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

private const val MAX_BLOCK_WORDS = 50
private const val MAX_BLOCK_WORD_LENGTH = 30

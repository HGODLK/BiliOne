package dev.openbili.webdemo.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.ui.controlFocusOutline

/** 专用播放页统一右侧面板。所有低频设置都通过同一组焦点按钮进入。 */
@Composable
internal fun ControllerPlaybackSidePanel(
  title: String,
  items: List<ControllerPlaybackActionItem>,
  onItemClick: (ControllerPlaybackActionItem) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val firstFocusRequester = remember(items) { FocusRequester() }
  LaunchedEffect(items) {
    if (items.isNotEmpty()) runCatching { firstFocusRequester.requestFocus() }
  }
  Surface(
    modifier = modifier.fillMaxHeight().widthIn(min = 300.dp, max = 440.dp),
    color = Color.Black.copy(alpha = .86f),
    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
    tonalElevation = 8.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        TextButton(onClick = onBack) { Text("返回", color = Color.White) }
      }
      items.forEachIndexed { index, item ->
        TextButton(
          onClick = { onItemClick(item) },
          enabled = item.enabled,
          modifier =
            Modifier.fillMaxWidth()
              .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier)
              .controlFocusOutline(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                width = 3.dp,
                enabled = true,
              )
              .background(Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp)),
        ) {
          Text(item.label, color = if (item.enabled) Color.White else Color.White.copy(alpha = .45f))
        }
      }
    }
  }
}

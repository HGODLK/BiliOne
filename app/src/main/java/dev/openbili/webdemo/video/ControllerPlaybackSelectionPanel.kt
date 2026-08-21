package dev.openbili.webdemo.video

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.ui.controlFocusOutline
import kotlinx.coroutines.launch

/** 可分层的选集面板；长按上下键通过重复事件快速滚动当前列表。 */
@Composable
internal fun ControllerPlaybackSelectionPanel(
  selectionItems: List<ControllerPlaybackActionItem>,
  selectionGroups: List<ControllerPlaybackSelectionGroup>,
  onItemClick: (ControllerPlaybackActionItem) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var path by remember { mutableStateOf<List<ControllerPlaybackSelectionGroup>>(emptyList()) }
  val currentGroup = path.lastOrNull()
  val currentContent =
    resolveControllerPlaybackSelectionContent(
      currentGroup = currentGroup,
      rootItems = selectionItems,
      rootGroups = selectionGroups,
    )
  val currentGroups = currentContent.groups
  val currentItems = currentContent.items
  val listKey =
    buildString {
      path.forEach { append(it.key).append('/') }
      append(currentGroups.size).append(':').append(currentItems.size)
    }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val firstFocusRequester = remember(listKey) { FocusRequester() }

  LaunchedEffect(listKey) {
    listState.scrollToItem(0)
    runCatching { firstFocusRequester.requestFocus() }
  }

  fun goBack() {
    if (path.isEmpty()) onBack() else path = path.dropLast(1)
  }

  Surface(
    modifier =
      modifier
        .fillMaxHeight()
        .widthIn(min = 360.dp, max = 500.dp)
        .onPreviewKeyEvent { event ->
          val native = event.nativeKeyEvent
          if (event.type != KeyEventType.KeyDown || native.repeatCount <= 0) {
            return@onPreviewKeyEvent false
          }
          val delta =
            when (native.keyCode) {
              android.view.KeyEvent.KEYCODE_DPAD_UP -> -1
              android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 1
              else -> return@onPreviewKeyEvent false
            }
          val distance = if (native.repeatCount >= 8) 720 else 420
          scope.launch { listState.scrollBy((delta * distance).toFloat()) }
          true
        },
    color = Color.Black.copy(alpha = .88f),
    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
    tonalElevation = 8.dp,
  ) {
    Column(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 24.dp, vertical = 22.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("选集", style = MaterialTheme.typography.titleLarge, color = Color.White)
        TextButton(onClick = ::goBack) {
          Text(if (path.isEmpty()) "返回" else "上一级", color = Color.White)
        }
      }
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (currentGroups.isNotEmpty()) {
          items(currentGroups, key = { "group:${it.key}" }) { group ->
            ControllerPlaybackSelectionButton(
              label = "${group.label}  ·  ${groupItemCount(group)} 项",
              modifier = if (itIsFirst(currentGroups, group)) Modifier.focusRequester(firstFocusRequester) else Modifier,
              onClick = { path = path + group },
            )
          }
        } else {
          items(currentItems, key = { it.key }) { item ->
            ControllerPlaybackSelectionButton(
              label = item.label,
              modifier = if (item == currentItems.firstOrNull()) Modifier.focusRequester(firstFocusRequester) else Modifier,
              onClick = { onItemClick(item) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ControllerPlaybackSelectionButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  TextButton(
    onClick = onClick,
    modifier =
      modifier
        .fillMaxWidth()
        .controlFocusOutline(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primary,
          width = 3.dp,
          enabled = true,
        )
        .background(Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp)),
  ) {
    Text(label, modifier = Modifier.fillMaxWidth(), color = Color.White)
  }
}

private fun groupItemCount(group: ControllerPlaybackSelectionGroup): Int =
  if (group.children.isNotEmpty()) group.children.sumOf(::groupItemCount) else group.items.size

private fun <T> itIsFirst(items: List<T>, item: T): Boolean = items.firstOrNull() == item

internal data class ControllerPlaybackSelectionContent(
  val groups: List<ControllerPlaybackSelectionGroup>,
  val items: List<ControllerPlaybackActionItem>,
)

/** 根层级读取传入分组，进入分组后再切换到该组的子分组或集数。 */
internal fun resolveControllerPlaybackSelectionContent(
  currentGroup: ControllerPlaybackSelectionGroup?,
  rootItems: List<ControllerPlaybackActionItem>,
  rootGroups: List<ControllerPlaybackSelectionGroup>,
): ControllerPlaybackSelectionContent =
  if (currentGroup == null) {
    ControllerPlaybackSelectionContent(groups = rootGroups, items = rootItems)
  } else {
    ControllerPlaybackSelectionContent(groups = currentGroup.children, items = currentGroup.items)
  }

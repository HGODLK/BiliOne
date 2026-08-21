package dev.openbili.webdemo.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.ui.controlFocusOutline

/** 专用播放页的底部信息层与操作行。 */
@Composable
internal fun ControllerPlaybackControls(
  title: String,
  positionMs: Long,
  durationMs: Long,
  isPlaying: Boolean,
  actions: List<ControllerPlaybackActionItem>,
  overlay: ControllerPlaybackOverlay,
  onAction: (ControllerPlaybackActionItem) -> Unit,
  initialActionFocusRequester: FocusRequester? = null,
  showProgress: Boolean = true,
  statusText: String? = null,
  modifier: Modifier = Modifier,
) {
  val visible = overlay != ControllerPlaybackOverlay.HIDDEN
  Box(
    modifier =
      modifier.fillMaxWidth().background(
        Brush.verticalGradient(
          listOf(Color.Transparent, Color.Black.copy(alpha = .88f)),
        )
      ),
  ) {
    AnimatedVisibility(
      visible = visible,
      enter = fadeIn(tween(160)),
      exit = fadeOut(tween(180)),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = title.ifBlank { "正在播放" },
          style = MaterialTheme.typography.headlineSmall,
          color = Color.White,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (showProgress) {
          ControllerPlaybackProgress(positionMs = positionMs, durationMs = durationMs)
        } else if (!statusText.isNullOrBlank()) {
          Text(statusText, color = Color.White.copy(alpha = .86f))
        }
        AnimatedVisibility(
          visible = overlay == ControllerPlaybackOverlay.ACTIONS,
          enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it },
          exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it },
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            actions.forEach { item ->
              TextButton(
                onClick = { onAction(item) },
                enabled = item.enabled,
                modifier =
                  Modifier.widthIn(min = 106.dp)
                    .then(
                      if (initialActionFocusRequester != null && actions.firstOrNull() == item) {
                        Modifier.focusRequester(initialActionFocusRequester)
                      } else Modifier
                    )
                    .controlFocusOutline(
                      shape = RoundedCornerShape(12.dp),
                      color = MaterialTheme.colorScheme.primary,
                      width = 3.dp,
                      enabled = true,
                    )
                    .background(Color.White.copy(alpha = .12f), RoundedCornerShape(12.dp)),
              ) {
                Text(
                  item.label,
                  color = if (item.enabled) Color.White else Color.White.copy(alpha = .45f),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ControllerPlaybackProgress(positionMs: Long, durationMs: Long) {
  val safeDuration = durationMs.coerceAtLeast(1L)
  val progress = (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
  val played = formatControllerPlaybackTime(positionMs)
  val total = formatControllerPlaybackTime(durationMs)
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(played, color = Color.White, style = MaterialTheme.typography.labelLarge)
      Text(total, color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelLarge)
    }
    Box(
      modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = .28f), RoundedCornerShape(4.dp)),
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth(progress).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
      ) {
        androidx.compose.foundation.layout.Spacer(Modifier.padding(vertical = 3.dp))
      }
    }
  }
}

internal fun formatControllerPlaybackTime(milliseconds: Long): String {
  val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
  val seconds = totalSeconds % 60
  val minutes = (totalSeconds / 60) % 60
  val hours = totalSeconds / 3600
  return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
  else "%02d:%02d".format(minutes, seconds)
}

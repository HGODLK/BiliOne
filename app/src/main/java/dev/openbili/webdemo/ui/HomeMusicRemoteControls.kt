package dev.openbili.webdemo.ui

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.PremiumAudioMode
import dev.openbili.webdemo.music.HomeMusicPlayerViewModel
import dev.openbili.webdemo.music.HomeMusicUiState
import dev.openbili.webdemo.music.MusicPlaybackOrder
import dev.openbili.webdemo.music.MusicPlaybackProgressState
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun MusicTransportControls(
  state: HomeMusicUiState,
  viewModel: HomeMusicPlayerViewModel,
  menuHideProgress: Float,
  controlFocusRequest: Int,
  playPauseFocusRequest: Int = 0,
  controlDismissTransientRequest: Int,
  onControlProgressRequested: () -> Unit,
  onControlLibraryRequested: () -> Unit,
  onControlTransientOpenChanged: (Boolean) -> Unit,
  modifier: Modifier,
) {
  val controlMode = LocalControlMode.current
  val advancedAudioAvailable = state.dolbyAvailable || state.hiResAvailable
  val focusRequesters = remember { List(5) { FocusRequester() } }
  LaunchedEffect(controlMode, controlFocusRequest) {
    if (controlMode && controlFocusRequest > 0) {
      withFrameNanos {}
      val targetIndex =
        when {
          state.currentItem != null && !state.playbackLoading -> 2
          state.items.isNotEmpty() -> 0
          advancedAudioAvailable -> 4
          else -> return@LaunchedEffect
        }
      runCatching { focusRequesters[targetIndex].requestFocus() }
    }
  }
  LaunchedEffect(controlMode, playPauseFocusRequest) {
    if (!controlMode || playPauseFocusRequest <= 0) return@LaunchedEffect
    withFrameNanos {}
    runCatching { focusRequesters[2].requestFocus() }
  }
  Row(
    modifier.fillMaxWidth(),
    horizontalArrangement =
      Arrangement.spacedBy(
        16.dp + 10.dp * menuHideProgress.coerceIn(0f, 1f),
        Alignment.CenterHorizontally,
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    MusicControlButton(
      onClick = viewModel::togglePlaybackOrder,
      enabled = state.items.isNotEmpty(),
      description =
        when (state.playbackOrder) {
          MusicPlaybackOrder.SEQUENTIAL -> "顺序播放"
          MusicPlaybackOrder.RANDOM -> "随机播放"
          MusicPlaybackOrder.SINGLE_REPEAT -> "单曲循环"
        },
      focusRequester = focusRequesters[0].takeIf { controlMode },
      controlLeftFocusRequester =
        if (advancedAudioAvailable) focusRequesters[4] else focusRequesters[3],
      controlRightFocusRequester = focusRequesters[1],
      onControlDown = onControlProgressRequested,
    ) {
      Icon(
        when (state.playbackOrder) {
          MusicPlaybackOrder.SEQUENTIAL -> Icons.Default.Repeat
          MusicPlaybackOrder.RANDOM -> Icons.Default.Shuffle
          MusicPlaybackOrder.SINGLE_REPEAT -> Icons.Default.RepeatOne
        },
        contentDescription = null,
      )
    }
    MusicControlButton(
      onClick = viewModel::playPrevious,
      enabled = state.items.isNotEmpty(),
      description = "上一首",
      focusRequester = focusRequesters[1].takeIf { controlMode },
      controlLeftFocusRequester = focusRequesters[0],
      controlRightFocusRequester = focusRequesters[2],
      onControlDown = onControlProgressRequested,
    ) {
      Icon(Icons.Default.SkipPrevious, contentDescription = null)
    }
    MusicControlButton(
      onClick = viewModel::togglePlayback,
      enabled = state.currentItem != null && !state.playbackLoading,
      description = if (state.isPlaying) "暂停" else "播放",
      emphasized = true,
      focusRequester = focusRequesters[2].takeIf { controlMode },
      controlLeftFocusRequester = focusRequesters[1],
      controlRightFocusRequester = focusRequesters[3],
      onControlDown = onControlProgressRequested,
    ) {
      Icon(
        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = null,
      )
    }
    MusicControlButton(
      onClick = viewModel::playNext,
      enabled = state.items.isNotEmpty(),
      description = "下一首",
      focusRequester = focusRequesters[3].takeIf { controlMode },
      controlLeftFocusRequester = focusRequesters[2],
      controlRightFocusRequester = focusRequesters[4].takeIf { advancedAudioAvailable },
      onControlRight = if (advancedAudioAvailable) null else onControlLibraryRequested,
      onControlDown = onControlProgressRequested,
    ) {
      Icon(Icons.Default.SkipNext, contentDescription = null)
    }
    AdvancedAudioButton(
      state = state,
      onSelect = viewModel::selectPremiumAudio,
      focusRequester = focusRequesters[4].takeIf { controlMode },
      controlDismissTransientRequest = controlDismissTransientRequest,
      controlLeftFocusRequester = focusRequesters[3],
      onControlRight = onControlLibraryRequested,
      onControlDown = onControlProgressRequested,
      onControlTransientOpenChanged = onControlTransientOpenChanged,
    )
  }
}

@Composable
private fun MusicControlButton(
  onClick: () -> Unit,
  enabled: Boolean,
  description: String,
  emphasized: Boolean = false,
  focusRequester: FocusRequester? = null,
  controlLeftFocusRequester: FocusRequester? = null,
  controlRightFocusRequester: FocusRequester? = null,
  onControlRight: (() -> Unit)? = null,
  onControlDown: () -> Unit = {},
  content: @Composable () -> Unit,
) {
  val controlMode = LocalControlMode.current
  val contentColor = if (enabled) Color.White else Color.White.copy(alpha = .28f)
  Surface(
    modifier =
      Modifier.size(if (emphasized) 60.dp else 52.dp)
        .clip(CircleShape)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .then(
          if (controlMode) {
            Modifier.focusProperties {
                left = controlLeftFocusRequester ?: FocusRequester.Cancel
                right = controlRightFocusRequester ?: FocusRequester.Cancel
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
              }
              .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                  KeyEvent.KEYCODE_DPAD_RIGHT ->
                    if (onControlRight != null) {
                      onControlRight()
                      true
                    } else {
                      false
                    }
                  KeyEvent.KEYCODE_DPAD_DOWN -> {
                    onControlDown()
                    true
                  }
                  KeyEvent.KEYCODE_DPAD_UP -> true
                  else -> false
                }
              }
          } else {
            Modifier
          }
        )
        .musicFocusChrome(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          width = if (emphasized) 3.dp else 2.dp,
        )
        .clickable(
          enabled = enabled || controlMode,
          onClickLabel = description,
          onClick = { if (enabled) onClick() },
        ),
    shape = CircleShape,
    color =
      if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = .36f)
      else Color.Black.copy(alpha = .30f),
    contentColor = contentColor,
    border = BorderStroke(.75.dp, Color.White.copy(alpha = if (enabled) .18f else .08f)),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
  }
}

@Composable
private fun AdvancedAudioButton(
  state: HomeMusicUiState,
  onSelect: (PremiumAudioMode?) -> Unit,
  focusRequester: FocusRequester? = null,
  controlDismissTransientRequest: Int = 0,
  controlLeftFocusRequester: FocusRequester? = null,
  onControlRight: () -> Unit = {},
  onControlDown: () -> Unit = {},
  onControlTransientOpenChanged: (Boolean) -> Unit = {},
) {
  val available = state.dolbyAvailable || state.hiResAvailable
  val controlMode = LocalControlMode.current
  var expanded by remember { mutableStateOf(false) }
  LaunchedEffect(expanded) { onControlTransientOpenChanged(expanded) }
  LaunchedEffect(controlDismissTransientRequest) {
    if (controlDismissTransientRequest > 0) expanded = false
  }
  Box {
    Surface(
      modifier =
        Modifier.size(52.dp)
          .clip(CircleShape)
          .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
          .then(
            if (controlMode) {
              Modifier.focusProperties {
                  left = controlLeftFocusRequester ?: FocusRequester.Cancel
                  right = FocusRequester.Cancel
                  up = FocusRequester.Cancel
                  down = FocusRequester.Cancel
                }
                .onPreviewKeyEvent { event ->
                  when {
                    event.type == KeyEventType.KeyDown &&
                      event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                      onControlRight()
                      true
                    }
                    event.type == KeyEventType.KeyDown &&
                      event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                      onControlDown()
                      true
                    }
                    event.type == KeyEventType.KeyDown &&
                      event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP -> true
                    else -> false
                  }
                }
            } else {
              Modifier
            }
          )
          .musicFocusChrome(CircleShape, MaterialTheme.colorScheme.primary, width = 3.dp)
          .clickable(
            enabled = available || controlMode,
            onClickLabel = "高级音质",
          ) {
            if (available) expanded = true
          },
      shape = CircleShape,
      color =
        if (state.selectedPremiumAudio != null) MaterialTheme.colorScheme.primary.copy(alpha = .34f)
        else Color.Black.copy(alpha = .30f),
      contentColor = if (available) Color.White else Color.White.copy(alpha = .28f),
      border = BorderStroke(.75.dp, Color.White.copy(alpha = if (available) .18f else .08f)),
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
    ) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MusicNote, contentDescription = null)
      }
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      containerColor = Color(0xEE202124),
    ) {
      PremiumAudioMenuItem(
        label = "标准音质",
        selected = state.selectedPremiumAudio == null,
        enabled = true,
      ) {
        onSelect(null)
        expanded = false
      }
      PremiumAudioMenuItem(
        label = "Dolby Atmos",
        selected = state.selectedPremiumAudio == PremiumAudioMode.DOLBY,
        enabled = state.dolbyAvailable,
      ) {
        onSelect(PremiumAudioMode.DOLBY)
        expanded = false
      }
      PremiumAudioMenuItem(
        label = "Hi-Res",
        selected = state.selectedPremiumAudio == PremiumAudioMode.HI_RES,
        enabled = state.hiResAvailable,
      ) {
        onSelect(PremiumAudioMode.HI_RES)
        expanded = false
      }
    }
  }
}

@Composable
private fun PremiumAudioMenuItem(
  label: String,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  DropdownMenuItem(
    text = { Text(label, color = if (enabled) Color.White else Color.White.copy(alpha = .32f)) },
    onClick = onClick,
    enabled = enabled,
    trailingIcon = {
      if (selected) {
        Icon(
          Icons.Default.Check,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    },
  )
}

@Composable
internal fun MusicProgressBar(
  progressState: StateFlow<MusicPlaybackProgressState>,
  onSeek: (Long) -> Unit,
  controlFocusRequest: Int,
  onControlPlayerRequested: () -> Unit,
  modifier: Modifier,
) {
  val progress by progressState.collectAsState()
  val controlMode = LocalControlMode.current
  val progressFocusRequester = remember { FocusRequester() }
  LaunchedEffect(controlMode, controlFocusRequest) {
    if (controlMode && controlFocusRequest > 0) {
      withFrameNanos {}
      runCatching { progressFocusRequester.requestFocus() }
    }
  }
  val duration = progress.durationMs.coerceAtLeast(1L)
  var dragFraction by remember { mutableStateOf<Float?>(null) }
  val playedFraction =
    dragFraction ?: (progress.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
  val enabled = progress.enabled
  val activeColor = MaterialTheme.colorScheme.primary
  val inactiveColor = Color.Gray.copy(alpha = .66f)
  val trackHeight = 5.dp
  val thumbRadius = 5.5.dp
  Canvas(
    modifier
      .height(24.dp)
      .focusRequester(progressFocusRequester)
      .focusProperties { canFocus = controlMode }
      .then(
        if (controlMode) {
          Modifier.musicFocusChrome(
              RoundedCornerShape(12.dp),
              MaterialTheme.colorScheme.primary,
              2.dp,
            )
            .focusable(enabled = controlMode)
            .onPreviewKeyEvent { event ->
              val keyCode = event.nativeKeyEvent.keyCode
              when {
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                  if (event.type == KeyEventType.KeyDown && enabled) {
                    val delta =
                      if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -CONTROL_SEEK_STEP_MS
                      else CONTROL_SEEK_STEP_MS
                    onSeek((progress.positionMs + delta).coerceIn(0L, duration))
                  }
                  true
                }
                keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                  if (event.type == KeyEventType.KeyDown) onControlPlayerRequested()
                  true
                }
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> true
                else -> false
              }
            }
        } else {
          Modifier
        }
      )
      .semantics { progressBarRangeInfo = ProgressBarRangeInfo(playedFraction, 0f..1f) }
      .pointerInput(enabled, duration) {
        if (!enabled) return@pointerInput
        detectTapGestures { offset ->
          onSeek(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
        }
      }
      .pointerInput(enabled, duration) {
        if (!enabled) return@pointerInput
        detectHorizontalDragGestures(
          onDragStart = { offset ->
            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
          },
          onHorizontalDrag = { change, _ ->
            change.consume()
            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
          },
          onDragEnd = {
            dragFraction?.let { onSeek((it * duration).toLong()) }
            dragFraction = null
          },
          onDragCancel = { dragFraction = null },
        )
      }
  ) {
    val centerY = size.height / 2f
    val endX = size.width * playedFraction
    drawLine(
      color = inactiveColor,
      start = Offset(0f, centerY),
      end = Offset(size.width, centerY),
      strokeWidth = trackHeight.toPx(),
    )
    drawLine(
      color = if (enabled) activeColor else activeColor.copy(alpha = .32f),
      start = Offset(0f, centerY),
      end = Offset(endX, centerY),
      strokeWidth = trackHeight.toPx(),
    )
    if (enabled) {
      drawCircle(
        color = activeColor,
        radius = thumbRadius.toPx(),
        center = Offset(endX, centerY),
      )
    }
  }
}

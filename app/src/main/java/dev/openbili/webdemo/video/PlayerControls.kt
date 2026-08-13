package dev.openbili.webdemo.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.PlayerSubtitleState
import dev.openbili.webdemo.api.DANMAKU_COLORFUL_NONE
import dev.openbili.webdemo.api.DANMAKU_COLORFUL_VIP_GRADIENT
import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.api.PremiumAudioMode
import dev.openbili.webdemo.settings.SubtitleHorizontalPosition
import dev.openbili.webdemo.settings.SubtitleStyle
import kotlin.math.roundToInt

@Composable
internal fun DanmakuComposer(
  onSend: (String, Int, Int, Int, Int) -> Unit,
  onDismiss: () -> Unit,
  vipActive: Boolean,
  initialColor: Int,
  initialColorful: Int,
  onColorChanged: (Int, Int) -> Unit,
  imeHostBottomInRoot: Float? = null,
  imeBaselineBottomPadding: Dp = 0.dp,
  modifier: Modifier = Modifier,
) {
  var text by remember { mutableStateOf("") }
  var color by remember(initialColor) { mutableIntStateOf(initialColor) }
  var colorful by
    remember(initialColorful, vipActive) {
      mutableIntStateOf(
        if (vipActive && initialColorful == DANMAKU_COLORFUL_VIP_GRADIENT) {
          DANMAKU_COLORFUL_VIP_GRADIENT
        } else {
          DANMAKU_COLORFUL_NONE
        }
      )
    }
  var mode by remember { mutableIntStateOf(1) }
  var fontSize by remember { mutableIntStateOf(25) }
  val density = LocalDensity.current
  val rootHeightPx = LocalView.current.height.toFloat()
  val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
  val baselinePaddingPx = with(density) { imeBaselineBottomPadding.toPx() }
  val imeOverlapPx =
    if (imeBottomPx > 0f && rootHeightPx > 0f) {
      val imeTopInRoot = rootHeightPx - imeBottomPx
      val composerBottomInRoot = (imeHostBottomInRoot ?: rootHeightPx) - baselinePaddingPx
      (composerBottomInRoot - imeTopInRoot).coerceIn(0f, imeBottomPx)
    } else {
      0f
    }
  val imeOverlap = with(density) { imeOverlapPx.toDp() }
  Surface(
    modifier = modifier.padding(bottom = imeOverlap).widthIn(max = 720.dp),
    shape = RoundedCornerShape(24.dp),
    color = Color.Black.copy(alpha = .86f),
    contentColor = Color.White,
    tonalElevation = 6.dp,
  ) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          modifier = Modifier.weight(1f),
          shape = CircleShape,
          color = Color.White.copy(alpha = .12f),
        ) {
          BasicTextField(
            value = text,
            onValueChange = { text = it.take(80) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            singleLine = true,
            decorationBox = { inner ->
              if (text.isEmpty()) Text("发送一条弹幕", color = Color.White.copy(alpha = .62f))
              inner()
            },
          )
        }
        TextButton(onClick = onDismiss) { Text("取消", color = Color.White) }
        TextButton(
          enabled = text.isNotBlank(),
          onClick = {
            val message = text.trim()
            if (message.isNotEmpty()) {
              val sendColorful =
                if (vipActive && colorful == DANMAKU_COLORFUL_VIP_GRADIENT) {
                  DANMAKU_COLORFUL_VIP_GRADIENT
                } else {
                  DANMAKU_COLORFUL_NONE
                }
              onSend(message, color, mode, fontSize, sendColorful)
            }
          },
        ) {
          Text("发送", color = if (text.isNotBlank()) Color.White else Color.Gray)
        }
      }
      Text(
        "颜色",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = .72f),
      )
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DANMAKU_COLORS.forEach { value ->
          val selected = colorful == DANMAKU_COLORFUL_NONE && color == value
          Box(
            Modifier.padding(end = 8.dp)
              .size(if (selected) 26.dp else 22.dp)
              .clip(CircleShape)
              .background(Color(0xFF000000L or value.toLong()))
              .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = .35f),
                shape = CircleShape,
              )
              .clickable {
                color = value
                colorful = DANMAKU_COLORFUL_NONE
                onColorChanged(value, DANMAKU_COLORFUL_NONE)
              }
          )
        }
        val gradientSelected = colorful == DANMAKU_COLORFUL_VIP_GRADIENT
        Box(
          modifier =
            Modifier.padding(end = 4.dp)
              .size(height = if (gradientSelected) 26.dp else 22.dp, width = 48.dp)
              .clip(CircleShape)
              .background(
                Brush.horizontalGradient(
                  VIP_DANMAKU_COLORS.map { it.copy(alpha = if (vipActive) 1f else .28f) }
                )
              )
              .border(
                width = if (gradientSelected) 2.dp else 1.dp,
                color =
                  if (gradientSelected) Color.White
                  else Color.White.copy(alpha = if (vipActive) .5f else .2f),
                shape = CircleShape,
              )
              .clickable(enabled = vipActive) {
                color = 0xFFFFFF
                colorful = DANMAKU_COLORFUL_VIP_GRADIENT
                onColorChanged(0xFFFFFF, DANMAKU_COLORFUL_VIP_GRADIENT)
              },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            "VIP",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = if (vipActive) 1f else .45f),
          )
        }
        if (!vipActive) {
          Text(
            "大会员专属",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = .45f),
          )
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        DanmakuComposerOption("滚动", selected = mode == 1) { mode = 1 }
        DanmakuComposerOption("顶部", selected = mode == 5) { mode = 5 }
        DanmakuComposerOption("底部", selected = mode == 4) { mode = 4 }
        Spacer(Modifier.weight(1f))
        DanmakuComposerOption("小", selected = fontSize == 18) { fontSize = 18 }
        DanmakuComposerOption("标准", selected = fontSize == 25) { fontSize = 25 }
      }
    }
  }
}

@Composable
private fun DanmakuComposerOption(text: String, selected: Boolean, onClick: () -> Unit) {
  TextButton(onClick = onClick) {
    Text(text, color = if (selected) MaterialTheme.colorScheme.primary else Color.White)
  }
}

private val DANMAKU_COLORS =
  listOf(
    0xFE0302,
    0xFF7204,
    0xFFAA02,
    0xFFD302,
    0xFFFF00,
    0xA0EE00,
    0x00CD00,
    0x019899,
    0x4266BE,
    0x89D5FF,
    0xCC0273,
    0x222222,
    0x9B9B9B,
    0xFFFFFF,
  )

private val VIP_DANMAKU_COLORS =
  listOf(
    Color(0xFFFF5A8F),
    Color(0xFFFF9A5A),
    Color(0xFFFFD75A),
    Color(0xFF63E6BE),
    Color(0xFF66C7F2),
    Color(0xFF8C9EFF),
    Color(0xFFC792EA),
  )

private val SUBTITLE_TEXT_COLORS =
  listOf(0xFFFFFF, 0xFFF176, 0x80DEEA, 0xA5D6A7, 0xF48FB1, 0xFFB74D, 0x212121)

@Composable
private fun SubtitlePositionOption(text: String, selected: Boolean, onClick: () -> Unit) {
  TextButton(onClick = onClick) {
    Text(text, color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified)
  }
}

@Composable
internal fun ModernPlayerControls(
  playData: PlayUrlData,
  premiumAudioVisible: Boolean,
  showDanmaku: Boolean,
  danmakuSmartBlocking: Boolean,
  isFullscreen: Boolean,
  isPlaying: Boolean,
  currentPositionMs: () -> Long,
  durationMs: Long,
  onPlayPause: () -> Unit,
  onSeek: (Long) -> Unit,
  onSeekPreview: (Long) -> Unit,
  onSeekCancel: () -> Unit,
  onFullscreen: () -> Unit,
  onFullscreenPress: () -> Unit = {},
  onToggleDanmaku: () -> Unit,
  onDanmakuSmartBlockingChange: (Boolean) -> Unit,
  danmakuComposerEnabled: Boolean,
  onComposeDanmaku: () -> Unit,
  danmakuDisplayArea: Float,
  danmakuDensity: Int,
  danmakuBlockLevel: Int,
  danmakuOpacity: Float,
  danmakuFontScale: Float,
  danmakuSpeed: Float,
  playbackSpeed: Float,
  onDanmakuDisplayAreaChange: (Float) -> Unit,
  onDanmakuDensityChange: (Int) -> Unit,
  onDanmakuBlockLevelChange: (Int) -> Unit,
  onDanmakuOpacityChange: (Float) -> Unit,
  onDanmakuFontScaleChange: (Float) -> Unit,
  onDanmakuSpeedChange: (Float) -> Unit,
  onPlaybackSpeedChange: (Float) -> Unit,
  onMenuVisibilityChanged: (Boolean) -> Unit,
  onProgressScrubChanged: (Boolean) -> Unit,
  onSwitchQuality: (Int) -> Unit,
  onSwitchPremiumAudio: (PremiumAudioMode) -> Unit,
  subtitleState: PlayerSubtitleState,
  onSelectSubtitle: (String?) -> Unit,
  subtitleStyle: SubtitleStyle,
  onSubtitleStyleChange: (SubtitleStyle) -> Unit,
  modifier: Modifier = Modifier,
  showCenterAction: Boolean = true,
  fullscreenTitle: String? = null,
  onlineViewerText: String? = null,
  onOpenSelection: (() -> Unit)? = null,
) {
  val displayedPositionMs = currentPositionMs()
  var speedMenu by remember { mutableStateOf(false) }
  var qualityMenu by remember { mutableStateOf(false) }
  var subtitleMenu by remember { mutableStateOf(false) }
  var danmakuMenu by remember { mutableStateOf(false) }
  var sliderPreviewMs by remember { mutableStateOf<Long?>(null) }
  LaunchedEffect(speedMenu, qualityMenu, subtitleMenu, danmakuMenu) {
    onMenuVisibilityChanged(speedMenu || qualityMenu || subtitleMenu || danmakuMenu)
  }
  LaunchedEffect(subtitleState.mediaId, subtitleState.tracks.isNotEmpty()) {
    if (subtitleState.tracks.isEmpty()) subtitleMenu = false
  }
  DisposableEffect(Unit) { onDispose { onMenuVisibilityChanged(false) } }
  Box(modifier) {
    if (isFullscreen && !fullscreenTitle.isNullOrBlank()) {
      Text(
        text = fullscreenTitle,
        modifier =
          Modifier.align(Alignment.TopStart)
            .padding(horizontal = 28.dp, vertical = 20.dp)
            .widthIn(max = 520.dp),
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
      )
    }
    if (isFullscreen && !onlineViewerText.isNullOrBlank()) {
      Text(
        text = "${onlineViewerText}人在看",
        modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 28.dp, vertical = 20.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
      )
    }
    if (showCenterAction) {
      PlayerCenterPlayPauseButton(
        isPlaying = isPlaying,
        onPlayPause = onPlayPause,
        modifier = Modifier.align(Alignment.Center),
      )
    }
    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))
          )
          .padding(start = 18.dp, end = 18.dp, top = 28.dp, bottom = 8.dp)
    ) {
      YoutubeSeekBar(
        value = (sliderPreviewMs ?: displayedPositionMs).coerceAtMost(durationMs).toFloat(),
        durationMs = durationMs,
        onValueChange = {
          val target = it.toLong()
          sliderPreviewMs = target
          onSeekPreview(target)
        },
        onValueChangeFinished = { finishedValue ->
          val target = sliderPreviewMs ?: finishedValue.toLong()
          sliderPreviewMs = null
          onSeek(target)
        },
        onScrubStateChanged = onProgressScrubChanged,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onPlayPause, modifier = Modifier.size(38.dp)) {
          if (isPlaying) {
            Text("Ⅱ", color = Color.White, style = MaterialTheme.typography.titleMedium)
          } else {
            Icon(Icons.Default.PlayArrow, "播放", tint = Color.White)
          }
        }
        Spacer(Modifier.width(4.dp))
        Text(
          formatPlayerTime(displayedPositionMs),
          color = Color.White,
          style = MaterialTheme.typography.labelMedium,
        )
        Text(" / ", color = Color.White, style = MaterialTheme.typography.labelMedium)
        Text(
          formatPlayerTime(durationMs),
          color = Color.White,
          style = MaterialTheme.typography.labelMedium,
        )
        if (premiumAudioVisible) {
          Spacer(Modifier.width(10.dp))
          PremiumAudioMode.entries.forEach { mode ->
            if (playData.supportsPremiumAudio(mode)) {
              TextButton(onClick = { onSwitchPremiumAudio(mode) }) {
                Text(
                  mode.label,
                  color =
                    if (playData.premiumAudioMode == mode) MaterialTheme.colorScheme.primary
                    else Color.White,
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }
          }
        }
        Spacer(Modifier.weight(1f))
        if (isFullscreen && onOpenSelection != null) {
          TextButton(onClick = onOpenSelection) { Text("选集", color = Color.White) }
        }
        Box {
          TextButton(onClick = { speedMenu = true }) {
            Text(formatPlaybackSpeed(playbackSpeed), color = Color.White)
          }
          DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
            PLAYER_SPEED_OPTIONS.forEach { speed ->
              DropdownMenuItem(
                text = { Text(formatPlaybackSpeed(speed)) },
                onClick = {
                  speedMenu = false
                  onPlaybackSpeedChange(speed)
                },
              )
            }
          }
        }
        Box {
          TextButton(onClick = { qualityMenu = true }) {
            Text(
              playData.streams.getOrNull(playData.currentStreamIndex)?.quality ?: "画质",
              color = Color.White,
            )
          }
          DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
            playData.streams.forEachIndexed { index, stream ->
              DropdownMenuItem(
                text = { Text(stream.quality) },
                onClick = {
                  qualityMenu = false
                  onSwitchQuality(index)
                },
              )
            }
          }
        }
        if (subtitleState.tracks.isNotEmpty()) {
          Box {
            TextButton(onClick = { subtitleMenu = true }) {
              Text(
                "字幕",
                color =
                  if (subtitleState.selectedTrackId != null) MaterialTheme.colorScheme.primary
                  else Color.White,
              )
            }
            DropdownMenu(
              expanded = subtitleMenu,
              onDismissRequest = { subtitleMenu = false },
              modifier = Modifier.width(330.dp),
            ) {
              DropdownMenuItem(
                text = { Text("关闭字幕") },
                trailingIcon = {
                  Checkbox(checked = subtitleState.selectedTrackId == null, onCheckedChange = null)
                },
                onClick = {
                  subtitleMenu = false
                  onSelectSubtitle(null)
                },
              )
              subtitleState.tracks.forEach { track ->
                DropdownMenuItem(
                  text = { Text(track.displayLabel) },
                  trailingIcon = {
                    Checkbox(
                      checked = subtitleState.selectedTrackId == track.id,
                      onCheckedChange = null,
                    )
                  },
                  onClick = {
                    subtitleMenu = false
                    onSelectSubtitle(track.id)
                  },
                )
              }
              Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  "背景不透明度  ${(subtitleStyle.backgroundOpacity * 100).roundToInt()}%",
                  style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                  value = subtitleStyle.backgroundOpacity,
                  onValueChange = {
                    onSubtitleStyleChange(subtitleStyle.copy(backgroundOpacity = it))
                  },
                  valueRange = 0f..1f,
                  steps = 9,
                )
                Text(
                  "字体不透明度  ${(subtitleStyle.textOpacity * 100).roundToInt()}%",
                  style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                  value = subtitleStyle.textOpacity,
                  onValueChange = { onSubtitleStyleChange(subtitleStyle.copy(textOpacity = it)) },
                  valueRange = .1f..1f,
                  steps = 8,
                )
                Text(
                  "字体大小  ${(subtitleStyle.fontScale * 100).roundToInt()}%",
                  style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                  value = subtitleStyle.fontScale,
                  onValueChange = { onSubtitleStyleChange(subtitleStyle.copy(fontScale = it)) },
                  valueRange = .4f..1.8f,
                  steps = 13,
                )
                Text("字体颜色", style = MaterialTheme.typography.labelMedium)
                Row(
                  Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  SUBTITLE_TEXT_COLORS.forEach { colorValue ->
                    val selected = subtitleStyle.textColor == colorValue
                    Box(
                      Modifier.size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF000000L or colorValue.toLong()))
                        .border(
                          width = if (selected) 3.dp else 1.dp,
                          color =
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                          shape = CircleShape,
                        )
                        .clickable {
                          onSubtitleStyleChange(subtitleStyle.copy(textColor = colorValue))
                        }
                    )
                  }
                }
                Text("字幕对齐", style = MaterialTheme.typography.labelMedium)
                Row(
                  Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                  SubtitleHorizontalPosition.entries.forEach { position ->
                    SubtitlePositionOption(
                      text = position.label,
                      selected = subtitleStyle.horizontalPosition == position,
                      onClick = {
                        onSubtitleStyleChange(subtitleStyle.copy(horizontalPosition = position))
                      },
                    )
                  }
                }
              }
            }
          }
        }
        Box {
          IconButton(onClick = { danmakuMenu = true }, modifier = Modifier.size(38.dp)) {
            DanmakuControlIcon(
              modifier = Modifier.size(23.dp),
              color = if (showDanmaku) Color.White else Color.White.copy(alpha = .48f),
            )
          }
          DropdownMenu(
            expanded = danmakuMenu,
            onDismissRequest = { danmakuMenu = false },
            modifier = Modifier.width(310.dp),
          ) {
            DropdownMenuItem(
              text = { Text(if (showDanmaku) "弹幕已开启" else "弹幕已关闭") },
              trailingIcon = { Checkbox(checked = showDanmaku, onCheckedChange = null) },
              onClick = onToggleDanmaku,
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("智能防挡弹幕")
                  Text(
                    "有蒙版的视频中让人物显示在弹幕前方",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              },
              trailingIcon = {
                Switch(checked = danmakuSmartBlocking, onCheckedChange = null)
              },
              onClick = { onDanmakuSmartBlockingChange(!danmakuSmartBlocking) },
            )
            Column(
              Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Text(
                "显示区域  ${(danmakuDisplayArea * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
              )
              Slider(
                value = danmakuDisplayArea,
                onValueChange = onDanmakuDisplayAreaChange,
                valueRange = .1f..1f,
                steps = 8,
              )
              Text(
                "弹幕密度（左右）  ${danmakuDensityLabel(danmakuDensity)}",
                style = MaterialTheme.typography.labelMedium,
              )
              Slider(
                value = danmakuDensity.toFloat(),
                onValueChange = { onDanmakuDensityChange(it.toInt().coerceIn(1, 5)) },
                valueRange = 1f..5f,
                steps = 3,
              )
              Text(
                "屏蔽等级  ${danmakuBlockLevel.coerceIn(1, 5)}级",
                style = MaterialTheme.typography.labelMedium,
              )
              Slider(
                value = danmakuBlockLevel.toFloat(),
                onValueChange = {
                  onDanmakuBlockLevelChange(it.roundToInt().coerceIn(1, 5))
                },
                valueRange = 1f..5f,
                steps = 3,
              )
              Text(
                "不透明度  ${(danmakuOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
              )
              Slider(
                value = danmakuOpacity,
                onValueChange = onDanmakuOpacityChange,
                valueRange = .2f..1f,
                steps = 7,
              )
              Text(
                "弹幕字号  ${(danmakuFontScale * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
              )
              Slider(
                value = danmakuFontScale,
                onValueChange = onDanmakuFontScaleChange,
                valueRange = .7f..1.5f,
                steps = 7,
              )
              Text(
                "滚动速度  ${"%.1f".format(danmakuSpeed)}×",
                style = MaterialTheme.typography.labelMedium,
              )
              Slider(
                value = danmakuSpeed,
                onValueChange = onDanmakuSpeedChange,
                valueRange = .5f..2f,
                steps = 14,
              )
            }
          }
        }
        if (danmakuComposerEnabled) {
          IconButton(onClick = onComposeDanmaku, modifier = Modifier.size(38.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送弹幕", tint = Color.White)
          }
        }
        IconButton(
          onClick = onFullscreen,
          modifier =
            Modifier.size(38.dp).pointerInteropFilter { event ->
              if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) onFullscreenPress()
              false
            },
        ) {
          FullscreenControlIcon(
            exiting = isFullscreen,
            modifier = Modifier.size(23.dp),
            color = Color.White,
          )
        }
      }
    }
  }
}

/** Center action kept outside the auto-hiding control chrome while playback is paused. */
@Composable
internal fun PlayerCenterPlayPauseButton(
  isPlaying: Boolean,
  onPlayPause: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(onClick = onPlayPause, modifier = modifier.size(68.dp)) {
    Icon(
      imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
      contentDescription = if (isPlaying) "暂停" else "播放",
      modifier = Modifier.size(38.dp),
      tint = Color.White,
    )
  }
}

private val PLAYER_SPEED_OPTIONS = listOf(.5f, 1f, 1.5f, 2f)

private fun danmakuDensityLabel(level: Int): String =
  listOf("疏", "较疏", "标准", "较密", "最密")[level.coerceIn(1, 5) - 1]

internal fun formatPlaybackSpeed(speed: Float): String = "%.1fX".format(speed)

package dev.openbili.webdemo.video

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import kotlin.math.roundToInt

/** 专用播放页共用的弹幕设置面板；参数范围与触屏播放页保持一致。 */
@Composable
internal fun ControllerDanmakuSettingsPanel(
  showDanmaku: Boolean,
  danmakuSmartBlocking: Boolean? = null,
  displayArea: Float,
  density: Int,
  blockLevel: Int? = null,
  opacity: Float,
  fontScale: Float,
  speed: Float,
  speedRange: ClosedFloatingPointRange<Float> = .5f..2f,
  speedSteps: Int = 14,
  blockWordsLabel: String? = null,
  onToggleDanmaku: () -> Unit,
  onDanmakuSmartBlockingChange: ((Boolean) -> Unit)? = null,
  onDisplayAreaChange: (Float) -> Unit,
  onDensityChange: (Int) -> Unit,
  onBlockLevelChange: ((Int) -> Unit)? = null,
  onOpacityChange: (Float) -> Unit,
  onFontScaleChange: (Float) -> Unit,
  onSpeedChange: (Float) -> Unit,
  onBlockWords: (() -> Unit)? = null,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val firstFocusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { firstFocusRequester.requestFocus() } }

  Surface(
    modifier = modifier.fillMaxHeight().widthIn(min = 360.dp, max = 500.dp),
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
        Text("弹幕设置", style = MaterialTheme.typography.titleLarge, color = Color.White)
        TextButton(onClick = onBack) { Text("返回", color = Color.White) }
      }
      Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ControllerDanmakuToggleRow(
          label = if (showDanmaku) "弹幕已开启" else "弹幕已关闭",
          checked = showDanmaku,
          firstFocusRequester = firstFocusRequester,
          onClick = onToggleDanmaku,
        )
        if (danmakuSmartBlocking != null && onDanmakuSmartBlockingChange != null) {
          ControllerDanmakuToggleRow(
            label = "智能防挡弹幕",
            description = "有蒙版的视频中让人物显示在弹幕前方",
            checked = danmakuSmartBlocking,
            switch = true,
            onClick = { onDanmakuSmartBlockingChange(!danmakuSmartBlocking) },
          )
        }
        if (blockWordsLabel != null && onBlockWords != null) {
          TextButton(
            onClick = onBlockWords,
            modifier =
              Modifier.fillMaxWidth()
                .controlFocusOutline(
                  RoundedCornerShape(12.dp),
                  MaterialTheme.colorScheme.primary,
                  width = 3.dp,
                  enabled = true,
                )
                .background(Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp)),
          ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp)) {
              Text("弹幕屏蔽词", color = Color.White)
              Text(
                blockWordsLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = .62f),
              )
            }
          }
        }
        ControllerDanmakuSliderSetting(
          label = "显示区域  ${danmakuPercentLabel(displayArea)}",
          value = displayArea,
          valueRange = .1f..1f,
          steps = 8,
          onValueChange = onDisplayAreaChange,
        )
        ControllerDanmakuSliderSetting(
          label = "弹幕密度（左右）  ${danmakuDensityLabel(density)}",
          value = density.toFloat(),
          valueRange = 1f..5f,
          steps = 3,
          onValueChange = { onDensityChange(it.roundToInt().coerceIn(1, 5)) },
        )
        if (blockLevel != null && onBlockLevelChange != null) {
          ControllerDanmakuSliderSetting(
            label = "屏蔽等级  ${blockLevel.coerceIn(1, 5)}级",
            value = blockLevel.toFloat(),
            valueRange = 1f..5f,
            steps = 3,
            onValueChange = { onBlockLevelChange(it.roundToInt().coerceIn(1, 5)) },
          )
        }
        ControllerDanmakuSliderSetting(
          label = "不透明度  ${danmakuPercentLabel(opacity)}",
          value = opacity,
          valueRange = .2f..1f,
          steps = 7,
          onValueChange = onOpacityChange,
        )
        ControllerDanmakuSliderSetting(
          label = "弹幕字号  ${danmakuPercentLabel(fontScale)}",
          value = fontScale,
          valueRange = .7f..1.5f,
          steps = 7,
          onValueChange = onFontScaleChange,
        )
        ControllerDanmakuSliderSetting(
          label = "滚动速度  ${"%.1f".format(speed)}×",
          value = speed,
          valueRange = speedRange,
          steps = speedSteps,
          onValueChange = onSpeedChange,
        )
      }
    }
  }
}

@Composable
private fun ControllerDanmakuToggleRow(
  label: String,
  checked: Boolean,
  onClick: () -> Unit,
  firstFocusRequester: FocusRequester? = null,
  description: String? = null,
  switch: Boolean = false,
) {
  TextButton(
    onClick = onClick,
    modifier =
      Modifier.fillMaxWidth()
        .then(if (firstFocusRequester != null) Modifier.focusRequester(firstFocusRequester) else Modifier)
        .controlFocusOutline(
          RoundedCornerShape(12.dp),
          MaterialTheme.colorScheme.primary,
          width = 3.dp,
          enabled = true,
        )
        .background(Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp)),
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(label, color = Color.White)
        description?.let {
          Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .62f))
        }
      }
      if (switch) Switch(checked = checked, onCheckedChange = null)
      else Checkbox(checked = checked, onCheckedChange = null)
    }
  }
}

@Composable
private fun ControllerDanmakuSliderSetting(
  label: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  steps: Int,
  onValueChange: (Float) -> Unit,
) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    ControlMenuSlider(
      value = value.coerceIn(valueRange.start, valueRange.endInclusive),
      onValueChange = onValueChange,
      valueRange = valueRange,
      steps = steps,
      controlEnabled = true,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

internal fun danmakuDensityLabel(level: Int): String =
  listOf("疏", "较疏", "标准", "较密", "最密")[level.coerceIn(1, 5) - 1]

internal fun danmakuPercentLabel(value: Float): String = "${(value * 100f).roundToInt()}%"

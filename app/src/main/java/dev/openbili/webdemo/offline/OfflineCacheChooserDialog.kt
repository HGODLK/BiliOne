package dev.openbili.webdemo.offline

/**
 * 离线缓存选择的确认弹窗。
 *
 * 用户触发“缓存”后，通过此弹窗选择要缓存的分 P / 剧集、清晰度，以及是否一并保存弹幕
 * 与字幕；确认后把带完整参数的 [OfflineMediaRequest] 列表回传给调用方入队下载。弹窗会
 * 提前过滤掉已在缓存列表中的目标，并对“需要大会员但当前无有效授权”的选择给出阻断提示，
 * 避免入队注定失败的任务。
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.VideoStream
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.controlFocusOutline

/**
 * 渲染离线缓存选择弹窗，收集用户选择后通过 [onConfirm] 回传入队请求。
 *
 * 内部主要分节：
 *  - 推导可选/已存在目标、已选集合与“全选”判定，并算出会员阻断状态；
 *  - 控制器模式下把初始焦点交给“取消”按钮，保证遥控/键盘可用；
 *  - 分 P / 剧集多选（已存在项置灰）、清晰度筛选、弹幕/字幕开关三段表单；
 *  - 确认按钮在无有效选择、无清晰度或受会员阻断时禁用。
 */
@Composable
fun OfflineCacheChooserDialog(
  title: String,
  targets: List<OfflineMediaRequest>,
  streams: List<VideoStream>,
  existingTargetIds: Set<String>,
  premiumAvailable: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (List<OfflineMediaRequest>) -> Unit,
) {
  // ── 推导选择状态：可选目标、已选集合、全选判定与会员阻断 ──────────
  val targetIds = remember(targets) { targets.map(::requestSelectionId) }
  val selectableTargetIds =
    remember(targetIds, existingTargetIds) { targetIds.filterNot(existingTargetIds::contains) }
  var selectedTargetIds by
    remember(selectableTargetIds) {
      mutableStateOf(selectableTargetIds.firstOrNull()?.let(::setOf).orEmpty())
    }
  var selectedQualityId by remember(streams) { mutableStateOf(streams.firstOrNull()?.id ?: 0) }
  var includeDanmaku by remember { mutableStateOf(true) }
  var includeSubtitles by remember { mutableStateOf(true) }
  val selectedTargets =
    remember(targets, selectedTargetIds, existingTargetIds) {
      targets.filter {
        val id = requestSelectionId(it)
        id in selectedTargetIds && id !in existingTargetIds
      }
    }
  val allSelected =
    selectableTargetIds.isNotEmpty() && selectedTargetIds.size == selectableTargetIds.size
  // 需要会员授权的目标中只要有一个未解锁（当前账号无有效大会员），就整体阻断确认
  val premiumBlocked = selectedTargets.any(OfflineMediaRequest::requiresVip) && !premiumAvailable
  val controlMode = LocalControlMode.current
  val dismissFocusRequester = remember { FocusRequester() }

  // ── 控制器模式：首帧后把焦点交给“取消”按钮，避免遥控进入弹窗后无落点 ──────────
  LaunchedEffect(controlMode, title) {
    if (controlMode) {
      androidx.compose.runtime.withFrameNanos {}
      runCatching { dismissFocusRequester.requestFocus() }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("缓存视频") },
    text = {
      Column(
        modifier =
          Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        // 所有目标都已在缓存列表中时，仅提示用户去“缓存视频”页管理
        if (selectableTargetIds.isEmpty()) {
          Text(
            "这些内容已经在缓存列表中，请在“缓存视频”页继续或管理现有任务。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        // ── 分 P / 剧集多选：已存在项置灰且不可勾选 ──────────
        if (targets.size > 1) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("选择分 P / 剧集", style = MaterialTheme.typography.labelLarge)
            TextButton(
              onClick = {
                selectedTargetIds = if (allSelected) emptySet() else selectableTargetIds.toSet()
              },
              modifier =
                Modifier.controlFocusOutline(
                  shape = RoundedCornerShape(20.dp),
                  color = MaterialTheme.colorScheme.primary,
                ),
            ) {
              Text(if (allSelected) "全不选" else "全选")
            }
          }
          targets.forEach { target ->
            val targetId = requestSelectionId(target)
            val checked = targetId in selectedTargetIds
            val alreadyExists = targetId in existingTargetIds
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .controlFocusOutline(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                  )
                  .clickable(enabled = !alreadyExists) {
                    if (!alreadyExists) {
                      selectedTargetIds =
                        if (checked) selectedTargetIds - targetId else selectedTargetIds + targetId
                    }
                  }
                  .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = checked,
                enabled = !alreadyExists,
                onCheckedChange = { enabled ->
                  selectedTargetIds =
                    if (enabled) selectedTargetIds + targetId else selectedTargetIds - targetId
                },
              )
              Text(
                buildString {
                  append(target.partTitle.ifBlank { "P${target.pageNumber}" })
                  if (alreadyExists) append(" · 已缓存或已在任务中")
                },
                color =
                  if (alreadyExists) MaterialTheme.colorScheme.onSurfaceVariant
                  else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
        // ── 清晰度筛选：仅展示接口返回的清晰度，去重后以 FilterChip 单选 ──────────
        Text("选择清晰度", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          streams.distinctBy(VideoStream::id).forEach { stream ->
            FilterChip(
              selected = selectedQualityId == stream.id,
              onClick = { selectedQualityId = stream.id },
              label = { Text(stream.quality) },
              modifier =
                Modifier.controlFocusOutline(
                  shape = RoundedCornerShape(20.dp),
                  color = MaterialTheme.colorScheme.primary,
                ),
            )
          }
        }
        // ── 弹幕 / 字幕开关 ──────────
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = includeDanmaku, onCheckedChange = { includeDanmaku = it })
          Text("一并保存弹幕")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = includeSubtitles, onCheckedChange = { includeSubtitles = it })
          Text("一并保存字幕")
        }
        // ── 缓存范围说明与会员阻断提示 ──────────
        Text(
          "视频、音频、标题和封面会保存在应用私有缓存中，不能导出。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (premiumBlocked) {
          Text(
            "缓存番剧影视需要当前账号具有有效大会员；缓存仍会绑定账号和会员授权。",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier =
          Modifier.focusRequester(dismissFocusRequester)
            .controlFocusOutline(
              shape = RoundedCornerShape(20.dp),
              color = MaterialTheme.colorScheme.primary,
            ),
      ) {
        Text("取消")
      }
    },
    confirmButton = {
      Button(
        enabled =
          selectedTargets.isNotEmpty() &&
            selectedQualityId > 0 &&
            streams.isNotEmpty() &&
            !premiumBlocked,
        onClick = {
          onConfirm(
            selectedTargets.map { target ->
              target.copy(
                qualityId = selectedQualityId,
                qualityLabel =
                  streams.firstOrNull { it.id == selectedQualityId }?.quality.orEmpty(),
                includeDanmaku = includeDanmaku,
                includeSubtitles = includeSubtitles,
              )
            }
          )
        },
        modifier =
          Modifier.controlFocusOutline(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary,
          ),
      ) {
        Text("缓存（${selectedTargets.size}）")
      }
    },
  )
}

/**
 * 计算请求在弹窗内的去重标识：同一视频/番剧的不同分 P 共享相同选择键，
 * 用于判定该目标是否已在缓存列表或已在任务中。
 */
private fun requestSelectionId(request: OfflineMediaRequest): String =
  offlineMediaId(request.kind, request.bvid, request.cid, request.episodeId)

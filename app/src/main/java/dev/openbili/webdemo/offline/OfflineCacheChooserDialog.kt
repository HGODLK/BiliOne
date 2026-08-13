package dev.openbili.webdemo.offline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.VideoStream

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
  val targetIds = remember(targets) { targets.map(::requestSelectionId) }
  val selectableTargetIds =
    remember(targetIds, existingTargetIds) { targetIds.filterNot(existingTargetIds::contains) }
  var selectedTargetIds by
    remember(selectableTargetIds) {
      mutableStateOf(selectableTargetIds.firstOrNull()?.let(::setOf).orEmpty())
    }
  var selectedQualityId by
    remember(streams) { mutableStateOf(streams.firstOrNull()?.id ?: 0) }
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
  val premiumBlocked = selectedTargets.any(OfflineMediaRequest::requiresVip) && !premiumAvailable

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("缓存视频") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (selectableTargetIds.isEmpty()) {
          Text(
            "这些内容已经在缓存列表中，请在“缓存视频”页继续或管理现有任务。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
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
              }
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
        Text("选择清晰度", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          streams.distinctBy(VideoStream::id).forEach { stream ->
            FilterChip(
              selected = selectedQualityId == stream.id,
              onClick = { selectedQualityId = stream.id },
              label = { Text(stream.quality) },
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = includeDanmaku, onCheckedChange = { includeDanmaku = it })
          Text("一并保存弹幕")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = includeSubtitles, onCheckedChange = { includeSubtitles = it })
          Text("一并保存字幕")
        }
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
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
      ) {
        Text("缓存（${selectedTargets.size}）")
      }
    },
  )
}

private fun requestSelectionId(request: OfflineMediaRequest): String =
  offlineMediaId(request.kind, request.bvid, request.cid, request.episodeId)

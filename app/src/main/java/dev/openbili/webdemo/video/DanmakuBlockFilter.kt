package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.DanmakuItem

internal const val DANMAKU_SEGMENT_DURATION_MS = 6 * 60 * 1_000L
private const val DANMAKU_BLOCK_GROUP_SIZE = 5

/**
 * 在每个服务端分段内独立应用五个按需屏蔽等级。
 *
 * 本地弹幕永不过滤。远端弹幕在每个六分钟分段内稳定排序，每五条连续条目按等级
 * 1..5 保留 5/4/3/2/1 条：这让选择在活动窗口变化之间保持确定，不依赖服务端
 * 质量元数据。
 */
internal fun filterDanmakuByBlockLevel(
  items: List<DanmakuItem>,
  level: Int,
): List<DanmakuItem> {
  val normalizedLevel = level.coerceIn(1, 5)
  if (normalizedLevel == 1 || items.isEmpty()) return items
  val retainedPerGroup = DANMAKU_BLOCK_GROUP_SIZE + 1 - normalizedLevel
  val retainedIndices = HashSet<Int>()
  items
    .withIndex()
    .filterNot { it.value.isLocal }
    .groupBy { it.value.timeMs.coerceAtLeast(0L) / DANMAKU_SEGMENT_DURATION_MS }
    .values
    .forEach { segment ->
      segment
        .sortedWith(
          compareBy<IndexedValue<DanmakuItem>> { it.value.timeMs }
            .thenBy { it.value.sourceId.orEmpty() }
            .thenBy { it.index }
        )
        .forEachIndexed { ordinal, indexed ->
          if (ordinal % DANMAKU_BLOCK_GROUP_SIZE < retainedPerGroup) {
            retainedIndices += indexed.index
          }
        }
    }
  return items.filterIndexed { index, item -> item.isLocal || index in retainedIndices }
}

package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.DanmakuItem

internal const val DANMAKU_SEGMENT_DURATION_MS = 6 * 60 * 1_000L
private const val DANMAKU_BLOCK_GROUP_SIZE = 5

/**
 * Applies the five point-on-demand blocking levels independently inside every server segment.
 *
 * Local danmaku is never filtered. Remote danmaku is ordered stably inside each six-minute segment
 * and every five consecutive items retain 5/4/3/2/1 items for levels 1..5. This keeps selection
 * deterministic across active-window changes without depending on server quality metadata.
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

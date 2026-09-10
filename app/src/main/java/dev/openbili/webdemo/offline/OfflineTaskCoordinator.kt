package dev.openbili.webdemo.offline

/**
 * 按视频任务而不是音视频轨道控制缓存并发。
 *
 * 调度器只负责决定哪些视频可以进入活动槽位，播放地址、附属文件和 Media3 轨道仍由
 * [OfflineMediaManager] 执行。这样一个视频即使包含两条轨道，也只占用一个视频槽位。
 */
internal class OfflineTaskCoordinator(
  private val maxActiveTasks: Int,
  private val loadEntries: () -> List<OfflineMediaEntry>,
  private val isTerminal: (OfflineMediaEntry) -> Boolean,
  private val startTask: (OfflineMediaEntry) -> Unit,
) {
  private val lock = Any()
  private val activeIds = linkedSetOf<String>()

  fun enqueue() {
    pump()
  }

  fun recover() {
    synchronized(lock) { activeIds.clear() }
    pump()
  }

  fun onTaskStateChanged() {
    pump()
  }

  fun onUserPause(id: String) {
    synchronized(lock) { activeIds.remove(id) }
    pump()
  }

  fun onUserResume() {
    pump()
  }

  fun onRemoved(id: String) {
    synchronized(lock) { activeIds.remove(id) }
    pump()
  }

  fun isActive(id: String): Boolean = synchronized(lock) { id in activeIds }

  private fun pump() {
    val starts = mutableListOf<OfflineMediaEntry>()
    synchronized(lock) {
      val entries = loadEntries()
      val entriesById = entries.associateBy(OfflineMediaEntry::id)
      activeIds.removeAll { id ->
        val entry = entriesById[id]
        entry == null || isIneligible(entry) || isTerminal(entry)
      }
      val ordered = entries.sortedWith(::compareQueueOrder)
      ordered.forEach { entry ->
        if (activeIds.size >= maxActiveTasks) return@forEach
        if (entry.id !in activeIds && !isIneligible(entry) && !isTerminal(entry)) {
          activeIds += entry.id
          starts += entry
        }
      }
    }
    starts.forEach(startTask)
  }

  private fun isIneligible(entry: OfflineMediaEntry): Boolean =
    entry.entitlementState == OfflineEntitlementState.REVOKED ||
      entry.entitlementState == OfflineEntitlementState.LOCKED ||
      entry.pausedByUser ||
      entry.preparationPaused ||
      entry.preparationError.isNotBlank()

  private fun compareQueueOrder(
    left: OfflineMediaEntry,
    right: OfflineMediaEntry,
  ): Int {
    val leftSequence = left.queueSequence
    val rightSequence = right.queueSequence
    return when {
      leftSequence > 0L && rightSequence > 0L ->
        compareValuesBy(left, right, OfflineMediaEntry::queueSequence)
      leftSequence > 0L -> 1
      rightSequence > 0L -> -1
      else ->
        compareValuesBy(
          left,
          right,
          OfflineMediaEntry::createdAtMs,
          OfflineMediaEntry::pageNumber,
          OfflineMediaEntry::id,
        )
    }
  }
}

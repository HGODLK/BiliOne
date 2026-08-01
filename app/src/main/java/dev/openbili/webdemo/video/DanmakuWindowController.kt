package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.DanmakuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DanmakuWindowController(
  private val loadSegment: (Long, Int) -> List<DanmakuItem> = BiliApi::getDanmakuSegment
) {
  private var activeCid = 0L
  private var activeCenterSegment = -1
  private var activeIndices = emptyList<Int>()
  private var loadedSegments = emptyMap<Int, List<DanmakuItem>>()
  private var historicalItems = emptyList<DanmakuItem>()
  private val localItemsByCid = LinkedHashMap<Long, MutableList<DanmakuItem>>()

  suspend fun monitor(
    cid: Long,
    durationSeconds: Long,
    initialPositionMs: Long,
    positionProvider: () -> Long,
    onWindowChanged: (List<DanmakuItem>) -> Unit,
  ): Nothing = coroutineScope {
    activate(cid)
    var firstSample = true
    var observedSegment = -1
    var generation = 0L
    var loadingJob: Job? = null
    while (currentCoroutineContext().isActive) {
      val positionMs =
        if (firstSample) initialPositionMs.coerceAtLeast(0L)
        else positionProvider().coerceAtLeast(0L)
      firstSample = false
      val nextSegment = danmakuSegmentIndexAt(positionMs, durationSeconds)
      if (nextSegment != observedSegment) {
        observedSegment = nextSegment
        generation += 1L
        val requestGeneration = generation
        loadingJob?.cancel()
        loadingJob = launch {
          val indices = danmakuWindowSegmentIndices(nextSegment, durationSeconds)
          activeCenterSegment = nextSegment
          activeIndices = indices
          loadedSegments = loadedSegments.filterKeys(indices::contains)

          val centerItems =
            withContext(Dispatchers.IO) {
              runCatching { loadSegment(cid, nextSegment) }.getOrDefault(emptyList())
            }
          if (requestGeneration != generation || activeCid != cid) return@launch
          loadedSegments = loadedSegments + (nextSegment to centerItems)
          onWindowChanged(buildWindow())

          val neighbors =
            indices
              .filter { it != nextSegment }
              .map { index ->
                async(Dispatchers.IO) {
                  index to runCatching { loadSegment(cid, index) }.getOrDefault(emptyList())
                }
              }
              .awaitAll()
          if (requestGeneration != generation || activeCid != cid) return@launch
          loadedSegments = loadedSegments + neighbors
          onWindowChanged(buildWindow())
        }
      }
      delay(POSITION_SAMPLE_INTERVAL_MS)
    }
    @Suppress("UNREACHABLE_CODE") error("Danmaku monitor completed unexpectedly")
  }

  fun setHistoricalDanmaku(cid: Long, items: List<DanmakuItem>): List<DanmakuItem>? {
    activate(cid)
    historicalItems = items
    return buildWindow().takeIf { activeCenterSegment >= 1 }
  }

  fun clearHistoricalDanmaku(cid: Long): List<DanmakuItem>? {
    if (activeCid != cid || historicalItems.isEmpty()) return null
    historicalItems = emptyList()
    return buildWindow().takeIf { activeCenterSegment >= 1 }
  }

  fun addLocalDanmaku(cid: Long, item: DanmakuItem): List<DanmakuItem>? {
    val items = localItemsByCid.getOrPut(cid) { mutableListOf() }
    items += item
    while (items.size > MAX_LOCAL_DANMAKU_PER_VIDEO) items.removeAt(0)
    while (localItemsByCid.size > MAX_LOCAL_VIDEO_CACHE) {
      localItemsByCid.remove(localItemsByCid.keys.first())
    }
    return buildWindow().takeIf { activeCid == cid && activeCenterSegment >= 1 }
  }

  fun seedLocalDanmaku(cid: Long, items: List<DanmakuItem>) {
    val local = items.filter(DanmakuItem::isLocal)
    if (local.isEmpty()) return
    val retained = localItemsByCid.getOrPut(cid) { mutableListOf() }
    local.forEach { item ->
      if (retained.none { existing -> existing.windowIdentity() == item.windowIdentity() }) {
        retained += item
      }
    }
    while (retained.size > MAX_LOCAL_DANMAKU_PER_VIDEO) retained.removeAt(0)
  }

  private fun activate(cid: Long) {
    if (activeCid == cid) return
    activeCid = cid
    activeCenterSegment = -1
    activeIndices = emptyList()
    loadedSegments = emptyMap()
    historicalItems = emptyList()
  }

  private fun buildWindow(): List<DanmakuItem> {
    if (activeCenterSegment < 1 || activeIndices.isEmpty()) return emptyList()
    val startMs = (activeIndices.first() - 1L) * DANMAKU_SEGMENT_DURATION_MS
    val endExclusiveMs = activeIndices.last().toLong() * DANMAKU_SEGMENT_DURATION_MS
    val candidates =
      activeIndices.flatMap { loadedSegments[it].orEmpty() } +
        historicalItems.filter { it.timeMs in startMs until endExclusiveMs } +
        localItemsByCid[activeCid].orEmpty().filter {
          it.timeMs in startMs until endExclusiveMs
        }
    return candidates.distinctBy(DanmakuItem::windowIdentity).sortedBy(DanmakuItem::timeMs)
  }

  private companion object {
    const val POSITION_SAMPLE_INTERVAL_MS = 250L
    const val MAX_LOCAL_DANMAKU_PER_VIDEO = 256
    const val MAX_LOCAL_VIDEO_CACHE = 16
  }
}

internal fun danmakuSegmentIndexAt(positionMs: Long, durationSeconds: Long): Int {
  val maximum =
    if (durationSeconds > 0L) {
      ((durationSeconds * 1_000L + DANMAKU_SEGMENT_DURATION_MS - 1L) / DANMAKU_SEGMENT_DURATION_MS)
        .toInt()
        .coerceAtLeast(1)
    } else {
      Int.MAX_VALUE
    }
  return (positionMs.coerceAtLeast(0L) / DANMAKU_SEGMENT_DURATION_MS + 1L)
    .coerceAtMost(maximum.toLong())
    .coerceAtMost(Int.MAX_VALUE.toLong())
    .toInt()
}

internal fun danmakuWindowSegmentIndices(
  centerSegment: Int,
  durationSeconds: Long,
): List<Int> {
  val maximum =
    if (durationSeconds > 0L) {
      ((durationSeconds * 1_000L + DANMAKU_SEGMENT_DURATION_MS - 1L) / DANMAKU_SEGMENT_DURATION_MS)
        .toInt()
        .coerceAtLeast(1)
    } else {
      centerSegment + 1
    }
  return ((centerSegment - 1).coerceAtLeast(1)..(centerSegment + 1).coerceAtMost(maximum)).toList()
}

private fun DanmakuItem.windowIdentity(): String =
  sourceId?.takeIf(String::isNotBlank)
    ?: "$timeMs\u0000$type\u0000$fontSize\u0000$color\u0000$content"

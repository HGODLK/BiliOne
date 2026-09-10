package dev.openbili.webdemo.offline

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineTaskCoordinatorTest {
  @Test
  fun startsAtMostFiveVideosAndFillsTheNextSlotInQueueOrder() {
    val entries = (1L..7L).map { id -> entry("video-$id", id) }
    val terminalIds = mutableSetOf<String>()
    val startedIds = mutableListOf<String>()
    val coordinator =
      OfflineTaskCoordinator(
        maxActiveTasks = 5,
        loadEntries = { entries },
        isTerminal = { it.id in terminalIds },
        startTask = { startedIds += it.id },
      )

    coordinator.enqueue()
    assertEquals(
      listOf("video-1", "video-2", "video-3", "video-4", "video-5"),
      startedIds,
    )

    terminalIds += "video-1"
    coordinator.onTaskStateChanged()

    assertEquals(
      listOf("video-1", "video-2", "video-3", "video-4", "video-5", "video-6"),
      startedIds,
    )
  }

  @Test
  fun pausedVideoDoesNotOccupyAnActiveSlot() {
    val entries =
      listOf(
        entry("video-1", 1L, paused = true),
        entry("video-2", 2L),
      )
    val startedIds = mutableListOf<String>()
    val coordinator =
      OfflineTaskCoordinator(
        maxActiveTasks = 1,
        loadEntries = { entries },
        isTerminal = { false },
        startTask = { startedIds += it.id },
      )

    coordinator.enqueue()

    assertEquals(listOf("video-2"), startedIds)
  }

  private fun entry(id: String, sequence: Long, paused: Boolean = false) =
    OfflineMediaEntry(
      id = id,
      kind = OfflineMediaKind.VIDEO,
      accountMid = 1L,
      title = id,
      partTitle = "",
      coverUrl = "",
      bvid = "BV$id",
      aid = 1L,
      cid = 1L,
      pageNumber = 1,
      durationMs = 1_000L,
      qualityId = 80,
      qualityLabel = "1080P",
      queueSequence = sequence,
      pausedByUser = paused,
    )
}

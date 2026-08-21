package dev.openbili.webdemo.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRoomRecommendationLoaderTest {
  @Test
  fun exactAreaResultsComeFirstAndParentAreaFillsTheRemainder() {
    val result =
      LiveRoomRecommendationLoader.select(
        currentRoomId = 100L,
        exactAreaRooms =
          listOf(
            room(100L, liveStatus = 1),
            room(101L, liveStatus = 1),
            room(102L, liveStatus = 0),
          ),
        parentAreaRooms =
          listOf(
            room(101L, liveStatus = 1),
            room(103L, liveStatus = 1),
            room(104L, liveStatus = 1),
          ),
        limit = 3,
      )

    assertEquals(listOf(101L, 103L, 104L), result.map { it.roomId })
  }

  @Test
  fun selectionKeepsServerOrderAndRespectsLimit() {
    val result =
      LiveRoomRecommendationLoader.select(
        currentRoomId = 999L,
        exactAreaRooms = listOf(room(3L), room(1L), room(2L)),
        limit = 2,
      )

    assertEquals(listOf(3L, 1L), result.map { it.roomId })
  }

  private fun room(roomId: Long, liveStatus: Int = 1) =
    LiveSearchRoom(
      roomId = roomId,
      uid = roomId + 10_000L,
      title = "直播 $roomId",
      uname = "主播 $roomId",
      liveStatus = liveStatus,
    )
}

package dev.openbili.webdemo.live

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LiveHistoryStoreTest {
  private val context
    get() = RuntimeEnvironment.getApplication()

  @Before
  fun clearStore() {
    context.getSharedPreferences("live_history", 0).edit().clear().commit()
  }

  @Test
  fun historyIsSeparatedByAccount() {
    LiveHistoryStore.record(context, 100, room(1, "账号甲直播"))
    LiveHistoryStore.record(context, 200, room(2, "账号乙直播"))

    assertEquals(listOf(1L), LiveHistoryStore.read(context, 100).map { it.room.roomId })
    assertEquals(listOf(2L), LiveHistoryStore.read(context, 200).map { it.room.roomId })
    assertEquals(emptyList<StoredLiveHistory>(), LiveHistoryStore.read(context, 300))
  }

  private fun room(roomId: Long, title: String) =
    LiveSearchRoom(roomId = roomId, uid = roomId + 100, title = title, uname = "主播")
}

package dev.openbili.webdemo.live

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoredLiveHistory(
  val room: LiveSearchRoom,
  val viewedAt: Long,
)

object LiveHistoryStore {
  private const val PREFERENCES = "live_history"
  private const val KEY_ITEMS = "items"
  private const val MAX_ITEMS = 100

  @Synchronized
  fun record(
    context: Context,
    room: LiveSearchRoom,
    viewedAt: Long = System.currentTimeMillis() / 1_000L,
  ) {
    if (room.roomId <= 0L) return
    val merged =
      (listOf(StoredLiveHistory(room, viewedAt)) + read(context))
        .distinctBy { it.room.roomId }
        .take(MAX_ITEMS)
    val value =
      JSONArray().apply {
        merged.forEach { item ->
          put(
            JSONObject()
              .put("room_id", item.room.roomId)
              .put("short_room_id", item.room.shortRoomId)
              .put("uid", item.room.uid)
              .put("title", item.room.title)
              .put("uname", item.room.uname)
              .put("face", item.room.faceUrl)
              .put("cover", item.room.coverUrl)
              .put("keyframe", item.room.keyframeUrl)
              .put("area_name", item.room.areaName)
              .put("parent_area_name", item.room.parentAreaName)
              .put("watched_text", item.room.watchedText)
              .put("live_status", item.room.liveStatus)
              .put("viewed_at", item.viewedAt)
          )
        }
      }
    context
      .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_ITEMS, value.toString())
      .apply()
  }

  @Synchronized
  fun read(context: Context): List<StoredLiveHistory> {
    val raw =
      context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_ITEMS, null)
        ?: return emptyList()
    val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
          val item = array.optJSONObject(index) ?: continue
          val roomId = item.optLong("room_id")
          if (roomId <= 0L) continue
          add(
            StoredLiveHistory(
              room =
                LiveSearchRoom(
                  roomId = roomId,
                  shortRoomId = item.optLong("short_room_id").takeIf { it > 0L },
                  uid = item.optLong("uid"),
                  title = item.optString("title").ifBlank { "直播间 $roomId" },
                  uname = item.optString("uname").ifBlank { "主播" },
                  faceUrl = item.nullableString("face"),
                  coverUrl = item.nullableString("cover"),
                  keyframeUrl = item.nullableString("keyframe"),
                  areaName = item.nullableString("area_name"),
                  parentAreaName = item.nullableString("parent_area_name"),
                  watchedText = item.nullableString("watched_text"),
                  liveStatus = item.optInt("live_status"),
                ),
              viewedAt = item.optLong("viewed_at"),
            )
          )
        }
      }
      .sortedByDescending(StoredLiveHistory::viewedAt)
      .take(MAX_ITEMS)
  }

  private fun JSONObject.nullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)
}

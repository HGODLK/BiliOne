package dev.openbili.webdemo.live

/**
 * 直播间内的分区推荐加载器。
 *
 * 推荐来源是当前直播间所在的直播分区，而不是首页 Hero 或账号关注列表。二级分区有足够
 * 房间时只使用二级分区；房间较少时再从同一一级分区补齐，保持推荐内容与当前直播间相关。
 */
object LiveRoomRecommendationLoader {
  private const val REQUEST_PAGE_SIZE = 30

  /** 从当前直播间的分区加载推荐房间。 */
  fun load(room: LiveRoomInfo, limit: Int = 12): List<LiveSearchRoom> {
    val safeLimit = limit.coerceAtLeast(1)
    if (room.parentAreaId <= 0) {
      throw IllegalStateException("当前直播间分区信息不可用")
    }

    val exactAreaRooms =
      BiliLiveApi.getLiveRooms(
          parentAreaId = room.parentAreaId,
          areaId = room.areaId,
          pageSize = REQUEST_PAGE_SIZE,
        )
        .rooms
    val exactAreaRecommendations =
      select(
        currentRoomId = room.roomId,
        exactAreaRooms = exactAreaRooms,
        limit = safeLimit,
      )
    if (exactAreaRecommendations.size >= safeLimit || room.areaId <= 0) {
      return exactAreaRecommendations
    }

    val parentAreaRooms =
      BiliLiveApi.getLiveRooms(
          parentAreaId = room.parentAreaId,
          areaId = 0,
          pageSize = REQUEST_PAGE_SIZE,
        )
        .rooms
    return select(
      currentRoomId = room.roomId,
      exactAreaRooms = exactAreaRooms,
      parentAreaRooms = parentAreaRooms,
      limit = safeLimit,
    )
  }

  /** 合并分区结果，保持接口返回顺序并去除当前房间、离线房间和重复房间。 */
  internal fun select(
    currentRoomId: Long,
    exactAreaRooms: List<LiveSearchRoom>,
    parentAreaRooms: List<LiveSearchRoom> = emptyList(),
    limit: Int = 12,
  ): List<LiveSearchRoom> {
    val safeLimit = limit.coerceAtLeast(1)
    return buildList {
      val seenRoomIds = HashSet<Long>()
      sequenceOf(exactAreaRooms, parentAreaRooms)
        .flatten()
        .forEach { candidate ->
          if (
            size < safeLimit &&
              candidate.roomId > 0L &&
              candidate.roomId != currentRoomId &&
              candidate.liveStatus == 1 &&
              seenRoomIds.add(candidate.roomId)
          ) {
            add(candidate)
          }
        }
    }
  }
}

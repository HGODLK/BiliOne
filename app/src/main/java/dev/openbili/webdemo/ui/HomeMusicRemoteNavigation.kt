package dev.openbili.webdemo.ui

/** 音乐卡收到控制器确认键时应执行的动作。 */
internal enum class MusicCardConfirmAction {
  PLAY,
  OPEN_DELETE_ACTIONS,
}

/** 控制器不使用双击或长按计时：确认非当前曲目负责播放，再次确认当前曲目才打开删除层。 */
internal fun resolveMusicCardConfirmAction(
  itemId: String,
  currentItemId: String?,
): MusicCardConfirmAction =
  if (itemId == currentItemId) MusicCardConfirmAction.OPEN_DELETE_ACTIONS
  else MusicCardConfirmAction.PLAY

/** 收藏区收到右方向键时的动作。 */
internal enum class MusicLibraryRightAction {
  KEEP_TRANSIENT,
  FOCUS_PLAY_PAUSE,
  COLLAPSE_AND_FOCUS_PLAY_PAUSE,
}

internal fun resolveMusicLibraryRightAction(
  wideLayout: Boolean,
  libraryCollapsed: Boolean,
  transientOpen: Boolean,
): MusicLibraryRightAction =
  when {
    transientOpen -> MusicLibraryRightAction.KEEP_TRANSIENT
    wideLayout && !libraryCollapsed -> MusicLibraryRightAction.COLLAPSE_AND_FOCUS_PLAY_PAUSE
    else -> MusicLibraryRightAction.FOCUS_PLAY_PAUSE
  }

/** 高级音质收到右方向键时的收藏区动作。 */
internal enum class MusicAdvancedAudioRightAction {
  FOCUS_LIBRARY,
  EXPAND_AND_FOCUS_LIBRARY,
}

internal fun resolveMusicAdvancedAudioRightAction(
  wideLayout: Boolean,
  libraryCollapsed: Boolean,
): MusicAdvancedAudioRightAction =
  if (wideLayout && libraryCollapsed) {
    MusicAdvancedAudioRightAction.EXPAND_AND_FOCUS_LIBRARY
  } else {
    MusicAdvancedAudioRightAction.FOCUS_LIBRARY
  }

/** 播放状态改变时保留原曲目焦点，仅在条目消失后使用当前曲目或首项兜底。 */
internal fun resolveMusicTrackFocusId(
  lastFocusedItemId: String?,
  currentItemId: String?,
  itemIds: List<String>,
): String? =
  lastFocusedItemId?.takeIf(itemIds::contains)
    ?: currentItemId?.takeIf(itemIds::contains)
    ?: itemIds.firstOrNull()

/** 删除焦点项后优先保持原列表位置，其次退回前一项。 */
internal fun musicFocusIndexAfterRemoval(
  removedIndex: Int,
  remainingItemCount: Int,
): Int? {
  if (remainingItemCount <= 0) return null
  return removedIndex.coerceIn(0, remainingItemCount - 1)
}

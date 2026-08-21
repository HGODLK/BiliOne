package dev.openbili.webdemo

import android.content.Context
import kotlin.math.max

/** 远端历史迟到或不可用时使用的持久播放位置。 */
internal object PlaybackProgressStore {
  private const val PREFS_NAME = "playback_progress"
  private const val POSITION_SUFFIX = ":position"
  private const val DURATION_SUFFIX = ":duration"
  private const val UPDATED_SUFFIX = ":updated"
  private const val RETENTION_MS = 3L * 24L * 60L * 60L * 1000L

  fun save(
    context: Context,
    aid: Long,
    cid: Long,
    positionMs: Long,
    durationMs: Long,
  ) {
    if (aid <= 0L || cid <= 0L) return
    val key = key(aid, cid)
    context
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putLong(key + POSITION_SUFFIX, positionMs.coerceAtLeast(0L))
      .putLong(key + DURATION_SUFFIX, durationMs.coerceAtLeast(0L))
      .putLong(key + UPDATED_SUFFIX, System.currentTimeMillis())
      .apply()
  }

  fun read(context: Context, aid: Long, cid: Long, durationMs: Long): Long {
    if (aid <= 0L || cid <= 0L) return 0L
    val key = key(aid, cid)
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val updatedAt = prefs.getLong(key + UPDATED_SUFFIX, 0L)
    if (updatedAt <= 0L || System.currentTimeMillis() - updatedAt > RETENTION_MS) {
      prefs
        .edit()
        .remove(key + POSITION_SUFFIX)
        .remove(key + DURATION_SUFFIX)
        .remove(key + UPDATED_SUFFIX)
        .apply()
      return 0L
    }
    val position = prefs.getLong(key + POSITION_SUFFIX, 0L).coerceAtLeast(0L)
    val knownDuration = max(durationMs, prefs.getLong(key + DURATION_SUFFIX, 0L))
    return normalize(position, knownDuration)
  }

  fun normalize(positionMs: Long, durationMs: Long): Long {
    val position = positionMs.coerceAtLeast(0L)
    if (durationMs <= 0L) return position
    // 接近结尾仍是有效的退出位置。实际播放完成由页面缓存单独跟踪，因此在这里把
    // 最后 2% 当作已完成，会在用户于媒体真正结束前退出时丢失本地和服务器的续播进度。
    return position.coerceAtMost(durationMs)
  }

  private fun key(aid: Long, cid: Long) = "$aid:$cid"
}

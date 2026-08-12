package dev.openbili.webdemo

import android.content.Context
import kotlin.math.max

/** Persistent playback positions used when the remote history is late or unavailable. */
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

    // Near-end is still a valid exit position. Actual playback completion is tracked separately by
    // the page cache, so treating the last 2% as completed here loses both local and server resume
    // progress when a user backs out before the media really ends.
    return position.coerceAtMost(durationMs)
  }

  private fun key(aid: Long, cid: Long) = "$aid:$cid"
}

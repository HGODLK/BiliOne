package dev.openbili.webdemo

internal const val SHORT_CDN_BUFFERING_LIMIT = 3
internal const val SHORT_CDN_BUFFERING_WINDOW_MS = 10_000L

internal enum class ShortBufferingDecision {
  USER_SEEK_LOAD,
  NOT_COUNTED,
  COUNTED,
  SWITCH_CDN,
}

/** 统计短时重复缓冲；连续长缓冲仍由播放器原有的三秒看门狗处理。 */
internal class CdnShortBufferingDetector(
  private val bufferingLimit: Int = SHORT_CDN_BUFFERING_LIMIT,
  private val bufferingWindowMs: Long = SHORT_CDN_BUFFERING_WINDOW_MS,
) {
  private val bufferingStartedAtMs = ArrayDeque<Long>()
  private var excludeNextBufferingForUserSeek = false

  init {
    require(bufferingLimit > 0)
    require(bufferingWindowMs >= 0L)
  }

  fun onUserSeek() {
    bufferingStartedAtMs.clear()
    excludeNextBufferingForUserSeek = true
  }

  fun onReady() {
    excludeNextBufferingForUserSeek = false
  }

  fun onBuffering(startedAtMs: Long, countShortBuffering: Boolean): ShortBufferingDecision {
    if (excludeNextBufferingForUserSeek) {
      excludeNextBufferingForUserSeek = false
      return ShortBufferingDecision.USER_SEEK_LOAD
    }
    if (!countShortBuffering) return ShortBufferingDecision.NOT_COUNTED

    while (
      bufferingStartedAtMs.isNotEmpty() &&
        startedAtMs - bufferingStartedAtMs.first() > bufferingWindowMs
    ) {
      bufferingStartedAtMs.removeFirst()
    }
    bufferingStartedAtMs.addLast(startedAtMs)
    if (bufferingStartedAtMs.size < bufferingLimit) return ShortBufferingDecision.COUNTED

    bufferingStartedAtMs.clear()
    return ShortBufferingDecision.SWITCH_CDN
  }

  fun reset() {
    bufferingStartedAtMs.clear()
    excludeNextBufferingForUserSeek = false
  }
}

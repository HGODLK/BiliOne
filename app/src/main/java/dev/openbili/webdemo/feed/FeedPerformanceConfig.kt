package dev.openbili.webdemo.feed

/** One-switch rollback point for the aggressive home-feed fast path. */
internal object FeedPerformanceConfig {
  const val aggressiveFastPathEnabled = true
  const val startupWarmupMaskEnabled = true

  const val coverPrefetchCount = 6
  const val coverRequestWidth = 448
  const val coverRequestHeight = 252
  const val initialAnimatedCardCount = 9
  const val navigationBrakeDurationMs = 96
  const val navigationFlingMemoryMs = 520L
  const val startupWarmupDurationMs = 680L
  const val startupWarmupTimeoutMs = 8_000L
  const val slowScrollLoadIntervalMs = 120L
  const val scrollIdleResumeDelayMs = 150L
  const val fastScrollHoldMs = 140L
  const val fastScrollThresholdDpPerSecond = 2_600f
}

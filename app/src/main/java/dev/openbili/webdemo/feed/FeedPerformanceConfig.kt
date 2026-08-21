package dev.openbili.webdemo.feed

/**
 * 首页推荐「激进快速路径」的性能参数集中地。
 *
 * 这些常量控制快速滑动时的图片加载暂停、封面预取窗口、导航刹车动画与启动预热遮罩
 * 等行为，是整条信息流性能调优的单一回滚开关与参数来源，避免魔法数字散落各处。
 */
internal object FeedPerformanceConfig {
  /** 是否启用激进的快速路径（单一开关，便于一键回滚）。 */
  const val aggressiveFastPathEnabled = true
  /** 是否启用启动预热遮罩。 */
  const val startupWarmupMaskEnabled = true

  /** 滚动停止后预取的封面数量上限。 */
  const val coverPrefetchCount = 6
  /** 封面请求的像素宽度。 */
  const val coverRequestWidth = 448
  /** 封面请求的像素高度。 */
  const val coverRequestHeight = 252
  /** 首屏参与入场动画的卡片数量。 */
  const val initialAnimatedCardCount = 9
  /** 导航前刹车动画的时长（毫秒）。 */
  const val navigationBrakeDurationMs = 96
  /** 保留最近一次 fling 速度的时长窗口（毫秒）。 */
  const val navigationFlingMemoryMs = 520L
  /** 启动预热遮罩的持续时长（毫秒）。 */
  const val startupWarmupDurationMs = 680L
  /** 启动预热的最长等待时间（毫秒）。 */
  const val startupWarmupTimeoutMs = 8_000L
  /** 慢速滚动时发布节流加载策略的间隔（毫秒）。 */
  const val slowScrollLoadIntervalMs = 120L
  /** 滚动停止后恢复正常加载策略的延迟（毫秒）。 */
  const val scrollIdleResumeDelayMs = 150L
  /** 判定为快速滑动后保持暂停状态的时长（毫秒）。 */
  const val fastScrollHoldMs = 140L
  /** 判定为快速滑动的速度阈值（dp/秒）。 */
  const val fastScrollThresholdDpPerSecond = 2_600f
}

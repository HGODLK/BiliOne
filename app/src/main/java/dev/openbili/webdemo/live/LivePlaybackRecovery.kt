package dev.openbili.webdemo.live

/**
 * 直播播放自动恢复策略。
 *
 * 当直播播放因线路/地址失效而失败时，自动恢复器会在「切换备用源」与「刷新播放地址」之间
 * 按有限轮次决策，避免无限重试。本文件定义恢复决策产生的动作（[LivePlaybackRecoveryAction]）
 * 以及执行决策的状态机 [LivePlaybackRecovery]。
 */

/**
 * 直播播放失败后自动恢复的决策结果（动作）。
 */
internal sealed interface LivePlaybackRecoveryAction {
  /** 切换到指定下标的备用源。 */
  data class SwitchSource(val index: Int) : LivePlaybackRecoveryAction

  /** 延迟 [delayMs] 毫秒后刷新播放地址，[round] 为当前已进行的刷新轮次。 */
  data class RefreshUrls(
    val delayMs: Long,
    val round: Int,
  ) : LivePlaybackRecoveryAction

  /** 忽略本次失败（判定为重复上报），不执行任何动作。 */
  data object Ignore : LivePlaybackRecoveryAction

  /** 停止自动恢复（已达最大刷新轮次或无法继续）。 */
  data object Stop : LivePlaybackRecoveryAction
}

/**
 * 直播播放自动恢复状态机，把自动恢复控制在有限范围内。
 *
 * 新获取到的地址集合会保留刷新轮次计数；一旦确认进入 READY 状态（或用户显式请求）则
 * 开启一轮全新的恢复序列，清空之前的失败记录。
 */
internal class LivePlaybackRecovery(
  private val maxRefreshRounds: Int = 2,
  private val duplicateWindowMs: Long = 750L,
  private val refreshDelaysMs: LongArray = longArrayOf(800L, 2_000L),
) {
  /** 已进行的刷新轮次。 */
  private var refreshRounds = 0
  /** 上一次失败时正在使用的源下标，用于识别重复失败。 */
  private var lastFailedSourceIndex = -1
  /** 上一次失败发生的时间戳（毫秒），用于重复窗口判定。 */
  private var lastFailureAtMs = Long.MIN_VALUE

  /** 清空全部失败记录与轮次计数，开启新一轮恢复序列。 */
  fun reset() {
    refreshRounds = 0
    lastFailedSourceIndex = -1
    lastFailureAtMs = Long.MIN_VALUE
  }

  /** 播放确认就绪时调用，重置恢复状态。 */
  fun onReady() = reset()

  /**
   * 播放失败时调用，返回下一步恢复动作。
   *
   * @param nowMs 当前时间戳（毫秒）。
   * @param currentSourceIndex 当前正在使用的源下标。
   * @param sourceCount 可用源总数。
   */
  fun onFailure(
    nowMs: Long,
    currentSourceIndex: Int,
    sourceCount: Int,
  ): LivePlaybackRecoveryAction {
    // 同一源在去抖窗口内连续失败视为重复上报，直接忽略，避免雪崩式重试。
    val duplicate =
      currentSourceIndex == lastFailedSourceIndex &&
        lastFailureAtMs != Long.MIN_VALUE &&
        nowMs - lastFailureAtMs in 0..duplicateWindowMs
    if (duplicate) return LivePlaybackRecoveryAction.Ignore

    lastFailedSourceIndex = currentSourceIndex
    lastFailureAtMs = nowMs
    val nextSourceIndex = currentSourceIndex + 1
    // 还有备用源可用时优先切换源。
    if (nextSourceIndex in 0 until sourceCount) {
      return LivePlaybackRecoveryAction.SwitchSource(nextSourceIndex)
    }
    // 源已用尽且达到最大刷新轮次则停止，防止无限重试。
    if (refreshRounds >= maxRefreshRounds) return LivePlaybackRecoveryAction.Stop

    refreshRounds += 1
    lastFailedSourceIndex = -1
    // 按轮次取对应的刷新延迟；超出数组长度时复用最后一个值，兜底为 0。
    val delayMs =
      refreshDelaysMs
        .getOrElse(refreshRounds - 1) { refreshDelaysMs.lastOrNull() ?: 0L }
        .coerceAtLeast(0L)
    return LivePlaybackRecoveryAction.RefreshUrls(delayMs, refreshRounds)
  }
}

package dev.openbili.webdemo.ui

/**
 * 转场就绪协调工具。
 *
 * 卡片转场在真正起飞前需要等待一系列前置条件（来源边界、目标挂载、首帧就绪等）全部
 * 满足，本文件提供两个配套原语：
 *  - [TransitionPreparationBarrier]：面向单次转场的就绪屏障，迟到的回调只能释放本次转场，
 *    不会误放后续转场；
 *  - [StableBoundsTracker]：连续若干渲染帧内保持不变的布局矩形探测，用于确认目标边界
 *    已经稳定。
 */

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** 转场在准备目标期间允许按住来源卡片保持不动的最长时间。 */
internal const val TRANSITION_PREPARE_TIMEOUT_MS = 450L

/** 评论/个人资料点击可短暂停顿以预组合，但仍需保持即时响应。 */
internal const val COMMENT_PROFILE_PREPARE_TIMEOUT_MS = 280L

/** 不同转场类型之间可能共用的具体前置条件。 */
internal enum class TransitionReadySignal {
  SOURCE_BOUNDS,
  SOURCE_SNAPSHOT,
  IMAGE_READY,
  TARGET_MOUNTED,
  TARGET_BOUNDS_STABLE,
  PLAYER_VIEW_READY,
  SURFACE_READY,
}

/** 转场准备的最终结果。 */
internal enum class TransitionPreparationResult {
  READY,
  TIMED_OUT,
  CANCELLED,
}

/**
 * 与单次转场绑定的就绪屏障。
 *
 * 迟到的回调仍可调用 [markReady]，但它们永远不会误放后续转场——因为每次转场都持有
 * 各自独立的屏障实例。
 */
internal class TransitionPreparationBarrier(requiredSignals: Set<TransitionReadySignal>) {
  private val required = requiredSignals.toSet()
  private val ready = mutableSetOf<TransitionReadySignal>()
  private val completion = CompletableDeferred<Unit>()
  private var cancelled = false

  init {
    // 无前置条件时立即视为就绪
    if (required.isEmpty()) completion.complete(Unit)
  }

  fun markReady(vararg signals: TransitionReadySignal) {
    synchronized(this) {
      // 已取消或已完成时忽略迟到信号
      if (cancelled || completion.isCompleted) return
      ready.addAll(signals)
      // 所有必需信号到齐即完成等待
      if (ready.containsAll(required)) completion.complete(Unit)
    }
  }

  fun isReady(): Boolean = synchronized(this) { !cancelled && ready.containsAll(required) }

  fun pendingSignals(): Set<TransitionReadySignal> =
    synchronized(this) { required.filterNotTo(mutableSetOf()) { it in ready } }

  fun cancel() {
    synchronized(this) {
      if (cancelled) return
      cancelled = true
      // 提前完成，让等待方立即返回 CANCELLED
      if (!completion.isCompleted) completion.complete(Unit)
    }
  }

  suspend fun await(
    timeoutMillis: Long = TRANSITION_PREPARE_TIMEOUT_MS
  ): TransitionPreparationResult {
    // 超时返回 null；完成返回 true
    val completed =
      withTimeoutOrNull(timeoutMillis) {
        completion.await()
        true
      }
    return synchronized(this) {
      when {
        // 取消优先于就绪判定
        cancelled -> TransitionPreparationResult.CANCELLED
        completed == true && ready.containsAll(required) -> TransitionPreparationResult.READY
        else -> TransitionPreparationResult.TIMED_OUT
      }
    }
  }
}

/** 检测连续多个渲染帧内保持不变的可用布局矩形。 */
internal class StableBoundsTracker(
  private val requiredMatches: Int = 2,
  private val tolerancePx: Float = 1f,
) {
  private var previous = Rect.Zero
  private var matches = 0

  fun observe(bounds: Rect): Boolean {
    // 尺寸不可用时重置计数
    if (!bounds.hasUsableSize()) {
      previous = bounds
      matches = 0
      return false
    }
    // 在容差范围内视为同一矩形，累计连续匹配次数
    if (bounds.approximatelyEquals(previous, tolerancePx)) {
      matches += 1
    } else {
      matches = 0
    }
    previous = bounds
    return matches >= requiredMatches
  }
}

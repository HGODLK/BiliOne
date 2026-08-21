package dev.openbili.webdemo.feed

/**
 * 信息流图片加载策略。
 *
 * 为避免快速滑动时封面/头像的解码与列表布局争抢主线程，这里按滚动速度在
 * NORMAL（正常）/THROTTLED（节流，仅加载可视键）/PAUSED（暂停）三档之间切换，
 * 并通过 CompositionLocal 下发给每张卡片决定是否发起图片请求。
 */

import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * 图片加载档位。
 *
 * 封面加载工作被刻意与列表的摆放循环解耦：快速滑动（fling）期间每张卡片都拿到同一个
 * 不可变的 PAUSED 策略，于是新组合出来的行只绘制廉价的占位符，而不会在每个滚动刻度
 * 反复启动/取消图片请求。
 */
internal enum class FeedImageLoadMode {
  /** 正常加载。 */
  NORMAL,
  /** 节流加载，只放行 [FeedImageLoadPolicy.allowedKeys] 中的键。 */
  THROTTLED,
  /** 完全暂停新图片请求。 */
  PAUSED,
}

/**
 * 一次图片加载策略快照，不可变（[Immutable]）以降低重组开销。
 *
 * [allowedKeys] 仅在 THROTTLED 模式下有意义，保存当前允许加载的卡片键集合。
 */
@Immutable
internal data class FeedImageLoadPolicy(
  val mode: FeedImageLoadMode,
  val allowedKeys: Set<String> = emptySet(),
) {
  /** 判断给定键是否被允许发起图片请求。 */
  fun permits(key: String): Boolean =
    mode == FeedImageLoadMode.NORMAL ||
      (mode == FeedImageLoadMode.THROTTLED &&
        allowedKeys.any { rowKey -> rowKey == key || rowKey.endsWith("_$key") })

  companion object {
    val Normal = FeedImageLoadPolicy(FeedImageLoadMode.NORMAL)
    val Paused = FeedImageLoadPolicy(FeedImageLoadMode.PAUSED)
  }
}

/** 通过 CompositionLocal 下发当前图片加载策略，默认正常加载。 */
internal val LocalFeedImageLoadPolicy = compositionLocalOf { FeedImageLoadPolicy.Normal }

/**
 * 是否启用「限制图片加载速度」。
 *
 * 关闭时，封面、头像以及由封面派生的渐变背景会在组合后立即开始加载。
 */
internal val LocalLimitImageLoadingSpeed = compositionLocalOf { false }

/** 一次滚动采样：是否滚动中、像素位置与当前可见键列表。 */
private data class FeedScrollSample(
  val scrolling: Boolean,
  val positionPx: Long,
  val visibleKeys: List<String>,
)

/**
 * 根据滚动采样计算图片加载策略的核心实现。
 *
 * [sample] 返回当前滚动状态、位置与可见键集合；本函数依据滑动速度把策略在
 * NORMAL/THROTTLED/PAUSED 之间切换：快速滑动暂停、慢速滑动只放行少量可视键、
 * 停止后延迟恢复。
 */
@Composable
private fun rememberFeedImageLoadPolicy(
  sample: () -> FeedScrollSample,
  slowVisibleLimit: Int,
): FeedImageLoadPolicy {
  val limitLoadingSpeed = LocalLimitImageLoadingSpeed.current
  // 把「dp/秒」的阈值换算为像素/秒，后续速度计算直接使用像素。
  val thresholdPxPerSecond =
    with(LocalDensity.current) { FeedPerformanceConfig.fastScrollThresholdDpPerSecond.dp.toPx() }
  var policy by remember { mutableStateOf(FeedImageLoadPolicy.Normal) }
  LaunchedEffect(limitLoadingSpeed, thresholdPxPerSecond, slowVisibleLimit) {
    // 未开启限制时始终维持正常策略，直接返回。
    if (!limitLoadingSpeed) {
      policy = FeedImageLoadPolicy.Normal
      return@LaunchedEffect
    }
    var previousPosition = sample().positionPx
    var previousAt = SystemClock.uptimeMillis()
    var fastUntil = 0L
    var lastSlowPublishAt = 0L
    snapshotFlow(sample).collectLatest { current ->
      val now = SystemClock.uptimeMillis()
      val elapsed = (now - previousAt).coerceAtLeast(1L)
      val velocity = abs(current.positionPx - previousPosition) * 1_000f / elapsed
      previousPosition = current.positionPx
      previousAt = now
      // 停止滚动后延迟一小段时间再恢复，避免刚停下的瞬间又爆发解码。
      if (!current.scrolling) {
        delay(FeedPerformanceConfig.scrollIdleResumeDelayMs)
        if (!sample().scrolling) policy = FeedImageLoadPolicy.Normal
        return@collectLatest
      }
      // 速度超过阈值时进入快速滑动窗口，并在窗口期内保持暂停。
      if (velocity >= thresholdPxPerSecond) {
        fastUntil = now + FeedPerformanceConfig.fastScrollHoldMs
      }
      if (now < fastUntil) {
        if (policy.mode != FeedImageLoadMode.PAUSED) policy = FeedImageLoadPolicy.Paused
        return@collectLatest
      }
      // 慢速滑动按固定间隔发布节流策略，只放行当前可见的前若干个键。
      if (now - lastSlowPublishAt >= FeedPerformanceConfig.slowScrollLoadIntervalMs) {
        lastSlowPublishAt = now
        val keys = current.visibleKeys.take(slowVisibleLimit).toSet()
        val next = FeedImageLoadPolicy(FeedImageLoadMode.THROTTLED, keys)
        if (next != policy) policy = next
      }
    }
  }
  return policy
}

/** 为 LazyVerticalGrid 采样滚动并计算图片加载策略。 */
@Composable
internal fun rememberGridFeedImageLoadPolicy(
  state: LazyGridState,
  columns: Int = 3,
): FeedImageLoadPolicy =
  rememberFeedImageLoadPolicy(
    sample = {
      val first = state.layoutInfo.visibleItemsInfo.firstOrNull()
      val row = (first?.index ?: state.firstVisibleItemIndex) / columns.coerceAtLeast(1)
      val extent = first?.size?.height?.coerceAtLeast(1) ?: 1
      FeedScrollSample(
        scrolling = state.isScrollInProgress,
        positionPx = row.toLong() * extent - (first?.offset?.y ?: 0),
        visibleKeys = state.layoutInfo.visibleItemsInfo.map { it.key.toString() },
      )
    },
    slowVisibleLimit = columns.coerceAtLeast(1) * 2,
  )

/** 为 LazyList 采样滚动并计算图片加载策略。 */
@Composable
internal fun rememberListFeedImageLoadPolicy(state: LazyListState): FeedImageLoadPolicy =
  rememberFeedImageLoadPolicy(
    sample = {
      val first = state.layoutInfo.visibleItemsInfo.firstOrNull()
      val extent = first?.size?.coerceAtLeast(1) ?: 1
      FeedScrollSample(
        scrolling = state.isScrollInProgress,
        positionPx =
          (first?.index ?: state.firstVisibleItemIndex).toLong() * extent -
            (first?.offset ?: state.firstVisibleItemScrollOffset),
        visibleKeys = state.layoutInfo.visibleItemsInfo.map { it.key.toString() },
      )
    },
    slowVisibleLimit = 4,
  )

/** 为 LazyStaggeredGrid 采样滚动并计算图片加载策略。 */
@Composable
internal fun rememberStaggeredFeedImageLoadPolicy(
  state: LazyStaggeredGridState,
  columns: Int = 2,
): FeedImageLoadPolicy =
  rememberFeedImageLoadPolicy(
    sample = {
      val first = state.layoutInfo.visibleItemsInfo.firstOrNull()
      val row = (first?.index ?: state.firstVisibleItemIndex) / columns.coerceAtLeast(1)
      val extent = first?.size?.height?.coerceAtLeast(1) ?: 1
      FeedScrollSample(
        scrolling = state.isScrollInProgress,
        positionPx = row.toLong() * extent - (first?.offset?.y ?: 0),
        visibleKeys = state.layoutInfo.visibleItemsInfo.map { it.key.toString() },
      )
    },
    slowVisibleLimit = columns.coerceAtLeast(1) * 2,
  )

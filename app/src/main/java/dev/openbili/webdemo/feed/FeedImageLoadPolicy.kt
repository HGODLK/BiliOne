package dev.openbili.webdemo.feed

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/**
 * Cover work is deliberately detached from the list's placement loop. During a fling every card
 * receives the same immutable PAUSED policy, so newly composed rows draw only their inexpensive
 * placeholders and do not start/cancel image requests on every scroll tick.
 */
internal enum class FeedImageLoadMode {
  NORMAL,
  THROTTLED,
  PAUSED,
}

@Immutable
internal data class FeedImageLoadPolicy(
  val mode: FeedImageLoadMode,
  val allowedKeys: Set<String> = emptySet(),
) {
  fun permits(key: String): Boolean =
    mode == FeedImageLoadMode.NORMAL ||
      (mode == FeedImageLoadMode.THROTTLED &&
        allowedKeys.any { rowKey -> rowKey == key || rowKey.endsWith("_$key") })

  companion object {
    val Normal = FeedImageLoadPolicy(FeedImageLoadMode.NORMAL)
    val Paused = FeedImageLoadPolicy(FeedImageLoadMode.PAUSED)
  }
}

internal val LocalFeedImageLoadPolicy = compositionLocalOf { FeedImageLoadPolicy.Normal }

/** When disabled, covers, avatars and cover-derived gradients start as soon as they are composed. */
internal val LocalLimitImageLoadingSpeed = compositionLocalOf { false }

private data class FeedScrollSample(
  val scrolling: Boolean,
  val positionPx: Long,
  val visibleKeys: List<String>,
)

@Composable
private fun rememberFeedImageLoadPolicy(
  sample: () -> FeedScrollSample,
  slowVisibleLimit: Int,
): FeedImageLoadPolicy {
  val limitLoadingSpeed = LocalLimitImageLoadingSpeed.current
  val thresholdPxPerSecond =
    with(LocalDensity.current) { FeedPerformanceConfig.fastScrollThresholdDpPerSecond.dp.toPx() }
  var policy by remember { mutableStateOf(FeedImageLoadPolicy.Normal) }
  LaunchedEffect(limitLoadingSpeed, thresholdPxPerSecond, slowVisibleLimit) {
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
      if (!current.scrolling) {
        delay(FeedPerformanceConfig.scrollIdleResumeDelayMs)
        if (!sample().scrolling) policy = FeedImageLoadPolicy.Normal
        return@collectLatest
      }
      if (velocity >= thresholdPxPerSecond) {
        fastUntil = now + FeedPerformanceConfig.fastScrollHoldMs
      }
      if (now < fastUntil) {
        if (policy.mode != FeedImageLoadMode.PAUSED) policy = FeedImageLoadPolicy.Paused
        return@collectLatest
      }
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

@Composable
internal fun rememberListFeedImageLoadPolicy(state: LazyListState): FeedImageLoadPolicy =
  rememberFeedImageLoadPolicy(
    sample = {
      val first = state.layoutInfo.visibleItemsInfo.firstOrNull()
      val extent = first?.size?.coerceAtLeast(1) ?: 1
      FeedScrollSample(
        scrolling = state.isScrollInProgress,
        positionPx = (first?.index ?: state.firstVisibleItemIndex).toLong() * extent -
          (first?.offset ?: state.firstVisibleItemScrollOffset),
        visibleKeys = state.layoutInfo.visibleItemsInfo.map { it.key.toString() },
      )
    },
    slowVisibleLimit = 4,
  )

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

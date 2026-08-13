package dev.openbili.webdemo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val INDICATOR_FADE_START_FRACTION = .05f
private const val INDICATOR_FULLY_OPAQUE_FRACTION = .75f
private val IndicatorSize = 40.dp
private val IndicatorEdgeClearance = 8.dp
private val IndicatorTravel = 32.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefreshContainer(
  refreshing: Boolean,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  indicatorTopPadding: Dp = 0.dp,
  content: @Composable BoxScope.() -> Unit,
) {
  val state = rememberPullToRefreshState()
  val density = LocalDensity.current
  LaunchedEffect(enabled) {
    if (!enabled) state.snapTo(0f)
  }
  Box(
    modifier =
      modifier.pullToRefresh(
        isRefreshing = refreshing && enabled,
        state = state,
        enabled = enabled,
        onRefresh = onRefresh,
      ),
  ) {
    content()
    if (enabled) {
      val distanceFraction = state.distanceFraction.coerceAtLeast(0f)
      val isRefreshing = refreshing && enabled
      val travelPx = with(density) { IndicatorTravel.toPx() }
      Box(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .padding(top = indicatorTopPadding + IndicatorEdgeClearance)
            .offset {
              IntOffset(
                x = 0,
                y =
                  (travelPx *
                      pullRefreshIndicatorOffsetFraction(distanceFraction, isRefreshing))
                    .roundToInt(),
              )
            }
            .graphicsLayer {
              clip = false
              alpha =
                pullRefreshIndicatorAlpha(
                  distanceFraction = distanceFraction,
                  isRefreshing = isRefreshing,
                )
            },
      ) {
        Surface(
          modifier = Modifier.size(IndicatorSize),
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.primary,
          tonalElevation = 4.dp,
          shadowElevation = 4.dp,
        ) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isRefreshing) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
              )
            } else {
              CircularProgressIndicator(
                progress = { distanceFraction.coerceIn(0f, 1f) },
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
              )
            }
          }
        }
      }
    }
  }
}

internal fun pullRefreshIndicatorOffsetFraction(
  distanceFraction: Float,
  isRefreshing: Boolean,
): Float = if (isRefreshing) 1f else distanceFraction.coerceIn(0f, 1f)

internal fun pullRefreshIndicatorAlpha(
  distanceFraction: Float,
  isRefreshing: Boolean,
): Float {
  if (isRefreshing) return 1f
  return when {
    distanceFraction <= INDICATOR_FADE_START_FRACTION -> 0f
    distanceFraction >= INDICATOR_FULLY_OPAQUE_FRACTION -> 1f
    else ->
      (distanceFraction - INDICATOR_FADE_START_FRACTION) /
        (INDICATOR_FULLY_OPAQUE_FRACTION - INDICATOR_FADE_START_FRACTION)
  }
}

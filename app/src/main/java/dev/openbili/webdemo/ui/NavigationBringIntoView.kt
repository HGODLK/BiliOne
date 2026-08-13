package dev.openbili.webdemo.ui

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

val NavigationCardBottomClearance = 112.dp
val LocalNavigationTopClearance = compositionLocalOf { 0.dp }
internal const val NAVIGATION_BRING_INTO_VIEW_TIMEOUT_MS = 360L

internal fun navigationBringIntoViewRect(
  width: Int,
  height: Int,
  topClearancePx: Int,
  bottomClearancePx: Int,
): Rect? {
  if (width <= 0 || height <= 0) return null
  val safeTopClearance = topClearancePx.coerceAtLeast(0)
  return Rect(
    left = 0f,
    top = if (safeTopClearance == 0) 0f else -safeTopClearance.toFloat(),
    right = width.toFloat(),
    bottom = height.toFloat() + bottomClearancePx.coerceAtLeast(0),
  )
}

@Stable
class NavigationBringIntoViewRequester internal constructor(
  internal val delegate: BringIntoViewRequester,
) {
  private var targetSize = IntSize.Zero
  internal var topClearancePx: Int = 0
  internal var bottomClearancePx: Int = 0

  internal fun updateTargetSize(size: IntSize) {
    targetSize = size
  }

  suspend fun bringIntoView() {
    // Relocation can remain suspended when a parent is being replaced by a transition. Navigation
    // may prefer the safe viewport, but it must still start if that parent never responds.
    withTimeoutOrNull(NAVIGATION_BRING_INTO_VIEW_TIMEOUT_MS) {
      val requestedRect =
        navigationBringIntoViewRect(
          width = targetSize.width,
          height = targetSize.height,
          topClearancePx = topClearancePx,
          bottomClearancePx = bottomClearancePx,
        )
      if (requestedRect == null) delegate.bringIntoView()
      else delegate.bringIntoView(requestedRect)
    }
  }
}

@Composable
fun rememberNavigationBringIntoViewRequester(
  topClearance: Dp = LocalNavigationTopClearance.current,
  bottomClearance: Dp = NavigationCardBottomClearance,
): NavigationBringIntoViewRequester {
  val density = LocalDensity.current
  val requester = remember { NavigationBringIntoViewRequester(BringIntoViewRequester()) }
  requester.topClearancePx = with(density) { topClearance.roundToPx() }
  requester.bottomClearancePx = with(density) { bottomClearance.roundToPx() }
  return requester
}

fun Modifier.navigationBringIntoViewTarget(
  requester: NavigationBringIntoViewRequester
): Modifier =
  bringIntoViewRequester(requester.delegate).onSizeChanged(requester::updateTargetSize)

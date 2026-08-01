package dev.openbili.webdemo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.R
import dev.openbili.webdemo.video.bangumiPageLayoutForPane
import dev.openbili.webdemo.video.videoPaneSpec
import kotlinx.coroutines.launch

@Composable
internal fun BiliOneStartupAnimation(
  reduceMotion: Boolean,
  modifier: Modifier = Modifier,
) {
  // Reuse the exact foreground used by the Android adaptive icon so the first visual impression
  // and the launcher identity cannot drift apart.
  val logoAlpha = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
  val logoScale = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else .9f) }
  LaunchedEffect(reduceMotion) {
    if (reduceMotion) return@LaunchedEffect
    logoAlpha.snapTo(0f)
    logoScale.snapTo(.9f)
    launch { logoAlpha.animateTo(1f, animationSpec = tween(150, easing = FastOutSlowInEasing)) }
    logoScale.animateTo(1f, animationSpec = tween(280, easing = FastOutSlowInEasing))
  }
  Image(
    painter = painterResource(R.drawable.bilione_icon_foreground_compact),
    contentDescription = null,
    modifier =
      modifier.size(172.dp).graphicsLayer {
        alpha = logoAlpha.value
        scaleX = logoScale.value
        scaleY = logoScale.value
      },
  )
}

/**
 * Measures only the poster destination used by the search-to-bangumi shared transition. Keeping
 * this layout independent from VideoScreen prevents the player, comments, and page effects from
 * being composed while the poster is in flight.
 */
@Composable
internal fun SearchBangumiTransitionTarget(onPosterBoundsChanged: (Rect) -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val contentWidth = maxOf(1.dp, maxWidth - 28.dp)
    val contentHeight = maxOf(1.dp, maxHeight - 88.dp)
    val paneSpec =
      videoPaneSpec(
        widthPx = with(density) { contentWidth.roundToPx() },
        heightPx = with(density) { contentHeight.roundToPx() },
        density = density.density,
        fontScale = density.fontScale,
      )
    val primaryWidth =
      if (paneSpec.split) {
        maxOf(1.dp, contentWidth - with(density) { paneSpec.secondarySizePx.toDp() } - 12.dp)
      } else contentWidth
    val primaryHeight = if (paneSpec.split) contentHeight else maxOf(1.dp, contentHeight * .68f)
    val pageLayout =
      bangumiPageLayoutForPane(
        primaryWidthDp = primaryWidth.value,
        primaryHeightDp = primaryHeight.value,
        fontScale = density.fontScale,
      )
    val posterHeight = maxOf(1.dp, primaryHeight - pageLayout.playerHeight - 8.dp)
    Box(
      Modifier.offset(x = 16.dp, y = 84.dp + pageLayout.playerHeight)
        .size(width = posterHeight * .75f, height = posterHeight)
        .onGloballyPositioned { onPosterBoundsChanged(it.boundsInRoot()) }
    )
  }
}

/** Lightweight 16:9 destination used while a bangumi-home player/card flight owns the frame. */
@Composable
internal fun BangumiPlayerTransitionTarget(onPlayerBoundsChanged: (Rect) -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val contentWidth = maxOf(1.dp, maxWidth - 28.dp)
    val contentHeight = maxOf(1.dp, maxHeight - 88.dp)
    val paneSpec =
      videoPaneSpec(
        widthPx = with(density) { contentWidth.roundToPx() },
        heightPx = with(density) { contentHeight.roundToPx() },
        density = density.density,
        fontScale = density.fontScale,
      )
    val primaryWidth =
      if (paneSpec.split) {
        maxOf(1.dp, contentWidth - with(density) { paneSpec.secondarySizePx.toDp() } - 12.dp)
      } else contentWidth
    val primaryHeight = if (paneSpec.split) contentHeight else maxOf(1.dp, contentHeight * .68f)
    val pageLayout =
      bangumiPageLayoutForPane(
        primaryWidthDp = primaryWidth.value,
        primaryHeightDp = primaryHeight.value,
        fontScale = density.fontScale,
      )
    Box(
      // Mirror VideoContent exactly: 76 dp BangumiHeader, then the content's 16 dp start padding.
      // The real Surface uses playerWidth, not the complete primary pane width.
      Modifier.offset(x = 16.dp, y = 76.dp)
        .size(width = pageLayout.playerWidth, height = pageLayout.playerHeight)
        .onGloballyPositioned { onPlayerBoundsChanged(it.boundsInRoot()) }
    )
  }
}

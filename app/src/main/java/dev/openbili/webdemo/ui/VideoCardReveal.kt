package dev.openbili.webdemo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.openbili.webdemo.feed.FeedImageLoadMode
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import kotlinx.coroutines.delay

/**
 * A short reveal for video-card feeds. Reveal state is saved by stable item key, so every newly
 * loaded card fades in once without replaying the animation after lazy-list recycling.
 */
@Composable
fun VideoCardReveal(
  index: Int,
  batchKey: Any?,
  itemKey: Any = index,
  modifier: Modifier = Modifier,
  animatedItemCount: Int = Int.MAX_VALUE,
  content: @Composable () -> Unit,
) {
  val imageLoadPolicy = LocalFeedImageLoadPolicy.current
  var revealed by
    rememberSaveable(batchKey, itemKey) {
      mutableStateOf(index >= animatedItemCount)
    }
  LaunchedEffect(batchKey, itemKey) {
    if (!revealed) {
      delay(((index / 3).coerceAtMost(5) * 22L))
      revealed = true
    }
  }
  val alpha by
    animateFloatAsState(
      targetValue = if (revealed) 1f else .34f,
      animationSpec = tween(220),
      label = "videoCardReveal",
    )
  Box(
    modifier.then(
      if (imageLoadPolicy.mode == FeedImageLoadMode.PAUSED || (revealed && alpha == 1f)) Modifier
      else Modifier.graphicsLayer { this.alpha = alpha }
    )
  ) {
    content()
  }
}

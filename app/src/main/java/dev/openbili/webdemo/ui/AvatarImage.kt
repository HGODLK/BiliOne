package dev.openbili.webdemo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import dev.openbili.webdemo.UrlPolicy
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy

/**
 * Small avatar requests are used in several scrolling surfaces. Keeping them software-backed avoids
 * a hardware-pixel readback when a surrounding Compose layer is recorded for a transition. The
 * request is still bounded to the displayed size, so this does not increase image memory.
 */
@Composable
fun AvatarImage(
  face: String,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  requestSize: Int = 80,
  loadKey: String = face,
) {
  val context = LocalContext.current
  val imageLoadPolicy = LocalFeedImageLoadPolicy.current
  val normalizedFace = remember(face) { UrlPolicy.normalizeImageUrl(face).orEmpty() }
  val previouslyLoaded =
    remember(normalizedFace) {
      normalizedFace.isNotBlank() && LoadedFeedImageRegistry.contains(normalizedFace)
    }
  var displayed by remember(normalizedFace) { mutableStateOf(false) }
  val requestPermitted = previouslyLoaded || displayed || imageLoadPolicy.permits(loadKey)
  val imageAlpha by
    animateFloatAsState(
      targetValue = if (displayed) 1f else 0f,
      animationSpec = tween(180),
      label = "avatarImageAlpha",
    )
  val model =
    remember(normalizedFace, requestSize, requestPermitted) {
      if (requestPermitted && normalizedFace.isNotBlank()) {
        ImageRequest.Builder(context)
          .data(normalizedFace)
          .size(requestSize, requestSize)
          .precision(Precision.INEXACT)
          .allowHardware(false)
          .build()
      } else null
    }
  AsyncImage(
    model = model,
    contentDescription = contentDescription,
    modifier = modifier.graphicsLayer { alpha = imageAlpha },
    contentScale = ContentScale.Crop,
    onSuccess = {
      displayed = true
      LoadedFeedImageRegistry.markLoaded(normalizedFace)
    },
  )
}

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
 * 小型头像请求用于多个滚动表面。保持其软件后备，可在周围 Compose 图层为转场而录制时
 * 避免一次硬件像素回读。请求仍受限于显示尺寸，因此不会增加图片内存。
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

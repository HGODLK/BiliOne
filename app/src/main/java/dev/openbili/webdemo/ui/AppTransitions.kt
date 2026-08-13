package dev.openbili.webdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry

@Composable
internal fun ProfileTransitionBackground(
  sourceBounds: Rect,
  progress: () -> Float,
  dimAlpha: Float,
  revealFromTransparent: Boolean = false,
  surfaceAlpha: () -> Float = { 1f },
) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val screenWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
    val screenHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
    val fallbackSize = with(density) { 48.dp.toPx() }
    val source =
      sourceBounds.takeIf { it.width > 0f && it.height > 0f }
        ?: Rect(
          left = (screenWidthPx - fallbackSize) / 2f,
          top = (screenHeightPx - fallbackSize) / 2f,
          right = (screenWidthPx + fallbackSize) / 2f,
          bottom = (screenHeightPx + fallbackSize) / 2f,
        )
    val startScaleX = (source.width / screenWidthPx).coerceAtLeast(.001f)
    val startScaleY = (source.height / screenHeightPx).coerceAtLeast(.001f)

    Box(
      Modifier.fillMaxSize()
        .graphicsLayer {
          alpha =
            progress().coerceIn(0f, 1f) *
              dimAlpha *
              surfaceAlpha().coerceIn(0f, 1f)
        }
        .background(Color.Black)
    )
    Surface(
      modifier =
        Modifier.fillMaxSize().graphicsLayer {
          val p = progress().coerceIn(0f, 1f)
          val pageScaleX = startScaleX + (1f - startScaleX) * p
          val pageScaleY = startScaleY + (1f - startScaleY) * p
          scaleX = pageScaleX
          scaleY = pageScaleY
          translationX = source.left * (1f - p)
          translationY = source.top * (1f - p)
          transformOrigin = TransformOrigin(0f, 0f)
          alpha =
            (if (revealFromTransparent) (p / .18f).coerceIn(0f, 1f) else 1f) *
              surfaceAlpha().coerceIn(0f, 1f)
          shape = RoundedCornerShape((18f * (1f - p) / pageScaleY.coerceAtLeast(.001f)).dp)
          clip = true
        },
      shape = RectangleShape,
      color = MaterialTheme.colorScheme.background,
      tonalElevation = if (revealFromTransparent) 0.dp else 2.dp,
      shadowElevation = 0.dp,
    ) {}
  }
}

@Composable
internal fun AvatarProfileTransitionForeground(
  sourceBounds: Rect,
  targetBounds: Rect,
  face: String,
  name: String,
  progress: () -> Float,
) {
  Box(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val source =
      sourceBounds.takeIf { it.width > 0f && it.height > 0f }
        ?: Rect(0f, 0f, with(density) { 36.dp.toPx() }, with(density) { 36.dp.toPx() })

    SharedProfileAvatar(
      face = face,
      name = name,
      sourceBounds = source,
      targetBounds = targetBounds,
      progress = progress,
    )
  }
}

@Composable
internal fun SharedProfileAvatar(
  face: String,
  name: String,
  sourceBounds: Rect,
  targetBounds: Rect,
  progress: () -> Float,
) {
  val density = LocalDensity.current
  fun Rect.centeredSquare(): Rect {
    val edge = minOf(width, height).coerceAtLeast(1f)
    return Rect(
      left = center.x - edge / 2f,
      top = center.y - edge / 2f,
      right = center.x + edge / 2f,
      bottom = center.y + edge / 2f,
    )
  }
  val source = sourceBounds.centeredSquare()
  val target = targetBounds.takeIf { it.width > 0f && it.height > 0f }?.centeredSquare() ?: source
  val targetScale = target.width / source.width
  AsyncImage(
    model = face,
    contentDescription = name,
    modifier =
      Modifier.offset { IntOffset(source.left.toInt(), source.top.toInt()) }
        .size(
          with(density) { source.width.toDp() },
          with(density) { source.height.toDp() },
        )
        .graphicsLayer {
          val p = progress().coerceIn(0f, 1f)
          translationX = (target.left - source.left) * p
          translationY = (target.top - source.top) * p
          val scale = 1f + (targetScale - 1f) * p
          scaleX = scale
          scaleY = scale
          transformOrigin = TransformOrigin(0f, 0f)
          clip = true
          shape = CircleShape
        },
    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
  )
}

@Composable
/** Lightweight rectangle-to-page surface used by every retained root-page expansion. */
internal fun ExpandingPageTransitionOverlay(
  sourceBounds: Rect,
  progress: () -> Float,
  overlayAlpha: () -> Float = { 1f },
  scrimAlpha: () -> Float = progress,
  sourceCornerRadius: Dp = 22.dp,
  targetCornerRadius: Dp = 20.dp,
) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val screenWidthPx = with(density) { maxWidth.toPx() }
    val screenHeightPx = with(density) { maxHeight.toPx() }
    val insetPx = with(density) { 16.dp.toPx() }
    val targetWidthPx = (screenWidthPx - insetPx * 2f).coerceAtLeast(1f)
    val targetHeightPx = (screenHeightPx - insetPx * 2f).coerceAtLeast(1f)
    val fallbackWidth = with(density) { 330.dp.toPx() }.coerceAtMost(targetWidthPx)
    val fallbackHeight = with(density) { 44.dp.toPx() }
    val source =
      sourceBounds.takeIf { it.width > 0f && it.height > 0f }
        ?: Rect(
          left = screenWidthPx - insetPx - fallbackWidth,
          top = insetPx,
          right = screenWidthPx - insetPx,
          bottom = insetPx + fallbackHeight,
        )
    Box(
      Modifier.fillMaxSize()
        .graphicsLayer { alpha = scrimAlpha().coerceIn(0f, 1f) }
        .background(MaterialTheme.colorScheme.background)
    )
    Surface(
      modifier =
        Modifier.offset { IntOffset(insetPx.toInt(), insetPx.toInt()) }
          .size(
            with(density) { targetWidthPx.toDp() },
            with(density) { targetHeightPx.toDp() },
          )
          .graphicsLayer {
            val p = progress().coerceIn(0f, 1f)
            alpha = overlayAlpha().coerceIn(0f, 1f)
            val startScaleX = (source.width / targetWidthPx).coerceAtLeast(.001f)
            val startScaleY = (source.height / targetHeightPx).coerceAtLeast(.001f)
            val currentScaleX = startScaleX + (1f - startScaleX) * p
            val currentScaleY = startScaleY + (1f - startScaleY) * p
            scaleX = currentScaleX
            scaleY = currentScaleY
            translationX = (source.left - insetPx) * (1f - p)
            translationY = (source.top - insetPx) * (1f - p)
            transformOrigin = TransformOrigin(0f, 0f)
            val apparentCorner =
              sourceCornerRadius.value + (targetCornerRadius.value - sourceCornerRadius.value) * p
            // The page uses different X/Y scales while collapsing. A normal rounded rectangle is
            // stretched into an ellipse; compensate each axis so the visible corner still matches
            // the source search field exactly.
            shape =
              NonUniformScaledRoundedShape(
                apparentRadiusPx = apparentCorner * density.density,
                scaleX = currentScaleX,
                scaleY = currentScaleY,
              )
            clip = true
          },
      shape = RectangleShape,
      color = MaterialTheme.colorScheme.background,
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
    ) {}
  }
}

private data class NonUniformScaledRoundedShape(
  val apparentRadiusPx: Float,
  val scaleX: Float,
  val scaleY: Float,
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val radiusX =
      (apparentRadiusPx / scaleX.coerceAtLeast(.001f)).coerceIn(0f, size.width / 2f)
    val radiusY =
      (apparentRadiusPx / scaleY.coerceAtLeast(.001f)).coerceIn(0f, size.height / 2f)
    return Outline.Rounded(
      RoundRect(
        left = 0f,
        top = 0f,
        right = size.width,
        bottom = size.height,
        cornerRadius = CornerRadius(radiusX, radiusY),
      )
    )
  }
}

// ── Transition phase ──────────────────────────────────────────────────

internal enum class VideoOrigin {
  HOME,
  HOME_DYNAMIC,
  POPULAR,
  MY,
  SEARCH,
  BANGUMI,
  ARTICLE,
  OTHER,
}

internal sealed interface TransitionPhase {
  data object Feed : TransitionPhase

  data class ToVideo(
    val item: FeedItem,
    val cardBounds: Rect?,
    val fromVideo: Boolean = false,
  ) : TransitionPhase

  data class Video(val item: FeedItem, val sourceBounds: Rect?) : TransitionPhase

  data class ToFeed(val item: FeedItem, val cardBounds: Rect?) : TransitionPhase

  data class ToPreviousVideo(
    val departingItem: FeedItem,
    val previousItem: FeedItem,
    val cardBounds: Rect,
    val previousSourceBounds: Rect?,
  ) : TransitionPhase
}

// ── Card transition overlay (full card, graphicsLayer-only animation) ──

@Composable
internal fun CardTransitionOverlay(
  item: FeedItem,
  startBounds: Rect,
  endBounds: Rect,
  progress: () -> Float,
  overlayAlpha: () -> Float,
  fitCover: Boolean = false,
  coverDimAlpha: () -> Float = { 0f },
  modifier: Modifier = Modifier,
  bitmap: android.graphics.Bitmap? = null,
  allowAsyncImageFallback: Boolean = true,
) {
  Box(modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val targetWidthPx = endBounds.width.coerceAtLeast(1f)
    val targetHeightPx = endBounds.height.coerceAtLeast(1f)
    // Do not remember a cache miss. Exit preparation may finish loading the full poster after this
    // composable first appears; retaining null would force a visible asynchronous placeholder.
    val transitionBitmap =
      bitmap ?: LoadedFeedImageRegistry.bitmap(item.coverUrl, requireUncropped = fitCover)

    Surface(
      modifier =
        Modifier.size(
            with(density) { targetWidthPx.toDp() },
            with(density) { targetHeightPx.toDp() },
          )
          .graphicsLayer {
            val p = progress().coerceIn(0f, 1f)
            val startScaleX = (startBounds.width / targetWidthPx).coerceAtLeast(0.001f)
            val startScaleY = (startBounds.height / targetHeightPx).coerceAtLeast(0.001f)
            val currentScaleX = startScaleX + (1f - startScaleX) * p
            val currentScaleY = startScaleY + (1f - startScaleY) * p
            translationX = startBounds.left + (endBounds.left - startBounds.left) * p
            translationY = startBounds.top + (endBounds.top - startBounds.top) * p
            scaleX = currentScaleX
            scaleY = currentScaleY
            transformOrigin = TransformOrigin(0f, 0f)
            alpha = overlayAlpha().coerceIn(0f, 1f)
            val cornerScale = minOf(currentScaleX, currentScaleY).coerceAtLeast(.001f)
            shape = RoundedCornerShape((VideoShapeTokens.CornerRadius.value / cornerScale).dp)
            clip = true
          },
      shape = RectangleShape,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
    ) {
      Box(
        Modifier.fillMaxSize().drawWithContent {
          drawContent()
          val dimAlpha = coverDimAlpha().coerceIn(0f, 1f)
          if (dimAlpha > 0f) drawRect(Color.Black.copy(alpha = dimAlpha))
        }
      ) {
        if (transitionBitmap != null) {
          Image(
            bitmap = transitionBitmap.asImageBitmap(),
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = if (fitCover) ContentScale.Fit else ContentScale.Crop,
          )
        } else if (allowAsyncImageFallback) {
          CoverImage(
            coverUrl = item.coverUrl,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            enforceAspectRatio = false,
            fadeIn = false,
            alwaysLoad = true,
            contentScale = if (fitCover) ContentScale.Fit else ContentScale.Crop,
          )
        } else {
          Box(Modifier.fillMaxSize().background(Color.Black))
        }
      }
    }
  }
}

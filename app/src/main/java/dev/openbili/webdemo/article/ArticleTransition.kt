package dev.openbili.webdemo.article

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.ui.VideoShapeTokens

enum class ArticleOrigin {
  MY,
  SEARCH,
  PROFILE,
  VIDEO,
  ARTICLE,
}

data class ArticleStackFrame(
  val entryId: Long,
  val article: ArticleItem,
  val origin: ArticleOrigin,
  val sourceBounds: Rect?,
)

class ArticleTransitionSession(
  val article: ArticleItem,
  startBounds: Rect,
  endBounds: Rect,
  initialProgress: Float,
) {
  var startBounds by mutableStateOf(startBounds)
  var endBounds by mutableStateOf(endBounds)
  val progress = Animatable(initialProgress)
  val overlayAlpha = Animatable(1f)
}

@Composable
fun ArticleTransitionOverlay(session: ArticleTransitionSession) {
  val density = LocalDensity.current
  val targetWidthPx = session.endBounds.width.coerceAtLeast(1f)
  val targetHeightPx = session.endBounds.height.coerceAtLeast(1f)
  Box(Modifier.fillMaxSize()) {
    Surface(
      modifier =
        Modifier.size(
            with(density) { targetWidthPx.toDp() },
            with(density) { targetHeightPx.toDp() },
          )
          .graphicsLayer {
            val p = session.progress.value.coerceIn(0f, 1f)
            val startScaleX = (session.startBounds.width / targetWidthPx).coerceAtLeast(.001f)
            val startScaleY = (session.startBounds.height / targetHeightPx).coerceAtLeast(.001f)
            val currentScaleX = startScaleX + (1f - startScaleX) * p
            val currentScaleY = startScaleY + (1f - startScaleY) * p
            translationX =
              session.startBounds.left + (session.endBounds.left - session.startBounds.left) * p
            translationY =
              session.startBounds.top + (session.endBounds.top - session.startBounds.top) * p
            scaleX = currentScaleX
            scaleY = currentScaleY
            transformOrigin = TransformOrigin(0f, 0f)
            alpha = session.overlayAlpha.value.coerceIn(0f, 1f)
            val cornerScale = minOf(currentScaleX, currentScaleY).coerceAtLeast(.001f)
            shape = RoundedCornerShape((VideoShapeTokens.CornerRadius.value / cornerScale).dp)
            clip = true
          },
      shape = RectangleShape,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
    ) {
      ArticleVisual(
        article = session.article,
        modifier = Modifier.fillMaxSize(),
        enforceAspectRatio = false,
        decorationAlpha = 0f,
        alwaysLoad = true,
        fadeIn = false,
      )
    }
  }
}

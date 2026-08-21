package dev.openbili.webdemo.article

/**
 * 文章页的转场模型与覆盖层。
 *
 * 定义文章卡片进入文章页时共享元素转场所需的数据结构（[ArticleOrigin]、[ArticleStackFrame]、
 * [ArticleTransitionSession]），并实现 [ArticleTransitionOverlay] 覆盖层：依据会话中的开始/结束
 * 边界与动画进度，在 graphicsLayer 里插值计算缩放、位移与圆角，逐帧重现封面卡片展开到全屏的过程。
 */

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

/**
 * 文章卡片进入文章页的来源页面。
 */
enum class ArticleOrigin {
  HOME_DYNAMIC,
  MY,
  SEARCH,
  PROFILE,
  VIDEO,
  ARTICLE,
}

/**
 * 文章关系栈中的一帧，记录从哪张卡片、以哪个边界进入了文章页。
 */
data class ArticleStackFrame(
  val entryId: Long,
  val article: ArticleItem,
  val origin: ArticleOrigin,
  val sourceBounds: Rect?,
)

/**
 * 一次文章转场的进行时状态。
 *
 * 持有开始/结束边界与 [Animatable] 进度；覆盖层根据 progress 在两者之间插值，
 * overlayAlpha 用于在转场收尾时淡出覆盖层。
 */
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

/**
 * 文章转场覆盖层：把文章封面从来源卡片边界动画展开到目标（全屏）边界。
 */
@Composable
fun ArticleTransitionOverlay(session: ArticleTransitionSession) {
  val density = LocalDensity.current
  // 目标尺寸至少保留 1px，避免除零与零尺寸布局异常
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
            // 动画进度钳制到 [0,1]，避免过度动画时越界
            val p = session.progress.value.coerceIn(0f, 1f)
            // 来源尺寸相对目标的缩放基准，防止来源为 0 时塌缩
            val startScaleX = (session.startBounds.width / targetWidthPx).coerceAtLeast(.001f)
            val startScaleY = (session.startBounds.height / targetHeightPx).coerceAtLeast(.001f)
            // 线性插值：进度 0 保持来源缩放，进度 1 恢复原始大小
            val currentScaleX = startScaleX + (1f - startScaleX) * p
            val currentScaleY = startScaleY + (1f - startScaleY) * p
            // 位移同样在来源与目标边界之间插值
            translationX =
              session.startBounds.left + (session.endBounds.left - session.startBounds.left) * p
            translationY =
              session.startBounds.top + (session.endBounds.top - session.startBounds.top) * p
            scaleX = currentScaleX
            scaleY = currentScaleY
            // 以左上角为缩放锚点，与卡片实际位置对齐
            transformOrigin = TransformOrigin(0f, 0f)
            alpha = session.overlayAlpha.value.coerceIn(0f, 1f)
            // 圆角随缩放反向补偿，保证视觉圆角大小在缩放过程中恒定
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

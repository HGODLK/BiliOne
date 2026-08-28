package dev.openbili.webdemo.video

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private const val LONG_IMAGE_HORIZONTAL_DOMINANCE_RATIO = 1.35f
private const val LONG_IMAGE_PAGE_DISTANCE_TOUCH_SLOP_MULTIPLIER = 3f

internal enum class CommentImageGestureAxis {
  UNDECIDED,
  HORIZONTAL,
  VERTICAL,
}

internal data class CommentImageTransform(
  val scale: Float,
  val panOffset: Offset,
)

/** 在未缩放页面坐标中计算下一帧变换，平移量始终使用屏幕像素。 */
internal fun updatedCommentImageTransform(
  currentScale: Float,
  currentPanOffset: Offset,
  centroid: Offset,
  zoomChange: Float,
  panChange: Offset,
  contentWidth: Float,
  contentHeight: Float,
  viewportWidth: Float,
  viewportHeight: Float,
): CommentImageTransform {
  val safeCurrentScale = currentScale.coerceAtLeast(.001f)
  val nextScale = (safeCurrentScale * zoomChange).coerceIn(1f, 5f)
  if (nextScale <= 1.001f) return CommentImageTransform(1f, Offset.Zero)

  val scaleChange = nextScale / safeCurrentScale
  val viewportCenter = Offset(viewportWidth / 2f, viewportHeight / 2f)
  val effectiveCentroid =
    if (centroid.x.isFinite() && centroid.y.isFinite()) centroid else viewportCenter
  val candidate =
    currentPanOffset * scaleChange +
      (effectiveCentroid - viewportCenter) * (1f - scaleChange) +
      panChange
  val maxPanX = commentImagePanLimit(contentWidth, viewportWidth, nextScale)
  val maxPanY = commentImagePanLimit(contentHeight, viewportHeight, nextScale)
  return CommentImageTransform(
    scale = nextScale,
    panOffset =
      Offset(
        candidate.x.coerceIn(-maxPanX, maxPanX),
        candidate.y.coerceIn(-maxPanY, maxPanY),
      ),
  )
}

/** 超长图优先保留纵向滚动，只有明显横向的移动才取得翻页手势。 */
internal fun classifyLongCommentImageGesture(
  totalPan: Offset,
  touchSlop: Float,
): CommentImageGestureAxis {
  if (totalPan.getDistance() <= touchSlop) return CommentImageGestureAxis.UNDECIDED
  return if (abs(totalPan.x) > abs(totalPan.y) * LONG_IMAGE_HORIZONTAL_DOMINANCE_RATIO) {
    CommentImageGestureAxis.HORIZONTAL
  } else {
    CommentImageGestureAxis.VERTICAL
  }
}

internal fun longCommentImagePageDelta(totalPanX: Float, minimumDistance: Float): Int =
  when {
    totalPanX <= -minimumDistance -> 1
    totalPanX >= minimumDistance -> -1
    else -> 0
  }

/** 观察超长图手势；纵向事件不消费，明确横向后才阻止 WebView 继续处理。 */
internal suspend fun PointerInputScope.detectLongCommentImagePageSwipes(
  onPageDelta: (Int) -> Unit,
) {
  awaitEachGesture {
    val firstDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val trackedPointerId = firstDown.id
    val touchSlop = viewConfiguration.touchSlop
    val pageDistance =
      maxOf(touchSlop * LONG_IMAGE_PAGE_DISTANCE_TOUCH_SLOP_MULTIPLIER, 48.dp.toPx())
    var totalPan = Offset.Zero
    var axis = CommentImageGestureAxis.UNDECIDED

    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      val change = event.changes.firstOrNull { it.id == trackedPointerId } ?: break
      totalPan += change.position - change.previousPosition
      if (axis == CommentImageGestureAxis.UNDECIDED) {
        axis = classifyLongCommentImageGesture(totalPan, touchSlop)
      }
      if (axis == CommentImageGestureAxis.HORIZONTAL) change.consume()
      if (!change.pressed) break
    }

    if (axis == CommentImageGestureAxis.HORIZONTAL) {
      longCommentImagePageDelta(totalPan.x, pageDistance)
        .takeIf { it != 0 }
        ?.let(onPageDelta)
    }
  }
}

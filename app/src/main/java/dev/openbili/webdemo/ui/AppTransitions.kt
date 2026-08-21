package dev.openbili.webdemo.ui

/**
 * 根页面与个人资料页之间的共享转场 UI 组件。
 *
 * 提供三类转场渲染器：个人资料页的矩形铺开背景（ProfileTransitionBackground）、
 * 头像从卡片飞向资料页顶栏的共享元素（SharedProfileAvatar），以及覆盖全部保留式
 * 根页面展开的通用卡片转场（ExpandingPageTransitionOverlay / CardTransitionOverlay）。
 * 同时定义转场阶段模型 [TransitionPhase] 与视频来源枚举 [VideoOrigin]，供 AppRoot
 * 拆分出的各文件在共享同一份状态时引用。
 */

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import kotlin.math.roundToInt

private fun lerpTransitionValue(start: Float, end: Float, progress: Float): Float =
  start + (end - start) * progress

/** 共享封面未命中缓存时的兜底尺寸：竖版与详情海报一致，避免转场解码超大位图。 */
internal const val TRANSITION_POSTER_REQUEST_WIDTH = 360
internal const val TRANSITION_POSTER_REQUEST_HEIGHT = 480
internal const val TRANSITION_LANDSCAPE_REQUEST_WIDTH = 672
internal const val TRANSITION_LANDSCAPE_REQUEST_HEIGHT = 378

/**
 * 个人资料页从来源卡片铺开进入时的转场背景。
 *
 * 底层是一块随进度淡入的纯黑压暗层；上层是用 graphicsLayer 模拟的页面 Surface：
 * 初始只覆盖来源卡片区域，随 [progress] 逐渐放大并平移到铺满全屏，同时圆角从
 * 卡片圆角平滑收敛到直角，形成"卡片展开成页面"的视觉效果。
 */
@Composable
internal fun ProfileTransitionBackground(
  sourceBounds: Rect,
  progress: () -> Float,
  dimAlpha: Float,
  revealFromTransparent: Boolean = false,
  surfaceAlpha: () -> Float = { 1f },
) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    // ── 依据屏幕尺寸与来源边界计算动画参数 ─────────────────────────────
    val density = LocalDensity.current
    val screenWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
    val screenHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
    val fallbackSize = with(density) { 48.dp.toPx() }
    // 来源边界非法时退回屏幕正中的 48dp 方块，避免除零或不可见的起始状态
    val source =
      sourceBounds.takeIf { it.width > 0f && it.height > 0f }
        ?: Rect(
          left = (screenWidthPx - fallbackSize) / 2f,
          top = (screenHeightPx - fallbackSize) / 2f,
          right = (screenWidthPx + fallbackSize) / 2f,
          bottom = (screenHeightPx + fallbackSize) / 2f,
        )
    // 初始缩放：来源尺寸相对全屏尺寸的比例，下限 0.001 防止退化为 0
    val startScaleX = (source.width / screenWidthPx).coerceAtLeast(.001f)
    val startScaleY = (source.height / screenHeightPx).coerceAtLeast(.001f)

    // 黑色压暗层：透明度由进度、压暗系数与表面透明度共同决定
    Box(
      Modifier.fillMaxSize()
        .graphicsLayer {
          alpha = progress().coerceIn(0f, 1f) * dimAlpha * surfaceAlpha().coerceIn(0f, 1f)
        }
        .background(Color.Black)
    )
    // 页面 Surface：随进度缩放、平移，并将圆角逐渐收敛为 0
    Surface(
      modifier =
        Modifier.fillMaxSize().graphicsLayer {
          val p = progress().coerceIn(0f, 1f)
          // 从来源尺寸线性插值到全屏尺寸
          val pageScaleX = startScaleX + (1f - startScaleX) * p
          val pageScaleY = startScaleY + (1f - startScaleY) * p
          scaleX = pageScaleX
          scaleY = pageScaleY
          // 以来源左上角为原点做平移，使卡片左顶角始终锚定原位
          translationX = source.left * (1f - p)
          translationY = source.top * (1f - p)
          transformOrigin = TransformOrigin(0f, 0f)
          // revealFromTransparent 时前 18% 进度专用于淡入
          alpha =
            (if (revealFromTransparent) (p / .18f).coerceIn(0f, 1f) else 1f) *
              surfaceAlpha().coerceIn(0f, 1f)
          // 圆角除以纵向缩放，抵消非等比缩放造成的圆角拉伸
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

/**
 * 头像转场前景容器。
 *
 * 在整屏 Box 内放置共享头像组件；若来源边界非法，则退回左上角的 36dp 方块作为
 * 起始位置，保证头像始终有一个可见的出发锚点。
 */
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
    // 来源边界非法时退回 36dp 方块（与顶栏头像尺寸一致）
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

/**
 * 头像共享元素：从来源位置飞向目标位置，并随进度缩放。
 *
 * 来源与目标矩形都先归一化为内接正方形（以最短边为边长、以中心为圆心），保证圆形
 * 头像在任意矩形内都不会变形；随后用 graphicsLayer 的平移与等比缩放完成动画。
 */
@Composable
internal fun SharedProfileAvatar(
  face: String,
  name: String,
  sourceBounds: Rect,
  targetBounds: Rect,
  progress: () -> Float,
) {
  val density = LocalDensity.current
  // 把矩形归一化为居中的内接正方形，避免非正方形边界造成头像椭圆变形
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
  // 目标边界非法时保持原位置（不缩放）
  val target = targetBounds.takeIf { it.width > 0f && it.height > 0f }?.centeredSquare() ?: source
  // 目标相对来源的边长比例，即最终的缩放倍率
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
          // 位置与缩放均随进度线性插值，以来源左上角为原点
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

/**
 * 轻量的"矩形到页面"铺开 Surface，供所有保留式根页面展开复用。
 */
@Composable
internal fun ExpandingPageTransitionOverlay(
  sourceBounds: Rect,
  progress: () -> Float,
  overlayAlpha: () -> Float = { 1f },
  scrimAlpha: () -> Float = progress,
  sourceCornerRadius: Dp = 22.dp,
  targetCornerRadius: Dp = 20.dp,
) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    // ── 依据屏幕尺寸、内边距与来源边界计算目标/起始几何参数 ─────────────
    val density = LocalDensity.current
    val screenWidthPx = with(density) { maxWidth.toPx() }
    val screenHeightPx = with(density) { maxHeight.toPx() }
    // 目标页面四周各留 16dp 内边距
    val insetPx = with(density) { 16.dp.toPx() }
    val targetWidthPx = (screenWidthPx - insetPx * 2f).coerceAtLeast(1f)
    val targetHeightPx = (screenHeightPx - insetPx * 2f).coerceAtLeast(1f)
    val fallbackWidth = with(density) { 330.dp.toPx() }.coerceAtMost(targetWidthPx)
    val fallbackHeight = with(density) { 44.dp.toPx() }
    // 来源边界非法时退回右上角的默认搜索框区域
    val source =
      sourceBounds.takeIf { it.width > 0f && it.height > 0f }
        ?: Rect(
          left = screenWidthPx - insetPx - fallbackWidth,
          top = insetPx,
          right = screenWidthPx - insetPx,
          bottom = insetPx + fallbackHeight,
        )
    // 背景压暗层：让来源页面在展开期间保持可读
    Box(
      Modifier.fillMaxSize()
        .graphicsLayer { alpha = scrimAlpha().coerceIn(0f, 1f) }
        .background(MaterialTheme.colorScheme.background)
    )
    // 铺开中的页面 Surface
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
            // 起始缩放为来源尺寸相对目标尺寸的比例，随进度线性插值到 1
            val startScaleX = (source.width / targetWidthPx).coerceAtLeast(.001f)
            val startScaleY = (source.height / targetHeightPx).coerceAtLeast(.001f)
            val currentScaleX = startScaleX + (1f - startScaleX) * p
            val currentScaleY = startScaleY + (1f - startScaleY) * p
            scaleX = currentScaleX
            scaleY = currentScaleY
            translationX = (source.left - insetPx) * (1f - p)
            translationY = (source.top - insetPx) * (1f - p)
            transformOrigin = TransformOrigin(0f, 0f)
            // 圆角半径随进度从来源圆角插值到目标圆角
            val apparentCorner =
              sourceCornerRadius.value + (targetCornerRadius.value - sourceCornerRadius.value) * p
            // 页面收起/展开时 X、Y 使用不同缩放比例：普通圆角矩形会被拉伸成椭圆，
            // 因此逐轴补偿，让可见圆角始终精确贴合来源搜索框。
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

/**
 * 非等比缩放下的圆角形状。
 *
 * 页面在转场时 X/Y 缩放不同，若直接用普通圆角会被拉成椭圆。此形状把"期望呈现的
 * 圆角半径"按两个轴的缩放比例反除回去，再各自夹到不超过边长一半，从而抵消拉伸。
 */
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
    // 逐轴除以缩放系数得到原始圆角，并夹到不超过对应边长的一半
    val radiusX = (apparentRadiusPx / scaleX.coerceAtLeast(.001f)).coerceIn(0f, size.width / 2f)
    val radiusY = (apparentRadiusPx / scaleY.coerceAtLeast(.001f)).coerceIn(0f, size.height / 2f)
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

// ── 转场阶段 ──────────────────────────────────────────────────

/**
 * 视频内容的来源页面。
 *
 * 用于标记当前视频是从哪个根页面进入的，转场与返回逻辑据此选择正确的退出方向。
 */
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

/**
 * 转场阶段模型。
 *
 * 描述根页面与视频页之间的状态机：停留在信息流（Feed）、进入视频（ToVideo）、
 * 停留在视频页（Video）、返回信息流（ToFeed）以及在视频间前后切换（ToPreviousVideo）。
 */
internal sealed interface TransitionPhase {
  /** 停留在信息流页面。 */
  data object Feed : TransitionPhase

  /** 正从卡片进入视频页；fromVideo 标记是否来自另一个视频（而非信息流）。 */
  data class ToVideo(
    val item: FeedItem,
    val cardBounds: Rect?,
    val fromVideo: Boolean = false,
  ) : TransitionPhase

  /** 停留在视频页，sourceBounds 记录进入时的来源卡片边界。 */
  data class Video(val item: FeedItem, val sourceBounds: Rect?) : TransitionPhase

  /** 正从视频页返回信息流。 */
  data class ToFeed(val item: FeedItem, val cardBounds: Rect?) : TransitionPhase

  /** 在视频页内切换到前一个/后一个视频。 */
  data class ToPreviousVideo(
    val departingItem: FeedItem,
    val previousItem: FeedItem,
    val cardBounds: Rect,
    val previousSourceBounds: Rect?,
  ) : TransitionPhase
}

// ── 卡片转场遮罩（整卡、仅 graphicsLayer 动画） ──────────────────────

/**
 * 整卡转场遮罩：把一张封面卡片从 [startBounds] 动画到 [endBounds]。
 *
 * 仅用 graphicsLayer 的平移与缩放完成动画，不触碰布局；封面优先使用已解码的位图，
 * 缺失时按需退回异步图片或纯黑占位。用于视频进入/退出时覆盖在页面之上的封面。
 */
@Composable
internal fun CardTransitionOverlay(
  item: FeedItem,
  startBounds: Rect,
  endBounds: Rect,
  progress: () -> Float,
  overlayAlpha: () -> Float,
  fitCover: Boolean = false,
  // 控制器专用页最终始终铺满窗口，但竖版来源仍需读取未裁剪原图。
  fillViewportCrop: Boolean = false,
  coverDimAlpha: () -> Float = { 0f },
  // 控制器专用页的封面转场按矩形尺寸变化，避免 graphicsLayer 分别缩放 X/Y 造成拉伸。
  preserveAspectRatio: Boolean = false,
  modifier: Modifier = Modifier,
  bitmap: android.graphics.Bitmap? = null,
  allowAsyncImageFallback: Boolean = true,
) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val viewportBounds =
      Rect(
        0f,
        0f,
        with(density) { maxWidth.toPx() },
        with(density) { maxHeight.toPx() },
      )
    val targetBounds =
      if (fillViewportCrop && viewportBounds.width > 0f && viewportBounds.height > 0f) {
        viewportBounds
      } else {
        endBounds
      }
    val p = progress().coerceIn(0f, 1f)
    val currentBounds =
      Rect(
        lerpTransitionValue(startBounds.left, targetBounds.left, p),
        lerpTransitionValue(startBounds.top, targetBounds.top, p),
        lerpTransitionValue(startBounds.right, targetBounds.right, p),
        lerpTransitionValue(startBounds.bottom, targetBounds.bottom, p),
      )
    // 不要 remember 一次缓存未命中：退出准备可能在本组合体首次出现后才加载完完整海报，
    // 若缓存了 null 会强制显示一个可见的异步占位图。
    val transitionBitmap =
      bitmap ?: LoadedFeedImageRegistry.bitmap(
        item.coverUrl,
        requireUncropped = fitCover || fillViewportCrop,
      )

    val transitionModifier =
      if (preserveAspectRatio) {
        Modifier
          .offset {
            IntOffset(currentBounds.left.roundToInt(), currentBounds.top.roundToInt())
          }
          .size(
            with(density) { currentBounds.width.coerceAtLeast(1f).toDp() },
            with(density) { currentBounds.height.coerceAtLeast(1f).toDp() },
          )
          .graphicsLayer { alpha = overlayAlpha().coerceIn(0f, 1f) }
      } else {
        val targetWidthPx = targetBounds.width.coerceAtLeast(1f)
        val targetHeightPx = targetBounds.height.coerceAtLeast(1f)
        Modifier
          .size(
            with(density) { targetWidthPx.toDp() },
            with(density) { targetHeightPx.toDp() },
          )
          .graphicsLayer {
            // 保留原有卡片转场的 graphicsLayer 路径，普通触屏页面不改变动画表现。
            val startScaleX = (startBounds.width / targetWidthPx).coerceAtLeast(0.001f)
            val startScaleY = (startBounds.height / targetHeightPx).coerceAtLeast(0.001f)
            val currentScaleX = startScaleX + (1f - startScaleX) * p
            val currentScaleY = startScaleY + (1f - startScaleY) * p
            translationX = startBounds.left + (targetBounds.left - startBounds.left) * p
            translationY = startBounds.top + (targetBounds.top - startBounds.top) * p
            scaleX = currentScaleX
            scaleY = currentScaleY
            transformOrigin = TransformOrigin(0f, 0f)
            alpha = overlayAlpha().coerceIn(0f, 1f)
            val cornerScale = minOf(currentScaleX, currentScaleY).coerceAtLeast(.001f)
            val cornerRadius =
              when {
                fillViewportCrop -> 0.dp
                fitCover -> 16.dp
                else -> VideoShapeTokens.CornerRadius
              }
            shape = RoundedCornerShape((cornerRadius.value / cornerScale).dp)
            clip = true
          }
      }
    val transitionShape =
      if (preserveAspectRatio) {
        when {
          fillViewportCrop -> RectangleShape
          fitCover -> RoundedCornerShape(16.dp)
          else -> RoundedCornerShape(VideoShapeTokens.CornerRadius)
        }
      } else {
        RectangleShape
      }

    Surface(
      modifier = transitionModifier,
      shape = transitionShape,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
    ) {
      // 内容层：先绘制封面，再按需叠加一层压暗
      Box(
        Modifier.fillMaxSize().drawWithContent {
          drawContent()
          val dimAlpha = coverDimAlpha().coerceIn(0f, 1f)
          if (dimAlpha > 0f) drawRect(Color.Black.copy(alpha = dimAlpha))
        }
      ) {
        // 优先显示已解码位图；否则异步加载，仍不允许时退回纯黑
        if (transitionBitmap != null) {
          Image(
            bitmap = transitionBitmap.asImageBitmap(),
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale =
              if (preserveAspectRatio || fillViewportCrop || !fitCover) ContentScale.Crop
              else ContentScale.Fit,
          )
        } else if (allowAsyncImageFallback) {
          CoverImage(
            coverUrl = item.coverUrl,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            enforceAspectRatio = false,
            fadeIn = false,
            alwaysLoad = true,
            requestWidth =
              if (fitCover || fillViewportCrop) TRANSITION_POSTER_REQUEST_WIDTH
              else TRANSITION_LANDSCAPE_REQUEST_WIDTH,
            requestHeight =
              if (fitCover || fillViewportCrop) TRANSITION_POSTER_REQUEST_HEIGHT
              else TRANSITION_LANDSCAPE_REQUEST_HEIGHT,
            contentScale =
              if (preserveAspectRatio || fillViewportCrop || !fitCover) ContentScale.Crop
              else ContentScale.Fit,
          )
        } else {
          Box(Modifier.fillMaxSize().background(Color.Black))
        }
      }
    }
  }
}

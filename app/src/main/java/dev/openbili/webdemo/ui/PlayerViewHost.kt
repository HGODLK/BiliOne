package dev.openbili.webdemo.ui

import android.graphics.Color
import android.graphics.Outline
import android.graphics.RectF
import android.os.Build
import android.os.Looper
import android.os.MessageQueue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import androidx.annotation.OptIn
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import dev.openbili.webdemo.R
import dev.openbili.webdemo.settings.SubtitleHorizontalPosition
import dev.openbili.webdemo.settings.SubtitleStyle
import dev.openbili.webdemo.video.DanmakuOverlayView
import java.util.WeakHashMap
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 等待空闲消息只能让出帧预算；它绝不能成为播放的门槛。
 *
 * 连续的动画/布局工作流可能合理地让主队列保持非空闲。这种情况下，有界的等待让进入
 * 管线继续，而不是让加载状态一直可见到之后的某次触摸恰好制造出空闲间隙。
 */
internal const val MAIN_QUEUE_IDLE_WAIT_TIMEOUT_MS = 48L

internal suspend fun awaitMainMessageQueueIdle() {
  check(Looper.myLooper() == Looper.getMainLooper())
  withTimeoutOrNull(MAIN_QUEUE_IDLE_WAIT_TIMEOUT_MS) {
    suspendCancellableCoroutine<Unit> { continuation ->
      val queue = Looper.myQueue()
      val idleHandler = MessageQueue.IdleHandler {
        if (continuation.isActive) continuation.resume(Unit)
        false
      }
      queue.addIdleHandler(idleHandler)
      continuation.invokeOnCancellation { queue.removeIdleHandler(idleHandler) }
    }
  }
}

@OptIn(UnstableApi::class)
internal fun createPlayerView(
  ctx: android.content.Context,
  initialPlayer: Player? = null,
): PlayerView =
  (LayoutInflater.from(ctx).inflate(R.layout.player_view_surface, null, false) as PlayerView)
    .apply {
      player = initialPlayer
      useController = false
      setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
      // 这是唯一的应用根 SurfaceView。Compose 为内嵌/全屏动画改变其真实布局边界；
      // 不涉及表面目标交接。
      setEnableComposeSurfaceSyncWorkaround(true)
      val roundedOutline =
        MutableRoundedOutlineProvider(PLAYER_CORNER_RADIUS_DP * resources.displayMetrics.density)
      outlineProvider = roundedOutline
      playerOutlineProviders[this] = roundedOutline
      clipToOutline = true
      // PlayerView 在充气期间把此视图绑定到 Media3 文本轨道。
      findViewById<SubtitleView>(androidx.media3.ui.R.id.exo_subtitles)?.apply {
        setUserDefaultStyle()
        setUserDefaultTextSize()
      }
    }

/**
 * 番剧推荐预览位于移动中的 Compose 页内。TextureView 与该页一起组合，不同于拥有自己
 * SurfaceControl 图层的详情播放器 SurfaceView。
 */
@OptIn(UnstableApi::class)
internal fun createTexturePlayerView(
  ctx: android.content.Context,
  initialPlayer: Player? = null,
): PlayerView =
  (LayoutInflater.from(ctx).inflate(R.layout.player_view_texture, null, false) as PlayerView)
    .apply {
      player = initialPlayer
      useController = false
      setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
      val roundedOutline =
        MutableRoundedOutlineProvider(PLAYER_CORNER_RADIUS_DP * resources.displayMetrics.density)
      outlineProvider = roundedOutline
      playerOutlineProviders[this] = roundedOutline
      clipToOutline = true
    }

@OptIn(UnstableApi::class)
internal fun PlayerView.updateSubtitlePresentation(
  visible: Boolean,
  style: SubtitleStyle,
) {
  val subtitleView = findViewById<SubtitleView>(androidx.media3.ui.R.id.exo_subtitles) ?: return
  subtitleView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
  val foregroundColor = colorWithOpacity(style.textColor, style.textOpacity.coerceIn(.1f, 1f))
  val edgeColor = if (isDarkSubtitleColor(style.textColor)) Color.WHITE else Color.BLACK
  subtitleView.setApplyEmbeddedStyles(false)
  subtitleView.setApplyEmbeddedFontSizes(false)
  subtitleView.setStyle(
    CaptionStyleCompat(
      foregroundColor,
      colorWithOpacity(Color.BLACK, style.backgroundOpacity.coerceIn(0f, 1f)),
      Color.TRANSPARENT,
      CaptionStyleCompat.EDGE_TYPE_OUTLINE,
      edgeColor,
      null,
    )
  )
  val textSizeFraction =
    SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * style.fontScale.coerceIn(.4f, 1.8f)
  subtitleView.setFractionalTextSize(textSizeFraction)
  if (subtitleView.width <= 0 || subtitleView.height <= 0) {
    subtitleView.post { updateSubtitlePresentation(visible, style) }
    return
  }
  val density = resources.displayMetrics.density
  val safeInsets = subtitleView.safeInsetsInsideView()
  val edgeMargin = 16f * density
  val safeStart = safeInsets.left + edgeMargin
  val safeEnd = subtitleView.width - safeInsets.right - edgeMargin
  val safeWidth = (safeEnd - safeStart).coerceAtLeast(1f)
  val targetCenterX =
    when (style.horizontalPosition) {
      SubtitleHorizontalPosition.LEFT -> safeStart + safeWidth * .22f
      SubtitleHorizontalPosition.CENTER -> safeStart + safeWidth * .5f
      SubtitleHorizontalPosition.RIGHT -> safeStart + safeWidth * .78f
    }
  subtitleView.animate().cancel()
  subtitleView.translationX = targetCenterX - subtitleView.width * .5f
  subtitleView.translationY = 0f

  val bottomPaddingFraction = (safeInsets.bottom + edgeMargin) / subtitleView.height
  subtitleView.setBottomPaddingFraction(bottomPaddingFraction.coerceIn(.02f, .95f))
}

private data class SubtitleSafeInsets(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
)

private fun View.safeInsetsInsideView(): SubtitleSafeInsets {
  val compatInsets =
    ViewCompat.getRootWindowInsets(this)
      ?.getInsetsIgnoringVisibility(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
  var safeLeft = compatInsets?.left ?: 0
  var safeTop = compatInsets?.top ?: 0
  var safeRight = compatInsets?.right ?: 0
  var safeBottom = compatInsets?.bottom ?: 0
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val insets = rootWindowInsets
    val topLeft =
      insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
    val topRight =
      insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0
    val bottomLeft =
      insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
    val bottomRight =
      insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
    safeLeft = max(safeLeft, max(topLeft, bottomLeft))
    safeTop = max(safeTop, max(topLeft, topRight))
    safeRight = max(safeRight, max(topRight, bottomRight))
    safeBottom = max(safeBottom, max(bottomLeft, bottomRight))
  }

  val location = IntArray(2)
  getLocationInWindow(location)
  val unshiftedLeft = location[0] - translationX.roundToInt()
  val unshiftedTop = location[1] - translationY.roundToInt()
  val rootWidth = rootView.width
  val rootHeight = rootView.height
  return SubtitleSafeInsets(
    left = (safeLeft - unshiftedLeft).coerceIn(0, width).toFloat(),
    top = (safeTop - unshiftedTop).coerceIn(0, height).toFloat(),
    right = (unshiftedLeft + width - (rootWidth - safeRight)).coerceIn(0, width).toFloat(),
    bottom = (unshiftedTop + height - (rootHeight - safeBottom)).coerceIn(0, height).toFloat(),
  )
}

private fun colorWithOpacity(color: Int, opacity: Float): Int =
  ((opacity.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (color and 0xFFFFFF)

private fun isDarkSubtitleColor(color: Int): Boolean {
  val red = color shr 16 and 0xFF
  val green = color shr 8 and 0xFF
  val blue = color and 0xFF
  return red * 299 + green * 587 + blue * 114 < 128_000
}

@OptIn(UnstableApi::class)
private data class DanmakuViewportBinding(
  val overlay: DanmakuOverlayView,
  val contentFrame: AspectRatioFrameLayout,
  val listener: View.OnLayoutChangeListener,
)

@OptIn(UnstableApi::class)
private val danmakuViewportBindings = WeakHashMap<PlayerView, DanmakuViewportBinding>()

/** 把 Media3 的真实视频帧映射到兄弟的全播放器弹幕表面中。 */
@OptIn(UnstableApi::class)
internal fun PlayerView.bindDanmakuVideoViewport(overlay: DanmakuOverlayView) {
  val contentFrame =
    findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame) ?: return
  danmakuViewportBindings.remove(this)?.let { previous ->
    previous.contentFrame.removeOnLayoutChangeListener(previous.listener)
  }
  fun updateViewport() {
    if (width <= 0 || height <= 0 || contentFrame.width <= 0 || contentFrame.height <= 0) return
    val overlayLocation = IntArray(2)
    val contentLocation = IntArray(2)
    overlay.getLocationInWindow(overlayLocation)
    contentFrame.getLocationInWindow(contentLocation)
    val left = (contentLocation[0] - overlayLocation[0]).toFloat()
    val top = (contentLocation[1] - overlayLocation[1]).toFloat()
    overlay.setVideoViewport(RectF(left, top, left + contentFrame.width, top + contentFrame.height))
  }
  val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewport() }
  contentFrame.addOnLayoutChangeListener(listener)
  danmakuViewportBindings[this] = DanmakuViewportBinding(overlay, contentFrame, listener)
  contentFrame.post(::updateViewport)
}

/** 立即隐藏透明 Surface，让 Compose 转场能够拥有顶层。 */
internal fun PlayerView.hideDanmakuForTransition() {
  danmakuViewportBindings[this]?.overlay?.hideForTransition()
}

/**
 * 控制实际的视频 SurfaceView，而不是 PlayerView 的父层级。
 *
 * 在目标平板上 SurfaceView 被提升到独立的 SurfaceControl 图层，因此单独改变 PlayerView
 * 的 alpha 不能可靠地阻止旧解码器缓冲在 Media3 替换源时出现在 Compose 封面之上。
 */
@OptIn(UnstableApi::class)
internal fun PlayerView.updateVideoSurfaceAlpha(alpha: Float) {
  videoSurfaceView?.alpha = alpha.coerceIn(0f, 1f)
}

internal fun PlayerView.updatePlayerCornerRadius(radiusDp: Float) {
  val provider = playerOutlineProviders[this] ?: return
  val radiusPx = resources.displayMetrics.density * radiusDp
  if (kotlin.math.abs(provider.radiusPx - radiusPx) < .25f) return
  provider.radiusPx = radiusPx
  invalidateOutline()
}

private class MutableRoundedOutlineProvider(var radiusPx: Float) : ViewOutlineProvider() {
  override fun getOutline(view: View, outline: Outline) {
    outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
  }
}

private val playerOutlineProviders = WeakHashMap<PlayerView, MutableRoundedOutlineProvider>()

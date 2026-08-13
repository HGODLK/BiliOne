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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
 * Waiting for an idle message must only yield the frame budget; it must never gate playback.
 *
 * A continuous stream of animation/layout work can legitimately keep the main queue non-idle.
 * In that case a bounded wait lets the entry pipeline continue instead of leaving its loading
 * state visible until a later touch happens to create an idle gap.
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
  (LayoutInflater.from(ctx)
      .inflate(R.layout.player_view_surface, null, false) as PlayerView)
    .apply {
      player = initialPlayer
      useController = false
      setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
      // This is the single app-root SurfaceView. Compose changes its real layout bounds for the
      // embedded/fullscreen animation; no surface target handoff is involved.
      setEnableComposeSurfaceSyncWorkaround(true)
      val roundedOutline =
        MutableRoundedOutlineProvider(PLAYER_CORNER_RADIUS_DP * resources.displayMetrics.density)
      outlineProvider = roundedOutline
      playerOutlineProviders[this] = roundedOutline
      clipToOutline = true
      // PlayerView binds this view to Media3 text tracks during inflation.
      findViewById<SubtitleView>(androidx.media3.ui.R.id.exo_subtitles)?.apply {
        setUserDefaultStyle()
        setUserDefaultTextSize()
      }
    }

/**
 * The Bangumi recommendation preview lives inside a moving Compose page. TextureView is composed
 * with that page, unlike the detail player's SurfaceView which has its own SurfaceControl layer.
 */
@OptIn(UnstableApi::class)
internal fun createTexturePlayerView(
  ctx: android.content.Context,
  initialPlayer: Player? = null,
): PlayerView =
  (LayoutInflater.from(ctx)
      .inflate(R.layout.player_view_texture, null, false) as PlayerView)
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
  val subtitleView =
    findViewById<SubtitleView>(androidx.media3.ui.R.id.exo_subtitles) ?: return
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
    bottom =
      (unshiftedTop + height - (rootHeight - safeBottom)).coerceIn(0, height).toFloat(),
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

/** Maps Media3's real video frame into the sibling full-player danmaku surface. */
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
    overlay.setVideoViewport(
      RectF(left, top, left + contentFrame.width, top + contentFrame.height)
    )
  }
  val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewport() }
  contentFrame.addOnLayoutChangeListener(listener)
  danmakuViewportBindings[this] = DanmakuViewportBinding(overlay, contentFrame, listener)
  contentFrame.post(::updateViewport)
}

/** Hides the transparent Surface immediately so a Compose transition can own the top layer. */
internal fun PlayerView.hideDanmakuForTransition() {
  danmakuViewportBindings[this]?.overlay?.hideForTransition()
}

/**
 * Controls the actual video SurfaceView rather than PlayerView's parent hierarchy.
 *
 * SurfaceView is promoted to a separate SurfaceControl layer on the target tablets, so changing
 * PlayerView alpha alone does not reliably prevent an old decoder buffer from appearing above a
 * Compose cover while Media3 replaces the source.
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

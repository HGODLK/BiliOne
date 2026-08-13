package dev.openbili.webdemo.ui

import android.graphics.Outline
import android.graphics.RectF
import android.os.Looper
import android.os.MessageQueue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.openbili.webdemo.R
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.DanmakuMaskTimeline
import dev.openbili.webdemo.video.DanmakuOverlayView
import java.util.WeakHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Wait until startup work has left the main message queue idle before inflating PlayerView. */
internal suspend fun awaitMainMessageQueueIdle() {
  check(Looper.myLooper() == Looper.getMainLooper())
  suspendCancellableCoroutine { continuation ->
    val queue = Looper.myQueue()
    val idleHandler = MessageQueue.IdleHandler {
      if (continuation.isActive) continuation.resume(Unit)
      false
    }
    queue.addIdleHandler(idleHandler)
    continuation.invokeOnCancellation { queue.removeIdleHandler(idleHandler) }
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
      // Danmaku spans the complete player so portrait video does not leave a visually empty
      // letterbox. The Media3 content frame remains available as the smart-mask coordinate space.
      val overlay = DanmakuOverlayView(ctx).apply { tag = DANMAKU_OVERLAY_TAG }
      addView(
        overlay,
        FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.MATCH_PARENT,
        ),
      )
      findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)?.apply {
        clipChildren = true
        clipToPadding = true
        fun updateDanmakuVideoViewport() {
          overlay.setVideoViewport(RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()))
        }
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateDanmakuVideoViewport() }
        post(::updateDanmakuVideoViewport)
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

private const val DANMAKU_OVERLAY_TAG = "player_danmaku_overlay"

internal fun PlayerView.updateDanmakuOverlay(
  items: List<DanmakuItem>,
  mask: DanmakuMaskTimeline?,
  enabled: Boolean,
  smartBlocking: Boolean,
  paused: Boolean,
  fullscreen: Boolean,
  highDynamicRange: Boolean,
  opacity: Float,
  displayArea: Float,
  densityLevel: Int,
  blockLevel: Int,
  fontScale: Float,
  speed: Float,
  positionEpoch: Long,
  positionProvider: () -> Long,
) {
  findViewWithTag<DanmakuOverlayView>(DANMAKU_OVERLAY_TAG)
    ?.update(
      items = items,
      mask = mask,
      enabled = enabled,
      smartBlocking = smartBlocking,
      paused = paused,
      fullscreen = fullscreen,
      highDynamicRange = highDynamicRange,
      opacity = opacity,
      displayArea = displayArea,
      densityLevel = densityLevel,
      blockLevel = blockLevel,
      fontScale = fontScale,
      speed = speed,
      positionEpoch = positionEpoch,
      currentPositionProvider = positionProvider,
    )
}

internal fun PlayerView.setDanmakuTransitionSuppressed(suppressed: Boolean) {
  findViewWithTag<DanmakuOverlayView>(DANMAKU_OVERLAY_TAG)
    ?.setTransitionSuppressed(suppressed)
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

package dev.openbili.webdemo.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PerformanceHintManager
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.openbili.webdemo.R
import dev.openbili.webdemo.api.DANMAKU_COLORFUL_VIP_GRADIENT
import dev.openbili.webdemo.api.DanmakuInlineEmote
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.DanmakuMaskTimeline
import java.util.ArrayDeque
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal sealed interface InlineDanmakuSegment {
  data class Text(val value: String) : InlineDanmakuSegment

  data class Emote(val value: DanmakuInlineEmote) : InlineDanmakuSegment
}

internal fun splitInlineDanmaku(
  content: String,
  emotes: List<DanmakuInlineEmote>,
): List<InlineDanmakuSegment> {
  val candidates =
    emotes
      .filter { it.token.isNotBlank() && it.imageUrl.isNotBlank() && content.contains(it.token) }
      .distinctBy(DanmakuInlineEmote::token)
      .sortedByDescending { it.token.length }
  if (candidates.isEmpty()) return listOf(InlineDanmakuSegment.Text(content))
  return buildList {
    var index = 0
    var textStart = 0
    while (index < content.length) {
      val emote = candidates.firstOrNull { content.startsWith(it.token, index) }
      if (emote == null) {
        index += 1
        continue
      }
      if (index > textStart) add(InlineDanmakuSegment.Text(content.substring(textStart, index)))
      add(InlineDanmakuSegment.Emote(emote))
      index += emote.token.length
      textStart = index
    }
    if (textStart < content.length) add(InlineDanmakuSegment.Text(content.substring(textStart)))
  }
}

/** A main-thread player sample that can be safely advanced by the danmaku render thread. */
internal data class DanmakuPlaybackClockSnapshot(
  val positionMs: Long,
  val sampledAtRealtimeNs: Long,
  val playbackRate: Float,
  val advancing: Boolean,
  val epoch: Long,
) {
  fun positionAt(realtimeNs: Long): Long {
    if (!advancing) return positionMs.coerceAtLeast(0L)
    val elapsedMs =
      (realtimeNs - sampledAtRealtimeNs).coerceAtLeast(0L).toDouble() / NANOS_PER_MILLISECOND
    return (positionMs + elapsedMs * playbackRate.coerceIn(.25f, 3f))
      .roundToLong()
      .coerceAtLeast(0L)
  }

  internal companion object {
    const val NANOS_PER_MILLISECOND = 1_000_000.0
    val Initial =
      DanmakuPlaybackClockSnapshot(
        positionMs = 0L,
        sampledAtRealtimeNs = 0L,
        playbackRate = 1f,
        advancing = false,
        epoch = Long.MIN_VALUE,
      )
  }
}

/**
 * A PlayerView-native danmaku layer.
 *
 * Keeping this view in PlayerView's root overlay covers the full player, including portrait-video
 * sidebars, while the smart-mask path remains mapped to Media3's real content frame.
 */
class DanmakuOverlayView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
  /**
   * State used by Canvas rendering is guarded as one unit. UI callbacks only hold this lock for
   * infrequent configuration/lifecycle changes; the continuous frame loop owns it on the dedicated
   * render thread, so Compose scrolling never executes danmaku drawing work.
   */
  private val renderStateLock = Any()
  private val density = resources.displayMetrics.density
  private val scaledDensity = density * resources.configuration.fontScale
  private val textPaint =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      typeface =
        ResourcesCompat.getFont(context, R.font.sarasa_ui_sc_bold)
          ?: android.graphics.Typeface.DEFAULT_BOLD
      textAlign = Paint.Align.LEFT
    }
  private val outlinePaint =
    Paint(textPaint).apply {
      color = Color.argb(210, 0, 0, 0)
      style = Paint.Style.STROKE
      strokeWidth = 1.7f * density
      strokeJoin = Paint.Join.ROUND
    }
  private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
  private var scheduled = emptyList<ScheduledDanmaku>()
  private var sourceItemsRef: List<DanmakuItem>? = null
  private var sourceItems = emptyList<DanmakuItem>()
  private var maskTimeline: DanmakuMaskTimeline? = null
  private var videoViewport: RectF? = null
  private var smartBlockingEnabled = false
  private var cachedMaskFrameIndex = -1
  private var cachedMaskViewportLeft = 0f
  private var cachedMaskViewportTop = 0f
  private var cachedMaskViewportWidth = 0f
  private var cachedMaskViewportHeight = 0f
  private var cachedProtectedMaskPath: Path? = null
  private val measurements = HashMap<DanmakuItem, DanmakuMeasurement>()
  private val inlineSegmentCache = HashMap<DanmakuItem, List<InlineDanmakuSegment>>()
  private var prepared = emptyList<PreparedDanmaku>()
  private var scheduledLaneCount = 0
  private var scheduledViewportWidth = 0
  private var scheduledLaneHeight = 0f
  private var uiPositionProvider: () -> Long = { 0L }
  private var uiPlaybackRateProvider: () -> Float = { 1f }
  private var uiPositionEpoch = Long.MIN_VALUE
  private var uiClockAdvancing = false
  private var uiClockSamplingEnabled = false
  @Volatile private var playbackClockSnapshot = DanmakuPlaybackClockSnapshot.Initial
  private var displayEnabled = false
  private var transitionSuppressed = false
  private var playbackPaused = true
  private var fullscreen = false
  private var highDynamicRange = false
  private var opacity = .72f
  private var displayArea = .75f
  private var densityLevel = 3
  private var blockLevel = 1
  private var fontScale = 1f
  private var speed = 1f
  private var animationRunning = false
  private var surfaceAvailable = false
  private var surfaceWidth = 0
  private var surfaceHeight = 0
  private var renderViewWidth = 0
  private var renderViewHeight = 0
  private var renderSafeInsetTop = 0
  private var renderPosted = false
  private var requestedFrameRateHz = Float.NaN
  private var requestedSurfaceFrameRateHz = Float.NaN
  private var uiHighFrameRateRequested = false
  @Volatile private var renderAttached = false
  @Volatile private var renderGeneration = 0L
  @Volatile private var renderLoop: RenderLoop? = null
  private var embeddedHost: ViewGroup? = null
  private var reparenting = false
  private var frozenPositionMs = 0L
  private var frozenMaskPositionMs = 0L
  // The player clock can jump several seconds while the decoder/compositor is catching up during
  // startup. Keep a visual clock so an already-visible comment is not discarded before a frame
  // has had a chance to draw it at the left edge.
  private var visualPositionMs = 0L
  private var visualPositionPreciseMs = 0.0
  private var visualClockLastFrameNs = 0L
  private var visualClockInitialized = false
  private var visualClockEpoch = Long.MIN_VALUE
  private var visualClockCatchingUp = false
  private var lastTextCacheQueueBucket = Long.MIN_VALUE
  private val bitmapTextCache = BitmapTextCache()
  private val renderViewport = RectF()
  private val maskViewport = RectF()
  private val mainHandler = Handler(Looper.getMainLooper())
  private val clockSampleRunnable =
    object : Runnable {
      override fun run() {
        if (!renderAttached || !uiClockSamplingEnabled) return
        samplePlaybackClock()
        if (uiHighFrameRateRequested) updateFrameRateHint(rendering = true)
        mainHandler.postDelayed(this, CLOCK_SAMPLE_INTERVAL_MS)
      }
    }
  private val imageBitmaps = LinkedHashMap<String, Bitmap>(32, .75f, true)
  private val imageRequests = HashSet<String>()
  private var imageAllocationBytes = 0L
  private var imageScope = newImageScope()

  /**
   * Choreographer is created on this Looper, so both frame callbacks and Canvas submission stay off
   * the main thread while retaining display-vsync pacing.
   */
  private inner class RenderLoop(val generation: Long) {
    val thread = HandlerThread("DanmakuRender", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    val handler = Handler(thread.looper)
    var choreographer: Choreographer? = null
    var frameCallback: Choreographer.FrameCallback? = null
    var idleWakeRunnable: Runnable? = null
    var performanceHintSession: PerformanceHintManager.Session? = null
    var performanceTargetWorkDurationNs = 0L

    fun initialize() {
      handler.post {
        if (renderLoop !== this || renderGeneration != generation) return@post
        initializePerformanceHint()
        val nextChoreographer = Choreographer.getInstance()
        val nextFrameCallback = Choreographer.FrameCallback {
          synchronized(renderStateLock) {
            if (renderLoop === this && renderGeneration == generation) {
              renderPosted = false
              renderFrame()
            }
          }
        }
        choreographer = nextChoreographer
        frameCallback = nextFrameCallback
        requestRenderOnLoop(this)
      }
    }

    private fun initializePerformanceHint() {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
      val targetWorkDurationNs = currentFrameBudgetNanos()
      performanceHintSession =
        runCatching {
            context
              .getSystemService(PerformanceHintManager::class.java)
              ?.createHintSession(intArrayOf(Process.myTid()), targetWorkDurationNs)
          }
          .getOrNull()
      if (performanceHintSession != null) {
        performanceTargetWorkDurationNs = targetWorkDurationNs
      }
    }

    fun updatePerformanceTarget(targetWorkDurationNs: Long) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
      val session = performanceHintSession ?: return
      if (targetWorkDurationNs == performanceTargetWorkDurationNs) return
      if (runCatching { session.updateTargetWorkDuration(targetWorkDurationNs) }.isSuccess) {
        performanceTargetWorkDurationNs = targetWorkDurationNs
      }
    }

    fun reportActualWorkDuration(actualWorkDurationNs: Long) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
      val session = performanceHintSession ?: return
      runCatching { session.reportActualWorkDuration(actualWorkDurationNs.coerceAtLeast(1L)) }
    }

    fun stop() {
      handler.postAtFrontOfQueue {
        idleWakeRunnable?.let(handler::removeCallbacks)
        frameCallback?.let { callback -> choreographer?.removeFrameCallback(callback) }
        idleWakeRunnable = null
        frameCallback = null
        choreographer = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) performanceHintSession?.close()
        performanceHintSession = null
        performanceTargetWorkDurationNs = 0L
        thread.quitSafely()
      }
    }
  }

  init {
    // Keep the transparent danmaku surface above the app window. A media-overlay SurfaceView is
    // still below that window and punches out its complete bounds; in fullscreen, that hole also
    // removes the Compose cover gradient from the video letterbox and exposes SurfaceFlinger's
    // black fill. An on-top translucent surface preserves the separate 120 Hz buffer while its
    // transparent pixels reveal the brightness-adjusted letterbox underneath.
    setZOrderOnTop(true)
    holder.setFormat(PixelFormat.TRANSLUCENT)
    holder.addCallback(this)
    setWillNotDraw(true)
    isClickable = false
    isFocusable = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
    }
  }

  fun update(
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
    currentPositionProvider: () -> Long,
    currentPlaybackRateProvider: () -> Float = { 1f },
  ) {
    uiPositionProvider = currentPositionProvider
    uiPlaybackRateProvider = currentPlaybackRateProvider
    uiPositionEpoch = positionEpoch
    uiClockAdvancing = !paused
    samplePlaybackClock()
    synchronized(renderStateLock) {
      val nextSpeed = speed.coerceIn(.5f, 2f)
      val speedChanged = this.speed != nextSpeed
      val nextOpacity = opacity.coerceIn(.2f, 1f)
    val opacityChanged = this.opacity != nextOpacity
    val nextDisplayArea = displayArea.coerceIn(.1f, 1f)
    val displayAreaChanged = this.displayArea != nextDisplayArea
    val nextDensityLevel = densityLevel.coerceIn(MIN_DENSITY_LEVEL, MAX_DENSITY_LEVEL)
    val densityLevelChanged = this.densityLevel != nextDensityLevel
    val nextBlockLevel = blockLevel.coerceIn(1, 5)
    val blockLevelChanged = this.blockLevel != nextBlockLevel
    val nextFontScale = fontScale.coerceIn(.7f, 1.5f)
    val fontScaleChanged = this.fontScale != nextFontScale
    val itemsChanged = items !== sourceItemsRef
    val maskChanged = mask !== maskTimeline
    val smartBlockingChanged = smartBlocking != smartBlockingEnabled
    val highDynamicRangeChanged = highDynamicRange != this.highDynamicRange
    val fullscreenChanged = fullscreen != this.fullscreen
    this.speed = nextSpeed
    this.opacity = nextOpacity
    this.displayArea = nextDisplayArea
    this.densityLevel = nextDensityLevel
    this.blockLevel = nextBlockLevel
    this.fontScale = nextFontScale
    maskTimeline = mask
    smartBlockingEnabled = smartBlocking
    this.highDynamicRange = highDynamicRange
    if (itemsChanged) {
      sourceItemsRef = items
      sourceItems = filterDanmakuByBlockLevel(items, nextBlockLevel).sortedBy(DanmakuItem::timeMs)
      requestImages(sourceItems)
    } else if (blockLevelChanged) {
      sourceItems = filterDanmakuByBlockLevel(items, nextBlockLevel).sortedBy(DanmakuItem::timeMs)
      requestImages(sourceItems)
    }
    var renderCacheInvalidated = false
    if (fontScaleChanged) {
      measurements.clear()
      clearRenderCache()
      renderCacheInvalidated = true
    }
    if (itemsChanged || blockLevelChanged) {
      trimItemCaches()
    }
    val scheduleGeometryChanged =
      speedChanged ||
        displayAreaChanged ||
        densityLevelChanged ||
        blockLevelChanged ||
        fontScaleChanged
    val scheduleChanged = scheduleGeometryChanged || itemsChanged
    if (opacityChanged) {
      clearRenderCache()
      renderCacheInvalidated = true
    }
    if (scheduleChanged) {
      rebuildSchedule()
    }
    if (maskChanged || smartBlockingChanged) clearMaskClipCache()
    if (highDynamicRangeChanged) {
        clearRenderCache()
        renderCacheInvalidated = true
      }
      val rawPositionMs = playbackClockSnapshot.positionAt(SystemClock.elapsedRealtimeNanos())
      val clockEpochChanged = visualClockEpoch != positionEpoch
      val clockWasUninitialized = !visualClockInitialized
      if (scheduleGeometryChanged || clockEpochChanged || clockWasUninitialized) {
        resetVisualClock(rawPositionMs, positionEpoch)
      }
      if (
        !highDynamicRange &&
          (itemsChanged || renderCacheInvalidated || clockEpochChanged || clockWasUninitialized)
      )
        queueTextCacheAround(rawPositionMs)
      if (paused) {
        // Preserve a deliberately smoothed on-screen position while the player is paused. Explicit
        // seeks carry a new epoch above and therefore intentionally reset to their target instead.
      frozenPositionMs = if (visualClockInitialized) visualPositionMs else rawPositionMs
        frozenMaskPositionMs = rawPositionMs
      }
      displayEnabled = enabled && scheduled.isNotEmpty()
      uiClockSamplingEnabled = displayEnabled && !paused
      playbackPaused = paused
      this.fullscreen = fullscreen
      if (fullscreenChanged) syncSurfaceSizeFromLayout()
      refreshRenderingState()
      requestRender()
    }
    refreshClockSampling()
  }

  /** The visible Media3 frame, in this full-player overlay's coordinates. */
  fun setVideoViewport(viewport: RectF) {
    val next = viewport.takeIf { it.width() > 0f && it.height() > 0f } ?: return
    synchronized(renderStateLock) {
      val current = videoViewport
      if (
        current != null &&
          abs(current.left - next.left) < .5f &&
          abs(current.top - next.top) < .5f &&
          abs(current.right - next.right) < .5f &&
          abs(current.bottom - next.bottom) < .5f
      )
        return
      videoViewport = RectF(next)
      clearMaskClipCache()
      requestRender()
    }
  }

  /** Avoid relaying out and redrawing the overlay while the SurfaceView bounds animate. */
  fun setTransitionSuppressed(suppressed: Boolean) {
    synchronized(renderStateLock) {
      if (transitionSuppressed == suppressed) return
      if (suppressed) {
        hideForTransition()
      return
      }
      transitionSuppressed = false
      refreshRenderingState()
    }
    // Compose finishes the fullscreen layout while this surface is hidden. Re-apply the final
    // View size when the handoff ends; otherwise some devices keep the embedded buffer size and
    // clip the right/bottom part of the fullscreen danmaku layer.
    syncSurfaceSizeFromLayout()
    requestRender()
  }

  fun setEmbeddedHost(host: ViewGroup) {
    embeddedHost = host
  }

  /**
   * An on-top SurfaceView does not inherit Compose's fullscreen Surface transform on Samsung
   * devices. Once the boundary animation is hidden, attach this same surface directly to the
   * Activity window so its layout is the real fullscreen size; move it back for embedded mode.
   */
  fun moveToFullscreenHost(fullscreen: Boolean): Boolean {
    val target =
      if (fullscreen) context.findActivity()?.window?.decorView as? ViewGroup else embeddedHost
    target ?: return false
    if (parent === target) return false
    reparenting = true
    try {
      (parent as? ViewGroup)?.removeView(this)
      target.addView(
        this,
        ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
        ),
      )
    } finally {
      reparenting = false
    }
    syncSurfaceSizeFromLayout()
    return true
  }

  fun releaseHost() {
    embeddedHost = null
    (parent as? ViewGroup)?.removeView(this)
  }

  /** Clears and hides the SurfaceControl without detaching the view or releasing raster caches. */
  fun hideForTransition() {
    synchronized(renderStateLock) {
      transitionSuppressed = true
      stopFrames()
      renderPosted = false
      // SurfaceView is composed independently from the Compose page. Merely changing View
      // visibility can leave its last submitted buffer on screen until the next ViewRoot
      // traversal, which makes danmaku float above an otherwise fading return animation. Submit
      // one transparent buffer synchronously so the compositor has nothing left to retain.
      clearSurfaceForTransition()
      visibility = INVISIBLE
      // Keep the current window refresh-rate request until the view is released/reparented after
      // the transition. Updating WindowManager.LayoutParams here can relayout the window exactly
      // when the first cover frame is due.
    }
  }

  private fun clearSurfaceForTransition() {
    if (!surfaceAvailable || !holder.surface.isValid) return
    val canvas =
      runCatching {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) holder.lockHardwareCanvas()
          else holder.lockCanvas()
        }
        .getOrNull() ?: return
    try {
      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    } finally {
      runCatching { holder.unlockCanvasAndPost(canvas) }
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    renderAttached = true
    startRenderLoop()
    updateRenderViewGeometry(width, height)
    samplePlaybackClock()
    refreshClockSampling()
    if (!imageScope.isActive) imageScope = newImageScope()
    synchronized(renderStateLock) {
      bitmapTextCache.onAttached()
      requestImages(sourceItems)
      refreshRenderingState()
    }
  }

  override fun onDetachedFromWindow() {
    renderAttached = false
    mainHandler.removeCallbacks(clockSampleRunnable)
    synchronized(renderStateLock) { stopFrames() }
    uiHighFrameRateRequested = false
    updateFrameRateHint(rendering = false)
    if (!reparenting) {
      stopRenderLoop()
      synchronized(renderStateLock) {
        imageScope.cancel()
        imageRequests.clear()
        bitmapTextCache.release()
      }
    }
    super.onDetachedFromWindow()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    // AspectRatioFrameLayout may settle one layout pass after a SurfaceView/window resize.
    // Request a fresh frame immediately so the clip boundary never lingers at its old width.
    if (w != oldw || h != oldh) {
      holder.setSizeFromLayout()
      updateRenderViewGeometry(w, h)
    }
  }

  override fun surfaceCreated(holder: SurfaceHolder) {
    synchronized(renderStateLock) {
      surfaceAvailable = true
      requestedSurfaceFrameRateHz = Float.NaN
      refreshRenderingState()
      requestRender()
    }
  }

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    synchronized(renderStateLock) {
      updateSurfaceSize(width, height)
      clearMaskClipCache()
      requestRender()
    }
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    synchronized(renderStateLock) {
      surfaceAvailable = false
      surfaceWidth = 0
      surfaceHeight = 0
      renderPosted = false
      requestedSurfaceFrameRateHz = Float.NaN
      stopFrames()
    }
  }

  private fun syncSurfaceSizeFromLayout() {
    holder.setSizeFromLayout()
    requestLayout()
    post {
      if (!isAttachedToWindow || transitionSuppressed) return@post
      holder.setSizeFromLayout()
      updateRenderViewGeometry(width, height)
    }
  }

  private fun updateRenderViewGeometry(width: Int, height: Int) {
    val safeInsetTop = ViewCompat.getRootWindowInsets(this)?.displayCutout?.safeInsetTop ?: 0
    synchronized(renderStateLock) {
      renderViewWidth = width
      renderViewHeight = height
      renderSafeInsetTop = safeInsetTop
      clearMaskClipCache()
      rebuildScheduleForViewportIfNeeded()
      requestRender()
    }
  }

  private fun updateSurfaceSize(width: Int, height: Int) {
    if (width <= 0 || height <= 0 || (surfaceWidth == width && surfaceHeight == height)) return
    surfaceWidth = width
    surfaceHeight = height
    clearMaskClipCache()
    rebuildScheduleForViewportIfNeeded()
  }

  private fun scaledVideoViewport(fallback: RectF): RectF {
    val source = videoViewport ?: return fallback
    val viewWidth = renderViewWidth.takeIf { it > 0 }?.toFloat() ?: return fallback
    val viewHeight = renderViewHeight.takeIf { it > 0 }?.toFloat() ?: return fallback
    val scaleX = fallback.width() / viewWidth
    val scaleY = fallback.height() / viewHeight
    maskViewport.set(
      source.left * scaleX,
      source.top * scaleY,
      source.right * scaleX,
      source.bottom * scaleY,
    )
    return maskViewport
  }

  private fun renderWidth(): Int = surfaceWidth.takeIf { it > 0 } ?: renderViewWidth

  private fun renderHeight(): Int = surfaceHeight.takeIf { it > 0 } ?: renderViewHeight

  private fun renderFrame() {
    if (
      !renderAttached ||
        !surfaceAvailable ||
        !holder.surface.isValid ||
        !displayEnabled ||
        transitionSuppressed ||
        renderWidth() <= 0 ||
        renderHeight() <= 0
    ) {
      return
    }
    val loop = renderLoop
    loop?.updatePerformanceTarget(currentFrameBudgetNanos())
    val frameStartNs = SystemClock.elapsedRealtimeNanos()
    val canvas =
      runCatching {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) holder.lockHardwareCanvas()
          else holder.lockCanvas()
        }
        .getOrNull() ?: return
    try {
      updateSurfaceSize(canvas.width, canvas.height)
      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
      drawDanmaku(canvas)
    } finally {
      runCatching { holder.unlockCanvasAndPost(canvas) }
      loop?.reportActualWorkDuration(SystemClock.elapsedRealtimeNanos() - frameStartNs)
    }
  }

  private fun drawDanmaku(canvas: Canvas) {
    if (!displayEnabled || canvas.width <= 0 || canvas.height <= 0) return
    val rawPositionMs =
      playbackClockSnapshot.positionAt(SystemClock.elapsedRealtimeNanos()).coerceAtLeast(0L)
    val positionMs = if (playbackPaused) frozenPositionMs else resolveVisualPosition(rawPositionMs)
    // Text may intentionally smooth a coarse player-clock jump, but the subject mask must stay on
    // the real playback clock so it never trails the independently composed video SurfaceView.
    val maskPositionMs = if (playbackPaused) frozenMaskPositionMs else rawPositionMs
    val scrollingMotionDurationMs = scrollingMotionDurationMs()
    val scrollingVisibleDurationMs = scrollingVisibleDurationMs()
    // The overlay covers the complete player, while smart blocking is mapped only to Media3's
    // actual video frame. This lets portrait videos use their side letterbox for danmaku.
    renderViewport.set(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
    val viewport = renderViewport
    val saveCount = canvas.save()
    canvas.clipRect(viewport)
    applySmartMaskClip(
      canvas = canvas,
      maskPositionMs = maskPositionMs,
      viewport = scaledVideoViewport(viewport),
    )
    // A scrolling label reaches the left edge after its motion duration. It stays in the
    // timeline for a short guard interval to absorb coarse player-clock samples, but it is
    // already entirely off-canvas then; do not keep issuing texture draws for that tail.
    val first = prepared.lowerBound(positionMs - scrollingMotionDurationMs)
    val last = prepared.upperBound(positionMs)
    var lastTextSize = Float.NaN
    var lastColor = Int.MIN_VALUE
    var activeCount = 0
    var index = first
    while (index < last) {
      val value = prepared[index]
      val onScreen =
        value.item.type == TYPE_TOP ||
          value.item.type == TYPE_BOTTOM ||
          positionMs - value.item.timeMs <= scrollingMotionDurationMs
      if (onScreen && positionMs <= value.endMs) {
        activeCount++
        // Bitmap-cache hits set up neither glyph shaping nor mutable Paint state. Avoid touching
        // the RenderNode paint for the common path; it is needed only for HDR/fallback text.
        val needsDirectTextPaint =
            value.item.imageUrl == null &&
            value.item.inlineEmotes.isEmpty() &&
            (highDynamicRange || !bitmapTextCache.contains(value))
        if (needsDirectTextPaint) {
          if (value.textSize != lastTextSize) {
            textPaint.textSize = value.textSize
            outlinePaint.textSize = value.textSize
            lastTextSize = value.textSize
          }
          if (value.item.colorful != DANMAKU_COLORFUL_VIP_GRADIENT && value.color != lastColor) {
            textPaint.shader = null
            textPaint.color = textColor(value.color)
            outlinePaint.shader = null
            outlinePaint.color = outlineColor()
            lastColor = value.color
          }
        }
        drawDanmaku(
          canvas,
          value,
          positionMs,
          scrollingMotionDurationMs,
          scrollingVisibleDurationMs,
          viewport,
        )
      }
      index++
    }
    if (!highDynamicRange) {
      val queueBucket = positionMs / BITMAP_QUEUE_INTERVAL_MS
      if (queueBucket != lastTextCacheQueueBucket) {
        lastTextCacheQueueBucket = queueBucket
        queueTextCacheAhead(positionMs)
      }
    }
    canvas.restoreToCount(saveCount)
    scheduleNextFrame(positionMs, activeCount)
  }

  private fun drawDanmaku(
    canvas: Canvas,
    value: PreparedDanmaku,
    positionMs: Long,
    scrollingMotionDurationMs: Long,
    scrollingVisibleDurationMs: Long,
    viewport: RectF,
  ) {
    val item = value.item
    val fixed = item.type == TYPE_BOTTOM || item.type == TYPE_TOP
    val motionDuration = if (fixed) FIXED_DURATION_MS else scrollingMotionDurationMs
    val visibleDuration = if (fixed) FIXED_DURATION_MS else scrollingVisibleDurationMs
    val elapsed = positionMs - item.timeMs
    if (elapsed !in 0..visibleDuration) return

    val textWidth = value.textWidth
    val laneStep = value.laneStep
    val baseline =
      when (item.type) {
        TYPE_BOTTOM ->
          viewport.top + viewport.height() * displayArea -
            value.lane * laneStep -
            value.laneHeight -
            value.ascent
        TYPE_TOP -> max(viewport.top, fixedTopSafeInset()) + value.lane * laneStep - value.ascent
        else -> viewport.top + value.lane * laneStep - value.ascent
      }
    val x =
      if (fixed) viewport.left + (viewport.width() - textWidth) / 2f
      else viewport.right - elapsed.toFloat() / motionDuration * (viewport.width() + textWidth)
    item.imageUrl?.let { imageUrl ->
      imageBitmaps[imageUrl]?.let { bitmap ->
        imagePaint.alpha = (opacity * 255f).toInt().coerceIn(0, 255)
        val top = baseline + value.ascent
        canvas.drawBitmap(
          bitmap,
          null,
          RectF(x, top, x + value.textWidth, top + value.descent - value.ascent),
          imagePaint,
        )
      }
      return
    }
    if (item.inlineEmotes.isNotEmpty()) {
      drawInlineDanmaku(canvas, value, x, baseline)
      return
    }
    // Stable SDR labels are rasterized only as they enter. This keeps their on-screen lifetime on
    // one small texture path, avoiding tens of thousands of glyph operations every ten seconds.
    if (
      !highDynamicRange &&
        (fixed || elapsed <= motionDuration) &&
        bitmapTextCache.draw(canvas, value, x, baseline)
    ) {
      return
    }
    if (item.colorful == DANMAKU_COLORFUL_VIP_GRADIENT) {
      val gradientTextPaint = Paint(textPaint)
      val gradientOutlinePaint = Paint(outlinePaint)
      configurePaints(
        value = value,
        targetTextPaint = gradientTextPaint,
        targetOutlinePaint = gradientOutlinePaint,
        gradientStartX = 0f,
      )
      canvas.withTranslation(x, 0f) {
        drawText(item.content, 0f, baseline, gradientOutlinePaint)
        drawText(item.content, 0f, baseline, gradientTextPaint)
      }
      return
    }
    canvas.drawText(item.content, x, baseline, outlinePaint)
    canvas.drawText(item.content, x, baseline, textPaint)
  }

  private fun drawInlineDanmaku(
    canvas: Canvas,
    value: PreparedDanmaku,
    startX: Float,
    baseline: Float,
  ) {
    configurePaints(value, textPaint, outlinePaint, startX)
    val metrics = textPaint.fontMetrics
    val imageSize = metrics.descent - metrics.ascent
    var x = startX
    inlineSegments(value.item).forEach { segment ->
      when (segment) {
        is InlineDanmakuSegment.Text -> {
          canvas.drawText(segment.value, x, baseline, outlinePaint)
          canvas.drawText(segment.value, x, baseline, textPaint)
          x += textPaint.measureText(segment.value)
        }
        is InlineDanmakuSegment.Emote -> {
          val width = max(imageSize, textPaint.measureText(segment.value.token))
          val bitmap = imageBitmaps[segment.value.imageUrl]
          if (bitmap != null) {
            imagePaint.alpha = (opacity * 255f).toInt().coerceIn(0, 255)
            val left = x + (width - imageSize) / 2f
            val bottom = baseline + metrics.descent
            canvas.drawBitmap(
              bitmap,
              null,
              RectF(left, bottom - imageSize, left + imageSize, bottom),
              imagePaint,
            )
          } else {
            canvas.drawText(segment.value.token, x, baseline, outlinePaint)
            canvas.drawText(segment.value.token, x, baseline, textPaint)
          }
          x += width
        }
      }
    }
  }

  private fun requestFrame() {
    if (!renderAttached || !surfaceAvailable) return
    animationRunning = true
    requestRender()
  }

  private fun requestRender() {
    val loop = renderLoop ?: return
    if (Looper.myLooper() === loop.handler.looper) {
      requestRenderOnLoop(loop)
    } else {
      loop.handler.post { requestRenderOnLoop(loop) }
    }
  }

  private fun requestRenderOnLoop(loop: RenderLoop) {
    synchronized(renderStateLock) {
      if (
        renderLoop !== loop ||
          renderGeneration != loop.generation ||
          !renderAttached ||
          !surfaceAvailable ||
          transitionSuppressed ||
          !displayEnabled ||
          renderPosted
      )
        return
      val choreographer = loop.choreographer ?: return
      val callback = loop.frameCallback ?: return
      loop.idleWakeRunnable?.let(loop.handler::removeCallbacks)
      loop.idleWakeRunnable = null
      renderPosted = true
      choreographer.postFrameCallback(callback)
    }
  }

  private fun startRenderLoop() {
    if (renderLoop != null) return
    val loop = RenderLoop(renderGeneration + 1L)
    renderGeneration = loop.generation
    renderLoop = loop
    loop.initialize()
  }

  private fun stopRenderLoop() {
    val loop = renderLoop ?: return
    renderLoop = null
    renderGeneration += 1L
    synchronized(renderStateLock) { renderPosted = false }
    loop.stop()
  }

  private fun samplePlaybackClock() {
    if (Looper.myLooper() !== Looper.getMainLooper()) {
      mainHandler.post(::samplePlaybackClock)
      return
    }
    val previous = playbackClockSnapshot
    val sampledAtNs = SystemClock.elapsedRealtimeNanos()
    val sampledPositionMs =
      runCatching(uiPositionProvider).getOrElse { previous.positionAt(sampledAtNs) }
    val sampledPlaybackRate =
      runCatching(uiPlaybackRateProvider).getOrDefault(previous.playbackRate).coerceIn(.25f, 3f)
    playbackClockSnapshot =
      DanmakuPlaybackClockSnapshot(
        positionMs = sampledPositionMs.coerceAtLeast(0L),
        sampledAtRealtimeNs = sampledAtNs,
        playbackRate = sampledPlaybackRate,
        advancing = uiClockAdvancing,
        epoch = uiPositionEpoch,
      )
  }

  private fun refreshClockSampling() {
    mainHandler.removeCallbacks(clockSampleRunnable)
    if (renderAttached && uiClockSamplingEnabled) {
      mainHandler.postDelayed(clockSampleRunnable, CLOCK_SAMPLE_INTERVAL_MS)
    }
  }

  private fun applySmartMaskClip(
    canvas: Canvas,
    maskPositionMs: Long,
    viewport: RectF,
  ) {
    if (!smartBlockingEnabled) return
    val timeline = maskTimeline ?: return
    val frameIndex = timeline.renderFrameIndexAt(maskPositionMs)
    if (frameIndex < 0) return
    val cacheMiss =
      frameIndex != cachedMaskFrameIndex ||
        viewport.left != cachedMaskViewportLeft ||
        viewport.top != cachedMaskViewportTop ||
        viewport.width() != cachedMaskViewportWidth ||
        viewport.height() != cachedMaskViewportHeight
    if (cacheMiss) {
      val allowedContours = timeline.allowedContoursAt(frameIndex)
      val evenOddFill = timeline.usesEvenOddFillAt(frameIndex)
      val viewportMatchesCachedPath =
        viewport.left == cachedMaskViewportLeft &&
          viewport.top == cachedMaskViewportTop &&
          viewport.width() == cachedMaskViewportWidth &&
          viewport.height() == cachedMaskViewportHeight
      val previousProtectedMaskPath = cachedProtectedMaskPath.takeIf { viewportMatchesCachedPath }
      var differenceSucceeded = true
      val nextProtectedMaskPath =
        if (allowedContours.isEmpty()) {
          null
        } else {
          val allowedPath =
            Path().apply {
              fillType = if (evenOddFill) Path.FillType.EVEN_ODD else Path.FillType.WINDING
              var index = 0
              while (index + 1 < allowedContours.size) {
                if (allowedContours[index].isNaN() || allowedContours[index + 1].isNaN()) {
                  index += 2
                  continue
                }
                val start = index
                while (
                  index + 1 < allowedContours.size &&
                    !allowedContours[index].isNaN() &&
                    !allowedContours[index + 1].isNaN()
                ) {
                  index += 2
                }
                appendDirectContour(allowedContours, start, index, viewport)
              }
            }
          // SVG paths always describe the allowed background. Restrict the difference to the
          // actual video frame so portrait sidebars remain available for danmaku.
          Path()
            .apply {
              addRect(viewport, Path.Direction.CW)
              differenceSucceeded = op(allowedPath, Path.Op.DIFFERENCE)
            }
            .takeIf { differenceSucceeded }
        }
      // Skia can reject a few self-intersecting SVG paths. Never pass the pre-operation
      // video rectangle to clipOutPath: retain the previous valid silhouette for this one mask
      // sample (or leave it unclipped on the first sample) and retry on the next timestamp.
      cachedProtectedMaskPath =
        if (differenceSucceeded) nextProtectedMaskPath else previousProtectedMaskPath
      cachedMaskFrameIndex = frameIndex
      cachedMaskViewportLeft = viewport.left
      cachedMaskViewportTop = viewport.top
      cachedMaskViewportWidth = viewport.width()
      cachedMaskViewportHeight = viewport.height()
    }
    // Clipping out only the subject is cheaper than constructing an inverted full-viewport path
    // for every source mask frame, and avoids an extra large stencil region above HDR SurfaceView.
    cachedProtectedMaskPath?.let { protectedPath ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        canvas.clipOutPath(protectedPath)
      } else {
        @Suppress("DEPRECATION") canvas.clipPath(protectedPath, Region.Op.DIFFERENCE)
      }
    }
  }

  /** Appends the sampled SVG contour directly; it is cached until the next mask timestamp. */
  private fun Path.appendDirectContour(
    contours: FloatArray,
    start: Int,
    endExclusive: Int,
    viewport: RectF,
  ) {
    val pointCount = (endExclusive - start) / 2
    if (pointCount < 3) return
    fun x(index: Int): Float = viewport.left + contours[start + index * 2] * viewport.width()
    fun y(index: Int): Float = viewport.top + contours[start + index * 2 + 1] * viewport.height()
    moveTo(x(0), y(0))
    for (point in 1 until pointCount) lineTo(x(point), y(point))
    close()
  }

  private fun clearMaskClipCache() {
    cachedMaskFrameIndex = -1
    cachedMaskViewportLeft = 0f
    cachedMaskViewportTop = 0f
    cachedMaskViewportWidth = 0f
    cachedMaskViewportHeight = 0f
    cachedProtectedMaskPath = null
  }

  private fun stopFrames() {
    animationRunning = false
  }

  private fun refreshRenderingState() {
    val rendering = displayEnabled && !transitionSuppressed
    visibility = if (rendering) VISIBLE else GONE
    uiHighFrameRateRequested = rendering && !playbackPaused
    updateFrameRateHint(uiHighFrameRateRequested)
    if (!rendering) {
      stopFrames()
    } else if (playbackPaused) {
      stopFrames()
      requestRender()
    } else {
      requestFrame()
    }
  }

  /**
   * A Canvas invalidated by Choreographer can still be compositor-paced at the default 60 Hz on
   * variable-refresh panels. Ask for the highest cadence supported at the active mode's resolution
   * while danmaku is actually moving. Using Display.refreshRate here would create a feedback loop:
   * once a 60 fps video surface makes the panel select 60 Hz, this animation surface would vote for
   * 60 Hz too and could never restore the panel's 120 Hz animation mode.
   */
  private fun updateFrameRateHint(rendering: Boolean) {
    val targetHz = if (rendering) preferredDanmakuRefreshRateHz() else 0f
    updateSurfaceFrameRateHint(targetHz)
    if (targetHz == requestedFrameRateHz) return
    val window = context.findActivity()?.window ?: return
    val attributes = window.attributes
    if (attributes.preferredRefreshRate == targetHz) {
      requestedFrameRateHz = targetHz
      return
    }
    attributes.preferredRefreshRate = targetHz
    window.attributes = attributes
    requestedFrameRateHz = targetHz
  }

  /**
   * Window refresh-rate requests do not automatically describe this independently composed
   * translucent SurfaceView. Give SurfaceFlinger a matching per-surface vote so Android 16's
   * adaptive-refresh policy treats moving danmaku as continuous UI animation rather than a normal
   * or fixed-rate video layer.
   */
  private fun updateSurfaceFrameRateHint(targetHz: Float) {
    if (
      Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
        targetHz == requestedSurfaceFrameRateHz ||
        !surfaceAvailable ||
        !holder.surface.isValid
    ) {
      return
    }
    val updated =
      runCatching {
          when {
            Build.VERSION.SDK_INT >= 36 ->
              holder.surface.setFrameRate(
                targetHz,
                Surface.FRAME_RATE_COMPATIBILITY_AT_LEAST,
                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS,
              )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
              holder.surface.setFrameRate(
                targetHz,
                Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS,
              )
            else ->
              holder.surface.setFrameRate(
                targetHz,
                Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
              )
          }
        }
        .isSuccess
    if (updated) requestedSurfaceFrameRateHz = targetHz
  }

  private fun currentFrameBudgetNanos(): Long =
    danmakuFrameBudgetNanos(preferredDanmakuRefreshRateHz())

  private fun preferredDanmakuRefreshRateHz(): Float {
    val currentDisplay = display ?: return FALLBACK_REFRESH_RATE_HZ
    val currentMode = currentDisplay.mode
    val sameResolutionRates =
      currentDisplay.supportedModes
        .asSequence()
        .filter {
          it.physicalWidth == currentMode.physicalWidth &&
            it.physicalHeight == currentMode.physicalHeight
        }
        .map { it.refreshRate }
        .toList()
    return preferredDanmakuRefreshRateHz(
      currentRefreshRateHz = currentDisplay.refreshRate,
      supportedRefreshRatesHz = sameResolutionRates,
    )
  }

  private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
      if (current is Activity) return current
      current = current.baseContext
    }
    return current as? Activity
  }

  private fun scheduleNextFrame(positionMs: Long, activeCount: Int) {
    if (!animationRunning || playbackPaused) return
    val loop = renderLoop ?: return
    if (activeCount > 0) {
      // Submit every active frame through this transparent child surface. It remains separately
      // paced at the player's display cadence while the surrounding Compose tree stays static.
      requestRender()
      return
    }
    val nextIndex = prepared.upperBound(positionMs)
    val untilNext = prepared.getOrNull(nextIndex)?.item?.timeMs?.minus(positionMs)
    val delayMs =
      untilNext
        ?.minus(FRAME_WAKE_MARGIN_MS)
        ?.coerceIn(MIN_IDLE_FRAME_DELAY_MS, MAX_IDLE_FRAME_DELAY_MS) ?: MAX_IDLE_FRAME_DELAY_MS
    // Empty spans only need a low-frequency position check. A seek backwards is still discovered
    // within the same bounded delay, while dense active spans continue to follow every VSync.
    loop.idleWakeRunnable?.let(loop.handler::removeCallbacks)
    loop.idleWakeRunnable = Runnable {
      loop.idleWakeRunnable = null
      requestRenderOnLoop(loop)
    }
    loop.handler.postDelayed(loop.idleWakeRunnable!!, delayMs)
  }

  /** Only fixed top comments need cutout clearance; scrolling comments keep the full canvas. */
  private fun fixedTopSafeInset(): Float =
    if (fullscreen) {
      val viewHeight = renderViewHeight.takeIf { it > 0 } ?: renderHeight().coerceAtLeast(1)
      renderSafeInsetTop.toFloat() * renderHeight().coerceAtLeast(1) / viewHeight
    } else {
      0f
    }

  /**
   * Leave a short tail after the nominal flight. Player timestamps can advance in visible chunks
   * during decoder catch-up; without this margin a frame can jump from visibly on-screen straight
   * to the removal boundary before the text reaches the left edge.
   */
  private fun scrollingMotionDurationMs(): Long = (SCROLL_DURATION_MS / speed).toLong()

  /**
   * Keep an already off-screen comment alive for a short tail. The old implementation added the
   * guard to the movement itself, so a coarse decoder timestamp could still jump from visible to
   * removed. Here the text reaches x=-textWidth first and only then becomes eligible for removal.
   */
  private fun scrollingVisibleDurationMs(): Long =
    scrollingMotionDurationMs() + (SCROLL_EXIT_GUARD_MS / speed).toLong()

  private fun rebuildSchedule() {
    scheduledLaneHeight = estimatedLaneHeight()
    scheduledLaneCount = laneCountFor(displayArea)
    scheduledViewportWidth = renderWidth()
    scheduled = schedule(sourceItems, scheduledLaneCount, densityLevel)
    rebuildPreparedDanmaku()
  }

  private fun rebuildScheduleForViewportIfNeeded() {
    if (sourceItems.isEmpty()) return
    val nextLaneCount = laneCountFor(displayArea)
    if (nextLaneCount != scheduledLaneCount || renderWidth() != scheduledViewportWidth) {
      rebuildSchedule()
    }
  }

  /** Queue the cold-start working set without rasterizing labels on the animation thread. */
  private fun queueTextCacheAround(positionMs: Long) {
    lastTextCacheQueueBucket = positionMs / BITMAP_QUEUE_INTERVAL_MS
    val activeStart = prepared.lowerBound(positionMs - scrollingMotionDurationMs())
    val aheadEnd = prepared.upperBound(positionMs + BITMAP_PREWARM_AHEAD_MS)
    bitmapTextCache.enqueueRange(
      values = prepared,
      start = activeStart,
      endExclusive = aheadEnd,
      maxNewEntries = MAX_INITIAL_BITMAP_PREWARM,
    )
  }

  /** Keep a small asynchronous producer ahead of newly entering comments. */
  private fun queueTextCacheAhead(positionMs: Long) {
    val activeStart = prepared.lowerBound(positionMs - scrollingMotionDurationMs())
    val end = prepared.upperBound(positionMs + BITMAP_PREWARM_AHEAD_MS)
    bitmapTextCache.enqueueRange(
      values = prepared,
      start = activeStart,
      endExclusive = end,
      maxNewEntries = MAX_BITMAP_ENQUEUES_PER_FRAME,
    )
  }

  private fun schedule(
    items: List<DanmakuItem>,
    laneCount: Int,
    densityLevel: Int,
  ): List<ScheduledDanmaku> {
    val scrollingPrevious = arrayOfNulls<DanmakuItem>(laneCount)
    val topAvailableAt = LongArray(laneCount)
    val bottomAvailableAt = LongArray(laneCount)
    return buildList {
      items.forEach { item ->
        val laneSpan =
          danmakuLaneSpan(
            contentHeight = measurementFor(item).laneHeight,
            laneStep = scheduledLaneHeight,
            laneCount = laneCount,
          )
        val lane =
          when (item.type) {
            TYPE_TOP -> fixedLaneFor(item, topAvailableAt, laneSpan, densityLevel) ?: return@forEach
            TYPE_BOTTOM ->
              fixedLaneFor(item, bottomAvailableAt, laneSpan, densityLevel) ?: return@forEach
            else ->
              scrollingLaneFor(item, scrollingPrevious, laneSpan, densityLevel) ?: return@forEach
          }
        add(ScheduledDanmaku(item, lane))
        val occupiedLanes = lane until lane + laneSpan
        when (item.type) {
          TYPE_TOP -> occupiedLanes.forEach { topAvailableAt[it] = item.timeMs + FIXED_DURATION_MS }
          TYPE_BOTTOM ->
            occupiedLanes.forEach { bottomAvailableAt[it] = item.timeMs + FIXED_DURATION_MS }
          else -> occupiedLanes.forEach { scrollingPrevious[it] = item }
        }
      }
    }
  }

  private fun fixedLaneFor(
    item: DanmakuItem,
    availableAt: LongArray,
    laneSpan: Int,
    densityLevel: Int,
  ): Int? =
    firstDanmakuLaneWindow(availableAt.size, laneSpan) { availableAt[it] <= item.timeMs }
      ?: if (item.isLocal || densityLevel == MAX_DENSITY_LEVEL) {
        (0..availableAt.size - laneSpan).minBy { start ->
          (start until start + laneSpan).maxOf { availableAt[it] }
        }
      } else {
        null
      }

  private fun scrollingLaneFor(
    item: DanmakuItem,
    previousItems: Array<DanmakuItem?>,
    laneSpan: Int,
    densityLevel: Int,
  ): Int? {
    val lane =
      firstDanmakuLaneWindow(previousItems.size, laneSpan) { index ->
        scrollingLaneAvailable(previousItems[index], item, densityLevel)
      }
    if (lane != null) return lane
    if (!item.isLocal && densityLevel != MAX_DENSITY_LEVEL) return null
    return (0..previousItems.size - laneSpan).minBy { start ->
      (start until start + laneSpan).maxOf { previousItems[it]?.timeMs ?: Long.MIN_VALUE }
    }
  }

  /**
   * Density controls horizontal distance only. Standard waits until the previous tail reaches the
   * right edge before the next comment enters; lower levels add a gap, while higher levels allow a
   * controlled overlap. Vertical lane count is independent and comes only from display area.
   */
  private fun scrollingLaneAvailable(
    previous: DanmakuItem?,
    next: DanmakuItem,
    densityLevel: Int,
  ): Boolean {
    previous ?: return true
    val elapsedMs = next.timeMs - previous.timeMs
    val durationMs = scrollingMotionDurationMs()
    if (elapsedMs >= durationMs) return true
    if (elapsedMs < 0L) return false
    val viewportWidth =
      renderWidth().takeIf { it > 0 }?.toFloat() ?: FALLBACK_VIEWPORT_WIDTH_DP * density
    val previousWidth = measurementFor(previous).textWidth
    val nextWidth = measurementFor(next).textWidth
    val requiredMs =
      max(
        scrollingReservationMs(previousWidth, viewportWidth, durationMs, densityLevel),
        scrollingReservationMs(nextWidth, viewportWidth, durationMs, densityLevel),
      )
    return elapsedMs >= requiredMs
  }

  private fun scrollingReservationMs(
    textWidth: Float,
    viewportWidth: Float,
    durationMs: Long,
    densityLevel: Int,
  ): Long {
    val horizontalGap =
      when (densityLevel) {
        1 -> 72f * density
        2 -> 32f * density
        3 -> 0f
        4 -> -textWidth * .3f
        else -> -textWidth * .6f
      }
    val occupiedWidth = (textWidth + horizontalGap).coerceIn(0f, textWidth + viewportWidth)
    return (durationMs * occupiedWidth / (viewportWidth + textWidth)).toLong()
  }

  private fun rebuildPreparedDanmaku() {
    prepared = scheduled.map { scheduledItem ->
      val item = scheduledItem.item
      val measurement = measurementFor(item)
      PreparedDanmaku(
        cacheKey =
          RenderCacheKey(item.content, measurement.textSize, measurement.color, item.colorful),
        item = item,
        lane = scheduledItem.lane,
        textSize = measurement.textSize,
        textWidth = measurement.textWidth,
        laneHeight = measurement.laneHeight,
        laneStep = scheduledLaneHeight,
        ascent = measurement.ascent,
        descent = measurement.descent,
        endMs =
          item.timeMs +
            if (item.type == TYPE_TOP || item.type == TYPE_BOTTOM) FIXED_DURATION_MS
            else scrollingVisibleDurationMs(),
        color = measurement.color,
      )
    }
  }

  private fun trimItemCaches() {
    val retained = sourceItems.toHashSet()
    if (measurements.size > retained.size + ITEM_CACHE_TRIM_SLACK) {
      measurements.keys.retainAll(retained)
    }
    if (inlineSegmentCache.size > retained.size + ITEM_CACHE_TRIM_SLACK) {
      inlineSegmentCache.keys.retainAll(retained)
    }
  }

  private fun measurementFor(item: DanmakuItem): DanmakuMeasurement =
    measurements.getOrPut(item) {
      if (!item.imageUrl.isNullOrBlank()) {
        val imageSize =
          (if (item.imageLarge) LIVE_LARGE_EMOJI_SIZE_DP else LIVE_EMOJI_SIZE_DP) *
            density *
            fontScale
        return@getOrPut DanmakuMeasurement(
          textSize = imageSize,
          textWidth = imageSize,
          laneHeight = imageSize + LIVE_EMOJI_LANE_GAP_DP * density,
          ascent = -imageSize,
          descent = 0f,
          color = Color.WHITE,
        )
      }
      val textSize = (item.fontSize.coerceIn(18, 36) / 25f) * 14f * scaledDensity * fontScale
      textPaint.textSize = textSize
      val metrics = textPaint.fontMetrics
      val rawColor = item.color and 0xFFFFFF
      val inlineImageSize = metrics.descent - metrics.ascent
      val measuredWidth =
        if (item.inlineEmotes.isEmpty()) {
          textPaint.measureText(item.content)
        } else {
          inlineSegments(item)
            .sumOf { segment ->
              when (segment) {
                is InlineDanmakuSegment.Text -> textPaint.measureText(segment.value).toDouble()
                is InlineDanmakuSegment.Emote ->
                  max(inlineImageSize, textPaint.measureText(segment.value.token)).toDouble()
              }
            }
            .toFloat()
        }
      DanmakuMeasurement(
        textSize = textSize,
        textWidth = measuredWidth,
        laneHeight =
          max(
            27f * density,
            max(
              metrics.descent - metrics.ascent + metrics.leading,
              if (item.inlineEmotes.isEmpty()) 0f
              else inlineImageSize + LIVE_EMOJI_LANE_GAP_DP * density,
            ),
          ),
        ascent = metrics.ascent,
        descent = metrics.descent,
        color = Color.rgb(rawColor shr 16, rawColor shr 8 and 0xFF, rawColor and 0xFF),
      )
    }

  /**
   * Interpolates Media3's millisecond position at display cadence while the clocks agree. When a
   * draw observes a real discontinuity, the existing snap/catch-up paths remain authoritative. This
   * removes duplicate positions on 120 Hz displays without changing seeks or recovery.
   */
  private fun resolveVisualPosition(rawPositionMs: Long): Long {
    val frameTimeNs = SystemClock.elapsedRealtimeNanos()
    if (!visualClockInitialized) {
      resetVisualClock(rawPositionMs, visualClockEpoch, frameTimeNs)
      return rawPositionMs
    }
    val frameElapsedNs = (frameTimeNs - visualClockLastFrameNs).coerceAtLeast(0L)
    visualClockLastFrameNs = frameTimeNs
    if (rawPositionMs + CLOCK_BACKWARD_RESET_TOLERANCE_MS < visualPositionMs) {
      // Replay and any external seek that does not travel through AppRoot's seek controls.
      resetVisualClock(rawPositionMs, visualClockEpoch, frameTimeNs)
      return rawPositionMs
    }

    if (rawPositionMs - visualPositionMs > MAX_SMOOTHABLE_CLOCK_JUMP_MS) {
      // Entering a video from watch history can move ExoPlayer from zero to a saved position
      // without travelling through this screen's seek controls. That is a timeline discontinuity,
      // not decoder catch-up: snap to the real position so old danmaku are not replayed rapidly.
      resetVisualClock(rawPositionMs, visualClockEpoch, frameTimeNs)
      return rawPositionMs
    }

    if (!visualClockCatchingUp) {
      val interpolatedPositionMs =
        interpolateDanmakuPosition(
          currentPositionMs = visualPositionPreciseMs,
          frameElapsedNs = frameElapsedNs,
          rawPositionMs = rawPositionMs,
          playbackRate = playbackClockSnapshot.playbackRate,
          toleranceMs = CLOCK_INTERPOLATION_TOLERANCE_MS,
        )
      if (interpolatedPositionMs != null) {
        visualPositionPreciseMs = interpolatedPositionMs
        visualPositionMs = interpolatedPositionMs.roundToLong()
        return visualPositionMs
      }
    }

    // A short decoder stall can leave the quantized player clock behind the interpolated clock.
    // Hold the last visual position until Media3 catches up instead of moving danmaku backwards.
    if (rawPositionMs <= visualPositionMs) return visualPositionMs

    val crossesDanmaku = hasDanmakuInWindow(visualPositionMs, rawPositionMs)
    if (
      !visualClockCatchingUp &&
        crossesDanmaku &&
        rawPositionMs - visualPositionMs > MAX_VISUAL_CLOCK_ADVANCE_MS
    ) {
      visualClockCatchingUp = true
    }
    if (visualClockCatchingUp) {
      visualPositionMs = minOf(rawPositionMs, visualPositionMs + MAX_VISUAL_CLOCK_ADVANCE_MS)
      if (rawPositionMs - visualPositionMs <= CLOCK_REJOIN_TOLERANCE_MS) {
        visualPositionMs = rawPositionMs
        visualClockCatchingUp = false
      }
    } else {
      visualPositionMs = rawPositionMs
    }
    visualPositionPreciseMs = visualPositionMs.toDouble()
    return visualPositionMs
  }

  private fun resetVisualClock(
    positionMs: Long,
    epoch: Long,
    frameTimeNs: Long = SystemClock.elapsedRealtimeNanos(),
  ) {
    visualPositionMs = positionMs
    visualPositionPreciseMs = positionMs.toDouble()
    visualClockLastFrameNs = frameTimeNs
    visualClockInitialized = true
    visualClockEpoch = epoch
    visualClockCatchingUp = false
  }

  /** True when skipping [startMs, endMs] could make a comment appear or disappear mid-flight. */
  private fun hasDanmakuInWindow(startMs: Long, endMs: Long): Boolean {
    if (prepared.isEmpty()) return false
    val first = prepared.lowerBound(startMs - MAX_DANMAKU_VISIBLE_DURATION_MS)
    val last = prepared.upperBound(endMs)
    var index = first
    while (index < last) {
      if (prepared[index].endMs >= startMs) return true
      index++
    }
    return false
  }

  private fun textCacheOutlinePadding(): Float = ceil(outlinePaint.strokeWidth + 1f)

  private fun textCachePaddingX(): Float = textCacheOutlinePadding()

  private fun textCachePaddingY(): Float = textCacheOutlinePadding()

  private fun textColor(color: Int): Int =
    Color.argb((opacity * 255f).toInt(), Color.red(color), Color.green(color), Color.blue(color))

  private fun outlineColor(): Int = Color.argb((opacity * 210f).toInt(), 0, 0, 0)

  private fun configurePaints(
    value: PreparedDanmaku,
    targetTextPaint: Paint,
    targetOutlinePaint: Paint,
    gradientStartX: Float,
  ) {
    targetTextPaint.textSize = value.textSize
    targetOutlinePaint.textSize = value.textSize
    targetOutlinePaint.shader = null
    targetOutlinePaint.color = outlineColor()
    if (value.item.colorful == DANMAKU_COLORFUL_VIP_GRADIENT) {
      targetTextPaint.color = Color.WHITE
      targetTextPaint.shader =
        LinearGradient(
          gradientStartX,
          0f,
          gradientStartX + value.textWidth.coerceAtLeast(1f),
          0f,
          vipGradientColors(),
          null,
          Shader.TileMode.CLAMP,
        )
    } else {
      targetTextPaint.shader = null
      targetTextPaint.color = textColor(value.color)
    }
  }

  private fun vipGradientColors(): IntArray {
    val alpha = (opacity * 255f).toInt()
    return VIP_GRADIENT_RGB.mapToIntArray { rgb ->
      Color.argb(alpha, rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
    }
  }

  /** Vertical coverage is controlled solely by display area, never by horizontal density. */
  private fun laneCountFor(displayArea: Float): Int {
    val viewportHeight =
      renderHeight().takeIf { it > 0 }?.toFloat() ?: FALLBACK_VIEWPORT_HEIGHT_DP * density
    val visibleHeight = viewportHeight * displayArea
    val textHeight = estimatedLaneHeight()
    return (((visibleHeight - textHeight).coerceAtLeast(0f) / textHeight).toInt() + 1)
      .coerceAtLeast(1)
  }

  private fun estimatedLaneHeight(): Float {
    return (27f * density * fontScale).coerceAtLeast(1f)
  }

  private fun newImageScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private fun requestImages(items: List<DanmakuItem>) {
    items
      .asSequence()
      .flatMap { item ->
        sequence {
          item.imageUrl?.let { yield(it) }
          item.inlineEmotes.forEach { yield(it.imageUrl) }
        }
      }
      .filter(String::isNotBlank)
      .distinct()
      .forEach { url ->
        synchronized(renderStateLock) {
          if (imageBitmaps.containsKey(url) || !imageRequests.add(url)) return@forEach
        }
        imageScope.launch {
          val targetSize =
            ceil(LIVE_LARGE_EMOJI_SIZE_DP * density * fontScale).toInt().coerceAtLeast(1)
          val bitmap =
            runCatching {
                context.imageLoader
                  .execute(
                    ImageRequest.Builder(context)
                      .data(url)
                      .size(targetSize, targetSize)
                      .allowHardware(false)
                      .build()
                  )
                  .image
              }
              .getOrNull()
              ?.let { it as? BitmapImage }
              ?.bitmap
          synchronized(renderStateLock) {
            imageRequests.remove(url)
            bitmap?.let {
              imageBitmaps[url] = it
            imageAllocationBytes += it.allocationByteCount
            trimImageCache()
            requestRender()
          }
          }
        }
      }
  }

  private fun inlineSegments(item: DanmakuItem): List<InlineDanmakuSegment> =
    inlineSegmentCache.getOrPut(item) {
      splitInlineDanmaku(item.content, item.inlineEmotes)
    }

  private fun trimImageCache() {
    while (imageAllocationBytes > IMAGE_CACHE_BYTES && imageBitmaps.isNotEmpty()) {
      val eldestKey = imageBitmaps.entries.first().key
      imageAllocationBytes -= imageBitmaps.remove(eldestKey)?.allocationByteCount ?: 0
    }
  }

  private fun clearRenderCache() {
    bitmapTextCache.clear()
  }

  /**
   * Rasterizes SDR labels on one background worker. A cache miss stays on Canvas' direct glyph path
   * for that frame, so playback never allocates or paints a software Bitmap on the animation
   * thread. The completed bitmap is adopted by a later frame and remains bounded by byte count.
   */
  private inner class BitmapTextCache {
    private val lock = Any()
    private val bitmaps = LinkedHashMap<RenderCacheKey, Bitmap>(256, .75f, true)
    private val pendingKeys = HashSet<RenderCacheKey>()
    private val buildQueue = ArrayDeque<BitmapBuildRequest>()
    private val cachedTextPaint = Paint(textPaint)
    private val cachedOutlinePaint = Paint(outlinePaint)
    private val bitmapCanvas = Canvas()
    private var workerScope = newWorkerScope()
    private var workerRunning = false
    private var generation = 0L
    private var allocatedBytes = 0L

    fun onAttached() {
      synchronized(lock) {
        if (!workerScope.isActive) workerScope = newWorkerScope()
      }
    }

    fun contains(value: PreparedDanmaku): Boolean =
      synchronized(lock) { bitmaps.containsKey(value.cacheKey) }

    fun enqueueRange(
      values: List<PreparedDanmaku>,
      start: Int,
      endExclusive: Int,
      maxNewEntries: Int,
    ) {
      if (maxNewEntries <= 0 || values.isEmpty()) return
      val opacitySnapshot = opacity
      var added = 0
      var index = start.coerceIn(0, values.size)
      val end = endExclusive.coerceIn(index, values.size)
      while (index < end && added < maxNewEntries) {
        if (enqueue(values[index], opacitySnapshot)) added++
        index++
      }
    }

    fun draw(
      canvas: Canvas,
      value: PreparedDanmaku,
      x: Float,
      baseline: Float,
    ): Boolean {
      val bitmap = synchronized(lock) { bitmaps[value.cacheKey] } ?: return false
      val paddingX = textCachePaddingX()
      val paddingY = textCachePaddingY()
      val top = baseline + value.ascent - paddingY
      canvas.drawBitmap(bitmap, x - paddingX, top, null)
      return true
    }

    private fun enqueue(value: PreparedDanmaku, opacitySnapshot: Float): Boolean {
      if (value.item.imageUrl != null || value.item.inlineEmotes.isNotEmpty()) return false
      var scopeToStart: CoroutineScope? = null
      synchronized(lock) {
        if (
          bitmaps.containsKey(value.cacheKey) ||
            value.cacheKey in pendingKeys ||
            buildQueue.size >= MAX_BITMAP_BUILD_QUEUE
        )
          return false
        pendingKeys += value.cacheKey
        buildQueue.addLast(
          BitmapBuildRequest(
            value = value,
            generation = generation,
            opacity = opacitySnapshot,
          )
        )
        if (!workerRunning) {
          workerRunning = true
          scopeToStart = workerScope
        }
      }
      scopeToStart?.let { scope -> scope.launch { drainQueue(scope) } }
      return true
    }

    private fun drainQueue(scope: CoroutineScope) {
      while (scope.isActive) {
        val request =
          synchronized(lock) {
            if (scope !== workerScope) return
            if (buildQueue.isEmpty()) {
              workerRunning = false
              null
            } else {
              buildQueue.removeFirst()
            }
          } ?: return
        val bitmap = renderBitmap(request)
        synchronized(lock) {
          pendingKeys.remove(request.value.cacheKey)
          if (bitmap != null && request.generation == generation) {
            bitmaps.put(request.value.cacheKey, bitmap)?.let { replaced ->
              allocatedBytes -= replaced.allocationByteCount.toLong()
            }
            allocatedBytes += bitmap.allocationByteCount.toLong()
            trimToByteLimit()
          } else {
            bitmap?.recycle()
          }
        }
      }
    }

    private fun renderBitmap(request: BitmapBuildRequest): Bitmap? {
      val value = request.value
      val paddingX = textCachePaddingX()
      val paddingY = textCachePaddingY()
      val bitmapWidth = ceil(value.textWidth + paddingX * 2f).toInt().coerceAtLeast(1)
      val bitmapHeight = ceil(value.descent - value.ascent + paddingY * 2f).toInt().coerceAtLeast(1)
      if (
        bitmapWidth > MAX_CACHED_TEXT_BITMAP_DIMENSION ||
          bitmapHeight > MAX_CACHED_TEXT_BITMAP_DIMENSION
      ) {
        return null
      }
      val requiredBytes = bitmapWidth.toLong() * bitmapHeight * Int.SIZE_BYTES
      if (requiredBytes > BITMAP_TEXT_CACHE_BYTES) return null
      val bitmap =
        runCatching { Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888) }
          .getOrNull() ?: return null
      configureCachedPaints(value, request.opacity, paddingX)
      val recordedBaseline = paddingY - value.ascent
      bitmapCanvas.setBitmap(bitmap)
      bitmapCanvas.apply {
        drawText(value.item.content, paddingX, recordedBaseline, cachedOutlinePaint)
        drawText(value.item.content, paddingX, recordedBaseline, cachedTextPaint)
      }
      bitmapCanvas.setBitmap(null)
      return bitmap
    }

    private fun configureCachedPaints(
      value: PreparedDanmaku,
      opacity: Float,
      gradientStartX: Float,
    ) {
      cachedTextPaint.textSize = value.textSize
      cachedOutlinePaint.textSize = value.textSize
      cachedOutlinePaint.shader = null
      cachedOutlinePaint.color = Color.argb((opacity * 210f).toInt(), 0, 0, 0)
      if (value.item.colorful == DANMAKU_COLORFUL_VIP_GRADIENT) {
        cachedTextPaint.color = Color.WHITE
        cachedTextPaint.shader =
          LinearGradient(
            gradientStartX,
            0f,
            gradientStartX + value.textWidth.coerceAtLeast(1f),
            0f,
            VIP_GRADIENT_RGB.mapToIntArray { rgb ->
              Color.argb(
                (opacity * 255f).toInt(),
                rgb shr 16 and 0xFF,
                rgb shr 8 and 0xFF,
                rgb and 0xFF,
              )
            },
            null,
            Shader.TileMode.CLAMP,
          )
      } else {
        cachedTextPaint.shader = null
        cachedTextPaint.color =
          Color.argb(
            (opacity * 255f).toInt(),
            Color.red(value.color),
            Color.green(value.color),
            Color.blue(value.color),
          )
      }
    }

    private fun trimToByteLimit() {
      while (allocatedBytes > BITMAP_TEXT_CACHE_BYTES && bitmaps.isNotEmpty()) {
        val eldestKey = bitmaps.entries.first().key
        allocatedBytes -= bitmaps.remove(eldestKey)?.allocationByteCount?.toLong() ?: 0L
      }
    }

    fun clear() {
      synchronized(lock) {
        generation++
        buildQueue.clear()
        pendingKeys.clear()
        bitmaps.clear()
        allocatedBytes = 0L
      }
    }

    fun release() {
      synchronized(lock) {
        generation++
        workerScope.cancel()
        buildQueue.clear()
        pendingKeys.clear()
        bitmaps.clear()
        allocatedBytes = 0L
        workerRunning = false
      }
    }

    private fun newWorkerScope(): CoroutineScope =
      CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }

  private fun List<PreparedDanmaku>.lowerBound(targetMs: Long): Int {
    var low = 0
    var high = size
    while (low < high) {
      val middle = (low + high) ushr 1
      if (this[middle].item.timeMs < targetMs) low = middle + 1 else high = middle
    }
    return low
  }

  private fun List<PreparedDanmaku>.upperBound(targetMs: Long): Int {
    var low = 0
    var high = size
    while (low < high) {
      val middle = (low + high) ushr 1
      if (this[middle].item.timeMs <= targetMs) low = middle + 1 else high = middle
    }
    return low
  }

  private data class ScheduledDanmaku(val item: DanmakuItem, val lane: Int)

  private data class DanmakuMeasurement(
    val textSize: Float,
    val textWidth: Float,
    val laneHeight: Float,
    val ascent: Float,
    val descent: Float,
    val color: Int,
  )

  /** Content plus pixels-affecting style lets recurring text share the same raster cache entry. */
  private data class RenderCacheKey(
    val content: String,
    val textSize: Float,
    val color: Int,
    val colorful: Int,
  )

  private data class PreparedDanmaku(
    val cacheKey: RenderCacheKey,
    val item: DanmakuItem,
    val lane: Int,
    val textSize: Float,
    val textWidth: Float,
    val laneHeight: Float,
    val laneStep: Float,
    val ascent: Float,
    val descent: Float,
    val endMs: Long,
    val color: Int,
  )

  private data class BitmapBuildRequest(
    val value: PreparedDanmaku,
    val generation: Long,
    val opacity: Float,
  )

  private companion object {
    const val MIN_DENSITY_LEVEL = 1
    const val MAX_DENSITY_LEVEL = 5
    const val FALLBACK_VIEWPORT_HEIGHT_DP = 360f
    const val FALLBACK_VIEWPORT_WIDTH_DP = 640f
    const val FALLBACK_REFRESH_RATE_HZ = 60f
    const val TYPE_BOTTOM = 4
    const val TYPE_TOP = 5
    const val SCROLL_DURATION_MS = 6_000L
    const val SCROLL_EXIT_GUARD_MS = 900L
    const val FIXED_DURATION_MS = 4_000L
    const val BITMAP_TEXT_CACHE_BYTES = 24 * 1024 * 1024L
    const val MAX_CACHED_TEXT_BITMAP_DIMENSION = 2_048
    const val MAX_BITMAP_ENQUEUES_PER_FRAME = 4
    const val MAX_BITMAP_BUILD_QUEUE = 64
    const val MAX_INITIAL_BITMAP_PREWARM = 128
    const val BITMAP_PREWARM_AHEAD_MS = 1_500L
    const val BITMAP_QUEUE_INTERVAL_MS = 125L
    const val ITEM_CACHE_TRIM_SLACK = 96
    const val IMAGE_CACHE_BYTES = 16 * 1024 * 1024L
    const val LIVE_EMOJI_SIZE_DP = 36f
    const val LIVE_LARGE_EMOJI_SIZE_DP = 54f
    const val LIVE_EMOJI_LANE_GAP_DP = 4f
    const val FRAME_WAKE_MARGIN_MS = 12L
    const val MIN_IDLE_FRAME_DELAY_MS = 16L
    const val MAX_IDLE_FRAME_DELAY_MS = 250L
    const val MAX_VISUAL_CLOCK_ADVANCE_MS = 160L
    const val MAX_SMOOTHABLE_CLOCK_JUMP_MS = 2_000L
    const val CLOCK_REJOIN_TOLERANCE_MS = 24L
    const val CLOCK_BACKWARD_RESET_TOLERANCE_MS = 500L
    const val CLOCK_INTERPOLATION_TOLERANCE_MS = 24.0
    const val CLOCK_SAMPLE_INTERVAL_MS = 100L
    const val MAX_DANMAKU_VISIBLE_DURATION_MS = 12_000L
    val VIP_GRADIENT_RGB =
      intArrayOf(0xFF5A8F, 0xFF9A5A, 0xFFD75A, 0x63E6BE, 0x66C7F2, 0x8C9EFF, 0xC792EA)
  }
}

/**
 * Advances the visual clock at display cadence while Media3's millisecond clock remains close
 * enough to prove that playback is continuous. Returning null preserves the discontinuity paths.
 */
internal fun interpolateDanmakuPosition(
  currentPositionMs: Double,
  frameElapsedNs: Long,
  rawPositionMs: Long,
  playbackRate: Float,
  toleranceMs: Double = 24.0,
): Double? {
  val elapsedMs = frameElapsedNs.coerceAtLeast(0L).toDouble() / 1_000_000.0
  val predictedPositionMs = currentPositionMs + elapsedMs * playbackRate.coerceAtLeast(0f)
  return predictedPositionMs.takeIf {
    abs(rawPositionMs.toDouble() - predictedPositionMs) <= toleranceMs
  }
}

internal fun danmakuFrameBudgetNanos(refreshRateHz: Float): Long {
  val safeRefreshRateHz = refreshRateHz.takeIf { it.isFinite() && it > 0f } ?: 60f
  return (1_000_000_000.0 / safeRefreshRateHz.toDouble()).roundToLong().coerceAtLeast(1L)
}

internal fun preferredDanmakuRefreshRateHz(
  currentRefreshRateHz: Float,
  supportedRefreshRatesHz: List<Float>,
): Float =
  supportedRefreshRatesHz.filter { it.isFinite() && it > 0f }.maxOrNull()
    ?: currentRefreshRateHz.takeIf { it.isFinite() && it > 0f }
    ?: 60f

internal fun danmakuLaneSpan(
  contentHeight: Float,
  laneStep: Float,
  laneCount: Int,
): Int {
  if (laneCount <= 1 || laneStep <= 0f) return 1
  return ceil(contentHeight.coerceAtLeast(1f) / laneStep).toInt().coerceIn(1, laneCount)
}

internal fun firstDanmakuLaneWindow(
  laneCount: Int,
  laneSpan: Int,
  laneAvailable: (Int) -> Boolean,
): Int? {
  if (laneCount <= 0) return null
  val safeSpan = laneSpan.coerceIn(1, laneCount)
  return (0..laneCount - safeSpan).firstOrNull { start ->
    (start until start + safeSpan).all(laneAvailable)
  }
}

private inline fun IntArray.mapToIntArray(transform: (Int) -> Int): IntArray =
  IntArray(size) { index -> transform(this[index]) }

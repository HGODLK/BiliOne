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
import dev.openbili.webdemo.DevicePerformancePolicy
import dev.openbili.webdemo.api.DANMAKU_COLORFUL_VIP_GRADIENT
import dev.openbili.webdemo.api.DanmakuInlineEmote
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.DanmakuMaskTimeline
import dev.openbili.webdemo.ui.PLAYER_CORNER_RADIUS_DP
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

internal fun danmakuViewportCornerRadiusPx(fullscreen: Boolean, density: Float): Float =
  if (fullscreen) 0f else PLAYER_CORNER_RADIUS_DP * density.coerceAtLeast(0f)

/** 一个主线程播放器采样，可被弹幕渲染线程安全推进。 */
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
 * 一个基于 PlayerView 原生层的弹幕层。
 *
 * 把该视图放在 PlayerView 的根覆盖层中可以盖住整个播放器（包括竖屏视频的侧边栏），
 * 而智能蒙版路径仍然映射到 Media3 的真实内容帧。
 */
class DanmakuOverlayView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
  /**
   * Canvas 渲染使用的状态作为一个整体受锁保护。UI 回调只在不频繁的配置/生命周期变更时
   * 短暂持有该锁；连续帧循环在专用渲染线程上持有它，因此 Compose 滚动永远不会执行弹幕
   * 绘制工作。
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
  private val localHighlightPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
    }
  private val localHighlightBorderPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeJoin = Paint.Join.ROUND
    }
  private var scheduled = emptyList<ScheduledDanmaku>()
  private var sourceItemsRef: List<DanmakuItem>? = null
  private var submittedItems = emptyList<DanmakuItem>()
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
  private val occlusionController = DanmakuOcclusionController()
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
  // 启动期间解码器/合成器追赶进度时，播放器时钟可能跳变数秒。保留一个视觉时钟，
  // 让已经可见的评论不会在某一帧有机会把它画到左边缘之前就被丢弃。
  private var visualPositionMs = 0L
  private var visualPositionPreciseMs = 0.0
  private var visualClockLastFrameNs = 0L
  private var visualClockInitialized = false
  private var visualClockEpoch = Long.MIN_VALUE
  private var visualClockCatchingUp = false
  private var lastTextCacheQueueBucket = Long.MIN_VALUE
  private val bitmapTextCache = BitmapTextCache()
  private val renderViewport = RectF()
  private val renderClipPath = Path()
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
  private val imageCacheBytes = DevicePerformancePolicy.danmakuImageCacheBytes
  private val textCacheBytes = DevicePerformancePolicy.danmakuTextCacheBytes
  private var imageScope = newImageScope()

  /**
   * Choreographer 在该 Looper 上创建，因此帧回调与 Canvas 提交都留在主线程之外，
   * 同时保留显示器 vsync 节奏。
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
    // 让透明弹幕表面保持在应用窗口之上。media-overlay 的 SurfaceView 仍在该窗口之下，
    // 并会挖穿其完整边界；在全屏时那个洞还会把 Compose 封面渐变从视频黑边上移除，
    // 露出 SurfaceFlinger 的黑色填充。一个置顶的半透明表面既保留独立的 120 Hz 缓冲，
    // 其透明像素又能透出下面经过亮度调整的黑边。
    setZOrderOnTop(true)
    holder.setFormat(PixelFormat.TRANSLUCENT)
    holder.addCallback(this)
    setWillNotDraw(true)
    isClickable = false
    isFocusable = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
    }
    DanmakuOcclusionRegistry.register(this)
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
      val rawPositionMs = playbackClockSnapshot.positionAt(SystemClock.elapsedRealtimeNanos())
      val clockEpochChanged = visualClockEpoch != positionEpoch
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
      var preservedLanes = emptyMap<DanmakuItem, Int>()
      if (itemsChanged) {
        sourceItemsRef = items
        val nextSubmitted =
          filterDanmakuByBlockLevel(items, nextBlockLevel).sortedBy(DanmakuItem::timeMs)
        val slidingWindowReplacement =
          !blockLevelChanged &&
            !clockEpochChanged &&
            isDanmakuSlidingWindowReplacement(submittedItems, nextSubmitted)
        if (slidingWindowReplacement) {
          preservedLanes = scheduled.associate { it.item to it.lane }
          val activePositionMs =
            if (visualClockInitialized) visualPositionMs else rawPositionMs
          val activeScheduledItems =
            prepared.asSequence().filter { it.endMs >= activePositionMs }.map { it.item }.toList()
          sourceItems =
            mergeActiveDanmakuSlidingWindow(
              incoming = nextSubmitted,
              activeScheduledItems = activeScheduledItems,
            )
        } else {
          sourceItems = nextSubmitted
        }
        submittedItems = nextSubmitted
        requestImages(sourceItems)
      } else if (blockLevelChanged) {
        submittedItems =
          filterDanmakuByBlockLevel(items, nextBlockLevel).sortedBy(DanmakuItem::timeMs)
        sourceItems = submittedItems
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
        rebuildSchedule(
          preferredLanes = if (scheduleGeometryChanged) emptyMap() else preservedLanes,
        )
      }
      if (maskChanged || smartBlockingChanged) clearMaskClipCache()
      if (highDynamicRangeChanged) {
        clearRenderCache()
        renderCacheInvalidated = true
      }
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
        // 播放器暂停时保留一个刻意平滑过的屏上位置。显式 seek 在上面带有新的 epoch，
        // 因此会有意地重置到其目标位置。
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

  /** 可见的 Media3 帧，位于这个全播放器覆盖层的坐标系中。 */
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

  /** SurfaceView 边界在动画期间避免重复布局与重绘覆盖层。 */
  fun setTransitionSuppressed(suppressed: Boolean) {
    synchronized(renderStateLock) {
      if (transitionSuppressed == suppressed) return
      if (suppressed) {
        hideForTransition()
        return
      }
      transitionSuppressed = false
      occlusionController.setDeclarativelySuppressed(false)
      occlusionController.releaseImmediateSuppression()
      refreshRenderingState()
    }
    // Compose 在该表面隐藏期间完成全屏布局。交接结束时重新应用最终的 View 尺寸；
    // 否则某些设备会保留内嵌缓冲尺寸，并把全屏弹幕层的右/下部分裁掉。
    syncSurfaceSizeFromLayout()
    requestRender()
  }

  /** 启动遮罩拥有应用级最高优先级，不能被播放器自身的恢复逻辑提前解除。 */
  fun setStartupMaskVisible(visible: Boolean) {
    synchronized(renderStateLock) {
      val wasBlocked = occlusionController.currentSnapshot().blocked
      occlusionController.setStartupMaskVisible(visible)
      if (visible && !wasBlocked) {
        stopFrames()
        renderPosted = false
        clearSurfaceForTransition()
      }
      refreshRenderingState()
    }
  }

  fun setEmbeddedHost(host: ViewGroup) {
    embeddedHost = host
  }

  /**
   * 置顶 SurfaceView 在三星设备上不会继承 Compose 的全屏 Surface 变换。边界动画隐藏后，
   * 把同一个表面直接挂到 Activity 窗口上，让它的布局就是真实的全屏尺寸；内嵌模式时再
   * 移回去。
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
    DanmakuOcclusionRegistry.unregister(this)
    embeddedHost = null
    (parent as? ViewGroup)?.removeView(this)
  }

  /** 清除并隐藏 SurfaceControl，不分离视图也不释放光栅缓存。 */
  fun hideForTransition() {
    synchronized(renderStateLock) {
      transitionSuppressed = true
      occlusionController.setDeclarativelySuppressed(true)
      occlusionController.suppressImmediately()
      stopFrames()
      renderPosted = false
      // SurfaceView 与 Compose 页面独立合成。仅仅改变 View 可见性可能把最后提交的缓冲
      // 留到下一次 ViewRoot 遍历之前，让弹幕漂浮在本该淡出的返回动画之上。同步提交一个
      // 透明缓冲，让合成器没有可保留的内容。
      clearSurfaceForTransition()
      visibility = INVISIBLE
      // 在视图于转场后被释放/重新挂载之前，保持当前的窗口刷新率请求。此时更新
      // WindowManager.LayoutParams 可能恰好在首帧封面到期时触发布局窗口。
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
    // AspectRatioFrameLayout 可能在 SurfaceView/窗口尺寸变化后的下一次布局遍历才稳定。
    // 立即请求新帧，让裁剪边界不会停留在旧的宽度上。
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
      if (!isAttachedToWindow || occlusionController.currentSnapshot().blocked) return@post
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
        occlusionController.currentSnapshot().blocked ||
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
    // 文本可能有意识地平滑粗略的播放器时钟跳变，但主体蒙版必须跟随真实播放时钟，
    // 这样它永远不会落后于独立合成的视频 SurfaceView。
    val maskPositionMs = if (playbackPaused) frozenMaskPositionMs else rawPositionMs
    val scrollingMotionDurationMs = scrollingMotionDurationMs()
    val scrollingVisibleDurationMs = scrollingVisibleDurationMs()
    // 覆盖层盖住整个播放器，而智能屏蔽只映射到 Media3 的实际视频帧。这让竖屏视频
    // 可以使用两侧的黑边区域显示弹幕。
    renderViewport.set(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
    val viewport = renderViewport
    val saveCount = canvas.save()
    val cornerRadiusPx =
      danmakuViewportCornerRadiusPx(fullscreen, density)
        .coerceAtMost(minOf(viewport.width(), viewport.height()) / 2f)
    if (cornerRadiusPx > 0f) {
      renderClipPath.rewind()
      renderClipPath.addRoundRect(viewport, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
      canvas.clipPath(renderClipPath)
    } else {
      canvas.clipRect(viewport)
    }
    applySmartMaskClip(
      canvas = canvas,
      maskPositionMs = maskPositionMs,
      viewport = scaledVideoViewport(viewport),
    )
    // 一条滚动弹幕在运动时长结束后到达左边缘。它会在时间线里再保留一小段保护间隔，
    // 以吸收粗略的播放器时钟采样，但那时它已经完全离开画布；不要为这段尾部持续
    // 发出纹理绘制。
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
        // 位图缓存命中既不做字形塑形也不设置可变 Paint 状态。避免为常见路径触碰
        // RenderNode 画笔；只有 HDR/回退文本才需要它。
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
    drawLocalDanmakuHighlight(canvas, value, x, baseline)
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
    // 稳定的 SDR 弹幕只在其入场时被光栅化。这让它们的屏上生命周期走一条小纹理路径，
    // 避免每十秒产生数万次字形操作。
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

  /** 给本地刚发送的弹幕加一层随文字移动的标记，不修改弹幕内容和碰撞宽度。 */
  private fun drawLocalDanmakuHighlight(
    canvas: Canvas,
    value: PreparedDanmaku,
    x: Float,
    baseline: Float,
  ) {
    if (!value.item.isLocal) return
    val horizontalPadding = max(4f * density, value.textSize * .14f)
    val verticalPadding = max(2f * density, value.textSize * .09f)
    val bounds =
      RectF(
        x - horizontalPadding,
        baseline + value.ascent - verticalPadding,
        x + value.textWidth + horizontalPadding,
        baseline + value.descent + verticalPadding,
      )
    val fillAlpha = (opacity * 150f).toInt().coerceIn(0, 255)
    val borderAlpha = (opacity * 255f).toInt().coerceIn(0, 255)
    localHighlightPaint.color = Color.argb(fillAlpha, 255, 92, 138)
    localHighlightBorderPaint.color = Color.argb(borderAlpha, 255, 232, 240)
    localHighlightBorderPaint.strokeWidth = max(1f * density, value.textSize * .045f)
    val radius = minOf(bounds.height() / 2f, 8f * density)
    canvas.drawRoundRect(bounds, radius, radius, localHighlightPaint)
    canvas.drawRoundRect(bounds, radius, radius, localHighlightBorderPaint)
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
          occlusionController.currentSnapshot().blocked ||
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
          // SVG 路径始终描述允许显示的背景。把差值运算限制在实际视频帧内，
          // 让竖屏侧边栏仍然可以显示弹幕。
          Path()
            .apply {
              addRect(viewport, Path.Direction.CW)
              differenceSucceeded = op(allowedPath, Path.Op.DIFFERENCE)
            }
            .takeIf { differenceSucceeded }
        }
      // Skia 可能拒绝少数自相交的 SVG 路径。永远不要把运算前的视频矩形传给 clipOutPath：
      // 对这一次蒙版采样保留上一个有效的剪影（首次采样则保持不裁剪），并在下一个
      // 时间戳重试。
      cachedProtectedMaskPath =
        if (differenceSucceeded) nextProtectedMaskPath else previousProtectedMaskPath
      cachedMaskFrameIndex = frameIndex
      cachedMaskViewportLeft = viewport.left
      cachedMaskViewportTop = viewport.top
      cachedMaskViewportWidth = viewport.width()
      cachedMaskViewportHeight = viewport.height()
    }
    // 只裁剪掉主体比给每个源蒙版帧构造反向的全视口路径更便宜，也避免了在 HDR
    // SurfaceView 之上再增加一个大模板区域。
    cachedProtectedMaskPath?.let { protectedPath ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        canvas.clipOutPath(protectedPath)
      } else {
        @Suppress("DEPRECATION") canvas.clipPath(protectedPath, Region.Op.DIFFERENCE)
      }
    }
  }

  /** 直接追加采样到的 SVG 轮廓；该轮廓会一直缓存到下一个蒙版时间戳。 */
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
    val rendering = displayEnabled && !occlusionController.currentSnapshot().blocked
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
   * 被 Choreographer 失效的 Canvas 在可变刷新率面板上仍可能以默认 60 Hz 被合成器定步。
   * 当弹幕实际移动时，请求当前模式分辨率下支持的最高节奏。在这里使用 Display.refreshRate
   * 会形成反馈回路：一旦 60 fps 的视频表面让面板选择了 60 Hz，这个动画表面也会投 60 Hz
   * 的票，就再也无法恢复面板的 120 Hz 动画模式。
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
   * 窗口刷新率请求并不会自动描述这个独立合成的半透明 SurfaceView。给 SurfaceFlinger 一个
   * 匹配的按表面投票，让 Android 16 的自适应刷新策略把移动中的弹幕当作连续的 UI 动画，
   * 而不是普通或固定帧率的视频层。
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
      // 把每一帧活跃画面都提交到这个透明子表面。它保持独立地按播放器的显示节奏
      // 定步，而周围的 Compose 树保持静止。
      requestRender()
      return
    }
    val nextIndex = prepared.upperBound(positionMs)
    val untilNext = prepared.getOrNull(nextIndex)?.item?.timeMs?.minus(positionMs)
    val delayMs =
      untilNext
        ?.minus(FRAME_WAKE_MARGIN_MS)
        ?.coerceIn(MIN_IDLE_FRAME_DELAY_MS, MAX_IDLE_FRAME_DELAY_MS) ?: MAX_IDLE_FRAME_DELAY_MS
    // 空白片段只需要低频的位置检查。向后 seek 仍能在同一个有界延迟内被发现，
    // 而密集的活跃片段继续跟随每一个 VSync。
    loop.idleWakeRunnable?.let(loop.handler::removeCallbacks)
    loop.idleWakeRunnable = Runnable {
      loop.idleWakeRunnable = null
      requestRenderOnLoop(loop)
    }
    loop.handler.postDelayed(loop.idleWakeRunnable!!, delayMs)
  }

  /** 只有固定置顶弹幕需要避开刘海区域；滚动弹幕保留整个画布。 */
  private fun fixedTopSafeInset(): Float =
    if (fullscreen) {
      val viewHeight = renderViewHeight.takeIf { it > 0 } ?: renderHeight().coerceAtLeast(1)
      renderSafeInsetTop.toFloat() * renderHeight().coerceAtLeast(1) / viewHeight
    } else {
      0f
    }

  /**
   * 在标称飞行结束后留一段短尾。解码器追赶期间播放器时间戳可能以可见的块前进；
   * 没有这个余量，一帧可能在文本到达左边缘之前就从可见状态直接跳到移除边界。
   */
  private fun scrollingMotionDurationMs(): Long = (SCROLL_DURATION_MS / speed).toLong()

  /**
   * 让已经离开屏幕的评论再存活一小段尾期。旧实现把保护加在移动本身上，因此粗略的
   * 解码器时间戳仍可能从可见直接跳到移除。这里文本先到达 x=-textWidth，之后才有
   * 资格被移除。
   */
  private fun scrollingVisibleDurationMs(): Long =
    scrollingMotionDurationMs() + (SCROLL_EXIT_GUARD_MS / speed).toLong()

  private fun rebuildSchedule(preferredLanes: Map<DanmakuItem, Int> = emptyMap()) {
    scheduledLaneHeight = estimatedLaneHeight()
    scheduledLaneCount = laneCountFor(displayArea)
    scheduledViewportWidth = renderWidth()
    scheduled = schedule(sourceItems, scheduledLaneCount, densityLevel, preferredLanes)
    rebuildPreparedDanmaku()
  }

  private fun rebuildScheduleForViewportIfNeeded() {
    if (sourceItems.isEmpty()) return
    val nextLaneCount = laneCountFor(displayArea)
    if (nextLaneCount != scheduledLaneCount || renderWidth() != scheduledViewportWidth) {
      rebuildSchedule()
    }
  }

  /** 排队冷启动工作集，不在动画线程上光栅化弹幕。 */
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

  /** 让一个小型异步生产者保持领先于新入场的弹幕。 */
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
    preferredLanes: Map<DanmakuItem, Int>,
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
        // 直播窗口裁掉旧前缀时，仍在屏上的弹幕必须沿用原轨道，不能从第零轨重新排布。
        val preferredLane =
          preferredLanes[item]?.takeIf { it >= 0 && it + laneSpan <= laneCount }
        val lane =
          preferredLane
            ?: when (item.type) {
              TYPE_TOP ->
                fixedLaneFor(item, topAvailableAt, laneSpan, densityLevel) ?: return@forEach
              TYPE_BOTTOM ->
                fixedLaneFor(item, bottomAvailableAt, laneSpan, densityLevel) ?: return@forEach
              else ->
                scrollingLaneFor(item, scrollingPrevious, laneSpan, densityLevel)
                  ?: return@forEach
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
   * 密度只控制水平距离。标准档位要等上一条的尾部到达右边缘后下一条才入场；低档位
   * 增加间隔，而高档位允许受控重叠。垂直轨道数与此无关，只来自显示区域。
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
   * 在两个时钟一致时按显示节奏插值 Media3 的毫秒位置。当一次绘制观察到真正的不连续时，
   * 现有的跳变/追赶路径仍然权威。这消除了 120 Hz 显示器上的重复位置，而不改变 seek
   * 或恢复行为。
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
      // 重播以及任何不经过 AppRoot seek 控件的外部 seek。
      resetVisualClock(rawPositionMs, visualClockEpoch, frameTimeNs)
      return rawPositionMs
    }

    if (rawPositionMs - visualPositionMs > MAX_SMOOTHABLE_CLOCK_JUMP_MS) {
      // 从观看历史进入视频可能让 ExoPlayer 从零跳到保存的位置，而不经过本页的 seek
      // 控件。那是时间线不连续，不是解码器追赶：直接跳到真实位置，避免旧弹幕被
      // 快速重放。
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

    // 短暂的解码器停顿可能让量化后的播放器时钟落后于插值时钟。保持最后的视觉位置
    // 直到 Media3 赶上，而不是让弹幕倒退。
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

  /** 为 true 表示跳过 [startMs, endMs] 可能让一条评论在飞行途中出现或消失。 */
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

  /** 垂直覆盖范围只由显示区域控制，从不取决于水平密度。 */
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
    while (imageAllocationBytes > imageCacheBytes && imageBitmaps.isNotEmpty()) {
      val eldestKey = imageBitmaps.entries.first().key
      imageAllocationBytes -= imageBitmaps.remove(eldestKey)?.allocationByteCount ?: 0
    }
  }

  private fun clearRenderCache() {
    bitmapTextCache.clear()
  }

  /**
   * 在一个后台工作器上光栅化 SDR 弹幕。缓存未命中时该帧仍走 Canvas 的直接字形路径，
   * 因此播放永远不会在动画线程上分配或绘制软件 Bitmap。完成的位图由后续帧接管，
   * 并始终受字节数上限约束。
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
      return synchronized(lock) {
        val bitmap = bitmaps[value.cacheKey] ?: return@synchronized false
        val paddingX = textCachePaddingX()
        val paddingY = textCachePaddingY()
        val top = baseline + value.ascent - paddingY
        canvas.drawBitmap(bitmap, x - paddingX, top, null)
        true
      }
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
              if (!replaced.isRecycled) replaced.recycle()
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
      if (requiredBytes > textCacheBytes) return null
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
      while (allocatedBytes > textCacheBytes && bitmaps.isNotEmpty()) {
        val eldestKey = bitmaps.entries.first().key
        bitmaps.remove(eldestKey)?.let { bitmap ->
          allocatedBytes -= bitmap.allocationByteCount.toLong()
          if (!bitmap.isRecycled) bitmap.recycle()
        }
      }
    }

    fun clear() {
      synchronized(lock) {
        generation++
        buildQueue.clear()
        pendingKeys.clear()
        bitmaps.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
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
        bitmaps.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
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

  /** 内容加上影响像素的样式，让重复出现的文本共享同一个光栅缓存条目。 */
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
 * 当 Media3 的毫秒时钟保持足够接近、足以证明播放连续时，按显示节奏推进视觉时钟。
 * 返回 null 表示保留不连续路径。
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

/**
 * 判断一次列表替换是否为直播有界窗口的“裁掉旧前缀并追加新后缀”。
 *
 * 纯追加不需要特殊处理；任意中段删除（例如修改屏蔽词）也不能被误认为窗口滑动。
 */
internal fun isDanmakuSlidingWindowReplacement(
  previous: List<DanmakuItem>,
  current: List<DanmakuItem>,
): Boolean {
  if (previous.size < 2 || current.size < 2) return false
  val previousStart = previous.indexOf(current.first())
  if (previousStart <= 0) return false
  val overlapSize = minOf(previous.size - previousStart, current.size)
  if (overlapSize <= 0 || current.size <= overlapSize) return false
  return previous.subList(previousStart, previousStart + overlapSize) ==
    current.subList(0, overlapSize)
}

/** 把仍在屏上的旧弹幕补回滑动窗口前端，直到其自然离场。 */
internal fun mergeActiveDanmakuSlidingWindow(
  incoming: List<DanmakuItem>,
  activeScheduledItems: List<DanmakuItem>,
): List<DanmakuItem> {
  if (activeScheduledItems.isEmpty()) return incoming
  val incomingItems = incoming.toHashSet()
  val retainedPrefix = activeScheduledItems.filterNot(incomingItems::contains)
  if (retainedPrefix.isEmpty()) return incoming
  return (retainedPrefix + incoming).sortedBy(DanmakuItem::timeMs)
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

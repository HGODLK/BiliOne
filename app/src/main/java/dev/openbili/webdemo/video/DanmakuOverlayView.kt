package dev.openbili.webdemo.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.os.Build
import android.view.View
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
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
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

/**
 * A PlayerView-native danmaku layer.
 *
 * Keeping this view in PlayerView's root overlay covers the full player, including portrait-video
 * sidebars, while the smart-mask path remains mapped to Media3's real content frame.
 */
class DanmakuOverlayView(context: Context) : View(context) {
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
  private var lastNonEmptyMaskFrameIndex = -1
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
  private var positionProvider: () -> Long = { 0L }
  private var displayEnabled = false
  private var transitionSuppressed = false
  private var playbackPaused = true
  private var fullscreen = false
  private var highDynamicRange = false
  private var opacity = .72f
  private var displayArea = .75f
  private var densityLevel = 3
  private var fontScale = 1f
  private var speed = 1f
  private var animationRunning = false
  private var frozenPositionMs = 0L
  // The player clock can jump several seconds while the decoder/compositor is catching up during
  // startup. Keep a visual clock so an already-visible comment is not discarded before a frame
  // has had a chance to draw it at the left edge.
  private var visualPositionMs = 0L
  private var visualClockInitialized = false
  private var visualClockEpoch = Long.MIN_VALUE
  private var visualClockCatchingUp = false
  private val bitmapTextCache = BitmapTextCache()
  private val imageBitmaps = LinkedHashMap<String, Bitmap>(32, .75f, true)
  private val imageRequests = HashSet<String>()
  private var imageAllocationBytes = 0L
  private var imageScope = newImageScope()

  init {
    // A forced hardware layer allocates and refreshes a full-player offscreen texture every frame.
    // Keep this view in PlayerView's display list and cache only the individual SDR labels.
    setLayerType(LAYER_TYPE_NONE, null)
    isClickable = false
    isFocusable = false
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
    fontScale: Float,
    speed: Float,
    positionEpoch: Long,
    currentPositionProvider: () -> Long,
  ) {
    val nextSpeed = speed.coerceIn(.6f, 1.8f)
    val speedChanged = this.speed != nextSpeed
    val nextOpacity = opacity.coerceIn(.2f, 1f)
    val opacityChanged = this.opacity != nextOpacity
    val nextDisplayArea = displayArea.coerceIn(.25f, 1f)
    val displayAreaChanged = this.displayArea != nextDisplayArea
    val nextDensityLevel = densityLevel.coerceIn(MIN_DENSITY_LEVEL, MAX_DENSITY_LEVEL)
    val densityLevelChanged = this.densityLevel != nextDensityLevel
    val nextFontScale = fontScale.coerceIn(.7f, 1.5f)
    val fontScaleChanged = this.fontScale != nextFontScale
    val itemsChanged = items !== sourceItemsRef
    val maskChanged = mask !== maskTimeline
    val smartBlockingChanged = smartBlocking != smartBlockingEnabled
    val highDynamicRangeChanged = highDynamicRange != this.highDynamicRange
    this.speed = nextSpeed
    this.opacity = nextOpacity
    this.displayArea = nextDisplayArea
    this.densityLevel = nextDensityLevel
    this.fontScale = nextFontScale
    maskTimeline = mask
    smartBlockingEnabled = smartBlocking
    this.highDynamicRange = highDynamicRange
    if (itemsChanged) {
      sourceItemsRef = items
      sourceItems = items.sortedBy(DanmakuItem::timeMs)
      requestImages(sourceItems)
    }
    if (itemsChanged || fontScaleChanged) {
      measurements.clear()
      inlineSegmentCache.clear()
    }
    val scheduleChanged =
      speedChanged || displayAreaChanged || densityLevelChanged || fontScaleChanged || itemsChanged
    if (scheduleChanged) {
      rebuildSchedule()
    } else if (opacityChanged) {
      clearRenderCache()
    }
    if (maskChanged || smartBlockingChanged) clearMaskClipCache()
    if (highDynamicRangeChanged) clearRenderCache()
    positionProvider = currentPositionProvider
    val rawPositionMs = currentPositionProvider().coerceAtLeast(0L)
    if (scheduleChanged || visualClockEpoch != positionEpoch || !visualClockInitialized) {
      resetVisualClock(rawPositionMs, positionEpoch)
    }
    if (paused) {
      // Preserve a deliberately smoothed on-screen position while the player is paused. Explicit
      // seeks carry a new epoch above and therefore intentionally reset to their target instead.
      frozenPositionMs = if (visualClockInitialized) visualPositionMs else rawPositionMs
    }
    displayEnabled = enabled && scheduled.isNotEmpty()
    playbackPaused = paused
    this.fullscreen = fullscreen
    refreshRenderingState()
    invalidate()
  }

  /** The visible Media3 frame, in this full-player overlay's coordinates. */
  fun setVideoViewport(viewport: RectF) {
    val next = viewport.takeIf { it.width() > 0f && it.height() > 0f } ?: return
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
    invalidate()
  }

  /** Avoid relaying out and redrawing the overlay while the SurfaceView bounds animate. */
  fun setTransitionSuppressed(suppressed: Boolean) {
    if (transitionSuppressed == suppressed) return
    transitionSuppressed = suppressed
    refreshRenderingState()
    if (!suppressed) invalidate()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (!imageScope.isActive) imageScope = newImageScope()
    requestImages(sourceItems)
    refreshRenderingState()
  }

  override fun onDetachedFromWindow() {
    stopFrames()
    imageScope.cancel()
    imageRequests.clear()
    clearRenderCache()
    super.onDetachedFromWindow()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    // AspectRatioFrameLayout may settle one layout pass after a SurfaceView/window resize.
    // Request a fresh frame immediately so the clip boundary never lingers at its old width.
    if (w != oldw || h != oldh) {
      clearMaskClipCache()
      rebuildScheduleForViewportIfNeeded()
      invalidate()
    }
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (!displayEnabled || width <= 0 || height <= 0) return
    val positionMs = if (playbackPaused) frozenPositionMs else resolveVisualPosition()
    val scrollingMotionDurationMs = scrollingMotionDurationMs()
    val scrollingVisibleDurationMs = scrollingVisibleDurationMs()
    // The overlay covers the complete player, while smart blocking is mapped only to Media3's
    // actual video frame. This lets portrait videos use their side letterbox for danmaku.
    val viewport = RectF(0f, 0f, width.toFloat(), height.toFloat())
    val saveCount = canvas.save()
    canvas.clipRect(viewport)
    applySmartMaskClip(canvas, positionMs, videoViewport ?: viewport)
    val first = prepared.lowerBound(positionMs - scrollingVisibleDurationMs)
    val last = prepared.upperBound(positionMs)
    var lastTextSize = Float.NaN
    var lastColor = Int.MIN_VALUE
    var activeCount = 0
    var index = first
    while (index < last) {
      val value = prepared[index]
      if (positionMs <= value.endMs) {
        activeCount++
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
    val laneHeight = value.laneHeight
    val baseline =
      when (item.type) {
        TYPE_BOTTOM ->
          viewport.top + viewport.height() * displayArea -
            (value.lane + 1) * laneHeight -
            value.ascent
        TYPE_TOP -> max(viewport.top, fixedTopSafeInset()) + value.lane * laneHeight - value.ascent
        else -> viewport.top + value.lane * laneHeight - value.ascent
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
    // Do not switch the complete active set between RenderNode and Bitmap when density crosses a
    // threshold. That transition uploads every visible label again and makes a few labels blink on
    // affected tablet GPU drivers. SDR uses one stable bitmap path; HDR keeps direct glyph drawing
    // to avoid promoting many textures above the protected SurfaceView.
    val useBitmapTextCache = !highDynamicRange
    if (
      useBitmapTextCache &&
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
    if (!isAttachedToWindow) return
    animationRunning = true
    postInvalidateOnAnimation()
  }

  private fun applySmartMaskClip(canvas: Canvas, positionMs: Long, viewport: RectF) {
    if (!smartBlockingEnabled) return
    val timeline = maskTimeline ?: return
    val frameIndex = timeline.frameIndexAt(positionMs)
    if (frameIndex < 0) return
    if (
      frameIndex != cachedMaskFrameIndex ||
        viewport.left != cachedMaskViewportLeft ||
        viewport.top != cachedMaskViewportTop ||
        viewport.width() != cachedMaskViewportWidth ||
        viewport.height() != cachedMaskViewportHeight
    ) {
      val protectedContours = timeline.protectedContoursAt(frameIndex)
      val sameViewport =
        viewport.left == cachedMaskViewportLeft &&
          viewport.top == cachedMaskViewportTop &&
          viewport.width() == cachedMaskViewportWidth &&
          viewport.height() == cachedMaskViewportHeight
      cachedProtectedMaskPath =
        if (protectedContours.isEmpty()) {
          // Some upstream masks contain an isolated empty sample between two valid subject
          // contours. Holding the preceding contour for at most two samples avoids a one-frame
          // unmask/remask flash without leaving a stale subject cutout across a real empty span.
          cachedProtectedMaskPath.takeIf {
            sameViewport && frameIndex - lastNonEmptyMaskFrameIndex in 1..2
          }
        } else {
          val sourcePath =
            Path().apply {
              fillType =
                if (timeline.usesEvenOddFillAt(frameIndex)) Path.FillType.EVEN_ODD
                else Path.FillType.WINDING
              var index = 0
              while (index + 1 < protectedContours.size) {
                if (protectedContours[index].isNaN() || protectedContours[index + 1].isNaN()) {
                  index += 2
                  continue
                }
                val start = index
                while (
                  index + 1 < protectedContours.size &&
                    !protectedContours[index].isNaN() &&
                    !protectedContours[index + 1].isNaN()
                ) {
                  index += 2
                }
                appendDirectContour(protectedContours, start, index, viewport)
              }
            }
          val resolvedPath =
            if (timeline.isInverseFillAt(frameIndex)) {
              // Inverse SVG masks describe the allowed background. Limit their inverse region to
              // the actual video frame, otherwise clipOutPath would also erase portrait sidebars.
              Path().apply {
                addRect(viewport, Path.Direction.CW)
                op(sourcePath, Path.Op.DIFFERENCE)
              }
            } else {
              sourcePath
            }
          lastNonEmptyMaskFrameIndex = frameIndex
          resolvedPath
        }
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
    lastNonEmptyMaskFrameIndex = -1
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
    if (rendering && !playbackPaused) requestFrame() else stopFrames()
  }

  private fun scheduleNextFrame(positionMs: Long, activeCount: Int) {
    if (!animationRunning || playbackPaused) return
    if (activeCount > 0) {
      // Keep every danmaku type on the player's VSync. A half-rate child View can be omitted from
      // an intervening 120 Hz parent frame on some SurfaceView compositors, which looks like a
      // one-frame disappearance even though its timeline remains active.
      postInvalidateOnAnimation()
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
    postInvalidateDelayed(delayMs)
  }

  /** Only fixed top comments need cutout clearance; scrolling comments keep the full canvas. */
  private fun fixedTopSafeInset(): Float =
    if (fullscreen) {
      (ViewCompat.getRootWindowInsets(this)?.displayCutout?.safeInsetTop ?: 0).toFloat()
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
    scheduledViewportWidth = width
    scheduled = schedule(sourceItems, scheduledLaneCount, densityLevel)
    rebuildPreparedDanmaku()
  }

  private fun rebuildScheduleForViewportIfNeeded() {
    if (sourceItems.isEmpty()) return
    val nextLaneCount = laneCountFor(displayArea)
    if (nextLaneCount != scheduledLaneCount || width != scheduledViewportWidth) rebuildSchedule()
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
        val lane =
          when (item.type) {
            TYPE_TOP -> fixedLaneFor(item, topAvailableAt, densityLevel) ?: return@forEach
            TYPE_BOTTOM -> fixedLaneFor(item, bottomAvailableAt, densityLevel) ?: return@forEach
            else -> scrollingLaneFor(item, scrollingPrevious, densityLevel) ?: return@forEach
          }
        add(ScheduledDanmaku(item, lane))
        when (item.type) {
          TYPE_TOP -> topAvailableAt[lane] = item.timeMs + FIXED_DURATION_MS
          TYPE_BOTTOM -> bottomAvailableAt[lane] = item.timeMs + FIXED_DURATION_MS
          else -> scrollingPrevious[lane] = item
        }
      }
    }
  }

  private fun fixedLaneFor(
    item: DanmakuItem,
    availableAt: LongArray,
    densityLevel: Int,
  ): Int? =
    availableAt.indices.firstOrNull { availableAt[it] <= item.timeMs }
      ?: if (item.isLocal || densityLevel == MAX_DENSITY_LEVEL) {
        availableAt.indices.minBy { availableAt[it] }
      } else {
        null
      }

  private fun scrollingLaneFor(
    item: DanmakuItem,
    previousItems: Array<DanmakuItem?>,
    densityLevel: Int,
  ): Int? {
    val lane =
      previousItems.indices.firstOrNull { index ->
        scrollingLaneAvailable(previousItems[index], item, densityLevel)
      }
    if (lane != null) return lane
    if (!item.isLocal && densityLevel != MAX_DENSITY_LEVEL) return null
    return previousItems.indices.minBy { previousItems[it]?.timeMs ?: Long.MIN_VALUE }
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
    val viewportWidth = width.takeIf { it > 0 }?.toFloat() ?: FALLBACK_VIEWPORT_WIDTH_DP * density
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
    clearRenderCache()
    prepared = scheduled.mapIndexed { index, scheduledItem ->
      val item = scheduledItem.item
      val measurement = measurementFor(item)
      PreparedDanmaku(
        cacheKey = index,
        item = item,
        lane = scheduledItem.lane,
        textSize = measurement.textSize,
        textWidth = measurement.textWidth,
        laneHeight =
          if (sourceItems.any { !it.imageUrl.isNullOrBlank() }) scheduledLaneHeight
          else measurement.laneHeight,
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
          inlineSegments(item).sumOf { segment ->
            when (segment) {
              is InlineDanmakuSegment.Text -> textPaint.measureText(segment.value).toDouble()
              is InlineDanmakuSegment.Emote ->
                max(inlineImageSize, textPaint.measureText(segment.value.token)).toDouble()
            }
          }.toFloat()
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
   * Follows Media3's position exactly on ordinary frames. When a single draw observes a very large
   * advance that crosses an active/upcoming danmaku window, limit just that visual advance and then
   * catch up over following VSyncs. This protects startup and decoder-recovery jank without
   * changing normal playback, quality switches, or explicit seek behavior.
   */
  private fun resolveVisualPosition(): Long {
    val rawPositionMs = positionProvider().coerceAtLeast(0L)
    if (!visualClockInitialized) {
      resetVisualClock(rawPositionMs, visualClockEpoch)
      return rawPositionMs
    }
    if (rawPositionMs + CLOCK_BACKWARD_RESET_TOLERANCE_MS < visualPositionMs) {
      // Replay and any external seek that does not travel through AppRoot's seek controls.
      resetVisualClock(rawPositionMs, visualClockEpoch)
      return rawPositionMs
    }
    if (rawPositionMs <= visualPositionMs) return visualPositionMs

    if (rawPositionMs - visualPositionMs > MAX_SMOOTHABLE_CLOCK_JUMP_MS) {
      // Entering a video from watch history can move ExoPlayer from zero to a saved position
      // without travelling through this screen's seek controls. That is a timeline discontinuity,
      // not decoder catch-up: snap to the real position so old danmaku are not replayed rapidly.
      resetVisualClock(rawPositionMs, visualClockEpoch)
      return rawPositionMs
    }

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
    return visualPositionMs
  }

  private fun resetVisualClock(positionMs: Long, epoch: Long) {
    visualPositionMs = positionMs
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
      height.takeIf { it > 0 }?.toFloat() ?: FALLBACK_VIEWPORT_HEIGHT_DP * density
    val visibleHeight = viewportHeight * displayArea
    val textHeight = estimatedLaneHeight()
    return (((visibleHeight - textHeight).coerceAtLeast(0f) / textHeight).toInt() + 1)
      .coerceAtLeast(1)
  }

  private fun estimatedLaneHeight(): Float {
    val textLaneHeight = (27f * density * fontScale).coerceAtLeast(1f)
    return sourceItems
      .asSequence()
      .filter { !it.imageUrl.isNullOrBlank() || it.inlineEmotes.isNotEmpty() }
      .map { measurementFor(it).laneHeight }
      .maxOrNull()
      ?.coerceAtLeast(textLaneHeight) ?: textLaneHeight
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
        if (imageBitmaps.containsKey(url) || !imageRequests.add(url)) return@forEach
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
          imageRequests.remove(url)
          bitmap?.let {
            imageBitmaps[url] = it
            imageAllocationBytes += it.allocationByteCount
            trimImageCache()
            invalidate()
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

  /** Keeps every SDR label on one bounded rendering path for its complete on-screen lifetime. */
  private inner class BitmapTextCache {
    private val bitmaps = LinkedHashMap<Int, Bitmap>(256, .75f, true)
    private var allocationBytes = 0

    fun draw(
      canvas: Canvas,
      value: PreparedDanmaku,
      x: Float,
      baseline: Float,
    ): Boolean {
      val bitmap = bitmapFor(value) ?: return false
      val paddingX = textCachePaddingX()
      val paddingY = textCachePaddingY()
      val top = baseline + value.ascent - paddingY
      canvas.drawBitmap(bitmap, x - paddingX, top, null)
      return true
    }

    private fun bitmapFor(value: PreparedDanmaku): Bitmap? {
      bitmaps[value.cacheKey]?.let {
        return it
      }
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
      while (allocationBytes + requiredBytes > BITMAP_TEXT_CACHE_BYTES && bitmaps.isNotEmpty()) {
        val eldestKey = bitmaps.entries.first().key
        allocationBytes -= bitmaps.remove(eldestKey)?.allocationByteCount ?: 0
      }
      val bitmap =
        runCatching { Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888) }
          .getOrNull() ?: return null
      val cachedTextPaint = Paint(textPaint)
      val cachedOutlinePaint = Paint(outlinePaint)
      configurePaints(
        value = value,
        targetTextPaint = cachedTextPaint,
        targetOutlinePaint = cachedOutlinePaint,
        gradientStartX = paddingX,
      )
      val recordedBaseline = paddingY - value.ascent
      Canvas(bitmap).apply {
        drawText(value.item.content, paddingX, recordedBaseline, cachedOutlinePaint)
        drawText(value.item.content, paddingX, recordedBaseline, cachedTextPaint)
      }
      bitmaps[value.cacheKey] = bitmap
      allocationBytes += bitmap.allocationByteCount
      return bitmap
    }

    fun clear() {
      bitmaps.clear()
      allocationBytes = 0
    }
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

  private data class PreparedDanmaku(
    val cacheKey: Int,
    val item: DanmakuItem,
    val lane: Int,
    val textSize: Float,
    val textWidth: Float,
    val laneHeight: Float,
    val ascent: Float,
    val descent: Float,
    val endMs: Long,
    val color: Int,
  )

  private companion object {
    const val MIN_DENSITY_LEVEL = 1
    const val MAX_DENSITY_LEVEL = 5
    const val FALLBACK_VIEWPORT_HEIGHT_DP = 360f
    const val FALLBACK_VIEWPORT_WIDTH_DP = 640f
    const val TYPE_BOTTOM = 4
    const val TYPE_TOP = 5
    const val SCROLL_DURATION_MS = 6_000L
    const val SCROLL_EXIT_GUARD_MS = 900L
    const val FIXED_DURATION_MS = 4_000L
    const val BITMAP_TEXT_CACHE_BYTES = 24 * 1024 * 1024L
    const val MAX_CACHED_TEXT_BITMAP_DIMENSION = 2_048
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
    const val MAX_DANMAKU_VISIBLE_DURATION_MS = 12_000L
    val VIP_GRADIENT_RGB =
      intArrayOf(0xFF5A8F, 0xFF9A5A, 0xFFD75A, 0x63E6BE, 0x66C7F2, 0x8C9EFF, 0xC792EA)
  }
}

private inline fun IntArray.mapToIntArray(transform: (Int) -> Int): IntArray =
  IntArray(size) { index -> transform(this[index]) }

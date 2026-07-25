package dev.openbili.webdemo.video

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.View
import android.view.Window
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.R
import dev.openbili.webdemo.feed.FeedItem
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PlayerGestureLayer(
  enabledBrightness: Boolean,
  enabledVolume: Boolean,
  enabledSeek: Boolean,
  enabledFullscreenToggle: Boolean,
  positionProvider: () -> Long,
  durationMs: Long,
  onSeek: (Long) -> Unit,
  onIndicator: (GestureIndicator) -> Unit,
  onSeekPreview: (Long?) -> Unit,
  onSeekCancel: () -> Unit,
  onToggleControls: () -> Unit,
  onDoubleTap: () -> Unit,
  onTemporarySpeedChanged: (Boolean) -> Unit,
  isFullscreen: Boolean,
  onFullscreenChanged: (Boolean) -> Unit,
  seekEdgeInset: Dp,
  modifier: Modifier = Modifier,
) {
  val view = LocalView.current
  val activity = view.context as? Activity
  val audio =
    remember(view.context) { view.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
  val brightnessApplier =
    remember(activity?.window, view) {
      FrameCoalescedWindowBrightness(activity?.window, view)
    }
  DisposableEffect(brightnessApplier) {
    onDispose { brightnessApplier.cancel() }
  }
  var showSeekCancelHint by remember { mutableStateOf(false) }
  var cancelSeekOnRelease by remember { mutableStateOf(false) }
  var speedBoosting by remember { mutableStateOf(false) }
  val pointerGate = remember { PlayerPointerGate() }
  val latestToggleControls by rememberUpdatedState(onToggleControls)
  val latestDoubleTap by rememberUpdatedState(onDoubleTap)
  val latestTemporarySpeedChanged by rememberUpdatedState(onTemporarySpeedChanged)
  val latestFullscreen by rememberUpdatedState(isFullscreen)
  val latestFullscreenChanged by rememberUpdatedState(onFullscreenChanged)
  val latestSeek by rememberUpdatedState(onSeek)
  val latestSeekPreview by rememberUpdatedState(onSeekPreview)
  val latestSeekCancel by rememberUpdatedState(onSeekCancel)
  Box(
    modifier
      .twoFingerPlayerGesture(
        enabled = enabledFullscreenToggle,
        edgeInset = 24.dp,
        isFullscreen = { latestFullscreen },
        onFullscreenChanged = { latestFullscreenChanged(it) },
        onSeekBy = { deltaMs ->
          val target =
            (positionProvider() + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
          latestSeek(target)
          latestSeekPreview(null)
        },
        onTwoFingerContactChanged = { active ->
          if (active) {
            pointerGate.claim()
            if (speedBoosting) {
              speedBoosting = false
              latestTemporarySpeedChanged(false)
            }
            if (showSeekCancelHint) latestSeekCancel()
            latestSeekPreview(null)
            showSeekCancelHint = false
            cancelSeekOnRelease = false
          } else {
            pointerGate.release()
          }
        },
      )
      .pointerInput(Unit) {
        var tapGeneration = pointerGate.generation
        detectTapGestures(
          onPress = {
            tapGeneration = pointerGate.generation
            coroutineScope {
              var boosted = false
              val boostJob = launch {
                delay(500)
                if (pointerGate.active || pointerGate.generation != tapGeneration) return@launch
                boosted = true
                speedBoosting = true
                latestTemporarySpeedChanged(true)
              }
              try {
                awaitRelease()
              } finally {
                boostJob.cancel()
                if (boosted) {
                  speedBoosting = false
                  latestTemporarySpeedChanged(false)
                }
              }
            }
          },
          onTap = {
            if (!pointerGate.active && pointerGate.generation == tapGeneration) {
              latestToggleControls()
            }
          },
          onDoubleTap = {
            if (!pointerGate.active && pointerGate.generation == tapGeneration) {
              latestDoubleTap()
            }
          },
          onLongPress = {},
        )
      }
      .pointerInput(enabledBrightness, enabledVolume, enabledSeek, durationMs) {
        var start = Offset.Zero
        var totalX = 0f
        var totalY = 0f
        var startBrightness = .5f
        var startVolume = 0
        var startPosition = 0L
        var pendingSeek: Long? = null
        var gestureAllowed = true
        var mode = PlayerDragMode.UNDECIDED
        var dragGeneration = pointerGate.generation
        detectDragGestures(
          onDragStart = { point ->
            dragGeneration = pointerGate.generation
            start = point
            totalX = 0f
            totalY = 0f
            startPosition = positionProvider()
            pendingSeek = null
            mode = PlayerDragMode.UNDECIDED
            showSeekCancelHint = false
            cancelSeekOnRelease = false
            val edgeInsetPx = seekEdgeInset.toPx()
            gestureAllowed = point.x >= edgeInsetPx && point.x <= size.width - edgeInsetPx
            startBrightness =
              activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }
                ?: (android.provider.Settings.System.getInt(
                  view.context.contentResolver,
                  android.provider.Settings.System.SCREEN_BRIGHTNESS,
                  128,
                ) / 255f)
            startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
          },
          onDragEnd = {
            if (
              mode == PlayerDragMode.SEEK &&
                !pointerGate.active &&
                pointerGate.generation == dragGeneration
            ) {
              if (cancelSeekOnRelease) onSeekCancel() else pendingSeek?.let(onSeek)
            }
            onSeekPreview(null)
            pendingSeek = null
            showSeekCancelHint = false
            cancelSeekOnRelease = false
          },
          onDragCancel = {
            if (mode == PlayerDragMode.SEEK) onSeekCancel()
            pendingSeek = null
            onSeekPreview(null)
            showSeekCancelHint = false
            cancelSeekOnRelease = false
          },
        ) { change, drag ->
          if (pointerGate.active || pointerGate.generation != dragGeneration) {
            change.consume()
            return@detectDragGestures
          }
          if (!gestureAllowed) return@detectDragGestures
          totalX += drag.x
          totalY += drag.y
          val absX = kotlin.math.abs(totalX)
          val absY = kotlin.math.abs(totalY)
          if (mode == PlayerDragMode.UNDECIDED) {
            val threshold = kotlin.math.max(viewConfiguration.touchSlop * 1.5f, 24.dp.toPx())
            if (kotlin.math.max(absX, absY) < threshold) return@detectDragGestures
            mode =
              when {
                enabledSeek && absX > absY * 1.35f -> PlayerDragMode.SEEK
                absY > absX * 1.2f && start.x / size.width < .36f && enabledBrightness ->
                  PlayerDragMode.BRIGHTNESS
                absY > absX * 1.2f && start.x / size.width > .64f && enabledVolume ->
                  PlayerDragMode.VOLUME
                else -> return@detectDragGestures
              }
          }
          if (mode == PlayerDragMode.SEEK) {
            val seekWindowMs =
              (durationMs * .35f).toLong().coerceIn(30_000L, 300_000L).coerceAtMost(durationMs)
            val target =
              (startPosition + totalX / size.width * seekWindowMs)
                .toLong()
                .coerceIn(0L, durationMs.coerceAtLeast(0L))
            pendingSeek = target
            onSeekPreview(target)
            val startedInCancelZone = start.y < size.height / 3f
            cancelSeekOnRelease =
              if (startedInCancelZone) totalY < -64.dp.toPx()
              else change.position.y < size.height / 3f
            showSeekCancelHint = true
            change.consume()
            return@detectDragGestures
          }
          when (mode) {
            PlayerDragMode.BRIGHTNESS -> {
              val value = (startBrightness - totalY / size.height).coerceIn(.05f, 1f)
              brightnessApplier.submit(value)
              onIndicator(GestureIndicator(GestureIndicatorKind.BRIGHTNESS, value))
              change.consume()
            }
            PlayerDragMode.VOLUME -> {
              val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
              val value = (startVolume - totalY / size.height * max).roundToInt().coerceIn(0, max)
              audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
              onIndicator(
                GestureIndicator(
                  GestureIndicatorKind.VOLUME,
                  value.toFloat() / max.coerceAtLeast(1),
                )
              )
              change.consume()
            }
            else -> Unit
          }
        }
      }
  ) {
    AnimatedVisibility(
      visible = speedBoosting,
      modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
      enter = fadeIn(tween(90)),
      exit = fadeOut(tween(90)),
    ) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = .76f),
        contentColor = Color.White,
      ) {
        Text(
          "长按 3.0×",
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
    AnimatedVisibility(
      visible = showSeekCancelHint,
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp),
      enter = fadeIn(tween(90)),
      exit = fadeOut(tween(90)),
    ) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color =
          if (cancelSeekOnRelease) MaterialTheme.colorScheme.errorContainer.copy(alpha = .94f)
          else Color.Black.copy(alpha = .72f),
        contentColor =
          if (cancelSeekOnRelease) MaterialTheme.colorScheme.onErrorContainer else Color.White,
      ) {
        Text(
          if (cancelSeekOnRelease) "松手取消调整" else "上滑到屏幕上方 1/3 区域可取消",
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

internal enum class PlayerDragMode {
  UNDECIDED,
  SEEK,
  BRIGHTNESS,
  VOLUME,
}

@Composable
internal fun GestureIndicatorOverlay(
  indicator: GestureIndicator,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(24.dp),
    color = Color.Black.copy(alpha = .72f),
    contentColor = Color.White,
  ) {
    Column(
      modifier = Modifier.width(58.dp).padding(horizontal = 14.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        painter =
          painterResource(
            if (indicator.kind == GestureIndicatorKind.BRIGHTNESS) R.drawable.ic_gesture_sun
            else R.drawable.ic_gesture_volume
          ),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(24.dp),
      )
      Box(
        Modifier.width(6.dp)
          .height(116.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = .24f))
      ) {
        Box(
          Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(indicator.value.coerceIn(0f, 1f))
            .background(Color.White, CircleShape)
        )
      }
    }
  }
}

internal enum class GestureIndicatorKind {
  BRIGHTNESS,
  VOLUME,
}

internal data class GestureIndicator(val kind: GestureIndicatorKind, val value: Float)

/**
 * Window attributes can trigger a display/color-mode transaction on HDR SurfaceView devices.
 * Pointer input may produce several drag samples in one display frame, so coalescing them to one
 * assignment per VSync avoids racing that transaction with an SDR/HDR surface handoff while
 * preserving the exact final brightness and continuous gesture feedback.
 */
private class FrameCoalescedWindowBrightness(
  private val window: Window?,
  private val scheduler: View,
) {
  private var pendingValue: Float? = null
  private var posted = false
  private val applyPending =
    Runnable {
      posted = false
      val value = pendingValue ?: return@Runnable
      pendingValue = null
      val target = window ?: return@Runnable
      val attributes = target.attributes
      if (kotlin.math.abs(attributes.screenBrightness - value) < .002f) return@Runnable
      attributes.screenBrightness = value
      target.attributes = attributes
    }

  fun submit(value: Float) {
    pendingValue = value
    if (posted) return
    posted = true
    scheduler.postOnAnimation(applyPending)
  }

  fun cancel() {
    scheduler.removeCallbacks(applyPending)
    posted = false
    pendingValue = null
  }
}

/** Shared arbitration state for the independent pointer detectors in [PlayerGestureLayer]. */
private class PlayerPointerGate {
  var generation: Long = 0L
    private set
  var active: Boolean = false
    private set

  fun claim() {
    if (active) return
    active = true
    generation += 1L
  }

  fun release() {
    active = false
  }
}

internal fun fullscreenTargetForSpan(
  isFullscreen: Boolean,
  initialSpan: Float,
  currentSpan: Float,
  minimumMovementPx: Float,
): Boolean? {
  if (initialSpan <= 0f) return null
  val threshold =
    kotlin.math.max(minimumMovementPx.coerceAtLeast(0f), initialSpan * PINCH_SPAN_FRACTION)
  val delta = currentSpan - initialSpan
  return when {
    !isFullscreen && delta >= threshold -> true
    isFullscreen && delta <= -threshold -> false
    else -> null
  }
}

internal fun Modifier.twoFingerPlayerGesture(
  enabled: Boolean,
  edgeInset: Dp,
  isFullscreen: () -> Boolean,
  onFullscreenChanged: (Boolean) -> Unit,
  onSeekBy: (Long) -> Unit,
  onTwoFingerContactChanged: (Boolean) -> Unit,
): Modifier =
  pointerInput(enabled, edgeInset) {
    var previousTapAt = 0L
    var previousTapCenter = Offset.Unspecified
    awaitEachGesture {
      val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      val startedAt = first.uptimeMillis
      var sawTwoPointers = false
      var invalid = false
      var fullscreenTriggered = false
      var contactClaimed = false
      var initialSpan: Float? = null
      var twoFingerCenter = Offset.Zero
      var eventTime = startedAt
      var maxMovement = 0f
      val starts = mutableMapOf<androidx.compose.ui.input.pointer.PointerId, Offset>()
      val safeInsetPx = edgeInset.toPx()
      try {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          eventTime = event.changes.maxOfOrNull { it.uptimeMillis } ?: eventTime
          val active = event.changes.filter { it.pressed }
          if (active.size > 2) invalid = true
          if (active.size >= 2 && !contactClaimed) {
            contactClaimed = true
            onTwoFingerContactChanged(true)
          }
          if (active.size == 2) {
            sawTwoPointers = true
            active.forEach { change ->
              val origin = starts.getOrPut(change.id) { change.position }
              maxMovement =
                kotlin.math.max(maxMovement, (change.position - origin).getDistance())
            }
            twoFingerCenter = (active[0].position + active[1].position) / 2f
            val span = (active[0].position - active[1].position).getDistance()
            val originSpan = initialSpan ?: span.also { initialSpan = it }
            if (active.any { it.position.x !in safeInsetPx..(size.width - safeInsetPx) }) {
              invalid = true
            }
            if (enabled && !invalid && !fullscreenTriggered) {
              val target =
                fullscreenTargetForSpan(
                  isFullscreen = isFullscreen(),
                  initialSpan = originSpan,
                  currentSpan = span,
                  minimumMovementPx =
                    kotlin.math.max(viewConfiguration.touchSlop * .75f, 10.dp.toPx()),
                )
              if (target != null) {
                fullscreenTriggered = true
                onFullscreenChanged(target)
              }
            }
          }
          if (sawTwoPointers) event.changes.forEach { it.consume() }
          if (active.isEmpty()) break
        }
      } finally {
        if (contactClaimed) onTwoFingerContactChanged(false)
      }
      val wasTap =
        enabled &&
          sawTwoPointers &&
          !invalid &&
          !fullscreenTriggered &&
          eventTime - startedAt <= TWO_FINGER_TAP_TIMEOUT_MS &&
          maxMovement <= viewConfiguration.touchSlop * 1.6f
      if (wasTap) {
        val closeToPrevious =
          previousTapCenter != Offset.Unspecified &&
            (twoFingerCenter - previousTapCenter).getDistance() <= 56.dp.toPx()
        if (eventTime - previousTapAt in 60L..TWO_FINGER_DOUBLE_TAP_TIMEOUT_MS && closeToPrevious) {
          onSeekBy(if (twoFingerCenter.x >= size.width / 2f) 5_000L else -5_000L)
          previousTapAt = 0L
          previousTapCenter = Offset.Unspecified
        } else {
          previousTapAt = eventTime
          previousTapCenter = twoFingerCenter
        }
      } else if (fullscreenTriggered) {
        previousTapAt = 0L
        previousTapCenter = Offset.Unspecified
      }
    }
  }

private const val PINCH_SPAN_FRACTION = .035f
private const val TWO_FINGER_TAP_TIMEOUT_MS = 280L
private const val TWO_FINGER_DOUBLE_TAP_TIMEOUT_MS = 360L
internal const val GESTURE_INDICATOR_FADE_IN_MS = 120
internal const val GESTURE_INDICATOR_FADE_OUT_MS = 180

@Composable
internal fun FullscreenInfoPanel(
  item: FeedItem,
  description: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .fillMaxHeight(.48f)
        .background(
          Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .96f)))
        )
        .clickable(onClick = onDismiss)
        .padding(start = 42.dp, end = 42.dp, bottom = 34.dp, top = 64.dp),
    contentAlignment = Alignment.BottomStart,
  ) {
    Column(Modifier.fillMaxWidth(.72f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        item.title,
        color = Color.White,
        style = MaterialTheme.typography.headlineMedium,
        maxLines = 2,
      )
      Text(
        "${item.uploader.orEmpty()}  ·  ${item.playCount.orEmpty()} 播放  ·  ${item.duration.orEmpty()}",
        color = Color.White.copy(alpha = .72f),
        style = MaterialTheme.typography.bodyMedium,
      )
      BiliRichText(
        text = description.ifBlank { "这个视频暂时没有填写简介  ( ´ ▽ ` )ﾉ" },
        emotes = emptyMap(),
        style = MaterialTheme.typography.bodyLarge.copy(color = Color.White.copy(alpha = .88f)),
        maxLines = 4,
      )
      Text(
        "向下滑动或轻触信息区域收起",
        color = Color.White.copy(alpha = .52f),
        style = MaterialTheme.typography.labelMedium,
      )
    }
  }
}

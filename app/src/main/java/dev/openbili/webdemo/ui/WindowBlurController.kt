package dev.openbili.webdemo.ui

import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import java.util.function.Consumer
import kotlin.math.roundToInt

/**
 * Applies Android 12 cross-window blur to a dialog-like [Window] when the system permits it.
 *
 * Call [attach] after the window is available and [close] when it is dismissed. The listener is
 * removed and the window reference is cleared on close, so an Activity cannot be retained by the
 * WindowManager service.
 */
@RequiresApi(31)
class WindowBlurController(
  window: Window,
  backgroundBlurRadiusPx: Int,
  blurBehindRadiusPx: Int,
  private val onBlurAvailabilityChanged: (Boolean) -> Unit = {},
) : AutoCloseable {
  private var window: Window? = window
  private var windowManager: WindowManager? =
    window.context.getSystemService(WindowManager::class.java)
  private val backgroundBlurRadiusPx = backgroundBlurRadiusPx.coerceAtLeast(0)
  private val blurBehindRadiusPx = blurBehindRadiusPx.coerceAtLeast(0)
  private var attached = false
  private val blurListener = Consumer<Boolean> { enabled -> applyAvailability(enabled) }

  fun attach() {
    if (attached) return
    val manager = windowManager ?: return
    attached = true
    manager.addCrossWindowBlurEnabledListener(blurListener)
    applyAvailability(manager.isCrossWindowBlurEnabled)
  }

  private fun applyAvailability(enabled: Boolean) {
    val targetWindow = window ?: return
    val blurEnabled = enabled
    targetWindow.setBackgroundBlurRadius(if (blurEnabled) backgroundBlurRadiusPx else 0)
    val attributes = targetWindow.attributes
    if (blurEnabled && blurBehindRadiusPx > 0) {
      attributes.flags = attributes.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
      attributes.blurBehindRadius = blurBehindRadiusPx
    } else {
      attributes.flags = attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
      attributes.blurBehindRadius = 0
    }
    targetWindow.attributes = attributes
    onBlurAvailabilityChanged(blurEnabled)
  }

  override fun close() {
    if (attached) windowManager?.removeCrossWindowBlurEnabledListener(blurListener)
    applyAvailability(false)
    attached = false
    window = null
    windowManager = null
  }

  companion object {
    fun forDialog(
      window: Window,
      density: Float,
      onBlurAvailabilityChanged: (Boolean) -> Unit = {},
    ): WindowBlurController =
      WindowBlurController(
        window = window,
        backgroundBlurRadiusPx = (GlassTokens.DialogBackgroundBlur.value * density).roundToInt(),
        blurBehindRadiusPx = (GlassTokens.DialogBlurBehind.value * density).roundToInt(),
        onBlurAvailabilityChanged = onBlurAvailabilityChanged,
      )
  }
}

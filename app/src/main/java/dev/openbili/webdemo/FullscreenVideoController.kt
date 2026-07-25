package dev.openbili.webdemo

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class FullscreenVideoController(
  private val activity: Activity,
  private val normalContent: View,
  private val onChanged: (Boolean) -> Unit,
  private val normalDecorFitsSystemWindows: Boolean = false,
) : BiliWebChromeClient.FullscreenDelegate {
  private var customView: View? = null
  private var fullscreenContainer: FrameLayout? = null
  private var callback: WebChromeClient.CustomViewCallback? = null
  private var snapshot: WindowSnapshot? = null
  private var orientationWasChanged = false
  private var orientationResetRunnable: Runnable? = null
  private var temporaryRestoreOrientation: Int? = null

  val isFullscreen: Boolean
    get() = customView != null

  override fun show(view: View, callback: WebChromeClient.CustomViewCallback) {
    enter(view, null, callback)
  }

  override fun show(
    view: View,
    requestedOrientation: Int,
    callback: WebChromeClient.CustomViewCallback,
  ) {
    enter(view, requestedOrientation, callback)
  }

  override fun hide() {
    exit()
  }

  fun reapplyImmersiveMode() {
    if (!isFullscreen) return
    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      hide(WindowInsetsCompat.Type.systemBars())
    }
  }

  fun onWindowFocusChanged(hasFocus: Boolean) {
    if (hasFocus) reapplyImmersiveMode()
  }

  fun exit(): Boolean {
    val exitingView = customView ?: return false
    val exitingContainer = fullscreenContainer
    val exitingCallback = callback
    val previous = snapshot

    customView = null
    fullscreenContainer = null
    callback = null
    snapshot = null

    (exitingView.parent as? ViewGroup)?.removeView(exitingView)
    (exitingContainer?.parent as? ViewGroup)?.removeView(exitingContainer)
    normalContent.visibility = previous?.normalContentVisibility ?: View.VISIBLE
    restoreWindow(previous)
    restoreOrientation(previous)
    runCatching { exitingCallback?.onCustomViewHidden() }
    onChanged(false)
    return true
  }

  private fun enter(
    view: View,
    requestedOrientation: Int?,
    customViewCallback: WebChromeClient.CustomViewCallback,
  ) {
    if (isFullscreen || view.parent != null) {
      runCatching { customViewCallback.onCustomViewHidden() }
      return
    }
    cancelPendingOrientationReset()

    val previous = captureWindowSnapshot()
    val root = activity.findViewById<ViewGroup>(android.R.id.content)
    val container =
      FrameLayout(activity).apply {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
      }
    view.setBackgroundColor(Color.BLACK)

    try {
      container.addView(
        view,
        FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
        ),
      )
      root.addView(
        container,
        FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
        ),
      )
    } catch (_: RuntimeException) {
      (view.parent as? ViewGroup)?.removeView(view)
      (container.parent as? ViewGroup)?.removeView(container)
      runCatching { customViewCallback.onCustomViewHidden() }
      return
    }

    snapshot = previous
    customView = view
    fullscreenContainer = container
    callback = customViewCallback
    normalContent.visibility = View.GONE
    if (!previous.keepScreenOn) {
      activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    reapplyImmersiveMode()
    orientationWasChanged = requestUsefulOrientation(view, requestedOrientation)
    container.requestFocus()
    container.post(::reapplyImmersiveMode)
    onChanged(true)
  }

  private fun captureWindowSnapshot(): WindowSnapshot {
    val decorView = activity.window.decorView
    val insets = ViewCompat.getRootWindowInsets(decorView)
    val controller = WindowInsetsControllerCompat(activity.window, decorView)
    return WindowSnapshot(
      requestedOrientation = activity.requestedOrientation,
      configurationOrientation = activity.resources.configuration.orientation,
      normalContentVisibility = normalContent.visibility,
      keepScreenOn =
        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0,
      statusBarsVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true,
      navigationBarsVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true,
      systemBarsBehavior = controller.systemBarsBehavior,
      lightStatusBars = controller.isAppearanceLightStatusBars,
      lightNavigationBars = controller.isAppearanceLightNavigationBars,
    )
  }

  private fun restoreWindow(previous: WindowSnapshot?) {
    if (previous == null) return
    if (previous.keepScreenOn) {
      activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
      activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    WindowCompat.setDecorFitsSystemWindows(activity.window, normalDecorFitsSystemWindows)
    val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
    controller.systemBarsBehavior = previous.systemBarsBehavior
    controller.isAppearanceLightStatusBars = previous.lightStatusBars
    controller.isAppearanceLightNavigationBars = previous.lightNavigationBars
    if (previous.statusBarsVisible) {
      controller.show(WindowInsetsCompat.Type.statusBars())
    } else {
      controller.hide(WindowInsetsCompat.Type.statusBars())
    }
    if (previous.navigationBarsVisible) {
      controller.show(WindowInsetsCompat.Type.navigationBars())
    } else {
      controller.hide(WindowInsetsCompat.Type.navigationBars())
    }
  }

  private fun requestUsefulOrientation(view: View, requestedOrientation: Int?): Boolean {
    val target =
      requestedOrientation?.takeIf(::isSpecificOrientation)
        ?: orientationFromIntrinsicSize(view)
        ?: return false
    val current = activity.resources.configuration.orientation
    if (isLandscapeOrientation(target) && current == Configuration.ORIENTATION_LANDSCAPE)
      return false
    if (isPortraitOrientation(target) && current == Configuration.ORIENTATION_PORTRAIT) return false
    return runCatching {
        activity.requestedOrientation = target
        true
      }
      .getOrDefault(false)
  }

  private fun restoreOrientation(previous: WindowSnapshot?) {
    if (previous == null) return
    if (!orientationWasChanged) {
      activity.requestedOrientation = previous.requestedOrientation
      return
    }
    orientationWasChanged = false

    if (
      previous.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED ||
        activity.isFinishing ||
        activity.isDestroyed
    ) {
      activity.requestedOrientation = previous.requestedOrientation
      return
    }

    val temporary =
      when (previous.configurationOrientation) {
        Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      }
    activity.requestedOrientation = temporary
    if (temporary == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return

    temporaryRestoreOrientation = temporary
    val reset = Runnable {
      orientationResetRunnable = null
      if (!isFullscreen && activity.requestedOrientation == temporaryRestoreOrientation) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      }
      temporaryRestoreOrientation = null
    }
    orientationResetRunnable = reset
    activity.window.decorView.postDelayed(reset, ORIENTATION_RESTORE_DELAY_MILLIS)
  }

  private fun cancelPendingOrientationReset() {
    orientationResetRunnable?.let(activity.window.decorView::removeCallbacks)
    if (
      temporaryRestoreOrientation != null &&
        activity.requestedOrientation == temporaryRestoreOrientation
    ) {
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    orientationResetRunnable = null
    temporaryRestoreOrientation = null
  }

  private fun orientationFromIntrinsicSize(view: View): Int? {
    val width = view.measuredWidth.takeIf { it > 0 } ?: view.layoutParams?.width?.takeIf { it > 0 }
    val height =
      view.measuredHeight.takeIf { it > 0 } ?: view.layoutParams?.height?.takeIf { it > 0 }
    if (width == null || height == null) return null
    return when {
      width > height * LANDSCAPE_ASPECT_THRESHOLD ->
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      height > width * LANDSCAPE_ASPECT_THRESHOLD -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
      else -> null
    }
  }

  private fun isSpecificOrientation(orientation: Int): Boolean =
    isLandscapeOrientation(orientation) || isPortraitOrientation(orientation)

  private fun isLandscapeOrientation(orientation: Int): Boolean =
    orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
      orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
      orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE ||
      orientation == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

  private fun isPortraitOrientation(orientation: Int): Boolean =
    orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
      orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT ||
      orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT ||
      orientation == ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT

  private data class WindowSnapshot(
    val requestedOrientation: Int,
    val configurationOrientation: Int,
    val normalContentVisibility: Int,
    val keepScreenOn: Boolean,
    val statusBarsVisible: Boolean,
    val navigationBarsVisible: Boolean,
    val systemBarsBehavior: Int,
    val lightStatusBars: Boolean,
    val lightNavigationBars: Boolean,
  )

  private companion object {
    const val LANDSCAPE_ASPECT_THRESHOLD = 1.25f
    const val ORIENTATION_RESTORE_DELAY_MILLIS = 750L
  }
}

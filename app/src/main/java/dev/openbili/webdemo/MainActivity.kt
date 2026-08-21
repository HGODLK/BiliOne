package dev.openbili.webdemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import dev.openbili.webdemo.api.BiliHttpClient
import dev.openbili.webdemo.bangumi.BangumiRecommendationViewModel
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.feed.LocalLimitImageLoadingSpeed
import dev.openbili.webdemo.my.MyViewModel
import dev.openbili.webdemo.search.SearchViewModel
import dev.openbili.webdemo.settings.AppSettingsViewModel
import dev.openbili.webdemo.settings.ThemeMode
import dev.openbili.webdemo.ui.AppRoot
import dev.openbili.webdemo.ui.AppInputKind
import dev.openbili.webdemo.ui.AppInputMode
import dev.openbili.webdemo.ui.BiliDemoTheme
import dev.openbili.webdemo.ui.ControllerModeTouchDialog
import dev.openbili.webdemo.ui.LocalColorfulCardsEnabled
import dev.openbili.webdemo.ui.LocalGlassEffectsEnabled
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.LocalControlFocusVisible
import dev.openbili.webdemo.ui.WebLinkHost
import dev.openbili.webdemo.ui.controlDpadKeyCodeForAxis
import dev.openbili.webdemo.ui.isControlNavigationInput
import dev.openbili.webdemo.ui.isMeaningfulControllerMotion
import dev.openbili.webdemo.ui.resolveAppInputMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal const val ROOT_BACK_EXIT_WINDOW_MS = 2_000L

internal fun shouldExitOnRootBack(previousPressAt: Long?, currentPressAt: Long): Boolean =
  previousPressAt != null &&
    currentPressAt >= previousPressAt &&
    currentPressAt - previousPressAt <= ROOT_BACK_EXIT_WINDOW_MS

private data class ControllerHatState(
  var horizontalKeyCode: Int? = null,
  var verticalKeyCode: Int? = null,
)

class MainActivity : ComponentActivity() {
  private val mainViewModel: MainViewModel by viewModels()
  private val feedViewModel: FeedViewModel by viewModels()
  private val authViewModel: AuthViewModel by viewModels()
  private val playerViewModel: PlayerViewModel by
    lazy(LazyThreadSafetyMode.NONE) {
      (application as BiliApplication).sharedPlayerViewModel
    }
  private val myViewModel: MyViewModel by viewModels()
  private val profileMessageViewModel: MyViewModel by lazy {
    ViewModelProvider(this).get("profile-private-messages", MyViewModel::class.java)
  }
  private val searchViewModel: SearchViewModel by viewModels()
  private val settingsViewModel: AppSettingsViewModel by viewModels()
  private val bangumiRecommendationViewModel: BangumiRecommendationViewModel by viewModels()
  private var darkShellTheme = false
  private var lastRootBackPressAt: Long? = null
  private var rootExitToast: Toast? = null
  private var notificationPermissionRequested = false
  private val inputMode = MutableStateFlow(AppInputMode.UNDECIDED)
  private val controllerTouchDialogVisible = MutableStateFlow(false)
  private var blockedControllerTouchGesture = false
  private val controllerModeSelectionKeyCodes = mutableSetOf<Int>()
  private val controllerHatStates = mutableMapOf<Int, ControllerHatState>()
  private val inputManager by lazy(LazyThreadSafetyMode.NONE) {
    getSystemService(InputManager::class.java)
  }
  private var inputListenerRegistered = false
  private val inputDeviceListener =
    object : InputManager.InputDeviceListener {
      override fun onInputDeviceAdded(deviceId: Int) = Unit

      override fun onInputDeviceRemoved(deviceId: Int) {
        controllerHatStates.remove(deviceId)
      }

      override fun onInputDeviceChanged(deviceId: Int) = Unit
    }
  private val notificationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    inputMode.value =
      consumeForcedInputMode()
        ?: savedInstanceState
          ?.getString(SAVED_INPUT_MODE)
          ?.let { savedMode -> runCatching { AppInputMode.valueOf(savedMode) }.getOrNull() }
        ?: AppInputMode.UNDECIDED

    // 在视图模型发出第一个请求之前恢复 cookie 并安装稳定的 UA。
    // 系统 WebView UA 解析被刻意排除在冷启动路径之外。
    try {
      BiliHttpClient.init(this)
    } catch (e: Exception) {
      Log.e("MainActivity", "BiliHttpClient.init failed", e)
      Toast.makeText(this, "网络初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
    }

    enableEdgeToEdge()
    applyImmersiveShell()
    val activeDisplay =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
      else @Suppress("DEPRECATION") windowManager.defaultDisplay
    activeDisplay
      ?.supportedModes
      ?.maxByOrNull { it.refreshRate }
      ?.let { mode ->
        window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
      }
    val composeView = ComposeView(this)
    setContentView(
      composeView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
      ),
    )

    // ── 返回处理 ──────────────────────────────────────────────────
    onBackPressedDispatcher.addCallback(
      this,
      object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          val state = mainViewModel.state.value
          when (resolveAppBackAction(state.video.isFullscreen, state.isVideoScreen)) {
            BackAction.EXIT_FULLSCREEN -> {
              clearRootExitConfirmation()
              mainViewModel.onFullscreenChanged(false)
            }
            BackAction.RETURN_TO_FEED -> {
              clearRootExitConfirmation()
              mainViewModel.returnToFeed()
            }
            BackAction.FINISH_ACTIVITY -> handleRootExitRequest()
            BackAction.WEB_HISTORY -> Unit
          }
        }
      },
    )

    composeView.setContent {
      val settings by settingsViewModel.state.collectAsState()
      val selectedInputMode by inputMode.collectAsState()
      val showControllerTouchDialog by controllerTouchDialogVisible.collectAsState()
      val controlMode = selectedInputMode == AppInputMode.CONTROLLER
      val systemDarkTheme = isSystemInDarkTheme()
      val darkTheme =
        when (settings.themeMode) {
          ThemeMode.SYSTEM -> systemDarkTheme
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
        }
      SideEffect {
        darkShellTheme = darkTheme
        applyImmersiveShell()
        if (controlMode) {
          window.setSoftInputMode(
            window.attributes.softInputMode or
              WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
          )
        }
      }
      BiliDemoTheme(darkTheme = darkTheme, accent = settings.themeAccent) {
        // 在系统字体被放大的平板上保持应用排版稳定。布局密度仍随设备而定；
        // 只有 sp 换算固定为 1x。
        val deviceDensity = LocalDensity.current
        CompositionLocalProvider(
          LocalDensity provides Density(deviceDensity.density, fontScale = 1f),
          LocalGlassEffectsEnabled provides settings.glassEffects,
          LocalColorfulCardsEnabled provides !settings.disableColorfulCards,
          LocalLimitImageLoadingSpeed provides settings.limitImageLoadingSpeed,
          LocalControlMode provides controlMode,
          LocalControlFocusVisible provides controlMode,
          LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) {
          WebLinkHost {
            AppRoot(
              mainViewModel = mainViewModel,
              feedViewModel = feedViewModel,
              authViewModel = authViewModel,
              playerViewModel = playerViewModel,
              myViewModel = myViewModel,
              profileMessageViewModel = profileMessageViewModel,
              searchViewModel = searchViewModel,
              settingsViewModel = settingsViewModel,
              bangumiRecommendationViewModel = bangumiRecommendationViewModel,
              onSearch = { /* placeholder */ },
              onFeedRefresh = { feedViewModel.refresh() },
              onFeedPullRefresh = feedViewModel::refreshPreserving,
              onExitRequested = ::finish,
            )
          }
          if (showControllerTouchDialog) {
            ControllerModeTouchDialog(
              onContinueWithController = { controllerTouchDialogVisible.value = false },
              onRestartWithTouch = ::restartWithTouchInput,
            )
          }
        }
      }
    }

    // 让 Compose 先提交初始外壳，账号工作再开始更新应用状态。
    lifecycleScope.launch {
      delay(48)
      runCatching { authViewModel.checkLoginStatus() }
        .onFailure { Log.e("MainActivity", "checkLoginStatus failed", it) }
    }
  }

  override fun onDestroy() {
    unregisterInputDeviceListener()
    rootExitToast?.cancel()
    super.onDestroy()
  }

  override fun onStart() {
    super.onStart()
    registerInputDeviceListener()
    requestNotificationPermissionIfNeeded()
  }

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
      return super.dispatchTouchEvent(event)
    }
    if (blockedControllerTouchGesture) {
      if (
        event.actionMasked == MotionEvent.ACTION_UP ||
          event.actionMasked == MotionEvent.ACTION_CANCEL
      ) {
        blockedControllerTouchGesture = false
      }
      return true
    }
    if (event.actionMasked != MotionEvent.ACTION_DOWN) {
      return super.dispatchTouchEvent(event)
    }
    when (inputMode.value) {
      AppInputMode.UNDECIDED -> {
        inputMode.value = resolveAppInputMode(inputMode.value, AppInputKind.TOUCH)
      }
      AppInputMode.TOUCH -> Unit
      AppInputMode.CONTROLLER -> {
        if (controllerTouchDialogVisible.value) return super.dispatchTouchEvent(event)
        controllerTouchDialogVisible.value = true
        // 吞掉唤起提示的整笔手势，避免原始抬起事件误点弹窗按钮。
        blockedControllerTouchGesture = true
        return true
      }
    }
    return super.dispatchTouchEvent(event)
  }

  @SuppressLint("RestrictedApi")
  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (!event.isPhysicalControllerInput()) return super.dispatchKeyEvent(event)

    if (event.keyCode in controllerModeSelectionKeyCodes) {
      if (event.action == KeyEvent.ACTION_UP) {
        controllerModeSelectionKeyCodes.remove(event.keyCode)
      }
      return true
    }
    return when (inputMode.value) {
      AppInputMode.UNDECIDED -> {
        if (event.action == KeyEvent.ACTION_DOWN) {
          inputMode.value = resolveAppInputMode(inputMode.value, AppInputKind.CONTROLLER)
          controllerModeSelectionKeyCodes += event.keyCode
        }
        // 首个按键只负责选定模式，等待焦点树按控制器模式重新组合后再接受导航。
        true
      }
      AppInputMode.TOUCH -> true
      AppInputMode.CONTROLLER -> super.dispatchKeyEvent(event)
    }
  }

  override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
    if (event.actionMasked != MotionEvent.ACTION_MOVE || !event.isPhysicalControllerInput()) {
      return super.dispatchGenericMotionEvent(event)
    }
    when (inputMode.value) {
      AppInputMode.UNDECIDED -> {
        if (event.hasMeaningfulControllerDirection()) {
          inputMode.value = resolveAppInputMode(inputMode.value, AppInputKind.CONTROLLER)
          return true
        }
      }
      AppInputMode.TOUCH -> return true
      AppInputMode.CONTROLLER -> if (dispatchControllerHatMotion(event)) return true
    }
    return super.dispatchGenericMotionEvent(event)
  }

  /**
   * Xbox 风格手柄的十字键上报 HAT_X/HAT_Y 位移而不是 KEYCODE_DPAD_* 事件。把每个物理
   * 方向变化转换成普通电视遥控器使用的同款按键事件，让所有 Compose 焦点导航都走
   * 一条与设备无关的路径。
   */
  private fun dispatchControllerHatMotion(event: MotionEvent): Boolean {
    val state = controllerHatStates.getOrPut(event.deviceId) { ControllerHatState() }
    val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
    val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
    val horizontalKeyCode =
      controlDpadKeyCodeForAxis(
        axisValue = if (hatX != 0f) hatX else event.getAxisValue(MotionEvent.AXIS_X),
        negativeKeyCode = KeyEvent.KEYCODE_DPAD_LEFT,
        positiveKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
      )
    val verticalKeyCode =
      controlDpadKeyCodeForAxis(
        axisValue = if (hatY != 0f) hatY else event.getAxisValue(MotionEvent.AXIS_Y),
        negativeKeyCode = KeyEvent.KEYCODE_DPAD_UP,
        positiveKeyCode = KeyEvent.KEYCODE_DPAD_DOWN,
      )
    val hadDirection = state.horizontalKeyCode != null || state.verticalKeyCode != null
    val hasDirection = horizontalKeyCode != null || verticalKeyCode != null

    dispatchControllerDirectionChange(event, state.horizontalKeyCode, horizontalKeyCode)
    dispatchControllerDirectionChange(event, state.verticalKeyCode, verticalKeyCode)
    state.horizontalKeyCode = horizontalKeyCode
    state.verticalKeyCode = verticalKeyCode

    if (!hadDirection && !hasDirection) {
      controllerHatStates.remove(event.deviceId)
      return false
    }
    return true
  }

  private fun dispatchControllerDirectionChange(
    motionEvent: MotionEvent,
    previousKeyCode: Int?,
    currentKeyCode: Int?,
  ) {
    if (previousKeyCode == currentKeyCode) return
    previousKeyCode?.let { dispatchKeyEvent(motionEvent.asDpadKeyEvent(KeyEvent.ACTION_UP, it)) }
    currentKeyCode?.let { dispatchKeyEvent(motionEvent.asDpadKeyEvent(KeyEvent.ACTION_DOWN, it)) }
  }

  private fun MotionEvent.asDpadKeyEvent(action: Int, keyCode: Int): KeyEvent =
    KeyEvent(
      eventTime,
      eventTime,
      action,
      keyCode,
      0,
      metaState,
      deviceId,
      0,
      KeyEvent.FLAG_FROM_SYSTEM,
      InputDevice.SOURCE_DPAD,
    )

  private fun KeyEvent.isPhysicalControllerInput(): Boolean =
    inputManager.getInputDevice(deviceId)?.isControlNavigationInput() == true

  private fun MotionEvent.isPhysicalControllerInput(): Boolean =
    inputManager.getInputDevice(deviceId)?.isControlNavigationInput() == true

  private fun MotionEvent.hasMeaningfulControllerDirection(): Boolean =
    isMeaningfulControllerMotion(
      getAxisValue(MotionEvent.AXIS_HAT_X),
      getAxisValue(MotionEvent.AXIS_HAT_Y),
      getAxisValue(MotionEvent.AXIS_X),
      getAxisValue(MotionEvent.AXIS_Y),
      getAxisValue(MotionEvent.AXIS_Z),
      getAxisValue(MotionEvent.AXIS_RZ),
    )

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putString(SAVED_INPUT_MODE, inputMode.value.name)
    super.onSaveInstanceState(outState)
  }

  override fun onStop() {
    unregisterInputDeviceListener()
    super.onStop()
  }

  private fun registerInputDeviceListener() {
    if (inputListenerRegistered) return
    inputManager.registerInputDeviceListener(inputDeviceListener, null)
    inputListenerRegistered = true
  }

  private fun unregisterInputDeviceListener() {
    if (!inputListenerRegistered) return
    inputManager.unregisterInputDeviceListener(inputDeviceListener)
    inputListenerRegistered = false
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus && !mainViewModel.state.value.video.isFullscreen) applyImmersiveShell()
  }

  private fun applyImmersiveShell() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }
    WindowInsetsControllerCompat(window, window.decorView).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      isAppearanceLightNavigationBars = !darkShellTheme
      hide(WindowInsetsCompat.Type.systemBars())
    }
  }

  private fun requestNotificationPermissionIfNeeded() {
    if (
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        notificationPermissionRequested ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    notificationPermissionRequested = true
    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun handleRootExitRequest() {
    val now = SystemClock.elapsedRealtime()
    if (shouldExitOnRootBack(lastRootBackPressAt, now)) {
      rootExitToast?.cancel()
      rootExitToast = null
      lastRootBackPressAt = null
      finish()
      return
    }
    lastRootBackPressAt = now
    rootExitToast?.cancel()
    rootExitToast = Toast.makeText(this, "再返回一次退出应用~", Toast.LENGTH_SHORT).also(Toast::show)
  }

  private fun clearRootExitConfirmation() {
    lastRootBackPressAt = null
    rootExitToast?.cancel()
    rootExitToast = null
  }

  private fun restartWithTouchInput() {
    forcedInputModeForNextCreation = AppInputMode.TOUCH
    controllerTouchDialogVisible.value = false
    // 这是用户明确选择的“重启”：清空当前任务栈和 Activity/ViewModel 页面状态，
    // 让新的根页面从首页开始，同时只为这次创建强制使用触屏模式。
    startActivity(
      Intent(this, MainActivity::class.java).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
      )
    )
  }

  companion object {
    private const val SAVED_INPUT_MODE = "main_activity_input_mode"

    @Volatile private var forcedInputModeForNextCreation: AppInputMode? = null

    private fun consumeForcedInputMode(): AppInputMode? =
      forcedInputModeForNextCreation.also { forcedInputModeForNextCreation = null }
  }
}

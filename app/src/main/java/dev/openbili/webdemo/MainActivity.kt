package dev.openbili.webdemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import dev.openbili.webdemo.api.BiliHttpClient
import dev.openbili.webdemo.bangumi.BangumiRecommendationViewModel
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.feed.LocalLimitImageLoadingSpeed
import dev.openbili.webdemo.my.MyViewModel
import dev.openbili.webdemo.search.SearchViewModel
import dev.openbili.webdemo.settings.AppSettingsViewModel
import dev.openbili.webdemo.settings.ThemeMode
import dev.openbili.webdemo.ui.AppRoot
import dev.openbili.webdemo.ui.BiliDemoTheme
import dev.openbili.webdemo.ui.LocalGlassEffectsEnabled
import dev.openbili.webdemo.ui.WebLinkHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val ROOT_BACK_EXIT_WINDOW_MS = 2_000L

internal fun shouldExitOnRootBack(previousPressAt: Long?, currentPressAt: Long): Boolean =
  previousPressAt != null &&
    currentPressAt >= previousPressAt &&
    currentPressAt - previousPressAt <= ROOT_BACK_EXIT_WINDOW_MS

class MainActivity : ComponentActivity() {
  private val mainViewModel: MainViewModel by viewModels()
  private val feedViewModel: FeedViewModel by viewModels()
  private val authViewModel: AuthViewModel by viewModels()
  private val playerViewModel: PlayerViewModel by lazy(LazyThreadSafetyMode.NONE) {
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
  private val notificationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Restore cookies and install a stable UA before view models issue their first request.
    // System WebView UA resolution is deliberately kept off the cold-start path.
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

    // ── Back handler ──────────────────────────────────────────────────
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
      }
      BiliDemoTheme(darkTheme = darkTheme, accent = settings.themeAccent) {
        // Keep the app's typography stable on tablets whose system font is enlarged.
        // Layout density remains device-specific; only sp conversion is fixed to 1x.
        val deviceDensity = LocalDensity.current
        CompositionLocalProvider(
          LocalDensity provides Density(deviceDensity.density, fontScale = 1f),
          LocalGlassEffectsEnabled provides settings.glassEffects,
          LocalLimitImageLoadingSpeed provides settings.limitImageLoadingSpeed,
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
            )
          }
        }
      }
    }

    // Let Compose submit the initial shell before account work starts updating app state.
    lifecycleScope.launch {
      delay(48)
      runCatching { authViewModel.checkLoginStatus() }
        .onFailure { Log.e("MainActivity", "checkLoginStatus failed", it) }
    }
  }

  override fun onDestroy() {
    rootExitToast?.cancel()
    super.onDestroy()
  }

  override fun onStart() {
    super.onStart()
    requestNotificationPermissionIfNeeded()
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
}

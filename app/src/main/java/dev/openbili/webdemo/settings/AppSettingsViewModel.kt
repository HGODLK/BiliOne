package dev.openbili.webdemo.settings

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import dev.openbili.webdemo.api.DANMAKU_COLORFUL_NONE
import dev.openbili.webdemo.api.DANMAKU_COLORFUL_VIP_GRADIENT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val title: String, val description: String) {
  SYSTEM("跟随系统", "随平板的浅色或深色外观自动切换"),
  LIGHT("浅色", "始终使用明亮、清爽的浅色主题"),
  DARK("深色", "始终使用低亮度的深色主题"),
}

enum class ThemeAccent(val title: String, val description: String) {
  CYAN("青色", "BiliOne 默认的清爽青色"),
  BILI_PINK("Bili 粉", "更接近哔哩哔哩的活力粉色"),
  BLUE("蓝色", "沉静、清晰的经典蓝色"),
  PURPLE("紫色", "柔和而醒目的紫色"),
  GREEN("绿色", "自然、低刺激的绿色"),
}

enum class PreferredResolutionMode(val title: String, val description: String) {
  EXTREME("极高", "设备撑得住就选最高画质，细节拉满 (✧ω✧)"),
  ULTRA_HIGH("超高", "优先 1080P+ / 1080P60，清晰又顺滑 (๑•̀ㅂ•́)و"),
  HIGH("高", "优先 1080P，日常观看刚刚好 (｡•̀ᴗ-)✧"),
  MEDIUM("一般", "优先 720P，更省流量也更轻快 (～￣▽￣)～"),
  LOW("低", "优先 480P，网络紧张时稳稳看 ( •̀ ω •́ )"),
}

enum class AdvancedAudioPriority(val title: String, val description: String) {
  DOLBY("优先 Dolby", "优先杜比全景声；不可用时自动改用 HiRes"),
  HI_RES("优先 HiRes", "优先 HiRes 无损；不可用时自动改用 Dolby"),
}

internal fun canSelectPreferredResolution(
  mode: PreferredResolutionMode,
  vipActive: Boolean,
): Boolean =
  vipActive ||
    (mode != PreferredResolutionMode.EXTREME && mode != PreferredResolutionMode.ULTRA_HIGH)

data class AppSettings(
  val keepScreenOn: Boolean = true,
  val brightnessGesture: Boolean = true,
  val volumeGesture: Boolean = true,
  val horizontalSeekGesture: Boolean = true,
  val twoFingerFullscreenGesture: Boolean = true,
  val twoFingerSeekGesture: Boolean = true,
  val fullscreenInfoGesture: Boolean = true,
  val reduceMotion: Boolean = false,
  val glassEffects: Boolean = true,
  /** Brightness of the cover-derived letterbox around fullscreen on-demand video. */
  val fullscreenBackgroundBrightness: Float = 1f,
  val showCommentLocation: Boolean = true,
  val showCommentEmotes: Boolean = true,
  val controlsTimeoutSeconds: Int = 3,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val themeAccent: ThemeAccent = ThemeAccent.CYAN,
  val preferredResolutionMode: PreferredResolutionMode = PreferredResolutionMode.HIGH,
  val cellularPreferredResolutionMode: PreferredResolutionMode = PreferredResolutionMode.MEDIUM,
  val autoPlayNext: Boolean = true,
  val autoNextCountdownSeconds: Int = 5,
  val defaultShowDanmaku: Boolean = true,
  val danmakuColor: Int = 0xFFFFFF,
  val danmakuColorful: Int = DANMAKU_COLORFUL_NONE,
  val danmakuSmartBlocking: Boolean = true,
  val danmakuDisplayArea: Float = .75f,
  val danmakuDensity: Int = 3,
  val danmakuBlockLevel: Int = 1,
  val danmakuOpacity: Float = .72f,
  val danmakuFontScale: Float = 1f,
  val danmakuSpeed: Float = 1f,
  /** Live-room danmaku settings are intentionally independent from on-demand video settings. */
  val liveShowDanmaku: Boolean = true,
  val liveDanmakuDisplayArea: Float = .75f,
  val liveDanmakuOpacity: Float = .72f,
  val liveDanmakuFontScale: Float = 1f,
  val liveDanmakuSpeed: Float = 1f,
  val unlockDolbyVision: Boolean = false,
  val unlockDolbyAtmos: Boolean = false,
  val advancedAudioEnabled: Boolean = false,
  val advancedAudioPriority: AdvancedAudioPriority = AdvancedAudioPriority.DOLBY,
)

class AppSettingsViewModel(application: Application) : AndroidViewModel(application) {
  private val prefs = application.getSharedPreferences("app_settings", 0)
  private val _state = MutableStateFlow(read())
  val state: StateFlow<AppSettings> = _state.asStateFlow()

  fun update(transform: (AppSettings) -> AppSettings) {
    val value = transform(_state.value)
    _state.value = value
    prefs
      .edit()
      .putBoolean("keep_screen_on", value.keepScreenOn)
      .putBoolean("brightness_gesture", value.brightnessGesture)
      .putBoolean("volume_gesture", value.volumeGesture)
      .putBoolean("horizontal_seek_gesture", value.horizontalSeekGesture)
      .putBoolean("two_finger_fullscreen_gesture", value.twoFingerFullscreenGesture)
      .putBoolean("two_finger_seek_gesture", value.twoFingerSeekGesture)
      .putBoolean("fullscreen_info_gesture", value.fullscreenInfoGesture)
      .putBoolean("reduce_motion", value.reduceMotion)
      .putBoolean("glass_effects", value.glassEffects)
      .putFloat(
        "fullscreen_background_brightness",
        value.fullscreenBackgroundBrightness.coerceIn(0f, 1f),
      )
      .putBoolean("comment_location", value.showCommentLocation)
      .putBoolean("comment_emotes", value.showCommentEmotes)
      .putInt("controls_timeout", value.controlsTimeoutSeconds)
      .putString("theme_mode", value.themeMode.name)
      .putString("theme_accent", value.themeAccent.name)
      // Keep the legacy key current so downgrading the app does not unexpectedly enable light mode.
      .putBoolean("force_dark_mode", value.themeMode == ThemeMode.DARK)
      .putString("preferred_resolution_mode", value.preferredResolutionMode.name)
      .putString(
        "cellular_preferred_resolution_mode",
        value.cellularPreferredResolutionMode.name,
      )
      .putBoolean("auto_play_next", value.autoPlayNext)
      .putInt("auto_next_countdown_seconds", value.autoNextCountdownSeconds)
      .putBoolean("default_show_danmaku", value.defaultShowDanmaku)
      .putInt("danmaku_color", value.danmakuColor and 0xFFFFFF)
      .putInt(
        "danmaku_colorful",
        if (value.danmakuColorful == DANMAKU_COLORFUL_VIP_GRADIENT) DANMAKU_COLORFUL_VIP_GRADIENT
        else DANMAKU_COLORFUL_NONE,
      )
      .putBoolean("danmaku_smart_blocking", value.danmakuSmartBlocking)
      .putFloat("danmaku_display_area", value.danmakuDisplayArea)
      .putInt("danmaku_density", value.danmakuDensity.coerceIn(1, 5))
      .putInt("danmaku_block_level", value.danmakuBlockLevel.coerceIn(1, 5))
      .putFloat("danmaku_opacity", value.danmakuOpacity)
      .putFloat("danmaku_font_scale", value.danmakuFontScale)
      .putFloat("danmaku_speed", value.danmakuSpeed)
      .putBoolean("live_show_danmaku", value.liveShowDanmaku)
      .putFloat("live_danmaku_display_area", value.liveDanmakuDisplayArea)
      .putFloat("live_danmaku_opacity", value.liveDanmakuOpacity)
      .putFloat("live_danmaku_font_scale", value.liveDanmakuFontScale)
      .putFloat("live_danmaku_speed", value.liveDanmakuSpeed)
      .putBoolean("unlock_dolby_vision", value.unlockDolbyVision)
      .putBoolean("unlock_dolby_atmos", value.unlockDolbyAtmos)
      .putBoolean("advanced_audio_enabled", value.advancedAudioEnabled)
      .putString("advanced_audio_priority", value.advancedAudioPriority.name)
      .apply()
  }

  private fun read() =
    AppSettings(
      keepScreenOn = prefs.getBoolean("keep_screen_on", true),
      brightnessGesture = prefs.getBoolean("brightness_gesture", true),
      volumeGesture = prefs.getBoolean("volume_gesture", true),
      horizontalSeekGesture = prefs.getBoolean("horizontal_seek_gesture", true),
      twoFingerFullscreenGesture = prefs.getBoolean("two_finger_fullscreen_gesture", true),
      twoFingerSeekGesture =
        if (prefs.contains("two_finger_seek_gesture")) {
          prefs.getBoolean("two_finger_seek_gesture", true)
        } else {
          prefs.getBoolean("two_finger_fullscreen_gesture", true)
        },
      fullscreenInfoGesture = prefs.getBoolean("fullscreen_info_gesture", true),
      reduceMotion = prefs.getBoolean("reduce_motion", false),
      glassEffects = prefs.getBoolean("glass_effects", true),
      fullscreenBackgroundBrightness =
        prefs.getFloat("fullscreen_background_brightness", 1f).coerceIn(0f, 1f),
      showCommentLocation = prefs.getBoolean("comment_location", true),
      showCommentEmotes = prefs.getBoolean("comment_emotes", true),
      controlsTimeoutSeconds = prefs.getInt("controls_timeout", 3),
      themeMode =
        runCatching {
            ThemeMode.valueOf(prefs.getString("theme_mode", null).orEmpty())
          }
          .getOrElse {
            if (prefs.getBoolean("force_dark_mode", false)) ThemeMode.DARK else ThemeMode.SYSTEM
          },
      themeAccent =
        runCatching {
            ThemeAccent.valueOf(prefs.getString("theme_accent", null).orEmpty())
          }
          .getOrDefault(ThemeAccent.CYAN),
      preferredResolutionMode =
        runCatching {
            PreferredResolutionMode.valueOf(
              prefs.getString("preferred_resolution_mode", null).orEmpty()
            )
          }
          .getOrDefault(PreferredResolutionMode.HIGH),
      cellularPreferredResolutionMode =
        runCatching {
            PreferredResolutionMode.valueOf(
              prefs.getString("cellular_preferred_resolution_mode", null).orEmpty()
            )
          }
          .getOrDefault(PreferredResolutionMode.MEDIUM),
      autoPlayNext = prefs.getBoolean("auto_play_next", true),
      autoNextCountdownSeconds = prefs.getInt("auto_next_countdown_seconds", 5).coerceIn(3, 10),
      defaultShowDanmaku = prefs.getBoolean("default_show_danmaku", true),
      danmakuFontScale = prefs.getFloat("danmaku_font_scale", 1f).coerceIn(.7f, 1.5f),
      danmakuSpeed = prefs.getFloat("danmaku_speed", 1f).coerceIn(.6f, 1.8f),
      danmakuColor = prefs.getInt("danmaku_color", 0xFFFFFF) and 0xFFFFFF,
      danmakuColorful =
        prefs.getInt("danmaku_colorful", DANMAKU_COLORFUL_NONE).takeIf {
          it == DANMAKU_COLORFUL_VIP_GRADIENT
        } ?: DANMAKU_COLORFUL_NONE,
      danmakuSmartBlocking = prefs.getBoolean("danmaku_smart_blocking", true),
      danmakuDisplayArea = prefs.getFloat("danmaku_display_area", .75f).coerceIn(.25f, 1f),
      danmakuDensity = prefs.getInt("danmaku_density", 3).coerceIn(1, 5),
      danmakuBlockLevel = prefs.getInt("danmaku_block_level", 1).coerceIn(1, 5),
      danmakuOpacity = prefs.getFloat("danmaku_opacity", .72f).coerceIn(.2f, 1f),
      liveShowDanmaku =
        if (prefs.contains("live_show_danmaku")) {
          prefs.getBoolean("live_show_danmaku", true)
        } else {
          prefs.getBoolean("default_show_danmaku", true)
        },
      liveDanmakuDisplayArea =
        prefs
          .getFloat(
            "live_danmaku_display_area",
            prefs.getFloat("danmaku_display_area", .75f),
          )
          .coerceIn(.25f, 1f),
      liveDanmakuOpacity =
        prefs
          .getFloat("live_danmaku_opacity", prefs.getFloat("danmaku_opacity", .72f))
          .coerceIn(.2f, 1f),
      liveDanmakuFontScale =
        prefs
          .getFloat("live_danmaku_font_scale", prefs.getFloat("danmaku_font_scale", 1f))
          .coerceIn(.7f, 1.5f),
      liveDanmakuSpeed =
        prefs
          .getFloat("live_danmaku_speed", prefs.getFloat("danmaku_speed", 1f))
          .coerceIn(.6f, 1.8f),
      unlockDolbyVision = prefs.getBoolean("unlock_dolby_vision", false),
      unlockDolbyAtmos = prefs.getBoolean("unlock_dolby_atmos", false),
      advancedAudioEnabled = prefs.getBoolean("advanced_audio_enabled", false),
      advancedAudioPriority =
        runCatching {
            AdvancedAudioPriority.valueOf(
              prefs.getString("advanced_audio_priority", null).orEmpty()
            )
          }
          .getOrDefault(AdvancedAudioPriority.DOLBY),
    )
}

internal fun AppSettings.preferredResolutionModeFor(context: Context): PreferredResolutionMode {
  val manager =
    context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
      as? ConnectivityManager
  val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
  return if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
    cellularPreferredResolutionMode
  } else {
    preferredResolutionMode
  }
}

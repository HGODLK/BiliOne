package dev.openbili.webdemo.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.openbili.webdemo.api.DANMAKU_COLORFUL_NONE
import dev.openbili.webdemo.api.DANMAKU_COLORFUL_VIP_GRADIENT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
  val fullscreenInfoGesture: Boolean = true,
  val reduceMotion: Boolean = false,
  val glassEffects: Boolean = true,
  val showCommentLocation: Boolean = true,
  val showCommentEmotes: Boolean = true,
  val initialFeedCount: Int = 30,
  val coverPrefetchScreens: Int = 2,
  val controlsTimeoutSeconds: Int = 3,
  val forceDarkMode: Boolean = false,
  val preferredResolutionMode: PreferredResolutionMode = PreferredResolutionMode.HIGH,
  val danmakuColor: Int = 0xFFFFFF,
  val danmakuColorful: Int = DANMAKU_COLORFUL_NONE,
  val danmakuSmartBlocking: Boolean = true,
  val danmakuDisplayArea: Float = .75f,
  val danmakuDensity: Int = 3,
  val danmakuOpacity: Float = .72f,
  val danmakuFontScale: Float = 1f,
  val danmakuSpeed: Float = 1f,
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
      .putBoolean("fullscreen_info_gesture", value.fullscreenInfoGesture)
      .putBoolean("reduce_motion", value.reduceMotion)
      .putBoolean("glass_effects", value.glassEffects)
      .putBoolean("comment_location", value.showCommentLocation)
      .putBoolean("comment_emotes", value.showCommentEmotes)
      .putInt("initial_feed_count", value.initialFeedCount)
      .putInt("cover_prefetch_screens", value.coverPrefetchScreens)
      .putInt("controls_timeout", value.controlsTimeoutSeconds)
      .putBoolean("force_dark_mode", value.forceDarkMode)
      .putString("preferred_resolution_mode", value.preferredResolutionMode.name)
      .putInt("danmaku_color", value.danmakuColor and 0xFFFFFF)
      .putInt(
        "danmaku_colorful",
        if (value.danmakuColorful == DANMAKU_COLORFUL_VIP_GRADIENT) DANMAKU_COLORFUL_VIP_GRADIENT
        else DANMAKU_COLORFUL_NONE,
      )
      .putBoolean("danmaku_smart_blocking", value.danmakuSmartBlocking)
      .putFloat("danmaku_display_area", value.danmakuDisplayArea)
      .putInt("danmaku_density", value.danmakuDensity.coerceIn(1, 5))
      .putFloat("danmaku_opacity", value.danmakuOpacity)
      .putFloat("danmaku_font_scale", value.danmakuFontScale)
      .putFloat("danmaku_speed", value.danmakuSpeed)
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
      fullscreenInfoGesture = prefs.getBoolean("fullscreen_info_gesture", true),
      reduceMotion = prefs.getBoolean("reduce_motion", false),
      glassEffects = prefs.getBoolean("glass_effects", true),
      showCommentLocation = prefs.getBoolean("comment_location", true),
      showCommentEmotes = prefs.getBoolean("comment_emotes", true),
      initialFeedCount = prefs.getInt("initial_feed_count", 30),
      coverPrefetchScreens = prefs.getInt("cover_prefetch_screens", 2),
      controlsTimeoutSeconds = prefs.getInt("controls_timeout", 3),
      forceDarkMode = prefs.getBoolean("force_dark_mode", false),
      preferredResolutionMode =
        runCatching {
            PreferredResolutionMode.valueOf(
              prefs.getString("preferred_resolution_mode", null).orEmpty()
            )
          }
          .getOrDefault(PreferredResolutionMode.HIGH),
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
      danmakuOpacity = prefs.getFloat("danmaku_opacity", .72f).coerceIn(.2f, 1f),
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

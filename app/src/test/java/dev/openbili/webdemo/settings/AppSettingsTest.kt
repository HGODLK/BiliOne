package dev.openbili.webdemo.settings

import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
  @Test
  fun `theme offers system light and dark modes`() {
    assertEquals(listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK), ThemeMode.entries)
  }

  @Test
  fun `theme offers all accent presets`() {
    assertEquals(
      listOf(
        ThemeAccent.CYAN,
        ThemeAccent.BILI_PINK,
        ThemeAccent.BLUE,
        ThemeAccent.PURPLE,
        ThemeAccent.GREEN,
      ),
      ThemeAccent.entries,
    )
  }

  @Test
  fun `two finger fullscreen and seek gestures have independent defaults`() {
    val settings = AppSettings()

    assertTrue(settings.twoFingerFullscreenGesture)
    assertTrue(settings.twoFingerSeekGesture)
  }

  @Test
  fun `video and live danmaku settings are independent`() {
    val videoChanged =
      AppSettings().copy(
        defaultShowDanmaku = false,
        danmakuDisplayArea = .25f,
        danmakuOpacity = .2f,
        danmakuFontScale = .7f,
        danmakuSpeed = .6f,
      )

    assertFalse(videoChanged.defaultShowDanmaku)
    assertTrue(videoChanged.liveShowDanmaku)
    assertEquals(.75f, videoChanged.liveDanmakuDisplayArea)
    assertEquals(.72f, videoChanged.liveDanmakuOpacity)
    assertEquals(1f, videoChanged.liveDanmakuFontScale)
    assertEquals(1f, videoChanged.liveDanmakuSpeed)

    val liveChanged =
      AppSettings().copy(
        liveShowDanmaku = false,
        liveDanmakuDisplayArea = .25f,
        liveDanmakuOpacity = .2f,
        liveDanmakuFontScale = .7f,
        liveDanmakuSpeed = .6f,
      )

    assertTrue(liveChanged.defaultShowDanmaku)
    assertEquals(.75f, liveChanged.danmakuDisplayArea)
    assertEquals(.72f, liveChanged.danmakuOpacity)
    assertEquals(1f, liveChanged.danmakuFontScale)
    assertEquals(1f, liveChanged.danmakuSpeed)
  }

  @Test
  fun `sim availability hides cellular settings only for confirmed absence`() {
    assertEquals(
      SimAvailability.ABSENT,
      classifySimAvailability(hasTelephony = false, simStates = emptyList()),
    )
    assertEquals(
      SimAvailability.ABSENT,
      classifySimAvailability(
        hasTelephony = true,
        simStates = listOf(TelephonyManager.SIM_STATE_ABSENT),
      ),
    )
    assertEquals(
      SimAvailability.UNKNOWN,
      classifySimAvailability(
        hasTelephony = true,
        simStates = listOf(TelephonyManager.SIM_STATE_UNKNOWN),
      ),
    )
    assertEquals(
      SimAvailability.PRESENT,
      classifySimAvailability(
        hasTelephony = true,
        simStates = listOf(
          TelephonyManager.SIM_STATE_ABSENT,
          TelephonyManager.SIM_STATE_READY,
        ),
      ),
    )
  }

  @Test
  fun `cache size formatter uses readable binary units`() {
    assertEquals("0.0 MB", AppCacheManager.formatSize(0))
    assertEquals("1.0 MB", AppCacheManager.formatSize(1024L * 1024L))
    assertEquals("12 MB", AppCacheManager.formatSize(12L * 1024L * 1024L))
    assertEquals("1.5 GB", AppCacheManager.formatSize(1536L * 1024L * 1024L))
  }
}

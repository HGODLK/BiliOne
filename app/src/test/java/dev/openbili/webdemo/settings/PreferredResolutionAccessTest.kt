package dev.openbili.webdemo.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferredResolutionAccessTest {
  @Test
  fun nonVipCannotSelectUltraHighOrExtreme() {
    assertFalse(canSelectPreferredResolution(PreferredResolutionMode.ULTRA_HIGH, vipActive = false))
    assertFalse(canSelectPreferredResolution(PreferredResolutionMode.EXTREME, vipActive = false))
  }

  @Test
  fun nonVipCanSelectOrdinaryResolutions() {
    assertTrue(canSelectPreferredResolution(PreferredResolutionMode.HIGH, vipActive = false))
    assertTrue(canSelectPreferredResolution(PreferredResolutionMode.MEDIUM, vipActive = false))
    assertTrue(canSelectPreferredResolution(PreferredResolutionMode.LOW, vipActive = false))
  }

  @Test
  fun vipCanSelectEveryResolutionMode() {
    PreferredResolutionMode.entries.forEach { mode ->
      assertTrue(canSelectPreferredResolution(mode, vipActive = true))
    }
  }
}

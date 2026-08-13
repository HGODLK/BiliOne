package dev.openbili.webdemo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHubInteractionTest {
  @Test
  fun expandedDynamicLocksHorizontalPagingOnlyWhileDynamicPageIsSelected() {
    assertTrue(
      shouldLockHomeHubPager(
        dynamicPageSelected = true,
        selectedDynamicId = "dynamic-1",
        dynamicOverlayActive = true,
      )
    )
    assertFalse(
      shouldLockHomeHubPager(
        dynamicPageSelected = false,
        selectedDynamicId = "dynamic-1",
        dynamicOverlayActive = true,
      )
    )
    assertFalse(
      shouldLockHomeHubPager(
        dynamicPageSelected = true,
        selectedDynamicId = null,
        dynamicOverlayActive = false,
      )
    )
  }

  @Test
  fun immersiveAndMusicModesLockHomePaging() {
    assertTrue(
      shouldLockHomeHubPager(
        dynamicPageSelected = false,
        selectedDynamicId = null,
        dynamicOverlayActive = false,
        recommendationMode = HomeRecommendationMode.IMMERSIVE,
      )
    )
    assertTrue(
      shouldLockHomeHubPager(
        dynamicPageSelected = false,
        selectedDynamicId = null,
        dynamicOverlayActive = false,
        recommendationMode = HomeRecommendationMode.MUSIC,
      )
    )
  }
}

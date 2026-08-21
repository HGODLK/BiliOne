package dev.openbili.webdemo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

  @Test
  fun touchPagingRemainsAvailableWhenControlNavigationIsActive() {
    assertTrue(
      shouldEnableHomeHubPagerUserScroll(
        horizontalRailTouched = false,
        navigationLocked = false,
        recommendationMode = HomeRecommendationMode.NORMAL,
      )
    )
    assertFalse(
      shouldEnableHomeHubPagerUserScroll(
        horizontalRailTouched = true,
        navigationLocked = false,
        recommendationMode = HomeRecommendationMode.NORMAL,
      )
    )
  }

  @Test
  fun recommendationTabsEnterNearestSecondLevelActionColumn() {
    assertEquals(HomeSecondLevelAction.IMMERSIVE, homeSecondLevelActionBelowTab(0))
    assertEquals(HomeSecondLevelAction.IMMERSIVE, homeSecondLevelActionBelowTab(1))
    assertEquals(HomeSecondLevelAction.SCROLL_TOP, homeSecondLevelActionBelowTab(2))
    assertEquals(HomeSecondLevelAction.SCROLL_TOP, homeSecondLevelActionBelowTab(3))
  }

  @Test
  fun recommendationAndLiveEnterTheirWholeFeedsWhileDynamicAndPopularEnterPageControls() {
    assertEquals(HomeControlLevel.CONTENT, homeTabEntryLevel(HomeHubTab.RECOMMENDATION))
    assertEquals(HomeControlLevel.PAGE_CONTROLS, homeTabEntryLevel(HomeHubTab.DYNAMIC))
    assertEquals(HomeControlLevel.PAGE_CONTROLS, homeTabEntryLevel(HomeHubTab.POPULAR))
    assertEquals(HomeControlLevel.CONTENT, homeTabEntryLevel(HomeHubTab.LIVE))

    assertEquals(HomeControlLevel.TABS, homeContentParentLevel(HomeHubTab.RECOMMENDATION))
    assertEquals(HomeControlLevel.PAGE_CONTROLS, homeContentParentLevel(HomeHubTab.DYNAMIC))
    assertEquals(HomeControlLevel.PAGE_CONTROLS, homeContentParentLevel(HomeHubTab.POPULAR))
    assertEquals(HomeControlLevel.TABS, homeContentParentLevel(HomeHubTab.LIVE))
  }

  @Test
  fun homeBackTargetsFollowEachTabsDeclaredHierarchy() {
    assertEquals(
      HomeControlLevel.TABS,
      homeControlBackTarget(HomeHubTab.RECOMMENDATION, HomeControlLevel.CONTENT),
    )
    assertEquals(
      HomeControlLevel.PAGE_CONTROLS,
      homeControlBackTarget(HomeHubTab.DYNAMIC, HomeControlLevel.CONTENT),
    )
    assertEquals(
      HomeControlLevel.PAGE_CONTROLS,
      homeControlBackTarget(HomeHubTab.POPULAR, HomeControlLevel.CONTENT),
    )
    assertEquals(
      HomeControlLevel.TABS,
      homeControlBackTarget(HomeHubTab.LIVE, HomeControlLevel.CONTENT),
    )
    HomeHubTab.entries.forEach { tab ->
      assertEquals(
        HomeControlLevel.TABS,
        homeControlBackTarget(tab, HomeControlLevel.PAGE_CONTROLS),
      )
      assertEquals(
        HomeControlLevel.ROOT,
        homeControlBackTarget(tab, HomeControlLevel.TABS),
      )
    }
  }

  @Test
  fun exitDialogOnlyBelongsToTheRootHomeLevel() {
    assertTrue(canShowControlHomeExit(HomeControlLevel.ROOT))
    assertFalse(canShowControlHomeExit(HomeControlLevel.TABS))
    assertFalse(canShowControlHomeExit(HomeControlLevel.PAGE_CONTROLS))
    assertFalse(canShowControlHomeExit(HomeControlLevel.CONTENT))
  }
}

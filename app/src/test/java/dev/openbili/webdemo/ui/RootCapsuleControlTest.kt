package dev.openbili.webdemo.ui

import dev.openbili.webdemo.my.MyControlLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCapsuleControlTest {
  @Test
  fun `my enters its sections on the first cross tab confirmation`() {
    assertTrue(shouldEnterRootTabOnControlConfirm(RootTab.HOME, RootTab.MY))
    assertFalse(shouldEnterRootTabOnControlConfirm(RootTab.HOME, RootTab.BANGUMI))
    assertTrue(shouldEnterRootTabOnControlConfirm(RootTab.MY, RootTab.MY))
  }

  @Test
  fun `capsule hides outside root only while controller owns interaction`() {
    assertFalse(
      rootCapsuleVisible(
        controlMode = true,
        controlFocusVisible = true,
        focusEnabled = false,
      )
    )
    assertTrue(
      rootCapsuleVisible(
        controlMode = true,
        controlFocusVisible = false,
        focusEnabled = false,
      )
    )
    assertTrue(
      rootCapsuleVisible(
        controlMode = true,
        controlFocusVisible = true,
        focusEnabled = true,
      )
    )
  }

  @Test
  fun `capsule only joins controller focus tree at the active root level`() {
    assertFalse(
      rootCapsuleFocusEnabled(
        controlMode = true,
        showVideo = true,
        showBangumiIndex = false,
        rootTab = RootTab.BANGUMI,
        homeControlLevel = HomeControlLevel.ROOT,
        bangumiControlLevel = BangumiControlLevel.ROOT,
        myControlLevel = MyControlLevel.ROOT,
      )
    )
    assertFalse(
      rootCapsuleFocusEnabled(
        controlMode = true,
        showVideo = false,
        showBangumiIndex = false,
        rootTab = RootTab.BANGUMI,
        homeControlLevel = HomeControlLevel.ROOT,
        bangumiControlLevel = BangumiControlLevel.HERO,
        myControlLevel = MyControlLevel.ROOT,
      )
    )
    assertTrue(
      rootCapsuleFocusEnabled(
        controlMode = true,
        showVideo = false,
        showBangumiIndex = false,
        rootTab = RootTab.BANGUMI,
        homeControlLevel = HomeControlLevel.ROOT,
        bangumiControlLevel = BangumiControlLevel.ROOT,
        myControlLevel = MyControlLevel.ROOT,
      )
    )
    assertFalse(
      rootCapsuleFocusEnabled(
        controlMode = true,
        showVideo = false,
        showBangumiIndex = false,
        rootTab = RootTab.MY,
        homeControlLevel = HomeControlLevel.ROOT,
        bangumiControlLevel = BangumiControlLevel.ROOT,
        myControlLevel = MyControlLevel.SECTIONS,
      )
    )
  }
}

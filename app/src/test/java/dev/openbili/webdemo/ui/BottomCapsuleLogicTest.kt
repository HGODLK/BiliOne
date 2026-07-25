package dev.openbili.webdemo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomCapsuleLogicTest {
  @Test
  fun `drag position selects nearest root tab`() {
    assertEquals(RootTab.HOME, rootTabForCapsulePosition(0.2f))
    assertEquals(RootTab.BANGUMI, rootTabForCapsulePosition(0.8f))
    assertEquals(RootTab.MY, rootTabForCapsulePosition(1.8f))
  }

  @Test
  fun `drag position maps continuously across pager midpoint`() {
    val homeSide = rootPagerAnchorForCapsulePosition(.4f)
    val bangumiSide = rootPagerAnchorForCapsulePosition(.6f)
    val mySide = rootPagerAnchorForCapsulePosition(1.6f)

    assertEquals(0, homeSide.page)
    assertEquals(.4f, homeSide.offsetFraction, .0001f)
    assertEquals(1, bangumiSide.page)
    assertEquals(-.4f, bangumiSide.offsetFraction, .0001f)
    assertEquals(2, mySide.page)
    assertEquals(-.4f, mySide.offsetFraction, .0001f)
  }

  @Test
  fun `bangumi preview stays inactive throughout root page switching`() {
    assertFalse(
      shouldActivateBangumiRootPage(
        selectedTab = RootTab.BANGUMI,
        settledPage = RootTab.BANGUMI.ordinal,
        pageSwitchInProgress = true,
        videoScreenVisible = false,
      )
    )
    assertTrue(
      shouldActivateBangumiRootPage(
        selectedTab = RootTab.BANGUMI,
        settledPage = RootTab.BANGUMI.ordinal,
        pageSwitchInProgress = false,
        videoScreenVisible = false,
      )
    )
  }
}

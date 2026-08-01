package dev.openbili.webdemo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationBringIntoViewTest {
  @Test
  fun cardRequestIncludesTheSharedBottomNavigationClearance() {
    val rect =
      navigationBringIntoViewRect(
        width = 480,
        height = 300,
        topClearancePx = 0,
        bottomClearancePx = 112,
      )

    assertEquals(0f, rect?.top)
    assertEquals(412f, rect?.bottom)
    assertEquals(480f, rect?.right)
  }

  @Test
  fun optionalTopAndBottomObstructionsAreBothRepresented() {
    val rect =
      navigationBringIntoViewRect(
        width = 320,
        height = 180,
        topClearancePx = 40,
        bottomClearancePx = 96,
      )

    assertEquals(-40f, rect?.top)
    assertEquals(276f, rect?.bottom)
  }

  @Test
  fun unmeasuredTargetFallsBackToThePlatformRequest() {
    assertNull(
      navigationBringIntoViewRect(
        width = 0,
        height = 180,
        topClearancePx = 0,
        bottomClearancePx = 112,
      )
    )
  }
}

package dev.openbili.webdemo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BangumiControllerTest {
  @Test
  fun backMovesFromContentToItsImmediateParent() {
    assertEquals(
      BangumiControlLevel.EXPLORE_NAV,
      bangumiControlBackTarget(BangumiControlLevel.EXPLORE_CONTENT),
    )
    assertEquals(
      BangumiControlLevel.INDEX_CONTROLS,
      bangumiControlBackTarget(BangumiControlLevel.INDEX_CONTENT),
    )
    assertEquals(
      BangumiControlLevel.EXPLORE_CONTENT,
      bangumiControlBackTarget(BangumiControlLevel.EXPLORE_HERO),
    )
  }

  @Test
  fun verticalNavigationKeepsHeroAndIndexBoundariesExplicit() {
    assertEquals(
      BangumiControlLevel.EXPLORE_NAV,
      bangumiControlDownTarget(BangumiControlLevel.HERO),
    )
    assertEquals(
      BangumiControlLevel.INDEX_CONTENT,
      bangumiControlDownTarget(BangumiControlLevel.INDEX_CONTROLS),
    )
    assertEquals(
      BangumiControlLevel.EXPLORE_CONTENT,
      bangumiControlDownTarget(BangumiControlLevel.EXPLORE_NAV),
    )
    assertEquals(
      BangumiControlLevel.HERO,
      bangumiControlUpTarget(BangumiControlLevel.EXPLORE_NAV),
    )
    assertNull(bangumiControlDownTarget(BangumiControlLevel.EXPLORE_HERO))
    assertNull(bangumiControlUpTarget(BangumiControlLevel.EXPLORE_HERO))
    assertNull(bangumiControlUpTarget(BangumiControlLevel.HERO))
  }

  @Test
  fun categoryMovementStopsAtCapsuleEdges() {
    assertEquals(1, bangumiControlMoveCategory(2, -1, 6))
    assertEquals(5, bangumiControlMoveCategory(4, 1, 6))
    assertNull(bangumiControlMoveCategory(0, -1, 6))
    assertNull(bangumiControlMoveCategory(5, 1, 6))
  }
}

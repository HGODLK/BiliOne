package dev.openbili.webdemo.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedGridControlNavigationTest {
  @Test
  fun `down keeps the current column in a three-column grid`() {
    assertEquals(3, feedGridControlTargetIndex(0, 12, 3, FeedGridControlDirection.DOWN))
    assertEquals(4, feedGridControlTargetIndex(1, 12, 3, FeedGridControlDirection.DOWN))
    assertEquals(5, feedGridControlTargetIndex(2, 12, 3, FeedGridControlDirection.DOWN))
  }

  @Test
  fun `horizontal grid boundaries never wrap into another row or page`() {
    assertNull(feedGridControlTargetIndex(0, 12, 3, FeedGridControlDirection.LEFT))
    assertNull(feedGridControlTargetIndex(2, 12, 3, FeedGridControlDirection.RIGHT))
    assertEquals(1, feedGridControlTargetIndex(0, 12, 3, FeedGridControlDirection.RIGHT))
  }

  @Test
  fun `missing item in a partial last row keeps focus in place`() {
    assertNull(feedGridControlTargetIndex(5, 8, 3, FeedGridControlDirection.DOWN))
    assertNull(feedGridControlTargetIndex(7, 8, 3, FeedGridControlDirection.RIGHT))
  }

  @Test
  fun `popular two-column grid keeps its column across rows`() {
    assertEquals(2, feedGridControlTargetIndex(0, 8, 2, FeedGridControlDirection.DOWN))
    assertEquals(3, feedGridControlTargetIndex(1, 8, 2, FeedGridControlDirection.DOWN))
    assertEquals(1, feedGridControlTargetIndex(3, 8, 2, FeedGridControlDirection.UP))
  }
}

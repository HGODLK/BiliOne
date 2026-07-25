package dev.openbili.webdemo.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoActionStateTest {
  @Test
  fun coinIconUsesOneOrTwoVisibleCoins() {
    assertEquals(1, coinIconCount(0))
    assertEquals(1, coinIconCount(1))
    assertEquals(2, coinIconCount(2))
  }
}

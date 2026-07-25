package dev.openbili.webdemo.video

import org.junit.Assert.assertEquals
import org.junit.Test

class BangumiMetadataFormattingTest {
  @Test
  fun publishTimeKeepsDateButDropsHourPrecision() {
    assertEquals("2023-09-29", bangumiPublishDate("2023-09-29 23:00:00"))
    assertEquals("2023-09-29", bangumiPublishDate("2023-09-29T23:00:00"))
  }

  @Test
  fun fiveStarsMapToBilibiliTenPointScore() {
    assertEquals(2, bangumiScoreForStars(1))
    assertEquals(6, bangumiScoreForStars(3))
    assertEquals(10, bangumiScoreForStars(5))
  }
}

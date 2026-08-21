package dev.openbili.webdemo.feed

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedCardLayoutTest {
  @Test
  fun `home card info never exceeds two thirds of cover height`() {
    val cardWidth = 240.dp
    val coverHeight = cardWidth * (9f / 16f)

    assertEquals(coverHeight * (2f / 3f), feedCardInfoMaxHeight(cardWidth))
  }
}

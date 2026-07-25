package dev.openbili.webdemo.video

import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiPageLayoutTest {
  @Test
  fun fourByThreeLandscapeKeepsRoomForDetailAndEpisodeCards() {
    val paneHeight = 650f
    val layout = bangumiPageLayoutForPane(primaryWidthDp = 700f, primaryHeightDp = paneHeight)

    assertTrue(layout.playerHeight.value <= 700f * 9f / 16f)
    assertTrue(paneHeight - layout.playerHeight.value >= 166f)
  }

  @Test
  fun smallLandscapePaneStillHasUsablePlayerAndLowerCards() {
    val paneHeight = 410f
    val layout = bangumiPageLayoutForPane(primaryWidthDp = 620f, primaryHeightDp = paneHeight)

    assertTrue(layout.playerHeight.value >= 112f)
    assertTrue(paneHeight - layout.playerHeight.value >= 158f)
  }

  @Test
  fun playerNeverExceedsPrimaryPaneWidth() {
    val layout = bangumiPageLayoutForPane(primaryWidthDp = 520f, primaryHeightDp = 460f)

    assertTrue(layout.playerWidth.value <= 520f)
  }
}

package dev.openbili.webdemo.video

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoInfoTileLayoutTest {
  @Test
  fun largeTabletUsesStableDialogCaps() {
    assertEquals(
      VideoInfoTileSize(width = 760.dp, height = 620.dp),
      videoInfoTileSizeForWindow(maxWidth = 1480.dp, maxHeight = 924.dp),
    )
  }

  @Test
  fun compactWindowKeepsSymmetricSafeMargins() {
    assertEquals(
      VideoInfoTileSize(width = 366.dp, height = 496.dp),
      videoInfoTileSizeForWindow(maxWidth = 390.dp, maxHeight = 520.dp),
    )
  }

  @Test
  fun impossibleWindowNeverProducesNegativeSize() {
    assertEquals(
      VideoInfoTileSize(width = 1.dp, height = 1.dp),
      videoInfoTileSizeForWindow(maxWidth = 12.dp, maxHeight = 12.dp),
    )
  }
}

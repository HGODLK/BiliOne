package dev.openbili.webdemo.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverImageRequestFactoryTest {
  @Test
  fun originalSourceRemainsUncroppedWhenDrawnWithCropScale() {
    assertEquals(
      false,
      coverImageRequestProducesCroppedBitmap(crop = true, useOriginalSource = true),
    )
  }

  @Test
  fun serverCropIsRecordedAsCroppedBitmap() {
    assertEquals(
      true,
      coverImageRequestProducesCroppedBitmap(crop = true, useOriginalSource = false),
    )
  }

  @Test
  fun fitRequestReplacesExistingServerCrop() {
    assertEquals(
      "https://i0.hdslb.com/bfs/bangumi/poster.jpg@840w.webp",
      optimizedCoverImageUrl(
        "https://i0.hdslb.com/bfs/bangumi/poster.jpg@100w_144h_1c.webp",
        width = 840,
        height = 1120,
        crop = false,
      ),
    )
  }

  @Test
  fun cropRequestReplacesExistingDerivativeAtRequestedSize() {
    assertEquals(
      "https://i0.hdslb.com/bfs/bangumi/poster.jpg@360w_480h_1c.webp?token=x",
      optimizedCoverImageUrl(
        "http://i0.hdslb.com/bfs/bangumi/poster.jpg@120w.webp?token=x",
        width = 360,
        height = 480,
        crop = true,
      ),
    )
  }
}

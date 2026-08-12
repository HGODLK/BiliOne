package dev.openbili.webdemo

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressStoreTest {
  @Test
  fun nearEndExitPositionIsStillResumable() {
    assertEquals(791_000L, PlaybackProgressStore.normalize(791_000L, 800_000L))
  }

  @Test
  fun serverPositionIsClampedWithoutBeingReset() {
    assertEquals(800_000L, PlaybackProgressStore.normalize(805_000L, 800_000L))
    assertEquals(0L, PlaybackProgressStore.normalize(-1L, 800_000L))
  }
}

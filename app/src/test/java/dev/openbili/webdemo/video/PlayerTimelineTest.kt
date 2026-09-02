package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.VideoChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTimelineTest {
  @Test
  fun chapterBoundariesAreNormalizedAndSorted() {
    val boundaries =
      chapterBoundaryFractions(
        durationMs = 100_000L,
        chapters =
          listOf(
            VideoChapter(startMs = 60_000L, endMs = 90_000L, title = "C"),
            VideoChapter(startMs = 0L, endMs = 20_000L, title = "A"),
            VideoChapter(startMs = 20_000L, endMs = 60_000L, title = "B"),
          ),
      )

    assertEquals(listOf(0.2f, 0.6f), boundaries)
  }

  @Test
  fun invalidAndOuterBoundariesAreIgnored() {
    val boundaries =
      chapterBoundaryFractions(
        durationMs = 100_000L,
        chapters =
          listOf(
            VideoChapter(startMs = -1L, endMs = 5_000L, title = "bad"),
            VideoChapter(startMs = 100_000L, endMs = 120_000L, title = "outer"),
            VideoChapter(startMs = 30_000L, endMs = 30_000L, title = "empty"),
          ),
      )

    assertTrue(boundaries.isEmpty())
  }

  @Test
  fun firstAndLastPointBoundariesAreAlwaysHidden() {
    val boundaries =
      chapterBoundaryFractions(
        durationMs = 100_000L,
        chapters =
          listOf(
            VideoChapter(startMs = 10_000L, endMs = 40_000L, title = "A"),
            VideoChapter(startMs = 40_000L, endMs = 90_000L, title = "B"),
          ),
      )

    assertEquals(listOf(0.4f), boundaries)
  }

  @Test
  fun nearestChapterBoundaryOnlySnapsInsideHitSlop() {
    val chapters =
      listOf(
        VideoChapter(0L, 20_000L, "A"),
        VideoChapter(20_000L, 60_000L, "B"),
      )
    assertEquals(20_000L, nearestChapterBoundaryForTap(200f, 1_000f, 100_000L, chapters, 12f))
    assertNull(nearestChapterBoundaryForTap(260f, 1_000f, 100_000L, chapters, 12f))
    assertNull(nearestChapterBoundaryForTap(600f, 1_000f, 100_000L, chapters, 12f))
  }

  @Test
  fun chapterAtPositionUsesDeclaredRange() {
    val chapters =
      listOf(
        VideoChapter(10_000L, 20_000L, "A"),
        VideoChapter(40_000L, 60_000L, "B"),
      )
    assertEquals("A", chapterAtPosition(15_000L, chapters)?.title)
    assertNull(chapterAtPosition(30_000L, chapters))
    assertEquals("B", chapterAtPosition(45_000L, chapters)?.title)
    assertNull(chapterAtPosition(60_000L, chapters))
  }

  @Test
  fun activeChapterColorsBothVisibleBoundaryPoints() {
    val chapters =
      listOf(
        VideoChapter(0L, 20_000L, "A"),
        VideoChapter(20_000L, 60_000L, "B"),
        VideoChapter(60_000L, 90_000L, "C"),
      )

    assertEquals(
      setOf(0.2f, 0.6f),
      activeChapterBoundaryFractions(100_000L, 30_000L, chapters),
    )
    assertTrue(activeChapterBoundaryFractions(100_000L, 95_000L, chapters).isEmpty())
  }
}

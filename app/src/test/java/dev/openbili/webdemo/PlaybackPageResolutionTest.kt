package dev.openbili.webdemo

import dev.openbili.webdemo.api.VideoPage
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPageResolutionTest {
  private val pages =
    listOf(
      VideoPage(page = 1, cid = 101L, part = "第一集", durationSeconds = 62L),
      VideoPage(page = 2, cid = 102L, part = "第二集", durationSeconds = 75L),
    )

  @Test
  fun initialMultiPagePlaybackUsesDurationOfDefaultCid() {
    val selected = resolvePlaybackPage(requestedPage = null, defaultCid = 101L, pages = pages)

    assertEquals(101L, selected?.cid)
    assertEquals(62L, selected?.durationSeconds)
  }

  @Test
  fun explicitSelectionWinsOverDefaultCid() {
    val selected = resolvePlaybackPage(requestedPage = pages[1], defaultCid = 101L, pages = pages)

    assertEquals(102L, selected?.cid)
    assertEquals(75L, selected?.durationSeconds)
  }

  @Test
  fun retainedSecondPageCidRestoresSecondPage() {
    val selected = resolvePlaybackPage(requestedPage = null, defaultCid = 102L, pages = pages)

    assertEquals(102L, selected?.cid)
    assertEquals("第二集", selected?.part)
  }
}

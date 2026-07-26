package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.DanmakuInlineEmote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineDanmakuTest {
  @Test
  fun longestTokenWinsAndMixedTextOrderIsPreserved() {
    val segments =
      splitInlineDanmaku(
        "前[doge大]中[doge]后",
        listOf(
          DanmakuInlineEmote("[doge]", "https://example.com/doge.png"),
          DanmakuInlineEmote("[doge大]", "https://example.com/doge-large.png"),
        ),
      )

    assertEquals(5, segments.size)
    assertEquals("前", (segments[0] as InlineDanmakuSegment.Text).value)
    assertEquals("[doge大]", (segments[1] as InlineDanmakuSegment.Emote).value.token)
    assertEquals("中", (segments[2] as InlineDanmakuSegment.Text).value)
    assertEquals("[doge]", (segments[3] as InlineDanmakuSegment.Emote).value.token)
    assertEquals("后", (segments[4] as InlineDanmakuSegment.Text).value)
  }

  @Test
  fun missingMappingFallsBackToOneTextSegment() {
    val segments = splitInlineDanmaku("普通文字", emptyList())

    assertEquals(1, segments.size)
    assertTrue(segments.single() is InlineDanmakuSegment.Text)
  }
}

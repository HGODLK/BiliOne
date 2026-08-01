package dev.openbili.webdemo.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDanmakuBlockWordsTest {
  @Test
  fun parsesCommaAndLineSeparatedWordsAndRemovesCaseInsensitiveDuplicates() {
    assertEquals(
      listOf("剧透", "广告", "spoiler"),
      parseLiveDanmakuBlockWords("剧透，广告\nspoiler,Spoiler"),
    )
  }

  @Test
  fun matchesTextEmojiAndSystemContentIgnoringCase() {
    val words = listOf("spoiler", "广告")
    assertTrue(isLiveDanmakuBlocked(LiveChatContent.Text("SPOILER 警告"), words))
    assertTrue(
      isLiveDanmakuBlocked(
        LiveChatContent.Emoji("广告表情", null, null, false),
        words,
      )
    )
    assertFalse(isLiveDanmakuBlocked(LiveChatContent.System("欢迎进入直播间"), words))
  }
}

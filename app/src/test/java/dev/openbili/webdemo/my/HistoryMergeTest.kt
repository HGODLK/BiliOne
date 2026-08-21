package dev.openbili.webdemo.my

import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.feed.FeedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HistoryMergeTest {
  @Test
  fun bangumiRecordsWithDifferentArchiveIdentityMergeByEpisode() {
    val newest = bangumiHistory(viewAt = 200L, bvid = "BVNEW", aid = 2L)
    val older = bangumiHistory(viewAt = 100L, bvid = "BVOLD", aid = 1L)

    val merged = mergeHistoryItems(listOf(older, newest))

    assertEquals(1, merged.size)
    assertSame(newest, merged.single())
  }

  private fun bangumiHistory(viewAt: Long, bvid: String, aid: Long): HistoryCardItem.Bangumi {
    val card =
      SpaceContentCard(
        id = "history:pgc:ep2009785",
        title = "测试番剧",
        bvid = bvid,
        aid = aid,
        episodeId = 2009785L,
      )
    return HistoryCardItem.Bangumi(
      item =
        FeedItem(
          id = card.id,
          title = card.title,
          videoUrl = card.videoUrl,
          coverUrl = card.coverUrl,
          uploader = null,
          playCount = null,
          duration = null,
          publishedAt = viewAt,
        ),
      bangumi = card,
      mediaLabel = "番剧",
      viewAt = viewAt,
    )
  }
}

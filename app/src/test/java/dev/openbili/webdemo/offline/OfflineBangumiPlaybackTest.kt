package dev.openbili.webdemo.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineBangumiPlaybackTest {
  @Test
  fun `completed matching episode returns its offline entry`() {
    val entry = bangumiEntry(episodeId = 101L, cid = 1001L)

    val result =
      OfflineBangumiPlaybackResolver.resolve(
        snapshots = listOf(OfflineMediaSnapshot(entry, OfflineTransferState.COMPLETED, 100f, 1L, 1L)),
        episodeId = 101L,
        cid = 1001L,
        seasonId = 9001L,
        canPlay = { true },
      )

    assertEquals(entry, result.entry)
    assertNull(result.blockReason)
  }

  @Test
  fun `episode with different cid is not selected`() {
    val entry = bangumiEntry(episodeId = 101L, cid = 1001L)

    val result =
      OfflineBangumiPlaybackResolver.resolve(
        snapshots = listOf(OfflineMediaSnapshot(entry, OfflineTransferState.COMPLETED, 100f, 1L, 1L)),
        episodeId = 101L,
        cid = 1002L,
        seasonId = 9001L,
        canPlay = { true },
      )

    assertNull(result.entry)
    assertEquals(OfflineBangumiPlaybackBlockReason.NOT_CACHED, result.blockReason)
  }

  @Test
  fun `incomplete episode is blocked without online fallback`() {
    val entry = bangumiEntry(episodeId = 101L, cid = 1001L)

    val result =
      OfflineBangumiPlaybackResolver.resolve(
        snapshots = listOf(OfflineMediaSnapshot(entry, OfflineTransferState.DOWNLOADING, 42f, 1L, 2L)),
        episodeId = 101L,
        cid = 1001L,
        seasonId = 9001L,
        canPlay = { true },
      )

    assertNull(result.entry)
    assertEquals(OfflineBangumiPlaybackBlockReason.NOT_COMPLETED, result.blockReason)
  }

  @Test
  fun `completed episode with invalid entitlement is blocked`() {
    val entry = bangumiEntry(episodeId = 101L, cid = 1001L)

    val result =
      OfflineBangumiPlaybackResolver.resolve(
        snapshots = listOf(OfflineMediaSnapshot(entry, OfflineTransferState.COMPLETED, 100f, 1L, 1L)),
        episodeId = 101L,
        cid = 1001L,
        seasonId = 9001L,
        canPlay = { false },
      )

    assertNull(result.entry)
    assertEquals(OfflineBangumiPlaybackBlockReason.UNAVAILABLE, result.blockReason)
  }

  private fun bangumiEntry(episodeId: Long, cid: Long) =
    OfflineMediaEntry(
      id = offlineMediaId(OfflineMediaKind.BANGUMI, "BV1test", cid, episodeId),
      kind = OfflineMediaKind.BANGUMI,
      accountMid = 1L,
      title = "测试番剧",
      partTitle = "第${episodeId}集",
      coverUrl = "https://example.com/cover.jpg",
      bvid = "BV1test",
      aid = 2L,
      cid = cid,
      pageNumber = episodeId.toInt(),
      seasonId = 9001L,
      episodeId = episodeId,
      durationMs = 60_000L,
      qualityId = 80,
      qualityLabel = "1080P",
    )
}

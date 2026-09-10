package dev.openbili.webdemo.offline

import androidx.media3.exoplayer.offline.Download
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineProgressCalculatorTest {
  @Test
  fun combinesKnownTrackSizesByBytes() {
    val result =
      combineOfflineTrackProgress(
        listOf(
          OfflineTrackProgress(Download.STATE_DOWNLOADING, 800L, 400L, 50f),
          OfflineTrackProgress(Download.STATE_DOWNLOADING, 200L, 100L, 50f),
        )
      )

    assertEquals(500L, result.bytesDownloaded)
    assertEquals(1_000L, result.totalBytes)
    assertEquals(50f, result.percent!!, 0.001f)
  }

  @Test
  fun doesNotTreatUnknownTrackAsZeroLength() {
    val result =
      combineOfflineTrackProgress(
        listOf(
          OfflineTrackProgress(Download.STATE_COMPLETED, -1L, 800L, -1f),
          OfflineTrackProgress(Download.STATE_QUEUED, -1L, 0L, -1f),
        )
      )

    assertEquals(800L, result.bytesDownloaded)
    assertEquals(0L, result.totalBytes)
    assertNull(result.percent)
  }

  @Test
  fun fallsBackToAverageOnlyWhenEveryTrackHasPercentage() {
    val result =
      combineOfflineTrackProgress(
        listOf(
          OfflineTrackProgress(Download.STATE_DOWNLOADING, -1L, 0L, 20f),
          OfflineTrackProgress(Download.STATE_DOWNLOADING, -1L, 0L, 60f),
        )
      )

    assertEquals(40f, result.percent!!, 0.001f)
  }
}

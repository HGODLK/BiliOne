package dev.openbili.webdemo

import org.junit.Assert.assertEquals
import org.junit.Test

class CdnRoutingPreferenceTest {
  @Test
  fun firstBackupIsTheDefaultPrimary() {
    val result =
      prioritizeCdnUrls(
        primary = "https://primary.example/video.m4s",
        backups =
          listOf(
            "https://backup-a.example/video.m4s",
            "https://backup-b.example/video.m4s",
          ),
        preferredHost = "",
      )

    assertEquals("https://backup-a.example/video.m4s", result.primary)
    assertEquals(
      listOf(
        "https://backup-b.example/video.m4s",
        "https://primary.example/video.m4s",
      ),
      result.backups,
    )
  }

  @Test
  fun persistedHostWinsOnLaterVideos() {
    val result =
      prioritizeCdnUrls(
        primary = "https://primary.example/another.m4s",
        backups =
          listOf(
            "https://backup-a.example/another.m4s",
            "https://backup-b.example/another.m4s",
          ),
        preferredHost = "backup-b.example",
      )

    assertEquals("https://backup-b.example/another.m4s", result.primary)
  }
}

package dev.openbili.webdemo

import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.api.VideoStream
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliDashManifestBackupUrlTest {
  @Test
  fun manifestKeepsPrimaryAndBackupCdnsInPriorityOrder() {
    val stream =
      VideoStream(
        id = 120,
        quality = "4K",
        url = "https://primary.example/video.m4s",
        codecId = 12,
        codecs = "hvc1.2.4.L153.90",
        bandwidth = 20_000_000,
        initializationRange = "0-999",
        indexRange = "1000-1999",
        backupUrls = listOf("https://backup.example/video.m4s"),
      )
    val manifest =
      requireNotNull(
        BiliDashManifest.build(
          PlayUrlData(
            dashAudioUrl = null,
            streams = listOf(stream),
            currentStreamIndex = 0,
            durationMs = 60_000,
          )
        )
      )

    val primary = manifest.indexOf("https://primary.example/video.m4s")
    val backup = manifest.indexOf("https://backup.example/video.m4s")
    assertTrue(primary >= 0)
    assertTrue(backup > primary)
    assertTrue(manifest.contains("priority=\"1\""))
    assertTrue(manifest.contains("priority=\"2\""))
  }
}

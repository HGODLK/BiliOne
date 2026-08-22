package dev.openbili.webdemo

import org.junit.Assert.assertEquals
import org.junit.Test

class CdnRoutingPreferenceTest {
  @Test
  fun apiPrimaryRemainsTheDefaultMainRoute() {
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

    assertEquals("https://primary.example/video.m4s", result.primary)
    assertEquals(
      listOf(
        "https://backup-a.example/video.m4s",
        "https://backup-b.example/video.m4s",
      ),
      result.backups,
    )
  }

  @Test
  fun configuredMainRouteWinsWhenApiProvidesIt() {
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

  @Test
  fun currentCarrierRouteIsTheFirstFallbackAfterConfiguredMainRoute() {
    val result =
      prioritizeCdnUrls(
        primary = "https://cn-sccd-ct-02-08.bilivideo.com/video.m4s",
        backups =
          listOf(
            "https://upos-sz-mirrorcos.bilivideo.com/video.m4s",
            "https://d1--cn-gotcha208.bilivideo.com/video.m4s",
            "https://cn-gz-cu-01-01.bilivideo.com/video.m4s",
            "https://cn-sccd-ct-02-07.bilivideo.com/video.m4s",
          ),
        preferredHost = "d1--cn-gotcha208.bilivideo.com",
      )

    assertEquals("https://d1--cn-gotcha208.bilivideo.com/video.m4s", result.primary)
    assertEquals(
      listOf(
        "https://cn-sccd-ct-02-08.bilivideo.com/video.m4s",
        "https://cn-sccd-ct-02-07.bilivideo.com/video.m4s",
        "https://upos-sz-mirrorcos.bilivideo.com/video.m4s",
        "https://cn-gz-cu-01-01.bilivideo.com/video.m4s",
      ),
      result.backups,
    )
  }

  @Test
  fun firstCarrierTaggedRouteProvidesTheCurrentCarrierHint() {
    val result =
      prioritizeCdnUrls(
        primary = "https://xy116x196x140x29xy.mcdn.bilivideo.cn/video.m4s",
        backups =
          listOf(
            "https://upos-sz-mirrorcos.bilivideo.com/video.m4s",
            "https://cn-jscz-cu-01-01.bilivideo.com/video.m4s",
            "https://cn-sccd-ct-02-07.bilivideo.com/video.m4s",
            "https://cn-jsnj-cu-01-02.bilivideo.com/video.m4s",
          ),
        preferredHost = "",
      )

    assertEquals("https://xy116x196x140x29xy.mcdn.bilivideo.cn/video.m4s", result.primary)
    assertEquals(
      listOf(
        "https://cn-jscz-cu-01-01.bilivideo.com/video.m4s",
        "https://cn-jsnj-cu-01-02.bilivideo.com/video.m4s",
        "https://upos-sz-mirrorcos.bilivideo.com/video.m4s",
        "https://cn-sccd-ct-02-07.bilivideo.com/video.m4s",
      ),
      result.backups,
    )
  }

  @Test
  fun mainlandMainHostWinsWhenApiProvidesIt() {
    val mainlandMainHost = "d1--cn-gotcha208.bilivideo.com"
    val result =
      prioritizeCdnUrls(
        primary = "https://upos-sz-upcdnbda2.bilivideo.com/video.m4s",
        backups =
          listOf(
            "https://upos-sz-mirrorcos.bilivideo.com/video.m4s",
            "https://$mainlandMainHost/video.m4s",
          ),
        preferredHost = mainlandMainHost,
      )

    assertEquals("https://$mainlandMainHost/video.m4s", result.primary)
  }
}

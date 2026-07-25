package dev.openbili.webdemo.api

import org.junit.Assert.assertEquals
import org.junit.Test

class BangumiCoverUrlTest {
  @Test
  fun createsTheWebHorizontalDerivativeFromAnAlreadyProcessedCover() {
    val url =
      bangumiCoverUrl(
        "//i0.hdslb.com/bfs/bangumi/image/season.png@480w_640h_1c.webp",
        BangumiCoverVariant.HORIZONTAL_CARD,
      )

    assertEquals(
      "https://i0.hdslb.com/bfs/bangumi/image/season.png@560w_312h_!web-ogv-anime-horizontal-card.webp",
      url,
    )
  }

  @Test
  fun createsTheWebPosterDerivativeAndLeavesOtherCdnHostsUntouched() {
    assertEquals(
      "https://i0.hdslb.com/bfs/bangumi/image/season.jpg@560w_746h_!web-ogv-anime-ranking-card.webp?x=1",
      bangumiCoverUrl(
        "https://i0.hdslb.com/bfs/bangumi/image/season.jpg?x=1",
        BangumiCoverVariant.POSTER,
      ),
    )
    assertEquals(
      "https://example.com/season.jpg",
      bangumiCoverUrl("https://example.com/season.jpg", BangumiCoverVariant.POSTER),
    )
  }

  @Test
  fun removesDerivedCropForHeroSource() {
    assertEquals(
      "https://i0.hdslb.com/bfs/archive/hero.jpg?token=abc",
      bangumiOriginalImageUrl(
        "https://i0.hdslb.com/bfs/archive/hero.jpg@600w_506h_!web-ogv-anime-newhot-bg.webp?token=abc"
      ),
    )
  }
}

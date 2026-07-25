package dev.openbili.webdemo

import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.SpaceContentCard
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BangumiPlaybackStoreTest {
  private val context
    get() = RuntimeEnvironment.getApplication()

  @Before
  fun clearStore() {
    context.getSharedPreferences("bangumi_playback_selection", 0).edit().clear().commit()
  }

  @Test
  fun selectionIsSharedBySeasonAcrossSearchAndProfileCards() {
    val searchCard = SpaceContentCard(id = "search:bangumi:42", title = "作品", seasonId = 42)
    val profileCard = SpaceContentCard(id = "bangumi:42", title = "作品", seasonId = 42)
    val episode =
      BangumiEpisode(
        id = 108,
        aid = 208,
        bvid = "BV1EPISODE",
        cid = 308,
        title = "8",
        longTitle = "继续冒险",
        coverUrl = "",
        durationSeconds = 1_400,
      )

    BangumiPlaybackStore.save(context, searchCard, seasonId = 42, episode = episode)

    assertEquals(
      BangumiPlaybackStore.Selection(42, 108, "BV1EPISODE"),
      BangumiPlaybackStore.read(context, profileCard),
    )
  }
}

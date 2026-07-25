package dev.openbili.webdemo.profile

import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileBangumiFilterTest {
  private val cards =
    listOf(
      SpaceContentCard(id = "bangumi:1", title = "番剧", kind = SpaceContentKind.BANGUMI),
      SpaceContentCard(id = "drama:2", title = "剧集", kind = SpaceContentKind.DRAMA),
    )

  @Test
  fun allKeepsBangumiAndDrama() {
    assertEquals(cards, filterProfileBangumi(cards, ProfileBangumiFilter.ALL))
  }

  @Test
  fun filtersBangumiAndDramaSeparately() {
    assertEquals(
      listOf("bangumi:1"),
      filterProfileBangumi(cards, ProfileBangumiFilter.BANGUMI).map { it.id },
    )
    assertEquals(
      listOf("drama:2"),
      filterProfileBangumi(cards, ProfileBangumiFilter.DRAMA).map { it.id },
    )
  }

  @Test
  fun loadMoreTargetsOnlyKindsThatStillHavePages() {
    assertEquals(
      listOf(1, 2),
      profileBangumiTypesToLoad(
        ProfileBangumiFilter.ALL,
        bangumiHasMore = true,
        dramaHasMore = true,
      ),
    )
    assertEquals(
      listOf(2),
      profileBangumiTypesToLoad(
        ProfileBangumiFilter.ALL,
        bangumiHasMore = false,
        dramaHasMore = true,
      ),
    )
    assertEquals(
      emptyList<Int>(),
      profileBangumiTypesToLoad(
        ProfileBangumiFilter.BANGUMI,
        bangumiHasMore = false,
        dramaHasMore = true,
      ),
    )
  }

  @Test
  fun followRequestUsesRequestedPageInsteadOfRepeatingFirstPage() {
    assertEquals(
      "https://api.bilibili.com/x/space/bangumi/follow/list?type=1&pn=3&ps=30&vmid=42",
      BiliApi.spaceBangumiFollowUrl(mid = 42, type = 1, page = 3, pageSize = 30),
    )
  }
}

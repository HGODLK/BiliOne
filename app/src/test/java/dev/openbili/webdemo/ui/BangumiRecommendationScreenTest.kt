package dev.openbili.webdemo.ui

import androidx.compose.ui.geometry.Offset
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.BangumiRecommendation
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.BangumiSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiRecommendationScreenTest {
  @Test
  fun cardWithoutNavigationIsNotHiddenWhenNoCardIsTransitioning() {
    assertFalse(shouldHideBangumiRecommendationCard(cardId = null, hiddenCardId = null))
  }

  @Test
  fun cardIsHiddenOnlyWhenItsConcreteIdMatchesTransitionTarget() {
    assertTrue(shouldHideBangumiRecommendationCard(cardId = "episode-1", hiddenCardId = "episode-1"))
    assertFalse(shouldHideBangumiRecommendationCard(cardId = "episode-1", hiddenCardId = null))
  }

  @Test
  fun retainedHeroStaysVisuallyAvailableWhileDetailIsAboveIt() {
    assertTrue(
      shouldRetainBangumiHeroVisuals(
        active = false,
        preloadEnabled = false,
        retainedForDetailReturn = true,
      )
    )
    assertFalse(
      shouldRetainBangumiHeroVisuals(
        active = false,
        preloadEnabled = false,
        retainedForDetailReturn = false,
      )
    )
  }

  @Test
  fun previewCommitWaitsForDecodedCover() {
    assertFalse(shouldCommitBangumiPreview(targetAvailable = true, coverReady = false))
    assertFalse(shouldCommitBangumiPreview(targetAvailable = false, coverReady = true))
    assertTrue(shouldCommitBangumiPreview(targetAvailable = true, coverReady = true))
  }

  @Test
  fun previewPlaybackRequiresVisiblePageAndStartedLifecycle() {
    assertTrue(
      shouldPlayBangumiPreview(active = true, mainPageVisible = false, lifecycleStarted = true)
    )
    assertFalse(
      shouldPlayBangumiPreview(active = true, mainPageVisible = false, lifecycleStarted = false)
    )
    assertFalse(
      shouldPlayBangumiPreview(active = true, mainPageVisible = true, lifecycleStarted = true)
    )
    assertFalse(
      shouldPlayBangumiPreview(active = false, mainPageVisible = false, lifecycleStarted = true)
    )
  }

  @Test
  fun previewCoverGestureStartsOnlyAfterHorizontalTouchSlop() {
    assertNull(bangumiCardGestureIsHorizontal(Offset(7f, 1f), touchSlop = 8f))
    assertTrue(bangumiCardGestureIsHorizontal(Offset(20f, 4f), touchSlop = 8f)!!)
    assertFalse(bangumiCardGestureIsHorizontal(Offset(4f, 20f), touchSlop = 8f)!!)
  }

  @Test
  fun startupPreloadWaitsForEveryCarouselItem() {
    val carouselItems = (1L..7L).map(::recommendation)
    val loadedSeason = season(episodes = emptyList(), pv = null)
    val seasons =
      carouselItems.take(6).associate { item -> item.stableId to loadedSeason }
    val errors = mapOf(carouselItems.last().stableId to "detail unavailable")

    assertTrue(bangumiPreloadDetailsSettled(carouselItems, seasons, errors))
    assertFalse(
      bangumiPreloadDetailsSettled(carouselItems, seasons - carouselItems[1].stableId, errors)
    )
  }

  @Test
  fun liveCardDoesNotBlockPreviewDetailPreload() {
    val live = recommendation(seasonId = 99L).copy(isLive = true)

    assertTrue(bangumiPreloadDetailsSettled(listOf(live), emptyMap(), emptyMap()))
  }

  @Test
  fun previewPortalParksWheneverPreviewPageIsNotVisible() {
    assertFalse(
      shouldPositionBangumiPreviewPortal(
        previewOwned = true,
        boundsUsable = true,
        previewPortalVisible = false,
      )
    )
    assertTrue(
      shouldPositionBangumiPreviewPortal(
        previewOwned = true,
        boundsUsable = true,
        previewPortalVisible = true,
      )
    )
  }

  @Test
  fun cardFlightDetachesDetailPlayerOnlyAfterExitCoverIsCommitted() {
    assertFalse(
      shouldSuppressDetailPlayerForBangumiCardTransition(
        TransitionKind.EXIT_ROOT,
        SessionPhase.PREPARING,
      )
    )
    assertTrue(
      shouldSuppressDetailPlayerForBangumiCardTransition(
        TransitionKind.EXIT_ROOT,
        SessionPhase.FLYING,
      )
    )
    assertTrue(
      shouldSuppressDetailPlayerForBangumiCardTransition(
        TransitionKind.ENTER_ROOT,
        SessionPhase.FLYING,
      )
    )
    assertFalse(
      shouldSuppressDetailPlayerForBangumiCardTransition(
        TransitionKind.ENTER_ROOT,
        SessionPhase.REVEALING_BACKGROUND,
      )
    )
  }

  @Test
  fun previewSelectionPrefersPromotionalSectionAndFormalPv() {
    val nextEpisodePreview = episode(1L, "第 2 集预告")
    val formalPv = episode(2L, "正式宣传片")
    val sections =
      listOf(
        BangumiSection(1L, "下集预告", listOf(nextEpisodePreview)),
        BangumiSection(2L, "PV", listOf(formalPv)),
      )

    assertEquals(formalPv, selectBangumiPreviewEpisode(sections))
  }

  @Test
  fun previewSelectionUsesFullPriorityAndKeepsMvPlayable() {
    val behindTheScenes = episode(1L, "制作花絮")
    val nextEpisodePreview = episode(2L, "第2集预告")
    val highlight = episode(3L, "高能名场面")
    val musicVideo = episode(4L, "主题曲 MV")
    val trailer = episode(5L, "终极预告")
    val sections =
      listOf(
        BangumiSection(1L, "PV＆花絮", listOf(behindTheScenes, musicVideo)),
        BangumiSection(2L, "精彩片段", listOf(highlight)),
        BangumiSection(3L, "下集预告", listOf(nextEpisodePreview)),
        BangumiSection(4L, "宣传物料", listOf(trailer)),
      )

    assertEquals(trailer, selectBangumiPreviewEpisode(sections))
  }

  @Test
  fun musicVideoPrecedesHighlightsAndNextEpisodePreview() {
    val musicVideo = episode(1L, "片头曲 MV")
    val highlight = episode(2L, "热血高光片段")
    val nextEpisodePreview = episode(3L, "第2集预告")
    val sections =
      listOf(
        BangumiSection(1L, "PV＆花絮", listOf(musicVideo)),
        BangumiSection(2L, "精彩看点", listOf(highlight)),
        BangumiSection(3L, "下集预告", listOf(nextEpisodePreview)),
      )

    assertEquals(musicVideo, selectBangumiPreviewEpisode(sections))
  }

  @Test
  fun highlightsPrecedeNextEpisodePreview() {
    val highlight = episode(1L, "热血高光片段")
    val nextEpisodePreview = episode(2L, "第2集预告")

    assertEquals(
      highlight,
      selectBangumiPreviewEpisode(
        listOf(
          BangumiSection(1L, "下集预告", listOf(nextEpisodePreview)),
          BangumiSection(2L, "精彩片段", listOf(highlight)),
        )
      ),
    )
  }

  @Test
  fun autoplaySkipsUnrecognizedExtrasAndFallsBackToMainEpisode() {
    val material = episode(1L, "独家物料")
    val main = episode(2L, "第一话")

    assertEquals(
      main,
      selectBangumiAutoplayEpisode(
        sections = listOf(BangumiSection(1L, "独家物料", listOf(material))),
        episodes = listOf(main),
      ),
    )
  }

  @Test
  fun previewSelectionDoesNotUseMainEpisodeAsPv() {
    assertNull(selectBangumiPreviewEpisode(emptyList()))
  }

  @Test
  fun playbackSelectionUsesSettledPagerAnchor() {
    assertEquals(0, bangumiRecommendationIndexForSettledPage(0, 7))
    assertEquals(1, bangumiRecommendationIndexForSettledPage(1, 7))
    assertEquals(0, bangumiRecommendationIndexForSettledPage(7, 7))
  }

  @Test
  fun coverSelectionUsesFirstMainEpisodeInsteadOfPv() {
    val firstEpisode = episode(10L, "第一话")
    val secondEpisode = episode(11L, "第二话")

    assertEquals(firstEpisode, selectBangumiMainEpisode(listOf(firstEpisode, secondEpisode)))
  }

  @Test
  fun autoplayFallsBackToFirstEpisodeWhenSeasonHasNoPv() {
    val firstEpisode = episode(20L, "第一话")

    assertEquals(firstEpisode, selectBangumiAutoplayEpisode(emptyList(), listOf(firstEpisode)))
  }

  @Test
  fun playerCoverUsesActualAutoplayEpisodeCover() {
    val pv = episode(30L, "正式 PV", "https://example.com/pv.webp")
    val season = season(episodes = listOf(episode(31L, "第一话", "main")), pv = pv)
    val item = recommendation(seasonId = 48511L, bannerUrl = "https://example.com/banner.png")

    assertEquals(pv.coverUrl, recommendationPlaybackCover(item, season))
  }

  @Test
  fun mainCardCoverUsesActualFirstEpisodeCover() {
    val first = episode(40L, "第一话", "https://example.com/episode.webp")
    val season = season(episodes = listOf(first), pv = null)
    val item = recommendation(seasonId = 1L, bannerUrl = "https://example.com/banner.png")

    assertEquals(first.coverUrl, recommendationMainEpisodeCover(item, season))
  }

  @Test
  fun playbackCoverFallsBackToSeasonThenCard() {
    val season = season(episodes = emptyList(), pv = null)
      .copy(coverUrl = "https://example.com/season.webp")
    val item = recommendation(seasonId = 1L, cardUrl = "https://example.com/card.png")

    assertEquals(season.coverUrl, recommendationPlaybackCover(item, season))
    assertEquals(item.cardUrl, recommendationPlaybackCover(item, null))
  }

  @Test
  fun mainCardCoverFallsBackToSeasonThenCard() {
    val season = season(episodes = emptyList(), pv = null)
      .copy(coverUrl = "https://example.com/season.webp")
    val item = recommendation(seasonId = 1L, cardUrl = "https://example.com/card.png")

    assertEquals(season.coverUrl, recommendationMainEpisodeCover(item, season))
    assertEquals(item.cardUrl, recommendationMainEpisodeCover(item, null))
  }

  private fun recommendation(
    seasonId: Long = 1L,
    bannerUrl: String = "https://example.com/banner.png",
    cardUrl: String = "https://example.com/card.png",
  ) = BangumiRecommendation(
    stableId = "season:$seasonId",
    title = "测试番剧",
    bannerUrl = bannerUrl,
    cardUrl = cardUrl,
    targetUrl = "https://www.bilibili.com/bangumi/play/ss$seasonId",
    seasonId = seasonId,
    position = 0,
  )

  private fun episode(id: Long, title: String, coverUrl: String = "") =
    BangumiEpisode(
      id = id,
      aid = id,
      bvid = "BV$id",
      cid = id,
      title = title,
      longTitle = "",
      coverUrl = coverUrl,
      durationSeconds = 30,
    )

  private fun season(episodes: List<BangumiEpisode>, pv: BangumiEpisode?) =
    BangumiSeason(
      seasonId = 1L,
      mediaId = 1L,
      title = "测试番剧",
      coverUrl = "season",
      evaluate = "",
      typeName = "番剧",
      areas = emptyList(),
      styles = emptyList(),
      publishText = "",
      rating = null,
      ratingCount = 0L,
      followCount = 0L,
      viewCount = 0L,
      danmakuCount = 0L,
      followed = false,
      episodes = episodes,
      seasons = emptyList(),
      sections = pv?.let { listOf(BangumiSection(2L, "PV", listOf(it))) }.orEmpty(),
    )
}

package dev.openbili.webdemo.video

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationCardSizingTest {
  @Test
  fun tabS8UltraPaneUsesAvailableWidthAndHeight() {
    assertEquals(312.dp, recommendationCardWidthForPane(1074f, 912f))
  }

  @Test
  fun narrowerAppWindowProducesContinuousCardWidth() {
    assertEquals(302.94f, recommendationCardWidthForPane(891f, 912f).value, .01f)
  }

  @Test
  fun reducedAppHeightUsesCompactMinimum() {
    val layout = videoPageLayoutForPane(1074f, 800f)
    assertEquals(true, layout.compactHorizontalRecommendations)
    assertEquals(440.dp, layout.recommendationCardWidth)
    assertEquals(155.875f, layout.compactRecommendationCardHeight.value, .01f)
  }

  @Test
  fun shortPaneUsesHorizontalRecommendationsWithoutZeroSizedPlayer() {
    val layout = videoPageLayoutForPane(296f, 235f)
    assertEquals(true, layout.compactHorizontalRecommendations)
    assertEquals(111.dp, layout.playerHeight)
    assertEquals(280.dp, layout.recommendationCardWidth)
  }

  @Test
  fun largeFontScaleExpandsHorizontalRecommendationCard() {
    val layout = videoPageLayoutForPane(744f, 582f, fontScale = 1.4f)

    assertEquals(true, layout.compactHorizontalRecommendations)
    assertEquals(105.5f, layout.compactRecommendationCardHeight.value, .01f)
    assertEquals(418.5f, layout.playerHeight.value, .01f)
  }

  @Test
  fun recommendationCardsScaleContinuouslyAcrossCommonTabletRatios() {
    val fourByThree = videoPageLayoutForPane(680f, 540f)
    val sixteenByTen = videoPageLayoutForPane(744f, 582f)
    val wide = videoPageLayoutForPane(920f, 620f)

    assertEquals(339.56f, fourByThree.recommendationCardWidth.value, .01f)
    assertEquals(350.22f, sixteenByTen.recommendationCardWidth.value, .01f)
    assertEquals(423.2f, wide.recommendationCardWidth.value, .01f)
    assertEquals(117.5f, fourByThree.compactRecommendationCardHeight.value, .01f)
    assertEquals(123.5f, sixteenByTen.compactRecommendationCardHeight.value, .01f)
    assertEquals(84f, wide.compactRecommendationCardHeight.value, .01f)
  }
}

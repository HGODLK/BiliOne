package dev.openbili.webdemo.video

import androidx.compose.ui.graphics.Color
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.parseVideoEngagement
import dev.openbili.webdemo.api.remainingVideoCoins
import dev.openbili.webdemo.api.videoCoinLimit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoActionStateTest {
  @Test
  fun coinIconUsesOneOrTwoVisibleCoins() {
    assertEquals(1, coinIconCount(0))
    assertEquals(1, coinIconCount(1))
    assertEquals(2, coinIconCount(2))
  }

  @Test
  fun coinLimitMatchesOfficialCopyrightRules() {
    assertEquals(2, videoCoinLimit(1))
    assertEquals(1, videoCoinLimit(2))
    assertEquals(2, videoCoinLimit(3))
    assertEquals(1, videoCoinLimit(0))
    assertEquals(1, videoCoinLimit(99))

    assertEquals(2, remainingVideoCoins(copyright = 1, alreadyCoined = 0))
    assertEquals(1, remainingVideoCoins(copyright = 1, alreadyCoined = 1))
    assertEquals(1, remainingVideoCoins(copyright = 2, alreadyCoined = 0))
    assertEquals(0, remainingVideoCoins(copyright = 2, alreadyCoined = 1))
  }

  @Test
  fun relationStateParsesBooleanAndNumericFields() {
    val booleanState =
      parseVideoEngagement(JSONObject("""{"like":true,"coin":2,"favorite":false}"""))
    assertTrue(booleanState.loaded)
    assertTrue(booleanState.liked)
    assertEquals(2, booleanState.coins)
    assertFalse(booleanState.favorited)

    val numericState =
      parseVideoEngagement(JSONObject("""{"like":0,"coin":1,"favorite":1}"""))
    assertTrue(numericState.loaded)
    assertFalse(numericState.liked)
    assertEquals(1, numericState.coins)
    assertTrue(numericState.favorited)
  }

  @Test(expected = IllegalStateException::class)
  fun incompleteRelationStateIsRejected() {
    parseVideoEngagement(JSONObject("""{"like":true,"coin":1}"""))
  }

  @Test
  fun favoriteMenuKeepsDefaultFirstAndMusicSecond() {
    val folders =
      listOf(
        FavoriteFolder(id = 7L, title = "动画", mediaCount = 2),
        FavoriteFolder(id = 9L, title = "音乐", mediaCount = 3),
        FavoriteFolder(id = 1L, title = "默认收藏夹", mediaCount = 4),
        FavoriteFolder(id = 8L, title = "稍后", mediaCount = 1),
      )

    assertEquals(
      listOf(1L, 9L, 7L, 8L),
      prioritizeVideoFavoriteFolders(folders).map(FavoriteFolder::id),
    )
  }

  @Test
  fun actionPanelDarkensGlassBehindLightForeground() {
    val colors = videoActionPanelGlassColors(Color.White)

    assertEquals(Color.Black.red, colors.container.red, 0f)
    assertTrue(colors.container.alpha >= .58f)
  }

  @Test
  fun actionPanelLightensGlassBehindDarkForeground() {
    val colors = videoActionPanelGlassColors(Color.Black)

    assertEquals(Color.White.red, colors.container.red, 0f)
    assertTrue(colors.container.alpha >= .70f)
  }
}

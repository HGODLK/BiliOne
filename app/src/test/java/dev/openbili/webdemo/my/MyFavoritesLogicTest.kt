package dev.openbili.webdemo.my

import dev.openbili.webdemo.api.FavoriteFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class MyFavoritesLogicTest {
  private val folders =
    listOf(
      FavoriteFolder(id = 1L, title = "默认收藏夹", mediaCount = 3),
      FavoriteFolder(id = 2L, title = "稍后再看", mediaCount = 4),
    )

  @Test
  fun `copy only increments destination folder`() {
    val result = favoriteFoldersAfterAction(folders, 1L, 2L, move = false)

    assertEquals(listOf(3, 5), result.map { it.mediaCount })
  }

  @Test
  fun `move decrements source and increments destination`() {
    val result = favoriteFoldersAfterAction(folders, 1L, 2L, move = true)

    assertEquals(listOf(2, 5), result.map { it.mediaCount })
  }

  @Test
  fun `remove never makes source count negative`() {
    val result =
      favoriteFoldersAfterAction(
        folders = listOf(FavoriteFolder(1L, "空收藏夹", 0)),
        sourceFolderId = 1L,
        destinationFolderId = null,
        move = true,
      )

    assertEquals(0, result.single().mediaCount)
  }

  @Test
  fun `copy is confirmed only when resource remains in source and appears in target`() {
    assertEquals(
      true,
      favoriteActionConfirmed(
        sourceContains = true,
        destinationContains = true,
        hasDestination = true,
        move = false,
      ),
    )
    assertEquals(
      false,
      favoriteActionConfirmed(
        sourceContains = true,
        destinationContains = false,
        hasDestination = true,
        move = false,
      ),
    )
  }

  @Test
  fun `move is confirmed only when resource leaves source and appears in target`() {
    assertEquals(
      true,
      favoriteActionConfirmed(
        sourceContains = false,
        destinationContains = true,
        hasDestination = true,
        move = true,
      ),
    )
    assertEquals(
      false,
      favoriteActionConfirmed(
        sourceContains = true,
        destinationContains = true,
        hasDestination = true,
        move = true,
      ),
    )
  }

  @Test
  fun `remove is confirmed only when resource leaves source`() {
    assertEquals(
      true,
      favoriteActionConfirmed(
        sourceContains = false,
        destinationContains = null,
        hasDestination = false,
        move = true,
      ),
    )
  }
}

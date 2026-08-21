package dev.openbili.webdemo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeMusicRemoteNavigationTest {
  @Test
  fun `first confirmation plays and current item confirmation opens deletion`() {
    assertEquals(
      MusicCardConfirmAction.PLAY,
      resolveMusicCardConfirmAction(itemId = "BV2", currentItemId = "BV1"),
    )
    assertEquals(
      MusicCardConfirmAction.OPEN_DELETE_ACTIONS,
      resolveMusicCardConfirmAction(itemId = "BV1", currentItemId = "BV1"),
    )
  }

  @Test
  fun `right keeps an open transient action layer in place`() {
    assertEquals(
      MusicLibraryRightAction.KEEP_TRANSIENT,
      resolveMusicLibraryRightAction(
        wideLayout = true,
        libraryCollapsed = false,
        transientOpen = true,
      ),
    )
  }

  @Test
  fun `right collapses a visible wide library and targets play pause`() {
    assertEquals(
      MusicLibraryRightAction.COLLAPSE_AND_FOCUS_PLAY_PAUSE,
      resolveMusicLibraryRightAction(true, false, transientOpen = false),
    )
  }

  @Test
  fun `advanced audio right expands a collapsed wide library`() {
    assertEquals(
      MusicAdvancedAudioRightAction.EXPAND_AND_FOCUS_LIBRARY,
      resolveMusicAdvancedAudioRightAction(wideLayout = true, libraryCollapsed = true),
    )
    assertEquals(
      MusicAdvancedAudioRightAction.FOCUS_LIBRARY,
      resolveMusicAdvancedAudioRightAction(wideLayout = true, libraryCollapsed = false),
    )
  }

  @Test
  fun `track focus stays on the same card when playback changes`() {
    assertEquals(
      "BV2",
      resolveMusicTrackFocusId(
        lastFocusedItemId = "BV2",
        currentItemId = "BV3",
        itemIds = listOf("BV1", "BV2", "BV3"),
      ),
    )
    assertEquals(
      "BV3",
      resolveMusicTrackFocusId(
        lastFocusedItemId = "removed",
        currentItemId = "BV3",
        itemIds = listOf("BV1", "BV3"),
      ),
    )
  }

  @Test
  fun `removal keeps the same list slot and falls back at the end`() {
    assertEquals(1, musicFocusIndexAfterRemoval(removedIndex = 1, remainingItemCount = 3))
    assertEquals(2, musicFocusIndexAfterRemoval(removedIndex = 3, remainingItemCount = 3))
    assertEquals(null, musicFocusIndexAfterRemoval(removedIndex = 0, remainingItemCount = 0))
  }
}

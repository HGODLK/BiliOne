package dev.openbili.webdemo.ui

import androidx.compose.ui.geometry.Rect
import dev.openbili.webdemo.api.SpaceDynamicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDynamicRestoreTest {
  @Test
  fun `parent and child profiles keep independent saveable state`() {
    assertNotEquals(profileSaveableStateKey(42L), profileSaveableStateKey(7L))
    assertNotEquals(profileSaveableStateKey(1L, 42L), profileSaveableStateKey(2L, 42L))
  }

  @Test
  fun `profile nesting follows the shared eight layer limit`() {
    assertEquals(MAX_VIDEO_STACK_DEPTH, MAX_PROFILE_STACK_DEPTH)
    assertEquals(8, MAX_PROFILE_STACK_DEPTH)
  }

  @Test
  fun `video profile overlay hides but does not replace retained profile parents`() {
    val parent = ProfileStackEntry(1L, AppRootProfileState())
    val child = ProfileStackEntry(2L, AppRootProfileState())
    val videoCommentProfile =
      ProfileStackEntry(3L, AppRootProfileState(), returnsToVideo = true)
    val grandchild = ProfileStackEntry(4L, AppRootProfileState())
    val retainedStack = listOf(parent, child, videoCommentProfile, grandchild)

    assertEquals(listOf(videoCommentProfile, grandchild), visibleProfileStack(retainedStack))
    assertEquals(listOf(parent, child), retainedStack.dropLast(2))
  }

  @Test
  fun `article profile overlay is rendered as a standalone child`() {
    val articleProfile =
      ProfileStackEntry(1L, AppRootProfileState(), returnsToArticle = true)

    assertEquals(listOf(articleProfile), visibleProfileStack(listOf(articleProfile)))
    assertFalse(rootProfileEntryVisible(null, listOf(articleProfile)))
  }

  @Test
  fun `nested transition keeps parent visible while child crossfades`() {
    assertEquals(1f, parentProfileContentAlpha(0f, nestedTransitionActive = true), 0f)
    assertEquals(1f, parentProfileContentAlpha(1f, nestedTransitionActive = true), 0f)
    assertEquals(0f, profileTransitionContentAlpha(0f), 0f)
    assertEquals(1f, profileTransitionContentAlpha(1f), 0f)
  }

  @Test
  fun `parent header chrome stays hidden until nested profile is removed`() {
    assertFalse(parentProfileHeaderChromeVisible(nestedProfilePresent = true))
    assertTrue(parentProfileHeaderChromeVisible(nestedProfilePresent = false))
  }

  @Test
  fun `closing nested profile restores parent return transition`() {
    val state = AppRootProfileState()
    val parentReturn =
      AvatarProfileTransition(
        token = 1L,
        targetMid = 42L,
        face = "face",
        name = "parent",
        sourceBounds = Rect(0f, 0f, 48f, 48f),
      )
    state.profileMid = 42L
    state.avatarProfileReturnTransition = parentReturn
    val parentSnapshot = state.snapshotProfile(42L)
    state.avatarProfileReturnTransition =
      AvatarProfileTransition(
        token = 2L,
        targetMid = 7L,
        face = "face",
        name = "child",
        sourceBounds = Rect(8f, 8f, 40f, 40f),
      )

    state.restoreProfileReturnTransitions(parentSnapshot)

    assertSame(parentReturn, state.avatarProfileReturnTransition)
  }

  @Test
  fun `video nested profile cannot overwrite parent route back to its video`() {
    val parentReturn =
      AvatarProfileTransition(
        token = 1L,
        targetMid = 42L,
        face = "face",
        name = "parent",
        sourceBounds = Rect(0f, 0f, 48f, 48f),
      )
    val parent = ProfileStackEntry(10L, AppRootProfileState())

    val retained =
      listOf(parent).retainReturnTransitionsFor(
        entryId = parent.entryId,
        commentTransition = null,
        avatarTransition = parentReturn,
      )

    assertSame(parentReturn, retained.single().avatarTransition)
  }

  @Test
  fun `profile snapshot restores opened dynamic and its feed`() {
    val state = AppRootProfileState()
    val dynamic =
      SpaceDynamicItem(
        id = "dynamic-42",
        text = "正文",
        publishTimestamp = 1L,
        authorMid = 42L,
        authorName = "作者",
        authorFace = "",
        commentOid = 42L,
        commentType = 17,
      )
    state.profileMid = 42L
    state.spaceDynamics = listOf(dynamic)
    state.spaceDynamicOffset = "next"
    state.spaceDynamicHasMore = true
    state.selectedDynamicId = dynamic.id

    val snapshot = state.snapshotProfile(42L)
    state.prepareProfile(7L) {}
    state.restoreProfile(snapshot)

    assertEquals(42L, state.profileMid)
    assertEquals(listOf(dynamic), state.spaceDynamics)
    assertEquals("next", state.spaceDynamicOffset)
    assertEquals(true, state.spaceDynamicHasMore)
    assertEquals(dynamic.id, state.selectedDynamicId)
  }
}

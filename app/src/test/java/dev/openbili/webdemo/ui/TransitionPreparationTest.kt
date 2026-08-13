package dev.openbili.webdemo.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionPreparationTest {
  @Test
  fun prepareTimeoutStaysInsideHalfSecondInteractionBudget() {
    assertTrue(TRANSITION_PREPARE_TIMEOUT_MS < 500L)
  }

  @Test
  fun exitCoverDoesNotAppearBeforeVideoToCoverPreludeFinishes() {
    assertFalse(shouldDisplayCardTransitionOverlay(TransitionKind.EXIT_ROOT, SessionPhase.READY))
    assertTrue(shouldDisplayCardTransitionOverlay(TransitionKind.EXIT_ROOT, SessionPhase.FLYING))
    assertTrue(shouldDisplayCardTransitionOverlay(TransitionKind.ENTER_ROOT, SessionPhase.READY))
  }

  @Test
  fun recommendationCoverFlightSuppressesTopSurfaceDanmaku() {
    assertFalse(
      shouldSuppressDanmakuForCardTransition(
        TransitionKind.ENTER_RECOMMENDATION,
        SessionPhase.PREPARING,
      )
    )
    assertTrue(
      shouldSuppressDanmakuForCardTransition(
        TransitionKind.ENTER_RECOMMENDATION,
        SessionPhase.READY,
      )
    )
    assertTrue(
      shouldSuppressDanmakuForCardTransition(
        TransitionKind.ENTER_RECOMMENDATION,
        SessionPhase.FLYING,
      )
    )
    assertFalse(
      shouldSuppressDanmakuForCardTransition(
        TransitionKind.ENTER_ROOT,
        SessionPhase.FLYING,
      )
    )
  }

  @Test
  fun rootExitKeepsVideoPageHiddenWhileCoverFliesHome() {
    assertFalse(shouldHideVideoPageBehindExitCover(TransitionKind.EXIT_ROOT, SessionPhase.READY))
    assertTrue(shouldHideVideoPageBehindExitCover(TransitionKind.EXIT_ROOT, SessionPhase.FLYING))
    assertTrue(
      shouldHideVideoPageBehindExitCover(
        TransitionKind.EXIT_ROOT,
        SessionPhase.REVEALING_BACKGROUND,
      )
    )
    assertFalse(
      shouldHideVideoPageBehindExitCover(TransitionKind.EXIT_RECOMMENDATION, SessionPhase.FLYING)
    )
  }

  @Test
  fun rootEntryBackdropCoversAllRootPlaybackButNotNestedRecommendations() {
    assertTrue(shouldUseRootVideoEntryBackdrop(kind = TransitionKind.ENTER_ROOT))
    assertFalse(shouldUseRootVideoEntryBackdrop(kind = TransitionKind.ENTER_RECOMMENDATION))

    assertEquals(0f, rootVideoEntryContentAlpha(SessionPhase.READY))
    assertEquals(0f, rootVideoEntryContentAlpha(SessionPhase.FLYING))
    assertEquals(1f, rootVideoEntryContentAlpha(SessionPhase.REVEALING_BACKGROUND))
    assertEquals(1f, rootVideoEntryContentAlpha(SessionPhase.WAITING_FIRST_FRAME))
    assertEquals(
      .15625f,
      rootVideoEntryContentAlpha(SessionPhase.REVEALING_BACKGROUND, .25f),
    )
  }

  @Test
  fun profileBangumiKeepsLightweightPosterTargetMountedUntilFlightLands() {
    listOf(SessionPhase.PREPARING, SessionPhase.READY, SessionPhase.FLYING).forEach { phase ->
      assertTrue(
        shouldUseProfileBangumiTransitionTarget(
          sourceProfileEntryId = 7L,
          kind = TransitionKind.ENTER_PROFILE,
          phase = phase,
        )
      )
    }
    assertFalse(
      shouldUseProfileBangumiTransitionTarget(
        sourceProfileEntryId = 7L,
        kind = TransitionKind.ENTER_PROFILE,
        phase = SessionPhase.REVEALING_BACKGROUND,
      )
    )
    assertFalse(
      shouldUseProfileBangumiTransitionTarget(
        sourceProfileEntryId = 0L,
        kind = TransitionKind.ENTER_PROFILE,
        phase = SessionPhase.PREPARING,
      )
    )
    assertFalse(
      shouldUseProfileBangumiTransitionTarget(
        sourceProfileEntryId = 7L,
        kind = TransitionKind.ENTER_ROOT,
        phase = SessionPhase.PREPARING,
      )
    )
  }

  @Test
  fun entryWaitsHaveHardInteractionBounds() {
    assertTrue(MAIN_QUEUE_IDLE_WAIT_TIMEOUT_MS in 1L..499L)
    assertTrue(NAVIGATION_BRING_INTO_VIEW_TIMEOUT_MS in 1L..499L)
  }

  @Test
  fun enteringVideoDefersAuxiliaryPanelsUntilCoverHasLanded() {
    assertTrue(
      shouldDeferVideoAuxiliaryContent(
        preparingRootEnter = false,
        kind = TransitionKind.ENTER_ROOT,
        phase = SessionPhase.FLYING,
      )
    )
    assertFalse(
      shouldDeferVideoAuxiliaryContent(
        preparingRootEnter = false,
        kind = TransitionKind.ENTER_ROOT,
        phase = SessionPhase.REVEALING_BACKGROUND,
      )
    )
    assertFalse(
      shouldDeferVideoAuxiliaryContent(
        preparingRootEnter = false,
        kind = TransitionKind.ENTER_RECOMMENDATION,
        phase = SessionPhase.FLYING,
      )
    )
    assertFalse(
      shouldDeferVideoAuxiliaryContent(
        preparingRootEnter = false,
        kind = TransitionKind.EXIT_ROOT,
        phase = SessionPhase.FLYING,
      )
    )
    assertTrue(
      shouldDeferVideoAuxiliaryContent(
        preparingRootEnter = true,
        kind = null,
        phase = null,
      )
    )
  }

  @Test
  fun rootEntryStagesCommentsWithoutChangingRecommendationNavigation() {
    assertTrue(
      shouldDeferVideoCommentContent(
        deferAllAuxiliaryContent = false,
        kind = TransitionKind.ENTER_ROOT,
        deferRootEnterComments = true,
      )
    )
    assertFalse(
      shouldDeferVideoCommentContent(
        deferAllAuxiliaryContent = false,
        kind = TransitionKind.ENTER_ROOT,
        deferRootEnterComments = false,
      )
    )
    assertFalse(
      shouldDeferVideoCommentContent(
        deferAllAuxiliaryContent = false,
        kind = TransitionKind.ENTER_RECOMMENDATION,
        deferRootEnterComments = true,
      )
    )
  }

  @Test
  fun barrierReleasesOnlyAfterEveryRequiredSignal() = runTest {
    val barrier =
      TransitionPreparationBarrier(
        setOf(TransitionReadySignal.SOURCE_BOUNDS, TransitionReadySignal.TARGET_MOUNTED)
      )

    barrier.markReady(TransitionReadySignal.SOURCE_BOUNDS)
    assertFalse(barrier.isReady())
    assertEquals(setOf(TransitionReadySignal.TARGET_MOUNTED), barrier.pendingSignals())

    barrier.markReady(TransitionReadySignal.TARGET_MOUNTED)
    assertTrue(barrier.isReady())
    assertEquals(TransitionPreparationResult.READY, barrier.await())
  }

  @Test
  fun cancelledBarrierCannotBeReleasedByLateCallback() = runTest {
    val barrier = TransitionPreparationBarrier(setOf(TransitionReadySignal.TARGET_BOUNDS_STABLE))

    barrier.cancel()
    barrier.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)

    assertFalse(barrier.isReady())
    assertEquals(TransitionPreparationResult.CANCELLED, barrier.await())
  }

  @Test
  fun stableBoundsRequireTwoMatchingFramesAfterFirstObservation() {
    val tracker = StableBoundsTracker(requiredMatches = 2)
    val bounds = Rect(10f, 20f, 210f, 120f)

    assertFalse(tracker.observe(bounds))
    assertFalse(tracker.observe(bounds))
    assertTrue(tracker.observe(bounds))
  }

  @Test
  fun changedBoundsRestartStabilityCount() {
    val tracker = StableBoundsTracker(requiredMatches = 2)
    val first = Rect(10f, 20f, 210f, 120f)
    val moved = first.translate(Offset(5f, 0f))

    assertFalse(tracker.observe(first))
    assertFalse(tracker.observe(first))
    assertFalse(tracker.observe(moved))
    assertFalse(tracker.observe(moved))
    assertTrue(tracker.observe(moved))
  }

  @Test
  fun rootExitKeepsFrozenCardTargetWhenLatestMeasurementCollapsesIntoPlayer() {
    val player = Rect(24f, 80f, 824f, 530f)
    val capturedSource = Rect(48f, 660f, 436f, 878f)

    assertEquals(
      capturedSource,
      resolveExitTransitionTargetBounds(
        latest = player,
        fallback = capturedSource,
        playerBounds = player,
      ),
    )
  }

  @Test
  fun rootExitStillUsesAValidFreshCardMeasurement() {
    val player = Rect(24f, 80f, 824f, 530f)
    val capturedSource = Rect(48f, 660f, 436f, 878f)
    val remountedSource = Rect(48f, 410f, 436f, 628f)

    assertEquals(
      remountedSource,
      resolveExitTransitionTargetBounds(
        latest = remountedSource,
        fallback = capturedSource,
        playerBounds = player,
      ),
    )
  }
}

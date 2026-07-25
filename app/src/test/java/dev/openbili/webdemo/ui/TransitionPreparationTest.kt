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

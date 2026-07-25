package dev.openbili.webdemo.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedNavigationBrakeTest {
  @Test
  fun brakeContinuesBrieflyInTheCurrentScrollDirection() {
    assertTrue(estimatedNavigationBrakeDistance(pointerVelocityY = -6_000f, elapsedMs = 0L) > 0f)
    assertTrue(estimatedNavigationBrakeDistance(pointerVelocityY = 6_000f, elapsedMs = 0L) < 0f)
  }

  @Test
  fun brakeDistanceDecaysWhileTapIsResolved() {
    val immediate = estimatedNavigationBrakeDistance(pointerVelocityY = -5_000f, elapsedMs = 0L)
    val delayed = estimatedNavigationBrakeDistance(pointerVelocityY = -5_000f, elapsedMs = 180L)

    assertTrue(delayed > 0f && delayed < immediate)
  }

  @Test
  fun brakeDistanceIsCappedToAvoidASecondFling() {
    assertEquals(180f, estimatedNavigationBrakeDistance(-50_000f, 0L), .001f)
    assertEquals(-180f, estimatedNavigationBrakeDistance(50_000f, 0L), .001f)
  }
}

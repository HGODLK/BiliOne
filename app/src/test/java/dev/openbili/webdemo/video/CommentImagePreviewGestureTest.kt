package dev.openbili.webdemo.video

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentImagePreviewGestureTest {
  @Test
  fun zoomedImagePanKeepsOneToOneViewportDistance() {
    val updated =
      updatedCommentImageTransform(
        currentScale = 3f,
        currentPanOffset = Offset.Zero,
        centroid = Offset(500f, 400f),
        zoomChange = 1f,
        panChange = Offset(120f, -48f),
        contentWidth = 1000f,
        contentHeight = 800f,
        viewportWidth = 1000f,
        viewportHeight = 800f,
      )

    assertEquals(3f, updated.scale, .0001f)
    assertEquals(120f, updated.panOffset.x, .0001f)
    assertEquals(-48f, updated.panOffset.y, .0001f)
  }

  @Test
  fun zoomAroundViewportCenterScalesExistingPan() {
    val updated =
      updatedCommentImageTransform(
        currentScale = 2f,
        currentPanOffset = Offset(100f, -40f),
        centroid = Offset(500f, 400f),
        zoomChange = 1.5f,
        panChange = Offset.Zero,
        contentWidth = 1000f,
        contentHeight = 800f,
        viewportWidth = 1000f,
        viewportHeight = 800f,
      )

    assertEquals(3f, updated.scale, .0001f)
    assertEquals(150f, updated.panOffset.x, .0001f)
    assertEquals(-60f, updated.panOffset.y, .0001f)
  }

  @Test
  fun longImageWaitsForTouchSlopBeforeChoosingDirection() {
    assertEquals(
      CommentImageGestureAxis.UNDECIDED,
      classifyLongCommentImageGesture(Offset(15f, 0f), touchSlop = 16f),
    )
  }

  @Test
  fun longImageOnlyLocksHorizontalForClearlyHorizontalMovement() {
    assertEquals(
      CommentImageGestureAxis.HORIZONTAL,
      classifyLongCommentImageGesture(Offset(40f, 20f), touchSlop = 16f),
    )
    assertEquals(
      CommentImageGestureAxis.VERTICAL,
      classifyLongCommentImageGesture(Offset(40f, 32f), touchSlop = 16f),
    )
    assertEquals(
      CommentImageGestureAxis.VERTICAL,
      classifyLongCommentImageGesture(Offset(12f, 40f), touchSlop = 16f),
    )
  }

  @Test
  fun longImageRequiresMinimumDistanceBeforeChangingPage() {
    assertEquals(0, longCommentImagePageDelta(totalPanX = -47f, minimumDistance = 48f))
    assertEquals(1, longCommentImagePageDelta(totalPanX = -48f, minimumDistance = 48f))
    assertEquals(-1, longCommentImagePageDelta(totalPanX = 48f, minimumDistance = 48f))
  }
}

package dev.openbili.webdemo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleVideoTransitionStateTest {
  @Test
  fun articleOpenedFromVideoOnlyShowsFramesAboveRetainedDepth() {
    assertFalse(
      shouldShowArticleFrame(
        showVideo = true,
        returningVideoToArticle = false,
        retainedArticleDepth = 2,
        frameIndex = 1,
      )
    )
    assertTrue(
      shouldShowArticleFrame(
        showVideo = true,
        returningVideoToArticle = false,
        retainedArticleDepth = 2,
        frameIndex = 2,
      )
    )
  }

  @Test
  fun ordinaryArticleVisibilityStillFollowsVideoLayer() {
    assertTrue(
      shouldShowArticleFrame(
        showVideo = false,
        returningVideoToArticle = false,
        retainedArticleDepth = null,
        frameIndex = 0,
      )
    )
    assertFalse(
      shouldShowArticleFrame(
        showVideo = true,
        returningVideoToArticle = false,
        retainedArticleDepth = null,
        frameIndex = 0,
      )
    )
    assertTrue(
      shouldShowArticleFrame(
        showVideo = true,
        returningVideoToArticle = true,
        retainedArticleDepth = null,
        frameIndex = 0,
      )
    )
  }

  @Test
  fun suspendedVideoIsRestoredAtItsArticleStackBoundary() {
    assertFalse(isReturningToSuspendedVideo(retainedArticleDepth = null, remainingArticleDepth = 0))
    assertFalse(isReturningToSuspendedVideo(retainedArticleDepth = 2, remainingArticleDepth = 3))
    assertTrue(isReturningToSuspendedVideo(retainedArticleDepth = 2, remainingArticleDepth = 2))
  }
}

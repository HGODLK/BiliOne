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

  @Test
  fun onlyArticlesOpenedAboveSuspendedVideoCoverThePlayer() {
    assertFalse(articleCoversSuspendedVideo(articleDepth = 0, retainedArticleDepth = 0))
    assertTrue(articleCoversSuspendedVideo(articleDepth = 1, retainedArticleDepth = 0))
    assertFalse(articleCoversSuspendedVideo(articleDepth = 1, retainedArticleDepth = 1))
    assertTrue(articleCoversSuspendedVideo(articleDepth = 2, retainedArticleDepth = 1))
  }

  @Test
  fun restoredVideoIsNotCoveredWhenSuspensionSnapshotIsCleared() {
    assertFalse(articleCoversSuspendedVideo(articleDepth = 3, retainedArticleDepth = null))
  }
}

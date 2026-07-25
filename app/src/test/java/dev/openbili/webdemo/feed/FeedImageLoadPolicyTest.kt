package dev.openbili.webdemo.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedImageLoadPolicyTest {
  @Test
  fun throttledRowsPermitTheirNestedImages() {
    val policy =
      FeedImageLoadPolicy(
        mode = FeedImageLoadMode.THROTTLED,
        allowedKeys = setOf("article_comment_123", "reply_456"),
      )

    assertTrue(policy.permits("123"))
    assertTrue(policy.permits("456"))
    assertFalse(policy.permits("789"))
  }
}

package dev.openbili.webdemo.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedViewModelTest {
  @Test
  fun `refresh target is four rows of the configured column count`() {
    assertEquals(12, feedRefreshItemCount(3))
    assertEquals(16, feedRefreshItemCount(4))
    assertEquals(24, feedRefreshItemCount(6))
    assertEquals(12, feedRefreshItemCount(2))
  }

  @Test
  fun `preserving refresh prepends unique items and keeps the old feed`() {
    val old = feedItem("old")
    val duplicate = feedItem("old")
    val fresh = feedItem("fresh")

    assertEquals(
      listOf("fresh", "old"),
      mergeRefreshedFeedItems(listOf(old), listOf(duplicate, fresh, fresh)).map { it.id },
    )
  }

  @Test
  fun `unique refresh items can fill the remainder after a capped first page`() {
    val old = feedItem("old")
    val firstPage = (1..12).map { feedItem("fresh-$it") }
    val secondPage = listOf(feedItem("old"), feedItem("fresh-13"), feedItem("fresh-14"))

    val afterFirst = mergeRefreshedFeedItems(listOf(old), firstPage)
    val remaining = uniqueRefreshedFeedItems(afterFirst, secondPage).take(4)

    assertEquals(15, (remaining + afterFirst).size)
    assertEquals(listOf("fresh-13", "fresh-14"), remaining.map { it.id })
  }

  @Test
  fun `pending refresh items are accumulated without changing the visible feed`() {
    val old = feedItem("old")
    val pending = listOf(feedItem("fresh-1"))
    val incoming = listOf(feedItem("fresh-1"), feedItem("fresh-2"))

    assertEquals(
      listOf("fresh-1", "fresh-2"),
      appendUniqueRefreshedFeedItems(listOf(old), pending, incoming).map { it.id },
    )
  }

  private fun feedItem(id: String) =
    FeedItem(
      id = id,
      title = id,
      videoUrl = "https://www.bilibili.com/video/$id",
      coverUrl = "",
      uploader = null,
      playCount = null,
      duration = null,
    )
}

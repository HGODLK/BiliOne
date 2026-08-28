package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchVideoPublishedAtTest {
  @Test
  fun prefersPublishedAtOverSendDate() {
    val item = JSONObject().put("pubdate", 1_700_000_000L).put("senddate", 1_800_000_000L)

    assertEquals(1_700_000_000L, BiliSearchApi.searchVideoPublishedAt(item))
  }

  @Test
  fun fallsBackToSendDateWhenPublishedAtIsMissing() {
    val item = JSONObject().put("senddate", 1_800_000_000L)

    assertEquals(1_800_000_000L, BiliSearchApi.searchVideoPublishedAt(item))
  }
}

package dev.openbili.webdemo.feed

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedItemJsonParserTest {
  @Test
  fun parsesWebViewQuotedEnvelopeAndNormalizesFields() {
    val item =
      JSONObject()
        .put("id", "BV1TEST")
        .put("title", "  一个   视频标题  ")
        .put("videoUrl", "https://m.bilibili.com/video/BV1TEST?tracking=secret#reply")
        .put("coverUrl", "//i0.hdslb.com/bfs/archive/cover.jpg")
        .put("uploader", "测试作者")
        .put("playCount", "12.3万")
        .put("duration", JSONObject.NULL)
    val envelope = successEnvelope(JSONArray().put(item))

    val result = FeedItemJsonParser.parse(JSONObject.quote(envelope.toString()))

    assertTrue(result is FeedExtractionResult.Success)
    val parsed = (result as FeedExtractionResult.Success).items.single()
    assertEquals("BV1TEST", parsed.id)
    assertEquals("一个 视频标题", parsed.title)
    assertEquals("https://www.bilibili.com/video/BV1TEST", parsed.videoUrl)
    assertEquals("https://i0.hdslb.com/bfs/archive/cover.jpg", parsed.coverUrl)
    assertEquals("测试作者", parsed.uploader)
    assertNull(parsed.duration)
  }

  @Test
  fun rejectsUnsafeItemsDeduplicatesAndLimitsResult() {
    val items = JSONArray()
    items.put(feedItem("https://bilibili.com.attacker.example/video/BV_BAD", "bad"))
    items.put(feedItem("https://www.bilibili.com/video/BV_DUP?one=1", "duplicate first"))
    items.put(feedItem("https://www.bilibili.com/video/BV_DUP?two=2", "duplicate second"))
    repeat(45) { index ->
      items.put(feedItem("https://www.bilibili.com/video/BV_$index", "title $index"))
    }

    val result = FeedItemJsonParser.parse(successEnvelope(items).toString())

    assertTrue(result is FeedExtractionResult.Success)
    result as FeedExtractionResult.Success
    assertEquals(40, result.items.size)
    assertEquals(1, result.stats.filteredInvalidVideoUrl)
    assertEquals(1, result.stats.duplicateItems)
  }

  @Test
  fun emptyAndMalformedResponsesAreExplicit() {
    val empty =
      JSONObject()
        .put("status", "empty")
        .put("items", JSONArray())
        .put("stats", JSONObject().put("videoLinksFound", 0))
        .put(
          "error",
          JSONObject()
            .put("code", "NO_VIDEO_LINKS")
            .put("message", "页面尚未出现视频卡片")
            .put("retryable", true),
        )

    assertTrue(
      FeedItemJsonParser.parse(JSONObject.quote(empty.toString())) is FeedExtractionResult.Empty
    )
    val malformed = FeedItemJsonParser.parse("undefined")
    assertTrue(malformed is FeedExtractionResult.Failure)
    assertEquals(
      FeedExtractionErrorCode.INVALID_RESPONSE,
      (malformed as FeedExtractionResult.Failure).code,
    )
  }

  private fun successEnvelope(items: JSONArray): JSONObject =
    JSONObject()
      .put("status", "success")
      .put("items", items)
      .put(
        "stats",
        JSONObject()
          .put("videoLinksFound", items.length())
          .put("parsedItems", items.length())
          .put("uniqueItems", items.length()),
      )
      .put("error", JSONObject.NULL)

  private fun feedItem(videoUrl: String, title: String): JSONObject =
    JSONObject()
      .put("id", title)
      .put("title", title)
      .put("videoUrl", videoUrl)
      .put("coverUrl", "https://i0.hdslb.com/cover.jpg")
}

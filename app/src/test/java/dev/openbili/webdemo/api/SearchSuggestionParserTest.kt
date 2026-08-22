package dev.openbili.webdemo.api

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchSuggestionParserTest {
  @Test
  fun parsesDistinctTermsFromTagArray() {
    val body =
      """
      {
        "code": 0,
        "result": {
          "tag": [
            {"term": "初音未来"},
            {"term": "初音未来的消失"},
            {"term": "初音未来"},
            {"term": ""}
          ]
        }
      }
      """.trimIndent()

    assertEquals(
      listOf("初音未来", "初音未来的消失"),
      BiliSearchApi.parseSearchSuggestionsResponse(body),
    )
  }
}

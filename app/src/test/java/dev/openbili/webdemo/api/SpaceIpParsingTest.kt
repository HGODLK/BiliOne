package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpaceIpParsingTest {
  @Test
  fun parsesPrimarySpaceTag() {
    val card = JSONObject("""{"space_tag":[{"title":"IP属地：广东"}]}""")

    assertEquals("广东", BiliApi.parseSpaceIpLocation(card))
  }

  @Test
  fun fallsBackToBottomSpaceTag() {
    val card = JSONObject("""{"space_tag_bottom":[{"title":"IP属地: 上海"}]}""")

    assertEquals("上海", BiliApi.parseSpaceIpLocation(card))
  }

  @Test
  fun missingTagsStayHidden() {
    assertEquals("", BiliApi.parseSpaceIpLocation(JSONObject()))
  }
}

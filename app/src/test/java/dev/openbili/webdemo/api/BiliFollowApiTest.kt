package dev.openbili.webdemo.api

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BiliFollowApiTest {
  @Test
  fun `following group members use relation tag endpoint`() {
    val url = BiliFollowApi.followingGroupMembersUrl(123L, 2, "attention")

    assertTrue(url.contains("/x/relation/tag?"))
    assertFalse(url.contains("BiliApiCommon.TAG"))
    assertTrue(url.contains("tagid=123"))
    assertTrue(url.contains("pn=2"))
  }

  @Test
  fun `following users read group ids from tag field`() {
    val users =
      BiliFollowApi.parseFollowingUsers(
        JSONArray(
          """
          [{
            "mid": 42,
            "uname": "测试用户",
            "face": "//i.test/avatar.jpg",
            "tag": [1, 7]
          }]
          """
            .trimIndent()
        )
      )

    assertEquals(listOf(1L, 7L), users.single().groupIds)
  }
}

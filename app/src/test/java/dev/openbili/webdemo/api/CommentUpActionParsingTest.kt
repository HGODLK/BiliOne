package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommentUpActionParsingTest {
  @Test
  fun parsesUploaderLikeAndReplyFlags() {
    val comment =
      BiliApi.parseComment(
        JSONObject(
          """
          {
            "rpid": 1,
            "mid": 2,
            "like": 3,
            "rcount": 4,
            "ctime": 5,
            "action": 0,
            "member": {"uname": "测试用户", "avatar": ""},
            "content": {"message": "测试评论"},
            "up_action": {"like": true, "reply": true}
          }
          """
            .trimIndent()
        )
      )

    assertTrue(comment.upLiked)
    assertTrue(comment.upReplied)
  }

  @Test
  fun missingUploaderActionDefaultsToFalse() {
    val comment =
      BiliApi.parseComment(
        JSONObject(
          """
          {
            "rpid": 1,
            "mid": 2,
            "like": 0,
            "rcount": 0,
            "ctime": 5,
            "member": {},
            "content": {"message": ""}
          }
          """
            .trimIndent()
        )
      )

    assertFalse(comment.upLiked)
    assertFalse(comment.upReplied)
  }

  @Test
  fun parsesInstitutionCertificationFromCommentMember() {
    val comment =
      BiliApi.parseComment(
        JSONObject(
          """
          {
            "rpid": 1,
            "mid": 2,
            "ctime": 5,
            "member": {
              "uname": "认证账号",
              "official_verify": {"type": 1, "desc": "认证服务官方账号"}
            },
            "content": {"message": "测试评论"}
          }
          """
            .trimIndent()
        )
      )

    assertTrue(comment.officialVerification.verified)
    assertEquals(1, comment.officialVerification.type)
    assertEquals("认证服务官方账号", comment.officialVerification.description)
  }

  @Test
  fun profileCertificationPrefersCurrentTitleField() {
    val verification =
      BiliApi.parseOfficialVerification(
        primary = JSONObject("""{"type":0,"title":"哔哩哔哩知名UP主","desc":"旧说明"}"""),
        legacy = JSONObject("""{"type":1,"desc":"旧版认证"}"""),
      )

    assertEquals(0, verification.type)
    assertEquals("哔哩哔哩知名UP主", verification.description)
  }

  @Test
  fun listedUserCertificationReadsStructuredFollowingField() {
    val verification =
      BiliApi.parseListedUserOfficialVerification(
        JSONObject("""{"official_verify":{"type":0,"desc":"知名UP主"}}""")
      )

    assertEquals(0, verification.type)
    assertEquals("知名UP主", verification.description)
  }

  @Test
  fun listedUserCertificationFallsBackToSearchVerifyInfo() {
    val verification =
      BiliApi.parseListedUserOfficialVerification(
        JSONObject("""{"verify_info":"哔哩哔哩官方账号"}""")
      )

    assertEquals(1, verification.type)
    assertEquals("哔哩哔哩官方账号", verification.description)
  }
}

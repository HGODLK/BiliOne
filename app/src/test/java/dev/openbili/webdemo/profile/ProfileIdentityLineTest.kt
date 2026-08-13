package dev.openbili.webdemo.profile

import dev.openbili.webdemo.api.OfficialVerification
import dev.openbili.webdemo.api.SpaceProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileIdentityLineTest {
  @Test
  fun authorizedIpFollowsCertificationOnTheSameLine() {
    val profile =
      profile(
        verification = OfficialVerification(type = 1, description = "认证服务官方账号"),
        ipLocation = "北京",
      )

    assertEquals(
      "机构认证：认证服务官方账号 · IP属地：北京",
      profileIdentityLine(profile, profileIpAuthorized = true),
    )
  }

  @Test
  fun authorizedIpOccupiesIdentityLineWhenCertificationIsMissing() {
    assertEquals(
      "IP属地：上海",
      profileIdentityLine(profile(ipLocation = "上海"), profileIpAuthorized = true),
    )
  }

  @Test
  fun unauthorizedIpValueIsNeverRendered() {
    val profile =
      profile(
        verification = OfficialVerification(type = 0, description = "知名UP主"),
        ipLocation = "广东",
      )

    assertEquals("个人认证：知名UP主", profileIdentityLine(profile, profileIpAuthorized = false))
  }

  private fun profile(
    verification: OfficialVerification = OfficialVerification(),
    ipLocation: String = "",
  ) =
    SpaceProfile(
      mid = 1L,
      name = "测试用户",
      face = "",
      banner = "",
      signature = "",
      followerCount = 0L,
      followingCount = 0L,
      officialVerification = verification,
      ipLocation = ipLocation,
    )
}

package dev.openbili.webdemo.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileFormattingTest {
  @Test
  fun followerCountBelowTenThousandStaysExact() {
    assertEquals("9999", formatProfileFollowerCount(9_999))
  }

  @Test
  fun followerCountUsesOneDecimalWanUnit() {
    assertEquals("2.9w", formatProfileFollowerCount(28_993))
  }

  @Test
  fun followerCountDropsRedundantDecimal() {
    assertEquals("10w", formatProfileFollowerCount(99_999))
  }

  @Test
  fun signatureLineBreaksBecomeSingleSpaces() {
    assertEquals("第一行 第二行 第三行", formatProfileSignature("第一行\r\n第二行\n第三行"))
  }

  @Test
  fun signatureTrimsWhitespaceCreatedByLineBreaks() {
    assertEquals("简介", formatProfileSignature("\n  简介  \r"))
  }

  @Test
  fun headerCapsuleExpandsHidesAndRestoresFromConfirmedEvents() {
    assertEquals(
      ProfileHeaderInfoState.EXPANDED,
      reduceProfileHeaderInfoState(
        ProfileHeaderInfoState.COLLAPSED,
        ProfileHeaderInfoEvent.CAPSULE_TAP,
      ),
    )
    assertEquals(
      ProfileHeaderInfoState.HIDDEN,
      reduceProfileHeaderInfoState(
        ProfileHeaderInfoState.COLLAPSED,
        ProfileHeaderInfoEvent.BANNER_TAP,
      ),
    )
    assertEquals(
      ProfileHeaderInfoState.COLLAPSED,
      reduceProfileHeaderInfoState(
        ProfileHeaderInfoState.EXPANDED,
        ProfileHeaderInfoEvent.OTHER_OPERATION,
      ),
    )
    assertEquals(
      ProfileHeaderInfoState.COLLAPSED,
      reduceProfileHeaderInfoState(
        ProfileHeaderInfoState.HIDDEN,
        ProfileHeaderInfoEvent.OTHER_OPERATION,
      ),
    )
  }
}

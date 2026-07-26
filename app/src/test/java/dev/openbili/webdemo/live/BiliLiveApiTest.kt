package dev.openbili.webdemo.live

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BiliLiveApiTest {
  @Test
  fun interactiveLotteryParsesFreeDanmakuParticipation() {
    val lottery =
      requireNotNull(
        BiliLiveApi.parseInteractiveLottery(
          JSONObject(
            """
            {
              "id": 88,
              "room_id": 99,
              "award_name": "测试奖品",
              "award_num": 2,
              "award_image": "//i0.hdslb.com/award.png",
              "danmu": "259",
              "require_text": "发送 259 参与",
              "current_time": 1000,
              "time": 60,
              "status": 0
            }
            """
          )
        )
      )

    assertEquals(88L, lottery.id)
    assertEquals(99L, lottery.roomId)
    assertEquals("259", lottery.command)
    assertEquals(1_060_000L, lottery.endAtEpochMs)
    assertEquals(LiveLotteryStatus.ACTIVE, lottery.status)
    assertFalse(lottery.requiresPayment)
  }

  @Test
  fun interactiveLotteryTreatsAnyGiftConditionAsPaid() {
    val lottery =
      requireNotNull(
        BiliLiveApi.parseInteractiveLottery(
          JSONObject(
            """
            {
              "id": 1,
              "room_id": 2,
              "gift_id": 30000,
              "gift_num": 0,
              "current_time": 100,
              "time": 10,
              "status": 2
            }
            """
          )
        )
      )

    assertTrue(lottery.requiresPayment)
    assertEquals(LiveLotteryStatus.JOINED, lottery.status)
  }

  @Test
  fun roomPlayInfoUsesTheCompleteSignedWebPlayerParameterSet() {
    val parameters = BiliLiveApi.playInfoRequestParameters(roomId = 42L, qn = 20_000)

    assertEquals("42", parameters["room_id"])
    assertEquals("10000", parameters["qn"])
    assertEquals("0,1,2", parameters["codec"])
    assertEquals("5", parameters["dolby"])
    assertEquals("1", parameters["panorama"])
    assertEquals("0,1,2", parameters["eotf"])
    assertEquals("0", parameters["req_reason"])
    assertEquals("0,1,2,3", parameters["supported_drms"])
  }
}

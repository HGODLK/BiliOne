package dev.openbili.webdemo.live

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BiliLiveApiTest {
  @Test
  fun liveAreasParseOnlyTheParentCategoryLevel() {
    val areas =
      BiliLiveApi.parseLiveAreas(
        JSONObject(
          """
          {
            "code": 0,
            "data": [
              {"id": 2, "name": "网游", "list": [{"id": 86, "name": "英雄联盟"}]},
              {"id": 9, "name": "虚拟主播", "list": []}
            ]
          }
          """
        )
      )

    assertEquals(listOf(2, 9), areas.map { it.parentAreaId })
    assertEquals(listOf("网游", "虚拟主播"), areas.map { it.name })
    assertTrue(areas.all { it.areaId == 0 })
  }

  @Test
  fun liveAreaGroupsKeepChildrenForTheIndexPage() {
    val groups =
      BiliLiveApi.parseLiveAreaGroups(
        JSONObject(
          """
          {
            "data": [
              {
                "id": 2,
                "name": "网游",
                "pic": "//i0.hdslb.com/parent.png",
                "list": [
                  {"id": 86, "name": "英雄联盟", "pic": "//i0.hdslb.com/child.png"}
                ]
              }
            ]
          }
          """
        )
      )

    assertEquals(1, groups.size)
    assertEquals("网游", groups.single().parent.name)
    assertEquals("https://i0.hdslb.com/parent.png", groups.single().parent.iconUrl)
    assertEquals("2:86", groups.single().children.single().stableId)
    assertEquals("https://i0.hdslb.com/child.png", groups.single().children.single().iconUrl)
  }

  @Test
  fun liveHomeRecommendationsParseTheWebTopList() {
    val rooms =
      BiliLiveApi.parseLiveHomeRecommendations(
        JSONObject(
          """
          {
            "data": {
              "recommend_room_list": [
                {
                  "roomid": 5050,
                  "uid": 433351,
                  "title": "推荐直播",
                  "uname": "测试主播",
                  "cover": "//i0.hdslb.com/cover.jpg",
                  "keyframe": "//i0.hdslb.com/keyframe.jpg",
                  "watched_show": {"text_small": "2.5万人气"}
                }
              ]
            }
          }
          """
        )
      )

    assertEquals(1, rooms.size)
    assertEquals(5050L, rooms.single().roomId)
    assertEquals("2.5万人气", rooms.single().watchedText)
    assertTrue(rooms.single().keyframeUrl.orEmpty().contains("keyframe.jpg"))
  }

  @Test
  fun liveHomeRecommendationsPreferHeroListWhenHomepageAlsoContainsFollowing() {
    val rooms =
      BiliLiveApi.parseLiveHomeRecommendations(
        JSONObject(
          """
          {
            "data": {
              "recommend_room_list": [
                {
                  "roomid": 5050,
                  "uid": 433351,
                  "title": "网页顶部推荐",
                  "uname": "推荐主播",
                  "status": 1
                }
              ],
              "room_list": [
                {
                  "module_info": {"id": 13},
                  "list": [
                    {
                      "roomid": 6060,
                      "title": "我的关注",
                      "uname": "关注主播",
                      "status": 1
                    }
                  ]
                }
              ]
            }
          }
          """
        )
      )

    assertEquals(listOf(5050L), rooms.map { it.roomId })
    assertEquals("网页顶部推荐", rooms.single().title)
  }

  @Test
  fun followedLiveRoomsParseHomepageFollowModuleAndOnlyLiveRooms() {
    val rooms =
      BiliLiveApi.parseFollowedLiveRooms(
        JSONObject(
          """
          {
            "data": {
              "room_list": [
                {
                  "module_info": {"id": 3},
                  "list": [{"roomid": 9, "title": "其他模块", "status": true}]
                },
                {
                  "module_info": {"id": 13},
                  "extra": {"follow_Online": 1},
                  "list": [
                    {
                      "roomid": 1,
                      "title": "正在直播",
                      "uid": 99,
                      "uname": "测试主播",
                      "status": true
                    },
                    {
                      "roomid": 2,
                      "title": "已下播",
                      "uid": 100,
                      "uname": "测试主播2",
                      "status": false
                    }
                  ]
                }
              ]
            }
          }
          """
        )
      )

    assertEquals(listOf(1L), rooms.map { it.roomId })
  }

  @Test
  fun followedLiveRoomsKeepCompatibilityWithLegacyRoomsPayload() {
    val rooms =
      BiliLiveApi.parseFollowedLiveRooms(
        JSONObject(
          """
          {
            "data": {
              "rooms": [
                {
                  "roomid": 1,
                  "title": "正在直播",
                  "live_status": 1,
                  "user_cover": "",
                  "cover_from_user": "//i0.hdslb.com/follow-cover.jpg",
                  "keyframe": "",
                  "pic": "//i0.hdslb.com/follow-keyframe.jpg"
                },
                {"roomid": 2, "title": "已下播", "live_status": 0}
              ]
            }
          }
          """
        )
      )

    assertEquals(listOf(1L), rooms.map { it.roomId })
    assertEquals("https://i0.hdslb.com/follow-cover.jpg", rooms.single().coverUrl)
    assertEquals("https://i0.hdslb.com/follow-keyframe.jpg", rooms.single().keyframeUrl)
  }

  @Test
  fun followedLiveRoomsMergeFullMetadataWithoutChangingHomepageOrder() {
    val homepageRooms =
      listOf(
        LiveSearchRoom(roomId = 1L, uid = 11L, title = "首页第一", uname = "主播一"),
        LiveSearchRoom(roomId = 2L, uid = 22L, title = "首页第二", uname = "主播二"),
      )
    val fullRooms =
      listOf(
        LiveSearchRoom(
          roomId = 2L,
          uid = 22L,
          title = "完整第二",
          uname = "主播二",
          coverUrl = "https://i0.hdslb.com/second.jpg",
        ),
        LiveSearchRoom(
          roomId = 3L,
          uid = 33L,
          title = "完整第三",
          uname = "主播三",
          coverUrl = "https://i0.hdslb.com/third.jpg",
        ),
      )

    val merged = BiliLiveApi.mergeFollowedLiveRooms(homepageRooms, fullRooms)

    assertEquals(listOf(1L, 2L, 3L), merged.map { it.roomId })
    assertEquals("首页第二", merged[1].title)
    assertEquals("https://i0.hdslb.com/second.jpg", merged[1].coverUrl)
  }

  @Test
  fun followedLiveOnlineCountReadsHomepageAndLegacyTotals() {
    val homepage =
      JSONObject(
        """
        {
          "data": {
            "room_list": [
              {
                "module_info": {"id": 13},
                "extra": {"follow_Online": 10},
                "list": []
              }
            ]
          }
        }
        """
      )
    val legacy = JSONObject("""{"data":{"count":12,"rooms":[]}}""")

    assertEquals(10, BiliLiveApi.parseFollowedLiveOnlineCount(homepage))
    assertEquals(12, BiliLiveApi.parseFollowedLiveOnlineCount(legacy))
  }

  @Test
  fun liveRoomListParsesCompatibilityResponseAndPaging() {
    val response =
      BiliLiveApi.parseLiveRoomList(
        json =
          JSONObject(
            """
            {
              "code": 0,
              "data": {
                "count": 31,
                "list": [
                  {
                    "roomid": 5050,
                    "uid": 433351,
                    "title": "测试直播间",
                    "uname": "测试主播",
                    "online": 123456,
                    "user_cover": "//i0.hdslb.com/cover.jpg",
                    "system_cover": "//i0.hdslb.com/keyframe.jpg",
                    "face": "//i0.hdslb.com/face.jpg",
                    "area_v2_parent_id": 1,
                    "area_v2_id": 2,
                    "area_v2_parent_name": "单机游戏",
                    "area_v2_name": "主机游戏"
                  }
                ]
              }
            }
            """
          ),
        page = 1,
        pageSize = 30,
      )

    assertTrue(response.hasMore)
    assertEquals(1, response.rooms.size)
    val room = response.rooms.single()
    assertEquals(5050L, room.roomId)
    assertEquals(1, room.parentAreaId)
    assertEquals(2, room.areaId)
    assertEquals("单机游戏", room.parentAreaName)
    assertEquals("主机游戏", room.areaName)
    assertEquals("12.3万人气", room.watchedText)
    assertTrue(room.keyframeUrl.orEmpty().contains("keyframe.jpg"))
  }

  @Test
  fun liveRoomListStopsPagingAtTheReportedEnd() {
    val response =
      BiliLiveApi.parseLiveRoomList(
        JSONObject("""{"data":{"count":31,"list":[{"roomid":1,"title":"直播"}]}}"""),
        page = 2,
        pageSize = 30,
      )

    assertFalse(response.hasMore)
  }

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

  @Test
  fun baseEmojiKeepsInputTextSeparateFromTheDirectSendToken() {
    val packs =
      BiliLiveApi.parseEmojiPacks(
        JSONObject(
          """
          {
            "data": {
              "data": [
                {
                  "pkg_id": 1,
                  "pkg_name": "经典",
                  "emoticons": [
                    {
                      "emoji": "[哇]",
                      "emoticon_unique": "emoji_208",
                      "descript": "哇",
                      "url": "//i0.hdslb.com/emoji.png",
                      "perm": 1,
                      "bulge_display": 0
                    }
                  ]
                }
              ]
            }
          }
          """
        ),
        roomId = 4006440L,
      )

    val emoji = packs.single().emojis.single()
    assertEquals(LiveEmojiKind.BASE, emoji.kind)
    assertEquals("[哇]", emoji.inputText)
    assertEquals("emoji_208", emoji.sendToken)
    assertTrue(emoji.directSend)
    val fields = BiliLiveApi.emojiDanmakuFields(emoji)
    assertEquals("emoji_208", fields["msg"])
    assertEquals("1", fields["dm_type"])
    assertEquals("{}", fields["emoticon_options"])
  }

  @Test
  fun roomEmojiStillUsesItsUniqueTokenForDirectSend() {
    val packs =
      BiliLiveApi.parseEmojiPacks(
        JSONObject(
          """
          {
            "data": {
              "data": [
                {
                  "pkg_id": 2,
                  "pkg_name": "房间专属",
                  "emoticons": [
                    {
                      "emoji": "哇酷哇酷",
                      "emoticon_unique": "room_4006440_17346",
                      "url": "//i0.hdslb.com/room-emoji.png",
                      "perm": 1,
                      "bulge_display": 1
                    }
                  ]
                }
              ]
            }
          }
          """
        ),
        roomId = 4006440L,
      )

    val emoji = packs.single().emojis.single()
    assertEquals(LiveEmojiKind.ROOM_EXCLUSIVE, emoji.kind)
    assertEquals("哇酷哇酷", emoji.inputText)
    assertEquals("room_4006440_17346", emoji.sendToken)
    assertTrue(emoji.directSend)
  }

  @Test
  fun liveSourcesTryDifferentFormatsOnTheSameCdnBeforeTheNextCdn() {
    val sources =
      listOf(
        LiveStreamSource("https://cdn1/live-fmp4", LiveStreamFormat.HLS_FMP4, "avc", 1),
        LiveStreamSource("https://cdn0/live-ts", LiveStreamFormat.HLS_TS, "avc", 0),
        LiveStreamSource("https://cdn0/live-fmp4", LiveStreamFormat.HLS_FMP4, "avc", 0),
        LiveStreamSource("https://cdn0/live-flv", LiveStreamFormat.HTTP_FLV, "avc", 0),
        LiveStreamSource("https://cdn0/live-hevc", LiveStreamFormat.HLS_FMP4, "hevc", 0),
      )

    assertEquals(
      listOf(
        "https://cdn0/live-fmp4",
        "https://cdn0/live-ts",
        "https://cdn0/live-flv",
        "https://cdn1/live-fmp4",
        "https://cdn0/live-hevc",
      ),
      BiliLiveApi.orderLiveSources(sources).map(LiveStreamSource::url),
    )
  }
}

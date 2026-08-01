package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BangumiFollowProgressParsingTest {

  @Test
  fun `merged following card prefers the watched episode cover`() {
    val followed =
      SpaceContentCard(
        id = "bangumi:season-1",
        title = "番剧",
        coverUrl = "https://example.com/season.jpg",
        seasonId = 1L,
        seasonType = 1,
        kind = SpaceContentKind.BANGUMI,
      )
    val history =
      SpaceContentCard(
        id = "history:pgc:ep-2",
        title = "第 2 集",
        historyCoverUrl = "https://example.com/episode-2.jpg",
        seasonId = 1L,
        episodeId = 2L,
        seasonType = 1,
        kind = SpaceContentKind.BANGUMI,
      )

    val merged = BiliApi.mergeBangumiWatchingCards(listOf(followed), listOf(history), 1)

    assertEquals("https://example.com/episode-2.jpg", merged.single().coverUrl)
  }

  @Test
  fun `guochuang history is sorted by the latest view time`() {
    val older =
      SpaceContentCard(
        id = "history:pgc:ep1",
        title = "较早观看",
        seasonId = 1L,
        episodeId = 11L,
        seasonType = 4,
        kind = SpaceContentKind.BANGUMI,
        lastViewedAt = 100L,
      )
    val newer =
      SpaceContentCard(
        id = "history:pgc:ep2",
        title = "最近观看",
        seasonId = 2L,
        episodeId = 22L,
        seasonType = 4,
        kind = SpaceContentKind.BANGUMI,
        lastViewedAt = 200L,
      )

    val merged =
      BiliApi.mergeBangumiWatchingCards(
        followed = emptyList(),
        history = listOf(older, newer),
        seasonType = 4,
      )

    assertEquals(listOf(2L, 1L), merged.map(SpaceContentCard::seasonId))
  }

  @Test
  fun `follow only card keeps server watch progress for episode cover resolution`() {
    val progress = BangumiWatchProgress(episodeId = 22L, episodeIndex = "2", positionMs = 30_000L)
    val followed =
      SpaceContentCard(
        id = "bangumi:2",
        title = "有服务端进度",
        coverUrl = "https://example.com/season.jpg",
        seasonId = 2L,
        episodeId = 99L,
        watchProgress = progress,
        seasonType = 1,
        kind = SpaceContentKind.BANGUMI,
      )

    val merged =
      BiliApi.mergeBangumiWatchingCards(
        followed = listOf(followed),
        history = emptyList(),
        seasonType = 1,
      ).single()

    assertEquals(22L, merged.episodeId)
    assertEquals(progress, merged.watchProgress)
    assertEquals("https://www.bilibili.com/bangumi/play/ep22", merged.videoUrl)
  }

  @Test
  fun `web player history uses milliseconds and title field`() {
    val row = JSONObject("""
      {
        "user_season": {
          "last_ep_id": 469261,
          "last_ep_index_title": "152",
          "last_ep_progress": 103000
        }
      }
    """.trimIndent())

    val progress = BiliApi.parseBangumiWatchProgress(row)
    assertNotNull(progress)
    assertEquals(469261L, progress!!.episodeId)
    assertEquals("152", progress.episodeIndex)
    assertEquals(103_000L, progress.positionMs)
  }

  @Test
  fun `web player fields take precedence over legacy fields`() {
    val row = JSONObject("""
      {
        "user_season": {
          "last_ep_id": 469261,
          "last_ep_index_title": "152",
          "last_ep_index": "151",
          "last_ep_progress": 103000,
          "last_time": 12
        }
      }
    """.trimIndent())

    val progress = BiliApi.parseBangumiWatchProgress(row)
    assertNotNull(progress)
    assertEquals("152", progress!!.episodeIndex)
    assertEquals(103_000L, progress.positionMs)
  }

  // ── parseBangumiWatchProgress ─────────────────────────────────────────

  @Test
  fun `inline user_season with last_ep_id and numeric progress`() {
    val row = JSONObject("""
      {
        "season_id": 12345,
        "progress": 7,
        "user_season": {
          "last_ep_id": 469261,
          "last_ep_index": "152",
          "last_time": 103
        }
      }
    """.trimIndent())

    val progress = BiliApi.parseBangumiWatchProgress(row)
    assertNotNull(progress)
    assertEquals(469261L, progress!!.episodeId)
    assertEquals("152", progress.episodeIndex)
    assertEquals(103_000L, progress.positionMs)
    assertEquals(7, progress.percent)
  }

  @Test
  fun `inline user_season with string last_time`() {
    val row = JSONObject("""
      {
        "season_id": 12345,
        "user_season": {
          "last_ep_id": 469261,
          "last_ep_index": "152",
          "last_time": "103"
        }
      }
    """.trimIndent())

    val progress = BiliApi.parseBangumiWatchProgress(row)
    assertNotNull(progress)
    assertEquals(469261L, progress!!.episodeId)
    assertEquals(103_000L, progress.positionMs)
  }

  @Test
  fun `progress as percentage string`() {
    val row = JSONObject("""
      {
        "season_id": 12345,
        "progress": "7%",
        "user_season": {
          "last_ep_id": 469261,
          "last_ep_index": "152",
          "last_time": 103
        }
      }
    """.trimIndent())

    val progress = BiliApi.parseBangumiWatchProgress(row)
    assertNotNull(progress)
    assertEquals(7, progress!!.percent)
  }

  @Test
  fun `progress as JSONObject must not be parsed as percentage`() {
    val row = JSONObject("""
      {
        "season_id": 12345,
        "progress": {"some": "object"},
        "user_season": {
          "last_ep_id": 469261,
          "last_ep_index": "152",
          "last_time": 103
        }
      }
    """.trimIndent())

    val progress = BiliApi.parseBangumiWatchProgress(row)
    assertNotNull(progress)
    // When progress is a JSONObject, percent should be null (not mistakenly parsed)
    assertNull(progress!!.percent)
  }

  @Test
  fun `no user_season returns null`() {
    val row = JSONObject("""
      {
        "season_id": 12345,
        "new_ep": {"id": 999, "index_show": "更新至第161话"}
      }
    """.trimIndent())

    assertNull(BiliApi.parseBangumiWatchProgress(row))
  }

  @Test
  fun `unwatched season has no progress`() {
    val row = JSONObject("""
      {
        "season_id": 67890,
        "progress": 0,
        "user_season": {
          "last_ep_id": 0,
          "last_ep_index": "",
          "last_time": 0
        }
      }
    """.trimIndent())

    // last_ep_id = 0 means invalid, should return null
    assertNull(BiliApi.parseBangumiWatchProgress(row))
  }

  @Test
  fun `negative percent is clamped`() {
    val row = JSONObject("""
      {
        "season_id": 12345,
        "progress": -5,
        "user_season": {
          "last_ep_id": 469261,
          "last_ep_index": "152",
          "last_time": 103
        }
      }
    """.trimIndent())

    val progress = BiliApi.parseBangumiWatchProgress(row)
    assertNotNull(progress)
    val pct = progress!!.percent
    assertNotNull(pct)
    assertTrue(pct!! >= 0 && pct <= 100)
  }

  @Test
  fun `percent over 100 is clamped`() {
    val row = JSONObject("""
      {
        "season_id": 12345,
        "progress": 150,
        "user_season": {
          "last_ep_id": 469261,
          "last_ep_index": "152",
          "last_time": 103
        }
      }
    """.trimIndent())

    val progress = BiliApi.parseBangumiWatchProgress(row)
    assertNotNull(progress)
    assertEquals(100, progress!!.percent)
  }

  @Test
  fun `last_ep_id is zero invalidates whole record`() {
    val row = JSONObject("""
      {
        "season_id": 12345,
        "user_season": {
          "last_ep_id": "0",
          "last_ep_index": "",
          "last_time": 0
        }
      }
    """.trimIndent())

    assertNull(BiliApi.parseBangumiWatchProgress(row))
  }

  @Test
  fun `parse season slash user status response`() {
    val json = JSONObject("""
      {
        "code": 0,
        "message": "success",
        "result": {
          "follow": 1,
          "follow_status": 1,
          "login": true,
          "progress": {
            "last_ep_id": 469261,
            "last_ep_index": "152",
            "last_time": 103
          }
        }
      }
    """.trimIndent())

    val progress = BiliApi.parseBangumiWatchProgress(json)
    assertNotNull(progress)
    assertEquals(469261L, progress!!.episodeId)
    assertEquals("152", progress.episodeIndex)
    assertEquals(103_000L, progress.positionMs)
  }

  @Test
  fun `parse watch_progress as fallback key`() {
    val json = JSONObject("""
      {
        "result": {
          "login": true,
          "watch_progress": {
            "last_ep_id": "555",
            "last_ep_index": "SP1",
            "last_time": 42
          }
        }
      }
    """.trimIndent())

    val progress = BiliApi.parseBangumiWatchProgress(json)
    assertNotNull(progress)
    assertEquals(555L, progress!!.episodeId)
    assertEquals("SP1", progress.episodeIndex)
    assertEquals(42_000L, progress.positionMs)
  }

  // ── parseSpaceBangumiResponse ────────────────────────────────────────

  @Test
  fun `parseSpaceBangumiResponse extracts inline watch progress`() {
    val json = JSONObject("""
      {
        "code": 0,
        "data": {
          "list": [
            {
              "season_id": 12345,
              "title": "Test Anime",
              "cover": "//i0.hdslb.com/test.jpg",
              "new_ep": {"id": 999, "index_show": "更新至第161话"},
              "progress": 7,
              "user_season": {
                "last_ep_id": 469261,
                "last_ep_index": "152",
                "last_time": 103
              }
            }
          ],
          "total": 1,
          "pn": 1,
          "ps": 10
        }
      }
    """.trimIndent())

    val response = BiliApi.parseSpaceBangumiResponse(json, type = 1, page = 1, pageSize = 10)

    assertEquals(1, response.cards.size)
    val card = response.cards[0]
    assertEquals("Test Anime", card.title)
    assertEquals(999L, card.episodeId)
    assertEquals(12345L, card.seasonId)
    val wp = card.watchProgress
    assertNotNull(wp)
    assertEquals(469261L, wp!!.episodeId)
    assertEquals("152", wp.episodeIndex)
    assertEquals(103_000L, wp.positionMs)
    assertEquals(7, wp.percent)
  }

  @Test
  fun `parseSpaceBangumiResponse card without progress gets null watchProgress`() {
    val json = JSONObject("""
      {
        "code": 0,
        "data": {
          "list": [
            {
              "season_id": 67890,
              "title": "Unwatched Anime",
              "cover": "//i0.hdslb.com/test2.jpg",
              "new_ep": {"id": 888, "index_show": "更新至第10话"}
            }
          ],
          "total": 1,
          "pn": 1,
          "ps": 10
        }
      }
    """.trimIndent())

    val response = BiliApi.parseSpaceBangumiResponse(json, type = 1, page = 1, pageSize = 10)

    assertEquals(1, response.cards.size)
    assertNull(response.cards[0].watchProgress)
    assertEquals(888L, response.cards[0].episodeId)
  }

  @Test
  fun `parseSpaceBangumiResponse card stableId does not change with progress`() {
    val json = JSONObject("""
      {
        "code": 0,
        "data": {
          "list": [
            {
              "season_id": 12345,
              "title": "Stable Anime",
              "cover": "//i0.hdslb.com/stable.jpg",
              "new_ep": {"id": 999, "index_show": "更新至第161话"},
              "user_season": {
                "last_ep_id": 469261,
                "last_ep_index": "152",
                "last_time": 103
              }
            }
          ],
          "total": 1,
          "pn": 1,
          "ps": 10
        }
      }
    """.trimIndent())

    val response = BiliApi.parseSpaceBangumiResponse(json, type = 1, page = 1, pageSize = 10)

    assertEquals(1, response.cards.size)
    val card = response.cards[0]
    // Stable ID derives from seasonId, not episodeId
    assertTrue(card.id.startsWith("bangumi:"))
    assertEquals(999L, card.episodeId) // new_ep.id, not watchProgress.episodeId
  }

  @Test
  fun `watching merge keeps every loaded season instead of truncating the rail`() {
    val followed =
      (1L..8L).map { seasonId ->
        SpaceContentCard(
          id = "bangumi:$seasonId",
          title = "番剧 $seasonId",
          seasonId = seasonId,
          seasonType = 1,
          kind = SpaceContentKind.BANGUMI,
        )
      }

    val merged = BiliApi.mergeBangumiWatchingCards(followed, emptyList(), seasonType = 1)

    assertEquals(8, merged.size)
    assertEquals(followed.map(SpaceContentCard::seasonId), merged.map(SpaceContentCard::seasonId))
  }
}

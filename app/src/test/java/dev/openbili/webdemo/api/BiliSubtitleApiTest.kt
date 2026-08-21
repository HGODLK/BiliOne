package dev.openbili.webdemo.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BiliSubtitleApiTest {
  @Test
  fun parsesCurrentWebSubtitleCatalogAndDecodesItsBoundSourceUrl() {
    val catalog =
      BiliSubtitleApi.parseCatalog(
        bytes = CURRENT_SUBTITLE_REPLY,
        aid = 117013062224541L,
        cid = 40463961475L,
        bvid = "BV1KFGc6jEzQ",
      )

    assertFalse(catalog.loginRequired)
    assertEquals(117013062224541L, catalog.aid)
    assertEquals(40463961475L, catalog.cid)
    assertEquals("BV1KFGc6jEzQ", catalog.bvid)
    assertEquals(1, catalog.tracks.size)
    with(catalog.tracks.single()) {
      assertEquals("2074732943640842496", id)
      assertEquals("ai-zh", language)
      assertTrue(
        sourceUrl.startsWith(
          "https://aisubtitle.hdslb.com/bfs/ai_subtitle/prod/" + "11701306222454140463961475"
        )
      )
      assertTrue(sourceUrl.contains("?auth_key="))
      assertTrue(isAiGenerated)
      assertFalse(isAiTranslation)
      assertEquals("中文 · AI 字幕", displayLabel)
      assertEquals(117013062224541L, aid)
      assertEquals(40463961475L, cid)
      assertEquals("BV1KFGc6jEzQ", bvid)
    }
  }

  @Test
  fun emptyCurrentWebSubtitleCatalogProducesNoButtonTrack() {
    val catalog =
      BiliSubtitleApi.parseCatalog(
        bytes = byteArrayOf(0x0A, 0x00),
        aid = 117032137851494L,
        cid = 40567046596L,
        bvid = "BV12xMR6pETX",
      )

    assertTrue(catalog.tracks.isEmpty())
  }

  @Test
  fun rejectsAiSubtitleSourceWhoseDecodedPathBelongsToAnotherMedia() {
    val catalog =
      BiliSubtitleApi.parseCatalog(
        bytes = CURRENT_SUBTITLE_REPLY,
        aid = 117032137851494L,
        cid = 40567046596L,
        bvid = "BV12xMR6pETX",
      )

    assertTrue(catalog.tracks.isEmpty())
  }

  @Test
  fun convertsBilibiliJsonBodyToEscapedWebVtt() {
    val cues =
      BiliSubtitleApi.parseDocument(
        JSONObject(
          """
          {
            "body": [
              {"from": 4.14, "to": 8.52, "content": "第一行\n第二行 <测试> &"},
              {"from": 9.0, "to": 9.0, "content": "无效时间"}
            ]
          }
          """
            .trimIndent()
        )
      )

    assertEquals(1, cues.size)
    assertEquals(
      "WEBVTT\n\n" + "00:00:04.140 --> 00:00:08.520\n" + "第一行\n第二行 &lt;测试&gt; &amp;\n\n",
      BiliSubtitleApi.toWebVtt(cues),
    )
  }

  private companion object {
    val CURRENT_SUBTITLE_REPLY =
      hexToBytes(
        """
        0a91031a8e030880a28decead3bbe51c1213323037343733323934333634303834323439361a0561
        692d7a682206e4b8ade696872ad1022f2f7375627469746c652e62696c6962696c692e636f6d2f25
        30312531422535433d5f2530342531322531322530343966253246253037482530382532397e2531
        3624352530442e2530422530414c2530332532432530312531412530304d3a253143652530302530
        46253146462530332530303325314136253137253041253235552531342531365125313625324349
        2531366f253136475f253044406e414d5757253543532531464774536e5f46253130735458582535
        4557253542253542253545724c2531387e25313525314346253144566f4347722531364f25354325
        35432531376b4948515525304425303548253135253231503f617574685f6b65793d313738363237
        303736332d36343366333631326336316334363334623665643539306635353935633364362d302d
        326363353661653532636435333237383135393636333931656632613135363238014206e4b8ade6
        96875002
        """
          .trimIndent()
      )

    private fun hexToBytes(value: String): ByteArray {
      val compact = value.filterNot(Char::isWhitespace)
      require(compact.length % 2 == 0)
      return ByteArray(compact.length / 2) { index ->
        compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
      }
    }
  }
}

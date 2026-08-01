package dev.openbili.webdemo.api

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DanmakuMaskParserTest {
  @Test
  fun parsesAllowedAreaWithSubjectCutout() {
    val svg =
      """
      <svg viewBox="0 0 160 90" xmlns="http://www.w3.org/2000/svg">
        <path d="M0 0 H160 V90 H96 V18 H64 V90 H0 Z"/>
      </svg>
      """
        .trimIndent()
    val timeline = DanmakuMaskParser.parseBytes(webmask(svg))

    assertTrue(
      timeline.allowedContoursAt(0).contentToString(),
      timeline.isProtectedAt(0L, .5f, .5f),
    )
    assertFalse(timeline.isProtectedAt(0L, .1f, .1f))
    assertFalse(timeline.isProtectedAt(-1L, .5f, .5f))
  }

  @Test
  fun keepsAllowedBackgroundDirectionWhenCloseUpSubjectCoversMostOfFrame() {
    val svg =
      """
      <svg viewBox="0 0 160 90" xmlns="http://www.w3.org/2000/svg">
        <path d="M0 0 H20 V90 H0 Z"/>
        <path d="M140 0 H160 V90 H140 Z"/>
      </svg>
      """
        .trimIndent()
    val timeline = DanmakuMaskParser.parseBytes(webmask(svg))

    assertTrue(timeline.isProtectedAt(0L, .5f, .5f))
    assertFalse(timeline.isProtectedAt(0L, .05f, .5f))
    assertFalse(timeline.isProtectedAt(0L, .95f, .5f))
  }

  @Test
  fun treatsFullViewportPathAsEntirelyAllowedBackground() {
    val svg =
      """
      <svg viewBox="0 0 160 90" xmlns="http://www.w3.org/2000/svg">
        <path d="M0 0 H160 V90 H0 Z"/>
      </svg>
      """
        .trimIndent()
    val timeline = DanmakuMaskParser.parseBytes(webmask(svg))

    assertFalse(timeline.isProtectedAt(0L, .5f, .5f))
    assertFalse(timeline.isProtectedAt(0L, .05f, .05f))
  }

  @Test
  fun treatsPathlessFrameAsNoProtectedSubject() {
    val svg = """<svg viewBox="0 0 160 90" xmlns="http://www.w3.org/2000/svg"></svg>"""
    val timeline = DanmakuMaskParser.parseBytes(webmask(svg))

    assertFalse(timeline.isProtectedAt(0L, .5f, .5f))
  }

  @Test
  fun parsesPotraceRelativePathAndGroupTransform() {
    val svg =
      """
      <svg viewBox="0 0 320 180" xmlns="http://www.w3.org/2000/svg">
        <g transform="translate(0,180) scale(0.1,-0.1)">
          <path d="M0 0 l0 1800 3200 0 0 -1800 -1200 0 0 1200 -800 0 0 -1200 -1200 0 z"/>
        </g>
      </svg>
      """
        .trimIndent()
    val timeline = DanmakuMaskParser.parseBytes(webmask(svg))

    assertTrue(timeline.isProtectedAt(0L, .5f, .5f))
    assertFalse(timeline.isProtectedAt(0L, .1f, .1f))
  }

  @Test
  fun parsesPotracePathWithWrappedNumericCommands() {
    val svg =
      """
      <svg viewBox="0 0 320 180" xmlns="http://www.w3.org/2000/svg">
        <g transform="translate(0,180) scale(0.1,-0.1)">
          <path d="M0 0 l0 1800 1200 0 0 -1200 -800 0 0 1200
          1200 0 0 -1800 z"/>
        </g>
      </svg>
      """
        .trimIndent()
    val timeline = DanmakuMaskParser.parseBytes(webmask(svg))

    assertTrue(timeline.isProtectedAt(0L, .5f, .5f))
  }

  @Test
  fun retainsEveryDistinctSourceMaskTimestamp() {
    val frames =
      arrayOf(
        0 to "<svg viewBox=\"0 0 160 90\"><path d=\"M0 0 H160 V90 H96 V18 H64 V90 H0 Z\"/></svg>",
        33 to "<svg viewBox=\"0 0 160 90\"><path d=\"M0 0 H160 V90 H112 V18 H80 V90 H0 Z\"/></svg>",
        66 to "<svg viewBox=\"0 0 160 90\"><path d=\"M0 0 H160 V90 H128 V18 H96 V90 H0 Z\"/></svg>",
      )
    val timeline = DanmakuMaskParser.parseBytes(webmask(*frames))

    assertEquals(0, timeline.frameIndexAt(0L))
    assertEquals(1, timeline.frameIndexAt(33L))
    assertEquals(2, timeline.frameIndexAt(66L))
  }

  @Test
  fun limitsHighRateMasksToSixtySamplesPerSecond() {
    val frames =
      arrayOf(
        0 to "<svg viewBox=\"0 0 160 90\"><path d=\"M0 0 H160 V90 H96 V18 H64 V90 H0 Z\"/></svg>",
        8 to "<svg viewBox=\"0 0 160 90\"><path d=\"M0 0 H160 V90 H104 V18 H72 V90 H0 Z\"/></svg>",
        17 to "<svg viewBox=\"0 0 160 90\"><path d=\"M0 0 H160 V90 H112 V18 H80 V90 H0 Z\"/></svg>",
        25 to "<svg viewBox=\"0 0 160 90\"><path d=\"M0 0 H160 V90 H120 V18 H88 V90 H0 Z\"/></svg>",
        34 to "<svg viewBox=\"0 0 160 90\"><path d=\"M0 0 H160 V90 H128 V18 H96 V90 H0 Z\"/></svg>",
      )
    val timeline = DanmakuMaskParser.parseBytes(webmask(*frames))

    assertEquals(0, timeline.frameIndexAt(0L))
    assertEquals(0, timeline.frameIndexAt(16L))
    assertEquals(1, timeline.frameIndexAt(17L))
    assertEquals(2, timeline.frameIndexAt(34L))
  }

  @Test
  fun skipsMalformedSampleWithoutDroppingThePreviousMask() {
    val validStart =
      "<svg viewBox=\"0 0 160 90\"><path d=\"M0 0 H160 V90 H96 V18 H64 V90 H0 Z\"/></svg>"
    val malformed = "<svg><path d=\"not-a-path\"/></svg>"
    val validEnd =
      "<svg viewBox=\"0 0 160 90\"><path d=\"M0 0 H160 V90 H112 V18 H80 V90 H0 Z\"/></svg>"

    val timeline =
      DanmakuMaskParser.parseBytes(webmask(0 to validStart, 33 to malformed, 66 to validEnd))

    assertEquals(0, timeline.renderFrameIndexAt(33L))
    assertTrue(timeline.isProtectedAt(33L, .5f, .5f))
    assertEquals(1, timeline.frameIndexAt(66L))
  }

  @Test
  fun holdsOnlyShortEmptySampleBetweenValidMasks() {
    val validStart =
      "<svg viewBox=\"0 0 160 90\"><path d=\"M0 0 H160 V90 H96 V18 H64 V90 H0 Z\"/></svg>"
    val empty = "<svg viewBox=\"0 0 160 90\"></svg>"
    val validEnd =
      "<svg viewBox=\"0 0 160 90\"><path d=\"M0 0 H160 V90 H112 V18 H80 V90 H0 Z\"/></svg>"

    val shortGap =
      DanmakuMaskParser.parseBytes(webmask(0 to validStart, 33 to empty, 66 to validEnd))
    val sustainedGap =
      DanmakuMaskParser.parseBytes(webmask(0 to validStart, 33 to empty, 200 to validEnd))

    assertEquals(0, shortGap.renderFrameIndexAt(33L))
    assertEquals(1, sustainedGap.renderFrameIndexAt(33L))
    assertTrue(sustainedGap.allowedContoursAt(1).isEmpty())
  }

  private fun webmask(svg: String): ByteArray = webmask(0 to svg)

  private fun webmask(vararg frames: Pair<Int, String>): ByteArray {
    val encodedFrames = frames.map { (timeMs, svg) ->
      val encoded =
        "data:image/svg+xml;base64," +
          Base64.encodeToString(svg.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
      timeMs to encoded.toByteArray(Charsets.US_ASCII)
    }
    val frame =
      ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
          encodedFrames.forEach { (timeMs, data) ->
            output.writeInt(data.size)
            output.writeInt(0)
            output.writeInt(timeMs)
            output.write(data)
          }
        }
        bytes.toByteArray()
      }
    val compressed =
      ByteArrayOutputStream().use { bytes ->
        GZIPOutputStream(bytes).use { it.write(frame) }
        bytes.toByteArray()
      }
    return ByteArrayOutputStream().use { bytes ->
      DataOutputStream(bytes).use { output ->
        output.writeBytes("MASK")
        output.writeInt(1)
        output.writeInt(0x02000000)
        output.writeInt(1)
        output.writeLong(0L)
        output.writeLong(32L)
        output.write(compressed)
      }
      bytes.toByteArray()
    }
  }
}

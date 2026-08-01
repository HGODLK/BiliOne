package dev.openbili.webdemo.api

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DanmakuProtoParserTest {
  @Test
  fun parsesElementsAndSkipsUnknownFields() {
    val first =
      message(
        varintField(1, 123456789L),
        varintField(2, 12_345L),
        varintField(3, 1),
        varintField(4, 25),
        varintField(5, 0x66CCFF),
        stringField(7, "第一条弹幕"),
        varintField(9, 8),
        varintField(24, DANMAKU_COLORFUL_VIP_GRADIENT.toLong()),
        varintField(20, 99),
      )
    val second =
      message(
        varintField(2, 54_321L),
        varintField(3, 5),
        varintField(4, 18),
        varintField(5, 0xFFFFFF),
        stringField(7, "顶部弹幕"),
        stringField(12, "987654321"),
      )
    val payload = message(bytesField(1, first), bytesField(1, second))

    val parsed = DanmakuProtoParser.parseSegment(payload)

    assertEquals(2, parsed.size)
    assertEquals(12_345L, parsed[0].timeMs)
    assertEquals(1, parsed[0].type)
    assertEquals(25, parsed[0].fontSize)
    assertEquals(0x66CCFF, parsed[0].color)
    assertEquals("第一条弹幕", parsed[0].content)
    assertEquals("123456789", parsed[0].sourceId)
    assertEquals(DANMAKU_COLORFUL_VIP_GRADIENT, parsed[0].colorful)
    assertEquals(54_321L, parsed[1].timeMs)
    assertEquals(5, parsed[1].type)
    assertEquals("987654321", parsed[1].sourceId)
  }

  @Test
  fun ignoresEmptyContentAndAllowsMissingId() {
    val empty = message(varintField(2, 1_000L))
    val withoutId = message(varintField(2, 2_000L), stringField(7, "无 ID"))
    val parsed =
      DanmakuProtoParser.parseSegment(message(bytesField(1, empty), bytesField(1, withoutId)))

    assertEquals(1, parsed.size)
    assertEquals("无 ID", parsed.single().content)
    assertNull(parsed.single().sourceId)
  }

  private fun message(vararg fields: ByteArray): ByteArray =
    ByteArrayOutputStream().use { output ->
      fields.forEach(output::write)
      output.toByteArray()
    }

  private fun varintField(field: Int, value: Long): ByteArray =
    message(varint((field shl 3).toLong()), varint(value))

  private fun stringField(field: Int, value: String): ByteArray =
    bytesField(field, value.toByteArray(Charsets.UTF_8))

  private fun bytesField(field: Int, value: ByteArray): ByteArray =
    message(varint(((field shl 3) or 2).toLong()), varint(value.size.toLong()), value)

  private fun varint(value: Long): ByteArray {
    var remaining = value
    return ByteArrayOutputStream().use { output ->
      do {
        var next = (remaining and 0x7F).toInt()
        remaining = remaining ushr 7
        if (remaining != 0L) next = next or 0x80
        output.write(next)
      } while (remaining != 0L)
      output.toByteArray()
    }
  }
}

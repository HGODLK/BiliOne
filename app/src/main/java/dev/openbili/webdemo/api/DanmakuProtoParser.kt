package dev.openbili.webdemo.api

/**
 * Minimal reader for bilibili.community.service.dm.v1.DmSegMobileReply.
 *
 * Keeping this parser local avoids shipping the full protobuf runtime for one small wire format.
 * Unknown fields are skipped so newer server fields remain forward-compatible.
 */
internal object DanmakuProtoParser {
  fun parseSegment(bytes: ByteArray): List<DanmakuItem> {
    if (bytes.isEmpty()) return emptyList()
    val reader = ProtoReader(bytes)
    return buildList {
      while (reader.hasRemaining()) {
        val tag = reader.readVarint().toInt()
        val field = tag ushr 3
        val wireType = tag and 0x07
        if (field == ELEMENTS_FIELD && wireType == WIRE_LENGTH_DELIMITED) {
          parseElement(reader.readSubReader())?.let(::add)
        } else {
          reader.skip(wireType)
        }
      }
    }
  }

  private fun parseElement(reader: ProtoReader): DanmakuItem? {
    var id = 0L
    var idString = ""
    var progressMs = 0L
    var mode = 1
    var fontSize = 25
    var color = 0xFFFFFF
    var colorful = DANMAKU_COLORFUL_NONE
    var content = ""
    while (reader.hasRemaining()) {
      val tag = reader.readVarint().toInt()
      val field = tag ushr 3
      val wireType = tag and 0x07
      when {
        field == ID_FIELD && wireType == WIRE_VARINT -> id = reader.readVarint()
        field == PROGRESS_FIELD && wireType == WIRE_VARINT -> progressMs = reader.readVarint()
        field == MODE_FIELD && wireType == WIRE_VARINT -> mode = reader.readVarint().toInt()
        field == FONT_SIZE_FIELD && wireType == WIRE_VARINT ->
          fontSize = reader.readVarint().toInt()
        field == COLOR_FIELD && wireType == WIRE_VARINT -> color = reader.readVarint().toInt()
        field == CONTENT_FIELD && wireType == WIRE_LENGTH_DELIMITED -> content = reader.readString()
        field == ID_STRING_FIELD && wireType == WIRE_LENGTH_DELIMITED ->
          idString = reader.readString()
        field == COLORFUL_FIELD && wireType == WIRE_VARINT -> colorful = reader.readVarint().toInt()
        else -> reader.skip(wireType)
      }
    }
    if (content.isEmpty()) return null
    return DanmakuItem(
      timeMs = progressMs.coerceAtLeast(0L),
      type = mode,
      fontSize = fontSize,
      color = color,
      content = content,
      sourceId = idString.ifBlank { id.takeIf { it > 0L }?.toString().orEmpty() }.ifBlank { null },
      colorful = colorful,
    )
  }

  private class ProtoReader(
    private val bytes: ByteArray,
    private var position: Int = 0,
    private val limit: Int = bytes.size,
  ) {
    fun hasRemaining(): Boolean = position < limit

    fun readVarint(): Long {
      var result = 0L
      var shift = 0
      while (shift < Long.SIZE_BITS) {
        require(position < limit) { "Truncated protobuf varint" }
        val value = bytes[position++].toInt() and 0xFF
        result = result or ((value and 0x7F).toLong() shl shift)
        if (value and 0x80 == 0) return result
        shift += 7
      }
      throw IllegalArgumentException("Malformed protobuf varint")
    }

    fun readSubReader(): ProtoReader {
      val length = readLength()
      val end = position + length
      require(end in position..limit) { "Truncated protobuf message" }
      val reader = ProtoReader(bytes, position, end)
      position = end
      return reader
    }

    fun readString(): String {
      val length = readLength()
      val end = position + length
      require(end in position..limit) { "Truncated protobuf string" }
      return String(bytes, position, length, Charsets.UTF_8).also { position = end }
    }

    fun skip(wireType: Int) {
      when (wireType) {
        WIRE_VARINT -> readVarint()
        WIRE_FIXED_64 -> skipBytes(Long.SIZE_BYTES)
        WIRE_LENGTH_DELIMITED -> skipBytes(readLength())
        WIRE_FIXED_32 -> skipBytes(Int.SIZE_BYTES)
        else -> throw IllegalArgumentException("Unsupported protobuf wire type: $wireType")
      }
    }

    private fun readLength(): Int {
      val value = readVarint()
      require(value in 0..Int.MAX_VALUE.toLong()) { "Invalid protobuf length" }
      return value.toInt()
    }

    private fun skipBytes(count: Int) {
      require(count >= 0 && position + count in position..limit) { "Truncated protobuf field" }
      position += count
    }
  }

  private const val ELEMENTS_FIELD = 1
  private const val ID_FIELD = 1
  private const val PROGRESS_FIELD = 2
  private const val MODE_FIELD = 3
  private const val FONT_SIZE_FIELD = 4
  private const val COLOR_FIELD = 5
  private const val CONTENT_FIELD = 7
  private const val ID_STRING_FIELD = 12
  private const val COLORFUL_FIELD = 24
  private const val WIRE_VARINT = 0
  private const val WIRE_FIXED_64 = 1
  private const val WIRE_LENGTH_DELIMITED = 2
  private const val WIRE_FIXED_32 = 5
}

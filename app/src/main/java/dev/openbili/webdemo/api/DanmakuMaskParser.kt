package dev.openbili.webdemo.api

import android.util.Base64
import androidx.core.graphics.PathParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * 把 B 站 webmask 资源解析成"归一化允许弹幕背景轮廓"的紧凑时间线。
 *
 * 解析、gzip 解压、SVG 路径解码与降采样都应在主线程之外执行；播放器只做一次二分
 * 时间查找并套用缓存的 Canvas 裁剪路径。
 */
internal object DanmakuMaskParser {
  fun parse(resource: DanmakuMaskResource): DanmakuMaskTimeline? =
    runCatching { parseBytes(resource.bytes) }.getOrNull()?.takeUnless(DanmakuMaskTimeline::isEmpty)

  internal fun parseBytes(bytes: ByteArray): DanmakuMaskTimeline {
    require(bytes.size >= MIN_WEBMASK_BYTES) { "Truncated webmask header" }
    require(bytes.copyOfRange(0, 4).contentEquals(MASK_MAGIC)) { "Invalid webmask magic" }
    require(bytes.readInt(4) == SUPPORTED_VERSION) { "Unsupported webmask version" }
    val segmentCount = bytes.readInt(12)
    require(segmentCount in 1..MAX_SEGMENTS) { "Invalid webmask segment count" }
    val headerEnd = HEADER_BYTES + segmentCount * SEGMENT_INDEX_BYTES
    require(headerEnd <= bytes.size) { "Truncated webmask segment index" }
    val segmentOffsets =
      LongArray(segmentCount) { index ->
        bytes.readLong(HEADER_BYTES + index * SEGMENT_INDEX_BYTES + Long.SIZE_BYTES)
      }
    segmentOffsets.forEachIndexed { index, offset ->
      require(offset in headerEnd.toLong()..bytes.size.toLong()) {
        "Invalid webmask segment offset"
      }
      if (index > 0)
        require(offset >= segmentOffsets[index - 1]) {
          "Unordered webmask segment offsets"
        }
    }

    val frameTimes = mutableListOf<Int>()
    val frameContours = mutableListOf<FloatArray>()
    val frameEvenOddFills = mutableListOf<Boolean>()
    var previousContours: FloatArray? = null
    var previousEvenOddFill = false
    var pendingTimeMs = -1
    var pendingSvgData: ByteArray? = null
    var sourceFrameCount = 0
    var lastOutputSampleSlot = Long.MIN_VALUE

    fun flushPendingFrame() {
      val svgData = pendingSvgData ?: return
      // webmask 的源帧率可能高于渲染节奏：每个 1/60 秒槽位最多保留一帧源数据，
      // 保证智能防挡产生的几何更新频率不会超过弹幕本身。
      val sampleSlot = pendingTimeMs.toLong() * SMART_MASK_SAMPLE_FPS / 1_000L
      if (sampleSlot == lastOutputSampleSlot) {
        pendingTimeMs = -1
        pendingSvgData = null
        return
      }
      val mask = decodeAllowedMask(svgData)
      if (
        mask != null &&
          (previousContours?.contentEquals(mask.contours) != true ||
            previousEvenOddFill != mask.evenOddFill)
      ) {
        frameTimes += pendingTimeMs
        frameContours += mask.contours
        frameEvenOddFills += mask.evenOddFill
        previousContours = mask.contours
        previousEvenOddFill = mask.evenOddFill
      }
      lastOutputSampleSlot = sampleSlot
      pendingTimeMs = -1
      pendingSvgData = null
    }

    segmentOffsets.indices.forEach { segmentIndex ->
      val start = segmentOffsets[segmentIndex].toInt()
      val end =
        if (segmentIndex + 1 < segmentOffsets.size) segmentOffsets[segmentIndex + 1].toInt()
        else bytes.size
      if (end <= start) return@forEach
      val inflated = inflateSegment(bytes, start, end - start)
      var position = 0
      while (
        position + FRAME_HEADER_BYTES <= inflated.size &&
          sourceFrameCount < MAX_SOURCE_FRAMES &&
          frameTimes.size < MAX_OUTPUT_FRAMES
      ) {
        val dataLength = inflated.readInt(position)
        val timeMs = inflated.readInt(position + Int.SIZE_BYTES * 2)
        val dataStart = position + FRAME_HEADER_BYTES
        val dataEnd = dataStart + dataLength
        if (dataLength <= 0 || dataEnd !in dataStart..inflated.size) break
        sourceFrameCount += 1
        if (timeMs >= 0) {
          if (timeMs == pendingTimeMs) {
            // 第 0 段可能包含被钳到同一时间戳的 pre-roll 帧；最后一个重复帧才是真正
            // 属于该时间戳的帧。
            pendingSvgData = inflated.copyOfRange(dataStart, dataEnd)
          } else {
            flushPendingFrame()
            // dm_mask 通常携带约 30fps 的源时间戳：保留每个独立的 60Hz 采样槽位，
            // 让运动主体不会退回旧的 100ms 步进。
            pendingTimeMs = timeMs
            pendingSvgData = inflated.copyOfRange(dataStart, dataEnd)
          }
        }
        position = dataEnd
      }
    }
    flushPendingFrame()
    return DanmakuMaskTimeline(
      frameTimesMs = frameTimes.toIntArray(),
      allowedContours = frameContours,
      evenOddFills = frameEvenOddFills.toBooleanArray(),
    )
  }

  private fun decodeAllowedMask(encodedFrame: ByteArray): DecodedMask? {
    val encoded = String(encodedFrame, Charsets.US_ASCII)
    val markerIndex = encoded.indexOf(BASE64_MARKER)
    if (markerIndex < 0) return null
    val svg =
      runCatching {
          String(
            Base64.decode(encoded.substring(markerIndex + BASE64_MARKER.length), Base64.DEFAULT),
            Charsets.UTF_8,
          )
        }
        .getOrNull() ?: return null
    return decodeSvgPath(svg)
  }

  /**
   * 只保留 SVG 轮廓本身；刻意不做光栅化，避免出现块状锯齿边缘。
   */
  private fun decodeSvgPath(svg: String): DecodedMask? {
    val viewBoxMatch = VIEW_BOX_REGEX.find(svg) ?: return null
    val minX = viewBoxMatch.groupValues[1].toFloatOrNull() ?: return null
    val minY = viewBoxMatch.groupValues[2].toFloatOrNull() ?: return null
    val viewWidth = viewBoxMatch.groupValues[3].toFloatOrNull()?.takeIf { it > 0f } ?: return null
    val viewHeight = viewBoxMatch.groupValues[4].toFloatOrNull()?.takeIf { it > 0f } ?: return null
    val pathMatches = PATH_DATA_REGEX.findAll(svg).toList()
    // 合法但无 path 的 SVG 明确表示该采样帧没有受保护主体。
    if (pathMatches.isEmpty()) return DecodedMask()

    val evenOdd = svg.contains("fill-rule=\"evenodd\"", ignoreCase = true)
    val transform =
      TRANSFORM_REGEX.find(svg)?.groupValues?.getOrNull(1)?.let { transform ->
        val translate = TRANSLATE_REGEX.find(transform)
        val scale = SCALE_REGEX.find(transform)
        val translateX = translate?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f
        val translateY = translate?.groupValues?.getOrNull(2)?.toFloatOrNull() ?: 0f
        val scaleX = scale?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 1f
        val scaleY = scale?.groupValues?.getOrNull(2)?.toFloatOrNull() ?: scaleX
        SvgTransform(scaleX, scaleY, translateX, translateY)
      } ?: SvgTransform()
    val sampleStep = (minOf(viewWidth, viewHeight) / DIRECT_PATH_SAMPLE_DIVISOR).coerceAtLeast(.5f)
    val sourceSampleStep =
      sampleStep / maxOf(abs(transform.scaleX), abs(transform.scaleY)).coerceAtLeast(.0001f)
    val flattenedPaths = pathMatches.mapNotNull { match ->
      flattenPathData(match.groupValues[1], sourceSampleStep, transform)
        .takeIf(List<FloatArray>::isNotEmpty)
    }
    val contours = flattenedPaths.flatten()
    // 声明了 path 却解不出轮廓属于坏采样帧，而不是"有意为空的蒙版"。
    if (contours.isEmpty()) return null
    val packed = ArrayList<Float>()
    contours.forEach { contour ->
      contour.forEachIndexed { index, value ->
        packed += if (index % 2 == 0) (value - minX) / viewWidth else (value - minY) / viewHeight
      }
      packed += Float.NaN
      packed += Float.NaN
    }
    packed.removeAt(packed.lastIndex)
    packed.removeAt(packed.lastIndex)
    // SVG 填充路径描述的是"允许显示弹幕的背景区域"；透明剩余部分是受保护主体——
    // 即使特写镜头让背景不足半帧也按此规则处理。
    return DecodedMask(packed.toFloatArray(), evenOddFill = evenOdd)
  }

  private fun flattenPathData(
    pathData: String,
    sampleStep: Float,
    transform: SvgTransform,
  ): List<FloatArray> {
    val nodes =
      runCatching {
          // B 站 Potrace 输出会把长路径命令折成多行。AndroidX PathParser 会把两个数字
          // 参数之间的换行当成一个数字的一部分，从而拒绝整条路径，只留下零星小路径。
          PathParser.createNodesFromPathData(pathData.replace(PATH_DATA_WHITESPACE_REGEX, " "))
        }
        .getOrNull() ?: return emptyList()
    val contours = mutableListOf<FloatArray>()
    var points = mutableListOf<Float>()
    var currentX = 0f
    var currentY = 0f
    var contourStartX = 0f
    var contourStartY = 0f
    var controlX = 0f
    var controlY = 0f
    var lastCommand = ' '

    fun flushContour() {
      if (points.size >= 6) contours += points.toFloatArray()
      points = mutableListOf()
    }

    fun addPoint(x: Float, y: Float) {
      val mappedX = x * transform.scaleX + transform.translateX
      val mappedY = y * transform.scaleY + transform.translateY
      val last = points.size
      if (last >= 2 && points[last - 2] == mappedX && points[last - 1] == mappedY) {
        return
      }
      if (points.size / 2 >= MAX_CONTOUR_POINTS) return
      points += mappedX
      points += mappedY
    }

    fun curveSteps(length: Float): Int =
      ceil(length / sampleStep).toInt().coerceIn(2, MAX_CURVE_STEPS)

    fun addCubic(
      startX: Float,
      startY: Float,
      firstControlX: Float,
      firstControlY: Float,
      secondControlX: Float,
      secondControlY: Float,
      endX: Float,
      endY: Float,
    ) {
      val roughLength =
        hypot(firstControlX - startX, firstControlY - startY) +
          hypot(secondControlX - firstControlX, secondControlY - firstControlY) +
          hypot(endX - secondControlX, endY - secondControlY)
      val steps = curveSteps(roughLength)
      for (step in 1..steps) {
        val t = step.toFloat() / steps
        val oneMinusT = 1f - t
        addPoint(
          oneMinusT * oneMinusT * oneMinusT * startX +
            3f * oneMinusT * oneMinusT * t * firstControlX +
            3f * oneMinusT * t * t * secondControlX +
            t * t * t * endX,
          oneMinusT * oneMinusT * oneMinusT * startY +
            3f * oneMinusT * oneMinusT * t * firstControlY +
            3f * oneMinusT * t * t * secondControlY +
            t * t * t * endY,
        )
      }
    }

    fun addQuadratic(
      startX: Float,
      startY: Float,
      curveControlX: Float,
      curveControlY: Float,
      endX: Float,
      endY: Float,
    ) {
      val roughLength =
        hypot(curveControlX - startX, curveControlY - startY) +
          hypot(endX - curveControlX, endY - curveControlY)
      val steps = curveSteps(roughLength)
      for (step in 1..steps) {
        val t = step.toFloat() / steps
        val oneMinusT = 1f - t
        addPoint(
          oneMinusT * oneMinusT * startX + 2f * oneMinusT * t * curveControlX + t * t * endX,
          oneMinusT * oneMinusT * startY + 2f * oneMinusT * t * curveControlY + t * t * endY,
        )
      }
    }

    nodes.forEach { node ->
      val command = node.type
      val parameters = node.params
      var index = 0
      when (command) {
        'M',
        'm' -> {
          while (index + 1 < parameters.size) {
            val nextX = if (command == 'm') currentX + parameters[index] else parameters[index]
            val nextY =
              if (command == 'm') currentY + parameters[index + 1] else parameters[index + 1]
            if (index == 0) {
              flushContour()
              contourStartX = nextX
              contourStartY = nextY
            }
            currentX = nextX
            currentY = nextY
            addPoint(currentX, currentY)
            lastCommand = if (index == 0) command else if (command == 'm') 'l' else 'L'
            index += 2
          }
          controlX = currentX
          controlY = currentY
        }
        'L',
        'l' -> {
          while (index + 1 < parameters.size) {
            currentX = if (command == 'l') currentX + parameters[index] else parameters[index]
            currentY =
              if (command == 'l') currentY + parameters[index + 1] else parameters[index + 1]
            addPoint(currentX, currentY)
            index += 2
          }
          controlX = currentX
          controlY = currentY
          lastCommand = command
        }
        'H',
        'h' -> {
          parameters.forEach { value ->
            currentX = if (command == 'h') currentX + value else value
            addPoint(currentX, currentY)
          }
          controlX = currentX
          controlY = currentY
          lastCommand = command
        }
        'V',
        'v' -> {
          parameters.forEach { value ->
            currentY = if (command == 'v') currentY + value else value
            addPoint(currentX, currentY)
          }
          controlX = currentX
          controlY = currentY
          lastCommand = command
        }
        'C',
        'c' -> {
          while (index + 5 < parameters.size) {
            val firstControlX =
              if (command == 'c') currentX + parameters[index] else parameters[index]
            val firstControlY =
              if (command == 'c') currentY + parameters[index + 1] else parameters[index + 1]
            val secondControlX =
              if (command == 'c') currentX + parameters[index + 2] else parameters[index + 2]
            val secondControlY =
              if (command == 'c') currentY + parameters[index + 3] else parameters[index + 3]
            val endX =
              if (command == 'c') currentX + parameters[index + 4] else parameters[index + 4]
            val endY =
              if (command == 'c') currentY + parameters[index + 5] else parameters[index + 5]
            addCubic(
              currentX,
              currentY,
              firstControlX,
              firstControlY,
              secondControlX,
              secondControlY,
              endX,
              endY,
            )
            currentX = endX
            currentY = endY
            controlX = secondControlX
            controlY = secondControlY
            lastCommand = command
            index += 6
          }
        }
        'S',
        's' -> {
          while (index + 3 < parameters.size) {
            val firstControlX = if (lastCommand in "CcSs") currentX * 2f - controlX else currentX
            val firstControlY = if (lastCommand in "CcSs") currentY * 2f - controlY else currentY
            val secondControlX =
              if (command == 's') currentX + parameters[index] else parameters[index]
            val secondControlY =
              if (command == 's') currentY + parameters[index + 1] else parameters[index + 1]
            val endX =
              if (command == 's') currentX + parameters[index + 2] else parameters[index + 2]
            val endY =
              if (command == 's') currentY + parameters[index + 3] else parameters[index + 3]
            addCubic(
              currentX,
              currentY,
              firstControlX,
              firstControlY,
              secondControlX,
              secondControlY,
              endX,
              endY,
            )
            currentX = endX
            currentY = endY
            controlX = secondControlX
            controlY = secondControlY
            lastCommand = command
            index += 4
          }
        }
        'Q',
        'q' -> {
          while (index + 3 < parameters.size) {
            val curveControlX =
              if (command == 'q') currentX + parameters[index] else parameters[index]
            val curveControlY =
              if (command == 'q') currentY + parameters[index + 1] else parameters[index + 1]
            val endX =
              if (command == 'q') currentX + parameters[index + 2] else parameters[index + 2]
            val endY =
              if (command == 'q') currentY + parameters[index + 3] else parameters[index + 3]
            addQuadratic(
              currentX,
              currentY,
              curveControlX,
              curveControlY,
              endX,
              endY,
            )
            currentX = endX
            currentY = endY
            controlX = curveControlX
            controlY = curveControlY
            lastCommand = command
            index += 4
          }
        }
        'T',
        't' -> {
          while (index + 1 < parameters.size) {
            val curveControlX = if (lastCommand in "QqTt") currentX * 2f - controlX else currentX
            val curveControlY = if (lastCommand in "QqTt") currentY * 2f - controlY else currentY
            val endX = if (command == 't') currentX + parameters[index] else parameters[index]
            val endY =
              if (command == 't') currentY + parameters[index + 1] else parameters[index + 1]
            addQuadratic(
              currentX,
              currentY,
              curveControlX,
              curveControlY,
              endX,
              endY,
            )
            currentX = endX
            currentY = endY
            controlX = curveControlX
            controlY = curveControlY
            lastCommand = command
            index += 2
          }
        }
        'A',
        'a' -> {
          // Potrace 生成的 B 站蒙版只使用直线与三次曲线：少见的 SVG 圆弧按端点做安全
          // 有界处理，而不是拒绝整帧。
          while (index + 6 < parameters.size) {
            currentX =
              if (command == 'a') currentX + parameters[index + 5] else parameters[index + 5]
            currentY =
              if (command == 'a') currentY + parameters[index + 6] else parameters[index + 6]
            addPoint(currentX, currentY)
            index += 7
          }
          controlX = currentX
          controlY = currentY
          lastCommand = command
        }
        'Z',
        'z' -> {
          currentX = contourStartX
          currentY = contourStartY
          controlX = currentX
          controlY = currentY
          lastCommand = command
        }
      }
    }
    flushContour()
    return contours
  }

  private fun inflateSegment(bytes: ByteArray, offset: Int, length: Int): ByteArray {
    val output = ByteArrayOutputStream()
    GZIPInputStream(ByteArrayInputStream(bytes, offset, length)).use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= MAX_INFLATED_SEGMENT_BYTES) {
          "Oversized webmask segment"
        }
        output.write(buffer, 0, count)
      }
    }
    return output.toByteArray()
  }

  private fun ByteArray.readInt(offset: Int): Int {
    require(offset >= 0 && offset + Int.SIZE_BYTES <= size)
    return ((this[offset].toInt() and 0xFF) shl 24) or
      ((this[offset + 1].toInt() and 0xFF) shl 16) or
      ((this[offset + 2].toInt() and 0xFF) shl 8) or
      (this[offset + 3].toInt() and 0xFF)
  }

  private fun ByteArray.readLong(offset: Int): Long {
    require(offset >= 0 && offset + Long.SIZE_BYTES <= size)
    return (readInt(offset).toLong() shl 32) or
      (readInt(offset + Int.SIZE_BYTES).toLong() and 0xFFFFFFFFL)
  }

  private const val SUPPORTED_VERSION = 1
  private const val HEADER_BYTES = 16
  private const val SEGMENT_INDEX_BYTES = 16
  private const val FRAME_HEADER_BYTES = 12
  private const val MIN_WEBMASK_BYTES = HEADER_BYTES + SEGMENT_INDEX_BYTES
  private const val MAX_SEGMENTS = 720
  private const val MAX_SOURCE_FRAMES = 250_000
  private const val MAX_OUTPUT_FRAMES = 72_000
  private const val SMART_MASK_SAMPLE_FPS = 60L
  private const val MAX_INFLATED_SEGMENT_BYTES = 16 * 1024 * 1024
  private const val DIRECT_PATH_SAMPLE_DIVISOR = 256f
  private const val MAX_CONTOUR_POINTS = 4096
  private const val MAX_CURVE_STEPS = 64
  private const val BASE64_MARKER = "data:image/svg+xml;base64,"
  private const val NUMBER = "[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)(?:[eE][-+]?\\d+)?"
  private val MASK_MAGIC = byteArrayOf(0x4D, 0x41, 0x53, 0x4B)
  private val VIEW_BOX_REGEX =
    Regex(
      """viewBox\s*=\s*["']\s*($NUMBER)[,\s]+($NUMBER)[,\s]+($NUMBER)[,\s]+($NUMBER)\s*["']""",
      RegexOption.IGNORE_CASE,
    )
  private val PATH_DATA_REGEX =
    Regex("""<path\b[^>]*\bd\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
  private val PATH_DATA_WHITESPACE_REGEX = Regex("\\s+")
  private val TRANSFORM_REGEX =
    Regex("""transform\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
  private val TRANSLATE_REGEX =
    Regex("""translate\s*\(\s*($NUMBER)(?:[,\s]+($NUMBER))?\s*\)""", RegexOption.IGNORE_CASE)
  private val SCALE_REGEX =
    Regex("""scale\s*\(\s*($NUMBER)(?:[,\s]+($NUMBER))?\s*\)""", RegexOption.IGNORE_CASE)

  private data class SvgTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
  )

  private data class DecodedMask(
    val contours: FloatArray = FloatArray(0),
    val evenOddFill: Boolean = false,
  )
}

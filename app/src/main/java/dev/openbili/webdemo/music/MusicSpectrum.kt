package dev.openbili.webdemo.music

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import dev.openbili.webdemo.HiResCompatibleRenderersFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

internal const val MUSIC_SPECTRUM_BAND_COUNT = 28

internal const val MUSIC_SPECTRUM_MAX_TIMELINE_LEAD_MS = 5_000L
// 初始/回退的总输出延迟（毫秒），在运行时 EMA 观测到真实 sink 前置量之前使用
// （getCurrentPositionUs 已经携带 Media3 反射的 AudioTrack 延迟）。
internal const val MUSIC_SPECTRUM_OUTPUT_LATENCY_MS = 220L
// 自适应估计的安全下限；绝不让短暂过短的前置量使柱条延迟不足。
internal const val MUSIC_SPECTRUM_MIN_OUTPUT_LATENCY_MS = 60L
internal const val MUSIC_SPECTRUM_PEAK_HOLD_MS = 500.0
internal const val MUSIC_SPECTRUM_DELAY_QUEUE_CAPACITY = 24

internal fun musicSpectrumFrameWaitMillis(
  presentationTimeUs: Long,
  playbackPositionMs: Long,
  elapsedSinceCaptureMs: Long = 0L,
  audioSinkPositionUs: Long = C.TIME_UNSET,
  outputLatencyMs: Long = MUSIC_SPECTRUM_OUTPUT_LATENCY_MS,
): Long {
  val wallClockWaitMs =
    (outputLatencyMs - elapsedSinceCaptureMs.coerceAtLeast(0L)).coerceAtLeast(0L)
  if (presentationTimeUs == C.TIME_UNSET || presentationTimeUs < 0L) {
    return wallClockWaitMs
  }
  val playerLeadMs = presentationTimeUs / 1_000L - playbackPositionMs.coerceAtLeast(0L)
  val sinkLeadMs =
    if (audioSinkPositionUs != C.TIME_UNSET && audioSinkPositionUs >= 0L) {
      (presentationTimeUs - audioSinkPositionUs) / 1_000L - elapsedSinceCaptureMs.coerceAtLeast(0L)
    } else {
      0L
    }
  val leadMs = maxOf(playerLeadMs, sinkLeadMs)
  val timelineWaitMs =
    if (leadMs in 1L..MUSIC_SPECTRUM_MAX_TIMELINE_LEAD_MS) {
      leadMs + outputLatencyMs
    } else {
      0L
    }
  return maxOf(wallClockWaitMs, timelineWaitMs)
}

@OptIn(UnstableApi::class)
internal class MusicRenderersFactory(
  context: Context,
  preferSoftwareAudioDecoder: Boolean,
  private val onSpectrumSamples: (FloatArray, Int, Long, Long) -> Unit,
) : HiResCompatibleRenderersFactory(context, preferSoftwareAudioDecoder) {

  override fun buildAudioSink(
    context: Context,
    enableFloatOutput: Boolean,
    enableAudioTrackPlaybackParams: Boolean,
  ): AudioSink? {
    val delegate =
      super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
        ?: return null
    return LittleEndianAudioSink(delegate, onSpectrumSamples)
  }
}

@OptIn(UnstableApi::class)
internal class LittleEndianAudioSink(
  delegate: AudioSink,
  onSpectrumSamples: (FloatArray, Int, Long, Long) -> Unit,
) : ForwardingAudioSink(delegate) {
  private val spectrumTap = Pcm16SpectrumTap(onSpectrumSamples)

  override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
    spectrumTap.configure(inputFormat)
    super.configure(inputFormat, specifiedBufferSize, outputChannels)
  }

  override fun handleBuffer(
    buffer: ByteBuffer,
    presentationTimeUs: Long,
    encodedAccessUnitCount: Int,
  ): Boolean {
    if (buffer.order() != ByteOrder.LITTLE_ENDIAN) buffer.order(ByteOrder.LITTLE_ENDIAN)
    val startPosition = buffer.position()
    val fullyConsumed = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    val endPosition = buffer.position()
    if (endPosition > startPosition) {
      spectrumTap.accept(
        buffer,
        startPosition,
        endPosition,
        presentationTimeUs,
        getCurrentPositionUs(false),
      )
    }
    return fullyConsumed
  }
}

internal class Pcm16SpectrumTap(
  private val onSpectrumSamples: (FloatArray, Int, Long, Long) -> Unit
) {
  private val ring = FloatArray(1_024)
  private var writeIndex = 0
  private var sampleCount = 0
  private var channelCount = 0
  private var sampleRate = 0
  private var pcm16 = false
  private var lastEmissionNanos = 0L
  private var latestEndTimeUs = C.TIME_UNSET

  fun configure(format: Format) {
    channelCount = format.channelCount.coerceAtLeast(1)
    sampleRate = format.sampleRate.coerceAtLeast(0)
    pcm16 =
      format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding == C.ENCODING_PCM_16BIT
    writeIndex = 0
    sampleCount = 0
    lastEmissionNanos = 0L
    latestEndTimeUs = C.TIME_UNSET
  }

  fun accept(
    source: ByteBuffer,
    start: Int,
    end: Int,
    presentationTimeUs: Long,
    audioSinkPositionUs: Long,
  ) {
    if (!pcm16 || sampleRate <= 0 || end <= start) return
    val frameBytes = channelCount * 2
    val consumedFrames = (end - start) / frameBytes
    latestEndTimeUs =
      if (presentationTimeUs == C.TIME_UNSET) C.TIME_UNSET
      else presentationTimeUs + consumedFrames * 1_000_000L / sampleRate
    val input = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    input.position(start)
    input.limit(end)
    while (input.remaining() >= frameBytes) {
      var sum = 0f
      repeat(channelCount) { sum += input.short / 32_768f }
      ring[writeIndex] = sum / channelCount
      writeIndex = (writeIndex + 1) % ring.size
      sampleCount = (sampleCount + 1).coerceAtMost(ring.size)
    }
    val now = System.nanoTime()
    if (sampleCount < 256 || now - lastEmissionNanos < 24_000_000L) return
    lastEmissionNanos = now
    val snapshot = FloatArray(sampleCount)
    val first = (writeIndex - sampleCount + ring.size) % ring.size
    snapshot.indices.forEach { index -> snapshot[index] = ring[(first + index) % ring.size] }
    onSpectrumSamples(snapshot, sampleRate, latestEndTimeUs, audioSinkPositionUs)
  }
}

/** 为音乐页可视化器生成对数间隔、真实响应 PCM 的频段。 */
internal fun analyzeMusicSpectrum(
  samples: FloatArray,
  sampleRate: Int,
  bandCount: Int = MUSIC_SPECTRUM_BAND_COUNT,
  systemVolume: Float = 1f,
): List<Float> {
  if (samples.size < 128 || sampleRate <= 0 || bandCount <= 0)
    return List(bandCount.coerceAtLeast(0)) { 0f }
  val windowSize = samples.size.coerceAtMost(1_024)
  val offset = samples.size - windowSize
  var mean = 0.0
  repeat(windowSize) { mean += samples[offset + it] }
  mean /= windowSize
  val windowed = DoubleArray(windowSize)
  var squareSum = 0.0
  repeat(windowSize) { index ->
    val centered = samples[offset + index] - mean
    val hann = .5 - .5 * cos(2.0 * PI * index / (windowSize - 1).coerceAtLeast(1))
    val value = centered * hann
    windowed[index] = value
    squareSum += centered * centered
  }
  val rms = sqrt(squareSum / windowSize)
  if (rms < .000_001) return List(bandCount) { 0f }

  val minimumFrequency = 55.0
  val maximumFrequency = minOf(16_000.0, sampleRate * .46).coerceAtLeast(minimumFrequency)
  val logRange = ln(maximumFrequency / minimumFrequency)
  val magnitudes = DoubleArray(bandCount)
  repeat(bandCount) { band ->
    val fraction = if (bandCount == 1) 0.0 else band.toDouble() / (bandCount - 1)
    val frequency = minimumFrequency * exp(logRange * fraction)
    val angularStep = 2.0 * PI * frequency / sampleRate
    val coefficient = 2.0 * cos(angularStep)
    var previous = 0.0
    var previousPrevious = 0.0
    repeat(windowSize) { index ->
      val current = windowed[index] + coefficient * previous - previousPrevious
      previousPrevious = previous
      previous = current
    }
    // Goertzel 只计算同样的选定 DFT 频点，不需要为每个采样点做两次三角函数调用。
    // 视觉效果不变，而持续播放不再让一个 CPU 核心忙到几分钟后就热得让 UI 动画劣化。
    magnitudes[band] =
      sqrt(
        (previousPrevious * previousPrevious + previous * previous -
            coefficient * previous * previousPrevious)
          .coerceAtLeast(0.0)
      )
  }
  val peak = magnitudes.maxOrNull()?.takeIf { it > 0.0 } ?: return List(bandCount) { 0f }
  val levelGain = musicSpectrumLevelGain(rms * systemVolume.coerceIn(0f, 1f))
  if (levelGain <= 0.0) return List(bandCount) { 0f }
  return magnitudes.mapIndexed { band, magnitude ->
    // 峰值归一化保留真实的频率形状。压缩后的 RMS 包络再还原可闻的响度变化，
    // 既不让安静段落塌陷，也不让响亮段落把所有柱条顶满：其斜率在接近静音处刻意
    // 陡峭，随信号增长逐渐平缓。频率塑形放在峰值归一化之后，避免重新归一化把
    // 高频的额外灵敏度和低频软上限抵消掉。
    val fraction = if (bandCount == 1) 0.0 else band.toDouble() / (bandCount - 1)
    val frequency = minimumFrequency * exp(logRange * fraction)
    shapeMusicSpectrumBand(
      normalizedMagnitude =
        (magnitude / peak).coerceIn(0.0, 1.0).pow(.55) * levelGain,
      frequency = frequency,
      minimumFrequency = minimumFrequency,
      maximumFrequency = maximumFrequency,
    )
  }
}

/**
 * 对单个频谱柱做频率相关的视觉塑形。
 *
 * 低频能量通常远高于中高频，直接峰值归一化会让鼓点和高频纹理被压扁。因此低频
 * 使用平滑软上限，高频逐步增加灵敏度；两者都只改变视觉目标，不改变实际音频数据。
 */
internal fun shapeMusicSpectrumBand(
  normalizedMagnitude: Double,
  frequency: Double,
  minimumFrequency: Double,
  maximumFrequency: Double,
): Float {
  if (normalizedMagnitude <= 0.0 || minimumFrequency <= 0.0 || maximumFrequency <= minimumFrequency) {
    return 0f
  }
  val position =
    (ln(frequency.coerceIn(minimumFrequency, maximumFrequency) / minimumFrequency) /
        ln(maximumFrequency / minimumFrequency))
      .coerceIn(0.0, 1.0)
  val highFrequencySensitivity = .82 + .58 * position.pow(1.35)
  val lowFrequencyCeiling =
    .64 + .36 * musicSpectrumSmoothStep(((position - .18) / .30).coerceIn(0.0, 1.0))
  return (normalizedMagnitude * highFrequencySensitivity)
    .coerceAtMost(lowFrequencyCeiling)
    .coerceIn(0.0, 1.0)
    .toFloat()
}

private fun musicSpectrumSmoothStep(value: Double): Double = value * value * (3.0 - 2.0 * value)

internal fun musicSpectrumLevelGain(rms: Double): Double {
  val level = rms.coerceIn(0.0, 1.0)
  val compressed = ln(1.0 + level * 80.0) / ln(81.0)
  // 软拐点替代了旧的硬门限，让安静段落淡入/淡出而不是闪烁。
  return (.58 + .42 * compressed) * musicSoftKnee(level)
}

/** 从 0 到 1 的平滑阶跃，跨越 [knee - width, knee + width]，去掉开/关门限。 */
internal fun musicSoftKnee(level: Double): Double {
  val knee = 0.000_02
  val width = 0.000_01
  if (level <= knee - width) return 0.0
  if (level >= knee + width) return 1.0
  val t = (level - (knee - width)) / (2.0 * width)
  return t * t * (3.0 - 2.0 * t)
}

internal fun musicFrameRms(samples: FloatArray): Float {
  if (samples.isEmpty()) return 0f
  var sum = 0.0
  for (sample in samples) sum += sample.toDouble() * sample
  return sqrt(sum / samples.size).toFloat()
}

/**
 * 输出延迟估计的非对称 EMA：快速上升、缓慢回落，并夹在 [min, max] 之间。
 */
internal fun advanceOutputLatencyEstimate(
  estimateMs: Long,
  observedLeadMs: Long,
  minMs: Long,
  maxMs: Long,
): Long {
  val rate = if (observedLeadMs > estimateMs) 0.15 else 0.01
  return (estimateMs + (observedLeadMs - estimateMs) * rate).toLong().coerceIn(minMs, maxMs)
}

/**
 * 逐频段峰值保持：保持的峰值按指数衰减，但永远不会低于当前值。
 */
internal fun advanceMusicPeak(
  current: Float,
  peak: Float,
  elapsedMillis: Double,
  holdMillis: Double,
): Float = maxOf(current, (peak * exp(-elapsedMillis / holdMillis)).toFloat())

/**
 * 轻量级能量起音检测器，在打击性瞬态上增强可视化器。它把快速短期能量 EMA 与缓慢的
 * 长期 EMA 相比，比率超过起音阈值时产生尖峰，并在后续帧中让尖峰衰减。
 */
internal class MusicBeatTracker(
  private val onsetThreshold: Float = 1.4f,
  private val shortTermAlpha: Float = 0.30f,
  private val longTermAlpha: Float = 0.04f,
  private val decayPerFrame: Float = 0.06f,
  private val strength: Float = 0.3f,
) {
  private var shortTerm = 0f
  private var longTerm = 0f
  private var beatLevel = 0f

  fun next(rms: Float): Float {
    val level = rms.coerceAtLeast(0f)
    if (shortTerm == 0f) {
      shortTerm = level
      longTerm = level
    } else {
      shortTerm += (level - shortTerm) * shortTermAlpha
      longTerm += (level - longTerm) * longTermAlpha
    }
    val ratio = if (longTerm > 1e-6f) shortTerm / longTerm else 0f
    if (ratio > onsetThreshold && shortTerm > 1e-4f) {
      beatLevel = 1f
    } else {
      beatLevel = (beatLevel * (1f - decayPerFrame)).coerceAtLeast(0f)
    }
    return 1f + beatLevel * strength
  }

  fun reset() {
    shortTerm = 0f
    longTerm = 0f
    beatLevel = 0f
  }
}

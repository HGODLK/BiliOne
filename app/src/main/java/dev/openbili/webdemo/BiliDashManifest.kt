package dev.openbili.webdemo

import dev.openbili.webdemo.api.AudioStream
import dev.openbili.webdemo.api.PlayUrlData
import java.util.Locale

/**
 * 把 B 站单文件 fMP4 音视频资源包装成 ISO-BMFF 按需 DASH 清单。
 *
 * API 已经提供了初始化和 SIDX 字节范围。把这些范围保留在 SegmentBase 中，让 Media3
 * 可以直接调度原始文件，同时把每个画质和音频变体暴露为真正的轨道。
 */
internal object BiliDashManifest {
  /**
   * 当响应中没有可用 SegmentBase 描述的 fMP4 流时返回 null。调用方随后可以使用渐进式
   * 兼容源，而不是把不完整的 DASH 响应当作致命播放错误。
   */
  fun build(playData: PlayUrlData): String? {
    val durationSeconds = playData.durationMs.coerceAtLeast(1L) / 1000.0
    val duration = String.format(Locale.US, "PT%.3fS", durationSeconds)
    val videoRepresentations =
      playData.streams.mapIndexedNotNull { index, stream ->
        if (
          stream.url.isBlank() ||
            stream.initializationRange.isBlank() ||
            stream.indexRange.isBlank()
        ) {
          null
        } else {
          buildString {
            append("""      <Representation id="${videoTrackId(index)}"""")
            append(""" bandwidth="${stream.bandwidth.coerceAtLeast(1L)}"""")
            append(""" mimeType="${xml(stream.mimeType)}"""")
            append(""" codecs="${xml(stream.codecs)}"""")
            if (stream.width > 0) append(""" width="${stream.width}"""")
            if (stream.height > 0) append(""" height="${stream.height}"""")
            if (stream.frameRate > 0f) {
              append(
                """ frameRate="${String.format(Locale.US, "%.3f", stream.frameRate).trimEnd('0').trimEnd('.')}""""
              )
            }
            append(">\n")
            appendBaseUrls(stream.url, stream.backupUrls, "video_${index}")
            append(
              """        <SegmentBase indexRange="${xml(stream.indexRange)}" indexRangeExact="true">"""
            )
            append('\n')
            append("""          <Initialization range="${xml(stream.initializationRange)}" />""")
            append('\n')
            append("        </SegmentBase>\n")
            append("      </Representation>")
          }
        }
      }
    val audioRepresentations =
      audioTracks(playData).mapNotNull { (trackId, stream, isAtmos) ->
        audioRepresentation(trackId, stream, isAtmos)
      }
    if (videoRepresentations.isEmpty()) return null
    return buildManifest(duration, videoRepresentations, audioRepresentations)
  }

  /** 只生成音频 AdaptationSet，供音乐页的独立音频主播放器使用。 */
  fun buildAudioOnly(playData: PlayUrlData): String? {
    val durationSeconds = playData.durationMs.coerceAtLeast(1L) / 1000.0
    val duration = String.format(Locale.US, "PT%.3fS", durationSeconds)
    val audioRepresentations =
      audioTracks(playData).mapNotNull { (trackId, stream, isAtmos) ->
        audioRepresentation(trackId, stream, isAtmos)
      }
    if (audioRepresentations.isEmpty()) return null
    return buildManifest(duration, emptyList(), audioRepresentations)
  }

  private fun buildManifest(
    duration: String,
    videoRepresentations: List<String>,
    audioRepresentations: List<String>,
  ): String =
    buildString {
      append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
      append(
        """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" mediaPresentationDuration="$duration" minBufferTime="PT1.5S">"""
      )
      append('\n')
      append("""  <Period id="0" start="PT0S" duration="$duration">""").append('\n')
      if (videoRepresentations.isNotEmpty()) {
        append(
          """    <AdaptationSet id="1" contentType="video" segmentAlignment="true" startWithSAP="1">"""
        )
        append('\n')
        videoRepresentations.forEach { append(it).append('\n') }
        append("    </AdaptationSet>\n")
      }
      if (audioRepresentations.isNotEmpty()) {
        append(
          """    <AdaptationSet id="2" contentType="audio" segmentAlignment="true" startWithSAP="1">"""
        )
        append('\n')
        append("""      <Role schemeIdUri="urn:mpeg:dash:role:2011" value="main" />""").append('\n')
        audioRepresentations.forEach { append(it).append('\n') }
        append("    </AdaptationSet>\n")
      }
      append("  </Period>\n")
      append("</MPD>\n")
    }

  fun videoTrackId(index: Int): String = "bili_video_$index"

  fun audioTrackId(playData: PlayUrlData): String =
    when (playData.premiumAudioMode) {
      dev.openbili.webdemo.api.PremiumAudioMode.DOLBY -> DOLBY_AUDIO_ID
      dev.openbili.webdemo.api.PremiumAudioMode.HI_RES -> HI_RES_AUDIO_ID
      null -> DEFAULT_AUDIO_ID
    }

  private fun audioTracks(playData: PlayUrlData): List<Triple<String, AudioStream, Boolean>> =
    buildList {
        playData.dashAudio?.let { add(Triple(DEFAULT_AUDIO_ID, it, false)) }
        playData.dolbyAudio?.let { add(Triple(DOLBY_AUDIO_ID, it, true)) }
        playData.hiResAudio?.let { add(Triple(HI_RES_AUDIO_ID, it, false)) }
      }
      .distinctBy { it.first }

  private fun audioRepresentation(
    trackId: String,
    stream: AudioStream,
    isAtmos: Boolean,
  ): String? {
    if (
      stream.url.isBlank() || stream.initializationRange.isBlank() || stream.indexRange.isBlank()
    ) {
      return null
    }
    return buildString {
      append("""      <Representation id="$trackId"""")
      append(""" bandwidth="${stream.bandwidth.coerceAtLeast(1L)}"""")
      append(""" mimeType="${xml(stream.mimeType)}"""")
      append(""" codecs="${xml(stream.codecs)}">""").append('\n')
      if (isAtmos && stream.codecs.startsWith("ec-3", ignoreCase = true)) {
        append(
          """        <SupplementalProperty schemeIdUri="tag:dolby.com,2018:dash:EC3_ExtensionType:2018" value="JOC" />"""
        )
        append('\n')
      }
      appendBaseUrls(stream.url, stream.backupUrls, trackId)
      append(
        """        <SegmentBase indexRange="${xml(stream.indexRange)}" indexRangeExact="true">"""
      )
      append('\n')
      append("""          <Initialization range="${xml(stream.initializationRange)}" />""")
        .append('\n')
      append("        </SegmentBase>\n")
      append("      </Representation>")
    }
  }

  private fun xml(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("\"", "&quot;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("'", "&apos;")

  private fun StringBuilder.appendBaseUrls(
    primaryUrl: String,
    backupUrls: List<String>,
    servicePrefix: String,
  ) {
    (listOf(primaryUrl) + backupUrls)
      .filter { it.isNotBlank() }
      .distinct()
      .forEachIndexed { index, url ->
        append(
            """        <BaseURL serviceLocation="${xml(servicePrefix)}_$index" priority="${index + 1}">${xml(url)}</BaseURL>"""
          )
          .append('\n')
      }
  }

  private const val DEFAULT_AUDIO_ID = "bili_audio_default"
  private const val DOLBY_AUDIO_ID = "bili_audio_dolby"
  private const val HI_RES_AUDIO_ID = "bili_audio_hires"
}

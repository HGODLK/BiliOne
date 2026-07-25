package dev.openbili.webdemo

import dev.openbili.webdemo.api.AudioStream
import dev.openbili.webdemo.api.PlayUrlData
import java.util.Locale

/**
 * Wraps Bilibili's single-file fMP4 video/audio resources in an ISO-BMFF on-demand DASH manifest.
 *
 * The API already supplies the initialization and SIDX byte ranges. Keeping those ranges in
 * SegmentBase lets Media3 schedule the original files directly while exposing every quality and
 * audio variant as a real track.
 */
internal object BiliDashManifest {
  /**
   * Returns null when the response has no fMP4 streams that can be described by SegmentBase.
   * The caller can then use the progressive compatibility source instead of treating a partial
   * DASH response as a fatal playback error.
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
            append(
              """          <Initialization range="${xml(stream.initializationRange)}" />"""
            )
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
    return buildString {
      append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
      append(
        """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" mediaPresentationDuration="$duration" minBufferTime="PT1.5S">"""
      )
      append('\n')
      append("""  <Period id="0" start="PT0S" duration="$duration">""").append('\n')
      append(
        """    <AdaptationSet id="1" contentType="video" segmentAlignment="true" startWithSAP="1">"""
      )
      append('\n')
      videoRepresentations.forEach { append(it).append('\n') }
      append("    </AdaptationSet>\n")
      if (audioRepresentations.isNotEmpty()) {
        append(
          """    <AdaptationSet id="2" contentType="audio" segmentAlignment="true" startWithSAP="1">"""
        )
        append('\n')
        append("""      <Role schemeIdUri="urn:mpeg:dash:role:2011" value="main" />""")
          .append('\n')
        audioRepresentations.forEach { append(it).append('\n') }
        append("    </AdaptationSet>\n")
      }
      append("  </Period>\n")
      append("</MPD>\n")
    }
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
    }.distinctBy { it.first }

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
        ).append('\n')
      }
  }

  private const val DEFAULT_AUDIO_ID = "bili_audio_default"
  private const val DOLBY_AUDIO_ID = "bili_audio_dolby"
  private const val HI_RES_AUDIO_ID = "bili_audio_hires"
}

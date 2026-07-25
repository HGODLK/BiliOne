package dev.openbili.webdemo.settings

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Display

data class DeviceMediaCapabilities(
  val supportsHdr10: Boolean,
  val supportsDolbyVision: Boolean,
  val supportsDolbyAtmos: Boolean,
) {
  companion object {
    fun detect(context: Context): DeviceMediaCapabilities {
      val codecInfos =
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
          }
          .getOrDefault(emptyList())
      val display =
        (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
          ?.getDisplay(Display.DEFAULT_DISPLAY)
      val hdrTypes =
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
              display?.mode?.supportedHdrTypes ?: intArrayOf()
            } else {
              @Suppress("DEPRECATION")
              display?.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
            }
          }
          .getOrDefault(intArrayOf())
      val hasDolbyVisionDisplay = Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdrTypes
      val hasHdr10Display =
        Display.HdrCapabilities.HDR_TYPE_HDR10 in hdrTypes ||
          Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in hdrTypes
      val hasDolbyVisionDecoder =
        codecInfos.supportsMime(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION)
      val hasHdr10Decoder =
        codecInfos.any { codec ->
          codec.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) } &&
            runCatching {
                codec
                  .getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                  .profileLevels
                  .any { level ->
                    level.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                      level.profile ==
                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus ||
                      level.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                  }
              }
              .getOrDefault(false)
        }
      val hasAtmosDecoder = codecInfos.supportsMime(MediaFormat.MIMETYPE_AUDIO_EAC3_JOC)
      return DeviceMediaCapabilities(
        supportsHdr10 = hasHdr10Display && hasHdr10Decoder,
        supportsDolbyVision = hasDolbyVisionDisplay && hasDolbyVisionDecoder,
        supportsDolbyAtmos = hasAtmosDecoder,
      )
    }

    private fun List<MediaCodecInfo>.supportsMime(mime: String): Boolean =
      any { codec -> codec.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
  }
}

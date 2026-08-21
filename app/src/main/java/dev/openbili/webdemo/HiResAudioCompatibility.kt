package dev.openbili.webdemo

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Android 厂商可以自由地把自己实现的 FLAC 解码器排在 AOSP 实现之前。其中一些解码器
 * 声明支持 FLAC，却保留 32 KiB 的输入缓冲，小于合法的 B 站 Hi-Res FLAC 访问单元。
 * 在所有 Android 构建上对 FLAC 优先使用 Media3 的软件排序，并显式留出足够空间容纳
 * 高分辨率无损帧。
 */
@OptIn(UnstableApi::class)
internal open class HiResCompatibleRenderersFactory(
  context: Context,
  preferSoftwareAudioDecoders: Boolean = false,
) : DefaultRenderersFactory(context) {
  init {
    setEnableDecoderFallback(true)
    setMediaCodecSelector(
      MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val selector =
          if (
            mimeType.equals(MimeTypes.AUDIO_FLAC, ignoreCase = true) ||
              (preferSoftwareAudioDecoders && MimeTypes.isAudio(mimeType))
          ) {
            MediaCodecSelector.PREFER_SOFTWARE
          } else {
            MediaCodecSelector.DEFAULT
          }
        selector
          .getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
          )
          .let { decoders ->
            if (
              mimeType.equals(MimeTypes.AUDIO_FLAC, ignoreCase = true) ||
                (preferSoftwareAudioDecoders && MimeTypes.isAudio(mimeType))
            ) {
              // 一些 OEM 的 FLAC 实现本身被标记为纯软件，因此 Media3 通用的软件偏好仍可能
              // 让它们排在 AOSP 解码器之前。平台解码器名称在 OEM Android 构建之间保持稳定，
              // 构成最终且厂商中立的决胜条件。
              decoders.sortedBy { decoder -> hiResAudioDecoderRank(decoder.name) }
            } else {
              decoders
            }
          }
      }
    )
  }

  override fun buildAudioRenderers(
    context: Context,
    extensionRendererMode: Int,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    audioSink: AudioSink,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    out: ArrayList<Renderer>,
  ) {
    val firstRendererIndex = out.size
    super.buildAudioRenderers(
      context,
      extensionRendererMode,
      mediaCodecSelector,
      enableDecoderFallback,
      audioSink,
      eventHandler,
      eventListener,
      out,
    )
    val mediaCodecRendererIndex =
      (firstRendererIndex until out.size).firstOrNull { index ->
        out[index] is MediaCodecAudioRenderer
      } ?: return
    out[mediaCodecRendererIndex] =
      HiResMediaCodecAudioRenderer(
        context = context,
        codecAdapterFactory = getCodecAdapterFactory(),
        mediaCodecSelector = mediaCodecSelector,
        enableDecoderFallback = enableDecoderFallback,
        eventHandler = eventHandler,
        eventListener = eventListener,
        audioSink = audioSink,
      )
  }
}

@OptIn(UnstableApi::class)
private class HiResMediaCodecAudioRenderer(
  context: Context,
  codecAdapterFactory: MediaCodecAdapter.Factory,
  mediaCodecSelector: MediaCodecSelector,
  enableDecoderFallback: Boolean,
  eventHandler: Handler,
  eventListener: AudioRendererEventListener,
  audioSink: AudioSink,
) :
  MediaCodecAudioRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    audioSink,
  ) {
  override fun getCodecMaxInputSize(
    codecInfo: MediaCodecInfo,
    format: Format,
    streamFormats: Array<out Format>,
  ): Int =
    hiResCodecMaxInputSize(
      sampleMimeType = format.sampleMimeType,
      defaultMaxInputSize = super.getCodecMaxInputSize(codecInfo, format, streamFormats),
    )
}

internal const val HI_RES_FLAC_MAX_INPUT_SIZE_BYTES = 512 * 1024

internal fun hiResAudioDecoderRank(name: String): Int =
  when {
    name.startsWith("c2.android.", ignoreCase = true) -> 0
    name.startsWith("OMX.google.", ignoreCase = true) -> 1
    name.contains("google", ignoreCase = true) -> 1
    else -> 2
  }

internal fun hiResCodecMaxInputSize(sampleMimeType: String?, defaultMaxInputSize: Int): Int =
  if (sampleMimeType.equals(MimeTypes.AUDIO_FLAC, ignoreCase = true)) {
    maxOf(defaultMaxInputSize, HI_RES_FLAC_MAX_INPUT_SIZE_BYTES)
  } else {
    defaultMaxInputSize
  }

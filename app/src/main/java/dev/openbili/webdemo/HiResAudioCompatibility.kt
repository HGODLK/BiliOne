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
 * Android vendors are free to rank their own FLAC decoder ahead of the AOSP implementation.
 * Some of those decoders advertise FLAC support but keep a 32 KiB input buffer, which is smaller
 * than a valid Bilibili Hi-Res FLAC access unit. Prefer Media3's software ordering for FLAC on every
 * Android build and explicitly reserve enough room for high-resolution lossless frames.
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
              // Some OEM FLAC implementations are themselves marked software-only, so Media3's
              // generic software preference can still leave them ahead of the AOSP decoder. The
              // platform decoder names are stable across OEM Android builds and form the final,
              // vendor-neutral tie break.
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
) : MediaCodecAudioRenderer(
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

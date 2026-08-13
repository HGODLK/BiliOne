package dev.openbili.webdemo

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class HiResAudioCompatibilityTest {
  @Test
  fun flacAlwaysReceivesCapacityForLargeLosslessFrames() {
    assertEquals(
      HI_RES_FLAC_MAX_INPUT_SIZE_BYTES,
      hiResCodecMaxInputSize(MimeTypes.AUDIO_FLAC, Format.NO_VALUE),
    )
    assertEquals(
      HI_RES_FLAC_MAX_INPUT_SIZE_BYTES,
      hiResCodecMaxInputSize(MimeTypes.AUDIO_FLAC, 32 * 1024),
    )
  }

  @Test
  fun largerExtractorCapacityIsNeverReduced() {
    val extractorCapacity = HI_RES_FLAC_MAX_INPUT_SIZE_BYTES * 2

    assertEquals(
      extractorCapacity,
      hiResCodecMaxInputSize(MimeTypes.AUDIO_FLAC, extractorCapacity),
    )
  }

  @Test
  fun nonFlacFormatsKeepTheirOriginalCapacity() {
    assertEquals(48 * 1024, hiResCodecMaxInputSize(MimeTypes.AUDIO_AAC, 48 * 1024))
  }
}

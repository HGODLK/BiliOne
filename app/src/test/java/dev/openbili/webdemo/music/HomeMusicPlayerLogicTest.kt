package dev.openbili.webdemo.music

import dev.openbili.webdemo.hiResAudioDecoderRank
import androidx.media3.common.Player
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.FeedCard
import dev.openbili.webdemo.api.VideoStream
import dev.openbili.webdemo.ui.formatMusicDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class HomeMusicPlayerLogicTest {
  @Test
  fun musicFolderRequiresExactTrimmedTitle() {
    val folders =
      listOf(
        FavoriteFolder(1L, "音乐收藏", 3),
        FavoriteFolder(2L, " 音乐 ", 5),
        FavoriteFolder(3L, "音乐", 8),
      )

    assertEquals(2L, findMusicFavoriteFolder(folders)?.id)
    assertNull(findMusicFavoriteFolder(listOf(FavoriteFolder(4L, "我的音乐", 1))))
    assertEquals(3L, findMusicFavoriteFolder(folders, preferredFolderId = 3L)?.id)
    assertNull(findMusicFavoriteFolder(folders, preferredFolderId = 99L))
  }

  @Test
  fun musicPageWaitsForTheRememberedTrackResourceBeforeEntering() {
    val item =
      musicFeedItem(
        FeedCard(
          aid = 1L,
          bvid = "BV1",
          cid = 1L,
          title = "歌",
          coverUrl = "",
          uploaderName = "UP",
          uploaderFace = "",
          uploaderMid = 1L,
          playCount = 0L,
          danmakuCount = 0L,
          durationSeconds = 60L,
          pubdate = 0L,
        )
      )

    assertTrue(!isMusicLaunchReady(HomeMusicUiState(libraryStatus = MusicLibraryStatus.LOADING)))
    assertTrue(
      !isMusicLaunchReady(
        HomeMusicUiState(
          libraryStatus = MusicLibraryStatus.READY,
          items = listOf(item),
          currentItem = item,
          playbackLoading = true,
        )
      )
    )
    assertTrue(
      isMusicLaunchReady(
        HomeMusicUiState(
          libraryStatus = MusicLibraryStatus.READY,
          items = listOf(item),
          currentItem = item,
          playbackLoading = false,
        )
      )
    )
    assertTrue(isMusicLaunchReady(HomeMusicUiState(libraryStatus = MusicLibraryStatus.MISSING)))
  }

  @Test
  fun favoriteVideoMapsToStablePlayableFeedItem() {
    val item =
      musicFeedItem(
        FeedCard(
          aid = 123L,
          bvid = "BV1234567890",
          cid = 456L,
          title = "测试音乐",
          coverUrl = "https://example.com/cover.webp",
          uploaderName = "测试 UP",
          uploaderFace = "",
          uploaderMid = 9L,
          playCount = 12L,
          danmakuCount = 3L,
          durationSeconds = 65L,
          pubdate = 10L,
          resourceType = 2,
        )
      )

    assertEquals("BV1234567890", item.id)
    assertEquals("https://www.bilibili.com/video/BV1234567890", item.videoUrl)
    assertEquals("1:05", item.duration)
    assertEquals(123L, item.favoriteResourceId)
    assertEquals(2, item.favoriteResourceType)
  }

  @Test
  fun progressTimeFormattingSupportsHours() {
    assertEquals("0:00", formatMusicDuration(0L))
    assertEquals("1:05", formatMusicDuration(65_000L))
    assertEquals("1:02:03", formatMusicDuration(3_723_000L))
  }

  @Test
  fun musicPlaybackPinsExact720Stream() {
    val streams =
      listOf(
        stream(id = 80, height = 1080),
        stream(id = 64, height = 720),
        stream(id = 32, height = 480),
      )

    assertEquals(1, selectMusic720StreamIndex(streams))
  }

  @Test
  fun musicPlaybackFallsBackToHighestTierNotAbove720() {
    val streams =
      listOf(
        stream(id = 80, height = 1080),
        stream(id = 48, height = 720),
        stream(id = 32, height = 480),
      )

    assertEquals(1, selectMusic720StreamIndex(streams))
  }

  @Test
  fun sequentialPlaybackWrapsInBothDirections() {
    assertEquals(0, adjacentMusicIndex(3, 2, MusicPlaybackOrder.SEQUENTIAL, direction = 1))
    assertEquals(2, adjacentMusicIndex(3, 0, MusicPlaybackOrder.SEQUENTIAL, direction = -1))
  }

  @Test
  fun randomPlaybackNeverSelectsCurrentItem() {
    assertEquals(
      2,
      adjacentMusicIndex(
        itemCount = 4,
        currentIndex = 1,
        order = MusicPlaybackOrder.RANDOM,
        direction = 1,
        randomValue = 1,
      ),
    )
  }

  @Test
  fun playbackRetryBacksOffAndCapsDelay() {
    assertEquals(0L, musicRetryDelayMillis(0))
    assertEquals(3_200L, musicRetryDelayMillis(3))
    assertEquals(15_000L, musicRetryDelayMillis(100))
  }

  @Test
  fun foregroundRecoveryOnlyRebuildsAHealthyPlayerAfterARealBackgroundStall() {
    assertTrue(
      !needsMusicForegroundRecovery(
        backgroundDurationMs = 1_000L,
        firstFrameReady = true,
        playbackState = Player.STATE_READY,
        playerHasError = false,
        shouldBePlaying = true,
        isPlaying = true,
      )
    )
    assertTrue(
      needsMusicForegroundRecovery(
        backgroundDurationMs = MUSIC_FOREGROUND_RECOVERY_THRESHOLD_MS,
        firstFrameReady = false,
        playbackState = Player.STATE_READY,
        playerHasError = false,
        shouldBePlaying = true,
        isPlaying = false,
      )
    )
    assertTrue(
      !needsMusicForegroundRecovery(
        backgroundDurationMs = MUSIC_FOREGROUND_RECOVERY_THRESHOLD_MS,
        firstFrameReady = true,
        playbackState = Player.STATE_READY,
        playerHasError = false,
        shouldBePlaying = true,
        isPlaying = true,
      )
    )
    assertTrue(
      needsMusicForegroundRecovery(
        backgroundDurationMs = 0L,
        firstFrameReady = true,
        playbackState = Player.STATE_IDLE,
        playerHasError = false,
        shouldBePlaying = false,
        isPlaying = false,
      )
    )
  }

  @Test
  fun compatibilityDecoderPrefersPlatformSoftwareBeforeAnyOemCodec() {
    assertEquals(0, hiResAudioDecoderRank("c2.android.flac.decoder"))
    assertEquals(1, hiResAudioDecoderRank("OMX.google.flac.decoder"))
    assertEquals(2, hiResAudioDecoderRank("c2.vendor.flac.decoder"))
    assertEquals(2, hiResAudioDecoderRank("OMX.oem.flac.decoder"))
  }

  @Test
  fun realSpectrumPlacesPureToneInItsFrequencyBand() {
    val sampleRate = 48_000
    val frequency = 440.0
    val samples =
      FloatArray(1_024) { index ->
        (sin(2.0 * PI * frequency * index / sampleRate) * .55).toFloat()
      }

    val bands = analyzeMusicSpectrum(samples, sampleRate)
    val peakBand = bands.indices.maxBy { bands[it] }

    assertEquals(MUSIC_SPECTRUM_BAND_COUNT, bands.size)
    assertTrue("440Hz should land near logarithmic band 10, got $peakBand", peakBand in 9..11)
    assertTrue(bands[peakBand] > .4f)
  }

  @Test
  fun realSpectrumReturnsSilenceForSilentPcm() {
    val bands = analyzeMusicSpectrum(FloatArray(1_024), 48_000)

    assertTrue(bands.all { it == 0f })
  }

  @Test
  fun realSpectrumPreservesHeightWhileStillShowingSourceLevel() {
    val sampleRate = 48_000
    fun tone(amplitude: Double) =
      FloatArray(1_024) { index ->
        (sin(2.0 * PI * 440.0 * index / sampleRate) * amplitude).toFloat()
      }

    val quietPeak = analyzeMusicSpectrum(tone(.04), sampleRate).maxOrNull() ?: 0f
    val loudPeak = analyzeMusicSpectrum(tone(.8), sampleRate).maxOrNull() ?: 0f

    assertTrue(quietPeak > .6f)
    assertTrue(loudPeak > quietPeak)
    assertTrue(loudPeak - quietPeak < .35f)
  }

  @Test
  fun spectrumLevelIsMoreSensitiveAtLowVolumeThanHighVolume() {
    val quietStep = musicSpectrumLevelGain(.03) - musicSpectrumLevelGain(.02)
    val loudStep = musicSpectrumLevelGain(.80) - musicSpectrumLevelGain(.79)

    assertTrue(quietStep > loudStep)
  }

  @Test
  fun realSpectrumFollowsActualSystemMediaVolume() {
    val sampleRate = 48_000
    val samples =
      FloatArray(1_024) { index ->
        (sin(2.0 * PI * 440.0 * index / sampleRate) * .6).toFloat()
      }

    val muted = analyzeMusicSpectrum(samples, sampleRate, systemVolume = 0f)
    val quiet = analyzeMusicSpectrum(samples, sampleRate, systemVolume = .1f).maxOrNull() ?: 0f
    val loud = analyzeMusicSpectrum(samples, sampleRate, systemVolume = .9f).maxOrNull() ?: 0f

    assertTrue(muted.all { it == 0f })
    assertTrue(quiet > 0f)
    assertTrue(loud > quiet)
  }

  private fun stream(id: Int, height: Int): VideoStream =
    VideoStream(
      id = id,
      quality = "${height}P",
      url = "https://example.com/$id.m4s",
      codecId = 7,
      codecs = "avc1",
      width = height * 16 / 9,
      height = height,
    )
}

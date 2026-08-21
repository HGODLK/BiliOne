package dev.openbili.webdemo.music

import androidx.media3.common.C
import androidx.media3.common.Player
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.FeedCard
import dev.openbili.webdemo.api.VideoStream
import dev.openbili.webdemo.hiResAudioDecoderRank
import dev.openbili.webdemo.settings.PreferredResolutionMode
import dev.openbili.webdemo.ui.formatMusicDuration
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
  fun firstMusicEntryRequiresAnExplicitFolderChoice() {
    val folders = listOf(FavoriteFolder(2L, "音乐", 5))

    assertNull(
      resolveMusicFavoriteFolder(
        folders = folders,
        preferredFolderId = 0L,
        folderSelectionConfigured = false,
      )
    )
    assertEquals(
      2L,
      resolveMusicFavoriteFolder(
          folders = folders,
          preferredFolderId = 0L,
          folderSelectionConfigured = true,
        )
        ?.id,
    )
  }

  @Test
  fun deletedSelectedMusicFolderReturnsToFolderChoice() {
    val remainingFolders = listOf(FavoriteFolder(7L, "其他收藏", 3))

    assertNull(
      resolveMusicFavoriteFolder(
        folders = remainingFolders,
        preferredFolderId = 99L,
        folderSelectionConfigured = true,
      )
    )
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
  fun vipMusicPlaybackDefaultsTo1080P60Then1080PPlus() {
    val streams =
      listOf(
        stream(id = 120, height = 2160),
        stream(id = 116, height = 1080),
        stream(id = 112, height = 1080),
        stream(id = 80, height = 1080),
        stream(id = 64, height = 720),
      )

    assertEquals(
      1,
      selectMusicStreamIndex(streams, PreferredResolutionMode.ULTRA_HIGH, vipActive = true),
    )
    val without60Fps = streams.filterNot { it.id == 116 }
    assertEquals(
      112,
      without60Fps[
          selectMusicStreamIndex(
            without60Fps,
            PreferredResolutionMode.ULTRA_HIGH,
            vipActive = true,
          )]
        .id,
    )
  }

  @Test
  fun nonVipMusicPlaybackIsCappedAtRegular1080P() {
    val streams =
      listOf(
        stream(id = 116, height = 1080),
        stream(id = 112, height = 1080),
        stream(id = 80, height = 1080),
        stream(id = 64, height = 720),
        stream(id = 32, height = 480),
      )

    assertEquals(
      2,
      selectMusicStreamIndex(streams, PreferredResolutionMode.ULTRA_HIGH, vipActive = false),
    )
  }

  @Test
  fun musicPlaybackRespectsLowerConfiguredQuality() {
    val streams =
      listOf(
        stream(id = 116, height = 1080),
        stream(id = 80, height = 1080),
        stream(id = 64, height = 720),
        stream(id = 32, height = 480),
      )

    assertEquals(
      2,
      selectMusicStreamIndex(streams, PreferredResolutionMode.MEDIUM, vipActive = true),
    )
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
  fun playbackOrderCyclesThroughSingleRepeat() {
    assertEquals(
      MusicPlaybackOrder.RANDOM,
      nextMusicPlaybackOrder(MusicPlaybackOrder.SEQUENTIAL),
    )
    assertEquals(
      MusicPlaybackOrder.SINGLE_REPEAT,
      nextMusicPlaybackOrder(MusicPlaybackOrder.RANDOM),
    )
    assertEquals(
      MusicPlaybackOrder.SEQUENTIAL,
      nextMusicPlaybackOrder(MusicPlaybackOrder.SINGLE_REPEAT),
    )
    assertEquals(Player.REPEAT_MODE_ONE, musicRepeatMode(MusicPlaybackOrder.SINGLE_REPEAT))
    assertEquals(Player.REPEAT_MODE_OFF, musicRepeatMode(MusicPlaybackOrder.RANDOM))
  }

  @Test
  fun explicitSkipStillMovesToAdjacentTrackDuringSingleRepeat() {
    assertEquals(
      2,
      adjacentMusicIndex(3, 1, MusicPlaybackOrder.SINGLE_REPEAT, direction = 1),
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
  fun spectrumLimitsBassAndMakesHighFrequenciesMoreSensitive() {
    val lowFrequency = shapeMusicSpectrumBand(1.0, 80.0, 55.0, 16_000.0)
    val highFrequency = shapeMusicSpectrumBand(.8, 8_000.0, 55.0, 16_000.0)

    assertTrue("bass should have a soft visual ceiling", lowFrequency < .7f)
    assertTrue("high frequency should remain visible at lower energy", highFrequency > lowFrequency)
    assertTrue(highFrequency <= 1f)
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

  @Test
  fun spectrumFrameWaitsUntilPlaybackReachesItsPcmTimestamp() {
    assertEquals(
      240L + MUSIC_SPECTRUM_OUTPUT_LATENCY_MS,
      musicSpectrumFrameWaitMillis(1_240_000L, 1_000L),
    )
    assertEquals(560L, musicSpectrumFrameWaitMillis(1_240_000L, 1_000L, outputLatencyMs = 320L))
    assertEquals(MUSIC_SPECTRUM_OUTPUT_LATENCY_MS, musicSpectrumFrameWaitMillis(1_000_000L, 1_000L))
    assertEquals(
      MUSIC_SPECTRUM_OUTPUT_LATENCY_MS,
      musicSpectrumFrameWaitMillis(C.TIME_UNSET, 1_000L),
    )
    assertEquals(MUSIC_SPECTRUM_OUTPUT_LATENCY_MS, musicSpectrumFrameWaitMillis(8_000_000L, 1_000L))
    assertEquals(
      MUSIC_SPECTRUM_OUTPUT_LATENCY_MS - 100L,
      musicSpectrumFrameWaitMillis(C.TIME_UNSET, 1_000L, elapsedSinceCaptureMs = 100L),
    )
    assertEquals(
      320L + MUSIC_SPECTRUM_OUTPUT_LATENCY_MS,
      musicSpectrumFrameWaitMillis(
        presentationTimeUs = 1_240_000L,
        playbackPositionMs = 1_000L,
        audioSinkPositionUs = 920_000L,
      ),
    )
    assertEquals(
      0L,
      musicSpectrumFrameWaitMillis(
        presentationTimeUs = 1_240_000L,
        playbackPositionMs = 1_240L,
        elapsedSinceCaptureMs = 440L,
        audioSinkPositionUs = 920_000L,
      ),
    )
  }

  @Test
  fun softKneeFadesGainInAndOutAroundTheNoiseFloor() {
    assertEquals(0.0, musicSpectrumLevelGain(0.0), 0.0)
    // 拐点内部（1e-5 .. 3e-5）增益是一个小的非零分数，而不是硬性的 0/1。
    assertTrue(musicSpectrumLevelGain(0.000_02) > 0.0)
    assertTrue(musicSpectrumLevelGain(0.000_02) < musicSpectrumLevelGain(0.001))
    assertEquals(1.0, musicSoftKnee(0.001), 0.0)
    assertEquals(0.0, musicSoftKnee(0.0), 0.0)
  }

  @Test
  fun peakHoldDecaysSlowlyButNeverBelowCurrent() {
    val held =
      advanceMusicPeak(current = 0.2f, peak = 0.9f, elapsedMillis = 100.0, holdMillis = 500.0)
    assertTrue(held > 0.2f)
    assertTrue(held < 0.9f)
    assertEquals(
      0.2f,
      advanceMusicPeak(current = 0.2f, peak = 0.05f, elapsedMillis = 1.0, holdMillis = 500.0),
      0.0f,
    )
  }

  @Test
  fun beatTrackerSpikesOnOnsetAndDecaysAfterward() {
    val tracker = MusicBeatTracker()
    repeat(50) { tracker.next(0.01f) }
    assertEquals(1.0f, tracker.next(0.01f), 0.0001f)
    val onBeat = tracker.next(0.9f)
    assertTrue(onBeat > 1.1f)
    var boost = onBeat
    repeat(20) { boost = tracker.next(0.01f) }
    assertTrue(boost < onBeat)
    assertTrue(boost >= 1.0f)
  }

  @Test
  fun outputLatencyEstimateClimbsFastAndRelaxesSlowly() {
    val climbed =
      advanceOutputLatencyEstimate(
        estimateMs = 220L,
        observedLeadMs = 400L,
        minMs = 60L,
        maxMs = 1_000L,
      )
    assertTrue(climbed > 220L && climbed < 400L)
    val relaxed =
      advanceOutputLatencyEstimate(
        estimateMs = 400L,
        observedLeadMs = 100L,
        minMs = 60L,
        maxMs = 1_000L,
      )
    assertTrue(relaxed < 400L && relaxed > 100L)
    assertEquals(
      60L,
      advanceOutputLatencyEstimate(
        estimateMs = 60L,
        observedLeadMs = 10L,
        minMs = 60L,
        maxMs = 1_000L,
      ),
    )
    assertEquals(
      1_000L,
      advanceOutputLatencyEstimate(
        estimateMs = 990L,
        observedLeadMs = 2_000L,
        minMs = 60L,
        maxMs = 1_000L,
      ),
    )
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

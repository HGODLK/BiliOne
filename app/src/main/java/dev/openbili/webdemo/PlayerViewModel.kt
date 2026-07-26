package dev.openbili.webdemo

import android.app.Application
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.api.PremiumAudioMode
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.api.VideoStream
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.live.LiveStreamFormat
import dev.openbili.webdemo.live.LiveStreamSource
import dev.openbili.webdemo.settings.AdvancedAudioPriority
import dev.openbili.webdemo.settings.DeviceMediaCapabilities
import dev.openbili.webdemo.settings.PreferredResolutionMode
import java.io.File
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PlayerState {
  data object Idle : PlayerState

  data object Loading : PlayerState

  data class Ready(val playData: PlayUrlData) : PlayerState

  data class Error(val message: String, val playData: PlayUrlData? = null) : PlayerState
}

@OptIn(UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
  private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
  val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
  private val _renderedVideoId = MutableStateFlow<String?>(null)
  val renderedVideoId: StateFlow<String?> = _renderedVideoId.asStateFlow()

  var exoPlayer: ExoPlayer? = null
    private set

  private var playData: PlayUrlData? = null
  private var lastItem: FeedItem? = null
  private var loadedVideoId: String? = null
  private var liveRoomId: Long? = null
  private var loadJob: Job? = null
  private var loadGeneration = 0L
  private var pendingStartPositionMs = 0L
  private var pendingVideoTrackId: String? = null
  private var pendingAudioTrackId: String? = null
  private var activeManifestFile: File? = null
  private var cdnFallbackJob: Job? = null
  private var cdnFallbackInProgress = false
  private var skipNextCdnBufferingFallback = false
  private var unlockDolbyVision = false
  private var unlockDolbyAtmos = false
  private var advancedAudioEnabled = false
  private var advancedAudioPriority = AdvancedAudioPriority.DOLBY
  private val playbackRoutingPrefs =
    application.getSharedPreferences(PLAYBACK_ROUTING_PREFS, 0)
  private var preferredCdnHost =
    playbackRoutingPrefs.getString(KEY_PREFERRED_CDN_HOST, null).orEmpty()
  private val mediaCapabilities by lazy { DeviceMediaCapabilities.detect(getApplication()) }
  private val deviceStreamSupport = mutableMapOf<String, Boolean>()
  private val decoderCodecInfos by lazy {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
  }
  private val upstreamDataSourceFactory =
    DefaultHttpDataSource.Factory()
      .setUserAgent(
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
      )
      .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com/"))
  private val defaultDataSourceFactory by lazy {
    DefaultDataSource.Factory(getApplication(), upstreamDataSourceFactory)
  }
  private val cachedDataSourceFactory by lazy {
    CacheDataSource.Factory()
      .setCache(PlaybackCache.get(getApplication()))
      .setUpstreamDataSourceFactory(defaultDataSourceFactory)
      .setCacheKeyFactory { dataSpec ->
        dataSpec.key
          ?: dataSpec.uri.encodedPath
            ?.takeIf { it.isNotBlank() }
            ?.let { path -> "bili:$path" }
          ?: dataSpec.uri.toString()
      }
      .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
  }

  /** Warms local-only playback objects without requesting video metadata or a media URL. */
  suspend fun prewarmLocalInfrastructure() = coroutineScope {
    val cacheJob =
      async(Dispatchers.IO) {
        PlaybackCache.get(getApplication())
        cachedDataSourceFactory
      }
    val codecJob = async(Dispatchers.Default) { decoderCodecInfos.size }
    cacheJob.await()
    codecJob.await()
  }

  fun loadVideo(
    item: FeedItem,
    startPositionMs: Long = 0L,
    preferredStreamIndex: Int? = null,
    preferredResolutionMode: PreferredResolutionMode = PreferredResolutionMode.HIGH,
    page: VideoPage? = null,
    restoreSavedProgress: Boolean = true,
  ) {
    liveRoomId = null
    lastItem = item
    val requestedStartPositionMs = startPositionMs.coerceAtLeast(0L)
    pendingStartPositionMs = requestedStartPositionMs
    val generation = ++loadGeneration
    loadJob?.cancel()
    resetCdnBufferingDetector()
    _playerState.value = PlayerState.Loading
    _renderedVideoId.value = null
    // The preview Surface is already hidden by its cached cover before this committed switch.
    // Clearing here prevents Samsung SurfaceView from presenting a retained buffer from the old
    // PV while the new URL is being resolved.
    exoPlayer?.stop()
    exoPlayer?.clearMediaItems()
    loadedVideoId = null
    playData = null
    loadJob = viewModelScope.launch {
      try {
        val data =
          withContext(Dispatchers.IO) {
            PlaybackCache.get(getApplication())
            val bvid = BiliApi.resolveVideoBvid(item.videoUrl)
            val bangumiEpisodeId = BiliApi.bangumiEpisodeId(item.videoUrl)
            val info = BiliApi.getVideoInfo(bvid) ?: throw Exception("获取视频信息失败")
            val selectedPage =
              resolvePlaybackPage(
                requestedPage = page,
                defaultCid = info.cid,
                pages = info.pages,
              )
            val cid = selectedPage?.cid ?: info.cid
            val durationSeconds =
              selectedPage?.durationSeconds?.takeIf { it > 0L } ?: info.durationSeconds
            val rawData =
              (
                bangumiEpisodeId?.let { episodeId ->
                  BiliApi.getBangumiPlayUrl(episodeId, cid)
                } ?: BiliApi.getPlayUrl(bvid, cid)
              ) ?: throw Exception("获取播放地址失败，可能需要登录、会员或地区权限")
            val durationMs = durationSeconds * 1000L
            val data =
              prioritizeCdnRoutes(filterPlayableTracks(rawData.copy(durationMs = durationMs)))
            val localPositionMs =
              if (restoreSavedProgress) {
                PlaybackProgressStore.read(getApplication(), info.aid, cid, durationMs)
              } else {
                0L
              }
            val serverPositionMs =
              if (!restoreSavedProgress || localPositionMs > 0L) 0L
              else runCatching { BiliApi.getPlaybackProgressMs(info.aid, cid) }.getOrDefault(0L)
            val resumePositionMs =
              if (!restoreSavedProgress) {
                PlaybackProgressStore.normalize(requestedStartPositionMs, durationMs)
              } else {
                requestedStartPositionMs.takeIf { it > 0L }
                  ?.let { PlaybackProgressStore.normalize(it, durationMs) }
                  ?: localPositionMs.takeIf { it > 0L }
                  ?: PlaybackProgressStore.normalize(serverPositionMs, durationMs)
              }
            LoadedPlayback(data, info.aid, cid, durationMs, resumePositionMs)
          }
        if (generation != loadGeneration) return@launch
        pendingStartPositionMs = data.resumePositionMs
        val selectedIndex =
          preferredStreamIndex?.takeIf { it in data.playData.streams.indices }
            ?: selectPreferredStreamIndex(
              data.playData.streams,
              preferredResolutionMode,
              ::isStreamSupportedByDevice,
            )
        val selectedData =
          data.playData.copy(
            currentStreamIndex = selectedIndex,
            premiumAudioMode = preferredPremiumAudioMode(data.playData),
          )
        playData = selectedData
        _playerState.value = PlayerState.Ready(selectedData)
      } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        if (generation != loadGeneration) return@launch
        Log.e(TAG, "load failed: item=${item.id} url=${item.videoUrl}", e)
        _playerState.value = PlayerState.Error(e.message ?: "未知错误")
      }
    }
  }

  fun preparePlayer(): ExoPlayer {
    exoPlayer?.let {
      return it
    }
    val app = getApplication<Application>()
    val trackSelector =
      DefaultTrackSelector(app).apply {
        setParameters(buildUponParameters().setAllowVideoMixedMimeTypeAdaptiveness(true))
      }
    val player = ExoPlayer.Builder(app).setTrackSelector(trackSelector).build()
    player.addListener(
      object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          if (BuildConfig.DEBUG) Log.d(TAG, "playbackState=$playbackState")
          if (playbackState == Player.STATE_BUFFERING && liveRoomId == null) {
            if (!skipNextCdnBufferingFallback) scheduleCdnFallback(player)
          } else if (playbackState == Player.STATE_READY) {
            cdnFallbackJob?.cancel()
            cdnFallbackJob = null
            skipNextCdnBufferingFallback = false
          } else if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
            resetCdnBufferingDetector()
          }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (BuildConfig.DEBUG) Log.d(TAG, "isPlaying=$isPlaying")
        }

        override fun onRenderedFirstFrame() {
          // Bind the callback to the MediaItem that actually produced the frame. During a rapid PV
          // switch, the previous renderer can report one final frame after lastItem already points
          // at the new request; using lastItem here falsely marked that stale frame as the new PV.
          _renderedVideoId.value =
            player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
        }

        override fun onTracksChanged(tracks: Tracks) {
          applyPendingTrackOverrides(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
          Log.e(TAG, "playback failed: ${error.errorCodeName}", error)
          resetCdnBufferingDetector()
          if (liveRoomId != null) return
          _playerState.value = PlayerState.Error("视频流加载失败，请重试或切换画质", playData)
        }
      }
    )
    exoPlayer = player
    return player
  }

  /**
   * Switches the one root player into a non-cached live mode. The signed live URL is kept only in
   * the active MediaItem and is never written to playback history or the VOD cache.
   */
  fun playLive(roomId: Long, source: LiveStreamSource) {
    require(roomId > 0L) { "直播间号无效" }
    loadJob?.cancel()
    loadJob = null
    loadGeneration++
    resetCdnBufferingDetector()
    lastItem = null
    loadedVideoId = null
    playData = null
    liveRoomId = roomId
    _renderedVideoId.value = null
    _playerState.value = PlayerState.Idle
    val player = preparePlayer()
    val liveHttpFactory =
      DefaultHttpDataSource.Factory()
        .setUserAgent(
          "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
        )
        .setDefaultRequestProperties(
          mapOf(
            "Referer" to "https://live.bilibili.com/$roomId",
            "Origin" to "https://live.bilibili.com",
          )
        )
    val liveDataSourceFactory = DefaultDataSource.Factory(getApplication(), liveHttpFactory)
    val mediaItem =
      MediaItem.Builder()
        .setMediaId("live:$roomId")
        .setUri(source.url)
        .setLiveConfiguration(
          MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(3_000L).build()
        )
        .build()
    val mediaSource =
      when (source.format) {
        LiveStreamFormat.HLS_FMP4,
        LiveStreamFormat.HLS_TS ->
          HlsMediaSource.Factory(liveDataSourceFactory).createMediaSource(mediaItem)
        LiveStreamFormat.HTTP_FLV ->
          ProgressiveMediaSource.Factory(liveDataSourceFactory).createMediaSource(mediaItem)
      }
    player.stop()
    player.clearMediaItems()
    player.setMediaSource(mediaSource)
    player.prepare()
    player.playWhenReady = true
  }

  fun seekToLiveEdge() {
    val player = exoPlayer ?: return
    if (liveRoomId == null || !player.isCurrentMediaItemLive) return
    player.seekToDefaultPosition()
    player.play()
  }

  fun stopLive(roomId: Long) {
    if (liveRoomId != roomId) return
    exoPlayer?.stop()
    exoPlayer?.clearMediaItems()
    liveRoomId = null
    _playerState.value = PlayerState.Idle
    _renderedVideoId.value = null
  }

  /**
   * Called when the player and stream data are both ready. Safe to call multiple times — only acts
   * when the ExoPlayer is idle (no media source set).
   */
  fun playInitialStream() {
    val player = exoPlayer ?: return
    if (player.playbackState != Player.STATE_IDLE) return
    val data = playData ?: return
    val stream = data.streams.getOrNull(data.currentStreamIndex) ?: return
    Log.d(
      TAG,
      "starting quality=${stream.quality} codecId=${stream.codecId} codecs=${stream.codecs}",
    )
    loadedVideoId = lastItem?.id
    playDash(data)
    if (pendingStartPositionMs > 0L) {
      player.seekTo(pendingStartPositionMs)
      pendingStartPositionMs = 0L
    }
  }

  fun cancelPendingLoad() {
    loadJob?.cancel()
    loadJob = null
    loadGeneration++
  }

  /** A user-initiated seek is expected to rebuffer a remote DASH stream and must not count as a
   * slow playback interval for CDN failover. */
  fun resetCdnBufferingDetectorForUserSeek() {
    skipNextCdnBufferingFallback = true
    resetCdnBufferingDetector(clearSeekSuppression = false)
  }

  fun switchQuality(streamIndex: Int) {
    val data = playData ?: return
    if (streamIndex !in data.streams.indices) return
    val newData = data.copy(currentStreamIndex = streamIndex)
    playData = newData
    _playerState.value = PlayerState.Ready(newData)
    pendingVideoTrackId = BiliDashManifest.videoTrackId(streamIndex)
    resetCdnBufferingDetector()
    exoPlayer?.let { applyPendingTrackOverrides(it.currentTracks) }
  }

  fun switchPremiumAudio(mode: PremiumAudioMode) {
    val data = playData ?: return
    if (!data.supportsPremiumAudio(mode)) return
    val nextData = data.copy(premiumAudioMode = mode.takeUnless { data.premiumAudioMode == it })
    playData = nextData
    _playerState.value = PlayerState.Ready(nextData)
    pendingAudioTrackId = BiliDashManifest.audioTrackId(nextData)
    exoPlayer?.let { applyPendingTrackOverrides(it.currentTracks) }
  }

  fun retry() {
    val data = playData
    if (data == null) {
      lastItem?.let(::loadVideo)
      return
    }
    _playerState.value = PlayerState.Ready(data)
    playDash(data)
  }

  fun retryWithNextQuality() {
    val data = playData
    if (data == null || data.streams.size < 2) {
      retry()
      return
    }
    val next = (data.currentStreamIndex + 1) % data.streams.size
    val nextData = data.copy(currentStreamIndex = next)
    playData = nextData
    _playerState.value = PlayerState.Ready(nextData)
    pendingVideoTrackId = BiliDashManifest.videoTrackId(next)
    if (exoPlayer?.playbackState == Player.STATE_IDLE) {
      playDash(nextData)
    } else {
      exoPlayer?.let { applyPendingTrackOverrides(it.currentTracks) }
    }
  }

  fun pauseForBackground() {
    exoPlayer?.pause()
  }

  /**
   * Releases the current media/decoder resources while retaining the locally constructed player
   * engine. Returning to the feed therefore does not make the next video tap rebuild ExoPlayer,
   * while no video stream remains buffered in the background.
   */
  fun resetForWarmIdle() {
    cancelPendingLoad()
    exoPlayer?.stop()
    exoPlayer?.clearMediaItems()
    playData = null
    loadedVideoId = null
    pendingVideoTrackId = null
    pendingAudioTrackId = null
    _playerState.value = PlayerState.Idle
    _renderedVideoId.value = null
    resetCdnBufferingDetector()
  }

  /**
   * Replays only when the shared player still contains the video shown by the requesting page.
   *
   * A completed parent page can remain in the navigation stack while a recommended child replaces
   * the single ExoPlayer media item. Returning to that retained parent must not seek and replay the
   * child's media.
   */
  fun replayIfLoaded(expectedVideoId: String): Boolean {
    if (!isReplayTargetCurrent(loadedVideoId, expectedVideoId)) return false
    val player = exoPlayer ?: return false
    player.seekTo(0L)
    player.play()
    return true
  }

  /**
   * Resumes the media already held by the shared player without requiring a rendered frame.
   *
   * The SurfaceView can detach while a profile covers the video page. Its first-frame callback is
   * a presentation signal, not evidence that the media item was cleared, so using it here would
   * unnecessarily stop and reload the same stream on return.
   */
  fun resumeIfLoaded(expectedVideoId: String): Boolean {
    if (!isReplayTargetCurrent(loadedVideoId, expectedVideoId)) return false
    val player = exoPlayer ?: return false
    player.play()
    return true
  }

  fun setCompatibilityUnlocks(dolbyVision: Boolean, dolbyAtmos: Boolean) {
    unlockDolbyVision = dolbyVision
    unlockDolbyAtmos = dolbyAtmos
  }

  /** Applies the saved default immediately and again whenever a new video is prepared. */
  fun setAdvancedAudioPreferences(enabled: Boolean, priority: AdvancedAudioPriority) {
    advancedAudioEnabled = enabled
    advancedAudioPriority = priority
    val data = playData ?: return
    val mode = preferredPremiumAudioMode(data)
    if (data.premiumAudioMode == mode) return
    val nextData = data.copy(premiumAudioMode = mode)
    playData = nextData
    _playerState.value = PlayerState.Ready(nextData)
    pendingAudioTrackId = BiliDashManifest.audioTrackId(nextData)
    exoPlayer?.let { applyPendingTrackOverrides(it.currentTracks) }
  }

  private fun playDash(data: PlayUrlData, startPositionMs: Long? = null) {
    val player = exoPlayer ?: return
    val stream = data.streams.getOrNull(data.currentStreamIndex) ?: return
    pendingVideoTrackId = BiliDashManifest.videoTrackId(data.currentStreamIndex)
    pendingAudioTrackId = BiliDashManifest.audioTrackId(data)
    val manifest =
      runCatching { BiliDashManifest.build(data) }
        .onFailure { Log.w(TAG, "DASH manifest generation failed; using compatibility source", it) }
        .getOrNull()
    if (manifest == null) {
      playCompatibilityStream(stream.url, data.selectedAudioUrl())
      return
    }
    val manifestDirectory = File(getApplication<Application>().cacheDir, "bili_dash")
    if (!manifestDirectory.exists()) manifestDirectory.mkdirs()
    val manifestFile = File(manifestDirectory, "play_${loadGeneration}_${System.nanoTime()}.mpd")
    runCatching { manifestFile.writeText(manifest, Charsets.UTF_8) }
      .onFailure {
        Log.w(TAG, "Unable to write local DASH manifest; using compatibility source", it)
      }
      .getOrElse {
        playCompatibilityStream(stream.url, data.selectedAudioUrl())
        return
      }
    activeManifestFile?.takeIf { it != manifestFile }?.delete()
    activeManifestFile = manifestFile
    val mediaItemBuilder =
      MediaItem.Builder()
        .setUri(Uri.fromFile(manifestFile))
        .setMimeType(MimeTypes.APPLICATION_MPD)
    lastItem?.id?.let(mediaItemBuilder::setMediaId)
    val mediaItem = mediaItemBuilder.build()
    val mediaSource =
      DashMediaSource.Factory(cachedDataSourceFactory).createMediaSource(mediaItem)
    if (startPositionMs != null) {
      player.setMediaSource(mediaSource, startPositionMs.coerceAtLeast(0L))
    } else {
      player.setMediaSource(mediaSource)
    }
    player.prepare()
    player.playWhenReady = true
  }

  /** Switch once a CDN leaves the player buffering for the full timeout window. */
  private fun scheduleCdnFallback(player: ExoPlayer) {
    if (cdnFallbackInProgress || cdnFallbackJob?.isActive == true) return
    cdnFallbackJob =
      viewModelScope.launch {
        delay(SLOW_CDN_BUFFERING_TIMEOUT_MS)
        if (
          cdnFallbackInProgress ||
            skipNextCdnBufferingFallback ||
            player.playbackState != Player.STATE_BUFFERING ||
            !player.playWhenReady
        ) {
          return@launch
        }
        fallbackToNextCdn(player)
      }
  }

  private fun fallbackToNextCdn(player: ExoPlayer) {
    if (cdnFallbackInProgress) return
    val data = playData ?: return
    val index = data.currentStreamIndex
    val stream = data.streams.getOrNull(index) ?: return
    val urls = (listOf(stream.url) + stream.backupUrls).filter { it.isNotBlank() }.distinct()
    val replacement = urls.getOrNull(1) ?: return
    val resumePositionMs = player.currentPosition.coerceAtLeast(0L)
    val shouldPlay = player.playWhenReady
    val rotatedStream =
      stream.copy(
        url = replacement,
        backupUrls = (urls.drop(2) + stream.url).filter { it != replacement }.distinct(),
      )
    val nextStreams = data.streams.toMutableList().also { it[index] = rotatedStream }
    val nextData = data.copy(streams = nextStreams)

    cdnFallbackInProgress = true
    cdnFallbackJob = null
    playData = nextData
    _playerState.value = PlayerState.Ready(nextData)
    persistPreferredCdn(replacement)
    Log.w(TAG, "buffering for ${SLOW_CDN_BUFFERING_TIMEOUT_MS}ms; switching ${stream.quality} to backup CDN")
    playDash(nextData, resumePositionMs)
    player.playWhenReady = shouldPlay
    cdnFallbackInProgress = false
    if (player.playbackState == Player.STATE_BUFFERING) scheduleCdnFallback(player)
  }

  private fun resetCdnBufferingDetector(clearSeekSuppression: Boolean = true) {
    cdnFallbackJob?.cancel()
    cdnFallbackJob = null
    cdnFallbackInProgress = false
    if (clearSeekSuppression) skipNextCdnBufferingFallback = false
  }

  private fun playCompatibilityStream(videoUrl: String, audioUrl: String?) {
    val player = exoPlayer ?: return
    val sources = mutableListOf<MediaItem>()
    if (audioUrl != null) sources.add(cachedMediaItem(audioUrl))
    sources.add(cachedMediaItem(videoUrl))

    if (sources.size == 2) {
      val audioSource =
        ProgressiveMediaSource.Factory(cachedDataSourceFactory).createMediaSource(sources[0])
      val videoSource =
        ProgressiveMediaSource.Factory(cachedDataSourceFactory).createMediaSource(sources[1])
      player.setMediaSource(MergingMediaSource(videoSource, audioSource))
    } else {
      player.setMediaItem(sources[0])
    }
    player.prepare()
    player.playWhenReady = true
  }

  private fun applyPendingTrackOverrides(tracks: Tracks) {
    val player = exoPlayer ?: return
    var parameters = player.trackSelectionParameters
    var changed = false

    fun apply(trackType: Int, desiredId: String?) {
      if (desiredId == null) return
      val match =
        tracks.groups.firstNotNullOfOrNull { group ->
          if (group.type != trackType) return@firstNotNullOfOrNull null
          val directMatch =
            (0 until group.length).firstOrNull { group.getTrackFormat(it).id == desiredId }
          val videoFormatFallback =
            if (trackType == C.TRACK_TYPE_VIDEO && directMatch == null) {
              val selectedData = playData
              val desiredStream =
                selectedData?.streams?.getOrNull(selectedData.currentStreamIndex)
              desiredStream?.let { stream ->
                (0 until group.length).firstOrNull { index ->
                  val format = group.getTrackFormat(index)
                  format.width == stream.width &&
                    format.height == stream.height &&
                    (stream.codecs.isBlank() || format.codecs.equals(stream.codecs, ignoreCase = true))
                }
              }
            } else {
              null
            }
          (directMatch ?: videoFormatFallback)
            ?.let { index -> group to index }
        } ?: return
      val (group, index) = match
      // Adaptive DASH selection marks every eligible rendition as selected. That does not mean
      // the requested rendition is pinned, so checking group.isTrackSelected(index) here causes
      // the quality menu to update while playback stays on the adaptive/default track.
      val existingOverride = parameters.overrides[group.mediaTrackGroup]
      if (existingOverride?.trackIndices?.singleOrNull() == index) return
      parameters =
        parameters
          .buildUpon()
          .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
          .build()
      changed = true
    }

    apply(C.TRACK_TYPE_VIDEO, pendingVideoTrackId)
    apply(C.TRACK_TYPE_AUDIO, pendingAudioTrackId)
    if (changed) player.trackSelectionParameters = parameters
  }

  private fun cachedMediaItem(url: String): MediaItem {
    val uri = Uri.parse(url)
    val stablePath = uri.encodedPath?.takeIf { it.isNotBlank() } ?: url.substringBefore('?')
    val builder = MediaItem.Builder().setUri(uri).setCustomCacheKey("bili:$stablePath")
    lastItem?.id?.let(builder::setMediaId)
    return builder.build()
  }

  fun release() {
    loadJob?.cancel()
    loadJob = null
    loadGeneration++
    exoPlayer?.release()
    exoPlayer = null
    playData = null
    loadedVideoId = null
    activeManifestFile?.delete()
    activeManifestFile = null
    pendingVideoTrackId = null
    pendingAudioTrackId = null
    _playerState.value = PlayerState.Idle
    _renderedVideoId.value = null
  }

  override fun onCleared() {
    release()
  }

  private fun isStreamSupportedByDevice(stream: VideoStream): Boolean {
    if (stream.id == 126) {
      return unlockDolbyVision || mediaCapabilities.supportsDolbyVision
    }
    if (stream.id == 125 && !mediaCapabilities.supportsHdr10) return false
    val height = effectiveStreamHeight(stream)
    val width = stream.width.takeIf { it > 0 } ?: (height * 16 / 9)
    val mime =
      when (stream.codecId) {
        7 -> MediaFormat.MIMETYPE_VIDEO_AVC
        12 -> MediaFormat.MIMETYPE_VIDEO_HEVC
        13 -> MediaFormat.MIMETYPE_VIDEO_AV1
        else -> return true
      }
    val key = "$mime:${width}x$height@${stream.frameRate}"
    return deviceStreamSupport.getOrPut(key) {
      runCatching {
          decoderCodecInfos.any { codec ->
            codec.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
              runCatching {
                  codec.getCapabilitiesForType(mime).videoCapabilities?.let { capabilities ->
                    if (stream.frameRate > 0f)
                      capabilities.areSizeAndRateSupported(
                        width,
                        height,
                        stream.frameRate.toDouble(),
                      )
                    else capabilities.isSizeSupported(width, height)
                  } ?: false
                }
                .getOrDefault(false)
          }
        }
        .getOrDefault(true)
    }
  }

  private fun filterPlayableTracks(data: PlayUrlData): PlayUrlData {
    val compatibleStreams = data.streams.filter(::isStreamSupportedByDevice)
    val fallbackStreams =
      data.streams.filter { it.id !in setOf(125, 126) }.takeLast(1).ifEmpty {
        data.streams.take(1)
      }
    val streams = compatibleStreams.ifEmpty { fallbackStreams }
    val dolbyAvailable = mediaCapabilities.supportsDolbyAtmos || unlockDolbyAtmos
    return data.copy(
      streams = streams,
      currentStreamIndex = BiliApi.defaultStreamIndex(streams),
      dolbyAudioUrl = data.dolbyAudioUrl.takeIf { dolbyAvailable },
      dolbyAudio = data.dolbyAudio.takeIf { dolbyAvailable },
    )
  }

  /**
   * Bilibili's first backup has proven more stable on the target tablets, so it is the default
   * route. Once the under-run detector selects another host, that host wins for later videos too.
   */
  private fun prioritizeCdnRoutes(data: PlayUrlData): PlayUrlData {
    fun video(stream: VideoStream): VideoStream {
      val ordered = prioritizeCdnUrls(stream.url, stream.backupUrls, preferredCdnHost)
      return stream.copy(url = ordered.primary, backupUrls = ordered.backups)
    }

    fun audio(stream: dev.openbili.webdemo.api.AudioStream?): dev.openbili.webdemo.api.AudioStream? {
      stream ?: return null
      val ordered = prioritizeCdnUrls(stream.url, stream.backupUrls, preferredCdnHost)
      return stream.copy(url = ordered.primary, backupUrls = ordered.backups)
    }

    val dashAudio = audio(data.dashAudio)
    val dolbyAudio = audio(data.dolbyAudio)
    val hiResAudio = audio(data.hiResAudio)
    return data.copy(
      streams = data.streams.map(::video),
      dashAudio = dashAudio,
      dolbyAudio = dolbyAudio,
      hiResAudio = hiResAudio,
      dashAudioUrl = dashAudio?.url ?: data.dashAudioUrl,
      dolbyAudioUrl = dolbyAudio?.url ?: data.dolbyAudioUrl,
      hiResAudioUrl = hiResAudio?.url ?: data.hiResAudioUrl,
    )
  }

  private fun persistPreferredCdn(url: String) {
    val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
    if (host.isBlank() || host == preferredCdnHost) return
    preferredCdnHost = host
    playbackRoutingPrefs.edit().putString(KEY_PREFERRED_CDN_HOST, host).apply()
  }

  private fun preferredPremiumAudioMode(data: PlayUrlData): PremiumAudioMode? {
    if (!advancedAudioEnabled) return null
    val preferred =
      when (advancedAudioPriority) {
        AdvancedAudioPriority.DOLBY -> PremiumAudioMode.DOLBY to PremiumAudioMode.HI_RES
        AdvancedAudioPriority.HI_RES -> PremiumAudioMode.HI_RES to PremiumAudioMode.DOLBY
      }
    return preferred.first.takeIf(data::supportsPremiumAudio)
      ?: preferred.second.takeIf(data::supportsPremiumAudio)
  }

  private companion object {
    const val SLOW_CDN_BUFFERING_TIMEOUT_MS = 3_000L
    const val TAG = "PlayerVM"
    const val PLAYBACK_ROUTING_PREFS = "playback_routing"
    const val KEY_PREFERRED_CDN_HOST = "preferred_cdn_host"
  }

  private data class LoadedPlayback(
    val playData: PlayUrlData,
    val aid: Long,
    val cid: Long,
    val durationMs: Long,
    val resumePositionMs: Long,
  )
}

internal fun isReplayTargetCurrent(loadedVideoId: String?, expectedVideoId: String): Boolean =
  loadedVideoId == expectedVideoId

internal data class PrioritizedCdnUrls(val primary: String, val backups: List<String>)

internal fun prioritizeCdnUrls(
  primary: String,
  backups: List<String>,
  preferredHost: String,
): PrioritizedCdnUrls {
  // Backup #1 is the new baseline route. Keep the API primary as the final fallback.
  val candidates = (backups + primary).filter(String::isNotBlank).distinct()
  if (candidates.isEmpty()) return PrioritizedCdnUrls(primary, emptyList())
  val preferred =
    preferredHost.takeIf(String::isNotBlank)?.let { expected ->
      candidates.firstOrNull { url ->
        runCatching { URI(url).host.equals(expected, ignoreCase = true) }.getOrDefault(false)
      }
    }
  val selected = preferred ?: candidates.first()
  return PrioritizedCdnUrls(selected, candidates.filterNot { it == selected })
}

internal fun resolvePlaybackPage(
  requestedPage: VideoPage?,
  defaultCid: Long,
  pages: List<VideoPage>,
): VideoPage? =
  requestedPage?.takeIf { requested -> pages.any { it.cid == requested.cid } }
    ?: pages.firstOrNull { it.cid == defaultCid }

internal fun selectPreferredStreamIndex(
  streams: List<VideoStream>,
  mode: PreferredResolutionMode,
  isSupported: (VideoStream) -> Boolean = { true },
): Int {
  if (streams.isEmpty()) return 0
  val supported =
    streams.indices.filter { isSupported(streams[it]) }.ifEmpty { streams.indices.toList() }

  fun firstAvailable(vararg ids: Int): Int? {
    ids.forEach { id ->
      supported
        .firstOrNull { streams[it].id == id }
        ?.let {
          return it
        }
    }
    return null
  }

  fun highest(indices: List<Int>): Int =
    indices.maxWithOrNull(
      compareBy<Int> { effectiveStreamHeight(streams[it]) }
        .thenBy { streams[it].frameRate }
        .thenBy { streams[it].id }
    ) ?: 0

  fun lowest(indices: List<Int>): Int =
    indices.minWithOrNull(
      compareBy<Int> { effectiveStreamHeight(streams[it]) }
        .thenBy { streams[it].frameRate }
        .thenBy { streams[it].id }
    ) ?: 0

  return when (mode) {
    PreferredResolutionMode.EXTREME -> highest(supported)
    PreferredResolutionMode.ULTRA_HIGH ->
      firstAvailable(116, 112, 80, 74, 64, 32, 16) ?: highest(supported)
    PreferredResolutionMode.HIGH -> firstAvailable(80, 74, 64, 32, 16) ?: lowest(supported)
    PreferredResolutionMode.MEDIUM -> firstAvailable(64, 32, 16) ?: lowest(supported)
    PreferredResolutionMode.LOW -> firstAvailable(32, 16) ?: lowest(supported)
  }
}

internal fun effectiveStreamHeight(stream: VideoStream): Int =
  stream.height.takeIf { it > 0 }
    ?: when (stream.id) {
      127 -> 4320
      120,
      125,
      126 -> 2160
      80,
      112,
      116 -> 1080
      64,
      74 -> 720
      32 -> 480
      16 -> 360
      else -> 0
    }

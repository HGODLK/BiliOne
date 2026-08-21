package dev.openbili.webdemo

import android.app.Application
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dev.openbili.webdemo.api.AudioStream
import dev.openbili.webdemo.api.BiliBangumiApi
import dev.openbili.webdemo.api.BiliReportApi
import dev.openbili.webdemo.api.BiliSubtitleApi
import dev.openbili.webdemo.api.BiliVideoApi
import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.api.PremiumAudioMode
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.api.VideoStream
import dev.openbili.webdemo.api.VideoSubtitleTrack
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.live.LiveStreamFormat
import dev.openbili.webdemo.live.LiveStreamSource
import dev.openbili.webdemo.offline.OfflineEntitlementState
import dev.openbili.webdemo.offline.OfflineMediaEntry
import dev.openbili.webdemo.offline.OfflineMediaManager
import dev.openbili.webdemo.offline.OfflineTransferState
import dev.openbili.webdemo.settings.AdvancedAudioPriority
import dev.openbili.webdemo.settings.CdnRegionPreference
import dev.openbili.webdemo.settings.DeviceMediaCapabilities
import dev.openbili.webdemo.settings.PreferredResolutionMode
import java.io.File
import java.net.URI
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PlayerState {
  data object Idle : PlayerState

  data object Loading : PlayerState

  data class Ready(val playData: PlayUrlData) : PlayerState

  data class Error(val message: String, val playData: PlayUrlData? = null) : PlayerState
}

data class PlayerSubtitleState(
  val mediaId: String? = null,
  val bvid: String? = null,
  val aid: Long = 0L,
  val cid: Long = 0L,
  val tracks: List<VideoSubtitleTrack> = emptyList(),
  val selectedTrackId: String? = null,
  val isLoading: Boolean = false,
  val message: String? = null,
)

internal const val STARTUP_PREFERRED_CDN_HOST = "d1--cn-gotcha208.bilivideo.com"

@OptIn(UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
  private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
  val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
  private val _renderedVideoId = MutableStateFlow<String?>(null)
  val renderedVideoId: StateFlow<String?> = _renderedVideoId.asStateFlow()
  private val _renderedVideoFrameCount = MutableStateFlow(0)
  val renderedVideoFrameCount: StateFlow<Int> = _renderedVideoFrameCount.asStateFlow()
  private val _subtitleState = MutableStateFlow(PlayerSubtitleState())
  val subtitleState: StateFlow<PlayerSubtitleState> = _subtitleState.asStateFlow()

  var exoPlayer: ExoPlayer? = null
    private set

  private var playData: PlayUrlData? = null
  private var lastItem: FeedItem? = null
  private var loadedVideoId: String? = null
  private var activePlaybackIdentity: ActivePlaybackIdentity? = null
  private var liveRoomId: Long? = null
  private var offlinePlaybackEntry: OfflineMediaEntry? = null
  private var loadJob: Job? = null
  private var loadGeneration = 0L
  private var pendingStartPositionMs = 0L
  private var failedPlaybackPositionMs = 0L
  private var pendingVideoTrackId: String? = null
  private var pendingAudioTrackId: String? = null
  private var activeManifestFile: File? = null
  private var activeSubtitles: List<PreparedSubtitle> = emptyList()
  private var activeSubtitleIdentity: SubtitleMediaIdentity? = null
  private var cdnFallbackJob: Job? = null
  private var undefinedCdnLoadJob: Job? = null
  private var cdnFallbackInProgress = false
  private var skipNextCdnBufferingFallback = false
  private var unlockDolbyVision = false
  private var unlockDolbyAtmos = false
  private var unlockHiRes = false
  private var defaultSubtitlesEnabled = false
  private var advancedAudioEnabled = false
  private var advancedAudioPriority = AdvancedAudioPriority.DOLBY
  private var foregroundTrackSelectionParameters: TrackSelectionParameters? = null
  private var backgroundAudioOnly = false
  private var appInForeground = true
  private val playbackRoutingPrefs = application.getSharedPreferences(PLAYBACK_ROUTING_PREFS, 0)
  private var preferredCdnRegion = readCdnRegionPreference(application)
  private var preferredCdnHost = loadPersistedCdnHost(preferredCdnRegion)

  init {
    // 旧版本没有保存地区标记，无法判断旧主机是否符合新的地区偏好，因此从新默认线路开始。
  }

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
          ?: dataSpec.uri.encodedPath?.takeIf { it.isNotBlank() }?.let { path -> "bili:$path" }
          ?: dataSpec.uri.toString()
      }
      .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
  }
  private val vodMediaSourceFactory by lazy { DefaultMediaSourceFactory(cachedDataSourceFactory) }

  /** 预热仅本地的播放对象，不请求视频元数据或媒体 URL。 */
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

  /** 为播放页封面背景图启动新的三帧已呈现闸门。 */
  fun resetRenderedVideoFrameCountForPageEntry() {
    _renderedVideoFrameCount.value = 0
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
    offlinePlaybackEntry = null
    activePlaybackIdentity = null
    lastItem = item
    exitBackgroundAudioMode()
    PlaybackSessionService.publishDetailPlayer(getApplication())
    val requestedStartPositionMs = startPositionMs.coerceAtLeast(0L)
    pendingStartPositionMs = requestedStartPositionMs
    failedPlaybackPositionMs = 0L
    val generation = ++loadGeneration
    loadJob?.cancel()
    resetCdnBufferingDetector()
    _playerState.value = PlayerState.Loading
    _renderedVideoId.value = null
    _renderedVideoFrameCount.value = 0
    // 在这次提交式切换之前，预览 Surface 已被其缓存封面隐藏。这里清除可以防止三星
    // SurfaceView 在新 URL 解析期间呈现来自旧 PV 的保留缓冲。
    exoPlayer?.stop()
    exoPlayer?.clearMediaItems()
    loadedVideoId = null
    playData = null
    clearActiveSubtitles()
    _subtitleState.value =
      PlayerSubtitleState(mediaId = item.id, cid = page?.cid ?: 0L, isLoading = true)
    exoPlayer?.let { player ->
      player.trackSelectionParameters =
        player.trackSelectionParameters
          .buildUpon()
          .clearOverridesOfType(C.TRACK_TYPE_TEXT)
          .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
          .build()
    }
    if (OfflineMediaManager.isOfflineUri(item.videoUrl)) {
      loadOfflineVideo(item, requestedStartPositionMs, generation)
      return
    }
    loadJob = viewModelScope.launch {
      try {
        val data =
          withContext(Dispatchers.IO) {
            PlaybackCache.get(getApplication())
            val bvid = BiliBangumiApi.resolveVideoBvid(item.videoUrl)
            val bangumiEpisodeId = BiliVideoApi.bangumiEpisodeId(item.videoUrl)
            val info = BiliVideoApi.getVideoInfo(bvid) ?: throw Exception("获取视频信息失败")
            val selectedPage =
              resolvePlaybackPage(
                requestedPage = page,
                defaultCid = info.cid,
                pages = info.pages,
              )
            val cid = selectedPage?.cid ?: info.cid
            val durationSeconds =
              resolvePlaybackDurationSeconds(
                selectedPage = selectedPage,
                totalDurationSeconds = info.durationSeconds,
              )
            val resolvedBvid = info.bvid.ifBlank { bvid }
            val subtitleRequest = async {
              loadSubtitles(
                mediaId = item.id,
                bvid = resolvedBvid,
                aid = info.aid,
                cid = cid,
                generation = generation,
              )
            }
            val rawData =
              (bangumiEpisodeId?.let { episodeId ->
                BiliVideoApi.getBangumiPlayUrl(episodeId, cid)
              } ?: BiliVideoApi.getPlayUrl(bvid, cid)) ?: throw Exception("获取播放地址失败，可能需要登录、会员或地区权限")
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
              else runCatching { BiliReportApi.getPlaybackProgressMs(info.aid, cid) }.getOrDefault(0L)
            val resumePositionMs =
              if (!restoreSavedProgress) {
                PlaybackProgressStore.normalize(requestedStartPositionMs, durationMs)
              } else {
                requestedStartPositionMs
                  .takeIf { it > 0L }
                  ?.let { PlaybackProgressStore.normalize(it, durationMs) }
                  ?: localPositionMs.takeIf { it > 0L }
                  ?: PlaybackProgressStore.normalize(serverPositionMs, durationMs)
              }
            LoadedPlayback(
              playData = data,
              bvid = resolvedBvid,
              aid = info.aid,
              cid = cid,
              durationMs = durationMs,
              resumePositionMs = resumePositionMs,
              subtitles = subtitleRequest.await(),
            )
          }
        if (generation != loadGeneration) {
          deleteSubtitleGenerationFiles(generation)
          return@launch
        }
        activePlaybackIdentity =
          ActivePlaybackIdentity(itemId = item.id, aid = data.aid, cid = data.cid)
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
        val expectedSubtitleIdentity =
          SubtitleMediaIdentity(
            mediaId = item.id,
            bvid = data.bvid,
            aid = data.aid,
            cid = data.cid,
            generation = generation,
          )
        check(data.subtitles.identity == expectedSubtitleIdentity) {
          "字幕加载结果与当前视频身份不匹配"
        }
        activeSubtitleIdentity = expectedSubtitleIdentity
        activeSubtitles = data.subtitles.prepared
        val defaultSubtitleId =
          activeSubtitles.firstOrNull()?.track?.id.takeIf { defaultSubtitlesEnabled }
        _subtitleState.value =
          PlayerSubtitleState(
            mediaId = item.id,
            bvid = data.bvid,
            aid = data.aid,
            cid = data.cid,
            tracks = activeSubtitles.map(PreparedSubtitle::track),
            selectedTrackId = defaultSubtitleId,
            isLoading = false,
            message = data.subtitles.message,
          )
        _playerState.value = PlayerState.Ready(selectedData)
      } catch (e: Exception) {
        if (e is CancellationException) {
          deleteSubtitleGenerationFiles(generation)
          throw e
        }
        if (generation != loadGeneration) {
          deleteSubtitleGenerationFiles(generation)
          return@launch
        }
        deleteSubtitleGenerationFiles(generation)
        Log.e(TAG, "load failed: item=${item.id} url=${item.videoUrl}", e)
        _subtitleState.value = PlayerSubtitleState(mediaId = item.id, cid = page?.cid ?: 0L)
        _playerState.value = PlayerState.Error(e.message ?: "未知错误")
      }
    }
  }

  private fun loadOfflineVideo(item: FeedItem, startPositionMs: Long, generation: Long) {
    loadJob = viewModelScope.launch {
      try {
        val manager = OfflineMediaManager.get(getApplication())
        val entry =
          withContext(Dispatchers.IO) {
            manager.entryFromPlaybackUri(item.videoUrl) ?: error("找不到对应的缓存视频")
          }
        val snapshot =
          withContext(Dispatchers.IO) {
            manager.snapshots().firstOrNull { it.entry.id == entry.id } ?: error("缓存状态不存在")
          }
        check(snapshot.state == OfflineTransferState.COMPLETED) { "视频尚未缓存完成" }
        check(
          !entry.requiresVip ||
            (entry.entitlementState == OfflineEntitlementState.ACTIVE &&
              entry.entitlementValidUntilMs >= System.currentTimeMillis())
        ) {
          "当前账号或会员状态无法播放此缓存"
        }
        val identity =
          SubtitleMediaIdentity(
            mediaId = item.id,
            bvid = entry.bvid,
            aid = entry.aid,
            cid = entry.cid,
            generation = generation,
          )
        val preparedSubtitles =
          entry.subtitles.mapNotNull { subtitle ->
            File(manager.rootDirectory, subtitle.relativePath).takeIf(File::isFile)?.let { file ->
              PreparedSubtitle(
                track =
                  VideoSubtitleTrack(
                    id = subtitle.id,
                    language = subtitle.language,
                    languageLabel = subtitle.label,
                    sourceUrl = Uri.fromFile(file).toString(),
                    type = 0,
                    aiType = 0,
                    aiStatus = 0,
                    aid = entry.aid,
                    cid = entry.cid,
                    bvid = entry.bvid,
                  ),
                file = file,
                deleteOnClear = false,
              )
            }
          }
        val stream =
          VideoStream(
            id = entry.qualityId,
            quality = entry.qualityLabel,
            url = entry.videoUrl,
            codecId = 0,
            codecs = "",
            mimeType = entry.videoMimeType,
          )
        val audio =
          entry.audioUrl.takeIf(String::isNotBlank)?.let { url ->
            AudioStream(id = 0, url = url, mimeType = entry.audioMimeType)
          }
        val data =
          PlayUrlData(
            dashAudioUrl = audio?.url,
            dashAudio = audio,
            streams = listOf(stream),
            currentStreamIndex = 0,
            durationMs = entry.durationMs,
          )
        if (generation != loadGeneration) return@launch
        activePlaybackIdentity =
          ActivePlaybackIdentity(itemId = item.id, aid = entry.aid, cid = entry.cid)
        offlinePlaybackEntry = entry
        playData = data
        pendingStartPositionMs = startPositionMs.coerceIn(0L, entry.durationMs.coerceAtLeast(0L))
        activeSubtitleIdentity = identity
        activeSubtitles = preparedSubtitles
        val defaultSubtitleId =
          preparedSubtitles.firstOrNull()?.track?.id.takeIf { defaultSubtitlesEnabled }
        _subtitleState.value =
          PlayerSubtitleState(
            mediaId = item.id,
            bvid = entry.bvid,
            aid = entry.aid,
            cid = entry.cid,
            tracks = preparedSubtitles.map(PreparedSubtitle::track),
            selectedTrackId = defaultSubtitleId,
            isLoading = false,
          )
        _playerState.value = PlayerState.Ready(data)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (generation != loadGeneration) return@launch
        Log.e(TAG, "offline load failed: item=${item.id}", e)
        _subtitleState.value = PlayerSubtitleState(mediaId = item.id)
        _playerState.value = PlayerState.Error(e.message ?: "缓存视频不可用")
      }
    }
  }

  fun preparePlayer(publishSystemControls: Boolean = true): ExoPlayer {
    exoPlayer?.let {
      if (publishSystemControls && liveRoomId == null) {
        PlaybackSessionService.publishDetailPlayer(getApplication())
      }
      return it
    }
    val app = getApplication<Application>()
    val trackSelector =
      DefaultTrackSelector(app).apply {
        setParameters(
          buildUponParameters()
            .setAllowVideoMixedMimeTypeAdaptiveness(true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        )
      }
    val audioAttributes =
      AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
    val player =
      ExoPlayer.Builder(app, HiResCompatibleRenderersFactory(app))
        .setTrackSelector(trackSelector)
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .build()
    player.addListener(
      object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          if (BuildConfig.DEBUG) Log.d(TAG, "playbackState=$playbackState")
          if (playbackState == Player.STATE_BUFFERING && liveRoomId == null) {
            undefinedCdnLoadJob?.cancel()
            undefinedCdnLoadJob = null
            if (!skipNextCdnBufferingFallback) scheduleCdnFallback(player)
          } else if (playbackState == Player.STATE_READY) {
            undefinedCdnLoadJob?.cancel()
            undefinedCdnLoadJob = null
            cdnFallbackJob?.cancel()
            cdnFallbackJob = null
            skipNextCdnBufferingFallback = false
            failedPlaybackPositionMs = 0L
          } else if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
            resetCdnBufferingDetector()
          }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (BuildConfig.DEBUG) Log.d(TAG, "isPlaying=$isPlaying")
          if (isPlaying && liveRoomId == null) {
            PlaybackSessionService.publishDetailPlayer(app)
            if (!appInForeground) enterBackgroundAudioMode()
          }
        }

        override fun onRenderedFirstFrame() {
          // 把回调绑定到真正产出该帧的 MediaItem。快速切换 PV 时，上一个渲染器可能在
          // lastItem 已指向新请求之后上报最后一帧；这里若使用 lastItem 会把那帧过期
          // 画面误标记为新 PV。
          _renderedVideoId.value = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
          _renderedVideoFrameCount.value = 1
        }

        override fun onTracksChanged(tracks: Tracks) {
          applyPendingTrackOverrides(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
          Log.e(TAG, "playback failed: ${error.errorCodeName}", error)
          resetCdnBufferingDetector()
          if (liveRoomId != null) return
          failedPlaybackPositionMs =
            maxOf(failedPlaybackPositionMs, player.currentPosition.coerceAtLeast(0L))
          _playerState.value = PlayerState.Error("视频流加载失败，请重试或切换画质", playData)
        }
      }
    )
    player.setVideoFrameMetadataListener { _, _, _, _ ->
      // 第一帧的元数据回调先于 onRenderedFirstFrame。只在那个真正的渲染回调之后计数，
      // 意味着数值二和三代表后续的输出帧，而不是解码器输入或已准备却从未呈现的缓冲。
      if (_renderedVideoFrameCount.value in 0..2) {
        _renderedVideoFrameCount.value += 1
      }
    }
    exoPlayer = player
    if (publishSystemControls && liveRoomId == null) {
      PlaybackSessionService.publishDetailPlayer(app)
    }
    return player
  }

  /**
   * 把唯一根播放器切换进不缓存的直播模式。签名后的直播 URL 只保留在活动 MediaItem 中，
   * 绝不写入播放历史或点播缓存。
   */
  fun playLive(roomId: Long, source: LiveStreamSource) {
    require(roomId > 0L) { "直播间号无效" }
    loadJob?.cancel()
    loadJob = null
    loadGeneration++
    resetCdnBufferingDetector()
    exitBackgroundAudioMode()
    PlaybackSessionService.stop(getApplication())
    lastItem = null
    activePlaybackIdentity = null
    loadedVideoId = null
    playData = null
    offlinePlaybackEntry = null
    liveRoomId = roomId
    _renderedVideoId.value = null
    _renderedVideoFrameCount.value = 0
    _playerState.value = PlayerState.Idle
    val player = preparePlayer(publishSystemControls = false)
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
    activePlaybackIdentity = null
    _playerState.value = PlayerState.Idle
    _renderedVideoId.value = null
  }

  /**
   * 在播放器和流数据都就绪时调用。可安全地多次调用 —— 只在 ExoPlayer 空闲（未设置媒体
   * 源）时才起作用。
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

  /**
   * 用户发起的 seek 预计会让远端 DASH 流重新缓冲，因此不能计作 CDN 故障转移的慢速
   * 播放区间。
   */
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

  fun selectSubtitle(trackId: String?) {
    val current = _subtitleState.value
    val identity = activeSubtitleIdentity ?: return
    if (current.mediaId == null || current.mediaId != lastItem?.id || !current.matches(identity))
      return
    val selectedId = trackId?.takeIf { requested ->
      activeSubtitles.any { it.track.id == requested }
    }
    if (current.selectedTrackId == selectedId) return
    _subtitleState.value = current.copy(selectedTrackId = selectedId)
    exoPlayer?.let { applyPendingTrackOverrides(it.currentTracks) }
  }

  fun retry() {
    val resumePositionMs =
      maxOf(failedPlaybackPositionMs, exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L)
    val data = playData
    if (data == null) {
      lastItem?.let { item ->
        loadVideo(
          item = item,
          startPositionMs = resumePositionMs,
          restoreSavedProgress = false,
        )
      }
      return
    }
    _playerState.value = PlayerState.Ready(data)
    playDash(data, resumePositionMs)
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
      val resumePositionMs =
        maxOf(failedPlaybackPositionMs, exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L)
      playDash(nextData, resumePositionMs)
    } else {
      exoPlayer?.let { applyPendingTrackOverrides(it.currentTracks) }
    }
  }

  fun pauseForBackground() {
    exitBackgroundAudioMode()
    exoPlayer?.pause()
  }

  fun setAppInForeground(inForeground: Boolean) {
    appInForeground = inForeground
    if (inForeground) exitBackgroundAudioMode()
  }

  /** Activity 不可见时，把同一条播放时间线延续为仅音频。 */
  fun enterBackgroundAudioMode() {
    val player = exoPlayer ?: return
    if (backgroundAudioOnly || liveRoomId != null) return
    foregroundTrackSelectionParameters = player.trackSelectionParameters
    player.trackSelectionParameters =
      audioOnlyTrackSelectionParameters(player.trackSelectionParameters)
    backgroundAudioOnly = true
  }

  /** 恢复精确的前台轨道策略，而不改变播放/暂停状态。 */
  fun exitBackgroundAudioMode() {
    if (!backgroundAudioOnly) return
    val player = exoPlayer
    val foregroundParameters = foregroundTrackSelectionParameters
    backgroundAudioOnly = false
    foregroundTrackSelectionParameters = null
    if (player != null && foregroundParameters != null) {
      player.trackSelectionParameters = foregroundParameters
    }
  }

  /**
   * 释放当前媒体/解码器资源，同时保留本地构建的播放器引擎。返回信息流因此不会让
   * 下一次点击视频重建 ExoPlayer，同时后台也不再缓冲任何视频流。
   */
  fun resetForWarmIdle(stopMediaSession: Boolean = true) {
    cancelPendingLoad()
    exitBackgroundAudioMode()
    exoPlayer?.stop()
    exoPlayer?.clearMediaItems()
    playData = null
    offlinePlaybackEntry = null
    activePlaybackIdentity = null
    loadedVideoId = null
    pendingVideoTrackId = null
    pendingAudioTrackId = null
    clearActiveSubtitles()
    _subtitleState.value = PlayerSubtitleState()
    _playerState.value = PlayerState.Idle
    _renderedVideoId.value = null
    resetCdnBufferingDetector()
    if (
      stopMediaSession &&
        getApplication<BiliApplication>().playbackSessionTarget == PlaybackSessionTarget.DETAIL
    ) {
      PlaybackSessionService.stop(getApplication())
    }
  }

  /**
   * 仅当共享播放器仍包含请求页所展示的视频时才重播。
   *
   * 已完成的父页可能留在导航栈中，而一个推荐子页替换了唯一 ExoPlayer 的媒体条目。
   * 返回那个保留的父页时绝不能 seek 并重播子页的媒体。
   */
  fun replayIfLoaded(expectedVideoId: String): Boolean {
    if (!isReplayTargetCurrent(loadedVideoId, expectedVideoId)) return false
    val player = exoPlayer ?: return false
    player.seekTo(0L)
    player.play()
    return true
  }

  /**
   * 无需已渲染帧即可恢复共享播放器已持有的媒体。
   *
   * 资料页盖住视频页时 SurfaceView 可能分离。它的首帧回调是呈现信号，而不是媒体条目
   * 已被清除的证据，因此在这里使用它会在返回时无谓地停止并重载同一条流。
   */
  fun resumeIfLoaded(expectedVideoId: String): Boolean {
    if (!isReplayTargetCurrent(loadedVideoId, expectedVideoId)) return false
    val player = exoPlayer ?: return false
    player.play()
    return true
  }

  /** 判断当前播放器身份是否仍对应页面正在展示的 aid/cid。 */
  fun isPlaybackIdentityActive(itemId: String?, aid: Long, cid: Long): Boolean {
    val identity = activePlaybackIdentity ?: return false
    return identity.itemId == itemId && identity.aid == aid && identity.cid == cid
  }

  fun setCompatibilityUnlocks(
    dolbyVision: Boolean,
    dolbyAtmos: Boolean,
    hiRes: Boolean = false,
  ) {
    unlockDolbyVision = dolbyVision
    unlockDolbyAtmos = dolbyAtmos
    unlockHiRes = hiRes
  }

  fun setDefaultSubtitlesEnabled(enabled: Boolean) {
    if (defaultSubtitlesEnabled == enabled) return
    defaultSubtitlesEnabled = enabled
    if (_subtitleState.value.tracks.isNotEmpty()) {
      selectSubtitle(activeSubtitles.firstOrNull()?.track?.id.takeIf { enabled })
    }
  }

  /** 立即应用已保存的默认值，并在每次准备新视频时再次应用。 */
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
    offlinePlaybackEntry?.let { entry ->
      playOffline(entry, startPositionMs)
      return
    }
    val player = exoPlayer ?: return
    val stream = data.streams.getOrNull(data.currentStreamIndex) ?: return
    pendingVideoTrackId = BiliDashManifest.videoTrackId(data.currentStreamIndex)
    pendingAudioTrackId = BiliDashManifest.audioTrackId(data)
    val manifest =
      runCatching { BiliDashManifest.build(data) }
        .onFailure { Log.w(TAG, "DASH manifest generation failed; using compatibility source", it) }
        .getOrNull()
    if (manifest == null) {
      playCompatibilityStream(stream.url, data.selectedAudioUrl(), startPositionMs)
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
        playCompatibilityStream(stream.url, data.selectedAudioUrl(), startPositionMs)
        return
      }
    activeManifestFile?.takeIf { it != manifestFile }?.delete()
    activeManifestFile = manifestFile
    val mediaItemBuilder =
      MediaItem.Builder()
        .setUri(Uri.fromFile(manifestFile))
        .setMimeType(MimeTypes.APPLICATION_MPD)
        .setSubtitleConfigurations(subtitleConfigurations())
    lastItem?.let { item ->
      mediaItemBuilder.setMediaId(item.id).setMediaMetadata(playbackMediaMetadata(item))
    }
    val mediaItem = mediaItemBuilder.build()
    val mediaSource = vodMediaSourceFactory.createMediaSource(mediaItem)
    if (startPositionMs != null) {
      player.setMediaSource(mediaSource, startPositionMs.coerceAtLeast(0L))
    } else {
      player.setMediaSource(mediaSource)
    }
    player.prepare()
    player.playWhenReady = true
    scheduleUndefinedCdnLoadFallback(player)
  }

  private fun playOffline(entry: OfflineMediaEntry, startPositionMs: Long? = null) {
    val player = exoPlayer ?: return
    val cacheOnlyFactory = OfflineMediaManager.get(getApplication()).cacheOnlyDataSourceFactory
    // DefaultDataSource 把 file:// 侧载字幕路由到 FileDataSource，而每个 HTTP 媒体请求
    // 仍然只经过仅缓存的上级。
    val offlineDataSourceFactory = DefaultDataSource.Factory(getApplication(), cacheOnlyFactory)
    val offlineMediaSourceFactory = DefaultMediaSourceFactory(offlineDataSourceFactory)
    val videoItem =
      MediaItem.Builder()
        .setUri(entry.videoUrl)
        .setMimeType(entry.videoMimeType)
        .setCustomCacheKey(entry.videoCacheKey)
        .setSubtitleConfigurations(subtitleConfigurations())
        .apply {
          lastItem?.let { item ->
            setMediaId(item.id)
            setMediaMetadata(playbackMediaMetadata(item))
          }
        }
        .build()
    val videoSource = offlineMediaSourceFactory.createMediaSource(videoItem)
    val mergedSource =
      entry.audioUrl.takeIf(String::isNotBlank)?.let { audioUrl ->
        val audioItem =
          MediaItem.Builder()
            .setUri(audioUrl)
            .setMimeType(entry.audioMimeType)
            .setCustomCacheKey(entry.audioCacheKey)
            .build()
        MergingMediaSource(
          videoSource,
          ProgressiveMediaSource.Factory(offlineDataSourceFactory).createMediaSource(audioItem),
        )
      } ?: videoSource
    if (startPositionMs != null) {
      player.setMediaSource(mergedSource, startPositionMs.coerceAtLeast(0L))
    } else {
      player.setMediaSource(mergedSource)
    }
    player.prepare()
    player.playWhenReady = true
  }

  /** 一旦某个 CDN 让播放器缓冲满整个超时窗口就切换一次。 */
  private fun scheduleCdnFallback(player: ExoPlayer) {
    if (cdnFallbackInProgress || cdnFallbackJob?.isActive == true) return
    cdnFallbackJob = viewModelScope.launch {
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

  /** 对没有进入明确 BUFFERING 状态的媒体加载补充 6 秒线路看门狗。 */
  private fun scheduleUndefinedCdnLoadFallback(player: ExoPlayer) {
    if (cdnFallbackInProgress || undefinedCdnLoadJob?.isActive == true || liveRoomId != null) return
    undefinedCdnLoadJob = viewModelScope.launch {
      delay(UNDEFINED_CDN_LOAD_TIMEOUT_MS)
      if (
        cdnFallbackInProgress ||
          skipNextCdnBufferingFallback ||
          player.playbackState == Player.STATE_BUFFERING ||
          player.playbackState == Player.STATE_READY ||
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
    Log.w(
      TAG,
      "buffering for ${SLOW_CDN_BUFFERING_TIMEOUT_MS}ms; switching ${stream.quality} to backup CDN",
    )
    playDash(nextData, resumePositionMs)
    player.playWhenReady = shouldPlay
    cdnFallbackInProgress = false
    if (player.playbackState == Player.STATE_BUFFERING) scheduleCdnFallback(player)
  }

  private fun resetCdnBufferingDetector(clearSeekSuppression: Boolean = true) {
    cdnFallbackJob?.cancel()
    cdnFallbackJob = null
    undefinedCdnLoadJob?.cancel()
    undefinedCdnLoadJob = null
    cdnFallbackInProgress = false
    if (clearSeekSuppression) skipNextCdnBufferingFallback = false
  }

  private fun playCompatibilityStream(
    videoUrl: String,
    audioUrl: String?,
    startPositionMs: Long? = null,
  ) {
    val player = exoPlayer ?: return
    val videoItem = cachedMediaItem(videoUrl, includeSubtitles = true)
    val videoSource = vodMediaSourceFactory.createMediaSource(videoItem)
    if (audioUrl != null) {
      val audioSource =
        ProgressiveMediaSource.Factory(cachedDataSourceFactory)
          .createMediaSource(cachedMediaItem(audioUrl))
      val source = MergingMediaSource(videoSource, audioSource)
      if (startPositionMs != null) {
        player.setMediaSource(source, startPositionMs.coerceAtLeast(0L))
      } else {
        player.setMediaSource(source)
      }
    } else {
      if (startPositionMs != null) {
        player.setMediaSource(videoSource, startPositionMs.coerceAtLeast(0L))
      } else {
        player.setMediaSource(videoSource)
      }
    }
    player.prepare()
    player.playWhenReady = true
    scheduleUndefinedCdnLoadFallback(player)
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
              val desiredStream = selectedData?.streams?.getOrNull(selectedData.currentStreamIndex)
              desiredStream?.let { stream ->
                (0 until group.length).firstOrNull { index ->
                  val format = group.getTrackFormat(index)
                  format.width == stream.width &&
                    format.height == stream.height &&
                    (stream.codecs.isBlank() ||
                      format.codecs.equals(stream.codecs, ignoreCase = true))
                }
              }
            } else {
              null
            }
          (directMatch ?: videoFormatFallback)?.let { index -> group to index }
        } ?: return
      val (group, index) = match
      // 自适应 DASH 选择会把每个符合条件的渲染规格都标记为选中。那并不代表请求的规格
      // 被锁定，所以这里检查 group.isTrackSelected(index) 会让画质菜单更新，而播放
      // 仍停留在自适应/默认轨道上。
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
    val selectedSubtitleId = _subtitleState.value.selectedTrackId
    val desiredSubtitle = activeSubtitles.firstOrNull { subtitle ->
      subtitle.track.id == selectedSubtitleId
    }
    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val subtitleMatch = desiredSubtitle?.let { desired ->
      textGroups.firstNotNullOfOrNull { group ->
        (0 until group.length)
          .firstOrNull { index ->
            val format = group.getTrackFormat(index)
            format.id == desired.track.id ||
              format.label == desired.track.displayLabel ||
              (activeSubtitles.count { it.track.language == desired.track.language } == 1 &&
                format.language == desired.track.language)
          }
          ?.let { index -> group to index }
      }
        ?: activeSubtitles
          .indexOf(desired)
          .takeIf { it >= 0 }
          ?.let { desiredIndex ->
            textGroups
              .flatMap { group -> (0 until group.length).map { index -> group to index } }
              .getOrNull(desiredIndex)
          }
    }
    val subtitleParameters =
      parameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, selectedSubtitleId == null)
        .apply {
          subtitleMatch?.let { (group, index) ->
            setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
          }
        }
        .build()
    if (subtitleParameters != parameters) {
      parameters = subtitleParameters
      changed = true
    }
    if (changed) player.trackSelectionParameters = parameters
  }

  private fun cachedMediaItem(url: String, includeSubtitles: Boolean = false): MediaItem {
    val uri = Uri.parse(url)
    val stablePath = uri.encodedPath?.takeIf { it.isNotBlank() } ?: url.substringBefore('?')
    val builder = MediaItem.Builder().setUri(uri).setCustomCacheKey("bili:$stablePath")
    if (includeSubtitles) builder.setSubtitleConfigurations(subtitleConfigurations())
    lastItem?.let { item ->
      builder.setMediaId(item.id).setMediaMetadata(playbackMediaMetadata(item))
    }
    return builder.build()
  }

  private suspend fun loadSubtitles(
    mediaId: String,
    bvid: String,
    aid: Long,
    cid: Long,
    generation: Long,
  ): PreparedSubtitleLoad {
    val identity =
      SubtitleMediaIdentity(
        mediaId = mediaId,
        bvid = bvid,
        aid = aid,
        cid = cid,
        generation = generation,
      )
    return try {
      Log.d(
        TAG,
        "subtitle catalog request: generation=$generation media=$mediaId bvid=$bvid aid=$aid cid=$cid",
      )
      val catalog = BiliSubtitleApi.getCatalog(aid = aid, cid = cid, bvid = bvid)
      val directory = File(getApplication<Application>().cacheDir, "bili_subtitles")
      if (catalog.tracks.isNotEmpty() && !directory.exists() && !directory.mkdirs()) {
        throw IllegalStateException("无法创建字幕缓存目录")
      }
      val prepared = coroutineScope {
        catalog.tracks
          .take(MAX_SUBTITLE_TRACKS)
          .mapIndexed { index, track ->
            async {
              try {
                val cues =
                  BiliSubtitleApi.getDocument(
                    track = track,
                    bvid = bvid,
                    aid = aid,
                    cid = cid,
                  )
                if (cues.isEmpty()) return@async null
                val safeId = track.id.replace(Regex("[^0-9A-Za-z_-]"), "_").take(48)
                val file = File(directory, "subtitle_${generation}_${index}_$safeId.vtt")
                file.writeText(BiliSubtitleApi.toWebVtt(cues), Charsets.UTF_8)
                PreparedSubtitle(track = track, file = file)
              } catch (e: CancellationException) {
                throw e
              } catch (e: Exception) {
                Log.w(TAG, "subtitle download failed: cid=$cid track=${track.id}", e)
                null
              }
            }
          }
          .mapNotNull { it.await() }
      }
      val message =
        when {
          prepared.isNotEmpty() -> null
          catalog.loginRequired -> "登录后可查看字幕"
          catalog.tracks.isNotEmpty() -> "字幕加载失败"
          else -> null
        }
      Log.d(
        TAG,
        "subtitle catalog result: generation=$generation media=$mediaId bvid=$bvid aid=$aid cid=$cid tracks=${prepared.size}",
      )
      PreparedSubtitleLoad(identity = identity, prepared = prepared, message = message)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(
        TAG,
        "subtitle catalog failed: generation=$generation media=$mediaId bvid=$bvid aid=$aid cid=$cid",
        e,
      )
      PreparedSubtitleLoad(identity = identity, prepared = emptyList(), message = "字幕加载失败")
    }
  }

  private fun subtitleConfigurations(): List<MediaItem.SubtitleConfiguration> =
    activeSubtitles
      .takeIf {
        val identity = activeSubtitleIdentity
        identity != null &&
          identity.mediaId == lastItem?.id &&
          _subtitleState.value.matches(identity)
      }
      .orEmpty()
      .map { subtitle ->
        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitle.file))
          .setMimeType(MimeTypes.TEXT_VTT)
          .setLanguage(subtitle.track.language)
          .setLabel(subtitle.track.displayLabel)
          .setId(subtitle.track.id)
          .setSelectionFlags(0)
          .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
          .build()
      }

  private fun clearActiveSubtitles() {
    activeSubtitles.filter(PreparedSubtitle::deleteOnClear).forEach { it.file.delete() }
    activeSubtitles = emptyList()
    activeSubtitleIdentity = null
  }

  private fun deleteSubtitleGenerationFiles(generation: Long) {
    val directory = File(getApplication<Application>().cacheDir, "bili_subtitles")
    directory
      .listFiles { file -> file.isFile && file.name.startsWith("subtitle_${generation}_") }
      ?.forEach { it.delete() }
  }

  fun release() {
    loadJob?.cancel()
    loadJob = null
    loadGeneration++
    exitBackgroundAudioMode()
    if (getApplication<BiliApplication>().playbackSessionTarget == PlaybackSessionTarget.DETAIL) {
      PlaybackSessionService.stop(getApplication())
    }
    exoPlayer?.release()
    exoPlayer = null
    playData = null
    offlinePlaybackEntry = null
    activePlaybackIdentity = null
    loadedVideoId = null
    activeManifestFile?.delete()
    activeManifestFile = null
    pendingVideoTrackId = null
    pendingAudioTrackId = null
    clearActiveSubtitles()
    _subtitleState.value = PlayerSubtitleState()
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
      data.streams
        .filter { it.id !in setOf(125, 126) }
        .takeLast(1)
        .ifEmpty {
          data.streams.take(1)
        }
    val streams = compatibleStreams.ifEmpty { fallbackStreams }
    val dolbyAvailable = mediaCapabilities.supportsDolbyAtmos || unlockDolbyAtmos
    val hiResAvailable = mediaCapabilities.supportsHiRes || unlockHiRes
    return data.copy(
      streams = streams,
      currentStreamIndex = BiliVideoApi.defaultStreamIndex(streams),
      dolbyAudioUrl = data.dolbyAudioUrl.takeIf { dolbyAvailable },
      dolbyAudio = data.dolbyAudio.takeIf { dolbyAvailable },
      hiResAudioUrl = data.hiResAudioUrl.takeIf { hiResAvailable },
      hiResAudio = data.hiResAudio.takeIf { hiResAvailable },
    )
  }

  /**
   * B 站的第一个备用主机在目标平板上被证明更稳定，因此它是默认路由。一旦欠载检测器
   * 选择了另一个主机，该主机对之后的视频也生效。
   */
  private fun prioritizeCdnRoutes(data: PlayUrlData): PlayUrlData {
    refreshConfiguredCdnPreference()
    fun video(stream: VideoStream): VideoStream {
      val ordered = prioritizeCdnUrls(stream.url, stream.backupUrls, preferredCdnHost)
      return stream.copy(url = ordered.primary, backupUrls = ordered.backups)
    }

    fun audio(
      stream: dev.openbili.webdemo.api.AudioStream?
    ): dev.openbili.webdemo.api.AudioStream? {
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
    playbackRoutingPrefs
      .edit()
      .putString(KEY_PREFERRED_CDN_HOST, host)
      .putString(KEY_PREFERRED_CDN_REGION, preferredCdnRegion.name)
      .apply()
  }

  private fun refreshConfiguredCdnPreference() {
    val configuredRegion = readCdnRegionPreference(getApplication())
    if (configuredRegion == preferredCdnRegion) return
    preferredCdnRegion = configuredRegion
    preferredCdnHost = configuredRegion.preferredHost
    playbackRoutingPrefs
      .edit()
      .remove(KEY_PREFERRED_CDN_HOST)
      .remove(KEY_PREFERRED_CDN_REGION)
      .apply()
  }

  private fun loadPersistedCdnHost(region: CdnRegionPreference): String {
    val persistedRegion =
      runCatching {
          CdnRegionPreference.valueOf(
            playbackRoutingPrefs.getString(KEY_PREFERRED_CDN_REGION, null).orEmpty()
          )
        }
        .getOrNull()
    return if (persistedRegion == region) {
      playbackRoutingPrefs.getString(KEY_PREFERRED_CDN_HOST, null).orEmpty().ifBlank {
        region.preferredHost
      }
    } else {
      region.preferredHost
    }
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
    const val MAX_SUBTITLE_TRACKS = 12
    const val SLOW_CDN_BUFFERING_TIMEOUT_MS = 3_000L
    const val UNDEFINED_CDN_LOAD_TIMEOUT_MS = 6_000L
    const val TAG = "PlayerVM"
    const val PLAYBACK_ROUTING_PREFS = "playback_routing"
    const val KEY_PREFERRED_CDN_HOST = "preferred_cdn_host"
    const val KEY_PREFERRED_CDN_REGION = "preferred_cdn_region"
  }

  private data class LoadedPlayback(
    val playData: PlayUrlData,
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val durationMs: Long,
    val resumePositionMs: Long,
    val subtitles: PreparedSubtitleLoad,
  )

  private data class ActivePlaybackIdentity(
    val itemId: String,
    val aid: Long,
    val cid: Long,
  )

  private data class PreparedSubtitle(
    val track: VideoSubtitleTrack,
    val file: File,
    val deleteOnClear: Boolean = true,
  )

  private data class PreparedSubtitleLoad(
    val identity: SubtitleMediaIdentity,
    val prepared: List<PreparedSubtitle>,
    val message: String?,
  )

  private data class SubtitleMediaIdentity(
    val mediaId: String,
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val generation: Long,
  )

  private fun PlayerSubtitleState.matches(identity: SubtitleMediaIdentity): Boolean =
    mediaId == identity.mediaId &&
      bvid.equals(identity.bvid, ignoreCase = true) &&
      aid == identity.aid &&
      cid == identity.cid
}

internal fun isReplayTargetCurrent(loadedVideoId: String?, expectedVideoId: String): Boolean =
  loadedVideoId == expectedVideoId

internal fun subtitleStateForMedia(
  state: PlayerSubtitleState,
  mediaId: String,
  cid: Long,
  bvid: String? = null,
  aid: Long = 0L,
): PlayerSubtitleState =
  state.takeIf {
    it.mediaId == mediaId &&
      (cid <= 0L || it.cid == cid) &&
      (bvid.isNullOrBlank() || it.bvid.equals(bvid, ignoreCase = true)) &&
      (aid <= 0L || it.aid == aid)
  } ?: PlayerSubtitleState(mediaId = mediaId, bvid = bvid, aid = aid, cid = cid)

internal fun playbackMediaMetadata(item: FeedItem): MediaMetadata {
  val artworkUri =
    UrlPolicy.normalizeImageUrl(item.coverUrl)?.takeIf(String::isNotBlank)?.let(Uri::parse)
  return MediaMetadata.Builder()
    .setTitle(item.title)
    .setArtist(item.uploader?.takeIf(String::isNotBlank))
    .setArtworkUri(artworkUri)
    .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
    .build()
}

internal fun audioOnlyTrackSelectionParameters(
  parameters: TrackSelectionParameters
): TrackSelectionParameters =
  parameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()

internal data class PrioritizedCdnUrls(val primary: String, val backups: List<String>)

internal fun prioritizeCdnUrls(
  primary: String,
  backups: List<String>,
  preferredHost: String,
): PrioritizedCdnUrls {
  // 备用 #1 是新的基线路由。保留 API 主地址作为最终回退。
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

internal fun resolvePlaybackDurationSeconds(
  selectedPage: VideoPage?,
  totalDurationSeconds: Long,
): Long = selectedPage?.durationSeconds?.takeIf { it > 0L } ?: totalDurationSeconds

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

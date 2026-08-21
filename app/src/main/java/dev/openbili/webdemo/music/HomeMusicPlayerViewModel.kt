package dev.openbili.webdemo.music

/**
 * 首页“音乐”入口的播放状态机与收藏夹数据源。
 *
 * 本文件围绕 [HomeMusicPlayerViewModel] 组织，它同时承担三层职责：
 *  - 收藏夹数据：加载 / 搜索 / 分页“音乐”收藏夹、创建文件夹、删除条目；
 *  - 播放控制：用独立的音频主播放器与静音画面播放器播放 DASH 流，含重试、前后台恢复、
 *    高级音轨（杜比 / 高解析）与画质选择；画面缓冲不会阻塞音频时钟；
 *  - 频谱可视化：通过自定义 [Pcm16SpectrumTap] 从 AudioSink 截取 PCM，再经
 *    [analyzeMusicSpectrum] 计算频带并推进节拍检测。
 *
 * 文件顶部另有一批顶层纯函数（收藏夹匹配、流选择、重试退避、前台恢复判定等），
 * 便于脱离 Android 环境做单元测试。
 */

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.openbili.webdemo.api.AudioStream
import dev.openbili.webdemo.api.BiliBangumiApi
import dev.openbili.webdemo.api.BiliFavoriteApi
import dev.openbili.webdemo.api.BiliVideoApi
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.FeedCard
import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.api.PremiumAudioMode
import dev.openbili.webdemo.api.VideoStream
import dev.openbili.webdemo.BiliApplication
import dev.openbili.webdemo.BiliDashManifest
import dev.openbili.webdemo.effectiveStreamHeight
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.PlaybackCache
import dev.openbili.webdemo.PlaybackSessionService
import dev.openbili.webdemo.PlaybackSessionTarget
import dev.openbili.webdemo.configuredCdnHost
import dev.openbili.webdemo.prioritizeCdnUrls
import dev.openbili.webdemo.selectPreferredStreamIndex
import dev.openbili.webdemo.settings.AdvancedAudioPriority
import dev.openbili.webdemo.settings.DeviceMediaCapabilities
import dev.openbili.webdemo.settings.PreferredResolutionMode
import dev.openbili.webdemo.UrlPolicy
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** “音乐”收藏夹的默认标题；当用户未显式选择文件夹时按标题匹配。 */
internal const val MusicFavoriteFolderTitle = "音乐"

/** 音乐收藏夹的加载状态，驱动首页音乐区的占位 / 错误 / 列表三态切换。 */
internal enum class MusicLibraryStatus {
  SIGNED_OUT,
  LOADING,
  MISSING,
  READY,
  ERROR,
}

/** 列表播放顺序：顺序、随机或单曲循环。 */
internal enum class MusicPlaybackOrder {
  SEQUENTIAL,
  RANDOM,
  SINGLE_REPEAT,
}

internal fun nextMusicPlaybackOrder(current: MusicPlaybackOrder): MusicPlaybackOrder =
  when (current) {
    MusicPlaybackOrder.SEQUENTIAL -> MusicPlaybackOrder.RANDOM
    MusicPlaybackOrder.RANDOM -> MusicPlaybackOrder.SINGLE_REPEAT
    MusicPlaybackOrder.SINGLE_REPEAT -> MusicPlaybackOrder.SEQUENTIAL
  }

internal fun musicRepeatMode(order: MusicPlaybackOrder): Int =
  if (order == MusicPlaybackOrder.SINGLE_REPEAT) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

/**
 * 音乐页对外暴露的单一 UI 状态。
 *
 * 所有可变状态集中在一个不可变 data class 里，通过 copy 更新；positionMs / durationMs
 * 这类高频字段被 [withoutRapidlyChangingFields] 剥离后单独走 [screenState]，
 * 以免进度刷新触发整个页面的重组。
 */
internal data class HomeMusicUiState(
  val accountMid: Long = 0L,
  /** 0 表示按默认标题“音乐”查找收藏夹。 */
  val folderSelectionId: Long = 0L,
  val folderSelectionConfigured: Boolean = false,
  val libraryStatus: MusicLibraryStatus = MusicLibraryStatus.SIGNED_OUT,
  val availableFolders: List<FavoriteFolder> = emptyList(),
  val folder: FavoriteFolder? = null,
  val items: List<FeedItem> = emptyList(),
  val query: String = "",
  val page: Int = 0,
  val hasMore: Boolean = false,
  val loadingMore: Boolean = false,
  val creatingFolder: Boolean = false,
  val libraryError: String? = null,
  val currentItem: FeedItem? = null,
  val playbackLoading: Boolean = false,
  val firstFrameReady: Boolean = false,
  val playbackError: String? = null,
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val volume: Float = 1f,
  val muted: Boolean = false,
  val lastAudibleVolume: Float = 1f,
  val playbackOrder: MusicPlaybackOrder = MusicPlaybackOrder.SEQUENTIAL,
  val dolbyAvailable: Boolean = false,
  val hiResAvailable: Boolean = false,
  val selectedPremiumAudio: PremiumAudioMode? = null,
  val displayNameOverrides: Map<String, String> = emptyMap(),
  val deletingItemIds: Set<String> = emptySet(),
)

/** 播放进度的精简状态，供进度条单独订阅，避免每次位置刷新都重建整个 UI 状态。 */
internal data class MusicPlaybackProgressState(
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val enabled: Boolean = false,
)

// 把高频变化的进度字段清零，使 screenState 只在“真正影响 UI 布局”的字段变化时才发新值。
private fun HomeMusicUiState.withoutRapidlyChangingFields(): HomeMusicUiState =
  copy(
    positionMs = 0L,
    durationMs = 0L,
  )

/**
 * 判断前台恢复后是否需要重建播放器。
 *
 * 回到前台时播放器可能已被系统挂起或音视频渲染器失联：出错、处于 IDLE，或后台停留
 * 超过阈值且（首帧未就绪 / 应播却未播且不在缓冲）都视为需要恢复。抽成顶层纯函数便于测试。
 */
internal fun needsMusicForegroundRecovery(
  backgroundDurationMs: Long,
  firstFrameReady: Boolean,
  playbackState: Int,
  playerHasError: Boolean,
  shouldBePlaying: Boolean,
  isPlaying: Boolean,
): Boolean =
  playerHasError ||
    playbackState == Player.STATE_IDLE ||
    (backgroundDurationMs >= MUSIC_FOREGROUND_RECOVERY_THRESHOLD_MS &&
      (!firstFrameReady ||
        (shouldBePlaying && !isPlaying && playbackState != Player.STATE_BUFFERING)))

/** 在收藏夹列表中定位“音乐”文件夹：优先按显式选择的 id，否则回退到标题匹配。 */
internal fun findMusicFavoriteFolder(
  folders: List<FavoriteFolder>,
  preferredFolderId: Long = 0L,
): FavoriteFolder? =
  if (preferredFolderId > 0L) folders.firstOrNull { it.id == preferredFolderId }
  else folders.firstOrNull { it.title.trim() == MusicFavoriteFolderTitle }

/**
 * 解析本次会话应使用的音乐收藏夹。
 *
 * 只有“已配置过选择”时才真正去匹配；否则返回 null，交由调用方走默认 / 缺失流程，
 * 避免把从未选择过文件夹的新账号误判成有现成收藏夹。
 */
internal fun resolveMusicFavoriteFolder(
  folders: List<FavoriteFolder>,
  preferredFolderId: Long,
  folderSelectionConfigured: Boolean,
): FavoriteFolder? =
  if (!folderSelectionConfigured && preferredFolderId <= 0L) null
  else findMusicFavoriteFolder(folders, preferredFolderId)

/** 音乐页是否可进入：加载完成、或处于可安全展示的非就绪状态（未登录 / 缺失 / 出错）。 */
internal fun isMusicLaunchReady(state: HomeMusicUiState): Boolean =
  when (state.libraryStatus) {
    MusicLibraryStatus.LOADING -> false
    MusicLibraryStatus.SIGNED_OUT,
    MusicLibraryStatus.MISSING,
    MusicLibraryStatus.ERROR -> true
    MusicLibraryStatus.READY ->
      if (state.items.isEmpty()) true
      else state.currentItem != null && (!state.playbackLoading || state.playbackError != null)
  }

/** 把收藏夹接口返回的卡片转成通用 [FeedItem]；id 优先用 bvid，缺失时退回 av 号。 */
internal fun musicFeedItem(card: FeedCard): FeedItem =
  FeedItem(
    id = card.bvid.ifBlank { "av${card.aid}" },
    title = card.title,
    videoUrl =
      if (card.bvid.isNotBlank()) "https://www.bilibili.com/video/${card.bvid}"
      else "https://www.bilibili.com/video/av${card.aid}",
    coverUrl = card.coverUrl,
    uploader = card.uploaderName,
    playCount = FeedViewModel.formatCount(card.playCount),
    duration = FeedViewModel.formatDuration(card.durationSeconds),
    uploaderFace = card.uploaderFace,
    uploaderMid = card.uploaderMid,
    danmakuCount = card.danmakuCount,
    publishedAt = card.pubdate,
    description = card.description,
    favoriteResourceId = card.aid,
    favoriteResourceType = card.resourceType,
  )

/**
 * 为音乐页选择视频流索引。
 *
 * 音乐场景的视频流不会超过 1080P+ / 1080P60，非大会员账号也不会超过普通 1080P，
 * 因此先按会员状态降级目标画质，再过滤掉 112 / 116（杜比视界 / HDR）等特殊编码。
 */
internal fun selectMusicStreamIndex(
  streams: List<VideoStream>,
  requestedMode: PreferredResolutionMode,
  vipActive: Boolean,
): Int {
  val effectiveMode =
    when {
      requestedMode == PreferredResolutionMode.EXTREME ->
        if (vipActive) PreferredResolutionMode.ULTRA_HIGH else PreferredResolutionMode.HIGH
      requestedMode == PreferredResolutionMode.ULTRA_HIGH && !vipActive ->
        PreferredResolutionMode.HIGH
      else -> requestedMode
    }
  return selectPreferredStreamIndex(streams, effectiveMode) {
    effectiveStreamHeight(it) <= 1080 && (vipActive || (it.id != 112 && it.id != 116))
  }
}

/**
 * 计算相邻曲目索引：顺序模式循环 ±1，随机模式排除当前曲目后取一个不等于当前的位置。
 * [randomValue] 注入使函数可预测，便于测试；为 null 时由 [kotlin.random.Random] 生成。
 */
internal fun adjacentMusicIndex(
  itemCount: Int,
  currentIndex: Int,
  order: MusicPlaybackOrder,
  direction: Int,
  randomValue: Int? = null,
): Int {
  if (itemCount <= 1) return 0
  val normalized = currentIndex.takeIf { it in 0 until itemCount } ?: 0
  if (order != MusicPlaybackOrder.RANDOM) {
    return (normalized + (if (direction < 0) -1 else 1) + itemCount) % itemCount
  }
  val value = (randomValue ?: Random.nextInt(itemCount - 1)).coerceIn(0, itemCount - 2)
  return if (value >= normalized) value + 1 else value
}

/** 播放失败后的指数退避表（毫秒）：前几次快速重试，随后固定为 15 秒。 */
internal fun musicRetryDelayMillis(attempt: Int): Long =
  longArrayOf(0L, 800L, 1_600L, 3_200L, 6_400L, 12_000L, 15_000L).getOrElse(
    attempt.coerceAtLeast(0)
  ) {
    15_000L
  }

@OptIn(UnstableApi::class)
internal class HomeMusicPlayerViewModel(application: Application) : AndroidViewModel(application) {
  private val _state = MutableStateFlow(HomeMusicUiState())
  val state: StateFlow<HomeMusicUiState> = _state.asStateFlow()
  val screenState: StateFlow<HomeMusicUiState> =
    _state
      .map { current -> current.withoutRapidlyChangingFields() }
      .distinctUntilChanged()
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        _state.value.withoutRapidlyChangingFields(),
      )
  // 绘制阶段消费的可变就地目标缓冲。由频谱任务在主线程写入；由可视化器的
  // withFrameNanos 循环读取，且不会触发重组。
  val spectrumTargets = FloatArray(MUSIC_SPECTRUM_BAND_COUNT)
  val progressState: StateFlow<MusicPlaybackProgressState> =
    _state
      .map { current ->
        MusicPlaybackProgressState(
          positionMs = current.positionMs,
          durationMs = current.durationMs,
          enabled = current.currentItem != null && current.durationMs > 0L,
        )
      }
      .distinctUntilChanged()
      .stateIn(viewModelScope, SharingStarted.Eagerly, MusicPlaybackProgressState())

  private val app: BiliApplication
    get() = getApplication()

  private val mediaCapabilities by lazy { DeviceMediaCapabilities.detect(getApplication()) }
  private val audioManager by lazy {
    getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
  }
  private var measuredOutputLatencyMs: Long = MUSIC_SPECTRUM_OUTPUT_LATENCY_MS
  private val resumePrefs by lazy {
    getApplication<Application>().getSharedPreferences("home_music_resume", Context.MODE_PRIVATE)
  }
  private val displayNameStore by lazy { MusicDisplayNameStore(getApplication()) }
  private val httpFactory =
    DefaultHttpDataSource.Factory()
      .setUserAgent(
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
      )
      .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com/"))
  private val dataSourceFactory by lazy {
    DefaultDataSource.Factory(getApplication(), httpFactory)
  }
  private val cachedDataSourceFactory by lazy {
    CacheDataSource.Factory()
      .setCache(PlaybackCache.get(getApplication()))
      .setUpstreamDataSourceFactory(dataSourceFactory)
      .setCacheKeyFactory { dataSpec ->
        dataSpec.key
          ?: dataSpec.uri.encodedPath?.takeIf(String::isNotBlank)?.let { "bili:$it" }
          ?: dataSpec.uri.toString()
      }
      .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
  }

  private var libraryJob: Job? = null
  private var queryJob: Job? = null
  private var mediaJob: Job? = null
  private var retryJob: Job? = null
  private var videoRetryJob: Job? = null
  private var positionJob: Job? = null
  private var foregroundRecoveryJob: Job? = null
  private var libraryGeneration = 0L
  private var mediaGeneration = 0L
  private var pageActive = false
  private var appInForeground = true
  private var backgroundedAtElapsedRealtime = 0L
  private var resumeWhenReady = true
  private var activeAudioManifestFile: File? = null
  private var activeVideoManifestFile: File? = null
  private var activeResolvedTrack: ResolvedTrack? = null
  private var pendingVideoSource: MediaSource? = null
  private var activeVideoItemId: String? = null
  private var retryAttempt = 0
  private var lastStablePositionMs = 0L
  private var lastPersistedPositionMs = -RESUME_WRITE_INTERVAL_MS
  private var retryPreparedPositionMs = 0L
  private var lastFailureWasNetwork = false
  private var preferSoftwareAudioDecoder = false
  private var unlockDolbyAtmos = false
  private var unlockHiRes = false
  private var advancedAudioEnabled = false
  private var advancedAudioPriority = AdvancedAudioPriority.DOLBY
  private var musicPreferredResolutionMode = PreferredResolutionMode.ULTRA_HIGH
  private var accountVipActive = false
  private var preparedEntryAccountMid = Long.MIN_VALUE
  private var preparedEntryFolderSelectionId = Long.MIN_VALUE
  private var preparedEntryFolderSelectionConfigured = false
  @Volatile private var spectrumEpoch = 0L
  private val spectrumFrames =
    MutableSharedFlow<SpectrumFrame>(
      // 保留足够的 24 ms PCM 快照以覆盖正常的 AudioTrack 输出前置量。第一帧延迟帧
      // 呈现之后，后续帧保持原有的节奏。
      extraBufferCapacity = MUSIC_SPECTRUM_DELAY_QUEUE_CAPACITY,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  private val beatTracker = MusicBeatTracker()
  private val spectrumJob =
    viewModelScope.launch(Dispatchers.Default) {
      // 每次只处理一个已呈现的帧。共享流只保留一个待处理帧，因此新的 PCM 快照会替换
      // 过期的排队工作，而不会取消当前正在等待 AudioTrack 的帧。collectLatest 会在
      // 下一个 24 ms 回调时取消每个延迟帧，可视化器就永远发布不了任何东西。
      spectrumFrames.collect { frame ->
        val bands =
          analyzeMusicSpectrum(
            frame.samples,
            frame.sampleRate,
            systemVolume = frame.systemVolume,
          )
        val beatBoost = beatTracker.next(musicFrameRms(frame.samples))
        withContext(Dispatchers.Main.immediate) {
          adaptOutputLatency(frame.presentationTimeUs, frame.audioSinkPositionUs)
          while (pageActive && frame.epoch == spectrumEpoch) {
            val playback = player ?: break
            if (!playback.isPlaying) {
              delay(MUSIC_SPECTRUM_PRESENTATION_POLL_MS)
              continue
            }
            val elapsedSinceCaptureMs =
              ((System.nanoTime() - frame.capturedAtNanos) / 1_000_000L).coerceAtLeast(0L)
            val waitMillis =
              musicSpectrumFrameWaitMillis(
                frame.presentationTimeUs,
                playback.currentPosition,
                elapsedSinceCaptureMs,
                frame.audioSinkPositionUs,
                outputLatencyMs = measuredOutputLatencyMs,
              )
            if (waitMillis <= 0L) break
            delay(waitMillis.coerceAtMost(MUSIC_SPECTRUM_PRESENTATION_POLL_MS))
          }
          if (pageActive && frame.epoch == spectrumEpoch && bands.isNotEmpty()) {
            bands.forEachIndexed { index, value ->
              spectrumTargets[index] = (value * beatBoost).coerceAtMost(1f)
            }
          }
        }
      }
    }

  var player: ExoPlayer? = null
    private set

  /** 只负责 TextureView 画面的静音播放器；音频和媒体会话始终由 [player] 驱动。 */
  var videoPlayer: ExoPlayer? = null
    private set

  fun preparePlayer(): ExoPlayer {
    player?.let {
      return it
    }
    val audioAttributes =
      AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()
    val renderersFactory =
      MusicRenderersFactory(
          getApplication(),
          preferSoftwareAudioDecoder,
          ::submitSpectrumSamples,
        )
        .apply {
          setEnableDecoderFallback(true)
          if (preferSoftwareAudioDecoder) forceDisableMediaCodecAsynchronousQueueing()
        }
    return ExoPlayer.Builder(getApplication(), renderersFactory)
      .setAudioAttributes(audioAttributes, true)
      .setHandleAudioBecomingNoisy(true)
      .build()
      .also { created ->
        created.repeatMode = musicRepeatMode(_state.value.playbackOrder)
        created.volume = 1f
        created.addListener(
          object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
              resumeWhenReady = playWhenReady
              syncVideoPlaybackToAudio()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
              _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
              when (playbackState) {
                Player.STATE_READY -> {
                  _state.value =
                    _state.value.copy(
                      playbackLoading = false,
                      playbackError = null,
                      durationMs = created.duration.coerceAtLeast(0L),
                    )
                  syncVideoPlaybackToAudio(forceSeek = true)
                }
                Player.STATE_ENDED -> playNext()
              }
            }

            override fun onPlayerError(error: PlaybackException) {
              handlePlayerError(error)
            }
          }
        )
        player = created
      }
  }

  fun prepareVideoPlayer(): ExoPlayer {
    videoPlayer?.let { return it }
    return ExoPlayer.Builder(getApplication())
      .build()
      .also { created ->
        created.volume = 0f
        created.repeatMode = musicRepeatMode(_state.value.playbackOrder)
        created.trackSelectionParameters =
          created.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        created.addListener(
          object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
              if (playbackState == Player.STATE_READY) syncVideoPlaybackToAudio(forceSeek = true)
            }

            override fun onRenderedFirstFrame() {
              val renderedId = created.currentMediaItem?.mediaId
              if (renderedId != null && renderedId == _state.value.currentItem?.id) {
                _state.value = _state.value.copy(firstFrameReady = true)
              }
            }

            override fun onPlayerError(error: PlaybackException) {
              Log.w(TAG, "music video renderer failed; audio continues", error)
              _state.value = _state.value.copy(firstFrameReady = false)
              activeVideoItemId = null
              scheduleVideoRetry()
            }
          }
        )
        videoPlayer = created
      }
  }

  private fun preparePendingVideoPlayback() {
    if (!pageActive || !appInForeground) return
    val item = _state.value.currentItem ?: return
    val source = pendingVideoSource ?: return
    // 先让独立音频时间线达到可播放状态，再允许视频占用网络与解码器资源。
    val audio = player?.takeIf { it.playbackState == Player.STATE_READY } ?: return
    val playback = prepareVideoPlayer()
    val startPositionMs = audio.currentPosition.coerceAtLeast(0L)
    playback.stop()
    playback.clearMediaItems()
    playback.setMediaSource(source, startPositionMs)
    pendingVideoSource = null
    activeVideoItemId = item.id
    playback.repeatMode = musicRepeatMode(_state.value.playbackOrder)
    playback.prepare()
    playback.playWhenReady = audio.playWhenReady
  }

  private fun syncVideoPlaybackToAudio(forceSeek: Boolean = false) {
    if (!pageActive || !appInForeground) {
      videoPlayer?.pause()
      return
    }
    preparePendingVideoPlayback()
    val audio = player ?: return
    val video = videoPlayer ?: return
    val itemId = _state.value.currentItem?.id ?: return
    if (activeVideoItemId != itemId || video.currentMediaItem?.mediaId != itemId) return
    val positionDelta = abs(video.currentPosition - audio.currentPosition)
    if (
      positionDelta > MUSIC_VIDEO_SYNC_TOLERANCE_MS &&
        (forceSeek || video.playbackState == Player.STATE_READY)
    ) {
      video.seekTo(audio.currentPosition.coerceAtLeast(0L))
    }
    video.repeatMode = musicRepeatMode(_state.value.playbackOrder)
    video.playWhenReady = audio.playWhenReady
  }

  fun configureAudio(
    unlockDolbyAtmos: Boolean,
    unlockHiRes: Boolean,
    advancedAudioEnabled: Boolean,
    advancedAudioPriority: AdvancedAudioPriority,
  ) {
    this.unlockDolbyAtmos = unlockDolbyAtmos
    this.unlockHiRes = unlockHiRes
    this.advancedAudioEnabled = advancedAudioEnabled
    this.advancedAudioPriority = advancedAudioPriority
    activeResolvedTrack?.let { resolved ->
      val dolby = supportsDolby(resolved.data)
      val hiRes = supportsHiRes(resolved.data)
      val selected =
        _state.value.selectedPremiumAudio.takeIf { mode ->
          mode == PremiumAudioMode.DOLBY && dolby || mode == PremiumAudioMode.HI_RES && hiRes
        }
      _state.value =
        _state.value.copy(
          dolbyAvailable = dolby,
          hiResAvailable = hiRes,
          selectedPremiumAudio = selected,
        )
    }
  }

  fun configureVideoQuality(mode: PreferredResolutionMode, vipActive: Boolean) {
    musicPreferredResolutionMode = mode
    accountVipActive = vipActive
  }

  /**
   * 解析选中的收藏夹并准备记忆的媒体源，但不启动播放。HomeHub 在推荐页仍完全可见时
   * 调用它。
   */
  fun prepareOpen(
    accountMid: Long,
    folderSelectionId: Long,
    folderSelectionConfigured: Boolean,
  ) {
    val normalizedFolderId = folderSelectionId.coerceAtLeast(0L)
    val normalizedConfigured = folderSelectionConfigured || normalizedFolderId > 0L
    if (
      preparedEntryAccountMid == accountMid &&
        preparedEntryFolderSelectionId == normalizedFolderId &&
        preparedEntryFolderSelectionConfigured == normalizedConfigured
    )
      return
    preparedEntryAccountMid = accountMid
    preparedEntryFolderSelectionId = normalizedFolderId
    preparedEntryFolderSelectionConfigured = normalizedConfigured
    val targetChanged =
      accountMid != _state.value.accountMid ||
        normalizedFolderId != _state.value.folderSelectionId ||
        normalizedConfigured != _state.value.folderSelectionConfigured
    pageActive = false
    if (targetChanged) {
      persistResumeSnapshot(force = true)
      stopPlayback(clearCurrent = true)
      _state.value =
        HomeMusicUiState(
          accountMid = accountMid,
          folderSelectionId = normalizedFolderId,
          folderSelectionConfigured = normalizedConfigured,
          displayNameOverrides = displayNameStore.getForAccount(accountMid),
          libraryStatus =
            if (accountMid > 0L) MusicLibraryStatus.LOADING else MusicLibraryStatus.SIGNED_OUT,
        )
    }
    preparePlayer()
    applyForegroundVideoState()
    syncSystemVolume()
    // 音乐页进入也是一次服务器同步边界。不要复用上一次访问的内存收藏夹快照，
    // 因为收藏可能已在别处变化。
    if (accountMid > 0L) {
      loadLibrary(resetQuery = true)
    }
  }

  fun open(
    accountMid: Long,
    folderSelectionId: Long,
    folderSelectionConfigured: Boolean,
  ) {
    val normalizedFolderId = folderSelectionId.coerceAtLeast(0L)
    val normalizedConfigured = folderSelectionConfigured || normalizedFolderId > 0L
    if (
      preparedEntryAccountMid != accountMid ||
        preparedEntryFolderSelectionId != normalizedFolderId ||
        preparedEntryFolderSelectionConfigured != normalizedConfigured
    ) {
      prepareOpen(accountMid, normalizedFolderId, normalizedConfigured)
    }
    pageActive = true
    preparedEntryAccountMid = Long.MIN_VALUE
    preparedEntryFolderSelectionId = Long.MIN_VALUE
    preparedEntryFolderSelectionConfigured = false
    app.sharedPlayerViewModel.exoPlayer?.pause()
    val playback = preparePlayer()
    applyForegroundVideoState()
    syncSystemVolume()
    startPositionUpdates()
    app.playbackSessionTarget = PlaybackSessionTarget.MUSIC
    PlaybackSessionService.publishMusicPlayer(getApplication())
    val currentItem = _state.value.currentItem
    if (
      currentItem != null &&
        playback.currentMediaItem?.mediaId == currentItem.id &&
        _state.value.playbackError == null
    ) {
      playback.playWhenReady = resumeWhenReady
      syncVideoPlaybackToAudio(forceSeek = true)
    } else if (_state.value.playbackError != null) {
      scheduleRetry()
    }
  }

  fun setAppInForeground(inForeground: Boolean) {
    if (appInForeground == inForeground) return
    appInForeground = inForeground
    if (!inForeground) {
      foregroundRecoveryJob?.cancel()
      foregroundRecoveryJob = null
      backgroundedAtElapsedRealtime = SystemClock.elapsedRealtime()
      applyForegroundVideoState()
      return
    }
    val backgroundDurationMs =
      (SystemClock.elapsedRealtime() - backgroundedAtElapsedRealtime).coerceAtLeast(0L)
    backgroundedAtElapsedRealtime = 0L
    if (_state.value.currentItem != null) {
      _state.value = _state.value.copy(firstFrameReady = false)
    }
    applyForegroundVideoState()
    scheduleForegroundRecovery(backgroundDurationMs)
  }

  private fun applyForegroundVideoState() {
    if (!appInForeground || !pageActive) {
      videoPlayer?.pause()
    } else {
      syncVideoPlaybackToAudio(forceSeek = true)
      if (
        pendingVideoSource == null &&
          activeVideoItemId != _state.value.currentItem?.id
      ) {
        scheduleVideoRetry()
      }
    }
  }

  private fun scheduleForegroundRecovery(backgroundDurationMs: Long) {
    val expectedItemId = _state.value.currentItem?.id ?: return
    val playback = player ?: return
    if (
      backgroundDurationMs < MUSIC_FOREGROUND_RECOVERY_THRESHOLD_MS &&
        playback.playerError == null &&
        playback.playbackState != Player.STATE_IDLE
    ) {
      return
    }
    val shouldPlay = resumeWhenReady || playback.playWhenReady
    val recoveryPositionMs = maxOf(lastStablePositionMs, playback.currentPosition.coerceAtLeast(0L))
    foregroundRecoveryJob?.cancel()
    foregroundRecoveryJob = viewModelScope.launch {
      delay(MUSIC_FOREGROUND_RECOVERY_WATCHDOG_MS)
      if (!pageActive || !appInForeground || _state.value.currentItem?.id != expectedItemId) {
        return@launch
      }
      val currentPlayer = player
      val needsRecovery =
        currentPlayer == null ||
          needsMusicForegroundRecovery(
            backgroundDurationMs = backgroundDurationMs,
            // 画面使用独立播放器，不能再用视频首帧判定音频主播放器是否健康。
            firstFrameReady = currentPlayer.playbackState == Player.STATE_READY,
            playbackState = currentPlayer.playbackState,
            playerHasError = currentPlayer.playerError != null,
            shouldBePlaying = shouldPlay,
            isPlaying = currentPlayer.isPlaying,
          )
      if (!needsRecovery) return@launch
      Log.w(
        TAG,
        "foreground renderer did not recover after ${backgroundDurationMs}ms; rebuilding at " +
          "$recoveryPositionMs ms",
      )
      lastStablePositionMs = recoveryPositionMs
      _state.value =
        _state.value.copy(
          playbackLoading = true,
          playbackError = null,
          firstFrameReady = false,
          isPlaying = false,
        )
      val oldPlayer = player
      player = null
      oldPlayer?.release()
      videoPlayer?.stop()
      videoPlayer?.clearMediaItems()
      activeVideoItemId = null
      resumeWhenReady = shouldPlay
      preparePlayer()
      if (backgroundDurationMs >= MUSIC_FOREGROUND_URL_REFRESH_MS) {
        activeResolvedTrack = null
        lastFailureWasNetwork = true
        retryNow()
      } else {
        val resolved = activeResolvedTrack
        if (resolved == null) {
          retryNow()
        } else {
          prepareResolvedTrack(
            resolved = resolved,
            startPositionMs = recoveryPositionMs,
            shouldPlay = shouldPlay,
            transitionCover = false,
          )
        }
      }
    }
  }

  fun stopAndClose() {
    persistResumeSnapshot(force = true)
    pageActive = false
    resumeWhenReady = false
    positionJob?.cancel()
    positionJob = null
    foregroundRecoveryJob?.cancel()
    foregroundRecoveryJob = null
    stopPlayback(clearCurrent = true)
    preparedEntryAccountMid = Long.MIN_VALUE
    preparedEntryFolderSelectionId = Long.MIN_VALUE
    preparedEntryFolderSelectionConfigured = false
    app.playbackSessionTarget = PlaybackSessionTarget.DETAIL
    PlaybackSessionService.stop(getApplication())
  }

  internal fun stopForTaskRemoval() {
    persistResumeSnapshot(force = true)
    pageActive = false
    resumeWhenReady = false
    positionJob?.cancel()
    positionJob = null
    foregroundRecoveryJob?.cancel()
    foregroundRecoveryJob = null
    stopPlayback(clearCurrent = true)
    app.playbackSessionTarget = PlaybackSessionTarget.DETAIL
  }

  fun retryLibrary() {
    if (_state.value.accountMid > 0L) loadLibrary(resetQuery = false)
  }

  fun createMusicFolder(onCreated: (Long) -> Unit) {
    val current = _state.value
    if (current.accountMid <= 0L || current.creatingFolder) return
    libraryJob?.cancel()
    val generation = ++libraryGeneration
    _state.value = current.copy(creatingFolder = true, libraryError = null)
    libraryJob = viewModelScope.launch {
      val result = runCatching {
        withContext(Dispatchers.IO) {
          BiliFavoriteApi.createFavoriteFolder(MusicFavoriteFolderTitle, isPublic = false)
        }
      }
      if (generation != libraryGeneration) return@launch
      result
        .onSuccess { folderId ->
          _state.value = _state.value.copy(creatingFolder = false)
          onCreated(folderId)
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          _state.value =
            _state.value.copy(
              creatingFolder = false,
              libraryStatus = MusicLibraryStatus.MISSING,
              libraryError = error.message ?: "收藏夹创建失败",
            )
        }
    }
  }

  fun updateQuery(value: String) {
    if (value == _state.value.query) return
    _state.value = _state.value.copy(query = value)
    queryJob?.cancel()
    queryJob = viewModelScope.launch {
      delay(320)
      loadFirstPage()
    }
  }

  /** 不做防抖地清除搜索，让 UI 能翻页到当前正在播放的曲目。 */
  fun clearQueryForCurrentTrack() {
    if (_state.value.query.isBlank()) return
    queryJob?.cancel()
    _state.value = _state.value.copy(query = "")
    loadFirstPage()
  }

  fun loadMore() {
    val current = _state.value
    val folderId = current.folder?.id ?: return
    if (
      current.libraryStatus != MusicLibraryStatus.READY || current.loadingMore || !current.hasMore
    )
      return
    val page = current.page + 1
    val generation = libraryGeneration
    _state.value = current.copy(loadingMore = true, libraryError = null)
    viewModelScope.launch {
      val result = runCatching {
        withContext(Dispatchers.IO) {
          BiliFavoriteApi.getFavoriteVideos(folderId, page, _state.value.query.trim())
        }
      }
      if (generation != libraryGeneration || _state.value.folder?.id != folderId) return@launch
      result
        .onSuccess { response ->
          _state.value =
            _state.value.copy(
              items =
                (_state.value.items + response.cards.map(::musicFeedItem)).distinctBy { it.id },
              page = page,
              hasMore = response.hasMore,
              loadingMore = false,
            )
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          _state.value =
            _state.value.copy(
              loadingMore = false,
              libraryError = error.message ?: "收藏夹续页失败",
            )
        }
    }
  }

  fun selectItem(item: FeedItem) {
    if (_state.value.currentItem?.id == item.id) {
      if (_state.value.playbackError != null) retryNow()
      else if (!player.orIdle().isPlaying) togglePlayback()
      return
    }
    loadTrack(item)
  }

  fun setDisplayName(item: FeedItem, alias: String) {
    val accountMid = _state.value.accountMid
    if (accountMid <= 0L) return
    displayNameStore.set(accountMid, item.id, alias)
    val overrides = _state.value.displayNameOverrides.toMutableMap()
    val normalized = alias.trim()
    if (normalized.isBlank()) overrides.remove(item.id) else overrides[item.id] = normalized
    _state.value = _state.value.copy(displayNameOverrides = overrides)
  }

  fun removeFromMusicFolder(item: FeedItem) {
    val current = _state.value
    val folderId = current.folder?.id ?: return
    val resourceId = item.favoriteResourceId
    val resourceType = item.favoriteResourceType
    if (resourceId <= 0L || resourceType <= 0 || item.id in current.deletingItemIds) return
    _state.value =
      current.copy(
        deletingItemIds = current.deletingItemIds + item.id,
        libraryError = null,
      )
    viewModelScope.launch {
      val result = runCatching {
        withContext(Dispatchers.IO) {
          BiliFavoriteApi.removeFavoriteResource(resourceId, resourceType, folderId)
        }
      }
      result
        .onSuccess {
          val latest = _state.value
          _state.value =
            latest.copy(
              items = latest.items.filterNot { it.id == item.id },
              deletingItemIds = latest.deletingItemIds - item.id,
            )
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          val latest = _state.value
          _state.value =
            latest.copy(
              deletingItemIds = latest.deletingItemIds - item.id,
              libraryError = error.message ?: "从“音乐”收藏夹删除失败",
            )
        }
    }
  }

  fun togglePlayback() {
    val current = _state.value.currentItem
    if (current == null) {
      _state.value.items.firstOrNull()?.let(::selectItem)
      return
    }
    val playback = preparePlayer()
    if (playback.currentMediaItem?.mediaId != current.id) {
      loadTrack(current)
    } else if (playback.isPlaying || playback.playWhenReady) {
      resumeWhenReady = false
      playback.pause()
    } else {
      resumeWhenReady = true
      playback.play()
    }
  }

  fun togglePlaybackOrder() {
    val next = nextMusicPlaybackOrder(_state.value.playbackOrder)
    _state.value = _state.value.copy(playbackOrder = next)
    player?.repeatMode = musicRepeatMode(next)
    videoPlayer?.repeatMode = musicRepeatMode(next)
  }

  fun playNext() = playAdjacent(direction = 1)

  fun playPrevious() = playAdjacent(direction = -1)

  private fun playAdjacent(direction: Int) {
    val items = _state.value.items
    if (items.isEmpty()) return
    val currentIndex = items.indexOfFirst { it.id == _state.value.currentItem?.id }
    val target =
      adjacentMusicIndex(
        itemCount = items.size,
        currentIndex = currentIndex,
        order = _state.value.playbackOrder,
        direction = direction,
      )
    loadTrack(items[target])
  }

  fun selectPremiumAudio(mode: PremiumAudioMode?) {
    val current = _state.value
    if (mode == PremiumAudioMode.DOLBY && !current.dolbyAvailable) return
    if (mode == PremiumAudioMode.HI_RES && !current.hiResAvailable) return
    if (mode == current.selectedPremiumAudio) return
    val resolved = activeResolvedTrack ?: return
    val playback = preparePlayer()
    val position = playback.currentPosition.coerceAtLeast(lastStablePositionMs)
    val shouldPlay = playback.playWhenReady || resumeWhenReady
    _state.value =
      current.copy(selectedPremiumAudio = mode, playbackLoading = true, playbackError = null)
    prepareResolvedTrack(
      resolved = resolved,
      startPositionMs = position,
      shouldPlay = shouldPlay,
      transitionCover = false,
    )
  }

  fun seekTo(positionMs: Long) {
    resetSpectrumTimeline()
    val duration = _state.value.durationMs.coerceAtLeast(0L)
    val target = positionMs.coerceIn(0L, duration)
    lastStablePositionMs = target
    val playback = player
    val shouldPlay = playback?.playWhenReady == true || resumeWhenReady
    _state.value =
      _state.value.copy(
        positionMs = target,
        playbackLoading = activeResolvedTrack != null,
        playbackError = null,
      )
    val resolved = activeResolvedTrack
    if (resolved == null) {
      playback?.seekTo(target)
      return
    }
    // 在受影响的小米 MediaCodec 构建上，seek 一条较长的 SegmentBase 条目会冲洗两个
    // 渲染器却从不重建被销毁的 AudioTrack。之后 ExoPlayer 在音视频冻结的情况下继续
    // 推进自己的独立时钟。在请求的位置重新打开同一个已解析的源，能给两个渲染器
    // 一条干净的时间线并可靠地重建 AudioTrack。
    prepareResolvedTrack(
      resolved = resolved,
      startPositionMs = target,
      shouldPlay = shouldPlay,
      transitionCover = false,
    )
  }

  fun setSystemVolume(value: Float) {
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val target = (value.coerceIn(0f, 1f) * maxVolume).roundToInt().coerceIn(0, maxVolume)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    syncSystemVolume()
  }

  fun toggleMute() {
    val current = _state.value
    if (current.muted) {
      setSystemVolume(current.lastAudibleVolume.coerceAtLeast(1f / 15f))
    } else {
      setSystemVolume(0f)
    }
  }

  private fun syncSystemVolume() {
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val currentVolume =
      audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVolume)
    val volume = currentVolume.toFloat() / maxVolume
    val muted = currentVolume == 0 || audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
    val previous = _state.value
    val lastAudible = if (!muted) volume else previous.lastAudibleVolume
    if (
      previous.volume != volume ||
        previous.muted != muted ||
        previous.lastAudibleVolume != lastAudible
    ) {
      _state.value = previous.copy(volume = volume, muted = muted, lastAudibleVolume = lastAudible)
    }
  }

  private fun loadLibrary(resetQuery: Boolean) {
    val accountMid = _state.value.accountMid
    val folderSelectionId = _state.value.folderSelectionId
    val folderSelectionConfigured = _state.value.folderSelectionConfigured
    if (accountMid <= 0L) return
    libraryJob?.cancel()
    queryJob?.cancel()
    val generation = ++libraryGeneration
    _state.value =
      _state.value.copy(
        libraryStatus = MusicLibraryStatus.LOADING,
        query = if (resetQuery) "" else _state.value.query,
        creatingFolder = false,
        libraryError = null,
      )
    libraryJob = viewModelScope.launch {
      val result = runCatching {
        withContext(Dispatchers.IO) {
          val folders = BiliFavoriteApi.getFavoriteFolders(accountMid)
          val folder =
            resolveMusicFavoriteFolder(
              folders = folders,
              preferredFolderId = folderSelectionId,
              folderSelectionConfigured = folderSelectionConfigured,
            )
          val response = folder?.let {
            BiliFavoriteApi.getFavoriteVideos(it.id, 1, _state.value.query.trim())
          }
          Triple(folders, folder, response)
        }
      }
      if (
        generation != libraryGeneration ||
          accountMid != _state.value.accountMid ||
          folderSelectionId != _state.value.folderSelectionId ||
          folderSelectionConfigured != _state.value.folderSelectionConfigured
      )
        return@launch
      result
        .onSuccess { (folders, folder, response) ->
          if (folder == null) {
            _state.value =
              _state.value.copy(
                libraryStatus = MusicLibraryStatus.MISSING,
                availableFolders = folders,
                folder = null,
                items = emptyList(),
                page = 0,
                hasMore = false,
              )
            return@onSuccess
          }
          val items = response?.cards.orEmpty().map(::musicFeedItem)
          _state.value =
            _state.value.copy(
              libraryStatus = MusicLibraryStatus.READY,
              availableFolders = folders,
              folder = folder,
              items = items,
              page = 1,
              hasMore = response?.hasMore == true,
              loadingMore = false,
            )
          if (_state.value.currentItem == null) restoreLastTrack(items)
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          _state.value =
            _state.value.copy(
              libraryStatus = MusicLibraryStatus.ERROR,
              loadingMore = false,
              libraryError = error.message ?: "音乐收藏夹加载失败",
            )
        }
    }
  }

  private fun loadFirstPage() {
    val current = _state.value
    val folderId = current.folder?.id ?: return
    libraryJob?.cancel()
    val generation = ++libraryGeneration
    _state.value =
      current.copy(
        libraryStatus = MusicLibraryStatus.LOADING,
        items = emptyList(),
        page = 0,
        hasMore = false,
        libraryError = null,
      )
    libraryJob = viewModelScope.launch {
      val result = runCatching {
        withContext(Dispatchers.IO) {
          BiliFavoriteApi.getFavoriteVideos(folderId, 1, _state.value.query.trim())
        }
      }
      if (generation != libraryGeneration || _state.value.folder?.id != folderId) return@launch
      result
        .onSuccess { response ->
          _state.value =
            _state.value.copy(
              libraryStatus = MusicLibraryStatus.READY,
              items = response.cards.map(::musicFeedItem),
              page = 1,
              hasMore = response.hasMore,
            )
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          _state.value =
            _state.value.copy(
              libraryStatus = MusicLibraryStatus.ERROR,
              libraryError = error.message ?: "音乐搜索失败",
            )
        }
    }
  }

  private fun loadTrack(item: FeedItem, startPositionMs: Long = 0L) {
    resetSpectrumTimeline()
    retryJob?.cancel()
    retryJob = null
    retryAttempt = 0
    lastStablePositionMs = startPositionMs.coerceAtLeast(0L)
    retryPreparedPositionMs = lastStablePositionMs
    lastPersistedPositionMs = lastStablePositionMs - RESUME_WRITE_INTERVAL_MS
    lastFailureWasNetwork = false
    resumeWhenReady = true
    val previous = _state.value.currentItem
    val playback = preparePlayer()
    playback.pause()
    videoRetryJob?.cancel()
    videoRetryJob = null
    videoPlayer?.stop()
    videoPlayer?.clearMediaItems()
    pendingVideoSource = null
    activeVideoItemId = null
    _state.value =
      _state.value.copy(
        currentItem = item,
        positionMs = lastStablePositionMs,
        durationMs = 0L,
        playbackLoading = true,
        firstFrameReady = false,
        playbackError = null,
        isPlaying = false,
        dolbyAvailable = false,
        hiResAvailable = false,
        selectedPremiumAudio = null,
      )
    persistResumeSnapshot(force = true)
    val generation = ++mediaGeneration
    mediaJob?.cancel()
    mediaJob = viewModelScope.launch {
      if (previous != null && previous.id != item.id) delay(COVER_HANDOFF_DELAY_MS)
      if (generation != mediaGeneration || _state.value.currentItem?.id != item.id) return@launch
      playback.stop()
      playback.clearMediaItems()
      val result = runCatching {
        val resolved = withContext(Dispatchers.IO) { resolveTrack(item) }
        val mode = preferredPremiumMode(resolved.data)
        withContext(Dispatchers.IO) { buildPreparedTrack(resolved, mode, generation) }
      }
      if (generation != mediaGeneration || _state.value.currentItem?.id != item.id) return@launch
      result
        .onSuccess { prepared ->
          val duration = prepared.resolved.data.durationMs.coerceAtLeast(0L)
          val resumePosition =
            if (duration > 0L) startPositionMs.coerceIn(0L, (duration - 1_000L).coerceAtLeast(0L))
            else startPositionMs.coerceAtLeast(0L)
          applyPreparedTrack(
            prepared,
            startPositionMs = resumePosition,
            shouldPlay = true,
            transitionCover = true,
          )
        }
        .onFailure(::handlePreparationFailure)
    }
  }

  private fun prepareResolvedTrack(
    resolved: ResolvedTrack,
    startPositionMs: Long,
    shouldPlay: Boolean,
    transitionCover: Boolean,
  ) {
    val item = _state.value.currentItem ?: return
    val generation = ++mediaGeneration
    mediaJob?.cancel()
    mediaJob = viewModelScope.launch {
      val mode = _state.value.selectedPremiumAudio
      val result = runCatching {
        withContext(Dispatchers.IO) { buildPreparedTrack(resolved, mode, generation) }
      }
      if (generation != mediaGeneration || _state.value.currentItem?.id != item.id) return@launch
      result
        .onSuccess { applyPreparedTrack(it, startPositionMs, shouldPlay, transitionCover) }
        .onFailure(::handlePreparationFailure)
    }
  }

  private fun applyPreparedTrack(
    prepared: PreparedTrack,
    startPositionMs: Long,
    shouldPlay: Boolean,
    transitionCover: Boolean,
  ) {
    resetSpectrumTimeline()
    activeResolvedTrack = prepared.resolved
    videoRetryJob?.cancel()
    videoRetryJob = null
    pendingVideoSource = prepared.videoSource
    activeVideoItemId = null
    videoPlayer?.stop()
    videoPlayer?.clearMediaItems()
    val playback = preparePlayer()
    val dolby = supportsDolby(prepared.resolved.data)
    val hiRes = supportsHiRes(prepared.resolved.data)
    val selected =
      prepared.mode.takeIf {
        it == PremiumAudioMode.DOLBY && dolby || it == PremiumAudioMode.HI_RES && hiRes
      }
    _state.value =
      _state.value.copy(
        dolbyAvailable = dolby,
        hiResAvailable = hiRes,
        selectedPremiumAudio = selected,
        playbackLoading = true,
        playbackError = null,
        firstFrameReady = if (transitionCover) false else _state.value.firstFrameReady,
      )
    playback.repeatMode = musicRepeatMode(_state.value.playbackOrder)
    playback.setMediaSource(prepared.audioSource, startPositionMs.coerceAtLeast(0L))
    retryPreparedPositionMs = startPositionMs.coerceAtLeast(0L)
    playback.prepare()
    resumeWhenReady = shouldPlay
    playback.playWhenReady = shouldPlay && pageActive
    applyForegroundVideoState()
    app.playbackSessionTarget = PlaybackSessionTarget.MUSIC
    PlaybackSessionService.publishMusicPlayer(getApplication())
  }

  private fun handlePlayerError(error: PlaybackException) {
    Log.e(TAG, "music playback failed: ${error.errorCodeName}", error)
    val playback = player
    videoPlayer?.pause()
    lastStablePositionMs =
      maxOf(lastStablePositionMs, playback?.currentPosition?.coerceAtLeast(0L) ?: 0L)
    _state.value =
      _state.value.copy(
        playbackLoading = true,
        playbackError = playbackFailureMessage(error),
        isPlaying = false,
      )
    lastFailureWasNetwork = isNetworkFailure(error)
    if (isAudioCompatibilityFailure(error)) {
      if (!preferSoftwareAudioDecoder) {
        switchToCompatibleAudioDecoder()
        return
      }
      if (_state.value.selectedPremiumAudio != null) {
        fallBackToStandardAudio()
        return
      }
    }
    scheduleRetry()
  }

  private fun handlePreparationFailure(error: Throwable) {
    if (error is CancellationException) throw error
    Log.e(TAG, "music preparation failed", error)
    lastFailureWasNetwork = true
    _state.value =
      _state.value.copy(
        playbackLoading = true,
        playbackError = "播放地址暂时不可用，正在自动重试",
        isPlaying = false,
      )
    scheduleRetry()
  }

  private fun scheduleRetry() {
    if (!pageActive || retryJob?.isActive == true || _state.value.currentItem == null) return
    val attempt = retryAttempt++
    retryJob = viewModelScope.launch {
      delay(musicRetryDelayMillis(attempt))
      if (!pageActive) return@launch
      retryJob = null
      retryNow()
    }
  }

  private fun scheduleVideoRetry() {
    if (!pageActive || !appInForeground || videoRetryJob?.isActive == true) return
    val item = _state.value.currentItem ?: return
    val resolved = activeResolvedTrack ?: return
    val generation = mediaGeneration
    videoRetryJob = viewModelScope.launch {
      delay(MUSIC_VIDEO_RETRY_DELAY_MS)
      if (
        !pageActive ||
          !appInForeground ||
          generation != mediaGeneration ||
          _state.value.currentItem?.id != item.id
      ) {
        videoRetryJob = null
        return@launch
      }
      val playbackData = playbackDataForMode(resolved.data, _state.value.selectedPremiumAudio)
      val result =
        runCatching {
          withContext(Dispatchers.IO) { buildVideoMediaSource(resolved, playbackData, generation) }
        }
      videoRetryJob = null
      if (generation != mediaGeneration || _state.value.currentItem?.id != item.id) return@launch
      result
        .onSuccess { source ->
          pendingVideoSource = source
          videoPlayer?.stop()
          videoPlayer?.clearMediaItems()
          syncVideoPlaybackToAudio(forceSeek = true)
        }
        .onFailure {
          Log.w(TAG, "music video retry failed; audio remains playable", it)
          scheduleVideoRetry()
        }
    }
  }

  private fun retryNow() {
    val item = _state.value.currentItem ?: return
    retryJob?.cancel()
    retryJob = null
    val shouldRefreshAddress =
      activeResolvedTrack == null || (lastFailureWasNetwork && retryAttempt % 4 == 0)
    if (shouldRefreshAddress) {
      val generation = ++mediaGeneration
      mediaJob?.cancel()
      mediaJob = viewModelScope.launch {
        val result = runCatching {
          val resolved = withContext(Dispatchers.IO) { resolveTrack(item) }
          val mode =
            _state.value.selectedPremiumAudio.takeIf { requested ->
              requested == PremiumAudioMode.DOLBY && supportsDolby(resolved.data) ||
                requested == PremiumAudioMode.HI_RES && supportsHiRes(resolved.data)
            } ?: preferredPremiumMode(resolved.data)
          withContext(Dispatchers.IO) { buildPreparedTrack(resolved, mode, generation) }
        }
        if (generation != mediaGeneration || _state.value.currentItem?.id != item.id) return@launch
        result
          .onSuccess {
            applyPreparedTrack(
              it,
              startPositionMs = lastStablePositionMs,
              shouldPlay = resumeWhenReady,
              transitionCover = false,
            )
          }
          .onFailure(::handlePreparationFailure)
      }
      return
    }
    val rotated =
      activeResolvedTrack?.let { resolved ->
        if (lastFailureWasNetwork) rotateResolvedTrack(resolved) else resolved
      } ?: return
    activeResolvedTrack = rotated
    prepareResolvedTrack(
      resolved = rotated,
      startPositionMs = lastStablePositionMs,
      shouldPlay = resumeWhenReady,
      transitionCover = false,
    )
  }

  private fun startPositionUpdates() {
    if (positionJob?.isActive == true) return
    positionJob = viewModelScope.launch {
      while (isActive && pageActive) {
        val playback = player
        if (playback != null) {
          val position = playback.currentPosition.coerceAtLeast(0L)
          if (position > 0L) lastStablePositionMs = position
          if (
            playback.isPlaying && position - retryPreparedPositionMs >= STABLE_PLAYBACK_RESET_MS
          ) {
            retryAttempt = 0
            lastFailureWasNetwork = false
          }
          _state.value =
            _state.value.copy(
              positionMs = position,
              durationMs = playback.duration.coerceAtLeast(0L),
            )
          syncVideoPlaybackToAudio()
        }
        if (lastStablePositionMs - lastPersistedPositionMs >= RESUME_WRITE_INTERVAL_MS) {
          persistResumeSnapshot(force = false)
        }
        syncSystemVolume()
        delay(250)
      }
    }
  }

  private fun resolveTrack(item: FeedItem): ResolvedTrack {
    val resolvedId = BiliBangumiApi.resolveVideoBvid(item.videoUrl)
    val info =
      if (resolvedId.startsWith("av", ignoreCase = true)) {
        BiliVideoApi.getVideoInfoByAid(resolvedId.drop(2).toLongOrNull() ?: 0L)
      } else {
        BiliVideoApi.getVideoInfo(resolvedId)
      } ?: error("获取视频信息失败")
    val data = BiliVideoApi.getPlayUrl(info.bvid, info.cid) ?: error("获取播放地址失败")
    val prioritized = prioritizeMusicRoutes(data.copy(durationMs = info.durationSeconds * 1_000L))
    val streamIndex =
      selectMusicStreamIndex(
        streams = prioritized.streams,
        requestedMode = musicPreferredResolutionMode,
        vipActive = accountVipActive,
      )
    val fixedStream = prioritized.streams.getOrNull(streamIndex) ?: error("没有可播放的视频流")
    return ResolvedTrack(
      item = item,
      data = prioritized.copy(streams = listOf(fixedStream), currentStreamIndex = 0),
    )
  }

  private fun buildPreparedTrack(
    resolved: ResolvedTrack,
    mode: PremiumAudioMode?,
    generation: Long,
  ): PreparedTrack {
    val playbackData = playbackDataForMode(resolved.data, mode)
    val audioSource = buildAudioMediaSource(resolved, playbackData, generation)
    val videoSource =
      runCatching { buildVideoMediaSource(resolved, playbackData, generation) }
        .onFailure { Log.w(TAG, "music video source unavailable; audio remains playable", it) }
        .getOrNull()
    return PreparedTrack(resolved, mode, audioSource, videoSource)
  }

  private fun buildAudioMediaSource(
    resolved: ResolvedTrack,
    playbackData: PlayUrlData,
    generation: Long,
  ): MediaSource {
    return runCatching {
        val manifest = BiliDashManifest.buildAudioOnly(playbackData) ?: error("音频 DASH 清单创建失败")
        val directory = File(getApplication<Application>().cacheDir, "bili_dash_music")
        if (!directory.exists() && !directory.mkdirs()) error("无法创建音乐清单缓存")
        val file = File(directory, "music_audio_${generation}_${System.nanoTime()}.mpd")
        file.writeText(manifest, Charsets.UTF_8)
        activeAudioManifestFile?.takeIf { it != file }?.delete()
        activeAudioManifestFile = file
        val mediaItem =
          MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .setMediaId(resolved.item.id)
            .setMediaMetadata(musicMediaMetadata(resolved.item))
            .build()
        DashMediaSource.Factory(cachedDataSourceFactory).createMediaSource(mediaItem)
      }
      .getOrElse { manifestError ->
        Log.w(TAG, "music audio DASH unavailable; using compatibility source", manifestError)
        val url =
          playbackData.selectedAudioUrl()
            ?: playbackData.streams.firstOrNull()?.url
            ?: throw manifestError
        ProgressiveMediaSource.Factory(cachedDataSourceFactory)
          .createMediaSource(cachedMediaItem(url, resolved.item, includeMetadata = true))
      }
  }

  private fun buildVideoMediaSource(
    resolved: ResolvedTrack,
    playbackData: PlayUrlData,
    generation: Long,
  ): MediaSource {
    val videoOnlyData =
      playbackData.copy(
        dashAudioUrl = null,
        dashAudio = null,
        dolbyAudioUrl = null,
        dolbyAudio = null,
        hiResAudioUrl = null,
        hiResAudio = null,
        premiumAudioMode = null,
      )
    return runCatching {
        val manifest = BiliDashManifest.build(videoOnlyData) ?: error("视频 DASH 清单创建失败")
        val directory = File(getApplication<Application>().cacheDir, "bili_dash_music")
        if (!directory.exists() && !directory.mkdirs()) error("无法创建音乐清单缓存")
        val file = File(directory, "music_video_${generation}_${System.nanoTime()}.mpd")
        file.writeText(manifest, Charsets.UTF_8)
        activeVideoManifestFile?.takeIf { it != file }?.delete()
        activeVideoManifestFile = file
        val mediaItem =
          MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .setMediaId(resolved.item.id)
            .build()
        DashMediaSource.Factory(cachedDataSourceFactory).createMediaSource(mediaItem)
      }
      .getOrElse { manifestError ->
        Log.w(TAG, "music video DASH unavailable; using compatibility source", manifestError)
        val stream = playbackData.streams.firstOrNull() ?: throw manifestError
        ProgressiveMediaSource.Factory(cachedDataSourceFactory)
          .createMediaSource(cachedMediaItem(stream.url, resolved.item, includeMetadata = false))
      }
  }

  private fun cachedMediaItem(
    url: String,
    item: FeedItem,
    includeMetadata: Boolean,
  ): MediaItem {
    val uri = Uri.parse(url)
    val cacheKey = uri.encodedPath?.takeIf(String::isNotBlank)?.let { "bili:$it" }
    return MediaItem.Builder()
      .setUri(uri)
      .setMediaId(item.id)
      .setCustomCacheKey(cacheKey)
      .apply { if (includeMetadata) setMediaMetadata(musicMediaMetadata(item)) }
      .build()
  }

  private fun musicMediaMetadata(item: FeedItem): MediaMetadata =
    MediaMetadata.Builder()
      .setTitle(displayTitle(item, _state.value.displayNameOverrides))
      .setArtist(item.uploader?.takeIf(String::isNotBlank))
      .setArtworkUri(UrlPolicy.normalizeImageUrl(item.coverUrl)?.let(Uri::parse))
      .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
      .build()

  private fun playbackDataForMode(data: PlayUrlData, mode: PremiumAudioMode?): PlayUrlData {
    val audio =
      when (mode) {
        PremiumAudioMode.DOLBY -> data.dolbyAudio
        PremiumAudioMode.HI_RES -> data.hiResAudio
        null -> data.dashAudio
      } ?: data.dashAudio
    val audioUrl =
      when (mode) {
        PremiumAudioMode.DOLBY -> data.dolbyAudioUrl
        PremiumAudioMode.HI_RES -> data.hiResAudioUrl
        null -> data.dashAudioUrl
      } ?: data.dashAudioUrl
    return data.copy(
      streams = data.streams.take(1),
      currentStreamIndex = 0,
      dashAudio = audio,
      dashAudioUrl = audio?.url ?: audioUrl,
      dolbyAudio = null,
      dolbyAudioUrl = null,
      hiResAudio = null,
      hiResAudioUrl = null,
      premiumAudioMode = null,
    )
  }

  private fun preferredPremiumMode(data: PlayUrlData): PremiumAudioMode? {
    if (!advancedAudioEnabled) return null
    val ordered =
      when (advancedAudioPriority) {
        AdvancedAudioPriority.DOLBY -> listOf(PremiumAudioMode.DOLBY, PremiumAudioMode.HI_RES)
        AdvancedAudioPriority.HI_RES -> listOf(PremiumAudioMode.HI_RES, PremiumAudioMode.DOLBY)
      }
    return ordered.firstOrNull { mode ->
      mode == PremiumAudioMode.DOLBY && supportsDolby(data) ||
        mode == PremiumAudioMode.HI_RES && supportsHiRes(data)
    }
  }

  private fun supportsDolby(data: PlayUrlData): Boolean =
    data.supportsPremiumAudio(PremiumAudioMode.DOLBY) &&
      (mediaCapabilities.supportsDolbyAtmos || unlockDolbyAtmos)

  private fun supportsHiRes(data: PlayUrlData): Boolean =
    data.supportsPremiumAudio(PremiumAudioMode.HI_RES) &&
      (mediaCapabilities.supportsHiRes || unlockHiRes)

  private fun prioritizeMusicRoutes(data: PlayUrlData): PlayUrlData {
    fun video(stream: VideoStream): VideoStream {
      val ordered =
        prioritizeCdnUrls(
          stream.url,
          stream.backupUrls,
          configuredCdnHost(getApplication()),
        )
      return stream.copy(url = ordered.primary, backupUrls = ordered.backups)
    }

    fun audio(stream: AudioStream?): AudioStream? {
      stream ?: return null
      val ordered =
        prioritizeCdnUrls(
          stream.url,
          stream.backupUrls,
          configuredCdnHost(getApplication()),
        )
      return stream.copy(url = ordered.primary, backupUrls = ordered.backups)
    }

    val regular = audio(data.dashAudio)
    val dolby = audio(data.dolbyAudio)
    val hiRes = audio(data.hiResAudio)
    return data.copy(
      streams = data.streams.map(::video),
      dashAudio = regular,
      dashAudioUrl = regular?.url ?: data.dashAudioUrl,
      dolbyAudio = dolby,
      dolbyAudioUrl = dolby?.url ?: data.dolbyAudioUrl,
      hiResAudio = hiRes,
      hiResAudioUrl = hiRes?.url ?: data.hiResAudioUrl,
    )
  }

  private fun rotateResolvedTrack(resolved: ResolvedTrack): ResolvedTrack {
    fun video(stream: VideoStream): VideoStream {
      val urls = (listOf(stream.url) + stream.backupUrls).filter(String::isNotBlank).distinct()
      if (urls.size < 2) return stream
      return stream.copy(url = urls[1], backupUrls = urls.drop(2) + urls.first())
    }

    fun audio(stream: AudioStream?): AudioStream? {
      stream ?: return null
      val urls = (listOf(stream.url) + stream.backupUrls).filter(String::isNotBlank).distinct()
      if (urls.size < 2) return stream
      return stream.copy(url = urls[1], backupUrls = urls.drop(2) + urls.first())
    }

    val data = resolved.data
    val regular = audio(data.dashAudio)
    val dolby = audio(data.dolbyAudio)
    val hiRes = audio(data.hiResAudio)
    return resolved.copy(
      data =
        data.copy(
          streams = data.streams.map(::video),
          dashAudio = regular,
          dashAudioUrl = regular?.url ?: data.dashAudioUrl,
          dolbyAudio = dolby,
          dolbyAudioUrl = dolby?.url ?: data.dolbyAudioUrl,
          hiResAudio = hiRes,
          hiResAudioUrl = hiRes?.url ?: data.hiResAudioUrl,
        )
    )
  }

  private fun playbackFailureMessage(error: PlaybackException): String =
    when {
      error.errorCodeName.contains("NETWORK", ignoreCase = true) -> "网络连接中断，正在自动重试"
      error.errorCodeName.contains("DECOD", ignoreCase = true) -> "当前音频解码器不兼容，正在切换兼容模式"
      error.errorCodeName.contains("RUNTIME_CHECK", ignoreCase = true) -> "音频输出格式异常，正在切换兼容模式"
      error.errorCodeName.contains("PARSING", ignoreCase = true) -> "播放清单解析失败，正在刷新地址"
      else -> "播放中断（${error.errorCodeName}），正在自动重试"
    }

  private fun stopPlayback(clearCurrent: Boolean) {
    resetSpectrumTimeline()
    mediaGeneration++
    mediaJob?.cancel()
    mediaJob = null
    retryJob?.cancel()
    retryJob = null
    videoRetryJob?.cancel()
    videoRetryJob = null
    foregroundRecoveryJob?.cancel()
    player?.stop()
    player?.clearMediaItems()
    videoPlayer?.stop()
    videoPlayer?.clearMediaItems()
    activeAudioManifestFile?.delete()
    activeAudioManifestFile = null
    activeVideoManifestFile?.delete()
    activeVideoManifestFile = null
    activeResolvedTrack = null
    pendingVideoSource = null
    activeVideoItemId = null
    retryAttempt = 0
    lastStablePositionMs = 0L
    if (clearCurrent) {
      _state.value =
        _state.value.copy(
          currentItem = null,
          playbackLoading = false,
          firstFrameReady = false,
          playbackError = null,
          isPlaying = false,
          positionMs = 0L,
          durationMs = 0L,
          dolbyAvailable = false,
          hiResAvailable = false,
          selectedPremiumAudio = null,
        )
    }
  }

  private fun submitSpectrumSamples(
    samples: FloatArray,
    sampleRate: Int,
    presentationTimeUs: Long,
    audioSinkPositionUs: Long,
  ) {
    if (!pageActive) return
    val volumeState = _state.value
    val systemVolume = if (volumeState.muted) 0f else volumeState.volume
    spectrumFrames.tryEmit(
      SpectrumFrame(
        samples = samples,
        sampleRate = sampleRate,
        systemVolume = systemVolume,
        presentationTimeUs = presentationTimeUs,
        audioSinkPositionUs = audioSinkPositionUs,
        capturedAtNanos = System.nanoTime(),
        epoch = spectrumEpoch,
      )
    )
  }

  /**
   * 让完全准备好的条目在音乐页移动期间保持暂停。页面在其滑动开始前立刻调用它，
   * 并且只在滑动加上其启动延迟之后才激活 [open]。
   */
  fun holdPreparedEntryForTransition() {
    pageActive = false
    positionJob?.cancel()
    positionJob = null
    foregroundRecoveryJob?.cancel()
    foregroundRecoveryJob = null
    player?.pause()
    videoPlayer?.pause()
  }

  /**
   * 音乐页滑走时暂停频谱管线，但不停止音频。页面在开始退出滑动时调用它，让可视化器
   * 在退出动画期间停止发布帧（以及停止失效页面图层）；[stopAndClose] 仍会在最后运行
   * 以完全释放播放。
   */
  fun holdPreparedExitForTransition() {
    pageActive = false
    positionJob?.cancel()
    positionJob = null
    foregroundRecoveryJob?.cancel()
    foregroundRecoveryJob = null
    resetSpectrumTimeline()
    videoPlayer?.pause()
  }

  private fun adaptOutputLatency(presentationTimeUs: Long, audioSinkPositionUs: Long) {
    if (presentationTimeUs < 0L || audioSinkPositionUs == C.TIME_UNSET || audioSinkPositionUs < 0L)
      return
    val observedLeadMs = (presentationTimeUs - audioSinkPositionUs) / 1_000L
    if (observedLeadMs <= 0L) return
    measuredOutputLatencyMs =
      advanceOutputLatencyEstimate(
        estimateMs = measuredOutputLatencyMs,
        observedLeadMs = observedLeadMs,
        minMs = MUSIC_SPECTRUM_MIN_OUTPUT_LATENCY_MS,
        maxMs = 1_000L,
      )
  }

  private fun resetSpectrumTimeline() {
    spectrumEpoch += 1L
    spectrumTargets.fill(0f)
    beatTracker.reset()
  }

  override fun onCleared() {
    persistResumeSnapshot(force = true)
    libraryJob?.cancel()
    queryJob?.cancel()
    positionJob?.cancel()
    spectrumJob.cancel()
    stopPlayback(clearCurrent = true)
    player?.release()
    player = null
    videoPlayer?.release()
    videoPlayer = null
  }

  private fun ExoPlayer?.orIdle(): ExoPlayer = this ?: preparePlayer()

  private data class ResolvedTrack(val item: FeedItem, val data: PlayUrlData)

  private data class SpectrumFrame(
    val samples: FloatArray,
    val sampleRate: Int,
    val systemVolume: Float,
    val presentationTimeUs: Long,
    val audioSinkPositionUs: Long,
    val capturedAtNanos: Long,
    val epoch: Long,
  )

  private fun switchToCompatibleAudioDecoder() {
    val resolved =
      activeResolvedTrack
        ?: run {
          scheduleRetry()
          return
        }
    Log.w(TAG, "rebuilding music player with software audio decoder at $lastStablePositionMs ms")
    preferSoftwareAudioDecoder = true
    retryAttempt = retryAttempt.coerceAtLeast(1)
    val shouldPlay = resumeWhenReady
    val oldPlayer = player
    player = null
    oldPlayer?.release()
    videoPlayer?.pause()
    preparePlayer()
    _state.value =
      _state.value.copy(
        playbackLoading = true,
        playbackError = "正在切换兼容音频解码器",
        isPlaying = false,
      )
    prepareResolvedTrack(
      resolved = resolved,
      startPositionMs = lastStablePositionMs,
      shouldPlay = shouldPlay,
      transitionCover = false,
    )
  }

  private fun fallBackToStandardAudio() {
    val resolved =
      activeResolvedTrack
        ?: run {
          scheduleRetry()
          return
        }
    Log.w(
      TAG,
      "premium audio decoder failed; falling back to standard audio at $lastStablePositionMs ms",
    )
    _state.value =
      _state.value.copy(
        selectedPremiumAudio = null,
        playbackLoading = true,
        playbackError = "高级音轨解码失败，正在切换标准音质",
        isPlaying = false,
      )
    prepareResolvedTrack(
      resolved = resolved,
      startPositionMs = lastStablePositionMs,
      shouldPlay = resumeWhenReady,
      transitionCover = false,
    )
  }

  private fun isNetworkFailure(error: PlaybackException): Boolean =
    error.errorCodeName.contains("NETWORK", ignoreCase = true) ||
      error.errorCodeName.contains("IO_", ignoreCase = true)

  private fun isAudioCompatibilityFailure(error: PlaybackException): Boolean =
    error.errorCodeName.contains("RUNTIME_CHECK", ignoreCase = true) ||
      error.errorCodeName.contains("DECOD", ignoreCase = true) ||
      generateSequence<Throwable>(error) { it.cause }
        .any { cause ->
          cause.javaClass.simpleName.contains("InsufficientCapacity", ignoreCase = true) ||
            (cause is IllegalArgumentException &&
              cause.stackTrace.any { frame ->
                frame.className.contains("DefaultAudioSink") ||
                  frame.className.contains("MediaCodecAudioRenderer")
              })
        }

  private fun restoreLastTrack(items: List<FeedItem>) {
    if (_state.value.currentItem != null) return
    val folderSelectionId = _state.value.folderSelectionId
    val snapshot =
      readResumeSnapshot(_state.value.accountMid)?.takeIf {
        it.folderSelectionId == folderSelectionId
      }
    val item =
      snapshot?.let { saved -> items.firstOrNull { it.id == saved.item.id } }
        ?: items.firstOrNull()
        ?: return
    loadTrack(
      item,
      startPositionMs = snapshot?.positionMs?.takeIf { snapshot.item.id == item.id } ?: 0L,
    )
  }

  private fun persistResumeSnapshot(force: Boolean) {
    val current = _state.value
    val item = current.currentItem ?: return
    val accountMid = current.accountMid
    if (accountMid <= 0L) return
    val position = maxOf(lastStablePositionMs, player?.currentPosition?.coerceAtLeast(0L) ?: 0L)
    if (!force && position - lastPersistedPositionMs < RESUME_WRITE_INTERVAL_MS) return
    lastPersistedPositionMs = position
    val key = "music_$accountMid"
    resumePrefs
      .edit()
      .putString("${key}_id", item.id)
      .putString("${key}_title", item.title)
      .putString("${key}_video_url", item.videoUrl)
      .putString("${key}_cover", item.coverUrl)
      .putString("${key}_uploader", item.uploader.orEmpty())
      .putString("${key}_duration", item.duration.orEmpty())
      .putLong("${key}_uploader_mid", item.uploaderMid)
      .putString("${key}_uploader_face", item.uploaderFace.orEmpty())
      .putLong("${key}_resource_id", item.favoriteResourceId)
      .putInt("${key}_resource_type", item.favoriteResourceType)
      .putLong("${key}_folder_selection_id", current.folderSelectionId)
      .putLong("${key}_position", position)
      .apply()
  }

  private fun readResumeSnapshot(accountMid: Long): MusicResumeSnapshot? {
    if (accountMid <= 0L) return null
    val key = "music_$accountMid"
    val id = resumePrefs.getString("${key}_id", "").orEmpty()
    val videoUrl = resumePrefs.getString("${key}_video_url", "").orEmpty()
    if (id.isBlank() || videoUrl.isBlank()) return null
    return MusicResumeSnapshot(
      item =
        FeedItem(
          id = id,
          title = resumePrefs.getString("${key}_title", "").orEmpty(),
          videoUrl = videoUrl,
          coverUrl = resumePrefs.getString("${key}_cover", "").orEmpty(),
          uploader = resumePrefs.getString("${key}_uploader", "").orEmpty(),
          playCount = null,
          duration = resumePrefs.getString("${key}_duration", "").orEmpty(),
          uploaderFace = resumePrefs.getString("${key}_uploader_face", "").orEmpty(),
          uploaderMid = resumePrefs.getLong("${key}_uploader_mid", 0L),
          favoriteResourceId = resumePrefs.getLong("${key}_resource_id", 0L),
          favoriteResourceType = resumePrefs.getInt("${key}_resource_type", 0),
        ),
      folderSelectionId = resumePrefs.getLong("${key}_folder_selection_id", 0L),
      positionMs = resumePrefs.getLong("${key}_position", 0L).coerceAtLeast(0L),
    )
  }

  private data class MusicResumeSnapshot(
    val item: FeedItem,
    val folderSelectionId: Long,
    val positionMs: Long,
  )

  private data class PreparedTrack(
    val resolved: ResolvedTrack,
    val mode: PremiumAudioMode?,
    val audioSource: MediaSource,
    val videoSource: MediaSource?,
  )

  private companion object {
    const val TAG = "HomeMusicPlayer"
    const val COVER_HANDOFF_DELAY_MS = 70L
    const val STABLE_PLAYBACK_RESET_MS = 3_000L
    const val RESUME_WRITE_INTERVAL_MS = 3_000L
    const val MUSIC_FOREGROUND_RECOVERY_WATCHDOG_MS = 1_400L
    const val MUSIC_SPECTRUM_PRESENTATION_POLL_MS = 12L
    const val MUSIC_VIDEO_SYNC_TOLERANCE_MS = 650L
    const val MUSIC_VIDEO_RETRY_DELAY_MS = 2_000L
  }
}

internal const val MUSIC_FOREGROUND_RECOVERY_THRESHOLD_MS = 20_000L
internal const val MUSIC_FOREGROUND_URL_REFRESH_MS = 20 * 60_000L

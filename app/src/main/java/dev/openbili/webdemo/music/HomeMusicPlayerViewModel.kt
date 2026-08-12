package dev.openbili.webdemo.music

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
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.openbili.webdemo.BiliApplication
import dev.openbili.webdemo.BiliDashManifest
import dev.openbili.webdemo.HiResCompatibleRenderersFactory
import dev.openbili.webdemo.PlaybackCache
import dev.openbili.webdemo.PlaybackSessionService
import dev.openbili.webdemo.PlaybackSessionTarget
import dev.openbili.webdemo.UrlPolicy
import dev.openbili.webdemo.api.AudioStream
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.FeedCard
import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.api.PremiumAudioMode
import dev.openbili.webdemo.api.VideoStream
import dev.openbili.webdemo.effectiveStreamHeight
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.prioritizeCdnUrls
import dev.openbili.webdemo.settings.AdvancedAudioPriority
import dev.openbili.webdemo.settings.DeviceMediaCapabilities
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val MusicFavoriteFolderTitle = "音乐"

internal enum class MusicLibraryStatus {
  SIGNED_OUT,
  LOADING,
  MISSING,
  READY,
  ERROR,
}

internal enum class MusicPlaybackOrder {
  SEQUENTIAL,
  RANDOM,
}

internal data class HomeMusicUiState(
  val accountMid: Long = 0L,
  /** 0 means the default title-based “音乐” folder lookup. */
  val folderSelectionId: Long = 0L,
  val libraryStatus: MusicLibraryStatus = MusicLibraryStatus.SIGNED_OUT,
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
  val deletingItemIds: Set<String> = emptySet(),
  val spectrumBands: List<Float> = List(MUSIC_SPECTRUM_BAND_COUNT) { 0f },
)

internal const val MUSIC_SPECTRUM_BAND_COUNT = 28

internal data class MusicPlaybackProgressState(
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val enabled: Boolean = false,
)

private fun HomeMusicUiState.withoutRapidlyChangingFields(): HomeMusicUiState =
  copy(
    positionMs = 0L,
    durationMs = 0L,
    spectrumBands = emptyList(),
  )

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
    (
      backgroundDurationMs >= MUSIC_FOREGROUND_RECOVERY_THRESHOLD_MS &&
        (
          !firstFrameReady ||
            (shouldBePlaying && !isPlaying && playbackState != Player.STATE_BUFFERING)
          )
      )

internal fun findMusicFavoriteFolder(
  folders: List<FavoriteFolder>,
  preferredFolderId: Long = 0L,
): FavoriteFolder? =
  if (preferredFolderId > 0L) folders.firstOrNull { it.id == preferredFolderId }
  else folders.firstOrNull { it.title.trim() == MusicFavoriteFolderTitle }

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

/** Music is fixed to 720P; older uploads may fall back to their highest tier below 720P. */
internal fun selectMusic720StreamIndex(streams: List<VideoStream>): Int {
  if (streams.isEmpty()) return 0
  val exact720Index = streams.indexOfFirst { it.id == 64 }
  if (exact720Index >= 0) return exact720Index
  val notAbove720 = streams.indices.filter { effectiveStreamHeight(streams[it]) <= 720 }
  return notAbove720.maxByOrNull { effectiveStreamHeight(streams[it]) }
    ?: streams.indices.minByOrNull { effectiveStreamHeight(streams[it]) }
    ?: 0
}

internal fun adjacentMusicIndex(
  itemCount: Int,
  currentIndex: Int,
  order: MusicPlaybackOrder,
  direction: Int,
  randomValue: Int? = null,
): Int {
  if (itemCount <= 1) return 0
  val normalized = currentIndex.takeIf { it in 0 until itemCount } ?: 0
  if (order == MusicPlaybackOrder.SEQUENTIAL) {
    return (normalized + (if (direction < 0) -1 else 1) + itemCount) % itemCount
  }
  val value = (randomValue ?: Random.nextInt(itemCount - 1)).coerceIn(0, itemCount - 2)
  return if (value >= normalized) value + 1 else value
}

internal fun musicRetryDelayMillis(attempt: Int): Long =
  longArrayOf(0L, 800L, 1_600L, 3_200L, 6_400L, 12_000L, 15_000L)
    .getOrElse(attempt.coerceAtLeast(0)) { 15_000L }

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
  val spectrumState: StateFlow<List<Float>> =
    _state
      .map { current -> current.spectrumBands }
      .distinctUntilChanged()
      .stateIn(viewModelScope, SharingStarted.Eagerly, _state.value.spectrumBands)
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
  private val resumePrefs by lazy {
    getApplication<Application>().getSharedPreferences("home_music_resume", Context.MODE_PRIVATE)
  }
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
  private var positionJob: Job? = null
  private var spectrumJob: Job? = null
  private var foregroundRecoveryJob: Job? = null
  private var libraryGeneration = 0L
  private var mediaGeneration = 0L
  private var pageActive = false
  private var appInForeground = true
  private var backgroundedAtElapsedRealtime = 0L
  private var resumeWhenReady = true
  private var activeManifestFile: File? = null
  private var activeResolvedTrack: ResolvedTrack? = null
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
  private var preparedEntryAccountMid = Long.MIN_VALUE
  private var preparedEntryFolderSelectionId = Long.MIN_VALUE

  var player: ExoPlayer? = null
    private set

  fun preparePlayer(): ExoPlayer {
    player?.let { return it }
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
      ).apply {
        setEnableDecoderFallback(true)
        if (preferSoftwareAudioDecoder) forceDisableMediaCodecAsynchronousQueueing()
      }
    return ExoPlayer.Builder(getApplication(), renderersFactory)
      .setAudioAttributes(audioAttributes, true)
      .setHandleAudioBecomingNoisy(true)
      .build()
      .also { created ->
        created.repeatMode = Player.REPEAT_MODE_OFF
        created.volume = 1f
        created.addListener(
          object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
              resumeWhenReady = playWhenReady
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
                }
                Player.STATE_ENDED -> playNext()
              }
            }

            override fun onRenderedFirstFrame() {
              val renderedId = created.currentMediaItem?.mediaId
              if (renderedId != null && renderedId == _state.value.currentItem?.id) {
                _state.value = _state.value.copy(firstFrameReady = true, playbackLoading = false)
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
      val selected = _state.value.selectedPremiumAudio.takeIf { mode ->
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

  /**
   * Resolves the selected collection and prepares the remembered media source without activating
   * playback. HomeHub calls this while the recommendation page is still fully visible.
   */
  fun prepareOpen(accountMid: Long, folderSelectionId: Long) {
    val normalizedFolderId = folderSelectionId.coerceAtLeast(0L)
    if (
      preparedEntryAccountMid == accountMid &&
        preparedEntryFolderSelectionId == normalizedFolderId
    ) return
    preparedEntryAccountMid = accountMid
    preparedEntryFolderSelectionId = normalizedFolderId
    val targetChanged =
      accountMid != _state.value.accountMid ||
        normalizedFolderId != _state.value.folderSelectionId
    pageActive = false
    if (targetChanged) {
      persistResumeSnapshot(force = true)
      stopPlayback(clearCurrent = true)
      _state.value =
        HomeMusicUiState(
          accountMid = accountMid,
          folderSelectionId = normalizedFolderId,
          libraryStatus =
            if (accountMid > 0L) MusicLibraryStatus.LOADING else MusicLibraryStatus.SIGNED_OUT,
        )
    }
    preparePlayer()
    applyForegroundVideoState()
    syncSystemVolume()
    if (
      accountMid > 0L &&
        (targetChanged ||
          _state.value.folder == null ||
          _state.value.libraryStatus == MusicLibraryStatus.ERROR ||
          _state.value.libraryStatus == MusicLibraryStatus.MISSING)
    ) {
      loadLibrary(resetQuery = targetChanged)
    } else if (accountMid > 0L && _state.value.currentItem == null) {
      restoreLastTrack(_state.value.items)
    }
  }

  fun open(accountMid: Long, folderSelectionId: Long) {
    val normalizedFolderId = folderSelectionId.coerceAtLeast(0L)
    if (
      preparedEntryAccountMid != accountMid ||
        preparedEntryFolderSelectionId != normalizedFolderId
    ) {
      prepareOpen(accountMid, normalizedFolderId)
    }
    pageActive = true
    preparedEntryAccountMid = Long.MIN_VALUE
    preparedEntryFolderSelectionId = Long.MIN_VALUE
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
    val playback = player ?: return
    playback.trackSelectionParameters =
      playback.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !appInForeground)
        .build()
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
    val recoveryPositionMs =
      maxOf(lastStablePositionMs, playback.currentPosition.coerceAtLeast(0L))
    foregroundRecoveryJob?.cancel()
    foregroundRecoveryJob =
      viewModelScope.launch {
        delay(MUSIC_FOREGROUND_RECOVERY_WATCHDOG_MS)
        if (!pageActive || !appInForeground || _state.value.currentItem?.id != expectedItemId) {
          return@launch
        }
        val currentPlayer = player
        val needsRecovery =
          currentPlayer == null ||
            needsMusicForegroundRecovery(
              backgroundDurationMs = backgroundDurationMs,
              firstFrameReady = _state.value.firstFrameReady,
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

  fun createMusicFolder() {
    val current = _state.value
    if (
      current.accountMid <= 0L ||
        current.folderSelectionId > 0L ||
        current.creatingFolder
    ) return
    libraryJob?.cancel()
    val generation = ++libraryGeneration
    _state.value = current.copy(creatingFolder = true, libraryError = null)
    libraryJob =
      viewModelScope.launch {
        val result =
          runCatching {
            withContext(Dispatchers.IO) {
              BiliApi.createFavoriteFolder(MusicFavoriteFolderTitle, isPublic = false)
            }
          }
        if (generation != libraryGeneration) return@launch
        result
          .onSuccess { loadLibrary(resetQuery = true) }
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
    queryJob =
      viewModelScope.launch {
        delay(320)
        loadFirstPage()
      }
  }

  /** Clears search without debounce so the UI can page toward the currently playing track. */
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
      current.libraryStatus != MusicLibraryStatus.READY ||
        current.loadingMore ||
        !current.hasMore
    ) return
    val page = current.page + 1
    val generation = libraryGeneration
    _state.value = current.copy(loadingMore = true, libraryError = null)
    viewModelScope.launch {
      val result =
        runCatching {
          withContext(Dispatchers.IO) {
            BiliApi.getFavoriteVideos(folderId, page, _state.value.query.trim())
          }
        }
      if (generation != libraryGeneration || _state.value.folder?.id != folderId) return@launch
      result
        .onSuccess { response ->
          _state.value =
            _state.value.copy(
              items = (_state.value.items + response.cards.map(::musicFeedItem)).distinctBy { it.id },
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

  fun removeFromMusicFolder(item: FeedItem) {
    val current = _state.value
    val folderId = current.folder?.id ?: return
    val resourceId = item.favoriteResourceId
    val resourceType = item.favoriteResourceType
    if (
      resourceId <= 0L ||
        resourceType <= 0 ||
        item.id in current.deletingItemIds
    ) return
    _state.value =
      current.copy(
        deletingItemIds = current.deletingItemIds + item.id,
        libraryError = null,
      )
    viewModelScope.launch {
      val result =
        runCatching {
          withContext(Dispatchers.IO) {
            BiliApi.removeFavoriteResource(resourceId, resourceType, folderId)
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
    _state.value =
      _state.value.copy(
        playbackOrder =
          if (_state.value.playbackOrder == MusicPlaybackOrder.SEQUENTIAL) {
            MusicPlaybackOrder.RANDOM
          } else {
            MusicPlaybackOrder.SEQUENTIAL
          }
      )
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
    _state.value = current.copy(selectedPremiumAudio = mode, playbackLoading = true, playbackError = null)
    prepareResolvedTrack(
      resolved = resolved,
      startPositionMs = position,
      shouldPlay = shouldPlay,
      transitionCover = false,
    )
  }

  fun seekTo(positionMs: Long) {
    val duration = _state.value.durationMs.coerceAtLeast(0L)
    val target = positionMs.coerceIn(0L, duration)
    lastStablePositionMs = target
    _state.value = _state.value.copy(positionMs = target)
    player?.seekTo(target)
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
    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVolume)
    val volume = currentVolume.toFloat() / maxVolume
    val muted = currentVolume == 0 || audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
    val previous = _state.value
    val lastAudible = if (!muted) volume else previous.lastAudibleVolume
    if (previous.volume != volume || previous.muted != muted || previous.lastAudibleVolume != lastAudible) {
      _state.value = previous.copy(volume = volume, muted = muted, lastAudibleVolume = lastAudible)
    }
  }

  private fun loadLibrary(resetQuery: Boolean) {
    val accountMid = _state.value.accountMid
    val folderSelectionId = _state.value.folderSelectionId
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
    libraryJob =
      viewModelScope.launch {
        val result =
          runCatching {
            withContext(Dispatchers.IO) {
              val folder =
                findMusicFavoriteFolder(
                  folders = BiliApi.getFavoriteFolders(accountMid),
                  preferredFolderId = folderSelectionId,
                )
              val response =
                folder?.let { BiliApi.getFavoriteVideos(it.id, 1, _state.value.query.trim()) }
              folder to response
            }
          }
        if (
          generation != libraryGeneration ||
            accountMid != _state.value.accountMid ||
            folderSelectionId != _state.value.folderSelectionId
        ) return@launch
        result
          .onSuccess { (folder, response) ->
            if (folder == null) {
              _state.value =
                _state.value.copy(
                  libraryStatus = MusicLibraryStatus.MISSING,
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
    libraryJob =
      viewModelScope.launch {
        val result =
          runCatching {
            withContext(Dispatchers.IO) {
              BiliApi.getFavoriteVideos(folderId, 1, _state.value.query.trim())
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
    mediaJob =
      viewModelScope.launch {
        if (previous != null && previous.id != item.id) delay(COVER_HANDOFF_DELAY_MS)
        if (generation != mediaGeneration || _state.value.currentItem?.id != item.id) return@launch
        playback.stop()
        playback.clearMediaItems()
        val result =
          runCatching {
            val resolved = withContext(Dispatchers.IO) { resolveTrack(item) }
            val mode = preferredPremiumMode(resolved.data)
            val source =
              withContext(Dispatchers.IO) {
                buildMediaSource(resolved, mode, generation)
              }
            PreparedTrack(resolved, mode, source)
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
    mediaJob =
      viewModelScope.launch {
        val mode = _state.value.selectedPremiumAudio
        val result =
          runCatching {
            val source =
              withContext(Dispatchers.IO) { buildMediaSource(resolved, mode, generation) }
            PreparedTrack(resolved, mode, source)
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
    activeResolvedTrack = prepared.resolved
    val playback = preparePlayer()
    val dolby = supportsDolby(prepared.resolved.data)
    val hiRes = supportsHiRes(prepared.resolved.data)
    val selected = prepared.mode.takeIf {
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
    playback.setMediaSource(prepared.source, startPositionMs.coerceAtLeast(0L))
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
    retryJob =
      viewModelScope.launch {
        delay(musicRetryDelayMillis(attempt))
        if (!pageActive) return@launch
        retryJob = null
        retryNow()
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
      mediaJob =
        viewModelScope.launch {
          val result =
            runCatching {
              val resolved = withContext(Dispatchers.IO) { resolveTrack(item) }
              val mode = _state.value.selectedPremiumAudio.takeIf { requested ->
                requested == PremiumAudioMode.DOLBY && supportsDolby(resolved.data) ||
                  requested == PremiumAudioMode.HI_RES && supportsHiRes(resolved.data)
              } ?: preferredPremiumMode(resolved.data)
              val source =
                withContext(Dispatchers.IO) { buildMediaSource(resolved, mode, generation) }
              PreparedTrack(resolved, mode, source)
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
    positionJob =
      viewModelScope.launch {
        while (isActive && pageActive) {
          val playback = player
          if (playback != null) {
            val position = playback.currentPosition.coerceAtLeast(0L)
            if (position > 0L) lastStablePositionMs = position
            if (
              playback.isPlaying &&
                position - retryPreparedPositionMs >= STABLE_PLAYBACK_RESET_MS
            ) {
              retryAttempt = 0
              lastFailureWasNetwork = false
            }
            _state.value =
              _state.value.copy(
                positionMs = position,
                durationMs = playback.duration.coerceAtLeast(0L),
              )
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
    val resolvedId = BiliApi.resolveVideoBvid(item.videoUrl)
    val info =
      if (resolvedId.startsWith("av", ignoreCase = true)) {
        BiliApi.getVideoInfoByAid(resolvedId.drop(2).toLongOrNull() ?: 0L)
      } else {
        BiliApi.getVideoInfo(resolvedId)
      } ?: error("获取视频信息失败")
    val data = BiliApi.getPlayUrl(info.bvid, info.cid) ?: error("获取播放地址失败")
    val prioritized = prioritizeMusicRoutes(data.copy(durationMs = info.durationSeconds * 1_000L))
    val streamIndex = selectMusic720StreamIndex(prioritized.streams)
    val fixedStream = prioritized.streams.getOrNull(streamIndex) ?: error("没有可播放的视频流")
    return ResolvedTrack(
      item = item,
      data = prioritized.copy(streams = listOf(fixedStream), currentStreamIndex = 0),
    )
  }

  private fun buildMediaSource(
    resolved: ResolvedTrack,
    mode: PremiumAudioMode?,
    generation: Long,
  ): MediaSource {
    val playbackData = playbackDataForMode(resolved.data, mode)
    return runCatching {
      val manifest = BiliDashManifest.build(playbackData) ?: error("DASH 清单创建失败")
      val directory = File(getApplication<Application>().cacheDir, "bili_dash_music")
      if (!directory.exists() && !directory.mkdirs()) error("无法创建音乐清单缓存")
      val file = File(directory, "music_${generation}_${System.nanoTime()}.mpd")
      file.writeText(manifest, Charsets.UTF_8)
      activeManifestFile?.takeIf { it != file }?.delete()
      activeManifestFile = file
      val mediaItem =
        MediaItem.Builder()
          .setUri(Uri.fromFile(file))
          .setMimeType(MimeTypes.APPLICATION_MPD)
          .setMediaId(resolved.item.id)
          .setMediaMetadata(musicMediaMetadata(resolved.item))
          .build()
      DashMediaSource.Factory(cachedDataSourceFactory).createMediaSource(mediaItem)
    }.getOrElse { manifestError ->
      Log.w(TAG, "music DASH manifest unavailable; using compatibility source", manifestError)
      val stream = playbackData.streams.firstOrNull() ?: throw manifestError
      val video = cachedMediaItem(stream.url, resolved.item, includeMetadata = true)
      val audio =
        playbackData.selectedAudioUrl()?.let { url ->
          cachedMediaItem(url, resolved.item, includeMetadata = false)
        }
      if (audio == null) {
        ProgressiveMediaSource.Factory(cachedDataSourceFactory).createMediaSource(video)
      } else {
        MergingMediaSource(
          ProgressiveMediaSource.Factory(cachedDataSourceFactory).createMediaSource(video),
          ProgressiveMediaSource.Factory(cachedDataSourceFactory).createMediaSource(audio),
        )
      }
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
      .setTitle(item.title)
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
      val ordered = prioritizeCdnUrls(stream.url, stream.backupUrls, "")
      return stream.copy(url = ordered.primary, backupUrls = ordered.backups)
    }

    fun audio(stream: AudioStream?): AudioStream? {
      stream ?: return null
      val ordered = prioritizeCdnUrls(stream.url, stream.backupUrls, "")
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
      error.errorCodeName.contains("NETWORK", ignoreCase = true) ->
        "网络连接中断，正在自动重试"
      error.errorCodeName.contains("DECOD", ignoreCase = true) ->
        "当前音频解码器不兼容，正在切换兼容模式"
      error.errorCodeName.contains("RUNTIME_CHECK", ignoreCase = true) ->
        "音频输出格式异常，正在切换兼容模式"
      error.errorCodeName.contains("PARSING", ignoreCase = true) ->
        "播放清单解析失败，正在刷新地址"
      else -> "播放中断（${error.errorCodeName}），正在自动重试"
    }

  private fun stopPlayback(clearCurrent: Boolean) {
    mediaGeneration++
    mediaJob?.cancel()
    mediaJob = null
    retryJob?.cancel()
    retryJob = null
    spectrumJob?.cancel()
    foregroundRecoveryJob?.cancel()
    spectrumJob = null
    player?.stop()
    player?.clearMediaItems()
    activeManifestFile?.delete()
    activeManifestFile = null
    activeResolvedTrack = null
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
          spectrumBands = List(MUSIC_SPECTRUM_BAND_COUNT) { 0f },
        )
    }
  }

  private fun submitSpectrumSamples(samples: FloatArray, sampleRate: Int) {
    if (!pageActive || spectrumJob?.isActive == true) return
    val volumeState = _state.value
    val systemVolume = if (volumeState.muted) 0f else volumeState.volume
    spectrumJob =
      viewModelScope.launch(Dispatchers.Default) {
        val bands = analyzeMusicSpectrum(samples, sampleRate, systemVolume = systemVolume)
        withContext(Dispatchers.Main.immediate) {
          if (pageActive && bands.isNotEmpty()) {
            _state.value = _state.value.copy(spectrumBands = bands)
          }
        }
      }
  }

  override fun onCleared() {
    persistResumeSnapshot(force = true)
    libraryJob?.cancel()
    queryJob?.cancel()
    positionJob?.cancel()
    spectrumJob?.cancel()
    stopPlayback(clearCurrent = true)
    player?.release()
    player = null
  }

  private fun ExoPlayer?.orIdle(): ExoPlayer = this ?: preparePlayer()

  private data class ResolvedTrack(val item: FeedItem, val data: PlayUrlData)

  private fun switchToCompatibleAudioDecoder() {
    val resolved = activeResolvedTrack ?: run {
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
    val resolved = activeResolvedTrack ?: run {
      scheduleRetry()
      return
    }
    Log.w(TAG, "premium audio decoder failed; falling back to standard audio at $lastStablePositionMs ms")
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
      generateSequence<Throwable>(error) { it.cause }.any { cause ->
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
      readResumeSnapshot(_state.value.accountMid)
        ?.takeIf { it.folderSelectionId == folderSelectionId }
    val item =
      snapshot?.let { saved -> items.firstOrNull { it.id == saved.item.id } }
        ?: items.firstOrNull()
        ?: return
    loadTrack(item, startPositionMs = snapshot?.positionMs?.takeIf { snapshot.item.id == item.id } ?: 0L)
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
    resumePrefs.edit()
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
    val source: MediaSource,
  )

  private companion object {
    const val TAG = "HomeMusicPlayer"
    const val COVER_HANDOFF_DELAY_MS = 70L
    const val STABLE_PLAYBACK_RESET_MS = 3_000L
    const val RESUME_WRITE_INTERVAL_MS = 3_000L
    const val MUSIC_FOREGROUND_RECOVERY_WATCHDOG_MS = 1_400L
  }
}

internal const val MUSIC_FOREGROUND_RECOVERY_THRESHOLD_MS = 20_000L
internal const val MUSIC_FOREGROUND_URL_REFRESH_MS = 20 * 60_000L

@OptIn(UnstableApi::class)
private class MusicRenderersFactory(
  context: Context,
  preferSoftwareAudioDecoder: Boolean,
  private val onSpectrumSamples: (FloatArray, Int) -> Unit,
) : HiResCompatibleRenderersFactory(context, preferSoftwareAudioDecoder) {

  override fun buildAudioSink(
    context: Context,
    enableFloatOutput: Boolean,
    enableAudioTrackPlaybackParams: Boolean,
  ): AudioSink? {
    val delegate =
      super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
        ?: return null
    return LittleEndianAudioSink(delegate, onSpectrumSamples)
  }
}

@OptIn(UnstableApi::class)
private class LittleEndianAudioSink(
  delegate: AudioSink,
  onSpectrumSamples: (FloatArray, Int) -> Unit,
) : ForwardingAudioSink(delegate) {
  private val spectrumTap = Pcm16SpectrumTap(onSpectrumSamples)

  override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
    spectrumTap.configure(inputFormat)
    super.configure(inputFormat, specifiedBufferSize, outputChannels)
  }

  override fun handleBuffer(
    buffer: ByteBuffer,
    presentationTimeUs: Long,
    encodedAccessUnitCount: Int,
  ): Boolean {
    if (buffer.order() != ByteOrder.LITTLE_ENDIAN) buffer.order(ByteOrder.LITTLE_ENDIAN)
    val startPosition = buffer.position()
    val fullyConsumed = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    val endPosition = buffer.position()
    if (endPosition > startPosition) {
      spectrumTap.accept(buffer, startPosition, endPosition)
    }
    return fullyConsumed
  }
}

private class Pcm16SpectrumTap(
  private val onSpectrumSamples: (FloatArray, Int) -> Unit,
) {
  private val ring = FloatArray(1_024)
  private var writeIndex = 0
  private var sampleCount = 0
  private var channelCount = 0
  private var sampleRate = 0
  private var pcm16 = false
  private var lastEmissionNanos = 0L

  fun configure(format: Format) {
    channelCount = format.channelCount.coerceAtLeast(1)
    sampleRate = format.sampleRate.coerceAtLeast(0)
    pcm16 =
      format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding == C.ENCODING_PCM_16BIT
    writeIndex = 0
    sampleCount = 0
    lastEmissionNanos = 0L
  }

  fun accept(source: ByteBuffer, start: Int, end: Int) {
    if (!pcm16 || sampleRate <= 0 || end <= start) return
    val frameBytes = channelCount * 2
    val input = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    input.position(start)
    input.limit(end)
    while (input.remaining() >= frameBytes) {
      var sum = 0f
      repeat(channelCount) { sum += input.short / 32_768f }
      ring[writeIndex] = sum / channelCount
      writeIndex = (writeIndex + 1) % ring.size
      sampleCount = (sampleCount + 1).coerceAtMost(ring.size)
    }
    val now = System.nanoTime()
    if (sampleCount < 256 || now - lastEmissionNanos < 45_000_000L) return
    lastEmissionNanos = now
    val snapshot = FloatArray(sampleCount)
    val first = (writeIndex - sampleCount + ring.size) % ring.size
    snapshot.indices.forEach { index -> snapshot[index] = ring[(first + index) % ring.size] }
    onSpectrumSamples(snapshot, sampleRate)
  }
}

/** Produces logarithmically spaced, real PCM-responsive bands for the music-page visualizer. */
internal fun analyzeMusicSpectrum(
  samples: FloatArray,
  sampleRate: Int,
  bandCount: Int = MUSIC_SPECTRUM_BAND_COUNT,
  systemVolume: Float = 1f,
): List<Float> {
  if (samples.size < 128 || sampleRate <= 0 || bandCount <= 0) return List(bandCount.coerceAtLeast(0)) { 0f }
  val windowSize = samples.size.coerceAtMost(1_024)
  val offset = samples.size - windowSize
  var mean = 0.0
  repeat(windowSize) { mean += samples[offset + it] }
  mean /= windowSize
  val windowed = DoubleArray(windowSize)
  var squareSum = 0.0
  repeat(windowSize) { index ->
    val centered = samples[offset + index] - mean
    val hann = .5 - .5 * cos(2.0 * PI * index / (windowSize - 1).coerceAtLeast(1))
    val value = centered * hann
    windowed[index] = value
    squareSum += centered * centered
  }
  val rms = sqrt(squareSum / windowSize)
  if (rms < .000_02) return List(bandCount) { 0f }

  val minimumFrequency = 55.0
  val maximumFrequency = minOf(16_000.0, sampleRate * .46).coerceAtLeast(minimumFrequency)
  val logRange = ln(maximumFrequency / minimumFrequency)
  val magnitudes = DoubleArray(bandCount)
  repeat(bandCount) { band ->
    val fraction = if (bandCount == 1) 0.0 else band.toDouble() / (bandCount - 1)
    val frequency = minimumFrequency * exp(logRange * fraction)
    val angularStep = 2.0 * PI * frequency / sampleRate
    val coefficient = 2.0 * cos(angularStep)
    var previous = 0.0
    var previousPrevious = 0.0
    repeat(windowSize) { index ->
      val current = windowed[index] + coefficient * previous - previousPrevious
      previousPrevious = previous
      previous = current
    }
    // Goertzel evaluates the same selected DFT bins without two trigonometric calls for every
    // sample. The visual result is unchanged, while sustained playback no longer keeps a CPU core
    // busy enough to thermally degrade UI animation after several minutes.
    magnitudes[band] =
      sqrt(
        (previousPrevious * previousPrevious + previous * previous -
            coefficient * previous * previousPrevious)
          .coerceAtLeast(0.0)
      )
  }
  val peak = magnitudes.maxOrNull()?.takeIf { it > 0.0 } ?: return List(bandCount) { 0f }
  val levelGain = musicSpectrumLevelGain(rms * systemVolume.coerceIn(0f, 1f))
  if (levelGain <= 0.0) return List(bandCount) { 0f }
  return magnitudes.map { magnitude ->
    // Peak normalization preserves the real frequency shape. A compressed RMS envelope then
    // restores audible loudness changes without letting quiet passages collapse or loud passages
    // pin every bar.
    ((magnitude / peak).coerceIn(0.0, 1.0).pow(.55) * levelGain).toFloat()
  }
}

internal fun musicSpectrumLevelGain(rms: Double): Double {
  val level = rms.coerceIn(0.0, 1.0)
  if (level < .000_02) return 0.0
  val compressed = ln(1.0 + level * 80.0) / ln(81.0)
  return .58 + .42 * compressed
}

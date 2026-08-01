package dev.openbili.webdemo.live

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LivePreviewPlayerState {
  data object Idle : LivePreviewPlayerState

  data class Loading(val roomId: Long) : LivePreviewPlayerState

  data class Ready(val roomId: Long) : LivePreviewPlayerState

  data class Error(val roomId: Long, val message: String) : LivePreviewPlayerState
}

/**
 * The landing-page preview owns only a muted rolling live stream. The full live-room player stays
 * in PlayerViewModel and is intentionally not shared with this screen.
 */
@OptIn(UnstableApi::class)
class LivePreviewPlayerViewModel(application: Application) : AndroidViewModel(application) {
  private val _state =
    kotlinx.coroutines.flow.MutableStateFlow<LivePreviewPlayerState>(LivePreviewPlayerState.Idle)
  val state: kotlinx.coroutines.flow.StateFlow<LivePreviewPlayerState> = _state

  private var loadJob: Job? = null
  private var generation = 0L
  private var activeRoomId: Long? = null
  private var muted = true
  private var pageActive = false
  private var sources = emptyList<LiveStreamSource>()
  private var sourceIndex = 0
  private var refreshCount = 0
  private var recoveryScheduled = false
  private var slowRetryAttempt = 0
  private var retryJob: Job? = null

  var player: ExoPlayer? = null
    private set

  fun preparePlayer(): ExoPlayer {
    player?.let {
      return it
    }
    return ExoPlayer.Builder(getApplication()).build().also { created ->
      created.volume = if (muted) 0f else 1f
      created.addListener(
        object : Player.Listener {
          override fun onRenderedFirstFrame() {
            val roomId = activeRoomId ?: return
            if (created.currentMediaItem?.mediaId == mediaId(roomId)) {
              refreshCount = 0
              recoveryScheduled = false
              slowRetryAttempt = 0
              retryJob?.cancel()
              retryJob = null
              _state.value = LivePreviewPlayerState.Ready(roomId)
            }
          }

          override fun onPlayerError(error: PlaybackException) {
            if (pageActive && activeRoomId != null && !recoveryScheduled) {
              recoveryScheduled = true
              viewModelScope.launch { recoverFromPlaybackError() }
            }
          }
        }
      )
      player = created
    }
  }

  fun play(room: LiveSearchRoom, muted: Boolean = true) {
    pageActive = true
    setMuted(muted)
    val roomId = room.roomId
    val current = preparePlayer()
    if (activeRoomId == roomId) {
      when (_state.value) {
        is LivePreviewPlayerState.Ready -> {
          current.play()
          return
        }
        is LivePreviewPlayerState.Loading -> {
          if (loadJob?.isActive == true || current.currentMediaItem?.mediaId == mediaId(roomId)) {
            current.playWhenReady = true
            return
          }
        }
        is LivePreviewPlayerState.Error -> {
          if (retryJob?.isActive == true) return
        }
        LivePreviewPlayerState.Idle -> Unit
      }
    }
    restartRoom(roomId)
  }

  fun setMuted(value: Boolean) {
    muted = value
    player?.volume = if (value) 0f else 1f
  }

  fun pauseForInactivePage() {
    pageActive = false
    recoveryScheduled = false
    loadJob?.cancel()
    retryJob?.cancel()
    retryJob = null
    player?.pause()
  }

  /** Releases this rolling decoder before the detail page starts its own player. */
  fun stopForNavigation() {
    pageActive = false
    recoveryScheduled = false
    generation++
    loadJob?.cancel()
    loadJob = null
    retryJob?.cancel()
    retryJob = null
    activeRoomId = null
    sources = emptyList()
    sourceIndex = 0
    refreshCount = 0
    slowRetryAttempt = 0
    player?.stop()
    player?.clearMediaItems()
    _state.value = LivePreviewPlayerState.Idle
  }

  override fun onCleared() {
    stopForNavigation()
    player?.release()
    player = null
  }

  private fun restartRoom(roomId: Long) {
    val requestGeneration = ++generation
    activeRoomId = roomId
    sources = emptyList()
    sourceIndex = 0
    refreshCount = 0
    recoveryScheduled = false
    slowRetryAttempt = 0
    loadJob?.cancel()
    retryJob?.cancel()
    retryJob = null
    val playback = preparePlayer()
    playback.stop()
    playback.clearMediaItems()
    _state.value = LivePreviewPlayerState.Loading(roomId)
    requestPlaybackInfo(roomId, requestGeneration)
  }

  private fun requestPlaybackInfo(roomId: Long, requestGeneration: Long) {
    loadJob = viewModelScope.launch {
      try {
        val freshSources =
          withContext(Dispatchers.IO) {
            BiliLiveApi.getPlayInfo(roomId, qn = PREVIEW_QN).sources
          }
        if (!isCurrent(requestGeneration, roomId)) return@launch
        sources = orderPreviewSources(freshSources)
        sourceIndex = 0
        if (sources.isEmpty()) error("没有可用的直播预览流")
        startSource(roomId, requestGeneration)
      } catch (error: Throwable) {
        if (error is CancellationException) throw error
        if (isCurrent(requestGeneration, roomId)) {
          recoveryScheduled = false
          scheduleSlowRetry(roomId, requestGeneration)
        }
      }
    }
  }

  private fun startSource(roomId: Long, requestGeneration: Long) {
    val playback = player ?: return
    val source = sources.getOrNull(sourceIndex) ?: return
    if (!isCurrent(requestGeneration, roomId)) return
    val mediaItem =
      MediaItem.Builder()
        .setMediaId(mediaId(roomId))
        .setUri(source.url)
        .setLiveConfiguration(
          MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(3_000L).build()
        )
        .build()
    playback.stop()
    playback.clearMediaItems()
    playback.setMediaSource(createMediaSource(source, mediaItem, roomId))
    playback.prepare()
    playback.playWhenReady = true
    _state.value = LivePreviewPlayerState.Loading(roomId)
  }

  private suspend fun recoverFromPlaybackError() {
    delay(RECOVERY_DELAY_MS)
    val roomId = activeRoomId
    if (!pageActive || roomId == null) {
      recoveryScheduled = false
      return
    }
    val requestGeneration = generation
    when {
      sourceIndex + 1 < sources.size -> {
        sourceIndex++
        recoveryScheduled = false
        startSource(roomId, requestGeneration)
      }
      refreshCount < MAX_PLAY_URL_REFRESHES -> {
        refreshCount++
        recoveryScheduled = false
        requestPlaybackInfo(roomId, requestGeneration)
      }
      else -> {
        recoveryScheduled = false
        scheduleSlowRetry(roomId, requestGeneration)
      }
    }
  }

  private fun scheduleSlowRetry(roomId: Long, requestGeneration: Long) {
    if (!isCurrent(requestGeneration, roomId) || !pageActive || retryJob?.isActive == true) return
    _state.value = LivePreviewPlayerState.Error(roomId, "正在重新连接…")
    val delayMs =
      SLOW_RETRY_DELAYS_MS[slowRetryAttempt.coerceAtMost(SLOW_RETRY_DELAYS_MS.lastIndex)]
    retryJob = viewModelScope.launch {
      delay(delayMs)
      if (!isCurrent(requestGeneration, roomId) || !pageActive) return@launch
      slowRetryAttempt++
      refreshCount = 0
      recoveryScheduled = false
      requestPlaybackInfo(roomId, requestGeneration)
    }
  }

  private fun createMediaSource(
    source: LiveStreamSource,
    mediaItem: MediaItem,
    roomId: Long,
  ): MediaSource {
    val httpFactory =
      DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setDefaultRequestProperties(
          mapOf(
            "Origin" to "https://live.bilibili.com",
            "Referer" to "https://live.bilibili.com/$roomId",
          )
        )
    val dataSourceFactory = DefaultDataSource.Factory(getApplication(), httpFactory)
    return when (source.format) {
      LiveStreamFormat.HLS_FMP4,
      LiveStreamFormat.HLS_TS ->
        HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
      LiveStreamFormat.HTTP_FLV ->
        ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }
  }

  private fun isCurrent(requestGeneration: Long, roomId: Long): Boolean =
    requestGeneration == generation && activeRoomId == roomId

  private fun mediaId(roomId: Long): String = "live:$roomId"

  private fun orderPreviewSources(value: List<LiveStreamSource>): List<LiveStreamSource> =
    value
      .distinctBy(LiveStreamSource::url)
      .sortedWith(
        compareBy<LiveStreamSource>(
          {
            when (it.format) {
              LiveStreamFormat.HLS_FMP4 -> 0
              LiveStreamFormat.HLS_TS -> 1
              LiveStreamFormat.HTTP_FLV -> 2
            }
          },
          { if (it.codec.contains("avc", ignoreCase = true)) 0 else 1 },
        )
      )

  private companion object {
    const val PREVIEW_QN = 400
    const val MAX_PLAY_URL_REFRESHES = 2
    const val RECOVERY_DELAY_MS = 350L
    val SLOW_RETRY_DELAYS_MS = longArrayOf(2_000L, 4_000L, 8_000L, 15_000L)
    const val USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
  }
}

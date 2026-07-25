package dev.openbili.webdemo.bangumi

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import dev.openbili.webdemo.BiliDashManifest
import dev.openbili.webdemo.PlaybackCache
import dev.openbili.webdemo.settings.PreferredResolutionMode
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.selectPreferredStreamIndex
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BangumiPreviewPlayerState {
  data object Idle : BangumiPreviewPlayerState

  data class Loading(val itemId: String) : BangumiPreviewPlayerState

  data class Ready(val itemId: String) : BangumiPreviewPlayerState

  data class Error(val itemId: String, val message: String) : BangumiPreviewPlayerState
}

/**
 * A screen-private, muted-by-default player for the Bangumi recommendation hero.
 *
 * It deliberately owns neither history nor detail-page state. The detail player remains in
 * [dev.openbili.webdemo.PlayerViewModel], so changing a PV cannot cancel or replace a video page.
 */
@OptIn(UnstableApi::class)
class BangumiPreviewPlayerViewModel(application: Application) : AndroidViewModel(application) {

  private val _state = MutableStateFlow<BangumiPreviewPlayerState>(BangumiPreviewPlayerState.Idle)
  val state: StateFlow<BangumiPreviewPlayerState> = _state.asStateFlow()

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
      .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
  }

  private var loadJob: Job? = null
  private var generation = 0L
  private var activeItemId: String? = null
  private var activeManifestFile: File? = null
  private var muted = true

  var player: ExoPlayer? = null
    private set

  fun preparePlayer(): ExoPlayer {
    player?.let { return it }
    return ExoPlayer.Builder(getApplication()).build().also { created ->
      created.repeatMode = Player.REPEAT_MODE_ONE
      created.volume = if (muted) 0f else 1f
      created.addListener(
        object : Player.Listener {
          override fun onRenderedFirstFrame() {
            val itemId = created.currentMediaItem?.mediaId.orEmpty()
            if (itemId.isNotBlank() && itemId == activeItemId) {
              _state.value = BangumiPreviewPlayerState.Ready(itemId)
            }
          }

          override fun onPlayerError(error: PlaybackException) {
            val itemId = activeItemId ?: return
            _state.value = BangumiPreviewPlayerState.Error(itemId, "预览暂时无法播放")
          }
        }
      )
      player = created
    }
  }

  fun play(item: FeedItem, muted: Boolean) {
    setMuted(muted)
    val existing = activeItemId
    val current = player
    if (existing == item.id && current?.currentMediaItem?.mediaId == item.id) {
      current.play()
      return
    }

    val requestGeneration = ++generation
    activeItemId = item.id
    loadJob?.cancel()
    val playback = preparePlayer()
    playback.stop()
    playback.clearMediaItems()
    _state.value = BangumiPreviewPlayerState.Loading(item.id)
    loadJob =
      viewModelScope.launch {
        val result =
          runCatching {
            val resolved = withContext(Dispatchers.IO) { resolvePreview(item) }
            withContext(Dispatchers.IO) { resolved to buildMediaSource(resolved, requestGeneration) }
          }
        if (requestGeneration != generation || activeItemId != item.id) return@launch
        result
          .onSuccess { (_, source) ->
            if (requestGeneration != generation || activeItemId != item.id) return@onSuccess
            playback.setMediaSource(source)
            playback.prepare()
            playback.playWhenReady = true
          }
          .onFailure {
            _state.value = BangumiPreviewPlayerState.Error(item.id, "预览暂时无法播放")
          }
      }
  }

  fun setMuted(value: Boolean) {
    muted = value
    player?.volume = if (value) 0f else 1f
  }

  fun pauseForGesture() {
    player?.pause()
  }

  fun resumeAfterGesture() {
    val ready = _state.value as? BangumiPreviewPlayerState.Ready ?: return
    if (ready.itemId == activeItemId) player?.play()
  }

  fun pauseForInactivePage() {
    player?.pause()
  }

  /** Releases the decoder before a detail page starts its own global player. */
  fun stopForNavigation() {
    generation++
    loadJob?.cancel()
    loadJob = null
    activeItemId = null
    player?.stop()
    player?.clearMediaItems()
    activeManifestFile?.delete()
    activeManifestFile = null
    _state.value = BangumiPreviewPlayerState.Idle
  }

  private fun resolvePreview(item: FeedItem): ResolvedPreview {
    val bvid = BiliApi.resolveVideoBvid(item.videoUrl)
    val episodeId = BiliApi.bangumiEpisodeId(item.videoUrl)
    val info = BiliApi.getVideoInfo(bvid) ?: error("获取视频信息失败")
    val data =
      (episodeId?.let { BiliApi.getBangumiPlayUrl(it, info.cid) } ?: BiliApi.getPlayUrl(bvid, info.cid))
        ?: error("获取播放地址失败，可能需要登录、会员或地区权限")
    val prepared =
      data.copy(durationMs = info.durationSeconds * 1_000L).let(::prioritizePreviewRoutes)
    val streamIndex =
      selectPreferredStreamIndex(prepared.streams, PreferredResolutionMode.MEDIUM)
    return ResolvedPreview(item.id, prepared.copy(currentStreamIndex = streamIndex))
  }

  private fun buildMediaSource(resolved: ResolvedPreview, requestGeneration: Long) =
    runCatching {
      val manifest = BiliDashManifest.build(resolved.data) ?: error("DASH 清单创建失败")
      val directory = File(getApplication<Application>().cacheDir, "bili_dash_preview")
      if (!directory.exists()) directory.mkdirs()
      val file = File(directory, "preview_${requestGeneration}_${System.nanoTime()}.mpd")
      file.writeText(manifest, Charsets.UTF_8)
      activeManifestFile?.takeIf { it != file }?.delete()
      activeManifestFile = file
      val mediaItem =
        MediaItem.Builder()
          .setUri(Uri.fromFile(file))
          .setMimeType(MimeTypes.APPLICATION_MPD)
          .setMediaId(resolved.itemId)
          .build()
      DashMediaSource.Factory(cachedDataSourceFactory).createMediaSource(mediaItem)
    }.getOrElse {
      val stream = resolved.data.streams.getOrNull(resolved.data.currentStreamIndex) ?: throw it
      val video = cachedMediaItem(stream.url, resolved.itemId)
      val audio = resolved.data.selectedAudioUrl()?.let { url -> cachedMediaItem(url, resolved.itemId) }
      if (audio == null) {
        ProgressiveMediaSource.Factory(cachedDataSourceFactory).createMediaSource(video)
      } else {
        MergingMediaSource(
          ProgressiveMediaSource.Factory(cachedDataSourceFactory).createMediaSource(video),
          ProgressiveMediaSource.Factory(cachedDataSourceFactory).createMediaSource(audio),
        )
      }
    }

  private fun cachedMediaItem(url: String, itemId: String): MediaItem {
    val uri = Uri.parse(url)
    val cacheKey = uri.encodedPath?.takeIf(String::isNotBlank)?.let { "bili:$it" }
    return MediaItem.Builder().setUri(uri).setMediaId(itemId).setCustomCacheKey(cacheKey).build()
  }

  private fun prioritizePreviewRoutes(data: PlayUrlData): PlayUrlData =
    data.copy(
      streams =
        data.streams.map { stream ->
          val ordered = dev.openbili.webdemo.prioritizeCdnUrls(stream.url, stream.backupUrls, "")
          stream.copy(url = ordered.primary, backupUrls = ordered.backups)
        },
      dashAudio =
        data.dashAudio?.let { audio ->
          val ordered = dev.openbili.webdemo.prioritizeCdnUrls(audio.url, audio.backupUrls, "")
          audio.copy(url = ordered.primary, backupUrls = ordered.backups)
        },
    ).let { prioritized ->
      prioritized.copy(dashAudioUrl = prioritized.dashAudio?.url ?: data.dashAudioUrl)
    }

  override fun onCleared() {
    stopForNavigation()
    player?.release()
    player = null
  }

  private data class ResolvedPreview(val itemId: String, val data: PlayUrlData)
}

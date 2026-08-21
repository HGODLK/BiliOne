package dev.openbili.webdemo.bangumi

/**
 * 番剧推荐头图的静音预览播放器。
 *
 * 这是一个屏幕私有、默认静音的轻量播放器，仅服务于推荐头图的悬浮预览。它刻意不持有历史
 * 与详情页状态（详情播放器仍在 PlayerViewModel 中），因此切换预览不会取消或替换视频页。
 */

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
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.openbili.webdemo.api.BiliBangumiApi
import dev.openbili.webdemo.api.BiliVideoApi
import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.BiliDashManifest
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.PlaybackCache
import dev.openbili.webdemo.selectPreferredStreamIndex
import dev.openbili.webdemo.settings.PreferredResolutionMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 预览播放器的状态机：空闲、加载中、就绪、错误。
 */
sealed interface BangumiPreviewPlayerState {
  data object Idle : BangumiPreviewPlayerState

  data class Loading(val itemId: String) : BangumiPreviewPlayerState

  data class Ready(val itemId: String) : BangumiPreviewPlayerState

  data class Error(val itemId: String, val message: String) : BangumiPreviewPlayerState
}

/**
 * 番剧推荐头图专用的屏幕私有、默认静音播放器。
 *
 * 它刻意不持有历史与详情页状态（详情播放器仍位于 [dev.openbili.webdemo.PlayerViewModel] 中），
 * 因此切换预览不会取消或替换视频页。
 */
@OptIn(UnstableApi::class)
class BangumiPreviewPlayerViewModel(application: Application) : AndroidViewModel(application) {

  private val _state = MutableStateFlow<BangumiPreviewPlayerState>(BangumiPreviewPlayerState.Idle)
  val state: StateFlow<BangumiPreviewPlayerState> = _state.asStateFlow()

  // 统一 B 站请求所需的 UA 与 Referer 头
  private val httpFactory =
    DefaultHttpDataSource.Factory()
      .setUserAgent(
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
      )
      .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com/"))
  // 默认数据源（网络直连）
  private val dataSourceFactory by lazy {
    DefaultDataSource.Factory(getApplication(), httpFactory)
  }
  // 带离线缓存的媒体数据源，缓存出错时自动回退到网络
  private val cachedDataSourceFactory by lazy {
    CacheDataSource.Factory()
      .setCache(PlaybackCache.get(getApplication()))
      .setUpstreamDataSourceFactory(dataSourceFactory)
      .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
  }

  // ── 运行时状态：加载任务、请求代数、当前条目、临时 DASH 清单与播放许可 ──
  private var loadJob: Job? = null
  private var generation = 0L
  private var activeItemId: String? = null
  private var activeManifestFile: File? = null
  private var muted = true
  private var pagePlaybackAllowed = false

  var player: ExoPlayer? = null
    private set

  fun preparePlayer(): ExoPlayer {
    player?.let {
      return it
    }
    return ExoPlayer.Builder(getApplication()).build().also { created ->
      // 单条循环播放，音量跟随静音开关
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
    pagePlaybackAllowed = true
    setMuted(muted)
    val existing = activeItemId
    val current = player
    if (existing == item.id && current?.currentMediaItem?.mediaId == item.id) {
      // 同一条目已在播放，直接续播
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
    loadJob = viewModelScope.launch {
      val result = runCatching {
        // 解析地址与构建媒体源都在 IO 线程完成
        val resolved = withContext(Dispatchers.IO) { resolvePreview(item) }
        withContext(Dispatchers.IO) { resolved to buildMediaSource(resolved, requestGeneration) }
      }
      if (requestGeneration != generation || activeItemId != item.id) return@launch
      result
        .onSuccess { (_, source) ->
          if (requestGeneration != generation || activeItemId != item.id) return@onSuccess
          playback.setMediaSource(source)
          playback.prepare()
          playback.playWhenReady = pagePlaybackAllowed
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

  // 手势拖动期间暂停播放
  fun pauseForGesture() {
    player?.pause()
  }

  // 手势结束后，仅在处于就绪态时续播
  fun resumeAfterGesture() {
    val ready = _state.value as? BangumiPreviewPlayerState.Ready ?: return
    if (ready.itemId == activeItemId) player?.play()
  }

  // 页面失活时暂停并收回播放许可
  fun pauseForInactivePage() {
    pagePlaybackAllowed = false
    player?.pause()
  }

  /** 在详情页启动自己的全局播放器前释放解码器。 */
  fun stopForNavigation() {
    pagePlaybackAllowed = false
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
    val bvid = BiliBangumiApi.resolveVideoBvid(item.videoUrl)
    val episodeId = BiliVideoApi.bangumiEpisodeId(item.videoUrl)
    val info = BiliVideoApi.getVideoInfo(bvid) ?: error("获取视频信息失败")
    // 优先走番剧播放地址，否则回退到通用视频播放地址
    val data =
      (episodeId?.let { BiliVideoApi.getBangumiPlayUrl(it, info.cid) }
        ?: BiliVideoApi.getPlayUrl(bvid, info.cid)) ?: error("获取播放地址失败，可能需要登录、会员或地区权限")
    val prepared =
      data.copy(durationMs = info.durationSeconds * 1_000L).let(::prioritizePreviewRoutes)
    // 预览统一取中等清晰度
    val streamIndex = selectPreferredStreamIndex(prepared.streams, PreferredResolutionMode.MEDIUM)
    return ResolvedPreview(item.id, prepared.copy(currentStreamIndex = streamIndex))
  }

  private fun buildMediaSource(resolved: ResolvedPreview, requestGeneration: Long) =
    runCatching {
        val manifest = BiliDashManifest.build(resolved.data) ?: error("DASH 清单创建失败")
        // 把清单写入缓存目录的临时 .mpd 文件，供 MediaItem 引用
        val directory = File(getApplication<Application>().cacheDir, "bili_dash_preview")
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, "preview_${requestGeneration}_${System.nanoTime()}.mpd")
        file.writeText(manifest, Charsets.UTF_8)
        // 删除上一个临时清单，避免残留文件
        activeManifestFile?.takeIf { it != file }?.delete()
        activeManifestFile = file
        val mediaItem =
          MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .setMediaId(resolved.itemId)
            .build()
        DashMediaSource.Factory(cachedDataSourceFactory).createMediaSource(mediaItem)
      }
      .getOrElse {
        // DASH 构建失败时回退到渐进式（progressive）媒体源
        val stream = resolved.data.streams.getOrNull(resolved.data.currentStreamIndex) ?: throw it
        val video = cachedMediaItem(stream.url, resolved.itemId)
        val audio =
          resolved.data.selectedAudioUrl()?.let { url -> cachedMediaItem(url, resolved.itemId) }
        if (audio == null) {
          // 无独立音频流：仅视频渐进源
          ProgressiveMediaSource.Factory(cachedDataSourceFactory).createMediaSource(video)
        } else {
          // 视频 + 音频分别作为渐进源合并播放
          MergingMediaSource(
            ProgressiveMediaSource.Factory(cachedDataSourceFactory).createMediaSource(video),
            ProgressiveMediaSource.Factory(cachedDataSourceFactory).createMediaSource(audio),
          )
        }
      }

  private fun cachedMediaItem(url: String, itemId: String): MediaItem {
    val uri = Uri.parse(url)
    // 以路径生成缓存键，使缓存命中不依赖完整查询串
    val cacheKey = uri.encodedPath?.takeIf(String::isNotBlank)?.let { "bili:$it" }
    return MediaItem.Builder().setUri(uri).setMediaId(itemId).setCustomCacheKey(cacheKey).build()
  }

  // 按 CDN 优先级重排视频流与音频流的地址，提升预览加载速度
  private fun prioritizePreviewRoutes(data: PlayUrlData): PlayUrlData =
    data
      .copy(
        streams =
          data.streams.map { stream ->
            val ordered =
              dev.openbili.webdemo.prioritizeCdnUrls(
                stream.url,
                stream.backupUrls,
                dev.openbili.webdemo.configuredCdnHost(getApplication()),
              )
            stream.copy(url = ordered.primary, backupUrls = ordered.backups)
          },
        dashAudio =
          data.dashAudio?.let { audio ->
            val ordered =
              dev.openbili.webdemo.prioritizeCdnUrls(
                audio.url,
                audio.backupUrls,
                dev.openbili.webdemo.configuredCdnHost(getApplication()),
              )
            audio.copy(url = ordered.primary, backupUrls = ordered.backups)
          },
      )
      .let { prioritized ->
        prioritized.copy(dashAudioUrl = prioritized.dashAudio?.url ?: data.dashAudioUrl)
      }

  override fun onCleared() {
    stopForNavigation()
    player?.release()
    player = null
  }

  private data class ResolvedPreview(val itemId: String, val data: PlayUrlData)
}

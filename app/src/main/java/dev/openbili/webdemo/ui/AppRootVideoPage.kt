package dev.openbili.webdemo.ui

/**
 * 视频页数据上下文：持有视频页缓存条目、数据提交闸门与选集/评论排序等操作，
 * 是 AppRoot 拆分出的视频页逻辑归宿。
 */

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import dev.openbili.webdemo.MainViewModel
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.offline.OfflineMediaManager
import dev.openbili.webdemo.video.DanmakuWindowController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.LifecycleOwner
import dev.openbili.webdemo.AppUiState
import dev.openbili.webdemo.LoginState
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.PreferredResolutionMode
import kotlinx.coroutines.CoroutineScope

/**
 * 视频页数据上下文：页面缓存、数据提交与视频页操作集合。
 */
internal class AppRootVideoPageContext(
  val context: Context,
  val scope: CoroutineScope,
  val lifecycleOwner: LifecycleOwner,
  val videoEntryCache: LinkedHashMap<String, VideoPageEntry>,
  val danmakuWindowController: DanmakuWindowController,
  val playerPositionProvider: () -> Long,
  val playerViewModel: PlayerViewModel,
  val mainViewModel: MainViewModel,
  val playerUiPositionProvider: () -> Long,
  val videoState: AppRootVideoState,
  val playerSession: AppRootPlayerSessionState,
  val appStateState: State<AppUiState>,
  val settingsState: State<AppSettings>,
  val authUserInfoState: State<UserInfo>,
  val loginStateState: State<LoginState>,
  val playerActivationIdState: MutableState<String?>,
  val dataCommitAllowedIdState: MutableState<String?>,
  val transitionSessionState: MutableState<CardTransitionSession?>,
  val videoPageDataReadyIdState: MutableState<String?>,
  val showEmbeddedCoverState: MutableState<Boolean>,
  val transitionPhaseState: MutableState<TransitionPhase>,
  val videoStackState: MutableState<List<StackFrame>>,
  val playerBoundsState: MutableState<Rect>,
  val awaitStablePlayerBoundsRef: suspend () -> Rect,
  val commitPlaybackProgressRef: () -> Unit,
  val currentPreferredResolutionModeRef: () -> PreferredResolutionMode,
  val retainedPlaybackPageRef: (String) -> VideoPage?,
) {
  val appState by appStateState
  val settings by settingsState
  val authUserInfo by authUserInfoState
  val loginState by loginStateState
  var playerActivationId by playerActivationIdState
  var dataCommitAllowedId by dataCommitAllowedIdState
  var transitionSession by transitionSessionState
  var videoPageDataReadyId by videoPageDataReadyIdState
  var showEmbeddedCover by showEmbeddedCoverState
  var transitionPhase by transitionPhaseState
  var videoStack by videoStackState
  var playerBounds by playerBoundsState
  var videoInfo by videoState::videoInfo
  var favoriteFolders by videoState::favoriteFolders
  var favoriteFoldersLoading by videoState::favoriteFoldersLoading
  var danmakuMask by videoState::danmakuMask
  var danmaku by videoState::danmaku
  var videoEngagement by videoState::videoEngagement
  var historyAid by videoState::historyAid
  var historyCid by videoState::historyCid
  var historyDuration by videoState::historyDuration
  var historyStartTimestamp by videoState::historyStartTimestamp
  var emotes by videoState::emotes
  var emotePackages by videoState::emotePackages
  var onlineViewerText by videoState::onlineViewerText
  var videoActionBusy by videoState::videoActionBusy
  var commentSort by videoState::commentSort
  var currentPositionMs by playerSession::currentPositionMs
  var playbackEnded by playerSession::playbackEnded

  suspend fun awaitStablePlayerBounds(): Rect = awaitStablePlayerBoundsRef()
  fun commitPlaybackProgress() = commitPlaybackProgressRef()
  fun currentPreferredResolutionMode(): PreferredResolutionMode = currentPreferredResolutionModeRef()
  fun retainedPlaybackPage(itemId: String): VideoPage? = retainedPlaybackPageRef(itemId)

fun loadMentionSuggestions(query: String) {
  videoState.loadMentionSuggestions(query, authUserInfo.mid, scope)
}

fun snapshotEntry(item: FeedItem): VideoPageEntry =
  videoState.snapshotEntry(
    item,
    playerViewModel,
    currentPositionMs,
    videoPageDataReadyId,
    playbackEnded,
  )

fun cacheEntry(entry: VideoPageEntry) {
  videoState.cacheEntry(entry, appState.selectedVideo?.id)
}

fun selectCommentSort(sort: CommentSort) {
  videoState.selectCommentSort(sort, { appState.selectedVideo?.id }, context, scope)
}

fun selectVideoPage(page: VideoPage) {
  val item = appState.selectedVideo ?: return
  val info = videoInfo ?: return
  if (page.cid <= 0L || page.cid == historyCid || info.pages.none { it.cid == page.cid }) return
  commitPlaybackProgress()
  videoStack.lastOrNull()?.let { currentFrame ->
    videoStack =
      videoStack.dropLast(1) +
        currentFrame.copy(sourceCardBounds = null, inPlaceSelectionChanged = true)
  }
  playerSession.clearPlaybackEnded()
  showEmbeddedCover = true
  currentPositionMs = 0L
  historyCid = page.cid
  historyDuration = page.durationSeconds
  historyStartTimestamp = System.currentTimeMillis() / 1000L
  danmaku = emptyList()
  videoEntryCache[item.id]?.let { entry ->
    cacheEntry(
      entry.copy(
        cid = page.cid,
        durationSeconds = page.durationSeconds,
        savedPositionMs = 0L,
        danmaku = emptyList(),
        playbackEnded = false,
      )
    )
  }
  playerViewModel.loadVideo(
    item =
      if (OfflineMediaManager.isOfflineUri(item.videoUrl)) {
        item.copy(videoUrl = "https://www.bilibili.com/video/${info.bvid}")
      } else item,
    preferredResolutionMode = currentPreferredResolutionMode(),
    page = page,
  )
}

fun restoreEntry(entry: VideoPageEntry) {
  videoState.restoreEntry(
    entry,
    playerSession,
    currentAccountMid = authUserInfo.mid.takeIf { authUserInfo.isLogin } ?: 0L,
  )
  videoPageDataReadyId = entry.item.id.takeIf { entry.dataReady }
}

/** 卡片点击会重启已完成的缓存；视频栈返回则保留其终态覆盖层。 */
fun restoreEntryForFreshPlayback(entry: VideoPageEntry) {
  restoreEntry(entry)
  if (entry.playbackEnded) {
    playerSession.clearPlaybackEnded()
    playerSession.currentPositionMs = 0L
  }
}

fun clearVisibleVideoData() {
  videoState.clearVisibleVideoData(playerSession)
  videoPageDataReadyId = null
}

  // 页面数据归属其缓存条目，而不是当前可见的组合：父页面因此在推荐的子视频
  // 打开期间仍可完成加载。
  fun ensureVideoPageData(item: FeedItem) {
  videoState.ensureVideoPageData(
    item = item,
    scope = scope,
    selectedVideoId = { mainViewModel.state.value.selectedVideo?.id },
    dataCommitAllowedId = { dataCommitAllowedId },
    onRestore = ::restoreEntry,
    onDataReady = { videoPageDataReadyId = it },
  )
}

fun selectCollectionEpisode(episode: FeedItem) {
  val current = appState.selectedVideo ?: return
  if (
    episode.id == current.id ||
      transitionSession != null ||
      transitionPhase !is TransitionPhase.Video
  )
    return

  cacheEntry(snapshotEntry(current))
  commitPlaybackProgress()

  playerViewModel.cancelPendingLoad()
  playerViewModel.exoPlayer?.pause()
  val currentFrame = videoStack.lastOrNull()
  if (currentFrame != null) {
    videoStack =
      videoStack.dropLast(1) +
        currentFrame.copy(
          entryId = episode.id,
          item = episode,
          sourceCardBounds = null,
          inPlaceSelectionChanged = true,
        )
  }
  val retained = videoEntryCache[episode.id]
  if (retained != null) restoreEntry(retained) else clearVisibleVideoData()
  val requestedPage = retainedPlaybackPage(episode.id) ?: episode.playbackPage
  playerSession.clearPlaybackEnded()
  showEmbeddedCover = true
  requestedPage?.let { page ->
    historyCid = page.cid
    historyDuration = page.durationSeconds
    historyStartTimestamp = System.currentTimeMillis() / 1000L
  }
  dataCommitAllowedId = episode.id
  playerActivationId = episode.id
  transitionPhase = TransitionPhase.Video(episode, null)
  mainViewModel.replaceVideoInPlace(episode)
  ensureVideoPageData(episode)
  playerViewModel.loadVideo(
    item = episode,
    startPositionMs = retained?.savedPositionMs ?: 0L,
    preferredStreamIndex = retained?.qualityIndex,
    preferredResolutionMode = currentPreferredResolutionMode(),
    page = requestedPage,
    restoreSavedProgress = retained?.playbackEnded != true,
  )
}

}

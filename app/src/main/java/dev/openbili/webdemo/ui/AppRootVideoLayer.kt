package dev.openbili.webdemo.ui

/**
 * 视频层上下文与组合体：视频页/番剧页的渲染、根进入背景、播放器封面交接与
 * 弹幕层级约束。注意 VideoLayer() 必须位于根 Box 内、RootPlayerLayer 之前。
 */

import android.content.Context
import android.widget.Toast
import dev.openbili.webdemo.api.DanmakuItem
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.graphicsLayer
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliDanmakuApi
import dev.openbili.webdemo.feed.LocalCoverImageLoadingEnabled
import dev.openbili.webdemo.offline.OfflineMediaManager
import dev.openbili.webdemo.video.BangumiPageUi
import dev.openbili.webdemo.video.buildControllerPlaybackMenu
import dev.openbili.webdemo.video.ControllerFullscreenPlaybackScreen
import dev.openbili.webdemo.video.feedItemFromCollectionEpisode
import dev.openbili.webdemo.video.playableEpisodes
import dev.openbili.webdemo.video.DanmakuWindowController
import dev.openbili.webdemo.video.VideoPlaybackPageKind
import dev.openbili.webdemo.video.VideoScreen
import dev.openbili.webdemo.video.resolveVideoPlaybackPageKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import dev.openbili.webdemo.AppUiState
import dev.openbili.webdemo.AuthViewModel
import dev.openbili.webdemo.MainViewModel
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.PlayerSubtitleState
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.CommentNavigationTarget
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.article.ArticleOrigin
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.AppSettingsViewModel
import dev.openbili.webdemo.settings.PreferredResolutionMode
import dev.openbili.webdemo.video.CommentProfileAnchor
import kotlinx.coroutines.CoroutineScope

/**
 * 视频层上下文。
 */
internal class AppRootVideoContext(
  // 环境
  val context: Context,
  val scope: CoroutineScope,
  val settings: AppSettings,
  val settingsViewModel: AppSettingsViewModel,
  val authViewModel: AuthViewModel,
  val playerViewModel: PlayerViewModel,
  val mainViewModel: MainViewModel,
  // 共享对象
  val videoState: AppRootVideoState,
  val playerSession: AppRootPlayerSessionState,
  val danmakuWindowController: DanmakuWindowController,
  val profileState: AppRootProfileState,
  val videoEntryCache: LinkedHashMap<String, VideoPageEntry>,
  val playerViewHolder: Array<HeldPlayerView?>,
  val rootPlayerContent: @Composable (Modifier, Float, Boolean, Boolean, SharedPlayerViewRole, Boolean?, Boolean) -> Unit,
  val playerPositionProvider: () -> Long,
  val playerUiPositionProvider: () -> Long,
  // 收集到的值
  val appState: AppUiState,
  val userInfo: UserInfo,
  val authUserInfo: UserInfo,
  val playerState: PlayerState,
  val subtitleState: PlayerSubtitleState,
  val renderedVideoId: String?,
  val renderedVideoFrameCount: Int,
  val networkAvailable: Boolean,
  // 计算值
  val showVideo: Boolean,
  val activeSession: CardTransitionSession?,
  val transitionVisualsActive: Boolean,
  val rootPlayerHostEnabled: Boolean,
  val bangumiDetailPlayerSuppressed: Boolean,
  val rootEnterSession: CardTransitionSession?,
  val profileEnterSession: CardTransitionSession?,
  val preparingRootEnter: Boolean,
  val bangumiHomeTransitionSession: CardTransitionSession?,
  val deferVideoCommentContent: Boolean,
  val deferVideoAuxiliaryContent: Boolean,
  // 状态持有器
  val activeBangumiPageState: MutableState<ActiveBangumiPage?>,
  val videoExitPreludeState: MutableState<VideoExitPrelude?>,
  val bangumiPosterBoundsState: MutableState<Rect>,
  val deferSearchBangumiPageCompositionState: MutableState<Boolean>,
  val deferBangumiIndexPageCompositionState: MutableState<Boolean>,
  val deferBangumiHomePageCompositionState: MutableState<Boolean>,
  val hiddenRecommendationCoverItemIdState: MutableState<String?>,
  val hiddenPlaybackEndRecommendationCoverItemIdState: MutableState<String?>,
  val commentImagePreviewActiveState: MutableState<Boolean>,
  val videoFullscreenTransitionActiveState: MutableState<Boolean>,
  val videoPageDataReadyIdState: MutableState<String?>,
  val directHomeInProgressState: MutableState<Boolean>,
  val playerBoundsState: MutableState<Rect>,
  val showEmbeddedCoverState: MutableState<Boolean>,
  val rootPlayerOwnershipState: MutableState<RootPlayerOwnership>,
  val videoStackState: MutableState<List<StackFrame>>,
  val articleSuspendedVideoState: MutableState<SuspendedArticleVideo?>,
  val pendingVideoCommentTargetState: MutableState<CommentNavigationTarget?>,
  val hiddenVideoCommentArticleItemIdState: MutableState<String?>,
  val transitionPhaseState: MutableState<TransitionPhase>,
  val transitionSessionState: MutableState<CardTransitionSession?>,
  val playerActivationIdState: MutableState<String?>,
  val profileLayerSuppressedState: MutableState<Boolean>,
  val directHomeAlpha: Animatable<Float, AnimationVector1D>,
  // 外部函数
  val reverseActiveEnterRef: () -> Unit,
  val cancelPreparingProfileVideoRef: () -> Unit,
  val cancelPreparingRootEnterRef: () -> Unit,
  val startExitBangumiRef: () -> Unit,
  val startExitVideoToProfileRef: () -> Unit,
  val startBackToPreviousVideoRef: () -> Unit,
  val startExitVideoRef: () -> Unit,
  val returnDirectlyHomeRef: () -> Unit,
  val startRecommendedVideoRef: (FeedItem, FeedItem, Rect, Rect?, Boolean) -> Unit,
  val selectCollectionEpisodeRef: (FeedItem) -> Unit,
  val showVideoPreviewRef: (FeedItem) -> Unit,
  val startEnterArticleRef: (ArticleItem, Rect?, ArticleOrigin) -> Unit,
  val cacheEntryRef: (VideoPageEntry) -> Unit,
  val snapshotEntryRef: (FeedItem) -> VideoPageEntry,
  val currentPreferredResolutionModeRef: () -> PreferredResolutionMode,
  val openAvatarProfileRef: (Long, Rect, String?, String?) -> Unit,
  val selectFollowingGroupRef: (Long, Long) -> Unit,
  val unfollowRef: (Long) -> Unit,
  val openCommentProfileRef: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  val setTemporarySpeedBoostRef: (Boolean) -> Unit,
  val setPlaybackSpeedRef: (Float) -> Unit,
  val commitSeekRef: (Long) -> Unit,
  val previewSeekRef: (Long) -> Unit,
  val cancelSeekPreviewRef: () -> Unit,
  val selectVideoPageRef: (VideoPage) -> Unit,
  val selectCommentSortRef: (CommentSort) -> Unit,
  val loadFollowingGroupsRef: () -> Unit,
  val loadMentionSuggestionsRef: (String) -> Unit,
  val selectBangumiEpisodeRef: (BangumiEpisode) -> Unit,
  val selectBangumiSeasonRef: (Long) -> Unit,
  val toggleBangumiFollowRef: () -> Unit,
  val postBangumiShortReviewRef: (Int, String) -> Unit,
) {
  var activeBangumiPage by activeBangumiPageState
  var videoExitPrelude by videoExitPreludeState
  var bangumiPosterBounds by bangumiPosterBoundsState
  var deferSearchBangumiPageComposition by deferSearchBangumiPageCompositionState
  var deferBangumiIndexPageComposition by deferBangumiIndexPageCompositionState
  var deferBangumiHomePageComposition by deferBangumiHomePageCompositionState
  var hiddenRecommendationCoverItemId by hiddenRecommendationCoverItemIdState
  var hiddenPlaybackEndRecommendationCoverItemId by hiddenPlaybackEndRecommendationCoverItemIdState
  var commentImagePreviewActive by commentImagePreviewActiveState
  var videoFullscreenTransitionActive by videoFullscreenTransitionActiveState
  var videoPageDataReadyId by videoPageDataReadyIdState
  var directHomeInProgress by directHomeInProgressState
  var playerBounds by playerBoundsState
  var showEmbeddedCover by showEmbeddedCoverState
  var rootPlayerOwnership by rootPlayerOwnershipState
  var videoStack by videoStackState
  var articleSuspendedVideo by articleSuspendedVideoState
  var pendingVideoCommentTarget by pendingVideoCommentTargetState
  var hiddenVideoCommentArticleItemId by hiddenVideoCommentArticleItemIdState
  var transitionPhase by transitionPhaseState
  var transitionSession by transitionSessionState
  var playerActivationId by playerActivationIdState
  var profileLayerSuppressed by profileLayerSuppressedState
  var commentTotalCount by videoState::commentTotalCount
  var commentsRefreshing by videoState::commentsRefreshing
  var commentsLoading by videoState::commentsLoading
  var commentHasMore by videoState::commentHasMore
  var commentSort by videoState::commentSort
  var commentItems by videoState::commentItems
  var replyRoot by videoState::replyRoot
  var replyItems by videoState::replyItems
  var replyHasMore by videoState::replyHasMore
  var repliesLoading by videoState::repliesLoading
  var danmaku by videoState::danmaku
  var videoRecommendations by videoState::videoRecommendations
  var videoDescription by videoState::videoDescription
  var videoInfo by videoState::videoInfo
  var videoEngagement by videoState::videoEngagement
  var favoriteFolders by videoState::favoriteFolders
  var favoriteFoldersLoading by videoState::favoriteFoldersLoading
  var onlineViewerText by videoState::onlineViewerText
  var emotes by videoState::emotes
  var emotePackages by videoState::emotePackages
  var mentionSuggestions by videoState::mentionSuggestions
  var mentionSuggestionsLoading by videoState::mentionSuggestionsLoading
  var historyAid by videoState::historyAid
  var historyCid by videoState::historyCid
  var historyDuration by videoState::historyDuration
  var currentPositionMs by playerSession::currentPositionMs
  var scrubPreviewMs by playerSession::scrubPreviewMs
  var pendingSeekTargetMs by playerSession::pendingSeekTargetMs
  var seekWasPlaying by playerSession::seekWasPlaying
  var isPlaying by playerSession::isPlaying
  var isBuffering by playerSession::isBuffering
  var showDanmaku by playerSession::showDanmaku
  var playerReady by playerSession::playerReady
  var playerControlsVisible by playerSession::playerControlsVisible
  var playbackEnded by playerSession::playbackEnded
  var playbackSpeed by playerSession::playbackSpeed
  var profileMid by profileState::profileMid
  var commentProfileTransition by profileState::commentProfileTransition
  var followingGroups by profileState::followingGroups
  var followingGroupsLoading by profileState::followingGroupsLoading
  val followingStates = profileState.followingStates
  val followingBusy = profileState.followingBusy

  fun reverseActiveEnter() = reverseActiveEnterRef()
  fun cancelPreparingProfileVideo() = cancelPreparingProfileVideoRef()
  fun cancelPreparingRootEnter() = cancelPreparingRootEnterRef()
  fun startExitBangumi() = startExitBangumiRef()
  fun startExitVideoToProfile() = startExitVideoToProfileRef()
  fun startBackToPreviousVideo() = startBackToPreviousVideoRef()
  fun startExitVideo() = startExitVideoRef()
  fun returnDirectlyHome() = returnDirectlyHomeRef()
  fun startRecommendedVideo(
    current: FeedItem,
    recommendation: FeedItem,
    bounds: Rect,
    returnBounds: Rect?,
    fromPlaybackEnd: Boolean,
  ) = startRecommendedVideoRef(current, recommendation, bounds, returnBounds, fromPlaybackEnd)
  fun selectCollectionEpisode(episode: FeedItem) = selectCollectionEpisodeRef(episode)
  fun showVideoPreview(item: FeedItem) = showVideoPreviewRef(item)
  fun startEnterArticle(article: ArticleItem, sourceBounds: Rect?, origin: ArticleOrigin) =
    startEnterArticleRef(article, sourceBounds, origin)
  fun cacheEntry(entry: VideoPageEntry) = cacheEntryRef(entry)
  fun snapshotEntry(item: FeedItem): VideoPageEntry = snapshotEntryRef(item)
  fun currentPreferredResolutionMode(): PreferredResolutionMode = currentPreferredResolutionModeRef()
  fun openAvatarProfile(mid: Long, bounds: Rect, face: String?, name: String?) =
    openAvatarProfileRef(mid, bounds, face, name)
  fun selectFollowingGroup(mid: Long, groupId: Long) = selectFollowingGroupRef(mid, groupId)
  fun unfollow(mid: Long) = unfollowRef(mid)
  fun openCommentProfile(mid: Long, comment: CommentItem, anchor: CommentProfileAnchor) =
    openCommentProfileRef(mid, comment, anchor)
  fun setTemporarySpeedBoost(active: Boolean) = setTemporarySpeedBoostRef(active)
  @JvmName("setPlaybackSpeedImpl")
  fun setPlaybackSpeed(speed: Float) = setPlaybackSpeedRef(speed)
  fun commitSeek(targetMs: Long) = commitSeekRef(targetMs)
  fun previewSeek(targetMs: Long) = previewSeekRef(targetMs)
  fun cancelSeekPreview() = cancelSeekPreviewRef()
  fun selectVideoPage(page: VideoPage) = selectVideoPageRef(page)
  fun selectCommentSort(sort: CommentSort) = selectCommentSortRef(sort)
  fun loadFollowingGroups() = loadFollowingGroupsRef()
  fun loadMentionSuggestions(query: String) = loadMentionSuggestionsRef(query)
  fun selectBangumiEpisode(episode: BangumiEpisode) = selectBangumiEpisodeRef(episode)
  fun selectBangumiSeason(seasonId: Long) = selectBangumiSeasonRef(seasonId)
  fun toggleBangumiFollow() = toggleBangumiFollowRef()
  fun postBangumiShortReview(score: Int, content: String) = postBangumiShortReviewRef(score, content)

  @Composable
  fun VideoLayer() {

  if (showVideo) {
    val item = appState.selectedVideo!!
    val playbackPageKind =
      resolveVideoPlaybackPageKind(
        controlMode = LocalControlMode.current,
        controllerTouchPlaybackPage = settings.controllerTouchPlaybackPage,
        alwaysControllerPlaybackPage = settings.alwaysControllerPlaybackPage,
      )
    val enteringVideoPage =
      activeSession?.kind in
        setOf(
          TransitionKind.ENTER_ROOT,
          TransitionKind.ENTER_RECOMMENDATION,
          TransitionKind.ENTER_PROFILE,
        ) && activeSession?.phase != SessionPhase.COMPLETED
    var playbackCoverFrameGateReady by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id, enteringVideoPage) {
      if (enteringVideoPage) {
        playbackCoverFrameGateReady = false
      } else {
        playbackCoverFrameGateReady = false
        playerViewModel.resetRenderedVideoFrameCountForPageEntry()
        withFrameNanos {}
        playbackCoverFrameGateReady = true
      }
    }
    val bangumiPageUi = activeBangumiPage?.let { page ->
      BangumiPageUi(
        sourceCard = page.sourceCard,
        season = page.season,
        loading = page.loading,
        error = page.error,
        currentEpisodeId = page.currentEpisodeId,
        posterVisible =
          transitionSession?.let { session ->
            val enteringPosterFlight =
              session.fitCover &&
                session.kind in
                  setOf(
                    TransitionKind.ENTER_ROOT,
                    TransitionKind.ENTER_RECOMMENDATION,
                    TransitionKind.ENTER_PROFILE,
                  )
            !enteringPosterFlight ||
              session.phase in
                setOf(
                  SessionPhase.WAITING_FIRST_FRAME,
                  SessionPhase.REVEALING,
                  SessionPhase.COMPLETED,
                )
          } ?: true,
        followBusy = page.followBusy,
      )
    }
    val showSearchBangumiTransitionTarget =
      (deferSearchBangumiPageComposition || deferBangumiIndexPageComposition) &&
        activeBangumiPage?.sourceOrigin in setOf(PageOrigin.Search, PageOrigin.BangumiIndex) &&
        activeSession?.kind == TransitionKind.ENTER_ROOT
    val showProfileBangumiTransitionTarget =
      activeBangumiPage?.let { page ->
        shouldUseProfileBangumiTransitionTarget(
          sourceProfileEntryId = page.sourceProfileEntryId,
          kind = activeSession?.kind,
          phase = activeSession?.phase,
        )
      } == true
    val showBangumiHomeTransitionTarget =
      deferBangumiHomePageComposition &&
        activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome &&
        activeSession?.kind == TransitionKind.ENTER_ROOT
    val useRootVideoEntryBackdrop = shouldUseRootVideoEntryBackdrop(kind = activeSession?.kind)
    if (useRootVideoEntryBackdrop && rootEnterSession != null) {
      RootVideoEntryBackdrop(
        settings = settings,
        playerBounds = playerBounds,
        revealAlpha = { rootEnterSession.backgroundAlpha.value },
        // 背景可填满 PGC 播放器周围的页面，但绝不能画到真实 SurfaceView 上。
        // 搜索/索引进入的海报阶段还没有播放器边界，因此遮罩在测量到 portal 前
        // 保持全屏。
        punchPlayerPortal = activeBangumiPage == null || playerBounds.hasUsableSize(),
      )
    }
    Box(
      Modifier.fillMaxSize().graphicsLayer {
        val pageAlpha =
          when {
            preparingRootEnter ||
              shouldHideVideoPageBehindExitCover(activeSession?.kind, activeSession?.phase) -> 0f
            rootEnterSession != null && useRootVideoEntryBackdrop ->
              rootVideoEntryContentAlpha(
                phase = rootEnterSession.phase,
                backgroundRevealProgress = rootEnterSession.backgroundAlpha.value,
              )
            rootEnterSession != null -> rootEnterSession.backgroundAlpha.value.coerceIn(0f, 1f)
            profileEnterSession != null ->
              profileEnterSession.backgroundAlpha.value.coerceIn(0f, 1f)
            else -> 1f
          }
        alpha = pageAlpha * directHomeAlpha.value
      }
    ) {
      if (showSearchBangumiTransitionTarget || showProfileBangumiTransitionTarget) {
        BangumiPosterTransitionTarget { bounds ->
          if (bounds.hasUsableSize()) bangumiPosterBounds = bounds
        }
      } else if (showBangumiHomeTransitionTarget) {
        BangumiPlayerTransitionTarget { bounds ->
          if (bounds.hasUsableSize()) playerBounds = bounds
        }
      } else {
        CompositionLocalProvider(
          LocalCoverImageLoadingEnabled provides (bangumiHomeTransitionSession == null)
        ) {
          val handlePlaybackBack = {
            if (directHomeInProgress) Unit
            else if (appState.video.isFullscreen) mainViewModel.onFullscreenChanged(false)
            else if (
              transitionSession?.kind == TransitionKind.ENTER_ROOT ||
                transitionSession?.kind == TransitionKind.ENTER_RECOMMENDATION ||
                transitionSession?.kind == TransitionKind.ENTER_PROFILE
            )
              reverseActiveEnter()
            else if (transitionSession == null && transitionPhase is TransitionPhase.ToVideo)
              if (videoStack.lastOrNull()?.parentPage is PageOrigin.Profile)
                cancelPreparingProfileVideo()
              else cancelPreparingRootEnter()
            else if (transitionPhase is TransitionPhase.Video)
              if (activeBangumiPage != null) startExitBangumi()
              else if (videoStack.lastOrNull()?.parentPage is PageOrigin.Profile)
                startExitVideoToProfile()
              else if (videoStack.size > 1) startBackToPreviousVideo() else startExitVideo()
          }
          fun replayCurrentVideo() {
            val replayItem = appState.selectedVideo
            playerSession.clearPlaybackEnded()
            if (replayItem != null) {
              val retained = videoEntryCache[replayItem.id]
              retained?.let {
                cacheEntry(it.copy(savedPositionMs = 0L, playbackEnded = false))
              }
              playerSession.currentPositionMs = 0L
              if (!playerViewModel.replayIfLoaded(replayItem.id)) {
                // 已完成页面重播时保留当前页的选集与清晰度，只重置播放进度。
                playerActivationId = replayItem.id
                rootPlayerOwnership =
                  RootPlayerOwnership(RootPlayerSurfaceRole.DETAIL_PENDING, replayItem.id)
                showEmbeddedCover = true
                val replayPage = videoInfo?.pages?.firstOrNull { it.cid == historyCid }
                playerViewModel.loadVideo(
                  item = replayItem,
                  startPositionMs = 0L,
                  preferredStreamIndex = retained?.qualityIndex,
                  preferredResolutionMode = currentPreferredResolutionMode(),
                  page = replayPage,
                  restoreSavedProgress = false,
                )
              }
            }
          }
          if (playbackPageKind == VideoPlaybackPageKind.CONTROLLER_FULLSCREEN) {
            val controllerMenu =
              buildControllerPlaybackMenu(
                bangumiPage = bangumiPageUi,
                videoInfo = videoInfo,
                playerState = playerState,
                subtitleState = subtitleState,
                playbackSpeed = playbackSpeed,
              )
            ControllerFullscreenPlaybackScreen(
              item = item,
              playerState = playerState,
              playerReady = playerReady,
              renderedVideoId = renderedVideoId,
              renderedVideoFrameCount = renderedVideoFrameCount,
              isBuffering = isBuffering,
              keepScreenOn = settings.keepScreenOn || LocalControlMode.current,
              currentPositionMs = playerPositionProvider,
              durationMs = historyDuration * 1000,
              isPlaying = isPlaying,
              title = videoInfo?.title ?: item.title,
              selectionItems = controllerMenu.selectionItems,
              selectionGroups = controllerMenu.selectionGroups,
              moreItems = controllerMenu.moreItems,
              qualityItems = controllerMenu.qualityItems,
              subtitleItems = controllerMenu.subtitleItems,
              showDanmaku = showDanmaku,
              settings = settings,
              onSettingsChange = settingsViewModel::update,
              onToggleDanmaku = {
                val enabled = !showDanmaku
                showDanmaku = enabled
                settingsViewModel.update { it.copy(defaultShowDanmaku = enabled) }
              },
              playbackEnded = playbackEnded,
              recommendations = videoRecommendations,
              hiddenPlaybackEndRecommendationCoverItemId = hiddenPlaybackEndRecommendationCoverItemId,
              onReplay = ::replayCurrentVideo,
              onRecommendationClick = { recommendation, bounds ->
                startRecommendedVideo(item, recommendation, bounds, bounds, true)
              },
              onRecommendationLongClick = ::showVideoPreview,
              onControllerAction = { action ->
                when {
                  action.key.startsWith("bangumi:") -> {
                    val episodeId = action.key.substringAfter(':').toLongOrNull()
                    bangumiPageUi?.playableEpisodes()?.firstOrNull { it.id == episodeId }?.let(::selectBangumiEpisode)
                  }
                  action.key.startsWith("page:") -> {
                    val pageNumber = action.key.substringAfter(':').toIntOrNull()
                    videoInfo?.pages?.firstOrNull { it.page == pageNumber }?.let(::selectVideoPage)
                  }
                  action.key.startsWith("collection:") -> {
                    val payload = action.key.substringAfter(':')
                    val bvid = payload.substringBeforeLast(':')
                    val cid = payload.substringAfterLast(':').toLongOrNull() ?: 0L
                    val episodes =
                      videoInfo?.collection?.let { collection ->
                        collection.episodes + collection.sections.flatMap { it.episodes }
                      }.orEmpty()
                    episodes
                      .firstOrNull { it.bvid == bvid && (cid <= 0L || it.cid == cid) }
                      ?.let { selectCollectionEpisode(feedItemFromCollectionEpisode(it)) }
                  }
                  action.key.startsWith("quality:") ->
                    action.key.substringAfter(':').toIntOrNull()?.let(playerViewModel::switchQuality)
                  action.key.startsWith("speed:") ->
                    action.key.substringAfter(':').toFloatOrNull()?.let(::setPlaybackSpeed)
                  action.key.startsWith("subtitle:") ->
                    playerViewModel.selectSubtitle(action.key.substringAfter(':').takeUnless { it == "off" }.orEmpty())
                  else -> Unit
                }
              },
              onBack = handlePlaybackBack,
              onTogglePlayPause = {
                val p = playerViewModel.exoPlayer
                if (p != null) {
                  if (p.isPlaying) p.pause() else p.play()
                }
              },
              onSeek = ::commitSeek,
              onRetryPlayback = {
                playerSession.clearPlaybackEnded()
                playerViewModel.retry()
              },
              onRetryNextQuality = {
                playerSession.clearPlaybackEnded()
                playerViewModel.retryWithNextQuality()
              },
              onPlayerBoundsChanged = { bounds ->
                if (bounds.width > 0f && bounds.height > 0f) playerBounds = bounds
              },
              playerView = { modifier ->
                if (playerReady && !rootPlayerHostEnabled && !bangumiDetailPlayerSuppressed) {
                  rootPlayerContent(
                    modifier,
                    1f,
                    true,
                    true,
                    SharedPlayerViewRole.DETAIL,
                    true,
                    true,
                  )
                }
              },
            )
          } else {
            VideoScreen(
            item = item,
            description = videoDescription,
            videoInfo = videoInfo,
            currentCid = historyCid,
            videoEngagement = videoEngagement,
            favoriteFolders = favoriteFolders,
            favoriteFoldersLoading = favoriteFoldersLoading,
            showCoverUntilFirstFrame = showEmbeddedCover && renderedVideoId != item.id,
            renderedVideoId = renderedVideoId,
            renderedVideoFrameCount = renderedVideoFrameCount,
            playbackCoverFrameGateReady = playbackCoverFrameGateReady,
            onlineViewerText = onlineViewerText,
            playerState = playerState,
            subtitleState = subtitleState,
            danmaku = danmaku,
            danmakuPaused = transitionVisualsActive,
            commentItems = commentItems,
            commentTotalCount = commentTotalCount,
            commentHasMore = commentHasMore,
            commentsLoading = commentsLoading,
            commentSort = commentSort,
            commentsRefreshing = commentsRefreshing,
            pageContentLoading = videoPageDataReadyId != item.id,
            deferAuxiliaryContent = deferVideoAuxiliaryContent,
            deferCommentContent = deferVideoCommentContent,
            currentAccountMid = authUserInfo.mid,
            currentAccountVipActive = authUserInfo.vipActive,
            hiddenCommentAvatarRpid =
              commentProfileTransition
                ?.takeIf { it.sourceAvatarBounds != null }
                ?.sourceComment
                ?.rpid,
            commentNavigationTarget = pendingVideoCommentTarget,
            replyRoot = replyRoot,
            replyItems = replyItems,
            replyHasMore = replyHasMore,
            repliesLoading = repliesLoading,
            emotes = emotes,
            emotePackages = emotePackages,
            mentionSuggestions = mentionSuggestions,
            mentionSuggestionsLoading = mentionSuggestionsLoading,
            recommendations = videoRecommendations,
            hiddenRecommendationCoverItemId = hiddenRecommendationCoverItemId,
            hiddenPlaybackEndRecommendationCoverItemId =
              hiddenPlaybackEndRecommendationCoverItemId,
            commentMediaBounds = videoState.commentMediaBounds,
            hiddenLinkedArticleItemId = hiddenVideoCommentArticleItemId,
            currentPositionMs = playerUiPositionProvider,
            durationMs = historyDuration * 1000,
            playerPositionProvider = playerPositionProvider,
            isPlaying =
              if (scrubPreviewMs != null || pendingSeekTargetMs != null) seekWasPlaying
              else isPlaying,
            isBuffering = isBuffering,
            playbackEnded = playbackEnded,
            playbackSpeed = playbackSpeed,
            showDanmaku = showDanmaku,
            danmakuComposerEnabled =
              !OfflineMediaManager.isOfflineUri(item.videoUrl) || networkAvailable,
            isFullscreen = appState.video.isFullscreen,
            isPlaybackPageForeground =
              isVideoPageForeground(
                videoScreenVisible = appState.isVideoScreen,
                profileVisible = profileMid != null,
                // 番剧详情页仍是视频页，但从其评论打开的资料页可见期间，它不能
                // 声称自己在前台。
                profileSuppressed = profileLayerSuppressed,
              ) && articleSuspendedVideo == null,
            pageExitAlpha = { videoExitPrelude?.pageAlpha?.value ?: 1f },
            retainBackgroundDuringPageExit = transitionPhase is TransitionPhase.ToPreviousVideo,
            playerControlsVisible = playerControlsVisible,
            onPlayerControlsVisibilityChanged = { playerControlsVisible = it },
            panelSlideProgress = {
              transitionSession?.let { session ->
                when (session.kind) {
                  TransitionKind.EXIT_ROOT,
                  TransitionKind.EXIT_RECOMMENDATION,
                  TransitionKind.EXIT_PROFILE -> 1f - session.panelAlpha.value
                  else -> session.panelAlpha.value
                }
              } ?: 1f
            },
            settings = settings,
            onSettingsChange = settingsViewModel::update,
            onFullscreenChanged = mainViewModel::onFullscreenChanged,
            onFullscreenTransitionChanged = { active ->
              if (active) playerViewHolder[0]?.view?.hideDanmakuForTransition()
              videoFullscreenTransitionActive = active
            },
            onBack = handlePlaybackBack,
            onHome = {
              // returnDirectlyHome 同步提交：在资料页重挂载期间保持 PGC 页面身份
              // 存活。
              returnDirectlyHome()
              activeBangumiPage = null
              deferSearchBangumiPageComposition = false
              deferBangumiIndexPageComposition = false
              deferBangumiHomePageComposition = false
              bangumiPosterBounds = Rect.Zero
            },
            onTogglePlayPause = {
              val p = playerViewModel.exoPlayer
              if (p != null) {
                if (p.isPlaying) p.pause() else p.play()
              }
            },
            onTemporarySpeedChanged = ::setTemporarySpeedBoost,
            onPlaybackSpeedChanged = ::setPlaybackSpeed,
            onRetryPlayback = {
              playerSession.clearPlaybackEnded()
              playerViewModel.retry()
            },
            onRetryNextQuality = {
              playerSession.clearPlaybackEnded()
              playerViewModel.retryWithNextQuality()
            },
            onReplay = ::replayCurrentVideo,
            onSeek = ::commitSeek,
            onSeekPreview = ::previewSeek,
            onSeekCancel = ::cancelSeekPreview,
            onToggleDanmaku = {
              val enabled = !showDanmaku
              showDanmaku = enabled
              settingsViewModel.update { it.copy(defaultShowDanmaku = enabled) }
            },
            onSendDanmaku = { message, color, mode, fontSize, colorful ->
              if (historyCid > 0) {
                val position = playerViewModel.exoPlayer?.currentPosition ?: 0L
                scope.launch {
                  val result =
                    withContext(Dispatchers.IO) {
                      runCatching {
                        BiliDanmakuApi.sendDanmakuAuthenticated(
                          cid = historyCid,
                          aid = historyAid,
                          bvid =
                            item.id.takeIf { it.startsWith("BV") }
                              ?: item.videoUrl
                                .substringAfterLast("/")
                                .substringBefore("?")
                                .takeIf { it.startsWith("BV") }
                                .orEmpty(),
                          message = message,
                          progressMs = position,
                          color = color,
                          mode = mode,
                          fontSize = fontSize,
                          colorful = colorful,
                        )
                      }
                    }
                  if (result.isSuccess) {
                    val local =
                      DanmakuItem(
                        timeMs = position,
                        type = mode,
                        fontSize = fontSize,
                        color = color,
                        content = message,
                        isLocal = true,
                        colorful = colorful,
                      )
                    danmakuWindowController.addLocalDanmaku(historyCid, local)?.let {
                      danmaku = it
                    }
                  } else {
                    Toast.makeText(
                        context,
                        result.exceptionOrNull()?.message ?: "弹幕发送失败",
                        Toast.LENGTH_SHORT,
                      )
                      .show()
                  }
                }
              }
            },
            onRecommendationClick = { rec, bounds, returnBounds, fromPlaybackEnd ->
              startRecommendedVideo(item, rec, bounds, returnBounds, fromPlaybackEnd)
            },
            onVideoPageSelected = ::selectVideoPage,
            onCollectionEpisodeSelected = { episode, _ -> selectCollectionEpisode(episode) },
            onLoadMoreComments = {
              videoState.loadMoreComments(
                item = item,
                selectedVideoUrl = { mainViewModel.state.value.selectedVideo?.videoUrl },
                scope = scope,
              )
            },
            onRefreshComments = {
              videoState.refreshComments(
                item = item,
                selectedVideoId = { appState.selectedVideo?.id },
                scope = scope,
              )
            },
            onPostComment = { message, imageUri ->
              videoState.postComment(context, message, imageUri, scope)
            },
            onRecommendationLongClick = { showVideoPreview(it) },
            onArticleClick = { article, bounds ->
              startEnterArticle(
                article,
                bounds.takeIf(Rect::hasUsableSize),
                ArticleOrigin.VIDEO,
              )
            },
            onPostReply = { root, parent, message, imageUri ->
              videoState.postReply(context, root, parent, message, imageUri, scope)
            },
            onLikeComment = { comment -> videoState.toggleCommentLike(comment, scope) },
            onDeleteComment = { comment ->
              videoState.deleteComment(context, comment, scope) {
                appState.selectedVideo?.let { cacheEntry(snapshotEntry(it)) }
              }
            },
            onToggleCommentPin = { comment ->
              videoState.toggleCommentPin(context, comment, scope) {
                appState.selectedVideo?.let { cacheEntry(snapshotEntry(it)) }
              }
            },
            onLikeVideo = { targetLiked ->
              videoState.setVideoLike(context, targetLiked, scope)
            },
            onCoinVideo = { count, alsoLike ->
              videoState.coinVideo(context, count, alsoLike, scope)
            },
            onFavoriteVideo = { addIds, removeIds ->
              videoState.setFavoriteFolders(context, addIds, removeIds, scope)
            },
            onLoadFavoriteFolders = {
              videoState.loadFavoriteFolders(context, authUserInfo.mid, scope)
            },
            onPlayerBoundsChanged = { bounds ->
              if (bounds.width > 0f && bounds.height > 0f) playerBounds = bounds
            },
            onOpenReplies = { comment -> videoState.openReplies(comment, scope) },
            onLoadMoreReplies = { videoState.loadMoreReplies(scope) },
            onRefreshReplies = { videoState.refreshReplies(scope) },
            onDismissReplies = { replyRoot = null },
            onCommentNavigationConsumed = { pendingVideoCommentTarget = null },
            onProfileClick = { mid, face, name, bounds ->
              openAvatarProfile(mid, bounds, face, name)
            },
            onUploaderProfileClick = { mid, face, name, bounds ->
              openAvatarProfile(mid, bounds, face, name)
            },
            onCommentSort = ::selectCommentSort,
            showUploaderFollowButton =
              (videoInfo?.uploaderMid ?: item.uploaderMid).let {
                it > 0L && it != authUserInfo.mid
              },
            uploaderFollowed =
              followingStates[videoInfo?.uploaderMid ?: item.uploaderMid] == true,
            uploaderFollowBusy =
              followingBusy[videoInfo?.uploaderMid ?: item.uploaderMid] == true,
            followingGroups = followingGroups,
            followingGroupsLoading = followingGroupsLoading,
            loggedIn = authUserInfo.isLogin,
            premiumAudioVisible = authUserInfo.vipActive,
            commentImageEnabled = authUserInfo.vipActive || userInfo.vipActive,
            onLoadFollowingGroups = ::loadFollowingGroups,
            onSelectUploaderFollowingGroup = { groupId ->
              selectFollowingGroup(videoInfo?.uploaderMid ?: item.uploaderMid, groupId)
            },
            onUnfollowUploader = { unfollow(videoInfo?.uploaderMid ?: item.uploaderMid) },
            onLogin = authViewModel::startLogin,
            onCommentProfileClick = { mid, comment, anchor ->
              // 三星平板上弹幕是独立顶层 Surface：在点击路径上清除它，避免它在
              // 重新组合前把一帧残留盖在资料页之上。
              playerViewHolder[0]?.view?.hideDanmakuForTransition()
              openCommentProfile(mid, comment, anchor)
            },
            onMentionQuery = ::loadMentionSuggestions,
            onCommentImagePreviewActiveChanged = { commentImagePreviewActive = it },
            playerView = { modifier, fullscreenProgress, fullscreen ->
              if (playerReady && !rootPlayerHostEnabled && !bangumiDetailPlayerSuppressed) {
                rootPlayerContent(
                  modifier,
                  fullscreenProgress,
                  fullscreen,
                  true,
                  SharedPlayerViewRole.DETAIL,
                  true,
                  true,
                )
              }
            },
            onSwitchQuality = { idx -> playerViewModel.switchQuality(idx) },
            onSwitchPremiumAudio = playerViewModel::switchPremiumAudio,
            onSelectSubtitle = playerViewModel::selectSubtitle,
            bangumiPage = bangumiPageUi,
            onBangumiPosterBoundsChanged = { bounds ->
              if (bounds.hasUsableSize()) bangumiPosterBounds = bounds
            },
            onBangumiEpisodeSelected = ::selectBangumiEpisode,
            onBangumiSeasonSelected = ::selectBangumiSeason,
            onBangumiFollow = ::toggleBangumiFollow,
            onBangumiRate = ::postBangumiShortReview,
            )
          }
        }
      }
    }
  }
  }
}

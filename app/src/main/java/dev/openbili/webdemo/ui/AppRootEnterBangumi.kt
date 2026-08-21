package dev.openbili.webdemo.ui

/**
 * 视频/番剧进入导航上下文：从首页、搜索、番剧首页、历史、资料页等来源进入视频或
 * 番剧页，负责进入转场的分阶段挂载（海报先行、评论/推荐延后）与返回路由保存。
 */

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.SoftwareKeyboardController
import dev.openbili.webdemo.AppUiState
import dev.openbili.webdemo.AuthViewModel
import dev.openbili.webdemo.MainViewModel
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.CommentNavigationTarget
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.bangumi.BangumiExploreViewModel
import dev.openbili.webdemo.BangumiPlaybackStore
import dev.openbili.webdemo.api.BiliBangumiApi
import dev.openbili.webdemo.offline.OfflineMediaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedScrollAnchor
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.PreferredResolutionMode
import kotlinx.coroutines.CoroutineScope

/**
 * 视频/番剧进入导航上下文。
 */
internal class AppRootEnterBangumiContext(
  val context: Context,
  val scope: CoroutineScope,
  val keyboardController: SoftwareKeyboardController?,
  val videoEntryCache: LinkedHashMap<String, VideoPageEntry>,
  val playerViewModel: PlayerViewModel,
  val mainViewModel: MainViewModel,
  val authViewModel: AuthViewModel,
  val bangumiExploreViewModel: BangumiExploreViewModel,
  val videoState: AppRootVideoState,
  val playerSession: AppRootPlayerSessionState,
  val profileState: AppRootProfileState,
  val searchCardBounds: SnapshotStateMap<String, Rect>,
  val myCardBounds: SnapshotStateMap<String, Rect>,
  val bangumiIndexCardBounds: SnapshotStateMap<String, Rect>,
  val appStateState: State<AppUiState>,
  val settingsState: State<AppSettings>,
  val authUserInfoState: State<UserInfo>,
  val playerStateState: State<PlayerState>,
  val renderedVideoId: String?,
  val transitionTokenState: MutableState<Long>,
  val dataCommitAllowedIdState: MutableState<String?>,
  val hiddenProfileCoverItemIdState: MutableState<String?>,
  val pendingVideoCommentTargetState: MutableState<CommentNavigationTarget?>,
  val rootPlayerOwnershipState: MutableState<RootPlayerOwnership>,
  val hiddenMyCoverItemIdState: MutableState<String?>,
  val playerActivationIdState: MutableState<String?>,
  val showEmbeddedCoverState: MutableState<Boolean>,
  val profileStackState: MutableState<List<ProfileStackEntry>>,
  val hiddenArticleVideoCoverItemIdState: MutableState<String?>,
  val profileVideoTransitionActiveState: MutableState<Boolean>,
  val transitionSessionState: MutableState<CardTransitionSession?>,
  val transitionPhaseState: MutableState<TransitionPhase>,
  val hiddenSearchCoverItemIdState: MutableState<String?>,
  val videoStackState: MutableState<List<StackFrame>>,
  val profileLayerSuppressedState: MutableState<Boolean>,
  val deferBangumiHomePageCompositionState: MutableState<Boolean>,
  val deferBangumiIndexPageCompositionState: MutableState<Boolean>,
  val hiddenBangumiIndexItemIdState: MutableState<String?>,
  val deferSearchBangumiPageCompositionState: MutableState<Boolean>,
  val hiddenHomeDynamicCoverItemIdState: MutableState<String?>,
  val hiddenFeedCoverItemIdState: MutableState<String?>,
  val hiddenBangumiRecommendationItemIdState: MutableState<String?>,
  val bangumiPosterBoundsState: MutableState<Rect>,
  val activeBangumiPageState: MutableState<ActiveBangumiPage?>,
  val hiddenPopularCoverItemIdState: MutableState<String?>,
  val playerBoundsState: MutableState<Rect>,
  val videoPageDataReadyIdState: MutableState<String?>,
  val selectCollectionEpisodeRef: (FeedItem) -> Unit,
  val activeProfileEntryRef: (Long?) -> ProfileStackEntry?,
  val restoreEntryForFreshPlaybackRef: (VideoPageEntry) -> Unit,
  val awaitStablePlayerBoundsRef: suspend () -> Rect,
  val cacheEntryRef: (VideoPageEntry) -> Unit,
  val restoredBangumiCardRef: (SpaceContentCard) -> SpaceContentCard,
  val clearVisibleVideoDataRef: () -> Unit,
  val currentPreferredResolutionModeRef: () -> PreferredResolutionMode,
  val snapshotEntryRef: (FeedItem) -> VideoPageEntry,
  val revealTransitionSessionRef: (CardTransitionSession, Boolean) -> Unit,
  val prepareCardTransitionRef: suspend (CardTransitionSession, () -> Rect) -> Rect,
  val launchTransitionRef: (suspend CoroutineScope.() -> Unit) -> Unit,
  val retainedPlaybackPageRef: (String) -> VideoPage?,
) {
  val appState by appStateState
  val settings by settingsState
  val authUserInfo by authUserInfoState
  val playerState by playerStateState
  var transitionToken by transitionTokenState
  var dataCommitAllowedId by dataCommitAllowedIdState
  var hiddenProfileCoverItemId by hiddenProfileCoverItemIdState
  var pendingVideoCommentTarget by pendingVideoCommentTargetState
  var rootPlayerOwnership by rootPlayerOwnershipState
  var hiddenMyCoverItemId by hiddenMyCoverItemIdState
  var playerActivationId by playerActivationIdState
  var showEmbeddedCover by showEmbeddedCoverState
  var profileStack by profileStackState
  var hiddenArticleVideoCoverItemId by hiddenArticleVideoCoverItemIdState
  var profileVideoTransitionActive by profileVideoTransitionActiveState
  var transitionSession by transitionSessionState
  var transitionPhase by transitionPhaseState
  var hiddenSearchCoverItemId by hiddenSearchCoverItemIdState
  var videoStack by videoStackState
  var profileLayerSuppressed by profileLayerSuppressedState
  var deferBangumiHomePageComposition by deferBangumiHomePageCompositionState
  var deferBangumiIndexPageComposition by deferBangumiIndexPageCompositionState
  var hiddenBangumiIndexItemId by hiddenBangumiIndexItemIdState
  var deferSearchBangumiPageComposition by deferSearchBangumiPageCompositionState
  var hiddenHomeDynamicCoverItemId by hiddenHomeDynamicCoverItemIdState
  var hiddenFeedCoverItemId by hiddenFeedCoverItemIdState
  var hiddenBangumiRecommendationItemId by hiddenBangumiRecommendationItemIdState
  var bangumiPosterBounds by bangumiPosterBoundsState
  var activeBangumiPage by activeBangumiPageState
  var hiddenPopularCoverItemId by hiddenPopularCoverItemIdState
  var playerBounds by playerBoundsState
  var videoPageDataReadyId by videoPageDataReadyIdState
  var danmakuMask by videoState::danmakuMask
  var danmaku by videoState::danmaku
  var historyAid by videoState::historyAid
  var historyCid by videoState::historyCid
  var historyDuration by videoState::historyDuration
  var historyStartTimestamp by videoState::historyStartTimestamp
  var currentPositionMs by playerSession::currentPositionMs
  var playbackEnded by playerSession::playbackEnded
  var profileMid by profileState::profileMid
  var spaceProfile by profileState::spaceProfile
  var commentProfileReturnTransition by profileState::commentProfileReturnTransition
  var avatarProfileReturnTransition by profileState::avatarProfileReturnTransition

  fun selectCollectionEpisode(episode: FeedItem) = selectCollectionEpisodeRef(episode)
  fun activeProfileEntry(entryId: Long? = null): ProfileStackEntry? = activeProfileEntryRef(entryId)
  fun restoreEntryForFreshPlayback(entry: VideoPageEntry) = restoreEntryForFreshPlaybackRef(entry)
  suspend fun awaitStablePlayerBounds(): Rect = awaitStablePlayerBoundsRef()
  fun cacheEntry(entry: VideoPageEntry) = cacheEntryRef(entry)
  fun restoredBangumiCard(sourceCard: SpaceContentCard): SpaceContentCard =
    restoredBangumiCardRef(sourceCard)
  fun clearVisibleVideoData() = clearVisibleVideoDataRef()
  fun currentPreferredResolutionMode(): PreferredResolutionMode = currentPreferredResolutionModeRef()
  fun snapshotEntry(item: FeedItem): VideoPageEntry = snapshotEntryRef(item)
  fun revealTransitionSession(session: CardTransitionSession, timedOut: Boolean = false) =
    revealTransitionSessionRef(session, timedOut)
  suspend fun prepareCardTransition(session: CardTransitionSession, targetBounds: (() -> Rect)? = null): Rect =
    prepareCardTransitionRef(session, targetBounds ?: { playerBounds })
  fun launchTransition(block: suspend CoroutineScope.() -> Unit) = launchTransitionRef(block)
  fun retainedPlaybackPage(itemId: String): VideoPage? = retainedPlaybackPageRef(itemId)

fun startEnterVideo(
  item: FeedItem,
  cardBounds: Rect?,
  origin: VideoOrigin = VideoOrigin.OTHER,
  commentTarget: CommentNavigationTarget? = null,
  rootFeedScrollAnchor: FeedScrollAnchor? = null,
  onLanded: (() -> Unit)? = null,
  fitCover: Boolean = false,
  preserveCurrentPlayback: Boolean = false,
  startPositionMs: Long = 0L,
  restoreSavedProgress: Boolean = true,
  transitionTargetBounds: (() -> Rect)? = null,
  sourceAnchorKey: String? = null,
) {
  if (transitionPhase !is TransitionPhase.Feed) return
  val restoreProgressForEntry =
    restoreSavedProgress && videoEntryCache[item.id]?.playbackEnded != true
  val reuseCurrentPlayback =
    preserveCurrentPlayback &&
      renderedVideoId == item.id &&
      playerState is dev.openbili.webdemo.PlayerState.Ready
  val bangumiHomeEnter = origin == VideoOrigin.BANGUMI
  val bangumiIndexEnter = activeBangumiPage?.sourceOrigin == PageOrigin.BangumiIndex
  if (bangumiHomeEnter && item.coverUrl.isNotBlank()) {
    LoadedFeedImageRegistry.bitmap(bangumiPreviewCoverCacheKey(item.coverUrl))?.let { bitmap ->
      LoadedFeedImageRegistry.markLoaded(item.coverUrl, bitmap)
    }
  }
  rootPlayerOwnership =
    RootPlayerOwnership(
      if (reuseCurrentPlayback) RootPlayerSurfaceRole.DETAIL
      else RootPlayerSurfaceRole.DETAIL_PENDING,
      item.id,
    )
  pendingVideoCommentTarget = commentTarget
  keyboardController?.hide()
  val parent =
    when (origin) {
      VideoOrigin.HOME -> PageOrigin.Home
      VideoOrigin.HOME_DYNAMIC -> PageOrigin.Home
      VideoOrigin.POPULAR -> PageOrigin.Home
      VideoOrigin.MY -> PageOrigin.My
      VideoOrigin.SEARCH -> PageOrigin.Search
      VideoOrigin.BANGUMI -> PageOrigin.BangumiHome
      VideoOrigin.ARTICLE -> PageOrigin.Article
      VideoOrigin.OTHER -> PageOrigin.Other
    }
  videoStack =
    listOf(
      StackFrame(
        entryId = item.id,
        item = item,
        parentPage = parent,
        sourceCardBounds = cardBounds,
        rootFeedScrollAnchor = rootFeedScrollAnchor.takeIf { origin == VideoOrigin.HOME },
        rootVideoOrigin = origin,
        sourceAnchorKey = sourceAnchorKey,
      )
    )
  dataCommitAllowedId = null
  playerActivationId = null
  showEmbeddedCover = cardBounds == null && !reuseCurrentPlayback
  // 目的地外壳在根视频间共享同一个状态持有器：趁它仍被来源页盖住时重置或恢复
  // 该持有器，让揭示动画不会暴露上一个视频的评论、推荐或元数据。
  if (!bangumiHomeEnter && !bangumiIndexEnter) {
    videoEntryCache[item.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
  }
  val session = cardBounds?.let { source ->
    CardTransitionSession(
        token = ++transitionToken,
        kind = TransitionKind.ENTER_ROOT,
        item = item,
        startBounds = source,
        endBounds = source,
        initialProgress = 0f,
        fitCover = fitCover,
        reusePlayerSurface = reuseCurrentPlayback,
        requiredSignals = playerTransitionRequiredSignals,
      )
      .also {
        it.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
        transitionSession = it
      }
  }
  transitionPhase = TransitionPhase.ToVideo(item, cardBounds)
  playerBounds = Rect.Zero
  mainViewModel.openVideo(item)
  launchTransition {
    if (bangumiHomeEnter) {
      playerViewModel.cancelPendingLoad()
      playerViewModel.exoPlayer?.pause()
    }
    withFrameNanos {}
    val target =
      session?.let {
        transitionTargetBounds?.let { bounds -> prepareCardTransition(it, bounds) }
          ?: prepareCardTransition(it)
      } ?: awaitStablePlayerBounds()
    if (session == null || target == Rect.Zero || target.width <= 0f) {
      session?.apply {
        phase = SessionPhase.CANCELLED
        preparation.cancel()
      }
      if (transitionSession === session) transitionSession = null
      transitionPhase = TransitionPhase.Video(item, cardBounds)
      if (bangumiHomeEnter || bangumiIndexEnter) {
        videoEntryCache[item.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
        if (bangumiHomeEnter) deferBangumiHomePageComposition = false
        withFrameNanos {}
        awaitMainMessageQueueIdle()
      }
      onLanded?.invoke()
      dataCommitAllowedId = item.id
      playerActivationId = item.id
      showEmbeddedCover = !reuseCurrentPlayback
      if (!reuseCurrentPlayback) {
        playerViewModel.loadVideo(
          item,
          startPositionMs = startPositionMs,
          preferredResolutionMode = currentPreferredResolutionMode(),
          page = retainedPlaybackPage(item.id),
          restoreSavedProgress = restoreProgressForEntry,
        )
      }
      return@launchTransition
    }
    session.endBounds = target
    hiddenFeedCoverItemId = item.id.takeIf { origin == VideoOrigin.HOME }
    hiddenHomeDynamicCoverItemId = sourceAnchorKey.takeIf { origin == VideoOrigin.HOME_DYNAMIC }
    hiddenPopularCoverItemId = item.id.takeIf { origin == VideoOrigin.POPULAR }
    hiddenMyCoverItemId = item.id.takeIf { origin == VideoOrigin.MY }
    hiddenSearchCoverItemId = item.id.takeIf { origin == VideoOrigin.SEARCH }
    hiddenBangumiIndexItemId = item.id.takeIf { bangumiIndexEnter }
    hiddenBangumiRecommendationItemId =
      item.id.takeIf { origin == VideoOrigin.BANGUMI && !reuseCurrentPlayback }
    hiddenArticleVideoCoverItemId = item.id.takeIf { origin == VideoOrigin.ARTICLE }
    session.backgroundStarted = true
    // 给保留的来源页一帧时间隐藏真实封面并抓取那张设备尺寸的精确结果：飞行封面
    // 随后可复用冻结页面，而不必在每帧动画后重绘完整信息流。
    withFrameNanos {}
    if (session.reverseRequested) return@launchTransition
    session.phase = SessionPhase.FLYING
    kotlinx.coroutines.coroutineScope {
      launch {
        session.progress.animateTo(
          1f,
          tween(if (settings.reduceMotion) 140 else 400, easing = FastOutSlowInEasing),
        )
      }
      launch {
        delay(if (settings.reduceMotion) 10 else 45)
        session.backgroundAlpha.animateTo(
          1f,
          tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
        )
      }
      if (bangumiHomeEnter && settings.videoBackgroundUri.isBlank()) {
        launch {
          session.themeScrimAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 100 else 260, easing = FastOutSlowInEasing),
          )
        }
      }
    }
    if (session.reverseRequested) return@launchTransition
    session.phase = SessionPhase.REVEALING_BACKGROUND
    if (bangumiHomeEnter || bangumiIndexEnter) {
      // 飞行拥有帧预算：只有其几何落地后才挂载完整播放器页，随后让出主队列，
      // 再淡入信息面板。
      videoEntryCache[item.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
      if (bangumiHomeEnter) deferBangumiHomePageComposition = false
      if (bangumiIndexEnter) onLanded?.invoke()
      withFrameNanos {}
      awaitMainMessageQueueIdle()
      withFrameNanos {}
    }
    // 先挂载下层推荐窗格：这一帧不可见的稳定期内不挂评论，然后在 panelAlpha 仍为
    // 零时挂载评论。既有的面板揭示仍是第一个可见变化，推荐到推荐的导航绝不进入
    // 这条仅根页使用的分阶段路径。
    withFrameNanos {}
    if (!bangumiHomeEnter && !bangumiIndexEnter) awaitMainMessageQueueIdle()
    if (session.reverseRequested) return@launchTransition
    session.deferRootEnterComments = false
    withFrameNanos {}
    awaitMainMessageQueueIdle()
    withFrameNanos {}
    if (session.reverseRequested) return@launchTransition
    kotlinx.coroutines.coroutineScope {
      launch {
        session.panelAlpha.animateTo(
          1f,
          tween(if (settings.reduceMotion) 90 else 180, easing = FastOutSlowInEasing),
        )
      }
      if (bangumiHomeEnter && settings.videoBackgroundUri.isBlank()) {
        launch {
          session.themeScrimAlpha.animateTo(
            0f,
            tween(if (settings.reduceMotion) 80 else 180, easing = FastOutSlowInEasing),
          )
        }
      }
    }
    if (session.reverseRequested) return@launchTransition
    if (reuseCurrentPlayback) {
      session.phase = SessionPhase.WAITING_FIRST_FRAME
      playerActivationId = item.id
      revealTransitionSession(session)
      while (transitionSession?.token == session.token) withFrameNanos {}
      onLanded?.invoke()
      dataCommitAllowedId = item.id
    } else if (bangumiHomeEnter || bangumiIndexEnter) {
      // 卡片与面板动画已结束：页面数据可立即开始，与普通视频进入一致。首帧揭示
      // 保持独立，不得阻塞评论、推荐、元数据或弹幕请求。
      if (bangumiHomeEnter) onLanded?.invoke()
      dataCommitAllowedId = item.id
      session.phase = SessionPhase.WAITING_FIRST_FRAME
      playerActivationId = item.id
      playerViewModel.loadVideo(
        item,
        startPositionMs = startPositionMs,
        preferredResolutionMode = currentPreferredResolutionMode(),
        page = retainedPlaybackPage(item.id),
        restoreSavedProgress = restoreProgressForEntry,
      )
      // 这个转场协程只为静止封面的首帧揭示保持存活。
      while (transitionSession?.token == session.token) withFrameNanos {}
    } else {
      onLanded?.invoke()
      dataCommitAllowedId = item.id
      session.phase = SessionPhase.WAITING_FIRST_FRAME
      playerActivationId = item.id
      playerViewModel.loadVideo(
        item,
        startPositionMs = startPositionMs,
        preferredResolutionMode = currentPreferredResolutionMode(),
        page = retainedPlaybackPage(item.id),
        restoreSavedProgress = restoreProgressForEntry,
      )
    }
  }
}

fun startProfileVideo(profileEntryId: Long, item: FeedItem, cardBounds: Rect) {
  val sourceEntry = activeProfileEntry(profileEntryId) ?: return
  val sourceState = sourceEntry.state
  val sourceMid = sourceState.profileMid ?: return
  if (
    transitionSession != null ||
      transitionPhase !is TransitionPhase.Feed && transitionPhase !is TransitionPhase.Video
  )
    return
  // 一旦资料页隐藏到其子视频之后，从该视频打开的资料页会使用全局转场槽位：
  // 在那发生之前先持久化这个资料页自己的返回路由。
  profileStack =
    profileStack.retainReturnTransitionsFor(
      sourceEntry.entryId,
      commentProfileReturnTransition,
      avatarProfileReturnTransition,
    )
  val bounds = cardBounds.takeIf { it != Rect.Zero && it.width > 0f && it.height > 0f }
  val fromVideo = transitionPhase is TransitionPhase.Video
  val currentVideo = appState.selectedVideo
  val restoreProfileProgress = videoEntryCache[item.id]?.playbackEnded != true
  keyboardController?.hide()
  if (fromVideo && currentVideo != null) cacheEntry(snapshotEntry(currentVideo))
  val expandedStack =
    (if (fromVideo) videoStack else emptyList()) +
      StackFrame(
        entryId = item.id,
        item = item,
        parentPage = PageOrigin.Profile(sourceEntry.entryId, sourceMid),
        sourceCardBounds = bounds,
        sourceProfile = sourceState.snapshotProfile(sourceMid),
      )
  videoStack =
    if (expandedStack.size <= MAX_VIDEO_STACK_DEPTH) expandedStack
    else expandedStack.takeLast(MAX_VIDEO_STACK_DEPTH)
  dataCommitAllowedId = null
  playerActivationId = null
  showEmbeddedCover = false
  // 来源资料页已盖住共享视频外壳：在其缓存中保留任何父视频，然后在外壳可见
  // 之前只布置被点击视频的状态。
  videoEntryCache[item.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
  val session = bounds?.let { source ->
    CardTransitionSession(
        token = ++transitionToken,
        kind = TransitionKind.ENTER_PROFILE,
        item = item,
        startBounds = source,
        endBounds = source,
        initialProgress = 0f,
        initialPanelAlpha = 0f,
        requiredSignals = playerTransitionRequiredSignals,
      )
      .also {
        it.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
        transitionSession = it
      }
  }
  transitionPhase = TransitionPhase.ToVideo(item, bounds, fromVideo = fromVideo)
  if (!fromVideo) playerBounds = Rect.Zero
  // 与根信息流转场一致：先只组合目的地外壳。视频数据与播放仍由
  // dataCommitAllowedId/playerActivationId 阻塞，直到飞行结束。
  mainViewModel.openVideo(item)
  launchTransition {
    withFrameNanos {}
    val target = session?.let { prepareCardTransition(it) } ?: awaitStablePlayerBounds()
    if (session == null || target == Rect.Zero || target.width <= 0f || target.height <= 0f) {
      session?.apply {
        phase = SessionPhase.CANCELLED
        preparation.cancel()
      }
      if (transitionSession === session) transitionSession = null
      profileLayerSuppressed = true
      hiddenProfileCoverItemId = null
      profileVideoTransitionActive = false
      transitionPhase = TransitionPhase.Video(item, null)
      dataCommitAllowedId = item.id
      playerActivationId = item.id
      showEmbeddedCover = true
      playerViewModel.loadVideo(
        item,
        preferredResolutionMode = currentPreferredResolutionMode(),
        page = retainedPlaybackPage(item.id),
        restoreSavedProgress = restoreProfileProgress,
      )
      return@launchTransition
    }
    session.endBounds = target
    hiddenProfileCoverItemId = item.id
    profileVideoTransitionActive = true
    session.backgroundStarted = true
    session.phase = SessionPhase.FLYING
    kotlinx.coroutines.coroutineScope {
      launch {
        session.progress.animateTo(
          1f,
          tween(if (settings.reduceMotion) 140 else 400, easing = FastOutSlowInEasing),
        )
      }
      launch {
        delay(if (settings.reduceMotion) 10 else 45)
        session.backgroundAlpha.animateTo(
          1f,
          tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
        )
      }
    }
    if (session.reverseRequested) return@launchTransition
    profileLayerSuppressed = true
    hiddenProfileCoverItemId = null
    profileVideoTransitionActive = false
    session.phase = SessionPhase.REVEALING_BACKGROUND
    withFrameNanos {}
    session.panelAlpha.animateTo(
      1f,
      tween(if (settings.reduceMotion) 90 else 180, easing = FastOutSlowInEasing),
    )
    if (session.reverseRequested) return@launchTransition
    dataCommitAllowedId = item.id
    session.phase = SessionPhase.WAITING_FIRST_FRAME
    playerActivationId = item.id
    playerViewModel.loadVideo(
      item,
      preferredResolutionMode = currentPreferredResolutionMode(),
      page = retainedPlaybackPage(item.id),
      restoreSavedProgress = restoreProfileProgress,
    )
  }
}

fun selectBangumiEpisode(episode: BangumiEpisode) {
  val page = activeBangumiPage ?: return
  if (episode.id <= 0L || episode.id == page.currentEpisodeId) return
  val item =
    FeedItem(
      id = "${page.sourceCard.id}:ep${episode.id}",
      title =
        listOf(episode.title, episode.longTitle)
          .filter(String::isNotBlank)
          .joinToString(" · ")
          .ifBlank { page.sourceCard.title },
      videoUrl = "https://www.bilibili.com/bangumi/play/ep${episode.id}",
      coverUrl = episode.coverUrl.ifBlank { page.sourceCard.coverUrl },
      uploader = appState.selectedVideo?.uploader,
      playCount = null,
      duration = null,
      uploaderFace = appState.selectedVideo?.uploaderFace,
      uploaderMid = appState.selectedVideo?.uploaderMid ?: 0L,
      description = page.season?.evaluate.orEmpty(),
    )
  BangumiPlaybackStore.save(
    context.applicationContext,
    page.sourceCard,
    page.season?.seasonId ?: 0L,
    episode,
  )
  selectCollectionEpisode(item)
  // selectCollectionEpisode() 会提交旧 aid/cid：只有在那之后才发布新剧集，避免
  // 后台心跳把旧剧集的最终进度归到新剧集上。
  activeBangumiPage = page.copy(currentEpisodeId = episode.id)
  if (page.sourceCard.seasonId > 0L && page.season?.seasonId == page.sourceCard.seasonId) {
    // 在来源卡隐藏于播放器之后时更新它：最终进度在退出时还会再补一次，但这里
    // 更新封面让卡片有时间在共享元素飞行返回前解码所选剧集封面。
    bangumiExploreViewModel.applyFollowingPlayback(
      seasonId = page.sourceCard.seasonId,
      episode = episode,
      positionMs = 0L,
    )
  }
}

fun selectBangumiSeason(seasonId: Long) {
  val page = activeBangumiPage ?: return
  if (
    seasonId <= 0L ||
      seasonId == page.season?.seasonId ||
      page.loading ||
      transitionSession != null
  ) {
    return
  }
  val pageId = page.sourceCard.id
  activeBangumiPage = page.copy(loading = true, error = null)
  scope.launch {
    val result =
      withContext(Dispatchers.IO) {
        runCatching { BiliBangumiApi.getBangumiSeason(seasonId = seasonId) }
      }
    val current = activeBangumiPage
    if (current?.sourceCard?.id != pageId) return@launch
    result
      .onSuccess { season ->
        activeBangumiPage =
          current.copy(
            season =
              season.copy(
                followed =
                  season.followed ||
                    (current.sourceFollowedByViewer && season.seasonId == current.sourceSeasonId)
              ),
            loading = false,
            error = null,
            currentEpisodeId = 0L,
            seasonChangedFromSource =
              current.sourceSeasonId > 0L && season.seasonId != current.sourceSeasonId,
          )
        season.episodes
          .ifEmpty {
            season.sections.firstOrNull { it.episodes.isNotEmpty() }?.episodes.orEmpty()
          }
          .firstOrNull()
          ?.let(::selectBangumiEpisode)
      }
      .onFailure { error ->
        activeBangumiPage = current.copy(loading = false, error = error.message ?: "季度加载失败")
      }
  }
}

fun toggleBangumiFollow() {
  if (!authUserInfo.isLogin) {
    authViewModel.startLogin()
    return
  }
  val page = activeBangumiPage ?: return
  val season = page.season ?: return
  if (page.followBusy || season.seasonId <= 0L) return
  val target = !season.followed
  val pageId = page.sourceCard.id
  activeBangumiPage = page.copy(followBusy = true)
  scope.launch {
    val result =
      withContext(Dispatchers.IO) {
        runCatching {
          BiliBangumiApi.setBangumiFollow(season.seasonId, target)
          BiliBangumiApi.getBangumiSeason(seasonId = season.seasonId)
        }
      }
    val current = activeBangumiPage
    if (current?.sourceCard?.id != pageId) return@launch
    result
      .onSuccess { confirmedSeason ->
        activeBangumiPage =
          current.copy(
            followBusy = false,
            // 成功的变更是权威结果：写后读接口可能短暂返回旧关注关系，
            // 不能让按钮被它翻回去。
            season = confirmedSeason.copy(followed = target),
          )
        Toast.makeText(
            context,
            if (target) {
              if (page.sourceCard.kind == dev.openbili.webdemo.api.SpaceContentKind.DRAMA) "已追剧"
              else "已追番"
            } else "已取消追番追剧",
            Toast.LENGTH_SHORT,
          )
          .show()
      }
      .onFailure { error ->
        activeBangumiPage = current.copy(followBusy = false)
        Toast.makeText(context, error.message ?: "追番操作失败", Toast.LENGTH_SHORT).show()
      }
  }
}

fun postBangumiShortReview(score: Int, content: String) {
  if (!authUserInfo.isLogin) {
    authViewModel.startLogin()
    return
  }
  val mediaId = activeBangumiPage?.season?.mediaId ?: 0L
  if (mediaId <= 0L) return
  scope.launch {
    val result =
      withContext(Dispatchers.IO) {
        runCatching { BiliBangumiApi.postBangumiShortReview(mediaId, score, content) }
      }
    result
      .onSuccess {
        activeBangumiPage
          ?.takeIf { it.season?.mediaId == mediaId }
          ?.let { current ->
            activeBangumiPage =
              current.copy(season = current.season?.copy(userRatingScore = score))
          }
        Toast.makeText(context, "短评发布成功", Toast.LENGTH_SHORT).show()
      }
      .onFailure { error ->
        Toast.makeText(context, error.message ?: "短评发布失败", Toast.LENGTH_SHORT).show()
      }
  }
}

fun loadActiveBangumiMetadata(card: SpaceContentCard) {
  scope.launch {
    val result =
      withContext(Dispatchers.IO) {
        runCatching {
          BiliBangumiApi.getBangumiSeason(
            seasonId = card.seasonId,
            episodeId = card.episodeId,
            bvid = card.bvid,
            aid = card.aid,
          )
        }
      }
    val current = activeBangumiPage
    if (current?.sourceCard?.id != card.id) return@launch
    activeBangumiPage =
      result.fold(
        onSuccess = { season ->
          val playableEpisodes =
            (season.episodes + season.sections.flatMap { it.episodes }).distinctBy { it.id }
          val selectedId =
            card.episodeId.takeIf { requested -> playableEpisodes.any { it.id == requested } }
              ?: card.bvid.takeIf(String::isNotBlank)?.let { requested ->
                playableEpisodes.firstOrNull { it.bvid.equals(requested, ignoreCase = true) }?.id
              }
              ?: card.aid
                .takeIf { it > 0L }
                ?.let { requested -> playableEpisodes.firstOrNull { it.aid == requested }?.id }
              ?: playableEpisodes.firstOrNull()?.id
              ?: 0L
          val fellBack = selectedId > 0L && selectedId != card.episodeId && card.episodeId > 0L
          if (fellBack && !current.playbackFallbackEmitted) {
            val fallbackEp = playableEpisodes.firstOrNull { it.id == selectedId }
            if (fallbackEp != null) {
              val fallbackItem =
                FeedItem(
                  id = "${current.sourceCard.id}:ep$selectedId",
                  title = current.sourceCard.title,
                  videoUrl = "https://www.bilibili.com/bangumi/play/ep$selectedId",
                  coverUrl = current.sourceCard.coverUrl,
                  uploader = null,
                  playCount = null,
                  duration = null,
                  description = current.sourceCard.subtitle,
                )
              playerViewModel.loadVideo(
                item = fallbackItem,
                startPositionMs = 0L,
                preferredResolutionMode = currentPreferredResolutionMode(),
                restoreSavedProgress = true,
              )
            }
          }
          val sourceSeasonId = current.sourceSeasonId.takeIf { it > 0L } ?: season.seasonId
          current.copy(
            sourceSeasonId = sourceSeasonId,
            season =
              season.copy(
                followed =
                  season.followed ||
                    (current.sourceFollowedByViewer && season.seasonId == sourceSeasonId)
              ),
            loading = false,
            error = null,
            currentEpisodeId = selectedId,
            seasonChangedFromSource = sourceSeasonId > 0L && season.seasonId != sourceSeasonId,
            playbackFallbackEmitted = fellBack || current.playbackFallbackEmitted,
          )
        },
        onFailure = { error ->
          current.copy(loading = false, error = error.message ?: "番剧资料加载失败")
        },
      )
  }
}


fun startRootBangumi(
  card: SpaceContentCard,
  item: FeedItem,
  cardBounds: Rect,
  pageOrigin: PageOrigin,
  videoOrigin: VideoOrigin,
  restoreEpisodeSelection: Boolean = true,
  preserveCurrentPlayback: Boolean = false,
  initialSeason: BangumiSeason? = null,
  returnToSourceCover: Boolean = false,
) {
  if (activeBangumiPage != null || transitionPhase !is TransitionPhase.Feed) return
  val offlinePlayback = OfflineMediaManager.isOfflineUri(item.videoUrl)
  val shouldRestoreEpisodeSelection = restoreEpisodeSelection && !offlinePlayback
  val localSelection =
    if (shouldRestoreEpisodeSelection) BangumiPlaybackStore.read(context.applicationContext, card)
    else null
  val entryTarget = resolveBangumiEntryTarget(card, localSelection, shouldRestoreEpisodeSelection)
  val resolvedCard = entryTarget.card
  val playbackItem = if (offlinePlayback) item else item.copy(videoUrl = resolvedCard.videoUrl)
  if (resolvedCard.episodeId != card.episodeId || resolvedCard.seasonId != card.seasonId) {
    videoEntryCache.remove(playbackItem.id)
  }
  val bounds = cardBounds.takeIf { it.hasUsableSize() }
  bounds?.let {
    when (pageOrigin) {
      PageOrigin.My -> myCardBounds[playbackItem.id] = it
      PageOrigin.Search -> searchCardBounds[playbackItem.id] = it
      PageOrigin.BangumiIndex -> bangumiIndexCardBounds[playbackItem.id] = it
      else -> Unit
    }
  }
  activeBangumiPage =
    ActiveBangumiPage(
      sourceCard = card,
      sourceProfileEntryId = 0L,
      sourceMid = authUserInfo.mid,
      sourceBounds = bounds,
      sourceVideoCoverUrl =
        item.coverUrl
          .takeIf { pageOrigin == PageOrigin.My || pageOrigin == PageOrigin.BangumiHome }
          .orEmpty(),
      returnToSourceCover = returnToSourceCover,
      sourceUsesLivePlayer = preserveCurrentPlayback,
      sourceOrigin = pageOrigin,
      sourceFollowedByViewer = initialSeason?.followed == true,
      season = initialSeason,
      loading = initialSeason == null,
      currentEpisodeId = resolvedCard.episodeId,
    )
  deferBangumiHomePageComposition = pageOrigin == PageOrigin.BangumiHome
  deferBangumiIndexPageComposition = pageOrigin == PageOrigin.BangumiIndex
  bangumiPosterBounds = Rect.Zero
  startEnterVideo(
    item = playbackItem,
    cardBounds = bounds,
    origin = videoOrigin,
    onLanded = {
      deferBangumiIndexPageComposition = false
      if (activeBangumiPage?.season == null) loadActiveBangumiMetadata(resolvedCard)
    },
    fitCover = pageOrigin == PageOrigin.Search || pageOrigin == PageOrigin.BangumiIndex,
    preserveCurrentPlayback = preserveCurrentPlayback,
    startPositionMs = entryTarget.startPositionMs,
    restoreSavedProgress = !entryTarget.serverResumeAuthoritative,
    transitionTargetBounds =
      if (pageOrigin == PageOrigin.BangumiIndex) {
        { bangumiPosterBounds }
      } else null,
  )
}

fun startHistoryBangumi(card: SpaceContentCard, item: FeedItem, cardBounds: Rect) {
  startRootBangumi(card, item, cardBounds, PageOrigin.My, VideoOrigin.MY)
}

fun startSearchBangumi(
  card: SpaceContentCard,
  cardBounds: Rect,
  sourceIsBangumiExplorePoster: Boolean = false,
  sourceOrigin: PageOrigin = PageOrigin.Search,
) {
  if (
    card.videoUrl.isBlank() ||
      transitionSession != null ||
      activeBangumiPage != null ||
      transitionPhase !is TransitionPhase.Feed
  ) {
    return
  }
  val restoredCard = restoredBangumiCard(card)
  val item = restoredCard.toBangumiVideoItem()
  if (restoredCard.episodeId != card.episodeId || restoredCard.seasonId != card.seasonId) {
    videoEntryCache.remove(item.id)
  }
  val bounds = cardBounds.takeIf { it.hasUsableSize() }
  fun setPortraitSourceHidden(hidden: Boolean) {
    when (sourceOrigin) {
      PageOrigin.BangumiIndex -> hiddenBangumiIndexItemId = card.id.takeIf { hidden }
      PageOrigin.Search -> {
        if (sourceIsBangumiExplorePoster) {
          hiddenBangumiRecommendationItemId = card.id.takeIf { hidden }
        } else {
          hiddenSearchCoverItemId = card.id.takeIf { hidden }
        }
      }
      else -> Unit
    }
  }
  bounds?.let {
    when (sourceOrigin) {
      PageOrigin.BangumiIndex -> bangumiIndexCardBounds[card.id] = it
      PageOrigin.Search -> searchCardBounds[card.id] = it
      else -> Unit
    }
  }
  keyboardController?.hide()
  videoStack =
    listOf(
      StackFrame(
        entryId = item.id,
        item = item,
        parentPage = sourceOrigin,
        sourceCardBounds = bounds,
      )
    )
  activeBangumiPage =
    ActiveBangumiPage(
      sourceCard = card,
      sourceProfileEntryId = 0L,
      sourceMid = authUserInfo.mid,
      sourceBounds = bounds,
      sourceOrigin = sourceOrigin,
      sourceIsBangumiExplorePoster = sourceIsBangumiExplorePoster,
    )
  deferSearchBangumiPageComposition = sourceOrigin == PageOrigin.Search
  deferBangumiIndexPageComposition = sourceOrigin == PageOrigin.BangumiIndex
  bangumiPosterBounds = Rect.Zero
  dataCommitAllowedId = null
  playerActivationId = null
  showEmbeddedCover = false
  videoEntryCache[item.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
  val session = bounds?.let { source ->
    CardTransitionSession(
        token = ++transitionToken,
        kind = TransitionKind.ENTER_ROOT,
        item = item,
        startBounds = source,
        endBounds = source,
        initialProgress = 0f,
        initialPanelAlpha = 0f,
        fitCover = true,
        requiredSignals = playerTransitionRequiredSignals,
      )
      .also {
        it.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
        transitionSession = it
      }
  }
  transitionPhase = TransitionPhase.ToVideo(item, bounds)
  playerBounds = Rect.Zero
  mainViewModel.openVideo(item)
  launchTransition {
    withFrameNanos {}
    val target =
      session?.let { prepareCardTransition(it) { bangumiPosterBounds } }
        ?: bangumiPosterBounds.takeIf { it.hasUsableSize() }
        ?: Rect.Zero
    if (!target.hasUsableSize()) {
      session?.apply {
        phase = SessionPhase.CANCELLED
        preparation.cancel()
      }
      if (transitionSession === session) transitionSession = null
      setPortraitSourceHidden(false)
      transitionPhase = TransitionPhase.Video(item, null)
      deferSearchBangumiPageComposition = false
      deferBangumiIndexPageComposition = false
      withFrameNanos {}
      loadActiveBangumiMetadata(restoredCard)
      dataCommitAllowedId = item.id
      playerActivationId = item.id
      showEmbeddedCover = true
      playerViewModel.loadVideo(item, preferredResolutionMode = currentPreferredResolutionMode())
      return@launchTransition
    }
    session?.endBounds = target
    setPortraitSourceHidden(true)
    session?.apply {
      backgroundStarted = true
      phase = SessionPhase.FLYING
    }
    if (session != null) {
      coroutineScope {
        launch {
          session.progress.animateTo(
            1f,
            tween(if (settings.reduceMotion) 140 else 400, easing = FastOutSlowInEasing),
          )
        }
        launch {
          delay(if (settings.reduceMotion) 10 else 45)
          session.backgroundAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
          )
        }
      }
      if (session.reverseRequested) return@launchTransition
    }
    setPortraitSourceHidden(false)
    session?.phase = SessionPhase.REVEALING_BACKGROUND
    deferSearchBangumiPageComposition = false
    deferBangumiIndexPageComposition = false
    // 共享海报飞行拥有帧预算：只有它落地后才挂载完整页面，然后让新组合稳定，
    // 再开始组件揭示。
    withFrameNanos {}
    awaitMainMessageQueueIdle()
    withFrameNanos {}
    session?.deferRootEnterComments = false
    withFrameNanos {}
    awaitMainMessageQueueIdle()
    withFrameNanos {}
    if (session?.reverseRequested == true) return@launchTransition
    session
      ?.panelAlpha
      ?.animateTo(
        1f,
        tween(if (settings.reduceMotion) 90 else 190, easing = FastOutSlowInEasing),
      )
    if (session?.reverseRequested == true) return@launchTransition
    loadActiveBangumiMetadata(restoredCard)
    dataCommitAllowedId = item.id
    if (session != null) session.phase = SessionPhase.WAITING_FIRST_FRAME
    else transitionPhase = TransitionPhase.Video(item, null)
    playerActivationId = item.id
    playerViewModel.loadVideo(item, preferredResolutionMode = currentPreferredResolutionMode())
  }
}

fun startProfileBangumi(
  profileEntryId: Long,
  card: SpaceContentCard,
  cardBounds: Rect,
) {
  val sourceEntry = activeProfileEntry(profileEntryId) ?: return
  val sourceState = sourceEntry.state
  val sourceMid = sourceState.profileMid ?: return
  if (
    card.videoUrl.isBlank() ||
      transitionSession != null ||
      activeBangumiPage != null ||
      transitionPhase !is TransitionPhase.Feed && transitionPhase !is TransitionPhase.Video
  ) {
    return
  }
  val bounds = cardBounds.takeIf { it.hasUsableSize() }
  val fromVideo = transitionPhase is TransitionPhase.Video
  val currentVideo = appState.selectedVideo
  val restoredCard = restoredBangumiCard(card)
  val item =
    restoredCard.toBangumiVideoItem(
      uploader = sourceState.spaceProfile?.name,
      uploaderFace = sourceState.spaceProfile?.face,
      uploaderMid = sourceMid,
    )
  if (restoredCard.episodeId != card.episodeId || restoredCard.seasonId != card.seasonId) {
    videoEntryCache.remove(item.id)
  }
  keyboardController?.hide()
  if (fromVideo && currentVideo != null) cacheEntry(snapshotEntry(currentVideo))
  val expandedStack =
    (if (fromVideo) videoStack else emptyList()) +
      StackFrame(
        entryId = item.id,
        item = item,
        parentPage = PageOrigin.Profile(sourceEntry.entryId, sourceMid),
        sourceCardBounds = bounds,
        sourceProfile = sourceState.snapshotProfile(sourceMid),
      )
  videoStack =
    if (expandedStack.size <= MAX_VIDEO_STACK_DEPTH) expandedStack
    else expandedStack.takeLast(MAX_VIDEO_STACK_DEPTH)
  activeBangumiPage =
    ActiveBangumiPage(
      sourceCard = card,
      sourceProfileEntryId = profileEntryId,
      sourceMid = sourceMid,
      sourceBounds = bounds,
      sourceFollowedByViewer = sourceMid == authUserInfo.mid,
    )
  bangumiPosterBounds = Rect.Zero
  dataCommitAllowedId = null
  playerActivationId = null
  showEmbeddedCover = false
  videoEntryCache[item.id]?.let(::restoreEntryForFreshPlayback) ?: clearVisibleVideoData()
  val session = bounds?.let { source ->
    CardTransitionSession(
        token = ++transitionToken,
        kind = TransitionKind.ENTER_PROFILE,
        item = item,
        startBounds = source,
        endBounds = source,
        initialProgress = 0f,
        initialPanelAlpha = 0f,
        fitCover = true,
        requiredSignals = playerTransitionRequiredSignals,
      )
      .also {
        it.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
        transitionSession = it
      }
  }
  transitionPhase = TransitionPhase.ToVideo(item, bounds, fromVideo = fromVideo)
  if (!fromVideo) playerBounds = Rect.Zero
  mainViewModel.openVideo(item)
  launchTransition {
    withFrameNanos {}
    val target =
      session?.let { prepareCardTransition(it) { bangumiPosterBounds } }
        ?: bangumiPosterBounds.takeIf { it.hasUsableSize() }
        ?: Rect.Zero
    if (!target.hasUsableSize()) {
      session?.apply {
        phase = SessionPhase.CANCELLED
        preparation.cancel()
      }
      if (transitionSession === session) transitionSession = null
      profileLayerSuppressed = true
      hiddenProfileCoverItemId = null
      profileVideoTransitionActive = false
      transitionPhase = TransitionPhase.Video(item, null)
      loadActiveBangumiMetadata(restoredCard)
      dataCommitAllowedId = item.id
      playerActivationId = item.id
      showEmbeddedCover = true
      playerViewModel.loadVideo(item, preferredResolutionMode = currentPreferredResolutionMode())
      return@launchTransition
    }
    session?.endBounds = target
    hiddenProfileCoverItemId = card.id
    profileVideoTransitionActive = true
    session?.apply {
      backgroundStarted = true
      phase = SessionPhase.FLYING
    }
    if (session != null) {
      coroutineScope {
        launch {
          session.progress.animateTo(
            1f,
            tween(if (settings.reduceMotion) 140 else 400, easing = FastOutSlowInEasing),
          )
        }
        launch {
          delay(if (settings.reduceMotion) 10 else 45)
          session.backgroundAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
          )
        }
      }
      if (session.reverseRequested) return@launchTransition
    }
    loadActiveBangumiMetadata(restoredCard)
    profileLayerSuppressed = true
    hiddenProfileCoverItemId = null
    profileVideoTransitionActive = false
    session?.phase = SessionPhase.REVEALING_BACKGROUND
    withFrameNanos {}
    session
      ?.panelAlpha
      ?.animateTo(
        1f,
        tween(if (settings.reduceMotion) 90 else 190, easing = FastOutSlowInEasing),
      )
    if (session?.reverseRequested == true) return@launchTransition
    dataCommitAllowedId = item.id
    if (session != null) session.phase = SessionPhase.WAITING_FIRST_FRAME
    else transitionPhase = TransitionPhase.Video(item, null)
    playerActivationId = item.id
    playerViewModel.loadVideo(item, preferredResolutionMode = currentPreferredResolutionMode())
  }
}

}

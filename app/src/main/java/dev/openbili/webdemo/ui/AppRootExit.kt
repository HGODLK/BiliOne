package dev.openbili.webdemo.ui

/**
 * 视频/番剧退出导航上下文：负责各类来源的返回转场（根信息流、搜索、番剧首页、
 * 历史、资料页、播放结束推荐），实现"封面替换 → 页面淡出 → 封面飞回"的三段式
 * 返回与返回目的地的恢复。
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import dev.openbili.webdemo.MainViewModel
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.api.BangumiSection
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.bangumi.BangumiExploreViewModel
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedUiState
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.PreferredResolutionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * 视频/番剧退出导航上下文。
 */
internal class AppRootExitContext(
  val scope: CoroutineScope,
  val keyboardController: SoftwareKeyboardController?,
  val videoEntryCache: LinkedHashMap<String, VideoPageEntry>,
  val playerViewHolder: Array<HeldPlayerView?>,
  val playerViewModel: PlayerViewModel,
  val mainViewModel: MainViewModel,
  val bangumiExploreViewModel: BangumiExploreViewModel,
  val videoState: AppRootVideoState,
  val playerSession: AppRootPlayerSessionState,
  val profileState: AppRootProfileState,
  val feedState: FeedUiState,
  val renderedVideoId: String?,
  val appStateState: State<AppUiState>,
  val settingsState: State<AppSettings>,
  val playerStateState: State<PlayerState>,
  val feedGridState: LazyGridState,
  val popularCardBounds: MutableMap<String, Rect>,
  val feedCardBounds: MutableMap<String, Rect>,
  val myCardBounds: SnapshotStateMap<String, Rect>,
  val searchCardBounds: SnapshotStateMap<String, Rect>,
  val bangumiIndexCardBounds: SnapshotStateMap<String, Rect>,
  val articleVideoBounds: SnapshotStateMap<String, Rect>,
  val profileCardBounds: SnapshotStateMap<ProfileVideoKey, Rect>,
  val bangumiSeasonExitFadeAlpha: Animatable<Float, AnimationVector1D>,
  val videoStackState: MutableState<List<StackFrame>>,
  val showEmbeddedCoverState: MutableState<Boolean>,
  val transitionPhaseState: MutableState<TransitionPhase>,
  val transitionSessionState: MutableState<CardTransitionSession?>,
  val transitionTokenState: MutableState<Long>,
  val playerActivationIdState: MutableState<String?>,
  val dataCommitAllowedIdState: MutableState<String?>,
  val rootPlayerOwnershipState: MutableState<RootPlayerOwnership>,
  val hiddenArticleVideoCoverItemIdState: MutableState<String?>,
  val hiddenMyCoverItemIdState: MutableState<String?>,
  val hiddenSearchCoverItemIdState: MutableState<String?>,
  val hiddenProfileCoverItemIdState: MutableState<String?>,
  val profileLayerSuppressedState: MutableState<Boolean>,
  val hiddenPopularCoverItemIdState: MutableState<String?>,
  val hiddenHomeDynamicCoverItemIdState: MutableState<String?>,
  val hiddenFeedCoverItemIdState: MutableState<String?>,
  val hiddenBangumiRecommendationItemIdState: MutableState<String?>,
  val hiddenBangumiIndexItemIdState: MutableState<String?>,
  val bangumiPosterBoundsState: MutableState<Rect>,
  val activeBangumiPageState: MutableState<ActiveBangumiPage?>,
  val deferSearchBangumiPageCompositionState: MutableState<Boolean>,
  val deferBangumiIndexPageCompositionState: MutableState<Boolean>,
  val deferBangumiHomePageCompositionState: MutableState<Boolean>,
  val playerBoundsState: MutableState<Rect>,
  val videoPageDataReadyIdState: MutableState<String?>,
  val profileVideoTransitionActiveState: MutableState<Boolean>,
  val bangumiPreviewTargetState: MutableState<BangumiPreviewTarget?>,
  val hiddenRecommendationCoverItemIdState: MutableState<String?>,
  val hiddenPlaybackEndRecommendationCoverItemIdState: MutableState<String?>,
  val videoExitPreludeState: MutableState<VideoExitPrelude?>,
  val profileBangumiReturnRequestState: MutableState<ProfileBangumiReturnRequest?>,
  val activeRevealJobState: MutableState<Job?>,
  val activeTransitionJobState: MutableState<Job?>,
  val launchTransitionRef: (suspend CoroutineScope.() -> Unit) -> Unit,
  val prepareCardTransitionRef: suspend (CardTransitionSession, () -> Rect) -> Rect,
  val prepareExitTransitionRef: suspend (CardTransitionSession, () -> Rect?) -> Rect,
  val currentPreferredResolutionModeRef: () -> PreferredResolutionMode,
  val restoreEntryRef: (VideoPageEntry) -> Unit,
  val restoreEntryForFreshPlaybackRef: (VideoPageEntry) -> Unit,
  val snapshotEntryRef: (FeedItem) -> VideoPageEntry,
  val ensureVideoPageDataRef: (FeedItem) -> Unit,
  val clearVisibleVideoDataRef: () -> Unit,
  val cacheEntryRef: (VideoPageEntry) -> Unit,
  val retainedPlaybackPageRef: (String) -> VideoPage?,
  val commitPlaybackProgressRef: () -> Unit,
  val loadProfileRef: (Long) -> Unit,
  val restoreProfileRef: (ProfilePageEntry) -> Unit,
  val activeProfileEntryRef: (Long?) -> ProfileStackEntry?,
) {
  val appState by appStateState
  val settings by settingsState
  val playerState by playerStateState
  var videoStack by videoStackState
  var showEmbeddedCover by showEmbeddedCoverState
  var transitionPhase by transitionPhaseState
  var transitionSession by transitionSessionState
  var transitionToken by transitionTokenState
  var playerActivationId by playerActivationIdState
  var dataCommitAllowedId by dataCommitAllowedIdState
  var rootPlayerOwnership by rootPlayerOwnershipState
  var hiddenArticleVideoCoverItemId by hiddenArticleVideoCoverItemIdState
  var hiddenMyCoverItemId by hiddenMyCoverItemIdState
  var hiddenSearchCoverItemId by hiddenSearchCoverItemIdState
  var hiddenProfileCoverItemId by hiddenProfileCoverItemIdState
  var profileLayerSuppressed by profileLayerSuppressedState
  var hiddenPopularCoverItemId by hiddenPopularCoverItemIdState
  var hiddenHomeDynamicCoverItemId by hiddenHomeDynamicCoverItemIdState
  var hiddenFeedCoverItemId by hiddenFeedCoverItemIdState
  var hiddenBangumiRecommendationItemId by hiddenBangumiRecommendationItemIdState
  var hiddenBangumiIndexItemId by hiddenBangumiIndexItemIdState
  var bangumiPosterBounds by bangumiPosterBoundsState
  var activeBangumiPage by activeBangumiPageState
  var deferSearchBangumiPageComposition by deferSearchBangumiPageCompositionState
  var deferBangumiIndexPageComposition by deferBangumiIndexPageCompositionState
  var deferBangumiHomePageComposition by deferBangumiHomePageCompositionState
  var playerBounds by playerBoundsState
  var videoPageDataReadyId by videoPageDataReadyIdState
  var profileVideoTransitionActive by profileVideoTransitionActiveState
  var bangumiPreviewTarget by bangumiPreviewTargetState
  var hiddenRecommendationCoverItemId by hiddenRecommendationCoverItemIdState
  var hiddenPlaybackEndRecommendationCoverItemId by hiddenPlaybackEndRecommendationCoverItemIdState
  var videoExitPrelude by videoExitPreludeState
  var profileBangumiReturnRequest by profileBangumiReturnRequestState
  var activeRevealJob by activeRevealJobState
  var activeTransitionJob by activeTransitionJobState
  var danmaku by videoState::danmaku
  var currentPositionMs by playerSession::currentPositionMs
  var isPlaying by playerSession::isPlaying
  var playbackEnded by playerSession::playbackEnded
  var commentProfileTransition by profileState::commentProfileTransition
  var avatarProfileTransition by profileState::avatarProfileTransition

  fun launchTransition(block: suspend CoroutineScope.() -> Unit) = launchTransitionRef(block)
  suspend fun prepareCardTransition(session: CardTransitionSession, targetBounds: (() -> Rect)? = null): Rect =
    prepareCardTransitionRef(session, targetBounds ?: { playerBounds })
  suspend fun prepareExitTransition(session: CardTransitionSession, targetBounds: () -> Rect?): Rect =
    prepareExitTransitionRef(session, targetBounds)
  fun currentPreferredResolutionMode(): PreferredResolutionMode = currentPreferredResolutionModeRef()
  fun restoreEntry(entry: VideoPageEntry) = restoreEntryRef(entry)
  fun restoreEntryForFreshPlayback(entry: VideoPageEntry) = restoreEntryForFreshPlaybackRef(entry)
  fun snapshotEntry(item: FeedItem): VideoPageEntry = snapshotEntryRef(item)
  fun ensureVideoPageData(item: FeedItem) = ensureVideoPageDataRef(item)
  fun clearVisibleVideoData() = clearVisibleVideoDataRef()
  fun cacheEntry(entry: VideoPageEntry) = cacheEntryRef(entry)
  fun retainedPlaybackPage(itemId: String): VideoPage? = retainedPlaybackPageRef(itemId)
  fun commitPlaybackProgress() = commitPlaybackProgressRef()
  fun loadProfile(mid: Long) = loadProfileRef(mid)
  fun restoreProfile(entry: ProfilePageEntry) = restoreProfileRef(entry)
  fun activeProfileEntry(entryId: Long? = null): ProfileStackEntry? = activeProfileEntryRef(entryId)

fun beginVideoExitPrelude(
  item: FeedItem,
  bounds: Rect,
  fitCover: Boolean = false,
  reusePlayerSurface: Boolean = false,
): VideoExitPrelude {
  val transitionBitmap =
    LoadedFeedImageRegistry.bitmap(item.coverUrl, requireUncropped = fitCover)
      ?: activeBangumiPage
        ?.takeIf { it.sourceOrigin == PageOrigin.BangumiHome && !fitCover }
        ?.let { LoadedFeedImageRegistry.bitmap(bangumiPreviewCoverCacheKey(item.coverUrl)) }
  val prelude =
    VideoExitPrelude(
        item = item,
        playerBounds = bounds,
        fitCover = fitCover,
        reusePlayerSurface = reusePlayerSurface,
      )
      .also { it.transitionBitmap = transitionBitmap }
  // 弹幕 Surface 位于 Compose 之上：同步隐藏它，但保持视图与栅格缓存挂载——
  // 在这里分离会在 UI 线程释放它们并与飞行竞争帧预算。
  playerViewHolder[0]?.view?.hideDanmakuForTransition()
  videoExitPrelude = prelude
  playerViewModel.exoPlayer?.pause()
  return prelude
}

suspend fun animateVideoExitPrelude(
  prelude: VideoExitPrelude,
  onPageFade: (suspend () -> Unit)? = null,
) {
  // SurfaceView 可见性由 Back 回调之后的遍历提交：不要在同一帧启动封面动画，
  // 首个动画帧必须不含弹幕。
  withFrameNanos {}
  // 退出准备可能在前奏创建后填充了注册表。
  if (prelude.transitionBitmap == null) {
    prelude.transitionBitmap =
      LoadedFeedImageRegistry.bitmap(
        prelude.item.coverUrl,
        requireUncropped = prelude.fitCover,
      )
        ?: activeBangumiPage
          ?.takeIf { it.sourceOrigin == PageOrigin.BangumiHome && !prelude.fitCover }
          ?.let {
            LoadedFeedImageRegistry.bitmap(bangumiPreviewCoverCacheKey(prelude.item.coverUrl))
          }
  }
  // 封面必须在目的地开始变化前完全替换直播画面。
  prelude.coverAlpha.animateTo(
    1f,
    tween(if (settings.reduceMotion) 70 else 140, easing = FastOutSlowInEasing),
  )
  withFrameNanos {}
  rootPlayerOwnership =
    RootPlayerOwnership(
      RootPlayerSurfaceRole.EXIT_COVERED,
      rootPlayerOwnership.mediaId ?: prelude.item.id,
    )
  // 在完全不透明的静止封面之下提交所有权变更，然后再让它移动。
  withFrameNanos {}
  withFrameNanos {}
  kotlinx.coroutines.coroutineScope {
    launch {
      prelude.pageAlpha.animateTo(
        0f,
        tween(if (settings.reduceMotion) 90 else 200, easing = FastOutSlowInEasing),
      )
    }
    onPageFade?.let { fade -> launch { fade() } }
  }
}

suspend fun fadeOutVideoExitPrelude(prelude: VideoExitPrelude) {
  prelude.coverAlpha.animateTo(
    0f,
    tween(if (settings.reduceMotion) 80 else 180, easing = FastOutSlowInEasing),
  )
  if (videoExitPrelude === prelude) videoExitPrelude = null
}

suspend fun fadeBangumiPageDirectly(prelude: VideoExitPrelude) {
  val duration = if (settings.reduceMotion) 90 else 220
  val surfaceAlpha = Animatable(1f)
  coroutineScope {
    launch {
      prelude.pageAlpha.animateTo(
        0f,
        tween(duration, easing = FastOutSlowInEasing),
      )
    }
    launch {
      surfaceAlpha.animateTo(
        0f,
        tween(duration, easing = FastOutSlowInEasing),
      ) {
        playerViewHolder[0]?.view?.updateVideoSurfaceAlpha(value)
      }
    }
  }
}

fun startExitRootBangumi(
  page: ActiveBangumiPage,
  departing: FeedItem,
) {
  val returnPreviewTarget = bangumiPreviewTarget.takeIf {
    page.sourceOrigin == PageOrigin.BangumiHome
  }
  // 竖版来源不能只按入口页面判断：历史/个人页也可能放置竖版番剧卡。
  val measuredPortraitSource =
    page.sourceBounds?.takeIf(Rect::hasUsableSize)?.let { bounds ->
      bounds.height > bounds.width * 1.08f
    } == true
  val portraitSource =
    measuredPortraitSource ||
      page.sourceOrigin == PageOrigin.Search ||
      page.sourceOrigin == PageOrigin.BangumiIndex
  val reusePlayerSurface =
    page.sourceUsesLivePlayer &&
      page.currentEpisodeId == page.sourceCard.episodeId &&
      renderedVideoId == departing.id
  val savedPosterBounds = if (portraitSource) bangumiPosterBounds else playerBounds
  // 竖版来源卡代表被点击的作品，而不是详情里当前选中的季度：只要稳定的来源
  // 边界仍存在，它们始终接收原封面返回。
  val skipPosterFlight =
    page.seasonChangedFromSource &&
      page.sourceOrigin !in setOf(PageOrigin.Search, PageOrigin.BangumiIndex)
  fun currentDestination(): Rect? {
    val exploreBounds =
      if (
        page.sourceOrigin == PageOrigin.BangumiHome || page.sourceIsBangumiExplorePoster
      ) {
        bangumiExploreViewModel.currentSourceBounds(page.sourceCard.id)
      } else {
        null
      }
    return exploreBounds
      ?: when (page.sourceOrigin) {
        PageOrigin.My -> myCardBounds[page.sourceCard.id]
        PageOrigin.Search -> searchCardBounds[page.sourceCard.id]
        PageOrigin.BangumiIndex -> bangumiIndexCardBounds[page.sourceCard.id]
        else -> null
      }
  }
  val destination =
    if (skipPosterFlight) null
    else currentDestination() ?: page.sourceBounds
  val currentEpisode =
    page.season
      ?.let { it.episodes + it.sections.flatMap(BangumiSection::episodes) }
      ?.firstOrNull { it.id == page.currentEpisodeId }
  val currentEpisodeCover = currentEpisode?.coverUrl.orEmpty().ifBlank { departing.coverUrl }
  if (
    page.sourceCard.seasonId > 0L &&
      page.season?.seasonId == page.sourceCard.seasonId &&
      currentEpisode != null
  ) {
    // 在取消播放器加载前抓取最后位置：趁退出封面仍不透明时更新隐藏的目的地卡。
    bangumiExploreViewModel.applyFollowingPlayback(
      seasonId = page.sourceCard.seasonId,
      episode = currentEpisode,
      positionMs = playerViewModel.exoPlayer?.currentPosition ?: currentPositionMs,
    )
  }
  val posterItem =
    departing.copy(
      id = page.sourceCard.id,
      title = page.season?.title ?: page.sourceCard.title,
      // 搜索卡保留竖版季度封面；保留的番剧轨道使用当前选中剧集的横版封面，
      // 让目的地与飞行保持一致。
      coverUrl =
        if (portraitSource) {
          // 搜索返回必须复用完全相同的来源请求键/位图：季度元数据可能把同一 CDN
          // 封面拼写不同，否则会触发二次解码并在目的地卡出现可见交接。
          page.sourceCard.coverUrl
        } else if (page.sourceOrigin == PageOrigin.BangumiHome && page.returnToSourceCover) {
          page.sourceVideoCoverUrl.ifBlank { page.sourceCard.coverUrl }
        } else if (page.sourceOrigin == PageOrigin.BangumiHome) {
          currentEpisodeCover.ifBlank { page.sourceVideoCoverUrl }
        } else {
          page.sourceVideoCoverUrl.ifBlank { departing.coverUrl }
        },
    )
  val prelude =
    beginVideoExitPrelude(
      posterItem,
      bounds = if (skipPosterFlight) Rect.Zero else savedPosterBounds,
      fitCover = portraitSource && !skipPosterFlight,
      reusePlayerSurface = reusePlayerSurface && !skipPosterFlight,
    )
  playerViewModel.cancelPendingLoad()
  launchTransition {
    var bangumiHandoffPrepared = false
    if (skipPosterFlight) {
      fadeBangumiPageDirectly(prelude)
    } else {
      val exitSession =
        if (destination?.hasUsableSize() == true && savedPosterBounds.hasUsableSize()) {
          CardTransitionSession(
              token = ++transitionToken,
              kind = TransitionKind.EXIT_ROOT,
              item = posterItem,
              startBounds = destination,
              endBounds = savedPosterBounds,
              initialProgress = 1f,
              initialPanelAlpha = 0f,
              fitCover = portraitSource,
              reusePlayerSurface = reusePlayerSurface,
              requiredSignals = exitTransitionRequiredSignals,
            )
            .also {
              it.preparation.markReady(
                TransitionReadySignal.SOURCE_BOUNDS,
                TransitionReadySignal.SOURCE_SNAPSHOT,
              )
              transitionSession = it
            }
        } else null
      if (exitSession != null) {
        val preparedBounds =
          prepareExitTransition(exitSession) {
            currentDestination() ?: destination
          }
        if (preparedBounds.hasUsableSize()) exitSession.startBounds = preparedBounds
        if (page.sourceOrigin == PageOrigin.My) hiddenMyCoverItemId = page.sourceCard.id
        if (page.sourceOrigin == PageOrigin.Search) {
          if (page.sourceIsBangumiExplorePoster) {
            hiddenBangumiRecommendationItemId = page.sourceCard.id
          } else {
            hiddenSearchCoverItemId = page.sourceCard.id
          }
        }
        if (page.sourceOrigin == PageOrigin.BangumiIndex) {
          hiddenBangumiIndexItemId = page.sourceCard.id
        }
        if (page.sourceOrigin == PageOrigin.BangumiHome && !reusePlayerSurface) {
          hiddenBangumiRecommendationItemId = page.sourceCard.id
        }
        withFrameNanos {}
        // 番剧 Hero 是稳定的保留层：先用静止右卡封面替换详情播放器，只淡出详情
        // 页；再把那张封面移回它的卡片。两个阶段之间不要放全屏主题桥接，因为它
        // 会短暂隐藏/显示 Hero，让它看起来像第二个共享元素。
        animateVideoExitPrelude(prelude) {
          if (portraitSource) {
            exitSession.panelAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 90 else 200, easing = FastOutSlowInEasing),
            )
          }
        }
        exitSession.phase = SessionPhase.FLYING
        withFrameNanos {}
        if (videoExitPrelude === prelude) videoExitPrelude = null
        exitSession.progress.animateTo(
          0f,
          tween(if (settings.reduceMotion) 140 else 340, easing = FastOutSlowInEasing),
        )
        if (page.sourceOrigin == PageOrigin.BangumiHome) {
          // 首页预览拥有自己的 TextureView 播放器：详情播放器现在是热的空闲态，
          // 而保留的首页在返回卡封面之下重新可见。
          rootPlayerOwnership = RootPlayerOwnership(RootPlayerSurfaceRole.IDLE)
          exitSession.phase = SessionPhase.REVEALING_BACKGROUND
          withFrameNanos {}
          withFrameNanos {}
          bangumiHandoffPrepared = true
        }
      } else {
        animateVideoExitPrelude(prelude)
        fadeOutVideoExitPrelude(prelude)
      }
    }
    if (page.sourceOrigin == PageOrigin.BangumiHome && !bangumiHandoffPrepared) {
      rootPlayerOwnership = RootPlayerOwnership(RootPlayerSurfaceRole.IDLE)
      withFrameNanos {}
      withFrameNanos {}
    }
    if (
      page.sourceOrigin != PageOrigin.BangumiHome &&
        (reusePlayerSurface || returnPreviewTarget != null)
    ) {
      rootPlayerOwnership = RootPlayerOwnership(RootPlayerSurfaceRole.IDLE)
      withFrameNanos {}
    }
    // 大页面状态的快照与播放进度写入刻意放在移动区间之外：它们可能分配或排队
    // IO，但不能再偷走动画帧。
    cacheEntry(snapshotEntry(departing))
    commitPlaybackProgress()
    dataCommitAllowedId = null
    playerActivationId = null
    mainViewModel.returnToFeed()
    videoStack = emptyList()
    bangumiPosterBounds = Rect.Zero
    transitionPhase = TransitionPhase.Feed
    if (page.sourceOrigin == PageOrigin.BangumiHome) {
      // 在释放转场会话前提交现已可见的保留根页面：媒体预览激活仍被门控，
      // 因此没有任何 PV 准备与卡片飞行竞争。
      withFrameNanos {}
      withFrameNanos {}
    }
    activeBangumiPage = null
    transitionSession = null
    hiddenMyCoverItemId = null
    hiddenSearchCoverItemId = null
    hiddenBangumiIndexItemId = null
    hiddenBangumiRecommendationItemId = null
    withFrameNanos {}
    if (page.sourceOrigin == PageOrigin.BangumiHome && page.sourceCard.seasonId > 0L) {
      // 返回的封面已落在来源卡上：短暂稳定后，把刚看过的季度移到保留"正在追"
      // 轨道最前。moveFollowingToFront() 在季度不在轨道时为空操作，animateItem()
      // 驱动重排。
      val frontSeasonId = page.sourceCard.seasonId
      scope.launch {
        delay(220)
        bangumiExploreViewModel.moveFollowingToFront(frontSeasonId)
      }
    }
    if (skipPosterFlight) {
      playerViewHolder[0]?.view?.updateVideoSurfaceAlpha(1f)
    }
    if (videoExitPrelude === prelude) videoExitPrelude = null
  }
}

fun startExitBangumi() {
  if (transitionSession != null || videoExitPrelude != null) return
  val page = activeBangumiPage ?: return
  val departing = appState.selectedVideo ?: return
  val departingFrame = videoStack.lastOrNull() ?: return
  if (
    page.sourceOrigin == PageOrigin.My ||
      page.sourceOrigin == PageOrigin.Search ||
      page.sourceOrigin == PageOrigin.BangumiIndex ||
      page.sourceOrigin == PageOrigin.BangumiHome
  ) {
    startExitRootBangumi(page, departing)
    return
  }
  val origin = departingFrame.parentPage as? PageOrigin.Profile ?: return
  val savedPosterBounds = bangumiPosterBounds
  val skipPosterFlight = page.seasonChangedFromSource
  val destinationKey = ProfileVideoKey(page.sourceProfileEntryId, page.sourceCard.id)
  val remainingStack = videoStack.dropLast(1)
  val parentFrame = remainingStack.lastOrNull()
  val posterItem =
    page.sourceCard
      .copy(
        title = page.season?.title ?: page.sourceCard.title,
        coverUrl = page.season?.coverUrl?.takeIf(String::isNotBlank) ?: page.sourceCard.coverUrl,
      )
      .toBangumiVideoItem(
        uploader = departing.uploader,
        uploaderFace = departing.uploaderFace,
        uploaderMid = departing.uploaderMid,
      )
  cacheEntry(snapshotEntry(departing))
  commitPlaybackProgress()
  val prelude =
    beginVideoExitPrelude(
      posterItem,
      bounds = if (skipPosterFlight) Rect.Zero else savedPosterBounds,
      fitCover = !skipPosterFlight,
    )
  playerViewModel.cancelPendingLoad()
  fun restoreUnderlyingParent() {
    if (parentFrame != null) {
      videoEntryCache[parentFrame.entryId]?.let(::restoreEntry) ?: clearVisibleVideoData()
      dataCommitAllowedId = parentFrame.item.id
      showEmbeddedCover = true
      mainViewModel.openVideo(parentFrame.item)
      transitionPhase = TransitionPhase.Video(parentFrame.item, parentFrame.sourceCardBounds)
      ensureVideoPageData(parentFrame.item)
    } else {
      mainViewModel.returnToFeed()
      dataCommitAllowedId = null
      playerActivationId = null
      transitionPhase = TransitionPhase.Feed
    }
  }
  launchTransition {
    val retainedProfile = activeProfileEntry(origin.entryId)
    if (retainedProfile == null) {
      departingFrame.sourceProfile?.let(::restoreProfile) ?: loadProfile(origin.mid)
    }
    // 资料页保持组合于播放器之下：像首页提升其最新"正在追"项一样重排刚看的
    // 作品，然后让保留网格把那个稳定 ID 滚入视野。丢弃点击时刻的坐标：其索引
    // 已不再权威。
    profileCardBounds.remove(destinationKey)
    profileBangumiReturnRequest =
      ProfileBangumiReturnRequest(
        token = System.nanoTime(),
        profileEntryId = page.sourceProfileEntryId,
        cardId = page.sourceCard.id,
      )
    var reorderedDestination: Rect? = null
    if (!skipPosterFlight) {
      var previousDestination: Rect? = null
      var stableFrames = 0
      for (frame in 0 until 60) {
        withFrameNanos {}
        val candidate = profileCardBounds[destinationKey]?.takeIf { it.hasUsableSize() }
        if (candidate != null) {
          stableFrames = if (candidate == previousDestination) stableFrames + 1 else 0
          previousDestination = candidate
          if (stableFrames >= 1) {
            reorderedDestination = candidate
            break
          }
        }
      }
    }
    val destination = reorderedDestination
    val exitSession =
      if (destination?.hasUsableSize() == true && savedPosterBounds.hasUsableSize()) {
        CardTransitionSession(
            token = ++transitionToken,
            kind = TransitionKind.EXIT_PROFILE,
            item = posterItem,
            startBounds = destination,
            endBounds = savedPosterBounds,
            initialProgress = 1f,
            initialPanelAlpha = 0f,
            fitCover = true,
            requiredSignals = exitTransitionRequiredSignals,
          )
          .also {
            it.preparation.markReady(
              TransitionReadySignal.SOURCE_BOUNDS,
              TransitionReadySignal.SOURCE_SNAPSHOT,
            )
            transitionSession = it
          }
      } else null
    profileVideoTransitionActive = exitSession != null
    if (exitSession != null) hiddenProfileCoverItemId = page.sourceCard.id
    profileLayerSuppressed = false
    commentProfileTransition = null
    if (skipPosterFlight) {
      // 不同的季度在保留资料页上不再有有意义的来源海报。SurfaceView 内容无法由
      // 父级 graphicsLayer 可靠淡出：通过不透明交接把整个窗口一起淡出，让播放器、
      // 海报与面板作为同一张图离开。
      bangumiSeasonExitFadeAlpha.snapTo(0f)
      coroutineScope {
        launch {
          prelude.pageAlpha.animateTo(
            0f,
            tween(if (settings.reduceMotion) 90 else 220, easing = FastOutSlowInEasing),
          )
        }
        launch {
          bangumiSeasonExitFadeAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 100 else 260, easing = FastOutSlowInEasing),
          )
        }
      }
      restoreUnderlyingParent()
      activeBangumiPage = null
      withFrameNanos {}
      if (videoExitPrelude === prelude) videoExitPrelude = null
      bangumiSeasonExitFadeAlpha.animateTo(
        0f,
        tween(if (settings.reduceMotion) 90 else 190, easing = FastOutSlowInEasing),
      )
    } else if (exitSession != null) {
      val preparedBounds =
        prepareExitTransition(exitSession) {
          profileCardBounds[destinationKey] ?: destination
        }
      if (preparedBounds.hasUsableSize()) exitSession.startBounds = preparedBounds
      withFrameNanos {}
      prelude.transitionBitmap = exitSession.transitionBitmap ?: prelude.transitionBitmap
      animateVideoExitPrelude(prelude) {
        exitSession.panelAlpha.animateTo(
          1f,
          tween(if (settings.reduceMotion) 90 else 200, easing = FastOutSlowInEasing),
        )
      }
      restoreUnderlyingParent()
      activeBangumiPage = null
      exitSession.phase = SessionPhase.FLYING
      withFrameNanos {}
      if (videoExitPrelude === prelude) videoExitPrelude = null
      exitSession.progress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 140 else 360, easing = FastOutSlowInEasing),
      )
    } else {
      animateVideoExitPrelude(prelude)
      restoreUnderlyingParent()
      activeBangumiPage = null
      fadeOutVideoExitPrelude(prelude)
    }
    withFrameNanos {}
    videoStack = remainingStack
    hiddenProfileCoverItemId = null
    withFrameNanos {}
    transitionSession = null
    profileVideoTransitionActive = false
    profileBangumiReturnRequest = null
    bangumiPosterBounds = Rect.Zero
    if (parentFrame != null) {
      dataCommitAllowedId = parentFrame.item.id
      playerActivationId = null
    }
  }
}

fun startExitVideoToProfile() {
  if (transitionSession != null || videoExitPrelude != null) return
  val departing = appState.selectedVideo ?: return
  val departingFrame = videoStack.lastOrNull() ?: return
  val origin = departingFrame.parentPage as? PageOrigin.Profile ?: return
  val savedPlayerBounds = playerBounds
  val destinationKey = ProfileVideoKey(origin.entryId, departing.id)
  val destination =
    if (departingFrame.inPlaceSelectionChanged) null
    else departingFrame.sourceCardBounds ?: profileCardBounds[destinationKey]
  val remainingStack = videoStack.dropLast(1)
  val parentFrame = remainingStack.lastOrNull()
  cacheEntry(snapshotEntry(departing))
  commitPlaybackProgress()
  val prelude = beginVideoExitPrelude(departing, savedPlayerBounds)
  playerViewModel.cancelPendingLoad()
  fun restoreUnderlyingParent() {
    if (parentFrame != null) {
      videoEntryCache[parentFrame.entryId]?.let(::restoreEntry) ?: clearVisibleVideoData()
      dataCommitAllowedId = parentFrame.item.id
      showEmbeddedCover = true
      mainViewModel.openVideo(parentFrame.item)
      transitionPhase = TransitionPhase.Video(parentFrame.item, parentFrame.sourceCardBounds)
      ensureVideoPageData(parentFrame.item)
    } else {
      mainViewModel.returnToFeed()
      dataCommitAllowedId = null
      playerActivationId = null
      transitionPhase = TransitionPhase.Feed
    }
  }

  launchTransition {
    val exitSession =
      if (
        destination != null &&
          destination != Rect.Zero &&
          savedPlayerBounds != Rect.Zero &&
          savedPlayerBounds.width > 0f
      ) {
        CardTransitionSession(
            token = ++transitionToken,
            kind = TransitionKind.EXIT_PROFILE,
            item = departing,
            startBounds = destination,
            endBounds = savedPlayerBounds,
            initialProgress = 1f,
            initialPanelAlpha = 0f,
            requiredSignals = exitTransitionRequiredSignals,
          )
          .also {
            it.preparation.markReady(
              TransitionReadySignal.SOURCE_BOUNDS,
              TransitionReadySignal.SOURCE_SNAPSHOT,
            )
            transitionSession = it
          }
      } else null
    // 来源资料页保留在播放器之下，因此其直播卡坐标仍是权威返回目标：等两帧
    // 匹配后再揭示它；与番剧列表不同，普通视频卡在播放打开期间不会重排。
    profileVideoTransitionActive = exitSession != null
    profileLayerSuppressed = false
    val retainedProfile = activeProfileEntry(origin.entryId)
    if (retainedProfile == null) {
      departingFrame.sourceProfile?.let(::restoreProfile) ?: loadProfile(origin.mid)
    }
    commentProfileTransition = null
    if (exitSession != null) {
      // 等待保留网格的那张卡保持稳定后再开始飞行。
      var remountedDestination: Rect? = null
      var previousDestination: Rect? = null
      var stableFrames = 0
      for (frame in 0 until 18) {
        withFrameNanos {}
        val candidate =
          (profileCardBounds[destinationKey] ?: destination)?.takeIf { it.hasUsableSize() }
        if (candidate != null) {
          stableFrames = if (candidate == previousDestination) stableFrames + 1 else 0
          previousDestination = candidate
          if (stableFrames >= 1) {
            remountedDestination = candidate
            break
          }
        }
      }
      if (remountedDestination == null) {
        exitSession.phase = SessionPhase.CANCELLED
        exitSession.preparation.cancel()
        if (transitionSession === exitSession) transitionSession = null
        profileVideoTransitionActive = false
        animateVideoExitPrelude(prelude)
        restoreUnderlyingParent()
        fadeOutVideoExitPrelude(prelude)
      } else {
        exitSession.startBounds = remountedDestination
        val preparedBounds =
          prepareExitTransition(exitSession) {
            profileCardBounds[destinationKey] ?: remountedDestination
          }
        if (preparedBounds.hasUsableSize()) exitSession.startBounds = preparedBounds
        hiddenProfileCoverItemId = departing.id
        // 恢复的资料页已由 prepareExitTransition 挂载：让它在视频页之下保持完全
        // 可见，隐藏其目的地封面，然后开始有序的封面/页面淡出。
        withFrameNanos {}
        animateVideoExitPrelude(prelude) {
          exitSession.panelAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 90 else 200, easing = FastOutSlowInEasing),
          )
        }
        restoreUnderlyingParent()
        exitSession.phase = SessionPhase.FLYING
        withFrameNanos {}
        if (videoExitPrelude === prelude) videoExitPrelude = null
        exitSession.progress.animateTo(
          0f,
          tween(if (settings.reduceMotion) 140 else 360, easing = FastOutSlowInEasing),
        )
      }
    } else {
      animateVideoExitPrelude(prelude)
      restoreUnderlyingParent()
      fadeOutVideoExitPrelude(prelude)
    }
    withFrameNanos {}
    videoStack = remainingStack
    // 先在 p=0 覆盖层之下揭示真实卡封面，再交叉淡出覆盖层。飞行副本与真实封面
    // 使用不同解码，同帧瞬时交换会在落地位置闪一下。
    hiddenProfileCoverItemId = null
    withFrameNanos {}
    val landingSession = transitionSession
    if (landingSession != null) {
      landingSession.coverAlpha.animateTo(
        0f,
        tween(if (settings.reduceMotion) 90 else 170, easing = FastOutSlowInEasing),
      )
      withFrameNanos {}
    }
    transitionSession = null
    profileVideoTransitionActive = false
    if (parentFrame != null) {
      dataCommitAllowedId = parentFrame.item.id
      // 在其资料页子页可见期间，底层父页保持选中但暂停。
      playerActivationId = null
    }
  }
}

fun cancelPreparingProfileVideo() {
  val departingFrame = videoStack.lastOrNull() ?: return
  if (departingFrame.parentPage !is PageOrigin.Profile) return
  activeTransitionJob?.cancel()
  activeTransitionJob = null
  playerViewModel.cancelPendingLoad()
  playerViewModel.exoPlayer?.pause()
  val remainingStack = videoStack.dropLast(1)
  val parentFrame = remainingStack.lastOrNull()
  if (parentFrame != null) {
    videoEntryCache[parentFrame.entryId]?.let(::restoreEntry) ?: clearVisibleVideoData()
    dataCommitAllowedId = parentFrame.item.id
    playerActivationId = null
    showEmbeddedCover = true
    mainViewModel.openVideo(parentFrame.item)
    transitionPhase = TransitionPhase.Video(parentFrame.item, parentFrame.sourceCardBounds)
  } else {
    mainViewModel.returnToFeed()
    dataCommitAllowedId = null
    playerActivationId = null
    transitionPhase = TransitionPhase.Feed
  }
  videoStack = remainingStack
  activeBangumiPage = null
  bangumiPosterBounds = Rect.Zero
  hiddenProfileCoverItemId = null
  profileVideoTransitionActive = false
  transitionSession = null
}

// ── 开始退出转场 ────────────────────────────────────────────
fun startExitVideo() {
  if (videoExitPrelude != null) return
  keyboardController?.hide()
  val item = appState.selectedVideo ?: return
  val frame = videoStack.firstOrNull() ?: return
  val prelude = beginVideoExitPrelude(item, playerBounds)
  cacheEntry(snapshotEntry(item))
  commitPlaybackProgress()
  val returnBounds = frame.sourceCardBounds.takeUnless { frame.inPlaceSelectionChanged }
  fun latestReturnBounds(): Rect? =
    when (frame.parentPage) {
      PageOrigin.Home ->
        when (frame.rootVideoOrigin) {
          VideoOrigin.HOME -> feedCardBounds[item.id]
          // 动态详情可能在同一视频的正文与评论中各出现一次：两者都报在动态 ID 下，
          // 因此最后测量的卡片有歧义。帧已经拥有用户点击的精确边界：保留该目的地。
          VideoOrigin.HOME_DYNAMIC -> frame.sourceCardBounds
          VideoOrigin.POPULAR -> popularCardBounds[item.id]
          else -> popularCardBounds[item.id] ?: feedCardBounds[item.id]
        }
      PageOrigin.My -> myCardBounds[item.id]
      PageOrigin.Search -> searchCardBounds[item.id]
      PageOrigin.Article -> articleVideoBounds[item.id]
      else -> null
    }

  fun resolveReturnBounds(fallback: Rect?): Rect? {
    val latest = latestReturnBounds()
    // 搜索在视频之下保持挂载，在中间的资料页导航后仍能上报播放器矩形；其它
    // 成熟根来源保持各自既有策略。
    return if (frame.parentPage == PageOrigin.Search)
      resolveExitTransitionTargetBounds(
        latest = latest,
        fallback = fallback,
        playerBounds = playerBounds,
      )
    else latest ?: fallback
  }

  transitionPhase = TransitionPhase.ToFeed(item, returnBounds)
  launchTransition {
    var bounds = returnBounds
    if (bounds != null) {
      if (frame.parentPage == PageOrigin.Home && frame.rootFeedScrollAnchor != null) {
        val itemCount =
          (feedState as? dev.openbili.webdemo.feed.FeedUiState.Content)?.items?.size ?: 0
        if (itemCount > 0) {
          val anchor = frame.rootFeedScrollAnchor
          feedGridState.scrollToItem(
            anchor.firstVisibleItemIndex.coerceIn(0, itemCount - 1),
            anchor.firstVisibleItemScrollOffset,
          )
          withFrameNanos {}
          withFrameNanos {}
        }
      }
      bounds = resolveReturnBounds(bounds)
    }
    transitionPhase = TransitionPhase.ToFeed(item, bounds)
    if (bounds != null && playerBounds != Rect.Zero) {
      val initialBounds = bounds
      val session =
        CardTransitionSession(
            token = ++transitionToken,
            kind = TransitionKind.EXIT_ROOT,
            item = item,
            startBounds = initialBounds,
            endBounds = playerBounds,
            initialProgress = 1f,
            initialPanelAlpha = 0f,
            requiredSignals = exitTransitionRequiredSignals,
          )
          .also {
            it.preparation.markReady(
              TransitionReadySignal.SOURCE_BOUNDS,
              TransitionReadySignal.SOURCE_SNAPSHOT,
            )
            transitionSession = it
          }
      val preparedBounds =
        prepareExitTransition(session) {
          resolveReturnBounds(initialBounds)
        }
      if (preparedBounds.hasUsableSize()) bounds = preparedBounds
      hiddenFeedCoverItemId = item.id.takeIf { frame.rootVideoOrigin == VideoOrigin.HOME }
      hiddenHomeDynamicCoverItemId =
        frame.sourceAnchorKey.takeIf { frame.rootVideoOrigin == VideoOrigin.HOME_DYNAMIC }
      hiddenPopularCoverItemId = item.id.takeIf { frame.rootVideoOrigin == VideoOrigin.POPULAR }
      hiddenMyCoverItemId = item.id.takeIf { frame.parentPage == PageOrigin.My }
      hiddenSearchCoverItemId = item.id.takeIf { frame.parentPage == PageOrigin.Search }
      hiddenArticleVideoCoverItemId = item.id.takeIf { frame.parentPage == PageOrigin.Article }
      // 保留的来源页在前奏期间可见：在淡出开始前隐藏其真实封面，让只有播放器
      // 位置的封面能交接进飞行层。
      withFrameNanos {}
      animateVideoExitPrelude(prelude)
      session.startBounds = bounds
      session.phase = SessionPhase.FLYING
      withFrameNanos {}
      if (videoExitPrelude === prelude) videoExitPrelude = null
      session.progress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 140 else 340, easing = FastOutSlowInEasing),
      )
      withFrameNanos {}
      // 先在 p=0 覆盖层之下揭示真实卡封面，再交叉淡出覆盖层。飞行副本与真实封面
      // 使用不同解码，同帧瞬时交换会在落地位置闪一下。
      hiddenFeedCoverItemId = null
      hiddenPopularCoverItemId = null
      hiddenHomeDynamicCoverItemId = null
      hiddenMyCoverItemId = null
      hiddenSearchCoverItemId = null
      hiddenArticleVideoCoverItemId = null
      withFrameNanos {}
      session.coverAlpha.animateTo(
        0f,
        tween(if (settings.reduceMotion) 90 else 170, easing = FastOutSlowInEasing),
      )
      withFrameNanos {}
    } else {
      animateVideoExitPrelude(prelude)
      mainViewModel.returnToFeed()
      videoStack = emptyList()
      transitionPhase = TransitionPhase.Feed
      hiddenFeedCoverItemId = null
      hiddenPopularCoverItemId = null
      hiddenHomeDynamicCoverItemId = null
      hiddenMyCoverItemId = null
      hiddenSearchCoverItemId = null
      hiddenArticleVideoCoverItemId = null
      withFrameNanos {}
      withFrameNanos {}
      fadeOutVideoExitPrelude(prelude)
      return@launchTransition
    }
    withFrameNanos {}
    mainViewModel.returnToFeed()
    videoStack = emptyList()
    transitionSession = null
    transitionPhase = TransitionPhase.Feed
  }
}

fun startBackToPreviousVideo() {
  if (videoExitPrelude != null) return
  keyboardController?.hide()
  if (videoStack.size < 2) return startExitVideo()
  val departingFrame = videoStack.last()
  val parentFrame = videoStack[videoStack.lastIndex - 1]
  val departing = appState.selectedVideo ?: return
  cacheEntry(snapshotEntry(departing))
  commitPlaybackProgress()
  // 在点击路径上同步暂停，先于封面开始替换实时画面。
  playerViewModel.exoPlayer?.pause()
  playerSession.isPlaying = false
  val parentEntry = videoEntryCache[parentFrame.entryId]
  val savedPlayerBounds = playerBounds
  val destination =
    departingFrame.sourceCardBounds.takeUnless {
      departingFrame.inPlaceSelectionChanged
    }
  // 保留已确立的三段式返回：封面替换实时子播放器 → 子页淡出 → 该被封面的播放器
  // 飞回父推荐卡。
  val prelude = beginVideoExitPrelude(departing, savedPlayerBounds)
  mainViewModel.onFullscreenChanged(false)
  playerViewModel.cancelPendingLoad()
  showEmbeddedCover = true
  transitionPhase =
    TransitionPhase.ToPreviousVideo(
      departingItem = departing,
      previousItem = parentFrame.item,
      cardBounds = destination ?: Rect.Zero,
      previousSourceBounds = parentFrame.sourceCardBounds,
    )
  launchTransition {
    val session =
      if (destination != null && destination != Rect.Zero && savedPlayerBounds != Rect.Zero) {
        CardTransitionSession(
            token = ++transitionToken,
            kind = TransitionKind.EXIT_RECOMMENDATION,
            item = departing,
            startBounds = destination,
            endBounds = savedPlayerBounds,
            initialProgress = 1f,
            // EXIT_RECOMMENDATION 把该值映射为 1 - panelAlpha：退出前奏淡出子面板
            // 期间保持它们可见，恢复父页后重置为 1，并在封面飞回时动画回 0。
            initialPanelAlpha = 0f,
            requiredSignals = exitTransitionRequiredSignals,
          )
          .also {
            it.preparation.markReady(
              TransitionReadySignal.SOURCE_BOUNDS,
              TransitionReadySignal.SOURCE_SNAPSHOT,
            )
            transitionSession = it
          }
      } else null
    // 立即开始可见前奏：返回目的地在子页打开时已冻结，此路径不得等待新图片
    // 解码或布局稳定屏障。
    animateVideoExitPrelude(prelude)
    val waitingForCommentMedia = videoState.commentMediaBounds.containsKey(departing.id)
    if (waitingForCommentMedia) videoState.commentMediaBounds.remove(departing.id)
    // 在离场封面开始飞回前恢复并组合父页。旧实现在飞行之后才做，导致覆盖层
    // 底下是空页面。
    if (parentEntry != null) restoreEntry(parentEntry) else clearVisibleVideoData()
    dataCommitAllowedId = parentFrame.item.id
    // 已完成的父页已拥有保留的结束卡：从推荐子视频返回期间，不要在其后重启普通
    // 的加载封面管线。
    showEmbeddedCover = parentEntry?.playbackEnded != true
    mainViewModel.openVideo(parentFrame.item)
    transitionPhase =
      TransitionPhase.ToPreviousVideo(
        departingItem = departing,
        previousItem = parentFrame.item,
        cardBounds = destination ?: Rect.Zero,
        previousSourceBounds = parentFrame.sourceCardBounds,
      )
    ensureVideoPageData(parentFrame.item)
    withFrameNanos {}
    withFrameNanos {}
    // 评论内媒体由网络解析后才进入评论组合树。若直接使用子页打开时冻结的坐标，
    // 解析完成后的卡片会在封面飞回之后才出现。等待同一视频引用重新上报稳定坐标，
    // 并把它作为本次返回的真实起点；普通推荐卡没有该记录时仍沿用原坐标。
    if (waitingForCommentMedia) {
      var previous = Rect.Zero
      var stableFrames = 0
      for (frame in 0 until 60) {
        withFrameNanos {}
        val candidate = videoState.commentMediaBounds[departing.id]?.takeIf { it.hasUsableSize() }
        if (candidate != null) {
          stableFrames = if (candidate == previous) stableFrames + 1 else 0
          previous = candidate
          if (stableFrames >= 1) break
        }
      }
      val liveDestination = videoState.commentMediaBounds[departing.id]?.takeIf { it.hasUsableSize() }
      if (liveDestination != null) {
        session?.startBounds = liveDestination
        transitionPhase =
          TransitionPhase.ToPreviousVideo(
            departingItem = departing,
            previousItem = parentFrame.item,
            cardBounds = liveDestination,
            previousSourceBounds = parentFrame.sourceCardBounds,
          )
      }
    }
    hiddenRecommendationCoverItemId =
      departing.id.takeUnless { departingFrame.sourceWasPlaybackEndRecommendation }
    hiddenPlaybackEndRecommendationCoverItemId =
      departing.id.takeIf { departingFrame.sourceWasPlaybackEndRecommendation }
    if (session != null) {
      session.panelAlpha.snapTo(1f)
      if (videoExitPrelude === prelude) videoExitPrelude = null
      session.phase = SessionPhase.FLYING
      coroutineScope {
        launch {
          session.progress.animateTo(
            0f,
            tween(if (settings.reduceMotion) 140 else 340, easing = FastOutSlowInEasing),
          )
        }
        launch {
          session.panelAlpha.animateTo(
            0f,
            tween(if (settings.reduceMotion) 100 else 240, easing = FastOutSlowInEasing),
          )
        }
      }
    } else {
      fadeOutVideoExitPrelude(prelude)
    }
    withFrameNanos {}
    // 先在 p=0 覆盖层之下揭示真实推荐封面，再交叉淡出覆盖层。飞行副本与真实封面
    // 使用不同解码，同帧瞬时交换会在落地位置闪一下。
    hiddenRecommendationCoverItemId = null
    hiddenPlaybackEndRecommendationCoverItemId = null
    withFrameNanos {}
    if (session != null) {
      session.coverAlpha.animateTo(
        0f,
        tween(if (settings.reduceMotion) 90 else 170, easing = FastOutSlowInEasing),
      )
      withFrameNanos {}
    }
    videoStack = videoStack.dropLast(1)
    transitionSession = null
    transitionPhase = TransitionPhase.Video(parentFrame.item, parentFrame.sourceCardBounds)
    dataCommitAllowedId = parentFrame.item.id
    val restore = videoEntryCache[parentFrame.entryId]
    if (restore?.playbackEnded == true) {
      // 栈返回应揭示父页的终态封面/结束卡，而不是在离场封面飞行之后准备媒体并
      // 意外重启播放。
      playerSession.restorePlaybackEnded(true)
      playerActivationId = null
    } else {
      rootPlayerOwnership =
        RootPlayerOwnership(RootPlayerSurfaceRole.DETAIL_PENDING, parentFrame.item.id)
      playerActivationId = parentFrame.item.id
      playerViewModel.loadVideo(
        parentFrame.item,
        startPositionMs = restore?.savedPositionMs ?: 0L,
        preferredStreamIndex = restore?.qualityIndex,
        preferredResolutionMode = currentPreferredResolutionMode(),
        page = retainedPlaybackPage(parentFrame.entryId),
      )
    }
  }
}

fun reverseActiveEnter() {
  val session = transitionSession ?: return
  if (
    session.reverseRequested ||
      session.kind !in
        setOf(
          TransitionKind.ENTER_ROOT,
          TransitionKind.ENTER_RECOMMENDATION,
          TransitionKind.ENTER_PROFILE,
        )
  )
    return
  session.reverseRequested = true
  session.preparation.cancel()
  if (session.kind == TransitionKind.ENTER_PROFILE) {
    playerViewModel.exoPlayer?.pause()
  }
  // 在推荐提交之前，playerActivationId 仍属于父页：飞行中途反转期间保持那次
  // 精确的 ExoPlayer 加载存活，而不是从缓存重建。
  val retainedRecommendationParent =
    if (session.kind == TransitionKind.ENTER_RECOMMENDATION)
      videoStack.dropLast(1).lastOrNull()?.takeIf { playerActivationId == it.item.id }
    else null
  if (retainedRecommendationParent == null) playerViewModel.cancelPendingLoad()
  activeRevealJob?.cancel()
  activeRevealJob = null
  launchTransition {
    session.phase = SessionPhase.CANCELLED
    val returningParent =
      if (
        session.kind == TransitionKind.ENTER_RECOMMENDATION ||
          session.kind == TransitionKind.ENTER_PROFILE
      )
        videoStack.dropLast(1).lastOrNull()
      else null
    val returningProfile =
      if (session.kind == TransitionKind.ENTER_PROFILE)
        (videoStack.lastOrNull()?.parentPage as? PageOrigin.Profile)
      else null
    if (returningProfile != null) {
      profileLayerSuppressed = false
      if (activeProfileEntry(returningProfile.entryId) == null) {
        videoStack.lastOrNull()?.sourceProfile?.let(::restoreProfile)
          ?: loadProfile(returningProfile.mid)
      }
      commentProfileTransition = null
      avatarProfileTransition = null
      hiddenProfileCoverItemId = session.item.id
      profileVideoTransitionActive = true
    }
    if (
      returningParent != null &&
        mainViewModel.state.value.selectedVideo?.id != returningParent.item.id
    ) {
      videoEntryCache[returningParent.entryId]?.let(::restoreEntry) ?: clearVisibleVideoData()
      dataCommitAllowedId = returningParent.item.id
      showEmbeddedCover = true
      mainViewModel.openVideo(returningParent.item)
      ensureVideoPageData(returningParent.item)
      withFrameNanos {}
      withFrameNanos {}
    }
    if (session.kind == TransitionKind.ENTER_PROFILE && returningParent == null) {
      mainViewModel.returnToFeed()
      dataCommitAllowedId = null
      playerActivationId = null
    }
    val duration =
      ((if (settings.reduceMotion) 140 else 360) * session.progress.value)
        .toInt()
        .coerceAtLeast(1)
    kotlinx.coroutines.coroutineScope {
      launch {
        session.progress.animateTo(0f, tween(duration, easing = FastOutSlowInEasing))
      }
      launch {
        session.coverAlpha.animateTo(
          1f,
          tween(if (settings.reduceMotion) 50 else 100, easing = FastOutSlowInEasing),
        )
      }
      launch {
        session.panelAlpha.animateTo(
          1f,
          tween(if (settings.reduceMotion) 50 else 100, easing = FastOutSlowInEasing),
        )
      }
      launch {
        session.backgroundAlpha.animateTo(
          0f,
          tween(if (settings.reduceMotion) 50 else 100, easing = FastOutSlowInEasing),
        )
      }
      launch {
        session.bridgeAlpha.animateTo(
          0f,
          tween(if (settings.reduceMotion) 50 else 100, easing = FastOutSlowInEasing),
        )
      }
      launch {
        session.themeScrimAlpha.animateTo(
          0f,
          tween(if (settings.reduceMotion) 80 else 180, easing = FastOutSlowInEasing),
        )
      }
    }
    withFrameNanos {}
    if (session.reusePlayerSurface && activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome) {
      rootPlayerOwnership = RootPlayerOwnership(RootPlayerSurfaceRole.IDLE)
    } else if (!session.reusePlayerSurface) {
      rootPlayerOwnership =
        RootPlayerOwnership(RootPlayerSurfaceRole.EXIT_COVERED, session.item.id)
    }
    withFrameNanos {}
    when (session.kind) {
      TransitionKind.ENTER_ROOT -> {
        mainViewModel.returnToFeed()
        videoStack = emptyList()
        if (
          activeBangumiPage?.sourceOrigin in
            setOf(
              PageOrigin.My,
              PageOrigin.Search,
              PageOrigin.BangumiIndex,
              PageOrigin.BangumiHome,
            )
        ) {
          activeBangumiPage = null
          deferSearchBangumiPageComposition = false
          deferBangumiIndexPageComposition = false
          deferBangumiHomePageComposition = false
          bangumiPosterBounds = Rect.Zero
        }
        transitionPhase = TransitionPhase.Feed
        hiddenFeedCoverItemId = null
        hiddenPopularCoverItemId = null
        hiddenHomeDynamicCoverItemId = null
        hiddenMyCoverItemId = null
        hiddenSearchCoverItemId = null
        hiddenBangumiIndexItemId = null
        hiddenBangumiRecommendationItemId = null
        hiddenArticleVideoCoverItemId = null
        hiddenProfileCoverItemId = null
        profileVideoTransitionActive = false
      }
      TransitionKind.ENTER_RECOMMENDATION -> {
        val parentFrame = returningParent
        if (parentFrame != null && appState.selectedVideo?.id != parentFrame.item.id) {
          videoEntryCache[parentFrame.entryId]?.let(::restoreEntry)
          dataCommitAllowedId = parentFrame.item.id
          showEmbeddedCover = true
          mainViewModel.openVideo(parentFrame.item)
        }
        videoStack = videoStack.dropLast(1)
        transitionPhase =
          parentFrame?.let { TransitionPhase.Video(it.item, it.sourceCardBounds) }
            ?: TransitionPhase.Feed
        hiddenRecommendationCoverItemId = null
        hiddenPlaybackEndRecommendationCoverItemId = null
        if (parentFrame != null) {
          dataCommitAllowedId = parentFrame.item.id
          playerActivationId = parentFrame.item.id
          showEmbeddedCover = true
          val parentAlreadyRendered =
            renderedVideoId == parentFrame.item.id &&
              playerState is dev.openbili.webdemo.PlayerState.Ready
          rootPlayerOwnership =
            RootPlayerOwnership(
              if (parentAlreadyRendered) RootPlayerSurfaceRole.DETAIL
              else RootPlayerSurfaceRole.DETAIL_PENDING,
              parentFrame.item.id,
            )
          if (parentAlreadyRendered) playerViewModel.exoPlayer?.play()
          if (retainedRecommendationParent?.entryId != parentFrame.entryId) {
            val restore = videoEntryCache[parentFrame.entryId]
            playerViewModel.loadVideo(
              parentFrame.item,
              startPositionMs = restore?.savedPositionMs ?: 0L,
              preferredStreamIndex = restore?.qualityIndex,
              preferredResolutionMode = currentPreferredResolutionMode(),
              page = retainedPlaybackPage(parentFrame.entryId),
            )
          }
        }
      }
      TransitionKind.ENTER_PROFILE -> {
        videoStack = videoStack.dropLast(1)
        activeBangumiPage = null
        bangumiPosterBounds = Rect.Zero
        transitionPhase =
          returningParent?.let { TransitionPhase.Video(it.item, it.sourceCardBounds) }
            ?: TransitionPhase.Feed
        hiddenProfileCoverItemId = null
        withFrameNanos {}
        profileVideoTransitionActive = false
        if (returningParent != null) {
          dataCommitAllowedId = returningParent.item.id
          playerActivationId = null
        }
      }
      else -> Unit
    }
    transitionSession = null
  }
}

fun cancelPreparingRootEnter() {
  if (transitionSession != null || transitionPhase !is TransitionPhase.ToVideo) return
  activeTransitionJob?.cancel()
  activeTransitionJob = null
  playerViewModel.cancelPendingLoad()
  mainViewModel.returnToFeed()
  videoStack = emptyList()
  if (
    activeBangumiPage?.sourceOrigin in
      setOf(
        PageOrigin.My,
        PageOrigin.Search,
        PageOrigin.BangumiIndex,
        PageOrigin.BangumiHome,
      )
  ) {
    activeBangumiPage = null
    deferSearchBangumiPageComposition = false
    deferBangumiIndexPageComposition = false
    deferBangumiHomePageComposition = false
    bangumiPosterBounds = Rect.Zero
  }
  transitionPhase = TransitionPhase.Feed
  hiddenFeedCoverItemId = null
  hiddenPopularCoverItemId = null
  hiddenHomeDynamicCoverItemId = null
  hiddenMyCoverItemId = null
  hiddenSearchCoverItemId = null
  hiddenBangumiIndexItemId = null
  hiddenBangumiRecommendationItemId = null
  hiddenArticleVideoCoverItemId = null
  hiddenProfileCoverItemId = null
  profileVideoTransitionActive = false
}

fun startRecommendedVideo(
  current: FeedItem,
  recommendation: FeedItem,
  bounds: Rect,
  returnBounds: Rect?,
  fromPlaybackEnd: Boolean,
) {
  if (
    transitionSession != null || transitionPhase !is TransitionPhase.Video || bounds == Rect.Zero
  )
    return
  val restoreRecommendationProgress = videoEntryCache[recommendation.id]?.playbackEnded != true
  // 卡片飞行由 Compose 渲染，而弹幕拥有独立顶层 Surface：在点击路径上清除该
  // 表面，让首个飞行帧就在它之上。
  playerViewHolder[0]?.view?.hideDanmakuForTransition()
  keyboardController?.hide()
  mainViewModel.onFullscreenChanged(false)
  cacheEntry(snapshotEntry(current))
  commitPlaybackProgress()
  val frame =
    StackFrame(
      entryId = recommendation.id,
      item = recommendation,
      parentPage = PageOrigin.Video(current.id),
      sourceCardBounds = returnBounds ?: bounds,
      sourceWasPlaybackEndRecommendation = fromPlaybackEnd,
    )
  val expandedStack = videoStack + frame
  videoStack =
    if (expandedStack.size <= MAX_VIDEO_STACK_DEPTH) expandedStack
    else {
      val inheritedRootFrame = expandedStack.first()
      expandedStack.drop(1).mapIndexed { index, retained ->
        if (index == 0)
          retained.copy(
            parentPage = inheritedRootFrame.parentPage,
            sourceCardBounds = null,
            sourceProfile = inheritedRootFrame.sourceProfile,
          )
        else retained
      }
    }
  dataCommitAllowedId = null
  showEmbeddedCover = false
  val session =
    CardTransitionSession(
        token = ++transitionToken,
        kind = TransitionKind.ENTER_RECOMMENDATION,
        item = recommendation,
        startBounds = bounds,
        endBounds = bounds,
        initialProgress = 0f,
        initialPanelAlpha = 1f,
        requiredSignals = playerTransitionRequiredSignals,
      )
      .also {
        it.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
        transitionSession = it
      }
  transitionPhase = TransitionPhase.ToVideo(recommendation, bounds, fromVideo = true)

  launchTransition {
    withFrameNanos {}
    val target = prepareCardTransition(session)
    if (!target.hasUsableSize()) {
      session.phase = SessionPhase.CANCELLED
      session.preparation.cancel()
      if (transitionSession === session) transitionSession = null
      hiddenRecommendationCoverItemId = null
      hiddenPlaybackEndRecommendationCoverItemId = null
      videoEntryCache[recommendation.id]?.let(::restoreEntryForFreshPlayback)
        ?: clearVisibleVideoData()
      dataCommitAllowedId = recommendation.id
      mainViewModel.openVideo(recommendation)
      transitionPhase = TransitionPhase.Video(recommendation, bounds)
      playerActivationId = recommendation.id
      showEmbeddedCover = true
      playerViewModel.loadVideo(
        recommendation,
        preferredResolutionMode = currentPreferredResolutionMode(),
        page = retainedPlaybackPage(recommendation.id),
        restoreSavedProgress = restoreRecommendationProgress,
      )
      return@launchTransition
    }
    session.endBounds = target
    hiddenRecommendationCoverItemId = recommendation.id.takeUnless { fromPlaybackEnd }
    hiddenPlaybackEndRecommendationCoverItemId = recommendation.id.takeIf { fromPlaybackEnd }
    session.phase = SessionPhase.FLYING
    kotlinx.coroutines.coroutineScope {
      launch {
        session.progress.animateTo(
          1f,
          tween(if (settings.reduceMotion) 140 else 400, easing = FastOutSlowInEasing),
        )
      }
      launch {
        session.panelAlpha.animateTo(
          0f,
          tween(if (settings.reduceMotion) 70 else 150, easing = FastOutSlowInEasing),
        )
      }
    }
    if (session.reverseRequested) return@launchTransition
    // 旧面板在这里已完全透明：在选择子页前先交换其共享数据，让子页只能揭示
    // 自己的保留缓存或加载骨架，绝不显示父页数据。
    videoEntryCache[recommendation.id]?.let(::restoreEntryForFreshPlayback)
      ?: clearVisibleVideoData()
    dataCommitAllowedId = recommendation.id
    mainViewModel.openVideo(recommendation)
    transitionPhase = TransitionPhase.Video(recommendation, bounds)
    withFrameNanos {}
    session.panelAlpha.animateTo(
      1f,
      tween(if (settings.reduceMotion) 90 else 220, easing = FastOutSlowInEasing),
    )
    if (session.reverseRequested) return@launchTransition
    session.phase = SessionPhase.WAITING_FIRST_FRAME
    playerActivationId = recommendation.id
    playerViewModel.loadVideo(
      recommendation,
      preferredResolutionMode = currentPreferredResolutionMode(),
      page = retainedPlaybackPage(recommendation.id),
      restoreSavedProgress = restoreRecommendationProgress,
    )
  }
}

}

package dev.openbili.webdemo.ui

/**
 * 播放基础设施上下文：PlayerView 宿主管理、页面切换播放闸门、共享边界转场与番剧
 * 播放进度提交，是 AppRoot 拆分出的播放逻辑归宿。
 */

import android.content.Context
import android.view.ViewGroup
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.media3.ui.PlayerView
import dev.openbili.webdemo.BangumiPlaybackStore
import dev.openbili.webdemo.AppUiState
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.BangumiSection
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import dev.openbili.webdemo.api.BiliBangumiApi
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.BangumiLocalHistoryStore
import dev.openbili.webdemo.api.BiliReportApi
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.PlaybackProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.openbili.webdemo.feed.CoverImageRequestFactory
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

internal class AppRootPlaybackContext(
  val context: Context,
  val scope: CoroutineScope,
  val playerViewHolder: Array<HeldPlayerView?>,
  val rootPagerState: PagerState,
  val playerViewModel: PlayerViewModel,
  val videoState: AppRootVideoState,
  val playerSession: AppRootPlayerSessionState,
  val settingsState: State<AppSettings>,
  val authUserInfoState: State<dev.openbili.webdemo.api.UserInfo>,
  val appStateState: State<AppUiState>,
  val transitionPhaseState: MutableState<TransitionPhase>,
  val transitionSessionState: MutableState<CardTransitionSession?>,
  val showEmbeddedCoverState: MutableState<Boolean>,
  val playerBoundsState: MutableState<Rect>,
  val activeBangumiPageState: MutableState<ActiveBangumiPage?>,
  val profileVideoTransitionActiveState: MutableState<Boolean>,
  val hiddenProfileCoverItemIdState: MutableState<String?>,
  val hiddenSearchCoverItemIdState: MutableState<String?>,
  val hiddenArticleVideoCoverItemIdState: MutableState<String?>,
  val hiddenMyCoverItemIdState: MutableState<String?>,
  val commentImagePreviewActiveState: MutableState<Boolean>,
  val rootTabState: MutableState<RootTab>,
  val rootPageSwitchRequestTokenState: MutableState<Long>,
  val rootPageSwitchRequestedState: MutableState<Boolean>,
  val homeRecommendationModeState: MutableState<HomeRecommendationMode>,
  val hiddenRecommendationCoverItemIdState: MutableState<String?>,
  val hiddenHomeDynamicCoverItemIdState: MutableState<String?>,
  val hiddenBangumiIndexItemIdState: MutableState<String?>,
  val hiddenBangumiRecommendationItemIdState: MutableState<String?>,
  val hiddenFeedCoverItemIdState: MutableState<String?>,
  val hiddenPopularCoverItemIdState: MutableState<String?>,
  val hiddenPlaybackEndRecommendationCoverItemIdState: MutableState<String?>,
  val activeTransitionJobState: MutableState<Job?>,
  val activeRevealJobState: MutableState<Job?>,
) {
  val settings by settingsState
  val authUserInfo by authUserInfoState
  val appState by appStateState
  var transitionPhase by transitionPhaseState
  var transitionSession by transitionSessionState
  var showEmbeddedCover by showEmbeddedCoverState
  var playerBounds by playerBoundsState
  var activeBangumiPage by activeBangumiPageState
  var profileVideoTransitionActive by profileVideoTransitionActiveState
  var hiddenProfileCoverItemId by hiddenProfileCoverItemIdState
  var hiddenSearchCoverItemId by hiddenSearchCoverItemIdState
  var hiddenArticleVideoCoverItemId by hiddenArticleVideoCoverItemIdState
  var hiddenMyCoverItemId by hiddenMyCoverItemIdState
  var commentImagePreviewActive by commentImagePreviewActiveState
  var rootTab by rootTabState
  var rootPageSwitchRequestToken by rootPageSwitchRequestTokenState
  var rootPageSwitchRequested by rootPageSwitchRequestedState
  var homeRecommendationMode by homeRecommendationModeState
  var hiddenRecommendationCoverItemId by hiddenRecommendationCoverItemIdState
  var hiddenHomeDynamicCoverItemId by hiddenHomeDynamicCoverItemIdState
  var hiddenBangumiIndexItemId by hiddenBangumiIndexItemIdState
  var hiddenBangumiRecommendationItemId by hiddenBangumiRecommendationItemIdState
  var hiddenFeedCoverItemId by hiddenFeedCoverItemIdState
  var hiddenPopularCoverItemId by hiddenPopularCoverItemIdState
  var hiddenPlaybackEndRecommendationCoverItemId by hiddenPlaybackEndRecommendationCoverItemIdState
  var activeTransitionJob by activeTransitionJobState
  var activeRevealJob by activeRevealJobState
  var historyAid by videoState::historyAid
  var historyCid by videoState::historyCid
  var historyDuration by videoState::historyDuration
  var historyStartTimestamp by videoState::historyStartTimestamp
  var currentPositionMs by playerSession::currentPositionMs

fun launchTransition(block: suspend CoroutineScope.() -> Unit) {
  val previous = activeTransitionJob
  activeTransitionJob = scope.launch {
    previous?.cancelAndJoin()
    block()
  }
}
fun animateToRootTab(tab: RootTab) {
  if (tab != RootTab.HOME) homeRecommendationMode = HomeRecommendationMode.NORMAL
  val requestToken = rootPageSwitchRequestToken + 1L
  rootPageSwitchRequestToken = requestToken
  rootPageSwitchRequested = true
  scope.launch {
    try {
      rootPagerState.animateScrollToPage(
        page = tab.ordinal,
        animationSpec =
          tween(if (settings.reduceMotion) 140 else 360, easing = FastOutSlowInEasing),
      )
    } finally {
      // animateScrollToPage 在停稳位置返回：把播放闸门再保持一个显示提交，
      // 让进入页与离开页在飞行期间都不能播放。
      withFrameNanos {}
      if (rootPageSwitchRequestToken == requestToken) rootPageSwitchRequested = false
    }
  }
}

fun restoredBangumiCard(sourceCard: SpaceContentCard): SpaceContentCard {
  val selection =
    BangumiPlaybackStore.read(context.applicationContext, sourceCard) ?: return sourceCard
  val restoredSeasonId = selection.seasonId.takeIf { it > 0L } ?: sourceCard.seasonId
  return sourceCard.copy(
    aid = 0L,
    bvid = selection.bvid,
    videoUrl = "https://www.bilibili.com/bangumi/play/ep${selection.episodeId}",
    seasonId = restoredSeasonId,
    episodeId = selection.episodeId,
  )
}

fun commitPlaybackProgress() {
  val bangumiPage = activeBangumiPage
  val bangumiEpisode =
    bangumiPage
      ?.season
      ?.let { it.episodes + it.sections.flatMap { section -> section.episodes } }
      ?.firstOrNull { it.id == bangumiPage.currentEpisodeId }
  val player = playerViewModel.exoPlayer
  val expectedVideoId = appState.selectedVideo?.id
  val validPlayerSnapshot =
    player != null &&
      playerViewModel.isPlaybackIdentityActive(expectedVideoId, historyAid, historyCid) &&
      dev.openbili.webdemo.isPlaybackSnapshotValid(
        expectedMediaId = expectedVideoId,
        actualMediaId = player.currentMediaItem?.mediaId,
        playbackState = player.playbackState,
        requireReady = false,
      )
  val positionMs = player?.takeIf { validPlayerSnapshot }?.currentPosition ?: return
  bangumiPage?.let { page ->
    val season = page.season
    if (season != null && bangumiEpisode != null) {
      BangumiPlaybackStore.save(
        context.applicationContext,
        page.sourceCard,
        season.seasonId,
        bangumiEpisode,
      )
      BangumiLocalHistoryStore.record(
        context.applicationContext,
        page.sourceCard,
        season.seasonId,
        bangumiEpisode,
        positionMs,
        bangumiEpisode.durationSeconds * 1_000L,
      )
    }
  }
  val aid = historyAid
  val cid = historyCid
  if (aid <= 0L || cid <= 0L) return
  val durationSeconds = historyDuration
  PlaybackProgressStore.save(
    context.applicationContext,
    aid,
    cid,
    positionMs,
    durationSeconds * 1000L,
  )
  if (authUserInfo.isLogin) {
    // 在发起 IO 前快照 PGC 身份：本函数返回后剧集切换会立即更新 activeBangumiPage。
    val bangumiSubType =
      bangumiPage?.let { page -> pgcPlaybackSubType(page.sourceCard.seasonType) } ?: 0
    val bangumiEpisodeId = bangumiEpisode?.takeIf { it.aid == aid && it.cid == cid }?.id ?: 0L
    val bangumiSeasonId = if (bangumiEpisodeId > 0L) bangumiPage?.season?.seasonId ?: 0L else 0L
    scope.launch(Dispatchers.IO) {
      runCatching {
        if (bangumiSubType > 0 && bangumiEpisodeId > 0L && bangumiSeasonId > 0L) {
          BiliReportApi.reportBangumiPlayback(
            aid = aid,
            cid = cid,
            episodeId = bangumiEpisodeId,
            seasonId = bangumiSeasonId,
            playedSeconds = positionMs / 1000L,
            durationSeconds = durationSeconds,
            startTimestamp = historyStartTimestamp,
            subType = bangumiSubType,
            playType = 2,
          )
        } else {
          BiliReportApi.reportPlayback(aid, cid, positionMs / 1000L)
        }
      }
    }
  }
}

fun obtainPlayerView(
  ctx: android.content.Context,
  role: SharedPlayerViewRole,
): PlayerView {
  playerViewHolder[0]
    ?.takeIf { it.role == role }
    ?.let {
      return it.view
    }
  playerViewHolder[0]?.view?.let { previous ->
    previous.animate().cancel()
    previous.player = null
    (previous.parent as? ViewGroup)?.removeView(previous)
  }
  return createPlayerView(ctx).also { view ->
    // 保持 Android 视图层级挂载，直接控制独立的 SurfaceControl 图层。三星
    // SurfaceView 实现上父级 alpha 不够用，可能在新 PV 准备期间暴露旧解码器缓冲。
    view.alpha = 1f
    view.updateVideoSurfaceAlpha(if (role == SharedPlayerViewRole.PREVIEW) 0f else 1f)
    playerViewHolder[0] = HeldPlayerView(role, view)
  }
}

fun obtainPlayerViewForHost(
  ctx: android.content.Context,
  role: SharedPlayerViewRole,
): PlayerView =
  obtainPlayerView(ctx, role).also { view ->
    // Compose 可能在销毁源持有者之前先插入目标 AndroidViewHolder：同角色宿主可能
    // 被重建，重新挂载前要先同步分离它。
    (view.parent as? ViewGroup)?.removeView(view)
  }

fun unbindPlayerView() {
  playerViewHolder[0]?.view?.player = null
}

fun prewarmPlayerInfrastructure() {
  val player = playerViewModel.preparePlayer(publishSystemControls = false)
  obtainPlayerView(context, SharedPlayerViewRole.PREVIEW).player = player
}

fun cachedCardTransitionBitmap(session: CardTransitionSession) =
  LoadedFeedImageRegistry.bitmap(
    session.item.coverUrl,
    requireUncropped = session.fitCover,
  )
    ?: activeBangumiPage
      ?.takeIf { it.sourceIsBangumiExplorePoster && session.fitCover }
      // 探索海报已由专用 3:4 PGC 衍生图渲染：拟合飞行直接复用这张卡片位图，
      // 而不是点击后再解码一次原图。
      ?.let { LoadedFeedImageRegistry.bitmap(session.item.coverUrl) }
    ?: activeBangumiPage
      ?.takeIf { it.sourceOrigin == PageOrigin.BangumiHome && !session.fitCover }
      ?.let {
        LoadedFeedImageRegistry.bitmap(bangumiPreviewCoverCacheKey(session.item.coverUrl))
      }

private suspend fun awaitBangumiSourcePosterBitmap(
  session: CardTransitionSession
): android.graphics.Bitmap? {
  if (!session.fitCover) return null
  val keys =
    listOfNotNull(session.item.coverUrl, activeBangumiPage?.sourceCard?.coverUrl).distinct()
  for (key in keys) {
    LoadedFeedImageRegistry.awaitBitmap(
        key,
        timeoutMs = 180L,
        requireUncropped = true,
      )
      ?.let { return it }
  }
  return null
}

suspend fun prepareCardTransition(
  session: CardTransitionSession,
  targetBounds: () -> Rect = { playerBounds },
): Rect = coroutineScope {
  val boundsTracker = StableBoundsTracker()
  val coverJob = launch {
    if (session.reusePlayerSurface) {
      session.transitionBitmap =
        LoadedFeedImageRegistry.bitmap(session.item.coverUrl)
          ?: LoadedFeedImageRegistry.bitmap(bangumiPreviewCoverCacheKey(session.item.coverUrl))
      session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
      return@launch
    }
    if (session.item.coverUrl.isBlank()) {
      session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
    } else if (
      (cachedCardTransitionBitmap(session) ?: awaitBangumiSourcePosterBitmap(session))?.also {
        session.transitionBitmap = it
        LoadedFeedImageRegistry.markLoaded(
          session.item.coverUrl,
          it,
          cropped = !session.fitCover,
        )
      } != null
    ) {
      session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
    } else if (activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome) {
      // 番剧动画只走缓存：未命中由它的黑色桥接表示；用户已在观看转场时绝不启动
      // Coil/网络/解码工作。
      session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
    } else {
      runCatching {
        val request =
          CoverImageRequestFactory.request(
            session.item.coverUrl,
            ImageRequest.Builder(context.applicationContext),
            width =
              if (session.fitCover) TRANSITION_POSTER_REQUEST_WIDTH
              else TRANSITION_LANDSCAPE_REQUEST_WIDTH,
            height =
              if (session.fitCover) TRANSITION_POSTER_REQUEST_HEIGHT
              else TRANSITION_LANDSCAPE_REQUEST_HEIGHT,
            crop = !session.fitCover,
          )
        context.applicationContext.imageLoader.execute(request).image
      }
        .getOrNull()
        ?.let { image ->
          val bitmap = (image as? BitmapImage)?.bitmap
          session.transitionBitmap = bitmap
          LoadedFeedImageRegistry.markLoaded(
            session.item.coverUrl,
            bitmap,
            cropped = !session.fitCover,
          )
          session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
        }
    }
  }
  val readinessJob = launch {
    while (isActive && !session.preparation.isReady()) {
      withFrameNanos {}
      val bounds = targetBounds()
      if (bounds.hasUsableSize()) {
        session.preparation.markReady(TransitionReadySignal.TARGET_MOUNTED)
        if (boundsTracker.observe(bounds)) {
          session.endBounds = bounds
          session.preparation.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
        }
      }
    }
  }
  val result =
    session.preparation.await(
      timeoutMillis = if (session.fitCover) 1_600L else TRANSITION_PREPARE_TIMEOUT_MS
    )
  readinessJob.cancelAndJoin()
  coverJob.cancelAndJoin()
  if (result == TransitionPreparationResult.CANCELLED) return@coroutineScope Rect.Zero
  session.timedOut = result == TransitionPreparationResult.TIMED_OUT
  val resolvedBounds = targetBounds().takeIf { it.hasUsableSize() }
  resolvedBounds?.let { session.endBounds = it }
  session.phase = SessionPhase.READY
  withFrameNanos {}
  resolvedBounds ?: Rect.Zero
}


suspend fun prepareExitTransition(
  session: CardTransitionSession,
  targetBounds: () -> Rect?,
): Rect = coroutineScope {
  val boundsTracker = StableBoundsTracker()
  val coverJob = launch {
    if (session.reusePlayerSurface) {
      session.transitionBitmap =
        LoadedFeedImageRegistry.bitmap(session.item.coverUrl)
          ?: LoadedFeedImageRegistry.bitmap(bangumiPreviewCoverCacheKey(session.item.coverUrl))
      session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
      return@launch
    }
    if (session.item.coverUrl.isBlank()) {
      session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
    } else if (
      (cachedCardTransitionBitmap(session) ?: awaitBangumiSourcePosterBitmap(session))?.also {
        session.transitionBitmap = it
        LoadedFeedImageRegistry.markLoaded(
          session.item.coverUrl,
          it,
          cropped = !session.fitCover,
        )
      } != null
    ) {
      session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
    } else if (activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome) {
      session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
    } else {
      runCatching {
        val request =
          CoverImageRequestFactory.request(
            session.item.coverUrl,
            ImageRequest.Builder(context.applicationContext),
            width =
              if (session.fitCover) TRANSITION_POSTER_REQUEST_WIDTH
              else TRANSITION_LANDSCAPE_REQUEST_WIDTH,
            height =
              if (session.fitCover) TRANSITION_POSTER_REQUEST_HEIGHT
              else TRANSITION_LANDSCAPE_REQUEST_HEIGHT,
            crop = !session.fitCover,
          )
        context.applicationContext.imageLoader.execute(request).image
      }
        .getOrNull()
        ?.let { image ->
          val bitmap = (image as? BitmapImage)?.bitmap
          session.transitionBitmap = bitmap
          LoadedFeedImageRegistry.markLoaded(
            session.item.coverUrl,
            bitmap,
            cropped = !session.fitCover,
          )
          session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
        }
    }
  }
  val readinessJob = launch {
    while (isActive && !session.preparation.isReady()) {
      withFrameNanos {}
      val bounds = targetBounds()
      if (bounds != null && bounds.hasUsableSize()) {
        session.preparation.markReady(TransitionReadySignal.TARGET_MOUNTED)
        if (boundsTracker.observe(bounds)) {
          session.startBounds = bounds
          session.preparation.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
        }
      }
    }
  }
  val result =
    session.preparation.await(
      timeoutMillis = if (session.fitCover) 1_600L else TRANSITION_PREPARE_TIMEOUT_MS
    )
  readinessJob.cancelAndJoin()
  coverJob.cancelAndJoin()
  if (result == TransitionPreparationResult.CANCELLED) return@coroutineScope Rect.Zero
  session.timedOut = result == TransitionPreparationResult.TIMED_OUT
  val resolvedBounds = targetBounds()?.takeIf { it.hasUsableSize() }
  resolvedBounds?.let { session.startBounds = it }
  session.phase = SessionPhase.READY
  withFrameNanos {}
  resolvedBounds ?: Rect.Zero
}

fun previewSeek(targetMs: Long) = playerSession.previewSeek(playerViewModel, targetMs, scope)

fun setTemporarySpeedBoost(active: Boolean) =
  playerSession.setTemporarySpeedBoost(playerViewModel, active)

fun setPlaybackSpeed(speed: Float) = playerSession.setPlaybackSpeed(playerViewModel, speed)

fun cancelSeekPreview() = playerSession.cancelSeekPreview(playerViewModel)

fun commitSeek(targetMs: Long) =
  playerSession.commitSeek(playerViewModel, targetMs, historyDuration, scope)

fun revealTransitionSession(session: CardTransitionSession, timedOut: Boolean = false) {
  if (
    transitionSession?.token != session.token ||
      session.reverseRequested ||
      session.phase != SessionPhase.WAITING_FIRST_FRAME
  )
    return
  if (timedOut) {
    session.timedOut = true
    showEmbeddedCover = !session.reusePlayerSurface
  }
  val animateSharedCover =
    !session.reusePlayerSurface || activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome
  activeRevealJob?.cancel()
  activeRevealJob = scope.launch {
    if (timedOut) withFrameNanos {}
    session.phase = SessionPhase.REVEALING
    kotlinx.coroutines.coroutineScope {
      if (animateSharedCover) {
        launch {
          session.coverAlpha.animateTo(
            0f,
            tween(if (settings.reduceMotion) 90 else 170, easing = FastOutSlowInEasing),
          )
        }
      }
      launch {
        delay(if (settings.reduceMotion) 20 else 70)
        session.panelAlpha.animateTo(
          1f,
          tween(if (settings.reduceMotion) 90 else 210, easing = FastOutSlowInEasing),
        )
      }
    }
    session.phase = SessionPhase.COMPLETED
    withFrameNanos {}
    if (transitionSession?.token == session.token && !session.reverseRequested) {
      transitionPhase = TransitionPhase.Video(session.item, null)
      transitionSession = null
      hiddenFeedCoverItemId = null
      hiddenPopularCoverItemId = null
      hiddenHomeDynamicCoverItemId = null
      hiddenMyCoverItemId = null
      hiddenSearchCoverItemId = null
      hiddenBangumiIndexItemId = null
      hiddenBangumiRecommendationItemId = null
      hiddenArticleVideoCoverItemId = null
      hiddenRecommendationCoverItemId = null
      hiddenPlaybackEndRecommendationCoverItemId = null
      hiddenProfileCoverItemId = null
      profileVideoTransitionActive = false
    }
    activeRevealJob = null
  }
}

}

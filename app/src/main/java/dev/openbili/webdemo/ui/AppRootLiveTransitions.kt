package dev.openbili.webdemo.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.ArticleDetail
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.BangumiExploreSectionKind
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.BangumiSection
import dev.openbili.webdemo.api.BiliArticleApi
import dev.openbili.webdemo.api.BiliBangumiApi
import dev.openbili.webdemo.api.BiliCommentApi
import dev.openbili.webdemo.api.BiliDanmakuApi
import dev.openbili.webdemo.api.BiliFollowApi
import dev.openbili.webdemo.api.BiliReportApi
import dev.openbili.webdemo.api.BiliVideoApi
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.CommentNavigationTarget
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.DanmakuMaskParser
import dev.openbili.webdemo.api.MessageTargetKind
import dev.openbili.webdemo.api.RiskControlManager
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.VideoEngagement
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.article.ArticleOrigin
import dev.openbili.webdemo.article.ArticleScreen
import dev.openbili.webdemo.article.ArticleStackFrame
import dev.openbili.webdemo.article.ArticleTransitionOverlay
import dev.openbili.webdemo.article.ArticleTransitionSession
import dev.openbili.webdemo.AuthViewModel
import dev.openbili.webdemo.bangumi.BangumiExploreViewModel
import dev.openbili.webdemo.bangumi.BangumiIndexViewModel
import dev.openbili.webdemo.bangumi.BangumiRecommendationViewModel
import dev.openbili.webdemo.BangumiLocalHistoryStore
import dev.openbili.webdemo.BangumiPlaybackStore
import dev.openbili.webdemo.feed.CoverImageRequestFactory
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedPerformanceConfig
import dev.openbili.webdemo.feed.FeedScrollAnchor
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import dev.openbili.webdemo.feed.LocalCoverImageLoadingEnabled
import dev.openbili.webdemo.live.currentDisplayCoverUrl
import dev.openbili.webdemo.live.LiveHomeSourceAnchor
import dev.openbili.webdemo.live.LiveHomeViewModel
import dev.openbili.webdemo.live.LiveRoomScreen
import dev.openbili.webdemo.live.LiveRoomViewModel
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.LoginState
import dev.openbili.webdemo.MainViewModel
import dev.openbili.webdemo.my.contains
import dev.openbili.webdemo.my.MyScreen
import dev.openbili.webdemo.my.MyViewModel
import dev.openbili.webdemo.my.ProfilePrivateConversationPane
import dev.openbili.webdemo.my.WatchLaterViewModel
import dev.openbili.webdemo.offline.OfflineMediaManager
import dev.openbili.webdemo.PlaybackProgressStore
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.resolvePlaybackPage
import dev.openbili.webdemo.search.SearchResultsScreen
import dev.openbili.webdemo.search.SearchScreen
import dev.openbili.webdemo.search.SearchViewModel
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.AppSettingsViewModel
import dev.openbili.webdemo.settings.preferredResolutionModeFor
import dev.openbili.webdemo.video.BangumiPageUi
import dev.openbili.webdemo.video.CommentProfileAnchor
import dev.openbili.webdemo.video.DanmakuOverlayView
import dev.openbili.webdemo.video.DanmakuWindowController
import dev.openbili.webdemo.video.PlaybackPageGlassBackdrop
import dev.openbili.webdemo.video.VideoInfoTile
import dev.openbili.webdemo.video.VideoScreen
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AppRootLiveTransitionContext(
  val scope: kotlinx.coroutines.CoroutineScope,
  val settings: AppSettings,
  val playerViewModel: dev.openbili.webdemo.PlayerViewModel,
  val myViewModel: dev.openbili.webdemo.my.MyViewModel,
  val activeLiveOrigin: androidx.compose.runtime.MutableState<PageOrigin>,
  val activeLiveSourceAnchor: androidx.compose.runtime.MutableState<LiveHomeSourceAnchor?>,
  val hiddenSearchCoverItemId: androidx.compose.runtime.MutableState<String?>,
  val hiddenMyCoverItemId: androidx.compose.runtime.MutableState<String?>,
  val hiddenHomeLiveCoverItemId: androidx.compose.runtime.MutableState<String?>,
  val myCardBounds: MutableMap<String, androidx.compose.ui.geometry.Rect>,
  val homeLiveCardBounds: MutableMap<String, androidx.compose.ui.geometry.Rect>,
  val searchCardBounds: MutableMap<String, androidx.compose.ui.geometry.Rect>,
  val activeLiveRoom: androidx.compose.runtime.MutableState<LiveSearchRoom?>,
  val liveExitPrelude: androidx.compose.runtime.MutableState<VideoExitPrelude?>,
  val liveVideoSurfaceVisible: androidx.compose.runtime.MutableState<Boolean>,
  val livePlayerBounds: androidx.compose.runtime.MutableState<androidx.compose.ui.geometry.Rect>,
  val liveRoomParentStack: androidx.compose.runtime.MutableState<List<LiveRoomParentFrame>>,
  val hiddenLiveRecommendationCoverItemId: androidx.compose.runtime.MutableState<String?>,
  val activeLiveEntryId: androidx.compose.runtime.MutableState<Long>,
  val nextLiveEntryId: androidx.compose.runtime.MutableState<Long>,
  val liveFirstFrameEntryId: androidx.compose.runtime.MutableState<Long>,
  val transitionToken: androidx.compose.runtime.MutableState<Long>,
  val liveTransitionSession: androidx.compose.runtime.MutableState<CardTransitionSession?>,
  val liveTransitionJob: androidx.compose.runtime.MutableState<kotlinx.coroutines.Job?>,
  val livePageAlpha: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
  val liveRecommendationCardBounds: MutableMap<String, androidx.compose.ui.geometry.Rect>,
  val rootPlayerOwnership: androidx.compose.runtime.MutableState<RootPlayerOwnership>,
  val transitionSession: androidx.compose.runtime.MutableState<CardTransitionSession?>,
  val articleTransitionSession: androidx.compose.runtime.MutableState<ArticleTransitionSession?>,
  val prepareCardTransition: suspend (CardTransitionSession, () -> androidx.compose.ui.geometry.Rect) -> androidx.compose.ui.geometry.Rect,
  val closeSearchResultsAnimated: () -> Unit,
  val animateToRootTab: (RootTab) -> Unit,
)
internal fun liveTransitionItem(room: LiveSearchRoom): FeedItem =
    FeedItem(
      id = room.stableId,
      title = room.title,
      videoUrl = "https://live.bilibili.com/${room.roomId}",
      coverUrl = room.currentDisplayCoverUrl(),
      uploader = room.uname,
      playCount = null,
      duration = null,
      uploaderFace = room.faceUrl,
      uploaderMid = room.uid,
      description = listOfNotNull(room.parentAreaName, room.areaName).joinToString(" · "),
    )

internal fun currentLiveTransitionBounds(session: CardTransitionSession): Rect {
    val progress = session.progress.value.coerceIn(0f, 1f)
    return Rect(
      left =
        session.startBounds.left + (session.endBounds.left - session.startBounds.left) * progress,
      top = session.startBounds.top + (session.endBounds.top - session.startBounds.top) * progress,
      right =
        session.startBounds.right +
          (session.endBounds.right - session.startBounds.right) * progress,
      bottom =
        session.startBounds.bottom +
          (session.endBounds.bottom - session.startBounds.bottom) * progress,
    )
  }

internal fun setLiveSourceCoverHidden(ctx: AppRootLiveTransitionContext, room: LiveSearchRoom, hidden: Boolean) {
    when (ctx.activeLiveOrigin.value) {
      PageOrigin.My -> ctx.hiddenMyCoverItemId.value = room.stableId.takeIf { hidden }
      PageOrigin.Home ->
        ctx.hiddenHomeLiveCoverItemId.value = ctx.activeLiveSourceAnchor.value?.stableId.takeIf { hidden }
      else -> ctx.hiddenSearchCoverItemId.value = room.stableId.takeIf { hidden }
    }
  }

internal fun liveSourceBounds(ctx: AppRootLiveTransitionContext, room: LiveSearchRoom): Rect? =
    when (ctx.activeLiveOrigin.value) {
      PageOrigin.My -> ctx.myCardBounds[room.stableId]
      PageOrigin.Home -> ctx.activeLiveSourceAnchor.value?.stableId?.let(ctx.homeLiveCardBounds::get)
      else -> ctx.searchCardBounds[room.stableId]
    }

internal fun startEnterLive(ctx: AppRootLiveTransitionContext,
    room: LiveSearchRoom,
    cardBounds: Rect?,
    origin: PageOrigin = PageOrigin.Search,
    sourceAnchor: LiveHomeSourceAnchor? = null,
  ) {
    if (ctx.activeLiveRoom.value != null || ctx.transitionSession.value != null || ctx.articleTransitionSession.value != null)
      return
    ctx.playerViewModel.cancelPendingLoad()
    ctx.playerViewModel.exoPlayer?.pause()
    ctx.liveExitPrelude.value = null
    ctx.liveVideoSurfaceVisible.value = false
    ctx.rootPlayerOwnership.value = RootPlayerOwnership(RootPlayerSurfaceRole.IDLE)
    ctx.livePlayerBounds.value = Rect.Zero
    ctx.hiddenSearchCoverItemId.value = null
    ctx.hiddenMyCoverItemId.value = null
    ctx.activeLiveOrigin.value = origin
    ctx.activeLiveSourceAnchor.value = sourceAnchor.takeIf { origin == PageOrigin.Home }
    ctx.liveRoomParentStack.value = emptyList()
    ctx.hiddenLiveRecommendationCoverItemId.value = null
    ctx.activeLiveEntryId.value = ++ctx.nextLiveEntryId.value
    val liveEntryId = ctx.activeLiveEntryId.value
    ctx.liveFirstFrameEntryId.value = 0L
    ctx.activeLiveRoom.value = room
    val source = cardBounds?.takeIf { it.hasUsableSize() }
    val item = liveTransitionItem(room)
    val session = source?.let {
      CardTransitionSession(
          token = ++ctx.transitionToken.value,
          kind = TransitionKind.ENTER_ROOT,
          item = item,
          startBounds = it,
          endBounds = it,
          initialProgress = 0f,
          requiredSignals = playerTransitionRequiredSignals,
        )
        .also { created ->
          created.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
          ctx.liveTransitionSession.value = created
        }
    }
    val previous = ctx.liveTransitionJob.value
    ctx.liveTransitionJob.value = ctx.scope.launch {
      previous?.cancelAndJoin()
      ctx.livePageAlpha.snapTo(0f)
      if (session == null) {
        ctx.livePageAlpha.animateTo(
          1f,
          tween(if (ctx.settings.reduceMotion) 100 else 220, easing = FastOutSlowInEasing),
        )
        ctx.liveVideoSurfaceVisible.value = true
        ctx.liveTransitionJob.value = null
        return@launch
      }
      withFrameNanos {}
      val target = ctx.prepareCardTransition(session) { ctx.livePlayerBounds.value }
      if (!target.hasUsableSize()) {
        session.phase = SessionPhase.CANCELLED
        if (ctx.liveTransitionSession.value === session) ctx.liveTransitionSession.value = null
        ctx.livePageAlpha.animateTo(
          1f,
          tween(if (ctx.settings.reduceMotion) 100 else 220, easing = FastOutSlowInEasing),
        )
        ctx.liveVideoSurfaceVisible.value = true
        ctx.liveTransitionJob.value = null
        return@launch
      }
      session.endBounds = target
      setLiveSourceCoverHidden(ctx, room, true)
      session.phase = SessionPhase.FLYING
      withFrameNanos {}
      kotlinx.coroutines.coroutineScope {
        launch {
          session.progress.animateTo(
            1f,
            tween(if (ctx.settings.reduceMotion) 140 else 400, easing = FastOutSlowInEasing),
          )
        }
        launch {
          delay(if (ctx.settings.reduceMotion) 10 else 45)
          ctx.livePageAlpha.animateTo(
            1f,
            tween(if (ctx.settings.reduceMotion) 100 else 300, easing = FastOutSlowInEasing),
          )
        }
      }
      session.phase = SessionPhase.REVEALING
      ctx.liveVideoSurfaceVisible.value = true
      snapshotFlow { ctx.liveFirstFrameEntryId.value }.first { it == liveEntryId }
      session.coverAlpha.animateTo(
        0f,
        tween(if (ctx.settings.reduceMotion) 90 else 180, easing = FastOutSlowInEasing),
      )
      session.phase = SessionPhase.COMPLETED
      setLiveSourceCoverHidden(ctx, room, false)
      if (ctx.liveTransitionSession.value === session) ctx.liveTransitionSession.value = null
      ctx.liveTransitionJob.value = null
    }
  }

internal fun startEnterRecommendedLive(ctx: AppRootLiveTransitionContext, room: LiveSearchRoom, cardBounds: Rect) {
    val parentRoom = ctx.activeLiveRoom.value ?: return
    if (
      room.roomId == parentRoom.roomId ||
        ctx.liveRoomParentStack.value.any { it.room.roomId == room.roomId } ||
        ctx.liveTransitionJob.value != null ||
        ctx.liveTransitionSession.value != null ||
        cardBounds.width <= 0f ||
        cardBounds.height <= 0f
    ) {
      return
    }
    val parentEntryId = ctx.activeLiveEntryId.value
    val playerTarget = ctx.livePlayerBounds.value.takeIf { it.hasUsableSize() }
    val item = liveTransitionItem(room)
    val session = playerTarget?.let { target ->
      CardTransitionSession(
          token = ++ctx.transitionToken.value,
          kind = TransitionKind.ENTER_RECOMMENDATION,
          item = item,
          startBounds = cardBounds,
          endBounds = target,
          initialProgress = 0f,
        )
        .also {
          it.transitionBitmap = LoadedFeedImageRegistry.bitmap(item.coverUrl)
          it.phase = SessionPhase.FLYING
          ctx.liveTransitionSession.value = it
        }
    }
    ctx.liveRecommendationCardBounds[liveRecommendationBoundsKey(parentRoom.roomId, room.roomId)] =
      cardBounds
    ctx.liveRoomParentStack.value =
      (ctx.liveRoomParentStack.value +
          LiveRoomParentFrame(
            entryId = parentEntryId,
            room = parentRoom,
            childRoomId = room.roomId,
            childCoverBounds = cardBounds,
          ))
        .takeLast(MAX_LIVE_ROOM_STACK_DEPTH)
    ctx.hiddenLiveRecommendationCoverItemId.value = room.stableId
    ctx.playerViewModel.exoPlayer?.pause()
    ctx.liveVideoSurfaceVisible.value = false
    val previous = ctx.liveTransitionJob.value
    ctx.liveTransitionJob.value = ctx.scope.launch {
      previous?.cancelAndJoin()
      ctx.livePageAlpha.animateTo(
        0f,
        tween(if (ctx.settings.reduceMotion) 70 else 150, easing = FastOutSlowInEasing),
      )
      ctx.activeLiveEntryId.value = ++ctx.nextLiveEntryId.value
      val liveEntryId = ctx.activeLiveEntryId.value
      ctx.liveFirstFrameEntryId.value = 0L
      ctx.activeLiveRoom.value = room
      ctx.livePlayerBounds.value = Rect.Zero
      withFrameNanos {}
      repeat(10) {
        if (ctx.livePlayerBounds.value.hasUsableSize()) return@repeat
        withFrameNanos {}
      }
      session?.endBounds = ctx.livePlayerBounds.value.takeIf { it.hasUsableSize() } ?: session.endBounds
      kotlinx.coroutines.coroutineScope {
        if (session != null) {
          launch {
            session.progress.animateTo(
              1f,
              tween(if (ctx.settings.reduceMotion) 140 else 380, easing = FastOutSlowInEasing),
            )
          }
        }
        launch {
          ctx.livePageAlpha.animateTo(
            1f,
            tween(if (ctx.settings.reduceMotion) 90 else 260, easing = FastOutSlowInEasing),
          )
        }
      }
      ctx.liveVideoSurfaceVisible.value = true
      session?.apply {
        phase = SessionPhase.REVEALING
        snapshotFlow { ctx.liveFirstFrameEntryId.value }.first { it == liveEntryId }
        coverAlpha.animateTo(
          0f,
          tween(if (ctx.settings.reduceMotion) 80 else 170, easing = FastOutSlowInEasing),
        )
        phase = SessionPhase.COMPLETED
      }
      if (ctx.liveTransitionSession.value === session) ctx.liveTransitionSession.value = null
      ctx.hiddenLiveRecommendationCoverItemId.value = null
      ctx.liveTransitionJob.value = null
    }
  }

internal fun startBackToPreviousLive(ctx: AppRootLiveTransitionContext) {
    val parentFrame = ctx.liveRoomParentStack.value.lastOrNull() ?: return
    val departing = ctx.activeLiveRoom.value ?: return
    if (ctx.liveTransitionJob.value != null || ctx.liveTransitionSession.value != null) return
    val savedPlayerBounds = ctx.livePlayerBounds.value.takeIf { it.hasUsableSize() }
    val item = liveTransitionItem(departing)
    ctx.playerViewModel.exoPlayer?.pause()
    val prelude = savedPlayerBounds?.let { bounds ->
      VideoExitPrelude(item = item, playerBounds = bounds).also {
        it.transitionBitmap = LoadedFeedImageRegistry.bitmap(item.coverUrl)
        ctx.liveExitPrelude.value = it
      }
    }
    ctx.liveVideoSurfaceVisible.value = false
    val key = liveRecommendationBoundsKey(parentFrame.room.roomId, parentFrame.childRoomId)
    ctx.liveRecommendationCardBounds.remove(key)
    ctx.hiddenLiveRecommendationCoverItemId.value = departing.stableId
    val previous = ctx.liveTransitionJob.value
    ctx.liveTransitionJob.value = ctx.scope.launch {
      previous?.cancelAndJoin()
      prelude
        ?.coverAlpha
        ?.animateTo(
          1f,
          tween(if (ctx.settings.reduceMotion) 70 else 140, easing = FastOutSlowInEasing),
        )
      ctx.livePageAlpha.animateTo(
        0f,
        tween(if (ctx.settings.reduceMotion) 90 else 190, easing = FastOutSlowInEasing),
      )
      ctx.activeLiveEntryId.value = parentFrame.entryId
      ctx.activeLiveRoom.value = parentFrame.room
      ctx.liveRoomParentStack.value = ctx.liveRoomParentStack.value.dropLast(1)
      ctx.livePlayerBounds.value = Rect.Zero
      withFrameNanos {}
      repeat(10) {
        if (ctx.liveRecommendationCardBounds[key]?.hasUsableSize() == true) return@repeat
        withFrameNanos {}
      }
      ctx.livePageAlpha.animateTo(
        1f,
        tween(if (ctx.settings.reduceMotion) 90 else 200, easing = FastOutSlowInEasing),
      )
      val destination =
        ctx.liveRecommendationCardBounds[key]?.takeIf { it.hasUsableSize() }
          ?: parentFrame.childCoverBounds.takeIf { it.hasUsableSize() }
      val session =
        if (savedPlayerBounds != null && destination != null) {
          CardTransitionSession(
              token = ++ctx.transitionToken.value,
              kind = TransitionKind.EXIT_RECOMMENDATION,
              item = item,
              startBounds = savedPlayerBounds,
              endBounds = destination,
              initialProgress = 0f,
            )
            .also {
              it.transitionBitmap = prelude?.transitionBitmap
              it.phase = SessionPhase.FLYING
              ctx.liveTransitionSession.value = it
            }
        } else null
      ctx.liveVideoSurfaceVisible.value = true
      ctx.liveExitPrelude.value = null
      session
        ?.progress
        ?.animateTo(
          1f,
          tween(if (ctx.settings.reduceMotion) 140 else 340, easing = FastOutSlowInEasing),
        )
      ctx.hiddenLiveRecommendationCoverItemId.value = null
      // 在保留卡片恢复自身封面的同时，把落地的共享封面再保留两个已提交帧。
      // 在同一快照里同时移除两者会让 TextureView 源闪一下空白。
      withFrameNanos {}
      withFrameNanos {}
      session?.phase = SessionPhase.COMPLETED
      if (ctx.liveTransitionSession.value === session) ctx.liveTransitionSession.value = null
      ctx.liveTransitionJob.value = null
    }
  }

internal fun startExitLive(ctx: AppRootLiveTransitionContext, closeSearchAfter: Boolean = false) {
    val room = ctx.activeLiveRoom.value ?: return
    // 与点播退出对齐：在封面替换播放器之前，先在点击路径上停掉直播音频，
    // 同时把媒体条目保留到共享返回完成。
    ctx.playerViewModel.exoPlayer?.pause()
    val item = liveTransitionItem(room)
    val activeFlight = ctx.liveTransitionSession.value
    val startBounds =
      activeFlight
        ?.takeIf { it.phase != SessionPhase.PREPARING }
        ?.let(::currentLiveTransitionBounds)
        ?.takeIf { it.hasUsableSize() } ?: ctx.livePlayerBounds.value.takeIf { it.hasUsableSize() }
    val destination =
      resolveExitTransitionTargetBounds(
        latest = liveSourceBounds(ctx, room),
        fallback = activeFlight?.startBounds,
        playerBounds = ctx.livePlayerBounds.value,
      )
    val previous = ctx.liveTransitionJob.value
    ctx.liveTransitionJob.value = ctx.scope.launch {
      previous?.cancelAndJoin()
      activeFlight?.preparation?.cancel()
      val prelude = startBounds?.let { bounds ->
        VideoExitPrelude(item = item, playerBounds = bounds).also {
          it.transitionBitmap =
            activeFlight?.transitionBitmap ?: LoadedFeedImageRegistry.bitmap(item.coverUrl)
          ctx.liveExitPrelude.value = it
        }
      }
      if (prelude != null) {
        prelude.coverAlpha.animateTo(
          1f,
          tween(if (ctx.settings.reduceMotion) 70 else 140, easing = FastOutSlowInEasing),
        )
        ctx.liveVideoSurfaceVisible.value = false
        withFrameNanos {}
        withFrameNanos {}
      }
      if (startBounds == null || destination == null) {
        ctx.livePageAlpha.animateTo(
          0f,
          tween(if (ctx.settings.reduceMotion) 90 else 180, easing = FastOutSlowInEasing),
        )
        ctx.activeLiveRoom.value = null
        ctx.activeLiveEntryId.value = 0L
        ctx.liveRoomParentStack.value = emptyList()
        ctx.liveTransitionSession.value = null
        ctx.liveExitPrelude.value = null
        ctx.hiddenLiveRecommendationCoverItemId.value = null
        setLiveSourceCoverHidden(ctx, room, false)
        ctx.activeLiveSourceAnchor.value = null
        ctx.liveVideoSurfaceVisible.value = true
        ctx.livePageAlpha.snapTo(1f)
        if (ctx.activeLiveOrigin.value == PageOrigin.My) ctx.myViewModel.refresh()
        if (closeSearchAfter) {
          if (ctx.activeLiveOrigin.value == PageOrigin.Search) ctx.closeSearchResultsAnimated()
          else ctx.animateToRootTab(RootTab.HOME)
        }
        ctx.liveTransitionJob.value = null
        return@launch
      }
      val session =
        CardTransitionSession(
          token = ++ctx.transitionToken.value,
          kind = TransitionKind.EXIT_ROOT,
          item = item,
          startBounds = startBounds,
          endBounds = destination,
          initialProgress = 0f,
          initialPanelAlpha = 1f,
        )
      session.transitionBitmap =
        prelude?.transitionBitmap
          ?: activeFlight?.transitionBitmap
          ?: LoadedFeedImageRegistry.bitmap(item.coverUrl)
      // 在静止的播放器位置封面下露出保留的源页面。源卡片自己的封面必须已经隐藏，
      // 让前奏期间只有一个副本。
      setLiveSourceCoverHidden(ctx, room, true)
      ctx.livePageAlpha.animateTo(
        0f,
        tween(if (ctx.settings.reduceMotion) 90 else 200, easing = FastOutSlowInEasing),
      )
      session.phase = SessionPhase.FLYING
      ctx.liveTransitionSession.value = session
      withFrameNanos {}
      ctx.liveExitPrelude.value = null
      session.progress.animateTo(
        1f,
        tween(if (ctx.settings.reduceMotion) 140 else 340, easing = FastOutSlowInEasing),
      )
      // 把保留的源封面恢复到落地的共享封面之下，然后等待该恢复被呈现，
      // 再移除覆盖层和详情层。
      setLiveSourceCoverHidden(ctx, room, false)
      withFrameNanos {}
      withFrameNanos {}
      session.phase = SessionPhase.COMPLETED
      ctx.activeLiveRoom.value = null
      ctx.activeLiveEntryId.value = 0L
      ctx.liveRoomParentStack.value = emptyList()
      ctx.liveTransitionSession.value = null
      ctx.liveExitPrelude.value = null
      ctx.hiddenLiveRecommendationCoverItemId.value = null
      ctx.activeLiveSourceAnchor.value = null
      ctx.livePlayerBounds.value = Rect.Zero
      ctx.liveVideoSurfaceVisible.value = true
      ctx.livePageAlpha.snapTo(1f)
      if (ctx.activeLiveOrigin.value == PageOrigin.My) ctx.myViewModel.refresh()
      if (closeSearchAfter) {
        if (ctx.activeLiveOrigin.value == PageOrigin.Search) ctx.closeSearchResultsAnimated()
        else ctx.animateToRootTab(RootTab.HOME)
      }
      ctx.liveTransitionJob.value = null
    }
  }

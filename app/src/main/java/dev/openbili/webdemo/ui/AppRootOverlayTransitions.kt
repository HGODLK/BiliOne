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

internal class AppRootOverlayContext(
  val scope: kotlinx.coroutines.CoroutineScope,
  val settings: dev.openbili.webdemo.settings.AppSettings,
  val searchViewModel: dev.openbili.webdemo.search.SearchViewModel,
  val bangumiIndexViewModel: dev.openbili.webdemo.bangumi.BangumiIndexViewModel,
  val bangumiExploreViewModel: dev.openbili.webdemo.bangumi.BangumiExploreViewModel,
  val articleStack: List<ArticleStackFrame>,
  val controlMode: Boolean,
  val rootTab: RootTab,
  val transitionPhase: androidx.compose.runtime.MutableState<TransitionPhase>,
  val focusManager: androidx.compose.ui.focus.FocusManager,
  val keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
  val controlInitialFocusRequester: androidx.compose.ui.focus.FocusRequester,
  val activeLiveRoom: androidx.compose.runtime.MutableState<dev.openbili.webdemo.live.LiveSearchRoom?>,
  val homeControlLevel: androidx.compose.runtime.MutableState<HomeControlLevel>,
  val bangumiControlLevel: androidx.compose.runtime.MutableState<BangumiControlLevel>,
  val homeControlSearchFocusRequest: androidx.compose.runtime.MutableState<Int>,
  val homeControlFocusRestoreRequest: androidx.compose.runtime.MutableState<Int>,
  val showSearch: androidx.compose.runtime.MutableState<Boolean>,
  val showSearchResults: androidx.compose.runtime.MutableState<Boolean>,
  val searchOpenedFromController: androidx.compose.runtime.MutableState<Boolean>,
  val showBangumiIndex: androidx.compose.runtime.MutableState<Boolean>,
  val bangumiIndexTransitionDirection: androidx.compose.runtime.MutableState<SearchTransitionDirection?>,
  val bangumiIndexTransitionSourceBounds: androidx.compose.runtime.MutableState<androidx.compose.ui.geometry.Rect>,
  val bangumiIndexTransitionJob: androidx.compose.runtime.MutableState<kotlinx.coroutines.Job?>,
  val bangumiIndexTransitionProgress: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
  val bangumiIndexTransitionMaskAlpha: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
  val bangumiIndexTransitionScrimAlpha: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
  val searchTransitionDirection: androidx.compose.runtime.MutableState<SearchTransitionDirection?>,
  val searchTransitionQuery: androidx.compose.runtime.MutableState<String>,
  val searchTransitionJob: androidx.compose.runtime.MutableState<kotlinx.coroutines.Job?>,
  val searchTransitionPreparation: androidx.compose.runtime.MutableState<TransitionPreparationBarrier?>,
  val searchTransitionSourceBounds: androidx.compose.runtime.MutableState<androidx.compose.ui.geometry.Rect>,
  val searchTransitionProgress: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
  val searchTransitionMaskAlpha: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
  val searchTransitionScrimAlpha: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
  val searchBounds: androidx.compose.runtime.MutableState<androidx.compose.ui.geometry.Rect>,
  val liveAreaIndex: LiveAreaIndexTransitionState,
  val liveAreaIndexFocusRestoreRequest: androidx.compose.runtime.MutableState<Int>,
  val appState: dev.openbili.webdemo.AppUiState,
)

internal fun requestHomeSearchFocus(ctx: AppRootOverlayContext) {
    if (ctx.controlMode && ctx.rootTab == RootTab.HOME) {
      when {
        ctx.searchOpenedFromController.value -> ctx.homeControlSearchFocusRequest.value++
        ctx.homeControlLevel.value == HomeControlLevel.ROOT ->
          ctx.scope.launch {
            withFrameNanos {}
            runCatching { ctx.controlInitialFocusRequester.requestFocus() }
          }
        else -> ctx.homeControlFocusRestoreRequest.value++
      }
    }
    ctx.searchOpenedFromController.value = false
  }

internal fun openSearchResultsAnimated(ctx: AppRootOverlayContext, keyword: String) {
    val normalized = keyword.trim()
    if (normalized.isEmpty() || ctx.appState.isVideoScreen) return
    ctx.searchTransitionJob.value?.cancel()
    ctx.keyboardController?.hide()
    ctx.focusManager.clearFocus(force = true)
    ctx.searchTransitionQuery.value = normalized
    ctx.searchTransitionSourceBounds.value = ctx.searchBounds.value
    ctx.searchTransitionPreparation.value?.cancel()
    val preparation =
      TransitionPreparationBarrier(
        setOf(
          TransitionReadySignal.SOURCE_BOUNDS,
          TransitionReadySignal.TARGET_MOUNTED,
          TransitionReadySignal.TARGET_BOUNDS_STABLE,
        )
      )
    preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
    ctx.searchTransitionPreparation.value = preparation
    ctx.showSearch.value = false
    ctx.showSearchResults.value = false
    ctx.searchTransitionDirection.value = SearchTransitionDirection.ENTER
    ctx.searchTransitionJob.value = ctx.scope.launch {
      ctx.searchTransitionMaskAlpha.snapTo(1f)
      ctx.searchTransitionScrimAlpha.snapTo(0f)
      ctx.searchTransitionProgress.snapTo(0f)
      withFrameNanos {}
      preparation.markReady(TransitionReadySignal.TARGET_MOUNTED)
      withFrameNanos {}
      preparation.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
      preparation.await()
      if (ctx.searchTransitionPreparation.value !== preparation) return@launch
      coroutineScope {
        launch {
          ctx.searchTransitionProgress.animateTo(
            1f,
            tween(if (ctx.settings.reduceMotion) 120 else 380, easing = FastOutSlowInEasing),
          )
        }
        launch {
          ctx.searchTransitionScrimAlpha.animateTo(
            1f,
            tween(if (ctx.settings.reduceMotion) 80 else 220, easing = FastOutSlowInEasing),
          )
        }
      }
      if (ctx.searchTransitionDirection.value != SearchTransitionDirection.ENTER) return@launch
      ctx.searchViewModel.search(normalized)
      ctx.showSearchResults.value = true
      withFrameNanos {}
      ctx.searchTransitionDirection.value = null
      ctx.searchTransitionPreparation.value = null
      ctx.searchTransitionMaskAlpha.snapTo(0f)
    }
  }

internal fun closeSearchResultsAnimated(ctx: AppRootOverlayContext) {
    if (
      ctx.appState.isVideoScreen ||
        ctx.articleStack.isNotEmpty() ||
        ctx.searchTransitionDirection.value == SearchTransitionDirection.EXIT
    )
      return
    val reversingEnter = ctx.searchTransitionDirection.value == SearchTransitionDirection.ENTER
    ctx.searchTransitionJob.value?.cancel()
    ctx.searchTransitionPreparation.value?.cancel()
    ctx.keyboardController?.hide()
    ctx.focusManager.clearFocus(force = true)
    if (!ctx.settings.retainLastSearchQuery) ctx.searchViewModel.clearEntry()
    if (ctx.searchBounds.value.width > 0f && ctx.searchBounds.value.height > 0f) {
      ctx.searchTransitionSourceBounds.value = ctx.searchBounds.value
    }
    ctx.showSearch.value = false
    val preparation =
      TransitionPreparationBarrier(
        setOf(
          TransitionReadySignal.SOURCE_BOUNDS,
          TransitionReadySignal.TARGET_MOUNTED,
          TransitionReadySignal.TARGET_BOUNDS_STABLE,
        )
      )
    preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
    ctx.searchTransitionPreparation.value = preparation
    ctx.searchTransitionDirection.value = SearchTransitionDirection.EXIT
    ctx.searchTransitionJob.value = ctx.scope.launch {
      if (!reversingEnter) {
        ctx.searchTransitionMaskAlpha.snapTo(0f)
        ctx.searchTransitionScrimAlpha.snapTo(0f)
        ctx.searchTransitionProgress.snapTo(1f)
        withFrameNanos {}
        withFrameNanos {}
        coroutineScope {
          launch {
            ctx.searchTransitionMaskAlpha.animateTo(
              1f,
              tween(if (ctx.settings.reduceMotion) 70 else 120),
            )
          }
          launch {
            ctx.searchTransitionScrimAlpha.animateTo(
              1f,
              tween(if (ctx.settings.reduceMotion) 70 else 120),
            )
          }
        }
        if (ctx.searchTransitionDirection.value != SearchTransitionDirection.EXIT) return@launch
        // 不透明表面现在完全盖住结果页。在改变几何形状前丢弃昂贵的网格，
        // 让退出时只有一块扁平 Material 表面参与。
        ctx.showSearchResults.value = false
        withFrameNanos {}
      } else {
        ctx.searchTransitionMaskAlpha.snapTo(1f)
        ctx.showSearchResults.value = false
      }
      preparation.markReady(
        TransitionReadySignal.TARGET_MOUNTED,
        TransitionReadySignal.TARGET_BOUNDS_STABLE,
      )
      preparation.await()
      if (ctx.searchTransitionPreparation.value !== preparation) return@launch
      coroutineScope {
        launch {
          ctx.searchTransitionProgress.animateTo(
            0f,
            tween(if (ctx.settings.reduceMotion) 100 else 320, easing = FastOutSlowInEasing),
          )
        }
        launch {
          ctx.searchTransitionScrimAlpha.animateTo(
            0f,
            tween(if (ctx.settings.reduceMotion) 70 else 180, easing = FastOutSlowInEasing),
          )
        }
      }
      ctx.searchTransitionMaskAlpha.animateTo(
        0f,
        tween(
          if (ctx.settings.reduceMotion) 70 else 180,
          easing = FastOutSlowInEasing,
        ),
      )
      // 从组合中移除覆盖层之前，先提交完全透明的胶囊。没有这一帧，最终的 alpha
      // 更新和节点移除可能被合并成一次硬切。
      withFrameNanos {}
      if (ctx.searchTransitionDirection.value == SearchTransitionDirection.EXIT) {
        ctx.searchTransitionDirection.value = null
        ctx.searchTransitionPreparation.value = null
        if (!ctx.settings.retainLastSearchQuery) ctx.searchViewModel.clearEntry()
        requestHomeSearchFocus(ctx)
      }
    }
  }

internal fun openBangumiIndexAnimated(ctx: AppRootOverlayContext, sourceBounds: Rect) {
    if (
      ctx.appState.isVideoScreen ||
        ctx.showBangumiIndex.value ||
        ctx.bangumiIndexTransitionDirection.value != null ||
        ctx.transitionPhase.value !is TransitionPhase.Feed
    )
      return
    // The index follows the explore page's current category (番剧/国创/电影…), not always 番剧.
    ctx.bangumiIndexViewModel.openCategory(ctx.bangumiExploreViewModel.state.value.selectedCategory)
    ctx.bangumiIndexTransitionJob.value?.cancel()
    ctx.bangumiIndexTransitionSourceBounds.value = sourceBounds.takeIf { it.hasUsableSize() } ?: Rect.Zero
    ctx.bangumiIndexTransitionDirection.value = SearchTransitionDirection.ENTER
    ctx.bangumiIndexTransitionJob.value = ctx.scope.launch {
      ctx.bangumiIndexTransitionMaskAlpha.snapTo(1f)
      ctx.bangumiIndexTransitionScrimAlpha.snapTo(0f)
      ctx.bangumiIndexTransitionProgress.snapTo(0f)
      // 源矩形已在上面冻结；等待两个已提交的目标帧再移动它。
      withFrameNanos {}
      withFrameNanos {}
      coroutineScope {
        launch {
          ctx.bangumiIndexTransitionProgress.animateTo(
            1f,
            tween(if (ctx.settings.reduceMotion) 120 else 380, easing = FastOutSlowInEasing),
          )
        }
        launch {
          ctx.bangumiIndexTransitionScrimAlpha.animateTo(
            1f,
            tween(if (ctx.settings.reduceMotion) 80 else 220, easing = FastOutSlowInEasing),
          )
        }
      }
      if (ctx.bangumiIndexTransitionDirection.value != SearchTransitionDirection.ENTER) return@launch
      ctx.showBangumiIndex.value = true
      withFrameNanos {}
      ctx.bangumiIndexTransitionDirection.value = null
      ctx.bangumiIndexTransitionMaskAlpha.snapTo(0f)
      // 网络工作只在轻量 Surface 落地且页面挂载之后才开始。
      ctx.bangumiIndexViewModel.ensureLoaded()
    }
  }

internal fun closeBangumiIndexAnimated(ctx: AppRootOverlayContext) {
    if (ctx.appState.isVideoScreen || ctx.bangumiIndexTransitionDirection.value == SearchTransitionDirection.EXIT)
      return
    ctx.bangumiControlLevel.value = BangumiControlLevel.EXPLORE_NAV
    val reversingEnter = ctx.bangumiIndexTransitionDirection.value == SearchTransitionDirection.ENTER
    ctx.bangumiIndexTransitionJob.value?.cancel()
    ctx.bangumiIndexTransitionDirection.value = SearchTransitionDirection.EXIT
    ctx.bangumiIndexTransitionJob.value = ctx.scope.launch {
      if (!reversingEnter) {
        ctx.bangumiIndexTransitionMaskAlpha.snapTo(0f)
        ctx.bangumiIndexTransitionScrimAlpha.snapTo(0f)
        ctx.bangumiIndexTransitionProgress.snapTo(1f)
        coroutineScope {
          launch {
            ctx.bangumiIndexTransitionMaskAlpha.animateTo(
              1f,
              tween(if (ctx.settings.reduceMotion) 70 else 120),
            )
          }
          launch {
            ctx.bangumiIndexTransitionScrimAlpha.animateTo(
              1f,
              tween(if (ctx.settings.reduceMotion) 70 else 120),
            )
          }
        }
        ctx.showBangumiIndex.value = false
        withFrameNanos {}
      } else {
        ctx.bangumiIndexTransitionMaskAlpha.snapTo(1f)
        ctx.showBangumiIndex.value = false
      }
      coroutineScope {
        launch {
          ctx.bangumiIndexTransitionProgress.animateTo(
            0f,
            tween(if (ctx.settings.reduceMotion) 100 else 320, easing = FastOutSlowInEasing),
          )
        }
        launch {
          ctx.bangumiIndexTransitionScrimAlpha.animateTo(
            0f,
            tween(if (ctx.settings.reduceMotion) 70 else 180, easing = FastOutSlowInEasing),
          )
        }
      }
      ctx.bangumiIndexTransitionMaskAlpha.animateTo(0f, tween(if (ctx.settings.reduceMotion) 60 else 100))
      if (ctx.bangumiIndexTransitionDirection.value == SearchTransitionDirection.EXIT) {
        ctx.bangumiIndexTransitionDirection.value = null
      }
    }
  }



internal fun openLiveAreaIndexAnimated(ctx: AppRootOverlayContext, sourceBounds: Rect) {
    openLiveAreaIndex(
      scope = ctx.scope,
      state = ctx.liveAreaIndex,
      sourceBounds = sourceBounds,
      reduceMotion = ctx.settings.reduceMotion,
      blocked = ctx.appState.isVideoScreen || ctx.transitionPhase.value !is TransitionPhase.Feed,
    )
  }

internal fun closeLiveAreaIndexAnimated(ctx: AppRootOverlayContext) {
    closeLiveAreaIndex(
      scope = ctx.scope,
      state = ctx.liveAreaIndex,
      reduceMotion = ctx.settings.reduceMotion,
      blocked = ctx.appState.isVideoScreen,
      onFinished = { ctx.liveAreaIndexFocusRestoreRequest.value++ },
    )
  }


@Composable
internal fun AppRootOverlayBackHandlers(ctx: AppRootOverlayContext) {
  BackHandler(
    enabled =
      ctx.activeLiveRoom.value == null &&
        ctx.searchTransitionDirection.value != null &&
        !ctx.appState.isVideoScreen &&
        ctx.articleStack.isEmpty(),
    onBack = { closeSearchResultsAnimated(ctx) },
  )
  BackHandler(
    enabled =
      (ctx.showBangumiIndex.value || ctx.bangumiIndexTransitionDirection.value != null) &&
        !ctx.appState.isVideoScreen &&
        ctx.articleStack.isEmpty(),
    onBack = { closeBangumiIndexAnimated(ctx) },
  )
  BackHandler(
    enabled =
      (ctx.liveAreaIndex.show || ctx.liveAreaIndex.direction != null) &&
        !ctx.appState.isVideoScreen,
    onBack = { closeLiveAreaIndexAnimated(ctx) },
  )
}

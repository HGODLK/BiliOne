package dev.openbili.webdemo.ui

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.openbili.webdemo.BiliApplication
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.SpaceDynamicItem
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.dynamic.HomeDynamicScreen
import dev.openbili.webdemo.dynamic.HomeDynamicViewModel
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedScreen
import dev.openbili.webdemo.feed.FeedScrollAnchor
import dev.openbili.webdemo.feed.FeedUiState
import dev.openbili.webdemo.feed.LocalCoverImageLoadingEnabled
import dev.openbili.webdemo.feed.PopularFeedScreen
import dev.openbili.webdemo.feed.PopularFeedViewModel
import dev.openbili.webdemo.live.LiveHomeScreen
import dev.openbili.webdemo.live.LiveHomeSourceAnchor
import dev.openbili.webdemo.live.LiveHomeViewModel
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.music.isMusicLaunchReady
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.video.CommentProfileAnchor
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal enum class HomeHubTab(val label: String) {
  RECOMMENDATION("推荐"),
  DYNAMIC("动态"),
  POPULAR("热门"),
  LIVE("直播"),
}

internal enum class HomeSecondLevelAction {
  IMMERSIVE,
  MUSIC,
  SCROLL_TOP,
  REFRESH,
}

internal fun homeSecondLevelActionBelowTab(tabIndex: Int): HomeSecondLevelAction =
  if (tabIndex <= 1) HomeSecondLevelAction.IMMERSIVE else HomeSecondLevelAction.SCROLL_TOP

private enum class HomeControlDirection {
  LEFT,
  RIGHT,
  UP,
  DOWN,
}

private fun homeControlDirection(keyCode: Int): HomeControlDirection? =
  when (keyCode) {
    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> HomeControlDirection.LEFT
    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> HomeControlDirection.RIGHT
    AndroidKeyEvent.KEYCODE_DPAD_UP -> HomeControlDirection.UP
    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> HomeControlDirection.DOWN
    else -> null
  }

enum class HomeControlLevel {
  ROOT,
  TABS,
  PAGE_CONTROLS,
  CONTENT,
}

internal fun homeTabEntryLevel(tab: HomeHubTab): HomeControlLevel =
  if (tab == HomeHubTab.DYNAMIC || tab == HomeHubTab.POPULAR) {
    HomeControlLevel.PAGE_CONTROLS
  } else {
    HomeControlLevel.CONTENT
  }

internal fun homeContentParentLevel(tab: HomeHubTab): HomeControlLevel =
  if (tab == HomeHubTab.DYNAMIC || tab == HomeHubTab.POPULAR) {
    HomeControlLevel.PAGE_CONTROLS
  } else {
    HomeControlLevel.TABS
  }

internal fun homeControlBackTarget(
  tab: HomeHubTab,
  level: HomeControlLevel,
): HomeControlLevel =
  when (level) {
    HomeControlLevel.CONTENT -> homeContentParentLevel(tab)
    HomeControlLevel.PAGE_CONTROLS -> HomeControlLevel.TABS
    HomeControlLevel.TABS -> HomeControlLevel.ROOT
    HomeControlLevel.ROOT -> HomeControlLevel.ROOT
  }

internal fun canShowControlHomeExit(level: HomeControlLevel): Boolean =
  level == HomeControlLevel.ROOT

private enum class HomeSecondLevelFocusRegion {
  ACCOUNT,
  SEARCH,
  TAB,
  ACTION,
}

private const val MUSIC_ENTRY_INPUT_LOCK_TIMEOUT_MS = 5_000L

enum class HomeRecommendationMode {
  NORMAL,
  IMMERSIVE,
  MUSIC,
}

internal fun shouldLockHomeHubPager(
  dynamicPageSelected: Boolean,
  selectedDynamicId: String?,
  dynamicOverlayActive: Boolean,
  recommendationMode: HomeRecommendationMode = HomeRecommendationMode.NORMAL,
): Boolean =
  recommendationMode != HomeRecommendationMode.NORMAL ||
    (dynamicPageSelected && (selectedDynamicId != null || dynamicOverlayActive))

internal fun shouldEnableHomeHubPagerUserScroll(
  horizontalRailTouched: Boolean,
  navigationLocked: Boolean,
  recommendationMode: HomeRecommendationMode,
): Boolean =
  !horizontalRailTouched &&
    !navigationLocked &&
    recommendationMode == HomeRecommendationMode.NORMAL

/**
 * 首页的二级分页器。根分页器仍负责 HOME/BANGUMI/MY；本组件只拥有 HOME 内部的
 * 四个内容模式。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeHubScreen(
  feedState: FeedUiState,
  userInfo: UserInfo,
  onRecommendationRefresh: () -> Unit,
  onRecommendationPullRefresh: (Int) -> Unit,
  onRecommendationLoadNextPage: () -> Unit,
  onRecommendationItemClick: (FeedItem, Rect, FeedScrollAnchor) -> Unit,
  onPopularItemClick: (FeedItem, Rect, FeedScrollAnchor) -> Unit,
  onDynamicItemClick: (SpaceDynamicItem, FeedItem, Rect) -> Unit,
  onDynamicLiveClick: (LiveSearchRoom, Rect) -> Unit,
  onDynamicItemBoundsChanged: (String, Rect) -> Unit,
  onDynamicLiveBoundsChanged: (LiveSearchRoom, Rect) -> Unit,
  onDynamicArticleClick: (ArticleItem, Rect) -> Unit,
  onDynamicArticleBoundsChanged: (ArticleItem, Rect) -> Unit,
  onDynamicCommentProfileClick: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onDynamicAvatarProfileClick: (Long, String?, String?, Rect) -> Unit,
  onRecommendationItemLongClick: (FeedItem) -> Unit,
  onRecommendationProfileClick: (FeedItem, Rect) -> Unit,
  onRecommendationLoginClick: (Rect) -> Unit,
  onSearch: (fromController: Boolean) -> Unit,
  searchQuery: String,
  onConsumeRecommendationRefreshMessage: () -> Unit,
  onSearchBoundsChanged: (Rect) -> Unit,
  coverPrefetchCount: Int,
  backgroundWorkAllowed: Boolean,
  recommendationGridState: LazyGridState,
  hiddenRecommendationCoverItemId: String?,
  hiddenPopularCoverItemId: String?,
  hiddenDynamicCoverItemId: String?,
  hiddenDynamicLiveCoverItemId: String? = null,
  hiddenDynamicArticleItemId: String?,
  hiddenDynamicCommentAvatarRpid: Long?,
  hiddenDynamicAvatarSourceBounds: Rect?,
  dismissedRecommendationItemIds: Set<String>,
  onRestoreDismissedRecommendationItem: (FeedItem) -> Unit,
  onRecommendationItemBoundsChanged: (FeedItem, Rect) -> Unit,
  onLiveRoomClick: (LiveSearchRoom, LiveHomeSourceAnchor, Rect) -> Unit,
  onLiveRoomBoundsChanged: (LiveHomeSourceAnchor, Rect) -> Unit,
  onLiveTransitionActiveChanged: (Boolean) -> Unit,
  hiddenLiveCoverItemId: String?,
  liveDetailActive: Boolean,
  rootPageVisible: Boolean,
  onDynamicDetailActiveChanged: (Boolean) -> Unit,
  recommendationMode: HomeRecommendationMode,
  onRecommendationModeChanged: (HomeRecommendationMode) -> Unit,
  onLiveAreaIndex: (Rect) -> Unit,
  liveAreaIndexFocusRestoreRequest: Int = 0,
  onMusicFavoriteFolderSelected: (Long) -> Unit,
  onMusicEntryInputLockChanged: (Boolean) -> Unit,
  settings: AppSettings,
  controlSecondLevelRequest: Int = 0,
  controlSearchFocusRequest: Int = 0,
  controlFocusRestoreRequest: Int = 0,
  controlLevel: HomeControlLevel,
  onControlLevelChanged: (HomeControlLevel) -> Unit = {},
) {
  val popularViewModel: PopularFeedViewModel = viewModel()
  val popularState by popularViewModel.state.collectAsState()
  val dynamicViewModel: HomeDynamicViewModel = viewModel()
  val dynamicState by dynamicViewModel.state.collectAsState()
  val liveViewModel: LiveHomeViewModel = viewModel()
  val liveState by liveViewModel.state.collectAsState()
  val musicPlayerViewModel =
    (LocalContext.current.applicationContext as BiliApplication).homeMusicPlayerViewModel
  val musicPlayerState by musicPlayerViewModel.screenState.collectAsState()
  val homeDefaultTabIndex = settings.homeDefaultTab.coerceIn(0, HomeHubTab.entries.lastIndex)
  val pagerState =
    rememberPagerState(
      initialPage = homeDefaultTabIndex,
      pageCount = { HomeHubTab.entries.size },
    )
  val popularGridState = rememberLazyGridState()
  val liveGridState = rememberLazyGridState()
  val scope = rememberCoroutineScope()
  val controlMode = LocalControlMode.current
  val controlTabFocusRequesters = remember { HomeHubTab.entries.map { FocusRequester() } }
  val controlContentFocusRequesters = remember { HomeHubTab.entries.map { FocusRequester() } }
  val controlAccountFocusRequester = remember { FocusRequester() }
  val controlSearchFocusRequester = remember { FocusRequester() }
  val controlActionFocusRequesters =
    remember { HomeSecondLevelAction.entries.associateWith { FocusRequester() } }
  val controlFocusMemoryRequester = remember { FocusRequester() }
  var lastControlTabIndex by remember { mutableIntStateOf(homeDefaultTabIndex) }
  var controlSelectedTabIndex by remember { mutableIntStateOf(homeDefaultTabIndex) }
  var lastControlAction by remember { mutableStateOf<HomeSecondLevelAction?>(null) }
  var lastSecondLevelFocusRegion by remember { mutableStateOf(HomeSecondLevelFocusRegion.TAB) }
  var pendingControlContentTab by remember { mutableStateOf<HomeHubTab?>(null) }
  var controlFocusRestorePending by remember { mutableStateOf(false) }
  var controlInputFromController by remember { mutableStateOf(true) }
  var controlTabNavigationJob by remember { mutableStateOf<Job?>(null) }
  var horizontalRailTouched by remember { mutableStateOf(false) }
  var dynamicOverlayActive by remember { mutableStateOf(false) }
  var musicLaunchPending by remember { mutableStateOf(false) }
  var musicExitInProgress by remember { mutableStateOf(false) }
  var musicEntryInputLocked by remember { mutableStateOf(false) }
  val lifecycleOwner = LocalLifecycleOwner.current
  val dynamicPageSelected = pagerState.currentPage == HomeHubTab.DYNAMIC.ordinal
  val dynamicPageVisible = dynamicPageSelected && rootPageVisible
  val activeTab = HomeHubTab.entries[pagerState.currentPage]
  val recommendationSelected = activeTab == HomeHubTab.RECOMMENDATION
  DisposableEffect(Unit) {
    onDispose { onControlLevelChanged(HomeControlLevel.ROOT) }
  }
  val controlContentReadyMarker =
    when (val tab = pendingControlContentTab) {
      HomeHubTab.RECOMMENDATION ->
        (feedState as? FeedUiState.Content)?.items?.firstOrNull()?.id
      HomeHubTab.DYNAMIC -> dynamicState.items.firstOrNull()?.id
      HomeHubTab.POPULAR ->
        (popularState.content as? FeedUiState.Content)?.items?.firstOrNull()?.id
      HomeHubTab.LIVE ->
        (liveState as? dev.openbili.webdemo.live.LiveHomeUiState.Content)
          ?.let { content -> content.heroRooms.firstOrNull() ?: content.rooms.firstOrNull() }
          ?.stableId
      null -> null
    }
  val dynamicNavigationLocked =
    shouldLockHomeHubPager(
      dynamicPageSelected = dynamicPageSelected,
      selectedDynamicId = dynamicState.selectedDynamicId,
      dynamicOverlayActive = dynamicOverlayActive,
      recommendationMode = recommendationMode,
    )
  val dynamicDetailActive = dynamicPageVisible && dynamicOverlayActive
  val musicOverlayActive = musicLaunchPending || recommendationMode == HomeRecommendationMode.MUSIC
  val homeContentBackgroundWorkAllowed = backgroundWorkAllowed && !musicOverlayActive
  LaunchedEffect(controlSecondLevelRequest, controlMode, rootPageVisible) {
    if (controlMode && rootPageVisible && controlSecondLevelRequest > 0) {
      controlTabNavigationJob?.cancel()
      controlInputFromController = true
      onControlLevelChanged(HomeControlLevel.TABS)
      pendingControlContentTab = null
      lastControlTabIndex = activeTab.ordinal
      controlSelectedTabIndex = activeTab.ordinal
      lastControlAction = null
      lastSecondLevelFocusRegion = HomeSecondLevelFocusRegion.TAB
      withFrameNanos {}
      runCatching { controlTabFocusRequesters[activeTab.ordinal].requestFocus() }
    }
  }
  LaunchedEffect(controlSearchFocusRequest, controlMode, rootPageVisible) {
    if (controlMode && rootPageVisible && controlSearchFocusRequest > 0) {
      controlTabNavigationJob?.cancel()
      controlInputFromController = true
      onControlLevelChanged(HomeControlLevel.TABS)
      pendingControlContentTab = null
      lastControlAction = null
      lastSecondLevelFocusRegion = HomeSecondLevelFocusRegion.SEARCH
      withFrameNanos {}
      withFrameNanos {}
      runCatching { controlSearchFocusRequester.requestFocus() }
    }
  }
  LaunchedEffect(controlFocusRestoreRequest, controlMode, rootPageVisible) {
    if (controlMode && rootPageVisible && controlFocusRestoreRequest > 0) {
      withFrameNanos {}
      withFrameNanos {}
      val restored =
        runCatching { controlFocusMemoryRequester.restoreFocusedChild() }.getOrDefault(false)
      if (!restored) {
        val fallback =
          when (controlLevel) {
            HomeControlLevel.TABS ->
              when (lastSecondLevelFocusRegion) {
                HomeSecondLevelFocusRegion.ACCOUNT -> controlAccountFocusRequester
                HomeSecondLevelFocusRegion.SEARCH -> controlSearchFocusRequester
                HomeSecondLevelFocusRegion.TAB -> controlTabFocusRequesters[lastControlTabIndex]
                HomeSecondLevelFocusRegion.ACTION ->
                  lastControlAction?.let(controlActionFocusRequesters::get)
                    ?: controlTabFocusRequesters[lastControlTabIndex]
              }
            HomeControlLevel.PAGE_CONTROLS ->
              lastControlAction?.let(controlActionFocusRequesters::get)
                ?: controlTabFocusRequesters[lastControlTabIndex]
            HomeControlLevel.CONTENT ->
              controlContentFocusRequesters[controlSelectedTabIndex]
            HomeControlLevel.ROOT -> null
          }
        fallback?.let { runCatching { it.requestFocus() } }
      }
      controlFocusRestorePending = false
    }
  }
  LaunchedEffect(rootPageVisible, controlMode) {
    if (!controlMode || !rootPageVisible) {
      controlTabNavigationJob?.cancel()
      onControlLevelChanged(HomeControlLevel.ROOT)
      pendingControlContentTab = null
      controlFocusRestorePending = false
    }
  }
  LaunchedEffect(
    pendingControlContentTab,
    controlContentReadyMarker,
    pagerState.currentPage,
  ) {
    val target = pendingControlContentTab ?: return@LaunchedEffect
    if (!controlMode || pagerState.currentPage != target.ordinal) return@LaunchedEffect
    if (controlContentReadyMarker == null) return@LaunchedEffect
    // 第一项的焦点请求器可能在就绪标记出现若干帧之后才挂载（惰性组合、头图数据
    // 迟到）。在有界窗口内重试，让数据在标签切换后才加载完成的页面仍能自动获得焦点。
    val deadline = SystemClock.uptimeMillis() + 3000
    while (
      pendingControlContentTab == target &&
        controlMode &&
        pagerState.currentPage == target.ordinal &&
        SystemClock.uptimeMillis() < deadline
    ) {
      withFrameNanos {}
      val focused =
        runCatching { controlContentFocusRequesters[target.ordinal].requestFocus() }
          .getOrDefault(false)
      if (focused) {
        pendingControlContentTab = null
        return@LaunchedEffect
      }
      delay(50)
    }
  }
  LaunchedEffect(recommendationMode, controlMode, rootPageVisible) {
    if (
      controlMode &&
        rootPageVisible &&
        recommendationSelected &&
        recommendationMode == HomeRecommendationMode.IMMERSIVE
    ) {
      controlInputFromController = true
      onControlLevelChanged(HomeControlLevel.CONTENT)
      // 在请求第一张卡片焦点之前，等待浮动操作组完成其退出动画，
      // 让离开的按钮不会在转场途中把焦点抢回去。
      delay(if (settings.reduceMotion) 120 else 260)
      pendingControlContentTab = HomeHubTab.RECOMMENDATION
    }
  }
  LaunchedEffect(recommendationMode, controlLevel, recommendationSelected, lastControlAction) {
    val action = lastControlAction ?: return@LaunchedEffect
    if (
      controlMode &&
        rootPageVisible &&
        controlLevel == HomeControlLevel.TABS &&
        recommendationSelected &&
        recommendationMode == HomeRecommendationMode.NORMAL
    ) {
      withFrameNanos {}
      runCatching { controlActionFocusRequesters.getValue(action).requestFocus() }
    }
  }
  val stagedMusicBackgroundSource =
    when {
      !musicLaunchPending -> ""
      settings.useHomeBackgroundForMusic && settings.homeBackgroundUri.isNotBlank() ->
        settings.homeBackgroundUri
      else -> musicPlayerState.currentItem?.coverUrl.orEmpty()
    }
  val stagedMusicBackgroundModel =
    if (stagedMusicBackgroundSource.isNotBlank()) {
      val useCustomMusicBackground =
        settings.useHomeBackgroundForMusic && settings.homeBackgroundUri.isNotBlank()
      rememberStaticBackgroundModel(
        source = stagedMusicBackgroundSource,
        blurred = !useCustomMusicBackground || settings.homeBackgroundMusicBlur,
      )
    } else {
      null
    }
  val stagedMusicBackgroundReady =
    stagedMusicBackgroundSource.isBlank() || stagedMusicBackgroundModel != null
  LaunchedEffect(
    musicLaunchPending,
    musicPlayerState,
    stagedMusicBackgroundReady,
    recommendationSelected,
    rootPageVisible,
    recommendationMode,
  ) {
    if (!musicLaunchPending) return@LaunchedEffect
    if (
      !recommendationSelected ||
        !rootPageVisible ||
        recommendationMode != HomeRecommendationMode.NORMAL
    ) {
      musicLaunchPending = false
      musicEntryInputLocked = false
      return@LaunchedEffect
    }
    if (isMusicLaunchReady(musicPlayerState) && stagedMusicBackgroundReady) {
      musicLaunchPending = false
      onRecommendationModeChanged(HomeRecommendationMode.MUSIC)
    }
  }
  // 音乐页在加载和滑入期间绝不能接受触摸。该锁由 onEntrySettled 在页面完全进入后
  // 释放，同时以这个超时作为硬上限，让卡住的加载永远无法锁死屏幕。
  LaunchedEffect(musicEntryInputLocked) {
    if (musicEntryInputLocked) {
      delay(MUSIC_ENTRY_INPUT_LOCK_TIMEOUT_MS)
      musicEntryInputLocked = false
    }
  }
  LaunchedEffect(musicEntryInputLocked) {
    onMusicEntryInputLockChanged(musicEntryInputLocked)
  }
  LaunchedEffect(dynamicDetailActive) {
    onDynamicDetailActiveChanged(dynamicDetailActive)
  }
  DisposableEffect(Unit) {
    onDispose { onDynamicDetailActiveChanged(false) }
  }
  LaunchedEffect(pagerState.currentPage, userInfo.isLogin) {
    if (HomeHubTab.entries[pagerState.currentPage] == HomeHubTab.DYNAMIC) {
      dynamicViewModel.ensureLoaded(userInfo.isLogin)
    }
  }
  LaunchedEffect(dynamicPageVisible) {
    dynamicViewModel.setPageVisible(dynamicPageVisible)
  }
  LaunchedEffect(dynamicPageVisible, backgroundWorkAllowed) {
    dynamicViewModel.setHostCommitAllowed(dynamicPageVisible && backgroundWorkAllowed)
  }
  LaunchedEffect(dynamicPageVisible, userInfo.isLogin, lifecycleOwner) {
    if (!dynamicPageVisible || !userInfo.isLogin) return@LaunchedEffect
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      while (isActive) {
        delay(5_000)
        dynamicViewModel.pollUpdates()
      }
    }
  }
  val backgroundBackdropLayer = rememberGraphicsLayer()
  val recommendationBackdropLayer = rememberGraphicsLayer()
  val dynamicBackdropLayer = rememberGraphicsLayer()
  val popularBackdropLayer = rememberGraphicsLayer()
  val liveBackdropLayer = rememberGraphicsLayer()
  var backgroundBackdropBounds by remember { mutableStateOf(Rect.Zero) }
  var recommendationBackdropBounds by remember { mutableStateOf(Rect.Zero) }
  var dynamicBackdropBounds by remember { mutableStateOf(Rect.Zero) }
  var popularBackdropBounds by remember { mutableStateOf(Rect.Zero) }
  var liveBackdropBounds by remember { mutableStateOf(Rect.Zero) }
  var headerHeightPx by remember { mutableIntStateOf(0) }
  val density = LocalDensity.current
  val headerClearance = with(density) { headerHeightPx.toDp() }
  val immersiveMode = recommendationMode == HomeRecommendationMode.IMMERSIVE
  val recommendationTopPadding by
    animateDpAsState(
      targetValue =
        if (immersiveMode) 12.dp
        else headerClearance + if (controlMode) 20.dp else 12.dp,
      animationSpec = tween(if (settings.reduceMotion) 90 else 220),
      label = "recommendationTopPadding",
    )
  val recommendationBottomPadding by
    animateDpAsState(
      targetValue = if (immersiveMode) 16.dp else NavigationCardBottomClearance,
      animationSpec = tween(if (settings.reduceMotion) 90 else 220),
      label = "recommendationBottomPadding",
    )
  BackHandler(
    enabled =
      controlMode &&
        rootPageVisible &&
        backgroundWorkAllowed &&
        !dynamicOverlayActive &&
        recommendationMode == HomeRecommendationMode.NORMAL &&
        controlLevel != HomeControlLevel.ROOT
  ) {
    controlTabNavigationJob?.cancel()
    controlInputFromController = true
    controlFocusRestorePending = false
    val controlTab = HomeHubTab.entries[controlSelectedTabIndex]
    val targetLevel = homeControlBackTarget(controlTab, controlLevel)
    if (pagerState.currentPage != controlTab.ordinal) {
      pagerState.requestScrollToPage(controlTab.ordinal)
    }
    pendingControlContentTab = null
    onControlLevelChanged(targetLevel)
    when (targetLevel) {
      HomeControlLevel.TABS -> {
        lastControlTabIndex = controlTab.ordinal
        lastControlAction = null
        lastSecondLevelFocusRegion = HomeSecondLevelFocusRegion.TAB
        scope.launch {
          withFrameNanos {}
          runCatching { controlTabFocusRequesters[controlTab.ordinal].requestFocus() }
        }
      }
      HomeControlLevel.PAGE_CONTROLS -> Unit
      HomeControlLevel.CONTENT -> pendingControlContentTab = controlTab
      HomeControlLevel.ROOT -> Unit
    }
  }
  BackHandler(
    enabled =
      rootPageVisible &&
        backgroundWorkAllowed &&
        recommendationSelected &&
        recommendationMode == HomeRecommendationMode.IMMERSIVE
  ) {
    onRecommendationModeChanged(HomeRecommendationMode.NORMAL)
    if (controlMode && controlLevel == HomeControlLevel.CONTENT) {
      controlInputFromController = true
      lastControlAction = HomeSecondLevelAction.IMMERSIVE
      lastSecondLevelFocusRegion = HomeSecondLevelFocusRegion.ACTION
      onControlLevelChanged(HomeControlLevel.TABS)
    }
  }
  val activeBackdropLayer =
    when (activeTab) {
      HomeHubTab.RECOMMENDATION -> recommendationBackdropLayer
      HomeHubTab.DYNAMIC -> dynamicBackdropLayer
      HomeHubTab.POPULAR -> popularBackdropLayer
      HomeHubTab.LIVE -> liveBackdropLayer
    }
  val activeBackdropBounds =
    when (activeTab) {
      HomeHubTab.RECOMMENDATION -> recommendationBackdropBounds
      HomeHubTab.DYNAMIC -> dynamicBackdropBounds
      HomeHubTab.POPULAR -> popularBackdropBounds
      HomeHubTab.LIVE -> liveBackdropBounds
    }
  CompositionLocalProvider(
    LocalCoverImageLoadingEnabled provides homeContentBackgroundWorkAllowed
  ) {
    Box(
      Modifier.fillMaxSize()
        .focusRequester(controlFocusMemoryRequester)
        .focusGroup()
        .pointerInput(controlMode) {
          if (!controlMode) return@pointerInput
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              if (
                !controlFocusRestorePending &&
                  event.changes.any { it.pressed && !it.previousPressed }
              ) {
                controlInputFromController = false
                controlFocusRestorePending =
                  runCatching { controlFocusMemoryRequester.saveFocusedChild() }
                    .getOrDefault(false)
              }
            }
          }
        }
        .onPreviewKeyEvent { event ->
          if (!controlMode) return@onPreviewKeyEvent false
          val keyCode = event.nativeKeyEvent.keyCode
          val controlKey =
            homeControlDirection(keyCode) != null || isControlConfirmKey(keyCode)
          if (!controlKey) return@onPreviewKeyEvent false
          if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
            controlInputFromController = true
            if (controlFocusRestorePending) {
              controlFocusRestorePending = false
              val rememberedTab = HomeHubTab.entries[controlSelectedTabIndex]
              if (
                controlLevel != HomeControlLevel.ROOT &&
                  pagerState.currentPage != rememberedTab.ordinal
              ) {
                controlTabNavigationJob?.cancel()
                pagerState.requestScrollToPage(rememberedTab.ordinal)
                if (controlLevel == HomeControlLevel.CONTENT) {
                  pendingControlContentTab = rememberedTab
                }
                scope.launch {
                  withFrameNanos {}
                  withFrameNanos {}
                  when (controlLevel) {
                    HomeControlLevel.TABS ->
                      runCatching { controlTabFocusRequesters[lastControlTabIndex].requestFocus() }
                    HomeControlLevel.CONTENT ->
                      runCatching {
                        controlContentFocusRequesters[rememberedTab.ordinal].requestFocus()
                      }
                    HomeControlLevel.PAGE_CONTROLS,
                    HomeControlLevel.ROOT -> Unit
                  }
                }
                return@onPreviewKeyEvent true
              }
              val restored =
                runCatching { controlFocusMemoryRequester.restoreFocusedChild() }
                  .getOrDefault(false)
              if (!restored && controlLevel == HomeControlLevel.TABS) {
                val fallback =
                  when (lastSecondLevelFocusRegion) {
                    HomeSecondLevelFocusRegion.ACCOUNT -> controlAccountFocusRequester
                    HomeSecondLevelFocusRegion.SEARCH -> controlSearchFocusRequester
                    HomeSecondLevelFocusRegion.TAB ->
                      controlTabFocusRequesters[lastControlTabIndex]
                    HomeSecondLevelFocusRegion.ACTION ->
                      lastControlAction?.let(controlActionFocusRequesters::get)
                        ?: controlTabFocusRequesters[lastControlTabIndex]
                  }
                runCatching { fallback.requestFocus() }
              }
            }
          }
          false
        }
    ) {
      Box(
        Modifier.fillMaxSize()
          .onGloballyPositioned { backgroundBackdropBounds = it.boundsInRoot() }
          .drawWithContent {
            if (homeContentBackgroundWorkAllowed) {
              backgroundBackdropLayer.record { this@drawWithContent.drawContent() }
              drawLayer(backgroundBackdropLayer)
            } else {
              drawContent()
            }
          }
          .background(MaterialTheme.colorScheme.background)
      ) {
        if (settings.homeBackgroundUri.isNotBlank()) {
          val backgroundModel =
            rememberStaticBackgroundModel(
              source = settings.homeBackgroundUri,
              blurred = settings.homeBackgroundBlur,
            )
          CrossfadeBackgroundImage(
            model = backgroundModel,
            modifier =
              Modifier.fillMaxSize().graphicsLayer {
                alpha =
                  if (settings.homeBackgroundBlur || musicExitInProgress) 1f
                  else 1f - settings.homeBackgroundTransparency.coerceIn(0f, 1f)
              },
            contentScale = ContentScale.Crop,
            transitionMillis = if (settings.reduceMotion) 90 else 300,
          )
        }
      }
      HorizontalPager(
        state = pagerState,
        // 二级导航有意漂浮在信息流之上，而不是预留一条工具栏宽的条带。让分页器
        // 保持边到边，也把避开刘海的安全偏移留给胶囊本身，而不是烘焙进每个子页。
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = if (controlMode) 0 else 1,
        // 周期/分类轨道从首次按下事件起就拥有水平拖动。这防止一次很长的历史滑动
        // 也同时移动推荐/热门/直播分页器。
        userScrollEnabled =
          shouldEnableHomeHubPagerUserScroll(
            horizontalRailTouched = horizontalRailTouched,
            navigationLocked = dynamicNavigationLocked,
            recommendationMode = recommendationMode,
          ),
        key = { HomeHubTab.entries[it] },
      ) { page ->
        Box(
          Modifier.fillMaxSize()
            .then(
              // 控制器的方向键导航绝不能跨入相邻的二级页面。仅以 controlMode 作护栏：
              // 跟踪的控制层级可能落后于触摸驱动的页面切换，而按已稳定的页面索引做闸门
              // 会在分页稳定后留下一帧窗口，让焦点搜索逃入仍在组合中的相邻页。
              if (controlMode) {
                Modifier.focusProperties {
                    onExit = {
                      if (
                        requestedFocusDirection == FocusDirection.Left ||
                          requestedFocusDirection == FocusDirection.Right
                      ) {
                        cancelFocusChange()
                      }
                    }
                  }
                  .focusGroup()
              } else Modifier
            )
        ) {
          when (HomeHubTab.entries[page]) {
          HomeHubTab.RECOMMENDATION ->
            CapturedHomePageContent(
              layer = recommendationBackdropLayer,
              captureEnabled = homeContentBackgroundWorkAllowed,
              onBoundsChanged = { recommendationBackdropBounds = it },
            ) {
              FeedScreen(
                state = feedState,
                onRefresh = onRecommendationRefresh,
                onPullRefresh = onRecommendationPullRefresh,
                onLoadNextPage = onRecommendationLoadNextPage,
                onItemClick = onRecommendationItemClick,
                onItemLongClick = onRecommendationItemLongClick,
                onProfileClick = onRecommendationProfileClick,
                onConsumeRefreshMessage = onConsumeRecommendationRefreshMessage,
                coverPrefetchCount = coverPrefetchCount,
                backgroundWorkAllowed =
                  homeContentBackgroundWorkAllowed &&
                    pagerState.currentPage == HomeHubTab.RECOMMENDATION.ordinal,
                gridState = recommendationGridState,
                hiddenCoverItemId = hiddenRecommendationCoverItemId,
                dismissedItemIds = dismissedRecommendationItemIds,
                onRestoreDismissedItem = onRestoreDismissedRecommendationItem,
                onItemBoundsChanged = onRecommendationItemBoundsChanged,
                columns = settings.homeGridColumns,
                topContentPadding = recommendationTopPadding,
                bottomContentPadding = recommendationBottomPadding,
                chromeVisible = !immersiveMode,
                onBackgroundClick = {
                  if (immersiveMode) onRecommendationModeChanged(HomeRecommendationMode.NORMAL)
                },
                initialFocusRequester = controlContentFocusRequesters[page],
                controlNavigationEnabled =
                  controlMode && controlLevel == HomeControlLevel.CONTENT,
              )
            }
          HomeHubTab.DYNAMIC ->
            HomeDynamicScreen(
              state = dynamicState,
              accountMid = userInfo.mid,
              settings = settings,
              onSelectUploader = dynamicViewModel::selectUploader,
              onVideoOnlyChange = dynamicViewModel::setVideoOnly,
              onSelectDynamic = dynamicViewModel::selectDynamic,
              onRefresh = dynamicViewModel::refresh,
              onLoadMore = dynamicViewModel::loadMore,
              onLoadMoreUploaders = dynamicViewModel::loadMoreUploaders,
              onLike = dynamicViewModel::toggleLike,
              onVideoClick = onDynamicItemClick,
              onVideoLongClick = onRecommendationItemLongClick,
              onLiveClick = onDynamicLiveClick,
              hiddenDynamicId = hiddenDynamicCoverItemId,
              hiddenLiveCoverItemId = hiddenDynamicLiveCoverItemId,
              onVideoBoundsChanged = onDynamicItemBoundsChanged,
              onLiveBoundsChanged = onDynamicLiveBoundsChanged,
              onArticleClick = onDynamicArticleClick,
              hiddenArticleItemId = hiddenDynamicArticleItemId,
              onArticleBoundsChanged = onDynamicArticleBoundsChanged,
              onCommentProfileClick = onDynamicCommentProfileClick,
              onAvatarProfileClick = onDynamicAvatarProfileClick,
              hiddenCommentAvatarRpid = hiddenDynamicCommentAvatarRpid,
              hiddenAvatarSourceBounds = hiddenDynamicAvatarSourceBounds,
              backHandlingEnabled = homeContentBackgroundWorkAllowed,
              backdropCaptureEnabled = homeContentBackgroundWorkAllowed,
              backdropLayer = dynamicBackdropLayer,
              onBackdropBoundsChanged = { dynamicBackdropBounds = it },
              underlayLayer = backgroundBackdropLayer,
              underlayBounds = backgroundBackdropBounds,
              topContentPadding = headerClearance,
              onDetailOverlayActiveChanged = { active ->
                dynamicOverlayActive = active
                dynamicViewModel.setDetailOverlayActive(active)
              },
              pageControlsEnabled =
                controlMode &&
                  controlLevel == HomeControlLevel.PAGE_CONTROLS &&
                  pagerState.currentPage == HomeHubTab.DYNAMIC.ordinal,
              contentControlsEnabled =
                controlMode &&
                  controlLevel == HomeControlLevel.CONTENT &&
                  pagerState.currentPage == HomeHubTab.DYNAMIC.ordinal,
              onControlEnterContent = {
                onControlLevelChanged(HomeControlLevel.CONTENT)
                // 从左栏进入时，焦点目标必须继续指向信息流首项，不能让子页回退到“仅视频”。
                pendingControlContentTab = HomeHubTab.DYNAMIC
              },
              onControlFocusFeed = {
                pendingControlContentTab = HomeHubTab.DYNAMIC
              },
              initialFocusRequester = controlContentFocusRequesters[page],
            )
          HomeHubTab.POPULAR ->
            PopularFeedScreen(
              state = popularState,
              onSection = popularViewModel::selectSection,
              onWeeklyPeriod = popularViewModel::selectWeeklyPeriod,
              onRankCategory = popularViewModel::selectRankCategory,
              onMusicPeriod = popularViewModel::selectMusicPeriod,
              onRefresh = popularViewModel::refresh,
              onLoadNextPage = popularViewModel::loadNextPage,
              onItemClick = onPopularItemClick,
              onItemLongClick = onRecommendationItemLongClick,
              onConsumeRefreshMessage = popularViewModel::consumeRefreshMessage,
              onHorizontalRailInteractionChanged = { horizontalRailTouched = it },
              gridState = popularGridState,
              hiddenCoverItemId = hiddenPopularCoverItemId,
              backgroundWorkAllowed =
                homeContentBackgroundWorkAllowed &&
                  pagerState.currentPage == HomeHubTab.POPULAR.ordinal,
              backdropLayer = popularBackdropLayer,
              onBackdropBoundsChanged = { popularBackdropBounds = it },
              underlayLayer = backgroundBackdropLayer,
              underlayBounds = backgroundBackdropBounds,
              topContentPadding = headerClearance,
              pageControlsEnabled =
                controlMode &&
                  controlLevel == HomeControlLevel.PAGE_CONTROLS &&
                  pagerState.currentPage == HomeHubTab.POPULAR.ordinal,
              onControlEnterContent = {
                onControlLevelChanged(HomeControlLevel.CONTENT)
                pendingControlContentTab = HomeHubTab.POPULAR
              },
              onControlReturnToPageControls = {
                onControlLevelChanged(HomeControlLevel.PAGE_CONTROLS)
                pendingControlContentTab = null
              },
              initialFocusRequester = controlContentFocusRequesters[page],
            )
          HomeHubTab.LIVE ->
            CapturedHomePageContent(
              layer = liveBackdropLayer,
              captureEnabled = homeContentBackgroundWorkAllowed,
              onBoundsChanged = { liveBackdropBounds = it },
            ) {
              CompositionLocalProvider(
                LocalNavigationTopClearance provides (headerClearance + 10.dp)
              ) {
                LiveHomeScreen(
                  state = liveState,
                  onVisible = liveViewModel::ensureLoaded,
                  onRefresh = liveViewModel::refresh,
                  onLoadNextPage = liveViewModel::loadNextPage,
                  onAreaSelected = liveViewModel::selectArea,
                  onAreaIndex = onLiveAreaIndex,
                  liveAreaIndexFocusRestoreRequest = liveAreaIndexFocusRestoreRequest,
                  onHeroRoomSelected = liveViewModel::selectHeroRoom,
                  onRoomClick = onLiveRoomClick,
                  onRoomBoundsChanged = onLiveRoomBoundsChanged,
                  onTransitionActiveChanged = onLiveTransitionActiveChanged,
                  onConsumeRefreshMessage = liveViewModel::consumeRefreshMessage,
                  onHorizontalRailInteractionChanged = { horizontalRailTouched = it },
                  gridState = liveGridState,
                  hiddenCoverItemId = hiddenLiveCoverItemId,
                  backgroundWorkAllowed =
                    homeContentBackgroundWorkAllowed &&
                      pagerState.currentPage == HomeHubTab.LIVE.ordinal,
                  detailActive = liveDetailActive,
                  topContentPadding = headerClearance + 10.dp,
                  initialFocusRequester = controlContentFocusRequesters[page],
                )
              }
            }
          }
        }
      }

      AnimatedVisibility(
        visible = !immersiveMode,
        modifier = Modifier.align(Alignment.TopCenter).zIndex(3f),
        enter =
          slideInVertically(tween(if (settings.reduceMotion) 90 else 220)) { -it } +
            fadeIn(tween(if (settings.reduceMotion) 90 else 180)),
        exit =
          slideOutVertically(tween(if (settings.reduceMotion) 90 else 220)) { -it } +
            fadeOut(tween(if (settings.reduceMotion) 90 else 180)),
      ) {
        HomeHubHeader(
          userInfo = userInfo,
          onLoginClick = onRecommendationLoginClick,
          onSearch = { onSearch(controlInputFromController) },
          searchQuery = searchQuery,
          onSearchBoundsChanged = onSearchBoundsChanged,
          refreshing =
            when (activeTab) {
              HomeHubTab.RECOMMENDATION -> feedState.isRefreshing
              HomeHubTab.DYNAMIC -> dynamicState.loading
              HomeHubTab.POPULAR -> popularState.isRefreshing
              HomeHubTab.LIVE -> liveState.isRefreshing
            },
          backdropLayer = activeBackdropLayer,
          backdropBounds = activeBackdropBounds,
          underlayLayer = backgroundBackdropLayer,
          underlayBounds = backgroundBackdropBounds,
          controlSecondLevelEnabled = controlLevel == HomeControlLevel.TABS,
          controlAccountFocusRequester = controlAccountFocusRequester,
          controlSearchFocusRequester = controlSearchFocusRequester,
          controlFirstTabFocusRequester = controlTabFocusRequesters.first(),
          controlLastTabFocusRequester = controlTabFocusRequesters.last(),
          onControlAccountFocused = {
            if (controlInputFromController) {
              lastSecondLevelFocusRegion = HomeSecondLevelFocusRegion.ACCOUNT
              lastControlAction = null
            }
          },
          onControlSearchFocused = {
            if (controlInputFromController) {
              lastSecondLevelFocusRegion = HomeSecondLevelFocusRegion.SEARCH
              lastControlAction = null
            }
          },
          modifier = Modifier.fillMaxWidth().onSizeChanged { headerHeightPx = it.height },
        ) {
          HomeHubTabCapsule(
            pagerState = pagerState,
            clickEnabled = true,
            dragEnabled = !dynamicNavigationLocked,
            controlMode = controlMode,
            controlTabsEnabled = controlLevel == HomeControlLevel.TABS,
            controlFocusRequesters = controlTabFocusRequesters,
            controlStartFocusRequester = controlAccountFocusRequester,
            controlEndFocusRequester = controlSearchFocusRequester,
            controlDownFocusRequester = { tab ->
              if (recommendationSelected) {
                controlActionFocusRequesters.getValue(
                  homeSecondLevelActionBelowTab(tab.ordinal)
                )
              } else null
            },
            onControlFocused = { tab ->
              if (controlInputFromController) {
                lastControlTabIndex = tab.ordinal
                lastControlAction = null
                lastSecondLevelFocusRegion = HomeSecondLevelFocusRegion.TAB
              }
            },
            onTab = { tab ->
              controlTabNavigationJob?.cancel()
              controlTabNavigationJob =
                scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
            },
            onControlTab = { tab ->
              controlTabNavigationJob?.cancel()
              controlInputFromController = true
              controlSelectedTabIndex = tab.ordinal
              controlTabNavigationJob = scope.launch {
                pagerState.animateScrollToPage(tab.ordinal)
                val entryLevel = homeTabEntryLevel(tab)
                onControlLevelChanged(entryLevel)
                if (entryLevel == HomeControlLevel.CONTENT) {
                  pendingControlContentTab = tab
                } else {
                  pendingControlContentTab = null
                }
              }
            },
            onDragPosition = { position ->
              val clamped = position.coerceIn(0f, HomeHubTab.entries.lastIndex.toFloat())
              val lower = floor(clamped).toInt()
              if (lower >= HomeHubTab.entries.lastIndex) {
                pagerState.requestScrollToPage(HomeHubTab.entries.lastIndex)
              } else {
                val fraction = clamped - lower
                if (fraction <= .5f) pagerState.requestScrollToPage(lower, fraction)
                else pagerState.requestScrollToPage(lower + 1, fraction - 1f)
              }
            },
            modifier =
              Modifier.align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = 4.dp)
                .height(32.dp)
                .zIndex(1f),
          )
        }
      }

      AnimatedVisibility(
        visible = recommendationSelected && recommendationMode == HomeRecommendationMode.NORMAL,
        modifier =
          Modifier.align(Alignment.BottomStart)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(start = 20.dp, bottom = 18.dp)
            .zIndex(4f),
        enter =
          slideInHorizontally(tween(if (settings.reduceMotion) 90 else 220)) { -it * 2 } +
            fadeIn(tween(if (settings.reduceMotion) 90 else 180)),
        exit =
          slideOutHorizontally(tween(if (settings.reduceMotion) 90 else 220)) { -it * 2 } +
            fadeOut(tween(if (settings.reduceMotion) 90 else 180)),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          HomeGlassIconButton(
            contentDescription = "进入沉浸模式",
            backdropLayer = recommendationBackdropLayer,
            backdropBounds = recommendationBackdropBounds,
            underlayLayer = backgroundBackdropLayer,
            underlayBounds = backgroundBackdropBounds,
            controlFocusRequester =
              controlActionFocusRequesters.getValue(HomeSecondLevelAction.IMMERSIVE),
            controlFocusEnabled = controlLevel == HomeControlLevel.TABS,
            controlRightFocusRequester =
              controlActionFocusRequesters.getValue(HomeSecondLevelAction.SCROLL_TOP),
            controlUpFocusRequester = controlTabFocusRequesters[lastControlTabIndex],
            controlDownFocusRequester =
              controlActionFocusRequesters.getValue(HomeSecondLevelAction.MUSIC),
            onControlFocused = {
              if (controlInputFromController) {
                lastControlAction = HomeSecondLevelAction.IMMERSIVE
                lastSecondLevelFocusRegion = HomeSecondLevelFocusRegion.ACTION
              }
            },
            onClick = {
              if (feedState is FeedUiState.Content) {
                onRecommendationModeChanged(HomeRecommendationMode.IMMERSIVE)
              }
            },
          ) {
            ImmersiveFeedIcon()
          }
          HomeGlassIconButton(
            contentDescription = "打开音乐播放器",
            backdropLayer = recommendationBackdropLayer,
            backdropBounds = recommendationBackdropBounds,
            underlayLayer = backgroundBackdropLayer,
            underlayBounds = backgroundBackdropBounds,
            controlFocusRequester =
              controlActionFocusRequesters.getValue(HomeSecondLevelAction.MUSIC),
            controlFocusEnabled = controlLevel == HomeControlLevel.TABS,
            controlRightFocusRequester =
              controlActionFocusRequesters.getValue(HomeSecondLevelAction.REFRESH),
            controlUpFocusRequester =
              controlActionFocusRequesters.getValue(HomeSecondLevelAction.IMMERSIVE),
            onControlFocused = {
              if (controlInputFromController) {
                lastControlAction = HomeSecondLevelAction.MUSIC
                lastSecondLevelFocusRegion = HomeSecondLevelFocusRegion.ACTION
              }
            },
            onClick = {
              if (!musicLaunchPending) {
                musicLaunchPending = true
                musicEntryInputLocked = true
                musicPlayerViewModel.configureVideoQuality(
                  mode = settings.musicPreferredResolutionMode,
                  vipActive = userInfo.vipActive,
                )
                musicPlayerViewModel.prepareOpen(
                  accountMid = userInfo.mid,
                  folderSelectionId = settings.musicFavoriteFolderId,
                  folderSelectionConfigured = settings.musicFavoriteFolderConfigured,
                )
              }
            },
          ) {
            if (musicLaunchPending) {
              CircularProgressIndicator(
                modifier = Modifier.size(21.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
              )
            } else {
              Icon(Icons.Default.MusicNote, contentDescription = null)
            }
          }
        }
      }

      AnimatedVisibility(
        visible = recommendationSelected && recommendationMode == HomeRecommendationMode.NORMAL,
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(end = 20.dp, bottom = 18.dp)
            .zIndex(4f),
        enter =
          slideInHorizontally(tween(if (settings.reduceMotion) 90 else 220)) { it * 2 } +
            fadeIn(tween(if (settings.reduceMotion) 90 else 180)),
        exit =
          slideOutHorizontally(tween(if (settings.reduceMotion) 90 else 220)) { it * 2 } +
            fadeOut(tween(if (settings.reduceMotion) 90 else 180)),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          HomeGlassIconButton(
            contentDescription = "回到顶部",
            backdropLayer = recommendationBackdropLayer,
            backdropBounds = recommendationBackdropBounds,
            underlayLayer = backgroundBackdropLayer,
            underlayBounds = backgroundBackdropBounds,
            controlFocusRequester =
              controlActionFocusRequesters.getValue(HomeSecondLevelAction.SCROLL_TOP),
            controlFocusEnabled = controlLevel == HomeControlLevel.TABS,
            controlLeftFocusRequester =
              controlActionFocusRequesters.getValue(HomeSecondLevelAction.IMMERSIVE),
            controlUpFocusRequester = controlTabFocusRequesters[lastControlTabIndex],
            controlDownFocusRequester =
              controlActionFocusRequesters.getValue(HomeSecondLevelAction.REFRESH),
            onControlFocused = {
              if (controlInputFromController) {
                lastControlAction = HomeSecondLevelAction.SCROLL_TOP
                lastSecondLevelFocusRegion = HomeSecondLevelFocusRegion.ACTION
              }
            },
            onClick = { scope.launch { recommendationGridState.animateScrollToItem(0) } },
          ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
          }
          HomeGlassIconButton(
            contentDescription = "刷新推荐",
            backdropLayer = recommendationBackdropLayer,
            backdropBounds = recommendationBackdropBounds,
            underlayLayer = backgroundBackdropLayer,
            underlayBounds = backgroundBackdropBounds,
            iconTint = MaterialTheme.colorScheme.primary,
            controlFocusRequester =
              controlActionFocusRequesters.getValue(HomeSecondLevelAction.REFRESH),
            controlFocusEnabled = controlLevel == HomeControlLevel.TABS,
            controlLeftFocusRequester =
              controlActionFocusRequesters.getValue(HomeSecondLevelAction.MUSIC),
            controlUpFocusRequester =
              controlActionFocusRequesters.getValue(HomeSecondLevelAction.SCROLL_TOP),
            onControlFocused = {
              if (controlInputFromController) {
                lastControlAction = HomeSecondLevelAction.REFRESH
                lastSecondLevelFocusRegion = HomeSecondLevelFocusRegion.ACTION
              }
            },
            onClick = {
              if (feedState is FeedUiState.Content) {
                scope.launch {
                  recommendationGridState.scrollToItem(0)
                  onRecommendationRefresh()
                }
              } else {
                onRecommendationRefresh()
              }
            },
          ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
          }
        }
      }

      if (recommendationMode == HomeRecommendationMode.MUSIC) {
        HomeMusicPlayerScreen(
          accountMid = userInfo.mid,
          vipActive = userInfo.vipActive,
          settings = settings,
          viewModel = musicPlayerViewModel,
          entryBackdropLayer = recommendationBackdropLayer,
          entryBackdropBounds = recommendationBackdropBounds,
          entryUnderlayLayer = backgroundBackdropLayer,
          entryUnderlayBounds = backgroundBackdropBounds,
          onExitStarted = { musicExitInProgress = true },
          onDismissed = {
            musicExitInProgress = false
            onRecommendationModeChanged(HomeRecommendationMode.NORMAL)
          },
          onEntrySettled = { musicEntryInputLocked = false },
          onLoginClick = onRecommendationLoginClick,
          onFavoriteFolderSelected = onMusicFavoriteFolderSelected,
          modifier = Modifier.fillMaxSize().zIndex(20f),
        )
      }
    }
  }
}

@Composable
private fun HomeGlassIconButton(
  contentDescription: String,
  backdropLayer: GraphicsLayer,
  backdropBounds: Rect,
  underlayLayer: GraphicsLayer,
  underlayBounds: Rect,
  onClick: () -> Unit,
  iconTint: Color = MaterialTheme.colorScheme.onSurface,
  controlFocusRequester: FocusRequester? = null,
  controlFocusEnabled: Boolean = true,
  controlLeftFocusRequester: FocusRequester? = null,
  controlRightFocusRequester: FocusRequester? = null,
  controlUpFocusRequester: FocusRequester? = null,
  controlDownFocusRequester: FocusRequester? = null,
  onControlFocused: () -> Unit = {},
  content: @Composable () -> Unit,
) {
  val controlMode = LocalControlMode.current
  val controlFocusVisible = LocalControlFocusVisible.current
  val interactionSource = remember { MutableInteractionSource() }
  val focused by interactionSource.collectIsFocusedAsState()
  BackdropGlassSurface(
    backdropLayer = backdropLayer,
    backdropBounds = backdropBounds,
    underlayLayer = underlayLayer,
    underlayBounds = underlayBounds,
    modifier =
      Modifier.size(48.dp)
        .then(
          if (controlMode) {
            Modifier.then(
                if (controlFocusRequester != null) {
                  Modifier.focusRequester(controlFocusRequester)
                } else Modifier
              )
              .focusProperties {
                canFocus = controlFocusEnabled
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
              }
              .onFocusChanged { if (it.isFocused) onControlFocused() }
              .onPreviewKeyEvent { event ->
                if (!controlFocusEnabled) return@onPreviewKeyEvent false
                val target =
                  when (homeControlDirection(event.nativeKeyEvent.keyCode)) {
                    HomeControlDirection.LEFT -> controlLeftFocusRequester
                    HomeControlDirection.RIGHT -> controlRightFocusRequester
                    HomeControlDirection.UP -> controlUpFocusRequester
                    HomeControlDirection.DOWN -> controlDownFocusRequester
                    null -> return@onPreviewKeyEvent false
                  }
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                  target?.let { runCatching { it.requestFocus() } }
                }
                true
              }
          } else Modifier
        )
        .clip(CircleShape)
        .semantics {
          this.contentDescription = contentDescription
          role = Role.Button
        }
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
        )
        .testTag(contentDescription),
    shape = CircleShape,
    blurRadius = 14.dp,
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .48f),
    border =
      BorderStroke(
        if (focused && controlFocusVisible) 2.dp else .75.dp,
        if (focused && controlFocusVisible) MaterialTheme.colorScheme.primary
        else Color.White.copy(alpha = .20f),
      ),
  ) {
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides iconTint) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
  }
}

@Composable
private fun ImmersiveFeedIcon() {
  val color = androidx.compose.material3.LocalContentColor.current
  Canvas(Modifier.size(25.dp)) {
    val stroke = 1.8.dp.toPx()
    val inset = 2.5.dp.toPx()
    val arm = 6.dp.toPx()
    val cardLeft = size.width * .28f
    val cardTop = size.height * .35f
    val cardRight = size.width * .72f
    val cardBottom = size.height * .65f
    drawRoundRect(
      color = color,
      topLeft = Offset(cardLeft, cardTop),
      size = Size(cardRight - cardLeft, cardBottom - cardTop),
      cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
      style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
    )
    listOf(
        Offset(inset, inset) to Offset(inset + arm, inset),
        Offset(inset, inset) to Offset(inset, inset + arm),
        Offset(size.width - inset, inset) to Offset(size.width - inset - arm, inset),
        Offset(size.width - inset, inset) to Offset(size.width - inset, inset + arm),
        Offset(inset, size.height - inset) to Offset(inset + arm, size.height - inset),
        Offset(inset, size.height - inset) to Offset(inset, size.height - inset - arm),
        Offset(size.width - inset, size.height - inset) to
          Offset(size.width - inset - arm, size.height - inset),
        Offset(size.width - inset, size.height - inset) to
          Offset(size.width - inset, size.height - inset - arm),
      )
      .forEach { (start, end) ->
        drawLine(color = color, start = start, end = end, strokeWidth = stroke)
      }
  }
}

@Composable
private fun CapturedHomePageContent(
  layer: GraphicsLayer,
  captureEnabled: Boolean,
  onBoundsChanged: (Rect) -> Unit,
  content: @Composable () -> Unit,
) {
  Box(
    Modifier.fillMaxSize()
      .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
      .drawWithContent {
        if (captureEnabled) {
          layer.record { this@drawWithContent.drawContent() }
          drawLayer(layer)
        } else {
          drawContent()
        }
      }
  ) {
    content()
  }
}

@Composable
private fun HomeHubHeader(
  userInfo: UserInfo,
  onLoginClick: (Rect) -> Unit,
  onSearch: () -> Unit,
  searchQuery: String,
  onSearchBoundsChanged: (Rect) -> Unit,
  refreshing: Boolean,
  backdropLayer: GraphicsLayer,
  backdropBounds: Rect,
  underlayLayer: GraphicsLayer,
  underlayBounds: Rect,
  controlSecondLevelEnabled: Boolean,
  controlAccountFocusRequester: FocusRequester,
  controlSearchFocusRequester: FocusRequester,
  controlFirstTabFocusRequester: FocusRequester,
  controlLastTabFocusRequester: FocusRequester,
  onControlAccountFocused: () -> Unit,
  onControlSearchFocused: () -> Unit,
  modifier: Modifier = Modifier,
  navigationContent: @Composable BoxScope.() -> Unit,
) {
  val darkTheme = MaterialTheme.colorScheme.background.luminance() < .5f
  val controlMode = LocalControlMode.current
  val searchInteractionSource = remember { MutableInteractionSource() }
  val borderAlpha =
    if (darkTheme) HomeGlassTokens.DarkBorderAlpha else HomeGlassTokens.LightBorderAlpha
  BackdropGlassSurface(
    backdropLayer = backdropLayer,
    backdropBounds = backdropBounds,
    underlayLayer = underlayLayer,
    underlayBounds = underlayBounds,
    modifier = modifier,
    shape = RectangleShape,
    blurRadius = HomeGlassTokens.BlurRadius,
    containerColor =
      MaterialTheme.colorScheme.surface.copy(
        alpha =
          if (darkTheme) HomeGlassTokens.DarkContainerAlpha else HomeGlassTokens.LightContainerAlpha
      ),
    border =
      BorderStroke(.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha)),
    shadowElevation = 0.dp,
  ) {
    Box(Modifier.fillMaxWidth()) {
      Column {
        RootAccountHeader(
          user = userInfo,
          onClick = onLoginClick,
          containerColor = Color.Transparent,
          showUid = false,
          focusEnabled = !controlMode || controlSecondLevelEnabled,
          clickIndicationEnabled = controlMode,
          identityModifier =
            if (controlMode) {
              Modifier.focusRequester(controlAccountFocusRequester)
                .focusProperties {
                  canFocus = controlSecondLevelEnabled
                  left = FocusRequester.Cancel
                  right = controlFirstTabFocusRequester
                  up = FocusRequester.Cancel
                  down = controlFirstTabFocusRequester
                }
                .onFocusChanged {
                  if (it.isFocused) onControlAccountFocused()
                }
                .controlFocusOutline(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.primary,
                  enabled = controlSecondLevelEnabled,
                )
            } else Modifier,
          nameStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        ) {
          Spacer(Modifier.width(12.dp))
          DeviceStatusCluster()
          Spacer(Modifier.weight(1f))
          Surface(
            modifier =
              Modifier.width(330.dp)
                .height(44.dp)
                .padding(end = 12.dp)
                .onGloballyPositioned { onSearchBoundsChanged(it.boundsInRoot()) }
                .then(
                  if (controlMode) {
                    Modifier.focusRequester(controlSearchFocusRequester)
                      .focusProperties {
                        canFocus = controlSecondLevelEnabled
                        left = controlLastTabFocusRequester
                        right = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = controlLastTabFocusRequester
                      }
                      .onFocusChanged { if (it.isFocused) onControlSearchFocused() }
                      .controlFocusOutline(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        enabled = controlSecondLevelEnabled,
                      )
                  } else Modifier
                )
                .clickable(
                  interactionSource = searchInteractionSource,
                  indication = null,
                  onClick = onSearch,
                )
                .testTag("feed_search_button"),
            shape = CircleShape,
            color =
              MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha =
                  if (darkTheme) HomeGlassTokens.DarkControlAlpha
                  else HomeGlassTokens.LightControlAlpha
              ),
          ) {
            Row(
              Modifier.fillMaxSize().padding(horizontal = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp))
              Text(
                text = searchQuery.ifBlank { "搜索视频" },
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                color =
                  if (searchQuery.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                  else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (searchQuery.isNotBlank()) {
                Icon(
                  Icons.Default.Close,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
        if (refreshing) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("feed_progress"))
        }
      }
      navigationContent()
    }
  }
}

@Composable
private fun HomeHubTabCapsule(
  pagerState: PagerState,
  clickEnabled: Boolean,
  dragEnabled: Boolean,
  onTab: (HomeHubTab) -> Unit,
  onControlTab: (HomeHubTab) -> Unit,
  onDragPosition: (Float) -> Unit,
  controlMode: Boolean,
  controlTabsEnabled: Boolean,
  controlFocusRequesters: List<FocusRequester>,
  controlStartFocusRequester: FocusRequester,
  controlEndFocusRequester: FocusRequester,
  controlDownFocusRequester: (HomeHubTab) -> FocusRequester?,
  onControlFocused: (HomeHubTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  val controlFocusVisible = LocalControlFocusVisible.current
  var dragPosition by remember { mutableStateOf<Float?>(null) }
  var selectionTravelPx by remember { mutableFloatStateOf(1f) }
  LaunchedEffect(dragEnabled) {
    if (!dragEnabled) dragPosition = null
  }
  val shape = androidx.compose.foundation.shape.CircleShape
  val selectionPosition =
    (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(
      0f,
      HomeHubTab.entries.lastIndex.toFloat(),
    )
  fun settleDrag() {
    val selected =
      (dragPosition ?: selectionPosition)
        .coerceIn(0f, HomeHubTab.entries.lastIndex.toFloat())
        .roundToInt()
    dragPosition = null
    onTab(HomeHubTab.entries[selected])
  }
  BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
    val capsuleWidth = (maxWidth * .24f).coerceIn(156.dp, 320.dp)
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < .5f
    Surface(
      modifier = Modifier.width(capsuleWidth),
      shape = shape,
      color =
        MaterialTheme.colorScheme.surface.copy(
          alpha =
            if (darkTheme) HomeGlassTokens.DarkControlAlpha else HomeGlassTokens.LightControlAlpha
        ),
      border =
        androidx.compose.foundation.BorderStroke(
          .75.dp,
          MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f),
        ),
      shadowElevation = 0.dp,
    ) {
      val selectionPillColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)
      Box(
        Modifier.padding(4.dp)
          .height(24.dp)
          .fillMaxWidth()
          .onSizeChanged {
            selectionTravelPx = (it.width / HomeHubTab.entries.size.toFloat()).coerceAtLeast(1f)
          }
          .pointerInput(selectionTravelPx, dragEnabled) {
            if (dragEnabled) {
              detectHorizontalDragGestures(
                onDragStart = { dragPosition = selectionPosition },
                onHorizontalDrag = { change, dragAmount ->
                  change.consume()
                  val updated =
                    ((dragPosition ?: selectionPosition) + dragAmount / selectionTravelPx).coerceIn(
                      0f,
                      HomeHubTab.entries.lastIndex.toFloat(),
                    )
                  dragPosition = updated
                  onDragPosition(updated)
                },
                onDragEnd = ::settleDrag,
                onDragCancel = ::settleDrag,
              )
            }
          }
      ) {
        val currentPosition = dragPosition ?: selectionPosition
        Canvas(Modifier.fillMaxSize()) {
          val pillWidth = size.width / HomeHubTab.entries.size
          drawRoundRect(
            color = selectionPillColor,
            topLeft = Offset(pillWidth * currentPosition, 0f),
            size = Size(pillWidth, size.height),
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
          )
        }
        androidx.compose.foundation.layout.Row(
          Modifier.fillMaxSize(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
          HomeHubTab.entries.forEach { tab ->
            val selected = tab.ordinal == currentPosition.roundToInt()
            val interactionSource = remember(tab) { MutableInteractionSource() }
            val focused by interactionSource.collectIsFocusedAsState()
            Box(
              Modifier.weight(1f)
                .fillMaxSize()
                .then(
                  if (controlMode) {
                    Modifier.focusRequester(controlFocusRequesters[tab.ordinal])
                      .focusProperties {
                        canFocus = controlTabsEnabled
                        left =
                          controlFocusRequesters.getOrNull(tab.ordinal - 1)
                            ?: controlStartFocusRequester
                        right =
                          controlFocusRequesters.getOrNull(tab.ordinal + 1)
                            ?: controlEndFocusRequester
                        up = FocusRequester.Cancel
                        down = controlDownFocusRequester(tab) ?: FocusRequester.Cancel
                      }
                      .onFocusChanged { if (it.isFocused) onControlFocused(tab) }
                      .onPreviewKeyEvent { event ->
                        if (!controlTabsEnabled) return@onPreviewKeyEvent false
                        if (isControlConfirmKey(event.nativeKeyEvent.keyCode)) {
                          if (event.type == KeyEventType.KeyUp) onControlTab(tab)
                          return@onPreviewKeyEvent true
                        }
                        false
                      }
                  } else Modifier
                )
                .clip(shape)
                .background(
                  if (focused && controlFocusVisible)
                    MaterialTheme.colorScheme.primary.copy(alpha = .16f)
                  else Color.Transparent
                )
                .border(
                  width = if (focused && controlFocusVisible) 2.dp else 0.dp,
                  color =
                    if (focused && controlFocusVisible) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                  shape = shape,
                )
                .clickable(
                  enabled = clickEnabled,
                  interactionSource = interactionSource,
                  indication = null,
                ) {
                  onTab(tab)
                },
              contentAlignment = Alignment.Center,
            ) {
              androidx.compose.material3.Text(
                tab.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color =
                  if (selected) MaterialTheme.colorScheme.onSurface
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

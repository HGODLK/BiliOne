package dev.openbili.webdemo.ui

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import dev.openbili.webdemo.feed.LocalCoverImageLoadingEnabled
import dev.openbili.webdemo.feed.FeedScreen
import dev.openbili.webdemo.feed.FeedScrollAnchor
import dev.openbili.webdemo.feed.FeedUiState
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class HomeHubTab(val label: String) {
  RECOMMENDATION("推荐"),
  DYNAMIC("动态"),
  POPULAR("热门"),
  LIVE("直播"),
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

/**
 * Home's secondary pager. The root pager remains responsible for HOME/BANGUMI/MY; this component
 * owns only the four content modes inside HOME.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeHubScreen(
  feedState: FeedUiState,
  userInfo: UserInfo,
  onRecommendationRefresh: () -> Unit,
  onRecommendationLoadNextPage: () -> Unit,
  onRecommendationItemClick: (FeedItem, Rect, FeedScrollAnchor) -> Unit,
  onPopularItemClick: (FeedItem, Rect, FeedScrollAnchor) -> Unit,
  onDynamicItemClick: (SpaceDynamicItem, FeedItem, Rect) -> Unit,
  onDynamicItemBoundsChanged: (String, Rect) -> Unit,
  onDynamicArticleClick: (ArticleItem, Rect) -> Unit,
  onDynamicArticleBoundsChanged: (ArticleItem, Rect) -> Unit,
  onDynamicCommentProfileClick: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onDynamicAvatarProfileClick: (Long, String?, String?, Rect) -> Unit,
  onRecommendationItemLongClick: (FeedItem) -> Unit,
  onRecommendationProfileClick: (FeedItem, Rect) -> Unit,
  onRecommendationLoginClick: (Rect) -> Unit,
  onSearch: () -> Unit,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onSearchSubmit: (String) -> Unit,
  onConsumeRecommendationRefreshMessage: () -> Unit,
  onSearchBoundsChanged: (Rect) -> Unit,
  coverPrefetchCount: Int,
  backgroundWorkAllowed: Boolean,
  recommendationGridState: LazyGridState,
  hiddenRecommendationCoverItemId: String?,
  hiddenPopularCoverItemId: String?,
  hiddenDynamicCoverItemId: String?,
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
  onMusicFavoriteFolderSelected: (Long) -> Unit,
  onMusicEntryInputLockChanged: (Boolean) -> Unit,
  settings: AppSettings,
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
  val pagerState = rememberPagerState(pageCount = { HomeHubTab.entries.size })
  val popularGridState = rememberLazyGridState()
  val liveGridState = rememberLazyGridState()
  val scope = rememberCoroutineScope()
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
  val stagedMusicBackgroundSource =
    when {
      !musicLaunchPending -> ""
      settings.useHomeBackgroundForMusic && settings.homeBackgroundUri.isNotBlank() ->
        settings.homeBackgroundUri
      else -> musicPlayerState.currentItem?.coverUrl.orEmpty()
    }
  val stagedMusicBackgroundModel =
    if (stagedMusicBackgroundSource.isNotBlank()) {
      rememberStaticBackgroundModel(source = stagedMusicBackgroundSource, blurred = true)
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
  // The music page must not accept touches while it is still loading and sliding in. The lock is
  // released by onEntrySettled once the page has fully entered, and by this timeout as a hard cap
  // so a stalled load can never keep the screen locked.
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
      targetValue = if (immersiveMode) 12.dp else headerClearance + 12.dp,
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
      rootPageVisible &&
        backgroundWorkAllowed &&
        recommendationSelected &&
        recommendationMode == HomeRecommendationMode.IMMERSIVE
  ) {
    onRecommendationModeChanged(HomeRecommendationMode.NORMAL)
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
    LocalCoverImageLoadingEnabled provides homeContentBackgroundWorkAllowed,
  ) {
    Box(Modifier.fillMaxSize()) {
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
      // The secondary navigation intentionally floats over the feed rather than reserving a
      // toolbar-sized strip. Keeping the pager edge-to-edge also leaves the notch-safe offset
      // to the capsule itself instead of baking it into every child page.
      modifier = Modifier.fillMaxSize(),
      beyondViewportPageCount = 1,
      // Period/category rails own a horizontal drag from its first down event. This prevents a
      // long history swipe from also moving the recommendation/popular/live pager.
      userScrollEnabled =
        !horizontalRailTouched &&
          !dynamicNavigationLocked &&
          recommendationMode == HomeRecommendationMode.NORMAL,
      key = { HomeHubTab.entries[it] },
    ) { page ->
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
            hiddenDynamicId = hiddenDynamicCoverItemId,
            onVideoBoundsChanged = onDynamicItemBoundsChanged,
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
              )
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
        onSearch = onSearch,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchSubmit = onSearchSubmit,
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
        modifier = Modifier.fillMaxWidth().onSizeChanged { headerHeightPx = it.height },
      ) {
        HomeHubTabCapsule(
          pagerState = pagerState,
          clickEnabled = true,
          dragEnabled = !dynamicNavigationLocked,
          onTab = { tab ->
            scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
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
  content: @Composable () -> Unit,
) {
  BackdropGlassSurface(
    backdropLayer = backdropLayer,
    backdropBounds = backdropBounds,
    underlayLayer = underlayLayer,
    underlayBounds = underlayBounds,
    modifier =
      Modifier.size(48.dp)
        .clip(CircleShape)
        .semantics {
          this.contentDescription = contentDescription
          role = Role.Button
        }
        .clickable(onClick = onClick)
        .testTag(contentDescription),
    shape = CircleShape,
    blurRadius = 14.dp,
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .48f),
    border = BorderStroke(.75.dp, Color.White.copy(alpha = .20f)),
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
  onSearchQueryChange: (String) -> Unit,
  onSearchSubmit: (String) -> Unit,
  onSearchBoundsChanged: (Rect) -> Unit,
  refreshing: Boolean,
  backdropLayer: GraphicsLayer,
  backdropBounds: Rect,
  underlayLayer: GraphicsLayer,
  underlayBounds: Rect,
  modifier: Modifier = Modifier,
  navigationContent: @Composable BoxScope.() -> Unit,
) {
  val darkTheme = MaterialTheme.colorScheme.background.luminance() < .5f
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
                .testTag("feed_search_button"),
            shape = CircleShape,
            color =
              MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha =
                  if (darkTheme) HomeGlassTokens.DarkControlAlpha
                  else HomeGlassTokens.LightControlAlpha
              ),
          ) {
            BasicTextField(
              value = searchQuery,
              onValueChange = {
                onSearchQueryChange(it)
                onSearch()
              },
              modifier = Modifier.fillMaxSize().onFocusChanged { if (it.isFocused) onSearch() },
              singleLine = true,
              textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                  color = MaterialTheme.colorScheme.onSurface
                ),
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
              keyboardActions = KeyboardActions(onSearch = { onSearchSubmit(searchQuery) }),
              decorationBox = { innerField ->
                Row(
                  Modifier.fillMaxSize().padding(horizontal = 14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp))
                  Box(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (searchQuery.isBlank()) {
                      Text("搜索视频", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    innerField()
                  }
                  if (searchQuery.isNotBlank()) {
                    IconButton(
                      onClick = {
                        onSearchQueryChange("")
                        onSearch()
                      },
                      modifier = Modifier.size(32.dp),
                    ) {
                      Icon(
                        Icons.Default.Close,
                        contentDescription = "清空搜索内容",
                        modifier = Modifier.size(18.dp),
                      )
                    }
                  }
                }
              },
            )
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
  onDragPosition: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
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
            Box(
              Modifier.weight(1f).fillMaxSize().clip(shape).clickable(enabled = clickEnabled) {
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

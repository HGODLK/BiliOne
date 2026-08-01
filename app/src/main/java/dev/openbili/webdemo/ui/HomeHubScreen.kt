package dev.openbili.webdemo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedScreen
import dev.openbili.webdemo.feed.FeedScrollAnchor
import dev.openbili.webdemo.feed.FeedUiState
import dev.openbili.webdemo.feed.PopularFeedScreen
import dev.openbili.webdemo.feed.PopularFeedViewModel
import dev.openbili.webdemo.live.LiveHomeScreen
import dev.openbili.webdemo.live.LiveHomeSourceAnchor
import dev.openbili.webdemo.live.LiveHomeViewModel
import dev.openbili.webdemo.live.LiveSearchRoom
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class HomeHubTab(val label: String) {
  RECOMMENDATION("推荐"),
  POPULAR("热门"),
  LIVE("直播"),
}

/**
 * Home's secondary pager. The root pager remains responsible for HOME/BANGUMI/MY; this component
 * owns only the three content modes inside HOME.
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
  dismissedRecommendationItemIds: Set<String>,
  onRestoreDismissedRecommendationItem: (FeedItem) -> Unit,
  onRecommendationItemBoundsChanged: (FeedItem, Rect) -> Unit,
  onLiveRoomClick: (LiveSearchRoom, LiveHomeSourceAnchor, Rect) -> Unit,
  onLiveRoomBoundsChanged: (LiveHomeSourceAnchor, Rect) -> Unit,
  onLiveTransitionActiveChanged: (Boolean) -> Unit,
  hiddenLiveCoverItemId: String?,
  liveDetailActive: Boolean,
) {
  val popularViewModel: PopularFeedViewModel = viewModel()
  val popularState by popularViewModel.state.collectAsState()
  val liveViewModel: LiveHomeViewModel = viewModel()
  val liveState by liveViewModel.state.collectAsState()
  val pagerState = rememberPagerState(pageCount = { HomeHubTab.entries.size })
  val popularGridState = rememberLazyGridState()
  val liveGridState = rememberLazyGridState()
  val scope = rememberCoroutineScope()
  var horizontalRailTouched by remember { mutableStateOf(false) }
  Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      HomeHubHeader(
        userInfo = userInfo,
        onLoginClick = onRecommendationLoginClick,
        onSearch = onSearch,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchSubmit = onSearchSubmit,
        onSearchBoundsChanged = onSearchBoundsChanged,
        refreshing =
          when (HomeHubTab.entries[pagerState.currentPage]) {
            HomeHubTab.RECOMMENDATION -> feedState.isRefreshing
            HomeHubTab.POPULAR -> popularState.isRefreshing
            HomeHubTab.LIVE -> liveState.isRefreshing
          },
      )
      HorizontalPager(
        state = pagerState,
        // The secondary navigation intentionally floats over the feed rather than reserving a
        // toolbar-sized strip. Keeping the pager edge-to-edge also leaves the notch-safe offset
        // to the capsule itself instead of baking it into every child page.
        modifier = Modifier.weight(1f),
        beyondViewportPageCount = 1,
        // Period/category rails own a horizontal drag from its first down event. This prevents a
        // long history swipe from also moving the recommendation/popular/live pager.
        userScrollEnabled = !horizontalRailTouched,
        key = { HomeHubTab.entries[it] },
      ) { page ->
        when (HomeHubTab.entries[page]) {
          HomeHubTab.RECOMMENDATION ->
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
                backgroundWorkAllowed &&
                  pagerState.currentPage == HomeHubTab.RECOMMENDATION.ordinal,
              gridState = recommendationGridState,
              hiddenCoverItemId = hiddenRecommendationCoverItemId,
              dismissedItemIds = dismissedRecommendationItemIds,
              onRestoreDismissedItem = onRestoreDismissedRecommendationItem,
              onItemBoundsChanged = onRecommendationItemBoundsChanged,
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
                backgroundWorkAllowed && pagerState.currentPage == HomeHubTab.POPULAR.ordinal,
            )
          HomeHubTab.LIVE ->
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
                backgroundWorkAllowed && pagerState.currentPage == HomeHubTab.LIVE.ordinal,
              detailActive = liveDetailActive,
            )
        }
      }
    }

    HomeHubTabCapsule(
      pagerState = pagerState,
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
          .zIndex(2f),
    )
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
) {
  Column {
    RootAccountHeader(
      user = userInfo,
      onClick = onLoginClick,
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
        color = MaterialTheme.colorScheme.surfaceVariant,
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
            MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
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
}

@Composable
private fun HomeHubTabCapsule(
  pagerState: PagerState,
  onTab: (HomeHubTab) -> Unit,
  onDragPosition: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  var dragPosition by remember { mutableStateOf<Float?>(null) }
  var selectionTravelPx by remember { mutableFloatStateOf(1f) }
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
    Surface(
      modifier = Modifier.width(capsuleWidth),
      shape = shape,
      color = MaterialTheme.colorScheme.surface,
      border =
        androidx.compose.foundation.BorderStroke(
          .75.dp,
          MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f),
        ),
      shadowElevation = 8.dp,
    ) {
      val selectionPillColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)
      Box(
        Modifier.padding(4.dp)
          .height(24.dp)
          .fillMaxWidth()
          .onSizeChanged {
            selectionTravelPx = (it.width / HomeHubTab.entries.size.toFloat()).coerceAtLeast(1f)
          }
          .pointerInput(selectionTravelPx) {
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
              Modifier.weight(1f).fillMaxSize().clip(shape).clickable { onTab(tab) },
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

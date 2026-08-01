package dev.openbili.webdemo.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.PopularPeriod
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.VideoCardReveal
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.ui.navigationBringIntoViewTarget
import dev.openbili.webdemo.ui.rememberNavigationBringIntoViewRequester
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun PopularFeedScreen(
  state: PopularFeedUiState,
  onSection: (PopularSection) -> Unit,
  onWeeklyPeriod: (PopularPeriod) -> Unit,
  onRankCategory: (PopularRankCategory) -> Unit,
  onMusicPeriod: (PopularPeriod) -> Unit,
  onRefresh: () -> Unit,
  onLoadNextPage: () -> Unit,
  onItemClick: (FeedItem, Rect, FeedScrollAnchor) -> Unit,
  onItemLongClick: (FeedItem) -> Unit,
  onConsumeRefreshMessage: () -> Unit,
  onHorizontalRailInteractionChanged: (Boolean) -> Unit = {},
  gridState: LazyGridState = rememberLazyGridState(),
  hiddenCoverItemId: String? = null,
  backgroundWorkAllowed: Boolean = true,
  coverPrefetchCount: Int = FeedPerformanceConfig.coverPrefetchCount,
) {
  val content = state.content
  val context = LocalContext.current
  val prefetchedCovers = remember { mutableSetOf<String>() }
  // Keep the same Fast Path as recommendation. The two-column layout changes only the number of
  // visible keys retained during slow movement; fast flings still pause every new image request.
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(gridState, columns = 2)
  val shouldLoadMore by
    remember(state.section, gridState) {
      derivedStateOf {
        if (state.section != PopularSection.ALL) return@derivedStateOf false
        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = gridState.layoutInfo.totalItemsCount
        total > 0 && last >= total - 6
      }
    }
  LaunchedEffect(shouldLoadMore, content, imageLoadPolicy.mode, backgroundWorkAllowed, state.section) {
    if (
      backgroundWorkAllowed &&
        shouldLoadMore &&
        imageLoadPolicy.mode != FeedImageLoadMode.PAUSED &&
        content is FeedUiState.Content &&
        !content.isLoadingMore
    ) {
      onLoadNextPage()
    }
  }
  val contentItems = (content as? FeedUiState.Content)?.items.orEmpty()
  LaunchedEffect(
    gridState,
    state.section,
    contentItems,
    imageLoadPolicy.mode,
    backgroundWorkAllowed,
    coverPrefetchCount,
  ) {
    snapshotFlow {
        (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to
          gridState.isScrollInProgress
      }
      .distinctUntilChanged()
      .collect { (lastVisible, isScrolling) ->
        if (!backgroundWorkAllowed) return@collect
        if (isScrolling) {
          if (imageLoadPolicy.mode == FeedImageLoadMode.THROTTLED) {
            // Match recommendation: movement below the fast-fling threshold gets a small,
            // serial look-ahead window; a fast fling starts no new work at all.
            contentItems.drop(lastVisible + 1).take(3).forEach { item ->
              val url = item.coverUrl
              if (prefetchedCovers.add(url)) {
                runCatching {
                  context.imageLoader.execute(
                    CoverImageRequestFactory.request(
                      url,
                      ImageRequest.Builder(context),
                      width = FeedPerformanceConfig.coverRequestWidth,
                      height = FeedPerformanceConfig.coverRequestHeight,
                    )
                  )
                }
              }
            }
          }
          return@collect
        }
        // Do not compete with the visible card decodes immediately after a fling settles.
        delay(350)
        if (!backgroundWorkAllowed || gridState.isScrollInProgress) return@collect
        val effectivePrefetchCount =
          if (FeedPerformanceConfig.aggressiveFastPathEnabled) {
            minOf(coverPrefetchCount, FeedPerformanceConfig.coverPrefetchCount)
          } else {
            coverPrefetchCount
          }
        contentItems.drop(lastVisible + 1).take(effectivePrefetchCount).forEach { item ->
          val url = item.coverUrl
          if (prefetchedCovers.add(url)) {
            context.imageLoader.enqueue(
              CoverImageRequestFactory.request(
                url,
                ImageRequest.Builder(context),
                width = FeedPerformanceConfig.coverRequestWidth,
                height = FeedPerformanceConfig.coverRequestHeight,
              )
            )
          }
        }
      }
  }
  LaunchedEffect(state.section) {
    if (gridState.layoutInfo.totalItemsCount > 0) gridState.scrollToItem(0)
  }
  val refreshMessage = (content as? FeedUiState.Content)?.refreshMessage
  LaunchedEffect(refreshMessage) {
    if (!refreshMessage.isNullOrBlank()) onConsumeRefreshMessage()
  }

  Column(Modifier.fillMaxSize()) {
    PopularSectionBar(selected = state.section, onSection = onSection)
    PopularContextBar(
      state = state,
      onWeeklyPeriod = onWeeklyPeriod,
      onRankCategory = onRankCategory,
      onMusicPeriod = onMusicPeriod,
      onHorizontalRailInteractionChanged = onHorizontalRailInteractionChanged,
    )
    PullRefreshContainer(
      refreshing = state.isRefreshing,
      onRefresh = onRefresh,
      modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
      when (content) {
        FeedUiState.Loading -> PopularFeedSkeleton()
        is FeedUiState.Content ->
          CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
            PopularVideoGrid(
              items = content.items,
              section = state.section,
              isLoadingMore = content.isLoadingMore,
              gridState = gridState,
              onItemClick = onItemClick,
              onItemLongClick = onItemLongClick,
              hiddenCoverItemId = hiddenCoverItemId,
            )
          }
        is FeedUiState.Empty -> PopularFeedMessage(content.message)
        is FeedUiState.ExtractionError -> PopularFeedError(content.detail, onRefresh)
        is FeedUiState.NetworkError -> PopularFeedError(content.detail, onRefresh)
      }
    }
  }
}

@Composable
private fun PopularSectionBar(
  selected: PopularSection,
  onSection: (PopularSection) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(18.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PopularSection.entries.forEach { section ->
        val isSelected = section == selected
        val visual = popularSectionVisual(section)
        Column(
          modifier =
            Modifier.widthIn(min = 108.dp)
              .clip(MaterialTheme.shapes.large)
              .clickable { onSection(section) }
              .padding(horizontal = 8.dp, vertical = 4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = visual.color,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = visual.icon,
                contentDescription = section.title,
                modifier = Modifier.size(21.dp),
                tint = Color.White,
              )
            }
          }
          Text(
            section.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color =
              if (isSelected) MaterialTheme.colorScheme.onSurface
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Box(
            Modifier.width(36.dp)
              .height(2.dp)
              .clip(CircleShape)
              .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.background
              )
          )
        }
      }
    }
  }
}

private data class PopularSectionVisual(
  val icon: ImageVector,
  val color: Color,
)

private fun popularSectionVisual(section: PopularSection): PopularSectionVisual =
  when (section) {
    PopularSection.ALL ->
      PopularSectionVisual(Icons.Default.LocalFireDepartment, Color(0xFFFF6B78))
    PopularSection.WEEKLY ->
      PopularSectionVisual(Icons.Default.CalendarMonth, Color(0xFFFFC83D))
    PopularSection.PRECIOUS ->
      PopularSectionVisual(Icons.Default.WorkspacePremium, Color(0xFFFF9400))
    PopularSection.RANKING ->
      PopularSectionVisual(Icons.Default.Leaderboard, Color(0xFFFF6F9E))
    PopularSection.MUSIC ->
      PopularSectionVisual(Icons.Default.Headset, Color(0xFF4098F8))
  }

@Composable
private fun PopularContextBar(
  state: PopularFeedUiState,
  onWeeklyPeriod: (PopularPeriod) -> Unit,
  onRankCategory: (PopularRankCategory) -> Unit,
  onMusicPeriod: (PopularPeriod) -> Unit,
  onHorizontalRailInteractionChanged: (Boolean) -> Unit,
) {
  Column(
    Modifier.fillMaxWidth()
      .background(MaterialTheme.colorScheme.background)
      .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      state.section.description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when (state.section) {
      PopularSection.WEEKLY ->
        PopularPeriodRail(
          periods = state.weeklyPeriods,
          selectedPeriodId = state.selectedWeeklyNumber,
          onPeriod = onWeeklyPeriod,
          onHorizontalRailInteractionChanged = onHorizontalRailInteractionChanged,
        )
      PopularSection.RANKING ->
        PopularRankRail(
          selected = state.rankCategory,
          onCategory = onRankCategory,
          onHorizontalRailInteractionChanged = onHorizontalRailInteractionChanged,
        )
      PopularSection.MUSIC ->
        PopularPeriodRail(
          periods = state.musicPeriods,
          selectedPeriodId = state.selectedMusicListId,
          onPeriod = onMusicPeriod,
          onHorizontalRailInteractionChanged = onHorizontalRailInteractionChanged,
        )
      PopularSection.ALL, PopularSection.PRECIOUS -> Unit
    }
  }
}

@Composable
private fun PopularPeriodRail(
  periods: List<PopularPeriod>,
  selectedPeriodId: Int?,
  onPeriod: (PopularPeriod) -> Unit,
  onHorizontalRailInteractionChanged: (Boolean) -> Unit,
) {
  PopularInteractiveRail(onHorizontalRailInteractionChanged) {
    lazyRowItems(periods, key = PopularPeriod::id) { period ->
      PopularFilterChip(
        text = period.label,
        selected = selectedPeriodId == period.id,
        onClick = { onPeriod(period) },
      )
    }
  }
}

@Composable
private fun PopularRankRail(
  selected: PopularRankCategory,
  onCategory: (PopularRankCategory) -> Unit,
  onHorizontalRailInteractionChanged: (Boolean) -> Unit,
) {
  PopularInteractiveRail(onHorizontalRailInteractionChanged) {
    lazyRowItems(PopularRankCategory.entries, key = PopularRankCategory::rid) { category ->
      PopularFilterChip(
        text = category.title,
        selected = selected == category,
        onClick = { onCategory(category) },
      )
    }
  }
}

@Composable
private fun PopularInteractiveRail(
  onHorizontalRailInteractionChanged: (Boolean) -> Unit,
  content: LazyListScope.() -> Unit,
) {
  val railState = rememberLazyListState()
  var touched by remember { mutableStateOf(false) }
  LaunchedEffect(touched, railState.isScrollInProgress) {
    onHorizontalRailInteractionChanged(touched || railState.isScrollInProgress)
  }
  LazyRow(
    state = railState,
    modifier =
      Modifier.fillMaxWidth().pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false)
          touched = true
          try {
            waitForUpOrCancellation()
          } finally {
            touched = false
          }
        }
      },
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

@Composable
private fun PopularFilterChip(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    modifier = Modifier.height(32.dp).clickable(onClick = onClick),
    shape = CircleShape,
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
      Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color =
          if (selected) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun PopularVideoGrid(
  items: List<FeedItem>,
  section: PopularSection,
  isLoadingMore: Boolean,
  gridState: LazyGridState,
  onItemClick: (FeedItem, Rect, FeedScrollAnchor) -> Unit,
  onItemLongClick: (FeedItem) -> Unit,
  hiddenCoverItemId: String?,
) {
  val flingTracker = remember(gridState) { FeedNavigationFlingTracker() }
  val dynamicPaletteAllowed =
    remember(gridState) {
      derivedStateOf {
        !FeedPerformanceConfig.aggressiveFastPathEnabled || !gridState.isScrollInProgress
      }
    }
  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    state = gridState,
    modifier = Modifier.fillMaxSize().nestedScroll(flingTracker).testTag("popular_grid"),
    contentPadding =
      PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 8.dp,
        bottom = NavigationCardBottomClearance,
      ),
    horizontalArrangement = Arrangement.spacedBy(18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    itemsIndexed(
      items = items,
      key = { _, item -> "${section.name}:${item.id}" },
      contentType = { _, _ -> "popular-video" },
    ) { index, item ->
      val itemKey = "${section.name}:${item.id}"
      VideoCardReveal(
        index = index,
        batchKey = "${section.name}:${items.firstOrNull()?.id}",
        itemKey = itemKey,
        animatedItemCount =
          if (FeedPerformanceConfig.aggressiveFastPathEnabled) {
            FeedPerformanceConfig.initialAnimatedCardCount
          } else {
            Int.MAX_VALUE
          },
      ) {
        PopularVideoCard(
          item = item,
          itemKey = itemKey,
          rank = index + 1,
          showRank = section == PopularSection.RANKING || section == PopularSection.MUSIC,
          gridState = gridState,
          flingTracker = flingTracker,
          dynamicPaletteAllowed = dynamicPaletteAllowed,
          coverVisible = item.id != hiddenCoverItemId,
          onClick = { bounds, anchor -> onItemClick(item, bounds, anchor) },
          onLongClick = { onItemLongClick(item) },
        )
      }
    }
    if (isLoadingMore) {
      item(key = "popular-loading", span = { GridItemSpan(maxLineSpan) }) {
        Box(
          Modifier.fillMaxWidth().padding(vertical = 12.dp),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PopularVideoCard(
  item: FeedItem,
  itemKey: String,
  rank: Int,
  showRank: Boolean,
  gridState: LazyGridState,
  flingTracker: FeedNavigationFlingTracker,
  dynamicPaletteAllowed: State<Boolean>,
  coverVisible: Boolean,
  onClick: (Rect, FeedScrollAnchor) -> Unit,
  onLongClick: () -> Unit,
) {
  var coverBounds = remember(item.id) { Rect.Zero }
  val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
  val scope = rememberCoroutineScope()
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) .98f else 1f,
      animationSpec = spring(dampingRatio = .82f, stiffness = 700f),
      label = "popularCardPress",
    )
  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .navigationBringIntoViewTarget(bringIntoViewRequester)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .combinedClickable(
          interactionSource = interactionSource,
          indication = LocalIndication.current,
          onClick = {
            scope.launch {
              settleFeedForNavigation(gridState, flingTracker)
              bringIntoViewRequester.bringIntoView()
              withFrameNanos {}
              withFrameNanos {}
              val anchor =
                feedReturnScrollAnchorAfterBringIntoView(
                  firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                  firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                )
              onClick(coverBounds, anchor)
            }
          },
          onLongClick = onLongClick,
    )
        .testTag("popular_card"),
    shape = VideoShapeTokens.Card,
    color = Color.Transparent,
    tonalElevation = 1.dp,
    shadowElevation = 0.dp,
  ) {
    VideoCardGradient(
      coverUrl = item.coverUrl,
      modifier = Modifier.fillMaxWidth(),
      loadKey = itemKey,
      dynamicPaletteAllowed = dynamicPaletteAllowed,
      paletteRequestWidth = FeedPerformanceConfig.coverRequestWidth,
      paletteRequestHeight = FeedPerformanceConfig.coverRequestHeight,
    ) {
      Box(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
          val coverWidth = (maxWidth * .36f).coerceIn(168.dp, 232.dp)
          Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Box(
              Modifier.width(coverWidth)
                .aspectRatio(16f / 9f)
                .onGloballyPositioned { coverBounds = it.boundsInRoot() }
                // Match recommendation cards: the source image is hidden as soon as the shared
                // cover takes ownership, while the palette background stays in place.
                .then(if (coverVisible) Modifier else Modifier.graphicsLayer { alpha = 0f })
            ) {
              CoverImage(
                coverUrl = item.coverUrl,
                modifier = Modifier.fillMaxSize(),
                shape = VideoShapeTokens.Player,
                requestWidth = FeedPerformanceConfig.coverRequestWidth,
                requestHeight = FeedPerformanceConfig.coverRequestHeight,
                loadKey = itemKey,
              )
            }
            Column(
              Modifier.weight(1f).fillMaxHeight(),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Spacer(Modifier.weight(1f, fill = false))
              Text(
                text = item.uploader.orEmpty(),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  feedCardStatsText(item.playCount, item.danmakuCount),
                  modifier = Modifier.weight(1f),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                item.duration?.takeIf(String::isNotBlank)?.let {
                  Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                  )
                }
              }
              item.description.takeIf(String::isNotBlank)?.let {
                Text(
                  it,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
        if (showRank) {
          val topThree = rank <= 3
          Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            shape = RoundedCornerShape(8.dp),
            color =
              if (topThree) Color(0xFFFF6F9E)
              else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .9f),
          ) {
            Text(
              rank.toString(),
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color =
                if (topThree) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PopularFeedSkeleton() {
  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    items(10) {
      Row(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          Modifier.width(210.dp)
            .aspectRatio(16f / 9f)
            .clip(VideoShapeTokens.Player)
            .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(
          Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            Modifier.fillMaxWidth().height(16.dp)
              .background(MaterialTheme.colorScheme.surfaceVariant)
          )
          Box(
            Modifier.fillMaxWidth(.72f).height(14.dp)
              .background(MaterialTheme.colorScheme.surfaceVariant)
          )
        }
      }
    }
  }
}

@Composable
private fun PopularFeedMessage(message: String) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun PopularFeedError(detail: String, onRetry: () -> Unit) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
      androidx.compose.material3.TextButton(onClick = onRetry) { Text("重试") }
    }
  }
}

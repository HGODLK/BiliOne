package dev.openbili.webdemo.feed

/**
 * 热门页：综合热门/每周必看/入站必刷/排行榜/全站音乐榜五个分类。
 */

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed as lazyRowItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import dev.openbili.webdemo.api.PopularPeriod
import dev.openbili.webdemo.ui.BackdropGlassSurface
import dev.openbili.webdemo.ui.HomeGlassTokens
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.LocalControlFocusVisible
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.VideoCardReveal
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.ui.navigationBringIntoViewTarget
import dev.openbili.webdemo.ui.rememberNavigationBringIntoViewRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 热门页组合体。 */
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
  backdropLayer: GraphicsLayer,
  onBackdropBoundsChanged: (Rect) -> Unit,
  underlayLayer: GraphicsLayer,
  underlayBounds: Rect,
  topContentPadding: Dp,
  pageControlsEnabled: Boolean = false,
  onControlEnterContent: () -> Unit = {},
  onControlReturnToPageControls: () -> Unit = {},
  initialFocusRequester: FocusRequester? = null,
) {
  val content = state.content
  val context = LocalContext.current
  val prefetchedCovers = remember { mutableSetOf<String>() }
  // 与推荐保持同一 Fast Path：两列布局只改变慢速移动时保留的可见键数量，
  // 快速滑动仍然暂停所有新图请求。
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
  LaunchedEffect(
    shouldLoadMore,
    content,
    imageLoadPolicy.mode,
    backgroundWorkAllowed,
    state.section,
  ) {
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
            // 与推荐一致：低于快滑阈值的移动获得一个小而串行的预看窗口；
            // 快速滑动则完全不开启新工作。
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
        // 滑动刚停时不要与可见卡片的解码竞争。
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

  var contentBackdropBounds by remember { mutableStateOf(Rect.Zero) }
  var chromeHeightPx by remember { mutableIntStateOf(0) }
  val density = LocalDensity.current
  val chromeHeight = with(density) { chromeHeightPx.toDp() }
  val sectionFocusRequesters = remember { PopularSection.entries.map { FocusRequester() } }
  val contextEntryFocusRequester = remember { FocusRequester() }
  LaunchedEffect(pageControlsEnabled, state.section) {
    if (pageControlsEnabled) {
      withFrameNanos {}
      runCatching { sectionFocusRequesters[state.section.ordinal].requestFocus() }
    }
  }
  val darkTheme = MaterialTheme.colorScheme.background.luminance() < .5f
  Box(Modifier.fillMaxSize()) {
    Box(
      Modifier.fillMaxSize()
        .onGloballyPositioned {
          contentBackdropBounds = it.boundsInRoot()
          onBackdropBoundsChanged(contentBackdropBounds)
        }
        .drawWithContent {
          if (backgroundWorkAllowed) {
            backdropLayer.record { this@drawWithContent.drawContent() }
            drawLayer(backdropLayer)
          } else {
            drawContent()
          }
        }
    ) {
      PullRefreshContainer(
        refreshing = state.isRefreshing,
        onRefresh = onRefresh,
        indicatorTopPadding = topContentPadding + chromeHeight + 8.dp,
        modifier = Modifier.fillMaxSize(),
      ) {
        val gridTopPadding = topContentPadding + chromeHeight + 8.dp
        when (content) {
          FeedUiState.Loading -> PopularFeedSkeleton(gridTopPadding)
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
                topContentPadding = gridTopPadding,
                onControlExitUp = onControlReturnToPageControls,
                initialFocusRequester = initialFocusRequester,
              )
            }
          is FeedUiState.Empty -> PopularFeedMessage(content.message)
          is FeedUiState.ExtractionError -> PopularFeedError(content.detail, onRefresh)
          is FeedUiState.NetworkError -> PopularFeedError(content.detail, onRefresh)
        }
      }
    }
    BackdropGlassSurface(
      backdropLayer = backdropLayer,
      backdropBounds = contentBackdropBounds,
      underlayLayer = underlayLayer,
      underlayBounds = underlayBounds,
      modifier =
        Modifier.align(Alignment.TopCenter)
          .padding(top = topContentPadding)
          .fillMaxWidth()
          .onSizeChanged { chromeHeightPx = it.height },
      shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
      blurRadius = HomeGlassTokens.BlurRadius,
      containerColor =
        MaterialTheme.colorScheme.surface.copy(
          alpha =
            if (darkTheme) HomeGlassTokens.DarkContainerAlpha
            else HomeGlassTokens.LightContainerAlpha
        ),
      border =
        BorderStroke(
          .75.dp,
          MaterialTheme.colorScheme.outlineVariant.copy(
            alpha =
              if (darkTheme) HomeGlassTokens.DarkBorderAlpha else HomeGlassTokens.LightBorderAlpha
          ),
        ),
      shadowElevation = 0.dp,
    ) {
      Column(Modifier.fillMaxWidth()) {
        PopularSectionBar(
          selected = state.section,
          onSection = onSection,
          controlEnabled = pageControlsEnabled,
          focusRequesters = sectionFocusRequesters,
          contextFocusRequester = contextEntryFocusRequester,
          hasContextControl = popularHasContextControl(state),
          onControlEnterContent = onControlEnterContent,
        )
        PopularContextBar(
          state = state,
          onWeeklyPeriod = onWeeklyPeriod,
          onRankCategory = onRankCategory,
          onMusicPeriod = onMusicPeriod,
          onHorizontalRailInteractionChanged = onHorizontalRailInteractionChanged,
          controlEnabled = pageControlsEnabled,
          sectionFocusRequester = sectionFocusRequesters[state.section.ordinal],
          entryFocusRequester = contextEntryFocusRequester,
          onControlEnterContent = onControlEnterContent,
        )
      }
    }
  }
}

@Composable
private fun PopularSectionBar(
  selected: PopularSection,
  onSection: (PopularSection) -> Unit,
  controlEnabled: Boolean,
  focusRequesters: List<FocusRequester>,
  contextFocusRequester: FocusRequester,
  hasContextControl: Boolean,
  onControlEnterContent: () -> Unit,
) {
  val controlFocusVisible = LocalControlFocusVisible.current
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Color.Transparent,
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(18.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PopularSection.entries.forEach { section ->
        val isSelected = section == selected
        val visual = popularSectionVisual(section)
        val interactionSource = remember(section) { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        Column(
          modifier =
            Modifier.widthIn(min = 108.dp)
              .focusRequester(focusRequesters[section.ordinal])
              .focusProperties {
                canFocus = controlEnabled
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
              }
              .onPreviewKeyEvent { event ->
                val direction =
                  popularControlDirection(event.nativeKeyEvent.keyCode)
                    ?: return@onPreviewKeyEvent false
                if (
                  event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.repeatCount == 0
                ) {
                  when (direction) {
                    FeedGridControlDirection.LEFT ->
                      focusRequesters.getOrNull(section.ordinal - 1)?.let {
                        runCatching { it.requestFocus() }
                      }
                    FeedGridControlDirection.RIGHT ->
                      focusRequesters.getOrNull(section.ordinal + 1)?.let {
                        runCatching { it.requestFocus() }
                      }
                    FeedGridControlDirection.DOWN ->
                      if (isSelected && hasContextControl) {
                        runCatching { contextFocusRequester.requestFocus() }
                      } else {
                        onControlEnterContent()
                      }
                    FeedGridControlDirection.UP -> Unit
                  }
                }
                true
              }
              .clip(MaterialTheme.shapes.large)
              .border(
                width = if (focused && controlFocusVisible) 3.dp else 0.dp,
                color =
                  if (focused && controlFocusVisible) MaterialTheme.colorScheme.primary
                  else Color.Transparent,
                shape = MaterialTheme.shapes.large,
              )
              .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
              ) {
                onSection(section)
              }
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
    PopularSection.ALL -> PopularSectionVisual(Icons.Default.LocalFireDepartment, Color(0xFFFF6B78))
    PopularSection.WEEKLY -> PopularSectionVisual(Icons.Default.CalendarMonth, Color(0xFFFFC83D))
    PopularSection.PRECIOUS ->
      PopularSectionVisual(Icons.Default.WorkspacePremium, Color(0xFFFF9400))
    PopularSection.RANKING -> PopularSectionVisual(Icons.Default.Leaderboard, Color(0xFFFF6F9E))
    PopularSection.MUSIC -> PopularSectionVisual(Icons.Default.Headset, Color(0xFF4098F8))
  }

private fun popularHasContextControl(state: PopularFeedUiState): Boolean =
  when (state.section) {
    PopularSection.WEEKLY -> state.weeklyPeriods.isNotEmpty()
    PopularSection.RANKING -> true
    PopularSection.MUSIC -> state.musicPeriods.isNotEmpty()
    PopularSection.ALL,
    PopularSection.PRECIOUS -> false
  }

private fun popularControlDirection(keyCode: Int): FeedGridControlDirection? =
  when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT -> FeedGridControlDirection.LEFT
    KeyEvent.KEYCODE_DPAD_RIGHT -> FeedGridControlDirection.RIGHT
    KeyEvent.KEYCODE_DPAD_UP -> FeedGridControlDirection.UP
    KeyEvent.KEYCODE_DPAD_DOWN -> FeedGridControlDirection.DOWN
    else -> null
  }

@Composable
private fun PopularContextBar(
  state: PopularFeedUiState,
  onWeeklyPeriod: (PopularPeriod) -> Unit,
  onRankCategory: (PopularRankCategory) -> Unit,
  onMusicPeriod: (PopularPeriod) -> Unit,
  onHorizontalRailInteractionChanged: (Boolean) -> Unit,
  controlEnabled: Boolean,
  sectionFocusRequester: FocusRequester,
  entryFocusRequester: FocusRequester,
  onControlEnterContent: () -> Unit,
) {
  Column(
    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
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
          controlEnabled = controlEnabled,
          sectionFocusRequester = sectionFocusRequester,
          entryFocusRequester = entryFocusRequester,
          onControlEnterContent = onControlEnterContent,
        )
      PopularSection.RANKING ->
        PopularRankRail(
          selected = state.rankCategory,
          onCategory = onRankCategory,
          onHorizontalRailInteractionChanged = onHorizontalRailInteractionChanged,
          controlEnabled = controlEnabled,
          sectionFocusRequester = sectionFocusRequester,
          entryFocusRequester = entryFocusRequester,
          onControlEnterContent = onControlEnterContent,
        )
      PopularSection.MUSIC ->
        PopularPeriodRail(
          periods = state.musicPeriods,
          selectedPeriodId = state.selectedMusicListId,
          onPeriod = onMusicPeriod,
          onHorizontalRailInteractionChanged = onHorizontalRailInteractionChanged,
          controlEnabled = controlEnabled,
          sectionFocusRequester = sectionFocusRequester,
          entryFocusRequester = entryFocusRequester,
          onControlEnterContent = onControlEnterContent,
        )
      PopularSection.ALL,
      PopularSection.PRECIOUS -> Unit
    }
  }
}

@Composable
private fun PopularPeriodRail(
  periods: List<PopularPeriod>,
  selectedPeriodId: Int?,
  onPeriod: (PopularPeriod) -> Unit,
  onHorizontalRailInteractionChanged: (Boolean) -> Unit,
  controlEnabled: Boolean,
  sectionFocusRequester: FocusRequester,
  entryFocusRequester: FocusRequester,
  onControlEnterContent: () -> Unit,
) {
  val focusRequesters =
    remember(periods, entryFocusRequester) {
      periods.indices.map { index ->
        if (index == 0) entryFocusRequester else FocusRequester()
      }
    }
  PopularInteractiveRail(onHorizontalRailInteractionChanged) {
    lazyRowItemsIndexed(periods, key = { _, period -> period.id }) { index, period ->
      PopularFilterChip(
        text = period.label,
        selected = selectedPeriodId == period.id,
        onClick = { onPeriod(period) },
        controlEnabled = controlEnabled,
        focusRequester = focusRequesters[index],
        controlLeftFocusRequester = focusRequesters.getOrNull(index - 1),
        controlRightFocusRequester = focusRequesters.getOrNull(index + 1),
        controlUpFocusRequester = sectionFocusRequester,
        onControlDown = onControlEnterContent,
      )
    }
  }
}

@Composable
private fun PopularRankRail(
  selected: PopularRankCategory,
  onCategory: (PopularRankCategory) -> Unit,
  onHorizontalRailInteractionChanged: (Boolean) -> Unit,
  controlEnabled: Boolean,
  sectionFocusRequester: FocusRequester,
  entryFocusRequester: FocusRequester,
  onControlEnterContent: () -> Unit,
) {
  val categories = PopularRankCategory.entries
  val focusRequesters =
    remember(entryFocusRequester) {
      categories.indices.map { index ->
        if (index == 0) entryFocusRequester else FocusRequester()
      }
    }
  PopularInteractiveRail(onHorizontalRailInteractionChanged) {
    lazyRowItemsIndexed(categories, key = { _, category -> category.rid }) { index, category ->
      PopularFilterChip(
        text = category.title,
        selected = selected == category,
        onClick = { onCategory(category) },
        controlEnabled = controlEnabled,
        focusRequester = focusRequesters[index],
        controlLeftFocusRequester = focusRequesters.getOrNull(index - 1),
        controlRightFocusRequester = focusRequesters.getOrNull(index + 1),
        controlUpFocusRequester = sectionFocusRequester,
        onControlDown = onControlEnterContent,
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
  controlEnabled: Boolean,
  focusRequester: FocusRequester,
  controlLeftFocusRequester: FocusRequester?,
  controlRightFocusRequester: FocusRequester?,
  controlUpFocusRequester: FocusRequester,
  onControlDown: () -> Unit,
) {
  val controlFocusVisible = LocalControlFocusVisible.current
  val interactionSource = remember { MutableInteractionSource() }
  val focused by interactionSource.collectIsFocusedAsState()
  Surface(
    modifier =
      Modifier.height(32.dp)
        .focusRequester(focusRequester)
        .focusProperties {
          canFocus = controlEnabled
          left = FocusRequester.Cancel
          right = FocusRequester.Cancel
          up = FocusRequester.Cancel
          down = FocusRequester.Cancel
        }
        .onPreviewKeyEvent { event ->
          val direction =
            popularControlDirection(event.nativeKeyEvent.keyCode)
              ?: return@onPreviewKeyEvent false
          if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
            when (direction) {
              FeedGridControlDirection.LEFT ->
                controlLeftFocusRequester?.let { runCatching { it.requestFocus() } }
              FeedGridControlDirection.RIGHT ->
                controlRightFocusRequester?.let { runCatching { it.requestFocus() } }
              FeedGridControlDirection.UP ->
                runCatching { controlUpFocusRequester.requestFocus() }
              FeedGridControlDirection.DOWN -> onControlDown()
            }
          }
          true
        }
        .clickable(
          interactionSource = interactionSource,
          indication = LocalIndication.current,
          onClick = onClick,
        ),
    shape = CircleShape,
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant,
    border =
      if (focused && controlFocusVisible)
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
      else null,
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
  topContentPadding: Dp,
  onControlExitUp: () -> Unit,
  initialFocusRequester: FocusRequester? = null,
) {
  val flingTracker = remember(gridState) { FeedNavigationFlingTracker() }
  val controlFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
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
        top = topContentPadding,
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
        val itemFocusRequester =
          initialFocusRequester.takeIf { index == 0 }
            ?: remember(itemKey) { FocusRequester() }
        DisposableEffect(itemKey, itemFocusRequester) {
          controlFocusRequesters[itemKey] = itemFocusRequester
          onDispose {
            if (controlFocusRequesters[itemKey] === itemFocusRequester) {
              controlFocusRequesters.remove(itemKey)
            }
          }
        }
        PopularVideoCard(
          item = item,
          itemKey = itemKey,
          rank = index + 1,
          showRank = section == PopularSection.RANKING || section == PopularSection.MUSIC,
          gridState = gridState,
          flingTracker = flingTracker,
          dynamicPaletteAllowed = dynamicPaletteAllowed,
          navigationTopClearance = topContentPadding,
          coverVisible = item.id != hiddenCoverItemId,
          focusRequester = itemFocusRequester,
          controlIndex = index,
          controlItemCount = items.size,
          controlFocusRequesterAt = { targetIndex ->
            items.getOrNull(targetIndex)?.let { target ->
              controlFocusRequesters["${section.name}:${target.id}"]
            }
          },
          onControlExitUp = onControlExitUp,
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
  navigationTopClearance: Dp,
  coverVisible: Boolean,
  focusRequester: FocusRequester? = null,
  controlIndex: Int,
  controlItemCount: Int,
  controlFocusRequesterAt: (Int) -> FocusRequester?,
  onControlExitUp: () -> Unit,
  onClick: (Rect, FeedScrollAnchor) -> Unit,
  onLongClick: () -> Unit,
) {
  var coverBounds = remember(item.id) { Rect.Zero }
  val bringIntoViewRequester =
    rememberNavigationBringIntoViewRequester(topClearance = navigationTopClearance)
  val scope = rememberCoroutineScope()
  val controlMode = LocalControlMode.current
  val controlFocusVisible = LocalControlFocusVisible.current
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val focused by interactionSource.collectIsFocusedAsState()
  LaunchedEffect(focused) {
    if (focused) bringIntoViewRequester.bringIntoView()
  }
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) .98f else if (focused) 1.025f else 1f,
      animationSpec = spring(dampingRatio = .82f, stiffness = 700f),
      label = "popularCardPress",
    )
  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .then(
          if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
        )
        .then(
          if (controlMode) {
            Modifier.onPreviewKeyEvent { event ->
              val direction =
                popularControlDirection(event.nativeKeyEvent.keyCode)
                  ?: return@onPreviewKeyEvent false
              if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                if (direction == FeedGridControlDirection.UP && controlIndex < 2) {
                  onControlExitUp()
                } else {
                  val targetIndex =
                    feedGridControlTargetIndex(
                      currentIndex = controlIndex,
                      itemCount = controlItemCount,
                      columns = 2,
                      direction = direction,
                    )
                  if (targetIndex != null) {
                    scope.launch {
                      var targetRequester = controlFocusRequesterAt(targetIndex)
                      val targetFocused =
                        targetRequester?.let {
                          runCatching { it.requestFocus() }.getOrDefault(false)
                        } == true
                      if (!targetFocused) {
                        gridState.animateScrollToItem(targetIndex)
                        withFrameNanos {}
                        withFrameNanos {}
                        targetRequester = controlFocusRequesterAt(targetIndex)
                        targetRequester?.let { runCatching { it.requestFocus() } }
                      }
                    }
                  }
                }
              }
              true
            }
          } else Modifier
        )
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
    border =
      if (focused && controlFocusVisible)
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
      else null,
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
                // 与推荐卡一致：共享封面接管后源图立即隐藏，取色渐变背景保持原位。
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
              color = if (topThree) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PopularFeedSkeleton(topContentPadding: Dp) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    modifier = Modifier.fillMaxSize(),
    contentPadding =
      PaddingValues(
        start = 16.dp,
        top = topContentPadding,
        end = 16.dp,
        bottom = 16.dp,
      ),
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
            Modifier.fillMaxWidth()
              .height(16.dp)
              .background(MaterialTheme.colorScheme.surfaceVariant)
          )
          Box(
            Modifier.fillMaxWidth(.72f)
              .height(14.dp)
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

package dev.openbili.webdemo.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.openbili.webdemo.api.BangumiCoverVariant
import dev.openbili.webdemo.api.BangumiExploreCardStyle
import dev.openbili.webdemo.api.BangumiExploreCategory
import dev.openbili.webdemo.api.BangumiExploreItem
import dev.openbili.webdemo.api.BangumiExplorePage
import dev.openbili.webdemo.api.BangumiExploreSectionKind
import dev.openbili.webdemo.api.BangumiWatchProgress
import dev.openbili.webdemo.api.BangumiWatchProgressState
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.bangumiCoverUrl
import dev.openbili.webdemo.api.bangumiOriginalImageUrl
import dev.openbili.webdemo.bangumi.BangumiExploreUiState
import dev.openbili.webdemo.bangumi.BangumiFollowingUiState
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.video.formatCompactCount
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The second-level PGC page deliberately owns no player. It is safe to mount underneath the shared
 * root player and keeps both the top category capsule and bottom root capsule clear of scroll
 * content.
 */
@Composable
internal fun BangumiExploreScreen(
  active: Boolean,
  interactionEnabled: Boolean,
  state: BangumiExploreUiState,
  hiddenItemId: String?,
  onSelectCategory: (BangumiExploreCategory) -> Unit,
  onRefresh: (BangumiExploreCategory) -> Unit,
  onLoadMoreFollowing: (BangumiExploreCategory) -> Unit,
  onExplorePull: (Float) -> Unit,
  onExplorePullRelease: (Float) -> Unit,
  onOpenLandscape: (BangumiExploreItem, Rect) -> Unit,
  onOpenPoster: (BangumiExploreItem, Rect) -> Unit,
  onOpenIndex: (Rect) -> Unit,
  modifier: Modifier = Modifier,
) {
  val categories = BangumiExploreCategory.entries
  val pagerState =
    rememberPagerState(
      initialPage = state.selectedCategory.ordinal,
      pageCount = { categories.size },
    )
  val scope = rememberCoroutineScope()
  val currentSelectCategory by rememberUpdatedState(onSelectCategory)
  val contentBackdropLayer = rememberGraphicsLayer()
  var contentBounds by remember { mutableStateOf(Rect.Zero) }
  val density = LocalDensity.current
  val contentTopPadding =
    bangumiExploreContentTopPadding(
      safeDrawingTop = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
    )

  // The base page is composed behind the recommendation cover, so start its first category load
  // immediately. This guarantees a visible loading state the instant the cover is pulled away.
  LaunchedEffect(Unit) { onSelectCategory(state.selectedCategory) }
  LaunchedEffect(pagerState.settledPage) {
    currentSelectCategory(categories[pagerState.settledPage])
  }
  LaunchedEffect(state.selectedCategory) {
    val target = state.selectedCategory.ordinal
    if (!pagerState.isScrollInProgress && pagerState.currentPage != target) {
      pagerState.scrollToPage(target)
    }
  }

  Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Capture only the scrollable content. The glass capsule is drawn outside this recorder, so
    // it can blur the page beneath it without sampling itself or AppRoot's capture layer.
    Box(
      Modifier.fillMaxSize()
        .onGloballyPositioned { contentBounds = it.boundsInRoot() }
        .drawWithContent {
          contentBackdropLayer.record { this@drawWithContent.drawContent() }
          drawLayer(contentBackdropLayer)
        }
    ) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
      ) { pageIndex ->
        val category = categories[pageIndex]
        BangumiExploreCategoryContent(
          category = category,
          state = state,
          contentTopPadding = contentTopPadding,
          hiddenItemId = hiddenItemId,
          foregroundActive = active,
          onRefresh = { onRefresh(category) },
          onExplorePull = onExplorePull,
          onExplorePullRelease = onExplorePullRelease,
          onLoadMoreFollowing = { onLoadMoreFollowing(category) },
          onOpenLandscape = onOpenLandscape,
          onOpenPoster = onOpenPoster,
        )
      }
    }
    BoxWithConstraints(
      Modifier.align(Alignment.TopCenter)
        .fillMaxWidth()
        // Keep the category capsule independently centered. The adjacent index entry is placed
        // from the capsule's measured design width instead of reserving space inside that center.
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        .padding(top = 10.dp)
        .height(56.dp)
        .zIndex(1f)
    ) {
      val density = LocalDensity.current
      val categoryWidth = minOf(maxWidth, 680.dp)
      var indexWidthPx by remember { mutableIntStateOf(0) }
      BangumiExploreCategoryCapsule(
        selectionPosition = {
          (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(
            0f,
            categories.lastIndex.toFloat(),
          )
        },
        backdropLayer = contentBackdropLayer,
        backdropBounds = contentBounds,
        modifier = Modifier.align(Alignment.Center),
        onCategoryClick = { category ->
          scope.launch { pagerState.animateScrollToPage(category.ordinal) }
        },
        onSelectionDrag = { position ->
          val clamped = position.coerceIn(0f, categories.lastIndex.toFloat())
          val lower = floor(clamped).toInt()
          if (lower >= categories.lastIndex) {
            pagerState.requestScrollToPage(categories.lastIndex)
          } else {
            val fraction = clamped - lower
            if (fraction <= .5f) {
              pagerState.requestScrollToPage(lower, fraction)
            } else {
              pagerState.requestScrollToPage(lower + 1, fraction - 1f)
            }
          }
        },
      )
      BangumiIndexEntryCapsule(
        backdropLayer = contentBackdropLayer,
        backdropBounds = contentBounds,
        enabled = interactionEnabled,
        onClick = onOpenIndex,
        modifier =
          Modifier.align(Alignment.Center)
            .onSizeChanged { indexWidthPx = it.width }
            .offset {
              val categoryHalfPx = with(density) { categoryWidth.toPx() / 2f }
              val gapPx = with(density) { 8.dp.toPx() }
              IntOffset((categoryHalfPx + gapPx + indexWidthPx / 2f).roundToInt(), 0)
            },
      )
    }
    if (!interactionEnabled) {
      // The recommendation layer may be visually transparent in parts while it is covering or
      // returning. Keep a real input barrier above this page so taps never reach cards beneath.
      Box(
        Modifier.fillMaxSize().zIndex(2f).pointerInput(interactionEnabled) {
            awaitPointerEventScope {
              while (true) awaitPointerEvent().changes.forEach { it.consume() }
            }
          }
      )
    }
  }
}

@Composable
private fun BangumiIndexEntryCapsule(
  backdropLayer: GraphicsLayer,
  backdropBounds: Rect,
  enabled: Boolean,
  onClick: (Rect) -> Unit,
  modifier: Modifier = Modifier,
) {
  var bounds by remember { mutableStateOf(Rect.Zero) }
  val shape = CircleShape
  BackdropGlassSurface(
    backdropLayer = backdropLayer,
    backdropBounds = backdropBounds,
    modifier =
      modifier
        .size(48.dp)
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        .clip(shape)
        .clickable(enabled = enabled) { onClick(bounds) },
    shape = shape,
    blurRadius = 14.dp,
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .48f),
    border = BorderStroke(.75.dp, Color.White.copy(alpha = .20f)),
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Default.Search,
        contentDescription = "打开索引",
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun BangumiExploreCategoryCapsule(
  selectionPosition: () -> Float,
  backdropLayer: GraphicsLayer,
  backdropBounds: Rect,
  modifier: Modifier,
  onCategoryClick: (BangumiExploreCategory) -> Unit,
  onSelectionDrag: (Float) -> Unit,
) {
  var dragPosition by remember { mutableStateOf<Float?>(null) }
  var selectionTravelPx by remember { mutableFloatStateOf(1f) }
  val selectionPillColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)
  val shape = CircleShape
  fun settleDrag() {
    val position =
      (dragPosition ?: selectionPosition()).coerceIn(
        0f,
        BangumiExploreCategory.entries.lastIndex.toFloat(),
      )
    dragPosition = null
    onCategoryClick(BangumiExploreCategory.entries[position.roundToInt()])
  }
  BackdropGlassSurface(
    backdropLayer = backdropLayer,
    backdropBounds = backdropBounds,
    // Same capsule recipe as the root navigation: full rounded glass, a subtle border, and a
    // sliding selection pill. The top bar remains thinner because it carries text only.
    modifier = modifier.widthIn(max = 680.dp).fillMaxWidth(),
    shape = shape,
    blurRadius = 14.dp,
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .48f),
    border = BorderStroke(0.75.dp, Color.White.copy(alpha = .20f)),
  ) {
    Box(
      Modifier.padding(6.dp)
        .height(44.dp)
        .fillMaxWidth()
        .onSizeChanged {
          selectionTravelPx =
            (it.width / BangumiExploreCategory.entries.size.toFloat()).coerceAtLeast(1f)
        }
        .pointerInput(selectionTravelPx) {
          detectHorizontalDragGestures(
            onDragStart = { dragPosition = selectionPosition() },
            onHorizontalDrag = { change, dragAmount ->
              change.consume()
              val updated =
                ((dragPosition ?: selectionPosition()) + dragAmount / selectionTravelPx).coerceIn(
                  0f,
                  BangumiExploreCategory.entries.lastIndex.toFloat(),
                )
              dragPosition = updated
              onSelectionDrag(updated)
            },
            onDragEnd = ::settleDrag,
            onDragCancel = ::settleDrag,
          )
        }
    ) {
      val currentPosition = dragPosition ?: selectionPosition()
      Canvas(Modifier.matchParentSize()) {
        val pillWidth = size.width / BangumiExploreCategory.entries.size
        drawRoundRect(
          color = selectionPillColor,
          topLeft = Offset(pillWidth * currentPosition, 0f),
          size = Size(pillWidth, size.height),
          cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
        )
      }
      Row(
        Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
      ) {
        BangumiExploreCategory.entries.forEach { category ->
          val selectedItem = category.ordinal == currentPosition.roundToInt()
          Box(
            Modifier.weight(1f).fillMaxSize().clip(CircleShape).clickable {
              onCategoryClick(category)
            },
            contentAlignment = Alignment.Center,
          ) {
            Text(
              category.label,
              maxLines = 1,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = if (selectedItem) FontWeight.Bold else FontWeight.Medium,
              color =
                if (selectedItem) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BangumiExploreCategoryContent(
  category: BangumiExploreCategory,
  state: BangumiExploreUiState,
  contentTopPadding: Dp,
  hiddenItemId: String?,
  foregroundActive: Boolean,
  onRefresh: () -> Unit,
  onExplorePull: (Float) -> Unit,
  onExplorePullRelease: (Float) -> Unit,
  onLoadMoreFollowing: () -> Unit,
  onOpenLandscape: (BangumiExploreItem, Rect) -> Unit,
  onOpenPoster: (BangumiExploreItem, Rect) -> Unit,
) {
  val page = state.pages[category]
  val loading = category in state.loading
  val error = state.errors[category]
  when {
    page == null && loading ->
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp)
      }
    page == null && error != null ->
      Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRefresh) { Text("重试") }
      }
    page == null -> Unit
    else ->
      if (
        category == BangumiExploreCategory.ANIME || category == BangumiExploreCategory.GUOCHUANG
      ) {
        // 国创 is追更型 like anime: it reuses the anime hero + following + ranking + recommendation
        // layout, but owns a distinct state tree. A new session key resets all local scroll and
        // animation state on entry instead of retaining the previously selected subpage.
        val following = state.following(category)
        key(category, following.sessionId) {
          AnimeExploreContent(
            category = category,
            page = page,
            contentTopPadding = contentTopPadding,
            following = following,
            accountMid = state.accountMid,
            hiddenItemId = hiddenItemId,
            foregroundActive = foregroundActive,
            onExplorePull = onExplorePull,
            onExplorePullRelease = onExplorePullRelease,
            onLoadMoreFollowing = onLoadMoreFollowing,
            onOpenLandscape = onOpenLandscape,
            onOpenPoster = onOpenPoster,
          )
        }
      } else {
        ExploreCategoryGridContent(
          category = category,
          page = page,
          contentTopPadding = contentTopPadding,
          hiddenItemId = hiddenItemId,
          foregroundActive = foregroundActive,
          onExplorePull = onExplorePull,
          onExplorePullRelease = onExplorePullRelease,
          onOpenLandscape = onOpenLandscape,
          onOpenPoster = onOpenPoster,
        )
      }
  }
}

internal data class AnimeExploreContentGroups(
  val hot: List<BangumiExploreItem>,
  val ranking: List<BangumiExploreItem>,
  val recommendations: List<BangumiExploreItem>,
)

internal fun bangumiExploreContentTopPadding(safeDrawingTop: Dp): Dp =
  safeDrawingTop + 78.dp

internal fun animeExploreContentGroups(page: BangumiExplorePage): AnimeExploreContentGroups {
  val visibleSections = page.sections.filterNot { it.kind == BangumiExploreSectionKind.TIMELINE }
  val hot =
    visibleSections
      .filter { it.kind == BangumiExploreSectionKind.HOT }
      .flatMap { it.items }
      .ifEmpty {
        visibleSections.filter { it.kind != BangumiExploreSectionKind.RANKING }.flatMap { it.items }
      }
      .distinctBy(BangumiExploreItem::stableId)
      .take(6)
  val ranking =
    visibleSections
      .filter { it.kind == BangumiExploreSectionKind.RANKING }
      .flatMap { it.items }
      .distinctBy(BangumiExploreItem::stableId)
      .take(10)
  val recommendations =
    visibleSections
      .filter { it.kind == BangumiExploreSectionKind.FEED }
      .flatMap { it.items }
      .distinctBy(BangumiExploreItem::stableId)
  return AnimeExploreContentGroups(hot = hot, ranking = ranking, recommendations = recommendations)
}

@Composable
private fun AnimeExploreContent(
  category: BangumiExploreCategory = BangumiExploreCategory.ANIME,
  page: BangumiExplorePage,
  contentTopPadding: Dp,
  following: BangumiFollowingUiState,
  accountMid: Long,
  hiddenItemId: String?,
  foregroundActive: Boolean,
  onExplorePull: (Float) -> Unit,
  onExplorePullRelease: (Float) -> Unit,
  onLoadMoreFollowing: () -> Unit,
  onOpenLandscape: (BangumiExploreItem, Rect) -> Unit,
  onOpenPoster: (BangumiExploreItem, Rect) -> Unit,
) {
  val groups = remember(page) { animeExploreContentGroups(page) }
  val gridState = rememberLazyGridState()
  var recommendationVisibleCount by remember(page) { mutableIntStateOf(10) }
  val visibleRecommendations = groups.recommendations.take(recommendationVisibleCount)
  val latestExplorePull by rememberUpdatedState(onExplorePull)
  val latestExplorePullRelease by rememberUpdatedState(onExplorePullRelease)
  val handoffSlop = with(LocalDensity.current) { 12.dp.toPx() }
  var pullDistance by remember { mutableFloatStateOf(0f) }
  // Hoist the hero card pager's state here so the nested-scroll connection can read
  // isScrollInProgress directly. Unlike a pointerInput-set flag, isScrollInProgress stays true
  // through the pager's fling/settle phase (after finger lift), preventing pull-to-collapse from
  // stealing the fling's scroll deltas. Reading it is a snapshot read — current within the same
  // input phase, no recomposition delay.
  val heroItems = groups.hot
  val midpoint = Int.MAX_VALUE / 2
  val heroInitialPage =
    remember(heroItems.size) {
    if (heroItems.isEmpty()) 0 else midpoint - Math.floorMod(midpoint, heroItems.size)
  }
  val heroPagerState =
    rememberPagerState(
    initialPage = heroInitialPage,
    pageCount = { if (heroItems.isEmpty()) 1 else Int.MAX_VALUE },
  )
  LaunchedEffect(gridState, visibleRecommendations.size, groups.recommendations.size) {
    snapshotFlow {
      val layoutInfo = gridState.layoutInfo
      val lastVisible = layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
      layoutInfo.totalItemsCount > 0 && lastVisible >= layoutInfo.totalItemsCount - 2
      }
      .collect { nearEnd ->
      if (nearEnd && recommendationVisibleCount < groups.recommendations.size) {
        recommendationVisibleCount =
          (recommendationVisibleCount + 10).coerceAtMost(groups.recommendations.size)
      }
    }
  }
  val pullToCollapseConnection =
    remember(gridState, handoffSlop) {
      object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
          if (heroPagerState.isScrollInProgress) {
            pullDistance = 0f
            return Offset.Zero
          }
          if (available.y > 0f && !gridState.canScrollBackward) {
            val previous = pullDistance
            pullDistance += available.y
            val handedBefore = (previous - handoffSlop).coerceAtLeast(0f)
            val handedAfter = (pullDistance - handoffSlop).coerceAtLeast(0f)
            val handoff = handedAfter - handedBefore
            if (handoff > 0f) {
              latestExplorePull(handoff)
              return Offset(0f, handoff)
            }
          } else if (available.y < 0f && pullDistance > handoffSlop) {
            val retract = minOf(-available.y, pullDistance - handoffSlop)
            pullDistance -= retract
            latestExplorePull(-retract)
            return Offset(0f, -retract)
          } else if (available.y < 0f) {
            pullDistance = 0f
          }
          return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
          if (heroPagerState.isScrollInProgress) {
            pullDistance = 0f
            return Velocity.Zero
          }
          if (pullDistance > handoffSlop) {
            latestExplorePullRelease(available.y)
            pullDistance = 0f
          }
          return Velocity.Zero
        }
      }
    }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = if (maxWidth >= 900.dp) GridCells.Fixed(5) else GridCells.Adaptive(140.dp)
    LazyVerticalGrid(
      columns = columns,
      state = gridState,
      modifier = Modifier.fillMaxSize().nestedScroll(pullToCollapseConnection),
      contentPadding =
        PaddingValues(start = 28.dp, top = contentTopPadding, end = 28.dp, bottom = 118.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      if (groups.hot.isNotEmpty()) {
        item(key = "anime-header", span = { GridItemSpan(maxLineSpan) }) {
          AnimeExploreHeader(
            items = groups.hot,
            following = following,
            accountMid = accountMid,
            hiddenItemId = hiddenItemId,
            foregroundActive = foregroundActive,
            onOpen = onOpenLandscape,
            pagerState = heroPagerState,
            onLoadMoreFollowing = onLoadMoreFollowing,
          )
        }
      } else {
        item(key = "anime-following", span = { GridItemSpan(maxLineSpan) }) {
          AnimeFollowingSection(
            state = following,
            accountMid = accountMid,
            hiddenItemId = hiddenItemId,
            onLoadMore = onLoadMoreFollowing,
            onOpen = onOpenLandscape,
          )
        }
      }

      if (groups.ranking.isNotEmpty()) {
        item(key = "anime-ranking-heading", span = { GridItemSpan(maxLineSpan) }) {
          ExploreSectionHeading("热播榜")
        }
        gridItemsIndexed(groups.ranking, key = { _, item -> "anime-rank-${item.stableId}" }) {
          index,
          item ->
          AnimeRankingPoster(
            item = item,
            rank = index + 1,
            hidden = "bangumi-explore-${item.stableId}" == hiddenItemId,
            foregroundActive = foregroundActive,
            onOpen = onOpenPoster,
          )
        }
      }

      if (visibleRecommendations.isNotEmpty()) {
        item(key = "anime-recommend-heading", span = { GridItemSpan(maxLineSpan) }) {
          AnimeRecommendationBoundary(subtitle = "为你精选的${category.label}")
        }
        gridItemsIndexed(
          visibleRecommendations,
          key = { _, item -> "anime-rec-${item.stableId}" },
        ) { _, item ->
          AnimeRecommendationPoster(
            item = item,
            hidden = "bangumi-explore-${item.stableId}" == hiddenItemId,
            foregroundActive = foregroundActive,
            onOpen = onOpenPoster,
          )
        }
      }
    }
  }
}

@Composable
private fun ExploreSectionHeading(title: String) {
  Text(
    title,
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.SemiBold,
  )
}

@Composable
private fun AnimeRecommendationBoundary(
  title: String = "猜你喜欢",
  subtitle: String = "为你精选的番剧",
) {
  Box(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(
        Brush.horizontalGradient(
          listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = .82f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f),
          )
        )
      )
      .padding(horizontal = 20.dp, vertical = 16.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        subtitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun AnimeExploreHeader(
  items: List<BangumiExploreItem>,
  pagerState: androidx.compose.foundation.pager.PagerState,
  following: BangumiFollowingUiState,
  accountMid: Long,
  hiddenItemId: String?,
  foregroundActive: Boolean,
  onOpen: (BangumiExploreItem, Rect) -> Unit,
  onLoadMoreFollowing: () -> Unit,
) {
  Column(Modifier.fillMaxWidth()) {
    AnimeHotHeroContent(
      items = items,
      pagerState = pagerState,
      hiddenItemId = hiddenItemId,
      foregroundActive = foregroundActive,
      onOpen = onOpen,
    )
    AnimeFollowingSection(
      state = following,
      accountMid = accountMid,
      hiddenItemId = hiddenItemId,
      onLoadMore = onLoadMoreFollowing,
      onOpen = onOpen,
      modifier = Modifier.padding(top = 22.dp, bottom = 24.dp),
    )
  }
}

@Composable
private fun AnimeHotHeroContent(
  items: List<BangumiExploreItem>,
  pagerState: androidx.compose.foundation.pager.PagerState,
  hiddenItemId: String?,
  foregroundActive: Boolean,
  onOpen: (BangumiExploreItem, Rect) -> Unit,
) {
  val selectedItem = items[Math.floorMod(pagerState.currentPage, items.size)]
  // The hero never joins the shared-cover transition. Opening always flies the selected stack
  // card; the hero info only fades out before that flight and fades back in after the return
  // cover has landed (hiddenItemId cleared by the root transition).
  var openRequestTick by remember { mutableStateOf(0) }
  var opening by remember { mutableStateOf(false) }
  val heroInfoAlpha = remember { Animatable(1f) }
  val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
  val scope = rememberCoroutineScope()

  fun requestOpen() {
    if (opening || pagerState.isScrollInProgress) return
    scope.launch {
      bringIntoViewRequester.bringIntoView()
      withFrameNanos {}
      if (!opening && !pagerState.isScrollInProgress) {
        opening = true
        openRequestTick += 1
      }
    }
  }

  LaunchedEffect(openRequestTick) {
    if (openRequestTick > 0) {
      heroInfoAlpha.animateTo(0f, tween(MotionTokens.Standard))
    }
  }
  // Restore only once the return cover has landed and the explore page is foreground again.
  // hiddenItemId also clears when the forward flight lands (detail covers this page); gating on
  // foregroundActive keeps the info hidden until the real return, so it fades back instead of
  // popping in underneath the detail page.
  LaunchedEffect(hiddenItemId, foregroundActive) {
    if (opening && hiddenItemId == null && foregroundActive) {
      opening = false
      heroInfoAlpha.animateTo(1f, tween(MotionTokens.Standard))
    }
  }

  // Stack cards share the player cover's 16:9 artwork (same as the recommendation stack), so the
  // shared return flight lands with an identical crop instead of a re-cropped, distorted frame.
  // The info rail's end padding derives from the same width so text always clears the stack.
  val stackCardHeight = 184.dp
  val stackWidth = stackCardHeight * 16f / 9f
  val stackEndPadding = 24.dp
  val heroInfoEndPadding = stackWidth + stackEndPadding + 32.dp

  Box(
    Modifier.fillMaxWidth()
      .height(410.dp)
      .navigationBringIntoViewTarget(bringIntoViewRequester)
      .clip(RoundedCornerShape(24.dp))
      .clickable { requestOpen() }
  ) {
    Crossfade(
      targetState = selectedItem,
      animationSpec = tween(durationMillis = 280),
      label = "animeHeroCover",
      modifier = Modifier.fillMaxSize(),
    ) { heroItem ->
      CoverImage(
        coverUrl = bangumiOriginalImageUrl(heroItem.heroCoverUrl),
        contentDescription = null,
        // The upstream hero artwork is often only available at a modest resolution. A subtle
        // blur plus overscan keeps that limitation from reading as compression noise while
        // remaining isolated to this background layer.
        modifier =
          Modifier.fillMaxSize()
            .graphicsLayer {
              scaleX = 1.04f
              scaleY = 1.04f
            }
            .blur(9.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
        shape = RoundedCornerShape(24.dp),
        enforceAspectRatio = false,
        contentScale = ContentScale.Crop,
        requestWidth = 1920,
        requestHeight = 1080,
        loadKey = "anime-header-hero-${heroItem.stableId}",
        useOriginalSource = true,
      )
    }
    Box(
      Modifier.fillMaxSize()
        .background(
        Brush.horizontalGradient(
            listOf(Color.Black.copy(alpha = .50f), Color.Transparent, Color.Transparent)
        )
      )
    )
    Column(
      Modifier.align(Alignment.BottomStart)
        .padding(start = 52.dp, end = heroInfoEndPadding, bottom = 58.dp)
        .graphicsLayer { alpha = heroInfoAlpha.value },
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        "新热推荐",
        color = Color.White.copy(alpha = .88f),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        selectedItem.title,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = Color.White,
        style =
          MaterialTheme.typography.headlineLarge.copy(
            fontSize = 40.sp,
            lineHeight = 44.sp,
          ),
        fontWeight = FontWeight.Bold,
      )
      if (selectedItem.subtitle.isNotBlank()) {
        Text(
          selectedItem.subtitle,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          color = Color.White.copy(alpha = .84f),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      if (selectedItem.rating != null || selectedItem.ratingCount > 0L) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          selectedItem.rating
            ?.takeIf { it > 0.0 }
            ?.let { rating ->
            Text(
              "评分 ${String.format(Locale.US, "%.1f", rating)}",
              color = Color.White,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
          }
          selectedItem.ratingCount
            .takeIf { it > 0L }
            ?.let { count ->
            Text(
              "${formatCompactCount(count)} 人评分",
              color = Color.White.copy(alpha = .82f),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }
      Text(
        "查看详情",
        color = Color.White.copy(alpha = .94f),
        style = MaterialTheme.typography.labelLarge,
      )
    }
    VerticalPager(
      state = pagerState,
      modifier =
        Modifier.align(Alignment.CenterEnd)
          .padding(end = stackEndPadding)
          .width(stackWidth)
          .height(410.dp)
          .nestedScroll(
            remember {
            object : NestedScrollConnection {
              override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                Offset.Zero

                override fun onPostScroll(
                  consumed: Offset,
                  available: Offset,
                  source: NestedScrollSource,
                ): Offset = available

                override suspend fun onPostFling(
                  consumed: Velocity,
                  available: Velocity,
                ): Velocity = available
            }
            }
          ),
      pageSize = PageSize.Fixed(stackCardHeight),
      contentPadding = PaddingValues(vertical = 113.dp),
      pageSpacing = 10.dp,
      beyondViewportPageCount = 1,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) { page ->
      val item = items[Math.floorMod(page, items.size)]
      val selected = page == pagerState.currentPage
      val pageOffset =
        ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
      AnimeHotStackCard(
        item = item,
        selected = selected,
        pageOffset = pageOffset,
        hidden = "bangumi-explore-${item.stableId}" == hiddenItemId,
        foregroundActive = foregroundActive,
        openRequestTick = openRequestTick,
        onRequestOpen = { requestOpen() },
        onOpen = { bounds -> onOpen(item, bounds) },
      )
    }
  }
}

@Composable
private fun AnimeHotStackCard(
  item: BangumiExploreItem,
  selected: Boolean,
  pageOffset: Float,
  hidden: Boolean,
  foregroundActive: Boolean,
  openRequestTick: Int,
  onRequestOpen: () -> Unit,
  onOpen: (Rect) -> Unit,
) {
  var bounds by remember(item.stableId) { mutableStateOf(Rect.Zero) }
  var opening by remember(item.stableId) { mutableStateOf(false) }
  val interactionSource = remember(item.stableId) { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val pressScale by
    animateFloatAsState(
      targetValue = if (pressed) MotionTokens.PressedScale else 1f,
      animationSpec = tween(MotionTokens.Quick),
      label = "animeHotCardPress",
    )
  val chromeAlpha = remember(item.stableId) { Animatable(1f) }
  var chromeAnimationJob by remember(item.stableId) { mutableStateOf<Job?>(null) }
  val scope = rememberCoroutineScope()

  fun startOpenPrelude() {
    if (opening) return
    opening = true
    chromeAnimationJob?.cancel()
    chromeAnimationJob = scope.launch {
        // Match the home-card contract: fade the chrome first while the card is stationary, then
        // hand the real bounds to the shared transition so only the cover flies.
        chromeAlpha.animateTo(0f, tween(MotionTokens.Standard))
        withFrameNanos {}
        onOpen(bounds)
      }
  }

  LaunchedEffect(openRequestTick) {
    // Trigger-time guards (selected, not scrolling, not already opening) live in requestOpen;
    // every accepted tick must complete its prelude so the hero-level opening state cannot
    // get stuck.
    if (openRequestTick > 0 && selected) startOpenPrelude()
  }
  LaunchedEffect(hidden, foregroundActive) {
    // Restore chrome only after the returning cover has landed AND the explore page is foreground
    // again. hidden also flips back to false when the forward flight lands (detail covers this
    // page); without the foregroundActive gate the title/gradient would snap back invisibly under
    // the detail page and then pop in on return instead of fading.
    if (opening && !hidden && foregroundActive) {
      opening = false
      chromeAnimationJob?.cancel()
      chromeAnimationJob = scope.launch { chromeAlpha.animateTo(1f, tween(MotionTokens.Standard)) }
    }
  }

  val clickModifier =
    if (selected) {
      Modifier.clickable(
        interactionSource = interactionSource,
        indication = null,
      ) {
        onRequestOpen()
      }
    } else {
      Modifier
    }

  Box(
    Modifier.fillMaxSize().zIndex(10f - pageOffset),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      modifier =
        Modifier.fillMaxSize()
          .graphicsLayer {
            val depth = pageOffset.coerceIn(0f, 1f)
            val stackScale = (1f - depth * .075f) * pressScale
            scaleX = stackScale
            scaleY = stackScale
            alpha = if (hidden) 0f else 1f - depth * .14f
          }
          .onGloballyPositioned { bounds = it.boundsInRoot() }
          .then(clickModifier),
      shape = RoundedCornerShape(16.dp),
      color = Color.Black,
      shadowElevation = 0.dp,
    ) {
      Box(Modifier.fillMaxSize()) {
        CoverImage(
          coverUrl = bangumiCoverUrl(item.coverUrl, BangumiCoverVariant.NEW_HOT_CARD),
          contentDescription = item.title,
          modifier = Modifier.fillMaxSize(),
          shape = RoundedCornerShape(16.dp),
          enforceAspectRatio = false,
          contentScale = ContentScale.Crop,
          requestWidth = 640,
          requestHeight = 360,
          loadKey = "anime-hot-stack-${item.stableId}",
          // Register under the raw cover URL so the shared transition lookup hits this bitmap
          // instead of falling back to the black bridge.
          bitmapCacheKey = item.coverUrl,
          useOriginalSource = true,
        )
        Box(
          Modifier.fillMaxSize()
            .graphicsLayer { alpha = chromeAlpha.value }
            .background(
              Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .70f)))
            )
        )
        Text(
          item.title,
          modifier =
            Modifier.align(Alignment.BottomStart).padding(12.dp).graphicsLayer {
              alpha = chromeAlpha.value
            },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = Color.White,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

@Composable
private fun AnimeFollowingSection(
  state: BangumiFollowingUiState,
  accountMid: Long,
  hiddenItemId: String?,
  onLoadMore: () -> Unit,
  onOpen: (BangumiExploreItem, Rect) -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val latestLoadMore by rememberUpdatedState(onLoadMore)
  var displayedFollowing by remember { mutableStateOf(state.cards) }
  var enteringFollowingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
  var entryAnimationGeneration by remember { mutableIntStateOf(0) }
  var showBoundaryLoadingIndicator by remember { mutableStateOf(false) }
  val latestHasMore by rememberUpdatedState(state.hasMore)
  val latestLoadingMore by rememberUpdatedState(state.loadingMore)
  val latestDisplayedFollowing by rememberUpdatedState(displayedFollowing)
  LaunchedEffect(listState) {
    var wasScrolling = false
    var gestureStarted = false
    var requestedForCardCount = -1
    snapshotFlow {
        Triple(
          listState.firstVisibleItemIndex,
          listState.firstVisibleItemScrollOffset,
          listState.isScrollInProgress,
        )
      }
      .collect { (_, _, scrolling) ->
        if (scrolling && !wasScrolling) {
          gestureStarted = true
          requestedForCardCount = -1
        }
        val cards = latestDisplayedFollowing
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val shouldPrefetch =
          scrolling &&
            shouldAutoLoadMoreFollowing(
              userScrollStarted = gestureStarted,
              hasMore = latestHasMore,
              loadingMore = latestLoadingMore,
              lastVisibleIndex = lastVisibleIndex,
              lastCardIndex = cards.lastIndex,
              prefetchDistanceCards = FOLLOWING_PREFETCH_DISTANCE_CARDS,
            )
        if (shouldPrefetch && requestedForCardCount != cards.size) {
          requestedForCardCount = cards.size
          latestLoadMore()
        }
        if (!scrolling && wasScrolling && gestureStarted) {
          val reachedBoundary =
            cards.isNotEmpty() && lastVisibleIndex >= cards.lastIndex && latestHasMore
          if (reachedBoundary) {
            // Slow scrolling normally starts the request several cards earlier and keeps the tail
            // visually quiet. Only expose a compact spinner when the gesture actually reaches the
            // end before that request has supplied more cards.
            showBoundaryLoadingIndicator = true
            if (!latestLoadingMore && requestedForCardCount != cards.size) {
              requestedForCardCount = cards.size
              latestLoadMore()
            }
          }
          gestureStarted = false
        }
        wasScrolling = scrolling
      }
  }
  LaunchedEffect(listState) {
    var dragStarted = false
    listState.interactionSource.interactions.collect { interaction ->
      when (interaction) {
        is DragInteraction.Start -> dragStarted = true
        is DragInteraction.Stop,
        is DragInteraction.Cancel -> {
          if (!dragStarted) return@collect
          dragStarted = false
          val cards = latestDisplayedFollowing
          val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
          val reachedBoundary =
            cards.isNotEmpty() && lastVisibleIndex >= cards.lastIndex && latestHasMore
          if (reachedBoundary) showBoundaryLoadingIndicator = true
          if (
            shouldAutoLoadMoreFollowing(
              userScrollStarted = true,
              hasMore = latestHasMore,
              loadingMore = latestLoadingMore,
              lastVisibleIndex = lastVisibleIndex,
              lastCardIndex = cards.lastIndex,
              prefetchDistanceCards = FOLLOWING_PREFETCH_DISTANCE_CARDS,
            )
          ) {
            latestLoadMore()
          }
        }
        else -> Unit
      }
    }
  }
  LaunchedEffect(state.loadingMore) {
    if (!state.loadingMore) showBoundaryLoadingIndicator = false
  }
  LaunchedEffect(state.cards, listState.isScrollInProgress) {
    val canCommitWhileScrolling =
      canCommitFollowingCardsDuringScroll(displayedFollowing, state.cards)
    if (
      (!listState.isScrollInProgress || canCommitWhileScrolling) &&
        state.cards != displayedFollowing
    ) {
      val previousKeys = displayedFollowing.mapTo(mutableSetOf(), ::followingCardKey)
      enteringFollowingKeys =
        if (listState.isScrollInProgress) emptySet()
        else state.cards.map(::followingCardKey).filterNot(previousKeys::contains).toSet()
      val reorderSeasonId = state.reorderingSeasonId
      val movingExistingCardToFront =
        reorderSeasonId != null &&
          displayedFollowing.indexOfFirst { it.seasonId == reorderSeasonId } > 0 &&
          state.cards.firstOrNull()?.seasonId == reorderSeasonId
      if (
        movingExistingCardToFront &&
          (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
      ) {
        // Disable LazyRow's old-key viewport anchoring for this remeasure. The reordered card's
        // destination is now the visible left edge instead of an off-screen global index zero.
        listState.requestScrollToItem(0)
      }
      displayedFollowing = state.cards
      entryAnimationGeneration += 1
    }
  }
  LaunchedEffect(entryAnimationGeneration) {
    if (entryAnimationGeneration <= 0) return@LaunchedEffect
    val generation = entryAnimationGeneration
    delay(520)
    if (entryAnimationGeneration == generation) enteringFollowingKeys = emptySet()
  }
  val showingPlaceholders =
    shouldShowFollowingPlaceholders(
      following = displayedFollowing,
      loading = state.loading,
      refreshing = state.refreshing,
    )
  when {
    showingPlaceholders ->
      Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExploreSectionHeading("正在追")
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          items(FOLLOWING_PLACEHOLDER_COUNT, key = { index -> "following-placeholder-$index" }) {
            AnimeFollowingPlaceholderCard()
          }
        }
      }
    displayedFollowing.isNotEmpty() ->
      Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExploreSectionHeading("正在追")
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          items(displayedFollowing, key = ::followingCardKey) { card ->
            val cardKey = followingCardKey(card)
            val entering = cardKey in enteringFollowingKeys
            val entryProgress = remember(cardKey) { Animatable(if (entering) 0f else 1f) }
            LaunchedEffect(entering, entryAnimationGeneration) {
              if (entering) {
                entryProgress.snapTo(0f)
                entryProgress.animateTo(1f, tween(300))
              } else if (entryProgress.value < 1f) {
                entryProgress.snapTo(1f)
              }
            }
            AnimeFollowingCard(
              card = card,
              hidden = "bangumi-explore-${card.id}" == hiddenItemId,
              onOpen = onOpen,
              modifier =
                Modifier.zIndex(
                    if (entering || card.seasonId == state.reorderingSeasonId) 1f else 0f
                  )
                  .animateItem()
                  .graphicsLayer {
                    alpha = entryProgress.value
                    translationX = -FOLLOWING_CARD_WIDTH.toPx() * (1f - entryProgress.value)
                  },
            )
          }
          if (state.loadingMore || state.hasMore) {
            item(key = "following-tail") {
              Box(
                Modifier.width(148.dp).height(FOLLOWING_CARD_HEIGHT),
                contentAlignment = Alignment.Center,
              ) {
                when {
                  state.loadingMore && showBoundaryLoadingIndicator ->
                  CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                  !state.loadingMore ->
                    TextButton(
                      onClick = {
                        showBoundaryLoadingIndicator = true
                        latestLoadMore()
                      },
                      modifier = Modifier.fillMaxSize(),
                    ) {
                      Text("加载更多")
                    }
                }
              }
            }
          }
        }
      }
    accountMid <= 0L ->
      Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f),
        shape = RoundedCornerShape(16.dp),
      ) {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("正在追", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
          Text(
            "登录后查看追番更新",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
  }
}

private val FOLLOWING_CARD_WIDTH = 276.dp
private val FOLLOWING_CARD_HEIGHT = 238.dp
private const val FOLLOWING_PLACEHOLDER_COUNT = 5
private const val FOLLOWING_PREFETCH_DISTANCE_CARDS = 3

internal fun shouldShowFollowingPlaceholders(
  following: List<SpaceContentCard>,
  loading: Boolean,
  refreshing: Boolean,
): Boolean = following.isEmpty() && (loading || refreshing)

internal fun shouldAutoLoadMoreFollowing(
  userScrollStarted: Boolean,
  hasMore: Boolean,
  loadingMore: Boolean,
  lastVisibleIndex: Int,
  lastCardIndex: Int,
  prefetchDistanceCards: Int = FOLLOWING_PREFETCH_DISTANCE_CARDS,
): Boolean =
  userScrollStarted &&
    hasMore &&
    !loadingMore &&
    lastCardIndex >= 0 &&
    lastVisibleIndex >= lastCardIndex - prefetchDistanceCards.coerceAtLeast(0)

internal fun canCommitFollowingCardsDuringScroll(
  current: List<SpaceContentCard>,
  updated: List<SpaceContentCard>,
): Boolean {
  if (updated.size < current.size) return false
  return current.indices.all { index ->
    followingCardKey(current[index]) == followingCardKey(updated[index])
  }
}

private fun followingCardKey(card: SpaceContentCard): String =
  card.seasonId.takeIf { it > 0L }?.let { "following-season-$it" } ?: card.id

@Composable
private fun AnimeFollowingPlaceholderCard() {
  Surface(
    modifier = Modifier.width(FOLLOWING_CARD_WIDTH).height(FOLLOWING_CARD_HEIGHT),
    shape = VideoShapeTokens.Card,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
  ) {
    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(
        Modifier.fillMaxWidth()
          .aspectRatio(16f / 9f)
          .clip(VideoShapeTokens.Player)
          .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
      )
      Box(
        Modifier.fillMaxWidth(0.82f)
          .height(16.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
      )
      Box(
        Modifier.fillMaxWidth(0.56f)
          .height(14.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
      )
      Box(
        Modifier.fillMaxWidth()
          .height(3.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
      )
    }
  }
}

@Composable
private fun AnimeFollowingCard(
  card: SpaceContentCard,
  hidden: Boolean,
  onOpen: (BangumiExploreItem, Rect) -> Unit,
  modifier: Modifier = Modifier,
) {
  val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
  val scope = rememberCoroutineScope()
  val item =
    remember(card) {
      BangumiExploreItem(
        stableId = card.id,
        title = card.title,
        subtitle = card.subtitle,
        coverUrl = card.coverUrl,
        targetUrl = card.videoUrl,
        seasonId = card.seasonId,
        episodeId = card.episodeId,
        style = BangumiExploreCardStyle.LANDSCAPE,
        watchProgress = card.watchProgress,
        seasonType = card.seasonType,
        hasHistory = card.hasHistory,
        historicalOnly = card.historicalOnly,
      )
    }
  var bounds by remember(card.id) { mutableStateOf(Rect.Zero) }
  val progress = card.watchProgress
  val subtitleText =
    buildFollowingSubtitle(
      progress = progress,
      progressState = card.watchProgressState,
      hasHistory = card.hasHistory,
      historicalOnly = card.historicalOnly,
      fallback = card.subtitle,
    )
  val progressPercent = progress?.percent?.coerceIn(0, 100)
  VideoCardGradient(
    coverUrl = card.coverUrl,
    loadKey = "anime-following-${card.id}",
    modifier =
      modifier
        .width(FOLLOWING_CARD_WIDTH)
        .height(FOLLOWING_CARD_HEIGHT)
        .navigationBringIntoViewTarget(bringIntoViewRequester)
        .clip(VideoShapeTokens.Card)
        .clickable(enabled = card.videoUrl.isNotBlank()) {
          scope.launch {
            bringIntoViewRequester.bringIntoView()
            withFrameNanos {}
            onOpen(item, bounds)
          }
        },
  ) {
    Column(Modifier.padding(8.dp)) {
      CoverImage(
        coverUrl = bangumiCoverUrl(card.coverUrl, BangumiCoverVariant.HORIZONTAL_CARD),
        contentDescription = card.title,
        // Only the cover is hidden during the shared flight (matching the feed card contract);
        // the gradient background, title, subtitle and progress stay visible.
        modifier =
          Modifier.fillMaxWidth()
            .aspectRatio(16f / 9f)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .then(if (hidden) Modifier.graphicsLayer { alpha = 0f } else Modifier),
        // This inner cover is the element captured by the shared transition. Keep its radius
        // aligned with the player and the surrounding following-card surface.
        shape = VideoShapeTokens.Player,
        enforceAspectRatio = false,
        contentScale = ContentScale.Crop,
        requestWidth = 640,
        requestHeight = 360,
        loadKey = "anime-following-cover-${card.id}",
        bitmapCacheKey = card.coverUrl,
        useOriginalSource = true,
        retainBitmap = true,
      )
      Text(
        card.title,
        modifier = Modifier.padding(top = 8.dp),
        maxLines = 2,
        minLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        subtitleText,
        maxLines = 1,
        minLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
      Box(
        Modifier.fillMaxWidth()
          .height(7.dp)
          .padding(top = 4.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
      ) {
        if (progressPercent != null) {
          Box(
            Modifier.fillMaxHeight()
              .fillMaxWidth(fraction = progressPercent / 100f)
              .background(MaterialTheme.colorScheme.primary)
          )
        }
      }
    }
  }
}

/** Build the subtitle without mistaking an unavailable progress response for no viewing record. */
internal fun buildFollowingSubtitle(
  progress: BangumiWatchProgress?,
  progressState: BangumiWatchProgressState,
  hasHistory: Boolean,
  historicalOnly: Boolean,
  fallback: String = "",
): String {
  if (progress == null || progress.episodeId <= 0L) {
    return when {
      historicalOnly || hasHistory -> "历史观看"
      progressState == BangumiWatchProgressState.NO_RECORD -> "尚未观看"
      else -> fallback.ifBlank { "追番中" }
    }
  }
  val episodeIndex = progress.episodeIndex.ifBlank { "第${progress.episodeId}话" }
  val prefix = episodeIndex.toIntOrNull()?.let { "第${it}话" } ?: episodeIndex
  val percent = progress.percent?.coerceIn(0, 100)
  return if (percent != null) "看到$prefix · ${percent}%" else "看到$prefix"
}

@Composable
private fun AnimeRankingPoster(
  item: BangumiExploreItem,
  rank: Int,
  hidden: Boolean,
  foregroundActive: Boolean,
  onOpen: (BangumiExploreItem, Rect) -> Unit,
) {
  var bounds by remember(item.stableId) { mutableStateOf(Rect.Zero) }
  val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
  val scope = rememberCoroutineScope()
  var opening by remember(item.stableId) { mutableStateOf(false) }
  val chromeAlpha = remember(item.stableId) { Animatable(1f) }
  LaunchedEffect(hidden, foregroundActive) {
    if (opening && !hidden && foregroundActive) {
      opening = false
      chromeAlpha.animateTo(1f, tween(MotionTokens.Standard))
    }
  }
  Box(
    Modifier.fillMaxWidth()
      .aspectRatio(3f / 4f)
      .navigationBringIntoViewTarget(bringIntoViewRequester)
      .clip(RoundedCornerShape(16.dp))
      .clickable(enabled = !opening && !hidden) {
        opening = true
        scope.launch {
          bringIntoViewRequester.bringIntoView()
          chromeAlpha.animateTo(0f, tween(MotionTokens.Standard))
          withFrameNanos {}
          onOpen(item, bounds)
        }
      }
  ) {
    CoverImage(
      coverUrl = bangumiCoverUrl(item.coverUrl, BangumiCoverVariant.POSTER),
      contentDescription = item.title,
      modifier =
        Modifier.fillMaxSize()
          .onGloballyPositioned { bounds = it.boundsInRoot() }
          .graphicsLayer { alpha = if (hidden) 0f else 1f },
      shape = RoundedCornerShape(16.dp),
      enforceAspectRatio = false,
      contentScale = ContentScale.Crop,
      requestWidth = 480,
      requestHeight = 640,
      loadKey = "anime-rank-${item.stableId}",
      bitmapCacheKey = item.coverUrl,
      useOriginalSource = true,
      retainBitmap = true,
    )
    Box(
      Modifier.fillMaxSize()
        .graphicsLayer { alpha = chromeAlpha.value }
        .background(
          Brush.verticalGradient(
            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .78f))
          )
        )
    )
    Surface(
      modifier =
        Modifier.align(Alignment.TopStart).padding(8.dp).graphicsLayer {
          alpha = chromeAlpha.value
        },
      color =
        if (rank <= 3) MaterialTheme.colorScheme.primary.copy(alpha = .92f)
        else Color.Black.copy(alpha = .62f),
      shape = RoundedCornerShape(9.dp),
    ) {
      Text(
        "TOP $rank",
        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        color = if (rank <= 3) MaterialTheme.colorScheme.onPrimary else Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
      )
    }
    Column(
      Modifier.align(Alignment.BottomStart).padding(12.dp).graphicsLayer {
        alpha = chromeAlpha.value
      },
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        item.title,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      if (item.subtitle.isNotBlank()) {
        Text(
          item.subtitle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = Color.White.copy(alpha = .82f),
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }
  }
}

@Composable
private fun AnimeRecommendationPoster(
  item: BangumiExploreItem,
  hidden: Boolean,
  foregroundActive: Boolean,
  onOpen: (BangumiExploreItem, Rect) -> Unit,
) {
  var bounds by remember(item.stableId) { mutableStateOf(Rect.Zero) }
  val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
  val scope = rememberCoroutineScope()
  var opening by remember(item.stableId) { mutableStateOf(false) }
  val chromeAlpha = remember(item.stableId) { Animatable(1f) }
  LaunchedEffect(hidden, foregroundActive) {
    if (opening && !hidden && foregroundActive) {
      opening = false
      chromeAlpha.animateTo(1f, tween(MotionTokens.Standard))
    }
  }
  Box(
    Modifier.fillMaxWidth()
      .aspectRatio(3f / 4f)
      .navigationBringIntoViewTarget(bringIntoViewRequester)
      .clip(RoundedCornerShape(16.dp))
      .clickable(enabled = !opening && !hidden) {
        opening = true
        scope.launch {
          bringIntoViewRequester.bringIntoView()
          chromeAlpha.animateTo(0f, tween(MotionTokens.Standard))
          withFrameNanos {}
          onOpen(item, bounds)
        }
      }
  ) {
    CoverImage(
      coverUrl = bangumiCoverUrl(item.coverUrl, BangumiCoverVariant.POSTER),
      contentDescription = item.title,
      modifier =
        Modifier.fillMaxSize()
          .onGloballyPositioned { bounds = it.boundsInRoot() }
          .graphicsLayer { alpha = if (hidden) 0f else 1f },
      shape = RoundedCornerShape(16.dp),
      enforceAspectRatio = false,
      contentScale = ContentScale.Crop,
      requestWidth = 480,
      requestHeight = 640,
      loadKey = "anime-recommend-${item.stableId}",
      bitmapCacheKey = item.coverUrl,
      useOriginalSource = true,
      retainBitmap = true,
    )
    Box(
      Modifier.fillMaxSize()
        .graphicsLayer { alpha = chromeAlpha.value }
        .background(
          Brush.verticalGradient(
            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .78f))
          )
        )
    )
    item.rating
      ?.takeIf { it > 0.0 }
      ?.let { rating ->
      Surface(
        modifier =
            Modifier.align(Alignment.TopStart).padding(8.dp).graphicsLayer {
              alpha = chromeAlpha.value
            },
        color = MaterialTheme.colorScheme.primary.copy(alpha = .92f),
        shape = RoundedCornerShape(9.dp),
      ) {
        Text(
          String.format(Locale.US, "%.1f", rating),
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
          color = MaterialTheme.colorScheme.onPrimary,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    Column(
      Modifier.align(Alignment.BottomStart).padding(12.dp).graphicsLayer {
        alpha = chromeAlpha.value
      },
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        item.title,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      if (item.subtitle.isNotBlank()) {
        Text(
          item.subtitle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = Color.White.copy(alpha = .82f),
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }
  }
}

/**
 * Grid body for the five non-anime categories. It shares the anime page's metrics, pull-to-collapse
 * and shared-cover transition, but replaces the placeholder horizontal rows with a differentiated
 * layout: discover categories lead with a single [ExploreFocusBanner], 国创 keeps its following rail
 * and timeline, and every category converges on the shared ranking / recommendation posters.
 */
@Composable
private fun ExploreCategoryGridContent(
  category: BangumiExploreCategory,
  page: BangumiExplorePage,
  contentTopPadding: Dp,
  hiddenItemId: String?,
  foregroundActive: Boolean,
  onExplorePull: (Float) -> Unit,
  onExplorePullRelease: (Float) -> Unit,
  onOpenLandscape: (BangumiExploreItem, Rect) -> Unit,
  onOpenPoster: (BangumiExploreItem, Rect) -> Unit,
) {
  val gridState = rememberLazyGridState()
  val latestExplorePull by rememberUpdatedState(onExplorePull)
  val latestExplorePullRelease by rememberUpdatedState(onExplorePullRelease)
  val handoffSlop = with(LocalDensity.current) { 12.dp.toPx() }
  var pullDistance by remember(category) { mutableFloatStateOf(0f) }
  val pullToCollapseConnection =
    remember(gridState, handoffSlop) {
      object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
          if (available.y > 0f && !gridState.canScrollBackward) {
            val previous = pullDistance
            pullDistance += available.y
            val handedBefore = (previous - handoffSlop).coerceAtLeast(0f)
            val handedAfter = (pullDistance - handoffSlop).coerceAtLeast(0f)
            val handoff = handedAfter - handedBefore
            if (handoff > 0f) {
              latestExplorePull(handoff)
              return Offset(0f, handoff)
            }
          } else if (available.y < 0f && pullDistance > handoffSlop) {
            val retract = minOf(-available.y, pullDistance - handoffSlop)
            pullDistance -= retract
            latestExplorePull(-retract)
            return Offset(0f, -retract)
          } else if (available.y < 0f) {
            pullDistance = 0f
          }
          return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
          if (pullDistance > handoffSlop) {
            latestExplorePullRelease(available.y)
            pullDistance = 0f
          }
          return Velocity.Zero
        }
      }
    }

  // Feed-like modules merge into a single recommendation grid rendered last behind a boundary band.
  val contentSections =
    page.sections.filterNot {
      it.kind == BangumiExploreSectionKind.FEED ||
        it.kind == BangumiExploreSectionKind.RECOMMENDATION
    }
  val feedItems =
    page.sections
      .filter {
        it.kind == BangumiExploreSectionKind.FEED ||
          it.kind == BangumiExploreSectionKind.RECOMMENDATION
      }
      .flatMap { it.items }
      .distinctBy(BangumiExploreItem::stableId)
  val feedTitle =
    page.sections
      .firstOrNull {
        it.kind == BangumiExploreSectionKind.FEED ||
          it.kind == BangumiExploreSectionKind.RECOMMENDATION
      }
      ?.title
      ?.takeIf { it.isNotBlank() } ?: "猜你喜欢"
  var feedVisibleCount by remember(page) { mutableIntStateOf(10) }
  val visibleFeed = feedItems.take(feedVisibleCount)

  LaunchedEffect(gridState, feedVisibleCount, feedItems.size) {
    snapshotFlow {
      val layoutInfo = gridState.layoutInfo
      val lastVisible = layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
      layoutInfo.totalItemsCount > 0 && lastVisible >= layoutInfo.totalItemsCount - 2
      }
      .collect { nearEnd ->
      if (nearEnd && feedVisibleCount < feedItems.size) {
        feedVisibleCount = (feedVisibleCount + 10).coerceAtMost(feedItems.size)
      }
    }
  }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = if (maxWidth >= 900.dp) GridCells.Fixed(5) else GridCells.Adaptive(140.dp)
    LazyVerticalGrid(
      columns = columns,
      state = gridState,
      modifier = Modifier.fillMaxSize().nestedScroll(pullToCollapseConnection),
      contentPadding =
        PaddingValues(start = 28.dp, top = contentTopPadding, end = 28.dp, bottom = 118.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      contentSections.forEach { section ->
        when (section.kind) {
          BangumiExploreSectionKind.HOT -> {
            // Discover categories lead with one cinematic focus banner. Its right-side 16:9 cover
            // is
            // the shared-flight source; the wide backdrop itself never flies.
            if (section.items.isNotEmpty()) {
              item(key = section.stableId + ":focus", span = { GridItemSpan(maxLineSpan) }) {
                ExploreFocusBanner(
                  item = section.items.first(),
                  kicker = section.title,
                  hidden = "bangumi-explore-${section.items.first().stableId}" == hiddenItemId,
                  foregroundActive = foregroundActive,
                  onOpen = onOpenLandscape,
                )
              }
            }
            if (section.items.size > 1) {
              // The banner's kicker already labels this module, so the remaining focus items follow
              // without a redundant heading.
              gridItemsIndexed(
                section.items.drop(1),
                key = { _, item -> section.stableId + ":hot:" + item.stableId },
              ) { _, item ->
                ExploreLandscapeCard(
                  item = item,
                  hidden = "bangumi-explore-${item.stableId}" == hiddenItemId,
                  onOpen = onOpenLandscape,
                )
              }
            }
          }
          BangumiExploreSectionKind.RANKING -> {
            item(key = section.stableId + ":head", span = { GridItemSpan(maxLineSpan) }) {
              ExploreSectionHeading(section.title)
            }
            gridItemsIndexed(
              section.items,
              key = { _, item -> section.stableId + ":rank:" + item.stableId },
            ) { index, item ->
              AnimeRankingPoster(
                item = item,
                rank = index + 1,
                hidden = "bangumi-explore-${item.stableId}" == hiddenItemId,
                foregroundActive = foregroundActive,
                onOpen = onOpenPoster,
              )
            }
          }
          BangumiExploreSectionKind.TIMELINE -> {
            item(key = section.stableId + ":head", span = { GridItemSpan(maxLineSpan) }) {
              ExploreSectionHeading(section.title.ifBlank { "本周更新" })
            }
            gridItemsIndexed(
              section.items,
              key = { _, item -> section.stableId + ":timeline:" + item.stableId },
            ) { _, item ->
              AnimeRecommendationPoster(
                item = item,
                hidden = "bangumi-explore-${item.stableId}" == hiddenItemId,
                foregroundActive = foregroundActive,
                onOpen = onOpenPoster,
              )
            }
          }
          else -> {
            // OTHER (专题 / 厂牌 / 运营): render as a poster or landscape grid by item style.
            val poster = section.items.firstOrNull()?.style == BangumiExploreCardStyle.POSTER
            item(key = section.stableId + ":head", span = { GridItemSpan(maxLineSpan) }) {
              ExploreSectionHeading(section.title)
            }
            gridItemsIndexed(
              section.items,
              key = { _, item -> section.stableId + ":other:" + item.stableId },
            ) { _, item ->
              if (poster) {
                AnimeRecommendationPoster(
                  item = item,
                  hidden = "bangumi-explore-${item.stableId}" == hiddenItemId,
                  foregroundActive = foregroundActive,
                  onOpen = onOpenPoster,
                )
              } else {
                ExploreLandscapeCard(
                  item = item,
                  hidden = "bangumi-explore-${item.stableId}" == hiddenItemId,
                  onOpen = onOpenLandscape,
                )
              }
            }
          }
        }
      }

      if (visibleFeed.isNotEmpty()) {
        item(key = "${category.apiName}:feed-boundary", span = { GridItemSpan(maxLineSpan) }) {
          AnimeRecommendationBoundary(
            title = feedTitle,
            subtitle = "为你精选的${category.label}",
          )
        }
        gridItemsIndexed(
          visibleFeed,
          key = { _, item -> "${category.apiName}:feed:" + item.stableId },
        ) { _, item ->
          AnimeRecommendationPoster(
            item = item,
            hidden = "bangumi-explore-${item.stableId}" == hiddenItemId,
            foregroundActive = foregroundActive,
            onOpen = onOpenPoster,
          )
        }
      }
    }
  }
}

/**
 * A cinematic focus banner for discover categories. Like the anime hero, the wide artwork is only a
 * backdrop: the shared-flight source is the 16:9 cover card on its trailing edge, which flies to
 * the detail player. The backdrop itself never transforms into the player cover.
 */
@Composable
private fun ExploreFocusBanner(
  item: BangumiExploreItem,
  kicker: String,
  hidden: Boolean,
  foregroundActive: Boolean,
  onOpen: (BangumiExploreItem, Rect) -> Unit,
) {
  var cardBounds by remember(item.stableId) { mutableStateOf(Rect.Zero) }
  val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
  val scope = rememberCoroutineScope()
  var opening by remember(item.stableId) { mutableStateOf(false) }
  val chromeAlpha = remember(item.stableId) { Animatable(1f) }
  val interactionSource = remember(item.stableId) { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val pressScale by
    animateFloatAsState(
      targetValue = if (pressed) MotionTokens.PressedScale else 1f,
      animationSpec = tween(MotionTokens.Quick),
      label = "exploreFocusPress",
    )
  LaunchedEffect(hidden, foregroundActive) {
    if (opening && !hidden && foregroundActive) {
      opening = false
      chromeAlpha.animateTo(1f, tween(MotionTokens.Standard))
    }
  }
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val bannerHeight = (maxWidth * 9f / 21f).coerceIn(200.dp, 320.dp)
    val cardEndPadding = 20.dp
    val cardHeight = bannerHeight - 40.dp
    val cardWidth = cardHeight * 16f / 9f
    val infoEndPadding = cardWidth + cardEndPadding + 24.dp
    Box(
      Modifier.fillMaxWidth()
        .height(bannerHeight)
        .navigationBringIntoViewTarget(bringIntoViewRequester)
        .clip(RoundedCornerShape(24.dp))
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          enabled = !opening && !hidden,
        ) {
          opening = true
          scope.launch {
            bringIntoViewRequester.bringIntoView()
            chromeAlpha.animateTo(0f, tween(MotionTokens.Standard))
            withFrameNanos {}
            onOpen(item, cardBounds)
          }
        }
    ) {
      // Wide backdrop. It stays put during the shared flight and is covered by the transition's
      // background takeover; only the trailing 16:9 cover flies to the detail player.
      CoverImage(
        coverUrl = bangumiOriginalImageUrl(item.heroCoverUrl),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        enforceAspectRatio = false,
        contentScale = ContentScale.Crop,
        requestWidth = 1600,
        requestHeight = 900,
        loadKey = "explore-focus-bg-${item.stableId}",
        useOriginalSource = true,
      )
      // A bottom gradient keeps the overlaid info readable over the backdrop.
      Box(
        Modifier.fillMaxSize()
          .graphicsLayer { alpha = chromeAlpha.value }
          .background(
            Brush.verticalGradient(
              listOf(
                Color.Transparent,
                Color.Black.copy(alpha = .24f),
                Color.Black.copy(alpha = .74f),
              )
            )
          )
      )
      Column(
        Modifier.align(Alignment.BottomStart)
          .padding(start = 28.dp, end = infoEndPadding, bottom = 24.dp)
          .graphicsLayer { alpha = chromeAlpha.value },
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (kicker.isNotBlank()) {
          Text(
            kicker,
            color = Color.White.copy(alpha = .86f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }
        Text(
          item.title,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          color = Color.White,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
        )
        if (item.subtitle.isNotBlank()) {
          Text(
            item.subtitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = .84f),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        if (item.rating != null || item.ratingCount > 0L) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            item.rating
              ?.takeIf { it > 0.0 }
              ?.let { rating ->
              Text(
                "评分 ${String.format(Locale.US, "%.1f", rating)}",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
            }
            item.ratingCount
              .takeIf { it > 0L }
              ?.let { count ->
              Text(
                "${formatCompactCount(count)} 人评分",
                color = Color.White.copy(alpha = .82f),
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
        }
        Text(
          "查看详情",
          color = Color.White.copy(alpha = .94f),
          style = MaterialTheme.typography.labelLarge,
        )
      }
      // Trailing 16:9 cover card — the shared-flight source. It registers under the raw cover URL
      // so
      // the LANDSCAPE transition flies this bitmap to the player (same contract as following
      // cards).
      Surface(
        modifier =
          Modifier.align(Alignment.CenterEnd)
            .padding(end = cardEndPadding)
            .width(cardWidth)
            .height(cardHeight)
            .graphicsLayer {
              scaleX = pressScale
              scaleY = pressScale
            }
            .onGloballyPositioned { cardBounds = it.boundsInRoot() },
        shape = VideoShapeTokens.Player,
        color = Color.Black,
        shadowElevation = 0.dp,
      ) {
        CoverImage(
          coverUrl = bangumiCoverUrl(item.coverUrl, BangumiCoverVariant.HORIZONTAL_CARD),
          contentDescription = item.title,
          modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (hidden) 0f else 1f },
          shape = VideoShapeTokens.Player,
          enforceAspectRatio = false,
          contentScale = ContentScale.Crop,
          requestWidth = 640,
          requestHeight = 360,
          loadKey = "explore-focus-card-${item.stableId}",
          bitmapCacheKey = item.coverUrl,
          useOriginalSource = true,
          retainBitmap = true,
        )
      }
    }
  }
}

/** A 16:9 grid card for landscape (焦点 / 专题 / 运营) items; flies its cover to the detail player. */
@Composable
private fun ExploreLandscapeCard(
  item: BangumiExploreItem,
  hidden: Boolean,
  onOpen: (BangumiExploreItem, Rect) -> Unit,
) {
  var bounds by remember(item.stableId) { mutableStateOf(Rect.Zero) }
  val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
  val scope = rememberCoroutineScope()
  Column(
    Modifier.fillMaxWidth()
      .navigationBringIntoViewTarget(bringIntoViewRequester)
      .clip(VideoShapeTokens.Player)
      .clickable {
        scope.launch {
          bringIntoViewRequester.bringIntoView()
          withFrameNanos {}
          onOpen(item, bounds)
        }
      }
  ) {
    Box(
      Modifier.fillMaxWidth()
        .aspectRatio(16f / 9f)
        // Landscape covers share the player corner radius so the flying cover lands flush.
        .clip(VideoShapeTokens.Player)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .onGloballyPositioned { bounds = it.boundsInRoot() }
    ) {
      CoverImage(
        coverUrl = bangumiCoverUrl(item.coverUrl, BangumiCoverVariant.HORIZONTAL_CARD),
        contentDescription = item.title,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (hidden) 0f else 1f },
        shape = VideoShapeTokens.Player,
        enforceAspectRatio = false,
        contentScale = ContentScale.Crop,
        requestWidth = 640,
        requestHeight = 360,
        loadKey = "explore-landscape-${item.stableId}",
        bitmapCacheKey = item.coverUrl,
        useOriginalSource = true,
        retainBitmap = true,
      )
      item.rating
        ?.takeIf { it > 0.0 }
        ?.let { rating ->
        Surface(
          modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
          color = Color.Black.copy(alpha = .52f),
          shape = RoundedCornerShape(9.dp),
        ) {
          Text(
            String.format(Locale.US, "%.1f", rating),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
    Text(
      item.title,
      modifier = Modifier.padding(top = 8.dp),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
    )
    if (item.subtitle.isNotBlank()) {
      Text(
        item.subtitle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

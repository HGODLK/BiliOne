package dev.openbili.webdemo.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.openbili.webdemo.api.BangumiCoverVariant
import dev.openbili.webdemo.api.BangumiExploreCardStyle
import dev.openbili.webdemo.api.BangumiExploreCategory
import dev.openbili.webdemo.api.BangumiExploreItem
import dev.openbili.webdemo.api.BangumiExplorePage
import dev.openbili.webdemo.api.BangumiWatchProgress
import dev.openbili.webdemo.api.BangumiWatchProgressState
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.bangumiCoverUrl
import dev.openbili.webdemo.api.bangumiOriginalImageUrl
import dev.openbili.webdemo.bangumi.BangumiFollowingUiState
import dev.openbili.webdemo.bangumi.BangumiExploreFollowingScrollAnchor
import dev.openbili.webdemo.bangumi.BangumiExploreSourceBounds
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.video.formatCompactCount
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AnimeExploreContent(
  category: BangumiExploreCategory = BangumiExploreCategory.ANIME,
  page: BangumiExplorePage,
  gridState: LazyGridState,
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
  controlMode: Boolean = false,
  controlLevel: BangumiControlLevel = BangumiControlLevel.ROOT,
  contentFocusRequester: FocusRequester? = null,
  heroFocusRequester: FocusRequester? = null,
  returnFocusItemId: String? = null,
  returnItemFocusRequester: FocusRequester? = null,
  followingScrollAnchor: BangumiExploreFollowingScrollAnchor? = null,
  followingFocusRegistry: BangumiFollowingFocusRegistry,
  onUpdateReturnAnchorFollowingScroll:
    (String, BangumiExploreFollowingScrollAnchor) -> Unit = { _, _ -> },
  onUpdateReturnAnchorSourceBounds: (String, BangumiExploreSourceBounds) -> Unit = { _, _ -> },
  onControlLevelChanged: (BangumiControlLevel) -> Unit = {},
  controlTargetActive: Boolean = false,
) {
  val groups = remember(page) { animeExploreContentGroups(page) }
  val focusManager = LocalFocusManager.current
  var recommendationVisibleCount by remember(page) { mutableIntStateOf(10) }
  val visibleRecommendations = groups.recommendations.take(recommendationVisibleCount)
  val latestExplorePull by rememberUpdatedState(onExplorePull)
  val latestExplorePullRelease by rememberUpdatedState(onExplorePullRelease)
  val handoffSlop = with(LocalDensity.current) { 12.dp.toPx() }
  var pullDistance by remember { mutableFloatStateOf(0f) }
  // 把头图卡片分页器的状态提升到这里，让嵌套滚动连接能直接读取 isScrollInProgress。
  // 与 pointerInput 设置的标志不同，isScrollInProgress 在分页器的惯性滑动/稳定阶段
  // （手指抬起后）保持为 true，防止下拉折叠偷走惯性滑动的滚动增量。读取它是一次
  // 快照读 —— 在同一输入阶段内即时生效，没有重组延迟。
  val heroItems = groups.hot
  val midpoint = Int.MAX_VALUE / 2
  val returnHeroItemIndex =
    heroItems.indexOfFirst { it.stableId == returnFocusItemId }.coerceAtLeast(0)
  val heroInitialPage =
    remember(heroItems.size, returnHeroItemIndex) {
      if (heroItems.isEmpty()) {
        0
      } else {
        midpoint - Math.floorMod(midpoint, heroItems.size) + returnHeroItemIndex
      }
    }
  val heroPagerState =
    rememberPagerState(
      initialPage = heroInitialPage,
      pageCount = { if (heroItems.isEmpty()) 1 else Int.MAX_VALUE },
    )
  var previousControlLevel by remember { mutableStateOf(controlLevel) }
  LaunchedEffect(
    controlTargetActive,
    controlMode,
    controlLevel,
  ) {
    val levelChanged = previousControlLevel != controlLevel
    previousControlLevel = controlLevel
    if (
      !levelChanged ||
      !controlTargetActive ||
        !controlMode ||
        controlLevel !in
          setOf(BangumiControlLevel.EXPLORE_CONTENT, BangumiControlLevel.EXPLORE_HERO)
    ) {
      return@LaunchedEffect
    }
    focusManager.clearFocus(force = true)
    withFrameNanos {}
    val requester =
      if (controlLevel == BangumiControlLevel.EXPLORE_CONTENT) {
        if (heroItems.isEmpty()) {
          following.cards.firstOrNull()?.let {
            followingFocusRegistry.requester(category, it.id)
          } ?: contentFocusRequester
        } else {
          contentFocusRequester
        }
      } else {
        heroFocusRequester
      }
    requester?.requestFocusWithinFrames(maxFrames = 8)
  }
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
            controlMode = controlMode,
            controlLevel = controlLevel,
            contentFocusRequester = contentFocusRequester,
            heroFocusRequester = heroFocusRequester,
            returnFocusItemId = returnFocusItemId,
            returnItemFocusRequester = returnItemFocusRequester,
            category = category,
            followingScrollAnchor = followingScrollAnchor,
            followingFocusRegistry = followingFocusRegistry,
            onUpdateReturnAnchorFollowingScroll = onUpdateReturnAnchorFollowingScroll,
            onUpdateReturnAnchorSourceBounds = onUpdateReturnAnchorSourceBounds,
            onControlLevelChanged = onControlLevelChanged,
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
            controlMode = controlMode,
            controlLevel = controlLevel,
            entryFocusRequester = contentFocusRequester,
            returnFocusItemId = returnFocusItemId,
            returnItemFocusRequester = returnItemFocusRequester,
            category = category,
            followingScrollAnchor = followingScrollAnchor,
            followingFocusRegistry = followingFocusRegistry,
            onUpdateReturnAnchorFollowingScroll = onUpdateReturnAnchorFollowingScroll,
            onUpdateReturnAnchorSourceBounds = onUpdateReturnAnchorSourceBounds,
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
            onSourceBoundsChanged = onUpdateReturnAnchorSourceBounds,
            controlMode = controlMode,
            controlLevel = controlLevel,
            focusRequester =
              returnItemFocusRequester.takeIf { item.stableId == returnFocusItemId },
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
            onSourceBoundsChanged = onUpdateReturnAnchorSourceBounds,
            controlMode = controlMode,
            controlLevel = controlLevel,
            focusRequester =
              returnItemFocusRequester.takeIf { item.stableId == returnFocusItemId },
          )
        }
      }
    }
  }
}

@Composable
internal fun ExploreSectionHeading(title: String) {
  Text(
    title,
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.SemiBold,
  )
}

@Composable
internal fun AnimeRecommendationBoundary(
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
  controlMode: Boolean,
  controlLevel: BangumiControlLevel,
  contentFocusRequester: FocusRequester?,
  heroFocusRequester: FocusRequester?,
  returnFocusItemId: String?,
  returnItemFocusRequester: FocusRequester?,
  category: BangumiExploreCategory,
  followingScrollAnchor: BangumiExploreFollowingScrollAnchor?,
  followingFocusRegistry: BangumiFollowingFocusRegistry,
  onUpdateReturnAnchorFollowingScroll:
    (String, BangumiExploreFollowingScrollAnchor) -> Unit,
  onUpdateReturnAnchorSourceBounds: (String, BangumiExploreSourceBounds) -> Unit,
  onControlLevelChanged: (BangumiControlLevel) -> Unit,
) {
  Column(Modifier.fillMaxWidth()) {
    AnimeHotHeroContent(
      items = items,
      pagerState = pagerState,
      hiddenItemId = hiddenItemId,
      foregroundActive = foregroundActive,
      onOpen = onOpen,
      controlMode = controlMode,
      controlLevel = controlLevel,
      contentFocusRequester = contentFocusRequester,
      heroFocusRequester = heroFocusRequester,
      returnFocusItemId = returnFocusItemId,
      returnItemFocusRequester = returnItemFocusRequester,
      onControlLevelChanged = onControlLevelChanged,
    )
    AnimeFollowingSection(
      state = following,
      accountMid = accountMid,
      hiddenItemId = hiddenItemId,
      onLoadMore = onLoadMoreFollowing,
      onOpen = onOpen,
      controlMode = controlMode,
      controlLevel = controlLevel,
      returnFocusItemId = returnFocusItemId,
      returnItemFocusRequester = returnItemFocusRequester,
      category = category,
      followingScrollAnchor = followingScrollAnchor,
      followingFocusRegistry = followingFocusRegistry,
      onUpdateReturnAnchorFollowingScroll = onUpdateReturnAnchorFollowingScroll,
      onUpdateReturnAnchorSourceBounds = onUpdateReturnAnchorSourceBounds,
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
  controlMode: Boolean,
  controlLevel: BangumiControlLevel,
  contentFocusRequester: FocusRequester?,
  heroFocusRequester: FocusRequester?,
  returnFocusItemId: String?,
  returnItemFocusRequester: FocusRequester?,
  onControlLevelChanged: (BangumiControlLevel) -> Unit,
) {
  val selectedItem = items[Math.floorMod(pagerState.currentPage, items.size)]
  // 头图从不参与共享封面转场。打开时总是让选中的堆叠卡片飞行；头图信息只在该飞行
  // 之前淡出，并在返回封面落地（hiddenItemId 由根转场清除）之后淡回。
  var openRequestTick by remember { mutableStateOf(0) }
  var opening by remember { mutableStateOf(false) }
  val heroInfoAlpha = remember { Animatable(1f) }
  val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
  val scope = rememberCoroutineScope()

  fun moveHero(delta: Int) {
    if (pagerState.isScrollInProgress) return
    scope.launch {
      pagerState.animateScrollToPage((pagerState.currentPage + delta).coerceAtLeast(0))
      withFrameNanos {}
      runCatching { heroFocusRequester?.requestFocus() }
    }
  }

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
  // 只在返回封面已落地且 explore 页重新回到前台后恢复。hiddenItemId 也会在正向飞行
  // 落地时清除（详情盖住本页）；以 foregroundActive 为闸门让信息保持隐藏直到真正的
  // 返回，使它淡回而不是在详情页之下突然弹出。
  LaunchedEffect(hiddenItemId, foregroundActive) {
    if (opening && hiddenItemId == null && foregroundActive) {
      opening = false
      heroInfoAlpha.animateTo(1f, tween(MotionTokens.Standard))
    }
  }

  // 堆叠卡片共享播放器封面的 16:9 作品图（与推荐堆叠相同），因此共享返回飞行以
  // 相同的裁剪落地，而不是重新裁剪后的变形帧。信息轨的尾部内边距由相同宽度推导，
  // 让文本始终避开堆叠。
  val stackCardHeight = 184.dp
  val stackWidth = stackCardHeight * 16f / 9f
  val stackEndPadding = 24.dp
  val heroInfoEndPadding = stackWidth + stackEndPadding + 32.dp

  Box(
    Modifier.fillMaxWidth()
      .height(410.dp)
      .navigationBringIntoViewTarget(bringIntoViewRequester)
      .bangumiControllerFocus(
        focusRequester = contentFocusRequester,
        enabled = controlMode && controlLevel == BangumiControlLevel.EXPLORE_CONTENT,
        shape = RoundedCornerShape(24.dp),
        onFocused = { scope.launch { bringIntoViewRequester.bringIntoView() } },
        onKeyEvent = { event ->
          if (
            event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
          ) {
            if (
              event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                event.nativeKeyEvent.repeatCount == 0
            ) {
              onControlLevelChanged(BangumiControlLevel.EXPLORE_NAV)
            }
            true
          } else {
            false
          }
        },
        onConfirm = { onControlLevelChanged(BangumiControlLevel.EXPLORE_HERO) },
      )
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
        // 上游头图作品通常只有中等分辨率。轻微模糊加过扫描让这一限制看起来不像
        // 压缩噪声，同时仍隔离在此背景图层内。
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
        controlMode = controlMode,
        controlLevel = controlLevel,
        heroFocusRequester = heroFocusRequester,
        returnFocusRequester =
          returnItemFocusRequester.takeIf { item.stableId == returnFocusItemId },
        onHeroMove = ::moveHero,
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
  controlMode: Boolean,
  controlLevel: BangumiControlLevel,
  heroFocusRequester: FocusRequester?,
  returnFocusRequester: FocusRequester?,
  onHeroMove: (Int) -> Unit,
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
      // 与首页卡片契约一致：卡片静止时先淡出界面元素，然后把真实边界交给共享转场，
      // 只让封面飞行。
      chromeAlpha.animateTo(0f, tween(MotionTokens.Standard))
      withFrameNanos {}
      onOpen(bounds)
    }
  }

  LaunchedEffect(openRequestTick) {
    // 触发时机的护栏（已选中、未滚动、未在打开中）位于 requestOpen；每个被接受的
    // tick 必须完成其前奏，让头图级的打开状态不会被卡住。
    if (openRequestTick > 0 && selected) startOpenPrelude()
  }
  LaunchedEffect(hidden, foregroundActive) {
    // 只在返回封面落地并且 explore 页重新回到前台之后恢复界面元素。hidden 也会在
    // 正向飞行落地时翻回 false（详情盖住本页）；没有 foregroundActive 闸门，
    // 标题/渐变会在详情页之下悄然恢复，然后在返回时弹出而不是淡入。
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
          .bangumiControllerFocus(
            focusRequester = (returnFocusRequester ?: heroFocusRequester).takeIf { selected },
            enabled = controlMode && controlLevel == BangumiControlLevel.EXPLORE_HERO && selected,
            shape = RoundedCornerShape(16.dp),
            onKeyEvent = { event ->
              if (
                !selected ||
                  !controlMode ||
                  controlLevel != BangumiControlLevel.EXPLORE_HERO
              ) {
                false
              } else {
                when (event.nativeKeyEvent.keyCode) {
                  android.view.KeyEvent.KEYCODE_DPAD_UP,
                  android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (
                      event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                        event.nativeKeyEvent.repeatCount == 0
                    ) {
                      onHeroMove(
                        if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP)
                          -1
                        else 1
                      )
                    }
                    true
                  }
                  else -> false
                }
              }
            },
            onConfirm = onRequestOpen,
          )
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
          // 以原始封面 URL 注册，让共享转场查找命中这张位图，
          // 而不是回退到黑色桥接。
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
  controlMode: Boolean,
  controlLevel: BangumiControlLevel,
  entryFocusRequester: FocusRequester? = null,
  returnFocusItemId: String? = null,
  returnItemFocusRequester: FocusRequester? = null,
  category: BangumiExploreCategory,
  followingScrollAnchor: BangumiExploreFollowingScrollAnchor? = null,
  followingFocusRegistry: BangumiFollowingFocusRegistry,
  onUpdateReturnAnchorFollowingScroll:
    (String, BangumiExploreFollowingScrollAnchor) -> Unit,
  onUpdateReturnAnchorSourceBounds: (String, BangumiExploreSourceBounds) -> Unit,
  modifier: Modifier = Modifier,
) {
  val returnFollowingIndex =
    state.cards.indexOfFirst { it.id == returnFocusItemId }.coerceAtLeast(0)
  val listState =
    rememberLazyListState(
      initialFirstVisibleItemIndex =
        followingScrollAnchor?.firstVisibleItemIndex?.coerceAtLeast(0) ?: returnFollowingIndex,
      initialFirstVisibleItemScrollOffset =
        followingScrollAnchor?.firstVisibleItemScrollOffset?.coerceAtLeast(0) ?: 0,
    )
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
            // 慢速滚动通常提前几张卡片就开始请求，让尾部保持视觉安静。只有手势真的
            // 在该请求提供更多卡片之前到达末尾时，才暴露一个紧凑的加载指示。
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
        // 本次重测量禁用 LazyRow 的旧键视口锚定。重排后卡片的目的地现在是可见的
        // 左边缘，而不是屏幕外的全局索引零。
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
            val isInitialEntryCard = card == displayedFollowing.firstOrNull()
            val stableFocusRequester =
              remember(card.id, entryFocusRequester) {
                if (isInitialEntryCard && entryFocusRequester != null) {
                  entryFocusRequester
                } else {
                  FocusRequester()
                }
              }
            DisposableEffect(category, card.id, stableFocusRequester) {
              followingFocusRegistry.register(category, card.id, stableFocusRequester)
              onDispose {
                followingFocusRegistry.unregister(category, card.id, stableFocusRequester)
              }
            }
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
              onOpen = { item, bounds ->
                onOpen(item, bounds)
                onUpdateReturnAnchorFollowingScroll(
                  item.stableId,
                  BangumiExploreFollowingScrollAnchor(
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                  ),
                )
              },
              onSourceBoundsChanged = onUpdateReturnAnchorSourceBounds,
              controlMode = controlMode,
              controlLevel = controlLevel,
              focusRequester = stableFocusRequester,
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
  onSourceBoundsChanged: (String, BangumiExploreSourceBounds) -> Unit,
  controlMode: Boolean,
  controlLevel: BangumiControlLevel,
  focusRequester: FocusRequester?,
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
  fun requestOpen(ensureVisible: Boolean) {
    if (card.videoUrl.isBlank()) return
    // 控制器获焦时已经完成过 bringIntoView。确认时若再执行一次，LazyRow 会在冻结共享
    // 元素坐标前产生细小横移，返回目标便会与恢复后的卡片错位。触屏点击仍保留可见性校正。
    val stableBounds = bounds
    scope.launch {
      if (ensureVisible) {
        bringIntoViewRequester.bringIntoView()
        withFrameNanos {}
        onOpen(item, bounds)
      } else {
        onOpen(item, stableBounds)
      }
    }
  }
  VideoCardGradient(
    coverUrl = card.coverUrl,
    loadKey = "anime-following-${card.id}",
    modifier =
      modifier
        .width(FOLLOWING_CARD_WIDTH)
        .height(FOLLOWING_CARD_HEIGHT)
        .navigationBringIntoViewTarget(bringIntoViewRequester)
        .bangumiControllerFocus(
          focusRequester = focusRequester,
          enabled =
            controlMode &&
              controlLevel == BangumiControlLevel.EXPLORE_CONTENT &&
              card.videoUrl.isNotBlank(),
          shape = VideoShapeTokens.Card,
          // LazyRow 自己会把方向键选中的子项带入视口。这里再异步请求一次会在确认后
          // 继续推动整条“正在追”轨道，使共享封面的冻结坐标与返回目标错开。
          onConfirm = { requestOpen(ensureVisible = false) },
        )
        .clip(VideoShapeTokens.Card)
        .clickable(enabled = card.videoUrl.isNotBlank()) { requestOpen(ensureVisible = true) },
  ) {
    Column(Modifier.padding(8.dp)) {
      CoverImage(
        coverUrl = bangumiCoverUrl(card.coverUrl, BangumiCoverVariant.HORIZONTAL_CARD),
        contentDescription = card.title,
        // 共享飞行期间只隐藏封面（与信息流卡片契约一致）；
        // 渐变背景、标题、副标题和进度保持可见。
        modifier =
          Modifier.fillMaxWidth()
            .aspectRatio(16f / 9f)
            .onGloballyPositioned {
              val updated = it.boundsInRoot()
              bounds = updated
              onSourceBoundsChanged(
                card.id,
                BangumiExploreSourceBounds(
                  updated.left,
                  updated.top,
                  updated.right,
                  updated.bottom,
                ),
              )
            }
            .then(if (hidden) Modifier.graphicsLayer { alpha = 0f } else Modifier),
        // 这个内层封面是共享转场捕获的元素。让其圆角与播放器及周围后续卡片表面
        // 对齐。
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

/** 构建副标题，同时不把不可用的进度响应误认为没有观看记录。 */
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

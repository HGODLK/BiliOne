package dev.openbili.webdemo.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
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
import dev.openbili.webdemo.bangumi.BangumiExploreFollowingScrollAnchor
import dev.openbili.webdemo.bangumi.BangumiExploreReturnAnchor
import dev.openbili.webdemo.bangumi.BangumiExploreSourceBounds
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.video.formatCompactCount
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun shouldRestoreBangumiExploreControllerFocus(
  active: Boolean,
  controlMode: Boolean,
  restoreRequestChanged: Boolean,
  hasReturnAnchor: Boolean,
): Boolean =
  active && controlMode && (restoreRequestChanged || hasReturnAnchor)

/**
 * 二级 PGC 页面有意不拥有任何播放器。它可以安全地挂载在共享根播放器之下，
 * 并让顶部分类胶囊和底部根胶囊都避开滚动内容。
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
  controlMode: Boolean = false,
  controlLevel: BangumiControlLevel = BangumiControlLevel.ROOT,
  onControlLevelChanged: (BangumiControlLevel) -> Unit = {},
  onControlExploreNavUp: () -> Unit = {},
  controlFocusRestoreRequest: Int = 0,
  returnAnchor: (BangumiExploreCategory, Long) -> BangumiExploreReturnAnchor? = { _, _ -> null },
  onRememberReturnAnchor: (BangumiExploreReturnAnchor) -> Unit = {},
  onUpdateReturnAnchorFollowingScroll:
    (String, BangumiExploreFollowingScrollAnchor) -> Unit = { _, _ -> },
  onUpdateReturnAnchorSourceBounds: (String, BangumiExploreSourceBounds) -> Unit = { _, _ -> },
  onConsumeReturnAnchor: (BangumiExploreReturnAnchor) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val categories = BangumiExploreCategory.entries
  val controlFocusMemoryRequester = remember { FocusRequester() }
  val returnItemFocusRequester = remember { FocusRequester() }
  val followingFocusRegistry = remember { BangumiFollowingFocusRegistry() }
  val categoryGridStates = remember { mutableMapOf<BangumiExploreCategory, LazyGridState>() }
  val categoryFocusRequesters = remember { categories.map { FocusRequester() } }
  val indexFocusRequester = remember { FocusRequester() }
  val contentFocusRequesters = remember { categories.map { FocusRequester() } }
  val heroFocusRequesters = remember { categories.map { FocusRequester() } }
  val focusManager = LocalFocusManager.current
  val pagerState =
    rememberPagerState(
      initialPage = state.selectedCategory.ordinal,
      pageCount = { categories.size },
    )
  val scope = rememberCoroutineScope()
  var controllerFocusedCategory by remember { mutableStateOf(state.selectedCategory) }
  val currentSelectCategory by rememberUpdatedState(onSelectCategory)
  val contentBackdropLayer = rememberGraphicsLayer()
  var contentBounds by remember { mutableStateOf(Rect.Zero) }
  val density = LocalDensity.current
  val contentTopPadding =
    bangumiExploreContentTopPadding(
      safeDrawingTop = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
    )
  var consumedContentFocusRestoreRequest by remember {
    mutableIntStateOf(controlFocusRestoreRequest)
  }

  fun scrollSessionId(category: BangumiExploreCategory): Long =
    if (
      category == BangumiExploreCategory.ANIME || category == BangumiExploreCategory.GUOCHUANG
    ) {
      state.following(category).sessionId
    } else {
      0L
    }

  suspend fun requestFollowingFocusWithinFrames(
    category: BangumiExploreCategory,
    itemStableId: String,
    maxFrames: Int,
  ): Boolean {
    repeat(maxFrames) {
      val requester = followingFocusRegistry.requester(category, itemStableId)
      if (requester != null && runCatching { requester.requestFocus() }.getOrDefault(false)) {
        return true
      }
      withFrameNanos {}
    }
    return false
  }

  // 基础页组合在推荐封面之后，因此立刻开始其首次分类加载。这保证封面被拉开的
  // 瞬间就能看到加载状态。
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
  LaunchedEffect(
    active,
    controlMode,
    controlLevel,
    controlFocusRestoreRequest,
    state.selectedCategory,
  ) {
    if (!active || !controlMode || controlLevel != BangumiControlLevel.EXPLORE_NAV) {
      return@LaunchedEffect
    }
    val requester = categoryFocusRequesters.getOrNull(state.selectedCategory.ordinal)
      ?: return@LaunchedEffect
    focusManager.clearFocus(force = true)
    withFrameNanos {}
    withFrameNanos {}
    runCatching { requester.requestFocus() }
  }
  LaunchedEffect(controlMode, controlLevel, state.selectedCategory) {
    if (!controlMode || controlLevel != BangumiControlLevel.EXPLORE_NAV) {
      controllerFocusedCategory = state.selectedCategory
    }
  }
  LaunchedEffect(
    active,
    controlMode,
    controlLevel,
    controlFocusRestoreRequest,
    state.selectedCategory,
  ) {
    val restoreRequestChanged =
      controlFocusRestoreRequest != consumedContentFocusRestoreRequest
    val category = state.selectedCategory
    val anchor =
      if (active && controlMode) {
        returnAnchor(category, scrollSessionId(category))
      } else {
        null
      }
    if (
      !shouldRestoreBangumiExploreControllerFocus(
        active = active,
        controlMode = controlMode,
        restoreRequestChanged = restoreRequestChanged,
        hasReturnAnchor = anchor != null,
      )
    ) {
      return@LaunchedEffect
    }
    if (restoreRequestChanged) {
      consumedContentFocusRestoreRequest = controlFocusRestoreRequest
    }
    if (
      controlLevel !in
        setOf(BangumiControlLevel.EXPLORE_CONTENT, BangumiControlLevel.EXPLORE_HERO)
    ) {
      // Hero 与分类导航拥有各自的恢复路径；在这里消费令牌，避免稍后进入内容层时
      // 把一次已经完成的旧恢复请求重新执行。
      return@LaunchedEffect
    }
    // 专用播放页返回不会经过普通覆盖层的焦点令牌；未消费的来源锚点本身
    // 也代表一次待执行的恢复，必须把焦点交还给进入前的卡片。
    val gridState = categoryGridStates[category]
    if (anchor != null && gridState != null) {
      // 番剧探索页会在播放页进场时短暂退出组合。先用 ViewModel 中的轻量锚点
      // 还原网格，再申请原卡片焦点，避免首卡片焦点把页面重新拉到顶部。
      gridState.scrollToItem(
        anchor.firstVisibleItemIndex,
        anchor.firstVisibleItemScrollOffset,
      )
    }
    var restored =
      if (anchor != null) {
        // 嵌套懒加载内容在返回后的两个布局帧内不一定已经重新挂载。持续等待原卡片，
        // 避免一次失败后整页没有控制器焦点。
        if (anchor.followingScrollAnchor != null) {
          requestFollowingFocusWithinFrames(category, anchor.itemStableId, maxFrames = 45)
        } else {
          returnItemFocusRequester.requestFocusWithinFrames(maxFrames = 45)
        }
      } else {
        false
      }
    if (restored && anchor != null) onConsumeReturnAnchor(anchor)
    if (!restored) {
      restored =
        runCatching { controlFocusMemoryRequester.restoreFocusedChild() }.getOrDefault(false)
    }
    if (!restored && anchor == null) {
      val fallback =
        if (controlLevel == BangumiControlLevel.EXPLORE_HERO) {
          heroFocusRequesters[category.ordinal]
        } else {
          contentFocusRequesters[category.ordinal]
        }
      fallback.requestFocusWithinFrames(maxFrames = 8)
    } else if (!restored && anchor != null) {
      // 数据刷新导致原卡片确实不存在时，退到分类胶囊。胶囊不参与网格滚动，
      // 因此页面仍停在返回前的位置，同时重新获得可用的控制器焦点。
      onConsumeReturnAnchor(anchor)
      onControlLevelChanged(BangumiControlLevel.EXPLORE_NAV)
    }
  }

  fun enterControllerCategory(category: BangumiExploreCategory) {
    scope.launch {
      if (pagerState.currentPage != category.ordinal) {
        pagerState.animateScrollToPage(category.ordinal)
      }
      currentSelectCategory(category)
      withFrameNanos {}
      onControlLevelChanged(BangumiControlLevel.EXPLORE_CONTENT)
    }
  }

  CompositionLocalProvider(
    LocalNavigationTopClearance provides (contentTopPadding + 10.dp)
  ) {
    Box(
      modifier
        .fillMaxSize()
        .focusRequester(controlFocusMemoryRequester)
        .focusGroup()
        .background(MaterialTheme.colorScheme.background)
    ) {
    // 只捕获可滚动内容。玻璃胶囊绘制在此录制器之外，因此它可以模糊其下的页面，
    // 而不采样自身或 AppRoot 的捕获图层。
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
        val sessionId = scrollSessionId(category)
        val anchor = returnAnchor(category, sessionId)
        key(category, sessionId) {
          val gridState =
            rememberLazyGridState(
              initialFirstVisibleItemIndex = anchor?.firstVisibleItemIndex ?: 0,
              initialFirstVisibleItemScrollOffset = anchor?.firstVisibleItemScrollOffset ?: 0,
            )
          SideEffect { categoryGridStates[category] = gridState }
          fun rememberOpenAnchor(item: BangumiExploreItem, bounds: Rect) {
            onRememberReturnAnchor(
              BangumiExploreReturnAnchor(
                category = category,
                sessionId = sessionId,
                itemStableId = item.stableId,
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                sourceBounds =
                  bounds
                    .takeIf { it.width > 0f && it.height > 0f }
                    ?.let {
                      BangumiExploreSourceBounds(it.left, it.top, it.right, it.bottom)
                    },
              )
            )
            if (controlMode) {
              runCatching { controlFocusMemoryRequester.saveFocusedChild() }
              // 播放页会在下一帧接管焦点。先主动清除来源焦点，避免卡片变为
              // canFocus=false 时由 Lazy 容器临时把焦点迁到相邻项并推动视口。
              focusManager.clearFocus(force = true)
            }
          }
          BangumiExploreCategoryContent(
            category = category,
            state = state,
            gridState = gridState,
            contentTopPadding = contentTopPadding,
            hiddenItemId = hiddenItemId,
            foregroundActive = active,
            onRefresh = { onRefresh(category) },
            onExplorePull = onExplorePull,
            onExplorePullRelease = onExplorePullRelease,
            onLoadMoreFollowing = { onLoadMoreFollowing(category) },
            onOpenLandscape = { item, bounds ->
              rememberOpenAnchor(item, bounds)
              onOpenLandscape(item, bounds)
            },
            onOpenPoster = { item, bounds ->
              rememberOpenAnchor(item, bounds)
              onOpenPoster(item, bounds)
            },
            controlMode = controlMode,
            controlLevel = controlLevel,
            contentFocusRequester = contentFocusRequesters[category.ordinal],
            heroFocusRequester = heroFocusRequesters[category.ordinal],
            returnFocusItemId = anchor?.itemStableId,
            returnItemFocusRequester = returnItemFocusRequester,
            followingScrollAnchor = anchor?.followingScrollAnchor,
            followingFocusRegistry = followingFocusRegistry,
            onUpdateReturnAnchorFollowingScroll = onUpdateReturnAnchorFollowingScroll,
            onUpdateReturnAnchorSourceBounds = onUpdateReturnAnchorSourceBounds,
            onControlLevelChanged = onControlLevelChanged,
            controlTargetActive = active && category == state.selectedCategory,
          )
        }
      }
    }
    BoxWithConstraints(
      Modifier.align(Alignment.TopCenter)
        .fillMaxWidth()
        // 让分类胶囊保持独立居中。相邻的索引入口按胶囊测量出的设计宽度摆放，
        // 而不是在那个中心内部预留空间。
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
          if (controlMode && controlLevel == BangumiControlLevel.EXPLORE_NAV) {
            controllerFocusedCategory.ordinal.toFloat()
          } else {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(
              0f,
              categories.lastIndex.toFloat(),
            )
          }
        },
        backdropLayer = contentBackdropLayer,
        backdropBounds = contentBounds,
        modifier = Modifier.align(Alignment.Center),
        onCategoryClick = { category ->
          scope.launch { pagerState.animateScrollToPage(category.ordinal) }
        },
        controlMode = controlMode,
        controlLevel = controlLevel,
        categoryFocusRequesters = categoryFocusRequesters,
        indexFocusRequester = indexFocusRequester,
        onCategoryFocused = { category -> controllerFocusedCategory = category },
        onControlLevelChanged = onControlLevelChanged,
        onControlEnterCategory = ::enterControllerCategory,
        onControlExploreNavUp = onControlExploreNavUp,
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
        controlMode = controlMode,
        controlLevel = controlLevel,
        focusRequester = indexFocusRequester,
        leftFocusRequester = categoryFocusRequesters.last(),
        onControlExploreNavUp = onControlExploreNavUp,
        onControlLevelChanged = onControlLevelChanged,
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
      // 推荐层在覆盖或返回期间可能有部分视觉透明。在本页上方保留真实的输入屏障，
      // 让点击永远不会到达下面的卡片。
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
}

@Composable
private fun BangumiIndexEntryCapsule(
  backdropLayer: GraphicsLayer,
  backdropBounds: Rect,
  enabled: Boolean,
  onClick: (Rect) -> Unit,
  controlMode: Boolean,
  controlLevel: BangumiControlLevel,
  focusRequester: FocusRequester,
  leftFocusRequester: FocusRequester,
  onControlExploreNavUp: () -> Unit,
  onControlLevelChanged: (BangumiControlLevel) -> Unit,
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
        .bangumiControllerFocus(
          focusRequester = focusRequester,
          enabled = controlMode && controlLevel == BangumiControlLevel.EXPLORE_NAV,
          shape = shape,
          onKeyEvent = { event ->
            when (event.nativeKeyEvent.keyCode) {
              android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                if (
                  event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                    event.nativeKeyEvent.repeatCount == 0
                ) {
                  onControlExploreNavUp()
                }
                true
              }
              android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (
                  event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                    event.nativeKeyEvent.repeatCount == 0
                ) {
                  onControlLevelChanged(BangumiControlLevel.EXPLORE_CONTENT)
                }
                true
              }
              else -> false
            }
          },
          onConfirm = { onClick(bounds) },
        )
        .focusProperties {
          canFocus = controlMode && controlLevel == BangumiControlLevel.EXPLORE_NAV
          left = leftFocusRequester
          right = FocusRequester.Cancel
          up = FocusRequester.Cancel
          down = FocusRequester.Cancel
        }
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
  controlMode: Boolean,
  controlLevel: BangumiControlLevel,
  categoryFocusRequesters: List<FocusRequester>,
  indexFocusRequester: FocusRequester,
  onCategoryFocused: (BangumiExploreCategory) -> Unit,
  onControlLevelChanged: (BangumiControlLevel) -> Unit,
  onControlEnterCategory: (BangumiExploreCategory) -> Unit,
  onControlExploreNavUp: () -> Unit,
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
    // 与根导航相同的胶囊配方：完整圆角玻璃、细边框和滑动的选中药丸。
    // 顶栏保持更薄，因为它只承载文本。
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
            Modifier.weight(1f)
              .fillMaxSize()
              .bangumiControllerFocus(
                focusRequester = categoryFocusRequesters[category.ordinal],
                enabled = controlMode && controlLevel == BangumiControlLevel.EXPLORE_NAV,
                shape = CircleShape,
                onFocused = { onCategoryFocused(category) },
                onKeyEvent = { event ->
                  when (event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                      if (
                        event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                          event.nativeKeyEvent.repeatCount == 0
                      ) {
                        onControlExploreNavUp()
                      }
                      true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                      if (
                        event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                          event.nativeKeyEvent.repeatCount == 0
                      ) {
                        onControlEnterCategory(category)
                      }
                      true
                    }
                    else -> false
                  }
                },
                onConfirm = { onControlEnterCategory(category) },
              )
              .focusProperties {
                canFocus = controlMode && controlLevel == BangumiControlLevel.EXPLORE_NAV
                left =
                  categoryFocusRequesters.getOrNull(category.ordinal - 1)
                    ?: FocusRequester.Cancel
                right =
                  categoryFocusRequesters.getOrNull(category.ordinal + 1)
                    ?: indexFocusRequester
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
              }
              .clip(CircleShape)
              .clickable { onCategoryClick(category) },
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
  gridState: LazyGridState,
  contentTopPadding: Dp,
  hiddenItemId: String?,
  foregroundActive: Boolean,
  onRefresh: () -> Unit,
  onExplorePull: (Float) -> Unit,
  onExplorePullRelease: (Float) -> Unit,
  onLoadMoreFollowing: () -> Unit,
  onOpenLandscape: (BangumiExploreItem, Rect) -> Unit,
  onOpenPoster: (BangumiExploreItem, Rect) -> Unit,
  controlMode: Boolean,
  controlLevel: BangumiControlLevel,
  contentFocusRequester: FocusRequester,
  heroFocusRequester: FocusRequester,
  returnFocusItemId: String?,
  returnItemFocusRequester: FocusRequester,
  followingScrollAnchor: BangumiExploreFollowingScrollAnchor?,
  followingFocusRegistry: BangumiFollowingFocusRegistry,
  onUpdateReturnAnchorFollowingScroll:
    (String, BangumiExploreFollowingScrollAnchor) -> Unit,
  onUpdateReturnAnchorSourceBounds: (String, BangumiExploreSourceBounds) -> Unit,
  onControlLevelChanged: (BangumiControlLevel) -> Unit,
  controlTargetActive: Boolean,
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
        // 国创与番剧一样属于追更型：复用番剧的头图 + 追番 + 排行 + 推荐布局，
        // 但拥有独立的状态树。会话键已在上层连同网格状态一起管理。
        val following = state.following(category)
        AnimeExploreContent(
          category = category,
          page = page,
          gridState = gridState,
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
          controlMode = controlMode,
          controlLevel = controlLevel,
          contentFocusRequester = contentFocusRequester,
          heroFocusRequester = heroFocusRequester,
          returnFocusItemId = returnFocusItemId,
          returnItemFocusRequester = returnItemFocusRequester,
          followingScrollAnchor = followingScrollAnchor,
          followingFocusRegistry = followingFocusRegistry,
          onUpdateReturnAnchorFollowingScroll = onUpdateReturnAnchorFollowingScroll,
          onUpdateReturnAnchorSourceBounds = onUpdateReturnAnchorSourceBounds,
          onControlLevelChanged = onControlLevelChanged,
          controlTargetActive = controlTargetActive,
        )
      } else {
        ExploreCategoryGridContent(
          category = category,
          page = page,
          gridState = gridState,
          contentTopPadding = contentTopPadding,
          hiddenItemId = hiddenItemId,
          foregroundActive = foregroundActive,
          onExplorePull = onExplorePull,
          onExplorePullRelease = onExplorePullRelease,
          onOpenLandscape = onOpenLandscape,
          onOpenPoster = onOpenPoster,
          controlMode = controlMode,
          controlLevel = controlLevel,
          contentFocusRequester = contentFocusRequester,
          heroFocusRequester = heroFocusRequester,
          returnFocusItemId = returnFocusItemId,
          returnItemFocusRequester = returnItemFocusRequester,
          onUpdateReturnAnchorSourceBounds = onUpdateReturnAnchorSourceBounds,
          onControlLevelChanged = onControlLevelChanged,
          controlTargetActive = controlTargetActive,
        )
      }
  }
}

internal data class AnimeExploreContentGroups(
  val hot: List<BangumiExploreItem>,
  val ranking: List<BangumiExploreItem>,
  val recommendations: List<BangumiExploreItem>,
)

internal fun bangumiExploreContentTopPadding(safeDrawingTop: Dp): Dp = safeDrawingTop + 78.dp

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
internal fun AnimeRankingPoster(
  item: BangumiExploreItem,
  rank: Int,
  hidden: Boolean,
  foregroundActive: Boolean,
  onOpen: (BangumiExploreItem, Rect) -> Unit,
  onSourceBoundsChanged: (String, BangumiExploreSourceBounds) -> Unit = { _, _ -> },
  controlMode: Boolean = false,
  controlLevel: BangumiControlLevel = BangumiControlLevel.ROOT,
  focusRequester: FocusRequester? = null,
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
  fun requestOpen() {
    if (opening || hidden) return
    opening = true
    scope.launch {
      if (!controlMode) {
        bringIntoViewRequester.bringIntoView()
        withFrameNanos {}
      }
      chromeAlpha.animateTo(0f, tween(MotionTokens.Standard))
      withFrameNanos {}
      onOpen(item, bounds)
    }
  }
  Box(
    Modifier.fillMaxWidth()
      .aspectRatio(3f / 4f)
      .navigationBringIntoViewTarget(bringIntoViewRequester)
      .bangumiControllerFocus(
        focusRequester = focusRequester,
        enabled = controlMode && controlLevel == BangumiControlLevel.EXPLORE_CONTENT,
        shape = RoundedCornerShape(16.dp),
        onFocused = { scope.launch { bringIntoViewRequester.bringIntoView() } },
        onConfirm = ::requestOpen,
      )
      .clip(RoundedCornerShape(16.dp))
      .clickable(enabled = !opening && !hidden, onClick = ::requestOpen)
  ) {
    CoverImage(
      coverUrl = bangumiCoverUrl(item.coverUrl, BangumiCoverVariant.POSTER),
      contentDescription = item.title,
      modifier =
        Modifier.fillMaxSize()
          .onGloballyPositioned {
            val updated = it.boundsInRoot()
            bounds = updated
            onSourceBoundsChanged(
              item.stableId,
              BangumiExploreSourceBounds(
                updated.left,
                updated.top,
                updated.right,
                updated.bottom,
              ),
            )
          }
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
internal fun AnimeRecommendationPoster(
  item: BangumiExploreItem,
  hidden: Boolean,
  foregroundActive: Boolean,
  onOpen: (BangumiExploreItem, Rect) -> Unit,
  onSourceBoundsChanged: (String, BangumiExploreSourceBounds) -> Unit = { _, _ -> },
  controlMode: Boolean = false,
  controlLevel: BangumiControlLevel = BangumiControlLevel.ROOT,
  focusRequester: FocusRequester? = null,
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
  fun requestOpen() {
    if (opening || hidden) return
    opening = true
    scope.launch {
      if (!controlMode) {
        bringIntoViewRequester.bringIntoView()
        withFrameNanos {}
      }
      chromeAlpha.animateTo(0f, tween(MotionTokens.Standard))
      withFrameNanos {}
      onOpen(item, bounds)
    }
  }
  Box(
    Modifier.fillMaxWidth()
      .aspectRatio(3f / 4f)
      .navigationBringIntoViewTarget(bringIntoViewRequester)
      .bangumiControllerFocus(
        focusRequester = focusRequester,
        enabled = controlMode && controlLevel == BangumiControlLevel.EXPLORE_CONTENT,
        shape = RoundedCornerShape(16.dp),
        onFocused = { scope.launch { bringIntoViewRequester.bringIntoView() } },
        onConfirm = ::requestOpen,
      )
      .clip(RoundedCornerShape(16.dp))
      .clickable(enabled = !opening && !hidden, onClick = ::requestOpen)
  ) {
    CoverImage(
      coverUrl = bangumiCoverUrl(item.coverUrl, BangumiCoverVariant.POSTER),
      contentDescription = item.title,
      modifier =
        Modifier.fillMaxSize()
          .onGloballyPositioned {
            val updated = it.boundsInRoot()
            bounds = updated
            onSourceBoundsChanged(
              item.stableId,
              BangumiExploreSourceBounds(
                updated.left,
                updated.top,
                updated.right,
                updated.bottom,
              ),
            )
          }
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
 * 五个非番剧分类的网格主体。它共享番剧页的度量、下拉折叠和共享封面转场，但用差异化
 * 布局替换占位横向行：发现分类以单个 [ExploreFocusBanner] 开头，国创保留其追番轨道
 * 与时间线，而每个分类都汇聚到共享的排行 / 推荐海报上。
 */
@Composable
private fun ExploreCategoryGridContent(
  category: BangumiExploreCategory,
  page: BangumiExplorePage,
  gridState: LazyGridState,
  contentTopPadding: Dp,
  hiddenItemId: String?,
  foregroundActive: Boolean,
  onExplorePull: (Float) -> Unit,
  onExplorePullRelease: (Float) -> Unit,
  onOpenLandscape: (BangumiExploreItem, Rect) -> Unit,
  onOpenPoster: (BangumiExploreItem, Rect) -> Unit,
  controlMode: Boolean,
  controlLevel: BangumiControlLevel,
  contentFocusRequester: FocusRequester,
  heroFocusRequester: FocusRequester,
  returnFocusItemId: String?,
  returnItemFocusRequester: FocusRequester,
  onUpdateReturnAnchorSourceBounds: (String, BangumiExploreSourceBounds) -> Unit,
  onControlLevelChanged: (BangumiControlLevel) -> Unit,
  controlTargetActive: Boolean,
) {
  val focusManager = LocalFocusManager.current
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

  // 类信息流模块合并成一个推荐网格，最后渲染在边界带之后。
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
        contentFocusRequester
      } else {
        heroFocusRequester
      }
    runCatching { requester.requestFocus() }
  }

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
            // 发现分类以一块电影感焦点横幅开头。其右侧 16:9 封面是共享飞行的
            // 源；宽幅背景本身从不飞行。
            if (section.items.isNotEmpty()) {
              item(key = section.stableId + ":focus", span = { GridItemSpan(maxLineSpan) }) {
                ExploreFocusBanner(
                  item = section.items.first(),
                  kicker = section.title,
                  hidden = "bangumi-explore-${section.items.first().stableId}" == hiddenItemId,
                  foregroundActive = foregroundActive,
                  onOpen = onOpenLandscape,
                  controlMode = controlMode,
                  controlLevel = controlLevel,
                  contentFocusRequester = contentFocusRequester,
                  heroFocusRequester = heroFocusRequester,
                  returnFocusRequester =
                    returnItemFocusRequester.takeIf {
                      section.items.first().stableId == returnFocusItemId
                    },
                  onControlLevelChanged = onControlLevelChanged,
                )
              }
            }
            if (section.items.size > 1) {
              // 横幅的眉题已经为该模块打了标签，因此其余的焦点条目无需冗余标题
              // 直接跟随。
              gridItemsIndexed(
                section.items.drop(1),
                key = { _, item -> section.stableId + ":hot:" + item.stableId },
              ) { _, item ->
                ExploreLandscapeCard(
                  item = item,
                  hidden = "bangumi-explore-${item.stableId}" == hiddenItemId,
                  onOpen = onOpenLandscape,
                  controlMode = controlMode,
                  controlLevel = controlLevel,
                  focusRequester =
                    returnItemFocusRequester.takeIf { item.stableId == returnFocusItemId },
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
                onSourceBoundsChanged = onUpdateReturnAnchorSourceBounds,
                controlMode = controlMode,
                controlLevel = controlLevel,
                focusRequester =
                  returnItemFocusRequester.takeIf { item.stableId == returnFocusItemId },
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
                onSourceBoundsChanged = onUpdateReturnAnchorSourceBounds,
                controlMode = controlMode,
                controlLevel = controlLevel,
                focusRequester =
                  returnItemFocusRequester.takeIf { item.stableId == returnFocusItemId },
              )
            }
          }
          else -> {
            // OTHER（专题 / 厂牌 / 运营）：按条目样式渲染为竖版海报或横版网格。
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
                  onSourceBoundsChanged = onUpdateReturnAnchorSourceBounds,
                  controlMode = controlMode,
                  controlLevel = controlLevel,
                  focusRequester =
                    returnItemFocusRequester.takeIf { item.stableId == returnFocusItemId },
                )
              } else {
                ExploreLandscapeCard(
                  item = item,
                  hidden = "bangumi-explore-${item.stableId}" == hiddenItemId,
                  onOpen = onOpenLandscape,
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

/**
 * 发现分类的电影感焦点横幅。与番剧头图一样，宽幅作品只是背景：共享飞行源是其尾缘
 * 上的 16:9 封面卡片，飞向详情播放器。背景本身绝不转变成播放器封面。
 */
@Composable
private fun ExploreFocusBanner(
  item: BangumiExploreItem,
  kicker: String,
  hidden: Boolean,
  foregroundActive: Boolean,
  onOpen: (BangumiExploreItem, Rect) -> Unit,
  controlMode: Boolean,
  controlLevel: BangumiControlLevel,
  contentFocusRequester: FocusRequester,
  heroFocusRequester: FocusRequester,
  returnFocusRequester: FocusRequester?,
  onControlLevelChanged: (BangumiControlLevel) -> Unit,
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
  fun requestOpen() {
    if (opening || hidden) return
    opening = true
    scope.launch {
      bringIntoViewRequester.bringIntoView()
      chromeAlpha.animateTo(0f, tween(MotionTokens.Standard))
      withFrameNanos {}
      onOpen(item, cardBounds)
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
        .bangumiControllerFocus(
          focusRequester =
            returnFocusRequester ?: when (controlLevel) {
              BangumiControlLevel.EXPLORE_CONTENT -> contentFocusRequester
              BangumiControlLevel.EXPLORE_HERO -> heroFocusRequester
              else -> null
            },
          enabled =
            controlMode &&
              controlLevel in
                setOf(BangumiControlLevel.EXPLORE_CONTENT, BangumiControlLevel.EXPLORE_HERO),
          shape = RoundedCornerShape(24.dp),
          onFocused = { scope.launch { bringIntoViewRequester.bringIntoView() } },
          onKeyEvent = { event ->
            if (
              controlLevel == BangumiControlLevel.EXPLORE_CONTENT &&
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
          onConfirm = {
            if (controlLevel == BangumiControlLevel.EXPLORE_CONTENT) {
              onControlLevelChanged(BangumiControlLevel.EXPLORE_HERO)
            } else {
              requestOpen()
            }
          },
        )
        .clip(RoundedCornerShape(24.dp))
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          enabled = !opening && !hidden,
        ) {
          requestOpen()
        }
    ) {
      // 宽幅背景。共享飞行期间它保持不动，并被转场的背景接管盖住；
      // 只有尾缘的 16:9 封面飞向详情播放器。
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
      // 底部渐变让叠加在背景上的信息保持可读。
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
      // 尾缘 16:9 封面卡片 —— 共享飞行源。它以原始封面 URL 注册，让 LANDSCAPE
      // 转场把这张位图飞向播放器（与后续卡片相同的契约）。
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

/** 横版（焦点 / 专题 / 运营）条目的 16:9 网格卡片；其封面飞向详情播放器。 */
@Composable
private fun ExploreLandscapeCard(
  item: BangumiExploreItem,
  hidden: Boolean,
  onOpen: (BangumiExploreItem, Rect) -> Unit,
  controlMode: Boolean,
  controlLevel: BangumiControlLevel,
  focusRequester: FocusRequester? = null,
) {
  var bounds by remember(item.stableId) { mutableStateOf(Rect.Zero) }
  val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
  val scope = rememberCoroutineScope()
  fun requestOpen() {
    scope.launch {
      bringIntoViewRequester.bringIntoView()
      withFrameNanos {}
      onOpen(item, bounds)
    }
  }
  Column(
    Modifier.fillMaxWidth()
      .navigationBringIntoViewTarget(bringIntoViewRequester)
      .bangumiControllerFocus(
        focusRequester = focusRequester,
        enabled = controlMode && controlLevel == BangumiControlLevel.EXPLORE_CONTENT,
        shape = VideoShapeTokens.Player,
        onFocused = { scope.launch { bringIntoViewRequester.bringIntoView() } },
        onConfirm = ::requestOpen,
      )
      .clip(VideoShapeTokens.Player)
      .clickable(onClick = ::requestOpen)
  ) {
    Box(
      Modifier.fillMaxWidth()
        .aspectRatio(16f / 9f)
        // 横版封面共享播放器的圆角半径，让飞行中的封面严丝合缝地落地。
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

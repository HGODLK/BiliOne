package dev.openbili.webdemo.feed

/**
 * 首页推荐信息流：网格分页、卡片入场动画、滚动锚点与导航手势刹车。
 */

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import dev.openbili.webdemo.R
import dev.openbili.webdemo.ui.AvatarImage
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.LocalControlFocusVisible
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.VideoCardReveal
import dev.openbili.webdemo.ui.VideoShapeTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 首页推荐信息流组合体。 */
@Composable
fun FeedScreen(
  state: FeedUiState,
  onRefresh: () -> Unit,
  onPullRefresh: (Int) -> Unit = {},
  onLoadNextPage: () -> Unit,
  onItemClick: (FeedItem, Rect, FeedScrollAnchor) -> Unit,
  onItemLongClick: (FeedItem) -> Unit,
  onProfileClick: (FeedItem, Rect) -> Unit,
  onConsumeRefreshMessage: () -> Unit,
  coverPrefetchCount: Int = FeedPerformanceConfig.coverPrefetchCount,
  backgroundWorkAllowed: Boolean = true,
  gridState: LazyGridState = rememberLazyGridState(),
  hiddenCoverItemId: String? = null,
  dismissedItemIds: Set<String> = emptySet(),
  onRestoreDismissedItem: (FeedItem) -> Unit = {},
  onItemBoundsChanged: (FeedItem, Rect) -> Unit = { _, _ -> },
  columns: Int = 3,
  topContentPadding: Dp = 12.dp,
  bottomContentPadding: Dp = NavigationCardBottomClearance,
  chromeVisible: Boolean = true,
  onBackgroundClick: () -> Unit = {},
  initialFocusRequester: FocusRequester? = null,
  controlNavigationEnabled: Boolean = true,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  val prefetchedCovers = remember { mutableSetOf<String>() }
  val effectiveColumns = columns.coerceIn(3, 6)
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(gridState, effectiveColumns)
  // 无限滚动触发条件
  val shouldLoadMore by remember {
    derivedStateOf {
      val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      val totalItems = gridState.layoutInfo.totalItemsCount
      lastVisible >= totalItems - 6 && totalItems > 0
    }
  }
  LaunchedEffect(shouldLoadMore, imageLoadPolicy.mode, backgroundWorkAllowed) {
    if (
      backgroundWorkAllowed &&
        shouldLoadMore &&
        imageLoadPolicy.mode != FeedImageLoadMode.PAUSED &&
        state is FeedUiState.Content &&
        !state.isLoadingMore
    ) {
      onLoadNextPage()
    }
  }

  val contentItems = (state as? FeedUiState.Content)?.items.orEmpty()
  LaunchedEffect(gridState, contentItems, imageLoadPolicy.mode, backgroundWorkAllowed) {
    snapshotFlow {
        (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to
          gridState.isScrollInProgress
      }
      .distinctUntilChanged()
      .collect { (lastVisible, isScrolling) ->
        if (!backgroundWorkAllowed) return@collect
        if (isScrolling) {
          if (imageLoadPolicy.mode == FeedImageLoadMode.THROTTLED) {
            // 慢速移动获得一个小而串行的预看窗口：串行执行避免三张解码与主线程的
            // 列表摆放竞争。
            contentItems.drop(lastVisible + 1).take(3).forEach { item ->
              if (prefetchedCovers.add(item.coverUrl)) {
                runCatching {
                  context.imageLoader.execute(
                    CoverImageRequestFactory.request(
                      item.coverUrl,
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
        // 让当前可见封面赢得第一批解码/纹理上传；宽限期内用户又开始移动时，
        // 把预取推迟到下一个空闲点。
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

  // 刷新消息 -> snackbar
  val refreshMessage = (state as? FeedUiState.Content)?.refreshMessage
  LaunchedEffect(refreshMessage, chromeVisible) {
    if (chromeVisible && !refreshMessage.isNullOrBlank()) {
      snackbarHostState.showSnackbar(refreshMessage)
      onConsumeRefreshMessage()
    }
  }

  Box(
    Modifier.fillMaxSize()
      .clickable(
        enabled = !chromeVisible,
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onBackgroundClick,
      )
  ) {
    PullRefreshContainer(
      refreshing = state.isRefreshing,
      onRefresh = { onPullRefresh(feedRefreshItemCount(effectiveColumns)) },
      enabled = chromeVisible,
      indicatorTopPadding = topContentPadding + 8.dp,
      modifier = Modifier.fillMaxSize(),
    ) {
      val current = state
      when (current) {
        is FeedUiState.Loading -> FeedSkeleton(effectiveColumns, topContentPadding)
        is FeedUiState.Content ->
          CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
            FeedGrid(
              items = current.items,
              isLoadingMore = current.isLoadingMore,
              gridState = gridState,
              onItemClick = onItemClick,
              onItemLongClick = onItemLongClick,
              onProfileClick = onProfileClick,
              hiddenCoverItemId = hiddenCoverItemId,
              dismissedItemIds = dismissedItemIds,
              onRestoreDismissedItem = onRestoreDismissedItem,
              onItemBoundsChanged = onItemBoundsChanged,
              columns = effectiveColumns,
              topContentPadding = topContentPadding,
              bottomContentPadding = bottomContentPadding,
              showLoadingIndicator = chromeVisible,
              initialFocusRequester = initialFocusRequester,
              controlNavigationEnabled = controlNavigationEnabled,
            )
          }
        is FeedUiState.Empty -> FeedEmpty()
        is FeedUiState.ExtractionError -> FeedError(current.detail, onRefresh)
        is FeedUiState.NetworkError -> FeedError(current.detail, onRefresh)
      }
    }
    if (chromeVisible) {
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }
}

internal fun feedRefreshItemCount(columns: Int): Int = columns.coerceIn(3, 6) * 4

/**
 * 导航目标完全进入屏幕后保留的精确安全视口位置。
 */
data class FeedScrollAnchor(
  val firstVisibleItemIndex: Int,
  val firstVisibleItemScrollOffset: Int,
)

internal fun feedReturnScrollAnchorAfterBringIntoView(
  firstVisibleItemIndex: Int,
  firstVisibleItemScrollOffset: Int,
): FeedScrollAnchor =
  FeedScrollAnchor(
    firstVisibleItemIndex = firstVisibleItemIndex,
    firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
  )

// ── FeedGrid（信息流网格） ────────────────────────────────────────────────────

internal class FeedNavigationFlingTracker : NestedScrollConnection {
  private var pointerVelocityY = 0f
  private var capturedAtMs = 0L

  override suspend fun onPreFling(available: Velocity): Velocity {
    pointerVelocityY = available.y
    capturedAtMs = SystemClock.uptimeMillis()
    return Velocity.Zero
  }

  fun takeResidualScrollDistance(nowMs: Long = SystemClock.uptimeMillis()): Float {
    val elapsedMs = (nowMs - capturedAtMs).coerceAtLeast(0L)
    if (capturedAtMs == 0L || elapsedMs > FeedPerformanceConfig.navigationFlingMemoryMs) return 0f
    capturedAtMs = 0L
    return estimatedNavigationBrakeDistance(pointerVelocityY, elapsedMs)
  }
}

internal fun estimatedNavigationBrakeDistance(pointerVelocityY: Float, elapsedMs: Long): Float {
  val decay = exp(-elapsedMs.coerceAtLeast(0L) / 155f)
  return (-pointerVelocityY * .024f * decay).coerceIn(-180f, 180f)
}

internal suspend fun settleFeedForNavigation(
  gridState: LazyGridState,
  flingTracker: FeedNavigationFlingTracker,
) {
  val residualDistance =
    if (FeedPerformanceConfig.aggressiveFastPathEnabled) {
      flingTracker.takeResidualScrollDistance()
    } else {
      0f
    }
  gridState.stopScroll()
  if (abs(residualDistance) >= 2f) {
    gridState.animateScrollBy(
      residualDistance,
      animationSpec =
        tween(
          durationMillis = FeedPerformanceConfig.navigationBrakeDurationMs,
          easing = LinearOutSlowInEasing,
        ),
    )
  }
  // 几何回调在布局期间送达：刹车与读取转场源矩形之间保留一个稳定帧。
  withFrameNanos {}
}

internal fun feedNavigationScrollDelta(
  itemOffsetY: Int,
  itemHeight: Int,
  viewportStartOffset: Int,
  viewportEndOffset: Int,
  bottomClearancePx: Int,
  topClearancePx: Int = 0,
): Float {
  val safeViewportStart =
    (viewportStartOffset + topClearancePx.coerceAtLeast(0)).coerceAtMost(viewportEndOffset)
  val safeViewportEnd =
    (viewportEndOffset - bottomClearancePx.coerceAtLeast(0)).coerceAtLeast(safeViewportStart)
  val safeViewportHeight = safeViewportEnd - safeViewportStart
  if (itemHeight > safeViewportHeight) {
    // 卡片通常能完整放下；若窗口缩放使其不可能，则对齐卡片顶部，保证 16:9 封面
    // ——共享转场的来源——完整可见。
    return (itemOffsetY - safeViewportStart).toFloat()
  }
  val itemEnd = itemOffsetY + itemHeight
  return when {
    itemOffsetY < safeViewportStart -> (itemOffsetY - safeViewportStart).toFloat()
    itemEnd > safeViewportEnd -> (itemEnd - safeViewportEnd).toFloat()
    else -> 0f
  }
}

internal suspend fun bringFeedItemIntoSafeViewport(
  gridState: LazyGridState,
  itemKey: String,
  topClearancePx: Int,
  bottomClearancePx: Int,
) {
  repeat(2) {
    val layoutInfo = gridState.layoutInfo
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey } ?: return
    val delta =
      feedNavigationScrollDelta(
        itemOffsetY = itemInfo.offset.y,
        itemHeight = itemInfo.size.height,
        viewportStartOffset = layoutInfo.viewportStartOffset,
        viewportEndOffset = layoutInfo.viewportEndOffset,
        bottomClearancePx = bottomClearancePx,
        topClearancePx = topClearancePx,
      )
    if (abs(delta) < 1f) return
    gridState.animateScrollBy(
      delta,
      animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing),
    )
    withFrameNanos {}
  }
}

internal enum class FeedGridControlDirection {
  LEFT,
  RIGHT,
  UP,
  DOWN,
}

internal fun feedGridControlTargetIndex(
  currentIndex: Int,
  itemCount: Int,
  columns: Int,
  direction: FeedGridControlDirection,
): Int? {
  if (currentIndex !in 0 until itemCount) return null
  val safeColumns = columns.coerceAtLeast(1)
  val column = currentIndex % safeColumns
  val target =
    when (direction) {
      FeedGridControlDirection.LEFT -> if (column > 0) currentIndex - 1 else return null
      FeedGridControlDirection.RIGHT ->
        if (column < safeColumns - 1) currentIndex + 1 else return null
      FeedGridControlDirection.UP -> currentIndex - safeColumns
      FeedGridControlDirection.DOWN -> currentIndex + safeColumns
    }
  return target.takeIf { it in 0 until itemCount }
}

private fun feedGridControlDirection(keyCode: Int): FeedGridControlDirection? =
  when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT -> FeedGridControlDirection.LEFT
    KeyEvent.KEYCODE_DPAD_RIGHT -> FeedGridControlDirection.RIGHT
    KeyEvent.KEYCODE_DPAD_UP -> FeedGridControlDirection.UP
    KeyEvent.KEYCODE_DPAD_DOWN -> FeedGridControlDirection.DOWN
    else -> null
  }

@Composable
internal fun FeedGrid(
  items: List<FeedItem>,
  isLoadingMore: Boolean,
  gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
  onItemClick: (FeedItem, Rect, FeedScrollAnchor) -> Unit,
  onItemLongClick: (FeedItem) -> Unit,
  onProfileClick: (FeedItem, Rect) -> Unit,
  hiddenCoverItemId: String?,
  dismissedItemIds: Set<String>,
  onRestoreDismissedItem: (FeedItem) -> Unit,
  onItemBoundsChanged: (FeedItem, Rect) -> Unit,
  columns: Int,
  topContentPadding: Dp,
  bottomContentPadding: Dp = NavigationCardBottomClearance,
  showLoadingIndicator: Boolean = true,
  initialFocusRequester: FocusRequester? = null,
  controlNavigationEnabled: Boolean = true,
) {
  val flingTracker = remember(gridState) { FeedNavigationFlingTracker() }
  val controlFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
  val topClearancePx = with(LocalDensity.current) { topContentPadding.roundToPx() }
  val dynamicPaletteAllowed =
    remember(gridState) {
      derivedStateOf {
        !FeedPerformanceConfig.aggressiveFastPathEnabled || !gridState.isScrollInProgress
      }
    }
  LazyVerticalGrid(
    columns = GridCells.Fixed(columns.coerceIn(3, 6)),
    state = gridState,
    modifier = Modifier.fillMaxSize().nestedScroll(flingTracker).testTag("feed_grid"),
    contentPadding =
      PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = topContentPadding,
        bottom = bottomContentPadding,
      ),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    itemsIndexed(
      items = items,
      key = { _, item -> item.id },
      contentType = { _, _ -> "video" },
    ) { index, item ->
      VideoCardReveal(
        index = index,
        batchKey = items.firstOrNull()?.id,
        itemKey = item.id,
        animatedItemCount =
          if (FeedPerformanceConfig.aggressiveFastPathEnabled) {
            FeedPerformanceConfig.initialAnimatedCardCount
          } else {
            Int.MAX_VALUE
          },
      ) {
        val itemFocusRequester =
          initialFocusRequester.takeIf { index == 0 }
            ?: remember(item.id) { FocusRequester() }
        DisposableEffect(item.id, itemFocusRequester) {
          controlFocusRequesters[item.id] = itemFocusRequester
          onDispose {
            if (controlFocusRequesters[item.id] === itemFocusRequester) {
              controlFocusRequesters.remove(item.id)
            }
          }
        }
        FeedCard(
          item = item,
          onClick = { bounds, scrollAnchor -> onItemClick(item, bounds, scrollAnchor) },
          onLongClick = { onItemLongClick(item) },
          onProfileClick = onProfileClick,
          // LazyGrid 已把组合限制在可见与附近预取的卡片上。第二个索引窗口在快速
          // 停稳时可能落后于布局，让整行已组合卡片持有空图片模型，而其独立取色
          // 请求却成功了。
          loadCover = true,
          coverVisible = item.id != hiddenCoverItemId,
          dismissed = item.id in dismissedItemIds,
          onRestoreDismissed = { onRestoreDismissedItem(item) },
          onBoundsChanged = { bounds -> onItemBoundsChanged(item, bounds) },
          gridState = gridState,
          flingTracker = flingTracker,
          dynamicPaletteAllowed = dynamicPaletteAllowed,
          topClearancePx = topClearancePx,
          focusRequester = itemFocusRequester,
          controlIndex = index,
          controlItemCount = items.size,
          controlColumns = columns,
          controlFocusRequesterAt = { targetIndex ->
            items.getOrNull(targetIndex)?.id?.let(controlFocusRequesters::get)
          },
          controlItemIdAt = { targetIndex -> items.getOrNull(targetIndex)?.id },
          controlNavigationEnabled = controlNavigationEnabled,
        )
      }
    }
    if (isLoadingMore && showLoadingIndicator) {
      item(
        key = "loading",
        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
      ) {
        Box(
          Modifier.fillMaxWidth().padding(vertical = 10.dp),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
      }
    }
  }
}

// ── FeedCardContent（可复用 — 无点击处理、无外层 Surface） ────────

private class FeedCardContentBounds {
  var cover = Rect.Zero
  var avatar = Rect.Zero
  var uploaderName = Rect.Zero
}

enum class FeedCardMetadataMode {
  VIDEO,
  LIVE,
}

@Composable
fun FeedCardContent(
  item: FeedItem,
  metadataMode: FeedCardMetadataMode = FeedCardMetadataMode.VIDEO,
  textAlpha: Float = 1f,
  onProfileClick: (Long) -> Unit = {},
  onProfileBoundsClick: (Long, Rect) -> Unit = { mid, _ -> onProfileClick(mid) },
  profileClickEnabled: Boolean = true,
  modifier: Modifier = Modifier,
  loadCover: Boolean = true,
  coverVisible: Boolean = true,
  allowDynamicPalette: Boolean = true,
  dynamicPaletteAllowed: State<Boolean>? = null,
  paletteRequestWidth: Int = 96,
  paletteRequestHeight: Int = 54,
  onCoverBoundsChanged: (Rect) -> Unit = {},
  onAvatarBoundsChanged: (Rect) -> Unit = {},
  statsTextOverride: String? = null,
  publishDateTextOverride: String? = null,
  durationTextOverride: String? = null,
  coverOverlay: @Composable BoxScope.() -> Unit = {},
  liveStatusText: String? = null,
  liveSecondaryText: String? = null,
  liveTrailingText: String? = null,
  overlayInfoOnCover: Boolean = false,
) {
  val measuredBounds = remember(item.id) { FeedCardContentBounds() }
  val fontScale = LocalDensity.current.fontScale.coerceIn(.85f, 2f)
  val extraScale = (fontScale - 1f).coerceAtLeast(0f)
  val titleHeight = (48f + extraScale * 28f).dp
  val metadataLineHeight = (21f + extraScale * 14f).dp
  val statsText =
    remember(item.playCount, item.danmakuCount, statsTextOverride) {
      statsTextOverride ?: feedCardStatsText(item.playCount, item.danmakuCount)
    }
  val publishDate =
    remember(item.publishedAt, publishDateTextOverride) {
      publishDateTextOverride ?: formatCardPublishDate(item.publishedAt)
    }
  val displayedDuration = durationTextOverride ?: item.duration
  VideoCardGradient(
    coverUrl = item.coverUrl,
    modifier = modifier,
    loadKey = item.id,
    allowDynamicPalette = allowDynamicPalette,
    dynamicPaletteAllowed = dynamicPaletteAllowed,
    paletteRequestWidth = paletteRequestWidth,
    paletteRequestHeight = paletteRequestHeight,
  ) {
    BoxWithConstraints {
      val coverHeight = maxWidth * (9f / 16f)
      val infoMaxHeight = feedCardInfoMaxHeight(maxWidth)
      val compactInfo = infoMaxHeight < 112.dp
      val verticalPadding = if (compactInfo) 6.dp else 11.dp
      val preferredMetadataLineHeight = if (compactInfo) 18.dp else metadataLineHeight
      val desiredTitleHeight = if (compactInfo) 28.dp else titleHeight
      val desiredInfoHeight =
        desiredTitleHeight + preferredMetadataLineHeight * 2 + verticalPadding * 2
      val infoHeight = desiredInfoHeight.coerceAtMost(infoMaxHeight).coerceAtLeast(1.dp)
      val contentHeight = (infoHeight - verticalPadding * 2).coerceAtLeast(1.dp)
      val titleSlotHeight =
        (contentHeight - preferredMetadataLineHeight * 2)
          .coerceAtLeast(18.dp.coerceAtMost(contentHeight))
          .coerceAtMost(desiredTitleHeight)
      val displayedMetadataLineHeight =
        ((contentHeight - titleSlotHeight) / 2f)
          .coerceAtLeast(1.dp)
          .coerceAtMost(preferredMetadataLineHeight)
      val avatarSize =
        (if (compactInfo) 28.dp else 40.dp).coerceAtMost(contentHeight)
      val primaryTextColor = if (overlayInfoOnCover) Color.White else MaterialTheme.colorScheme.onSurface
      val secondaryTextColor =
        if (overlayInfoOnCover) Color.White.copy(alpha = .82f)
        else MaterialTheme.colorScheme.onSurfaceVariant
      Column {
        Box(
          Modifier.onGloballyPositioned { coords ->
              val newBounds = coords.boundsInRoot()
              if (measuredBounds.cover != newBounds) {
                measuredBounds.cover = newBounds
                onCoverBoundsChanged(newBounds)
              }
            }
            .then(if (coverVisible) Modifier else Modifier.graphicsLayer { alpha = 0f })
        ) {
          CoverImage(
            coverUrl = if (loadCover) item.coverUrl else null,
            modifier = Modifier.fillMaxWidth(),
            shape = VideoShapeTokens.Player,
            requestWidth = FeedPerformanceConfig.coverRequestWidth,
            requestHeight = FeedPerformanceConfig.coverRequestHeight,
            loadKey = item.id,
          )
          coverOverlay()
          if (overlayInfoOnCover) {
            Box(
              Modifier.matchParentSize()
                .graphicsLayer { alpha = textAlpha }
                .background(
                  Brush.verticalGradient(
                    0f to Color.Transparent,
                    .3f to Color.Black.copy(alpha = .34f),
                    1f to Color.Black.copy(alpha = .72f),
                  )
                )
            )
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomStart) {
              FeedCardInfo(
                item = item,
                metadataMode = metadataMode,
                statsText = statsText,
                publishDate = publishDate,
                displayedDuration = displayedDuration,
                liveStatusText = liveStatusText,
                liveSecondaryText = liveSecondaryText,
                liveTrailingText = liveTrailingText,
                measuredBounds = measuredBounds,
                profileClickEnabled = profileClickEnabled,
                onProfileBoundsClick = onProfileBoundsClick,
                onAvatarBoundsChanged = onAvatarBoundsChanged,
                infoHeight = infoHeight,
                titleSlotHeight = titleSlotHeight,
                metadataLineHeight = displayedMetadataLineHeight,
                avatarSize = avatarSize,
                verticalPadding = verticalPadding,
                compactInfo = compactInfo,
                alpha = textAlpha,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                liveStatusColor = Color(0xFFFFB2C8),
              )
            }
          }
        }
        if (!overlayInfoOnCover) {
          FeedCardInfo(
            item = item,
            metadataMode = metadataMode,
            statsText = statsText,
            publishDate = publishDate,
            displayedDuration = displayedDuration,
            liveStatusText = liveStatusText,
            liveSecondaryText = liveSecondaryText,
            liveTrailingText = liveTrailingText,
            measuredBounds = measuredBounds,
            profileClickEnabled = profileClickEnabled,
            onProfileBoundsClick = onProfileBoundsClick,
            onAvatarBoundsChanged = onAvatarBoundsChanged,
            infoHeight = infoHeight,
            titleSlotHeight = titleSlotHeight,
            metadataLineHeight = displayedMetadataLineHeight,
            avatarSize = avatarSize,
            verticalPadding = verticalPadding,
            compactInfo = compactInfo,
            alpha = textAlpha,
            primaryTextColor = primaryTextColor,
            secondaryTextColor = secondaryTextColor,
            liveStatusColor = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

internal fun feedCardInfoMaxHeight(cardWidth: Dp): Dp = cardWidth * (3f / 8f)

@Composable
private fun FeedCardInfo(
  item: FeedItem,
  metadataMode: FeedCardMetadataMode,
  statsText: String,
  publishDate: String,
  displayedDuration: String?,
  liveStatusText: String?,
  liveSecondaryText: String?,
  liveTrailingText: String?,
  measuredBounds: FeedCardContentBounds,
  profileClickEnabled: Boolean,
  onProfileBoundsClick: (Long, Rect) -> Unit,
  onAvatarBoundsChanged: (Rect) -> Unit,
  infoHeight: Dp,
  titleSlotHeight: Dp,
  metadataLineHeight: Dp,
  avatarSize: Dp,
  verticalPadding: Dp,
  compactInfo: Boolean,
  alpha: Float,
  primaryTextColor: Color,
  secondaryTextColor: Color,
  liveStatusColor: Color,
) {
  Row(
    Modifier.fillMaxWidth()
      .height(infoHeight)
      .padding(horizontal = 12.dp, vertical = verticalPadding)
      .clipToBounds()
      .graphicsLayer { this.alpha = alpha },
    verticalAlignment = Alignment.Top,
  ) {
    if (!item.uploaderFace.isNullOrBlank()) {
      AvatarImage(
        face = item.uploaderFace,
        contentDescription = item.uploader,
        loadKey = item.id,
        modifier =
          Modifier.size(avatarSize)
            .onGloballyPositioned {
              val bounds = it.boundsInRoot()
              measuredBounds.avatar = bounds
              onAvatarBoundsChanged(bounds)
            }
            .clip(CircleShape)
            .clickable(enabled = profileClickEnabled && item.uploaderMid > 0) {
              onProfileBoundsClick(item.uploaderMid, measuredBounds.avatar)
            },
      )
      Spacer(Modifier.width(10.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(
        text = item.title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        maxLines = if (compactInfo) 1 else 2,
        overflow = TextOverflow.Ellipsis,
        color = primaryTextColor,
        modifier = Modifier.height(titleSlotHeight),
      )
      Row(
        modifier = Modifier.fillMaxWidth().height(metadataLineHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = item.uploader.orEmpty(),
          modifier =
            Modifier.then(
                if (metadataMode == FeedCardMetadataMode.LIVE) Modifier.weight(1f)
                else Modifier.weight(.42f)
              )
              .onGloballyPositioned { measuredBounds.uploaderName = it.boundsInRoot() }
              .clickable(enabled = profileClickEnabled && item.uploaderMid > 0) {
                onProfileBoundsClick(
                  item.uploaderMid,
                  measuredBounds.avatar.takeIf { it.width > 0f && it.height > 0f }
                    ?: measuredBounds.uploaderName,
                )
              },
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = secondaryTextColor,
        )
        if (metadataMode == FeedCardMetadataMode.VIDEO) {
          Text(
            statsText,
            modifier = Modifier.weight(.58f),
            style = MaterialTheme.typography.labelSmall,
            color = secondaryTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
          )
        } else {
          liveStatusText?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = liveStatusColor, maxLines = 1)
          }
        }
      }
      if (metadataMode == FeedCardMetadataMode.VIDEO) {
        Row(
          modifier = Modifier.fillMaxWidth().height(metadataLineHeight),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            publishDate,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = secondaryTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          displayedDuration?.takeIf(String::isNotBlank)?.let {
            Text(
              it,
              modifier = Modifier.widthIn(max = 88.dp),
              style = MaterialTheme.typography.labelSmall,
              color = secondaryTextColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.End,
            )
          }
        }
      } else if (!liveSecondaryText.isNullOrBlank() || !liveTrailingText.isNullOrBlank()) {
        Row(
          modifier = Modifier.fillMaxWidth().height(metadataLineHeight),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            liveSecondaryText.orEmpty(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = secondaryTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          liveTrailingText?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = secondaryTextColor, maxLines = 1)
          }
        }
      }
    }
  }
}

internal fun feedCardStatsText(playCount: String?, danmakuCount: Long): String =
  buildList {
      playCount?.takeIf(String::isNotBlank)?.let { add("$it 播放") }
      add("${FeedViewModel.formatCount(danmakuCount)} 弹幕")
    }
    .joinToString(" · ")

private val cardPublishDateFormatter =
  DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

private fun formatCardPublishDate(epochSeconds: Long): String =
  if (epochSeconds > 0L) {
    cardPublishDateFormatter.format(Instant.ofEpochSecond(epochSeconds))
  } else {
    "时间未知"
  }

// ── FeedCard（信息流卡片） ────────────────────────────────────────────────────

private class FeedCardNavigationBounds {
  var cover = Rect.Zero
  var avatar = Rect.Zero
}

private fun Rect.moveBy(deltaX: Float, deltaY: Float): Rect =
  Rect(left + deltaX, top + deltaY, right + deltaX, bottom + deltaY)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedCard(
  item: FeedItem,
  onClick: (Rect, FeedScrollAnchor) -> Unit,
  onLongClick: () -> Unit,
  onProfileClick: (FeedItem, Rect) -> Unit,
  loadCover: Boolean,
  coverVisible: Boolean,
  dismissed: Boolean,
  onRestoreDismissed: () -> Unit,
  onBoundsChanged: (Rect) -> Unit,
  gridState: LazyGridState,
  flingTracker: FeedNavigationFlingTracker,
  dynamicPaletteAllowed: State<Boolean>,
  topClearancePx: Int,
  focusRequester: FocusRequester? = null,
  controlIndex: Int,
  controlItemCount: Int,
  controlColumns: Int,
  controlFocusRequesterAt: (Int) -> FocusRequester?,
  controlItemIdAt: (Int) -> String?,
  controlNavigationEnabled: Boolean,
) {
  val controlMode = LocalControlMode.current
  val controlFocusVisible = LocalControlFocusVisible.current
  val navigationBounds = remember(item.id) { FeedCardNavigationBounds() }
  val scope = rememberCoroutineScope()
  val bottomClearancePx = with(LocalDensity.current) { NavigationCardBottomClearance.roundToPx() }
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val focused by interactionSource.collectIsFocusedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) .98f else if (focused) 1.025f else 1f,
      animationSpec = spring(dampingRatio = .82f, stiffness = 700f),
      label = "feedCardPress",
    )
  val blurRadius by
    animateDpAsState(
      targetValue = if (dismissed) 11.dp else 0.dp,
      animationSpec = tween(280),
      label = "feedDismissBlur",
    )
  val contentAlpha by
    animateFloatAsState(
      targetValue = if (dismissed) .42f else 1f,
      animationSpec = tween(240),
      label = "feedDismissAlpha",
    )
  var undoArmed by remember(item.id) { mutableStateOf(false) }
  LaunchedEffect(dismissed) {
    if (!dismissed) undoArmed = false
  }
  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .then(
          if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
        )
        .then(
          if (controlMode) {
            Modifier.focusProperties {
                canFocus = controlNavigationEnabled
              }
              .onPreviewKeyEvent { event ->
                if (!controlNavigationEnabled) return@onPreviewKeyEvent false
                val direction =
                  feedGridControlDirection(event.nativeKeyEvent.keyCode)
                    ?: return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                  val targetIndex =
                    feedGridControlTargetIndex(
                      currentIndex = controlIndex,
                      itemCount = controlItemCount,
                      columns = controlColumns,
                      direction = direction,
                    )
                  if (targetIndex != null) {
                    scope.launch {
                      var targetRequester = controlFocusRequesterAt(targetIndex)
                      val targetItemId = controlItemIdAt(targetIndex)
                      var focused =
                        targetRequester?.let {
                          runCatching { it.requestFocus() }.getOrDefault(false)
                        } == true
                      if (!focused) {
                        gridState.animateScrollToItem(targetIndex)
                        withFrameNanos {}
                        withFrameNanos {}
                        targetRequester = controlFocusRequesterAt(targetIndex)
                        focused =
                          targetRequester?.let {
                            runCatching { it.requestFocus() }.getOrDefault(false)
                          } == true
                      }
                      if (focused && targetItemId != null) {
                        bringFeedItemIntoSafeViewport(
                          gridState = gridState,
                          itemKey = targetItemId,
                          topClearancePx = topClearancePx,
                          bottomClearancePx = bottomClearancePx,
                        )
                      }
                    }
                  }
                }
                // 控制器模式下方向键总是被消费：缺失目标是硬性网格边界，绝不邀请
                // 父级 Home Pager 去选择相邻频道。
                true
              }
          } else Modifier
        )
        .then(
          if (pressed || scale != 1f) {
            Modifier.graphicsLayer {
              scaleX = scale
              scaleY = scale
            }
          } else Modifier
        )
        .combinedClickable(
          enabled = !dismissed,
          interactionSource = interactionSource,
          indication = LocalIndication.current,
          onClick = {
            scope.launch {
              settleFeedForNavigation(gridState, flingTracker)
              bringFeedItemIntoSafeViewport(
                gridState,
                item.id,
                topClearancePx,
                bottomClearancePx,
              )
              withFrameNanos {}
              withFrameNanos {}
              val scrollAnchor =
                feedReturnScrollAnchorAfterBringIntoView(
                  firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                  firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                )
              onClick(navigationBounds.cover, scrollAnchor)
            }
          },
          onLongClick = onLongClick,
        )
        .testTag("feed_card"),
    shape = VideoShapeTokens.Card,
    color = MaterialTheme.colorScheme.surface,
    // 空闲、按压与滑动期间保持同一条圆角渲染路径。唯一的描边是稳定的键盘/遥控器
    // 焦点环；指针交互仍依赖色调表面。
    tonalElevation = 2.dp,
    shadowElevation = 0.dp,
    border =
      if (focused && controlFocusVisible)
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
      else null,
  ) {
    Box(Modifier.fillMaxWidth()) {
      Box(
        Modifier.fillMaxWidth()
          .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
          .then(
            if (dismissed || contentAlpha != 1f) Modifier.graphicsLayer { alpha = contentAlpha }
            else Modifier
          )
      ) {
        FeedCardContent(
          item = item,
          onProfileBoundsClick = { _, sourceAvatarBounds ->
            val sourceCoverBounds = navigationBounds.cover
            scope.launch {
              settleFeedForNavigation(gridState, flingTracker)
              bringFeedItemIntoSafeViewport(
                gridState,
                item.id,
                topClearancePx,
                bottomClearancePx,
              )
              // 安全视口滚动可能移动一张 LazyGrid 认为可见、但实际被根导航胶囊
              // 遮住的卡片：等待最终的头像坐标。
              withFrameNanos {}
              withFrameNanos {}
              val latestCoverBounds = navigationBounds.cover
              val latestAvatarBounds = navigationBounds.avatar
              val resolvedAvatarBounds =
                if (latestAvatarBounds.width > 0f && latestAvatarBounds.height > 0f) {
                  latestAvatarBounds
                } else if (sourceCoverBounds != Rect.Zero && latestCoverBounds != Rect.Zero) {
                  sourceAvatarBounds.moveBy(
                    latestCoverBounds.left - sourceCoverBounds.left,
                    latestCoverBounds.top - sourceCoverBounds.top,
                  )
                } else sourceAvatarBounds
              onProfileClick(item, resolvedAvatarBounds)
            }
          },
          profileClickEnabled = !dismissed,
          loadCover = loadCover,
          coverVisible = coverVisible,
          dynamicPaletteAllowed = dynamicPaletteAllowed,
          paletteRequestWidth = FeedPerformanceConfig.coverRequestWidth,
          paletteRequestHeight = FeedPerformanceConfig.coverRequestHeight,
          onCoverBoundsChanged = {
            navigationBounds.cover = it
            onBoundsChanged(it)
          },
          onAvatarBoundsChanged = { navigationBounds.avatar = it },
        )
      }
      AnimatedVisibility(
        visible = dismissed,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(tween(220)) + scaleIn(tween(300), initialScale = .9f),
      ) {
        Box(
          Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = .16f))
            .clickable {
              if (undoArmed) onRestoreDismissed() else undoArmed = true
            }
            .padding(18.dp),
          contentAlignment = Alignment.Center,
        ) {
          Crossfade(
            targetState = undoArmed,
            animationSpec = tween(170),
            label = "feedDismissUndoHint",
          ) { showUndoHint ->
            Text(
              if (showUndoHint) "(๑•̀ㅂ•́)و✧ 想反悔的话，再点一次吧" else "(｡•́︿•̀｡) 这类内容先藏起来啦",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}

// ── 骨架屏 ─────────────────────────────────────────────────────────────────

@Composable
private fun FeedSkeleton(columns: Int, topContentPadding: Dp) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(columns.coerceIn(3, 6)),
    modifier = Modifier.fillMaxSize().testTag("feed_skeleton"),
    contentPadding =
      PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = topContentPadding,
        bottom = 16.dp,
      ),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    items(count = 12) {
      Column {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .aspectRatio(16f / 9f)
              .clip(MaterialTheme.shapes.small)
              .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(6.dp))
        Box(
          modifier =
            Modifier.fillMaxWidth(0.85f)
              .height(14.dp)
              .clip(MaterialTheme.shapes.extraSmall)
              .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        )
        Spacer(Modifier.height(4.dp))
        Box(
          modifier =
            Modifier.fillMaxWidth(0.5f)
              .height(12.dp)
              .clip(MaterialTheme.shapes.extraSmall)
              .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
        )
      }
    }
  }
}

// ── 空态 ────────────────────────────────────────────────────────────────────

@Composable
private fun FeedEmpty() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      text = stringResource(R.string.feed_empty),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

// ── 错误态 ──────────────────────────────────────────────────────────────────

@Composable
private fun FeedError(detail: String, onRetry: () -> Unit) {
  Box(
    modifier = Modifier.fillMaxSize().padding(24.dp).testTag("feed_error"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 520.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = stringResource(R.string.feed_loading_failed),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text = detail,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(4.dp))
      Button(onClick = onRetry, modifier = Modifier.testTag("feed_retry_button")) {
        Text(stringResource(R.string.retry))
      }
    }
  }
}

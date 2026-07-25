package dev.openbili.webdemo.feed

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import dev.openbili.webdemo.R
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.ui.AvatarImage
import dev.openbili.webdemo.ui.DeviceStatusCluster
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.RootAccountHeader
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

@Composable
fun FeedScreen(
  state: FeedUiState,
  userInfo: UserInfo,
  onRefresh: () -> Unit,
  onLoadNextPage: () -> Unit,
  onItemClick: (FeedItem, Rect, FeedScrollAnchor) -> Unit,
  onItemLongClick: (FeedItem) -> Unit,
  onProfileClick: (FeedItem, Rect) -> Unit,
  onLoginClick: (Rect) -> Unit,
  onSearch: () -> Unit,
  searchQuery: String = "",
  onSearchQueryChange: (String) -> Unit = {},
  onSearchSubmit: (String) -> Unit = {},
  onConsumeRefreshMessage: () -> Unit,
  onSearchBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit = {},
  coverPrefetchCount: Int = FeedPerformanceConfig.coverPrefetchCount,
  backgroundWorkAllowed: Boolean = true,
  gridState: LazyGridState = rememberLazyGridState(),
  hiddenCoverItemId: String? = null,
  dismissedItemIds: Set<String> = emptySet(),
  onRestoreDismissedItem: (FeedItem) -> Unit = {},
  onItemBoundsChanged: (FeedItem, Rect) -> Unit = { _, _ -> },
) {
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  val prefetchedCovers = remember { mutableSetOf<String>() }
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(gridState)
  // Infinite scroll trigger
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
            // Slow movement gets a small, serial look-ahead window. Serial execution prevents
            // three decodes from competing with the list placement pass on the main thread.
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
        // Let the currently visible covers win the first decode/texture-upload burst. If the
        // user starts moving during this grace period, defer prefetch until the next idle point.
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

  // Refresh message -> snackbar
  val refreshMessage = (state as? FeedUiState.Content)?.refreshMessage
  LaunchedEffect(refreshMessage) {
    if (!refreshMessage.isNullOrBlank()) {
      snackbarHostState.showSnackbar(refreshMessage)
      onConsumeRefreshMessage()
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
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
                      Text(
                        "搜索视频",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
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
        if (state.isRefreshing) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("feed_progress"))
        }
      }
    },
    floatingActionButton = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // scroll to top
        androidx.compose.material3.SmallFloatingActionButton(
          onClick = {
            scope.launch {
              gridState.animateScrollToItem(0)
            }
          },
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = "回到顶部",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        // refresh
        androidx.compose.material3.SmallFloatingActionButton(
          onClick = {
            if (state is FeedUiState.Content) {
              scope.launch {
                gridState.scrollToItem(0)
                onRefresh()
              }
            } else onRefresh()
          },
          containerColor = MaterialTheme.colorScheme.primary,
        ) {
          Icon(
            Icons.Default.Refresh,
            contentDescription = stringResource(R.string.refresh_feed),
            tint = MaterialTheme.colorScheme.onPrimary,
          )
        }
      }
    },
  ) { padding ->
    PullRefreshContainer(
      refreshing = state.isRefreshing,
      onRefresh = onRefresh,
      modifier = Modifier.fillMaxSize().padding(padding),
    ) {
      val current = state
      when (current) {
        is FeedUiState.Loading -> FeedSkeleton()
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
            )
          }
        is FeedUiState.Empty -> FeedEmpty()
        is FeedUiState.ExtractionError -> FeedError(current.detail, onRefresh)
        is FeedUiState.NetworkError -> FeedError(current.detail, onRefresh)
      }
    }
  }
}

/** Exact feed position before a partially visible card is brought on-screen for navigation. */
data class FeedScrollAnchor(
  val firstVisibleItemIndex: Int,
  val firstVisibleItemScrollOffset: Int,
)

// ── FeedGrid ─────────────────────────────────────────────────────────────────

private class FeedNavigationFlingTracker : NestedScrollConnection {
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

private suspend fun settleFeedForNavigation(
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
  // Geometry callbacks are delivered during layout; keep one stable frame between braking and
  // reading the transition source rectangle.
  withFrameNanos {}
}

@Composable
private fun FeedGrid(
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
) {
  val flingTracker = remember(gridState) { FeedNavigationFlingTracker() }
  val dynamicPaletteAllowed =
    remember(gridState) {
      derivedStateOf {
        !FeedPerformanceConfig.aggressiveFastPathEnabled || !gridState.isScrollInProgress
      }
    }
  LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    state = gridState,
    modifier =
      Modifier.fillMaxSize()
        .nestedScroll(flingTracker)
        .testTag("feed_grid"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
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
        FeedCard(
          item = item,
          onClick = { bounds, scrollAnchor -> onItemClick(item, bounds, scrollAnchor) },
          onLongClick = { onItemLongClick(item) },
          onProfileClick = onProfileClick,
          // LazyGrid already limits composition to visible and nearby prefetched cards. A second
          // index window can lag behind layout during a fast settle and leave an entire composed
          // row with null image models while its independent palette request still succeeds.
          loadCover = true,
          coverVisible = item.id != hiddenCoverItemId,
          dismissed = item.id in dismissedItemIds,
          onRestoreDismissed = { onRestoreDismissedItem(item) },
          onBoundsChanged = { bounds -> onItemBoundsChanged(item, bounds) },
          gridState = gridState,
          flingTracker = flingTracker,
          dynamicPaletteAllowed = dynamicPaletteAllowed,
        )
      }
    }
    if (isLoadingMore) {
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

// ── FeedCardContent (reusable — no click handling, no outer Surface) ────────

private class FeedCardContentBounds {
  var cover = Rect.Zero
  var avatar = Rect.Zero
  var uploaderName = Rect.Zero
}

@Composable
fun FeedCardContent(
  item: FeedItem,
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
) {
  val measuredBounds = remember(item.id) { FeedCardContentBounds() }
  val fontScale = LocalDensity.current.fontScale.coerceIn(.85f, 2f)
  val extraScale = (fontScale - 1f).coerceAtLeast(0f)
  val titleHeight = (48f + extraScale * 28f).dp
  val metadataLineHeight = (21f + extraScale * 14f).dp
  val statsText =
    remember(item.playCount, item.danmakuCount) {
      feedCardStatsText(item.playCount, item.danmakuCount)
    }
  val publishDate = remember(item.publishedAt) { formatCardPublishDate(item.publishedAt) }
  VideoCardGradient(
    coverUrl = item.coverUrl,
    modifier = modifier,
    loadKey = item.id,
    allowDynamicPalette = allowDynamicPalette,
    dynamicPaletteAllowed = dynamicPaletteAllowed,
    paletteRequestWidth = paletteRequestWidth,
    paletteRequestHeight = paletteRequestHeight,
  ) {
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
      }

      Row(
        Modifier.fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 11.dp)
          .then(if (textAlpha == 1f) Modifier else Modifier.graphicsLayer { alpha = textAlpha }),
        verticalAlignment = Alignment.Top,
      ) {
        if (!item.uploaderFace.isNullOrBlank()) {
          AvatarImage(
            face = item.uploaderFace,
            contentDescription = item.uploader,
            loadKey = item.id,
            modifier =
              Modifier.size(40.dp)
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.height(titleHeight),
          )
          Row(
            modifier = Modifier.fillMaxWidth().height(metadataLineHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = item.uploader.orEmpty(),
              modifier =
                Modifier.weight(.42f)
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
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              statsText,
              modifier = Modifier.weight(.58f),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.End,
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth().height(metadataLineHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              publishDate,
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            item.duration?.takeIf(String::isNotBlank)?.let {
              Text(
                it,
                modifier = Modifier.widthIn(max = 88.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
              )
            }
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

// ── FeedCard ─────────────────────────────────────────────────────────────────

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
) {
  val navigationBounds = remember(item.id) { FeedCardNavigationBounds() }
  val bringIntoViewRequester = remember { BringIntoViewRequester() }
  val scope = rememberCoroutineScope()
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) .98f else 1f,
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
        .bringIntoViewRequester(bringIntoViewRequester)
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
              bringIntoViewRequester.bringIntoView()
              withFrameNanos {}
              val scrollAnchor =
                FeedScrollAnchor(
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
    // Keep one rounded rendering path while idle, pressed, and flinging. Feed cards intentionally
    // have no outline: a border switching between Surface and the fast-path clip is visually more
    // distracting than the subtle separation already provided by the tonal surface.
    tonalElevation = 2.dp,
    shadowElevation = 0.dp,
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
              bringIntoViewRequester.bringIntoView()
              // bringIntoView may move a partially clipped card. Wait until the moved avatar has
              // been measured, then start the shared transition from that final on-screen point.
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

// ── Skeleton ─────────────────────────────────────────────────────────────────

@Composable
private fun FeedSkeleton() {
  LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    modifier = Modifier.fillMaxSize().testTag("feed_skeleton"),
    contentPadding = PaddingValues(16.dp),
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

// ── Empty ────────────────────────────────────────────────────────────────────

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

// ── Error ────────────────────────────────────────────────────────────────────

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

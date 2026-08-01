package dev.openbili.webdemo.live

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedCardContent
import dev.openbili.webdemo.feed.FeedCardMetadataMode
import dev.openbili.webdemo.feed.FeedImageLoadMode
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import dev.openbili.webdemo.feed.rememberGridFeedImageLoadPolicy
import dev.openbili.webdemo.ui.PressableVideoCard
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.VideoCardReveal
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.ui.createTexturePlayerView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun LiveHomeScreen(
  state: LiveHomeUiState,
  onVisible: () -> Unit,
  onRefresh: () -> Unit,
  onLoadNextPage: () -> Unit,
  onAreaSelected: (LiveAreaFilter) -> Unit,
  onHeroRoomSelected: (LiveSearchRoom) -> Unit,
  onRoomClick: (LiveSearchRoom, LiveHomeSourceAnchor, Rect) -> Unit,
  onRoomBoundsChanged: (LiveHomeSourceAnchor, Rect) -> Unit,
  onTransitionActiveChanged: (Boolean) -> Unit,
  onConsumeRefreshMessage: () -> Unit,
  onHorizontalRailInteractionChanged: (Boolean) -> Unit = {},
  gridState: LazyGridState = rememberLazyGridState(),
  hiddenCoverItemId: String? = null,
  backgroundWorkAllowed: Boolean = true,
  detailActive: Boolean = false,
) {
  val previewViewModel: LivePreviewPlayerViewModel = viewModel()
  val previewState by previewViewModel.state.collectAsState()
  var areaIndexVisible by remember { mutableStateOf(false) }

  LaunchedEffect(backgroundWorkAllowed) {
    if (backgroundWorkAllowed) onVisible() else previewViewModel.pauseForInactivePage()
  }
  DisposableEffect(Unit) {
    onDispose { previewViewModel.stopForNavigation() }
  }

  val shouldLoadMore by
    remember(gridState) {
      derivedStateOf {
        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = gridState.layoutInfo.totalItemsCount
        total > 0 && last >= total - 6
      }
    }
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(gridState)
  LaunchedEffect(shouldLoadMore, state, backgroundWorkAllowed, imageLoadPolicy.mode) {
    if (
      backgroundWorkAllowed &&
        shouldLoadMore &&
        imageLoadPolicy.mode != FeedImageLoadMode.PAUSED &&
        state is LiveHomeUiState.Content &&
        !state.isLoadingMore &&
        !state.isChangingArea
    ) {
      onLoadNextPage()
    }
  }
  val refreshMessage = (state as? LiveHomeUiState.Content)?.refreshMessage
  LaunchedEffect(refreshMessage) {
    if (!refreshMessage.isNullOrBlank()) onConsumeRefreshMessage()
  }

  if (areaIndexVisible && state is LiveHomeUiState.Content) {
    BackHandler { areaIndexVisible = false }
    LiveAreaIndexScreen(
      groups = state.areaGroups,
      selectedArea = state.selectedArea,
      onBack = { areaIndexVisible = false },
      onAreaSelected = {
        areaIndexVisible = false
        onAreaSelected(it)
      },
      onHorizontalRailInteractionChanged = onHorizontalRailInteractionChanged,
    )
    return
  }

  PullRefreshContainer(
    refreshing = state.isRefreshing,
    onRefresh = onRefresh,
    modifier = Modifier.fillMaxSize(),
  ) {
    when (state) {
      LiveHomeUiState.Loading ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      is LiveHomeUiState.Content ->
        CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
          LiveHomeGrid(
            state = state,
            previewState = previewState,
            previewViewModel = previewViewModel,
            backgroundWorkAllowed = backgroundWorkAllowed,
            gridState = gridState,
            hiddenCoverItemId = hiddenCoverItemId,
            detailActive = detailActive,
            onRefresh = onRefresh,
            onHeroRoomSelected = onHeroRoomSelected,
            onRoomClick = { room, anchor, bounds ->
              if (anchor.section != LiveHomeSourceSection.HERO) {
                previewViewModel.stopForNavigation()
              }
              onRoomClick(room, anchor, bounds)
            },
            onRoomBoundsChanged = onRoomBoundsChanged,
            onTransitionActiveChanged = onTransitionActiveChanged,
            onAreaIndex = { areaIndexVisible = true },
            onHorizontalRailInteractionChanged = onHorizontalRailInteractionChanged,
          )
        }
    }
  }
}

@Composable
private fun LiveHomeGrid(
  state: LiveHomeUiState.Content,
  previewState: LivePreviewPlayerState,
  previewViewModel: LivePreviewPlayerViewModel,
  backgroundWorkAllowed: Boolean,
  gridState: LazyGridState,
  hiddenCoverItemId: String?,
  detailActive: Boolean,
  onRefresh: () -> Unit,
  onHeroRoomSelected: (LiveSearchRoom) -> Unit,
  onRoomClick: (LiveSearchRoom, LiveHomeSourceAnchor, Rect) -> Unit,
  onRoomBoundsChanged: (LiveHomeSourceAnchor, Rect) -> Unit,
  onTransitionActiveChanged: (Boolean) -> Unit,
  onAreaIndex: () -> Unit,
  onHorizontalRailInteractionChanged: (Boolean) -> Unit,
) {
  val selectedHero =
    state.heroRooms.firstOrNull { it.roomId == state.selectedHeroRoomId }
      ?: state.heroRooms.firstOrNull()
  LaunchedEffect(backgroundWorkAllowed, selectedHero?.roomId) {
    if (backgroundWorkAllowed && selectedHero != null) {
      previewViewModel.play(selectedHero)
    } else {
      previewViewModel.pauseForInactivePage()
    }
  }
  var gridViewportBounds by remember { mutableStateOf(Rect.Zero) }

  LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    state = gridState,
    modifier =
      Modifier.fillMaxSize()
        .onGloballyPositioned { gridViewportBounds = it.boundsInRoot() }
        .testTag("live_home_grid"),
    contentPadding =
      PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 10.dp,
        bottom = NavigationCardBottomClearance,
      ),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item(key = "live-hero", span = { GridItemSpan(maxLineSpan) }) {
      LiveHeroSection(
        rooms = state.heroRooms,
        selectedRoom = selectedHero,
        loading = state.heroLoading,
        error = state.heroError,
        previewState = previewState,
        previewViewModel = previewViewModel,
        active = backgroundWorkAllowed,
        detailActive = detailActive,
        coverVisible =
          selectedHero?.let { LiveHomeSourceAnchor.hero(it.roomId).stableId } !=
            hiddenCoverItemId,
        gridState = gridState,
        viewportBounds = gridViewportBounds,
        onSelect = onHeroRoomSelected,
        onRoomClick = onRoomClick,
        onRoomBoundsChanged = onRoomBoundsChanged,
        onTransitionActiveChanged = onTransitionActiveChanged,
      )
    }
    item(key = "live-following", span = { GridItemSpan(maxLineSpan) }) {
      LiveFollowingSection(
        state = state.following,
        hiddenCoverItemId = hiddenCoverItemId,
        onRoomClick = onRoomClick,
        onRoomBoundsChanged = onRoomBoundsChanged,
        onHorizontalRailInteractionChanged = onHorizontalRailInteractionChanged,
      )
    }
    item(key = "live-feed-title", span = { GridItemSpan(maxLineSpan) }) {
      Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          if (state.selectedArea.parentAreaId == 0) "推荐直播" else "${state.selectedArea.name}直播",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAreaIndex) { Text("所有分区") }
      }
    }
    when {
      state.isChangingArea ->
        item(key = "live-area-loading", span = { GridItemSpan(maxLineSpan) }) {
          LiveHomeMessage("正在切换分区…", loading = true)
        }
      state.loadError != null ->
        item(key = "live-error", span = { GridItemSpan(maxLineSpan) }) {
          LiveHomeError(state.loadError, onRetry = onRefresh)
        }
      state.rooms.isEmpty() ->
        item(key = "live-empty", span = { GridItemSpan(maxLineSpan) }) {
          LiveHomeMessage(state.emptyMessage ?: "暂时没有正在直播的内容")
        }
      else -> {
        itemsIndexed(
          items = state.rooms,
          key = { _, room -> room.stableId },
          contentType = { _, _ -> "live-room" },
        ) { index, room ->
          LiveRoomCard(
            room = room,
            index = index,
            selectedArea = state.selectedArea,
            hiddenCoverItemId = hiddenCoverItemId,
            onRoomClick = onRoomClick,
            onRoomBoundsChanged = onRoomBoundsChanged,
          )
        }
        if (state.isLoadingMore) {
          item(key = "live-loading-more", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LiveHeroSection(
  rooms: List<LiveSearchRoom>,
  selectedRoom: LiveSearchRoom?,
  loading: Boolean,
  error: String?,
  previewState: LivePreviewPlayerState,
  previewViewModel: LivePreviewPlayerViewModel,
  active: Boolean,
  detailActive: Boolean,
  coverVisible: Boolean,
  gridState: LazyGridState,
  viewportBounds: Rect,
  onSelect: (LiveSearchRoom) -> Unit,
  onRoomClick: (LiveSearchRoom, LiveHomeSourceAnchor, Rect) -> Unit,
  onRoomBoundsChanged: (LiveHomeSourceAnchor, Rect) -> Unit,
  onTransitionActiveChanged: (Boolean) -> Unit,
) {
  val titleAlpha = remember { Animatable(1f) }
  val navigationCoverAlpha = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  var previewBounds by remember { mutableStateOf(Rect.Zero) }
  var navigationPending by remember { mutableStateOf(false) }
  val heroRoom = selectedRoom ?: rooms.firstOrNull()

  LaunchedEffect(detailActive) {
    if (detailActive) {
      titleAlpha.snapTo(0f)
      navigationCoverAlpha.snapTo(1f)
    } else {
      navigationCoverAlpha.snapTo(0f)
      titleAlpha.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
      )
    }
  }

  fun openRoom(room: LiveSearchRoom) {
    if (navigationPending) return
    navigationPending = true
    onTransitionActiveChanged(true)
    scope.launch {
      try {
        val sourceAnchor = LiveHomeSourceAnchor.hero(room.roomId)
        if (!isLiveHeroPreviewFullyVisible(previewBounds, viewportBounds)) {
          gridState.animateScrollToItem(0)
          withFrameNanos {}
          withFrameNanos {}
        }
        if (room.roomId != selectedRoom?.roomId) {
          onSelect(room)
          withFrameNanos {}
          withFrameNanos {}
        }
        if (previewBounds.width > 0f && previewBounds.height > 0f) {
          onRoomBoundsChanged(sourceAnchor, previewBounds)
        }
        coroutineScope {
          launch {
            titleAlpha.animateTo(
              targetValue = 0f,
              animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            )
          }
          launch {
            navigationCoverAlpha.animateTo(
              targetValue = 1f,
              animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
            )
          }
        }
        withFrameNanos {}
        previewViewModel.stopForNavigation()
        withFrameNanos {}
        onRoomClick(room, sourceAnchor, previewBounds)
      } finally {
        navigationPending = false
        onTransitionActiveChanged(false)
      }
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      "推荐直播",
      modifier = Modifier.graphicsLayer { alpha = titleAlpha.value },
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
    )
    if (rooms.isEmpty()) {
      Box(
        Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (loading) CircularProgressIndicator() else Text(error ?: "暂时没有推荐直播")
      }
      return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val gap = 14.dp
      val leftWeight = 1.6f
      val rightWeight = 1f
      val leftWidth =
        ((maxWidth - gap).coerceAtLeast(0.dp) * leftWeight / (leftWeight + rightWeight))
      val heroHeight = leftWidth * (9f / 16f)
      Row(
        Modifier.fillMaxWidth().height(heroHeight),
        horizontalArrangement = Arrangement.spacedBy(gap),
      ) {
        heroRoom?.let { room ->
          LivePreviewPanel(
            room = room,
            previewState = previewState,
            previewViewModel = previewViewModel,
            active = active,
            coverVisible = coverVisible,
            titleAlpha = titleAlpha.value,
            onBoundsChanged = { bounds ->
              previewBounds = bounds
              onRoomBoundsChanged(LiveHomeSourceAnchor.hero(room.roomId), bounds)
            },
            navigationCoverAlpha = { navigationCoverAlpha.value },
            onClick = { openRoom(room) },
            modifier = Modifier.weight(leftWeight).fillMaxHeight().aspectRatio(16f / 9f),
          )
        }
        Column(
          Modifier.weight(rightWeight).fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          rooms.take(5).forEach { room ->
            LiveHeroRecommendationCard(
              room = room,
              selected = room.stableId == selectedRoom?.stableId,
              onClick = { openRoom(room) },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

internal fun isLiveHeroPreviewFullyVisible(
  previewBounds: Rect,
  viewportBounds: Rect,
  tolerancePx: Float = 1f,
): Boolean =
  previewBounds.width > 0f &&
    previewBounds.height > 0f &&
    viewportBounds.width > 0f &&
    viewportBounds.height > 0f &&
    previewBounds.left >= viewportBounds.left - tolerancePx &&
    previewBounds.top >= viewportBounds.top - tolerancePx &&
    previewBounds.right <= viewportBounds.right + tolerancePx &&
    previewBounds.bottom <= viewportBounds.bottom + tolerancePx

@Composable
private fun LivePreviewPanel(
  room: LiveSearchRoom,
  previewState: LivePreviewPlayerState,
  previewViewModel: LivePreviewPlayerViewModel,
  active: Boolean,
  coverVisible: Boolean,
  titleAlpha: Float,
  onBoundsChanged: (Rect) -> Unit,
  navigationCoverAlpha: () -> Float,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val ready = (previewState as? LivePreviewPlayerState.Ready)?.roomId == room.roomId
  Surface(
    modifier =
      modifier
        .clip(VideoShapeTokens.Player)
        .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
        .clickable(onClick = onClick),
    shape = VideoShapeTokens.Player,
    color = Color.Black,
    shadowElevation = 10.dp,
  ) {
    Box(
      Modifier.fillMaxSize().graphicsLayer {
        alpha = if (coverVisible) 1f else 0f
      }
    ) {
      if (active) {
        val player = previewViewModel.preparePlayer()
        AndroidView(
          factory = { context -> createTexturePlayerView(context, player) },
          update = { view: PlayerView -> if (view.player !== player) view.player = player },
          modifier = Modifier.fillMaxSize(),
        )
      }
      CoverImage(
        coverUrl = room.currentDisplayCoverUrl(),
        contentDescription = room.title,
        modifier =
          Modifier.fillMaxSize().graphicsLayer {
            alpha = if (!active || !ready) 1f else navigationCoverAlpha()
          },
        shape = VideoShapeTokens.Player,
        enforceAspectRatio = false,
        alwaysLoad = true,
        loadKey = "live-hero-${room.roomId}",
        bitmapCacheKey = room.currentDisplayCoverUrl(),
      )
      if (!ready && active) {
        Box(
          Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          when (previewState) {
            is LivePreviewPlayerState.Loading -> CircularProgressIndicator(color = Color.White)
            is LivePreviewPlayerState.Error ->
              Text(
                previewState.message,
                color = Color.White,
                modifier =
                  Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = .36f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
              )
            else -> Unit
          }
        }
      }
      Column(
        Modifier.align(Alignment.BottomStart)
          .fillMaxWidth()
          .graphicsLayer { alpha = titleAlpha }
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          room.title,
          color = Color.White,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          "${room.uname} · ${room.watchedText ?: "直播中"}",
          color = Color.White.copy(alpha = .82f),
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun LiveHeroRecommendationCard(
  room: LiveSearchRoom,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(
          if (selected) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .58f)
        )
        .clickable(onClick = onClick)
        .padding(5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    CoverImage(
      coverUrl = room.currentDisplayCoverUrl(),
      contentDescription = null,
      modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(7.dp)),
      shape = RoundedCornerShape(7.dp),
      enforceAspectRatio = false,
      alwaysLoad = true,
      loadKey = "live-hero-card-${room.roomId}",
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      Text(
        room.title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        room.uname,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun LiveFollowingSection(
  state: LiveFollowingUiState,
  hiddenCoverItemId: String?,
  onRoomClick: (LiveSearchRoom, LiveHomeSourceAnchor, Rect) -> Unit,
  onRoomBoundsChanged: (LiveHomeSourceAnchor, Rect) -> Unit,
  onHorizontalRailInteractionChanged: (Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      "我的关注",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
    )
    when {
      state.isLoading ->
        Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
      state.rooms.isNotEmpty() ->
        LazyRow(
          modifier =
            Modifier.fillMaxWidth().testTag("live_following_rail").pointerInput(Unit) {
              awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onHorizontalRailInteractionChanged(true)
                try {
                  do {
                    val event = awaitPointerEvent()
                  } while (event.changes.any { it.pressed })
                } finally {
                  onHorizontalRailInteractionChanged(false)
                }
              }
            },
          contentPadding = PaddingValues(end = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(state.rooms, key = LiveSearchRoom::stableId) { room ->
            val sourceAnchor = LiveHomeSourceAnchor.following(room.roomId)
            LiveFollowingCard(
              room = room,
              sourceAnchor = sourceAnchor,
              coverVisible = sourceAnchor.stableId != hiddenCoverItemId,
              onRoomClick = onRoomClick,
              onRoomBoundsChanged = onRoomBoundsChanged,
            )
          }
        }
      !state.isLoggedIn ->
        Text(
          "登录后即可看到正在直播的关注主播",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
        )
      state.error != null ->
        Text(
          state.error,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
        )
      else ->
        Text(
          "关注的主播暂时没有直播",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
        )
    }
  }
}

@Composable
private fun LiveFollowingCard(
  room: LiveSearchRoom,
  sourceAnchor: LiveHomeSourceAnchor,
  coverVisible: Boolean,
  onRoomClick: (LiveSearchRoom, LiveHomeSourceAnchor, Rect) -> Unit,
  onRoomBoundsChanged: (LiveHomeSourceAnchor, Rect) -> Unit,
) {
  var bounds by remember(room.roomId) { mutableStateOf(Rect.Zero) }
  PressableVideoCard(
    modifier = Modifier.width(212.dp),
    onClick = { onRoomClick(room, sourceAnchor, bounds) },
    onLongClick = {},
  ) {
    Column {
      CoverImage(
        coverUrl = room.currentDisplayCoverUrl(),
        contentDescription = room.title,
        modifier =
          Modifier.fillMaxWidth()
            .aspectRatio(16f / 9f)
            .graphicsLayer { alpha = if (coverVisible) 1f else 0f }
            .onGloballyPositioned {
              bounds = it.boundsInRoot()
              onRoomBoundsChanged(sourceAnchor, bounds)
            },
        shape = VideoShapeTokens.Card,
        alwaysLoad = true,
        loadKey = "live-following-${room.roomId}",
      )
      Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(
          room.title,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          "${room.uname} · ${room.watchedText ?: "直播中"}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun LiveRoomCard(
  room: LiveSearchRoom,
  index: Int,
  selectedArea: LiveAreaFilter,
  hiddenCoverItemId: String?,
  onRoomClick: (LiveSearchRoom, LiveHomeSourceAnchor, Rect) -> Unit,
  onRoomBoundsChanged: (LiveHomeSourceAnchor, Rect) -> Unit,
) {
  var coverBounds by remember(room.roomId) { mutableStateOf(Rect.Zero) }
  val sourceAnchor = LiveHomeSourceAnchor.feed(room.roomId, selectedArea.stableId)
  val card =
    FeedItem(
      id = room.stableId,
      title = room.title,
      videoUrl = "https://live.bilibili.com/${room.roomId}",
      coverUrl = room.currentDisplayCoverUrl(),
      uploader = room.uname,
      playCount = null,
      duration = null,
      uploaderFace = room.faceUrl,
      uploaderMid = room.uid,
      description =
        listOfNotNull(room.parentAreaName, room.areaName).distinct().joinToString(" · "),
    )
  VideoCardReveal(
    index = index,
    batchKey = "${selectedArea.stableId}:${card.id}",
    itemKey = room.stableId,
  ) {
    PressableVideoCard(
      onClick = { onRoomClick(room, sourceAnchor, coverBounds) },
      onLongClick = {},
    ) {
      FeedCardContent(
        item = card,
        metadataMode = FeedCardMetadataMode.LIVE,
        profileClickEnabled = false,
        coverVisible = sourceAnchor.stableId != hiddenCoverItemId,
        liveStatusText = "直播中",
        liveSecondaryText =
          listOfNotNull(room.parentAreaName, room.areaName).distinct().joinToString(" · "),
        liveTrailingText = room.watchedText,
        onCoverBoundsChanged = {
          coverBounds = it
          onRoomBoundsChanged(sourceAnchor, it)
        },
      )
    }
  }
}

@Composable
private fun LiveAreaIndexScreen(
  groups: List<LiveAreaGroup>,
  selectedArea: LiveAreaFilter,
  onBack: () -> Unit,
  onAreaSelected: (LiveAreaFilter) -> Unit,
  onHorizontalRailInteractionChanged: (Boolean) -> Unit,
) {
  var selectedParentId by
    remember(groups, selectedArea.parentAreaId) {
      mutableStateOf(
        selectedArea.parentAreaId.takeIf { it > 0 } ?: groups.firstOrNull()?.parent?.parentAreaId
      )
    }
  val selectedGroup = groups.firstOrNull { it.parent.parentAreaId == selectedParentId }
  Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onBack) { Text("返回") }
      Text(
        "所有分区",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
      )
    }
    LazyRow(
      modifier =
        Modifier.fillMaxWidth().testTag("live_area_index_parent_rail").pointerInput(Unit) {
          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onHorizontalRailInteractionChanged(true)
            try {
              do {
                val event = awaitPointerEvent()
              } while (event.changes.any { it.pressed })
            } finally {
              onHorizontalRailInteractionChanged(false)
            }
          }
        },
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        LiveAreaChip(
          area = LiveAreaFilter(0, name = "推荐"),
          selected = selectedParentId == null,
          onClick = { onAreaSelected(LiveAreaFilter(0, name = "推荐")) },
        )
      }
      items(groups, key = { it.parent.stableId }) { group ->
        LiveAreaChip(
          area = group.parent,
          selected = group.parent.parentAreaId == selectedParentId,
          onClick = { selectedParentId = group.parent.parentAreaId },
        )
      }
    }
    if (selectedGroup == null) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("选择一个分区查看子分区", color = MaterialTheme.colorScheme.onBackground)
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize().padding(top = 14.dp),
        contentPadding =
          PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = NavigationCardBottomClearance,
          ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        item {
          LiveAreaIndexCard(
            area = selectedGroup.parent,
            selected = selectedArea.stableId == selectedGroup.parent.stableId,
            onClick = { onAreaSelected(selectedGroup.parent) },
          )
        }
        items(selectedGroup.children, key = LiveAreaFilter::stableId) { area ->
          LiveAreaIndexCard(
            area = area,
            selected = selectedArea.stableId == area.stableId,
            onClick = { onAreaSelected(area) },
          )
        }
      }
    }
  }
}

@Composable
private fun LiveAreaIndexCard(area: LiveAreaFilter, selected: Boolean, onClick: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth().height(92.dp).clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f),
    contentColor =
      if (selected) MaterialTheme.colorScheme.onPrimaryContainer
      else MaterialTheme.colorScheme.onSurfaceVariant,
  ) {
    Column(
      Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (!area.iconUrl.isNullOrBlank()) {
        CoverImage(
          coverUrl = area.iconUrl,
          contentDescription = null,
          modifier = Modifier.size(36.dp).clip(CircleShape),
          shape = CircleShape,
          enforceAspectRatio = false,
          alwaysLoad = true,
          loadKey = "live-area-${area.stableId}",
        )
      }
      Text(
        area.name,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color =
          if (selected) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun LiveAreaChip(area: LiveAreaFilter, selected: Boolean, onClick: () -> Unit) {
  Surface(
    modifier = Modifier.height(34.dp).clickable(onClick = onClick),
    shape = CircleShape,
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Box(Modifier.padding(horizontal = 15.dp), contentAlignment = Alignment.Center) {
      Text(
        area.name,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color =
          if (selected) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun LiveHomeMessage(message: String, loading: Boolean = false) {
  Box(
    Modifier.fillMaxWidth().height(120.dp),
    contentAlignment = Alignment.Center,
  ) {
    if (loading) {
      CircularProgressIndicator(modifier = Modifier.size(24.dp))
    } else {
      Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun LiveHomeError(detail: String, onRetry: () -> Unit) {
  Column(
    Modifier.fillMaxWidth().padding(vertical = 30.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = onRetry) { Text("重试") }
  }
}

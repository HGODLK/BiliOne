package dev.openbili.webdemo.my

/**
 * 历史记录面板：按日期锚点独立续页，并支持全部/视频/直播/专栏筛选。
 */

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.request.allowHardware
import coil3.request.ImageRequest
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.BiliEmotePackage
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.FollowingUser
import dev.openbili.webdemo.api.MessageTargetKind
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.article.ArticleCard
import dev.openbili.webdemo.BuildConfig
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedImageLoadPolicy
import dev.openbili.webdemo.feed.FeedImageLoadMode
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import dev.openbili.webdemo.feed.rememberGridFeedImageLoadPolicy
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.settings.AdvancedAudioPriority
import dev.openbili.webdemo.settings.AppCacheManager
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.canSelectPreferredResolution
import dev.openbili.webdemo.settings.detectSimAvailability
import dev.openbili.webdemo.settings.DeviceMediaCapabilities
import dev.openbili.webdemo.settings.PreferredResolutionMode
import dev.openbili.webdemo.settings.SimAvailability
import dev.openbili.webdemo.settings.ThemeAccent
import dev.openbili.webdemo.settings.ThemeMode
import dev.openbili.webdemo.ui.controlFocusOutline
import dev.openbili.webdemo.ui.HomeHubTab
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import dev.openbili.webdemo.ui.OfficialVerificationIcon
import dev.openbili.webdemo.ui.OfficialVerificationIconSize
import dev.openbili.webdemo.ui.PressableVideoCard
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.RootAccountHeader
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.VideoCardReveal
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.video.BiliRichText
import dev.openbili.webdemo.video.CommentAvatarPaletteCache
import dev.openbili.webdemo.video.CommentEmoteMarkerRegistry
import dev.openbili.webdemo.video.CommentImagePreviewOverlay
import dev.openbili.webdemo.video.CommentImagePreviewSession
import dev.openbili.webdemo.video.CommentProfileAnchor
import dev.openbili.webdemo.video.CommentRow
import dev.openbili.webdemo.video.CommentTextEditor
import dev.openbili.webdemo.video.CommentToolPage
import dev.openbili.webdemo.video.CommentToolPanel
import dev.openbili.webdemo.video.extractAvatarDominantColors
import dev.openbili.webdemo.video.readableCommentCardColor
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 历史记录面板组合体。 */
@Composable
internal fun HistoryPanel(
  state: MyUiState,
  onVideo: (FeedItem, Rect) -> Unit,
  onBangumi: (SpaceContentCard, FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  onArticle: (ArticleItem, Rect) -> Unit,
  onArticleBounds: (ArticleItem, Rect) -> Unit,
  onLive: (LiveSearchRoom, Rect) -> Unit,
  onFilter: (HistoryFilter) -> Unit,
  onLoadMore: (HistoryPeriod) -> Unit,
  onLoadThrough: (HistoryPeriod) -> Unit,
  onSearch: (String) -> Unit,
  onLoadMoreSearch: () -> Unit,
  hiddenCoverItemId: String?,
  hiddenArticleItemId: String?,
) {
  val controlMode = LocalControlMode.current
  val gridState = rememberLazyGridState()
  val scope = rememberCoroutineScope()
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(gridState)
  var searchVisible by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf("") }
  var submittedQuery by remember { mutableStateOf("") }
  var preciseMode by remember { mutableStateOf(false) }
  var selectedMinute by remember { mutableStateOf(0) }
  var pendingTarget by remember { mutableStateOf<HistoryTimelineTarget?>(null) }
  val normalizedQuery = query.trim()
  val searchCommitted = normalizedQuery.isNotBlank() && normalizedQuery == submittedQuery
  val displayedItems =
    remember(state.historyItems, state.historySearch, normalizedQuery, submittedQuery) {
      val items =
        when {
        normalizedQuery.isBlank() -> state.historyItems
        !searchCommitted -> emptyList()
        state.historySearch.query == normalizedQuery -> state.historySearch.items
        else -> emptyList()
        }
      // 服务端“全部”返回的是混合业务流，渲染前再按观看时间兜底，避免分页边界把
      // 视频、直播、专栏或番剧固定成业务分组；“正在追”不经过此页面。
      items.sortedWith(compareByDescending<HistoryCardItem> { it.viewAt }.thenBy { it.stableId })
    }
  val gridEntries =
    remember(displayedItems, state.historyPeriods, normalizedQuery) {
      historyGridEntries(
        items = displayedItems,
        periodStates = state.historyPeriods,
        includeAnchors = normalizedQuery.isBlank(),
      )
    }
  val latestGridEntries by rememberUpdatedState(gridEntries)
  val latestHistoryPeriods by rememberUpdatedState(state.historyPeriods)
  val latestOnLoadMore by rememberUpdatedState(onLoadMore)
  val historyScrollLocked =
    normalizedQuery.isBlank() &&
      (state.loading ||
        state.historyTimeline.loading ||
        state.historyPeriods.values.any { it.loading })
  val effectiveImageLoadPolicy =
    if (historyScrollLocked) FeedImageLoadPolicy.Paused else imageLoadPolicy
  val currentPeriod by
    remember(gridEntries, gridState) {
      derivedStateOf {
        gridEntries.getOrNull(gridState.firstVisibleItemIndex)?.period ?: HistoryPeriod.TODAY
      }
    }
  val preciseMaxMinute =
    remember(currentPeriod) {
      if (currentPeriod == HistoryPeriod.TODAY) {
        (historyMinuteOfDay(System.currentTimeMillis() / 1_000L) / 12) * 12
      } else {
        1_439
      }
    }
  LaunchedEffect(state.historyFilter) {
    query = ""
    submittedQuery = ""
    gridState.scrollToItem(0)
  }
  LaunchedEffect(pendingTarget, state.historyTimeline, gridEntries) {
    val target = pendingTarget ?: return@LaunchedEffect
    if (
      state.historyTimeline.target != target.period ||
        state.historyTimeline.loading ||
        state.historyTimeline.error != null
    ) {
      return@LaunchedEffect
    }
    val index = gridEntries.indexOfFirst {
      it is HistoryGridEntry.Section && it.period == target.period
    }
    if (index >= 0) {
      gridState.animateScrollToItem(index)
      pendingTarget = null
    }
  }
  LaunchedEffect(historyScrollLocked) {
    if (historyScrollLocked) gridState.stopScroll()
  }
  // 快速滑动时日期标题可能直接被跳过。根据当前可见卡片反推日期锚点，确保目标日期
  // 即使没有进入组合树也会开始自己的分页，不再等到“更早”列表底部才触发。
  LaunchedEffect(normalizedQuery, state.historyFilter) {
    if (normalizedQuery.isNotBlank()) return@LaunchedEffect
    snapshotFlow {
        val layoutInfo = gridState.layoutInfo
        val firstVisible = layoutInfo.visibleItemsInfo.minOfOrNull { it.index } ?: -1
        val lastVisible = layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
        if (firstVisible < 0 || lastVisible < 0) {
          emptyList()
        } else {
          val start = (firstVisible - 2).coerceAtLeast(0)
          val end = (lastVisible + 2).coerceAtMost(latestGridEntries.size - 1)
          if (start > end) emptyList()
          else {
            (start..end)
              .mapNotNull { latestGridEntries.getOrNull(it)?.period }
              .distinct()
          }
        }
      }
      .distinctUntilChanged()
      .collect { visiblePeriods ->
        visiblePeriods.forEach { period ->
          val periodState = latestHistoryPeriods[period]
          if (periodState != null && !periodState.initialized && !periodState.loading) {
            gridState.stopScroll()
            latestOnLoadMore(period)
          }
        }
      }
  }
  LaunchedEffect(preciseMode, selectedMinute, currentPeriod, gridEntries) {
    if (!preciseMode || currentPeriod == HistoryPeriod.EARLIER) return@LaunchedEffect
    val index =
      gridEntries
        .withIndex()
        .filter { (_, entry) ->
          entry is HistoryGridEntry.Card && entry.period == currentPeriod
        }
        .minByOrNull { (_, entry) ->
          val history = (entry as HistoryGridEntry.Card).history
          abs(historyMinuteOfDay(history.viewAt) - selectedMinute)
        }
        ?.index ?: return@LaunchedEffect
    gridState.scrollToItem(index)
  }
  BackHandler(enabled = searchVisible || preciseMode) {
    if (preciseMode) preciseMode = false
    else {
      searchVisible = false
      query = ""
      submittedQuery = ""
      onSearch("")
    }
  }
  fun submitHistorySearch() {
    val normalized = query.trim()
    submittedQuery = normalized
    onSearch(normalized)
  }
  Column(Modifier.fillMaxSize()) {
    LazyRow(
      modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(HistoryFilter.entries, key = { it.name }) { filter ->
        FilterChip(
          selected = state.historyFilter == filter,
          enabled = filter.enabled,
          onClick = { onFilter(filter) },
          label = { Text(filter.label) },
        )
      }
    }
    AnimatedVisibility(
      visible = searchVisible,
      enter = fadeIn(tween(160)),
      exit = fadeOut(tween(120)),
    ) {
      OutlinedTextField(
        value = query,
        onValueChange = {
          query = it.take(60)
          if (it.trim().isBlank()) {
            submittedQuery = ""
            onSearch("")
          }
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submitHistorySearch() }),
        label = { Text("搜索历史记录") },
        placeholder = { Text("输入标题或作者，按回车搜索") },
        trailingIcon = {
          IconButton(onClick = { submitHistorySearch() }) {
            Icon(Icons.Default.Search, contentDescription = "搜索历史记录")
          }
        },
      )
    }
    Box(Modifier.weight(1f)) {
      CompositionLocalProvider(LocalFeedImageLoadPolicy provides effectiveImageLoadPolicy) {
        LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          state = gridState,
          userScrollEnabled = !historyScrollLocked,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(end = 136.dp, bottom = NavigationCardBottomClearance),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (displayedItems.isEmpty() && !state.loading && normalizedQuery.isNotBlank()) {
            item(
              key = "history_empty_${state.historyFilter}_$query",
              span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
            ) {
              Box(
                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  when {
                    normalizedQuery.isNotBlank() && !searchCommitted -> "按回车搜索历史记录"
                    state.historySearch.loading -> "正在搜索历史记录…"
                    state.historySearch.error != null -> state.historySearch.error
                    normalizedQuery.isNotBlank() -> "没有找到相关历史"
                    state.historyFilter == HistoryFilter.ARTICLE -> "暂无专栏历史"
                    state.historyFilter == HistoryFilter.LIVE -> "暂无直播历史"
                    else -> "暂无历史记录"
                  },
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          itemsIndexed(
            items = gridEntries,
            key = { _, entry -> entry.key },
            span = { _, entry ->
              androidx.compose.foundation.lazy.grid.GridItemSpan(
                if (entry !is HistoryGridEntry.Card) maxLineSpan else 1
              )
            },
          ) { index, entry ->
            when (entry) {
              is HistoryGridEntry.Section -> {
                val periodState = state.historyPeriods[entry.period]
                LaunchedEffect(state.historyFilter, entry.period) {
                  if (periodState != null && !periodState.initialized) onLoadMore(entry.period)
                }
                HistorySectionDivider(entry.period)
              }
              is HistoryGridEntry.Status -> {
                HistoryPeriodStatus(
                  period = entry.period,
                  state = state.historyPeriods[entry.period],
                  onRetry = { onLoadMore(entry.period) },
                )
              }
              is HistoryGridEntry.LoadMore -> {
                val periodState = state.historyPeriods[entry.period]
                LaunchedEffect(
                  state.historyFilter,
                  entry.period,
                  periodState?.cursor,
                  periodState?.hasMore,
                ) {
                  if (periodState?.let { it.hasMore && !it.loading } == true) {
                    onLoadMore(entry.period)
                  }
                }
                Box(
                  Modifier.fillMaxWidth().padding(16.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
              }
              is HistoryGridEntry.Card -> {
                val history = entry.history
                VideoCardReveal(
                  index = index,
                  batchKey = displayedItems.firstOrNull()?.stableId,
                  itemKey = history.stableId,
                ) {
                  when (history) {
                    is HistoryCardItem.Video -> {
                      var coverBounds by remember(history.item.id) { mutableStateOf(Rect.Zero) }
                      PressableVideoCard(
                        onClick = { onVideo(history.item, coverBounds) },
                        onLongClick = { onVideoLongClick(history.item) },
                      ) {
                        MyVideoCardContent(
                          item = history.item,
                          loadKey = history.stableId,
                          coverVisible = history.item.id != hiddenCoverItemId,
                          onCoverBoundsChanged = { coverBounds = it },
                          historyLabel = formatHistoryWatchTime(history.viewAt),
                        )
                      }
                    }
                    is HistoryCardItem.Bangumi -> {
                      var coverBounds by remember(history.item.id) { mutableStateOf(Rect.Zero) }
                      PressableVideoCard(
                        onClick = { onBangumi(history.bangumi, history.item, coverBounds) },
                        onLongClick = { onVideoLongClick(history.item) },
                      ) {
                        MyVideoCardContent(
                          item = history.item,
                          loadKey = history.stableId,
                          coverVisible = history.item.id != hiddenCoverItemId,
                          onCoverBoundsChanged = { coverBounds = it },
                          historyLabel = formatHistoryWatchTime(history.viewAt),
                          mediaBadge = history.mediaLabel,
                        )
                      }
                    }
                    is HistoryCardItem.Article ->
                      ArticleCard(
                        article = history.item,
                        coverVisible = history.item.stableId != hiddenArticleItemId,
                        onClick = { bounds -> onArticle(history.item, bounds) },
                        onBoundsChanged = { bounds -> onArticleBounds(history.item, bounds) },
                        loadKey = history.stableId,
                        historyLabel = formatHistoryWatchTime(history.viewAt),
                      )
                    is HistoryCardItem.Live -> {
                      var coverBounds by remember(history.room.roomId) { mutableStateOf(Rect.Zero) }
                      val room = history.room
                      val card =
                        FeedItem(
                          id = room.stableId,
                          title = room.title,
                          videoUrl = "https://live.bilibili.com/${room.roomId}",
                          coverUrl = room.keyframeUrl ?: room.coverUrl.orEmpty(),
                          uploader = room.uname,
                          playCount = null,
                          duration = null,
                          uploaderFace = room.faceUrl,
                          uploaderMid = room.uid,
                          description =
                            listOfNotNull(room.parentAreaName, room.areaName)
                              .distinct()
                              .joinToString(" · "),
                        )
                      PressableVideoCard(
                        onClick = { onLive(room, coverBounds) },
                        onLongClick = {},
                      ) {
                        MyVideoCardContent(
                          item = card,
                          loadKey = history.stableId,
                          coverVisible = room.stableId != hiddenCoverItemId,
                          onCoverBoundsChanged = { coverBounds = it },
                          historyLabel = formatHistoryWatchTime(history.viewAt),
                          mediaBadge = if (room.liveStatus == 1) "直播中" else "未开播",
                        )
                      }
                    }
                  }
                }
              }
            }
          }
          if (searchCommitted && state.historySearch.hasMore) {
            item(
              key = "history_search_load_more_${state.historySearch.query}_${state.historySearch.page}",
              span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
            ) {
              LaunchedEffect(
                state.historySearch.query,
                state.historySearch.page,
                imageLoadPolicy.mode,
              ) {
                if (imageLoadPolicy.mode != FeedImageLoadMode.PAUSED) onLoadMoreSearch()
              }
              Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
              }
            }
          }
        }
      }
      if (normalizedQuery.isBlank()) {
        HistoryTimeline(
          selected = currentPeriod,
          onPeriod = { period ->
            pendingTarget = HistoryTimelineTarget(period)
            onLoadThrough(period)
          },
          timelineLoading = state.historyTimeline.loading,
          timelineError = state.historyTimeline.error,
          preciseMode = preciseMode,
          selectedMinute = selectedMinute,
          onPreciseModeChange = { enabled ->
            if (enabled && currentPeriod != HistoryPeriod.EARLIER) {
              val visibleHistory =
                gridEntries
                  .getOrNull(gridState.firstVisibleItemIndex)
                  .let { it as? HistoryGridEntry.Card }
                  ?.history
                  ?: gridEntries.drop(gridState.firstVisibleItemIndex).firstNotNullOfOrNull {
                    (it as? HistoryGridEntry.Card)?.history
                  }
              selectedMinute =
                (visibleHistory?.viewAt?.let(::historyMinuteOfDay) ?: 0).coerceIn(
                  0,
                  preciseMaxMinute,
                )
            }
            preciseMode = enabled && currentPeriod != HistoryPeriod.EARLIER
          },
          onMinuteChange = { selectedMinute = it.coerceIn(0, preciseMaxMinute) },
          maxMinute = preciseMaxMinute,
          modifier =
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).fillMaxHeight().width(88.dp),
        )
      }
      if (!controlMode) {
        Column(
          modifier = Modifier.align(Alignment.BottomEnd).padding(end = 128.dp, bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SmallFloatingActionButton(
            onClick = { scope.launch { gridState.animateScrollToItem(0) } },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "回到历史记录顶部")
          }
          SmallFloatingActionButton(
            onClick = {
              searchVisible = !searchVisible
              if (!searchVisible) {
                query = ""
                submittedQuery = ""
                onSearch("")
              }
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ) {
            Icon(Icons.Default.Search, contentDescription = "搜索历史记录")
          }
        }
      }
    }
  }
}

@Composable
private fun HistorySectionDivider(period: HistoryPeriod) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    Text(
      period.label,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
    )
    HorizontalDivider(
      modifier = Modifier.weight(1f),
      color = MaterialTheme.colorScheme.outlineVariant,
    )
  }
}

private data class HistoryTimelineTarget(val period: HistoryPeriod)

private sealed interface HistoryGridEntry {
  val key: String
  val period: HistoryPeriod

  data class Section(override val period: HistoryPeriod) : HistoryGridEntry {
    override val key: String = "history_section_${period.name}"
  }

  data class Status(override val period: HistoryPeriod) : HistoryGridEntry {
    override val key: String = "history_status_${period.name}"
  }

  data class LoadMore(override val period: HistoryPeriod) : HistoryGridEntry {
    override val key: String = "history_load_more_${period.name}"
  }

  data class Card(
    val history: HistoryCardItem,
    override val period: HistoryPeriod,
    override val key: String,
  ) :
    HistoryGridEntry {
  }
}

private fun historyPeriodFor(timestampSeconds: Long): HistoryPeriod {
  if (timestampSeconds <= 0L) return HistoryPeriod.EARLIER
  val zone = ZoneId.systemDefault()
  val viewedDate = Instant.ofEpochSecond(timestampSeconds).atZone(zone).toLocalDate()
  val days = ChronoUnit.DAYS.between(viewedDate, LocalDate.now(zone)).coerceAtLeast(0L)
  return when (days) {
    0L -> HistoryPeriod.TODAY
    1L -> HistoryPeriod.YESTERDAY
    2L -> HistoryPeriod.DAY_BEFORE
    else -> HistoryPeriod.EARLIER
  }
}

private fun historyGridEntries(
  items: List<HistoryCardItem>,
  periodStates: Map<HistoryPeriod, HistoryPeriodLoadState>,
  includeAnchors: Boolean,
): List<HistoryGridEntry> = buildList {
  val usedKeys = HashSet<String>()
  val grouped = items.groupBy { historyPeriodFor(it.viewAt) }
  val periods = if (includeAnchors) HistoryPeriod.entries.toList() else grouped.keys.toList()
  periods.forEach { period ->
    val section = HistoryGridEntry.Section(period)
    add(section)
    usedKeys += section.key
    grouped[period].orEmpty().forEach { history ->
      val baseKey = history.stableId
      var key = baseKey
      var suffix = 1
      while (!usedKeys.add(key)) {
        key = "$baseKey#$suffix"
        suffix += 1
      }
      add(HistoryGridEntry.Card(history, period, key))
    }
    if (includeAnchors) {
      val periodState = periodStates[period]
      when {
        periodState == null || !periodState.initialized || periodState.loading ->
          add(HistoryGridEntry.Status(period))
        periodState.error != null -> add(HistoryGridEntry.Status(period))
        periodState.hasMore -> add(HistoryGridEntry.LoadMore(period))
        grouped[period].isNullOrEmpty() -> add(HistoryGridEntry.Status(period))
      }
    }
  }
}

@Composable
private fun HistoryPeriodStatus(
  period: HistoryPeriod,
  state: HistoryPeriodLoadState?,
  onRetry: () -> Unit,
) {
  val error = state?.error
  Box(
    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
    contentAlignment = Alignment.Center,
  ) {
    when {
      state == null ->
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
      error != null ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(error, color = MaterialTheme.colorScheme.error)
          TextButton(onClick = onRetry) { Text("重试") }
        }
      state.loading || !state.initialized ->
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
      else -> Text("${period.label}暂无历史记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

private fun historyMinuteOfDay(timestampSeconds: Long): Int {
  if (timestampSeconds <= 0L) return 0
  val zone = ZoneId.systemDefault()
  val time = Instant.ofEpochSecond(timestampSeconds).atZone(zone)
  return time.hour * 60 + time.minute
}

private fun formatHistoryWatchTime(timestampSeconds: Long): String {
  if (timestampSeconds <= 0L) return "最后观看时间未知"
  val zone = ZoneId.systemDefault()
  val viewed = Instant.ofEpochSecond(timestampSeconds).atZone(zone)
  val today = LocalDate.now(zone)
  val datePrefix =
    when (ChronoUnit.DAYS.between(viewed.toLocalDate(), today)) {
      0L -> "今天"
      1L -> "昨天"
      2L -> "前天"
      else ->
        if (viewed.year == today.year) "%02d-%02d".format(viewed.monthValue, viewed.dayOfMonth)
        else "%04d-%02d-%02d".format(viewed.year, viewed.monthValue, viewed.dayOfMonth)
    }
  return "最后观看 $datePrefix %02d:%02d".format(viewed.hour, viewed.minute)
}

@Composable
private fun HistoryTimeline(
  selected: HistoryPeriod,
  onPeriod: (HistoryPeriod) -> Unit,
  timelineLoading: Boolean,
  timelineError: String?,
  preciseMode: Boolean,
  selectedMinute: Int,
  maxMinute: Int,
  onPreciseModeChange: (Boolean) -> Unit,
  onMinuteChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedPosition by
    animateFloatAsState(
      targetValue = selected.ordinal.toFloat(),
      animationSpec = tween(360, easing = FastOutSlowInEasing),
      label = "historyPeriodNode",
    )
  Surface(
    modifier = modifier.padding(vertical = 6.dp),
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
    tonalElevation = 3.dp,
    shadowElevation = 0.dp,
  ) {
    Crossfade(
      targetState = preciseMode,
      animationSpec = tween(180),
      label = "historyTimelineMode",
    ) { precise ->
      if (precise) {
        HistoryTimeScale(
          selectedMinute = selectedMinute,
          maxMinute = maxMinute,
          onMinuteChange = onMinuteChange,
          onClose = { onPreciseModeChange(false) },
          modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 10.dp),
        )
      } else {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 10.dp)) {
          val rowHeight = maxHeight / HistoryPeriod.entries.size
          val firstCenter = rowHeight / 2
          Box(
            Modifier.offset(x = 37.dp, y = firstCenter)
              .width(2.dp)
              .height(rowHeight * (HistoryPeriod.entries.size - 1))
              .background(MaterialTheme.colorScheme.outlineVariant)
          )
          Column(Modifier.fillMaxSize()) {
            HistoryPeriod.entries.forEach { period ->
              Row(
                modifier =
                  Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).clickable {
                    onPeriod(period)
                  },
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(Modifier.width(31.dp))
                Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) {
                  Box(
                    Modifier.size(8.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.outline)
                  )
                }
                Text(
                  period.label,
                  modifier = Modifier.padding(start = 5.dp),
                  style = MaterialTheme.typography.labelMedium,
                  color =
                    if (period == selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                )
              }
            }
          }
          val activeY = firstCenter + rowHeight * selectedPosition
          Box(
            Modifier.offset(x = 34.dp, y = activeY - 6.dp)
              .size(12.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary)
          )
          if (selected != HistoryPeriod.EARLIER) {
            Text(
              "<",
              modifier =
                Modifier.offset(y = activeY - 16.dp)
                  .clip(CircleShape)
                  .clickable { onPreciseModeChange(true) }
                  .padding(horizontal = 7.dp, vertical = 4.dp),
              color = MaterialTheme.colorScheme.primary,
            )
          }
          if (timelineLoading || !timelineError.isNullOrBlank()) {
            Surface(
              modifier = Modifier.align(Alignment.TopCenter),
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .96f),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                if (timelineLoading) {
                  CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                  Text("准备历史…", style = MaterialTheme.typography.labelSmall)
                } else {
                  Text(
                    timelineError.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HistoryTimeScale(
  selectedMinute: Int,
  maxMinute: Int,
  onMinuteChange: (Int) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var heightPx by remember { mutableStateOf(1f) }
  val currentSelectedMinute by rememberUpdatedState(selectedMinute)
  val primary = MaterialTheme.colorScheme.primary
  val outline = MaterialTheme.colorScheme.outline
  val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
  val density = androidx.compose.ui.platform.LocalDensity.current
  Box(modifier = modifier.onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }) {
    Canvas(Modifier.fillMaxSize()) {
      val right = size.width - 3.dp.toPx()
      val paint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
          color = labelColor.toArgb()
          textSize = 8.dp.toPx()
        }
      for (step in 0..120) {
        val minute = 1440 - step * 12
        val y = size.height * step / 120f
        val hourTick = minute % 60 == 0
        val future = minute > maxMinute
        val length = if (hourTick) 19.dp.toPx() else 10.dp.toPx()
        drawLine(
          color = outline.copy(alpha = if (future) .18f else if (hourTick) .78f else .42f),
          start = Offset(right - length, y),
          end = Offset(right, y),
          strokeWidth = if (hourTick) 1.5.dp.toPx() else 1.dp.toPx(),
          cap = StrokeCap.Round,
        )
        if (hourTick && minute in 60..1380) {
          drawContext.canvas.nativeCanvas.drawText(
            "%02d".format(minute / 60),
            right - 38.dp.toPx(),
            (y + 3.dp.toPx()).coerceIn(paint.textSize, size.height),
            paint.apply { alpha = if (future) 70 else 255 },
          )
        }
      }
      val currentY = (1f - selectedMinute / 1440f) * size.height
      drawLine(
        color = primary,
        start = Offset(right - 42.dp.toPx(), currentY),
        end = Offset(right, currentY),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
      )
      paint.color = primary.toArgb()
      paint.textSize = 10.dp.toPx()
      drawContext.canvas.nativeCanvas.drawText(
        "%02d:%02d".format(selectedMinute / 60, selectedMinute % 60),
        2.dp.toPx(),
        (currentY - 5.dp.toPx()).coerceIn(paint.textSize, size.height),
        paint,
      )
    }
    val currentYDp = with(density) { ((1f - selectedMinute / 1440f) * heightPx).toDp() }
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(34.dp)
          .offset(y = currentYDp - 17.dp)
          .pointerInput(heightPx, maxMinute) {
            var initialMinute = 0
            var dragDelta = 0f
            detectVerticalDragGestures(
              onDragStart = {
                initialMinute = currentSelectedMinute
                dragDelta = 0f
              },
              onVerticalDrag = { change, amount ->
                dragDelta += amount
                change.consume()
                onMinuteChange(
                  ((initialMinute - dragDelta / heightPx * 1_440f) / 12f)
                    .roundToInt()
                    .times(12)
                    .coerceIn(0, maxMinute)
                )
              },
              onDragEnd = onClose,
              onDragCancel = onClose,
            )
          }
          .clickable(onClick = onClose)
          .zIndex(1f)
    )
  }
}

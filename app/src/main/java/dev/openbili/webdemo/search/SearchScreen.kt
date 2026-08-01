package dev.openbili.webdemo.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.BitmapImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.imageLoader
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.SearchUser
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.article.ArticleCard
import dev.openbili.webdemo.feed.FeedCardContent
import dev.openbili.webdemo.feed.FeedCardMetadataMode
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedImageLoadMode
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import dev.openbili.webdemo.feed.rememberGridFeedImageLoadPolicy
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.profile.formatProfileFollowerCount
import dev.openbili.webdemo.profile.BangumiPosterCard
import dev.openbili.webdemo.video.CommentAuthorBadge
import dev.openbili.webdemo.video.CommentAvatarPaletteCache
import dev.openbili.webdemo.video.CommentLevelIcon
import dev.openbili.webdemo.video.extractAvatarDominantColors
import dev.openbili.webdemo.video.readableCommentCardColor
import dev.openbili.webdemo.ui.PressableVideoCard
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.VideoCardReveal
import dev.openbili.webdemo.ui.VideoShapeTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Search suggestions panel that expands from the search capsule in the top bar. */
@Composable
fun SearchScreen(
  state: SearchUiState,
  searchBounds: Rect,
  onQuery: (String) -> Unit,
  onSearch: (String) -> Unit,
  onClearHistory: () -> Unit,
  onBack: () -> Unit,
  onDismiss: () -> Unit,
  reduceMotion: Boolean = false,
) {
  val scope = rememberCoroutineScope()
  val showAnim = remember { Animatable(0f) }
  val scrimAnim = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    coroutineScope {
      launch {
        showAnim.animateTo(
          1f,
          if (reduceMotion) tween(100, easing = FastOutSlowInEasing)
          else spring(dampingRatio = 0.82f, stiffness = 420f),
        )
      }
      launch {
        scrimAnim.animateTo(
          1f,
          tween(if (reduceMotion) 80 else 220, easing = FastOutSlowInEasing),
        )
      }
    }
  }

  fun dismiss() {
    scope.launch {
      coroutineScope {
        launch { showAnim.animateTo(0f, tween(170, easing = FastOutSlowInEasing)) }
        launch { scrimAnim.animateTo(0f, tween(150, easing = FastOutSlowInEasing)) }
      }
      onDismiss()
    }
  }
  fun commitSearch(keyword: String) {
    onSearch(keyword)
  }

  BackHandler { dismiss() }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val screenWidthPx = with(density) { maxWidth.toPx() }
    val screenHeightPx = with(density) { maxHeight.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val panelWidth = minOf(470.dp, maxWidth - 32.dp)
    val panelWidthPx = with(density) { panelWidth.toPx() }
    val panelLeftPx =
      ((if (searchBounds.width > 0f) searchBounds.right else screenWidthPx - marginPx) -
          panelWidthPx)
        .coerceIn(marginPx, (screenWidthPx - panelWidthPx - marginPx).coerceAtLeast(marginPx))
    val panelTopPx =
      ((if (searchBounds.height > 0f) searchBounds.bottom else with(density) { 64.dp.toPx() }) +
          gapPx)
        .coerceAtMost(screenHeightPx - with(density) { 180.dp.toPx() })
    val panelTop = with(density) { panelTopPx.toDp() }
    val availableHeightPx = (screenHeightPx - panelTopPx - marginPx).coerceAtLeast(1f)
    val panelMaxHeight = with(density) { minOf(availableHeightPx, screenHeightPx * .5f).toDp() }
    Box(
      Modifier.fillMaxSize()
        .padding(top = panelTop)
        .graphicsLayer { alpha = scrimAnim.value.coerceIn(0f, 1f) }
        .background(Color.Black.copy(alpha = .08f))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = ::dismiss,
        )
    )

    Surface(
      modifier =
        Modifier.offset { IntOffset(panelLeftPx.toInt(), panelTopPx.toInt()) }
          .width(panelWidth)
          .heightIn(max = panelMaxHeight)
          .graphicsLayer {
            val p = showAnim.value.coerceIn(0f, 1f)
            scaleX = .94f + .06f * p
            scaleY = .94f + .06f * p
            alpha = p
            transformOrigin = TransformOrigin(1f, 0f)
          },
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 10.dp,
    ) {
      Column(Modifier.padding(18.dp)) {
        if (state.query.isBlank()) {
          if (state.history.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Text(
                "搜索历史",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = onClearHistory) { Text("清空") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(state.history) { keyword ->
                AssistChip(onClick = { commitSearch(keyword) }, label = { Text(keyword) })
              }
            }
          }
          Text(
            "热搜",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
          )
          LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(state.hot) { hot ->
              Text(
                hot.displayName,
                modifier =
                  Modifier.fillMaxWidth().clickable { commitSearch(hot.keyword) }.padding(12.dp),
              )
            }
          }
        } else {
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "搜索联想",
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.weight(1f),
            )
            if (state.suggestionsLoading) {
              CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
          }
          LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item {
              Text(
                "搜索 “${state.query}”",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier =
                  Modifier.fillMaxWidth().clickable { commitSearch(state.query) }.padding(12.dp),
              )
            }
            items(state.suggestions) { suggestion ->
              val query = state.query
              val start = suggestion.indexOf(query, ignoreCase = true)
              val display = buildAnnotatedString {
                append(suggestion)
                if (start >= 0) {
                  addStyle(
                    SpanStyle(
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.SemiBold,
                    ),
                    start,
                    start + query.length,
                  )
                }
              }
              Text(
                display,
                modifier =
                  Modifier.fillMaxWidth().clickable { commitSearch(suggestion) }.padding(12.dp),
              )
            }
          }
          if (!state.suggestionsLoading && state.suggestions.isEmpty()) {
            Text(
              "暂无联想词，按回车直接搜索",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
fun SearchResultsScreen(
  state: SearchUiState,
  gridState: LazyGridState,
  onCategory: (SearchCategory) -> Unit,
  onOrder: (SearchOrder) -> Unit,
  onArticleOrder: (ArticleSearchOrder) -> Unit,
  onVideo: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  onVideoBounds: (FeedItem, Rect) -> Unit,
  onVideoProfile: (Long, String?, String?, Rect) -> Unit,
  onBangumi: (SpaceContentCard, Rect) -> Unit,
  onLive: (LiveSearchRoom, Rect) -> Unit,
  onLiveBounds: (LiveSearchRoom, Rect) -> Unit,
  onArticle: (ArticleItem, Rect) -> Unit,
  onArticleBounds: (ArticleItem, Rect) -> Unit,
  onUser: (Long, String, String, Rect) -> Unit,
  onLoadMore: () -> Unit,
  onRefresh: () -> Unit,
  onRetry: () -> Unit,
  onBack: () -> Unit,
  hiddenCoverItemId: String? = null,
  hiddenArticleItemId: String? = null,
  backEnabled: Boolean = true,
  effectsEnabled: Boolean = true,
) {
  BackHandler(enabled = backEnabled, onBack = onBack)
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(gridState)
  val nearEnd by remember {
    derivedStateOf {
      val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      last >= gridState.layoutInfo.totalItemsCount - 5
    }
  }
  LaunchedEffect(
    nearEnd,
    state.hasMore,
    state.loadingMore,
    effectsEnabled,
    imageLoadPolicy.mode,
  ) {
    if (
      effectsEnabled &&
        nearEnd &&
        state.hasMore &&
        !state.loadingMore &&
        imageLoadPolicy.mode != FeedImageLoadMode.PAUSED
    ) onLoadMore()
  }
  LaunchedEffect(
    state.submittedQuery,
    state.category,
    state.order,
    state.articleOrder,
    effectsEnabled,
  ) {
    if (effectsEnabled && state.submittedQuery.isNotBlank()) gridState.scrollToItem(0)
  }
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
        }
        Text(
          text =
            state.submittedQuery
              .ifBlank { state.query }
              .takeIf(String::isNotBlank)
              ?.let {
                "“$it”的搜索结果"
              } ?: "搜索结果",
          modifier = Modifier.weight(1f).padding(start = 8.dp),
          style = MaterialTheme.typography.titleLarge,
          maxLines = 1,
        )
      }
      Row(
        Modifier.fillMaxWidth().padding(start = 58.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SearchCategory.entries.forEach { category ->
          val selected = state.category == category
          Column(
            modifier =
              Modifier.graphicsLayer { alpha = if (category.enabled) 1f else .38f }
                .clickable(enabled = category.enabled) { onCategory(category) }
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              category.title,
              color =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
              Modifier.padding(top = 5.dp)
                .width(30.dp)
                .height(3.dp)
                .background(
                  if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                  CircleShape,
                )
            )
          }
        }
      }
      AnimatedVisibility(
        visible =
          state.category == SearchCategory.COMPREHENSIVE ||
            state.category == SearchCategory.VIDEO ||
            state.category == SearchCategory.ARTICLE,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(100)),
      ) {
        Row(
          Modifier.fillMaxWidth().padding(start = 58.dp, top = 8.dp, bottom = 2.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (state.category == SearchCategory.ARTICLE) {
            ArticleSearchOrder.entries.forEach { order ->
              SearchOrderChip(
                title = order.title,
                selected = state.articleOrder == order,
                onClick = { onArticleOrder(order) },
              )
            }
          } else {
            SearchOrder.entries.forEach { order ->
              SearchOrderChip(
                title = order.title,
                selected = state.order == order,
                onClick = { onOrder(order) },
              )
            }
          }
        }
      }
      PullRefreshContainer(
        refreshing = state.loading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp),
      ) {
        CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
          LazyVerticalGrid(
            state = gridState,
            columns =
              GridCells.Fixed(
                if (state.category == SearchCategory.BANGUMI ||
                  state.category == SearchCategory.CINEMA
                ) 5
                else 3
              ),
            contentPadding = PaddingValues(bottom = NavigationCardBottomClearance),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
          if (state.category == SearchCategory.USER) {
            items(state.users, key = { it.mid }) { user ->
              SearchUserCard(user = user, onClick = onUser)
            }
          } else if (state.category == SearchCategory.LIVE) {
            itemsIndexed(state.liveRooms, key = { _, room -> room.stableId }) { index, room ->
              var coverBounds by remember(room.roomId) { mutableStateOf(Rect.Zero) }
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
                    listOfNotNull(room.parentAreaName, room.areaName).joinToString(" · "),
                )
              VideoCardReveal(
                index = index,
                batchKey = state.liveRooms.firstOrNull()?.stableId,
                itemKey = room.stableId,
              ) {
                PressableVideoCard(
                  onClick = { onLive(room, coverBounds) },
                  onLongClick = {},
                ) {
                  FeedCardContent(
                    item = card,
                    metadataMode = FeedCardMetadataMode.LIVE,
                    profileClickEnabled = false,
                    onProfileBoundsClick = { _, _ -> },
                    coverVisible = room.stableId != hiddenCoverItemId,
                    liveStatusText = if (room.liveStatus == 1) "直播中" else "未开播",
                    liveSecondaryText =
                      listOfNotNull(room.parentAreaName, room.areaName)
                        .distinct()
                        .joinToString(" · "),
                    onCoverBoundsChanged = {
                      coverBounds = it
                      onLiveBounds(room, it)
                    },
                  )
                }
              }
            }
          } else if (state.category == SearchCategory.ARTICLE) {
            itemsIndexed(state.articles, key = { _, article -> article.stableId }) { index, article
              ->
              VideoCardReveal(
                index = index,
                batchKey = state.articles.firstOrNull()?.stableId,
                itemKey = article.stableId,
              ) {
                ArticleCard(
                  article = article,
                  coverVisible = article.stableId != hiddenArticleItemId,
                  onClick = { bounds -> onArticle(article, bounds) },
                  onBoundsChanged = { bounds -> onArticleBounds(article, bounds) },
                )
              }
            }
          } else if (
            state.category == SearchCategory.BANGUMI ||
              state.category == SearchCategory.CINEMA
          ) {
            itemsIndexed(state.bangumiResults, key = { _, card -> card.id }) { index, card ->
              val video =
                FeedItem(
                  id = card.id,
                  title = card.title,
                  videoUrl = card.videoUrl,
                  coverUrl = card.coverUrl,
                  uploader = null,
                  playCount = null,
                  duration = null,
                  description = card.subtitle,
                )
              BangumiPosterCard(
                card = card,
                video = video,
                index = index,
                batchKey = state.bangumiResults.firstOrNull()?.id,
                hiddenCoverItemId = hiddenCoverItemId,
                onClick = { bounds -> onBangumi(card, bounds) },
                onLongClick = { onVideoLongClick(video) },
                onBoundsChanged = { bounds -> onVideoBounds(video, bounds) },
              )
            }
          } else {
            itemsIndexed(state.results, key = { _, video -> video.id }) { index, video ->
              var coverBounds by remember(video.id) { mutableStateOf(Rect.Zero) }
              VideoCardReveal(
                index = index,
                batchKey = state.results.firstOrNull()?.id,
                itemKey = video.id,
              ) {
                PressableVideoCard(
                  onClick = { onVideo(video, coverBounds) },
                  onLongClick = { onVideoLongClick(video) },
                ) {
                  FeedCardContent(
                    item = video,
                    profileClickEnabled = video.uploaderMid > 0L,
                    onProfileBoundsClick = { mid, bounds ->
                      onVideoProfile(mid, video.uploaderFace, video.uploader, bounds)
                    },
                    coverVisible = video.id != hiddenCoverItemId,
                    onCoverBoundsChanged = {
                      coverBounds = it
                      onVideoBounds(video, it)
                    },
                  )
                }
              }
            }
          }
          if (state.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
              Box(
                Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
              }
            }
          }
          }
        }
        if (
          state.loading &&
            state.results.isEmpty() &&
            state.bangumiResults.isEmpty() &&
            state.liveRooms.isEmpty() &&
            state.articles.isEmpty() &&
            state.users.isEmpty()
        )
          CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (
          state.searched && !state.loading && searchResultCount(state) == 0 && state.error == null
        ) {
          Text("(・_・;) 没有找到相关内容", modifier = Modifier.align(Alignment.Center))
        }
        state.error?.let {
          Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("重试") }
          }
        }
      }
    }
  }
}

@Composable
private fun SearchUserCard(
  user: SearchUser,
  onClick: (Long, String, String, Rect) -> Unit,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = rememberCoroutineScope()
  val loadPolicy = LocalFeedImageLoadPolicy.current
  val darkTheme = MaterialTheme.colorScheme.surface.luminance() < .5f
  val surface = MaterialTheme.colorScheme.surface
  var avatarBounds by remember(user.mid) { mutableStateOf(Rect.Zero) }
  var avatarLoaded by remember(user.face) { mutableStateOf(false) }
  var avatarColors by
    remember(user.face, darkTheme) {
      mutableStateOf(CommentAvatarPaletteCache.get(user.face).orEmpty())
    }
  val colors =
    remember(avatarColors, surface, darkTheme) {
      if (avatarColors.isEmpty()) listOf(surface, surface)
      else avatarColors.take(2).map { readableCommentCardColor(it, surface, darkTheme) }
    }
  val avatarModel =
    remember(user.face, avatarLoaded, loadPolicy) {
      if (avatarLoaded || loadPolicy.permits(user.mid.toString())) {
        ImageRequest.Builder(context).data(user.face).size(96, 96).allowHardware(false).build()
      } else null
    }
  Surface(
    modifier =
      Modifier.fillMaxWidth().height(138.dp).clickable {
        onClick(user.mid, user.face, user.name, avatarBounds)
      },
    shape = VideoShapeTokens.Card,
    color = Color.Transparent,
    tonalElevation = 0.dp,
    shadowElevation = 3.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      Modifier.fillMaxSize()
        .background(Brush.horizontalGradient(colors))
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AsyncImage(
        model = avatarModel,
        contentDescription = user.name,
        modifier =
          Modifier.size(68.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onGloballyPositioned { avatarBounds = it.boundsInRoot() },
        contentScale = ContentScale.Crop,
        onSuccess = { result ->
          avatarLoaded = true
          if (avatarColors.isEmpty()) {
            val bitmap = (result.result.image as? BitmapImage)?.bitmap ?: return@AsyncImage
            scope.launch {
              val extracted =
                CommentAvatarPaletteCache.resolve(user.face) {
                  withContext(Dispatchers.Default) { extractAvatarDominantColors(bitmap) }
                }
              if (extracted.isNotEmpty()) avatarColors = extracted
            }
          }
        },
      )
      Column(
        Modifier.weight(1f).padding(start = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            user.name,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (user.vipActive) Color(0xFFF06A94) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
          )
          CommentLevelIcon(user.level, loadKey = user.mid.toString())
          if (user.vipActive) {
            CommentAuthorBadge(user.vipLabel.ifBlank { "大会员" }, vip = true)
          }
        }
        Text(
          "${formatProfileFollowerCount(user.fans)} 粉丝 · ${user.videoCount} 个视频",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
        Text(
          user.sign.ifBlank { "暂无简介" },
          modifier = Modifier.height(36.dp),
          maxLines = 2,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun SearchOrderChip(title: String, selected: Boolean, onClick: () -> Unit) {
  Surface(
    modifier = Modifier.clickable(onClick = onClick),
    shape = MaterialTheme.shapes.medium,
    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    contentColor =
      if (selected) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onSurfaceVariant,
  ) {
    Text(
      title,
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
    )
  }
}

private fun searchResultCount(state: SearchUiState): Int =
  when (state.category) {
    SearchCategory.USER -> state.users.size
    SearchCategory.LIVE -> state.liveRooms.size
    SearchCategory.ARTICLE -> state.articles.size
    SearchCategory.BANGUMI,
    SearchCategory.CINEMA -> state.bangumiResults.size
    else -> state.results.size
  }

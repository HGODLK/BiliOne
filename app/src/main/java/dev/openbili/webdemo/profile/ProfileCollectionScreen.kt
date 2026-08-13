package dev.openbili.webdemo.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.openbili.webdemo.api.SpaceCollectionType
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceProfile
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedCardContent
import dev.openbili.webdemo.feed.FeedImageLoadMode
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import dev.openbili.webdemo.feed.rememberGridFeedImageLoadPolicy
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.ui.PressableVideoCard
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.VideoCardReveal
import kotlinx.coroutines.delay

@Composable
internal fun ProfileCollectionGrid(
  collections: List<SpaceContentCard>,
  loading: Boolean,
  error: String?,
  searchQuery: String,
  profile: SpaceProfile?,
  selectedCollectionId: String?,
  collectionVideos: List<FeedItem>,
  collectionPage: Int,
  collectionHasMore: Boolean,
  collectionLoading: Boolean,
  collectionError: String?,
  collectionTotal: Int,
  settings: AppSettings,
  onSelectedCollectionChange: (SpaceContentCard?) -> Unit,
  onLoadMore: () -> Unit,
  onVideoClick: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  hiddenCoverItemId: String?,
  onVideoBoundsChanged: (FeedItem, Rect) -> Unit,
  onScrollStarted: () -> Unit,
  backHandlingEnabled: Boolean,
) {
  val state = rememberLazyGridState()
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(state)
  val cardBounds = remember(profile?.mid) { mutableMapOf<String, Rect>() }
  var displayedCollectionId by remember(profile?.mid) { mutableStateOf(selectedCollectionId) }
  var transitionSourceBounds by remember(profile?.mid) { mutableStateOf(Rect.Zero) }
  var transitionTargetBounds by remember(profile?.mid) { mutableStateOf(Rect.Zero) }
  val detailProgress =
    remember(profile?.mid) { Animatable(if (selectedCollectionId == null) 0f else 1f) }
  var detailContentReady by
    remember(profile?.mid) {
      mutableStateOf(selectedCollectionId != null)
    }
  val filteredCollections =
    remember(collections, searchQuery) {
      collections.filter { collection ->
        matchesProfileContentSearch(searchQuery, collection.title, collection.subtitle)
      }
    }
  val displayedCollection = collections.firstOrNull { it.id == displayedCollectionId }

  BackHandler(enabled = backHandlingEnabled && displayedCollectionId != null) {
    onSelectedCollectionChange(null)
  }
  LaunchedEffect(searchQuery) { state.scrollToItem(0) }
  LaunchedEffect(state.isScrollInProgress) {
    if (state.isScrollInProgress) onScrollStarted()
  }
  LaunchedEffect(selectedCollectionId) {
    if (selectedCollectionId != null) {
      if (collections.none { it.id == selectedCollectionId }) return@LaunchedEffect
      displayedCollectionId = selectedCollectionId
      detailContentReady = false
      if (transitionSourceBounds.hasUsableSize()) {
        detailProgress.snapTo(0f)
        repeat(3) {
          withFrameNanos {}
          if (transitionTargetBounds.hasUsableSize()) return@repeat
        }
        detailProgress.animateTo(
          1f,
          tween(if (settings.reduceMotion) 140 else 460, easing = FastOutSlowInEasing),
        )
        withFrameNanos {}
        detailContentReady = true
      } else {
        detailProgress.snapTo(1f)
        detailContentReady = true
      }
    } else if (displayedCollectionId != null) {
      detailContentReady = false
      val latestSource = cardBounds[displayedCollectionId] ?: transitionSourceBounds
      if (latestSource.hasUsableSize()) transitionSourceBounds = latestSource
      detailProgress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 120 else 400, easing = FastOutSlowInEasing),
      )
      delay(16)
      displayedCollectionId = null
      transitionSourceBounds = Rect.Zero
      transitionTargetBounds = Rect.Zero
    }
  }

  Box(Modifier.fillMaxSize()) {
    when {
      loading && collections.isEmpty() ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        }
      collections.isEmpty() ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            error ?: if (searchQuery.isBlank()) "暂无公开合集和系列" else "没有找到相关内容",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      else ->
        CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = NavigationCardBottomClearance),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            if (filteredCollections.isEmpty()) {
              item(key = "collection_filter_empty", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                  Modifier.fillMaxWidth().padding(vertical = 48.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    "没有找到相关内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
            itemsIndexed(
              filteredCollections,
              key = { _, collection -> collection.id },
            ) { index, collection ->
              VideoCardReveal(
                index = index,
                batchKey = collections.firstOrNull()?.id,
                itemKey = collection.id,
              ) {
                CollectionCard(
                  collection = collection,
                  onBoundsChanged = { cardBounds[collection.id] = it },
                  onClick = {
                    transitionSourceBounds = cardBounds[collection.id] ?: Rect.Zero
                    displayedCollectionId = collection.id
                    onSelectedCollectionChange(collection)
                  },
                )
              }
            }
          }
        }
    }

    displayedCollection?.let { collection ->
      BoxWithConstraints(Modifier.fillMaxSize().zIndex(10f)) {
        Box(
          Modifier.offset(12.dp, 12.dp)
            .size(
              width = (maxWidth - 24.dp).coerceAtLeast(1.dp),
              height = (maxHeight - 24.dp).coerceAtLeast(1.dp),
            )
            .onGloballyPositioned { transitionTargetBounds = it.boundsInRoot() }
        ) {
          CollectionDetailTransition(
            sourceBounds = transitionSourceBounds,
            targetBounds = transitionTargetBounds,
            progress = { detailProgress.value },
            contentReady = detailContentReady,
          ) {
            key(collection.id) {
              CollectionDetail(
                collection = collection,
                videos = collectionVideos,
                page = collectionPage,
                hasMore = collectionHasMore,
                loading = collectionLoading,
                error = collectionError,
                total = collectionTotal,
                onDismiss = { onSelectedCollectionChange(null) },
                onLoadMore = onLoadMore,
                onVideoClick = onVideoClick,
                onVideoLongClick = onVideoLongClick,
                hiddenCoverItemId = hiddenCoverItemId,
                onVideoBoundsChanged = onVideoBoundsChanged,
                onScrollStarted = onScrollStarted,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CollectionCard(
  collection: SpaceContentCard,
  onBoundsChanged: (Rect) -> Unit,
  onClick: () -> Unit,
) {
  PressableVideoCard(
    enabled = collection.collectionId > 0L,
    onClick = onClick,
    onLongClick = {},
    modifier = Modifier.onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) },
    shape = RoundedCornerShape(20.dp),
  ) {
    VideoCardGradient(coverUrl = collection.coverUrl, loadKey = collection.id) {
      Column(Modifier.padding(10.dp)) {
        CoverImage(
          coverUrl = collection.coverUrl,
          contentDescription = collection.title,
          modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
          shape = RoundedCornerShape(14.dp),
          contentScale = ContentScale.Crop,
          loadKey = collection.id,
        )
        Spacer(Modifier.height(8.dp))
        Row(
          Modifier.fillMaxWidth().height(48.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            collection.title,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
          )
          if (collection.collectionTotal > 0) {
            Text(
              "${collection.collectionTotal}个视频",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
        Spacer(Modifier.height(4.dp))
        Text(
          collection.subtitle.ifBlank { " " },
          modifier = Modifier.fillMaxWidth().height(36.dp),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@Composable
private fun CollectionDetail(
  collection: SpaceContentCard,
  videos: List<FeedItem>,
  page: Int,
  hasMore: Boolean,
  loading: Boolean,
  error: String?,
  total: Int,
  onDismiss: () -> Unit,
  onLoadMore: () -> Unit,
  onVideoClick: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  hiddenCoverItemId: String?,
  onVideoBoundsChanged: (FeedItem, Rect) -> Unit,
  onScrollStarted: () -> Unit,
) {
  val state = rememberLazyGridState()
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(state)
  val nearEnd by remember {
    derivedStateOf {
      val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      lastVisible >= state.layoutInfo.totalItemsCount - 5
    }
  }
  LaunchedEffect(nearEnd, hasMore, loading, imageLoadPolicy.mode) {
    if (nearEnd && hasMore && !loading && imageLoadPolicy.mode != FeedImageLoadMode.PAUSED) {
      onLoadMore()
    }
  }
  LaunchedEffect(state.isScrollInProgress) {
    if (state.isScrollInProgress) onScrollStarted()
  }
  Column(Modifier.fillMaxSize()) {
    CollectionDetailHeader(
      collection = collection,
      total = total,
      onDismiss = onDismiss,
    )
    CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(230.dp),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding =
          PaddingValues(
            start = 14.dp,
            top = 12.dp,
            end = 14.dp,
            bottom = NavigationCardBottomClearance,
          ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when {
          loading && videos.isEmpty() ->
            item(key = "collection_initial_loading", span = { GridItemSpan(maxLineSpan) }) {
              Box(
                Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
              }
            }
          error != null && videos.isEmpty() ->
            item(key = "collection_initial_error", span = { GridItemSpan(maxLineSpan) }) {
              Row(
                Modifier.fillMaxWidth().height(180.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onLoadMore) { Text("重试") }
              }
            }
          videos.isEmpty() && page > 0 ->
            item(key = "collection_empty", span = { GridItemSpan(maxLineSpan) }) {
              Box(
                Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text("这个合集暂时没有公开视频", color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          else ->
            itemsIndexed(videos, key = { _, video -> video.id }) { index, video ->
              var coverBounds by remember(video.id) { mutableStateOf(Rect.Zero) }
              VideoCardReveal(
                index = index,
                batchKey = collection.id,
                itemKey = video.id,
              ) {
                PressableVideoCard(
                  onClick = { onVideoClick(video, coverBounds) },
                  onLongClick = { onVideoLongClick(video) },
                  shape = RoundedCornerShape(18.dp),
                ) {
                  FeedCardContent(
                    item = video,
                    profileClickEnabled = false,
                    coverVisible = video.id != hiddenCoverItemId,
                    onCoverBoundsChanged = {
                      coverBounds = it
                      onVideoBoundsChanged(video, it)
                    },
                  )
                }
              }
            }
        }
        if (loading && videos.isNotEmpty()) {
          item(key = "collection_loading_more", span = { GridItemSpan(maxLineSpan) }) {
            Box(
              Modifier.fillMaxWidth().padding(vertical = 10.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
          }
        } else if (error != null && videos.isNotEmpty()) {
          item(key = "collection_more_error", span = { GridItemSpan(maxLineSpan) }) {
            Row(
              Modifier.fillMaxWidth().padding(vertical = 10.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
              TextButton(onClick = onLoadMore) { Text("重试") }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CollectionDetailHeader(
  collection: SpaceContentCard,
  total: Int,
  onDismiss: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp,
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onDismiss) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回合集和系列")
      }
      if (collection.coverUrl.isNotBlank()) {
        CoverImage(
          coverUrl = collection.coverUrl,
          contentDescription = null,
          modifier = Modifier.width(86.dp).aspectRatio(16f / 9f),
          shape = RoundedCornerShape(10.dp),
          contentScale = ContentScale.Crop,
          loadKey = "${collection.id}:detail_header",
        )
        Spacer(Modifier.width(12.dp))
      }
      Column(Modifier.weight(1f)) {
        Text(
          collection.title,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontWeight = FontWeight.SemiBold,
          style = MaterialTheme.typography.titleMedium,
        )
        val typeLabel = if (collection.collectionType == SpaceCollectionType.SEASON) "合集" else "系列"
        Text(
          buildString {
            append(typeLabel)
            val resolvedTotal = total.takeIf { it > 0 } ?: collection.collectionTotal
            if (resolvedTotal > 0) append(" · $resolvedTotal 个视频")
          },
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@Composable
private fun CollectionDetailTransition(
  sourceBounds: Rect,
  targetBounds: Rect,
  progress: () -> Float,
  contentReady: Boolean,
  content: @Composable () -> Unit,
) {
  val validBounds = sourceBounds.hasUsableSize() && targetBounds.hasUsableSize()
  val startScaleX = if (validBounds) sourceBounds.width / targetBounds.width else .96f
  val startScaleY = if (validBounds) sourceBounds.height / targetBounds.height else .96f
  val startX = if (validBounds) sourceBounds.left - targetBounds.left else 0f
  val startY = if (validBounds) sourceBounds.top - targetBounds.top else 0f
  Box(
    Modifier.fillMaxSize().graphicsLayer {
      val value = progress().coerceIn(0f, 1f)
      transformOrigin = TransformOrigin(0f, 0f)
      scaleX = startScaleX + (1f - startScaleX) * value
      scaleY = startScaleY + (1f - startScaleY) * value
      translationX = startX * (1f - value)
      translationY = startY * (1f - value)
      alpha = if (validBounds) (value * 2.5f).coerceIn(0f, 1f) else value
    }
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.background,
      border = BorderStroke(.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f)),
      tonalElevation = 1.dp,
      shadowElevation = 0.dp,
    ) {
      AnimatedVisibility(
        visible = contentReady,
        enter = fadeIn(tween(160, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(80, easing = FastOutSlowInEasing)),
      ) {
        Box(Modifier.fillMaxSize()) { content() }
      }
    }
  }
}

private fun Rect.hasUsableSize(): Boolean = width > 1f && height > 1f

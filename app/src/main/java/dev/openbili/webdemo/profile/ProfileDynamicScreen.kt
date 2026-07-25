package dev.openbili.webdemo.profile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.BiliEmotePackage
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.MentionSuggestion
import dev.openbili.webdemo.api.SpaceDynamicImage
import dev.openbili.webdemo.api.SpaceDynamicItem
import dev.openbili.webdemo.api.SpaceDynamicVideo
import dev.openbili.webdemo.api.SpaceProfile
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedImageLoadMode
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import dev.openbili.webdemo.feed.rememberStaggeredFeedImageLoadPolicy
import dev.openbili.webdemo.article.ArticleCard
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.ui.PressableVideoCard
import dev.openbili.webdemo.ui.AvatarImage
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.VideoCardReveal
import dev.openbili.webdemo.video.CommentComposer
import dev.openbili.webdemo.video.BiliRichText
import dev.openbili.webdemo.video.CommentImagePreviewOverlay
import dev.openbili.webdemo.video.CommentImagePreviewSession
import dev.openbili.webdemo.video.CommentProfileAnchor
import dev.openbili.webdemo.video.CommentRow
import dev.openbili.webdemo.video.ReplyThreadPanel
import dev.openbili.webdemo.video.ReplyThreadTransitionContainer
import dev.openbili.webdemo.video.commentCanBeDeletedBy
import dev.openbili.webdemo.video.formatCompactCount
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DynamicCommentSnapshot(
  val comments: List<CommentItem>,
  val total: Long,
  val page: Int,
  val hasMore: Boolean,
  val sort: CommentSort,
)

internal enum class ProfileDynamicFilter(val label: String) {
  ALL("全部"),
  VIDEO("仅视频"),
  IMAGE_TEXT("仅图文"),
}

internal fun filterProfileDynamics(
  items: List<SpaceDynamicItem>,
  filter: ProfileDynamicFilter,
): List<SpaceDynamicItem> =
  when (filter) {
    ProfileDynamicFilter.ALL -> items
    ProfileDynamicFilter.VIDEO -> items.filter { it.video != null }
    ProfileDynamicFilter.IMAGE_TEXT -> items.filter { it.video == null }
  }

private val DynamicCommentBubbleIcon: ImageVector by lazy {
  ImageVector.Builder(
      name = "DynamicCommentBubble",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
      ) {
        moveTo(20f, 2f)
        horizontalLineTo(4f)
        curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
        verticalLineTo(22f)
        lineTo(6f, 18f)
        horizontalLineTo(20f)
        curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
        verticalLineTo(4f)
        curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
        close()
        moveTo(20f, 16f)
        horizontalLineTo(5.17f)
        lineTo(4f, 17.17f)
        verticalLineTo(4f)
        horizontalLineTo(20f)
        verticalLineTo(16f)
        close()
      }
    }
    .build()
}

private val DynamicPinnedIcon: ImageVector by lazy {
  ImageVector.Builder(
      name = "DynamicPinned",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
      ) {
        moveTo(16f, 2f)
        horizontalLineTo(8f)
        verticalLineTo(4f)
        horizontalLineTo(9f)
        verticalLineTo(9f)
        lineTo(6f, 12f)
        verticalLineTo(15f)
        horizontalLineTo(11f)
        verticalLineTo(22f)
        horizontalLineTo(13f)
        verticalLineTo(15f)
        horizontalLineTo(18f)
        verticalLineTo(12f)
        lineTo(15f, 9f)
        verticalLineTo(4f)
        horizontalLineTo(16f)
        close()
      }
    }
    .build()
}

private object DynamicCommentMemoryCache {
  private const val MAX_ENTRIES = 16
  private val entries = LinkedHashMap<String, DynamicCommentSnapshot>(MAX_ENTRIES, .75f, true)

  @Synchronized fun get(dynamicId: String): DynamicCommentSnapshot? = entries[dynamicId]

  @Synchronized
  fun put(dynamicId: String, snapshot: DynamicCommentSnapshot) {
    entries[dynamicId] = snapshot
    while (entries.size > MAX_ENTRIES) entries.remove(entries.entries.first().key)
  }
}

private fun Rect.matchesTransitionAnchor(other: Rect?): Boolean {
  if (other == null || width <= 0f || height <= 0f || other.width <= 0f || other.height <= 0f)
    return false
  val tolerance = maxOf(4f, minOf(width, height, other.width, other.height) * .16f)
  return kotlin.math.abs(center.x - other.center.x) <= tolerance &&
    kotlin.math.abs(center.y - other.center.y) <= tolerance
}

@Composable
internal fun ProfileDynamicGrid(
  items: List<SpaceDynamicItem>,
  searchQuery: String,
  loading: Boolean,
  hasMore: Boolean,
  error: String?,
  selectedDynamicId: String?,
  profile: SpaceProfile?,
  currentAccountMid: Long,
  settings: AppSettings,
  onVideoClick: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  hiddenCoverItemId: String?,
  onVideoBoundsChanged: (FeedItem, Rect) -> Unit,
  onArticleClick: (ArticleItem, Rect) -> Unit,
  hiddenArticleItemId: String?,
  onArticleBoundsChanged: (ArticleItem, Rect) -> Unit,
  onCommentProfileClick: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onAvatarProfileClick: (Long, String?, String?, Rect) -> Unit,
  hiddenCommentAvatarRpid: Long? = null,
  hiddenAvatarSourceBounds: Rect? = null,
  backHandlingEnabled: Boolean = true,
  onSelectedDynamicIdChange: (String?) -> Unit,
  onDynamicLike: (SpaceDynamicItem) -> Unit,
  onDynamicDelete: (SpaceDynamicItem) -> Unit,
  onDynamicPin: (SpaceDynamicItem) -> Unit,
  onLoadMore: () -> Unit,
  onScrollStarted: () -> Unit,
) {
  val state = rememberLazyStaggeredGridState()
  val imageLoadPolicy = rememberStaggeredFeedImageLoadPolicy(state)
  LaunchedEffect(searchQuery) { state.scrollToItem(0) }
  val cardBounds = remember(profile?.mid) { mutableMapOf<String, Rect>() }
  var displayedDynamicId by remember(profile?.mid) { mutableStateOf(selectedDynamicId) }
  var transitionSourceBounds by remember(profile?.mid) { mutableStateOf(Rect.Zero) }
  var transitionTargetBounds by remember(profile?.mid) { mutableStateOf(Rect.Zero) }
  var managedDynamicId by remember(profile?.mid) { mutableStateOf<String?>(null) }
  var deleteConfirmationId by remember(profile?.mid) { mutableStateOf<String?>(null) }
  var selectedFilter by
    rememberSaveable(profile?.mid) { mutableStateOf(ProfileDynamicFilter.ALL) }
  val filteredItems =
    remember(items, selectedFilter, searchQuery) {
      filterProfileDynamics(items, selectedFilter).filter { item ->
        matchesProfileContentSearch(
          searchQuery,
          item.text,
          item.authorName,
          item.video?.title,
          item.video?.description,
          item.article?.title,
          item.article?.summary,
        )
      }
    }
  val dynamicEmoteMap =
    remember(items) {
      buildMap {
        items.forEach { item ->
          item.emotes.forEach { (text, url) -> put(text, BiliEmote(text, url)) }
        }
      }
    }
  val detailProgress =
    remember(profile?.mid) { Animatable(if (selectedDynamicId == null) 0f else 1f) }
  var detailContentReady by remember(profile?.mid) { mutableStateOf(false) }
  val displayedItem = items.firstOrNull { it.id == displayedDynamicId }
  BackHandler(enabled = backHandlingEnabled && displayedDynamicId != null) {
    onSelectedDynamicIdChange(null)
  }
  BackHandler(
    enabled = backHandlingEnabled && managedDynamicId != null && displayedDynamicId == null
  ) {
    if (deleteConfirmationId != null) deleteConfirmationId = null else managedDynamicId = null
  }
  LaunchedEffect(selectedDynamicId) {
    if (selectedDynamicId != null) {
      if (items.none { it.id == selectedDynamicId }) return@LaunchedEffect
      displayedDynamicId = selectedDynamicId
      detailContentReady = false
      if (transitionSourceBounds.width > 1f && transitionSourceBounds.height > 1f) {
        detailProgress.snapTo(0f)
        repeat(3) {
          withFrameNanos {}
          if (transitionTargetBounds.width > 1f && transitionTargetBounds.height > 1f)
            return@repeat
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
    } else if (displayedDynamicId != null) {
      detailContentReady = false
      val latestSource = cardBounds[displayedDynamicId] ?: transitionSourceBounds
      if (latestSource.width > 1f && latestSource.height > 1f) transitionSourceBounds = latestSource
      detailProgress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 120 else 400, easing = FastOutSlowInEasing),
      )
      delay(16)
      displayedDynamicId = null
      transitionSourceBounds = Rect.Zero
    }
  }
  Box(Modifier.fillMaxSize()) {
    if (items.isEmpty()) {
      Column(Modifier.fillMaxSize()) {
        DynamicFilterRow(
          selected = selectedFilter,
          onSelected = { selectedFilter = it },
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          when {
            loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            error != null -> Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else ->
              Text(
                if (searchQuery.isBlank()) "暂无公开动态" else "没有找到相关动态",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
          }
        }
      }
    } else {
      val nearEnd by remember {
        derivedStateOf {
          val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
          last >= state.layoutInfo.totalItemsCount - 4
        }
      }
      LaunchedEffect(nearEnd, hasMore, loading, imageLoadPolicy.mode) {
        if (
          nearEnd && hasMore && !loading && imageLoadPolicy.mode != FeedImageLoadMode.PAUSED
        ) onLoadMore()
      }
      LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) onScrollStarted()
      }
      CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
        LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        ) {
        item(key = "dynamic_filters", span = StaggeredGridItemSpan.FullLine) {
          DynamicFilterRow(
            selected = selectedFilter,
            onSelected = { selectedFilter = it },
          )
        }
        if (filteredItems.isEmpty()) {
          item(key = "dynamic_filter_empty", span = StaggeredGridItemSpan.FullLine) {
            Box(
              Modifier.fillMaxWidth().padding(vertical = 48.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                if (searchQuery.isBlank()) "暂时没有符合筛选的动态" else "没有找到相关动态",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        itemsIndexed(filteredItems, key = { _, item -> item.id }) { index, item ->
          VideoCardReveal(index = index, batchKey = items.firstOrNull()?.id, itemKey = item.id) {
            val dynamicArticle = item.article
            if (dynamicArticle != null) {
              ArticleCard(
                article = dynamicArticle,
                coverVisible = dynamicArticle.stableId != hiddenArticleItemId,
                onClick = { bounds -> onArticleClick(dynamicArticle, bounds) },
                onBoundsChanged = { bounds -> onArticleBoundsChanged(dynamicArticle, bounds) },
                loadKey = item.id,
              )
            } else DynamicCard(
              item = item,
              emotes = dynamicEmoteMap,
              profile = profile,
              onBoundsChanged = { cardBounds[item.id] = it },
              onClick = {
                transitionSourceBounds = cardBounds[item.id] ?: Rect.Zero
                displayedDynamicId = item.id
                onSelectedDynamicIdChange(item.id)
              },
              onAvatarClick = { bounds ->
                onAvatarProfileClick(
                  item.authorMid.takeIf { it > 0L } ?: profile?.mid ?: 0L,
                  item.authorFace.ifBlank { profile?.face.orEmpty() },
                  item.authorName.ifBlank { profile?.name.orEmpty() },
                  bounds,
                )
              },
              hiddenAvatarSourceBounds = hiddenAvatarSourceBounds,
              onLike = { onDynamicLike(item) },
              managementSelected = managedDynamicId == item.id,
              deleteConfirmation = deleteConfirmationId == item.id,
              canManage =
                currentAccountMid > 0L &&
                  (item.authorMid.takeIf { it > 0L } ?: profile?.mid) == currentAccountMid,
              onManage = {
                deleteConfirmationId = null
                managedDynamicId = item.id
              },
              onCancelManage = {
                deleteConfirmationId = null
                managedDynamicId = null
              },
              onDeleteRequest = { deleteConfirmationId = item.id },
              onDeleteUndo = { deleteConfirmationId = null },
              onDeleteConfirm = {
                deleteConfirmationId = null
                managedDynamicId = null
                onDynamicDelete(item)
              },
              onPin = {
                managedDynamicId = null
                onDynamicPin(item)
              },
            )
          }
        }
        if (loading || error != null) {
          item {
            Box(
              Modifier.fillMaxWidth().padding(12.dp),
              contentAlignment = Alignment.Center,
            ) {
              if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
              else Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
        }
      }
    }
    displayedItem?.let { item ->
      BoxWithConstraints(Modifier.fillMaxSize().zIndex(10f)) {
        Box(
          Modifier.offset(12.dp, 12.dp)
            .size(
              width = (maxWidth - 24.dp).coerceAtLeast(1.dp),
              height = (maxHeight - 24.dp).coerceAtLeast(1.dp),
            )
            .onGloballyPositioned { transitionTargetBounds = it.boundsInRoot() }
        ) {
          DynamicDetailTransition(
            sourceBounds = transitionSourceBounds,
            targetBounds = transitionTargetBounds,
            progress = { detailProgress.value },
            contentReady = detailContentReady,
          ) {
            DynamicDetail(
              item = item,
              profile = profile,
              currentAccountMid = currentAccountMid,
              settings = settings,
              onDismiss = { onSelectedDynamicIdChange(null) },
              onVideoClick = onVideoClick,
              onVideoLongClick = onVideoLongClick,
              hiddenCoverItemId = hiddenCoverItemId,
              onVideoBoundsChanged = onVideoBoundsChanged,
              onArticleClick = onArticleClick,
              hiddenArticleItemId = hiddenArticleItemId,
              onArticleBoundsChanged = onArticleBoundsChanged,
              onCommentProfileClick = onCommentProfileClick,
              onAvatarProfileClick = onAvatarProfileClick,
              hiddenCommentAvatarRpid = hiddenCommentAvatarRpid,
              hiddenAvatarSourceBounds = hiddenAvatarSourceBounds,
              backHandlingEnabled = backHandlingEnabled,
              onDynamicLike = { onDynamicLike(item) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DynamicDetailTransition(
  sourceBounds: Rect,
  targetBounds: Rect,
  progress: () -> Float,
  contentReady: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val validBounds =
    sourceBounds.width > 1f &&
      sourceBounds.height > 1f &&
      targetBounds.width > 1f &&
      targetBounds.height > 1f
  val startScaleX = if (validBounds) sourceBounds.width / targetBounds.width else .96f
  val startScaleY = if (validBounds) sourceBounds.height / targetBounds.height else .96f
  val startX = if (validBounds) sourceBounds.left - targetBounds.left else 0f
  val startY = if (validBounds) sourceBounds.top - targetBounds.top else 0f
  // Single morph container — the same approach as ReplyThreadTransitionContainer: one opaque
  // surface expands from the source card while content cross-fades in after the morph settles.
  Box(modifier = modifier) {
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
        border =
          androidx.compose.foundation.BorderStroke(
            .75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f),
          ),
        tonalElevation = 1.dp,
        shadowElevation = 6.dp,
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
}

@Composable
private fun DynamicCard(
  item: SpaceDynamicItem,
  emotes: Map<String, BiliEmote>,
  profile: SpaceProfile?,
  onBoundsChanged: (Rect) -> Unit,
  onClick: () -> Unit,
  onAvatarClick: (Rect) -> Unit,
  hiddenAvatarSourceBounds: Rect?,
  onLike: () -> Unit,
  managementSelected: Boolean,
  deleteConfirmation: Boolean,
  canManage: Boolean,
  onManage: () -> Unit,
  onCancelManage: () -> Unit,
  onDeleteRequest: () -> Unit,
  onDeleteUndo: () -> Unit,
  onDeleteConfirm: () -> Unit,
  onPin: () -> Unit,
) {
  PressableVideoCard(
    onClick = onClick,
    onLongClick = { if (canManage) onManage() },
    modifier =
      Modifier.onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) },
    shape = RoundedCornerShape(20.dp),
  ) {
    Box {
      Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DynamicAuthorRow(item, profile, onAvatarClick, hiddenAvatarSourceBounds)
        if (item.text.isNotBlank()) {
          BiliRichText(
            text = item.text,
            emotes = emotes,
            onTextClick = onClick,
            maxLines = 4,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        item.video?.let {
          VideoCardGradient(
            coverUrl = it.coverUrl,
            loadKey = item.id,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
          ) {
            DynamicVideoPreview(it, compact = true, loadKey = item.id)
          }
        }
          ?: DynamicImageGrid(item.images, compact = true, loadKey = item.id)
        DynamicStats(item, onLike)
      }
      if (item.pinned) {
        Surface(
          modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp),
          shape = CircleShape,
          color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .94f),
          contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
          tonalElevation = 2.dp,
          shadowElevation = 3.dp,
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              DynamicPinnedIcon,
              contentDescription = "已置顶",
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
      if (managementSelected) {
        Surface(
          modifier = Modifier.matchParentSize().clickable {},
          color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .96f),
          shape = RoundedCornerShape(20.dp),
        ) {
          Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
              visible = !deleteConfirmation,
              enter = fadeIn(tween(150)),
              exit = fadeOut(tween(120)),
            ) {
              Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
              ) {
                Text(
                  "管理这条动态？",
                  modifier = Modifier.weight(1f),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = onCancelManage) { Text("取消") }
                TextButton(onClick = onPin) { Text(if (item.pinned) "取消置顶" else "置顶") }
                TextButton(onClick = onDeleteRequest) {
                  Text("删除", color = MaterialTheme.colorScheme.error)
                }
              }
            }
            AnimatedVisibility(
              visible = deleteConfirmation,
              enter = fadeIn(tween(durationMillis = 160, delayMillis = 120)),
              exit = fadeOut(tween(100)),
            ) {
              Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
              ) {
                Text(
                  "真的要删除这个动态吗 T_T",
                  modifier = Modifier.weight(1f),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = onDeleteUndo) { Text("反悔") }
                TextButton(onClick = onDeleteConfirm) {
                  Text("删除", color = MaterialTheme.colorScheme.error)
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
private fun DynamicFilterRow(
  selected: ProfileDynamicFilter,
  onSelected: (ProfileDynamicFilter) -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ProfileDynamicFilter.entries.forEach { filter ->
      FilterChip(
        selected = selected == filter,
        onClick = { onSelected(filter) },
        label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
      )
    }
  }
}

@Composable
private fun DynamicAuthorRow(
  item: SpaceDynamicItem,
  profile: SpaceProfile?,
  onAvatarClick: (Rect) -> Unit = {},
  hiddenAvatarSourceBounds: Rect? = null,
) {
  val face = item.authorFace.ifBlank { profile?.face.orEmpty() }
  val name = item.authorName.ifBlank { profile?.name.orEmpty() }
  var avatarBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    AvatarImage(
      face = face,
      contentDescription = name,
      loadKey = item.id,
      requestSize = 96,
      modifier =
        Modifier.size(48.dp)
          .onGloballyPositioned { avatarBounds = it.boundsInRoot() }
          .graphicsLayer {
            alpha = if (avatarBounds.matchesTransitionAnchor(hiddenAvatarSourceBounds)) 0f else 1f
          }
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .clickable { onAvatarClick(avatarBounds) }
          .padding(4.dp)
          .clip(CircleShape),
    )
    Column(Modifier.padding(start = 10.dp).weight(1f)) {
      Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
      Text(
        formatDynamicTime(item.publishTimestamp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun DynamicStats(item: SpaceDynamicItem, onLike: () -> Unit) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(26.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    DynamicStat(DynamicCommentBubbleIcon, item.commentCount, "评论")
    DynamicStat(
      icon = Icons.Default.ThumbUp,
      count = item.likeCount,
      label = "点赞",
      active = item.liked,
      onClick = onLike,
    )
  }
}

@Composable
private fun DynamicStat(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  count: Long,
  label: String,
  active: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  Row(
    modifier =
      if (onClick != null)
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(8.dp)
      else Modifier.padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      icon,
      contentDescription = label,
      modifier = Modifier.size(17.dp),
      tint = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      if (count > 0) formatCompactCount(count) else label,
      modifier = Modifier.padding(start = 5.dp),
      style = MaterialTheme.typography.bodySmall,
      color = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun DynamicImageGrid(
  images: List<SpaceDynamicImage>,
  compact: Boolean,
  visibleHeight: Dp? = null,
  onPreview: ((CommentImage, Rect) -> Unit)? = null,
  loadKey: String = "",
) {
  if (images.isEmpty()) return
  val shown = images.take(if (compact) 4 else 9)
  if (shown.size == 1) {
    val image = shown.first()
    var imageBounds by remember(image.url) { mutableStateOf(Rect.Zero) }
    val actualRatio =
      if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else 1f
    val extraLong = actualRatio < .48f
    val displayRatio = actualRatio.coerceIn(if (extraLong) .62f else .55f, 2.4f)
    val configuration = LocalConfiguration.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
      val heightLimit =
        minOf(
            configuration.screenHeightDp.dp * (2f / 3f),
            visibleHeight?.times(2f / 3f) ?: Dp.Infinity,
          )
          .coerceAtMost(if (compact) 480.dp else 560.dp)
      // Portrait media narrows instead of stretching across the whole card. Very long media gets
      // a readable crop here; the detail preview still opens the complete source image.
      val widthLimit =
        if (compact) maxWidth * .94f else minOf(maxWidth * .72f, 560.dp)
      val displayWidth = minOf(widthLimit, heightLimit * displayRatio)
      val displayHeight = minOf(heightLimit, displayWidth / displayRatio)
      CoverImage(
        coverUrl = image.url,
        contentDescription = "动态图片",
        modifier =
          Modifier.width(displayWidth)
            .height(displayHeight)
            .onGloballyPositioned { imageBounds = it.boundsInRoot() }
            .then(
              if (onPreview != null)
                Modifier.clickable {
                  onPreview(CommentImage(image.url, image.width, image.height), imageBounds)
                }
              else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        enforceAspectRatio = false,
        requestWidth = if (compact) 900 else 1200,
        requestHeight = if (compact) 900 else 1200,
        loadKey = loadKey.ifBlank { image.url },
        fadeIn = false,
        contentScale = if (extraLong) ContentScale.Crop else ContentScale.Fit,
      )
    }
    return
  }
  val columns = if (shown.size == 2) 2 else 3
  BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
    val heightLimit =
      minOf(
        LocalConfiguration.current.screenHeightDp.dp * (2f / 3f),
        visibleHeight?.times(2f / 3f) ?: Dp.Infinity,
      )
    val rowCount = (shown.size + columns - 1) / columns
    val maxItemHeight =
      ((heightLimit - 6.dp * (rowCount - 1)) / rowCount).coerceAtLeast(1.dp)
    val widthFromHeight = maxItemHeight * columns + 6.dp * (columns - 1)
    val gridWidth =
      if (compact) minOf(maxWidth, widthFromHeight)
      else minOf(maxWidth * .72f, 640.dp, widthFromHeight)
    Column(
      modifier = Modifier.width(gridWidth),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      shown.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          row.forEach { image ->
            var imageBounds by remember(image.url) { mutableStateOf(Rect.Zero) }
            CoverImage(
              coverUrl = image.url,
              contentDescription = "动态图片",
              modifier =
                Modifier.weight(1f)
                  .aspectRatio(1f)
                  .onGloballyPositioned { imageBounds = it.boundsInRoot() }
                  .then(
                    if (onPreview != null)
                      Modifier.clickable {
                        onPreview(
                          CommentImage(image.url, image.width, image.height),
                          imageBounds,
                        )
                      }
                    else Modifier
                  ),
              shape = RoundedCornerShape(14.dp),
              enforceAspectRatio = false,
              requestWidth = if (compact) 640 else 900,
              requestHeight = if (compact) 640 else 900,
              loadKey = loadKey.ifBlank { image.url },
              fadeIn = false,
              contentScale = ContentScale.Crop,
            )
          }
          repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
      }
    }
  }
}

@Composable
private fun DynamicVideoPreview(
  video: SpaceDynamicVideo,
  compact: Boolean,
  coverVisible: Boolean = true,
  onCoverBoundsChanged: (Rect) -> Unit = {},
  loadKey: String = video.bvid.ifBlank { video.aid.toString() },
) {
  Row(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CoverImage(
      coverUrl = video.coverUrl,
      contentDescription = video.title,
      modifier =
        Modifier.width(if (compact) 172.dp else 230.dp)
          .aspectRatio(16f / 9f)
          .onGloballyPositioned { onCoverBoundsChanged(it.boundsInRoot()) }
          .graphicsLayer { alpha = if (coverVisible) 1f else 0f },
      shape = RoundedCornerShape(12.dp),
      enforceAspectRatio = false,
      loadKey = loadKey,
    )
    Column(Modifier.padding(start = 12.dp).weight(1f)) {
      Text(
        video.title,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        fontWeight = FontWeight.SemiBold,
        style =
          if (compact) MaterialTheme.typography.titleMedium
          else MaterialTheme.typography.titleLarge,
      )
      if (!compact && video.description.isNotBlank()) {
        Text(
          video.description,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        listOf(
            video.playCount.takeIf { it.isNotBlank() },
            video.danmakuCount.takeIf { it.isNotBlank() },
            video.duration.takeIf { it.isNotBlank() },
          )
          .filterNotNull()
          .joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun DynamicDetail(
  item: SpaceDynamicItem,
  profile: SpaceProfile?,
  currentAccountMid: Long,
  settings: AppSettings,
  onDismiss: () -> Unit,
  onVideoClick: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  hiddenCoverItemId: String?,
  onVideoBoundsChanged: (FeedItem, Rect) -> Unit,
  onArticleClick: (ArticleItem, Rect) -> Unit,
  hiddenArticleItemId: String?,
  onArticleBoundsChanged: (ArticleItem, Rect) -> Unit,
  onCommentProfileClick: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onAvatarProfileClick: (Long, String?, String?, Rect) -> Unit,
  hiddenCommentAvatarRpid: Long?,
  hiddenAvatarSourceBounds: Rect?,
  backHandlingEnabled: Boolean,
  onDynamicLike: () -> Unit,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val listState = androidx.compose.foundation.lazy.rememberLazyListState()
  val restoredComments = remember(item.id) { DynamicCommentMemoryCache.get(item.id) }
  var comments by
    remember(item.id) { mutableStateOf(restoredComments?.comments ?: emptyList()) }
  var commentsLoading by remember(item.id) { mutableStateOf(false) }
  var commentsRefreshing by remember(item.id) { mutableStateOf(false) }
  var commentsPage by remember(item.id) { mutableIntStateOf(restoredComments?.page ?: 1) }
  var commentsHasMore by remember(item.id) { mutableStateOf(restoredComments?.hasMore ?: false) }
  var commentTotal by remember(item.id) {
    mutableStateOf(restoredComments?.total ?: item.commentCount)
  }
  var commentSort by remember(item.id) {
    mutableStateOf(restoredComments?.sort ?: CommentSort.DEFAULT)
  }
  var replyRoot by remember(item.id) { mutableStateOf<CommentItem?>(null) }
  var displayedReplyRoot by remember(item.id) { mutableStateOf<CommentItem?>(null) }
  var replySourceBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  var replyTargetBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  val replyTransitionProgress = remember(item.id) { Animatable(0f) }
  var replyContentReady by remember(item.id) { mutableStateOf(false) }
  var replyTarget by remember(item.id) { mutableStateOf<CommentItem?>(null) }
  var replyTargetRoot by remember(item.id) { mutableStateOf<CommentItem?>(null) }
  var replies by remember(item.id) { mutableStateOf<List<CommentItem>>(emptyList()) }
  var repliesPage by remember(item.id) { mutableIntStateOf(1) }
  var repliesHasMore by remember(item.id) { mutableStateOf(false) }
  var repliesLoading by remember(item.id) { mutableStateOf(false) }
  var emotePackages by remember { mutableStateOf<List<BiliEmotePackage>>(emptyList()) }
  val emotes =
    remember(emotePackages) { emotePackages.flatMap { it.emotes }.distinctBy { it.text } }
  val emoteMap =
    remember(emotes, item.emotes) {
      emotes.associateBy(BiliEmote::text) +
        item.emotes.mapValues { (text, url) -> BiliEmote(text, url) }
    }
  var mentionQuery by remember(item.id) { mutableStateOf("") }
  var mentionSuggestions by
    remember(item.id) { mutableStateOf<List<MentionSuggestion>>(emptyList()) }
  var mentionLoading by remember(item.id) { mutableStateOf(false) }
  var deleteCandidate by remember(item.id) { mutableStateOf<CommentItem?>(null) }
  var imagePreview by remember(item.id) { mutableStateOf<CommentImagePreviewSession?>(null) }
  var imagePreviewJob by remember(item.id) { mutableStateOf<Job?>(null) }
  var rootBounds by remember { mutableStateOf(Rect.Zero) }
  var composerHeightPx by remember { mutableStateOf(0f) }
  val videoItem = remember(item.id) { item.video?.toFeedItem(item, profile) }

  fun cacheComments() {
    DynamicCommentMemoryCache.put(
      item.id,
      DynamicCommentSnapshot(comments, commentTotal, commentsPage, commentsHasMore, commentSort),
    )
  }

  fun loadComments(page: Int, refresh: Boolean = false) {
    if (item.commentOid <= 0L || commentsLoading || commentsRefreshing) return
    if (refresh) commentsRefreshing = true else commentsLoading = true
    scope.launch {
      runCatching {
          withContext(Dispatchers.IO) {
            BiliApi.getComments(item.commentOid, page, commentSort.apiValue, item.commentType)
          }
        }
        .onSuccess { response ->
          comments =
            if (page == 1) response.items else (comments + response.items).distinctBy { it.rpid }
          commentsPage = page
          commentsHasMore = response.hasMore
          commentTotal = response.totalCount
          cacheComments()
        }
        .onFailure {
          Toast.makeText(context, it.message ?: "评论加载失败", Toast.LENGTH_SHORT).show()
        }
      commentsLoading = false
      commentsRefreshing = false
    }
  }

  fun loadReplies(root: CommentItem, page: Int) {
    if (item.commentOid <= 0L || repliesLoading) return
    repliesLoading = true
    scope.launch {
      runCatching {
          withContext(Dispatchers.IO) {
            BiliApi.getCommentReplies(item.commentOid, root.rpid, page, item.commentType)
          }
        }
        .onSuccess { response ->
          replies =
            if (page == 1) response.items else (replies + response.items).distinctBy { it.rpid }
          repliesPage = page
          repliesHasMore = response.hasMore
        }
        .onFailure { Toast.makeText(context, it.message ?: "回复加载失败", Toast.LENGTH_SHORT).show() }
      repliesLoading = false
    }
  }

  fun updateLiked(target: CommentItem, liked: Boolean) {
    fun update(comment: CommentItem) =
      if (comment.rpid == target.rpid)
        comment.copy(
          liked = liked,
          likeCount = (comment.likeCount + if (liked) 1 else -1).coerceAtLeast(0),
        )
      else comment
    comments = comments.map(::update)
    replies = replies.map(::update)
    cacheComments()
  }

  fun likeComment(comment: CommentItem) {
    val liked = !comment.liked
    updateLiked(comment, liked)
    scope.launch {
      runCatching {
          withContext(Dispatchers.IO) {
            BiliApi.setCommentLike(item.commentOid, comment.rpid, liked, item.commentType)
          }
        }
        .onFailure {
          updateLiked(comment.copy(liked = liked), !liked)
          Toast.makeText(context, it.message ?: "操作失败", Toast.LENGTH_SHORT).show()
        }
    }
  }

  fun deleteComment(comment: CommentItem) {
    scope.launch {
      runCatching {
          withContext(Dispatchers.IO) {
            BiliApi.deleteComment(item.commentOid, comment.rpid, item.commentType)
          }
        }
        .onSuccess {
          comments = comments.filterNot { it.rpid == comment.rpid }
          replies = replies.filterNot { it.rpid == comment.rpid }
          commentTotal = (commentTotal - 1).coerceAtLeast(0)
          if (replyRoot?.rpid == comment.rpid) replyRoot = null
          cacheComments()
        }
        .onFailure { Toast.makeText(context, it.message ?: "删除失败", Toast.LENGTH_SHORT).show() }
      deleteCandidate = null
    }
  }

  fun openImage(image: CommentImage, bounds: Rect) {
    if (bounds.width <= 0f || bounds.height <= 0f || imagePreview != null) return
    val session = CommentImagePreviewSession(image, bounds)
    imagePreview = session
    imagePreviewJob?.cancel()
    imagePreviewJob = scope.launch {
      val result = session.preparation.await()
      if (
        imagePreview !== session ||
          result == dev.openbili.webdemo.ui.TransitionPreparationResult.CANCELLED
      )
        return@launch
      session.progress.animateTo(
        1f,
        tween(if (settings.reduceMotion) 100 else 360, easing = FastOutSlowInEasing),
      )
    }
  }

  fun closeImage() {
    val session = imagePreview ?: return
    imagePreviewJob?.cancel()
    session.preparation.cancel()
    imagePreviewJob = scope.launch {
      session.progress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 80 else 280, easing = FastOutSlowInEasing),
      )
      delay(16)
      if (imagePreview === session) imagePreview = null
    }
  }

  LaunchedEffect(item.id, commentSort) {
    val cached = DynamicCommentMemoryCache.get(item.id)
    if (cached == null || cached.sort != commentSort || cached.comments.isEmpty()) {
      comments = emptyList()
      commentsPage = 1
      commentsHasMore = false
      loadComments(1)
    }
    if (emotePackages.isEmpty()) {
      emotePackages =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.getReplyEmotes() }.getOrDefault(emptyList())
        }
    }
  }
  LaunchedEffect(mentionQuery, currentAccountMid) {
    mentionLoading = true
    if (mentionQuery.isNotBlank()) delay(220)
    val query = mentionQuery.trim()
    mentionSuggestions =
      withContext(Dispatchers.IO) {
          val followed =
            if (currentAccountMid > 0L)
              runCatching { BiliApi.getFollowings(currentAccountMid, 1, query).items }
                .getOrDefault(emptyList())
            else emptyList()
          val followedIds = followed.mapTo(mutableSetOf()) { it.mid }
          followed.take(24).map {
            MentionSuggestion(it.mid, it.name, it.face, "我的关注", true)
          } +
            if (query.isNotBlank()) {
              runCatching { BiliApi.searchUsers(query, 1) }
                .getOrDefault(emptyList())
                .asSequence()
                .filter { it.mid !in followedIds }
                .take(24)
                .map { MentionSuggestion(it.mid, it.name, it.face, "${it.fans} 粉丝", false) }
                .toList()
            } else emptyList()
        }
        .distinctBy { it.mid }
    mentionLoading = false
  }
  val nearEnd by remember {
    derivedStateOf {
      val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      last >= listState.layoutInfo.totalItemsCount - 4
    }
  }
  LaunchedEffect(nearEnd, commentsHasMore, commentsLoading) {
    if (nearEnd && commentsHasMore && !commentsLoading) loadComments(commentsPage + 1)
  }
  LaunchedEffect(replyRoot?.rpid) {
    val root = replyRoot
    if (root != null) {
      displayedReplyRoot = root
      replyContentReady = false
      replyTransitionProgress.snapTo(0f)
      repeat(3) {
        withFrameNanos {}
        if (replyTargetBounds.width > 1f && replyTargetBounds.height > 1f) return@repeat
      }
      replyTransitionProgress.animateTo(
        1f,
        tween(if (settings.reduceMotion) 120 else 320, easing = FastOutSlowInEasing),
      )
      withFrameNanos {}
      replyContentReady = true
    } else if (displayedReplyRoot != null) {
      replyContentReady = false
      replyTransitionProgress.animateTo(
        0f,
        tween(if (settings.reduceMotion) 100 else 280, easing = FastOutSlowInEasing),
      )
      delay(16)
      displayedReplyRoot = null
      replyTargetBounds = Rect.Zero
    }
  }
  BackHandler(enabled = backHandlingEnabled && imagePreview != null) { closeImage() }
  BackHandler(
    enabled = backHandlingEnabled && deleteCandidate != null && imagePreview == null
  ) {
    deleteCandidate = null
  }

  Box(
    modifier = Modifier.fillMaxSize().onGloballyPositioned { rootBounds = it.boundsInRoot() },
  ) {
    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        Row(
          Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "关闭动态详情") }
          Text(
            "动态详情",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
        PullRefreshContainer(
          refreshing = commentsRefreshing,
          onRefresh = { loadComments(1, refresh = true) },
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
              PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 18.dp,
                bottom =
                  with(LocalDensity.current) {
                    maxOf(112.dp.toPx(), composerHeightPx + 18.dp.toPx()).toDp()
                  },
              ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            item(key = "dynamic_author") {
              DynamicAuthorRow(
                item = item,
                profile = profile,
                onAvatarClick = { bounds ->
                  onAvatarProfileClick(
                    item.authorMid.takeIf { it > 0L } ?: profile?.mid ?: 0L,
                    item.authorFace.ifBlank { profile?.face.orEmpty() },
                    item.authorName.ifBlank { profile?.name.orEmpty() },
                    bounds,
                  )
                },
                hiddenAvatarSourceBounds = hiddenAvatarSourceBounds,
              )
            }
            if (item.text.isNotBlank()) {
              item(key = "dynamic_text") {
                BiliRichText(
                  text = item.text,
                  emotes = emoteMap,
                  style = MaterialTheme.typography.bodyLarge,
                  maxLines = Int.MAX_VALUE,
                )
              }
            }
            item.article?.let { article ->
              item(key = "dynamic_article") {
                ArticleCard(
                  article = article,
                  coverVisible = article.stableId != hiddenArticleItemId,
                  onClick = { bounds -> onArticleClick(article, bounds) },
                  onBoundsChanged = { bounds -> onArticleBoundsChanged(article, bounds) },
                )
              }
            }
            if (item.article == null && item.images.isNotEmpty()) {
              item(key = "dynamic_images") {
                DynamicImageGrid(
                  images = item.images,
                  compact = false,
                  visibleHeight =
                    rootBounds.height.takeIf { it > 0f }?.let { with(density) { it.toDp() } },
                  onPreview = ::openImage,
                )
              }
            }
            videoItem?.let { video ->
              item(key = "dynamic_video") {
                var coverBounds by remember(video.id) { mutableStateOf(Rect.Zero) }
                PressableVideoCard(
                  onClick = { onVideoClick(video, coverBounds) },
                  onLongClick = { onVideoLongClick(video) },
                  shape = RoundedCornerShape(18.dp),
                ) {
                  VideoCardGradient(coverUrl = video.coverUrl, loadKey = video.id) {
                    DynamicVideoPreview(
                      video = item.video!!,
                      compact = false,
                      coverVisible = video.id != hiddenCoverItemId,
                      onCoverBoundsChanged = {
                        coverBounds = it
                        onVideoBoundsChanged(video, it)
                      },
                      loadKey = video.id,
                    )
                  }
                }
              }
            }
            item(key = "dynamic_stats") { DynamicStats(item, onDynamicLike) }
            item(key = "comments_header") {
              Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  "评论 ${formatCompactCount(commentTotal)}",
                  modifier = Modifier.weight(1f),
                  fontWeight = FontWeight.SemiBold,
                )
                CommentSort.entries.forEach { sort ->
                  FilterChip(
                    selected = sort == commentSort,
                    onClick = { if (sort != commentSort) commentSort = sort },
                    label = { Text(if (sort == CommentSort.DEFAULT) "默认" else "最新") },
                    modifier = Modifier.padding(start = 8.dp),
                  )
                }
              }
            }
            items(comments, key = { it.rpid }, contentType = { "dynamic_comment" }) { comment ->
              CommentRow(
                comment = comment,
                showEmotes = settings.showCommentEmotes,
                emoteCatalog = emoteMap,
                showLocation = settings.showCommentLocation,
                onLike = ::likeComment,
                uploaderMid = item.authorMid,
                onProfileClick = onCommentProfileClick,
                onImagePreview = ::openImage,
                onReplies = { root, bounds ->
                  replySourceBounds = bounds
                  replyRoot = root
                  replies = emptyList()
                  loadReplies(root, 1)
                },
                onReply = { target ->
                  replyTargetRoot = comment
                  replyTarget = target
                },
                bottomClearancePx = composerHeightPx,
                viewportHeightPx = rootBounds.height,
                hiddenLinkedVideoCoverItemId = hiddenCoverItemId,
                onLinkedVideoClick = onVideoClick,
                onLinkedVideoBoundsChanged = onVideoBoundsChanged,
                onLinkedVideoLongClick = onVideoLongClick,
                onLinkedArticleClick = onArticleClick,
                hiddenLinkedArticleItemId = hiddenArticleItemId,
                onLinkedArticleBoundsChanged = onArticleBoundsChanged,
                deletionSelected = deleteCandidate?.rpid == comment.rpid,
                onDeleteRequest =
                  if (commentCanBeDeletedBy(currentAccountMid, item.authorMid, comment.mid))
                    { _: Rect -> deleteCandidate = comment }
                  else null,
                onDeleteConfirm = { deleteComment(comment) },
                onDeleteCancel = { deleteCandidate = null },
                avatarVisible = hiddenCommentAvatarRpid != comment.rpid,
                largeText = true,
              )
            }
            if (commentsLoading) {
              item(key = "comments_loading") {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
              }
            } else if (comments.isEmpty()) {
              item(key = "comments_empty") {
                Text("还没有评论，来坐第一排吧~", color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
      CommentComposer(
        emotes = emotes,
        emotePackages = emotePackages,
        mentionSuggestions = mentionSuggestions,
        mentionSuggestionsLoading = mentionLoading,
        onMentionQuery = { mentionQuery = it },
        targetName = replyTarget?.name,
        onClearTarget = {
          replyTarget = null
          replyTargetRoot = null
        },
        imageEnabled = false,
        onSend = { message, _ ->
          val target = replyTarget
          scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                  if (target == null) BiliApi.addComment(item.commentOid, message, item.commentType)
                  else
                    BiliApi.addReply(
                      item.commentOid,
                      (replyTargetRoot ?: target).rpid,
                      target.rpid,
                      message,
                      item.commentType,
                    )
                }
              }
              .onSuccess { added ->
                if (target == null) {
                  comments = listOf(added) + comments
                  commentTotal += 1
                  cacheComments()
                } else {
                  replies = replies + added
                  replyTarget = null
                  replyTargetRoot = null
                }
              }
              .onFailure {
                Toast.makeText(context, it.message ?: "发送失败", Toast.LENGTH_SHORT).show()
              }
          }
        },
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .width(LocalConfiguration.current.screenWidthDp.dp * .4f)
            .navigationBarsPadding()
            .imePadding()
            .padding(12.dp)
            .zIndex(25f)
            .onGloballyPositioned { composerHeightPx = it.size.height.toFloat() },
      )
      displayedReplyRoot?.let { root ->
        Box(
          Modifier.fillMaxSize()
            .padding(top = 56.dp)
            .zIndex(15f)
            .onGloballyPositioned { replyTargetBounds = it.boundsInRoot() }
        ) {
          ReplyThreadTransitionContainer(
            sourceBounds = replySourceBounds,
            targetBounds = replyTargetBounds,
            progress = { replyTransitionProgress.value },
            contentReady = replyContentReady,
            modifier = Modifier.fillMaxSize(),
          ) {
            ReplyThreadPanel(
          root = root,
          replies = replies,
          hasMore = repliesHasMore,
          loading = repliesLoading,
          showEmotes = settings.showCommentEmotes,
          emoteCatalog = emoteMap,
          showLocation = settings.showCommentLocation,
          onLike = ::likeComment,
          uploaderMid = item.authorMid,
          onProfileClick = onCommentProfileClick,
          onImagePreview = ::openImage,
          onReply = { rootComment, target ->
            replyTargetRoot = rootComment
            replyTarget = target
          },
          onLoadMore = { loadReplies(root, repliesPage + 1) },
          onRefresh = { loadReplies(root, 1) },
          onDismiss = {
            replyRoot = null
            replyTarget = null
            replyTargetRoot = null
          },
           bottomClearancePx = composerHeightPx,
           hiddenCommentAvatarRpid = hiddenCommentAvatarRpid,
          hiddenLinkedVideoCoverItemId = hiddenCoverItemId,
          onLinkedVideoClick = onVideoClick,
          onLinkedVideoBoundsChanged = onVideoBoundsChanged,
          onLinkedVideoLongClick = onVideoLongClick,
          onLinkedArticleClick = onArticleClick,
          hiddenLinkedArticleItemId = hiddenArticleItemId,
          onLinkedArticleBoundsChanged = onArticleBoundsChanged,
          deletionSelectedRpid = deleteCandidate?.rpid,
          canDeleteComment = { commentCanBeDeletedBy(currentAccountMid, item.authorMid, it.mid) },
          onDeleteRequest = { comment, _ -> deleteCandidate = comment },
          onDeleteConfirm = ::deleteComment,
          onDeleteCancel = { deleteCandidate = null },
              largeCommentText = true,
              modifier = Modifier.fillMaxSize(),
            )
          }
        }
      }
      imagePreview?.let { session ->
        CommentImagePreviewOverlay(
          session = session,
          rootBounds = rootBounds,
          onDismiss = ::closeImage,
          modifier = Modifier.fillMaxSize().zIndex(30f),
        )
      }
    }
  }
}

private fun SpaceDynamicVideo.toFeedItem(item: SpaceDynamicItem, profile: SpaceProfile?) =
  FeedItem(
    id = bvid,
    title = title,
    videoUrl = "https://www.bilibili.com/video/$bvid",
    coverUrl = coverUrl,
    uploader = item.authorName.ifBlank { profile?.name.orEmpty() },
    playCount = playCount,
    duration = duration,
    uploaderFace = item.authorFace.ifBlank { profile?.face.orEmpty() },
    uploaderMid = item.authorMid.takeIf { it > 0L } ?: profile?.mid ?: 0L,
    publishedAt = item.publishTimestamp,
    description = description,
  )

private fun formatDynamicTime(timestamp: Long): String {
  if (timestamp <= 0L) return ""
  return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochSecond(timestamp))
}

package dev.openbili.webdemo.video

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import dev.openbili.webdemo.R
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.FollowingGroup
import dev.openbili.webdemo.api.VideoCollectionEpisode
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.ui.DeviceStatusCluster
import dev.openbili.webdemo.ui.FollowButton
import dev.openbili.webdemo.ui.PullRefreshContainer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun VideoHeader(
  item: FeedItem,
  info: VideoInfo?,
  onlineViewerText: String?,
  description: String,
  currentCid: Long,
  onOpenSelection: () -> Unit,
  onBack: () -> Unit,
  onHome: () -> Unit,
  onProfileClick: (Long, String?, String?, Rect) -> Unit,
  onUploaderProfileClick: (Long, String?, String?, Rect) -> Unit,
  showFollowButton: Boolean,
  followed: Boolean,
  followBusy: Boolean,
  followingGroups: List<FollowingGroup>,
  followingGroupsLoading: Boolean,
  loggedIn: Boolean,
  onLoadFollowingGroups: () -> Unit,
  onSelectFollowingGroup: (Long) -> Unit,
  onUnfollow: () -> Unit,
  onLogin: () -> Unit,
  onShowInfo: () -> Unit,
  panelSlideProgress: () -> Float,
) {
  val collection = info?.collection
  val collectionIndex = collection?.episodes?.indexOfFirst { it.bvid == info.bvid } ?: -1
  val pageIndex = info?.pages?.indexOfFirst { it.cid == currentCid } ?: -1
  val selectionTitle =
    when {
      collection != null -> "合集 ${collection.title}"
      (info?.pages?.size ?: 0) > 1 -> "视频选集"
      else -> null
    }
  val selectionProgress =
    when {
      collection != null ->
        "(${collectionIndex.coerceAtLeast(0) + 1}/${collection.episodes.size})"
      (info?.pages?.size ?: 0) > 1 ->
        "(${pageIndex.coerceAtLeast(0) + 1}/${info?.pages?.size ?: 0})"
      else -> ""
    }
  val face = info?.uploaderFace?.takeIf(String::isNotBlank) ?: item.uploaderFace
  val ownerName = info?.uploaderName ?: item.uploader.orEmpty()
  val ownerMid = info?.uploaderMid ?: item.uploaderMid
  val metadata =
    buildList {
        add("${formatCompactCount(info?.playCount ?: 0)} 播放")
        add("${formatCompactCount(info?.danmakuCount ?: item.danmakuCount)} 弹幕")
        onlineViewerText?.takeIf(String::isNotBlank)?.let { add("$it 人在看") }
        formatPublishDate(info?.publishedAt ?: 0)?.let(::add)
        (info?.bvid ?: item.id).takeIf { it.startsWith("BV") }?.let(::add)
      }
      .joinToString("  ·  ")
  PlaybackHeader(
    model =
      PlaybackHeaderUiModel(
        stableId = item.id,
        title = info?.title ?: item.title,
        ownerMid = ownerMid,
        ownerName = ownerName,
        ownerFace = face,
        description = description,
        metadata = metadata,
        selectionTitle = selectionTitle,
        selectionProgress = selectionProgress,
      ),
    onBack = onBack,
    onHome = onHome,
    onOwnerProfileClick = onUploaderProfileClick,
    showFollowButton = showFollowButton,
    followed = followed,
    followBusy = followBusy,
    followingGroups = followingGroups,
    followingGroupsLoading = followingGroupsLoading,
    loggedIn = loggedIn,
    onLoadFollowingGroups = onLoadFollowingGroups,
    onSelectFollowingGroup = onSelectFollowingGroup,
    onUnfollow = onUnfollow,
    onLogin = onLogin,
    onShowInfo = onShowInfo,
    onOpenSelection = onOpenSelection.takeIf { selectionTitle != null },
    panelSlideProgress = panelSlideProgress,
  )
}

@Composable
internal fun VideoSelectionTile(
  info: VideoInfo,
  currentCid: Long,
  onPageSelected: (VideoPage) -> Unit,
  onEpisodeSelected: (VideoCollectionEpisode, Rect) -> Unit,
  onDismiss: () -> Unit,
) {
  val collection = info.collection
  val selectedIndex =
    if (collection != null) collection.episodes.indexOfFirst { it.bvid == info.bvid }
    else info.pages.indexOfFirst { it.cid == currentCid }
  val initialIndex = (selectedIndex - 2).coerceAtLeast(0)
  val selectionListState =
    remember(collection?.id, info.bvid, currentCid) {
      LazyListState(firstVisibleItemIndex = initialIndex)
    }
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.widthIn(min = 620.dp, max = 760.dp).heightIn(max = 620.dp),
      shape = RoundedCornerShape(26.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 8.dp,
      shadowElevation = 14.dp,
    ) {
      Column(Modifier.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(
              if (collection != null) "合集 ${collection.title}" else "视频选集",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              if (collection != null) "共 ${collection.episodes.size} 集"
              else "共 ${info.pages.size} 个分 P",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TextButton(onClick = onDismiss) { Text("关闭") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
          state = selectionListState,
          modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (collection != null) {
            items(collection.episodes, key = { it.bvid }) { episode ->
              var coverBounds by remember(episode.bvid) { mutableStateOf(Rect.Zero) }
              val selected = episode.bvid == info.bvid
              Surface(
                modifier =
                  Modifier.fillMaxWidth().clickable(enabled = !selected) {
                    onEpisodeSelected(episode, coverBounds)
                  },
                shape = RoundedCornerShape(16.dp),
                color =
                  if (selected) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .54f),
              ) {
                Row(
                  Modifier.fillMaxWidth().padding(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  CoverImage(
                    coverUrl = episode.coverUrl,
                    modifier =
                      Modifier.width(126.dp).onGloballyPositioned {
                        coverBounds = it.boundsInRoot()
                      },
                    shape = RoundedCornerShape(11.dp),
                  )
                  Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                      episode.title,
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      "${episode.uploaderName}  ·  ${formatPlayerTime(episode.durationSeconds * 1000L)}",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  if (selected) Text("播放中", color = MaterialTheme.colorScheme.primary)
                }
              }
            }
          } else {
            items(info.pages, key = { it.cid }) { page ->
              val selected = page.cid == currentCid
              Surface(
                modifier =
                  Modifier.fillMaxWidth().clickable(enabled = !selected) { onPageSelected(page) },
                shape = RoundedCornerShape(15.dp),
                color =
                  if (selected) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .54f),
              ) {
                Row(
                  Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    "P${page.page}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(54.dp),
                  )
                  Text(
                    page.part.ifBlank { "第 ${page.page} P" },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    formatPlayerTime(page.durationSeconds * 1000L),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

internal fun feedItemFromCollectionEpisode(episode: VideoCollectionEpisode) =
  FeedItem(
    id = episode.bvid,
    title = episode.title,
    videoUrl = "https://www.bilibili.com/video/${episode.bvid}",
    coverUrl = episode.coverUrl,
    uploader = episode.uploaderName,
    playCount = formatCompactCount(episode.playCount),
    duration = formatPlayerTime(episode.durationSeconds * 1000L),
    uploaderFace = episode.uploaderFace,
    uploaderMid = episode.uploaderMid,
    danmakuCount = episode.danmakuCount,
    publishedAt = episode.publishedAt,
  )

@Composable
fun VideoInfoTile(
  item: FeedItem,
  info: VideoInfo?,
  onlineViewerText: String?,
  description: String,
  onDismiss: () -> Unit,
  onNotInterested: (() -> Unit)? = null,
  onNotInterestedUploader: (() -> Unit)? = null,
) {
  var visible by remember { mutableStateOf(false) }
  var dismissing by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  fun dismissThen(action: () -> Unit) {
    if (dismissing) return
    dismissing = true
    visible = false
    scope.launch {
      delay(180)
      action()
    }
  }
  LaunchedEffect(Unit) { visible = true }
  Dialog(onDismissRequest = { dismissThen(onDismiss) }) {
    AnimatedVisibility(
      visible = visible,
      enter = fadeIn(tween(180)) + scaleIn(tween(280), initialScale = .86f),
      exit = fadeOut(tween(120)) + scaleOut(tween(180), targetScale = .92f),
    ) {
      Surface(
        modifier = Modifier.fillMaxWidth(.82f).widthIn(max = 760.dp).heightIn(max = 620.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 20.dp,
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Text(
            text = info?.title ?: item.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
          )
          val uploaderFace = info?.uploaderFace?.takeIf(String::isNotBlank) ?: item.uploaderFace
          val uploaderName = info?.uploaderName ?: item.uploader.orEmpty()
          Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (!uploaderFace.isNullOrBlank()) {
              AsyncImage(
                model = uploaderFace,
                contentDescription = uploaderName,
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
              )
              Spacer(Modifier.width(10.dp))
            }
            Text(
              text = uploaderName,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Text(
            buildList {
                add("${formatCompactCount(info?.playCount ?: 0)} 播放")
                add("${formatCompactCount(info?.danmakuCount ?: item.danmakuCount)} 弹幕")
                add("${formatCompactCount(info?.replyCount ?: 0)} 评论")
                onlineViewerText?.takeIf(String::isNotBlank)?.let { add("$it 人在看") }
                formatPublishDate(info?.publishedAt ?: 0)?.let(::add)
                (info?.bvid ?: item.id).takeIf { it.startsWith("BV") }?.let(::add)
              }
              .joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(vertical = 4.dp),
          ) {
            item {
              BiliRichText(
                text = description.ifBlank { "这个视频暂时没有填写简介。" },
                emotes = emptyMap(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = Int.MAX_VALUE,
              )
            }
          }
          if (onNotInterested != null && onNotInterestedUploader != null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
              OutlinedButton(onClick = { dismissThen(onNotInterested) }) {
                Text("内容不感兴趣")
              }
              OutlinedButton(onClick = { dismissThen(onNotInterestedUploader) }) {
                Text("不想看此 UP 主")
              }
            }
          }
          TextButton(
            onClick = { dismissThen(onDismiss) },
            modifier = Modifier.align(Alignment.End),
          ) {
            Text("关闭")
          }
        }
      }
    }
  }
}

@Composable
internal fun ReplyThreadPanel(
  root: CommentItem,
  replies: List<CommentItem>,
  hasMore: Boolean,
  loading: Boolean,
  showEmotes: Boolean = true,
  emoteCatalog: Map<String, BiliEmote> = emptyMap(),
  showLocation: Boolean,
  onLike: (CommentItem) -> Unit,
  uploaderMid: Long,
  onProfileClick: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onImagePreview: (CommentImage, Rect) -> Unit,
  onReply: (CommentItem, CommentItem) -> Unit,
  onLoadMore: () -> Unit,
  navigationTargetRpid: Long? = null,
  navigationRequestId: Long? = null,
  onNavigationTargetReached: () -> Unit = {},
  onRefresh: () -> Unit,
  onDismiss: () -> Unit,
  bottomClearancePx: Float,
  hiddenCommentAvatarRpid: Long? = null,
  hiddenLinkedVideoCoverItemId: String? = null,
  onLinkedVideoClick: (FeedItem, Rect) -> Unit = { _, _ -> },
  onLinkedVideoBoundsChanged: (FeedItem, Rect) -> Unit = { _, _ -> },
  onLinkedVideoLongClick: (FeedItem) -> Unit = {},
  hiddenLinkedArticleItemId: String? = null,
  onLinkedArticleClick: (ArticleItem, Rect) -> Unit = { _, _ -> },
  onLinkedArticleBoundsChanged: (ArticleItem, Rect) -> Unit = { _, _ -> },
  deletionSelectedRpid: Long? = null,
  canDeleteComment: (CommentItem) -> Boolean = { false },
  onDeleteRequest: (CommentItem, Rect) -> Unit = { _, _ -> },
  onDeleteConfirm: (CommentItem) -> Unit = {},
  onDeleteCancel: () -> Unit = {},
  onDeletionBoundsChanged: (Rect) -> Unit = {},
  largeCommentText: Boolean = false,
  backHandlingEnabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  var replyViewportHeightPx by remember(root.rpid) { mutableStateOf(0f) }
  val replyListState = rememberLazyListState()
  var reachedNavigationRequestId by remember(root.rpid) { mutableStateOf<Long?>(null) }
  LaunchedEffect(navigationRequestId, navigationTargetRpid, replies, hasMore, loading) {
    val targetRpid = navigationTargetRpid ?: return@LaunchedEffect
    val requestId = navigationRequestId ?: return@LaunchedEffect
    if (reachedNavigationRequestId == requestId) return@LaunchedEffect
    val targetIndex =
      if (targetRpid == root.rpid) 1
      else replies.indexOfFirst { it.rpid == targetRpid }.takeIf { it >= 0 }?.plus(3)
    if (targetIndex != null) {
      replyListState.animateScrollToItem(targetIndex)
      reachedNavigationRequestId = requestId
      onNavigationTargetReached()
    } else if (hasMore && !loading) {
      onLoadMore()
    } else if (!hasMore && !loading) {
      reachedNavigationRequestId = requestId
      onNavigationTargetReached()
    }
  }
  val replyBottomPadding =
    with(LocalDensity.current) {
      maxOf(104.dp.toPx(), bottomClearancePx + 16.dp.toPx()).toDp()
    }
  BackHandler(
    enabled = backHandlingEnabled && deletionSelectedRpid == null,
    onBack = onDismiss,
  )
  Box(modifier) {
    Surface(
      modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(12.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
      tonalElevation = 1.dp,
    ) {}
    Surface(
      modifier = Modifier.fillMaxSize(),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
      border =
        androidx.compose.foundation.BorderStroke(
          .75.dp,
          MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f),
        ),
    ) {
      Column(Modifier.fillMaxSize()) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f))
              .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = onDismiss) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭回复")
          }
          Text(
            "${root.replyCount} 条回复",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            "楼中楼",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        PullRefreshContainer(
          refreshing = loading,
          onRefresh = onRefresh,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
          LazyColumn(
            state = replyListState,
            modifier =
              Modifier.fillMaxSize().onGloballyPositioned {
                replyViewportHeightPx = it.size.height.toFloat()
              },
            contentPadding =
              PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = replyBottomPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            item(key = "root_label") {
              Text(
                "原评论",
                modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            item(key = "root") {
              CommentRow(
                root,
                showEmotes,
                emoteCatalog,
                showLocation,
                onLike,
                uploaderMid,
                onProfileClick,
                onImagePreview,
                { _, _ -> },
                { onReply(root, root) },
                bottomClearancePx = bottomClearancePx,
                viewportHeightPx = replyViewportHeightPx,
                flat = true,
                avatarVisible = hiddenCommentAvatarRpid != root.rpid,
                hiddenLinkedVideoCoverItemId = hiddenLinkedVideoCoverItemId,
                onLinkedVideoClick = onLinkedVideoClick,
                onLinkedVideoBoundsChanged = onLinkedVideoBoundsChanged,
                onLinkedVideoLongClick = onLinkedVideoLongClick,
                hiddenLinkedArticleItemId = hiddenLinkedArticleItemId,
                onLinkedArticleClick = onLinkedArticleClick,
                onLinkedArticleBoundsChanged = onLinkedArticleBoundsChanged,
                deletionSelected = deletionSelectedRpid == root.rpid,
                onDeleteRequest =
                  { bounds: Rect -> onDeleteRequest(root, bounds) }.takeIf {
                    canDeleteComment(root)
                  },
                onDeleteConfirm = { onDeleteConfirm(root) },
                onDeleteCancel = onDeleteCancel,
                onDeletionBoundsChanged = onDeletionBoundsChanged,
                largeText = largeCommentText,
              )
            }
            if (replies.isNotEmpty()) {
              item(key = "reply_label") {
                Text(
                  "全部回复",
                  modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            items(
              items = replies,
              key = { "reply_${it.rpid}" },
              contentType = { "reply" },
            ) { reply ->
              CommentRow(
                reply,
                showEmotes,
                emoteCatalog,
                showLocation,
                onLike,
                uploaderMid,
                onProfileClick,
                onImagePreview,
                { _, _ -> },
                { onReply(root, reply) },
                bottomClearancePx = bottomClearancePx,
                viewportHeightPx = replyViewportHeightPx,
                flat = true,
                avatarVisible = hiddenCommentAvatarRpid != reply.rpid,
                hiddenLinkedVideoCoverItemId = hiddenLinkedVideoCoverItemId,
                onLinkedVideoClick = onLinkedVideoClick,
                onLinkedVideoBoundsChanged = onLinkedVideoBoundsChanged,
                onLinkedVideoLongClick = onLinkedVideoLongClick,
                hiddenLinkedArticleItemId = hiddenLinkedArticleItemId,
                onLinkedArticleClick = onLinkedArticleClick,
                onLinkedArticleBoundsChanged = onLinkedArticleBoundsChanged,
                deletionSelected = deletionSelectedRpid == reply.rpid,
                onDeleteRequest =
                  { bounds: Rect -> onDeleteRequest(reply, bounds) }.takeIf {
                    canDeleteComment(reply)
                  },
                onDeleteConfirm = { onDeleteConfirm(reply) },
                onDeleteCancel = onDeleteCancel,
                onDeletionBoundsChanged = onDeletionBoundsChanged,
                largeText = largeCommentText,
              )
            }
            if (hasMore || loading) {
              item(key = "more") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                  if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                  else TextButton(onClick = onLoadMore) { Text("加载更多回复") }
                }
              }
            }
          }
        }
      }
    }
    // Continue the reply tile through its lower rounded edge. The global composer sits above this
    // area, so the extra surface masks the underlying comment list without covering reply content.
    Surface(
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(20.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
      tonalElevation = 1.dp,
    ) {}
  }
}

internal fun formatCompactCount(value: Long): String =
  when {
    value >= 100_000_000 -> "%.1f亿".format(value / 100_000_000f)
    value >= 10_000 -> "%.1f万".format(value / 10_000f)
    else -> value.toString()
  }

internal fun formatPublishDate(epochSeconds: Long): String? =
  epochSeconds
    .takeIf { it > 0 }
    ?.let {
      DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(it))
    }

@Composable
internal fun FadingVisibility(
  visible: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter = fadeIn(tween(140)),
    exit = fadeOut(tween(180)),
  ) {
    content()
  }
}

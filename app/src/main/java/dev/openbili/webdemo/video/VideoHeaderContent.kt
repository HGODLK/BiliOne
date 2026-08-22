package dev.openbili.webdemo.video

/**
 * 视频页头部内容：标题/UP 主/简介、选集瓦片与信息瓦片尺寸适配。
 */

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.FollowingGroup
import dev.openbili.webdemo.api.VideoCollectionEpisode
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.VideoPageSurfaceTokens
import dev.openbili.webdemo.ui.controlFocusOutline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** 视频页头部组合体。 */
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
  showDeviceStatus: Boolean,
  foregroundColor: Color? = null,
  glassBackdrop: PlaybackPageGlassBackdrop = PlaybackPageGlassBackdrop(),
  controlFocus: PlaybackHeaderControlFocus? = null,
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
      collection != null -> "(${collectionIndex.coerceAtLeast(0) + 1}/${collection.episodes.size})"
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
    showDeviceStatus = showDeviceStatus,
    foregroundColor = foregroundColor,
    glassBackdrop = glassBackdrop,
    controlFocus = controlFocus,
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
  val selectionGroups =
    remember(info.bvid, info.pages, collection) {
      videoSelectionGroups(info.bvid, info.pages, collection)
    }
  var selectedGroupKey by
    remember(info.bvid, collection?.id) {
      mutableStateOf(selectionGroups.firstOrNull()?.key.orEmpty())
    }
  val selectedGroup =
    selectionGroups.firstOrNull { it.key == selectedGroupKey } ?: selectionGroups.firstOrNull()
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.widthIn(min = 620.dp, max = 760.dp).heightIn(max = 620.dp),
      shape = RoundedCornerShape(26.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 8.dp,
      shadowElevation = 0.dp,
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
              if (collection != null) {
                buildString {
                  append("共 ${collection.episodes.size} 集")
                  if (info.pages.size > 1) append(" · 当前视频 ${info.pages.size} 个分P")
                }
              }
              else "共 ${info.pages.size} 个分 P",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TextButton(onClick = onDismiss) { Text("关闭") }
        }
        Spacer(Modifier.height(12.dp))
        if (selectionGroups.size > 1 && selectedGroup != null) {
          Row(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            LazyColumn(
              modifier = Modifier.width(184.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              items(selectionGroups, key = VideoSelectionGroup::key) { group ->
                val selected = group.key == selectedGroup.key
                Surface(
                  modifier = Modifier.fillMaxWidth().clickable { selectedGroupKey = group.key },
                  shape = RoundedCornerShape(14.dp),
                  color =
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .54f),
                ) {
                  Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                      group.title,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                    Text(
                      group.subtitle,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
            Spacer(Modifier.width(12.dp))
            VideoSelectionGroupContent(
              group = selectedGroup,
              currentBvid = info.bvid,
              currentCid = currentCid,
              onPageSelected = onPageSelected,
              onEpisodeSelected = onEpisodeSelected,
              modifier = Modifier.weight(1f),
            )
          }
        } else if (selectedGroup != null) {
          VideoSelectionGroupContent(
            group = selectedGroup,
            currentBvid = info.bvid,
            currentCid = currentCid,
            onPageSelected = onPageSelected,
            onEpisodeSelected = onEpisodeSelected,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
          )
        }
      }
    }
  }
}

internal data class VideoSelectionGroup(
  val key: String,
  val title: String,
  val subtitle: String,
  val pages: List<VideoPage> = emptyList(),
  val episodes: List<VideoCollectionEpisode> = emptyList(),
)

internal fun videoSelectionGroups(
  currentBvid: String,
  pages: List<VideoPage>,
  collection: dev.openbili.webdemo.api.VideoCollection?,
): List<VideoSelectionGroup> = buildList {
  if (pages.isNotEmpty() && (collection == null || pages.size > 1)) {
    add(
      VideoSelectionGroup(
        key = "pages:$currentBvid",
        title = "分P",
        subtitle = "${pages.size} 个分P",
        pages = pages,
      )
    )
  }
  if (collection != null) {
    val sections = collection.sections.filter { it.episodes.isNotEmpty() }
    if (sections.isEmpty()) {
      add(
        VideoSelectionGroup(
          key = "collection:${collection.id}",
          title = collection.title.ifBlank { "合集" },
          subtitle = "${collection.episodes.size} 个视频",
          episodes = collection.episodes,
        )
      )
    } else {
      sections.forEach { section ->
        add(
          VideoSelectionGroup(
            key = "collection:${collection.id}:${section.id}",
            title = section.title,
            subtitle = "${section.episodes.size} 个视频",
            episodes = section.episodes,
          )
        )
      }
    }
  }
}

@Composable
private fun VideoSelectionGroupContent(
  group: VideoSelectionGroup,
  currentBvid: String,
  currentCid: Long,
  onPageSelected: (VideoPage) -> Unit,
  onEpisodeSelected: (VideoCollectionEpisode, Rect) -> Unit,
  modifier: Modifier,
) {
  val selectedIndex =
    if (group.pages.isNotEmpty()) group.pages.indexOfFirst { it.cid == currentCid }
    else group.episodes.indexOfFirst { episode ->
      collectionEpisodeSelected(episode, group.episodes, currentBvid, currentCid)
    }
  val listState =
    remember(group.key, currentBvid, currentCid) {
      LazyListState(firstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
    }
  if (group.pages.isNotEmpty()) {
    LazyColumn(
      state = listState,
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(group.pages, key = VideoPage::cid) { page ->
        val selected = page.cid == currentCid
        Surface(
          modifier = Modifier.fillMaxWidth().clickable(enabled = !selected) { onPageSelected(page) },
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
  } else {
    CollectionEpisodeList(
      episodes = group.episodes,
      currentBvid = currentBvid,
      currentCid = currentCid,
      onEpisodeSelected = onEpisodeSelected,
      listState = listState,
      modifier = modifier,
    )
  }
}

@Composable
private fun CollectionEpisodeList(
  episodes: List<VideoCollectionEpisode>,
  currentBvid: String,
  currentCid: Long,
  onEpisodeSelected: (VideoCollectionEpisode, Rect) -> Unit,
  listState: LazyListState,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    state = listState,
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(episodes, key = ::collectionEpisodeKey) { episode ->
      var coverBounds by remember(collectionEpisodeKey(episode)) { mutableStateOf(Rect.Zero) }
      val selected = collectionEpisodeSelected(episode, episodes, currentBvid, currentCid)
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
              Modifier.width(126.dp).onGloballyPositioned { coverBounds = it.boundsInRoot() },
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
  }
}

internal fun collectionEpisodeKey(episode: VideoCollectionEpisode): String =
  "${episode.bvid}:${episode.cid}"

internal fun collectionEpisodeSelected(
  episode: VideoCollectionEpisode,
  episodes: List<VideoCollectionEpisode>,
  currentBvid: String,
  currentCid: Long,
): Boolean {
  if (episode.bvid != currentBvid) return false
  val sameBvidCount = episodes.count { it.bvid == currentBvid }
  return sameBvidCount <= 1 || episode.cid <= 0L || currentCid <= 0L || episode.cid == currentCid
}

internal fun feedItemFromCollectionEpisode(episode: VideoCollectionEpisode) =
  FeedItem(
    id = collectionEpisodeKey(episode),
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
    playbackPage =
      episode.cid.takeIf { it > 0L }?.let { cid ->
        VideoPage(
          page = 1,
          cid = cid,
          part = episode.title,
          durationSeconds = episode.durationSeconds,
        )
      },
  )

internal data class VideoInfoTileSize(val width: Dp, val height: Dp)

internal fun videoInfoTileSizeForWindow(maxWidth: Dp, maxHeight: Dp): VideoInfoTileSize {
  val horizontalMargin = if (maxWidth < 420.dp) 12.dp else 24.dp
  val verticalMargin = if (maxHeight < 560.dp) 12.dp else 32.dp
  return VideoInfoTileSize(
    width =
      (maxWidth - horizontalMargin - horizontalMargin).coerceAtLeast(1.dp).coerceAtMost(760.dp),
    height = (maxHeight - verticalMargin - verticalMargin).coerceAtLeast(1.dp).coerceAtMost(620.dp),
  )
}

@Composable
fun VideoInfoTile(
  item: FeedItem,
  info: VideoInfo?,
  onlineViewerText: String?,
  description: String,
  onDismiss: () -> Unit,
  onWatchLaterClick: (() -> Unit)? = null,
  watchLaterAdded: Boolean = false,
  watchLaterBusy: Boolean = false,
  onCacheClick: (() -> Unit)? = null,
  onNotInterested: (() -> Unit)? = null,
  onNotInterestedUploader: (() -> Unit)? = null,
  controlEnabled: Boolean = false,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val displayedInfo = remember { info }
  val displayedOnlineViewerText = remember { onlineViewerText }
  val displayedDescription = remember { description }
  val videoLink = remember(displayedInfo?.bvid, item.id, item.videoUrl) {
    buildVideoShareUrl(displayedInfo, item)
  }
  val revealProgress = remember { Animatable(0f) }
  var dismissing by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val controlCopyFocusRequester = remember { FocusRequester() }
  val controlCacheFocusRequester = remember { FocusRequester() }
  val controlCloseFocusRequester = remember { FocusRequester() }
  fun dismissThen(action: () -> Unit) {
    if (dismissing) return
    dismissing = true
    scope.launch {
      revealProgress.animateTo(
        0f,
        tween(durationMillis = 140, easing = FastOutSlowInEasing),
      )
      action()
    }
  }
  LaunchedEffect(Unit) {
    withFrameNanos {}
    revealProgress.animateTo(
      1f,
      tween(durationMillis = 260, easing = FastOutSlowInEasing),
    )
  }
  LaunchedEffect(controlEnabled, onCacheClick != null, videoLink) {
    if (controlEnabled) {
      withFrameNanos {}
      withFrameNanos {}
      runCatching {
        when {
          onCacheClick != null -> controlCacheFocusRequester.requestFocus()
          videoLink.isNotBlank() -> controlCopyFocusRequester.requestFocus()
          else -> controlCloseFocusRequester.requestFocus()
        }
      }
    }
  }
  Dialog(
    onDismissRequest = { dismissThen(onDismiss) },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    BoxWithConstraints(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      val tileSize = videoInfoTileSizeForWindow(maxWidth, maxHeight)
      val darkPage = MaterialTheme.colorScheme.background.luminance() < .5f
      Surface(
        modifier =
          Modifier.width(tileSize.width).height(tileSize.height).graphicsLayer {
            val progress = revealProgress.value.coerceIn(0f, 1f)
            alpha = progress
            val scale = .92f + .08f * progress
            scaleX = scale
            scaleY = scale
          },
        shape = RoundedCornerShape(28.dp),
        color =
          MaterialTheme.colorScheme.surface.copy(
            alpha =
              if (darkPage) VideoPageSurfaceTokens.DarkDialogAlpha
              else VideoPageSurfaceTokens.LightDialogAlpha
          ),
        tonalElevation = 4.dp,
        shadowElevation = 0.dp,
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Text(
            text = displayedInfo?.title ?: item.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
          )
          val uploaderFace =
            displayedInfo?.uploaderFace?.takeIf(String::isNotBlank) ?: item.uploaderFace
          val uploaderName = displayedInfo?.uploaderName ?: item.uploader.orEmpty()
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
          val bvid = (displayedInfo?.bvid ?: item.id).takeIf { it.startsWith("BV") }
          val publishDate = formatPublishDate(displayedInfo?.publishedAt ?: 0)
          Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
              buildList {
                  add("${formatCompactCount(displayedInfo?.playCount ?: 0)} 播放")
                  add("${formatCompactCount(displayedInfo?.danmakuCount ?: item.danmakuCount)} 弹幕")
                  add("${formatCompactCount(displayedInfo?.replyCount ?: 0)} 评论")
                  displayedOnlineViewerText?.takeIf(String::isNotBlank)?.let {
                    add("$it 人在看")
                  }
                }
                .joinToString("  ·  "),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (publishDate != null || bvid != null) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                if (publishDate != null) {
                  Text(
                    text = publishDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                  )
                }
                if (publishDate != null && bvid != null) {
                  Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                if (bvid != null) {
                  Text(
                    text = bvid,
                    modifier =
                      Modifier.pointerInput(bvid) {
                        detectTapGestures(
                          onLongPress = {
                            clipboard.setText(AnnotatedString(bvid))
                            Toast.makeText(context, "已复制 BV 号", Toast.LENGTH_SHORT).show()
                          }
                        )
                      },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                  )
                }
              }
            }
          }
          LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
          ) {
            item {
              BiliRichText(
                text = displayedDescription.ifBlank { "这个视频暂时没有填写简介。" },
                emotes = emptyMap(),
                onTextLongClick = {
                  val copyText = displayedDescription.ifBlank { "这个视频暂时没有填写简介。" }
                  clipboard.setText(AnnotatedString(copyText))
                  Toast.makeText(context, "已复制简介", Toast.LENGTH_SHORT).show()
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = Int.MAX_VALUE,
              )
            }
          }
          if (
            onWatchLaterClick != null || onNotInterested != null || onNotInterestedUploader != null
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
              if (onWatchLaterClick != null) {
                OutlinedButton(
                  enabled = !watchLaterBusy,
                  onClick = { dismissThen(onWatchLaterClick) },
                ) {
                  Text(if (watchLaterAdded) "移出稍后再看" else "添加到稍后再看")
                }
              }
              if (onNotInterested != null) {
                OutlinedButton(onClick = { dismissThen(onNotInterested) }) {
                  Text("内容不感兴趣")
                }
              }
              if (onNotInterestedUploader != null) {
                OutlinedButton(onClick = { dismissThen(onNotInterestedUploader) }) {
                  Text("不想看此 UP 主")
                }
              }
            }
          }
          Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (videoLink.isNotBlank()) {
              TextButton(
                onClick = {
                  clipboard.setText(AnnotatedString(videoLink))
                  Toast.makeText(context, "已复制视频链接", Toast.LENGTH_SHORT).show()
                },
                modifier =
                  Modifier.then(
                    if (controlEnabled) {
                      Modifier.focusRequester(controlCopyFocusRequester)
                        .focusProperties {
                          left = FocusRequester.Cancel
                          right =
                            if (onCacheClick != null) controlCacheFocusRequester
                            else controlCloseFocusRequester
                        }
                        .controlFocusOutline(
                          RoundedCornerShape(14.dp),
                          MaterialTheme.colorScheme.primary,
                          enabled = true,
                        )
                    } else Modifier
                  ),
              ) {
                Text("复制视频链接")
              }
            }
            if (onCacheClick != null) {
              TextButton(
                onClick = { dismissThen(onCacheClick) },
                modifier =
                  Modifier.then(
                    if (controlEnabled) {
                      Modifier.focusRequester(controlCacheFocusRequester)
                        .focusProperties {
                          left =
                            if (videoLink.isNotBlank()) controlCopyFocusRequester
                            else FocusRequester.Cancel
                          right = controlCloseFocusRequester
                        }
                        .controlFocusOutline(
                          RoundedCornerShape(14.dp),
                          MaterialTheme.colorScheme.primary,
                          enabled = true,
                        )
                    } else Modifier
                  ),
              ) {
                Text("缓存视频")
              }
            }
            TextButton(
              onClick = { dismissThen(onDismiss) },
              modifier =
                Modifier.then(
                  if (controlEnabled) {
                    Modifier.focusRequester(controlCloseFocusRequester)
                      .focusProperties {
                        left =
                          when {
                            onCacheClick != null -> controlCacheFocusRequester
                            videoLink.isNotBlank() -> controlCopyFocusRequester
                            else -> FocusRequester.Cancel
                          }
                        right = FocusRequester.Cancel
                      }
                      .controlFocusOutline(
                        RoundedCornerShape(14.dp),
                        MaterialTheme.colorScheme.primary,
                        enabled = true,
                      )
                  } else Modifier
                ),
            ) {
              Text("关闭")
            }
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
  onTimestampClick: (Long, Int) -> Unit = { _, _ -> },
  hiddenLinkedArticleItemId: String? = null,
  onLinkedArticleClick: (ArticleItem, Rect) -> Unit = { _, _ -> },
  onLinkedArticleBoundsChanged: (ArticleItem, Rect) -> Unit = { _, _ -> },
  deletionSelectedRpid: Long? = null,
  canDeleteComment: (CommentItem) -> Boolean = { false },
  onDeleteRequest: (CommentItem, Rect) -> Unit = { _, _ -> },
  onDeleteConfirm: (CommentItem) -> Unit = {},
  onDeleteCancel: () -> Unit = {},
  onDeletionBoundsChanged: (Rect) -> Unit = {},
  pinActionAvailable: Boolean = false,
  pinActionLabel: (CommentItem) -> String = { "置顶" },
  onPinRequest: (CommentItem) -> Unit = {},
  largeCommentText: Boolean = false,
  backHandlingEnabled: Boolean = true,
  controlEnabled: Boolean = false,
  controlInitialFocusRequester: FocusRequester? = null,
  modifier: Modifier = Modifier,
) {
  val controlBackFocusRequester = remember { FocusRequester() }
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
  Box(modifier.clip(RoundedCornerShape(24.dp))) {
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
          IconButton(
            onClick = onDismiss,
            modifier =
              Modifier.then(
                if (controlEnabled) {
                  Modifier.focusRequester(controlBackFocusRequester)
                    .controlFocusOutline(
                      CircleShape,
                      MaterialTheme.colorScheme.primary,
                      width = 3.dp,
                      enabled = true,
                    )
                } else Modifier
              ),
          ) {
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
                onTimestampClick = onTimestampClick,
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
                pinActionAvailable = pinActionAvailable,
                pinActionLabel = pinActionLabel(root),
                onPinRequest = { onPinRequest(root) },
                largeText = largeCommentText,
                controlEnabled = controlEnabled,
                controlFocusRequester = controlInitialFocusRequester,
                controlUpFocusRequester = controlBackFocusRequester,
                controlAtListEnd = replies.isEmpty() && !hasMore,
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
            itemsIndexed(
              items = replies,
              key = { _, reply -> "reply_${reply.rpid}" },
              contentType = { _, _ -> "reply" },
            ) { index, reply ->
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
                onTimestampClick = onTimestampClick,
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
                controlEnabled = controlEnabled,
                controlAtListEnd = index == replies.lastIndex && !hasMore,
              )
            }
            if (hasMore || loading) {
              item(key = "more") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                  if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                  else
                    TextButton(
                      onClick = onLoadMore,
                      modifier =
                        Modifier.controlFocusOutline(
                          RoundedCornerShape(14.dp),
                          MaterialTheme.colorScheme.primary,
                          width = 3.dp,
                          enabled = controlEnabled,
                        ),
                    ) {
                      Text("加载更多回复")
                    }
                }
              }
            }
          }
        }
      }
    }
    // 让回复瓦片延续穿过其下圆角边：全局输入坞位于该区域之上，因此额外的表面
    // 遮住底下评论列表而不盖住回复内容。
    Surface(
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(20.dp),
      shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
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

package dev.openbili.webdemo.my

/**
 * "我的收藏"面板：收藏夹网格、夹内搜索分页与卡片操作。
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 收藏视频面板组合体。 */
@Composable
internal fun VideoPanel(
  state: MyUiState,
  onFolder: ((Long) -> Unit)?,
  onVideo: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  hiddenCoverItemId: String?,
  onFavoriteQuery: (String) -> Unit = {},
  onLoadMoreFavorites: () -> Unit = {},
  onRemoveFavorite: (FeedItem) -> Unit = {},
  onCopyFavorite: (FeedItem, Long) -> Unit = { _, _ -> },
  onMoveFavorite: (FeedItem, Long) -> Unit = { _, _ -> },
  onCreateFolder: (String, Boolean) -> Unit = { _, _ -> },
  onEditFolder: (FavoriteFolder, String, Boolean) -> Unit = { _, _, _ -> },
  onDeleteFolder: (FavoriteFolder) -> Unit = {},
) {
  val favoriteMode = onFolder != null
  val gridState = rememberLazyGridState()
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(gridState)
  val scope = rememberCoroutineScope()
  var managedVideoId by remember(state.selectedFolderId) { mutableStateOf<String?>(null) }
  var deleteConfirmationId by remember(state.selectedFolderId) { mutableStateOf<String?>(null) }
  var transferRequest by
    remember(state.selectedFolderId) {
      mutableStateOf<FavoriteTransferRequest?>(null)
    }
  var searchVisible by remember(state.selectedFolderId) { mutableStateOf(false) }
  var managedFolderId by remember { mutableStateOf<Long?>(null) }
  var addingFolder by remember { mutableStateOf(false) }
  var folderDeleteConfirmation by remember(managedFolderId) { mutableStateOf(false) }
  val managedVideo = state.videos.firstOrNull { it.id == managedVideoId }
  val managedFolder = state.folders.firstOrNull { it.id == managedFolderId }
  LaunchedEffect(state.selectedFolderId, state.favoriteQuery) {
    if (favoriteMode && state.videos.isNotEmpty()) gridState.scrollToItem(0)
  }
  LaunchedEffect(managedVideoId, state.videos) {
    if (managedVideoId != null && managedVideo == null) {
      managedVideoId = null
      deleteConfirmationId = null
      transferRequest = null
    }
  }
  BackHandler(
    enabled =
      favoriteMode &&
        (transferRequest != null ||
          managedVideoId != null ||
          managedFolderId != null ||
          addingFolder ||
          searchVisible)
  ) {
    when {
      transferRequest != null -> transferRequest = null
      managedFolderId != null -> managedFolderId = null
      addingFolder -> addingFolder = false
      managedVideoId != null -> {
        managedVideoId = null
        deleteConfirmationId = null
      }
      searchVisible -> {
        searchVisible = false
        if (state.favoriteQuery.isNotBlank()) onFavoriteQuery("")
      }
    }
  }

  Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      if (favoriteMode) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
          ) {
            items(state.folders, key = { it.id }) { folder ->
              Surface(
                modifier =
                  Modifier.combinedClickable(
                    onClick = {
                      managedVideoId = null
                      deleteConfirmationId = null
                      transferRequest = null
                      onFolder(folder.id)
                    },
                    onLongClick = {
                      folderDeleteConfirmation = false
                      managedFolderId = folder.id
                    },
                  ),
                shape = RoundedCornerShape(18.dp),
                color =
                  if (folder.isPublic) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceContainer,
                contentColor =
                  if (folder.isPublic) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSurface,
                border =
                  if (state.selectedFolderId == folder.id)
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                  else BorderStroke(.75.dp, MaterialTheme.colorScheme.outlineVariant),
              ) {
                Text(
                  "${folder.title}  ${folder.mediaCount}",
                  Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                )
              }
            }
          }
          Surface(
            modifier = Modifier.size(38.dp).clickable { addingFolder = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text("+", style = MaterialTheme.typography.titleLarge)
            }
          }
        }
      }
      AnimatedVisibility(
        visible = favoriteMode && searchVisible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(120)),
      ) {
        OutlinedTextField(
          value = state.favoriteQuery,
          onValueChange = onFavoriteQuery,
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
          singleLine = true,
          label = { Text("搜索此收藏夹") },
          placeholder = { Text("输入视频标题或 UP 主") },
        )
      }
      CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
        LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          state = gridState,
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(bottom = NavigationCardBottomClearance),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (state.videos.isEmpty() && !state.loading) {
            item(
              key = "video_panel_empty_${state.selectedFolderId}_${state.favoriteQuery}",
              span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
            ) {
              Box(
                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  if (favoriteMode && state.favoriteQuery.isNotBlank()) "没有找到相关视频"
                  else if (favoriteMode) "这个收藏夹还是空的" else "暂无历史视频",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          itemsIndexed(state.videos, key = { _, video -> video.id }) { index, video ->
            var coverBounds by remember(video.id) { mutableStateOf(Rect.Zero) }
            VideoCardReveal(
              index = index,
              batchKey = state.videos.firstOrNull()?.id,
              itemKey = video.id,
            ) {
              PressableVideoCard(
                onClick = {
                  if (managedVideoId != null) {
                    managedVideoId = null
                    deleteConfirmationId = null
                  } else {
                    onVideo(video, coverBounds.takeUnless { it == Rect.Zero } ?: Rect.Zero)
                  }
                },
                onLongClick = {
                  if (favoriteMode) {
                    deleteConfirmationId = null
                    managedVideoId = video.id
                  } else onVideoLongClick(video)
                },
              ) {
                Box {
                  MyVideoCardContent(
                    item = video,
                    coverVisible = video.id != hiddenCoverItemId,
                    onCoverBoundsChanged = { coverBounds = it },
                  )
                  if (favoriteMode && managedVideoId == video.id) {
                    FavoriteManagementOverlay(
                      deleteConfirmation = deleteConfirmationId == video.id,
                      busy = state.favoriteActionBusyId == video.id,
                      onDismiss = {
                        managedVideoId = null
                        deleteConfirmationId = null
                      },
                      onRemoveRequest = { deleteConfirmationId = video.id },
                      onRemoveUndo = { deleteConfirmationId = null },
                      onRemoveConfirm = {
                        managedVideoId = null
                        deleteConfirmationId = null
                        onRemoveFavorite(video)
                      },
                      onCopy = {
                        transferRequest = FavoriteTransferRequest(video, move = false)
                      },
                      onMove = {
                        transferRequest = FavoriteTransferRequest(video, move = true)
                      },
                    )
                  }
                }
              }
            }
          }
          if (favoriteMode && state.favoriteHasMore) {
            item(
              key =
                "favorite_load_more_${state.selectedFolderId}_${state.favoritePage}_${state.favoriteQuery}",
              span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
            ) {
              LaunchedEffect(
                state.selectedFolderId,
                state.favoritePage,
                state.favoriteQuery,
                imageLoadPolicy.mode,
              ) {
                if (imageLoadPolicy.mode != FeedImageLoadMode.PAUSED) onLoadMoreFavorites()
              }
              Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
              }
            }
          }
        }
      }
    }

    if (favoriteMode) {
      Column(
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SmallFloatingActionButton(
          onClick = { scope.launch { gridState.animateScrollToItem(0) } },
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
          contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
          Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = "回到收藏夹顶部",
          )
        }
        SmallFloatingActionButton(
          onClick = {
            searchVisible = !searchVisible
            if (!searchVisible && state.favoriteQuery.isNotBlank()) onFavoriteQuery("")
          },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
          Icon(Icons.Default.Search, contentDescription = "搜索此收藏夹的视频")
        }
      }
    }
  }

  transferRequest?.let { request ->
    FavoriteFolderPicker(
      folders = state.folders.filterNot { it.id == state.selectedFolderId },
      move = request.move,
      busy = state.favoriteActionBusyId == request.video.id,
      onDismiss = { transferRequest = null },
      onFolder = { folderId ->
        transferRequest = null
        managedVideoId = null
        if (request.move) onMoveFavorite(request.video, folderId)
        else onCopyFavorite(request.video, folderId)
      },
    )
  }
  if (addingFolder) {
    FavoriteFolderEditorDialog(
      folder = null,
      busy = state.favoriteFolderActionBusy,
      deleteConfirmation = false,
      onDismiss = { addingFolder = false },
      onSave = { title, isPublic ->
        addingFolder = false
        onCreateFolder(title, isPublic)
      },
      onDeleteRequest = {},
      onDeleteUndo = {},
      onDeleteConfirm = {},
    )
  }
  managedFolder?.let { folder ->
    FavoriteFolderEditorDialog(
      folder = folder,
      busy = state.favoriteFolderActionBusy,
      deleteConfirmation = folderDeleteConfirmation,
      onDismiss = { managedFolderId = null },
      onSave = { title, isPublic ->
        managedFolderId = null
        onEditFolder(folder, title, isPublic)
      },
      onDeleteRequest = { folderDeleteConfirmation = true },
      onDeleteUndo = { folderDeleteConfirmation = false },
      onDeleteConfirm = {
        managedFolderId = null
        onDeleteFolder(folder)
      },
    )
  }
}

@Composable
private fun FavoriteFolderEditorDialog(
  folder: FavoriteFolder?,
  busy: Boolean,
  deleteConfirmation: Boolean,
  onDismiss: () -> Unit,
  onSave: (String, Boolean) -> Unit,
  onDeleteRequest: () -> Unit,
  onDeleteUndo: () -> Unit,
  onDeleteConfirm: () -> Unit,
) {
  var title by remember(folder?.id) { mutableStateOf(folder?.title.orEmpty()) }
  var isPublic by remember(folder?.id) { mutableStateOf(folder?.isPublic ?: true) }
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(if (folder == null) "添加收藏夹" else "管理收藏夹") },
    text = {
      if (deleteConfirmation) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("确定删除“${folder?.title.orEmpty()}”吗？")
          Text(
            "这一步无法自动恢复，再想一下也完全没关系。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(40) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("收藏夹名称") },
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("公开收藏夹")
              Text(
                if (isPublic) "其他人可以看到" else "仅自己可见",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(checked = isPublic, onCheckedChange = { isPublic = it }, enabled = !busy)
          }
          if (folder != null) {
            TextButton(onClick = onDeleteRequest, enabled = !busy) {
              Text("删除收藏夹", color = MaterialTheme.colorScheme.error)
            }
          }
        }
      }
    },
    confirmButton = {
      if (deleteConfirmation) {
        TextButton(onClick = onDeleteUndo, enabled = !busy) { Text("反悔") }
      } else {
        TextButton(
          onClick = { onSave(title.trim(), isPublic) },
          enabled = title.isNotBlank() && !busy,
        ) {
          Text(if (folder == null) "添加" else "保存")
        }
      }
    },
    dismissButton = {
      if (deleteConfirmation) {
        TextButton(onClick = onDeleteConfirm, enabled = !busy) {
          Text("删除", color = MaterialTheme.colorScheme.error)
        }
      } else {
        TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
      }
    },
  )
}

private data class FavoriteTransferRequest(val video: FeedItem, val move: Boolean)

@Composable
private fun FavoriteManagementOverlay(
  deleteConfirmation: Boolean,
  busy: Boolean,
  onDismiss: () -> Unit,
  onRemoveRequest: () -> Unit,
  onRemoveUndo: () -> Unit,
  onRemoveConfirm: () -> Unit,
  onCopy: () -> Unit,
  onMove: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxSize().zIndex(2f).clickable(enabled = !busy, onClick = onDismiss),
    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .96f),
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    shape = VideoShapeTokens.Card,
  ) {
    Box(Modifier.fillMaxSize()) {
      AnimatedVisibility(
        visible = !deleteConfirmation && !busy,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(110)),
      ) {
        Row(
          Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onRemoveRequest) {
            Text("取消收藏", color = MaterialTheme.colorScheme.error)
          }
          TextButton(onClick = onCopy) { Text("复制到") }
          TextButton(onClick = onMove) { Text("移动到") }
        }
      }
      AnimatedVisibility(
        visible = deleteConfirmation && !busy,
        enter = fadeIn(tween(durationMillis = 160, delayMillis = 100)),
        exit = fadeOut(tween(100)),
      ) {
        Row(
          Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(20.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            "真的吗？",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
          )
          TextButton(onClick = onRemoveUndo) { Text("反悔") }
          TextButton(onClick = onRemoveConfirm) {
            Text("删除", color = MaterialTheme.colorScheme.error)
          }
        }
      }
      if (busy) {
        CircularProgressIndicator(
          modifier = Modifier.size(26.dp).align(Alignment.Center),
          strokeWidth = 2.dp,
        )
      }
    }
  }
}

@Composable
private fun FavoriteFolderPicker(
  folders: List<dev.openbili.webdemo.api.FavoriteFolder>,
  move: Boolean,
  busy: Boolean,
  onDismiss: () -> Unit,
  onFolder: (Long) -> Unit,
) {
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(if (move) "移动到哪个收藏夹？" else "复制到哪个收藏夹？") },
    text = {
      if (folders.isEmpty()) {
        Text("暂时没有其他收藏夹")
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().height(320.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(folders, key = { it.id }) { folder ->
            Surface(
              modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onFolder(folder.id) },
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
              Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(folder.title, modifier = Modifier.weight(1f))
                Text(
                  folder.mediaCount.toString(),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } },
  )
}

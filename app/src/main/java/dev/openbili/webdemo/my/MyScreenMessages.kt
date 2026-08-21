package dev.openbili.webdemo.my

/**
 * "我的消息"与个人空间私信面板：虚拟列表聊天、图片/卡片消息与输入坞。
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
import dev.openbili.webdemo.api.BiliVideoApi
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

/** 个人空间来源的单一私信对话面板。 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ProfilePrivateConversationPane(
  state: MyUiState,
  onLoadMoreHistory: () -> Unit,
  onReplyPrivate: (String, Uri?) -> Unit,
  onWithdraw: (AccountMessage) -> Unit,
  onDelete: (AccountMessage) -> Unit,
  onProfile: (Long, String, String, Rect) -> Unit,
  onTarget: (AccountMessage, Rect) -> Unit,
) {
  NativeMessagePane(
    state = state,
    onMessage = {},
    onLoadMore = {},
    onLoadMoreHistory = onLoadMoreHistory,
    onReply = {},
    onReplyPrivate = onReplyPrivate,
    onWithdraw = onWithdraw,
    onDelete = onDelete,
    onProfile = onProfile,
    onTarget = onTarget,
    hiddenTargetMessageId = null,
    showSessionList = false,
  )
}

/** "我的消息"面板组合体。 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun NativeMessagePane(
  state: MyUiState,
  onMessage: (Long) -> Unit,
  onLoadMore: () -> Unit,
  onLoadMoreHistory: () -> Unit,
  onReply: (String) -> Unit,
  onReplyPrivate: (String, Uri?) -> Unit,
  onWithdraw: (AccountMessage) -> Unit,
  onDelete: (AccountMessage) -> Unit,
  onProfile: (Long, String, String, Rect) -> Unit,
  onTarget: (AccountMessage, Rect) -> Unit,
  hiddenTargetMessageId: Long?,
  showSessionList: Boolean = true,
) {
  val selected = state.messages.firstOrNull { it.id == state.selectedMessageId }
  val emotes = remember(state.messageEmotePackages) { state.messageEmotePackages.emoteCatalog() }
  val clipboard = LocalClipboardManager.current
  val paneScope = rememberCoroutineScope()
  var paneBounds by remember { mutableStateOf(Rect.Zero) }
  var imagePreview by remember { mutableStateOf<CommentImagePreviewSession?>(null) }
  var actionMessage by remember { mutableStateOf<AccountMessage?>(null) }
  var selectableMessage by remember { mutableStateOf<AccountMessage?>(null) }
  val sessionListState = rememberLazyListState()
  val density = LocalDensity.current
  val imeVisible = WindowInsets.ime.getBottom(density) > 0
  LaunchedEffect(sessionListState, state.messages.size, state.privateSessionHasMore) {
    if (!showSessionList) return@LaunchedEffect
    snapshotFlow {
        sessionListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
      }
      .collectLatest { lastVisible ->
        if (state.privateSessionHasMore && lastVisible >= state.messages.lastIndex - 3) {
          onLoadMore()
        }
      }
  }
  Box(
    Modifier.fillMaxSize()
      .clip(RoundedCornerShape(20.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
      .onGloballyPositioned { paneBounds = it.boundsInRoot() }
  ) {
    Row(Modifier.fillMaxSize()) {
      if (showSessionList) {
        LazyColumn(
          state = sessionListState,
          modifier =
            Modifier.width(310.dp)
              .fillMaxHeight()
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
          contentPadding =
            PaddingValues(
              start = 8.dp,
              top = 8.dp,
              end = 8.dp,
              bottom = NavigationCardBottomClearance,
            ),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(state.messages, key = { it.id }) { message ->
            MessageListRow(
              message,
              selected = message.id == selected?.id,
              onClick = { onMessage(message.id) },
            )
          }
          if (state.privateSessionLoadingMore) {
            item(key = "private_session_loading") {
              Box(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
              }
            }
          }
        }
        Spacer(Modifier.width(20.dp))
      }
      if (selected == null) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
          Text(
            "(。・ω・。) 暂无私信消息",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        var avatarBounds by remember(selected.id) { mutableStateOf(Rect.Zero) }
        val history = state.privateMessageHistory[selected.userMid].orEmpty()
        // 前后各保持约十条紧凑气泡处于热组合状态；像素窗口之外的消息只保留轻量
        // 模型，滚动接近时才组合，避免长图/卡片会话保留整段渲染历史。
        val historyState =
          rememberLazyListState(
            cacheWindow = LazyLayoutCacheWindow(ahead = 720.dp, behind = 720.dp)
          )
        var renderedHistoryIds by remember(selected.userMid) { mutableStateOf(emptySet<Long>()) }
        var initialHistoryPositioned by remember(selected.userMid) { mutableStateOf(false) }
        var lastObservedHistoryId by remember(selected.userMid) { mutableStateOf<Long?>(null) }
        var followConversationBottom by remember(selected.userMid) { mutableStateOf(true) }
        // 不要把我们自己的滚底当成用户接管会话，否则新收到的消息会把自己的
        // 跟随动画中途取消。
        var autoScrollingToBottom by remember(selected.userMid) { mutableStateOf(false) }
        LaunchedEffect(historyState, initialHistoryPositioned) {
          if (!initialHistoryPositioned) return@LaunchedEffect
          snapshotFlow { historyState.isScrollInProgress to historyState.canScrollForward }
            .collectLatest { (scrolling, canScrollForward) ->
              if (!canScrollForward) {
                followConversationBottom = true
              } else if (scrolling && !autoScrollingToBottom) {
                followConversationBottom = false
              }
            }
        }
        LaunchedEffect(selected.userMid, history.lastOrNull()?.id) {
          if (history.isNotEmpty() && !initialHistoryPositioned) {
            historyState.scrollToItem(history.lastIndex)
            initialHistoryPositioned = true
            renderedHistoryIds = history.mapTo(mutableSetOf(), AccountMessage::id)
            lastObservedHistoryId = history.last().id
            followConversationBottom = true
          } else if (history.isNotEmpty() && history.last().id != lastObservedHistoryId) {
            val newest = history.last()
            lastObservedHistoryId = newest.id
            if (followConversationBottom) {
              autoScrollingToBottom = true
              try {
                // 先让新气泡进入组合：直接定位滚动能让新消息与已发送消息对齐，
                // 而不是差一行停在半截。
                kotlinx.coroutines.yield()
                historyState.scrollToItem(history.lastIndex)
                // AnimatedVisibility 在第一次定位之后才展开收到的气泡：等进入动画
                // 贡献了最终高度后再对齐一次。
                delay(280L)
                historyState.scrollToItem(history.lastIndex)
              } finally {
                autoScrollingToBottom = false
                followConversationBottom = !historyState.canScrollForward
              }
            }
          }
        }
        LaunchedEffect(
          historyState,
          state.privateHistoryHasMore,
          state.privateHistoryLoadingMore,
          initialHistoryPositioned,
        ) {
          if (!initialHistoryPositioned) return@LaunchedEffect
          // 三星的拉伸式过滚动会让 canScrollBackward 在手势反向之前一直为 true。
          // 因此在前三行之一进入视口时就预取，而不是等平台报告精确的顶部边缘。
          snapshotFlow {
              historyState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: Int.MAX_VALUE
            }
            .collectLatest { firstVisible ->
              if (
                firstVisible <= 2 && state.privateHistoryHasMore && !state.privateHistoryLoadingMore
              ) {
                onLoadMoreHistory()
              }
            }
        }
        VideoCardGradient(
          coverUrl = selected.userFace,
          loadKey = "private-pane:${selected.userMid}",
          modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)),
        ) {
          Column(
            Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.surface.copy(alpha = .62f))
              .padding(start = 20.dp, top = 16.dp, end = 20.dp)
          ) {
            Row(
              modifier =
                Modifier.clickable {
                  onProfile(selected.userMid, selected.userFace, selected.userName, avatarBounds)
                },
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AsyncImage(
                selected.userFace,
                null,
                Modifier.size(44.dp)
                  .onGloballyPositioned { avatarBounds = it.boundsInRoot() }
                  .clip(CircleShape),
                contentScale = ContentScale.Crop,
              )
              Text(
                selected.userName,
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.titleMedium,
              )
            }
            HorizontalDivider(Modifier.padding(top = 12.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
              if (state.privateMessagesLoading && history.isEmpty()) {
                CircularProgressIndicator(
                  Modifier.size(28.dp).align(Alignment.Center),
                  strokeWidth = 2.dp,
                )
              } else {
                LazyColumn(
                  state = historyState,
                  modifier = Modifier.fillMaxSize(),
                  // 列表绘制在两个悬浮面之后；这个 inset 只保证最后一条消息仍能
                  // 完整滚到它们上方。
                  contentPadding =
                    PaddingValues(
                      top = 18.dp,
                      bottom = if (showSessionList) 250.dp else 126.dp,
                    ),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  if (state.privateHistoryLoadingMore) {
                    item(key = "private_history_loading") {
                      Box(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                      ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                      }
                    }
                  }
                  items(
                    items = history,
                    key = { it.id },
                    contentType = { message ->
                      when {
                        message.withdrawn || message.isPrivateNotice -> "notice"
                        message.messageType == 2 && message.coverUrl.isNotBlank() -> "image"
                        message.coverUrl.isNotBlank() ||
                          message.targetKind != MessageTargetKind.UNKNOWN -> "target"
                        else -> "text"
                      }
                    },
                  ) { message ->
                    val isIncomingAfterInitialRender =
                      !message.isOutgoing &&
                        renderedHistoryIds.isNotEmpty() &&
                        message.id !in renderedHistoryIds
                    var messageVisible by
                      remember(message.id) {
                        mutableStateOf(!isIncomingAfterInitialRender)
                      }
                    LaunchedEffect(message.id) {
                      if (isIncomingAfterInitialRender) {
                        delay(16L)
                        messageVisible = true
                      }
                      renderedHistoryIds = renderedHistoryIds + message.id
                    }
                    androidx.compose.animation.AnimatedVisibility(
                      visible = messageVisible,
                      enter =
                        if (isIncomingAfterInitialRender) {
                          fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 2 }
                        } else {
                          EnterTransition.None
                        },
                    ) {
                      PrivateMessageBubble(
                        message = message,
                        emotes = emotes,
                        coverVisible = hiddenTargetMessageId != message.id,
                        onTarget = onTarget,
                        onImagePreview = { image, bounds ->
                          imagePreview = CommentImagePreviewSession(image, bounds)
                        },
                        onLongPress = { actionMessage = message },
                      )
                    }
                  }
                }
              }
              Column(
                modifier =
                  Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    // 独立空间会话没有底部根胶囊：只有"我的消息"变体在键盘收起时
                    // 预留这段空间。
                    .padding(bottom = if (imeVisible || !showSessionList) 0.dp else 124.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                AnimatedVisibility(visible = !state.error.isNullOrBlank()) {
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shadowElevation = 0.dp,
                  ) {
                    Text(
                      state.error.orEmpty(),
                      color = MaterialTheme.colorScheme.onErrorContainer,
                      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                  }
                }
                PrivateMessageComposer(
                  placeholder = "发送私信给 ${selected.userName}",
                  emotePackages = state.messageEmotePackages,
                  sending = state.privateMessageSending,
                  sendSuccessToken = state.privateMessageSendSuccessToken,
                  onSend = onReplyPrivate,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }
        }
      }
    }
    imagePreview?.let { session ->
      LaunchedEffect(session) {
        session.progress.snapTo(0f)
        session.progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
      }
      CommentImagePreviewOverlay(
        session = session,
        rootBounds = paneBounds,
        onDismiss = {
          paneScope.launch {
            session.progress.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
            if (imagePreview === session) imagePreview = null
          }
        },
        modifier = Modifier.fillMaxSize().zIndex(80f),
      )
    }
    actionMessage?.let { message ->
      AlertDialog(
        onDismissRequest = { actionMessage = null },
        title = { Text("消息操作") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (message.isOutgoing && !message.withdrawn) {
              TextButton(
                enabled = message.messageKey > 0L,
                onClick = {
                  actionMessage = null
                  onWithdraw(message)
                },
              ) {
                Text(if (message.messageKey > 0L) "撤回" else "撤回（等待同步）")
              }
            }
            TextButton(
              onClick = {
                actionMessage = null
                onDelete(message)
              }
            ) {
              Text("删除")
            }
            TextButton(
              onClick = {
                clipboard.setText(AnnotatedString(message.content.ifBlank { message.linkUrl }))
                actionMessage = null
              }
            ) {
              Text("复制")
            }
            if (message.content.isNotBlank() || message.linkUrl.isNotBlank()) {
              TextButton(
                onClick = {
                  actionMessage = null
                  selectableMessage = message
                }
              ) {
                Text("选择文本")
              }
            }
          }
        },
        confirmButton = { TextButton(onClick = { actionMessage = null }) { Text("取消") } },
      )
    }
    selectableMessage?.let { message ->
      val selectableText = message.content.ifBlank { message.linkUrl }
      var selectionValue by
        remember(message.id, selectableText) {
          mutableStateOf(TextFieldValue(selectableText))
        }
      AlertDialog(
        onDismissRequest = { selectableMessage = null },
        title = { Text("选择要复制的文本") },
        text = {
          // 单条视觉行让字符命中检测只依赖水平位置：手指垂直漂移不会把选区
          // 跳到另一行换行文本上。
          BasicTextField(
            value = selectionValue,
            onValueChange = { selectionValue = it.copy(text = selectableText) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            singleLine = true,
            textStyle =
              MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
          )
        },
        confirmButton = {
          TextButton(onClick = { selectableMessage = null }) { Text("完成") }
        },
      )
    }
  }
}

@Composable
private fun PrivateMessageBubble(
  message: AccountMessage,
  emotes: Map<String, BiliEmote>,
  coverVisible: Boolean,
  onTarget: (AccountMessage, Rect) -> Unit,
  onImagePreview: (CommentImage, Rect) -> Unit,
  onLongPress: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
  ) {
    if (!message.withdrawn && !message.isPrivateNotice) {
      Text(
        formatMessageTime(message.time),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
      )
    }
    if (message.withdrawn || message.isPrivateNotice) {
      Surface(
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f),
      ) {
        Text(
          message.content,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else if (message.messageType == 2 && message.coverUrl.isNotBlank()) {
      var imageBounds by remember(message.id) { mutableStateOf(Rect.Zero) }
      val aspect =
        if (message.mediaWidth > 0 && message.mediaHeight > 0)
          (message.mediaWidth.toFloat() / message.mediaHeight).coerceIn(.4f, 2.4f)
        else 1f
      val imageWidth = if (aspect >= 1f) 320.dp else (360.dp * aspect).coerceAtLeast(144.dp)
      val imageHeight = if (aspect >= 1f) (320.dp / aspect).coerceAtLeast(134.dp) else 360.dp
      AsyncImage(
        model = message.coverUrl,
        contentDescription = "图片消息",
        modifier =
          Modifier.size(imageWidth, imageHeight)
            .onGloballyPositioned { imageBounds = it.boundsInRoot() }
            .clip(VideoShapeTokens.Player)
            .combinedClickable(
              onClick = {
                onImagePreview(
                  CommentImage(message.coverUrl, message.mediaWidth, message.mediaHeight),
                  imageBounds,
                )
              },
              onLongClick = onLongPress,
            ),
        contentScale = ContentScale.Fit,
      )
    } else if (message.coverUrl.isNotBlank() || message.targetKind != MessageTargetKind.UNKNOWN) {
      var cardBounds by remember(message.id) { mutableStateOf(Rect.Zero) }
      var coverBounds by remember(message.id) { mutableStateOf(Rect.Zero) }
      var displayTitle by
        remember(message.id, message.title, message.subjectTitle) {
          mutableStateOf(message.subjectTitle.ifBlank { message.title })
        }
      LaunchedEffect(message.id, message.linkUrl, message.targetKind) {
        if (message.targetKind == MessageTargetKind.VIDEO) {
          val bvid =
            Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE).find(message.linkUrl)?.value
          if (!bvid.isNullOrBlank()) {
            val resolved =
              withContext(Dispatchers.IO) {
                runCatching { BiliVideoApi.getVideoInfo(bvid)?.title }.getOrNull()
              }
            if (!resolved.isNullOrBlank()) displayTitle = resolved
          }
        }
      }
      Surface(
        modifier =
          Modifier.width(330.dp)
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .combinedClickable(
              onClick = {
                if (message.linkUrl.isNotBlank() || message.oid > 0L) {
                  val source = coverBounds.takeIf { it.width > 0f && it.height > 0f } ?: cardBounds
                  onTarget(message.copy(subjectTitle = displayTitle), source)
                }
              },
              onLongClick = onLongPress,
            ),
        shape = VideoShapeTokens.Player,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(.75.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
          if (message.coverUrl.isNotBlank()) {
            AsyncImage(
              model = message.coverUrl,
              contentDescription = displayTitle,
              modifier =
                Modifier.width(132.dp)
                  .aspectRatio(16f / 9f)
                  .onGloballyPositioned { coverBounds = it.boundsInRoot() }
                  .graphicsLayer { alpha = if (coverVisible) 1f else 0f }
                  .clip(VideoShapeTokens.Card),
              contentScale = ContentScale.Crop,
            )
          }
          Column(
            Modifier.weight(1f).padding(start = if (message.coverUrl.isBlank()) 0.dp else 12.dp)
          ) {
            if (message.targetKind != MessageTargetKind.UNKNOWN) {
              Text(
                if (message.targetKind == MessageTargetKind.ARTICLE) "专栏" else "视频",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Text(
              displayTitle.ifBlank { message.content },
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            if (message.content.isNotBlank() && message.content != displayTitle) {
              Text(
                message.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    } else {
      Surface(
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
        shape = RoundedCornerShape(16.dp),
        color =
          if (message.isOutgoing) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surfaceVariant,
      ) {
        BiliRichText(
          text = message.content,
          emotes = emotes,
          onTextLongClick = onLongPress,
          modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 14.dp, vertical = 11.dp),
          style = MaterialTheme.typography.bodyLarge,
          maxLines = Int.MAX_VALUE,
        )
      }
    }
  }
}

@Composable
private fun MessageListRow(message: AccountMessage, selected: Boolean, onClick: () -> Unit) {
  VideoCardGradient(
    coverUrl = message.userFace,
    loadKey = "private:${message.userMid}",
    // 让每个会话行高度一致，两行预览不会改变人物列表的节奏。
    modifier = Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(14.dp)),
  ) {
    Box(
      Modifier.fillMaxWidth()
        .fillMaxHeight()
        .background(
          if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .58f)
          else Color.Transparent
        )
        .clickable(onClick = onClick)
    ) {
      Row(Modifier.fillMaxHeight().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
          message.userFace,
          null,
          Modifier.size(42.dp).clip(CircleShape),
          contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(message.userName, maxLines = 1, modifier = Modifier.weight(1f))
            Text(
              formatMessageTime(message.time),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            message.content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (message.unreadCount > 0) {
        Box(
          Modifier.align(Alignment.TopEnd)
            .offset(x = (-3).dp, y = 3.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
        )
      }
    }
  }
}

@Composable
private fun PrivateMessageComposer(
  placeholder: String,
  emotePackages: List<BiliEmotePackage>,
  sending: Boolean,
  sendSuccessToken: Long,
  onSend: (String, Uri?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val editorState = rememberTextFieldState()
  val emotes = remember(emotePackages) { emotePackages.flatMap(BiliEmotePackage::emotes) }
  val markerRegistry = remember { CommentEmoteMarkerRegistry() }
  val markerSnapshot = remember(emotes, markerRegistry) { markerRegistry.snapshot(emotes) }
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  var showTools by remember { mutableStateOf(false) }
  var toolPage by remember { mutableStateOf(CommentToolPage.EMOTES) }
  var imageUri by remember { mutableStateOf<Uri?>(null) }
  var observedSuccessToken by remember { mutableStateOf(sendSuccessToken) }
  var submissionLocked by remember { mutableStateOf(false) }
  var observedSending by remember { mutableStateOf(sending) }
  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selected ->
      if (selected != null) {
        imageUri = selected
        showTools = false
      }
    }
  val text = editorState.text.toString()
  val canSend = !sending && !submissionLocked && (text.isNotBlank() || imageUri != null)
  val editorMaxLines = if (imageUri == null) 8 else 5
  val editorMaxHeight = if (imageUri == null) 224.dp else 152.dp
  LaunchedEffect(sending) {
    if (sending) {
      observedSending = true
    } else if (observedSending) {
      observedSending = false
      submissionLocked = false
    }
  }
  LaunchedEffect(sendSuccessToken) {
    if (sendSuccessToken != observedSuccessToken) {
      observedSuccessToken = sendSuccessToken
      submissionLocked = false
      editorState.edit { delete(0, length) }
      imageUri = null
      showTools = false
    }
  }
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    AnimatedVisibility(
      visible = showTools,
      enter = fadeIn(tween(180)),
      exit = fadeOut(tween(130)),
    ) {
      CommentToolPanel(
        page = toolPage,
        onPageChanged = { toolPage = it },
        emotes = emotes,
        emotePackages = emotePackages,
        mentionQuery = "",
        onMentionQueryChanged = {},
        mentionSuggestions = emptyList(),
        mentionSuggestionsLoading = false,
        onEmoteSelected = { emote ->
          val replacement = markerSnapshot.markerFor(emote)?.toString() ?: emote.text
          val start = minOf(editorState.selection.start, editorState.selection.end)
          val end = maxOf(editorState.selection.start, editorState.selection.end)
          editorState.edit {
            replace(start, end, replacement)
            selection = TextRange(start + replacement.length)
          }
          focusRequester.requestFocus()
        },
        onMentionSelected = {},
        allowMentions = false,
        imagePickerAvailable = true,
        onImagePick = { imagePicker.launch("image/*") },
      )
    }
    Surface(
      modifier = Modifier.fillMaxWidth().animateContentSize(),
      shape = RoundedCornerShape(30.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
      shadowElevation = 0.dp,
      border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
    ) {
      Column(
        Modifier.fillMaxWidth().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        imageUri?.let { uri ->
          Box(Modifier.size(78.dp)) {
            AsyncImage(
              model = uri,
              contentDescription = "待发送图片",
              modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
              contentScale = ContentScale.Crop,
            )
            Surface(
              modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp),
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surface,
              shadowElevation = 0.dp,
            ) {
              IconButton(onClick = { imageUri = null }, modifier = Modifier.size(26.dp)) {
                Icon(
                  Icons.Default.Close,
                  contentDescription = "移除图片",
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            modifier = Modifier.weight(1f).heightIn(min = 54.dp, max = editorMaxHeight),
            shape = RoundedCornerShape(27.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
          ) {
            CommentTextEditor(
              state = editorState,
              placeholder = placeholder,
              emoteMarkers = markerSnapshot.markerToEmote,
              focusRequester = focusRequester,
              onFocused = { showTools = false },
              modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp, max = editorMaxHeight),
              // 到达可见行数上限后 BasicTextField 会自行内部滚动。
              maxLines = editorMaxLines,
            )
          }
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .92f),
          ) {
            IconButton(
              onClick = {
                val opening = !showTools
                showTools = opening
                if (opening) {
                  focusManager.clearFocus()
                  keyboardController?.hide()
                }
              },
              modifier = Modifier.size(54.dp),
            ) {
              Icon(Icons.Default.AddCircle, contentDescription = "表情和图片")
            }
          }
          Surface(
            shape = CircleShape,
            color =
              if (canSend || sending) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surfaceVariant,
          ) {
            IconButton(
              enabled = canSend,
              onClick = {
                submissionLocked = true
                onSend(markerSnapshot.decode(text).trim(), imageUri)
              },
              modifier = Modifier.size(54.dp),
            ) {
              if (sending) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
              } else {
                Icon(
                  Icons.AutoMirrored.Filled.Send,
                  contentDescription = "发送",
                  tint =
                    if (canSend) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun MessageComposer(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  emotePackages: List<BiliEmotePackage>,
  onSend: () -> Unit,
  modifier: Modifier = Modifier,
  onFocused: () -> Unit = {},
) {
  var showEmotes by remember { mutableStateOf(false) }
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = { Text(placeholder) },
      maxLines = 3,
      singleLine = false,
      supportingText = null,
      modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) onFocused() },
      trailingIcon = {
        Box {
          IconButton(onClick = { showEmotes = true }) {
            Icon(Icons.Default.AddCircle, contentDescription = "表情和工具")
          }
          DropdownMenu(
            expanded = showEmotes,
            onDismissRequest = { showEmotes = false },
          ) {
            Column(
              Modifier.width(360.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            ) {
              emotePackages.forEach { pack ->
                Text(
                  pack.name,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                  style = MaterialTheme.typography.labelLarge,
                )
                pack.emotes.chunked(7).forEach { rowEmotes ->
                  Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    rowEmotes.forEach { emote ->
                      AsyncImage(
                        model = emote.url,
                        contentDescription = emote.text,
                        modifier =
                          Modifier.size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                              onValueChange(value + emote.text)
                              showEmotes = false
                            }
                            .padding(4.dp),
                      )
                    }
                  }
                }
              }
            }
          }
        }
      },
    )
    Button(
      onClick = onSend,
      enabled = value.isNotBlank(),
      modifier = Modifier.padding(start = 10.dp),
    ) {
      Text("发送")
    }
  }
}

private fun formatMessageTime(timestampSeconds: Long): String {
  if (timestampSeconds <= 0L) return ""
  val dateTime = Instant.ofEpochSecond(timestampSeconds).atZone(ZoneId.systemDefault())
  val now = Instant.now().atZone(ZoneId.systemDefault())
  return if (dateTime.toLocalDate() == now.toLocalDate()) {
    dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
  } else {
    dateTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
  }
}

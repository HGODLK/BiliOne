package dev.openbili.webdemo.my

/**
 * 互动消息面板：点赞消息与"回复/@我的"列表。
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

/** 互动消息面板组合体。 */
@Composable
internal fun InteractionMessagePane(
  state: MyUiState,
  onSelect: (Long) -> Unit,
  onReply: (String) -> Unit,
  onTarget: (AccountMessage, Rect) -> Unit,
  onProfile: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onLoadMore: () -> Unit,
  hiddenTargetMessageId: Long?,
  hiddenCommentAvatarRpid: Long?,
  emotePackages: List<BiliEmotePackage>,
  hasMore: Boolean,
  allowReply: Boolean,
  emptyText: String,
) {
  val listState = rememberLazyListState()
  var replyingMessageId by remember { mutableStateOf<Long?>(null) }
  val shouldLoadMore by
    remember(state.messages.size, hasMore) {
      derivedStateOf {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        lastVisible >= state.messages.lastIndex - 3 && hasMore
      }
    }
  LaunchedEffect(shouldLoadMore, state.messages.size) {
    if (shouldLoadMore) onLoadMore()
  }
  if (state.messages.isEmpty() && !state.loading) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(
        emptyText,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    return
  }
  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize().imePadding(),
    contentPadding = PaddingValues(bottom = NavigationCardBottomClearance),
    verticalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    items(state.messages, key = { it.id }) { message ->
      InteractionMessageCard(
        message = message,
        replying = replyingMessageId == message.id,
        onToggleReply = {
          onSelect(message.id)
          replyingMessageId = message.id.takeUnless { replyingMessageId == message.id }
        },
        onReply = { text ->
          onSelect(message.id)
          onReply(text)
          replyingMessageId = null
        },
        onTarget = { bounds -> onTarget(message, bounds) },
        onProfile = onProfile,
        targetVisible = hiddenTargetMessageId != message.id,
        avatarVisible = hiddenCommentAvatarRpid != message.id,
        emotePackages = emotePackages,
        replyEnabled = allowReply,
      )
    }
    if (state.messagesLoadingMore) {
      item(key = "interaction_loading_more") {
        Box(
          Modifier.fillMaxWidth().padding(vertical = 18.dp),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
      }
    } else if (state.messages.isNotEmpty() && !hasMore) {
      item(key = "interaction_end") {
        Text(
          "已经加载全部可查询记录",
          modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.labelMedium,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun InteractionMessageCard(
  message: AccountMessage,
  replying: Boolean,
  onToggleReply: () -> Unit,
  onReply: (String) -> Unit,
  onTarget: (Rect) -> Unit,
  onProfile: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  targetVisible: Boolean,
  avatarVisible: Boolean,
  emotePackages: List<BiliEmotePackage>,
  replyEnabled: Boolean,
) {
  var replyText by remember(message.id) { mutableStateOf("") }
  val comment = remember(message) { message.toInteractionCommentItem() }
  val emotes = remember(emotePackages) { emotePackages.emoteCatalog() }
  val replyBringIntoView = remember(message.id) { BringIntoViewRequester() }
  val replyScope = rememberCoroutineScope()
  val density = LocalDensity.current
  val imeBottom = WindowInsets.ime.getBottom(density)
  LaunchedEffect(replying, imeBottom) {
    if (replying) replyBringIntoView.bringIntoView()
  }
  Column(Modifier.fillMaxWidth()) {
    CommentRow(
      comment = comment,
      showEmotes = true,
      emoteCatalog = emotes,
      showLocation = false,
      onLike = {},
      uploaderMid = 0L,
      onProfileClick = onProfile,
      onImagePreview = { _, _ -> },
      onReplies = { _, _ -> },
      onReply = { onToggleReply() },
      replyEnabled = replyEnabled,
      avatarVisible = avatarVisible,
      onLinkedVideoClick = { _, bounds -> onTarget(bounds) },
      onLinkedArticleClick = { _, bounds -> onTarget(bounds) },
      headerLabel = message.title,
      quotedContent = message.sourceContent,
      linkedMediaVisible = targetVisible,
      linkedArticleCompactHeight = 82.dp,
      renderLinkedMediaCards = true,
    )
    AnimatedVisibility(visible = replying && replyEnabled) {
      MessageComposer(
        value = replyText,
        onValueChange = { replyText = it },
        placeholder = "回复 ${message.userName}",
        emotePackages = emotePackages,
        onSend = {
          val text = replyText.trim()
          if (text.isNotEmpty()) {
            onReply(text)
            replyText = ""
          }
        },
        modifier =
          Modifier.fillMaxWidth()
            .bringIntoViewRequester(replyBringIntoView)
            .padding(start = 58.dp, end = 12.dp, bottom = 10.dp),
        onFocused = {
          replyScope.launch { replyBringIntoView.bringIntoView() }
        },
      )
    }
  }
}

private fun AccountMessage.toInteractionCommentItem(): CommentItem {
  val targetUrl =
    linkUrl.takeIf {
      it.contains("/video/", ignoreCase = true) ||
        it.contains("/read/", ignoreCase = true) ||
        it.contains("/opus/", ignoreCase = true) ||
        it.startsWith("bilibili://video", ignoreCase = true) ||
        it.startsWith("bilibili://article", ignoreCase = true)
    }
      ?: when {
        targetKind == MessageTargetKind.VIDEO && oid > 0L -> "bilibili://video/$oid"
        targetKind == MessageTargetKind.ARTICLE && oid > 0L ->
          "https://www.bilibili.com/read/cv$oid"
        else -> ""
      }
  return CommentItem(
    rpid = id,
    mid = userMid,
    name = userName,
    face = userFace,
    content =
      listOf(content.ifBlank { "回复了你的内容" }, targetUrl)
        .filter(String::isNotBlank)
        .joinToString("\n"),
    likeCount = 0L,
    replyCount = 0L,
    ctime = time,
    level = userLevel,
    vipActive = userVipActive,
    vipLabel = userVipLabel,
  )
}

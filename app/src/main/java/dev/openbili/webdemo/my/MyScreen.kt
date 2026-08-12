package dev.openbili.webdemo.my

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.openbili.webdemo.BuildConfig
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliApi
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
import dev.openbili.webdemo.settings.DeviceMediaCapabilities
import dev.openbili.webdemo.settings.PreferredResolutionMode
import dev.openbili.webdemo.settings.SimAvailability
import dev.openbili.webdemo.settings.ThemeAccent
import dev.openbili.webdemo.settings.ThemeMode
import dev.openbili.webdemo.settings.canSelectPreferredResolution
import dev.openbili.webdemo.settings.detectSimAvailability
import dev.openbili.webdemo.ui.PressableVideoCard
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import dev.openbili.webdemo.ui.OfficialVerificationIcon
import dev.openbili.webdemo.ui.OfficialVerificationIconSize
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.RootAccountHeader
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.VideoCardReveal
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.video.CommentImagePreviewOverlay
import dev.openbili.webdemo.video.CommentImagePreviewSession
import dev.openbili.webdemo.video.BiliRichText
import dev.openbili.webdemo.video.CommentEmoteMarkerRegistry
import dev.openbili.webdemo.video.CommentTextEditor
import dev.openbili.webdemo.video.CommentToolPage
import dev.openbili.webdemo.video.CommentToolPanel
import dev.openbili.webdemo.video.CommentAvatarPaletteCache
import dev.openbili.webdemo.video.CommentProfileAnchor
import dev.openbili.webdemo.video.CommentRow
import dev.openbili.webdemo.video.extractAvatarDominantColors
import dev.openbili.webdemo.video.readableCommentCardColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MyScreen(
  user: UserInfo,
  state: MyUiState,
  watchLaterState: WatchLaterUiState,
  onSection: (MySection) -> Unit,
  onFolder: (Long) -> Unit,
  onVideo: (FeedItem, Rect) -> Unit,
  onBangumi: (SpaceContentCard, FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  onArticle: (ArticleItem, Rect) -> Unit,
  onArticleBounds: (ArticleItem, Rect) -> Unit,
  onLive: (LiveSearchRoom, Rect) -> Unit,
  onHistoryFilter: (HistoryFilter) -> Unit,
  onLoadMoreHistory: () -> Unit,
  onFavoriteQuery: (String) -> Unit,
  onLoadMoreFavorites: () -> Unit,
  onRemoveFavorite: (FeedItem) -> Unit,
  onCopyFavorite: (FeedItem, Long) -> Unit,
  onMoveFavorite: (FeedItem, Long) -> Unit,
  onCreateFavoriteFolder: (String, Boolean) -> Unit,
  onEditFavoriteFolder: (FavoriteFolder, String, Boolean) -> Unit,
  onDeleteFavoriteFolder: (FavoriteFolder) -> Unit,
  hiddenCoverItemId: String? = null,
  cachedVideosBackHandlingEnabled: Boolean = true,
  hiddenArticleItemId: String? = null,
  hiddenInteractionTargetMessageId: Long? = null,
  onProfile: (FollowingUser, Rect) -> Unit,
  onUnfollow: (FollowingUser) -> Unit,
  onFollowingQuery: (String) -> Unit,
  onFollowingGroup: (Long?) -> Unit,
  onFollowingOrder: (FollowingOrder) -> Unit,
  onLoadMoreFollowings: () -> Unit,
  onRefresh: () -> Unit,
  onWatchLaterRefresh: () -> Unit,
  onLogin: () -> Unit,
  profileIpAuthorized: Boolean,
  onAuthorizeProfileIp: () -> Unit,
  onAccountClick: (Rect) -> Unit,
  onMessage: (Long) -> Unit,
  onLoadMorePrivateSessions: () -> Unit,
  onLoadMorePrivateMessageHistory: () -> Unit,
  onReplyMessage: (String) -> Unit,
  onReplyPrivateMessage: (String, Uri?) -> Unit,
  onWithdrawPrivateMessage: (AccountMessage) -> Unit,
  onDeletePrivateMessage: (AccountMessage) -> Unit,
  onPrivateMessageProfile: (Long, String, String, Rect) -> Unit,
  onPrivateMessageTarget: (AccountMessage, Rect) -> Unit,
  onInteractionTarget: (AccountMessage, Rect) -> Unit,
  onInteractionProfile: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onLoadMoreInteractions: () -> Unit,
  onLoadMoreLikes: () -> Unit,
  onErrorConsumed: () -> Unit,
  onWatchLaterErrorConsumed: () -> Unit,
  hiddenInteractionCommentAvatarRpid: Long? = null,
  settings: AppSettings,
  onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
  onLogout: () -> Unit,
) {
  var showLogoutDialog by remember { mutableStateOf(false) }
  val visibleError =
    if (state.section == MySection.WATCH_LATER) watchLaterState.error else state.error
  val contentLoading =
    if (state.section == MySection.WATCH_LATER) watchLaterState.loading else state.loading
  LaunchedEffect(visibleError, state.section) {
    if (!visibleError.isNullOrBlank()) {
      delay(4_000L)
      if (state.section == MySection.WATCH_LATER) onWatchLaterErrorConsumed() else onErrorConsumed()
    }
  }
  val immerseBehindBottomCapsule =
    when (state.section) {
      MySection.FAVORITES,
      MySection.HISTORY,
      MySection.WATCH_LATER,
      MySection.FOLLOWING,
      MySection.INTERACTIONS,
      MySection.LIKES,
      MySection.CACHED_VIDEOS -> true
      MySection.MESSAGES -> true
      MySection.SETTINGS -> false
    }
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
    contentColor = MaterialTheme.colorScheme.onBackground,
  ) {
    Row(Modifier.fillMaxSize()) {
      Column(Modifier.width(250.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
        RootAccountHeader(
          user = user,
          onClick = { bounds -> if (user.isLogin) onAccountClick(bounds) else onLogin() },
          modifier = Modifier.fillMaxWidth(),
          containerColor = MaterialTheme.colorScheme.surface,
        )
        Column(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          MySection.entries.forEach { section ->
            val selected = state.section == section
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                  )
                  .clickable { onSection(section) },
            ) {
              Text(
                section.label,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
                color =
                  if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSurface,
              )
              if (user.isLogin && state.hasUnread(section)) {
                Box(
                  Modifier.align(Alignment.TopEnd)
                    .offset(x = (-12).dp, y = 10.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                )
              }
            }
          }
          if (user.isLogin) {
            Text(
              "退出登录",
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { showLogoutDialog = true }
                  .padding(horizontal = 18.dp, vertical = 15.dp),
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
      Box(
        Modifier.weight(1f)
          .fillMaxHeight()
          .windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End)
          )
          .padding(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom =
              if (state.section == MySection.MESSAGES) 14.dp
              else if (immerseBehindBottomCapsule) 0.dp
              else 20.dp,
          )
      ) {
        if (!user.isLogin && state.section != MySection.CACHED_VIDEOS) {
          Text(
            "登录后查看账号内容",
            modifier = Modifier.align(Alignment.Center).clickable(onClick = onLogin),
            color = MaterialTheme.colorScheme.primary,
          )
        } else {
          PullRefreshContainer(
            refreshing = contentLoading,
            onRefresh =
              if (state.section == MySection.WATCH_LATER) onWatchLaterRefresh else onRefresh,
            enabled =
              state.section != MySection.SETTINGS &&
                state.section != MySection.MESSAGES &&
                state.section != MySection.CACHED_VIDEOS,
            modifier = Modifier.fillMaxSize(),
          ) {
            Crossfade(
              targetState = state.section,
              animationSpec = tween(180),
              label = "mySectionFade",
            ) { section ->
              when (section) {
                MySection.FAVORITES ->
                  VideoPanel(
                    state = state,
                    onFolder = onFolder,
                    onVideo = onVideo,
                    onVideoLongClick = onVideoLongClick,
                    hiddenCoverItemId = hiddenCoverItemId,
                    onFavoriteQuery = onFavoriteQuery,
                    onLoadMoreFavorites = onLoadMoreFavorites,
                    onRemoveFavorite = onRemoveFavorite,
                    onCopyFavorite = onCopyFavorite,
                    onMoveFavorite = onMoveFavorite,
                    onCreateFolder = onCreateFavoriteFolder,
                    onEditFolder = onEditFavoriteFolder,
                    onDeleteFolder = onDeleteFavoriteFolder,
                  )
                MySection.HISTORY ->
                  HistoryPanel(
                    state = state,
                    onVideo = onVideo,
                    onBangumi = onBangumi,
                    onVideoLongClick = onVideoLongClick,
                    onArticle = onArticle,
                    onArticleBounds = onArticleBounds,
                    onLive = onLive,
                    onFilter = onHistoryFilter,
                    onLoadMore = onLoadMoreHistory,
                    hiddenCoverItemId = hiddenCoverItemId,
                    hiddenArticleItemId = hiddenArticleItemId,
                  )
                MySection.WATCH_LATER ->
                  WatchLaterPanel(
                    state = watchLaterState,
                    onVideo = onVideo,
                    onVideoLongClick = onVideoLongClick,
                    hiddenCoverItemId = hiddenCoverItemId,
                  )
                MySection.FOLLOWING ->
                  FollowingPanel(
                    state = state,
                    onProfile = onProfile,
                    onUnfollow = onUnfollow,
                    onQuery = onFollowingQuery,
                    onGroup = onFollowingGroup,
                    onOrder = onFollowingOrder,
                    onLoadMore = onLoadMoreFollowings,
                  )
                MySection.MESSAGES ->
                  NativeMessagePane(
                    state = state,
                    onMessage = onMessage,
                    onLoadMore = onLoadMorePrivateSessions,
                    onLoadMoreHistory = onLoadMorePrivateMessageHistory,
                    onReply = onReplyMessage,
                    onReplyPrivate = onReplyPrivateMessage,
                    onWithdraw = onWithdrawPrivateMessage,
                    onDelete = onDeletePrivateMessage,
                    onProfile = onPrivateMessageProfile,
                    onTarget = onPrivateMessageTarget,
                    hiddenTargetMessageId = hiddenInteractionTargetMessageId,
                  )
                MySection.INTERACTIONS ->
                  InteractionMessagePane(
                    state = state,
                    onSelect = onMessage,
                    onReply = onReplyMessage,
                    onTarget = onInteractionTarget,
                    onProfile = onInteractionProfile,
                    onLoadMore = onLoadMoreInteractions,
                    emotePackages = state.messageEmotePackages,
                    hiddenTargetMessageId = hiddenInteractionTargetMessageId,
                    hiddenCommentAvatarRpid = hiddenInteractionCommentAvatarRpid,
                    hasMore = state.messageReplyHasMore || state.messageAtHasMore,
                    allowReply = true,
                    emptyText = "(。・ω・。) 暂无回复或@消息",
                  )
                MySection.LIKES ->
                  InteractionMessagePane(
                    state = state,
                    onSelect = onMessage,
                    onReply = onReplyMessage,
                    onTarget = onInteractionTarget,
                    onProfile = onInteractionProfile,
                    onLoadMore = onLoadMoreLikes,
                    emotePackages = state.messageEmotePackages,
                    hiddenTargetMessageId = hiddenInteractionTargetMessageId,
                    hiddenCommentAvatarRpid = hiddenInteractionCommentAvatarRpid,
                    hasMore = state.messageLikeHasMore,
                    allowReply = false,
                    emptyText = "(。・ω・。) 暂无点赞消息",
                  )
                MySection.CACHED_VIDEOS ->
                  CachedVideosPane(
                    user = user,
                    onVideo = onVideo,
                    onBangumi = onBangumi,
                    hiddenCoverItemId = hiddenCoverItemId,
                    columns = settings.homeGridColumns,
                    backHandlingEnabled = cachedVideosBackHandlingEnabled,
                  )
                MySection.SETTINGS ->
                  SettingsPane(
                    settings = settings,
                    favoriteFolders = state.folders,
                    favoriteFoldersLoading = state.loading,
                    vipActive = user.vipActive,
                    profileIpAuthorized = profileIpAuthorized,
                    onAuthorizeProfileIp = onAuthorizeProfileIp,
                    onChange = onSettingsChange,
                  )
              }
            }
          }
        }
        if (contentLoading)
          CircularProgressIndicator(
            Modifier.size(28.dp).align(Alignment.Center),
            strokeWidth = 2.dp,
          )
        if (state.section != MySection.MESSAGES)
          visibleError?.let { message ->
            Surface(
              modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.errorContainer,
              shadowElevation = 0.dp,
            ) {
              Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
              )
            }
          }
      }
    }
  }
  if (showLogoutDialog) {
    AlertDialog(
      onDismissRequest = { showLogoutDialog = false },
      title = { Text("真的要退出登录吗？(｡•́︿•̀｡)") },
      text = { Text("退出后，收藏、历史和消息会暂时藏起来，下次还可以扫码回来喔～") },
      dismissButton = {
        TextButton(onClick = { showLogoutDialog = false }) { Text("再待一会 (´▽｀)") }
      },
      confirmButton = {
        TextButton(
          onClick = {
            showLogoutDialog = false
            onLogout()
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("狠心退出 (╥﹏╥)")
        }
      },
    )
  }
}

@Composable
private fun FollowingPanel(
  state: MyUiState,
  onProfile: (FollowingUser, Rect) -> Unit,
  onUnfollow: (FollowingUser) -> Unit,
  onQuery: (String) -> Unit,
  onGroup: (Long?) -> Unit,
  onOrder: (FollowingOrder) -> Unit,
  onLoadMore: () -> Unit,
) {
  val gridState = rememberLazyGridState()
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(gridState, columns = 2)
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    LazyRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item(key = "all_followings") {
        FilterChip(
          selected = state.selectedFollowingGroupId == null,
          onClick = { onGroup(null) },
          label = { Text("全部关注  ${state.followingTotal}") },
        )
      }
      items(state.followingGroups, key = { it.id }) { group ->
        FilterChip(
          selected = state.selectedFollowingGroupId == group.id,
          onClick = { onGroup(group.id) },
          label = { Text("${group.name}  ${group.count}") },
        )
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FollowingOrder.entries.forEach { order ->
        FilterChip(
          selected = state.followingOrder == order,
          onClick = { onOrder(order) },
          label = { Text(order.label) },
        )
      }
      Box(Modifier.weight(1f))
      Icon(
        Icons.Default.Search,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedTextField(
        value = state.followingQuery,
        onValueChange = onQuery,
        modifier = Modifier.width(320.dp),
        singleLine = true,
        label = { Text("搜索关注") },
        placeholder = { Text("输入用户名") },
      )
    }
    CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = NavigationCardBottomClearance),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        itemsIndexed(state.followings, key = { _, person -> person.mid }) { index, person ->
          VideoCardReveal(
            index = index,
            batchKey = state.followings.firstOrNull()?.mid,
            itemKey = person.mid,
            animatedItemCount = 20,
          ) {
            FollowingUserCard(
              person = person,
              unfollowed = person.mid in state.unfollowedIds,
              onProfile = onProfile,
              onUnfollow = onUnfollow,
            )
          }
        }
        if (state.followingHasMore) {
          item(
            key =
              "following_load_more_${state.followingPage}_${state.followingQuery}_" +
                "${state.selectedFollowingGroupId}_${state.followingOrder}",
            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
          ) {
            LaunchedEffect(
              state.followingPage,
              state.followingQuery,
              state.selectedFollowingGroupId,
              state.followingOrder,
              imageLoadPolicy.mode,
            ) {
              if (imageLoadPolicy.mode != FeedImageLoadMode.PAUSED) onLoadMore()
            }
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FollowingUserCard(
  person: FollowingUser,
  unfollowed: Boolean,
  onProfile: (FollowingUser, Rect) -> Unit,
  onUnfollow: (FollowingUser) -> Unit,
) {
  val context = LocalContext.current
  val imageLoadPolicy = LocalFeedImageLoadPolicy.current
  val scope = rememberCoroutineScope()
  val surface = MaterialTheme.colorScheme.surfaceVariant
  val primaryContainer = MaterialTheme.colorScheme.primaryContainer
  val darkTheme = MaterialTheme.colorScheme.surface.luminance() < .5f
  var avatarBounds by remember(person.mid) { mutableStateOf(Rect.Zero) }
  var avatarColors by
    remember(person.face, darkTheme) {
      mutableStateOf(CommentAvatarPaletteCache.get(person.face).orEmpty())
    }
  val gradientColors =
    remember(avatarColors, surface, primaryContainer, darkTheme) {
      if (avatarColors.isEmpty()) {
        listOf(primaryContainer, surface)
      } else {
        avatarColors.take(2).map { readableCommentCardColor(it, surface, darkTheme) }
      }
    }
  val avatarPreviouslyLoaded =
    remember(person.face) { LoadedFeedImageRegistry.contains(person.face) }
  var avatarDisplayed by remember(person.face) { mutableStateOf(false) }
  val avatarRequestPermitted =
    avatarPreviouslyLoaded ||
      avatarDisplayed ||
      imageLoadPolicy.permits(person.mid.toString())
  val avatarAlpha by
    animateFloatAsState(
      targetValue = if (avatarDisplayed) 1f else 0f,
      animationSpec = tween(180),
      label = "followingAvatarAlpha",
    )
  val avatarRequest =
    remember(person.face, avatarRequestPermitted) {
      if (!avatarRequestPermitted) null
      else ImageRequest.Builder(context).data(person.face).size(96, 96).allowHardware(false).build()
    }
  val buttonTint by
    animateColorAsState(
      targetValue =
        if (unfollowed) Color(0xFFF06A94).copy(alpha = .22f) else Color.Black.copy(alpha = .2f),
      animationSpec = tween(180),
      label = "followingButtonTint",
    )
  Surface(
    modifier = Modifier.fillMaxWidth().height(138.dp),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    tonalElevation = 2.dp,
    shadowElevation = 0.dp,
  ) {
    Box {
      Box(Modifier.fillMaxSize()) {
        Box(
          Modifier.fillMaxSize()
            .background(Brush.horizontalGradient(gradientColors))
        )
        Box(
          Modifier.fillMaxSize()
            .background(
              Brush.horizontalGradient(
                listOf(Color.Black.copy(alpha = .32f), Color.Black.copy(alpha = .12f))
              )
            )
        )
      }
      Row(
        Modifier.fillMaxSize()
          .clickable { onProfile(person, avatarBounds) }
          .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AsyncImage(
          model = avatarRequest,
          contentDescription = person.name,
          modifier =
            Modifier.size(56.dp)
              .clip(CircleShape)
              .graphicsLayer { alpha = avatarAlpha }
              .onGloballyPositioned { avatarBounds = it.boundsInRoot() },
          contentScale = ContentScale.Crop,
          onSuccess = { result ->
            avatarDisplayed = true
            LoadedFeedImageRegistry.markLoaded(person.face)
            if (avatarColors.isEmpty()) {
              val bitmap = (result.result.image as? BitmapImage)?.bitmap ?: return@AsyncImage
              scope.launch {
                val extracted =
                  CommentAvatarPaletteCache.resolve(person.face) {
                    withContext(Dispatchers.Default) { extractAvatarDominantColors(bitmap) }
                  }
                if (extracted.isNotEmpty()) avatarColors = extracted
              }
            }
          },
        )
        Column(
          Modifier.padding(start = 12.dp).weight(1f),
          verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            OfficialVerificationIcon(
              verification = person.officialVerification,
              modifier = Modifier.size(OfficialVerificationIconSize),
            )
            Text(
              person.name,
              style = MaterialTheme.typography.titleSmall,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          BiliRichText(
            text = person.signature.ifBlank { "这个人很神秘，什么也没有写 (´･ω･`)" },
            emotes = emptyMap(),
            maxLines = 2,
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = .82f)),
          )
        }
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = buttonTint,
          contentColor = Color.White,
          shadowElevation = 0.dp,
        ) {
          Button(
            onClick = { onUnfollow(person) },
            modifier = Modifier.animateContentSize(animationSpec = tween(180)),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
              ),
            elevation =
              ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
              ),
          ) {
            Crossfade(
              targetState = unfollowed,
              animationSpec = tween(160),
              label = "followingToggleText",
            ) { undone ->
              Text(if (undone) "点错了T_T" else "取关", maxLines = 1)
            }
          }
        }
      }
    }
  }
}

private data class SettingsOption<T>(
  val value: T,
  val title: String,
  val description: String,
)

@Composable
private fun SettingsPane(
  settings: AppSettings,
  favoriteFolders: List<FavoriteFolder>,
  favoriteFoldersLoading: Boolean,
  vipActive: Boolean,
  profileIpAuthorized: Boolean,
  onAuthorizeProfileIp: () -> Unit,
  onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val mediaCapabilities =
    remember(context.applicationContext) {
      DeviceMediaCapabilities.detect(context.applicationContext)
    }
  var cacheSizeBytes by remember { mutableStateOf<Long?>(null) }
  var clearingCache by remember { mutableStateOf(false) }
  var showResetDialog by remember { mutableStateOf(false) }
  var showMusicFolderPicker by remember { mutableStateOf(false) }
  var simAvailability by remember(context.applicationContext) {
    mutableStateOf(detectSimAvailability(context))
  }
  val homeBackgroundPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        onChange { it.copy(homeBackgroundUri = uri.toString()) }
      }
    }
  val videoBackgroundPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        onChange { it.copy(videoBackgroundUri = uri.toString()) }
      }
    }

  LaunchedEffect(Unit) {
    cacheSizeBytes = withContext(Dispatchers.IO) { AppCacheManager.sizeBytes(context) }
  }
  DisposableEffect(lifecycleOwner, context.applicationContext) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        simAvailability = detectSimAvailability(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = { Text("恢复默认设置？") },
      text = { Text("主题、播放、手势和弹幕选项都会恢复默认值，缓存与登录状态不会清除。") },
      confirmButton = {
        TextButton(
          onClick = {
            showResetDialog = false
            onChange { AppSettings() }
          }
        ) {
          Text("恢复")
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetDialog = false }) { Text("取消") }
      },
    )
  }
  if (showMusicFolderPicker) {
    MusicFavoriteFolderPicker(
      folders = favoriteFolders,
      selectedFolderId = settings.musicFavoriteFolderId,
      loading = favoriteFoldersLoading,
      onDismiss = { showMusicFolderPicker = false },
      onSelected = { folderId ->
        showMusicFolderPicker = false
        onChange { it.copy(musicFavoriteFolderId = folderId) }
      },
    )
  }

  fun selectResolution(mode: PreferredResolutionMode, cellular: Boolean) {
    if (canSelectPreferredResolution(mode, vipActive)) {
      onChange { value ->
        if (cellular) value.copy(cellularPreferredResolutionMode = mode)
        else value.copy(preferredResolutionMode = mode)
      }
    } else {
      Toast.makeText(context, "只有大会员可以选择~", Toast.LENGTH_SHORT).show()
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = NavigationCardBottomClearance),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item { SettingsTitle("播放与网络") }
    item {
      PreferredResolutionSetting(
        title = "Wi-Fi 优先分辨率",
        selected = settings.preferredResolutionMode,
        onSelected = { selectResolution(it, false) },
      )
    }
    if (simAvailability != SimAvailability.ABSENT) {
      item {
        PreferredResolutionSetting(
          title = "移动网络优先分辨率",
          selected = settings.cellularPreferredResolutionMode,
          onSelected = { selectResolution(it, true) },
        )
      }
    }
    item {
      SettingsSwitch("播放页阻止休眠", "视频或番剧播放页可见时保持屏幕常亮", settings.keepScreenOn) {
        onChange { value -> value.copy(keepScreenOn = it) }
      }
    }
    item {
      SettingsSwitch(
        "离开应用时暂停",
        "关闭后可在后台继续听视频",
        settings.pauseWhenLeavingApp,
      ) {
        onChange { value -> value.copy(pauseWhenLeavingApp = it) }
      }
    }
    item {
      SettingsSwitch(
        "自动播放下一集",
        "视频结束后按设定倒计时继续播放，也可以手动立即播放",
        settings.autoPlayNext,
      ) {
        onChange { value -> value.copy(autoPlayNext = it) }
      }
    }
    if (settings.autoPlayNext) {
      item {
        SettingsSlider(
          title = "连播倒计时",
          valueText = "${settings.autoNextCountdownSeconds} 秒",
          value = settings.autoNextCountdownSeconds.toFloat(),
          range = 3f..10f,
          steps = 6,
        ) { next ->
          onChange { it.copy(autoNextCountdownSeconds = next.roundToInt()) }
        }
      }
    }
    item {
      SettingsSwitch(
        "默认开启字幕",
        "进入有字幕的新视频时自动选择首个字幕轨道，播放中仍可临时关闭",
        settings.defaultShowSubtitles,
      ) {
        onChange { value -> value.copy(defaultShowSubtitles = it) }
      }
    }
    item {
      SettingsSlider(
        title = "播放器控件隐藏时间",
        valueText = "${settings.controlsTimeoutSeconds} 秒",
        value = settings.controlsTimeoutSeconds.toFloat(),
        range = 2f..5f,
        steps = 2,
      ) { next ->
        onChange { it.copy(controlsTimeoutSeconds = next.roundToInt()) }
      }
    }
    item {
      SettingsSwitch(
        "高级音质",
        "自动启用当前视频可用的 Dolby 或 HiRes 音轨",
        settings.advancedAudioEnabled,
      ) {
        onChange { value -> value.copy(advancedAudioEnabled = it) }
      }
    }
    if (settings.advancedAudioEnabled) {
      item {
        AdvancedAudioPrioritySetting(settings.advancedAudioPriority) { priority ->
          onChange { value -> value.copy(advancedAudioPriority = priority) }
        }
      }
    }
    if (!mediaCapabilities.supportsDolbyVision) {
      item {
        SettingsSwitch(
          "解锁杜比视界",
          "当前设备未报告支持，仅建议用于兼容性测试",
          settings.unlockDolbyVision,
        ) {
          onChange { value -> value.copy(unlockDolbyVision = it) }
        }
      }
    }
    if (!mediaCapabilities.supportsDolbyAtmos) {
      item {
        SettingsSwitch(
          "解锁杜比全景声",
          "当前设备未报告支持，仅建议用于兼容性测试",
          settings.unlockDolbyAtmos,
        ) {
          onChange { value -> value.copy(unlockDolbyAtmos = it) }
        }
      }
    }
    if (!mediaCapabilities.supportsHiRes) {
      item {
        SettingsSwitch(
          "解锁 Hi-Res",
          "当前设备未报告支持 FLAC 高解析音频，仅建议用于兼容性测试",
          settings.unlockHiRes,
        ) {
          onChange { value -> value.copy(unlockHiRes = it) }
        }
      }
    }

    item { SettingsTitle("音乐播放器") }
    item {
      val selectedFolder =
        favoriteFolders.firstOrNull { it.id == settings.musicFavoriteFolderId }
      SettingsAction(
        title = "音乐播放器收藏夹",
        subtitle =
          when {
            settings.musicFavoriteFolderId <= 0L -> "默认按名称自动查找“音乐”收藏夹"
            selectedFolder != null ->
              "当前使用“${selectedFolder.title}” · ${selectedFolder.mediaCount} 个内容"
            favoriteFoldersLoading -> "正在读取个人收藏夹…"
            else -> "已选择的收藏夹不可用，请重新选择"
          },
        action = if (favoriteFoldersLoading) "加载中" else "选择",
        enabled = !favoriteFoldersLoading,
        onClick = { showMusicFolderPicker = true },
      )
    }

    item { SettingsTitle("播放器手势") }
    item {
      SettingsSwitch("左侧滑动调节亮度", "只调整当前播放窗口", settings.brightnessGesture) {
        onChange { value -> value.copy(brightnessGesture = it) }
      }
    }
    item {
      SettingsSwitch("右侧滑动调节音量", "调整系统媒体音量", settings.volumeGesture) {
        onChange { value -> value.copy(volumeGesture = it) }
      }
    }
    item {
      SettingsSwitch("横向滑动调整进度", "全屏时左右边缘保留系统手势安全区", settings.horizontalSeekGesture) {
        onChange { value -> value.copy(horizontalSeekGesture = it) }
      }
    }
    item {
      SettingsSwitch(
        "双指捏合切换全屏",
        "张开进入全屏，捏合退出全屏",
        settings.twoFingerFullscreenGesture,
      ) {
        onChange { value -> value.copy(twoFingerFullscreenGesture = it) }
      }
    }
    item {
      SettingsSwitch(
        "双指双击快进/快退",
        "双指双击播放器左侧快退 5 秒，右侧快进 5 秒",
        settings.twoFingerSeekGesture,
      ) {
        onChange { value -> value.copy(twoFingerSeekGesture = it) }
      }
    }

    item { SettingsTitle("搜索") }
    item {
      SettingsSwitch(
        "返回保留上次搜索内容",
        "关闭时退出搜索页会立即清空首页搜索框",
        settings.retainLastSearchQuery,
      ) {
        onChange { value -> value.copy(retainLastSearchQuery = it) }
      }
    }

    item { SettingsTitle("外观") }
    item {
      SettingsSlider(
        title = "首页推荐列数",
        valueText = "${settings.homeGridColumns} 列",
        value = settings.homeGridColumns.toFloat(),
        range = 3f..6f,
        steps = 2,
      ) { next ->
        onChange { it.copy(homeGridColumns = next.roundToInt().coerceIn(3, 6)) }
      }
    }
    item {
      SettingsSwitch(
        "播放页显示设备信息",
        "控制普通视频、番剧、影视和直播页右上角的时间、网络与电量",
        settings.showPlaybackDeviceStatus,
      ) {
        onChange { value -> value.copy(showPlaybackDeviceStatus = it) }
      }
    }
    item { SettingsTitle("页面背景") }
    item {
      BackgroundImageSetting(
        title = "首页背景图",
        selected = settings.homeBackgroundUri.isNotBlank(),
        onPick = { homeBackgroundPicker.launch(arrayOf("image/*")) },
        onClear = { onChange { it.copy(homeBackgroundUri = "") } },
      )
    }
    if (settings.homeBackgroundUri.isNotBlank()) {
      item {
        SettingsSwitch(
          "模糊首页背景图",
          "预先生成静态模糊图；开启后背景透明度不生效",
          settings.homeBackgroundBlur,
        ) { checked -> onChange { it.copy(homeBackgroundBlur = checked) } }
      }
      item {
        SettingsSwitch(
          "用于音乐播放页",
          "音乐页会使用无压暗的静态模糊版本",
          settings.useHomeBackgroundForMusic,
        ) { checked -> onChange { it.copy(useHomeBackgroundForMusic = checked) } }
      }
      if (!settings.homeBackgroundBlur) {
        item {
          SettingsSlider(
            title = "首页背景透明度",
            valueText = "${(settings.homeBackgroundTransparency * 100).roundToInt()}%",
            value = settings.homeBackgroundTransparency,
            range = 0f..1f,
            steps = 9,
          ) { next -> onChange { it.copy(homeBackgroundTransparency = next) } }
        }
      }
    }
    item {
      BackgroundImageSetting(
        title = "播放页背景图",
        selected = settings.videoBackgroundUri.isNotBlank(),
        onPick = { videoBackgroundPicker.launch(arrayOf("image/*")) },
        onClear = { onChange { it.copy(videoBackgroundUri = "") } },
      )
    }
    item {
      SettingsSwitch(
        "使用当前视频封面作为播放页背景",
        "默认开启；番剧和分 P 会跟随当前播放集。设置自定义播放页背景图后不生效",
        settings.useVideoCoverBackground,
      ) { checked -> onChange { it.copy(useVideoCoverBackground = checked) } }
    }
    if (settings.videoBackgroundUri.isNotBlank()) {
      item {
        SettingsSwitch(
          "模糊播放页背景图",
          "预先生成静态模糊图；开启后背景透明度不生效",
          settings.videoBackgroundBlur,
        ) { checked -> onChange { it.copy(videoBackgroundBlur = checked) } }
      }
      if (!settings.videoBackgroundBlur) {
        item {
          SettingsSlider(
            title = "播放页背景透明度",
            valueText = "${(settings.videoBackgroundTransparency * 100).roundToInt()}%",
            value = settings.videoBackgroundTransparency,
            range = 0f..1f,
            steps = 9,
          ) { next -> onChange { it.copy(videoBackgroundTransparency = next) } }
        }
      }
    }
    item {
      SettingsRadioGroup(
        title = "主题",
        selected = settings.themeMode,
        options =
          ThemeMode.entries.map { mode ->
            SettingsOption(mode, mode.title, mode.description)
          },
      ) { mode ->
        onChange { it.copy(themeMode = mode) }
      }
    }
    item {
      SettingsRadioGroup(
        title = "主题色",
        selected = settings.themeAccent,
        options =
          ThemeAccent.entries.map { accent ->
            SettingsOption(accent, accent.title, accent.description)
          },
      ) { accent ->
        onChange { it.copy(themeAccent = accent) }
      }
    }
    item {
      SettingsSwitch(
        "减少动态效果",
        "缩短或关闭页面切换、共享元素和播放器动效",
        settings.reduceMotion,
      ) {
        onChange { value -> value.copy(reduceMotion = it) }
      }
    }
    item {
      SettingsSwitch(
        "实时毛玻璃",
        "关闭后改用不透明主题表面，降低合成与模糊开销",
        settings.glassEffects,
      ) {
        onChange { value -> value.copy(glassEffects = it) }
      }
    }
    item {
      SettingsSwitch(
        "限制加载速度",
        "打开后会在快速滑动时分批加载封面、头像和卡片渐变，可以减少掉帧，但内容显示会稍晚",
        settings.limitImageLoadingSpeed,
      ) {
        onChange { value -> value.copy(limitImageLoadingSpeed = it) }
      }
    }
    item {
      SettingsSlider(
        title = "全屏视频背景亮度",
        valueText =
          if (settings.fullscreenBackgroundBrightness <= .005f) "完全黑"
          else "${(settings.fullscreenBackgroundBrightness * 100).roundToInt()}%",
        value = settings.fullscreenBackgroundBrightness,
        range = 0f..1f,
        steps = 9,
      ) { next ->
        onChange { it.copy(fullscreenBackgroundBrightness = next) }
      }
    }
    item { SettingsTitle("评论与弹幕") }
    item {
      SettingsSwitch("显示评论 IP 属地", "仅显示接口公开返回的信息", settings.showCommentLocation) {
        onChange { value -> value.copy(showCommentLocation = it) }
      }
    }
    item {
      SettingsSwitch("显示评论表情", "关闭后评论正文仍显示表情代码", settings.showCommentEmotes) {
        onChange { value -> value.copy(showCommentEmotes = it) }
      }
    }
    item {
      SettingsSwitch(
        "默认开启弹幕",
        "进入新视频时自动显示弹幕，播放中仍可临时关闭",
        settings.defaultShowDanmaku,
      ) {
        onChange { value -> value.copy(defaultShowDanmaku = it) }
      }
    }
    item {
      SettingsSwitch(
        "弹幕智能屏蔽",
        "优先隐藏重复、低质量和高密度弹幕",
        settings.danmakuSmartBlocking,
      ) {
        onChange { value -> value.copy(danmakuSmartBlocking = it) }
      }
    }
    item {
      SettingsSlider(
        title = "弹幕显示区域",
        valueText = "${(settings.danmakuDisplayArea * 100).roundToInt()}%",
        value = settings.danmakuDisplayArea,
        range = .1f..1f,
        steps = 8,
      ) { next ->
        onChange { it.copy(danmakuDisplayArea = next) }
      }
    }
    item {
      SettingsSlider(
        title = "同屏弹幕密度",
        valueText = "${settings.danmakuDensity} 级",
        value = settings.danmakuDensity.toFloat(),
        range = 1f..5f,
        steps = 3,
      ) { next ->
        onChange { it.copy(danmakuDensity = next.roundToInt()) }
      }
    }
    item {
      SettingsSlider(
        title = "弹幕屏蔽等级",
        valueText = "${settings.danmakuBlockLevel} 级",
        value = settings.danmakuBlockLevel.toFloat(),
        range = 1f..5f,
        steps = 3,
      ) { next ->
        onChange { it.copy(danmakuBlockLevel = next.roundToInt()) }
      }
    }
    item {
      SettingsSlider(
        title = "弹幕不透明度",
        valueText = "${(settings.danmakuOpacity * 100).roundToInt()}%",
        value = settings.danmakuOpacity,
        range = .2f..1f,
        steps = 7,
      ) { next ->
        onChange { it.copy(danmakuOpacity = next) }
      }
    }
    item {
      SettingsSlider(
        title = "弹幕字号",
        valueText = "${(settings.danmakuFontScale * 100).roundToInt()}%",
        value = settings.danmakuFontScale,
        range = .7f..1.5f,
        steps = 7,
      ) { next ->
        onChange { it.copy(danmakuFontScale = next) }
      }
    }
    item {
      SettingsSlider(
        title = "弹幕速度",
        valueText = String.format(java.util.Locale.US, "%.1f×", settings.danmakuSpeed),
        value = settings.danmakuSpeed,
        range = .5f..2f,
        steps = 14,
      ) { next ->
        onChange { it.copy(danmakuSpeed = next) }
      }
    }

    item { SettingsTitle("存储与关于") }
    item {
      SettingsAction(
        title = "个人主页 IP 属地",
        subtitle =
          if (profileIpAuthorized) "已授权，可显示个人主页接口返回的公开 IP 属地"
          else "授权后可显示个人主页接口返回的公开 IP 属地",
        action = if (profileIpAuthorized) "已授权" else "去授权",
        enabled = !profileIpAuthorized,
        onClick = onAuthorizeProfileIp,
      )
    }
    item {
      SettingsAction(
        title = "播放与图片缓存",
        subtitle =
          when {
            clearingCache -> "正在清理可重新生成的缓存…"
            cacheSizeBytes == null -> "正在计算占用空间…"
            else -> "当前占用 ${AppCacheManager.formatSize(cacheSizeBytes!!)}"
          },
        action = if (clearingCache) "清理中" else "清理",
        enabled = !clearingCache,
      ) {
        clearingCache = true
        scope.launch {
          withContext(Dispatchers.IO) { AppCacheManager.clear(context) }
          cacheSizeBytes = withContext(Dispatchers.IO) { AppCacheManager.sizeBytes(context) }
          clearingCache = false
          Toast.makeText(context, "缓存已清理", Toast.LENGTH_SHORT).show()
        }
      }
    }
    item {
      BiliOneAboutCard()
    }
    item {
      SettingsAction(
        title = "恢复默认设置",
        subtitle = "保留登录状态和缓存，仅重置本页选项",
        action = "恢复",
      ) {
        showResetDialog = true
      }
    }
  }
}

@Composable
private fun MusicFavoriteFolderPicker(
  folders: List<FavoriteFolder>,
  selectedFolderId: Long,
  loading: Boolean,
  onDismiss: () -> Unit,
  onSelected: (Long) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("选择音乐播放器收藏夹") },
    text = {
      if (loading && folders.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          item(key = "default_music_folder") {
            SettingsRadioRow(
              selected = selectedFolderId <= 0L,
              title = "音乐（默认）",
              description = "每次进入时按名称精确查找“音乐”收藏夹；没有则提示创建",
              onClick = { onSelected(0L) },
            )
          }
          items(folders, key = FavoriteFolder::id) { folder ->
            SettingsRadioRow(
              selected = selectedFolderId == folder.id,
              title = folder.title,
              description =
                "${folder.mediaCount} 个内容 · ${if (folder.isPublic) "公开" else "私密"}",
              onClick = { onSelected(folder.id) },
            )
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

@Composable
private fun PreferredResolutionSetting(
  title: String,
  selected: PreferredResolutionMode,
  onSelected: (PreferredResolutionMode) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(Modifier.padding(vertical = 8.dp)) {
      Text(
        title,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleSmall,
      )
      PreferredResolutionMode.entries.forEach { mode ->
        SettingsRadioRow(
          selected = selected == mode,
          title = mode.title,
          description = mode.description,
          onClick = { onSelected(mode) },
        )
      }
    }
  }
}

@Composable
private fun <T> SettingsRadioGroup(
  title: String,
  selected: T,
  options: List<SettingsOption<T>>,
  onSelected: (T) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(Modifier.padding(vertical = 8.dp)) {
      Text(
        title,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleSmall,
      )
      options.forEach { option ->
        SettingsRadioRow(
          selected = selected == option.value,
          title = option.title,
          description = option.description,
          onClick = { onSelected(option.value) },
        )
      }
    }
  }
}

@Composable
private fun SettingsRadioRow(
  selected: Boolean,
  title: String,
  description: String,
  onClick: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(start = 10.dp, end = 18.dp, top = 7.dp, bottom = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = onClick)
    Column(Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color =
          if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
      )
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SettingsTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleLarge,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.padding(top = 6.dp),
  )
}

@Composable
private fun BackgroundImageSetting(
  title: String,
  selected: Boolean,
  onPick: () -> Unit,
  onClear: () -> Unit,
) {
  Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
          if (selected) "已选择；仅作为页面最底层背景" else "未选择，使用主题背景",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (selected) TextButton(onClick = onClear) { Text("清除") }
      Button(onClick = onPick) { Text(if (selected) "更换" else "选择") }
    }
  }
}

@Composable
private fun SettingsSwitch(
  title: String,
  subtitle: String,
  checked: Boolean,
  onChecked: (Boolean) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth().clickable { onChecked(!checked) },
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = checked, onCheckedChange = onChecked)
    }
  }
}

@Composable
private fun SettingsSlider(
  title: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  steps: Int,
  onChange: (Float) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
      Row(Modifier.fillMaxWidth()) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(valueText, color = MaterialTheme.colorScheme.primary)
      }
      Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
  }
}

@Composable
private fun SettingsAction(
  title: String,
  subtitle: String,
  action: String,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Row(
      modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TextButton(onClick = onClick, enabled = enabled) { Text(action) }
    }
  }
}

@Composable
private fun BiliOneAboutCard() {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      AsyncImage(
        model = "https://i1.hdslb.com/bfs/face/c0903076fb89022aef21a99503bd7e79a6774edf.jpg",
        contentDescription = null,
        modifier =
          Modifier.size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentScale = ContentScale.Crop,
      )
      Column(Modifier.weight(1f)) {
        Text("关于 BiliOne", style = MaterialTheme.typography.titleSmall)
        Text(
          "开发者 · ShuyunR",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          "版本 ${BuildConfig.VERSION_NAME.removeSuffix("-debugrelease")} · 缓存清理不会影响登录和设置",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun WatchLaterPanel(
  state: WatchLaterUiState,
  onVideo: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  hiddenCoverItemId: String?,
) {
  val gridState = rememberLazyGridState()
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(gridState)
  CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(3),
      state = gridState,
      modifier = Modifier.fillMaxSize(),
      contentPadding =
        PaddingValues(end = 136.dp, bottom = NavigationCardBottomClearance),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(
        key = "watch_later_title",
        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            "稍后再看",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Spacer(Modifier.weight(1f))
          if (state.loaded) {
            Text(
              "${state.items.size} 个视频",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }
      if (state.items.isEmpty() && !state.loading) {
        item(
          key = "watch_later_empty",
          span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
        ) {
          Box(
            Modifier.fillMaxWidth().padding(vertical = 56.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              if (state.error.isNullOrBlank()) "稍后再看的视频会出现在这里" else "稍后再看加载失败",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      itemsIndexed(
        items = state.items,
        key = { _, item -> item.id },
      ) { index, item ->
        var coverBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
        VideoCardReveal(
          index = index,
          batchKey = state.items.firstOrNull()?.id,
          itemKey = "watch-later-${item.id}",
        ) {
          PressableVideoCard(
            onClick = { onVideo(item, coverBounds) },
            onLongClick = { onVideoLongClick(item) },
          ) {
            MyVideoCardContent(
              item = item,
              loadKey = "watch-later-${item.id}",
              coverVisible = item.id != hiddenCoverItemId,
              onCoverBoundsChanged = { coverBounds = it },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HistoryPanel(
  state: MyUiState,
  onVideo: (FeedItem, Rect) -> Unit,
  onBangumi: (SpaceContentCard, FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  onArticle: (ArticleItem, Rect) -> Unit,
  onArticleBounds: (ArticleItem, Rect) -> Unit,
  onLive: (LiveSearchRoom, Rect) -> Unit,
  onFilter: (HistoryFilter) -> Unit,
  onLoadMore: () -> Unit,
  hiddenCoverItemId: String?,
  hiddenArticleItemId: String?,
) {
  val gridState = rememberLazyGridState()
  val scope = rememberCoroutineScope()
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(gridState)
  var searchVisible by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf("") }
  var preciseMode by remember { mutableStateOf(false) }
  var selectedMinute by remember { mutableStateOf(0) }
  var pendingTarget by remember { mutableStateOf<HistoryTimelineTarget?>(null) }
  val displayedItems =
    remember(state.historyItems, query) {
      val keyword = query.trim()
      if (keyword.isBlank()) state.historyItems
      else
        state.historyItems.filter { item ->
          when (item) {
            is HistoryCardItem.Video ->
              item.item.title.contains(keyword, ignoreCase = true) ||
                item.item.uploader.orEmpty().contains(keyword, ignoreCase = true)
            is HistoryCardItem.Bangumi ->
              item.item.title.contains(keyword, ignoreCase = true) ||
                item.mediaLabel.contains(keyword, ignoreCase = true)
            is HistoryCardItem.Article ->
              item.item.title.contains(keyword, ignoreCase = true) ||
                item.item.authorName.contains(keyword, ignoreCase = true) ||
                item.item.summary.contains(keyword, ignoreCase = true)
            is HistoryCardItem.Live ->
              item.room.title.contains(keyword, ignoreCase = true) ||
                item.room.uname.contains(keyword, ignoreCase = true) ||
                item.room.areaName.orEmpty().contains(keyword, ignoreCase = true)
          }
        }
    }
  val gridEntries = remember(displayedItems) { historyGridEntries(displayedItems) }
  val currentPeriod by
    remember(gridEntries, gridState) {
      derivedStateOf {
        gridEntries
          .getOrNull(gridState.firstVisibleItemIndex)
          ?.period
          ?: HistoryPeriod.TODAY
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
  LaunchedEffect(state.historyFilter) { gridState.scrollToItem(0) }
  LaunchedEffect(
    pendingTarget,
    displayedItems,
    state.historyHasMore,
    state.historyLoadingMore,
    state.loading,
  ) {
    val target = pendingTarget ?: return@LaunchedEffect
    val index = gridEntries.indexOfFirst { it is HistoryGridEntry.Section && it.period == target.period }
    if (index >= 0) {
      gridState.animateScrollToItem(index)
      pendingTarget = null
    } else if (state.historyHasMore && !state.historyLoadingMore && !state.loading) {
      onLoadMore()
    } else if (!state.historyHasMore) {
      pendingTarget = null
    }
  }
  LaunchedEffect(preciseMode, selectedMinute, currentPeriod, gridEntries) {
    if (!preciseMode || currentPeriod == HistoryPeriod.EARLIER) return@LaunchedEffect
    val index =
      gridEntries.withIndex()
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
    }
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
        onValueChange = { query = it.take(60) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        singleLine = true,
        label = { Text("搜索历史记录") },
        placeholder = { Text("输入标题或作者") },
      )
    }
    Box(Modifier.weight(1f)) {
      CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
        LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          state = gridState,
          modifier = Modifier.fillMaxSize(),
          contentPadding =
            PaddingValues(end = 136.dp, bottom = NavigationCardBottomClearance),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (displayedItems.isEmpty() && !state.loading) {
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
                    query.isNotBlank() -> "没有找到相关历史"
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
                if (entry is HistoryGridEntry.Section) maxLineSpan else 1
              )
            },
          ) { index, entry ->
            when (entry) {
              is HistoryGridEntry.Section -> HistorySectionDivider(entry.period)
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
                      var coverBounds by
                        remember(history.room.roomId) { mutableStateOf(Rect.Zero) }
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
          if (state.historyHasMore && query.isBlank()) {
            item(
              key =
                "history_load_more_${state.historyFilter}_${state.historyCursor.max}_${state.historyCursor.viewAt}",
              span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
            ) {
              LaunchedEffect(state.historyFilter, state.historyCursor, imageLoadPolicy.mode) {
                if (imageLoadPolicy.mode != FeedImageLoadMode.PAUSED) onLoadMore()
              }
              Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
              }
            }
          }
        }
      }
      if (query.isBlank()) {
        HistoryTimeline(
          selected = currentPeriod,
          onPeriod = { period -> pendingTarget = HistoryTimelineTarget(period) },
          preciseMode = preciseMode,
          selectedMinute = selectedMinute,
          onPreciseModeChange = { enabled ->
            if (enabled && currentPeriod != HistoryPeriod.EARLIER) {
              val visibleHistory =
                gridEntries
                  .getOrNull(gridState.firstVisibleItemIndex)
                  .let { it as? HistoryGridEntry.Card }
                  ?.history
                  ?: gridEntries
                    .drop(gridState.firstVisibleItemIndex)
                    .firstNotNullOfOrNull { (it as? HistoryGridEntry.Card)?.history }
              selectedMinute =
                (visibleHistory?.viewAt?.let(::historyMinuteOfDay) ?: 0)
                  .coerceIn(0, preciseMaxMinute)
            }
            preciseMode = enabled && currentPeriod != HistoryPeriod.EARLIER
          },
          onMinuteChange = { selectedMinute = it.coerceIn(0, preciseMaxMinute) },
          maxMinute = preciseMaxMinute,
          modifier =
            Modifier.align(Alignment.CenterEnd)
              .padding(end = 10.dp)
              .fillMaxHeight()
              .width(88.dp),
        )
      }
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
            if (!searchVisible) query = ""
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

@Composable
private fun AdvancedAudioPrioritySetting(
  selected: AdvancedAudioPriority,
  onSelected: (AdvancedAudioPriority) -> Unit,
) {
  Column(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.surface)
      .padding(vertical = 8.dp)
  ) {
    Text(
      "高级音质优先级",
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
      style = MaterialTheme.typography.titleSmall,
    )
    AdvancedAudioPriority.entries.forEach { priority ->
      Row(
        Modifier.fillMaxWidth()
          .clickable { onSelected(priority) }
          .padding(start = 10.dp, end = 18.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(selected = selected == priority, onClick = { onSelected(priority) })
        Column(Modifier.weight(1f)) {
          Text(
            priority.title,
            style = MaterialTheme.typography.titleSmall,
            color =
              if (selected == priority) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurface,
          )
          Text(
            priority.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
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
    Box(
      Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
    )
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

private enum class HistoryPeriod(val label: String) {
  TODAY("今天"),
  YESTERDAY("昨天"),
  DAY_BEFORE("前天"),
  EARLIER("更早"),
}

private data class HistoryTimelineTarget(val period: HistoryPeriod)

private sealed interface HistoryGridEntry {
  val key: String
  val period: HistoryPeriod

  data class Section(override val period: HistoryPeriod) : HistoryGridEntry {
    override val key: String = "history_section_${period.name}"
  }

  data class Card(val history: HistoryCardItem, override val period: HistoryPeriod) :
    HistoryGridEntry {
    override val key: String = history.stableId
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

private fun historyGridEntries(items: List<HistoryCardItem>): List<HistoryGridEntry> = buildList {
  var previous: HistoryPeriod? = null
  items.forEach { history ->
    val period = historyPeriodFor(history.viewAt)
    if (period != previous) add(HistoryGridEntry.Section(period))
    add(HistoryGridEntry.Card(history, period))
    previous = period
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
                  Modifier.fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPeriod(period) },
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
  Box(
    modifier =
      modifier.onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
  ) {
    Canvas(Modifier.fillMaxSize()) {
      val right = size.width - 3.dp.toPx()
      val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
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
    val currentYDp =
      with(density) { ((1f - selectedMinute / 1440f) * heightPx).toDp() }
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
          .zIndex(1f),
    )
  }
}

@Composable
private fun VideoPanel(
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

@Composable
private fun MyVideoCardContent(
  item: FeedItem,
  loadKey: String = item.id,
  coverVisible: Boolean,
  onCoverBoundsChanged: (Rect) -> Unit,
  historyLabel: String? = null,
  mediaBadge: String? = null,
) {
  VideoCardGradient(coverUrl = item.coverUrl, loadKey = loadKey) {
    Column {
      Box(
        Modifier.onGloballyPositioned { onCoverBoundsChanged(it.boundsInRoot()) }
          .graphicsLayer { alpha = if (coverVisible) 1f else 0f }
      ) {
        CoverImage(
          coverUrl = item.coverUrl,
          modifier = Modifier.fillMaxWidth(),
          shape = VideoShapeTokens.Player,
          loadKey = loadKey,
        )
      }
      Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Text(
          item.title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
          maxLines = 2,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
          modifier = Modifier.height(48.dp),
        )
        BiliRichText(
          text = item.description.ifBlank { "暂无简介" },
          emotes = emptyMap(),
          style =
            MaterialTheme.typography.bodySmall.copy(
              color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
          maxLines = 2,
          modifier = Modifier.height(36.dp),
        )
        Row(
          modifier = Modifier.fillMaxWidth().height(18.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          mediaBadge?.takeIf(String::isNotBlank)?.let { badge ->
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .82f),
            ) {
              Text(
                badge,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
              )
            }
          }
          if (!mediaBadge.isNullOrBlank() && !historyLabel.isNullOrBlank()) Spacer(Modifier.width(8.dp))
          historyLabel?.let { label ->
            Text(
              label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            item.uploader.orEmpty(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
          )
          item.duration?.takeIf(String::isNotBlank)?.let {
            Text(
              it,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun NativeMessagePane(
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
    }.collectLatest { lastVisible ->
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
        modifier = Modifier.width(310.dp)
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
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
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
      // Keep roughly the neighbouring ten compact bubbles warm in either direction. Messages
      // outside this pixel window remain lightweight models and are composed only when scrolled
      // towards, so long image/card conversations do not retain an entire rendered history.
      val historyState =
        rememberLazyListState(
          cacheWindow = LazyLayoutCacheWindow(ahead = 720.dp, behind = 720.dp)
        )
      var renderedHistoryIds by remember(selected.userMid) { mutableStateOf(emptySet<Long>()) }
      var initialHistoryPositioned by remember(selected.userMid) { mutableStateOf(false) }
      var lastObservedHistoryId by remember(selected.userMid) { mutableStateOf<Long?>(null) }
      var followConversationBottom by remember(selected.userMid) { mutableStateOf(true) }
      // Do not treat our own scroll-to-bottom as the user taking control of the conversation.
      // Otherwise a received message can cancel its own follow animation halfway through.
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
              // Let the new bubble enter composition first.  A direct positioning scroll keeps
              // incoming messages aligned with sent messages rather than stopping one row short.
              kotlinx.coroutines.yield()
              historyState.scrollToItem(history.lastIndex)
              // AnimatedVisibility expands the received bubble after the first positioning
              // pass. Re-align once that enter animation has contributed its final height.
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
        // Samsung's stretch overscroll can keep canScrollBackward=true until the gesture reverses.
        // Prefetch as soon as one of the first three rows enters the viewport instead of waiting
        // for the platform to report the exact top edge.
        snapshotFlow {
            historyState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: Int.MAX_VALUE
          }
          .collectLatest { firstVisible ->
            if (
              firstVisible <= 2 &&
                state.privateHistoryHasMore &&
                !state.privateHistoryLoadingMore
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
          modifier = Modifier.clickable {
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
              // The list paints behind both floating surfaces; this inset only guarantees that
              // its last message can still be scrolled fully above them.
              contentPadding =
                PaddingValues(
                  top = 18.dp,
                  bottom = if (showSessionList) 250.dp else 126.dp,
                ),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (state.privateHistoryLoadingMore) {
                item(key = "private_history_loading") {
                  Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
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
                  !message.isOutgoing && renderedHistoryIds.isNotEmpty() && message.id !in renderedHistoryIds
                var messageVisible by remember(message.id) {
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
                // The standalone profile conversation has no root bottom capsule. Only the
                // "My messages" variant reserves that space when the keyboard is absent.
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
                onClick = { actionMessage = null; onWithdraw(message) },
              ) { Text(if (message.messageKey > 0L) "撤回" else "撤回（等待同步）") }
            }
            TextButton(onClick = { actionMessage = null; onDelete(message) }) { Text("删除") }
            TextButton(
              onClick = {
                clipboard.setText(AnnotatedString(message.content.ifBlank { message.linkUrl }))
                actionMessage = null
              }
            ) { Text("复制") }
            if (message.content.isNotBlank() || message.linkUrl.isNotBlank()) {
              TextButton(
                onClick = {
                  actionMessage = null
                  selectableMessage = message
                }
              ) { Text("选择文本") }
            }
          }
        },
        confirmButton = { TextButton(onClick = { actionMessage = null }) { Text("取消") } },
      )
    }
    selectableMessage?.let { message ->
      val selectableText = message.content.ifBlank { message.linkUrl }
      var selectionValue by remember(message.id, selectableText) {
        mutableStateOf(TextFieldValue(selectableText))
      }
      AlertDialog(
        onDismissRequest = { selectableMessage = null },
        title = { Text("选择要复制的文本") },
        text = {
          // A single visual line makes character hit testing depend only on horizontal position.
          // Vertical finger drift therefore cannot jump the selection to another wrapped line.
          BasicTextField(
            value = selectionValue,
            onValueChange = { selectionValue = it.copy(text = selectableText) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
              color = MaterialTheme.colorScheme.onSurface,
            ),
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
          val bvid = Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE)
            .find(message.linkUrl)?.value
          if (!bvid.isNullOrBlank()) {
            val resolved =
              withContext(Dispatchers.IO) {
                runCatching { BiliApi.getVideoInfo(bvid)?.title }.getOrNull()
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
          Column(Modifier.weight(1f).padding(start = if (message.coverUrl.isBlank()) 0.dp else 12.dp)) {
            if (message.targetKind != MessageTargetKind.UNKNOWN) {
              Text(
                if (message.targetKind == MessageTargetKind.ARTICLE) "专栏" else "视频",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Text(displayTitle.ifBlank { message.content }, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun InteractionMessagePane(
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

@Composable
private fun MessageListRow(message: AccountMessage, selected: Boolean, onClick: () -> Unit) {
  VideoCardGradient(
    coverUrl = message.userFace,
    loadKey = "private:${message.userMid}",
    // Keep every conversation row the same height so the two-line preview never changes the
    // rhythm of the people list.
    modifier = Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(14.dp)),
  ) {
    Box(
      Modifier.fillMaxWidth()
        .fillMaxHeight()
        .background(
          if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .58f)
          else Color.Transparent
        )
        .clickable(onClick = onClick),
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
    AnimatedVisibility(visible = showTools, enter = fadeIn(tween(180)), exit = fadeOut(tween(130))) {
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
      Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Icon(Icons.Default.Close, contentDescription = "移除图片", modifier = Modifier.size(16.dp))
              }
            }
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
              // BasicTextField keeps scrolling internally after reaching this visible line cap.
              maxLines = editorMaxLines,
            )
          }
          Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .92f)) {
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
                  tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun MessageComposer(
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
          IconButton(
            onClick = { showEmotes = true },
          ) {
            Icon(Icons.Default.AddCircle, contentDescription = "表情和工具")
          }
          DropdownMenu(
            expanded = showEmotes,
            onDismissRequest = { showEmotes = false },
          ) {
            Column(Modifier.width(360.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
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

private fun List<BiliEmotePackage>.emoteCatalog(): Map<String, BiliEmote> =
  flatMap(BiliEmotePackage::emotes).associateBy(BiliEmote::text)

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

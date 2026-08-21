package dev.openbili.webdemo.my

/** "我的"页根组合体：左侧分区栏目 + 右侧各子页面（收藏/历史/关注/消息/设置/缓存）。 */
import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.FollowingUser
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.RootAccountHeader
import dev.openbili.webdemo.ui.controlFocusOutline
import dev.openbili.webdemo.video.CommentProfileAnchor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "我的"页根组合体。 */
@Composable
fun MyScreen(
  user: UserInfo,
  state: MyUiState,
  watchLaterState: WatchLaterUiState,
  onSection: (MySection) -> Unit,
  onMarkSectionRead: (MySection) -> Unit = {},
  onFolder: (Long) -> Unit,
  onVideo: (FeedItem, Rect) -> Unit,
  onBangumi: (SpaceContentCard, FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  onArticle: (ArticleItem, Rect) -> Unit,
  onArticleBounds: (ArticleItem, Rect) -> Unit,
  onLive: (LiveSearchRoom, Rect) -> Unit,
  onHistoryFilter: (HistoryFilter) -> Unit,
  onLoadMoreHistory: (HistoryPeriod) -> Unit,
  onLoadHistoryThrough: (HistoryPeriod) -> Unit,
  onHistorySearch: (String) -> Unit,
  onLoadMoreHistorySearch: () -> Unit,
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
  rootPageVisible: Boolean = true,
  controlInputEnabled: Boolean = true,
  controlSecondLevelRequest: Int = 0,
  controlLevel: MyControlLevel = MyControlLevel.ROOT,
  onControlLevelChanged: (MyControlLevel) -> Unit = {},
) {
  val controlMode = LocalControlMode.current
  val visibleSections = remember(controlMode) { mySectionsForControlMode(controlMode) }
  val sectionFocusRequesters =
    remember(visibleSections) { visibleSections.associateWith { FocusRequester() } }
  val focusManager = LocalFocusManager.current
  val controlScope = rememberCoroutineScope()
  var handledControlSecondLevelRequest by remember { mutableStateOf(0) }
  var showLogoutDialog by remember { mutableStateOf(false) }
  var sectionToMarkRead by remember { mutableStateOf<MySection?>(null) }
  val visibleError =
    if (state.section == MySection.WATCH_LATER) watchLaterState.error else state.error
  val contentLoading =
    if (state.section == MySection.WATCH_LATER) watchLaterState.loading else state.loading
  val selectedVisibleSection =
    state.section.takeIf { it in visibleSections } ?: visibleSections.first()
  LaunchedEffect(controlMode, state.section, visibleSections) {
    if (controlMode && state.section !in visibleSections) onSection(selectedVisibleSection)
  }
  LaunchedEffect(
    controlSecondLevelRequest,
    controlMode,
    rootPageVisible,
    controlInputEnabled,
    controlLevel,
  ) {
    if (
      controlSecondLevelRequest > handledControlSecondLevelRequest &&
        controlMode &&
        rootPageVisible &&
        controlInputEnabled &&
        controlLevel == MyControlLevel.ROOT
    ) {
      handledControlSecondLevelRequest = controlSecondLevelRequest
      onControlLevelChanged(MyControlLevel.SECTIONS)
      withFrameNanos {}
      runCatching { sectionFocusRequesters.getValue(selectedVisibleSection).requestFocus() }
    }
  }
  LaunchedEffect(controlMode, rootPageVisible) {
    if (!controlMode || !rootPageVisible) onControlLevelChanged(MyControlLevel.ROOT)
  }
  BackHandler(
    enabled =
      controlMode && rootPageVisible && controlInputEnabled && controlLevel != MyControlLevel.ROOT
  ) {
    val target = myControlBackTarget(controlLevel)
    onControlLevelChanged(target)
    if (target == MyControlLevel.SECTIONS) {
      controlScope.launch {
        withFrameNanos {}
        runCatching { sectionFocusRequesters.getValue(selectedVisibleSection).requestFocus() }
      }
    }
  }
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
          focusEnabled = !controlMode || controlLevel == MyControlLevel.SECTIONS,
        )
        Column(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          visibleSections.forEach { section ->
            val selected = state.section == section
            val sectionShape = RoundedCornerShape(14.dp)
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .then(
                    if (controlMode) {
                      Modifier.focusRequester(sectionFocusRequesters.getValue(section))
                        .focusProperties {
                          canFocus = controlLevel == MyControlLevel.SECTIONS
                        }
                        .onPreviewKeyEvent { event ->
                          if (
                            controlLevel != MyControlLevel.SECTIONS ||
                              event.nativeKeyEvent.keyCode != AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                          ) {
                            return@onPreviewKeyEvent false
                          }
                          if (
                            event.type == KeyEventType.KeyDown &&
                              event.nativeKeyEvent.repeatCount == 0 &&
                              focusManager.moveFocus(FocusDirection.Right)
                          ) {
                            onControlLevelChanged(MyControlLevel.CONTENT)
                          }
                          true
                        }
                    } else Modifier
                  )
                  .clip(sectionShape)
                  .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                  )
                  .controlFocusOutline(
                    shape = sectionShape,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  .combinedClickable(
                    onClick = { onSection(section) },
                    onLongClick =
                      if (user.isLogin && state.hasUnread(section)) {
                        { sectionToMarkRead = section }
                      } else null,
                  )
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
                  .focusProperties {
                    canFocus = !controlMode || controlLevel == MyControlLevel.SECTIONS
                  }
                  .clip(RoundedCornerShape(14.dp))
                  .controlFocusOutline(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                  )
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
              else if (immerseBehindBottomCapsule) 0.dp else 20.dp,
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
                    onLoadThrough = onLoadHistoryThrough,
                    onSearch = onHistorySearch,
                    onLoadMoreSearch = onLoadMoreHistorySearch,
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
  sectionToMarkRead?.let { section ->
    AlertDialog(
      onDismissRequest = { sectionToMarkRead = null },
      title = { Text("标记为已读？") },
      text = { Text("将在所有设备上清除“${section.label}”的未读标记。") },
      dismissButton = {
        TextButton(onClick = { sectionToMarkRead = null }) { Text("取消") }
      },
      confirmButton = {
        TextButton(
          onClick = {
            sectionToMarkRead = null
            onMarkSectionRead(section)
          }
        ) { Text("已读") }
      },
    )
  }
}

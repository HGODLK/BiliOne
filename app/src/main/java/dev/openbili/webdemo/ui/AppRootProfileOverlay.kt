package dev.openbili.webdemo.ui

/**
 * 资料页覆盖层：从根覆盖层渲染个人空间，处理资料层与视频/番剧层的叠放与返回转场。
 */

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import dev.openbili.webdemo.AuthViewModel
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.article.ArticleOrigin
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.my.MyUiState
import dev.openbili.webdemo.my.MyViewModel
import dev.openbili.webdemo.my.ProfilePrivateConversationPane

@Composable
internal fun AppRootProfileOverlay(
  ctx: AppRootProfileContext,
  profileState: AppRootProfileState,
  profileStateHolder: SaveableStateHolder,
  authViewModel: AuthViewModel,
  profileMessageViewModel: MyViewModel,
  profileMessageState: MyUiState,
  hiddenProfileCoverItemIdState: MutableState<String?>,
  hiddenMyCoverItemIdState: MutableState<String?>,
  profileBangumiReturnRequestState: MutableState<ProfileBangumiReturnRequest?>,
  hiddenProfileArticleItemIdState: MutableState<String?>,
  profileVideoTransitionActiveState: MutableState<Boolean>,
  transitionSessionState: MutableState<CardTransitionSession?>,
  activeSession: CardTransitionSession?,
  profileArticleBounds: SnapshotStateMap<String, Rect>,
  profileCardBounds: SnapshotStateMap<ProfileVideoKey, Rect>,
  showVideoPreviewRef: (FeedItem) -> Unit,
  cancelPreparingProfileVideoRef: () -> Unit,
  reverseActiveEnterRef: () -> Unit,
  startProfileBangumiRef: (Long, SpaceContentCard, Rect) -> Unit,
  startEnterArticleRef: (ArticleItem, Rect?, ArticleOrigin) -> Unit,
  startEnterLiveRef: (LiveSearchRoom, Rect) -> Unit,
  loadFollowingGroupsRef: () -> Unit,
  startProfileVideoRef: (Long, FeedItem, Rect) -> Unit,
  openInteractionTargetRef: (AccountMessage, Rect, Long?) -> Unit,
) {
  var hiddenProfileCoverItemId by hiddenProfileCoverItemIdState
  val hiddenMyCoverItemId by hiddenMyCoverItemIdState
  var profileBangumiReturnRequest by profileBangumiReturnRequestState
  var hiddenProfileArticleItemId by hiddenProfileArticleItemIdState
  var profileVideoTransitionActive by profileVideoTransitionActiveState
  var transitionSession by transitionSessionState
  var spaceVideos by profileState::spaceVideos
  var spacePage by profileState::spacePage
  var spaceHasMore by profileState::spaceHasMore
  var spaceLoading by profileState::spaceLoading
  var spaceError by profileState::spaceError
  var spaceDynamics by profileState::spaceDynamics
  var spaceDynamicHasMore by profileState::spaceDynamicHasMore
  var spaceDynamicLoading by profileState::spaceDynamicLoading
  var spaceDynamicError by profileState::spaceDynamicError
  var selectedDynamicId by profileState::selectedDynamicId
  var spaceCollections by profileState::spaceCollections
  var spaceCollectionsLoading by profileState::spaceCollectionsLoading
  var spaceCollectionsError by profileState::spaceCollectionsError
  var selectedCollectionId by profileState::selectedCollectionId
  var spaceCollectionVideos by profileState::spaceCollectionVideos
  var spaceCollectionPage by profileState::spaceCollectionPage
  var spaceCollectionHasMore by profileState::spaceCollectionHasMore
  var spaceCollectionLoading by profileState::spaceCollectionLoading
  var spaceCollectionError by profileState::spaceCollectionError
  var spaceCollectionTotal by profileState::spaceCollectionTotal
  var followingGroups by profileState::followingGroups
  var followingGroupsLoading by profileState::followingGroupsLoading
  val followingStates = profileState.followingStates
  val followingBusy = profileState.followingBusy
  fun showVideoPreview(item: FeedItem) = showVideoPreviewRef(item)
  fun cancelPreparingProfileVideo() = cancelPreparingProfileVideoRef()
  fun reverseActiveEnter() = reverseActiveEnterRef()
  fun startProfileBangumi(profileEntryId: Long, card: SpaceContentCard, cardBounds: Rect) =
    startProfileBangumiRef(profileEntryId, card, cardBounds)
  fun startEnterArticle(article: ArticleItem, sourceBounds: Rect?, origin: ArticleOrigin) =
    startEnterArticleRef(article, sourceBounds, origin)
  fun startEnterLive(room: LiveSearchRoom, sourceBounds: Rect) = startEnterLiveRef(room, sourceBounds)
  fun loadFollowingGroups() = loadFollowingGroupsRef()
  fun startProfileVideo(profileEntryId: Long, item: FeedItem, cardBounds: Rect) =
    startProfileVideoRef(profileEntryId, item, cardBounds)
  fun openInteractionTarget(message: AccountMessage, sourceBounds: Rect, profileEntryId: Long?) =
    openInteractionTargetRef(message, sourceBounds, profileEntryId)

if (ctx.profileStack.isNotEmpty())
  AppRootProfileLayer(
    // 与保留的番剧主页一致：来源资料页保持组合，让它的分页、筛选、滚动状态、
    // 已解码封面与直播卡边界在播放期间存活。被压制的资料页位于活动详情层之下且
    // 完全透明，因此不会遮挡或拦截播放器，同时仍能准备精确的返回目的地。
    modifier =
      Modifier.zIndex(if (ctx.profileLayerSuppressed) -1f else 1f).graphicsLayer {
        alpha = if (ctx.profileLayerSuppressed) 0f else 1f
      },
    profileMid = ctx.profileMid,
    profileStateHolder = profileStateHolder,
    profile = ctx.spaceProfile,
    videos = spaceVideos,
    dynamics = spaceDynamics,
    dynamicsLoading = spaceDynamicLoading,
    dynamicsHasMore = spaceDynamicHasMore,
    dynamicsError = spaceDynamicError,
    selectedDynamicId = selectedDynamicId,
    collections = spaceCollections,
    collectionsLoading = spaceCollectionsLoading,
    collectionsError = spaceCollectionsError,
    selectedCollectionId = selectedCollectionId,
    collectionVideos = spaceCollectionVideos,
    collectionPage = spaceCollectionPage,
    collectionHasMore = spaceCollectionHasMore,
    collectionLoading = spaceCollectionLoading,
    collectionError = spaceCollectionError,
    collectionTotal = spaceCollectionTotal,
    loading = spaceLoading,
    hasMore = spaceHasMore,
    error = spaceError,
    currentPage = spacePage,
    currentAccountMid = ctx.authUserInfo.mid,
    followed = ctx.profileMid?.let { followingStates[it] ?: ctx.spaceProfile?.followed } ?: false,
    followBusy = ctx.profileMid?.let { followingBusy[it] } == true,
    followingGroups = followingGroups,
    followingGroupsLoading = followingGroupsLoading,
    loggedIn = ctx.authUserInfo.isLogin,
    profileIpAuthorized = ctx.profileIpAuthorized,
    settings = ctx.settings,
    hiddenCoverItemId = hiddenProfileCoverItemId,
    hiddenLiveCoverItemId = hiddenMyCoverItemId,
    bangumiReturnRequest = profileBangumiReturnRequest,
    hiddenArticleItemId = hiddenProfileArticleItemId,
    profileAvatarBounds = ctx.profileAvatarBounds,
    commentTransition = ctx.commentProfileTransition,
    commentReturnTransition = ctx.commentProfileReturnTransition,
    avatarTransition = ctx.avatarProfileTransition,
    avatarReturnTransition = ctx.avatarProfileReturnTransition,
    activeSession = activeSession,
    profileVideoTransitionActive = profileVideoTransitionActive,
    onBack = {
      when {
        transitionSession?.kind == TransitionKind.ENTER_PROFILE -> reverseActiveEnter()
        transitionSession == null && ctx.transitionPhase is TransitionPhase.ToVideo ->
          cancelPreparingProfileVideo()
        else -> ctx.closeProfile()
      }
    },
    onVideoClick = ::startProfileVideo,
    onBangumiClick = ::startProfileBangumi,
    onVideoLongClick = { showVideoPreview(it) },
    onArticleClick = { article, bounds ->
      val sourceBounds = bounds.takeUnless { it == Rect.Zero }
      if (sourceBounds != null) profileArticleBounds[article.stableId] = sourceBounds
      startEnterArticle(article, sourceBounds, ArticleOrigin.PROFILE)
    },
    onLiveClick = { room, bounds -> startEnterLive(room, bounds) },
    onLiveBoundsChanged = { room, bounds ->
      if (bounds.hasUsableSize()) ctx.myCardBounds[room.stableId] = bounds
    },
    onArticleBoundsChanged = { article, bounds ->
      if (bounds.hasUsableSize()) profileArticleBounds[article.stableId] = bounds
    },
    onLoadPage = { page -> ctx.profileMid?.let { ctx.loadSpacePage(it, page) } },
    onRefresh = { ctx.profileMid?.let(ctx::loadPreparedProfile) },
    onLoadFollowingGroups = ::loadFollowingGroups,
    onSelectFollowingGroup = { entryId, groupId ->
      ctx.activeProfileEntry(entryId)?.state?.let { state ->
        state.profileMid?.let { mid ->
          state.selectFollowingGroup(
            mid = mid,
            groupId = groupId,
            loggedIn = ctx.authUserInfo.isLogin,
            onLogin = authViewModel::startLogin,
            context = ctx.context,
            scope = ctx.scope,
          )
        }
      }
    },
    onUnfollow = { entryId ->
      ctx.activeProfileEntry(entryId)?.state?.let { state ->
        state.profileMid?.let { mid ->
          state.unfollow(
            mid = mid,
            loggedIn = ctx.authUserInfo.isLogin,
            onLogin = authViewModel::startLogin,
            context = ctx.context,
            scope = ctx.scope,
          )
        }
      }
    },
    onPrivateMessagesSelected = { mid, name, face ->
      if (ctx.authUserInfo.isLogin) {
        profileMessageViewModel.openPrivateConversation(mid, name, face)
      } else {
        authViewModel.startLogin()
      }
    },
    privateMessageContent = { profileEntryId ->
      ProfilePrivateConversationPane(
        state = profileMessageState,
        onLoadMoreHistory = profileMessageViewModel::loadMorePrivateMessageHistory,
        onReplyPrivate = { text, imageUri ->
          profileMessageViewModel.replyToSelectedPrivate(
            ctx.context.applicationContext,
            text,
            imageUri,
          )
        },
        onWithdraw = profileMessageViewModel::withdrawPrivateMessage,
        onDelete = profileMessageViewModel::deletePrivateMessage,
        onProfile = { _, _, _, _ -> },
        onTarget = { message, bounds ->
          openInteractionTarget(message, bounds, profileEntryId)
        },
      )
    },
    onLogin = authViewModel::startLogin,
    onCommentProfileClick = { entryId, mid, comment, anchor ->
      ctx.openCommentProfileFrom(entryId, mid, comment, anchor)
    },
    onAvatarProfileClick = { entryId, mid, face, name, bounds ->
      ctx.openAvatarProfileFrom(entryId, mid, bounds, face, name)
    },
    onEnsureDynamics = {
      if (spaceDynamics.isEmpty()) ctx.loadSpaceDynamics(refresh = false)
    },
    onLoadMoreDynamics = { ctx.loadSpaceDynamics(refresh = false) },
    onRefreshDynamics = { ctx.loadSpaceDynamics(refresh = true) },
    onSelectedDynamicIdChange = { selectedDynamicId = it },
    onEnsureCollections = {
      ctx.profileMid?.let { mid ->
        profileState.loadSpaceCollections(mid, refresh = false, ctx.scope)
      }
    },
    onRefreshCollections = {
      ctx.profileMid?.let { mid ->
        profileState.loadSpaceCollections(mid, refresh = true, ctx.scope)
      }
    },
    onSelectedCollectionChange = { collection ->
      val mid = ctx.profileMid
      if (collection == null) profileState.clearSelectedSpaceCollection()
      else if (mid != null) profileState.selectSpaceCollection(mid, collection, ctx.scope)
    },
    onLoadMoreCollection = {
      val mid = ctx.profileMid
      val collection = spaceCollections.firstOrNull { it.id == selectedCollectionId }
      if (mid != null && collection != null) {
        profileState.loadSpaceCollectionPage(
          mid = mid,
          collection = collection,
          page = (spaceCollectionPage + 1).coerceAtLeast(1),
          scope = ctx.scope,
        )
      }
    },
    onDynamicLike = { entryId, item ->
      ctx.activeProfileEntry(entryId)
        ?.state
        ?.toggleDynamicLike(
          item = item,
          accountMid = ctx.authUserInfo.mid,
          onLogin = authViewModel::startLogin,
          context = ctx.context,
          scope = ctx.scope,
        )
    },
    onDynamicDelete = { entryId, item ->
      ctx.activeProfileEntry(entryId)
        ?.state
        ?.deleteDynamic(
          item = item,
          accountMid = ctx.authUserInfo.mid,
          onLogin = authViewModel::startLogin,
          context = ctx.context,
          scope = ctx.scope,
        )
    },
    onDynamicPin = { entryId, item ->
      ctx.activeProfileEntry(entryId)
        ?.state
        ?.setDynamicPinned(
          item = item,
          accountMid = ctx.authUserInfo.mid,
          onLogin = authViewModel::startLogin,
          context = ctx.context,
          scope = ctx.scope,
        )
    },
    onVideoBoundsChanged = { entryId, video, bounds ->
      profileCardBounds[ProfileVideoKey(entryId, video.id)] = bounds
    },
    onAvatarBoundsChanged = { bounds ->
      if (bounds.width > 0f && bounds.height > 0f) ctx.profileAvatarBounds = bounds
    },
    profileStack = ctx.profileStack,
    // 可见资料页是顶层页面，因此拥有系统返回键：其关闭转场恢复底下的保留番剧
    // 播放页。
    backHandlingEnabled = !ctx.profileLayerSuppressed,
  )
}

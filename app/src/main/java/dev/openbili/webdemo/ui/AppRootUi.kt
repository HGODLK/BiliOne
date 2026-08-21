package dev.openbili.webdemo.ui

/**
 * 根 Box 内容层：把首页信息流、番剧索引、直播区索引、视频页（层级关键位置见下）、
 * 播放器宿主、直播层与文章层按基线顺序叠放。曾因把 videoCtx.VideoLayer() 移出本
 * Box 导致视频页被不透明根背景盖住（提交 97be303 修复）。
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.openbili.webdemo.AppUiState
import dev.openbili.webdemo.AuthViewModel
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.bangumi.BangumiIndexUiState
import dev.openbili.webdemo.bangumi.BangumiIndexViewModel
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.live.LiveHomeUiState
import dev.openbili.webdemo.live.LiveHomeViewModel
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.AppSettingsViewModel
import dev.openbili.webdemo.video.CommentProfileAnchor
import kotlinx.coroutines.Job

@Composable
internal fun AppRootBoxContent(
  feedCtx: AppRootFeedContext,
  videoCtx: AppRootVideoContext,
  articleCtx: AppRootArticleContext,
  showFeed: Boolean,
  showBangumiIndex: Boolean,
  searchBangumiSourceAboveVideo: Boolean,
  feedLayerAlpha: Float,
  bangumiIndexState: BangumiIndexUiState,
  bangumiIndexGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
  bangumiIndexViewModel: BangumiIndexViewModel,
  bangumiControlLevelState: MutableState<BangumiControlLevel>,
  bangumiControlFocusRestoreRequestState: MutableState<Int>,
  overlayTransitionContext: AppRootOverlayContext,
  bangumiIndexCardBounds: SnapshotStateMap<String, Rect>,
  hiddenBangumiIndexItemId: String?,
  appState: AppUiState,
  transitionPhase: TransitionPhase,
  liveAreaIndex: LiveAreaIndexTransitionState,
  liveHomeState: LiveHomeUiState,
  liveHomeViewModel: LiveHomeViewModel,
  startRootBangumiRef: (SpaceContentCard, FeedItem, Rect, PageOrigin, VideoOrigin, Boolean) -> Unit,
  settings: AppSettings,
  authUserInfo: UserInfo,
  profileStack: List<ProfileStackEntry>,
  profileLayerSuppressed: Boolean,
  activeLiveRoom: LiveSearchRoom?,
  activeLiveEntryId: Long,
  liveTransitionSession: CardTransitionSession?,
  liveTransitionJob: Job?,
  liveExitPrelude: VideoExitPrelude?,
  liveVideoSurfaceVisibleState: MutableState<Boolean>,
  liveFullscreenTransitionActiveState: MutableState<Boolean>,
  liveFirstFrameEntryIdState: MutableState<Long>,
  livePlayerBoundsState: MutableState<Rect>,
  liveRoomParentStack: List<LiveRoomParentFrame>,
  hiddenLiveRecommendationCoverItemIdState: MutableState<String?>,
  liveRoomStateHolder: SaveableStateHolder,
  liveRecommendationCardBounds: SnapshotStateMap<String, Rect>,
  livePageAlpha: Animatable<Float, AnimationVector1D>,
  liveTransitionContext: AppRootLiveTransitionContext,
  renderedVideoId: String?,
  renderedVideoFrameCount: Int,
  playerViewModel: PlayerViewModel,
  authViewModel: AuthViewModel,
  settingsViewModel: AppSettingsViewModel,
  rootPlayerContent: @Composable (Modifier, Float, Boolean, Boolean, SharedPlayerViewRole, Boolean?, Boolean) -> Unit,
  openAvatarProfileRef: (Long, Rect, String?, String?) -> Unit,
  rootPlayerHostEnabled: Boolean,
  rootPlayerOwnershipState: MutableState<RootPlayerOwnership>,
  bangumiPreviewTargetState: MutableState<BangumiPreviewTarget?>,
  showVideo: Boolean,
  profileStackState: MutableState<List<ProfileStackEntry>>,
  hiddenArticleVideoCoverItemIdState: MutableState<String?>,
  articleVideoBounds: SnapshotStateMap<String, Rect>,
  videoState: AppRootVideoState,
  openProfileRef: (Long) -> Unit,
  openArticleCommentProfileRef: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  returnDirectlyHomeRef: () -> Unit,
  showVideoPreviewRef: (FeedItem) -> Unit,
  loadMentionSuggestionsRef: (String) -> Unit,
) {
  fun openAvatarProfile(mid: Long, bounds: Rect, face: String?, name: String?) =
    openAvatarProfileRef(mid, bounds, face, name)
  fun openProfile(mid: Long) = openProfileRef(mid)
  fun openArticleCommentProfile(mid: Long, comment: CommentItem, anchor: CommentProfileAnchor) =
    openArticleCommentProfileRef(mid, comment, anchor)
  fun returnDirectlyHome() = returnDirectlyHomeRef()
  fun showVideoPreview(item: FeedItem) = showVideoPreviewRef(item)
  fun loadMentionSuggestions(query: String) = loadMentionSuggestionsRef(query)
  var rootPlayerOwnership by rootPlayerOwnershipState
  var bangumiPreviewTarget by bangumiPreviewTargetState
  fun startRootBangumi(
    card: SpaceContentCard,
    item: FeedItem,
    cardBounds: Rect,
    pageOrigin: PageOrigin,
    videoOrigin: VideoOrigin,
    restoreEpisodeSelection: Boolean = true,
  ) = startRootBangumiRef(card, item, cardBounds, pageOrigin, videoOrigin, restoreEpisodeSelection)
  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

  feedCtx.FeedLayer()

  // 让根 Pager 保持在索引下方组合。推荐屏拥有当前的探索/推荐状态与滚动位置：
  // 在这里替换它会在索引关闭时把状态重置回"本期推荐"层。
  if (showFeed && showBangumiIndex) {
    Box(
      Modifier.fillMaxSize()
        .zIndex(if (searchBangumiSourceAboveVideo) 1f else 0f)
        .graphicsLayer { alpha = feedLayerAlpha }
        .background(MaterialTheme.colorScheme.background)
    ) {
      Surface(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        shape = VideoShapeTokens.Card,
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp,
        border =
          androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
          ),
      ) {
        BangumiIndexScreen(
          state = bangumiIndexState,
          gridState = bangumiIndexGridState,
          controlLevel = bangumiControlLevelState.value,
          controlFocusRestoreRequest = bangumiControlFocusRestoreRequestState.value,
          onControlLevelChanged = { bangumiControlLevelState.value = it },
          onBack = {
            bangumiControlLevelState.value = BangumiControlLevel.EXPLORE_NAV
            closeBangumiIndexAnimated(overlayTransitionContext)
          },
          onReset = bangumiIndexViewModel::reset,
          onOrderSelected = bangumiIndexViewModel::selectOrder,
          onSortDirectionToggle = bangumiIndexViewModel::toggleSortDirection,
          onQueryChanged = bangumiIndexViewModel::updateQuery,
          onKeywordChange = bangumiIndexViewModel::setKeyword,
          onRefresh = bangumiIndexViewModel::refresh,
          onLoadMore = bangumiIndexViewModel::loadNextPage,
          onRetry = bangumiIndexViewModel::retry,
          onOpen = { indexItem, bounds ->
            val card = indexItem.toIndexBangumiCard()
            startRootBangumi(
              card = card,
              item = card.toBangumiVideoItem(),
              cardBounds = bounds,
              pageOrigin = PageOrigin.BangumiIndex,
              videoOrigin = VideoOrigin.OTHER,
              restoreEpisodeSelection = !bangumiIndexState.searching,
            )
          },
          onItemBounds = { item, bounds ->
            if (bounds.hasUsableSize()) bangumiIndexCardBounds[item.stableId] = bounds
          },
          hiddenItemId = hiddenBangumiIndexItemId,
          foregroundActive = !appState.isVideoScreen && transitionPhase is TransitionPhase.Feed,
        )
      }
    }
  }
  if (showFeed && liveAreaIndex.show) {
    LiveAreaIndexOverlay(
      state = liveHomeState,
      onBack = { closeLiveAreaIndexAnimated(overlayTransitionContext) },
      onAreaSelected = { area ->
        closeLiveAreaIndexAnimated(overlayTransitionContext)
        liveHomeViewModel.selectArea(area)
      },
    )
  }


  // 只有番剧主页拥有这个应用根 PlayerView。卡片飞行会拆下真实播放器；落地的详情
  // 页稍后在静止缓存封面之下挂载它。
  videoCtx.VideoLayer()
  RootPlayerLayer(
    hostEnabled =
      rootPlayerHostEnabled &&
        rootPlayerOwnership.role !in
          setOf(RootPlayerSurfaceRole.PREVIEW, RootPlayerSurfaceRole.PREVIEW_PENDING),
    ownership = rootPlayerOwnership,
    previewBounds = Rect.Zero,
    previewCoverAlpha = { 1f },
    previewCoverBlend = null,
    previewGestureVisualActive = false,
    previewPortalVisible = false,
    previewImageLoadingEnabled = false,
    previewTarget = bangumiPreviewTarget,
    layerItem = appState.selectedVideo ?: bangumiPreviewTarget?.item,
    playerContent = { host ->
      rootPlayerContent(
        host.modifier,
        host.fullscreenProgress,
        host.fullscreen,
        host.danmakuAllowed,
        SharedPlayerViewRole.PREVIEW,
        null,
        false,
      )
    },
  )

  // 第 1 层：直播房。它是视频、番剧与文章的同级详情页，同时保留底下的搜索结果
  // 作为其唯一的第一阶段进入来源。
  AppRootLiveLayer(
    settings = settings,
    authUserInfo = authUserInfo,
    profileStack = profileStack,
    profileLayerSuppressed = profileLayerSuppressed,
    activeLiveRoom = activeLiveRoom,
    activeLiveEntryId = activeLiveEntryId,
    liveTransitionSession = liveTransitionSession,
    liveTransitionJob = liveTransitionJob,
    liveExitPrelude = liveExitPrelude,
    liveVideoSurfaceVisibleState = liveVideoSurfaceVisibleState,
    liveFullscreenTransitionActiveState = liveFullscreenTransitionActiveState,
    liveFirstFrameEntryIdState = liveFirstFrameEntryIdState,
    livePlayerBoundsState = livePlayerBoundsState,
    liveRoomParentStack = liveRoomParentStack,
    hiddenLiveRecommendationCoverItemIdState = hiddenLiveRecommendationCoverItemIdState,
    liveRoomStateHolder = liveRoomStateHolder,
    liveRecommendationCardBounds = liveRecommendationCardBounds,
    livePageAlpha = livePageAlpha,
    liveTransitionContext = liveTransitionContext,
    renderedVideoId = renderedVideoId,
    renderedVideoFrameCount = renderedVideoFrameCount,
    playerViewModel = playerViewModel,
    authViewModel = authViewModel,
    settingsViewModel = settingsViewModel,
    rootPlayerContent = rootPlayerContent,
    openAvatarProfileRef = { mid: Long, bounds: Rect, face: String?, name: String? ->
      openAvatarProfile(mid, bounds, face, name)
    },
  )

  // 第 1 层：文章。它是视频的同级详情页，并保持其根来源挂载。
  AppRootArticleLayer(
    ctx = articleCtx,
    showVideo = showVideo,
    authUserInfo = authUserInfo,
    profileStackState = profileStackState,
    hiddenArticleVideoCoverItemIdState = hiddenArticleVideoCoverItemIdState,
    articleVideoBounds = articleVideoBounds,
    videoState = videoState,
    openAvatarProfileRef = { mid: Long, bounds: Rect, face: String?, name: String? ->
      openAvatarProfile(mid, bounds, face, name)
    },
    openProfileRef = { mid: Long -> openProfile(mid) },
    openArticleCommentProfileRef = { mid: Long, comment: CommentItem, anchor: CommentProfileAnchor ->
      openArticleCommentProfile(mid, comment, anchor)
    },
    returnDirectlyHomeRef = { returnDirectlyHome() },
    showVideoPreviewRef = { item: FeedItem -> showVideoPreview(item) },
    loadMentionSuggestionsRef = { query: String -> loadMentionSuggestions(query) },
  )
  }
}

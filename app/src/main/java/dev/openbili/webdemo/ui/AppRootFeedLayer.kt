package dev.openbili.webdemo.ui

/** 首页信息流上下文与图层：首页推荐/动态/热门频道的 Pager、控制器焦点策略与 番剧索引覆盖层。 */
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import dev.openbili.webdemo.AppUiState
import dev.openbili.webdemo.AuthViewModel
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BangumiExploreSectionKind
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.article.ArticleOrigin
import dev.openbili.webdemo.article.ArticleStackFrame
import dev.openbili.webdemo.bangumi.BangumiExploreViewModel
import dev.openbili.webdemo.bangumi.BangumiRecommendationUiState
import dev.openbili.webdemo.bangumi.BangumiRecommendationViewModel
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedScrollAnchor
import dev.openbili.webdemo.feed.FeedUiState
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.live.LiveHomeSourceAnchor
import dev.openbili.webdemo.my.MyControlLevel
import dev.openbili.webdemo.my.MyControllerState
import dev.openbili.webdemo.my.MyScreen
import dev.openbili.webdemo.my.MyUiState
import dev.openbili.webdemo.my.MyViewModel
import dev.openbili.webdemo.my.WatchLaterUiState
import dev.openbili.webdemo.my.WatchLaterViewModel
import dev.openbili.webdemo.search.SearchResultsScreen
import dev.openbili.webdemo.search.SearchUiState
import dev.openbili.webdemo.search.SearchViewModel
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.AppSettingsViewModel
import dev.openbili.webdemo.video.CommentProfileAnchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 首页信息流上下文。 */
internal class AppRootFeedContext(
  // 环境
  val context: Context,
  val scope: CoroutineScope,
  val controlMode: Boolean,
  val controlInitialFocusRequester: FocusRequester,
  val feedGridState: LazyGridState,
  val searchGridState: LazyGridState,
  val rootPagerState: PagerState,
  val liveAreaIndex: LiveAreaIndexTransitionState,
  val overlayTransitionContext: AppRootOverlayContext,
  val liveTransitionContext: AppRootLiveTransitionContext,
  // 视图模型与回调
  val feedViewModel: FeedViewModel,
  val authViewModel: AuthViewModel,
  val playerViewModel: PlayerViewModel,
  val myViewModel: MyViewModel,
  val searchViewModel: SearchViewModel,
  val settingsViewModel: AppSettingsViewModel,
  val bangumiRecommendationViewModel: BangumiRecommendationViewModel,
  val bangumiExploreViewModel: BangumiExploreViewModel,
  val watchLaterViewModel: WatchLaterViewModel,
  val onSearch: () -> Unit,
  val onFeedRefresh: () -> Unit,
  val onFeedPullRefresh: (Int) -> Unit,
  // 收集到的值
  val userInfo: UserInfo,
  val appState: AppUiState,
  val myState: MyUiState,
  val searchState: SearchUiState,
  val watchLaterState: WatchLaterUiState,
  val settings: AppSettings,
  val authUserInfo: UserInfo,
  val profileIpAuthorized: Boolean,
  val bangumiRecommendationState: BangumiRecommendationUiState,
  val feedState: FeedUiState,
  // 计算值
  val rootPageSwitchInProgress: Boolean,
  val searchBangumiSourceAboveVideo: Boolean,
  val rootEnterSession: CardTransitionSession?,
  val bangumiRootPageActive: Boolean,
  val showFeed: Boolean,
  val feedLayerAlpha: Float,
  val activeArticleFrame: ArticleStackFrame?,
  val showVideo: Boolean,
  val navigationLocked: Boolean,
  // 映射表
  val feedCardBounds: MutableMap<String, Rect>,
  val popularCardBounds: MutableMap<String, Rect>,
  val dynamicCardBounds: MutableMap<String, Rect>,
  val homeLiveCardBounds: MutableMap<String, Rect>,
  val homeDynamicArticleBounds: SnapshotStateMap<String, Rect>,
  val myArticleBounds: SnapshotStateMap<String, Rect>,
  val searchArticleBounds: SnapshotStateMap<String, Rect>,
  val searchCardBounds: SnapshotStateMap<String, Rect>,
  val myCardBounds: SnapshotStateMap<String, Rect>,
  val myInteractionArticleMessageIds: SnapshotStateMap<String, Long>,
  val myInteractionVideoMessageIds: SnapshotStateMap<String, Long>,
  // 资料页持有状态
  val profileState: AppRootProfileState,
  // 具名状态持有器
  val homeControlSearchFocusRequestState: MutableState<Int>,
  val homeControlFocusRestoreRequestState: MutableState<Int>,
  val homeControlLevelState: MutableState<HomeControlLevel>,
  val bangumiControlSecondLevelRequestState: MutableState<Int>,
  val bangumiControlFocusRestoreRequestState: MutableState<Int>,
  val bangumiControlLevelState: MutableState<BangumiControlLevel>,
  val myControllerState: MyControllerState,
  val hiddenMyCoverItemIdState: MutableState<String?>,
  val searchBoundsState: MutableState<Rect>,
  val showSearchResultsState: MutableState<Boolean>,
  val searchTransitionDirectionState: MutableState<SearchTransitionDirection?>,
  val showSearchState: MutableState<Boolean>,
  val searchOpenedFromControllerState: MutableState<Boolean>,
  val hiddenSearchCoverItemIdState: MutableState<String?>,
  val bangumiIndexTransitionDirectionState: MutableState<SearchTransitionDirection?>,
  val hiddenHomeLiveCoverItemIdState: MutableState<String?>,
  val showBangumiIndexState: MutableState<Boolean>,
  val hiddenMyArticleItemIdState: MutableState<String?>,
  val hiddenRecommendationCoverItemIdState: MutableState<String?>,
  val hiddenSearchArticleItemIdState: MutableState<String?>,
  val hiddenHomeDynamicArticleItemIdState: MutableState<String?>,
  val activeLiveRoomState: MutableState<LiveSearchRoom?>,
  val activeLiveOriginState: MutableState<PageOrigin>,
  val liveAreaIndexFocusRestoreRequestState: MutableState<Int>,
  val transitionPhaseState: MutableState<TransitionPhase>,
  val activeBangumiPageState: MutableState<ActiveBangumiPage?>,
  val directHomeInProgressState: MutableState<Boolean>,
  val dismissedFeedItemIdsState: MutableState<Set<String>>,
  val homeDynamicDetailActiveState: MutableState<Boolean>,
  val musicEntryInputLockedState: MutableState<Boolean>,
  val rootTabState: MutableState<RootTab>,
  val hiddenHomeDynamicCoverItemIdState: MutableState<String?>,
  val bangumiPreviewTargetState: MutableState<BangumiPreviewTarget?>,
  val hiddenFeedCoverItemIdState: MutableState<String?>,
  val bangumiStartupPreloadReadyState: MutableState<Boolean>,
  val homeLivePreludeActiveState: MutableState<Boolean>,
  val bangumiPreviewMutedState: MutableState<Boolean>,
  val bangumiCardEnterPendingState: MutableState<Boolean>,
  val hiddenBangumiRecommendationItemIdState: MutableState<String?>,
  val rootPageSwitchRequestedState: MutableState<Boolean>,
  val homeRecommendationModeState: MutableState<HomeRecommendationMode>,
  val startupWarmupFadeInProgressState: MutableState<Boolean>,
  val hiddenPopularCoverItemIdState: MutableState<String?>,
  val homeControlSecondLevelRequestState: MutableState<Int>,
  // 外部函数
  val startEnterVideoRef: (FeedItem, Rect?, VideoOrigin, FeedScrollAnchor?, String?) -> Unit,
  val startSearchBangumiRef: (SpaceContentCard, Rect, Boolean) -> Unit,
  val startEnterArticleRef: (ArticleItem, Rect?, ArticleOrigin) -> Unit,
  val openInteractionTargetRef: (AccountMessage, Rect) -> Unit,
  val openCommentProfileRef: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  val startRootBangumiRef:
    (
      SpaceContentCard,
      FeedItem,
      Rect,
      PageOrigin,
      VideoOrigin,
      Boolean,
      BangumiSeason?,
      Boolean,
    ) -> Unit,
  val showVideoPreviewRef: (FeedItem, Boolean) -> Unit,
  val openAvatarProfileRef: (Long, Rect, String?, String?) -> Unit,
  val animateToRootTabRef: (RootTab) -> Unit,
  val startHistoryBangumiRef: (SpaceContentCard, FeedItem, Rect) -> Unit,
) {
  var homeControlSearchFocusRequest by homeControlSearchFocusRequestState
  var homeControlFocusRestoreRequest by homeControlFocusRestoreRequestState
  var homeControlLevel by homeControlLevelState
  var bangumiControlSecondLevelRequest by bangumiControlSecondLevelRequestState
  var bangumiControlFocusRestoreRequest by bangumiControlFocusRestoreRequestState
  var bangumiControlLevel by bangumiControlLevelState
  val myController = myControllerState
  var hiddenMyCoverItemId by hiddenMyCoverItemIdState
  var searchBounds by searchBoundsState
  var showSearchResults by showSearchResultsState
  var searchTransitionDirection by searchTransitionDirectionState
  var showSearch by showSearchState
  var searchOpenedFromController by searchOpenedFromControllerState
  var hiddenSearchCoverItemId by hiddenSearchCoverItemIdState
  var bangumiIndexTransitionDirection by bangumiIndexTransitionDirectionState
  var hiddenHomeLiveCoverItemId by hiddenHomeLiveCoverItemIdState
  var showBangumiIndex by showBangumiIndexState
  var hiddenMyArticleItemId by hiddenMyArticleItemIdState
  var hiddenRecommendationCoverItemId by hiddenRecommendationCoverItemIdState
  var hiddenSearchArticleItemId by hiddenSearchArticleItemIdState
  var hiddenHomeDynamicArticleItemId by hiddenHomeDynamicArticleItemIdState
  var activeLiveRoom by activeLiveRoomState
  var activeLiveOrigin by activeLiveOriginState
  var liveAreaIndexFocusRestoreRequest by liveAreaIndexFocusRestoreRequestState
  var transitionPhase by transitionPhaseState
  var activeBangumiPage by activeBangumiPageState
  var directHomeInProgress by directHomeInProgressState
  var dismissedFeedItemIds by dismissedFeedItemIdsState
  var homeDynamicDetailActive by homeDynamicDetailActiveState
  var musicEntryInputLocked by musicEntryInputLockedState
  var rootTab by rootTabState
  var hiddenHomeDynamicCoverItemId by hiddenHomeDynamicCoverItemIdState
  var bangumiPreviewTarget by bangumiPreviewTargetState
  var hiddenFeedCoverItemId by hiddenFeedCoverItemIdState
  var bangumiStartupPreloadReady by bangumiStartupPreloadReadyState
  var homeLivePreludeActive by homeLivePreludeActiveState
  var bangumiPreviewMuted by bangumiPreviewMutedState
  var bangumiCardEnterPending by bangumiCardEnterPendingState
  var hiddenBangumiRecommendationItemId by hiddenBangumiRecommendationItemIdState
  var rootPageSwitchRequested by rootPageSwitchRequestedState
  var homeRecommendationMode by homeRecommendationModeState
  var startupWarmupFadeInProgress by startupWarmupFadeInProgressState
  var hiddenPopularCoverItemId by hiddenPopularCoverItemIdState
  var homeControlSecondLevelRequest by homeControlSecondLevelRequestState
  var commentProfileTransition by profileState::commentProfileTransition
  var avatarProfileTransition by profileState::avatarProfileTransition
  val isRootCapsuleFocusEnabled =
    rootCapsuleFocusEnabled(
      controlMode = controlMode,
      showVideo = showVideo,
      showBangumiIndex = showBangumiIndex,
      rootTab = rootTab,
      homeControlLevel = homeControlLevel,
      bangumiControlLevel = bangumiControlLevel,
      myControlLevel = myController.level,
    )

  fun startEnterVideo(
    item: FeedItem,
    cardBounds: Rect?,
    origin: VideoOrigin,
    rootFeedScrollAnchor: FeedScrollAnchor? = null,
    sourceAnchorKey: String? = null,
  ) = startEnterVideoRef(item, cardBounds, origin, rootFeedScrollAnchor, sourceAnchorKey)

  fun startSearchBangumi(
    card: SpaceContentCard,
    cardBounds: Rect,
    sourceIsBangumiExplorePoster: Boolean = false,
  ) = startSearchBangumiRef(card, cardBounds, sourceIsBangumiExplorePoster)

  fun startEnterArticle(article: ArticleItem, sourceBounds: Rect?, origin: ArticleOrigin) =
    startEnterArticleRef(article, sourceBounds, origin)

  fun openInteractionTarget(message: AccountMessage, sourceBounds: Rect) =
    openInteractionTargetRef(message, sourceBounds)

  fun openCommentProfile(mid: Long, comment: CommentItem, anchor: CommentProfileAnchor) =
    openCommentProfileRef(mid, comment, anchor)

  fun startRootBangumi(
    card: SpaceContentCard,
    item: FeedItem,
    cardBounds: Rect,
    pageOrigin: PageOrigin,
    videoOrigin: VideoOrigin,
    restoreEpisodeSelection: Boolean = true,
    initialSeason: BangumiSeason? = null,
    returnToSourceCover: Boolean = false,
  ) =
    startRootBangumiRef(
      card,
      item,
      cardBounds,
      pageOrigin,
      videoOrigin,
      restoreEpisodeSelection,
      initialSeason,
      returnToSourceCover,
    )

  fun showVideoPreview(item: FeedItem, fromHomeFeed: Boolean = false) =
    showVideoPreviewRef(item, fromHomeFeed)

  fun openAvatarProfile(mid: Long, bounds: Rect, face: String? = null, name: String? = null) =
    openAvatarProfileRef(mid, bounds, face ?: authUserInfo.face, name ?: authUserInfo.name)

  fun animateToRootTab(tab: RootTab) = animateToRootTabRef(tab)

  fun startHistoryBangumi(card: SpaceContentCard, item: FeedItem, cardBounds: Rect) =
    startHistoryBangumiRef(card, item, cardBounds)

  @Composable
  fun FeedLayer() {
    val isRootCapsuleVisible =
      rootCapsuleVisible(
        controlMode = controlMode,
        controlFocusVisible = LocalControlFocusVisible.current,
        focusEnabled = isRootCapsuleFocusEnabled,
      )

    if (showFeed) {
      val rootBackdropLayer = rememberGraphicsLayer()
      val rootBackdropCaptured = remember { booleanArrayOf(false) }
      Box(
        Modifier.fillMaxSize().zIndex(if (searchBangumiSourceAboveVideo) 1f else 0f).graphicsLayer {
          alpha = feedLayerAlpha
        }
      ) {
        if (showSearchResults) {
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
            SearchResultsScreen(
              state = searchState,
              gridState = searchGridState,
              columns = settings.homeGridColumns,
              onCategory = searchViewModel::selectCategory,
              onOrder = searchViewModel::selectOrder,
              onArticleOrder = searchViewModel::selectArticleOrder,
              onVideo = { video, bounds ->
                val sourceBounds = bounds.takeUnless { it == Rect.Zero }
                if (sourceBounds != null) searchCardBounds[video.id] = sourceBounds
                startEnterVideo(video, sourceBounds, VideoOrigin.SEARCH)
              },
              onVideoLongClick = { showVideoPreview(it) },
              onVideoBounds = { video, bounds ->
                if (bounds.width > 0f && bounds.height > 0f) searchCardBounds[video.id] = bounds
              },
              onVideoProfile = { mid, face, name, bounds ->
                openAvatarProfile(mid, bounds, face, name)
              },
              onBangumi = { card, bounds ->
                val sourceBounds = bounds.takeUnless { it == Rect.Zero }
                if (sourceBounds != null) searchCardBounds[card.id] = sourceBounds
                startSearchBangumi(card, bounds)
              },
              onLive = { room, bounds ->
                val sourceBounds = bounds.takeUnless { it == Rect.Zero }
                if (sourceBounds != null) searchCardBounds[room.stableId] = sourceBounds
                startEnterLive(liveTransitionContext, room, sourceBounds)
              },
              onLiveBounds = { room, bounds ->
                if (bounds.hasUsableSize()) searchCardBounds[room.stableId] = bounds
              },
              onArticle = { article, bounds ->
                val sourceBounds = bounds.takeUnless { it == Rect.Zero }
                if (sourceBounds != null) searchArticleBounds[article.stableId] = sourceBounds
                startEnterArticle(article, sourceBounds, ArticleOrigin.SEARCH)
              },
              onArticleBounds = { article, bounds ->
                if (bounds.hasUsableSize()) searchArticleBounds[article.stableId] = bounds
              },
              onUser = { mid, face, name, bounds ->
                openAvatarProfile(mid, bounds, face, name)
              },
              onLoadMore = searchViewModel::loadNextPage,
              onRefresh = searchViewModel::retry,
              onRetry = searchViewModel::retry,
              onBack = { closeSearchResultsAnimated(overlayTransitionContext) },
              hiddenCoverItemId = hiddenSearchCoverItemId,
              hiddenArticleItemId = hiddenSearchArticleItemId,
              backEnabled =
                activeLiveRoom == null && !appState.isVideoScreen && activeArticleFrame == null,
            )
          }
        } else {
          val captureTransitionSourceBackdrop =
            rootEnterSession?.let {
              it.phase == SessionPhase.READY && it.backgroundStarted
            } == true
          val captureRootBackdrop =
            captureTransitionSourceBackdrop ||
              (!navigationLocked &&
                !directHomeInProgress &&
                searchTransitionDirection == null &&
                !rootPagerState.isScrollInProgress &&
                (rootTab != RootTab.HOME || !feedGridState.isScrollInProgress) &&
                transitionPhase is TransitionPhase.Feed)
          val drawFrozenTransitionSource =
            rootEnterSession?.backgroundStarted == true && rootBackdropCaptured[0]
          Box(
            Modifier.fillMaxSize().drawWithContent {
              if (captureRootBackdrop) {
                rootBackdropLayer.record { this@drawWithContent.drawContent() }
                rootBackdropCaptured[0] = true
                drawLayer(rootBackdropLayer)
              } else if (drawFrozenTransitionSource) {
                drawLayer(rootBackdropLayer)
              } else {
                // 让上一次抓帧继续供底部毛玻璃胶囊使用；没有冻结的视频转场来源时
                // 直接绘制直播页。
                drawContent()
              }
            }
          ) {
            HorizontalPager(
              state = rootPagerState,
              modifier = Modifier.fillMaxSize(),
              // 控制器模式通常不组合屏外根页，避免空间焦点越界；但“本期推荐”尚未
              // 预载完成时短暂保留相邻页。番剧页的焦点由 active 闸门关闭，这段组合只
              // 负责在用户确认切页前解析详情并解码首屏图片，完成后恢复零屏外页。
              beyondViewportPageCount = if (controlMode && bangumiStartupPreloadReady) 0 else 1,
              userScrollEnabled = false,
              key = { RootTab.entries[it] },
            ) { page ->
              val tab = RootTab.entries[page]
              if (tab == RootTab.HOME) {
                HomeHubScreen(
                  feedState = feedState,
                  userInfo = userInfo,
                  onRecommendationRefresh = onFeedRefresh,
                  onRecommendationPullRefresh = onFeedPullRefresh,
                  onRecommendationLoadNextPage = { feedViewModel.loadNextPage() },
                  onRecommendationItemClick = { item, bounds, scrollAnchor ->
                    if (transitionPhase is TransitionPhase.Feed)
                      startEnterVideo(
                        item,
                        bounds,
                        VideoOrigin.HOME,
                        rootFeedScrollAnchor = scrollAnchor,
                      )
                  },
                  onPopularItemClick = { item, bounds, scrollAnchor ->
                    if (transitionPhase is TransitionPhase.Feed) {
                      val sourceBounds = bounds.takeIf { it.hasUsableSize() }
                      if (sourceBounds != null) popularCardBounds[item.id] = sourceBounds
                      startEnterVideo(
                        item,
                        sourceBounds,
                        VideoOrigin.POPULAR,
                        rootFeedScrollAnchor = scrollAnchor,
                      )
                    }
                  },
                  onDynamicItemClick = { dynamic, item, bounds ->
                    val sourceBounds = bounds.takeIf { it.hasUsableSize() }
                    if (sourceBounds != null) dynamicCardBounds[dynamic.id] = sourceBounds
                    if (transitionPhase is TransitionPhase.Feed) {
                      startEnterVideo(
                        item = item,
                        cardBounds = sourceBounds,
                        origin = VideoOrigin.HOME_DYNAMIC,
                        sourceAnchorKey = dynamic.id,
                      )
                    }
                  },
                  onDynamicLiveClick = { room, bounds ->
                    val sourceBounds = bounds.takeIf { it.hasUsableSize() }
                    val sourceAnchor = LiveHomeSourceAnchor.feed(room.roomId, "dynamic")
                    if (sourceBounds != null) {
                      homeLiveCardBounds[sourceAnchor.stableId] = sourceBounds
                    }
                    if (transitionPhase is TransitionPhase.Feed) {
                      startEnterLive(
                        liveTransitionContext,
                        room,
                        sourceBounds,
                        PageOrigin.Home,
                        sourceAnchor = sourceAnchor,
                      )
                    }
                  },
                  onDynamicItemBoundsChanged = { dynamicId, bounds ->
                    if (bounds.hasUsableSize()) dynamicCardBounds[dynamicId] = bounds
                  },
                  onDynamicLiveBoundsChanged = { room, bounds ->
                    if (bounds.hasUsableSize()) {
                      homeLiveCardBounds[LiveHomeSourceAnchor.feed(room.roomId, "dynamic").stableId] = bounds
                    }
                  },
                  onDynamicArticleClick = { article, bounds ->
                    val sourceBounds = bounds.takeIf { it.hasUsableSize() }
                    if (sourceBounds != null) {
                      homeDynamicArticleBounds[article.stableId] = sourceBounds
                    }
                    startEnterArticle(article, sourceBounds, ArticleOrigin.HOME_DYNAMIC)
                  },
                  onDynamicArticleBoundsChanged = { article, bounds ->
                    if (bounds.hasUsableSize()) {
                      homeDynamicArticleBounds[article.stableId] = bounds
                    }
                  },
                  onDynamicCommentProfileClick = ::openCommentProfile,
                  onDynamicAvatarProfileClick = { mid, face, name, bounds ->
                    openAvatarProfile(mid, bounds, face, name)
                  },
                  onRecommendationItemLongClick = { showVideoPreview(it, fromHomeFeed = true) },
                  onRecommendationProfileClick = { item, bounds ->
                    openAvatarProfile(
                      item.uploaderMid,
                      bounds,
                      item.uploaderFace,
                      item.uploader,
                    )
                  },
                  onRecommendationLoginClick = { bounds ->
                    if (userInfo.isLogin) openAvatarProfile(userInfo.mid, bounds)
                    else authViewModel.startLogin()
                  },
                  onSearch = { fromController ->
                    if (!showSearch) {
                      searchOpenedFromController = fromController
                      searchViewModel.open()
                      showSearch = true
                    }
                  },
                  searchQuery = searchState.query,
                  onConsumeRecommendationRefreshMessage = {
                    feedViewModel.consumeRefreshMessage()
                  },
                  onSearchBoundsChanged = { searchBounds = it },
                  coverPrefetchCount = 18,
                  backgroundWorkAllowed =
                    !rootPageSwitchInProgress &&
                      transitionPhase is TransitionPhase.Feed &&
                      activeLiveRoom == null,
                  recommendationGridState = feedGridState,
                  hiddenRecommendationCoverItemId = hiddenFeedCoverItemId,
                  hiddenPopularCoverItemId = hiddenPopularCoverItemId,
                  hiddenDynamicCoverItemId = hiddenHomeDynamicCoverItemId,
                  hiddenDynamicLiveCoverItemId = hiddenHomeLiveCoverItemId,
                  hiddenDynamicArticleItemId = hiddenHomeDynamicArticleItemId,
                  hiddenDynamicCommentAvatarRpid =
                    commentProfileTransition
                      ?.takeIf { it.sourceAvatarBounds != null }
                      ?.sourceComment
                      ?.rpid,
                  hiddenDynamicAvatarSourceBounds = avatarProfileTransition?.sourceBounds,
                  dismissedRecommendationItemIds = dismissedFeedItemIds,
                  onRestoreDismissedRecommendationItem = { item ->
                    dismissedFeedItemIds = dismissedFeedItemIds - item.id
                  },
                  onRecommendationItemBoundsChanged = { feedItem, bounds ->
                    feedCardBounds[feedItem.id] = bounds
                  },
                  onLiveRoomClick = { room, sourceAnchor, bounds ->
                    val sourceBounds = bounds.takeIf { it.hasUsableSize() }
                    if (sourceBounds != null) {
                      homeLiveCardBounds[sourceAnchor.stableId] = sourceBounds
                    }
                    startEnterLive(
                      liveTransitionContext,
                      room,
                      sourceBounds,
                      PageOrigin.Home,
                      sourceAnchor = sourceAnchor,
                    )
                  },
                  onLiveRoomBoundsChanged = { sourceAnchor, bounds ->
                    if (bounds.hasUsableSize()) {
                      homeLiveCardBounds[sourceAnchor.stableId] = bounds
                    }
                  },
                  onLiveTransitionActiveChanged = { homeLivePreludeActive = it },
                  hiddenLiveCoverItemId = hiddenHomeLiveCoverItemId,
                  liveDetailActive = activeLiveRoom != null && activeLiveOrigin == PageOrigin.Home,
                  rootPageVisible = rootTab == RootTab.HOME,
                  onDynamicDetailActiveChanged = { homeDynamicDetailActive = it },
                  recommendationMode = homeRecommendationMode,
                  onRecommendationModeChanged = { homeRecommendationMode = it },
                  onMusicEntryInputLockChanged = { musicEntryInputLocked = it },
                  onMusicFavoriteFolderSelected = { folderId ->
                    settingsViewModel.update {
                      it.copy(
                        musicFavoriteFolderId = folderId,
                        musicFavoriteFolderConfigured = true,
                      )
                    }
                  },
                  settings = settings,
                  controlSecondLevelRequest = homeControlSecondLevelRequest,
                  controlSearchFocusRequest = homeControlSearchFocusRequest,
                  controlFocusRestoreRequest = homeControlFocusRestoreRequest,
                  controlLevel = homeControlLevel,
                  onControlLevelChanged = { homeControlLevel = it },
                  onLiveAreaIndex = { bounds ->
                    openLiveAreaIndexAnimated(overlayTransitionContext, bounds)
                  },
                  liveAreaIndexFocusRestoreRequest = liveAreaIndexFocusRestoreRequest,
                )
              } else if (tab == RootTab.MY) {
                MyScreen(
                  user = userInfo,
                  state = myState,
                  watchLaterState = watchLaterState,
                  onSection = { section ->
                    myViewModel.select(section)
                    if (section == dev.openbili.webdemo.my.MySection.WATCH_LATER) {
                      watchLaterViewModel.ensureLoaded()
                    }
                  },
                  onMarkSectionRead = myViewModel::markSectionRead,
                  onFolder = myViewModel::selectFolder,
                  onVideo = { item, bounds ->
                    val sourceBounds = bounds.takeUnless { it == Rect.Zero }
                    if (sourceBounds != null) myCardBounds[item.id] = sourceBounds
                    startEnterVideo(item, sourceBounds, VideoOrigin.MY)
                  },
                  onBangumi = { card, item, bounds ->
                    startHistoryBangumi(card, item, bounds)
                  },
                  onVideoLongClick = { showVideoPreview(it) },
                  onArticle = { article, bounds ->
                    val sourceBounds = bounds.takeUnless { it == Rect.Zero }
                    if (sourceBounds != null) myArticleBounds[article.stableId] = sourceBounds
                    startEnterArticle(article, sourceBounds, ArticleOrigin.MY)
                  },
                  onArticleBounds = { article, bounds ->
                    if (bounds.hasUsableSize()) myArticleBounds[article.stableId] = bounds
                  },
                  onLive = { room, bounds ->
                    val sourceBounds = bounds.takeUnless { it == Rect.Zero }
                    if (sourceBounds != null) myCardBounds[room.stableId] = sourceBounds
                    startEnterLive(liveTransitionContext, room, sourceBounds, PageOrigin.My)
                  },
                  onHistoryFilter = myViewModel::selectHistoryFilter,
                  onLoadMoreHistory = { period -> myViewModel.loadMoreHistory(period) },
                  onLoadHistoryThrough = { period -> myViewModel.loadHistoryThrough(period) },
                  onHistorySearch = myViewModel::searchHistory,
                  onLoadMoreHistorySearch = myViewModel::loadMoreHistorySearch,
                  onFavoriteQuery = myViewModel::setFavoriteQuery,
                  onLoadMoreFavorites = myViewModel::loadMoreFavorites,
                  onRemoveFavorite = myViewModel::removeFavorite,
                  onCopyFavorite = myViewModel::copyFavorite,
                  onMoveFavorite = myViewModel::moveFavorite,
                  onCreateFavoriteFolder = myViewModel::createFavoriteFolder,
                  onEditFavoriteFolder = myViewModel::editFavoriteFolder,
                  onDeleteFavoriteFolder = myViewModel::deleteFavoriteFolder,
                  hiddenCoverItemId = hiddenMyCoverItemId,
                  cachedVideosBackHandlingEnabled = !showVideo,
                  hiddenArticleItemId = hiddenMyArticleItemId,
                  hiddenInteractionTargetMessageId =
                    hiddenMyCoverItemId?.let(myInteractionVideoMessageIds::get)
                      ?: hiddenMyArticleItemId?.let(myInteractionArticleMessageIds::get),
                  onProfile = { person, bounds ->
                    if (myState.section == dev.openbili.webdemo.my.MySection.FOLLOWING) {
                      myViewModel.commitPendingUnfollows()
                    }
                    openAvatarProfile(person.mid, bounds, person.face, person.name)
                  },
                  onUnfollow = myViewModel::unfollow,
                  onFollowingQuery = myViewModel::setFollowingQuery,
                  onFollowingGroup = myViewModel::selectFollowingGroup,
                  onFollowingOrder = myViewModel::selectFollowingOrder,
                  onLoadMoreFollowings = myViewModel::loadMoreFollowings,
                  onRefresh = myViewModel::refresh,
                  onWatchLaterRefresh = watchLaterViewModel::refresh,
                  onLogin = { authViewModel.startLogin() },
                  profileIpAuthorized = profileIpAuthorized,
                  onAuthorizeProfileIp = authViewModel::startAppAuthorization,
                  onAccountClick = { bounds -> openAvatarProfile(userInfo.mid, bounds) },
                  onMessage = myViewModel::selectMessage,
                  onLoadMorePrivateSessions = myViewModel::loadMorePrivateSessions,
                  onLoadMorePrivateMessageHistory = myViewModel::loadMorePrivateMessageHistory,
                  onReplyMessage = myViewModel::replyToSelected,
                  onReplyPrivateMessage = { text, imageUri ->
                    myViewModel.replyToSelectedPrivate(context.applicationContext, text, imageUri)
                  },
                  onWithdrawPrivateMessage = myViewModel::withdrawPrivateMessage,
                  onDeletePrivateMessage = myViewModel::deletePrivateMessage,
                  onPrivateMessageProfile = { mid, face, name, bounds ->
                    openAvatarProfile(mid, bounds, face, name)
                  },
                  onPrivateMessageTarget = { message, bounds ->
                    openInteractionTarget(message, bounds)
                  },
                  onInteractionTarget = { message, bounds ->
                    openInteractionTarget(message, bounds)
                  },
                  onInteractionProfile = ::openCommentProfile,
                  onLoadMoreInteractions = myViewModel::loadMoreInteractions,
                  onLoadMoreLikes = myViewModel::loadMoreLikes,
                  onErrorConsumed = myViewModel::consumeError,
                  onWatchLaterErrorConsumed = watchLaterViewModel::consumeError,
                  hiddenInteractionCommentAvatarRpid =
                    commentProfileTransition
                      ?.takeIf { it.sourceAvatarBounds != null }
                      ?.sourceComment
                      ?.rpid,
                  settings = settings,
                  onSettingsChange = settingsViewModel::update,
                  onLogout = {
                    if (myState.section == dev.openbili.webdemo.my.MySection.FOLLOWING) {
                      myViewModel.commitPendingUnfollows()
                    }
                    authViewModel.logout()
                    Toast.makeText(context, "已经安全退出啦 (｡•̀ᴗ-)✧", Toast.LENGTH_SHORT).show()
                  },
                  rootPageVisible = rootTab == RootTab.MY,
                  controlInputEnabled =
                    rootTab == RootTab.MY &&
                      !showVideo &&
                      profileState.profileMid == null &&
                      activeArticleFrame == null &&
                      activeLiveRoom == null,
                  controlSecondLevelRequest = myController.secondLevelRequest,
                  controlLevel = myController.level,
                  onControlLevelChanged = { level ->
                    myController.level = level
                    if (controlMode && level == MyControlLevel.ROOT) {
                      scope.launch {
                        withFrameNanos {}
                        withFrameNanos {}
                        runCatching { controlInitialFocusRequester.requestFocus() }
                      }
                    }
                  },
                )
              } else {
                BangumiRecommendationScreen(
                  exploreViewModel = bangumiExploreViewModel,
                  controlSecondLevelRequest = bangumiControlSecondLevelRequest,
                  controlFocusRestoreRequest = bangumiControlFocusRestoreRequest,
                  controlLevel = bangumiControlLevel,
                  onControlLevelChanged = { level ->
                    bangumiControlLevel = level
                    if (controlMode && !showVideo && level == BangumiControlLevel.ROOT) {
                      scope.launch {
                        withFrameNanos {}
                        withFrameNanos {}
                        runCatching { controlInitialFocusRequester.requestFocus() }
                      }
                    }
                  },
                  // 索引是保留的覆盖层：它的 BackHandler 必须是唯一活动的，否则底层
                  // 探索页会先消费返回键并在索引关闭前塌回"本期推荐"。
                  active = bangumiRootPageActive && !showVideo && !showBangumiIndex,
                  preloadEnabled =
                    !bangumiStartupPreloadReady &&
                      !startupWarmupFadeInProgress &&
                      !rootPageSwitchInProgress &&
                      transitionPhase is TransitionPhase.Feed,
                  retainedForDetailReturn =
                    activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome,
                  previewMuted = bangumiPreviewMuted,
                  hiddenCardId = hiddenBangumiRecommendationItemId,
                  currentAccountMid = authUserInfo.mid,
                  state = bangumiRecommendationState,
                  onRefresh = { bangumiRecommendationViewModel.refresh() },
                  onSelect = { bangumiRecommendationViewModel.select(it) },
                  onRequireDetails = { bangumiRecommendationViewModel.ensureDetails(it) },
                  onRetryDetail = { bangumiRecommendationViewModel.retryDetail(it) },
                  onPreviewChanged = { target ->
                    bangumiPreviewTarget = target
                  },
                  onPreloadReady = { bangumiStartupPreloadReady = true },
                  onTogglePreviewMute = { bangumiPreviewMuted = !bangumiPreviewMuted },
                  onOpenMainEpisode = { card, item, bounds ->
                    if (!bangumiCardEnterPending && transitionPhase is TransitionPhase.Feed) {
                      bangumiCardEnterPending = true
                      playerViewModel.exoPlayer?.pause()
                      scope.launch {
                        try {
                          withFrameNanos {}
                          withFrameNanos {}
                          if (transitionPhase !is TransitionPhase.Feed) return@launch
                          playerViewModel.exoPlayer?.apply {
                            volume = 1f
                            repeatMode = Player.REPEAT_MODE_OFF
                          }
                          startRootBangumi(
                            card = card,
                            item = item,
                            cardBounds = bounds,
                            pageOrigin = PageOrigin.BangumiHome,
                            videoOrigin = VideoOrigin.BANGUMI,
                            restoreEpisodeSelection = false,
                            initialSeason = bangumiPreviewTarget?.season,
                          )
                        } finally {
                          bangumiCardEnterPending = false
                        }
                      }
                    }
                  },
                  onOpenExploreLandscape = { exploreItem, bounds ->
                    if (!bangumiCardEnterPending && transitionPhase is TransitionPhase.Feed) {
                      bangumiCardEnterPending = true
                      playerViewModel.exoPlayer?.pause()
                      scope.launch {
                        try {
                          val card =
                            SpaceContentCard(
                              id = "bangumi-explore-${exploreItem.stableId}",
                              title = exploreItem.title,
                              subtitle = exploreItem.subtitle,
                              coverUrl = exploreItem.coverUrl,
                              videoUrl = exploreItem.targetUrl,
                              seasonId = exploreItem.seasonId,
                              episodeId = exploreItem.episodeId,
                              kind = dev.openbili.webdemo.api.SpaceContentKind.BANGUMI,
                              watchProgress = exploreItem.watchProgress,
                              seasonType = exploreItem.seasonType,
                              hasHistory = exploreItem.hasHistory,
                              historicalOnly = exploreItem.historicalOnly,
                            )
                          val item =
                            FeedItem(
                              id = card.id,
                              title = card.title,
                              videoUrl = card.videoUrl,
                              coverUrl = card.coverUrl,
                              uploader = null,
                              playCount = null,
                              duration = null,
                              description = card.subtitle,
                            )
                          startRootBangumi(
                            card = card,
                            item = item,
                            cardBounds = bounds,
                            pageOrigin = PageOrigin.BangumiHome,
                            videoOrigin = VideoOrigin.BANGUMI,
                            restoreEpisodeSelection = false,
                            returnToSourceCover =
                              exploreItem.sectionKind == BangumiExploreSectionKind.HOT,
                          )
                        } finally {
                          bangumiCardEnterPending = false
                        }
                      }
                    }
                  },
                  onOpenExplorePoster = { exploreItem, bounds ->
                    if (transitionPhase is TransitionPhase.Feed) {
                      startSearchBangumi(
                        SpaceContentCard(
                          id = "bangumi-explore-${exploreItem.stableId}",
                          title = exploreItem.title,
                          subtitle = exploreItem.subtitle,
                          coverUrl = exploreItem.coverUrl,
                          videoUrl = exploreItem.targetUrl,
                          seasonId = exploreItem.seasonId,
                          episodeId = exploreItem.episodeId,
                          kind = dev.openbili.webdemo.api.SpaceContentKind.BANGUMI,
                          seasonType = exploreItem.seasonType,
                          hasHistory = exploreItem.hasHistory,
                          historicalOnly = exploreItem.historicalOnly,
                        ),
                        bounds,
                        sourceIsBangumiExplorePoster = true,
                      )
                    }
                  },
                  onOpenIndex = { bounds ->
                    bangumiControlLevel = BangumiControlLevel.INDEX_CONTROLS
                    openBangumiIndexAnimated(overlayTransitionContext, bounds)
                  },
                )
              }
            }
          }
          AnimatedVisibility(
            visible =
              isRootCapsuleVisible &&
                !showBangumiIndex &&
                !(rootTab == RootTab.HOME && homeDynamicDetailActive) &&
                !(rootTab == RootTab.HOME &&
                  homeRecommendationMode != HomeRecommendationMode.NORMAL),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter =
              slideInVertically(tween(if (settings.reduceMotion) 90 else 220)) { it } +
                fadeIn(tween(if (settings.reduceMotion) 90 else 180)),
            exit =
              slideOutVertically(tween(if (settings.reduceMotion) 90 else 220)) { it } +
                fadeOut(tween(if (settings.reduceMotion) 90 else 180)),
          ) {
            BottomCapsule(
              selected = rootTab,
              backdropLayer = rootBackdropLayer,
              onSelected = { tab -> animateToRootTab(tab) },
              onControlSelected = { tab ->
                if (controlMode && shouldEnterRootTabOnControlConfirm(rootTab, tab)) {
                  if (rootTab != tab) animateToRootTab(tab)
                  when (tab) {
                    RootTab.HOME -> homeControlSecondLevelRequest += 1
                    RootTab.BANGUMI -> bangumiControlSecondLevelRequest += 1
                    RootTab.MY -> myController.requestSections()
                  }
                } else {
                  animateToRootTab(tab)
                }
              },
              selectionPosition = {
                (rootPagerState.currentPage + rootPagerState.currentPageOffsetFraction).coerceIn(
                  0f,
                  RootTab.entries.lastIndex.toFloat(),
                )
              },
              onSelectionDrag = { position ->
                rootPageSwitchRequested = true
                val anchor = rootPagerAnchorForCapsulePosition(position)
                rootPagerState.requestScrollToPage(
                  page = anchor.page,
                  pageOffsetFraction = anchor.offsetFraction,
                )
              },
              onInteractionStart = {},
              onInteractionEnd = {},
              dragEnabled =
                !navigationLocked &&
                  searchTransitionDirection == null &&
                  bangumiIndexTransitionDirection == null &&
                  liveAreaIndex.direction == null &&
                  !showBangumiIndex &&
                  !liveAreaIndex.show,
              initialFocusRequester = controlInitialFocusRequester.takeIf { controlMode },
              focusEnabled = isRootCapsuleFocusEnabled,
              modifier = Modifier.padding(bottom = 12.dp),
            )
          }
        }
      }
    }
  }
}

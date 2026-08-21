package dev.openbili.webdemo.ui

/**
 * 根组合体的派生 UI 值：把各页面可见性、转场会话与内容延迟标志集中计算，供 AppRoot
 * 与各图层消费。拆出独立文件是为了控制 AppRoot 的规模并保持计算可测试。
 */

import dev.openbili.webdemo.AppUiState
import dev.openbili.webdemo.article.ArticleStackFrame
import dev.openbili.webdemo.article.ArticleTransitionSession
import kotlinx.coroutines.Job

/** 根组合体每次组合时计算的派生 UI 值集合。 */
internal data class AppRootUiValues(
  val showFeed: Boolean,
  val showVideo: Boolean,
  val activeSession: CardTransitionSession?,
  val activeArticleFrame: ArticleStackFrame?,
  val transitionVisualsActive: Boolean,
  val waitingForFirstFrame: Boolean,
  val navigationLocked: Boolean,
  val liveWaitingForFirstFrame: Boolean,
  val interactionTransitionActive: Boolean,
  val preparingRootEnter: Boolean,
  val deferVideoAuxiliaryContent: Boolean,
  val deferVideoCommentContent: Boolean,
  val rootEnterSession: CardTransitionSession?,
  val profileEnterSession: CardTransitionSession?,
  val bangumiHomeTransitionSession: CardTransitionSession?,
  val bangumiDetailPlayerSuppressed: Boolean,
  val rootPlayerHostEnabled: Boolean,
  val searchBangumiSession: CardTransitionSession?,
  val searchBangumiExitPrelude: Boolean,
  val searchBangumiSourceAboveVideo: Boolean,
  val feedLayerAlpha: Float,
)

/** 由当前状态推导全部派生 UI 值。 */
internal fun computeAppRootUiValues(
  appState: AppUiState,
  transitionPhase: TransitionPhase,
  transitionSession: CardTransitionSession?,
  articleStack: List<ArticleStackFrame>,
  videoExitPrelude: VideoExitPrelude?,
  bangumiCardEnterPending: Boolean,
  articleTransitionSession: ArticleTransitionSession?,
  liveTransitionSession: CardTransitionSession?,
  musicEntryInputLocked: Boolean,
  rootPageSwitchRequested: Boolean,
  directHomeInProgress: Boolean,
  searchTransitionDirection: SearchTransitionDirection?,
  bangumiIndexTransitionDirection: SearchTransitionDirection?,
  liveAreaIndex: LiveAreaIndexTransitionState,
  liveTransitionJob: Job?,
  liveExitPrelude: VideoExitPrelude?,
  homeLivePreludeActive: Boolean,
  liveFullscreenTransitionActive: Boolean,
  videoFullscreenTransitionActive: Boolean,
  profileVideoTransitionActive: Boolean,
  commentProfileTransition: CommentProfileTransition?,
  avatarProfileTransition: AvatarProfileTransition?,
  activeBangumiPage: ActiveBangumiPage?,
  startupWarmupVisible: Boolean,
  bangumiRootPageActive: Boolean,
): AppRootUiValues {
  val showFeed = true
  val showVideo = appState.selectedVideo != null && transitionPhase !is TransitionPhase.Feed
  val activeSession = transitionSession
  val activeArticleFrame = articleStack.lastOrNull()
  val transitionVisualsActive =
    videoExitPrelude != null ||
      activeSession != null ||
      (transitionPhase !is TransitionPhase.Feed && transitionPhase !is TransitionPhase.Video)
  val waitingForFirstFrame = activeSession?.phase == SessionPhase.WAITING_FIRST_FRAME
  val navigationLocked =
    !waitingForFirstFrame &&
      (bangumiCardEnterPending ||
        videoExitPrelude != null ||
        activeSession != null ||
        articleTransitionSession != null ||
        (transitionPhase !is TransitionPhase.Feed && transitionPhase !is TransitionPhase.Video))
  val liveWaitingForFirstFrame =
    liveTransitionSession?.let { session ->
      session.phase == SessionPhase.REVEALING &&
        session.kind in
          setOf(TransitionKind.ENTER_ROOT, TransitionKind.ENTER_RECOMMENDATION)
    } == true
  val interactionTransitionActive =
    navigationLocked ||
      musicEntryInputLocked ||
      rootPageSwitchRequested ||
      directHomeInProgress ||
      searchTransitionDirection != null ||
      bangumiIndexTransitionDirection != null ||
      liveAreaIndex.direction != null ||
      (liveTransitionJob != null && !liveWaitingForFirstFrame) ||
      (liveTransitionSession != null && !liveWaitingForFirstFrame) ||
      liveExitPrelude != null ||
      homeLivePreludeActive ||
      liveFullscreenTransitionActive ||
      videoFullscreenTransitionActive ||
      profileVideoTransitionActive ||
      profileTransitionInputLocked(
        activeCommentTransitionBlocksInput = commentProfileTransition?.blocksInput == true,
        activeAvatarTransition = avatarProfileTransition != null,
      )
  val preparingRootEnter =
    (transitionPhase as? TransitionPhase.ToVideo)?.let { !it.fromVideo && activeSession == null } ==
      true
  val deferVideoAuxiliaryContent =
    shouldDeferVideoAuxiliaryContent(
      preparingRootEnter = preparingRootEnter,
      kind = activeSession?.kind,
      phase = activeSession?.phase,
    )
  val deferVideoCommentContent =
    shouldDeferVideoCommentContent(
      deferAllAuxiliaryContent = deferVideoAuxiliaryContent,
      kind = activeSession?.kind,
      deferRootEnterComments = activeSession?.deferRootEnterComments == true,
    )
  val rootEnterSession = activeSession?.takeIf { it.kind == TransitionKind.ENTER_ROOT }
  val profileEnterSession = activeSession?.takeIf { it.kind == TransitionKind.ENTER_PROFILE }
  val bangumiHomeTransitionSession = activeSession?.takeIf {
    activeBangumiPage?.sourceOrigin == PageOrigin.BangumiHome
  }
  val bangumiDetailPlayerSuppressed =
    bangumiHomeTransitionSession?.let { session ->
      shouldSuppressDetailPlayerForBangumiCardTransition(session.kind, session.phase)
    } == true
  val rootPlayerHostEnabled =
    shouldUseRootPlayerHost(
      startupWarmupVisible = startupWarmupVisible,
      bangumiRootPageActive = bangumiRootPageActive,
      hasBangumiHomeTransition = false,
    )
  val searchBangumiSession = activeSession?.takeIf {
    activeBangumiPage?.sourceOrigin in setOf(PageOrigin.Search, PageOrigin.BangumiIndex) &&
      (it.kind == TransitionKind.ENTER_ROOT || it.kind == TransitionKind.EXIT_ROOT)
  }
  val searchBangumiExitPrelude =
    activeBangumiPage?.sourceOrigin in setOf(PageOrigin.Search, PageOrigin.BangumiIndex) &&
      videoExitPrelude != null
  val searchBangumiSourceAboveVideo =
    when (searchBangumiSession?.kind) {
      TransitionKind.ENTER_ROOT ->
        searchBangumiSession.phase !in
          setOf(
            SessionPhase.REVEALING_BACKGROUND,
            SessionPhase.WAITING_FIRST_FRAME,
            SessionPhase.REVEALING,
            SessionPhase.COMPLETED,
          )
      TransitionKind.EXIT_ROOT -> true
      else -> false
    }
  val feedLayerAlpha =
    when {
      !showVideo || directHomeInProgress || preparingRootEnter -> 1f
      searchBangumiSession?.kind == TransitionKind.ENTER_ROOT ->
        1f - searchBangumiSession.backgroundAlpha.value.coerceIn(0f, 1f)
      searchBangumiSession?.kind == TransitionKind.EXIT_ROOT ->
        searchBangumiSession.panelAlpha.value.coerceIn(0f, 1f)
      searchBangumiExitPrelude -> 1f
      rootEnterSession != null || activeSession?.kind == TransitionKind.EXIT_ROOT -> 1f
      else -> 0f
    }
  return AppRootUiValues(
    showFeed = showFeed,
    showVideo = showVideo,
    activeSession = activeSession,
    activeArticleFrame = activeArticleFrame,
    transitionVisualsActive = transitionVisualsActive,
    waitingForFirstFrame = waitingForFirstFrame,
    navigationLocked = navigationLocked,
    liveWaitingForFirstFrame = liveWaitingForFirstFrame,
    interactionTransitionActive = interactionTransitionActive,
    preparingRootEnter = preparingRootEnter,
    deferVideoAuxiliaryContent = deferVideoAuxiliaryContent,
    deferVideoCommentContent = deferVideoCommentContent,
    rootEnterSession = rootEnterSession,
    profileEnterSession = profileEnterSession,
    bangumiHomeTransitionSession = bangumiHomeTransitionSession,
    bangumiDetailPlayerSuppressed = bangumiDetailPlayerSuppressed,
    rootPlayerHostEnabled = rootPlayerHostEnabled,
    searchBangumiSession = searchBangumiSession,
    searchBangumiExitPrelude = searchBangumiExitPrelude,
    searchBangumiSourceAboveVideo = searchBangumiSourceAboveVideo,
    feedLayerAlpha = feedLayerAlpha,
  )
}

package dev.openbili.webdemo.ui

/**
 * 专栏导航上下文：专栏详情加载、文章关系栈、评论定位与文章↔视频转场，是 AppRoot
 * 拆分出的专栏逻辑归宿。
 */

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import dev.openbili.webdemo.AppUiState
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.ArticleDetail
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliArticleApi
import dev.openbili.webdemo.api.BiliVideoApi
import dev.openbili.webdemo.api.CommentNavigationTarget
import dev.openbili.webdemo.api.MessageTargetKind
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.article.ArticleOrigin
import dev.openbili.webdemo.article.ArticleStackFrame
import dev.openbili.webdemo.article.ArticleTransitionSession
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.MainViewModel
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.PreferredResolutionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 专栏导航上下文。
 */
internal class AppRootArticleContext(
  // 共享环境
  val context: Context,
  val scope: CoroutineScope,
  val playerViewModel: PlayerViewModel,
  val mainViewModel: MainViewModel,
  val videoEntryCache: LinkedHashMap<String, VideoPageEntry>,
  val articlePageAlpha: Animatable<Float, AnimationVector1D>,
  val homeDynamicArticleBounds: SnapshotStateMap<String, Rect>,
  val profileArticleBounds: SnapshotStateMap<String, Rect>,
  val myCardBounds: SnapshotStateMap<String, Rect>,
  val searchArticleBounds: SnapshotStateMap<String, Rect>,
  val myArticleBounds: SnapshotStateMap<String, Rect>,
  val myInteractionVideoMessageIds: SnapshotStateMap<String, Long>,
  val myInteractionArticleMessageIds: SnapshotStateMap<String, Long>,
  val articleDetailCache: SnapshotStateMap<Long, ArticleDetail>,
  // 收集到的 UI 状态
  val appStateState: State<AppUiState>,
  val settingsState: State<AppSettings>,
  val playerStateState: State<PlayerState>,
  // 转场状态
  val transitionPhaseState: MutableState<TransitionPhase>,
  val transitionSessionState: MutableState<CardTransitionSession?>,
  val articleTransitionSessionState: MutableState<ArticleTransitionSession?>,
  // 文章与视频页状态
  val playerActivationIdState: MutableState<String?>,
  val hiddenProfileArticleItemIdState: MutableState<String?>,
  val showEmbeddedCoverState: MutableState<Boolean>,
  val articleContentReadyState: MutableState<Boolean>,
  val articleDetailState: MutableState<ArticleDetail?>,
  val videoStackState: MutableState<List<StackFrame>>,
  val hiddenHomeDynamicArticleItemIdState: MutableState<String?>,
  val profileLayerSuppressedState: MutableState<Boolean>,
  val pendingVideoCommentTargetState: MutableState<CommentNavigationTarget?>,
  val articleStackState: MutableState<List<ArticleStackFrame>>,
  val interactionTargetLoadingIdState: MutableState<Long?>,
  val hiddenMyArticleItemIdState: MutableState<String?>,
  val dataCommitAllowedIdState: MutableState<String?>,
  val hiddenSearchArticleItemIdState: MutableState<String?>,
  val pendingArticleCommentTargetState: MutableState<CommentNavigationTarget?>,
  val articleSuspendedVideoState: MutableState<SuspendedArticleVideo?>,
  val hiddenVideoCommentArticleItemIdState: MutableState<String?>,
  val commentNavigationRequestTokenState: MutableState<Long>,
  val articleTransitionJobState: MutableState<Job?>,
  val hiddenArticleCommentArticleItemIdState: MutableState<String?>,
  val articleErrorState: MutableState<String?>,
  val articleRestoringParentEntryIdState: MutableState<Long?>,
  val articleHeroBoundsState: MutableState<Rect>,
  val articleLoadTokenState: MutableState<Long>,
  val articleLoadingState: MutableState<Boolean>,
  val articleEntryTokenState: MutableState<Long>,
  // 来自 AppRoot 的委托辅助函数
  val startEnterVideoRef: (FeedItem, Rect?, VideoOrigin, CommentNavigationTarget?) -> Unit,
  val startProfileVideoRef: (Long, FeedItem, Rect) -> Unit,
  val retainedPlaybackPageRef: (String) -> VideoPage?,
  val commitPlaybackProgressRef: () -> Unit,
  val cacheEntryRef: (VideoPageEntry) -> Unit,
  val clearVisibleVideoDataRef: () -> Unit,
  val snapshotEntryRef: (FeedItem) -> VideoPageEntry,
  val currentPreferredResolutionModeRef: () -> PreferredResolutionMode,
  val restoreEntryRef: (VideoPageEntry) -> Unit,
) {
  val appState by appStateState
  val settings by settingsState
  val playerState by playerStateState
  var transitionPhase by transitionPhaseState
  var transitionSession by transitionSessionState
  var articleTransitionSession by articleTransitionSessionState
  var playerActivationId by playerActivationIdState
  var hiddenProfileArticleItemId by hiddenProfileArticleItemIdState
  var showEmbeddedCover by showEmbeddedCoverState
  var articleContentReady by articleContentReadyState
  var articleDetail by articleDetailState
  var videoStack by videoStackState
  var hiddenHomeDynamicArticleItemId by hiddenHomeDynamicArticleItemIdState
  var profileLayerSuppressed by profileLayerSuppressedState
  var pendingVideoCommentTarget by pendingVideoCommentTargetState
  var articleStack by articleStackState
  var interactionTargetLoadingId by interactionTargetLoadingIdState
  var hiddenMyArticleItemId by hiddenMyArticleItemIdState
  var dataCommitAllowedId by dataCommitAllowedIdState
  var hiddenSearchArticleItemId by hiddenSearchArticleItemIdState
  var pendingArticleCommentTarget by pendingArticleCommentTargetState
  var articleSuspendedVideo by articleSuspendedVideoState
  var hiddenVideoCommentArticleItemId by hiddenVideoCommentArticleItemIdState
  var commentNavigationRequestToken by commentNavigationRequestTokenState
  var articleTransitionJob by articleTransitionJobState
  var hiddenArticleCommentArticleItemId by hiddenArticleCommentArticleItemIdState
  var articleError by articleErrorState
  var articleRestoringParentEntryId by articleRestoringParentEntryIdState
  var articleHeroBounds by articleHeroBoundsState
  var articleLoadToken by articleLoadTokenState
  var articleLoading by articleLoadingState
  var articleEntryToken by articleEntryTokenState

  fun startEnterVideo(
    item: FeedItem,
    cardBounds: Rect?,
    origin: VideoOrigin = VideoOrigin.OTHER,
    commentTarget: CommentNavigationTarget? = null,
  ) = startEnterVideoRef(item, cardBounds, origin, commentTarget)

  fun startProfileVideo(profileEntryId: Long, item: FeedItem, cardBounds: Rect) =
    startProfileVideoRef(profileEntryId, item, cardBounds)

  fun retainedPlaybackPage(itemId: String): VideoPage? = retainedPlaybackPageRef(itemId)

  fun commitPlaybackProgress() = commitPlaybackProgressRef()

  fun cacheEntry(entry: VideoPageEntry) = cacheEntryRef(entry)

  fun clearVisibleVideoData() = clearVisibleVideoDataRef()

  fun snapshotEntry(item: FeedItem): VideoPageEntry = snapshotEntryRef(item)

  fun currentPreferredResolutionMode(): PreferredResolutionMode = currentPreferredResolutionModeRef()

  fun restoreEntry(entry: VideoPageEntry) = restoreEntryRef(entry)

  fun loadArticleDetail(article: ArticleItem) {
    val token = ++articleLoadToken
    articleLoading = true
    articleError = null
    articleDetail = null
    scope.launch {
      runCatching { withContext(Dispatchers.IO) { BiliArticleApi.getArticleDetail(article) } }
        .onSuccess { detail ->
          if (token == articleLoadToken && articleStack.lastOrNull()?.article?.id == article.id) {
            articleDetail = detail
            articleDetailCache[article.id] = detail
            articleLoading = false
            scope.launch(Dispatchers.IO) { BiliArticleApi.reportArticleRead(detail.commentOid) }
          }
        }
        .onFailure { error ->
          if (token == articleLoadToken && articleStack.lastOrNull()?.article?.id == article.id) {
            articleError = error.message ?: "专栏正文加载失败"
            articleLoading = false
          }
        }
    }
  }

  suspend fun awaitStableArticleHeroBounds(): Rect {
    var previous = Rect.Zero
    var stableFrames = 0
    repeat(30) {
      withFrameNanos {}
      val current = articleHeroBounds
      if (current.hasUsableSize() && current.approximatelyEquals(previous, tolerancePx = 1.5f)) {
        stableFrames++
        if (stableFrames >= 2) return current
      } else {
        stableFrames = 0
      }
      previous = current
    }
    return articleHeroBounds
  }

  fun articleSourceBounds(frame: ArticleStackFrame): Rect? =
    when (frame.origin) {
      ArticleOrigin.HOME_DYNAMIC -> homeDynamicArticleBounds[frame.article.stableId]
      ArticleOrigin.MY -> myArticleBounds[frame.article.stableId]
      ArticleOrigin.SEARCH -> searchArticleBounds[frame.article.stableId]
      ArticleOrigin.PROFILE -> profileArticleBounds[frame.article.stableId]
      ArticleOrigin.VIDEO -> null
      ArticleOrigin.ARTICLE -> null
    } ?: frame.sourceBounds

  fun hideArticleSource(frame: ArticleStackFrame, hidden: Boolean) {
    when (frame.origin) {
      ArticleOrigin.HOME_DYNAMIC ->
        hiddenHomeDynamicArticleItemId = frame.article.stableId.takeIf { hidden }
      ArticleOrigin.MY -> hiddenMyArticleItemId = frame.article.stableId.takeIf { hidden }
      ArticleOrigin.SEARCH -> hiddenSearchArticleItemId = frame.article.stableId.takeIf { hidden }
      ArticleOrigin.PROFILE -> hiddenProfileArticleItemId = frame.article.stableId.takeIf { hidden }
      ArticleOrigin.VIDEO ->
        hiddenVideoCommentArticleItemId = frame.article.stableId.takeIf { hidden }
      ArticleOrigin.ARTICLE ->
        hiddenArticleCommentArticleItemId = frame.article.stableId.takeIf { hidden }
    }
  }

  fun suspendVideoForArticle(): Boolean {
    val current = appState.selectedVideo ?: return true
    if (transitionSession != null || transitionPhase !is TransitionPhase.Video) return false
    cacheEntry(snapshotEntry(current))
    commitPlaybackProgress()
    val wasPlaying = playerViewModel.exoPlayer?.isPlaying == true
    val playerWasReady = playerState is dev.openbili.webdemo.PlayerState.Ready
    playerViewModel.exoPlayer?.pause()
    playerViewModel.cancelPendingLoad()
    articleSuspendedVideo =
      SuspendedArticleVideo(
        item = current,
        stack =
          videoStack.ifEmpty { listOf(StackFrame(current.id, current, PageOrigin.Other, null)) },
        retainedArticleDepth = articleStack.size,
        wasPlaying = wasPlaying,
        playerWasReady = playerWasReady,
      )
    dataCommitAllowedId = null
    playerActivationId = null
    return true
  }

  fun restoreVideoSuspendedByArticle() {
    val suspended = articleSuspendedVideo ?: return
    if (appState.selectedVideo?.id != suspended.item.id) {
      val retained = videoEntryCache[suspended.item.id]
      if (retained != null) restoreEntry(retained) else clearVisibleVideoData()
      videoStack = suspended.stack
      mainViewModel.openVideo(suspended.item)
    }
    dataCommitAllowedId = suspended.item.id
    playerActivationId = suspended.item.id
    transitionPhase =
      TransitionPhase.Video(suspended.item, suspended.stack.lastOrNull()?.sourceCardBounds)
    articleSuspendedVideo = null
    if (suspended.playerWasReady) {
      showEmbeddedCover = false
      if (suspended.wasPlaying) playerViewModel.exoPlayer?.play()
      else playerViewModel.exoPlayer?.pause()
    } else {
      val retained = videoEntryCache[suspended.item.id]
      showEmbeddedCover = true
      playerViewModel.loadVideo(
        suspended.item,
        startPositionMs = retained?.savedPositionMs ?: 0L,
        preferredStreamIndex = retained?.qualityIndex,
        preferredResolutionMode = currentPreferredResolutionMode(),
        page = retainedPlaybackPage(suspended.item.id),
      )
    }
  }

  fun startEnterArticle(
    article: ArticleItem,
    sourceBounds: Rect?,
    origin: ArticleOrigin,
    commentTarget: CommentNavigationTarget? = null,
  ) {
    if (transitionSession != null || articleTransitionSession != null) return
    pendingArticleCommentTarget = commentTarget
    val nested = articleStack.isNotEmpty()
    if (origin == ArticleOrigin.VIDEO) {
      if (articleSuspendedVideo == null && !suspendVideoForArticle()) return
    } else if (!nested && !suspendVideoForArticle()) {
      return
    }
    articleDetail?.let { current ->
      articleStack.lastOrNull()?.article?.id?.let { articleDetailCache[it] = current }
    }
    val frame = ArticleStackFrame(++articleEntryToken, article, origin, sourceBounds)
    val expandedStack = if (nested) articleStack + frame else listOf(frame)
    articleStack =
      if (expandedStack.size <= MAX_ARTICLE_STACK_DEPTH) expandedStack
      else {
        val inheritedRoot = expandedStack.first()
        articleDetailCache.remove(inheritedRoot.article.id)
        expandedStack.drop(1).mapIndexed { index, retained ->
          if (index == 0) retained.copy(origin = inheritedRoot.origin, sourceBounds = null)
          else retained
        }
      }
    articleHeroBounds = Rect.Zero
    articleContentReady = false
    articleRestoringParentEntryId = null
    articleLoading = false
    articleError = null
    articleDetail = null
    articleTransitionJob?.cancel()
    articleTransitionJob = scope.launch {
      articlePageAlpha.snapTo(0f)
      val target = awaitStableArticleHeroBounds()
      val source = articleSourceBounds(frame)
      if (source?.hasUsableSize() == true && target.hasUsableSize()) {
        val session = ArticleTransitionSession(article, source, target, initialProgress = 0f)
        articleTransitionSession = session
        hideArticleSource(frame, hidden = true)
        withFrameNanos {}
        if (origin == ArticleOrigin.PROFILE) {
          profileLayerSuppressed = true
          withFrameNanos {}
        }
        coroutineScope {
          launch {
            session.progress.animateTo(
              1f,
              tween(if (settings.reduceMotion) 130 else 380, easing = FastOutSlowInEasing),
            )
          }
          launch {
            delay(if (settings.reduceMotion) 0 else 45)
            articlePageAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 100 else 280, easing = FastOutSlowInEasing),
            )
          }
        }
        withFrameNanos {}
        articleTransitionSession = null
        hideArticleSource(frame, hidden = false)
      } else {
        if (origin == ArticleOrigin.PROFILE) {
          profileLayerSuppressed = true
          withFrameNanos {}
        }
        articlePageAlpha.animateTo(
          1f,
          tween(if (settings.reduceMotion) 100 else 220, easing = FastOutSlowInEasing),
        )
      }
      if (articleStack.lastOrNull() == frame) {
        articleContentReady = true
        loadArticleDetail(article)
      }
    }
  }

  fun openInteractionTarget(
    message: AccountMessage,
    sourceBounds: Rect,
    profileEntryId: Long? = null,
  ) {
    val sourcePageActive =
      transitionPhase is TransitionPhase.Feed ||
        (profileEntryId != null && transitionPhase is TransitionPhase.Video)
    if (interactionTargetLoadingId != null || !sourcePageActive) return
    interactionTargetLoadingId = message.id
    scope.launch {
      try {
        val rootRpid = message.rootId.takeIf { it > 0L } ?: message.targetCommentId
        // 仅媒体的私享与点赞通知没有回复楼语义：把它们的零 ID 传入评论导航会让
        // 目的地显示"此条评论被删除"。
        val target =
          if (message.isPrivate || rootRpid <= 0L) null
          else
            CommentNavigationTarget(
              oid = message.oid,
              type = message.commentType,
              rootRpid = rootRpid,
              targetRpid = message.targetCommentId.takeIf { it > 0L } ?: rootRpid,
              requestId = ++commentNavigationRequestToken,
            )
        if (message.targetKind == MessageTargetKind.ARTICLE) {
          val articleId =
            Regex("(?:read/cv|opus/|article/)(\\d+)", RegexOption.IGNORE_CASE)
              .find(message.linkUrl)
              ?.groupValues
              ?.getOrNull(1)
              ?.toLongOrNull() ?: message.oid
          if (articleId <= 0L) throw IllegalStateException("无法解析这条专栏消息")
          val sourceUrl =
            message.linkUrl
              .takeIf {
                it.startsWith("https://", ignoreCase = true) ||
                  it.startsWith("http://", ignoreCase = true)
              }
              .orEmpty()
          val articleStableId = "article:$articleId"
          if (profileEntryId == null) {
            myInteractionArticleMessageIds[articleStableId] = message.id
            if (sourceBounds.hasUsableSize()) myArticleBounds[articleStableId] = sourceBounds
          } else if (sourceBounds.hasUsableSize()) {
            profileArticleBounds[articleStableId] = sourceBounds
          }
          startEnterArticle(
            ArticleItem(
              id = articleId,
              title = message.subjectTitle.ifBlank { "专栏" },
              coverUrl = message.coverUrl,
              sourceUrl = sourceUrl,
            ),
            sourceBounds.takeIf(Rect::hasUsableSize),
            if (profileEntryId == null) ArticleOrigin.MY else ArticleOrigin.PROFILE,
            target,
          )
        } else {
          val bvid =
            Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE).find(message.linkUrl)?.value
          val linkedAid =
            Regex("(?:/av|video/av|video/)(\\d+)", RegexOption.IGNORE_CASE)
              .find(message.linkUrl)
              ?.groupValues
              ?.getOrNull(1)
              ?.toLongOrNull()
          val info =
            withContext(Dispatchers.IO) {
              if (!bvid.isNullOrBlank()) BiliVideoApi.getVideoInfo(bvid)
              else BiliVideoApi.getVideoInfoByAid(message.oid.takeIf { it > 0L } ?: linkedAid ?: 0L)
            }
          val resolvedBvid = info?.bvid ?: bvid
          if (resolvedBvid.isNullOrBlank()) throw IllegalStateException("无法解析这条视频消息")
          val item =
            FeedItem(
              id = resolvedBvid,
              title = info?.title ?: message.subjectTitle.ifBlank { "视频" },
              videoUrl = "https://www.bilibili.com/video/$resolvedBvid",
              coverUrl = info?.coverUrl ?: message.coverUrl,
              uploader = info?.uploaderName,
              playCount = info?.let { FeedViewModel.formatCount(it.playCount) },
              duration = info?.let { FeedViewModel.formatDuration(it.durationSeconds) },
              uploaderFace = info?.uploaderFace,
              uploaderMid = info?.uploaderMid ?: 0L,
              danmakuCount = info?.danmakuCount ?: 0L,
              publishedAt = info?.publishedAt ?: 0L,
              description = info?.desc.orEmpty(),
            )
          if (profileEntryId != null) {
            startProfileVideo(profileEntryId, item, sourceBounds)
          } else {
            myInteractionVideoMessageIds[item.id] = message.id
            if (sourceBounds.hasUsableSize()) myCardBounds[item.id] = sourceBounds
            startEnterVideo(
              item,
              sourceBounds.takeIf(Rect::hasUsableSize),
              VideoOrigin.MY,
              target?.copy(oid = info?.aid ?: target.oid),
            )
          }
        }
      } catch (error: Exception) {
        Toast.makeText(context, error.message ?: "无法打开这条互动消息", Toast.LENGTH_SHORT).show()
        pendingVideoCommentTarget = null
        pendingArticleCommentTarget = null
      } finally {
        interactionTargetLoadingId = null
      }
    }
  }

  fun startExitArticle() {
    val frame = articleStack.lastOrNull() ?: return
    articleContentReady = false
    articleLoadToken++
    val activeSession = articleTransitionSession
    articleTransitionJob?.cancel()
    articleTransitionJob = scope.launch {
      val session =
        activeSession
          ?: run {
            val source = articleSourceBounds(frame)
            val target = articleHeroBounds
            if (source?.hasUsableSize() == true && target.hasUsableSize()) {
              ArticleTransitionSession(
                  articleDetail?.article ?: frame.article,
                  source,
                  target,
                  initialProgress = 1f,
                )
                .also {
                  articleTransitionSession = it
                }
            } else null
          }
      if (session != null) {
        articleTransitionSession = session
        session.endBounds = articleHeroBounds.takeIf(Rect::hasUsableSize) ?: session.endBounds
        hideArticleSource(frame, hidden = true)
        withFrameNanos {}
        articlePageAlpha.animateTo(
          0f,
          tween(if (settings.reduceMotion) 80 else 170, easing = FastOutSlowInEasing),
        )
        if (frame.origin == ArticleOrigin.PROFILE) {
          // 在保留资料页重新挂载期间，保持一张静止 16:9 主图在交接层之上。
          // 目的地卡片已隐藏，所以只有飞行封面可见。
          profileLayerSuppressed = false
          repeat(2) { withFrameNanos {} }
        }
        session.startBounds = articleSourceBounds(frame) ?: session.startBounds
        session.progress.animateTo(
          0f,
          tween(if (settings.reduceMotion) 120 else 330, easing = FastOutSlowInEasing),
        )
      } else {
        articlePageAlpha.animateTo(
          0f,
          tween(if (settings.reduceMotion) 90 else 190, easing = FastOutSlowInEasing),
        )
        if (frame.origin == ArticleOrigin.PROFILE) profileLayerSuppressed = false
      }
      val remainingStack = articleStack.dropLast(1)
      val returningToSuspendedVideo =
        isReturningToSuspendedVideo(
          retainedArticleDepth = articleSuspendedVideo?.retainedArticleDepth,
          remainingArticleDepth = remainingStack.size,
        )
      val retainedArticle = remainingStack.lastOrNull()
      val parent = retainedArticle.takeUnless { returningToSuspendedVideo }
      val restoredParentDetail = retainedArticle?.let { articleDetailCache[it.article.id] }
      if (returningToSuspendedVideo) {
        articleDetail = restoredParentDetail
        articleLoading = retainedArticle != null && restoredParentDetail == null
        articleError = null
        articleContentReady = restoredParentDetail != null
        articleRestoringParentEntryId = null
      } else if (parent == null) {
        articleDetail = null
        articleLoading = false
        articleError = null
        articleContentReady = false
        articleRestoringParentEntryId = null
      } else {
        // 在保留父页成为顶层组合前提交其数据：哪怕只把父条目与离开的子详情配对
        // 一帧，都会重置评论并让保留的 LazyColumn 看起来跳动。
        articleDetail = restoredParentDetail
        articleLoading = restoredParentDetail == null
        articleError = null
        articleContentReady = restoredParentDetail != null
        articleRestoringParentEntryId = parent.entryId
      }
      articleStack = remainingStack
      articleLoadToken++
      hideArticleSource(frame, hidden = false)
      articleTransitionSession = null
      articleHeroBounds = Rect.Zero
      if (returningToSuspendedVideo) {
        articlePageAlpha.snapTo(if (remainingStack.isEmpty()) 0f else 1f)
        restoreVideoSuspendedByArticle()
      } else if (parent == null) {
        articlePageAlpha.snapTo(0f)
      } else {
        articlePageAlpha.snapTo(1f)
        articleRestoringParentEntryId = null
        if (restoredParentDetail == null) {
          articleContentReady = true
          loadArticleDetail(parent.article)
        }
      }
    }
  }
}

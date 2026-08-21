package dev.openbili.webdemo.ui

/**
 * 资料页导航上下文：空间资料加载、资料关系栈（最多 8 层）、评论/头像来源转场与
 * 资料页返回视频的恢复逻辑。
 */

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import coil3.imageLoader
import coil3.request.ImageRequest
import dev.openbili.webdemo.AppUiState
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.article.ArticleOrigin
import dev.openbili.webdemo.article.ArticleStackFrame
import dev.openbili.webdemo.resolvePlaybackPage
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.PreferredResolutionMode
import dev.openbili.webdemo.video.CommentProfileAnchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class AppRootProfileContext(
  val context: Context,
  val scope: CoroutineScope,
  val profileState: AppRootProfileState,
  val playerViewModel: PlayerViewModel,
  val videoEntryCache: LinkedHashMap<String, VideoPageEntry>,
  val appStateState: State<AppUiState>,
  val settingsState: State<AppSettings>,
  val authUserInfoState: State<UserInfo>,
  val profileIpAuthorizedState: State<Boolean>,
  val articleStackState: MutableState<List<ArticleStackFrame>>,
  val profileLayerSuppressedState: MutableState<Boolean>,
  val transitionPhaseState: MutableState<TransitionPhase>,
  val playerActivationIdState: MutableState<String?>,
  val dataCommitAllowedIdState: MutableState<String?>,
  val profileEntryTokenState: MutableState<Long>,
  val profileStackState: MutableState<List<ProfileStackEntry>>,
  val myCardBounds: MutableMap<String, Rect>,
  val currentPreferredResolutionModeRef: () -> PreferredResolutionMode,
) {
  val appState by appStateState
  val settings by settingsState
  val authUserInfo by authUserInfoState
  val profileIpAuthorized by profileIpAuthorizedState
  var articleStack by articleStackState
  var profileLayerSuppressed by profileLayerSuppressedState
  var transitionPhase by transitionPhaseState
  var playerActivationId by playerActivationIdState
  var dataCommitAllowedId by dataCommitAllowedIdState
  var profileEntryToken by profileEntryTokenState
  var profileStack by profileStackState
  var profileMid by profileState::profileMid
  var profileAvatarBounds by profileState::profileAvatarBounds
  var commentProfileTransition by profileState::commentProfileTransition
  var commentProfileReturnTransition by profileState::commentProfileReturnTransition
  var avatarProfileTransition by profileState::avatarProfileTransition
  var avatarProfileReturnTransition by profileState::avatarProfileReturnTransition
  var profileTransitionJob by profileState::profileTransitionJob
  var spaceProfile by profileState::spaceProfile

  fun currentPreferredResolutionMode(): PreferredResolutionMode = currentPreferredResolutionModeRef()

  fun loadSpacePage(mid: Long, page: Int) = profileState.loadSpacePage(mid, page, scope)

  fun loadSpaceDynamics(refresh: Boolean) {
    profileMid?.let { profileState.loadSpaceDynamics(it, refresh, scope) }
  }

  fun newProfileEntry(
    state: AppRootProfileState,
    commentTransition: CommentProfileTransition? = null,
    avatarTransition: AvatarProfileTransition? = null,
    returnsToVideo: Boolean = false,
    returnsToArticle: Boolean = false,
  ): ProfileStackEntry =
    ProfileStackEntry(
      entryId = ++profileEntryToken,
      state = state,
      commentTransition = commentTransition,
      avatarTransition = avatarTransition,
      returnsToVideo = returnsToVideo,
      returnsToArticle = returnsToArticle,
    )

  fun prepareProfile(mid: Long, initialProfile: dev.openbili.webdemo.api.SpaceProfile? = null) {
    profileState.prepareProfile(mid, initialProfile) { playerViewModel.exoPlayer?.pause() }
    profileStack = listOf(newProfileEntry(profileState))
    profileLayerSuppressed = false
  }

  fun loadPreparedProfile(mid: Long) = profileState.loadPreparedProfile(mid, scope)

  fun loadProfile(mid: Long) {
    prepareProfile(mid)
    loadPreparedProfile(mid)
  }


  fun snapshotProfile(mid: Long) = profileState.snapshotProfile(mid)

  fun restoreProfile(entry: ProfilePageEntry) {
    profileState.restoreProfile(entry)
    if (profileStack.none { it.state === profileState }) {
      profileStack = listOf(newProfileEntry(profileState))
    }
    profileLayerSuppressed = false
  }

  fun activeProfileEntry(entryId: Long? = null): ProfileStackEntry? =
    if (entryId == null) profileStack.lastOrNull()
    else profileStack.firstOrNull { it.entryId == entryId }

  suspend fun prepareProfileTransition(
    barrier: TransitionPreparationBarrier,
    imageUrl: String? = null,
    timeoutMillis: Long = TRANSITION_PREPARE_TIMEOUT_MS,
    targetMounted: () -> Boolean = { profileMid != null },
    targetBounds: () -> Rect = { profileAvatarBounds },
  ): TransitionPreparationResult = coroutineScope {
    val boundsTracker = StableBoundsTracker()
    val imageJob =
      imageUrl?.takeIf(String::isNotBlank)?.let { url ->
        launch {
          runCatching {
              context.applicationContext.imageLoader
                .execute(
                  ImageRequest.Builder(context.applicationContext).data(url).size(96, 96).build()
                )
                .image
            }
            .getOrNull()
            ?.let { barrier.markReady(TransitionReadySignal.IMAGE_READY) }
        }
      }
    val readinessJob = launch {
      while (isActive && !barrier.isReady()) {
        withFrameNanos {}
        if (targetMounted()) barrier.markReady(TransitionReadySignal.TARGET_MOUNTED)
        val bounds = targetBounds()
        if (boundsTracker.observe(bounds)) {
          barrier.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
        }
      }
    }
    val result = barrier.await(timeoutMillis)
    readinessJob.cancelAndJoin()
    imageJob?.cancelAndJoin()
    result
  }

  suspend fun prepareBoundsTransition(
    barrier: TransitionPreparationBarrier,
    bounds: () -> Rect,
  ): TransitionPreparationResult = coroutineScope {
    val tracker = StableBoundsTracker()
    val readinessJob = launch {
      while (isActive && !barrier.isReady()) {
        withFrameNanos {}
        val current = bounds()
        if (current.hasUsableSize()) {
          barrier.markReady(TransitionReadySignal.TARGET_MOUNTED)
          if (tracker.observe(current)) {
            barrier.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
          }
        }
      }
    }
    val result = barrier.await()
    readinessJob.cancelAndJoin()
    result
  }

  fun openProfile(mid: Long) {
    profileTransitionJob?.cancel()
    commentProfileTransition = null
    commentProfileReturnTransition = null
    avatarProfileTransition = null
    avatarProfileReturnTransition = null
    loadProfile(mid)
  }

  fun openAvatarProfileFrom(
    sourceEntryId: Long?,
    mid: Long,
    bounds: Rect,
    face: String? = authUserInfo.face,
    name: String? = authUserInfo.name,
  ) {
    if (mid <= 0) return
    val sourceEntry = sourceEntryId?.let(::activeProfileEntry)
    val sourceState = sourceEntry?.state
    val returnsToVideo =
      sourceEntryId == null &&
        profileLayerSuppressed &&
        profileStack.isNotEmpty() &&
        transitionPhase is TransitionPhase.Video
    val returnsToArticle = sourceEntryId == null && articleStack.isNotEmpty()
    val retainedProfileEntry = profileStack.lastOrNull().takeIf { returnsToVideo }
    (sourceEntry ?: retainedProfileEntry)?.let { owner ->
      profileStack =
        profileStack.retainReturnTransitionsFor(
          owner.entryId,
          commentProfileReturnTransition,
          avatarProfileReturnTransition,
        )
    }
    // 横幅已经代表这个账号：在这里重放共享头像转场看起来像头像做了无意义的往返，
    // 而且并未执行导航。
    if (
      sourceState?.profileMid == mid ||
        (sourceEntry == null && !returnsToVideo && !returnsToArticle && profileMid == mid)
    )
      return
    if (
      (sourceEntry != null || returnsToVideo || returnsToArticle) &&
        profileStack.size >= MAX_PROFILE_STACK_DEPTH
    ) {
      Toast.makeText(context, "个人主页最多可以连续打开八层", Toast.LENGTH_SHORT).show()
      return
    }
    val sourceProfileState = sourceState ?: retainedProfileEntry?.state
    val sourceProfile = sourceProfileState?.profileMid?.let(sourceProfileState::snapshotProfile)
    if (bounds == Rect.Zero || bounds.width <= 0f || bounds.height <= 0f) {
      if (sourceEntry != null || returnsToVideo || returnsToArticle) {
        val child = AppRootProfileState()
        child.prepareProfile(mid) { playerViewModel.exoPlayer?.pause() }
        profileStack =
          profileStack +
            newProfileEntry(
              child,
              returnsToVideo = returnsToVideo,
              returnsToArticle = returnsToArticle,
            )
        profileLayerSuppressed = false
        child.loadPreparedProfile(mid, scope)
      } else openProfile(mid)
      return
    }
    val session =
      AvatarProfileTransition(
        token = System.nanoTime(),
        targetMid = mid,
        face = face.orEmpty(),
        name = name.orEmpty(),
        sourceBounds = bounds,
        sourceProfile = sourceProfile,
      )
    commentProfileTransition = null
    commentProfileReturnTransition = null
    avatarProfileTransition = session
    avatarProfileReturnTransition = session
    session.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
    if (session.face.isBlank()) session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
    val avatarChildEntry: ProfileStackEntry? =
      if (
        (sourceEntry != null || returnsToVideo || returnsToArticle) &&
          (sourceProfile != null || returnsToArticle)
      ) {
        val child = AppRootProfileState()
        child.prepareProfile(mid) { playerViewModel.exoPlayer?.pause() }
        newProfileEntry(
          child,
          avatarTransition = session,
          returnsToVideo = returnsToVideo,
          returnsToArticle = returnsToArticle,
        )
      } else null
    if (avatarChildEntry == null) prepareProfile(mid)
    else {
      profileStack = profileStack + avatarChildEntry
      profileLayerSuppressed = false
    }
    profileTransitionJob?.cancel()
    profileTransitionJob = scope.launch {
      session.progress.snapTo(0f)
      val preparationResult = coroutineScope {
        val preparation = async {
          prepareProfileTransition(
            barrier = session.preparation,
            imageUrl = session.face,
            targetMounted = {
              avatarChildEntry?.let {
                profileStack.lastOrNull()?.entryId == it.entryId && it.state.profileMid == mid
              } ?: (profileMid != null)
            },
            targetBounds = { avatarChildEntry?.state?.profileAvatarBounds ?: profileAvatarBounds },
          )
        }
        if (avatarChildEntry != null) {
          delay(NESTED_PROFILE_HEADER_FADE_OUT_MS)
        }
        preparation.await()
      }
      if (
        preparationResult == TransitionPreparationResult.CANCELLED ||
          avatarProfileTransition?.token != session.token ||
          session.closing
      )
        return@launch
      session.preparationTimedOut = preparationResult == TransitionPreparationResult.TIMED_OUT
      session.phase = SessionPhase.READY
      withFrameNanos {}
      session.phase = SessionPhase.FLYING
      coroutineScope {
        launch {
          session.progress.animateTo(
            1f,
            tween(if (settings.reduceMotion) 140 else 380, easing = FastOutSlowInEasing),
          )
        }
        if (sourceProfile != null || returnsToArticle) {
          launch {
            session.backgroundAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 80 else 170, easing = FastOutSlowInEasing),
            )
          }
        }
      }
      if (avatarProfileTransition?.token == session.token && !session.closing) {
        session.phase = SessionPhase.COMPLETED
        session.progress.snapTo(1f)
        if (avatarChildEntry != null) {
          avatarChildEntry.state.loadPreparedProfile(mid, scope)
          avatarProfileTransition = null
          session.backgroundAlpha.snapTo(0f)
          return@launch
        }
        withFrameNanos {}
        if (avatarProfileTransition?.token == session.token && !session.closing) {
          avatarProfileTransition = null
          loadPreparedProfile(mid)
        }
      }
    }
  }

  fun openAvatarProfile(
    mid: Long,
    bounds: Rect,
    face: String? = authUserInfo.face,
    name: String? = authUserInfo.name,
  ) = openAvatarProfileFrom(null, mid, bounds, face, name)

  fun openCommentProfileFrom(
    sourceEntryId: Long?,
    mid: Long,
    comment: CommentItem,
    anchor: CommentProfileAnchor,
    returnsToArticleSource: Boolean = false,
  ) {
    val sourceEntry = sourceEntryId?.let(::activeProfileEntry)
    val sourceState = sourceEntry?.state
    val returnsToVideo =
      sourceEntryId == null &&
        profileLayerSuppressed &&
        profileStack.isNotEmpty() &&
        transitionPhase is TransitionPhase.Video
    val returnsToArticle =
      sourceEntryId == null && returnsToArticleSource && articleStack.isNotEmpty()
    val retainedProfileEntry = profileStack.lastOrNull().takeIf { returnsToVideo }
    (sourceEntry ?: retainedProfileEntry)?.let { owner ->
      profileStack =
        profileStack.retainReturnTransitionsFor(
          owner.entryId,
          commentProfileReturnTransition,
          avatarProfileReturnTransition,
        )
    }
    if (
      mid <= 0 ||
        sourceState?.profileMid == mid ||
        (sourceEntry == null && !returnsToVideo && !returnsToArticle && profileMid == mid)
    )
      return
    if (
      (sourceEntry != null || returnsToVideo || returnsToArticle) &&
        profileStack.size >= MAX_PROFILE_STACK_DEPTH
    ) {
      Toast.makeText(context, "个人主页最多可以连续打开八层", Toast.LENGTH_SHORT).show()
      return
    }
    val sourceProfileState = sourceState ?: retainedProfileEntry?.state
    val sourceProfile = sourceProfileState?.profileMid?.let(sourceProfileState::snapshotProfile)
    val bounds =
      anchor.currentCardBounds().takeIf { it.width > 0f && it.height > 0f }
        ?: anchor.initialCardBounds
    if (bounds.width <= 0f || bounds.height <= 0f) {
      if (sourceEntry != null || returnsToVideo || returnsToArticle) {
        val child = AppRootProfileState()
        child.prepareProfile(mid) { playerViewModel.exoPlayer?.pause() }
        profileStack =
          profileStack +
            newProfileEntry(
              child,
              returnsToVideo = returnsToVideo,
              returnsToArticle = returnsToArticle,
            )
        profileLayerSuppressed = false
        child.loadPreparedProfile(mid, scope)
      } else openProfile(mid)
      return
    }
    val session =
      CommentProfileTransition(
        token = System.nanoTime(),
        targetMid = mid,
        sourceComment = comment,
        sourceBounds = bounds,
        sourceAvatarBounds =
          anchor.currentAvatarBounds().takeIf {
            mid == comment.mid && it.width > 0f && it.height > 0f
          },
        currentSourceBounds = anchor.currentCardBounds,
        currentSourceAvatarBounds = anchor.currentAvatarBounds,
        sourceProfile = sourceProfile,
      )
    avatarProfileTransition = null
    avatarProfileReturnTransition = null
    commentProfileTransition = session
    commentProfileReturnTransition = session
    session.preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
    // 嵌套导航（资料页 → 资料页）：为子页创建独立状态持有器，让父资料页保持完整
    // 组合。父页从未离开组合，因此它的滚动位置、已打开的动态详情、评论状态与
    // 分区选择全部保留。
    val childEntry: ProfileStackEntry? =
      if (
        (sourceEntry != null || returnsToVideo || returnsToArticle) &&
          (sourceProfile != null || returnsToArticle)
      ) {
        val child = AppRootProfileState()
        child.prepareProfile(mid) { playerViewModel.exoPlayer?.pause() }
        newProfileEntry(
          child,
          commentTransition = session,
          returnsToVideo = returnsToVideo,
          returnsToArticle = returnsToArticle,
        )
      } else null
    if (childEntry == null) prepareProfile(mid)
    else {
      profileStack = profileStack + childEntry
      profileLayerSuppressed = false
    }
    profileTransitionJob?.cancel()
    profileTransitionJob = scope.launch {
      session.progress.snapTo(0f)
      val preparationResult = coroutineScope {
        val preparation = async {
          prepareProfileTransition(
            barrier = session.preparation,
            imageUrl =
              session.sourceComment.face.takeIf {
                session.sourceAvatarBounds != null && it.isNotBlank()
              },
            timeoutMillis = COMMENT_PROFILE_PREPARE_TIMEOUT_MS,
            targetMounted = {
              childEntry?.let {
                profileStack.lastOrNull()?.entryId == it.entryId && it.state.profileMid == mid
              } ?: (profileMid != null)
            },
            targetBounds = { childEntry?.state?.profileAvatarBounds ?: profileAvatarBounds },
          )
        }
        if (childEntry != null) {
          delay(NESTED_PROFILE_HEADER_FADE_OUT_MS)
        }
        preparation.await()
      }
      if (
        preparationResult == TransitionPreparationResult.CANCELLED ||
          commentProfileTransition?.token != session.token ||
          session.closing
      )
        return@launch
      session.preparationTimedOut = preparationResult == TransitionPreparationResult.TIMED_OUT
      session.phase = SessionPhase.READY
      withFrameNanos {}
      session.phase = SessionPhase.FLYING
      coroutineScope {
        launch {
          session.progress.animateTo(
            1f,
            tween(if (settings.reduceMotion) 140 else 420, easing = FastOutSlowInEasing),
          )
        }
        if (sourceProfile != null || returnsToArticle) {
          launch {
            session.backgroundAlpha.animateTo(
              1f,
              tween(if (settings.reduceMotion) 80 else 180, easing = FastOutSlowInEasing),
            )
          }
        }
      }
      if (commentProfileTransition?.token == session.token && !session.closing) {
        session.phase = SessionPhase.COMPLETED
        session.progress.snapTo(1f)
        session.blocksInput = false
        if (childEntry != null) {
          childEntry.state.loadPreparedProfile(mid, scope)
          commentProfileTransition = null
          session.backgroundAlpha.snapTo(0f)
          return@launch
        }
        withFrameNanos {}
        if (commentProfileTransition?.token == session.token && !session.closing) {
          commentProfileTransition = null
          loadPreparedProfile(mid)
        }
      }
    }
  }

  fun openCommentProfile(
    mid: Long,
    comment: CommentItem,
    anchor: CommentProfileAnchor,
  ) = openCommentProfileFrom(null, mid, comment, anchor)

  fun openArticleCommentProfile(
    mid: Long,
    comment: CommentItem,
    anchor: CommentProfileAnchor,
  ) = openCommentProfileFrom(null, mid, comment, anchor, returnsToArticleSource = true)

  fun retainedPlaybackPage(itemId: String): VideoPage? {
    val retained = videoEntryCache[itemId] ?: return null
    return resolvePlaybackPage(
      requestedPage = null,
      defaultCid = retained.cid,
      pages = retained.info?.pages.orEmpty(),
    )
  }

  fun resumeVideoUnderProfile() {
    val item = appState.selectedVideo ?: return
    if (transitionPhase !is TransitionPhase.Video) return
    dataCommitAllowedId = item.id
    playerActivationId = item.id
    if (!playerViewModel.resumeIfLoaded(item.id)) {
      val restore = videoEntryCache[item.id]
      playerViewModel.loadVideo(
        item,
        startPositionMs = restore?.savedPositionMs ?: 0L,
        preferredStreamIndex = restore?.qualityIndex,
        preferredResolutionMode = currentPreferredResolutionMode(),
        page = retainedPlaybackPage(item.id),
      )
    }
  }

  fun completeProfileReturnToArticle() {
    profileStack = profileStack.dropLast(1)
    val parentEntry = profileStack.lastOrNull()
    commentProfileReturnTransition = parentEntry?.commentTransition
    avatarProfileReturnTransition = parentEntry?.avatarTransition
    profileLayerSuppressed = articleStack.lastOrNull()?.origin == ArticleOrigin.PROFILE
  }

  fun closeProfile() {
    val topProfileEntry = profileStack.lastOrNull()
    val commentSession =
      commentProfileTransition
        ?: topProfileEntry?.commentTransition
        ?: commentProfileReturnTransition
    if (commentSession != null) {
      commentSession.closing = true
      commentSession.blocksInput = true
      commentSession.preparation.cancel()
      val returnPreparation =
        TransitionPreparationBarrier(
          setOf(
            TransitionReadySignal.TARGET_MOUNTED,
            TransitionReadySignal.TARGET_BOUNDS_STABLE,
          )
        )
      profileTransitionJob?.cancel()
      profileTransitionJob = scope.launch {
        commentProfileTransition = commentSession
        prepareBoundsTransition(returnPreparation) {
          resolvedCommentProfileBounds(
            commentSession.sourceBounds,
            commentSession.currentSourceBounds(),
          )
        }
        if (commentProfileTransition?.token != commentSession.token) return@launch
        commentSession.phase = SessionPhase.READY
        withFrameNanos {}
        commentSession.phase = SessionPhase.FLYING
        if (commentSession.sourceProfile != null) {
          commentSession.backgroundAlpha.snapTo(0f)
          commentSession.backgroundAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 70 else 150, easing = FastOutSlowInEasing),
          )
        }
        val duration =
          if (settings.reduceMotion) 110
          else (380 * commentSession.progress.value.coerceIn(.25f, 1f)).toInt()
        commentSession.progress.animateTo(
          0f,
          tween(duration, easing = FastOutSlowInEasing),
        )
        commentSession.phase = SessionPhase.COMPLETED
        withFrameNanos {}
        if (commentProfileTransition?.token == commentSession.token) {
          val sourceProfile = commentSession.sourceProfile
          if (topProfileEntry?.returnsToArticle == true) {
            completeProfileReturnToArticle()
            commentProfileTransition = null
          } else if (topProfileEntry?.returnsToVideo == true) {
            profileStack = profileStack.dropLast(1)
            profileLayerSuppressed = true
            commentProfileTransition = null
            val parentEntry = profileStack.lastOrNull()
            commentProfileReturnTransition = parentEntry?.commentTransition
            avatarProfileReturnTransition = parentEntry?.avatarTransition
            resumeVideoUnderProfile()
          } else if (sourceProfile != null) {
            if (profileStack.size > 1) {
              // 只移除顶层覆盖：每个父页面保持组合且不变。
              profileStack = profileStack.dropLast(1)
              commentProfileTransition = null
              val parentEntry = profileStack.lastOrNull()
              commentProfileReturnTransition = parentEntry?.commentTransition
              avatarProfileReturnTransition = parentEntry?.avatarTransition
            } else {
              restoreProfile(sourceProfile)
            }
          } else {
            profileMid = null
            profileStack = emptyList()
            profileLayerSuppressed = false
            commentProfileTransition = null
            if (commentProfileReturnTransition?.token == commentSession.token) {
              commentProfileReturnTransition = null
            }
            resumeVideoUnderProfile()
          }
        }
      }
      return
    }
    val avatarSession =
      avatarProfileTransition ?: topProfileEntry?.avatarTransition ?: avatarProfileReturnTransition
    if (avatarSession != null) {
      avatarSession.closing = true
      avatarSession.preparation.cancel()
      val returnPreparation =
        TransitionPreparationBarrier(
          setOf(
            TransitionReadySignal.TARGET_MOUNTED,
            TransitionReadySignal.TARGET_BOUNDS_STABLE,
          )
        )
      profileTransitionJob?.cancel()
      profileTransitionJob = scope.launch {
        avatarProfileTransition = avatarSession
        prepareBoundsTransition(returnPreparation) { avatarSession.sourceBounds }
        if (avatarProfileTransition?.token != avatarSession.token) return@launch
        avatarSession.phase = SessionPhase.READY
        withFrameNanos {}
        avatarSession.phase = SessionPhase.FLYING
        if (avatarSession.sourceProfile != null) {
          avatarSession.backgroundAlpha.snapTo(0f)
          avatarSession.backgroundAlpha.animateTo(
            1f,
            tween(if (settings.reduceMotion) 70 else 150, easing = FastOutSlowInEasing),
          )
        }
        val duration =
          if (settings.reduceMotion) 110
          else (340 * avatarSession.progress.value.coerceIn(.25f, 1f)).toInt()
        avatarSession.progress.animateTo(
          0f,
          tween(duration, easing = FastOutSlowInEasing),
        )
        avatarSession.phase = SessionPhase.COMPLETED
        withFrameNanos {}
        if (avatarProfileTransition?.token == avatarSession.token) {
          val sourceProfile = avatarSession.sourceProfile
          if (topProfileEntry?.returnsToArticle == true) {
            completeProfileReturnToArticle()
            avatarProfileTransition = null
          } else if (topProfileEntry?.returnsToVideo == true) {
            profileStack = profileStack.dropLast(1)
            profileLayerSuppressed = true
            avatarProfileTransition = null
            val parentEntry = profileStack.lastOrNull()
            commentProfileReturnTransition = parentEntry?.commentTransition
            avatarProfileReturnTransition = parentEntry?.avatarTransition
            resumeVideoUnderProfile()
          } else if (sourceProfile != null) {
            if (profileStack.size > 1) {
              profileStack = profileStack.dropLast(1)
              avatarProfileTransition = null
              val parentEntry = profileStack.lastOrNull()
              commentProfileReturnTransition = parentEntry?.commentTransition
              avatarProfileReturnTransition = parentEntry?.avatarTransition
            } else {
              restoreProfile(sourceProfile)
            }
          } else {
            profileMid = null
            profileStack = emptyList()
            profileLayerSuppressed = false
            avatarProfileTransition = null
            if (avatarProfileReturnTransition?.token == avatarSession.token) {
              avatarProfileReturnTransition = null
            }
            resumeVideoUnderProfile()
          }
        }
      }
      return
    }
    profileTransitionJob?.cancel()
    if (topProfileEntry?.returnsToArticle == true) {
      completeProfileReturnToArticle()
      return
    }
    if (topProfileEntry?.returnsToVideo == true) {
      profileStack = profileStack.dropLast(1)
      profileLayerSuppressed = true
      val parentEntry = profileStack.lastOrNull()
      commentProfileReturnTransition = parentEntry?.commentTransition
      avatarProfileReturnTransition = parentEntry?.avatarTransition
      resumeVideoUnderProfile()
      return
    }
    if (profileStack.size > 1) {
      profileStack = profileStack.dropLast(1)
      val parentEntry = profileStack.lastOrNull()
      commentProfileReturnTransition = parentEntry?.commentTransition
      avatarProfileReturnTransition = parentEntry?.avatarTransition
      return
    }
    profileMid = null
    profileStack = emptyList()
    profileLayerSuppressed = false
    resumeVideoUnderProfile()
  }
}

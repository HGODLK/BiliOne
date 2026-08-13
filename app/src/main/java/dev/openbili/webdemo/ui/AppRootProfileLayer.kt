package dev.openbili.webdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.FollowingGroup
import dev.openbili.webdemo.api.SpaceProfile
import dev.openbili.webdemo.api.SpaceDynamicItem
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.profile.ProfileScreen
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.video.CommentProfileAnchor

internal fun profileSaveableStateKey(mid: Long): String = "profile_$mid"

internal fun profileSaveableStateKey(entryId: Long, mid: Long): String =
  "profile_${entryId}_$mid"

internal fun profileTransitionContentAlpha(progress: Float): Float =
  ((progress.coerceIn(0f, 1f) - .28f) / .52f).coerceIn(0f, 1f)

internal fun parentProfileContentAlpha(
  transitionProgress: Float?,
  nestedTransitionActive: Boolean,
): Float =
  if (nestedTransitionActive) 1f
  else transitionProgress?.let(::profileTransitionContentAlpha) ?: 1f

internal fun parentProfileHeaderChromeVisible(nestedProfilePresent: Boolean): Boolean =
  !nestedProfilePresent

/** A retained profile may stay mounted behind a video that has returned from that profile. */
internal fun isVideoPageForeground(
  videoScreenVisible: Boolean,
  profileVisible: Boolean,
  profileSuppressed: Boolean,
): Boolean = videoScreenVisible && (!profileVisible || profileSuppressed)

internal fun rootProfileEntryVisible(
  profileMid: Long?,
  entries: List<ProfileStackEntry>,
): Boolean {
  val root = entries.firstOrNull() ?: return false
  if (profileMid == null || root.returnsToVideo || root.returnsToArticle) return false
  return visibleProfileStack(entries).firstOrNull() === root
}

internal fun visibleProfileStack(entries: List<ProfileStackEntry>): List<ProfileStackEntry> {
  val overlayStartIndex = entries.indexOfLast { it.returnsToVideo || it.returnsToArticle }
  return if (overlayStartIndex < 0) entries else entries.drop(overlayStartIndex)
}

/** Profile page and its avatar/comment/video transition layers above the retained root page. */
@Composable
internal fun AppRootProfileLayer(
  modifier: Modifier = Modifier,
  profileMid: Long?,
  profileStateHolder: SaveableStateHolder,
  profile: SpaceProfile?,
  videos: List<FeedItem>,
  dynamics: List<SpaceDynamicItem>,
  dynamicsLoading: Boolean,
  dynamicsHasMore: Boolean,
  dynamicsError: String?,
  selectedDynamicId: String?,
  collections: List<SpaceContentCard>,
  collectionsLoading: Boolean,
  collectionsError: String?,
  selectedCollectionId: String?,
  collectionVideos: List<FeedItem>,
  collectionPage: Int,
  collectionHasMore: Boolean,
  collectionLoading: Boolean,
  collectionError: String?,
  collectionTotal: Int,
  loading: Boolean,
  hasMore: Boolean,
  error: String?,
  currentPage: Int,
  currentAccountMid: Long,
  followed: Boolean,
  followBusy: Boolean,
  followingGroups: List<FollowingGroup>,
  followingGroupsLoading: Boolean,
  loggedIn: Boolean,
  profileIpAuthorized: Boolean,
  settings: AppSettings,
  hiddenCoverItemId: String?,
  bangumiReturnRequest: ProfileBangumiReturnRequest?,
  hiddenArticleItemId: String?,
  profileAvatarBounds: Rect,
  commentTransition: CommentProfileTransition?,
  commentReturnTransition: CommentProfileTransition?,
  avatarTransition: AvatarProfileTransition?,
  avatarReturnTransition: AvatarProfileTransition?,
  activeSession: CardTransitionSession?,
  profileVideoTransitionActive: Boolean,
  onBack: () -> Unit,
  onVideoClick: (Long, FeedItem, Rect) -> Unit,
  onBangumiClick: (Long, SpaceContentCard, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  onArticleClick: (ArticleItem, Rect) -> Unit,
  onArticleBoundsChanged: (ArticleItem, Rect) -> Unit,
  onLoadPage: (Int) -> Unit,
  onRefresh: () -> Unit,
  onLoadFollowingGroups: () -> Unit,
  onSelectFollowingGroup: (Long, Long) -> Unit,
  onUnfollow: (Long) -> Unit,
  onPrivateMessagesSelected: (Long, String, String) -> Unit,
  privateMessageContent: @Composable (Long) -> Unit,
  onLogin: () -> Unit,
  onCommentProfileClick: (Long, Long, CommentItem, CommentProfileAnchor) -> Unit,
  onAvatarProfileClick: (Long, Long, String?, String?, Rect) -> Unit,
  onEnsureDynamics: () -> Unit,
  onLoadMoreDynamics: () -> Unit,
  onRefreshDynamics: () -> Unit,
  onSelectedDynamicIdChange: (String?) -> Unit,
  onEnsureCollections: () -> Unit,
  onRefreshCollections: () -> Unit,
  onSelectedCollectionChange: (SpaceContentCard?) -> Unit,
  onLoadMoreCollection: () -> Unit,
  onDynamicLike: (Long, SpaceDynamicItem) -> Unit,
  onDynamicDelete: (Long, SpaceDynamicItem) -> Unit,
  onDynamicPin: (Long, SpaceDynamicItem) -> Unit,
  onVideoBoundsChanged: (Long, FeedItem, Rect) -> Unit,
  onAvatarBoundsChanged: (Rect) -> Unit,
  profileStack: List<ProfileStackEntry>,
  backHandlingEnabled: Boolean = true,
) {
  Box(modifier.fillMaxSize()) {
    val rootEntry = profileStack.firstOrNull()
    val visibleProfileEntries = visibleProfileStack(profileStack)
    val rootProfileVisible = rootProfileEntryVisible(profileMid, profileStack)
    val visibleNestedEntries =
      if (rootProfileVisible) profileStack.drop(1) else visibleProfileEntries
    val nestedProfileState =
      profileStack.lastOrNull()?.takeIf { profileStack.size > 1 || !rootProfileVisible }?.state
    val nestedTransitionActive =
      nestedProfileState != null &&
        (commentTransition?.sourceProfile != null ||
          avatarTransition?.sourceProfile != null ||
          profileStack.lastOrNull()?.returnsToArticle == true)
    profileMid
      ?.takeIf { rootProfileVisible }
      ?.let { mid ->
        val profileContent: @Composable () -> Unit = {
          ProfileScreen(
            profile = profile,
            videos = videos,
            dynamics = dynamics,
            dynamicsLoading = dynamicsLoading,
            dynamicsHasMore = dynamicsHasMore,
            dynamicsError = dynamicsError,
            selectedDynamicId = selectedDynamicId,
            collections = collections,
            collectionsLoading = collectionsLoading,
            collectionsError = collectionsError,
            selectedCollectionId = selectedCollectionId,
            collectionVideos = collectionVideos,
            collectionPage = collectionPage,
            collectionHasMore = collectionHasMore,
            collectionLoading = collectionLoading,
            collectionError = collectionError,
            collectionTotal = collectionTotal,
            loading = loading,
            hasMore = hasMore,
            error = error,
            onBack = onBack,
            onVideoClick = { item, bounds ->
              rootEntry?.let { onVideoClick(it.entryId, item, bounds) }
            },
            onBangumiClick = { item, bounds ->
              rootEntry?.let { onBangumiClick(it.entryId, item, bounds) }
            },
            onVideoLongClick = onVideoLongClick,
            onArticleClick = onArticleClick,
            hiddenArticleItemId = hiddenArticleItemId,
            onArticleBoundsChanged = onArticleBoundsChanged,
            onLoadMore = { onLoadPage(currentPage + 1) },
            onRetry = { onLoadPage(if (videos.isEmpty()) 1 else currentPage + 1) },
            onRefresh = onRefresh,
            showFollowButton = mid > 0L && mid != currentAccountMid,
            followed = followed,
            followBusy = followBusy,
            followingGroups = followingGroups,
            followingGroupsLoading = followingGroupsLoading,
            loggedIn = loggedIn,
            profileIpAuthorized = profileIpAuthorized,
            currentAccountMid = currentAccountMid,
            settings = settings,
            onCommentProfileClick = { targetMid, comment, anchor ->
              rootEntry?.let { onCommentProfileClick(it.entryId, targetMid, comment, anchor) }
            },
            onAvatarProfileClick = { targetMid, face, name, bounds ->
              rootEntry?.let { onAvatarProfileClick(it.entryId, targetMid, face, name, bounds) }
            },
            onEnsureDynamics = onEnsureDynamics,
            onLoadMoreDynamics = onLoadMoreDynamics,
            onRefreshDynamics = onRefreshDynamics,
            onSelectedDynamicIdChange = onSelectedDynamicIdChange,
            onEnsureCollections = onEnsureCollections,
            onRefreshCollections = onRefreshCollections,
            onSelectedCollectionChange = onSelectedCollectionChange,
            onLoadMoreCollection = onLoadMoreCollection,
            onDynamicLike = { item -> rootEntry?.let { onDynamicLike(it.entryId, item) } },
            onDynamicDelete = { item -> rootEntry?.let { onDynamicDelete(it.entryId, item) } },
            onDynamicPin = { item -> rootEntry?.let { onDynamicPin(it.entryId, item) } },
            onLoadFollowingGroups = onLoadFollowingGroups,
            onSelectFollowingGroup = { groupId ->
              rootEntry?.let { onSelectFollowingGroup(it.entryId, groupId) }
            },
            onUnfollow = { rootEntry?.let { onUnfollow(it.entryId) } },
            showPrivateMessages = mid > 0L && mid != currentAccountMid,
            onPrivateMessagesSelected = onPrivateMessagesSelected,
            privateMessageContent = {
              rootEntry?.let { privateMessageContent(it.entryId) }
            },
            onLogin = onLogin,
            hiddenCoverItemId = hiddenCoverItemId,
            bangumiReturnRequestToken =
              bangumiReturnRequest?.takeIf { it.profileEntryId == rootEntry?.entryId }?.token
                ?: 0L,
            bangumiReturnCardId =
              bangumiReturnRequest?.takeIf { it.profileEntryId == rootEntry?.entryId }?.cardId,
            onVideoBoundsChanged = { item, bounds ->
              rootEntry?.let { onVideoBoundsChanged(it.entryId, item, bounds) }
            },
            onAvatarBoundsChanged = onAvatarBoundsChanged,
            avatarVisible =
              nestedProfileState != null ||
                (avatarTransition == null && commentTransition?.sourceAvatarBounds == null),
            headerChromeVisible = parentProfileHeaderChromeVisible(nestedProfileState != null),
            placeholderFace =
              (avatarTransition ?: avatarReturnTransition)?.face
                ?: (commentTransition ?: commentReturnTransition)
                  ?.takeIf { it.sourceAvatarBounds != null }
                  ?.sourceComment
                  ?.face,
            placeholderName =
              (avatarTransition ?: avatarReturnTransition)?.name
                ?: (commentTransition ?: commentReturnTransition)
                  ?.takeIf { it.sourceAvatarBounds != null }
                  ?.sourceComment
                  ?.name,
            transitionRunning = commentTransition != null || avatarTransition != null,
            backHandlingEnabled = backHandlingEnabled,
          )
        }
        val blocksUnderlyingPage =
          activeSession?.kind == TransitionKind.ENTER_PROFILE && activeSession.reverseRequested
        Box(
          Modifier.fillMaxSize()
            .then(
              if (blocksUnderlyingPage) Modifier.background(MaterialTheme.colorScheme.background)
              else Modifier
            )
        ) {
          Box(
            Modifier.fillMaxSize().graphicsLayer {
              alpha =
                when {
                  activeSession?.kind == TransitionKind.ENTER_PROFILE ->
                    1f - activeSession.backgroundAlpha.value.coerceIn(0f, 1f)
                  profileVideoTransitionActive &&
                    activeSession?.kind == TransitionKind.EXIT_PROFILE ->
                    activeSession.panelAlpha.value
                  else -> 1f
                }
            }
          ) {
            val currentCommentSourceBounds = commentTransition?.let { session ->
              resolvedCommentProfileBounds(session.sourceBounds, session.currentSourceBounds())
            }
            val currentCommentAvatarBounds =
              commentTransition?.sourceAvatarBounds?.let { fallback ->
                resolvedCommentProfileBounds(
                  fallback,
                  commentTransition.currentSourceAvatarBounds(),
                )
              }
            when {
              nestedTransitionActive -> Unit
              commentTransition != null && commentTransition.sourceProfile == null ->
                ProfileTransitionBackground(
                  sourceBounds = currentCommentSourceBounds ?: commentTransition.sourceBounds,
                  progress = { commentTransition.progress.value },
                  dimAlpha = .12f,
                  revealFromTransparent = true,
                )
              avatarTransition != null && avatarTransition.sourceProfile == null ->
                ProfileTransitionBackground(
                  sourceBounds = avatarTransition.sourceBounds,
                  progress = { avatarTransition.progress.value },
                  dimAlpha = .1f,
                )
            }
            Box(
              Modifier.fillMaxSize().graphicsLayer {
                val progress =
                  commentTransition?.progress?.value ?: avatarTransition?.progress?.value
                alpha = parentProfileContentAlpha(progress, nestedTransitionActive)
              }
            ) {
              profileStateHolder.SaveableStateProvider(
                profileSaveableStateKey(rootEntry?.entryId ?: 0L, mid)
              ) {
                profileContent()
              }
            }
            when {
              nestedTransitionActive -> Unit
              commentTransition != null && currentCommentAvatarBounds != null ->
                AvatarProfileTransitionForeground(
                  sourceBounds = currentCommentAvatarBounds,
                  targetBounds = profileAvatarBounds,
                  face = commentTransition.sourceComment.face,
                  name = commentTransition.sourceComment.name,
                  progress = { commentTransition.progress.value },
                )
              avatarTransition != null ->
                AvatarProfileTransitionForeground(
                  sourceBounds = avatarTransition.sourceBounds,
                  targetBounds = profileAvatarBounds,
                  face = avatarTransition.face,
                  name = avatarTransition.name,
                  progress = { avatarTransition.progress.value },
                )
            }
          }
        }
      }

    val nestedCommentTransition = commentTransition?.takeIf {
      (it.sourceProfile != null || profileStack.lastOrNull()?.returnsToArticle == true) &&
        nestedProfileState?.profileMid == it.targetMid
    }
    val nestedAvatarTransition = avatarTransition?.takeIf {
      (it.sourceProfile != null || profileStack.lastOrNull()?.returnsToArticle == true) &&
        nestedProfileState?.profileMid == it.targetMid
    }
    val nestedCommentPlaceholder =
      nestedCommentTransition
        ?: commentReturnTransition?.takeIf {
          (it.sourceProfile != null || profileStack.lastOrNull()?.returnsToArticle == true) &&
            nestedProfileState?.profileMid == it.targetMid
        }
    val nestedAvatarPlaceholder =
      nestedAvatarTransition
        ?: avatarReturnTransition?.takeIf {
          (it.sourceProfile != null || profileStack.lastOrNull()?.returnsToArticle == true) &&
            nestedProfileState?.profileMid == it.targetMid
        }
    when {
      nestedCommentTransition != null ->
        ProfileTransitionBackground(
          sourceBounds =
            resolvedCommentProfileBounds(
              nestedCommentTransition.sourceBounds,
              nestedCommentTransition.currentSourceBounds(),
            ),
          progress = { nestedCommentTransition.progress.value },
          dimAlpha = .1f,
          revealFromTransparent = true,
          surfaceAlpha = { nestedCommentTransition.backgroundAlpha.value },
        )
      nestedAvatarTransition != null ->
        ProfileTransitionBackground(
          sourceBounds = nestedAvatarTransition.sourceBounds,
          progress = { nestedAvatarTransition.progress.value },
          dimAlpha = .08f,
          revealFromTransparent = true,
          surfaceAlpha = { nestedAvatarTransition.backgroundAlpha.value },
        )
    }

    // The child is mounted before preparation starts and remains an independent full-screen layer
    // until the reverse animation has finished. The live parent stays composed underneath it.
    visibleNestedEntries.forEachIndexed { nestedIndex, nestedEntry ->
      val nested = nestedEntry.state
      val nestedMid = nested.profileMid ?: return@forEachIndexed
      val isTopProfile = nestedIndex == visibleNestedEntries.lastIndex
      val nestedScope = androidx.compose.runtime.rememberCoroutineScope()
      val nestedTransitionRunning =
        isTopProfile && (nestedCommentTransition != null || nestedAvatarTransition != null)
      val articleTransitionSource =
        if (!isTopProfile || !nestedEntry.returnsToArticle) null
        else
          nestedCommentTransition?.let { session ->
            resolvedCommentProfileBounds(session.sourceBounds, session.currentSourceBounds())
          } ?: nestedAvatarTransition?.sourceBounds
      Box(
        Modifier.fillMaxSize()
          .graphicsLayer {
            val progress =
              nestedCommentTransition?.progress?.value?.takeIf { isTopProfile }
                ?: nestedAvatarTransition?.progress?.value?.takeIf { isTopProfile }
                ?: 1f
            val videoTransitionAlpha =
              when {
                activeSession?.kind == TransitionKind.ENTER_PROFILE ->
                  1f - activeSession.backgroundAlpha.value.coerceIn(0f, 1f)
                profileVideoTransitionActive &&
                  activeSession?.kind == TransitionKind.EXIT_PROFILE ->
                  activeSession.panelAlpha.value
                else -> 1f
              }
            articleTransitionSource
              ?.takeIf { it.hasUsableSize() }
              ?.let { source ->
                val fullWidth = size.width.coerceAtLeast(1f)
                val fullHeight = size.height.coerceAtLeast(1f)
                val startScaleX = (source.width / fullWidth).coerceAtLeast(.001f)
                val startScaleY = (source.height / fullHeight).coerceAtLeast(.001f)
                val pageScaleX = startScaleX + (1f - startScaleX) * progress
                val pageScaleY = startScaleY + (1f - startScaleY) * progress
                scaleX = pageScaleX
                scaleY = pageScaleY
                translationX = source.left * (1f - progress)
                translationY = source.top * (1f - progress)
                transformOrigin = TransformOrigin(0f, 0f)
                shape =
                  RoundedCornerShape((18f * (1f - progress) / pageScaleY.coerceAtLeast(.001f)).dp)
                clip = true
              }
            val contentAlpha =
              if (articleTransitionSource != null) (progress / .32f).coerceIn(0f, 1f)
              else profileTransitionContentAlpha(progress)
            alpha = contentAlpha * videoTransitionAlpha
          }
          .background(MaterialTheme.colorScheme.background)
      ) {
        profileStateHolder.SaveableStateProvider(
          profileSaveableStateKey(nestedEntry.entryId, nestedMid)
        ) {
          ProfileScreen(
            profile = nested.spaceProfile,
            videos = nested.spaceVideos,
            dynamics = nested.spaceDynamics,
            dynamicsLoading = nested.spaceDynamicLoading,
            dynamicsHasMore = nested.spaceDynamicHasMore,
            dynamicsError = nested.spaceDynamicError,
            selectedDynamicId = nested.selectedDynamicId,
            collections = nested.spaceCollections,
            collectionsLoading = nested.spaceCollectionsLoading,
            collectionsError = nested.spaceCollectionsError,
            selectedCollectionId = nested.selectedCollectionId,
            collectionVideos = nested.spaceCollectionVideos,
            collectionPage = nested.spaceCollectionPage,
            collectionHasMore = nested.spaceCollectionHasMore,
            collectionLoading = nested.spaceCollectionLoading,
            collectionError = nested.spaceCollectionError,
            collectionTotal = nested.spaceCollectionTotal,
            loading = nested.spaceLoading,
            hasMore = nested.spaceHasMore,
            error = nested.spaceError,
            onBack = onBack,
            onVideoClick = { item, bounds -> onVideoClick(nestedEntry.entryId, item, bounds) },
            onBangumiClick = { item, bounds ->
              onBangumiClick(nestedEntry.entryId, item, bounds)
            },
            onVideoLongClick = onVideoLongClick,
            onArticleClick = onArticleClick,
            hiddenArticleItemId = hiddenArticleItemId.takeIf { isTopProfile },
            onArticleBoundsChanged = onArticleBoundsChanged,
            onLoadMore = { nested.loadSpacePage(nestedMid, nested.spacePage + 1, nestedScope) },
            onRetry = {
              nested.loadSpacePage(
                nestedMid,
                if (nested.spaceVideos.isEmpty()) 1 else nested.spacePage + 1,
                nestedScope,
              )
            },
            onRefresh = { nested.loadSpacePage(nestedMid, 1, nestedScope) },
            showFollowButton = nestedMid > 0L && nestedMid != currentAccountMid,
            followed = nested.followingStates[nestedMid] == true,
            followBusy = nested.followingBusy[nestedMid] == true,
            followingGroups = followingGroups,
            followingGroupsLoading = followingGroupsLoading,
            loggedIn = loggedIn,
            profileIpAuthorized = profileIpAuthorized,
            currentAccountMid = currentAccountMid,
            settings = settings,
            onCommentProfileClick = { targetMid, comment, anchor ->
              onCommentProfileClick(nestedEntry.entryId, targetMid, comment, anchor)
            },
            onAvatarProfileClick = { targetMid, face, name, bounds ->
              onAvatarProfileClick(nestedEntry.entryId, targetMid, face, name, bounds)
            },
            onEnsureDynamics = {
              if (nested.spaceDynamics.isEmpty())
                nested.loadSpaceDynamics(nestedMid, false, nestedScope)
            },
            onLoadMoreDynamics = { nested.loadSpaceDynamics(nestedMid, false, nestedScope) },
            onRefreshDynamics = { nested.loadSpaceDynamics(nestedMid, true, nestedScope) },
            onSelectedDynamicIdChange = { nested.selectedDynamicId = it },
            onEnsureCollections = {
              nested.loadSpaceCollections(nestedMid, refresh = false, nestedScope)
            },
            onRefreshCollections = {
              nested.loadSpaceCollections(nestedMid, refresh = true, nestedScope)
            },
            onSelectedCollectionChange = { collection ->
              if (collection == null) nested.clearSelectedSpaceCollection()
              else nested.selectSpaceCollection(nestedMid, collection, nestedScope)
            },
            onLoadMoreCollection = {
              val collection =
                nested.spaceCollections.firstOrNull { it.id == nested.selectedCollectionId }
              if (collection != null) {
                nested.loadSpaceCollectionPage(
                  mid = nestedMid,
                  collection = collection,
                  page = (nested.spaceCollectionPage + 1).coerceAtLeast(1),
                  scope = nestedScope,
                )
              }
            },
            onDynamicLike = { item -> onDynamicLike(nestedEntry.entryId, item) },
            onDynamicDelete = { item -> onDynamicDelete(nestedEntry.entryId, item) },
            onDynamicPin = { item -> onDynamicPin(nestedEntry.entryId, item) },
            onLoadFollowingGroups = onLoadFollowingGroups,
            onSelectFollowingGroup = { groupId ->
              onSelectFollowingGroup(nestedEntry.entryId, groupId)
            },
            onUnfollow = { onUnfollow(nestedEntry.entryId) },
            showPrivateMessages = nestedMid > 0L && nestedMid != currentAccountMid,
            onPrivateMessagesSelected = onPrivateMessagesSelected,
            privateMessageContent = { privateMessageContent(nestedEntry.entryId) },
            onLogin = onLogin,
            hiddenCoverItemId = hiddenCoverItemId.takeIf { isTopProfile },
            bangumiReturnRequestToken =
              bangumiReturnRequest?.takeIf { it.profileEntryId == nestedEntry.entryId }?.token
                ?: 0L,
            bangumiReturnCardId =
              bangumiReturnRequest?.takeIf { it.profileEntryId == nestedEntry.entryId }?.cardId,
            onVideoBoundsChanged = { item, bounds ->
              onVideoBoundsChanged(nestedEntry.entryId, item, bounds)
            },
            onAvatarBoundsChanged = { nested.profileAvatarBounds = it },
            avatarVisible =
              !isTopProfile ||
                (nestedAvatarTransition == null &&
                  nestedCommentTransition?.sourceAvatarBounds == null),
            placeholderFace =
              nestedAvatarPlaceholder?.face?.takeIf { isTopProfile }
                ?: nestedCommentPlaceholder
                  ?.takeIf { isTopProfile && it.sourceAvatarBounds != null }
                  ?.sourceComment
                  ?.face,
            placeholderName =
              nestedAvatarPlaceholder?.name?.takeIf { isTopProfile }
                ?: nestedCommentPlaceholder
                  ?.takeIf { isTopProfile && it.sourceAvatarBounds != null }
                  ?.sourceComment
                  ?.name,
            transitionRunning = nestedTransitionRunning,
            headerChromeVisible = isTopProfile,
            backHandlingEnabled = backHandlingEnabled && isTopProfile,
          )
        }
      }
    }

    val nestedTargetBounds =
      nestedProfileState?.profileAvatarBounds?.takeIf { it.hasUsableSize() } ?: profileAvatarBounds
    when {
      nestedCommentTransition?.sourceAvatarBounds != null ->
        AvatarProfileTransitionForeground(
          sourceBounds =
            resolvedCommentProfileBounds(
              nestedCommentTransition.sourceAvatarBounds,
              nestedCommentTransition.currentSourceAvatarBounds(),
            ),
          targetBounds = nestedTargetBounds,
          face = nestedCommentTransition.sourceComment.face,
          name = nestedCommentTransition.sourceComment.name,
          progress = { nestedCommentTransition.progress.value },
        )
      nestedAvatarTransition != null ->
        AvatarProfileTransitionForeground(
          sourceBounds = nestedAvatarTransition.sourceBounds,
          targetBounds = nestedTargetBounds,
          face = nestedAvatarTransition.face,
          name = nestedAvatarTransition.name,
          progress = { nestedAvatarTransition.progress.value },
        )
    }

    // The shared cover itself is drawn once by AppRoot. Keep only the full-page bridge here so the
    // retained profile stack can still participate without creating a duplicate cover frame.
    activeSession
      ?.takeIf {
        shouldDisplayCardTransitionOverlay(it.kind, it.phase) &&
          (it.kind == TransitionKind.ENTER_PROFILE || it.kind == TransitionKind.EXIT_PROFILE)
      }
      ?.let { session ->
        Box(
          Modifier.fillMaxSize()
            .graphicsLayer { alpha = session.bridgeAlpha.value.coerceIn(0f, 1f) }
            .background(
              Brush.verticalGradient(
                listOf(
                  MaterialTheme.colorScheme.background,
                  MaterialTheme.colorScheme.surfaceVariant,
                  MaterialTheme.colorScheme.background,
                )
              )
            )
        )
      }
  }
}

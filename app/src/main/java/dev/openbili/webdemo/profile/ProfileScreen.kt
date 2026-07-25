package dev.openbili.webdemo.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.FollowingGroup
import dev.openbili.webdemo.api.SpaceBangumiResponse
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import dev.openbili.webdemo.api.SpaceProfile
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedCardContent
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedImageLoadMode
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import dev.openbili.webdemo.feed.rememberGridFeedImageLoadPolicy
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.ui.BackdropGlassSurface
import dev.openbili.webdemo.ui.FollowButton
import dev.openbili.webdemo.ui.PressableVideoCard
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.VideoCardReveal
import dev.openbili.webdemo.video.CommentProfileAnchor
import dev.openbili.webdemo.video.BiliRichText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ProfileSection(val label: String) {
  POSTS("投稿"),
  DYNAMICS("动态"),
  COLLECTIONS("合集和系列"),
  BANGUMI("追番追剧"),
  PRIVATE_MESSAGES("私信"),
}

internal enum class ProfileBangumiFilter(val label: String) {
  ALL("全部"),
  BANGUMI("追番"),
  DRAMA("追剧"),
}

internal fun filterProfileBangumi(
  cards: List<SpaceContentCard>,
  filter: ProfileBangumiFilter,
): List<SpaceContentCard> =
  when (filter) {
    ProfileBangumiFilter.ALL -> cards
    ProfileBangumiFilter.BANGUMI -> cards.filter { it.kind == SpaceContentKind.BANGUMI }
    ProfileBangumiFilter.DRAMA -> cards.filter { it.kind == SpaceContentKind.DRAMA }
  }

internal data class ProfileBangumiPagingState(
  val page: Int = 0,
  val hasMore: Boolean = true,
  val loading: Boolean = false,
  val error: String? = null,
)

internal fun profileBangumiTypesToLoad(
  filter: ProfileBangumiFilter,
  bangumiHasMore: Boolean,
  dramaHasMore: Boolean,
): List<Int> =
  when (filter) {
    ProfileBangumiFilter.ALL ->
      buildList {
        if (bangumiHasMore) add(1)
        if (dramaHasMore) add(2)
      }
    ProfileBangumiFilter.BANGUMI -> if (bangumiHasMore) listOf(1) else emptyList()
    ProfileBangumiFilter.DRAMA -> if (dramaHasMore) listOf(2) else emptyList()
  }

internal enum class ProfileHeaderInfoState {
  COLLAPSED,
  EXPANDED,
  HIDDEN,
}

internal enum class ProfileHeaderInfoEvent {
  CAPSULE_TAP,
  BANNER_TAP,
  OTHER_OPERATION,
}

internal fun reduceProfileHeaderInfoState(
  state: ProfileHeaderInfoState,
  event: ProfileHeaderInfoEvent,
): ProfileHeaderInfoState =
  when (state) {
    ProfileHeaderInfoState.COLLAPSED ->
      when (event) {
        ProfileHeaderInfoEvent.CAPSULE_TAP -> ProfileHeaderInfoState.EXPANDED
        ProfileHeaderInfoEvent.BANNER_TAP -> ProfileHeaderInfoState.HIDDEN
        ProfileHeaderInfoEvent.OTHER_OPERATION -> ProfileHeaderInfoState.COLLAPSED
      }
    ProfileHeaderInfoState.EXPANDED ->
      when (event) {
        ProfileHeaderInfoEvent.CAPSULE_TAP -> ProfileHeaderInfoState.EXPANDED
        ProfileHeaderInfoEvent.BANNER_TAP,
        ProfileHeaderInfoEvent.OTHER_OPERATION -> ProfileHeaderInfoState.COLLAPSED
      }
    ProfileHeaderInfoState.HIDDEN -> ProfileHeaderInfoState.COLLAPSED
  }

@Composable
fun ProfileScreen(
  profile: SpaceProfile?,
  videos: List<FeedItem>,
  dynamics: List<dev.openbili.webdemo.api.SpaceDynamicItem>,
  dynamicsLoading: Boolean,
  dynamicsHasMore: Boolean,
  dynamicsError: String?,
  selectedDynamicId: String?,
  loading: Boolean,
  hasMore: Boolean,
  error: String?,
  onBack: () -> Unit,
  onVideoClick: (FeedItem, Rect) -> Unit,
  onBangumiClick: (SpaceContentCard, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  onArticleClick: (ArticleItem, Rect) -> Unit,
  hiddenArticleItemId: String? = null,
  onArticleBoundsChanged: (ArticleItem, Rect) -> Unit = { _, _ -> },
  onLoadMore: () -> Unit,
  onRetry: () -> Unit,
  onRefresh: () -> Unit,
  showFollowButton: Boolean,
  followed: Boolean,
  followBusy: Boolean,
  followingGroups: List<FollowingGroup>,
  followingGroupsLoading: Boolean,
  loggedIn: Boolean,
  profileIpAuthorized: Boolean,
  currentAccountMid: Long,
  settings: AppSettings,
  onCommentProfileClick: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onAvatarProfileClick: (Long, String?, String?, Rect) -> Unit,
  onEnsureDynamics: () -> Unit,
  onLoadMoreDynamics: () -> Unit,
  onRefreshDynamics: () -> Unit,
  onSelectedDynamicIdChange: (String?) -> Unit,
  onDynamicLike: (dev.openbili.webdemo.api.SpaceDynamicItem) -> Unit,
  onDynamicDelete: (dev.openbili.webdemo.api.SpaceDynamicItem) -> Unit,
  onDynamicPin: (dev.openbili.webdemo.api.SpaceDynamicItem) -> Unit,
  onLoadFollowingGroups: () -> Unit,
  onSelectFollowingGroup: (Long) -> Unit,
  onUnfollow: () -> Unit,
  showPrivateMessages: Boolean,
  onPrivateMessagesSelected: (Long, String, String) -> Unit,
  privateMessageContent: @Composable () -> Unit,
  onLogin: () -> Unit,
  onAuthorizeProfileIp: () -> Unit,
  hiddenCoverItemId: String? = null,
  onVideoBoundsChanged: (FeedItem, Rect) -> Unit = { _, _ -> },
  onAvatarBoundsChanged: (Rect) -> Unit = {},
  avatarVisible: Boolean = true,
  headerChromeVisible: Boolean = true,
  hiddenCommentAvatarRpid: Long? = null,
  hiddenDynamicAvatarBounds: Rect? = null,
  placeholderFace: String? = null,
  placeholderName: String? = null,
  transitionRunning: Boolean = false,
  backHandlingEnabled: Boolean = true,
) {
  BackHandler(enabled = backHandlingEnabled, onBack = onBack)
  var section by rememberSaveable(profile?.mid) { mutableStateOf(ProfileSection.POSTS) }
  var contentSearchQuery by rememberSaveable(profile?.mid) { mutableStateOf("") }
  var extraCards by remember(profile?.mid) { mutableStateOf<List<SpaceContentCard>>(emptyList()) }
  var extraLoading by remember(profile?.mid) { mutableStateOf(false) }
  var extraError by remember(profile?.mid) { mutableStateOf<String?>(null) }
  var extraRefreshGeneration by remember(profile?.mid) { mutableStateOf(0) }
  var bangumiPaging by
    remember(profile?.mid) { mutableStateOf(ProfileBangumiPagingState()) }
  var dramaPaging by remember(profile?.mid) { mutableStateOf(ProfileBangumiPagingState()) }
  var bangumiRequestToken by remember(profile?.mid) { mutableStateOf(0L) }
  var headerInfoState by
    rememberSaveable(profile?.mid) {
      mutableStateOf(ProfileHeaderInfoState.COLLAPSED)
    }
  val scope = rememberCoroutineScope()
  val currentHeaderInfoState by rememberUpdatedState(headerInfoState)
  val density = LocalDensity.current
  val privateMessageImeVisible =
    section == ProfileSection.PRIVATE_MESSAGES && WindowInsets.ime.getBottom(density) > 0
  val contentInteractionObserver =
    Modifier.pointerInput(profile?.mid) {
      awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        if (currentHeaderInfoState != ProfileHeaderInfoState.COLLAPSED) {
          headerInfoState =
            reduceProfileHeaderInfoState(
              currentHeaderInfoState,
              ProfileHeaderInfoEvent.OTHER_OPERATION,
            )
        }
      }
    }

  LaunchedEffect(showPrivateMessages) {
    if (!showPrivateMessages && section == ProfileSection.PRIVATE_MESSAGES) {
      section = ProfileSection.POSTS
    }
  }

  fun bangumiPagingState(type: Int): ProfileBangumiPagingState =
    if (type == 1) bangumiPaging else dramaPaging

  fun updateBangumiPagingState(type: Int, value: ProfileBangumiPagingState) {
    if (type == 1) bangumiPaging = value else dramaPaging = value
  }

  suspend fun loadBangumiTypes(
    types: List<Int>,
    refresh: Boolean,
    expectedToken: Long,
  ) {
    val requests =
      types.distinct().mapNotNull { type ->
        val current = bangumiPagingState(type)
        if (current.loading || (!refresh && !current.hasMore)) null
        else type to if (refresh) 1 else current.page + 1
      }
    if (requests.isEmpty()) return
    requests.forEach { (type, _) ->
      updateBangumiPagingState(
        type,
        bangumiPagingState(type).copy(loading = true, error = null),
      )
    }
    val results =
      coroutineScope {
        requests
          .map { request ->
            async {
              request to
                try {
                  Result.success(
                    withContext(Dispatchers.IO) {
                      BiliApi.getSpaceBangumi(
                        mid = profile?.mid ?: 0L,
                        type = request.first,
                        page = request.second,
                      )
                    }
                  )
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                  throw cancelled
                } catch (error: Exception) {
                  Result.failure<SpaceBangumiResponse>(error)
                }
            }
          }
          .awaitAll()
      }
    if (
      expectedToken != bangumiRequestToken ||
        section != ProfileSection.BANGUMI
    ) {
      return
    }
    val addedCards = mutableListOf<SpaceContentCard>()
    results.forEach { (request, result) ->
      val type = request.first
      val page = request.second
      result
        .onSuccess { response ->
          addedCards += response.cards
          updateBangumiPagingState(
            type,
            ProfileBangumiPagingState(
              page = page,
              hasMore = response.hasMore,
              loading = false,
            ),
          )
        }
        .onFailure { error ->
          updateBangumiPagingState(
            type,
            bangumiPagingState(type).copy(
              loading = false,
              error = error.message ?: "加载失败",
            ),
          )
        }
    }
    extraCards =
      ((if (refresh) emptyList() else extraCards) + addedCards).distinctBy { it.id }
  }

  fun loadMoreBangumi(filter: ProfileBangumiFilter) {
    val types =
      profileBangumiTypesToLoad(
        filter = filter,
        bangumiHasMore = bangumiPaging.hasMore,
        dramaHasMore = dramaPaging.hasMore,
      )
    if (types.isEmpty()) return
    val expectedToken = bangumiRequestToken
    scope.launch { loadBangumiTypes(types, refresh = false, expectedToken = expectedToken) }
  }

  LaunchedEffect(profile?.mid, section, extraRefreshGeneration, loggedIn) {
    val mid = profile?.mid ?: return@LaunchedEffect
    bangumiRequestToken += 1L
    if (section == ProfileSection.POSTS) return@LaunchedEffect
    if (section == ProfileSection.DYNAMICS) {
      onEnsureDynamics()
      return@LaunchedEffect
    }
    if (section == ProfileSection.PRIVATE_MESSAGES) {
      onPrivateMessagesSelected(mid, profile.name, profile.face)
      return@LaunchedEffect
    }
    if (section == ProfileSection.BANGUMI) {
      extraCards = emptyList()
      extraError = null
      bangumiPaging = ProfileBangumiPagingState()
      dramaPaging = ProfileBangumiPagingState()
      loadBangumiTypes(
        types = listOf(1, 2),
        refresh = true,
        expectedToken = bangumiRequestToken,
      )
      return@LaunchedEffect
    }
    extraLoading = true
    extraError = null
    extraCards = emptyList()
    runCatching {
        withContext(Dispatchers.IO) {
          when (section) {
            ProfileSection.COLLECTIONS -> BiliApi.getSpaceCollections(mid)
            ProfileSection.PRIVATE_MESSAGES -> emptyList()
            else -> emptyList()
          }
        }
      }
      .onSuccess { extraCards = it }
      .onFailure { extraError = it.message ?: "加载失败" }
    extraLoading = false
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
    contentColor = MaterialTheme.colorScheme.onBackground,
  ) {
    Row(Modifier.fillMaxSize().statusBarsPadding()) {
        ProfileMenu(
          selected = section,
          showPrivateMessages = showPrivateMessages,
          searchQuery = contentSearchQuery,
        onBack = onBack,
        onSearchQueryChange = { contentSearchQuery = it },
        onSelected = {
          headerInfoState = ProfileHeaderInfoState.COLLAPSED
          if (it != ProfileSection.DYNAMICS) onSelectedDynamicIdChange(null)
          section = it
          contentSearchQuery = ""
        },
      )
      Column(Modifier.weight(1f).fillMaxHeight().padding(18.dp)) {
        if (!privateMessageImeVisible) {
          ProfileHeader(
            profile = profile,
            showFollowButton = showFollowButton,
            followed = followed,
            followBusy = followBusy,
            followingGroups = followingGroups,
            followingGroupsLoading = followingGroupsLoading,
            loggedIn = loggedIn,
            profileIpAuthorized = profileIpAuthorized,
            onLoadFollowingGroups = onLoadFollowingGroups,
            onSelectFollowingGroup = onSelectFollowingGroup,
            onUnfollow = onUnfollow,
            onLogin = onLogin,
            onAuthorizeProfileIp = onAuthorizeProfileIp,
            onAvatarBoundsChanged = onAvatarBoundsChanged,
            avatarVisible = avatarVisible,
            chromeVisible = headerChromeVisible,
            placeholderFace = placeholderFace,
            placeholderName = placeholderName,
            infoState = headerInfoState,
            onInfoEvent = { event ->
              if (!transitionRunning) {
                headerInfoState = reduceProfileHeaderInfoState(headerInfoState, event)
              }
            },
            interactionLocked = transitionRunning,
          )
          Spacer(Modifier.height(14.dp))
        }
        PullRefreshContainer(
          enabled = section != ProfileSection.PRIVATE_MESSAGES,
          refreshing =
            when (section) {
              ProfileSection.POSTS -> loading
              ProfileSection.DYNAMICS -> dynamicsLoading
              ProfileSection.BANGUMI -> bangumiPaging.loading || dramaPaging.loading
              ProfileSection.PRIVATE_MESSAGES -> false
              else -> extraLoading
            },
          onRefresh = {
            when (section) {
              ProfileSection.POSTS -> onRefresh()
              ProfileSection.DYNAMICS -> onRefreshDynamics()
              ProfileSection.PRIVATE_MESSAGES ->
                profile?.let { onPrivateMessagesSelected(it.mid, it.name, it.face) }
              else -> extraRefreshGeneration++
            }
          },
          modifier = Modifier.weight(1f).fillMaxWidth().then(contentInteractionObserver),
        ) {
          when (section) {
            ProfileSection.POSTS ->
              ProfileVideoGrid(
                videos.filter { video ->
                    matchesProfileContentSearch(
                      contentSearchQuery,
                      video.title,
                      video.description,
                      video.uploader,
                    )
                  },
                loading,
                hasMore,
                error,
                onVideoClick,
                onVideoLongClick,
                onLoadMore,
                onRetry,
                hiddenCoverItemId,
                onVideoBoundsChanged,
                emptyMessage =
                  if (contentSearchQuery.isBlank()) "暂无公开投稿" else "没有找到相关投稿",
                searchQuery = contentSearchQuery,
                onScrollStarted = {
                  if (!transitionRunning) {
                    headerInfoState =
                      reduceProfileHeaderInfoState(
                        headerInfoState,
                        ProfileHeaderInfoEvent.OTHER_OPERATION,
                      )
                  }
                },
              )
            ProfileSection.DYNAMICS ->
              ProfileDynamicGrid(
                items = dynamics,
                searchQuery = contentSearchQuery,
                loading = dynamicsLoading,
                hasMore = dynamicsHasMore,
                error = dynamicsError,
                selectedDynamicId = selectedDynamicId,
                profile = profile,
                currentAccountMid = currentAccountMid,
                settings = settings,
                onVideoClick = onVideoClick,
                onVideoLongClick = onVideoLongClick,
                hiddenCoverItemId = hiddenCoverItemId,
                onVideoBoundsChanged = onVideoBoundsChanged,
                onArticleClick = onArticleClick,
                hiddenArticleItemId = hiddenArticleItemId,
                onArticleBoundsChanged = onArticleBoundsChanged,
                onCommentProfileClick = onCommentProfileClick,
                onAvatarProfileClick = onAvatarProfileClick,
                hiddenCommentAvatarRpid = hiddenCommentAvatarRpid,
                hiddenAvatarSourceBounds = hiddenDynamicAvatarBounds,
                backHandlingEnabled = backHandlingEnabled,
                onSelectedDynamicIdChange = onSelectedDynamicIdChange,
                onDynamicLike = onDynamicLike,
                onDynamicDelete = onDynamicDelete,
                onDynamicPin = onDynamicPin,
                onLoadMore = onLoadMoreDynamics,
                onScrollStarted = {
                  if (!transitionRunning) {
                    headerInfoState =
                      reduceProfileHeaderInfoState(
                        headerInfoState,
                        ProfileHeaderInfoEvent.OTHER_OPERATION,
                      )
                  }
                },
              )
            ProfileSection.PRIVATE_MESSAGES -> privateMessageContent()
            ProfileSection.BANGUMI ->
              ProfileBangumiGrid(
                cards = extraCards,
                initialLoading =
                  extraCards.isEmpty() && (bangumiPaging.loading || dramaPaging.loading),
                loadingMore =
                  extraCards.isNotEmpty() && (bangumiPaging.loading || dramaPaging.loading),
                bangumiHasMore = bangumiPaging.hasMore,
                dramaHasMore = dramaPaging.hasMore,
                bangumiError = bangumiPaging.error,
                dramaError = dramaPaging.error,
                searchQuery = contentSearchQuery,
                profile = profile,
                onLoadMore = ::loadMoreBangumi,
                onBangumiClick = onBangumiClick,
                onVideoLongClick = onVideoLongClick,
                hiddenCoverItemId = hiddenCoverItemId,
                onVideoBoundsChanged = onVideoBoundsChanged,
                onScrollStarted = {
                  if (!transitionRunning) {
                    headerInfoState =
                      reduceProfileHeaderInfoState(
                        headerInfoState,
                        ProfileHeaderInfoEvent.OTHER_OPERATION,
                      )
                  }
                },
              )
            ProfileSection.COLLECTIONS ->
              ProfileExtraGrid(
                cards =
                  extraCards.filter { card ->
                    matchesProfileContentSearch(contentSearchQuery, card.title, card.subtitle)
                  },
                loading = extraLoading,
                error = extraError,
                emptyMessage =
                  if (contentSearchQuery.isBlank()) "暂无公开内容" else "没有找到相关内容",
                searchQuery = contentSearchQuery,
                profile = profile,
                onVideoClick = onVideoClick,
                onVideoLongClick = onVideoLongClick,
                hiddenCoverItemId = hiddenCoverItemId,
                onVideoBoundsChanged = onVideoBoundsChanged,
                onScrollStarted = {
                  if (!transitionRunning) {
                    headerInfoState =
                      reduceProfileHeaderInfoState(
                        headerInfoState,
                        ProfileHeaderInfoEvent.OTHER_OPERATION,
                      )
                  }
                },
              )
          }
        }
      }
    }
  }
}

@Composable
private fun ProfileMenu(
  selected: ProfileSection,
  showPrivateMessages: Boolean,
  searchQuery: String,
  onBack: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSelected: (ProfileSection) -> Unit,
) {
  Column(
    Modifier.width(238.dp)
      .fillMaxHeight()
      .background(MaterialTheme.colorScheme.surface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
    if (selected != ProfileSection.PRIVATE_MESSAGES) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("搜索${selected.label}") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
      )
    } else {
      Spacer(Modifier.height(56.dp))
    }
    ProfileSection.entries
      .filter { it != ProfileSection.PRIVATE_MESSAGES || showPrivateMessages }
      .forEach { item ->
      val active = item == selected
      Text(
        item.label,
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
              if (active) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surface
            )
            .clickable { onSelected(item) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        color =
          if (active) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun ProfileHeader(
  profile: SpaceProfile?,
  showFollowButton: Boolean,
  followed: Boolean,
  followBusy: Boolean,
  followingGroups: List<FollowingGroup>,
  followingGroupsLoading: Boolean,
  loggedIn: Boolean,
  profileIpAuthorized: Boolean,
  onLoadFollowingGroups: () -> Unit,
  onSelectFollowingGroup: (Long) -> Unit,
  onUnfollow: () -> Unit,
  onLogin: () -> Unit,
  onAuthorizeProfileIp: () -> Unit,
  onAvatarBoundsChanged: (Rect) -> Unit,
  avatarVisible: Boolean,
  chromeVisible: Boolean,
  placeholderFace: String?,
  placeholderName: String?,
  infoState: ProfileHeaderInfoState,
  onInfoEvent: (ProfileHeaderInfoEvent) -> Unit,
  interactionLocked: Boolean,
) {
  val darkTheme = isSystemInDarkTheme()
  val context = LocalContext.current
  val backdropLayer = rememberGraphicsLayer()
  var backdropBounds by remember { mutableStateOf(Rect.Zero) }
  var capsuleVisible by remember(profile?.mid) { mutableStateOf(false) }
  var detailsVisible by remember(profile?.mid) { mutableStateOf(false) }
  var bannerReady by remember(profile?.banner) { mutableStateOf(false) }
  val infoRequested = infoState == ProfileHeaderInfoState.EXPANDED
  val infoHidden = infoState == ProfileHeaderInfoState.HIDDEN
  val bannerAlpha by
    animateFloatAsState(
      targetValue = if (bannerReady) 1f else 0f,
      animationSpec = tween(280),
      label = "profileBannerAlpha",
    )
  val detailsAlpha by
    animateFloatAsState(
      targetValue = if (detailsVisible) 1f else 0f,
      animationSpec = tween(180),
      label = "profileDetailsAlpha",
    )
  val chromeAlpha by
    animateFloatAsState(
      targetValue = if (chromeVisible) 1f else 0f,
      animationSpec = tween(if (chromeVisible) 180 else 140),
      label = "profileHeaderChromeAlpha",
    )
  LaunchedEffect(profile?.mid) {
    if (profile == null) {
      detailsVisible = false
      capsuleVisible = false
    } else {
      capsuleVisible = true
      delay(230)
      detailsVisible = true
    }
  }
  Surface(
    modifier = Modifier.fillMaxWidth().height(210.dp),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    shadowElevation = 4.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Box {
      Box(
        Modifier.fillMaxSize()
          .clickable(
            enabled = !interactionLocked && profile != null,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          ) {
            onInfoEvent(ProfileHeaderInfoEvent.BANNER_TAP)
          }
          .onGloballyPositioned { backdropBounds = it.boundsInRoot() }
          .drawWithContent {
            backdropLayer.record { this@drawWithContent.drawContent() }
            drawLayer(backdropLayer)
          }
      ) {
        if (!profile?.banner.isNullOrBlank()) {
          AsyncImage(
            model = profile.banner,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = bannerAlpha },
            contentScale = ContentScale.Crop,
            onSuccess = { bannerReady = true },
          )
        }
        Box(
          Modifier.fillMaxSize()
            .background(
              Brush.verticalGradient(
                if (darkTheme)
                  listOf(Color.Black.copy(alpha = .22f), Color.Black.copy(alpha = .58f))
                else listOf(Color.Transparent, Color.Black.copy(alpha = .44f))
              )
            )
        )
      }
      Box(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 22.dp)) {
        Box(
          Modifier.size(92.dp)
            .onGloballyPositioned { onAvatarBoundsChanged(it.boundsInRoot()) }
            .graphicsLayer { alpha = if (avatarVisible) chromeAlpha else 0f }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = .2f))
            .clickable(
              enabled = !interactionLocked,
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) {
              onInfoEvent(ProfileHeaderInfoEvent.OTHER_OPERATION)
            },
          contentAlignment = Alignment.Center,
        ) {
          val face = profile?.face?.takeIf(String::isNotBlank) ?: placeholderFace
          if (!face.isNullOrBlank()) {
            AsyncImage(
              model = face,
              contentDescription = profile?.name ?: placeholderName,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
          } else {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
          }
        }
      }
      BoxWithConstraints(
        Modifier.align(Alignment.BottomStart)
          .fillMaxWidth()
          .clipToBounds()
          .padding(start = 130.dp, end = 22.dp, bottom = 22.dp)
      ) {
        val followReserve = if (showFollowButton) 126.dp else 0.dp
        val collapsedWidth = minOf(440.dp, (maxWidth - followReserve).coerceAtLeast(280.dp))
        val capsuleWidth by
          animateDpAsState(
            targetValue = if (infoRequested) maxWidth else collapsedWidth,
            animationSpec =
              spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
              ),
            label = "profileInfoWidth",
          )
        val capsuleHeight by
          animateDpAsState(
            targetValue = if (infoRequested) 166.dp else 116.dp,
            animationSpec =
              spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
              ),
            label = "profileInfoHeight",
          )
        val followExitProgress by
          animateFloatAsState(
            targetValue = if (infoRequested) 1f else 0f,
            animationSpec =
              spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
              ),
            label = "profileFollowExitProgress",
          )
        var followButtonWidthPx by remember { mutableStateOf(0) }
        AnimatedVisibility(
          visible = chromeVisible && capsuleVisible && !infoHidden,
          modifier = Modifier.align(Alignment.CenterStart),
          enter = fadeIn(tween(220)),
          exit = fadeOut(tween(140)),
        ) {
          BackdropGlassSurface(
            backdropLayer = backdropLayer,
            backdropBounds = backdropBounds,
            modifier =
              Modifier.width(capsuleWidth)
                .height(capsuleHeight)
                .combinedClickable(
                  enabled = !interactionLocked && profile != null,
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  onClick = { onInfoEvent(ProfileHeaderInfoEvent.CAPSULE_TAP) },
                  onLongClick = {
                    profile?.let {
                      val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      clipboard.setPrimaryClip(ClipData.newPlainText("UID", it.mid.toString()))
                      Toast.makeText(context, "已复制 UID ${it.mid}", Toast.LENGTH_SHORT).show()
                    }
                  },
                ),
            shape = RoundedCornerShape(18.dp),
            blurRadius = 10.dp,
            containerColor = Color.Black.copy(alpha = .34f),
            border = BorderStroke(.75.dp, Color.White.copy(alpha = .22f)),
          ) {
            Column(
              Modifier.fillMaxSize()
                .graphicsLayer { alpha = detailsAlpha }
                .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  profile?.name ?: placeholderName ?: "正在加载…",
                  modifier = Modifier.weight(1f),
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  color = if (profile?.vipActive == true) Color(0xFFFF77A8) else Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                profile?.let {
                  val sex =
                    when (it.sex) {
                      "男" -> "♂"
                      "女" -> "♀"
                      else -> "保密"
                    }
                  ProfileBannerBadge(sex)
                  ProfileLevelIcon(it.level)
                  if (it.vipLabel.isNotBlank()) {
                    if (it.vipIconUrl.isNotBlank())
                      AsyncImage(
                        model = it.vipIconUrl,
                        contentDescription = it.vipLabel,
                        modifier = Modifier.height(22.dp),
                        contentScale = ContentScale.Fit,
                      )
                    else ProfileBannerBadge(it.vipLabel, vip = true)
                  }
                }
              }
              if (profile != null) {
                val displaySignature = formatProfileSignature(profile.signature)
                Text(
                  "${profile.followingCount} 关注  ·  ${formatProfileFollowerCount(profile.followerCount)} 粉丝  ·  UID ${profile.mid}",
                  color = Color.White.copy(alpha = .82f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (profile.ipLocation.isNotBlank()) {
                  Text(
                    "IP属地：${profile.ipLocation}",
                    modifier = Modifier.padding(top = 2.dp),
                    color = Color.White.copy(alpha = .78f),
                  )
                } else if (loggedIn && !profileIpAuthorized && infoRequested) {
                  TextButton(
                    onClick = onAuthorizeProfileIp,
                    enabled = !interactionLocked,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                  ) {
                    Text("授权显示 IP 属地", color = Color.White.copy(alpha = .86f))
                  }
                }
                if (displaySignature.isNotBlank())
                  BiliRichText(
                    text = displaySignature,
                    emotes = emptyMap(),
                    maxLines = if (infoRequested) 3 else 1,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = .9f)),
                  )
              }
            }
          }
        }
        AnimatedVisibility(
          visible = chromeVisible && showFollowButton && detailsVisible && !infoHidden,
          modifier =
            Modifier.align(Alignment.CenterEnd).graphicsLayer {
              translationX = (followButtonWidthPx.toFloat() + 22.dp.toPx()) * followExitProgress
              alpha =
                if (followExitProgress <= .72f) 1f
                else ((1f - followExitProgress) / .28f).coerceIn(0f, 1f)
            },
          enter = fadeIn(tween(180)),
          exit = fadeOut(tween(120)),
        ) {
          BackdropGlassSurface(
            backdropLayer = backdropLayer,
            backdropBounds = backdropBounds,
            modifier = Modifier.onSizeChanged { followButtonWidthPx = it.width },
            shape = RoundedCornerShape(22.dp),
            blurRadius = 12.dp,
            containerColor = Color.White.copy(alpha = .16f),
            border = BorderStroke(.75.dp, Color.White.copy(alpha = .3f)),
          ) {
            FollowButton(
              followed = followed,
              busy = followBusy,
              groups = followingGroups,
              groupsLoading = followingGroupsLoading,
              loggedIn = loggedIn,
              onLoadGroups = onLoadFollowingGroups,
              onSelectGroup = onSelectFollowingGroup,
              onUnfollow = onUnfollow,
              onLogin = onLogin,
              transparentContainer = true,
            )
          }
        }
      }
    }
  }
}

internal fun formatProfileFollowerCount(count: Long): String {
  if (count < 10_000L) return count.toString()
  val tenths = ((count + 500L) / 1_000L).coerceAtLeast(10L)
  val whole = tenths / 10L
  val decimal = tenths % 10L
  return if (decimal == 0L) "${whole}w" else "$whole.${decimal}w"
}

internal fun formatProfileSignature(signature: String): String =
  signature.replace(Regex("\\r\\n|\\r|\\n"), " ").replace(Regex(" {2,}"), " ").trim()

@Composable
private fun ProfileBannerBadge(text: String, vip: Boolean = false) {
  Surface(
    shape = RoundedCornerShape(9.dp),
    color = Color.Black.copy(alpha = .3f),
    contentColor = if (vip) Color(0xFFFF77A8) else Color.White,
  ) {
    Text(
      text,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
      style = MaterialTheme.typography.labelSmall,
    )
  }
}

@Composable
private fun ProfileLevelIcon(level: Int) {
  val fileName = if (level >= 6) "level_h.svg" else "level_${level.coerceIn(0, 5)}.svg"
  AsyncImage(
    model = "https://i0.hdslb.com/bfs/seed/jinkela/short/webui/user-profile/img/$fileName",
    contentDescription = "等级 $level",
    modifier = Modifier.size(30.dp),
    contentScale = ContentScale.Fit,
  )
}

@Composable
private fun ProfileBadge(text: String) {
  Text(
    text,
    modifier =
      Modifier.clip(RoundedCornerShape(9.dp))
        .background(MaterialTheme.colorScheme.secondaryContainer)
        .padding(horizontal = 7.dp, vertical = 3.dp),
    color = MaterialTheme.colorScheme.onSecondaryContainer,
    style = MaterialTheme.typography.labelSmall,
  )
}

@Composable
private fun ProfileVideoGrid(
  videos: List<FeedItem>,
  loading: Boolean,
  hasMore: Boolean,
  error: String?,
  onVideoClick: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  onLoadMore: () -> Unit,
  onRetry: () -> Unit,
  hiddenCoverItemId: String?,
  onVideoBoundsChanged: (FeedItem, Rect) -> Unit,
  emptyMessage: String,
  searchQuery: String,
  onScrollStarted: () -> Unit,
) {
  if (videos.isEmpty()) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      when {
        loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        error != null ->
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text("重新试试") }
          }
        else -> Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    return
  }
  val state = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(state)
  LaunchedEffect(searchQuery) { state.scrollToItem(0) }
  val nearEnd by remember {
    derivedStateOf {
      val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      last >= state.layoutInfo.totalItemsCount - 5
    }
  }
  LaunchedEffect(nearEnd, hasMore, loading, imageLoadPolicy.mode) {
    if (
      nearEnd && hasMore && !loading && imageLoadPolicy.mode != FeedImageLoadMode.PAUSED
    ) onLoadMore()
  }
  LaunchedEffect(state.isScrollInProgress) {
    if (state.isScrollInProgress) onScrollStarted()
  }
  CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
    LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    state = state,
    contentPadding = PaddingValues(bottom = 112.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
    itemsIndexed(videos, key = { _, video -> video.id }) { index, video ->
      VideoCardReveal(index = index, batchKey = videos.firstOrNull()?.id, itemKey = video.id) {
        ProfileVideoCard(
          video = video,
          onVideoClick = onVideoClick,
          onVideoLongClick = onVideoLongClick,
          coverVisible = video.id != hiddenCoverItemId,
          onBoundsChanged = { onVideoBoundsChanged(video, it) },
        )
      }
    }
    if (error != null) {
      item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
          TextButton(onClick = onRetry) { Text("重试") }
        }
      }
    }
    }
  }
}

@Composable
private fun ProfileVideoCard(
  video: FeedItem,
  onVideoClick: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  coverVisible: Boolean,
  onBoundsChanged: (Rect) -> Unit,
) {
  var coverBounds by remember(video.id) { mutableStateOf(Rect.Zero) }
  PressableVideoCard(
    onClick = { onVideoClick(video, coverBounds) },
    onLongClick = { onVideoLongClick(video) },
  ) {
    FeedCardContent(
      item = video,
      profileClickEnabled = false,
      coverVisible = coverVisible,
      onCoverBoundsChanged = {
        coverBounds = it
        onBoundsChanged(it)
      },
    )
  }
}

@Composable
private fun ProfileExtraGrid(
  cards: List<SpaceContentCard>,
  loading: Boolean,
  error: String?,
  emptyMessage: String,
  searchQuery: String,
  profile: SpaceProfile?,
  onVideoClick: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  hiddenCoverItemId: String?,
  onVideoBoundsChanged: (FeedItem, Rect) -> Unit,
  onScrollStarted: () -> Unit,
) {
  if (loading) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
    }
    return
  }
  if (cards.isEmpty()) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(error ?: emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    return
  }
  val state = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(state)
  LaunchedEffect(searchQuery) { state.scrollToItem(0) }
  LaunchedEffect(state.isScrollInProgress) {
    if (state.isScrollInProgress) onScrollStarted()
  }
  CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
    LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    state = state,
    contentPadding = PaddingValues(bottom = 112.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
    itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
      var coverBounds by remember(card.id) { mutableStateOf(Rect.Zero) }
      val video =
        FeedItem(
          id = card.bvid,
          title = card.title,
          videoUrl = "https://www.bilibili.com/video/${card.bvid}",
          coverUrl = card.coverUrl,
          uploader = profile?.name,
          playCount = null,
          duration = null,
          uploaderFace = profile?.face,
          uploaderMid = profile?.mid ?: 0,
          description = card.subtitle,
        )
      VideoCardReveal(index = index, batchKey = cards.firstOrNull()?.id, itemKey = card.id) {
        PressableVideoCard(
          enabled = card.bvid.isNotBlank(),
          onClick = { onVideoClick(video, coverBounds) },
          onLongClick = { onVideoLongClick(video) },
          shape = RoundedCornerShape(18.dp),
        ) {
          VideoCardGradient(coverUrl = card.coverUrl, loadKey = card.id) {
            Column(Modifier.padding(10.dp)) {
              if (card.coverUrl.isNotBlank())
                CoverImage(
                  coverUrl = card.coverUrl,
                  contentDescription = card.title,
                  modifier =
                    Modifier.fillMaxWidth()
                      .height(130.dp)
                      .onGloballyPositioned {
                        coverBounds = it.boundsInRoot()
                        onVideoBoundsChanged(video, coverBounds)
                      }
                      .graphicsLayer {
                        this.alpha = if (video.id != hiddenCoverItemId) 1f else 0f
                      }
                      .clip(RoundedCornerShape(14.dp)),
                  shape = RoundedCornerShape(14.dp),
                  enforceAspectRatio = false,
                  loadKey = card.id,
                )
              Text(
                card.title,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
              )
              if (card.subtitle.isNotBlank())
                Text(
                  card.subtitle,
                  maxLines = 3,
                  overflow = TextOverflow.Ellipsis,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  style = MaterialTheme.typography.bodySmall,
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
private fun ProfileBangumiGrid(
  cards: List<SpaceContentCard>,
  initialLoading: Boolean,
  loadingMore: Boolean,
  bangumiHasMore: Boolean,
  dramaHasMore: Boolean,
  bangumiError: String?,
  dramaError: String?,
  searchQuery: String,
  profile: SpaceProfile?,
  onLoadMore: (ProfileBangumiFilter) -> Unit,
  onBangumiClick: (SpaceContentCard, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  hiddenCoverItemId: String?,
  onVideoBoundsChanged: (FeedItem, Rect) -> Unit,
  onScrollStarted: () -> Unit,
) {
  var selectedFilter by
    rememberSaveable(profile?.mid) { mutableStateOf(ProfileBangumiFilter.ALL) }
  val filteredCards =
    remember(cards, selectedFilter, searchQuery) {
      filterProfileBangumi(cards, selectedFilter).filter { card ->
        matchesProfileContentSearch(searchQuery, card.title, card.subtitle)
      }
    }
  val state = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(state)
  val selectedHasMore =
    when (selectedFilter) {
      ProfileBangumiFilter.ALL -> bangumiHasMore || dramaHasMore
      ProfileBangumiFilter.BANGUMI -> bangumiHasMore
      ProfileBangumiFilter.DRAMA -> dramaHasMore
    }
  val selectedError =
    when (selectedFilter) {
      ProfileBangumiFilter.ALL -> bangumiError ?: dramaError
      ProfileBangumiFilter.BANGUMI -> bangumiError
      ProfileBangumiFilter.DRAMA -> dramaError
    }
  val nearEnd by
    remember {
      derivedStateOf {
        val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalItems = state.layoutInfo.totalItemsCount
        lastVisible >= totalItems - 6 && totalItems > 0
      }
    }
  LaunchedEffect(searchQuery, selectedFilter) { state.scrollToItem(0) }
  LaunchedEffect(
    nearEnd,
    selectedFilter,
    selectedHasMore,
    initialLoading,
    loadingMore,
    selectedError,
    imageLoadPolicy.mode,
  ) {
    if (
      nearEnd &&
        selectedHasMore &&
        !initialLoading &&
        !loadingMore &&
        selectedError == null &&
        imageLoadPolicy.mode != FeedImageLoadMode.PAUSED
    ) {
      onLoadMore(selectedFilter)
    }
  }
  LaunchedEffect(state.isScrollInProgress) {
    if (state.isScrollInProgress) onScrollStarted()
  }
  CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(5),
      state = state,
      contentPadding = PaddingValues(bottom = 112.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
        BangumiFilterRow(selected = selectedFilter, onSelected = { selectedFilter = it })
      }
      when {
        initialLoading ->
          item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Box(
              Modifier.fillMaxWidth().padding(vertical = 48.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
          }
        selectedError != null && filteredCards.isEmpty() ->
          item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Box(
              Modifier.fillMaxWidth().padding(vertical = 48.dp),
              contentAlignment = Alignment.Center,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(selectedError, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onLoadMore(selectedFilter) }) { Text("重新试试") }
              }
            }
          }
        filteredCards.isEmpty() ->
          item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Box(
              Modifier.fillMaxWidth().padding(vertical = 48.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                if (searchQuery.isBlank()) "暂时没有符合筛选的内容" else "没有找到相关内容",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        else ->
          itemsIndexed(filteredCards, key = { _, card -> card.id }) { index, card ->
            val video = card.toBangumiFeedItem(profile)
            BangumiPosterCard(
              card = card,
              video = video,
              index = index,
              batchKey = cards.firstOrNull()?.id,
              hiddenCoverItemId = hiddenCoverItemId,
              onClick = { bounds -> onBangumiClick(card, bounds) },
              onLongClick = { onVideoLongClick(video) },
              onBoundsChanged = { onVideoBoundsChanged(video, it) },
            )
          }
      }
      if (loadingMore) {
        item(
          key = "bangumi_loading_more",
          span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
        ) {
          Box(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
          }
        }
      } else if (selectedError != null && filteredCards.isNotEmpty()) {
        item(
          key = "bangumi_load_error",
          span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
        ) {
          Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(selectedError, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onLoadMore(selectedFilter) }) { Text("重试") }
          }
        }
      }
    }
  }
}

@Composable
internal fun BangumiPosterCard(
  card: SpaceContentCard,
  video: FeedItem,
  index: Int,
  batchKey: String?,
  hiddenCoverItemId: String?,
  onClick: (Rect) -> Unit,
  onLongClick: () -> Unit,
  onBoundsChanged: (Rect) -> Unit,
) {
  var coverBounds by remember(card.id) { mutableStateOf(Rect.Zero) }
  VideoCardReveal(index = index, batchKey = batchKey, itemKey = card.id) {
    PressableVideoCard(
      enabled = card.videoUrl.isNotBlank(),
      onClick = { onClick(coverBounds) },
      onLongClick = onLongClick,
      shape = RoundedCornerShape(18.dp),
    ) {
      VideoCardGradient(coverUrl = card.coverUrl, loadKey = card.id) {
        Column(Modifier.padding(8.dp)) {
          CoverImage(
            coverUrl = card.coverUrl,
            contentDescription = card.title,
            modifier =
              Modifier.fillMaxWidth()
                .aspectRatio(3f / 4f)
                .onGloballyPositioned {
                  coverBounds = it.boundsInRoot()
                  onBoundsChanged(coverBounds)
                }
                .graphicsLayer { alpha = if (video.id != hiddenCoverItemId) 1f else 0f },
            shape = RoundedCornerShape(14.dp),
            enforceAspectRatio = false,
            requestWidth = 360,
            requestHeight = 480,
            loadKey = card.id,
            contentScale = ContentScale.Fit,
          )
          Text(
            card.title,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            card.subtitle.ifBlank { " " },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }
}

@Composable
private fun BangumiFilterRow(
  selected: ProfileBangumiFilter,
  onSelected: (ProfileBangumiFilter) -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ProfileBangumiFilter.entries.forEach { filter ->
      androidx.compose.material3.FilterChip(
        selected = selected == filter,
        onClick = { onSelected(filter) },
        label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
      )
    }
  }
}

private fun SpaceContentCard.toBangumiFeedItem(profile: SpaceProfile?) =
  FeedItem(
    id = id,
    title = title,
    videoUrl = videoUrl,
    coverUrl = coverUrl,
    uploader = profile?.name,
    playCount = null,
    duration = null,
    uploaderFace = profile?.face,
    uploaderMid = profile?.mid ?: 0,
    description = subtitle,
  )

/** The profile API has no per-section search endpoint, so filter the retained section data in place. */
internal fun matchesProfileContentSearch(query: String, vararg values: String?): Boolean {
  val keyword = query.trim()
  return keyword.isBlank() || values.any { it?.contains(keyword, ignoreCase = true) == true }
}

package dev.openbili.webdemo.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import dev.openbili.webdemo.api.BiliFollowApi
import dev.openbili.webdemo.api.BiliSpaceApi
import dev.openbili.webdemo.api.FollowingGroup
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceDynamicItem
import dev.openbili.webdemo.api.SpaceProfile
import dev.openbili.webdemo.feed.FeedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 根导航器拥有的资料数据、资料转场锚点和关注变更。 */
internal class AppRootProfileState {
  val followingStates = mutableStateMapOf<Long, Boolean>()
  val followingBusy = mutableStateMapOf<Long, Boolean>()
  var followingGroups by mutableStateOf<List<FollowingGroup>>(emptyList())
  var followingGroupsLoading by mutableStateOf(false)
  var followingGroupsLoaded by mutableStateOf(false)

  var profileMid by mutableStateOf<Long?>(null)
  var profileAvatarBounds by mutableStateOf(Rect.Zero)
  var commentProfileTransition by mutableStateOf<CommentProfileTransition?>(null)
  var commentProfileReturnTransition by mutableStateOf<CommentProfileTransition?>(null)
  var avatarProfileTransition by mutableStateOf<AvatarProfileTransition?>(null)
  var avatarProfileReturnTransition by mutableStateOf<AvatarProfileTransition?>(null)
  var profileTransitionJob by mutableStateOf<Job?>(null)
  var spaceProfile by mutableStateOf<SpaceProfile?>(null)
  var spaceVideos by mutableStateOf<List<FeedItem>>(emptyList())
  var spacePage by mutableStateOf(1)
  var spaceHasMore by mutableStateOf(false)
  var spaceLoading by mutableStateOf(false)
  var spaceError by mutableStateOf<String?>(null)
  var spaceDynamics by mutableStateOf<List<SpaceDynamicItem>>(emptyList())
  var spaceDynamicOffset by mutableStateOf("")
  var spaceDynamicHasMore by mutableStateOf(false)
  var spaceDynamicLoading by mutableStateOf(false)
  var spaceDynamicError by mutableStateOf<String?>(null)
  var selectedDynamicId by mutableStateOf<String?>(null)
  var spaceCollections by mutableStateOf<List<SpaceContentCard>>(emptyList())
  var spaceCollectionsLoading by mutableStateOf(false)
  var spaceCollectionsError by mutableStateOf<String?>(null)
  var selectedCollectionId by mutableStateOf<String?>(null)
  var spaceCollectionVideos by mutableStateOf<List<FeedItem>>(emptyList())
  var spaceCollectionPage by mutableStateOf(0)
  var spaceCollectionHasMore by mutableStateOf(false)
  var spaceCollectionLoading by mutableStateOf(false)
  var spaceCollectionError by mutableStateOf<String?>(null)
  var spaceCollectionTotal by mutableStateOf(0)
  private var spaceDynamicGeneration = 0L
  private var spaceCollectionGeneration = 0L
  private val dynamicLikeBusy = mutableSetOf<String>()
  private val dynamicManageBusy = mutableSetOf<String>()

  suspend fun profileDataCommitAllowed(mid: Long): Boolean {
    val session =
      commentProfileTransition?.takeIf { it.targetMid == mid } ?: return profileMid == mid
    while (
      profileMid == mid &&
        commentProfileTransition?.token == session.token &&
        session.blocksInput &&
        !session.closing
    ) {
      delay(16)
    }
    return profileMid == mid && !session.closing
  }

  fun loadSpacePage(mid: Long, page: Int, scope: CoroutineScope) {
    if (spaceLoading) return
    spaceLoading = true
    spaceError = null
    scope.launch {
      try {
        val result = withContext(Dispatchers.IO) { BiliSpaceApi.getSpaceVideos(mid, page) }
        if (!profileDataCommitAllowed(mid)) return@launch
        val loadedVideos =
          result.cards.map(::feedItemFromCard).map { video ->
            video.copy(
              uploader = spaceProfile?.name ?: video.uploader,
              uploaderFace = spaceProfile?.face ?: video.uploaderFace,
              uploaderMid = mid,
            )
          }
        spaceVideos =
          if (page == 1) loadedVideos else (spaceVideos + loadedVideos).distinctBy { it.id }
        spacePage = page
        spaceHasMore = result.hasMore
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        if (profileDataCommitAllowed(mid)) {
          val message = error.message.orEmpty()
          spaceError =
            if (
              message.contains("风控") ||
                message.contains("banned", ignoreCase = true) ||
                message.contains("risk", ignoreCase = true)
            ) {
              "个人空间暂时触发风控，请稍后重试 (・ω・)ノ"
            } else {
              message.ifBlank { "个人空间加载失败，请稍后重试" }
            }
          if (page == 1) spaceHasMore = false
        }
      } finally {
        if (profileMid == mid) spaceLoading = false
      }
    }
  }

  fun loadSpaceDynamics(mid: Long, refresh: Boolean, scope: CoroutineScope) {
    if (profileMid != mid || spaceDynamicLoading) return
    if (!refresh && spaceDynamics.isNotEmpty() && !spaceDynamicHasMore) return
    val offset = if (refresh || spaceDynamics.isEmpty()) "" else spaceDynamicOffset
    val generation = ++spaceDynamicGeneration
    spaceDynamicLoading = true
    spaceDynamicError = null
    scope.launch {
      val result =
        withContext(Dispatchers.IO) { runCatching { BiliSpaceApi.getSpaceDynamics(mid, offset) } }
      if (profileMid == mid && generation == spaceDynamicGeneration) {
        result
          .onSuccess { response ->
            spaceDynamics =
              if (offset.isBlank()) response.items
              else (spaceDynamics + response.items).distinctBy { it.id }
            spaceDynamicOffset = response.offset
            spaceDynamicHasMore = response.hasMore
          }
          .onFailure { spaceDynamicError = it.message ?: "动态加载失败" }
        spaceDynamicLoading = false
      }
    }
  }

  fun loadSpaceCollections(mid: Long, refresh: Boolean, scope: CoroutineScope) {
    if (profileMid != mid || spaceCollectionsLoading) return
    if (!refresh && spaceCollections.isNotEmpty()) return
    val generation = ++spaceCollectionGeneration
    spaceCollectionsLoading = true
    spaceCollectionsError = null
    scope.launch {
      val result = withContext(Dispatchers.IO) { runCatching { BiliSpaceApi.getSpaceCollections(mid) } }
      if (profileMid == mid && generation == spaceCollectionGeneration) {
        result
          .onSuccess { collections ->
            spaceCollections = collections
            if (
              selectedCollectionId != null && collections.none { it.id == selectedCollectionId }
            ) {
              clearSelectedSpaceCollection()
            }
          }
          .onFailure { spaceCollectionsError = it.message ?: "合集和系列加载失败" }
        spaceCollectionsLoading = false
      }
    }
  }

  fun selectSpaceCollection(
    mid: Long,
    collection: SpaceContentCard,
    scope: CoroutineScope,
  ) {
    if (profileMid != mid || collection.collectionId <= 0L) return
    if (selectedCollectionId != collection.id) {
      spaceCollectionGeneration++
      selectedCollectionId = collection.id
      spaceCollectionVideos = emptyList()
      spaceCollectionPage = 0
      spaceCollectionHasMore = true
      spaceCollectionLoading = false
      spaceCollectionError = null
      spaceCollectionTotal = collection.collectionTotal
    }
    if (spaceCollectionVideos.isEmpty() && !spaceCollectionLoading) {
      loadSpaceCollectionPage(mid, collection, page = 1, scope = scope)
    }
  }

  fun loadSpaceCollectionPage(
    mid: Long,
    collection: SpaceContentCard,
    page: Int,
    scope: CoroutineScope,
  ) {
    if (
      profileMid != mid ||
        selectedCollectionId != collection.id ||
        spaceCollectionLoading ||
        page <= 0 ||
        (page > 1 && !spaceCollectionHasMore)
    ) {
      return
    }
    val generation = ++spaceCollectionGeneration
    spaceCollectionLoading = true
    spaceCollectionError = null
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching { BiliSpaceApi.getSpaceCollectionVideos(mid, collection, page) }
        }
      if (
        profileMid == mid &&
          selectedCollectionId == collection.id &&
          generation == spaceCollectionGeneration
      ) {
        result
          .onSuccess { response ->
            val loaded =
              response.cards.map(::feedItemFromCard).map { video ->
                video.copy(
                  uploader = video.uploader.orEmpty().ifBlank { spaceProfile?.name.orEmpty() },
                  uploaderFace =
                    video.uploaderFace.orEmpty().ifBlank { spaceProfile?.face.orEmpty() },
                  uploaderMid = video.uploaderMid.takeIf { it > 0L } ?: mid,
                )
              }
            spaceCollectionVideos =
              if (page == 1) loaded else (spaceCollectionVideos + loaded).distinctBy { it.id }
            spaceCollectionPage = page
            spaceCollectionHasMore = response.hasMore
            spaceCollectionTotal = response.total
          }
          .onFailure { spaceCollectionError = it.message ?: "合集和系列视频加载失败" }
        spaceCollectionLoading = false
      }
    }
  }

  fun clearSelectedSpaceCollection() {
    spaceCollectionGeneration++
    selectedCollectionId = null
    spaceCollectionLoading = false
  }

  fun toggleDynamicLike(
    item: SpaceDynamicItem,
    accountMid: Long,
    onLogin: () -> Unit,
    context: Context,
    scope: CoroutineScope,
  ) {
    if (accountMid <= 0L) {
      onLogin()
      return
    }
    if (!dynamicLikeBusy.add(item.id)) return
    val currentItem = spaceDynamics.firstOrNull { it.id == item.id } ?: item
    val targetLiked = !currentItem.liked
    fun updated(source: SpaceDynamicItem, liked: Boolean): SpaceDynamicItem =
      source.copy(
        liked = liked,
        likeCount =
          (source.likeCount + if (liked == source.liked) 0 else if (liked) 1 else -1).coerceAtLeast(
            0L
          ),
      )
    spaceDynamics = spaceDynamics.map { if (it.id == item.id) updated(it, targetLiked) else it }
    scope.launch {
      runCatching {
          withContext(Dispatchers.IO) { BiliSpaceApi.setDynamicLike(item.id, targetLiked, accountMid) }
        }
        .onFailure { error ->
          spaceDynamics = spaceDynamics.map { current ->
            if (current.id == item.id && current.liked == targetLiked)
              updated(current, currentItem.liked)
            else current
          }
          Toast.makeText(context, error.message ?: "点赞失败啦", Toast.LENGTH_SHORT).show()
        }
      dynamicLikeBusy.remove(item.id)
    }
  }

  fun deleteDynamic(
    item: SpaceDynamicItem,
    accountMid: Long,
    onLogin: () -> Unit,
    context: Context,
    scope: CoroutineScope,
  ) {
    if (accountMid <= 0L) {
      onLogin()
      return
    }
    if ((item.authorMid > 0L && item.authorMid != accountMid) || !dynamicManageBusy.add(item.id))
      return
    scope.launch {
      withContext(Dispatchers.IO) { runCatching { BiliSpaceApi.deleteDynamic(item.id) } }
        .onSuccess {
          spaceDynamics = spaceDynamics.filterNot { it.id == item.id }
          if (selectedDynamicId == item.id) selectedDynamicId = null
          Toast.makeText(context, "动态已经删除", Toast.LENGTH_SHORT).show()
        }
        .onFailure {
          Toast.makeText(context, it.message ?: "删除失败", Toast.LENGTH_SHORT).show()
        }
      dynamicManageBusy.remove(item.id)
    }
  }

  fun setDynamicPinned(
    item: SpaceDynamicItem,
    accountMid: Long,
    onLogin: () -> Unit,
    context: Context,
    scope: CoroutineScope,
  ) {
    if (accountMid <= 0L) {
      onLogin()
      return
    }
    if ((item.authorMid > 0L && item.authorMid != accountMid) || !dynamicManageBusy.add(item.id))
      return
    val targetPinned = !item.pinned
    scope.launch {
      withContext(Dispatchers.IO) {
          runCatching { BiliSpaceApi.setDynamicPinned(item.id, targetPinned) }
        }
        .onSuccess {
          val updated = spaceDynamics.map { current ->
            when {
              current.id == item.id -> current.copy(pinned = targetPinned)
              targetPinned && current.pinned -> current.copy(pinned = false)
              else -> current
            }
          }
          spaceDynamics =
            if (targetPinned) {
              val pinnedItem = updated.firstOrNull { it.id == item.id }
              listOfNotNull(pinnedItem) + updated.filterNot { it.id == item.id }
            } else updated
          Toast.makeText(
              context,
              if (targetPinned) "动态已经置顶" else "已经取消置顶",
              Toast.LENGTH_SHORT,
            )
            .show()
        }
        .onFailure {
          Toast.makeText(context, it.message ?: "置顶失败", Toast.LENGTH_SHORT).show()
        }
      dynamicManageBusy.remove(item.id)
    }
  }

  fun prepareProfile(
    mid: Long,
    initialProfile: SpaceProfile? = null,
    onPausePlayer: () -> Unit,
  ) {
    if (mid <= 0) return
    onPausePlayer()
    profileMid = mid
    profileAvatarBounds = Rect.Zero
    spaceProfile = initialProfile
    spaceVideos = emptyList()
    spacePage = 1
    spaceHasMore = false
    spaceLoading = false
    spaceError = null
    spaceDynamics = emptyList()
    spaceDynamicOffset = ""
    spaceDynamicHasMore = false
    spaceDynamicLoading = false
    spaceDynamicError = null
    selectedDynamicId = null
    spaceCollections = emptyList()
    spaceCollectionsLoading = false
    spaceCollectionsError = null
    clearSelectedSpaceCollection()
    spaceCollectionVideos = emptyList()
    spaceCollectionPage = 0
    spaceCollectionHasMore = false
    spaceCollectionError = null
    spaceCollectionTotal = 0
    dynamicLikeBusy.clear()
    dynamicManageBusy.clear()
    spaceDynamicGeneration++
  }

  fun loadPreparedProfile(mid: Long, scope: CoroutineScope) {
    if (mid <= 0 || profileMid != mid) return
    scope.launch {
      val profile =
        withContext(Dispatchers.IO) { runCatching { BiliSpaceApi.getSpaceProfile(mid) }.getOrNull() }
      if (profileDataCommitAllowed(mid)) {
        spaceProfile = profile
        profile?.let {
          followingStates[it.mid] = it.followed
          spaceVideos = spaceVideos.map { video ->
            video.copy(uploader = it.name, uploaderFace = it.face, uploaderMid = it.mid)
          }
        }
      }
    }
    loadSpacePage(mid, 1, scope)
  }

  fun loadProfile(mid: Long, scope: CoroutineScope, onPausePlayer: () -> Unit) {
    prepareProfile(mid, onPausePlayer = onPausePlayer)
    loadPreparedProfile(mid, scope)
  }

  fun snapshotProfile(mid: Long) =
    ProfilePageEntry(
      mid = mid,
      profile = spaceProfile,
      videos = spaceVideos,
      page = spacePage,
      hasMore = spaceHasMore,
      error = spaceError,
      dynamics = spaceDynamics,
      dynamicOffset = spaceDynamicOffset,
      dynamicHasMore = spaceDynamicHasMore,
      dynamicError = spaceDynamicError,
      selectedDynamicId = selectedDynamicId,
      commentReturnTransition = commentProfileReturnTransition,
      avatarReturnTransition = avatarProfileReturnTransition,
      collections = spaceCollections,
      collectionsError = spaceCollectionsError,
      selectedCollectionId = selectedCollectionId,
      collectionVideos = spaceCollectionVideos,
      collectionPage = spaceCollectionPage,
      collectionHasMore = spaceCollectionHasMore,
      collectionError = spaceCollectionError,
      collectionTotal = spaceCollectionTotal,
    )

  fun restoreProfile(entry: ProfilePageEntry) {
    commentProfileTransition = null
    avatarProfileTransition = null
    commentProfileReturnTransition = entry.commentReturnTransition
    avatarProfileReturnTransition = entry.avatarReturnTransition
    profileMid = entry.mid
    spaceProfile = entry.profile
    spaceVideos = entry.videos
    spacePage = entry.page
    spaceHasMore = entry.hasMore
    spaceError = entry.error
    spaceLoading = false
    spaceDynamics = entry.dynamics
    spaceDynamicOffset = entry.dynamicOffset
    spaceDynamicHasMore = entry.dynamicHasMore
    spaceDynamicError = entry.dynamicError
    spaceDynamicLoading = false
    selectedDynamicId = entry.selectedDynamicId
    spaceCollections = entry.collections
    spaceCollectionsLoading = false
    spaceCollectionsError = entry.collectionsError
    selectedCollectionId = entry.selectedCollectionId
    spaceCollectionVideos = entry.collectionVideos
    spaceCollectionPage = entry.collectionPage
    spaceCollectionHasMore = entry.collectionHasMore
    spaceCollectionLoading = false
    spaceCollectionError = entry.collectionError
    spaceCollectionTotal = entry.collectionTotal
    spaceDynamicGeneration++
    spaceCollectionGeneration++
  }

  fun restoreProfileReturnTransitions(entry: ProfilePageEntry) {
    commentProfileReturnTransition = entry.commentReturnTransition
    avatarProfileReturnTransition = entry.avatarReturnTransition
  }

  fun loadFollowingGroups(
    loggedIn: Boolean,
    onLogin: () -> Unit,
    context: Context,
    scope: CoroutineScope,
  ) {
    if (!loggedIn) {
      onLogin()
      return
    }
    if (followingGroupsLoaded || followingGroupsLoading) return
    followingGroupsLoading = true
    scope.launch {
      val result = withContext(Dispatchers.IO) { runCatching { BiliFollowApi.getFollowingGroups() } }
      result
        .onSuccess {
          followingGroups = it
          followingGroupsLoaded = true
        }
        .onFailure {
          Toast.makeText(context, it.message ?: "分组加载失败啦 (´･ω･`)", Toast.LENGTH_SHORT).show()
        }
      followingGroupsLoading = false
    }
  }

  fun selectFollowingGroup(
    mid: Long,
    groupId: Long,
    loggedIn: Boolean,
    onLogin: () -> Unit,
    context: Context,
    scope: CoroutineScope,
  ) {
    if (mid <= 0 || followingBusy[mid] == true) return
    if (!loggedIn) {
      onLogin()
      return
    }
    val wasFollowing = followingStates[mid] == true
    var followApplied = wasFollowing
    followingBusy[mid] = true
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching {
            if (!wasFollowing) {
              BiliFollowApi.setFollowing(mid, true)
              followApplied = true
            }
            if (wasFollowing || groupId != 0L) BiliFollowApi.setFollowingGroup(mid, groupId)
          }
        }
      result
        .onSuccess {
          followingStates[mid] = true
          spaceProfile
            ?.takeIf { it.mid == mid }
            ?.let {
              spaceProfile =
                it.copy(
                  followed = true,
                  followerCount = it.followerCount + if (wasFollowing) 0 else 1,
                )
            }
          Toast.makeText(context, "关注好啦，今后也请多多指教 (｡•̀ᴗ-)✧", Toast.LENGTH_SHORT).show()
        }
        .onFailure {
          if (followApplied) {
            followingStates[mid] = true
            spaceProfile
              ?.takeIf { profile -> profile.mid == mid }
              ?.let { profile ->
                if (!profile.followed) {
                  spaceProfile =
                    profile.copy(followed = true, followerCount = profile.followerCount + 1)
                }
              }
          }
          val message =
            if (followApplied) "关注成功啦，不过分组没有放好 (´･ω･`)" else it.message ?: "关注失败啦 (´･ω･`)"
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
      followingBusy.remove(mid)
    }
  }

  fun unfollow(
    mid: Long,
    loggedIn: Boolean,
    onLogin: () -> Unit,
    context: Context,
    scope: CoroutineScope,
  ) {
    if (mid <= 0 || followingBusy[mid] == true) return
    if (!loggedIn) {
      onLogin()
      return
    }
    val wasFollowing = followingStates[mid] == true
    followingBusy[mid] = true
    scope.launch {
      val result = withContext(Dispatchers.IO) { runCatching { BiliFollowApi.setFollowing(mid, false) } }
      result
        .onSuccess {
          followingStates[mid] = false
          spaceProfile
            ?.takeIf { it.mid == mid }
            ?.let {
              spaceProfile =
                it.copy(
                  followed = false,
                  followerCount = (it.followerCount - if (wasFollowing) 1 else 0).coerceAtLeast(0),
                )
            }
          Toast.makeText(context, "已经轻轻告别啦 (´･ᴗ･`)", Toast.LENGTH_SHORT).show()
        }
        .onFailure {
          Toast.makeText(context, it.message ?: "操作失败啦 (´･ω･`)", Toast.LENGTH_SHORT).show()
        }
      followingBusy.remove(mid)
    }
  }
}

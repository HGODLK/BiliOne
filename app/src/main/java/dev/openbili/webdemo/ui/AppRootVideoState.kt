package dev.openbili.webdemo.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.BiliEmotePackage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.DanmakuMaskTimeline
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.MentionSuggestion
import dev.openbili.webdemo.api.VideoEngagement
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.api.commentTimeHasMore
import dev.openbili.webdemo.api.commentTimeStartPage
import dev.openbili.webdemo.api.orderCommentsByTime
import dev.openbili.webdemo.api.remainingVideoCoins
import dev.openbili.webdemo.api.videoCoinLimit
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.offline.OfflineMediaManager
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Data and cache for the currently visible video and its retained parent video pages. */
internal class AppRootVideoState {
  val videoEntryCache = LinkedHashMap<String, VideoPageEntry>(6, .75f, true)
  private val videoPageLoadJobs = mutableMapOf<String, Job>()
  private var mentionSuggestionsJob: Job? = null

  var videoRecommendations by mutableStateOf<List<FeedItem>>(emptyList())
  var videoDescription by mutableStateOf("")
  var videoInfo by mutableStateOf<VideoInfo?>(null)
  var videoEngagement by mutableStateOf(VideoEngagement())
  var videoEngagementAccountMid by mutableStateOf(0L)
  var favoriteFolders by mutableStateOf<List<FavoriteFolder>>(emptyList())
  var favoriteFoldersLoading by mutableStateOf(false)
  var videoActionBusy by mutableStateOf(false)
  var onlineViewerText by mutableStateOf<String?>(null)
  var commentItems by mutableStateOf<List<CommentItem>>(emptyList())
  var commentTotalCount by mutableStateOf(0L)
  var commentPage by mutableStateOf(1)
  var commentHasMore by mutableStateOf(false)
  var commentSort by mutableStateOf(CommentSort.DEFAULT)
  var commentsRefreshing by mutableStateOf(false)
  var commentOid by mutableStateOf(0L)
  var commentsLoading by mutableStateOf(false)
  var replyRoot by mutableStateOf<CommentItem?>(null)
  var replyItems by mutableStateOf<List<CommentItem>>(emptyList())
  var replyPage by mutableStateOf(1)
  var replyHasMore by mutableStateOf(false)
  var repliesLoading by mutableStateOf(false)
  var danmaku by mutableStateOf<List<DanmakuItem>>(emptyList())
  var danmakuMask by mutableStateOf<DanmakuMaskTimeline?>(null)
  var emotes by mutableStateOf<List<BiliEmote>>(emptyList())
  var emotePackages by mutableStateOf<List<BiliEmotePackage>>(emptyList())
  var mentionSuggestions by mutableStateOf<List<MentionSuggestion>>(emptyList())
  var mentionSuggestionsLoading by mutableStateOf(false)
  var historyAid by mutableStateOf(0L)
  var historyCid by mutableStateOf(0L)
  var historyDuration by mutableStateOf(0L)
  var historyStartTimestamp by mutableStateOf(0L)

  fun loadMentionSuggestions(query: String, accountMid: Long, scope: CoroutineScope) {
    mentionSuggestionsJob?.cancel()
    val normalized = query.trim()
    mentionSuggestionsLoading = true
    mentionSuggestionsJob = scope.launch {
      if (normalized.isNotEmpty()) delay(220)
      val loaded =
        withContext(Dispatchers.IO) {
          val followed =
            if (accountMid > 0) {
              runCatching { BiliApi.getFollowings(accountMid, 1, normalized).items }
                .getOrDefault(emptyList())
            } else emptyList()
          val followedIds = followed.mapTo(mutableSetOf()) { it.mid }
          val followedSuggestions =
            followed.take(24).map {
              MentionSuggestion(
                mid = it.mid,
                name = it.name,
                face = it.face,
                subtitle = "我的关注",
                followed = true,
              )
            }
          val searched =
            if (normalized.isNotEmpty()) {
              runCatching { BiliApi.searchUsers(normalized, 1) }.getOrDefault(emptyList())
            } else emptyList()
          followedSuggestions +
            searched
              .asSequence()
              .filter { it.mid !in followedIds }
              .take(24)
              .map {
                MentionSuggestion(
                  mid = it.mid,
                  name = it.name,
                  face = it.face,
                  subtitle = "${it.fans} 粉丝",
                  followed = false,
                )
              }
              .toList()
        }
      mentionSuggestions = loaded.distinctBy { it.mid }
      mentionSuggestionsLoading = false
    }
  }

  fun snapshotEntry(
    item: FeedItem,
    playerViewModel: PlayerViewModel,
    currentPositionMs: Long,
    videoPageDataReadyId: String?,
    playbackEnded: Boolean,
  ): VideoPageEntry {
    return VideoPageEntry(
      item = item,
      recommendations = videoRecommendations,
      info = videoInfo,
      engagement = videoEngagement,
      favoriteFolders = favoriteFolders,
      description = videoDescription,
      onlineViewerText = onlineViewerText,
      comments = commentItems,
      commentTotalCount = commentTotalCount,
      commentPage = commentPage,
      commentHasMore = commentHasMore,
      commentOid = commentOid,
      commentSort = commentSort,
      danmaku = danmaku,
      danmakuMask = danmakuMask,
      emotes = emotes,
      cid = historyCid,
      durationSeconds = historyDuration,
      savedPositionMs = playerViewModel.exoPlayer?.currentPosition ?: currentPositionMs,
      qualityIndex =
        (playerViewModel.playerState.value as? PlayerState.Ready)?.playData?.currentStreamIndex
          ?: 0,
      dataReady = videoPageDataReadyId == item.id,
      playbackEnded = playbackEnded,
      engagementAccountMid = videoEngagementAccountMid,
    )
  }

  fun cacheEntry(entry: VideoPageEntry, currentVideoId: String?) {
    videoEntryCache[entry.item.id] = entry
    while (videoEntryCache.size > MAX_VIDEO_ENTRY_CACHE) {
      val candidate = videoEntryCache.keys.firstOrNull { it != currentVideoId } ?: break
      videoEntryCache.remove(candidate)
    }
  }

  fun selectCommentSort(
    sort: CommentSort,
    selectedVideoId: () -> String?,
    context: Context,
    scope: CoroutineScope,
  ) {
    if (commentsLoading || commentOid <= 0L || sort == commentSort) return
    val previousSort = commentSort
    val previousPage = commentPage
    val previousHasMore = commentHasMore
    val expectedItemId = selectedVideoId() ?: return
    val expectedOid = commentOid
    commentSort = sort
    val targetPage = if (sort == CommentSort.TIME) commentTimeStartPage() else 1
    commentPage = targetPage
    commentHasMore = false
    commentsLoading = true
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.getComments(expectedOid, page = targetPage, sort = sort.apiValue) }
        }
      if (selectedVideoId() == expectedItemId && commentOid == expectedOid && commentSort == sort) {
        result
          .onSuccess { response ->
            val sortedItems =
              if (sort == CommentSort.TIME) orderCommentsByTime(response.items) else response.items
            commentItems = sortedItems
            commentTotalCount = response.totalCount
            commentHasMore =
              if (sort == CommentSort.TIME) {
                commentTimeHasMore(targetPage, response.totalCount)
              } else {
                response.hasMore
              }
            commentPage = targetPage
            videoEntryCache[expectedItemId]?.let { entry ->
              cacheEntry(
                entry.copy(
                  comments = sortedItems,
                  commentTotalCount = response.totalCount,
                  commentPage = targetPage,
                  commentHasMore = commentHasMore,
                  commentSort = sort,
                ),
                selectedVideoId(),
              )
            }
          }
          .onFailure {
            commentSort = previousSort
            commentPage = previousPage
            commentHasMore = previousHasMore
            Toast.makeText(context, it.message ?: "评论排序加载失败", Toast.LENGTH_SHORT).show()
          }
        commentsLoading = false
      }
    }
  }

  fun loadMoreComments(
    item: FeedItem,
    selectedVideoUrl: () -> String?,
    scope: CoroutineScope,
  ) {
    if (commentsLoading || commentOid == 0L || !commentHasMore) return
    val expectedUrl = item.videoUrl
    val expectedOid = commentOid
    val expectedSort = commentSort
    val next =
      if (expectedSort == CommentSort.TIME) {
        dev.openbili.webdemo.api.commentTimeNextPage(commentPage)
      } else {
        commentPage + 1
      }
    commentsLoading = true
    scope.launch {
      try {
        val response =
          withContext(Dispatchers.IO) {
            BiliApi.getComments(expectedOid, next, expectedSort.apiValue)
          }
        if (
          selectedVideoUrl() != expectedUrl ||
            commentOid != expectedOid ||
            commentSort != expectedSort
        ) {
          return@launch
        }
        val combined = (commentItems + response.items).distinctBy { it.rpid }
        commentItems =
          if (expectedSort == CommentSort.TIME) orderCommentsByTime(combined) else combined
        commentHasMore =
          if (expectedSort == CommentSort.TIME) {
            commentTimeHasMore(next, response.totalCount)
          } else {
            response.hasMore
          }
        commentPage = next
      } finally {
        if (selectedVideoUrl() == expectedUrl) commentsLoading = false
      }
    }
  }

  fun refreshComments(
    item: FeedItem,
    selectedVideoId: () -> String?,
    scope: CoroutineScope,
  ) {
    if (commentsRefreshing || commentsLoading || commentOid <= 0L) return
    val expectedItemId = item.id
    val expectedOid = commentOid
    val expectedSort = commentSort
    val targetPage = if (expectedSort == CommentSort.TIME) commentTimeStartPage() else 1
    commentsRefreshing = true
    scope.launch {
      try {
        val response =
          withContext(Dispatchers.IO) {
            BiliApi.getComments(expectedOid, targetPage, expectedSort.apiValue)
          }
        if (
          selectedVideoId() == expectedItemId &&
            commentOid == expectedOid &&
            commentSort == expectedSort
        ) {
          commentItems =
            if (expectedSort == CommentSort.TIME) {
              orderCommentsByTime(response.items)
            } else {
              response.items
            }
          commentTotalCount = response.totalCount
          commentHasMore =
            if (expectedSort == CommentSort.TIME) {
              commentTimeHasMore(targetPage, response.totalCount)
            } else {
              response.hasMore
            }
          commentPage = targetPage
        }
      } finally {
        if (selectedVideoId() == expectedItemId) commentsRefreshing = false
      }
    }
  }

  fun postComment(
    context: Context,
    message: String,
    imageUri: Uri?,
    scope: CoroutineScope,
  ) {
    if (commentsLoading || commentOid == 0L) return
    val expectedOid = commentOid
    commentsLoading = true
    scope.launch {
      try {
        val added =
          withContext(Dispatchers.IO) {
            val uploadedImage = imageUri?.let { uploadCommentImage(context, it) }
            BiliApi.addComment(expectedOid, message, image = uploadedImage)
          }
        if (commentOid == expectedOid) {
          val updated = (commentItems + added).distinctBy { it.rpid }
          commentItems =
            if (commentSort == CommentSort.TIME) orderCommentsByTime(updated)
            else listOf(added) + commentItems
          commentTotalCount += 1
        }
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Toast.makeText(context, error.message ?: "评论发送失败", Toast.LENGTH_SHORT).show()
      } finally {
        if (commentOid == expectedOid) commentsLoading = false
      }
    }
  }

  fun postReply(
    context: Context,
    root: CommentItem,
    parent: CommentItem,
    message: String,
    imageUri: Uri?,
    scope: CoroutineScope,
  ) {
    if (repliesLoading || commentOid == 0L) return
    val expectedOid = commentOid
    repliesLoading = true
    scope.launch {
      try {
        val added =
          withContext(Dispatchers.IO) {
            val uploadedImage = imageUri?.let { uploadCommentImage(context, it) }
            BiliApi.addReply(
              expectedOid,
              root.rpid,
              parent.rpid,
              message,
              image = uploadedImage,
            )
          }
        if (commentOid == expectedOid) {
          if (replyRoot?.rpid == root.rpid) {
            replyItems = (replyItems + added).distinctBy { it.rpid }
          }
          commentItems =
            commentItems.map {
              if (it.rpid == root.rpid) it.copy(replyCount = it.replyCount + 1) else it
            }
        }
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Toast.makeText(context, error.message ?: "回复发送失败", Toast.LENGTH_SHORT).show()
      } finally {
        if (commentOid == expectedOid) repliesLoading = false
      }
    }
  }

  fun toggleCommentLike(comment: CommentItem, scope: CoroutineScope) {
    val expectedOid = commentOid
    scope.launch {
      val target = !comment.liked
      val success =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.setCommentLike(expectedOid, comment.rpid, target) }.isSuccess
        }
      if (success && commentOid == expectedOid) {
        commentItems =
          commentItems.map {
            if (it.rpid == comment.rpid) {
              it.copy(
                liked = target,
                likeCount = (it.likeCount + if (target) 1 else -1).coerceAtLeast(0),
              )
            } else {
              it
            }
          }
        replyItems =
          replyItems.map {
            if (it.rpid == comment.rpid) {
              it.copy(
                liked = target,
                likeCount = (it.likeCount + if (target) 1 else -1).coerceAtLeast(0),
              )
            } else {
              it
            }
          }
      }
    }
  }

  fun deleteComment(
    context: Context,
    comment: CommentItem,
    scope: CoroutineScope,
    onCommentsChanged: () -> Unit,
  ) {
    val expectedOid = commentOid
    if (expectedOid <= 0L) return
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.deleteComment(expectedOid, comment.rpid) }
        }
      result
        .onSuccess {
          if (commentOid == expectedOid) {
            val deletedRoot = commentItems.any { it.rpid == comment.rpid }
            commentItems = commentItems.filterNot { it.rpid == comment.rpid }
            if (replyRoot?.rpid == comment.rpid) {
              replyRoot = null
              replyItems = emptyList()
              replyHasMore = false
            } else if (replyItems.any { it.rpid == comment.rpid }) {
              replyItems = replyItems.filterNot { it.rpid == comment.rpid }
              replyRoot =
                replyRoot?.let { root ->
                  root.copy(replyCount = (root.replyCount - 1).coerceAtLeast(0))
                }
              val rootId = replyRoot?.rpid
              commentItems =
                commentItems.map { root ->
                  if (root.rpid == rootId) {
                    root.copy(replyCount = (root.replyCount - 1).coerceAtLeast(0))
                  } else {
                    root
                  }
                }
            }
            if (deletedRoot) commentTotalCount = (commentTotalCount - 1).coerceAtLeast(0)
            onCommentsChanged()
          }
          Toast.makeText(context, "评论已删除", Toast.LENGTH_SHORT).show()
        }
        .onFailure {
          Toast.makeText(context, it.message ?: "删除失败", Toast.LENGTH_SHORT).show()
        }
    }
  }

  fun setVideoLike(
    context: Context,
    targetLiked: Boolean,
    scope: CoroutineScope,
  ) {
    val expected = videoInfo
    val expectedAccountMid = videoEngagementAccountMid
    if (
      expected == null ||
        expectedAccountMid <= 0L ||
        !videoEngagement.loaded ||
        videoActionBusy
    ) {
      return
    }
    val previousEngagement = videoEngagement
    val previousInfo = expected
    videoActionBusy = true
    videoEngagement = previousEngagement.copy(liked = targetLiked)
    videoInfo =
      previousInfo.copy(
        likeCount = (previousInfo.likeCount + if (targetLiked) 1 else -1).coerceAtLeast(0)
      )
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.setVideoLike(expected.aid, targetLiked) }
        }
      result
        .onSuccess {
          if (videoInfo?.aid == expected.aid && videoEngagementAccountMid == expectedAccountMid) {
            Toast.makeText(
                context,
                if (targetLiked) "已点赞" else "已取消点赞",
                Toast.LENGTH_SHORT,
              )
              .show()
          }
        }
        .onFailure {
          if (
            videoInfo?.aid == expected.aid &&
              videoEngagementAccountMid == expectedAccountMid
          ) {
            videoEngagement = previousEngagement
            videoInfo = previousInfo
            Toast.makeText(context, it.message ?: "点赞失败", Toast.LENGTH_SHORT).show()
          }
        }
      if (
        videoInfo?.aid == expected.aid && videoEngagementAccountMid == expectedAccountMid
      ) {
        videoActionBusy = false
      }
    }
  }

  fun coinVideo(
    context: Context,
    count: Int,
    alsoLike: Boolean,
    scope: CoroutineScope,
  ) {
    val expected = videoInfo
    val expectedAccountMid = videoEngagementAccountMid
    val remaining =
      expected?.let { remainingVideoCoins(it.copyright, videoEngagement.coins) } ?: 0
    if (
      expected == null ||
        expectedAccountMid <= 0L ||
        !videoEngagement.loaded ||
        videoActionBusy ||
        count !in 1..remaining
    ) {
      return
    }
    videoActionBusy = true
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.coinVideo(expected.aid, count, alsoLike) }
        }
      result
        .onSuccess {
          if (
            videoInfo?.aid == expected.aid &&
              videoEngagementAccountMid == expectedAccountMid
          ) {
            val wasLiked = videoEngagement.liked
            videoEngagement =
              videoEngagement.copy(
                coins =
                  (videoEngagement.coins + count).coerceAtMost(
                    videoCoinLimit(expected.copyright)
                  ),
                liked = videoEngagement.liked || alsoLike,
              )
            videoInfo =
              videoInfo?.copy(
                coinCount = expected.coinCount + count,
                likeCount = expected.likeCount + if (alsoLike && !wasLiked) 1 else 0,
              )
          }
          if (videoInfo?.aid == expected.aid && videoEngagementAccountMid == expectedAccountMid) {
            Toast.makeText(context, "已投 $count 枚硬币", Toast.LENGTH_SHORT).show()
          }
        }
        .onFailure {
          if (videoInfo?.aid == expected.aid && videoEngagementAccountMid == expectedAccountMid) {
            Toast.makeText(context, it.message ?: "投币失败", Toast.LENGTH_SHORT).show()
          }
        }
      if (
        videoInfo?.aid == expected.aid && videoEngagementAccountMid == expectedAccountMid
      ) {
        videoActionBusy = false
      }
    }
  }

  fun setFavoriteFolders(
    context: Context,
    addIds: List<Long>,
    removeIds: List<Long>,
    scope: CoroutineScope,
  ) {
    val expected = videoInfo
    val expectedAccountMid = videoEngagementAccountMid
    if (
      expected == null ||
        expectedAccountMid <= 0L ||
        !videoEngagement.loaded ||
        videoActionBusy
    ) {
      return
    }
    val previousFolders = favoriteFolders
    val wasFavorited = previousFolders.any { it.favorited }
    videoActionBusy = true
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.setFavoriteFolders(expected.aid, addIds, removeIds) }
        }
      result
        .onSuccess {
          if (
            videoInfo?.aid == expected.aid &&
              videoEngagementAccountMid == expectedAccountMid
          ) {
            favoriteFolders =
              previousFolders.map { folder ->
                when (folder.id) {
                  in addIds -> folder.copy(favorited = true)
                  in removeIds -> folder.copy(favorited = false)
                  else -> folder
                }
              }
            val isFavorited = favoriteFolders.any { it.favorited }
            videoEngagement = videoEngagement.copy(favorited = isFavorited)
            if (wasFavorited != isFavorited) {
              videoInfo =
                videoInfo?.copy(
                  favoriteCount =
                    (expected.favoriteCount + if (isFavorited) 1 else -1).coerceAtLeast(0)
                )
            }
            Toast.makeText(
                context,
                if (isFavorited) "收藏成功" else "已取消收藏",
                Toast.LENGTH_SHORT,
              )
              .show()
          }
        }
        .onFailure {
          if (videoInfo?.aid == expected.aid && videoEngagementAccountMid == expectedAccountMid) {
            Toast.makeText(context, it.message ?: "收藏失败", Toast.LENGTH_SHORT).show()
          }
        }
      if (
        videoInfo?.aid == expected.aid && videoEngagementAccountMid == expectedAccountMid
      ) {
        videoActionBusy = false
      }
    }
  }

  fun loadFavoriteFolders(
    context: Context,
    accountMid: Long,
    scope: CoroutineScope,
  ) {
    val aid = videoInfo?.aid ?: 0L
    if (favoriteFoldersLoading || aid <= 0L) return
    favoriteFoldersLoading = true
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.getFavoriteFolders(accountMid, aid) }
        }
      result
        .onSuccess { folders ->
          if (videoInfo?.aid == aid && videoEngagementAccountMid == accountMid) {
            favoriteFolders = folders
            videoEngagement = videoEngagement.copy(favorited = folders.any { it.favorited })
          }
        }
        .onFailure {
          if (videoInfo?.aid == aid && videoEngagementAccountMid == accountMid) {
            Toast.makeText(context, it.message ?: "收藏夹加载失败", Toast.LENGTH_SHORT).show()
          }
        }
      if (videoInfo?.aid == aid && videoEngagementAccountMid == accountMid) {
        favoriteFoldersLoading = false
      }
    }
  }

  fun openReplies(comment: CommentItem, scope: CoroutineScope) {
    if (repliesLoading || commentOid <= 0L) return
    replyRoot = comment
    replyItems = emptyList()
    replyPage = 1
    replyHasMore = false
    repliesLoading = true
    val expectedOid = commentOid
    scope.launch {
      try {
        val response =
          withContext(Dispatchers.IO) {
            BiliApi.getCommentReplies(expectedOid, comment.rpid, 1)
          }
        if (commentOid == expectedOid && replyRoot?.rpid == comment.rpid) {
          replyItems = response.items
          replyHasMore = response.hasMore
        }
      } finally {
        if (replyRoot?.rpid == comment.rpid) repliesLoading = false
      }
    }
  }

  fun loadMoreReplies(scope: CoroutineScope) {
    val root = replyRoot
    if (root == null || !replyHasMore || repliesLoading || commentOid <= 0L) return
    repliesLoading = true
    val expectedOid = commentOid
    val next = replyPage + 1
    scope.launch {
      try {
        val response =
          withContext(Dispatchers.IO) {
            BiliApi.getCommentReplies(expectedOid, root.rpid, next)
          }
        if (commentOid == expectedOid && replyRoot?.rpid == root.rpid) {
          replyItems = (replyItems + response.items).distinctBy { it.rpid }
          replyHasMore = response.hasMore
          replyPage = next
        }
      } finally {
        if (replyRoot?.rpid == root.rpid) repliesLoading = false
      }
    }
  }

  fun refreshReplies(scope: CoroutineScope) {
    val root = replyRoot
    if (root == null || repliesLoading || commentOid <= 0L) return
    repliesLoading = true
    val expectedOid = commentOid
    scope.launch {
      try {
        val response =
          withContext(Dispatchers.IO) {
            BiliApi.getCommentReplies(expectedOid, root.rpid, 1)
          }
        if (commentOid == expectedOid && replyRoot?.rpid == root.rpid) {
          replyItems = response.items
          replyHasMore = response.hasMore
          replyPage = 1
        }
      } finally {
        if (replyRoot?.rpid == root.rpid) repliesLoading = false
      }
    }
  }

  fun restoreEntry(
    entry: VideoPageEntry,
    playerSession: AppRootPlayerSessionState,
    currentAccountMid: Long,
  ) {
    val restoreAccountState =
      currentAccountMid > 0L &&
        entry.engagementAccountMid == currentAccountMid &&
        entry.engagement.loaded
    videoRecommendations = entry.recommendations
    videoInfo = entry.info
    videoEngagement = if (restoreAccountState) entry.engagement else VideoEngagement()
    videoEngagementAccountMid = if (restoreAccountState) currentAccountMid else 0L
    favoriteFolders = if (restoreAccountState) entry.favoriteFolders else emptyList()
    videoDescription = entry.description
    onlineViewerText = entry.onlineViewerText
    commentItems = entry.comments
    commentTotalCount = entry.commentTotalCount
    commentPage = entry.commentPage
    commentHasMore = entry.commentHasMore
    commentOid = entry.commentOid
    commentSort = entry.commentSort
    danmaku = entry.danmaku
    danmakuMask = entry.danmakuMask
    if (entry.emotes.isNotEmpty()) emotes = entry.emotes
    historyAid = entry.commentOid
    historyCid = entry.cid
    historyDuration = entry.durationSeconds
    playerSession.currentPositionMs = entry.savedPositionMs
    playerSession.restorePlaybackEnded(entry.playbackEnded)
    replyRoot = null
    replyItems = emptyList()
    replyPage = 1
    replyHasMore = false
    repliesLoading = false
  }

  fun clearVisibleVideoData(playerSession: AppRootPlayerSessionState) {
    videoRecommendations = emptyList()
    videoDescription = ""
    videoInfo = null
    videoEngagement = VideoEngagement()
    videoEngagementAccountMid = 0L
    favoriteFolders = emptyList()
    favoriteFoldersLoading = false
    videoActionBusy = false
    onlineViewerText = null
    commentItems = emptyList()
    commentTotalCount = 0L
    commentHasMore = false
    commentsLoading = false
    commentsRefreshing = false
    replyRoot = null
    replyItems = emptyList()
    replyPage = 1
    replyHasMore = false
    repliesLoading = false
    commentOid = 0L
    commentPage = 1
    commentSort = CommentSort.DEFAULT
    danmaku = emptyList()
    danmakuMask = null
    historyAid = 0L
    historyCid = 0L
    historyDuration = 0L
    playerSession.currentPositionMs = 0L
    playerSession.isPlaying = false
    playerSession.clearPlaybackEnded()
  }

  fun ensureVideoPageData(
    item: FeedItem,
    scope: CoroutineScope,
    selectedVideoId: () -> String?,
    dataCommitAllowedId: () -> String?,
    onRestore: (VideoPageEntry) -> Unit,
    onDataReady: (String) -> Unit,
  ) {
    if (videoEntryCache[item.id]?.dataReady == true) return
    if (videoPageLoadJobs[item.id]?.isActive == true) return
    videoPageLoadJobs[item.id] = scope.launch {
      try {
        val entry =
          withContext(Dispatchers.IO) {
            val cached = OfflineMediaManager.resolveLoadedEntry(item.videoUrl)
            val cachedDanmaku = cached?.let(OfflineMediaManager::loadLoadedDanmaku).orEmpty()
            val bvid = cached?.bvid?.takeIf(String::isNotBlank) ?: BiliApi.resolveVideoBvid(item.videoUrl)
            val onlineEntry =
              runCatching {
                coroutineScope {
              val recommendationsDeferred = async {
                runCatching { BiliApi.getRelated(bvid) }
                  .getOrDefault(emptyList())
                  .map(::feedItemFromCard)
              }
              val info = BiliApi.getVideoInfo(bvid) ?: error("视频资料暂时不可用")
              val commentsDeferred = async {
                runCatching { BiliApi.getComments(info.aid, 1) }.getOrNull()
              }
              val comments = commentsDeferred.await()
              VideoPageEntry(
                item = item,
                recommendations = recommendationsDeferred.await(),
                info = info,
                engagement = VideoEngagement(),
                favoriteFolders = emptyList(),
                description = info.desc,
                onlineViewerText = null,
                comments = comments?.items.orEmpty(),
                commentTotalCount = comments?.totalCount ?: info.replyCount,
                commentPage = 1,
                commentHasMore = comments?.hasMore == true,
                commentOid = info.aid,
                danmaku = cachedDanmaku,
                danmakuMask = null,
                emotes = emptyList(),
                cid = cached?.cid ?: info.cid,
                durationSeconds = cached?.durationMs?.div(1_000L) ?: info.durationSeconds,
                savedPositionMs = 0L,
                qualityIndex = 0,
                dataReady = true,
              )
            }
              }
                .getOrNull()
            onlineEntry ?: cached?.let { offlineVideoPageEntry(item, it, cachedDanmaku) }
              ?: error("视频资料加载失败")
          }
        cacheEntry(entry, selectedVideoId())
        if (selectedVideoId() == item.id && dataCommitAllowedId() == item.id) onRestore(entry)
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        if (selectedVideoId() == item.id && dataCommitAllowedId() == item.id) onDataReady(item.id)
      } finally {
        videoPageLoadJobs.remove(item.id)
      }
    }
  }
}

private fun offlineVideoPageEntry(
  item: FeedItem,
  cached: dev.openbili.webdemo.offline.OfflineMediaEntry,
  cachedDanmaku: List<DanmakuItem>,
): VideoPageEntry {
  val info =
    VideoInfo(
      bvid = cached.bvid,
      aid = cached.aid,
      cid = cached.cid,
      title = cached.title,
      coverUrl = item.coverUrl,
      uploaderName = "",
      uploaderFace = "",
      uploaderMid = 0L,
      durationSeconds = cached.durationMs / 1_000L,
      playCount = 0L,
      danmakuCount = cachedDanmaku.size.toLong(),
      replyCount = 0L,
      likeCount = 0L,
      coinCount = 0L,
      favoriteCount = 0L,
      shareCount = 0L,
      publishedAt = 0L,
      desc = "离线缓存 · ${cached.qualityLabel}",
      pages =
        listOf(
          VideoPage(
            page = cached.pageNumber,
            cid = cached.cid,
            part = cached.partTitle,
            durationSeconds = cached.durationMs / 1_000L,
          )
        ),
    )
  return VideoPageEntry(
    item = item,
    recommendations = emptyList(),
    info = info,
    engagement = VideoEngagement(),
    favoriteFolders = emptyList(),
    description = info.desc,
    onlineViewerText = null,
    comments = emptyList(),
    commentTotalCount = 0L,
    commentPage = 1,
    commentHasMore = false,
    commentOid = 0L,
    danmaku = cachedDanmaku,
    danmakuMask = null,
    emotes = emptyList(),
    cid = cached.cid,
    durationSeconds = cached.durationMs / 1_000L,
    savedPositionMs = 0L,
    qualityIndex = 0,
    dataReady = true,
  )
}

private fun uploadCommentImage(context: Context, uri: Uri): BiliApi.PrivateImageUpload {
  val bytes =
    context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
      ?: throw IllegalStateException("无法读取评论图片")
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
    throw IllegalStateException("评论图片格式不受支持")
  }
  val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
  return BiliApi.uploadCommentImage(
    bytes = bytes,
    fileName = "comment-image.${mimeType.substringAfterLast('/', "jpg")}",
    mimeType = mimeType,
    width = bounds.outWidth,
    height = bounds.outHeight,
  )
}

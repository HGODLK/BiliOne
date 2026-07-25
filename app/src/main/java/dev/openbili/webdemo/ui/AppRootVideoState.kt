package dev.openbili.webdemo.ui

import android.content.Context
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
import dev.openbili.webdemo.api.commentTimeHasMore
import dev.openbili.webdemo.api.commentTimeStartPage
import dev.openbili.webdemo.api.orderCommentsByTime
import dev.openbili.webdemo.feed.FeedItem
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

  fun restoreEntry(entry: VideoPageEntry, playerSession: AppRootPlayerSessionState) {
    videoRecommendations = entry.recommendations
    videoInfo = entry.info
    videoEngagement = entry.engagement
    favoriteFolders = entry.favoriteFolders
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
            coroutineScope {
              val bvid = BiliApi.resolveVideoBvid(item.videoUrl)
              val recommendationsDeferred = async {
                runCatching { BiliApi.getRelated(bvid) }
                  .getOrDefault(emptyList())
                  .map(::feedItemFromCard)
              }
              val info = BiliApi.getVideoInfo(bvid)
              val commentsDeferred = async {
                info?.let { runCatching { BiliApi.getComments(it.aid, 1) }.getOrNull() }
              }
              val comments = commentsDeferred.await()
              VideoPageEntry(
                item = item,
                recommendations = recommendationsDeferred.await(),
                info = info,
                engagement = VideoEngagement(),
                favoriteFolders = emptyList(),
                description = info?.desc.orEmpty(),
                onlineViewerText = null,
                comments = comments?.items.orEmpty(),
                commentTotalCount = comments?.totalCount ?: info?.replyCount ?: 0L,
                commentPage = 1,
                commentHasMore = comments?.hasMore == true,
                commentOid = info?.aid ?: 0L,
                danmaku = emptyList(),
                danmakuMask = null,
                emotes = emptyList(),
                cid = info?.cid ?: 0L,
                durationSeconds = info?.durationSeconds ?: 0L,
                savedPositionMs = 0L,
                qualityIndex = 0,
                dataReady = true,
              )
            }
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

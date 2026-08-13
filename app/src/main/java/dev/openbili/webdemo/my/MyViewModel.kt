package dev.openbili.webdemo.my

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.BangumiLocalHistoryStore
import dev.openbili.webdemo.api.AccountHistoryItem
import dev.openbili.webdemo.api.AccountHistoryResponse
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.AccountMessageUserStyle
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.BiliEmotePackage
import dev.openbili.webdemo.api.BangumiWatchProgress
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.FeedCard
import dev.openbili.webdemo.api.FollowingGroup
import dev.openbili.webdemo.api.FollowingUser
import dev.openbili.webdemo.api.HistoryCursor
import dev.openbili.webdemo.api.InteractionMessagePage
import dev.openbili.webdemo.api.MessageCursor
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.live.LiveHistoryStore
import dev.openbili.webdemo.live.LiveSearchRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

enum class MySection(val label: String) {
  FAVORITES("我的收藏"),
  HISTORY("历史记录"),
  WATCH_LATER("稍后再看"),
  FOLLOWING("我的关注"),
  MESSAGES("我的消息"),
  INTERACTIONS("回复或@我的"),
  LIKES("点赞消息"),
  CACHED_VIDEOS("缓存视频"),
  SETTINGS("设置"),
}

private const val PRIVATE_MESSAGE_SYNC_INTERVAL_MS = 800L
private const val PRIVATE_SESSION_PAGE_SIZE = 18
private const val PRIVATE_HISTORY_PAGE_SIZE = 15
private const val ACCOUNT_UNREAD_REFRESH_INTERVAL_MS = 5_000L
private const val MY_VIEW_MODEL_TAG = "MyViewModel"

internal fun accountUnreadRetryDelayMs(consecutiveFailures: Int): Long =
  when {
    consecutiveFailures <= 1 -> 5_000L
    consecutiveFailures == 2 -> 10_000L
    else -> 30_000L
  }

internal fun applyAccountMessageUserStyles(
  messages: List<AccountMessage>,
  styles: Map<Long, AccountMessageUserStyle>,
): List<AccountMessage> =
  messages.map { message ->
    styles[message.userMid]?.let { style ->
      message.copy(
        userLevel = style.level,
        userVipActive = style.vipActive,
        userVipLabel = style.vipLabel,
      )
    } ?: message
  }

internal fun privateConversationSession(
  userMid: Long,
  userName: String,
  userFace: String,
): AccountMessage =
  AccountMessage(
    id = userMid,
    userMid = userMid,
    userName = userName.ifBlank { "UID $userMid" },
    userFace = userFace,
    title = "私信会话",
    content = "开始聊天吧",
    sourceContent = "",
    oid = 0L,
    rootId = 0L,
    parentId = 0L,
    time = 0L,
    messageType = 1,
    isPrivate = true,
  )

internal fun includeDirectPrivateTarget(
  sessions: List<AccountMessage>,
  target: AccountMessage?,
): List<AccountMessage> =
  if (target != null && sessions.none { it.userMid == target.userMid }) {
    listOf(target) + sessions
  } else {
    sessions
  }

internal fun normalizePrivateMessageHistory(messages: List<AccountMessage>): List<AccountMessage> {
  // The immediate post-send read and the realtime poll can overlap. The server may therefore
  // return the same sequence in both batches; never let duplicate stable IDs reach LazyColumn.
  val sorted =
    messages
      .distinctBy { message ->
        when {
          message.id >= 0L -> "id:${message.id}"
          message.messageKey > 0L -> "key:${message.messageKey}"
          else -> "local:${message.id}"
        }
      }
      .sortedWith(compareBy<AccountMessage> { it.time }.thenBy { it.sequence })
  val serverMessages = sorted.filter { it.id >= 0L || it.messageKey > 0L }
  val withoutAcknowledgedLocalCopies =
    sorted.filterNot { pending ->
      pending.id < 0L &&
        serverMessages.any { server ->
          server.isOutgoing == pending.isOutgoing &&
            server.messageType == pending.messageType &&
            kotlin.math.abs(server.time - pending.time) <= 12L &&
            when (pending.messageType) {
              2 -> server.coverUrl.isNotBlank() && server.coverUrl == pending.coverUrl
              else -> server.content == pending.content
            }
        }
    }
  val result = mutableListOf<AccountMessage>()
  val withdrawalIndexByTarget = mutableMapOf<Long, Int>()
  withoutAcknowledgedLocalCopies.forEach { message ->
    if (!message.withdrawn) {
      result += message
      return@forEach
    }
    val targetKey = message.withdrawTargetMessageKey.takeIf { it > 0L } ?: message.messageKey
    val existingNoticeIndex = withdrawalIndexByTarget[targetKey]
    if (targetKey > 0L && existingNoticeIndex != null) return@forEach
    val originalIndex =
      if (targetKey > 0L) result.indexOfFirst { it.messageKey == targetKey } else -1
    val notice =
      message.copy(
        messageType = 5,
        content = if (message.isOutgoing) "你撤回了一条消息" else "对方撤回了一条消息",
        coverUrl = "",
        linkUrl = "",
        targetKind = dev.openbili.webdemo.api.MessageTargetKind.UNKNOWN,
        withdrawTargetMessageKey = targetKey,
      )
    val index =
      if (originalIndex >= 0) {
        result[originalIndex] = notice
        originalIndex
      } else {
        result += notice
        result.lastIndex
      }
    if (targetKey > 0L) withdrawalIndexByTarget[targetKey] = index
  }
  val withdrawnTargets =
    result.asSequence().filter(AccountMessage::withdrawn).map(AccountMessage::withdrawTargetMessageKey)
      .filter { it > 0L }.toSet()
  return result.filterNot { !it.withdrawn && it.messageKey in withdrawnTargets }
}

enum class FollowingOrder(val label: String, val apiValue: String) {
  RECENT("最近关注", ""),
  MOST_VISITED("最常访问", "attention"),
}

enum class HistoryFilter(val label: String, val apiType: String, val enabled: Boolean = true) {
  ALL("全部", ""),
  VIDEO("仅视频", "archive"),
  LIVE("仅直播", "live"),
  ARTICLE("仅专栏", "article"),
}

sealed interface HistoryCardItem {
  val stableId: String
  val viewAt: Long

  data class Video(val item: FeedItem, override val viewAt: Long = item.publishedAt) : HistoryCardItem {
    override val stableId: String = "video:${item.id}"
  }

  data class Bangumi(
    val item: FeedItem,
    val bangumi: SpaceContentCard,
    val mediaLabel: String,
    override val viewAt: Long = item.publishedAt,
  ) : HistoryCardItem {
    override val stableId: String = bangumi.id
  }

  data class Article(
    val item: ArticleItem,
    override val viewAt: Long = item.publishedAt,
  ) : HistoryCardItem {
    override val stableId: String = item.stableId
  }

  data class Live(
    val room: LiveSearchRoom,
    override val viewAt: Long,
  ) : HistoryCardItem {
    override val stableId: String = room.stableId
  }
}

private fun historyMergeKey(item: HistoryCardItem): String =
  (item as? HistoryCardItem.Bangumi)
    ?.bangumi
    ?.seasonId
    ?.takeIf { it > 0L }
    ?.let { "pgc:season:$it" }
    ?: item.stableId

data class MyUiState(
  val section: MySection = MySection.HISTORY,
  val folders: List<FavoriteFolder> = emptyList(),
  val selectedFolderId: Long? = null,
  val videos: List<FeedItem> = emptyList(),
  val historyFilter: HistoryFilter = HistoryFilter.ALL,
  val historyItems: List<HistoryCardItem> = emptyList(),
  val historyCursor: HistoryCursor = HistoryCursor(),
  val historyHasMore: Boolean = false,
  val historyLoadingMore: Boolean = false,
  val favoriteResourceIdByVideoId: Map<String, Long> = emptyMap(),
  val favoriteTypeByVideoId: Map<String, Int> = emptyMap(),
  val favoritePage: Int = 0,
  val favoriteHasMore: Boolean = false,
  val favoriteLoadingMore: Boolean = false,
  val favoriteQuery: String = "",
  val favoriteActionBusyId: String? = null,
  val favoriteFolderActionBusy: Boolean = false,
  val followings: List<FollowingUser> = emptyList(),
  val followingTotal: Int = 0,
  val followingResultTotal: Int = 0,
  val followingPage: Int = 0,
  val followingHasMore: Boolean = false,
  val followingLoadingMore: Boolean = false,
  val followingQuery: String = "",
  val followingGroups: List<FollowingGroup> = emptyList(),
  val selectedFollowingGroupId: Long? = null,
  val followingOrder: FollowingOrder = FollowingOrder.RECENT,
  val unfollowedIds: Set<Long> = emptySet(),
  val messages: List<AccountMessage> = emptyList(),
  val selectedMessageId: Long? = null,
  val privateMessageUnreadCount: Int = 0,
  val interactionUnreadCount: Int = 0,
  val likeUnreadCount: Int = 0,
  val privateMessageHistory: Map<Long, List<AccountMessage>> = emptyMap(),
  val privateMessagesLoading: Boolean = false,
  val privateHistoryHasMore: Boolean = false,
  val privateHistoryLoadingMore: Boolean = false,
  val privateSessionHasMore: Boolean = false,
  val privateSessionLoadingMore: Boolean = false,
  val privateMessageSending: Boolean = false,
  val privateMessageSendSuccessToken: Long = 0L,
  val messageEmotePackages: List<BiliEmotePackage> = emptyList(),
  val messageReplyCursor: MessageCursor = MessageCursor(),
  val messageAtCursor: MessageCursor = MessageCursor(),
  val messageReplyHasMore: Boolean = false,
  val messageAtHasMore: Boolean = false,
  val messageLikeCursor: MessageCursor = MessageCursor(),
  val messageLikeHasMore: Boolean = false,
  val messagesLoadingMore: Boolean = false,
  val loading: Boolean = false,
  val error: String? = null,
)

internal fun MyUiState.hasUnread(section: MySection): Boolean =
  when (section) {
    MySection.MESSAGES -> privateMessageUnreadCount > 0
    MySection.INTERACTIONS -> interactionUnreadCount > 0
    MySection.LIKES -> likeUnreadCount > 0
    else -> false
  }

internal fun resolvedPrivateMessageUnreadCount(
  section: MySection,
  privateMessagesLoaded: Boolean,
  cachedUnreadCount: Int,
  serverUnreadCount: Int,
): Int =
  if (section == MySection.MESSAGES && privateMessagesLoaded) {
    cachedUnreadCount
  } else {
    serverUnreadCount
  }

internal fun favoriteFoldersAfterAction(
  folders: List<FavoriteFolder>,
  sourceFolderId: Long,
  destinationFolderId: Long?,
  move: Boolean,
): List<FavoriteFolder> = folders.map { folder ->
  when (folder.id) {
    sourceFolderId ->
      if (move) folder.copy(mediaCount = (folder.mediaCount - 1).coerceAtLeast(0)) else folder
    destinationFolderId -> folder.copy(mediaCount = folder.mediaCount + 1)
    else -> folder
  }
}

internal fun favoriteActionConfirmed(
  sourceContains: Boolean,
  destinationContains: Boolean?,
  hasDestination: Boolean,
  move: Boolean,
): Boolean =
  when {
    !hasDestination -> !sourceContains
    move -> !sourceContains && destinationContains == true
    else -> sourceContains && destinationContains == true
  }

class MyViewModel(application: Application) : AndroidViewModel(application) {
  private val _state = MutableStateFlow(MyUiState())
  val state: StateFlow<MyUiState> = _state.asStateFlow()
  private var mid = 0L
  private var loadJob: Job? = null
  private var followingSearchJob: Job? = null
  private var favoriteSearchJob: Job? = null
  private var messageLoadMoreJob: Job? = null
  private var privateHistoryJob: Job? = null
  private var privateSessionLoadMoreJob: Job? = null
  private var privateMessageRealtimeJob: Job? = null
  private var unreadLoadJob: Job? = null
  private var unreadMonitorJob: Job? = null
  @Volatile private var unreadMonitoringActive = false
  private val unreadRequestMutex = kotlinx.coroutines.sync.Mutex()
  private var privateMessagesLoaded = false
  private var privateMessagesCache: List<AccountMessage> = emptyList()
  private var privateMessageHistoryCache: Map<Long, List<AccountMessage>> = emptyMap()
  private var privateHistoryCursorByUser: Map<Long, Long> = emptyMap()
  private var privateHistoryHasMoreByUser: Map<Long, Boolean> = emptyMap()
  private var privateSessionCursor = 0L
  private var privateSessionHasMore = false
  private var messageEmoteCache: List<BiliEmotePackage> = emptyList()
  private var directPrivateMessageTarget: AccountMessage? = null
  private var loadGeneration = 0L

  fun setUser(mid: Long, loadInitialSection: Boolean = true) {
    if (this.mid == mid) return
    commitPendingUnfollows()
    loadGeneration++
    loadJob?.cancel()
    followingSearchJob?.cancel()
    favoriteSearchJob?.cancel()
    messageLoadMoreJob?.cancel()
    privateHistoryJob?.cancel()
    privateSessionLoadMoreJob?.cancel()
    privateMessageRealtimeJob?.cancel()
    unreadLoadJob?.cancel()
    unreadMonitorJob?.cancel()
    privateMessagesLoaded = false
    privateMessagesCache = emptyList()
    privateMessageHistoryCache = emptyMap()
    privateHistoryCursorByUser = emptyMap()
    privateHistoryHasMoreByUser = emptyMap()
    privateSessionCursor = 0L
    privateSessionHasMore = false
    messageEmoteCache = emptyList()
    this.mid = mid
    if (mid > 0 && loadInitialSection) select(MySection.HISTORY)
    else _state.value = MyUiState()
    if (mid > 0L && unreadMonitoringActive) startUnreadMonitor()
  }

  /** Search debounce and pagination are disposable while the root pager is in motion. */
  fun cancelSupplementaryLoadingForPageSwitch() {
    followingSearchJob?.cancel()
    followingSearchJob = null
    favoriteSearchJob?.cancel()
    favoriteSearchJob = null
    messageLoadMoreJob?.cancel()
    privateHistoryJob?.cancel()
    privateSessionLoadMoreJob?.cancel()
    privateMessageRealtimeJob?.cancel()
    messageLoadMoreJob = null
    val current = _state.value
    if (
      !current.historyLoadingMore &&
        !current.favoriteLoadingMore &&
        !current.followingLoadingMore &&
        !current.messagesLoadingMore &&
        !current.privateMessagesLoading &&
        !current.privateSessionLoadingMore &&
        !current.privateHistoryLoadingMore
    ) {
      return
    }
    loadGeneration++
    loadJob?.cancel()
    loadJob = null
    _state.value =
      current.copy(
        historyLoadingMore = false,
        favoriteLoadingMore = false,
        followingLoadingMore = false,
        messagesLoadingMore = false,
        privateMessagesLoading = false,
        privateSessionLoadingMore = false,
        privateHistoryLoadingMore = false,
      )
  }

  fun resumeSupplementaryLoadingAfterPageSwitch() {
    if (
      mid > 0L &&
        _state.value.section == MySection.MESSAGES &&
        privateMessagesLoaded &&
        privateMessageRealtimeJob?.isActive != true
    ) {
      startPrivateMessageRealtime(loadGeneration, mid)
    }
  }

  fun refreshUnreadStatus() {
    val expectedMid = mid
    if (expectedMid <= 0L) {
      _state.value =
        _state.value.copy(
          privateMessageUnreadCount = 0,
          interactionUnreadCount = 0,
          likeUnreadCount = 0,
        )
      return
    }
    unreadLoadJob?.cancel()
    unreadLoadJob =
      viewModelScope.launch {
        requestUnreadStatus(expectedMid)
      }
  }

  fun setUnreadMonitoringActive(active: Boolean) {
    if (unreadMonitoringActive == active) {
      if (active && unreadMonitorJob?.isActive != true) startUnreadMonitor()
      return
    }
    unreadMonitoringActive = active
    if (active) {
      startUnreadMonitor()
    } else {
      unreadMonitorJob?.cancel()
      unreadMonitorJob = null
      unreadLoadJob?.cancel()
      unreadLoadJob = null
    }
  }

  fun onUnreadNetworkAvailable() {
    if (unreadMonitoringActive && mid > 0L) refreshUnreadStatus()
  }

  private fun startUnreadMonitor() {
    unreadMonitorJob?.cancel()
    val expectedMid = mid
    if (!unreadMonitoringActive || expectedMid <= 0L) {
      unreadMonitorJob = null
      return
    }
    unreadMonitorJob =
      viewModelScope.launch {
        var consecutiveFailures = 0
        while (unreadMonitoringActive && mid == expectedMid) {
          val succeeded = requestUnreadStatus(expectedMid)
          consecutiveFailures = if (succeeded) 0 else consecutiveFailures + 1
          delay(
            if (succeeded) ACCOUNT_UNREAD_REFRESH_INTERVAL_MS
            else accountUnreadRetryDelayMs(consecutiveFailures)
          )
        }
      }
  }

  private suspend fun requestUnreadStatus(expectedMid: Long): Boolean =
    unreadRequestMutex.withLock {
      val (privateMessageCount, interactionSummary) =
        coroutineScope {
          val privateMessageRequest =
            async(Dispatchers.IO) {
              try {
                BiliApi.getPrivateMessageUnreadCount()
              } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
              } catch (error: Exception) {
                Log.w(MY_VIEW_MODEL_TAG, "Unable to refresh private-message unread count", error)
                null
              }
            }
          val interactionRequest =
            async(Dispatchers.IO) {
              try {
                BiliApi.getInteractionUnreadSummary()
              } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
              } catch (error: Exception) {
                Log.w(MY_VIEW_MODEL_TAG, "Unable to refresh interaction unread count", error)
                null
              }
            }
          privateMessageRequest.await() to interactionRequest.await()
        }
      if (privateMessageCount == null && interactionSummary == null) return@withLock false
      if (mid != expectedMid) return@withLock false
      val current = _state.value
      _state.value =
        current.copy(
          privateMessageUnreadCount =
            privateMessageCount?.let { serverUnreadCount ->
              resolvedPrivateMessageUnreadCount(
                section = current.section,
                privateMessagesLoaded = privateMessagesLoaded,
                cachedUnreadCount = privateMessagesCache.sumOf(AccountMessage::unreadCount),
                serverUnreadCount = serverUnreadCount,
              )
            } ?: current.privateMessageUnreadCount,
          interactionUnreadCount =
            interactionSummary?.let { summary ->
              if (current.section == MySection.INTERACTIONS) 0 else summary.interactionCount
            } ?: current.interactionUnreadCount,
          likeUnreadCount =
            interactionSummary?.let { summary ->
              if (current.section == MySection.LIKES) 0 else summary.likeCount
            } ?: current.likeUnreadCount,
        )
      privateMessageCount != null && interactionSummary != null
    }

  fun select(section: MySection) {
    if (
      _state.value.section == MySection.FOLLOWING &&
        section == MySection.FOLLOWING &&
        _state.value.unfollowedIds.isNotEmpty()
    )
      return
    if (_state.value.section == MySection.FOLLOWING && section != MySection.FOLLOWING) {
      commitPendingUnfollows()
    }
    val generation = ++loadGeneration
    val expectedMid = mid
    loadJob?.cancel()
    followingSearchJob?.cancel()
    favoriteSearchJob?.cancel()
    messageLoadMoreJob?.cancel()
    privateHistoryJob?.cancel()
    privateSessionLoadMoreJob?.cancel()
    privateMessageRealtimeJob?.cancel()
    _state.value =
      _state.value.copy(
        section = section,
        videos = emptyList(),
        historyFilter =
          if (section == MySection.HISTORY) HistoryFilter.ALL else _state.value.historyFilter,
        historyItems = emptyList(),
        historyCursor = HistoryCursor(),
        historyHasMore = false,
        historyLoadingMore = false,
        favoriteResourceIdByVideoId = emptyMap(),
        favoriteTypeByVideoId = emptyMap(),
        favoritePage = 0,
        favoriteHasMore = false,
        favoriteLoadingMore = false,
        favoriteQuery = "",
        favoriteActionBusyId = null,
        favoriteFolderActionBusy = false,
        followings = emptyList(),
        followingTotal = if (section == MySection.FOLLOWING) _state.value.followingTotal else 0,
        followingResultTotal = 0,
        followingPage = 0,
        followingHasMore = false,
        followingLoadingMore = false,
        followingQuery = "",
        followingGroups =
          if (section == MySection.FOLLOWING) _state.value.followingGroups else emptyList(),
        selectedFollowingGroupId = null,
        followingOrder = FollowingOrder.RECENT,
        unfollowedIds = emptySet(),
        messages = if (section == MySection.MESSAGES) privateMessagesCache else emptyList(),
        selectedMessageId =
          if (section == MySection.MESSAGES) privateMessagesCache.firstOrNull()?.id else null,
        privateMessageHistory =
          if (section == MySection.MESSAGES) privateMessageHistoryCache else emptyMap(),
        privateMessagesLoading = false,
        privateSessionHasMore = if (section == MySection.MESSAGES) privateSessionHasMore else false,
        privateSessionLoadingMore = false,
        messageEmotePackages = messageEmoteCache,
        messageReplyCursor = MessageCursor(),
        messageAtCursor = MessageCursor(),
        messageReplyHasMore = false,
        messageAtHasMore = false,
        messageLikeCursor = MessageCursor(),
        messageLikeHasMore = false,
        messagesLoadingMore = false,
        loading = false,
        error = null,
      )
    if (
      section == MySection.WATCH_LATER ||
        section == MySection.CACHED_VIDEOS ||
        mid <= 0
    ) return
    loadJob = viewModelScope.launch {
      _state.value =
        _state.value.copy(loading = !(section == MySection.MESSAGES && privateMessagesLoaded))
      try {
        when (section) {
          MySection.FAVORITES -> {
            val folders = withContext(Dispatchers.IO) { BiliApi.getFavoriteFolders(expectedMid) }
            val selected = folders.firstOrNull()?.id
            val response =
              selected?.let {
                withContext(Dispatchers.IO) { BiliApi.getFavoriteVideos(it, 1) }
              } ?: dev.openbili.webdemo.api.SpaceVideoResponse(emptyList(), false)
            if (!isCurrentLoad(generation, expectedMid, section)) return@launch
            _state.value =
              _state.value.copy(
                folders = folders,
                selectedFolderId = selected,
                videos = response.cards.map(::toFeedItem),
                favoriteResourceIdByVideoId =
                  response.cards.associate { card ->
                    favoriteVideoId(card) to card.aid
                  },
                favoriteTypeByVideoId =
                  response.cards.associate { card ->
                    favoriteVideoId(card) to card.resourceType
                  },
                favoritePage = if (selected == null) 0 else 1,
                favoriteHasMore = response.hasMore,
                loading = false,
              )
          }
          MySection.HISTORY -> {
            val (response, localHistory) =
              withContext(Dispatchers.IO) {
                BiliApi.getHistory(type = HistoryFilter.ALL.apiType) to
                  (readLocalLiveHistory() + readLocalBangumiHistory())
              }
            if (!isCurrentLoad(generation, expectedMid, section)) return@launch
            val remote = response.items.mapNotNull(::toHistoryCardItem)
            _state.value =
              _state.value.copy(
                historyItems =
                  (localHistory + remote)
                    .sortedByDescending(HistoryCardItem::viewAt)
                    .distinctBy(::historyMergeKey),
                historyCursor = response.cursor,
                historyHasMore = response.hasMore,
                loading = false,
              )
          }
          MySection.FOLLOWING -> {
            val (groups, response) =
              coroutineScope {
                val groupsRequest =
                  async(Dispatchers.IO) {
                    runCatching { BiliApi.getFollowingGroups() }.getOrDefault(emptyList())
                  }
                val followingRequest =
                  async(Dispatchers.IO) {
                    BiliApi.getFollowings(
                      expectedMid,
                      orderType = FollowingOrder.RECENT.apiValue,
                    )
                  }
                groupsRequest.await() to followingRequest.await()
              }
            if (!isCurrentLoad(generation, expectedMid, section)) return@launch
            _state.value =
              _state.value.copy(
                followings = response.items,
                followingGroups = groups,
                followingTotal = response.totalCount,
                followingResultTotal = response.totalCount,
                followingPage = 1,
                followingHasMore = response.hasMore,
                loading = false,
              )
          }
          MySection.MESSAGES -> {
            val wasAlreadyLoaded = privateMessagesLoaded
            val (sessionPage, emotes) =
              coroutineScope {
                val messagesRequest =
                  async(Dispatchers.IO) {
                    BiliApi.getPrivateMessageSessions(size = PRIVATE_SESSION_PAGE_SIZE)
                  }
                val emotesRequest =
                  async(Dispatchers.IO) {
                    if (messageEmoteCache.isNotEmpty()) messageEmoteCache
                    else runCatching { BiliApi.getReplyEmotes() }.getOrDefault(emptyList())
                  }
                messagesRequest.await() to emotesRequest.await()
              }
            if (!isCurrentLoad(generation, expectedMid, section)) return@launch
            val requestedTarget = directPrivateMessageTarget
            val sessionItems = includeDirectPrivateTarget(sessionPage.items, requestedTarget)
            val mergedMessages =
              mergePrivateSessionUpdates(sessionItems, replaceAll = !wasAlreadyLoaded)
            privateMessagesLoaded = true
            if (!wasAlreadyLoaded) {
              privateSessionCursor = sessionPage.endTimestamp
              privateSessionHasMore = sessionPage.hasMore
            }
            messageEmoteCache = emotes
            val selectedId =
              requestedTarget
                ?.let { target ->
                  mergedMessages.firstOrNull { it.userMid == target.userMid }?.id
                }
                ?: _state.value.selectedMessageId
                  ?.takeIf { id -> mergedMessages.any { it.id == id } }
                ?: mergedMessages.firstOrNull()?.id
            directPrivateMessageTarget = null
            _state.value =
              _state.value.copy(
                messages = mergedMessages,
                selectedMessageId = selectedId,
                privateMessageHistory = privateMessageHistoryCache,
                privateSessionHasMore = privateSessionHasMore,
                privateSessionLoadingMore = false,
                messageEmotePackages = emotes,
                loading = false,
              )
            if (selectedId != null) loadPrivateMessageHistory(selectedId)
            selectedId?.let(::markPrivateMessageRead)
            startPrivateMessageRealtime(generation, expectedMid)
          }
          MySection.INTERACTIONS -> {
            val (page, emotes) =
              coroutineScope {
                val pageRequest =
                  async(Dispatchers.IO) {
                    loadInteractionPage(MessageCursor(), MessageCursor(), true, true)
                  }
                val emotesRequest =
                  async(Dispatchers.IO) {
                    if (messageEmoteCache.isNotEmpty()) messageEmoteCache
                    else runCatching { BiliApi.getReplyEmotes() }.getOrDefault(emptyList())
                  }
                pageRequest.await() to emotesRequest.await()
              }
            if (!isCurrentLoad(generation, expectedMid, section)) return@launch
            _state.value =
              _state.value.copy(
                messages = page.items,
                selectedMessageId = page.items.firstOrNull()?.id,
                messageReplyCursor = page.replyCursor,
                messageAtCursor = page.atCursor,
                messageReplyHasMore = page.replyHasMore,
                messageAtHasMore = page.atHasMore,
                interactionUnreadCount = 0,
                messageEmotePackages = emotes,
                loading = false,
              )
            messageEmoteCache = emotes
            enrichAccountMessageUserStyles(
              messages = page.items,
              generation = generation,
              expectedMid = expectedMid,
              section = MySection.INTERACTIONS,
            )
          }
          MySection.LIKES -> {
            val (page, emotes) =
              coroutineScope {
                val pageRequest = async(Dispatchers.IO) { BiliApi.getLikeMessages() }
                val emotesRequest =
                  async(Dispatchers.IO) {
                    if (messageEmoteCache.isNotEmpty()) messageEmoteCache
                    else runCatching { BiliApi.getReplyEmotes() }.getOrDefault(emptyList())
                  }
                pageRequest.await() to emotesRequest.await()
              }
            if (!isCurrentLoad(generation, expectedMid, section)) return@launch
            _state.value =
              _state.value.copy(
                messages = page.items,
                selectedMessageId = null,
                messageLikeCursor = page.cursor,
                messageLikeHasMore = page.hasMore,
                likeUnreadCount = 0,
                messageEmotePackages = emotes,
                loading = false,
              )
            messageEmoteCache = emotes
            enrichAccountMessageUserStyles(
              messages = page.items,
              generation = generation,
              expectedMid = expectedMid,
              section = MySection.LIKES,
            )
          }
          MySection.WATCH_LATER -> Unit
          MySection.CACHED_VIDEOS -> Unit
          MySection.SETTINGS -> {
            val folders = withContext(Dispatchers.IO) { BiliApi.getFavoriteFolders(expectedMid) }
            if (!isCurrentLoad(generation, expectedMid, section)) return@launch
            _state.value = _state.value.copy(folders = folders, loading = false)
          }
        }
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (section == MySection.MESSAGES) directPrivateMessageTarget = null
        if (!isCurrentLoad(generation, expectedMid, section)) return@launch
        _state.value = _state.value.copy(loading = false, error = error.message ?: "加载失败")
      }
    }
  }

  fun refresh() {
    val section = _state.value.section
    if (mid <= 0L) return
    refreshUnreadStatus()
    if (
      section == MySection.SETTINGS ||
        section == MySection.WATCH_LATER ||
        section == MySection.CACHED_VIDEOS
    ) return
    commitPendingUnfollows()
    if (section == MySection.FAVORITES && _state.value.selectedFolderId != null) {
      favoriteSearchJob?.cancel()
      loadFavoritePage(reset = true)
      return
    }
    if (section == MySection.HISTORY) {
      loadHistoryPage(reset = true)
      return
    }
    select(section)
  }

  fun selectHistoryFilter(filter: HistoryFilter) {
    val state = _state.value
    if (
      state.section != MySection.HISTORY ||
        !filter.enabled ||
        filter == state.historyFilter ||
        state.loading ||
        state.historyLoadingMore
    )
      return
    _state.value = state.copy(historyFilter = filter)
    loadHistoryPage(reset = true)
  }

  fun loadMoreHistory() {
    val state = _state.value
    if (
      state.section != MySection.HISTORY ||
        state.loading ||
        state.historyLoadingMore ||
        !state.historyHasMore
    )
      return
    loadHistoryPage(reset = false)
  }

  private fun loadHistoryPage(reset: Boolean) {
    val state = _state.value
    val expectedMid = mid
    if (expectedMid <= 0L || state.section != MySection.HISTORY) return
    val filter = state.historyFilter
    val cursor = if (reset) HistoryCursor() else state.historyCursor
    val generation = ++loadGeneration
    loadJob?.cancel()
    _state.value =
      state.copy(
        historyItems = if (reset) emptyList() else state.historyItems,
        historyCursor = if (reset) HistoryCursor() else state.historyCursor,
        historyHasMore = if (reset) false else state.historyHasMore,
        historyLoadingMore = !reset,
        loading = reset,
        error = null,
      )
    loadJob = viewModelScope.launch {
      try {
        val (response, localHistory) =
          withContext(Dispatchers.IO) {
            loadFilteredHistory(cursor, filter) to
              if (!reset) emptyList()
              else
                when (filter) {
                  HistoryFilter.ALL -> readLocalLiveHistory() + readLocalBangumiHistory()
                  HistoryFilter.LIVE -> readLocalLiveHistory()
                  else -> emptyList()
                }
          }
        if (!isCurrentLoad(generation, expectedMid, MySection.HISTORY)) return@launch
        if (_state.value.historyFilter != filter) return@launch
        val remoteLoaded =
          response.items
            .filter { item ->
              when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.VIDEO -> item is AccountHistoryItem.Video
                HistoryFilter.ARTICLE -> item is AccountHistoryItem.Article
                HistoryFilter.LIVE -> item is AccountHistoryItem.Live
              }
            }
            .mapNotNull(::toHistoryCardItem)
        val loaded =
          (localHistory + remoteLoaded)
            .sortedByDescending(HistoryCardItem::viewAt)
            .distinctBy(::historyMergeKey)
        _state.value =
          _state.value.copy(
            historyItems =
              (if (reset) loaded else _state.value.historyItems + loaded)
                .distinctBy(::historyMergeKey)
                .sortedByDescending(HistoryCardItem::viewAt),
            historyCursor = response.cursor,
            historyHasMore = response.hasMore && response.cursor != cursor,
            historyLoadingMore = false,
            loading = false,
          )
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (!isCurrentLoad(generation, expectedMid, MySection.HISTORY)) return@launch
        _state.value =
          _state.value.copy(
            historyLoadingMore = false,
            loading = false,
            error = error.message ?: "历史记录加载失败",
          )
      }
    }
  }

  private fun loadFilteredHistory(
    cursor: HistoryCursor,
    filter: HistoryFilter,
  ): AccountHistoryResponse {
    // Each history business owns its cursor. Scanning the mixed feed here made article paging
    // stop after an arbitrary six pages and then continued with the wrong cursor.
    return BiliApi.getHistory(cursor = cursor, type = filter.apiType)
  }

  fun selectMessage(id: Long) {
    _state.value = _state.value.copy(selectedMessageId = id)
    if (_state.value.section == MySection.MESSAGES) {
      markPrivateMessageRead(id)
      loadPrivateMessageHistory(id)
    }
  }

  /**
   * Opens the existing messages section on a specific profile, including users that do not yet
   * have a session row. History still loads through the normal private-message paging pipeline.
   */
  fun openPrivateConversation(userMid: Long, userName: String, userFace: String) {
    if (mid <= 0L || userMid <= 0L || userMid == mid) return
    val target =
      privateMessagesCache.firstOrNull { it.userMid == userMid }
        ?: privateConversationSession(userMid, userName, userFace)
    directPrivateMessageTarget = target
    privateMessagesCache =
      (listOf(target) + privateMessagesCache).distinctBy(AccountMessage::userMid)
    select(MySection.MESSAGES)
    loadPrivateMessageHistory(target.id)
  }

  fun loadMorePrivateSessions() {
    val state = _state.value
    val expectedMid = mid
    val cursor = privateSessionCursor
    if (
      state.section != MySection.MESSAGES ||
        state.privateSessionLoadingMore ||
        !privateSessionHasMore ||
        cursor <= 0L ||
        expectedMid <= 0L
    ) return
    val generation = loadGeneration
    _state.value = state.copy(privateSessionLoadingMore = true, error = null)
    privateSessionLoadMoreJob?.cancel()
    privateSessionLoadMoreJob = viewModelScope.launch {
      try {
        val page =
          withContext(Dispatchers.IO) {
            BiliApi.getPrivateMessageSessions(cursor, PRIVATE_SESSION_PAGE_SIZE)
          }
        if (!isCurrentLoad(generation, expectedMid, MySection.MESSAGES)) return@launch
        val merged =
          mergePrivateSessionUpdates(page.items, replaceAll = false, prependFresh = false)
        privateSessionCursor = page.endTimestamp
        privateSessionHasMore = page.hasMore && page.endTimestamp != cursor
        _state.value =
          _state.value.copy(
            messages = merged,
            privateSessionHasMore = privateSessionHasMore,
            privateSessionLoadingMore = false,
          )
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (!isCurrentLoad(generation, expectedMid, MySection.MESSAGES)) return@launch
        _state.value =
          _state.value.copy(
            privateSessionLoadingMore = false,
            error = error.message ?: "私信会话加载失败",
          )
      }
    }
  }

  private fun mergePrivateSessionUpdates(
    freshMessages: List<AccountMessage>,
    replaceAll: Boolean,
    prependFresh: Boolean = true,
  ): List<AccountMessage> {
    val previousByUser = privateMessagesCache.associateBy(AccountMessage::userMid)
    val changedUsers =
      freshMessages.mapNotNull { fresh ->
        val previous = previousByUser[fresh.userMid]
        fresh.userMid.takeIf {
          previous == null || fresh.sequence != previous.sequence || fresh.time > previous.time
        }
      }.toSet()
    if (changedUsers.isNotEmpty()) {
      // Keep the open conversation intact. Realtime updates merge into it below; clearing it here
      // caused the whole right pane to flash and defeated per-message insertion animations.
      val selectedUser =
        privateMessagesCache.firstOrNull { it.id == _state.value.selectedMessageId }?.userMid
      val inactiveChangedUsers = changedUsers - setOfNotNull(selectedUser)
      privateMessageHistoryCache = privateMessageHistoryCache - inactiveChangedUsers
      privateHistoryCursorByUser = privateHistoryCursorByUser - inactiveChangedUsers
      privateHistoryHasMoreByUser = privateHistoryHasMoreByUser - inactiveChangedUsers
    }
    val selectedId = _state.value.selectedMessageId
    val normalizedFresh =
      freshMessages.map { message ->
        if (message.id == selectedId) message.copy(unreadCount = 0) else message
      }
    val merged =
      if (replaceAll) normalizedFresh
      else {
        (if (prependFresh) normalizedFresh + privateMessagesCache else privateMessagesCache + normalizedFresh)
          .distinctBy(AccountMessage::userMid)
      }
    privateMessagesCache = merged
    _state.value =
      _state.value.copy(
        messages = merged,
        privateMessageUnreadCount = merged.sumOf(AccountMessage::unreadCount),
        privateMessageHistory = privateMessageHistoryCache,
      )
    return merged
  }

  private fun markPrivateMessageRead(sessionId: Long) {
    val session = privateMessagesCache.firstOrNull { it.id == sessionId } ?: return
    val unreadBeforeAcknowledgement = session.unreadCount
    if (session.unreadCount > 0) {
      privateMessagesCache =
        privateMessagesCache.map { message ->
          if (message.id == sessionId) message.copy(unreadCount = 0) else message
        }
      _state.value =
        _state.value.copy(
          messages = privateMessagesCache,
          privateMessageUnreadCount = privateMessagesCache.sumOf(AccountMessage::unreadCount),
        )
    }
    if (session.sequence <= 0L) return
    viewModelScope.launch {
      val acknowledged =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.markPrivateMessageRead(session.userMid, session.sequence) }.isSuccess
        }
      if (!acknowledged) {
        // Do not permanently pretend that a failed receipt reached the server. The next sync may
        // still resolve it, but until then retain the visible unread indicator for this session.
        privateMessagesCache =
          privateMessagesCache.map { message ->
            if (message.id == sessionId && message.sequence == session.sequence) {
              message.copy(unreadCount = unreadBeforeAcknowledgement)
            } else {
              message
            }
          }
        val current = _state.value
        _state.value =
          current.copy(
            messages =
              if (current.section == MySection.MESSAGES) privateMessagesCache else current.messages,
            privateMessageUnreadCount = privateMessagesCache.sumOf(AccountMessage::unreadCount),
          )
      }
    }
  }

  private fun startPrivateMessageRealtime(generation: Long, expectedMid: Long) {
    privateMessageRealtimeJob?.cancel()
    privateMessageRealtimeJob = viewModelScope.launch {
      while (isCurrentLoad(generation, expectedMid, MySection.MESSAGES)) {
        delay(PRIVATE_MESSAGE_SYNC_INTERVAL_MS)
        val fresh =
          withContext(Dispatchers.IO) {
            runCatching { BiliApi.getPrivateMessageSessions(size = PRIVATE_SESSION_PAGE_SIZE).items }
              .getOrNull()
          } ?: continue
        if (!isCurrentLoad(generation, expectedMid, MySection.MESSAGES)) break
        val previousByUser = privateMessagesCache.associateBy(AccountMessage::userMid)
        val changedUsers =
          fresh.mapNotNull { message ->
            val previous = previousByUser[message.userMid]
            message.userMid.takeIf {
              previous == null ||
                message.sequence != previous.sequence ||
                message.time > previous.time
            }
          }.toSet()
        mergePrivateSessionUpdates(fresh, replaceAll = false)
        val selected = privateMessagesCache.firstOrNull { it.id == _state.value.selectedMessageId }
        if (selected != null && selected.userMid in changedUsers) {
          markPrivateMessageRead(selected.id)
          val latest =
            withContext(Dispatchers.IO) {
              runCatching {
                  BiliApi.getPrivateMessageHistory(
                    talkerId = selected.userMid,
                    accountMid = expectedMid,
                    size = PRIVATE_HISTORY_PAGE_SIZE,
                  )
                }
                .getOrNull()
            } ?: continue
          if (!isCurrentLoad(generation, expectedMid, MySection.MESSAGES)) break
          val existing = privateMessageHistoryCache[selected.userMid].orEmpty()
          val mergedHistory =
            normalizePrivateMessageHistory((existing + latest.items).distinctBy(AccountMessage::id))
          privateMessageHistoryCache =
            privateMessageHistoryCache + (selected.userMid to mergedHistory)
          // Do not touch loading flags: this is an in-place append, never a page refresh.
          _state.value =
            _state.value.copy(
              privateMessageHistory =
                _state.value.privateMessageHistory + (selected.userMid to mergedHistory)
            )
        }
      }
    }
  }

  fun loadMorePrivateMessageHistory() {
    val sessionId = _state.value.selectedMessageId ?: return
    loadPrivateMessageHistory(sessionId, loadMore = true)
  }

  private fun loadPrivateMessageHistory(sessionId: Long, loadMore: Boolean = false) {
    val state = _state.value
    val session = state.messages.firstOrNull { it.id == sessionId } ?: return
    val userMid = session.userMid
    if (!loadMore && state.privateMessageHistory.containsKey(userMid)) {
      _state.value =
        state.copy(
          privateHistoryHasMore = privateHistoryHasMoreByUser[userMid] == true,
          privateHistoryLoadingMore = false,
        )
      return
    }
    val endSequence = if (loadMore) privateHistoryCursorByUser[userMid] ?: 0L else 0L
    if (loadMore && (state.privateHistoryLoadingMore || endSequence <= 0L || privateHistoryHasMoreByUser[userMid] != true)) return
    val expectedMid = mid
    val generation = loadGeneration
    privateHistoryJob?.cancel()
    _state.value =
      state.copy(
        privateMessagesLoading = !loadMore,
        privateHistoryHasMore = if (loadMore) state.privateHistoryHasMore else false,
        privateHistoryLoadingMore = loadMore,
        error = null,
      )
    privateHistoryJob = viewModelScope.launch {
      try {
        val page =
          withContext(Dispatchers.IO) {
            BiliApi.getPrivateMessageHistory(
                talkerId = userMid,
                accountMid = expectedMid,
                endSequence = endSequence,
                size = PRIVATE_HISTORY_PAGE_SIZE,
              )
          }
        if (!isCurrentLoad(generation, expectedMid, MySection.MESSAGES)) return@launch
        val existing = _state.value.privateMessageHistory[userMid].orEmpty()
        val history =
          normalizePrivateMessageHistory(
            (if (loadMore) page.items + existing else page.items).distinctBy(AccountMessage::id)
          )
        privateHistoryCursorByUser = privateHistoryCursorByUser + (userMid to page.endSequence)
        privateHistoryHasMoreByUser =
          privateHistoryHasMoreByUser + (userMid to (page.hasMore && page.endSequence > 0L))
        _state.value =
          _state.value.copy(
            privateMessageHistory = _state.value.privateMessageHistory + (userMid to history),
            privateMessagesLoading = false,
            privateHistoryHasMore = privateHistoryHasMoreByUser[userMid] == true,
            privateHistoryLoadingMore = false,
          )
        privateMessageHistoryCache = privateMessageHistoryCache + (userMid to history)
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (!isCurrentLoad(generation, expectedMid, MySection.MESSAGES)) return@launch
        _state.value =
          _state.value.copy(
            privateMessagesLoading = false,
            privateHistoryLoadingMore = false,
            error = error.message ?: "私信历史加载失败",
          )
      }
    }
  }

  fun loadMoreInteractions() {
    val state = _state.value
    if (
      state.section != MySection.INTERACTIONS ||
        state.loading ||
        state.messagesLoadingMore ||
        (!state.messageReplyHasMore && !state.messageAtHasMore)
    )
      return
    val expectedMid = mid
    val generation = loadGeneration
    _state.value = state.copy(messagesLoadingMore = true, error = null)
    messageLoadMoreJob?.cancel()
    messageLoadMoreJob = viewModelScope.launch {
      try {
        val page =
          loadInteractionPage(
            state.messageReplyCursor,
            state.messageAtCursor,
            state.messageReplyHasMore,
            state.messageAtHasMore,
          )
        if (!isCurrentLoad(generation, expectedMid, MySection.INTERACTIONS)) return@launch
        _state.value =
          _state.value.copy(
            messages = (_state.value.messages + page.items).distinctBy { it.id }
              .sortedByDescending { it.time },
            messageReplyCursor = page.replyCursor,
            messageAtCursor = page.atCursor,
            messageReplyHasMore = state.messageReplyHasMore && page.replyHasMore,
            messageAtHasMore = state.messageAtHasMore && page.atHasMore,
            messagesLoadingMore = false,
          )
        enrichAccountMessageUserStyles(
          messages = page.items,
          generation = generation,
          expectedMid = expectedMid,
          section = MySection.INTERACTIONS,
        )
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (!isCurrentLoad(generation, expectedMid, MySection.INTERACTIONS)) return@launch
        _state.value =
          _state.value.copy(
            messagesLoadingMore = false,
            error = error.message ?: "历史回复加载失败",
          )
      }
    }
  }

  fun loadMoreLikes() {
    val state = _state.value
    if (
      state.section != MySection.LIKES ||
        state.loading ||
        state.messagesLoadingMore ||
        !state.messageLikeHasMore
    ) return
    val expectedMid = mid
    val generation = loadGeneration
    _state.value = state.copy(messagesLoadingMore = true, error = null)
    messageLoadMoreJob?.cancel()
    messageLoadMoreJob = viewModelScope.launch {
      try {
        val page = withContext(Dispatchers.IO) { BiliApi.getLikeMessages(state.messageLikeCursor) }
        if (!isCurrentLoad(generation, expectedMid, MySection.LIKES)) return@launch
        _state.value =
          _state.value.copy(
            messages = (_state.value.messages + page.items).distinctBy { it.id },
            messageLikeCursor = page.cursor,
            messageLikeHasMore = page.hasMore,
            messagesLoadingMore = false,
          )
        enrichAccountMessageUserStyles(
          messages = page.items,
          generation = generation,
          expectedMid = expectedMid,
          section = MySection.LIKES,
        )
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (!isCurrentLoad(generation, expectedMid, MySection.LIKES)) return@launch
        _state.value =
          _state.value.copy(
            messagesLoadingMore = false,
            error = error.message ?: "点赞消息加载失败",
          )
      }
    }
  }

  private suspend fun loadInteractionPage(
    replyCursor: MessageCursor,
    atCursor: MessageCursor,
    loadReply: Boolean,
    loadAt: Boolean,
  ): InteractionMessagePage = coroutineScope {
    val replyRequest =
      if (loadReply) async(Dispatchers.IO) { BiliApi.getReplyMessages(replyCursor) } else null
    val atRequest = if (loadAt) async(Dispatchers.IO) { BiliApi.getAtMessages(atCursor) } else null
    val replies =
      replyRequest?.await()
        ?: dev.openbili.webdemo.api.AccountMessagePage(emptyList(), replyCursor, false)
    val mentions =
      atRequest?.await()
        ?: dev.openbili.webdemo.api.AccountMessagePage(emptyList(), atCursor, false)
    InteractionMessagePage(
      items = (replies.items + mentions.items).distinctBy { it.id }.sortedByDescending { it.time },
      replyCursor = replies.cursor,
      atCursor = mentions.cursor,
      replyHasMore = replies.hasMore,
      atHasMore = mentions.hasMore,
    )
  }

  fun unfollow(user: FollowingUser) {
    val state = _state.value
    val undo = user.mid in state.unfollowedIds
    _state.value =
      state.copy(
        followingTotal = (state.followingTotal + if (undo) 1 else -1).coerceAtLeast(0),
        followingResultTotal = (state.followingResultTotal + if (undo) 1 else -1).coerceAtLeast(0),
        unfollowedIds =
          if (undo) state.unfollowedIds - user.mid else state.unfollowedIds + user.mid,
        followingGroups =
          state.followingGroups.map { group ->
            if (group.id == state.selectedFollowingGroupId || group.id in user.groupIds) {
              group.copy(count = (group.count + if (undo) 1 else -1).coerceAtLeast(0))
            } else {
              group
            }
          },
      )
  }

  fun commitPendingUnfollows() {
    val pending = _state.value.unfollowedIds
    if (pending.isEmpty()) return
    _state.value =
      _state.value.copy(
        followings = _state.value.followings.filterNot { it.mid in pending },
        unfollowedIds = emptySet(),
      )
    viewModelScope.launch(Dispatchers.IO) {
      pending.forEach { userMid -> runCatching { BiliApi.setFollowing(userMid, false) } }
    }
  }

  fun setFollowingQuery(query: String) {
    if (_state.value.section != MySection.FOLLOWING || query == _state.value.followingQuery) return
    _state.value = _state.value.copy(followingQuery = query)
    followingSearchJob?.cancel()
    followingSearchJob = viewModelScope.launch {
      delay(280)
      loadFollowingPage(reset = true)
    }
  }

  fun selectFollowingGroup(groupId: Long?) {
    val state = _state.value
    if (state.section != MySection.FOLLOWING || state.selectedFollowingGroupId == groupId) return
    val groupCount = state.followingGroups.firstOrNull { it.id == groupId }?.count
    _state.value =
      state.copy(
        selectedFollowingGroupId = groupId,
        followingQuery = "",
        followingResultTotal = groupCount ?: state.followingTotal,
      )
    followingSearchJob?.cancel()
    loadFollowingPage(reset = true)
  }

  fun selectFollowingOrder(order: FollowingOrder) {
    val state = _state.value
    if (state.section != MySection.FOLLOWING || state.followingOrder == order) return
    _state.value = state.copy(followingOrder = order, followingQuery = "")
    followingSearchJob?.cancel()
    loadFollowingPage(reset = true)
  }

  fun loadMoreFollowings() {
    val state = _state.value
    if (
      state.section != MySection.FOLLOWING ||
        state.loading ||
        state.followingLoadingMore ||
        !state.followingHasMore
    )
      return
    loadFollowingPage(reset = false)
  }

  private fun loadFollowingPage(reset: Boolean) {
    val expectedMid = mid
    if (expectedMid <= 0 || _state.value.section != MySection.FOLLOWING) return
    val query = _state.value.followingQuery.trim()
    val selectedGroupId = _state.value.selectedFollowingGroupId
    val order = _state.value.followingOrder
    val selectedGroupCount =
      _state.value.followingGroups.firstOrNull { it.id == selectedGroupId }?.count
    val page = if (reset) 1 else _state.value.followingPage + 1
    val generation = ++loadGeneration
    loadJob?.cancel()
    _state.value =
      _state.value.copy(
        followings = if (reset) emptyList() else _state.value.followings,
        followingPage = if (reset) 0 else _state.value.followingPage,
        followingHasMore = if (reset) false else _state.value.followingHasMore,
        loading = reset,
        followingLoadingMore = !reset,
        error = null,
      )
    loadJob = viewModelScope.launch {
      try {
        val response =
          withContext(Dispatchers.IO) {
            if (query.isNotBlank() || selectedGroupId == null) {
              BiliApi.getFollowings(expectedMid, page, query, order.apiValue)
            } else {
              BiliApi.getFollowingGroupMembers(selectedGroupId, page, order.apiValue)
            }
          }
        if (!isCurrentLoad(generation, expectedMid, MySection.FOLLOWING)) return@launch
        val resultTotal =
          if (query.isBlank() && selectedGroupId != null) selectedGroupCount ?: response.totalCount
          else response.totalCount
        _state.value =
          _state.value.copy(
            followings =
              (if (reset) response.items else _state.value.followings + response.items).distinctBy {
                it.mid
              },
            followingTotal =
              if (query.isBlank() && selectedGroupId == null)
                (response.totalCount - _state.value.unfollowedIds.size).coerceAtLeast(0)
              else _state.value.followingTotal,
            followingResultTotal =
              (resultTotal - response.items.count { it.mid in _state.value.unfollowedIds })
                .coerceAtLeast(0),
            followingPage = page,
            followingHasMore =
              if (query.isBlank() && selectedGroupId != null) page * 50 < resultTotal
              else response.hasMore,
            followingLoadingMore = false,
            loading = false,
          )
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (!isCurrentLoad(generation, expectedMid, MySection.FOLLOWING)) return@launch
        _state.value =
          _state.value.copy(
            followingLoadingMore = false,
            loading = false,
            error = error.message ?: "关注加载失败",
          )
      }
    }
  }

  fun replyToSelected(text: String) {
    val message =
      _state.value.messages.firstOrNull { it.id == _state.value.selectedMessageId } ?: return
    viewModelScope.launch {
      _state.value = _state.value.copy(loading = true, error = null)
      try {
        withContext(Dispatchers.IO) {
          if (_state.value.section == MySection.MESSAGES || message.isPrivate)
            BiliApi.sendPrivateMessage(mid, message.userMid, text)
          else BiliApi.replyToMessage(message, text)
        }
        val sentAt = System.currentTimeMillis() / 1000L
        val current = _state.value
        val updatedHistory =
          if (current.section == MySection.MESSAGES || message.isPrivate) {
            val local =
              AccountMessage(
                id = -System.nanoTime(),
                userMid = mid,
                userName = "我",
                userFace = "",
                title = "私信",
                content = text,
                sourceContent = "",
                oid = 0L,
                rootId = 0L,
                parentId = 0L,
                time = sentAt,
                messageType = 1,
                isPrivate = true,
                senderMid = mid,
                receiverMid = message.userMid,
                isOutgoing = true,
              )
            current.privateMessageHistory +
              (message.userMid to (current.privateMessageHistory[message.userMid].orEmpty() + local))
          } else current.privateMessageHistory
        _state.value = current.copy(loading = false, privateMessageHistory = updatedHistory)
        if (current.section == MySection.MESSAGES || message.isPrivate) {
          privateMessageHistoryCache = updatedHistory
          privateMessagesCache =
            privateMessagesCache.map { session ->
              if (session.userMid == message.userMid) {
                session.copy(content = text, time = sentAt, unreadCount = 0)
              } else session
            }
        }
      } catch (error: Exception) {
        _state.value = _state.value.copy(loading = false, error = error.message ?: "回复失败")
      }
    }
  }

  fun selectFolder(folderId: Long) {
    if (folderId == _state.value.selectedFolderId || _state.value.loading) return
    favoriteSearchJob?.cancel()
    _state.value =
      _state.value.copy(
        selectedFolderId = folderId,
        favoriteQuery = "",
      )
    loadFavoritePage(reset = true)
  }

  fun replyToSelectedPrivate(context: Context, text: String, imageUri: Uri?) {
    val selected = _state.value.messages.firstOrNull { it.id == _state.value.selectedMessageId } ?: return
    if (text.isBlank() && imageUri == null) return
    viewModelScope.launch {
      _state.value = _state.value.copy(privateMessageSending = true, error = null)
      try {
        val image =
          imageUri?.let { uri ->
            try {
              withContext(Dispatchers.IO) {
                val bytes =
                  context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                    ?: throw IllegalStateException("无法读取图片")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("图片格式不受支持")
                val mime = context.contentResolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
                BiliApi.uploadPrivateImage(
                  bytes = bytes,
                  fileName = "private-image.${mime.substringAfterLast('/', "jpg")}",
                  mimeType = mime,
                  width = bounds.outWidth,
                  height = bounds.outHeight,
                )
              }
            } catch (error: Exception) {
              if (error is kotlinx.coroutines.CancellationException) throw error
              throw IllegalStateException("图片上传失败：${error.message ?: "未知错误"}", error)
            }
          }
        val latestAfterSend = withContext(Dispatchers.IO) {
          // Keep the protocol order deterministic when a draft includes both media and text.
          if (image != null) {
            try {
              BiliApi.sendPrivateImage(mid, selected.userMid, image)
            } catch (error: Exception) {
              throw IllegalStateException("图片消息发送失败：${error.message ?: "未知错误"}", error)
            }
          }
          if (text.isNotBlank()) BiliApi.sendPrivateMessage(mid, selected.userMid, text)
          runCatching {
              BiliApi.getPrivateMessageHistory(
                talkerId = selected.userMid,
                accountMid = mid,
                size = PRIVATE_HISTORY_PAGE_SIZE,
              )
            }
            .getOrNull()
        }
        val sentAt = System.currentTimeMillis() / 1_000L
        val current = _state.value
        val mergedHistory =
          normalizePrivateMessageHistory(
            current.privateMessageHistory[selected.userMid].orEmpty() +
              latestAfterSend?.items.orEmpty()
          )
        val updatedHistory =
          current.privateMessageHistory +
            (selected.userMid to mergedHistory)
        val sessionPreview = text.ifBlank { "图片消息" }
        privateMessagesCache =
          privateMessagesCache.map { session ->
            if (session.userMid == selected.userMid) session.copy(content = sessionPreview, time = sentAt, unreadCount = 0)
            else session
          }
        privateMessageHistoryCache = updatedHistory
        _state.value =
          current.copy(
            privateMessageSending = false,
            privateMessageSendSuccessToken = current.privateMessageSendSuccessToken + 1L,
            messages = privateMessagesCache,
            privateMessageHistory = updatedHistory,
          )
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        _state.value =
          _state.value.copy(
            privateMessageSending = false,
            error = error.message ?: "私信发送失败",
          )
      }
    }
  }

  fun withdrawPrivateMessage(message: AccountMessage) {
    val selected = _state.value.messages.firstOrNull { it.id == _state.value.selectedMessageId } ?: return
    if (!message.isOutgoing || message.withdrawn || message.messageKey <= 0L) return
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) {
          BiliApi.withdrawPrivateMessage(mid, selected.userMid, message.messageKey)
        }
        updatePrivateMessage(message.id) {
          it.copy(
            content = "你撤回了一条消息",
            withdrawn = true,
            messageType = 5,
            withdrawTargetMessageKey = it.messageKey,
          )
        }
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        _state.value = _state.value.copy(error = error.message ?: "撤回失败")
      }
    }
  }

  fun deletePrivateMessage(message: AccountMessage) {
    val selected = _state.value.messages.firstOrNull { it.id == _state.value.selectedMessageId } ?: return
    val updated = _state.value.privateMessageHistory[selected.userMid].orEmpty().filterNot { it.id == message.id }
    privateMessageHistoryCache = privateMessageHistoryCache + (selected.userMid to updated)
    _state.value = _state.value.copy(privateMessageHistory = privateMessageHistoryCache)
  }

  private fun updatePrivateMessage(messageId: Long, transform: (AccountMessage) -> AccountMessage) {
    val selected = _state.value.messages.firstOrNull { it.id == _state.value.selectedMessageId } ?: return
    val updated =
      _state.value.privateMessageHistory[selected.userMid].orEmpty().map { message ->
        if (message.id == messageId) transform(message) else message
      }
    privateMessageHistoryCache = privateMessageHistoryCache + (selected.userMid to updated)
    _state.value = _state.value.copy(privateMessageHistory = privateMessageHistoryCache)
  }

  fun consumeError() {
    if (_state.value.error != null) _state.value = _state.value.copy(error = null)
  }

  fun createFavoriteFolder(title: String, isPublic: Boolean) {
    val ownerMid = mid
    if (
      ownerMid <= 0L ||
        _state.value.section != MySection.FAVORITES ||
        _state.value.favoriteFolderActionBusy
    ) return
    _state.value = _state.value.copy(favoriteFolderActionBusy = true, error = null)
    viewModelScope.launch {
      try {
        val folders =
          withContext(Dispatchers.IO) {
            BiliApi.createFavoriteFolder(title, isPublic)
            BiliApi.getFavoriteFolders(ownerMid)
          }
        if (mid != ownerMid || _state.value.section != MySection.FAVORITES) return@launch
        val previousSelection = _state.value.selectedFolderId
        val selection = previousSelection ?: folders.lastOrNull()?.id
        _state.value =
          _state.value.copy(
            folders = folders,
            selectedFolderId = selection,
            favoriteFolderActionBusy = false,
          )
        if (previousSelection == null && selection != null) loadFavoritePage(reset = true)
      } catch (error: Exception) {
        _state.value =
          _state.value.copy(
            favoriteFolderActionBusy = false,
            error = error.message ?: "收藏夹创建失败",
          )
      }
    }
  }

  private fun enrichAccountMessageUserStyles(
    messages: List<AccountMessage>,
    generation: Long,
    expectedMid: Long,
    section: MySection,
  ) {
    val userIds = messages.map { it.userMid }.filter { it > 0L }.distinct()
    if (userIds.isEmpty()) return
    viewModelScope.launch {
      val gate = Semaphore(6)
      val styles =
        coroutineScope {
          userIds.map { userMid ->
            async(Dispatchers.IO) {
              gate.withPermit { runCatching { userMid to BiliApi.getAccountMessageUserStyle(userMid) }.getOrNull() }
            }
          }.mapNotNull { it.await() }.toMap()
        }
      if (!isCurrentLoad(generation, expectedMid, section) || styles.isEmpty()) return@launch
      _state.value =
        _state.value.copy(
          messages = applyAccountMessageUserStyles(_state.value.messages, styles)
        )
    }
  }

  fun editFavoriteFolder(folder: FavoriteFolder, title: String, isPublic: Boolean) {
    val ownerMid = mid
    if (
      ownerMid <= 0L ||
        _state.value.section != MySection.FAVORITES ||
        _state.value.favoriteFolderActionBusy
    ) return
    _state.value = _state.value.copy(favoriteFolderActionBusy = true, error = null)
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { BiliApi.editFavoriteFolder(folder.id, title, isPublic) }
        if (mid != ownerMid || _state.value.section != MySection.FAVORITES) return@launch
        _state.value =
          _state.value.copy(
            folders =
              _state.value.folders.map {
                if (it.id == folder.id) it.copy(title = title.trim(), isPublic = isPublic) else it
              },
            favoriteFolderActionBusy = false,
          )
      } catch (error: Exception) {
        _state.value =
          _state.value.copy(
            favoriteFolderActionBusy = false,
            error = error.message ?: "收藏夹修改失败",
          )
      }
    }
  }

  fun deleteFavoriteFolder(folder: FavoriteFolder) {
    val ownerMid = mid
    if (
      ownerMid <= 0L ||
        _state.value.section != MySection.FAVORITES ||
        _state.value.favoriteFolderActionBusy
    ) return
    _state.value = _state.value.copy(favoriteFolderActionBusy = true, error = null)
    viewModelScope.launch {
      try {
        val folders =
          withContext(Dispatchers.IO) {
            BiliApi.deleteFavoriteFolder(folder.id)
            BiliApi.getFavoriteFolders(ownerMid)
          }
        if (mid != ownerMid || _state.value.section != MySection.FAVORITES) return@launch
        val oldSelection = _state.value.selectedFolderId
        val selection = oldSelection?.takeIf { id -> folders.any { it.id == id } } ?: folders.firstOrNull()?.id
        _state.value =
          _state.value.copy(
            folders = folders,
            selectedFolderId = selection,
            favoriteFolderActionBusy = false,
          )
        if (selection != oldSelection) {
          if (selection == null) {
            _state.value = _state.value.copy(videos = emptyList(), favoriteHasMore = false)
          } else loadFavoritePage(reset = true)
        }
      } catch (error: Exception) {
        _state.value =
          _state.value.copy(
            favoriteFolderActionBusy = false,
            error = error.message ?: "收藏夹删除失败",
          )
      }
    }
  }

  fun setFavoriteQuery(query: String) {
    val state = _state.value
    if (state.section != MySection.FAVORITES || query == state.favoriteQuery) return
    _state.value = state.copy(favoriteQuery = query)
    favoriteSearchJob?.cancel()
    favoriteSearchJob = viewModelScope.launch {
      delay(280)
      loadFavoritePage(reset = true)
    }
  }

  fun loadMoreFavorites() {
    val state = _state.value
    if (
      state.section != MySection.FAVORITES ||
        state.loading ||
        state.favoriteLoadingMore ||
        !state.favoriteHasMore
    )
      return
    loadFavoritePage(reset = false)
  }

  private fun loadFavoritePage(reset: Boolean) {
    val state = _state.value
    val folderId = state.selectedFolderId ?: return
    val expectedMid = mid
    if (expectedMid <= 0L || state.section != MySection.FAVORITES) return
    val page = if (reset) 1 else state.favoritePage + 1
    val query = state.favoriteQuery.trim()
    val generation = ++loadGeneration
    loadJob?.cancel()
    _state.value =
      state.copy(
        videos = if (reset) emptyList() else state.videos,
        favoriteResourceIdByVideoId = if (reset) emptyMap() else state.favoriteResourceIdByVideoId,
        favoriteTypeByVideoId = if (reset) emptyMap() else state.favoriteTypeByVideoId,
        favoritePage = if (reset) 0 else state.favoritePage,
        favoriteHasMore = if (reset) false else state.favoriteHasMore,
        favoriteLoadingMore = !reset,
        loading = reset,
        error = null,
      )
    loadJob = viewModelScope.launch {
      try {
        val response =
          withContext(Dispatchers.IO) { BiliApi.getFavoriteVideos(folderId, page, query) }
        if (!isCurrentLoad(generation, expectedMid, MySection.FAVORITES)) return@launch
        val loadedVideos = response.cards.map(::toFeedItem)
        val loadedAids = response.cards.associate { card -> favoriteVideoId(card) to card.aid }
        val loadedTypes =
          response.cards.associate { card -> favoriteVideoId(card) to card.resourceType }
        _state.value =
          _state.value.copy(
            videos =
              (if (reset) loadedVideos else _state.value.videos + loadedVideos).distinctBy {
                it.id
              },
            favoriteResourceIdByVideoId = _state.value.favoriteResourceIdByVideoId + loadedAids,
            favoriteTypeByVideoId = _state.value.favoriteTypeByVideoId + loadedTypes,
            favoritePage = page,
            favoriteHasMore = response.hasMore,
            favoriteLoadingMore = false,
            loading = false,
          )
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (!isCurrentLoad(generation, expectedMid, MySection.FAVORITES)) return@launch
        _state.value =
          _state.value.copy(
            favoriteLoadingMore = false,
            loading = false,
            error = error.message ?: "收藏夹加载失败",
          )
      }
    }
  }

  fun removeFavorite(video: FeedItem) {
    updateFavoriteFolders(video, destinationFolderId = null, move = true)
  }

  fun copyFavorite(video: FeedItem, destinationFolderId: Long) {
    updateFavoriteFolders(video, destinationFolderId, move = false)
  }

  fun moveFavorite(video: FeedItem, destinationFolderId: Long) {
    updateFavoriteFolders(video, destinationFolderId, move = true)
  }

  private fun updateFavoriteFolders(
    video: FeedItem,
    destinationFolderId: Long?,
    move: Boolean,
  ) {
    val state = _state.value
    val sourceFolderId = state.selectedFolderId ?: return
    val resourceId = state.favoriteResourceIdByVideoId[video.id] ?: return
    val resourceType = state.favoriteTypeByVideoId[video.id] ?: return
    val ownerMid = mid
    if (
      state.section != MySection.FAVORITES ||
        state.favoriteActionBusyId != null ||
        ownerMid <= 0L ||
        resourceId <= 0L ||
        resourceType <= 0 ||
        destinationFolderId == sourceFolderId
    )
      return
    _state.value = state.copy(favoriteActionBusyId = video.id, error = null)
    viewModelScope.launch {
      try {
        val sourceWasPresent =
          withContext(Dispatchers.IO) {
            BiliApi.favoriteFolderContains(sourceFolderId, resourceId, resourceType)
          }
        if (!sourceWasPresent) throw IllegalStateException("这个内容已经不在当前收藏夹了")
        val destinationWasPresent =
          destinationFolderId?.let { folderId ->
            withContext(Dispatchers.IO) {
              BiliApi.favoriteFolderContains(folderId, resourceId, resourceType)
            }
          } ?: false
        if (!move && destinationWasPresent) {
          throw IllegalStateException("目标收藏夹已经有这个内容了")
        }
        withContext(Dispatchers.IO) {
          when {
            destinationFolderId == null ->
              BiliApi.removeFavoriteResource(
                resourceId = resourceId,
                resourceType = resourceType,
                folderId = sourceFolderId,
              )
            move && destinationWasPresent ->
              BiliApi.removeFavoriteResource(
                resourceId = resourceId,
                resourceType = resourceType,
                folderId = sourceFolderId,
              )
            move ->
              BiliApi.moveFavoriteResource(
                ownerMid = ownerMid,
                resourceId = resourceId,
                resourceType = resourceType,
                sourceFolderId = sourceFolderId,
                targetFolderId = destinationFolderId,
              )
            else ->
              BiliApi.copyFavoriteResource(
                ownerMid = ownerMid,
                resourceId = resourceId,
                resourceType = resourceType,
                sourceFolderId = sourceFolderId,
                targetFolderId = destinationFolderId,
              )
          }
        }
        verifyFavoriteAction(
          resourceId = resourceId,
          resourceType = resourceType,
          sourceFolderId = sourceFolderId,
          destinationFolderId = destinationFolderId,
          move = move,
        )
        if (mid != ownerMid || _state.value.section != MySection.FAVORITES) return@launch
        val current = _state.value
        val removesFromCurrent = move && current.selectedFolderId == sourceFolderId
        _state.value =
          current.copy(
            videos =
              if (removesFromCurrent) current.videos.filterNot { it.id == video.id }
              else current.videos,
            favoriteResourceIdByVideoId =
              if (removesFromCurrent) current.favoriteResourceIdByVideoId - video.id
              else current.favoriteResourceIdByVideoId,
            favoriteTypeByVideoId =
              if (removesFromCurrent) current.favoriteTypeByVideoId - video.id
              else current.favoriteTypeByVideoId,
            folders =
              favoriteFoldersAfterAction(
                folders = current.folders,
                sourceFolderId = sourceFolderId,
                destinationFolderId = destinationFolderId?.takeUnless { destinationWasPresent },
                move = move,
              ),
            favoriteActionBusyId = null,
          )
      } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        _state.value =
          _state.value.copy(
            favoriteActionBusyId = null,
            error = error.message ?: "收藏操作失败",
          )
      }
    }
  }

  private suspend fun verifyFavoriteAction(
    resourceId: Long,
    resourceType: Int,
    sourceFolderId: Long,
    destinationFolderId: Long?,
    move: Boolean,
  ) {
    repeat(3) { attempt ->
      val sourceContains =
        withContext(Dispatchers.IO) {
          BiliApi.favoriteFolderContains(sourceFolderId, resourceId, resourceType)
        }
      val destinationContains = destinationFolderId?.let { folderId ->
        withContext(Dispatchers.IO) {
          BiliApi.favoriteFolderContains(folderId, resourceId, resourceType)
        }
      }
      val confirmed =
        favoriteActionConfirmed(
          sourceContains = sourceContains,
          destinationContains = destinationContains,
          hasDestination = destinationFolderId != null,
          move = move,
        )
      if (confirmed) return
      if (attempt < 2) delay(350L * (attempt + 1))
    }
    throw IllegalStateException("服务器没有确认这次收藏操作，请稍后重试")
  }

  private fun isCurrentLoad(generation: Long, expectedMid: Long, section: MySection): Boolean =
    generation == loadGeneration && expectedMid == mid && _state.value.section == section

  private fun toFeedItem(card: FeedCard) =
    FeedItem(
      id = card.bvid.ifBlank { card.aid.toString() },
      title = card.title,
      videoUrl = "https://www.bilibili.com/video/${card.bvid.ifBlank { "av${card.aid}" }}",
      coverUrl = card.coverUrl,
      uploader = card.uploaderName,
      playCount = FeedViewModel.formatCount(card.playCount),
      duration = FeedViewModel.formatDuration(card.durationSeconds),
      uploaderFace = card.uploaderFace,
      uploaderMid = card.uploaderMid,
      danmakuCount = card.danmakuCount,
      publishedAt = card.pubdate,
      description = card.description,
    )

  private fun toHistoryCardItem(item: AccountHistoryItem): HistoryCardItem? =
    when (item) {
      is AccountHistoryItem.Video -> HistoryCardItem.Video(toFeedItem(item.card), item.viewAt)
      is AccountHistoryItem.Bangumi ->
        HistoryCardItem.Bangumi(
          item =
            toFeedItem(item.card).copy(
              id = item.bangumi.id,
              videoUrl = item.bangumi.videoUrl,
            ),
          bangumi = item.bangumi,
          mediaLabel = item.mediaLabel,
          viewAt = item.viewAt,
        )
      is AccountHistoryItem.Article -> HistoryCardItem.Article(item.article, item.viewAt)
      is AccountHistoryItem.Live ->
        HistoryCardItem.Live(
          room =
            LiveSearchRoom(
              roomId = item.roomId,
              uid = item.anchorUid,
              title = item.title,
              uname = item.anchorName,
              faceUrl = item.anchorFace,
              coverUrl = item.coverUrl,
              keyframeUrl = item.keyframeUrl,
              areaName = item.areaName,
              parentAreaName = item.parentAreaName,
              liveStatus = item.liveStatus,
            ),
          viewAt = item.viewAt,
        )
    }

  private fun readLocalLiveHistory(): List<HistoryCardItem.Live> =
    LiveHistoryStore.read(getApplication()).map { item ->
      HistoryCardItem.Live(item.room, item.viewedAt)
    }

  private fun readLocalBangumiHistory(): List<HistoryCardItem.Bangumi> =
    BangumiLocalHistoryStore.read(getApplication()).map { stored ->
      val historyId = "history:pgc:ep${stored.episodeId}"
      val seasonType = stored.seasonType.takeIf { it > 0 } ?: 1
      val mediaLabel = pgcMediaLabel(seasonType)
      val progressPercent =
        stored.durationMs
          .takeIf { it > 0L }
          ?.let { duration -> ((stored.positionMs * 100L) / duration).toInt().coerceIn(0, 100) }
      val videoUrl = "https://www.bilibili.com/bangumi/play/ep${stored.episodeId}"
      val bangumi =
        SpaceContentCard(
          id = historyId,
          title = stored.title,
          subtitle = stored.episodeTitle,
          historyCoverUrl = stored.coverUrl,
          aid = stored.aid,
          bvid = stored.bvid,
          videoUrl = videoUrl,
          seasonId = stored.seasonId,
          episodeId = stored.episodeId,
          kind =
            if (seasonType == 1 || seasonType == 4) SpaceContentKind.BANGUMI
            else SpaceContentKind.DRAMA,
          watchProgress =
            BangumiWatchProgress(
              episodeId = stored.episodeId,
              episodeIndex = stored.episodeTitle,
              positionMs = stored.positionMs,
              percent = progressPercent,
            ),
          seasonType = seasonType,
          hasHistory = true,
          historicalOnly = true,
          lastViewedAt = stored.viewedAt,
        )
      HistoryCardItem.Bangumi(
        item =
          FeedItem(
            id = historyId,
            title = stored.title,
            videoUrl = videoUrl,
            coverUrl = stored.coverUrl,
            uploader = mediaLabel,
            playCount = null,
            duration =
              stored.durationMs
                .takeIf { it > 0L }
                ?.let { FeedViewModel.formatDuration(it / 1_000L) },
            publishedAt = stored.viewedAt,
            description = stored.episodeTitle,
          ),
        bangumi = bangumi,
        mediaLabel = mediaLabel,
        viewAt = stored.viewedAt,
      )
    }

  private fun pgcMediaLabel(seasonType: Int): String =
    when (seasonType) {
      2 -> "电影"
      3 -> "纪录片"
      4 -> "国创"
      5 -> "电视剧"
      7 -> "综艺"
      else -> "番剧"
    }

  private fun favoriteVideoId(card: FeedCard): String = card.bvid.ifBlank { card.aid.toString() }
}

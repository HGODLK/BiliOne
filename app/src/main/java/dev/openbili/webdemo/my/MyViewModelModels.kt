package dev.openbili.webdemo.my

/**
 * MyViewModel 的纯函数模型层：未读退避、用户样式合并、私信会话归一化、
 * 历史合并键与收藏夹操作判定，全部为可单测的纯函数。
 */

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.api.AccountHistoryItem
import dev.openbili.webdemo.api.AccountHistoryResponse
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.AccountMessageUserStyle
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BangumiWatchProgress
import dev.openbili.webdemo.api.BiliEmotePackage
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.FeedCard
import dev.openbili.webdemo.api.FollowingGroup
import dev.openbili.webdemo.api.FollowingUser
import dev.openbili.webdemo.api.HistoryCursor
import dev.openbili.webdemo.api.InteractionMessagePage
import dev.openbili.webdemo.api.MessageCursor
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import dev.openbili.webdemo.BangumiLocalHistoryStore
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.live.LiveHistoryStore
import dev.openbili.webdemo.live.LiveSearchRoom
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

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

private val controllerHiddenMySections =
  setOf(MySection.MESSAGES, MySection.INTERACTIONS, MySection.LIKES)

/** 控制器模式不提供依赖文字输入或精细触控的消息分区。 */
internal fun mySectionsForControlMode(controlMode: Boolean): List<MySection> =
  if (controlMode) MySection.entries.filterNot(controllerHiddenMySections::contains)
  else MySection.entries

internal const val PRIVATE_MESSAGE_SYNC_INTERVAL_MS = 800L
internal const val PRIVATE_SESSION_PAGE_SIZE = 18
internal const val PRIVATE_HISTORY_PAGE_SIZE = 15
internal const val ACCOUNT_UNREAD_REFRESH_INTERVAL_MS = 5_000L
internal const val MY_VIEW_MODEL_TAG = "MyViewModel"

/** 未读刷新连续失败时的退避间隔：5/10/30 秒。 */
internal fun accountUnreadRetryDelayMs(consecutiveFailures: Int): Long =
  when {
    consecutiveFailures <= 1 -> 5_000L
    consecutiveFailures == 2 -> 10_000L
    else -> 30_000L
  }

internal fun applyAccountMessageUserStyles(
  messages: List<AccountMessage>,
  styles: Map<Long, AccountMessageUserStyle>,
): List<AccountMessage> = messages.map { message ->
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
  // 发送后的即时读取和实时轮询可能重叠。服务器因此可能在同一批里返回重复序列；
  // 绝不让重复的稳定 ID 到达 LazyColumn。
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
  val withoutAcknowledgedLocalCopies = sorted.filterNot { pending ->
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
    result
      .asSequence()
      .filter(AccountMessage::withdrawn)
      .map(AccountMessage::withdrawTargetMessageKey)
      .filter { it > 0L }
      .toSet()
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

  data class Video(val item: FeedItem, override val viewAt: Long = item.publishedAt) :
    HistoryCardItem {
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

/** 网页端历史搜索的独立状态，避免把搜索结果混入普通游标列表。 */
data class HistorySearchState(
  val query: String = "",
  val items: List<HistoryCardItem> = emptyList(),
  val page: Int = 0,
  val total: Int = 0,
  val hasMore: Boolean = false,
  val loading: Boolean = false,
  val error: String? = null,
)

/**
 * 历史页的跨来源去重键。
 *
 * 本地追番记录和服务端游标记录的稳定 ID 生成规则不同，不能直接用 stableId 去重：
 * 同一分集可能一条是 `history:pgc:ep...`，另一条却只有 bvid 或 aid。优先使用稿件身份，
 * 再回退到分集/季度身份；这样既能合并同一条记录，也不会把同一季度的不同分集吞掉。
 */
internal fun historyMergeKey(item: HistoryCardItem): String =
  when (item) {
    is HistoryCardItem.Video -> {
      val id = item.item.id.trim()
      when {
        id.startsWith("BV", ignoreCase = true) -> "bvid:${id.lowercase()}"
        id.toLongOrNull()?.let { it > 0L } == true -> "aid:$id"
        else -> item.stableId
      }
    }
    is HistoryCardItem.Bangumi -> {
      val bvid = item.bangumi.bvid.trim()
      when {
        item.bangumi.episodeId > 0L -> "pgc:ep:${item.bangumi.episodeId}"
        bvid.isNotBlank() -> "bvid:${bvid.lowercase()}"
        item.bangumi.aid > 0L -> "aid:${item.bangumi.aid}"
        item.bangumi.seasonId > 0L -> "pgc:season:${item.bangumi.seasonId}"
        else -> item.stableId
      }
    }
    is HistoryCardItem.Article -> item.stableId
    is HistoryCardItem.Live -> item.stableId
  }

/** 按网页端的观看时间排序后去重，分页追加和本地记录合并都使用同一规则。 */
internal fun mergeHistoryItems(items: Iterable<HistoryCardItem>): List<HistoryCardItem> =
  items
    .sortedWith(compareByDescending<HistoryCardItem> { it.viewAt }.thenBy { it.stableId })
    .distinctBy(::historyMergeKey)

/** "我的"页界面状态（各分区数据与未读标记）。 */
data class MyUiState(
  val section: MySection = MySection.HISTORY,
  val folders: List<FavoriteFolder> = emptyList(),
  val selectedFolderId: Long? = null,
  val videos: List<FeedItem> = emptyList(),
  val historyFilter: HistoryFilter = HistoryFilter.ALL,
  val historyItems: List<HistoryCardItem> = emptyList(),
  val historyPeriods: Map<HistoryPeriod, HistoryPeriodLoadState> = emptyMap(),
  val historyTimeline: HistoryTimelineLoadState = HistoryTimelineLoadState(),
  /** 保留旧字段供状态切换和兼容调用使用；实际历史分页由 historyPeriods 管理。 */
  val historyCursor: HistoryCursor = HistoryCursor(),
  val historyHasMore: Boolean = false,
  val historyLoadingMore: Boolean = false,
  val historySearch: HistorySearchState = HistorySearchState(),
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

internal data class PendingUnreadAcknowledgement(
  val acknowledgedCount: Int,
  val createdAtMs: Long,
)

internal data class UnreadAcknowledgementResolution(
  val visibleCount: Int,
  val keepPending: Boolean,
)

/** 服务端未读汇总可能短暂滞后；扣除刚确认的旧未读，同时仍允许新增数量立即显示。 */
internal fun resolveUnreadAfterAcknowledgement(
  serverCount: Int,
  pending: PendingUnreadAcknowledgement?,
  nowMs: Long,
  timeoutMs: Long = 30_000L,
): UnreadAcknowledgementResolution {
  val safeServerCount = serverCount.coerceAtLeast(0)
  if (pending == null) return UnreadAcknowledgementResolution(safeServerCount, false)
  if (safeServerCount == 0) return UnreadAcknowledgementResolution(0, false)
  if (nowMs - pending.createdAtMs >= timeoutMs) {
    return UnreadAcknowledgementResolution(safeServerCount, false)
  }
  return UnreadAcknowledgementResolution(
    visibleCount = (safeServerCount - pending.acknowledgedCount).coerceAtLeast(0),
    keepPending = true,
  )
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

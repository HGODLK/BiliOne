package dev.openbili.webdemo.live

const val LIVE_ORIGINAL_QN = 10_000

data class LiveAreaFilter(
  val parentAreaId: Int,
  val areaId: Int = 0,
  val name: String,
  val iconUrl: String? = null,
) {
  val stableId: String
    get() = "$parentAreaId:$areaId"
}

data class LiveAreaGroup(
  val parent: LiveAreaFilter,
  val children: List<LiveAreaFilter>,
)

data class LiveSearchRoom(
  val roomId: Long,
  val shortRoomId: Long? = null,
  val uid: Long,
  val title: String,
  val uname: String,
  val faceUrl: String? = null,
  val coverUrl: String? = null,
  val keyframeUrl: String? = null,
  val areaName: String? = null,
  val parentAreaName: String? = null,
  val watchedText: String? = null,
  val liveStatus: Int = 1,
) {
  val stableId: String
    get() = "live:$roomId"
}

enum class LiveHomeSourceSection {
  HERO,
  FOLLOWING,
  FEED,
}

data class LiveHomeSourceAnchor(
  val section: LiveHomeSourceSection,
  val roomId: Long,
  val sectionKey: String = "",
) {
  val stableId: String
    get() = "${section.name.lowercase()}:$sectionKey:$roomId"

  companion object {
    fun hero(roomId: Long) = LiveHomeSourceAnchor(LiveHomeSourceSection.HERO, roomId)

    fun following(roomId: Long) =
      LiveHomeSourceAnchor(LiveHomeSourceSection.FOLLOWING, roomId)

    fun feed(roomId: Long, areaKey: String) =
      LiveHomeSourceAnchor(LiveHomeSourceSection.FEED, roomId, areaKey)
  }
}

data class LiveSearchResponse(
  val rooms: List<LiveSearchRoom>,
  val hasMore: Boolean,
)

data class LiveFollowingResponse(
  val isLoggedIn: Boolean,
  val rooms: List<LiveSearchRoom>,
)

data class LiveRoomInfo(
  val roomId: Long,
  val shortRoomId: Long?,
  val anchorUid: Long,
  val title: String,
  val description: String,
  val coverUrl: String?,
  val keyframeUrl: String?,
  val areaName: String?,
  val parentAreaName: String?,
  val liveStatus: Int,
  val online: Long,
)

data class LiveAnchorInfo(
  val uid: Long,
  val name: String,
  val faceUrl: String?,
)

data class LiveQuality(
  val qn: Int,
  val description: String,
)

enum class LiveStreamFormat {
  HLS_FMP4,
  HLS_TS,
  HTTP_FLV,
}

data class LiveStreamSource(
  val url: String,
  val format: LiveStreamFormat,
  val codec: String,
  val cdnIndex: Int = 0,
)

data class LivePlayInfo(
  val requestedQn: Int,
  val currentQn: Int,
  val qualities: List<LiveQuality>,
  val sources: List<LiveStreamSource>,
)

data class LiveDanmuEndpoint(
  val host: String,
  val wssPort: Int,
)

data class LiveDanmuConfig(
  val token: String,
  val endpoints: List<LiveDanmuEndpoint>,
)

data class FanMedalBadge(
  val name: String,
  val level: Int,
  val anchorUid: Long?,
  val color: Long?,
  val borderColor: Long?,
  val startColor: Long?,
  val endColor: Long?,
)

enum class LiveEmojiKind {
  BASE,
  OWNED,
  ROOM_EXCLUSIVE,
}

data class LiveEmoji(
  val displayName: String,
  val inputText: String,
  val sendToken: String,
  val fileId: String?,
  val imageUrl: String,
  val kind: LiveEmojiKind,
  val roomId: Long?,
  val directSend: Boolean,
  val isBulge: Boolean,
  val available: Boolean = true,
  val unavailableReason: String? = null,
)

data class LiveEmojiPack(
  val id: String,
  val title: String?,
  val iconUrl: String?,
  val kind: LiveEmojiKind,
  val roomId: Long?,
  val emojis: List<LiveEmoji>,
)

sealed interface LiveMessageDelivery {
  data object Received : LiveMessageDelivery

  data object Pending : LiveMessageDelivery

  data class Sent(val serverId: String? = null) : LiveMessageDelivery

  data class Failed(val reason: String) : LiveMessageDelivery
}

sealed interface LiveChatContent {
  data class Text(
    val text: String,
    val emotes: Map<String, String> = emptyMap(),
  ) : LiveChatContent

  data class Emoji(
    val displayName: String,
    val fileId: String?,
    val imageUrl: String?,
    val isBulge: Boolean,
  ) : LiveChatContent

  data class System(val text: String) : LiveChatContent
}

enum class LiveLotteryStatus {
  ACTIVE,
  JOINING,
  JOINED,
  ENDED,
  AWARDED,
  INVALID,
}

data class LiveLotteryWinner(
  val uid: Long,
  val name: String,
  val faceUrl: String?,
)

data class LiveInteractiveLottery(
  val id: Long,
  val roomId: Long,
  val awardName: String,
  val awardNum: Int,
  val awardImageUrl: String?,
  val command: String,
  val requireText: String,
  val requireType: Int,
  val requireValue: Int,
  val giftId: Long,
  val giftNum: Int,
  val giftPrice: Long,
  val sendGiftEnsure: Boolean,
  val endAtEpochMs: Long,
  val status: LiveLotteryStatus = LiveLotteryStatus.ACTIVE,
  val error: String? = null,
  val winners: List<LiveLotteryWinner> = emptyList(),
) {
  val requiresPayment: Boolean
    get() = giftId > 0L || giftPrice > 0L || giftNum > 0 || sendGiftEnsure
}

data class LiveChatMessage(
  val stableId: String,
  val uid: Long?,
  val uname: String?,
  val faceUrl: String?,
  val content: LiveChatContent,
  val fanMedal: FanMedalBadge?,
  val delivery: LiveMessageDelivery = LiveMessageDelivery.Received,
  val receivedAtMs: Long,
)

/**
 * A display-only live danmaku event. Its clock is deliberately captured when the socket event
 * reaches this client rather than from the server's chat timestamp, which can already be stale by
 * the time it is rendered.
 */
data class LiveDanmakuEvent(
  val stableId: String,
  val content: LiveChatContent,
  val enterAtElapsedMs: Long,
)

data class LiveRankUser(
  val uid: Long,
  val rank: Int,
  val name: String,
  val faceUrl: String?,
  val score: Long?,
  val accompanyDays: Int?,
  val fanMedal: FanMedalBadge?,
  val guardLevel: Int?,
)

data class LiveAudienceRank(
  val countText: String,
  val valueText: String?,
  val items: List<LiveRankUser>,
)

data class LiveGuardRankPage(
  val totalCount: Int,
  val totalPageHint: Int?,
  val currentPage: Int,
  val actualType: Int,
  val top3: List<LiveRankUser>,
  val items: List<LiveRankUser>,
)

enum class LiveRankTab(val title: String) {
  AUDIENCE("房间观众"),
  GUARD("大航海"),
}

data class LiveAudienceRankState(
  val type: String = "online_rank",
  val switch: String = "contribution_rank",
  val countText: String = "",
  val valueText: String? = null,
  val items: List<LiveRankUser> = emptyList(),
  val isLoading: Boolean = false,
  val error: String? = null,
)

data class LiveGuardRankState(
  val typ: Int = 4,
  val totalCount: Int = 0,
  val items: List<LiveRankUser> = emptyList(),
  val nextPage: Int = 1,
  val totalPageHint: Int? = null,
  val endReached: Boolean = false,
  val isLoading: Boolean = false,
  val error: String? = null,
)

enum class LiveConnectionState {
  DISCONNECTED,
  CONNECTING,
  CONNECTED,
  RETRYING,
}

data class LiveComposerState(
  val text: String = "",
  val selectionStart: Int = 0,
  val sending: Boolean = false,
  val emojiPanelVisible: Boolean = false,
  val selectedEmojiPackId: String? = null,
  val error: String? = null,
)

data class LiveRoomUiState(
  val entryRoomId: Long = 0L,
  val navigationEntryId: Long = 0L,
  val generation: Long = 0L,
  val roomInfo: LiveRoomInfo? = null,
  val anchorInfo: LiveAnchorInfo? = null,
  val loading: Boolean = false,
  val error: String? = null,
  val playback: LivePlayInfo? = null,
  val playbackLoading: Boolean = false,
  val playbackError: String? = null,
  val activeSourceIndex: Int = 0,
  val connection: LiveConnectionState = LiveConnectionState.DISCONNECTED,
  val connectionError: String? = null,
  val messages: List<LiveChatMessage> = emptyList(),
  val liveDanmaku: List<LiveDanmakuEvent> = emptyList(),
  val watchedText: String? = null,
  val online: Long = 0L,
  val emojiPacks: List<LiveEmojiPack> = emptyList(),
  val emojiLoading: Boolean = false,
  val emojiError: String? = null,
  val activeMedal: FanMedalBadge? = null,
  val interactiveLottery: LiveInteractiveLottery? = null,
  val composer: LiveComposerState = LiveComposerState(),
  val recommendations: List<LiveSearchRoom> = emptyList(),
  val recommendationsLoading: Boolean = false,
  val recommendationsError: String? = null,
  val rankTab: LiveRankTab = LiveRankTab.AUDIENCE,
  val audienceRank: LiveAudienceRankState = LiveAudienceRankState(),
  val guardRank: LiveGuardRankState = LiveGuardRankState(),
  val followed: Boolean = false,
  val followBusy: Boolean = false,
  val followingGroups: List<dev.openbili.webdemo.api.FollowingGroup> = emptyList(),
  val followingGroupsLoading: Boolean = false,
)

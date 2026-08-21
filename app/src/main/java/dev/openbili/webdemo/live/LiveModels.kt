package dev.openbili.webdemo.live

/**
 * 直播域的数据模型与 UI 状态定义。
 *
 * 本文件只承载纯数据（data class / sealed interface / enum / 常量），不含任何 IO 或
 * 组合逻辑：搜索/关注房间、分区、播放信息、弹幕连接配置、表情、抽奖、聊天消息、
 * 排行榜，以及直播间整体 UI 状态 [LiveRoomUiState] 均在此集中声明，供 LiveApi、
 * LiveDanmakuSocket 与各 UI 组件共享。
 *
 * 命名以 State 结尾的类型（[LiveAudienceRankState]、[LiveGuardRankState]、
 * [LiveComposerState]、[LiveRoomUiState]）表示"当前页面的可变快照"：本身是不可变
 * 数据类，由上层以 copy 方式整体推进。
 */

/**
 * 原画清晰度对应的 qn 请求值。
 *
 * 直播流接口以 qn 表达清晰度，数值越大越清晰；此常量给"原画/蓝光"档位一个语义化
 * 名称，避免各处散落裸魔数 10000。
 */
const val LIVE_ORIGINAL_QN = 10_000

/**
 * 直播分区筛选项：父分区 + 子分区。
 *
 * [areaId] 为 0 表示仅父分区（无子分区细分）；[stableId] 用 "父:子" 唯一标识筛选项，
 * 供列表 Diff 与分区筛选回传使用。
 */
data class LiveAreaFilter(
  val parentAreaId: Int,
  val areaId: Int = 0,
  val name: String,
  val iconUrl: String? = null,
) {
  val stableId: String
    get() = "$parentAreaId:$areaId"
}

/**
 * 直播分区组：一个父分区及其全部子分区。
 */
data class LiveAreaGroup(
  val parent: LiveAreaFilter,
  val children: List<LiveAreaFilter>,
)

/**
 * 直播搜索/关注接口返回的房间条目，也是直播间入场与卡片渲染共用的最小模型。
 *
 * 字段来自 B 站搜索与关注接口的扁平化结果；[stableId] 以 roomId 保证列表项键稳定。
 * [keyframeUrl] 为进房前的占位关键帧；分区 ID 用于直播间内的同分区推荐；[liveStatus] 默认 1（直播中）。
 */
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
  val parentAreaId: Int = 0,
  val areaId: Int = 0,
  val watchedText: String? = null,
  val liveStatus: Int = 1,
) {
  val stableId: String
    get() = "live:$roomId"
}

/**
 * 首页直播入口的三个来源区块，用于标记直播间是从哪里被点开的，以便返回时还原。
 */
enum class LiveHomeSourceSection {
  HERO,
  FOLLOWING,
  FEED,
}

/**
 * 首页直播卡片到直播间的转场来源锚点：记录来源区块、房间号与可选分区键，
 * 供共享转场动画计算"从哪张卡片放大进入"。
 *
 * [sectionKey] 仅 FEED 区块使用，用于区分不同推荐分区下的同一房间。
 */
data class LiveHomeSourceAnchor(
  val section: LiveHomeSourceSection,
  val roomId: Long,
  val sectionKey: String = "",
) {
  val stableId: String
    get() = "${section.name.lowercase()}:$sectionKey:$roomId"

  // 各来源区块的便捷构造入口。
  companion object {
    fun hero(roomId: Long) = LiveHomeSourceAnchor(LiveHomeSourceSection.HERO, roomId)

    fun following(roomId: Long) = LiveHomeSourceAnchor(LiveHomeSourceSection.FOLLOWING, roomId)

    fun feed(roomId: Long, areaKey: String) =
      LiveHomeSourceAnchor(LiveHomeSourceSection.FEED, roomId, areaKey)
  }
}

/**
 * 直播搜索接口的一页结果：[rooms] 为本页房间，[hasMore] 表示是否还有下一页。
 */
data class LiveSearchResponse(
  val rooms: List<LiveSearchRoom>,
  val hasMore: Boolean,
)

/**
 * 直播关注接口的结果；[isLoggedIn] 为 false 时 [rooms] 恒为空，UI 据此提示登录。
 */
data class LiveFollowingResponse(
  val isLoggedIn: Boolean,
  val rooms: List<LiveSearchRoom>,
)

/**
 * 直播间详情接口返回的完整房间信息（封面、关键帧、分区、直播状态、在线人数等）。
 */
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
  val parentAreaId: Int = 0,
  val areaId: Int = 0,
  val liveStatus: Int,
  val online: Long,
)

/**
 * 主播信息（uid、昵称、头像）。
 */
data class LiveAnchorInfo(
  val uid: Long,
  val name: String,
  val faceUrl: String?,
)

/**
 * 一路直播清晰度档位：[qn] 为档位数值，[description] 为展示名（如"原画""高清"）。
 */
data class LiveQuality(
  val qn: Int,
  val description: String,
)

/**
 * 直播流的封装/传输格式，决定播放器采用哪种加载与解析路径。
 */
enum class LiveStreamFormat {
  HLS_FMP4,
  HLS_TS,
  HTTP_FLV,
}

/**
 * 一路可播放的直播流：地址、格式、编码与 CDN 线路。
 *
 * 同一清晰度可能返回多条线路，[cdnIndex] 用于区分不同 CDN 节点，便于卡顿时切换重试。
 */
data class LiveStreamSource(
  val url: String,
  val format: LiveStreamFormat,
  val codec: String,
  val cdnIndex: Int = 0,
)

/**
 * 直播播放地址解析结果：请求档位、实际生效档位、可选清晰度列表与全部流地址。
 *
 * 请求档位可能拿不到而被服务端降级，故同时保留 [requestedQn] 与 [currentQn]，
 * 便于 UI 展示"实际清晰度"。
 */
data class LivePlayInfo(
  val requestedQn: Int,
  val currentQn: Int,
  val qualities: List<LiveQuality>,
  val sources: List<LiveStreamSource>,
)

/**
 * 弹幕 WebSocket 长连接端点（host + wss 端口）。
 */
data class LiveDanmuEndpoint(
  val host: String,
  val wssPort: Int,
)

/**
 * 弹幕连接配置：鉴权 token 与可用端点列表。
 */
data class LiveDanmuConfig(
  val token: String,
  val endpoints: List<LiveDanmuEndpoint>,
)

/**
 * 粉丝牌徽章展示数据（名称、等级、所属主播与配色/渐变）。
 */
data class FanMedalBadge(
  val name: String,
  val level: Int,
  val anchorUid: Long?,
  val color: Long?,
  val borderColor: Long?,
  val startColor: Long?,
  val endColor: Long?,
)

/**
 * 直播间表情的分类：基础表情 / 已拥有表情 / 房间专属表情。
 */
enum class LiveEmojiKind {
  BASE,
  OWNED,
  ROOM_EXCLUSIVE,
}

/**
 * 单个直播间表情的展示与发送信息。
 *
 * [inputText] 是插入输入框的文本形式，[sendToken] 是发送时上行的标识，[isBulge]
 * 表示该表情是否为"大表情"（放大渲染）；[available]/[unavailableReason] 表达
 * 该表情当前是否可用及其原因。
 */
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

/**
 * 一包表情：按来源（基础/拥有/房间专属）分组的表情集合。
 */
data class LiveEmojiPack(
  val id: String,
  val title: String?,
  val iconUrl: String?,
  val kind: LiveEmojiKind,
  val roomId: Long?,
  val emojis: List<LiveEmoji>,
)

/**
 * 一条聊天消息的投递状态。
 *
 * 本地消息从 [Pending] 起步，成功后转 [Sent]，失败转 [Failed]；服务端推送的消息
 * 默认为 [Received]。用 sealed interface 表达四态，避免用多个可空字段拼凑状态机。
 */
sealed interface LiveMessageDelivery {
  /** 服务端推送、已成功收到的消息。 */
  data object Received : LiveMessageDelivery

  /** 本地发出、尚未确认成功。 */
  data object Pending : LiveMessageDelivery

  /** 已发送成功；[serverId] 为服务端返回的消息 ID（可为空）。 */
  data class Sent(val serverId: String? = null) : LiveMessageDelivery

  /** 发送失败；[reason] 为失败原因描述。 */
  data class Failed(val reason: String) : LiveMessageDelivery
}

/**
 * 聊天内容的三类载体：纯文本、表情、系统消息。
 */
sealed interface LiveChatContent {
  /** 纯文本消息；[emotes] 为文本中表情占位符到其资源 ID 的映射。 */
  data class Text(
    val text: String,
    val emotes: Map<String, String> = emptyMap(),
  ) : LiveChatContent

  /** 单个表情消息（作为独立气泡发送的"大表情"）。 */
  data class Emoji(
    val displayName: String,
    val fileId: String?,
    val imageUrl: String?,
    val isBulge: Boolean,
  ) : LiveChatContent

  /** 系统通知消息（如欢迎、入场提示等）。 */
  data class System(val text: String) : LiveChatContent
}

/**
 * 互动抽奖的参与状态流转。
 */
enum class LiveLotteryStatus {
  ACTIVE,
  JOINING,
  JOINED,
  ENDED,
  AWARDED,
  INVALID,
}

/**
 * 抽奖中奖用户。
 */
data class LiveLotteryWinner(
  val uid: Long,
  val name: String,
  val faceUrl: String?,
)

/**
 * 直播间互动抽奖的完整信息（奖品、参与条件、截止时间、状态与中奖名单）。
 */
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
  /**
   * 是否需要送礼/付费才能参与：只要奖品涉及礼物或要求"送礼物确认"即为付费抽奖。
   */
  val requiresPayment: Boolean
    get() = giftId > 0L || giftPrice > 0L || giftNum > 0 || sendGiftEnsure
}

/**
 * 聊天面板里渲染的一条消息：投递状态、用户信息、内容与粉丝牌。
 *
 * [stableId] 由构造方生成以稳定 Diff；[receivedAtMs] 为本地到达时间，用于排序展示。
 */
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
 * 仅用于弹幕滚动展示的事件。其时间戳刻意取自 socket 事件到达本客户端的那一刻，
 * 而非服务端聊天时间戳——后者在渲染时往往已经过期。
 */
data class LiveDanmakuEvent(
  val stableId: String,
  val content: LiveChatContent,
  val enterAtElapsedMs: Long,
)

/**
 * 排行榜单条用户：名次、分数、粉丝牌与舰长等级等。
 */
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

/**
 * 观众榜（贡献榜）的汇总结果。
 */
data class LiveAudienceRank(
  val countText: String,
  val valueText: String?,
  val items: List<LiveRankUser>,
)

/**
 * 大航海（舰长）榜的一页数据，含前三名与分页游标。
 */
data class LiveGuardRankPage(
  val totalCount: Int,
  val totalPageHint: Int?,
  val currentPage: Int,
  val actualType: Int,
  val top3: List<LiveRankUser>,
  val items: List<LiveRankUser>,
)

/**
 * 直播间排行榜页签：观众榜 / 大航海榜。
 */
enum class LiveRankTab(val title: String) {
  AUDIENCE("房间观众"),
  GUARD("大航海"),
}

/**
 * 观众榜页的可变状态快照：请求参数、结果、加载与错误。
 *
 * [type]/[switch] 是 B 站榜单接口的固定请求参数，带默认值以便首屏直接请求。
 */
data class LiveAudienceRankState(
  val type: String = "online_rank",
  val switch: String = "contribution_rank",
  val countText: String = "",
  val valueText: String? = null,
  val items: List<LiveRankUser> = emptyList(),
  val isLoading: Boolean = false,
  val error: String? = null,
)

/**
 * 大航海榜页的可变状态快照：分页游标 [nextPage]、累计结果、加载与错误。
 */
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

/**
 * 弹幕 WebSocket 长连接的四种状态。
 */
enum class LiveConnectionState {
  DISCONNECTED,
  CONNECTING,
  CONNECTED,
  RETRYING,
}

/**
 * 弹幕输入框（发送器）的可变状态快照：文本、光标、发送中、表情面板与错误。
 */
data class LiveComposerState(
  val text: String = "",
  val selectionStart: Int = 0,
  val sending: Boolean = false,
  val emojiPanelVisible: Boolean = false,
  val selectedEmojiPackId: String? = null,
  val error: String? = null,
)

/**
 * 直播间的整体 UI 状态快照。
 *
 * 单个数据类聚合房间信息、播放、连接、消息、表情、抽奖、推荐与排行榜等所有子状态，
 * 由上层以 copy 方式整体推进。[generation] 在换房后自增，用于丢弃上一房间未完成的
 * 异步回写，避免旧结果污染新房间。
 */
data class LiveRoomUiState(
  val entryRoomId: Long = 0L,
  /** 导航令牌：标识本次进入房间的导航来源，用于返回栈还原。 */
  val navigationEntryId: Long = 0L,
  /** 状态代数：换房后自增，用于丢弃上一房间未完成的异步回写。 */
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

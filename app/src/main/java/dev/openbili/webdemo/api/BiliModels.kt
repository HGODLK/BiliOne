package dev.openbili.webdemo.api

import dev.openbili.webdemo.UrlPolicy
import org.json.JSONObject

const val DANMAKU_COLORFUL_NONE = 0
const val DANMAKU_COLORFUL_VIP_GRADIENT = 60001

// ── Feed ─────────────────────────────────────────────────────────────────────

data class FeedCard(
  val aid: Long,
  val bvid: String,
  val cid: Long,
  val title: String,
  val coverUrl: String,
  val uploaderName: String,
  val uploaderFace: String,
  val uploaderMid: Long,
  val playCount: Long,
  val danmakuCount: Long,
  val durationSeconds: Long,
  val pubdate: Long,
  val description: String = "",
  val resourceType: Int = 2,
) {
  companion object {
    fun fromJson(obj: JSONObject): FeedCard {
      // Popular API uses "aid"; recommendation API uses "id". Accept either.
      val aid = if (obj.has("aid")) obj.getLong("aid") else obj.getLong("id")
      val cid = if (obj.has("cid")) obj.getLong("cid") else 0L
      return FeedCard(
        aid = aid,
        bvid = obj.optString("bvid", ""),
        cid = cid,
        title = obj.optString("title", ""),
        coverUrl = obj.optString("pic", ""),
        uploaderName = obj.optJSONObject("owner")?.optString("name") ?: obj.optString("author", ""),
        uploaderFace = obj.optJSONObject("owner")?.optString("face") ?: "",
        uploaderMid = obj.optJSONObject("owner")?.optLong("mid", 0) ?: obj.optLong("mid", 0),
        playCount = obj.optJSONObject("stat")?.optLong("view", 0) ?: obj.optLong("play", 0),
        danmakuCount =
          obj.optJSONObject("stat")?.optLong("danmaku", 0)
            ?: obj.optLong("video_review", obj.optLong("comment", 0)),
        durationSeconds = parseDuration(obj.optString("duration", obj.optString("length", "0"))),
        pubdate = obj.optLong("pubdate", obj.optLong("created", 0)),
        description = obj.optString("intro", obj.optString("desc", "")),
      )
    }

    private fun parseDuration(value: String): Long {
      value.toLongOrNull()?.let {
        return it
      }
      val parts = value.split(':').mapNotNull(String::toLongOrNull)
      return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0L
      }
    }
  }
}

data class FeedResponse(val cards: List<FeedCard>)

// ── Video info ───────────────────────────────────────────────────────────────

data class VideoInfo(
  val bvid: String,
  val aid: Long,
  val cid: Long,
  val title: String,
  val coverUrl: String,
  val uploaderName: String,
  val uploaderFace: String,
  val uploaderMid: Long,
  val durationSeconds: Long,
  val playCount: Long,
  val danmakuCount: Long,
  val replyCount: Long,
  val likeCount: Long,
  val coinCount: Long,
  val favoriteCount: Long,
  val shareCount: Long,
  val publishedAt: Long,
  val desc: String,
  val pages: List<VideoPage>,
  val collection: VideoCollection? = null,
)

data class VideoEngagement(
  val liked: Boolean = false,
  val coins: Int = 0,
  val favorited: Boolean = false,
)

data class VideoPage(val page: Int, val cid: Long, val part: String, val durationSeconds: Long)

data class VideoCollection(
  val id: Long,
  val title: String,
  val episodes: List<VideoCollectionEpisode>,
)

data class VideoCollectionEpisode(
  val bvid: String,
  val cid: Long,
  val title: String,
  val coverUrl: String,
  val durationSeconds: Long,
  val uploaderName: String,
  val uploaderFace: String,
  val uploaderMid: Long,
  val playCount: Long,
  val danmakuCount: Long,
  val publishedAt: Long,
)

enum class CommentSort(val label: String, val apiValue: Int) {
  DEFAULT("默认", 2),
  TIME("最新", 0),
}

// ── Play URL ─────────────────────────────────────────────────────────────────

data class VideoStream(
  val id: Int,
  val quality: String,
  val url: String,
  val codecId: Int,
  val codecs: String,
  val width: Int = 0,
  val height: Int = 0,
  val frameRate: Float = 0f,
  val bandwidth: Long = 0L,
  val mimeType: String = "video/mp4",
  val initializationRange: String = "",
  val indexRange: String = "",
  val backupUrls: List<String> = emptyList(),
)

data class AudioStream(
  val id: Int,
  val url: String,
  val bandwidth: Long = 0L,
  val mimeType: String = "audio/mp4",
  val codecs: String = "",
  val initializationRange: String = "",
  val indexRange: String = "",
  val backupUrls: List<String> = emptyList(),
)

enum class PremiumAudioMode(val label: String) {
  DOLBY("Dolby"),
  HI_RES("HiRes"),
}

data class PlayUrlData(
  val dashAudioUrl: String?,
  val dolbyAudioUrl: String? = null,
  val hiResAudioUrl: String? = null,
  val dashAudio: AudioStream? = null,
  val dolbyAudio: AudioStream? = null,
  val hiResAudio: AudioStream? = null,
  val premiumAudioMode: PremiumAudioMode? = null,
  val streams: List<VideoStream>,
  val currentStreamIndex: Int,
  val durationMs: Long = 0L,
) {
  fun selectedAudioUrl(): String? =
    when (premiumAudioMode) {
      PremiumAudioMode.DOLBY -> dolbyAudioUrl
      PremiumAudioMode.HI_RES -> hiResAudioUrl
      null -> dashAudioUrl
    } ?: dashAudioUrl

  fun selectedAudio(): AudioStream? =
    when (premiumAudioMode) {
      PremiumAudioMode.DOLBY -> dolbyAudio
      PremiumAudioMode.HI_RES -> hiResAudio
      null -> dashAudio
    } ?: dashAudio

  fun supportsPremiumAudio(mode: PremiumAudioMode): Boolean =
    when (mode) {
      PremiumAudioMode.DOLBY -> dolbyAudio != null || !dolbyAudioUrl.isNullOrBlank()
      PremiumAudioMode.HI_RES -> hiResAudio != null || !hiResAudioUrl.isNullOrBlank()
    }
}

// ── Danmaku ──────────────────────────────────────────────────────────────────

data class DanmakuInlineEmote(
  val token: String,
  val imageUrl: String,
)

data class DanmakuItem(
  val timeMs: Long, // 弹幕出现时间（毫秒）
  val type: Int, // 1-3 滚动, 4 底部, 5 顶部
  val fontSize: Int,
  val color: Int,
  val content: String,
  val isLocal: Boolean = false,
  val sourceId: String? = null,
  val colorful: Int = DANMAKU_COLORFUL_NONE,
  /** Optional live-room emoji rendered by the same lane scheduler as text danmaku. */
  val imageUrl: String? = null,
  val imageLarge: Boolean = false,
  val inlineEmotes: List<DanmakuInlineEmote> = emptyList(),
)

internal data class DanmakuMaskResource(
  val fps: Int,
  val bytes: ByteArray,
)

class DanmakuMaskTimeline
internal constructor(
  private val frameTimesMs: IntArray,
  /** Normalized closed contours packed as x/y pairs and separated by NaN/NaN. */
  private val protectedContours: List<FloatArray>,
  private val inverseFills: BooleanArray,
  private val evenOddFills: BooleanArray,
) {
  val isEmpty: Boolean
    get() = frameTimesMs.isEmpty()

  fun frameIndexAt(positionMs: Long): Int {
    if (frameTimesMs.isEmpty() || positionMs < frameTimesMs[0]) return -1
    val target = positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    var low = 0
    var high = frameTimesMs.size
    while (low < high) {
      val middle = (low + high) ushr 1
      if (frameTimesMs[middle] <= target) low = middle + 1 else high = middle
    }
    return low - 1
  }

  internal fun protectedContoursAt(index: Int): FloatArray =
    protectedContours.getOrElse(index) { EMPTY_CONTOURS }

  internal fun isInverseFillAt(index: Int): Boolean = inverseFills.getOrElse(index) { false }

  internal fun usesEvenOddFillAt(index: Int): Boolean = evenOddFills.getOrElse(index) { false }

  internal fun isProtectedAt(positionMs: Long, normalizedX: Float, normalizedY: Float): Boolean {
    val frameIndex = frameIndexAt(positionMs)
    val contours = protectedContoursAt(frameIndex)
    var crossings = 0
    var winding = 0
    var index = 0
    while (index + 1 < contours.size) {
      if (contours[index].isNaN() || contours[index + 1].isNaN()) {
        index += 2
        continue
      }
      val start = index
      while (
        index + 1 < contours.size &&
          !contours[index].isNaN() &&
          !contours[index + 1].isNaN()
      ) {
        index += 2
      }
      var previous = index - 2
      var cursor = start
      while (cursor < index) {
        val currentX = contours[cursor]
        val currentY = contours[cursor + 1]
        val previousX = contours[previous]
        val previousY = contours[previous + 1]
        if (
          (currentY > normalizedY) != (previousY > normalizedY) &&
            normalizedX <
              (previousX - currentX) * (normalizedY - currentY) /
                (previousY - currentY) +
                currentX
        ) {
          crossings += 1
          winding += if (currentY > previousY) 1 else -1
        }
        previous = cursor
        cursor += 2
      }
    }
    val covered = if (usesEvenOddFillAt(frameIndex)) crossings % 2 != 0 else winding != 0
    return if (isInverseFillAt(frameIndex)) !covered else covered
  }

  private companion object {
    val EMPTY_CONTOURS = FloatArray(0)
  }
}

// ── Comment ──────────────────────────────────────────────────────────────────

data class CommentImage(
  val url: String,
  val width: Int = 0,
  val height: Int = 0,
)

data class CommentMention(
  val mid: Long,
  val name: String,
)

data class CommentItem(
  val rpid: Long,
  val mid: Long,
  val name: String,
  val face: String,
  val content: String,
  val likeCount: Long,
  val replyCount: Long,
  val ctime: Long,
  val liked: Boolean = false,
  val emotes: Map<String, String> = emptyMap(),
  val location: String = "",
  val images: List<CommentImage> = emptyList(),
  val mentions: List<CommentMention> = emptyList(),
  val level: Int = 0,
  val vipActive: Boolean = false,
  val vipLabel: String = "",
)

data class CommentResponse(
  val items: List<CommentItem>,
  val hasMore: Boolean,
  val totalCount: Long = items.size.toLong(),
)

data class CommentNavigationTarget(
  val oid: Long,
  val type: Int,
  val rootRpid: Long,
  val targetRpid: Long,
  /** A fresh id for every card click, including repeat visits to the same reply. */
  val requestId: Long = System.nanoTime(),
)

data class CommentThread(
  val root: CommentItem,
  val replies: List<CommentItem>,
  val hasMore: Boolean,
)

data class BiliEmote(val text: String, val url: String)

data class BiliEmotePackage(val id: Long, val name: String, val emotes: List<BiliEmote>)

data class MentionSuggestion(
  val mid: Long,
  val name: String,
  val face: String,
  val subtitle: String,
  val followed: Boolean,
)

data class SpaceProfile(
  val mid: Long,
  val name: String,
  val face: String,
  val banner: String,
  val signature: String,
  val followerCount: Long,
  val followingCount: Long,
  val sex: String = "保密",
  val level: Int = 0,
  val vipActive: Boolean = false,
  val vipLabel: String = "",
  val vipIconUrl: String = "",
  val ipLocation: String = "",
  val followed: Boolean = false,
)

data class FollowingGroup(
  val id: Long,
  val name: String,
  val count: Int,
)

data class BangumiWatchProgress(
  val episodeId: Long,
  val episodeIndex: String,
  val positionMs: Long,
  val percent: Int? = null,
)

/** Semantic state of a season/user/status response for watch progress resolution. */
enum class BangumiWatchProgressState {
  RESOLVED,
  NO_RECORD,
  UNAVAILABLE,
}

data class SpaceContentCard(
  val id: String,
  val title: String,
  val subtitle: String = "",
  val coverUrl: String = "",
  val aid: Long = 0L,
  val bvid: String = "",
  val videoUrl: String = "",
  val seasonId: Long = 0L,
  val episodeId: Long = 0L,
  val kind: SpaceContentKind = SpaceContentKind.COLLECTION,
  val watchProgress: BangumiWatchProgress? = null,
  val seasonType: Int = 0,
  val hasHistory: Boolean = false,
  val historicalOnly: Boolean = false,
  val historyCoverUrl: String = "",
)

enum class SpaceContentKind {
  COLLECTION,
  BANGUMI,
  DRAMA,
}

data class SpaceBangumiResponse(
  val cards: List<SpaceContentCard>,
  val hasMore: Boolean,
)

/** One cursor page of PGC history after it has been restricted to a home-page media category. */
data class BangumiWatchingHistoryPage(
  val cards: List<SpaceContentCard>,
  val cursor: HistoryCursor,
  val hasMore: Boolean,
)

data class BangumiEpisode(
  val id: Long,
  val aid: Long,
  val bvid: String,
  val cid: Long,
  val title: String,
  val longTitle: String,
  val coverUrl: String,
  val durationSeconds: Long,
)

data class BangumiSeasonOption(
  val seasonId: Long,
  val title: String,
)

data class BangumiSection(
  val id: Long,
  val title: String,
  val episodes: List<BangumiEpisode>,
)

data class BangumiRecommendation(
  val stableId: String,
  val title: String,
  val bannerUrl: String,
  val cardUrl: String,
  val targetUrl: String,
  val isLive: Boolean = false,
  val seasonId: Long = 0L,
  val episodeId: Long = 0L,
  val position: Int,
)

enum class BangumiExploreCategory(
  val label: String,
  val apiName: String,
  val endpointVersion: Int,
) {
  ANIME("番剧", "anime", 3),
  GUOCHUANG("国创", "guochuang", 3),
  MOVIE("电影", "movie", 2),
  TV("电视剧", "tv", 2),
  DOCUMENTARY("纪录片", "documentary", 2),
  VARIETY("综艺", "variety", 2),
}

enum class BangumiExploreCardStyle {
  LANDSCAPE,
  POSTER,
}

/** Purpose-specific PGC crops used by the web version instead of one generic season cover. */
enum class BangumiCoverVariant(
  internal val imageSpec: String,
) {
  NEW_HOT_HERO("600w_506h_!web-ogv-anime-newhot-bg.webp"),
  NEW_HOT_CARD("368w_202h_!web-ogv-anime-newhot-card.webp"),
  HORIZONTAL_CARD("560w_312h_!web-ogv-anime-horizontal-card.webp"),
  POSTER("560w_746h_!web-ogv-anime-ranking-card.webp"),
}

/** Returns Bilibili's purpose-specific PGC derivative when the cover comes from its image CDN. */
fun bangumiCoverUrl(rawUrl: String, variant: BangumiCoverVariant): String {
  val normalized = UrlPolicy.normalizeImageUrl(rawUrl).orEmpty()
  if (normalized.isBlank() || !normalized.contains("hdslb.com/")) return normalized
  val base = normalized.substringBefore('?').substringBefore('@')
  val query = normalized.substringAfter('?', "")
  return "$base@${variant.imageSpec}" + if (query.isBlank()) "" else "?$query"
}

/** Returns the original CDN image while preserving query parameters and removing a derived crop. */
fun bangumiOriginalImageUrl(rawUrl: String): String {
  val normalized = UrlPolicy.normalizeImageUrl(rawUrl).orEmpty()
  if (normalized.isBlank() || !normalized.contains("hdslb.com/")) return normalized
  val base = normalized.substringBefore('?').substringBefore('@')
  val query = normalized.substringAfter('?', "")
  return base + if (query.isBlank()) "" else "?$query"
}

/** Semantic role of a PGC module. The UI must not depend on mutable server-side titles. */
enum class BangumiExploreSectionKind {
  HOT,
  RANKING,
  TIMELINE,
  FEED,
  RECOMMENDATION,
  OTHER,
}

/**
 * Aggregate result of /pgc/view/web/season/user/status. Replaces the narrower
 * `getBangumiUserFollowed` signature so consumers can access both follow and
 * watch-progress state from a single response.
 */
data class BangumiUserStatus(
  val followed: Boolean?,
  val watchProgress: BangumiWatchProgress?,
)

data class BangumiExploreItem(
  val stableId: String,
  val title: String,
  val subtitle: String,
  val coverUrl: String,
  val targetUrl: String,
  val seasonId: Long,
  val episodeId: Long,
  val style: BangumiExploreCardStyle,
  /** Section this item came from; used to keep section-specific transition behavior explicit. */
  val sectionKind: BangumiExploreSectionKind = BangumiExploreSectionKind.OTHER,
  val rating: Double? = null,
  val ratingCount: Long = 0L,
  val heroCoverUrl: String = coverUrl,
  val watchProgress: BangumiWatchProgress? = null,
  val seasonType: Int = 0,
  val hasHistory: Boolean = false,
  val historicalOnly: Boolean = false,
)

data class BangumiExploreSection(
  val stableId: String,
  val title: String,
  val items: List<BangumiExploreItem>,
  val kind: BangumiExploreSectionKind = BangumiExploreSectionKind.OTHER,
)

data class BangumiExplorePage(
  val category: BangumiExploreCategory,
  val sections: List<BangumiExploreSection>,
)

/** Query values for the web PGC index. `-1` preserves Bilibili's "all" semantics. */
data class BangumiIndexQuery(
  val seasonVersion: String = "-1",
  val spokenLanguageType: String = "-1",
  val area: String = "-1",
  val isFinish: String = "-1",
  val copyright: String = "-1",
  val seasonStatus: String = "-1",
  val seasonMonth: String = "-1",
  val year: String = "-1",
  val styleId: String = "-1",
  /** Documentary/movie/TV use a release-date range instead of the anime `year` filter. */
  val releaseDate: String = "-1",
  /** Documentary-only producer filter. Not exposed yet, so it stays `-1`. */
  val producerId: String = "-1",
  val order: BangumiIndexOrder = BangumiIndexOrder.FOLLOWING,
  val sortDescending: Boolean = true,
)

enum class BangumiIndexOrder(val parameter: String, val label: String) {
  UPDATED("0", "更新时间"),
  VIEWS("2", "播放数量"),
  FOLLOWING("3", "追番人数"),
  SCORE("4", "最高评分"),
  RELEASED("5", "开播时间"),
}

data class BangumiIndexItem(
  val seasonId: Long,
  val mediaId: Long,
  val episodeId: Long,
  val title: String,
  val subtitle: String,
  val coverUrl: String,
  val targetUrl: String,
  val indexShow: String,
  val badge: String,
  val badgeColor: String,
  val badgeNightColor: String,
  val score: String,
  val orderText: String,
  val seasonType: Int,
) {
  val stableId: String
    get() = "bangumi-index:${seasonId.takeIf { it > 0L } ?: episodeId}"
}

data class BangumiIndexPage(
  val items: List<BangumiIndexItem>,
  val page: Int,
  val hasNext: Boolean,
  val total: Int,
)

data class BangumiSeason(
  val seasonId: Long,
  val mediaId: Long,
  val title: String,
  val coverUrl: String,
  val evaluate: String,
  val typeName: String,
  val areas: List<String>,
  val styles: List<String>,
  val publishText: String,
  val rating: Double?,
  val ratingCount: Long,
  val followCount: Long,
  val viewCount: Long,
  val danmakuCount: Long,
  val followed: Boolean,
  val episodes: List<BangumiEpisode>,
  val seasons: List<BangumiSeasonOption>,
  val sections: List<BangumiSection>,
  val userRatingScore: Int? = null,
)

data class SpaceDynamicImage(
  val url: String,
  val width: Int = 0,
  val height: Int = 0,
)

data class SpaceDynamicVideo(
  val aid: Long,
  val bvid: String,
  val title: String,
  val description: String = "",
  val coverUrl: String = "",
  val duration: String = "",
  val playCount: String = "",
  val danmakuCount: String = "",
)

data class SpaceDynamicItem(
  val id: String,
  val text: String,
  val publishTimestamp: Long,
  val authorMid: Long,
  val authorName: String,
  val authorFace: String,
  val emotes: Map<String, String> = emptyMap(),
  val images: List<SpaceDynamicImage> = emptyList(),
  val video: SpaceDynamicVideo? = null,
  val article: ArticleItem? = null,
  val commentOid: Long,
  val commentType: Int,
  val commentCount: Long = 0,
  val likeCount: Long = 0,
  val liked: Boolean = false,
  val pinned: Boolean = false,
  val repostCount: Long = 0,
)

data class SpaceDynamicResponse(
  val items: List<SpaceDynamicItem>,
  val offset: String = "",
  val hasMore: Boolean = false,
)

data class SpaceVideoResponse(val cards: List<FeedCard>, val hasMore: Boolean)

data class FavoriteFolder(
  val id: Long,
  val title: String,
  val mediaCount: Int,
  val favorited: Boolean = false,
  val isPublic: Boolean = true,
)

data class FollowingUser(
  val mid: Long,
  val name: String,
  val face: String,
  val signature: String,
  val groupIds: List<Long> = emptyList(),
)

data class FollowingResponse(
  val items: List<FollowingUser>,
  val totalCount: Int,
  val hasMore: Boolean,
)

enum class MessageTargetKind {
  VIDEO,
  ARTICLE,
  UNKNOWN,
}

data class AccountMessage(
  val id: Long,
  val userMid: Long,
  val userName: String,
  val userFace: String,
  val title: String,
  val content: String,
  val sourceContent: String,
  val oid: Long,
  val rootId: Long,
  val parentId: Long,
  val time: Long,
  val coverUrl: String = "",
  val linkUrl: String = "",
  val messageType: Int = 0,
  val isPrivate: Boolean = false,
  val targetKind: MessageTargetKind = MessageTargetKind.UNKNOWN,
  val subjectTitle: String = "",
  val targetCommentId: Long = parentId,
  val commentType: Int = 1,
  val userLevel: Int = 0,
  val userVipActive: Boolean = false,
  val userVipLabel: String = "",
  val senderMid: Long = 0L,
  val receiverMid: Long = 0L,
  val sequence: Long = 0L,
  val messageKey: Long = 0L,
  val unreadCount: Int = 0,
  val isOutgoing: Boolean = false,
  val withdrawn: Boolean = false,
  val withdrawTargetMessageKey: Long = 0L,
  val isPrivateNotice: Boolean = false,
  val mediaWidth: Int = 0,
  val mediaHeight: Int = 0,
)

data class AccountMessageUserStyle(
  val level: Int = 0,
  val vipActive: Boolean = false,
  val vipLabel: String = "",
)

data class MessageCursor(val id: Long = 0L, val time: Long = 0L)

data class AccountMessagePage(
  val items: List<AccountMessage>,
  val cursor: MessageCursor = MessageCursor(),
  val hasMore: Boolean = false,
)

data class PrivateMessagePage(
  val items: List<AccountMessage>,
  val endSequence: Long = 0L,
  val hasMore: Boolean = false,
)

data class PrivateSessionPage(
  val items: List<AccountMessage>,
  val endTimestamp: Long = 0L,
  val hasMore: Boolean = false,
)

data class InteractionMessagePage(
  val items: List<AccountMessage>,
  val replyCursor: MessageCursor = MessageCursor(),
  val atCursor: MessageCursor = MessageCursor(),
  val replyHasMore: Boolean = false,
  val atHasMore: Boolean = false,
)

data class HotSearchItem(val keyword: String, val displayName: String)

data class SearchUser(
  val mid: Long,
  val name: String,
  val face: String,
  val sign: String,
  val fans: Long,
  val videoCount: Int,
  val level: Int = 0,
  val vipActive: Boolean = false,
  val vipLabel: String = "",
)

// ── Article / account history ────────────────────────────────────────────────

data class ArticleItem(
  val id: Long,
  val title: String,
  val summary: String = "",
  val coverUrl: String = "",
  val authorName: String = "",
  val authorFace: String = "",
  val authorMid: Long = 0L,
  val categoryName: String = "",
  val publishedAt: Long = 0L,
  val viewCount: Long = 0L,
  val likeCount: Long = 0L,
  val replyCount: Long = 0L,
  val sourceUrl: String = "",
) {
  val stableId: String
    get() = "article:$id"
}

sealed interface ArticleBlock {
  data class Text(
    val content: String,
    val heading: Boolean = false,
    val quote: Boolean = false,
    val emotes: Map<String, String> = emptyMap(),
    val mentions: List<CommentMention> = emptyList(),
  ) : ArticleBlock

  data class Image(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val caption: String = "",
  ) : ArticleBlock

  data class Code(val content: String, val language: String = "") : ArticleBlock

  data class Video(val bvid: String) : ArticleBlock

  data object Divider : ArticleBlock
}

data class ArticleDetail(
  val article: ArticleItem,
  val blocks: List<ArticleBlock>,
  val commentOid: Long = article.id,
  val commentType: Int = 12,
)

data class ArticleSearchResponse(val items: List<ArticleItem>, val hasMore: Boolean)

enum class AccountHistoryKind {
  VIDEO,
  ARTICLE,
  LIVE,
}

sealed interface AccountHistoryItem {
  val stableId: String
  val viewAt: Long

  data class Video(val card: FeedCard, override val viewAt: Long = card.pubdate) : AccountHistoryItem {
    override val stableId: String = "video:${card.bvid.ifBlank { card.aid.toString() }}"
  }

  data class Bangumi(
    val card: FeedCard,
    val bangumi: SpaceContentCard,
    val mediaLabel: String,
    override val viewAt: Long = card.pubdate,
  ) : AccountHistoryItem {
    override val stableId: String = bangumi.id
  }

  data class Article(
    val article: ArticleItem,
    override val viewAt: Long = article.publishedAt,
  ) : AccountHistoryItem {
    override val stableId: String = article.stableId
  }

  data class Live(
    val roomId: Long,
    val title: String,
    val anchorUid: Long,
    val anchorName: String,
    val anchorFace: String?,
    val coverUrl: String?,
    val keyframeUrl: String?,
    val areaName: String?,
    val parentAreaName: String?,
    val liveStatus: Int,
    override val viewAt: Long = 0L,
  ) : AccountHistoryItem {
    override val stableId: String = "live:$roomId"
  }
}

data class HistoryCursor(
  val max: Long = 0L,
  val viewAt: Long = 0L,
  val business: String = "",
)

data class AccountHistoryResponse(
  val items: List<AccountHistoryItem>,
  val cursor: HistoryCursor,
  val hasMore: Boolean,
)

// ── Login / user ─────────────────────────────────────────────────────────────

data class UserInfo(
  val mid: Long,
  val name: String,
  val face: String,
  val isLogin: Boolean,
  val vipActive: Boolean = false,
)

data class QrCodeInfo(val url: String, val qrcodeKey: String)

data class QrStatus(val code: Int, val message: String)

data class AppQrStatus(
  val code: Int,
  val message: String,
  val mid: Long = 0L,
  val accessToken: String = "",
  val refreshToken: String = "",
  val expiresInSeconds: Long = 0L,
)

// code: 86101=等待扫码 86090=已扫码待确认 0=登录成功 86038=过期

// ── WBI keys ─────────────────────────────────────────────────────────────────

data class WbiKeys(val imgKey: String, val subKey: String, val fetchedAt: Long) {
  val isValid: Boolean
    get() = (System.currentTimeMillis() - fetchedAt) < 12 * 3600_000L
}

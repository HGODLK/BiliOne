package dev.openbili.webdemo.api

/**
 * 全项目共用的 API 数据模型。
 *
 * 按业务域分节组织：信息流卡片、视频信息与播放地址、弹幕与智能防挡、评论、个人空间
 * 与动态、番剧探索、文章与账号历史、登录用户、WBI 签名密钥。
 */

import dev.openbili.webdemo.UrlPolicy
import dev.openbili.webdemo.live.LiveSearchRoom
import org.json.JSONObject

const val DANMAKU_COLORFUL_NONE = 0
const val DANMAKU_COLORFUL_VIP_GRADIENT = 60001

// ── 信息流 ─────────────────────────────────────────────────────────────────────

/** 全站通用的视频卡片模型，供推荐/热门/历史/收藏/缓存等页面共用。 */
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
      // 热门接口用 "aid"、推荐接口用 "id"，两者都兼容。
      val aid = if (obj.has("aid")) obj.getLong("aid") else obj.getLong("id")
      val cid = if (obj.has("cid")) obj.getLong("cid") else 0L
      return FeedCard(
        aid = aid,
        bvid = obj.optString("bvid", ""),
        cid = cid,
        title = obj.optString("title", ""),
        coverUrl = UrlPolicy.normalizeImageUrl(obj.optString("pic", "")).orEmpty(),
        uploaderName = obj.optJSONObject("owner")?.optString("name") ?: obj.optString("author", ""),
        uploaderFace =
          UrlPolicy.normalizeImageUrl(obj.optJSONObject("owner")?.optString("face").orEmpty())
            .orEmpty(),
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

data class PopularPeriod(
  val id: Int,
  val label: String,
  val publishedAt: Long = 0,
  val subject: String = "",
)

// ── 视频信息 ───────────────────────────────────────────────────────────────

/** 视频资料（标题/UP 主/分 P/封面等）。 */
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
  val copyright: Int = 0,
)

data class VideoEngagement(
  val liked: Boolean = false,
  val coins: Int = 0,
  val favorited: Boolean = false,
  val loaded: Boolean = false,
)

internal fun videoCoinLimit(copyright: Int): Int = if (copyright == 1 || copyright == 3) 2 else 1

internal fun remainingVideoCoins(copyright: Int, alreadyCoined: Int): Int =
  (videoCoinLimit(copyright) - alreadyCoined).coerceAtLeast(0)

internal fun parseVideoEngagement(data: JSONObject): VideoEngagement {
  check(data.has("like") && data.has("coin") && data.has("favorite")) {
    "互动状态响应不完整"
  }

  fun booleanValue(name: String): Boolean =
    when (val value = data.opt(name)) {
      is Boolean -> value
      is Number -> value.toInt() != 0
      is String -> value == "1" || value.equals("true", ignoreCase = true)
      else -> false
    }

  return VideoEngagement(
    liked = booleanValue("like"),
    coins = data.optInt("coin", 0).coerceAtLeast(0),
    favorited = booleanValue("favorite"),
    loaded = true,
  )
}

data class VideoPage(
  val page: Int,
  val cid: Long,
  val part: String,
  val durationSeconds: Long,
)

data class VideoCollection(
  val id: Long,
  val title: String,
  val episodes: List<VideoCollectionEpisode>,
  val sections: List<VideoCollectionSection> = emptyList(),
)

/** 合集中的可展开分组；普通合集通常只有一个分组或没有分组。 */
data class VideoCollectionSection(
  val id: Long,
  val title: String,
  val episodes: List<VideoCollectionEpisode>,
)

data class VideoCollectionEpisode(
  val aid: Long = 0L,
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

// ── 播放地址 ─────────────────────────────────────────────────────────────────

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

// ── 弹幕 ──────────────────────────────────────────────────────────────────

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
  /** 直播房间可选表情，由与文字弹幕相同的分道调度器渲染。 */
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
  /** 归一化的允许弹幕背景轮廓，打包为 x/y 点对并以 NaN/NaN 分隔各轮廓。 */
  private val allowedContours: List<FloatArray>,
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

/**
 * 解析出绘制用的蒙版帧。
 *
 * 两段有效轮廓之间的短暂空采样通常是上游单帧漏检：只在这个有界间隔内沿用前一轮廓；
 * 开头、结尾或持续的空段保持为空，避免给没有受保护主体的场景过度加蒙版。
 */
  internal fun renderFrameIndexAt(positionMs: Long): Int {
    val frameIndex = frameIndexAt(positionMs)
    if (frameIndex < 0 || allowedContoursAt(frameIndex).isNotEmpty()) return frameIndex

    var previousNonEmpty = frameIndex - 1
    while (previousNonEmpty >= 0 && allowedContoursAt(previousNonEmpty).isEmpty()) {
      previousNonEmpty--
    }
    if (previousNonEmpty < 0) return frameIndex

    var nextNonEmpty = frameIndex + 1
    while (nextNonEmpty < frameTimesMs.size && allowedContoursAt(nextNonEmpty).isEmpty()) {
      nextNonEmpty++
    }
    if (nextNonEmpty >= frameTimesMs.size) return frameIndex

    val emptySpanMs = frameTimesMs[nextNonEmpty].toLong() - frameTimesMs[frameIndex]
    return if (emptySpanMs in 0..MAX_TRANSIENT_EMPTY_MASK_MS) previousNonEmpty else frameIndex
  }

  internal fun allowedContoursAt(index: Int): FloatArray =
    allowedContours.getOrElse(index) { EMPTY_CONTOURS }

  internal fun usesEvenOddFillAt(index: Int): Boolean = evenOddFills.getOrElse(index) { false }

  internal fun isProtectedAt(positionMs: Long, normalizedX: Float, normalizedY: Float): Boolean {
    val frameIndex = frameIndexAt(positionMs)
    val contours = allowedContoursAt(frameIndex)
    if (contours.isEmpty()) return false
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
        index + 1 < contours.size && !contours[index].isNaN() && !contours[index + 1].isNaN()
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
              (previousX - currentX) * (normalizedY - currentY) / (previousY - currentY) + currentX
        ) {
          crossings += 1
          winding += if (currentY > previousY) 1 else -1
        }
        previous = cursor
        cursor += 2
      }
    }
    val covered = if (usesEvenOddFillAt(frameIndex)) crossings % 2 != 0 else winding != 0
    return !covered
  }

  private companion object {
    const val MAX_TRANSIENT_EMPTY_MASK_MS = 80L
    val EMPTY_CONTOURS = FloatArray(0)
  }
}

// ── 评论 ──────────────────────────────────────────────────────────────────

data class CommentImage(
  val url: String,
  val width: Int = 0,
  val height: Int = 0,
)

data class CommentMention(
  val mid: Long,
  val name: String,
)

/** 评论正文中由 B 站标注的行内跳转节点。 */
data class CommentJumpLink(
  /** 该字段就是 content.message 中被替换的原始关键字。 */
  val key: String,
  val title: String = "",
  val prefixIconUrl: String = "",
  val pcUrl: String = "",
)

data class OfficialVerification(
  val type: Int = -1,
  val description: String = "",
) {
  val verified: Boolean
    get() = type == 0 || type == 1
}

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
  /** 评论接口 content.jump_url 返回的行内视频、专栏及网页跳转信息。 */
  val jumpLinks: List<CommentJumpLink> = emptyList(),
  val level: Int = 0,
  val vipActive: Boolean = false,
  val vipLabel: String = "",
  val officialVerification: OfficialVerification = OfficialVerification(),
  val upLiked: Boolean = false,
  val upReplied: Boolean = false,
  /** 是否为当前评论区的 UP 主置顶一级评论。 */
  val isPinned: Boolean = false,
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
  /** 每次卡片点击都生成全新 ID，包括重复进入同一条回复。 */
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
  val officialVerification: OfficialVerification = OfficialVerification(),
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

/** 季度/用户/状态响应的语义化状态，用于追番进度判定。 */
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
  val watchProgressState: BangumiWatchProgressState = BangumiWatchProgressState.UNAVAILABLE,
  val seasonType: Int = 0,
  val hasHistory: Boolean = false,
  val historicalOnly: Boolean = false,
  val historyCoverUrl: String = "",
  val lastViewedAt: Long = 0L,
  val collectionId: Long = 0L,
  val collectionType: SpaceCollectionType = SpaceCollectionType.SEASON,
  val collectionTotal: Int = 0,
)

enum class SpaceContentKind {
  COLLECTION,
  BANGUMI,
  DRAMA,
}

enum class SpaceCollectionType {
  SEASON,
  SERIES,
}

data class SpaceCollectionVideoResponse(
  val cards: List<FeedCard>,
  val hasMore: Boolean,
  val total: Int,
)

data class SpaceBangumiResponse(
  val cards: List<SpaceContentCard>,
  val hasMore: Boolean,
)

/** 限制到首页媒体分类后的一页 PGC 历史游标。 */
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

/** 网页版使用的按用途区分的 PGC 封面裁切，而不是一张通用季度封面。 */
enum class BangumiCoverVariant(internal val imageSpec: String) {
  NEW_HOT_HERO("600w_506h_!web-ogv-anime-newhot-bg.webp"),
  NEW_HOT_CARD("368w_202h_!web-ogv-anime-newhot-card.webp"),
  HORIZONTAL_CARD("560w_312h_!web-ogv-anime-horizontal-card.webp"),
  POSTER("560w_746h_!web-ogv-anime-ranking-card.webp"),
}

/** 封面来自其图片 CDN 时返回 B 站的按用途 PGC 衍生图。 */
fun bangumiCoverUrl(rawUrl: String, variant: BangumiCoverVariant): String {
  val normalized = UrlPolicy.normalizeImageUrl(rawUrl).orEmpty()
  if (normalized.isBlank() || !normalized.contains("hdslb.com/")) return normalized
  val base = normalized.substringBefore('?').substringBefore('@')
  val query = normalized.substringAfter('?', "")
  return "$base@${variant.imageSpec}" + if (query.isBlank()) "" else "?$query"
}

/** 返回原 CDN 图片，保留查询参数并去掉派生裁切后缀。 */
fun bangumiOriginalImageUrl(rawUrl: String): String {
  val normalized = UrlPolicy.normalizeImageUrl(rawUrl).orEmpty()
  if (normalized.isBlank() || !normalized.contains("hdslb.com/")) return normalized
  val base = normalized.substringBefore('?').substringBefore('@')
  val query = normalized.substringAfter('?', "")
  return base + if (query.isBlank()) "" else "?$query"
}

/** PGC 模块的语义角色。UI 不得依赖会变的服务端标题。 */
enum class BangumiExploreSectionKind {
  HOT,
  RANKING,
  TIMELINE,
  FEED,
  RECOMMENDATION,
  OTHER,
}

/**
 * /pgc/view/web/season/user/status 的聚合结果，替代更窄的 `getBangumiUserFollowed`
 * 签名，让调用方从一次响应中同时拿到关注状态与追番进度。
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
  /** 条目来源板块；用于保持各板块专属转场行为明确。 */
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

/** 网页 PGC 索引的查询参数；`-1` 沿用 B 站的"全部"语义。 */
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
  /** 纪录片/电影/电视剧用上映时间区间代替动画的 `year` 过滤。 */
  val releaseDate: String = "-1",
  /** 纪录片专属制片方过滤：尚未开放，保持 `-1`。 */
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
  val seasonType: Int = 0,
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
  val live: LiveSearchRoom? = null,
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

data class HomeDynamicUploader(
  val mid: Long,
  val name: String,
  val face: String,
  val hasUpdate: Boolean = false,
  val live: Boolean = false,
)

data class HomeDynamicUploaderResponse(
  val items: List<HomeDynamicUploader> = emptyList(),
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
  val officialVerification: OfficialVerification = OfficialVerification(),
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

data class InteractionUnreadSummary(
  val replyCount: Int = 0,
  val mentionCount: Int = 0,
  val likeCount: Int = 0,
) {
  val interactionCount: Int
    get() = replyCount + mentionCount
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
  val officialVerification: OfficialVerification = OfficialVerification(),
)

// ── 文章 / 账号历史 ────────────────────────────────────────────────────────

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

  data class Video(val card: FeedCard, override val viewAt: Long = card.pubdate) :
    AccountHistoryItem {
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

/** 历史记录标题搜索结果；网页端按页码搜索，而不是复用游标。 */
data class AccountHistorySearchResponse(
  val items: List<AccountHistoryItem>,
  val page: Int,
  val total: Int,
  val hasMore: Boolean,
)

// ── 登录 / 用户 ─────────────────────────────────────────────────────────────

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

// ── WBI 密钥 ─────────────────────────────────────────────────────────────────

data class WbiKeys(val imgKey: String, val subKey: String, val fetchedAt: Long) {
  val isValid: Boolean
    get() = (System.currentTimeMillis() - fetchedAt) < 12 * 3600_000L
}

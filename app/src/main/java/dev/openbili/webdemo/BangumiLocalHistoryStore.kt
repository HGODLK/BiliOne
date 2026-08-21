package dev.openbili.webdemo

import android.content.Context
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.SpaceContentCard
import org.json.JSONArray
import org.json.JSONObject

internal data class StoredBangumiHistory(
  val sourceId: String,
  val seasonId: Long,
  val episodeId: Long,
  val aid: Long,
  val bvid: String,
  val cid: Long,
  val title: String,
  val episodeTitle: String,
  val coverUrl: String,
  val seasonType: Int,
  val positionMs: Long,
  val durationMs: Long,
  val viewedAt: Long,
)

/** 本地 PGC 历史，在 B 站游标历史尚未追上时立即使用。 */
internal object BangumiLocalHistoryStore {
  private const val PREFERENCES = "bangumi_local_history"
  private const val KEY_ITEMS = "items"
  private const val MAX_ITEMS = 60

  @Synchronized
  fun record(
    context: Context,
    sourceCard: SpaceContentCard,
    seasonId: Long,
    episode: BangumiEpisode,
    positionMs: Long,
    durationMs: Long,
    viewedAt: Long = System.currentTimeMillis() / 1_000L,
  ) {
    if (episode.id <= 0L) return
    val resolvedSeasonId = seasonId.takeIf { it > 0L } ?: sourceCard.seasonId
    val item =
      StoredBangumiHistory(
        sourceId = sourceCard.id,
        seasonId = resolvedSeasonId,
        episodeId = episode.id,
        aid = episode.aid,
        bvid = episode.bvid,
        cid = episode.cid,
        title = sourceCard.title,
        episodeTitle = episode.longTitle.ifBlank { episode.title },
        coverUrl = episode.coverUrl.ifBlank { sourceCard.coverUrl },
        seasonType = sourceCard.seasonType,
        positionMs = positionMs.coerceAtLeast(0L),
        durationMs = maxOf(durationMs, episode.durationSeconds * 1_000L).coerceAtLeast(0L),
        viewedAt = viewedAt.coerceAtLeast(0L),
      )
    val merged =
      (listOf(item) + read(context))
        .distinctBy { historyKey(it.seasonId, it.episodeId, it.sourceId) }
        .take(MAX_ITEMS)
    val encoded =
      JSONArray().apply {
        merged.forEach { stored ->
          put(
            JSONObject()
              .put("source_id", stored.sourceId)
              .put("season_id", stored.seasonId)
              .put("episode_id", stored.episodeId)
              .put("aid", stored.aid)
              .put("bvid", stored.bvid)
              .put("cid", stored.cid)
              .put("title", stored.title)
              .put("episode_title", stored.episodeTitle)
              .put("cover", stored.coverUrl)
              .put("season_type", stored.seasonType)
              .put("position_ms", stored.positionMs)
              .put("duration_ms", stored.durationMs)
              .put("viewed_at", stored.viewedAt)
          )
        }
      }
    context
      .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_ITEMS, encoded.toString())
      .apply()
  }

  @Synchronized
  fun read(context: Context): List<StoredBangumiHistory> {
    val raw =
      context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_ITEMS, null)
        ?: return emptyList()
    val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
          val item = array.optJSONObject(index) ?: continue
          val episodeId = item.optLong("episode_id")
          if (episodeId <= 0L) continue
          add(
            StoredBangumiHistory(
              sourceId = item.optString("source_id"),
              seasonId = item.optLong("season_id"),
              episodeId = episodeId,
              aid = item.optLong("aid"),
              bvid = item.optString("bvid"),
              cid = item.optLong("cid"),
              title = item.optString("title").ifBlank { "番剧" },
              episodeTitle = item.optString("episode_title"),
              coverUrl = item.optString("cover"),
              seasonType = item.optInt("season_type"),
              positionMs = item.optLong("position_ms").coerceAtLeast(0L),
              durationMs = item.optLong("duration_ms").coerceAtLeast(0L),
              viewedAt = item.optLong("viewed_at").coerceAtLeast(0L),
            )
          )
        }
      }
      .sortedByDescending(StoredBangumiHistory::viewedAt)
      .distinctBy { historyKey(it.seasonId, it.episodeId, it.sourceId) }
      .take(MAX_ITEMS)
  }

  private fun historyKey(seasonId: Long, episodeId: Long, sourceId: String): String =
    seasonId.takeIf { it > 0L }?.let { "season:$it" }
      ?: episodeId.takeIf { it > 0L }?.let { "episode:$it" }
      ?: "source:$sourceId"
}

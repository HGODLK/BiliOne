package dev.openbili.webdemo

import android.content.Context
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.SpaceContentCard

/** Persists the last selected season/episode independently from per-video playback positions. */
internal object BangumiPlaybackStore {
  private const val PREFS_NAME = "bangumi_playback_selection"
  private const val SEASON_SUFFIX = ":season"
  private const val EPISODE_SUFFIX = ":episode"
  private const val BVID_SUFFIX = ":bvid"

  data class Selection(val seasonId: Long, val episodeId: Long, val bvid: String)

  fun save(
    context: Context,
    sourceCard: SpaceContentCard,
    seasonId: Long,
    episode: BangumiEpisode,
  ) {
    if (episode.id <= 0L) return
    val key = key(sourceCard)
    context
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putLong(key + SEASON_SUFFIX, seasonId.coerceAtLeast(0L))
      .putLong(key + EPISODE_SUFFIX, episode.id)
      .putString(key + BVID_SUFFIX, episode.bvid)
      .apply()
  }

  fun read(context: Context, sourceCard: SpaceContentCard): Selection? {
    val key = key(sourceCard)
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val episodeId = prefs.getLong(key + EPISODE_SUFFIX, 0L)
    if (episodeId <= 0L) return null
    return Selection(
      seasonId = prefs.getLong(key + SEASON_SUFFIX, sourceCard.seasonId),
      episodeId = episodeId,
      bvid = prefs.getString(key + BVID_SUFFIX, "").orEmpty(),
    )
  }

  internal fun key(sourceCard: SpaceContentCard): String =
    sourceCard.seasonId.takeIf { it > 0L }?.let { "season:$it" } ?: "card:${sourceCard.id}"
}

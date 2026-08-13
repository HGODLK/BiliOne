package dev.openbili.webdemo.offline

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class OfflineMediaStore(context: Context) {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val lock = Any()

  fun entries(): List<OfflineMediaEntry> =
    synchronized(lock) {
      val array = runCatching { JSONArray(prefs.getString(KEY_ENTRIES, "[]")) }.getOrDefault(JSONArray())
      buildList {
        for (index in 0 until array.length()) {
          runCatching { decode(array.getJSONObject(index)) }.getOrNull()?.let(::add)
        }
      }
    }

  fun entry(id: String): OfflineMediaEntry? = entries().firstOrNull { it.id == id }

  fun upsert(entry: OfflineMediaEntry) {
    synchronized(lock) {
      val next = entries().filterNot { it.id == entry.id } + entry
      write(next)
    }
  }

  fun insertIfAbsent(entry: OfflineMediaEntry): Boolean =
    synchronized(lock) {
      val current = entries()
      if (current.any { it.id == entry.id }) false
      else {
        write(current + entry)
        true
      }
    }

  fun remove(id: String) {
    synchronized(lock) { write(entries().filterNot { it.id == id }) }
  }

  var wifiOnly: Boolean
    get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
    set(value) {
      prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
    }

  var storageLocationId: String
    get() = prefs.getString(KEY_STORAGE_LOCATION_ID, INTERNAL_STORAGE_ID) ?: INTERNAL_STORAGE_ID
    set(value) {
      prefs.edit().putString(KEY_STORAGE_LOCATION_ID, value).commit()
    }

  var storageRootPath: String
    get() = prefs.getString(KEY_STORAGE_ROOT_PATH, "").orEmpty()
    set(value) {
      prefs.edit().putString(KEY_STORAGE_ROOT_PATH, value).commit()
    }

  private fun write(entries: List<OfflineMediaEntry>) {
    val array = JSONArray()
    entries.sortedByDescending(OfflineMediaEntry::createdAtMs).forEach { array.put(encode(it)) }
    prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
  }

  private fun encode(entry: OfflineMediaEntry): JSONObject =
    JSONObject()
      .put("id", entry.id)
      .put("kind", entry.kind.name)
      .put("accountMid", entry.accountMid)
      .put("title", entry.title)
      .put("partTitle", entry.partTitle)
      .put("coverUrl", entry.coverUrl)
      .put("coverRelativePath", entry.coverRelativePath)
      .put("bvid", entry.bvid)
      .put("aid", entry.aid)
      .put("cid", entry.cid)
      .put("pageNumber", entry.pageNumber)
      .put("collectionId", entry.collectionId)
      .put("seasonId", entry.seasonId)
      .put("episodeId", entry.episodeId)
      .put("durationMs", entry.durationMs)
      .put("qualityId", entry.qualityId)
      .put("qualityLabel", entry.qualityLabel)
      .put("videoUrl", entry.videoUrl)
      .put("audioUrl", entry.audioUrl)
      .put("videoCacheKey", entry.videoCacheKey)
      .put("audioCacheKey", entry.audioCacheKey)
      .put("videoMimeType", entry.videoMimeType)
      .put("audioMimeType", entry.audioMimeType)
      .put("includeDanmaku", entry.includeDanmaku)
      .put("includeSubtitles", entry.includeSubtitles)
      .put("danmakuRelativePath", entry.danmakuRelativePath)
      .put(
        "subtitles",
        JSONArray().apply {
          entry.subtitles.forEach { subtitle ->
            put(
              JSONObject()
                .put("id", subtitle.id)
                .put("label", subtitle.label)
                .put("language", subtitle.language)
                .put("relativePath", subtitle.relativePath)
            )
          }
        },
      )
      .put("requiresVip", entry.requiresVip)
      .put("entitlementState", entry.entitlementState.name)
      .put("entitlementValidUntilMs", entry.entitlementValidUntilMs)
      .put("createdAtMs", entry.createdAtMs)
      .put("preparationPaused", entry.preparationPaused)
      .put("preparationError", entry.preparationError)

  private fun decode(json: JSONObject): OfflineMediaEntry {
    val subtitleArray = json.optJSONArray("subtitles") ?: JSONArray()
    val subtitles = buildList {
      for (index in 0 until subtitleArray.length()) {
        val subtitle = subtitleArray.optJSONObject(index) ?: continue
        add(
          OfflineSubtitle(
            id = subtitle.optString("id"),
            label = subtitle.optString("label"),
            language = subtitle.optString("language"),
            relativePath = subtitle.optString("relativePath"),
          )
        )
      }
    }
    return OfflineMediaEntry(
      id = json.getString("id"),
      kind = runCatching { OfflineMediaKind.valueOf(json.optString("kind")) }.getOrDefault(OfflineMediaKind.VIDEO),
      accountMid = json.optLong("accountMid"),
      title = json.optString("title"),
      partTitle = json.optString("partTitle"),
      coverUrl = json.optString("coverUrl"),
      coverRelativePath = json.optString("coverRelativePath"),
      bvid = json.optString("bvid"),
      aid = json.optLong("aid"),
      cid = json.optLong("cid"),
      pageNumber = json.optInt("pageNumber", 1),
      collectionId = json.optLong("collectionId"),
      seasonId = json.optLong("seasonId"),
      episodeId = json.optLong("episodeId"),
      durationMs = json.optLong("durationMs"),
      qualityId = json.optInt("qualityId"),
      qualityLabel = json.optString("qualityLabel"),
      videoUrl = json.optString("videoUrl"),
      audioUrl = json.optString("audioUrl"),
      videoCacheKey = json.optString("videoCacheKey"),
      audioCacheKey = json.optString("audioCacheKey"),
      videoMimeType = json.optString("videoMimeType", "video/mp4"),
      audioMimeType = json.optString("audioMimeType", "audio/mp4"),
      includeDanmaku = json.optBoolean("includeDanmaku", true),
      includeSubtitles = json.optBoolean("includeSubtitles", true),
      danmakuRelativePath = json.optString("danmakuRelativePath"),
      subtitles = subtitles,
      requiresVip = json.optBoolean("requiresVip"),
      entitlementState =
        runCatching { OfflineEntitlementState.valueOf(json.optString("entitlementState")) }
          .getOrDefault(OfflineEntitlementState.FREE),
      entitlementValidUntilMs = json.optLong("entitlementValidUntilMs", Long.MAX_VALUE),
      createdAtMs = json.optLong("createdAtMs", System.currentTimeMillis()),
      preparationPaused = json.optBoolean("preparationPaused"),
      preparationError = json.optString("preparationError"),
    )
  }

  companion object {
    const val PREFS_NAME = "offline_media"
    const val KEY_ENTRIES = "entries_v1"
    const val KEY_WIFI_ONLY = "wifi_only"
    const val KEY_STORAGE_LOCATION_ID = "storage_location_id"
    const val KEY_STORAGE_ROOT_PATH = "storage_root_path"
    const val INTERNAL_STORAGE_ID = "internal"
  }
}

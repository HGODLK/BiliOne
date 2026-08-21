package dev.openbili.webdemo.music

import android.content.Context
import dev.openbili.webdemo.feed.FeedItem

/** 按账号和视频 ID 保存音乐页的本地显示名称，不修改服务端返回的真实标题。 */
internal class MusicDisplayNameStore(context: Context) {
  private val preferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun getForAccount(accountMid: Long): Map<String, String> {
    if (accountMid <= 0L) return emptyMap()
    val prefix = "$accountMid:"
    return preferences.all
      .asSequence()
      .filter { (key, value) -> key.startsWith(prefix) && value is String }
      .mapNotNull { (key, value) ->
        value.toString().trim().takeIf(String::isNotBlank)?.let { alias ->
          key.removePrefix(prefix) to alias
        }
      }
      .toMap()
  }

  fun set(accountMid: Long, itemId: String, alias: String) {
    if (accountMid <= 0L || itemId.isBlank()) return
    val key = "$accountMid:$itemId"
    val normalized = alias.trim()
    preferences
      .edit()
      .apply {
        if (normalized.isBlank()) remove(key) else putString(key, normalized)
      }
      .apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "home_music_display_names"
  }
}

internal fun displayTitle(item: FeedItem, displayNameOverrides: Map<String, String>): String =
  displayNameOverrides[item.id]?.takeIf(String::isNotBlank) ?: item.title

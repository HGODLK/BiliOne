package dev.openbili.webdemo.api

import android.content.SharedPreferences
import okhttp3.Cookie
import org.json.JSONArray
import org.json.JSONObject

internal data class AccountAppToken(
  val accessToken: String = "",
  val refreshToken: String = "",
  val expiresAt: Long = 0L,
)

internal data class SavedAccountSession(
  val account: SavedAccount,
  val cookies: List<Cookie>,
  val appToken: AccountAppToken,
)

/** 多账号凭据持久化，只向网络层返回当前要启用的单个账号会话。 */
internal class AccountSessionStore(private val prefs: SharedPreferences) {
  private companion object {
    const val KEY_SAVED_ACCOUNTS = "saved_accounts_v1"
    const val KEY_ACTIVE_ACCOUNT_MID = "active_account_mid"
  }

  private val sessions = linkedMapOf<Long, SavedAccountSession>()
  private var activeMid = 0L

  init {
    load()
  }

  @Synchronized fun activeAccountMid(): Long = activeMid

  @Synchronized
  fun accounts(): List<SavedAccount> =
    sessions.values
      .filter { session -> session.cookies.any { it.isUsableSessionCookie() } }
      .map(SavedAccountSession::account)
      .sortedByDescending(SavedAccount::lastUsedAt)

  @Synchronized
  fun remember(user: UserInfo, cookies: List<Cookie>, appToken: AccountAppToken) {
    val validCookies = cookies.filter { it.expiresAt >= System.currentTimeMillis() }
    if (!user.isLogin || user.mid <= 0L || validCookies.none { it.name == "SESSDATA" }) return
    activeMid = user.mid
    sessions[user.mid] =
      SavedAccountSession(
        account =
          SavedAccount(
            mid = user.mid,
            name = user.name,
            face = user.face,
            vipActive = user.vipActive,
            lastUsedAt = System.currentTimeMillis(),
          ),
        cookies = validCookies,
        appToken = appToken,
      )
    persist()
  }

  @Synchronized
  fun activate(mid: Long): SavedAccountSession? {
    val session = sessions[mid] ?: return null
    val validCookies = session.cookies.filter { it.expiresAt >= System.currentTimeMillis() }
    if (validCookies.none { it.name == "SESSDATA" }) {
      sessions.remove(mid)
      if (activeMid == mid) activeMid = 0L
      persist()
      return null
    }
    val updated =
      session.copy(
        account = session.account.copy(lastUsedAt = System.currentTimeMillis()),
        cookies = validCookies,
      )
    sessions[mid] = updated
    activeMid = mid
    persist()
    return updated
  }

  @Synchronized
  fun removeActive() {
    if (activeMid > 0L) sessions.remove(activeMid)
    activeMid = 0L
    persist()
  }

  @Synchronized
  fun updateActiveCookies(cookieMid: Long, cookies: List<Cookie>) {
    if (activeMid <= 0L || cookieMid != activeMid || cookies.none { it.name == "SESSDATA" }) return
    val current = sessions[activeMid] ?: return
    sessions[activeMid] = current.copy(cookies = cookies)
    persist()
  }

  @Synchronized
  fun updateActiveToken(cookieMid: Long, appToken: AccountAppToken) {
    if (activeMid <= 0L || cookieMid != activeMid) return
    val current = sessions[activeMid] ?: return
    sessions[activeMid] = current.copy(appToken = appToken)
    persist()
  }

  private fun load() {
    prefs
      .getStringSet(KEY_SAVED_ACCOUNTS, emptySet())
      .orEmpty()
      .mapNotNull(::decode)
      .filter { session -> session.cookies.any { it.name == "SESSDATA" } }
      .forEach { session -> sessions[session.account.mid] = session }
    activeMid = prefs.getLong(KEY_ACTIVE_ACCOUNT_MID, 0L)
    if (activeMid !in sessions) activeMid = 0L
  }

  private fun persist() {
    val encoded = sessions.values.mapTo(linkedSetOf(), ::encode)
    prefs
      .edit()
      .putStringSet(KEY_SAVED_ACCOUNTS, encoded)
      .putLong(KEY_ACTIVE_ACCOUNT_MID, activeMid)
      .apply()
  }

  private fun Cookie.isUsableSessionCookie(): Boolean =
    name == "SESSDATA" && expiresAt >= System.currentTimeMillis()

  private fun encode(session: SavedAccountSession): String =
    JSONObject()
      .put("mid", session.account.mid)
      .put("name", session.account.name)
      .put("face", session.account.face)
      .put("vipActive", session.account.vipActive)
      .put("lastUsedAt", session.account.lastUsedAt)
      .put(
        "cookies",
        JSONArray().apply { session.cookies.map(BiliHttpClient::encodeCookie).forEach(::put) },
      )
      .put("appAccessToken", session.appToken.accessToken)
      .put("appRefreshToken", session.appToken.refreshToken)
      .put("appTokenExpiresAt", session.appToken.expiresAt)
      .toString()

  private fun decode(raw: String): SavedAccountSession? =
    runCatching {
        val json = JSONObject(raw)
        val mid = json.getLong("mid")
        require(mid > 0L)
        val cookieJson = json.optJSONArray("cookies") ?: JSONArray()
        val cookies = buildList {
          for (index in 0 until cookieJson.length()) {
            BiliHttpClient.decodeCookie(cookieJson.optString(index))?.let(::add)
          }
        }
        SavedAccountSession(
          account =
            SavedAccount(
              mid = mid,
              name = json.optString("name"),
              face = json.optString("face"),
              vipActive = json.optBoolean("vipActive"),
              lastUsedAt = json.optLong("lastUsedAt"),
            ),
          cookies = cookies,
          appToken =
            AccountAppToken(
              accessToken = json.optString("appAccessToken"),
              refreshToken = json.optString("appRefreshToken"),
              expiresAt = json.optLong("appTokenExpiresAt"),
            ),
        )
      }
      .getOrNull()
}

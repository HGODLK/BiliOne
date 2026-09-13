package dev.openbili.webdemo.api

import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AccountSessionStoreTest {
  private val preferences
    get() =
      RuntimeEnvironment.getApplication()
        .getSharedPreferences("account_session_store_test", 0)

  @Before
  fun clearStore() {
    preferences.edit().clear().commit()
  }

  @Test
  fun accountsKeepIndependentCookiesAndTokensAcrossReload() {
    val store = AccountSessionStore(preferences)
    store.remember(
      user = user(mid = 100L, name = "账号甲"),
      cookies = accountCookies(mid = 100L, session = "session-a"),
      appToken = AccountAppToken("token-a", "refresh-a", FUTURE),
    )
    store.remember(
      user = user(mid = 200L, name = "账号乙"),
      cookies = accountCookies(mid = 200L, session = "session-b"),
      appToken = AccountAppToken("token-b", "refresh-b", FUTURE),
    )

    val reloaded = AccountSessionStore(preferences)
    val accountA = requireNotNull(reloaded.activate(100L))

    assertEquals("session-a", accountA.cookies.single { it.name == "SESSDATA" }.value)
    assertEquals("token-a", accountA.appToken.accessToken)
    assertEquals(100L, reloaded.activeAccountMid())
    assertTrue(reloaded.accounts().any { it.mid == 200L })

    reloaded.removeActive()
    assertEquals(listOf(200L), reloaded.accounts().map(SavedAccount::mid))
  }

  private fun user(mid: Long, name: String) =
    UserInfo(mid = mid, name = name, face = "", isLogin = true)

  private fun accountCookies(mid: Long, session: String) =
    listOf(cookie("DedeUserID", mid.toString()), cookie("SESSDATA", session))

  private fun cookie(name: String, value: String) =
    Cookie.Builder()
      .name(name)
      .value(value)
      .domain("bilibili.com")
      .path("/")
      .expiresAt(FUTURE)
      .build()

  private companion object {
    const val FUTURE = 4_000_000_000_000L
  }
}

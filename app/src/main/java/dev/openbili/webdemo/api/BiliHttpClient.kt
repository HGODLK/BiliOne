package dev.openbili.webdemo.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.annotation.SuppressLint
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object BiliHttpClient {
  private const val TAG = "BiliHttp"
  private const val PREFS_NAME = "bili_cookies"
  private const val KEY_COOKIES = "cookie_list"
  private const val KEY_COOKIES_V2 = "cookie_records_v2"
  private const val KEY_APP_ACCESS_TOKEN = "app_access_token"
  private const val KEY_APP_REFRESH_TOKEN = "app_refresh_token"
  private const val KEY_APP_TOKEN_EXPIRES_AT = "app_token_expires_at"
  private val LOGIN_COOKIE_NAMES =
    setOf(
      "SESSDATA",
      "bili_jct",
      "DedeUserID",
      "DedeUserID__ckMd5",
      "sid",
      "bili_ticket",
      "bili_ticket_expires",
      "ac_time_value",
    )

  private var cookieStore: MutableList<Cookie> = mutableListOf()
  private var cachedDesktopUa: String? = null
  @Volatile private var cachedGaiaToken: String? = null
  private var prefs: SharedPreferences? = null

  fun init(context: Context) {
    prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    loadCookies()
    cachedGaiaToken = cookieValue("x-bili-gaia-vtoken")

    if (cachedDesktopUa == null) cachedDesktopUa = FALLBACK_DESKTOP_UA
    Log.d(TAG, "desktop UA = $cachedDesktopUa")
    ensureBuvid3()
  }

  /** Resolve the WebView-provided Chrome version after the first app frame, never on startup. */
  fun refreshDesktopUserAgent(context: Context) {
    buildDesktopUa(context)?.let { cachedDesktopUa = it }
    Log.d(TAG, "refreshed desktop UA = $cachedDesktopUa")
  }

  private fun buildDesktopUa(context: Context): String? {
    return try {
      val defaultUa = WebSettings.getDefaultUserAgent(context)
      val version = Regex("""Chrome/(\d+\.\d+\.\d+\.\d+)""").find(defaultUa)?.groupValues?.get(1)
      if (version != null) {
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)" +
          " Chrome/$version Safari/537.36"
      } else null
    } catch (e: Exception) {
      Log.w(TAG, "WebSettings.getDefaultUserAgent failed; using hardcoded UA", e)
      null
    }
  }

  private const val FALLBACK_DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)" +
      " Chrome/130.0.0.0 Safari/537.36"

  private fun ensureBuvid3() {
    synchronized(cookieStore) {
      if (cookieStore.any { it.name == "buvid3" }) return
      val id = UUID.randomUUID().toString().replace("-", "").take(32).uppercase()
      val cookie =
        Cookie.Builder().name("buvid3").value(id).domain("bilibili.com").path("/").build()
      cookieStore.add(cookie)
      Log.d(TAG, "generated required device cookie")
      persistCookies()
    }
  }

  private fun persistCookies() {
    synchronized(cookieStore) {
      val now = System.currentTimeMillis()
      cookieStore.removeAll { it.expiresAt < now }
      val records = cookieStore.mapTo(linkedSetOf()) { encodeCookie(it) }
      prefs?.edit()?.putStringSet(KEY_COOKIES_V2, records)?.apply()
    }
  }

  private fun loadCookies() {
    cookieStore.clear()
    val records = prefs?.getStringSet(KEY_COOKIES_V2, emptySet()).orEmpty()
    records.mapNotNullTo(cookieStore) { decodeCookie(it) }
    // One-time compatibility with the original name-only persistence format.
    if (cookieStore.isEmpty()) {
      val names = prefs?.getStringSet(KEY_COOKIES, emptySet()).orEmpty()
      for (name in names) {
        val value = prefs?.getString("ck_$name", null) ?: continue
        val domain = prefs?.getString("ckd_$name", "bilibili.com") ?: "bilibili.com"
        val path = prefs?.getString("ckp_$name", "/") ?: "/"
        runCatching {
            Cookie.Builder().name(name).value(value).domain(domain).path(path).build()
          }
          .getOrNull()
          ?.let(cookieStore::add)
      }
      if (cookieStore.isNotEmpty()) persistCookies()
    }
    cookieStore.removeAll { it.expiresAt < System.currentTimeMillis() }
    Log.d(TAG, "loaded ${cookieStore.size} cookies from prefs")
  }

  val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .cookieJar(
        object : CookieJar {
          override fun loadForRequest(url: HttpUrl): List<Cookie> =
            synchronized(cookieStore) {
              val now = System.currentTimeMillis()
              val removed = cookieStore.removeAll { it.expiresAt < now }
              if (removed) persistCookies()
              matchingCookies(cookieStore, url, now)
            }

          override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
              for (c in cookies) {
                cookieStore.removeAll {
                  it.name == c.name && it.domain == c.domain && it.path == c.path
                }
                if (c.expiresAt >= System.currentTimeMillis()) cookieStore.add(c)
              }
            }
            persistCookies()
          }
        }
      )
      .addInterceptor { chain ->
        val original = chain.request()
        val gaiaToken = cachedGaiaToken
        val requestUrl =
          if (
            !gaiaToken.isNullOrBlank() &&
              original.url.host.endsWith("bilibili.com") &&
              original.url.queryParameter("gaia_vtoken") == null &&
              original.url.queryParameter("w_rid") == null &&
              !original.url.encodedPath.contains("/x/gaia-vgate/")
          ) {
            original.url.newBuilder().addQueryParameter("gaia_vtoken", gaiaToken).build()
          } else {
            original.url
          }
        val builder =
          original
            .newBuilder()
            .url(requestUrl)
            .header("Referer", "https://www.bilibili.com/")
            .header("Origin", "https://www.bilibili.com")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        cachedDesktopUa?.let { builder.header("User-Agent", it) }
        chain.proceed(builder.build()).also(RiskControlManager::inspectResponse)
      }
      .build()
  }

  /** Public, credential-free client for cacheable resources such as danmaku XML. */
  private val publicClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .cookieJar(CookieJar.NO_COOKIES)
      .addInterceptor { chain ->
        val builder =
          chain
            .request()
            .newBuilder()
            .header("Referer", "https://www.bilibili.com/")
            .header("Accept", "text/xml, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        cachedDesktopUa?.let { builder.header("User-Agent", it) }
        chain.proceed(builder.build())
      }
      .build()
  }

  fun replaceCookies(cookies: List<Cookie>) {
    synchronized(cookieStore) {
      cookieStore.clear()
      cookieStore.addAll(cookies)
    }
    persistCookies()
    cachedGaiaToken = cookieValue("x-bili-gaia-vtoken")
  }

  val gaiaToken: String?
    get() = cachedGaiaToken

  fun setGaiaToken(token: String) {
    if (token.isBlank()) return
    val cookie =
      Cookie.Builder()
        .name("x-bili-gaia-vtoken")
        .value(token)
        .domain("bilibili.com")
        .path("/")
        .secure()
        .build()
    synchronized(cookieStore) {
      cookieStore.removeAll { it.name == cookie.name }
      cookieStore.add(cookie)
    }
    cachedGaiaToken = token
    persistCookies()
  }

  /** Clears account credentials while retaining anonymous device cookies used by public APIs. */
  fun clearLoginSession() {
    synchronized(cookieStore) { cookieStore.removeAll { it.name in LOGIN_COOKIE_NAMES } }
    clearAppAccessToken()
    persistCookies()
    runCatching {
      val manager = CookieManager.getInstance()
      LOGIN_COOKIE_NAMES.forEach { name ->
        manager.setCookie(
          "https://bilibili.com/",
          "$name=; Max-Age=0; Path=/; Domain=.bilibili.com; Secure",
        )
      }
      manager.flush()
    }
  }

  fun get(url: String, headers: Map<String, String> = emptyMap()): okhttp3.Response {
    val request =
      Request.Builder()
        .url(url)
        .get()
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()
    return client.newCall(request).execute()
  }

  fun getPublic(url: String, headers: Map<String, String> = emptyMap()): okhttp3.Response {
    val request =
      Request.Builder()
        .url(url)
        .get()
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()
    return publicClient.newCall(request).execute()
  }

  fun postForm(url: String, fields: Map<String, String>): okhttp3.Response {
    val body =
      FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build()
    return client.newCall(Request.Builder().url(url).post(body).build()).execute()
  }

  fun postMultipart(
    url: String,
    fields: Map<String, String>,
    fileField: String,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
  ): okhttp3.Response {
    val body =
      MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .apply {
          fields.forEach { (name, value) -> addFormDataPart(name, value) }
          addFormDataPart(fileField, fileName, bytes.toRequestBody(mimeType.toMediaType()))
        }
        .build()
    return client.newCall(Request.Builder().url(url).post(body).build()).execute()
  }

  fun postJson(url: String, json: String): okhttp3.Response {
    val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
    return client.newCall(Request.Builder().url(url).post(body).build()).execute()
  }

  /** Returns a cookie value for authenticated API calls without exposing it to logs. */
  fun cookieValue(name: String): String? =
    synchronized(cookieStore) {
      cookieStore
        .filter { it.expiresAt >= System.currentTimeMillis() && it.name == name }
        .maxByOrNull { it.expiresAt }
        ?.value
    }

  fun saveAppAccessToken(accessToken: String, refreshToken: String, expiresInSeconds: Long) {
    if (accessToken.isBlank()) return
    val expiresAt = System.currentTimeMillis() + expiresInSeconds.coerceAtLeast(60L) * 1_000L
    prefs
      ?.edit()
      ?.putString(KEY_APP_ACCESS_TOKEN, accessToken)
      ?.putString(KEY_APP_REFRESH_TOKEN, refreshToken)
      ?.putLong(KEY_APP_TOKEN_EXPIRES_AT, expiresAt)
      ?.apply()
  }

  fun appAccessToken(): String? {
    val expiresAt = prefs?.getLong(KEY_APP_TOKEN_EXPIRES_AT, 0L) ?: 0L
    if (expiresAt <= System.currentTimeMillis()) {
      clearAppAccessToken()
      return null
    }
    return prefs?.getString(KEY_APP_ACCESS_TOKEN, null)?.takeIf(String::isNotBlank)
  }

  fun hasValidAppAccessToken(): Boolean = appAccessToken() != null

  private fun clearAppAccessToken() {
    prefs
      ?.edit()
      ?.remove(KEY_APP_ACCESS_TOKEN)
      ?.remove(KEY_APP_REFRESH_TOKEN)
      ?.remove(KEY_APP_TOKEN_EXPIRES_AT)
      ?.apply()
  }

  /** Copies the persisted login session into the system WebView cookie jar for official pages. */
  fun syncCookiesToWebView() {
    val manager = CookieManager.getInstance()
    manager.setAcceptCookie(true)
    synchronized(cookieStore) {
      cookieStore
        .filter { it.expiresAt >= System.currentTimeMillis() }
        .forEach { cookie ->
          val attributes = buildString {
            append("${cookie.name}=${cookie.value}; Path=${cookie.path}; Domain=${cookie.domain}")
            if (cookie.secure) append("; Secure")
          }
          manager.setCookie("https://${cookie.domain.trimStart('.')}/", attributes)
        }
    }
    manager.flush()
  }

  internal fun matchingCookies(
    cookies: List<Cookie>,
    url: HttpUrl,
    now: Long = System.currentTimeMillis(),
  ): List<Cookie> = cookies.filter { it.expiresAt >= now && it.matches(url) }

  @SuppressLint("NewApi") // java.util.Base64 is supplied on API 24/25 by core library desugaring.
  internal fun encodeCookie(cookie: Cookie): String =
    Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(
        listOf(
            cookie.name,
            cookie.value,
            cookie.expiresAt.toString(),
            cookie.domain,
            cookie.path,
            cookie.secure.toString(),
            cookie.httpOnly.toString(),
            cookie.hostOnly.toString(),
          )
          .joinToString("\u001f")
          .toByteArray(Charsets.UTF_8)
      )

  @SuppressLint("NewApi") // java.util.Base64 is supplied on API 24/25 by core library desugaring.
  internal fun decodeCookie(raw: String): Cookie? =
    runCatching {
        val fields = String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8).split("\u001f")
        require(fields.size == 8)
        val builder =
          Cookie.Builder()
            .name(fields[0])
            .value(fields[1])
            .expiresAt(fields[2].toLong())
            .path(fields[4])
        if (fields[7].toBoolean()) builder.hostOnlyDomain(fields[3]) else builder.domain(fields[3])
        if (fields[5].toBoolean()) builder.secure()
        if (fields[6].toBoolean()) builder.httpOnly()
        builder.build()
      }
      .getOrNull()
      ?.takeIf { it.expiresAt >= System.currentTimeMillis() }
}

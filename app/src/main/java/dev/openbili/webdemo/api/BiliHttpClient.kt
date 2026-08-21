package dev.openbili.webdemo.api

/**
 * B 站 Web API HTTP 客户端：承载 [BiliHttpClient]。
 *
 * 统一管理 OkHttp 客户端、登录 Cookie 与设备指纹 Cookie 的持久化、桌面 UA、风控通行令牌
 * （gaia token）以及 APP access token；所有 B 站接口请求都经由此处发出。
 */

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
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
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * B 站 HTTP 客户端与 Cookie/令牌统一管理。
 *
 * 提供 GET/POST（表单、JSON、multipart）请求封装，自动附带桌面 UA、Referer、Origin 与风控
 * 通行令牌；负责登录 Cookie 的持久化与 WebView 同步，以及扫码授权所用的 APP access token 存取。
 */
object BiliHttpClient {
  private const val TAG = "BiliHttp"
  private const val PREFS_NAME = "bili_cookies"
  private const val KEY_COOKIES = "cookie_list"
  private const val KEY_COOKIES_V2 = "cookie_records_v2"
  private const val KEY_APP_ACCESS_TOKEN = "app_access_token"
  private const val KEY_APP_REFRESH_TOKEN = "app_refresh_token"
  private const val KEY_APP_TOKEN_EXPIRES_AT = "app_token_expires_at"
  // 登录态相关 Cookie 名：退出登录时清除，匿名设备 Cookie（如 buvid3）保留。
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

  /**
   * 初始化客户端：读取持久化的 Cookie、恢复风控令牌与桌面 UA，并确保设备指纹 buvid3 存在。
   *
   * @param context 用于读取 SharedPreferences 与 WebView 的上下文。
   */
  fun init(context: Context) {
    prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    loadCookies()
    cachedGaiaToken = cookieValue("x-bili-gaia-vtoken")

    if (cachedDesktopUa == null) cachedDesktopUa = FALLBACK_DESKTOP_UA
    Log.d(TAG, "desktop UA = $cachedDesktopUa")
    ensureBuvid3()
  }

  /** 首个应用帧之后解析 WebView 提供的 Chrome 版本，绝不在启动阶段执行。 */
  fun refreshDesktopUserAgent(context: Context) {
    buildDesktopUa(context)?.let { cachedDesktopUa = it }
    Log.d(TAG, "refreshed desktop UA = $cachedDesktopUa")
  }

  /**
   * 从 WebView 的默认 UA 中解析 Chrome 版本，拼出桌面版 UA。
   *
   * @return 桌面 UA 字符串，解析失败时返回 null。
   */
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

  // 兜底桌面 UA：WebView 版本解析失败时使用固定 Chrome 版本。
  private const val FALLBACK_DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)" +
      " Chrome/130.0.0.0 Safari/537.36"

  /** 确保存在设备指纹 buvid3 Cookie，不存在则随机生成并持久化。 */
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

  /** 把当前 Cookie 序列化写入 SharedPreferences（先剔除已过期的）。 */
  private fun persistCookies() {
    synchronized(cookieStore) {
      val now = System.currentTimeMillis()
      cookieStore.removeAll { it.expiresAt < now }
      val records = cookieStore.mapTo(linkedSetOf()) { encodeCookie(it) }
      prefs?.edit()?.putStringSet(KEY_COOKIES_V2, records)?.apply()
    }
  }

  /** 从 SharedPreferences 读取并恢复 Cookie 列表。 */
  private fun loadCookies() {
    cookieStore.clear()
    val records = prefs?.getStringSet(KEY_COOKIES_V2, emptySet()).orEmpty()
    records.mapNotNullTo(cookieStore) { decodeCookie(it) }
    // 对旧版仅按名称持久化的格式做一次性兼容迁移。
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

  /**
   * 主 OkHttp 客户端（带 Cookie 与鉴权）。
   *
   * 负责连接/读取超时、Cookie 存取、自动附带语言/Referer/Origin/UA 请求头，并注入风控通行
   * 令牌 gaia_vtoken；每次响应都会交给 [RiskControlManager.inspectResponse] 检查是否触发风控。
   */
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
          original.newBuilder().url(requestUrl).header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        if (original.header("Accept") == null) {
          builder.header("Accept", "application/json, text/plain, */*")
        }
        if (original.header("Referer") == null) {
          builder.header("Referer", "https://www.bilibili.com/")
        }
        if (original.header("Origin") == null) {
          builder.header("Origin", "https://www.bilibili.com")
        }
        cachedDesktopUa?.let { builder.header("User-Agent", it) }
        chain.proceed(builder.build()).also(RiskControlManager::inspectResponse)
      }
      .build()
  }

  /** 公开、不带凭据的客户端，用于弹幕 XML 等可缓存资源。 */
  private val publicClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .cookieJar(CookieJar.NO_COOKIES)
      .addInterceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder().header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        if (original.header("Referer") == null) {
          builder.header("Referer", "https://www.bilibili.com/")
        }
        if (original.header("Accept") == null) {
          builder.header("Accept", "text/xml, */*")
        }
        cachedDesktopUa?.let { builder.header("User-Agent", it) }
        chain.proceed(builder.build())
      }
      .build()
  }

  /**
   * 用给定的 Cookie 列表整体替换当前存储。
   *
   * @param cookies 新的 Cookie 列表。
   */
  fun replaceCookies(cookies: List<Cookie>) {
    synchronized(cookieStore) {
      cookieStore.clear()
      cookieStore.addAll(cookies)
    }
    persistCookies()
    cachedGaiaToken = cookieValue("x-bili-gaia-vtoken")
  }

  /** 当前风控通行令牌（gaia token），可能为空。 */
  val gaiaToken: String?
    get() = cachedGaiaToken

  /**
   * 保存风控通行令牌并写入 Cookie（x-bili-gaia-vtoken）。
   *
   * @param token 风控验证通过后返回的 grisk_id。
   */
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

  /** 清除账号登录态，但保留公开接口使用的匿名设备 Cookie。 */
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

  /**
   * 发送 GET 请求（带登录凭据的主客户端）。
   *
   * @param url 目标 URL。
   * @param headers 附加请求头。
   * @return OkHttp 响应（调用方负责关闭）。
   */
  fun get(url: String, headers: Map<String, String> = emptyMap()): okhttp3.Response {
    val request =
      Request.Builder()
        .url(url)
        .get()
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()
    return client.newCall(request).execute()
  }

  /**
   * 发送 GET 请求（公开客户端，不带 Cookie）。
   *
   * @param url 目标 URL。
   * @param headers 附加请求头。
   * @return OkHttp 响应（调用方负责关闭）。
   */
  fun getPublic(url: String, headers: Map<String, String> = emptyMap()): okhttp3.Response {
    val request =
      Request.Builder()
        .url(url)
        .get()
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()
    return publicClient.newCall(request).execute()
  }

  /**
   * 发送表单编码的 POST 请求。
   *
   * @param url 目标 URL。
   * @param fields 表单字段。
   * @param headers 附加请求头。
   * @return OkHttp 响应（调用方负责关闭）。
   */
  fun postForm(
    url: String,
    fields: Map<String, String>,
    headers: Map<String, String> = emptyMap(),
  ): okhttp3.Response {
    val body =
      FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build()
    val request =
      Request.Builder()
        .url(url)
        .post(body)
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()
    return client.newCall(request).execute()
  }

  /**
   * 发送 multipart/form-data 的 POST 请求（用于上传文件）。
   *
   * @param url 目标 URL。
   * @param fields 表单字段。
   * @param fileField 文件字段名。
   * @param fileName 文件名。
   * @param mimeType 文件 MIME 类型。
   * @param bytes 文件内容字节。
   * @return OkHttp 响应（调用方负责关闭）。
   */
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

  /**
   * 发送 JSON 体的 POST 请求。
   *
   * @param url 目标 URL。
   * @param json JSON 字符串。
   * @return OkHttp 响应（调用方负责关闭）。
   */
  fun postJson(url: String, json: String): okhttp3.Response {
    val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
    return client.newCall(Request.Builder().url(url).post(body).build()).execute()
  }

  /** 返回鉴权接口所用的 Cookie 值，且不暴露到日志中。 */
  fun cookieValue(name: String): String? =
    synchronized(cookieStore) {
      cookieStore
        .filter { it.expiresAt >= System.currentTimeMillis() && it.name == name }
        .maxByOrNull { it.expiresAt }
        ?.value
    }

  /**
   * 保存扫码授权得到的 APP access token 与刷新 token。
   *
   * @param accessToken 访问令牌。
   * @param refreshToken 刷新令牌。
   * @param expiresInSeconds 有效期（秒）。
   */
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

  /**
   * 读取仍在有效期内的 APP access token。
   *
   * @return 有效的 access token，过期或不存在时返回 null（并清理过期记录）。
   */
  fun appAccessToken(): String? {
    val expiresAt = prefs?.getLong(KEY_APP_TOKEN_EXPIRES_AT, 0L) ?: 0L
    if (expiresAt <= System.currentTimeMillis()) {
      clearAppAccessToken()
      return null
    }
    return prefs?.getString(KEY_APP_ACCESS_TOKEN, null)?.takeIf(String::isNotBlank)
  }

  /** 是否存在仍在有效期内的 APP access token。 */
  fun hasValidAppAccessToken(): Boolean = appAccessToken() != null

  /** 清除持久化的 APP access token。 */
  private fun clearAppAccessToken() {
    prefs
      ?.edit()
      ?.remove(KEY_APP_ACCESS_TOKEN)
      ?.remove(KEY_APP_REFRESH_TOKEN)
      ?.remove(KEY_APP_TOKEN_EXPIRES_AT)
      ?.apply()
  }

  /** 把持久化的登录会话复制到系统 WebView 的 Cookie 罐，供官方页面使用。 */
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

  /**
   * 过滤出匹配指定 URL 且未过期的 Cookie。
   *
   * @param cookies 待过滤的 Cookie 列表。
   * @param url 目标 URL。
   * @param now 当前时间戳。
   * @return 匹配且有效的 Cookie 列表。
   */
  internal fun matchingCookies(
    cookies: List<Cookie>,
    url: HttpUrl,
    now: Long = System.currentTimeMillis(),
  ): List<Cookie> = cookies.filter { it.expiresAt >= now && it.matches(url) }

  /** 把 Cookie 序列化为 base64Url 字符串用于持久化。 */
  internal fun encodeCookie(cookie: Cookie): String =
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
      .toByteString()
      .base64Url()
      .trimEnd('=')

  /** 从 base64Url 字符串反序列化 Cookie，失败或已过期时返回 null。 */
  internal fun decodeCookie(raw: String): Cookie? =
    runCatching {
        val fields =
          String(raw.decodeBase64()?.toByteArray() ?: return@runCatching null, Charsets.UTF_8)
            .split("\u001f")
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

package dev.openbili.webdemo.live

/**
 * 直播弹幕 WebSocket 客户端。
 *
 * 通过 B 站直播弹幕的长连接协议（WebSocket + 自研二进制包格式）接收房间事件：认证、
 * 普通弹幕、系统提示、人气值、开播/下播、播放地址刷新、互动抽奖等。[LiveDanmakuClient]
 * 负责建连、心跳与重连；收到字节流后交给 [LivePacketParser] 解析为 [LiveSocketEvent] 回调。
 */

import dev.openbili.webdemo.api.BiliHttpClient
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.Inflater
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject

/**
 * 直播弹幕长连接上报给上层的事件（结果）。
 *
 * 覆盖连接认证、聊天消息、人气/关注、房间状态、播放地址刷新与互动抽奖等场景，
 * 由 [LivePacketParser] 解析二进制报文后产生。
 */
internal sealed interface LiveSocketEvent {
  /** 弹幕连接已通过鉴权，可开始接收房间事件。 */
  data object Authenticated : LiveSocketEvent

  /** 一条聊天消息（普通弹幕、礼物、醒目留言或系统提示的统一载体）。 */
  data class Message(val value: LiveChatMessage) : LiveSocketEvent

  /** 「X 人正在看」的人气/观看数文案。 */
  data class Watched(val text: String) : LiveSocketEvent

  /** 房间在线人气值。 */
  data class Online(val value: Long) : LiveSocketEvent

  /** 直播间标题发生变化。 */
  data class RoomChanged(val title: String?) : LiveSocketEvent

  /** 开播/下播状态：true 表示开播，false 表示下播（准备中）。 */
  data class LiveStatus(val living: Boolean) : LiveSocketEvent

  /** 播放地址需要刷新（服务端下发 PLAYURL_RELOAD）。 */
  data object PlayUrlReload : LiveSocketEvent

  /** 互动抽奖开始。 */
  data class LotteryStarted(val lottery: LiveInteractiveLottery) : LiveSocketEvent

  /** 互动抽奖结束。 */
  data class LotteryEnded(val id: Long) : LiveSocketEvent

  /** 互动抽奖开出结果（中奖者与奖品）。 */
  data class LotteryAwarded(
    val id: Long,
    val awardName: String,
    val awardImageUrl: String?,
    val winners: List<LiveLotteryWinner>,
  ) : LiveSocketEvent

  /** 互动抽奖作废（如因违规被驳回）。 */
  data class LotteryInvalidated(val id: Long, val reason: String?) : LiveSocketEvent
}

/**
 * 直播弹幕 WebSocket 客户端。
 *
 * 负责建立与直播弹幕服务器的长连接、发送鉴权包、维持心跳，并在断开后按退避策略重连。
 * 收到的事件通过 [onEvent] 回调，连接状态变化通过 [onState] 回调。
 *
 * @param roomId 直播间房间号。
 * @param accountUid 当前账号 UID（用于鉴权包）。
 * @param config 弹幕服务器配置（端点列表与鉴权 token）。
 * @param onState 连接状态变化回调，附带可选的描述信息。
 * @param onEvent 解析出的事件回调。
 */
internal class LiveDanmakuClient(
  private val roomId: Long,
  private val accountUid: Long,
  private val config: LiveDanmuConfig,
  private val onState: (LiveConnectionState, String?) -> Unit,
  private val onEvent: (LiveSocketEvent) -> Unit,
) {
  /** 专用于心跳与重连延迟的 IO 协程作用域，随客户端生命周期管理。 */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  /** 是否已永久停止（stop() 之后不再重连）。 */
  private val stopped = AtomicBoolean(false)
  /** 当前 WebSocket 连接，可能为 null。 */
  private var socket: WebSocket? = null
  /** 心跳发送协程。 */
  private var heartbeatJob: Job? = null
  /** 当前使用的端点下标，重连时轮换以规避单点故障。 */
  private var endpointIndex = 0
  /** 已把全部端点轮询过的次数，用于退避档位。 */
  private var retryAttempt = 0

  /** 启动连接；已停止时直接返回。 */
  fun start() {
    if (stopped.get()) return
    onState(LiveConnectionState.CONNECTING, null)
    connect()
  }

  /**
   * 停止并关闭连接。
   *
   * 使用 CAS 保证幂等：并发或重复调用只会生效一次。关闭心跳、WebSocket 与作用域后
   * 上报 DISCONNECTED。
   */
  fun stop() {
    if (!stopped.compareAndSet(false, true)) return
    heartbeatJob?.cancel()
    heartbeatJob = null
    socket?.close(1000, "leave room")
    socket = null
    scope.coroutineContext[Job]?.cancel()
    onState(LiveConnectionState.DISCONNECTED, null)
  }

  /** 建立 WebSocket 连接；onOpen 后发送鉴权包并启动心跳。 */
  private fun connect() {
    if (stopped.get() || config.endpoints.isEmpty()) return
    // 轮换选择端点，避免始终命中同一个已故障的服务器。
    val endpoint = config.endpoints[endpointIndex.mod(config.endpoints.size)]
    val request =
      Request.Builder()
        .url("wss://${endpoint.host}:${endpoint.wssPort}/sub")
        .header("Origin", "https://live.bilibili.com")
        .header("Referer", "https://live.bilibili.com/$roomId")
        .build()
    socket =
      BiliHttpClient.client.newWebSocket(
        request,
        object : WebSocketListener() {
          override fun onOpen(webSocket: WebSocket, response: Response) {
            if (stopped.get()) {
              webSocket.close(1000, "stopped")
              return
            }
            socket = webSocket
            webSocket.send(authPacket().toByteString())
            startHeartbeat(webSocket)
          }

          override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (!stopped.get()) {
              LivePacketParser.parse(bytes.toByteArray(), onEvent)
            }
          }

          override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!stopped.get()) scheduleReconnect(t.message)
          }

          override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!stopped.get()) scheduleReconnect(reason.takeIf(String::isNotBlank))
          }
        },
      )
  }

  private fun startHeartbeat(webSocket: WebSocket) {
    heartbeatJob?.cancel()
    heartbeatJob = scope.launch {
      while (isActive && !stopped.get()) {
        webSocket.send(packet(operation = OP_HEARTBEAT, body = "[object Object]").toByteString())
        delay(30_000L)
      }
    }
  }

  private fun scheduleReconnect(reason: String?) {
    heartbeatJob?.cancel()
    heartbeatJob = null
    socket = null
    endpointIndex++
    val allEndpointsTried = endpointIndex % config.endpoints.size == 0
    if (allEndpointsTried) retryAttempt++
    val backoffSeconds = intArrayOf(1, 2, 4, 8, 15)[min(retryAttempt, 4)]
    onState(LiveConnectionState.RETRYING, reason?.take(120))
    scope.launch {
      delay(if (allEndpointsTried) backoffSeconds * 1_000L else 250L)
      if (!stopped.get()) connect()
    }
  }

  private fun authPacket(): ByteArray {
    val body =
      JSONObject()
        .put("uid", accountUid.coerceAtLeast(0L))
        .put("roomid", roomId)
        .put("protover", 2)
        .put("buvid", BiliHttpClient.cookieValue("buvid3").orEmpty())
        .put("platform", "web")
        .put("type", 2)
        .put("key", config.token)
        .toString()
    return packet(operation = OP_AUTH, body = body)
  }

  private fun packet(operation: Int, body: String): ByteArray {
    val bytes = body.toByteArray(Charsets.UTF_8)
    return ByteBuffer.allocate(HEADER_LENGTH + bytes.size)
      .order(ByteOrder.BIG_ENDIAN)
      .putInt(HEADER_LENGTH + bytes.size)
      .putShort(HEADER_LENGTH.toShort())
      .putShort(1)
      .putInt(operation)
      .putInt(1)
      .put(bytes)
      .array()
  }

  private companion object {
    const val HEADER_LENGTH = 16
    const val OP_HEARTBEAT = 2
    const val OP_AUTH = 7
  }
}

private object LivePacketParser {
  private const val HEADER_LENGTH = 16
  private const val MAX_PACKET_BYTES = 8 * 1024 * 1024
  private const val MAX_DEPTH = 4

  fun parse(bytes: ByteArray, onEvent: (LiveSocketEvent) -> Unit) {
    if (bytes.isEmpty() || bytes.size > MAX_PACKET_BYTES) return
    runCatching { parsePackets(bytes, 0, onEvent) }
  }

  private fun parsePackets(
    bytes: ByteArray,
    depth: Int,
    onEvent: (LiveSocketEvent) -> Unit,
  ) {
    if (depth > MAX_DEPTH) return
    var offset = 0
    while (offset + HEADER_LENGTH <= bytes.size) {
      val header = ByteBuffer.wrap(bytes, offset, HEADER_LENGTH).order(ByteOrder.BIG_ENDIAN)
      val packetLength = header.int
      val headerLength = header.short.toInt() and 0xffff
      val version = header.short.toInt() and 0xffff
      val operation = header.int
      header.int
      if (
        packetLength < headerLength ||
          headerLength < HEADER_LENGTH ||
          packetLength > MAX_PACKET_BYTES ||
          offset + packetLength > bytes.size
      ) {
        return
      }
      val body = bytes.copyOfRange(offset + headerLength, offset + packetLength)
      when {
        version == 2 -> inflate(body)?.let { parsePackets(it, depth + 1, onEvent) }
        operation == 3 && body.size >= 4 -> {
          val value = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL
          onEvent(LiveSocketEvent.Online(value))
        }
        operation == 5 -> parseBusinessMessages(body, onEvent)
        operation == 8 -> {
          val auth = body.decodeToString().asJsonObject()
          if (auth?.optInt("code", -1) == 0) onEvent(LiveSocketEvent.Authenticated)
        }
      }
      offset += packetLength
    }
  }

  private fun parseBusinessMessages(
    body: ByteArray,
    onEvent: (LiveSocketEvent) -> Unit,
  ) {
    val text = body.decodeToString().trim()
    if (text.isBlank()) return
    splitJsonObjects(text).forEach { raw ->
      val json = raw.asJsonObject() ?: return@forEach
      val command = json.optString("cmd").substringBefore(':')
      val data = json.optJSONObject("data")
      when (command) {
        "DANMU_MSG" -> parseDanmaku(json)?.let { onEvent(LiveSocketEvent.Message(it)) }
        "WATCHED_CHANGE" -> {
          val watched =
            data?.optString("text_large")?.ifBlank { data.optString("text_small") }.orEmpty()
          if (watched.isNotBlank()) onEvent(LiveSocketEvent.Watched(watched))
        }
        "ROOM_CHANGE" -> onEvent(LiveSocketEvent.RoomChanged(data?.optString("title")))
        "LIVE" -> onEvent(LiveSocketEvent.LiveStatus(true))
        "PREPARING" -> onEvent(LiveSocketEvent.LiveStatus(false))
        "PLAYURL_RELOAD" -> onEvent(LiveSocketEvent.PlayUrlReload)
        "ANCHOR_LOT_START" ->
          data?.let(BiliLiveApi::parseInteractiveLottery)?.let {
            onEvent(LiveSocketEvent.LotteryStarted(it))
          }
        "ANCHOR_LOT_END" -> {
          val id = data?.optLong("id") ?: 0L
          if (id > 0L) onEvent(LiveSocketEvent.LotteryEnded(id))
        }
        "ANCHOR_LOT_AWARD" -> {
          val id = data?.optLong("id") ?: 0L
          if (id > 0L) {
            onEvent(
              LiveSocketEvent.LotteryAwarded(
                id = id,
                awardName = data?.optString("award_name").orEmpty(),
                awardImageUrl =
                  dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(
                    data?.optString("award_image").orEmpty()
                  ),
                winners = parseLotteryWinners(data?.optJSONArray("award_users")),
              )
            )
          }
        }
        "ANCHOR_LOT_CHECKSTATUS" -> {
          val id = data?.optLong("id") ?: 0L
          if (id > 0L && (data?.optInt("status") ?: 0) >= 4) {
            onEvent(
              LiveSocketEvent.LotteryInvalidated(
                id = id,
                reason = data?.optString("reject_reason")?.takeIf(String::isNotBlank),
              )
            )
          }
        }
        "SEND_GIFT" -> {
          val uname = data?.optString("uname").orEmpty()
          val giftName = data?.optString("giftName").orEmpty()
          val count = data?.optInt("num", 1) ?: 1
          if (giftName.isNotBlank()) {
            onEvent(
              LiveSocketEvent.Message(systemMessage("$uname 赠送了 $giftName ×$count", command, data))
            )
          }
        }
        "GUARD_BUY" -> {
          val uname = data?.optString("username").orEmpty()
          val count = data?.optInt("num", 1) ?: 1
          onEvent(LiveSocketEvent.Message(systemMessage("$uname 开通了大航海 ×$count", command, data)))
        }
        "SUPER_CHAT_MESSAGE" ->
          parseSuperChat(data)?.let {
            onEvent(LiveSocketEvent.Message(it))
          }
      }
    }
  }

  private fun parseDanmaku(json: JSONObject): LiveChatMessage? {
    val info = json.optJSONArray("info") ?: return null
    val metadata = info.optJSONArray(0)
    val text = info.optString(1)
    val user = info.optJSONArray(2)
    val medal = parseArrayMedal(info.optJSONArray(3))
    val extraHolder = metadata?.optJSONObject(15)
    val extra = extraHolder?.optString("extra")?.asJsonObject() ?: extraHolder
    val uid = user?.optLong(0)?.takeIf { it > 0L }
    val uname = user?.optString(1)?.takeIf(String::isNotBlank)
    val face =
      extra
        ?.optJSONObject("user")
        ?.optJSONObject("base")
        ?.optString("face")
        ?.takeIf(String::isNotBlank)
        ?.let(dev.openbili.webdemo.UrlPolicy::normalizeImageUrl)
    val emoteMetadata = metadata?.optJSONObject(13)
    val fileId =
      emoteMetadata?.optString("emoticon_unique")?.takeIf(String::isNotBlank)
        ?: extra?.optString("emoticon_unique")?.takeIf(String::isNotBlank)
    val isBulge =
      emoteMetadata?.optInt("bulge_display", 0) != 0 || extra?.optInt("bulge_display", 0) != 0
    val imageUrl =
      listOfNotNull(
          emoteMetadata?.optString("url"),
          emoteMetadata?.optString("gif"),
          extra?.optString("url"),
        )
        .firstOrNull(String::isNotBlank)
        ?.let(dev.openbili.webdemo.UrlPolicy::normalizeImageUrl)
    val inlineEmotes =
      parseEmoteUrls(extra?.optJSONObject("emots"), text).toMutableMap().apply {
        if (!isBulge && fileId != null && !imageUrl.isNullOrBlank() && text.contains(fileId)) {
          putIfAbsent(fileId, imageUrl)
        }
      }
    val content =
      if (isBulge && (fileId != null || imageUrl != null)) {
        LiveChatContent.Emoji(
          displayName = text.ifBlank { fileId.orEmpty() },
          fileId = fileId,
          imageUrl = imageUrl,
          isBulge = true,
        )
      } else {
        LiveChatContent.Text(text, inlineEmotes)
      }
    val timestamp =
      metadata?.optLong(4)?.takeIf { it > 10_000_000_000L } ?: System.currentTimeMillis()
    val id =
      extra?.optString("id_str")?.takeIf(String::isNotBlank)
        ?: "danmu:${uid ?: 0}:$timestamp:${(fileId ?: text).hashCode()}"
    return LiveChatMessage(
      stableId = id,
      uid = uid,
      uname = uname,
      faceUrl = face,
      content = content,
      fanMedal = medal,
      receivedAtMs = timestamp,
    )
  }

  private fun parseSuperChat(data: JSONObject?): LiveChatMessage? {
    data ?: return null
    val userInfo = data.optJSONObject("user_info")
    val uid = data.optLong("uid").takeIf { it > 0L }
    val text = data.optString("message")
    if (text.isBlank()) return null
    return LiveChatMessage(
      stableId =
        data.optString("id_str").ifBlank {
          "super:${uid ?: 0}:${data.optLong("ts")}:${text.hashCode()}"
        },
      uid = uid,
      uname = userInfo?.optString("uname")?.takeIf(String::isNotBlank),
      faceUrl =
        userInfo
          ?.optString("face")
          ?.takeIf(String::isNotBlank)
          ?.let(dev.openbili.webdemo.UrlPolicy::normalizeImageUrl),
      content = LiveChatContent.Text(text),
      fanMedal = BiliLiveApi.parseMedal(data.optJSONObject("medal_info")),
      receivedAtMs =
        data.optLong("ts").takeIf { it > 0L }?.times(1_000L) ?: System.currentTimeMillis(),
    )
  }

  private fun systemMessage(
    text: String,
    command: String,
    data: JSONObject?,
  ): LiveChatMessage =
    LiveChatMessage(
      stableId =
        "$command:${data?.optLong("timestamp") ?: System.currentTimeMillis()}:${text.hashCode()}",
      uid = null,
      uname = null,
      faceUrl = null,
      content = LiveChatContent.System(text),
      fanMedal = null,
      receivedAtMs = System.currentTimeMillis(),
    )

  private fun parseArrayMedal(value: JSONArray?): FanMedalBadge? {
    value ?: return null
    val level = value.optInt(0)
    val name = value.optString(1)
    if (level <= 0 || name.isBlank()) return null
    return FanMedalBadge(
      name = name,
      level = level,
      anchorUid = value.optLong(12).takeIf { it > 0L },
      color = value.optLong(4).takeIf { it > 0L },
      borderColor = value.optLong(7).takeIf { it > 0L },
      startColor = value.optLong(8).takeIf { it > 0L },
      endColor = value.optLong(9).takeIf { it > 0L },
    )
  }

  private fun parseEmoteUrls(emotes: JSONObject?, message: String): Map<String, String> {
    emotes ?: return emptyMap()
    return buildMap {
      val keys = emotes.keys()
      while (keys.hasNext()) {
        val token = keys.next()
        if (token.isBlank() || !message.contains(token)) continue
        val raw = emotes.opt(token)
        val value =
          when (raw) {
            is JSONObject -> raw
            is JSONArray -> raw.optJSONObject(0)
            else -> null
          } ?: continue
        val url =
          value
            .optString("url")
            .ifBlank { value.optString("gif") }
            .takeIf(String::isNotBlank)
            ?.let(dev.openbili.webdemo.UrlPolicy::normalizeImageUrl)
        if (!url.isNullOrBlank()) put(token, url)
      }
    }
  }

  private fun parseLotteryWinners(value: JSONArray?): List<LiveLotteryWinner> {
    value ?: return emptyList()
    return buildList {
      for (index in 0 until value.length()) {
        val item = value.optJSONObject(index) ?: continue
        val uid = item.optLong("uid")
        if (uid <= 0L) continue
        add(
          LiveLotteryWinner(
            uid = uid,
            name = item.optString("uname").ifBlank { "用户 $uid" },
            faceUrl = dev.openbili.webdemo.UrlPolicy.normalizeImageUrl(item.optString("face")),
          )
        )
      }
    }
  }

  private fun inflate(compressed: ByteArray): ByteArray? =
    runCatching {
        val inflater = Inflater()
        inflater.setInput(compressed)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (!inflater.finished() && output.size() <= MAX_PACKET_BYTES) {
          val read = inflater.inflate(buffer)
          if (read == 0) {
            if (inflater.needsInput() || inflater.needsDictionary()) break
          } else {
            output.write(buffer, 0, read)
          }
        }
        inflater.end()
        output.toByteArray().takeIf { it.size <= MAX_PACKET_BYTES }
      }
      .getOrNull()

  private fun splitJsonObjects(value: String): List<String> {
    if (!value.contains("}{")) return listOf(value)
    val result = mutableListOf<String>()
    var depth = 0
    var start = 0
    var quoted = false
    var escaped = false
    value.forEachIndexed { index, char ->
      when {
        escaped -> escaped = false
        char == '\\' && quoted -> escaped = true
        char == '"' -> quoted = !quoted
        !quoted && char == '{' -> depth++
        !quoted && char == '}' -> {
          depth--
          if (depth == 0) {
            result += value.substring(start, index + 1)
            start = index + 1
          }
        }
      }
    }
    return result.ifEmpty { listOf(value) }
  }

  private fun String.asJsonObject(): JSONObject? =
    takeIf { it.trimStart().startsWith('{') }?.let { runCatching { JSONObject(it) }.getOrNull() }
}

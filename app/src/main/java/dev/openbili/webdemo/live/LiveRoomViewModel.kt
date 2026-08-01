package dev.openbili.webdemo.live

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.UserInfo
import java.util.LinkedHashMap
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LiveRoomViewModel(application: Application) : AndroidViewModel(application) {
  private val _state = MutableStateFlow(LiveRoomUiState())
  val state: StateFlow<LiveRoomUiState> = _state.asStateFlow()

  private data class QueuedMessage(val generation: Long, val message: LiveChatMessage)

  private data class QueuedDanmaku(
    val generation: Long,
    val message: LiveChatMessage,
    val enterAtElapsedMs: Long,
  )

  private val messageQueue =
    Channel<QueuedMessage>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val liveDanmakuQueue =
    Channel<QueuedDanmaku>(
      capacity = LIVE_DANMAKU_QUEUE_CAPACITY,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  private var roomJob: Job? = null
  private var playbackJob: Job? = null
  private var playbackRetryJob: Job? = null
  private var audienceJob: Job? = null
  private var guardJob: Job? = null
  private var recommendationsJob: Job? = null
  private var avatarJob: Job? = null
  private var lotteryJob: Job? = null
  private var danmakuClient: LiveDanmakuClient? = null
  private var danmuConfig: LiveDanmuConfig? = null
  private var generation = 0L
  private var foreground = true
  private var lastPlayUrlReloadAtMs = 0L
  private val playbackRecovery = LivePlaybackRecovery()
  private var account = UserInfo(0L, "", "", false)
  private val pendingAvatarUids = linkedSetOf<Long>()
  private val avatarRetryAfter = mutableMapOf<Long, Long>()
  private val avatarCache =
    object : LinkedHashMap<Long, Pair<String, String>>(128, .75f, true) {
      override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<Long, Pair<String, String>>?
      ): Boolean = size > MAX_AVATAR_CACHE
    }

  init {
    viewModelScope.launch {
      while (true) {
        val first = messageQueue.receive()
        val batch = mutableListOf(first)
        withTimeoutOrNull(65L) {
          while (batch.size < 3) batch += messageQueue.receive()
        }
        val activeGeneration = _state.value.generation
        val messages =
          batch
            .asSequence()
            .filter { it.generation == activeGeneration }
            .map { it.message }
            .toList()
        if (messages.isNotEmpty()) mergeIncomingMessages(messages)
        if (batch.size >= 3) delay(90L)
      }
    }
    viewModelScope.launch {
      while (true) {
        val first = liveDanmakuQueue.receive()
        val batch = mutableListOf(first)
        withTimeoutOrNull(LIVE_DANMAKU_BATCH_WINDOW_MS) {
          while (batch.size < MAX_LIVE_DANMAKU_BATCH_SIZE) {
            batch += liveDanmakuQueue.receive()
          }
        }
        mergeLiveDanmaku(batch)
      }
    }
  }

  fun updateAccount(value: UserInfo) {
    val changed = account.mid != value.mid || account.isLogin != value.isLogin
    account = value
    if (changed && _state.value.roomInfo != null) {
      restartDanmakuConnection()
      loadAccountInteraction(_state.value.generation)
      _state.value.roomInfo?.let { loadEmojiPacks(_state.value.generation, it) }
      loadInteractiveLottery(_state.value.generation)
    }
  }

  fun open(entry: LiveSearchRoom, navigationEntryId: Long = 0L) {
    foreground = true
    val nextGeneration = ++generation
    roomJob?.cancel()
    playbackJob?.cancel()
    playbackRetryJob?.cancel()
    playbackRecovery.reset()
    lastPlayUrlReloadAtMs = 0L
    audienceJob?.cancel()
    guardJob?.cancel()
    recommendationsJob?.cancel()
    avatarJob?.cancel()
    lotteryJob?.cancel()
    pendingAvatarUids.clear()
    danmakuClient?.stop()
    danmakuClient = null
    danmuConfig = null
    _state.value =
      LiveRoomUiState(
        entryRoomId = entry.roomId,
        navigationEntryId = navigationEntryId,
        generation = nextGeneration,
        loading = true,
        watchedText = entry.watchedText,
        online = 0L,
        roomInfo =
          LiveRoomInfo(
            roomId = entry.roomId,
            shortRoomId = entry.shortRoomId,
            anchorUid = entry.uid,
            title = entry.title,
            description = "",
            coverUrl = entry.coverUrl,
            keyframeUrl = entry.keyframeUrl,
            areaName = entry.areaName,
            parentAreaName = entry.parentAreaName,
            liveStatus = entry.liveStatus,
            online = 0L,
          ),
        anchorInfo = LiveAnchorInfo(entry.uid, entry.uname, entry.faceUrl),
      )
    loadRecommendations(nextGeneration, entry.roomId)
    LiveHistoryStore.record(getApplication(), entry)
    roomJob = viewModelScope.launch {
      try {
        val room = withContext(Dispatchers.IO) { BiliLiveApi.getRoomInfo(entry.roomId) }
        if (!isCurrent(nextGeneration)) return@launch
        _state.update {
          it.copy(
            roomInfo = room,
            loading = false,
            online = room.online,
            error = null,
          )
        }
        LiveHistoryStore.record(
          getApplication(),
          entry.copy(
            roomId = room.roomId,
            shortRoomId = room.shortRoomId,
            uid = room.anchorUid,
            title = room.title,
            coverUrl = room.coverUrl,
            keyframeUrl = room.keyframeUrl,
            areaName = room.areaName,
            parentAreaName = room.parentAreaName,
            liveStatus = room.liveStatus,
          ),
        )
        loadRoomCapabilities(nextGeneration, room)
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!isCurrent(nextGeneration)) return@launch
        _state.update {
          it.copy(loading = false, error = error.message ?: "直播间加载失败")
        }
      }
    }
  }

  fun close() {
    foreground = false
    playbackJob?.cancel()
    playbackRetryJob?.cancel()
    playbackRecovery.reset()
    danmakuClient?.stop()
    danmakuClient = null
    _state.update { it.copy(connection = LiveConnectionState.DISCONNECTED) }
  }

  fun setForeground(value: Boolean) {
    if (foreground == value) return
    foreground = value
    if (value) {
      val current = _state.value
      if (current.roomInfo != null) {
        loadPlayback(current.playback?.currentQn ?: 10_000)
        restartDanmakuConnection()
        loadInteractiveLottery(current.generation)
      }
    } else {
      danmakuClient?.stop()
      danmakuClient = null
      _state.update { it.copy(connection = LiveConnectionState.DISCONNECTED) }
    }
  }

  fun retryRoom() {
    val current = _state.value
    val room = current.roomInfo
    open(
      LiveSearchRoom(
        roomId = current.entryRoomId,
        shortRoomId = room?.shortRoomId,
        uid = room?.anchorUid ?: current.anchorInfo?.uid ?: 0L,
        title = room?.title.orEmpty(),
        uname = current.anchorInfo?.name.orEmpty(),
        faceUrl = current.anchorInfo?.faceUrl,
        coverUrl = room?.coverUrl,
        keyframeUrl = room?.keyframeUrl,
        areaName = room?.areaName,
        parentAreaName = room?.parentAreaName,
        watchedText = current.watchedText,
        liveStatus = room?.liveStatus ?: 0,
      ),
      navigationEntryId = current.navigationEntryId,
    )
  }

  fun loadPlayback(qn: Int) {
    playbackRetryJob?.cancel()
    playbackRecovery.reset()
    requestPlayback(qn, reason = "explicit")
  }

  private fun requestPlayback(qn: Int, reason: String) {
    val roomId = _state.value.roomInfo?.roomId ?: return
    val safeQn = qn.takeIf { it > 0 }?.coerceAtMost(LIVE_ORIGINAL_QN) ?: LIVE_ORIGINAL_QN
    val requestGeneration = _state.value.generation
    _state.update {
      it.copy(playbackLoading = true, playbackError = null, activeSourceIndex = 0)
    }
    playbackJob?.cancel()
    playbackJob = viewModelScope.launch {
      try {
        val playback = withContext(Dispatchers.IO) { BiliLiveApi.getPlayInfo(roomId, safeQn) }
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          it.copy(
            playback = playback,
            playbackLoading = false,
            playbackError = null,
            activeSourceIndex = 0,
          )
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!isCurrent(requestGeneration)) return@launch
        Log.e(
          TAG,
          "play URL request failed room=$roomId qn=$safeQn reason=$reason " +
            "cause=${error.javaClass.simpleName}",
        )
        _state.update {
          it.copy(
            playbackLoading = false,
            playbackError = error.message ?: "直播流加载失败",
          )
        }
      }
    }
  }

  fun onPlaybackError(sourceIndex: Int, error: PlaybackException) {
    val current = _state.value
    val sources = current.playback?.sources.orEmpty()
    if (sourceIndex != current.activeSourceIndex) {
      Log.d(
        TAG,
        "ignored stale playback failure room=${current.roomInfo?.roomId} " +
          "source=$sourceIndex active=${current.activeSourceIndex}",
      )
      return
    }
    val source = sources.getOrNull(sourceIndex)
    val httpCode = findHttpResponseCode(error)
    val causeName = deepestCauseName(error)
    Log.e(
      TAG,
      buildString {
        append("playback failed room=${current.roomInfo?.roomId}")
        append(" qn=${current.playback?.currentQn}")
        append(" source=$sourceIndex/${sources.size}")
        source?.let { append(" ${safeSourceDescription(it)}") }
        append(" code=${error.errorCodeName}")
        if (httpCode != null) append(" http=$httpCode")
        append(" cause=$causeName")
      },
    )

    when (
      val action =
        playbackRecovery.onFailure(
          nowMs = SystemClock.elapsedRealtime(),
          currentSourceIndex = sourceIndex,
          sourceCount = sources.size,
        )
    ) {
      is LivePlaybackRecoveryAction.SwitchSource -> {
        playbackRetryJob?.cancel()
        _state.update {
          if (it.activeSourceIndex != sourceIndex) it
          else
            it.copy(
              activeSourceIndex = action.index,
              playbackLoading = true,
              playbackError = null,
            )
        }
      }
      is LivePlaybackRecoveryAction.RefreshUrls -> {
        playbackRetryJob?.cancel()
        val requestGeneration = current.generation
        val qn = current.playback?.currentQn ?: LIVE_ORIGINAL_QN
        _state.update {
          it.copy(playbackLoading = true, playbackError = null)
        }
        playbackRetryJob = viewModelScope.launch {
          delay(action.delayMs)
          if (!isCurrent(requestGeneration)) return@launch
          requestPlayback(qn, reason = "recovery-${action.round}")
        }
      }
      LivePlaybackRecoveryAction.Stop -> {
        playbackRetryJob?.cancel()
        _state.update {
          it.copy(
            playbackLoading = false,
            playbackError = "直播流连续加载失败，请手动重试",
          )
        }
      }
      LivePlaybackRecoveryAction.Ignore ->
        Log.d(TAG, "ignored duplicate playback failure source=$sourceIndex")
    }
  }

  fun onPlaybackReady(sourceIndex: Int) {
    val current = _state.value
    if (sourceIndex != current.activeSourceIndex) return
    val source = current.playback?.sources?.getOrNull(sourceIndex)
    playbackRetryJob?.cancel()
    playbackRecovery.onReady()
    _state.update {
      if (it.activeSourceIndex != sourceIndex) it
      else it.copy(playbackLoading = false, playbackError = null)
    }
    Log.i(
      TAG,
      "playback ready room=${current.roomInfo?.roomId} qn=${current.playback?.currentQn} " +
        "source=$sourceIndex ${source?.let(::safeSourceDescription).orEmpty()}",
    )
  }

  fun setComposerText(value: String, selectionStart: Int) {
    val text = value.take(MAX_COMPOSER_LENGTH)
    _state.update {
      it.copy(
        composer =
          it.composer.copy(
            text = text,
            selectionStart = selectionStart.coerceIn(0, text.length),
            error = null,
          )
      )
    }
  }

  fun toggleEmojiPanel() {
    _state.update {
      val nextVisible = !it.composer.emojiPanelVisible
      val firstPack = it.composer.selectedEmojiPackId ?: it.emojiPacks.firstOrNull()?.id
      it.copy(
        composer =
          it.composer.copy(
            emojiPanelVisible = nextVisible,
            selectedEmojiPackId = firstPack,
            error = null,
          )
      )
    }
  }

  fun selectEmojiPack(id: String) {
    _state.update {
      it.copy(composer = it.composer.copy(selectedEmojiPackId = id))
    }
  }

  fun sendText() {
    val current = _state.value
    val roomId = current.roomInfo?.roomId ?: return
    val text = current.composer.text.trim()
    if (text.isBlank() || current.composer.sending) return
    if (!account.isLogin) {
      _state.update {
        it.copy(composer = it.composer.copy(error = "请先登录后再发送弹幕"))
      }
      return
    }
    val requestGeneration = current.generation
    _state.update { it.copy(composer = it.composer.copy(sending = true, error = null)) }
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { BiliLiveApi.sendTextDanmaku(roomId, text) }
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          it.copy(
            composer =
              it.composer.copy(
                text = "",
                selectionStart = 0,
                sending = false,
                error = null,
              )
          )
        }
        insertLocalSentMessage(
          generation = requestGeneration,
          content = richTextContent(text),
        )
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          it.copy(
            composer =
              it.composer.copy(
                sending = false,
                error = error.message ?: "弹幕发送失败",
              )
          )
        }
      }
    }
  }

  fun sendEmoji(emoji: LiveEmoji) {
    val current = _state.value
    val roomId = current.roomInfo?.roomId ?: return
    if (current.composer.sending) return
    if (!emoji.available) {
      _state.update {
        it.copy(
          composer =
            it.composer.copy(error = emoji.unavailableReason ?: "该表情暂不可用")
        )
      }
      return
    }
    if (!account.isLogin) {
      _state.update {
        it.copy(composer = it.composer.copy(error = "请先登录后再发送表情"))
      }
      return
    }
    if (emoji.kind == LiveEmojiKind.ROOM_EXCLUSIVE && emoji.roomId != roomId) {
      _state.update {
        it.copy(composer = it.composer.copy(error = "这个专属表情不属于当前直播间"))
      }
      return
    }
    if (!emoji.directSend) {
      _state.update {
        val composer = it.composer
        val cursor = composer.selectionStart.coerceIn(0, composer.text.length)
        val available = (MAX_COMPOSER_LENGTH - composer.text.length).coerceAtLeast(0)
        val token = emoji.inputText.take(available)
        val next = composer.text.substring(0, cursor) + token + composer.text.substring(cursor)
        it.copy(
          composer =
            composer.copy(
              text = next,
              selectionStart = cursor + token.length,
              error = null,
            )
        )
      }
      return
    }
    val requestGeneration = current.generation
    _state.update { it.copy(composer = it.composer.copy(sending = true, error = null)) }
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { BiliLiveApi.sendEmoji(roomId, emoji) }
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          it.copy(
            composer =
              it.composer.copy(
                sending = false,
                emojiPanelVisible = false,
                error = null,
              )
          )
        }
        insertLocalSentMessage(
          generation = requestGeneration,
          content =
            LiveChatContent.Emoji(
              displayName = emoji.displayName,
              fileId = emoji.fileId,
              imageUrl = emoji.imageUrl,
              isBulge = emoji.isBulge,
            ),
        )
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          it.copy(
            composer =
              it.composer.copy(
                sending = false,
                error = error.message ?: "表情发送失败",
              )
          )
        }
      }
    }
  }

  fun joinInteractiveLottery() {
    val current = _state.value
    val lottery = current.interactiveLottery ?: return
    if (lottery.status != LiveLotteryStatus.ACTIVE || lottery.endAtEpochMs <= System.currentTimeMillis()) {
      _state.update {
        it.copy(
          interactiveLottery =
            it.interactiveLottery?.copy(
              status = LiveLotteryStatus.ENDED,
              error = "本轮互动抽奖已经结束",
            )
        )
      }
      return
    }
    if (lottery.requiresPayment) {
      _state.update {
        it.copy(
          interactiveLottery =
            it.interactiveLottery?.copy(
              error = "该抽奖包含付费条件，本应用不提供付费参与",
            )
        )
      }
      return
    }
    if (!account.isLogin) {
      _state.update {
        it.copy(
          interactiveLottery =
            it.interactiveLottery?.copy(error = "请先登录后再参与互动抽奖")
        )
      }
      return
    }
    val requestGeneration = current.generation
    _state.update {
      it.copy(
        interactiveLottery =
          it.interactiveLottery?.copy(status = LiveLotteryStatus.JOINING, error = null)
      )
    }
    lotteryJob?.cancel()
    lotteryJob =
      viewModelScope.launch {
        try {
          withContext(Dispatchers.IO) { BiliLiveApi.joinInteractiveLottery(lottery.id) }
          if (!isCurrent(requestGeneration)) return@launch
          _state.update {
            val active = it.interactiveLottery
            if (active?.id != lottery.id) it
            else
              it.copy(
                interactiveLottery =
                  active.copy(status = LiveLotteryStatus.JOINED, error = null)
              )
          }
        } catch (error: Exception) {
          if (error is CancellationException) throw error
          if (!isCurrent(requestGeneration)) return@launch
          _state.update {
            val active = it.interactiveLottery
            if (active?.id != lottery.id) it
            else
              it.copy(
                interactiveLottery =
                  active.copy(
                    status = LiveLotteryStatus.ACTIVE,
                    error = error.message ?: "参与互动抽奖失败",
                  )
              )
          }
        }
      }
  }

  fun selectRankTab(tab: LiveRankTab) {
    _state.update { it.copy(rankTab = tab) }
    when (tab) {
      LiveRankTab.AUDIENCE ->
        if (_state.value.audienceRank.items.isEmpty()) loadAudienceRank(_state.value.generation)
      LiveRankTab.GUARD -> if (_state.value.guardRank.items.isEmpty()) loadMoreGuards()
    }
  }

  fun ensureRankLoaded() {
    when (_state.value.rankTab) {
      LiveRankTab.AUDIENCE ->
        if (_state.value.audienceRank.items.isEmpty() && !_state.value.audienceRank.isLoading) {
          loadAudienceRank(_state.value.generation)
        }
      LiveRankTab.GUARD ->
        if (_state.value.guardRank.items.isEmpty() && !_state.value.guardRank.isLoading) {
          loadMoreGuards()
        }
    }
  }

  fun retryRecommendations() {
    val current = _state.value
    val roomId = current.roomInfo?.roomId ?: current.entryRoomId
    if (roomId > 0L && !current.recommendationsLoading) {
      loadRecommendations(current.generation, roomId)
    }
  }

  fun selectAudienceRank(type: String, switch: String) {
    if (type == _state.value.audienceRank.type && switch == _state.value.audienceRank.switch) return
    _state.update {
      it.copy(audienceRank = LiveAudienceRankState(type = type, switch = switch))
    }
    loadAudienceRank(_state.value.generation)
  }

  fun selectGuardType(type: Int) {
    if (type !in 3..5 || type == _state.value.guardRank.typ) return
    _state.update { it.copy(guardRank = LiveGuardRankState(typ = type)) }
    loadMoreGuards()
  }

  fun loadMoreGuards() {
    val current = _state.value
    val room = current.roomInfo ?: return
    val rank = current.guardRank
    if (rank.isLoading || rank.endReached) return
    val requestGeneration = current.generation
    val page = rank.nextPage
    guardJob?.cancel()
    _state.update {
      it.copy(guardRank = it.guardRank.copy(isLoading = true, error = null))
    }
    guardJob = viewModelScope.launch {
      try {
        val result =
          withContext(Dispatchers.IO) {
            BiliLiveApi.getGuardRank(
              roomId = room.roomId,
              anchorUid = room.anchorUid,
              page = page,
              pageSize = 30,
              type = rank.typ,
            )
          }
        if (!isCurrent(requestGeneration) || _state.value.guardRank.typ != rank.typ) return@launch
        _state.update {
          val existing = it.guardRank.items
          val incoming = if (page == 1) result.top3 + result.items else result.items
          val merged = (existing + incoming).distinctBy(LiveRankUser::uid)
          it.copy(
            guardRank =
              it.guardRank.copy(
                totalCount = result.totalCount,
                items = merged,
                nextPage = page + 1,
                totalPageHint = result.totalPageHint,
                endReached = result.items.isEmpty(),
                isLoading = false,
                error = null,
              )
          )
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          it.copy(
            guardRank =
              it.guardRank.copy(
                isLoading = false,
                error = error.message ?: "大航海加载失败",
              )
          )
        }
      }
    }
  }

  fun loadFollowingGroups() {
    if (!account.isLogin || _state.value.followingGroupsLoading) return
    _state.update { it.copy(followingGroupsLoading = true) }
    val requestGeneration = _state.value.generation
    viewModelScope.launch {
      val groups =
        withContext(Dispatchers.IO) {
          runCatching { BiliApi.getFollowingGroups() }.getOrDefault(emptyList())
        }
      if (!isCurrent(requestGeneration)) return@launch
      _state.update { it.copy(followingGroups = groups, followingGroupsLoading = false) }
    }
  }

  fun selectFollowingGroup(groupId: Long) {
    setFollowState(true, groupId)
  }

  fun unfollow() {
    setFollowState(false, null)
  }

  private fun setFollowState(follow: Boolean, groupId: Long?) {
    val uid = _state.value.anchorInfo?.uid ?: return
    if (!account.isLogin || uid <= 0L || _state.value.followBusy) return
    val requestGeneration = _state.value.generation
    _state.update { it.copy(followBusy = true) }
    viewModelScope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching {
            BiliApi.setFollowing(uid, follow)
            if (follow && groupId != null) BiliApi.setFollowingGroup(uid, groupId)
          }
        }
      if (!isCurrent(requestGeneration)) return@launch
      _state.update {
        it.copy(
          followed = if (result.isSuccess) follow else it.followed,
          followBusy = false,
        )
      }
    }
  }

  private fun loadRoomCapabilities(requestGeneration: Long, room: LiveRoomInfo) {
    viewModelScope.launch {
      val anchor =
        withContext(Dispatchers.IO) {
          runCatching { BiliLiveApi.getAnchorInfo(room.anchorUid) }.getOrNull()
        }
      if (isCurrent(requestGeneration) && anchor != null) {
        _state.update { it.copy(anchorInfo = anchor) }
        val currentRoom = _state.value.roomInfo
        if (currentRoom != null) {
          LiveHistoryStore.record(
            getApplication(),
            LiveSearchRoom(
              roomId = currentRoom.roomId,
              shortRoomId = currentRoom.shortRoomId,
              uid = anchor.uid,
              title = currentRoom.title,
              uname = anchor.name,
              faceUrl = anchor.faceUrl,
              coverUrl = currentRoom.coverUrl,
              keyframeUrl = currentRoom.keyframeUrl,
              areaName = currentRoom.areaName,
              parentAreaName = currentRoom.parentAreaName,
              watchedText = _state.value.watchedText,
              liveStatus = currentRoom.liveStatus,
            ),
          )
        }
      }
    }
    loadPlayback(10_000)
    loadDanmakuConfig(requestGeneration, room)
    loadEmojiPacks(requestGeneration, room)
    loadAccountInteraction(requestGeneration)
    loadInteractiveLottery(requestGeneration)
  }

  private fun loadRecommendations(requestGeneration: Long, roomId: Long) {
    recommendationsJob?.cancel()
    _state.update {
      it.copy(recommendationsLoading = true, recommendationsError = null)
    }
    recommendationsJob = viewModelScope.launch {
      try {
        val rooms =
          withContext(Dispatchers.IO) {
            BiliLiveApi.getHomeRecommendations(limit = 14)
              .filterNot { it.roomId == roomId }
              .distinctBy(LiveSearchRoom::roomId)
              .take(12)
          }
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          it.copy(
            recommendations = rooms,
            recommendationsLoading = false,
            recommendationsError = null,
          )
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          it.copy(
            recommendationsLoading = false,
            recommendationsError = error.message ?: "推荐直播加载失败",
          )
        }
      }
    }
  }

  private fun loadDanmakuConfig(requestGeneration: Long, room: LiveRoomInfo) {
    viewModelScope.launch {
      try {
        val config = withContext(Dispatchers.IO) { BiliLiveApi.getDanmuConfig(room.roomId) }
        if (!isCurrent(requestGeneration)) return@launch
        danmuConfig = config
        restartDanmakuConnection()
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          it.copy(
            connection = LiveConnectionState.DISCONNECTED,
            connectionError = error.message ?: "直播消息连接失败",
          )
        }
      }
    }
  }

  private fun restartDanmakuConnection() {
    danmakuClient?.stop()
    danmakuClient = null
    val current = _state.value
    val roomId = current.roomInfo?.roomId ?: return
    val config = danmuConfig ?: return
    if (!foreground) return
    val requestGeneration = current.generation
    danmakuClient =
      LiveDanmakuClient(
          roomId = roomId,
          accountUid = account.mid,
          config = config,
          onState = { connection, error ->
            if (isCurrent(requestGeneration)) {
              _state.update {
                it.copy(connection = connection, connectionError = error)
              }
            }
          },
          onEvent = { event -> handleSocketEvent(requestGeneration, event) },
        )
        .also(LiveDanmakuClient::start)
  }

  private fun handleSocketEvent(requestGeneration: Long, event: LiveSocketEvent) {
    if (!isCurrent(requestGeneration)) return
    when (event) {
      LiveSocketEvent.Authenticated -> {
        _state.update {
          it.copy(connection = LiveConnectionState.CONNECTED, connectionError = null)
        }
        loadInteractiveLottery(requestGeneration)
      }
      is LiveSocketEvent.Message ->
        event.value.let { message ->
          if (message.content !is LiveChatContent.System) {
            liveDanmakuQueue.trySend(
              QueuedDanmaku(
                generation = requestGeneration,
                message = message,
                enterAtElapsedMs = SystemClock.elapsedRealtime() + LIVE_DANMAKU_ENTRY_DELAY_MS,
              )
            )
          }
          messageQueue.trySend(QueuedMessage(requestGeneration, message))
        }
      is LiveSocketEvent.Online -> _state.update { it.copy(online = event.value) }
      is LiveSocketEvent.Watched -> _state.update { it.copy(watchedText = event.text) }
      is LiveSocketEvent.RoomChanged -> {
        event.title?.takeIf(String::isNotBlank)?.let { title ->
          _state.update { state ->
            state.copy(roomInfo = state.roomInfo?.copy(title = title))
          }
        }
      }
      is LiveSocketEvent.LiveStatus ->
        _state.update { state ->
          state.copy(roomInfo = state.roomInfo?.copy(liveStatus = if (event.living) 1 else 0))
        }
      LiveSocketEvent.PlayUrlReload -> {
        val nowMs = SystemClock.elapsedRealtime()
        if (
          lastPlayUrlReloadAtMs == 0L ||
            nowMs - lastPlayUrlReloadAtMs >= PLAY_URL_RELOAD_DEBOUNCE_MS
        ) {
          lastPlayUrlReloadAtMs = nowMs
          loadPlayback(_state.value.playback?.currentQn ?: LIVE_ORIGINAL_QN)
        } else {
          Log.d(TAG, "ignored duplicate PLAYURL_RELOAD")
        }
      }
      is LiveSocketEvent.LotteryStarted -> {
        val roomId = _state.value.roomInfo?.roomId
        if (event.lottery.roomId <= 0L || event.lottery.roomId == roomId) {
          lotteryJob?.cancel()
          _state.update { it.copy(interactiveLottery = event.lottery.copy(error = null)) }
        }
      }
      is LiveSocketEvent.LotteryEnded ->
        updateLottery(event.id) {
          it.copy(status = LiveLotteryStatus.ENDED, error = null)
        }
      is LiveSocketEvent.LotteryAwarded -> {
        val winners = event.winners
        updateLottery(event.id) {
          it.copy(
            awardName = event.awardName.ifBlank { it.awardName },
            awardImageUrl = event.awardImageUrl ?: it.awardImageUrl,
            status = LiveLotteryStatus.AWARDED,
            winners = winners,
            error = null,
          )
        }
        val winnerNames = winners.take(3).joinToString("、", transform = LiveLotteryWinner::name)
        val text =
          when {
            winners.any { it.uid == account.mid } -> "恭喜你在互动抽奖中获得「${event.awardName}」"
            winnerNames.isNotBlank() -> "互动抽奖开奖：$winnerNames 获得「${event.awardName}」"
            else -> "互动抽奖「${event.awardName}」已开奖"
          }
        messageQueue.trySend(
          QueuedMessage(
            requestGeneration,
            LiveChatMessage(
              stableId = "lottery-award:${event.id}:${System.currentTimeMillis()}",
              uid = null,
              uname = null,
              faceUrl = null,
              content = LiveChatContent.System(text),
              fanMedal = null,
              receivedAtMs = System.currentTimeMillis(),
            ),
          )
        )
        scheduleLotteryClear(requestGeneration, event.id, LOTTERY_RESULT_VISIBLE_MS)
      }
      is LiveSocketEvent.LotteryInvalidated -> {
        updateLottery(event.id) {
          it.copy(
            status = LiveLotteryStatus.INVALID,
            error = event.reason ?: "本轮互动抽奖已失效",
          )
        }
        scheduleLotteryClear(requestGeneration, event.id, LOTTERY_INVALID_VISIBLE_MS)
      }
    }
  }

  private fun loadInteractiveLottery(requestGeneration: Long) {
    val roomId = _state.value.roomInfo?.roomId ?: return
    viewModelScope.launch {
      val lottery =
        withContext(Dispatchers.IO) {
          runCatching { BiliLiveApi.getInteractiveLottery(roomId) }.getOrNull()
        }
      if (!isCurrent(requestGeneration)) return@launch
      _state.update {
        val restored =
          lottery?.takeIf { value -> value.endAtEpochMs > System.currentTimeMillis() }
        val current = it.interactiveLottery
        when {
          restored == null -> it
          current == null -> it.copy(interactiveLottery = restored)
          current.id != restored.id &&
            current.status in setOf(LiveLotteryStatus.ENDED, LiveLotteryStatus.INVALID) ->
            it.copy(interactiveLottery = restored)
          current.id == restored.id && current.status == LiveLotteryStatus.ACTIVE ->
            it.copy(interactiveLottery = restored)
          else -> it
        }
      }
    }
  }

  private fun updateLottery(
    id: Long,
    transform: (LiveInteractiveLottery) -> LiveInteractiveLottery,
  ) {
    _state.update {
      val current = it.interactiveLottery
      if (current?.id != id) it else it.copy(interactiveLottery = transform(current))
    }
  }

  private fun scheduleLotteryClear(requestGeneration: Long, id: Long, delayMs: Long) {
    lotteryJob?.cancel()
    lotteryJob =
      viewModelScope.launch {
        delay(delayMs)
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          if (it.interactiveLottery?.id == id) it.copy(interactiveLottery = null) else it
        }
      }
  }

  private fun loadEmojiPacks(requestGeneration: Long, room: LiveRoomInfo) {
    _state.update { it.copy(emojiLoading = true, emojiError = null, emojiPacks = emptyList()) }
    viewModelScope.launch {
      try {
        val packs = withContext(Dispatchers.IO) { BiliLiveApi.getEmojiPacks(room.roomId) }
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          val selectedPackId =
            it.composer.selectedEmojiPackId?.takeIf { selected ->
              packs.any { pack -> pack.id == selected }
            } ?: packs.firstOrNull()?.id
          it.copy(
            emojiPacks = packs,
            emojiLoading = false,
            emojiError = null,
            composer = it.composer.copy(selectedEmojiPackId = selectedPackId),
          )
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          it.copy(
            emojiLoading = false,
            emojiError = error.message ?: "表情加载失败",
            emojiPacks = emptyList(),
          )
        }
      }
    }
  }

  private fun loadAudienceRank(requestGeneration: Long) {
    val current = _state.value
    val room = current.roomInfo ?: return
    val type = current.audienceRank.type
    val switch = current.audienceRank.switch
    audienceJob?.cancel()
    _state.update {
      it.copy(audienceRank = it.audienceRank.copy(isLoading = true, error = null))
    }
    audienceJob = viewModelScope.launch {
      try {
        val rank =
          withContext(Dispatchers.IO) {
            BiliLiveApi.getAudienceRank(room.roomId, room.anchorUid, type, switch)
          }
        if (
          !isCurrent(requestGeneration) ||
            _state.value.audienceRank.type != type ||
            _state.value.audienceRank.switch != switch
        )
          return@launch
        _state.update {
          it.copy(
            audienceRank =
              it.audienceRank.copy(
                countText = rank.countText,
                valueText = rank.valueText,
                items = rank.items,
                isLoading = false,
                error = null,
              )
          )
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!isCurrent(requestGeneration)) return@launch
        _state.update {
          it.copy(
            audienceRank =
              it.audienceRank.copy(
                isLoading = false,
                error = error.message ?: "房间观众加载失败",
              )
          )
        }
      }
    }
  }

  private fun loadAccountInteraction(requestGeneration: Long) {
    val room = _state.value.roomInfo ?: return
    viewModelScope.launch {
      val medal =
        withContext(Dispatchers.IO) {
          runCatching { BiliLiveApi.getActiveMedal(room.roomId, room.anchorUid) }.getOrNull()
        }
      val followed =
        if (account.isLogin && room.anchorUid > 0L) {
          withContext(Dispatchers.IO) {
            runCatching { BiliApi.isFollowing(room.anchorUid) }.getOrDefault(false)
          }
        } else {
          false
        }
      if (!isCurrent(requestGeneration)) return@launch
      _state.update { it.copy(activeMedal = medal, followed = followed) }
    }
  }

  private fun insertLocalSentMessage(
    generation: Long,
    content: LiveChatContent,
  ) {
    if (!isCurrent(generation)) return
    val now = System.currentTimeMillis()
    val echo =
      _state.value.messages.lastOrNull {
        it.uid == account.mid &&
          now - it.receivedAtMs in 0..5_000L &&
          contentKey(it.content) == contentKey(content)
      }
    if (echo != null) return
    val message =
      LiveChatMessage(
        stableId = "local:${UUID.randomUUID()}",
        uid = account.mid,
        uname = account.name,
        faceUrl = account.face,
        content = content,
        fanMedal = _state.value.activeMedal,
        delivery = LiveMessageDelivery.Pending,
        receivedAtMs = now,
      )
    _state.update { it.copy(messages = (it.messages + message).takeLast(MAX_MESSAGES)) }
  }

  private fun mergeLiveDanmaku(batch: List<QueuedDanmaku>) {
    if (batch.isEmpty()) return
    val requestGeneration = _state.value.generation
    val queued = batch.filter {
      it.generation == requestGeneration && it.message.content !is LiveChatContent.System
    }
    if (queued.isEmpty()) return
    val cutoff = SystemClock.elapsedRealtime() - LIVE_DANMAKU_RETAIN_MS
    _state.update { current ->
      if (current.generation != requestGeneration) return@update current
      val seenIds = current.liveDanmaku.asSequence().mapTo(HashSet(), LiveDanmakuEvent::stableId)
      val additions = queued.mapNotNull { value ->
        val message = value.message
        if (!seenIds.add(message.stableId)) return@mapNotNull null
        LiveDanmakuEvent(
          stableId = message.stableId,
          content = message.content,
          enterAtElapsedMs = value.enterAtElapsedMs,
        )
      }
      if (additions.isEmpty() && current.liveDanmaku.none { it.enterAtElapsedMs < cutoff }) {
        return@update current
      }
      val next =
        (current.liveDanmaku + additions)
          .asSequence()
          .filter { it.enterAtElapsedMs >= cutoff }
          .toList()
          .takeLast(MAX_LIVE_DANMAKU)
      current.copy(liveDanmaku = next)
    }
  }

  private fun mergeIncomingMessages(messages: List<LiveChatMessage>) {
    val enriched =
      messages.map { message ->
        val uid = message.uid
        val cached = uid?.let(avatarCache::get)
        when {
          cached != null ->
            message.copy(
              uname = message.uname ?: cached.first,
              faceUrl = message.faceUrl ?: cached.second.takeIf(String::isNotBlank),
            )
          uid == account.mid && account.isLogin ->
            message.copy(
              uname = message.uname ?: account.name,
              faceUrl = message.faceUrl ?: account.face,
            )
          else -> message
        }
      }
    _state.update { current ->
      val next = current.messages.toMutableList()
      enriched.forEach { message ->
        val pendingIndex =
          next.indexOfLast {
            it.delivery is LiveMessageDelivery.Pending &&
              it.uid == message.uid &&
              kotlin.math.abs(it.receivedAtMs - message.receivedAtMs) <= 5_000L &&
              contentKey(it.content) == contentKey(message.content)
          }
        when {
          pendingIndex >= 0 -> {
            val pending = next[pendingIndex]
            next[pendingIndex] =
              message.copy(
                stableId = pending.stableId,
                delivery = LiveMessageDelivery.Sent(message.stableId),
                receivedAtMs = pending.receivedAtMs,
              )
          }
          next.none { it.stableId == message.stableId } -> next += message
        }
      }
      current.copy(messages = next.takeLast(MAX_MESSAGES))
    }
    enqueueMissingAvatars(_state.value.generation, enriched)
  }

  private fun enqueueMissingAvatars(
    requestGeneration: Long,
    messages: List<LiveChatMessage>,
  ) {
    val now = System.currentTimeMillis()
    messages.forEach { message ->
      val uid = message.uid ?: return@forEach
      if (
        uid > 0L &&
          message.faceUrl.isNullOrBlank() &&
          avatarCache[uid] == null &&
          (avatarRetryAfter[uid] ?: 0L) <= now
      ) {
        pendingAvatarUids += uid
      }
    }
    if (pendingAvatarUids.isEmpty() || avatarJob?.isActive == true) return
    avatarJob =
      viewModelScope.launch {
        delay(AVATAR_BATCH_DELAY_MS)
        while (pendingAvatarUids.isNotEmpty() && isCurrent(requestGeneration)) {
          val batch = pendingAvatarUids.take(50)
          pendingAvatarUids.removeAll(batch.toSet())
          val profiles =
            withContext(Dispatchers.IO) {
              runCatching { BiliApi.getMessageUsers(batch) }.getOrDefault(emptyMap())
            }
          if (!isCurrent(requestGeneration)) return@launch
          val failed = batch.filterNot(profiles::containsKey)
          val retryAt = System.currentTimeMillis() + AVATAR_FAILURE_BACKOFF_MS
          failed.forEach { avatarRetryAfter[it] = retryAt }
          profiles.forEach { (uid, profile) ->
            avatarCache[uid] = profile
            avatarRetryAfter.remove(uid)
          }
          if (profiles.isNotEmpty()) {
            _state.update { state ->
              state.copy(
                messages =
                  state.messages.map { message ->
                    val profile = message.uid?.let(profiles::get)
                    if (profile == null) message
                    else
                      message.copy(
                        uname = message.uname ?: profile.first,
                        faceUrl = message.faceUrl ?: profile.second.takeIf(String::isNotBlank),
                      )
                  }
              )
            }
          }
        }
      }
  }

  private fun richTextContent(text: String): LiveChatContent.Text {
    val emotes =
      _state.value.emojiPacks
        .asSequence()
        .flatMap { it.emojis.asSequence() }
        .filter { emoji ->
          !emoji.isBulge &&
            emoji.inputText.isNotBlank() &&
            emoji.imageUrl.isNotBlank() &&
            text.contains(emoji.inputText)
        }
        .associate { it.inputText to it.imageUrl }
    return LiveChatContent.Text(text = text, emotes = emotes)
  }

  private fun contentKey(content: LiveChatContent): String =
    when (content) {
      is LiveChatContent.Text -> "text:${content.text}"
      is LiveChatContent.Emoji -> "emoji:${content.fileId ?: content.displayName}"
      is LiveChatContent.System -> "system:${content.text}"
    }

  private fun isCurrent(requestGeneration: Long): Boolean =
    requestGeneration == generation && requestGeneration == _state.value.generation

  private fun safeSourceDescription(source: LiveStreamSource): String {
    val host =
      runCatching { Uri.parse(source.url).host }.getOrNull().orEmpty().ifBlank { "unknown" }
    return "format=${source.format} codec=${source.codec} cdn=${source.cdnIndex} host=$host"
  }

  private fun findHttpResponseCode(error: Throwable): Int? {
    var current: Throwable? = error
    repeat(8) {
      val value = current ?: return null
      if (value is HttpDataSource.InvalidResponseCodeException) return value.responseCode
      current = value.cause
    }
    return null
  }

  private fun deepestCauseName(error: Throwable): String {
    var current = error
    repeat(8) {
      val cause = current.cause ?: return current.javaClass.simpleName
      current = cause
    }
    return current.javaClass.simpleName
  }

  override fun onCleared() {
    danmakuClient?.stop()
    danmakuClient = null
    playbackJob?.cancel()
    playbackRetryJob?.cancel()
    avatarJob?.cancel()
    lotteryJob?.cancel()
  }

  private companion object {
    const val TAG = "LiveRoomPlayback"
    const val PLAY_URL_RELOAD_DEBOUNCE_MS = 2_000L
    const val MAX_MESSAGES = 20
    const val MAX_COMPOSER_LENGTH = 200
    const val MAX_AVATAR_CACHE = 400
    const val AVATAR_BATCH_DELAY_MS = 80L
    const val AVATAR_FAILURE_BACKOFF_MS = 60_000L
    const val LIVE_DANMAKU_QUEUE_CAPACITY = 128
    const val LIVE_DANMAKU_BATCH_WINDOW_MS = 110L
    const val MAX_LIVE_DANMAKU_BATCH_SIZE = 8
    const val MAX_LIVE_DANMAKU = 120
    const val LIVE_DANMAKU_ENTRY_DELAY_MS = 180L
    const val LIVE_DANMAKU_RETAIN_MS = 15_000L
    const val LOTTERY_RESULT_VISIBLE_MS = 12_000L
    const val LOTTERY_INVALID_VISIBLE_MS = 6_000L
  }
}

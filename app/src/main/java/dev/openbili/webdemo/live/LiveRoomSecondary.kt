package dev.openbili.webdemo.live

import android.app.Activity
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.DanmakuInlineEmote
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.controlFocusOutline
import dev.openbili.webdemo.ui.isControlConfirmKey
import dev.openbili.webdemo.video.AdaptiveVideoPanes
import dev.openbili.webdemo.video.BiliRichText
import dev.openbili.webdemo.video.CommentEmoteMarkerRegistry
import dev.openbili.webdemo.video.CommentTextEditor
import dev.openbili.webdemo.video.DanmakuControlIcon
import dev.openbili.webdemo.video.DanmakuOverlayView
import dev.openbili.webdemo.video.FullscreenControlIcon
import dev.openbili.webdemo.video.GestureIndicator
import dev.openbili.webdemo.video.GestureIndicatorOverlay
import dev.openbili.webdemo.video.PlaybackHeader
import dev.openbili.webdemo.video.PlaybackHeaderUiModel
import dev.openbili.webdemo.video.PlaybackPageGlassBackdrop
import dev.openbili.webdemo.video.PlaybackPageGlassSurface
import dev.openbili.webdemo.video.PlayerGestureLayer
import dev.openbili.webdemo.video.RecommendationCard
import dev.openbili.webdemo.video.floatingPlayerLayout
import dev.openbili.webdemo.video.formatCompactCount
import dev.openbili.webdemo.video.videoPageLayoutForPane
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal enum class LiveSecondaryTab(val title: String) {
  CHAT("聊天"),
  RANK("榜单"),
}

private data class AudienceRankOption(
  val title: String,
  val type: String,
  val switch: String,
)

private val audienceRankOptions =
  listOf(
    AudienceRankOption("在线·贡献", "online_rank", "contribution_rank"),
    AudienceRankOption("在线·进房", "online_rank", "entry_time_rank"),
    AudienceRankOption("今日", "daily_rank", "today_rank"),
    AudienceRankOption("昨日", "daily_rank", "yesterday_rank"),
    AudienceRankOption("本周", "weekly_rank", "current_week_rank"),
    AudienceRankOption("上周", "weekly_rank", "last_week_rank"),
    AudienceRankOption("本月", "monthly_rank", "current_month_rank"),
    AudienceRankOption("上月", "monthly_rank", "last_month_rank"),
  )

@Composable
private fun LiveRankSection(
  state: LiveRoomUiState,
  onRankTab: (LiveRankTab) -> Unit,
  onAudienceRank: (String, String) -> Unit,
  onGuardType: (Int) -> Unit,
  onLoadMoreGuards: () -> Unit,
  foregroundColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = VideoShapeTokens.Card,
    color = Color.Transparent,
    contentColor = foregroundColor,
  ) {
    val chipColors = liveAdaptiveFilterChipColors(foregroundColor)
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp)) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        items(LiveRankTab.entries) { tab ->
          FilterChip(
            selected = state.rankTab == tab,
            onClick = { onRankTab(tab) },
            colors = chipColors,
            label = {
              val suffix =
                when (tab) {
                  LiveRankTab.AUDIENCE -> state.audienceRank.countText
                  LiveRankTab.GUARD ->
                    state.guardRank.totalCount.takeIf { it > 0 }?.toString().orEmpty()
                }
              Text(if (suffix.isBlank()) tab.title else "${tab.title} $suffix")
            },
          )
        }
        if (state.rankTab == LiveRankTab.AUDIENCE) {
          items(audienceRankOptions) { option ->
            FilterChip(
              selected =
                state.audienceRank.type == option.type &&
                  state.audienceRank.switch == option.switch,
              onClick = { onAudienceRank(option.type, option.switch) },
              colors = chipColors,
              label = { Text(option.title) },
            )
          }
        } else {
          items(listOf(3 to "周榜", 4 to "月榜", 5 to "陪伴榜")) { (type, label) ->
            FilterChip(
              selected = state.guardRank.typ == type,
              onClick = { onGuardType(type) },
              colors = chipColors,
              label = { Text(label) },
            )
          }
        }
      }
      val loading =
        if (state.rankTab == LiveRankTab.AUDIENCE) state.audienceRank.isLoading
        else state.guardRank.isLoading && state.guardRank.items.isEmpty()
      val error =
        if (state.rankTab == LiveRankTab.AUDIENCE) state.audienceRank.error
        else state.guardRank.error
      val users =
        if (state.rankTab == LiveRankTab.AUDIENCE) state.audienceRank.items
        else state.guardRank.items
      Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        when {
          loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
          error != null && users.isEmpty() -> Text(error, color = MaterialTheme.colorScheme.error)
          users.isEmpty() -> Text("暂时没有榜单数据", color = foregroundColor.copy(alpha = .72f))
          else ->
            LazyRow(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(top = 5.dp),
              horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
              itemsIndexed(users, key = { _, user -> user.uid }) { index, user ->
                LiveRankUserCard(user, foregroundColor)
                if (state.rankTab == LiveRankTab.GUARD && index >= users.lastIndex - 3) {
                  LaunchedEffect(user.uid, state.guardRank.nextPage) { onLoadMoreGuards() }
                }
              }
              if (state.rankTab == LiveRankTab.GUARD && state.guardRank.isLoading) {
                item {
                  Box(Modifier.width(50.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                  }
                }
              }
            }
        }
      }
    }
  }
}

@Composable
private fun LiveRankUserCard(user: LiveRankUser, foregroundColor: Color) {
  Surface(
    modifier = Modifier.width(188.dp).fillMaxHeight(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = .26f),
    contentColor = foregroundColor,
    border = BorderStroke(1.dp, foregroundColor.copy(alpha = .16f)),
  ) {
    Row(
      Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Box(contentAlignment = Alignment.BottomEnd) {
        AsyncImage(
          model = user.faceUrl,
          contentDescription = user.name,
          modifier =
            Modifier.size(44.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant),
          contentScale = ContentScale.Crop,
        )
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
          Text(
            "#${user.rank}",
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          user.name,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        user.fanMedal?.let { FanMedalChip(it) }
        val detail =
          when {
            user.accompanyDays != null -> "陪伴 ${user.accompanyDays} 天"
            user.score != null -> "${formatCompactCount(user.score)} 贡献"
            user.guardLevel != null -> guardLevelName(user.guardLevel)
            else -> null
          }
        detail?.let {
          Text(
            it,
            style = MaterialTheme.typography.labelSmall,
            color = foregroundColor.copy(alpha = .72f),
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
internal fun LiveSecondaryPane(
  state: LiveRoomUiState,
  account: UserInfo,
  selectedTab: LiveSecondaryTab,
  onSelectedTabChange: (LiveSecondaryTab) -> Unit,
  onText: (String, Int) -> Unit,
  onSend: () -> Unit,
  onToggleEmoji: () -> Unit,
  onSelectEmojiPack: (String) -> Unit,
  onEmoji: (LiveEmoji) -> Unit,
  onJoinLottery: () -> Unit,
  onLogin: () -> Unit,
  onRankTab: (LiveRankTab) -> Unit,
  onAudienceRank: (String, String) -> Unit,
  onGuardType: (Int) -> Unit,
  onLoadMoreGuards: () -> Unit,
  glassBackdrop: PlaybackPageGlassBackdrop,
) {
  val animatedForeground by
    animateColorAsState(Color(0xFF17191E), tween(220), label = "liveSecondaryForeground")
  val chipColors = liveAdaptiveFilterChipColors(animatedForeground)
  PlaybackPageGlassSurface(
    backdrop = glassBackdrop,
    modifier = Modifier.fillMaxSize(),
    shape = VideoShapeTokens.Card,
    containerColor = Color.White.copy(alpha = .34f),
    fallbackColor = Color(0xFFF1F2F5).copy(alpha = .94f),
    border = BorderStroke(1.dp, Color.Black.copy(alpha = .12f)),
    blurRadius = 22.dp,
  ) {
    CompositionLocalProvider(
      androidx.compose.material3.LocalContentColor provides animatedForeground
    ) {
      Column(
        Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          color = Color.White.copy(alpha = .24f),
          contentColor = animatedForeground,
          border = BorderStroke(1.dp, animatedForeground.copy(alpha = .14f)),
        ) {
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            LiveSecondaryTab.entries.forEach { tab ->
              FilterChip(
                selected = selectedTab == tab,
                onClick = { onSelectedTabChange(tab) },
                colors = chipColors,
                label = { Text(tab.title) },
              )
            }
          }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
          when (selectedTab) {
            LiveSecondaryTab.CHAT ->
              LiveMessagePane(
                state = state,
                account = account,
                onText = onText,
                onSend = onSend,
                onToggleEmoji = onToggleEmoji,
                onSelectEmojiPack = onSelectEmojiPack,
                onEmoji = onEmoji,
                onJoinLottery = onJoinLottery,
                onLogin = onLogin,
                foregroundColor = animatedForeground,
              )
            LiveSecondaryTab.RANK ->
              LiveRankSection(
                state = state,
                onRankTab = onRankTab,
                onAudienceRank = onAudienceRank,
                onGuardType = onGuardType,
                onLoadMoreGuards = onLoadMoreGuards,
                foregroundColor = animatedForeground,
                modifier = Modifier.fillMaxSize(),
              )
          }
        }
      }
    }
  }
}

@Composable
private fun LiveMessagePane(
  state: LiveRoomUiState,
  account: UserInfo,
  onText: (String, Int) -> Unit,
  onSend: () -> Unit,
  onToggleEmoji: () -> Unit,
  onSelectEmojiPack: (String) -> Unit,
  onEmoji: (LiveEmoji) -> Unit,
  onJoinLottery: () -> Unit,
  onLogin: () -> Unit,
  foregroundColor: Color,
) {
  val listState = rememberLazyListState()
  val lastMessageId = state.messages.lastOrNull()?.stableId
  LaunchedEffect(lastMessageId) {
    if (state.messages.isNotEmpty()) {
      listState.animateScrollToItem(state.messages.lastIndex)
    }
  }
  Surface(
    Modifier.fillMaxSize(),
    shape = VideoShapeTokens.Card,
    color = Color.Transparent,
    contentColor = foregroundColor,
    tonalElevation = 2.dp,
  ) {
    Column(Modifier.fillMaxSize()) {
      LiveMessageHeader(state.connectionError)
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        items(state.messages, key = LiveChatMessage::stableId) { message ->
          LiveMessageCard(message)
        }
      }
      state.interactiveLottery?.let { lottery ->
        LiveInteractiveLotteryCard(
          lottery = lottery,
          onJoin = onJoinLottery,
          foregroundColor = foregroundColor,
        )
      }
      if (state.composer.emojiPanelVisible) {
        LiveEmojiPanel(
          state = state,
          onSelectPack = onSelectEmojiPack,
          onEmoji = onEmoji,
          foregroundColor = foregroundColor,
        )
      }
      Box(Modifier.imePadding().navigationBarsPadding()) {
        LiveComposer(
          state = state,
          account = account,
          onText = onText,
          onSend = onSend,
          onToggleEmoji = onToggleEmoji,
          onLogin = onLogin,
          foregroundColor = foregroundColor,
        )
      }
    }
  }
}

@Composable
private fun LiveMessageHeader(error: String?) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
    Text(
      "直播消息",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    error?.takeIf(String::isNotBlank)?.let {
      Text(
        it,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        maxLines = 2,
      )
    }
  }
}

@Composable
private fun LiveInteractiveLotteryCard(
  lottery: LiveInteractiveLottery,
  onJoin: () -> Unit,
  foregroundColor: Color,
) {
  var remainingMs by
    remember(lottery.id, lottery.endAtEpochMs) {
      mutableLongStateOf((lottery.endAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L))
    }
  LaunchedEffect(lottery.id, lottery.endAtEpochMs, lottery.status) {
    while (
      remainingMs > 0L &&
        lottery.status in
          setOf(
            LiveLotteryStatus.ACTIVE,
            LiveLotteryStatus.JOINING,
            LiveLotteryStatus.JOINED,
          )
    ) {
      delay(1_000L)
      remainingMs = (lottery.endAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
    }
  }
  val statusText =
    when (lottery.status) {
      LiveLotteryStatus.ACTIVE ->
        if (remainingMs > 0L) "剩余 ${((remainingMs + 999L) / 1_000L)} 秒" else "等待开奖"
      LiveLotteryStatus.JOINING -> "正在参与"
      LiveLotteryStatus.JOINED -> "已参与，等待开奖"
      LiveLotteryStatus.ENDED -> "已结束"
      LiveLotteryStatus.AWARDED -> "已开奖"
      LiveLotteryStatus.INVALID -> "已失效"
    }
  Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .34f),
    contentColor = foregroundColor,
    border = BorderStroke(1.dp, foregroundColor.copy(alpha = .16f)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      if (!lottery.awardImageUrl.isNullOrBlank()) {
        AsyncImage(
          model = lottery.awardImageUrl,
          contentDescription = lottery.awardName,
          modifier = Modifier.size(42.dp),
          contentScale = ContentScale.Fit,
        )
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          "天选时刻 · ${lottery.awardName} ×${lottery.awardNum}",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          lottery.requireText.ifBlank {
            lottery.command.takeIf(String::isNotBlank) ?: "发送指定弹幕参与"
          },
          style = MaterialTheme.typography.labelSmall,
          color = foregroundColor.copy(alpha = .82f),
          maxLines = 2,
        )
        Text(
          lottery.error ?: statusText,
          style = MaterialTheme.typography.labelSmall,
          color =
            if (lottery.error == null) foregroundColor.copy(alpha = .72f)
            else MaterialTheme.colorScheme.error,
          maxLines = 2,
        )
      }
      if (lottery.status == LiveLotteryStatus.ACTIVE) {
        TextButton(
          onClick = onJoin,
          enabled = remainingMs > 0L && !lottery.requiresPayment,
        ) {
          Text(if (lottery.requiresPayment) "不支持付费参与" else "参与")
        }
      }
    }
  }
}

@Composable
private fun LiveMessageCard(message: LiveChatMessage) {
  val pending = message.delivery is LiveMessageDelivery.Pending
  val messageForeground = Color.White
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color =
      if (message.content is LiveChatContent.System)
        Color.Black.copy(alpha = .48f)
      else Color.Black.copy(alpha = .38f),
    contentColor = messageForeground,
    border = BorderStroke(1.dp, Color.White.copy(alpha = .16f)),
  ) {
    if (message.content is LiveChatContent.System) {
      Text(
        message.content.text,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = messageForeground,
      )
    } else {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AsyncImage(
          model = message.faceUrl,
          contentDescription = message.uname,
          modifier =
            Modifier.size(34.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant),
          contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              message.uname ?: "用户",
              modifier = Modifier.weight(1f, fill = false),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            message.fanMedal?.let { FanMedalChip(it) }
            if (pending) {
              CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            }
          }
          when (val content = message.content) {
            is LiveChatContent.Text ->
              BiliRichText(
                text = content.text,
                emotes =
                  content.emotes.mapValues { (token, url) ->
                    BiliEmote(text = token, url = url)
                  },
                style = MaterialTheme.typography.bodyMedium,
              )
            is LiveChatContent.Emoji ->
              Column {
                AsyncImage(
                  model = content.imageUrl,
                  contentDescription = content.displayName,
                  modifier = Modifier.size(if (content.isBulge) 76.dp else 44.dp),
                  contentScale = ContentScale.Fit,
                )
                if (content.imageUrl.isNullOrBlank()) {
                  Text(content.displayName, style = MaterialTheme.typography.bodyMedium)
                }
              }
            is LiveChatContent.System -> Unit
          }
        }
      }
    }
  }
}

@Composable
private fun FanMedalChip(medal: FanMedalBadge) {
  val start = liveColor(medal.startColor ?: medal.color, MaterialTheme.colorScheme.primary)
  val end = liveColor(medal.endColor ?: medal.borderColor ?: medal.color, start)
  Text(
    "${medal.name} ${medal.level}",
    modifier =
      Modifier.clip(RoundedCornerShape(7.dp))
        .background(Brush.horizontalGradient(listOf(start, end)))
        .padding(horizontal = 6.dp, vertical = 2.dp),
    color = Color.White,
    style = MaterialTheme.typography.labelSmall,
    maxLines = 1,
  )
}

@Composable
private fun LiveEmojiPanel(
  state: LiveRoomUiState,
  onSelectPack: (String) -> Unit,
  onEmoji: (LiveEmoji) -> Unit,
  foregroundColor: Color,
) {
  val chipColors = liveAdaptiveFilterChipColors(foregroundColor)
  Surface(
    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 236.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = .28f),
    contentColor = foregroundColor,
    tonalElevation = 4.dp,
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(state.emojiPacks, key = LiveEmojiPack::id) { pack ->
          FilterChip(
            selected = state.composer.selectedEmojiPackId == pack.id,
            onClick = { onSelectPack(pack.id) },
            colors = chipColors,
            label = {
              Text(
                when (pack.kind) {
                  LiveEmojiKind.ROOM_EXCLUSIVE -> "${pack.title ?: "专属"} · 本房"
                  else -> pack.title ?: "表情"
                }
              )
            },
          )
        }
      }
      val selected =
        state.emojiPacks.firstOrNull { it.id == state.composer.selectedEmojiPackId }
          ?: state.emojiPacks.firstOrNull()
      when {
        state.emojiLoading ->
          Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
          }
        state.emojiError != null ->
          Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(state.emojiError, color = MaterialTheme.colorScheme.error)
          }
        selected == null ->
          Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text("暂无可用表情")
          }
        else ->
          LazyVerticalGrid(
            columns = GridCells.Adaptive(48.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            gridItems(selected.emojis, key = { it.fileId ?: it.sendToken }) { emoji ->
              Column(
                modifier =
                  Modifier.clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = emoji.available) { onEmoji(emoji) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                AsyncImage(
                  model = emoji.imageUrl,
                  contentDescription = emoji.displayName,
                  modifier = Modifier.size(38.dp),
                  contentScale = ContentScale.Fit,
                  alpha = if (emoji.available) 1f else .35f,
                )
                Text(
                  emoji.displayName,
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
      }
    }
  }
}

@Composable
private fun LiveComposer(
  state: LiveRoomUiState,
  account: UserInfo,
  onText: (String, Int) -> Unit,
  onSend: () -> Unit,
  onToggleEmoji: () -> Unit,
  onLogin: () -> Unit,
  foregroundColor: Color,
) {
  val editorState = rememberTextFieldState()
  val focusRequester = remember { FocusRequester() }
  val emoteRegistry = remember(state.entryRoomId) { CommentEmoteMarkerRegistry() }
  val inputEmotes =
    remember(state.emojiPacks) {
      state.emojiPacks
        .flatMap(LiveEmojiPack::emojis)
        .filter { !it.directSend && it.inputText.isNotBlank() && it.imageUrl.isNotBlank() }
        .distinctBy(LiveEmoji::inputText)
        .map { BiliEmote(text = it.inputText, url = it.imageUrl) }
    }
  val markerSnapshot = remember(inputEmotes) { emoteRegistry.snapshot(inputEmotes) }
  val latestComposer by rememberUpdatedState(state.composer)
  val latestOnText by rememberUpdatedState(onText)
  LaunchedEffect(state.composer.text, state.composer.selectionStart, markerSnapshot) {
    val encoded = markerSnapshot.encode(state.composer.text)
    val selection =
      markerSnapshot
        .encodedOffset(state.composer.text, state.composer.selectionStart)
        .coerceIn(0, encoded.length)
    if (
      editorState.text.toString() != encoded ||
        editorState.selection.start != selection ||
        editorState.selection.end != selection
    ) {
      editorState.edit {
        replace(0, length, encoded)
        this.selection = TextRange(selection)
      }
    }
  }
  LaunchedEffect(editorState, markerSnapshot) {
    snapshotFlow { editorState.text.toString() to editorState.selection.start }
      .collectLatest { (encoded, selection) ->
        val decoded = markerSnapshot.decode(encoded)
        val decodedSelection = markerSnapshot.decodedOffset(encoded, selection)
        if (decoded != latestComposer.text || decodedSelection != latestComposer.selectionStart) {
          latestOnText(decoded, decodedSelection)
        }
      }
  }
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface.copy(alpha = .28f),
    contentColor = foregroundColor,
    tonalElevation = 5.dp,
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp)) {
      state.composer.error?.let {
        Text(
          it,
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
          maxLines = 2,
        )
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        state.activeMedal?.let { FanMedalChip(it) }
        IconButton(onClick = onToggleEmoji) {
          Text("☺", style = MaterialTheme.typography.titleLarge)
        }
        Surface(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = .34f),
          border = BorderStroke(1.dp, foregroundColor.copy(alpha = .18f)),
        ) {
          CommentTextEditor(
            state = editorState,
            placeholder = if (account.isLogin) "发个弹幕呗~" else "登录后发送弹幕",
            emoteMarkers = markerSnapshot.markerToEmote,
            focusRequester = focusRequester,
            enabled = account.isLogin && !state.composer.sending,
            contentColor = foregroundColor,
            placeholderColor = foregroundColor.copy(alpha = .68f),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp, max = 54.dp),
          )
        }
        FilledIconButton(
          onClick = if (account.isLogin) onSend else onLogin,
          enabled =
            !state.composer.sending && (!account.isLogin || state.composer.text.isNotBlank()),
        ) {
          if (state.composer.sending) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
          }
        }
      }
    }
  }
}

@Composable
internal fun LiveRoomInfoDialog(
  state: LiveRoomUiState,
  onDismiss: () -> Unit,
) {
  val room = state.roomInfo
  val anchor = state.anchorInfo
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(.72f).heightIn(max = 580.dp),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 8.dp,
      shadowElevation = 0.dp,
    ) {
      Column(
        Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(
          room?.title ?: "直播间",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          AsyncImage(
            model = anchor?.faceUrl,
            contentDescription = anchor?.name,
            modifier =
              Modifier.size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
          )
          Text(
            anchor?.name ?: "主播",
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.titleMedium,
          )
        }
        Text(
          listOfNotNull(room?.parentAreaName, room?.areaName, room?.roomId?.let { "房间号 $it" })
            .joinToString("  ·  "),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          room?.description?.ifBlank { "这个直播间暂时没有填写简介。" } ?: "这个直播间暂时没有填写简介。",
          modifier = Modifier.weight(1f, fill = false),
          style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
          Text("关闭")
        }
      }
    }
  }
}

private fun liveColor(value: Long?, fallback: Color): Color =
  value?.takeIf { it > 0L }?.let { Color((0xff000000L or (it and 0x00ffffffL)).toInt()) }
    ?: fallback

@Composable
private fun liveAdaptiveFilterChipColors(foregroundColor: Color) =
  FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .18f),
    labelColor = foregroundColor,
    iconColor = foregroundColor,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .72f),
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
  )

private fun guardLevelName(level: Int): String =
  when (level) {
    1 -> "总督"
    2 -> "提督"
    3 -> "舰长"
    else -> "大航海"
  }

internal fun Player?.isLiveBuffering(): Boolean =
  this == null || playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING
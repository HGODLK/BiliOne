package dev.openbili.webdemo.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.music.HomeMusicPlayerViewModel
import dev.openbili.webdemo.music.HomeMusicUiState
import dev.openbili.webdemo.music.MusicLibraryStatus
import dev.openbili.webdemo.music.displayTitle
import kotlinx.coroutines.launch

@Composable
internal fun MusicTrackList(
  state: HomeMusicUiState,
  viewModel: HomeMusicPlayerViewModel,
  controlFocusRequest: Int,
  controlDismissTransientRequest: Int,
  controlFocusable: Boolean,
  searchFocusRequester: FocusRequester,
  onControlReturnToPlayer: () -> Unit,
  onControlFocused: () -> Unit,
  onControlTransientOpenChanged: (Boolean) -> Unit,
) {
  val listState = rememberLazyListState()
  val context = LocalContext.current
  val hostView = LocalView.current
  val controlMode = LocalControlMode.current
  val scope = rememberCoroutineScope()
  val cardFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
  val deleteFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
  val cancelFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
  var lastControlItemId by remember { mutableStateOf<String?>(null) }
  var actionItemOriginalIndex by remember { mutableIntStateOf(-1) }
  var deleteCandidateId by remember { mutableStateOf<String?>(null) }
  var actionMenuItemId by remember { mutableStateOf<String?>(null) }
  var renameItem by remember { mutableStateOf<FeedItem?>(null) }
  var aliasDraft by remember { mutableStateOf("") }
  var locateCurrentPending by remember { mutableStateOf(false) }
  BackHandler(enabled = controlMode && actionMenuItemId != null) {
    val itemId = actionMenuItemId
    if (itemId in state.deletingItemIds) return@BackHandler
    actionMenuItemId = null
    itemId?.let { id ->
      scope.launch {
        withFrameNanos {}
        runCatching { cardFocusRequesters[id]?.requestFocus() }
      }
    }
  }
  LaunchedEffect(actionMenuItemId, controlMode) {
    onControlTransientOpenChanged(controlMode && actionMenuItemId != null)
    val itemId = actionMenuItemId ?: return@LaunchedEffect
    if (!controlMode) return@LaunchedEffect
    withFrameNanos {}
    runCatching { cancelFocusRequesters[itemId]?.requestFocus() }
  }
  LaunchedEffect(controlDismissTransientRequest) {
    if (controlDismissTransientRequest <= 0 || actionMenuItemId == null) return@LaunchedEffect
    val itemId = actionMenuItemId
    if (itemId in state.deletingItemIds) return@LaunchedEffect
    actionMenuItemId = null
    withFrameNanos {}
    itemId?.let { runCatching { cardFocusRequesters[it]?.requestFocus() } }
  }
  LaunchedEffect(controlFocusRequest, controlMode) {
    if (!controlMode || !controlFocusable || controlFocusRequest <= 0) return@LaunchedEffect
    val targetId =
      resolveMusicTrackFocusId(
        lastFocusedItemId = lastControlItemId,
        currentItemId = state.currentItem?.id,
        itemIds = state.items.map { it.id },
      )
    if (targetId == null) {
      withFrameNanos {}
      runCatching { searchFocusRequester.requestFocus() }
      return@LaunchedEffect
    }
    val targetIndex = state.items.indexOfFirst { it.id == targetId }
    if (targetIndex >= 0) listState.scrollToItem(targetIndex)
    withFrameNanos {}
    runCatching { cardFocusRequesters[targetId]?.requestFocus() }
  }
  LaunchedEffect(state.items, deleteCandidateId) {
    if (deleteCandidateId != null && state.items.none { it.id == deleteCandidateId }) {
      deleteCandidateId = null
    }
  }
  LaunchedEffect(state.items, actionMenuItemId) {
    val removedActionId = actionMenuItemId ?: return@LaunchedEffect
    if (state.items.any { it.id == removedActionId }) return@LaunchedEffect
    actionMenuItemId = null
    val focusIndex = musicFocusIndexAfterRemoval(actionItemOriginalIndex, state.items.size)
    if (focusIndex == null) {
      withFrameNanos {}
      runCatching { searchFocusRequester.requestFocus() }
    } else {
      val target = state.items[focusIndex]
      lastControlItemId = target.id
      listState.scrollToItem(focusIndex)
      withFrameNanos {}
      runCatching { cardFocusRequesters[target.id]?.requestFocus() }
    }
  }
  val loadMore by
    remember(state.items.size, state.hasMore) {
      derivedStateOf {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        state.hasMore && last >= state.items.lastIndex - 3
      }
    }
  LaunchedEffect(loadMore) {
    if (loadMore) viewModel.loadMore()
  }
  LaunchedEffect(
    locateCurrentPending,
    state.libraryStatus,
    state.query,
    state.currentItem?.id,
    state.items,
    state.loadingMore,
    state.hasMore,
  ) {
    if (
      !locateCurrentPending ||
        state.query.isNotBlank() ||
        state.libraryStatus != MusicLibraryStatus.READY
    ) {
      return@LaunchedEffect
    }
    val currentIndex = state.items.indexOfFirst { it.id == state.currentItem?.id }
    when {
      currentIndex >= 0 -> {
        listState.scrollToItem(currentIndex)
        locateCurrentPending = false
      }
      state.hasMore && !state.loadingMore -> viewModel.loadMore()
      !state.loadingMore -> locateCurrentPending = false
    }
  }
  Box(Modifier.fillMaxSize()) {
    if (state.items.isEmpty()) {
      MusicLibraryMessage(title = "没有找到相关音乐")
    }
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 58.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
        val deleteSelected = deleteCandidateId == item.id
        val actionSelected = actionMenuItemId == item.id
        val deleting = item.id in state.deletingItemIds
        val playing = state.currentItem?.id == item.id
        val title = displayTitle(item, state.displayNameOverrides)
        val cardFocusRequester = remember(item.id) { FocusRequester() }
        val cancelFocusRequester = remember(item.id) { FocusRequester() }
        val deleteFocusRequester = remember(item.id) { FocusRequester() }
        SideEffect {
          cardFocusRequesters[item.id] = cardFocusRequester
          cancelFocusRequesters[item.id] = cancelFocusRequester
          deleteFocusRequesters[item.id] = deleteFocusRequester
        }
        val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
        fun closeControlActions() {
          actionMenuItemId = null
          scope.launch {
            withFrameNanos {}
            runCatching { cardFocusRequester.requestFocus() }
          }
        }
        var wasDeleting by remember(item.id) { mutableStateOf(false) }
        LaunchedEffect(deleting, actionSelected, controlMode) {
          if (controlMode && wasDeleting && !deleting && actionSelected) {
            withFrameNanos {}
            runCatching { cancelFocusRequester.requestFocus() }
          }
          wasDeleting = deleting
        }
        val cardBlur by
          animateDpAsState(
            targetValue = if (deleteSelected || actionSelected) 14.dp else 0.dp,
            animationSpec = tween(150),
            label = "musicActionCardBlur",
          )
        Box(Modifier.fillMaxWidth()) {
          Surface(
            modifier =
              Modifier.fillMaxWidth()
                .blur(cardBlur)
                .clip(RoundedCornerShape(17.dp))
                .then(
                  if (controlMode) {
                    Modifier.focusRequester(cardFocusRequester)
                      .focusProperties {
                        canFocus =
                          controlFocusable && !deleteSelected && !actionSelected && !deleting
                      }
                      .navigationBringIntoViewTarget(bringIntoViewRequester)
                      .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                          lastControlItemId = item.id
                          onControlFocused()
                          scope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                      }
                      .onPreviewKeyEvent { event ->
                        if (!controlFocusable) return@onPreviewKeyEvent true
                        val keyCode = event.nativeKeyEvent.keyCode
                        if (isControlConfirmKey(keyCode)) {
                          if (event.type == KeyEventType.KeyUp) {
                            when (resolveMusicCardConfirmAction(item.id, state.currentItem?.id)) {
                              MusicCardConfirmAction.PLAY -> viewModel.selectItem(item)
                              MusicCardConfirmAction.OPEN_DELETE_ACTIONS -> {
                                actionItemOriginalIndex = index
                                actionMenuItemId = item.id
                              }
                            }
                          }
                          return@onPreviewKeyEvent true
                        }
                        if (event.type != KeyEventType.KeyDown) {
                          return@onPreviewKeyEvent false
                        }
                        when (keyCode) {
                          KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onControlReturnToPlayer()
                            true
                          }
                          KeyEvent.KEYCODE_DPAD_UP -> {
                            if (index == 0) runCatching { searchFocusRequester.requestFocus() }
                            index == 0
                          }
                          KeyEvent.KEYCODE_DPAD_RIGHT -> true
                          else -> false
                        }
                      }
                      .musicFocusChrome(
                        shape = RoundedCornerShape(17.dp),
                        color = MaterialTheme.colorScheme.primary,
                        width = 4.dp,
                        fill = MaterialTheme.colorScheme.primary.copy(alpha = .20f),
                      )
                      .focusable(enabled = controlFocusable && !actionSelected && !deleting)
                  } else {
                    Modifier.combinedClickable(
                      enabled = !deleteSelected && !actionSelected && !deleting,
                      onClick = { viewModel.selectItem(item) },
                      onLongClick = {
                        performMusicDeleteVibration(context, hostView)
                        actionMenuItemId = item.id
                      },
                    )
                  }
                ),
            shape = RoundedCornerShape(17.dp),
            color = Color.Transparent,
            border =
              BorderStroke(
                if (playing) 2.75.dp else .5.dp,
                if (playing) MaterialTheme.colorScheme.primary.copy(alpha = .96f)
                else Color.White.copy(alpha = .12f),
              ),
          ) {
            VideoCardGradient(
              coverUrl = item.coverUrl,
              overlayStyle = true,
              backgroundAlpha = .68f,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                CoverImage(
                  coverUrl = item.coverUrl,
                  contentDescription = title,
                  modifier = Modifier.width(112.dp).aspectRatio(16f / 9f),
                  shape = RoundedCornerShape(12.dp),
                  requestWidth = 448,
                  requestHeight = 252,
                  loadKey = "home-music-list-${item.id}",
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    listOfNotNull(item.uploader, item.duration).joinToString(" · ").ifBlank { " " },
                    color = Color.White.copy(alpha = .64f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
          if (playing && !deleteSelected && !actionSelected) {
            val highlightShape = RoundedCornerShape(17.dp)
            Box(
              Modifier.matchParentSize()
                .clip(highlightShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .08f))
                .border(2.75.dp, MaterialTheme.colorScheme.primary, highlightShape)
                .padding(3.5.dp)
                .border(
                  .9.dp,
                  Color.White.copy(alpha = .78f),
                  RoundedCornerShape(13.5.dp),
                )
            )
          }
          if (controlMode) {
            AnimatedVisibility(
              visible = actionSelected,
              modifier = Modifier.matchParentSize(),
              enter = fadeIn(tween(140)),
              exit = fadeOut(tween(100)),
            ) {
              Row(
                Modifier.fillMaxSize()
                  .clip(RoundedCornerShape(17.dp))
                  .background(Color.Black.copy(alpha = .24f))
                  .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Button(
                  onClick = { viewModel.removeFromMusicFolder(item) },
                  enabled = actionSelected && !deleting,
                  modifier =
                    Modifier.height(44.dp)
                      .focusRequester(deleteFocusRequester)
                      .focusProperties {
                        canFocus = actionSelected && !deleting
                        left = FocusRequester.Cancel
                        right = cancelFocusRequester
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                      }
                      .onFocusChanged { if (it.isFocused) onControlFocused() }
                      .musicFocusChrome(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.error,
                        width = 4.dp,
                        fill = MaterialTheme.colorScheme.error.copy(alpha = .28f),
                      ),
                ) {
                  if (deleting) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(16.dp),
                      strokeWidth = 2.dp,
                    )
                  } else {
                    Text("删除")
                  }
                }
                TextButton(
                  onClick = ::closeControlActions,
                  enabled = actionSelected && !deleting,
                  modifier =
                    Modifier.height(44.dp)
                      .focusRequester(cancelFocusRequester)
                      .focusProperties {
                        canFocus = actionSelected && !deleting
                        left = deleteFocusRequester
                        right = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                      }
                      .onFocusChanged { if (it.isFocused) onControlFocused() }
                      .musicFocusChrome(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        width = 4.dp,
                        fill = MaterialTheme.colorScheme.primary.copy(alpha = .28f),
                      ),
                ) {
                  Text("取消", color = Color.White)
                }
              }
            }
          } else if (actionSelected) {
            Row(
              Modifier.matchParentSize()
                .clip(RoundedCornerShape(17.dp))
                .background(Color.Black.copy(alpha = .20f))
                .padding(horizontal = 12.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              TextButton(
                onClick = {
                  aliasDraft = state.displayNameOverrides[item.id].orEmpty()
                  renameItem = item
                  actionMenuItemId = null
                }
              ) {
                Text("备注显示名称", color = Color.White)
              }
              Button(
                onClick = {
                  deleteCandidateId = item.id
                  actionMenuItemId = null
                }
              ) {
                Text("从音乐收藏夹删除")
              }
              TextButton(onClick = { actionMenuItemId = null }) {
                Text("取消", color = Color.White)
              }
            }
          }
          if (deleteSelected) {
            Row(
              Modifier.matchParentSize()
                .clip(RoundedCornerShape(17.dp))
                .background(Color.Black.copy(alpha = .18f))
                .padding(horizontal = 18.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              TextButton(
                onClick = { deleteCandidateId = null },
                enabled = !deleting,
              ) {
                Text("取消", color = Color.White)
              }
              Button(
                onClick = { viewModel.removeFromMusicFolder(item) },
                enabled = !deleting,
              ) {
                if (deleting) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                  )
                } else {
                  Text("删除")
                }
              }
            }
          }
        }
      }
      if (state.loadingMore) {
        item(key = "music_loading_more") {
          Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = Color.White,
              strokeWidth = 2.dp,
            )
          }
        }
      }
    }
    renameItem?.let { item ->
      val currentAlias = state.displayNameOverrides[item.id].orEmpty()
      AlertDialog(
        onDismissRequest = { renameItem = null },
        title = { Text("备注显示名称") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              "原视频名：${item.title}",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            TextField(
              value = aliasDraft,
              onValueChange = { aliasDraft = it },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              label = { Text("显示名称") },
            )
          }
        },
        confirmButton = {
          TextButton(
            onClick = {
              viewModel.setDisplayName(item, aliasDraft)
              renameItem = null
            }
          ) {
            Text("保存")
          }
        },
        dismissButton = {
          Row {
            if (currentAlias.isNotBlank()) {
              TextButton(
                onClick = {
                  viewModel.setDisplayName(item, "")
                  renameItem = null
                }
              ) {
                Text("恢复原名")
              }
            }
            TextButton(onClick = { renameItem = null }) { Text("取消") }
          }
        },
      )
    }
    state.currentItem?.let {
      Surface(
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .padding(8.dp)
            .size(42.dp)
            .clip(CircleShape)
            .focusProperties { canFocus = !controlMode }
            .clickable(
              enabled = !locateCurrentPending,
              onClickLabel = "定位到当前播放的视频",
            ) {
              locateCurrentPending = true
              if (state.query.isNotBlank()) viewModel.clearQueryForCurrentTrack()
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = .88f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(.75.dp, Color.White.copy(alpha = .24f)),
        shadowElevation = 4.dp,
      ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          if (locateCurrentPending) {
            CircularProgressIndicator(
              modifier = Modifier.size(19.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          } else {
            Icon(
              Icons.Default.MyLocation,
              contentDescription = null,
              modifier = Modifier.size(21.dp),
            )
          }
        }
      }
    }
  }
}

private fun performMusicDeleteVibration(context: Context, fallbackView: android.view.View) {
  val vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  if (vibrator?.hasVibrator() == true) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      vibrator.vibrate(VibrationEffect.createOneShot(42L, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
      @Suppress("DEPRECATION") vibrator.vibrate(42L)
    }
  } else {
    fallbackView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
  }
}

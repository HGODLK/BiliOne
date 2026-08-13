package dev.openbili.webdemo.my

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.feed.FeedCardContent
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedNavigationFlingTracker
import dev.openbili.webdemo.feed.bringFeedItemIntoSafeViewport
import dev.openbili.webdemo.feed.settleFeedForNavigation
import dev.openbili.webdemo.offline.OfflineMediaEntry
import dev.openbili.webdemo.offline.OfflineMediaKind
import dev.openbili.webdemo.offline.OfflineMediaManager
import dev.openbili.webdemo.offline.OfflineMediaSnapshot
import dev.openbili.webdemo.offline.OfflineStorageLocation
import dev.openbili.webdemo.offline.OfflineStorageMigrationProgress
import dev.openbili.webdemo.offline.OfflineTransferState
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import dev.openbili.webdemo.ui.VideoShapeTokens
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CachedVideosPane(
  user: UserInfo,
  onVideo: (FeedItem, Rect) -> Unit,
  onBangumi: (SpaceContentCard, FeedItem, Rect) -> Unit,
  hiddenCoverItemId: String?,
  columns: Int,
  backHandlingEnabled: Boolean,
) {
  val context = LocalContext.current
  val manager = remember(context) { OfflineMediaManager.get(context) }
  val scope = rememberCoroutineScope()
  var snapshots by remember { mutableStateOf<List<OfflineMediaSnapshot>>(emptyList()) }
  var usedBytes by remember { mutableStateOf(0L) }
  var wifiOnly by remember { mutableStateOf(manager.wifiOnly) }
  var storageLocations by remember { mutableStateOf(manager.availableStorageLocations()) }
  var storageMenuExpanded by remember { mutableStateOf(false) }
  var pendingStorage by remember { mutableStateOf<OfflineStorageLocation?>(null) }
  var migrationProgress by remember { mutableStateOf<OfflineStorageMigrationProgress?>(null) }
  var migrationError by remember { mutableStateOf<String?>(null) }
  var showBatchDelete by remember { mutableStateOf(false) }
  var deleteCandidate by remember { mutableStateOf<OfflineMediaSnapshot?>(null) }
  val groups = remember(snapshots) { snapshots.toOfflineGroups() }
  val groupBounds = remember { mutableMapOf<String, Rect>() }
  var selectedGroupId by remember { mutableStateOf<String?>(null) }
  var displayedGroupId by remember { mutableStateOf<String?>(null) }
  var detailSourceBounds by remember { mutableStateOf(Rect.Zero) }
  var detailTargetBounds by remember { mutableStateOf(Rect.Zero) }
  var detailContentReady by remember { mutableStateOf(false) }
  val detailProgress = remember { Animatable(0f) }
  val gridState = rememberLazyGridState()
  val flingTracker = remember(gridState) { FeedNavigationFlingTracker() }

  LaunchedEffect(manager) {
    var storageRefreshTicks = 0
    while (isActive) {
      snapshots = withContext(Dispatchers.IO) { manager.snapshots() }
      usedBytes = withContext(Dispatchers.IO) { manager.totalBytes() }
      if (storageRefreshTicks++ % 4 == 0) {
        storageLocations = withContext(Dispatchers.IO) { manager.availableStorageLocations() }
      }
      delay(500L)
    }
  }

  LaunchedEffect(selectedGroupId) {
    if (selectedGroupId != null) {
      displayedGroupId = selectedGroupId
      detailContentReady = false
      detailProgress.snapTo(0f)
      repeat(3) {
        withFrameNanosCompat()
        if (detailTargetBounds.width > 1f && detailTargetBounds.height > 1f) return@repeat
      }
      detailProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
      detailContentReady = true
    } else if (displayedGroupId != null) {
      groupBounds[displayedGroupId]?.takeIf(Rect::hasUsableSize)?.let { detailSourceBounds = it }
      detailContentReady = false
      withFrameNanosCompat()
      withFrameNanosCompat()
      detailProgress.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
      displayedGroupId = null
      detailSourceBounds = Rect.Zero
    }
  }

  BackHandler(enabled = displayedGroupId != null && backHandlingEnabled) {
    selectedGroupId = null
  }

  fun startMigration(target: OfflineStorageLocation) {
    migrationProgress = OfflineStorageMigrationProgress(0L, usedBytes)
    scope.launch {
      manager
        .migrateStorage(target) { progress -> migrationProgress = progress }
        .onSuccess {
          storageLocations = manager.availableStorageLocations()
          usedBytes = manager.totalBytes()
        }
        .onFailure { migrationError = it.message ?: "缓存迁移失败" }
      migrationProgress = null
    }
  }

  Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Column(Modifier.weight(1f)) {
          Text("缓存视频", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
          Text(
            buildString {
              append("占用空间 ${formatOfflineBytes(usedBytes)}")
              if (!manager.storageAvailable()) append(" · ${manager.currentStorageLabel()}")
            },
            color =
              if (manager.storageAvailable()) MaterialTheme.colorScheme.onSurfaceVariant
              else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        if (storageLocations.any(OfflineStorageLocation::removable)) {
          Box {
            OutlinedButton(onClick = { storageMenuExpanded = true }) {
              Text(manager.currentStorageLabel())
            }
            DropdownMenu(
              expanded = storageMenuExpanded,
              onDismissRequest = { storageMenuExpanded = false },
            ) {
              storageLocations.forEach { location ->
                DropdownMenuItem(
                  text = {
                    Column {
                      Text(if (location.selected) "✓ ${location.label}" else location.label)
                      Text(
                        "可用 ${formatOfflineBytes(location.availableBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  },
                  onClick = {
                    storageMenuExpanded = false
                    if (!location.selected) {
                      if (snapshots.isEmpty()) startMigration(location)
                      else pendingStorage = location
                    }
                  },
                )
              }
            }
          }
        }
        Text("仅 Wi‑Fi 下载", style = MaterialTheme.typography.bodyMedium)
        Switch(
          checked = wifiOnly,
          onCheckedChange = { enabled ->
            wifiOnly = enabled
            manager.setWifiOnly(enabled)
          },
        )
        IconButton(
          enabled = snapshots.isNotEmpty() && migrationProgress == null,
          onClick = { showBatchDelete = true },
        ) {
          Icon(Icons.Default.Delete, contentDescription = "批量删除缓存")
        }
      }

      if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("还没有缓存视频", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Fixed(columns.coerceIn(3, 6)),
          state = gridState,
          modifier = Modifier.fillMaxSize().nestedScroll(flingTracker),
          contentPadding = PaddingValues(bottom = NavigationCardBottomClearance),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(groups, key = OfflineMediaGroup::id) { group ->
            val aggregate = remember(group.snapshots) { group.aggregateSnapshot() }
            OfflineFeedCard(
              snapshot = aggregate,
              item = group.toFeedItem(manager.rootDirectory),
              coverVisible =
                group.expandable ||
                  group.snapshots.single().entry.toFeedItem(manager.rootDirectory).id !=
                    hiddenCoverItemId,
              canPlay = group.snapshots.all { manager.canPlay(it.entry, user.mid, user.vipActive) },
              collectionCount = group.snapshots.size,
              navigationKey = group.id,
              gridState = gridState,
              flingTracker = flingTracker,
              onClick = { coverBounds, cardBounds ->
                if (group.expandable) {
                  detailSourceBounds = cardBounds
                  selectedGroupId = group.id
                } else {
                  val child = group.snapshots.single()
                  openCachedEntry(child, manager, onVideo, onBangumi, coverBounds)
                }
              },
              onPause = { group.snapshots.forEach { manager.pause(it.entry.id) } },
              onResume = { group.snapshots.forEach { manager.resume(it.entry.id) } },
              onDelete = {
                if (group.snapshots.size == 1) deleteCandidate = group.snapshots.single()
                else showBatchDelete = true
              },
              onCardBoundsChanged = { bounds -> groupBounds[group.id] = bounds },
            )
          }
        }
      }
    }

    displayedGroupId?.let { id ->
      groups.firstOrNull { it.id == id }?.let { group ->
        val collectionGridState = rememberLazyGridState()
        val collectionFlingTracker = remember(collectionGridState) { FeedNavigationFlingTracker() }
        BoxWithConstraints(Modifier.fillMaxSize().zIndex(10f)) {
          Box(
            Modifier.offset(12.dp, 12.dp)
              .size(
                width = (maxWidth - 24.dp).coerceAtLeast(1.dp),
                height = (maxHeight - 24.dp).coerceAtLeast(1.dp),
              )
              .onGloballyPositioned { detailTargetBounds = it.boundsInRoot() }
          ) {
            OfflineCollectionTransition(
              sourceBounds = detailSourceBounds,
              targetBounds = detailTargetBounds,
              progress = detailProgress.value,
              contentReady = detailContentReady,
            ) {
              Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(Modifier.weight(1f)) {
                    Text(group.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                      "${group.snapshots.size} 个${if (group.kind == OfflineMediaKind.BANGUMI) "剧集" else "分 P"}",
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  IconButton(onClick = { selectedGroupId = null }) {
                    Icon(Icons.Default.Close, contentDescription = "收起合集")
                  }
                }
                LazyVerticalGrid(
                  columns = GridCells.Fixed(columns.coerceIn(3, 6)),
                  state = collectionGridState,
                  modifier = Modifier.fillMaxSize().nestedScroll(collectionFlingTracker),
                  contentPadding = PaddingValues(bottom = NavigationCardBottomClearance),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  items(group.snapshots, key = { it.entry.id }) { snapshot ->
                    val item = remember(snapshot.entry, manager.rootDirectory) {
                      snapshot.entry.toExpandedFeedItem(manager.rootDirectory)
                    }
                    OfflineFeedCard(
                      snapshot = snapshot,
                      item = item,
                      coverVisible = item.id != hiddenCoverItemId,
                      canPlay = manager.canPlay(snapshot.entry, user.mid, user.vipActive),
                      collectionCount = 1,
                      navigationKey = snapshot.entry.id,
                      gridState = collectionGridState,
                      flingTracker = collectionFlingTracker,
                      onClick = { coverBounds, _ ->
                        openCachedEntry(
                          snapshot,
                          manager,
                          onVideo,
                          onBangumi,
                          coverBounds,
                          displayItem = item,
                        )
                      },
                      onPause = { manager.pause(snapshot.entry.id) },
                      onResume = { manager.resume(snapshot.entry.id) },
                      onDelete = { deleteCandidate = snapshot },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  pendingStorage?.let { target ->
    AlertDialog(
      onDismissRequest = { pendingStorage = null },
      title = { Text("迁移缓存到${target.label}？") },
      text = {
        Text("现有视频、音频、封面、弹幕、字幕和任务状态会先完整迁移并校验；取消则继续使用当前存储位置。")
      },
      dismissButton = { TextButton(onClick = { pendingStorage = null }) { Text("取消") } },
      confirmButton = {
        Button(
          onClick = {
            pendingStorage = null
            startMigration(target)
          }
        ) {
          Text("开始迁移")
        }
      },
    )
  }

  migrationProgress?.let { progress ->
    AlertDialog(
      onDismissRequest = {},
      title = { Text("正在迁移缓存") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
          Text("${formatOfflineBytes(progress.copiedBytes)} / ${formatOfflineBytes(progress.totalBytes)}")
          Text("校验完成前不会删除原缓存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      },
      confirmButton = {},
    )
  }

  migrationError?.let { message ->
    AlertDialog(
      onDismissRequest = { migrationError = null },
      title = { Text("缓存迁移失败") },
      text = { Text(message) },
      confirmButton = { TextButton(onClick = { migrationError = null }) { Text("知道了") } },
    )
  }

  if (showBatchDelete) {
    BatchDeleteDialog(
      groups = groups,
      onDismiss = { showBatchDelete = false },
      onDelete = { ids ->
        ids.forEach(manager::remove)
        showBatchDelete = false
      },
    )
  }

  deleteCandidate?.let { snapshot ->
    AlertDialog(
      onDismissRequest = { deleteCandidate = null },
      title = { Text("删除缓存？") },
      text = { Text("会删除《${snapshot.entry.title}》${snapshot.entry.partTitle}的缓存内容。") },
      dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("取消") } },
      confirmButton = {
        TextButton(
          onClick = {
            manager.remove(snapshot.entry.id)
            deleteCandidate = null
          }
        ) {
          Text("删除", color = MaterialTheme.colorScheme.error)
        }
      },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OfflineFeedCard(
  snapshot: OfflineMediaSnapshot,
  item: FeedItem,
  coverVisible: Boolean,
  canPlay: Boolean,
  collectionCount: Int,
  navigationKey: String,
  gridState: LazyGridState,
  flingTracker: FeedNavigationFlingTracker,
  onClick: (coverBounds: Rect, cardBounds: Rect) -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onDelete: () -> Unit,
  onCardBoundsChanged: (Rect) -> Unit = {},
) {
  var coverBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  var cardBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
  var menuExpanded by remember(item.id) { mutableStateOf(false) }
  val unavailable = snapshot.state == OfflineTransferState.UNAVAILABLE || !canPlay
  val clickEnabled = collectionCount > 1 || (snapshot.state == OfflineTransferState.COMPLETED && canPlay)
  val scope = rememberCoroutineScope()
  val bottomClearancePx = with(LocalDensity.current) { NavigationCardBottomClearance.roundToPx() }
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) .98f else 1f,
      animationSpec = spring(dampingRatio = .82f, stiffness = 700f),
      label = "cachedCardPress",
    )
  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .then(
          if (pressed || scale != 1f) {
            Modifier.graphicsLayer {
              scaleX = scale
              scaleY = scale
            }
          } else Modifier
        )
        .onGloballyPositioned {
          cardBounds = it.boundsInRoot()
          onCardBoundsChanged(cardBounds)
        }
        .combinedClickable(
          enabled = true,
          interactionSource = interactionSource,
          indication = LocalIndication.current,
          onClick = {
            if (clickEnabled) {
              scope.launch {
                settleFeedForNavigation(gridState, flingTracker)
                bringFeedItemIntoSafeViewport(
                  gridState = gridState,
                  itemKey = navigationKey,
                  topClearancePx = 0,
                  bottomClearancePx = bottomClearancePx,
                )
                withFrameNanosCompat()
                withFrameNanosCompat()
                onClick(coverBounds, cardBounds)
              }
            }
          },
          onLongClick = { menuExpanded = true },
        )
        .testTag("feed_card"),
    shape = VideoShapeTokens.Card,
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    shadowElevation = 0.dp,
  ) {
    Box(Modifier.fillMaxWidth()) {
      Box(
        Modifier.fillMaxWidth()
          .then(if (unavailable) Modifier.blur(11.dp) else Modifier)
          .then(if (unavailable) Modifier.graphicsLayer { alpha = .42f } else Modifier)
      ) {
        FeedCardContent(
          item = item,
          coverVisible = coverVisible,
          profileClickEnabled = false,
          onCoverBoundsChanged = { coverBounds = it },
          statsTextOverride = offlineStateLabel(snapshot, unavailable),
          publishDateTextOverride =
            if (collectionCount > 1) "$collectionCount 项 · ${formatOfflineBytes(snapshot.bytesDownloaded)}"
            else "${snapshot.entry.qualityLabel} · ${formatOfflineBytes(snapshot.bytesDownloaded)}",
          durationTextOverride = item.duration,
          coverOverlay = {
            if (snapshot.state !in setOf(OfflineTransferState.COMPLETED, OfflineTransferState.UNAVAILABLE)) {
              LinearProgressIndicator(
                progress = { snapshot.progressPercent / 100f },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
              )
            }
          },
        )
      }
      if (unavailable) {
        Text(
          if (snapshot.failureReason.contains("SD")) "存储设备不可用" else "缓存不可用",
          modifier = Modifier.align(Alignment.Center).padding(18.dp),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Box(Modifier.align(Alignment.TopEnd)) {
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
          when (snapshot.state) {
            OfflineTransferState.DOWNLOADING,
            OfflineTransferState.QUEUED,
            OfflineTransferState.PREPARING ->
              DropdownMenuItem(
                text = { Text("暂停") },
                leadingIcon = { Icon(Icons.Default.Pause, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  onPause()
                },
              )
            OfflineTransferState.PAUSED,
            OfflineTransferState.FAILED ->
              DropdownMenuItem(
                text = { Text("继续") },
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  onResume()
                },
              )
            else -> Unit
          }
          DropdownMenuItem(
            text = { Text("删除") },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onDelete()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun OfflineCollectionTransition(
  sourceBounds: Rect,
  targetBounds: Rect,
  progress: Float,
  contentReady: Boolean,
  content: @Composable () -> Unit,
) {
  val valid = sourceBounds.hasUsableSize() && targetBounds.hasUsableSize()
  val startScaleX = if (valid) sourceBounds.width / targetBounds.width else .96f
  val startScaleY = if (valid) sourceBounds.height / targetBounds.height else .96f
  val startX = if (valid) sourceBounds.left - targetBounds.left else 0f
  val startY = if (valid) sourceBounds.top - targetBounds.top else 0f
  Box(
    Modifier.fillMaxSize().graphicsLayer {
      val value = progress.coerceIn(0f, 1f)
      transformOrigin = TransformOrigin(0f, 0f)
      scaleX = startScaleX + (1f - startScaleX) * value
      scaleY = startScaleY + (1f - startScaleY) * value
      translationX = startX * (1f - value)
      translationY = startY * (1f - value)
      alpha = if (valid) (value * 2.5f).coerceIn(0f, 1f) else value
    }
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.background,
      tonalElevation = 1.dp,
      shadowElevation = 0.dp,
    ) {
      if (contentReady) {
        AnimatedVisibility(
          visible = true,
          enter = fadeIn(tween(150)),
        ) {
          content()
        }
      }
    }
  }
}

@Composable
private fun BatchDeleteDialog(
  groups: List<OfflineMediaGroup>,
  onDismiss: () -> Unit,
  onDelete: (Set<String>) -> Unit,
) {
  val allIds = remember(groups) { groups.flatMap { it.snapshots }.map { it.entry.id }.toSet() }
  var selectedIds by remember(groups) { mutableStateOf(emptySet<String>()) }
  var expandedIds by remember(groups) { mutableStateOf(emptySet<String>()) }
  var confirming by remember { mutableStateOf(false) }
  val allSelected = allIds.isNotEmpty() && selectedIds.size == allIds.size
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (confirming) "确认删除缓存？" else "批量删除") },
    text = {
      if (confirming) {
        Text("将删除已选的 ${selectedIds.size} 个视频、分 P 或剧集；正在下载的项目会先停止。")
      } else {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("已选 ${selectedIds.size} 项", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { selectedIds = if (allSelected) emptySet() else allIds }) {
              Text(if (allSelected) "全不选" else "全选")
            }
          }
          LazyColumn(Modifier.fillMaxWidth()) {
            items(groups, key = OfflineMediaGroup::id) { group ->
              val childIds = group.snapshots.map { it.entry.id }.toSet()
              val selectedCount = childIds.count(selectedIds::contains)
              val collapsible = group.expandable
              if (!collapsible) {
                val snapshot = group.snapshots.single()
                DeleteSelectionRow(
                  checked = snapshot.entry.id in selectedIds,
                  title = snapshot.entry.title,
                  subtitle = snapshot.entry.partTitle,
                  onChecked = { checked ->
                    selectedIds =
                      if (checked) selectedIds + snapshot.entry.id else selectedIds - snapshot.entry.id
                  },
                )
              } else {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  TriStateCheckbox(
                    state =
                      when {
                        selectedCount == 0 -> ToggleableState.Off
                        selectedCount == childIds.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                      },
                    onClick = {
                      selectedIds =
                        if (selectedCount == childIds.size) selectedIds - childIds
                        else selectedIds + childIds
                    },
                  )
                  Column(
                    Modifier.weight(1f).combinedClickable(
                      onClick = {
                        expandedIds =
                          if (group.id in expandedIds) expandedIds - group.id
                          else expandedIds + group.id
                      },
                      onLongClick = {},
                    )
                  ) {
                    Text(group.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                      "${group.snapshots.size} 个${if (group.kind == OfflineMediaKind.BANGUMI) "剧集" else "分 P"}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Icon(
                    if (group.id in expandedIds) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (group.id in expandedIds) "收起" else "展开",
                  )
                }
                if (group.id in expandedIds) {
                  group.snapshots.forEach { snapshot ->
                    DeleteSelectionRow(
                      checked = snapshot.entry.id in selectedIds,
                      title = snapshot.entry.partTitle.ifBlank { snapshot.entry.title },
                      subtitle = snapshot.entry.qualityLabel,
                      indent = true,
                      onChecked = { checked ->
                        selectedIds =
                          if (checked) selectedIds + snapshot.entry.id else selectedIds - snapshot.entry.id
                      },
                    )
                  }
                }
              }
            }
          }
        }
      }
    },
    dismissButton = {
      TextButton(onClick = { if (confirming) confirming = false else onDismiss() }) {
        Text(if (confirming) "返回" else "取消")
      }
    },
    confirmButton = {
      Button(
        enabled = selectedIds.isNotEmpty(),
        onClick = { if (confirming) onDelete(selectedIds) else confirming = true },
      ) {
        Text(if (confirming) "确认删除" else "删除（${selectedIds.size}）")
      }
    },
  )
}

@Composable
private fun DeleteSelectionRow(
  checked: Boolean,
  title: String,
  subtitle: String,
  indent: Boolean = false,
  onChecked: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = if (indent) 30.dp else 0.dp, top = 3.dp, bottom = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = onChecked)
    Column(Modifier.weight(1f)) {
      Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
      subtitle.takeIf(String::isNotBlank)?.let {
        Text(
          it,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private data class OfflineMediaGroup(
  val id: String,
  val title: String,
  val kind: OfflineMediaKind,
  val collectionId: Long,
  val snapshots: List<OfflineMediaSnapshot>,
) {
  val expandable: Boolean
    get() =
      snapshots.size > 1 || kind == OfflineMediaKind.BANGUMI || collectionId > 0L

  fun toFeedItem(root: File): FeedItem {
    val representative = snapshots.first().entry.toFeedItem(root)
    return representative.copy(
      id = "offline-group:$id",
      title = title,
      uploader =
        if (snapshots.size > 1) "${snapshots.size} 个${if (kind == OfflineMediaKind.BANGUMI) "剧集" else "分 P"}"
        else representative.uploader,
    )
  }

  fun aggregateSnapshot(): OfflineMediaSnapshot {
    val bytes = snapshots.sumOf(OfflineMediaSnapshot::bytesDownloaded)
    val total = snapshots.sumOf(OfflineMediaSnapshot::totalBytes)
    val progress =
      if (total > 0L) bytes.toFloat() / total.toFloat() * 100f
      else snapshots.map(OfflineMediaSnapshot::progressPercent).average().toFloat().coerceAtLeast(0f)
    val state =
      when {
        snapshots.all { it.state == OfflineTransferState.COMPLETED } -> OfflineTransferState.COMPLETED
        snapshots.any { it.state == OfflineTransferState.DOWNLOADING } -> OfflineTransferState.DOWNLOADING
        snapshots.any { it.state == OfflineTransferState.PREPARING } -> OfflineTransferState.PREPARING
        snapshots.any { it.state == OfflineTransferState.QUEUED } -> OfflineTransferState.QUEUED
        snapshots.any { it.state == OfflineTransferState.FAILED } -> OfflineTransferState.FAILED
        snapshots.any { it.state == OfflineTransferState.PAUSED } -> OfflineTransferState.PAUSED
        snapshots.any { it.state == OfflineTransferState.UNAVAILABLE } -> OfflineTransferState.UNAVAILABLE
        else -> snapshots.first().state
      }
    return OfflineMediaSnapshot(
      entry = snapshots.first().entry,
      state = state,
      progressPercent = progress.coerceIn(0f, 100f),
      bytesDownloaded = bytes,
      totalBytes = total,
      failureReason = snapshots.firstOrNull { it.failureReason.isNotBlank() }?.failureReason.orEmpty(),
    )
  }
}

private fun List<OfflineMediaSnapshot>.toOfflineGroups(): List<OfflineMediaGroup> =
  groupBy { snapshot ->
      val entry = snapshot.entry
      when (entry.kind) {
        OfflineMediaKind.VIDEO ->
          if (entry.collectionId > 0L) "collection:${entry.collectionId}"
          else "video:${entry.bvid.ifBlank { entry.id }}"
        OfflineMediaKind.BANGUMI ->
          "bangumi:${entry.seasonId.takeIf { it > 0L } ?: entry.title.hashCode().toLong()}"
      }
    }
    .map { (id, entries) ->
      val sorted = entries.sortedWith(compareBy<OfflineMediaSnapshot> { it.entry.pageNumber }.thenBy { it.entry.createdAtMs })
      OfflineMediaGroup(
        id = id,
        title = sorted.first().entry.title,
        kind = sorted.first().entry.kind,
        collectionId = sorted.first().entry.collectionId,
        snapshots = sorted,
      )
    }
    .sortedByDescending { group -> group.snapshots.maxOf { it.entry.createdAtMs } }

private fun openCachedEntry(
  snapshot: OfflineMediaSnapshot,
  manager: OfflineMediaManager,
  onVideo: (FeedItem, Rect) -> Unit,
  onBangumi: (SpaceContentCard, FeedItem, Rect) -> Unit,
  coverBounds: Rect,
  displayItem: FeedItem? = null,
) {
  if (snapshot.state != OfflineTransferState.COMPLETED) return
  val entry = snapshot.entry
  val item = displayItem ?: entry.toFeedItem(manager.rootDirectory)
  if (entry.kind == OfflineMediaKind.BANGUMI) {
    onBangumi(
      SpaceContentCard(
        id = item.id,
        title = entry.title,
        subtitle = entry.partTitle,
        coverUrl = item.coverUrl,
        aid = entry.aid,
        bvid = entry.bvid,
        videoUrl = "https://www.bilibili.com/bangumi/play/ep${entry.episodeId}",
        seasonId = entry.seasonId,
        episodeId = entry.episodeId,
        kind = SpaceContentKind.BANGUMI,
      ),
      item,
      coverBounds,
    )
  } else {
    onVideo(item, coverBounds)
  }
}

private fun OfflineMediaEntry.toExpandedFeedItem(root: File): FeedItem {
  val parent = toFeedItem(root)
  return parent.copy(
    title = partTitle.ifBlank { title },
    uploader = title.takeIf { it.isNotBlank() && it != partTitle },
  )
}

private fun offlineStateLabel(snapshot: OfflineMediaSnapshot, unavailable: Boolean): String =
  when {
    snapshot.state == OfflineTransferState.UNAVAILABLE ->
      snapshot.failureReason.ifBlank { "会员状态已失效，缓存内容不可用" }
    unavailable -> "请使用缓存绑定的账号和有效会员播放"
    snapshot.state == OfflineTransferState.PREPARING -> "正在准备缓存"
    snapshot.state == OfflineTransferState.QUEUED -> "等待下载"
    snapshot.state == OfflineTransferState.DOWNLOADING -> "下载中 ${snapshot.progressPercent.toInt()}%"
    snapshot.state == OfflineTransferState.PAUSED -> "已暂停 ${snapshot.progressPercent.toInt()}%"
    snapshot.state == OfflineTransferState.COMPLETED -> "已完成 · 点击播放"
    snapshot.state == OfflineTransferState.FAILED -> snapshot.failureReason.ifBlank { "缓存失败" }
    else -> "不可用"
  }

internal fun formatOfflineBytes(bytes: Long): String =
  when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GiB".format(bytes / (1024f * 1024f * 1024f))
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.1f KiB".format(bytes / 1024f)
    else -> "$bytes B"
  }

private fun Rect.hasUsableSize(): Boolean = width > 1f && height > 1f

private suspend fun withFrameNanosCompat() {
  androidx.compose.runtime.withFrameNanos { }
}

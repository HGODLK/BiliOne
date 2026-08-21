package dev.openbili.webdemo.video

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import dev.openbili.webdemo.BuildConfig
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.PlayerSubtitleState
import dev.openbili.webdemo.WebViewConfigurator
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.BiliEmotePackage
import dev.openbili.webdemo.api.BiliHttpClient
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.CommentNavigationTarget
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.FollowingGroup
import dev.openbili.webdemo.api.MentionSuggestion
import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.api.PremiumAudioMode
import dev.openbili.webdemo.api.VideoEngagement
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.api.VideoStream
import dev.openbili.webdemo.api.remainingVideoCoins
import dev.openbili.webdemo.api.videoCoinLimit
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.PlaybackCoverRegistry
import dev.openbili.webdemo.offline.OfflineCacheChooserDialog
import dev.openbili.webdemo.offline.OfflineMediaKind
import dev.openbili.webdemo.offline.OfflineMediaManager
import dev.openbili.webdemo.offline.OfflineMediaRequest
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.subtitleStateForMedia
import dev.openbili.webdemo.ui.AvatarImage
import dev.openbili.webdemo.ui.CONTROL_DOUBLE_CONFIRM_TIMEOUT_MS
import dev.openbili.webdemo.ui.CONTROL_SEEK_STEP_MS
import dev.openbili.webdemo.ui.ControlVideoMode
import dev.openbili.webdemo.ui.ControlVideoSurfaceAction
import dev.openbili.webdemo.ui.CrossfadeBackgroundImage
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.LocalVideoCardContentColors
import dev.openbili.webdemo.ui.SessionPhase
import dev.openbili.webdemo.ui.StableBoundsTracker
import dev.openbili.webdemo.ui.TransitionPreparationBarrier
import dev.openbili.webdemo.ui.TransitionPreparationResult
import dev.openbili.webdemo.ui.TransitionReadySignal
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.ui.controlFocusOutline
import dev.openbili.webdemo.ui.navigationBringIntoViewTarget
import dev.openbili.webdemo.ui.rememberBackgroundLuminanceProfile
import dev.openbili.webdemo.ui.rememberNavigationBringIntoViewRequester
import dev.openbili.webdemo.ui.rememberStaticBackgroundModel
import dev.openbili.webdemo.ui.isControlConfirmKey
import dev.openbili.webdemo.ui.resolveControlVideoSurfaceAction
import dev.openbili.webdemo.ui.videoBackgroundForeground
import dev.openbili.webdemo.ui.videoBackgroundScrim
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BiliCoinIcon(coinCount: Int, modifier: Modifier = Modifier) {
  val color = androidx.compose.material3.LocalContentColor.current
  Canvas(modifier) {
    fun drawCoin(center: Offset, radius: Float) {
      val strokeWidth = 2.dp.toPx()
      drawCircle(color = color, radius = radius, center = center, style = Stroke(strokeWidth))
      drawLine(
        color,
        Offset(center.x - radius * .30f, center.y - radius * .18f),
        Offset(center.x + radius * .30f, center.y - radius * .18f),
        strokeWidth = strokeWidth,
      )
      drawLine(
        color,
        Offset(center.x - radius * .20f, center.y + radius * .20f),
        Offset(center.x + radius * .20f, center.y + radius * .20f),
        strokeWidth = strokeWidth,
      )
    }

    if (coinIconCount(coinCount) == 2) {
      val radius = size.minDimension * .28f
      drawCoin(Offset(size.width * .42f, size.height * .55f), radius)
      drawCoin(Offset(size.width * .61f, size.height * .43f), radius)
    } else {
      drawCoin(Offset(size.width * .5f, size.height * .5f), size.minDimension * .36f)
    }
  }
}

internal fun coinIconCount(coins: Int): Int = if (coins >= 2) 2 else 1

@Composable
internal fun CoinBurst(modifier: Modifier = Modifier) {
  val scale = remember { Animatable(.35f) }
  val alpha = remember { Animatable(1f) }
  LaunchedEffect(Unit) {
    coroutineScope {
      launch {
        scale.animateTo(1.15f, tween(260, easing = FastOutSlowInEasing))
        scale.animateTo(.9f, tween(180))
      }
      launch {
        delay(340)
        alpha.animateTo(0f, tween(300))
      }
    }
  }
  Box(
    modifier.size(64.dp).graphicsLayer {
      scaleX = scale.value
      scaleY = scale.value
      this.alpha = alpha.value
    }
  ) {
    repeat(3) { index ->
      Surface(
        modifier =
          Modifier.size((13 - index).dp)
            .align(
              when (index) {
                0 -> Alignment.TopCenter
                1 -> Alignment.CenterStart
                else -> Alignment.CenterEnd
              }
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
      ) {}
    }
  }
}

@Composable
internal fun CoinDialog(
  alreadyCoined: Int,
  alreadyLiked: Boolean,
  copyright: Int,
  width: Dp,
  positionProvider: PopupPositionProvider,
  onDismiss: () -> Unit,
  onConfirm: (Int, Boolean) -> Unit,
) {
  val coinLimit = videoCoinLimit(copyright)
  val remaining = remainingVideoCoins(copyright, alreadyCoined)
  var count by
    remember(alreadyCoined, coinLimit) {
      mutableIntStateOf(remaining.coerceIn(1, coinLimit))
    }
  var alsoLike by remember(alreadyLiked) { mutableStateOf(!alreadyLiked) }
  var exiting by remember { mutableStateOf(false) }
  var pendingConfirm by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
  val scaleProgress = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    scaleProgress.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
  }
  LaunchedEffect(exiting) {
    if (exiting) {
      scaleProgress.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
      val confirm = pendingConfirm
      if (confirm != null) onConfirm(confirm.first, confirm.second)
      onDismiss()
    }
  }
  Popup(
    popupPositionProvider = positionProvider,
    onDismissRequest = { exiting = true },
    properties =
      PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
  ) {
    Surface(
      modifier =
        Modifier.graphicsLayer {
          val p = scaleProgress.value.coerceIn(0f, 1f)
          scaleX = 0.92f + 0.08f * p
          scaleY = 0.92f + 0.08f * p
          alpha = p
        },
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface,
      border =
        androidx.compose.foundation.BorderStroke(
          .75.dp,
          MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
      Column(
        Modifier.width(width).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text("给视频投币", style = MaterialTheme.typography.titleLarge)
        Text(
          if (remaining == 0) {
            "这个视频已经投满 $coinLimit 枚硬币"
          } else {
            "本视频已投 $alreadyCoined 枚，还可以投 $remaining 枚"
          },
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (remaining > 0) {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (1..remaining).forEach { value ->
              FilterChip(
                selected = count == value,
                onClick = { count = value },
                label = { Text("$value 枚") },
              )
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = alsoLike,
              onCheckedChange = { alsoLike = it },
              enabled = !alreadyLiked,
            )
            Text(if (alreadyLiked) "已点赞" else "同时点赞")
          }
        }
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { exiting = true }) { Text("取消") }
          Button(
            onClick = {
              pendingConfirm = count to alsoLike
              exiting = true
            },
            enabled = remaining > 0,
          ) {
            Text("确认投币")
          }
        }
      }
    }
  }
}

@Composable
internal fun FavoriteFolderDialog(
  folders: List<FavoriteFolder>,
  loading: Boolean,
  width: Dp,
  positionProvider: PopupPositionProvider,
  onDismiss: () -> Unit,
  onConfirm: (List<Long>, List<Long>) -> Unit,
) {
  val original = remember(folders) { folders.filter { it.favorited }.map { it.id }.toSet() }
  var selected by remember(folders) { mutableStateOf(original) }
  var exiting by remember { mutableStateOf(false) }
  var contentReady by remember { mutableStateOf(false) }
  var pendingConfirm by remember { mutableStateOf<Pair<List<Long>, List<Long>>?>(null) }
  val scaleProgress = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    scaleProgress.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
    contentReady = true
  }
  LaunchedEffect(exiting) {
    if (exiting) {
      contentReady = false
      scaleProgress.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
      val confirm = pendingConfirm
      if (confirm != null) onConfirm(confirm.first, confirm.second)
      onDismiss()
    }
  }
  Popup(
    popupPositionProvider = positionProvider,
    onDismissRequest = { exiting = true },
    properties =
      PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
  ) {
    Surface(
      modifier =
        Modifier.graphicsLayer {
          val p = scaleProgress.value.coerceIn(0f, 1f)
          scaleX = 0.92f + 0.08f * p
          scaleY = 0.92f + 0.08f * p
          alpha = p
        },
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface,
      border =
        androidx.compose.foundation.BorderStroke(
          .75.dp,
          MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
      Column(
        Modifier.width(width).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("收藏到", style = MaterialTheme.typography.titleLarge)
        if (!contentReady) {
          Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
          }
        } else
          when {
            loading ->
              Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
              }
            folders.isEmpty() -> Text("暂无可用收藏夹", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else ->
              LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(folders, key = { it.id }) { folder ->
                  Row(
                    Modifier.fillMaxWidth()
                      .clickable {
                        selected =
                          if (folder.id in selected) selected - folder.id else selected + folder.id
                      }
                      .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Checkbox(
                      checked = folder.id in selected,
                      onCheckedChange = { checked ->
                        selected = if (checked) selected + folder.id else selected - folder.id
                      },
                    )
                    Text(folder.title, modifier = Modifier.weight(1f))
                    Text(
                      folder.mediaCount.toString(),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
          }
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { exiting = true }) { Text("取消") }
          Button(
            onClick = {
              pendingConfirm = (selected - original).toList() to (original - selected).toList()
              exiting = true
            },
            enabled = !loading && folders.isNotEmpty() && selected != original,
          ) {
            Text("确定")
          }
        }
      }
    }
  }
}

@Composable
internal fun AdaptiveVideoPanes(
  primary: @Composable () -> Unit,
  secondary: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  Layout(
    modifier = modifier,
    content = {
      Box(Modifier.fillMaxSize()) { primary() }
      Box(Modifier.fillMaxSize()) { secondary() }
    },
  ) { measurables, constraints ->
    if (measurables.size < 2) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val paneSpec = videoPaneSpec(width, height, density, fontScale)
    if (paneSpec.split) {
      val gap = 12.dp.roundToPx()
      val secondaryWidth = paneSpec.secondarySizePx
      val primaryWidth = width - secondaryWidth - gap
      val primaryPlaceable = measurables[0].measure(Constraints.fixed(primaryWidth, height))
      val secondaryPlaceable = measurables[1].measure(Constraints.fixed(secondaryWidth, height))
      layout(width, height) {
        primaryPlaceable.placeRelative(0, 0)
        secondaryPlaceable.placeRelative(primaryWidth + gap, 0)
      }
    } else {
      val gap = 12.dp.roundToPx()
      val primaryFraction = if (width >= height) .68f else .62f
      val primaryHeight = (height * primaryFraction).roundToInt().coerceAtMost(height - gap)
      val secondaryHeight = height - primaryHeight - gap
      val primaryPlaceable = measurables[0].measure(Constraints.fixed(width, primaryHeight))
      val secondaryPlaceable = measurables[1].measure(Constraints.fixed(width, secondaryHeight))
      layout(width, height) {
        primaryPlaceable.placeRelative(0, 0)
        secondaryPlaceable.placeRelative(0, primaryHeight + gap)
      }
    }
  }
}

internal data class VideoPaneSpec(val split: Boolean, val secondarySizePx: Int)

internal fun videoPaneSpec(
  widthPx: Int,
  heightPx: Int,
  density: Float,
  fontScale: Float,
): VideoPaneSpec {
  if (widthPx <= 0 || heightPx <= 0) return VideoPaneSpec(false, 0)
  val safeDensity = density.coerceAtLeast(.5f)
  val widthDp = widthPx / safeDensity
  val heightDp = heightPx / safeDensity
  val safeFontScale = fontScale.coerceIn(.85f, 2f)
  val gapDp = 12f
  val minimumPrimaryDp = 520f
  val minimumSecondaryDp = (300f + (safeFontScale - 1f) * 90f).coerceIn(288f, 360f)
  val split =
    widthPx >= heightPx &&
      heightDp >= 460f &&
      widthDp >= minimumPrimaryDp + minimumSecondaryDp + gapDp
  if (!split) return VideoPaneSpec(false, 0)

  val targetSecondaryDp = (widthDp * .30f).coerceAtLeast(minimumSecondaryDp)
  val maximumSecondaryDp = widthDp - minimumPrimaryDp - gapDp
  val secondaryPx = (targetSecondaryDp.coerceAtMost(maximumSecondaryDp) * safeDensity).roundToInt()
  return VideoPaneSpec(true, secondaryPx)
}

internal fun shouldUseSplitVideoPanes(
  widthPx: Int,
  heightPx: Int,
  density: Float = 1f,
  fontScale: Float = 1f,
): Boolean = videoPaneSpec(widthPx, heightPx, density, fontScale).split

// ── 推荐卡 ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecommendationCard(
  item: FeedItem,
  onClick: (Rect) -> Unit,
  onLongClick: () -> Unit,
  coverVisible: Boolean,
  onCoverBoundsChanged: (Rect) -> Unit = {},
  overlayStyle: Boolean = false,
  cardWidth: Dp = 232.dp,
  compactHorizontal: Boolean = false,
  compactHeight: Dp = 68.dp,
  showDuration: Boolean = false,
  controlEnabled: Boolean = false,
  controlFocusRequester: FocusRequester? = null,
  controlUpFocusRequester: FocusRequester? = null,
  controlDownFocusRequester: FocusRequester? = null,
  controlAtHorizontalStart: Boolean = false,
  controlAtHorizontalEnd: Boolean = false,
) {
  var coverBounds by remember { mutableStateOf(Rect.Zero) }
  val bringIntoViewRequester = rememberNavigationBringIntoViewRequester()
  val scope = rememberCoroutineScope()
  val interactionSource = remember { MutableInteractionSource() }
  var controlConfirmTracking by remember(item.id) { mutableStateOf(false) }
  var controlLongPressTriggered by remember(item.id) { mutableStateOf(false) }
  var controlLongPressJob by remember(item.id) { mutableStateOf<Job?>(null) }
  DisposableEffect(item.id) {
    onDispose { controlLongPressJob?.cancel() }
  }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) .98f else 1f,
      animationSpec = spring(dampingRatio = .82f, stiffness = 700f),
      label = "recommendationPress",
    )
  val compact = cardWidth < 210.dp
  val compactCoverWidth = (compactHeight - 12.dp) * (16f / 9f)
  val compactContentHeight = (compactHeight - 12.dp).coerceAtLeast(1.dp)
  val compactTitleHeight =
    40.dp.coerceAtMost((compactContentHeight - 34.dp).coerceAtLeast(20.dp))
  val duration = item.duration?.takeIf(String::isNotBlank)
  val regularInfoHeight = recommendationInfoHeight(cardWidth)
  val regularTitleHeight = if (compact) 38.dp else 42.dp
  val regularAvatarSize = if (compact) 22.dp else 26.dp
  Surface(
    modifier =
      Modifier.width(cardWidth)
        .then(if (compactHorizontal) Modifier.height(compactHeight) else Modifier)
        .then(
          if (controlEnabled) {
            Modifier.then(
                if (controlFocusRequester != null) {
                  Modifier.focusRequester(controlFocusRequester)
                } else Modifier
              )
              .onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                if (event.type == KeyEventType.KeyDown) {
                  when (nativeEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                      if (nativeEvent.repeatCount == 0) {
                        controlUpFocusRequester?.let { requester ->
                          scope.launch { runCatching { requester.requestFocus() } }
                        }
                      }
                      return@onPreviewKeyEvent true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                      if (nativeEvent.repeatCount == 0) {
                        controlDownFocusRequester?.let { requester ->
                          scope.launch { runCatching { requester.requestFocus() } }
                        }
                      }
                      return@onPreviewKeyEvent true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT ->
                      if (controlAtHorizontalStart) return@onPreviewKeyEvent true
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT ->
                      if (controlAtHorizontalEnd) return@onPreviewKeyEvent true
                  }
                }
                if (!isControlConfirmKey(nativeEvent.keyCode)) {
                  return@onPreviewKeyEvent false
                }
                when (event.type) {
                  KeyEventType.KeyDown -> {
                    if (nativeEvent.repeatCount == 0 && !controlConfirmTracking) {
                      controlConfirmTracking = true
                      controlLongPressTriggered = false
                      controlLongPressJob =
                        scope.launch {
                          delay(dev.openbili.webdemo.ui.CONTROL_LONG_PRESS_TIMEOUT_MS)
                          if (controlConfirmTracking) {
                            controlLongPressTriggered = true
                            onLongClick()
                          }
                        }
                    }
                    true
                  }
                  KeyEventType.KeyUp -> {
                    controlLongPressJob?.cancel()
                    controlLongPressJob = null
                    val click = controlConfirmTracking && !controlLongPressTriggered
                    controlConfirmTracking = false
                    controlLongPressTriggered = false
                    if (click) {
                      scope.launch {
                        bringIntoViewRequester.bringIntoView()
                        withFrameNanos {}
                        onClick(coverBounds)
                      }
                    }
                    true
                  }
                  else -> true
                }
              }
              .controlFocusOutline(
                shape = VideoShapeTokens.Card,
                color = MaterialTheme.colorScheme.primary,
                width = 3.dp,
                enabled = true,
              )
          } else Modifier
        )
        .navigationBringIntoViewTarget(bringIntoViewRequester)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .combinedClickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = {
            scope.launch {
              bringIntoViewRequester.bringIntoView()
              withFrameNanos {}
              onClick(coverBounds)
            }
          },
          onLongClick = onLongClick,
        ),
    shape = VideoShapeTokens.Card,
    color = if (overlayStyle) Color(0xFF171A1F) else MaterialTheme.colorScheme.surface,
    contentColor = if (overlayStyle) Color.White else MaterialTheme.colorScheme.onSurface,
    tonalElevation = if (overlayStyle) 0.dp else 2.dp,
    shadowElevation = 0.dp,
    border =
      if (overlayStyle) null
      else
        androidx.compose.foundation.BorderStroke(
          .75.dp,
          MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f),
        ),
  ) {
    VideoCardGradient(
      coverUrl = item.coverUrl,
      loadKey = item.id,
      modifier = Modifier.fillMaxWidth(),
      overlayStyle = overlayStyle,
    ) {
      val cardContentColors = LocalVideoCardContentColors.current
      if (compactHorizontal) {
        Row(
          Modifier.fillMaxSize().padding(6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            Modifier.width(compactCoverWidth)
              .fillMaxHeight()
              .clip(VideoShapeTokens.Player)
              .graphicsLayer {
                alpha = if (coverVisible) 1f else 0f
              }
              .onGloballyPositioned {
                coverBounds = it.boundsInRoot()
                onCoverBoundsChanged(coverBounds)
              }
          ) {
            CoverImage(
              coverUrl = item.coverUrl,
              modifier = Modifier.fillMaxSize(),
              shape = VideoShapeTokens.Player,
              enforceAspectRatio = false,
              loadKey = item.id,
            )
          }
          Column(
            Modifier.weight(1f).heightIn(max = (compactHeight - 12.dp).coerceAtLeast(1.dp)),
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Text(
              text = item.title,
              modifier = Modifier.height(compactTitleHeight),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Medium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (!item.uploaderFace.isNullOrBlank()) {
                if (showDuration) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RecommendationAvatar(item.uploaderFace, 18.dp, item.id)
                    if (duration != null) {
                      Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = cardContentColors.secondary,
                        maxLines = 1,
                      )
                    } else {
                      // 为缺失时长的卡片保留同一行高，避免相邻头像上下跳动。
                      Spacer(Modifier.height(14.dp))
                    }
                  }
                } else {
                  RecommendationAvatar(item.uploaderFace, 18.dp, item.id)
                }
                Spacer(Modifier.width(5.dp))
              }
              Text(
                text = item.uploader.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = cardContentColors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (showDuration && duration != null && item.uploaderFace.isNullOrBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                  text = duration,
                  style = MaterialTheme.typography.labelSmall,
                  color = cardContentColors.secondary,
                  maxLines = 1,
                )
              }
            }
          }
        }
      } else {
        Column {
          Box(
            Modifier.onGloballyPositioned {
                coverBounds = it.boundsInRoot()
                onCoverBoundsChanged(coverBounds)
              }
              .graphicsLayer { alpha = if (coverVisible) 1f else 0f }
          ) {
            CoverImage(
              coverUrl = item.coverUrl,
              modifier = Modifier.fillMaxWidth(),
              shape = VideoShapeTokens.Player,
              loadKey = item.id,
            )
          }
          Column(Modifier.height(regularInfoHeight).clipToBounds()) {
            Text(
              text = item.title,
              modifier =
                Modifier.padding(
                    start = if (compact) 9.dp else 11.dp,
                    end = if (compact) 9.dp else 11.dp,
                    top = if (compact) 7.dp else 9.dp,
                  )
                  .height(regularTitleHeight),
              style =
                if (compact) MaterialTheme.typography.bodySmall
                else MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(
                    horizontal = if (compact) 9.dp else 11.dp,
                    vertical = if (compact) 7.dp else 9.dp,
                  ),
              verticalAlignment = Alignment.CenterVertically,
            ) {
            if (!item.uploaderFace.isNullOrBlank()) {
              RecommendationAvatar(item.uploaderFace, regularAvatarSize, item.id)
              Spacer(Modifier.width(if (compact) 7.dp else 8.dp))
            }
            Text(
              text = item.uploader.orEmpty(),
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.labelSmall,
              color = cardContentColors.secondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (showDuration && duration != null) {
              Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
              Text(
                text = duration,
                style = MaterialTheme.typography.labelSmall,
                color = cardContentColors.secondary,
                maxLines = 1,
              )
            }
            }
          }
        }
      }
    }
  }
}

internal fun recommendationInfoHeight(cardWidth: Dp): Dp {
  val desiredHeight = if (cardWidth < 210.dp) 81.dp else 89.dp
  val coverHeight = cardWidth * (9f / 16f)
  return desiredHeight.coerceAtMost(coverHeight).coerceAtLeast(1.dp)
}

@Composable
internal fun RecommendationAvatar(
  imageUrl: String?,
  size: Dp,
  loadKey: String,
) {
  val cardContentColors = LocalVideoCardContentColors.current
  Box(
    modifier =
      Modifier.requiredSize(size)
        .clip(CircleShape)
        .background(cardContentColors.primary.copy(alpha = .12f))
  ) {
    AvatarImage(
      face = imageUrl.orEmpty(),
      contentDescription = null,
      loadKey = loadKey,
      requestSize = 64,
      modifier = Modifier.matchParentSize(),
    )
  }
}

// ── 评论行 ──────────────────────────────────────────────────────────────

/**
 * 评论滚动稳定后等待多少毫秒再触发头像取色预取，让快速滑动不会每帧都排队并
 * 取消提取任务。
 */
internal const val PALETTE_PREFETCH_DEBOUNCE_MS = 160L

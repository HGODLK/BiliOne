package dev.openbili.webdemo.my

/**
 * "我的关注"面板：分组、排序与无阴影双列关注卡。
 */

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.request.allowHardware
import coil3.request.ImageRequest
import dev.openbili.webdemo.api.AccountMessage
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.BiliEmotePackage
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.FollowingUser
import dev.openbili.webdemo.api.MessageTargetKind
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.article.ArticleCard
import dev.openbili.webdemo.BuildConfig
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedImageLoadMode
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import dev.openbili.webdemo.feed.rememberGridFeedImageLoadPolicy
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.settings.AdvancedAudioPriority
import dev.openbili.webdemo.settings.AppCacheManager
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.canSelectPreferredResolution
import dev.openbili.webdemo.settings.detectSimAvailability
import dev.openbili.webdemo.settings.DeviceMediaCapabilities
import dev.openbili.webdemo.settings.PreferredResolutionMode
import dev.openbili.webdemo.settings.SimAvailability
import dev.openbili.webdemo.settings.ThemeAccent
import dev.openbili.webdemo.settings.ThemeMode
import dev.openbili.webdemo.ui.controlFocusOutline
import dev.openbili.webdemo.ui.HomeHubTab
import dev.openbili.webdemo.ui.isControlConfirmKey
import dev.openbili.webdemo.ui.LocalColorfulCardsEnabled
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import dev.openbili.webdemo.ui.OfficialVerificationIcon
import dev.openbili.webdemo.ui.OfficialVerificationIconSize
import dev.openbili.webdemo.ui.PressableVideoCard
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.requestFocusWithinFrames
import dev.openbili.webdemo.ui.RootAccountHeader
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.VideoCardReveal
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.video.BiliRichText
import dev.openbili.webdemo.video.CommentAvatarPaletteCache
import dev.openbili.webdemo.video.CommentEmoteMarkerRegistry
import dev.openbili.webdemo.video.CommentImagePreviewOverlay
import dev.openbili.webdemo.video.CommentImagePreviewSession
import dev.openbili.webdemo.video.CommentProfileAnchor
import dev.openbili.webdemo.video.CommentRow
import dev.openbili.webdemo.video.CommentTextEditor
import dev.openbili.webdemo.video.CommentToolPage
import dev.openbili.webdemo.video.CommentToolPanel
import dev.openbili.webdemo.video.extractAvatarDominantColors
import dev.openbili.webdemo.video.readableCommentCardColor
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 我的关注面板组合体。 */
@Composable
internal fun FollowingPanel(
  state: MyUiState,
  onProfile: (FollowingUser, Rect) -> Unit,
  onUnfollow: (FollowingUser) -> Unit,
  onQuery: (String) -> Unit,
  onGroup: (Long?) -> Unit,
  onOrder: (FollowingOrder) -> Unit,
  onLoadMore: () -> Unit,
) {
  val controlMode = LocalControlMode.current
  val gridState = rememberLazyGridState()
  val imageLoadPolicy = rememberGridFeedImageLoadPolicy(gridState, columns = 2)
  val searchFocusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  var searchEditing by remember { mutableStateOf(false) }
  LaunchedEffect(searchEditing) {
    if (searchEditing) {
      searchFocusRequester.requestFocusWithinFrames(maxFrames = 3)
      keyboardController?.show()
    }
  }
  BackHandler(enabled = controlMode && searchEditing) {
    searchEditing = false
    keyboardController?.hide()
  }
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    LazyRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item(key = "all_followings") {
        FilterChip(
          selected = state.selectedFollowingGroupId == null,
          onClick = { onGroup(null) },
          label = { Text("全部关注  ${state.followingTotal}") },
        )
      }
      items(state.followingGroups, key = { it.id }) { group ->
        FilterChip(
          selected = state.selectedFollowingGroupId == group.id,
          onClick = { onGroup(group.id) },
          label = { Text("${group.name}  ${group.count}") },
        )
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FollowingOrder.entries.forEach { order ->
        FilterChip(
          selected = state.followingOrder == order,
          onClick = { onOrder(order) },
          label = { Text(order.label) },
        )
      }
      Box(Modifier.weight(1f))
      Icon(
        Icons.Default.Search,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedTextField(
        value = state.followingQuery,
        onValueChange = onQuery,
        modifier =
          Modifier.width(320.dp)
            .focusRequester(searchFocusRequester)
            .onFocusChanged { focusState ->
              if (!focusState.isFocused && searchEditing) {
                searchEditing = false
                keyboardController?.hide()
              }
            }
            .onPreviewKeyEvent { event ->
              if (!controlMode || searchEditing) return@onPreviewKeyEvent false
              val keyCode = event.nativeKeyEvent.keyCode
              if (isControlConfirmKey(keyCode)) {
                if (event.type == KeyEventType.KeyUp) searchEditing = true
                return@onPreviewKeyEvent true
              }
              val direction =
                when (keyCode) {
                  android.view.KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
                  android.view.KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
                  android.view.KeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.Left
                  android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.Right
                  else -> null
                }
              if (direction != null) {
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                  focusManager.moveFocus(direction)
                }
                true
              } else {
                false
              }
            }
            .controlFocusOutline(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.primary,
              width = 3.dp,
              enabled = controlMode && !searchEditing,
            ),
        singleLine = true,
        readOnly = controlMode && !searchEditing,
        label = { Text("搜索关注") },
        placeholder = { Text("输入用户名") },
      )
    }
    CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = NavigationCardBottomClearance),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        itemsIndexed(state.followings, key = { _, person -> person.mid }) { index, person ->
          VideoCardReveal(
            index = index,
            batchKey = state.followings.firstOrNull()?.mid,
            itemKey = person.mid,
            animatedItemCount = 20,
          ) {
            FollowingUserCard(
              person = person,
              unfollowed = person.mid in state.unfollowedIds,
              onProfile = onProfile,
              onUnfollow = onUnfollow,
            )
          }
        }
        if (state.followingHasMore) {
          item(
            key =
              "following_load_more_${state.followingPage}_${state.followingQuery}_" +
                "${state.selectedFollowingGroupId}_${state.followingOrder}",
            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
          ) {
            LaunchedEffect(
              state.followingPage,
              state.followingQuery,
              state.selectedFollowingGroupId,
              state.followingOrder,
              imageLoadPolicy.mode,
            ) {
              if (imageLoadPolicy.mode != FeedImageLoadMode.PAUSED) onLoadMore()
            }
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FollowingUserCard(
  person: FollowingUser,
  unfollowed: Boolean,
  onProfile: (FollowingUser, Rect) -> Unit,
  onUnfollow: (FollowingUser) -> Unit,
) {
  val context = LocalContext.current
  val imageLoadPolicy = LocalFeedImageLoadPolicy.current
  val colorfulCardsEnabled = LocalColorfulCardsEnabled.current
  val scope = rememberCoroutineScope()
  val surface = MaterialTheme.colorScheme.surfaceVariant
  val primaryContainer = MaterialTheme.colorScheme.primaryContainer
  val darkTheme = MaterialTheme.colorScheme.surface.luminance() < .5f
  var avatarBounds by remember(person.mid) { mutableStateOf(Rect.Zero) }
  var avatarColors by
    remember(person.face, darkTheme, colorfulCardsEnabled) {
      mutableStateOf(
        if (colorfulCardsEnabled) CommentAvatarPaletteCache.get(person.face).orEmpty()
        else emptyList()
      )
    }
  val gradientColors =
    remember(avatarColors, surface, primaryContainer, darkTheme, colorfulCardsEnabled) {
      if (!colorfulCardsEnabled) {
        listOf(surface, surface)
      } else if (avatarColors.isEmpty()) {
        listOf(primaryContainer, surface)
      } else {
        avatarColors.take(2).map { readableCommentCardColor(it, surface, darkTheme) }
      }
    }
  val avatarPreviouslyLoaded =
    remember(person.face) { LoadedFeedImageRegistry.contains(person.face) }
  var avatarDisplayed by remember(person.face) { mutableStateOf(false) }
  val avatarRequestPermitted =
    avatarPreviouslyLoaded || avatarDisplayed || imageLoadPolicy.permits(person.mid.toString())
  val avatarAlpha by
    animateFloatAsState(
      targetValue = if (avatarDisplayed) 1f else 0f,
      animationSpec = tween(180),
      label = "followingAvatarAlpha",
    )
  val avatarRequest =
    remember(person.face, avatarRequestPermitted) {
      if (!avatarRequestPermitted) null
      else ImageRequest.Builder(context).data(person.face).size(96, 96).allowHardware(false).build()
    }
  val buttonTint by
    animateColorAsState(
      targetValue =
        if (unfollowed) Color(0xFFF06A94).copy(alpha = .22f) else Color.Black.copy(alpha = .2f),
      animationSpec = tween(180),
      label = "followingButtonTint",
    )
  Surface(
    modifier = Modifier.fillMaxWidth().height(138.dp),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    tonalElevation = 2.dp,
    shadowElevation = 0.dp,
  ) {
    Box {
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(gradientColors)))
        if (colorfulCardsEnabled) {
          Box(
            Modifier.fillMaxSize()
              .background(
                Brush.horizontalGradient(
                  listOf(Color.Black.copy(alpha = .32f), Color.Black.copy(alpha = .12f))
                )
              )
          )
        }
      }
      Row(
        Modifier.fillMaxSize()
          .clickable { onProfile(person, avatarBounds) }
          .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AsyncImage(
          model = avatarRequest,
          contentDescription = person.name,
          modifier =
            Modifier.size(56.dp)
              .clip(CircleShape)
              .graphicsLayer { alpha = avatarAlpha }
              .onGloballyPositioned { avatarBounds = it.boundsInRoot() },
          contentScale = ContentScale.Crop,
          onSuccess = { result ->
            avatarDisplayed = true
            LoadedFeedImageRegistry.markLoaded(person.face)
            if (colorfulCardsEnabled && avatarColors.isEmpty()) {
              val bitmap = (result.result.image as? BitmapImage)?.bitmap ?: return@AsyncImage
              scope.launch {
                val extracted =
                  CommentAvatarPaletteCache.resolve(person.face) {
                    withContext(Dispatchers.Default) { extractAvatarDominantColors(bitmap) }
                  }
                if (extracted.isNotEmpty()) avatarColors = extracted
              }
            }
          },
        )
        Column(
          Modifier.padding(start = 12.dp).weight(1f),
          verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            OfficialVerificationIcon(
              verification = person.officialVerification,
              modifier = Modifier.size(OfficialVerificationIconSize),
            )
            Text(
              person.name,
              style = MaterialTheme.typography.titleSmall,
              color = if (colorfulCardsEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          BiliRichText(
            text = person.signature.ifBlank { "这个人很神秘，什么也没有写 (´･ω･`)" },
            emotes = emptyMap(),
            maxLines = 2,
            style =
              MaterialTheme.typography.bodySmall.copy(
                color =
                  if (colorfulCardsEnabled) Color.White.copy(alpha = .82f)
                  else MaterialTheme.colorScheme.onSurfaceVariant
              ),
          )
        }
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = buttonTint,
          contentColor =
            if (colorfulCardsEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
          shadowElevation = 0.dp,
        ) {
          Button(
            onClick = { onUnfollow(person) },
            modifier = Modifier.animateContentSize(animationSpec = tween(180)),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor =
                  if (colorfulCardsEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
              ),
            elevation =
              ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
              ),
          ) {
            Crossfade(
              targetState = unfollowed,
              animationSpec = tween(160),
              label = "followingToggleText",
            ) { undone ->
              Text(if (undone) "点错了T_T" else "取关", maxLines = 1)
            }
          }
        }
      }
    }
  }
}

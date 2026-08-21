package dev.openbili.webdemo.my

/**
 * "我的"页共用 UI 组件：无阴影双列视频卡与表情目录解析。
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
import androidx.compose.ui.focus.FocusRequester
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
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import dev.openbili.webdemo.ui.OfficialVerificationIcon
import dev.openbili.webdemo.ui.OfficialVerificationIconSize
import dev.openbili.webdemo.ui.PressableVideoCard
import dev.openbili.webdemo.ui.PullRefreshContainer
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

/** 收藏/历史/稍后再看共用的无阴影双列视频卡内容。 */
@Composable
internal fun MyVideoCardContent(
  item: FeedItem,
  loadKey: String = item.id,
  coverVisible: Boolean,
  onCoverBoundsChanged: (Rect) -> Unit,
  historyLabel: String? = null,
  mediaBadge: String? = null,
) {
  VideoCardGradient(coverUrl = item.coverUrl, loadKey = loadKey) {
    Column {
      Box(
        Modifier.onGloballyPositioned { onCoverBoundsChanged(it.boundsInRoot()) }
          .graphicsLayer { alpha = if (coverVisible) 1f else 0f }
      ) {
        CoverImage(
          coverUrl = item.coverUrl,
          modifier = Modifier.fillMaxWidth(),
          shape = VideoShapeTokens.Player,
          loadKey = loadKey,
        )
      }
      Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Text(
          item.title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
          maxLines = 2,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
          modifier = Modifier.height(48.dp),
        )
        BiliRichText(
          text = item.description.ifBlank { "暂无简介" },
          emotes = emptyMap(),
          style =
            MaterialTheme.typography.bodySmall.copy(
              color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
          maxLines = 2,
          modifier = Modifier.height(36.dp),
        )
        Row(
          modifier = Modifier.fillMaxWidth().height(18.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          mediaBadge?.takeIf(String::isNotBlank)?.let { badge ->
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .82f),
            ) {
              Text(
                badge,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
              )
            }
          }
          if (!mediaBadge.isNullOrBlank() && !historyLabel.isNullOrBlank())
            Spacer(Modifier.width(8.dp))
          historyLabel?.let { label ->
            Text(
              label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            item.uploader.orEmpty(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
          )
          item.duration?.takeIf(String::isNotBlank)?.let {
            Text(
              it,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

internal fun List<BiliEmotePackage>.emoteCatalog(): Map<String, BiliEmote> =
  flatMap(BiliEmotePackage::emotes).associateBy(BiliEmote::text)

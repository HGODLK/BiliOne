package dev.openbili.webdemo.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.openbili.webdemo.R
import dev.openbili.webdemo.api.FollowingGroup
import dev.openbili.webdemo.ui.DeviceStatusCluster
import dev.openbili.webdemo.ui.FollowButton
import dev.openbili.webdemo.ui.controlFocusOutline

internal data class PlaybackHeaderControlFocus(
  val back: FocusRequester,
  val home: FocusRequester,
  val owner: FocusRequester,
  val follow: FocusRequester,
  val selection: FocusRequester,
  val details: FocusRequester,
  val player: FocusRequester,
)

internal data class PlaybackHeaderUiModel(
  val stableId: String,
  val title: String,
  val ownerMid: Long,
  val ownerName: String,
  val ownerFace: String?,
  val description: String,
  val metadata: String,
  val selectionTitle: String? = null,
  val selectionProgress: String = "",
)

@Composable
internal fun PlaybackHeader(
  model: PlaybackHeaderUiModel,
  onBack: () -> Unit,
  onHome: () -> Unit,
  onOwnerProfileClick: (Long, String?, String?, Rect) -> Unit,
  showFollowButton: Boolean,
  followed: Boolean,
  followBusy: Boolean,
  followingGroups: List<FollowingGroup>,
  followingGroupsLoading: Boolean,
  loggedIn: Boolean,
  onLoadFollowingGroups: () -> Unit,
  onSelectFollowingGroup: (Long) -> Unit,
  onUnfollow: () -> Unit,
  onLogin: () -> Unit,
  onShowInfo: () -> Unit,
  onOpenSelection: (() -> Unit)? = null,
  panelSlideProgress: () -> Float = { 1f },
  showDeviceStatus: Boolean = true,
  foregroundColor: Color? = null,
  glassBackdrop: PlaybackPageGlassBackdrop = PlaybackPageGlassBackdrop(),
  controlFocus: PlaybackHeaderControlFocus? = null,
) {
  var ownerBounds by remember(model.stableId) { mutableStateOf(Rect.Zero) }
  val resolvedForeground = foregroundColor ?: MaterialTheme.colorScheme.onBackground
  val secondaryForeground = resolvedForeground.copy(alpha = .72f)
  val afterHome =
    when {
      model.ownerMid > 0L -> controlFocus?.owner
      showFollowButton -> controlFocus?.follow
      model.selectionTitle != null && onOpenSelection != null -> controlFocus?.selection
      else -> controlFocus?.details
    }
  val afterOwner =
    when {
      showFollowButton -> controlFocus?.follow
      model.selectionTitle != null && onOpenSelection != null -> controlFocus?.selection
      else -> controlFocus?.details
    }
  val afterFollow =
    if (model.selectionTitle != null && onOpenSelection != null) controlFocus?.selection
    else controlFocus?.details
  val beforeDetails =
    when {
      model.selectionTitle != null && onOpenSelection != null -> controlFocus?.selection
      showFollowButton -> controlFocus?.follow
      model.ownerMid > 0L -> controlFocus?.owner
      else -> controlFocus?.home
    }
  Surface(
    modifier =
      Modifier.fillMaxWidth().height(94.dp).graphicsLayer {
        alpha = panelSlideProgress().coerceIn(0f, 1f)
      },
    color = Color.Transparent,
    contentColor = resolvedForeground,
    tonalElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(end = 18.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onBack,
        modifier =
          Modifier.testTag("video_back_button")
            .then(
              if (controlFocus != null) {
                Modifier.focusRequester(controlFocus.back)
                  .focusProperties {
                    left = FocusRequester.Cancel
                    right = controlFocus.home
                    up = FocusRequester.Cancel
                    down = controlFocus.player
                  }
                  .controlFocusOutline(
                    CircleShape,
                    MaterialTheme.colorScheme.primary,
                    enabled = true,
                  )
              } else Modifier
            ),
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
      }
      IconButton(
        onClick = onHome,
        modifier =
          Modifier.then(
            if (controlFocus != null) {
              Modifier.focusRequester(controlFocus.home)
                .focusProperties {
                  left = controlFocus.back
                  right = afterHome ?: FocusRequester.Cancel
                  up = FocusRequester.Cancel
                  down = controlFocus.player
                }
                .controlFocusOutline(
                  CircleShape,
                  MaterialTheme.colorScheme.primary,
                  enabled = true,
                )
            } else Modifier
          ),
      ) {
        Icon(Icons.Default.Home, contentDescription = "返回首页")
      }
      Column(
        modifier =
          Modifier.weight(1f)
            .then(
              if (controlFocus != null) {
                Modifier.focusRequester(controlFocus.details)
                  .focusProperties {
                    left = beforeDetails ?: FocusRequester.Cancel
                    right = FocusRequester.Cancel
                    up = FocusRequester.Cancel
                    down = controlFocus.player
                  }
                  .controlFocusOutline(
                    RoundedCornerShape(18.dp),
                    MaterialTheme.colorScheme.primary,
                    enabled = true,
                    includeDescendants = false,
                  )
              } else Modifier
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onShowInfo)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
      ) {
        // 选集信息是异步到达的；给标题行预留固定高度，避免资料加载后整组内容重新垂直居中。
        Row(
          modifier = Modifier.fillMaxWidth().height(48.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = model.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (model.selectionTitle != null && onOpenSelection != null) {
            PlaybackPageGlassSurface(
              backdrop = glassBackdrop,
              modifier =
                Modifier.height(42.dp)
                  .padding(start = 10.dp)
                  .widthIn(min = 138.dp, max = 210.dp)
                  .then(
                    if (controlFocus != null) {
                      Modifier.focusRequester(controlFocus.selection)
                        .focusProperties {
                          left =
                            when {
                              showFollowButton -> controlFocus.follow
                              model.ownerMid > 0L -> controlFocus.owner
                              else -> controlFocus.home
                            }
                          right = controlFocus.details
                          up = FocusRequester.Cancel
                          down = controlFocus.player
                        }
                        .controlFocusOutline(
                          RoundedCornerShape(13.dp),
                          MaterialTheme.colorScheme.primary,
                          enabled = true,
                        )
                    } else Modifier
                  )
                  .clickable(onClick = onOpenSelection),
              shape = RoundedCornerShape(13.dp),
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .24f),
              fallbackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .84f),
            ) {
              Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(
                  model.selectionTitle,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = resolvedForeground,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  model.selectionProgress,
                  style = MaterialTheme.typography.labelSmall,
                  color = secondaryForeground,
                )
              }
            }
          }
          Icon(
            Icons.Default.Info,
            contentDescription = "查看完整信息",
            modifier = Modifier.padding(start = 10.dp).size(18.dp),
            tint = resolvedForeground,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (!model.ownerFace.isNullOrBlank()) {
            AsyncImage(
              model = model.ownerFace,
              contentDescription = null,
              modifier =
                Modifier.size(22.dp)
                  .onGloballyPositioned { ownerBounds = it.boundsInRoot() }
                  .clip(CircleShape)
                  .then(
                    if (controlFocus != null) {
                      Modifier.focusRequester(controlFocus.owner)
                        .focusProperties {
                          left = controlFocus.home
                          right = afterOwner ?: FocusRequester.Cancel
                          up = FocusRequester.Cancel
                          down = controlFocus.player
                        }
                        .controlFocusOutline(
                          CircleShape,
                          MaterialTheme.colorScheme.primary,
                          enabled = true,
                        )
                    } else Modifier
                  )
                  .clickable(enabled = model.ownerMid > 0L) {
                    onOwnerProfileClick(
                      model.ownerMid,
                      model.ownerFace,
                      model.ownerName,
                      ownerBounds,
                    )
                  },
              contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(6.dp))
          }
          Text(
            text = model.ownerName,
            modifier =
              Modifier.onGloballyPositioned {
                  if (model.ownerFace.isNullOrBlank()) ownerBounds = it.boundsInRoot()
                }
                .then(
                  if (controlFocus != null && model.ownerFace.isNullOrBlank()) {
                    Modifier.focusRequester(controlFocus.owner)
                      .focusProperties {
                        left = controlFocus.home
                        right = afterOwner ?: FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = controlFocus.player
                      }
                      .controlFocusOutline(
                        RoundedCornerShape(6.dp),
                        MaterialTheme.colorScheme.primary,
                        enabled = true,
                      )
                  } else if (controlFocus != null) {
                    Modifier.focusProperties { canFocus = false }
                  } else Modifier
                )
                .clickable(enabled = model.ownerMid > 0L) {
                  onOwnerProfileClick(
                    model.ownerMid,
                    model.ownerFace,
                    model.ownerName,
                    ownerBounds,
                  )
                },
            style = MaterialTheme.typography.labelMedium,
            color = resolvedForeground,
            maxLines = 1,
          )
          if (showFollowButton) {
            FollowButton(
              followed = followed,
              busy = followBusy,
              groups = followingGroups,
              groupsLoading = followingGroupsLoading,
              loggedIn = loggedIn,
              onLoadGroups = onLoadFollowingGroups,
              onSelectGroup = onSelectFollowingGroup,
              onUnfollow = onUnfollow,
              onLogin = onLogin,
              modifier =
                Modifier.padding(start = 7.dp)
                  .widthIn(min = 72.dp)
                  .then(
                    if (controlFocus != null) {
                      Modifier.focusRequester(controlFocus.follow)
                        .focusProperties {
                          left =
                            if (model.ownerMid > 0L) controlFocus.owner else controlFocus.home
                          right = afterFollow ?: FocusRequester.Cancel
                          up = FocusRequester.Cancel
                          down = controlFocus.player
                        }
                        .controlFocusOutline(
                          RoundedCornerShape(12.dp),
                          MaterialTheme.colorScheme.primary,
                          enabled = true,
                        )
                    } else Modifier
                  ),
              compact = true,
            )
          }
          Text(
            text = "  ·  ",
            style = MaterialTheme.typography.labelMedium,
            color = secondaryForeground,
          )
          BiliRichText(
            text = model.description.ifBlank { "暂无简介" },
            emotes = emptyMap(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium.copy(color = secondaryForeground),
            maxLines = 1,
          )
          Text(
            text = model.metadata,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = secondaryForeground,
            maxLines = 1,
          )
        }
      }
      if (showDeviceStatus) {
        PlaybackPageGlassSurface(
          backdrop = glassBackdrop,
          shape = CircleShape,
        ) {
          DeviceStatusCluster(
            containerColor = Color.Transparent,
            contentColor = resolvedForeground,
          )
        }
      }
    }
  }
}

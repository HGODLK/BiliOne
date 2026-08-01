package dev.openbili.webdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.UserInfo

private val RootIdentityWidth = 218.dp

/**
 * Shared root-page header. Keeping the system inset and the 64 dp content row here prevents Home
 * and My from applying subtly different TopAppBar/sidebar measurements to the same identity block.
 */
@Composable
fun RootAccountHeader(
  user: UserInfo,
  onClick: (Rect) -> Unit,
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.background,
  showUid: Boolean = true,
  nameStyle: TextStyle? = null,
  trailingContent: @Composable RowScope.() -> Unit = {},
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(containerColor)
        .statusBarsPadding()
        .height(64.dp)
        .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AccountIdentity(
      user = user,
      onClick = onClick,
      modifier =
        if (showUid) Modifier.width(RootIdentityWidth)
        else Modifier.widthIn(max = RootIdentityWidth),
      showUid = showUid,
      nameStyle = nameStyle,
    )
    trailingContent()
  }
}

/** Shared root-page account header so Home and My keep identical identity geometry. */
@Composable
fun AccountIdentity(
  user: UserInfo,
  onClick: (Rect) -> Unit,
  modifier: Modifier = Modifier,
  showUid: Boolean = true,
  nameStyle: TextStyle? = null,
) {
  var avatarBounds by remember { mutableStateOf(Rect.Zero) }
  Row(
    modifier =
      modifier.height(64.dp).clip(RoundedCornerShape(12.dp)).clickable { onClick(avatarBounds) },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.size(36.dp)
          .onGloballyPositioned { avatarBounds = it.boundsInRoot() }
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = if (user.isLogin) user.name.trim().take(1).ifBlank { "?" } else "访",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (user.isLogin && user.face.isNotBlank()) {
        AvatarImage(
          face = user.face,
          contentDescription = user.name,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
    Column(Modifier.padding(start = 9.dp)) {
      Text(
        text = if (user.isLogin) user.name else "登录账号",
        style = nameStyle ?: MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (showUid) {
        Text(
          text = if (user.isLogin) "UID ${user.mid}" else "点击扫码登录",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }
    }
  }
}

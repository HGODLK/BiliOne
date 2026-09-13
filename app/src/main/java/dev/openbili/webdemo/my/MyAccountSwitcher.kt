package dev.openbili.webdemo.my

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.openbili.webdemo.api.SavedAccount
import dev.openbili.webdemo.ui.controlFocusOutline

/** “我的”页账号切换入口与弹窗，独立于栏目内容，避免继续扩张 MyScreen。 */
@Composable
internal fun AccountSwitchMenuItem(
  focusRequester: FocusRequester,
  focusEnabled: Boolean,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(14.dp)
  Text(
    "切换账号",
    modifier =
      Modifier.fillMaxWidth()
        .focusRequester(focusRequester)
        .focusProperties { canFocus = focusEnabled }
        .clip(shape)
        .controlFocusOutline(shape = shape, color = MaterialTheme.colorScheme.primary)
        .clickable(onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 15.dp),
    color = MaterialTheme.colorScheme.onSurface,
  )
}

@Composable
internal fun AccountSwitcherDialog(
  accounts: List<SavedAccount>,
  currentMid: Long,
  onDismiss: () -> Unit,
  onAddAccount: () -> Unit,
  onSwitchAccount: (Long) -> Unit,
) {
  val accountRequesters =
    remember(accounts.map(SavedAccount::mid)) {
      accounts.associate { account -> account.mid to FocusRequester() }
    }
  val addAccountRequester = remember { FocusRequester() }
  val closeRequester = remember { FocusRequester() }
  LaunchedEffect(accounts, currentMid) {
    withFrameNanos {}
    val target = accounts.firstOrNull { it.mid != currentMid }
    runCatching {
      if (target != null) accountRequesters.getValue(target.mid).requestFocus()
      else addAccountRequester.requestFocus()
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("切换账号") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(accounts, key = SavedAccount::mid) { account ->
            SavedAccountRow(
              account = account,
              current = account.mid == currentMid,
              focusRequester = accountRequesters.getValue(account.mid),
              onClick = {
                if (account.mid == currentMid) onDismiss() else onSwitchAccount(account.mid)
              },
            )
          }
        }
        HorizontalDivider()
        AccountDialogAction(
          title = "添加账号",
          description = "扫描二维码登录新账号",
          focusRequester = addAccountRequester,
          onClick = onAddAccount,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier =
          Modifier.focusRequester(closeRequester)
            .controlFocusOutline(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.primary,
            ),
      ) {
        Text("关闭")
      }
    },
  )
}

@Composable
private fun SavedAccountRow(
  account: SavedAccount,
  current: Boolean,
  focusRequester: FocusRequester,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(16.dp)
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .focusRequester(focusRequester)
        .clip(shape)
        .background(
          if (current) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surfaceVariant
        )
        .controlFocusOutline(shape = shape, color = MaterialTheme.colorScheme.primary, width = 3.dp)
        .clickable(onClick = onClick)
        .semantics {
          selected = current
          role = Role.Button
        }
        .padding(horizontal = 14.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AccountAvatar(account)
    Column(Modifier.weight(1f)) {
      Text(
        account.name.ifBlank { "UID ${account.mid}" },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontWeight = FontWeight.Medium,
      )
      Text(
        if (current) "当前账号 · UID ${account.mid}" else "UID ${account.mid}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun AccountAvatar(account: SavedAccount) {
  Box(
    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      account.name.trim().take(1).ifBlank { "B" },
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
    )
    if (account.face.isNotBlank()) {
      AsyncImage(
        model = account.face,
        contentDescription = null,
        modifier = Modifier.matchParentSize().clip(CircleShape),
      )
    }
  }
}

@Composable
private fun AccountDialogAction(
  title: String,
  description: String,
  focusRequester: FocusRequester,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(16.dp)
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .focusRequester(focusRequester)
        .clip(shape)
        .controlFocusOutline(shape = shape, color = MaterialTheme.colorScheme.primary, width = 3.dp)
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 10.dp)
  ) {
    Text(title, fontWeight = FontWeight.Medium)
    Text(
      description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

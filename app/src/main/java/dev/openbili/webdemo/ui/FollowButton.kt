package dev.openbili.webdemo.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.FollowingGroup

@Composable
fun FollowButton(
  followed: Boolean,
  busy: Boolean,
  groups: List<FollowingGroup>,
  groupsLoading: Boolean,
  loggedIn: Boolean,
  onLoadGroups: () -> Unit,
  onSelectGroup: (Long) -> Unit,
  onUnfollow: () -> Unit,
  onLogin: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  transparentContainer: Boolean = false,
  focusRequester: FocusRequester? = null,
) {
  var expanded by remember { mutableStateOf(false) }
  Box(modifier) {
    FilledTonalButton(
      onClick = {
        if (!loggedIn) {
          onLogin()
        } else {
          expanded = true
          onLoadGroups()
        }
      },
      enabled = !busy,
      modifier =
        Modifier.heightIn(min = if (compact) 30.dp else 36.dp)
          .then(
            if (focusRequester != null) {
              Modifier.focusRequester(focusRequester!!)
            } else Modifier
          )
          .animateContentSize(),
      colors =
        if (transparentContainer)
          androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color.White.copy(alpha = .55f),
          )
        else androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(),
      elevation =
        if (transparentContainer)
          androidx.compose.material3.ButtonDefaults.filledTonalButtonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
          )
        else androidx.compose.material3.ButtonDefaults.filledTonalButtonElevation(),
      contentPadding =
        PaddingValues(
          horizontal = if (compact) 11.dp else 16.dp,
          vertical = if (compact) 4.dp else 7.dp,
        ),
    ) {
      Crossfade(
        targetState = busy to followed,
        animationSpec = tween(160),
        label = "followState",
      ) { (loading, isFollowed) ->
        if (loading) {
          CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
        } else {
          Text(if (isFollowed) "已关注" else "+ 关注", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      Text(
        if (followed) "要搬去哪个分组呢？( •̀ᴗ•́ )و" else "想把 TA 放在哪里呀？(｡•̀ᴗ-)✧",
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (groupsLoading) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
          Text("正在翻找分组…")
        }
      } else {
        val visibleGroups = groups.ifEmpty {
          listOf(FollowingGroup(id = 0, name = "默认分组", count = 0))
        }
        visibleGroups.forEach { group ->
          DropdownMenuItem(
            text = {
              Text(
                if (group.count > 0) "${group.name}  ${group.count}" else group.name,
                maxLines = 1,
              )
            },
            onClick = {
              expanded = false
              onSelectGroup(group.id)
            },
          )
        }
      }
      if (followed) {
        DropdownMenuItem(
          text = { Text("取消关注，再见啦 (´･ω･`)", color = MaterialTheme.colorScheme.error) },
          onClick = {
            expanded = false
            onUnfollow()
          },
        )
      }
    }
  }
}

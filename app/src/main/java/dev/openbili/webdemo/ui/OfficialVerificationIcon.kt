package dev.openbili.webdemo.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.OfficialVerification

@Composable
internal fun OfficialVerificationIcon(
  verification: OfficialVerification,
  modifier: Modifier = Modifier,
) {
  if (!verification.verified) return
  val personal = verification.type == 0
  Icon(
    imageVector = Icons.Rounded.Bolt,
    contentDescription = verification.description.ifBlank { if (personal) "个人认证" else "机构认证" },
    modifier = modifier,
    tint = if (personal) Color(0xFFFFB23F) else Color(0xFF4E9FFF),
  )
}

internal val OfficialVerificationIconSize = 16.dp

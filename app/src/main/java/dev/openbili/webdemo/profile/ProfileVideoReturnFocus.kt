package dev.openbili.webdemo.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.requestFocusWithinFrames

/** 保存个人主页视频卡的焦点锚点，返回动画结束后恢复到实际点击项。 */
internal class ProfileVideoReturnFocusRegistry {
  private val requesters = mutableMapOf<String, FocusRequester>()
  private var pendingItemId: String? = null

  fun register(itemId: String, requester: FocusRequester) {
    requesters[itemId] = requester
  }

  fun unregister(itemId: String, requester: FocusRequester) {
    if (requesters[itemId] === requester) requesters.remove(itemId)
  }

  fun rememberReturningItem(itemId: String) {
    pendingItemId = itemId
  }

  fun clearPending() {
    pendingItemId = null
  }

  suspend fun restorePending(): Boolean {
    val itemId = pendingItemId ?: return false
    val requester = requesters[itemId] ?: return false
    val restored = requester.requestFocusWithinFrames(maxFrames = 8)
    if (restored) pendingItemId = null
    return restored
  }
}

@Composable
internal fun rememberProfileVideoReturnFocusRegistry(
  profileKey: Any?,
): ProfileVideoReturnFocusRegistry =
  remember(profileKey) { ProfileVideoReturnFocusRegistry() }

/** 只监听退出视频到个人主页的转场，进入视频时不会在底层页面抢焦点。 */
@Composable
internal fun ProfileVideoReturnFocusEffect(
  registry: ProfileVideoReturnFocusRegistry,
  profilePageActive: Boolean,
  returnTransitionActive: Boolean,
  hiddenCoverItemId: String?,
) {
  val controlMode = LocalControlMode.current
  LaunchedEffect(controlMode, profilePageActive, returnTransitionActive, hiddenCoverItemId) {
    if (!controlMode) {
      registry.clearPending()
    } else if (!profilePageActive) {
      return@LaunchedEffect
    } else if (returnTransitionActive) {
      hiddenCoverItemId?.let(registry::rememberReturningItem)
    } else {
      registry.restorePending()
    }
  }
}

/** 为懒列表卡片提供稳定焦点锚点，并在离开组合时安全注销。 */
@Composable
internal fun rememberProfileVideoCardFocusRequester(
  itemId: String,
  registry: ProfileVideoReturnFocusRegistry,
  preferredRequester: FocusRequester? = null,
): FocusRequester {
  val localRequester = remember(itemId) { FocusRequester() }
  val requester = preferredRequester ?: localRequester
  DisposableEffect(itemId, registry, requester) {
    registry.register(itemId, requester)
    onDispose { registry.unregister(itemId, requester) }
  }
  return requester
}

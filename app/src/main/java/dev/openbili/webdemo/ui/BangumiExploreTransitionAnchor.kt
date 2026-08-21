package dev.openbili.webdemo.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import dev.openbili.webdemo.api.BangumiExploreCategory
import dev.openbili.webdemo.bangumi.BangumiExploreViewModel

private const val BANGUMI_EXPLORE_SOURCE_PREFIX = "bangumi-explore-"

/** “正在追”卡片从首次组合开始持有同一个焦点请求器，避免进场时替换焦点节点。 */
internal class BangumiFollowingFocusRegistry {
  private val requesters = mutableMapOf<Pair<BangumiExploreCategory, String>, FocusRequester>()

  fun register(
    category: BangumiExploreCategory,
    itemStableId: String,
    requester: FocusRequester,
  ) {
    requesters[category to itemStableId] = requester
  }

  fun unregister(
    category: BangumiExploreCategory,
    itemStableId: String,
    requester: FocusRequester,
  ) {
    requesters.remove(category to itemStableId, requester)
  }

  fun requester(
    category: BangumiExploreCategory,
    itemStableId: String,
  ): FocusRequester? = requesters[category to itemStableId]
}

/** 把根转场卡片 ID 还原为探索项 ID，并读取仍在布局中持续更新的真实目的地。 */
internal fun BangumiExploreViewModel.currentSourceBounds(sourceCardId: String): Rect? {
  val itemStableId = sourceCardId.removePrefix(BANGUMI_EXPLORE_SOURCE_PREFIX)
  val bounds = returnAnchorForItem(itemStableId)?.sourceBounds?.takeIf { it.hasUsableSize() }
    ?: return null
  return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
}

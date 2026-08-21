package dev.openbili.webdemo.ui

/**
 * 番剧根页的控制器层级。状态只描述“焦点在哪一层”，具体卡片焦点仍由各页面保存，
 * 这样不会把番剧页面的布局细节重新集中到 AppRoot。
 */
internal enum class BangumiControlLevel {
  ROOT,
  HERO,
  EXPLORE_NAV,
  EXPLORE_CONTENT,
  EXPLORE_HERO,
  INDEX_CONTROLS,
  INDEX_CONTENT,
}

internal fun bangumiControlBackTarget(level: BangumiControlLevel): BangumiControlLevel =
  when (level) {
    BangumiControlLevel.ROOT -> BangumiControlLevel.ROOT
    BangumiControlLevel.HERO -> BangumiControlLevel.ROOT
    BangumiControlLevel.EXPLORE_NAV -> BangumiControlLevel.HERO
    BangumiControlLevel.EXPLORE_CONTENT -> BangumiControlLevel.EXPLORE_NAV
    BangumiControlLevel.EXPLORE_HERO -> BangumiControlLevel.EXPLORE_CONTENT
    BangumiControlLevel.INDEX_CONTROLS -> BangumiControlLevel.EXPLORE_NAV
    BangumiControlLevel.INDEX_CONTENT -> BangumiControlLevel.INDEX_CONTROLS
  }

internal fun bangumiControlDownTarget(level: BangumiControlLevel): BangumiControlLevel? =
  when (level) {
    BangumiControlLevel.HERO -> BangumiControlLevel.EXPLORE_NAV
    BangumiControlLevel.EXPLORE_NAV -> BangumiControlLevel.EXPLORE_CONTENT
    BangumiControlLevel.INDEX_CONTROLS -> BangumiControlLevel.INDEX_CONTENT
    else -> null
  }

internal fun bangumiControlUpTarget(level: BangumiControlLevel): BangumiControlLevel? =
  when (level) {
    BangumiControlLevel.EXPLORE_NAV -> BangumiControlLevel.HERO
    BangumiControlLevel.INDEX_CONTENT -> BangumiControlLevel.INDEX_CONTROLS
    else -> null
  }

internal fun bangumiControlMoveCategory(
  current: Int,
  delta: Int,
  categoryCount: Int,
): Int? {
  if (categoryCount <= 0) return null
  val target = current + delta
  return target.takeIf { it in 0 until categoryCount }
}

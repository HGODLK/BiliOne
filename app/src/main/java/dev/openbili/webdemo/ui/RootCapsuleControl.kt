package dev.openbili.webdemo.ui

import dev.openbili.webdemo.my.MyControlLevel

/**
 * 根导航胶囊只在当前根页的第一级、且没有详情/索引覆盖层时进入控制器焦点树。
 *
 * 根信息流会为了共享元素返回保留在组合树中；视觉隐藏不能作为焦点失效的依据。
 */
internal fun rootCapsuleFocusEnabled(
  controlMode: Boolean,
  showVideo: Boolean,
  showBangumiIndex: Boolean,
  rootTab: RootTab,
  homeControlLevel: HomeControlLevel,
  bangumiControlLevel: BangumiControlLevel,
  myControlLevel: MyControlLevel,
): Boolean =
  !controlMode ||
    (!showVideo &&
      !showBangumiIndex &&
      when (rootTab) {
        RootTab.HOME -> homeControlLevel == HomeControlLevel.ROOT
        RootTab.BANGUMI -> bangumiControlLevel == BangumiControlLevel.ROOT
        RootTab.MY -> myControlLevel == MyControlLevel.ROOT
      })

/** 触控输入时保持胶囊可点；控制器接管后仅在胶囊实际处于一级焦点层时显示。 */
internal fun rootCapsuleVisible(
  controlMode: Boolean,
  controlFocusVisible: Boolean,
  focusEnabled: Boolean,
): Boolean = !controllerInteractionActive(controlMode, controlFocusVisible) || focusEnabled

/** “我的”在跨根页确认时直接进入栏目，其余根页保持再次确认才进入。 */
internal fun shouldEnterRootTabOnControlConfirm(current: RootTab, target: RootTab): Boolean =
  current == target || target == RootTab.MY

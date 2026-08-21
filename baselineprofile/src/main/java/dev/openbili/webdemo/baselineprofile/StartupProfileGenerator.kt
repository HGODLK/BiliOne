package dev.openbili.webdemo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup Profile: captures only the cold-start path, which the baseline profile tooling extracts
 * into the release startup-profile input, which AGP merges into the release ART profile used for
 * DEX layout.
 *
 * Scenarios: cold start -> home framework first frame -> home first-screen content.
 * All selectors are existing UI text / a11y nodes; no testTag was added, no coordinates are used.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {
  @get:Rule val baselineProfileRule = BaselineProfileRule()

  @Test
  fun captureStartupProfile() {
    baselineProfileRule.collect(
      packageName = "io.github.shuyunr.bilione",
      includeInStartupProfile = true,
    ) {
      // 冷启动：回到桌面后重新启动应用。
      pressHome()
      startActivityAndWait()

      // 首页框架首帧：静态分区胶囊（推荐/动态/热门/直播）出现即视为框架已绘制。
      device.wait(Until.hasObject(By.text("推荐")), 5_000)

      // 首页首屏内容：优先使用现有 feed 网格资源 ID；当前 Compose 无障碍树未暴露
      // testTag 时，退回到首个卡片尺寸的可点击节点，不依赖标题或屏幕坐标。
      if (!device.wait(Until.hasObject(By.res("feed_grid")), 20_000)) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (System.nanoTime() < deadline) {
          val firstCard =
            device
              .findObjects(By.clazz("android.view.View").clickable(true).focusable(true))
              .firstOrNull { it.visibleBounds.width() > 400 && it.visibleBounds.height() > 250 }
          if (firstCard != null) break
          Thread.sleep(250)
        }
      }
    }
  }
}

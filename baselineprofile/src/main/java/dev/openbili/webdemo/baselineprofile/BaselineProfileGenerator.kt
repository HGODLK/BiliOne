package dev.openbili.webdemo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile: covers the main user flows. All selectors are existing UI text /
 * contentDescription / a11y nodes (no testTag added, no hardcoded coordinates). Network-driven
 * steps are guarded so a slow/failed response degrades to fewer rules rather than aborting the
 * collection — the profile only records what actually executed.
 *
 * Covered: cold start, home load + scroll, partition switch, and the music player surface. The
 * video detail/player flow is intentionally omitted from this capture: on the current
 * nonMinifiedRelease emulator artifact, opening the first feed card reaches the existing
 * VideoScreen and triggers an ART VerifyError before the flow can be completed. The seek bar and
 * fullscreen toggle are custom Canvas/self-drawn controls with no stable accessibility node, so
 * they are also intentionally omitted rather than represented by an unstable coordinate.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
  @get:Rule val baselineProfileRule = BaselineProfileRule()

  @Test
  fun captureBaselineProfile() {
    baselineProfileRule.collect("io.github.shuyunr.bilione") {
      // 1. 冷启动 + 首页加载完成。
      pressHome()
      startActivityAndWait()
      device.wait(Until.hasObject(By.text("推荐")), 5_000)
      val feedLoaded = device.wait(Until.hasObject(By.res("feed_grid")), 20_000)

      // 2. 首页滚动。
      if (feedLoaded) {
        device.findObject(By.res("feed_grid")).scroll(Direction.DOWN, 1.0f)
      } else if (device.wait(Until.hasObject(By.scrollable(true)), 20_000)) {
        // Compose testTags are not exposed as resource IDs in this build; use the existing
        // accessibility scroll container as the fallback, never a screen coordinate.
        device.findObjects(By.scrollable(true)).firstOrNull()?.scroll(Direction.DOWN, 1.0f)
      }

      // 3. 分区切换：动态 -> 热门 -> 直播 -> 推荐。
      if (device.wait(Until.hasObject(By.text("动态")), 3_000)) {
        device.findObject(By.text("动态")).parent?.click()
        device.wait(Until.hasObject(By.text("仅视频")), 5_000)
      }
      if (device.wait(Until.hasObject(By.text("热门")), 3_000)) {
        device.findObject(By.text("热门")).parent?.click()
        device.wait(Until.hasObject(By.text("综合热门")), 8_000)
      }
      if (device.wait(Until.hasObject(By.text("直播")), 3_000)) {
        device.findObject(By.text("直播")).parent?.click()
        device.wait(Until.hasObject(By.text("推荐直播")), 8_000)
      }
      if (device.wait(Until.hasObject(By.text("推荐")), 3_000)) {
        device.findObject(By.text("推荐")).parent?.click()
        device.wait(Until.hasObject(By.res("feed_grid")), 10_000)
      }

      // 4. 音乐界面：使用现有无障碍描述进入，并用现有“回到首页”退出。账号、收藏夹和
      // 网络内容不是 Profile 的前置条件；页面在未登录/空收藏夹状态也会完成框架绘制。
      if (device.wait(Until.hasObject(By.desc("打开音乐播放器")), 3_000)) {
        device.findObject(By.desc("打开音乐播放器")).parent?.click()
        if (device.wait(Until.hasObject(By.text("音乐播放")), 8_000)) {
          if (device.wait(Until.hasObject(By.desc("回到首页")), 3_000)) {
            device.findObject(By.desc("回到首页")).parent?.click()
            device.wait(Until.hasObject(By.desc("打开音乐播放器")), 5_000)
          }
        }
      }
    }
  }
}

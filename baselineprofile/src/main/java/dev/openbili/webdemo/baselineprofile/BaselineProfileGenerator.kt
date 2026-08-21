package dev.openbili.webdemo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.MacrobenchmarkScope
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
 * Covered: cold start, home load + scroll, all public home partitions, search overlay, the
 * bangumi and account roots, the settings/history surfaces, and the music player surface. The
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

      // 3. 分区切换：动态 -> 热门（含排行榜）-> 直播 -> 推荐。
      // 每一步都使用稳定的语义文本，并允许网络请求超时后继续采集后续本地 UI。
      if (clickText("动态")) {
        device.wait(Until.hasObject(By.text("仅视频")), 5_000)
        scrollResource("feed_grid")
      }
      if (clickText("热门")) {
        device.wait(Until.hasObject(By.text("综合热门")), 8_000)
        scrollResource("popular_grid")
        if (clickText("排行榜")) {
          device.wait(Until.hasObject(By.text("排行榜")), 5_000)
          scrollResource("popular_grid")
          clickText("综合热门")
        }
      }
      if (clickText("直播")) {
        device.wait(Until.hasObject(By.text("推荐直播")), 8_000)
        scrollResource("live_home_grid")
      }
      if (clickText("推荐")) {
        device.wait(Until.hasObject(By.res("feed_grid")), 10_000)
        scrollResource("feed_grid")
      }

      // 4. 搜索浮层：覆盖搜索入口、热搜/联想面板及关闭路径，不提交网络搜索。
      if (clickText("搜索视频")) {
        device.wait(Until.hasObject(By.text("热搜")), 5_000) ||
          device.wait(Until.hasObject(By.text("搜索历史")), 1_000)
        device.pressBack()
        device.wait(Until.hasObject(By.desc("打开音乐播放器")), 3_000)
      }

      // 5. 根页签：番剧和我的。它们的请求即使未登录也会绘制稳定的空态/错误态框架。
      if (clickText("番剧")) {
        device.wait(Until.hasObject(By.text("本期推荐")), 12_000)
        scrollAny()
      }
      if (clickText("我的")) {
        device.wait(Until.hasObject(By.text("设置")), 8_000)
        if (clickText("历史记录")) {
          device.wait(Until.hasObject(By.text("历史记录")), 5_000)
          scrollAny()
        }
        if (clickText("设置")) {
          device.wait(Until.hasObject(By.text("存储与关于")), 8_000)
          scrollAny()
        }
      }

      // 回到首页后再采集音乐路径，避免根页签/子页的状态影响播放器入口。
      clickText("首页")
      device.wait(Until.hasObject(By.desc("打开音乐播放器")), 5_000)

      // 6. 音乐界面：使用现有无障碍描述进入，并用现有“回到首页”退出。账号、收藏夹和
      // 网络内容不是 Profile 的前置条件；页面在未登录/空收藏夹状态也会完成框架绘制。
      if (device.wait(Until.hasObject(By.desc("打开音乐播放器")), 3_000)) {
        clickDescription("打开音乐播放器")
        if (device.wait(Until.hasObject(By.text("音乐播放")), 8_000)) {
          if (device.wait(Until.hasObject(By.desc("回到首页")), 3_000)) {
            clickDescription("回到首页")
            device.wait(Until.hasObject(By.desc("打开音乐播放器")), 5_000)
          }
        }
      }
    }
  }

  private fun MacrobenchmarkScope.clickText(text: String, timeoutMs: Long = 3_000): Boolean {
    if (!device.wait(Until.hasObject(By.text(text)), timeoutMs)) return false
    val node = device.findObject(By.text(text)) ?: return false
    return runCatching {
      (node.parent ?: node).click()
      true
    }.getOrDefault(false)
  }

  private fun MacrobenchmarkScope.clickDescription(
    description: String,
    timeoutMs: Long = 3_000,
  ): Boolean {
    if (!device.wait(Until.hasObject(By.desc(description)), timeoutMs)) return false
    val node = device.findObject(By.desc(description)) ?: return false
    return runCatching {
      (node.parent ?: node).click()
      true
    }.getOrDefault(false)
  }

  private fun MacrobenchmarkScope.scrollResource(
    resourceName: String,
    timeoutMs: Long = 3_000,
  ): Boolean {
    if (!device.wait(Until.hasObject(By.res(resourceName)), timeoutMs)) return scrollAny()
    return runCatching {
      device.findObject(By.res(resourceName))?.scroll(Direction.DOWN, 1.0f) ?: false
    }.getOrDefault(false)
  }

  private fun MacrobenchmarkScope.scrollAny(): Boolean =
    runCatching {
      device.findObjects(By.scrollable(true)).firstOrNull()?.scroll(Direction.DOWN, 1.0f)
        ?: false
    }.getOrDefault(false)
}

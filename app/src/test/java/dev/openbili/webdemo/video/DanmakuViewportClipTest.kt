package dev.openbili.webdemo.video

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuViewportClipTest {
  @Test
  fun `embedded danmaku uses the player corner radius`() {
    assertEquals(50f, danmakuViewportCornerRadiusPx(fullscreen = false, density = 2.5f), 0f)
  }

  @Test
  fun `fullscreen danmaku keeps square window edges`() {
    assertEquals(0f, danmakuViewportCornerRadiusPx(fullscreen = true, density = 2.5f), 0f)
  }
}

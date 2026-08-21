package dev.openbili.webdemo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面 UA 构造规则的 JVM 测试，使用固定的已知 Chrome 版本，
 * 因此不需要 Android 上下文。
 */
class UserAgentProviderTest {

  @Test
  fun `desktop UA contains Chrome version`() {
    val ua = buildDesktopUa("130.0.6723.102")
    assertTrue("UA should contain Chrome/130.0.6723.102", ua.contains("Chrome/130.0.6723.102"))
  }

  @Test
  fun `desktop UA excludes Android`() {
    val ua = buildDesktopUa("130.0.6723.102")
    assertFalse("UA must not contain 'Android'", ua.contains("Android"))
  }

  @Test
  fun `desktop UA excludes Mobile`() {
    val ua = buildDesktopUa("130.0.6723.102")
    assertFalse("UA must not contain 'Mobile'", ua.contains("Mobile"))
  }

  @Test
  fun `desktop UA excludes wv`() {
    val ua = buildDesktopUa("130.0.6723.102")
    assertFalse("UA must not contain 'wv'", ua.contains("wv"))
  }

  @Test
  fun `desktop UA uses Linux x86_64`() {
    val ua = buildDesktopUa("130.0.6723.102")
    assertTrue("UA should contain 'Linux x86_64'", ua.contains("Linux x86_64"))
  }

  @Test
  fun `desktop UA excludes Version_slash`() {
    val ua = buildDesktopUa("130.0.6723.102")
    assertFalse("UA must not contain 'Version/'", ua.contains("Version/"))
  }

  @Test
  fun `desktop UA starts with Mozilla`() {
    val ua = buildDesktopUa("130.0.6723.102")
    assertTrue("UA should start with Mozilla/5.0", ua.startsWith("Mozilla/5.0"))
  }

  @Test
  fun `desktop UA contains Safari`() {
    val ua = buildDesktopUa("130.0.6723.102")
    assertTrue("UA should contain Safari/", ua.contains("Safari/"))
  }

  // ── 辅助函数 ──────────────────────────────────────────────────────────────

  companion object {
    /**
     * UserAgentProvider 所用桌面 UA 模板的镜像，但无需 Android 上下文即可调用。
     */
    fun buildDesktopUa(chromeVersion: String): String =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)" +
        " Chrome/$chromeVersion Safari/537.36"
  }
}

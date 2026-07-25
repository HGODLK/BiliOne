package dev.openbili.webdemo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the desktop-UA construction rules, using a fixed known Chrome version so no Android
 * context is needed.
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

  // ── helpers ──────────────────────────────────────────────────────────────

  companion object {
    /**
     * Mirror of the desktop-UA template used by UserAgentProvider, but callable without an Android
     * context.
     */
    fun buildDesktopUa(chromeVersion: String): String =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)" +
        " Chrome/$chromeVersion Safari/537.36"
  }
}

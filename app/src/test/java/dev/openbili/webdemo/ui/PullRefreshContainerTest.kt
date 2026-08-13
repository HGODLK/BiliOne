package dev.openbili.webdemo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRefreshContainerTest {
  @Test
  fun `indicator fades in while pull distance grows`() {
    assertEquals(0f, pullRefreshIndicatorAlpha(0f, isRefreshing = false))
    val early = pullRefreshIndicatorAlpha(.2f, isRefreshing = false)
    val later = pullRefreshIndicatorAlpha(.55f, isRefreshing = false)
    assertTrue(early > 0f)
    assertTrue(later > early)
    assertEquals(1f, pullRefreshIndicatorAlpha(.75f, isRefreshing = false))
  }

  @Test
  fun `complete indicator layer moves only inside its visible travel range`() {
    assertEquals(0f, pullRefreshIndicatorOffsetFraction(-.2f, isRefreshing = false))
    assertEquals(.4f, pullRefreshIndicatorOffsetFraction(.4f, isRefreshing = false))
    assertEquals(1f, pullRefreshIndicatorOffsetFraction(1.4f, isRefreshing = false))
    assertEquals(1f, pullRefreshIndicatorOffsetFraction(0f, isRefreshing = true))
  }

  @Test
  fun `refreshing indicator remains fully visible`() {
    assertEquals(1f, pullRefreshIndicatorAlpha(0f, isRefreshing = true))
  }
}

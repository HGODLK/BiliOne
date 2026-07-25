package dev.openbili.webdemo.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleImageLayoutTest {
  @Test
  fun ordinaryPortraitScalesWithoutCropping() {
    val layout = calculateArticleImageLayout(.75f, maxWidth = 900f, maxHeight = 400f)

    assertFalse(layout.crop)
    assertEquals(300f, layout.width, .01f)
    assertEquals(400f, layout.height, .01f)
  }

  @Test
  fun extraLongPortraitUsesCenteredHalfScreenCrop() {
    val layout = calculateArticleImageLayout(.2f, maxWidth = 900f, maxHeight = 400f)

    assertTrue(layout.crop)
    assertEquals(400f, layout.height, .01f)
    assertTrue(layout.width < 900f)
  }

  @Test
  fun landscapeDoesNotFillAvailableWidth() {
    val layout = calculateArticleImageLayout(2f, maxWidth = 900f, maxHeight = 500f)

    assertFalse(layout.crop)
    assertTrue(layout.width < 900f)
    assertTrue(layout.height <= 500f)
  }
}

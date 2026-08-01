package dev.openbili.webdemo.video

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentImagePreviewGeometryTest {
  @Test
  fun openingUsesOneUniformScaleThatFitsInsideSourceBounds() {
    val source = Rect(0f, 0f, 180f, 120f)

    assertEquals(.2f, commentImageStartScale(source, 600f, 600f), .0001f)
  }

  @Test
  fun panIsLockedUntilScaledImageExceedsViewport() {
    assertEquals(0f, commentImagePanLimit(600f, 1000f, 1.5f), .0001f)
    assertEquals(100f, commentImagePanLimit(600f, 1000f, 2f), .0001f)
  }

  @Test
  fun longScreenshotUsesScrollableTwoFifthsTabletWidth() {
    val layout =
      commentImagePreviewLayout(
        viewportWidth = 1800f,
        viewportHeight = 1000f,
        imageWidth = 1080,
        imageHeight = 6000,
        wideViewport = true,
      )

    assertEquals(720f, layout.widthPx, .0001f)
    assertEquals(860f, layout.heightPx, .0001f)
    assertEquals(true, layout.verticallyScrollable)
  }

  @Test
  fun longScreenshotCanLoadTheOriginalBilibiliImage() {
    assertEquals(
      "https://i0.hdslb.com/bfs/note/long.png?token=x",
      fullResolutionCommentImageUrl(
        "https://i0.hdslb.com/bfs/note/long.png@720w_4000h_1c.webp?token=x"
      ),
    )
    assertEquals(
      "https://example.com/long.png@720w.webp",
      fullResolutionCommentImageUrl("https://example.com/long.png@720w.webp"),
    )
  }

  @Test
  fun longScreenshotHtmlUsesTheFullImageWithoutAResizeSuffix() {
    val html =
      longCommentImageHtml(
        fullResolutionCommentImageUrl(
          "https://i0.hdslb.com/bfs/new_dyn/long.jpg@176w_0-0-176-176a_1s.avif"
        )
      )

    assertEquals(true, html.contains("https://i0.hdslb.com/bfs/new_dyn/long.jpg"))
    assertEquals(false, html.contains("@176w_0-0-176-176a_1s.avif"))
    assertEquals(true, html.contains("width: 100%"))
  }

  @Test
  fun regularImageKeepsFitPreviewGeometry() {
    val layout =
      commentImagePreviewLayout(
        viewportWidth = 1800f,
        viewportHeight = 1000f,
        imageWidth = 1600,
        imageHeight = 900,
        wideViewport = true,
      )

    assertEquals(false, layout.verticallyScrollable)
    assertEquals(1528.8889f, layout.widthPx, .001f)
    assertEquals(860f, layout.heightPx, .001f)
  }
}

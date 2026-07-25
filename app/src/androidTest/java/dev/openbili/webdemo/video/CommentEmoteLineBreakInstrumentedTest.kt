package dev.openbili.webdemo.video

import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.ceil
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentEmoteLineBreakInstrumentedTest {
  @Test
  fun consecutiveObjectPlaceholdersSoftWrap() {
    val text = COMMENT_EMOTE_LAYOUT_PLACEHOLDER.toString().repeat(12)
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 48f }
    val placeholderWidth = paint.measureText(COMMENT_EMOTE_LAYOUT_PLACEHOLDER.toString())
    val layoutWidth = ceil(placeholderWidth * 3.25f).toInt()
    val layout =
      StaticLayout.Builder.obtain(text, 0, text.length, paint, layoutWidth)
        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
        .setIncludePad(false)
        .build()

    assertTrue("The placeholder must occupy layout width", placeholderWidth > 0f)
    assertTrue("Consecutive emotes must soft-wrap to multiple lines", layout.lineCount >= 3)
    assertTrue("The last emote must be below the first line", layout.getLineForOffset(11) > 0)
  }
}

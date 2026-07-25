package dev.openbili.webdemo.video

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.toTextFieldBuffer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.openbili.webdemo.api.BiliEmote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentComposerEmoteTest {
  private val doge = BiliEmote("[doge]", "https://example.invalid/doge.png")
  private val smile = BiliEmote("[微笑]", "https://example.invalid/smile.png")

  @Test
  fun eachEmoteGetsOneStableEditorCharacter() {
    val registry = CommentEmoteMarkerRegistry()
    val first = registry.snapshot(listOf(doge, smile))
    val second = registry.snapshot(listOf(smile, doge))

    assertEquals(first.markerFor(doge), second.markerFor(doge))
    assertEquals(first.markerFor(smile), second.markerFor(smile))
    assertNotEquals(first.markerFor(doge), first.markerFor(smile))
  }

  @Test
  fun emoteAfterNewlineUsesNativeSingleCharacterIndex() {
    val snapshot = CommentEmoteMarkerRegistry().snapshot(listOf(doge))
    val marker = requireNotNull(snapshot.markerFor(doge))
    val editorText = "$marker\n$marker"
    val placements = findCommentEmotePlacements(editorText, snapshot.markerToEmote)

    assertEquals(3, editorText.length)
    assertEquals(listOf(0, 2), placements.map { it.index })
    assertEquals("[doge]\n[doge]", snapshot.decode(editorText))
  }

  @Test
  fun outputUsesOnePlaceholderAndPreservesFollowingLine() {
    val snapshot = CommentEmoteMarkerRegistry().snapshot(listOf(doge))
    val marker = requireNotNull(snapshot.markerFor(doge))
    val buffer = TextFieldState("$marker\nnext").toTextFieldBuffer()

    with(commentEmoteMarkerOutputTransformation(snapshot.markerToEmote.keys)) {
      buffer.transformOutput()
    }

    assertEquals("$COMMENT_EMOTE_LAYOUT_PLACEHOLDER\nnext", buffer.toString())
    assertEquals(6, buffer.length)
  }

  @Test
  fun consecutiveEmotesUseNonWhitespaceLayoutPlaceholders() {
    val snapshot = CommentEmoteMarkerRegistry().snapshot(listOf(doge))
    val marker = requireNotNull(snapshot.markerFor(doge))
    val buffer = TextFieldState(marker.toString().repeat(12)).toTextFieldBuffer()

    with(commentEmoteMarkerOutputTransformation(snapshot.markerToEmote.keys)) {
      buffer.transformOutput()
    }

    assertEquals(COMMENT_EMOTE_LAYOUT_PLACEHOLDER.toString().repeat(12), buffer.toString())
    assertFalse(COMMENT_EMOTE_LAYOUT_PLACEHOLDER.isWhitespace())
  }

  @Test
  fun emoteTriggersDetachedEditorAtSameSixthVisualLineAsText() {
    val snapshot = CommentEmoteMarkerRegistry().snapshot(listOf(doge))
    val marker = requireNotNull(snapshot.markerFor(doge))

    assertEquals("汉".length, marker.toString().length)
    assertFalse(commentEditorShouldDetach(marker.toString().length, visualLineCount = 5))
    assertTrue(commentEditorShouldDetach(marker.toString().length, visualLineCount = 6))
  }

  @Test
  fun characterFallbackTracksTheLargerLongCommentThreshold() {
    assertFalse(commentEditorShouldDetach(characterCount = 359, visualLineCount = 5))
    assertTrue(commentEditorShouldDetach(characterCount = 360, visualLineCount = 5))
  }

  @Test
  fun deletingAnEmoteIsARegularSingleCharacterDeletion() {
    val snapshot = CommentEmoteMarkerRegistry().snapshot(listOf(doge))
    val marker = requireNotNull(snapshot.markerFor(doge))
    val editorText = "a${marker}b"

    val updated = editorText.removeRange(1, 2)

    assertEquals("ab", updated)
    assertTrue(findCommentEmotePlacements(updated, snapshot.markerToEmote).isEmpty())
  }

  @Test
  fun submissionRestoresOriginalEmoteTokens() {
    val snapshot = CommentEmoteMarkerRegistry().snapshot(listOf(doge, smile))
    val dogeMarker = requireNotNull(snapshot.markerFor(doge))
    val smileMarker = requireNotNull(snapshot.markerFor(smile))

    assertEquals(
      "第一行[doge]\n第二行[微笑]",
      snapshot.decode("第一行$dogeMarker\n第二行$smileMarker"),
    )
  }

  @Test
  fun emoteIsInsertedAtCurrentSelectionAsOneCharacter() {
    val snapshot = CommentEmoteMarkerRegistry().snapshot(listOf(doge))
    val marker = requireNotNull(snapshot.markerFor(doge))
    val result =
      replaceCommentSelection(
        TextFieldValue("hello world", selection = TextRange(5)),
        marker.toString(),
      )

    assertEquals("hello${marker} world", result.text)
    assertEquals(TextRange(6), result.selection)
  }
}

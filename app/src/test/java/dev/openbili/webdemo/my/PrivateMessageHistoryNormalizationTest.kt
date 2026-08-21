package dev.openbili.webdemo.my

import dev.openbili.webdemo.api.AccountMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateMessageHistoryNormalizationTest {
  @Test
  fun `overlapping server pages keep one item per stable id`() {
    val original =
      message(
        id = 2497347355676692L,
        key = 9001L,
        type = 2,
        cover = "https://i0.hdslb.com/image.jpg",
      )

    val normalized = normalizePrivateMessageHistory(listOf(original, original.copy()))

    assertEquals(1, normalized.size)
    assertEquals(original.id, normalized.single().id)
  }

  @Test
  fun `server image replaces matching local pending image`() {
    val local = message(id = -1L, key = 0L, type = 2, cover = "https://i0.example/a.jpg")
    val server = message(id = 42L, key = 420L, type = 2, cover = "https://i0.example/a.jpg")

    val result = normalizePrivateMessageHistory(listOf(local, server))

    assertEquals(listOf(42L), result.map(AccountMessage::id))
  }

  @Test
  fun `withdraw notice replaces original and duplicate withdraw status`() {
    val original = message(id = 10L, key = 100L, content = "hello")
    val withdrawnOriginal =
      original.copy(
        withdrawn = true,
        messageType = 5,
        content = "你撤回了一条消息",
        withdrawTargetMessageKey = 100L,
      )
    val withdrawNotice =
      message(id = 11L, key = 101L, type = 5, content = "100")
        .copy(
          withdrawn = true,
          withdrawTargetMessageKey = 100L,
        )

    val result = normalizePrivateMessageHistory(listOf(original, withdrawnOriginal, withdrawNotice))

    assertEquals(1, result.size)
    assertTrue(result.single().withdrawn)
    assertEquals(100L, result.single().withdrawTargetMessageKey)
  }

  private fun message(
    id: Long,
    key: Long,
    type: Int = 1,
    content: String = "",
    cover: String = "",
  ) =
    AccountMessage(
      id = id,
      userMid = 1L,
      userName = "我",
      userFace = "",
      title = "",
      content = content,
      sourceContent = "",
      oid = 0L,
      rootId = 0L,
      parentId = 0L,
      time = 1_000L,
      coverUrl = cover,
      messageType = type,
      isPrivate = true,
      senderMid = 1L,
      receiverMid = 2L,
      sequence = id.coerceAtLeast(0L),
      messageKey = key,
      isOutgoing = true,
    )
}

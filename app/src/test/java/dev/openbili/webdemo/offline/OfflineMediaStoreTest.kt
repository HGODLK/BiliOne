package dev.openbili.webdemo.offline

import android.content.Context
import dev.openbili.webdemo.api.PremiumAudioMode
import dev.openbili.webdemo.api.VideoChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OfflineMediaStoreTest {
  private lateinit var context: Context

  @Before
  fun clearStore() {
    context = RuntimeEnvironment.getApplication()
    context
      .getSharedPreferences(OfflineMediaStore.PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun insertIfAbsentKeepsTheFirstCacheTask() {
    val store = OfflineMediaStore(context)
    val first = entry(title = "合集", partTitle = "第 1 集")
    val duplicate = first.copy(title = "被覆盖的标题", partTitle = "第 2 集")

    assertTrue(store.insertIfAbsent(first))
    assertFalse(store.insertIfAbsent(duplicate))
    assertEquals(listOf(first), store.entries())
  }

  @Test
  fun collectionIdSurvivesPersistence() {
    val store = OfflineMediaStore(context)
    val entry = entry(title = "合集", partTitle = "第 1 集")

    assertTrue(store.insertIfAbsent(entry))

    assertEquals(9988L, store.entry(entry.id)?.collectionId)
    assertEquals("第 1 集", store.entry(entry.id)?.partTitle)
  }

  @Test
  fun audioModeAndChaptersSurvivePersistence() {
    val store = OfflineMediaStore(context)
    val entry =
      entry(title = "音乐", partTitle = "P1").copy(
        audioMode = PremiumAudioMode.HI_RES,
        audioQualityLabel = "HiRes",
        chapters = listOf(VideoChapter(0L, 10_000L, "前奏")),
      )

    assertTrue(store.insertIfAbsent(entry))

    val restored = store.entry(entry.id)
    assertEquals(PremiumAudioMode.HI_RES, restored?.audioMode)
    assertEquals("HiRes", restored?.audioQualityLabel)
    assertEquals(entry.chapters, restored?.chapters)
  }

  @Test
  fun queueMetadataSurvivesPersistenceAndSequenceIsMonotonic() {
    val store = OfflineMediaStore(context)
    val entry = entry(title = "队列", partTitle = "P1").copy(queueSequence = 42L, pausedByUser = true)

    assertTrue(store.insertIfAbsent(entry))

    assertEquals(entry, store.entry(entry.id))
    assertEquals(43L, store.nextQueueSequence())
    assertEquals(44L, store.nextQueueSequence())
  }

  private fun entry(title: String, partTitle: String) =
    OfflineMediaEntry(
      id = offlineMediaId(OfflineMediaKind.VIDEO, "BV1test", 100L, 0L),
      kind = OfflineMediaKind.VIDEO,
      accountMid = 1L,
      title = title,
      partTitle = partTitle,
      coverUrl = "https://example.com/cover.jpg",
      bvid = "BV1test",
      aid = 2L,
      cid = 100L,
      pageNumber = 1,
      collectionId = 9988L,
      durationMs = 60_000L,
      qualityId = 80,
      qualityLabel = "1080P",
      createdAtMs = 123L,
    )
}

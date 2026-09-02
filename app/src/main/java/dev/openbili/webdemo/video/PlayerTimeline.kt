package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.VideoChapter

/** 过滤无效章节并按起点去重，供播放器和音乐页共享。 */
internal fun normalizedChapters(
  durationMs: Long,
  chapters: List<VideoChapter>,
): List<VideoChapter> {
  if (chapters.isEmpty()) return emptyList()
  return chapters
    .asSequence()
    .filter { it.startMs >= 0L && it.endMs > it.startMs }
    .filter { it.title.isNotBlank() }
    .filter { durationMs <= 0L || it.startMs < durationMs }
    .map { chapter ->
      if (durationMs > 0L) chapter.copy(endMs = chapter.endMs.coerceIn(chapter.startMs + 1L, durationMs))
      else chapter
    }
    .filter { it.endMs > it.startMs }
    .sortedWith(compareBy<VideoChapter> { it.startMs }.thenBy { it.endMs })
    .distinctBy(VideoChapter::startMs)
    .toList()
}

/** 仅返回可用于显示文本的章节；空 content 不回退、不显示。 */
internal fun displayableChapters(chapters: List<VideoChapter>): List<VideoChapter> =
  chapters.filter { it.title.isNotBlank() }

/** 返回当前位置实际落入的章节；位于章节空档或边界之外时返回 null。 */
internal fun chapterAtPosition(positionMs: Long, chapters: List<VideoChapter>): VideoChapter? {
  val normalized = normalizedChapters(0L, chapters)
  val position = positionMs.coerceAtLeast(0L)
  return normalized.firstOrNull { position >= it.startMs && position < it.endMs }
}

/** 返回整个 Point 集合内部的章节边界；第一个起点和最后一个终点不绘制圆点。 */
private fun chapterBoundaryPositions(
  durationMs: Long,
  chapters: List<VideoChapter>,
): List<Long> {
  if (durationMs <= 0L || chapters.isEmpty()) return emptyList()
  val allBoundaries =
    normalizedChapters(durationMs, chapters)
      .asSequence()
      .flatMap { chapter -> sequenceOf(chapter.startMs, chapter.endMs) }
      .distinct()
      .sorted()
      .toList()
  if (allBoundaries.size <= 2) return emptyList()
  return allBoundaries.drop(1).dropLast(1)
}

/** 返回进度条上需要绘制的内部章节分隔线，并保持稳定顺序。 */
internal fun chapterBoundaryFractions(
  durationMs: Long,
  chapters: List<VideoChapter>,
): List<Float> {
  return chapterBoundaryPositions(durationMs, chapters)
    .asSequence()
    .map { it.toFloat() / durationMs.toFloat() }
    .filter { it.isFinite() }
    .toList()
}

/** 返回当前播放章节前后两个可见内部节点；整个 Point 集合的首尾节点始终隐藏。 */
internal fun activeChapterBoundaryFractions(
  durationMs: Long,
  positionMs: Long,
  chapters: List<VideoChapter>,
): Set<Float> {
  if (durationMs <= 0L || chapters.isEmpty()) return emptySet()
  val position = positionMs.coerceIn(0L, durationMs)
  val activeChapter =
    normalizedChapters(durationMs, chapters)
      .firstOrNull { position >= it.startMs && position < it.endMs }
      ?: return emptySet()
  val visibleBoundaries = chapterBoundaryPositions(durationMs, chapters).toSet()
  return sequenceOf(activeChapter.startMs, activeChapter.endMs)
    .filter { it in visibleBoundaries }
    .map { it.toFloat() / durationMs.toFloat() }
    .filter { it.isFinite() }
    .toSet()
}

/** 在给定横坐标命中范围内返回最近的可见章节边界，否则返回 null。 */
internal fun nearestChapterBoundaryForTap(
  xPx: Float,
  widthPx: Float,
  durationMs: Long,
  chapters: List<VideoChapter>,
  hitSlopPx: Float,
): Long? {
  if (widthPx <= 0f || durationMs <= 0L || hitSlopPx < 0f) return null
  val nearest =
    chapterBoundaryPositions(durationMs, chapters)
      .minByOrNull { boundaryMs -> kotlin.math.abs(xPx - widthPx * boundaryMs / durationMs) }
      ?: return null
  val distance = kotlin.math.abs(xPx - widthPx * nearest / durationMs)
  return nearest.takeIf { distance <= hitSlopPx }
}

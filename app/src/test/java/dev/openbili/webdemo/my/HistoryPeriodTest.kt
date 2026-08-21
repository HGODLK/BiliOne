package dev.openbili.webdemo.my

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPeriodTest {
  private val zone = ZoneId.of("Asia/Taipei")
  private val today = LocalDate.of(2026, 8, 21)

  @Test
  fun rangesUseLocalMidnightAndDoNotOverlap() {
    val ranges = historyPeriodRanges(today = today, zone = zone, nowSeconds = 1_787_241_600L)
    val todayStart = today.atStartOfDay(zone).toEpochSecond()
    val yesterdayStart = today.minusDays(1).atStartOfDay(zone).toEpochSecond()
    val dayBeforeStart = today.minusDays(2).atStartOfDay(zone).toEpochSecond()

    assertEquals(todayStart, ranges.getValue(HistoryPeriod.TODAY).startSeconds)
    assertEquals(todayStart, ranges.getValue(HistoryPeriod.YESTERDAY).endSeconds)
    assertEquals(yesterdayStart, ranges.getValue(HistoryPeriod.YESTERDAY).startSeconds)
    assertEquals(yesterdayStart, ranges.getValue(HistoryPeriod.DAY_BEFORE).endSeconds)
    assertEquals(dayBeforeStart, ranges.getValue(HistoryPeriod.EARLIER).endSeconds)
    assertTrue(
      ranges.values.all { range ->
        range.startSeconds < range.endSeconds
      }
    )
  }

  @Test
  fun eachBoundaryContainsOnlyItsOwnDate() {
    val ranges = historyPeriodRanges(today = today, zone = zone, nowSeconds = 1_787_241_600L)
    val yesterdayStart = ranges.getValue(HistoryPeriod.YESTERDAY).startSeconds
    val todayStart = ranges.getValue(HistoryPeriod.TODAY).startSeconds

    assertTrue(ranges.getValue(HistoryPeriod.YESTERDAY).contains(yesterdayStart))
    assertTrue(!ranges.getValue(HistoryPeriod.TODAY).contains(yesterdayStart))
    assertTrue(!ranges.getValue(HistoryPeriod.YESTERDAY).contains(todayStart))
  }

  @Test
  fun timelineTargetIncludesEveryNewerPeriod() {
    assertEquals(
      listOf(HistoryPeriod.TODAY, HistoryPeriod.YESTERDAY),
      historyPeriodsThrough(HistoryPeriod.YESTERDAY),
    )
    assertEquals(
      HistoryPeriod.entries.toList(),
      historyPeriodsThrough(HistoryPeriod.EARLIER),
    )
  }
}

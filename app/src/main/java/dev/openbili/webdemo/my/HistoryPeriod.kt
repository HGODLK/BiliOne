package dev.openbili.webdemo.my

import dev.openbili.webdemo.api.HistoryCursor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 历史记录页面的四个日期锚点。每个锚点都有自己的服务端游标。 */
enum class HistoryPeriod(val label: String) {
  TODAY("今天"),
  YESTERDAY("昨天"),
  DAY_BEFORE("前天"),
  EARLIER("更早"),
}

/** 一个日期锚点允许显示的观看时间范围，结束时间为开区间。 */
data class HistoryPeriodRange(
  val startSeconds: Long,
  val endSeconds: Long,
) {
  fun contains(viewAt: Long): Boolean = viewAt in startSeconds until endSeconds
}

/** 历史锚点的独立分页状态；“正在追”不使用此状态。 */
data class HistoryPeriodLoadState(
  val range: HistoryPeriodRange = HistoryPeriodRange(0L, 0L),
  val items: List<HistoryCardItem> = emptyList(),
  val cursor: HistoryCursor = HistoryCursor(),
  val hasMore: Boolean = false,
  val loading: Boolean = false,
  val initialized: Boolean = false,
  val error: String? = null,
)

/** 时间轴跳转期间的连续元数据加载状态；封面加载不计入此状态。 */
data class HistoryTimelineLoadState(
  val target: HistoryPeriod? = null,
  val loading: Boolean = false,
  val completed: Boolean = false,
  val error: String? = null,
)

fun historyPeriodsThrough(target: HistoryPeriod): List<HistoryPeriod> =
  HistoryPeriod.entries.take(target.ordinal + 1)

/** 使用本地时区计算四个日期锚点，避免用固定 24 小时导致夏令时边界错误。 */
fun historyPeriodRanges(
  today: LocalDate = LocalDate.now(),
  zone: ZoneId = ZoneId.systemDefault(),
  nowSeconds: Long = Instant.now().epochSecond,
): Map<HistoryPeriod, HistoryPeriodRange> {
  fun startOf(date: LocalDate): Long = date.atStartOfDay(zone).toEpochSecond()

  val todayStart = startOf(today)
  val yesterdayStart = startOf(today.minusDays(1))
  val dayBeforeStart = startOf(today.minusDays(2))
  return mapOf(
    HistoryPeriod.TODAY to HistoryPeriodRange(todayStart, (nowSeconds + 1L).coerceAtLeast(todayStart)),
    HistoryPeriod.YESTERDAY to HistoryPeriodRange(yesterdayStart, todayStart),
    HistoryPeriod.DAY_BEFORE to HistoryPeriodRange(dayBeforeStart, yesterdayStart),
    HistoryPeriod.EARLIER to HistoryPeriodRange(1L, dayBeforeStart),
  )
}

fun emptyHistoryPeriodStates(
  ranges: Map<HistoryPeriod, HistoryPeriodRange> = historyPeriodRanges(),
): Map<HistoryPeriod, HistoryPeriodLoadState> =
  HistoryPeriod.entries.associateWith { period ->
    val range = ranges.getValue(period)
    HistoryPeriodLoadState(
      range = range,
      cursor = HistoryCursor(viewAt = range.endSeconds),
    )
  }

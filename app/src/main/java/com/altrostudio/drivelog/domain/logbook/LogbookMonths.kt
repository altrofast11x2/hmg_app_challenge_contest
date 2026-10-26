package com.altrostudio.drivelog.domain.logbook

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** 운행기록부에서 바로 고를 수 있는 달과 그 달의 운행 수 */
data class MonthChoice(val month: YearMonth, val tripCount: Int)

/** 월 목록: 최근 1년은 항상, 그보다 오래된 운행이 있으면 그 달까지 (최대 2년). 이번 달보다 뒤는 없다 */
object LogbookMonths {
    private const val ALWAYS_SHOWN = 12L
    private const val MAX_SHOWN = 24L

    fun choices(tripStartedAt: List<Long>, current: YearMonth, zone: ZoneId): List<MonthChoice> {
        val counts = tripStartedAt
            .map { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) }
            .groupingBy { it }
            .eachCount()
        val earliestTrip = counts.keys.minOrNull()
        val oldest = current.minusMonths(MAX_SHOWN - 1)
        var month = minOf(earliestTrip ?: current, current.minusMonths(ALWAYS_SHOWN - 1)).let { if (it < oldest) oldest else it }
        val result = mutableListOf<MonthChoice>()
        while (month <= current) {
            result += MonthChoice(month, counts[month] ?: 0)
            month = month.plusMonths(1)
        }
        return result
    }
}
